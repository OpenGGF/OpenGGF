package sitarhero;

import com.openggf.mods.scene.*;
import java.util.ArrayList;
import java.util.List;

/** ROM-backed cosmetic actors; instrument props are new pixel art drawn by this mod. */
final class PerformerArt {
    private record Layer(SceneSpriteSet set, int frame, int x, int y) { }
    private final List<Layer> layers = new ArrayList<>();
    private SceneSpriteSet character;
    private SceneSpriteSet tails;

    PerformerArt(SceneRomArt rom, String performer) {
        switch (performer) {
            case "sonic", "tails", "knuckles" -> {
                character = rom.character(performer);
                if (performer.equals("tails")) tails = rom.characterAccessory("tails");
            }
            case "robotnik" -> robotnik(rom);
            case "silver-sonic" -> {
                int[] palette = s2Palette(rom, 0x12); // PalPtr_DEZ, native Silver Sonic line 1
                layers.add(new Layer(rom.sprites(RomSpriteRequest.of(0x8BE12,
                        RomSpriteRequest.Compression.NEMESIS, 0x39E68, 1), palette), 0, 0, 0));
            }
            case "mecha-sonic" -> {
                int[] palette = s3Palette(rom, 0x0A973C);
                System.arraycopy(rom.palette(0x07D850, 16), 0, palette, 16, 16); // Pal_SSZGHZMisc
                layers.add(new Layer(rom.sprites(RomSpriteRequest.streamed(0x175A9E, 0x56E0,
                        0x1853AA, 0x185852, RomSpriteRequest.DplcLayout.OBJECT, 1), palette), 0, 0, 0));
            }
            case "egg-robo" -> {
                SceneSpriteSet set = rom.sprites(RomSpriteRequest.of(0x17B17E,
                        RomSpriteRequest.Compression.KOSINSKI_MODULED, 0x184F34, 0), s3Palette(rom, 0x0A973C));
                // Obj_EggRobo's head, legs and body are separate native mapping frames.
                layers.add(new Layer(set, 2, -0x1C, -4));
                layers.add(new Layer(set, 4, -0xC, 0x1C));
                layers.add(new Layer(set, 1, 0, 0));
            }
            default -> throw new IllegalArgumentException("Unknown performer: " + performer);
        }
    }

    private void robotnik(SceneRomArt rom) {
        switch (rom.gameId()) {
            case "s1" -> layers.add(new Layer(rom.sprites(RomSpriteRequest.of(0x5E4CE,
                    RomSpriteRequest.Compression.NEMESIS, 0x01A1E4, 0), rom.characterPalette("sonic")), 0, 0, 0));
            case "s2" -> {
                int[] palette = s2Palette(rom, 0x0A); // PalPtr_WFZ + Pal_SonicTails
                // WFZ Robotnik composes three native banks at VRAM $500/$518/$564.
                int[] art = {0x8E886, 0x8EA5A, 0x8EE52};
                int[] offset = {0x500, 0x518, 0x564};
                for (int i = 0; i < art.length; i++) layers.add(new Layer(rom.sprites(
                        RomSpriteRequest.of(art[i], RomSpriteRequest.Compression.NEMESIS, 0x3D0EE, 0)
                                .withTileOffset(offset[i]), palette), 0, 0, 0));
            }
            default -> {
                SceneSpriteSet ship = rom.sprites(RomSpriteRequest.of(0x0D771E,
                        RomSpriteRequest.Compression.NEMESIS, 0x06820C, 0), s3Palette(rom, 0x0A8B7C));
                layers.add(new Layer(ship, 5, 0, 8));
                layers.add(new Layer(ship, 0, 0, -8));
            }
        }
    }

    private static int[] s3Palette(SceneRomArt rom, int zone) {
        int[] palette = new int[64];
        System.arraycopy(rom.palette(0x0A8A3C, 16), 0, palette, 0, 16);
        System.arraycopy(rom.palette(zone, 48), 0, palette, 16, 48);
        return palette;
    }
    private static int[] s2Palette(SceneRomArt rom, int id) {
        byte[] pointer = rom.read(0x2782 + id * 8, 4);
        int address = (pointer[1] & 255) << 16 | (pointer[2] & 255) << 8 | pointer[3] & 255;
        int[] palette = new int[64];
        System.arraycopy(rom.palette(0x29E2, 16), 0, palette, 0, 16);
        System.arraycopy(rom.palette(address, 48), 0, palette, 16, 48);
        return palette;
    }

    void draw(SceneCanvas c, int x, int y, long ticks, String role, boolean performing) {
        int bob = performing && ticks % 20 < 3 ? -2 : 0;
        SceneDraw style = SceneDraw.plain();
        // Obj_Tails_Tail_AniSelection: native standing tail frames $22..$26
        // share the body's origin; mapping frame zero is a transparent placeholder.
        if (tails != null) c.draw(tails.frame(0x22 + (int) (ticks / 8 % 5)), x, y + bob, style);
        if (character != null) {
            int[] frames = character.animationFrames(5);
            int frame = frames.length == 0 ? 0 : frames[(int) (ticks / 12 % frames.length)];
            c.draw(character.frame(frame), x, y + bob, style);
        }
        for (Layer layer : layers) c.draw(layer.set().frame(layer.frame()), x + layer.x(), y + bob + layer.y(), style);
        if (performing) instrument(c, x, y + bob, role);
    }

    private void instrument(SceneCanvas c, int x, int y, String role) {
        switch (role) {
            case "Bongos" -> {
                for (int i = 0; i < 2; i++) {
                    c.fill(x - 24 + i * 25, y + 4, 22, 17, 0xFF8D4424);
                    c.fill(x - 24 + i * 25, y + 3, 22, 5, 0xFFFFD090);
                    c.fill(x - 22 + i * 25, y + 9, 3, 10, 0xFFE8A04B);
                }
                c.fill(x - 5, y + 27, 10, 4, 0xFF555575);
            }
            case "Synth" -> {
                c.fill(x - 30, y + 3, 60, 15, 0xFF283850);
                for (int i = 0; i < 8; i++) {
                    c.fill(x - 27 + i * 7, y + 6, 6, 9, 0xFFFFF4DB);
                    if (i % 3 != 0) c.fill(x - 24 + i * 7, y + 5, 3, 6, 0xFF141C32);
                }
                c.fill(x - 25, y + 18, 3, 15, 0xFF8DA0BC); c.fill(x + 22, y + 18, 3, 15, 0xFF8DA0BC);
            }
            case "Harp" -> {
                c.fill(x + 8, y - 20, 5, 46, 0xFFE6A329); c.fill(x + 8, y + 23, 29, 5, 0xFFE6A329);
                for (int i = 0; i < 6; i++) {
                    int height = 39 - i * 5;
                    c.fill(x + 14 + i * 4, y + 22 - height, 1, height, 0xFFFFF0AA);
                    c.fill(x + 12 + i * 4, y + 18 - height, 5, 4, 0xFFE6A329);
                }
            }
            default -> {
                c.fill(x - 8, y, 19, 18, 0xFF743E23); c.fill(x - 6, y + 2, 15, 14, 0xFFE8A447);
                for (int i = 0; i < 7; i++) c.fill(x + 5 + i * 3, y + 4 - i * 2, 5, 5, 0xFFFFD277);
                c.fill(x - 1, y + 6, 5, 5, 0xFF3B2823);
                c.fill(x - 5, y + 17, 14, 2, 0xFFFFDC86);
            }
        }
    }
}
