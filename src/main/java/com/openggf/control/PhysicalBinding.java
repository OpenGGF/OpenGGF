package com.openggf.control;

import java.util.Map;
import java.util.Set;

/** A creator-owned binding: key, standard pad button, or signed pad axis. */
@com.openggf.game.ModApi
public record PhysicalBinding(char kind, int device, int code, int direction) {
    public PhysicalBinding {
        if (kind != 'K' && kind != 'B' && kind != 'A') throw new IllegalArgumentException("Binding kind");
        if (device < -1 || device > 15 || code < -1 || code > 65535) throw new IllegalArgumentException("Binding device/code");
        if (direction != -1 && direction != 1) throw new IllegalArgumentException("Binding direction");
    }
    public float amount(Set<Integer> keys, Map<String, Float> pads) {
        if (code < 0) return 0;
        if (kind == 'K') return keys.contains(code) ? 1 : 0;
        float value = pads.getOrDefault(device + ":" + kind + ":" + code,
                kind == 'A' && code >= 4 ? -1f : 0f);
        if (!Float.isFinite(value)) return 0;
        if (kind == 'B') return Math.clamp(value, 0f, 1f);
        return Math.max(0, Math.min(1, value * direction));
    }
    public String encode() { return kind + "," + device + "," + code + "," + direction; }
    public static PhysicalBinding decode(String value) {
        if (value == null) throw new IllegalArgumentException("Binding");
        String[] fields = value.split(",", -1);
        if (fields.length != 4 || fields[0].length() != 1) throw new IllegalArgumentException("Binding");
        return new PhysicalBinding(fields[0].charAt(0), Integer.parseInt(fields[1]),
                Integer.parseInt(fields[2]), Integer.parseInt(fields[3]));
    }
    /** Capture a rising key/button or a deliberate axis deflection; trigger rest is ignored. */
    public static PhysicalBinding capture(PhysicalInputEvent event, boolean gamepad, float axisThreshold) {
        java.util.Objects.requireNonNull(event, "event");
        if (!Float.isFinite(axisThreshold) || axisThreshold <= 0 || axisThreshold >= 1)
            throw new IllegalArgumentException("Axis capture threshold must be between zero and one");
        if (!gamepad && event.kind() == PhysicalInputEvent.Kind.KEY && event.pressed())
            return new PhysicalBinding('K', -1, event.code(), 1);
        if (gamepad && event.kind() == PhysicalInputEvent.Kind.BUTTON && event.pressed())
            return new PhysicalBinding('B', event.deviceId(), event.code(), 1);
        if (gamepad && event.kind() == PhysicalInputEvent.Kind.AXIS && Math.abs(event.value()) > axisThreshold
                && !(event.code() >= 4 && event.value() < 0))
            return new PhysicalBinding('A', event.deviceId(), event.code(), event.value() < 0 ? -1 : 1);
        return null;
    }
    public String label() {
        if (code < 0) return "UNBOUND";
        if (kind == 'K') {
            if (code >= 65 && code <= 90 || code >= 48 && code <= 57) return Character.toString((char) code);
            return switch (code) {
                case 32 -> "SPACE"; case 256 -> "ESC"; case 257 -> "ENTER";
                case 262 -> "RIGHT"; case 263 -> "LEFT"; case 264 -> "DOWN"; case 265 -> "UP";
                case 340 -> "L SHIFT"; default -> "KEY " + code;
            };
        }
        if (kind == 'A') return "PAD " + device + " AXIS " + code + (direction < 0 ? "-" : "+");
        String name = switch (code) {
            case 0 -> "A"; case 1 -> "B"; case 2 -> "X"; case 3 -> "Y";
            case 4 -> "LB"; case 5 -> "RB"; case 6 -> "BACK"; case 7 -> "START";
            case 11 -> "UP"; case 12 -> "RIGHT"; case 13 -> "DOWN"; case 14 -> "LEFT";
            default -> "BUTTON " + code;
        };
        return "PAD " + device + " " + name;
    }
}
