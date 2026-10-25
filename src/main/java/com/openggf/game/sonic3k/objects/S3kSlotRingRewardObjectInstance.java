package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.mutators.LevelMutatorPolicyAccess;
import com.openggf.audio.GameSound;
import com.openggf.game.sonic3k.bonusstage.slots.S3kSlotStageController;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;

import java.util.List;

/**
 * Slot-machine ring reward child.
 *
 * <p>ROM reference: {@code Obj_SlotRing}. The object interpolates its position toward the cage
 * center each frame (1/16th of remaining distance), and when its timer expires it awards one
 * bonus-stage ring, plays the retained-cursor sparkle script, then deletes itself.
 */
public final class S3kSlotRingRewardObjectInstance extends AbstractObjectInstance implements RewindRecreatable {

    private static final int EXPIRY_FRAMES = 0x1A;
    private final S3kSlotStageController controller;
    private int framesRemaining = EXPIRY_FRAMES;
    private boolean active;
    private boolean inSparkle;
    private final byte[] spinScript;
    private final byte[] sparkleScript;
    private int animationFrame;
    private int animationTimer;
    private int mappingFrame;
    private boolean deleteNextUpdate;

    // ROM $34/$38: 32-bit fixed-point position (pixel:16 | sub:16)
    private int currentX32;
    private int currentY32;
    // ROM $3C/$3E: target pixel position (cage center)
    private int targetX;
    private int targetY;
    private boolean suppressObjectManagerUpdate;

    public S3kSlotRingRewardObjectInstance(ObjectSpawn spawn, S3kSlotStageController controller) {
        super(spawn, "S3kSlotRingReward");
        this.controller = controller;
        spinScript = controller == null ? null : controller.rewardRingAnimation(false);
        sparkleScript = controller == null ? null : controller.rewardRingAnimation(true);
    }

    private S3kSlotRingRewardObjectInstance(ObjectSpawn spawn) {
        this(spawn, null);
    }

    @Override
    public AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        S3kSlotStageController controller =
                S3kSlotRewindSupport.resolveSlotStageController(ctx.objectServices());
        return controller != null ? new S3kSlotRingRewardObjectInstance(ctx.spawn(), controller) : null;
    }

    /** Activates without position tracking (backward-compatible; position stays at spawn). */
    public void activate() {
        activate(spawn.x(), spawn.y(), spawn.x(), spawn.y());
    }

    /**
     * Activates with interpolated movement toward the cage center.
     *
     * <p>ROM reference: {@code Obj_SlotRing} — spawned at a radial offset, converges on
     * {@code (centerX, centerY)} at 1/16th of the remaining distance each frame.
     */
    public void activate(int spawnX, int spawnY, int centerX, int centerY) {
        active = true;
        framesRemaining = EXPIRY_FRAMES;
        inSparkle = false;
        animationFrame = 0;
        animationTimer = 0;
        mappingFrame = 0;
        deleteNextUpdate = false;
        setDestroyed(false);
        this.currentX32 = spawnX << 16;
        this.currentY32 = spawnY << 16;
        this.targetX = centerX;
        this.targetY = centerY;
    }

    public boolean isActive() {
        return active;
    }

    /**
     * Returns the current interpolated X position (pixel units).
     *
     * <p>Only meaningful while active.
     */
    public int getInterpolatedX() {
        return currentX32 >>> 16;
    }

    /**
     * Returns the current interpolated Y position (pixel units).
     *
     * <p>Only meaningful while active.
     */
    public int getInterpolatedY() {
        return currentY32 >>> 16;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        if (suppressObjectManagerUpdate) {
            return;
        }
        tickSlotRuntime(vIntRunCount, playerEntity);
    }

    public void tickSlotRuntime(int frameCounter, PlayableEntity playerEntity) {
        if (isDestroyed() || !active) {
            return;
        }
        // loc_1AA62 deletes on the pass AFTER Animate_Sprite consumes $FC.
        if (deleteNextUpdate) {
            setDestroyed(true);
            active = false;
            return;
        }
        if (inSparkle) {
            animate(sparkleScript);
            return;
        }

        // loc_1AA02 (sonic3k.asm:35898-35919): SUB.L/ASR.L/SUB.L.
        // Keep signed 32-bit wrap and rounding; reversing the subtraction loses low bits.
        currentX32 -= (currentX32 - (targetX << 16)) >> 4;
        currentY32 -= (currentY32 - (targetY << 16)) >> 4;
        animate(spinScript);

        if (--framesRemaining > 0) {
            return;
        }

        if (!LevelMutatorPolicyAccess.ringsAllowed(services())) {
            controller.onRewardExpired();
            active = false;
            setDestroyed(true);
            return;
        }

        // Grant ring and enter sparkle phase (ROM routine 1)
        controller.addRewardRing();
        addLiveRings(playerEntity, 1);
        services().addBonusStageRings(1);
        services().playSfx(GameSound.RING);
        // loc_1AA02 (sonic3k.asm:35923-35927): retire the cage counter at grant,
        // then fall through to loc_1AA56 without resetting anim_frame/timer.
        controller.onRewardExpired();
        inSparkle = true;
        animate(sparkleScript);
    }

    /** Animate_Sprite (sonic3k.asm:36197-36279), animation id remains zero. */
    private void animate(byte[] script) {
        if (--animationTimer >= 0) {
            return;
        }
        animationTimer = script[0] & 0xFF;
        int command = script[1 + animationFrame] & 0xFF;
        if (command == 0xFF) {
            animationFrame = 0;
            command = script[1] & 0xFF;
        } else if (command == 0xFC) {
            deleteNextUpdate = true;
            animationTimer = 0;
            animationFrame++;
            return;
        }
        mappingFrame = command;
        animationFrame++;
    }

    public void suppressObjectManagerUpdate() {
        suppressObjectManagerUpdate = true;
    }

    /** Returns true while in sparkle animation phase (ROM routine 1). */
    public boolean isInSparkle() {
        return inSparkle;
    }

    private void addLiveRings(PlayableEntity playerEntity, int amount) {
        if (playerEntity instanceof com.openggf.sprites.playable.AbstractPlayableSprite sprite) {
            sprite.addRings(amount);
            return;
        }
        if (services().levelGamestate() != null) {
            services().levelGamestate().addRings(amount);
        }
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.fromS3kWord(0x180); // loc_4C172: prize ring bucket 3
    }

    @Override
    public boolean isHighPriority() {
        return false; // loc_4C172: make_art_tile(ArtTile_Ring,1,0)
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!active || isDestroyed()) {
            return;
        }
        if (services().ringManager() == null) {
            return;
        }
        if (mappingFrame < 4) {
            services().ringManager().drawRingFrameAt(getInterpolatedX(), getInterpolatedY(), mappingFrame);
        } else {
            services().ringManager().drawSparkleAt(getInterpolatedX(), getInterpolatedY(), mappingFrame - 4);
        }
    }
}
