package com.openggf.mods.code;

import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;

import static org.junit.jupiter.api.Assertions.*;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.MenuInput;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.mode.ControlledFrameRuntime;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.objects.TornadoObjectInstance;
import com.openggf.sprites.playable.SuperState;
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
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Production selected-section courses against all three real ROMs. Every enabled ground stop
 * must prove four distinct native arrivals and the boss stop, then clear the typed act. Flight
 * checks establish native Tornado contact / completed Super transformation and reject ground
 * substitutes. No rail rows, manually completed encounters, or synthetic floor are used.
 * Select a bounded subset with -Dtypinghog.sections=s1:1:2,s2:8:0,s3k:12:0.
 */
class TestTypingHedgehogSections {
    private static final Path PROJECT = Path.of("examples/typing-of-the-hedgehog");
    @TempDir static Path work;
    private static ExampleModHarness harness;

    static Stream<String> s1() throws Exception { return stops("s1"); }
    static Stream<String> s2() throws Exception { return stops("s2"); }
    static Stream<String> s3k() throws Exception { return stops("s3k"); }
    private static Stream<String> stops(String game) throws Exception {
        Set<String> selected = Set.of(System.getProperty("typinghog.sections", "").split(","));
        return Files.readAllLines(PROJECT.resolve("src/main/resources/sections.txt")).stream().map(String::strip)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .map(line -> line.split("\\s+"))
                .filter(f -> f[0].equals(game))
                .map(f -> f[0] + ":" + f[1] + ":" + f[2])
                .filter(key -> selected.contains("") || selected.contains(key));
    }

    @ParameterizedTest(name = "selected section {0}") @MethodSource("s1") @RequiresRom(SonicGame.SONIC_1)
    void sonic1Sections(String key) throws Exception { check(SonicGame.SONIC_1, key); }
    @ParameterizedTest(name = "selected section {0}") @MethodSource("s2") @RequiresRom(SonicGame.SONIC_2)
    void sonic2Sections(String key) throws Exception { check(SonicGame.SONIC_2, key); }
    @ParameterizedTest(name = "selected section {0}") @MethodSource("s3k") @RequiresRom(SonicGame.SONIC_3K)
    void sonic3kSections(String key) throws Exception { check(SonicGame.SONIC_3K, key); }

    @AfterAll static void close() throws Exception {
        SessionManager.clear();
        if (harness != null) harness.close();
        GameServices.configuration().clearSessionOverrides();
    }

    private void check(SonicGame game, String key) throws Exception {
        String[] identity = key.split(":");
        int zone = Integer.parseInt(identity[1]), act = Integer.parseInt(identity[2]);
        var bootstrap = SharedLevel.load(game, zone, act);
        try {
            if (harness == null) harness = ExampleModHarness.build(PROJECT, work.resolve("mod"));
            var config = GameServices.configuration();
            config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
            config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
            config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, "WIDE_16_9");
            config.setSessionOverride(SonicConfiguration.S3K_SKIP_INTROS, true);
            config.resolveDisplayAspect();
            GameServices.graphics().setProjectionWidth(400);
            var effective = harness.apply(GameServices.module());
            Path storage = work.resolve("mod/storage/mods/typing-of-the-hedgehog");
            Files.createDirectories(storage);
            Files.deleteIfExists(storage.resolve("result.txt"));
            Files.writeString(storage.resolve("intent.txt"), "mode=STORY\ngame=" + identity[0] + "\nzone=" + zone
                    + "\nact=" + act + "\ndifficulty=EASY\ncapitals=false\npunctuation=false\ndictionaries=standard\n"
                    + "seed=413\nbossStars=1\nnoFail=true\nslowMotion=false\nvoice=OFF\n");
            SessionManager.clear();
            GameModuleRegistry.setCurrent(effective);
            TestEnvironment.activeGameplayMode();
            var builder = HeadlessTestFixture.builder().withZoneAndAct(zone, act);
            if (game == SonicGame.SONIC_3K
                    && zone == Sonic3kZoneIds.ZONE_DDZ && act == 0) {
                builder.withFreshLevelStartLifecycle();
            }
            var fixture = builder.build();
            var input = new InputHandler();
            Object course = effective.getGameService(harness.loader().loadClass("typinghog.play.Course"));
            assertNotNull(course);
            Set<Integer> fights = new HashSet<>();
            Set<Integer> groundX = new HashSet<>();
            boolean inspectedHolds = false, bossSeen = false;
            String mode = null;
            int nativeClock = -1;
            double previousX = Double.NaN;
            int movedRows = 0, mappingChanges = 0, previousMapping = -1;
            for (int row = 0; row < 30_000; row++) {
                Object view = invoke(course, "view");
                assertNull(get(view, "fault"), key + " " + view);
                String phase = (String) get(view, "phase");
                Object section = invoke(course, "sectionView");
                if (section != null) mode = (String) get(section, "mode");
                Object route = section == null ? null : get(section, "travel");
                if (route != null) {
                    double x = (double) get(route, "x");
                    if (!Double.isNaN(previousX) && Math.abs(x - previousX) > .1) movedRows++;
                    previousX = x;
                    int mapping = fixture.sprite().getMappingFrame();
                    if (previousMapping >= 0 && mapping != previousMapping) mappingChanges++;
                    previousMapping = mapping;
                }
                if (!phase.equals("IDLE") && !phase.equals("ENTRY")) {
                    int clock = GameServices.level().getObjectManager().getVblaCounter();
                    if (nativeClock < 0) nativeClock = clock;
                    assertEquals(nativeClock, clock, key + " section changed the held native clock");
                    assertEquals(-1, get(view, "railRow"), "a selected section consumes no rail");
                }
                if (phase.equals("STAND")) {
                    int fight = (int) get(view, "nextStand");
                    if (fights.add(fight)) {
                        assertEquals(0, get(view, "fightNativeRows"), "section fights hold on their first row");
                        if (route != null) {
                            assertTrue((boolean) get(route, "grounded"), key);
                            assertTrue(Math.abs((int) get(route, "xSpeed")) <= 64, key);
                            assertTrue(Math.abs((int) get(route, "groundSpeed")) <= 64, key);
                            groundX.add((int) Math.round((double) get(route, "x")));
                            assertFalse(fixture.sprite().getRolling(), "ground arrival stands before homing");
                            assertFalse(fixture.sprite().getAir(), "ground arrival has native support");
                            assertEquals(196, fixture.sprite().getCentreY() + fixture.sprite().getYRadius()
                                    - GameServices.camera().getY(), "native feet are framed for typing");
                        }
                    }
                    if (mode.equals("ESCORT")) {
                        var riding = GameServices.level().getObjectManager().getRidingObject(fixture.sprite());
                        assertInstanceOf(TornadoObjectInstance.class, riding);
                        assertFalse(riding.isDestroyed());
                    }
                    if (!inspectedHolds) {
                        inspectedHolds = true;
                        verifyPauseSlowAndRetry(course, fixture, input, route);
                    }
                    typeNext(course, view, input);
                } else if (phase.equals("BOSS")) {
                    bossSeen = true;
                    if (mode.equals("FINALE")) {
                        assertTrue(fixture.sprite().isSuperSonic(), "DDZ keeps actual native Super Sonic");
                        assertEquals(SuperState.SUPER, fixture.sprite().getSuperStateController().getState());
                    } else {
                        assertNotNull(route);
                        assertEquals(5, get(route, "arrived"));
                        assertTrue((boolean) get(route, "grounded"));
                    }
                    typeNext(course, view, input);
                }
                if (phase.equals("LEAVING")) break;
                row(fixture, input);
            }
            Object finalView = invoke(course, "view");
            assertEquals("LEAVING", get(finalView, "phase"), key + " never completed: " + finalView);
            assertTrue(Files.readString(storage.resolve("result.txt")).contains("cleared=true"), key);
            assertNotNull(mode);
            if (mode.equals("GROUND")) {
                assertEquals(Set.of(1, 2, 3, 4), fights, key);
                assertEquals(4, groundX.size(), key + " needs distinct forward fights");
                assertTrue(movedRows >= 20, key + " must move through real terrain between fights");
                assertTrue(mappingChanges > 0, key + " uses native animated movement");
                assertTrue(bossSeen, key);
            } else if (mode.equals("ESCORT")) {
                assertEquals(Set.of(1, 2, 3, 4), fights);
                assertFalse(bossSeen, "Sky Chase clears its four air waves");
            } else {
                assertTrue(fights.isEmpty(), "DDZ is boss only");
                assertTrue(bossSeen);
            }
            assertTrue(harness.findings().isEmpty(), harness.findings().toString());
            invoke(course, "close");
            invoke(course, "close");
            System.out.println("SECTION|" + key + " mode=" + mode + " fights=" + fights + " movedRows=" + movedRows
                    + " boss=" + bossSeen + " cleared=true");
        } finally {
            SessionManager.clear();
            bootstrap.dispose();
            GameServices.configuration().clearSessionOverrides();
        }
    }

    private static void verifyPauseSlowAndRetry(Object course, HeadlessTestFixture fixture,
            InputHandler input, Object route) throws Exception {
        input.handleKeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE, org.lwjgl.glfw.GLFW.GLFW_PRESS);
        row(fixture, input);
        input.handleKeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE, org.lwjgl.glfw.GLFW.GLFW_RELEASE);
        row(fixture, input);
        assertTrue((boolean) get(invoke(course, "view"), "paused"));
        for (int i = 0; i < 8; i++) row(fixture, input);
        assertEquals(route, travel(course), "pause does not consume private route state");
        input.handleKeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE, org.lwjgl.glfw.GLFW.GLFW_PRESS);
        row(fixture, input);
        input.handleKeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE, org.lwjgl.glfw.GLFW.GLFW_RELEASE);
        row(fixture, input);
        field(course, "slowRows").setInt(course, 4);
        field(course, "slowPhase").setInt(course, 1);
        row(fixture, input);
        assertEquals(route, travel(course), "slow HOLD does not consume private route state");
        Method retry = course.getClass().getDeclaredMethod("retryStand");
        retry.setAccessible(true);
        retry.invoke(course);
        row(fixture, input);
        assertEquals("STAND", get(invoke(course, "view"), "phase"));
        assertEquals(route, travel(course), "retry restores matching private route checkpoint");
        if (route != null) {
            assertEquals((int) Math.round((double) get(route, "x")), fixture.sprite().getCentreX());
            assertEquals((int) Math.round((double) get(route, "y")), fixture.sprite().getCentreY());
        }
    }

    private static Object travel(Object course) throws Exception {
        Object section = invoke(course, "sectionView");
        return section == null ? null : get(section, "travel");
    }
    private static void typeNext(Object course, Object view, InputHandler input) throws Exception {
        @SuppressWarnings("unchecked") List<Object> targets = (List<Object>) get(view, "targets");
        Object encounter = field(course, "encounter").get(course);
        Method preferred = encounter.getClass().getDeclaredMethod("preferredTarget");
        preferred.setAccessible(true);
        int id = (int) preferred.invoke(encounter);
        Object choice = null;
        for (Object target : targets) if ((boolean) get(target, "locked")) choice = target;
        if (choice == null && id >= 0) for (Object target : targets) if ((int) get(target, "id") == id) choice = target;
        if (choice == null) for (Object target : targets) if ((boolean) get(target, "typeable")) { choice = target; break; }
        if (choice == null) return;
        String word = (String) get(choice, "typed");
        int progress = (int) get(choice, "progress");
        word.substring(progress).codePoints().forEach(cp -> MenuInput.handleCharEvent(input, cp));
    }
    private static void row(HeadlessTestFixture fixture, InputHandler input) {
        ControlledFrameRuntime.step(fixture.runtime(), input, LogicalInputSnapshot.neutral());
        input.update();
    }
    private static Field field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field;
    }
    private static Object invoke(Object object, String name) throws Exception { return object.getClass().getMethod(name).invoke(object); }
    private static Object get(Object object, String name) throws Exception {
        Method method = object.getClass().getMethod(name); method.setAccessible(true); return method.invoke(object);
    }
}
