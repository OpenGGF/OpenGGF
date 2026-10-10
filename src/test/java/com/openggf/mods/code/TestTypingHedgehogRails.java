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
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Gate for the shipped Typing of the Hedgehog rails, driven through the packaged mod's real
 * {@code Course} from a cold act start (320 px, FAST load time, the rail's team and profile, stock
 * badniks present), every fight cleared by typing as a player does. Each packaged rail is read by
 * the mod's own {@code Rail.parse}.
 *
 * <p><b>Forward rails</b> (format 2) must qualify: four or five regular fights (none for a boss-only
 * course), the ROM SHA-1 they name, and a run of the real course that, on every native row outside
 * fights, has no hurt, death, level change, rail fault, native badnik kill (a badnik's slot turning
 * into an explosion, the ROM's in-place {@code ExplosionItem}), camera more than 16 px behind its
 * high water or Sonic more than 64 px behind his; the countdown plays its 180 native rows; each
 * fight starts on its row with its manifest's loaded badniks; the endpoint starts the boss on its
 * row with the stock boss flag up and the act's boss rig art registered. The course is run four
 * ways: typed at once, held 1800 rows before typing, retried once mid-fight, and from a hub-like
 * V_int_run_count phase (333); the first three must reach every fight, the first row after it and
 * the endpoint in the same native state (equal native rows, whatever the fights took).
 *
 * <p><b>Legacy rails</b> (format 1) keep the older check: the boss stand on its row, no native hurt
 * or death on any row. They are not forward-qualified.
 */
@Tag("slow-suite")
class TestTypingHedgehogRails {
    static final String MOD = "typing-of-the-hedgehog";
    static final Path SOURCE = Path.of("examples/typing-of-the-hedgehog");
    static final int HOLD_ROWS = 1800, RETRY_AFTER = 120, HUB_PHASE = 333;

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
        int forward = 0, legacy = 0;
        for (Spec spec : specs) {
            boolean isForward = (boolean) spec.rail().getClass().getMethod("forward").invoke(spec.rail());
            if (isForward) forward++;
            else legacy++;
            String fault;
            try {
                fault = isForward ? forward(spec, sonicGame) : legacy(spec, sonicGame);
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
        System.out.println("[typing-hedgehog] " + game + " rails: " + forward + " forward, " + legacy + " legacy");
        assertTrue(faults.isEmpty(), "rail faults:\n" + String.join("\n", faults));
    }

    private Path storage() { return work.resolve("storage").resolve("mods").resolve(MOD); }

    /** One cold course of {@code spec}: returns its fixture after writing the launch intent. */
    private HeadlessTestFixture launch(Spec spec, SonicGame sonicGame, boolean skipIntros) throws Exception {
        bootstrap = SharedLevel.load(sonicGame, spec.zone(), spec.act());
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, spec.sidekick());
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, "NATIVE_4_3");
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
        var fixture = launch(spec, sonicGame, "skip".equals(profile.get("intros")));
        GameServices.level().getObjectManager().initVblaCounter(mode == Mode.PHASE ? HUB_PHASE : 0);
        CourseControl course = CourseControlProbeAccess.open(SessionManager.getCurrentGameplayMode());
        String sha1 = (String) get(plan, "romSha1");
        if (!sha1.equalsIgnoreCase(course.romSha1())) return "made for ROM " + sha1 + ", running " + course.romSha1();
        int countdownRow = (int) get(plan, "countdownRow");
        List<Object> stands = (List<Object>) get(plan, "stands");
        Object endpoint = get(plan, "endpoint");
        int endpointRow = (int) get(endpoint, "row");
        Map<Integer, Object> standsByRow = new HashMap<>();
        for (Object s : stands) standsByRow.put((int) get(s, "row"), s);
        var input = new InputHandler();
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
                if (countdownRows < 180) return "the countdown played " + countdownRows + " native rows";
                if (get(endpoint, "kind").equals("boss-arena") && !GameServices.gameState().isBossFightActive()) {
                    return "no stock boss arena at the endpoint";
                }
                String art = bossArtProblem(spec);
                if (art != null) return art;
                boundaries.put("endpoint", ForwardRails.Row.observe(course));
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
            if (!nativeRow) continue;
            var player = (AbstractPlayableSprite) GameServices.camera().getFocusedSprite();
            if (player.getDead()) return "native death in " + phase + " at rail row " + railRow;
            if (player.isHurt()) return "native hurt in " + phase + " at rail row " + railRow;
            if (GameServices.level().getCurrentZone() != spec.zone() || GameServices.level().getCurrentAct() != spec.act()) {
                return "level changed at rail row " + railRow;
            }
            String kill = nativeKill(badnikSlots);
            if (kill != null) return "native kill of " + kill + " at rail row " + railRow;
            if (railRow > countdownRow + 180 && phase.equals("RUN")) {
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

    // ------------------------------------------------------------------ legacy rails

    /** Runs a format-1 rail to its boss stand; returns a fault description or null. */
    private String legacy(Spec spec, SonicGame sonicGame) throws Exception {
        var fixture = launch(spec, sonicGame, false);
        int boss = (int) get(spec.rail(), "bossRow");
        int stands = ((List<?>) get(spec.rail(), "stands")).size();
        int validated = (int) get(spec.rail(), "validated");
        var input = new InputHandler();
        int limit = validated + stands * 6000 + 20000;
        for (int i = 0; i < limit; i++) {
            Object view = view();
            String phase = (String) get(view, "phase");
            int railRow = (int) get(view, "railRow");
            if (phase.equals("BOSS")) {
                if (railRow != boss) return "boss stand began on row " + railRow + ", rail says " + boss;
                int passed = (int) get(view, "nextStand");
                if (passed != stands) return "passed " + passed + " stands, rail lists " + stands;
                return null;
            }
            // Typing-layer misses retry a stand (noFail); only native faults fail the rail.
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
