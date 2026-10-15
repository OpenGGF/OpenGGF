package sitarhero.controls;

import sitarhero.model.LoadDisposition;

/** Two independently remappable ten-action profiles, with keyboard and pad alternatives. */
public final class ControlSettings {
    public static final int STRUM_UP = 5, STRUM_DOWN = 6, WHAMMY = 7, POWER = 8, PAUSE = 9;
    private final PhysicalBinding[][] keyboard = new PhysicalBinding[2][10];
    private final PhysicalBinding[][] gamepad = new PhysicalBinding[2][10];
    private LoadDisposition disposition = LoadDisposition.ABSENT;
    private int inputOffsetMs;
    private int displayOffsetMs;
    private boolean lefty;
    private boolean reducedFlashes;
    private int scrollSpeed = 100;
    private final int playerIndex;

    public ControlSettings() { this(0); }
    public ControlSettings(int playerIndex) {
        if (playerIndex < 0 || playerIndex > 1) throw new IllegalArgumentException("playerIndex");
        this.playerIndex = playerIndex;
        defaults();
    }
    public void defaults() {
        for (int profile = 0; profile < 2; profile++) {
            int[] keys = {65, 83, 68, 70, profile == 0 ? 71 : 32, 265, 264, 32, 340, 256};
            for (int i = 0; i < keys.length; i++) keyboard[profile][i] = new PhysicalBinding('K', -1, keys[i], 1);
            gamepad[profile][0] = new PhysicalBinding('A', 0, 4, 1);
            gamepad[profile][1] = new PhysicalBinding('B', 0, 4, 1);
            gamepad[profile][2] = new PhysicalBinding('B', 0, 5, 1);
            gamepad[profile][3] = new PhysicalBinding('A', 0, 5, 1);
            gamepad[profile][4] = new PhysicalBinding('B', 0, 0, 1);
            gamepad[profile][5] = new PhysicalBinding('B', 0, 11, 1);
            gamepad[profile][6] = new PhysicalBinding('B', 0, 13, 1);
            gamepad[profile][7] = new PhysicalBinding('A', 0, 3, -1);
            gamepad[profile][8] = new PhysicalBinding('B', 0, 6, 1);
            gamepad[profile][9] = new PhysicalBinding('B', 0, 7, 1);
            if (playerIndex == 1) {
                int[] secondKeys = {74, 75, 76, 59, profile == 0 ? 39 : 80, 91, 93, 344, 346, 256};
                for (int i = 0; i < 10; i++) {
                    keyboard[profile][i] = new PhysicalBinding('K', -1, secondKeys[i], 1);
                    PhysicalBinding old = gamepad[profile][i];
                    gamepad[profile][i] = new PhysicalBinding(old.kind(), 1, old.code(), old.direction());
                }
            }
        }
        inputOffsetMs = 0; displayOffsetMs = 0; lefty = false; reducedFlashes = false; scrollSpeed = 100;
    }
    public PhysicalBinding binding(boolean drums, boolean pad, int action) {
        return (pad ? gamepad : keyboard)[drums ? 1 : 0][action];
    }
    public void bind(boolean drums, boolean pad, int action, PhysicalBinding binding) {
        (pad ? gamepad : keyboard)[drums ? 1 : 0][action] = binding;
    }
    public int inputOffsetMs() { return inputOffsetMs; }
    public int displayOffsetMs() { return displayOffsetMs; }
    public void inputOffsetMs(int value) { inputOffsetMs = Math.max(-250, Math.min(250, value)); }
    public void displayOffsetMs(int value) { displayOffsetMs = Math.max(-250, Math.min(250, value)); }
    public boolean lefty() { return lefty; }
    public void lefty(boolean value) { lefty = value; }
    public boolean reducedFlashes() { return reducedFlashes; }
    public void reducedFlashes(boolean value) { reducedFlashes = value; }
    public int scrollSpeed() { return scrollSpeed; }
    public void scrollSpeed(int value) { scrollSpeed = Math.max(50, Math.min(150, value)); }
    public String encode() {
        StringBuilder text = new StringBuilder("sitar-settings=1\ninput=" + inputOffsetMs + "\ndisplay=" + displayOffsetMs + "\nlefty=" + lefty + "\n");
        text.append("flashes=").append(reducedFlashes).append("\nscroll=").append(scrollSpeed).append('\n');
        for (int p = 0; p < 2; p++) for (int a = 0; a < 10; a++) {
            text.append("k").append(p).append('.').append(a).append('=').append(keyboard[p][a].encode()).append('\n');
            text.append("p").append(p).append('.').append(a).append('=').append(gamepad[p][a].encode()).append('\n');
        }
        return text.toString();
    }
    /** V1 settings publish atomically; malformed/future files retain defaults and their original bytes. */
    public boolean read(String text) {
        disposition = LoadDisposition.rejected(text, "sitar-settings", "sitar-settings=1");
        if (text == null || text.length() > 65_536) return false;
        var lines = text.lines().limit(129).toList();
        if (lines.isEmpty() || lines.size() > 128 || !lines.getFirst().equals("sitar-settings=1")) return false;
        ControlSettings candidate = new ControlSettings(playerIndex);
        var keys = new java.util.HashSet<String>(); keys.add("sitar-settings");
        try {
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i);
                int separator = line.indexOf('=');
                if (separator <= 0 || line.length() > 512) return false;
                String key = line.substring(0, separator), value = line.substring(separator + 1);
                if (!keys.add(key)) return false;
                if (key.equals("input")) candidate.inputOffsetMs(Integer.parseInt(value));
                else if (key.equals("display")) candidate.displayOffsetMs(Integer.parseInt(value));
                else if (key.equals("lefty")) candidate.lefty(flag(value));
                else if (key.equals("flashes")) candidate.reducedFlashes(flag(value));
                else if (key.equals("scroll")) candidate.scrollSpeed(Integer.parseInt(value));
                else if (key.matches("[kp][01]\\.[0-9]")) {
                    int profile = key.charAt(1) - '0', action = key.charAt(3) - '0';
                    (key.charAt(0) == 'k' ? candidate.keyboard : candidate.gamepad)[profile][action] = PhysicalBinding.decode(value);
                }
                // Bounded unknown metadata is compatible with the current schema.
            }
        } catch (IllegalArgumentException malformed) { return false; }
        for (int profile = 0; profile < 2; profile++) {
            System.arraycopy(candidate.keyboard[profile], 0, keyboard[profile], 0, 10);
            System.arraycopy(candidate.gamepad[profile], 0, gamepad[profile], 0, 10);
        }
        inputOffsetMs = candidate.inputOffsetMs; displayOffsetMs = candidate.displayOffsetMs;
        lefty = candidate.lefty; reducedFlashes = candidate.reducedFlashes; scrollSpeed = candidate.scrollSpeed;
        disposition = LoadDisposition.VALID;
        return true;
    }
    public boolean saveEligible() { return disposition.saveEligible(); }
    public LoadDisposition loadDisposition() { return disposition; }
    private static boolean flag(String value) {
        if (!value.equals("true") && !value.equals("false")) throw new IllegalArgumentException("Boolean setting");
        return value.equals("true");
    }

    public String actionName(int action, boolean drums) {
        if (action < 5) return drums ? (action == 4 ? "KICK PEDAL" : "PAD " + (action + 1)) : "FRET " + (action + 1);
        return switch (action) {
            case 5 -> "STRUM UP"; case 6 -> "STRUM DOWN"; case 7 -> "WHAMMY";
            case 8 -> "STAR POWER"; case 9 -> "PAUSE"; default -> throw new IllegalArgumentException();
        };
    }
}
