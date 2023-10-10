/*
 * Copyright (c) 2012, Swedish Institute of Computer Science.
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

package org.contikios.cooja.plugins;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemListener;
import java.awt.event.ItemEvent;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.JToolBar;
import javax.swing.JToggleButton;
import javax.swing.JRadioButton;

import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Cooja;
import org.contikios.cooja.HasQuickHelp;
import org.contikios.cooja.PluginType;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.VisPlugin;
import org.contikios.cooja.util.EventTriggers;

/**
 * Control panel for starting and pausing the current simulation.
 *
 * @author Fredrik Osterlind
 */
@ClassDescription("Simulation control")
@PluginType(PluginType.PType.SIM_STANDARD_PLUGIN)
public class SimControl extends VisPlugin implements HasQuickHelp {
  private static final int LABEL_UPDATE_INTERVAL = 150;

  private final Simulation simulation;

  private final JButton startButton;
  private final JButton stopButton;
  private final JLabel simulationTime;
  private final JLabel simulationSpeedup;

  private long lastSimulationTimeTimestamp;
  private long lastSystemTimeTimestamp;

  /** Listener for the toolbar. */
  private ToolbarListener toolbarListener;
  private JToolBar        toolBar;
  
  /**
   * Create a new simulation control panel.
   *
   * @param simulation Simulation to control
   */
  public SimControl(Simulation simulation, Cooja gui) {
    super("Simulation control", gui);
    this.simulation = simulation;

    /* Menus */
    JMenuBar menuBar = new JMenuBar();
    JMenu runMenu = new JMenu("Run");
    JMenu speedMenu = new JMenu("Speed limit");

    menuBar.add(runMenu);
    menuBar.add(speedMenu);
    this.setJMenuBar(menuBar);

    runMenu.add(new JMenuItem(startAction));
    runMenu.add(new JMenuItem(stopAction));
    runMenu.add(new JMenuItem(stepAction));
    runMenu.add(new JMenuItem(reloadAction));

    ButtonGroup speedlimitButtonGroup = new ButtonGroup();
    JRadioButtonMenuItem limitMenuItemNo = new JRadioButtonMenuItem(
        new ChangeMaxSpeedLimitAction("No speed limit", null));
    speedlimitButtonGroup.add(limitMenuItemNo);
    speedMenu.add(limitMenuItemNo);
    JRadioButtonMenuItem limitMenuItem1 = new JRadioButtonMenuItem(
        new ChangeMaxSpeedLimitAction("1%", 0.01));
    speedlimitButtonGroup.add(limitMenuItem1);
    speedMenu.add(limitMenuItem1);
    JRadioButtonMenuItem limitMenuItem2 = new JRadioButtonMenuItem(
        new ChangeMaxSpeedLimitAction("10%", 0.10));
    speedlimitButtonGroup.add(limitMenuItem2);
    speedMenu.add(limitMenuItem2);
    JRadioButtonMenuItem limitMenuItem3 = new JRadioButtonMenuItem(
            new ChangeMaxSpeedLimitAction("100%", 1.0));
        speedlimitButtonGroup.add(limitMenuItem3);
        speedMenu.add(limitMenuItem3);
        JRadioButtonMenuItem limitMenuItem200 = new JRadioButtonMenuItem(
                new ChangeMaxSpeedLimitAction("200%", 2.0));
            speedlimitButtonGroup.add(limitMenuItem200);
            speedMenu.add(limitMenuItem200);
    JRadioButtonMenuItem limitMenuItem4 = new JRadioButtonMenuItem(
        new ChangeMaxSpeedLimitAction("1000%", 10.0));
    speedlimitButtonGroup.add(limitMenuItem4);
    speedMenu.add(limitMenuItem4);

    if (simulation.getSpeedLimit() == null) {
      limitMenuItemNo.setSelected(true);
    } else if (simulation.getSpeedLimit() == 0.01) {
      limitMenuItem1.setSelected(true);
    } else if (simulation.getSpeedLimit() == 0.10) {
      limitMenuItem2.setSelected(true);
    } else if (simulation.getSpeedLimit() == 1.0) {
        limitMenuItem3.setSelected(true);
    } else if (simulation.getSpeedLimit() == 2.0) {
        limitMenuItem200.setSelected(true);
    } else if (simulation.getSpeedLimit() == 10) {
      limitMenuItem4.setSelected(true);
    }

    /* Container */
    JPanel smallPanel;
    JPanel controlPanel = new JPanel();
    controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.Y_AXIS));

    getContentPane().add(controlPanel, BorderLayout.NORTH);

    /* Control buttons */
    smallPanel = new JPanel();
    smallPanel.setLayout(new BoxLayout(smallPanel, BoxLayout.X_AXIS));
    smallPanel.setBorder(BorderFactory.createEmptyBorder(10, 5, 10, 5));

    smallPanel.add(startButton = new JButton(startAction));
    smallPanel.add(stopButton = new JButton(stopAction));
    smallPanel.add(new JButton(stepAction));
    smallPanel.add(new JButton(breakAction));
    smallPanel.add(new JButton(reloadAction));

    smallPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
    controlPanel.add(smallPanel);

    smallPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
    controlPanel.add(smallPanel);

    /* Time label */
    smallPanel = new JPanel();
    smallPanel.setLayout(new BoxLayout(smallPanel, BoxLayout.X_AXIS));
    smallPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 0, 5));

    JLabel label = new JLabel("?");
    smallPanel.add(label);
    simulationTime = label;

    smallPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
    controlPanel.add(smallPanel);

    /* Simulation speed label */
    smallPanel = new JPanel();
    smallPanel.setLayout(new BoxLayout(smallPanel, BoxLayout.X_AXIS));
    smallPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 0, 5));

    label = new JLabel("?");
    smallPanel.add(label);
    simulationSpeedup = label;

    smallPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
    controlPanel.add(smallPanel);

    pack();
    
    this.createToolBar();

    this.lastSystemTimeTimestamp = System.currentTimeMillis();
    this.lastSimulationTimeTimestamp = 0;

    /* Observe current simulation */
    simulation.getSimulationStateTriggers().addTrigger(this
            , (op, sim) ->{ SwingUtilities.invokeLater(() -> updateValues(op));}
            );
    /* Set initial values */
    updateValues(EventTriggers.Operation.STOP);
    
    /* XXX HACK: here we set the position and size of the window when it
     * appears on a blank simulation screen. */
    this.setLocation(400, 0);
    this.setSize(280, 160);
    
    this.setIconifiable(true);                 // minimize
    
    /* Update current time label when simulation is running */
    if (simulation.isRunning()) {
      updateLabelTimer.start();
    }
    
  }

  public void createToolBar()
  {
      // Create simulation control toolbar.
      toolBar = new JToolBar("Simulation Control");
      
      var startButton = new JToggleButton("Start/Pause");
      startButton.setText("Start/Pause");
      startButton.setToolTipText("Start");
      toolBar.add(startButton);
      
      var stepButton = new JButton(stepAction);
      stepButton.setToolTipText("Step");
      toolBar.add(stepButton);

      var breakButton = new JButton(breakAction);
      breakButton.setToolTipText("Break");
      toolBar.add(breakButton);

      var reloadButton = new JButton(reloadAction);
      reloadButton.setToolTipText("Reload");
      toolBar.add(reloadButton);

      toolBar.addSeparator();
      toolBar.add(new JLabel("Speed limit:"));
      
      var pane = new JPanel(new GridBagLayout());
      var group = new ButtonGroup();
      var radioConstraints = new GridBagConstraints();
      radioConstraints.fill = GridBagConstraints.HORIZONTAL;

      var slowCrawlSpeedButton = new JRadioButton("0.01X");
      slowCrawlSpeedButton.setToolTipText("1%");
      pane.add(slowCrawlSpeedButton, radioConstraints);
      group.add(slowCrawlSpeedButton);
      
      var crawlSpeedButton = new JRadioButton("0.1X");
      crawlSpeedButton.setToolTipText("10%");
      pane.add(crawlSpeedButton, radioConstraints);
      group.add(crawlSpeedButton);
      
      var normalSpeedButton = new JRadioButton("1X");
      normalSpeedButton.setToolTipText("100%");
      pane.add(normalSpeedButton, radioConstraints);
      group.add(normalSpeedButton);
      
      var doubleSpeedButton = new JRadioButton("2X");
      doubleSpeedButton.setToolTipText("200%");
      pane.add(doubleSpeedButton, radioConstraints);
      group.add(doubleSpeedButton);
      
      var superSpeedButton = new JRadioButton("20X");
      superSpeedButton.setToolTipText("2000%");
      pane.add(superSpeedButton, radioConstraints);
      group.add(superSpeedButton);
      
      var unlimitedSpeedButton = new JRadioButton("Unlimited");
      superSpeedButton.setToolTipText("Unlimited");
      pane.add(unlimitedSpeedButton, radioConstraints);
      group.add(unlimitedSpeedButton);
      
      // The system and Nimbus look and feels size the pane differently. Clamp the size
      // to prevent the time label to end up to the far right on the toolbar.
      pane.setMaximumSize(pane.getPreferredSize());
      toolBar.add(pane);
      toolBar.addSeparator();
      final var simulationTime = new JLabel("Time:");
      toolBar.add(simulationTime);
      toolBar.setMinimumSize(toolBar.getSize());
      
      var desktop = gui.getTopParentContainer().getContentPane(); 
      desktop.add(toolBar, BorderLayout.PAGE_START);

      toolbarListener = new ToolbarListener() {
        private final Timer updateTimer = new Timer(500, e -> {
          final var sim = getSimulation();
          simulationTime.setText(getTimeString(sim));
        });

        @Override
        public void itemStateChanged(ItemEvent e) {
          final var sim = getSimulation();
          // Simulation is null when resetting the state of startButton after closing simulation.
          if (sim == null) {
            return;
          }
          switch (e.getStateChange()) {
            case ItemEvent.SELECTED:
              sim.startSimulation();
              stepButton.setEnabled(false);
              break;
            case ItemEvent.DESELECTED:
              sim.stopSimulation();
              stepButton.setEnabled(true);
              break;
          }
          updateToolbar(false);
        }

        @Override
        public void updateToolbar(boolean stoppedSimulation) {
          // Ensure the start button can be pressed if this update was from stopping the simulation.
          if (stoppedSimulation) {
            startButton.setSelected(false);
            updateTimer.stop();
          }
          
          final var sim = getSimulation();
          simulationTime.setText(getTimeString(sim));
          
          var hasSim = sim != null;
          startButton.setEnabled(hasSim);
          startButton.setEnabled(hasSim && sim.isRunnable());
          startButton.setSelected(hasSim && sim.isRunning());
          stepButton.setEnabled(hasSim && !startButton.isSelected());
          reloadButton.setEnabled(hasSim);
          
          slowCrawlSpeedButton.setEnabled(hasSim);
          crawlSpeedButton.setEnabled(hasSim);
          normalSpeedButton.setEnabled(hasSim);
          doubleSpeedButton.setEnabled(hasSim);
          superSpeedButton.setEnabled(hasSim);
          unlimitedSpeedButton.setEnabled(hasSim);
          
          if (hasSim) {
            Double speed = sim.getSpeedLimit();
            if (speed == null) {
              unlimitedSpeedButton.setSelected(true);
            } else if (speed == 0.01) {
              slowCrawlSpeedButton.setSelected(true);
            } else if (speed == 0.1) {
              crawlSpeedButton.setSelected(true);
            } else if (speed == 1.0) {
              normalSpeedButton.setSelected(true);
            } else if (speed == 2.0) {
              doubleSpeedButton.setSelected(true);
            } else if (speed == 20.0) {
              superSpeedButton.setSelected(true);
            }
          } else {
            startButton.setSelected(false);
          }
          // Start timer after updating the UI states.
          if (hasSim && sim.isRunning() && !stoppedSimulation && !updateTimer.isRunning()) {
            updateTimer.start();
          }
        }
      };
      startButton.addItemListener(toolbarListener);
      
      
      final var buttonAction = new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          toolbarListener.updateToolbar(false);
        }
      };
      stepButton.addActionListener(buttonAction);
      reloadButton.addActionListener(buttonAction);
      
      
      final var speedListener = new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          switch (e.getActionCommand()) {
            case "0.01X":
              getSimulation().setSpeedLimit(0.01);
              break;
            case "0.1X":
              getSimulation().setSpeedLimit(0.1);
              break;
            case "1X":
              getSimulation().setSpeedLimit(1.0);
              break;
            case "2X":
              getSimulation().setSpeedLimit(2.0);
              break;
            case "20X":
              getSimulation().setSpeedLimit(20.0);
              break;
            case "Unlimited":
              getSimulation().setSpeedLimit(null);
              break;
          }
        }
      };
      
      slowCrawlSpeedButton.addActionListener(speedListener);
      crawlSpeedButton.addActionListener(speedListener);
      normalSpeedButton.addActionListener(speedListener);
      doubleSpeedButton.addActionListener(speedListener);
      superSpeedButton.addActionListener(speedListener);
      unlimitedSpeedButton.addActionListener(speedListener);
  }

  private interface ToolbarListener extends ItemListener {
      /** Updates buttons according to simulation status. */
      void updateToolbar(boolean stoppedSimulation);
  }
  
  private class ChangeMaxSpeedLimitAction extends AbstractAction {
    private final Double maxSpeed;
    public ChangeMaxSpeedLimitAction(String name, Double maxSpeed) {
      super(name);
      this.maxSpeed = maxSpeed;
    }
    @Override
    public void actionPerformed(ActionEvent e) {
      simulation.setSpeedLimit(maxSpeed);
    }
  }

  private void updateValues(EventTriggers.Operation op) {
      /* Update current time */
      simulationTime.setText(getTimeString());
      simulationSpeedup.setText("Speed: ---");
      if (simulation.isRunning() && !updateLabelTimer.isRunning()) {
        updateLabelTimer.start();
      }

      /* Update control buttons */
      if (simulation.isRunning()) {
        startAction.setEnabled(false);
        stopAction.setEnabled(true);
        stepAction.setEnabled(false);
        stopButton.requestFocus();
      } else {
        if(simulation.isRunnable()) {
          startAction.setEnabled(true);
          stepAction.setEnabled(true);
        } else {
          startAction.setEnabled(false);
          stepAction.setEnabled(false);
        }
        stopAction.setEnabled(false);
        startButton.requestFocus();
      }

    final boolean stoppedSimulation = !simulation.isRunning();
    toolbarListener.updateToolbar(stoppedSimulation);
  }

  protected 
  Simulation getSimulation( ) {
      return this.simulation;
  }
  
  private static final long TIME_SECOND = 1000*Simulation.MILLISECOND;
  private static final long TIME_MINUTE = 60*TIME_SECOND;
  private static final long TIME_HOUR = 60*TIME_MINUTE;
  public String getTimeString(Simulation sim) {
    long t = sim.getSimulationTime();
    long h = (t / TIME_HOUR);
    t -= (t / TIME_HOUR)*TIME_HOUR;
    long m = (t / TIME_MINUTE);
    t -= (t / TIME_MINUTE)*TIME_MINUTE;
    long s = (t / TIME_SECOND);
    t -= (t / TIME_SECOND)*TIME_SECOND;
    long ms = t / sim.MILLISECOND;
    if (h > 0) {
      return String.format("Time: %d:%02d:%02d.%03d", h,m,s,ms);
    } else {
      return String.format("Time: %02d:%02d.%03d", m,s,ms);
    }
  }
  
  public String getTimeString() {
      return getTimeString(simulation);
  }

  @Override
  public void closePlugin() {
    /* Remove simulation observer */
    simulation.getSimulationStateTriggers().deleteTriggers(this);

    /* Remove label update timer */
    //updateLabelTimer.stop();
    
    var desktop = gui.getTopParentContainer().getContentPane();
    desktop.remove(toolBar);
  }

  private final Timer updateLabelTimer = new Timer(LABEL_UPDATE_INTERVAL, new ActionListener() {
    @Override
    public void actionPerformed(ActionEvent e) {
      simulationTime.setText(getTimeString());

      long systemTimeDiff = System.currentTimeMillis() - lastSystemTimeTimestamp;

      if (systemTimeDiff > 1000) {
        long simulationTimeDiff = simulation.getSimulationTimeMillis() - lastSimulationTimeTimestamp;
        lastSimulationTimeTimestamp = simulation.getSimulationTimeMillis();
        lastSystemTimeTimestamp = System.currentTimeMillis();

        double speedup = (double)simulationTimeDiff / (double)systemTimeDiff;
        simulationSpeedup.setText(String.format("Speed: %2.2f%%", 100 * speedup));
      }

      /* Automatically stop if simulation is no longer running */
      if (!simulation.isRunning()) {
        updateLabelTimer.stop();
      }
    }
  });

  private final Action startAction = new AbstractAction("Start") {
    @Override
    public void actionPerformed(ActionEvent e) {
      simulation.startSimulation();
    }
  };
  private final Action stopAction = new AbstractAction("Pause") {
    @Override
    public void actionPerformed(ActionEvent e) {
      simulation.stopSimulation();
    }
  };
  private final Action stepAction = new AbstractAction("Step") {
    @Override
    public void actionPerformed(ActionEvent e) {
      simulation.stepMillisecondSimulation();
    }
  };
  private Action breakAction = new AbstractAction("Break") {
      public void actionPerformed(ActionEvent e) {
        simulation.breakSimulation();
      }
  };
  private final Action reloadAction = new AbstractAction("Reload") {
        @Override
        public void actionPerformed(ActionEvent e) {
          gui.gui().reloadCurrentSimulation( simulation.getRandomSeed() );
        }
  };

  @Override
  public String getQuickHelp() {
    return "<b>Control Panel</b>" +
        "<p>The control panel controls the simulation. " +
        "<p><i>Start</i> starts the simulation. " +
        "<p><i>Pause</i> stops the simulation. " +
        "<p>The keyboard shortcut for starting and pausing the simulation is <i>Ctrl+S</i>. " +
        "<p><i>Step</i> runs the simulation for one millisecond. " +
        "<p><i>Break</i> signal kill to simulaton thread of current mote. " +
        "<p><i>Reload</i> reloads and restarts the simulation. " +
        "<p>Simulation speed is controlled via the Speed limit menu.";
  }
}
