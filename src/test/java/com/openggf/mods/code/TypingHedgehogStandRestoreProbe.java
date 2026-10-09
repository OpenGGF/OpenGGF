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
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.session.SessionManager;
import com.openggf.level.objects.ModRewindRecreatable;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Diagnoses a Typing of the Hedgehog stand restore that cannot resolve a captured object
 * reference (first seen on the S3K HCZ1 rail: {@code Missing required object reference ...
 * dynamicId=92} from {@code DefaultSolidExecutionRegistry.restore}).
 *
 * <p>Part A drives the rail through the packaged mod's real Course exactly as
 * {@code TestTypingHedgehogRails} does, takes a census of every live object (rewind identity,
 * class, {@link RewindRecreatable}/{@link ModRewindRecreatable}) at each stand capture, tracks
 * which census objects stay alive through the held stand, and on the restore failure names the
 * missing object's class and when it was last alive. Part B replays the same rail with the rail
 * builder's controller (no typing layer), holds the failing stand for the same number of rows and
 * for the builder's 1850-row soak, and reports whether an object of that class is alive at the
 * restore and whether the restore throws. Opt-in: {@code -Dtypinghog.restoreprobe=s3k-hcz1.rail}.
 * Origin: rails lane, 2026-10-09.
 */
class TypingHedgehogStandRestoreProbe {
    private static final String RAIL = System.getProperty("typinghog.restoreprobe");
    static final String MOD = "typing-of-the-hedgehog";
    static final Path SOURCE = Path.of("examples/typing-of-the-hedgehog");

    @TempDir Path temp;
    private SharedLevel bootstrap;

    @AfterEach void dispose() {
        if (bootstrap != null) bootstrap.dispose();
        bootstrap = null;
        GameServices.configuration().clearSessionOverrides();
    }

    record Census(int dynamicId, int spawnId, String kind, String type, boolean recreatable, boolean modRecreatable,
                  int x, int y) { }

    static Map<Integer, Census> census() {
        Map<Integer, Census> out = new LinkedHashMap<>();
        var query = GameServices.level().getObjectManager().objectQuery();
        for (ObjectInstance object : query.activeObjectsOfType(ObjectInstance.class)) {
            var id = query.identityOf(object);
            if (id.isEmpty()) continue;
            ObjectRefId ref = id.get();
            int x = -1, y = -1;
            try {
                x = object.getX();
                y = object.getY();
            } catch (RuntimeException spawnless) {
                // Spawnless dynamic objects have no placement position to report.
            }
            out.put(ref.dynamicId(), new Census(ref.dynamicId(), ref.spawnId(), ref.kind().name(),
                    object.getClass().getName(), object instanceof RewindRecreatable,
                    object instanceof ModRewindRecreatable, x, y));
        }
        return out;
    }

    @Test @RequiresRom(SonicGame.SONIC_3K) void standRestoreReferences() throws Exception {
        Assumptions.assumeTrue(RAIL != null, "opt-in: -Dtypinghog.restoreprobe=<rail file>");
        Path railFile = SOURCE.resolve("src/main/resources/rails").resolve(RAIL);
        String text = Files.readString(railFile);
        int zone = intKey(text, "zone"), act = intKey(text, "act");
        String team = strKey(text, "team");
        String sidekick = team.contains(",") ? team.substring(team.indexOf(',') + 1) : "";
        // ---- Part A: the real Course.
        Path project = temp.resolve("project");
        try (Stream<Path> files = Files.walk(SOURCE)) {
            for (Path p : files.toList()) {
                Path target = project.resolve(SOURCE.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(target);
                else Files.copy(p, target);
            }
        }
        // The probed rail may be held out of the shipped index: list it in the copy.
        Path index = project.resolve("src/main/resources/rails/index.txt");
        String listed = Files.exists(index) ? Files.readString(index) : "";
        if (!listed.lines().map(String::strip).toList().contains(RAIL)) Files.writeString(index, listed + "\n" + RAIL + "\n");
        Path work = temp.resolve("work");
        int failingStandRow = -1, heldRows = -1;
        String culpritType = null;
        try (ExampleModHarness harness = ExampleModHarness.build(project, work)) {
            bootstrap = SharedLevel.load(SonicGame.SONIC_3K, zone, act);
            var config = SonicConfigurationService.getInstance();
            config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
            config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, sidekick);
            config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, "NATIVE_4_3");
            config.resolveDisplayAspect();
            config.setSessionOverride(SonicConfiguration.LOAD_TIME_SIMULATION, "FAST");
            config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);
            config.setSessionOverride(SonicConfiguration.S3K_SKIP_INTROS, false);
            GameServices.graphics().setProjectionWidth(config.getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS));
            GameModule effective = harness.apply(GameServices.module());
            Path storage = work.resolve("storage").resolve("mods").resolve(MOD);
            Files.createDirectories(storage);
            Files.writeString(storage.resolve("intent.txt"), "mode=ARCADE\ngame=s3k\nzone=" + zone + "\nact=" + act
                    + "\ndifficulty=EASY\ncapitals=false\npunctuation=false\ndictionaries=standard\nseed=7\n"
                    + "bossStars=1\nnoFail=true\nslowMotion=false\n");
            SessionManager.clear();
            GameModuleRegistry.setCurrent(effective);
            TestEnvironment.activeGameplayMode();
            var fixture = HeadlessTestFixture.builder().withZoneAndAct(zone, act).build();
            var input = new InputHandler();
            Class<?> courseType = harness.loader().loadClass("typinghog.play.Course");
            Map<Integer, Census> census = Map.of();
            Map<Integer, Integer> lastAlive = new LinkedHashMap<>();
            int standRow = -1, held = 0;
            String previous = "";
            for (int i = 0; i < 200_000; i++) {
                Object course = GameModuleRegistry.getCurrent().getGameService(courseType);
                Object view = course.getClass().getMethod("view").invoke(course);
                String phase = (String) get(view, "phase");
                int railRow = (int) get(view, "railRow");
                if (i == 600 && (phase.equals("IDLE") || railRow < 0)) {
                    System.out.println("RESTOREPROBE|A the course never started the rail (phase " + phase + ")");
                    break;
                }
                if (phase.equals("BOSS") || phase.equals("TALLY")) {
                    System.out.println("RESTOREPROBE|A reached " + phase + " at rail row " + railRow + " without a restore failure");
                    break;
                }
                if (phase.equals("STAND") && previous.equals("RUN")) {
                    census = census();
                    lastAlive.clear();
                    standRow = railRow;
                    held = 0;
                    System.out.println("RESTOREPROBE|A stand at rail row " + railRow + ": " + census.size() + " live objects");
                }
                if (phase.equals("STAND") || phase.equals("STAND_CLEAR") || phase.equals("DYING")) {
                    held++;
                    var live = census();
                    for (int id : census.keySet()) if (live.containsKey(id)) lastAlive.put(id, held);
                }
                previous = phase;
                if (phase.equals("STAND")) typeNext(view, input);
                try {
                    ControlledFrameRuntime.step(fixture.runtime(), input, LogicalInputSnapshot.neutral());
                    input.update();
                } catch (Throwable t) {
                    Throwable cause = t;
                    while (cause.getCause() != null) cause = cause.getCause();
                    System.out.println("RESTOREPROBE|A restore failed after " + held + " held rows at the stand on rail row "
                            + standRow + ": " + cause);
                    Matcher m = Pattern.compile("dynamicId=(\\d+)").matcher(String.valueOf(cause.getMessage()));
                    if (m.find()) {
                        int id = Integer.parseInt(m.group(1));
                        Census c = census.get(id);
                        System.out.println("RESTOREPROBE|A missing dynamicId " + id + ": " + (c == null
                                ? "not in the stand-capture census" : c + ", last alive on held row "
                                + lastAlive.getOrDefault(id, -1) + " of " + held));
                        if (c != null) culpritType = c.type();
                    }
                    failingStandRow = standRow;
                    heldRows = held;
                    break;
                }
            }
        }
        dispose();
        if (failingStandRow < 0 || culpritType == null) return;
        // ---- Part B: the builder's controller, same stand, held for the same rows and for the soak.
        var builder = new TypingHedgehogRailBuilderProbe();
        var target = TypingHedgehogRailBuilderProbe.targets(SonicGame.SONIC_3K).stream()
                .filter(t -> RAIL.equals(t.file())).findFirst().orElseThrow();
        int[] masks = programMasks(text);
        int[] src = new int[masks.length];
        java.util.Arrays.fill(src, -1);
        var program = new TypingHedgehogRailBuilderProbe.Program(masks, src);
        for (int hold : new int[] {heldRows, TypingHedgehogRailBuilderProbe.SOAK_ROWS + TypingHedgehogRailBuilderProbe.STAND_CLEAR_ROWS}) {
            var s = builder.new Session(target, program);
            try {
                while (s.driver.row < failingStandRow) s.step();
                var before = census();
                var mark = s.mark();
                s.driver.holding = true;
                for (int i = 0; i < hold; i++) s.step();
                s.driver.holding = false;
                var after = census();
                final String type = culpritType;
                long atCapture = before.values().stream().filter(c -> c.type().equals(type)).count();
                long atRestore = after.values().stream().filter(c -> c.type().equals(type)
                        && before.containsKey(c.dynamicId())).count();
                String restore;
                try {
                    s.restore(mark, program.masks());
                    restore = "restore ok";
                } catch (Throwable t) {
                    Throwable cause = t;
                    while (cause.getCause() != null) cause = cause.getCause();
                    restore = "restore threw " + cause;
                }
                System.out.println("RESTOREPROBE|B hold " + hold + " rows at rail row " + failingStandRow + ": "
                        + atCapture + " " + type + " at capture, " + atRestore + " of them alive at restore; " + restore);
            } finally {
                s.close();
                GameServices.configuration().clearSessionOverrides();
            }
        }
    }

    /**
     * The S2 MTZ act 3 boss art question: drives {@code s2-mtz3.rail} through the real Course and
     * reports, at 240 controlled rows and at the rail's boss stand, whether the object art provider
     * has a {@code mtz_boss} renderer and whether a stock MTZ boss is live (and has one to draw
     * with). Opt-in: {@code -Dtypinghog.mtz3art=true}.
     */
    @Test @RequiresRom(SonicGame.SONIC_2) void mtz3BossArt() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("typinghog.mtz3art"), "opt-in: -Dtypinghog.mtz3art=true");
        String text = Files.readString(SOURCE.resolve("src/main/resources/rails/s2-mtz3.rail"));
        int zone = intKey(text, "zone"), act = intKey(text, "act"), boss = intKey(text, "boss");
        Path project = temp.resolve("project-mtz3");
        try (Stream<Path> files = Files.walk(SOURCE)) {
            for (Path p : files.toList()) {
                Path target = project.resolve(SOURCE.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(target);
                else Files.copy(p, target);
            }
        }
        Path work = temp.resolve("work-mtz3");
        try (ExampleModHarness harness = ExampleModHarness.build(project, work)) {
            bootstrap = SharedLevel.load(SonicGame.SONIC_2, zone, act);
            var config = SonicConfigurationService.getInstance();
            config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
            config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
            config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, "NATIVE_4_3");
            config.resolveDisplayAspect();
            config.setSessionOverride(SonicConfiguration.LOAD_TIME_SIMULATION, "FAST");
            config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);
            GameServices.graphics().setProjectionWidth(config.getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS));
            GameModule effective = harness.apply(GameServices.module());
            Path storage = work.resolve("storage").resolve("mods").resolve(MOD);
            Files.createDirectories(storage);
            Files.writeString(storage.resolve("intent.txt"), "mode=ARCADE\ngame=s2\nzone=" + zone + "\nact=" + act
                    + "\ndifficulty=EASY\ncapitals=false\npunctuation=false\ndictionaries=standard\nseed=7\n"
                    + "bossStars=1\nnoFail=true\nslowMotion=false\n");
            SessionManager.clear();
            GameModuleRegistry.setCurrent(effective);
            TestEnvironment.activeGameplayMode();
            var fixture = HeadlessTestFixture.builder().withZoneAndAct(zone, act).build();
            var input = new InputHandler();
            Class<?> courseType = harness.loader().loadClass("typinghog.play.Course");
            System.out.println("RESTOREPROBE|M MTZ3 loaded: engine zone " + GameServices.level().getCurrentZone()
                    + " act " + GameServices.level().getCurrentAct() + "; renderer keys "
                    + GameServices.module().getObjectArtProvider().getRendererKeys().stream()
                            .filter(k -> k.contains("boss")).toList());
            for (int i = 0; i < 40_000; i++) {
                Object course = GameModuleRegistry.getCurrent().getGameService(courseType);
                Object view = course.getClass().getMethod("view").invoke(course);
                String phase = (String) get(view, "phase");
                if (i == 240 || phase.equals("BOSS")) {
                    var art = GameServices.module().getObjectArtProvider();
                    var bosses = GameServices.level().getObjectManager().objectQuery()
                            .activeObjectsOfType(com.openggf.game.sonic2.objects.bosses.Sonic2MTZBossInstance.class);
                    var p = GameServices.camera().getFocusedSprite();
                    System.out.println("RESTOREPROBE|M " + (phase.equals("BOSS") ? "boss stand (rail row " + get(view, "railRow")
                            + ", rail boss " + boss + ")" : "row 240") + ": mtz_boss renderer "
                            + (art.getRenderer("mtz_boss") == null ? "NULL" : "present")
                            + "; live MTZ bosses " + bosses.size() + "; Sonic x=" + p.getCentreX()
                            + " camera x=" + GameServices.camera().getX() + " boss flag "
                            + GameServices.gameState().isBossFightActive());
                    if (phase.equals("BOSS")) break;
                }
                if (phase.equals("STAND")) typeNext(view, input);
                ControlledFrameRuntime.step(fixture.runtime(), input, LogicalInputSnapshot.neutral());
                input.update();
            }
        }
    }

    static int[] programMasks(String text) {
        List<Integer> out = new ArrayList<>();
        int validated = intKey(text, "validated");
        for (String line : text.split("\n")) {
            if (!line.startsWith("program ")) continue;
            for (String pair : line.substring(8).split(",")) {
                String[] p = pair.strip().split(":");
                int frames = Integer.parseInt(p[0]);
                int mask = Integer.parseInt(p[1], 16);
                for (int i = 0; i < frames; i++) out.add(mask);
            }
        }
        return out.stream().limit(validated).mapToInt(Integer::intValue).toArray();
    }

    static int intKey(String text, String key) { return Integer.parseInt(strKey(text, key)); }

    static String strKey(String text, String key) {
        for (String line : text.split("\n")) if (line.startsWith(key + " ")) return line.substring(key.length() + 1).strip();
        throw new IllegalArgumentException("rail has no " + key);
    }

    private static void typeNext(Object view, InputHandler input) throws Exception {
        @SuppressWarnings("unchecked")
        List<Object> targets = (List<Object>) get(view, "targets");
        if (targets.isEmpty()) return;
        String typed = (String) get(targets.getFirst(), "typed");
        int progress = (int) get(targets.getFirst(), "progress");
        if (progress < typed.length()) MenuInput.handleCharEvent(input, typed.codePointAt(progress));
    }

    private static Object get(Object record, String component) throws Exception {
        Method method = record.getClass().getMethod(component);
        method.setAccessible(true);
        return method.invoke(record);
    }
}
