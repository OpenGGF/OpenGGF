package com.openggf.tests;

import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.objects.GumballMachineObjectInstance;
import com.openggf.game.sonic3k.objects.PachinkoEnergyTrapObjectInstance;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.trace.TraceData;
import com.openggf.trace.replay.TraceReplaySessionBootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Actual admitted pre-row bonus setup; no compared row or auxiliary object state is hydrated. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kBonusTraceInitialSetup {
    private TraceReplaySessionBootstrap.ConfigSnapshot saved;
    @BeforeEach void saveConfiguration() {
        saved = TraceReplaySessionBootstrap.snapshotGameplayConfig();
        SonicConfigurationService.getInstance().clearSessionOverrides();
    }
    @AfterEach void restoreConfiguration() {
        TestEnvironment.resetAll();
        TraceReplaySessionBootstrap.restoreGameplayConfig(saved);
    }

    @ParameterizedTest @ValueSource(strings = {"bonus_gumball", "bonus_pachinko"})
    void bonusEntryRetainsTheRealInitialPassBeforeRecordedRngPublication(String fixtureName) throws Exception {
        var trace = TraceData.load(Path.of("src/test/resources/traces/s3k", fixtureName));
        var meta = trace.metadata();
        TraceReplaySessionBootstrap.prepareConfiguration(trace, meta);
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(meta.zoneId(), 0)
                .startPosition(meta.startX(), meta.startY()).startPositionIsCentre()
                .withFreshLevelStartLifecycle().build();
        TraceReplaySessionBootstrap.applyStartPositionAndGroundSnap(trace, fixture);
        var level = GameServices.level();
        assertTrue(level.hasPendingInitialProcessSpritesPass(), "fresh production assembly owns the setup token");
        assertTrue(TraceReplaySessionBootstrap.applyBonusStageEntry(trace));
        int logicalFrames = level.getFrameCounter();
        int inputFrames = fixture.frameCount();
        int nativeVint = level.getObjectManager().getVblaCounter();
        assertTrue(level.consumePendingInitialProcessSpritesPass(),
                "Level loc_6468 must actually initialize every admitted SST before the first LevelLoop row");
        assertFalse(level.hasPendingInitialProcessSpritesPass());
        assertEquals(logicalFrames, level.getFrameCounter(), "setup spends no native Level_frame_counter tick");
        assertEquals(inputFrames, fixture.frameCount(), "setup consumes no recorded controller row");
        assertEquals(nativeVint, level.getObjectManager().getVblaCounter(), "setup spends no VBlank");
        if (fixtureName.equals("bonus_gumball")) {
            var machine = level.getObjectManager().activeObjectsOfType(GumballMachineObjectInstance.class).getFirst();
            var initialized = GumballMachineObjectInstance.class.getDeclaredField("driftInitialized");
            initialized.setAccessible(true);assertTrue(initialized.getBoolean(machine));
            assertTrue(level.getObjectManager().getActiveObjects().stream().anyMatch(object ->
                    object.getClass().getSimpleName().equals("ContainerDisplayChild")), "real machine initialization created its graph");
            TraceReplaySessionBootstrap.applyInitialRngSeedForReplay(meta);
            long capturedEntrySeed = GameServices.rng().getSeed();
            assertEquals(meta.initialRngSeed().longValue(), capturedEntrySeed);
            machine.update(trace.initialVblankCounter(), null);
            assertEquals(capturedEntrySeed, GameServices.rng().getSeed(),
                    "first driven ordinary machine pass cannot overwrite the declared post-setup RNG seed");
        } else {
            var trap = level.getObjectManager().activeObjectsOfType(PachinkoEnergyTrapObjectInstance.class).getFirst();
            assertEquals(1, trap.getUpdateCount(), "native setup dispatches the injected trap exactly once");
            assertEquals(239, trap.getRiseDelayFrames(), "first native setup owns the unsigned F0 countdown decrement");
        }
    }
}
