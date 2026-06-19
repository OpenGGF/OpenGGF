package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.DynamicObjectRewindCodec;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link Sonic3kObjectRegistry} exposes a dynamic rewind codec
 * for every AIZ2 battleship / boss-endgame object that must survive a rewind
 * keyframe restore, including the transient combat/cosmetic children that were
 * previously dropped.
 *
 * <p>Held rewind restores the nearest keyframe and re-simulates forward each
 * displayed frame, so any object dropped on restore is re-emitted from scratch
 * and visibly plays forward. To make the whole scene reverse cleanly, every AIZ2
 * battleship/boss child is now captured and recreated — there are no longer any
 * intentionally-dropped AIZ2 transients.
 *
 * <p>This is a pure registry-content test: it constructs a registry and reads
 * {@code dynamicRewindCodecs()} without a ROM, OpenGL, or an active gameplay
 * session.
 */
class TestAiz2ObjectRewindCodecs {

    private static Set<String> codecClassNames() {
        List<DynamicObjectRewindCodec> codecs = new Sonic3kObjectRegistry().dynamicRewindCodecs();
        Set<String> names = new HashSet<>();
        for (DynamicObjectRewindCodec codec : codecs) {
            names.add(codec.className());
        }
        return names;
    }

    @Test
    void registersCodecsForAiz2RecreatableObjects() {
        Set<String> names = codecClassNames();
        // Tier 1: self-contained objects.
        assertTrue(names.contains(AizBgTreeSpawnerInstance.class.getName()),
                "missing codec for AizBgTreeSpawnerInstance");
        assertTrue(names.contains(AizBossSmallInstance.class.getName()),
                "missing codec for AizBossSmallInstance");
        assertTrue(names.contains(AizMinibossNapalmProjectile.class.getName()),
                "missing codec for AizMinibossNapalmProjectile");

        // Tier 2: non-final differentiator reapplied after recreate.
        assertTrue(names.contains(AizBattleshipInstance.class.getName()),
                "missing codec for AizBattleshipInstance");
        assertTrue(names.contains(AizBgTreeInstance.class.getName()),
                "missing codec for AizBgTreeInstance");
        assertTrue(names.contains(Aiz2BossEndSequenceController.class.getName()),
                "missing codec for Aiz2BossEndSequenceController");

        // Tier 3: boss-relinked children.
        assertTrue(names.contains(AizMinibossBodyChild.class.getName()),
                "missing codec for AizMinibossBodyChild");
        assertTrue(names.contains(AizMinibossArmChild.class.getName()),
                "missing codec for AizMinibossArmChild");
        assertTrue(names.contains(AizMinibossNapalmController.class.getName()),
                "missing codec for AizMinibossNapalmController");
        assertTrue(names.contains(AizMinibossFlameBarrelChild.class.getName()),
                "missing codec for AizMinibossFlameBarrelChild");
        assertTrue(names.contains(AizEndBossShipChild.class.getName()),
                "missing codec for AizEndBossShipChild");
        assertTrue(names.contains(AizEndBossFlameColumnChild.class.getName()),
                "missing codec for AizEndBossFlameColumnChild");
        assertTrue(names.contains(AizEndBossArmChild.class.getName()),
                "missing codec for AizEndBossArmChild");
    }

    @Test
    void registersCodecsForFormerlyDroppedTransientChildren() {
        Set<String> names = codecClassNames();
        // Previously Tier-4 (dropped). Now captured + recreated so held rewind
        // reverses them cleanly instead of re-emitting them forward.
        List<String> nowCovered = List.of(
                AizShipBombInstance.class.getName(),
                AizBombExplosionInstance.class.getName(),
                AizMinibossBarrelShotChild.class.getName(),
                AizMinibossBarrelShotFlareChild.class.getName(),
                AizMinibossImpactFlameChild.class.getName(),
                AizMinibossFlameChild.class.getName(),
                AizMinibossDebrisChild.class.getName(),
                AizEndBossPropellerChild.class.getName(),
                AizEndBossFlameChild.class.getName(),
                AizEndBossBombChild.class.getName(),
                AizEndBossSmokeChild.class.getName(),
                AizEndBossDebrisChild.class.getName());
        for (String name : nowCovered) {
            assertTrue(names.contains(name),
                    "AIZ2 transient child must now have a rewind codec: " + name);
        }
    }
}
