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

package org.contikios.cooja.contikimote;

import java.util.ArrayList;
import java.util.Collection;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.contikios.cooja.interfaces.PolledAfterActiveTicks;
import org.contikios.cooja.interfaces.PolledAfterAllTicks;
import org.contikios.cooja.interfaces.PolledBeforeActiveTicks;
import org.contikios.cooja.interfaces.PolledBeforeAllTicks;
import org.jdom2.Element;
import org.contikios.cooja.Mote;
import org.contikios.cooja.MoteInterface;
import org.contikios.cooja.MoteInterfaceHandler;
import org.contikios.cooja.MoteType;
import org.contikios.cooja.mote.memory.SectionMoteMemory;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.mote.memory.MemoryInterface;
import org.contikios.cooja.motes.AbstractWakeupMote;
import org.contikios.cooja.util.StringUtils;
import java.lang.Throwable;
import java.lang.Exception;
import java.lang.Error;
import java.lang.RuntimeException;


import org.contikios.cooja.contikimote.interfaces.ContikiLog;
import org.contikios.cooja.contikimote.interfaces.ContikiRS232;

import org.contikios.cooja.contikimote.interfaces.ContikiRadio;

/**
 * A Contiki mote executes an actual Contiki system via
 * a loaded shared library and JNI.
 * It contains a section mote memory, a mote interface handler and a
 * Contiki mote type.
 * <p>
 * The mote type is responsible for the connection to the loaded
 * Contiki system.
 * <p>
 * When ticked a Contiki mote polls all interfaces, copies the mote
 * memory to the core, lets the Contiki system handle one event,
 * fetches the updated memory and finally polls all interfaces again.
 *
 * @author      Fredrik Osterlind
 */
public class ContikiMote extends AbstractWakeupMote implements Mote {
  private static final Logger logger = LogManager.getLogger(ContikiMote.class);

  private final ContikiMoteType myType;
  private SectionMoteMemory myMemory;
  private MoteInterfaceHandler myInterfaceHandler;
  public enum MoteState{ STATE_OK, STATE_EXEC, STATE_HANG};
  public MoteState execute_state = MoteState.STATE_OK;

  private final ArrayList<PolledBeforeActiveTicks> polledBeforeActive = new ArrayList<>();
  private final ArrayList<PolledAfterActiveTicks> polledAfterActive = new ArrayList<>();
  private final ArrayList<PolledBeforeAllTicks> polledBeforePassive = new ArrayList<>();
  private final ArrayList<PolledAfterAllTicks> polledAfterPassive = new ArrayList<>();

  /**
   * Creates a new mote of given type.
   * Both the initial mote memory and the interface handler
   * are supplied from the mote type.
   *
   * @param moteType Mote type
   * @param sim Mote's simulation
   */
  protected ContikiMote(ContikiMoteType moteType, Simulation sim) throws MoteType.MoteTypeCreationException {
    super(sim);
    this.myType = moteType;
    this.myMemory = moteType.createInitialMemory();
    this.myInterfaceHandler = new MoteInterfaceHandler(this, moteType.getMoteInterfaceClasses());
    for (var intf : myInterfaceHandler.getInterfaces()) {
      if (intf instanceof PolledBeforeActiveTicks) {
        polledBeforeActive.add( (PolledBeforeActiveTicks)intf );
      }
      if (intf instanceof PolledAfterActiveTicks) {
        polledAfterActive.add( (PolledAfterActiveTicks)intf );
      }
      if (intf instanceof PolledBeforeAllTicks) {
        polledBeforePassive.add( (PolledBeforeAllTicks)intf );
      }
      if (intf instanceof PolledAfterAllTicks) {
        polledAfterPassive.add( (PolledAfterAllTicks)intf );
      }
    }
    requestImmediateWakeup();
  }

  @Override
  public int getID() {
    return myInterfaceHandler.getMoteID().getMoteID();
  }

  @Override
  public MoteInterfaceHandler getInterfaces() {
    return myInterfaceHandler;
  }

  public void setInterfaces(MoteInterfaceHandler newInterfaces) {
    myInterfaceHandler = newInterfaces;
  }

  @Override
  public MemoryInterface getMemory() {
    return myMemory;
  }

  public void setMemory(SectionMoteMemory memory) {
    myMemory = memory;
  }

  @Override
  public MoteType getType() {
    return myType;
  }

  /**
   * Ticks mote once. This is done by first polling all interfaces
   * and letting them act on the stored memory before the memory is set. Then
   * the mote is ticked, and the new memory is received.
   * Finally, all interfaces are allowing to act on the new memory in order to
   * discover relevant changes. This method also schedules the next mote tick time
   * depending on Contiki specifics; pending timers and polled processes.
   *
   * @param simTime Current simulation time
   */
  @Override
  public void execute(long simTime) {
    if (execute_state != MoteState.STATE_OK)
        return;
    /* Poll mote interfaces */
    polledBeforeActive.forEach(PolledBeforeActiveTicks::doActionsBeforeTick);
    polledBeforePassive.forEach(PolledBeforeAllTicks::doActionsBeforeTick);

    /* Check if pre-boot time */
    var moteTime = myInterfaceHandler.getClock().getTime();
    if (moteTime < 0) {
      scheduleNextWakeup(simTime + Math.abs(moteTime));
      return;
    }

    /* Copy mote memory to Contiki */
    myType.setCoreMemory(myMemory);

    /* Handle a single Contiki events */
    try {
        execute_state = MoteState.STATE_EXEC;
        myType.tick();
        execute_state = MoteState.STATE_OK;
    } 
    catch (RuntimeException e) {
        execute_state = MoteState.STATE_HANG;
        //coffeecatch_throw_exception rises Error
        String dump = StringUtils.dumpStackTrace(e);
        logger.fatal( "mote" + getID() 
                      + "crashed with:" + e.toString()
                      + dump 
                    );
    }
    catch (Exception e) {
        execute_state = MoteState.STATE_HANG;
        //coffeecatch_throw_exception rises Error
        String dump = StringUtils.dumpStackTrace(e);
        logger.fatal( "mote" + getID() 
                      + "crashed with:" + e.toString()
                      + dump 
                    );
    }
    catch (Error e) {
        execute_state = MoteState.STATE_HANG;
        //coffeecatch_throw_exception rises Error
        String dump = StringUtils.dumpStackTrace(e);
        logger.fatal( "mote" + getID() 
                      + "crashed with:" + e.toString()
                      + dump 
                    );
    }

    /* Copy mote memory from Contiki */
    myType.getCoreMemory(myMemory);

    /* Poll mote interfaces */
    myMemory.pollForMemoryChanges();
    polledAfterActive.forEach(PolledAfterActiveTicks::doActionsAfterTick);
    polledAfterPassive.forEach(PolledAfterAllTicks::doActionsAfterTick);

    if (execute_state != MoteState.STATE_OK) {
        simulation.stopSimulation();
        logger.warn( "stop simulation by hang of mote"+getID() );
        // do not remove mote, just make it hung 
        //simulation.removeMote(this);
    }
  }

  /**
   * try abort mote executing thread
   * */
  @Override
  public void kill() {
      if (execute_state == MoteState.STATE_EXEC) {
          logger.warn( "killing mote"+getID() );
          myType.kill();
      }
  }

  /**
   * Returns the current Contiki mote config represented by XML elements.
   * This config also includes all mote interface configs.
   *
   * @return Current simulation config
   */
  @Override
  public Collection<Element> getConfigXML() {
    ArrayList<Element> config = new ArrayList<>();

    /* Mote interfaces */
    for (MoteInterface moteInterface: getInterfaces().getInterfaces()) {
      var element = new Element("interface_config");
      element.setText(moteInterface.getClass().getName());

      Collection<Element> interfaceXML = moteInterface.getConfigXML();
      if (interfaceXML != null) {
        element.addContent(interfaceXML);
        config.add(element);
      }
    }

    return config;
  }

  @Override
  public boolean setConfigXML(Simulation simulation, Collection<Element> configXML, boolean visAvailable) {
    for (Element element: configXML) {
      String name = element.getName();

      if (name.equals("interface_config")) {
        String intfClass = element.getText().trim();
        var moteInterfaceClass = MoteInterfaceHandler.getInterfaceClass(simulation.getCooja(), this, intfClass);
        if (moteInterfaceClass == null) {
          logger.fatal("Could not load mote"+ getID() +" interface class: " + intfClass);
          continue;
          //TODO new CCOJA revisions may have not investigated interfaces
          //     ignore this miss, to allow load later projects
          //return false;
        }

        MoteInterface moteInterface = myInterfaceHandler.getInterfaceOfType(moteInterfaceClass);
        if (moteInterface != null) {
          moteInterface.setConfigXML(element.getChildren(), visAvailable);
        } else {
          logger.warn("Can't restore configuration for non-existing interface: " + moteInterfaceClass.getName());
        }
      }
    }

    if ( getInterfaces().getInterfaceOfType(ContikiRS232.class) != null ) 
    if ( getInterfaces().getInterfaceOfType(ContikiLog.class) == null )
    {
        //looks tis is old project, that use ContikiRS232 combined SerialPort with Log
        //So load ContikiLog for this project, since now it deployed from ContikiRS232
        logger.info("update ContikiLog mote"+ getID() + " from old project");
        addInterface("org.contikios.cooja.contikimote.interfaces.ContikiLog");
    }

    if (myMemory.symbolExists("simRadioHWOn"))
    if ( getInterfaces().getInterfaceOfType(ContikiRadio.class) == null )
    {
        /* if firmware have radio, should provide it, 
         * since radio firmware have blocking operations*/
        logger.info("mote"+ getID() + " firmware have radio");
        addInterface("org.contikios.cooja.contikimote.interfaces.ContikiRadio");
    }

    requestImmediateWakeup();
    return true;
  }

  public
  MoteInterface addInterface(final String typeName) {
      //looks tis is old project, that use ContikiRS232 combined SerialPort with Log
      //So load ContikiLog for this project, since now it deployed from ContikiRS232
      Class<? extends MoteInterface> moteInterfaceClass =
              simulation.getCooja().tryLoadClass(this, MoteInterface.class, typeName);

      if (moteInterfaceClass == null) {
        logger.fatal("Could not append mote interface class: " + typeName);
        return null;
      }
      else {
          logger.info("Append mote interface class: " + typeName);
          try {
          MoteInterface intf = MoteInterface.generateInterface(moteInterfaceClass, this);
          myInterfaceHandler.addInterface(intf);
          return intf;
          }
          catch (MoteType.MoteTypeCreationException e) {
              return null;
          }
      }
  }

  @Override
  public String toString() {
    return "Contiki " + getID();
  }

}
