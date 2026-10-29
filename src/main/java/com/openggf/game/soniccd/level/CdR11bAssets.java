package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.media.CdMmdHeader;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.media.CdTimeWarpPrograms;
import com.openggf.game.soniccd.release.CdRetailReleases;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;

/**
 * Detached immutable USA R11B source prerequisites. Runtime bytes come only from retained supplied media.
 * A Past identity is verified independently from the shared R11A header. No load, PLC service, art bank,
 * current residency, player execution, palette publication, World or program lifetime is installed here.
 * Native pin1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: Act 1 Past.asm; Data (Act 1 Past).asm;
 * Load Level Data.asm:11-22,57-98; Sonic/Main.asm:146,3055-3099; Functions (General).asm:401-579,794-816.
 */
public final class CdR11bAssets {
    public record Identity(CdReleaseVariant variant, CdStageKey stage, CdNativeFile file, CdMmdImage.Domain domain,
                           String mmdSha256, String observedWholeDiscSha256) {
        public Identity {
            Objects.requireNonNull(variant); Objects.requireNonNull(stage); Objects.requireNonNull(file);
            Objects.requireNonNull(domain); Objects.requireNonNull(mmdSha256); Objects.requireNonNull(observedWholeDiscSha256);
            if (variant != CdReleaseVariant.USA || !stage.equals(CdStagePayload.R11B.stage()) || file != CdNativeFile.R11B
                    || domain != CdMmdImage.Domain.ORIGINAL_WORD_RAM) {
                throw new IllegalArgumentException("Not a USA original R11B identity value");
            }
        }
    }

    /** Value metadata only. No public arbitrary-image read method or authority is carried by a range. */
    public record FileRange(CdNativeFile file, CdMmdImage.Domain domain, int fileOffset, int length) {
        public FileRange {
            if (file != CdNativeFile.R11B || domain != CdMmdImage.Domain.ORIGINAL_WORD_RAM
                    || fileOffset < 0 || length < 0 || fileOffset > 0x40000 || length > 0x40000 - fileOffset) {
                throw new IllegalArgumentException("Range is outside the original R11B file");
            }
        }
        public int nativeAddress() { return 0x200000 + fileOffset; }
        public int endExclusive() { return nativeAddress() + length; }
    }

    public record View(Asset asset, FileRange range, CdAssetBytes bytes) {
        public View {
            Objects.requireNonNull(asset); Objects.requireNonNull(range); Objects.requireNonNull(bytes);
            if (range.fileOffset() != asset.fileOffset() || range.length() != bytes.length()) {
                throw new IllegalArgumentException("View extent does not match its named source");
            }
        }
    }

    /** Complete blob, finite Java/native views, and fetched Java cursor are deliberately separate facts. */
    public record ReaderProfile(Asset asset, FileRange sourceBlobRange, FileRange javaInputRange,
                                FileRange nativeInputRange, int outputBytes, int javaConsumedInputBytes) {
        public ReaderProfile {
            Objects.requireNonNull(asset); Objects.requireNonNull(sourceBlobRange);
            Objects.requireNonNull(javaInputRange); Objects.requireNonNull(nativeInputRange);
            if (!sourceBlobRange.equals(asset.range()) || javaInputRange.fileOffset() != asset.fileOffset()
                    || nativeInputRange.fileOffset() != asset.fileOffset() || outputBytes <= 0 || (outputBytes & 31) != 0
                    || outputBytes > 0x10000 || javaConsumedInputBytes < 2
                    || javaConsumedInputBytes > javaInputRange.length() || nativeInputRange.length() < 2) {
                throw new IllegalArgumentException("Invalid finite R11B reader metadata");
            }
        }
    }

    public record NemesisRead(ReaderProfile profile, int consumedInputBytes, CdAssetBytes bytes) {
        public NemesisRead {
            Objects.requireNonNull(profile); Objects.requireNonNull(bytes);
            if (consumedInputBytes != profile.javaConsumedInputBytes() || bytes.length() != profile.outputBytes()) {
                throw new IllegalArgumentException("Decoded result differs from its finite reader contract");
            }
        }
    }

    public enum ReaderKind { JAVA, NATIVE }

    /** Original source extents, never current residency. The five Java lookaheads are separately named below. */
    public enum Asset {
        LEVEL_CHUNKS("LevelChunks", 0x10000, 54272, 0, 0, 0, 0, false),
        LEVEL_COLLISION("LevelCollision", 0x3B274, 800, 0, 0, 0, 0, false),
        LAYOUT_FG("LayoutFG", 0x3B5DC, 170, 0, 0, 0, 0, false),
        LAYOUT_BG("LayoutBG", 0x3B686, 22, 0, 0, 0, 0, false),
        LEVEL_START_LOC("LevelStartLoc", 0x29D4, 4, 0, 0, 0, 0, false),
        LEVEL_BLOCKS("LevelBlocks", 0x3BA3A, 4228, 5280, 4228, 4228, 4227, false),
        ART_LEVEL_TILES("Art_LevelTiles", 0x3CABE, 10222, 23872, 10222, 10223, 10222, true),
        PAL_SONIC("Pal_Sonic", 0x6F0, 32, 0, 0, 0, 0, false),
        PAL_LEVEL("Pal_Level", 0x710, 96, 0, 0, 0, 0, false),
        COL_ANGLE_MAP("ColAngleMap", 0x39174, 256, 0, 0, 0, 0, false),
        COL_HEIGHT_MAP("ColHeightMap", 0x39274, 4096, 0, 0, 0, 0, false),
        COL_WIDTH_MAP("ColWidthMap", 0x3A274, 4096, 0, 0, 0, 0, false),
        OBJECT_PLACEMENT_RECORDS("ObjectPlacementRecords", 0x775E, 3984, 0, 0, 0, 0, false),
        LEVEL_DATA_INDEX("LevelDataIndex", 0xF188, 16, 0, 0, 0, 0, false),
        PLC_LISTS("PLCListsAndBodies", 0xF198, 498, 0, 0, 0, 0, false),
        LEVEL_LAYOUTS("LevelLayouts", 0x3B594, 72, 0, 0, 0, 0, false),
        LEVEL_COL_INDEX("LevelColIndex", 0x1A56, 32, 0, 0, 0, 0, false),
        CAM_BOUNDS("CamBounds", 0x2910, 12, 0, 0, 0, 0, false),
        OBJECT_LAYOUTS("ObjectLayouts", 0x7752, 4002, 0, 0, 0, 0, false),
        PALETTE_TABLE("PaletteTable", 0x5C0, 48, 0, 0, 0, 0, false),
        SECTION_DATA("SectionData", 0xCF5A, 24, 0, 0, 0, 0, false),
        PALETTE_CYCLE_DATA("PaletteCycleData", 0x2C0, 84, 0, 0, 0, 0, false),
        BG_CAMERA_SECT_IDS("BGCameraSectIDs", 0x3398, 34, 0, 0, 0, 0, false),
        BG_CAMERA_SECTS("BGCameraSects", 0x33BA, 16, 0, 0, 0, 0, false),
        OBJECT_INDEX("ObjectIndex", 0x3C38, 256, 0, 0, 0, 0, false),
        ART_SONIC("Art_Sonic", 0x20000, 69471, 0, 0, 0, 0, false),
        MAP_SPR_SONIC("MapSpr_Sonic", 0x30F60, 3004, 0, 0, 0, 0, false),
        DPLC_SONIC("DPLC_Sonic", 0x31B1C, 1636, 0, 0, 0, 0, false),
        ANI_SONIC("Ani_Sonic", 0x579E, 578, 0, 0, 0, 0, false),
        SONIC_MINI_ANIMATIONS("MiniAnims", 0x574E, 60, 0, 0, 0, 0, false),
        SONIC_RAMP_CHUNKS("RampChunks", 0x4514, 6, 0, 0, 0, 0, false),
        SPEC_CHUNKS("SpecChunks", 0x29D8, 4, 0, 0, 0, 0, false),
        SINE_TABLE("SineTable", 0x82E, 640, 0, 0, 0, 0, false),
        ARC_TAN_TABLE("ArcTanTable", 0xB14, 257, 0, 0, 0, 0, false),
        ART_ANIMALS("Art_Animals", 0x38D7C, 446, 640, 446, 446, 445, true),
        ART_ANTON("Art_Anton", 0x37A02, 484, 704, 484, 485, 484, true),
        ART_BIG_RING("Art_BigRing", 0x3288C, 1034, 3392, 1034, 1034, 1033, true),
        ART_BIG_RING_FLASH("Art_BigRingFlash", 0xDB86, 384, 2464, 384, 385, 384, true),
        ART_BOULDER("Art_Boulder", 0x3707E, 270, 608, 270, 271, 270, true),
        ART_CHECKPOINT("Art_Checkpoint", 0x36982, 212, 384, 212, 212, 211, true),
        ART_DIAGONAL_SPRING("Art_DiagonalSpring", 0x351C8, 780, 1472, 780, 780, 779, true),
        ART_EXPLOSIONS("Art_Explosions", 0x35CFA, 1256, 2240, 1256, 1257, 1256, true),
        ART_FLOAT_BLOCK("Art_FloatBlock", 0x3718C, 230, 480, 231, 231, 231, true),
        ART_FLOWER("Art_Flower", 0x36B0A, 646, 1248, 646, 646, 645, true),
        ART_GOAL_POST("Art_GoalPost", 0x32C96, 448, 768, 448, 449, 448, true),
        ART_HUD("Art_HUD", 0x36856, 300, 864, 300, 301, 300, true),
        ART_LOG_SHADOW("Art_LogShadow", 0x36A7C, 24, 512, 24, 24, 23, true),
        ART_MONITOR_TIME_POSTS("Art_MonitorTimePosts", 0x356F6, 1540, 2624, 1541, 1541, 1541, true),
        ART_MOSQUI("Art_Mosqui", 0x37BE6, 846, 2240, 846, 847, 846, true),
        ART_PATA_BATA("Art_PataBata", 0x37F34, 714, 1600, 714, 715, 714, true),
        ART_PLATFORM("Art_Platform", 0x36EBA, 452, 1824, 452, 453, 452, true),
        ART_POINTS("Art_Points", 0x32180, 100, 160, 101, 101, 101, true),
        ART_PROJ_ANIMALS("Art_ProjAnimals", 0x3F45E, 434, 640, 434, 435, 434, true),
        ART_PROJECTOR("Art_Projector", 0x3F610, 1024, 1632, 1024, 1024, 1024, true),
        ART_RESULTS("Art_Results", 0x33432, 1354, 3840, 1354, 1354, 1354, true),
        ART_RINGS("Art_Rings", 0x361E2, 244, 448, 244, 244, 244, true),
        ART_ROBOT_GENERATOR("Art_RobotGenerator", 0x3F2AC, 434, 736, 434, 434, 434, true),
        ART_SCENERY("Art_Scenery", 0x3B908, 306, 544, 306, 307, 306, true),
        ART_SIGNPOST("Art_Signpost", 0x32E56, 1500, 2432, 1500, 1501, 1500, true),
        ART_SONIC_HOLE("Art_SonicHole", 0x3B896, 114, 448, 114, 114, 113, true),
        ART_SPIKES("Art_Spikes", 0x38BD4, 86, 256, 86, 87, 86, true),
        ART_SPINNING_DISC("Art_SpinningDisc", 0x37328, 166, 384, 167, 167, 167, true),
        ART_SPRINGBOARD("Art_Springboard", 0x38982, 312, 672, 312, 312, 311, true),
        ART_SPRINGS("Art_Springs", 0x354D4, 546, 1152, 547, 547, 547, true),
        ART_TAMABBOH("Art_Tamabboh", 0x3850C, 1142, 1888, 1142, 1143, 1142, true),
        ART_TITLE_CARD("Art_TitleCard", 0x3413C, 940, 3488, 940, 940, 939, true),
        ART_TITLE_CARD_TEXT("Art_TitleCardText", 0x36D90, 298, 1664, 298, 298, 298, true),
        ART_TUNNEL_DOOR("Art_TunnelDoor", 0x376BA, 154, 384, 154, 154, 153, true),
        ART_TUNNEL_WATERFALL("Art_TunnelWaterfall", 0x373CE, 328, 928, 328, 328, 327, true),
        ART_LIFE_ICON("Art_LifeIcon", 0x362D6, 384, 0, 0, 0, 0, false),
        TOUCH_DIMENSIONS("ObjColSizes", 0x6ECA, 126, 0, 0, 0, 0, false),
        ENEMY_POINTS("EnemyPoints", 0x6CE0, 8, 0, 0, 0, 0, false),
        ART_HUD_NUMBERS("Art_HUDNumbers", 0x36456, 1024, 0, 0, 0, 0, false),
        HUD_RESET_TILES("HUD_DrawScoreTiles/HUD_DrawRingsTiles", 0xAEF4, 12, 0, 0, 0, 0, false),
        HUD_DIVISORS("Hud_100000/Hud_1000h", 0xB054, 40, 0, 0, 0, 0, false),
        MAP_SPR_HUD("MapSpr_HUD", 0xABBE, 168, 0, 0, 0, 0, false),
        MAP_SPR_MONITOR_TIME("MapSpr_MonitorTime", 0xA8A6, 450, 0, 0, 0, 0, false);

        private final String nativeLabel;
        private final int fileOffset;
        private final int length;
        private final int outputBytes;
        private final int javaBytes;
        private final int nativeBytes;
        private final int javaConsumed;
        private final boolean plcSource;

        Asset(String nativeLabel, int fileOffset, int length, int outputBytes, int javaBytes, int nativeBytes,
              int javaConsumed, boolean plcSource) {
            this.nativeLabel = nativeLabel; this.fileOffset = fileOffset; this.length = length;
            this.outputBytes = outputBytes; this.javaBytes = javaBytes; this.nativeBytes = nativeBytes;
            this.javaConsumed = javaConsumed; this.plcSource = plcSource;
        }
        public String nativeLabel() { return nativeLabel; }
        public int fileOffset() { return fileOffset; }
        public int length() { return length; }
        public FileRange range() { return rangeAt(fileOffset, length); }
        public boolean isPlcSource() { return plcSource; }
        public Optional<ReaderProfile> readerProfile() {
            return outputBytes == 0 ? Optional.empty() : Optional.of(new ReaderProfile(this, range(),
                    rangeAt(fileOffset, javaBytes), rangeAt(fileOffset, nativeBytes), outputBytes, javaConsumed));
        }
    }

    private static final String PAST_SHA256 = "b7ebd9d362e91b213d53e984f737642f3e09cd2a511d3c5f1142c660e6aff265";
    private static final CdMmdHeader HEADER = new CdMmdHeader(0, 0, 0, 0, 0x200100, 0x20010C, 0x200112);
    private final Identity identity;
    private final CdMmdImage image;
    private final NemesisRead blocks;
    private final NemesisRead tiles;
    private final CdStageTerrain terrain;
    private final CdR11bLoadPlan loadPlan;
    private final CdObjectPlacements placements;
    private final CdSonicVisualAssets sonic;
    private final CdR11aPlayerAssets player;

    private CdR11bAssets(Identity identity, CdMmdImage image, NemesisRead blocks, NemesisRead tiles,
                         CdStageTerrain terrain, CdR11bLoadPlan loadPlan, CdObjectPlacements placements,
                         CdSonicVisualAssets sonic, CdR11aPlayerAssets player) {
        this.identity = identity; this.image = image; this.blocks = blocks; this.tiles = tiles;
        this.terrain = terrain; this.loadPlan = loadPlan; this.placements = placements; this.sonic = sonic; this.player = player;
    }

    /**
     * Independently verifies the complete retained original R11B, then decodes only the two core terrain sources.
     * The private bundle's provenance is necessary; its Source record, shared header or claimed hash is insufficient.
     * Rejection never reads WARP, borrows an unresolved relocation tail or alters its caller.
     */
    public static CdR11bAssets fromPrograms(CdTimeWarpPrograms programs) throws IOException {
        Objects.requireNonNull(programs, "retained actual CD programs");
        var source = Objects.requireNonNull(programs.source(), "observed program source");
        if (source.variant() != CdReleaseVariant.USA
                || !CdRetailReleases.USA.normalizedSha256().equals(source.wholeDiscSha256())
                || !PAST_SHA256.equals(source.r11bSha256())) {
            throw new IOException("R11B assets require the actual authenticated USA source");
        }
        var image = Objects.requireNonNull(programs.r11b(), "retained original R11B");
        if (image.fileBytes() != 262144 || !HEADER.equals(image.header())
                || image.fileBackedCopyBytes() != 0 || image.unresolvedCopyBytes() != 0) {
            throw new IOException("R11B original header, extent or relocation differs");
        }
        String observed = sha256(image.readFile(0, image.fileBytes()));
        if (!PAST_SHA256.equals(observed)) { throw new IOException("Retained R11B content differs from authenticated USA bytes"); }
        var blocks = decodeCore(image, Asset.LEVEL_BLOCKS);
        var tiles = decodeCore(image, Asset.ART_LEVEL_TILES);
        var terrain = CdStageTerrain.decode(CdStageTerrain.Shape.PPZ1_PAST, blocks.bytes().copy(), tiles.bytes().copy(),
                read(image, Asset.LEVEL_CHUNKS), read(image, Asset.LAYOUT_FG), read(image, Asset.LAYOUT_BG),
                read(image, Asset.LEVEL_COLLISION), read(image, Asset.COL_ANGLE_MAP),
                read(image, Asset.COL_HEIGHT_MAP), read(image, Asset.COL_WIDTH_MAP));
        var loadPlan = CdR11bLoadPlan.decode(image);
        var placements = CdObjectPlacements.decodeLayout(read(image, Asset.OBJECT_LAYOUTS));
        // ObjSonic_Init's live MOVE.L selects $230F60, not the identical unused $21F200 copy.
        // LoadSonicDynPLC can reach the actual zero EVEN at $230F5F; no padding is manufactured.
        var sonic = CdSonicVisualAssets.decode(read(image, Asset.MAP_SPR_SONIC), read(image, Asset.DPLC_SONIC),
                read(image, rangeAt(0x20000, 69472)));
        // This existing class is a pure byte-format decoder; its R11A name supplies no identity/profile admission.
        var player = CdR11aPlayerAssets.decode(read(image, Asset.ANI_SONIC), read(image, Asset.SONIC_MINI_ANIMATIONS),
                read(image, Asset.SONIC_RAMP_CHUNKS), read(image, Asset.SPEC_CHUNKS),
                read(image, Asset.SINE_TABLE), read(image, Asset.ARC_TAN_TABLE), sonic);
        return new CdR11bAssets(new Identity(CdReleaseVariant.USA, CdStagePayload.R11B.stage(), CdNativeFile.R11B,
                CdMmdImage.Domain.ORIGINAL_WORD_RAM, observed, source.wholeDiscSha256()),
                image, blocks, tiles, terrain, loadPlan, placements, sonic, player);
    }

    public Identity identity() { return identity; }
    public CdStageTerrain terrain() { return terrain; }
    public CdR11bLoadPlan loadPlan() { return loadPlan; }
    public CdObjectPlacements placements() { return placements; }
    public CdSonicVisualAssets sonic() { return sonic; }
    public CdR11aPlayerAssets player() { return player; }
    public NemesisRead blocksDecode() { return blocks; }
    public NemesisRead levelTilesDecode() { return tiles; }
    public FileRange sonicArtReadRange() { return rangeAt(0x20000, 69472); }
    public View view(Asset asset) {
        Objects.requireNonNull(asset);
        return new View(asset, asset.range(), CdAssetBytes.copyOf(read(image, asset)));
    }
    public View readerView(Asset asset, ReaderKind kind) {
        Objects.requireNonNull(asset); Objects.requireNonNull(kind);
        var profile = asset.readerProfile().orElseThrow(() -> new IllegalArgumentException("Asset has no finite Nemesis reader"));
        var range = kind == ReaderKind.JAVA ? profile.javaInputRange() : profile.nativeInputRange();
        return new View(asset, range, CdAssetBytes.copyOf(read(image, range)));
    }

    private static NemesisRead decodeCore(CdMmdImage image, Asset asset) throws IOException {
        var profile = asset.readerProfile().orElseThrow();
        var decoded = CdNemesisAsset.decode(read(image, profile.javaInputRange()), profile.outputBytes(), profile.outputBytes());
        if (decoded.consumedInputBytes() != profile.javaConsumedInputBytes()) {
            throw new IOException("Actual Java reader cursor differs for " + asset.nativeLabel());
        }
        return new NemesisRead(profile, decoded.consumedInputBytes(), decoded.bytes());
    }
    private static FileRange rangeAt(int offset, int length) {
        return new FileRange(CdNativeFile.R11B, CdMmdImage.Domain.ORIGINAL_WORD_RAM, offset, length);
    }
    private static byte[] read(CdMmdImage image, Asset asset) { return read(image, asset.range()); }
    private static byte[] read(CdMmdImage image, FileRange range) { return image.readFile(range.fileOffset(), range.length()); }
    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError("Required SHA-256 is unavailable", impossible); }
    }
}
