package openggf.timeattack.ui;

import com.openggf.mods.scene.SceneKeys;
import com.openggf.mods.ui.NameFont;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestFullTextReader {
    @Test void unsupportedNamesKeepDistinctCodePointIdentitiesAndControllerReadCannotConfirm() {
        var reader = new FullTextReader();
        var input = new FakeViewInput();
        input.physical = new com.openggf.control.PhysicalInput(1, List.of(), List.of(), List.of(
                new com.openggf.control.PhysicalInputEvent(1, 1, com.openggf.control.PhysicalInputEvent.Kind.BUTTON,
                        0, com.openggf.control.PhysicalGamepad.BUTTON_RIGHT_THUMB, 1)), 0);
        input.accept = true;
        assertTrue(reader.handleLines(input, () -> List.of("Racer😀", "Racer🚀")));
        assertTrue(reader.isOpen(), "The read gesture consumes simultaneous confirmation");
        reader.draw(new RecordingSceneCanvas(320));
        String text = String.join("", reader.visibleLines());
        assertTrue(text.contains("Racer[U+1F600]"), text);
        assertTrue(text.contains("Racer[U+1F680]"), text);
    }

    @Test
    void everyGraphemeAndExplicitParagraphSurvivesMeasuredLineAndPageNavigation() {
        String paragraph = "始まりe\u0301🚀" + "العربيةと日本語".repeat(100) + "END_OF_BODY";
        FullTextReader reader = new FullTextReader();
        FakeViewInput input = new FakeViewInput();
        var source = (java.util.function.Supplier<List<String>>) () -> List.of("Title", paragraph, "FINAL_NOTICE");
        input.tapKey(frame -> assertTrue(reader.handleLines(frame, source)), SceneKeys.F1);
        RecordingSceneCanvas first = new RecordingSceneCanvas(320);
        reader.draw(first);
        List<String> initial = reader.visibleLines();
        input.tapRight(frame -> reader.handleLines(frame, source));
        reader.draw(first);
        assertNotEquals(initial, reader.visibleLines(), "Right advances a page");
        input.tapLeft(frame -> reader.handleLines(frame, source));
        String read = FullTextReaderProbe.readAll(input, frame -> reader.handleLines(frame, source), reader::draw,
                reader::visibleLines, reader::firstVisibleLine);
        assertTrue(read.contains(FullTextReader.readable(paragraph)), read);
        assertTrue(read.contains("FINAL_NOTICE"), read);
        for (String line : reader.visibleLines()) assertTrue(NameFont.width(line) <= 292);
        input.clear();
        input.accept = true;
        assertTrue(reader.handleLines(input, source));
        assertFalse(reader.isOpen());
    }

    @Test
    void sentencesBreakAtSpacesAndLongAddressesAfterASeparatorWithoutLosingACharacter() {
        String sentence = "Up/Down select. Left/Right change. Enter edits a vote pool. Start goes. "
                + "Back returns to the title, or cancels a connection.";
        List<String> rows = FullTextReader.wrap(sentence, 120);
        assertEquals(sentence, String.join("", rows), "rows rejoin the exact text");
        for (int i = 0; i < rows.size() - 1; i++) {
            assertTrue(rows.get(i).endsWith(" "), "row " + i + " ends at a space, not inside a word: " + rows);
            assertTrue(NameFont.width(rows.get(i)) <= 120, rows.get(i));
        }
        String address = "wss://master.racing-league.example.test/regions/europe-west/openggf/time-attack/master";
        List<String> parts = FullTextReader.wrap(address, 150);
        assertEquals(address, String.join("", parts));
        assertTrue(parts.size() > 1, parts.toString());
        for (int i = 0; i < parts.size() - 1; i++) {
            char last = parts.get(i).charAt(parts.get(i).length() - 1);
            assertTrue("/.-_:?&=#".indexOf(last) >= 0, "an address row ends after a separator: " + parts);
            assertTrue(NameFont.width(parts.get(i)) <= 150);
        }
    }

    @Test
    void detailsPageTitlesItsOwnerAlignsFactsAndOmitsBlankEntries() {
        FullTextReader reader = new FullTextReader();
        FakeViewInput input = new FakeViewInput();
        String url = "https://example.test/" + "long-path/".repeat(12) + "ADDRESS_TAIL";
        var source = (java.util.function.Supplier<FullTextReader.Text>) () -> FullTextReader.Text.of("SETTINGS", "Context")
                .field("Name", "Tails").field("Master server", url).field("Status", "").line("")
                .section("Empty").section("Notes").line("A sentence that wraps across the page at its spaces, whole.");
        input.tapKey(frame -> assertTrue(reader.handle(frame, source)), SceneKeys.F1);
        for (int width : new int[] {320, 400}) {
            RecordingSceneCanvas canvas = new RecordingSceneCanvas(width);
            reader.draw(canvas);
            canvas.assertFillsOnScreen();
            assertTrue(canvas.joined().contains("SETTINGS"), "the page keeps its owner's title");
            assertTrue(canvas.shows("DETAILS"), "a DETAILS chip says this is the read-only page");
            List<String> rows = reader.visibleLines();
            assertEquals("NameTails", rows.getFirst(), "label then value on the first row");
            assertTrue(rows.get(1).startsWith("Master server"), rows.toString());
            String joined = String.join("", rows);
            assertTrue(joined.contains(url), "the address is whole across its rows at " + width);
            assertFalse(joined.contains("Status"), "a blank value is left out");
            assertFalse(joined.contains("EMPTY") || joined.contains("Empty"), "a caption with nothing under it is left out");
            int valueX = MenuPage.MARGIN + 6 + NameFont.width("Master server") + 10;
            assertTrue(canvas.drewName("Tails", valueX, MenuPage.TEXT), "values start after the widest label at " + width);
        }
        input.tapEscape(frame -> reader.handle(frame, source));
        assertFalse(reader.isOpen());
    }
}
