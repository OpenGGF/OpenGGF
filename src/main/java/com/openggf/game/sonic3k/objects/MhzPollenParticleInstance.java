package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.runtime.MhzZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.SubpixelMotion;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.TrigLookupTable;

import java.util.List;

/**
 * MHZ pollen / falling leaf particle.
 *
 * <p>ROM: {@code Obj_MHZ_Pollen}, {@code loc_3DBE0}, {@code loc_3DC18}.
 */
public class MhzPollenParticleInstance extends AbstractObjectInstance implements RewindRecreatable {
    public enum ArtMode {
        POLLEN,
        BIG_LEAF
    }

    private enum Routine {
        RISING,
        FLOATING
    }

    private final SubpixelMotion.State motion;
    // Non-final so the generic field capturer reapplies them after a rewind
    // recreate (not spawn-derivable: the stored spawn carries only x/y).
    private ArtMode artMode;
    private int gravityStep;
    private boolean preserveInitialAngleOnFloat;
    private Routine routine;
    private int angle;
    private int mappingFrame;
    private int animFrameTimer;
    private boolean releasedCounter;
    private boolean renderFlagOnScreen = true;

    MhzPollenParticleInstance() {
        this(0, 0, 0, 0, 0, 0, ArtMode.POLLEN);
    }

    public MhzPollenParticleInstance(
            int x,
            int y,
            int xVelocity,
            int yVelocity,
            int gravityStep,
            int angle,
            ArtMode artMode) {
        this(x, y, xVelocity, yVelocity, gravityStep, angle, artMode, false);
    }

    public MhzPollenParticleInstance(
            int x,
            int y,
            int xVelocity,
            int yVelocity,
            int gravityStep,
            int angle,
            ArtMode artMode,
            boolean preserveInitialAngleOnFloat) {
        super(new ObjectSpawn(x, y, 0, 0, 0, false, 0), "MHZ Pollen");
        this.motion = new SubpixelMotion.State(x, y, 0, 0, xVelocity, yVelocity);
        this.gravityStep = gravityStep;
        this.angle = angle & 0xFF;
        this.artMode = artMode;
        this.preserveInitialAngleOnFloat = preserveInitialAngleOnFloat;
        this.routine = Routine.RISING;
        this.animFrameTimer = 0;
    }

    @Override
    public AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        ObjectSpawn spawn = ctx.spawn();
        return new MhzPollenParticleInstance(
                spawn.x(), spawn.y(), 0, 0, 0, 0, ArtMode.POLLEN);
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (isDestroyed()) {
            return;
        }

        boolean enteredFloatingRoutine = routine == Routine.FLOATING;
        // loc_3DBE0 consumes the render_flags bit left by the preceding render
        // pass. Keep it latched separately from the bounds calculated for the
        // next pass; recomputing it at routine entry culls one frame early and
        // opens three extra pollen RNG gates before the first MHZ Madmole.
        boolean wasRenderFlagOnScreen = renderFlagOnScreen;
        if (!enteredFloatingRoutine) {
            SubpixelMotion.moveSprite2(motion);
            if (motion.yVel < 0) {
                motion.yVel += gravityStep << 2;
            }
            if (motion.yVel >= 0) {
                routine = Routine.FLOATING;
                if (!preserveInitialAngleOnFloat) {
                    angle = (resolveLevelFrameCounter(vIntRunCount) + 1) & 0xFF;
                }
            }
        } else {
            motion.xVel = TrigLookupTable.sinHex(angle);
            angle = (angle + 4) & 0xFF;
            SubpixelMotion.moveSprite2(motion);
            motion.yVel += gravityStep;
        }

        animateMappingFrame();
        if (enteredFloatingRoutine && !wasRenderFlagOnScreen) {
            cleanupOffscreen();
        }
    }

    @Override
    public void refreshPostCameraRenderState() {
        // Render_Sprites runs after the camera and object pass, setting
        // render_flags bit 7 for loc_3DBE0 to consume on the next dispatch.
        // Sampling here is essential when the camera moves vertically during
        // the same frame (sonic3k.asm:36347-36365, 81767-81805).
        renderFlagOnScreen = isWithinRenderSpriteBounds(4, 4);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(resolveArtKey());
        if (renderer != null) {
            renderer.drawFrameIndex(mappingFrame, getX(), getY(), false, false);
        }
    }

    @Override
    public int getX() {
        return motion.x;
    }

    @Override
    public int getY() {
        return motion.y;
    }

    @Override
    public int getOnScreenHalfWidth() {
        return 4;
    }

    @Override
    public int getOnScreenHalfHeight() {
        return 4;
    }

    public ArtMode getArtMode() {
        return artMode;
    }

    public int getVelocityX() {
        return motion.xVel;
    }

    public int getVelocityY() {
        return motion.yVel;
    }

    public int getGravityStep() {
        return gravityStep;
    }

    public int getMappingFrame() {
        return mappingFrame;
    }

    private void animateMappingFrame() {
        animFrameTimer--;
        if (animFrameTimer >= 0) {
            return;
        }
        animFrameTimer = 7;
        mappingFrame = (mappingFrame + 1) & 1;
        if (motion.xVel < 0) {
            mappingFrame += 2;
        }
    }

    private String resolveArtKey() {
        if (artMode == ArtMode.BIG_LEAF) {
            return Sonic3kObjectArtKeys.MHZ_BIG_LEAVES;
        }
        if (services().zoneRuntimeState() instanceof MhzZoneRuntimeState state
                && state.isSeasonFlagSet()) {
            return Sonic3kObjectArtKeys.MHZ_POLLEN_SEASONAL;
        }
        return Sonic3kObjectArtKeys.MHZ_POLLEN_SPRING;
    }

    private void cleanupOffscreen() {
        motion.x = 0x7F00;
        releaseRuntimeCounter();
        setDestroyedByOffscreen();
    }

    private int resolveLevelFrameCounter(int fallback) {
        return services().levelManager() != null
                ? services().levelManager().getFrameCounter()
                : fallback;
    }

    private void releaseRuntimeCounter() {
        if (releasedCounter) {
            return;
        }
        if (services().zoneRuntimeState() instanceof MhzZoneRuntimeState state) {
            state.releasePollenParticle();
        }
        releasedCounter = true;
    }
}
