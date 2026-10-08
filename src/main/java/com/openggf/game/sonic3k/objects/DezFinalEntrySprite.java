package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.runtime.DezFinalBossZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/** DEZ3 entry Robotnik ($80DE0) and the separately allocated body cover ($80D72). */
public final class DezFinalEntrySprite extends AbstractObjectInstance implements RewindRecreatable {
    public static final int ROBOTNIK = 0, COVER = 1;
    private AbstractObjectInstance parent;
    private final SubpixelMotion.State motion = new SubpixelMotion.State(0, 0, 0, 0, 0, 0);
    private int kind;
    private int routine;
    private int timer;
    private int frame;
    private int animationCursor;
    private int animationTimer;

    private DezFinalEntrySprite(ObjectSpawn spawn) {
        super(spawn, "DEZFinalEntrySprite"); kind = spawn.subtype();
        motion.x = spawn.x(); motion.y = spawn.y();
    }
    public static DezFinalEntrySprite robotnik() {
        return new DezFinalEntrySprite(new ObjectSpawn(0, 0, 0, ROBOTNIK, 0, false, 0));
    }
    public static DezFinalEntrySprite cover(AbstractObjectInstance parent) {
        var child = new DezFinalEntrySprite(new ObjectSpawn(parent.getX(), parent.getY(), 0, COVER, 0, false, 0));
        child.parent = parent;
        return child;
    }
    @Override public DezFinalEntrySprite recreateForRewind(RewindRecreateContext context) {
        return new DezFinalEntrySprite(context.spawn());
    }
    private DezFinalBossZoneRuntimeState state() {
        return (DezFinalBossZoneRuntimeState) services().zoneRuntimeState();
    }
    @Override public void update(int vIntRunCount, PlayableEntity player) {
        if (kind == ROBOTNIK) {
            if (routine == 0) { routine = 1; motion.x = 0x70; motion.y = 0xC0; }
            animateRunner();
            motion.x = (motion.x + 6) & 0xFFFF;
            if (motion.x >= 0x3D0) {
                state().bossSignals(state().bossSignals() | 1);
                ObjectLifetimeOps.deleteNoRespawn(this);
            }
        } else {
            if (routine == 0) {
                if (parent == null) return;
                routine = 1; frame = 5;
                motion.x = (parent.getX() - 0x10) & 0xFFFF;
                motion.y = (parent.getY() - 0x3C) & 0xFFFF;
            }
            if (routine == 1) {
                if ((state().bossSignals() & 1) != 0) {
                    routine = 2; timer = 0x1F; motion.xVel = -0x80;
                }
            } else {
                timer = (short) (timer - 1);
                if (timer < 0) {
                    state().bossSignals(state().bossSignals() | 2);
                    // st affects the high byte of this word.
                    state().eventsFg5(state().eventsFg5() | 0xFF00);
                    ObjectLifetimeOps.deleteNoRespawn(this);
                } else SubpixelMotion.moveSprite2(motion);
            }
        }
        updateDynamicSpawn(getX(), getY());
    }
    private void animateRunner() {
        animationTimer = (byte) (animationTimer - 1);
        if (animationTimer >= 0) return;
        try {
            animationCursor = (animationCursor + 1) & 0xFF;
            int next = services().romReader().readU8(0x81355 + animationCursor);
            if (next == 0xFC) {
                animationCursor = 0;
                next = services().romReader().readU8(0x81355);
            }
            frame = next;
            animationTimer = services().romReader().readU8(0x81354);
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
    @Override public int getX() { return motion.x & 0xFFFF; }
    @Override public int getY() { return motion.y & 0xFFFF; }
    @Override public int getPriorityBucket() { return 5; }
    @Override public boolean isHighPriority() { return false; }
    @Override public int getOnScreenHalfWidth() { return 0x20; }
    @Override public int getOnScreenHalfHeight() { return 0x20; }
    @Override public boolean isPersistent() { return true; }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if (routine == 0 || isDestroyed()) return;
        var renderer = getRenderer(kind == ROBOTNIK ? Sonic3kObjectArtKeys.DEZ_ROBOTNIK_RUN
                : Sonic3kObjectArtKeys.DEZ_FINAL_BOSS_MISC);
        if (renderer != null && renderer.isReady())
            renderer.drawFrameIndexForcedPriority(frame, getX(), getY(), kind == ROBOTNIK, false,
                    kind == ROBOTNIK ? 0 : 1, false);
    }
    int frameForTest() { return frame; }
    int fractionForTest() { return motion.xSub; }
}
