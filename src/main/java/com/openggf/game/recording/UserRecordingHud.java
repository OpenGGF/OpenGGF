package com.openggf.game.recording;

import com.openggf.game.presentation.TransportHud;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.PixelFontTextRenderer;

public final class UserRecordingHud {
    public static final String HOLD_PROMPT_TEXT = "Hold Shift+Record for 1 Sec to Begin Recording";

    private UserRecordingHud() {
    }

    public static UserRecordingHudState holdPromptState(int heldFrames, int requiredFrames) {
        int safeRequired = Math.max(1, requiredFrames);
        int safeHeld = Math.max(0, Math.min(heldFrames, safeRequired));
        return new UserRecordingHudState(
                true,
                HOLD_PROMPT_TEXT,
                "Hold " + safeHeld + "/" + safeRequired,
                safeHeld,
                false,
                true);
    }

    public static void render(PixelFontTextRenderer text, GraphicsManager graphics, UserRecordingHudState state) {
        if (text == null || state == null || !state.visible()) return;
        int width = graphics == null ? 320 : graphics.getProjectionWidth();
        TransportHud.render(text, graphics, width, TransportHud.Placement.BOTTOM_RIGHT, message(state));
    }

    static TransportHud.Message message(UserRecordingHudState state) {
        String primary = state.primaryText() == null ? "" : state.primaryText();
        String secondary = state.secondaryText() == null ? "" : state.secondaryText();
        // Interpret the existing display snapshot only; recording/playback clocks and policy stay with their owners.
        if (HOLD_PROMPT_TEXT.equals(primary)) {
            int progress = holdProgress(secondary);
            return new TransportHud.Message(TransportHud.Status.HOLD, "HOLD TO REC", "SHIFT+RECORD",
                    progress + "% OF 1 SEC", progress);
        }
        if (primary.startsWith("REC frame ")) {
            return new TransportHud.Message(TransportHud.Status.RECORD, "RECORDING", frameLabel(state.frame()), secondary, -1);
        }
        if (primary.startsWith("PLAYBACK ")) {
            boolean fast = primary.startsWith("PLAYBACK FF ");
            String frames = primary.substring(fast ? "PLAYBACK FF ".length() : "PLAYBACK ".length());
            String detail = frames + " FR";
            if (detail.length() > 14 && frames.contains("/")) {
                String[] counts = frames.split("/", 2);
                detail = "F " + counts[0] + "\nOF " + counts[1];
            }
            return new TransportHud.Message(state.amberWarning() ? TransportHud.Status.PLAY_WARNING : TransportHud.Status.PLAY,
                    fast ? "PLAYBACK FF" : "PLAYBACK", detail, verificationLabel(secondary), -1);
        }
        return new TransportHud.Message(state.redWarning() ? TransportHud.Status.ERROR
                : state.amberWarning() ? TransportHud.Status.WARNING : TransportHud.Status.STOP,
                primary, secondary, frameLabel(state.frame()), -1);
    }

    private static String frameLabel(int frame) {
        String label = "FRAME " + frame;
        return label.length() <= 14 ? label : "F " + frame;
    }

    private static String verificationLabel(String secondary) {
        return switch (secondary) {
            case "VERIFY clean" -> "VERIFIED";
            case "VERIFY missing-sidecar" -> "NO SIDECAR";
            case "VERIFY schema-unsupported" -> "SCHEMA UNSUPP.";
            case "VERIFY truncated-sidecar" -> "SIDECAR SHORT";
            default -> {
                String prefix = "VERIFY first-mismatch(frame=";
                int end = secondary.indexOf(',');
                yield secondary.startsWith(prefix) && end > prefix.length()
                        ? "DESYNC F " + secondary.substring(prefix.length(), end) : secondary;
            }
        };
    }

    private static int holdProgress(String secondary) {
        // holdPromptState supplies "Hold held/required"; no transport state is inferred from wall time.
        try {
            String[] counts = secondary.substring("Hold ".length()).split("/");
            long held = Long.parseLong(counts[0]);
            long required = Long.parseLong(counts[1]);
            return required <= 0 ? 0 : (int) Math.clamp(held * 100 / required, 0, 100);
        } catch (IndexOutOfBoundsException | NumberFormatException malformed) {
            return 0;
        }
    }
}
