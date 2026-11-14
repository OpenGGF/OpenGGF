package com.openggf.game.soniccd.objects.boss.qqz;

import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.SonicCdZoneRegistry;
import com.openggf.game.soniccd.objects.boss.CdBossPart;
import com.openggf.game.soniccd.zones.qqz.QqzScrollHandler;
import com.openggf.level.objects.ObjectSpawn;

/**
 * State every Quartz Quadrant boss part shares (objects/r5/boss.asm:3-1821). The routines name their scratch bytes
 * by offset; these fields name them by use:
 * <ul>
 *   <li>{@link #flags} ({@code var_2c}): the booth's handshake byte, which the other parts read. Bit 1
 *       {@link #LATCH} (top: the camera rise is done; booth: this wear step's spark is out), bit 2 {@link #BEATEN}
 *       (booth and Eggman: the booth's wear ran out), bit 3 {@link #HIDDEN} (booth: the booth, its back, the pipes and
 *       the top are not drawn), bit 4 {@link #AIRBORNE} (belt: the player left the belt this frame), bit 6
 *       {@link #RELEASE} (dropper: let the bomb go), bit 7 {@link #LANDED} (booth: a drop landed; the top shakes the
 *       bottom boundary).</li>
 *   <li>{@link #timer} ({@code var_2a}) and {@link #step} ({@code var_2d}): counters.</li>
 * </ul>
 */
abstract class QqzBossPiece extends CdBossPart {
    static final int LATCH = 0x02, BEATEN = 0x04, HIDDEN = 0x08, AIRBORNE = 0x10, RELEASE = 0x40, LANDED = 0x80;

    int flags;
    int timer;
    int step;
    /** This frame's routine ended in {@code addq.l #4,sp} or skipped {@code DrawObject}. */
    boolean skipDraw;

    QqzBossPiece(int id, int x, int y, String name) {
        super(id, x, y, name);
    }

    QqzBossPiece(ObjectSpawn spawn, String name) {
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

    /**
     * sub_20C3FE / loc_20C400 (objects/r5/boss.asm:1764-1773): {@code move.b d0,obj.anim_id}; the map frame, the
     * animation frame and timer and {@code var_1f} cleared. Other parts call it on Eggman and the dropper.
     */
    final void setPose(int newAnim) { restartAnimation(newAnim); }

    /** {@code obj.anim_id}. */
    final int anim() { return anim; }

    /** The loaded stage's payload, or null. */
    final CdStagePayload payload() {
        var level = cdLevel();
        return level == null ? null : level.payload();
    }

    /** {@code SineCosine}: the sine of the byte angle (common/math.asm:3-10). */
    static int sine(int angle) { return com.openggf.physics.TrigLookupTable.sinHex(angle & 0xFF); }

    /**
     * BossExplosionObject_0_Routine4 (objects/r5/boss.asm:1047-1049), the shared exit of several routines:
     * {@code addq.l #4,sp; jmp DeleteObject}: the slot is cleared and nothing after the routine runs.
     */
    final void deleteAndSkip() {
        deleteObject();
        skipDraw = true;
    }

    /**
     * sub_20C420 (objects/r5/boss.asm:1789-1820): nonzero when the part's box ({@code oWidth}, {@code oYRadius}) is
     * entirely off the 320x224 screen at the camera position the objects see ({@code scroll_fg_x/y}).
     */
    final boolean offScreen() {
        var camera = services().camera();
        if (camera == null) return false;
        int dx = (short) (x - camera.getX());
        if ((short) (dx + width) < 0 || (short) (dx - width) >= 0x140) return true;
        int dy = (short) (y - camera.getY());
        return (short) (dy + yRadius) < 0 || (short) (dy - yRadius) >= 0xE0;
    }

    /**
     * loc_20BC7C (objects/r5/boss.asm:943-953): a bomb that touched the player becomes the explosion in its own slot
     * ({@code DeleteObject}; {@code st obj.routine_2}; ID $18 at its position: the non-badnik form) and
     * {@code FM_EXPLODE} plays; {@code addq.l #4,sp} skips its {@code DrawObject}.
     */
    final void becomeBossExplosion() {
        skipDraw = true;
        becomeExplosion(0, 0, 0, false);
        fmSound(FM_EXPLODE);
    }

    /** The Quartz Quadrant scroll handler, which holds {@code boss_flags} for the zone's presentation, or null. */
    final QqzScrollHandler scrollHandler() {
        var level = cdLevel();
        var parallax = services().parallaxManager();
        return level != null && parallax != null
                && parallax.getHandler(SonicCdZoneRegistry.zoneIndex(level.payload())) instanceof QqzScrollHandler h
                ? h : null;
    }

    /** The live boss top ({@link QqzBossTop}), which owns {@code boss_flags} and {@code boss_started}, or null. */
    final QqzBossTop top() {
        var objects = services().objectManager();
        if (objects == null) return null;
        for (var object : objects.getActiveObjects()) {
            if (object instanceof QqzBossTop top && !top.isDestroyed()) return top;
        }
        return null;
    }

    @Override protected boolean drawn() { return routine != 0 && !skipDraw; }

    @Override
    protected String sheetKey() {
        return artProvider() == null || sheet() == null ? null : QqzBossArt.key(sheet());
    }

    /** The part's sheet kind ({@link QqzBossArt}). */
    abstract String sheet();
}
