package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.player.CdR4WaterAirPorts.WaterState;
import static com.openggf.game.soniccd.player.CdR4WaterAirPorts.*;
import static com.openggf.game.soniccd.player.CdR4WaterAirPorts.Value.*;

/** Sole WaterEvents/UpdateWater leaf: src/r4/main.asm:566-770 at native pin 1ecc5f3.
 * No FixBugs alternative. Root owns scheduling/capture; E consumes Projection, never re-runs sway.
 * Native writes remain reached prefixes on a read/call fault. Public values confer no live authority.
 */
public final class CdR4WaterEvents {
    private CdR4WaterEvents() { }
    /** Complete known projection of a returned UpdateWater. Unknown reads throw before construction. */
    public record Projection(int waterY, int split, boolean fullWater) {
        public Projection {
            checked(waterY, 2, "waterY"); checked(split, 1, "split");
        }
    }
    /** Pause returns without even reading the player, angle, sine or camera. */
    public static java.util.Optional<Projection> update(WaterState s) {
        if (read(s, PAUSED) != 0) return java.util.Optional.empty();
        if (read(s.player(), B.ROUTINE) < 6) events(s);
        write(s, WATER_FULL, 0);
        int angle = (read(s, WATER_ANGLE) + 2) & 0xFF;
        write(s, WATER_ANGLE, angle);
        int offset = swayOffset(s.sineCosine(angle).sine());
        write(s, WATER_Y, offset + read(s, STATIC_WATER_Y));
        int water = read(s, WATER_Y), camera = read(s, CAMERA_Y);
        int delta = (water - camera) & 0xFFFF;
        // SUB.W/BCC; only the borrow + negative result selects full-water.
        boolean full = water < camera && (short) delta < 0;
        if (full) {
            write(s, SPLIT, 0xDF); write(s, WATER_FULL, 1);
        }
        int split = delta >= 0xDF ? 0xDF : delta;
        write(s, SPLIT, split);
        return java.util.Optional.of(new Projection(water, split, full));
    }
    public static int swayOffset(int sineWord) { return (byte) ((sineWord & 0xFFFF) >>> 6); }
    public static void events(WaterState s) {
        int act = read(s, ACT);
        switch (act) {
            case 0 -> act1(s);
            case 1 -> act2(s);
            case 2 -> act3(s);
            default -> throw new UnsupportedOperationException("WaterEvents act directory BYTE " + act);
        }
        int speed = read(s, WATER_SPEED); // zero-extended raw BYTE, not signed or boolean.
        int target = read(s, TARGET_WATER_Y), current = read(s, STATIC_WATER_Y);
        if (target != current) write(s, STATIC_WATER_Y, current + (target < current ? -speed : speed));
    }
    private static void act1(WaterState s) {
        int x = read(s.player(), W.X), era = read(s, TIME_ZONE) & 0x7F;
        if (era == 0) return;
        int target;
        if (era == 1) target = x < 0x5E0 ? 0x280 : 0x260;
        else {
            target = x < 0x920 ? 0x1D0 : x < 0x1980 ? 0x110 : 0x240;
            if (x >= 0x1980) write(s, WATER_SPEED, 3);
        }
        write(s, TARGET_WATER_Y, target);
    }
    private static void act2(WaterState s) {
        int x = read(s.player(), W.X), target = (read(s, CAMERA_Y) + 0xE8) & 0xFFFF;
        if (x < 0x200 || (read(s.player(), W.Y) >= 0x600 && read(s.player(), W.Y) < 0x6C0 && x < 0x2A0)) {
            write(s, STATIC_WATER_Y, target); write(s, TARGET_WATER_Y, target); return;
        }
        int era = read(s, TIME_ZONE) & 0x7F;
        if (era == 0) {
            write(s, STATIC_WATER_Y, 0x5B0); write(s, TARGET_WATER_Y, 0x5B0); return;
        }
        if (era == 1) {
            target = x < 0xC00 ? 0x530 : x < 0xDC0 ? 0x780 : 0x500;
            if (x >= 0xDC0 && x < 0x1380) {
                if (read(s.player(), W.Y) >= 0x400 || x < 0x1100) return; // REAL target nonwrite.
                target = 0x3C0;
            }
        } else target = x < 0x400 ? 0x510 : x < 0x1600 ? 0x390 : x < 0x1900 ? 0x410 : 0x500;
        write(s, TARGET_WATER_Y, target);
    }
    private static void act3(WaterState s) {
        int x = read(s.player(), W.X), target = (read(s, CAMERA_Y) + 0xE8) & 0xFFFF;
        if (x < 0x7C0) write(s, STATIC_WATER_Y, target);
        else if (x < 0xAF0) target = 0x490;
        else { write(s, WATER_SPEED, 2); target = 0x5C0; }
        write(s, TARGET_WATER_Y, target);
    }
}
