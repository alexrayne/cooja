/*
 * Copyright (c) 2014, TU Braunschweig. All rights reserved.
 * Copyright (c) 2006, Swedish Institute of Computer Science. All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer. 2. Redistributions in
 * binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other
 * materials provided with the distribution. 3. Neither the name of the
 * Institute nor the names of its contributors may be used to endorse or promote
 * products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE INSTITUTE AND CONTRIBUTORS ``AS IS'' AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE INSTITUTE OR CONTRIBUTORS BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.contikios.cooja.mote.memory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.contikios.cooja.util.ArrayUtils;

/**
 * Represents a mote memory consisting of non-overlapping memory sections with
 * symbol addresses.
 * <p>
 * Every section must implement MemoryInterface.
 * <p>
 * Implements MemoryInterface by forwarding calls to available sections or returning
 * an error if no section is available.
 *
 * @author Fredrik Osterlind
 * @author Enrico Jorns
 */
public class SectionMoteMemory implements MemoryInterface {
  private static final Logger logger = LoggerFactory.getLogger(SectionMoteMemory.class);
  private static final boolean DEBUG = logger.isDebugEnabled();

  private final Map<String, MemoryInterface> sections = new HashMap<>();

  private final Map<String, Symbol> symbols;
  private MemoryLayout memLayout;
  private long startAddr = Long.MAX_VALUE;

  /**
   * @param symbols Symbol addresses
   */
  public SectionMoteMemory(Map<String, Symbol> symbols) {
    this.symbols = symbols;
  }

  /**
   * Adds a section to this memory.
   * <p>
   * A new section will be checked for address overlap with existing sections.
   *
   * @param name
   * @param section
   * @return true if adding succeeded, false otherwise
   */
  public boolean addMemorySection(String name ,MemoryInterface section) {

    if (section == null) {
      return false;
    }

    /* Cooja address space */
    for (MemoryInterface sec : sections.values()) {
      /* check for overlap with existing region */
      if ((section.getStartAddr() <= sec.getStartAddr() + sec.getTotalSize() - 1)
              && (section.getStartAddr() + section.getTotalSize() - 1 >= sec.getStartAddr())) {
        logger.error(String.format(
                "Failed adding section '%s' of size %d @0x%x",
                name,
                section.getTotalSize(),
                section.getStartAddr()));
        return false;
      }
      /* Min start address is main start address */
      startAddr = Math.min(sec.getStartAddr(), startAddr);
      /* Layout is last layout. XXX Check layout consistency? */
      memLayout = section.getLayout();
    }

    sections.put(name, section);
    if (section.getSymbolMap() != null) {
      // XXX how to handle double names here?
      symbols.putAll(section.getSymbolMap());
    }

    if (DEBUG) {
      logger.debug(String.format(
              "Added section '%s' of size %d @0x%x",
              name,
              section.getTotalSize(),
              section.getStartAddr()));
    }
    return true;
  }

  /**
   * Returns the total number of sections in this memory.
   *
   * @return Number of sections
   */
  public int getNumberOfSections() {
    return sections.size();
  }

  /**
   * Returns memory section of this memory.
   * @param name Name of section
   * @return memory section
   */
  public MemoryInterface getSection(String name) {
    return sections.getOrDefault(name, null);
  }

  /**
   * Return all sections of this memory.
   * @return All memory sections
   */
  public Map<String, MemoryInterface> getSections() {
    return sections;
  }

  /**
   * True if given address is part of this memory section.
   *
   * @param intf
   * @param addr
   * Address
   * @return True if given address is part of this memory section, false
   * otherwise
   */
  public static boolean includesAddr(MemoryInterface intf, long addr) {
    return addr >= intf.getStartAddr() && addr < (intf.getStartAddr() + intf.getTotalSize());
  }

  /**
   * True if the whole address range specified by address and size
   * lies inside this memory section.
   *
   * @param intf
   * @param addr Start address of segment to check
   * @param size Size of segment to check
   *
   * @return True if given address range lies inside address range of this
   * section
   */
  public static boolean inSection(MemoryInterface intf, long addr, int size) {
    return addr >= intf.getStartAddr() && addr + size <= intf.getStartAddr() + intf.getTotalSize();
  }

  @Override
  public void clearMemory() {
    sections.clear();
  }

  @Override
  public int getTotalSize() {
    int totalSize = 0;
    for (MemoryInterface section : sections.values()) {
      totalSize += section.getTotalSize();
    }
    return totalSize;
  }

  @Override
  public byte[] getMemory() throws MoteMemoryException {
    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }


  /**
   * Returns memory segment from section matching segment given by address and size
   * @param address start address of segment to get
   * @param size size of segment to get
   * @return Array containing data of segment
   * @throws MoteMemoryException if no single section containing the given address range was found
   */
  @Override
  public byte[] getMemorySegment(long address, int size) throws MoteMemoryException {

    for (MemoryInterface section : sections.values()) {
      if (includesAddr(section, address) && includesAddr(section, address + size - 1)) {
        return section.getMemorySegment(address, size);
      }
    }

    throw new MoteMemoryException(
            "Getting memory segment [0x%x,0x%x] failed: No section available",
            address, address + size - 1);
  }

  /**
   * Sets memory segment of section matching segment given by address and size.
   * @param address start address of segment to set
   * @param data data to set
   * @throws MoteMemoryException if no single section containing the given address range was found
   * or memory is readonly.
   */
  @Override
  public void setMemorySegment(long address, byte[] data) throws MoteMemoryException {

    for (MemoryInterface section : sections.values()) {
      if (inSection(section, address, data.length)) {
        section.setMemorySegment(address, data);
        if (DEBUG) {
          logger.debug(String.format(
                  "Wrote memory segment [0x%x,0x%x]",
                  address, address + data.length - 1));
        }
        return;
      }
    }
    throw new MoteMemoryException(
            "Writing memory segment [0x%x,0x%x] failed: No section available",
            address, address + data.length - 1);
  }

  @Override
  public long getStartAddr() {
    return startAddr;
  }

  @Override
  public Map<String, Symbol> getSymbolMap() {
    return symbols;
  }

  /**
   * Checks if given variable exists in memory.
   *
   * @param varName Variable name
   * @return True if variable exists, false otherwise
   */
  public boolean symbolExists(String varName) {
    return getSymbolMap().containsKey(varName);
  }

  @Override
  public MemoryLayout getLayout() {
    return memLayout;
  }

  @Override
  public boolean addSegmentMonitor(SegmentMonitor.EventType flag, long address, int size, SegmentMonitor monitor) {
    PolledMemorySegments t = new PolledMemorySegments(monitor, address, size);
    polledMemories = ArrayUtils.add(polledMemories,t);
    return true;
  }

  @Override
  public boolean removeSegmentMonitor(long address, int size, SegmentMonitor monitor) {
    for (PolledMemorySegments mcm: polledMemories) {
      if (mcm.mm != monitor || mcm.address != address || mcm.size != size) {
        continue;
      }
      polledMemories = ArrayUtils.remove(polledMemories, mcm);
      return true;
    }
    return false;
  }

  /** Copies section memory to new (array backed) one
   * @return Cloned memory
   */
  @Override
  public SectionMoteMemory clone() {

    SectionMoteMemory clone = new SectionMoteMemory(symbols);

    for (Map.Entry<String, MemoryInterface> entry : sections.entrySet()) {
      // Copy section memory to new ArrayMemory
      MemoryInterface section = entry.getValue();
      MemoryInterface cpmem = new ArrayMemory(section.getStartAddr(), section.getLayout(), section.getMemory().clone(), section.getSymbolMap());
      clone.addMemorySection(entry.getKey(), cpmem);
    }

    return clone;
  }

  private PolledMemorySegments[] polledMemories = new PolledMemorySegments[0];
  
  public void pollForMemoryChanges() {
    int sz = polledMemories.length;
    for (int i = 0; i < sz; ++i) {
        polledMemories[i].notifyIfChanged();
    }
  }

  private class PolledMemorySegments {
    public final SegmentMonitor mm;
    public final long address;
    public final int size;
    private byte[] oldMem;

    public PolledMemorySegments(SegmentMonitor mm, long address, int size) {
      this.mm = mm;
      this.address = address;
      this.size = size;
      
      oldMem = getMemorySegment(address, size);
    }

    private void notifyIfChanged() {
      byte[] newMem = getMemorySegment(address, size);
      if (Arrays.equals(oldMem, newMem)) {
        return;
      }
      
      mm.memoryChanged(SectionMoteMemory.this, SegmentMonitor.EventType.WRITE, address);
      oldMem = newMem;
    }
  }

}
