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
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Drift gate for the shipped Typing of the Hedgehog rails: every rail listed in
 * {@code rails/index.txt} is driven through the packaged mod's real {@code Course} from a cold act
 * start (320 px, forced FAST load time, the rail's team, stock badniks present), with every stand
 * cleared by typing its prompts. Each rail must reach its boss stand with no native hurt or death on
 * any row, including the held stand rows, so a rail that drifts after an engine change fails here.
 * Rails are built and repaired by {@code TypingHedgehogRailBuilderProbe}.
 */
@Tag("slow-suite")
class TestTypingHedgehogRails {
    static final String MOD = "typing-of-the-hedgehog";
    static final Path SOURCE = Path.of("examples/typing-of-the-hedgehog");

    @TempDir static Path temp;
    static ExampleModHarness harness;
    private SharedLevel bootstrap;

    record Spec(String file, String game, int zone, int act, String sidekick, int validated, int boss, int stands) { }

    @BeforeAll static void copyProject() throws Exception {
        Path project = temp.resolve("project");
        try (Stream<Path> files = Files.walk(SOURCE)) {
            for (Path p : files.toList()) {
                Path target = project.resolve(SOURCE.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(target);
                else Files.copy(p, target);
            }
        }
        harness = ExampleModHarness.build(project, temp.resolve("work"));
    }

    @AfterAll static void closeHarness() throws Exception {
        if (harness != null) harness.close();
    }

    @AfterEach void dispose() {
        if (bootstrap != null) bootstrap.dispose();
        bootstrap = null;
        GameServices.configuration().clearSessionOverrides();
    }

    static List<Spec> shipped(String game) throws Exception {
        Path rails = SOURCE.resolve("src/main/resources/rails");
        List<Spec> specs = new ArrayList<>();
        if (!Files.exists(rails.resolve("index.txt"))) return specs;
        for (String line : Files.readAllLines(rails.resolve("index.txt"))) {
            String name = line.strip();
            if (name.isEmpty() || name.startsWith("#")) continue;
            String g = null, team = "sonic";
            int zone = -1, act = -1, validated = -1, boss = -1, stands = 0;
            for (String raw : Files.readAllLines(rails.resolve(name))) {
                String l = raw.strip();
                if (l.startsWith("#") || l.isEmpty()) continue;
                String[] kv = l.split(" ", 2);
                switch (kv[0]) {
                    case "game" -> g = kv[1].strip();
                    case "zone" -> zone = Integer.parseInt(kv[1].strip());
                    case "act" -> act = Integer.parseInt(kv[1].strip());
                    case "team" -> team = kv[1].strip();
                    case "validated" -> validated = Integer.parseInt(kv[1].strip());
                    case "boss" -> boss = Integer.parseInt(kv[1].strip());
                    case "stand" -> stands++;
                    default -> { }
                }
            }
            if (!game.equals(g)) continue;
            String sidekick = team.contains(",") ? team.substring(team.indexOf(',') + 1) : "";
            specs.add(new Spec(name, g, zone, act, sidekick, validated, boss < 0 ? validated : boss, stands));
        }
        return specs;
    }

    @Test @RequiresRom(SonicGame.SONIC_1) void s1RailsRunCleanThroughTheRealCourse() throws Exception {
        check("s1", SonicGame.SONIC_1);
    }

    @Test @RequiresRom(SonicGame.SONIC_2) void s2RailsRunCleanThroughTheRealCourse() throws Exception {
        check("s2", SonicGame.SONIC_2);
    }

    @Test @RequiresRom(SonicGame.SONIC_3K) void s3kRailsRunCleanThroughTheRealCourse() throws Exception {
        check("s3k", SonicGame.SONIC_3K);
    }

    private void check(String game, SonicGame sonicGame) throws Exception {
        List<Spec> specs = shipped(game);
        Assumptions.assumeFalse(specs.isEmpty(), "no shipped " + game + " rails");
        List<String> faults = new ArrayList<>();
        for (Spec spec : specs) {
            String fault = drive(spec, sonicGame);
            if (fault != null) faults.add(spec.file() + ": " + fault);
            dispose();
        }
        assertTrue(faults.isEmpty(), "rail faults:\n" + String.join("\n", faults));
    }

    private Path storage() { return temp.resolve("work").resolve("storage").resolve("mods").resolve(MOD); }

    /** Runs one rail to its boss stand; returns a fault description or null. */
    private String drive(Spec spec, SonicGame sonicGame) throws Exception {
        bootstrap = SharedLevel.load(sonicGame, spec.zone(), spec.act());
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, spec.sidekick());
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, "NATIVE_4_3");
        config.resolveDisplayAspect();
        config.setSessionOverride(SonicConfiguration.LOAD_TIME_SIMULATION, "FAST");
        config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);
        config.setSessionOverride(SonicConfiguration.S3K_SKIP_INTROS, false);
        GameServices.graphics().setProjectionWidth(config.getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS));
        GameModule effective = harness.apply(GameServices.module());
        Files.createDirectories(storage());
        Files.writeString(storage().resolve("intent.txt"), "mode=ARCADE\ngame=" + spec.game() + "\nzone=" + spec.zone()
                + "\nact=" + spec.act() + "\ndifficulty=EASY\ncapitals=false\npunctuation=false\n"
                + "dictionaries=standard\nseed=7\nbossStars=1\nnoFail=true\nslowMotion=false\n");
        SessionManager.clear();
        GameModuleRegistry.setCurrent(effective);
        TestEnvironment.activeGameplayMode();
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(spec.zone(), spec.act()).build();
        if (spec.game().equals("s3k") && spec.zone() == 12) {
            GameServices.gameState().restoreS3kEmeraldProgress(java.util.Collections.nCopies(7, 1), false);
        }
        var input = new InputHandler();
        int limit = spec.validated() + spec.stands() * 6000 + 20000;
        int standsSeen = 0;
        String previousPhase = "";
        for (int i = 0; i < limit; i++) {
            Object view = view();
            String phase = (String) get(view, "phase");
            int railRow = (int) get(view, "railRow");
            if (!phase.equals(previousPhase) && phase.equals("STAND")) standsSeen++;
            previousPhase = phase;
            if (phase.equals("BOSS")) {
                if (railRow != spec.boss()) return "boss stand began on row " + railRow + ", rail says " + spec.boss();
                if (standsSeen != spec.stands()) return "saw " + standsSeen + " stands, rail lists " + spec.stands();
                return null;
            }
            if (phase.equals("STAND")) typeNext(view, input);
            ControlledFrameRuntime.step(fixture.runtime(), input, LogicalInputSnapshot.neutral());
            input.update();
            var player = (AbstractPlayableSprite) GameServices.camera().getFocusedSprite();
            if (player.getDead()) return "native death in " + phase + " at rail row " + railRow;
            if (player.isHurt()) return "native hurt in " + phase + " at rail row " + railRow
                    + " (" + player.getCentreX() + "," + player.getCentreY() + ")";
            if (GameServices.level().getCurrentZone() != spec.zone() || GameServices.level().getCurrentAct() != spec.act()) {
                return "level changed at rail row " + railRow;
            }
        }
        return "boss stand not reached within " + limit + " rows; view " + view();
    }

    private static void typeNext(Object view, InputHandler input) throws Exception {
        @SuppressWarnings("unchecked")
        List<Object> targets = (List<Object>) get(view, "targets");
        if (targets.isEmpty()) return;
        String typed = (String) get(targets.getFirst(), "typed");
        int progress = (int) get(targets.getFirst(), "progress");
        if (progress < typed.length()) MenuInput.handleCharEvent(input, typed.codePointAt(progress));
    }

    private Object view() throws Exception {
        Class<?> type = harness.loader().loadClass("typinghog.play.Course");
        Object course = GameModuleRegistry.getCurrent().getGameService(type);
        assertNotNull(course, "the packaged mod registers its Course as a game service");
        return course.getClass().getMethod("view").invoke(course);
    }

    private static Object get(Object record, String component) throws Exception {
        Method method = record.getClass().getMethod(component);
        method.setAccessible(true);
        return method.invoke(record);
    }
}
