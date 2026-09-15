package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.IdentityHashMap;
import java.util.List;

/** SKL $44, Obj_SOZBreakableSandRock ($41702), shipped FixBugs=0 behavior. */
public final class SozBreakableSandRockObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SpawnRewindRecreatable, RomObjectCodePointerProvider {
    private boolean breaking;
    private int mappingFrame;
    private int animationTimer;
    private int x;

    public SozBreakableSandRockObjectInstance(ObjectSpawn spawn) {
        super(spawn, "SOZBreakableSandRock");
        x = spawn.x();
    }

    @Override public void update(int vIntRunCount, PlayableEntity leader) {
        if (!breaking) {
            var players = services().playerQuery().playersFor(
                    ObjectPlayerParticipationPolicy.MAIN_PLUS_ENGINE_SIDEKICKS_AS_NATIVE_P2_EXTENDED);
            // loc_4172E saves anim before SolidObjectFull/ResetOnFloor changes it.
            var rolling = new IdentityHashMap<PlayableEntity, Boolean>();
            for (var player : players) rolling.put(player,
                    player instanceof AbstractPlayableSprite p && p.getAnimationId() == 2);
            checkpointAll();
            var manager = services().objectManager();
            // SolidObjectFull may return no contact for offscreen P2 while
            // retaining this object's standing bit. loc_41760 reads that latch.
            boolean rollLanded = players.stream().anyMatch(p -> Boolean.TRUE.equals(rolling.get(p))
                    && manager != null && manager.hasObjectStandingBit(p, this));
            if (rollLanded) {
                // loc_41776 releases both native riders when either was rolling;
                // only the rolling rider receives the -$300 rebound. Extra riders
                // follow the same independent rider rule.
                for (var player : players) {
                    if (!manager.hasObjectStandingBit(player, this)) continue;
                    manager.releaseRidingObject(player, this);
                    if (Boolean.TRUE.equals(rolling.get(player)) && player instanceof AbstractPlayableSprite p) {
                        // sub_417AC writes status/radii directly, without shifting y_pos.
                        p.setRollingFlagPreserveRadii(true);
                        p.applyCustomRadii(7, 14);
                        p.setAnimationId(2);
                        p.setYSpeed((short) -0x300);
                    }
                    player.setAir(true);
                    player.setOnObject(false);
                    if (player instanceof AbstractPlayableSprite p) p.setHurt(false); // routine = 2
                }
                breaking = true;
                services().playSfx(Sonic3kSfx.BOSS_HIT.id);
            }
        }
        if (breaking) {
            // loc_4180A runs on the breaking pass itself. SUBQ.B/BPL makes the
            // first visible breakup frame immediate, then one frame every six ticks.
            animationTimer = (byte) (animationTimer - 1);
            if (animationTimer < 0) {
                animationTimer = 5;
                mappingFrame++;
                if (mappingFrame >= 5) x = 0x7F00;
            }
        }
    }

    @Override public SolidObjectParams getSolidParams() { return new SolidObjectParams(0x23, 0x10, 0x11); }
    @Override public SolidExecutionMode solidExecutionMode() { return SolidExecutionMode.MANUAL_CHECKPOINT; }
    @Override public boolean isSolidFor(PlayableEntity player) { return !breaking; }
    @Override public boolean usesInstanceSolidStateLatchKey() { return true; }
    @Override public int romObjectCodePointerHighWord() { return 4; }
    @Override public int getX() { return x; }
    @Override public int getPriorityBucket() { return 5; }
    @Override public int getOnScreenHalfWidth() { return 0x18; }
    @Override public int getOnScreenHalfHeight() { return 0x10; }
    @Override public boolean checksOutOfRangeAfterRoutine() { return true; }
    @Override public boolean usesCustomOutOfRangeCheck() { return true; }
    @Override public boolean isCustomOutOfRange(int cameraX) {
        return isCoarseXOutOfRange(x, cameraX, coarseXCullRange());
    }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        var renderer = getRenderer(Sonic3kObjectArtKeys.SOZ_BREAKABLE_SAND_ROCK);
        if (renderer != null && renderer.isReady()) renderer.drawFrameIndex(mappingFrame,
                x, spawn.y(), (spawn.renderFlags() & 1) != 0, (spawn.renderFlags() & 2) != 0);
    }
}
