package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.PlayableEntity;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.game.sonic3k.S3kPaletteWriteSupport;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.events.S3kCnzEventWriteSupport;
import com.openggf.game.sonic3k.objects.bosses.CnzEndBossBoundaryController;
import com.openggf.game.sonic3k.objects.bosses.S3kSharedBossCameraGate;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.graphics.GLCommand;
import com.openggf.level.Level;
import com.openggf.level.Palette;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.objects.SubpixelMotion;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.ObjectTerrainUtils;

import java.util.List;

/**
 * Rival Knuckles cutscene for the first CNZ Act 2 encounter.
 *
 * <p>ROM reference: {@code CutsceneKnux_CNZ2A}.
 */
public class CutsceneKnucklesCnz2AInstance extends AbstractObjectInstance
        implements SpawnRewindRecreatable {
    private static final int CAMERA_LOCK_X = 0x1D00;
    private static final int CAMERA_LOCK_Y = 0x0280;
    private static final int CAMERA_RANGE_MIN_X = 0x1C00;
    private static final int CAMERA_RANGE_MAX_X = 0x1E00;
    private static final int CAMERA_RANGE_MIN_Y = 0x0176;
    private static final int CAMERA_RANGE_MAX_Y = 0x0300;
    private static final int MUSIC_FADE_WAIT = 2 * 60;
    private static final int PRE_JUMP_WAIT = 0x3F;
    private static final int POST_BOUNCE_WAIT = 0x3F;
    private static final int FIRST_JUMP_X_VEL = 0x0140;
    private static final int FIRST_JUMP_Y_VEL = -0x0600;
    private static final int FINAL_JUMP_X_VEL = 0x0400;
    private static final int FINAL_JUMP_Y_VEL = -0x0600;

    private static final int[] JUMP_FRAMES = {8, 4, 8, 5, 8, 6, 8, 7};
    private static final int JUMP_DELAY = 2;
    private static final int[] LAUGH_LOOP = {0x1E, 0x1F};
    private static final int[] LAUGH_STAND_LOOP = {0x1C, 0x1C, 0x1D};
    private static final int LAUGH_DELAY = 8;

    private enum Phase { INIT, CAMERA_LOCK, PRE_JUMP_WAIT, MULTI_BOUNCE, LAUGH_WAIT, FINAL_JUMP }

    private Phase phase = Phase.INIT;
    private int currentX;
    private int currentY;
    private int xSub;
    private int ySub;
    private int xVel;
    private int yVel;
    private int timer;
    private int bounceIndex;
    private int mappingFrame;
    private int animationTick;
    private int animationIndex;
    private int storedMinX;
    private int storedMaxX;
    private int storedMinY;
    private int storedMaxYTarget;
    private final S3kSharedBossCameraGate cameraGate = new S3kSharedBossCameraGate();
    private boolean facingRight;
    private boolean visible = true;
    private byte[] savedPaletteLine2;
    private CutsceneKnuxCnz2WallInstance blockingWall;

    // ROM ChildObjDat_66560: the blocking wall child is placed at parentX-$20,
    // parentY-$6C (docs/skdisasm/sonic3k.asm:134971, applied by CreateChild1_Normal
    // at :176931-176942).
    private static final int WALL_OFFSET_X = -0x20;
    private static final int WALL_OFFSET_Y = -0x6C;

    private static volatile CutsceneKnucklesCnz2AInstance activeInstance;

    public CutsceneKnucklesCnz2AInstance(ObjectSpawn spawn) {
        super(spawn, "CutsceneKnuxCNZ2A");
        this.currentX = spawn.x();
        this.currentY = spawn.y();
    }

    @Override
    public int getX() {
        return currentX;
    }

    @Override
    public int getY() {
        return currentY;
    }

    public int getRoutine() {
        return phase.ordinal() * 2;
    }

    public static CutsceneKnucklesCnz2AInstance getActiveInstance() {
        return activeInstance;
    }

    public CutsceneKnuxCnz2WallInstance getSpawnedWallForTest() {
        return blockingWall;
    }

    void rewindAttachBlockingWall(CutsceneKnuxCnz2WallInstance wall) {
        blockingWall = wall;
    }

    public static void clearActiveInstance() {
        activeInstance = null;
    }

    public static void clearActiveInstanceForTests() {
        clearActiveInstance();
    }

    public static void setActiveInstanceForTests(CutsceneKnucklesCnz2AInstance instance) {
        activeInstance = instance;
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    @Override
    public boolean isHighPriority() {
        return true;
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        if (phase == Phase.INIT && !isCameraInActivationRange()) {
            ObjectLifetimeOps.destroyRespawnableOffscreen(this);
            return;
        }
        if (phase == Phase.INIT && isPlayerKnuckles()) {
            setDestroyed(true);
            return;
        }

        switch (phase) {
            case INIT -> routineInit();
            case CAMERA_LOCK -> routineCameraLock();
            case PRE_JUMP_WAIT -> routinePreJumpWait();
            case MULTI_BOUNCE -> routineMultiBounce();
            case LAUGH_WAIT -> routineLaughWait();
            case FINAL_JUMP -> routineFinalJump();
        }
    }

    private void routineInit() {
        snapshotPaletteLine2();
        AizIntroArtLoader.loadAllIntroArt(services());
        AizIntroArtLoader.applyKnucklesPalette(services());

        Camera camera = services().camera();
        storedMinX = camera.getMinX() & 0xFFFF;
        storedMaxX = camera.getMaxX() & 0xFFFF;
        storedMinY = camera.getMinY() & 0xFFFF;
        storedMaxYTarget = camera.getMaxYTarget() & 0xFFFF;

        services().fadeOutMusic();
        cameraGate.begin(camera,
                new S3kSharedBossCameraGate.LockBounds(
                        CAMERA_LOCK_Y, CAMERA_LOCK_Y, CAMERA_LOCK_X, CAMERA_LOCK_X),
                MUSIC_FADE_WAIT);
        activeInstance = this;
        phase = Phase.CAMERA_LOCK;
        mappingFrame = 0x1E;
        animationTick = 0;
        animationIndex = 0;

        // ROM loc_622E4: CreateChild1_Normal(ChildObjDat_66560) spawns the invisible
        // SolidObjectFull2 wall (loc_62458) that blocks Sonic from running past
        // Knuckles for the duration of the cutscene.
        int wallX = getSpawn().x() + WALL_OFFSET_X;
        int wallY = getSpawn().y() + WALL_OFFSET_Y;
        blockingWall = spawnChild(() ->
                new CutsceneKnuxCnz2WallInstance(buildSpawnAt(wallX, wallY), this));
    }

    private void routineCameraLock() {
        animateLoop(LAUGH_LOOP, LAUGH_DELAY);
        if (!cameraGate.update(services().camera(),
                () -> services().playMusic(Sonic3kMusic.KNUCKLES.id))) {
            return;
        }
        timer = PRE_JUMP_WAIT;
        phase = Phase.PRE_JUMP_WAIT;
    }

    private void routinePreJumpWait() {
        animateLoop(LAUGH_LOOP, LAUGH_DELAY);
        if (timer > 0) {
            timer--;
            return;
        }
        facingRight = true;
        startJump(FIRST_JUMP_X_VEL, FIRST_JUMP_Y_VEL);
        phase = Phase.MULTI_BOUNCE;
    }

    private void routineMultiBounce() {
        updateJumpMotion();
        if (yVel < 0) {
            return;
        }
        var floor = ObjectTerrainUtils.checkFloorDist(currentX, currentY, 0x13);
        if (!floor.hasCollision() || floor.distance() >= 0) {
            return;
        }

        currentY += floor.distance();
        if (bounceIndex == 0) {
            bounceIndex = 1;
            xVel = -0x0100;
            yVel = -0x0400;
            facingRight = !facingRight;
            return;
        }
        if (bounceIndex == 1) {
            bounceIndex = 2;
            xVel = 0x0100;
            yVel = -0x0400;
            facingRight = !facingRight;
            return;
        }

        facingRight = false;
        timer = POST_BOUNCE_WAIT;
        phase = Phase.LAUGH_WAIT;
        mappingFrame = 0x1C;
        animationTick = 0;
        animationIndex = 0;
    }

    private void routineLaughWait() {
        animateLoop(LAUGH_STAND_LOOP, LAUGH_DELAY);
        if (timer > 0) {
            timer--;
            return;
        }
        facingRight = true;
        startJump(FINAL_JUMP_X_VEL, FINAL_JUMP_Y_VEL);
        phase = Phase.FINAL_JUMP;
    }

    private void routineFinalJump() {
        updateJumpMotion();
        if (isOnScreen(96)) {
            return;
        }

        S3kCnzEventWriteSupport.setWallGrabSuppressed(services(), false);
        restoreStoredCameraBounds();
        restorePaletteLine2Snapshot();
        spawnFreeChild(() -> new SongFadeTransitionInstance(2 * 60, Sonic3kMusic.CNZ2.id));
        if (blockingWall != null) {
            // ROM loc_62458 deletes the wall child once the parent's destroyed
            // status bit is set; mirror that immediately on cutscene completion.
            blockingWall.setDestroyed(true);
            blockingWall = null;
        }
        activeInstance = null;
        setDestroyed(true);
    }

    private void snapshotPaletteLine2() {
        Level level = services().currentLevel();
        if (level == null || level.getPaletteCount() <= 1) {
            return;
        }
        Palette palette = level.getPalette(1);
        savedPaletteLine2 = new byte[Palette.PALETTE_SIZE * 3];
        for (int i = 0; i < Palette.PALETTE_SIZE; i++) {
            Palette.Color color = palette.getColor(i);
            savedPaletteLine2[i * 3] = color.r;
            savedPaletteLine2[i * 3 + 1] = color.g;
            savedPaletteLine2[i * 3 + 2] = color.b;
        }
    }

    private void restorePaletteLine2Snapshot() {
        Level level = services().currentLevel();
        if (level == null || level.getPaletteCount() <= 1
                || savedPaletteLine2 == null || savedPaletteLine2.length != Palette.PALETTE_SIZE * 3) {
            return;
        }
        Palette restored = new Palette();
        for (int i = 0; i < Palette.PALETTE_SIZE; i++) {
            restored.setColor(i, new Palette.Color(
                    savedPaletteLine2[i * 3],
                    savedPaletteLine2[i * 3 + 1],
                    savedPaletteLine2[i * 3 + 2]));
        }
        S3kPaletteWriteSupport.applyPaletteLine(
                services().paletteOwnershipRegistryOrNull(),
                level,
                services().graphicsManager(),
                S3kPaletteOwners.CNZ2_CUTSCENE_RESTORE,
                S3kPaletteOwners.PRIORITY_CUTSCENE_OVERRIDE,
                1,
                restored,
                true);
    }

    private void restoreStoredCameraBounds() {
        Camera camera = services().camera();
        camera.setMaxYTarget((short) storedMaxYTarget);
        spawnFreeChild(() -> CnzEndBossBoundaryController.decreaseMinX(
                currentX, currentY, storedMinX));
        spawnFreeChild(() -> CnzEndBossBoundaryController.decreaseMinY(
                currentX, currentY, storedMinY));
        spawnFreeChild(() -> CnzEndBossBoundaryController.increaseMaxX(
                currentX, currentY, storedMaxX));
    }

    private boolean isCameraInActivationRange() {
        Camera camera = services().camera();
        int cameraX = camera.getX() & 0xFFFF;
        int cameraY = camera.getY() & 0xFFFF;
        return cameraX >= CAMERA_RANGE_MIN_X && cameraX <= CAMERA_RANGE_MAX_X
                && cameraY >= CAMERA_RANGE_MIN_Y && cameraY <= CAMERA_RANGE_MAX_Y;
    }

    private void startJump(int newXVel, int newYVel) {
        xVel = newXVel;
        yVel = newYVel;
        mappingFrame = 8;
        animationTick = 0;
        animationIndex = 0;
    }

    private void updateJumpMotion() {
        animateLoop(JUMP_FRAMES, JUMP_DELAY);
        SubpixelMotion.State motion = new SubpixelMotion.State(
                currentX, currentY, xSub, ySub, xVel, yVel);
        SubpixelMotion.objectFallXY(motion, SubpixelMotion.S3K_GRAVITY);
        currentX = motion.x;
        currentY = motion.y;
        xSub = motion.xSub;
        ySub = motion.ySub;
        xVel = motion.xVel;
        yVel = motion.yVel;
    }

    private boolean isPlayerKnuckles() {
        return S3kRuntimeStates.resolvePlayerCharacter(
                services().zoneRuntimeRegistry(),
                services().configuration()) == PlayerCharacter.KNUCKLES;
    }

    private void animateLoop(int[] frames, int delay) {
        if (animationTick <= 0) {
            mappingFrame = frames[animationIndex];
            animationIndex = (animationIndex + 1) % frames.length;
            animationTick = delay;
        }
        animationTick--;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!visible) {
            return;
        }
        PatternSpriteRenderer renderer = AizIntroArtLoader.getKnucklesRenderer(services());
        if (renderer == null || !renderer.isReady()) {
            return;
        }
        renderer.drawFrameIndex(mappingFrame, currentX, currentY, facingRight, false);
    }
}
