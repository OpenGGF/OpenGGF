package com.openggf.mods.code;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PhysicalInput;
import com.openggf.control.PhysicalInputEvent;
import com.openggf.control.PhysicalGamepad;
import com.openggf.mods.run.PlayerPose;
import com.openggf.mods.run.RunEndReason;
import com.openggf.mods.run.RunHost;
import com.openggf.mods.run.RunStep;
import com.openggf.mods.scene.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Tests the packaged examples' actual load, settlement and exit boundaries with rejected writes. */
class TestModExamplePersistence {
    @TempDir static Path work;
    static final Map<String, ExampleModHarness> mods = new HashMap<>();

    @BeforeAll static void build() throws Exception {
        for (String id : List.of("flappy-tails", "sitar-hero", "hello-scene", "robotnik-tower-defense", "act-practice")) {
            mods.put(id, ExampleModHarness.build(Path.of("examples", id), work.resolve(id)));
        }
        mods.put("tutorial", ExampleModHarness.build(Path.of("examples/flappy-tails/tutorial/part-5-pillars"), work.resolve("tutorial")));
    }
    @AfterAll static void close() throws Exception { for (var mod : mods.values()) mod.close(); }

    @Test void flappyFutureFileSurvivesOpenExitAndFinishedRun() throws Exception {
        for (String rejected : List.of("formatVersion=999\nbest.CLASSIC=77\n", "broken records", "", "formatVersion=1\nflights=-1\n", "formatVersion=1\nflights=2\nflights=3\n")) {
            MemoryStorage storage = new MemoryStorage(); storage.files.put("records.txt", rejected);
            Object scene = scene("flappy-tails", "flappytails.FlappyScene");
            SceneContext ctx = context(storage);
            call(scene, "enter", ctx);
            call(scene, "exit", ctx);
            assertEquals(rejected, storage.files.get("records.txt"));
            call(scene, "startRun", ctx, 0, false);
            call(scene, "finishRun", ctx);
            call(scene, "exit", ctx);
            assertEquals(rejected, storage.files.get("records.txt"), "rejected primary must survive a finished run");
            assertEquals(0, storage.writes);
        }
    }

    @Test void failedFlappyRecordWriteRetriesWithoutCountingTheFlightTwice() throws Exception {
        MemoryStorage storage = new MemoryStorage(); storage.failures = 1;
        Object scene = scene("flappy-tails", "flappytails.FlappyScene"); SceneContext ctx = context(storage);
        call(scene, "enter", ctx); call(scene, "startRun", ctx, 0, false); call(scene, "finishRun", ctx);
        assertFalse(storage.files.containsKey("records.txt"));
        call(scene, "exit", ctx);
        assertTrue(storage.files.getOrDefault("records.txt", "").contains("flights=1"), "exit retries dirty records");
        assertEquals(2, storage.writes);
    }

    @Test void flappyPracticeAtEveryUnlockedZoneAndRetryCannotSetRecords() throws Exception {
        MemoryStorage storage = new MemoryStorage();
        storage.files.put("records.txt", "formatVersion=1\nfurthestZone=4\n");
        Object scene = scene("flappy-tails", "flappytails.FlappyScene"); SceneContext ctx = context(storage);
        call(scene, "enter", ctx);
        for (int zone = 0; zone < 5; zone++) {
            set(scene, "cursor", 1); set(scene, "practiceZone", zone); set(scene, "screenTick", 100L);
            call(scene, "updateTitle", context(storage, SceneKeys.ENTER));
            Object run = field(scene, "run");
            assertEquals(true, field(run, "practice"), "Practice command is explicit even at zone " + zone);
            set(run, "score", 12); call(scene, "finishRun", ctx);
            assertEquals(0, field(scene, "resultMedal"));
            set(scene, "screenTick", 110L); set(scene, "shownScore", 12);
            call(scene, "updateResults", context(storage, SceneKeys.SPACE));
            assertEquals(true, field(field(scene, "run"), "practice"), "retry retains practice");
        }
        Object records = field(scene, "records");
        assertEquals(5, call(records, "flights"));
        assertEquals(0, call(records, "best", mods.get("flappy-tails").loader().loadClass("flappytails.Mode").getEnumConstants()[0]));
        set(scene, "cursor", 0); set(scene, "screenTick", 100L);
        call(scene, "updateTitle", context(storage, SceneKeys.ENTER));
        assertEquals(false, field(field(scene, "run"), "practice"));
    }

    @Test void sitarFutureFilesSurviveEntryExitAndSaveRequests() throws Exception {
        MemoryStorage storage = new MemoryStorage();
        storage.files.put("career.txt", "sitar-career=999\nclear=aiz1\n");
        storage.files.put("settings.txt", "sitar-settings=999\ninput=143\n");
        storage.files.put("settings-p2.txt", "unreadable settings");
        storage.files.put("profile.txt", "sitar-profile=999\nattempts=8\n");
        Map<String, String> before = Map.copyOf(storage.files);
        Object scene = scene("sitar-hero", "sitarhero.SitarScene"); SceneContext ctx = context(storage);
        call(scene, "enter", ctx); call(scene, "saveCareer"); call(scene, "saveSettings"); call(scene, "exit", ctx);
        assertEquals(before, storage.files); assertEquals(0, storage.writes);
        assertEquals(0, call(field(scene, "settings"), "inputOffsetMs"), "future settings cannot mutate defaults");
    }

    @Test void sitarFailedSettingsWriteRetriesOnExitAndKeepsFailureNotice() throws Exception {
        MemoryStorage storage = new MemoryStorage(); storage.failures = 1;
        Object scene = scene("sitar-hero", "sitarhero.SitarScene"); SceneContext ctx = context(storage);
        call(scene, "enter", ctx); call(field(scene, "settings"), "inputOffsetMs", 37);
        call(scene, "saveSettings");
        assertTrue(field(scene, "notice").toString().contains("could not"));
        call(scene, "exit", ctx);
        assertTrue(storage.files.getOrDefault("settings.txt", "").contains("input=37"));
    }

    @Test void sitarFutureProfileSurvivesFinishedPerformanceAndRetry() throws Exception {
        MemoryStorage storage = new MemoryStorage();
        String future = "sitar-profile=999\nattempts=8\n"; storage.files.put("profile.txt", future);
        Object scene = finishedSitar(storage);
        call(scene, "finish"); call(scene, "exit", context(storage));
        assertEquals(future, storage.files.get("profile.txt"));
    }

    @Test void sitarRejectedLegacyScoreSurvivesAnEligibleClear() throws Exception {
        MemoryStorage storage = new MemoryStorage();
        String rejected = "sitar-score=999\nscore=88\n"; storage.files.put("angel-island-1.sitar.txt", rejected);
        Object scene = finishedSitar(storage); call(scene, "finish"); call(scene, "exit", context(storage));
        assertEquals(rejected, storage.files.get("angel-island-1.sitar.txt"));
    }

    @Test void sitarFailedProfileWriteRetriesWithoutCountingThePerformanceTwice() throws Exception {
        MemoryStorage storage = new MemoryStorage(); storage.failures = 1;
        Object scene = finishedSitar(storage); call(scene, "finish");
        assertFalse(storage.files.containsKey("profile.txt"));
        call(scene, "exit", context(storage));
        assertTrue(storage.files.getOrDefault("profile.txt", "").contains("attempts=1"));
        assertEquals(1L, call(field(scene, "profile"), "attempts"));
    }

    private static Object finishedSitar(MemoryStorage storage) throws Exception {
        var loader = mods.get("sitar-hero").loader();
        Object scene = scene("sitar-hero", "sitarhero.SitarScene"); call(scene, "enter", context(storage));
        Object note = loader.loadClass("sitarhero.model.ChartNote")
                .getConstructor(long.class, long.class, int.class, boolean.class, int.class, int.class).newInstance(100L, 100L, 1, false, -1, 0);
        Object chart = loader.loadClass("sitarhero.model.Chart").getConstructor(List.class, long.class, long.class).newInstance(List.of(note), 1000L, 500L);
        Object session = loader.loadClass("sitarhero.model.RhythmSession").getConstructor(chart.getClass(), boolean.class, int.class).newInstance(chart, false, 48000);
        call(session, "input", 100L, 1, 0, true, false, false);
        call(session, "advance", 1001L);
        Object song = loader.loadClass("sitarhero.model.SongSpec")
                .getConstructor(String.class, String.class, String.class, int.class, int.class, int.class, int.class)
                .newInstance("angel-island-1", "Angel Island", "s3k", 1, 60, 0, 0);
        set(scene, "session", session); set(scene, "song", song);
        set(scene, "mode", loader.loadClass("sitarhero.SitarScene$Mode").getEnumConstants()[1]);
        return scene;
    }

    @Test void helloRejectedBestSurvivesCollectAndExit() throws Exception {
        for (String rejected : List.of("version=999\nbest=88", "broken", "-1", "")) {
            MemoryStorage storage = new MemoryStorage(); storage.files.put("best.txt", rejected);
            Object scene = scene("hello-scene", "hello.HelloScene"); SceneContext ctx = context(storage);
            call(scene, "enter", ctx); set(scene, "x", 40f); call(scene, "collectRings", ctx); call(scene, "exit", ctx);
            assertEquals(rejected, storage.files.get("best.txt")); assertEquals(0, storage.writes);
        }
    }

    @Test void helloFailedBestWriteRetriesWhenNoNewRecordIsCollected() throws Exception {
        for (boolean throwing : List.of(false, true)) {
            MemoryStorage storage = new MemoryStorage(); storage.failures = 1; storage.throwFailure = throwing;
            Object scene = scene("hello-scene", "hello.HelloScene"); SceneContext ctx = context(storage);
            call(scene, "enter", ctx); set(scene, "x", 40f);
            assertDoesNotThrow(() -> call(scene, "collectRings", ctx));
            call(scene, "exit", ctx);
            assertEquals("1", storage.files.get("best.txt")); assertEquals(2, storage.writes);
        }
    }

    @Test void failedTowerRecordWriteRetriesWithoutCountingTheCampaignTwice() throws Exception {
        for (boolean throwing : List.of(false, true)) {
            MemoryStorage storage = new MemoryStorage(); storage.failures = 1; storage.throwFailure = throwing;
            Object scene = mods.get("robotnik-tower-defense").loader().loadClass("towerdefense.TowerDefenseScene")
                    .getConstructor(byte[].class).newInstance(new byte[0]);
            Object battle = mods.get("robotnik-tower-defense").loader().loadClass("towerdefense.core.Battlefield").getConstructor().newInstance();
            set(battle, "phase", "WON"); set(battle, "wave", 15); set(scene, "battle", battle);
            SceneContext ctx = context(storage);
            assertDoesNotThrow(() -> call(scene, "record", ctx));
            assertTrue(field(scene, "message").toString().toLowerCase().contains("save"));
            call(scene, "exit", ctx); call(scene, "exit", ctx);
            assertTrue(storage.files.getOrDefault("records.txt", "").endsWith(",1"), "retry publishes exactly one win");
            assertEquals(2, storage.writes);
        }
    }

    @Test void practiceFailedBestWriteRetriesAfterCompletionWithoutAnotherPersonalBest() throws Exception {
        for (boolean throwing : List.of(false, true)) {
            MemoryStorage storage = new MemoryStorage(); storage.failures = 1; storage.throwFailure = throwing;
            Object run = scene("act-practice", "practice.PracticeHost"); call(run, "prepare", "s2", storage);
            RunHost host = (RunHost) run;
            host.afterStep(step(0, false));
            assertDoesNotThrow(() -> host.afterStep(step(1, true)));
            host.onRunEnded(RunEndReason.ACT_COMPLETED);
            assertEquals("1", storage.files.get("best-s2.txt")); assertEquals(2, storage.writes);
        }
    }

    @Test void practiceRejectedBestSurvivesACompletedRun() throws Exception {
        for (String rejected : List.of("version=999\ntime=88", "broken", "-1", "")) {
            MemoryStorage storage = new MemoryStorage(); storage.files.put("best-s2.txt", rejected);
            Object run = scene("act-practice", "practice.PracticeHost"); call(run, "prepare", "s2", storage);
            RunHost host = (RunHost) run; host.afterStep(step(0, false)); host.afterStep(step(1, true));
            host.onRunEnded(RunEndReason.ACT_COMPLETED);
            assertEquals(rejected, storage.files.get("best-s2.txt")); assertEquals(0, storage.writes);
        }
    }

    @Test void persistentFlappyWriteFailureKeepsDirtyProgressAndUsesBoundedRetries() throws Exception {
        MemoryStorage storage = new MemoryStorage(); storage.failures = Integer.MAX_VALUE;
        Object scene = scene("flappy-tails", "flappytails.FlappyScene"); SceneContext ctx = context(storage);
        call(scene, "enter", ctx); call(scene, "startRun", ctx, 0, false); call(scene, "finishRun", ctx);
        for (int tick = 0; tick < 120; tick++) call(scene, "update", ctx);
        assertTrue(storage.writes >= 2 && storage.writes <= 4, "failed publication retries with a bounded cadence");
        assertFalse(storage.files.containsKey("records.txt"));
        assertEquals(1, call(field(scene, "records"), "flights"));
        call(scene, "exit", ctx);
    }

    @Test void validAndSupportedLegacySavesRemainUsable() throws Exception {
        MemoryStorage storage = new MemoryStorage(); storage.files.put("best.txt", " 7\n");
        Object hello = scene("hello-scene", "hello.HelloScene"); call(hello, "enter", context(storage));
        assertEquals(7, field(hello, "best"));
        storage.files.put("best-s2.txt", "12\n");
        Object practice = scene("act-practice", "practice.PracticeHost"); call(practice, "prepare", "s2", storage);
        assertEquals(12, call(practice, "bestFrames"));
        storage.files.put("records.txt", "formatVersion=1\nbest.CLASSIC=14\nfurthestZone=2\nfuture-extra=kept\n");
        Object flappy = scene("flappy-tails", "flappytails.FlappyScene"); call(flappy, "enter", context(storage));
        assertEquals(2, call(field(flappy, "records"), "furthestZone"));
        Object sitar = scene("sitar-hero", "sitarhero.SitarScene");
        storage.files.put("settings.txt", "sitar-settings=1\ninput=17\nextra=value\n");
        storage.files.put("profile.txt", "sitar-profile=1\nattempts=0\ntotal-score=0\nextra=value\n");
        storage.files.put("career.txt", "sitar-career=1\n");
        call(sitar, "enter", context(storage));
        assertEquals(17, call(field(sitar, "settings"), "inputOffsetMs"));
        Map<String, String> before = Map.copyOf(storage.files); call(sitar, "exit", context(storage));
        assertEquals(before, storage.files, "an unchanged lifecycle preserves accepted extra fields too");
    }

    @Test void tutorialBestPreservesRejectedFilesAndRetriesFalseWrites() throws Exception {
        for (String rejected : List.of("broken", "-1", "")) {
            MemoryStorage storage = new MemoryStorage(); storage.files.put("best.txt", rejected);
            Object scene = scene("tutorial", "flappytails.FlappyScene"); SceneContext ctx = context(storage);
            call(scene, "enter", ctx); set(scene, "flying", true); set(scene, "score", 1);
            set(field(scene, "flight"), "yPos", 210 << 8); call(scene, "update", ctx); call(scene, "exit", ctx);
            assertEquals(rejected, storage.files.get("best.txt")); assertEquals(0, storage.writes);
        }
        MemoryStorage storage = new MemoryStorage(); storage.failures = 1;
        Object scene = scene("tutorial", "flappytails.FlappyScene"); SceneContext ctx = context(storage);
        call(scene, "enter", ctx); set(scene, "flying", true); set(scene, "score", 1);
        set(field(scene, "flight"), "yPos", 210 << 8); call(scene, "update", ctx); call(scene, "exit", ctx);
        assertEquals("1", storage.files.get("best.txt")); assertEquals(2, storage.writes);
    }

    @Test void calibrationUsesConfiguredTimestampedKickEdgesForBothPlayersAndAllBindingKinds() throws Exception {
        for (int playerIndex = 0; playerIndex < 2; playerIndex++) {
            for (char variant : new char[]{'K', 'B', 'A', 'T'}) {
                char kind = variant == 'T' ? 'A' : variant;
                MemoryStorage storage = new MemoryStorage();
                Object scene = scene("sitar-hero", "sitarhero.SitarScene");
                Object settings = field(scene, playerIndex == 0 ? "settings" : "settings2");
                set(scene, "settingsSecond", playerIndex == 1);
                Class<?> bindingType = mods.get("sitar-hero").loader().loadClass("sitarhero.controls.PhysicalBinding");
                int device = kind == 'K' ? -1 : 2, code = kind == 'K' ? 86 : kind == 'B' ? 5 : variant == 'T' ? 4 : 3;
                Object binding = bindingType.getConstructor(char.class, int.class, int.class, int.class)
                        .newInstance(kind, device, code, variant == 'A' ? -1 : 1);
                call(settings, "bind", true, kind != 'K', 4, binding);
                Object controls = field(scene, playerIndex == 0 ? "controls" : "controls2");
                call(controls, "reset", PhysicalInput.neutral(), true);
                List<Long> sampled = new java.util.ArrayList<>();
                SceneMusicPlayer player = (SceneMusicPlayer) Proxy.newProxyInstance(SceneMusicPlayer.class.getClassLoader(),
                        new Class<?>[]{SceneMusicPlayer.class}, (proxy, method, args) -> {
                            if (method.getName().equals("samplePositionAt")) { sampled.add((Long) args[0]); return args[0]; }
                            if (method.getReturnType() == boolean.class) return false;
                            if (method.getReturnType() == long.class) return 0L;
                            return null;
                        });
                set(scene, "player", player);
                set(scene, "prepared", new ScenePreparedMusic() {
                    public int sampleRate() { return 48000; }
                    public long lengthSamples() { return 10000; }
                    public List<SceneNoteEvent> notes() {
                        return java.util.stream.LongStream.rangeClosed(1, 6).mapToObj(n ->
                                new SceneNoteEvent(0, SceneNoteEvent.Kind.DAC, 0, 0, 0, n * 1000, 1)).toList();
                    }
                });
                PhysicalInputEvent.Kind eventKind = kind == 'K' ? PhysicalInputEvent.Kind.KEY
                        : kind == 'B' ? PhysicalInputEvent.Kind.BUTTON : PhysicalInputEvent.Kind.AXIS;
                float down = variant == 'A' ? -1f : 1f;
                List<PhysicalInputEvent> events = List.of(
                        new PhysicalInputEvent(1, 1037, eventKind, device, code, down),
                        new PhysicalInputEvent(2, 2037, eventKind, device, code, down), // held/repeat cannot tap
                        new PhysicalInputEvent(3, 3037, eventKind, device, code, 0),
                        new PhysicalInputEvent(4, 4037, eventKind, device, code, down));
                PhysicalInput input = new PhysicalInput(5000, List.of(), List.of(), events, 0);
                SceneContext base = context(storage);
                SceneContext ctx = (SceneContext) Proxy.newProxyInstance(SceneContext.class.getClassLoader(), new Class<?>[]{SceneContext.class},
                        (proxy, method, args) -> method.getName().equals("physicalInput") ? input : method.invoke(base, args));
                set(scene, "context", ctx);
                call(scene, "calibrate");
                assertEquals(List.of(1037L, 4037L), sampled, "configured " + kind + " P" + playerIndex + " keeps original edge times");
                assertEquals(List.of(37L, 37L), field(scene, "calibration"));
            }
        }
    }

    private static RunStep step(long index, boolean complete) {
        return new RunStep(index, 8, false, new PlayerPose(100, 200, 0, false, false, 2, false), complete, -1, false);
    }
    private static Object scene(String id, String type) throws Exception { return mods.get(id).loader().loadClass(type).getConstructor().newInstance(); }
    private static Object field(Object target, String name) throws Exception { Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target); }
    private static void set(Object target, String name, Object value) throws Exception { Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); f.set(target, value); }
    private static Object call(Object target, String name, Object... args) throws Exception {
        for (Method method : target.getClass().getDeclaredMethods()) if (method.getName().equals(name) && method.getParameterCount() == args.length) {
            method.setAccessible(true);
            try { return method.invoke(target, args); } catch (InvocationTargetException failure) {
                if (failure.getCause() instanceof Exception exception) throw exception;
                throw failure;
            }
        }
        for (Method method : target.getClass().getMethods()) if (method.getName().equals(name) && method.getParameterCount() == args.length) {
            try { return method.invoke(target, args); } catch (InvocationTargetException failure) {
                if (failure.getCause() instanceof Exception exception) throw exception;
                throw failure;
            }
        }
        throw new NoSuchMethodException(name);
    }
    private static SceneContext context(MemoryStorage storage, int... pressed) {
        return (SceneContext) Proxy.newProxyInstance(SceneContext.class.getClassLoader(), new Class<?>[]{SceneContext.class}, (proxy, method, args) -> switch (method.getName()) {
            case "storage" -> storage;
            case "width" -> 400; case "height" -> 224; case "ticks" -> 100L;
            case "mouse" -> SceneMouse.none(); case "input" -> LogicalInputSnapshot.neutral(); case "physicalInput" -> PhysicalInput.neutral();
            case "art" -> Proxy.newProxyInstance(SceneArt.class.getClassLoader(), new Class<?>[]{SceneArt.class}, (p, m, a) -> m.getName().equals("availableGames") ? List.of() : null);
            case "audio" -> Proxy.newProxyInstance(SceneAudio.class.getClassLoader(), new Class<?>[]{SceneAudio.class}, (p, m, a) -> m.getReturnType() == boolean.class ? false : null);
            case "keyPressed" -> java.util.Arrays.stream(pressed).anyMatch(key -> key == (int) args[0]);
            default -> method.getReturnType() == boolean.class ? false : null;
        });
    }
    private static final class MemoryStorage implements SceneStorage {
        final Map<String, String> files = new HashMap<>(); final Map<String, byte[]> binary = new HashMap<>();
        int failures, writes; boolean throwFailure;
        @Override public Optional<String> read(String name) { return Optional.ofNullable(files.get(name)); }
        @Override public boolean write(String name, String text) {
            writes++;
            if (failures-- > 0) { if (throwFailure) throw new IllegalStateException("storage unavailable"); return false; }
            files.put(name, text); return true;
        }
        @Override public Optional<byte[]> readBytes(String name) { return Optional.ofNullable(binary.get(name)).map(byte[]::clone); }
        @Override public boolean writeBytes(String name, byte[] bytes) { binary.put(name, bytes.clone()); return true; }
        @Override public boolean delete(String name) { return files.remove(name) != null | binary.remove(name) != null; }
        @Override public List<String> list() { return java.util.stream.Stream.concat(files.keySet().stream(), binary.keySet().stream()).distinct().sorted().toList(); }
    }
}
