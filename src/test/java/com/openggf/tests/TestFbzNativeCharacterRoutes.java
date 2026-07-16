package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputActionMasks;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.GameServices;
import com.openggf.game.CheckpointState;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.levelselect.Sonic3kLevelSelectManager;
import com.openggf.game.sonic3k.objects.FbzEndBossInstance;
import com.openggf.game.sonic3k.runtime.FbzZoneRuntimeState;
import com.openggf.sprites.playable.Knuckles;
import com.openggf.sprites.playable.Sonic;
import com.openggf.sprites.playable.Tails;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Eight native-team act starts and three bounded solo-character boss entries. */
@RequiresRom(SonicGame.SONIC_3K)
class TestFbzNativeCharacterRoutes {
    /**
     * Immutable input from authored FBZ2 starpost 6 through the plane event and
     * into the real end-boss graph. Task 17 owns the strict uninterrupted
     * Sonic+Tails FBZ2-to-SOZ completion oracle; this route only proves that
     * each solo native character can participate in the production graph.
     */
    private static final List<InputRun> TERMINAL_BOSS_ENTRY = List.of(
            new InputRun(600, 0x0), new InputRun(300, 0x8),
            new InputRun(20, 0x18), new InputRun(300, 0x8),
            new InputRun(800, 0x0), new InputRun(60, 0x8),
            new InputRun(2140, 0x0), new InputRun(20, 0x18),
            new InputRun(100, 0x8));
    private static final List<InputRun> TAILS_TERMINAL_BOSS_ENTRY = List.of(
            new InputRun(600, 0x0), new InputRun(300, 0x8),
            new InputRun(20, 0x18), new InputRun(300, 0x8),
            new InputRun(800, 0x0), new InputRun(120, 0x4),
            new InputRun(2140, 0x0), new InputRun(20, 0x18),
            new InputRun(100, 0x8));

    @ParameterizedTest(name = "{0} level-select FBZ{1} normal start")
    @MethodSource("teamActCases")
    void everyNativeTeamCanEnterBothActsFromLevelSelectAtTheRomStart(Team team, int act) {
        ConfigSnapshot config = configure(team);
        try {
            Sonic3kLevelSelectManager levelSelect = selectFbzAct(act);
            assertTrue(levelSelect.isExiting());
            assertEquals(Sonic3kZoneIds.ZONE_FBZ, levelSelect.getSelectedZone());
            assertEquals(act, levelSelect.getSelectedAct());

            HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                    .withZoneAndAct(levelSelect.getSelectedZone(), levelSelect.getSelectedAct())
                    .build();
            int[] start = GameServices.module().getZoneRegistry()
                    .getStartPosition(Sonic3kZoneIds.ZONE_FBZ, act);

            assertInstanceOf(team.mainType(), fixture.sprite());
            assertEquals(team.main(), fixture.sprite().getCode());
            assertEquals(start[0], fixture.sprite().getCentreX() & 0xFFFF,
                    "ROM x_pos is the playable centre coordinate");
            assertEquals(start[1] + team.groundSnapYOffset(),
                    fixture.sprite().getCentreY() & 0xFFFF,
                    "the headless fixture's production ground probe must settle each native radius");
            assertEquals(team.sidekickCount(), GameServices.sprites().getSidekicks().size());
            if (team.sidekickCount() == 1) {
                assertInstanceOf(Tails.class, GameServices.sprites().getSidekicks().getFirst());
                assertEquals("tails_p2", GameServices.sprites().getSidekicks().getFirst().getCode());
            }

            Sonic3kLevelEventManager events = assertInstanceOf(
                    Sonic3kLevelEventManager.class, GameServices.module().getLevelEventProvider());
            assertEquals(team.playerCharacter(), events.getPlayerCharacter());
            FbzZoneRuntimeState runtime = assertInstanceOf(
                    FbzZoneRuntimeState.class, GameServices.zoneRuntimeRegistry().current());
            assertEquals(act, runtime.actIndex());
            assertEquals(team.playerCharacter(), runtime.playerCharacter());
            fixture.stepIdleFrames(2);
            assertFalse(fixture.sprite().getDead());
            assertEquals(Sonic3kZoneIds.ZONE_FBZ, GameServices.level().getCurrentZone());
            assertEquals(act, GameServices.level().getCurrentAct());
        } finally {
            restore(config);
        }
    }

    @ParameterizedTest(name = "{0} starpost 6 reaches the native terminal boss graph")
    @MethodSource("soloTeams")
    void everySoloNativeCharacterCanEnterTheTerminalBossGraphAlive(Team team) {
        ConfigSnapshot config = configure(team);
        try {
            runAuthoredTerminalBossEntry(team);
        } finally {
            restore(config);
        }
    }

    private static void runAuthoredTerminalBossEntry(Team team) {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_FBZ, 1)
                .build();
        ObjectSpawn checkpoint = GameServices.level().getCurrentLevel().getObjects().stream()
                .filter(spawn -> spawn.objectId() == GameServices.module().getCheckpointObjectId())
                .filter(spawn -> (spawn.subtype() & 0x7F) == 6)
                .findFirst().orElseThrow();
        CheckpointState state = (CheckpointState) GameServices.level().getCheckpointState();
        state.restoreFromSaved(checkpoint.x(), checkpoint.y(),
                checkpoint.x() - 0xA0, checkpoint.y() - 0x60, 6);
        GameServices.level().respawnPlayer();

        assertEquals(checkpoint.x(), fixture.sprite().getCentreX() & 0xFFFF);
        assertEquals(checkpoint.y(), fixture.sprite().getCentreY() & 0xFFFF);
        FbzEndBossInstance boss = null;
        int frame = 0;
        List<InputRun> route = team == Team.TAILS
                ? TAILS_TERMINAL_BOSS_ENTRY : TERMINAL_BOSS_ENTRY;
        outer:
        for (InputRun run : route) {
            for (int i = 0; i < run.frames(); i++, frame++) {
                fixture.stepFrame(
                        (run.mask() & com.openggf.sprites.playable.AbstractPlayableSprite.INPUT_UP) != 0,
                        (run.mask() & com.openggf.sprites.playable.AbstractPlayableSprite.INPUT_DOWN) != 0,
                        (run.mask() & com.openggf.sprites.playable.AbstractPlayableSprite.INPUT_LEFT) != 0,
                        (run.mask() & com.openggf.sprites.playable.AbstractPlayableSprite.INPUT_RIGHT) != 0,
                        (run.mask() & com.openggf.sprites.playable.AbstractPlayableSprite.INPUT_JUMP) != 0);
                List<FbzEndBossInstance> bosses = GameServices.level().getObjectManager()
                        .activeObjectsOfType(FbzEndBossInstance.class);
                if (!bosses.isEmpty()) {
                    boss = bosses.getFirst();
                    break outer;
                }
                if (fixture.sprite().getDead()) break outer;
            }
        }
        assertFalse(fixture.sprite().getDead(), team + " died before terminal participation at frame "
                + frame + " player=(0x"
                + Integer.toHexString(fixture.sprite().getCentreX() & 0xFFFF) + ",0x"
                + Integer.toHexString(fixture.sprite().getCentreY() & 0xFFFF) + ")");
        assertTrue(boss != null, team + " never entered the real end-boss graph; frame=" + frame
                + " player=(0x" + Integer.toHexString(fixture.sprite().getCentreX() & 0xFFFF)
                + ",0x" + Integer.toHexString(fixture.sprite().getCentreY() & 0xFFFF)
                + ") camera=(0x" + Integer.toHexString(fixture.camera().getX() & 0xFFFF)
                + ",0x" + Integer.toHexString(fixture.camera().getY() & 0xFFFF) + ")");
        assertEquals(FbzEndBossInstance.Phase.PRE_MUSIC, boss.phase(),
                "the route must observe the graph at its native first visible phase");
    }

    private static Sonic3kLevelSelectManager selectFbzAct(int act) {
        Sonic3kLevelSelectManager manager = new Sonic3kLevelSelectManager();
        InputHandler input = new InputHandler();
        manager.initialize();
        for (int i = 0; i < 16; i++) update(manager, input, PlayerInputState.neutral());
        int targetIndex = 8 + act;
        for (int i = 0; i < targetIndex; i++) {
            update(manager, input, PlayerInputState.of(
                    com.openggf.sprites.playable.AbstractPlayableSprite.INPUT_DOWN,
                    com.openggf.sprites.playable.AbstractPlayableSprite.INPUT_DOWN,
                    0, 0, false, false));
            update(manager, input, PlayerInputState.neutral());
        }
        assertEquals(targetIndex, manager.getSelectedIndex());
        update(manager, input, PlayerInputState.of(
                0, 0, InputActionMasks.ACTION_A, InputActionMasks.ACTION_A, false, false));
        return manager;
    }

    private static void update(
            Sonic3kLevelSelectManager manager, InputHandler input, PlayerInputState p1) {
        input.setLogicalOverride(LogicalInputSnapshot.ofPlayers(p1, PlayerInputState.neutral()));
        try {
            manager.update(input);
        } finally {
            input.clearLogicalOverride();
            input.update();
        }
    }

    private static Stream<Arguments> teamActCases() {
        return Stream.of(Team.values()).flatMap(team ->
                Stream.of(0, 1).map(act -> Arguments.of(team, act)));
    }

    private static Stream<Team> soloTeams() {
        return Stream.of(Team.SONIC, Team.TAILS, Team.KNUCKLES);
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
        SONIC("sonic", "", PlayerCharacter.SONIC_ALONE, Sonic.class, 0, 0),
        TAILS("tails", "", PlayerCharacter.TAILS_ALONE, Tails.class, 0, 4),
        SONIC_TAILS("sonic", "tails", PlayerCharacter.SONIC_AND_TAILS, Sonic.class, 1, 0),
        KNUCKLES("knuckles", "", PlayerCharacter.KNUCKLES, Knuckles.class, 0, 0);

        private final String main;
        private final String sidekicks;
        private final PlayerCharacter playerCharacter;
        private final Class<?> mainType;
        private final int sidekickCount;
        private final int groundSnapYOffset;

        Team(String main, String sidekicks, PlayerCharacter playerCharacter,
             Class<?> mainType, int sidekickCount, int groundSnapYOffset) {
            this.main = main;
            this.sidekicks = sidekicks;
            this.playerCharacter = playerCharacter;
            this.mainType = mainType;
            this.sidekickCount = sidekickCount;
            this.groundSnapYOffset = groundSnapYOffset;
        }

        String main() { return main; }
        String sidekicks() { return sidekicks; }
        PlayerCharacter playerCharacter() { return playerCharacter; }
        Class<?> mainType() { return mainType; }
        int sidekickCount() { return sidekickCount; }
        int groundSnapYOffset() { return groundSnapYOffset; }
    }

    private record ConfigSnapshot(String main, String sidekicks) { }
    private record InputRun(int frames, int mask) { }
}
