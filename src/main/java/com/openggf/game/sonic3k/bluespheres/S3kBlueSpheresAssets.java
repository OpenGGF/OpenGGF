package com.openggf.game.sonic3k.bluespheres;

import com.openggf.data.RomByteReader;
import com.openggf.data.compression.EnigmaReader;
import com.openggf.data.compression.KosinskiReader;
import com.openggf.data.compression.NemesisReader;
import com.openggf.game.sonic3k.S3kSpriteDataLoader;
import com.openggf.level.Pattern;
import com.openggf.level.render.SpriteMappingFrame;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.List;
import java.util.Objects;

/**
 * ROM data for the Blue Spheres title and results screens, read from the S&amp;K
 * half of the locked-on image. Addresses come from {@code sonic3k.lst}; every
 * byte drawn comes from here.
 */
final class S3kBlueSpheresAssets {
    // Art (VRAM destinations are the ArtTile_BlueSphere_* constants below).
    static final int ART_NEM_CREDITS_TEXT = 0xDDE34;
    static final int ART_NEM_SK_LOGO = 0x189C7C;
    static final int ART_KOS_CHAR = 0x187D1C;
    static final int ART_KOS_CHAR_SPRITE = 0x188F1E;
    static final int ART_KOS_NORMAL_TEXT = 0x1897DE;
    static final int ART_KOS_RESULTS_GENERAL = 0x15BAC0;
    static final int ART_KOS_RESULTS_TK_ICONS = 0x15C130;
    static final int ART_UNC_SONIC = 0x100000;
    static final int ART_UNC_KNUCKLES = 0x1200E0;
    // PLC_SphereResults and the showcase's Kosinski art (loc_4DC1A).
    static final int ART_NEM_ROBOTNIK_SHIP = 0xD771E;
    static final int ART_NEM_CHICKEN = 0x193308;
    static final int ART_NEM_SQUIRREL = 0x1935A8;
    static final int ART_NEM_RABBIT = 0x193706;
    static final int ART_NEM_BLUE_SPHERE_TAILS = 0x189F62;
    static final int ART_KOSM_SK_POSE_BANNER = 0x16416C;
    static final int ART_KOSM_AND_KNUCKLES = 0xDE362;
    static final int ART_KOSM_ENDING_MASTER_EMERALD = 0x17FCBA;
    static final int ART_KOSM_ROBOTNIK_SMUG = 0x163246;
    static final int ART_KOSM_EGG_ROBO_HEAD = 0x15FDDC;

    // Plane maps.
    static final int MAP_ENI_TITLE_FG = 0x187CB0;
    static final int MAP_ENI_TITLE_BG = 0x189C4E;

    // Sprite mappings and animation.
    static final int MAP_TEXT = 0x4D7BE;
    static final int MAP_NUMBERS = 0x4D8AE;
    static final int MAP_ICON = 0x4D926;
    static final int MAP_CHAR_SPRITE = 0x188E7C;
    static final int MAP_COPYRIGHT = 0x4DA68;
    static final int MAP_RESULTS_EMERALD = 0x4E316;
    static final int MAP_SONIC = 0x146620;
    static final int MAP_KNUCKLES = 0x14A8D6;
    static final int DPLC_SONIC = 0x148182;
    static final int DPLC_KNUCKLES = 0x14BD0A;
    static final int ANI_CHAR_SPRITE = 0x4DA04;
    static final int ANI_SONIC = 0x12AA6;
    static final int ANI_KNUCKLES = 0x17EF4;

    // The perfect screen's difficulty objects (Difficulty_Sprites, sonic3k.asm:102069-102081).
    static final int MAP_BS_TAILS_POSE = 0x4E2A2;
    static final int MAP_SUPER_SONIC = 0x146816;
    static final int DPLC_SUPER_SONIC = 0x148378;
    static final int ART_UNC_SONIC_EXTRA = 0x140060;
    static final int ANI_SUPER_SONIC = 0x12C3A;
    static final int MAP_ENDING_EYECATCH_EGGMAN = 0x60A14;
    /** {@code AniRaw_602C6}: Obj_Difficulty_Eggman's raw script. */
    static final int ANI_RAW_EGGMAN = 0x602C6;
    static final int MAP_ROBOTNIK_SHIP = 0x6820C;
    static final int ANI_RAW_EGG_ROBO_HEAD = 0x681D0;
    static final int CHILD1_DIFFICULTY_SHIP_HEAD = 0x681C4;
    static final int MAP_MECHA_SONIC = 0x1853AA;
    static final int ART_UNC_MECHA_SONIC = 0x175A9E;
    static final int DPLC_MECHA_SONIC = 0x185852;
    /** {@code ObjSlot_MechaSonic}: SetUp_ObjAttributesSlotted's table. */
    static final int OBJSLOT_MECHA_SONIC = 0x7D3EA;
    static final int MAP_ANIMALS_1 = 0x2CEBA;
    static final int MAP_ANIMALS_2 = 0x2CED8;
    static final int MAP_ANIMALS_5 = 0x2CF32;
    /** {@code word_2CD5E}/{@code word_2CDDA}/{@code word_2CE66}: animal (x, y) per child. */
    static final int CHICKEN_POSITIONS = 0x2CD5E;
    static final int SQUIRREL_POSITIONS = 0x2CDDA;
    static final int RABBIT_POSITIONS = 0x2CE66;
    static final int MAP_SSZ_MASTER_EMERALD = 0x7D712;
    /** {@code word_7D03E}: the Master Emerald's y by subtype. */
    static final int MASTER_EMERALD_Y = 0x7D03E;
    static final int MAP_SK_POSE_BANNER = 0x60644;
    static final int MAP_AND_KNUCKLES = 0x60938;
    /** {@code byte_5F520}: the "&amp; Knuckles" trademark's (dx, dy) by subtype. */
    static final int TM_OFFSETS = 0x5F520;
    static final int PAL_CYCLE_SUPER_SONIC = 0x398E;
    /** ArtTile_MechaSonic / ArtTile_SSZMasterEmerald (sonic3k.constants.asm:1340-1343). */
    static final int TILE_MECHA_SONIC = 0x3F4;
    static final int TILE_SSZ_MASTER_EMERALD = 0x1EE;

    // Palettes and text.
    static final int PAL_TITLE_1 = 0x4CB36;
    static final int PAL_TITLE_2 = 0x4CBB6;
    static final int PAL_RESULTS_012 = 0x4DE08;
    static final int PAL_KNUCKLES = 0xA8AFC;
    static final int PAL_SONIC_TAILS = 0xA8A3C;
    static final int PAL_RESULTS_3 = 0x4DE68;
    static final int PAL_ENDING_SK_LOGO = 0x60ABA;
    static final int CREDITS_GLYPHS = 0x5B566;
    /** {@code word_4D7A6} / {@code word_4D7B2}: cursor icon targets per option. */
    static final int ICON_TARGETS_LEFT = 0x4D7A6;
    static final int ICON_TARGETS_RIGHT = 0x4D7B2;
    /** {@code word_4D976}: the four character sprites' positions. */
    static final int CHAR_POSITIONS = 0x4D976;
    /** {@code word_4D2DC}: code digit x positions. */
    static final int CODE_DIGIT_X = 0x4D2DC;
    /** {@code word_4DEDC}: plane B destinations of the results backdrop. */
    static final int RESULTS_BACKDROP_TARGETS = 0x4DEDC;
    /** {@code GetSineCosine}'s table. */
    static final int SINE_TABLE = 0x1D64;

    // ArtTile_* (sonic3k.constants.asm:1097-1102, 1406).
    static final int TILE_BG = 0x197;
    static final int TILE_EMERALDS = 0x1DC;
    static final int TILE_MISC = 0x54E;
    static final int TILE_TITLE_TEXT = 0x5BF;
    static final int TILE_PLAYER_1 = 0x680;
    static final int TILE_PLAYER_2 = 0x6A0;
    static final int TILE_DIFFICULTY = 0x180;
    static final int TILE_TAILS = 0x7A0;

    /** VRAM_Plane_A_Name_Table / VRAM_Plane_B_Name_Table: 64x32 cells ($9001). */
    static final int PLANE_WIDTH = 64;
    static final int PLANE_HEIGHT = 32;

    private final RomByteReader rom;

    S3kBlueSpheresAssets(RomByteReader rom) {
        this.rom = Objects.requireNonNull(rom, "rom");
    }

    RomByteReader rom() {
        return rom;
    }

    byte[] nemesis(int address) throws IOException {
        return NemesisReader.decompress(channel(address));
    }

    byte[] kosinski(int address) throws IOException {
        return KosinskiReader.decompress(channel(address));
    }

    /** Enigma map words with {@code artTile} added, as {@code Eni_Decomp} leaves them. */
    int[] enigma(int address, int artTile) throws IOException {
        byte[] bytes = EnigmaReader.decompress(channel(address), artTile);
        int[] words = new int[bytes.length / 2];
        for (int i = 0; i < words.length; i++) {
            words[i] = ((bytes[i * 2] & 0xFF) << 8) | (bytes[i * 2 + 1] & 0xFF);
        }
        return words;
    }

    List<SpriteMappingFrame> mappings(int address) {
        return S3kSpriteDataLoader.loadMappingFrames(rom, address);
    }

    /** {@code bytesToWcnt($80)} palette copies: 64 colour words. */
    int[] paletteWords(int address, int count) {
        int[] words = new int[count];
        for (int i = 0; i < count; i++) words[i] = rom.readU16BE(address + i * 2);
        return words;
    }

    int word(int address) {
        return rom.readU16BE(address);
    }

    int signedWord(int address) {
        return rom.readS16BE(address);
    }

    int u8(int address) {
        return rom.readU8(address);
    }

    /** Writes {@code data} into the VRAM pattern bank at {@code tile}. */
    static void load(Pattern[] vram, int tile, byte[] data) {
        int count = data.length / Pattern.PATTERN_SIZE_IN_ROM;
        byte[] cell = new byte[Pattern.PATTERN_SIZE_IN_ROM];
        for (int i = 0; i < count && tile + i < vram.length; i++) {
            System.arraycopy(data, i * cell.length, cell, 0, cell.length);
            Pattern pattern = new Pattern();
            pattern.fromSegaFormat(cell);
            vram[tile + i] = pattern;
        }
    }

    static Pattern[] emptyVram() {
        Pattern[] vram = new Pattern[0x800];
        for (int i = 0; i < vram.length; i++) vram[i] = new Pattern();
        return vram;
    }

    private ReadableByteChannel channel(int address) {
        int length = Math.min(0x10000, rom.size() - address);
        return Channels.newChannel(new ByteArrayInputStream(rom.slice(address, length)));
    }
}
