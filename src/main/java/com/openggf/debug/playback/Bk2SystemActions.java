package com.openggf.debug.playback;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Engine-internal system-action view; the public BK2 pad records stay unchanged. */
public final class Bk2SystemActions {
    private Bk2SystemActions() { }

    public static Bk2SystemAction at(Bk2Movie movie, int frame) {
        return Objects.requireNonNull(movie, "movie").systemAction(frame);
    }

    /** Uses the real movie cursor, never a comparator's applied-pad-row offset. */
    public static Bk2SystemAction current(PlaybackDebugManager playback) {
        return playback.currentSystemAction();
    }

    /** A reset boundary consumes one row and clears pad-edge carry from the retired world. */
    public static void completeReset(PlaybackDebugManager playback) {
        playback.onLevelFrameAdvanced();
        playback.clearLastAppliedState();
    }

    public static boolean playing(PlaybackDebugManager playback) {
        return playback.isSystemActionPlaybackRunning();
    }

    public static Bk2Movie movie(PlaybackDebugManager playback) {
        return playback.systemActionMovie();
    }

    /** Timeline identity survives pause, but changes when the same movie is restarted. */
    public static Object sessionIdentity(PlaybackDebugManager playback) {
        return playback.systemActionSessionIdentity();
    }

    static List<Bk2SystemAction> parse(String logKey, List<Bk2FrameInput> frames) {
        List<String> keys = fields(logKey);
        List<List<String>> groups = new ArrayList<>();
        List<String> group = null;
        boolean hashed = keys.stream().anyMatch(key -> key.trim().startsWith("#"));
        if (hashed) {
            for (String key : keys) {
                String token = key.trim();
                if (token.isEmpty()) continue;
                if (token.startsWith("#") || group == null) {
                    group = new ArrayList<>();
                    groups.add(group);
                }
                group.add(token.replaceFirst("^#", ""));
            }
        } else {
            // Direct named fields, with or without the optional outer separators.
            for (String key : keys) if (!key.isBlank()) groups.add(List.of(key));
        }
        List<Binding> bindings = new ArrayList<>();
        for (int field = 0; field < groups.size(); field++) {
            List<String> tokens = groups.get(field);
            for (int ch = 0; ch < tokens.size(); ch++) {
                String token = tokens.get(ch).trim().toLowerCase(Locale.ROOT);
                Bk2SystemAction action = switch (token) {
                    case "power" -> Bk2SystemAction.POWER;
                    case "reset" -> Bk2SystemAction.RESET;
                    default -> Bk2SystemAction.NONE;
                };
                if (action != Bk2SystemAction.NONE) bindings.add(new Binding(field, ch, action));
            }
        }
        List<Bk2SystemAction> actions = new ArrayList<>(frames.size());
        for (Bk2FrameInput frame : frames) {
            Bk2SystemAction action = Bk2SystemAction.NONE;
            if (!bindings.isEmpty()) {
                List<String> row = fields(Objects.requireNonNull(frame.rawLine(), "system-action row"));
                if (!row.isEmpty() && row.getFirst().isEmpty()) row.removeFirst();
                if (!row.isEmpty() && row.getLast().isEmpty()) row.removeLast();
                if (row.size() != groups.size()) {
                    throw new IllegalArgumentException("BK2 system-action field count at row " + frame.frameIndex());
                }
                for (Binding binding : bindings) {
                    String value = row.get(binding.field());
                    if (binding.character() >= value.length()) {
                        throw new IllegalArgumentException("Truncated BK2 system-action field at row " + frame.frameIndex());
                    }
                    char held = value.charAt(binding.character());
                    if (held != '.' && held != ' ' && held != '0') {
                        // A cold boot dominates when both requests are present on one row.
                        if (binding.action() == Bk2SystemAction.POWER || action == Bk2SystemAction.NONE) {
                            action = binding.action();
                        }
                    }
                }
            }
            actions.add(action);
        }
        return List.copyOf(actions);
    }

    private static List<String> fields(String line) {
        return new ArrayList<>(List.of(line.split("\\|", -1)));
    }

    private record Binding(int field, int character, Bk2SystemAction action) { }
}
