package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Short regression for the incoming DEZ→DDZ whole-registry restore mismatch. */
class TestDdzChildRecreationSpawn {
    @ParameterizedTest
    @ValueSource(strings = {"BossMasterEmerald", "EndBossBody", "EndBossBomb",
            "EndBossExplosionAnchor", "EndBossLauncher", "EndBossPart", "EndBossRocketFlame",
            "EndBossRocket", "EndBossShipPart", "EndBossTurret", "MissileExhaust"})
    void recreationRetainsSpawnBeforeParentRelinking(String family) throws Exception {
        var type = Class.forName("com.openggf.game.sonic3k.objects.Ddz" + family + "ObjectInstance");
        var constructor = type.getDeclaredConstructor(ObjectSpawn.class);
        constructor.setAccessible(true);
        var spawn = new ObjectSpawn(21193, 160, 0, 0, 0, false, 0);
        var original = (AbstractDdzObjectInstance) constructor.newInstance(spawn);
        var recreated = original.recreateForRewind(new RewindRecreateContext(spawn, null, null));
        assertEquals(spawn, recreated.getSpawn(),
                "the absent parent is a relinking phase, not a new spawn at the origin");
    }
}
