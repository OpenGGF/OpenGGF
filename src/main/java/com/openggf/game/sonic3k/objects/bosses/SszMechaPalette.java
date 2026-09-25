package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.rewind.RewindStateful;
import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.game.sonic3k.S3kPaletteWriteSupport;
import com.openggf.level.objects.ObjectServices;
import java.io.IOException;
import java.io.UncheckedIOException;

/** Mecha's single Palette_rotation_data entry, installed by sub_7C678. */
final class SszMechaPalette implements RewindStateful<SszMechaPalette.Value> {
    record Value(int header, int displacement, int delay, int counter) { }
    private int header;
    private int displacement;
    private int delay;
    private int counter;

    void install(ObjectServices services, int table) {
        try {
            var rom = services.rom();
            displacement = rom.read16BitAddr(table) & 0xFFFF;
            byte[] clocks = rom.readBytes(table + 2, 2);
            delay = clocks[0] & 0xFF;
            counter = clocks[1] & 0xFF;
            header = rom.read32BitAddr(table + 4);
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }

    /** Run_PalRotationScript / sub_859CE, including the callback's skipped colour write. */
    void tick(ObjectServices services, Runnable customCallback) {
        var registry = services.paletteOwnershipRegistryOrNull();
        if (displacement == 0 || registry != null && registry.isPaletteRotationDisabled()) return;
        delay = (byte) (delay - 1);
        if (delay >= 0) return;
        try {
            var rom = services.rom();
            int cursor = header + (short) displacement;
            int command = (short) rom.read16BitAddr(cursor);
            if (command < 0) {
                int parameter = rom.readBytes(header + 3, 1)[0] & 0xFF;
                if (parameter != 0) {
                    counter = (counter + 1) & 0xFF;
                    if (counter >= parameter) {
                        if (command == -12) {
                            customCallback.run();
                            return; // loc_85A02 discards the colour-write return address.
                        }
                        if (command != -8) throw new IllegalStateException("Unsupported Mecha palette command " + command);
                        header += (short) rom.read16BitAddr(cursor + 2);
                        delay = 0; counter = 0; // clr.w 2(a1) on a header transition.
                    }
                }
                cursor = header + 4;
            }
            int destination = rom.read16BitAddr(header) & 0xFFFF;
            int count = (rom.readBytes(header + 2, 1)[0] & 0xFF) + 1;
            // All Mecha scripts replace the complete second line. Reject a changed
            // contract rather than silently publishing a partial line as 16 colours.
            if (destination != 0xFC20 || count != 16)
                throw new IllegalStateException("Unexpected Mecha palette destination/length");
            S3kPaletteWriteSupport.applyLine(registry, services.currentLevel(),
                    services.graphicsManager(), S3kPaletteOwners.SSZ_MECHA_SONIC,
                    S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE, 1, rom.readBytes(cursor, count * 2), true);
            cursor += count * 2;
            delay = rom.read16BitAddr(cursor) & 0xFF;
            displacement = (cursor + 2 - header) & 0xFFFF;
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }

    int headerAddress() { return header; }
    @Override public Value captureRewindStateValue() { return new Value(header, displacement, delay, counter); }
    @Override public void restoreRewindStateValue(Value value) {
        header = value.header(); displacement = value.displacement(); delay = value.delay(); counter = value.counter();
    }
}
