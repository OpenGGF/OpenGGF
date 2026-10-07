package sitarhero.controls;

/** Two independently remappable ten-action profiles, with keyboard and pad alternatives. */
public final class ControlSettings {
    public static final int STRUM_UP = 5, STRUM_DOWN = 6, WHAMMY = 7, POWER = 8, PAUSE = 9;
    private final PhysicalBinding[][] keyboard = new PhysicalBinding[2][10];
    private final PhysicalBinding[][] gamepad = new PhysicalBinding[2][10];
    private int inputOffsetMs;
    private int displayOffsetMs;
    private boolean lefty;

    public ControlSettings() { defaults(); }
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
        }
        inputOffsetMs = 0; displayOffsetMs = 0; lefty = false;
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
    public String encode() {
        StringBuilder text = new StringBuilder("sitar-settings=1\ninput=" + inputOffsetMs + "\ndisplay=" + displayOffsetMs + "\nlefty=" + lefty + "\n");
        for (int p = 0; p < 2; p++) for (int a = 0; a < 10; a++) {
            text.append("k").append(p).append('.').append(a).append('=').append(keyboard[p][a].encode()).append('\n');
            text.append("p").append(p).append('.').append(a).append('=').append(gamepad[p][a].encode()).append('\n');
        }
        return text.toString();
    }
    public void read(String text) {
        for (String line : text.split("\n")) {
            int separator = line.indexOf('='); if (separator < 0) continue;
            String key = line.substring(0, separator), value = line.substring(separator + 1);
            try {
                if (key.equals("input")) inputOffsetMs(Integer.parseInt(value));
                else if (key.equals("display")) displayOffsetMs(Integer.parseInt(value));
                else if (key.equals("lefty")) lefty(Boolean.parseBoolean(value));
                else if (key.matches("[kp][01]\\.[0-9]")) {
                    int profile = key.charAt(1) - '0', action = key.charAt(3) - '0';
                    (key.charAt(0) == 'k' ? keyboard : gamepad)[profile][action] = PhysicalBinding.decode(value);
                }
            } catch (IllegalArgumentException ignored) { /* retain defaults for a malformed setting */ }
        }
    }
    public String actionName(int action, boolean drums) {
        if (action < 5) return drums ? (action == 4 ? "KICK PEDAL" : "PAD " + (action + 1)) : "FRET " + (action + 1);
        return switch (action) {
            case 5 -> "STRUM UP"; case 6 -> "STRUM DOWN"; case 7 -> "WHAMMY";
            case 8 -> "STAR POWER"; case 9 -> "PAUSE"; default -> throw new IllegalArgumentException();
        };
    }
}
