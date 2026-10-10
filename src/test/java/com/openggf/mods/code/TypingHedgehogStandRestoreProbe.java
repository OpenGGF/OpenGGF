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
 * {@code standAllocationCensus} (add {@code -Dtypinghog.restoreprobe.census=true}) compares the
 * live-object census of the packaged Course and the rail builder row by row up to the first stand
 * and prints the first row where they differ.
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

    // ------------------------------------------------------------------ divergence experiment

    /** A packaged-mod Course session on one S3K rail, stepped one row at a time. */
    final class CourseRun {
        final ExampleModHarness harness;
        final HeadlessTestFixture fixture;
        final InputHandler input = new InputHandler();
        final Class<?> courseType;

        CourseRun(String tag, int zone, int act, String sidekick) throws Exception {
            Path project = temp.resolve("project-" + tag);
            try (Stream<Path> files = Files.walk(SOURCE)) {
                for (Path p : files.toList()) {
                    Path target = project.resolve(SOURCE.relativize(p).toString());
                    if (Files.isDirectory(p)) Files.createDirectories(target);
                    else Files.copy(p, target);
                }
            }
            Path index = project.resolve("src/main/resources/rails/index.txt");
            String listed = Files.exists(index) ? Files.readString(index) : "";
            if (!listed.lines().map(String::strip).toList().contains(RAIL)) Files.writeString(index, listed + "\n" + RAIL + "\n");
            Path work = temp.resolve("work-" + tag);
            harness = ExampleModHarness.build(project, work);
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
            fixture = HeadlessTestFixture.builder().withZoneAndAct(zone, act).build();
            courseType = harness.loader().loadClass("typinghog.play.Course");
        }

        Object view() throws Exception {
            Object course = GameModuleRegistry.getCurrent().getGameService(courseType);
            return course.getClass().getMethod("view").invoke(course);
        }

        void step() {
            ControlledFrameRuntime.step(fixture.runtime(), input, LogicalInputSnapshot.neutral());
            input.update();
        }

        void close() throws Exception {
            harness.close();
            dispose();
        }
    }

    /** The object-manager snapshot's HCZ fan entries (and any entry with dynamic id 92). */
    static List<String> fanEntries(com.openggf.game.rewind.CompositeSnapshot snapshot) {
        List<String> out = new ArrayList<>();
        var om = (com.openggf.game.rewind.snapshot.ObjectManagerSnapshot) snapshot.get("object-manager");
        for (var e : om.slots()) {
            if (e.className() != null && e.className().contains("HCZCGZFan")) {
                out.add("slot " + e.slotIndex() + " " + simple(e.className()) + " " + e.objectId());
            }
        }
        for (var e : om.dynamicObjects()) {
            boolean id92 = e.objectId() != null && e.objectId().dynamicId() == 92;
            if (id92 || (e.className() != null && e.className().contains("HCZCGZFan"))) {
                out.add("dynamic " + simple(e.className()) + " slot " + e.slotIndex() + " " + e.objectId()
                        + " owner " + e.ownerModId() + " aux " + e.rewindableAuxiliary());
            }
        }
        out.add("dynamic entries " + om.dynamicObjects().size() + ", slot entries " + om.slots().size());
        return out;
    }

    /** Live HCZ fan objects with their current rewind identity and slot. */
    static List<String> liveFans() {
        List<String> out = new ArrayList<>();
        var query = GameServices.level().getObjectManager().objectQuery();
        for (ObjectInstance object : query.activeObjectsOfType(ObjectInstance.class)) {
            String name = object.getClass().getName();
            var id = query.identityOf(object);
            boolean id92 = id.isPresent() && id.get().dynamicId() == 92;
            if (name.contains("HCZCGZFan") || id92) {
                int slot = object instanceof com.openggf.level.objects.AbstractObjectInstance a ? a.getSlotIndex() : -9;
                out.add(simple(name) + " slot " + slot + " " + id.map(Object::toString).orElse("no identity"));
            }
        }
        return out;
    }

    static String simple(String className) { return className == null ? "null" : className.replaceAll(".*\\.", ""); }

    static List<Integer> stands(String text) {
        List<Integer> out = new ArrayList<>();
        for (String line : text.split("\n")) if (line.startsWith("stand ")) out.add(Integer.parseInt(line.substring(6).strip()));
        return out;
    }

    /**
     * Where the real Course and the builder chain first differ around the first stand of
     * {@code RAIL}: the captured object-manager entries for the HCZ fan objects, the identities
     * after the Course's failing restore, the same checkpoint restored from outside a mod callback
     * at the Course's restore row, and the builder chain's restore. Opt-in with
     * {@code -Dtypinghog.restoreprobe=<rail>} and {@code -Dtypinghog.restoreprobe.divergence=true}.
     */
    @Test @RequiresRom(SonicGame.SONIC_3K) void standRestoreDivergence() throws Exception {
        Assumptions.assumeTrue(RAIL != null && Boolean.getBoolean("typinghog.restoreprobe.divergence"),
                "opt-in: -Dtypinghog.restoreprobe=<rail> -Dtypinghog.restoreprobe.divergence=true");
        String text = Files.readString(SOURCE.resolve("src/main/resources/rails").resolve(RAIL));
        int zone = intKey(text, "zone"), act = intKey(text, "act");
        String team = strKey(text, "team");
        String sidekick = team.contains(",") ? team.substring(team.indexOf(',') + 1) : "";
        int standRow = stands(text).getFirst();
        com.openggf.game.rewind.CompositeSnapshot snapA = null;
        int heldUntilRestore = -1;
        // A1: the Course restores the stand itself.
        CourseRun a = new CourseRun("a1", zone, act, sidekick);
        try {
            int held = 0;
            String previous = "";
            for (int i = 0; i < 60_000; i++) {
                Object view = a.view();
                String phase = (String) get(view, "phase");
                int railRow = (int) get(view, "railRow");
                if (phase.equals("RUN") && railRow == standRow && snapA == null) {
                    snapA = SessionManager.getCurrentGameplayMode().getRewindRegistry().captureCourse();
                    System.out.println("DIVERGE|A1 capture before rail row " + standRow + " (the Course captures at this row's beforeTick):");
                    fanEntries(snapA).forEach(l -> System.out.println("DIVERGE|A1   snapshot " + l));
                    liveFans().forEach(l -> System.out.println("DIVERGE|A1   live " + l));
                }
                if (snapA != null && !phase.equals("RUN")) held++;
                if (phase.equals("STAND")) typeNext(view, a.input);
                if (!phase.equals(previous)) System.out.println("DIVERGE|A1 phase " + phase + " at held row " + held);
                previous = phase;
                try {
                    a.step();
                } catch (Throwable t) {
                    Throwable cause = t;
                    while (cause.getCause() != null) cause = cause.getCause();
                    heldUntilRestore = held;
                    System.out.println("DIVERGE|A1 Course restore failed on held row " + held + ": " + cause.getMessage());
                    liveFans().forEach(l -> System.out.println("DIVERGE|A1   live after the object phase " + l));
                    break;
                }
                if (phase.equals("RUN") && snapA != null && held > 0) {
                    System.out.println("DIVERGE|A1 Course restore succeeded; back in RUN");
                    break;
                }
            }
        } finally {
            a.close();
        }
        // A2: the same world, but the identical checkpoint is restored from outside a mod callback
        // on the row before the Course's own restore.
        CourseRun a2 = new CourseRun("a2", zone, act, sidekick);
        try {
            com.openggf.game.mode.CourseCheckpoint checkpoint = null;
            int held = 0;
            for (int i = 0; i < 60_000; i++) {
                Object view = a2.view();
                String phase = (String) get(view, "phase");
                int railRow = (int) get(view, "railRow");
                if (phase.equals("RUN") && railRow == standRow && checkpoint == null) {
                    checkpoint = com.openggf.game.mode.CourseControlProbeAccess.open(
                            SessionManager.getCurrentGameplayMode()).capture();
                }
                if (checkpoint != null && !phase.equals("RUN")) held++;
                if (checkpoint != null && heldUntilRestore > 0 && held == heldUntilRestore) {
                    try {
                        com.openggf.game.mode.CourseControlProbeAccess.open(SessionManager.getCurrentGameplayMode())
                                .restore(checkpoint);
                        System.out.println("DIVERGE|A2 outside restore of the same checkpoint on held row " + held + ": OK");
                    } catch (Throwable t) {
                        Throwable cause = t;
                        while (cause.getCause() != null) cause = cause.getCause();
                        System.out.println("DIVERGE|A2 outside restore of the same checkpoint on held row " + held + " failed: "
                                + cause.getMessage());
                    }
                    liveFans().forEach(l -> System.out.println("DIVERGE|A2   live after restore " + l));
                    break;
                }
                if (phase.equals("STAND")) typeNext(view, a2.input);
                a2.step();
            }
        } finally {
            a2.close();
        }
        // B: the builder's controller, same stand, held for the same rows.
        var builder = new TypingHedgehogRailBuilderProbe();
        var target = TypingHedgehogRailBuilderProbe.targets(SonicGame.SONIC_3K).stream()
                .filter(t -> RAIL.equals(t.file())).findFirst().orElseThrow();
        int[] masks = programMasks(text);
        int[] src = new int[masks.length];
        java.util.Arrays.fill(src, -1);
        var program = new TypingHedgehogRailBuilderProbe.Program(masks, src);
        var s = builder.new Session(target, program);
        try {
            while (s.driver.row < standRow) s.step();
            var snapB = SessionManager.getCurrentGameplayMode().getRewindRegistry().captureCourse();
            System.out.println("DIVERGE|B capture before rail row " + standRow + ":");
            fanEntries(snapB).forEach(l -> System.out.println("DIVERGE|B   snapshot " + l));
            liveFans().forEach(l -> System.out.println("DIVERGE|B   live " + l));
            if (snapA != null) {
                var diffs = com.openggf.game.rewind.RewindSnapshotDiff.diffKey("object-manager",
                        snapA.get("object-manager"), snapB.get("object-manager"));
                System.out.println("DIVERGE|A1-vs-B object-manager snapshot differences: " + diffs.size());
                diffs.stream().limit(8).forEach(d -> System.out.println("DIVERGE|   " + d));
            }
            var mark = s.mark();
            s.driver.holding = true;
            for (int i = 0; i < Math.max(1, heldUntilRestore); i++) s.step();
            s.driver.holding = false;
            liveFans().forEach(l -> System.out.println("DIVERGE|B   live before restore " + l));
            try {
                s.restore(mark, program.masks());
                System.out.println("DIVERGE|B restore after " + heldUntilRestore + " held rows: OK");
            } catch (Throwable t) {
                Throwable cause = t;
                while (cause.getCause() != null) cause = cause.getCause();
                System.out.println("DIVERGE|B restore failed: " + cause.getMessage());
            }
            liveFans().forEach(l -> System.out.println("DIVERGE|B   live after restore " + l));
        } finally {
            s.close();
            GameServices.configuration().clearSessionOverrides();
        }
    }

    /** Every live object at this moment: class, slot and rewind identity (or none). */
    static List<String> allocationCensus() {
        List<String> out = new ArrayList<>();
        var query = GameServices.level().getObjectManager().objectQuery();
        for (ObjectInstance object : query.activeObjectsOfType(ObjectInstance.class)) {
            int slot = object instanceof com.openggf.level.objects.AbstractObjectInstance a ? a.getSlotIndex() : -9;
            var id = query.identityOf(object);
            out.add(String.format("%3d %s %s", slot, simple(object.getClass().getName()),
                    id.map(r -> r.kind() + "#" + r.dynamicId() + "/spawn" + r.spawnId()).orElse("no-identity")));
        }
        out.sort(null);
        return out;
    }

    /**
     * What the real Course does to native allocation before a rail's first stand: the live-object
     * census at the first stand capture in the packaged Course and in the rail builder's chain,
     * printed side by side with the entries only one side has. Opt-in with
     * {@code -Dtypinghog.restoreprobe=<rail>} and {@code -Dtypinghog.restoreprobe.census=true}.
     */
    @Test @RequiresRom(SonicGame.SONIC_3K) void standAllocationCensus() throws Exception {
        Assumptions.assumeTrue(RAIL != null && Boolean.getBoolean("typinghog.restoreprobe.census"),
                "opt-in: -Dtypinghog.restoreprobe=<rail> -Dtypinghog.restoreprobe.census=true");
        String text = Files.readString(SOURCE.resolve("src/main/resources/rails").resolve(RAIL));
        int zone = intKey(text, "zone"), act = intKey(text, "act");
        String team = strKey(text, "team");
        String sidekick = team.contains(",") ? team.substring(team.indexOf(',') + 1) : "";
        int standRow = stands(text).getFirst();
        List<String> courseSide = null;
        int firstDiffRow = -1;
        java.util.Map<Integer, List<String>> courseTrack = new java.util.HashMap<>();
        CourseRun a = new CourseRun("census", zone, act, sidekick);
        try {
            for (int i = 0; i < 60_000 && courseSide == null; i++) {
                Object view = a.view();
                String phase = (String) get(view, "phase");
                int railRow = (int) get(view, "railRow");
                if (phase.equals("RUN") || phase.equals("ENTRY")) {
                    courseTrack.put(railRow, allocationCensus());
                }
                if (phase.equals("RUN") && railRow == standRow) courseSide = allocationCensus();
                else a.step();
            }
        } finally {
            a.close();
        }
        var builder = new TypingHedgehogRailBuilderProbe();
        var target = TypingHedgehogRailBuilderProbe.targets(SonicGame.SONIC_3K).stream()
                .filter(t -> RAIL.equals(t.file())).findFirst().orElseThrow();
        int[] masks = programMasks(text);
        int[] src = new int[masks.length];
        java.util.Arrays.fill(src, -1);
        var s = builder.new Session(target, new TypingHedgehogRailBuilderProbe.Program(masks, src));
        List<String> builderSide;
        try {
            while (s.driver.row < standRow) {
                List<String> here = allocationCensus();
                List<String> there = courseTrack.get(s.driver.row);
                if (firstDiffRow < 0 && there != null && !there.equals(here)) {
                    firstDiffRow = s.driver.row;
                    printDiff("at row " + firstDiffRow, there, here);
                }
                s.step();
            }
            builderSide = allocationCensus();
        } finally {
            s.close();
            GameServices.configuration().clearSessionOverrides();
        }
        System.out.println("CENSUS|first rail row where the live objects differ: " + firstDiffRow);
        printDiff("at the stand row " + standRow, courseSide, builderSide);
    }

    private static void printDiff(String where, List<String> course, List<String> builder) {
        var onlyCourse = new ArrayList<>(course);
        builder.forEach(onlyCourse::remove);
        var onlyBuilder = new ArrayList<>(builder);
        course.forEach(onlyBuilder::remove);
        System.out.println("CENSUS|" + where + ": course " + course.size() + " live objects, builder " + builder.size());
        onlyCourse.forEach(l -> System.out.println("CENSUS|  only in the Course:  " + l));
        onlyBuilder.forEach(l -> System.out.println("CENSUS|  only in the builder: " + l));
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
