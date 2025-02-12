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

package org.contikios.cooja.dialogs;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.nio.file.Path;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;

import org.contikios.cooja.Cooja;
import org.contikios.cooja.contikimote.ContikiMoteType;
import org.contikios.cooja.contikimote.ContikiMoteType.NetworkStack;
import org.contikios.cooja.mote.BaseContikiMoteType;

/**
 * Contiki Mote Type compile dialog.
 *
 * @author Fredrik Osterlind
 */
public class ContikiMoteCompileDialog extends AbstractCompileDialog {

  public ContikiMoteCompileDialog(Cooja gui, ContikiMoteType moteType, BaseContikiMoteType.MoteTypeConfig cfg) {
    super(gui, moteType, cfg);
    // Add Contiki mote type specifics.
    // Communication stack.

    JLabel label = new JLabel("Default network stack header");
    label.setPreferredSize(LABEL_DIMENSION);
    final var headerTextField = new JTextField();
    headerTextField.setText(moteType.getNetworkStack().manualHeader);
    headerTextField.getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void insertUpdate(DocumentEvent e) {
        updateHeader();
      }
      @Override
      public void removeUpdate(DocumentEvent e) {
        updateHeader();
      }
      @Override
      public void changedUpdate(DocumentEvent e) {
        updateHeader();
      }
      private void updateHeader() {
        moteType.getNetworkStack().manualHeader = headerTextField.getText();
      }
    });
    final Box netStackHeaderBox = Box.createHorizontalBox();
    netStackHeaderBox.setAlignmentX(Component.LEFT_ALIGNMENT);
    netStackHeaderBox.add(label);
    netStackHeaderBox.add(Box.createHorizontalStrut(20));
    netStackHeaderBox.add(headerTextField);

    label = new JLabel("Default network stack");
    label.setPreferredSize(LABEL_DIMENSION);
    final var netStackComboBox = new JComboBox<>(NetworkStack.values());
    netStackComboBox.setSelectedItem(moteType.getNetworkStack());
    netStackComboBox.setEnabled(true);
    netStackComboBox.addActionListener(e -> {
      moteType.setNetworkStack((NetworkStack)netStackComboBox.getSelectedItem());
      netStackHeaderBox.setVisible(netStackComboBox.getSelectedItem() == NetworkStack.MANUAL);
      setDialogState(DialogState.SELECTED_SOURCE);
    });
    Box netStackBox = Box.createHorizontalBox();
    netStackBox.setAlignmentX(Component.LEFT_ALIGNMENT);
    netStackBox.add(label);
    netStackBox.add(Box.createHorizontalStrut(20));
    netStackBox.add(netStackComboBox);
    netStackHeaderBox.setVisible(netStackComboBox.getSelectedItem() == NetworkStack.MANUAL);

    // Advanced tab.
    Box box = Box.createVerticalBox();
    box.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
    box.add(netStackBox);
    box.add(netStackHeaderBox);
    box.add(Box.createVerticalGlue());
    JPanel container = new JPanel(new BorderLayout());
    container.add(BorderLayout.NORTH, box);
    tabbedPane.addTab("Advanced", null, new JScrollPane(container), "Advanced Contiki Mote Type settings");
    // Create new tab, fill with current environment data.
    final var model = new DefaultTableModel(new String[] { "Variable", "Value" }, 0);
    var table = new JTable(model);
    table.setDefaultEditor(Object.class, null);
    for (var entry : moteType.getCompilationEnvironment().entrySet()) {
      model.addRow(new Object[] { entry.getKey(), entry.getValue() });
    }
    JPanel panel = new JPanel(new BorderLayout());
    JButton button = new JButton("Change environment variables: Open external tools dialog");
    button.addActionListener(e -> {
      ExternalToolsDialog.showDialog();
      // Update data in the table.
      model.setRowCount(0);
      for (var entry : moteType.getCompilationEnvironment().entrySet()) {
        model.addRow(new Object[]{entry.getKey(), entry.getValue()});
      }
      // User might have changed compiler, force recompile.
      setDialogState(DialogState.SELECTED_SOURCE);
    });
    panel.add(BorderLayout.NORTH, button);
    panel.add(BorderLayout.CENTER, new JScrollPane(table));
    tabbedPane.addTab("Environment", null, panel, "Environment variables");
  }

  @Override
  public boolean canLoadFirmware(String name) {
    return false; // Always recompile, CoreComm needs fresh names.
  }

  @Override
  public String getDefaultCompileCommands(String name) {
      String save_command = getCompileCommands();
      if ( save_command.isEmpty() ) 
          save_command = super.getDefaultCompileCommands(name);

    /*"make clean TARGET=cooja\n" + */
    final String target = moteType.getExpectedFirmwareFile(name).getName();
    String command = setCommandTarget(save_command, target);

    final String newstack = ((ContikiMoteType) moteType).getNetworkStack().getHeaderFile(); 
    command = setNetStack(command, newstack);

    return command;
  }

  private
  String setNetStack(String command, String newstack) {
      final String netstack_patern = "NETSTACK_CONF_H=([^\\s,]*)";

      if (newstack == null) {
          //remove netstack
          command = command.replaceAll(netstack_patern, "");
          return command;
      }

      final String netstack_def = "NETSTACK_CONF_H=" + newstack; 
      final String netstack_cmd = " DEFINES=" + netstack_def;  

      // update netstack header
      if ( command.indexOf("NETSTACK_CONF_H=") > 0) {
          command = command.replaceAll(netstack_patern, netstack_def);
      }
      else
          command += netstack_cmd;
      return command;
  }

  private
  String setCommandTarget(String command, String target) {

      String target_cmd = "TARGET=cooja";
      
      int cmd_finish = command.lastIndexOf("make ");
      if (cmd_finish > 0) {
          // update old command with new source, and netstack
          final String target_patern = " ((\\S)*.cooja) "+target_cmd;
          String make = command.substring(cmd_finish);
          make = make.replaceAll(target_patern, " " + target + " "+target_cmd );

          return command.substring(0, cmd_finish) + make;
      }
      else {
          String new_command = Cooja.getExternalToolsSetting("PATH_MAKE") + " -j$(CPUS) "
                  + target + " "+target_cmd
                  ;
          return command + "\n"+new_command;
      }
  }
  
}
