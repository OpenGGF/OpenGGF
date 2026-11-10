package com.openggf.game.soniccd.player;

import com.openggf.game.soniccd.level.CdAssetBytes;
import static com.openggf.game.soniccd.player.CdR4WaterAirPorts.*;
import static com.openggf.game.soniccd.player.CdR4WaterAirPorts.Value.*;

/** CheckWaterCurrents, src/r4/main.asm:775-870, N=1ecc5f3. After objects AND scroll.
 * Ordered14-byte native records, not an independent switch bank or route velocity injection.
 */
public final class CdR4WaterCurrents {
    private CdR4WaterCurrents() { }
    /** C2 owns the real switch storage/schema. Only the reached selected BYTE is borrowed. */
    @FunctionalInterface public interface SwitchSpan { int readByte(int unsignedIndex); }
    public static void check(WaterState s, CdAssetBytes records, SwitchSpan switches) {
        Slot p = s.player();
        if (read(p, B.ANIM) == 0x2B || (read(p, B.CONTROL) & 1) != 0 || read(p, B.ID) == 0
                || read(s, DEBUG_MODE) != 0 || read(s, ACT) >= 2) return;
        int at = 0; // The first record is NOT sentinel-tested by native entry.
        while (true) {
            int x = read(p, W.X);
            if (x >= records.unsignedWord(at) && x < records.unsignedWord(at + 4)) {
                int y = read(p, W.Y);
                if (y >= records.unsignedWord(at + 2) && y < records.unsignedWord(at + 6)) {
                    read(s, VBLANK_LOW); // Reached even though the computed $D0 is never played.
                    int index = records.unsignedByte(at + 13);
                    int mode = records.unsignedByte(at + 12);
                    if (mode == 1 && (checked(switches.readByte(index), 1, "switch") & 0x40) == 0) return;
                    if (read(p, B.ROUTINE) >= 4) { write(s, WATER_CURRENT, 0); return; }
                    write(s, WATER_CURRENT, 1); write(p, B.CHARGE, 0);
                    write(p, W.VX, records.unsignedWord(at + 8));
                    write(p, W.VY, records.unsignedWord(at + 10));
                    write(p, B.ANIM, 0xF); write(p, B.FLAGS, read(p, B.FLAGS) | 2);
                    // Mode is read again AFTER writes, as in the source.
                    if (records.unsignedByte(at + 12) == 2
                            && (checked(switches.readByte(index), 1, "switch") & 0x20) != 0) {
                        write(p, W.VX, -read(p, W.VX)); write(p, W.VY, -read(p, W.VY));
                    }
                    if ((read(s, HELD) & 1) != 0) write(p, W.Y, read(p, W.Y) - 1);
                    if ((read(s, HELD) & 2) != 0) write(p, W.Y, read(p, W.Y) + 1);
                    if ((read(s, HELD) & 4) != 0) write(p, W.X, read(p, W.X) - 1);
                    if ((read(s, HELD) & 8) != 0) write(p, W.X, read(p, W.X) + 1);
                    return;
                }
            }
            at = Math.addExact(at, 14);
            if (records.signedByte(at) >= 0) continue;
            if (read(s, WATER_CURRENT) == 0) return;
            write(p, B.ANIM, 0); write(s, WATER_CURRENT, 0); return;
        }
    }
}
