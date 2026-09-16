package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.save.SaveReason;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;

import java.util.List;

/**
 * ROM {@code loc_45B94} (sonic3k.asm:91514-91541): the helper {@code Obj_SSZHPZTeleporter}
 * allocates beside every Hidden Palace teleporter.
 *
 * <p>For Knuckles it ends the act: once the camera is above Y {@code $240} and Player 1 has
 * reached X {@code $B00}, it fades the music, saves and starts SSZ act 2 ({@code $A01}).
 * For Sonic and Tails it deletes itself unless its teleporter is the altar teleporter
 * (X {@code >= $1000}), which the Knuckles fight ending drives.
 */
public final class HpzTeleporterRouteHelperObjectInstance extends AbstractObjectInstance
        implements RewindRecreatable {
    static final int KNUCKLES_EXIT_CAMERA_Y = 0x240;
    static final int KNUCKLES_EXIT_PLAYER_X = 0xB00;
    static final int ALTAR_TELEPORTER_MIN_X = 0x1000;

    private SSZHPZTeleporterObjectInstance teleporter;
    private boolean exitRequested;

    private record RewindExtra(boolean exitRequested, ObjectRefId teleporterId)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {}

    public HpzTeleporterRouteHelperObjectInstance(ObjectSpawn spawn,
                                                  SSZHPZTeleporterObjectInstance teleporter) {
        super(spawn, "HpzTeleporterRouteHelper");
        this.teleporter = teleporter;
    }

    @Override
    public HpzTeleporterRouteHelperObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new HpzTeleporterRouteHelperObjectInstance(ctx.spawn(), null);
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        var registry = services().zoneRuntimeRegistry();
        var hpz = registry == null ? null : S3kRuntimeStates.currentHpz(registry).orElse(null);
        if (hpz != null && hpz.playerCharacter() == PlayerCharacter.KNUCKLES) {
            updateKnucklesExit(player);
            return;
        }
        if (teleporter == null || teleporter.getX() < ALTAR_TELEPORTER_MIN_X) {
            ObjectLifetimeOps.deleteNoRespawn(this);
        }
    }

    private void updateKnucklesExit(PlayableEntity player) {
        if (exitRequested || player == null) {
            return;
        }
        if ((services().camera().getY() & 0xFFFF) >= KNUCKLES_EXIT_CAMERA_Y) {
            return;
        }
        if ((player.getCentreX() & 0xFFFF) < KNUCKLES_EXIT_PLAYER_X) {
            return;
        }
        exitRequested = true;
        // moveq #cmd_FadeOut / SaveGame / move.w #$A01,d0 / StartNewLevel
        services().fadeOutMusic();
        services().requestSessionSave(SaveReason.PROGRESSION_SAVE);
        services().requestZoneAndAct(Sonic3kZoneIds.ZONE_SSZ, 1, true);
    }

    @Override
    public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        ObjectRefId teleporterId = context.identityTable()
                .map(table -> table.encodeObject(teleporter)).orElse(null);
        return super.captureRewindState(context).withObjectSubclassExtra(
                new RewindExtra(exitRequested, teleporterId));
    }

    @Override
    public void restoreRewindState(PerObjectRewindSnapshot snapshot, RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
        if (snapshot.objectSubclassExtra() instanceof RewindExtra extra) {
            exitRequested = extra.exitRequested();
            teleporter = extra.teleporterId() == null ? null
                    : (SSZHPZTeleporterObjectInstance) context.requireIdentityTable()
                    .resolveObject(extra.teleporterId(), true);
        }
    }

    @Override public boolean isPersistent() { return true; }
    @Override public void appendRenderCommands(List<GLCommand> commands) { }
}
