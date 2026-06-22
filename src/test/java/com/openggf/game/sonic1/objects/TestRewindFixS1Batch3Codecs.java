package com.openggf.game.sonic1.objects;

import com.openggf.game.rewind.RewindRoundTripHarness;
import com.openggf.game.rewind.RewindRoundTripHarness.RoundTripSweepResult;
import com.openggf.game.sonic1.objects.bosses.FZCylinder;
import com.openggf.game.sonic1.objects.bosses.FZPlasmaBall;
import com.openggf.game.sonic1.objects.bosses.FZPlasmaLauncher;
import com.openggf.game.sonic1.objects.bosses.Sonic1BossBlockInstance;
import com.openggf.level.objects.DynamicObjectRewindCodec;
import com.openggf.level.objects.RewindRecreatable;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link Sonic1ObjectRegistry} (unioned with the shared codecs)
 * now exposes a dynamic rewind recreate path for every batch-3 S1 object that
 * was previously dropped on a held-rewind restore.
 *
 * <p>Most assertions are registry-content checks that construct a registry and
 * read {@code deleted dynamic-codec registry API} without a ROM or active gameplay session.
 * Codec deletions that now rely on generic recreate also run the headless
 * ObjectManager round-trip harness here.
 *
 * <p>{@code Sonic1SplashObjectInstance} is intentionally excluded: it is an
 * accept-drop transient cosmetic (LZ water splash re-emitted on water
 * entry/exit), documented in {@code docs/KNOWN_DISCREPANCIES.md}.
 */
class TestRewindFixS1Batch3Codecs {

    private static Set<String> codecClassNames() {
        Set<String> names = new HashSet<>();
        List<DynamicObjectRewindCodec> codecs = java.util.List.<com.openggf.level.objects.DynamicObjectRewindCodec>of();
        for (DynamicObjectRewindCodec codec : codecs) {
            names.add(codec.className());
        }
        return names;
    }

    @Test
    void hasRecreatePathForBatch3S1Objects() {
        Set<String> names = codecClassNames();

        List<String> required = List.of(
                Sonic1BossBlockInstance.class.getName(),
                Sonic1CollapsingFloorObjectInstance.class.getName(),
                Sonic1ExplosionItemObjectInstance.class.getName(),
                Sonic1GrassFireObjectInstance.class.getName(),
                Sonic1LamppostTwirlInstance.class.getName(),
                Sonic1MonitorPowerUpObjectInstance.class.getName(),
                Sonic1RingInstance.class.getName(),
                Sonic1SeesawBallObjectInstance.class.getName(),
                Sonic1SpikedBallChainObjectInstance.class.getName());

        for (String name : required) {
            assertTrue(names.contains(name) || implementsRewindRecreatable(name),
                    "missing rewind recreate path for " + name);
        }
    }

    @Test
    void fzBossChildrenUseGenericRecreatablePathInsteadOfRegisteredCodecs() {
        Set<String> names = codecClassNames();

        for (Class<?> type : List.of(FZCylinder.class, FZPlasmaLauncher.class, FZPlasmaBall.class)) {
            assertFalse(names.contains(type.getName()),
                    type.getSimpleName() + " must restore through RewindRecreatable generic recreate, "
                            + "not an explicit S1 dynamic codec");
            assertTrue(RewindRecreatable.class.isAssignableFrom(type),
                    type.getSimpleName() + " must opt into the generic RewindRecreatable path");
        }
    }

    @Test
    void syzBossBlockUsesGenericRecreateInsteadOfRegisteredCodec() {
        Set<String> names = codecClassNames();

        assertFalse(names.contains(Sonic1BossBlockInstance.class.getName()),
                "Sonic1BossBlockInstance must restore through RewindRecreatable generic recreate, "
                        + "not an explicit S1 dynamic codec");
        assertTrue(RewindRecreatable.class.isAssignableFrom(Sonic1BossBlockInstance.class),
                "Sonic1BossBlockInstance must opt into the generic RewindRecreatable path");
    }

    @Test
    void eggPrisonBodyUsesGenericRecreateInsteadOfRegisteredCodec() {
        Set<String> names = codecClassNames();

        assertFalse(names.contains(Sonic1EggPrisonObjectInstance.class.getName()),
                "Sonic1EggPrisonObjectInstance must restore through RewindRecreatable generic recreate, "
                        + "not an explicit S1 dynamic codec");
        assertTrue(RewindRecreatable.class.isAssignableFrom(Sonic1EggPrisonObjectInstance.class),
                "Sonic1EggPrisonObjectInstance must opt into the generic RewindRecreatable path");

        RoundTripSweepResult result =
                RewindRoundTripHarness.probeClass(Sonic1EggPrisonObjectInstance.class.getName());
        assertInstanceOf(RoundTripSweepResult.Passed.class, result,
                "Sonic1EggPrisonObjectInstance must round-trip through the generic recreate path; got: "
                        + result);
    }

    @Test
    void ringFlashUsesGenericRecreateInsteadOfRegisteredCodec() {
        Set<String> names = codecClassNames();

        assertFalse(names.contains(Sonic1RingFlashObjectInstance.class.getName()),
                "Sonic1RingFlashObjectInstance must restore through RewindRecreatable generic recreate, "
                        + "not an explicit S1 dynamic codec");
        assertTrue(RewindRecreatable.class.isAssignableFrom(Sonic1RingFlashObjectInstance.class),
                "Sonic1RingFlashObjectInstance must opt into the generic RewindRecreatable path");
    }

    private static boolean implementsRewindRecreatable(String className) {
        try {
            return RewindRecreatable.class.isAssignableFrom(Class.forName(className));
        } catch (ClassNotFoundException e) {
            throw new AssertionError(e);
        }
    }
}
