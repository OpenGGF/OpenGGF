package starpost.scene;

import com.openggf.mods.scene.SceneStorage;
import com.openggf.mods.state.ModSaveCodec;
import com.openggf.mods.state.ModSaveLoadResult;
import com.openggf.mods.state.ModSaveSlot;
import com.openggf.mods.state.ModSaveWriteResult;

/** Owner-local legacy options with checked immutable publication checkpoints. */
final class Settings {
    static final String FILE = "settings.txt";
    boolean music = true;
    int dayMinutes = 14;
    boolean stamina;
    private final ModSaveSlot<Value> slot;
    private String notice = "";
    private long noticeUntil;

    private record Value(boolean music, int dayMinutes, boolean stamina) {}

    private Settings(SceneStorage storage) {
        slot = new ModSaveSlot<>(storage, FILE, new ModSaveCodec<>() {
            public String encode(Value value) {
                // Keep the original unversioned three-line encoding.
                return "music=" + (value.music() ? 1 : 0) + "\nday=" + value.dayMinutes()
                        + "\nstamina=" + (value.stamina() ? 1 : 0) + "\n";
            }
            public ModSaveLoadResult<Value> decode(String text) {
                boolean music = true, stamina = false, recognized = false;
                int day = 14;
                for (String raw : text.split("\n")) {
                    String line = raw.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    String[] pair = line.split("=", 2);
                    if (pair.length != 2) return ModSaveLoadResult.rejected(ModSaveLoadResult.Status.CORRUPT);
                    String key = pair[0].trim(), value = pair[1].trim();
                    switch (key) {
                        case "music", "stamina" -> {
                            if (!value.equals("0") && !value.equals("1"))
                                return ModSaveLoadResult.rejected(ModSaveLoadResult.Status.CORRUPT);
                            if (key.equals("music")) music = value.equals("1"); else stamina = value.equals("1");
                            recognized = true;
                        }
                        case "day" -> {
                            if (!value.equals("14") && !value.equals("20") && !value.equals("28"))
                                return ModSaveLoadResult.rejected(ModSaveLoadResult.Status.CORRUPT);
                            day = Integer.parseInt(value); recognized = true;
                        }
                        default -> { return ModSaveLoadResult.rejected(ModSaveLoadResult.Status.INCOMPATIBLE); }
                    }
                }
                return recognized ? ModSaveLoadResult.valid(new Value(music, day, stamina))
                        : ModSaveLoadResult.rejected(ModSaveLoadResult.Status.CORRUPT);
            }
        });
        var loaded = slot.load();
        loaded.value().ifPresent(value -> {
            music = value.music(); dayMinutes = value.dayMinutes(); stamina = value.stamina();
        });
        if (!slot.writable()) notice = "OPTIONS PRESERVED - REPLACE TO SAVE";
    }

    static Settings load(SceneStorage storage) { return new Settings(storage); }

    boolean save() {
        return outcome(slot.save(new Value(music, dayMinutes, stamina)));
    }

    boolean flush() { return !slot.pending() || outcome(slot.retry()); }
    boolean replacementRequired() { return !slot.writable(); }

    void replaceStoredOptions() {
        slot.allowReplacement();
        save();
    }

    private boolean outcome(ModSaveWriteResult result) {
        notice = result.saved() ? "OPTIONS SAVED" : result == ModSaveWriteResult.REPLACEMENT_REQUIRED
                ? "OPTIONS PRESERVED - REPLACE TO SAVE" : "OPTIONS SAVE PENDING - RETRYING";
        noticeUntil = -1;
        return result.saved();
    }

    String notice(long ticks) {
        if (!notice.isEmpty() && slot.writable() && !slot.pending()) {
            if (noticeUntil < 0) noticeUntil = ticks + 120;
            if (ticks >= noticeUntil) return "";
        }
        return notice;
    }
}
