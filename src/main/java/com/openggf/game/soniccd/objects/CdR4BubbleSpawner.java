package com.openggf.game.soniccd.objects;

import static com.openggf.game.soniccd.player.CdR4WaterAirPorts.*;
import static com.openggf.game.soniccd.player.CdR4WaterAirPorts.Value.*;

/** BubbleSpawnerObject, src/objects/r4/bubble_spawner.asm:3-289 at N=1ecc5f3.
 * USA air contact gate is explicit. Pattern/wobble bytes come only from the media owner.
 * No pool copies, clears on allocation, default clocks/RNG, art loader or shared Body changes.
 */
public final class CdR4BubbleSpawner {
    private CdR4BubbleSpawner() { }
    public enum ContactProfile { USA, JP_EU }
    public static void run(Slot o, Calls s, BubbleData data, ContactProfile profile) {
        int routine = read(o, B.ROUTINE);
        switch (routine) {
            case 0 -> {
                write(o, B.ROUTINE, read(o, B.ROUTINE) + 2); o.write(L.MAPPINGS, data.mappingsBits());
                write(o, W.TILE, 0x848A); write(o, B.SPRITE_FLAGS, 0x84);
                write(o, B.WIDTH_2, 0x10); write(o, B.LAYER, 1);
                int subtype = read(o, B.SUBTYPE);
                if ((byte) subtype < 0) {
                    write(o, B.ROUTINE, read(o, B.ROUTINE) + 8);
                    write(o, B.COUNTDOWN, subtype & 0x7F); write(o, B.RELOAD, subtype & 0x7F);
                    write(o, B.ANIM, 6); controller(o, s, data); return;
                }
                write(o, B.ANIM, subtype); write(o, W.ANCHOR_X, read(o, W.X)); write(o, W.VY, -0x88);
                write(o, B.ANGLE, s.random()); animateRise(o, s, data, profile);
            }
            case 2 -> animateRise(o, s, data, profile);
            case 4 -> rise(o, s, data, profile);
            case 6 -> pop(o, s);
            case 8 -> s.delete(o);
            case 10 -> controller(o, s, data);
            default -> throw new UnsupportedOperationException("BubbleSpawner routine " + routine);
        }
    }
    private static void animateRise(Slot o, Calls s, BubbleData data, ContactProfile profile) {
        s.animate(o, Animation.BUBBLE_SPAWNER);
        if (read(o, B.MAP_FRAME) == 6) write(o, B.ELIGIBLE, 1);
        rise(o, s, data, profile);
    }
    private static void rise(Slot o, Calls s, BubbleData data, ContactProfile profile) {
        if (read(s, WATER_Y) >= read(o, W.Y)) { startPop(o, s); return; }
        int angle = read(o, B.ANGLE); write(o, B.ANGLE, angle + 1);
        int wobble = data.wobble().signedByte(angle & 0x7F);
        write(o, W.X, wobble + read(o, W.ANCHOR_X));
        if (read(o, B.ELIGIBLE) != 0 && contact(o, s, profile)) {
            CdR4PlayerBubbles.resetAir(s); s.sound(0xAD);
            Slot p = s.player();
            write(p, W.VX, 0); write(p, W.VY, 0); write(p, W.GROUND_SPEED, 0);
            write(p, B.ANIM, 0x15); write(p, W.AIR_TIMER, 0x23); write(p, B.CHARGE, 0);
            write(p, B.FLAGS, read(p, B.FLAGS) & ~0x20);
            write(p, B.FLAGS, read(p, B.FLAGS) & ~0x10);
            if ((read(p, B.FLAGS) & 4) != 0) {
                write(p, B.FLAGS, read(p, B.FLAGS) & ~4);
                write(p, B.HEIGHT, 0x13); write(p, B.WIDTH, 9); write(p, W.Y, read(p, W.Y) - 5);
            }
            startPop(o, s); return;
        }
        s.move(o); drawOrDelete(o, s);
    }
    /** sub_20A81E: strict lower / inclusive upper contact edges, with WORD wrap. */
    public static boolean contact(Slot o, WaterState s, ContactProfile profile) {
        java.util.Objects.requireNonNull(profile);
        Slot p = s.player();
        if (profile == ContactProfile.USA && read(p, B.ANIM) == 0x17) return false;
        if ((byte) read(p, B.CONTROL) < 0) return false;
        int x = read(p, W.X), edge = (read(o, W.X) - 0x10) & 0xFFFF;
        if (edge >= x) return false;
        edge = (edge + 0x20) & 0xFFFF;
        if (edge < x) return false;
        int y = read(p, W.Y); edge = read(o, W.Y);
        if (edge >= y) return false;
        return ((edge + 0x10) & 0xFFFF) >= y;
    }
    private static void startPop(Slot o, Calls s) {
        write(o, B.ROUTINE, 6); write(o, B.ANIM, read(o, B.ANIM) + 3); pop(o, s);
    }
    private static void pop(Slot o, Calls s) { s.animate(o, Animation.BUBBLE_SPAWNER); drawOrDelete(o, s); }
    private static void drawOrDelete(Slot o, Calls s) {
        if ((byte) read(o, B.SPRITE_FLAGS) < 0) s.draw(o); else s.delete(o);
    }
    private static void controller(Slot o, Calls s, BubbleData data) {
        if (read(o, W.BURST) == 0) {
            if (read(s, WATER_Y) >= read(o, W.Y) || (byte) read(o, B.SPRITE_FLAGS) >= 0) {
                tail(o, s); return;
            }
            int timer = (read(o, W.TIMER) - 1) & 0xFFFF; write(o, W.TIMER, timer);
            if ((short) timer >= 0) { s.animate(o, Animation.BUBBLE_SPAWNER); tail(o, s); return; }
            write(o, W.BURST, 1);
            int random;
            // Native rejection loop consumes every rejected RNG call. No synthetic fallback/cap.
            do { random = s.random(); } while ((random & 7) >= 6);
            write(o, B.REMAINING, random & 7);
            o.write(L.PATTERN, data.spawnPatternsAddressBits() + (random & 0xC));
            int count = (read(o, B.COUNTDOWN) - 1) & 0xFF; write(o, B.COUNTDOWN, count);
            if ((byte) count < 0) {
                write(o, B.COUNTDOWN, read(o, B.RELOAD)); write(o, B.BURST_HIGH, read(o, B.BURST_HIGH) | 0x80);
            }
        } else {
            int timer = (read(o, W.TIMER) - 1) & 0xFFFF; write(o, W.TIMER, timer);
            if ((short) timer >= 0) { s.animate(o, Animation.BUBBLE_SPAWNER); tail(o, s); return; }
        }
        write(o, W.TIMER, s.random() & 0x1F);
        if ((byte) read(o, B.SPRITE_FLAGS) < 0) {
            var child = s.spawn();
            if (child.isPresent()) {
                Slot c = child.get(); write(c, B.ID, 0x20); write(c, W.X, read(o, W.X));
                int dx = (s.random() & 0xF) - 8; write(c, W.X, read(c, W.X) + dx);
                write(c, W.Y, read(o, W.Y));
                int index = read(o, B.REMAINING), pointer = o.read(L.PATTERN);
                // Read the ACTUAL reached programme source, not just a normalized 18-byte table.
                // A dirty raw pointer/index can legally reach another authenticated source byte.
                int subtype = checked(data.spawnPatterns().readByte(pointer, index), 1, "spawn pattern");
                write(c, B.SUBTYPE, subtype);
                if ((read(o, B.BURST_HIGH) & 0x80) != 0) {
                    boolean skip = false;
                    if ((s.random() & 3) == 0) {
                        if (markLarge(o)) write(c, B.SUBTYPE, 2); else skip = true;
                    }
                    if (!skip && read(o, B.REMAINING) == 0 && markLarge(o)) write(c, B.SUBTYPE, 2);
                }
            }
        }
        int remaining = (read(o, B.REMAINING) - 1) & 0xFF; write(o, B.REMAINING, remaining);
        if ((byte) remaining < 0) {
            int delay = (s.random() & 0x7F) + 0x80;
            write(o, W.TIMER, read(o, W.TIMER) + delay); write(o, W.BURST, 0);
        }
        s.animate(o, Animation.BUBBLE_SPAWNER); tail(o, s);
    }
    private static boolean markLarge(Slot o) {
        int old = read(o, B.BURST_HIGH); write(o, B.BURST_HIGH, old | 0x40); return (old & 0x40) == 0;
    }
    private static void tail(Slot o, Calls s) {
        if ((byte) read(o, B.SPRITE_FLAGS) >= 0) {
            int x = read(o, W.X) & 0xFF80, camera = (read(s, CAMERA_X) - 0x80) & 0xFF80;
            if (((x - camera) & 0xFFFF) > 0x280) { s.delete(o); return; }
        }
        if (read(s, WATER_Y) < read(o, W.Y)) s.draw(o);
        else write(o, B.SPRITE_FLAGS, read(o, B.SPRITE_FLAGS) & 0x7F);
    }
}
