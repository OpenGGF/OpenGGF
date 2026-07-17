package com.openggf.tools.fbzvisual;

import com.openggf.game.GameMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestFbzVisualGameplayAdvanceContract {

    @TempDir
    Path tempDir;

    @Test
    void titleCardAndNoOpStepsAreRejected() {
        IllegalStateException titleCard = assertThrows(IllegalStateException.class,
                () -> HiddenGlCaptureSession.verifyGameplayFrameAdvance(
                        GameMode.TITLE_CARD, 0, GameMode.TITLE_CARD, 0));
        assertTrue(titleCard.getMessage().contains("LEVEL"));

        IllegalStateException noOp = assertThrows(IllegalStateException.class,
                () -> HiddenGlCaptureSession.verifyGameplayFrameAdvance(
                        GameMode.LEVEL, 0, GameMode.LEVEL, 0));
        assertTrue(noOp.getMessage().contains("exactly one"));

        assertDoesNotThrow(() -> HiddenGlCaptureSession.verifyGameplayFrameAdvance(
                GameMode.LEVEL, 0, GameMode.LEVEL, 1));
    }

    @Test
    void nativeStartUsesReviewedAmendmentInsteadOfAssumingFrameOneTimers() throws Exception {
        Map<String, Object> pre = nativeStartState(0, 0);
        Map<String, Object> post = nativeStartState(37, 5);
        post.put("camera_x_copy", 0x1234);
        FbzVisualEvidenceAmendment amendment = amendment("approved", 37, 0x1234);

        assertDoesNotThrow(() -> FbzVisualGameplayAdvanceVerifier.verify(
                "fbz1-start-outdoor", 1, pre, post, amendment));

        Map<String, Object> noOp = nativeStartState(0, 0);
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> FbzVisualGameplayAdvanceVerifier.verify(
                        "fbz1-start-outdoor", 1, pre, noOp, amendment));
        assertTrue(failure.getMessage().contains("level_frame_counter"));

        FbzVisualEvidenceAmendment pending = amendment(
                "awaiting-fresh-independent-review", null, null);
        assertThrows(IllegalStateException.class,
                () -> FbzVisualGameplayAdvanceVerifier.verify(
                        "fbz1-start-outdoor", 1, pre, post, pending));
    }

    @Test
    void visibilityGateRejectsTitleFadeAndOverlayContamination() {
        Map<String, Object> visible = new LinkedHashMap<>();
        visible.put("game_mode", "LEVEL");
        visible.put("gameplay_context_active", true);
        visible.put("title_card_overlay_active", false);
        visible.put("title_card_complete", true);
        visible.put("fade_active", false);
        visible.put("fade_alpha", 0.0f);
        visible.put("overlays_disabled", true);

        assertDoesNotThrow(() -> FbzVisualVisibilityVerifier.verifyState(visible));
        for (String contaminated : new String[]{
                "title_card_overlay_active", "fade_active"}) {
            Map<String, Object> invalid = new LinkedHashMap<>(visible);
            invalid.put(contaminated, true);
            assertThrows(IllegalStateException.class,
                    () -> FbzVisualVisibilityVerifier.verifyState(invalid));
        }
        Map<String, Object> overlay = new LinkedHashMap<>(visible);
        overlay.put("overlays_disabled", false);
        assertThrows(IllegalStateException.class,
                () -> FbzVisualVisibilityVerifier.verifyState(overlay));
    }

    private static Map<String, Object> nativeStartState(int levelFrame, int aniFrame) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("level_frame_counter", levelFrame);
        for (int channel = 0; channel < 5; channel++) {
            values.put("aniplc_timer_" + channel, 0);
            values.put("aniplc_frame_" + channel, aniFrame);
        }
        values.put("raw_anim_counters", new ArrayList<>(List.of(
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)));
        return values;
    }

    private FbzVisualEvidenceAmendment amendment(String status, Integer levelFrame,
                                                 Integer cameraXCopy) throws Exception {
        Path path = tempDir.resolve(status + ".json");
        String exactState = levelFrame == null ? "null" : """
                {"level_frame_counter":%d,"camera_x_copy":%d}
                """.formatted(levelFrame, cameraXCopy);
        Files.writeString(path, """
                {
                  "rom_initialization_invariant": {
                    "anim_counters": [0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0],
                    "first_animation_tick": {
                      "anim_counters": [63,1,7,1,1,1,7,1,7,1,0,0,0,0,0,0]
                    }
                  },
                  "accepted_first_visible_frame": {
                    "status": "%s",
                    "exact_state": %s
                  }
                }
                """.formatted(status, exactState));
        return FbzVisualEvidenceAmendment.load(path,
                FbzVisualPrebootVerifier.sha256(Files.readAllBytes(path)));
    }
}
