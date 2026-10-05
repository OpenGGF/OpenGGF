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

    static Profile load(SceneStorage storage) {
        Profile profile = new Profile();
        storage.read(FILE).ifPresent(text -> {
            try {
                profile.values.load(new StringReader(text));
            } catch (IOException | IllegalArgumentException ignored) {
                profile.values.clear();
            }
        });
        return profile;
    }

    public void save(SceneStorage storage) {
        StringWriter out = new StringWriter();
        try {
            values.store(out, "Slay the Robotnik profile");
        } catch (IOException impossible) {
            return;
        }
        storage.write(FILE, out.toString());
    }

    public int get(String key) {
        try {
            return Integer.parseInt(values.getProperty(key, "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public void set(String key, int value) {
        values.setProperty(key, Integer.toString(value));
    }

    public void add(String key, int delta) {
        set(key, get(key) + delta);
    }

    public void max(String key, int value) {
        set(key, Math.max(get(key), value));
    }
}
