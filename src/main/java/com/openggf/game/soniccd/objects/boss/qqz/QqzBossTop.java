package com.openggf.game.soniccd.objects.boss.qqz;

import com.openggf.camera.Camera;
import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.level.CdStageCatalogue;
import com.openggf.game.soniccd.objects.boss.CdBossPart;
import com.openggf.level.objects.ObjectSpawn;

/**
 * The boss's top, object $33 ({@code BossTopObject}, objects/r5/boss.asm:89-220; IDs from
 * r5/object_index_3_act3.asm, the ID being the entry's position plus one), placed by the act 3 layout at ($DD0, $228).
 * It owns the level's {@code boss_flags} and {@code boss_started} (R5 Compatibility.asm:174-175: {@code bossFlags},
 * {@code bossFight}).
 *
 * <ul>
 *   <li>Routine 0 loads palette entry 6 and allocates the booth $38 pixels below; routine 2 raises
 *       {@code camYCenter} by 6 a frame and, the frame it reads $C8 or more, sets {@code boss_flags} to 5. It pins
 *       the right boundary at $CC0 and drags the left boundary to the player's X - $A0; from player X $D60 the boss
 *       music starts, {@code boss_started} is 5 and both boundaries lock at $CC0.</li>
 *   <li>Routine 6 shakes the bottom boundary by 2 for 8 frames after each booth landing.</li>
 *   <li>Routine 4 (set by the booth when it gives out) widens the right boundary 6 a frame to the stage's, then
 *       clears {@code boss_started}, plays the zone's future music and loads the capsule palette. {@code boss_flags}
 *       is never cleared.</li>
 * </ul>
 * It draws unless the booth is hidden.
 */
public final class QqzBossTop extends QqzBossPiece {
    /** BossTopObject is index entry $32 (r5/object_index_3_act3.asm:3-57). */
    public static final int ID = 0x33;
    /** BossTopObject_0_Routine0: move.w #6,d0; jsr LoadPalette (objects/r5/boss.asm:115-117). */
    static final int BOSS_PALETTE = 6;
    /** BossTopObject_0_Routine2: move.w #$67,d0 (SCMD_BOSSMUS); jsr SubCpuCommand (objects/r5/boss.asm:151-152). */
    static final int SCMD_BOSSMUS = 0x67;
    /** loc_20B4DE: $1A (SCMD_R5DMUS), $19 (SCMD_R5CMUS) in the good future (objects/r5/boss.asm:187-193). */
    static final int SCMD_R5CMUS = 0x19, SCMD_R5DMUS = 0x1A;
    /** move.b #5,boss_flags / boss_started (objects/r5/boss.asm:133, 153). */
    static final int BOSS_ACTIVE = 5;

    /** var_30: the booth. */
    QqzBossBooth booth;
    /** var_3e: frames of bottom-boundary shake left. */
    private int shake;
    private int bossFlags;
    private int bossFight;

    public QqzBossTop(ObjectSpawn spawn) {
        super(spawn, "QqzBossTop");
    }

    /** {@code boss_flags} (5 from the camera rise on; never cleared on this stage). */
    public int bossFlags() { return bossFlags; }

    /** {@code boss_started}: 5 during the fight, the touch gate of {@code PlayerObjectCollide}. */
    public int bossFight() { return bossFight; }

    @Override String sheet() { return QqzBossArt.TOP; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        skipDraw = false;
        switch (routine) {
            case 0 -> {
                init();
                waitForPlayer(player);
            }
            case 2 -> waitForPlayer(player);
            case 4 -> widenRightBound();
            case 6 -> shakeBottom();
            default -> { }
        }
        // movea.w obj.var_30(a0),a1; btst #3,obj.var_2c(a1); bne.s: no DrawObject while the booth is hidden.
        skipDraw = booth != null && booth.has(HIDDEN);
    }

    /** BossTopObject_0_Routine0 (objects/r5/boss.asm:114-126). */
    private void init() {
        loadPalette(BOSS_PALETTE);
        routine = 2;
        priority = 4;
        width = 0x20;
        yRadius = 0x18;
        tileWord = QqzBossArt.PART_TILE;
        // sub_20C2AE (objects/r5/boss.asm:1640-1652): the booth at Y + $38; its var_2e is this, this var_30 is it.
        QqzBossBooth newBooth = new QqzBossBooth(x, y + 0x38, this);
        if (spawn(newBooth)) booth = newBooth;
    }

    /** BossTopObject_0_Routine2 (objects/r5/boss.asm:128-165). */
    private void waitForPlayer(PlayableEntity player) {
        Camera camera = services().camera();
        if (camera == null || player == null) return;
        if (!has(LATCH)) {
            if (camera.getYPosBias() >= 0xC8) {                   // cmpi.w #$C8,scroll_focus_y; blt.s
                setBossFlags(BOSS_ACTIVE);
                set(LATCH);
            }
            camera.setYPosBias((short) (camera.getYPosBias() + 6));   // addq.w #6,scroll_focus_y
        }
        camera.setMaxX((short) 0xCC0);                             // right_bound, target_right_bound
        int px = (short) player.getCentreX();
        int left = (short) (px - 0xA0);
        if (left < camera.getMinX()) return;                       // cmp.w left_bound,d0; blt.s
        if (px >= 0xD60) {
            clear(LATCH);
            subCpu(SCMD_BOSSMUS);
            setBossFight(BOSS_ACTIVE);
            routine = 6;
            camera.setMaxX((short) 0xCC0);
            left = 0xCC0;
        }
        camera.setMinX((short) left);                              // left_bound, target_left_bound
    }

    /** BossTopObject_0_Routine4 (objects/r5/boss.asm:169-196). */
    private void widenRightBound() {
        Camera camera = services().camera();
        var level = cdLevel();
        var image = stageImage();
        if (camera == null || level == null || image == null) return;
        // lea word_202856+2,a1; move.w (a1)+,d0; move.w (a1)+,d1: d1 is the stage's right boundary (CamBounds: unused,
        // left, right, top, bottom).
        int stageRight = (short) image.u16(CdStageCatalogue.entry(level.payload()).camBounds() + 4);
        int right = (short) (camera.getMaxX() + 6);
        camera.setMaxX((short) right);                             // addq.w #6,right_bound and target_right_bound
        if (stageRight > right) return;                            // cmp.w right_bound,d1; ble.s
        setBossFight(0);
        camera.setMaxX((short) stageRight);
        subCpu(flags().goodFuture ? SCMD_R5CMUS : SCMD_R5DMUS);
        loadCapsulePalette();
        routine = 6;
    }

    /** BossTopObject_0_Routine6 (objects/r5/boss.asm:200-220): +2/-2 on bottomBound and destBottomBound. */
    private void shakeBottom() {
        if (booth != null && booth.clear(LANDED)) shake = 8;
        if (shake == 0) return;
        shake = (shake - 1) & 0xFF;
        int d = (shake & 1) != 0 ? -2 : 2;
        Camera camera = services().camera();
        if (camera == null) return;
        camera.setMaxYCurrent((short) (camera.getMaxY() + d));
        camera.setMaxYTarget((short) (camera.getMaxYTarget() + d));
    }

    /** {@code move.b #5,boss_flags}; the zone's presentation reads it through its scroll handler. */
    private void setBossFlags(int value) {
        bossFlags = value;
        var handler = scrollHandler();
        if (handler != null) handler.setBossFlags(value != 0);
    }

    /**
     * {@code boss_started}. It also gates the player's right boundary: {@code ObjSonic_LevelBound} drops its 56-pixel
     * extension while it is set (Level/_Objects/Sonic/Main.asm:1673-1691); the shared boundary reads that gate from
     * the screen-lock flag, so the boss drives it with {@code boss_started}.
     */
    private void setBossFight(int value) {
        bossFight = value;
        var state = services().gameState();
        if (state != null) state.setScreenLocked(value != 0);
    }

    @Override
    protected CdBossPart shell(ObjectSpawn spawn) {
        return new QqzBossTop(spawn);
    }
}
