package sonicbros.input;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PhysicalGamepad;
import com.openggf.control.PhysicalInput;
import com.openggf.control.PlayerInputState;
import com.openggf.mods.scene.SceneContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import sonicbros.sim.Pad;

/**
 * Turns the keyboard and every connected gamepad into separate controllers, each producing a
 * {@link Pad}. Two people can share the keyboard:
 *
 * <ul>
 *   <li><b>Keyboard (arrows)</b>: arrows move; Z attack, X special, C or Space jump, V shield,
 *       Enter start. The keys right above the arrows work too: comma, full stop, slash and
 *       right Shift.</li>
 *   <li><b>Keyboard (WASD)</b>: WASD move; F attack, G special, H jump, R shield, Q start.</li>
 *   <li><b>Each gamepad</b>: d-pad or left stick; X/Square attack, B/Circle special, A/Cross or
 *       Y/Triangle jump, any shoulder or trigger shield, Start.</li>
 * </ul>
 *
 * When the host supplies no physical input (headless tests) the engine's two logical players
 * stand in: A attack, B special, C jump.
 */
public final class Controls {
    public static final String KEYS_ARROWS = "KEYS1";
    public static final String KEYS_WASD = "KEYS2";
    public static final String LOGICAL_1 = "LOG1";
    public static final String LOGICAL_2 = "LOG2";

    // GLFW key codes (the same numbers SceneKeys uses).
    private static final int K_SPACE = 32;
    private static final int K_COMMA = 44;
    private static final int K_PERIOD = 46;
    private static final int K_SLASH = 47;
    private static final int K_ENTER = 257;
    private static final int K_KP_ENTER = 335;
    private static final int K_RIGHT = 262;
    private static final int K_LEFT = 263;
    private static final int K_DOWN = 264;
    private static final int K_UP = 265;
    private static final int K_RSHIFT = 344;
    private static final int K_BACKSPACE = 259;

    private final Map<String, Integer> held = new LinkedHashMap<>();
    private final Map<String, Integer> previous = new HashMap<>();
    private final Map<String, int[]> repeat = new HashMap<>();
    private final List<String> devices = new ArrayList<>();
    private boolean physical;

    /** Reads this tick's input. Call once per update before asking for pads. */
    public void update(SceneContext ctx) {
        previous.clear();
        previous.putAll(held);
        held.clear();
        devices.clear();
        PhysicalInput in = ctx.physicalInput();
        physical = in != null && (in.timestampNanos() != 0 || !in.keysDown().isEmpty() || !in.gamepads().isEmpty());
        if (physical) {
            held.put(KEYS_ARROWS, arrows(in));
            held.put(KEYS_WASD, wasd(in));
            devices.add(KEYS_ARROWS);
            devices.add(KEYS_WASD);
            for (PhysicalGamepad g : in.gamepads()) {
                String id = "PAD" + g.deviceId();
                held.put(id, gamepad(g));
                devices.add(id);
            }
        } else {
            LogicalInputSnapshot li = ctx.input();
            held.put(LOGICAL_1, logical(li == null ? null : li.player1()));
            held.put(LOGICAL_2, logical(li == null ? null : li.player2()));
            devices.add(LOGICAL_1);
            devices.add(LOGICAL_2);
        }
        for (String d : devices) {
            int[] r = repeat.computeIfAbsent(d, k -> new int[4]);
            int h = held.get(d);
            int[] bits = {Pad.UP, Pad.DOWN, Pad.LEFT, Pad.RIGHT};
            for (int i = 0; i < 4; i++) r[i] = (h & bits[i]) != 0 ? r[i] + 1 : 0;
        }
    }

    private static int arrows(PhysicalInput in) {
        int b = 0;
        if (in.keyDown(K_LEFT)) b |= Pad.LEFT;
        if (in.keyDown(K_RIGHT)) b |= Pad.RIGHT;
        if (in.keyDown(K_UP)) b |= Pad.UP;
        if (in.keyDown(K_DOWN)) b |= Pad.DOWN;
        if (in.keyDown('Z') || in.keyDown(K_COMMA)) b |= Pad.ATTACK;
        if (in.keyDown('X') || in.keyDown(K_PERIOD)) b |= Pad.SPECIAL;
        if (in.keyDown('C') || in.keyDown(K_SPACE) || in.keyDown(K_SLASH)) b |= Pad.JUMP;
        if (in.keyDown('V') || in.keyDown(K_RSHIFT)) b |= Pad.SHIELD;
        if (in.keyDown(K_ENTER) || in.keyDown(K_KP_ENTER) || in.keyDown(K_BACKSPACE)) b |= Pad.START;
        return b;
    }

    private static int wasd(PhysicalInput in) {
        int b = 0;
        if (in.keyDown('A')) b |= Pad.LEFT;
        if (in.keyDown('D')) b |= Pad.RIGHT;
        if (in.keyDown('W')) b |= Pad.UP;
        if (in.keyDown('S')) b |= Pad.DOWN;
        if (in.keyDown('F')) b |= Pad.ATTACK;
        if (in.keyDown('G')) b |= Pad.SPECIAL;
        if (in.keyDown('H')) b |= Pad.JUMP;
        if (in.keyDown('R')) b |= Pad.SHIELD;
        if (in.keyDown('Q')) b |= Pad.START;
        return b;
    }

    private static int gamepad(PhysicalGamepad g) {
        int b = 0;
        float ax = g.axis(PhysicalGamepad.AXIS_LEFT_X);
        float ay = g.axis(PhysicalGamepad.AXIS_LEFT_Y);
        if (g.buttonDown(PhysicalGamepad.BUTTON_DPAD_LEFT) || ax < -0.45f) b |= Pad.LEFT;
        if (g.buttonDown(PhysicalGamepad.BUTTON_DPAD_RIGHT) || ax > 0.45f) b |= Pad.RIGHT;
        if (g.buttonDown(PhysicalGamepad.BUTTON_DPAD_UP) || ay < -0.5f) b |= Pad.UP;
        if (g.buttonDown(PhysicalGamepad.BUTTON_DPAD_DOWN) || ay > 0.5f) b |= Pad.DOWN;
        if (g.buttonDown(PhysicalGamepad.BUTTON_X)) b |= Pad.ATTACK;
        if (g.buttonDown(PhysicalGamepad.BUTTON_B)) b |= Pad.SPECIAL;
        if (g.buttonDown(PhysicalGamepad.BUTTON_A) || g.buttonDown(PhysicalGamepad.BUTTON_Y)) b |= Pad.JUMP;
        if (g.buttonDown(PhysicalGamepad.BUTTON_LEFT_BUMPER) || g.buttonDown(PhysicalGamepad.BUTTON_RIGHT_BUMPER)
                || g.axis(PhysicalGamepad.AXIS_LEFT_TRIGGER) > 0.4f || g.axis(PhysicalGamepad.AXIS_RIGHT_TRIGGER) > 0.4f) {
            b |= Pad.SHIELD;
        }
        if (g.buttonDown(PhysicalGamepad.BUTTON_START)) b |= Pad.START;
        return b;
    }

    private static int logical(PlayerInputState s) {
        if (s == null) return 0;
        int b = 0;
        int h = s.heldMask();
        if ((h & 0x01) != 0) b |= Pad.UP;
        if ((h & 0x02) != 0) b |= Pad.DOWN;
        if ((h & 0x04) != 0) b |= Pad.LEFT;
        if ((h & 0x08) != 0) b |= Pad.RIGHT;
        int a = s.actionHeldMask();
        if ((a & 0x01) != 0) b |= Pad.ATTACK;
        if ((a & 0x02) != 0) b |= Pad.SPECIAL;
        if ((a & 0x04) != 0) b |= Pad.JUMP;
        if (s.startHeld()) b |= Pad.START;
        return b;
    }

    /** The devices present this tick, keyboard first. */
    public List<String> devices() { return devices; }

    public boolean physical() { return physical; }

    /** The pad for a device this tick (neutral when it disappeared). */
    public Pad pad(String device) {
        int now = held.getOrDefault(device, 0);
        int before = previous.getOrDefault(device, 0);
        return new Pad(now, now & ~before);
    }

    /** True on the tick {@code bits} went down on {@code device}. */
    public boolean pressed(String device, int bits) { return pad(device).hit(bits); }

    /** Like {@link #pressed} for directions, but repeating while held, as menus scroll. */
    public boolean repeated(String device, int direction) {
        if (pressed(device, direction)) return true;
        int[] r = repeat.get(device);
        if (r == null) return false;
        int i = direction == Pad.UP ? 0 : direction == Pad.DOWN ? 1 : direction == Pad.LEFT ? 2 : 3;
        return r[i] > 22 && (r[i] - 22) % 5 == 0;
    }

    /** Any device: true when {@code bits} went down on it. */
    public boolean anyPressed(int bits) {
        for (String d : devices) if (pressed(d, bits)) return true;
        return false;
    }

    /** Any device: a repeating direction. */
    public boolean anyRepeated(int direction) {
        for (String d : devices) if (repeated(d, direction)) return true;
        return false;
    }

    /** Confirm on any device: attack, jump or start. */
    public boolean anyAccept() { return anyPressed(Pad.ATTACK | Pad.JUMP | Pad.START); }

    /** Back on any device: special. */
    public boolean anyBack() { return anyPressed(Pad.SPECIAL); }

    /** The first device that pressed {@code bits} this tick, or null. */
    public String whoPressed(int bits) {
        for (String d : devices) if (pressed(d, bits)) return d;
        return null;
    }

    /** A short human name for a device. */
    public static String deviceName(String device) {
        if (device == null) return "";
        return switch (device) {
            case KEYS_ARROWS -> "KEYS (ARROWS)";
            case KEYS_WASD -> "KEYS (WASD)";
            case LOGICAL_1 -> "PAD 1";
            case LOGICAL_2 -> "PAD 2";
            default -> "GAMEPAD " + (device.startsWith("PAD") ? (Integer.parseInt(device.substring(3)) + 1) : "");
        };
    }
}
