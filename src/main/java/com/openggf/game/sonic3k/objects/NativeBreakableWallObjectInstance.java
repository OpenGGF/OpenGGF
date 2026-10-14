package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;

/** The native AIZ breakable-wall owner with an explicit detached ROM art source. */
public final class NativeBreakableWallObjectInstance extends BreakableWallObjectInstance implements SpawnRewindRecreatable {
    public NativeBreakableWallObjectInstance(ObjectSpawn spawn) { super(spawn, Sonic3kZoneIds.ZONE_AIZ); }
}
