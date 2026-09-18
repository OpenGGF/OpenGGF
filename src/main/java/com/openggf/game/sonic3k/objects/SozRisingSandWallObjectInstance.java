package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.solid.ContactKind;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import java.util.List;

/** SKL $40, Obj_SOZRisingSandWall ($40B16), loc_40B48 through loc_40D0A. */
public final class SozRisingSandWallObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SpawnRewindRecreatable, RomObjectCodePointerProvider {
    private final FbzParticipantStateTable pushing = new FbzParticipantStateTable(1);
    private int phase, riseTimer = 0x19, animationTimer, frame, plumeFrame = 9;
    private int x, y;
    public SozRisingSandWallObjectInstance(ObjectSpawn spawn) {
        super(spawn, "SOZRisingSandWall"); x = spawn.x(); y = spawn.y();
    }
    @Override public void update(int vIntRunCount, PlayableEntity leader) {
        var players = services().playerQuery().playersFor(
                ObjectPlayerParticipationPolicy.MAIN_PLUS_ENGINE_SIDEKICKS_AS_NATIVE_P2_EXTENDED);
        if (phase == 0) {
            boolean debug = leader instanceof AbstractPlayableSprite p && p.isDebugMode();
            for (var p : players) {
                if (!debug && ((p.getCentreX() - x + spawn.subtype()) & 0xFFFF) < spawn.subtype() * 2
                        && ((p.getCentreY() - y + 0xA4) & 0xFFFF) < 0xC0) {
                    phase = 1; animationTimer = 3;
                    services().playSfx(Sonic3kSfx.SANDWALL_RISE.id);
                }
            }
            checkpointAll(); return; // Activation does not execute the rising routine.
        }
        if (phase == 1) {
            if (--animationTimer < 0) { animationTimer = 3; if (++plumeFrame == 13) plumeFrame = 9; }
            y = (y - 4) & 0xFFFF;
            if (--riseTimer < 0) phase = 2;
            checkpointAll(); return;
        }
        if (phase == 2) {
            var contacts = checkpointAll();
            for (var player : players) {
                var contact = contacts.perPlayer().get(player);
                if (contact == null || contact.kind() != ContactKind.SIDE
                        || !(player instanceof AbstractPlayableSprite p) || p.getAnimationId() != 2) continue;
                p.setXSpeed((short) (p.getXSpeed() >> 1));
                p.setGSpeed((short) (p.getGSpeed() >> 1));
                phase = 3; animationTimer = 5; frame = 1;
                services().playSfx(Sonic3kSfx.SAND_SPLASH.id);
                var manager = services().objectManager();
                for (var rider : players) {
                    if (manager != null && manager.hasObjectStandingBit(rider, this)) {
                        manager.releaseRidingObject(rider, this); rider.setOnObject(false); rider.setAir(true);
                    }
                    if (pushing.flag(pushing.slot(rider), 0)) {
                        pushing.flag(pushing.slot(rider), 0, false);
                        if (rider instanceof AbstractPlayableSprite sprite) sprite.setPushing(false);
                    }
                }
                break;
            }
        } else if (--animationTimer < 0) {
            animationTimer = 5; if (++frame == 9) x = 0x7F00;
        }
    }
    @Override public void setPlayerPushing(PlayableEntity player, boolean value) {
        pushing.flag(pushing.slot(player), 0, value);
    }
    @Override public SolidObjectParams getSolidParams() { return new SolidObjectParams(0x17, 0x34, 0x35); }
    @Override public SolidExecutionMode solidExecutionMode() { return SolidExecutionMode.MANUAL_CHECKPOINT; }
    @Override public boolean isSolidFor(PlayableEntity player) { return phase != 3; }
    @Override public boolean usesInstanceSolidStateLatchKey() { return true; }
    @Override public int romObjectCodePointerHighWord() { return 4; }
    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public int getPriorityBucket() { return 5; }
    @Override public int getOnScreenHalfWidth() { return 0xC; }
    @Override public int getOnScreenHalfHeight() { return 0x34; }
    @Override public boolean checksOutOfRangeAfterRoutine() { return true; }
    @Override public boolean usesCustomOutOfRangeCheck() { return true; }
    @Override public boolean isCustomOutOfRange(int cameraX) { return isCoarseXOutOfRange(x, cameraX, coarseXCullRange()); }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        var renderer = getRenderer(Sonic3kObjectArtKeys.SOZ_RISING_SAND_WALL);
        if (renderer == null || !renderer.isReady()) return;
        renderer.drawFrameIndex(frame, x, y, false, false);
        if (phase == 1) renderer.drawFrameIndex(plumeFrame, x, spawn.y(), false, false);
    }
}
