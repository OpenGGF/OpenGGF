package com.openggf.game.sonic3k.competition;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.data.RomFingerprint;
import com.openggf.data.RomIdentity;
import com.openggf.data.RomIdentityTable;

import java.io.IOException;

/** Cartridge-bound competition resources. No address table is written globally.
 * Original S3 Competition_Menu (s3.asm:11589-11670), Character_Speeds
 * (s3.asm:116485-116489), and LevelLoadBlock (s3.asm:115691-115733).
 * Asset-window relocation applies only to references into the original S3 chip;
 * program-owned tables below it have explicit independent bindings.
 */
public enum S3kCompetitionResources {
    LOCKED_ON, ORIGINAL_S3;

    /** Product admission requires an original verified cartridge, not a header guess. */
    public static S3kCompetitionResources identify(Rom rom) throws IOException {
        var reader = RomByteReader.fromRom(rom);
        var fingerprint = RomFingerprint.of(reader.slice(0, reader.size()));
        if (RomIdentityTable.verify(RomIdentity.S3, fingerprint).isPresent()) return ORIGINAL_S3;
        if (RomIdentityTable.verify(RomIdentity.S3K, fingerprint).isPresent()) return LOCKED_ON;
        throw new IOException("Native Competition requires verified original Sonic 3 or Sonic 3 & Knuckles bytes");
    }

    /** Resolves a named resource reference in the existing locked-on port.
     * Returned table contents/pointers already belong to the selected cartridge;
     * callers must not relocate those contents a second time.
     */
    public int at(int lockedOnReference) {
        if (this == LOCKED_ON) return lockedOnReference;
        if (lockedOnReference >= 0x200000 && lockedOnReference < 0x400000)
            return lockedOnReference - 0x200000;
        return switch (lockedOnReference) {
            case 0x1D64 -> 0x1FEC; // SineTable
            case 0x34CC -> 0x2FA2; // AnPal_PalBPZ_1
            case 0x34DE -> 0x2FB4; // AnPal_PalBPZ_2
            case 0x355C -> 0x3032; // AnPal_PalCGZ
            case 0x35AC -> 0x3082; // AnPal_PalEMZ_1
            case 0x35E8 -> 0x30BE; // AnPal_PalEMZ_2
            case 0x61B0 -> 0x4878;
            case 0x61BC -> 0x4884;
            case 0x6C0C -> 0x5092; // Pal_Level_2P
            case 0xAADC -> 0x97AC; // Obj_Competition_AADC / Obj_Competition_97AC
            case 0xAB00 -> 0x97D0; // Obj_Competition_AB00 / Obj_Competition_97D0
            case 0xAB48 -> 0x9878; // ObjDat_AB48 / ObjDat_9878
            case 0xAF24 -> 0x9D8E; // Obj_Competition_ZoneSelect
            case 0xB0F4 -> 0x9F58; // VRAMDatList_CompetitionLevelSelect
            case 0xB1F0 -> 0xA054; // ObjDat_CompetitionMatchRace
            case 0xB282 -> 0xA0E6; // ObjDat_CompetitionTimeAttack
            case 0xB4F6 -> 0xA38C; // Obj_Competition_StaticSprite
            case 0xB508 -> 0xA39E; // Obj_Competition_2PSelect
            case 0xB52C -> 0xA3C2; // Obj_Competition_1PSelect
            case 0xB64E -> 0xA4E4; // Obj_CompetitionPlayerSprite2P
            case 0xB656 -> 0xA4EC; // Obj_CompetitionPlayerSprite
            case 0xB6A6 -> 0xA53C; // Obj_Competition_PRESSSTART
            case 0xB6C8 -> 0xA65E; // ObjDat_B6C8 / ObjDat_A65E
            case 0xB9A6 -> 0xABE6; // Obj_Competition_B9A6 / Obj_Competition_ABE6
            case 0xB9CA -> 0xAC0A; // Obj_Competition_B9CA / Obj_Competition_AC0A
            case 0xBB4A -> 0xADA4; // ObjDat_BB4A / ObjDat_ADA4
            case 0xBBAC -> 0xAE06; // VRAMDatList_BBAC / VRAMDatList_AE06
            case 0xBEF8 -> 0xB20C; // Obj_Competition_BEF8 / Obj_Competition_B20C
            case 0xBF58 -> 0xB28C; // ObjDat_BF58 / ObjDat_B28C
            case 0xBF96 -> 0xB2CA; // VRAMDatList_BF96 / VRAMDatList_B2CA
            case 0xBFF2 -> 0xB3D6; // CompTimeAttack_LevelNameMaps
            case 0x12F84 -> 0x13CDA; // AniSonic2P
            case 0x12FCC -> 0x13D22;
            case 0x15F60 -> 0x16A3C; // AniTails2P
            case 0x162A0 -> 0x16D58;
            case 0x162C2 -> 0x16D7A; // AniTails2P_Tail
            case 0x190F2 -> 0x17E3E; // Ani_DashDust2P
            case 0x1910C -> 0x17E58; // Map_DashDust2P
            case 0x1A992 -> 0x190D6; // Ani_RingSparkle
            case 0x1A99A -> 0x190DE; // Map_Ring
            case 0x1BDA6 -> 0x1A2D4;
            case 0x202A6 -> 0x1DC1A;
            case 0x20358 -> 0x1DC94;
            case 0x23722 -> 0x21DB0; // Ani_Spring
            case 0x23904 -> 0x21F92; // Map_2PSpring
            case 0x23F4A -> 0x225D8; // Map_2PRetractingSpring
            case 0x27778 -> 0x26AB2;
            case 0x27838 -> 0x26B72; // AnimateTiles_NULL
            case 0x283E2 -> 0x274D0; // AnimateTiles_DPZ
            case 0x28446 -> 0x27534;
            case 0x286E4 -> 0x275EC; // AnimateTiles_DoAniPLC
            case 0x2F26A -> 0x2DC04; // Map_Results
            case 0x35BBC -> 0x3514E; // Map_CGZBladePlatform
            case 0x35D5A -> 0x352EC; // Map_BPZElephantBlock
            case 0x35E74 -> 0x35406; // Ani_BPZBalloon
            case 0x35E9A -> 0x3542C; // Map_BPZBalloon
            case 0x36046 -> 0x355D8; // Ani_DPZDissolvingSandBar
            case 0x3609A -> 0x3562C; // Map_DPZDissolvingSandBar
            case 0x36350 -> 0x358E2; // Map_DPZButton
            case 0x36A58 -> 0x35FEA; // Ani_2PItem
            case 0x36AA6 -> 0x36038; // Map_2PItem
            case 0x36FDA -> 0x3656C;
            case 0x36FF2 -> 0x36584; // Ani_2PGoalMarker
            case 0x36FFA -> 0x3658C; // Map_2PGoalMarker
            case 0x370D6 -> 0x36668; // Map_2PLapNumbers
            case 0x3750E -> 0x36AA0; // Ani_2PNeonDisplay
            case 0x375FA -> 0x36B8C; // Map_2PNeonDisplay
            case 0x37E2A -> 0x373BC;
            case 0x37E8E -> 0x37420;
            case 0x37F6A -> 0x374FC; // Map_2PItemIcon
            case 0x380C6 -> 0x37658; // Map_2PPosition
            case 0x38252 -> 0x377E4; // Map_EMZDripper
            case 0x5D788 -> 0x40268; // ArtNem_ContinueDigits
            case 0xA8A3C -> 0x8C234; // Pal_SonicTails
            case 0xA99DC -> 0x8CB74; // Pal_ALZ
            case 0xA9A3C -> 0x8CBD4; // Pal_BPZ
            case 0xA9A9C -> 0x8CC34; // Pal_DPZ
            case 0xA9AFC -> 0x8CC94; // Pal_CGZ
            case 0xA9B5C -> 0x8CCF4; // Pal_EMZ
            case 0x15BAC0 -> 0x19B3E6; // ArtKos_SSResultsGeneral
            case 0x15C130 -> 0x19BA66; // ArtKos_SSResultsTKIcons
            case 0x1E3C00 -> 0x5E000; // Character_Speeds
            default -> throw new IllegalArgumentException("Unbound original-S3 competition resource $"
                    + Integer.toHexString(lockedOnReference));
        };
    }

    /** Recognize declarative native menu object routines; no code is executed. */
    int menuActor(int pointer) {
        if (this == LOCKED_ON) return pointer;
        for (int role : new int[]{0xAADC,0xAB00,0xAF24,0xB4F6,0xB508,0xB52C,
                0xB64E,0xB656,0xB6A6,0xB9A6,0xB9CA,0xBEF8})
            if (at(role) == pointer) return role;
        throw new IllegalArgumentException("Unknown original-S3 competition menu actor $" + Integer.toHexString(pointer));
    }

    public int starts() { return this == ORIGINAL_S3 ? 0x5E018 : com.openggf.game.sonic3k.constants.Sonic3kConstants.SONIC_START_LOCATIONS_ADDR; }
    public int sizes() { return this == ORIGINAL_S3 ? 0x1A1F4 : com.openggf.game.sonic3k.constants.Sonic3kConstants.LEVEL_SIZES_ADDR; }
    public int objects() { return this == ORIGINAL_S3 ? 0x5E0D8 : com.openggf.game.sonic3k.constants.Sonic3kConstants.SPRITE_LOC_PTRS_ADDR; }
    public int layouts() { return this == ORIGINAL_S3 ? 0x81360 : com.openggf.game.sonic3k.constants.Sonic3kConstants.LEVEL_PTRS_ADDR; }
    public int defaultRecords() { return this == ORIGINAL_S3 ? 0xB6B0 : 0xC262; }
    public byte[] bytes(Rom rom, int reference, int count) throws IOException { return rom.readBytes(at(reference), count); }
}
