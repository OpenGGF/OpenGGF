package flappytails;

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
        assertEquals(0, call(records, "best", Class.forName("flappytails.Mode").getEnumConstants()[0]));
        set(scene, "cursor", 0); set(scene, "screenTick", 100L);
        call(scene, "updateTitle", context(storage, SceneKeys.ENTER));
        assertEquals(false, field(field(scene, "run"), "practice"));
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

    @Test void maximumValidTotalsRemainReadableAfterAnotherFlight() throws Exception {
        MemoryStorage storage = new MemoryStorage();
        storage.files.put("records.txt", "formatVersion=1\nflights=2147483647\ngates=2147483647\nrings=2147483647\n");
        FlappyScene scene = new FlappyScene(); SceneContext ctx = context(storage);
        scene.enter(ctx); call(scene, "startRun", ctx, 0, false);
        Object run = field(scene, "run"); set(run, "score", 12); set(run, "ringsTaken", 3);
        call(scene, "finishRun", ctx);
        Records loaded = Records.load(storage);
        assertEquals(Records.LoadDisposition.VALID, loaded.loadDisposition());
        assertEquals(Integer.MAX_VALUE, loaded.flights());
        assertEquals(Integer.MAX_VALUE, loaded.gates());
        assertEquals(Integer.MAX_VALUE, loaded.rings());
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
