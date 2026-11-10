package com.openggf.game.soniccd.player;

import com.openggf.game.soniccd.objects.CdR4PlayerBubbles;
import static com.openggf.game.soniccd.player.CdR4WaterAirPorts.*;
import static com.openggf.game.soniccd.player.CdR4WaterAirPorts.Value.*;

/** PlayerCheckWater, src/objects/player.asm:430-489 at N=1ecc5f3; no MODE/Body clone.
 * Entry/exit and fallible reset/allocation retain ordered prefixes. No FixBugs alternate.
 */
public final class CdR4PlayerWater {
    private CdR4PlayerWater() { }
    /** Operands for existing Body call sites, not another movement/Hurt algorithm. */
    public record Rules(int jumpImpulse, int releaseCap, int afterFallSubtract,
                        int hurtGravity, int hurtAfterGravitySubtract, int hurtX, int hurtY) { }
    public static Rules rules(int flagsByte) {
        checked(flagsByte, 1, "player flags");
        return (flagsByte & 0x40) == 0 ? new Rules(0x680, -0x400, 0, 0x30, 0, -0x200, -0x400)
                : new Rules(0x380, -0x200, 0x28, 0x30, 0x20, -0x100, -0x200);
    }
    public static void check(Calls s) {
        if (read(s, ZONE) != 2) return;
        if (read(s, ACT) == 1 && read(s.player(), W.X) < 0xC8) return;
        Slot p = s.player();
        // CMP.W/BGE is signed, unlike the current rectangles' unsigned comparisons.
        int water = read(s, WATER_Y), y = read(p, W.Y);
        if ((short) water < (short) y) {
            int flags = read(p, B.FLAGS); write(p, B.FLAGS, flags | 0x40);
            if ((flags & 0x40) != 0) return;
            CdR4PlayerBubbles.resetAir(s);
            write(s.reservedBubbles(), B.ID, 0x21); write(s.reservedBubbles(), B.SUBTYPE, 0x81);
            write(s, MAX_SPEED, 0x300); write(s, ACCELERATION, 6); write(s, DECELERATION, 0x40);
            write(p, W.VX, (short) read(p, W.VX) >> 1);
            write(p, W.VY, (short) read(p, W.VY) >> 1);
            write(p, W.VY, (short) read(p, W.VY) >> 1);
            if (read(p, W.VY) == 0) return;
        } else {
            if ((short) read(p, W.VY) > 0) return;
            int flags = read(p, B.FLAGS); write(p, B.FLAGS, flags & ~0x40);
            if ((flags & 0x40) == 0) return;
            write(s, MAX_SPEED, 0x600); write(s, ACCELERATION, 0xC); write(s, DECELERATION, 0x80);
            write(p, W.VY, read(p, W.VY) << 1);
            if (read(p, W.VY) == 0) return;
            if ((short) read(p, W.VY) <= -0x1000) write(p, W.VY, -0x1000);
        }
        var child = s.spawn();
        if (child.isPresent()) {
            write(child.get(), B.ID, 0xB);
            write(child.get(), W.X, read(p, W.X)); // no subtype/Y/clear: dirty allocation is native.
        }
    }
}
