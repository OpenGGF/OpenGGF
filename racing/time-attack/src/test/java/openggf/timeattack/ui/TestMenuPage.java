package openggf.timeattack.ui;

import com.openggf.mods.ui.NameFont;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Time Attack page style: measured wrapping, footer fitting and key caps. */
class TestMenuPage {
    @Test
    void wrappingIsMeasuredAndNeverSplitsAGlyph() {
        String text = "Replace HOST_IP with your LAN address and share HOST_IP:27888#" + "aB1_".repeat(20);
        for (int width : new int[] {120, 200, 304}) {
            List<String> lines = MenuPage.wrap(text, width);
            for (String line : lines) assertTrue(NameFont.width(line) <= width, width + ": " + line);
            assertEquals(text.replace(" ", ""), String.join("", lines).replace(" ", ""), "every character is kept");
        }
    }

    @Test
    void theFooterKeepsBackAndDropsLeadingHintsOnlyWhenTheyDoNotFit() {
        List<MenuPage.Hint> hints = List.of(MenuPage.hint(MenuPage.ARROWS_UD, "Choose"),
                MenuPage.hint(MenuPage.ARROWS_LR, "Change"), MenuPage.hint("ENTER", "Edit pools"),
                MenuPage.hint("J", "Code"), MenuPage.hint("R", "Refresh"), MenuPage.hint("ESC", "Back"));
        for (int width : new int[] {320, 400, 640}) {
            RecordingSceneCanvas canvas = new RecordingSceneCanvas(width);
            MenuPage.footer(canvas, "", MenuPage.TEXT, hints);
            canvas.assertFillsOnScreen();
            assertTrue(canvas.shows("BACK"), "the rightmost hint always stays");
            boolean fits = MenuPage.hintsWidth(hints) <= width - 2 * MenuPage.MARGIN;
            assertEquals(fits, canvas.shows("CHOOSE"), "the first hint is dropped only when the row overflows");
        }
    }

    @Test
    void keyCapsDrawInsideTheirMeasuredWidth() {
        for (String key : List.of("ENTER", "ESC", "A", "BKSP", MenuPage.ARROWS_LR, MenuPage.ARROWS_UD, "1-3")) {
            RecordingSceneCanvas canvas = new RecordingSceneCanvas(320);
            int width = MenuPage.cap(canvas, 10, 20, key);
            assertEquals(MenuPage.capWidth(key), width);
            for (int[] fill : canvas.fills) {
                assertTrue(fill[0] >= 10 && fill[0] + fill[2] <= 10 + width && fill[1] >= 20 && fill[1] + fill[3] <= 31,
                        key + " " + java.util.Arrays.toString(fill));
            }
        }
        RecordingSceneCanvas canvas = new RecordingSceneCanvas(320);
        MenuPage.cap(canvas, 0, 0, "ENTER");
        assertTrue(canvas.shows("ENTER"));
        assertFalse(canvas.shows("ESC"));
    }
}
