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

package org.contikios.cooja.plugins;

import java.awt.BorderLayout;
import java.awt.Dimension;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Cooja;
import org.contikios.cooja.Mote;
import org.contikios.cooja.MotePlugin;
import org.contikios.cooja.PluginType;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.VisPlugin;
import org.contikios.cooja.motes.AbstractEmulatedMote;

/**
 * Mote information displays information about a given mote.
 *
 * @author Fredrik Osterlind
 */
@ClassDescription("Mote Information")
@PluginType(PluginType.PType.MOTE_PLUGIN)
public class MoteInformation extends VisPlugin implements MotePlugin {
  private final Mote mote;

  private final static int LABEL_WIDTH = 170;
  private final static int LABEL_HEIGHT = 20;
  private final static Dimension size = new Dimension(LABEL_WIDTH,LABEL_HEIGHT);
  
  /**
   * Create a new mote information window.
   *
   * @param m Mote
   * @param simulation Simulation
   * @param gui Simulator
   */
  public MoteInformation(Mote m, Simulation simulation, Cooja gui) {
    super("Mote Information (" + m + ")", gui);
    this.mote = m;

    JPanel mainPane = new JPanel();
    mainPane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    mainPane.setLayout(new BoxLayout(mainPane, BoxLayout.Y_AXIS));

    /* Mote type */
    var smallPane = new JPanel(new BorderLayout());
    var label = new JLabel("Mote type");
    label.setPreferredSize(size);
    smallPane.add(BorderLayout.WEST, label);
    label = new JLabel(mote.getType().getDescription());
    label.setPreferredSize(size);
    smallPane.add(BorderLayout.EAST, label);
    mainPane.add(smallPane);

    smallPane = new JPanel(new BorderLayout());
    label = new JLabel("Mote type identifier");
    label.setPreferredSize(size);
    smallPane.add(BorderLayout.WEST, label);
    label = new JLabel(mote.getType().getIdentifier());
    label.setPreferredSize(size);
    smallPane.add(BorderLayout.EAST, label);
    mainPane.add(smallPane);

    /* Mote interfaces */
    smallPane = new JPanel(new BorderLayout());
    label = new JLabel("Mote interfaces");
    label.setPreferredSize(size);
    smallPane.add(BorderLayout.WEST, label);
    label = new JLabel(mote.getInterfaces().getInterfaces().size() + " interfaces");
    label.setPreferredSize(size);
    smallPane.add(BorderLayout.EAST, label);
    mainPane.add(smallPane);

    smallPane = new JPanel(new BorderLayout());
    var button = new JButton("Mote interface viewer");
    button.setPreferredSize(size);
    button.addActionListener(e -> simulation.getCooja().tryStartPlugin(MoteInterfaceViewer.class, simulation, mote));
    smallPane.add(BorderLayout.EAST, button);
    mainPane.add(smallPane);
    
    /* CPU frequency */
    if (mote instanceof AbstractEmulatedMote<?, ?> emulatedMote) {
      smallPane = new JPanel(new BorderLayout());
      label = new JLabel("CPU frequency");
      label.setPreferredSize(size);
      smallPane.add(BorderLayout.WEST, label);
      var freq = emulatedMote.getCPUFrequency();
      label = new JLabel(freq < 0 ? "[unknown]" : emulatedMote.getCPUFrequency() + " Hz");
      label.setPreferredSize(size);
      smallPane.add(BorderLayout.EAST, label);
      mainPane.add(smallPane);
    }
    
    /* Remove button */
    smallPane = new JPanel(new BorderLayout());
    label = new JLabel("Remove mote");
    label.setPreferredSize(size);
    smallPane.add(BorderLayout.WEST, label);

    button = new JButton("Remove");
    button.setPreferredSize(size);
    button.addActionListener(e -> {
      /* TODO In simulation event (if running) */
      simulation.removeMote(MoteInformation.this.mote);
    });
    smallPane.add(BorderLayout.EAST, button);
    mainPane.add(smallPane);

    this.getContentPane().add(BorderLayout.CENTER,
        new JScrollPane(mainPane,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED));

    pack();
    setSize(new Dimension(getWidth()+15, getHeight()+15));
  }

  @Override
  public Mote getMote() {
    return mote;
  }
}
