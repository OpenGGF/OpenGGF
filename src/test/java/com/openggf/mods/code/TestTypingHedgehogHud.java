package com.openggf.mods.code;

import com.openggf.mods.ui.PixelCanvas;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/** Viewport checks for the in-level UI, including the 400-pixel widescreen display. */
class TestTypingHedgehogHud {
    @TempDir static Path work;
    static ExampleModHarness harness;
    static Object font, plates, hud;

    @BeforeAll static void build() throws Exception {
        harness = ExampleModHarness.build(Path.of("examples/typing-of-the-hedgehog"), work);
        var loader = harness.loader();
        var fontType = loader.loadClass("typinghog.art.PlateFont");
        font = fontType.getConstructor(String.class).newInstance(Files.readString(Path.of(
                "examples/typing-of-the-hedgehog/src/main/resources/art/plate-font.txt")));
        var platesType = loader.loadClass("typinghog.art.Plates");
        plates = platesType.getConstructor(fontType).newInstance(font);
        var assetsType = loader.loadClass("typinghog.play.CourseAssets");
        Object assets = assetsType.getConstructors()[0].newInstance(null, plates, Map.of(), Map.of(), Map.of(),
                Map.of(), Map.of());
        var constructor = loader.loadClass("typinghog.play.Hud").getDeclaredConstructor(assetsType);
        constructor.setAccessible(true);
        hud = constructor.newInstance(assets);
    }

    @AfterAll static void close() throws Exception { if (harness != null) harness.close(); }

    @Test void bannersUseTheWholeViewportAndCentreTheirText() throws Exception {
        var draw = hud.getClass().getDeclaredMethod("drawBanner", PixelCanvas.class, String.class, int.class, int.class);
        draw.setAccessible(true);
        for (int width : List.of(320, 400)) {
            for (String text : List.of("GET READY", "FIGHT 4", "WARNING!")) {
                var canvas = new Canvas(width);
                draw.invoke(hud, canvas, text, 48, 0xFFFFFFFF);
                var band = canvas.fills.getFirst();
                assertEquals(new Fill(0, 44, width, 26), band);
                // Ignore the full-width band; the double-size text (and shadow) is symmetric.
                int left = canvas.fills.subList(1, canvas.fills.size()).stream().mapToInt(Fill::x).min().orElseThrow();
                int right = canvas.fills.subList(1, canvas.fills.size()).stream().mapToInt(f -> f.x + f.w).max().orElseThrow();
                assertTrue(Math.abs(left + right - width) <= 4, text + " centred at " + width);
            }
        }
    }

    @Test void platesFollowTargetsAcrossThe400PixelViewport() throws Exception {
        var loader = harness.loader();
        var promptType = loader.loadClass("typinghog.core.Prompt");
        Object prompt = promptType.getMethod("of", String.class, boolean.class, boolean.class)
                .invoke(null, "Sonic saves the animals!", false, false);
        var styleType = loader.loadClass("typinghog.art.Plates$Style");
        Object style = styleType.getEnumConstants()[1]; // LOCKED
        var draw = plates.getClass().getMethod("draw", PixelCanvas.class, promptType, int.class, styleType,
                int.class, int.class, boolean.class, int.class);
        for (int width : List.of(320, 400)) {
            for (int target : List.of(2, width / 2, width - 2)) {
                for (int shake : List.of(-2, 0, 2)) {
                    var canvas = new Canvas(width);
                    draw.invoke(plates, canvas, prompt, 6, style, target, 120, false, shake);
                    if (target > width / 2) {
                        assertTrue(canvas.fills.stream().anyMatch(f -> f.x > width - 20),
                                "right-hand plates use the added viewport space");
                    }
                }
            }
        }
    }

    @Test void quizWrappingUsesTheWiderViewport() throws Exception {
        Method wrap = hud.getClass().getDeclaredMethod("wrap", String.class, int.class);
        wrap.setAccessible(true);
        String question = "Which animal does Sonic free when he defeats the badniks in Green Hill Zone?";
        int previousLines = 100;
        for (int width : List.of(320, 400)) {
            @SuppressWarnings("unchecked") var lines = (List<String>) wrap.invoke(hud, question, width - 32);
            assertTrue(lines.size() <= previousLines);
            previousLines = lines.size();
            for (String line : lines) {
                assertTrue((int) font.getClass().getMethod("width", String.class).invoke(font, line) <= width - 32);
            }
            assertEquals(question, String.join(" ", lines));
        }
    }

    @Test void tallyPanelFillsBothViewportWidths() throws Exception {
        var cardType = harness.loader().loadClass("typinghog.core.Scorecard");
        Object card = cardType.getConstructor().newInstance();
        var tally = hud.getClass().getDeclaredField("tally");
        tally.setAccessible(true);
        tally.set(hud, cardType.getMethod("tally", int.class).invoke(card, 12));
        var draw = hud.getClass().getDeclaredMethod("drawTally", PixelCanvas.class,
                harness.loader().loadClass("typinghog.play.Course"), int.class, int.class);
        draw.setAccessible(true);
        for (int width : List.of(320, 400)) {
            var canvas = new Canvas(width);
            draw.invoke(hud, canvas, null, width, 200);
            assertEquals(new Fill(0, 40, width, 140), canvas.fills.getFirst());
        }
    }

    @Test void readyWaitsForCountdownAndEveryTransientBanner() throws Exception {
        var cue = hud.getClass().getDeclaredMethod("drawCues", PixelCanvas.class, boolean.class);
        cue.setAccessible(true);
        var ready = hud.getClass().getDeclaredMethod("ready", boolean.class);
        ready.setAccessible(true);
        var start = hud.getClass().getDeclaredMethod("start");
        start.setAccessible(true);
        var banner = hud.getClass().getDeclaredMethod("banner", String.class, int.class);
        banner.setAccessible(true);
        var bannerRows = hud.getClass().getDeclaredField("bannerRows");
        bannerRows.setAccessible(true);
        var startRows = hud.getClass().getDeclaredField("startRows");
        startRows.setAccessible(true);
        ready.invoke(hud, true);
        bannerRows.setInt(hud, 0);
        startRows.setInt(hud, 0);
        var canvas = new Canvas(400);
        cue.invoke(hud, canvas, true);
        assertTrue(canvas.fills.isEmpty(), "only the countdown owns the start screen");
        start.invoke(hud);
        for (int remaining = 50; remaining > 0; remaining--) {
            startRows.setInt(hud, remaining);
            canvas.fills.clear();
            cue.invoke(hud, canvas, false);
            assertTrue(canvas.fills.isEmpty(), "START! clears before GET READY");
        }
        startRows.setInt(hud, 0);
        canvas.fills.clear();
        cue.invoke(hud, canvas, false);
        assertEquals(1, canvas.fills.stream().filter(f -> f.w == 400).count());
        for (String text : List.of("GO!", "FIGHT 1", "WARNING!", "WAVE CLEAR!", "TOO SLOW!")) {
            banner.invoke(hud, text, 0xFFFFFFFF);
            for (int i = 0; i < 90; i++) {
                canvas.fills.clear();
                cue.invoke(hud, canvas, false);
                assertEquals(1, canvas.fills.stream().filter(f -> f.w == 400).count(), text + " owns its row");
                int y = 72 - Math.max(0, 89 - i - 80) * 3;
                assertEquals(new Fill(0, y - 4, 400, 26), canvas.fills.getFirst(), text + " is the displayed band");
            }
        }
    }

    private record Fill(int x, int y, int w, int h) { }

    private static final class Canvas implements PixelCanvas {
        private final int width;
        final List<Fill> fills = new ArrayList<>();
        Canvas(int width) { this.width = width; }
        @Override public int width() { return width; }
        @Override public int height() { return 224; }
        @Override public void fill(int x, int y, int w, int h, int argb) {
            if (w <= 0 || h <= 0 || argb >>> 24 == 0) return;
            assertTrue(x >= 0 && x + w <= width, "fill outside " + width + ": " + x + "+" + w);
            fills.add(new Fill(x, y, w, h));
        }
    }
}
