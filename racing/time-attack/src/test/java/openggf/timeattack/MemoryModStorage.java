package openggf.timeattack;

import com.openggf.mods.ModStorage;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory {@link ModStorage} for Time Attack tests. */
public final class MemoryModStorage implements ModStorage {
    public final Map<String, String> texts = new ConcurrentHashMap<>();
    private final Map<String, byte[]> bytes = new ConcurrentHashMap<>();

    @Override public Optional<String> read(String name) { return Optional.ofNullable(texts.get(name)); }

    @Override public boolean write(String name, String text) {
        texts.put(name, text);
        return true;
    }

    @Override public Optional<byte[]> readBytes(String name) {
        byte[] stored = bytes.get(name);
        return stored == null ? Optional.empty() : Optional.of(stored.clone());
    }

    @Override public boolean writeBytes(String name, byte[] data) {
        bytes.put(name, data.clone());
        return true;
    }

    @Override public boolean delete(String name) {
        return texts.remove(name) != null | bytes.remove(name) != null;
    }

    @Override public List<String> list() {
        Map<String, Boolean> names = new TreeMap<>();
        texts.keySet().forEach(name -> names.put(name, true));
        bytes.keySet().forEach(name -> names.put(name, true));
        return List.copyOf(names.keySet());
    }
}
