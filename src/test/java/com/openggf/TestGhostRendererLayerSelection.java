package com.openggf;

import com.openggf.ghost.GhostFrame;
import com.openggf.mods.ui.CompactFont;
import com.openggf.mods.ui.PixelCanvas;
import com.openggf.sprites.ghost.ActiveGhost;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestGhostRendererLayerSelection {
    @Test
    void matchesOnlyOwnRecordedLayer() {
        GhostFrame frame = new GhostFrame(0, 0, 0, false, false, false, 2, true);
        assertTrue(GhostRenderer.layerMatches(frame, 2, true));
        assertFalse(GhostRenderer.layerMatches(frame, 2, false));
        assertFalse(GhostRenderer.layerMatches(frame, 3, true));
    }

    @Test
    void nameplatesWaitForOverlayAndAreConsumedOnce() {
        GhostRenderer renderer = new GhostRenderer();
        ActiveGhost ghost = ghost("net:5", 200, 150, "[SPEED]+");
        renderer.queueNameplate(ghost, 100, 50, 1f);
        renderer.queueNameplate(ghost, 110, 60, 1f); // repeated preparation replaces, rather than duplicates

        RecordingCanvas canvas = new RecordingCanvas();
        renderer.drawNameplates(canvas);
        int textWidth = CompactFont.width("[SPEED]+", 1);
        // Backing plate centred over the ghost (screen x 90), bottom 22 pixels above its centre (y 90).
        assertEquals(new Fill(90 - (textWidth + 4) / 2, 90 - 22 - 9, textWidth + 4, 9, 0xD8101828),
                canvas.fills.getFirst());
        assertTrue(canvas.fills.stream().skip(1).allMatch(fill -> fill.argb() == 0xFFF5F8FF),
                "glyphs are opaque compact-font text on the plate");
        assertTrue(canvas.fills.size() > 1);
        canvas.fills.clear();
        renderer.drawNameplates(canvas);
        assertTrue(canvas.fills.isEmpty());
    }

    @Test
    void unloadingDiscardsLabelsAndUnnamedGhostsDrawNothing() {
        GhostRenderer renderer = new GhostRenderer();
        renderer.queueNameplate(ghost("net:1", 200, 150, "Old name"), 0, 0, 1f);
        renderer.clearSlots();
        renderer.queueNameplate(ghost("net:2", 200, 150, " "), 0, 0, 1f);
        renderer.queueNameplate(new ActiveGhost("net:3", "sonic",
                new GhostFrame(200, 150, 0, false, false, false, 2, false)), 0, 0, 1f);
        RecordingCanvas canvas = new RecordingCanvas();
        renderer.drawNameplates(canvas);
        assertTrue(canvas.fills.isEmpty());
    }

    @Test
    void bunchedNamesStackInsteadOfOverlapping() {
        GhostRenderer renderer = new GhostRenderer();
        for (int index = 0; index < 5; index++) {
            renderer.queueNameplate(ghost("net:" + index, 160 + index * 4, 120, "RUNNER " + index), 0, 0, 1f);
        }
        List<GhostRenderer.NameplateTag> tags = renderer.layoutNameplates(320, 224);
        assertEquals(5, tags.size());
        for (int a = 0; a < tags.size(); a++) {
            for (int b = a + 1; b < tags.size(); b++) {
                var first = tags.get(a);
                var second = tags.get(b);
                boolean overlap = first.x() < second.x() + second.width() && second.x() < first.x() + first.width()
                        && first.y() < second.y() + second.height() && second.y() < first.y() + first.height();
                assertFalse(overlap, () -> first + " overlaps " + second);
            }
        }
    }

    @Test
    void longNamesAreTruncatedAndTagsStayInsideTheViewport() {
        GhostRenderer renderer = new GhostRenderer();
        renderer.queueNameplate(ghost("net:1", 2, 120, "A Very Long Player Name"), 0, 0, 1f);
        renderer.queueNameplate(ghost("net:2", 318, 120, "Edge"), 0, 0, 0.5f);
        renderer.queueNameplate(ghost("net:3", 500, 120, "Offscreen"), 0, 0, 1f);
        renderer.queueNameplate(ghost("net:4", 160, 120, "Faded"), 0, 0, 0f);
        List<GhostRenderer.NameplateTag> tags = renderer.layoutNameplates(320, 224);
        assertEquals(2, tags.size(), "offscreen and fully faded ghosts have no tag");
        for (var tag : tags) {
            assertTrue(tag.x() >= 0 && tag.x() + tag.width() <= 320, tag::toString);
            assertTrue(CompactFont.width(tag.text(), 1) <= GhostRenderer.NAMEPLATE_TEXT_WIDTH, tag::toString);
        }
        assertTrue(tags.stream().anyMatch(tag -> tag.text().endsWith("...")));
        assertTrue(tags.stream().anyMatch(tag -> tag.alpha() == 0.5f), "tags fade with their ghost");
    }

    private static ActiveGhost ghost(String slot, int x, int y, String name) {
        return new ActiveGhost(slot, "sonic", new GhostFrame(x, y, 0, false, false, false, 2, false), name, 1f);
    }

    private record Fill(int x, int y, int width, int height, int argb) { }

    private static final class RecordingCanvas implements PixelCanvas {
        final List<Fill> fills = new ArrayList<>();
        @Override public int width() { return 320; }
        @Override public int height() { return 224; }
        @Override public void fill(int x, int y, int width, int height, int argb) {
            fills.add(new Fill(x, y, width, height, argb));
        }
    }
}
