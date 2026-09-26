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
import com.openggf.game.sonic3k.runtime.MhzZoneRuntimeState;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Production-load and death-reload coverage for every ROM-authored MHZ starpost. */
@RequiresRom(SonicGame.SONIC_3K)
class TestMhzCheckpointRoutes {
    // Independent placement oracle: skdisasm Levels/MHZ/Object Pos/{1,2}.bin.
    // Scripted checkpoints (including Act 2 admission index 7) are not physical posts.
    private static final List<CheckpointPlacement> AUTHORED_CHECKPOINTS = List.of(
            new CheckpointPlacement(0, 2, 0x1D60, 0x01A8, false),
            new CheckpointPlacement(0, 3, 0x27C0, 0x05A8, false),
            new CheckpointPlacement(0, 4, 0x2D40, 0x0428, false),
            new CheckpointPlacement(0, 5, 0x3000, 0x02A8, false),
            new CheckpointPlacement(0, 6, 0x3EE0, 0x06A8, false),
            new CheckpointPlacement(1, 9, 0x1778, 0x07E8, false),
            new CheckpointPlacement(1, 11, 0x1E78, 0x04A8, false),
            new CheckpointPlacement(1, 12, 0x2A98, 0x07A8, false),
            new CheckpointPlacement(1, 13, 0x3AF8, 0x02E8, false));

    @Test
    void romDecodedActsExposeTheCompleteAuthoredStarpostSet() {
        List<CheckpointPlacement> checkpoints = Stream.of(0, 1)
                .flatMap(act -> {
                    HeadlessTestFixture.builder()
                            .withZoneAndAct(Sonic3kZoneIds.ZONE_MHZ, act)
                            .build();
                    return GameServices.level().getCurrentLevel().getObjects().stream()
                            .filter(spawn -> spawn.objectId() == Sonic3kObjectIds.STAR_POST)
                            .map(spawn -> placement(act, spawn));
                })
                .sorted(Comparator.comparingInt(CheckpointPlacement::act)
                        .thenComparingInt(CheckpointPlacement::index))
                .toList();

        assertEquals(AUTHORED_CHECKPOINTS, checkpoints,
                "MHZ checkpoint coverage must change when the ROM placement inventory changes");
    }

    @ParameterizedTest(name = "{0} MHZ{1} starpost {2} physical activation")
    @MethodSource("checkpointTeamCases")
    void approachingEveryPlacedPostActivatesAndReplaysItsSave(
            Team team, int act, int checkpointIndex, CheckpointPlacement checkpoint) {
        ConfigSnapshot config = configure(team);
        try {
            // Declared local approach, not evidence of a cold traversal to this post.
            // Use the ROM placement and production player/object updates; never seed
            // CheckpointState or call the post's activation/save routines.
            var fixture = HeadlessTestFixture.builder()
                    .withZoneAndAct(Sonic3kZoneIds.ZONE_MHZ, act)
                    .startPosition((short) (checkpoint.approachX()), (short) checkpoint.approachY())
                    .startPositionIsCentre().withFreshLevelStartLifecycle().build();
            // Admit the initial gameplay frame before taking a rewind sample:
            // boot's setup-only state is not a live history point.
            fixture.stepIdleFrames(1);
            var state = assertInstanceOf(CheckpointState.class, GameServices.level().getCheckpointState());
            assertFalse(state.isActive());
            var registry = fixture.gameplayMode().getRewindRegistry();
            var before = registry.capture();
            for (int frame = 0; frame < checkpoint.approachFrames(); frame++) {
                fixture.stepFrame(false, false, false, true, checkpoint.jump(frame));
            }
            assertEquals(checkpointIndex, state.getLastCheckpointIndex(),
                    "ordinary movement must contact the ROM-placed post; player=" + fixture.sprite().getCentreX()
                            + "," + fixture.sprite().getCentreY() + " dead=" + fixture.sprite().getDead());
            assertTrue(state.isActive());
            var after = registry.capture();
            for (int cycle = 0; cycle < 2; cycle++) {
                registry.restore(before);
                sameCheckpointWorld(before, registry.capture(), "restore cycle " + cycle);
                fixture.runner().primeInputState(new com.openggf.debug.playback.Bk2FrameInput(0, 0, 0, false, ""));
                for (int frame = 0; frame < checkpoint.approachFrames(); frame++) {
                    fixture.stepFrame(false, false, false, true, checkpoint.jump(frame));
                }
                sameCheckpointWorld(after, registry.capture(), "replay cycle " + cycle);
            }
        } finally {
            restore(config);
        }
    }

    @ParameterizedTest(name = "{0} MHZ{1} physical post {2}, width {4}, repeated death")
    @MethodSource("checkpointLifecycleCases")
    void physicalCheckpointSurvivesTwoRealDeathReloads(Team team, int act, int index,
            CheckpointPlacement checkpoint, int width) {
        runPhysicalCheckpointLifecycle(team, act, index, checkpoint, width, "off");
    }

    @ParameterizedTest(name = "{5} {0} MHZ{1} physical post {2}, width {4}, repeated death")
    @MethodSource("donorCheckpointLifecycleCases")
    void supportedDonorTeamsKeepTheirRulesAndArtAcrossPhysicalCheckpointDeaths(Team team,
            int act, int index, CheckpointPlacement checkpoint, int width, String donor) {
        runPhysicalCheckpointLifecycle(team, act, index, checkpoint, width, donor);
    }

    private void runPhysicalCheckpointLifecycle(Team team, int act, int index,
            CheckpointPlacement checkpoint, int width, String donor) {
        ConfigSnapshot savedConfig = configure(team);
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED, true);
        config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, !donor.equals("off"));
        config.setSessionOverride(SonicConfiguration.CROSS_GAME_SOURCE, donor);
        if (!donor.equals("off")) {
            var rom = donor.equals("s1") ? RomTestUtils.ensureSonic1RomAvailable() : RomTestUtils.ensureSonic2RomAvailable();
            config.setSessionOverride(donor.equals("s1") ? SonicConfiguration.SONIC_1_ROM : SonicConfiguration.SONIC_2_ROM,
                    rom.getAbsolutePath());
        }
        var aspect = java.util.Arrays.stream(com.openggf.configuration.WidescreenAspect.values())
                .filter(value -> value.pixelWidth() == width).findFirst().orElseThrow();
        // Use a real preset and create the gameplay camera only after resolving it.
        // configure(team) has already opened a native-width session for older tests.
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, aspect.name());
        config.resolveDisplayAspect();
        config.setSessionOverride(SonicConfiguration.DISCORD_RICH_PRESENCE_ENABLED, false);
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
        try {
            // Local ROM-placed post approach, not a cold route. No saved-post state
            // is injected. Death itself is a declared production pit-death stimulus.
            var builder = HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_MHZ, act)
                    .startPosition((short) (checkpoint.approachX()), (short) checkpoint.approachY())
                    .startPositionIsCentre().withFreshLevelStartLifecycle();
            if (!donor.equals("off")) builder.withCrossGameDonation(donor);
            var f = builder.build();
            // Match the live approach used by the rewind case: the first dispatch
            // establishes grounded/input state before the first jump edge.
            f.stepIdleFrames(1);
            assertDonorState(donor);
            assertEquals(width, GameServices.camera().getWidth() & 0xFFFF, "initial configured viewport");
            for (int n = 0; n < checkpoint.approachFrames() && !GameServices.level().getCheckpointState().isActive(); n++)
                f.stepFrame(false, false, false, true, checkpoint.jump(n));
            var post = assertInstanceOf(CheckpointState.class, GameServices.level().getCheckpointState());
            assertTrue(post.isActive()); assertEquals(index, post.getLastCheckpointIndex());
            int savedX = post.getSavedX(), savedY = post.getSavedY();
            var followers = List.copyOf(GameServices.sprites().getRegisteredSidekicks());
            var input = new com.openggf.control.InputHandler();
            var neutral = new com.openggf.debug.playback.Bk2FrameInput(0, 0, 0, false, "");
            input.setLogicalOverride(com.openggf.debug.playback.RecordedInputSnapshots.fromBk2(neutral, neutral));
            var loop = new com.openggf.GameLoop(input);
            loop.setGameplayMode(f.gameplayMode()); loop.setGameMode(com.openggf.game.GameMode.LEVEL);
            try {
                for (int cycle = 0; cycle < 2; cycle++) {
                    for (int n = 0; n < 30; n++) {f.gameplayMode().getFadeManager().update();loop.step();}
                    var objects = GameServices.level().getObjectManager();
                    var runtime = GameServices.zoneRuntimeRegistry().current();
                    assertTrue(GameServices.sprites().getMainPlayable().applyPitDeath());
                    long outgoing = 0; boolean loaded = false;
                    for (int n = 0; n < 1200; n++) {
                        f.gameplayMode().getFadeManager().update(); loop.step();
                        var rewind = f.gameplayMode().getRewindController();
                        if (GameServices.level().getObjectManager() != objects) {
                            loaded = true;
                            assertEquals(Sonic3kZoneIds.ZONE_MHZ, GameServices.level().getCurrentZone());
                            assertEquals(act, GameServices.level().getCurrentAct());
                            assertEquals(index, GameServices.level().getCheckpointState().getLastCheckpointIndex());
                            var player = GameServices.sprites().getMainPlayable();
                            assertEquals(savedX, player.getCentreX() & 0xFFFF);
                            assertEquals(savedY, player.getCentreY() & 0xFFFF);
                            assertEquals(team.main(), player.getCode());
                            assertEquals(followers, GameServices.sprites().getRegisteredSidekicks());
                            assertEquals(width, GameServices.camera().getWidth() & 0xFFFF);
                            assertDonorState(donor);
                            assertNotSame(runtime, GameServices.zoneRuntimeRegistry().current());
                            var next = assertInstanceOf(MhzZoneRuntimeState.class, GameServices.zoneRuntimeRegistry().current());
                            var events = assertInstanceOf(Sonic3kLevelEventManager.class, GameServices.module().getLevelEventProvider());
                            assertTrue(next.isBackedBy(events.getMhzEvents()));
                            assertEquals(team.playerCharacter(), events.getPlayerCharacter());
                            assertTrue(outgoing > 10, "the outgoing timeline must exist");
                            assertTrue(rewind == null || rewind.currentFrame() < outgoing,
                                    "death reload must isolate outgoing history");
                            break;
                        }
                        if (rewind != null) outgoing = Math.max(outgoing, rewind.currentFrame());
                    }
                    assertTrue(loaded, "real GameLoop death reload cycle " + cycle);
                    boolean released = false;
                    for (int n = 0; n < 500; n++) {
                        f.gameplayMode().getFadeManager().update(); loop.step();
                        var title = GameServices.module().getTitleCardProvider();
                        if (loop.getCurrentGameMode() == com.openggf.game.GameMode.LEVEL
                                && (title == null || title.isComplete())
                                && !f.gameplayMode().getFadeManager().isActive()
                                && !GameServices.sprites().getMainPlayable().isControlLocked()) {
                            released = true; break;
                        }
                    }
                    assertTrue(released, "title/fade must release controls");
                    assertFalse(GameServices.sprites().getMainPlayable().getDead());
                    assertDonorState(donor);
                }
            } finally {loop.closePresence();}
        } finally {config.clearSessionOverrides(); restore(savedConfig);}
    }

    private static Stream<Arguments> checkpointLifecycleCases() {
        return AUTHORED_CHECKPOINTS.stream().flatMap(post -> Stream.of(Team.values())
                .flatMap(team -> Stream.of(320, 352, 400, 528, 800)
                        .map(width -> Arguments.of(team, post.act(), post.index(), post, width))));
    }

    private static Stream<Arguments> donorCheckpointLifecycleCases() {
        return Stream.of("s1", "s2").flatMap(donor -> Stream.of(Team.values()).filter(team -> {
            var profile = new com.openggf.game.launch.LaunchProfile(false, donor, false, "global",
                    team.main(), team.sidekicks().isEmpty() ? "none" : team.sidekicks());
            return profile.equals(profile.sanitizedFor(com.openggf.game.MasterTitleScreen.GameEntry.SONIC_3K));
        }).flatMap(team -> AUTHORED_CHECKPOINTS.stream().flatMap(post -> Stream.of(320, 352, 400, 528, 800)
                .map(width -> Arguments.of(team, post.act(), post.index(), post, width, donor)))));
    }

    private static void assertDonorState(String donor) {
        boolean active = !donor.equals("off");
        assertEquals(active, com.openggf.game.CrossGameFeatureProvider.isActive());
        if (active) assertEquals(donor, com.openggf.game.CrossGameFeatureProvider.getInstance().getDonorGameId());
        var leader = GameServices.sprites().getMainPlayable();
        assertEquals(!donor.equals("s1"), leader.getGameRules().playerCapability().spindashEnabled());
        var participants = new java.util.ArrayList<com.openggf.sprites.playable.AbstractPlayableSprite>();
        participants.add(leader); participants.addAll(GameServices.sprites().getRegisteredSidekicks());
        for (var player : participants) {
            org.junit.jupiter.api.Assertions.assertNotNull(player.getSpriteRenderer(), "ROM-backed participant renderer");
            org.junit.jupiter.api.Assertions.assertNotNull(player.getAnimationProfile());
            org.junit.jupiter.api.Assertions.assertNotNull(player.getAnimationSet());
            assertTrue(player.getAnimationFrameCount() > 0, "decoded participant mappings");
        }
    }

    private static void sameCheckpointWorld(com.openggf.game.rewind.CompositeSnapshot expected,
            com.openggf.game.rewind.CompositeSnapshot actual, String where) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet(), where);
        for (var key : expected.entries().keySet()) {
            var differences = com.openggf.game.rewind.RewindSnapshotDiff.diffKey(
                    key, expected.get(key), actual.get(key));
            assertTrue(differences.isEmpty(), where + " " + key + ": "
                    + differences.stream().limit(8).toList());
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
        SONIC_TAILS("sonic", "tails", PlayerCharacter.SONIC_AND_TAILS);

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

    private record CheckpointPlacement(int act, int index, int x, int y, boolean cameraLock) {
        // The last post uses the upper-passage jumping approach also exercised by
        // TestS3kMhzEndBossAdmission (15008,704). A flat post-height approach
        // skips the corridor transition; farther left it falls to the lower route.
        // These are declared local input setups, not cold-route reachability.
        int approachX() { return x - (act == 1 && index == 13 ? 88 : 48); }
        int approachY() { return y - (act == 1 && index == 13 ? 40 : 0); }
        boolean jump(int frame) { return act == 1 && index == 13 && frame % 40 < 20; }
        int approachFrames() { return act == 1 && index == 13 ? 180 : 90; }
    }
    private record ConfigSnapshot(String main, String sidekicks) { }
}
