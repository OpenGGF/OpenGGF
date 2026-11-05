package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.media.CdMmdHeader;
import com.openggf.game.soniccd.media.CdMmdImage;

import java.io.IOException;
import java.util.Objects;

/**
 * Original USA R12A detached terrain, player and object-source metadata. Pin1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff:
 * Palmtree Panic/Data (Act 2 Present).asm:11-20,45-47,273-334; Scroll (Act 2 Present).asm:21-35,44-45,139-140;
 * Palette Data (Act 2 Present).asm:7-25,48-59; Graphics Lists (Act 2 Present).asm:7-26 and its full bodies.
 * Complete native blobs were matched to original R12A;
 * the actual loader at$203494 selects$20F09E, and layout loader$203510 selects$23B5F8.
 * These original-file extents are not runtime, programme-transition or World admission.
 */
public final class CdR12aProfile {
    public static final int FILE_BYTES = 0x40000;
    private static final String MMD_SHA256 = "6091e19b34358cf241e9b51f222b6ea2202230ff0b684b44f9ed050cf840b5e7";
    private static final CdMmdHeader HEADER = new CdMmdHeader(0, 0, 0, 0, 0x200100, 0x20010C, 0x200112);

    public enum Asset {
        LEVEL_CHUNKS(0x10000, 54272), LEVEL_COLLISION(0x3B274, 900),
        LAYOUT_FG(0x3B640, 170), LAYOUT_BG(0x3B6EA, 22),
        LEVEL_BLOCKS(0x3B8FA, 4600), ART_LEVEL_TILES(0x3CAF2, 10722),
        COL_ANGLE_MAP(0x39174, 256), COL_HEIGHT_MAP(0x39274, 4096), COL_WIDTH_MAP(0x3A274, 4096),
        LEVEL_DATA_INDEX(0xF09E, 16), LEVEL_LAYOUTS(0x3B5F8, 72), LEVEL_COL_INDEX(0x1A9A, 32),
        CAM_BOUNDS(0x2954, 12), LEVEL_START_LOC(0x2A18, 4),
        PALETTE_TABLE(0x5A4, 48), PAL_SONIC(0x6D4, 32), PAL_LEVEL(0x6F4, 96),
        // Complete source extents, including MiniAnims'20 unused bytes and ArcTan's trailing00.
        // The pinned gfx/r1/player.unc already contains its real final00; no read padding is added.
        ART_SONIC(0x20000, 69472), MAP_SPR_SONIC(0x30F60, 3004), DPLC_SONIC(0x31B1C, 1636),
        ANI_SONIC(0x582E, 578), SONIC_MINI_ANIMATIONS(0x57DE, 80), SONIC_RAMP_CHUNKS(0x45A4, 6),
        SPEC_CHUNKS(0x2A1C, 4), SINE_TABLE(0x872, 640), ARC_TAN_TABLE(0xB58, 258),
        // Scroll (Act 2 Present).asm:981-993; actual Init/Draw LEAs select$203428.
        BG_CAMERA_SECT_IDS(0x3428, 34), BG_CAMERA_SECTS(0x344A, 16),
        // Original USA first-viewport data and complete finite PLC source directory.
        PLC_LISTS(0xF0AE, 564),
        ART_ANIMALS(0x38D7C, 446),
        ART_ANTON(0x37A02, 484),
        ART_BIG_RING(0x3288C, 1034),
        ART_BIG_RING_FLASH(0xD866, 384),
        ART_BOULDER(0x3707E, 270),
        ART_CHECKPOINT(0x36982, 212),
        ART_DIAGONAL_SPRING(0x351C8, 780),
        ART_EXPLOSIONS(0x35CFA, 1256),
        ART_FLOAT_BLOCK(0x3718C, 230),
        ART_FLOWER(0x36B0A, 646),
        ART_GOAL_POST(0x32C96, 448),
        ART_HUD(0x36856, 300),
        ART_LOG_SHADOW(0x36A7C, 24),
        ART_MONITOR_TIME_POSTS(0x356F6, 1540),
        ART_MOSQUI(0x37BE6, 846),
        ART_PATA_BATA(0x37F34, 714),
        ART_PLATFORM(0x36EBA, 452),
        ART_POINTS(0x32180, 100),
        ART_RESULTS(0x33432, 1354),
        ART_RINGS(0x361E2, 244),
        ART_ROBOT_GEN_WITHERED(0x39028, 332),
        ART_SIGNPOST(0x32E56, 1500),
        ART_SPIKES(0x38BD4, 86),
        ART_SPINNING_DISC(0x37328, 166),
        ART_SPRING_WHEEL(0x37272, 182),
        ART_SPRINGBOARD(0x38982, 312),
        ART_SPRINGS(0x354D4, 546),
        ART_SWING(0x38C2A, 338),
        ART_TAGA_TAGA(0x381FE, 782),
        ART_TAMABBOH(0x3850C, 1142),
        ART_TITLE_CARD(0x3413C, 940),
        ART_TITLE_CARD_TEXT(0x36D90, 298),
        ART_TUNNEL_DOOR(0x376BA, 154),
        ART_TUNNEL_DOOR_SPLASH(0x37754, 686),
        OBJECT_LAYOUTS(0x776A, 3930),
        OBJECT_INDEX(0x3CC8, 256),
        MAP_SPR_RING(0x9F22, 67),
        ANI_RING(0x9F1A, 8),
        MAP_SPRING(0x99C8, 199),
        ANI_SPRING(0x99BA, 14),
        MAP_SPR_HUD(0xAB8E, 168),
        ANI_MONITOR(0xA7EE, 136),
        MAP_SPR_MONITOR_TIME(0xA876, 450),
        SECTION_DATA(0xCC32, 30),
        OBJECT_TILE_IDS(0xCC72, 74),
        RING_DELTAS(0x9ABE, 32),
        SPRING_SPEEDS(0x9710, 4),
        TOUCH_DIMENSIONS(0x6F5A, 126),
        ENEMY_POINTS(0x6D70, 8),
        HUD_RESET_TILES(0xAEC4, 12),
        HUD_DIVISORS(0xB024, 40),
        PALETTE_CYCLE_DATA(0x2C0, 84),
        ART_LIFE_ICON(0x362D6, 384),
        ART_HUD_NUMBERS(0x36456, 1024),
        ART_SHIELD(0x344E8, 1152),
        ART_INV_STARS(0x34968, 1152),
        ART_TIME_STARS(0x34DE8, 992),
        TITLE_CARD_DATA(0xE1CE, 64),
        TITLE_CARD_MAPPINGS(0xE20E, 276),
        // Breakable Wall/Main.asm:128-159. Actual live operands select these, not equal legacy padding.
        MAP_SPR_BREAKABLE_WALL(0xEED8, 312),
        BREAKABLE_WALL_PIECE_FRAMES(0xF010, 64),
        BREAKABLE_WALL_PIECE_DELTAS(0xF050, 24),
        BREAKABLE_WALL_PIECE_SPEEDS(0xF068, 48),
        // Boulder/Main.asm:30 and Platform/Main.asm:37-55: actual original R12A operands.
        MAP_SPR_BOULDER(0x947C, 14), MAP_SPR_PLATFORM(0xC9F2, 132),
        PLATFORM_WIDTHS(0xC66A, 4), PLATFORM_RANGES(0xC66E, 4),
        // Anton/Pata-Bata Main.asm actual original R12A Init and animation operands, retaining literal include padding.
        ANI_ANTON(0xB1C6, 12), MAP_SPR_ANTON(0xB1D2, 112),
        ANI_PATA_BATA(0xB344, 18), MAP_SPR_PATA_BATA1(0xB356, 34), MAP_SPR_PATA_BATA2(0xB378, 40),
        // Tamabboh/Floating Block actual original R12A Init/animation operands and complete source includes.
        ANI_TAMABBOH_NORMAL(0xB76A, 12), ANI_TAMABBOH_DAMAGED(0xB776, 26), MAP_SPR_TAMABBOH(0xB790, 62),
        ANI_TAMABBOH_MISSILE(0xB890, 8), MAP_SPR_TAMABBOH_MISSILE(0xB898, 42),
        ANI_FLOAT_BLOCK(0xA204, 30), MAP_SPR_FLOAT_BLOCK(0xA222, 76),
        // Checkpoint Init/Ball/Animate live operands select these complete original R12A includes, not equal duplicates.
        ANI_CHECKPOINT(0x1D8A6, 6), MAP_SPR_CHECKPOINT(0x1D8AC, 66),
        // Act 2 Present's complete native collapse block: real1/2/7/2 maps, not Act1's1/6/7/6 shape.
        MAP_SPR_COLLAPSE_PLATFORM_1(0xC348, 118), COLLAPSE_PLATFORM_SIZES_1(0xC3BE, 24),
        MAP_SPR_COLLAPSE_PLATFORM_3(0xC3D6, 62), MAP_SPR_COLLAPSE_PLATFORM_2(0xC414, 286),
        COLLAPSE_PLATFORM_SIZES_2(0xC532, 24), MAP_SPR_COLLAPSE_PLATFORM_4(0xC54A, 42),
        // Log Shadow/Mosqui actual R12A Init operands; includes retain literal and parent EVEN padding.
        LOG_SHADOW_BASE_TILES(0x8806, 16), MAP_SPR_LOG_SHADOW(0x8816, 16),
        ANI_MOSQUI_NORMAL(0xB4F2, 14), ANI_MOSQUI_DAMAGED(0xB500, 14),
        MAP_SPR_MOSQUI_NORMAL(0xB50E, 60), MAP_SPR_MOSQUI_DAMAGED(0xB54A, 66),
        // Actual R12A Explosion/Points and selected PP Flower source operands; full tables retain padding.
        ANI_EXPLOSION(0x1D700, 20), MAP_SPR_EXPLOSION(0x1D714, 146), MAP_SPR_POINTS(0xAA8C, 82),
        ANI_FLOWER(0x36A94, 28), MAP_SPR_FLOWER(0x36AB0, 90),
        // Horizontal Door's actual absolute Init/Animate operands, not equal vertical-script copies.
        ANI_TUNNEL_DOOR(0x1D6B6, 14), MAP_SPR_TUNNEL_DOOR(0x1D6C4, 28);
        private final int offset, length;
        Asset(int offset, int length) { this.offset = offset; this.length = length; }
        public int fileOffset() { return offset; }
        public int length() { return length; }
        public int nativeAddress() { return CdMmdHeader.WORD_RAM_ADDRESS + offset; }
    }

    public record FileRange(CdNativeFile file, CdMmdImage.Domain domain, int fileOffset, int length) {
        public FileRange {
            if (file != CdNativeFile.R12A || domain != CdMmdImage.Domain.ORIGINAL_WORD_RAM
                    || fileOffset < 0 || length < 0 || fileOffset > FILE_BYTES || length > FILE_BYTES - fileOffset)
                throw new IllegalArgumentException("Not a bounded R12A original-file range");
        }
        public int nativeAddress() { return CdMmdHeader.WORD_RAM_ADDRESS + fileOffset; }
        public int endExclusive() { return fileOffset + length; }
        public CdAssetBytes read(CdMmdImage image) {
            var source = Objects.requireNonNull(image).nativeSlice(domain, nativeAddress(), length);
            byte[] bytes = new byte[length]; source.get(bytes); return CdAssetBytes.copyOf(bytes);
        }
    }

    private CdR12aProfile() { }
    public static CdR12aProfile forStage(CdReleaseVariant variant, CdStageKey stage) {
        Objects.requireNonNull(variant); Objects.requireNonNull(stage);
        if (variant != CdReleaseVariant.USA || !stage.equals(CdStagePayload.R12A.stage()))
            throw new UnsupportedOperationException("Only original USA R12A asset metadata is reviewed");
        return new CdR12aProfile();
    }
    public CdReleaseVariant variant() { return CdReleaseVariant.USA; }
    public CdStageKey stage() { return CdStagePayload.R12A.stage(); }
    public CdNativeFile file() { return CdNativeFile.R12A; }
    public CdMmdImage.Domain domain() { return CdMmdImage.Domain.ORIGINAL_WORD_RAM; }
    public String expectedMmdSha256() { return MMD_SHA256; }
    public FileRange range(Asset asset) {
        Objects.requireNonNull(asset); return new FileRange(file(), domain(), asset.fileOffset(), asset.length());
    }
    /** The shared Java reader's real one-byte lookahead is not manufactured padding or a native cursor claim. */
    public FileRange readerRange(Asset asset) {
        Objects.requireNonNull(asset);
        return switch (asset) {
            case LEVEL_BLOCKS -> range(asset);
            case ART_LEVEL_TILES -> new FileRange(file(), domain(), asset.fileOffset(), asset.length() + 1);
            default -> throw new IllegalArgumentException("R12A asset has no reviewed Nemesis reader: " + asset);
        };
    }
    public void verifyProgram(CdNativeFile file, CdMmdImage.Domain domain, CdMmdImage image) throws IOException {
        if (file != file() || domain != domain()) throw new IllegalArgumentException("R12A profile/file/domain mismatch");
        Objects.requireNonNull(image);
        if (!originalShape(image) || !MMD_SHA256.equals(CdR11aProfile.sha256(image.readFile(0, image.fileBytes()))))
            throw new IOException("Original USA R12A content, header or file extent differs");
    }
    static boolean originalShape(CdMmdImage image) {
        return image.fileBytes() == FILE_BYTES && HEADER.equals(image.header())
                && image.fileBackedCopyBytes() == 0 && image.unresolvedCopyBytes() == 0;
    }
}
