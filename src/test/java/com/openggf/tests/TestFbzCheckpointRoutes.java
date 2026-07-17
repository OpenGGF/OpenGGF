package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.CheckpointState;
import com.openggf.game.GameServices;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.runtime.FbzZoneRuntimeState;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Production-load and death-reload coverage for every ROM-authored FBZ starpost. */
@RequiresRom(SonicGame.SONIC_3K)
class TestFbzCheckpointRoutes {
    private static final List<CheckpointPlacement> AUTHORED_CHECKPOINTS = List.of(
            new CheckpointPlacement(0, 1, 0x0DC8, 0x07EC, false),
            new CheckpointPlacement(0, 2, 0x1380, 0x036C, false),
            new CheckpointPlacement(0, 3, 0x1CC0, 0x026C, false),
            new CheckpointPlacement(0, 4, 0x1FC0, 0x086C, false),
            new CheckpointPlacement(0, 5, 0x2D70, 0x05EC, false),
            new CheckpointPlacement(1, 1, 0x09B0, 0x0A6C, false),
            new CheckpointPlacement(1, 2, 0x13C0, 0x046C, false),
            new CheckpointPlacement(1, 3, 0x1930, 0x0A6C, false),
            new CheckpointPlacement(1, 4, 0x1BB8, 0x076C, false),
            new CheckpointPlacement(1, 5, 0x28E8, 0x0B6C, false),
            new CheckpointPlacement(1, 6, 0x2D20, 0x066C, false));

    @Test
    void romDecodedActsExposeTheCompleteAuthoredStarpostSet() {
        List<CheckpointPlacement> checkpoints = Stream.of(0, 1)
                .flatMap(act -> {
                    HeadlessTestFixture.builder()
                            .withZoneAndAct(Sonic3kZoneIds.ZONE_FBZ, act)
                            .build();
                    return GameServices.level().getCurrentLevel().getObjects().stream()
                            .filter(spawn -> spawn.objectId() == Sonic3kObjectIds.STAR_POST)
                            .map(spawn -> placement(act, spawn));
                })
                .sorted(Comparator.comparingInt(CheckpointPlacement::act)
                        .thenComparingInt(CheckpointPlacement::index))
                .toList();

        assertEquals(AUTHORED_CHECKPOINTS, checkpoints,
                "FBZ checkpoint coverage must change when the ROM placement inventory changes");
    }

    @ParameterizedTest(name = "{0} FBZ{1} starpost {2} death reload")
    @MethodSource("checkpointTeamCases")
    void everyNativeTeamDeathReloadsAtEverySupportedCheckpoint(
            Team team, int act, int checkpointIndex, CheckpointPlacement checkpoint) {
        ConfigSnapshot config = configure(team);
        try {
            HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                    .withZoneAndAct(Sonic3kZoneIds.ZONE_FBZ, act)
                    .build();
            ObjectManager beforeReload = GameServices.level().getObjectManager();
            var owningSession = fixture.gameplayMode();
            List<?> sidekickIdentities = List.copyOf(GameServices.sprites().getSidekicks());
            CheckpointState state = assertInstanceOf(
                    CheckpointState.class, GameServices.level().getCheckpointState());
            int savedCameraX = Math.max(0, checkpoint.x() - 0xA0);
            int savedCameraY = Math.max(0, checkpoint.y() - 0x60);

            // This is the production saved-starpost intake used after a load has
            // rebuilt the level. The ensuing respawn runs the ordinary full
            // death reload; no player/object coordinate is written by the test.
            state.restoreFromSaved(checkpoint.x(), checkpoint.y(),
                    savedCameraX, savedCameraY, checkpointIndex);
            state.saveS3kRuntimeState(fixture.camera().getMaxY(), 0);
            state.saveSolidBits(fixture.sprite().getTopSolidBit(), fixture.sprite().getLrbSolidBit());
            GameServices.level().respawnPlayer();

            CheckpointState restored = assertInstanceOf(
                    CheckpointState.class, GameServices.level().getCheckpointState());
            assertNotSame(beforeReload, GameServices.level().getObjectManager(),
                    "death reload must rebuild the object/SST owner");
            assertSame(owningSession, SessionManager.getCurrentGameplayMode());
            assertEquals(Sonic3kZoneIds.ZONE_FBZ, GameServices.level().getCurrentZone());
            assertEquals(act, GameServices.level().getCurrentAct());
            assertEquals(act, GameServices.level().getApparentAct());
            assertTrue(restored.isActive());
            assertEquals(checkpointIndex, restored.getLastCheckpointIndex());
            assertEquals(checkpoint.x(), fixture.sprite().getCentreX() & 0xFFFF);
            assertEquals(checkpoint.y(), fixture.sprite().getCentreY() & 0xFFFF);
            assertEquals(savedCameraX, fixture.camera().getX() & 0xFFFF);
            assertEquals(Math.min(savedCameraY, fixture.camera().getMaxY() & 0xFFFF),
                    fixture.camera().getY() & 0xFFFF,
                    "checkpoint restart camera must honor the game-owned FBZ vertical clamp");
            assertFalse(fixture.sprite().getDead());
            assertEquals(0, fixture.sprite().getDeathCountdown());
            assertEquals(sidekickIdentities, GameServices.sprites().getSidekicks(),
                    "a death reload must retain the configured native team identities");

            Sonic3kLevelEventManager events = assertInstanceOf(
                    Sonic3kLevelEventManager.class, GameServices.module().getLevelEventProvider());
            assertEquals(team.playerCharacter(), events.getPlayerCharacter());
            FbzZoneRuntimeState runtime = assertInstanceOf(
                    FbzZoneRuntimeState.class, GameServices.zoneRuntimeRegistry().current());
            assertEquals(act, runtime.actIndex());
            assertTrue(runtime.isBackedBy(events.getFbzEvents()),
                    "death reload must rebind FBZ runtime state to the replacement event owner");
        } finally {
            restore(config);
        }
    }

    private static Stream<Arguments> checkpointTeamCases() {
        return AUTHORED_CHECKPOINTS.stream().flatMap(checkpoint ->
                Stream.of(Team.values()).map(team -> Arguments.of(
                        team, checkpoint.act(), checkpoint.index(), checkpoint)));
    }

    private static CheckpointPlacement placement(int act, ObjectSpawn spawn) {
        return new CheckpointPlacement(
                act, spawn.subtype() & 0x7F, spawn.x(), spawn.y(),
                (spawn.subtype() & 0x80) != 0);
    }

    private static ConfigSnapshot configure(Team team) {
        SonicConfigurationService configuration = SonicConfigurationService.getInstance();
        ConfigSnapshot snapshot = new ConfigSnapshot(
                configuration.getString(SonicConfiguration.MAIN_CHARACTER_CODE),
                configuration.getString(SonicConfiguration.SIDEKICK_CHARACTER_CODE));
        configuration.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, team.main());
        configuration.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, team.sidekicks());
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
        return snapshot;
    }

    private static void restore(ConfigSnapshot snapshot) {
        SonicConfigurationService configuration = SonicConfigurationService.getInstance();
        configuration.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE,
                snapshot.main() == null ? "sonic" : snapshot.main());
        configuration.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE,
                snapshot.sidekicks() == null ? "tails" : snapshot.sidekicks());
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
    }

    private enum Team {
        SONIC("sonic", "", PlayerCharacter.SONIC_ALONE),
        TAILS("tails", "", PlayerCharacter.TAILS_ALONE),
        SONIC_TAILS("sonic", "tails", PlayerCharacter.SONIC_AND_TAILS),
        KNUCKLES("knuckles", "", PlayerCharacter.KNUCKLES);

        private final String main;
        private final String sidekicks;
        private final PlayerCharacter playerCharacter;

        Team(String main, String sidekicks, PlayerCharacter playerCharacter) {
            this.main = main;
            this.sidekicks = sidekicks;
            this.playerCharacter = playerCharacter;
        }

        String main() { return main; }
        String sidekicks() { return sidekicks; }
        PlayerCharacter playerCharacter() { return playerCharacter; }
    }

    private record CheckpointPlacement(int act, int index, int x, int y, boolean cameraLock) { }
    private record ConfigSnapshot(String main, String sidekicks) { }
}
