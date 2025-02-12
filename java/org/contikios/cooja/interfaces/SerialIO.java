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

package org.contikios.cooja.interfaces;

import org.contikios.cooja.*;

import org.contikios.cooja.interfaces.SerialPort;
import org.contikios.cooja.interfaces.Log;

/**
 * A Log represents a mote logging output. An implementation should notify all
 * observers whenever new logging output is available.
 *
 * @author Fredrik Osterlind
 */
@ClassDescription("Serial IO")
public abstract class SerialIO  extends Log //maybe better extends Log?  
	implements MoteInterface, SerialPort
{
      // SerialPort
	  public abstract void writeByte(byte b);
	  public abstract void writeArray(byte[] s);
	  public abstract void writeString(String s);

	  public abstract byte getLastSerialData();

	  // dummy omplementation
	  public byte[] getLastSerialBuf( int limit) {
		  byte[] b = new byte[1];
		  b[0] = getLastSerialData();
		  return b; 
	  }

	  public abstract void flushInput();

	  public abstract void close();

	  @Override
	  public void removed() {
	      close();
	  }
}
