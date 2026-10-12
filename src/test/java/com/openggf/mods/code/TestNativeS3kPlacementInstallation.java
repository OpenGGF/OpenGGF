package com.openggf.mods.code;

import com.openggf.game.CheckpointState;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.Sonic3kLevel;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.io.ModAssetRoot;
import com.openggf.level.LevelData;
import com.openggf.level.LevelPlacementPlan;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Real ROM assembly/contact proof for the placement contract, not a certified encounter route. */
@Isolated
@RequiresRom(SonicGame.SONIC_3K)
class TestNativeS3kPlacementInstallation {
    @AfterEach void cleanup() { TestEnvironment.resetAll(); }

    @Test void nativeMhzOwnersAndPhysicalPostSurviveBoundedRingAdmission() {
        HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_MHZ, 0).build();
        Sonic3kLevel stock = assertInstanceOf(Sonic3kLevel.class, GameServices.level().getCurrentLevel());
        var bounds = new LevelPlacementPlan.Bounds(0x1D10, 0x100, 0x1DB8, 0x1D0);
        var retained = stock.getRings().stream().filter(ring -> bounds.contains(ring.x(), ring.y())).limit(1).toList();
        var expected = stock.getRings().stream()
                .filter(ring -> !bounds.contains(ring.x(), ring.y()) || retained.contains(ring)).toList();
        ModContext context = new ModContext("trusted-owner", "s3k", ModAssetRoot.forTests("trusted-owner"));
        context.registerLevelPlacementPlan(LevelData.S3K_MUSHROOM_HILL_1.getLevelIndex(),
                new LevelPlacementPlan(bounds, retained, List.of(),
                        List.of(new LevelPlacementPlan.RingAddition(0x1D70, 0x1A8))));
        var findings = new ModRuntimeFindingStore();
        var boundary = new ModFaultBoundary(Map.of(), findings, owners -> new ModStateSaveResult.Saved(), owners -> { });
        var active = new ModBackedGamePatch(context.freeze(), boundary).apply(new Sonic3kGameModule(), null);
        TestEnvironment.configureGameModuleFixture(active);
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_MHZ, 0)
                .startPosition((short) 0x1D30, (short) 0x1A8).startPositionIsCentre()
                .withFreshLevelStartLifecycle().build();
        Sonic3kLevel loaded = assertInstanceOf(Sonic3kLevel.class, GameServices.level().getCurrentLevel());
        assertTrue(loaded.hasStockRomZoneIdentity());
        assertEquals(stock.getObjectZoneSet(), loaded.getObjectZoneSet());
        assertEquals(stock.getObjects(), loaded.getObjects(), "native placements remain lossless");
        var expectedWithRecovery = new java.util.ArrayList<>(expected);
        expectedWithRecovery.add(new com.openggf.level.rings.RingSpawn(0x1D70, 0x1A8,
                stock.getRings().stream().mapToInt(com.openggf.level.rings.RingSpawn::placementId).max().orElse(-1) + 1));
        assertEquals(expectedWithRecovery, loaded.getRings());
        assertArrayEquals(stock.getMap().getData(), loaded.getMap().getData(), "native terrain is untouched");
        assertEquals(stock.getPatternLoadCueSchedule(), loaded.getPatternLoadCueSchedule());
        fixture.stepIdleFrames(1);
        CheckpointState checkpoint = assertInstanceOf(CheckpointState.class, GameServices.level().getCheckpointState());
        assertFalse(checkpoint.isActive());
        for (int tick = 0; tick < 90 && (!checkpoint.isActive() || fixture.sprite().getRingCount() == 0); tick++) {
            fixture.stepFrame(false, false, false, true, false);
        }
        assertTrue(checkpoint.isActive());
        assertEquals(2, checkpoint.getLastCheckpointIndex());
        assertFalse(fixture.sprite().getDead());
        assertTrue(fixture.sprite().getRingCount() >= 1, "recovery uses real native ring collection");
        assertTrue(findings.snapshot().isEmpty());
    }
}
