package com.openggf.mods.code;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.control.InputActionMasks;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.GameModule;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServiceBundle;
import com.openggf.game.GameServices;
import com.openggf.game.mode.ControlledFrameRuntime;
import com.openggf.game.mode.CourseControl;
import com.openggf.game.mode.CourseControlProbeAccess;
import com.openggf.game.mode.GameplayFrameController;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.session.SessionManager;
import com.openggf.io.ModAssetRoot;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.SonicGame;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Shared forward-rail (format 2) test machinery: the rails' launch profile, a cold stock act with
 * a pad-program frame controller ({@link World}, {@link Driver}), the packaged mod's own
 * {@code CombatHandoff} ({@link Handoff}), the per-row native state a course compares
 * ({@link Row}), course-snapshot diffs without session-identity noise, the native countdown
 * schedule and floor geometry samples. Used by {@code TypingHedgehogRailsV2Probe}, the forward
 * rail builder and the forward-course tests. Origin: rails lane, rails v2, 2026-10-10.
 */
final class ForwardRails {
    private ForwardRails() { }

    static final Path SOURCE = Path.of("examples/typing-of-the-hedgehog");
    static final int WIDTH = 400;
    static final int PAD_BITS = 0x1F;
    static final int UP = 0x01, DOWN = 0x02, LEFT = 0x04, RIGHT = 0x08, JUMP = AbstractPlayableSprite.INPUT_JUMP;

    /** One act a forward rail covers: its game, ids, launch team and (legacy) rail file. */
    record Pilot(String name, SonicGame game, String code, int zone, int act, String sidekick, String rail) { }

    /** Copies the example project (so a test can add rails) and packages it. */
    static ExampleModHarness buildHarness(Path temp, String tag, Map<String, String> extraRails) throws Exception {
        Path project = temp.resolve("project-" + tag);
        try (Stream<Path> files = Files.walk(SOURCE)) {
            for (Path p : files.toList()) {
                Path target = project.resolve(SOURCE.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(target);
                else Files.copy(p, target);
            }
        }
        Path rails = project.resolve("src/main/resources/rails");
        for (var rail : extraRails.entrySet()) {
            Files.writeString(rails.resolve(rail.getKey()), rail.getValue());
            Path index = rails.resolve("index.txt");
            String listed = Files.exists(index) ? Files.readString(index) : "";
            if (!listed.lines().map(String::strip).toList().contains(rail.getKey())) {
                Files.writeString(index, listed + (listed.endsWith("\n") || listed.isEmpty() ? "" : "\n") + rail.getKey() + "\n");
            }
        }
        return ExampleModHarness.build(project, temp.resolve("work-" + tag));
    }

    // ------------------------------------------------------------------ the mod's real hand-off

    /** The packaged mod's {@code CombatHandoff} and {@code Cast.convertible}, loaded through the harness. */
    static final class Handoff {
        final Method pool;
        final Method convert;
        final Predicate<String> eligible;

        Handoff(ExampleModHarness harness, String game) throws Exception {
            ClassLoader loader = harness.loader();
            Class<?> handoff = loader.loadClass("typinghog.rails.CombatHandoff");
            pool = handoff.getMethod("pool", Predicate.class);
            convert = handoff.getMethod("convert", List.class);
            Class<?> castType = loader.loadClass("typinghog.play.Cast");
            Object cast = castType.getMethod("parse", String.class).invoke(null, "");
            cast = castType.getMethod("withNatives", String.class).invoke(cast,
                    Files.readString(SOURCE.resolve("src/main/resources/natives/" + game + ".txt")));
            Method convertible = castType.getMethod("convertible", String.class);
            Object castObject = cast;
            eligible = name -> {
                try {
                    return (boolean) convertible.invoke(castObject, name);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            };
        }

        public List<?> pool() {
            try {
                return (List<?>) pool.invoke(null, eligible);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e.getCause() != null ? e.getCause() : e);
            }
        }

        public void convert(List<?> bodies) {
            try {
                convert.invoke(null, bodies);
            } catch (java.lang.reflect.InvocationTargetException e) {
                if (e.getCause() instanceof RuntimeException r) throw r;
                throw new IllegalStateException(e.getCause());
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    static Object component(Object record, String name) {
        try {
            Method m = record.getClass().getMethod(name);
            m.setAccessible(true);
            return m.invoke(record);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A pooled body as a rail token: {@code Class@slot/Llayout}. */
    static String token(Object body) {
        return component(body, "className") + "@" + component(body, "slot") + "/L" + component(body, "layoutIndex");
    }

    static String describe(List<?> bodies) {
        return bodies.stream().map(b -> component(b, "className").toString().replace("BadnikInstance", "")
                + "@" + component(b, "slot") + "/L" + component(b, "layoutIndex"))
                .collect(Collectors.joining(" "));
    }

    // ------------------------------------------------------------------ the world

    /** The probe's frame controller: a pad program, Sonic held, the world frozen, a one-shot hook. */
    static final class Driver implements GameplayFrameController, RewindSnapshottable<Integer> {
        IntUnaryOperator program = row -> 0;
        int row;
        int previousMask;
        boolean hold;
        boolean freeze;
        int countdown = -1, burst;
        Runnable heldRun, release;
        Runnable before;
        int nativeSteps;

        @Override public String key() { return "typing-v2-probe"; }
        @Override public Integer capture() { return 0; }
        @Override public void restore(Integer value) { }

        @Override public boolean beforeTick(CourseControl course, LogicalInputSnapshot input) {
            if (before != null) {
                Runnable hook = before;
                before = null;
                hook.run();
            }
            if (hold) holdPlayer();
            else if (countdown >= 0 && row >= countdown && row < countdown + COUNTDOWN_ROWS) heldRun.run();
            else if (countdown >= 0 && row == countdown + COUNTDOWN_ROWS) release.run();
            return !freeze;
        }

        @Override public void afterTick(CourseControl course, boolean advanced) {
            if (advanced) nativeSteps++;
        }

        /** Every mask played so far, by program row (a restore rewinds {@code row}; later rows overwrite). */
        int[] log = new int[1024];

        /** The masks played on rows {@code 0..rows-1}. */
        int[] played(int rows) { return java.util.Arrays.copyOf(log, rows); }

        @Override public LogicalInputSnapshot nativeInput(CourseControl course) {
            int mask = 0;
            if (!hold) {
                mask = program.applyAsInt(row) & PAD_BITS;
                if (row >= log.length) log = java.util.Arrays.copyOf(log, Math.max(row + 1, log.length * 2));
                log[row] = mask;
                row++;
            }
            int action = (mask & JUMP) != 0 ? InputActionMasks.ACTION_A : 0;
            int previousAction = (previousMask & JUMP) != 0 ? InputActionMasks.ACTION_A : 0;
            var p1 = PlayerInputState.of(mask, mask & ~previousMask, action, action & ~previousAction, false, false);
            previousMask = mask;
            return LogicalInputSnapshot.ofPlayers(p1, PlayerInputState.neutral());
        }

        @Override public boolean allowsDebugRewind() { return false; }
    }

    /** The exact production held-run launch, including animation state, on native authoring rows. */
    static void heldLaunch(ExampleModHarness harness, Driver driver, int countdown, int burst) throws Exception {
        Class<?> launch = harness.loader().loadClass("typinghog.rails.Launch");
        Method hold = launch.getMethod("holdRun", int.class), release = launch.getMethod("release", int.class);
        driver.countdown = countdown;
        driver.burst = burst;
        driver.heldRun = () -> invokeLaunch(hold, burst);
        driver.release = () -> invokeLaunch(release, burst);
    }

    private static void invokeLaunch(Method method, int burst) {
        try { method.invoke(null, burst); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }

    static void holdPlayer() {
        if (!(GameServices.camera().getFocusedSprite() instanceof AbstractPlayableSprite player)) return;
        player.setObjectControlled(true);
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        player.setGSpeed((short) 0);
    }

    /** One cold stock act under the rails' launch profile, with the probe's controller. */
    static final class World implements AutoCloseable {
        final Driver driver = new Driver();
        final InputHandler input = new InputHandler();
        final HeadlessTestFixture fixture;
        final CourseControl course;

        World(Pilot pilot, Map<SonicConfiguration, Object> overrides) throws IOException {
            this(pilot, overrides, 0);
        }

        /**
         * {@code vbla} seeds V_int_run_count at the act start: the fixture otherwise inherits the
         * previous session's clock (the engine carries it, as the ROM never clears it), so two
         * worlds in one JVM would start at different phases.
         */
        World(Pilot pilot, Map<SonicConfiguration, Object> overrides, int vbla) throws IOException {
            TypingHedgehogRailBuilderProbe.resetModule();
            var config = GameServices.configuration();
            config.clearSessionOverrides();
            applyProfile(config, pilot, overrides);
            GameServices.graphics().setProjectionWidth(config.getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS));
            displayWidth(config, config.getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS));
            GameModule effective = patch(GameServices.module(), driver);
            SessionManager.clear();
            GameModuleRegistry.setCurrent(effective);
            com.openggf.tests.TestEnvironment.activeGameplayMode();
            fixture = HeadlessTestFixture.builder().withZoneAndAct(pilot.zone(), pilot.act()).build();
            course = CourseControlProbeAccess.open(SessionManager.getCurrentGameplayMode());
            if (course.viewportWidth() != config.getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS)) {
                throw new IllegalStateException("authoring viewport differs from launch profile");
            }
            GameServices.level().getObjectManager().initVblaCounter(vbla);
            for (int i = 0; i < 8 && driver.row == 0 && ControlledFrameRuntime.prepareSetup(fixture.runtime()); i++) { }
        }

        void step() {
            ControlledFrameRuntime.step(fixture.runtime(), input, LogicalInputSnapshot.neutral());
            input.update();
        }

        /** One native step (rows held by {@code freeze} are stepped through until one advances). */
        void advance() {
            int before = driver.nativeSteps;
            for (int i = 0; i < 16 && driver.nativeSteps == before; i++) step();
        }

        CompositeSnapshot snapshot() {
            return SessionManager.getCurrentGameplayMode().getRewindRegistry().captureCourse();
        }

        @Override public void close() {
            GameServices.configuration().clearSessionOverrides();
        }
    }

    static void displayWidth(com.openggf.configuration.SonicConfigurationService config, int width) {
        WidescreenAspect aspect = java.util.Arrays.stream(WidescreenAspect.values())
                .filter(a -> a.pixelWidth() == width).findFirst().orElseThrow();
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, aspect.name());
        config.resolveDisplayAspect();
    }

    static void applyProfile(com.openggf.configuration.SonicConfigurationService config, Pilot pilot,
                             Map<SonicConfiguration, Object> overrides) {
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, WidescreenAspect.WIDE_16_9.name());
        config.resolveDisplayAspect();
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, WIDTH);
        config.setSessionOverride(SonicConfiguration.LOAD_TIME_SIMULATION, "FAST");
        config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, pilot.sidekick());
        // The mod requires skipped act intros (GameModule.requiresSkippedActIntros), as the hub-launched game has.
        config.setSessionOverride(SonicConfiguration.S3K_SKIP_INTROS, true);
        config.setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED, false);
        overrides.forEach(config::setSessionOverride);
    }

    static GameModule patch(GameModule base, Driver driver) {
        var context = new ModContext("typing-v2-probe", base.getGameCode(), ModAssetRoot.forTests("v2"));
        context.registerServiceBundle("probe", () -> GameServiceBundle.builder().frameController("probe", driver).build());
        var boundary = new ModFaultBoundary(Map.of(), new com.openggf.mods.ModRuntimeFindingStore(),
                owners -> new com.openggf.mods.ModStateSaveResult.Saved(), owners -> { });
        return new ModBackedGamePatch(context.freeze(), boundary).apply(base, null);
    }

    static AbstractPlayableSprite sonic() { return (AbstractPlayableSprite) GameServices.camera().getFocusedSprite(); }

    static AbstractPlayableSprite sidekick() {
        for (var sprite : GameServices.sprites().getAllSprites()) {
            if (sprite instanceof AbstractPlayableSprite p && p != sonic()) return p;
        }
        return null;
    }

    /** The native state a forward course compares row by row (cheap; full snapshots are separate). */
    record Row(int p1x, int p1y, int p1xs, int p1ys, int p1gs, boolean p1air, boolean p1roll, boolean p1dash,
               int p2x, int p2y, int camX, int camY, long rng, int frame, int vbla, int rings, String slots,
               boolean ready, boolean controllable, boolean hurt, boolean dead) {
        static Row observe(CourseControl course) {
            var p = sonic();
            var k = sidekick();
            var om = GameServices.level().getObjectManager();
            return new Row(p.getCentreX(), p.getCentreY(), p.getXSpeed(), p.getYSpeed(), p.getGSpeed(), p.getAir(),
                    p.getRolling(), p.getSpindash(), k == null ? -1 : k.getCentreX(), k == null ? -1 : k.getCentreY(),
                    GameServices.camera().getX(), GameServices.camera().getY(), GameServices.rng().getSeed(),
                    GameServices.level().getFrameCounter(), om.getVblaCounter(), p.getRingCount(),
                    new TreeMap<>(om.occupiedDynamicSlotIdsWithReservations()).toString(),
                    course.presentationReady(), !p.getAir() && !p.isObjectControlled() && !p.isControlLocked(),
                    p.isHurt() || p.getHurtAtFrameStart(), p.getDead());
        }

        /** The native fields that differ from {@code o} (presentation readiness excluded). */
        List<String> differences(Row o) { return differences(o, true); }

        List<String> differences(Row o, boolean withClock) {
            List<String> d = new ArrayList<>();
            if (p1x != o.p1x || p1y != o.p1y) d.add("p1 " + p1x + "," + p1y + " vs " + o.p1x + "," + o.p1y);
            if (p1xs != o.p1xs || p1ys != o.p1ys || p1gs != o.p1gs) d.add("p1 speed " + p1xs + "/" + p1ys + "/" + p1gs
                    + " vs " + o.p1xs + "/" + o.p1ys + "/" + o.p1gs);
            if (p1air != o.p1air || p1roll != o.p1roll || p1dash != o.p1dash) d.add("p1 mode");
            if (p2x != o.p2x || p2y != o.p2y) d.add("p2 " + p2x + "," + p2y + " vs " + o.p2x + "," + o.p2y);
            if (camX != o.camX || camY != o.camY) d.add("camera " + camX + "," + camY + " vs " + o.camX + "," + o.camY);
            if (rng != o.rng) d.add("rng " + Long.toHexString(rng) + " vs " + Long.toHexString(o.rng));
            if (frame != o.frame) d.add("frame counter " + frame + " vs " + o.frame);
            if (withClock && vbla != o.vbla) d.add("vbla " + vbla + " vs " + o.vbla);
            if (rings != o.rings) d.add("rings " + rings + " vs " + o.rings);
            if (!slots.equals(o.slots)) d.add("slots " + slots + " vs " + o.slots);
            if (hurt != o.hurt || dead != o.dead) d.add("hurt/dead");
            return d;
        }
    }

    static final int COUNTDOWN_ROWS = 180;

    /** Countdown mask {@code i} rows after its start: DOWN, a DOWN+JUMP pulse every sixth row from 2, RIGHT at 180. */
    static int countdownMask(int i, boolean pulses) {
        if (i >= COUNTDOWN_ROWS) return RIGHT;
        if (pulses && i >= 2 && (i - 2) % 6 == 0) return DOWN | JUMP;
        return DOWN;
    }

    /** Diff of two course snapshots: differing keys, and the first few field differences. */
    static String snapshotDiff(CompositeSnapshot a, CompositeSnapshot b) {
        List<String> keys = new ArrayList<>();
        List<String> lines = new ArrayList<>();
        Set<String> all = new java.util.TreeSet<>(a.entries().keySet());
        all.addAll(b.entries().keySet());
        for (String key : all) {
            Object av = a.get(key), bv = b.get(key);
            if (RewindSnapshotDiff.keyEquals(key, av, bv)) continue;
            List<String> real = RewindSnapshotDiff.diffKey(key, av, bv).stream()
                    .filter(line -> !representationOnly(line)).toList();
            if (real.isEmpty()) continue;
            keys.add(key);
            if (lines.size() < 6) lines.addAll(real.stream().limit(3).toList());
        }
        return keys.isEmpty() ? "identical" : keys.size() + " keys " + keys + " e.g. " + lines;
    }

    /**
     * A diff line that only says two sessions hold different Java instances (an ObjectSpawn
     * reference, or an opaque value printed as Class@hash on both sides): session identity, not
     * gameplay state.
     */
    static boolean representationOnly(String line) {
        if (line.endsWith("spawn ref differs")) return true;
        var m = java.util.regex.Pattern.compile("A=([\\w.$]+)@[0-9a-f]+ B=([\\w.$]+)@[0-9a-f]+$").matcher(line);
        return m.find() && m.group(1).equals(m.group(2));
    }

    record Geometry(int samples, int missing, int range, int lowCeilings, String profile) {
        boolean flat() { return flat(4); }

        boolean flat(int maxRange) { return missing == 0 && range <= maxRange && lowCeilings == 0; }
    }

    /** Floor samples every 8 px from 48 px left to 112 px right of Sonic at his feet. */
    static Geometry geometry() { return geometry(-48, 112); }

    /** Floor samples every 8 px from {@code left} to {@code right} px of Sonic at his feet. */
    static Geometry geometry(int left, int right) {
        var s = sonic();
        int feet = s.getCentreY() + s.getYRadius();
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE, missing = 0, low = 0, samples = 0;
        StringBuilder profile = new StringBuilder();
        for (int dx = left; dx <= right; dx += 8) {
            int x = s.getCentreX() + dx;
            TerrainCheckResult floor = ObjectTerrainUtils.checkFloorDist(x, feet - 8);
            samples++;
            if (!floor.foundSurface() || Math.abs(floor.distance() - 8) > 12) {
                missing++;
                profile.append(" x");
                continue;
            }
            int floorY = feet - 8 + floor.distance();
            min = Math.min(min, floorY);
            max = Math.max(max, floorY);
            profile.append(' ').append(floorY - feet);
            // Upright clearance: no point 16, 32 or 47 px above the floor lies inside solid ground.
            // (A ceiling probe from just above the floor can report the floor's own tile.)
            for (int up : new int[] {16, 32, 47}) {
                TerrainCheckResult inside = ObjectTerrainUtils.checkFloorDist(x, floorY - up);
                if (inside.foundSurface() && inside.distance() < 0) {
                    low++;
                    break;
                }
            }
        }
        return new Geometry(samples, missing, missing == samples ? 999 : max - min, low, profile.toString().strip());
    }

    /**
     * The route's pace on native rows outside fights, for the builder's scoring and the gate:
     * the longest <em>stationary</em> stretch (Sonic moved under {@link #STILL_PX} px over the last
     * {@link #STILL_WINDOW} rows, so a lift ride counts as moving) and the longest <em>slow roll</em>
     * (rolling on the ground under {@link #SLOW_ROLL} subpixels a frame). Excluded rows (countdown,
     * a fight's quiet rows and native spindash charge) end a stretch.
     */
    static final class Pace {
        static final int STILL_WINDOW = 8, STILL_PX = 8, SLOW_ROLL = 0x300;
        static final int STILL_LIMIT = 30, SLOW_ROLL_LIMIT = 45;
        private final int[] xs = new int[STILL_WINDOW], ys = new int[STILL_WINDOW];
        private int seen, stillRun, rollRun;
        int maxStill, maxStillRow = -1, maxRoll, maxRollRow = -1;

        Pace copy() {
            Pace p = new Pace();
            System.arraycopy(xs, 0, p.xs, 0, xs.length);
            System.arraycopy(ys, 0, p.ys, 0, ys.length);
            p.seen = seen;
            p.stillRun = stillRun;
            p.rollRun = rollRun;
            p.maxStill = maxStill;
            p.maxStillRow = maxStillRow;
            p.maxRoll = maxRoll;
            p.maxRollRow = maxRollRow;
            return p;
        }

        /** One native row's state; {@code excluded} rows end any stretch. */
        void row(AbstractPlayableSprite s, int row, boolean excluded) {
            row(s.getCentreX(), s.getCentreY(), s.getRolling() && !s.getAir(), s.getGSpeed(), row, excluded);
        }

        void row(int x, int y, boolean groundedRoll, int speed, int row, boolean excluded) {
            int i = seen++ % STILL_WINDOW;
            int oldX = xs[i], oldY = ys[i];
            xs[i] = x;
            ys[i] = y;
            if (excluded) {
                stillRun = 0;
                rollRun = 0;
                return;
            }
            boolean still = seen > STILL_WINDOW && Math.abs(x - oldX) < STILL_PX
                    && Math.abs(y - oldY) < STILL_PX;
            stillRun = still ? stillRun + 1 : 0;
            if (stillRun > maxStill) {
                maxStill = stillRun;
                maxStillRow = row;
            }
            boolean slowRoll = groundedRoll && Math.abs(speed) < SLOW_ROLL;
            rollRun = slowRoll ? rollRun + 1 : 0;
            if (rollRun > maxRoll) {
                maxRoll = rollRun;
                maxRollRow = row;
            }
        }

        @Override public String toString() {
            return "longest stationary " + maxStill + " rows (to row " + maxStillRow + "), longest slow roll " + maxRoll
                    + " rows (to row " + maxRollRow + ")";
        }
    }

    static int signedAngle(int angle) {
        int a = angle & 0xFF;
        return a > 128 ? a - 256 : a;
    }

}
