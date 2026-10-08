package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.level.Level;
import com.openggf.level.Pattern;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Locale;

/**
 * Opt-in diagnostic: renders every frame of the object art sheets a zone registers to
 * labelled PNG contact sheets, one per art key, so mapping frames can be picked by eye.
 *
 * <p>Answers "which frame of this sheet is the walk, the hurt face, the projectile?" for
 * object, boss and mod work without guessing from code. Each cell is one mapping frame drawn
 * on the CPU from the sheet's patterns with the act's level palette (pieces choose their own
 * palette line), with the frame index top-left and a red cross at the object origin. Sheets
 * whose art uses a runtime tile base (Grounder in Sonic 2, for example) may draw scrambled.
 *
 * <p>Inputs (system properties): {@code openggf.artsheets.out} (output directory; the probe
 * is skipped when unset), {@code openggf.artsheets.game} ({@code s1}, {@code s2},
 * {@code s3k}; default {@code s2}), {@code openggf.artsheets.acts} (comma-separated 0-based
 * {@code zone:act} pairs; default {@code 0:0}), {@code openggf.artsheets.keys} (optional
 * comma-separated substrings; only matching art keys are drawn). Files are named
 * {@code <zone>-<act>-<key>.png}. The usual ROM path properties select the ROM.
 *
 * <pre>
 * mvn -Dmse=off -Dsonic2.rom.path=/abs/s2.gen -Dtest=ObjectArtContactSheetProbe \
 *     -Dopenggf.artsheets.out=/abs/task-dir/sheets -Dopenggf.artsheets.acts=0:0,5:0 \
 *     -Dopenggf.artsheets.keys=boss,buzzer test
 * </pre>
 *
 * <p>Originating task: Sonic Survivors badnik and boss frame selection (2026-10-06).
 * Comparison-only: it reads loaded art and never changes engine state.
 */
@RequiresRom(SonicGame.SONIC_2)
class ObjectArtContactSheetProbe {

    @Test
    void writeContactSheets() throws Exception {
        String out = System.getProperty("openggf.artsheets.out");
        Assumptions.assumeTrue(out != null && !out.isBlank(),
                "set -Dopenggf.artsheets.out=<output directory> to write contact sheets");
        SonicGame game = switch (System.getProperty("openggf.artsheets.game", "s2")) {
            case "s1" -> SonicGame.SONIC_1;
            case "s3k" -> SonicGame.SONIC_3K;
            default -> SonicGame.SONIC_2;
        };
        String filter = System.getProperty("openggf.artsheets.keys", "");
        String[] wanted = filter.isBlank() ? new String[0] : filter.toLowerCase(Locale.ROOT).split(",");
        Path dir = Files.createDirectories(Path.of(out));
        int written = 0;
        for (String pair : System.getProperty("openggf.artsheets.acts", "0:0").split(",")) {
            String[] za = pair.trim().split(":");
            int zone = Integer.decode(za[0]), act = Integer.decode(za[1]);
            SharedLevel shared = SharedLevel.load(game, zone, act);
            try {
                HeadlessTestFixture.builder().withSharedLevel(shared).build();
                var level = GameServices.level().getCurrentLevel();
                var art = GameServices.module().getObjectArtProvider();
                for (String key : art.getRendererKeys()) {
                    if (wanted.length > 0 && java.util.Arrays.stream(wanted).noneMatch(w -> key.contains(w.trim()))) {
                        continue;
                    }
                    var sheet = art.getSheet(key);
                    if (sheet == null || sheet.getFrameCount() == 0) continue;
                    javax.imageio.ImageIO.write(render(sheet, level), "png",
                            dir.resolve(zone + "-" + act + "-" + key + ".png").toFile());
                    written++;
                }
            } finally {
                shared.dispose();
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(written > 0, "no sheets matched; check the acts and keys");
    }

    static BufferedImage render(ObjectSpriteSheet sheet, Level level) {
        int cell = 112, cols = 8, count = sheet.getFrameCount();
        int rows = (count + cols - 1) / cols;
        var image = new BufferedImage(cols * cell, Math.max(1, rows) * cell, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        g.setColor(new Color(40, 40, 60));
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        Pattern[] patterns = sheet.getPatterns();
        for (int f = 0; f < count; f++) {
            int cellX = (f % cols) * cell, cellY = (f / cols) * cell;
            int ox = cellX + cell / 2, oy = cellY + cell / 2;
            g.setColor(new Color(70, 70, 90));
            g.drawRect(cellX, cellY, cell - 1, cell - 1);
            g.setColor(Color.YELLOW);
            g.drawString(Integer.toString(f), cellX + 3, cellY + 12);
            var frame = sheet.getFrame(f);
            if (frame == null) continue;
            var pieces = new java.util.ArrayList<>(frame.pieces());
            // The first piece is frontmost: draw back to front.
            Collections.reverse(pieces);
            for (var piece : pieces) {
                var palette = level.getPalette(piece.paletteIndex() & 3);
                for (int tx = 0; tx < piece.widthTiles(); tx++) {
                    for (int ty = 0; ty < piece.heightTiles(); ty++) {
                        // Mapping tiles run down each column first.
                        int tile = piece.tileIndex() + tx * piece.heightTiles() + ty;
                        if (tile < 0 || tile >= patterns.length) continue;
                        int sx = piece.hFlip() ? piece.widthTiles() - 1 - tx : tx;
                        int sy = piece.vFlip() ? piece.heightTiles() - 1 - ty : ty;
                        for (int py = 0; py < 8; py++) {
                            for (int px = 0; px < 8; px++) {
                                int v = patterns[tile].getPixel(px, py) & 0xF;
                                if (v == 0) continue;
                                var c = palette.getColor(v);
                                int x = ox + piece.xOffset() + sx * 8 + (piece.hFlip() ? 7 - px : px);
                                int y = oy + piece.yOffset() + sy * 8 + (piece.vFlip() ? 7 - py : py);
                                if (x < 0 || y < 0 || x >= image.getWidth() || y >= image.getHeight()) continue;
                                image.setRGB(x, y, 0xFF000000 | (c.r & 0xFF) << 16 | (c.g & 0xFF) << 8 | (c.b & 0xFF));
                            }
                        }
                    }
                }
            }
            g.setColor(Color.RED);
            g.drawLine(ox - 2, oy, ox + 2, oy);
            g.drawLine(ox, oy - 2, ox, oy + 2);
        }
        g.dispose();
        return image;
    }
}
