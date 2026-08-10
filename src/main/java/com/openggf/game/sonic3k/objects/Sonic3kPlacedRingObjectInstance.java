package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kRingAwardService;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.objects.TouchCategory;
import com.openggf.level.objects.TouchResponseListener;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.level.rings.RingManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/** The ordinary six-byte placement-list form of S3K {@code Obj_Ring} ($00). */
public final class Sonic3kPlacedRingObjectInstance extends AbstractObjectInstance
        implements TouchResponseProvider, TouchResponseListener, SpawnRewindRecreatable {
    public static final int COLLISION_FLAGS = 0x47;
    private static final int SPARKLE_FRAME_DELAY = 6; // Ani_RingSparkle delay 5 => 6 ticks/frame
    private static final int SPARKLE_FRAMES = 4;

    private enum State { ACTIVE, SPARKLE }

    private State state = State.ACTIVE;
    private int sparkleTicks;
    private int lastFrameCounter;

    public Sonic3kPlacedRingObjectInstance(ObjectSpawn spawn) {
        super(spawn, "Ring");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        lastFrameCounter = vIntRunCount;
        if (state == State.SPARKLE) {
            if (sparkleTicks >= SPARKLE_FRAMES * SPARKLE_FRAME_DELAY) {
                setDestroyed(true);
            } else {
                sparkleTicks++;
            }
        }
    }

    @Override
    public int getCollisionFlags() {
        return state == State.ACTIVE ? COLLISION_FLAGS : 0;
    }

    @Override
    public int getCollisionProperty() {
        return 0;
    }

    @Override
    public void onTouchResponse(PlayableEntity playerEntity, TouchResponseResult result, int frameCounter) {
        if (state != State.ACTIVE || result.category() != TouchCategory.SPECIAL
                || !(playerEntity instanceof AbstractPlayableSprite player)
                || player.getDead() || player.isTouchResponseSuppressedByObjectControl()) {
            return;
        }
        PlayableEntity main = services().playerQuery().mainPlayerOrNull();
        if (main instanceof AbstractPlayableSprite mainPlayer
                && mainPlayer.getInvulnerableFrames() >= 90) {
            return;
        }
        state = State.SPARKLE;
        sparkleTicks = 0;
        Sonic3kRingAwardService.giveOne(services(), player);
    }

    @Override
    public boolean publishesTouchResponseListEntryThisFrame() {
        return state == State.ACTIVE;
    }

    @Override
    public boolean isPersistent() {
        // Ring_Sparkle uses Draw_Sprite, not Sprite_CheckDeleteTouch3: a
        // collected slot completes its animation even if the camera moves away.
        return state == State.SPARKLE;
    }

    @Override
    public int getOnScreenHalfWidth() {
        return 8;
    }

    @Override
    public int getOnScreenHalfHeight() {
        return 8;
    }

    @Override
    public int getPriorityBucket() {
        return state == State.SPARKLE ? RenderPriority.clamp(2) : RenderPriority.clamp(4);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        RingManager rings = services().ringManager();
        if (rings == null) {
            return;
        }
        if (state == State.ACTIVE) {
            rings.drawRingAt(getX(), getY(), lastFrameCounter);
        } else {
            rings.drawSparkleAt(getX(), getY(), sparkleTicks / SPARKLE_FRAME_DELAY);
        }
    }

    @Override
    public boolean isHighPriority() {
        return true;
    }

    @Override
    public String traceDebugDetails() {
        return "state=" + state + " sparkleTicks=" + sparkleTicks;
    }
}
