package com.openggf.game.soniccd.objects.boss.wwz;

import com.openggf.game.soniccd.zones.wwz.WwzStageState;
import com.openggf.camera.Camera;
import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.audio.CdSubCpuAudio;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Wacky Workbench boss, object $34 ({@code EggmanObject}, objects/r6/boss.asm:3-664; ID = position in
 * r6/object_index_3.asm:3-69 plus one), placed by the act 3 layout at ($AC0, $580) (r6/stage_object_maps_3.asm). Both
 * futures run the same code; the music after the escape ($1C good, $1D bad) and the stage-palette fade table and the
 * colours it writes (common/palette_fade.asm:362-451) differ.
 *
 * <p>Every frame, before its routine, it shakes the bottom boundary ({@code var_38}) and runs the hit check; after it,
 * unless the routine popped its return, it updates {@code bossFlags} from the player's floor, animates and draws.
 * <ul>
 *   <li>Routine 0 waits for the player at X $A80 (no flags, animation or drawing). Routine 2 loads palette 6, queues
 *       cue $13, allocates the machine $35, its spikes $36 and exhaust $37, sets {@code bossFlags} 6 and bit 4,
 *       {@code bossFight} 6 and the bounds $9E0-$A60, then waits $78 frames, starts the boss music and descends.</li>
 *   <li>Routine 4 falls to the floor ($620, $428, $228), routine 6 hovers there until it is hit or $12C frames pass,
 *       routine 8 rises to the floor above ($428, $228, $130). Routine A attacks from the floor above: debris every 16
 *       frames, four floor pieces below, electricity at $1F2, the player's floor electrified at $21C, the floor pieces
 *       thrown up at $258, then down again at $348; on the top floor it fires spikes and loops, and one hit defeats it.</li>
 *   <li>Routine C falls to $2A0 with smoke, routine E holds the player, fades the stage to white, turns into the
 *       escape pod over the broken machine, fades back, and routine $10 flies right to X $EF0 (the capsule palette once
 *       the player reaches X $C00).</li>
 * </ul>
 *
 * <p>Touch: Eggman ($3C) and the machine ($3D) are ordinary enemy types with status 2: a rolling player rebounds at
 * half speed and clears the type (PlayerObjectCollideEnemy, src/r6/player_object_collide.asm:133-153),
 * anyone else is hurt. A cleared type on either is a hit ({@code FixBugs = 0}: the attack is not tested).
 */
public final class WwzBossEggman extends WwzBossPiece implements com.openggf.level.objects.TouchResponseAttackable {
    /** Object $34: EggmanObject is entry $33 of r6/object_index_3.asm:3-69. */
    public static final int ID = 0x34;
    /** EggmanWaitForPlayer: cmpi.w #$A80,obj.x(a1) (objects/r6/boss.asm:99-105). */
    static final int TRIGGER_X = 0xA80;
    /** EggmanTakeHit: move.w #$AC,d0; jsr PlayFmSound (objects/r6/boss.asm:57-60). */
    static final int FM_HIT = 0xAC;
    /** EggmanLand: move.w #$B1,d0 (objects/r6/boss.asm:185-196). */
    static final int FM_LAND = 0xB1;
    /** EggmanReachAscentTarget: move.w #$A3,d0 (objects/r6/boss.asm:278-297). */
    static final int FM_RISE = 0xA3;
    /** EggmanAttackSoundCue: move.w #$B2,d0 (objects/r6/boss.asm:326-334). */
    static final int FM_ATTACK = 0xB2;
    /** EggmanDefeat: move.w #$C7,d0 (objects/r6/boss.asm:426-443). */
    static final int FM_DEFEAT = 0xC7;
    /** EggmanEnterBattle: move.w #$67,d0; jsr SubCpuCommand (SCMD_BOSSMUS, objects/r6/boss.asm:158-161). */
    static final int SCMD_BOSSMUS = 0x67;
    /** EggmanInitialize: move.w #6,d0; jsr LoadFadePalette / LoadPalette (objects/r6/boss.asm:111-143). */
    static final int BOSS_PALETTE = 6;
    /** Eggman's collision type and status, and the machine's (EggmanRestoreCollision, objects/r6/boss.asm:66-72). */
    static final int COL_TYPE = 0x3C, MACHINE_COL_TYPE = 0x3D, COL_STATUS = 2;

    // var_2e: the machine.
    WwzBossMachine machine;
    /** var_2c bit 0: hit checks on; bit 1: the capsule palette loaded. */
    private int status;
    /** var_2d: the floor (0 bottom ... 3 top). */
    int floor;
    /** var_34: the spikes' state (0 hidden, 1 hit, 2+ spinning, $FF gone); var_35 the exhaust's animation. */
    int spikesState;
    int exhaustAnim;
    /** var_36: the floor pieces jump while set. */
    int floorPieceJump;
    /** var_37: frames of hit immunity; var_38: frames of bottom-boundary shake; var_39: hits on this floor. */
    private int immunity;
    private int shake;
    private int hits;
    /** var_3a: the last sine offset; var_3c: the hover phase; var_3d: the spike wave's side. */
    private int bob;
    private int hoverPhase;
    private int spikeSide;
    /** var_3e / var_3f: the stage-palette fade timer and table index. */
    private int fadeTimer;
    private int fadeIndex;
    /** {@code fadePalette} after LoadFadePalette 6: colours 0-$2F as Sega words (the FadeObjects target). */
    private int[] fadeTarget = new int[0x30];
    private boolean skipDraw;

    public WwzBossEggman(ObjectSpawn spawn) {
        super(spawn, "WwzBossEggman");
    }

    /** var_2d. */
    public int floor() { return floor; }

    /** var_39. */
    public int hitsThisFloor() { return hits; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        AbstractPlayableSprite sonic = sonic(player);
        WwzBossRam ram = ram();
        updateBottomBound();
        checkCollision();
        skipDraw = false;
        switch (routine) {
            case 0 -> waitForPlayer(sonic, ram);
            case 2 -> initialize(ram);
            case 4 -> descend();
            case 6 -> hover(sonic);
            case 8 -> ascend();
            case 0xA -> attack(sonic, ram);
            case 0xC -> defeatFall(sonic);
            case 0xE -> defeatSequence(sonic, ram);
            case 0x10 -> escape(sonic, ram);
            case 0x12 -> escapeWait(sonic);
            default -> { }                                   // EggmanObject_0_Routine14: rts (objects/r6/boss.asm:166-168)
        }
        if (skipDraw || isDestroyed()) return;               // addq.l #4,sp: no flags, AnimateObject or DrawObject
        updateBossFlags(sonic, ram);
        animate(WwzBossCatalogue.offset(payload(), "eggmanAnimations"));
    }

    @Override protected boolean drawn() { return !skipDraw && routine != 0; }

    /**
     * PlayerObjectCollideEnemy with a nonzero status (src/r6/player_object_collide.asm:133-153): the type clears and the
     * status drops by one; the shared touch owner applies the halved rebound. EggmanCheckCollision reads the cleared type.
     */
    @Override
    public void onPlayerAttack(com.openggf.game.PlayableEntity player, com.openggf.level.objects.TouchResponseResult result) {
        setCollision(0, (colStatus - 1) & 0xFF);
    }

    @Override String sheet() { return tileWord == WwzBossArt.POD_TILE ? WwzBossArt.POD : WwzBossArt.EGGMAN; }

    private com.openggf.game.soniccd.CdStagePayload payload() {
        var level = cdLevel();
        return level == null ? null : level.payload();
    }

    /** BossUpdateBottomBound (objects/r6/boss.asm:1984-2000): -2, or +4 on odd counts, to both bottom words. */
    private void updateBottomBound() {
        if (shake == 0) return;
        shake = (shake - 1) & 0xFF;
        int step = (shake & 1) == 0 ? -2 : 4;
        Camera camera = services().camera();
        if (camera == null) return;
        camera.setMaxY((short) (camera.getMaxY() + step));
        camera.setMaxYTarget((short) (camera.getMaxYTarget() + step));
    }

    /** EggmanCheckCollision (objects/r6/boss.asm:31-63). */
    private void checkCollision() {
        if ((status & 1) == 0) return;
        if (immunity != 0) {
            immunity = (immunity - 1) & 0xFF;
            if (immunity == 0) restoreCollision();
            return;
        }
        // EggmanCheckCollisionTarget: a cleared type on Eggman or the machine.
        if (colType() != 0 && (machine == null || machine.colType() != 0)) return;
        hits = (hits + 1) & 0xFF;                            // EggmanTakeHit
        setCollision(0, colStatus);
        if (machine != null) machine.setCollision(0, machine.getCollisionProperty());
        anim = 1;
        mapFrame = 0;
        immunity = 0x1E;
        fmSound(FM_HIT);
    }

    /**
     * EggmanRestoreCollision (objects/r6/boss.asm:66-72). With no machine (its allocation failed) {@code var_2e} is 0
     * and the native writes land in RAM at $FF0020; the engine skips them ({@code FixBugs = 0} has no other effect).
     */
    private void restoreCollision() {
        setCollision(COL_TYPE, COL_STATUS);
        if (machine != null) machine.setCollision(MACHINE_COL_TYPE, COL_STATUS);
    }

    /** EggmanUpdateBossFlags (objects/r6/boss.asm:76-89). */
    private void updateBossFlags(AbstractPlayableSprite player, WwzBossRam ram) {
        int band = bossBand(player);
        if ((ram.fight() & (1 << band)) != 0) {
            ram.flagBit(objects(), 7, false);                // EggmanClearActiveFlag
            return;
        }
        if ((ram.flags() & 0x80) != 0) return;
        ram.flagBit(objects(), 6, true);
    }

    /** EggmanWaitForPlayer (objects/r6/boss.asm:93-104): from player X $A80, routine 2 in the same frame. */
    private void waitForPlayer(AbstractPlayableSprite player, WwzBossRam ram) {
        if (player == null || (short) player.getCentreX() < TRIGGER_X) {
            skipDraw = true;
            return;
        }
        routine = 2;                                         // EggmanBeginEncounter
        initialize(ram);
    }

    /** EggmanInitialize (objects/r6/boss.asm:106-140) and EggmanWaitForFade (:144-150). */
    private void initialize(WwzBossRam ram) {
        if ((ram.flags() & 0xF) != 0) {
            timer = (timer + 1) & 0xFFFF;
            if (timer == 0x78) enterBattle(ram);
            return;
        }
        loadPalette(BOSS_PALETTE);                           // LoadFadePalette 6 (the target), LoadPalette 6
        captureFadeTarget();
        // AddGfxQueue $13: the sheets are registered at the stage load (WwzBossArt).
        spawnChildren();
        priority = 4;
        width = 0x18;
        yRadius = 0x18;
        tileWord = WwzBossArt.EGGMAN_TILE;
        status |= 1;
        restoreCollision();
        ram.setFlags(objects(), ((ram.flags() & 0xF0) + 6) | 0x10);   // andi.b #$F0; addq.b #6; bset #4
        setFight(6);
        Camera camera = services().camera();
        if (camera != null) {
            camera.setMinX((short) 0x9E0);                   // left_bound, target_left_bound
            camera.setMaxX((short) 0xA60);                   // right_bound, target_right_bound
        }
    }

    /** EggmanEnterBattle (objects/r6/boss.asm:154-163). */
    private void enterBattle(WwzBossRam ram) {
        timer = 0;
        routine = 4;
        xVel = 0;
        yVel = 0x300;
        accelY = -1;
        subCpu(SCMD_BOSSMUS);
        ram.setMusic(objects());                             // SubCpuCommand: cmpi.w #$67,d0; move.b #1,boss_music
    }

    /** BossSpawnEggmanChildren (objects/r6/boss.asm:1913-1930): the machine, then its spikes and exhaust. */
    private void spawnChildren() {
        WwzBossMachine body = new WwzBossMachine(this);
        if (!spawn(body)) return;
        machine = body;
        if (!spawn(new WwzBossSpikes(this))) return;
        spawn(new WwzBossExhaust(this));
    }

    /** EggmanDescend / EggmanLand (objects/r6/boss.asm:172-200). */
    private void descend() {
        integrateVelocity();
        int target = WwzBossCatalogue.word(stageImage(), payload(), "descentTargets", floor);
        if (target > yWord()) return;
        setYWord(target);
        routine = 6;
        timer = 0;
        exhaustAnim = 1;
        spikesState = 1;
        fmSound(FM_LAND);
    }

    /** EggmanHover (objects/r6/boss.asm:206-217), EggmanHoverExit (:206-222), EggmanStartRise (:206-233). */
    private void hover(AbstractPlayableSprite player) {
        if ((byte) verticalBand(player) < (byte) floor) {
            restartAttackCycle();
            return;
        }
        moveHover();
        if (hits == 0) {
            timer = (timer + 1) & 0xFFFF;
            if ((short) timer < 0x12C) return;
        }
        exhaustAnim = 0;
        routine = 8;
        xVel = 0;
        yVel = -0x100;
        accelY = -0x10;
        timer = 0;
    }

    /** EggmanMoveHover (objects/r6/boss.asm:236-248). */
    private void moveHover() {
        if (floor == 3) {
            hoverPhase = (hoverPhase + 1) & 0xFF;
            addX((hoverPhase & 1) != 0 ? 2 : -2);
            return;
        }
        addY(-bob);                                          // EggmanMoveHoverSine
        int angle = hoverPhase;
        hoverPhase = (hoverPhase + 2) & 0xFF;
        bob = (short) (sine(angle) * 4) >> 8;                // muls.w #4,d0; asr.w #8,d0
        addY(bob);
    }

    /** EggmanAscend / EggmanReachAscentTarget / EggmanResetAttackState (objects/r6/boss.asm:272-302). */
    private void ascend() {
        integrateVelocity();
        int target = WwzBossCatalogue.word(stageImage(), payload(), "ascentTargets", floor);
        if (target < yWord()) return;
        setYWord(target);
        spikesState = 0;
        routine = 0xA;
        shake = 0xF0;
        floor = (floor + 1) & 0xFF;
        if (floor != 3) {
            exhaustAnim = 1;
            fmSound(FM_RISE);
        }
        hits = 0;
        anim = 0;
        mapFrame = 0;
    }

    /** EggmanAttack (objects/r6/boss.asm:307-323). */
    private void attack(AbstractPlayableSprite player, WwzBossRam ram) {
        moveHover();
        timer = (timer + 1) & 0xFFFF;
        switch (timer) {
            case 0x1F2 -> {                                  // EggmanAttackSoundCue
                fmSound(FM_ATTACK);
                spawnElectricity(verticalBand(player));
            }
            case 0x21C -> {                                  // EggmanAttackSetBossFlags
                int value = ram.fight() & 0x1F;
                int band = verticalBand(player);
                value |= band == 1 ? 0x40 : band == 2 ? 0x80 : 0x20;
                setFight(value);
            }
            case 0x23A -> ram.flagBit(objects(), 5, false);  // EggmanAttackClearBossFlag
            case 0x258 -> floorPieceJump = 1;                // EggmanAttackEnableEffect
            case 0x276 -> {                                  // EggmanAttackEnableBossFlag
                ram.flagBit(objects(), 5, true);
                floorPieceJump = 0;
            }
            case 0x348 -> {                                  // EggmanAttackNextPhase
                if (floor == 3) {
                    restartAttackCycle();
                } else {
                    timer = 0;
                    routine = 4;
                    xVel = 0;
                    yVel = 0x300;
                    accelY = -1;
                }
                return;
            }
            default -> { }
        }
        updateEffects(player);
    }

    /** EggmanAttackUpdateEffects (objects/r6/boss.asm:380-411). */
    private void updateEffects(AbstractPlayableSprite player) {
        if ((short) timer <= 0x168) spawnDebris((timer - 0x168) & 0xFFFF);
        if ((short) timer >= 0xB4) spawnFloorPiece((timer - 0xB4) & 0xFFFF);
        if (floor != 3) return;
        if (colType() == 0) {                                // EggmanAttackCheckFinalCollision
            defeat();
            return;
        }
        if ((short) timer > 0xB4) return;
        spawnFallSpike(player, (timer - 1) & 0xFFFF);
    }

    /** EggmanRestartAttackCycle (objects/r6/boss.asm:446-451). */
    private void restartAttackCycle() {
        timer = 0x168;
        spikesState = 0;
        routine = 0xA;
    }

    /** EggmanDefeat (objects/r6/boss.asm:425-442). */
    private void defeat() {
        status &= ~1;
        timer = 0;
        routine = 0xC;
        fmSound(FM_DEFEAT);
        xVel = 0x300;
        yVel = 0x100;
        accelY = 0x10;
        spawnSmoke();
        anim = 2;
        mapFrame = 0;
        exhaustAnim = 0xFF;
        spikesState = 0xFF;
    }

    /** EggmanDefeatFall / EggmanDefeatLand (objects/r6/boss.asm:455-472). */
    private void defeatFall(AbstractPlayableSprite player) {
        constrainPlayer(player);
        if (yWord() >= 0x2A0) {
            routine = 0xE;
            anim = 3;
            mapFrame = 0;
            setYWord(0x2A0);
            timer = 0;
            return;
        }
        integrateVelocity();
        xVel = (short) -xVel;
    }

    /** EggmanDefeatSequence (objects/r6/boss.asm:476-525). */
    private void defeatSequence(AbstractPlayableSprite player, WwzBossRam ram) {
        if (timer == 0x78) setFight(ram.fight() & 0x1F);
        if ((short) timer > 0x146) setPlayerDefeatPosition(player);
        else constrainPlayer(player);
        timer = (timer + 1) & 0xFFFF;
        if (timer == 0xB4) {
            releasePlayer();
            return;
        }
        int t = (short) timer;
        if (t >= 0xF0) {
            setPaletteCycleSlow();
            if (t <= 0x144) {
                fadeStage(true);                             // EggmanDefeatSequenceFadeToWhite
                return;
            }
        }
        switch (t) {                                         // EggmanDefeatSequenceCheckEvents
            case 0x145 -> {
                setPaletteCycleSlow();
                fadeToWhite();
                return;
            }
            case 0x146 -> {
                turnIntoPod();
                return;
            }
            case 0x17F -> {
                fadeObjectsFromWhite();
                return;
            }
            default -> { }
        }
        if (t >= 0x180 && t <= 0x1D4) {
            fadeStage(false);                                // EggmanDefeatSequenceFadeFromWhite
            return;
        }
        if (t == 0x1D5) reward(ram);                         // EggmanDefeatSequenceWaitEvent
    }

    /**
     * EggmanDefeatSequenceSpawnCapsule (objects/r6/boss.asm:562-575): cue $14 (the pod and the broken machine), the
     * pod's animation at ($B30, $288), and the machine drops $10 to become the solid broken machine.
     */
    private void turnIntoPod() {
        anim = 4;
        mapFrame = 0;
        tileWord = WwzBossArt.POD_TILE;
        setXWord(0xB30);
        setYWord(0x288);
        if (machine != null) {
            machine.addY(0x10);
            machine.setLayer(1);
            machine.setRoutine(machine.routine() + 2);
        }
    }

    /** EggmanDefeatSequenceReward (objects/r6/boss.asm:557-583). */
    private void reward(WwzBossRam ram) {
        setPaletteCycleClear();
        yVel = 0;
        accelY = 0;
        xVel = 0x100;
        accelX = 0xC;
        routine = 0x10;
        timer = 0;
        Camera camera = services().camera();
        CdStageImage image = stageImage();
        if (camera != null && image != null) {
            camera.setMaxX((short) image.u16(WwzBossCatalogue.offset(payload(), "escapeRightBound")));
        }
        subCpu(CdSubCpuAudio.commandId(flags().goodFuture ? "SCMD_R6CMUS" : "SCMD_R6DMUS"));
        var game = services().gameState();
        if (game != null) game.addScore(100 * 10);           // moveq #100,d0; jsr AddPoints (units of ten)
    }

    /**
     * EggmanDefeatSequenceReleasePlayer (objects/r6/boss.asm:603-607): the first conductor ($3E) in slot order whose
     * routine is 4 (ConductorClampPlayerRight) moves up (routine 8). With none the native search runs past the object
     * table ({@code FixBugs = 0}); the act's layout always has one.
     */
    private void releasePlayer() {
        var objects = objects();
        if (objects == null) return;
        WwzConductor first = null;
        for (var object : objects.getActiveObjects()) {
            if (object instanceof WwzConductor c && !c.isDestroyed() && c.routine() == 4
                    && (first == null || c.getSlotIndex() < first.getSlotIndex())) {
                first = c;
            }
        }
        if (first != null) first.setRoutine(8);
    }

    /** EggmanEscape / EggmanEscapeComplete / EggmanEscapeWait (objects/r6/boss.asm:621-638). */
    private void escape(AbstractPlayableSprite player, WwzBossRam ram) {
        loadCapsulePalette(player);
        integrateVelocity();
        if ((short) x < 0xEF0) return;
        routine = 0x12;
        setFight(0);
        escapeWait(player);
    }

    private void escapeWait(AbstractPlayableSprite player) {
        loadCapsulePalette(player);
        skipDraw = true;
    }

    /** EggmanLoadCapsulePalette (objects/r6/boss.asm:641-652): once, from player X $C00. */
    private void loadCapsulePalette(AbstractPlayableSprite player) {
        if ((status & 2) != 0 || player == null || (short) player.getCentreX() < 0xC00) return;
        status |= 2;
        loadCapsulePalette();
    }

    /** EggmanSetPlayerDefeatPosition (objects/r6/boss.asm:655-661). */
    private static void setPlayerDefeatPosition(AbstractPlayableSprite player) {
        if (player == null) return;
        player.setXSpeed((short) 0);
        player.setGSpeed((short) 0);
        com.openggf.sprites.NativePositionOps.writeXPosPreserveSubpixel(player, 0xAC0);
    }

    /** EggmanConstrainPlayerDuringDefeat (objects/r6/boss.asm:650-669). */
    private void constrainPlayer(AbstractPlayableSprite player) {
        if (player == null || ram().playerKilled()) return;
        player.setXSpeed((short) 0);
        player.setGSpeed((short) 0);
        if ((short) player.getCentreY() > 0x28C) com.openggf.sprites.NativePositionOps.writeYPosPreserveSubpixel(player, 0x28C);
        com.openggf.sprites.NativePositionOps.writeXPosPreserveSubpixel(player,
                (short) player.getCentreX() < 0xAC0 ? 0xA70 : 0xB10);
    }

    /** BossGetPlayerVerticalBand (objects/r6/boss.asm:1945-1953): 2 at or above Y $298, 1 at or above $498, else 0. */
    static int verticalBand(AbstractPlayableSprite player) {
        int y = player == null ? 0 : (short) player.getCentreY();
        if (y <= 0x298) return 2;
        if (y <= 0x498) return 1;
        return 0;
    }

    /** BossGetPlayerBossBand (objects/r6/boss.asm:1968-1976 region): 7 at or above Y $360, 6 at or above $560, else 5. */
    static int bossBand(AbstractPlayableSprite player) {
        int y = player == null ? 0 : (short) player.getCentreY();
        if (y <= 0x360) return 7;
        if (y <= 0x560) return 6;
        return 5;
    }

    /** BossGetEggmanTargetY (objects/r6/boss.asm:1671-1678): the floor below Eggman's. */
    int targetY() {
        return WwzBossCatalogue.word(stageImage(), payload(), "targetYTable", (floor - 1) & 0xFFFF);
    }

    // ---- allocations ---------------------------------------------------------------------------------

    /** BossSpawnFallingProjectile (objects/r6/boss.asm:1754-1789): debris $39 every 16 frames. */
    private void spawnDebris(int count) {
        if ((count & 0xF) != 0) return;                      // divu.w #$10; swap; tst.w
        WwzFloorDebris debris = new WwzFloorDebris(this);
        if (!spawn(debris)) return;
        int raw = services().rng().nextRaw();                // jsr Random
        int word = (short) raw;                              // andi.l #$FFFF,d0; ext.l d0
        int dx = word % 0x50;                                // divs.w #$50,d0; swap (remainder, sign of the dividend)
        int xSpeed = word % 0x280;                           // divs.w #$280,d0; swap
        int gravity = 8 + (word & 0xF);                      // move.w #8; andi.w #$F; add.w
        debris.launch(0xAC0 + dx, targetY(), xSpeed, gravity);
    }

    /** BossSpawnSpikeWave (objects/r6/boss.asm:1717-1749): floor pieces $3D at $B4, $C8, $DC and $F0. */
    private void spawnFloorPiece(int count) {
        if (count % 0x14 != 0) return;
        int index = count / 0x14;
        if (index >= 4) return;
        int row = (floor - 1) & 0xFF;
        int px = WwzBossCatalogue.word(stageImage(), payload(), "floorPieceXTable", row * 4 + index);
        spawn(new WwzFloorPiece(this, px, targetY(), index, row));
    }

    /** BossSpawnProjectileWave (objects/r6/boss.asm:1661-1713): six spikes $38 from the side away from the player. */
    private void spawnFallSpike(AbstractPlayableSprite player, int count) {
        if (count % 0x23 != 0) return;
        int index = count / 0x23;
        WwzFallSpike spike = new WwzFallSpike(this);
        if (!spawn(spike)) return;
        spike.setYWord(targetY());
        if (index == 0) {
            spikeSide = 0;
            if (player != null && (short) player.getCentreX() < 0xAC0) spikeSide = 1;
        }
        int step = spikeSide != 0 ? 0x20 : -0x20;
        int start = spikeSide != 0 ? 0xA20 : 0xB60;
        spike.setXWord(start + index * step);
    }

    /** BossSpawnSpikePair (objects/r6/boss.asm:1834-1862): electricity $40 at X $A10 and $B70 on the player's floor. */
    private void spawnElectricity(int band) {
        int ey = WwzBossCatalogue.word(stageImage(), payload(), "electricityYTable", band);
        spawn(new WwzElectricity(0xA10, ey, 0));
        spawn(new WwzElectricity(0xB70, ey, 1));
    }

    /** BossSpawnExplosion (objects/r6/boss.asm:1866-1879): smoke $3F, animation 1, attached; FM_EXPLODE. */
    private void spawnSmoke() {
        if (spawn(new WwzBossSmoke(this))) fmSound(FM_EXPLODE);
    }

    // ---- palette -------------------------------------------------------------------------------------

    /** BossSetPaletteCycleSlow (objects/r6/boss.asm:2010-2013): the six palette-cycle timers to $80. */
    private void setPaletteCycleSlow() {
        WwzStageState.fillPaletteCycleTimers(services().levelManager(), 0x80);
    }

    /** BossSetPaletteCycleClear (objects/r6/boss.asm:2016-2027): the six timers to 0. */
    private void setPaletteCycleClear() {
        WwzStageState.fillPaletteCycleTimers(services().levelManager(), 0);
    }

    /** The current palette lines as Sega words. */
    private int[] paletteWords() {
        int[] out = new int[64];
        var level = services().levelManager() == null ? null : services().levelManager().getCurrentLevel();
        if (level == null) return out;
        for (int line = 0; line < 4; line++) {
            var palette = level.getPalette(line);
            for (int c = 0; c < 16; c++) {
                out[line * 16 + c] = com.openggf.game.palette.PaletteWriteSupport.segaWordFromColor(palette.getColor(c));
            }
        }
        return out;
    }

    private void writeWords(int[] words) {
        for (int line = 0; line < 4; line++) {
            byte[] bytes = new byte[32];
            for (int c = 0; c < 16; c++) {
                bytes[c * 2] = (byte) (words[line * 16 + c] >> 8);
                bytes[c * 2 + 1] = (byte) words[line * 16 + c];
            }
            services().updatePalette(line, bytes);
        }
    }

    /** LoadFadePalette 6 wrote the same entry to {@code fadePalette}; colours 0-$2F are the FadeObjects target. */
    private void captureFadeTarget() {
        int[] words = paletteWords();
        System.arraycopy(words, 0, fadeTarget, 0, fadeTarget.length);
    }

    /**
     * FadeToWhite (common/palette_fade.asm:285-296; FadeColorsToWhite :298-318, FadeColorToWhite :322-357): 22 VSync
     * iterations inside the routine, each stepping every colour one component toward white. The main loop stands still
     * for them (the level frame counter does not advance); the engine applies the 22 steps in this frame.
     */
    private void fadeToWhite() {
        int[] words = paletteWords();
        for (int i = 0; i <= 0x15; i++) {
            for (int c = 0; c < 64; c++) words[c] = toWhite(words[c]);
        }
        writeWords(words);
    }

    private static int toWhite(int color) {
        if (color == 0xEEE) return color;
        if ((color & 0xE) != 0xE) return color + 2;
        if ((color & 0xE0) != 0xE0) return color + 0x20;
        if ((color & 0xE00) != 0xE00) return color + 0x200;
        return color;
    }

    /**
     * BossFadeObjectsFromWhite (common/palette_fade.asm:463-484): colours 0-$2F to white, then 22 VSync iterations of
     * FadeColorsFromWhite (:216-243) toward {@code fadePalette} with colour $20 held white, applied in this frame.
     */
    private void fadeObjectsFromWhite() {
        int[] words = paletteWords();
        for (int c = 0; c < 0x30; c++) words[c] = 0xEEE;
        for (int i = 0; i <= 0x15; i++) {
            for (int c = 0; c < 0x30; c++) words[c] = fromWhite(words[c], fadeTarget[c]);
            words[0x20] = 0xEEE;
        }
        writeWords(words);
    }

    /** FadeColorFromWhite (common/palette_fade.asm:247-279). */
    private static int fromWhite(int color, int target) {
        if (color == target) return color;
        int d = color - 0x200;
        if (d >= 0 && d >= target) return d;
        d = color - 0x20;
        if (d >= 0 && d >= target) return d;
        return (color - 2) & 0xFFFF;
    }

    /**
     * BossFadeStageToWhite / BossFadeStageFromWhite / BossFadeStageWritePalette (common/palette_fade.asm:362-414): every
     * $C frames the table index steps by one entry ($14 bytes in the Good Future, $16 in the Bad; to white it holds at
     * the last entry, from white it stops at 0) and the entry's words are written to colour $20 and the stage colours:
     * Good Future $37-$3F, Bad Future $32-$33, $35-$36 and $3A-$3F.
     */
    private void fadeStage(boolean toWhite) {
        fadeTimer = (fadeTimer + 1) & 0xFF;
        if (fadeTimer < 0xC) return;
        fadeTimer = 0;
        boolean good = flags().goodFuture;
        int step = good ? 0x14 : 0x16;
        int index = fadeIndex & 0xFF;
        if (toWhite) {
            index = (index + step) & 0xFF;
            if (index == (good ? 0xA0 : 0xB0)) index = good ? 0x8C : 0x9A;
        } else {
            index -= step;
            if (index < 0) index = 0;
        }
        fadeIndex = index;
        CdStageImage image = stageImage();
        int table = WwzBossCatalogue.offset(payload(), "fadeStagePalettes");
        if (image == null || table < 0) return;
        int[] colors = good ? GOOD_FUTURE_COLORS : BAD_FUTURE_COLORS;
        int[] words = paletteWords();
        for (int i = 0; i < colors.length; i++) words[colors[i]] = image.u16(table + index + i * 2);
        writeWords(words);
    }

    /** BossFadeStageWritePalette's destinations (common/palette_fade.asm:410-427): palette+$40, then the stage colours. */
    private static final int[] GOOD_FUTURE_COLORS = {0x20, 0x37, 0x38, 0x39, 0x3A, 0x3B, 0x3C, 0x3D, 0x3E, 0x3F};
    private static final int[] BAD_FUTURE_COLORS = {0x20, 0x32, 0x33, 0x35, 0x36, 0x3A, 0x3B, 0x3C, 0x3D, 0x3E, 0x3F};

    @Override
    protected com.openggf.game.soniccd.objects.boss.CdBossPart shell(ObjectSpawn spawn) {
        return new WwzBossEggman(spawn);
    }
}
