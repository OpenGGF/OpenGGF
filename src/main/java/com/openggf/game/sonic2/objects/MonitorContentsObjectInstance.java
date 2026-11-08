package com.openggf.game.sonic2.objects;

import com.openggf.game.sonic2.competition.view.Sonic2NativeDisplaySource;
import com.openggf.game.sonic2.competition.view.Sonic2CompetitionDisplayInputs.*;

import com.openggf.level.objects.ObjectVisualFacing;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractMonitorObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnNullableReferenceRewindRecreatable;

import java.util.List;

/**
 * Sonic 2 monitor contents object (ROM Obj2E).
 * <p>
 * The broken monitor shell remains Obj26. This dynamic object owns the rising
 * icon, power-up timing, and final self-delete just like the ROM's separate SST
 * allocation from Obj26_Break.
 */
public final class MonitorContentsObjectInstance extends AbstractMonitorObjectInstance
        implements Sonic2NativeDisplaySource, SpawnNullableReferenceRewindRecreatable, ObjectVisualFacing.NonDirectional {
    // Obj2E raises a reward icon vertically; the portrait/icon is not a facing body.

    private static final int ICON_FRAME_OFFSET = 1;

    private int subtype;
    private boolean contentsInitialized;

    public MonitorContentsObjectInstance(int x, int y, int subtype, PlayableEntity player) {
        this(new ObjectSpawn(x, y, Sonic2ObjectIds.MONITOR_CONTENTS, subtype, 0, false, 0), player);
    }

    public MonitorContentsObjectInstance(ObjectSpawn spawn, PlayableEntity player) {
        super(spawn, "MonitorContents");
        this.subtype = spawn.subtype() & 0x0F;
        startIconRise(spawn.y(), player);
    }

    private MonitorContentsObjectInstance(ObjectSpawn spawn) {
        this(spawn, null);
    }

    void delayFirstIconUpdateForPassedSlot() {
        iconPendingInit = true;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        updateDynamicSpawn(spawn.x(), iconSubY >> 8);
        if (iconPendingInit) { iconPendingInit = false; return; }
        if (!contentsInitialized) {
            var policy = services().runPolicy();
            if (policy.monitors() == com.openggf.game.arena.MonitorRule.OFF) {
                ObjectLifetimeOps.deleteNoRespawn(this);
                return;
            }
            if (usesHostedRandomContents(policy.monitors())) {
                var level = services().levelManager();
                subtype = selectHostedContents(policy.monitors(),level.getFrameCounter(),
                        !level.getLevelGamestate().isTimerPaused(),policy.swapEnabled());
            }
            contentsInitialized = true;
        }
        updateIcon();
    }

    static boolean usesHostedRandomContents(com.openggf.game.arena.MonitorRule rule) {
        return rule == com.openggf.game.arena.MonitorRule.RANDOMISED
                || rule == com.openggf.game.arena.MonitorRule.COMPETITION_ITEMS;
    }

    static int selectHostedContents(com.openggf.game.arena.MonitorRule rule,int levelFrame,
            boolean timerRunning,boolean swapEnabled) {
        // Obj2E_Init (s2.asm:25755-25776) samples Level_frame_counter at this SST's init,
        // rather than V_int_run_count or the frame when its parent shell broke.
        int content = switch (rule) {
            case RANDOMISED -> (levelFrame & 7) + 1;
            // D22 invention: use bit 0 for teleport/Robotnik, based on the ROM timer draw.
            // Robotnik's membership and this one-bit set are room rules, not shipped S2.
            case COMPETITION_ITEMS -> (levelFrame & 1) != 0 ? 8 : 3;
            default -> throw new IllegalArgumentException("No hosted content draw for " + rule);
        };
        // Obj2E_Init's two timer flags must both be one. The frozen launch flag stands
        // in for the partner's flag; live roster changes cannot affect this draw.
        return content == 8 && (!timerRunning || !swapEnabled) ? 7 : content;
    }

    int selectedSubtype() { return subtype; }

    @Override
    protected void applyPowerup(PlayableEntity player) {
        MonitorObjectInstance.applyMonitorPowerup(subtype, player, services());
    }

    @Override
    protected void onIconDeactivated() {
        ObjectLifetimeOps.expireDynamic(this);
    }

    // Obj2E_Init move.b #3,priority(a0): docs/s2disasm/s2.asm:25750.
    private static final int PRIORITY_BUCKET = RenderPriority.bucket(3);

    @Override
    public int getPriorityBucket() {
        return PRIORITY_BUCKET;
    }

    @Override
    public boolean isHighPriority() {
        // make_art_tile(ArtTile_ArtNem_Powerups,0,1) sets art-word bit 15: docs/s2disasm/s2.asm:25747.
        return true;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed() || !iconActive) {
            return;
        }
        drawMonitorIcon(subtype + ICON_FRAME_OFFSET, spawn.x(), iconSubY >> 8);
    }

    // Obj2E_Init (s2.asm:25745-25774): first piece of the selected monitor frame, static_mappings.
    @Override public ObjectSlot nativeDisplaySlot() {
        if(isDestroyed() || !iconActive) return null;
        try {
            int pointer=0x12D36+services().romReader().readS16BE(0x12D36+(subtype+1)*2)+2;
            return new ObjectSlot(0x2E,0x24,spawn.x()&0xFFFF,(iconSubY>>8)&0xFFFF,8,0,
                    new Mapping(pointer,0),0x8340,null);
        } catch(java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
    }

}
