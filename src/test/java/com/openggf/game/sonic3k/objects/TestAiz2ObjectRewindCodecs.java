package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.DynamicObjectRewindCodec;
import com.openggf.level.objects.RewindRecreatable;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that every AIZ2 battleship / boss-endgame object that must survive a
 * rewind keyframe restore has a dynamic recreate path, either through a registry
 * codec or the Phase-2 {@link RewindRecreatable} generic path.
 *
 * <p>Held rewind restores the nearest keyframe and re-simulates forward each
 * displayed frame, so any object dropped on restore is re-emitted from scratch
 * and visibly plays forward. To make the whole scene reverse cleanly, every AIZ2
 * battleship/boss child is now captured and recreated — there are no longer any
 * intentionally-dropped AIZ2 transients.
 *
 * <p>This is a pure recreate-path test: it constructs a registry and reads
 * {@code dynamicRewindCodecs()} / class metadata without a ROM, OpenGL, or an
 * active gameplay session.
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

    private static boolean hasDynamicRecreatePath(Class<?> type, Set<String> codecNames) {
        return codecNames.contains(type.getName()) || RewindRecreatable.class.isAssignableFrom(type);
    }

    @Test
    void hasDynamicRecreatePathsForAiz2RecreatableObjects() {
        Set<String> names = codecClassNames();
        // Tier 1: self-contained objects may use codecs or Phase-2 generic recreate.
        assertTrue(hasDynamicRecreatePath(AizBgTreeSpawnerInstance.class, names),
                "missing dynamic recreate path for AizBgTreeSpawnerInstance");
        assertTrue(hasDynamicRecreatePath(AizBossSmallInstance.class, names),
                "missing dynamic recreate path for AizBossSmallInstance");
        assertTrue(hasDynamicRecreatePath(AizMinibossNapalmProjectile.class, names),
                "missing dynamic recreate path for AizMinibossNapalmProjectile");

        // Tier 2: non-final differentiator reapplied after recreate.
        assertFalse(names.contains(AizBattleshipInstance.class.getName()),
                "AizBattleshipInstance codec should be deleted via Phase-2 generic recreate");
        assertTrue(RewindRecreatable.class.isAssignableFrom(AizBattleshipInstance.class),
                "AizBattleshipInstance must implement RewindRecreatable after codec deletion");
        assertTrue(hasDynamicRecreatePath(AizBattleshipInstance.class, names),
                "missing dynamic recreate path for AizBattleshipInstance");
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
    void hasRecreatePathsForFormerlyDroppedTransientChildren() {
        Set<String> names = codecClassNames();
        // Previously Tier-4 (dropped). Now captured + recreated so held rewind
        // reverses them cleanly instead of re-emitting them forward.
        List<String> codecBacked = List.of(
                AizShipBombInstance.class.getName(),
                AizMinibossBarrelShotChild.class.getName(),
                AizMinibossBarrelShotFlareChild.class.getName(),
                AizMinibossFlameChild.class.getName(),
                AizEndBossPropellerChild.class.getName(),
                AizEndBossFlameChild.class.getName(),
                AizEndBossBombChild.class.getName(),
                AizEndBossSmokeChild.class.getName());
        for (String name : codecBacked) {
            assertTrue(names.contains(name),
                    "AIZ2 transient child must now have a rewind codec: " + name);
        }
    }

    @Test
    void selfContainedTransientChildrenUseGenericRecreateWithoutHandwrittenCodecs() {
        Set<String> names = codecClassNames();
        List<Class<?>> genericBacked = List.of(
                AizBombExplosionInstance.class,
                AizEndBossDebrisChild.class,
                AizMinibossImpactFlameChild.class,
                AizMinibossDebrisChild.class);
        for (Class<?> type : genericBacked) {
            assertFalse(names.contains(type.getName()),
                    type.getSimpleName() + " codec should be deleted via Phase-2 generic recreate");
            assertTrue(RewindRecreatable.class.isAssignableFrom(type),
                    type.getSimpleName() + " must implement RewindRecreatable after codec deletion");
            assertTrue(hasDynamicRecreatePath(type, names),
                    "missing dynamic recreate path for " + type.getSimpleName());
        }
    }
}
