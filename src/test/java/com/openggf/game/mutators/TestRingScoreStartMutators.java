package com.openggf.game.mutators;

import com.openggf.game.GameModule;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.WorldSession;
import com.openggf.game.session.WorldSessionPolicyAccess;
import com.openggf.game.session.WorldSessionPolicyProvider;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.mods.code.ModFaultBoundary;
import com.openggf.mods.mutators.*;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real ROM assembly in all three games with the host's world-owned mutator state: the first
 * active tick seeds the native counters, the level timer's own time over follows a 9:30 seed,
 * and ring hunger drains through the native ring counter into the native kill routine.
 */
@Isolated
class TestRingScoreStartMutators {
    private static final String OWNER = "test-lab";
    @TempDir Path temp;
    private String oldRoot;
    private GameModule original;

    @BeforeEach void setup() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        original = GameModuleRegistry.getCurrent();
        SessionManager.clear();
        oldRoot = System.getProperty("openggf.saveRoot");
        System.setProperty("openggf.saveRoot", temp.resolve("player").toString());
    }

    @AfterEach void cleanup() {
        SessionManager.clear();
        GameModuleRegistry.setCurrent(original);
        if (oldRoot == null) System.clearProperty("openggf.saveRoot");
        else System.setProperty("openggf.saveRoot", oldRoot);
    }

    private static MutatorOption.IntegerSlider slider(String id, MutatorScope scope, int value, int min, int max, int step) {
        return new MutatorOption.IntegerSlider(id, id, "", scope, value, min, max, step, "");
    }

    /** Mirrors Mutator Lab's five definitions; the example package is not on the engine test path. */
    private static List<OwnedMutator> catalogue() {
        var live = MutatorScope.LIVE;
        var launch = MutatorScope.LAUNCH;
        return List.of(
                new OwnedMutator(OWNER, new MutatorDefinition("start-rings", "Start with Rings", "", live, live,
                        List.of(slider("rings", live, 50, 0, 999, 1), slider("delay", live, 0, 0, 300, 1)),
                        Set.of(MutatorCapability.STARTING_RINGS),
                        v -> List.of(new StartingStatePolicy.Rings(v.integer("rings"), v.integer("delay"))))),
                new OwnedMutator(OWNER, new MutatorDefinition("start-time", "Start with Time", "", live, live,
                        List.of(slider("seconds", live, 300, 1, 570, 1)), Set.of(MutatorCapability.STARTING_TIME),
                        v -> List.of(new StartingStatePolicy.Time(v.integer("seconds"))))),
                new OwnedMutator(OWNER, new MutatorDefinition("start-score", "Start with Score", "", launch, launch,
                        List.of(slider("score", launch, 0, 0, 999_990, 10)), Set.of(MutatorCapability.STARTING_SCORE),
                        v -> List.of(new StartingStatePolicy.Score(v.integer("score"))))),
                new OwnedMutator(OWNER, new MutatorDefinition("start-lives", "Start with Lives", "", launch, launch,
                        List.of(slider("lives", launch, 3, 1, 99, 1)), Set.of(MutatorCapability.STARTING_LIVES),
                        v -> List.of(new StartingStatePolicy.Lives(v.integer("lives"))))),
                new OwnedMutator(OWNER, new MutatorDefinition("hungry-for-rings", "Hungry for Rings", "", live, live,
                        List.of(slider("grace", live, 30, 0, 300, 1), slider("loss", live, 1, 1, 100, 1),
                                slider("period", live, 1, 1, 60, 1), slider("threshold", live, 0, 0, 999, 1),
                                slider("starve", live, 5, 1, 60, 1)),
                        Set.of(MutatorCapability.RING_HUNGER),
                        v -> List.of(new RingHungerPolicy(v.integer("grace"), v.integer("loss"), v.integer("period"),
                                v.integer("threshold"), v.integer("starve"))))));
    }

    /** Opens the world, requests {@code settings} (key to option values) and assembles the first act. */
    private HeadlessTestFixture launch(Map<String, Map<String, Integer>> settings) {
        var faults = new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                owners -> new ModStateSaveResult.Saved(), owners -> { });
        var catalog = new MutatorCatalog(catalogue(), faults);
        var nativeModule = GameModuleRegistry.getCurrent();
        var module = new DelegatingGameModule(nativeModule, OWNER + ":fixture") {
            @Override public boolean supportsSidekick() { return false; }
            @Override public <T> T getGameService(Class<T> type) {
                if (type == WorldSessionPolicyProvider.class) return type.cast(new MutatorWorldProvider(catalog));
                if (type == MutatorCatalog.class) return type.cast(catalog);
                if (type == MutatorSupportProfile.class) return type.cast(new MutatorSupportProfile() {
                    public Set<MutatorCapability> capabilities(int zone, int act) { return EnumSet.allOf(MutatorCapability.class); }
                    public boolean supportsPlayer(String key, boolean leader) { return true; }
                });
                return super.getGameService(type);
            }
        };
        GameModuleRegistry.setCurrent(module);
        SessionManager.openGameplaySession(nativeModule, module, null);
        TestEnvironment.activeGameplayMode();
        var state = MutatorWorldAccess.state(SessionManager.getCurrentWorldSession());
        settings.forEach((local, options) -> {
            String key = OWNER + ":" + local;
            options.forEach((option, value) -> state.requestOption(key, option, value));
            state.requestEnabled(key, true);
        });
        // The first full assembly is this session's LAUNCH boundary, as Start from the Lab title.
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(0, 0).withSkippedZoneIntro().build();
        fixture.stepIdleFrames(1);
        return fixture;
    }

    private static WorldSession world() { return SessionManager.getCurrentWorldSession(); }
    private static RingHungerRuntime hunger() {
        return WorldSessionPolicyAccess.getService(world(), RingHungerRuntime.class);
    }

    @Test @RequiresRom(SonicGame.SONIC_1) void sonic1Seeds() { seeds(); }
    @Test @RequiresRom(SonicGame.SONIC_2) void sonic2Seeds() { seeds(); }
    @Test @RequiresRom(SonicGame.SONIC_3K) void sonic3kSeeds() { seeds(); }

    private void seeds() {
        var fixture = launch(Map.of("start-rings", Map.of("rings", 85), "start-time", Map.of("seconds", 270),
                "start-score", Map.of("score", 25_000), "start-lives", Map.of("lives", 7)));
        var level = GameServices.level();
        assertEquals(85, fixture.sprite().getRingCount());
        assertEquals(0, level.getLevelGamestate().getRingExtraLifeFlags(), "85 rings reach no threshold");
        long timer = level.getLevelGamestate().getTimerFrames();
        assertTrue(timer >= 270 * 60 && timer <= 270 * 60 + 2, "4:30 on the clock: " + timer);
        assertEquals("4:30", level.getLevelGamestate().getDisplayTime());
        assertEquals(25_000, GameServices.gameState().getScore());
        assertEquals(7, GameServices.gameState().getLives());
        fixture.stepIdleFrames(30);
        assertEquals(85, fixture.sprite().getRingCount(), "a seed is written once per start");
        GameServices.gameState().loseLife();
        level.respawnPlayer();
        fixture.stepIdleFrames(1);
        assertEquals(85, fixture.sprite().getRingCount(), "a restart from the act start is granted again");
        long restarted = level.getLevelGamestate().getTimerFrames();
        assertTrue(restarted >= 270 * 60 && restarted <= 270 * 60 + 2, "the clock is seeded again: " + restarted);
        assertEquals(6, GameServices.gameState().getLives(), "lives are a session seed, never refilled");
        assertEquals(25_000, GameServices.gameState().getScore());
    }

    @Test @RequiresRom(SonicGame.SONIC_1) void sonic1TimeOver() { timeOver(); }
    @Test @RequiresRom(SonicGame.SONIC_2) void sonic2TimeOver() { timeOver(); }
    @Test @RequiresRom(SonicGame.SONIC_3K) void sonic3kTimeOver() { timeOver(); }

    /** 9:30 leaves exactly the 30 seconds before the native 10:00 time over. */
    private void timeOver() {
        var fixture = launch(Map.of("start-time", Map.of("seconds", 570)));
        assertEquals("9:30", GameServices.level().getLevelGamestate().getDisplayTime());
        fixture.stepIdleFrames(29 * 60);
        assertFalse(fixture.sprite().getDead(), "alive at 9:59");
        int frames = 0;
        while (!fixture.sprite().getDead() && frames++ < 3 * 60) fixture.stepIdleFrames(1);
        assertTrue(fixture.sprite().getDead(), "the native timer kills at 10:00");
        assertTrue(GameServices.level().getLevelGamestate().isTimeOver());
    }

    @Test @RequiresRom(SonicGame.SONIC_1) void sonic1Hunger() { hungerStarves(); }
    @Test @RequiresRom(SonicGame.SONIC_2) void sonic2Hunger() { hungerStarves(); }
    @Test @RequiresRom(SonicGame.SONIC_3K) void sonic3kHunger() { hungerStarves(); }

    private void hungerStarves() {
        var fixture = launch(Map.of("start-rings", Map.of("rings", 3),
                "hungry-for-rings", Map.of("grace", 1, "loss", 1, "period", 1, "threshold", 0, "starve", 2)));
        var sonic = fixture.sprite();
        assertEquals(3, sonic.getRingCount());
        assertEquals(RingHungerRuntime.Phase.GRACE, hunger().phase());
        var phases = EnumSet.noneOf(RingHungerRuntime.Phase.class);
        int frames = 0;
        while (hunger().phase() != RingHungerRuntime.Phase.STARVING && frames++ < 600) {
            fixture.stepIdleFrames(1);
            phases.add(hunger().phase());
        }
        assertTrue(phases.contains(RingHungerRuntime.Phase.DRAIN));
        assertEquals(0, sonic.getRingCount(), "three drain steps through the native counter");
        assertFalse(sonic.getDead());
        var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
        var before = registry.capture();
        assertTrue(before.containsKey("mutators"));
        int toDeath = 0;
        while (!sonic.getDead() && toDeath++ < 600) fixture.stepIdleFrames(1);
        assertTrue(sonic.getDead(), "two starving seconds call the native kill");
        assertTrue(toDeath >= 115 && toDeath <= 121, "about two seconds after starving began: " + toDeath);
        registry.restore(before);
        assertFalse(sonic.getDead());
        assertEquals(RingHungerRuntime.Phase.STARVING, hunger().phase(), "the countdown is captured history");
        int replay = 0;
        while (!sonic.getDead() && replay++ < 600) fixture.stepIdleFrames(1);
        assertEquals(toDeath, replay, "rewind replays the same starvation frame");
    }

    @Test @RequiresRom(SonicGame.SONIC_1) void sonic1Disabled() { disabledIsNative(); }
    @Test @RequiresRom(SonicGame.SONIC_2) void sonic2Disabled() { disabledIsNative(); }
    @Test @RequiresRom(SonicGame.SONIC_3K) void sonic3kDisabled() { disabledIsNative(); }

    private void disabledIsNative() {
        var fixture = launch(Map.of());
        fixture.stepIdleFrames(119);
        assertEquals(0, fixture.sprite().getRingCount());
        assertTrue(GameServices.level().getLevelGamestate().getTimerFrames() <= 130);
        assertEquals(0, GameServices.gameState().getScore());
        assertEquals(3, GameServices.gameState().getLives());
        assertEquals(RingHungerRuntime.Indicator.NONE, hunger().indicator());
    }
}
