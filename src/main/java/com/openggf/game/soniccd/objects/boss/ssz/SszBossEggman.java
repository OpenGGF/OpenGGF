package com.openggf.game.soniccd.objects.boss.ssz;

import com.openggf.camera.Camera;
import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.level.CdStageCatalogue;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Stardust Speedway's Eggman, object $2C ({@code EggmanObject}, objects/r7/boss.asm:748-1449; ID from
 * r7/object_index_3.asm:3-62, the entry's position plus one), placed by the act 3 layout. He is the race's laser
 * ship: he owns {@code bossFlags} and {@code bossFight}, Metal Sonic ($2F), two exhausts ($2D) and a four-link laser
 * ($2E), and every frame, after his routine, he moves the camera's left and right bounds ({@code sub_20C97C}) and
 * kills a player the laser has caught ({@code sub_20C940}).
 *
 * <ul>
 *   <li>Routine 0 adds 7 to {@code bossFlags}, sets {@code bossFight} to 7, loads palette entry 6 and allocates the
 *       exhausts, Metal Sonic at ($C30, $1CD) and the laser.</li>
 *   <li>Routine 2 holds the right bound at $B40 and drags the left bound behind the player (X - $A0, up to $B40);
 *       from player X $BE0 he flies left to $B18, right to $CA8 and back to $C20 (120-frame waits), starting the boss
 *       music.</li>
 *   <li>Routine 4 runs the laser demonstration script (word_20C658): at 120 Metal Sonic's intro, flashes of the
 *       laser, and at 420 the chase.</li>
 *   <li>Routine 6 chases at a speed chosen by the player's X and his place on screen (word_20C930) until X $3E50.
 *       While Metal Sonic's start has set {@code var_38} bit 7 the left bound follows him (X - 8, at most $3DA0) and
 *       the right bound is $3DA0. A player within $10 of his X (or behind it) is killed: the laser has caught them.</li>
 *   <li>Routine 8 waits at $3E50 for the finish ({@code var_39}, written by Metal Sonic): 2 (Metal Sonic first) keeps
 *       him there with the laser lethal; 1 (the player first) stops the laser, flashes it for 120 frames, queues
 *       Amy's art, drops to Y $190 and flies right to X $3F60; then the zone's music, the stage's right bound, the
 *       capsule palette and the escape palette, and he deletes himself.</li>
 * </ul>
 * Both futures run the same code; the music after the escape ({@code SCMD_R7CMUS} $1F in the good future,
 * {@code SCMD_R7DMUS} $20) is the only difference.
 */
public final class SszBossEggman extends SszBossPiece {
    /** EggmanObject is index entry $2B (r7/object_index_3.asm:3-47). */
    public static final int ID = 0x2C;
    /** loc_20C580 / EggmanObject_1_Routine0: move.w #$67,d0 (SCMD_BOSSMUS); jsr SubCpuCommand (objects/r7/boss.asm:825-828). */
    static final int SCMD_BOSSMUS = 0x67;
    /** EggmanObject_2_Routine12: move.w #$20,d0 (SCMD_R7DMUS); move.w #$1F,d0 (SCMD_R7CMUS) in the good future (objects/r7/boss.asm:1121-1129). */
    static final int SCMD_R7CMUS = 0x1F, SCMD_R7DMUS = 0x20;
    /** EggmanObject_0_Routine0: move.w #6,d0; jsr LoadPalette (objects/r7/boss.asm:780-783). */
    static final int BOSS_PALETTE = 6;
    /** var_2c bits. */
    static final int ON_SCREEN = 0x04, EXHAUST = 0x08, LETHAL = 0x10, LASER_HURTS = 0x20, LASER_SHOWN = 0x40;

    /** var_2e: Metal Sonic (sub_20CF98, objects/r7/boss.asm:1919-1929). */
    SszMetalSonic metalLink;
    /** var_38: the bound mode (low nibble: right bound $B40, $3DA0 or the stage's) and bit 7, the left bound follows. */
    int boundMode;
    /** var_39: the race's finish (1 the player first, 2 Metal Sonic first; bit 7 set once settled). */
    int finish;
    /** var_3a: frames the laser stays fast after coming on screen; var_3c: frames off screen to the left. */
    private int fastFrames;
    private int offScreenFrames;
    /** {@code moveq #4,d0; jsr AddGfxQueue} has run: Amy's art is queued (SszBossArt). */
    private boolean amyArtQueued;
    /** Level-wide {@code bossFlags} and {@code bossFight} (Level/_Definitions.inc:314-316), which this object owns. */
    private int bossFlags;
    private int bossFight;

    public SszBossEggman(ObjectSpawn spawn) {
        super(spawn, "SszBossEggman");
    }

    /** {@code bossFlags}: Eggman's 7, and bit 7 while the blocks move (Metal Sonic sets it, the first block done clears it). */
    public int bossFlags() { return bossFlags; }

    public void setBossFlags(int value) { bossFlags = value & 0xFF; }

    /** {@code bossFight} (Level/_Definitions.inc:316): 7 from Eggman's init on; nothing clears it in this act. */
    public int bossFight() { return bossFight; }

    /** True once Eggman has queued PLC 4, Amy's art. */
    public boolean amyArtQueued() { return amyArtQueued; }

    /** The race's finish byte ({@code var_39}). */
    public int finish() { return finish; }

    @Override String sheet() { return SszBossArt.EGGMAN; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        AbstractPlayableSprite sonic = sonic(player);
        skipDraw = false;
        switch (routine) {
            case 0 -> init();
            case 2 -> intro(sonic);
            case 4 -> demonstration();
            case 6 -> chase(sonic);
            case 8 -> {
                if (finish(sonic)) return;
            }
            default -> { }
        }
        bounds(sonic);
        laserKill(sonic);
        animateObject(table("eggmanAnimations"));
    }

    /** EggmanObject_0_Routine0 (objects/r7/boss.asm:771-789). */
    private void init() {
        priority = 1;
        width = 0x24;
        yRadius = 0x24;
        tileWord = 0x300;
        bossFlags = (bossFlags + 7) & 0xFF;                      // addq.b #7,boss_flags
        bossFight = 7;                                          // move.b #7,boss_started
        lockScreen();
        loadPalette(BOSS_PALETTE);
        routine = 2;
        // sub_20CFBC (objects/r7/boss.asm:1933-1948): two exhausts, subtype and animation 0 and 1.
        if (spawnAfter(new SszBossExhaust(x, y, this, 0)) && spawnAfter(new SszBossExhaust(x, y, this, 1))) {
            // both allocated
        }
        set(EXHAUST);                                           // bset #3,obj.var_2c(a0)
        // sub_20CF98 (objects/r7/boss.asm:1919-1929): Metal Sonic at ($C30, $1CD), linked both ways.
        SszMetalSonic metal = new SszMetalSonic(this);
        if (spawnAfter(metal)) metalLink = metal;
        // sub_20CFFA (objects/r7/boss.asm:1952-1989): four laser links, each following the one before; the first hangs
        // $34 below Eggman, the others $20 below the last and 2 forward; the last one spits impacts and debris.
        SszBossLaser above = null;
        for (int link = 3; link >= 0; link--) {
            SszBossLaser laser = new SszBossLaser(this, above, link == 3 ? 0 : 2, link == 3 ? 0x34 : 0x20, link == 0);
            if (!spawnAfter(laser)) break;                      // bne.w locret_20D048
            above = laser;
        }
    }

    /**
     * {@code bossFight} gates the player's right boundary: {@code ObjSonic_LevelBound} drops its 56-pixel extension
     * while it is set (objects/player.asm:1607). The shared boundary reads that gate from the screen-lock flag, so
     * the boss drives it with {@code bossFight}, which no routine of this act clears.
     */
    private void lockScreen() {
        var state = services().gameState();
        if (state != null) state.setScreenLocked(true);
    }

    /** EggmanObject_0_Routine2 (objects/r7/boss.asm:793-890). */
    private void intro(AbstractPlayableSprite player) {
        floorFollow();
        switch (step) {
            case 0 -> waitForPlayer(player);
            case 1 -> {                                         // EggmanObject_1_Routine2 (:846-857)
                accelerateAndMove();
                if (0xB18 < xWord()) return;
                setXWord(0xB18);
                xSpeed = 0x600;
                accelX = 0;
                step++;
                status &= ~1;
                timer = 0x78;
            }
            case 2, 4 -> waitWord();                            // EggmanObject_1_Routine4
            case 3 -> {                                         // EggmanObject_1_Routine6 (:861-872)
                accelerateAndMove();
                if (0xCA8 > xWord()) return;
                setXWord(0xCA8);
                xSpeed = -0x600;
                accelX = 0;
                status |= 1;
                step++;
                timer = 0x78;
            }
            case 5 -> {                                         // EggmanObject_1_RoutineA (:876-887)
                accelerateAndMove();
                if (0xC20 < xWord()) return;
                setXWord(0xC20);
                xSpeed = 0;
                accelX = 0;
                step = 0;
                timer = 0;
                clear(EXHAUST);
                routine = 4;
            }
            default -> { }
        }
    }

    /** EggmanObject_1_Routine0 and loc_20C580 (objects/r7/boss.asm:813-842). */
    private void waitForPlayer(AbstractPlayableSprite player) {
        boundMode = (boundMode & 0xF0) + 1;                     // andi.b #$F0; addq.b #1,obj.var_38(a0)
        if (player == null) return;
        Camera camera = services().camera();
        int px = px(player);
        if (px < 0xBE0) {                                       // blt.w loc_20C580
            int left = (short) (px - 0xA0);
            if (camera == null || left < camera.getMinX() || left > 0xB40) return;
            camera.setMinX((short) left);                       // left_bound and target_left_bound
            return;
        }
        xSpeed = -0x600;
        accelX = 0;
        step++;
        status |= 1;
        if (camera != null) camera.setMinX((short) 0xB40);
        subCpu(SCMD_BOSSMUS);
    }

    /** EggmanObject_0_Routine4 (objects/r7/boss.asm:894-986). */
    private void demonstration() {
        accelerateAndMove();
        if (0xB00 >= xWord()) setXWord(0xB00);                  // cmp.w obj.x(a0),d0; blt.w loc_20C64A
        floorFollow();
        switch (runScript("eggmanScript")) {
            case 0 -> {                                         // loc_20C688 (:936-941): Metal Sonic's intro
                if (metalLink != null) metalLink.startIntro();
            }
            case 1, 5, 8 -> { set(LASER_SHOWN); clear(LASER_HURTS); }     // sub_20C6AA
            case 2, 6, 9 -> { set(LASER_SHOWN); set(LASER_HURTS); }       // sub_20C69C
            case 3 -> {                                         // sub_20C6C6 (:966-970)
                set(EXHAUST);
                xSpeed = -0x200;
                accelX = 0;
            }
            case 4, 7, 10 -> { clear(LASER_SHOWN); clear(LASER_HURTS); }  // sub_20C6B8
            case 11 -> {                                        // sub_20C6D8 (:974-986): the chase
                set(LASER_SHOWN);
                clear(LASER_HURTS);
                routine = 6;
                set(LETHAL);
                xSpeed = 0x200;
                speedCap = 0x200;
                accelX = 2;
                ySpeed = 0;
                gravity = 0;
                status &= ~1;
                step = 0;
                timer = 0;
            }
            default -> { }
        }
    }

    /** EggmanObject_0_Routine6 (objects/r7/boss.asm:990-1006). */
    private void chase(AbstractPlayableSprite player) {
        if (0x3E50 <= xWord()) {                                // cmp.w obj.x(a0),d0; ble.s loc_20C726
            setXWord(0x3E50);
            routine = 8;
            step = 0;
            xSpeed = 0;
            ySpeed = 0;
            return;
        }
        accelerateCappedAndMove();
        pace(player);
        floorFollow();
    }

    /**
     * sub_20C8A6 (objects/r7/boss.asm:1158-1198): d1 is Eggman's screen X plus {@code oWidth} ({@code move.b} into
     * d0, whose high byte is the Y speed's low byte from loc_20CEB0's {@code lsl.l #8}: 0 while chasing). On screen,
     * the first frame sets the X speed to the player's (at least $100) and 240 fast frames; off screen they run out.
     */
    private void pace(AbstractPlayableSprite player) {
        Camera camera = services().camera();
        int cameraX = camera == null ? 0 : camera.getX();
        int d1 = (short) (xWord() - cameraX + (((ySpeed & 0xFF) << 8) | width));
        if (d1 < 0) {
            offScreenFrames = (offScreenFrames + 1) & 0xFFFF;
            choosePace(player, d1);
            if (fastFrames != 0) {
                fastFrames = (fastFrames - 1) & 0xFFFF;
                return;
            }
            clear(ON_SCREEN);
            return;
        }
        offScreenFrames = 0;
        choosePace(player, d1);
        boolean was = has(ON_SCREEN);
        set(ON_SCREEN);
        if (was) return;
        fastFrames = 0xF0;
        int speed = player == null ? 0 : (short) player.getXSpeed();
        xSpeed = Math.max(speed, 0x100);                        // cmp.w d1,d0; bge.s; move.w d1,d0
    }

    /** sub_20C8FE (objects/r7/boss.asm:1202-1233): the {cap, acceleration} pair by screen place and player X. */
    private void choosePace(AbstractPlayableSprite player, int d1) {
        int at;
        if (d1 <= -0xA0) at = 0xC;
        else {
            int px = player == null ? 0 : px(player);
            at = px >= 0x3100 ? 8 : px >= 0xDC0 ? 4 : 0;
        }
        CdStageImage image = stageImage();
        if (image == null || payload() == null) return;
        int[] pair = SszBossCatalogue.eggmanSpeed(image, payload(), at);
        speedCap = pair[0];
        accelX = pair[1];
    }

    /** EggmanObject_0_Routine8 (objects/r7/boss.asm:1010-1136); true when Eggman deleted himself. */
    private boolean finish(AbstractPlayableSprite player) {
        switch (step) {
            case 0 -> {                                         // EggmanObject_2_Routine0 (:1033-1040)
                int who = finish & 0xF;
                if (who == 1) {                                 // loc_20C780 (:1050-1056): the player won
                    clear(LASER_HURTS);
                    clear(LETHAL);
                    boundMode &= 0xF;
                    step = 2;
                    timer = 0x1E;
                } else if (who == 2) {
                    step = 1;
                }
            }
            case 1 -> step = 1;                                 // EggmanObject_2_Routine2: stay
            case 2, 4, 6, 8 -> waitWord();
            case 3 -> {                                         // EggmanObject_2_Routine6 (:1060-1085)
                timer = (timer + 1) & 0xFFFF;
                if (timer >= 0x78) {
                    clear(LASER_SHOWN);
                    step++;
                    timer = 0x1E;
                    xSpeed = 0;
                    ySpeed = 0x200;
                    amyArtQueued = true;                        // moveq #4,d0; jsr AddGfxQueue: Amy's art

                } else if (timer % 0x14 == 0) {
                    bits ^= LASER_SHOWN;                        // eori.b #$40,obj.var_2c(a0)
                }
            }
            case 5 -> {                                         // EggmanObject_2_RoutineA (:1089-1102)
                move();
                if (yWord() < 0x190) return false;
                xSpeed = 0x600;
                ySpeed = 0;
                step++;
                timer = 0x3C;
            }
            case 7 -> {                                         // EggmanObject_2_RoutineE (:1106-1117)
                move();
                if (xWord() < 0x3F60) return false;
                step++;
                timer = 0x3C;
            }
            case 9 -> {                                         // EggmanObject_2_Routine12 (:1121-1136)
                subCpu(flags().goodFuture ? SCMD_R7CMUS : SCMD_R7DMUS);
                boundMode = 3;
                bounds(player);
                loadCapsulePalette();
                escapePalette();
                skipDraw = true;
                deleteObject();                                 // addq.l #4,sp; jmp DeleteObject
                return true;
            }
            default -> { }
        }
        return false;
    }

    /**
     * sub_20C85E (objects/r7/boss.asm:1140-1154): word_20C886 to palette line 1. The same 32 bytes also go $80 bytes
     * lower ({@code movem.l d0-d3,-$80(a4)}), to line 1 of the fade palette, which the engine does not hold.
     */
    private void escapePalette() {
        CdStageImage image = stageImage();
        int source = table("escapePalette");
        if (image != null && source >= 0) writePalette(image, source, 0x20, 0x20);
    }

    /**
     * sub_20C97C (objects/r7/boss.asm:1268-1330). With {@code var_38} bit 7 the left bound is Eggman's X - 8 (at most
     * $3DA0), and a player within $A0 of it has {@code camXCenter} set to their distance from it (even). Then the
     * right bound by the low nibble: 1 $B40, 2 $3DA0, 3 the stage's ({@code word_20292E+4}, r7/scroll_3c.asm:11-31,
     * the CamBounds right word).
     */
    private void bounds(AbstractPlayableSprite player) {
        Camera camera = services().camera();
        if (camera == null) return;
        if ((boundMode & 0x80) != 0) {
            int left = (short) (xWord() - 8);
            if (left > 0x3DA0) left = 0x3DA0;
            camera.setMinX((short) left);
            if (player != null) {
                int d0 = (short) (px(player) - left - 0xA0);
                if (d0 < 0) {
                    // addi.w #$A0,d0; andi.w #$FFFE,d0; move.w d0,scroll_focus_x: camXCenter, as the camera's
                    // offset from the screen centre (320/2).
                    int centre = (d0 + 0xA0) & 0xFFFE;
                    com.openggf.camera.CameraMotionInternalAccess.setHorizontalFocusOffset(camera,
                            (short) (centre - 160));
                }
            }
        }
        switch (boundMode & 0xF) {
            case 1 -> camera.setMaxX((short) 0xB40);
            case 2 -> camera.setMaxX((short) 0x3DA0);
            case 3 -> {
                CdStageImage image = stageImage();
                if (image != null && payload() != null) {
                    camera.setMaxX((short) image.u16(CdStageCatalogue.entry(payload()).camBounds() + 4));
                }
            }
            default -> { }
        }
    }

    /**
     * sub_20C940 (objects/r7/boss.asm:1245-1264): with {@code var_2c} bit 4, a live player whose X is at most $10 past
     * Eggman's is killed ({@code KillPlayer}, r7/player_object_collide.asm:273-293: rings, shield and invincibility do
     * not help).
     */
    private void laserKill(AbstractPlayableSprite player) {
        if (!has(LETHAL) || player == null || player.getDead()) return;
        if ((short) (px(player) - xWord()) > 0x10) return;
        com.openggf.game.soniccd.player.CdPlayerObjectFlags.setBit7(player);   // bset #7,obj.flags(a0) (:1259)
        player.applyCrushDeath();
    }

    /**
     * sub_20C9FC / sub_20CA64 (objects/r7/boss.asm:1334-1403): the floor $80 + $24 below; beyond 8 pixels either way
     * Eggman takes a lift-table step near its X instead (sub_20CA8E), else snaps to the floor.
     */
    private void floorFollow() {
        int distance = floorDistance(0x80);
        if (distance > 8 || distance < -8) {
            int[] lift = SszMetalSonic.liftNear(stageImage(), payload(), xWord());
            if (lift != null) {
                addX(lift[1]);
                addY(lift[2]);
                distance = 0;                                   // clr.w d1
            }
        }
        addY(distance);
    }

    @Override
    protected com.openggf.game.soniccd.objects.boss.CdBossPart shell(ObjectSpawn spawn) {
        return new SszBossEggman(spawn);
    }

    /** The live Eggman of the loaded stage, or null. */
    static SszBossEggman find(com.openggf.level.objects.ObjectManager objects) {
        if (objects == null) return null;
        for (var object : objects.getActiveObjects()) {
            if (object instanceof SszBossEggman eggman && !eggman.isDestroyed()) return eggman;
        }
        return null;
    }
}
