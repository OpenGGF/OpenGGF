package com.openggf.mods.state;

import com.openggf.mods.ModStorage;
import com.openggf.mods.scene.host.ModStorageFactory;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static com.openggf.mods.state.ModSaveLoadResult.Status.*;
import static com.openggf.mods.state.ModSaveWriteResult.*;
import static org.junit.jupiter.api.Assertions.*;

class TestModSaveSlot {
    @TempDir Path root;
    private static final ModSaveCodec<Integer> CODEC = new ModSaveCodec<>() {
        public ModSaveLoadResult<Integer> decode(String text) {
            if (text.startsWith("v2:")) return ModSaveLoadResult.rejected(INCOMPATIBLE);
            try {
                if (!text.startsWith("v1:")) return ModSaveLoadResult.rejected(CORRUPT);
                int value = Integer.parseInt(text.substring(3));
                return value >= 0 ? ModSaveLoadResult.valid(value) : ModSaveLoadResult.rejected(CORRUPT);
            } catch (NumberFormatException bad) { return ModSaveLoadResult.rejected(CORRUPT); }
        }
        public String encode(Integer value) { return "v1:" + value; }
    };
    private ModSaveSlot<Integer> slot(ModStorage storage) {
        return new ModSaveSlot<>(storage, "save.txt", "save.bak", CODEC);
    }

    @Test void futurePrimaryNeverFallsBackOrChangesWithoutExplicitReplacement() {
        Memory storage = new Memory();
        storage.files.put("save.txt", "v2:9"); storage.files.put("save.bak", "v1:2");
        var slot = slot(storage);
        assertEquals(INCOMPATIBLE, slot.load().status());
        assertEquals(REPLACEMENT_REQUIRED, slot.save(5));
        assertEquals("v2:9", storage.files.get("save.txt"));
        slot.allowReplacement(); storage.fail = "save.txt";
        assertEquals(PRIMARY_FAILED, slot.save(5));
        assertEquals(INCOMPATIBLE, slot.load().status());
        assertTrue(slot.writable(), "Reloading old bytes must not revoke explicit replacement");
        storage.fail = null;
        assertEquals(SAVED, slot.retry());
        assertEquals(5, slot.load().value().orElseThrow());
        assertEquals("v1:2", storage.files.get("save.bak"));
    }

    @Test void recoverySurvivesPrimaryFailureAndRetryPublishesExactCheckpoint() {
        Memory storage = new Memory(); storage.files.put("save.txt", "broken");
        storage.files.put("save.bak", "v1:7");
        var slot = slot(storage); assertEquals(RECOVERED, slot.load().status());
        storage.fail = "save.txt";
        assertEquals(PRIMARY_FAILED, slot.save(8));
        assertEquals("v1:7", storage.files.get("save.bak"));
        assertTrue(slot.pending()); storage.fail = null;
        assertEquals(SAVED, slot.retry()); assertFalse(slot.pending());
        assertEquals("v1:8", storage.files.get("save.txt"));
    }

    @Test void falseAndIoExceptionAtEachPublicationBoundaryPreserveGeneration() {
        for (String failing : List.of("save.bak", "save.txt")) for (boolean throwsIo : List.of(false, true)) {
            Memory storage = new Memory(); storage.files.put("save.txt", "v1:7");
            var slot = slot(storage); slot.load(); storage.fail = failing; storage.throwsIo = throwsIo;
            assertEquals(failing.equals("save.bak") ? BACKUP_FAILED : PRIMARY_FAILED, slot.save(8));
            assertEquals("v1:7", storage.files.get("save.txt"));
            if (failing.equals("save.txt")) assertEquals("v1:7", storage.files.get("save.bak"));
            storage.fail = null; assertEquals(SAVED, slot.retry());
            assertEquals("v1:7", storage.files.get("save.bak"));
            assertEquals("v1:8", storage.files.get("save.txt"));
        }
    }

    @Test void aFuturePrimaryArrivingAfterLoadAlsoRequiresReplacement() {
        Memory storage = new Memory(); var slot = slot(storage);
        assertEquals(ABSENT, slot.load().status()); storage.files.put("save.txt", "v2:9");
        assertEquals(REPLACEMENT_REQUIRED, slot.save(3));
        assertEquals("v2:9", storage.files.get("save.txt"));
    }

    @Test void productionStorageEnforcesBoundsIsolationAndNames() throws Exception {
        var storage = ModStorageFactory.forOwner(root, "slot-test");
        var slot = slot(storage); assertEquals(ABSENT, slot.load().status());
        assertEquals(SAVED, slot.save(7)); assertEquals(7, slot.load().value().orElseThrow());
        assertEquals(ABSENT, slot(ModStorageFactory.forOwner(root, "other")).load().status());
        assertThrows(IllegalArgumentException.class, () -> new ModSaveSlot<>(storage, "../other", CODEC));
        assertThrows(IllegalArgumentException.class, () -> new ModSaveSlot<>(storage, ".hidden", CODEC));
        assertThrows(IllegalArgumentException.class, () -> new ModSaveSlot<>(storage, "save.txt", "save.txt", CODEC));
        Files.writeString(root.resolve("mods/slot-test/save.txt"), "x".repeat(ModStorage.MAX_TEXT_BYTES + 1));
        assertEquals(UNREADABLE, slot.load().status());
        assertEquals(REPLACEMENT_REQUIRED, slot.save(8));
        assertEquals(ModStorage.MAX_TEXT_BYTES + 1, Files.size(root.resolve("mods/slot-test/save.txt")));
    }

    @Test void unreadableAndCorruptRemainDistinctAndPreserved() {
        Memory storage = new Memory(); storage.files.put("save.txt", "broken");
        var slot = slot(storage); assertEquals(CORRUPT, slot.load().status());
        storage.unreadable = true; assertEquals(UNREADABLE, slot.load().status());
        assertEquals(REPLACEMENT_REQUIRED, slot.save(1));
        assertEquals("broken", storage.files.get("save.txt"));
    }

    @Test void codecFaultsAndRevokedHandlesPropagateInsteadOfBecomingSaveFailures() {
        Memory storage = new Memory(); storage.files.put("save.txt", "v1:3");
        ModSaveCodec<Integer> broken = new ModSaveCodec<>() {
            public ModSaveLoadResult<Integer> decode(String text) { throw new UncheckedIOException(new IOException("codec fault")); }
            public String encode(Integer value) { return "v1:" + value; }
        };
        assertThrows(UncheckedIOException.class, () -> new ModSaveSlot<>(storage, "save.txt", broken).load());
        ModStorage revoked = (ModStorage) java.lang.reflect.Proxy.newProxyInstance(ModStorage.class.getClassLoader(),
                new Class<?>[]{ModStorage.class}, (proxy, method, args) -> { throw new IllegalStateException("revoked"); });
        assertThrows(IllegalStateException.class, () -> slot(revoked).load());
    }

    private static class Memory implements ModStorage {
        final Map<String,String> files = new HashMap<>();
        String fail; boolean throwsIo; boolean unreadable;
        public Optional<String> read(String name) { return unreadable ? Optional.empty() : Optional.ofNullable(files.get(name)); }
        public boolean write(String name, String text) {
            if (name.equals(fail)) {
                if (throwsIo) throw new UncheckedIOException(new IOException("write"));
                return false;
            }
            files.put(name,text); return true;
        }
        public boolean delete(String name) { return files.remove(name) != null; }
        public List<String> list() { return files.keySet().stream().sorted().toList(); }
        public Optional<byte[]> readBytes(String name) { throw new AssertionError(); }
        public boolean writeBytes(String name, byte[] bytes) { throw new AssertionError(); }
    }
}
