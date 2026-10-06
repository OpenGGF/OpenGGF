package com.openggf.mods.scene;

import com.openggf.data.Rom;
import com.openggf.game.GameId;
import com.openggf.io.PixelImage;
import com.openggf.io.PngCodec;
import com.openggf.mods.scene.host.SceneRomArtFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders every frame of a ROM sprite into one PNG grid (frame number above each cell), for
 * finding the frames and offsets a mod scene needs. CPU only; no GL or engine boot.
 *
 * <pre>
 * java -cp target/test-classes:target/classes:... com.openggf.mods.scene.SpriteSheetDump \
 *   s3k.gen s3k out.png art=0x367DCA comp=KOSINSKI_MODULED map=0x3616C0 line=1 \
 *   pal=0x0A8A3C:16:0 pal=0x0A8B7C:48:1 [dplc=0x36156E layout=OBJECT size=0xAA0 offset=0]
 * </pre>
 * {@code pal=address:colours:firstLine} may repeat; {@code char=sonic} (or tails, knuckles, tails_tails)
 * dumps a playable character's sheet and prints its animation scripts instead. Origin: Slay the
 * Robotnik, 2026-10-05.
 */
public final class SpriteSheetDump {
    private SpriteSheetDump() {
    }

    public static void main(String[] args) throws Exception {
        Rom rom = new Rom();
        rom.open(args[0]);
        GameId game = switch (args[1]) {
            case "s1" -> GameId.S1;
            case "s2" -> GameId.S2;
            default -> GameId.S3K;
        };
        Path out = Path.of(args[2]);
        int art = 0;
        int map = 0;
        int dplc = -1;
        int size = 0;
        int line = 0;
        int offset = 0;
        RomSpriteRequest.Compression comp = RomSpriteRequest.Compression.NEMESIS;
        RomSpriteRequest.DplcLayout layout = RomSpriteRequest.DplcLayout.OBJECT;
        List<int[]> palettes = new ArrayList<>();
        String character = null;
        for (int i = 3; i < args.length; i++) {
            String[] kv = args[i].split("=", 2);
            switch (kv[0]) {
                case "art" -> art = Integer.decode(kv[1]);
                case "map" -> map = Integer.decode(kv[1]);
                case "dplc" -> dplc = Integer.decode(kv[1]);
                case "size" -> size = Integer.decode(kv[1]);
                case "line" -> line = Integer.decode(kv[1]);
                case "offset" -> offset = Integer.decode(kv[1]);
                case "comp" -> comp = RomSpriteRequest.Compression.valueOf(kv[1]);
                case "char" -> character = kv[1];
                case "layout" -> layout = RomSpriteRequest.DplcLayout.valueOf(kv[1]);
                case "pal" -> {
                    String[] p = kv[1].split(":");
                    palettes.add(new int[] {Integer.decode(p[0]), Integer.decode(p[1]), Integer.decode(p[2])});
                }
                default -> throw new IllegalArgumentException("Unknown argument " + args[i]);
            }
        }
        com.openggf.tools.HeadlessGameBoot boot = null;
        SceneRomArt romArt;
        if (character != null) {
            // Character sheets come from the game module, which needs a booted session.
            boot = new com.openggf.tools.HeadlessGameBoot(320, 224, 320, 224);
            boot.boot(Path.of(args[0]), 0, 0);
            var module = com.openggf.game.GameServices.module();
            Object live = module.createGame(com.openggf.game.session.SessionManager.getCurrentWorldSession()
                    .getDataSource());
            romArt = SceneRomArtFactory.create(com.openggf.game.GameServices.rom().getRom(), game,
                    () -> (com.openggf.data.PlayerSpriteArtProvider) live, module::loadTailsTailArt);
        } else {
            romArt = SceneRomArtFactory.create(rom, game, () -> null, () -> null);
        }
        int[] palette = new int[64];
        for (int[] p : palettes) {
            int[] colors = romArt.palette(p[0], p[1]);
            System.arraycopy(colors, 0, palette, p[2] * 16, Math.min(colors.length, 64 - p[2] * 16));
        }
        SceneSpriteSet set;
        if (character != null) {
            // A playable character's sheet (char=sonic|tails|knuckles, or tails_tails for the tails object).
            set = character.endsWith("_tails") ? romArt.characterAccessory(character.replace("_tails", ""))
                    : romArt.character(character);
            for (int anim = 0; anim < 0x40; anim++) {
                int[] frames = set.animationFrames(anim);
                if (frames.length > 0) {
                    StringBuilder sb = new StringBuilder("anim 0x" + Integer.toHexString(anim) + " delay "
                            + set.animationDelay(anim) + ":");
                    for (int f : frames) {
                        sb.append(" 0x").append(Integer.toHexString(f));
                    }
                    System.out.println(sb);
                }
            }
        } else {
            var request = new RomSpriteRequest(art, comp, size, map, dplc, layout, line, offset);
            set = romArt.sprites(request, palette);
        }
        int cell = 0;
        for (int f = 0; f < set.frameCount(); f++) {
            SceneSprite s = set.frame(f);
            cell = Math.max(cell, Math.max(s.width(), s.height()));
        }
        cell += 12;
        int cols = Math.max(1, Math.min(8, set.frameCount()));
        int rows = (set.frameCount() + cols - 1) / cols;
        PixelImage sheet = PixelImage.blank(cols * cell, Math.max(1, rows) * cell);
        for (int y = 0; y < sheet.getHeight(); y++) {
            for (int x = 0; x < sheet.getWidth(); x++) {
                sheet.setRGB(x, y, ((x / 8 + y / 8) & 1) == 0 ? 0xFF404050 : 0xFF303040);
            }
        }
        for (int f = 0; f < set.frameCount(); f++) {
            SceneSprite s = set.frame(f);
            int cx = (f % cols) * cell;
            int cy = (f / cols) * cell;
            digits(sheet, f, cx + 2, cy + 2);
            for (int y = 0; y < s.height(); y++) {
                for (int x = 0; x < s.width(); x++) {
                    int px = s.image().pixel(x, y);
                    if ((px >>> 24) != 0) {
                        sheet.setRGB(cx + 6 + x, cy + 10 + y, px);
                    }
                }
            }
            // Origin marker.
            int ox = cx + 6 + s.originX();
            int oy = cy + 10 + s.originY();
            if (ox >= 0 && oy >= 0 && ox < sheet.getWidth() && oy < sheet.getHeight()) {
                sheet.setRGB(ox, oy, 0xFFFF00FF);
            }
        }
        PngCodec.write(out, sheet);
        System.out.println(set.frameCount() + " frames -> " + out);
        if (boot != null) {
            boot.close();
        }
    }

    private static void digits(PixelImage sheet, int value, int x, int y) {
        String[] font = {"111101101101111", "010110010010111", "111001111100111", "111001111001111",
                "101101111001001", "111100111001111", "111100111101111", "111001001010010",
                "111101111101111", "111101111001111"};
        String text = Integer.toString(value);
        for (int i = 0; i < text.length(); i++) {
            String g = font[text.charAt(i) - '0'];
            for (int k = 0; k < 15; k++) {
                if (g.charAt(k) == '1') {
                    sheet.setRGB(x + i * 4 + k % 3, y + k / 3, 0xFFFFFFFF);
                }
            }
        }
    }
}
