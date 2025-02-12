/*
 * Copyright (c) 2009, Swedish Institute of Computer Science.
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import org.contikios.cooja.Cooja;
import org.jdom2.Element;

import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.RadioConnection;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.interfaces.Position;
import org.contikios.cooja.interfaces.Radio;
import org.contikios.cooja.plugins.Visualizer;
import org.contikios.cooja.plugins.skins.UDGMVisualizerSkin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Unit Disk Graph Radio Medium abstracts radio transmission range as circles.
 * <p>
 * It uses two different range parameters: one for transmissions, and one for
 * interfering with other radios and transmissions.
 * <p>
 * Both radio ranges grow with the radio output power indicator.
 * The range parameters are multiplied with [output power]/[maximum output power].
 * For example, if the transmission range is 100m, the current power indicator 
 * is 50, and the maximum output power indicator is 100, then the resulting transmission 
 * range becomes 50m.
 * <p>
 * For radio transmissions within range, two different success ratios are used [0.0-1.0]:
 * one for successful transmissions, and one for successful receptions.
 * If the transmission fails, no radio will hear the transmission.
 * If one of receptions fail, only that receiving radio will not receive the transmission,
 * but will be interfered throughout the entire radio connection.  
 * <p>
 * The received radio packet signal strength grows inversely with the distance to the
 * transmitter.
 *
 * @see #SS_STRONG
 * @see #SS_WEAK
 * @see #SS_NOTHING
 *
 * @see DirectedGraphMedium
 * @see UDGMVisualizerSkin
 * @author Fredrik Osterlind
 */
@ClassDescription("Unit Disk Graph Medium (UDGM): Distance Loss")
public class UDGM extends AbstractRadioMedium {
  private static final Logger logger = LoggerFactory.getLogger(UDGM.class);

  public double SUCCESS_RATIO_TX = 1.0; /* Success ratio of TX. If this fails, no radios receive the packet */
  public double SUCCESS_RATIO_RX = 1.0; /* Success ratio of RX. If this fails, the single affected receiver does not receive the packet */
  public double TRANSMITTING_RANGE = 50; /* Transmission range. */
  public double INTERFERENCE_RANGE = 100; /* Interference range. Ignored if below transmission range. */

  private final DirectedGraphMedium dgrm; /* Used only for efficient destination lookup */

  private final Random random;

  public UDGM(Simulation simulation) {
    super(simulation);
    random = simulation.getRandomGenerator();
    dgrm = new DirectedGraphMedium(simulation) {
      @Override
      protected void analyzeEdges() {
        /* Create edges according to distances.
         * XXX May be slow for mobile networks */
        clearEdges();
        Radio[] radios = UDGM.this.getRegisteredRadios();
        for (Radio source: radios) {
          Position sourcePos = source.getPosition();
          for (Radio dest: radios) {
            /* Ignore ourselves */
            if (source == dest) {
              continue;
            }
            Position destPos = dest.getPosition();
            double distance = sourcePos.getDistanceTo(destPos);
            if (distance < Math.max(TRANSMITTING_RANGE, INTERFERENCE_RANGE)) {
              /* Add potential destination */
              addEdge(
                  new DirectedGraphMedium.Edge(source, 
                      new DGRMDestinationRadio(dest)));
            }
          }
        }
        super.analyzeEdges();
      }
    };

    /* Register as position observer.
     * If any positions change, re-analyze potential receivers. */
    simulation.getEventCentral().getPositionTriggers().addTrigger(this, (o, m) -> dgrm.requestEdgeAnalysis());
    /* Re-analyze potential receivers if radios are added/removed. */
    simulation.getMoteTriggers().addTrigger(this, (o, m) -> dgrm.requestEdgeAnalysis());

    dgrm.requestEdgeAnalysis();

    if (Cooja.isVisualized()) {
      Visualizer.registerVisualizerSkin(UDGMVisualizerSkin.class);
    }
  }

  @Override
  public List<Radio> getNeighbors(Radio sourceRadio) {
    var list = new ArrayList<Radio>();
    var sourceRadioPosition = sourceRadio.getPosition();
    double moteTransmissionRange = TRANSMITTING_RANGE
            * ((double) sourceRadio.getCurrentOutputPowerIndicator() / (double) sourceRadio.getOutputPowerIndicatorMax());
    for (var radio : dgrm.getPotentialDestinations(sourceRadio)) {
      if (radio.radio == sourceRadio) {
        continue;
      }
      double distance = sourceRadioPosition.getDistanceTo(radio.radio.getPosition());
      if (distance <= moteTransmissionRange) {
        list.add(radio.radio);
      }
    }
    return list;
  }

  @Override
  public void removed() {
  	super.removed();

    if (Cooja.isVisualized()) {
      Visualizer.unregisterVisualizerSkin(UDGMVisualizerSkin.class);
    }
  }
  
  public void setTxRange(double r) {
    TRANSMITTING_RANGE = r;
    dgrm.requestEdgeAnalysis();
  }

  public void setInterferenceRange(double r) {
    INTERFERENCE_RANGE = r;
    dgrm.requestEdgeAnalysis();
  }

  public double signalPowerFactorTo(Radio sender, Radio dstRadio) {
      double dist = sender.getPosition().getDistanceTo(dstRadio.getPosition());

      double maxTxDist = TRANSMITTING_RANGE
      * ((double) sender.getCurrentOutputPowerIndicator() / (double) sender.getOutputPowerIndicatorMax());

      if (maxTxDist == 0.0)
    	  return 0.0;
      
      double distFactor = dist/maxTxDist;
      return distFactor;
  }
  
  @Override
  public RadioConnection createConnections(Radio sender) {
    RadioConnection newConnection = new RadioConnection(sender);

    /* Fail radio transmission randomly - no radios will hear this transmission */
    if (getTxSuccessProbability(sender) < 1.0 && random.nextDouble() > getTxSuccessProbability(sender)) {
      return newConnection;
    }

    /* Calculate ranges: grows with radio output power */
    double powerRate = ((double) sender.getCurrentOutputPowerIndicator() / (double) sender.getOutputPowerIndicatorMax()); 
    double moteTransmissionRange = TRANSMITTING_RANGE * powerRate;
    double moteInterferenceRange = INTERFERENCE_RANGE * powerRate;

    /* Get all potential destination radios */
    final DestinationRadio[] potentialDestinations = dgrm.getPotentialDestinations(sender);
    if (potentialDestinations == null) {
      return newConnection;
    }

    /* Loop through all potential destinations */
    Position senderPos = sender.getPosition();
    
    int sz = potentialDestinations.length;
    for (int desti = 0; desti < sz; ++desti ) {
        DestinationRadio dest = potentialDestinations[desti]; 
      Radio recv = dest.radio;

      /* Fail if radios are on different (but configured) channels */
      if ( not_same_chanel(sender, recv) ) {
        /* Add the connection in a dormant state;
           it will be activated later when the radio will be
           turned on and switched to the right channel. This behavior
           is consistent with the case when receiver is turned off. */
        newConnection.addInterfered(recv);

        continue;
      }

      Position recvPos = recv.getPosition();

      /* Fail if radio is turned off */
//      if (!recv.isReceiverOn()) {
//        /* Special case: allow connection if source is Contiki radio, 
//         * and destination is something else (byte radio).
//         * Allows cross-level communication with power-saving MACs. */
//        if (sender instanceof ContikiRadio &&
//            !(recv instanceof ContikiRadio)) {
//          /*logger.info("Special case: creating connection to turned off radio");*/
//        } else {
//          recv.interfereAnyReception();
//          continue;
//        }
//      }

      double distance = senderPos.getDistanceTo(recvPos);
      if (distance <= moteTransmissionRange) {
        /* Within transmission range */

        if (!recv.isRadioOn()) {
          newConnection.addInterfered(recv);
          recv.interfereAnyReception();
        } else if (recv.isInterfered()) {
          /* Was interfered: keep interfering */
          newConnection.addInterfered(recv);
        } else if (recv.isTransmitting()) {
          newConnection.addInterfered(recv);
        } else if (recv.isReceiving() ||
            (random.nextDouble() > getRxSuccessProbability(sender, recv))) {
          /* Was receiving, or reception failed: start interfering */
          newConnection.addInterfered(recv);
          recv.interfereAnyReception();

          /* Interfere receiver in all other active radio connections */
          for (RadioConnection conn : getActiveConnections()) {
            if (conn.isDestination(recv)) {
              conn.addInterfered(recv);
            }
          }

        } else {
          /* Success: radio starts receiving */
          newConnection.addDestination(recv);
        }
      } else if (distance <= moteInterferenceRange) {
        /* Within interference range */
        newConnection.addInterfered(recv);
        recv.interfereAnyReception();
      }
    }

    return newConnection;
  }
  
  public double getSuccessProbability(Radio source, Radio dest) {
  	return getTxSuccessProbability(source) * getRxSuccessProbability(source, dest);
  }
  public double getTxSuccessProbability(Radio source) {
    return SUCCESS_RATIO_TX;
  }
  
  public double getRxSuccessProbability(Radio source, Radio dest) {
    
    double ratio = signalPowerFactorTo(source, dest); 
    if (ratio == 0.0) {
        return 0.0;
      }

    ratio = ratio* ratio;
    if (ratio > 1.0) {
    	return 0.0;
    }
    return 1.0 - ratio*(1.0-SUCCESS_RATIO_RX);
  }

  @Override
  public void updateSignalStrengths() {
    /* Override: uses distance as signal strength factor */
    
    /* Reset signal strengths */
    for (Radio radio : getRegisteredRadios()) {
      radio.setCurrentSignalStrength(getBaseRssi(radio));
    }

    /* Set signal strength to below strong on destinations */
    RadioConnection[] conns = getActiveConnections();
    for (RadioConnection conn : conns) {
      Radio source = conn.getSource(); 
      strength_powerup(source, SS_STRONG);

      for (Radio dstRadio : conn.getDestinations()) {
        if ( not_same_chanel( source, dstRadio) ) continue;

        double distFactor = signalPowerFactorTo(source, dstRadio);
        double signalStrength = SS_STRONG + distFactor*(SS_WEAK - SS_STRONG);

        strength_powerup(dstRadio, signalStrength);
      }
    }

    /* Set signal strength to below weak on interfered */
    for (RadioConnection conn : conns) {
      Radio source = conn.getSource(); 
      for (Radio intfRadio : conn.getInterfered()) {
        if ( not_same_chanel(source, intfRadio) )  continue;

        double distFactor = signalPowerFactorTo(source, intfRadio);

        if (distFactor < 1) {
        	
          double signalStrength = SS_STRONG + distFactor*(SS_WEAK - SS_STRONG);
          strength_powerup(intfRadio, signalStrength);
          
        } else {
        	
          intfRadio.setCurrentSignalStrength(SS_WEAK);
          strength_powerup(intfRadio, SS_WEAK);	// WT ? why set twice?
          
        }

        if (!intfRadio.isInterfered()) {
          /*logger.warn("Radio was not interfered: " + intfRadio);*/
          intfRadio.interfereAnyReception();
        }
      }
    }
  }

  @Override
  public Collection<Element> getConfigXML() {
    Collection<Element> config = super.getConfigXML();
    Element element;

    /* Transmitting range */
    element = new Element("transmitting_range");
    element.setText(Double.toString(TRANSMITTING_RANGE));
    config.add(element);

    /* Interference range */
    element = new Element("interference_range");
    element.setText(Double.toString(INTERFERENCE_RANGE));
    config.add(element);

    /* Transmission success probability */
    element = new Element("success_ratio_tx");
    element.setText(String.valueOf(SUCCESS_RATIO_TX));
    config.add(element);

    /* Reception success probability */
    element = new Element("success_ratio_rx");
    element.setText(String.valueOf(SUCCESS_RATIO_RX));
    config.add(element);

    return config;
  }

  @Override
  public boolean setConfigXML(Collection<Element> configXML, boolean visAvailable) {
    super.setConfigXML(configXML, visAvailable);
    for (Element element : configXML) {
      if (element.getName().equals("transmitting_range")) {
        TRANSMITTING_RANGE = Double.parseDouble(element.getText());
      }

      if (element.getName().equals("interference_range")) {
        INTERFERENCE_RANGE = Double.parseDouble(element.getText());
      }

      /* Backwards compatibility */
      if (element.getName().equals("success_ratio")) {
        SUCCESS_RATIO_TX = Double.parseDouble(element.getText());
        logger.warn("Loading old Cooja Config, XML element \"sucess_ratio\" parsed at \"sucess_ratio_tx\"");
      }

      if (element.getName().equals("success_ratio_tx")) {
        SUCCESS_RATIO_TX = Double.parseDouble(element.getText());
      }

      if (element.getName().equals("success_ratio_rx")) {
        SUCCESS_RATIO_RX = Double.parseDouble(element.getText());
      }
    }
    return true;
  }

}
