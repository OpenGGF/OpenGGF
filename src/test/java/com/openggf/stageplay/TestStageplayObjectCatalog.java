package com.openggf.stageplay;

import com.openggf.game.GameId;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.sonic3k.constants.S3kZoneSet;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.level.objects.AbstractObjectRegistry;
import com.openggf.level.objects.ObjectRegistryCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.openggf.stageplay.StageplayObjectCatalog.*;
import static org.junit.jupiter.api.Assertions.*;

class TestStageplayObjectCatalog {
    @Test
    void enumerationCoversAllByteIdsWithoutConstructingAnyObject() {
        var constructions = new AtomicInteger();
        var registry = registry(constructions);
        var catalog = inspect("test", registry, new Context(0, 0, "source"));
        assertEquals(256, catalog.entries().size());
        for (int id = 0; id < 256; id++) assertEquals(id, catalog.entries().get(id).objectId());
        assertEquals(Readiness.NEEDS_PREFLIGHT, catalog.entries().get(7).free().readiness());
        assertEquals(List.of(Reason.FACTORY_NOT_RESOLVED), catalog.entries().get(7).free().reasons());
        assertEquals(Readiness.UNAVAILABLE, catalog.entries().get(8).free().readiness());
        assertEquals(List.of(Reason.NO_LAYOUT_FACTORY), catalog.entries().get(8).free().reasons());
        assertEquals(Readiness.NEEDS_PREFLIGHT, catalog.entries().get(7).pose().readiness());
        assertTrue(catalog.entries().get(7).pose().reasons().contains(Reason.VISUAL_METADATA_MISSING));
        assertEquals(Readiness.UNAVAILABLE, catalog.entries().get(8).pose().readiness());
        assertEquals(List.of(Reason.NO_LAYOUT_FACTORY), catalog.entries().get(8).pose().reasons());
        assertEquals(0, constructions.get(), "even a probe constructor can publish world effects");
        assertThrows(UnsupportedOperationException.class, () -> catalog.entries().clear());
    }

    @Test
    void badnikRegistrationTraitsClassifyInactivePlacementsWithoutConstructingFactories() {
        var constructions = new AtomicInteger();
        var registry = new AbstractObjectRegistry() {
            @Override protected void registerDefaultFactories() {
                var factory = (com.openggf.level.objects.ObjectFactory) (spawn, owner) -> {
                    constructions.incrementAndGet(); throw new AssertionError("Classification invoked a factory");
                };
                registerBadnikFactory(7, factory);
                registerBadnikFactory(8, factory);
                registerFactory(8, factory); // A replacement ordinary object must not retain the old trait.
            }
            @Override public String getPrimaryName(int id) { return "native object"; }
        };
        var entries = ObjectRegistryCatalog.entries(registry, new ObjectRegistryCatalog.Context(0, 0));
        assertEquals(ObjectRegistryCatalog.Role.BADNIK, entries.get(7).semantics().role());
        assertEquals(ObjectRegistryCatalog.Role.UNRESOLVED, entries.get(8).semantics().role());
        assertEquals(1, entries.stream().filter(e -> e.semantics().role() == ObjectRegistryCatalog.Role.BADNIK).count());
        assertEquals(0, constructions.get());
    }

    @Test
    void everyStockContextExposesTheCompleteDeclaredBadnikInventoryAndKeepsNativeAliasesSeparate() {
        // Registration audit: S1 has 14 badnik roots (including Bomb/Ball Hog);
        // S2 has 28 registrations (Grounder and Rexon each have two native IDs).
        // S3K's 31 declarations cover 40 native routines: 26 S3KL roles, 10 SKL
        // roles (nine common byte IDs), plus three LRZ and one SSZ zone overrides.
        for (var game : List.of(GameId.S1, GameId.S2, GameId.S3K)) {
            for (var catalog : inspect(GameModuleRegistry.createStockModule(game))) {
                long expected = switch (game) {
                    case S1 -> 14;
                    case S2 -> 28;
                    case S3K -> S3kZoneSet.forZone(catalog.context().zone()) == S3kZoneSet.S3KL ? 26
                            : catalog.context().zone() == Sonic3kZoneIds.ZONE_LRZ ? 13
                            : catalog.context().zone() == Sonic3kZoneIds.ZONE_SSZ ? 11 : 10;
                    default -> throw new AssertionError(game);
                };
                var badniks = catalog.entries().stream().filter(e -> e.role() == ObjectRegistryCatalog.Role.BADNIK).toList();
                assertEquals(expected, badniks.size(), game + "/" + catalog.context());
                for (var badnik : badniks) {
                    assertEquals(ObjectRegistryCatalog.FactoryStatus.REGISTERED, badnik.factoryStatus(), badnik.toString());
                    assertNotEquals(ObjectRegistryCatalog.PositionRule.NATIVE_ARENA, badnik.positionRule(), badnik.toString());
                }
            }
        }
        var s3 = inspect("s3k", 0, 0).entries();
        var sk = inspect("s3k", Sonic3kZoneIds.ZONE_MHZ, 0).entries();
        assertEquals(9, java.util.stream.IntStream.range(0, 256)
                .filter(id -> s3.get(id).role() == ObjectRegistryCatalog.Role.BADNIK && sk.get(id).role() == ObjectRegistryCatalog.Role.BADNIK).count());
        assertEquals(17, java.util.stream.IntStream.range(0, 256)
                .filter(id -> s3.get(id).role() == ObjectRegistryCatalog.Role.BADNIK && sk.get(id).role() != ObjectRegistryCatalog.Role.BADNIK).count(),
                "boss, controller, cutscene and unsupported SKL aliases must not inherit S3KL badnik traits");
    }

    @Test
    void nativeArenaRestrictionComesFromProviderDeclarationInsteadOfBossRoleOrName() {
        var arena = entry("s2", 0, 1, 0x56);
        assertEquals(ObjectRegistryCatalog.Role.BOSS, arena.role());
        assertEquals(ObjectRegistryCatalog.PositionRule.NATIVE_ARENA, arena.positionRule());
        assertEquals(NATIVE_ARENA_LABEL, arena.freeRestriction());
        assertTrue(arena.citation().contains("Obj56_Init"));
        assertEquals(Animation.HELD_OR_AUTHORED_FRAME, arena.animation());
        assertTrue(arena.pose().reasons().contains(Reason.STATE_MACHINE_ANIMATION_HELD));
        assertFalse(arena.appearanceExclusions().isEmpty());
        assertTrue(arena.citation().contains("Ani_obj56_a"));
        assertEquals(Readiness.NEEDS_PREFLIGHT, arena.free().readiness(), "metadata does not execute native init");
        var anchored = entry("s2", 0, 1, 0xC5);
        assertEquals(ObjectRegistryCatalog.Role.BOSS, anchored.role());
        assertNotEquals(NATIVE_ARENA_LABEL, anchored.freeRestriction(), "boss role does not prove anchor rejection");

    }

    @Test
    void sameS3kByteIdSelectsItsActualNativePointerTableAndZoneOverride() {
        var hydrocity = entry("s3k", 1, 1, 0x9A);
        var lavaReef = entry("s3k", 9, 0, 0x9A);
        assertEquals("S3KL", hydrocity.objectTable());
        assertEquals("SKL", lavaReef.objectTable());
        assertEquals("pointer-table-entry", hydrocity.factorySelection());
        assertTrue(lavaReef.factorySelection().startsWith("stock-zone-override-"));
        assertNotEquals(hydrocity.name(), lavaReef.name());
        assertEquals(ObjectRegistryCatalog.FactoryStatus.REGISTERED, hydrocity.factoryStatus());
        assertEquals(ObjectRegistryCatalog.FactoryStatus.REGISTERED, lavaReef.factoryStatus());
    }

    @Test
    void satControlExclusionBelongsToItsNativePointerTableAndDoesNotClaimAnchorReset() {
        for (int id : List.of(0xA6, 0x8B)) {
            var restricted = entry("s3k", 12, 0, id);
            assertEquals("SKL", restricted.objectTable());
            assertTrue(restricted.pose().reasons().contains(Reason.UNSUPPORTED_RENDER_ORDER));
            assertTrue(restricted.appearanceExclusions().stream().anyMatch(note -> note.contains("SpriteMask")));
            assertTrue(restricted.citation().contains("loc_86308"));
            assertNotEquals(NATIVE_ARENA_LABEL, restricted.freeRestriction());
            assertFalse(entry("s3k", 3, 0, id).pose().reasons().contains(Reason.UNSUPPORTED_RENDER_ORDER),
                    "S3KL aliases have different routines and must not inherit SKL restrictions");
        }
    }

    @Test
    void releasedAnimalsAreAuxiliaryNativeFactoriesAndNeverInventedLayoutIds() {
        var s1 = inspect("s1", 0, 0);
        var s2 = inspect("s2", 0, 0);
        var s3k = inspect("s3k", 0, 0);
        assertTrue(s1.auxiliaryEntries().isEmpty(), "S1 keeps its actual layout animal owner");
        assertEquals(1, s2.auxiliaryEntries().size());
        assertEquals(1, s3k.auxiliaryEntries().size());
        for (var catalog : List.of(s2, s3k)) {
            var animal = catalog.auxiliaryEntries().getFirst();
            assertEquals("released-animal", animal.key());
            assertEquals(ObjectRegistryCatalog.Role.ANIMAL, animal.role());
            assertEquals(Readiness.NEEDS_PREFLIGHT, animal.free().readiness());
            assertEquals(Readiness.NEEDS_PREFLIGHT, animal.pose().readiness());
            assertFalse(animal.routineCitations().isEmpty());
            assertFalse(animal.animalVariants().isEmpty());
            assertTrue(animal.animalVariants().stream().allMatch(v -> v.artVariant() >= 0 && v.artVariant() <= 1));
        }
        assertTrue(s2.auxiliaryEntries().getFirst().routineCitations().getFirst().contains("Obj28_InitRandom"));
        assertTrue(s3k.auxiliaryEntries().getFirst().routineCitations().getFirst().contains("loc_2C924"));
    }

    @Test
    void stockMenusReuseTheirRomFreeContextCatalog() {
        assertSame(inspect("s2", 0, 0), inspect("s2", 0, 0));
        assertSame(entry("s2", 0, 0, 7), entry("s2", 0, 0, 7));
        assertNotSame(inspect("s2", 0, 0), inspect("s2", 0, 1));
        assertThrows(IllegalArgumentException.class, () -> entry("s2", -1, 0, 7));
        assertThrows(IllegalArgumentException.class, () -> entry("s2", 0, 0, 256));
    }

    private static AbstractObjectRegistry registry(AtomicInteger constructions) {
        return new AbstractObjectRegistry() {
            @Override protected void registerDefaultFactories() {
                registerFactory(7, (spawn, owner) -> { constructions.incrementAndGet(); throw new AssertionError("Factory executed"); });
            }
            @Override public String getPrimaryName(int id) { return "Object " + id; }
        };
    }
}
