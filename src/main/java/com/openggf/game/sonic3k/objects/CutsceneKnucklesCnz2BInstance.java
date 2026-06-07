package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.PlayableEntity;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SubpixelMotion;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;

import java.util.List;

/**
 * Rival Knuckles cutscene for the late CNZ Act 2 teleporter route.
 *
 * <p>ROM reference: {@code CutsceneKnux_CNZ2B}.
 */
public class CutsceneKnucklesCnz2BInstance extends AbstractObjectInstance {
    private static final int TRIGGER_X = 0x4728;
    private static final int WALK_RIGHT_STOP_X = 0x4760;
    private static final int PRE_JUMP_WAIT = 0x1F;
    private static final int POST_JUMP_WAIT = 0x7F;
    private static final int JUMP_X_VEL = -0x0100;
    private static final int JUMP_Y_VEL = -0x0400;
    private static final int EXIT_SPEED = 4;

    private static final int[] RUN_FRAMES = {0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10, 0x11};
    private static final int RUN_DELAY = 5;
    private static final int[] JUMP_FRAMES = {8, 4, 8, 5, 8, 6, 8, 7};
    private static final int JUMP_DELAY = 1;

    private enum Phase { INIT, WAIT_FOR_PLAYER_JUMP, FORCE_PLAYER_RIGHT, PRE_JUMP_WAIT, JUMP, POST_JUMP_WAIT, EXIT_RIGHT, FORCE_PLAYER_LEFT }

    private Phase phase = Phase.INIT;
    private int currentX;
    private int currentY;
    private int xSub;
    private int ySub;
    private int xVel;
    private int yVel;
    private int timer;
    private int mappingFrame;
    private int animationTick;
    private int animationIndex;
    private boolean facingRight;
    private boolean bounced;
    private boolean visible = true;

    private static volatile CutsceneKnucklesCnz2BInstance activeInstance;

    public CutsceneKnucklesCnz2BInstance(ObjectSpawn spawn) {
        super(spawn, "CutsceneKnuxCNZ2B");
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

    public static CutsceneKnucklesCnz2BInstance getActiveInstance() {
        return activeInstance;
    }

    public static void clearActiveInstance() {
        activeInstance = null;
    }

    public static void clearActiveInstanceForTests() {
        clearActiveInstance();
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

        AbstractPlayableSprite player = playerEntity instanceof AbstractPlayableSprite sprite ? sprite : null;
        switch (phase) {
            case INIT -> routineInit(player);
            case WAIT_FOR_PLAYER_JUMP -> routineWaitForPlayerJump(player);
            case FORCE_PLAYER_RIGHT -> routineForcePlayerRight(player);
            case PRE_JUMP_WAIT -> routinePreJumpWait();
            case JUMP -> routineJump();
            case POST_JUMP_WAIT -> routinePostJumpWait();
            case EXIT_RIGHT -> routineExitRight(player);
            case FORCE_PLAYER_LEFT -> routineForcePlayerLeft(player);
        }
    }

    private void routineInit(AbstractPlayableSprite player) {
        AizIntroArtLoader.loadAllIntroArt(services());
        AizIntroArtLoader.applyKnucklesPalette(services());
        services().playMusic(Sonic3kMusic.KNUCKLES.id);

        if (player != null) {
            player.clearLogicalInputState();
            player.clearForcedInputMask();
            player.setControlLocked(true);
        }
        activeInstance = this;
        phase = Phase.WAIT_FOR_PLAYER_JUMP;
    }

    private void routineWaitForPlayerJump(AbstractPlayableSprite player) {
        if (player == null) {
            return;
        }
        if ((player.getCentreX() & 0xFFFF) >= TRIGGER_X && player.getAir()) {
            phase = Phase.FORCE_PLAYER_RIGHT;
        }
    }

    private void routineForcePlayerRight(AbstractPlayableSprite player) {
        if (player == null) {
            return;
        }
        player.setControlLocked(true);
        player.setForcedInputMask(AbstractPlayableSprite.INPUT_RIGHT);
        if ((player.getCentreX() & 0xFFFF) < WALK_RIGHT_STOP_X) {
            return;
        }

        player.clearForcedInputMask();
        player.clearLogicalInputState();
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        player.setGSpeed((short) 0);
        timer = PRE_JUMP_WAIT;
        phase = Phase.PRE_JUMP_WAIT;
    }

    private void routinePreJumpWait() {
        if (timer > 0) {
            timer--;
            return;
        }
        startJump();
        phase = Phase.JUMP;
    }

    private void routineJump() {
        updateJumpMotion();
        if (yVel < 0) {
            return;
        }
        int floorY = getSpawn().y();
        if (currentY < floorY) {
            return;
        }

        currentY = floorY;
        if (!bounced) {
            bounced = true;
            xVel = -xVel;
            yVel = -yVel;
            facingRight = !facingRight;
            return;
        }

        xVel = 0;
        yVel = 0;
        timer = POST_JUMP_WAIT;
        phase = Phase.POST_JUMP_WAIT;
    }

    private void routinePostJumpWait() {
        if (timer > 0) {
            timer--;
            return;
        }
        animationTick = 0;
        animationIndex = 0;
        phase = Phase.EXIT_RIGHT;
    }

    private void routineExitRight(AbstractPlayableSprite player) {
        currentX += EXIT_SPEED;
        animateLoop(RUN_FRAMES, RUN_DELAY);
        if (isOnScreen(96)) {
            return;
        }

        if (player != null) {
            ObjectControlState.none().applyTo(player);
            player.clearForcedInputMask();
        }
        services().playMusic(Sonic3kMusic.CNZ2.id);
        phase = Phase.FORCE_PLAYER_LEFT;
    }

    private void routineForcePlayerLeft(AbstractPlayableSprite player) {
        if (player == null) {
            setDestroyed(true);
            return;
        }

        player.setControlLocked(true);
        player.setForcedInputMask(AbstractPlayableSprite.INPUT_LEFT);
        Camera camera = services().camera();
        if (camera.getY() + 0x160 < currentY) {
            player.setControlLocked(false);
            player.clearForcedInputMask();
            activeInstance = null;
            setDestroyed(true);
        }
    }

    private void startJump() {
        xVel = JUMP_X_VEL;
        yVel = JUMP_Y_VEL;
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
