package com.openggf.mods.code;

import com.openggf.game.GameModule;
import com.openggf.io.ModAssetRoot;
import com.openggf.level.AbstractLevel;
import com.openggf.level.LevelPlacementPlan;
import com.openggf.level.RegisteredLevelPlacements;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.rings.RingSpawn;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestModLevelPlacementRegistration {
    private static final int LEVEL = 7;
    private static final RingSpawn SAFE = new RingSpawn(100, 150, 2);
    private static final RingSpawn REMOVED = new RingSpawn(120, 150, 3);
    private static final RingSpawn OUTSIDE = new RingSpawn(300, 150, 4);

    private static LevelPlacementPlan plan(List<RingSpawn> rings) {
        return new LevelPlacementPlan(new LevelPlacementPlan.Bounds(90, 100, 200, 200), rings,
                List.of(new LevelPlacementPlan.ObjectAddition("sentry", 170, 150, 1, 0)));
    }

    private static GameModule stockModule() {
        GameModule stock = mock(GameModule.class);
        when(stock.getObjectPlacementEncoding()).thenReturn(new com.openggf.game.common.CommonObjectPlacementEncoding());
        return stock;
    }

    private static ModContext context(String game) {
        return new ModContext("trusted-owner", game, ModAssetRoot.forTests("trusted-owner"));
    }

    private static ModRegistrationPlan registration(LevelPlacementPlan plan) {
        ModContext context = context("s3k");
        context.registerLevelPlacementPlan(LEVEL, plan);
        context.registerObject("sentry", (spawn, registry) -> null);
        return context.freeze();
    }

    private static final class NativeLevel extends AbstractLevel {
        NativeLevel() { this(List.of(SAFE, REMOVED, OUTSIDE)); }
        NativeLevel(List<RingSpawn> nativeRings) {
            super(7);
            objects = List.of(new ObjectSpawn(140, 150, 0x79, 2, 0, true, 150, 12));
            rings = nativeRings;
        }
    }

    @Test void boundedValuesDefensivelyCopyAndRejectInvalidDeclarations() {
        var rings = new ArrayList<>(List.of(SAFE));
        var additions = new ArrayList<>(List.of(new LevelPlacementPlan.ObjectAddition("sentry", 170, 150, 1, 0)));
        var value = new LevelPlacementPlan(new LevelPlacementPlan.Bounds(90, 100, 200, 200), rings, additions);
        rings.clear(); additions.clear();
        assertEquals(List.of(SAFE), value.retainedRings());
        assertEquals(1, value.additions().size());
        assertThrows(UnsupportedOperationException.class, () -> value.retainedRings().clear());
        assertThrows(IllegalArgumentException.class, () -> new LevelPlacementPlan.Bounds(0, 0, 1024, 1));
        assertThrows(IllegalArgumentException.class, () -> new LevelPlacementPlan.Bounds(-1, 0, 10, 10));
        assertThrows(IllegalArgumentException.class, () -> plan(List.of(SAFE, SAFE)));
        assertThrows(IllegalArgumentException.class, () -> plan(List.of(OUTSIDE)));
        assertThrows(IllegalArgumentException.class, () -> plan(List.of(new RingSpawn(100, 150))));
        assertThrows(IllegalArgumentException.class, () -> new LevelPlacementPlan.ObjectAddition("foreign:sentry", 170, 150, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new LevelPlacementPlan.ObjectAddition("sentry", -1, 150, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new LevelPlacementPlan(value.bounds(), List.of(),
                List.of(new LevelPlacementPlan.ObjectAddition("sentry", 300, 150, 0, 0))));
    }

    @Test void registrationPoisonsForeignUnknownDuplicateAndUnsupportedPlans() {
        ModContext unknown = context("s3k");
        unknown.registerLevelPlacementPlan(LEVEL, plan(List.of(SAFE)));
        assertThrows(ModRegistrationException.class, unknown::freeze);
        assertThrows(ModRegistrationException.class, () -> unknown.registerObject("later", (spawn, registry) -> null));
        ModContext duplicate = context("s3k");
        duplicate.registerLevelPlacementPlan(LEVEL, plan(List.of(SAFE)));
        assertThrows(ModRegistrationException.class, () -> duplicate.registerLevelPlacementPlan(LEVEL, plan(List.of(SAFE))));
        assertThrows(ModRegistrationException.class, duplicate::freeze);
        for (String game : List.of("s1", "any")) {
            assertThrows(ModRegistrationException.class, () -> context(game).registerLevelPlacementPlan(LEVEL, plan(List.of(SAFE))));
        }
        assertThrows(ModRegistrationException.class, () -> context("s3k").registerLevelPlacementPlan(-1, plan(List.of(SAFE))));
        assertThrows(IllegalArgumentException.class, () -> new ModBackedGamePatch(registration(plan(List.of(SAFE)))));
    }

    @Test void activeTrustedOwnerRetainsNativeIdentitiesAndAppliesOnlyOnce() {
        var findings = new ModRuntimeFindingStore();
        ModFaultBoundary boundary = new ModFaultBoundary(Map.of(), findings,
                owners -> new ModStateSaveResult.Saved(), owners -> { });
        GameModule stock = stockModule();
        GameModule active = new ModBackedGamePatch(registration(plan(List.of(SAFE))), boundary).apply(stock, null);
        assertNull(stock.getGameService(RegisteredLevelPlacements.class), "stock-off has no placement authority");
        var source = active.getGameService(RegisteredLevelPlacements.class);
        assertSame(source, active.getGameService(RegisteredLevelPlacements.class));
        NativeLevel level = new NativeLevel();
        ObjectSpawn post = level.getObjects().getFirst();
        source.install("s3k", LEVEL, level, true);
        assertEquals(List.of(SAFE, OUTSIDE), level.getRings());
        assertSame(post, level.getObjects().getFirst());
        ObjectSpawn sentry = level.getObjects().getLast();
        assertEquals("trusted-owner", sentry.ownerModId());
        assertEquals("trusted-owner:sentry", sentry.objectKey());
        assertEquals(13, sentry.layoutIndex());
        assertEquals(0, sentry.objectId());
        source.install("s3k", LEVEL, level, true);
        assertEquals(2, level.getObjects().size());
        assertTrue(findings.snapshot().isEmpty());
        NativeLevel anotherIndex = new NativeLevel();
        source.install("s3k", LEVEL + 1, anotherIndex, true);
        assertEquals(3, anotherIndex.getRings().size());
        assertEquals(1, anotherIndex.getObjects().size());
    }

    @Test void addedNativeRingsHaveHostIdentitiesAndBoundedDeclarations() {
        var basePlan = plan(List.of(SAFE));
        var value = new LevelPlacementPlan(basePlan.bounds(), basePlan.retainedRings(), basePlan.additions(),
                List.of(new LevelPlacementPlan.RingAddition(180, 150)));
        ModFaultBoundary boundary = new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                owners -> new ModStateSaveResult.Saved(), owners -> { });
        GameModule active = new ModBackedGamePatch(registration(value), boundary).apply(stockModule(), null);
        NativeLevel level = new NativeLevel(List.of(SAFE, new RingSpawn(120, 150, 999), OUTSIDE));
        active.getGameService(RegisteredLevelPlacements.class).install("s3k", LEVEL, level, true);
        assertEquals(List.of(SAFE, OUTSIDE, new RingSpawn(180, 150, 1000)), level.getRings());
        active.getGameService(RegisteredLevelPlacements.class).install("s3k", LEVEL, level, true);
        assertEquals(3, level.getRings().size());
        assertThrows(IllegalArgumentException.class, () -> new LevelPlacementPlan(value.bounds(), List.of(), List.of(),
                List.of(new LevelPlacementPlan.RingAddition(300, 150))));
        assertThrows(IllegalArgumentException.class, () -> new LevelPlacementPlan(value.bounds(), List.of(SAFE), List.of(),
                List.of(new LevelPlacementPlan.RingAddition(SAFE.x(), SAFE.y()))));
        assertThrows(IllegalArgumentException.class, () -> new LevelPlacementPlan(value.bounds(), List.of(), List.of(),
                List.of(new LevelPlacementPlan.RingAddition(180, 150), new LevelPlacementPlan.RingAddition(180, 150))));
        assertThrows(IllegalArgumentException.class, () -> new LevelPlacementPlan(value.bounds(), List.of(), List.of(),
                java.util.stream.IntStream.range(0, 9).mapToObj(x -> new LevelPlacementPlan.RingAddition(180 + x, 150)).toList()));
    }

    @Test void ringAdditionCannotReplaceEvenAnUnretainedNativeIdentity() {
        var original = plan(List.of(SAFE));
        var value = new LevelPlacementPlan(original.bounds(), original.retainedRings(), original.additions(),
                List.of(new LevelPlacementPlan.RingAddition(REMOVED.x(), REMOVED.y())));
        ModFaultBoundary boundary = new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                owners -> new ModStateSaveResult.Saved(), owners -> { });
        GameModule active = new ModBackedGamePatch(registration(value), boundary).apply(stockModule(), null);
        NativeLevel level = new NativeLevel();
        assertThrows(ModFaultBoundary.CallbackAborted.class,
                () -> active.getGameService(RegisteredLevelPlacements.class).install("s3k", LEVEL, level, true));
        assertEquals(List.of(SAFE, REMOVED, OUTSIDE), level.getRings());
        assertEquals(1, level.getObjects().size());
    }

    @Test void overlappingActiveOwnersAbortAtomicallyAndBaseGameMismatchIsRejected() {
        var findings = new ModRuntimeFindingStore();
        ModFaultBoundary boundary = new ModFaultBoundary(Map.of(), findings,
                owners -> new ModStateSaveResult.Saved(), owners -> { });
        GameModule earlier = new ModBackedGamePatch(registration(plan(List.of(SAFE))), boundary)
                .apply(stockModule(), null);
        ModContext laterContext = new ModContext("later-owner", "s3k", ModAssetRoot.forTests("later-owner"));
        laterContext.registerObject("sentry", (spawn, registry) -> null);
        laterContext.registerLevelPlacementPlan(LEVEL, plan(List.of(SAFE)));
        GameModule later = new ModBackedGamePatch(laterContext.freeze(), boundary).apply(earlier, null);
        NativeLevel level = new NativeLevel();
        var failure = assertThrows(ModFaultBoundary.CallbackAborted.class,
                () -> later.getGameService(RegisteredLevelPlacements.class).install("s3k", LEVEL, level, true));
        assertEquals("later-owner", failure.owner());
        assertEquals(1, level.getObjects().size());
        assertEquals(List.of(SAFE, REMOVED, OUTSIDE), level.getRings());
        assertTrue(findings.findingsFor("trusted-owner").isEmpty());
        var wrongGame = assertThrows(ModFaultBoundary.CallbackAborted.class,
                () -> earlier.getGameService(RegisteredLevelPlacements.class).install("s2", LEVEL, level, true));
        assertEquals("trusted-owner", wrongGame.owner());
        assertEquals(1, level.getObjects().size());
    }

    @Test void invalidNativeIdentityOrOverrideAbortsTrustedOwnerBeforeMutation() {
        for (boolean override : List.of(false, true)) {
            var findings = new ModRuntimeFindingStore();
            var disabled = new AtomicReference<Set<String>>();
            ModFaultBoundary boundary = new ModFaultBoundary(Map.of("dependent", Set.of("trusted-owner")), findings,
                    owners -> new ModStateSaveResult.Saved(), disabled::set);
            var value = plan(override ? List.of(SAFE) : List.of(new RingSpawn(100, 150, 999)));
            GameModule active = new ModBackedGamePatch(registration(value), boundary).apply(stockModule(), null);
            NativeLevel level = new NativeLevel();
            var failure = assertThrows(ModFaultBoundary.CallbackAborted.class,
                    () -> active.getGameService(RegisteredLevelPlacements.class).install("s3k", LEVEL, level, !override));
            assertEquals("trusted-owner", failure.owner());
            assertEquals(Set.of("trusted-owner", "dependent"), disabled.get());
            assertEquals(List.of(SAFE, REMOVED, OUTSIDE), level.getRings());
            assertEquals(1, level.getObjects().size());
            assertEquals("MOD_CALLBACK_FAILED", findings.findingsFor("trusted-owner").getFirst().code());
        }
    }
}
