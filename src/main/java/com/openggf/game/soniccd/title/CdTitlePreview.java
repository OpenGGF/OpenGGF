package com.openggf.game.soniccd.title;

import com.openggf.game.preview.GeneratedTitlePreviewSequence;
import com.openggf.game.preview.TitlePreviewProvider;
import com.openggf.game.soniccd.CdDiscDataSource;
import com.openggf.game.soniccd.program.CdProgramData;
import com.openggf.game.soniccd.program.CdProgramScreenRenderer.Frame;

/** Detached disc title clip. Native program state stays private to this preview. */
public final class CdTitlePreview {
    private CdTitlePreview() { }

    public static TitlePreviewProvider.Sequence load(CdDiscDataSource disc) {
        var assets = new CdTitleAssets(new CdProgramData(disc));
        // A bounded presentation clip after the native entrance; no gameplay clock is changed.
        return new GeneratedTitlePreviewSequence(256, 224, 0, tick -> tick % 120, new Clip(assets)::image);
    }

    private static final class Clip {
        private final CdTitleAssets assets;
        private CdTitleProgram program;
        private int at = -1;
        private Clip(CdTitleAssets assets) { this.assets = assets; }
        private TitlePreviewProvider.Image image(int tick) {
            if (program == null || tick < at) {
                program = new CdTitleProgram(assets, 0);
                for (int i = 0; i < 400; i++) program.frame(0, 0);
                at = -1;
            }
            while (at < tick) { program.frame(0, 0); at++; }
            return raster(program.screen());
        }
    }

    // DrawTilemaps (scddisasm/src/Title Screen/Main.asm:1732-1757) puts the emblem on plane A
    // and the sea and mountains on plane B. DrawCloudsMap (scddisasm/src/Title Screen/Main.asm:646-659)
    // also uses plane B. ObjPlanet (scddisasm/src/Title Screen/Objects/Planet/Main.asm:15-50)
    // supplies Little Planet as a hovering sprite, which remains part of the clip.
    private static TitlePreviewProvider.Image raster(Frame frame) {
        var image = new TitlePreviewProvider.Image(frame.width(), 224, new byte[frame.width() * 224 * 4]);
        for (int y = 0; y < 224; y++) for (int x = 0; x < frame.width(); x++) {
            int colour = -1;
            for (int high = 0; high < 2; high++) {
                int emblem = emblemPixel(frame, x - frame.scrollA().applyAsInt(y), y + frame.vscrollA(), high);
                if (emblem >= 0) colour = emblem;
                for (int i = frame.sprites().size() - 1; i >= 0; i--) {
                    var s = frame.sprites().get(i);
                    int w = (((s.size() >> 2) & 3) + 1) * 8, h = ((s.size() & 3) + 1) * 8;
                    int dx = x - s.x(), dy = y - s.y();
                    if (dx < 0 || dy < 0 || dx >= w || dy >= h || (s.tile() >>> 15) != high) continue;
                    if ((s.tile() & 0x800) != 0) dx = w - 1 - dx;
                    if ((s.tile() & 0x1000) != 0) dy = h - 1 - dy;
                    int tile = ((s.tile() & 0x7ff) + (dx / 8) * (h / 8) + dy / 8) & 0x7ff;
                    int pixel = pixel(frame, tile, dx & 7, dy & 7);
                    if (pixel != 0) colour = frame.palette()[((s.tile() >> 9) & 0x30) + pixel];
                }
            }
            if (colour < 0) continue;
            int p = (y * frame.width() + x) * 4;
            image.rgba()[p] = (byte) ((((colour >> 1) & 7) * 255 + 3) / 7);
            image.rgba()[p + 1] = (byte) ((((colour >> 5) & 7) * 255 + 3) / 7);
            image.rgba()[p + 2] = (byte) ((((colour >> 9) & 7) * 255 + 3) / 7);
            image.rgba()[p + 3] = (byte) 255;
        }
        return image;
    }

    private static int emblemPixel(Frame f, int x, int y, int high) {
        x &= 511; y &= 255;
        int word = f.planeA()[(y / 8) * 64 + x / 8];
        if ((word & 0x7ff) == 0 || (word >>> 15) != high) return -1;
        int dx = x & 7, dy = y & 7;
        int pixel = pixel(f, word & 0x7ff, (word & 0x800) == 0 ? dx : 7 - dx,
                (word & 0x1000) == 0 ? dy : 7 - dy);
        return pixel == 0 ? -1 : f.palette()[((word >> 9) & 0x30) + pixel];
    }

    private static int pixel(Frame f, int tile, int x, int y) {
        int packed = f.tiles()[tile * 32 + y * 4 + x / 2] & 255;
        return (x & 1) == 0 ? packed >> 4 : packed & 15;
    }
}
