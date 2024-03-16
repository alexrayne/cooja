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

package org.contikios.cooja.dialogs;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import java.lang.String;

import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.BoxLayout;
import javax.swing.JToggleButton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jdom2.Element;

import org.contikios.cooja.Mote;
import org.contikios.cooja.interfaces.Log;
import org.contikios.cooja.interfaces.SerialIO;
import org.contikios.cooja.interfaces.SerialPort;
import org.contikios.cooja.dialogs.SerialUI;
import org.contikios.cooja.util.EventTriggers;

public abstract class LogUI extends Log 
    //implements SerialPort 
{
  private static final Logger logger = LoggerFactory.getLogger(LogUI.class);

  private final static int MAX_LENGTH = 16*1024;

  private String lastLogMessage = ""; /* Log */
  private StringBuilder newMessage = new StringBuilder(); /* Log */

  /* Log */
  public String getLastLogMessage() {
    return lastLogMessage;
  }

  // informer about controls changes
  private class controlsInformer 
          extends EventTriggers<EventTriggers.Update, Object > 
  {
      public void refresh( Object x ) {
          trigger(UPDATE , x);
      }
  }

  private controlsInformer controlsInform = new controlsInformer();
  
  protected final EventTriggers<EventTriggers.Update, String> msgEvent = new EventTriggers<>();
  protected static final EventTriggers.Update UPDATE = EventTriggers.Update.UPDATE;

  protected void InvalidatedData() {
      logDataTriggers.trigger(EventTriggers.Update.UPDATE, new LogDataInfo(getMote(), lastLogMessage));
      msgEvent.trigger(UPDATE, lastLogMessage);
  }

  /* controls */
  private boolean is_serial_listen=false;//sends to mote.Log all received data
  private SerialUI mote_sio = null;

  public boolean isSerialListen() {
      return is_serial_listen;
  }

  public void listenSerial( boolean onoff) {
      if (is_serial_listen == onoff)
          return;

      if (mote_sio == null) {
          is_serial_listen = false;
          SerialUI ui = moteSerial();
          if(ui == null)
              return;
          mote_sio = ui;
      }

      is_serial_listen = onoff;
      logger.info("mote"+getMote().getID()+ " log serial listen " + onoff);
      controlsInform.refresh(mote_sio);

      mote_sio.setLogged(onoff);
      
      if (is_serial_listen) {
          mote_sio.getLogDataTriggers().addTrigger(this, serialEvent);
      }
      else {
          mote_sio.getLogDataTriggers().removeTrigger(this, serialEvent);
      }
          
  }

  private SerialUI moteSerial() {
      SerialIO moteio = getMote().getInterfaces().getSerial();
      if (moteio == null)
          return null;
      if ( SerialUI.class.isInstance(moteio) ) {
          return SerialUI.class.cast(moteio);
      }
      else
          return null;
  }

  // observes moteSerial
  protected final BiConsumer<EventTriggers.Update, Log.LogDataInfo> serialEvent = (event, ev) ->{
      
      if (ev.mote() == getMote())
      if ( mote_sio != null) {
          //String msg = mote_sio.getLastLogMessage();
          final String msg = ev.msg();
          if (msg != null)
          if (msg.length() > 0) {
              lastLogMessage = msg;
              InvalidatedData();
          }
      }
  };

  private final Pattern nonPrintable = Pattern.compile("[^\\p{Print}[ \\t]]");
  protected String fix_nonPrintable( final String x) {
      try {
          return nonPrintable.matcher(x).replaceAll("");
      }
      catch (Exception e) {
          return x;
      }
  }

  public void dataReceivedBuf(final byte[] data) {
      int len = data.length;
      if ( newMessage.length() > 0) {
          for (byte b: data)
              dataReceived(b);
          return;
      }

      //we can try to build string directly from data
      //scan received for \n
      int start = 0;
      
      // filter data from nonprinting chars
      byte[] fdata = data;
      
      for (int i = 0; i < len; ++i) {
          
          if (fdata[i] == '\n') {
              
              int slen = (i-start);
              if ( slen > 1) {
              if ( slen < MAX_LENGTH) {
                  lastLogMessage = new String(fdata, start, slen);
              }
              else {
                  /*logger.warn("Dropping too large log message (>" + MAX_LENGTH + " bytes).");*/
                  lastLogMessage = "# [1024 bytes, no line ending]: " + new String(fdata, 0, 20) + "...";
              }
              }
              else {
                  lastLogMessage = "";
              }
              InvalidatedData();
              start = i+1;
          }

          if (fdata[i] < 32) {
              //replace nonprinting chars with '.'
              //if ((x != '\r') && (x != '\t') && x != '\r')
              fdata[i] = '.';
          }
      }
      // collect rest of received
      if (start < len) {
          for (int k = start; k < len; ++k) {
              byte x = fdata[k];
              if (x < 32) {
                  //replace nonprinting chars with '.'
                  //if ((x != '\r') && (x != '\t') && x != '\r')
                  x = '.';
              }
              newMessage.append((char) x);
          }
      }
  }

  public void dataReceived(int data) {
    if (data == '\n') {
      /* Notify observers of new log */
      lastLogMessage = newMessage.toString();
      newMessage.setLength(0);
      InvalidatedData();
    } else {
      if (data < 32) {
            //replace nonprinting chars with '.'
            //if ((x != '\r') && (x != '\t') && x != '\r')
            data = '.';
      }
      newMessage.append((char) data);
      
      if (newMessage.length() > MAX_LENGTH) {
        /*logger.warn("Dropping too large log message (>" + MAX_LENGTH + " bytes).");*/
        lastLogMessage = "# [1024 bytes, no line ending]: " + newMessage.substring(0, 20) + "...";
        newMessage.setLength(0);
        InvalidatedData();
      }
    }
  }


  /* Mote interface visualizer */
  public JPanel getInterfaceVisualizer() {
    JPanel panel = new JPanel(new BorderLayout());

    final JTextArea logTextPane = new JTextArea();

    logTextPane.setOpaque(false);
    logTextPane.setEditable(false);

    // controls panel
    JPanel controlsPane = new JPanel();
    controlsPane.setLayout(new BoxLayout(controlsPane, BoxLayout.X_AXIS));
    
    JToggleButton listenSerialButton = new JToggleButton("Serial");
    controlsPane.add(listenSerialButton);
    listenSerialButton.setSelected(isSerialListen());

    listenSerialButton.addItemListener( new ItemListener () {
        @Override
        public void itemStateChanged(ItemEvent e) {
            listenSerial(listenSerialButton.isSelected());
        }  
    }); 

    /* Mote interface observer */
    msgEvent.addTrigger(panel, (e, msg) -> {
        final String logMessage = getLastLogMessage();
        EventQueue.invokeLater(() -> {
            appendToTextArea(logTextPane, logMessage);
        });
    });
    
    controlsInform.addTrigger(panel, (e, o) -> {
        EventQueue.invokeLater(() ->{listenSerialButton.setSelected(isSerialListen());} );
    });
    
    JScrollPane scrollPane = new JScrollPane(logTextPane);
    scrollPane.setPreferredSize(new Dimension(100, 100));
    panel.add(BorderLayout.NORTH, controlsPane);
    panel.add(BorderLayout.CENTER, scrollPane);
    return panel;
  }

  public void releaseInterfaceVisualizer(JPanel panel) {
    msgEvent.deleteTriggers(panel);
    controlsInform.deleteTriggers(panel);
  }

  public Collection<Element> getConfigXML() {
    ArrayList<Element> config = new ArrayList<Element>();

    setXMLValue(config, "listen_serial", isSerialListen());

    return config;
  }

  private boolean cfg_serial_ok = false;

  public void setConfigXML(Collection<Element> configXML, boolean visAvailable) {
      cfg_serial_ok = false;
      for (Element element : configXML) {
          if (element.getName().equals("listen_serial")) {
              listenSerial( Boolean.parseBoolean(element.getText()) );
              cfg_serial_ok = true;
          }
      }
  }

  @Override
  public void added() {
      getMote().getInterfaces().setLog(this);

      //for legacy ContikiRSR232 compatibily, listen serial by default
      if (!cfg_serial_ok)
      if (!isSerialListen())
      {
          logger.info("mote"+getMote().getID()+ " log listen serial, for legacy project");
          listenSerial(true);
      }
  }

  public void close() {
  }

  public void flushInput() {
  }

  public void writeByte(byte b) {}
  public void writeArray(byte[] s) {};
  public void writeString(String s) {
      logger.info("write log mote"+ getMote().getID() + ":" + s);
  };

  public abstract Mote getMote();


  protected static void appendToTextArea(JTextArea textArea, String text) {
    String current = textArea.getText();
    int len = current.length();
    if (len > 8192) {
      current = current.substring(len - 8192);
    }
    current = len > 0 ? (current + '\n' + text) : text;
    textArea.setText(current);
    textArea.setCaretPosition(current.length());

    Rectangle visRect = textArea.getVisibleRect();
    if (visRect.x > 0) {
      visRect.x = 0;
      textArea.scrollRectToVisible(visRect);
    }
  }

  private static String trim(String text) {
    return (text != null) && ((text = text.trim()).length() > 0) ? text : null;
  }
}
