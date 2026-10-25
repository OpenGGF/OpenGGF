package com.openggf.game.sonic2.competition;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractMonitorObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;

import java.util.List;

/** Native competition Obj2E; contents are latched on the first actual SST execution. */
public final class Sonic2CompetitionMonitorContents extends AbstractMonitorObjectInstance
        implements RewindRecreatable {
    private int breakerSlot;
    private int content;
    private boolean contentsInitialized;
    private int vIntRunCount;

    public Sonic2CompetitionMonitorContents(int x, int y, int breakerSlot, PlayableEntity breaker) {
        this(new ObjectSpawn(x, y, Sonic2ObjectIds.MONITOR_CONTENTS, 9, 0, false, 0), breakerSlot, breaker);
    }

    private Sonic2CompetitionMonitorContents(ObjectSpawn spawn, int breakerSlot, PlayableEntity breaker) {
        super(spawn, "CompetitionMonitorContents");
        this.breakerSlot = breakerSlot;
        startIconRise(spawn.y(), breaker);
    }

    private Sonic2CompetitionMonitorContents(ObjectSpawn spawn) { this(spawn, 0, null); }

    public void delayFirstIconUpdateForPassedSlot() { iconPendingInit = true; }

    @Override public void update(int vIntRunCount, PlayableEntity ignoredObjectPassPlayer) {
        this.vIntRunCount = vIntRunCount;
        if (iconPendingInit) {
            iconPendingInit = false;
            return;
        }
        var session = Sonic2CompetitionObjectAccess.session(services());
        if (session == null) throw new IllegalStateException("Native monitor session is unavailable");
        if (!contentsInitialized) {
            // Obj2E_Init (s2.asm:25755-25776) reads Level_frame_counter, never V_int_run_count.
            content = session.monitorContents(services().levelManager().getFrameCounter(),
                    Sonic2CompetitionObjectAccess.host(services()).teleportOnly());
            contentsInitialized = true;
        }
        // The stable human slot survives removal/recreation of the original parent actor.
        if (!effectApplied) effectTarget = session.player(breakerSlot);
        updateDynamicSpawn(spawn.x(), iconSubY >> 8);
        updateIcon();
    }

    @Override protected void applyPowerup(PlayableEntity player) {
        Sonic2CompetitionObjectAccess.awardMonitor(services(), breakerSlot, content, spawn.x(), vIntRunCount);
    }

    @Override protected void onIconDeactivated() { ObjectLifetimeOps.expireDynamic(this); }
    @Override public int getPriorityBucket() { return RenderPriority.bucket(3); }
    @Override public boolean isHighPriority() { return true; }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if (!isDestroyed() && iconActive && contentsInitialized)
            drawMonitorIcon(content + 1, spawn.x(), iconSubY >> 8);
    }

    @Override public Sonic2CompetitionMonitorContents recreateForRewind(RewindRecreateContext ctx) {
        return new Sonic2CompetitionMonitorContents(ctx.spawn(), 0, null);
    }
}
