package org.contikios.cooja.interfaces;

import static java.nio.charset.StandardCharsets.UTF_8;

import org.contikios.cooja.Mote;
import org.contikios.cooja.dialogs.SerialUI;
import org.contikios.cooja.motes.AbstractApplicationMote;

public class ApplicationSerialPort extends SerialUI {
  private final Mote mote;

  public ApplicationSerialPort(Mote mote) {
    this.mote = mote;
  }

  @Override
  public Mote getMote() {
    return mote;
  }

  @Override
  public void writeArray(byte[] s) {
    ((AbstractApplicationMote) getMote()).writeArray(s);
  }
  @Override
  public void writeByte(byte b) {
    ((AbstractApplicationMote)getMote()).writeByte(b);
  }
  @Override
  public void writeString(String s) {
    ((AbstractApplicationMote)getMote()).writeString(s);
  }
}
