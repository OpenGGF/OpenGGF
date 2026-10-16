package com.openggf.tools;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.ClamerObjectInstance;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.TestTempFiles;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.modsdk.GgfModCli;
import org.junit.jupiter.api.Test;

import javax.tools.ToolProvider;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Two fixed ordinary alternatives against the registered CNZ Clamer pair (records 165/180),
 * via the strictly packaged CarnivalNightCampaign probe mod. Fixed predicates, no timing search:
 * A stops and jumps the committed shot; B jumps over the open body during the tell. Both then
 * stop for record 165's commit, jump its shot and jump its body. Test-only; no state writes
 * beyond the declared entry (x=$17F8,y=$42C), 0 declared rings, 320 wide, offscreen.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestSecondStrikeEastCnzClamerCommittedReplay {
    private static final Path OUT = Path.of(System.getProperty("c1east.cnzreplay.out", "target/c1-east-cnz-committed-replay"));
    private static final int ZONE_CNZ = 3, ACT_INDEX = 1;
    private static final int CADENCE_RECORD = 165, STAGGERED_RECORD = 180;
    private static final Path EXAMPLE = Path.of("examples/second-strike");

    // ------------------------------------------------------------------ probe mod (unchanged P14 path)

    private static Path buildProbeJar(Path work) throws Exception {
        Path project = Files.createDirectories(work.resolve("project"));
        Path src = Files.createDirectories(project.resolve("src/main/java"));
        copyTree(EXAMPLE.resolve("src/main/java"), src);
        Path entry = Files.createDirectories(src.resolve("secondstrike/east"));
        Files.writeString(entry.resolve("EastCnzProbeMod.java"), """
                package secondstrike.east;

                import com.openggf.mods.code.GgfMod;
                import com.openggf.mods.code.ModContext;

                /** Test-local probe: registers only the CNZ campaign recipe. */
                public final class EastCnzProbeMod implements GgfMod {
                    @Override public void register(ModContext context) {
                        secondstrike.campaign.CarnivalNightCampaign.register(context);
                    }
                }
                """);
        Path classes = Files.createDirectories(work.resolve("classes"));
        List<String> args = new ArrayList<>(List.of("--release", "25", "-cp", System.getProperty("java.class.path"),
                "-d", classes.toString()));
        try (Stream<Path> files = Files.walk(src)) {
            files.filter(p -> p.toString().endsWith(".java")).sorted().forEach(p -> args.add(p.toString()));
        }
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(String[]::new)),
                "probe mod source compiles against the engine classpath");
        Path meta = Files.createDirectories(classes.resolve("META-INF"));
        Files.writeString(meta.resolve("openggf-mod.yaml"), """
                formatVersion: 1
                id: second-strike-east-probe
                name: Second Strike East probe
                version: 0.0.1
                authors:
                  - OpenGGF contributors
                description: "Test-local probe registering only the Carnival Night campaign recipe."
                engineApiRange: ">=0.7.0 <0.8.0"
                type: patch
                baseGame: s3k
                entrypoint: secondstrike.east.EastCnzProbeMod
                dependencies: []
                audioOverrides: {}
                artOverrides: {}
                """);
        Path jar = work.resolve("second-strike-east-probe.jar");
        ByteArrayOutputStream log = new ByteArrayOutputStream();
        int status = GgfModCli.run(new String[]{"package", "--input", classes.toString(), "--out", jar.toString(),
                "--warnings", "error"}, new PrintStream(log, true, StandardCharsets.UTF_8));
        Files.writeString(OUT.resolve("package.log"), log.toString(StandardCharsets.UTF_8));
        assertEquals(0, status, "strict ggfmod package of the probe mod; see package.log");
        return jar;
    }

    private static void copyTree(Path from, Path to) throws Exception {
        try (Stream<Path> files = Files.walk(from)) {
            for (Path p : (Iterable<Path>) files::iterator) {
                Path target = to.resolve(from.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(target);
                else Files.copy(p, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static Bk2FrameInput token(int frame, String script) {
        var f = InputScriptCompiler.compile("1 " + script).getFirst();
        return new Bk2FrameInput(frame, f.p1InputMask(), f.p1ActionMask(), f.p1Start(),
                f.p2InputMask(), f.p2ActionMask(), false, "cnz-alt");
    }

    // ------------------------------------------------------------------ state reads

    private static Object field(Object o, String name) throws Exception {
        for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
            try { Field f = c.getDeclaredField(name); f.setAccessible(true); return f.get(o); }
            catch (NoSuchFieldException next) { }
        }
        throw new NoSuchFieldException(name);
    }

    private static List<ClamerObjectInstance> clamers() {
        List<ClamerObjectInstance> l = new ArrayList<>(
                GameServices.level().getObjectManager().activeObjectsOfType(ClamerObjectInstance.class));
        l.sort(Comparator.comparingInt(c -> c.getSpawn().layoutIndex()));
        return l;
    }

    private static ClamerObjectInstance clamer(int layoutIndex) {
        for (ClamerObjectInstance c : clamers()) if (c.getSpawn().layoutIndex() == layoutIndex) return c;
        return null;
    }

    private static List<AbstractObjectInstance> projectiles() {
        List<AbstractObjectInstance> l = new ArrayList<>();
        for (var o : GameServices.level().getObjectManager().getActiveObjects()) {
            if (o instanceof AbstractObjectInstance a && !a.isDestroyed()
                    && o.getClass().getSimpleName().equals("ClamerAutoCloseProjectile")) l.add(a);
        }
        l.sort(Comparator.comparingInt(AbstractObjectInstance::getX));
        return l;
    }

    private static String clamerTuple(ClamerObjectInstance c) throws Exception {
        if (c == null) return "absent";
        return String.format(Locale.ROOT, "rt%s f%s t%s c%s%s", field(c, "routine"), field(c, "mappingFrame"),
                field(c, "autoCloseAnimTimer"), field(c, "springCprop"), c.isDestroyed() ? " DESTROYED" : "");
    }

    private static String recipeOf(ClamerObjectInstance c) throws Exception {
        return c == null ? "absent" : String.valueOf(field(c, "recipe"));
    }

    // ------------------------------------------------------------------ floor probe

    /** First native floor at or below the scan start (the sensor only sees about one tile per probe). */
    private static int floorY(int x, int fromY) {
        for (int y = fromY; y < fromY + 0x80; y += 8) {
            var r = ObjectTerrainUtils.checkFloorDist(x, y, 16);
            if (r.foundSurface() && r.distance() >= -16 && r.distance() < 16) {
                return y + 16 + r.distance();
            }
        }
        return Integer.MIN_VALUE;
    }


    // ------------------------------------------------------------------ fixed policy

    /** Fixed alternative, a pure function of observed native state plus a small stage counter. */
    private static final class Policy implements Cloneable {
        final boolean waitForShot180;      // A: stop and jump the shot; B: jump the open body during the tell
        int stage;                         // 0 approach180, 1 wait180Shot, 2 pass180Body, 3 approach165, 4 wait165Shot, 5 pass165Body, 6 done
        int jump;                          // 0 none, 1 ascending, 2 descending
        boolean horizontalJump;
        Policy(boolean waitForShot180) { this.waitForShot180 = waitForShot180; }
        Policy copy() { try { return (Policy) clone(); } catch (CloneNotSupportedException e) { throw new AssertionError(e); } }

        String next(int px, int ySpeed, boolean grounded, int committed180, int committed165,
                    int x180, int x165, List<Integer> shotXs, int stageHint) throws Exception {
            // An airborne jump continues deterministically until grounded.
            if (jump != 0) {
                if (grounded && jump == 2) { jump = 0; }
                else {
                    if (jump == 1 && ySpeed >= 0) jump = 2;
                    String hold = jump == 1 ? "A" : "-";
                    if (horizontalJump) hold = jump == 1 ? "L+A" : "L";
                    return hold;
                }
            }
            switch (stage) {
                case 0 -> { // approach record 180
                    if (committed180 == 6) stage = waitForShot180 ? 1 : 2;
                    else return "L";
                }
                default -> { }
            }
            if (stage == 1) { // A: stand still, jump the shot when it is within $20 on our side
                boolean near = shotXs.stream().anyMatch(sx -> px - sx >= 0 && px - sx <= 0x20 && sx > x180);
                if (!near) return "-";
                if (!grounded) return "-";
                jump = 1; horizontalJump = false; stage = 2;
                return "A";
            }
            if (stage == 2) { // pass record 180's body: jump over it from dx <= $38 holding LEFT
                int dx = px - x180;
                if (dx > 0x38) return "L";
                if (!grounded) return "L";
                jump = 1; horizontalJump = true; stage = 3;
                return "L+A";
            }
            if (stage == 3) { // land past 180, then approach record 165
                if (px >= x180) return "L";
                stage = 4;
            }
            if (stage == 4) { // 165: stop at commit, wait for its shot, jump it
                if (committed165 != 6) return "L";
                boolean near = shotXs.stream().anyMatch(sx -> px - sx >= 0 && px - sx <= 0x20 && sx > x165 - 0x08
                        && sx < x180);
                if (!near) return "-";
                if (!grounded) return "-";
                jump = 1; horizontalJump = false; stage = 5;
                return "A";
            }
            if (stage == 5) { // pass 165's body, then done
                int dx = px - x165;
                if (dx > 0x38) return "L";
                if (!grounded) return "L";
                jump = 1; horizontalJump = true; stage = 6;
                return "L+A";
            }
            return "L";
        }
    }

    // ------------------------------------------------------------------ committed replay witness

    /** Full observable tuple of everything the request names, in a stable order. */
    private static String tuple(GameplayCaptureSession session) throws Exception {
        var p = session.player();
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "P[x=%d y=%d xs=%d ys=%d gs=%d air=%b roll=%b rings=%d hurt=%b dead=%b anim=%d map=%d ctrl=%b]",
                p.getCentreX(), p.getCentreY(), p.getXSpeed(), p.getYSpeed(), p.getGSpeed(), p.getAir(), p.getRolling(),
                p.getRingCount(), p.isHurt(), p.getDead(), p.getAnimationId(), p.getMappingFrame(), p.isObjectControlled()));
        for (int rec : new int[]{CADENCE_RECORD, STAGGERED_RECORD}) {
            ClamerObjectInstance c = clamer(rec);
            sb.append(" C").append(rec).append('[');
            if (c == null) sb.append("absent");
            else {
                sb.append("slot=").append(c.getSlotIndex()).append(" recipe=").append(field(c, "recipe"))
                        .append(" routine=").append(field(c, "routine")).append(" frame=").append(field(c, "mappingFrame"))
                        .append(" timer=").append(field(c, "autoCloseAnimTimer")).append(" idx=").append(field(c, "autoCloseAnimIndex"))
                        .append(" close=").append(field(c, "closeTimer")).append(" cprop=").append(field(c, "springCprop"))
                        .append(" fired=").append(field(c, "springFiredFlag")).append(" springRoutine=").append(field(c, "springRoutine"))
                        .append(" destroyed=").append(c.isDestroyed());
                Object child = field(c, "springChildSlot");
                if (child instanceof AbstractObjectInstance a) {
                    sb.append(" spring[slot=").append(a.getSlotIndex()).append(" x=").append(a.getX())
                            .append(" y=").append(a.getY()).append(" destroyed=").append(a.isDestroyed()).append(']');
                } else {
                    sb.append(" spring[none]");
                }
            }
            sb.append(']');
        }
        sb.append(" SHOTS[");
        for (var s : projectiles()) {
            sb.append(s.getClass().getSimpleName()).append("@slot").append(s.getSlotIndex()).append(':')
                    .append(s.getX()).append(',').append(s.getY()).append(' ');
        }
        sb.append(']');
        return sb.toString();
    }

    private static List<String> objectSlots() {
        List<String> l = new ArrayList<>();
        for (var o : GameServices.level().getObjectManager().getActiveObjects()) {
            int slot = o instanceof AbstractObjectInstance a ? a.getSlotIndex() : -2;
            l.add(o.getClass().getSimpleName() + "@" + slot + (o.isDestroyed() ? "(destroyed)" : ""));
        }
        Collections.sort(l);
        return l;
    }

    private boolean hurtEver, deadEver, springContactEver;

    private void observe(GameplayCaptureSession session) throws Exception {
        var p = session.player();
        hurtEver |= p.isHurt();
        deadEver |= p.getDead();
        springContactEver |= p.getAnimationId() == Sonic3kAnimationIds.SPRING.id();
        ClamerObjectInstance c = clamer(CADENCE_RECORD);
        if (c != null && (int) field(c, "springCprop") != 0) springContactEver = true;
    }

    private String drive(GameplayCaptureSession session, Policy policy, int frame) throws Exception {
        var p = session.player();
        ClamerObjectInstance c165 = clamer(CADENCE_RECORD), c180 = clamer(STAGGERED_RECORD);
        var shots = projectiles();
        int r180 = c180 == null ? -1 : (int) field(c180, "routine");
        int r165 = c165 == null ? -1 : (int) field(c165, "routine");
        String script = policy.next(p.getCentreX(), p.getYSpeed(), !p.getAir(), r180, r165,
                c180 == null ? 0x1770 : c180.getX(), c165 == null ? 0x16F0 : c165.getX(),
                shots.stream().map(AbstractObjectInstance::getX).toList(), 0);
        session.step(token(frame, script));
        observe(session);
        return script;
    }

    @Test void committedClamerShotRestoresAndReplaysExactly() throws Exception {
        Files.createDirectories(OUT);
        Path jar = buildProbeJar(TestTempFiles.createTempDirectory("c1east-probe-replay"));
        int startX = 0x17F8, startY = 0x42C;
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null,
                startX, startY, null, false, false, null, null, false, 0, false);
        try (BufferedWriter notes = Files.newBufferedWriter(OUT.resolve("notes.txt"), StandardCharsets.UTF_8);
             var session = new GameplayCaptureSession(settings)) {
            session.applyMod(jar);
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), ZONE_CNZ, ACT_INDEX, settings);
            Policy policy = new Policy(false); // fixed alternative B, unchanged from P15
            boolean springSeen = false;
            int captureFrame = -1;
            String lastScript = "L";
            int frame = 0;
            for (; frame < 900; frame++) {
                lastScript = drive(session, policy, frame);
                var p = session.player();
                springSeen = springContactEver;
                if (!projectiles().isEmpty()) { captureFrame = frame; break; }
                assertFalse(p.getDead(), "fixed alternative B must not die before its first shot (P15 history)");
            }
            assertTrue(captureFrame >= 0, "STOP: no genuine ClamerAutoCloseProjectile within the 900-frame budget");
            var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
            // Capture AFTER the ordinary update that produced the first projectile.
            String before = tuple(session);
            List<String> objectsBefore = objectSlots();
            var saved = registry.capture();
            Policy savedPolicy = policy.copy();
            boolean hurtAtCapture = hurtEver, deadAtCapture = deadEver, springAtCapture = springContactEver;
            Bk2FrameInput lastInput = token(captureFrame, lastScript);
            notes.write("capture at frame " + captureFrame + " lastInput=" + lastScript + " springSeenBefore=" + springSeen + "\n");
            notes.write("pre-capture tuple: " + before + "\n");

            List<String> forward = new ArrayList<>();
            for (int n = 1; n <= 60; n++) {
                drive(session, policy, captureFrame + n);
                forward.add(tuple(session));
            }
            var forwardSnapshot = registry.capture();
            var fp = session.player();
            notes.write("forward +60 predicate: x=" + fp.getCentreX() + " hurtEver=" + hurtEver + " deadEver=" + deadEver
                    + " springContactEver=" + springContactEver + "\n");
            List<String> forwardObjects = objectSlots();

            registry.restore(saved);
            hurtEver = hurtAtCapture; deadEver = deadAtCapture; springContactEver = springAtCapture;
            session.restoreInputHistory(lastInput);
            // IMMEDIATELY after restore, before any update.
            String restored = tuple(session);
            List<String> objectsRestored = objectSlots();
            notes.write("restored tuple : " + restored + "\n");
            notes.write("immediate tuple equal: " + before.equals(restored) + "; class/slot multiset equal: "
                    + objectsBefore.equals(objectsRestored) + "\n");

            Policy replayPolicy = savedPolicy.copy();
            List<String> replay = new ArrayList<>();
            for (int n = 1; n <= 60; n++) {
                drive(session, replayPolicy, captureFrame + n);
                replay.add(tuple(session));
            }
            var replaySnapshot = registry.capture();
            List<String> replayObjects = objectSlots();
            int divergence = -1;
            for (int i = 0; i < 60 && divergence < 0; i++) if (!forward.get(i).equals(replay.get(i))) divergence = i + 1;
            notes.write("forward==replay per-frame tuples: " + (divergence < 0) + "\n");
            if (divergence >= 0) {
                notes.write("  first divergence +" + divergence + "\n  forward: " + forward.get(divergence - 1)
                        + "\n  replay : " + replay.get(divergence - 1) + "\n");
            }
            notes.write("class/slot multiset equal at +60: " + forwardObjects.equals(replayObjects) + "\n");
            if (!forwardObjects.equals(replayObjects)) {
                notes.write("  forward " + forwardObjects + "\n  replay  " + replayObjects + "\n");
            }
            int keys = 0, entries = 0;
            for (String key : forwardSnapshot.entries().keySet()) {
                var d = RewindSnapshotDiff.diffKey(key, forwardSnapshot.get(key), replaySnapshot.get(key));
                if (!d.isEmpty()) {
                    keys++;
                    entries += d.size();
                    notes.write("  snapshot diff " + key + " (" + d.size() + "): " + d + "\n");
                }
            }
            notes.write("snapshot diff keys=" + keys + " entries=" + entries + "\n");
            // Exact factual success of fixed alternative B (not the stage-6 flag).
            var finalPlayer = session.player();
            boolean success = finalPlayer.getCentreX() < 0x16B0 && springContactEver && !hurtEver && !deadEver;
            notes.write("final player x=" + finalPlayer.getCentreX() + " rings=" + finalPlayer.getRingCount()
                    + " hurtEver=" + hurtEver + " deadEver=" + deadEver + " nativeSpringContactEver=" + springContactEver + "\n");
            notes.write("B factual predicate (x<$16B0 && native spring contact && no hurt && not dead): " + success + "\n");
            notes.flush();
            assertEquals(before, restored, "immediate pre-update tuple equality");
            assertEquals(objectsBefore, objectsRestored, "immediate class/slot multiset equality");
            assertEquals(forward, replay, "60-frame per-frame tuple replay");
            assertEquals(forwardObjects, replayObjects, "class/slot multiset at +60");
            assertEquals(0, keys, "complete snapshot entries equal");
            assertTrue(success, "B factual predicate after committed replay");
        }
    }
}
