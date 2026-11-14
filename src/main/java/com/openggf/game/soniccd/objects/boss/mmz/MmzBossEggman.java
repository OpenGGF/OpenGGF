package com.openggf.game.soniccd.objects.boss.mmz;

import com.openggf.camera.Camera;
import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.level.CdStageCatalogue;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.game.soniccd.objects.boss.CdBossPart;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchResponseAttackable;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * The final boss, object $3F ({@code EggmanObject}, objects/r8/boss.asm:3-1916; ID = entry position + 1 in
 * r8/object_index_3.asm:63-69). The Hotaru intro allocates it at ($F00, $13C) (HotaruObject_0_RoutineC,
 * objects/r8/hotaru.asm:567-575). Eggman runs the fight; the machine around him is the Egg Mobile ($41) with its four
 * rotating panels ($40). Both futures run the same code; the bad future's intro fade also lights palette line 3
 * ({@code BossFadeFromBlack}, common/palette_fade.asm:94-116).
 *
 * <p>Every frame (EggmanObject, :3-42): clear the Egg Mobile's rotate bits 5 and 6, laugh when the player is hurt or
 * dead, time out the pose, run the hit checks, take the next script step when the last one is done
 * ({@link #DONE}), run the routine, animate, cycle the boss palette and draw unless the Egg Mobile's immunity count is
 * odd. The script ({@code off_20C030}) is one list per hit count (4 to 1) of {routine, -, target word}.
 *
 * <ul>
 *   <li>Routine 2: the right boundary $E60; the left boundary follows the player (X - $A0). From player X $F00: boss
 *       flags 8, the Egg Mobile, both boundaries $E60.</li>
 *   <li>Routine 4: the intro: palettes 7, 8, 9, PLC 4, $F0 frames, music $68 and {@code boss_started} bit 7 (the
 *       boss door opens), the fade from black over $3F frames, $28 frames, then the fight.</li>
 *   <li>Routines 6-$30: script steps (wait, move, sweep, swing, charge, panel drops, the sine hop).</li>
 *   <li>Four hits; each restarts the script for the hits left and drops a panel picked by the player's side and the
 *       panels' angle. The last hit: routine $32 explodes for 90 frames and allocates Amy ($43); $34 drifts right to
 *       X $FE0, loads palette $B and clears {@code boss_started}; $36 keeps exploding.</li>
 * </ul>
 *
 * <p>Touch: Eggman ($32) and the Egg Mobile ($33) are enemy types with a status of 2: the player's attack rebounds
 * ({@code Player_TouchEnemy}, r8/player_object_collide.asm:150-163) and clears the type, which these checks read as
 * a hit; anyone else is hurt.
 */
final class MmzBossEggman extends MmzBossPiece implements TouchResponseAttackable {
    /** EggmanObject is index entry $3E (r8/object_index_3.asm:3-69). */
    static final int ID = 0x3F;
    /** var_2c bits: 0 a script step is done, 3 the hit checks run. */
    static final int DONE = 0x01, STARTED = 0x02, FIGHT = 0x08;
    /** loc_20B2C8: move.b #$32 / #$33,obj.collide_type; move.b #2,obj.collide_status (scddisasm/src/objects/r8/boss.asm:106-111). */
    static final int EGGMAN_COLLISION = 0x32, EGG_MOBILE_COLLISION = 0x33, COLLISION_STATUS = 2;
    /** loc_20B2E2: move.w #$AC,d0; jsr PlayFmSound (scddisasm/src/objects/r8/boss.asm:131-132). */
    static final int FM_HIT = 0xAC;
    /** EggmanObject_0_Routine1A: move.w #$C9,d0 (scddisasm/src/objects/r8/boss.asm:647-648). */
    static final int FM_LAUNCH = 0xC9;
    /** loc_20BDE0 / loc_20BE58: move.w #$B4,d0 (scddisasm/src/objects/r5/boss.asm:1292, 1332). */
    static final int FM_LAND = 0xB4;
    /** loc_20B9E6: move.w #$7E,d0; jsr SubCpuCommand (SCMD_STOMPSFX, scddisasm/src/objects/r8/boss.asm:865-866). */
    static final int SCMD_STOMP = 0x7E;
    /** loc_20B548: move.w #$68,d0; jsr SubCpuCommand (scddisasm/src/objects/r8/boss.asm:342-343). */
    static final int SCMD_FINAL_BOSS = 0x68;
    /** EggmanObject_0_Routine4: LoadPalette 7, 8, 9 (boss.asm:322-327); loc_20C2A2: LoadPalette $B (:1900-1903). */
    static final int PALETTE_TARGET = 7, PALETTE_DARK = 8, PALETTE_DARK2 = 9, PALETTE_ESCAPE = 0xB;
    /** The palette buffer and {@code fadePalette} (Level/_Definitions.inc:340-341). */
    private static final int PALETTE_RAM = 0xFB00, FADE_PALETTE_RAM = 0xFB80;

    MmzBossEggMobile eggMobile;
    /** var_3a: hits left; var_3b: the script index; var_3c: pose frames; var_3d/var_3e: the boss palette cycle. */
    int hitsLeft;
    private int scriptIndex;
    private int poseTimer;
    private int paletteIndex;
    private int paletteTimer;
    /** var_34 and the x_speed long: the 16.16 X and Y steps (sub_20BFA4). */
    private long stepX;
    private long stepY;
    /** var_30 / var_32: a target X (routine $10) or the sine hop's last X/Y offsets (sub_20BF4A). */
    private int offX;
    private int offY;
    private boolean hidden;
    /** {@code fadePalette}: the colours {@code BossFadeFromBlack} fades toward (LoadFadePalette, palette 7). */
    private int[] fadePalette;

    MmzBossEggman(int x, int y) {
        super(ID, x, y, "MmzBossEggman");
    }

    private MmzBossEggman(ObjectSpawn spawn) {
        super(spawn, "MmzBossEggman");
    }

    @Override String sheet() { return MmzBossArt.EGGMAN; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        AbstractPlayableSprite sonic = player instanceof AbstractPlayableSprite s ? s : null;
        if (eggMobile != null) eggMobile.clear(MmzBossEggMobile.ROTATE_UP | MmzBossEggMobile.ROTATE_DOWN);
        laugh(sonic);
        if (poseTimer != 0) {
            poseTimer = (poseTimer - 1) & 0xFF;
            if (poseTimer == 0) anim = 0;                         // move.b #0,obj.anim_id(a0)
        }
        hitChecks(sonic);
        if (clear(DONE)) nextScriptStep();
        switch (routine) {
            case 0 -> init();
            case 2 -> waitForPlayer(sonic);
            case 4 -> intro();
            case 6 -> waitSteps();
            case 8 -> moveRight();
            case 0xA -> moveLeft();
            case 0xC -> moveDown();
            case 0xE -> moveUp();
            case 0x10 -> sweep();
            case 0x12 -> rise();
            case 0x14 -> toggleRotation();
            case 0x16 -> settlePanels();
            case 0x18 -> expandPanels();
            case 0x1A -> launchPanels();
            case 0x1C -> retractPanels();
            case 0x1E -> dropPanels();
            case 0x20 -> reattachPanels();
            case 0x22, 0x24 -> swing(sonic);
            case 0x26 -> charge(sonic);
            case 0x28 -> returnUp();
            case 0x2A -> sideDrop(sonic);
            case 0x2C -> sideHop(sonic);
            case 0x2E -> bounceAcross();
            case 0x30 -> hopHalf();
            case 0x32 -> defeated();
            case 0x34 -> escape();
            case 0x36 -> exploding();
            default -> { }
        }
        animate(table("eggmanAnimations"));                       // lea EggmanAnims,a1; jsr AnimateObject
        cycleBossPalette();
        // movea.w obj.var_2e(a0),a2; btst #0,obj.var_3c(a2); bne: no DrawObject. Before the Egg Mobile exists var_2e
        // is 0 and the byte read is $00003C in the BIOS vector table, taken here as a clear bit (Eggman is drawn).
        hidden = eggMobile != null && (eggMobile.immunity & 1) != 0;
    }

    @Override protected boolean drawn() { return !hidden; }

    /** var_3a: the hit count the test harness and the panels read. */
    int hitsLeft() { return hitsLeft; }

    // ---- every frame --------------------------------------------------------------------------------------------

    /** sub_20B416 (scddisasm/src/objects/r8/boss.asm:244-261): pose 4 for $78 frames while the player is hurt ({@code var_30}) or dead (routine 6). */
    private void laugh(AbstractPlayableSprite player) {
        if (anim != 0 || player == null) return;
        if (player.getInvulnerableFrames() == 0 && !player.getDead()) return;
        poseTimer = 0x78;
        anim = 4;
    }

    /** sub_20B29E (scddisasm/src/objects/r8/boss.asm:83-241). */
    private void hitChecks(AbstractPlayableSprite player) {
        if (!has(FIGHT) || eggMobile == null) return;
        MmzBossEggMobile mobile = eggMobile;
        mobile.panelSelect = 0xFF;
        if (mobile.immunity != 0) {
            mobile.immunity = (mobile.immunity - 1) & 0xFF;
            if (mobile.immunity == 0) enableCollision();
            return;
        }
        if (colType() != 0 && mobile.colType() != 0) return;
        if (routine == 0x1E) {                                    // cmpi.b #$1E,obj.routine(a0); beq loc_20B2C8
            enableCollision();
            return;
        }
        setCollision(0, 0);
        mobile.setCollision(0, 0);
        spawnSparks();
        poseTimer = 0x96;
        anim = 1;
        mobile.immunity = 0x78;
        scriptIndex = 0;
        step = 0;
        set(DONE);
        fmSound(FM_HIT);
        hitsLeft = (hitsLeft - 1) & 0xFF;
        loadSpeeds();
        if (hitsLeft == 0) {
            // loc_20B362: the defeat; panel 0 is selected (loc_20B3F6).
            routine = 0x32;
            clear(DONE);
            timer = 0;
            mobile.panelSelect = 0;
            return;
        }
        int frame = (byte) mobile.panelFrame;                     // move.b $35(a2),d0
        boolean right = player != null && (short) player.getCentreX() >= xWord();   // cmp.w obj.x(a0),d1; bge
        mobile.panelSelect = switch (hitsLeft) {
            case 3 -> right ? pick(frame, 0xD, 4, 8) : (frame <= 3 ? 2 : frame <= 7 ? 1 : frame <= 0xB ? 0 : 3);
            case 2 -> right ? pick(frame, 0xC, 4, -1) : (frame >= 0xD || frame <= 2 ? 2 : frame <= 7 ? 1 : 0);
            case 1 -> {
                // loc_20B378: AddGfxQueue #5 (Amy's art; the sheet is registered at the stage load).
                yield right ? 0 : 1;
            }
            default -> {
                hitsLeft = 0;                                     // clr.b obj.var_3a(a0): never reached
                yield mobile.panelSelect;
            }
        };
    }

    /**
     * loc_20B3DC / loc_20B3AA (scddisasm/src/objects/r8/boss.asm:182-216): with the player on the right, frame {@code >= high} or 0 picks
     * panel 2, {@code <= one} panel 1, {@code <= zero} panel 0 (when {@code zero >= 0}), else 3 (or 0 without a
     * fourth band).
     */
    private static int pick(int frame, int high, int one, int zero) {
        if (frame >= high || frame == 0) return 2;
        if (frame <= one) return 1;
        if (zero < 0) return 0;
        return frame <= zero ? 0 : 3;
    }

    /** loc_20B2C8 (scddisasm/src/objects/r8/boss.asm:106-111). */
    private void enableCollision() {
        setCollision(EGGMAN_COLLISION, COLLISION_STATUS);
        if (eggMobile != null) eggMobile.setCollision(EGG_MOBILE_COLLISION, COLLISION_STATUS);
    }

    /** sub_20BFF6 (scddisasm/src/objects/r8/boss.asm:1494-1518): the next {routine, -, target} of the hit count's script; 0 restarts it. */
    private void nextScriptStep() {
        CdStageImage image = image();
        int base = table("eggmanScripts");
        if (image == null || base < 0) return;
        for (int guard = 0; guard < 2; guard++) {
            int list = base + (short) image.u16(base + hitsLeft * 2);
            int entry = list + scriptIndex * 4;
            if (image.u16(entry) == 0) {                          // tst.w (a3); bne
                scriptIndex = 0;
                continue;
            }
            scriptIndex = (scriptIndex + 1) & 0xFF;
            routine = image.u8(entry);
            target = image.u16(entry + 2);
            step = 0;
            return;
        }
    }

    /** sub_20BFA4 (scddisasm/src/objects/r8/boss.asm:1457-1491): per hit count, the X and Y steps and the Egg Mobile's rotation period. */
    private void loadSpeeds() {
        CdStageImage image = image();
        int base = table("eggmanSpeeds");
        if (image == null || base < 0) return;
        int entry = base + hitsLeft * 0xA;
        stepX = image.u32(entry) & 0xFFFFFFFFL;
        stepY = image.u32(entry + 4) & 0xFFFFFFFFL;
        if (eggMobile != null) eggMobile.period = image.u8(entry + 8);
    }

    /** sub_20CF62 (scddisasm/src/objects/r8/boss.asm:3128-3145): two sparks at Eggman, sides 0 and 1. */
    private void spawnSparks() {
        for (int side = 0; side < 2; side++) {
            if (!spawn(new MmzBossSparks(x, y, this, side))) return;
        }
    }

    /**
     * sub_20CF94 (scddisasm/src/objects/r8/boss.asm:3149-3184): on even counts an explosion at ($F00 + rnd % $80, $180 + rnd % $50) from one
     * {@code Random} long (high word for X, low word for Y; {@code divs} remainders keep the dividend's sign).
     * The allocation comes first: a failed one draws no random number. Here the number is drawn before the
     * allocation, which differs only when no slot is free.
     */
    private void explosions() {
        if ((timerHigh() & 1) != 0) return;
        int random = services().rng().nextRaw();
        int ex = (short) (random >> 16) % 0x80 + 0xF00;
        int ey = (short) random % 0x50 + 0x180;
        explode(ex, ey);
    }

    /** CycleBossPalette (r8/palette_cycle_c.asm:109-125): every 4 frames while {@code boss_started}, line 1 colours 8-11. */
    private void cycleBossPalette() {
        if (bossStarted() == 0) return;
        paletteTimer = (paletteTimer + 1) & 0xFF;
        if (paletteTimer < 4) return;
        paletteTimer = 0;
        int index = paletteIndex + 8;
        if (index >= 0x50) index = 0;
        paletteIndex = index;
        CdStageImage image = image();
        int base = table("bossPaletteCycle");
        if (image == null || base < 0) return;
        int[] words = new int[4];
        for (int i = 0; i < 4; i++) words[i] = image.u16(base + index + i * 2);
        writePaletteWords(0x18, words);                           // lea (palette+$30).w,a4
    }

    // ---- routines -----------------------------------------------------------------------------------------------

    /** EggmanObject_0_Routine0 (boss.asm:265-275). */
    private void init() {
        routine = 2;
        priority = 5;
        width = 0x18;
        yRadius = 0x10;
        tileWord = MmzBossArt.EGGMAN_TILE;
        hitsLeft = 4;
        y = (y - 0x100) & 0xFFFF;
    }

    /** EggmanObject_0_Routine2 (boss.asm:279-308). */
    private void waitForPlayer(AbstractPlayableSprite player) {
        Camera camera = services().camera();
        if (camera == null || player == null) return;
        camera.setMaxX((short) 0xE60);                            // right_bound, target_right_bound
        int px = (short) player.getCentreX();
        int left = (short) (px - 0xA0);
        if (left < camera.getMinX()) return;
        if (px >= 0xF00) {
            var signals = signals();
            if (signals != null) signals.setBossFlags(8);         // move.b #8,boss_flags
            setBossStarted(8);                                    // move.b #8,boss_started
            spawnEggMobile();
            loadSpeeds();
            clear(STARTED);
            routine = 4;
            camera.setMaxX((short) 0xE60);
            left = 0xE60;
        }
        camera.setMinX((short) left);                             // left_bound, target_left_bound
    }

    /** sub_20CF00 (scddisasm/src/objects/r8/boss.asm:3089-3100): {@code SpawnObjectAfter}, linked both ways, at Eggman. */
    private void spawnEggMobile() {
        MmzBossEggMobile mobile = new MmzBossEggMobile(x, y, this);
        var objects = services().objectManager();
        if (objects == null) return;
        objects.addDynamicObjectAfterCurrent(mobile);
        if (!mobile.isDestroyed()) eggMobile = mobile;
    }

    /** EggmanObject_0_Routine4 (boss.asm:312-394). */
    private void intro() {
        switch (step) {
            case 1 -> introWait();
            case 2 -> introFade();
            case 3 -> introPause();
            default -> {
                anim = 5;
                step = (step + 1) & 0xFF;
                loadPaletteEntry(PALETTE_TARGET);
                loadPaletteEntry(PALETTE_DARK);
                loadPaletteEntry(PALETTE_DARK2);
                // AddGfxQueue #4: the boss art (MmzBossArt registers it at the stage load).
                introWait();
            }
        }
    }

    /** loc_20B53A / loc_20B548 (scddisasm/src/objects/r8/boss.asm:332-349): $F0 frames, then the door opens and the music starts. */
    private void introWait() {
        timer = (timer + 1) & 0xFFFF;
        if (timer != 0xF0) return;
        y = (y + 0x100) & 0xFFFF;
        subCpu(SCMD_FINAL_BOSS);
        setBossStarted(bossStarted() | 0x80);                     // bset #7,boss_started
        timer = 0;
        step = (step + 1) & 0xFF;
        anim = 2;
    }

    /**
     * loc_20B572 (scddisasm/src/objects/r8/boss.asm:353-374): {@code BossFadeFromBlack} when {@code d0 / 3} has no remainder, for $3F frames.
     * {@code move.b obj.var_2a(a0),d0} only replaces d0's low byte; the rest still holds the routine's jump-table
     * offset from the dispatch (FixBugs = 0: the dividend is that offset's high byte times 256 plus the count, so the
     * fade steps fall where the sum is a multiple of 3, not every third count from 0).
     */
    private void introFade() {
        int dividend = (dispatchOffset(4) & 0xFF00) | timerHigh();
        if (dividend % 3 == 0) bossFadeFromBlack();
        setTimerHigh(timerHigh() + 1);
        if (timerHigh() != 0x3F) return;
        step = (step + 1) & 0xFF;
        anim = 0;
        setTimerHigh(0);
    }

    /** The word the dispatch read for {@code routine} ({@code move.w off_20B266(pc,d0.w),d0}, boss.asm:28-31, 51-79). */
    private int dispatchOffset(int forRoutine) {
        CdStageImage image = image();
        int jumps = table("eggmanDispatch");
        return image == null || jumps < 0 ? 0 : image.u16(jumps + forRoutine);
    }

    /** loc_20B5AE / loc_20B5BC (scddisasm/src/objects/r8/boss.asm:378-394): $28 frames, then the fight. */
    private void introPause() {
        setTimerHigh(timerHigh() + 1);
        if (timerHigh() != 0x28) return;
        set(DONE);
        setTimerHigh(0);
        set(FIGHT);
        enableCollision();
    }

    /** EggmanObject_0_Routine6 (scddisasm/src/objects/r8/boss.asm:398-410): wait {@code var_39} frames. */
    private void waitSteps() {
        setTimerHigh(timerHigh() + 1);
        if (targetLow() != timerHigh()) return;
        setTimerHigh(0);
        set(DONE);
    }

    /** EggmanObject_0_Routine8 (scddisasm/src/objects/r8/boss.asm:414-427). */
    private void moveRight() {
        addXLong(stepX);
        if (targetWord() > xWord()) return;
        x = target & 0xFFFF;
        set(DONE);
    }

    /** EggmanObject_0_RoutineA (scddisasm/src/objects/r8/boss.asm:431-444). */
    private void moveLeft() {
        addXLong(-stepX);
        if (targetWord() < xWord()) return;
        x = target & 0xFFFF;
        set(DONE);
    }

    /** EggmanObject_0_RoutineC (scddisasm/src/objects/r8/boss.asm:448-461). */
    private void moveDown() {
        addYLong(stepY);
        if (targetWord() > yWord()) return;
        y = target & 0xFFFF;
        set(DONE);
    }

    /** EggmanObject_0_RoutineE (scddisasm/src/objects/r8/boss.asm:465-478). */
    private void moveUp() {
        addYLong(-stepY);
        if (targetWord() < yWord()) return;
        y = target & 0xFFFF;
        set(DONE);
    }

    /** EggmanObject_0_Routine10 (scddisasm/src/objects/r8/boss.asm:482-557): sweep between $E84 and $F7C {@code var_38} times, then to $F00. */
    private void sweep() {
        if (step == 0) {
            step = 2;
            if (xWord() > 0xF00) sweepLeft(); else sweepRight();
            return;
        }
        if ((step & 1) == 0) {
            addXLong(-stepX);
            if ((short) offX < xWord()) return;
        } else {
            addXLong(stepX);
            if ((short) offX > xWord()) return;
        }
        x = offX & 0xFFFF;
        // loc_20B6C8
        if (target == 0) {
            set(DONE);
            return;
        }
        target = (target - 1) & 0xFFFF;
        boolean wasRight = (step & 1) != 0;
        step ^= 1;                                                // bchg #0,obj.var_2d(a0)
        if (target == 0) {
            offX = 0xF00;                                         // loc_20B710
            return;
        }
        if (!wasRight) sweepRight(); else sweepLeft();
    }

    /** loc_20B6F4: right toward $F7C. */
    private void sweepRight() {
        step |= 1;
        offX = 0xF7C;
    }

    /** loc_20B702: left toward $E84. */
    private void sweepLeft() {
        step &= ~1;
        offX = 0xE84;
    }

    /** EggmanObject_0_Routine12 (boss.asm:561-580): retract the panels, then rise to {@code var_38}. */
    private void rise() {
        if (step == 0) {
            retractPanels();
            clear(DONE);
            step = 1;
        }
        addYLong(-0x14000);
        if (targetWord() < yWord()) return;
        y = target & 0xFFFF;
        set(DONE);
    }

    /** EggmanObject_0_Routine14 (boss.asm:584-593): reverse the rotation unless bit 6 is set. */
    private void toggleRotation() {
        if (eggMobile != null && (eggMobile.mode & MmzBossEggMobile.MODE_DIRECTION) == 0) {
            eggMobile.set(MmzBossEggMobile.TOGGLE);
            eggMobile.step = 0;
            eggMobile.setTimerLow(0);
        }
        set(DONE);
    }

    /** EggmanObject_0_Routine16 (scddisasm/src/objects/r8/boss.asm:597-627): rotate to frame 4 (8 with two hits left) and wait for it. */
    private void settlePanels() {
        if (eggMobile == null) return;
        if (step == 0) {
            step = 1;
            eggMobile.set(MmzBossEggMobile.TOGGLE);
            eggMobile.mode &= ~MmzBossEggMobile.MODE_SETTLED;
            eggMobile.step = 0;
            eggMobile.setTimerLow(0);
            eggMobile.targetFrame = hitsLeft == 2 ? 8 : 4;
        }
        if ((eggMobile.mode & MmzBossEggMobile.MODE_SETTLED) != 0) set(DONE);
    }

    /** EggmanObject_0_Routine18 (scddisasm/src/objects/r8/boss.asm:631-642): the Egg Mobile's routine 4 (panels out). */
    private void expandPanels() {
        if (eggMobile != null) {
            eggMobile.step = 0;
            eggMobile.timer = 0;
            eggMobile.mode = (eggMobile.mode | MmzBossEggMobile.MODE_SETTLED | MmzBossEggMobile.MODE_SPUN)
                    & ~MmzBossEggMobile.MODE_DIRECTION;
            eggMobile.setRoutine(4);
        }
        timer = 0;
        set(DONE);
    }

    /** EggmanObject_0_Routine1A (scddisasm/src/objects/r8/boss.asm:646-654): the Egg Mobile's routine 6. */
    private void launchPanels() {
        fmSound(FM_LAUNCH);
        if (eggMobile != null) {
            eggMobile.step = 0;
            eggMobile.timer = 0;
            eggMobile.setRoutine(6);
        }
        set(DONE);
    }

    /** EggmanObject_0_Routine1C (scddisasm/src/objects/r8/boss.asm:658-664): the Egg Mobile's routine 8 (panels in). */
    private void retractPanels() {
        if (eggMobile != null) {
            eggMobile.step = 0;
            eggMobile.timer = 0;
            eggMobile.setRoutine(8);
        }
        set(DONE);
    }

    /** EggmanObject_0_Routine1E (scddisasm/src/objects/r8/boss.asm:668-710): the panels go up and drop; up to $13C, then wait for them. */
    private void dropPanels() {
        if (step == 1) {
            target = 0x13C;
            moveUp();
            if (clear(DONE)) step = (step + 1) & 0xFF;
            return;
        }
        if (step == 2) {
            if (eggMobile != null && ((hitsLeft * 2) & 0xFF) == eggMobile.panelsUp) set(DONE);
            return;
        }
        step = (step + 1) & 0xFF;
        if (eggMobile == null) return;
        eggMobile.step = 0;
        eggMobile.timer = 0;
        eggMobile.setRoutine(0xA);
        eggMobile.panelsUp = 0;
        eggMobile.panelsDown = 0;
    }

    /** EggmanObject_0_Routine20 (scddisasm/src/objects/r8/boss.asm:714-720): the Egg Mobile's routine $C (the panels come back). */
    private void reattachPanels() {
        if (eggMobile != null) {
            eggMobile.step = 0;
            eggMobile.timer = 0;
            eggMobile.setRoutine(0xC);
        }
        set(DONE);
    }

    /** EggmanObject_0_Routine22 (scddisasm/src/objects/r8/boss.asm:816-975): the swing, one table step every $14 frames, with the stomp. */
    private void swing(AbstractPlayableSprite player) {
        if ((byte) step >= 5) {
            endSwing();
            return;
        }
        if ((byte) step < 0) {
            // loc_20BAB0: a 60-frame pause.
            setTimerHigh(timerHigh() + 1);
            if (timerHigh() != 0x3C) return;
            setTimerHigh(0);
            step &= 0x7F;
            return;
        }
        if (step == 0) {
            setTimerHigh(0);
            setTargetHigh(0);
            boolean right = hitsLeft == 4
                    ? player != null && (short) player.getCentreX() >= xWord()
                    : xWord() < 0xF00;
            if (!right) {
                setTimerLow(7);                                   // loc_20B9B2
                step = 1;
            } else {
                setTimerLow(0);                                   // loc_20B9C0
                step = 2;
            }
            if (eggMobile != null) {
                eggMobile.setRoutine(0xE);
                eggMobile.step = 0;
                eggMobile.setTimerLow(0);
            }
        }
        setTimerHigh(timerHigh() + 1);
        if (timerHigh() != 0x14) return;
        setTargetHigh(targetHigh() + 1);
        if ((targetHigh() & 1) == 0) subCpu(SCMD_STOMP);
        setTimerHigh(0);
        CdStageImage image = image();
        int base = table("eggmanSwings");
        if (image == null || base < 0) return;
        int entry = base + (short) image.u16(base + hitsLeft * 2) + (timerLow() & 0xF) * 4;
        int dx = (short) image.u16(entry), dy = (short) image.u16(entry + 2);
        if ((step & 1) != 0) {
            if (eggMobile != null) eggMobile.set(MmzBossEggMobile.ROTATE_DOWN);
            dx = -dx;
            dy = -dy;
        } else if (eggMobile != null) {
            eggMobile.set(MmzBossEggMobile.ROTATE_UP);
        }
        addX(dx);
        addY(dy);
        int count = targetHigh();
        boolean turn = hitsLeft == 4 ? count == 4 || count == 0xC : count == 4 || count == 8;
        if (turn) {
            if ((byte) step < 3) {
                step = step == 1 ? 0x84 : 0x83;
                return;
            }
            step = 0x85;                                          // loc_20BA96, falling into loc_20BA9C
        }
        if ((step & 1) != 0) setTimerLow(timerLow() - 1); else setTimerLow(timerLow() + 1);
    }

    /** loc_20BAC8 (scddisasm/src/objects/r8/boss.asm:966-974). */
    private void endSwing() {
        if (eggMobile != null) {
            eggMobile.setRoutine(2);
            eggMobile.step = 0;
            eggMobile.setTimerLow(0);
        }
        step = 0;
        timer = 0;
        set(DONE);
    }

    /** EggmanObject_0_Routine26 (scddisasm/src/objects/r8/boss.asm:1023-1087): aim at the player, dive, bounce off the floor line. */
    private void charge(AbstractPlayableSprite player) {
        if (step == 0) aim(player);
        if ((step & 2) == 0 && eggMobile != null && (eggMobile.mode & MmzBossEggMobile.MODE_SETTLED) != 0) step |= 2;
        if ((step & 4) == 0) dive();
        if (step == 7) set(DONE);
    }

    /** sub_20BBFA (scddisasm/src/objects/r8/boss.asm:1091-1178). */
    private void aim(AbstractPlayableSprite player) {
        step |= 1;
        int d0 = (short) ((player == null ? x : player.getCentreX()) - x);
        int distance = Math.abs(d0);
        int d2 = d0;
        if (eggMobile != null) {
            eggMobile.set(MmzBossEggMobile.TOGGLE);
            eggMobile.mode &= ~MmzBossEggMobile.MODE_SETTLED;
            eggMobile.step = 0;
            eggMobile.setTimerLow(0);
        }
        if (distance < 0x40) {
            stepX = speed(d0, 90);
            stepY = speed((short) (0x17C - y), 90);
            if (eggMobile != null) eggMobile.targetFrame = hitsLeft == 2 ? 6 : 4;
            return;
        }
        d0 = (short) (d2 < 0 ? d0 + 0x10 : d0 - 0x10);           // loc_20BC72
        stepX = speed(d0, 0x5A);
        stepY = speed((short) (0x18A - y), 0x5A);
        if (eggMobile == null) return;
        if (hitsLeft == 2) eggMobile.targetFrame = 6;
        else eggMobile.targetFrame = d2 < 0 ? 6 : 2;
    }

    /**
     * {@code ext.l d0; asl.w #4,d0; divs.w #n,d0; ext.l d0; move.w #$C,d1; asl.l d1,d0} (boss.asm:1107-1113): the
     * low word shifted alone over the sign-extended long, the quotient's word extended and moved up 12 bits.
     */
    static long speed(int distance, int divisor) {
        int dividend = (distance < 0 ? 0xFFFF0000 : 0) | ((distance << 4) & 0xFFFF);
        int quotient = (short) (dividend / divisor);
        return ((long) quotient << 12) & 0xFFFFFFFFL;
    }

    /** sub_20BBB4 (scddisasm/src/objects/r8/boss.asm:1058-1081). */
    private void dive() {
        int floor = eggMobile != null && (eggMobile.targetFrame & 3) != 0 ? 0x18A : 0x17C;
        addXLong(stepX);
        addYLong(stepY);
        if (floor > yWord()) return;
        y = floor;
        stepX = -stepX & 0xFFFFFFFFL;
        stepY = -stepY & 0xFFFFFFFFL;
        step |= 4;
    }

    /** EggmanObject_0_Routine28 (scddisasm/src/objects/r8/boss.asm:1182-1198): back up to $13C. */
    private void returnUp() {
        addXLong(stepX);
        addYLong(stepY);
        if (yWord() > 0x13C) return;
        y = 0x13C;
        step = 0;
        set(DONE);
        loadSpeeds();
    }

    /** EggmanObject_0_Routine2A (scddisasm/src/objects/r8/boss.asm:724-812): to one side, drop the panels into place, down to $1A0. */
    private void sideDrop(AbstractPlayableSprite player) {
        switch (step) {
            case 1 -> {
                moveRight();
                if (clear(DONE)) startSideDrop();
                return;
            }
            case 2 -> {
                moveLeft();
                if (clear(DONE)) startSideDrop();
                return;
            }
            case 3 -> {
                finishSideDrop();
                return;
            }
            default -> { }
        }
        if (hitsLeft == 4) {
            if (eggMobile != null) eggMobile.targetFrame = 8;
            startSideDrop();
            return;
        }
        if (player != null && (short) player.getCentreX() >= xWord()) {
            if (eggMobile != null) eggMobile.targetFrame = hitsLeft == 3 ? 0 : 4;
            target = 0xE84;
            step = 2;
            moveLeft();
            if (clear(DONE)) startSideDrop();
        } else {
            if (eggMobile != null) eggMobile.targetFrame = 8;   // both branches write 8 (loc_20B8F2, :744-750)
            target = 0xF7C;
            step = 1;
            moveRight();
            if (clear(DONE)) startSideDrop();
        }
    }

    /** loc_20B93E (scddisasm/src/objects/r8/boss.asm:793-799), falling into loc_20B95E. */
    private void startSideDrop() {
        if (eggMobile != null) {
            eggMobile.set(MmzBossEggMobile.TOGGLE);
            eggMobile.mode &= ~MmzBossEggMobile.MODE_SETTLED;
            eggMobile.step = 0;
            eggMobile.setTimerLow(0);
        }
        target = 0x1A0;
        step = 3;
        finishSideDrop();
    }

    /** loc_20B95E (scddisasm/src/objects/r8/boss.asm:801-812). */
    private void finishSideDrop() {
        moveDown();
        if (!clear(DONE)) return;
        step = 0;
        set(DONE);
    }

    /** EggmanObject_0_Routine2C (scddisasm/src/objects/r8/boss.asm:1202-1294): to one side, settle the panels, down to $138, a sine hop. */
    private void sideHop(AbstractPlayableSprite player) {
        switch (step) {
            case 1 -> { moveRight(); sideHopArrived(); return; }
            case 2 -> { moveLeft(); sideHopArrived(); return; }
            case 3 -> { sideHopWait(); return; }
            case 4 -> { sideHopDown(); return; }
            case 5 -> { sideHopArc(); return; }
            default -> { }
        }
        // Step 0, and step 7 (FixBugs = 0: loc_20BD98's addq.b #4 turns 3 into 7, which no case matches, so a hop
        // that does not reach $138 on that frame starts over from here; the fixed value would be 4).
        if (player != null && (short) player.getCentreX() >= xWord()) {
            target = 0xE84;
            step = 2;
            moveLeft();
        } else {
            target = 0xF7C;
            step = 1;
            moveRight();
        }
        sideHopArrived();
    }

    /** loc_20BD64 / loc_20BD6E (scddisasm/src/objects/r8/boss.asm:1238-1251). */
    private void sideHopArrived() {
        if (!clear(DONE)) return;
        if (eggMobile != null) {
            eggMobile.set(MmzBossEggMobile.TOGGLE);
            eggMobile.mode &= ~MmzBossEggMobile.MODE_SETTLED;
            eggMobile.targetFrame = 8;
            eggMobile.step = 0;
            eggMobile.setTimerLow(0);
        }
        step = 3;
        sideHopWait();
    }

    /** loc_20BD8E / loc_20BD98 (scddisasm/src/objects/r8/boss.asm:1253-1262). */
    private void sideHopWait() {
        if (eggMobile == null || (eggMobile.mode & MmzBossEggMobile.MODE_SETTLED) == 0) return;
        target = 0x138;
        step = (step + 4) & 0xFF;
        sideHopDown();
    }

    /** loc_20BDA2 / loc_20BDB0 (scddisasm/src/objects/r8/boss.asm:1264-1276). */
    private void sideHopDown() {
        moveDown();
        if (!clear(DONE)) return;
        step = 5;
        offX = 0;
        offY = -0x68 & 0xFFFF;
        timer = 0x4000;
        sideHopArc();
    }

    /** loc_20BDC6 / loc_20BDE0 (scddisasm/src/objects/r8/boss.asm:1278-1294). */
    private void sideHopArc() {
        sineStep();
        addX(-(short) offX);
        offX = 0;
        if (timer != 0x8000) return;
        step = 0;
        set(DONE);
        fmSound(FM_LAND);
    }

    /** EggmanObject_0_Routine2E (scddisasm/src/objects/r8/boss.asm:1298-1389): half-circle hops across the arena with the panels out. */
    private void bounceAcross() {
        if ((byte) step >= 5) {
            endBounce();
            return;
        }
        if (step == 0) {
            offY = 0;
            timer = 0;
            if (xWord() >= 0xF00) {
                offX = 0x29;
                step = 1;
            } else {
                offX = -0x29 & 0xFFFF;
                step = 2;
            }
            if (eggMobile != null) {
                eggMobile.setRoutine(0x12);
                eggMobile.setIndex = 2;
                eggMobile.step = 0;
                eggMobile.setTimerLow(0);
            }
        }
        sineStep();
        if (timer != 0x8000) return;
        fmSound(FM_LAND);
        offY = 0;
        timer = 0;
        int distance;
        if ((step & 1) != 0) {
            offX = 0x29;
            distance = Math.abs((short) (x - 0xE84));
        } else {
            offX = -0x29 & 0xFFFF;
            distance = Math.abs((short) (x - 0xF7C));
        }
        if (distance >= 0x29) return;
        offX = -(short) offX & 0xFFFF;                            // loc_20BEA8
        if ((byte) step >= 3) {
            endBounce();
            return;
        }
        step = step == 1 ? 4 : 3;
    }

    /** loc_20BECC (scddisasm/src/objects/r8/boss.asm:1382-1389). */
    private void endBounce() {
        step = 0;
        set(DONE);
        if (eggMobile == null) return;
        eggMobile.setRoutine(2);
        eggMobile.step = 0;
        eggMobile.setTimerLow(0);
        eggMobile.radius = 0;
    }

    /** EggmanObject_0_Routine30 (scddisasm/src/objects/r8/boss.asm:1393-1421): a quarter of the sine hop, in place. */
    private void hopHalf() {
        if (step == 0) {
            step = 1;
            offX = 0;
            offY = 0;
            timer = 0;
        }
        sineStep();
        addX(-(short) offX);
        offX = 0;
        if ((short) timer < 0x4000) return;
        step = 0;
        timer = 0;
        offX = 0;
        offY = 0;
        set(DONE);
    }

    /**
     * sub_20BF4A (scddisasm/src/objects/r8/boss.asm:1425-1453): undo the last offsets, advance the angle word by $230 (to $8000 at most), and
     * add {@code cos * $29 / 256} (negated on even steps) and {@code sin * -$68 / 256}; {@code asr.w} shifts the low
     * word of each product.
     */
    private void sineStep() {
        addX(-(short) offX);
        addY(-(short) offY);
        timer = Math.min(timer + 0x230, 0x8000);
        int angle = timerHigh();
        int d0 = sine(angle), d1 = cosine(angle);
        if ((step & 1) == 0) d1 = -d1;
        d1 = ((short) (d1 * 0x29)) >> 8;
        d0 = ((short) (d0 * -0x68)) >> 8;
        offX = d1 & 0xFFFF;
        offY = d0 & 0xFFFF;
        addX(d1);
        addY(d0);
    }

    /** EggmanObject_0_Routine32 (scddisasm/src/objects/r8/boss.asm:1821-1854): 90 frames of explosions, then Amy at ($E48, $1CC). */
    private void defeated() {
        if (timerHigh() == 0) {
            anim = 3;
            poseTimer = 0;
            if (eggMobile != null) {
                eggMobile.setRoutine(0x10);
                eggMobile.setMapFrame(3);
                eggMobile.setCollision(0, 0);
            }
            setCollision(0, 0);
            clear(FIGHT);
        }
        setTimerHigh(timerHigh() + 1);
        if (timerHigh() < 90) {
            explosions();
            return;
        }
        if (spawn(new MmzBossAmy(0xE48, 0x1CC))) routine = 0x34;
    }

    /** EggmanObject_0_Routine34 / loc_20C2A2 (scddisasm/src/objects/r8/boss.asm:1858-1907): drift right on a sine to X $FE0. */
    private void escape() {
        if (step == 0) {
            var game = services().gameState();
            if (game != null) game.addScore(100 * 10);            // moveq #100,d0; jsr AddPoints (units of 10)
            setTimerLow(0);
            offY = 0;
            step = 1;
        }
        setTimerHigh(timerHigh() + 1);
        explosions();
        if (timerHigh() % 60 == 0) spawnSparks();
        addY(-(short) offY);
        addYLong(0x4000);
        int d0 = sine(timerLow());
        setTimerLow(timerLow() + 3);
        d0 = ((short) (d0 * 4)) >> 8;
        offY = d0 & 0xFFFF;
        addY(d0);
        addXLong(stepX);
        if (xWord() < 0xFE0) return;
        loadPaletteEntry(PALETTE_ESCAPE);
        routine = 0x36;
        setTimerHigh(0);
        setBossStarted(0);                                        // clr.b boss_started
    }

    /** EggmanObject_0_Routine36 (scddisasm/src/objects/r8/boss.asm:1911-1916). */
    private void exploding() {
        setTimerHigh(timerHigh() + 1);
        explosions();
    }

    // ---- palettes -----------------------------------------------------------------------------------------------

    /**
     * {@code LoadPalette} (common/palette_load.asm) of palette table entry {@code id}: the entry's destination is the
     * palette buffer or {@code fadePalette} ($FB80, palette 7); the latter is kept here as the fade's target.
     */
    private void loadPaletteEntry(int id) {
        CdStageImage image = image();
        var payload = payload();
        if (image == null || payload == null) return;
        int table = CdStageCatalogue.entry(payload).paletteTable();
        int entry = table + id * 8;
        int source = image.offsetOf(image.u32(entry));
        int destination = image.u16(entry + 4);
        int words = (image.u16(entry + 6) + 1) * 2;
        int[] data = new int[words];
        for (int i = 0; i < words; i++) data[i] = image.u16(source + i * 2);
        if (destination >= FADE_PALETTE_RAM) {
            int[] fade = fadePalette();
            System.arraycopy(data, 0, fade, (destination - FADE_PALETTE_RAM) / 2, Math.min(words, 64));
        } else {
            writePaletteWords((destination - PALETTE_RAM) / 2, data);
        }
    }

    /**
     * {@code fadePalette} as the level start leaves it: {@code LoadFadePalette} of the player's palette 3 and the
     * stage's palette (common/main.asm:243-246, {@code LoadStageData}); the boss's palette 7 replaces line 1.
     */
    private int[] fadePalette() {
        if (fadePalette != null) return fadePalette;
        fadePalette = new int[64];
        CdStageImage image = image();
        var payload = payload();
        if (image == null || payload == null) return fadePalette;
        var entry = CdStageCatalogue.entry(payload);
        int table = entry.paletteTable();
        int levelPalette = image.u8(entry.levelDataIndex() + 15);
        for (int id : new int[] {3, levelPalette}) {
            int e = table + id * 8;
            int source = image.offsetOf(image.u32(e));
            int at = (image.u16(e + 4) - PALETTE_RAM) / 2;
            int words = (image.u16(e + 6) + 1) * 2;
            for (int i = 0; i < words && at + i < 64; i++) {
                if (at + i >= 0) fadePalette[at + i] = image.u16(source + i * 2);
            }
        }
        return fadePalette;
    }

    /**
     * BossFadeFromBlack (common/palette_fade.asm:94-116): one {@code FadeColorFromBlack} step for line 1, and in the
     * bad future ({@code STAGE_GOOD_FUTURE = 0}) line 3, toward {@code fadePalette}.
     */
    private void bossFadeFromBlack() {
        fadeLine(1);
        if (!goodFutureStage()) fadeLine(3);
    }

    /** R83C is the good future build, R83D the bad (Metallic Madness/Act 3 Good/Bad Future.asm). */
    private boolean goodFutureStage() {
        var payload = payload();
        return payload != null && payload.stage().period() == com.openggf.game.soniccd.CdPeriod.GOOD_FUTURE;
    }

    private void fadeLine(int line) {
        int[] target = fadePalette();
        int[] words = new int[16];
        for (int c = 0; c < 16; c++) {
            int index = line * 16 + c;
            words[c] = fadeColorFromBlack(paletteWord(index), target[index]);
        }
        writePaletteWords(line * 16, words);
    }

    /** FadeColorFromBlack (common/palette_fade.asm:58-90): blue +$200, else green +$20, else red +2, toward the target. */
    static int fadeColorFromBlack(int current, int target) {
        if (current == target) return current;
        int blue = current + 0x200;
        if (Integer.compareUnsigned(blue & 0xFFFF, target) <= 0) return blue & 0xFFFF;
        int green = current + 0x20;
        if (Integer.compareUnsigned(green & 0xFFFF, target) <= 0) return green & 0xFFFF;
        return (current + 2) & 0xFFFF;
    }

    // ---- touch --------------------------------------------------------------------------------------------------

    /** {@code Player_TouchEnemy} with a status (r8/player_object_collide.asm:150-163): type cleared, status - 1. */
    @Override
    public void onPlayerAttack(PlayableEntity player, TouchResponseResult result) {
        attacked(this);
    }

    static void attacked(CdBossPart part) {
        part.setCollision(0, (part.getCollisionProperty() - 1) & 0xFF);
    }

    @Override
    protected CdBossPart shell(ObjectSpawn spawn) {
        return new MmzBossEggman(spawn);
    }
}
