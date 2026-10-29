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
        assertTrue(reader.handle(input, () -> List.of("Racer😀", "Racer🚀")));
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
        input.tapKey(frame -> assertTrue(reader.handle(frame, source)), SceneKeys.F1);
        RecordingSceneCanvas first = new RecordingSceneCanvas(320);
        reader.draw(first);
        List<String> initial = reader.visibleLines();
        input.tapRight(frame -> reader.handle(frame, source));
        reader.draw(first);
        assertNotEquals(initial, reader.visibleLines(), "Right advances a page");
        input.tapLeft(frame -> reader.handle(frame, source));
        String read = FullTextReaderProbe.readAll(input, frame -> reader.handle(frame, source), reader::draw,
                reader::visibleLines, reader::firstVisibleLine);
        assertTrue(read.contains(FullTextReader.readable(paragraph)), read);
        assertTrue(read.contains("FINAL_NOTICE"), read);
        for (String line : reader.visibleLines()) assertTrue(NameFont.width(line) <= 292);
        input.clear();
        input.accept = true;
        assertTrue(reader.handle(input, source));
        assertFalse(reader.isOpen());
    }
}
