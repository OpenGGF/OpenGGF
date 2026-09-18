package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.game.sonic3k.S3kPaletteWriteSupport;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.runtime.LrzZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RomObjectCodePointerProvider;
import com.openggf.level.objects.SubpixelMotion;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;

import java.util.List;

/**
 * ROM object {@code Obj_LRZRockCrusher} -- object id {@code $9C} in the {@code SKL} pointer set
 * (sonic3k.asm:196988-197400, ROM {@code $900E4}). The {@code S3KL} set spends the same id on
 * {@code Obj_Spiker}. Lava Reef act 1 places one of subtype {@code 0} and one of subtype
 * {@code 2}.
 *
 * <p>The crusher is the act's world-changing set piece: it waits for the camera to enter its own
 * window, locks the camera down to a new limit, rumbles in place while embedded in the rock, and
 * after three seconds its timer child rewrites the layout underneath it, drops two collapsing
 * slabs in, and lets the crusher fall through and explode.
 *
 * <h2>Init (:196989-197013)</h2>
 * <p>{@code Check_CameraInRange} (:180433-180456) gates the whole init on the camera being inside
 * {@code word_901B8} for subtype 0 ({@code Camera_Y} in {@code [$5E0,$740]}, {@code Camera_X} in
 * {@code [$DC0,$EC0]}) or {@code word_901C4} otherwise ({@code [$680,$880]} and
 * {@code [$400,$780]}); outside it, the routine pops its own return address and the object retries
 * next frame. Inside, the four live camera limits are copied to {@code Camera_stored_*}, entries 4
 * and 5 of the same table become {@code Camera_max_X_pos} and {@code Camera_target_max_Y_pos}
 * ({@code $EA0}/{@code $6A0}, or {@code $4A0}/{@code $790}), and entry 4 is also kept in
 * {@code $1C(a0)} as the X the camera must reach.
 *
 * <p>Also at init: {@code ObjDat_LRZRockCrusher} ({@code priority $180}, a {@code $80 x $40} box,
 * {@code collision_flags $10}), {@code y_radius $40}, {@code collision_property -1}, the priority
 * bit set for the non-zero subtype only, an {@code Obj_SpriteMask} child at {@code ($F40,$760)} or
 * {@code ($540,$860)}, {@code ArtKosM_LRZRockCrusher} queued, {@code Pal_LRZRockCrusher} on line 1,
 * and the eight {@code ChildObjDat_90626} hit pieces.
 *
 * <h2>Routines ({@code off_901EA}, :197057-197061)</h2>
 * <ol start="0">
 *   <li>{@code loc_901F4} (:197063-197093): two independent latches in {@code $27(a0)}. Bit 0 is
 *       set, and {@code Camera_min_Y_pos} pulled up to it, once {@code Camera_target_max_Y_pos}
 *       equals {@code Camera_max_Y_pos} -- i.e. once the camera has finished easing down to the
 *       new limit. Bit 1 keeps writing {@code Camera_min_X_pos = Camera_X_pos} until the camera
 *       passes {@code $1C(a0)}. With both set the crusher goes to routine 2, raises {@code $38}
 *       bit 2 (which releases the hit pieces) and allocates the timer child.</li>
 *   <li>{@code loc_9026E} (:197095-197125): {@code sfx_BigRumble} continuously and a one-pixel
 *       rumble -- {@code bchg #0,$38(a0)} alternates {@code +1} and {@code -1}, so there is no net
 *       descent. {@code ObjCheckFloorDist} then decides: a NEGATIVE distance means the crusher is
 *       still buried and it keeps rumbling (subject to the on-screen window), and a distance of
 *       zero or more -- which only happens once the timer child has rewritten the layout out from
 *       under it -- takes {@code loc_902BE}.</li>
 *   <li>{@code loc_902BE} (:197127-197135) loads routine 4, {@code ori.b #$28,$38(a0)},
 *       {@code $2E = $27}, {@code $3A = word_902EC[subtype]} ({@code $850} or {@code $950}),
 *       restores {@code Camera_target_max_Y_pos} and creates {@code Child7_ChangeLevSize}.
 *       {@code loc_902F0} then counts {@code $2E} down.</li>
 *   <li>{@code loc_902FE} (:197144-197152): {@code MoveSprite} -- gravity {@code $38} -- until
 *       {@code y_pos} reaches {@code $3A(a0)}, then routine 8 with {@code y_vel $40},
 *       {@code $2E = $7F} and a {@code Child6_CreateBossExplosion}.</li>
 *   <li>{@code loc_90338} (:197162-197168): {@code bset #7,status(a0)} on the frame {@code $2E}
 *       reaches {@code $40} -- the flicker the pieces read -- {@code MoveSprite2}, then
 *       {@code Obj_Wait}; {@code loc_90352} re-arms the wait for {@code $BF} frames and
 *       {@code loc_90368} (:197175-197184) requeues the two badnik art modules, restores
 *       {@code Pal_LRZ1} and deletes the crusher.</li>
 * </ol>
 *
 * <p><b>Recorded, not modelled.</b> {@code Check_CameraInRange}'s two tail comparisons read
 * {@code (a1)} and {@code 4(a1)} after four {@code (a1)+} reads, so the second one is index 6 of a
 * six-word table -- for {@code word_901B8} that is {@code word_901C4}'s first word. It sets
 * {@code $27(a0)} bit 6, which no routine here reads, so the over-read is inert. {@code FixBugs}
 * is 0 and this class reproduces the ROM's inert read by not modelling bit 6 at all.
 */
public final class LrzRockCrusherObjectInstance extends AbstractObjectInstance
        implements TouchResponseProvider, RewindRecreatable, RomObjectCodePointerProvider {

    /** {@code ObjDat_LRZRockCrusher}: {@code dc.w $180} (sonic3k.asm:197427). */
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x0180);
    /** {@code dc.b $80,$40,0,$10} (:197428). */
    private static final int WIDTH_PIXELS = 0x80;
    private static final int HEIGHT_PIXELS = 0x40;
    private static final int COLLISION_FLAGS = 0x10;
    /** {@code move.b #$40,y_radius(a0)} (:197016). */
    private static final int Y_RADIUS = 0x40;
    /** {@code word_901B8} and {@code word_901C4} (sonic3k.asm:197050-197052). */
    private static final int[] WINDOW_SUBTYPE_ZERO = {0x5E0, 0x740, 0xDC0, 0xEC0, 0xEA0, 0x6A0};
    private static final int[] WINDOW_OTHER = {0x680, 0x880, 0x400, 0x780, 0x4A0, 0x790};
    /** {@code word_902EC} (:197137-197138). */
    private static final int[] DROP_TARGET_Y = {0x850, 0x950};
    /** {@code move.w #$27,$2E(a0)} (:197130). */
    private static final int DROP_DELAY = 0x27;
    /** {@code move.w #$7F,$2E(a0)} and {@code #$BF} (:197155, :197172). */
    private static final int EXPLOSION_FRAMES = 0x7F;
    private static final int CLEANUP_FRAMES = 0xBF;
    /** {@code cmpi.w #$40,$2E(a0)} (:197163). */
    private static final int FLICKER_AT = 0x40;
    /** {@code move.w #$40,y_vel(a0)} (:197154). */
    private static final int EXPLOSION_Y_VEL = 0x40;
    /** {@code ChildObjDat_90626} (sonic3k.asm:197446-197463). */
    private static final int[][] PIECE_OFFSETS = {
            {-0x1C, 0x1C}, {0x1C, 0x1C}, {-0x24, 0x1C}, {0x24, 0x1C},
            {-0x1C, -0x24}, {0x1C, -0x24}, {-0x24, -0x24}, {0x24, -0x24}};
    /** {@code move.w #$F40,x_pos(a1)} / {@code #$540} (:197166-197174 of the init). */
    private static final int MASK_X_SUBTYPE_ZERO = 0x0F40;
    private static final int MASK_Y_SUBTYPE_ZERO = 0x0760;
    private static final int MASK_X_OTHER = 0x0540;
    private static final int MASK_Y_OTHER = 0x0860;

    private int subtype;
    private final int[] window;
    /** ROM {@code $1C(a0)}: the camera X the second latch waits for. */
    private int cameraTargetX;
    /** ROM {@code routine(a0)}: 0, 2, 4, 6, 8. */
    private int routine;
    /** ROM {@code $27(a0)} bits 0 and 1. */
    private boolean minYLatched;
    private boolean minXLatched;
    /** ROM {@code $38(a0)} bit 0 (the rumble parity), bit 2 (pieces released), bit 3 (finished). */
    private boolean rumbleDown;
    private boolean piecesReleased;
    private boolean piecesFinished;
    /** ROM {@code $2E(a0)}. */
    private int timer;
    /** ROM {@code $3A(a0)}. */
    private int dropTargetY;
    /** ROM {@code status(a0)} bit 7, the flicker the pieces read. */
    private boolean flickering;
    private boolean initialised;
    private boolean cleanupPhase;

    private final SubpixelMotion.State motion;
    private transient boolean paletteLoaded;
    private transient S3kBossExplosionController explosionController;

    public LrzRockCrusherObjectInstance(ObjectSpawn spawn) {
        super(spawn, "LRZRockCrusher");
        this.subtype = spawn.subtype() & 0xFF;
        this.window = subtype == 0 ? WINDOW_SUBTYPE_ZERO : WINDOW_OTHER;
        this.motion = new SubpixelMotion.State(spawn.x() & 0xFFFF, spawn.y() & 0xFFFF, 0, 0, 0, 0);
    }

    /**
     * {@code Obj_LRZRockCrusher} is installed from the SKL object pointer table at ROM
     * {@code $000900E4} (sonic3k.lst); its whole code block lies in one bank, so the high word
     * {@code sub_13EFC} latches into {@code Tails_CPU_interact} is {@code $0009}.
     */
    @Override
    public int romObjectCodePointerHighWord() {
        return 0x0009;
    }

    @Override
    public LrzRockCrusherObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return ObjectConstructionContext.construct(ctx.objectServices(),
                () -> new LrzRockCrusherObjectInstance(ctx.spawn()));
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        if (!initialised) {
            if (!cameraInRange()) {
                return;
            }
            initialise();
        }
        switch (routine) {
            case 0 -> waitForCameraLimits();
            case 2 -> rumble();
            case 4 -> countDownToDrop();
            case 6 -> drop();
            default -> explodeAndClean();
        }
        updateDynamicSpawn(motion.x, motion.y);
    }

    /** {@code Check_CameraInRange} (sonic3k.asm:180433-180446). */
    private boolean cameraInRange() {
        Camera camera = cameraOrNull();
        if (camera == null) {
            return false;
        }
        int cameraY = camera.getY() & 0xFFFF;
        int cameraX = camera.getX() & 0xFFFF;
        return cameraY >= window[0] && cameraY <= window[1]
                && cameraX >= window[2] && cameraX <= window[3];
    }

    /** The init tail (sonic3k.asm:197000-197013 and :197154-197174). */
    private void initialise() {
        initialised = true;
        cameraTargetX = window[4];
        Camera camera = cameraOrNull();
        LrzZoneRuntimeState state = lrzState();
        if (camera != null && state != null) {
            // Camera_stored_* <- the four live limits (:197001-197004).
            state.storeCameraBounds(camera.getMinX() & 0xFFFF, camera.getMaxX() & 0xFFFF,
                    camera.getMinY() & 0xFFFF, camera.getMaxYTarget() & 0xFFFF);
        }
        if (camera != null) {
            camera.setMaxX((short) window[4]);
            camera.setMaxYTarget((short) window[5]);
        }
        loadPalette();
        // ChildObjDat_9067A: one Obj_SpriteMask, positioned by subtype (:197018-197030).
        final int maskX = subtype == 0 ? MASK_X_SUBTYPE_ZERO : MASK_X_OTHER;
        final int maskY = subtype == 0 ? MASK_Y_SUBTYPE_ZERO : MASK_Y_OTHER;
        spawnFreeChild(() -> new SozSpriteMaskObjectInstance(
                new ObjectSpawn(maskX, maskY, 0x8B, 0x8B, 0, false, 0)));
        // CreateChild1_Normal returns the pieces already wired to their parent.
        // ChildObjDat_90626: eight pieces, subtype = index * 2 (:197031, :196933-196947).
        for (int i = 0; i < PIECE_OFFSETS.length; i++) {
            final int childSubtype = i * 2;
            final int dx = PIECE_OFFSETS[i][0];
            final int dy = PIECE_OFFSETS[i][1];
            LrzRockCrusherPieceInstance piece = spawnFreeChild(
                    () -> new LrzRockCrusherPieceInstance(childSubtype, dx, dy));
            if (piece != null) {
                piece.attachTo(this);
            }
        }
    }

    /** {@code loc_901F4} (sonic3k.asm:197063-197093). */
    private void waitForCameraLimits() {
        Camera camera = cameraOrNull();
        if (camera == null) {
            return;
        }
        if (!minYLatched) {
            int target = camera.getMaxYTarget() & 0xFFFF;
            if (target == (camera.getMaxY() & 0xFFFF)) {
                camera.setMinY((short) target);
                minYLatched = true;
            }
        }
        if (!minXLatched) {
            int cameraX = camera.getX() & 0xFFFF;
            camera.setMinX((short) cameraX);
            // move.w $1C(a0),d0 / cmp.w (Camera_X_pos).w,d0 / bhi: latch once the camera has
            // reached or passed the stored X, NOT while it is still short of it.
            if (cameraTargetX <= cameraX) {
                minXLatched = true;
            }
        }
        if (!(minYLatched && minXLatched)) {
            return;
        }
        routine = 2;
        minYLatched = false;
        minXLatched = false;
        cameraTargetX = 0;
        piecesReleased = true;
        spawnFreeChild(() -> new LrzRockCrusherTimerChildInstance(subtype));
    }

    /** {@code loc_9026E} (sonic3k.asm:197095-197125). */
    private void rumble() {
        playSfx(Sonic3kSfx.BIG_RUMBLE.id);
        // moveq #1,d0 / bchg #0,$38(a0) / beq -> +1 when the bit WAS clear, -1 when it was set.
        rumbleDown = !rumbleDown;
        motion.y = (motion.y + (rumbleDown ? 1 : -1)) & 0xFFFF;

        TerrainCheckResult floor =
                ObjectTerrainUtils.checkFloorDist(motion.x, motion.y, Y_RADIUS);
        int distance = floor.foundSurface() ? floor.distance() : 0;
        if (distance >= 0) {
            beginDrop();
            return;
        }
        if (!onScreenForRumble()) {
            // loc_902B6 (:197124-197125): the ROM abandons the whole set piece.
            screenShakeFlag(false);
            cleanUp();
        }
    }

    /**
     * {@code loc_9028E}-{@code loc_902B4} (:197104-197122): the coarse X window
     * {@code (x_pos & $FF80) - Camera_X_pos_coarse_back <= $280} and the Y window
     * {@code y_pos - Camera_Y_pos + $80 <= $200}, both unsigned.
     */
    private boolean onScreenForRumble() {
        Camera camera = cameraOrNull();
        if (camera == null) {
            return true;
        }
        int dx = ((motion.x & 0xFF80) - (camera.getX() & 0xFFFF)) & 0xFFFF;
        if (dx > 0x280) {
            return false;
        }
        int dy = ((motion.y - (camera.getY() & 0xFFFF)) + 0x80) & 0xFFFF;
        return dy <= 0x200;
    }

    /** {@code loc_902BE} (sonic3k.asm:197127-197135). */
    private void beginDrop() {
        routine = 4;
        // ori.b #$28,$38(a0): bits 3 and 5.
        piecesFinished = true;
        timer = DROP_DELAY;
        dropTargetY = DROP_TARGET_Y[subtype == 0 ? 0 : 1];
        restoreTargetMaxY(servicesOrNull(), lrzState());
        for (int kind : changeLevSizeKinds()) {
            final int k = kind;
            spawnFreeChild(() -> new S3kCameraGradualObjectInstance(k));
        }
    }

    /** {@code loc_902F0} (sonic3k.asm:197140-197147). */
    private void countDownToDrop() {
        timer--;
        if (timer < 0) {
            routine = 6;
        }
    }

    /** {@code loc_902FE} / {@code loc_90310} (sonic3k.asm:197144-197160). */
    private void drop() {
        SubpixelMotion.moveSprite(motion, SubpixelMotion.S3K_GRAVITY);
        if ((motion.y & 0xFFFF) < dropTargetY) {
            return;
        }
        routine = 8;
        motion.yVel = EXPLOSION_Y_VEL;
        timer = EXPLOSION_FRAMES;
        // Child6_CreateBossExplosion (sonic3k.asm:197157-197158): a plain emitter the owner
        // ticks, the same shape every other S3K boss uses.
        explosionController = new S3kBossExplosionController(
                motion.x & 0xFFFF, motion.y & 0xFFFF, 0, services().rng());
    }

    /** {@code loc_90338}, {@code loc_90352} and {@code loc_90368} (sonic3k.asm:197162-197184). */
    private void explodeAndClean() {
        tickExplosions();
        if (timer == FLICKER_AT) {
            flickering = true;
        }
        if (!cleanupPhase) {
            SubpixelMotion.moveSprite2(motion);
        }
        timer--;
        if (timer >= 0) {
            return;
        }
        if (!cleanupPhase) {
            // loc_90352: a second Obj_Wait of $BF frames before the art restore.
            cleanupPhase = true;
            timer = CLEANUP_FRAMES;
            return;
        }
        cleanUp();
    }

    private void tickExplosions() {
        if (explosionController == null || explosionController.isFinished()) {
            return;
        }
        explosionController.tick();
        for (S3kBossExplosionController.PendingExplosion entry
                : explosionController.drainPendingExplosions()) {
            if (entry.playSfx()) {
                playSfx(Sonic3kSfx.EXPLODE.id);
            }
            spawnFreeChild(() -> new S3kBossExplosionChild(entry.x(), entry.y()));
        }
    }

    /**
     * {@code loc_90368} (sonic3k.asm:197175-197184). The two badnik art modules it requeues belong
     * to slice 4's {@code Obj_Fireworm} and {@code Obj_Iwamodoki}; until those exist there is no
     * consumer for the queue entries, so this restores the palette and deletes, and the art
     * requeue is recorded as the remaining half of this routine.
     */
    private void cleanUp() {
        restorePalette();
        ObjectLifetimeOps.expireDynamic(this);
    }

    /** {@code move.w (Camera_stored_max_Y_pos).w,(Camera_target_max_Y_pos).w} (:197133, :197256). */
    static void restoreTargetMaxY(ObjectServices services, LrzZoneRuntimeState state) {
        if (services == null || state == null) {
            return;
        }
        Camera camera = services.camera();
        if (camera != null) {
            camera.setMaxYTarget((short) state.cameraStoredMaxY());
        }
    }

    /**
     * {@code Child7_ChangeLevSize} (sonic3k.asm:197465-197474): the four
     * {@code Obj_*LevStart/End*Gradual} objects that ease every stored bound back.
     */
    static int[] changeLevSizeKinds() {
        return new int[]{
                S3kCameraGradualObjectInstance.DEC_START_Y,
                S3kCameraGradualObjectInstance.INC_END_Y,
                S3kCameraGradualObjectInstance.DEC_START_X,
                S3kCameraGradualObjectInstance.INC_END_X};
    }

    /** {@code lea Pal_LRZRockCrusher(pc),a1 / jsr PalLoad_Line1} (sonic3k.asm:197010-197011). */
    private void loadPalette() {
        if (paletteLoaded) {
            return;
        }
        applyPaletteLine(Sonic3kConstants.PAL_LRZ_ROCK_CRUSHER_ADDR);
        paletteLoaded = true;
    }

    /** {@code lea (Pal_LRZ1).l,a1 / jsr PalLoad_Line1} (sonic3k.asm:197182-197183). */
    private void restorePalette() {
        applyPaletteLine(Sonic3kConstants.PAL_LRZ1_ADDR);
    }

    private void applyPaletteLine(int address) {
        try {
            byte[] line = services().rom().readBytes(address, 32);
            S3kPaletteWriteSupport.applyLine(
                    services().paletteOwnershipRegistryOrNull(),
                    services().currentLevel(),
                    services().graphicsManager(),
                    S3kPaletteOwners.LRZ_ROCK_CRUSHER,
                    S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE,
                    1,
                    line);
        } catch (Exception unavailable) {
            // Headless unit contexts have no palette registry; the routine's behaviour is the
            // camera work and the layout change, not the line write.
        }
    }

    private Camera cameraOrNull() {
        try {
            return services().camera();
        } catch (Exception e) {
            return null;
        }
    }

    private ObjectServices servicesOrNull() {
        try {
            return services();
        } catch (Exception e) {
            return null;
        }
    }

    private LrzZoneRuntimeState lrzState() {
        try {
            return services().zoneRuntimeState() instanceof LrzZoneRuntimeState lrz ? lrz : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void screenShakeFlag(boolean raised) {
        LrzZoneRuntimeState state = lrzState();
        if (state != null) {
            state.screenShake().writeFlag(raised ? -1 : 0);
        }
    }

    private void playSfx(int id) {
        try {
            services().playSfx(id);
        } catch (Exception e) {
            // Headless unit contexts have no audio service.
        }
    }

    // ===== Accessors, for the pieces and the tests =====

    /** ROM {@code $38(a0)} bit 2. */
    public boolean piecesReleased() {
        return piecesReleased;
    }

    /** ROM {@code $38(a0)} bit 3. */
    public boolean piecesFinished() {
        return piecesFinished;
    }

    /** ROM {@code routine(a0)}. */
    public int routine() {
        return routine;
    }

    /** ROM {@code $2E(a0)}. */
    public int timer() {
        return timer;
    }

    /** ROM {@code $3A(a0)}. */
    public int dropTargetY() {
        return dropTargetY;
    }

    /** ROM {@code $1C(a0)}. */
    public int cameraTargetX() {
        return cameraTargetX;
    }

    /** True once {@code Check_CameraInRange} has let the init tail run. */
    public boolean initialised() {
        return initialised;
    }

    /** ROM {@code status(a0)} bit 7. */
    public boolean flickering() {
        return flickering;
    }

    public int getCentreX() {
        return motion.x & 0xFFFF;
    }

    public int getCentreY() {
        return motion.y & 0xFFFF;
    }

    @Override
    public int getCollisionFlags() {
        return COLLISION_FLAGS;
    }

    @Override
    public int getCollisionProperty() {
        // move.b #-1,collision_property(a0) (sonic3k.asm:197015).
        return -1;
    }

    @Override
    public int getPriorityBucket() {
        return PRIORITY_BUCKET;
    }

    @Override
    public boolean isHighPriority() {
        // make_art_tile(ArtTile_LRZRockCrusher,1,0) leaves the bit clear; the non-zero subtype
        // sets it with bset #7,art_tile (sonic3k.asm:197018).
        return subtype != 0;
    }

    @Override
    public int getOnScreenHalfWidth() {
        return WIDTH_PIXELS;
    }

    @Override
    public int getOnScreenHalfHeight() {
        return HEIGHT_PIXELS;
    }

    @Override
    public boolean usesCustomOutOfRangeCheck() {
        return true;
    }

    @Override
    public boolean isCustomOutOfRange(int cameraX) {
        // Only Check_CameraInRange's Delete_Sprite_If_Not_In_Range can remove an uninitialised
        // crusher, and after init the routines own the object's lifetime.
        return false;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.LRZ_ROCK_CRUSHER);
        if (renderer == null) {
            return;
        }
        renderer.drawFrameIndex(0, getX(), getY(), false, false);
    }
}
