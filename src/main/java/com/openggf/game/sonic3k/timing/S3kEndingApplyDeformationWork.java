package com.openggf.game.sonic3k.timing;

import java.nio.ByteBuffer;
import com.openggf.data.RomByteReader;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;

/**
 * One ending BG0/4 ApplyDeformation call descriptor and closed instruction figure.
 * R16's ROM micro-execution and independent band oracle close this source path;
 * native evidence covers entry/exit only, not its input-dependent body.
 * Never a frame/window budget: the caller, other call paths and hardware remain unpriced.
 */
public final class S3kEndingApplyDeformationWork {
    public static final int CODE_START = 0x4F0CE;
    public static final int CODE_LENGTH = 0x8C;
    public static final int ISLAND_BANDS = 0x5B144;
    public static final int CLOUD_BANDS = 0x5B15A;
    public static final int LINES = 224;
    public static final int BYTES = Long.BYTES + 5 * Integer.BYTES + 1;
    public enum Qualification { NOT_EXECUTED, ROM_UNSUPPORTED, INPUT_UNSUPPORTED, QUALIFIED }
    public enum UnpricedTerm {
        ENDING_BACKGROUND_CALLER, OTHER_APPLY_CALL_PATHS, WATER_DEFORMATION, OTHER_FRAME_CALLERS,
        INTERRUPT_ACCEPTANCE, REFRESH, Z80_BUS, VDP_FIFO, DMA_FREEZE
    }
    private static final Set<UnpricedTerm> UNPRICED = Set.of(UnpricedTerm.values());
    private static final byte[] CODE_SHA256 = HexFormat.of().parseHex(
            "ea1cce9bf7b73fc63cffc164ebfe10658ec8d25e0a2a78e385c395ce295dafee");
    // Immutable session ROM resources; derived bindings are not live rewind state.
    private RomByteReader boundReader;
    private boolean codeSupported;
    private int[] islandBands, cloudBands;
    private long executionCount;
    private int lastExecutionFrame = Integer.MIN_VALUE;
    private int tableAddress, backgroundY, lines, instructionCycles = -1;
    private Qualification qualification = Qualification.NOT_EXECUTED;

    /** Called by the actual event tail, after Draw_TileRow and before palette cycling. */
    public void recordExecution(RomByteReader reader, int levelFrame, int table, int bgY) {
        if (executionCount != 0 && lastExecutionFrame == levelFrame) return;
        if (reader != boundReader) {
            codeSupported = romMatches(reader);
            islandBands = codeSupported ? readBands(reader, ISLAND_BANDS, 11) : null;
            cloudBands = codeSupported ? readBands(reader, CLOUD_BANDS, 10) : null;
            boundReader = reader;
        }
        executionCount++; lastExecutionFrame=levelFrame; tableAddress=table;
        backgroundY=(short)bgY; lines=LINES; instructionCycles=-1;
        if (!codeSupported) {qualification=Qualification.ROM_UNSUPPORTED;return;}
        int[] bands = table == ISLAND_BANDS ? islandBands : table == CLOUD_BANDS ? cloudBands : null;
        int cycles = bands == null ? -1 : instructionCycles(bands, backgroundY);
        qualification=cycles<0?Qualification.INPUT_UNSUPPORTED:Qualification.QUALIFIED;
        instructionCycles=cycles;
    }

    /** Covers every supported opcode AND operand byte through the RTS at $4F158. */
    public static boolean romMatches(RomByteReader reader) {
        if(reader==null || reader.size()<CODE_START+CODE_LENGTH)return false;
        try {
            return MessageDigest.isEqual(CODE_SHA256,
                    MessageDigest.getInstance("SHA-256").digest(reader.slice(CODE_START,CODE_LENGTH)));
        } catch(NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 is required to verify ApplyDeformation",unavailable);
        }
    }
    private static int[] readBands(RomByteReader reader,int address,int count) {
        if(reader.size()<address+count*2)return null;
        int[] result=new int[count];
        for(int i=0;i<count;i++){
            result[i]=reader.readU16BE(address+i*2);
            if((result[i]&0x7FFF)==0)return null;
        }
        return result[count-1]==0x7FFF?result:null;
    }

    /**
     * Closed 68000 basic-block sum of $4F0CE..$4F15A, native entry d1=$DF.
     * This pure path calculation alone does not qualify a production call.
     * Zero-height bands and word-wrapped skip counts are unsupported, never zero-cost.
     */
    public static int instructionCycles(int[] bands,int bgY) {
        if(bands==null)return -1;
        int c=40, band=0, y=(short)bgY, height;
        boolean perLine;
        while(true){
            if(band>=bands.length)return -1;
            int word=bands[band++];height=word&0x7FFF;perLine=(word&0x8000)!=0;
            if(height==0)return -1;
            c+=8+(perLine?6+8+8:4+10); // move / smi / bpl / optional andi
            int difference=y-height;
            if((short)difference!=difference)return -1;
            y=difference;c+=4;
            if(y<0){c+=10;break;}
            c+=8+8+4; // bmi fall, addq a5, tst d4
            c+=perLine?8+8+4+8+10:10; // per-line source skip or beq
        }
        c+=4+(perLine?8+4+4+8:10); // loc_4F0FA source adjustment
        int firstRows=-y;
        c+=4+4+4; // neg / move / sub
        int remainingMinusOne=LINES-1-firstRows;
        if(firstRows>LINES-1){c+=8+4+4;firstRows=LINES;}else c+=10;
        c+=4+4+4; // neg/swap d3, loc_4F114 subq
        int rows=firstRows;
        while(true){
            int counter=rows-1;
            c+=4+(perLine?8:10); // tst / beq
            if(!perLine)c+=8+4; // load and negate one constant scroll word
            c+=8+((counter&1)==0?10:8); // lsr / bcc
            c+=(perLine?24:12)*rows + 10*((rows+1)/2-1)+14;
            if(perLine)c+=10; // bra loc_4F140
            c+=4;
            if(remainingMinusOne<0)return c+10+16; // bmi / rts
            c+=8;
            if(band>=bands.length)return -1;
            int word=bands[band++];height=word&0x7FFF;perLine=(word&0x8000)!=0;
            if(height==0)return -1;
            c+=8+(perLine?6+8+8:4+10);
            int oldRemaining=remainingMinusOne;remainingMinusOne-=height;c+=4+4;
            if(remainingMinusOne>=0){c+=10+4;rows=height;}
            else {c+=8+4+10;rows=oldRemaining+1;}
        }
    }

    public long executionCount() { return executionCount; }
    public int lastExecutionFrame() { return lastExecutionFrame; }
    public int tableAddress() { return tableAddress; }
    public int backgroundY() { return backgroundY; }
    public int lines() { return lines; }
    public int lastInstructionCycles() { return instructionCycles; }
    public Qualification qualification() { return qualification; }
    public Set<UnpricedTerm> unpricedTerms() { return UNPRICED; }
    public void captureTo(ByteBuffer b) {
        b.putLong(executionCount).putInt(lastExecutionFrame).putInt(tableAddress)
                .putInt(backgroundY).putInt(lines).putInt(instructionCycles).put((byte)qualification.ordinal());
    }
    public void restoreFrom(ByteBuffer b) {
        executionCount=b.getLong();lastExecutionFrame=b.getInt();tableAddress=b.getInt();
        backgroundY=b.getInt();lines=b.getInt();instructionCycles=b.getInt();
        qualification=Qualification.values()[b.get()&255];
    }
}
