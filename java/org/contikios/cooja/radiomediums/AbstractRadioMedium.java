/*
 * Copyright (c) 2006, Swedish Institute of Computer Science.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the Institute nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE INSTITUTE AND CONTRIBUTORS ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE INSTITUTE OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 *
 */

package org.contikios.cooja.radiomediums;

import java.lang.Runnable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Observable;
import java.util.Observer;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.contikios.cooja.RadioConnection;
import org.contikios.cooja.RadioMedium;
import org.contikios.cooja.RadioPacket;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.TimeEvent;
import org.contikios.cooja.interfaces.CustomDataRadio;
import org.contikios.cooja.interfaces.Radio;
import org.contikios.cooja.util.ScnObservable;
import org.jdom2.Element;


/**
 * Abstract radio medium provides basic functionality for implementing radio
 * mediums.
 * <p>
 * The radio medium forwards both radio packets and custom data objects.
 * <p>
 * The registered radios' signal strengths are updated whenever the radio medium
 * changes. There are three fixed levels: no surrounding traffic heard, noise
 * heard and data heard.
 * <p>
 * It handles radio registrations, radio loggers, active connections and
 * observes all registered radio interfaces.
 *
 * @author Fredrik Osterlind
 */
public abstract class AbstractRadioMedium implements RadioMedium {
	private static final Logger logger = LogManager.getLogger(AbstractRadioMedium.class);
	
	/* Signal strengths in dBm.
	 * Approx. values measured on TmoteSky */
	public static final double SS_NOTHING = -100;
	public static final double SS_STRONG = -10;
	public static final double SS_WEAK = -95;
	protected final Map<Radio, Double> baseRssi = java.util.Collections.synchronizedMap(new HashMap<>());
	protected final Map<Radio, Double> sendRssi = java.util.Collections.synchronizedMap(new HashMap<>());
	
	private final ArrayList<Radio> registeredRadios = new ArrayList<>();
	
	private final ArrayList<RadioConnection> activeConnections = new ArrayList<>();
	
	private RadioConnection lastConnection = null;
	
	private final Simulation simulation;
	
	/* Bookkeeping */
	public int COUNTER_TX = 0;
	public int COUNTER_RX = 0;
	public int COUNTER_INTERFERED = 0;
	
	/**
	 * Two Observables to observe the radioMedium and radioTransmissions
	 * @see #addRadioTransmissionObserver
	 * @see #addRadioMediumObserver
	 */
	protected final ScnObservable radioMediumObservable = new ScnObservable();
	protected final ScnObservable radioTransmissionObservable = new ScnObservable();
	
	/**
	 * This constructor should always be called from implemented radio mediums.
	 *
	 * @param simulation Simulation
	 */
	public AbstractRadioMedium(Simulation simulation) {
		this.simulation = simulation;
	}
	
	/**
	 * @return All registered radios
	 */
	public Radio[] getRegisteredRadios() {
		return registeredRadios.toArray(new Radio[0]);
	}
	
	/**
	 * @return All active connections
	 */
	public RadioConnection[] getActiveConnections() {
		/* NOTE: toArray([0]) creates array and handles synchronization */
		return activeConnections.toArray(new RadioConnection[0]);
	}
	
	/**
	 * Creates a new connection from given radio.
	 * <p>
	 * Determines which radios should receive or be interfered by the transmission.
	 *
	 * @param radio Source radio
	 * @return New connection
	 */
	abstract public RadioConnection createConnections(Radio radio);

	final
	protected void strength_powerup(Radio dstRadio, double signalStrength) {
		if (dstRadio.getCurrentSignalStrength() < signalStrength) {
			dstRadio.setCurrentSignalStrength(signalStrength);
		}
	}

	/* Fail if radios are on different (but configured) channels */
	final
	protected boolean not_same_chanel(Radio sender, Radio recv) {
		return sender.getChannel() >= 0 &&
				recv.getChannel() >= 0 &&
				sender.getChannel() != recv.getChannel();
	}


	/**
	 * Updates all radio interfaces' signal strengths according to
	 * the current active connections.
	 */
	public void updateSignalStrengths() {
		
		/* Reset signal strengths */
		for (Radio radio : getRegisteredRadios()) {
			radio.setCurrentSignalStrength(getBaseRssi(radio));
		}
		
		/* Set signal strength to strong on destinations */
		RadioConnection[] conns = getActiveConnections();
		for (RadioConnection conn : conns) {
			Radio source = conn.getSource();
			strength_powerup(source, SS_STRONG);

			for (Radio dstRadio : conn.getDestinations()) {
				if (not_same_chanel(source, dstRadio ) ) continue;

				strength_powerup(dstRadio, SS_STRONG);
			}
		}
		
		/* Set signal strength to weak on interfered */
		for (RadioConnection conn : conns) {
			Radio source = conn.getSource();
			
			for (Radio intfRadio : conn.getInterfered()) {
				strength_powerup(intfRadio, SS_STRONG);

				if (not_same_chanel(source, intfRadio ) ) continue;

				if (!intfRadio.isInterfered()) {
					/*logger.warn("Radio was not interfered");*/
					intfRadio.interfereAnyReception();
				}
			}
		}
	}
	
	
	/**
	 * Remove given radio from any active connections.
	 * This method can be called if a radio node falls asleep or is removed.
	 *
	 * @param radio Radio
	 */
	protected void removeFromActiveConnections(Radio radio) {
		/* Set interfered if currently a connection destination */
		if (!activeConnections.isEmpty())
		for (int i = 0; i < activeConnections.size(); ++i) {
			RadioConnection conn = activeConnections.get(i);
			if (conn.isDestination(radio)) {
				conn.addInterfered(radio);
				if (!radio.isInterfered()) {
					radio.interfereAnyReception();
				}
			}
		}
	}
	
	protected RadioConnection getActiveConnectionFrom(Radio source) {
		if (!activeConnections.isEmpty())
		for (int i = 0; i < activeConnections.size(); ++i) {
			RadioConnection conn = activeConnections.get(i);
			if (conn.getSource() == source) {
				return conn;
			}
		}
		return null;
	}
	
	@FunctionalInterface
	interface RadioMethod {
	    // abstract method
	    void run(Radio x);
	}


	protected void invokeRemote(RadioConnection connection,  Radio radio, RadioMethod action) {
		long delay = connection.getDestinationDelay(radio);
			if (delay == 0) {
				action.run(radio);
			} else {
				
				/* EXPERIMENTAL: Simulating propagation delay */
				final Radio delayedRadio = radio;
				TimeEvent delayedEvent = new TimeEvent() {
					@Override
					public void execute(long t) {
						action.run(delayedRadio);
					}
				};
				simulation.scheduleEvent(delayedEvent, simulation.getSimulationTime() + delay);
			}
	}

	/**
	 * This observer is responsible for detecting radio interface events, for example
	 * new transmissions.
	 */
	private final Observer radioEventsObserver = new Observer() {
		@Override
		public void update(Observable obs, Object obj) {
      if (!(obs instanceof Radio radio)) {
				logger.fatal("Radio event dispatched by non-radio object");
				return;
			}
			final Radio.RadioEvent event = radio.getLastEvent();
			
			switch (event) {
				case RECEPTION_STARTED:
				case RECEPTION_INTERFERED:
				case RECEPTION_FINISHED:
					break;

				case UNKNOWN:
				case HW_ON: {
					/* Update signal strengths */
					updateSignalStrengths();
				}
				break;
				case HW_OFF: {
          // This radio must not be a connection source.
          if (getActiveConnectionFrom(radio) != null) {
            logger.fatal("Connection source turned off radio: " + radio);
          }

					/* Remove any radio connections from this radio */
					removeFromActiveConnections(radio);
					/* Update signal strengths */
					updateSignalStrengths();
				}
				break;
				case TRANSMISSION_STARTED: {
					/* Create new radio connection */
					if (radio.isReceiving()) {
						/*
						 * Radio starts transmitting when it should be
						 * receiving! Ok, but it won't receive the packet
						 */
						radio.interfereAnyReception();
						for (RadioConnection conn : activeConnections) {
							if (conn.isDestination(radio)) {
								conn.addInterfered(radio);
							}
						}
					}
					
					RadioConnection newConnection = createConnections(radio);
                    if (newConnection != null) {
                        activeConnections.add(newConnection);
                    }

					/* Update signal strengths before reception start */
					updateSignalStrengths();
					
					if (newConnection != null)
					for (Radio r : newConnection.getAllDestinations()) {
						invokeRemote(newConnection, r, (x)->{ x.signalReceptionStart(); } );
					} 
					
					/* Notify observers */
					lastConnection = null;
					radioTransmissionObservable.setChangedAndNotify();
				}
				break;
				case TRANSMISSION_FINISHED: {
					/* Remove radio connection */

					/* Connection */
					RadioConnection connection = getActiveConnectionFrom(radio);
					if (connection == null) {
						return; // SilentRadioMedium will return here.
					}
					
					activeConnections.remove(connection);
					lastConnection = connection;
					COUNTER_TX++;
					for (Radio dstRadio : connection.getAllDestinations()) {
						invokeRemote(connection, dstRadio, (x)->{ x.signalReceptionEnd(); } );
					}
					COUNTER_RX += connection.getDestinations().length;
					COUNTER_INTERFERED += connection.getInterfered().length;
					for (Radio intRadio : connection.getInterferedNonDestinations()) {

					  if (intRadio.isInterfered()) {
					    intRadio.signalReceptionEnd();
					  }
					}
					
					/* Notify observers */
					// TODO: observers takes old signal strengs for last transmited - right before finished
					//		this gives observers sence rssi for receivers.
					radioTransmissionObservable.setChangedAndNotify();

					/* Update signal strengths */
					updateSignalStrengths();
					
				}
				break;
				case CUSTOM_DATA_TRANSMITTED: {
					
					/* Connection */
					RadioConnection connection = getActiveConnectionFrom(radio);
					if (connection == null) {
						logger.fatal("No radio connection found");
						return;
					}
					
					/* Custom data object */
					Object data = ((CustomDataRadio) radio).getLastCustomDataTransmitted();
					if (data == null) {
						logger.fatal("No custom data objecTransmissiont to forward");
						return;
					}
					
					for (Radio dstRadio : connection.getAllDestinations()) {
						if (!(dstRadio instanceof CustomDataRadio) || 
						    !((CustomDataRadio) dstRadio).canReceiveFrom((CustomDataRadio)radio)) {
							/* Radios communicate via radio packets */
							continue;
						}
						
						invokeRemote(connection, dstRadio, 
								new RadioMethod() {
										final Object xdata = data;
										public void run( Radio x) {
											((CustomDataRadio) x).receiveCustomData(xdata);
										}
									} 
								);
					}
					
				}
				break;
				case PACKET_TRANSMITTED: {
					/* Connection */
					RadioConnection connection = getActiveConnectionFrom(radio);
					if (connection == null) {
						return; // SilentRadioMedium will return here.
					}
					
					/* Radio packet */
					RadioPacket packet = radio.getLastPacketTransmitted();
					if (packet == null) {
						logger.fatal("No radio packet to forward");
						return;
					}
					
					for (Radio dstRadio : connection.getAllDestinations()) {

					  if ((radio instanceof CustomDataRadio) &&
					      (dstRadio instanceof CustomDataRadio) && 
					      ((CustomDataRadio) dstRadio).canReceiveFrom((CustomDataRadio)radio)) {
					    /* Radios instead communicate via custom data objects */
					    continue;
					  }

					
					invokeRemote(connection, dstRadio, 
							new RadioMethod() {
								final RadioPacket xPacket = packet;
								public void run( Radio x) {
									x.setReceivedPacket(xPacket);
								}
							} 
						);
						
					}
				}
				break;
				default:
					logger.fatal("Unsupported radio event: " + event);
			}
		}
	};

	@Override
	public void registerRadioInterface(Radio radio, Simulation sim) {
		if (radio == null) {
			logger.warn("No radio to register");
			return;
		}
		
		registeredRadios.add(radio);
		radio.addObserver(radioEventsObserver);
		radioMediumObservable.setChangedAndNotify();
		
		/* Update signal strengths */
		updateSignalStrengths();
	}
	
	@Override
	public void unregisterRadioInterface(Radio radio, Simulation sim) {
		if (!registeredRadios.contains(radio)) {
			logger.warn("No radio to unregister: " + radio);
			return;
		}
		
		radio.deleteObserver(radioEventsObserver);
		registeredRadios.remove(radio);
		
		removeFromActiveConnections(radio);
		
		radioMediumObservable.setChangedAndNotify();
		
		/* Update signal strengths */
		updateSignalStrengths();
	}
	
	/**
	* Get the RSSI value that is set when there is "silence"
	* 
	* @param radio
	*          The radio to get the base RSSI for
	* @return The base RSSI value; Default: SS_NOTHING
	*/
	public double getBaseRssi(Radio radio) {
		Double rssi = baseRssi.get(radio);
		if (rssi == null) {
			rssi = SS_NOTHING;
		}
		return rssi;
	}

	/**
	* Set the base RSSI for a radio. This value is set when there is "silence"
	* 
	* @param radio
	*          The radio to set the RSSI value for
	* @param rssi
	*          The RSSI value to set during silence
	*/
	public void setBaseRssi(Radio radio, double rssi) {
		baseRssi.put(radio, rssi);
		simulation.invokeSimulationThread(this::updateSignalStrengths);
	}

	
	/**
	* Get the minimum RSSI value that is set when the radio is sending
	* 
	* @param radio
	*          The radio to get the send-RSSI for
	* @return The send-RSSI value; Default: SS_STRONG
	*/
	public double getSendRssi(Radio radio) {
		Double rssi = sendRssi.get(radio);
		if (rssi == null) {
			rssi = SS_STRONG;
		}
		return rssi;
	}

	/**
	* Set the send-RSSI for a radio. This is the minimum value when the radio is
	* sending
	* 
	* @param radio
	*          The radio to set the RSSI value for
	* @param rssi
	*          The minimum RSSI value to set when sending
	*/
	public void setSendRssi(Radio radio, double rssi) {
		sendRssi.put(radio, rssi);
	}
	
	/**
	 * Register an observer that gets notified when the radiotransmissions changed.
	 * E.g. creating new connections.
	 * This does not include changes in the settings and (de-)registration of radios.
	 * @see #addRadioMediumObserver
	 * @param observer the Observer to register
	 */
	@Override
	public void addRadioTransmissionObserver(Observer observer) {
		radioTransmissionObservable.addObserver(observer);
	}
	
	@Override
	public Observable getRadioTransmissionObservable() {
		return radioTransmissionObservable;
	}
	
	@Override
	public void deleteRadioTransmissionObserver(Observer observer) {
		radioTransmissionObservable.deleteObserver(observer);
	}
	
	/**
	 * Register an observer that gets notified when the radio medium changed.
	 * This includes changes in the settings and (de-)registration of radios. 
	 * This does not include transmissions, etc. as these are part of the radio
	 * and not the radio medium itself.
	 * @see #addRadioTransmissionObserver
	 * @param observer the Observer to register
	 */
	public void addRadioMediumObserver(Observer observer) {
		radioMediumObservable.addObserver(observer);
	}

	public void deleteRadioMediumObserver(Observer observer) {
		radioMediumObservable.deleteObserver(observer);
	}
	
	@Override
	public RadioConnection getLastConnection() {
		return lastConnection;
	}
	@Override
	public Collection<Element> getConfigXML() {
		Collection<Element> config = new ArrayList<>();
		for(Entry<Radio, Double> ent: baseRssi.entrySet()){
			Element element = new Element("BaseRSSIConfig");
			element.setAttribute("Mote", String.valueOf(ent.getKey().getMote().getID()));
			element.addContent(String.valueOf(ent.getValue()));
			config.add(element);
		}

		for(Entry<Radio, Double> ent: sendRssi.entrySet()){
			Element element = new Element("SendRSSIConfig");
			element.setAttribute("Mote", String.valueOf(ent.getKey().getMote().getID()));
			element.addContent(String.valueOf(ent.getValue()));
			config.add(element);
		}

		return config;
	}

  @Override
  public boolean setConfigXML(final Collection<Element> configXML, boolean visAvailable) {
    for (var element : configXML) {
      if (element.getName().equals("BaseRSSIConfig")) {
        Radio r = simulation.getMoteWithID(Integer.parseInt(element.getAttribute("Mote").getValue())).getInterfaces().getRadio();
        setBaseRssi(r, Double.parseDouble(element.getText()));
      } else if (element.getName().equals("SendRSSIConfig")) {
        Radio r = simulation.getMoteWithID(Integer.parseInt(element.getAttribute("Mote").getValue())).getInterfaces().getRadio();
        setSendRssi(r, Double.parseDouble(element.getText()));
      }
    }
    return true;
  }
}
