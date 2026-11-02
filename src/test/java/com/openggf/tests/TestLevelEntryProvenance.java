package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.game.session.GameplayTeamBootstrap;
import com.openggf.game.session.LevelEntryProvenance;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.trace.TraceData;
import com.openggf.trace.TraceReplayBootstrap;
import com.openggf.trace.replay.TraceReplaySessionBootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class TestLevelEntryProvenance {
    private HeadlessTestFixture fixture;
    private final TraceReplaySessionBootstrap.ConfigSnapshot saved =
            TraceReplaySessionBootstrap.snapshotGameplayConfig();

    @AfterEach void restore() {
        // These checks stop at setup, before driving the movie's timing edges.
        // Abort the bounded replay and immediately destroy its owning context.
        if (fixture != null) fixture.abortHardwareTimingReplayRun();
        TestEnvironment.resetAll();
        TraceReplaySessionBootstrap.restoreGameplayConfig(saved);
    }

    @Test @RequiresRom(SonicGame.SONIC_2)
    void declaredNativeLoadKeepsTheRomStartHistoryAndIgnoresDescriptiveCoordinates() throws Exception {
        var trace = TraceData.load(Path.of("src/test/resources/traces/kis2/ehz1"));
        TraceReplaySessionBootstrap.prepareConfiguration(trace, trace.metadata());
        com.openggf.tests.trace.TraceReplayTestSession.reopenForRecordedTeam(trace.metadata());
        // The production caller can declare a native load. The fixture itself
        // lacks recorder-observed placement provenance and stays unclassified.
        assertEquals(LevelEntryProvenance.UNCLASSIFIED_ENTRY,
                TraceReplayBootstrap.entryProvenanceForTraceReplay(trace));
        assertTrue(TraceReplayBootstrap.shouldApplyMetadataStartPositionForTraceReplay(trace));
        var origin = LevelEntryProvenance.NATIVE_LEVEL_START;
        fixture = HeadlessTestFixture.builder().withZoneAndAct(0, 0)
                .withEntryProvenance(origin)
                .startPosition((short) 0x700, (short) 0x700).startPositionIsCentre().build();
        var player = fixture.sprite();
        assertEquals(0x290, player.getCentreY());
        for (short y : player.copyYHistory()) assertEquals(0x293, y & 0xFFFF);
        var beforeX = player.copyXHistory();
        var beforeY = player.copyYHistory();
        int cursor = player.historyPos();
        int cameraY = fixture.camera().getY();
        TraceReplaySessionBootstrap.applyPostLoadLevelInit(trace, origin);
        assertEquals(cameraY, fixture.camera().getY());
        GameplayTeamBootstrap.repositionRegisteredSidekicks(GameServices.module(), GameServices.level(), origin);
        assertArrayEquals(beforeX, player.copyXHistory());
        assertArrayEquals(beforeY, player.copyYHistory());
        assertEquals(cursor, player.historyPos());
        // An intentional reposition changes the body, never the native-init history.
        player.setCentreY((short) 0x500);
        GameplayTeamBootstrap.repositionRegisteredSidekicks(GameServices.module(), GameServices.level(),
                LevelEntryProvenance.POSITIONED_ENTRY);
        assertArrayEquals(beforeY, player.copyYHistory());
        assertEquals(cursor, player.historyPos());
    }

    @Test
    void timingPreludeDoesNotDeclareStartTableOrReturnPlacement() throws Exception {
        for (String path : new String[] {
                "kis2/ehz1", "s2/runs/s2-ehz-halfpipe-roundtrip/seg2_ehz1" }) {
            var trace = TraceData.load(Path.of("src/test/resources/traces").resolve(path));
            assertTrue(trace.metadata().hasNativePreludeBootstrap());
            assertEquals(LevelEntryProvenance.UNCLASSIFIED_ENTRY,
                    TraceReplayBootstrap.entryProvenanceForTraceReplay(trace));
            assertTrue(TraceReplayBootstrap.shouldApplyMetadataStartPositionForTraceReplay(trace));
        }
    }

    @Test @RequiresRom(SonicGame.SONIC_3K)
    void shrineLockSurvivesInitialPlayableAssemblyAndNativeReanchoring() throws Exception {
        var trace = TraceData.load(Path.of(
                "src/test/resources/traces/s3k/runs/s3k-sonic-tails-complete-emeralds/dez23_2"));
        var meta = trace.metadata();
        TraceReplaySessionBootstrap.prepareConfiguration(trace, meta);
        fixture = HeadlessTestFixture.builder().withZoneAndAct(meta.zoneId(), 1)
                .withHardwareReadinessAdmissionPolicy(trace.hardwareTimingSchedule().hasRecordedInput()
                        ? com.openggf.game.timing.HardwareReadinessAdmissionPolicy.RECORDED
                        : com.openggf.game.timing.HardwareReadinessAdmissionPolicy.LIVE)
                .withEntryProvenance(TraceReplayBootstrap.entryProvenanceForTraceReplay(trace))
                .startPosition(meta.startX(), meta.startY()).startPositionIsCentre()
                .withFreshLevelStartLifecycle().build();
        TraceReplaySessionBootstrap.applyStartPositionAndGroundSnap(trace, fixture);
        TraceReplaySessionBootstrap.applyBootstrap(trace, fixture, -1);
        var tails = GameServices.sprites().getRegisteredSidekicks().getFirst();
        // sub_90A4C (sonic3k.asm:197928-197936) writes both native participants.
        assertEquals(0x1640, tails.getCentreX());
        assertEquals(0x3A3, tails.getCentreY());
        assertTrue(tails.isObjectControlSuppressesMovement());
        GameplayTeamBootstrap.repositionRegisteredSidekicks(GameServices.module(), GameServices.level(),
                LevelEntryProvenance.NATIVE_LEVEL_START);
        assertEquals(0x1640, tails.getCentreX());
        assertEquals(0x3A3, tails.getCentreY());
        // A caller explicitly choosing positioned entry does move the whole
        // team, even if a cold intro had acquired the old sidekick body.
        var history = fixture.sprite().copyYHistory();
        GameplayTeamBootstrap.repositionRegisteredSidekicks(GameServices.module(), GameServices.level(),
                LevelEntryProvenance.POSITIONED_ENTRY);
        assertEquals(0x1620, tails.getCentreX());
        assertEquals(0x3A7, tails.getCentreY());
        assertArrayEquals(history, fixture.sprite().copyYHistory());
    }

    @Test @RequiresRom(SonicGame.SONIC_3K)
    void explicitPostSpawnFixtureUsesDeclaredBodyAndPreSpawnCamera() {
        var config = com.openggf.configuration.SonicConfigurationService.getInstance();
        config.setConfigValue(com.openggf.configuration.SonicConfiguration.MAIN_CHARACTER_CODE, "tails");
        config.setConfigValue(com.openggf.configuration.SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(com.openggf.game.sonic3k.constants.Sonic3kZoneIds.ZONE_GUMBALL, 0)
                .withEntryProvenance(LevelEntryProvenance.POSITIONED_FRESH_SPAWN)
                .startPosition((short) 0x100, (short) 0x124).startPositionIsCentre()
                .withFreshLevelStartLifecycle().build();
        assertEquals(0x124, fixture.sprite().getCentreY());
        assertEquals(0xC0, GameServices.camera().getY());
    }
}
