package com.openggf.mods.code;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openggf.game.GameModule;
import com.openggf.game.GameServices;
import com.openggf.mods.scene.ModSceneHost;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Smoke test for the Slay the Robotnik scene against a real S3K session: it opens every
 * fight and every event through the scene, plays frames, draws them (recording only, no GL),
 * defeats each fight's enemies, and checks the engine's fault boundary caught nothing.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestSlayTheRobotnikScene {
    private static final String[] POOLS = {"Weak", "Strong", "Elite", "Boss"};

    @TempDir
    Path work;

    private SharedLevel level;

    @AfterEach
    void dispose() {
        if (level != null) {
            level.dispose();
        }
    }

    @Test
    void everyFightAndEventOpensDrawsAndEndsWithoutFaults() throws Exception {
        level = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
        try (SlayTheRobotnikHarness harness = SlayTheRobotnikHarness.build(work.resolve("build"))) {
            GameModule effective = harness.apply(GameServices.module());
            harness.open(effective, work.resolve("saves"), 400, 224);
            play(harness, 30);
            Object scene = harness.scene();
            Method jump = scene.getClass().getMethod("debugJump", String.class);
            List<String> rooms = rooms(harness);
            assertTrue(rooms.size() > 60, "fights and events found: " + rooms.size());
            int hero = 0;
            int fightsWon = 0;
            for (String room : rooms) {
                String character = new String[] {"sonic", "tails", "knuckles"}[hero++ % 3];
                jump.invoke(scene, character + ":7:" + room);
                play(harness, 40);
                boolean fight = room.startsWith("fight:");
                assertEquals(fight ? "CombatRoom" : "EventRoom", currentRoom(scene), "after jumping to " + room);
                if (fight) {
                    jump.invoke(scene, "kill");
                    play(harness, 200);
                    // Two-phase bosses revive instead of dying; everything else is beaten.
                    if (!currentRoom(scene).equals("CombatRoom")) {
                        fightsWon++;
                    }
                }
                assertEquals(Map.of(), harness.findings(), "faults after " + room);
            }
            assertTrue(fightsWon > 40, "fights that ended in victory: " + fightsWon);
            assertEquals(List.of(), harness.exits(), "the scene never left");
        }
    }

    @Test
    void mapWheelScrollHoldsUntilThePlayerMovesOn() throws Exception {
        level = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
        try (SlayTheRobotnikHarness harness = SlayTheRobotnikHarness.build(work.resolve("build"))) {
            GameModule effective = harness.apply(GameServices.module());
            harness.open(effective, work.resolve("saves"), 400, 224);
            play(harness, 30);
            Object scene = harness.scene();
            scene.getClass().getMethod("debugJump", String.class).invoke(scene, "sonic:7:map:1");
            play(harness, 90);
            assertEquals("MapRoom", currentRoom(scene));
            float start = mapScroll(scene);
            // Two notches towards the player scroll on towards the boss, and the view stays there.
            harness.input().handleScroll(-2);
            play(harness, 90);
            float ahead = mapScroll(scene);
            assertTrue(ahead > start + 100, "scrolled from " + start + " to " + ahead);
            play(harness, 60);
            assertEquals(ahead, mapScroll(scene), 0.5f, "the wheel's position holds");
            // Notches away from the player come back, stopping at the act's start.
            harness.input().handleScroll(10);
            play(harness, 90);
            assertEquals(0f, mapScroll(scene), 0.5f);
            assertEquals(Map.of(), harness.findings());
        }
    }

    /** The open act map's horizontal scroll. */
    private static float mapScroll(Object scene) throws Exception {
        Object shell = scene.getClass().getMethod("shell").invoke(scene);
        Object screen = shell.getClass().getMethod("screen").invoke(shell);
        var viewField = screen.getClass().getDeclaredField("view");
        viewField.setAccessible(true);
        Object view = viewField.get(screen);
        var scrollField = view.getClass().getDeclaredField("scroll");
        scrollField.setAccessible(true);
        return scrollField.getFloat(view);
    }

    /** The simple class name of the run's current room ("CombatRoom", "RewardRoom"...). */
    private static String currentRoom(Object scene) throws Exception {
        Object shell = scene.getClass().getMethod("shell").invoke(scene);
        Object run = shell.getClass().getField("run").get(shell);
        Object room = run.getClass().getMethod("room").invoke(run);
        return room.getClass().getSimpleName();
    }

    /** Every encounter and event id, as debugJump commands ("fight:aiz:rhinobot", "event:event:giant_ring"). */
    private static List<String> rooms(SlayTheRobotnikHarness harness) throws Exception {
        Object catalog = harness.loader().loadClass("slaytherobotnik.content.Content").getMethod("build").invoke(null);
        List<String> rooms = new ArrayList<>();
        int acts = (int) catalog.getClass().getMethod("actCount").invoke(catalog);
        Method encounters = catalog.getClass().getMethod("encounters", int.class, String.class);
        for (int act = 1; act <= acts; act++) {
            for (String pool : POOLS) {
                for (Object e : (List<?>) encounters.invoke(catalog, act, pool)) {
                    rooms.add("fight:" + e.getClass().getMethod("id").invoke(e));
                }
            }
        }
        for (Object e : (List<?>) catalog.getClass().getMethod("allEvents").invoke(catalog)) {
            rooms.add("event:" + e.getClass().getMethod("id").invoke(e));
        }
        return rooms;
    }

    /** Ticks and draws (recording the canvas without rendering), as the frame loop would. */
    private static void play(SlayTheRobotnikHarness harness, int frames) {
        ModSceneHost host = harness.host();
        for (int i = 0; i < frames; i++) {
            harness.tick();
            host.draw(null, null);
        }
        assertTrue(com.openggf.mods.scene.SceneHostTestAccess.lastFrameOps(host) > 0, "the scene drew something");
    }
}
