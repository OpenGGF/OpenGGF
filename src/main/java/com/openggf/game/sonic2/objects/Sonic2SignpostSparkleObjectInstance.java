package com.openggf.game.sonic2.objects;

import com.openggf.game.sonic2.competition.view.Sonic2NativeDisplaySource;
import com.openggf.game.sonic2.competition.view.Sonic2CompetitionDisplayInputs.*;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.SignpostSparkleObjectInstance;

/** loc_19398 (s2.asm:34772-34799): S2's real Obj25 sparkle SST; shared animation stays unchanged. */
public final class Sonic2SignpostSparkleObjectInstance extends SignpostSparkleObjectInstance
        implements Sonic2NativeDisplaySource, com.openggf.level.objects.SpawnRewindRecreatable,
        com.openggf.level.objects.ObjectVisualFacing.NonDirectional {
    public Sonic2SignpostSparkleObjectInstance(ObjectSpawn spawn) {
        super(spawn);
        if(spawn.objectId()!=0x25) throw new IllegalArgumentException("S2 sparkle must own native ID25");
    }
    @Override public Sonic2SignpostSparkleObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new Sonic2SignpostSparkleObjectInstance(ctx.spawn());
    }
    @Override public ObjectSlot nativeDisplaySlot() {
        if(isDestroyed()) return null;
        return new ObjectSlot(0x25,4,getX()&0xFFFF,getY()&0xFFFF,8,0,
                new Mapping(0x12382,displayedMappingFrame()),0x235E,null);
    }
}
