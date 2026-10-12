package practice;

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
