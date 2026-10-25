package com.openggf.game.soniccd.resources;

import com.openggf.game.soniccd.level.CdAssetBytes;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * The 96-byte descriptor portion of the CD PLC buffer, not the decoder/register owner.
 * scddisasm 1ecc5f3a, LoadPLC / InitPLC / DecompPLC_Pop
 * (Level/Functions (General).asm:588-658,773-785), FixBugs=0.
 * ClearPLCs clears 128 bytes natively: callers must ALSO clear their decoder/count/hint
 * state. cleared()/replace() cover only these 96 descriptor bytes. No VRAM or source read.
 */
public final class CdR11aPlcBuffer {
    private CdR11aPlcBuffer() { }
    public record Entry(long source, int destination) {
        public Entry { sourceLong(source); word(destination); }
    }
    public enum Access { SOURCE_SCAN, SOURCE_WRITE, DESTINATION_WRITE }
    /** A reached unowned CPU address; already completed descriptor stores remain in state. */
    public static final class BufferBoundary extends UnsupportedOperationException {
        private final State state;
        private final Access access;
        private final int byteOffset;
        private BufferBoundary(State state, Access access, int offset) {
            super("CD PLC descriptor buffer " + access + " at byte " + offset);
            this.state=state;this.access=access;this.byteOffset=offset;
        }
        public State state() { return state; }
        public Access access() { return access; }
        public int byteOffset() { return byteOffset; }
    }
    public static final class State {
        private final CdAssetBytes bytes;
        private State(byte[] bytes) { this.bytes=CdAssetBytes.copyOf(bytes); }
        public CdAssetBytes bytes() { return bytes; }
        public long headSource() { return bytes.unsignedLong(0); }
        public int headDestination() { return bytes.unsignedWord(4); }
        @Override public boolean equals(Object other) {
            return other instanceof State s && Arrays.equals(bytes.copy(),s.bytes.copy());
        }
        @Override public int hashCode() { return Arrays.hashCode(bytes.copy()); }
    }
    /** Explicit byte ingress certifies no native initialization or asset identity. */
    public static State fromBytes(CdAssetBytes bytes) {
        Objects.requireNonNull(bytes);
        if(bytes.length()!=96)throw new IllegalArgumentException("CD PLC descriptor extent must be96");
        return new State(bytes.copy());
    }
    public static State cleared() { return new State(new byte[96]); }

    /** Source-long free scan occurs even for an empty (negative native count) list. */
    public static State append(State before,List<Entry> entries) {
        Objects.requireNonNull(before);Objects.requireNonNull(entries);
        byte[] bytes=before.bytes.copy();int offset=0;
        while(true) {
            if(offset+4>bytes.length)throw boundary(bytes,Access.SOURCE_SCAN,offset);
            if(readLong(bytes,offset)==0)break;
            offset+=6;
        }
        return copyRows(bytes,offset,entries);
    }
    /** Descriptor portion of InitPLC. Decoder/count/hint clearing belongs to its caller. */
    public static State replace(State before,List<Entry> entries) {
        Objects.requireNonNull(before);Objects.requireNonNull(entries);
        return copyRows(new byte[96],0,entries);
    }
    private static State copyRows(byte[] bytes,int offset,List<Entry> entries) {
        for(Entry entry:entries) {
            Objects.requireNonNull(entry);
            if(offset+4>bytes.length)throw boundary(bytes,Access.SOURCE_WRITE,offset);
            putLong(bytes,offset,entry.source());offset+=4;
            if(offset+2>bytes.length)throw boundary(bytes,Access.DESTINATION_WRITE,offset);
            putWord(bytes,offset,entry.destination());offset+=2;
        }
        return new State(bytes);
    }
    /** Shipped bug: 22 MOVE.L operations copy88 bytes; final8 remain unchanged. */
    public static State pop(State before) {
        Objects.requireNonNull(before);byte[] bytes=before.bytes.copy();
        for(int offset=0;offset<88;offset+=4) {
            // Forward source is6 bytes ahead. Earlier stores cannot touch a future read.
            long value=readLong(bytes,offset+6);putLong(bytes,offset,value);
        }
        return new State(bytes);
    }
    /** ProcessPLCs publishes the compressed read cursor here, preserving destination/tail. */
    public static State withHeadSource(State before,long source) {
        Objects.requireNonNull(before);sourceLong(source);byte[] bytes=before.bytes.copy();
        putLong(bytes,0,source);return new State(bytes);
    }
    /** The selected native service preadvances this WORD by the whole budget before decode. */
    public static State withHeadDestination(State before,int destination) {
        Objects.requireNonNull(before);word(destination);byte[] bytes=before.bytes.copy();
        putWord(bytes,4,destination);return new State(bytes);
    }
    private static BufferBoundary boundary(byte[] bytes,Access access,int offset) {
        return new BufferBoundary(new State(bytes),access,offset);
    }
    private static long readLong(byte[] b,int offset) {
        return ((long)(b[offset]&255)<<24)|((long)(b[offset+1]&255)<<16)
                |((long)(b[offset+2]&255)<<8)|(b[offset+3]&255);
    }
    private static void putLong(byte[] b,int offset,long value) {
        b[offset]=(byte)(value>>>24);b[offset+1]=(byte)(value>>>16);
        b[offset+2]=(byte)(value>>>8);b[offset+3]=(byte)value;
    }
    private static void putWord(byte[] b,int offset,int value) {
        b[offset]=(byte)(value>>>8);b[offset+1]=(byte)value;
    }
    private static void sourceLong(long value) {
        if(value<0||value>0xFFFFFFFFL)throw new IllegalArgumentException("PLC source must be unsigned long");
    }
    private static void word(int value) {
        if((value&~0xFFFF)!=0)throw new IllegalArgumentException("PLC destination must be unsigned word");
    }
}
