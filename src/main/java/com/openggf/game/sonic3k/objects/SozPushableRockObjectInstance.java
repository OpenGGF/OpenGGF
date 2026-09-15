package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.IdentityHashMap;
import java.util.List;

/** SKL $3E, Obj_SOZPushableRock ($40546), shipped FixBugs=0 behavior. */
public final class SozPushableRockObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SpawnRewindRecreatable, RomObjectCodePointerProvider {
    private static final int PUSH = 0, FALL = 1, RIDE = 2, STOP = 3;
    // Native object status pushing bits. The provider callback preserves the bit
    // when SolidObjectFull skips offscreen P2 without returning fresh contact.
    private final FbzParticipantStateTable participants = new FbzParticipantStateTable(1);
    private int xFixed, yFixed;
    private int xVelocity, yVelocity;
    private int phase;
    private int pushTimer;
    private int target;
    private int nextTrackWord;
    private boolean initialized;
    private boolean carryThisPass;

    public SozPushableRockObjectInstance(ObjectSpawn spawn) {
        super(spawn, "SOZPushableRock");
        xFixed = spawn.x() << 16;
        yFixed = spawn.y() << 16;
    }

    @Override public void update(int vIntRunCount, PlayableEntity leader) {
        if (!initialized) {
            try {
                // Subtype bit 7 also publishes the native door link; that coupling
                // remains with the missing SOZDoor owner (documented discrepancy).
                int entry = Sonic3kConstants.SOZ_ROCK_RIDE_INFO_ADDR + ((spawn.subtype() & 0x1F) << 2);
                nextTrackWord = services().rom().read32BitAddr(entry);
                target = readTrackWord();
                nextTrackWord += 2;
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot load SOZ rock track from ROM", e);
            }
            initialized = true;
        }
        // Initial FALL saves d4 after the preceding 1px push; x_vel is still zero.
        // Do not carry that old push from the controller's last checkpoint baseline.
        carryThisPass = (phase == FALL || phase == RIDE) && xVelocity != 0;
        if (phase == PUSH) {
            push(leader);
            return;
        }
        if (phase == FALL) {
            move();
            yVelocity = (short) (yVelocity + 0x38); // MoveSprite uses old Y velocity first.
            var camera = services().camera();
            if (camera != null && (short) (camera.getMaxY() + 0x120) <= (short) getY()) {
                xFixed = (0x7F00 << 16) | (xFixed & 0xFFFF);
                carryThisPass = false; // loc_405D8 also replaces the saved d4 with $7F00.
            }
            // CMP.W/BHS: equality does not finish this falling phase.
            if (getY() > target) {
                yFixed = (target << 16) | (yFixed & 0xFFFF);
                yVelocity = 0;
                int next = readTrackWord();
                if ((short) next < 0) phase = STOP;
                else {
                    nextTrackWord += 2;
                    target = next;
                    phase = RIDE;
                    xVelocity = target >= getX() ? 0x100 : -0x100;
                }
            }
        } else if (phase == RIDE) {
            move();
            // loc_40654 gates sound on Level_frame_counter, not V_int_run_count.
            if ((services().levelManager().getFrameCounter() & 0x1F) == 0)
                services().playSfx(Sonic3kSfx.BLOCK_CONVEYOR.id);
            if (xVelocity >= 0 ? getX() >= target : getX() <= target) {
                int next = readTrackWord();
                if ((short) next < 0) phase = STOP;
                else { nextTrackWord += 2; target = next; phase = FALL; }
            }
        }
        checkpointAll();
    }

    private int readTrackWord() {
        try {
            int value = services().rom().read16BitAddr(nextTrackWord);
            return value;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read SOZ rock track from ROM", e);
        }
    }

    private void move() {
        xFixed += (short) xVelocity << 8;
        yFixed += (short) yVelocity << 8;
    }

    private void push(PlayableEntity leader) {
        var players = services().playerQuery().playersFor(
                ObjectPlayerParticipationPolicy.MAIN_PLUS_ENGINE_SIDEKICKS_AS_NATIVE_P2_EXTENDED);
        if (leader != null) participants.slot(leader);
        var wasPushing = new IdentityHashMap<PlayableEntity, Boolean>();
        for (var player : players) {
            participants.slot(player);
            wasPushing.put(player, player instanceof AbstractPlayableSprite p && p.getPushing());
        }
        checkpointAll();
        // sub_406B4 processes P1 first; only a non-eligible P1 lets P2 act.
        // Extra followers extend P2's independent eligibility in query order.
        for (var player : players) {
            if (!participants.flag(participants.slot(player), 0)
                    || !Boolean.TRUE.equals(wasPushing.get(player))) continue;
            pushTimer = (short) (pushTimer - 1);
            if (pushTimer < 0) {
                pushTimer = 4;
                int direction = getX() >= (player.getCentreX() & 0xFFFF) ? 1 : -1;
                xFixed += direction << 16;
                NativePositionOps.writeXPosPreserveSubpixel(player, player.getCentreX() + direction);
                services().playSfx(Sonic3kSfx.PUSH_BLOCK.id);
                // ObjCheckFloorDist2 samples the trailing edge, y_radius=$B, d5=$C.
                // Its collision path does not follow the focused player's path bits.
                int distance = ObjectTerrainUtils.checkFloorDist(services().levelManager(),
                        services().backgroundPlaneCollisionProvider(), false,
                        (getX() - direction * 0x10) & 0xFFFF, (getY() + 0xB) & 0xFFFF).distance();
                if (distance > 0xE) phase = FALL;
                else yFixed += distance << 16;
            }
            return;
        }
    }

    @Override public void setPlayerPushing(PlayableEntity player, boolean pushing) {
        participants.flag(participants.slot(player), 0, pushing);
    }
    @Override public SolidObjectParams getSolidParams() { return new SolidObjectParams(0x1B, 0xC, 0xD); }
    @Override public SolidExecutionMode solidExecutionMode() { return SolidExecutionMode.MANUAL_CHECKPOINT; }
    @Override public boolean usesInstanceSolidStateLatchKey() { return true; }
    @Override public boolean carriesRiderOnHorizontalMove(PlayableEntity player) { return carryThisPass; }
    @Override public int romObjectCodePointerHighWord() { return 4; }
    @Override public int getX() { return xFixed >>> 16; }
    @Override public int getY() { return yFixed >>> 16; }
    @Override public int getPriorityBucket() { return 5; }
    @Override public int getOnScreenHalfWidth() { return 0x10; }
    @Override public int getOnScreenHalfHeight() { return 0xC; }
    @Override public boolean checksOutOfRangeAfterRoutine() { return true; }
    @Override public boolean usesCustomOutOfRangeCheck() { return true; }
    @Override public boolean isCustomOutOfRange(int cameraX) {
        return isCoarseXOutOfRange(getX(), cameraX, coarseXCullRange());
    }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        var renderer = getRenderer(Sonic3kObjectArtKeys.SOZ_PUSHABLE_ROCK);
        if (renderer != null && renderer.isReady()) renderer.drawFrameIndex(0, getX(), getY(),
                (spawn.renderFlags() & 1) != 0, (spawn.renderFlags() & 2) != 0);
    }
}
