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
import org.jdom2.Element;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.contikios.cooja.interfaces.PolledAfterActiveTicks;
import org.contikios.cooja.interfaces.PolledAfterAllTicks;
import org.contikios.cooja.interfaces.PolledBeforeActiveTicks;
import org.contikios.cooja.interfaces.PolledBeforeAllTicks;
import org.contikios.cooja.MoteInterface;
import org.contikios.cooja.MoteInterfaceHandler;
import org.contikios.cooja.MoteType;
import org.contikios.cooja.mote.memory.SectionMoteMemory;
import org.contikios.cooja.Simulation;
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
public class ContikiMote extends AbstractWakeupMote<ContikiMoteType, SectionMoteMemory> {
  private static final Logger logger = LoggerFactory.getLogger(ContikiMote.class);

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
    super(moteType, moteType.createInitialMemory(), sim);
    moteInterfaces = new MoteInterfaceHandler(this);
    for (var intf : moteInterfaces.getInterfaces()) {
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

  public void setInterfaces(MoteInterfaceHandler newInterfaces) {
      moteInterfaces = newInterfaces;
  }

  public void setMemory(SectionMoteMemory memory) {
      moteMemory = memory;
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
    // (Jan 2023, Java 17/IntelliJ): Keep the interface actions in explicit for-loops,
    // so costs are clearly attributed in performance profiles.
    for (int i = 0; i < polledBeforeActive.size(); ++i) {
        var moteInterface = polledBeforeActive.get(i);
        if (moteInterface != null)
            moteInterface.doActionsBeforeTick();
    }
    for (int i = 0; i < polledBeforePassive.size(); ++i) {
      var moteInterface = polledBeforePassive.get(i);
      if (moteInterface != null)
          moteInterface.doActionsBeforeTick();
    }

    /* Check if pre-boot time */
    var moteTime = moteInterfaces.getClock().getTime();
    if (moteTime < 0) {
      scheduleNextWakeup(simTime + Math.abs(moteTime));
      return;
    }

    /* Copy mote memory to Contiki */
    moteType.setCoreMemory(moteMemory);

    /* Handle a single Contiki events */
    try {
        execute_state = MoteState.STATE_EXEC;
        moteType.tick();
        execute_state = MoteState.STATE_OK;
    } 
    catch (RuntimeException e) {
        execute_state = MoteState.STATE_HANG;
        //coffeecatch_throw_exception rises Error
        String dump = StringUtils.dumpStackTrace(e);
        logger.error( "mote" + getID() 
                      + "crashed with:" + e.toString()
                      + dump 
                    );
    }
    catch (Exception e) {
        execute_state = MoteState.STATE_HANG;
        //coffeecatch_throw_exception rises Error
        String dump = StringUtils.dumpStackTrace(e);
        logger.error( "mote" + getID() 
                      + "crashed with:" + e.toString()
                      + dump 
                    );
    }
    catch (Error e) {
        execute_state = MoteState.STATE_HANG;
        //coffeecatch_throw_exception rises Error
        String dump = StringUtils.dumpStackTrace(e);
        logger.error( "mote" + getID() 
                      + "crashed with:" + e.toString()
                      + dump 
                    );
    }

    /* Copy mote memory from Contiki */
    moteType.getCoreMemory(moteMemory);

    moteMemory.pollForMemoryChanges();
    int sz = polledAfterActive.size();
    for (int i =0; i < sz; ++i){
        var moteInterface = polledAfterActive.get(i);
        if (moteInterface != null)
            moteInterface.doActionsAfterTick();
    }
    
    sz = polledAfterPassive.size();
    for (int i =0; i < sz; ++i){
        var moteInterface = polledAfterPassive.get(i);
        if (moteInterface != null)
            moteInterface.doActionsAfterTick();
    }

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
          moteType.kill();
      }
  }

  @Override
  public boolean setConfigXML(Simulation sim, Collection<Element> configXML, boolean vis) 
          throws MoteType.MoteTypeCreationException 
  {
    if (!confInterfaces(sim, configXML, vis))
        return false;

          //TODO new CCOJA revisions may have not investigated interfaces
          //     ignore this miss, to allow load later projects
          //return false;
    if ( getInterfaces().getInterfaceOfType(ContikiRS232.class) != null ) 
    if ( getInterfaces().getInterfaceOfType(ContikiLog.class) == null )
    {
        //looks tis is old project, that use ContikiRS232 combined SerialPort with Log
        //So load ContikiLog for this project, since now it deployed from ContikiRS232
        logger.info("update ContikiLog mote"+ getID() + " from old project");
        addInterface("org.contikios.cooja.contikimote.interfaces.ContikiLog");
    }

    if ( moteMemory.symbolExists("simRadioHWOn") )
    if ( moteInterfaces.getInterfaceOfType(ContikiRadio.class) == null )
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
        logger.error("Could not append mote interface class: " + typeName);
        return null;
      }
      else {
          logger.info("Append mote interface class: " + typeName);
          try {
          MoteInterface intf = moteInterfaces.generateInterface(moteInterfaceClass, this);
          moteInterfaces.addInterface(intf);
          return intf;
          }
          catch (MoteType.MoteTypeCreationException e) {
              return null;
          }
      }
  }
  
  private String moteName;
  private int    moteNameID = -1;
  
  @Override
  public String toString() {
    int id = getID();
    if (moteNameID == id)
        return moteName;
    
    moteName = "Contiki" + id;
    moteNameID = id;
    return moteName;
  }
}
