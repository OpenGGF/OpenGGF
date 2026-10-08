package com.openggf.game.sonic3k.objects;

import com.openggf.game.rewind.RewindStateful;
import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.game.sonic3k.S3kPaletteWriteSupport;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.level.objects.ObjectServices;
import java.io.IOException;
import java.io.UncheckedIOException;

/** The miniboss's one-entry word_7F03C script; advanced only by loc_7E108. */
final class DezMinibossPaletteState implements RewindStateful<DezMinibossPaletteState.Value> {
    record Value(int header, int cursor, int delay, int iteration, boolean active) { }

    private int header;
    private int cursor;
    private int delay;
    private int iteration;
    private boolean active;

    void start(ObjectServices services) {
        try {
            var rom = services.rom();
            int entry = Sonic3kConstants.PAL_DEZ_MINIBOSS_ATTACK_SCRIPT_ADDR;
            header = rom.read32BitAddr(entry + 4);
            cursor = header + rom.read16BitAddr(entry);
            delay = rom.readBytes(entry + 2, 1)[0] & 255;
            iteration = 0;
            active = true;
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /** Returns true on the exact pass that Run_PalRotationScript calls loc_7E124. */
    boolean tick(ObjectServices services) {
        var registry = services.paletteOwnershipRegistryOrNull();
        if (!active || (registry != null && registry.isPaletteRotationDisabled())) return false;
        delay = (byte) (delay - 1);
        if (delay >= 0) return false;
        try {
            var rom = services.rom();
            if ((short) rom.read16BitAddr(cursor) < 0) {
                iteration = (iteration + 1) & 255;
                int repetitions = rom.readBytes(header + 3, 1)[0] & 255;
                if (iteration >= repetitions) {
                    // sub_859CE / loc_85A02 skips the palette copy on callback.
                    active = false;
                    return true;
                }
                cursor = header + 4;
            }
            int count = (rom.readBytes(header + 2, 1)[0] & 255) + 1;
            int destination = rom.read16BitAddr(header);
            int color = (destination - 0xFC00) / 2;
            byte[] data = rom.readBytes(cursor, count * 2);
            S3kPaletteWriteSupport.applyContiguousPatch(registry, services.currentLevel(),
                    services.graphicsManager(), S3kPaletteOwners.DEZ_MINIBOSS,
                    S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE, color / 16, color % 16, data);
            cursor += count * 2;
            delay = rom.read16BitAddr(cursor) & 255;
            cursor += 2;
            return false;
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /** sub_7EE12: even flash counters select the bright quartet, before decrement. */
    static void flash(ObjectServices services, int counter) {
        try {
            int address = Sonic3kConstants.PAL_DEZ_MINIBOSS_HIT_FLASH_ADDR
                    + ((counter & 1) == 0 ? 8 : 0);
            S3kPaletteWriteSupport.applyContiguousPatch(services.paletteOwnershipRegistryOrNull(),
                    services.currentLevel(), services.graphicsManager(), S3kPaletteOwners.DEZ_MINIBOSS,
                    S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE, 1, 11,
                    services.rom().readBytes(address, 8));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    @Override public Value captureRewindStateValue() {
        return new Value(header, cursor, delay, iteration, active);
    }

    @Override public void restoreRewindStateValue(Value value) {
        header = value.header(); cursor = value.cursor(); delay = value.delay();
        iteration = value.iteration(); active = value.active();
    }
}
