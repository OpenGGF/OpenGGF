package openggf.timeattack;

import com.openggf.mods.run.RunHandle;
import com.openggf.mods.run.RunHost;
import com.openggf.mods.run.RunSpec;
import com.openggf.mods.scene.SceneButtons;
import com.openggf.mods.scene.SceneContext;
import com.openggf.mods.scene.SceneGameplay;
import com.openggf.mods.scene.SceneKeys;
import com.openggf.mods.scene.SceneStorage;
import openggf.timeattack.ui.RecordingSceneCanvas;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Real scene publication lifecycle with refused/throwing storage and no ROM prerequisites. */
class TestTimeAttackSettingsPersistence {
    @TempDir Path root;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void falseAndThrownWritesRetainAcceptedSettingsForBoundedRetry(boolean throwing) throws Exception {
        Storage storage = new Storage();
        TimeAttackScene scene = new TimeAttackScene(root.resolve("ghosts"), root.resolve("identity"));
        SceneContext context = context(storage, 0, false);
        scene.enter(context);
        try {
            storage.writes = 0;
            storage.failures = 1;
            storage.throwing = throwing;
            scene.settings().setDisplayName("Ray");
            assertDoesNotThrow(() -> changed(scene));
            assertFalse(storage.files.get(TimeAttackSettings.FILE_NAME).contains("displayName=Ray"));
            for (int i = 0; i < 120; i++) scene.update(context);
            assertEquals("Ray", TimeAttackSettings.load(storage).displayName(),
                    "accepted settings must survive a failed publication");
            assertTrue(storage.writes >= 2 && storage.writes <= 4, "retry cadence is bounded");
        } finally {
            scene.exit(context);
        }
    }

    @Test
    void exitFlushesDirtySettingsAndSettingsPageDoesNotClaimSavedOnRefusal() throws Exception {
        Storage storage = new Storage();
        storage.files.put(TimeAttackSettings.FILE_NAME, "displayName=Racer\n"); // past the first-run name prompt
        TimeAttackScene scene = new TimeAttackScene(root.resolve("ghosts"), root.resolve("identity"));
        SceneContext context = context(storage, 0, false);
        scene.enter(context);
        for (int i = 0; i < 5; i++) scene.update(context(storage, 0, true));
        scene.update(context(storage, SceneKeys.ENTER, false));
        storage.failures = 1;
        scene.settings().setDisplayName("Exit value");
        changed(scene);
        RecordingSceneCanvas canvas = new RecordingSceneCanvas(320);
        scene.draw(context, canvas);
        assertFalse(canvas.shows("Saved"), "refused publication cannot be displayed as saved");
        assertTrue(canvas.shows("save"), "the settings page must display the pending outcome");
        scene.exit(context);
        assertEquals("Exit value", TimeAttackSettings.load(storage).displayName());
    }

    @Test
    void failedLegacyImportPublicationRetainsImportedValuesForRetry() throws Exception {
        Files.writeString(root.resolve("config.json"), "{\"TIME_ATTACK_NET_DISPLAY_NAME\":\"Legacy Ray\"}");
        Storage storage = new Storage();
        storage.failures = 1;
        TimeAttackScene scene = new TimeAttackScene(root.resolve("ghosts"), root.resolve("identity"));
        SceneContext context = context(storage, 0, false);
        scene.enter(context);
        try {
            assertEquals("Legacy Ray", scene.settings().displayName(),
                    "imported accepted values cannot be discarded on refused publication");
            for (int i = 0; i < 120; i++) scene.update(context);
            assertEquals("Legacy Ray", TimeAttackSettings.load(storage).displayName());
        } finally {
            scene.exit(context);
        }
    }

    private static void changed(TimeAttackScene scene) throws Exception {
        Method method = TimeAttackScene.class.getDeclaredMethod("settingsChanged");
        method.setAccessible(true);
        try {
            method.invoke(scene);
        } catch (InvocationTargetException failed) {
            if (failed.getCause() instanceof Exception cause) throw cause;
            throw failed;
        }
    }

    private static SceneContext context(Storage storage, int pressed, boolean down) {
        SceneGameplay gameplay = new SceneGameplay() {
            public List<String> availableGames() { return List.of("s2"); }
            public Optional<String> determinismFingerprint(String game) { return Optional.empty(); }
            public RunHandle launch(RunSpec spec, RunHost host) { throw new AssertionError("not launching"); }
        };
        return (SceneContext) Proxy.newProxyInstance(SceneContext.class.getClassLoader(),
                new Class<?>[]{SceneContext.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "storage" -> storage;
                    case "gameplay" -> gameplay;
                    case "width" -> 320;
                    case "height" -> 224;
                    case "ticks" -> 0L;
                    case "keyPressed" -> pressed != 0 && (int) args[0] == pressed;
                    case "typedText" -> "";
                    case "art" -> Proxy.newProxyInstance(SceneContext.class.getClassLoader(),
                            new Class<?>[]{com.openggf.mods.scene.SceneArt.class}, (art, call, values) -> null);
                    case "buttonRepeated" -> down && ((int) args[0] & SceneButtons.DOWN) != 0;
                    default -> method.getReturnType() == boolean.class ? false : null;
                });
    }

    static final class Storage implements SceneStorage {
        final Map<String, String> files = new HashMap<>();
        int failures;
        int writes;
        boolean throwing;

        public Optional<String> read(String name) { return Optional.ofNullable(files.get(name)); }
        public boolean write(String name, String text) {
            writes++;
            if (failures-- > 0) {
                if (throwing) throw new IllegalStateException("storage unavailable");
                return false;
            }
            files.put(name, text);
            return true;
        }
        public Optional<byte[]> readBytes(String name) { return Optional.empty(); }
        public boolean writeBytes(String name, byte[] bytes) { throw new AssertionError("text only"); }
        public boolean delete(String name) { return files.remove(name) != null; }
        public List<String> list() { return files.keySet().stream().sorted().toList(); }
    }
}
