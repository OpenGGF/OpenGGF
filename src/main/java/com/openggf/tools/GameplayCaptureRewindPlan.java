package com.openggf.tools;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * A capture's live rewind direction: the windows in which the rewind key is held, each with the
 * speed modifier held alongside it, and the rewind budget settings the capture pins for itself.
 * Unset budget values read the configuration, as live play does.
 *
 * @param windows           non-overlapping held windows, in frame order
 * @param scope             {@code rewind.budgetScope} (off, checkpoint, act, zone, never), or null
 * @param seconds           {@code rewind.budgetSeconds} (a positive count or unlimited), or null
 * @param refillOnLifeLoss  {@code rewind.refillOnLifeLoss}, or null
 * @param bonusStages       {@code rewind.bonusStages}, or null
 * @param specialStages     {@code rewind.specialStages}, or null
 */
public record GameplayCaptureRewindPlan(List<Window> windows, String scope, String seconds,
                                        Boolean refillOnLifeLoss, Boolean bonusStages, Boolean specialStages) {
    /** The held speed: the configured half- or double-speed modifier is held with the rewind key. */
    public enum Speed { HALF, NORMAL, DOUBLE }

    /** Holds the rewind key for {@code length} frames from capture frame {@code from}. */
    public record Window(int from, int length, Speed speed) {
        public Window {
            if (from < 0 || length <= 0) throw new IllegalArgumentException("--rewind needs a frame >= 0 and a positive length");
            if (speed == null) speed = Speed.NORMAL;
        }

        boolean contains(int frame) { return frame >= from && frame < from + length; }
    }

    public static final GameplayCaptureRewindPlan NONE = new GameplayCaptureRewindPlan(List.of(), null, null, null, null, null);

    public GameplayCaptureRewindPlan {
        List<Window> sorted = new ArrayList<>(windows == null ? List.of() : windows);
        sorted.sort(Comparator.comparingInt(Window::from));
        for (int i = 1; i < sorted.size(); i++) {
            Window previous = sorted.get(i - 1);
            if (sorted.get(i).from() < previous.from() + previous.length())
                throw new IllegalArgumentException("--rewind windows overlap at frame " + sorted.get(i).from());
        }
        windows = List.copyOf(sorted);
    }

    /** The existing single normal-speed window, or none when {@code from} is negative. */
    static GameplayCaptureRewindPlan single(int from, int length) {
        return from < 0 ? NONE : new GameplayCaptureRewindPlan(List.of(new Window(from, length, Speed.NORMAL)),
                null, null, null, null, null);
    }

    /** True when the capture records rewind history: any held window or pinned budget setting. */
    public boolean active() {
        return !windows.isEmpty() || scope != null || seconds != null || refillOnLifeLoss != null
                || bonusStages != null || specialStages != null;
    }

    /** The speed held at {@code frame}, or null when the rewind key is up. */
    public Speed heldAt(int frame) {
        for (Window window : windows) if (window.contains(frame)) return window.speed();
        return null;
    }

    /** Parses {@code FRAME:LENGTH} or {@code FRAME:LENGTH:half|normal|double}. */
    static Window parseWindow(String raw, String flag) {
        String[] parts = raw.split(":", -1);
        if (parts.length != 2 && parts.length != 3)
            throw new IllegalArgumentException(flag + " takes FRAME:LENGTH or FRAME:LENGTH:half|normal|double");
        Speed speed = parts.length == 2 ? Speed.NORMAL : switch (parts[2].trim().toLowerCase(Locale.ROOT)) {
            case "half" -> Speed.HALF;
            case "normal" -> Speed.NORMAL;
            case "double" -> Speed.DOUBLE;
            default -> throw new IllegalArgumentException(flag + " speed must be half, normal or double");
        };
        return new Window(GameplayCaptureTool.Arguments.number(parts[0], flag),
                GameplayCaptureTool.Arguments.number(parts[1], flag), speed);
    }

    static String parseScope(String raw, String flag) {
        String value = raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "off", "checkpoint", "act", "zone", "never" -> value;
            default -> throw new IllegalArgumentException(flag + " must be off, checkpoint, act, zone or never");
        };
    }

    static String parseSeconds(String raw, String flag) {
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.equals("unlimited")) return value;
        try {
            if (Integer.parseInt(value) > 0) return value;
        } catch (NumberFormatException ignored) {
            // Falls through to the message below.
        }
        throw new IllegalArgumentException(flag + " takes a positive number of seconds or unlimited");
    }

    static boolean parseBoolean(String raw, String flag) {
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "true", "on", "yes" -> true;
            case "false", "off", "no" -> false;
            default -> throw new IllegalArgumentException(flag + " takes true or false");
        };
    }
}
