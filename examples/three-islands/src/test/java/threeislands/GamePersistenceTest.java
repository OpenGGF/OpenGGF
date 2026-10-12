package threeislands;

import static org.junit.jupiter.api.Assertions.*;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.mods.scene.*;
import java.lang.reflect.Proxy;
import java.util.*;
import org.junit.jupiter.api.Test;
import threeislands.core.Progress;
import threeislands.core.SaveCodec;

/** Exercises the real game's save boundary without requiring ROM art. */
class GamePersistenceTest {
    @Test
    void corruptPrimaryRecoverySurvivesFailedPrimaryWrite() throws Exception {
        Storage storage = new Storage();
        String recovery = SaveCodec.encode(new Progress(73));
        storage.files.put(Game.SAVE, "damaged");
        storage.files.put(Game.BACKUP, recovery);
        Game game = game(storage);
        game.progress = game.readSave().orElseThrow();
        game.progress.addRings(10);
        storage.fail = Game.SAVE;
        game.save();
        assertEquals(recovery, storage.files.get(Game.BACKUP));
        assertEquals("damaged", storage.files.get(Game.SAVE));
        storage.fail = null;
        game.save();
        assertEquals(game.progress.rings(), SaveCodec.decode(storage.files.get(Game.SAVE)).rings());
    }

    @Test
    void failedBackupPreventsPrimaryReplacement() throws Exception {
        Storage storage = new Storage();
        String original = SaveCodec.encode(new Progress(19));
        storage.files.put(Game.SAVE, original);
        Game game = game(storage);
        game.progress = game.readSave().orElseThrow();
        game.progress.addRings(9);
        storage.fail = Game.BACKUP;
        game.save();
        assertEquals(original, storage.files.get(Game.SAVE));
        storage.fail = null;
        game.save();
        assertEquals(original, storage.files.get(Game.BACKUP));
        assertEquals(game.progress.rings(), SaveCodec.decode(storage.files.get(Game.SAVE)).rings());
    }

    @Test
    void rejectedPrimarySurvivesSaveAndDoesNotLoadOlderBackup() throws Exception {
        Storage storage = new Storage();
        String future = "three-islands-save 999\nrings=123\n";
        storage.files.put(Game.SAVE, future);
        storage.files.put(Game.BACKUP, SaveCodec.encode(new Progress(18)));
        Game game = game(storage);
        assertTrue(game.readSave().isEmpty());
        game.save();
        assertEquals(future, storage.files.get(Game.SAVE));
    }

    private static Game game(Storage storage) throws Exception {
        SceneArt art = (SceneArt) Proxy.newProxyInstance(SceneArt.class.getClassLoader(),
                new Class<?>[] {SceneArt.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "availableGames" -> List.of();
                    case "rom" -> null;
                    default -> throw new AssertionError(method.getName());
                });
        SceneContext context = (SceneContext) Proxy.newProxyInstance(SceneContext.class.getClassLoader(),
                new Class<?>[] {SceneContext.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "art" -> art;
                    case "storage" -> storage;
                    case "mouse" -> SceneMouse.none();
                    case "input" -> LogicalInputSnapshot.neutral();
                    case "width" -> 400;
                    case "height" -> 224;
                    case "keyPressed", "keyDown", "buttonPressed", "buttonDown", "buttonRepeated" -> false;
                    default -> throw new AssertionError(method.getName());
                });
        return new Game(context, GamePersistenceTest.class.getResourceAsStream("/text/font.txt").readAllBytes(),
                new byte[0]);
    }

    private static final class Storage implements SceneStorage {
        final Map<String, String> files = new HashMap<>();
        String fail;
        public Optional<String> read(String name) { return Optional.ofNullable(files.get(name)); }
        public boolean write(String name, String text) {
            if (name.equals(fail)) return false;
            files.put(name, text);
            return true;
        }
        public boolean delete(String name) { return files.remove(name) != null; }
        public List<String> list() { return files.keySet().stream().sorted().toList(); }
        public Optional<byte[]> readBytes(String name) { return Optional.empty(); }
        public boolean writeBytes(String name, byte[] bytes) { throw new AssertionError(); }
    }
}
