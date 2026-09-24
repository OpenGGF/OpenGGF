package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RomObjectCodePointerProvider;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * SKL {@code $52}, {@code Obj_DEZLightning} (sonic3k.asm:93608-93645).
 *
 * <p>The four visible mappings use {@code Ani_DEZLightning = 1,1,2,3,4,$FC}.
 * Animate_Sprite holds each mapping for delay+1 passes, and {@code $FC} advances the routine.
 * The idle routine then predecrements the subtype word and restarts after it becomes negative.
 * Only mapping 3 calls {@code Sprite_CheckDeleteTouch3}; the other mappings are visual only.
 */
public final class S3kDezLightningObjectInstance extends AbstractObjectInstance
        implements TouchResponseProvider, SpawnRewindRecreatable, RomObjectCodePointerProvider {
    private static final int COLLISION_FLAGS = 0x9F;
    private static final int HAZARDOUS_FRAME = 3;
    private static final int PRIORITY_WORD = 0x280;
    private static final int ROM_CODE_POINTER_HIGH_WORD = 0x0004;

    private int mappingFrame;
    private int animationTimer;
    private int waitCounter;
    private boolean animating;
    private boolean initialized;

    public S3kDezLightningObjectInstance(ObjectSpawn spawn) {
        super(spawn, "DEZLightning");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        if (!initialized) {
            initialized = true;
            startCycle();
            return;
        }
        if (animating) {
            animationTimer--;
            if (animationTimer >= 0) {
                return;
            }
            if (mappingFrame < 4) {
                mappingFrame++;
                animationTimer = 1;
            } else {
                // Animate_Sprite command $FC adds 2 to routine; loc_478E2 then resets the
                // routine and mapping before dispatching the waiting path in the same pass.
                animating = false;
                mappingFrame = 0;
                waitCounter = spawn.subtype() & 0xFF;
                tickWait();
            }
            return;
        }
        tickWait();
    }

    private void tickWait() {
        waitCounter--;
        if (waitCounter < 0) {
            startCycle();
        }
    }

    private void startCycle() {
        animating = true;
        mappingFrame = 1;
        animationTimer = 1;
        if (isOnScreen() && tryServices() != null) {
            services().playSfx(Sonic3kSfx.LIGHTNING.id);
        }
    }

    @Override
    public int getCollisionFlags() {
        return animating && mappingFrame == HAZARDOUS_FRAME ? COLLISION_FLAGS : 0;
    }

    @Override
    public int getCollisionProperty() {
        return 0;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (mappingFrame == 0) {
            return;
        }
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.DEZ_LIGHTNING);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(mappingFrame, getX(), getY(), false, false);
        }
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.fromS3kWord(PRIORITY_WORD);
    }

    @Override
    public int romObjectCodePointerHighWord() {
        return ROM_CODE_POINTER_HIGH_WORD;
    }

    int mappingFrameForTest() {
        return mappingFrame;
    }

    int waitCounterForTest() {
        return waitCounter;
    }

    boolean animatingForTest() {
        return animating;
    }
}
