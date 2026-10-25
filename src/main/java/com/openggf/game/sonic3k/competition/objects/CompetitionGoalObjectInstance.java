package com.openggf.game.sonic3k.competition.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.session.WorldSessionPolicyAccess;
import com.openggf.game.sonic3k.competition.S3kCompetitionSession;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.NativeSpriteSubmissionAccess;
import com.openggf.level.objects.NativeSpriteSubmissionSource;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.SpawnRewindRecreatable;

import java.util.List;

/**
 * S3KL $65, Obj_2PGoalMarker (sonic3k.asm:73992-74247).
 * The marker draws unflipped; its independently allocated HUD records retain
 * their native screen positions and have no shared horizontal body direction.
 */
public final class CompetitionGoalObjectInstance extends AbstractObjectInstance implements SpawnRewindRecreatable, NativeSpriteSubmissionSource, ObjectVisualFacing.NonDirectional {
    public static final String ART_KEY = "competition_goal";
    private int animationFrame;
    private int animationTimer;
    private int mappingFrame;
    private boolean initialized;
    private boolean drawPublished;

    public CompetitionGoalObjectInstance(ObjectSpawn spawn) { super(spawn, "CompetitionGoalMarker"); }

    @Override public void update(int vIntRunCount, PlayableEntity ignored) {
        S3kCompetitionSession session = WorldSessionPolicyAccess.getService(
                services().worldSession(), S3kCompetitionSession.class);
        if (session != null) {
            updateNative(session);
            if (drawPublished) NativeSpriteSubmissionAccess.submitSpilling(
                    services().objectManager(), getSlotIndex(), getPriorityBucket());
        }
    }

    public void updateNative(S3kCompetitionSession session) {
        if (isDestroyed()) return;
        drawPublished = false;
        if (!initialized) {
            initialized = true;
            session.beginGoalHud();
            // Obj_2PGoalMarker falls through after the first failed allocation.
            // Every call scans after the current GOAL SST, not after the last child.
            var kinds = new CompetitionHudObjectInstance.Kind[]{
                    CompetitionHudObjectInstance.Kind.ITEM_ICON, CompetitionHudObjectInstance.Kind.ITEM_ICON,
                    CompetitionHudObjectInstance.Kind.LAP, CompetitionHudObjectInstance.Kind.LAP,
                    CompetitionHudObjectInstance.Kind.NEON,
                    CompetitionHudObjectInstance.Kind.TIMER, CompetitionHudObjectInstance.Kind.TIMER,
                    CompetitionHudObjectInstance.Kind.RESERVED};
            int[] slots = {0, 1, 0, 1, 0, 0, 1, 0};
            for (int i = 0; i < kinds.length; i++) {
                var kind = kinds[i];
                int slot = slots[i];
                if (session.allocateAfter(this, () -> new CompetitionHudObjectInstance(kind, slot)) == null) break;
                if (kind == CompetitionHudObjectInstance.Kind.TIMER) session.installGoalTimer(slot);
            }
        }
        if (!session.tickGoalHold()) session.observeGoal(getX(), getY(), spawn.subtype(), this);
        // Both goal routines always reach Draw_Sprite, even when Animate_Sprite keeps its frame.
        drawPublished = true;
        // Animate_Sprite reads the ROM script, including its native six-tick cadence.
        animationTimer = (animationTimer - 1) & 0xFF;
        if ((byte) animationTimer >= 0) return;
        int script = (session.goalAnimationByte(0) << 8) | session.goalAnimationByte(1);
        animationTimer = session.goalAnimationByte(script);
        int next = session.goalAnimationByte(script + 1 + animationFrame);
        if (next == 0xFF) { animationFrame = 0; next = session.goalAnimationByte(script + 1); }
        mappingFrame = next;
        animationFrame = (animationFrame + 1) & 0xFF;
    }

    public int mappingFrame() { return mappingFrame; }
    public boolean nativeDrawPublished() { return drawPublished; }
    @Override public int getOnScreenHalfWidth() { return 8; }
    @Override public int getOnScreenHalfHeight() { return 0x20; }
    @Override public boolean isPersistent() { return true; } // Native has no out-of-range tail.
    @Override public boolean isHighPriority() { return false; }
    @Override public int getPriorityBucket() {
        return RenderPriority.fromS3kWord(0x280); // Obj_2PGoalMarker's native priority word.
    }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if (!drawPublished || isDestroyed()) return;
        var renderer = getRenderer(ART_KEY);
        if (renderer != null && renderer.isReady()) renderer.drawFrameIndex(mappingFrame, getX(), getY(), false, false);
    }
}
