package sitarhero;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PhysicalInput;
import com.openggf.control.PhysicalInputEvent;
import com.openggf.control.PhysicalGamepad;
import com.openggf.mods.run.PlayerPose;
import com.openggf.mods.run.RunEndReason;
import com.openggf.mods.run.RunHost;
import com.openggf.mods.run.RunStep;
import com.openggf.mods.scene.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Portable regressions for the authored save and input boundary. */
class PersistenceTest {
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

    @Test void calibrationUsesConfiguredTimestampedKickEdgesForBothPlayersAndAllBindingKinds() throws Exception {
        for (int playerIndex = 0; playerIndex < 2; playerIndex++) {
            for (char variant : new char[]{'K', 'B', 'A', 'T'}) {
                char kind = variant == 'T' ? 'A' : variant;
                MemoryStorage storage = new MemoryStorage();
                Object scene = scene("sitar-hero", "sitarhero.SitarScene");
                Object settings = field(scene, playerIndex == 0 ? "settings" : "settings2");
                set(scene, "settingsSecond", playerIndex == 1);
                Class<?> bindingType = Class.forName("sitarhero.controls.PhysicalBinding");
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

    @Test void cancellingAnUnsavedSettingsChangeCannotPublishTheStaleValue() throws Exception {
        MemoryStorage storage = new MemoryStorage();
        storage.files.put("settings.txt", new sitarhero.controls.ControlSettings().encode());
        storage.failures = 1;
        Object scene = scene("sitar-hero", "sitarhero.SitarScene"); call(scene, "enter", context(storage));
        Object settings = field(scene, "settings");
        call(settings, "inputOffsetMs", 37); call(scene, "saveSettings");
        call(settings, "inputOffsetMs", 0); call(scene, "saveSettings");
        assertTrue(storage.files.get("settings.txt").contains("input=0\n"));
        assertEquals(1, storage.writes, "returning to the published value cancels the dirty snapshot");
    }

    @Test void zeroOffsetCalibrationReportsAnAbsentSettingsWriteFailureHonestly() throws Exception {
        MemoryStorage storage = new MemoryStorage(); storage.failures = 1;
        Object scene = scene("sitar-hero", "sitarhero.SitarScene"); call(scene, "enter", context(storage));
        @SuppressWarnings("unchecked") List<Long> taps = (List<Long>) field(scene, "calibration");
        for (int i = 0; i < 8; i++) taps.add(0L);
        set(scene, "prepared", new ScenePreparedMusic() {
            public int sampleRate() { return 48000; }
            public long lengthSamples() { return 1000; }
            public List<SceneNoteEvent> notes() { return List.of(); }
        });
        set(scene, "player", Proxy.newProxyInstance(SceneMusicPlayer.class.getClassLoader(), new Class<?>[]{SceneMusicPlayer.class},
                (proxy, method, args) -> method.getReturnType() == boolean.class ? false : method.getReturnType() == long.class ? 0L : null));
        set(scene, "context", context(storage, SceneKeys.ENTER)); call(scene, "calibrate");
        assertNotEquals("Calibration saved", field(scene, "notice"));
        assertEquals(1, storage.writes);
        assertFalse(storage.files.containsKey("settings.txt"));
    }

    private static Object finishedSitar(MemoryStorage storage) throws Exception {
        var loader = PersistenceTest.class.getClassLoader();
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

    private static RunStep step(long index, boolean complete) {
        return new RunStep(index, 8, false, new PlayerPose(100, 200, 0, false, false, 2, false), complete, -1, false);
    }
    private static Object scene(String id, String type) throws Exception { return Class.forName(type).getConstructor().newInstance(); }
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
    }}
