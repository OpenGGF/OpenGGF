package com.openggf.tools.modsdk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.game.ObjectArtOverlayProvider;
import com.openggf.game.sonic2.Sonic2ObjectArtProvider;
import com.openggf.graphics.GraphicsManager;
import com.openggf.io.ModInputLimits;
import com.openggf.level.Pattern;
import com.openggf.level.PatternDesc;
import com.openggf.level.objects.BakedSheetReader;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.render.PatternSpriteRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TestCreatorFeedback {
    @TempDir Path temp;

    private Path reskin(String key) throws Exception {
        Path fixture = Path.of("src/test/resources/mods/sample-reskin-src");
        Path input = temp.resolve("input-" + key);
        Files.createDirectories(input.resolve("META-INF"));
        Files.createDirectories(input.resolve("art"));
        Files.writeString(input.resolve("META-INF/openggf-mod.yaml"),
                Files.readString(fixture.resolve("META-INF/openggf-mod.yaml"))
                        .replace("signpost:", key + ":"));
        Path png = temp.resolve(key + ".png");
        Files.write(png, Base64.getDecoder().decode(Files.readString(fixture.resolve("reskin.png.base64")).trim()));
        new ArtConverter().convert(png, fixture.resolve("reskin-sheet.yaml"), input.resolve("art/reskin.ggfs"));
        return input;
    }

    @Test void actualSignpostConsumerHasVisibleArtForEveryStockAnimationFrame() throws Exception {
        var baked = BakedSheetReader.read(Files.readAllBytes(reskin("signpost").resolve("art/reskin.ggfs")),
                ModInputLimits.production());
        var provider = new ObjectArtOverlayProvider(new Sonic2ObjectArtProvider(),
                Map.of("signpost", baked.toObjectSpriteSheet()));
        ObjectSpriteSheet consumed = new ObjectRenderManager(provider).getSignpostSheet();
        assertNotNull(consumed, "Use the stock consumer's exact key, not a test-only lookup");
        assertEquals(6, consumed.getFrameCount());
        assertEquals(0, consumed.getPaletteIndex(), "S2 stock Sonic/Tails palette line");
        RecordingGraphics graphics = new RecordingGraphics();
        PatternSpriteRenderer renderer = new PatternSpriteRenderer(consumed, graphics);
        renderer.ensurePatternsCached(graphics, 0x400);
        // Owning Ani_obj0D scripts use 2 for idle, all six for spin, 0/1 for final faces.
        for (int frame : new int[]{2, 2, 3, 4, 5, 1, 3, 4, 5, 0, 3, 4, 5, 0, 1}) {
            int before = graphics.visibleDraws;
            renderer.drawFrameIndex(frame, 32, 48);
            assertTrue(graphics.visibleDraws > before, "Stock frame " + frame + " must visibly draw");
        }
    }

    @Test void unknownKeyWarningsAreVisibleSortedAndMachineReadableAndStrictPolicyNeverPublishes() throws Exception {
        Path input = reskin("EndSign");
        Path manifest = input.resolve("META-INF/openggf-mod.yaml");
        Files.writeString(manifest, Files.readString(manifest).replace("  EndSign: art/reskin.ggfs",
                "  ZzzSign: art/reskin.ggfs\n  EndSign: art/reskin.ggfs"));
        Path jar = temp.resolve("warn.jar");
        ByteArrayOutputStream text = new ByteArrayOutputStream();
        assertEquals(0, GgfModCli.run(new String[]{"package", "--input", input.toString(), "--out", jar.toString()},
                new PrintStream(text)));
        assertTrue(text.toString().contains("WARNING UNKNOWN_ART_OVERRIDE_KEY"));
        assertTrue(text.toString().indexOf("'EndSign'") < text.toString().indexOf("'ZzzSign'"), text::toString);
        ByteArrayOutputStream json = new ByteArrayOutputStream();
        assertEquals(1, GgfModCli.run(new String[]{"validate", jar.toString(), "--format", "json", "--warnings", "error"},
                new PrintStream(json)));
        var report = new ObjectMapper().readTree(json.toByteArray());
        assertEquals(1, report.path("formatVersion").asInt());
        assertEquals("error", report.path("warningPolicy").asText());
        assertEquals("UNKNOWN_ART_OVERRIDE_KEY", report.path("findings").get(0).path("code").asText());
        assertEquals(2, report.path("warnings").asInt());
        assertEquals(2, report.path("findings").size());
        ByteArrayOutputStream packageJson = new ByteArrayOutputStream();
        Path jsonJar = temp.resolve("json-warn.jar");
        assertEquals(0, GgfModCli.run(new String[]{"package", "--input", input.toString(), "--out", jsonJar.toString(), "--format", "json"}, new PrintStream(packageJson)));
        assertEquals(report.path("findings"), new ObjectMapper().readTree(packageJson.toByteArray()).path("findings"));
        Path blocked = temp.resolve("blocked.jar");
        assertThrows(IllegalArgumentException.class,
                () -> JarPackager.packageDirectoryWithReport(input, blocked, true));
        assertFalse(Files.exists(blocked));
    }

    private static final class RecordingGraphics extends GraphicsManager {
        private final Map<Integer, Pattern> patterns = new HashMap<>();
        int visibleDraws;
        @Override public void cachePatternTexture(Pattern pattern, int id) { patterns.put(id, pattern); }
        @Override public void renderPatternWithId(int id, PatternDesc descriptor, int x, int y) {
            assertEquals(0, descriptor.getPaletteIndex());
            Pattern pattern = patterns.get(id);
            assertNotNull(pattern);
            for (int py = 0; py < 8; py++) for (int px = 0; px < 8; px++) {
                if (pattern.getPixel(px, py) == 6) { visibleDraws++; return; }
            }
        }
    }
}
