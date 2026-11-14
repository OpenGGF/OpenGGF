package com.openggf.game.sonic3k;

import com.openggf.data.Rom;
import com.openggf.game.CharacterPresentation.Image;
import com.openggf.game.presentation.CharacterNameComposer;
import com.openggf.game.presentation.HudNameFont;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.titlecard.Sonic3kTitleCardMappings;
import com.openggf.level.Pattern;
import com.openggf.util.PatternDecompressor;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Locked-on native letter inventories, read entirely from ROM-backed art banks. */
public final class Sonic3kCharacterFont {
    private Sonic3kCharacterFont() { }

    public static Map<Character, Image> hud(Rom rom) throws IOException {
        var glyphs = new LinkedHashMap<Character, Image>();
        HudNameFont.collect(glyphs, PatternDecompressor.nemesis(rom,
                Sonic3kConstants.ART_NEM_SONIC_LIFE_ICON_ADDR), "SONIC", 0, 7, 14, 21, 25, 32);
        HudNameFont.collect(glyphs, PatternDecompressor.nemesis(rom, 0x18A110),
                "MILES", 0, 9, 13, 19, 25, 32);
        HudNameFont.collect(glyphs, PatternDecompressor.nemesis(rom,
                Sonic3kConstants.ART_NEM_TAILS_LIFE_ICON_ADDR), "TAILS", 0, 8, 15, 19, 25, 32);
        // Knuckles' native K.T.E abbreviation contributes K; T and E are above.
        HudNameFont.collect(glyphs, PatternDecompressor.nemesis(rom,
                Sonic3kConstants.ART_NEM_KNUCKLES_LIFE_ICON_ADDR), "K", 0, 8);
        return Map.copyOf(glyphs);
    }

    /** Results name banks share a column-major two-tile-high strip. */
    public static Map<Character, Image> results(Rom rom) throws IOException {
        var glyphs = new LinkedHashMap<Character, Image>();
        collectResults(glyphs, rom, Sonic3kConstants.ART_KOSM_RESULTS_SONIC_ADDR,
                "SONIC", 16, 16, 16, 8, 16);
        collectResults(glyphs, rom, Sonic3kConstants.ART_KOSM_RESULTS_MILES_ADDR,
                "MILES", 18, 8, 12, 14, 16);
        collectResults(glyphs, rom, Sonic3kConstants.ART_KOSM_RESULTS_TAILS_ADDR,
                "TAILS", 12, 16, 8, 12, 16);
        collectResults(glyphs, rom, Sonic3kConstants.ART_KOSM_RESULTS_KNUCKLES_ADDR,
                "KNUCKLES", 16, 16, 16, 16, 16, 8, 16, 16);
        return Map.copyOf(glyphs);
    }

    private static void collectResults(Map<Character, Image> glyphs, Rom rom, int address,
            String name, int... widths) throws IOException {
        Pattern[] tiles = PatternDecompressor.kosinskiModuled(rom, address);
        int x = 0;
        for (int i = 0; i < name.length(); i++) {
            glyphs.putIfAbsent(name.charAt(i), CharacterNameComposer.extract(tiles, 0,
                    tiles.length / 2, 2, x, 0, widths[i], 16));
            x += widths[i];
        }
    }

    private static final String[] ZONE_LETTERS = {
            "A N G E L I S L A N D", "H Y D R O C I TY", "M A R B L E G A R D E N",
            "C A R N I V A L N I GH T", "F L Y I N G B A T T E R Y", "I C E C A P",
            "L A U N CH B A S E", "M U S H R O O M HIL L", "S A N D O P O L I S",
            "L A V A R E E F", "S K Y S A N C TU A R Y", "D E A T H E G G",
            "T H E D O O M S D A Y", "HI D D E N P A L A C E"};

    /** Union of all zone sets; repeated letters retain their first native instance. */
    public static Map<Character, Image> title(Rom rom) throws IOException {
        var glyphs = new LinkedHashMap<Character, Image>();
        for (int zone = 0; zone < ZONE_LETTERS.length; zone++)
            title(rom, zone).forEach(glyphs::putIfAbsent);
        return Map.copyOf(glyphs);
    }

    /** One title-card letter inventory, including letters shared with the ZONE bank. */
    public static Map<Character, Image> title(Rom rom, int zone) throws IOException {
        if (zone < 0 || zone >= ZONE_LETTERS.length) throw new IllegalArgumentException("Invalid title art index");
        var glyphs = new LinkedHashMap<Character, Image>();
        Pattern[] bank = new Pattern[0x100];
        Pattern[] redAct = PatternDecompressor.kosinskiModuled(rom,
                Sonic3kConstants.ART_KOSM_TITLE_CARD_RED_ACT_ADDR);
        System.arraycopy(redAct, 0, bank, 0, redAct.length);
        Pattern[] common = PatternDecompressor.kosinskiModuled(rom,
                Sonic3kConstants.ART_KOSM_TITLE_CARD_S3K_ZONE_ADDR);
        System.arraycopy(common, 0, bank, 0x10, common.length);
        Pattern[] local = PatternDecompressor.kosinskiModuled(rom,
                Sonic3kConstants.TITLE_CARD_ZONE_ART_ADDRS[zone]);
        System.arraycopy(local, 0, bank, 0x4D, local.length);
        var pieces = Sonic3kTitleCardMappings.getFrame(zone + 4);
        String[] tokens = ZONE_LETTERS[zone].split(" ");
        if (pieces.length != tokens.length) throw new IOException("Native title inventory mismatch");
        for (int i = 0; i < pieces.length; i++) {
            var piece = pieces[i];
            String token = tokens[i];
            int x = 0;
            for (char letter : token.toCharArray()) {
                int width = token.length() == 1 ? piece.widthTiles() * 8
                        : letter == 'I' || letter == 'L' ? 8 : 16;
                glyphs.putIfAbsent(letter, CharacterNameComposer.extract(bank,
                        piece.tileIndex() - 0x500, piece.widthTiles(), piece.heightTiles(),
                        x, 0, width, piece.heightTiles() * 8));
                x += width;
            }
        }
        // ZONE is present on every card, independently of the zone-name pieces.
        for (int i = 0; i < 4; i++) {
            var piece = Sonic3kTitleCardMappings.getFrame(3)[i];
            glyphs.putIfAbsent("ZONE".charAt(i), CharacterNameComposer.extract(bank,
                    piece.tileIndex() - 0x500, piece.widthTiles(), piece.heightTiles(),
                    0, 0, piece.widthTiles() * 8, piece.heightTiles() * 8));
        }
        return Map.copyOf(glyphs);
    }
}
