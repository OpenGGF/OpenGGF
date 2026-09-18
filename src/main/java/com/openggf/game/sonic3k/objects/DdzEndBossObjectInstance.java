package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.PlayableEntity;
import com.openggf.game.save.SaveReason;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.runtime.DdzZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * ROM {@code Obj_DDZEndBoss} ($B6, sonic3k.asm:173421-173820): the Doomsday end boss. The object
 * itself draws nothing; its body is the foreground plane positioned from {@code Events_bg+$02/$04},
 * which it writes every frame, plus sprite children.
 *
 * <p><b>Phase 1</b> (routines 0-{@code $A}). Routine 0 sets {@code _unkFAB8} bit 1 (the flight
 * controller locks the camera), {@code Events_bg+$00 = $200}, places the boss at
 * {@code Camera_X + $140, $A0} and creates the body {@code loc_81E3C}, three flicker parts
 * {@code loc_81F36} and three turrets {@code loc_81E82}. Routine 2 swings in one pixel a frame for
 * {@code $90} frames, then sets {@code $38} bit 3 (turrets fire), eases the left camera limit
 * {@code $100} back and creates the missile launcher {@code loc_81F94}. Routine 4 tracks the camera
 * with {@code sub_830C0} until the body is destroyed; routine 6 explodes for five seconds while the
 * autoscroll decelerates; routine 8 falls away darkening palette line 3 three times; routine
 * {@code $A} waits under the white flash with the {@code Pal_DDZ} line 3 reload and the Master
 * Emerald art queued.
 *
 * <p><b>Phase 2</b> (routines {@code $C/$E}). The ship rises from below with {@code Events_bg+$00 =
 * $600}, three {@code loc_81D72} parts and two {@code loc_81F7E} parts, accelerates right, then sets
 * {@code _unkFAB8} bit 0 (level wrap), eight hit points, a swing and eased Y limits. Routine
 * {@code $E} keeps it ahead of Player 1 ({@code loc_81B26}), fires {@code sub_8307C} attacks and
 * takes {@code loc_82DCE} overlap damage.
 *
 * <p><b>Exit</b> ({@code loc_81BBE} routines 0-6). The eighth hit locks the controls, pins
 * {@code Super_frame_count}, pushes the controller back and allocates {@code loc_82F1C}; the camera
 * creeps right until the boss is {@code $40} ahead, the ship falls under explosions, the
 * {@code loc_85E64} white fade runs while Player 1 flies right at 8 pixels a frame, and
 * {@code loc_81CA4} saves and starts {@code $D01}.
 */
public final class DdzEndBossObjectInstance extends AbstractDdzObjectInstance
        implements DdzCreateBossExplosionObjectInstance.ExplosionStopFlag {
    static final int ROUTINE_ENTER = 2;
    static final int ROUTINE_FIGHT = 4;
    static final int ROUTINE_DEFEAT_EXPLODE = 6;
    static final int ROUTINE_FALL = 8;
    static final int ROUTINE_FLASH_WAIT = 0xA;
    static final int ROUTINE_RISE = 0xC;
    static final int ROUTINE_CHASE = 0xE;

    /** {@code word_82D86} colour offsets on palette line 3 and the two {@code word_82D9E} rows. */
    static final int[] FLASH_COLOUR_INDICES = {3, 4, 6, 7, 8, 9, 0xA, 0xB, 0xC, 0xD, 0xE, 0xF};
    static final int[][] FLASH_ROWS = {
            {0x00A, 0x006, 0xCAA, 0xA88, 0x866, 0x444, 0xE42, 0xE00, 0xC00, 0x600, 0x200, 0x000},
            {0x888, 0xAAA, 0xCCC, 0xAAA, 0x888, 0x666, 0xECC, 0xECA, 0xAAA, 0xAAA, 0xCCC, 0xEEE}};
    /** {@code word_82E92}: phase-2 overlap box. */
    private static final int[] CHASE_TOUCH_BOX = {0x20, 0x40, 0x20, 0x40};

    private boolean exitMode;
    private int routine;
    private int xPos;
    private int yPos;
    private short xVel;
    private short yVel;
    /** {@code $2E}, {@code $3A}, {@code $39}. */
    private int timer;
    private int timer3A;
    private int counter39;
    /** {@code $38}: bit 0 swing direction, 3 turrets armed, 4 exit started, 5 explosions stop, 7 risen. */
    private int flags;
    /** {@code status} bit 7. */
    private boolean statusDestroyed;
    /** {@code collision_property} and {@code $20} flash timer for phase 2. */
    private int hitPoints;
    private int flashTimer;
    private boolean initialized;


    public DdzEndBossObjectInstance(ObjectSpawn spawn) {
        super(spawn, "DDZEndBoss", null);
        xPos = (spawn.x() & 0xFFFF) << 16;
        yPos = (spawn.y() & 0xFFFF) << 16;
    }

    @Override
    public DdzEndBossObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new DdzEndBossObjectInstance(ctx.spawn());
    }

    @Override
    public int getX() {
        return (xPos >>> 16) & 0xFFFF;
    }

    @Override
    public int getY() {
        return (yPos >>> 16) & 0xFFFF;
    }

    boolean flag(int bit) {
        return (flags & (1 << bit)) != 0;
    }

    boolean destroyedStatus() {
        return statusDestroyed;
    }

    boolean inExit() {
        return exitMode;
    }

    int routine() {
        return routine;
    }

    @Override
    public boolean stopsChildExplosions() {
        return flag(5);
    }

    @Override
    protected void updateObject(int vIntRunCount, PlayableEntity playerEntity) {
        DdzZoneRuntimeState ddz = DdzObjectSupport.ddz(services());
        if (ddz == null) {
            return;
        }
        xPos -= ddz.wrapOffset() << 16;
        if (exitMode) {
            updateExit(ddz, vIntRunCount);
            xPos += ddz.cameraDelta() << 16;
        } else {
            switch (routine) {
                case 0 -> init(ddz);
                case ROUTINE_ENTER -> enter(ddz);
                case ROUTINE_FIGHT -> fight(ddz);
                case ROUTINE_DEFEAT_EXPLODE -> defeatExplode();
                case ROUTINE_FALL -> fall(ddz);
                case ROUTINE_FLASH_WAIT -> flashWait(ddz);
                case ROUTINE_RISE -> rise(ddz);
                case ROUTINE_CHASE -> chase(ddz);
                default -> throw new IllegalStateException("DDZ end boss routine " + routine);
            }
        }
        if (!isDestroyed()) {
            ddz.setBossPosition(getX(), getY());
        }
    }

    /** {@code loc_8180E}. */
    private void init(DdzZoneRuntimeState ddz) {
        routine = ROUTINE_ENTER;
        ddz.setPhaseFlag(1);
        ddz.setForegroundWindowBase(0x200);
        Camera camera = services().camera();
        xPos = (((camera.getX() & 0xFFFF) + 0x140) & 0xFFFF) << 16;
        yPos = 0xA0 << 16;
        timer = 0x8F;
        swingSetup();
        // CreateChild6_Simple: body, three flicker parts, three turrets.
        spawnChild(() -> new DdzEndBossBodyObjectInstance(this));
        for (int i = 0; i < 3; i++) {
            int subtype = i * 2;
            spawnChild(() -> new DdzEndBossPartObjectInstance(this, DdzEndBossPartObjectInstance.KIND_FLICKER, subtype));
        }
        for (int i = 0; i < 3; i++) {
            int subtype = i * 2;
            spawnChild(() -> new DdzEndBossTurretObjectInstance(this, subtype));
        }
    }

    /** {@code loc_81856}. */
    private void enter(DdzZoneRuntimeState ddz) {
        swingUpAndDown();
        moveSprite2();
        xPos -= 1 << 16;
        timer = (short) (timer - 1);
        if (timer >= 0) {
            return;
        }
        routine = ROUTINE_FIGHT;
        flags |= 1 << 3;
        Camera camera = services().camera();
        ddz.setCameraStoredMinX(((camera.getMinX() & 0xFFFF) - 0x100) & 0xFFFF);
        spawnFreeChild(() -> new S3kCameraGradualObjectInstance(S3kCameraGradualObjectInstance.DEC_START_X));
        spawnFreeChild(() -> new DdzEndBossLauncherObjectInstance(this));
    }

    /** {@code loc_818B4}. */
    private void fight(DdzZoneRuntimeState ddz) {
        swingUpAndDown();
        int subpixelWord = moveSprite2();
        trackCamera(0x160, 0x130, subpixelWord);
        DdzEndBossBodyObjectInstance body = DdzEndBossBodyObjectInstance.find(services());
        if (body == null || !body.destroyedStatus()) {
            return;
        }
        routine = ROUTINE_DEFEAT_EXPLODE;
        statusDestroyed = true;
        timer = 5 * 60 - 1;
        ddz.setScrollAcceleration(-0x20);
        int x = getX();
        int y = getY();
        spawnFreeChild(() -> DdzBossExplosionClusterObjectInstance.defeat(this, x, y));
    }

    /** {@code loc_81914}. */
    private void defeatExplode() {
        trackCamera(0x160, 0x130, 0);
        timer = (short) (timer - 1);
        if (timer >= 0) {
            return;
        }
        routine = ROUTINE_FALL;
        timer3A = 60 - 1;
        counter39 = 3;
        xVel = -0x300;
        yVel = 0x100;
        spawnFreeChild(() -> new DdzEndBossExplosionAnchorObjectInstance(this, DdzEndBossExplosionAnchorObjectInstance.KIND_FALL));
    }

    /** {@code loc_81958}. */
    private void fall(DdzZoneRuntimeState ddz) {
        timer3A = (short) (timer3A - 1);
        if (timer3A < 0) {
            timer3A = 0x59;
            counter39 = (counter39 - 1) & 0xFF;
            if ((byte) counter39 >= 0) {
                DdzPalette.decreaseLine(services(), 2);
            }
        }
        services().playSfx(Sonic3kSfx.RUMBLE_2.id);
        trackCamera(0x160, 0x130, 0);
        moveSprite2();
        int limit = ((services().camera().getMaxY() & 0xFFFF) + 0xD0) & 0xFFFF;
        // cmp.w y_pos(a0),d0 / bhs.w locret: keep falling while the limit is at or below y.
        if (limit >= getY()) {
            return;
        }
        routine = ROUTINE_FLASH_WAIT;
        flags |= 0x30;
        timer = 0x1F;
        services().playSfx(Sonic3kSfx.MISSILE_EXPLODE.id);
        spawnFreeChild(() -> new DdzWhiteFadeObjectInstance(DdzWhiteFadeObjectInstance.Mode.DDZ_FLASH));
        // Pal_DDZ+$20 -> Normal_palette_line_3, Normal_palette -> Target_palette.
        DdzPalette.loadDdzLine3(services());
        DdzPalette.copyNormalToTarget(services());
        // Queue_Kos_Module ArtKosM_BossMasterEmerald: the standalone sheet is registered with the zone art.
    }

    /** {@code loc_81A00}. */
    private void flashWait(DdzZoneRuntimeState ddz) {
        timer = (short) (timer - 1);
        if (timer >= 0) {
            return;
        }
        routine = ROUTINE_RISE;
        statusDestroyed = false;
        flags &= 0xCF;
        DdzFlightControllerObjectInstance controller = DdzObjectSupport.controller(services());
        if (controller != null) {
            controller.clearCameraFollow();
        }
        ddz.setForegroundWindowBase(0x600);
        Camera camera = services().camera();
        int cameraX = camera.getX() & 0xFFFF;
        camera.setMinX((short) cameraX);
        camera.setMaxX((short) cameraX);
        xPos = ((cameraX - 0x80) & 0xFFFF) << 16;
        int cameraY = camera.getY() & 0xFFFF;
        camera.setMinY((short) cameraY);
        // Camera_max_Y_pos and Camera_target_max_Y_pos.
        camera.setMaxY((short) cameraY);
        yPos = ((cameraY + 0xE0) & 0xFFFF) << 16;
        xVel = 0;
        yVel = -0x200;
        for (int i = 0; i < 3; i++) {
            int subtype = i * 2;
            spawnChild(() -> new DdzEndBossShipPartObjectInstance(this, subtype));
        }
        for (int i = 0; i < 2; i++) {
            int subtype = i * 2;
            spawnChild(() -> new DdzEndBossPartObjectInstance(this, DdzEndBossPartObjectInstance.KIND_REAR, subtype));
        }
    }

    /** {@code loc_81A74}. */
    private void rise(DdzZoneRuntimeState ddz) {
        moveSprite2();
        Camera camera = services().camera();
        int stopY = ((camera.getY() & 0xFFFF) + 0x20) & 0xFFFF;
        // cmp.w y_pos(a0),d0 / blo.s: still below the stop line.
        if (stopY >= getY()) {
            boolean wasSet = flag(7);
            flags |= 1 << 7;
            if (!wasSet) {
                services().playSfx(Sonic3kSfx.BLAST.id);
                yVel = 0;
            }
        }
        xVel = (short) (xVel + 0x10);
        int goal = ((camera.getX() & 0xFFFF) + 0x1C0) & 0xFFFF;
        // cmp.w x_pos(a0),d0 / bhi.s: wait until x reaches Camera_X + $1C0.
        if (goal > getX()) {
            return;
        }
        routine = ROUTINE_CHASE;
        flags &= ~(1 << 4);
        ddz.setScrollAcceleration(0x800);
        ddz.setPhaseFlag(0);
        hitPoints = 7;
        timer = 0;
        xVel = 0;
        swingSetup();
        ddz.setCameraStoredMinY(0);
        yPos = (0xC0 << 16) | (yPos & 0xFFFF);
        spawnFreeChild(() -> new S3kCameraGradualObjectInstance(S3kCameraGradualObjectInstance.DEC_START_Y));
        ddz.setCameraStoredMaxY(0x120);
        camera.setMaxYTarget((short) 0x120);
        spawnFreeChild(() -> new S3kCameraGradualObjectInstance(S3kCameraGradualObjectInstance.INC_END_Y));
        DdzFlightControllerObjectInstance controller = DdzObjectSupport.controller(services());
        if (controller != null) {
            controller.setFollowY(true);
        }
    }

    /** {@code loc_81B26}. */
    private void chase(DdzZoneRuntimeState ddz) {
        swingUpAndDown();
        int subpixelWord = moveSprite2();
        AbstractPlayableSprite player = DdzObjectSupport.player(services());
        DdzFlightControllerObjectInstance controller = DdzObjectSupport.controller(services());
        // move.b x_vel(a1),d1 / bmi.s / moveq #0,d1: the high byte of a leftward push, halved.
        int pushByte = controller == null ? 0 : (byte) (controller.xVelocity() >> 8);
        int d1 = pushByte < 0 ? (short) (-pushByte) >> 1 : 0;
        Camera camera = services().camera();
        boolean rightHeld = player != null && player.isRightPressed();
        int x = getX();
        if (!rightHeld) {
            int next = (x + 2 + d1) & 0xFFFF;
            int limit = ((camera.getMaxX() & 0xFFFF) + 0x40) & 0xFFFF;
            if (next >= limit) {
                return;
            }
            xPos = (next << 16) | (subpixelWord & 0xFFFF);
        } else {
            int next = (x - 2 + d1) & 0xFFFF;
            int limit = ((camera.getMinX() & 0xFFFF) + 0x60) & 0xFFFF;
            if (next > limit) {
                xPos = (next << 16) | (subpixelWord & 0xFFFF);
            }
        }
        // loc_81B82: keep x + 7 (+8 more while flashing) inside min + $60 .. min + $1B0 as longwords.
        long d0 = (xPos & 0xFFFFFFFFL) + 0x70000L + (flashTimer != 0 ? 0x80000L : 0);
        long cameraLong = (((camera.getMinX() & 0xFFFFL) << 16) | (camera.getMaxX() & 0xFFFFL));
        long low = (cameraLong + 0x600000L) & 0xFFFFFFFFL;
        long clamped;
        if (d0 < low) {
            clamped = low;
        } else {
            long high = (low + 0x1500000L) & 0xFFFFFFFFL;
            clamped = d0 < high ? d0 : high;
        }
        xPos = (int) clamped;
        fireAttacks();
        chaseTouch(ddz);
    }

    /** {@code sub_8307C}. */
    private void fireAttacks() {
        Camera camera = services().camera();
        int onScreen = ((camera.getX() & 0xFFFF) + 0x140) & 0xFFFF;
        if (onScreen < getX()) {
            return;
        }
        timer = (short) (timer - 1);
        if (timer >= 0) {
            return;
        }
        timer = 0x82;
        AbstractPlayableSprite player = DdzObjectSupport.player(services());
        int belowLine = (getY() + 0x68) & 0xFFFF;
        // cmp.w (Player_1+y_pos).w,d0 / bhs.s: Player 1 above the line gets the rockets.
        if (player != null && belowLine < (player.getCentreY() & 0xFFFF)) {
            services().playSfx(Sonic3kSfx.BOSS_PROJECTILE.id);
            for (int i = 0; i < 4; i++) {
                int subtype = i * 2;
                spawnChild(() -> new DdzEndBossBombObjectInstance(this, subtype));
            }
            return;
        }
        for (int i = 0; i < 4; i++) {
            int subtype = i * 2;
            spawnChild(() -> new DdzEndBossRocketObjectInstance(this, subtype));
        }
    }

    /** {@code loc_82DCE}. */
    private void chaseTouch(DdzZoneRuntimeState ddz) {
        if (!DdzObjectSupport.playerPowered(services())) {
            return;
        }
        if (flashTimer == 0) {
            AbstractPlayableSprite player = DdzObjectSupport.player(services());
            if (player == null || !DdzObjectSupport.inMyRange(getX(), getY(), player.getCentreX(),
                    player.getCentreY(), CHASE_TOUCH_BOX)) {
                return;
            }
            hitPoints = (hitPoints - 1) & 0xFF;
            if ((byte) hitPoints < 0) {
                beginExit(ddz);
                return;
            }
            flashTimer = 0x20;
            player.setInvulnerableFrames(90);
            services().playSfx(Sonic3kSfx.BOSS_HIT.id);
            DdzObjectSupport.penaliseScrollSpeed(ddz);
            DdzFlightControllerObjectInstance controller = DdzObjectSupport.controller(services());
            if (controller != null) {
                controller.setXVelocity(-0x1000);
            }
        }
        DdzPalette.applyFlashRow(services(), (flashTimer & 1) != 0 ? 0 : 1);
        flashTimer = (flashTimer - 1) & 0xFF;
    }

    /** {@code loc_82E2C}. */
    private void beginExit(DdzZoneRuntimeState ddz) {
        // HUD_AddToScore d0 = 100.
        services().gameState().addScore(100);
        exitMode = true;
        routine = 0;
        statusDestroyed = true;
        AbstractPlayableSprite player = DdzObjectSupport.player(services());
        if (player != null) {
            player.setControlLocked(true);
            if (player.getSuperStateController() instanceof com.openggf.game.sonic3k.Sonic3kSuperStateController superState) {
                superState.holdDoomsdayRingDrain();
            }
        }
        Camera camera = services().camera();
        camera.setMinY(camera.getY());
        camera.setMaxY(camera.getY());
        DdzFlightControllerObjectInstance controller = DdzObjectSupport.controller(services());
        if (controller != null) {
            controller.setFollowY(false);
            controller.setXVelocity(-0x400);
            controller.setYVelocity(0);
        }
        int x = getX();
        int y = getY();
        spawnFreeChild(() -> DdzBossExplosionClusterObjectInstance.exit(this, x, y));
    }

    private DdzWhiteFadeObjectInstance findExitFade() {
        var manager = services().objectManager();
        if (manager == null) {
            return null;
        }
        for (var object : manager.getActiveObjects()) {
            if (object instanceof DdzWhiteFadeObjectInstance fade && fade.holdsWhite()) {
                return fade;
            }
        }
        return null;
    }

    /** {@code loc_81BBE}. */
    private void updateExit(DdzZoneRuntimeState ddz, int vIntRunCount) {
        Camera camera = services().camera();
        switch (routine) {
            case 0 -> {
                // loc_81BF2
                int cameraX = ((camera.getX() & 0xFFFF) + 1) & 0xFFFF;
                camera.setX((short) cameraX);
                if (((cameraX + 0x40) & 0xFFFF) < getX()) {
                    return;
                }
                routine = 2;
                flags |= 1 << 4;
                timer = 0x40;
                spawnFreeChild(() -> new DdzEndBossExplosionAnchorObjectInstance(this, DdzEndBossExplosionAnchorObjectInstance.KIND_EXIT));
            }
            case 2 -> {
                // loc_81C2C
                if ((vIntRunCount & 7) == 0) {
                    services().playSfx(Sonic3kSfx.MISSILE_EXPLODE.id);
                }
                yVel = (short) (yVel + 0x10);
                moveSprite2();
                timer = (short) (timer - 1);
                if (timer >= 0) {
                    return;
                }
                routine = 4;
                spawnFreeChild(() -> new DdzWhiteFadeObjectInstance(DdzWhiteFadeObjectInstance.Mode.TO_WHITE_HOLD));
            }
            case 4 -> {
                // loc_81C70
                DdzFlightControllerObjectInstance controller = DdzObjectSupport.controller(services());
                if (controller != null) {
                    controller.addX(8);
                }
                yVel = (short) (yVel + 0x10);
                moveSprite2();
                // movea.w $44(a0),a1 / btst #7,status(a1): looked up live so a rewind restore
                // finds the recreated fade.
                DdzWhiteFadeObjectInstance exitFade = findExitFade();
                if (exitFade != null && !exitFade.finished()) {
                    return;
                }
                routine = 6;
                timer = 0x1F;
                AbstractPlayableSprite player = DdzObjectSupport.player(services());
                if (player != null && player.getSuperStateController()
                        instanceof com.openggf.game.sonic3k.Sonic3kSuperStateController superState) {
                    superState.clearDoomsdayPalette();
                }
            }
            case 6 -> {
                // loc_81CA4
                timer = (short) (timer - 1);
                if (timer >= 0) {
                    return;
                }
                services().requestSessionSave(SaveReason.PROGRESSION_SAVE);
                services().requestZoneAndAct(0x0D, 1, true);
                deleteNow();
            }
            default -> throw new IllegalStateException("DDZ end boss exit routine " + routine);
        }
    }

    /**
     * {@code sub_830C0}: holding Right pulls the boss back two pixels (not past
     * {@code Camera_min_X + d3}); otherwise it drifts forward two pixels up to
     * {@code Camera_max_X + d2}, both offset by half of a leftward controller push. The write keeps
     * the high word of {@code d0} as the fraction.
     */
    private void trackCamera(int maxOffset, int minOffset, int subpixelWord) {
        DdzFlightControllerObjectInstance controller = DdzObjectSupport.controller(services());
        int pushByte = controller == null ? 0 : (byte) (controller.xVelocity() >> 8);
        int d1 = (short) (-pushByte) >> 1;
        AbstractPlayableSprite player = DdzObjectSupport.player(services());
        Camera camera = services().camera();
        int x = getX();
        if (player == null || !player.isRightPressed()) {
            int next = (x + 2 + d1) & 0xFFFF;
            int limit = ((camera.getMaxX() & 0xFFFF) + maxOffset) & 0xFFFF;
            if (next < limit) {
                xPos = (next << 16) | (subpixelWord & 0xFFFF);
            }
            return;
        }
        int next = (x - 2 + d1) & 0xFFFF;
        int limit = ((camera.getMinX() & 0xFFFF) + minOffset) & 0xFFFF;
        if (next > limit) {
            xPos = (next << 16) | (subpixelWord & 0xFFFF);
        }
    }

    /** {@code Swing_Setup1}. */
    private void swingSetup() {
        yVel = 0xC0;
        flags &= ~1;
    }

    /** {@code Swing_UpAndDown} with {@code $3E = $C0}, {@code $40 = $10}. */
    private void swingUpAndDown() {
        int d0 = 0x10;
        int d1 = yVel;
        int d2 = 0xC0;
        if ((flags & 1) == 0) {
            d0 = -d0;
            d1 = (short) (d1 + d0);
            d2 = -d2;
            if (d1 > d2) {
                yVel = (short) d1;
                return;
            }
            flags |= 1;
            d0 = -d0;
            d2 = -d2;
        }
        d1 = (short) (d1 + d0);
        if (d1 >= d2) {
            flags &= ~1;
            d0 = -d0;
            d1 = (short) (d1 + d0);
        }
        yVel = (short) d1;
    }

    /**
     * {@code MoveSprite2}. Returns the high word of the last {@code ext.l / lsl.l #8} product
     * ({@code y_vel << 8}), which {@code sub_830C0}'s {@code swap d0} writes as the X fraction.
     */
    private int moveSprite2() {
        xPos += xVel << 8;
        int yDelta = yVel << 8;
        yPos += yDelta;
        return (yDelta >> 16) & 0xFFFF;
    }

    void addXVelocity(int delta) {
        xVel = (short) (xVel + delta);
    }


    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
    }


}
