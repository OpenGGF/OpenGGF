package com.openggf.mods.code;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.MenuInput;
import com.openggf.game.CanonicalAnimation;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.mode.ControlledFrameRuntime;
import com.openggf.game.session.SessionManager;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/** Packaged puppets on production Boss Rush frames; no native-boss or rail mocks. */
class TestTypingHedgehogBosses {
    @TempDir static Path temp;
    static ExampleModHarness harness;
    SharedLevel bootstrap;
    HeadlessTestFixture fixture;
    InputHandler input;
    Object course;
    Object boss;

    @BeforeAll static void build() throws Exception {
        harness = ExampleModHarness.build(Path.of("examples/typing-of-the-hedgehog"), temp.resolve("work"));
    }
    @AfterAll static void close() throws Exception { if (harness != null) harness.close(); }
    @AfterEach void dispose() throws Exception {
        if (bootstrap != null) bootstrap.dispose();
        GameServices.configuration().clearSessionOverrides();
    }

    void launch(SonicGame game, String code, int zone, int act, boolean demo) throws Exception {
        bootstrap = SharedLevel.load(game, zone, act);
        var config = GameServices.configuration();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        var effective = harness.apply(GameServices.module());
        Path storage = temp.resolve("work/storage/mods/typing-of-the-hedgehog");
        Files.createDirectories(storage);
        Files.writeString(storage.resolve("intent.txt"), "mode=BOSS_RUSH\ngame=" + code + "\nzone=" + zone
                + "\nact=" + act + "\ndifficulty=NORMAL\ncapitals=false\npunctuation=false\nseed=11\n"
                + "dictionaries=standard\nnoFail=false\nvoice=OFF\ndemoRows=0\ndemo=" + demo + "\n");
        SessionManager.clear();
        GameModuleRegistry.setCurrent(effective);
        TestEnvironment.activeGameplayMode();
        fixture = HeadlessTestFixture.builder().withZoneAndAct(zone, act).build();
        // The rails lane owns the mod's display requirement. Exercise this lane at 400 even
        // before that requirement lands, using the supported harness configuration override.
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, "WIDE_16_9");
        config.resolveDisplayAspect();
        GameServices.camera().refreshViewportDimensions(config);
        GameServices.graphics().setProjectionWidth(400);
        assertEquals(400, GameServices.camera().getWidth());
        course = effective.getGameService(harness.loader().loadClass("typinghog.play.Course"));
        input = new InputHandler();
        untilCourse("BOSS", 1000);
        boss = call(field(course, "encounter"), "bossFight");
        // Check all declared rig animations, including hidden phases and compound copies.
        var art = GameServices.level().getObjectRenderManager().getArtProvider();
        int uses = 0;
        for (var use : TypingHedgehogArtTables.uses(code, zone, act)) {
            if (!use.table().startsWith("boss")) continue;
            var sheet = art.getSheet(use.artKey());
            assertNotNull(sheet, use.toString());
            assertNotNull(art.getRenderer(use.artKey()), use.toString());
            assertTrue(use.frame() >= 0 && use.frame() < sheet.getFrameCount(), use.toString());
            uses++;
        }
        assertTrue(uses > 0);
        System.out.println("[bosses-v2] " + code + ":" + zone + ":" + act + " 400px, " + uses + " boss art uses");
    }

    static Object call(Object o, String name, Object... args) throws Exception {
        Method m = java.util.Arrays.stream(o.getClass().getDeclaredMethods())
                .filter(it -> it.getName().equals(name) && it.getParameterCount() == args.length).findFirst().orElseThrow();
        m.setAccessible(true);
        return m.invoke(o, args);
    }
    static Object field(Object o, String name) throws Exception {
        Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(o);
    }
    Object view() throws Exception { return call(course, "view"); }
    void row() {
        ControlledFrameRuntime.step(fixture.runtime(), input, LogicalInputSnapshot.neutral());
        input.update();
    }
    void untilCourse(String phase, int limit) throws Exception {
        for (int i = 0; i < limit; i++) {
            if (phase.equals(call(view(), "phase"))) return;
            row();
        }
        fail("never reached " + phase + ": " + view() + "; findings " + harness.findings());
    }
    void untilBoss(String phase, int limit) throws Exception {
        for (int i = 0; i < limit; i++) {
            if (phase.equals(call(boss, "phase"))) return;
            row();
        }
        fail("never reached boss " + phase + ": " + view());
    }
    void type(int id) throws Exception {
        Object target = call(field(course, "field"), "target", id);
        assertNotNull(target, "live target " + id);
        String text = (String) call(call(target, "prompt"), "typed");
        for (int cp : text.codePoints().toArray()) { MenuInput.handleCharEvent(input, cp); row(); }
    }

    void demoBoss(SonicGame game, String code, int zone, int act, int hits, Set<String> expected) throws Exception {
        launch(game, code, zone, act, true);
        Set<String> attacks = new HashSet<>();
        java.util.ArrayList<String> order = new java.util.ArrayList<>();
        int rows = 0;
        while (!"TALLY".equals(call(view(), "phase")) && rows++ < 25000) {
            String attack = (String) call(boss, "attack");
            attacks.add(attack);
            if (!attack.isEmpty() && (order.isEmpty() || !order.getLast().equals(attack))) order.add(attack);
            assertEquals(3, call(view(), "lives"), "no life loss: " + view());
            row();
        }
        assertEquals("TALLY", call(view(), "phase"), "completed fight: " + view());
        assertEquals(hits, call(boss, "hits"), "original ROM hit count");
        assertTrue(attacks.containsAll(expected), attacks.toString());
        if (code.equals("s2") && zone == 10) {
            assertEquals(List.of("WALK", "SENSOR_SLAM", "BOMBS", "PUNCH", "SENSOR_SLAM", "BOMBS"),
                    order.subList(0, 6), "ROM action order, with bombs following a locked landing");
        }
        assertEquals(10, call(view(), "rings"), "demo typed every dodge, without ring loss");
        assertTrue(harness.findings().isEmpty(), harness.findings().toString());
        System.out.println("[bosses-v2] " + code + ":" + zone + " won in " + rows + " rows; attacks " + attacks);
    }

    @Test @RequiresRom(SonicGame.SONIC_1)
    void theDemoTypistBeatsGreenHillAt400() throws Exception {
        demoBoss(SonicGame.SONIC_1, "s1", 0, 2, 8, Set.of("BALL"));
    }
    @Test @RequiresRom(SonicGame.SONIC_1)
    void theDemoTypistBeatsFinalZoneAt400() throws Exception {
        demoBoss(SonicGame.SONIC_1, "s1", 6, 0, 8, Set.of("CYLINDERS", "PLASMA"));
    }
    @Test @RequiresRom(SonicGame.SONIC_2)
    void theDemoTypistBeatsDeathEggAt400() throws Exception {
        demoBoss(SonicGame.SONIC_2, "s2", 10, 0, 12, Set.of("WALK", "PUNCH", "SENSOR_SLAM", "BOMBS"));
    }
    @Test @RequiresRom(SonicGame.SONIC_2)
    void theDemoTypistBeatsEmeraldHillAt400() throws Exception {
        demoBoss(SonicGame.SONIC_2, "s2", 0, 1, 8, Set.of("DRILL"));
    }
    @Test @RequiresRom(SonicGame.SONIC_1)
    void typedDodgeReturnsStandingWithoutRingLoss() throws Exception {
        launch(SonicGame.SONIC_1, "s1", 0, 2, false);
        untilBoss("TELEGRAPH", 120);
        int x = fixture.sprite().getCentreX(), y = fixture.sprite().getCentreY();
        type((int) call(boss, "dodgeTarget"));
        assertEquals(true, call(boss, "dodged"));
        boolean moved = false, airborne = false;
        for (int i = 0; i < 600 && !"OPEN".equals(call(boss, "phase")); i++) {
            row();
            moved |= fixture.sprite().getCentreX() != x || fixture.sprite().getCentreY() != y;
            airborne |= fixture.sprite().getAir();
        }
        assertEquals("OPEN", call(boss, "phase"));
        assertTrue(moved, "typing actually moves Sonic through the dodge arc");
        assertTrue(airborne, "he leaves the ground during the dodge");
        for (int i = 0; i < 30; i++) row();
        assertEquals(10, call(view(), "rings"));
        assertEquals(x, fixture.sprite().getCentreX());
        assertEquals(y, fixture.sprite().getCentreY());
        assertFalse(fixture.sprite().getRolling());
        assertFalse(fixture.sprite().getAir());
        assertEquals(fixture.sprite().resolveAnimationId(CanonicalAnimation.WAIT), fixture.sprite().getAnimationId());
    }
    @Test @RequiresRom(SonicGame.SONIC_1)
    void anUntypedDodgeHitsSonic() throws Exception {
        launch(SonicGame.SONIC_1, "s1", 0, 2, false);
        untilBoss("OPEN", 800);
        assertEquals(0, call(view(), "rings"), "the incoming wrecking ball spills rings");
        assertEquals(3, call(view(), "lives"));
        assertEquals(0, call(boss, "hits"));
    }
    @Test @RequiresRom(SonicGame.SONIC_1)
    void onlyRobotniksPillarTakesDamage() throws Exception {
        launch(SonicGame.SONIC_1, "s1", 6, 0, false);
        untilBoss("TELEGRAPH", 120);
        type((int) call(boss, "dodgeTarget"));
        untilBoss("OPEN", 600);
        int right = (int) call(boss, "correctTarget");
        int[] ids = (int[]) call(boss, "openTargets");
        assertEquals(right, ids[(int) call(boss, "occupiedPillar")], "the visible occupant owns that pillar's word");
        int wrong = java.util.Arrays.stream(ids).filter(it -> it != right).findFirst().orElseThrow();
        type(wrong);
        assertEquals(false, call(call(field(course, "field"), "target", right), "typeable"),
                "the laugh penalty temporarily closes selection");
        for (int i = 0; i < 80; i++) row();
        assertEquals(0, call(boss, "hits"), "empty cylinder is harmless");
        assertEquals(10, call(view(), "rings"), "wrong pillar penalty costs time, not Sonic damage");
        type(right);
        for (int i = 0; i < 45; i++) row();
        assertEquals(1, call(boss, "hits"), "occupied cylinder takes the hit on contact");
    }
}
