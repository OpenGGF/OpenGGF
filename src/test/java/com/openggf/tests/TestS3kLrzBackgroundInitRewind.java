package com.openggf.tests;

import com.openggf.GameLoop;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.control.InputHandler;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.RecordedInputSnapshots;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.objects.LrzDeathEggBackgroundInstance;
import com.openggf.game.sonic3k.runtime.LrzZoneRuntimeState;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A rewind to a Lava Reef load frame, before the first background event, replays the
 * once-per-load background initializer exactly. {@code LRZ1_BackgroundInit} and
 * {@code LRZ2_BackgroundInit} (skdisasm 044fa46 sonic3k.asm:115704-115717 for act 2, which leaves
 * {@code Events_routine_bg} on 8) run in the native level setup; the engine runs them at the
 * first event dispatch, so whether they are still pending is restorable world state.
 *
 * <p>The initializer case compares the initializer's own output plus the whole world after the
 * replay; the immediate-restore case adds exact restore equality at the load frame and mid-scroll.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kLrzBackgroundInitRewind {
    private static final Bk2FrameInput NEUTRAL = new Bk2FrameInput(0, 0, 0, false, "");
    private static final int REPLAY_FRAMES = 12;
    /** LRZ1_BackgroundInit's Knuckles-only Plane B chunk write (Sonic3kLRZEvents.applyBackgroundInit). */
    private static final int KNUCKLES_BG_CHUNK_LAYER = 1, KNUCKLES_BG_CHUNK_ROW = 1,
            KNUCKLES_BG_CHUNK_COLUMN = 4, KNUCKLES_BG_CHUNK_ID = 0xF6;

    @ParameterizedTest(name = "LRZ act {1} load-frame restore {2}")
    @CsvSource({"9,1,tails", "9,0,knuckles"})
    void loadFrameRestoreReplaysTheBackgroundInitialiser(int zone, int act, String main) {
        var support = new CanonicalEntryMatrixSupport() {
            @Override TestS3kCanonicalEntryLifecycle.Act binding() {
                return new TestS3kCanonicalEntryLifecycle.Act("LRZ", zone, act);
            }
        };
        support.saveConfiguration();
        try {
            var row = new CanonicalEntryMatrixSupport.Row(main, WidescreenAspect.NATIVE_4_3, "off", "");
            var fixture = support.boot(row);
            var input = new InputHandler();
            input.setLogicalOverride(RecordedInputSnapshots.fromBk2(NEUTRAL, NEUTRAL));
            var loop = new GameLoop(input);
            loop.setGameplayMode(fixture.gameplayMode());
            loop.setGameMode(GameMode.LEVEL);
            try {
                var lrz = (LrzZoneRuntimeState) GameServices.zoneRuntimeState();
                assertEquals(0, lrz.backgroundRoutine(), "precondition: no background event has run yet");
                var registry = fixture.gameplayMode().getRewindRegistry();
                var before = registry.capture();
                step(loop, REPLAY_FRAMES);
                var first = initialiserState(act);
                if (act == 1) {
                    // LRZ2_BackgroundInit: Events_routine_bg = 8; loc_5700C's refresh never runs, so
                    // Draw_delayed_rowcount is untouched; one Death Egg background allocation.
                    assertEquals(List.of(8, 0, 1), first, "direct $901 load: steady stage, one Death Egg");
                } else {
                    assertEquals(KNUCKLES_BG_CHUNK_ID, first.get(3), "LRZ1_BackgroundInit's Knuckles chunk");
                }
                var after = registry.capture();
                for (int cycle = 0; cycle < 2; cycle++) {
                    registry.restore(before);
                    step(loop, REPLAY_FRAMES);
                    assertEquals(first, initialiserState(act), "load-frame restore replays the initializer");
                    TestS3kCanonicalEntryLifecycle.assertSameWorld(after, registry.capture(),
                            "LRZ load-frame replay", REPLAY_FRAMES);
                }
            } finally {
                loop.closePresence();
            }
        } finally {
            support.restoreConfiguration();
        }
    }

    /**
     * An immediate restore of a whole-world snapshot reproduces it exactly, at the load frame
     * before any scroll pass and at a released frame, and two forward replays with the camera
     * moving match the first pass. The post-restore presentation rebuild may derive scroll output
     * from restored RAM; it must not publish Lava Reef's logical scroll words itself.
     */
    @ParameterizedTest(name = "LRZ act {1} {3} restore {2}")
    @CsvSource({"9,1,tails,load", "9,1,tails,released", "9,0,knuckles,load", "9,0,knuckles,released"})
    void immediateRestoreIsExactAndForwardReplaysMatch(int zone, int act, String main, String point) {
        var support = new CanonicalEntryMatrixSupport() {
            @Override TestS3kCanonicalEntryLifecycle.Act binding() {
                return new TestS3kCanonicalEntryLifecycle.Act("LRZ", zone, act);
            }
        };
        support.saveConfiguration();
        try {
            var row = new CanonicalEntryMatrixSupport.Row(main, WidescreenAspect.NATIVE_4_3, "off", "");
            var fixture = support.boot(row);
            var input = new InputHandler();
            input.setLogicalOverride(RecordedInputSnapshots.fromBk2(NEUTRAL, NEUTRAL));
            var loop = new GameLoop(input);
            loop.setGameplayMode(fixture.gameplayMode());
            loop.setGameMode(GameMode.LEVEL);
            try {
                var right = new Bk2FrameInput(0, 0x08, 0, false, "R");
                if (point.equals("released")) {
                    // Ordinary input until the camera is scrolling, so the snapshot is taken mid-scroll.
                    step(loop, 60);
                    input.setLogicalOverride(RecordedInputSnapshots.fromBk2(right, NEUTRAL));
                    int previous = GameServices.camera().getX();
                    boolean moving = false;
                    for (int f = 0; f < 150 && !moving; f++) {
                        step(loop, 1);
                        moving = GameServices.camera().getX() != previous;
                        previous = GameServices.camera().getX();
                    }
                    assertTrue(moving, "precondition: the camera scrolls at the captured frame");
                }
                var registry = fixture.gameplayMode().getRewindRegistry();
                var before = registry.capture();
                registry.restore(before);
                TestS3kCanonicalEntryLifecycle.assertSameWorld(before, registry.capture(),
                        "LRZ immediate " + point + " restore", 0);
                input.setLogicalOverride(RecordedInputSnapshots.fromBk2(right, NEUTRAL));
                int cameraBefore = GameServices.camera().getX();
                step(loop, REPLAY_FRAMES);
                var after = registry.capture();
                if (point.equals("released")) {
                    assertNotEquals(cameraBefore, GameServices.camera().getX(), "the replay window scrolls");
                }
                for (int cycle = 0; cycle < 2; cycle++) {
                    registry.restore(before);
                    TestS3kCanonicalEntryLifecycle.assertSameWorld(before, registry.capture(),
                            "LRZ " + point + " restore " + cycle, REPLAY_FRAMES);
                    step(loop, REPLAY_FRAMES);
                    TestS3kCanonicalEntryLifecycle.assertSameWorld(after, registry.capture(),
                            "LRZ " + point + " replay " + cycle, REPLAY_FRAMES);
                }
            } finally {
                loop.closePresence();
            }
        } finally {
            support.restoreConfiguration();
        }
    }

    /** Background routine, Draw_delayed_rowcount, Death Egg backgrounds and the act-1 Knuckles cell. */
    private static List<Integer> initialiserState(int act) {
        var lrz = (LrzZoneRuntimeState) GameServices.zoneRuntimeState();
        int deathEggs = GameServices.level().getObjectManager()
                .activeObjectsOfType(LrzDeathEggBackgroundInstance.class).size();
        var state = new java.util.ArrayList<>(List.of(lrz.backgroundRoutine(), lrz.delayedRowcount(), deathEggs));
        if (act == 0) state.add(GameServices.level().getCurrentLevel().getMap()
                .getValue(KNUCKLES_BG_CHUNK_LAYER, KNUCKLES_BG_CHUNK_COLUMN, KNUCKLES_BG_CHUNK_ROW) & 0xFF);
        return state;
    }

    private static void step(GameLoop loop, int frames) {
        for (int f = 0; f < frames; f++) {
            loop.step();
            GameServices.graphics().runPendingRenderThreadTasks();
            assertFalse(GameServices.sprites().getMainPlayable().getDead(), "no P1 death");
        }
    }
}
