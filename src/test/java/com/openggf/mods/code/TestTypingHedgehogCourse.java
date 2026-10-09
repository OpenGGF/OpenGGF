package com.openggf.mods.code;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.MenuInput;
import com.openggf.game.GameModule;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.mode.ControlledFrameRuntime;
import com.openggf.game.session.SessionManager;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Drives the packaged Typing of the Hedgehog course on real Green Hill 1 with a synthetic test
 * rail (stand still, then hold right; one stand, then the boss): the rail reaches its stand,
 * badniks arrive with words, typing them kills them, the wave clears, and the stand's checkpoint
 * restore resumes the deterministic run exactly where it stopped.
 */
@RequiresRom(SonicGame.SONIC_1)
class TestTypingHedgehogCourse {
    static final String MOD = "typing-of-the-hedgehog";
    static final int STAND_ROW = 240;
    static final int BOSS_ROW = 600;

    @TempDir static Path temp;
    static Path project;
    static ExampleModHarness harness;
    private SharedLevel bootstrap;

    @BeforeAll static void copyProject() throws Exception {
        project = temp.resolve("project");
        Path source = Path.of("examples/typing-of-the-hedgehog");
        try (Stream<Path> files = Files.walk(source)) {
            for (Path p : files.toList()) {
                Path target = project.resolve(source.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(target);
                else Files.copy(p, target);
            }
        }
        Path rails = project.resolve("src/main/resources/rails");
        Files.createDirectories(rails);
        Files.writeString(rails.resolve("test-ghz1.rail"), String.join("\n",
                "game s1", "zone 0", "act 0", "team sonic", "validated 640",
                "program 120:0,520:8", "stand " + STAND_ROW, "boss " + BOSS_ROW, ""));
        Path index = rails.resolve("index.txt");
        String existing = Files.exists(index) ? Files.readString(index) : "";
        Files.writeString(index, existing + "\ntest-ghz1.rail\n");
        Path cast = project.resolve("src/main/resources/cast/s1.txt");
        Files.createDirectories(cast.getParent());
        if (!Files.exists(cast) || Files.readString(cast).isBlank()) {
            Files.writeString(cast, "0 Motobug motobug RUSHER false 20 14 8 0,1\n");
        }
        harness = ExampleModHarness.build(project, temp.resolve("work"));
    }

    @org.junit.jupiter.api.AfterAll static void closeHarness() throws Exception {
        if (harness != null) harness.close();
    }

    @AfterEach void dispose() throws Exception {
        if (bootstrap != null) bootstrap.dispose();
        bootstrap = null;
    }

    private Path storage() { return temp.resolve("work").resolve("storage").resolve("mods").resolve(MOD); }

    private HeadlessTestFixture launch(String intent) throws Exception {
        bootstrap = SharedLevel.load(SonicGame.SONIC_1, 0, 0);
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, "NATIVE_4_3");
        config.resolveDisplayAspect();
        GameServices.graphics().setProjectionWidth(config.getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS));
        GameModule effective = harness.apply(GameServices.module());
        Files.createDirectories(storage());
        Files.writeString(storage().resolve("intent.txt"), intent);
        SessionManager.clear();
        GameModuleRegistry.setCurrent(effective);
        TestEnvironment.activeGameplayMode();
        return HeadlessTestFixture.builder().withZoneAndAct(0, 0).build();
    }

    private static String intent(long seed) {
        return "mode=ARCADE\ngame=s1\nzone=0\nact=0\ndifficulty=EASY\ncapitals=false\npunctuation=false\n"
                + "dictionaries=standard\nseed=" + seed + "\nbossStars=1\nnoFail=true\nslowMotion=false\n";
    }

    private Object course() throws Exception {
        Class<?> type = harness.loader().loadClass("typinghog.play.Course");
        return GameModuleRegistry.getCurrent().getGameService(type);
    }

    private Object view() throws Exception {
        Object course = course();
        return course.getClass().getMethod("view").invoke(course);
    }

    private static Object get(Object record, String component) throws Exception {
        Method method = record.getClass().getMethod(component);
        method.setAccessible(true);
        return method.invoke(record);
    }

    private static void row(HeadlessTestFixture fixture, InputHandler input) {
        ControlledFrameRuntime.step(fixture.runtime(), input, LogicalInputSnapshot.neutral());
        input.update();
    }

    /** Steps until the course reports {@code phase}, or fails after {@code limit} rows. */
    private void stepUntil(HeadlessTestFixture fixture, InputHandler input, String phase, int limit) throws Exception {
        for (int i = 0; i < limit; i++) {
            if (phase.equals(get(view(), "phase"))) return;
            row(fixture, input);
        }
        fail("course never reached " + phase + "; view " + view());
    }

    @Test void theRailReachesItsStandAndTypedWordsKillTheWave() throws Exception {
        var fixture = launch(intent(42));
        var input = new InputHandler();
        stepUntil(fixture, input, "STAND", 900);
        assertEquals(STAND_ROW, get(view(), "railRow"), "the stand starts on its rail row");
        var art = GameServices.module().getObjectArtProvider();
        for (String key : List.of("motobug", "explosion", "animal", "points")) {
            var sheet = art.getSheet(key);
            assertNotNull(art.getRenderer(key), "puppets draw " + key + " with the zone's loaded ROM art");
            System.out.println("[typing-hedgehog] S1 GHZ1 sheet " + key + ": "
                    + (sheet == null ? "null" : sheet.getFrameCount() + " frames"));
        }
        int kills = 0;
        for (int i = 0; i < 6000 && "STAND".equals(get(view(), "phase")); i++) {
            @SuppressWarnings("unchecked")
            List<Object> targets = (List<Object>) get(view(), "targets");
            if (!targets.isEmpty()) {
                String typed = (String) get(targets.getFirst(), "typed");
                int progress = (int) get(targets.getFirst(), "progress");
                if (progress < typed.length()) MenuInput.handleCharEvent(input, typed.codePointAt(progress));
            }
            row(fixture, input);
            kills = (int) get(view(), "kills");
        }
        assertTrue(kills >= 6, "typing killed the wave; kills " + kills + " view " + view());
        stepUntil(fixture, input, "RUN", 400);
        assertEquals(STAND_ROW + 1, get(view(), "railRow"),
                "the restore's row plays the stand's own rail row, so the run resumes where it stopped");
        assertEquals(3, get(view(), "lives"));
    }

    @Test void aStandOfAnyLengthLeavesTheRailRunUnchanged() throws Exception {
        int[] fast = runPastStand(7, 0);
        dispose();
        int[] slow = runPastStand(7, 400);
        assertArrayEquals(fast, slow, "player state after the stand must not depend on how long it took");
    }

    /** Clears the stand after {@code idleRows} rows of not typing, then returns Sonic's state 200 rail rows later. */
    private int[] runPastStand(long seed, int idleRows) throws Exception {
        var fixture = launch(intent(seed));
        var input = new InputHandler();
        stepUntil(fixture, input, "STAND", 900);
        for (int i = 0; i < idleRows; i++) row(fixture, input);
        // Type whatever is on screen until the run resumes; hits and No-Fail retries may intervene.
        for (int i = 0; i < 12000 && !"RUN".equals(get(view(), "phase")); i++) {
            @SuppressWarnings("unchecked")
            List<Object> targets = (List<Object>) get(view(), "targets");
            if (!targets.isEmpty()) {
                String typed = (String) get(targets.getFirst(), "typed");
                int progress = (int) get(targets.getFirst(), "progress");
                if (progress < typed.length()) MenuInput.handleCharEvent(input, typed.codePointAt(progress));
            }
            row(fixture, input);
        }
        stepUntil(fixture, input, "RUN", 400);
        for (int i = 0; i < 200; i++) row(fixture, input);
        var player = fixture.sprite();
        return new int[] {player.getCentreX(), player.getCentreY(), player.getXSpeed(), player.getYSpeed(),
                (int) get(view(), "railRow")};
    }
}
