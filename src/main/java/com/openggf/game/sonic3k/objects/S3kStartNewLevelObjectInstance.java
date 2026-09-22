package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.save.SaveReason;
import com.openggf.game.session.ActiveGameplayTeamResolver;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;

import java.util.List;

/** SKL $B3, Obj_StartNewLevel ($863EC): Check_InMyRange and a packed zone/act request. */
public final class S3kStartNewLevelObjectInstance extends AbstractObjectInstance implements SpawnRewindRecreatable {
    private boolean requested;

    public S3kStartNewLevelObjectInstance(ObjectSpawn spawn) {
        super(spawn, "StartNewLevel");
    }

    @Override public void update(int vIntRunCount, PlayableEntity player) {
        if (requested || player == null || !inRange(player)) return;
        requested = true;
        // Obj_StartNewLevel gates only SaveGame on Player_mode=3 and Current_zone=9.
        // Sonic/Tails reaching this placement still take the same transition.
        if (services().currentZone() == Sonic3kZoneIds.ZONE_LRZ
                && ActiveGameplayTeamResolver.resolvePlayerCharacter(services().configuration()) == PlayerCharacter.KNUCKLES) {
            services().requestSessionSave(SaveReason.PROGRESSION_SAVE);
        }
        // loc_86418 reads a WORD at subtype: fresh SST byte $2D is zero. LSR.W #1
        // then ROL.B #1 decode the high byte's low bit into the destination act.
        int packed = spawn.subtype();
        services().requestZoneAndAct(packed >>> 1, packed & 1, true);
    }

    private boolean inRange(PlayableEntity player) {
        // Check_InMyRange uses signed word comparisons and half-open bounds.
        int minX = (short) (spawn.x() - 0x10), maxX = (short) (minX + 0x20);
        int minY = (short) (spawn.y() - 0x80), maxY = (short) (minY + 0x100);
        int x = player.getCentreX(), y = player.getCentreY();
        return x >= minX && x < maxX && y >= minY && y < maxY;
    }

    @Override public boolean isPersistent() { return true; }
    @Override public void appendRenderCommands(List<GLCommand> commands) { }
}
