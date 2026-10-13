package com.openggf.game.sonic3k.ending;

import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.game.sonic3k.S3kPaletteWriteSupport;
import com.openggf.level.objects.ObjectServices;
import java.io.IOException;
import java.io.UncheckedIOException;

/** loc_5E6C0's object-local Run_PalRotationScript2 state at $3A/$3B. */
public final class S3kEndingMasterPaletteState {
    private int delay;
    private int cursor;
    public record Value(int delay, int cursor) { }
    public Value captureRewindStateValue() { return new Value(delay, cursor); }
    public void restoreRewindStateValue(Value value) { delay=value.delay();cursor=value.cursor(); }

    public void tick(ObjectServices services) {
        var registry=services.paletteOwnershipRegistryOrNull();
        if (registry!=null&&registry.isPaletteRotationDisabled()) return;
        delay=(byte)(delay-1);
        if (delay>=0) return;
        try {
            var rom=services.romReader();
            int table=0x7DD5A;
            int rows=rom.readU32BE(table);
            int count=rom.readU16BE(table+4)+1;
            cursor=(cursor+2)&0xFF;
            int row=(byte)rom.readU8(table+6+cursor);
            if (row<0) { cursor=0;row=rom.readU8(table+6); }
            delay=rom.readU8(table+7+cursor);
            int address=rows+(short)rom.readU16BE(rows+row*2);
            S3kPaletteWriteSupport.applyContiguousPatch(registry,services.currentLevel(),
                    services.graphicsManager(),"s3k-ending-master",S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE,
                    2,14,services.rom().readBytes(address,count*2));
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
}
