package com.openggf.game.sonic3k.ending;

import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.game.sonic3k.S3kPaletteWriteSupport;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.level.objects.ObjectServices;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;

/** Shared Palette_cycle_counter0/1 and counters+$00/02 for ending BG and AnPal_AIZ1. */
public final class S3kEndingAizPaletteState {
    public static final int BYTES = 3 * Short.BYTES + 1;
    private int timer, primaryOffset, secondaryOffset, secondaryGate;

    /** loc_5AD30 clears only these words and gate; secondary $F652 survives. */
    public void beginHold() { timer = 0; primaryOffset = 0; secondaryGate = 0; }
    public void beginFade() { secondaryGate = 0xFF; }
    public void tick(ObjectServices services) {
        timer = (short) (timer - 1);
        if (timer >= 0) return;
        timer = 7;
        int primaryRow = primaryOffset & 0x18;
        primaryOffset = (primaryOffset + 8) & 0xFFFF;
        try {
            write(services, 2, 11, Sonic3kConstants.ANPAL_AIZ1_1_ADDR + primaryRow, 8);
            if (secondaryGate == 0) {
                int secondaryRow = secondaryOffset;
                secondaryOffset += 6;
                if (secondaryOffset >= 0x30) secondaryOffset = 0;
                write(services, 3, 12, Sonic3kConstants.ANPAL_AIZ1_2_ADDR + secondaryRow, 6);
            }
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
    /** sub_592EE: increment-before-read, shared word timer/cursor, native late BG writer. */
    public void tickEndingSky(ObjectServices services) { tickEnding(services,4,0x38,3,0x5931A,11,4); }
    /** sub_5928C uses the same two words and preserves them when the BG owner changes. */
    public void tickEndingWater(ObjectServices services) { tickEnding(services,6,0x30,7,0x592BE,13,6); }
    private void tickEnding(ObjectServices services,int stride,int limit,int delay,int address,int color,int bytes) {
        if(secondaryGate!=0)return;
        timer=(short)(timer-1);if(timer>=0)return;
        timer=delay;primaryOffset=(primaryOffset+stride)&0xFFFF;
        if(primaryOffset>=limit)primaryOffset=0;
        try { write(services,3,color,address+primaryOffset,bytes); }
        catch(IOException failure){throw new UncheckedIOException(failure);}
    }
    private void write(ObjectServices s, int line, int color, int address, int length) throws IOException {
        // The explicit ScreenEvents call is later than sprite palette writers;
        // the ordinary zone-cycle priority/LEVEL_STARTED gate is not this owner.
        S3kPaletteWriteSupport.applyContiguousPatch(s.paletteOwnershipRegistryOrNull(), s.currentLevel(),
                s.graphicsManager(), "s3k-ending-aiz-hold", S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE,
                line, color, s.rom().readBytes(address, length));
    }
    public void captureTo(ByteBuffer b) {
        b.putShort((short)timer).putShort((short)primaryOffset).putShort((short)secondaryOffset)
                .put((byte)secondaryGate);
    }
    public void restoreFrom(ByteBuffer b) {
        timer=b.getShort(); primaryOffset=b.getShort()&0xFFFF;
        secondaryOffset=b.getShort()&0xFFFF; secondaryGate=b.get()&0xFF;
    }
}
