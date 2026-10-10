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
import com.openggf.game.mode.CourseControl;
import com.openggf.game.mode.CourseControlProbeAccess;
import com.openggf.game.session.SessionManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Gate for the shipped Typing of the Hedgehog rails, driven through the packaged mod's real
 * {@code Course} from a cold act start (its profile width, FAST load time, the rail's team and profile, stock
 * badniks present), every fight cleared by typing as a player does. Each packaged rail is read by
 * the mod's own {@code Rail.parse}.
 *
 * <p>Every shipped rail is a forward course (format 2) and must qualify: four or five regular fights (none for a boss-only
 * course), the ROM SHA-1 they name, and a run of the real course that, on every native row outside
 * fights, has no hurt, death, level change, rail fault, native badnik kill (a badnik's slot turning
 * into an explosion, the ROM's in-place {@code ExplosionItem}), camera more than 16 px behind its
 * high water or Sonic more than 64 px behind his; the countdown plays its 180 native rows; each
 * fight starts on its row with its manifest's loaded badniks; the endpoint starts the boss on its
 * row with the stock boss flag up and the act's boss rig art registered. The course is run four
 * ways: typed at once, held 1800 rows before typing, retried once mid-fight, and from a hub-like
 * V_int_run_count phase (333); the first three must reach every fight, the first row after it and
 * the endpoint in the same native state (equal native rows, whatever the fights took). A game
 * that ships no rail passes with nothing to check, and says so.
 */
@Tag("slow-suite")
class TestTypingHedgehogRails {
    static final String MOD = "typing-of-the-hedgehog";
    static final Path SOURCE = Path.of("examples/typing-of-the-hedgehog");
    static final int HOLD_ROWS = 1800, RETRY_AFTER = 120, HUB_PHASE = 333;
    /**
     * The route's pace outside fights (rails v2 polish, 2026-10-10): the longest stationary stretch
     * and the longest slow roll a shipped rail may have on its run rows ({@link ForwardRails.Pace};
     * the countdown, a fight's quiet rows and native spindash charge do not count). {@code -Dtypinghog.pace.report=true} reports without asserting.
     */
    static final int STILL_LIMIT = ForwardRails.Pace.STILL_LIMIT, SLOW_ROLL_LIMIT = ForwardRails.Pace.SLOW_ROLL_LIMIT;
    static final boolean PACE_REPORT_ONLY = Boolean.getBoolean("typinghog.pace.report");

    @TempDir static Path temp;
    static ExampleModHarness harness;
    static Path work;
    static int generation;
    private SharedLevel bootstrap;

    /** How a forward run types its fights. */
    enum Mode { QUICK, HOLD, RETRY, PHASE }

    record Spec(String file, String game, int zone, int act, String sidekick, Object rail) { }

    @BeforeAll static void copyProject() throws Exception {
        harness = buildHarness(0);
    }

    /** Packages the example mod afresh (a mod callback fault disables it for the rest of its harness). */
    private static ExampleModHarness buildHarness(int generation) throws Exception {
        Path project = temp.resolve("project" + generation);
        try (Stream<Path> files = Files.walk(SOURCE)) {
            for (Path p : files.toList()) {
                Path target = project.resolve(SOURCE.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(target);
                else Files.copy(p, target);
            }
        }
        work = temp.resolve("work" + generation);
        return ExampleModHarness.build(project, work);
    }

    @AfterAll static void closeHarness() throws Exception {
        if (harness != null) harness.close();
    }

    @AfterEach void dispose() {
        if (bootstrap != null) bootstrap.dispose();
        bootstrap = null;
        GameServices.configuration().clearSessionOverrides();
    }

    /** The packaged rails for {@code game}, each read by the mod's own parser. */
    static List<Spec> shipped(String game) throws Exception {
        Path rails = SOURCE.resolve("src/main/resources/rails");
        List<Spec> specs = new ArrayList<>();
        if (!Files.exists(rails.resolve("index.txt"))) return specs;
        Method parse = harness.loader().loadClass("typinghog.rails.Rail").getMethod("parse", String.class);
        for (String line : Files.readAllLines(rails.resolve("index.txt"))) {
            String name = line.strip();
            if (name.isEmpty() || name.startsWith("#")) continue;
            Object rail = parse.invoke(null, Files.readString(rails.resolve(name)));
            if (!game.equals(get(rail, "game"))) continue;
            String team = (String) get(rail, "team");
            String sidekick = team.contains(",") ? team.substring(team.indexOf(',') + 1) : "";
            specs.add(new Spec(name, game, (int) get(rail, "zone"), (int) get(rail, "act"), sidekick, rail));
        }
        return specs;
    }

    @Test @RequiresRom(SonicGame.SONIC_1) void s1RailsRunCleanThroughTheRealCourse() throws Exception {
        check("s1", SonicGame.SONIC_1);
    }

    /** Inspect the actual Course's release before native physics can alter its authored velocity. */
    @Test @RequiresRom(SonicGame.SONIC_1) void heldS1StartReleasesAtTheHeaderBurstBeforeStockPhysics() throws Exception {
        Spec spec = shipped("s1").stream().filter(s -> s.file().equals("s1-ghz3.rail")).findFirst().orElseThrow();
        Object plan = get(spec.rail(), "plan");
        Map<?, ?> profile = (Map<?, ?>) get(plan, "profile");
        var fixture = launch(spec, SonicGame.SONIC_1, false, Integer.parseInt((String) profile.get("width")));
        CourseControl control = CourseControlProbeAccess.open(SessionManager.getCurrentGameplayMode());
        var input = new InputHandler();
        int release = (int) get(plan, "releaseRow");
        for (int i = 0; i < release + 600; i++) {
            Object current = view();
            if (get(current, "phase").equals("COUNTDOWN") && (int) get(current, "railRow") == release) {
                var course = (com.openggf.game.mode.GameplayFrameController) courseObject();
                assertTrue(course.beforeTick(control, LogicalInputSnapshot.neutral()));
                var player = ForwardRails.sonic();
                assertFalse(player.isObjectControlled());
                assertEquals((int) get(plan, "burst"), player.getGSpeed());
                assertEquals((int) get(plan, "burst"), player.getXSpeed());
                assertEquals(0x600, (int) get(plan, "burst"));
                return;
            }
            ControlledFrameRuntime.step(fixture.runtime(), input, LogicalInputSnapshot.neutral());
            input.update();
        }
        fail("the held-run course never reached START");
    }

    @Test @RequiresRom(SonicGame.SONIC_2) void s2RailsRunCleanThroughTheRealCourse() throws Exception {
        check("s2", SonicGame.SONIC_2);
    }

    @Test @RequiresRom(SonicGame.SONIC_3K) void s3kRailsRunCleanThroughTheRealCourse() throws Exception {
        check("s3k", SonicGame.SONIC_3K);
    }

    private void check(String game, SonicGame sonicGame) throws Exception {
        List<Spec> specs = shipped(game);
        if (specs.isEmpty()) {
            System.out.println("[typing-hedgehog] " + game + " rails: none shipped, nothing to check");
            return;
        }
        List<String> faults = new ArrayList<>();
        for (Spec spec : specs) {
            String fault;
            try {
                fault = forward(spec, sonicGame);
            } catch (Throwable t) {
                Throwable cause = t;
                while (cause.getCause() != null) cause = cause.getCause();
                StackTraceElement[] stack = cause.getStackTrace();
                fault = "aborted: " + cause + " at " + java.util.Arrays.stream(stack)
                        .limit(Math.min(stack.length, 12)).map(String::valueOf).toList();
                dispose();
                harness.close();
                harness = buildHarness(++generation);
            }
            if (fault != null) faults.add(spec.file() + ": " + fault);
            dispose();
        }
        System.out.println("[typing-hedgehog] " + game + " rails: " + specs.size() + " forward courses "
                + specs.stream().map(Spec::file).toList());
        assertTrue(faults.isEmpty(), "rail faults:\n" + String.join("\n", faults));
    }

    private Path storage() { return work.resolve("storage").resolve("mods").resolve(MOD); }

    /** One cold course of {@code spec} at {@code width} px: returns its fixture after writing the launch intent. */
    private HeadlessTestFixture launch(Spec spec, SonicGame sonicGame, boolean skipIntros, int width) throws Exception {
        bootstrap = SharedLevel.load(sonicGame, spec.zone(), spec.act());
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, spec.sidekick());
        String aspect = java.util.Arrays.stream(com.openggf.configuration.WidescreenAspect.values())
                .filter(a -> a.pixelWidth() == width).map(Enum::name).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no display preset is " + width + " px wide"));
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, aspect);
        config.resolveDisplayAspect();
        config.setSessionOverride(SonicConfiguration.LOAD_TIME_SIMULATION, "FAST");
        config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);
        config.setSessionOverride(SonicConfiguration.S3K_SKIP_INTROS, skipIntros);
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
        return fixture;
    }

    // ------------------------------------------------------------------ forward rails

    /** The native state a forward course compares at a boundary. */
    record Boundary(String what, ForwardRails.Row row) { }

    private String forward(Spec spec, SonicGame sonicGame) throws Exception {
        Object plan = get(spec.rail(), "plan");
        if (!(boolean) plan.getClass().getMethod("qualifies").invoke(plan)) {
            return "a " + get(plan, "kind") + " course with " + ((List<?>) get(plan, "stands")).size()
                    + " fights does not qualify";
        }
        Map<Mode, Map<String, ForwardRails.Row>> runs = new LinkedHashMap<>();
        for (Mode mode : Mode.values()) {
            Map<String, ForwardRails.Row> boundaries = new LinkedHashMap<>();
            String fault = forwardRun(spec, sonicGame, plan, mode, boundaries);
            dispose();
            if (fault != null) return mode + ": " + fault;
            runs.put(mode, boundaries);
            System.out.println("[typing-hedgehog] " + spec.file() + " " + mode + ": " + boundaries.size() + " boundaries " + boundaries.keySet());
        }
        var quick = runs.get(Mode.QUICK);
        for (Mode mode : List.of(Mode.HOLD, Mode.RETRY)) {
            for (var b : quick.entrySet()) {
                var other = runs.get(mode).get(b.getKey());
                if (other == null) return mode + " never reached " + b.getKey();
                List<String> d = b.getValue().differences(other);
                if (!d.isEmpty()) return mode + " differs from a quick run at " + b.getKey() + ": " + d;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String forwardRun(Spec spec, SonicGame sonicGame, Object plan, Mode mode, Map<String, ForwardRails.Row> boundaries)
            throws Exception {
        Map<String, String> profile = (Map<String, String>) get(plan, "profile");
        if (profile.get("width") == null) return "the rail names no profile width";
        int width = Integer.parseInt(profile.get("width"));
        String aspect = harness.plan().requiredDisplayAspect();
        if (aspect == null || com.openggf.configuration.WidescreenAspect.valueOf(aspect).pixelWidth() != width) {
            return "rail profile width " + width + " differs from mod display requirement " + aspect;
        }
        var fixture = launch(spec, sonicGame, "skip".equals(profile.get("intros")), width);
        GameServices.level().getObjectManager().initVblaCounter(mode == Mode.PHASE ? HUB_PHASE : 0);
        CourseControl course = CourseControlProbeAccess.open(SessionManager.getCurrentGameplayMode());
        if (course.viewportWidth() != width) {
            return "the course runs " + course.viewportWidth() + " px wide; the rail was built at " + width;
        }
        java.util.Set<Integer> quietRows = new java.util.HashSet<>();
        for (Object s : (List<Object>) get(plan, "stands")) {
            int row = (int) get(s, "row");
            for (int c = 0; c < (int) get(s, "cleanup"); c++) quietRows.add(row + c);
        }
        ForwardRails.Pace pace = new ForwardRails.Pace();
        String sha1 = (String) get(plan, "romSha1");
        if (!sha1.equalsIgnoreCase(course.romSha1())) return "made for ROM " + sha1 + ", running " + course.romSha1();
        int countdownRow = (int) get(plan, "countdownRow");
        List<Object> stands = (List<Object>) get(plan, "stands");
        Object endpoint = get(plan, "endpoint");
        int endpointRow = (int) get(endpoint, "row");
        Map<Integer, Object> standsByRow = new HashMap<>();
        for (Object s : stands) standsByRow.put((int) get(s, "row"), s);
        var input = new InputHandler();
        boolean heldRun = get(plan, "launch").equals("held-run");
        int countdownX = -1;
        java.util.Set<Integer> runFrames = new java.util.HashSet<>();
        int countdownRows = 0, cmax = Integer.MIN_VALUE, pmax = Integer.MIN_VALUE, heldRows = 0, sinceLoop = -1;
        boolean loopsExempt = "forward-v2".equals(get(plan, "policy"));
        boolean retried = false;
        String lastPhase = "";
        int lastRailRow = -1;
        Map<Integer, String> badnikSlots = new HashMap<>();
        int limit = endpointRow + stands.size() * 12_000 + 20_000;
        for (int i = 0; i < limit; i++) {
            Object view = view();
            String phase = (String) get(view, "phase");
            int railRow = (int) get(view, "railRow");
            Object fault = get(view, "fault");
            if (fault != null) return "rail fault on row " + railRow + ": " + fault;
            if (phase.equals("COUNTDOWN") && railRow != lastRailRow) countdownRows++;
            if (phase.equals("RUN") && standsByRow.containsKey(railRow) && !boundaries.containsKey("stand " + railRow)) {
                boundaries.put("stand " + railRow, ForwardRails.Row.observe(course));
            }
            if (phase.equals("RUN") && standsByRow.containsKey(railRow - 1) && !boundaries.containsKey("after " + (railRow - 1))) {
                boundaries.put("after " + (railRow - 1), ForwardRails.Row.observe(course));
            }
            if (phase.equals("STAND") && !lastPhase.equals("STAND") && !lastPhase.equals("DYING")) {
                Object stand = standsByRow.get(railRow);
                if (stand == null) return "a fight began on row " + railRow + ", which is no stand row";
                int expected = ((List<?>) get(stand, "pool")).size();
                int loaded = (int) get(view, "loaded");
                if (loaded != expected) return "stand " + railRow + " took " + loaded + " loaded badniks, its manifest " + expected;
                heldRows = 0;
            }
            if (phase.equals("BOSS") && !lastPhase.equals("BOSS")) {
                if (railRow != endpointRow) return "the boss began on row " + railRow + ", the endpoint is " + endpointRow;
                // S1 _anim/Sonic.asm: SonAni_Run uses fr_Run11..14 ($1E..$21).
                if (heldRun && !runFrames.equals(java.util.Set.of(0x1E, 0x1F, 0x20, 0x21))) {
                    return "held countdown did not play the fast run frames: " + runFrames;
                }
                if (countdownRows < 180) return "the countdown played " + countdownRows + " native rows";
                if (get(endpoint, "kind").equals("boss-arena") && !GameServices.gameState().isBossFightActive()) {
                    return "no stock boss arena at the endpoint";
                }
                String art = bossArtProblem(spec);
                if (art != null) return art;
                boundaries.put("endpoint", ForwardRails.Row.observe(course));
                if (mode == Mode.QUICK) System.out.println("[typing-hedgehog] " + spec.file() + " pace: " + pace);
                if (!PACE_REPORT_ONLY && pace.maxStill > STILL_LIMIT) {
                    return "Sonic stands still on the route for " + pace.maxStill + " rows (to row " + pace.maxStillRow
                            + "; limit " + STILL_LIMIT + ")";
                }
                if (!PACE_REPORT_ONLY && pace.maxRoll > SLOW_ROLL_LIMIT) {
                    return "Sonic rolls slowly for " + pace.maxRoll + " rows (to row " + pace.maxRollRow + "; limit "
                            + SLOW_ROLL_LIMIT + ")";
                }
                return null;
            }
            if (phase.equals("STAND")) {
                heldRows++;
                boolean type = switch (mode) {
                    case HOLD -> heldRows > HOLD_ROWS;
                    default -> true;
                };
                if (mode == Mode.RETRY && !retried && heldRows == RETRY_AFTER) {
                    Object c = courseObject();
                    Method retry = c.getClass().getDeclaredMethod("retryStand");
                    retry.setAccessible(true);
                    retry.invoke(c);
                    retried = true;
                }
                if (type) typeNext(view, input);
            }
            lastPhase = phase;
            lastRailRow = railRow;
            boolean nativeRow = phase.equals("RUN") || phase.equals("COUNTDOWN") || phase.equals("ENTRY");
            if (nativeRow) badnikSlots = badnikSlots();
            ControlledFrameRuntime.step(fixture.runtime(), input, LogicalInputSnapshot.neutral());
            input.update();
            // The endpoint hands the camera to the typing arena. Forward-run pace and
            // high-water checks apply only while this row remains a rail RUN.
            boolean stillRunning = phase.equals("RUN") && get(view(), "phase").equals("RUN");
            if (!nativeRow) continue;
            var player = (AbstractPlayableSprite) GameServices.camera().getFocusedSprite();
            if (heldRun && phase.equals("COUNTDOWN") && railRow < countdownRow + 180) {
                if (countdownX < 0) countdownX = player.getCentreX();
                if (!player.isObjectControlled() || player.getCentreX() != countdownX || player.getXSpeed() != 0
                        || player.getGSpeed() != (int) get(plan, "burst")) return "held-run countdown moved or lost its run speed";
                runFrames.add(player.getMappingFrame());
            }
            if (heldRun && railRow == countdownRow + 180 && phase.equals("COUNTDOWN")) {
                // This observes AFTER stock slope/velocity physics, which may change the launch speed.
                if (player.isObjectControlled() || player.getGSpeed() <= 0 || player.getCentreX() <= countdownX) {
                    return "START did not release running forward";
                }
            }
            if (stillRunning) {
                int played = railRow;
                boolean excluded = player.getSpindash() || quietRows.contains(played);
                pace.row(player, played, excluded);
            }
            if (player.getDead()) return "native death in " + phase + " at rail row " + railRow;
            if (player.isHurt()) return "native hurt in " + phase + " at rail row " + railRow;
            if (GameServices.level().getCurrentZone() != spec.zone() || GameServices.level().getCurrentAct() != spec.act()) {
                return "level changed at rail row " + railRow;
            }
            String kill = nativeKill(badnikSlots);
            if (kill != null) return "native kill of " + kill + " at rail row " + railRow;
            if (railRow > countdownRow + 180 && stillRunning) {
                int camX = GameServices.camera().getX(), x = player.getCentreX();
                // forward-v2: loop traversal (grounded at wall or ceiling angles) is exempt, and the
                // high water must come back within 60 rows of leaving the loop.
                int a = player.getAngle() & 0xFF;
                boolean inLoop = loopsExempt && !player.getAir() && Math.abs(a > 128 ? a - 256 : a) >= 0x20;
                if (inLoop) {
                    sinceLoop = 0;
                } else if (sinceLoop >= 0) {
                    if (x >= pmax && camX >= cmax - 16) sinceLoop = -1;
                    else if (++sinceLoop > 60) return "Sonic did not regain his high water after a loop at rail row " + railRow;
                } else {
                    if (cmax != Integer.MIN_VALUE && camX < cmax - 16) return "camera backtracked at rail row " + railRow;
                    if (pmax != Integer.MIN_VALUE && x < pmax - 64) return "Sonic backtracked at rail row " + railRow;
                }
                cmax = Math.max(cmax, camX);
                pmax = Math.max(pmax, x);
            }
        }
        return "the endpoint was not reached within " + limit + " rows; view " + view();
    }

    /** Live badnik bodies by slot (class name) before a native row. */
    private static Map<Integer, String> badnikSlots() {
        Map<Integer, String> out = new HashMap<>();
        for (ObjectInstance o : GameServices.level().getObjectManager().getActiveObjects()) {
            if (o instanceof AbstractObjectInstance a && !a.isDestroyed() && a.getSlotIndex() >= 0
                    && o.getClass().getSimpleName().endsWith("BadnikInstance")) {
                out.put(a.getSlotIndex(), o.getClass().getSimpleName());
            }
        }
        return out;
    }

    /**
     * A native kill: a badnik's slot now holds an explosion. The ROM turns a destroyed badnik into
     * ExplosionItem in place, and the engine's destroyBadnik hands the explosion that slot.
     */
    private static String nativeKill(Map<Integer, String> before) {
        if (before.isEmpty()) return null;
        for (ObjectInstance o : GameServices.level().getObjectManager().getActiveObjects()) {
            if (!(o instanceof AbstractObjectInstance a) || !o.getClass().getSimpleName().contains("Explosion")) continue;
            String badnik = before.get(a.getSlotIndex());
            if (badnik != null) return badnik + " in slot " + a.getSlotIndex();
        }
        return null;
    }

    /** At the endpoint: the act's boss rig art is registered (some zones load it behind a camera gate). */
    private static String bossArtProblem(Spec spec) throws Exception {
        var art = GameServices.level().getObjectRenderManager().getArtProvider();
        for (var use : TypingHedgehogArtTables.uses(spec.game(), spec.zone(), spec.act())) {
            if (!use.table().startsWith("boss")) continue;
            var sheet = art.getSheet(use.artKey());
            if (art.getRenderer(use.artKey()) == null || sheet == null) {
                return "at the endpoint the boss rig's " + use.artKey() + " is not registered";
            }
            if (use.frame() >= sheet.getFrameCount()) return "at the endpoint the boss rig's " + use.artKey()
                    + " has no frame " + use.frame();
        }
        return null;
    }

    // ------------------------------------------------------------------ typing and views

    /** Types the locked target's next letter, else the first typeable target's, as a player does. */
    private static void typeNext(Object view, InputHandler input) throws Exception {
        @SuppressWarnings("unchecked")
        List<Object> targets = (List<Object>) get(view, "targets");
        Object target = null;
        for (Object t : targets) if ((boolean) get(t, "locked")) target = t;
        if (target == null) for (Object t : targets) if ((boolean) get(t, "typeable")) { target = t; break; }
        if (target == null) return;
        String typed = (String) get(target, "typed");
        int progress = (int) get(target, "progress");
        if (progress < typed.length()) MenuInput.handleCharEvent(input, typed.codePointAt(progress));
    }

    private static Object courseObject() throws Exception {
        Class<?> type = harness.loader().loadClass("typinghog.play.Course");
        Object course = GameModuleRegistry.getCurrent().getGameService(type);
        assertNotNull(course, "the packaged mod registers its Course as a game service");
        return course;
    }

    private static Object view() throws Exception {
        Object course = courseObject();
        return course.getClass().getMethod("view").invoke(course);
    }

    private static Object get(Object record, String component) throws Exception {
        Method method = record.getClass().getMethod(component);
        method.setAccessible(true);
        return method.invoke(record);
    }
}
