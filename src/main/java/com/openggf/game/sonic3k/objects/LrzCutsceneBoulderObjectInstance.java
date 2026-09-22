package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.*;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.ObjectControlState;
import java.util.List;

/** loc_63C3E: pushed boulder, light-gravity bounce, and sub_65EFE's orbiting riders. */
public final class LrzCutsceneBoulderObjectInstance extends AbstractObjectInstance implements ZeroArgRewindRecreatable {
    private final SubpixelMotion.State motion = new SubpixelMotion.State(0x3A08, 0xE2, 0, 0, 0, 0);
    private int routine, frame, timer;
    private int[] angles = new int[0];
    private boolean[] captured = new boolean[0];

    public LrzCutsceneBoulderObjectInstance() {
        super(new ObjectSpawn(0x3A08, 0xE2, 0, 0, 0, false, 0), "LRZCutsceneBoulder");
    }

    @Override public void update(int vIntRunCount, PlayableEntity player) {
        var runtime = S3kRuntimeStates.currentLrz(services().zoneRuntimeRegistry()).orElseThrow();
        if (routine == 0) { routine = 2; return; }
        if (routine == 2) {
            if ((runtime.cutsceneFlags() & 4) != 0) { routine = 4; motion.xVel = -0x200; }
            return;
        }
        // Play_SFX_Continuous gates on V_int_run_count, not Level_frame_counter.
        if ((vIntRunCount & 0xF) == 0) services().playSfx(Sonic3kSfx.PUSH_BLOCK.id);
        if ((byte) --timer < 0) { timer = 4; frame = (frame + 1) & 0xFF; }
        captureAndPositionPlayers(); // native positions riders before moving their boulder
        if (routine == 4) {
            SubpixelMotion.speedToPos(motion);
            if (floorDistance() >= 0) routine = 6;
        } else {
            SubpixelMotion.objectFallXY(motion, 0x20); // MoveSprite_LightGravity
            if ((short) motion.yVel >= 0 && floorDistance() < 0) {
                motion.yVel = -0x100;
                runtime.screenShake().writeFlag(0x14);
                services().playSfx(Sonic3kSfx.COLLAPSE.id);
            }
        }
    }

    private int floorDistance() {
        return ObjectTerrainUtils.checkFloorDist(services().levelManager(), getX(), getY() + 0x1F).distance();
    }

    private void captureAndPositionPlayers() {
        var players = services().playerQuery().playersFor(ObjectPlayerParticipationPolicy.ALL_ENGINE_PLAYERS);
        if (angles.length < players.size()) {
            angles = java.util.Arrays.copyOf(angles, players.size());
            captured = java.util.Arrays.copyOf(captured, players.size());
        }
        for (int i = 0; i < players.size(); i++) {
            if (!(players.get(i) instanceof AbstractPlayableSprite player)) continue;
            if (!captured[i]) {
                int dx = (short) (player.getCentreX() - getX()), dy = (short) (player.getCentreY() - getY());
                if (dx < -0x20 || dx >= 0x20 || dy < -0x20 || dy >= 0x20) continue;
                captured[i] = true;
                angles[i] = S3kNativeObjectAngle.angleTowards(getX(), getY(), player.getCentreX(), player.getCentreY());
                services().camera().setScrollLocked(false);
                services().levelManager().getLevelGamestate().pauseTimer();
                ObjectControlState.nativeBit7FullControl().applyTo(player); // object_control=$83
                player.setObjectMappingFrameControl(true);
                player.setAnimationId(0);
                player.setMappingFrame(i == 0 ? 0x8D : 0x8A);
                player.setAnimationTick(0); player.setAnimationFrameIndex(0);
            }
        }
        for (int i = 0; i < players.size(); i++) {
            if (!captured[i] || !(players.get(i) instanceof AbstractPlayableSprite player)) continue;
            int tick = (player.getAnimationTick() - 1) & 0xFF;
            if ((byte) tick < 0) {
                tick = 8;
                int flip = player.getAnimationFrameIndex();
                player.setRenderFlips((flip & 1) != 0, (flip & 2) != 0);
                player.setAnimationFrameIndex((flip + 1) & 0xFF);
            }
            player.setAnimationTick(tick);
            int angle = angles[i]; angles[i] = (angle + 2) & 0xFF;
            NativePositionOps.writeXPosPreserveSubpixel(player, getX() + (TrigLookupTable.sinHex(angle) >> 3));
            NativePositionOps.writeYPosPreserveSubpixel(player, getY() + (TrigLookupTable.cosHex(angle) >> 3));
        }
    }

    @Override public int getX() { return motion.x; }
    @Override public int getY() { return motion.y; }
    @Override public boolean isPersistent() { return true; }
    @Override public int getPriorityBucket() { return RenderPriority.fromS3kWord(0x200); }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        var renderer = getRenderer(Sonic3kObjectArtKeys.LRZ_CUTSCENE_BOULDER);
        if (renderer != null && renderer.isReady()) renderer.drawFrameIndex(0, getX(), getY(), (frame & 1) != 0, (frame & 2) != 0);
    }
}
