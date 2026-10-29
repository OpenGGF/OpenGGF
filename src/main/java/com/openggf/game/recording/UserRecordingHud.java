package com.openggf.game.recording;

import com.openggf.game.presentation.TransportHud;
import com.openggf.configuration.GlfwKeyNameResolver;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.PixelFontTextRenderer;

public final class UserRecordingHud {
    public static final String HOLD_PROMPT_TEXT = "Hold Shift+Record for 1 Sec to Begin Recording";

    private UserRecordingHud() {
    }

    public static UserRecordingHudState holdPromptState(int heldFrames, int requiredFrames) {
        return holdPromptState(heldFrames, requiredFrames, org.lwjgl.glfw.GLFW.GLFW_KEY_F9);
    }

    public static UserRecordingHudState holdPromptState(int heldFrames, int requiredFrames, int recordKey) {
        int safeRequired = Math.max(1, requiredFrames);
        int safeHeld = Math.max(0, Math.min(heldFrames, safeRequired));
        return new UserRecordingHudState(
                true,
                "REC: HOLD SHIFT+" + keyLabel(recordKey),
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
        if (primary.startsWith("REC: HOLD SHIFT+") || HOLD_PROMPT_TEXT.equals(primary)) {
            int progress = holdProgress(secondary);
            return new TransportHud.Message(TransportHud.Status.HOLD, "REC:",
                    primary.startsWith("REC: ") ? primary.substring(5) : "HOLD SHIFT+F9", "", progress);
        }
        if (primary.startsWith("REC frame ")) {
            // The output filename is routine detail; it stays off the gameplay plate.
            return new TransportHud.Message(TransportHud.Status.RECORD, "REC", frameLabel(state.frame()), "", -1);
        }
        if (primary.startsWith("PLAYBACK ")) {
            boolean fast = primary.startsWith("PLAYBACK FF ");
            String frames = primary.substring(fast ? "PLAYBACK FF ".length() : "PLAYBACK ".length());
            String verification = verificationLabel(secondary);
            if (verification.startsWith("DESYNC F:")) {
                return new TransportHud.Message(TransportHud.Status.PLAY_WARNING, "DESYNC",
                        verification.substring("DESYNC ".length()), "Tab: Details", -1);
            }
            return new TransportHud.Message(state.amberWarning() ? TransportHud.Status.PLAY_WARNING : TransportHud.Status.PLAY,
                    fast ? "FF" : "PLAY", frames, verification.isEmpty() ? "Tab: Details" : verification + "  Tab: Details", -1);
        }
        String title = switch (primary) {
            case "REC ERROR" -> "ERR";
            case "REC STOPPED" -> "STOP";
            default -> primary;
        };
        return new TransportHud.Message(state.redWarning() ? TransportHud.Status.ERROR
                : state.amberWarning() ? TransportHud.Status.WARNING : TransportHud.Status.STOP,
                title, frameLabel(state.frame()), secondary, -1);
    }

    private static String keyLabel(int key) {
        return GlfwKeyNameResolver.nameOf(key).replace("LEFT_", "L").replace("RIGHT_", "R")
                .replace("PRINT_SCREEN", "PRTSC").replace("SCROLL_LOCK", "SCRLK").replace('_', ' ');
    }

    private static String frameLabel(int frame) {
        return "F:" + frame;
    }

    private static String verificationLabel(String secondary) {
        return switch (secondary) {
            case "VERIFY clean", "" -> "";
            case "VERIFY missing-sidecar" -> "NO SIDECAR";
            case "VERIFY schema-unsupported" -> "BAD SCHEMA";
            case "VERIFY truncated-sidecar" -> "SIDECAR SHORT";
            default -> {
                String prefix = "VERIFY first-mismatch(frame=";
                int end = secondary.indexOf(',');
                yield secondary.startsWith(prefix) && end > prefix.length()
                        ? "DESYNC F:" + secondary.substring(prefix.length(), end) : secondary;
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
