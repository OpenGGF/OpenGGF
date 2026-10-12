package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneStorage;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Properties;

/**
 * Persistent player profile: per-character records and the last seed. Stored as
 * {@code profile.properties} in the mod's storage.
 */
public final class Profile {
    private static final String FILE = "profile.properties";
    private final Properties values = new Properties();
    private final com.openggf.mods.state.ModSaveSlot<Properties> slot;
    private boolean dirty;
    private com.openggf.mods.state.ModSaveLoadResult.Status loadStatus;

    private Profile(SceneStorage storage) {
        slot = new com.openggf.mods.state.ModSaveSlot<>(storage, FILE, new com.openggf.mods.state.ModSaveCodec<>() {
            public com.openggf.mods.state.ModSaveLoadResult<Properties> decode(String text) {
                var status = com.openggf.mods.state.ModSaveLoadResult.Status.CORRUPT;
                try {
                    Properties candidate = new Properties();
                    candidate.load(new StringReader(text));
                    // Headerless profiles are the explicitly supported original format.
                    if (!candidate.getProperty("version", "1").equals("1"))
                        return com.openggf.mods.state.ModSaveLoadResult.rejected(
                                com.openggf.mods.state.ModSaveLoadResult.Status.INCOMPATIBLE);
                    for (String key : candidate.stringPropertyNames()) {
                        boolean setting = java.util.Set.of(SettingsScreen.FAST_COMBAT, SettingsScreen.NO_SHAKE,
                                SettingsScreen.NO_MUSIC, SettingsScreen.NO_SFX).contains(key);
                        if (key.startsWith("wins.") || key.startsWith("runs.") || key.startsWith("floor.")
                                || key.equals("score") || setting) {
                            int value = Integer.parseInt(candidate.getProperty(key));
                            if (value < 0 || (setting && value > 1))
                                throw new IllegalArgumentException("Invalid profile field " + key);
                        }
                    }
                    return com.openggf.mods.state.ModSaveLoadResult.valid(candidate);
                } catch (IOException | IllegalArgumentException damaged) {
                    return com.openggf.mods.state.ModSaveLoadResult.rejected(status);
                }
            }
            public String encode(Properties value) {
                StringWriter out = new StringWriter();
                try { value.store(out, "Slay the Robotnik profile"); }
                catch (IOException impossible) { throw new IllegalStateException(impossible); }
                return out.toString();
            }
        });
    }

    static Profile load(SceneStorage storage) {
        Profile profile = new Profile(storage);
        var result = profile.slot.load();
        profile.loadStatus = result.status();
        result.value().ifPresent(profile.values::putAll);
        return profile;
    }

    public boolean save(SceneStorage storage) {
        // The retained owner handle is authoritative, even if a caller supplies another handle.
        if (!slot.writable()) return false;
        if (!dirty) return true;
        values.setProperty("version", "1");
        if (!slot.save(values).saved()) return false;
        dirty = false;
        loadStatus = com.openggf.mods.state.ModSaveLoadResult.Status.VALID;
        return true;
    }

    public boolean dirty() { return dirty; }
    public boolean writable() { return slot.writable(); }
    public com.openggf.mods.state.ModSaveLoadResult.Status loadStatus() { return loadStatus; }

    public int get(String key) {
        try {
            return Integer.parseInt(values.getProperty(key, "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public void set(String key, int value) {
        String text = Integer.toString(value);
        if (!text.equals(values.getProperty(key))) {
            values.setProperty(key, text);
            dirty = true;
        }
    }

    public void add(String key, int delta) {
        set(key, (int) Math.clamp((long) get(key) + delta, 0, Integer.MAX_VALUE));
    }

    public void max(String key, int value) {
        set(key, Math.max(get(key), value));
    }
}
