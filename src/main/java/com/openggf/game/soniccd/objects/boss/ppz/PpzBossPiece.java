package com.openggf.game.soniccd.objects.boss.ppz;

import com.openggf.game.soniccd.objects.boss.CdBossPart;
import com.openggf.level.objects.ObjectSpawn;

/**
 * State every Palmtree Panic boss part shares (objects/r1/boss.asm:3-2747). The routines name their
 * scratch bytes by offset; these fields name them by use, the same way in every part:
 * <ul>
 *   <li>{@link #flags}: the part's handshake byte. Bit 0 {@link #DONE} (a step finished; the body and
 *       Eggman poll and clear it), bit 1 {@link #STARTED} (first pass of a routine), bit 2 {@link #BACK}
 *       (the far-side leg or arm: drawn behind, X - $A, the back arm not drawn), bit 3 {@link #FIGHT}
 *       (Eggman: hit checks on), bit 4 {@link #PLANTED} (a leg's foot is on the floor; a bumper's push),
 *       bit 5 {@link #CARRY} (the planted leg moves the body), bit 6 {@link #SWAP} (body: which leg leads;
 *       shoulders: drop the front arm), bit 7 {@link #FAST} (fast stride).</li>
 *   <li>{@link #angle}: a limb's joint angle byte, or a counter.</li>
 *   <li>{@link #phase}, {@link #steps}: counters (Eggman's hit count and timers, the body's step count).</li>
 *   <li>{@link #offsetX}, {@link #offsetY}: 16.16 offsets from the parent joint; {@link #stepX},
 *       {@link #stepY}: 16.16 per-frame steps (the second is the {@code x_speed} long, which spans the
 *       X and Y velocity words).</li>
 * </ul>
 */
abstract class PpzBossPiece extends CdBossPart {
    static final int DONE = 0x01, STARTED = 0x02, BACK = 0x04, FIGHT = 0x08, PLANTED = 0x10, CARRY = 0x20,
            SWAP = 0x40, FAST = 0x80;
    /** {@code cmpi.w #$240,obj.y(a0); blt}: a falling part is deleted below this line (e.g. objects/r1/boss.asm:1416-1419). */
    static final int FALL_DELETE_Y = 0x240;

    int flags;
    int angle;
    int phase;
    int steps;
    long offsetX;
    long offsetY;
    long stepX;
    long stepY;

    PpzBossPiece(int id, int x, int y, String name) {
        super(id, x, y, name);
    }

    PpzBossPiece(ObjectSpawn spawn, String name) {
        super(spawn, name);
    }

    final boolean has(int bit) { return (flags & bit) != 0; }

    final void set(int bit) { flags |= bit; }

    /** {@code bclr}: clears the bit and returns whether it was set. */
    final boolean clear(int bit) {
        boolean was = (flags & bit) != 0;
        flags &= ~bit;
        return was;
    }

    /** {@code bset}: sets the bit and returns whether it was already set. */
    final boolean testAndSet(int bit) {
        boolean was = (flags & bit) != 0;
        flags |= bit;
        return was;
    }

    /** {@code bchg}: toggles the bit and returns whether it was set. */
    final boolean toggle(int bit) {
        boolean was = (flags & bit) != 0;
        flags ^= bit;
        return was;
    }

    /** {@code move.w obj.var_34(a0),d0}: the offset's pixel word. */
    static int word(long fixed) { return (short) (fixed >> 16); }

    /** {@code move.w #n,obj.var_34(a0)}: writes the pixel word and keeps the fraction. */
    static long withWord(long fixed, int value) {
        return ((long) (short) value << 16) | (fixed & 0xFFFF);
    }

    /** The shared falling move: {@code add.l stepX,oX; add.l stepY,oY}; returns true once deleted. */
    final boolean fallAndCheck() {
        addXLong(stepX);
        addYLong(stepY);
        if (yWord() >= FALL_DELETE_Y) {
            deleteObject();
            return true;
        }
        return false;
    }

    /** {@code CheckBlockDown} ({@code ObjGetFloorDist}): the floor distance below the part, by {@code oYRadius}. */
    final int floorDistance() {
        return com.openggf.physics.ObjectTerrainUtils.checkFloorDist(x, y, yRadius).distance();
    }

    /** {@code SineCosine}: d0 = sine, d1 = cosine of the byte angle. */
    static int sine(int angle) { return com.openggf.physics.TrigLookupTable.sinHex(angle & 0xFF); }

    static int cosine(int angle) { return com.openggf.physics.TrigLookupTable.cosHex(angle & 0xFF); }

    @Override
    protected String sheetKey() {
        return PpzBossArt.key(artProvider(), sheet());
    }

    /** The part's sheet kind ({@link PpzBossArt}). */
    abstract String sheet();
}
