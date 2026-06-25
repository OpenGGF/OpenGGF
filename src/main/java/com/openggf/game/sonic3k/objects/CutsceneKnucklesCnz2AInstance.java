package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.PlayableEntity;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.game.sonic3k.S3kPaletteWriteSupport;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.events.S3kCnzEventWriteSupport;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.graphics.GLCommand;
import com.openggf.level.Level;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.objects.SubpixelMotion;
import com.openggf.level.render.PatternSpriteRenderer;

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
    private static final int MUSIC_FADE_WAIT = 2 * 60;
    private static final int PRE_JUMP_WAIT = 0x3F;
    private static final int POST_BOUNCE_WAIT = 0x3F;
    private static final int FIRST_JUMP_X_VEL = 0x0140;
    private static final int FIRST_JUMP_Y_VEL = -0x0600;
    private static final int FINAL_JUMP_X_VEL = 0x0400;
    private static final int FINAL_JUMP_Y_VEL = -0x0600;

    private static final int[] JUMP_FRAMES = {8, 4, 8, 5, 8, 6, 8, 7};
    private static final int JUMP_DELAY = 1;
    private static final int[] LAUGH_LOOP = {0x1E, 0x1F};
    private static final int LAUGH_DELAY = 7;

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
    private boolean cameraYLocked;
    private boolean cameraXLocked;
    private boolean knucklesMusicStarted;
    private boolean facingRight;
    private boolean visible = true;
    private boolean buttonImpactReached;
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

    public boolean hasReachedButtonImpact() {
        return buttonImpactReached;
    }

    public void forceButtonImpactForTest() {
        buttonImpactReached = true;
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
        AizIntroArtLoader.loadAllIntroArt(services());
        AizIntroArtLoader.applyKnucklesPalette(services());

        Camera camera = services().camera();
        storedMinX = camera.getMinX() & 0xFFFF;
        storedMaxX = camera.getMaxX() & 0xFFFF;
        storedMinY = camera.getMinY() & 0xFFFF;
        storedMaxYTarget = camera.getMaxYTarget() & 0xFFFF;

        services().fadeOutMusic();
        activeInstance = this;
        timer = MUSIC_FADE_WAIT;
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
        updateCameraLock();
        if (timer > 0) {
            timer--;
            return;
        }
        if (!knucklesMusicStarted) {
            services().playMusic(Sonic3kMusic.KNUCKLES.id);
            knucklesMusicStarted = true;
        }
        if (!cameraXLocked || !cameraYLocked) {
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
        int floorY = getSpawn().y();
        if (currentY < floorY) {
            return;
        }

        currentY = floorY;
        if (bounceIndex == 0) {
            bounceIndex = 1;
            xVel = -0x0100;
            yVel = -0x0400;
            facingRight = !facingRight;
            return;
        }
        if (bounceIndex == 1) {
            // ROM CutsceneKnux_CNZ2A reaches Obj_CutsceneButton during the
            // second landing, after the rightward approach and leftward hop.
            buttonImpactReached = true;
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
        animateLoop(LAUGH_LOOP, LAUGH_DELAY);
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
        // ROM loc_44D6E: lea (Pal_CNZ).l,a1; jsr (PalLoad_Line1).l — restore the
        // zone palette into line 1 so rings/objects lose the Knuckles cutscene
        // colors (otherwise line 1 keeps Pal_CutsceneKnux and rings look red).
        restoreLevelPaletteLine1();
        services().playMusic(Sonic3kMusic.CNZ2.id);
        if (blockingWall != null) {
            // ROM loc_62458 deletes the wall child once the parent's destroyed
            // status bit is set; mirror that immediately on cutscene completion.
            blockingWall.setDestroyed(true);
            blockingWall = null;
        }
        activeInstance = null;
        setDestroyed(true);
    }

    private void updateCameraLock() {
        Camera camera = services().camera();
        int cameraY = camera.getY() & 0xFFFF;
        if (!cameraYLocked) {
            if (cameraY >= CAMERA_LOCK_Y) {
                cameraYLocked = true;
                camera.setMinY((short) CAMERA_LOCK_Y);
                camera.setMaxYTarget((short) CAMERA_LOCK_Y);
            } else {
                camera.setMinY((short) cameraY);
            }
        }

        int cameraX = camera.getX() & 0xFFFF;
        if (!cameraXLocked) {
            if (cameraX >= CAMERA_LOCK_X) {
                cameraXLocked = true;
                camera.setMinX((short) CAMERA_LOCK_X);
                camera.setMaxX((short) CAMERA_LOCK_X);
            } else {
                camera.setMinX((short) cameraX);
            }
        }
    }

    /**
     * Restores palette line 1 to the level's own palette after the cutscene.
     *
     * <p>{@code AizIntroArtLoader.applyKnucklesPalette} overwrote only the GPU
     * texture for line 1 with {@code Pal_CutsceneKnux}; the level's
     * {@code getPalette(1)} is untouched. Re-caching it restores the ring/object
     * colors. ROM: {@code loc_44D6E} reloads {@code Pal_CNZ} via
     * {@code PalLoad_Line1}.
     */
    private void restoreLevelPaletteLine1() {
        Level level = services().currentLevel();
        if (level == null || level.getPaletteCount() <= 1) {
            return;
        }
        S3kPaletteWriteSupport.applyPaletteLine(
                services().paletteOwnershipRegistryOrNull(),
                level,
                services().graphicsManager(),
                S3kPaletteOwners.CNZ2_CUTSCENE_RESTORE,
                S3kPaletteOwners.PRIORITY_CUTSCENE_OVERRIDE,
                1,
                level.getPalette(1),
                true);
    }

    private void restoreStoredCameraBounds() {
        Camera camera = services().camera();
        camera.setMinX((short) storedMinX);
        camera.setMaxX((short) storedMaxX);
        camera.setMinY((short) storedMinY);
        camera.setMaxYTarget((short) storedMaxYTarget);
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
