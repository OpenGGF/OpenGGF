package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.save.SaveReason;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;

import java.util.List;

/**
 * ROM {@code Obj_StartNewLevel} (sonic3k.asm:181461-181479).
 *
 * <p>The placed object's two-byte {@code subtype} word is transformed by
 * {@code lsr.w #1 / rol.b #1} into the raw zone/act request. LRZ2's only placement has
 * subtype {@code $2D} and a cleared following byte, so {@code $2D00} becomes {@code $1601}
 * (Hidden Palace). The trigger uses {@code Check_InMyRange}'s asymmetric center-coordinate
 * rectangle: X {@code [-$10,+$20)} and Y {@code [-$80,+$100)}.
 */
public final class S3kStartNewLevelObjectInstance extends AbstractObjectInstance
        implements RewindRecreatable {
    private static final int LEFT = -0x10;
    private static final int RIGHT = 0x20;
    private static final int TOP = -0x80;
    private static final int BOTTOM = 0x100;

    private boolean requested;

    private record RewindExtra(boolean requested)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {}

    public S3kStartNewLevelObjectInstance(ObjectSpawn spawn) {
        super(spawn, "StartNewLevel");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (requested || player == null || !inRange(player)) {
            return;
        }
        requested = true;

        var state = S3kRuntimeStates.currentLrz(services().zoneRuntimeRegistry()).orElse(null);
        if (state != null && state.playerCharacter() == PlayerCharacter.KNUCKLES) {
            // Obj_StartNewLevel saves only for Player_mode 3 while Current_zone is LRZ ($09).
            services().requestSessionSave(SaveReason.PROGRESSION_SAVE);
        }

        int subtypeWord = (spawn.subtype() & 0xFF) << 8;
        int rawZoneAndAct = (subtypeWord >>> 1) & 0xFFFF;
        int low = rawZoneAndAct & 0xFF;
        low = ((low << 1) | (low >>> 7)) & 0xFF;
        rawZoneAndAct = (rawZoneAndAct & 0xFF00) | low;
        services().requestZoneAndAct((rawZoneAndAct >>> 8) & 0xFF,
                rawZoneAndAct & 0xFF, true);
    }

    private boolean inRange(PlayableEntity player) {
        int dx = (short) (player.getCentreX() - getX());
        int dy = (short) (player.getCentreY() - getY());
        return dx >= LEFT && dx < RIGHT && dy >= TOP && dy < BOTTOM;
    }

    @Override
    public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        return super.captureRewindState(context)
                .withObjectSubclassExtra(new RewindExtra(requested));
    }

    @Override
    public void restoreRewindState(PerObjectRewindSnapshot snapshot, RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
        if (snapshot.objectSubclassExtra() instanceof RewindExtra extra) {
            requested = extra.requested();
        }
    }

    @Override
    public AbstractObjectInstance recreateForRewind(RewindRecreateContext context) {
        return new S3kStartNewLevelObjectInstance(context.spawn());
    }

    @Override public boolean isPersistent() { return true; }
    @Override public void appendRenderCommands(List<GLCommand> commands) { }

    boolean requestedForTest() { return requested; }
}
