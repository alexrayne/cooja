/*
 * Copyright (c) 2007, Swedish Institute of Computer Science.
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

package org.contikios.cooja.avrmote;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jdom.Element;

import org.contikios.cooja.AbstractionLevelDescription;
import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Cooja;
import org.contikios.cooja.Mote;
import org.contikios.cooja.MoteInterface;
import org.contikios.cooja.MoteType;
import org.contikios.cooja.ProjectConfig;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.avrmote.interfaces.MicaClock;
import org.contikios.cooja.avrmote.interfaces.MicaSerial;
import org.contikios.cooja.avrmote.interfaces.MicaZID;
import org.contikios.cooja.avrmote.interfaces.MicaZLED;
import org.contikios.cooja.avrmote.interfaces.MicaZRadio;
import org.contikios.cooja.dialogs.CompileContiki;
import org.contikios.cooja.dialogs.MessageList;
import org.contikios.cooja.dialogs.MessageContainer;
import org.contikios.cooja.interfaces.Mote2MoteRelations;
import org.contikios.cooja.interfaces.MoteAttributes;
import org.contikios.cooja.interfaces.Position;

/**
 * AVR-based MicaZ mote types emulated in Avrora.
 *
 * @author Joakim Eriksson, Fredrik Osterlind
 */
@ClassDescription("MicaZ mote")
@AbstractionLevelDescription("Emulated level")
public class MicaZMoteType implements MoteType {
  private static final Logger logger = LogManager.getLogger(MicaZMoteType.class);

  private String identifier = null;
  private String description = null;

  /* If source file is defined, the firmware is recompiled when loading simulations */
  private File fileFirmware = null;
  private File fileSource = null;
  private String compileCommands = null;

  private Class<? extends MoteInterface>[] moteInterfaceClasses = null;

  public String getIdentifier() {
    return identifier;
  }

  public void setIdentifier(String identifier) {
    this.identifier = identifier;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getCompileCommands() {
    return compileCommands;
  }

  public void setCompileCommands(String command) {
    this.compileCommands = command;
  }

  public File getContikiSourceFile() {
    return fileSource;
  }

  public File getContikiFirmwareFile() {
    return fileFirmware;
  }

  public void setContikiSourceFile(File file) {
    fileSource = file;
  }

  public void setContikiFirmwareFile(File file) {
    this.fileFirmware = file;
  }

  public Class<? extends MoteInterface>[] getMoteInterfaceClasses() {
    return moteInterfaceClasses;
  }

  public void setMoteInterfaceClasses(Class<? extends MoteInterface>[] classes) {
    moteInterfaceClasses = classes;
  }

  public final Mote generateMote(Simulation simulation) 
                              throws MoteType.MoteTypeCreationException 
  {
    MicaZMote mote = new MicaZMote(simulation, this);
    mote.initMote();
    return mote;
  }

  public JPanel getTypeVisualizer() {
    /* TODO Move to emulated layer */
    JPanel panel = new JPanel();
    JLabel label = new JLabel();
    JPanel smallPane;

    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

    // Identifier
    smallPane = new JPanel(new BorderLayout());
    label = new JLabel("Identifier");
    smallPane.add(BorderLayout.WEST, label);
    label = new JLabel(getIdentifier());
    smallPane.add(BorderLayout.EAST, label);
    panel.add(smallPane);

    // Description
    smallPane = new JPanel(new BorderLayout());
    label = new JLabel("Description");
    smallPane.add(BorderLayout.WEST, label);
    label = new JLabel(getDescription());
    smallPane.add(BorderLayout.EAST, label);
    panel.add(smallPane);

    /* Contiki source */
    smallPane = new JPanel(new BorderLayout());
    label = new JLabel("Contiki source");
    smallPane.add(BorderLayout.WEST, label);
    if (getContikiSourceFile() != null) {
      label = new JLabel(getContikiSourceFile().getName());
      label.setToolTipText(getContikiSourceFile().getPath());
    } else {
      label = new JLabel("[not specified]");
    }
    smallPane.add(BorderLayout.EAST, label);
    panel.add(smallPane);

    /* Contiki firmware */
    smallPane = new JPanel(new BorderLayout());
    label = new JLabel("Contiki firmware");
    smallPane.add(BorderLayout.WEST, label);
    label = new JLabel(getContikiFirmwareFile().getName());
    label.setToolTipText(getContikiFirmwareFile().getPath());
    smallPane.add(BorderLayout.EAST, label);
    panel.add(smallPane);

    /* Compile commands */
    smallPane = new JPanel(new BorderLayout());
    label = new JLabel("Compile commands");
    smallPane.add(BorderLayout.WEST, label);
    JTextArea textArea = new JTextArea(getCompileCommands());
    textArea.setEditable(false);
    textArea.setBorder(BorderFactory.createEmptyBorder());
    smallPane.add(BorderLayout.EAST, textArea);
    panel.add(smallPane);

    /* Icon */
    Icon moteTypeIcon = getMoteTypeIcon();
    if (moteTypeIcon != null) {
      smallPane = new JPanel(new BorderLayout());
      label = new JLabel(moteTypeIcon);
      smallPane.add(BorderLayout.CENTER, label);
      panel.add(smallPane);
    }

    panel.add(Box.createRigidArea(new Dimension(0, 5)));
    return panel;
  }

  public Icon getMoteTypeIcon() {
    /* TODO Add MicaZ icon */
    return null;
  }

  public ProjectConfig getConfig() {
    logger.warn("MicaZ mote type project config not implemented");
    return null;
  }

  public Collection<Element> getConfigXML(Simulation simulation) {
    ArrayList<Element> config = new ArrayList<Element>();

    Element element;

    // Identifier
    element = new Element("identifier");
    element.setText(getIdentifier());
    config.add(element);

    // Description
    element = new Element("description");
    element.setText(getDescription());
    config.add(element);

    // Source file
    if (fileSource != null) {
      element = new Element("source");
      File file = simulation.getCooja().createPortablePath(fileSource);
      element.setText(file.getPath().replaceAll("\\\\", "/"));
      config.add(element);
      element = new Element("commands");
      element.setText(compileCommands);
      config.add(element);
    }

    // Firmware file
    element = new Element("firmware");
    File file = simulation.getCooja().createPortablePath(fileFirmware);
    element.setText(file.getPath().replaceAll("\\\\", "/"));
    config.add(element);

    // Mote interfaces
    for (Class<? extends MoteInterface> moteInterface : getMoteInterfaceClasses()) {
      element = new Element("moteinterface");
      element.setText(moteInterface.getName());
      config.add(element);
    }

    return config;
  }

  public boolean setConfigXML(Simulation simulation,
      Collection<Element> configXML, boolean visAvailable)
  throws MoteTypeCreationException {

    ArrayList<Class<? extends MoteInterface>> intfClassList = new ArrayList<Class<? extends MoteInterface>>();
    for (Element element : configXML) {
      String name = element.getName();

      if (name.equals("identifier")) {
        identifier = element.getText();
      } else if (name.equals("description")) {
        description = element.getText();
      } else if (name.equals("source")) {
        fileSource = new File(element.getText());
        if (!fileSource.exists()) {
          fileSource = simulation.getCooja().restorePortablePath(fileSource);
        }
      } else if (name.equals("commands")) {
        compileCommands = element.getText();
      } else if (name.equals("firmware")) {
        fileFirmware = new File(element.getText());
        if (!fileFirmware.exists()) {
          fileFirmware = simulation.getCooja().restorePortablePath(fileFirmware);
        }
      } else if (name.equals("moteinterface")) {
        String intfClass = element.getText().trim();

        /* Backwards compatibility: se.sics -> org.contikios */
        if (intfClass.startsWith("se.sics")) {
          intfClass = intfClass.replaceFirst("se\\.sics", "org.contikios");
        }

        Class<? extends MoteInterface> moteInterfaceClass =
          simulation.getCooja().tryLoadClass(this, MoteInterface.class, intfClass);

        if (moteInterfaceClass == null) {
          logger.warn("Can't find mote interface class: " + intfClass);
        } else {
          intfClassList.add(moteInterfaceClass);
        }
      } else {
        logger.fatal("Unrecognized entry in loaded configuration: " + name);
        throw new MoteTypeCreationException(
            "Unrecognized entry in loaded configuration: " + name);
      }
    }

    Class<? extends MoteInterface>[] intfClasses = intfClassList.toArray(new Class[0]);

    setMoteInterfaceClasses(intfClasses);

    if (fileFirmware == null || fileSource == null) {
      throw new MoteTypeCreationException("Either source or firmware not specified");
    }

    return configureAndInit(Cooja.getTopParentContainer(), simulation, visAvailable);
  }

  public boolean configureAndInit(Container parentContainer, Simulation simulation, boolean visAvailable)
  throws MoteTypeCreationException {

    /* If visualized, show compile dialog and let user configure */
    if (visAvailable) {

      /* Create unique identifier */
      if (getIdentifier() == null) {
        int counter = 0;
        boolean identifierOK = false;
        while (!identifierOK) {
          identifierOK = true;

          counter++;
          setIdentifier("micaz" + counter);

          for (MoteType existingMoteType : simulation.getMoteTypes()) {
            if (existingMoteType == this) {
              continue;
            }
            if (existingMoteType.getIdentifier().equals(getIdentifier())) {
              identifierOK = false;
              break;
            }
          }
        }
      }

      /* Create initial description */
      if (getDescription() == null) {
        setDescription("MicaZ Mote Type #" + getIdentifier());
      }

      return MicaZCompileDialog.showDialog(parentContainer, simulation, this);
    }

    /* Not visualized: Compile Contiki immediately */
    if (getIdentifier() == null) {
      throw new MoteTypeCreationException("No identifier");
    }

    final MessageList compilationOutput = MessageContainer.createMessageList(visAvailable);

    if (getCompileCommands() != null) {
      /* Handle multiple compilation commands one by one */
      String[] arr = getCompileCommands().split("\n");
      for (String cmd: arr) {
        if (cmd.trim().isEmpty()) {
          continue;
        }

        try {
          CompileContiki.compile(
              cmd,
              null,
              getContikiSourceFile().getParentFile(),
              null,
              null,
              compilationOutput,
              true
          );
        } catch (Exception e) {
          MoteTypeCreationException newException =
            new MoteTypeCreationException("Mote type creation failed: " + e.getMessage());
          newException = (MoteTypeCreationException) newException.initCause(e);
          newException.setCompilationOutput(compilationOutput);

          /* Print last 10 compilation errors to console */
          MessageContainer[] messages = compilationOutput.getMessages();
          for (int i=messages.length-10; i < messages.length; i++) {
            if (i < 0) {
              continue;
            }
            logger.fatal(">> " + messages[i]);
          }

          logger.fatal("Compilation error: " + e.getMessage());
          throw newException;
        }
      }
    }

    if (getContikiFirmwareFile() == null ||
        !getContikiFirmwareFile().exists()) {
      throw new MoteTypeCreationException("Contiki firmware file does not exist: " + getContikiFirmwareFile());
    }
    return true;
  }

  public Class<? extends MoteInterface>[] getAllMoteInterfaceClasses() {
    return new Class[] {
        Position.class,
        MicaZID.class,
        MicaZLED.class,
        MicaZRadio.class,
        MicaClock.class,
        MicaSerial.class,
        Mote2MoteRelations.class,
        MoteAttributes.class
    };
  }

  public File getExpectedFirmwareFile(File source) {
    File parentDir = source.getParentFile();
    String sourceNoExtension = source.getName().substring(0, source.getName().length()-2);

    return new File(parentDir, sourceNoExtension + ".elf");
  }

}
