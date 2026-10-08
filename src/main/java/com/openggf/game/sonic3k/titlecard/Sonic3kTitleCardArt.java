package com.openggf.game.sonic3k.titlecard;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.titlecard.TitleCardMappings;
import com.openggf.level.Pattern;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.level.render.ZonePictureSource;
import com.openggf.util.PatternDecompressor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A zone and act's title card as presentation sprites for mod scenes
 * ({@code SceneRomArt.titleCard}). The four KosM blocks the card's creation queues
 * ({@link Sonic3kTitleCardManager}: RedAct at VRAM $500, S3KZone over it at $510, the act number
 * at $53D and the zone's letters at $54D, in that order) are decompressed into a private copy of
 * those tiles, and the four elements' frames from {@code Map - Title Card.asm}
 * ({@link Sonic3kTitleCardMappings}: banner, zone name, "ZONE", act) are re-based on $500. The
 * palette is {@code Pal_SonicTails}: every card piece selects line 0, the player's line, which
 * holds Sonic's colours when Sonic or Tails plays. Reads only the ROM; it never touches the
 * running title card, graphics, session or settings. Engine-internal.
 */
public final class Sonic3kTitleCardArt {
    /** The card's VRAM window: $500-$5FF. */
    private static final int TILES = 0x100;

    private Sonic3kTitleCardArt() {
    }

    /**
     * Whether the ROM has a card for this act: zones 0-12 (acts 0 and 1), zone 22 (the LRZ boss
     * act and Hidden Palace) and zone 23 act 0 (the DEZ boss act), the {@code Apparent_zone}
     * values {@code TitleCard_LevelGfx} and {@code Obj_TitleCardName} name.
     */
    public static boolean supports(int zone, int act) {
        if (act < 0 || act > 1) {
            return false;
        }
        return (zone >= 0 && zone <= 12) || zone == 22 || (zone == 23 && act == 0);
    }

    /** The card's tiles, frames (banner, zone name, "ZONE", act) and palette, or null when unsupported. */
    public static ZonePictureSource.Sprites build(Rom rom, int zone, int act) throws IOException {
        if (!supports(zone, act)) {
            return null;
        }
        Pattern[] tiles = new Pattern[TILES];
        Pattern empty = new Pattern();
        Arrays.fill(tiles, empty);
        int base = Sonic3kConstants.VRAM_TITLE_CARD_BASE;
        place(tiles, PatternDecompressor.kosinskiModuled(rom, Sonic3kConstants.ART_KOSM_TITLE_CARD_RED_ACT_ADDR), 0);
        place(tiles, PatternDecompressor.kosinskiModuled(rom, Sonic3kConstants.ART_KOSM_TITLE_CARD_S3K_ZONE_ADDR),
                Sonic3kConstants.VRAM_TITLE_CARD_ZONE_TEXT - base);
        int number = act == 0 ? Sonic3kConstants.ART_KOSM_TITLE_CARD_NUM1_ADDR
                : Sonic3kConstants.ART_KOSM_TITLE_CARD_NUM2_ADDR;
        place(tiles, PatternDecompressor.kosinskiModuled(rom, number), Sonic3kConstants.VRAM_TITLE_CARD_ACT_NUM - base);
        int letters = Sonic3kTitleCardMappings.zoneArtIndex(zone, act);
        place(tiles, PatternDecompressor.kosinskiModuled(rom, Sonic3kConstants.TITLE_CARD_ZONE_ART_ADDRS[letters]),
                Sonic3kConstants.VRAM_TITLE_CARD_ZONE_ART - base);
        List<SpriteMappingFrame> frames = List.of(
                frame(Sonic3kTitleCardMappings.FRAME_BANNER, base),
                frame(Sonic3kTitleCardMappings.getZoneFrame(zone, act), base),
                frame(Sonic3kTitleCardMappings.FRAME_ZONE, base),
                frame(Sonic3kTitleCardMappings.FRAME_ACT, base));
        byte[] words = new byte[32];
        RomByteReader reader = RomByteReader.fromRom(rom);
        for (int i = 0; i < words.length; i++) {
            words[i] = (byte) reader.readU8(Sonic3kConstants.SONIC_PALETTE_ADDR + i);
        }
        return new ZonePictureSource.Sprites(tiles, frames, words);
    }

    private static void place(Pattern[] tiles, Pattern[] art, int destination) {
        for (int i = 0; i < art.length && destination + i < tiles.length; i++) {
            tiles[destination + i] = art[i];
        }
    }

    private static SpriteMappingFrame frame(int frameIndex, int base) {
        List<SpriteMappingPiece> pieces = new ArrayList<>();
        for (TitleCardMappings.SpritePiece p : Sonic3kTitleCardMappings.getFrame(frameIndex)) {
            pieces.add(new SpriteMappingPiece(p.xOffset(), p.yOffset(), p.widthTiles(), p.heightTiles(),
                    p.tileIndex() - base, p.hFlip(), p.vFlip(), p.paletteIndex()));
        }
        return new SpriteMappingFrame(pieces);
    }
}
