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
 */

package org.contikios.cooja.corecomm;

import java.io.File;
import org.contikios.cooja.CoreComm;

/**
 * This class is part of the resources of Cooja and is used by CoreComm to generate LibN.java,
 * which contains the interface to Contiki-NG.
 *
 * @see CoreComm
 * @author Fredrik Osterlind
 */
public class CoreCommTemplate implements CoreComm {

  /**
   * Loads library libFile.
   *
   * @see CoreComm
   * @param libFile Library file
   */
  public CoreCommTemplate(File libFile) {
    System.load(libFile.getAbsolutePath());
    init();
  }

  public native void tick();
  protected native void init();
  public native void kill();
  public native void setReferenceAddress(long addr);
  public long getReferenceAddress() { return 0;}
  public native void getMemory(long rel_addr, int length, byte[] mem);
  public native void setMemory(long rel_addr, int length, byte[] mem);
}
