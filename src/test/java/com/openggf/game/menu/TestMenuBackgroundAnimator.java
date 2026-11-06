package com.openggf.game.menu;

import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Pattern;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestMenuBackgroundAnimator {
    private static final int PATTERN_BASE = 0x500;

    @Test
    void repeatedResourcePublicationDoesNotAdvanceOrResetTheAnimationClock() {
        Pattern[] patterns = patterns(40);
        RecordingGraphics graphics = new RecordingGraphics();
        MenuBackgroundAnimator animator = new MenuBackgroundAnimator(patterns, graphics, PATTERN_BASE);
        for (int tick = 0; tick < 201; tick++) animator.update();
        graphics.uploads.clear();
        for (int picture = 0; picture < 10; picture++) {
            animator.publishCurrentFrame();
            assertFrame(graphics, patterns, 10, 10);
            graphics.uploads.clear();
        }
        for (int tick = 0; tick < 5; tick++) animator.update();
        assertTrue(graphics.uploads.isEmpty(), "pictures cannot spend the short hold");
        animator.update();
        assertFrame(graphics, patterns, 20, 10);
    }

    @Test
    void primePublishesFirstFrameWithoutAdvancingPlayback() {
        Pattern[] patterns = patterns(40);
        RecordingGraphics graphics = new RecordingGraphics();
        MenuBackgroundAnimator animator = new MenuBackgroundAnimator(patterns, graphics, PATTERN_BASE);

        animator.prime();
        assertFrame(graphics, patterns, 0, 10);
        graphics.uploads.clear();

        animator.update();
        assertFrame(graphics, patterns, 0, 10);
        graphics.uploads.clear();
        animator.prime();
        assertFrame(graphics, patterns, 0, 10);
        graphics.uploads.clear();
        for (int tick = 0; tick < 199; tick++) {
            animator.update();
        }
        assertTrue(graphics.uploads.isEmpty(), "Priming must not reset the active countdown");
        animator.update();
        assertFrame(graphics, patterns, 10, 10);
    }

    @Test
    void updatePreservesLongHoldsShortHoldsAndSixFrameWrap() {
        Pattern[] patterns = patterns(40);
        RecordingGraphics graphics = new RecordingGraphics();
        MenuBackgroundAnimator animator = new MenuBackgroundAnimator(patterns, graphics, PATTERN_BASE);
        int[] uploadTicks = {1, 201, 207, 213, 413, 419, 425};
        int[] sourceTiles = {0, 10, 20, 30, 20, 10, 0};
        int frame = 0;

        for (int tick = 1; tick <= 425; tick++) {
            animator.update();
            if (tick == uploadTicks[frame]) {
                assertFrame(graphics, patterns, sourceTiles[frame], 10);
                frame++;
            } else {
                assertTrue(graphics.uploads.isEmpty(), "Unexpected upload at tick " + tick);
            }
            graphics.uploads.clear();
        }
        assertEquals(uploadTicks.length, frame);
    }

    @Test
    void unavailableGraphicsSkipsPublicationWhilePlaybackAdvances() {
        Pattern[] patterns = patterns(40);
        RecordingGraphics graphics = new RecordingGraphics();
        graphics.available = false;
        MenuBackgroundAnimator animator = new MenuBackgroundAnimator(patterns, graphics, PATTERN_BASE);

        animator.prime();
        for (int tick = 1; tick <= 200; tick++) {
            animator.update();
        }
        assertTrue(graphics.uploads.isEmpty());
        graphics.available = true;
        animator.update();
        assertFrame(graphics, patterns, 10, 10);
    }

    @Test
    void partialFramesSkipMissingSourcesWithoutShiftingDestinations() {
        Pattern[] patterns = patterns(23);
        patterns[11] = null;
        patterns[21] = null;
        RecordingGraphics graphics = new RecordingGraphics();
        MenuBackgroundAnimator animator = new MenuBackgroundAnimator(patterns, graphics, PATTERN_BASE);

        for (int tick = 1; tick <= 200; tick++) {
            animator.update();
        }
        graphics.uploads.clear();
        animator.update();
        assertEquals(9, graphics.uploads.size());
        assertUpload(graphics.uploads.getFirst(), patterns[10], PATTERN_BASE + 1);
        for (int i = 1; i < 9; i++) {
            assertUpload(graphics.uploads.get(i), patterns[11 + i], PATTERN_BASE + 2 + i);
        }
        graphics.uploads.clear();
        for (int tick = 202; tick <= 207; tick++) {
            animator.update();
        }
        assertEquals(2, graphics.uploads.size());
        assertUpload(graphics.uploads.get(0), patterns[20], PATTERN_BASE + 1);
        assertUpload(graphics.uploads.get(1), patterns[22], PATTERN_BASE + 3);
    }

    @Test
    void absentArtIsANoOp() {
        RecordingGraphics graphics = new RecordingGraphics();
        for (Pattern[] patterns : new Pattern[][] {null, new Pattern[0]}) {
            MenuBackgroundAnimator animator = new MenuBackgroundAnimator(patterns, graphics, PATTERN_BASE);
            animator.prime();
            animator.update();
        }
        assertTrue(graphics.uploads.isEmpty());
    }

    private static Pattern[] patterns(int count) {
        Pattern[] patterns = new Pattern[count];
        for (int i = 0; i < count; i++) {
            patterns[i] = new Pattern();
        }
        return patterns;
    }

    private static void assertFrame(RecordingGraphics graphics, Pattern[] patterns, int first, int count) {
        assertEquals(count, graphics.uploads.size());
        for (int tile = 0; tile < count; tile++) {
            assertUpload(graphics.uploads.get(tile), patterns[first + tile], PATTERN_BASE + 1 + tile);
        }
    }

    private static void assertUpload(Upload upload, Pattern pattern, int destination) {
        assertSame(pattern, upload.pattern());
        assertEquals(destination, upload.destination());
    }

    private record Upload(Pattern pattern, int destination) {}

    private static final class RecordingGraphics extends GraphicsManager {
        private final List<Upload> uploads = new ArrayList<>();
        private boolean available = true;

        @Override public boolean isGlInitialized() { return available; }

        @Override
        public void updatePatternTexture(Pattern pattern, int patternId) {
            uploads.add(new Upload(pattern, patternId));
        }
    }
}
