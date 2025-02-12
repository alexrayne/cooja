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

package org.contikios.cooja.contikimote.interfaces;

import java.awt.EventQueue;
import java.util.LinkedHashMap;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.contikios.cooja.Cooja;
import org.contikios.cooja.Mote;

import org.contikios.cooja.interfaces.MoteID;
import org.contikios.cooja.mote.memory.VarMemory;

/**
 * Mote ID interface: 'node_id'.
 * <p>
 * Contiki variables:
 * <ul>
 * <li>int simMoteID
 * <li>char simMoteIDChanged
 * </ul>
 *
 * This interface also seeds the Contiki random generator: 'random_init()'.
 * <p>
 *
 * This observable notifies observers when the mote ID is set or altered.
 *
 * @author Fredrik Osterlind
 */
public class ContikiMoteID implements MoteID {
  private final VarMemory moteMem;

  private int moteID;

  private final Mote mote;
  private final LinkedHashMap<JPanel, JLabel> labels = new LinkedHashMap<>();

  /**
   * Creates an interface to the mote ID at mote.
   *
   * @param mote Mote

   * @see Mote
   * @see org.contikios.cooja.MoteInterfaceHandler
   */
  public ContikiMoteID(Mote mote) {
    this.mote = mote;
    this.moteMem = new VarMemory(mote.getMemory());
  }

  @Override
  public int getMoteID() {
    return moteID;
  }

  @Override
  public void setMoteID(int newID) {
    moteID = newID;
    moteMem.setIntValueOf("simMoteID", moteID);
    moteMem.setByteValueOf("simMoteIDChanged", (byte) 1);
    moteMem.setIntValueOf("simRandomSeed", (int) (mote.getSimulation().getRandomSeed() + newID));
    if (Cooja.isVisualized()) {
      EventQueue.invokeLater(() -> {
        for (var label : labels.values()) {
          label.setText("Mote ID: " + moteID);
        }
      });
    }
  }

  @Override
  public JPanel getInterfaceVisualizer() {
    JPanel panel = new JPanel();
    final JLabel idLabel = new JLabel();

    idLabel.setText("Mote ID: " + moteID);

    panel.add(idLabel);
    labels.put(panel, idLabel);
    return panel;
  }

  @Override
  public void releaseInterfaceVisualizer(JPanel panel) {
    labels.remove(panel);
  }
}
