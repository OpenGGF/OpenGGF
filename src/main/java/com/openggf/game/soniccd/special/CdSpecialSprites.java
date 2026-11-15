package com.openggf.game.soniccd.special;

import java.util.ArrayList;
import java.util.List;

import static com.openggf.game.soniccd.special.CdSpecialMath.divs;
import static com.openggf.game.soniccd.special.CdSpecialMath.muls;

/**
 * The sub-CPU's sprite list for one update ({@code subSprites}): objects call
 * {@link #draw} ({@code DrawObject}, scddisasm/src/Special Stage/Sub.asm:849-1155) in run order, 3D objects queue into the
 * 64-level Z buffer ({@code Set3DObjectDraw}, scddisasm/src/Special Stage/Sub.asm:715-741) and are drawn near to far by
 * {@code Draw3DObjects} (scddisasm/src/Special Stage/Sub.asm:747-792). Entries are the 8-byte sprite records the program builds
 * (Y + 128, size, link, tile word, X + 128), at most $50.
 */
final class CdSpecialSprites {
    /** One object's drawing state: base tile, animation, flags and sprite position (screen + 128). */
    static final class Sprite {
        int tile;
        CdSpecialAnimationScripts scripts;
        final CdSpecialAnimationScripts.State anim = new CdSpecialAnimationScripts.State();
        int flags;
        int x;
        int y;
        int spriteFlag;

        Sprite(int tile, CdSpecialAnimationScripts scripts) {
            this.tile = tile;
            this.scripts = scripts;
        }

        void set(int id) { anim.set(scripts, id); }
        void change(int id) { anim.change(scripts, id); }
    }

    /** {@code subSprites} entries: y, size, link, tile, x (words; size and link bytes). */
    record Entry(int y, int size, int link, int tile, int x) { }

    static final int NO_ANIMATE = 1 << 1, NO_DRAW = 1 << 2;
    private static final int ORIGIN = 0x10000;
    private final byte[] program;
    final List<Entry> entries = new ArrayList<>();
    private final List<List<Sprite>> zBuffer = new ArrayList<>();

    CdSpecialSprites(byte[] program) {
        this.program = program;
        for (int i = 0; i < 64; i++) zBuffer.add(new ArrayList<>(8));
    }

    /** {@code move.b #0,spriteCount} and {@code InitZBuffer} at the start of the objects (Sub.asm:294-296, 695-705). */
    void begin() {
        entries.clear();
        for (List<Sprite> level : zBuffer) level.clear();
    }

    /** {@code Set3DObjectDraw}: queue at level min(d, $FFF) / $40, at most seven per level. */
    void queue3D(Sprite sprite, long distance) {
        long d = distance >= 64 * 0x40 ? 64 * 0x40 - 1 : distance;
        List<Sprite> level = zBuffer.get((int) ((d >> 6) & 0x3F));
        if (level.size() < 7) level.add(sprite);
        else level.set(6, sprite); // the eighth search overwrites the last slot (Sub.asm:729-738)
    }

    /** {@code Draw3DObjects}: each level in queue order, nearest level first. */
    void draw3D() {
        for (List<Sprite> level : zBuffer) for (Sprite s : level) draw(s);
    }

    /** {@code DrawObject}: step the animation, then emit the frame's pieces with flip and culling. */
    void draw(Sprite s) {
        CdSpecialAnimationScripts.Script script = s.scripts.script(s.anim.anim);
        if ((s.flags & NO_ANIMATE) == 0) s.anim.step(script);
        if ((s.flags & NO_DRAW) != 0) return;
        int pointer = script.frames()[s.anim.frame];
        int flip = (pointer >>> 24) & 0xFF;
        int at = (pointer & 0xFFFFFF) - ORIGIN;
        int count = program[at];
        if (count < 0) return;
        s.spriteFlag = program[at + 1] & 0xFF;
        at += 5;
        for (int i = 0; i <= count; i++, at += 5) {
            if (entries.size() >= 0x50) return;
            int yOff = program[at];
            int size = program[at + 1] & 0xFF;
            int tile = ((((program[at + 2] & 0xFF) << 8) | (program[at + 3] & 0xFF)) + s.tile) & 0xFFFF;
            int xOff = program[at + 4];
            int y;
            int x;
            switch (flip) {
                case 1 -> {
                    y = yOff;
                    tile ^= 0x0800;
                    x = (byte) -(byte) (xOff + ((((size << 1) + 8) & 0xF8)));
                }
                case 2 -> {
                    y = -yOff - (((size << 3) & 0x18) + 8);
                    tile ^= 0x1000;
                    x = xOff;
                }
                case 3 -> {
                    y = -yOff - (((size << 3) & 0x18) + 8);
                    tile ^= 0x1800;
                    x = -xOff - (((size << 1) & 0x18) + 8);
                }
                default -> { y = yOff; x = xOff; }
            }
            y = (short) (y + s.y);
            if (y <= -32 + 128 || y >= 256 + 128) continue;
            x = (short) (x + s.x);
            if (x <= -32 + 128 || x >= 320 + 128) continue;
            x &= 0x1FF;
            if (x == 0) x = 1;
            entries.add(new Entry(y & 0xFFFF, size, entries.size() + 1, tile, x));
        }
    }

    /**
     * {@code Set3DSpritePos} (scddisasm/src/Special Stage/Sub.asm:2348-2393): project an object relative to Sonic with the
     * view's yaw sines and FOV ({@code Init3DSpritePos}, scddisasm/src/Special Stage/Sub.asm:2327-2339).
     */
    static void project(Sprite s, CdSpecialStageView view, int sonicX, int sonicY, int x, int y, int z) {
        int dx = (short) (sonicX - x), dy = (short) (sonicY - y);
        int ycFov = (short) (muls(view.fov(), view.yawCos()) >> 8);
        int ysFov = (short) (muls(view.fov(), view.yawSin()) >> 8);
        int d2 = (view.fov() + view.center()) & 0xFFFF;
        d2 -= muls(view.yawSin(), dx) >> 8;
        d2 += muls(view.yawCos(), dy) >> 8;
        if (d2 == 0) d2 = 1;
        s.x = (short) (divs(muls(ycFov, dx) + muls(ysFov, dy), d2) + 256);
        s.y = (short) (divs(muls(view.fov(), z) >> 3, d2) + 256);
    }

    /** {@code GetAngle} then {@code GetDistance} (scddisasm/src/Special Stage/Sub.asm:1807-1966) as {@code ObjUFO_Draw} calls them. */
    static long distance(short[] distanceTable, int sonicX, int sonicY, int x, int y) {
        int d0 = x & 0xFFFF, d1 = y & 0xFFFF, d4 = sonicX & 0xFFFF, d5 = sonicY & 0xFFFF;
        int flags = 0;
        int d3 = d0 ^ d4;
        int diff = d0 - d4;
        boolean borrow = d0 < d4;
        d0 = diff & 0xFFFF;
        boolean signsDiffer = (d3 & 0x8000) != 0;
        if (borrow ? !signsDiffer : signsDiffer) { flags |= 1; d0 = (-d0) & 0xFFFF; }
        int dy = (short) (d1 - d5);
        if (dy < 0 && (short) d5 >= 0) { flags |= 2; dy = -dy; }
        d1 = dy & 0xFFFF;
        if (Integer.compareUnsigned(d1, d0) >= 0) { flags |= 4; int t = d0; d0 = d1; d1 = t; }
        int angle;
        if (d0 == 0) {
            angle = 0;
        } else {
            long dividend = ((long) (short) d1 << 6) & 0xFFFFFFFFL;
            long q = dividend / d0;
            angle = q > 0xFFFF ? (int) (dividend & 0xFF) : (int) (q & 0xFF); // divu overflow keeps the dividend
        }
        if (angle >= 0x40) angle = 0x3F;
        // GetDistance: d1 = angle, d3/d4 = UFO X/Y, d5/d6 = Sonic X/Y.
        int entry = distanceTable[angle] & 0xFFFF;
        int a = (flags & 4) != 0 ? (sonicY & 0xFFFF) : (sonicX & 0xFFFF);
        int b = (flags & 4) != 0 ? (y & 0xFFFF) : (x & 0xFFFF);
        int delta = (short) (a - b);
        if (delta < 0) delta = -delta;
        return ((long) entry * (delta & 0xFFFF)) >>> 8;
    }
}
