package com.openggf.mods.code;

import com.openggf.game.GameModule;
import com.openggf.game.ZoneKey;
import com.openggf.game.ZoneProgressionPlan;
import com.openggf.game.ZoneRegistry;
import com.openggf.game.dataselect.DataSelectDestination;
import com.openggf.game.modzone.ModPaletteClaim;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.mods.StockProgressionAnchors;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestModGameStartResolver {
    @Test
    void lastEffectiveGameStartWinsAndReportsEveryShadowedOwnerOnce() {
        List<ObservedFinding> findings = new ArrayList<>();
        GameModule resolved = apply(new Sonic3kGameModule(), findings,
                startPlan("alpha", "sky", 0),
                startPlan("beta", "sky", 1),
                startPlan("gamma", "sky", 2));

        assertEquals(zoneDestination(resolved, "gamma", "sky"),
                resolved.getDataSelectHostProfile().newGameDestination());
        assertEquals(List.of(
                        new ObservedFinding("alpha", "MOD_GAME_START_SHADOWED"),
                        new ObservedFinding("beta", "MOD_GAME_START_SHADOWED")),
                findings);
    }

    @Test
    void multipleShadowedStartsFromOneOwnerEmitOneOwnerFinding() {
        List<ObservedFinding> findings = new ArrayList<>();
        GameModule resolved = apply(new Sonic3kGameModule(), findings,
                startPlan("alpha", "sky", 0),
                startPlan("alpha", "clouds", 1),
                startPlan("beta", "sky", 2));

        assertEquals(zoneDestination(resolved, "beta", "sky"),
                resolved.getDataSelectHostProfile().newGameDestination());
        assertEquals(List.of(new ObservedFinding("alpha", "MOD_GAME_START_SHADOWED")),
                findings);
    }

    @Test
    void disablingWinnerRevealsPreviousThenStock() {
        GameModule base = new Sonic3kGameModule();
        GameModule both = apply(base, new ArrayList<>(),
                startPlan("alpha", "sky", 0), startPlan("beta", "sky", 1));
        GameModule alphaOnly = apply(base, new ArrayList<>(), startPlan("alpha", "sky", 0));

        assertEquals(zoneDestination(both, "beta", "sky"),
                both.getDataSelectHostProfile().newGameDestination());
        assertEquals(zoneDestination(alphaOnly, "alpha", "sky"),
                alphaOnly.getDataSelectHostProfile().newGameDestination());
        assertEquals(new DataSelectDestination(0, 0),
                base.getDataSelectHostProfile().newGameDestination());
    }

    @Test
    void anchorlessGameStartUsesTaggedDestinationWithoutProgressionInsertion() {
        GameModule base = new Sonic3kGameModule();
        ModRegistrationPlan plan = startPlan("alpha", "sky", 0);
        GameModule resolved = apply(base, new ArrayList<>(), plan);
        ZoneRegistry stock = base.getZoneRegistry();
        ZoneRegistry decorated = resolved.getZoneRegistry();

        assertNull(plan.zones().getFirst().insertAfter());
        assertTrue(plan.zones().getFirst().gameStart());
        assertTrue(StockProgressionAnchors.anchorsFor("s3k").isEmpty());
        assertEquals(zoneDestination(resolved, "alpha", "sky"),
                resolved.getDataSelectHostProfile().newGameDestination());
        assertEquals(stock.getZoneCount(),
                decorated.resolveZoneKey(ZoneKey.mod("alpha", "sky")).orElseThrow());
        assertStockProgressionUnchanged(stock, decorated);
    }

    @Test
    void nonGameStartContributionAndS2DefaultRetainMarker() {
        ModZoneContribution compatible = new ModZoneContribution(
                "sky", new BakedLevelRef("sky/level.json"), null, null, false);
        ModZoneContribution start = new ModZoneContribution(
                "sky", new BakedLevelRef("sky/level.json"), null, null, true);

        assertFalse(compatible.gameStart());
        assertTrue(start.withDefaultAnchor("mtz3").gameStart());
        assertEquals("mtz3", start.withDefaultAnchor("mtz3").insertAfter());
    }

    @Test
    void declaredAndPreparedGameStartMismatchIsRejected() {
        ModZoneContribution declared = new ModZoneContribution(
                "sky", new BakedLevelRef("sky/level.json"), null, null, true);
        PreparedModZone mismatched = PreparedModZone.metadata(
                "alpha", "sky", null, "SKY", 0x400, 0x40, 0x20, 0x20);

        assertThrows(IllegalArgumentException.class, () -> new ModRegistrationPlan(
                "alpha", "s3k", Map.of(), Map.of(), Map.of(), List.of(),
                List.of(declared), List.of(mismatched)));
    }

    private static void assertStockProgressionUnchanged(ZoneRegistry stock, ZoneRegistry decorated) {
        for (int zone = 0; zone < stock.getZoneCount(); zone++) {
            for (int act = 0; act < stock.getActCount(zone); act++) {
                ZoneProgressionPlan.ProgressionResult expected = stock.progressionPlan().next(
                        stock.progressionTopology(), zone, act);
                ZoneProgressionPlan.ProgressionResult actual = decorated.progressionPlan().next(
                        decorated.progressionTopology(), zone, act);
                assertEquals(expected, actual, "stock progression changed at " + zone + ":" + act);
            }
        }
    }

    private static DataSelectDestination zoneDestination(GameModule module, String owner, String local) {
        int zone = module.getZoneRegistry().resolveZoneKey(ZoneKey.mod(owner, local)).orElseThrow();
        return new DataSelectDestination(zone, 0);
    }

    private static GameModule apply(GameModule base, List<ObservedFinding> findings,
                                    ModRegistrationPlan... plans) {
        GameModule current = base;
        for (ModRegistrationPlan plan : plans) {
            current = new ModBackedGamePatch(plan, null,
                    (owner, finding) -> findings.add(new ObservedFinding(owner, finding.code())))
                    .apply(current, patchContext());
        }
        return current;
    }

    private static ModRegistrationPlan startPlan(String owner, String local, int ordinal) {
        ModZoneContribution declared = new ModZoneContribution(
                local, new BakedLevelRef(local + "/level.json"), null, null, true);
        PreparedModZone prepared = PreparedModZone.prepared(owner, declared, definition(ordinal));
        return new ModRegistrationPlan(owner, "s3k", Map.of(), Map.of(), Map.of(), List.of(),
                List.of(declared), List.of(prepared));
    }

    private static ModLevelDefinition definition(int ordinal) {
        ModLevelDefinition source = TestS3kModZoneAdapter.definition(2, null,
                List.of(new ModPaletteClaim(2, 0, 0)));
        return new ModLevelDefinition(source.formatVersion(), source.zoneName(),
                0x40 + ordinal, 0x400 + ordinal, source.blockGridSide(),
                source.width(), source.height(), source.bounds(), source.start(), source.music(),
                source.objects(), source.rings(), source.patternBytes(), source.chunkBytes(),
                source.blockBytes(), source.foregroundMap(), source.backgroundMap().orElse(null),
                source.solidHeights(), source.solidWidths(), source.solidAngles(),
                source.primaryCollisionIndices(), source.secondaryCollisionIndices(),
                source.paletteLines(), source.patternCount(), source.chunkCount(), source.blockCount(),
                source.solidProfileCount(), source.hostMetadata().orElse(null), source.paletteClaims());
    }

    private static com.openggf.game.patch.PatchContext patchContext() {
        return new com.openggf.game.patch.PatchContext(ignored -> null,
                com.openggf.configuration.SonicConfigurationService.createStandalone());
    }

    private record ObservedFinding(String owner, String code) {
    }
}
