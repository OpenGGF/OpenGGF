package starpost.scene;

import com.openggf.mods.scene.SceneStorage;

/**
 * The player's options, kept apart from the save: music on or off, the day's length in real
 * minutes, and Momentum or classic stamina (the kickoff's fallback switch).
 */
final class Settings {
    static final String FILE = "settings.txt";
    boolean music = true;
    int dayMinutes = 14;
    boolean stamina;

    static Settings load(SceneStorage storage) {
        Settings s = new Settings();
        storage.read(FILE).ifPresent(text -> {
            for (String line : text.split("\n")) {
                String[] kv = line.split("=", 2);
                if (kv.length < 2) {
                    continue;
                }
                switch (kv[0].trim()) {
                    case "music" -> s.music = !kv[1].trim().equals("0");
                    case "day" -> {
                        int m = parse(kv[1]);
                        s.dayMinutes = m == 20 || m == 28 ? m : 14;
                    }
                    case "stamina" -> s.stamina = kv[1].trim().equals("1");
                    default -> {
                    }
                }
            }
        });
        return s;
    }

    void save(SceneStorage storage) {
        storage.write(FILE, "music=" + (music ? 1 : 0) + "\nday=" + dayMinutes + "\nstamina=" + (stamina ? 1 : 0) + "\n");
    }

    private static int parse(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return 14;
        }
    }
}
