package com.openggf.game.sonic3k.runtime;

import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.game.sonic3k.S3kPaletteWriteSupport;
import com.openggf.level.objects.ObjectServices;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;

/** The two looping Palette_rotation_data entries installed by $806DA / $81CC6.
 * Zone-owned because a newly allocated emerald replaces the shared script cursors.
 * This models the zero-parameter repeat branch of Run_PalRotationScript; finite
 * callbacks and script transitions belong to other owners and are not interpreted here.
 */
public final class S3kEmeraldPaletteState {
    public static final int SNAPSHOT_BYTES=6*Integer.BYTES;
    private int header0,header1,cursor0,cursor1,delay0,delay1;

    public void install(ObjectServices services,int table) {
        try {
            var rom=services.rom();
            header0=rom.read32BitAddr(table+4); header1=rom.read32BitAddr(table+12);
            cursor0=header0+rom.read16BitAddr(table); cursor1=header1+rom.read16BitAddr(table+8);
            delay0=rom.readBytes(table+2,1)[0]&255; delay1=rom.readBytes(table+10,1)[0]&255;
            if(rom.readBytes(header0+3,1)[0]!=0 || rom.readBytes(header1+3,1)[0]!=0)
                throw new IllegalStateException("Emerald palette requires the native infinite-repeat scripts");
        } catch(IOException failure) { throw new UncheckedIOException(failure); }
    }
    public void tick(ObjectServices services,String owner) {
        var registry=services.paletteOwnershipRegistryOrNull();
        if(header0==0 || (registry!=null && registry.isPaletteRotationDisabled())) return;
        delay0=(byte)(delay0-1); delay1=(byte)(delay1-1);
        try {
            var rom=services.rom();
            if(delay0<0) {
                if((short)rom.read16BitAddr(cursor0)<0) cursor0=header0+4;
                int count=write(services,owner,header0,cursor0); cursor0+=count*2;
                delay0=rom.read16BitAddr(cursor0)&255; cursor0+=2;
            }
            if(delay1<0) {
                if((short)rom.read16BitAddr(cursor1)<0) cursor1=header1+4;
                int count=write(services,owner,header1,cursor1); cursor1+=count*2;
                delay1=rom.read16BitAddr(cursor1)&255; cursor1+=2;
            }
        } catch(IOException failure) { throw new UncheckedIOException(failure); }
    }
    private int write(ObjectServices services,String owner,int header,int cursor) throws IOException {
        var rom=services.rom(); int color=(rom.read16BitAddr(header)-0xFC00)/2;
        int count=(rom.readBytes(header+2,1)[0]&255)+1;
        S3kPaletteWriteSupport.applyContiguousPatch(services.paletteOwnershipRegistryOrNull(),
                services.currentLevel(),services.graphicsManager(),owner,S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE,
                color/16,color%16,rom.readBytes(cursor,count*2));
        return count;
    }
    public void captureTo(ByteBuffer buffer) {
        buffer.putInt(header0).putInt(header1).putInt(cursor0).putInt(cursor1).putInt(delay0).putInt(delay1);
    }
    public void restoreFrom(ByteBuffer buffer) {
        header0=buffer.getInt(); header1=buffer.getInt(); cursor0=buffer.getInt(); cursor1=buffer.getInt();
        delay0=buffer.getInt(); delay1=buffer.getInt();
    }
}
