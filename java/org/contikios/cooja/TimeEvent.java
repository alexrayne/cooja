/*
 * Copyright (c) 2008, Swedish Institute of Computer Science.
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

package org.contikios.cooja;

/**
 * @author Joakim Eriksson (ported to COOJA by Fredrik Osterlind)
 *         Matthew Bradbury <matt-bradbury@live.co.uk>
 */
public abstract class TimeEvent {

  private boolean isQueued;
  private boolean isScheduled;

  public TimeEvent() {
  }

  public boolean isScheduled() {
    return isScheduled;
  }

  public boolean isQueued() {
    return isQueued;
  }

  public void setScheduled(boolean queued) {
    isScheduled = queued;
    isQueued = queued;
  }

  public void remove() {
    isScheduled = false;
  }

  public abstract void execute(long t);
  //kills event execution - signal to execution thread  
  public void kill() {};

  /**
   * there always used TimeEvent(0), this constructor now useless
   *
   * @deprecated use {TimeEvent()} instead.  
   */
  @Deprecated
  public TimeEvent(long time) {
  }
  @Deprecated
  public TimeEvent(long time, String name) {
  }
}
