package sonicbros.ui;

import com.openggf.mods.scene.SceneStorage;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lifetime records, saved as {@code key=value} lines in the mod's storage
 * ({@code saves/mods/super-sonic-bros/records.txt}): matches played, and per fighter how often
 * they were picked and won, KOs, falls and damage dealt.
 */
public final class Records {
    public static final String FILE = "records.txt";
    private final Map<String, Long> values = new LinkedHashMap<>();

    public long get(String key) { return values.getOrDefault(key, 0L); }

    public void add(String key, long amount) { values.merge(key, amount, Long::sum); }

    public void max(String key, long value) { if (value > get(key)) values.put(key, value); }

    public void load(SceneStorage storage) {
        try {
            String text = storage.read(FILE).orElse(null);
            if (text == null) return;
            for (String line : text.split("\n")) {
                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                try {
                    values.put(line.substring(0, eq).trim(), Long.parseLong(line.substring(eq + 1).trim()));
                } catch (NumberFormatException ignored) {
                    // Skip a damaged line rather than losing the rest.
                }
            }
        } catch (RuntimeException missing) {
            // No records yet.
        }
    }

    public void save(SceneStorage storage) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Long> e : values.entrySet()) sb.append(e.getKey()).append('=').append(e.getValue()).append('\n');
        try {
            storage.write(FILE, sb.toString());
        } catch (RuntimeException ignored) {
            // Records are a nicety; a failed write must not end the game.
        }
    }
}
