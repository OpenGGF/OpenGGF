package openggf.timeattack.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Walks real reader line input and checks the pixels of every visited viewport. */
public final class FullTextReaderProbe {
    private FullTextReaderProbe() { }

    public static String readAll(FakeViewInput input, Consumer<ViewInput> update,
                                 Consumer<RecordingSceneCanvas> draw, Supplier<List<String>> visible,
                                 java.util.function.IntSupplier firstLine) {
        List<String> read = new ArrayList<>();
        int previous = -1;
        for (int step = 0; step < 600; step++) {
            RecordingSceneCanvas canvas = new RecordingSceneCanvas(320);
            draw.accept(canvas);
            canvas.assertFillsOnScreen();
            assertTrue(canvas.shows("DETAILS"), canvas.joined()); // the reader's chip (or Room details' title)
            int marker = firstLine.getAsInt();
            List<String> page = visible.get();
            if (marker == previous) {
                read.addAll(page.subList(Math.min(1, page.size()), page.size()));
                return String.join("", read);
            }
            if (!page.isEmpty()) read.add(page.getFirst());
            previous = marker;
            input.tapDown(update);
        }
        throw new AssertionError("Reader did not reach the end of the bounded fixture");
    }
}
