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
    String overlapDetail;

    @BeforeAll static void build() throws Exception {
        harness = ExampleModHarness.build(Path.of("examples/typing-of-the-hedgehog"), temp.resolve("work"));
    }
    @AfterAll static void close() throws Exception { if (harness != null) harness.close(); }
    @AfterEach void dispose() throws Exception {
        if (bootstrap != null) bootstrap.dispose();
        GameServices.configuration().clearSessionOverrides();
    }

    void launch(SonicGame game, String code, int zone, int act, boolean demo) throws Exception {
        launch(game, code, zone, act, demo, "BOSS_RUSH", 11);
    }

    void launch(SonicGame game, String code, int zone, int act, boolean demo, String mode, int seed) throws Exception {
        bootstrap = SharedLevel.load(game, zone, act);
        var config = GameServices.configuration();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        var effective = harness.apply(GameServices.module());
        Path storage = temp.resolve("work/storage/mods/typing-of-the-hedgehog");
        Files.createDirectories(storage);
        Files.writeString(storage.resolve("intent.txt"), "mode=" + mode + "\ngame=" + code + "\nzone=" + zone
                + "\nact=" + act + "\ndifficulty=NORMAL\ncapitals=false\npunctuation=false\nseed=" + seed + "\n"
                + "dictionaries=standard\nnoFail=" + mode.equals("ARCADE") + "\nvoice=OFF\ndemoRows=0\ndemo=" + demo + "\n");
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
        untilCourse("BOSS", 30000);
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

    /** Match the lead's four ARCADE seed-5 captures, including entry, strikes and tally. */
    void arcadeFight(SonicGame game, String code, int zone, int act, int hits) throws Exception {
        launch(game, code, zone, act, true, "ARCADE", 5);
        var camera = GameServices.camera();
        int levelRight = GameServices.level().getCurrentLevel().getMaxX() + 320;
        var phases = new java.util.TreeMap<String, Integer>();
        Object renderer = call(GameServices.level(), "spritePresentationRenderer");
        Object boundsMask = field(renderer, "boundsMask");
        int rows = 0, outside = 0, overlaps = 0, masked = 0;
        while (!"TALLY".equals(call(view(), "phase")) && rows++ < 30000) {
            phases.merge((String) call(boss, "phase"), 1, Integer::sum);
            if (camera.getX() + camera.getWidth() > Math.min(levelRight, camera.getMaxX() + 320)) outside++;
            if (bodyOverlapsStand()) {
                if (overlaps++ == 0) System.out.println("[fixes-body] " + code + ":" + zone + " " + overlapDetail);
            }
            var mask = (com.openggf.graphics.ArenaMaskState) call(boundsMask, "sample");
            if (mask != null && mask.visible(camera.getWidth())) masked++;
            row();
        }
        System.out.println("[fixes-arcade] " + code + ":" + zone + " rows=" + rows + " phases=" + phases
                + " outside=" + outside + " overlaps=" + overlaps + " masked=" + masked + " camera=" + camera.getX()
                + " bounds=" + camera.getMinX() + ".." + camera.getMaxX() + " levelRight=" + levelRight);
        assertEquals("TALLY", call(view(), "phase"));
        assertEquals(hits, call(boss, "hits"));
        int measuredRows = rows, outsideRows = outside, overlapRows = overlaps, maskedRows = masked;
        assertAll(() -> assertEquals(0, outsideRows, "camera inside drawn arena"),
                () -> assertEquals(0, maskedRows, "entry never retains a faded native wing"),
                () -> assertEquals(0, overlapRows, "body clear of standing box"),
                () -> assertTrue(measuredRows <= (hits == 12 ? 7200 : 5400), "demo duration " + measuredRows));
        for (int i = 0; i < 420; i++) {
            assertTrue(camera.getX() + camera.getWidth() <= Math.min(levelRight, camera.getMaxX() + 320));
            var mask = (com.openggf.graphics.ArenaMaskState) call(boundsMask, "sample");
            assertTrue(mask == null || !mask.visible(camera.getWidth()), "tally keeps the full arena");
            row();
        }
    }

    /** Actual ROM mapping-piece boxes, excluding attack hazards and intended homing contact. */
    boolean bodyOverlapsStand() throws Exception {
        if (!(boolean) field(boss, "spawned") || (boolean) field(boss, "defeated")) return false;
        Object motion = field(boss, "motion");
        if ("ATTACK".equals(call(boss, "phase")) && (boolean) field(motion, "impact")) return false;
        int px = (int) call(course, "anchorX"), py = (int) call(course, "anchorY");
        var art = GameServices.level().getObjectRenderManager().getArtProvider();
        @SuppressWarnings("unchecked") List<List<Object>> sprites = (List<List<Object>>) field(boss, "sprites");
        @SuppressWarnings("unchecked") List<Object> parts = (List<Object>) call(field(boss, "rig"), "parts");
        Set<String> hazards = Set.of("ball", "pillar0", "pillar1", "pillar2", "pillar3", "plasma", "launcher",
                "panel", "punch", "sensor", "bombs", "jet", "drill", "flame");
        for (int i = 0; i < parts.size(); i++) {
            if (hazards.contains((String) call(parts.get(i), "name"))) continue;
            for (Object sprite : sprites.get(i)) {
                if (!(boolean) field(sprite, "visible")) continue;
                var mapping = art.getSheet((String) field(sprite, "artKey")).getFrame((int) field(sprite, "frame"));
                int sx = (int) field(sprite, "x"), sy = (int) field(sprite, "y");
                for (var piece : mapping.pieces()) {
                    int x = sx + ((boolean) field(sprite, "flipX") ? -piece.xOffset() - piece.widthTiles() * 8 : piece.xOffset());
                    int y = sy + ((boolean) field(sprite, "flipY") ? -piece.yOffset() - piece.heightTiles() * 8 : piece.yOffset());
                    // Sonic's standing X radius is 9px; retain another 10px of breathing room.
                    if (x < px + 19 && x + piece.widthTiles() * 8 > px - 19
                            && y < py + 19 && y + piece.heightTiles() * 8 > py - 19) {
                        overlapDetail = call(parts.get(i), "name") + " box " + x + "," + y + ","
                                + piece.widthTiles() * 8 + "," + piece.heightTiles() * 8
                                + " stand " + px + "," + py + " phase " + call(boss, "phase");
                        return true;
                    }
                }
            }
        }
        return false;
    }

    void retryFight(SonicGame game, String code, int zone, int act) throws Exception {
        launch(game, code, zone, act, true, "ARCADE", 5);
        for (int i = 0; i < 120; i++) row();
        var camera = GameServices.camera();
        int x = camera.getX(), min = camera.getMinX(), max = camera.getMaxX();
        int home = (int) field(field(boss, "motion"), "homeX");
        call(course, "retryStand");
        row();
        boss = call(field(course, "encounter"), "bossFight");
        assertEquals("BOSS", call(view(), "phase"));
        assertAll(() -> assertEquals(x, camera.getX(), "retry frames before its first native row"),
                () -> assertEquals(min, camera.getMinX()), () -> assertEquals(max, camera.getMaxX()));
        for (int i = 0; i < 120; i++) {
            assertEquals(x, camera.getX());
            assertEquals(max, camera.getMaxX());
            assertFalse(bodyOverlapsStand(), overlapDetail);
            row();
        }
        assertEquals(home, field(field(boss, "motion"), "homeX"), "body recreated in the framed arena");
        var sonic = (com.openggf.sprites.playable.AbstractPlayableSprite) camera.getFocusedSprite();
        sonic.setHurt(true); // The course's native-hazard checkpoint guard is another restore boundary.
        row();
        assertFalse(((com.openggf.sprites.playable.AbstractPlayableSprite) camera.getFocusedSprite()).isHurt());
        assertEquals(x, camera.getX());
        assertEquals(min, camera.getMinX());
        assertEquals(max, camera.getMaxX());
    }

    @Test @RequiresRom(SonicGame.SONIC_1)
    void greenHillRetryKeepsWideArenaAndBodyHome() throws Exception {
        retryFight(SonicGame.SONIC_1, "s1", 0, 2);
    }
    @Test @RequiresRom(SonicGame.SONIC_1)
    void finalZoneRetryKeepsWideArenaAndBodyHome() throws Exception {
        retryFight(SonicGame.SONIC_1, "s1", 6, 0);
    }
    @Test @RequiresRom(SonicGame.SONIC_2)
    void emeraldHillRetryKeepsWideArenaAndBodyHome() throws Exception {
        retryFight(SonicGame.SONIC_2, "s2", 0, 1);
    }
    @Test @RequiresRom(SonicGame.SONIC_2)
    void deathEggRetryKeepsWideArenaAndBodyHome() throws Exception {
        retryFight(SonicGame.SONIC_2, "s2", 10, 0);
    }

    @Test void talkBackEchoesAreShortWholeWordsAndKeepReadableClauses() throws Exception {
        Class<?> type = harness.loader().loadClass("typinghog.play.Boss");
        Method echo = type.getDeclaredMethod("echo", String.class);
        echo.setAccessible(true);
        assertEquals("Another typo", echo.invoke(null, "Ha! Another typo! I am counting every one!"));
        for (String line : Files.readAllLines(Path.of("examples/typing-of-the-hedgehog/src/main/resources/words/taunts.txt"))) {
            if (!line.contains("|") || line.startsWith("#")) continue;
            String taunt = line.substring(line.indexOf('|') + 1);
            String reply = (String) echo.invoke(null, taunt);
            assertFalse(reply.isBlank());
            assertTrue(reply.length() <= 20, reply);
            assertTrue(taunt.contains(reply), "echo comes from the spoken line");
            int end = taunt.indexOf(reply) + reply.length();
            assertTrue(end == taunt.length() || !Character.isLetterOrDigit(taunt.charAt(end)), "whole words: " + reply);
        }
    }

    @Test @RequiresRom(SonicGame.SONIC_1)
    void greenHillArcadeHasFullArenaClearBodyAndShortFight() throws Exception {
        arcadeFight(SonicGame.SONIC_1, "s1", 0, 2, 8);
    }
    @Test @RequiresRom(SonicGame.SONIC_1)
    void finalZoneArcadeHasFullArenaClearBodyAndShortFight() throws Exception {
        arcadeFight(SonicGame.SONIC_1, "s1", 6, 0, 8);
    }
    @Test @RequiresRom(SonicGame.SONIC_2)
    void emeraldHillArcadeHasFullArenaClearBodyAndShortFight() throws Exception {
        arcadeFight(SonicGame.SONIC_2, "s2", 0, 1, 8);
    }
    @Test @RequiresRom(SonicGame.SONIC_2)
    void deathEggArcadeHasFullArenaClearBodyAndShortFight() throws Exception {
        arcadeFight(SonicGame.SONIC_2, "s2", 10, 0, 12);
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
