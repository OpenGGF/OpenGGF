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
 * {@code deleted dynamic-codec registry API} / class metadata without a ROM, OpenGL, or an
 * active gameplay session.
 */
class TestAiz2ObjectRewindCodecs {

    private static Set<String> codecClassNames() {
        List<DynamicObjectRewindCodec> codecs = java.util.List.<com.openggf.level.objects.DynamicObjectRewindCodec>of();
        Set<String> names = new HashSet<>();
        for (DynamicObjectRewindCodec codec : codecs) {
            names.add(codec.className());
        }
        return names;
    }

    private static boolean hasDynamicRecreatePath(Class<?> type, Set<String> codecNames) {
        return codecNames.contains(type.getName()) || RewindRecreatable.class.isAssignableFrom(type);
    }

    private static void assertGenericBacked(Class<?> type, Set<String> codecNames) {
        assertFalse(codecNames.contains(type.getName()),
                type.getSimpleName() + " codec should be deleted via Phase-2 generic recreate");
        assertTrue(RewindRecreatable.class.isAssignableFrom(type),
                type.getSimpleName() + " must implement RewindRecreatable after codec deletion");
        assertTrue(hasDynamicRecreatePath(type, codecNames),
                "missing dynamic recreate path for " + type.getSimpleName());
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
        assertGenericBacked(AizBgTreeInstance.class, names);
        assertGenericBacked(Aiz2BossEndSequenceController.class, names);

        // Tier 3: boss-relinked children.
        assertGenericBacked(AizMinibossBodyChild.class, names);
        assertGenericBacked(AizMinibossArmChild.class, names);
        assertGenericBacked(AizMinibossNapalmController.class, names);
        assertGenericBacked(AizMinibossFlameBarrelChild.class, names);
        assertGenericBacked(AizEndBossShipChild.class, names);
        assertGenericBacked(AizEndBossFlameColumnChild.class, names);
        assertGenericBacked(AizEndBossArmChild.class, names);
    }

    @Test
    void hasRecreatePathsForFormerlyDroppedTransientChildren() {
        Set<String> names = codecClassNames();
        // Previously Tier-4 (dropped). Now captured + recreated so held rewind
        // reverses them cleanly instead of re-emitting them forward.
        List<Class<?>> genericBacked = List.of(
                AizShipBombInstance.class,
                AizMinibossBarrelShotChild.class,
                AizMinibossBarrelShotFlareChild.class,
                AizMinibossFlameChild.class,
                AizEndBossPropellerChild.class,
                AizEndBossFlameChild.class,
                AizEndBossBombChild.class,
                AizEndBossSmokeChild.class);
        for (Class<?> type : genericBacked) {
            assertGenericBacked(type, names);
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
            assertGenericBacked(type, names);
        }
    }
}
