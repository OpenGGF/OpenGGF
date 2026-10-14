package com.openggf.game.sonic3k.ending;

import com.openggf.data.RomByteReader;
import java.io.IOException;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Independent byte-RAM transcription of retail AllocateObject / Create_Continue_Sprite.
 * Origin: S3K parity allocation, develop5b2ff8e78. No engine allocator or object state is read.
 * The literal opcode gates bind field widths, preincrement, DBEQ and unchecked writes to ROM.
 */
final class ContinueSpriteRamOracle {
    static final int SLOT_SIZE=0x4A, FIRST=4, LAST=93;
    private final RomByteReader rom;
    private final byte[] ram=new byte[0x10000];
    ContinueSpriteRamOracle(RomByteReader rom) throws IOException {
        this.rom=rom;
        int[] program={0x7600,0x4EB9,0x0001,0xBAF2,0x22BC,0x0005,0xEDAC,
                0x1341,0x002C,0x1343,0x002D,0x3372,0x3000,0x0010,
                0x3372,0x3002,0x0014,0x5843,0x51CA,0xFFDC,0x4E75};
        for(int i=0;i<program.length;i++)assertEquals(program[i],rom.readU16BE(0x5FE1E+i*2));
        int[] allocator={0x43F8,0xB0DE,0x7059,0x6010};
        for(int i=0;i<allocator.length;i++)assertEquals(allocator[i],rom.readU16BE(0x1BAF2+i*2));
        int[] loop={0x43E9,0x004A,0x4A91,0x57C8,0xFFF8,0x4E75};
        for(int i=0;i<loop.length;i++)assertEquals(loop[i],rom.readU16BE(0x1BB0A+i*2));
    }
    static int address(int slot){return 0xB000+slot*SLOT_SIZE;}
    void putSlot(int slot,byte[] bytes){System.arraycopy(bytes,0,ram,address(slot),SLOT_SIZE);}
    byte[] slot(int slot){return Arrays.copyOfRange(ram,address(slot),address(slot)+SLOT_SIZE);}
    int allocate() throws IOException {
        int a1=rom.readU16BE(0x1BAF4),d0=(byte)rom.readU8(0x1BAF7);
        do {
            a1=(a1+rom.readU16BE(0x1BB0C))&0xFFFF;
            if(u32(ram,a1)==0)return a1;
        }while((short)--d0!=-1); // DBEQ consumes all90 probes and retains its last A1 on failure.
        return a1;
    }
    void group(int table,int subtype,int count) throws IOException {
        int d3=0;
        do {
            int a1=allocate();
            put32(ram,a1,rom.readU32BE(0x5FE28)); // move.l #loc_5EDAC,(a1)
            ram[a1+0x2C]=(byte)subtype;ram[a1+0x2D]=(byte)d3;
            put16(ram,a1+0x10,rom.readU16BE(table+d3));
            put16(ram,a1+0x14,rom.readU16BE(table+d3+2));
            d3=(d3+4)&0xFFFF;
        }while(--count>0);
    }
    static int u16(byte[] b,int at){return (b[at]&255)<<8|b[at+1]&255;}
    static int u32(byte[] b,int at){return u16(b,at)<<16|u16(b,at+2);}
    static void put16(byte[] b,int at,int value){b[at]=(byte)(value>>>8);b[at+1]=(byte)value;}
    static void put32(byte[] b,int at,int value){put16(b,at,value>>>16);put16(b,at+2,value);}
}
