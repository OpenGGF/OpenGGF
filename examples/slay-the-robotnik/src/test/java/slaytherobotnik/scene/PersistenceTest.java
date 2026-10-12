package slaytherobotnik.scene;

import static org.junit.jupiter.api.Assertions.*;

import com.openggf.mods.scene.*;
import java.lang.reflect.Proxy;
import java.util.*;
import org.junit.jupiter.api.Test;
import slaytherobotnik.content.Content;
import slaytherobotnik.run.Run;

class PersistenceTest {
    @Test void validMaximumCounterAndUnknownMetadataRemainReadable() {
        Storage storage = new Storage();
        storage.files.put("profile.properties", "wins.sonic=2147483647\nsetting.future=opaque\n");
        Profile profile = Profile.load(storage);
        profile.add("wins.sonic", 1);
        assertTrue(profile.save(storage));
        assertEquals(Integer.MAX_VALUE, Profile.load(storage).get("wins.sonic"));
        assertTrue(storage.files.get("profile.properties").contains("setting.future=opaque"));
    }

    @Test void failedExplicitReplacementRemainsAuthorizedAfterTitleReads() {
        Storage storage = new Storage();
        storage.files.put("run.properties", "version=999\nseed=7\n");
        Shell shell = shell(storage);
        storage.fail = true;
        shell.beginRun(Run.start(shell.catalog, "sonic", 8));
        assertFalse(shell.hasSavedRun());
        storage.fail = false;
        shell.flushPersistence();
        assertNotNull(shell.loadRun());
    }

    @Test
    void malformedProfileSurvivesOpenAndSave() {
        Storage storage = new Storage();
        String original = "wins.sonic=7\nbroken=\\uNOPE\n";
        storage.files.put("profile.properties", original);
        Profile profile = Profile.load(storage);
        profile.add("wins.sonic", 1);
        profile.save(storage);
        assertEquals(original, storage.files.get("profile.properties"));
    }

    @Test
    void unsupportedProfileVersionSurvivesOpenAndSave() {
        Storage storage = new Storage();
        String original = "version=999\nwins.sonic=7\n";
        storage.files.put("profile.properties", original);
        Profile profile = Profile.load(storage);
        profile.save(storage);
        assertEquals(original, storage.files.get("profile.properties"));
    }

    @Test
    void autosaveFailureCanRetryTheCurrentRun() {
        Storage storage = new Storage();
        Shell shell = shell(storage);
        shell.attach(Run.start(shell.catalog, "sonic", 7));
        storage.fail = true;
        assertFalse(shell.saveRun());
        assertNotNull(shell.persistenceNotice());
        assertFalse(storage.files.containsKey("run.properties"));
        storage.fail = false;
        shell.flushPersistence();
        assertNotNull(shell.loadRun());
        assertNull(shell.persistenceNotice());
    }

    @Test void rejectedRunSurvivesAutosaveAndDeleteUntilDeliberateNewRun() {
        Storage storage = new Storage();
        String original = "version=999\nseed=7\n";
        storage.files.put("run.properties", original);
        Shell shell = shell(storage);
        shell.attach(Run.start(shell.catalog, "sonic", 7));
        assertFalse(shell.saveRun());
        assertFalse(shell.deleteRun());
        shell.flushPersistence();
        assertEquals(original, storage.files.get("run.properties"));
        shell.beginRun(Run.start(shell.catalog, "tails", 9));
        assertNotNull(shell.loadRun());
        assertNotEquals(original, storage.files.get("run.properties"));
    }

    @Test void profilePublicationAndDeleteRetryDoNotRecountTheResult() {
        Storage storage = new Storage();
        Shell shell = shell(storage);
        shell.beginRun(Run.start(shell.catalog, "sonic", 8));
        shell.profile.add("wins.sonic", 1);
        storage.fail = true;
        assertFalse(shell.saveProfile());
        assertFalse(shell.deleteRun());
        assertTrue(storage.files.containsKey("run.properties"));
        storage.fail = false;
        shell.flushPersistence();
        shell.flushPersistence();
        assertFalse(storage.files.containsKey("run.properties"));
        assertEquals(1, Profile.load(storage).get("wins.sonic"));
        assertNull(shell.persistenceNotice());
    }

    private static Shell shell(Storage storage) {
        SceneContext ctx = (SceneContext) Proxy.newProxyInstance(SceneContext.class.getClassLoader(),
                new Class<?>[] {SceneContext.class}, (proxy, method, args) -> {
                    if (method.getName().equals("storage")) return storage;
                    throw new AssertionError(method.getName());
                });
        return new Shell(ctx, null, null, Content.build());
    }

    private static final class Storage implements SceneStorage {
        final Map<String, String> files = new HashMap<>();
        boolean fail;
        public Optional<String> read(String name) { return Optional.ofNullable(files.get(name)); }
        public boolean write(String name, String text) {
            if (fail) return false;
            files.put(name, text);
            return true;
        }
        public boolean delete(String name) { return !fail && files.remove(name) != null; }
        public List<String> list() { return files.keySet().stream().sorted().toList(); }
        public Optional<byte[]> readBytes(String name) { return Optional.empty(); }
        public boolean writeBytes(String name, byte[] bytes) { throw new AssertionError(); }
    }
}
