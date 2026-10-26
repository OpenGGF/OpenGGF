package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.media.CdMmdImage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Optional authenticated USA R11A object data. No object execution or regional offset inference.
 * Source: scddisasm 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff; runtime reads only the retained MMD.
 * Instances can be obtained only through the existing loaded asset owner's explicit openingObjects().
 */
public final class CdR11aObjectAssets {
    /** USA-only literal ranges, not additions to the three-region asset enumeration. */
    public enum Table {
        RING_DELTAS("ObjRing_Deltas", 0x9C96, 32),
        RING_ANIMATIONS("Ani_Ring", 0xA0F2, 8),
        RING_MAPPINGS("MapSpr_Ring", 0xA0FA, 67),
        MOSQUI_NORMAL_ANIMATIONS("Ani_Mosqui1", 0xB6CA, 14),
        MOSQUI_DAMAGED_ANIMATIONS("Ani_Mosqui2", 0xB6D8, 13),
        MOSQUI_NORMAL_MAPPINGS("MapSpr_Mosqui1", 0xB6E6, 60),
        MOSQUI_DAMAGED_MAPPINGS("MapSpr_Mosqui2", 0xB722, 66),
        OBJECT_TILE_IDS("ObjectTileIDs", 0xCF48, 74),
        TOUCH_DIMENSIONS("ObjColSizes", 0x70C6, 126),
        // Animal/Main.asm:221-244; direct USA operands independently join each include.
        FLYING_ANIMAL_ANIMATIONS("Ani_FlyingAnimal", 0xD1AE, 66),
        GROUND_ANIMAL_ANIMATIONS("Ani_GroundAnimal", 0xD1F0, 60),
        FLYING_ANIMAL_MAPPINGS("MapSpr_FlyingAnimal", 0xD22C, 20),
        GROUND_ANIMAL_MAPPINGS("MapSpr_GroundAnimal", 0xD240, 36),
        ANIMAL_BASE_TILES("ObjAnimal_BaseTileList", 0xD264, 22),
        // 3D Ramp/Main.asm:89-90,344-349; booster mappings are included by the level Data owner.
        PLANT_MAPPINGS("MapSpr_3DPlant", 0xE020, 38),
        PLANT_OFFSETS("Obj3DPlant_Offsets1/Obj3DPlant_Offsets2", 0xDDC8, 14),
        RAMP_BOOSTER_MAPPINGS("MapSpr_3DRamp", 0x1FDBC, 580),
        RAMP_BOOSTER_ANIMATIONS("Ani_3DRamp", 0xE046, 30),
        // Player Object Collision.asm:166,200-204 and the three objects' direct USA operands.
        ENEMY_POINTS("EnemyPoints", 0x6EDC, 8),
        EXPLOSION_ANIMATIONS("Ani_Explosion", 0x3F074, 20),
        EXPLOSION_MAPPINGS("MapSpr_Explosion", 0x3F088, 146),
        POINTS_MAPPINGS("MapSpr_Points", 0xAC64, 82),
        FLOWER_ANIMATIONS("Ani_Flower", 0x35ED8, 28),
        FLOWER_MAPPINGS("MapSpr_Flower", 0x35EF4, 90),
        // Game Over/Main.asm:36,43,118: one include holding BOTH two-word tables (TIME is +4, with its own base).
        // Read lazily through view(); never decoded here as uniform visual frames.
        GAME_OVER_MAPPINGS("MapSpr_GameOver/MapSpr_TimeOver", 0xF03E, 56),
        // Floating Block/Main.asm:42,110,129,140,171-176; direct USA operands join both adjacent includes.
        FLOAT_BLOCK_ANIMATIONS("Ani_FloatBlock", 0xA3DC, 30),
        FLOAT_BLOCK_MAPPINGS("MapSpr_FloatBlock", 0xA3FA, 76),
        // Spring/Main.asm:492-496; one real script and one shared include with three independent mapping bases.
        // Keep the mappings raw: the vertical/horizontal tables do not fit the uniform visual decoder.
        SPRING_ANIMATIONS("Ani_Spring", 0x9B92, 14),
        SPRING_MAPPINGS("MapSpr_Spring1/MapSpr_Spring2/MapSpr_Spring3", 0x9BA0, 200),
        // ObjSpring_Init ANDI.W #2 / MOVE.W ObjSpring_Speeds(pc,d0.w) (Spring/Main.asm:237-244).
        SPRING_SPEEDS("ObjSpring_Speeds", 0x98E8, 4),
        // ObjSpinTunnel_Init (Spin Tunnel/Main (Tag).asm:30): actual USA immediate $23EE9C.
        // Keep the full raw include: frame 0 is an empty target inside frame 1's counted body.
        POWERUP_MAPPINGS("MapSpr_Powerup", 0x3EE9C, 274),
        // ObjTunnelDoorV_Init (Tunnel Door/Main.asm:36): actual USA immediate $20CE9A.
        // Two counted frames with their real trailing zeros; the vertical body never calls Ani_FlapDoorV.
        TUNNEL_DOOR_VERTICAL_MAPPINGS("MapSpr_FlapDoorV", 0xCE9A, 28),
        // Monitor and Time Post/Main.asm:304,366,455,644-650; actual USA operands join both full includes.
        MONITOR_TIME_ANIMATIONS("Ani_Monitor", 0xA9C6, 136),
        MONITOR_TIME_MAPPINGS("MapSpr_MonitorTime", 0xAA4E, 450),
        // Spin Tunnel/Main (Door).asm:57,83 and Data (Act 1 Present).asm:536-540; actual USA operands.
        // Separate normal Door views; both orientations use the existing standard 12-tile TunnelDoorArtBank.
        TUNNEL_DOOR_ANIMATIONS("Ani_TunnelDoor", 0x3F02A, 14),
        TUNNEL_DOOR_MAPPINGS("MapSpr_TunnelDoor", 0x3F038, 28),
        // Pata-Bata/Main.asm:29,39,76,84-92; actual USA PC-relative operands join the three complete includes.
        PATA_BATA_ANIMATIONS("Ani_PataBata", 0xB51C, 18),
        PATA_BATA_NORMAL_MAPPINGS("MapSpr_PataBata1", 0xB52E, 34),
        PATA_BATA_DAMAGED_MAPPINGS("MapSpr_PataBata2", 0xB550, 40),
        // Boulder/Main.asm:30,57-59; actual USA MOVE.L at $9618 stores $209654. Includes the real EVEN byte.
        BOULDER_MAPPINGS("MapSpr_Boulder", 0x9654, 14),
        // Spikes/Main.asm:21,87-89; actual USA MOVE.L at $C130 stores $20C1F0. Full include plus real pad.
        SPIKES_MAPPINGS("MapSpr_Spikes", 0xC1F0, 24),
        // Checkpoint/Main.asm:74,101,188; actual USA MOVE.L/LEA operands use the shared $23F120 root.
        CHECKPOINT_ANIMATIONS("Ani_Checkpoint", 0x3F11A, 6),
        CHECKPOINT_MAPPINGS("MapSpr_Checkpoint", 0x3F120, 66),
        // Anton/Main.asm:27,85,98-103; actual USA MOVE.L $B2EA / PC-relative LEA $B382.
        ANTON_ANIMATIONS("Ani_Anton", 0xB39E, 12),
        ANTON_MAPPINGS("MapSpr_Anton", 0xB3AA, 112),
        // Collapse Main.asm:36-41,142,183,229,281: actual USA LEAs and both child LONG operands.
        COLLAPSE_PLATFORM_MAPPINGS_1("MapSpr_CollapsePlatform1", 0xC520, 118),
        COLLAPSE_PLATFORM_SIZES_1("ObjCollapsePlatform_Sizes1", 0xC596, 24),
        COLLAPSE_PLATFORM_MAPPINGS_3("MapSpr_CollapsePlatform3", 0xC5AE, 62),
        COLLAPSE_PLATFORM_MAPPINGS_2("MapSpr_CollapsePlatform2", 0xC5EC, 408),
        COLLAPSE_PLATFORM_SIZES_2("ObjCollapsePlatform_Sizes2", 0xC784, 40),
        COLLAPSE_PLATFORM_MAPPINGS_4("MapSpr_CollapsePlatform4", 0xC7AC, 158),
        // Platform/Main.asm:37/40/43,51,55,85-86,559-561: actual USA LONG/PC-indexed operands.
        PLATFORM_MAPPINGS("MapSpr_Platform", 0xCCC8, 132),
        PLATFORM_WIDTHS("ObjPlatform_Widths", 0xC940, 4),
        PLATFORM_RANGES("ObjPlatform_Ranges", 0xC944, 4),
        // Tamabboh/Main.asm:40-57,202-209,233,280-290: independently authenticated USA operands.
        TAMABBOH_NORMAL_ANIMATIONS("Ani_Tamabboh1", 0xB942, 12),
        TAMABBOH_DAMAGED_ANIMATIONS("Ani_Tamabboh2", 0xB94E, 26),
        TAMABBOH_MAPPINGS("MapSpr_Tamabboh1/MapSpr_Tamabboh2", 0xB968, 62),
        TAMABBOH_MISSILE_ANIMATIONS("Ani_TamabbohMissile", 0xBA68, 8),
        TAMABBOH_MISSILE_MAPPINGS("MapSpr_TamabbohMissile", 0xBA70, 42),
        // Spinning Disc/Main.asm:96,111,228-233: actual USA operands; full native-order includes.
        SPINNING_DISC_PLAYER_FRAMES("ObjSpinningDisc_PlayerFrames", 0x9496, 16),
        SPINNING_DISC_ANIMATIONS("Ani_SpinningDisc", 0x95BE, 8),
        SPINNING_DISC_MAPPINGS("MapSpr_SpinningDisc", 0x95C6, 36),
        // Tunnel Path/Main.asm:191,382,392-478; actual USA operands join both complete raw tables.
        // ID07 stores the existing Powerup mapping pointer and literal $0541 without drawing or decoding art.
        TUNNEL_PATH_GROUND_SPEEDS("ObjTunnelPath_GVels", 0x8D90, 8),
        TUNNEL_PATH_COORDINATES("ObjTunnelPath_Paths", 0x8D98, 284),
        // Log Shadow/Main.asm:26,53,60-70; actual USA operands join both complete raw tables.
        LOG_SHADOW_BASE_TILES("ObjLogShadow_BaseTileList", 0x89DE, 16),
        LOG_SHADOW_MAPPINGS("MapSpr_LogShadow", 0x89EE, 16);

        private final String nativeLabel;
        private final int offset;
        private final int length;
        Table(String nativeLabel, int offset, int length) {
            this.nativeLabel = nativeLabel;
            this.offset = offset;
            this.length = length;
        }
        public String nativeLabel() { return nativeLabel; }
        public CdR11aProfile.FileRange range() {
            return new CdR11aProfile.FileRange(CdNativeFile.R11A, CdMmdImage.Domain.ORIGINAL_WORD_RAM, offset, length);
        }
    }
    public record View(Table table, CdR11aProfile.FileRange range, CdAssetBytes bytes) { }
    public record Delta(int x, int y) { }
    public record TouchSize(int halfWidth, int halfHeight) { }
    /** Native signed-word offsets: four main plants, followed by three small plants. */
    public record PlantOffsets(List<Integer> main, List<Integer> small) {
        public PlantOffsets {
            main = signedWords(main, 4);
            small = signedWords(small, 3);
        }
    }
    /** Descriptive source/PLC/base-word association, not a native transfer or a residency certificate. */
    public record ArtBinding(CdR11aAsset source, int plcId, CdR11aLoadPlan.PlcEntry plc,
                              int baseWord, CdR11aAssets.NemesisRead decode) {
        public CdAssetBytes bytes() { return decode.bytes(); }
        public int tileCount() { return bytes().length() / 32; }
        public int firstTileId() { return plc.vramAddress() / 32; }
    }
    /**
     * Only the two decoded sources associated with PLC_Std and PLC_Cam1_Full.
     * This executes no PLC queue/section entry and supplies no surrounding VRAM bytes.
     * Any future DEV preload must be an explicit, separately owned runtime action.
     */
    public static final class InitialArtBank {
        private final List<ArtBinding> bindings;
        private InitialArtBank(List<ArtBinding> bindings) { this.bindings = List.copyOf(bindings); }
        public List<ArtBinding> bindings() { return bindings; }
        public ArtBinding binding(CdR11aAsset source) {
            return bindings.stream().filter(b -> b.source() == source).findFirst()
                    .orElseThrow(() -> new UnsupportedOperationException("No opening object art binding for " + source));
        }
        /** Native 11-bit tile ID after the mapping/base-word addition; absent art is never zero-filled. */
        public CdAssetBytes tile(int nativeTileId) {
            if ((nativeTileId & ~0x7FF) != 0) { throw new IllegalArgumentException("Not an 11-bit tile ID"); }
            for (var binding : bindings) {
                int index = nativeTileId - binding.firstTileId();
                if (index >= 0 && index < binding.tileCount()) { return binding.bytes().slice(index * 32, 32); }
            }
            throw new UnsupportedOperationException("Tile has no decoded opening object source: " + nativeTileId);
        }
    }

    /**
     * Separate optional decoded sources for PLC_Cam1_Full rows 1/4/5. This bank does not extend
     * initialArtBank(), execute a PLC or certify live residency after section changes.
     */
    public static final class FrontierArtBank {
        private final List<ArtBinding> bindings;
        private FrontierArtBank(List<ArtBinding> bindings) { this.bindings = List.copyOf(bindings); }
        public List<ArtBinding> bindings() { return bindings; }
        public ArtBinding binding(CdR11aAsset source) {
            return bindings.stream().filter(b -> b.source() == source).findFirst()
                    .orElseThrow(() -> new UnsupportedOperationException("No frontier object art binding for " + source));
        }
        /** Native 11-bit tile ID; gaps and sources belonging to the opening bank remain absent. */
        public CdAssetBytes tile(int nativeTileId) {
            if ((nativeTileId & ~0x7FF) != 0) { throw new IllegalArgumentException("Not an 11-bit tile ID"); }
            for (var binding : bindings) {
                int index = nativeTileId - binding.firstTileId();
                if (index >= 0 && index < binding.tileCount()) { return binding.bytes().slice(index * 32, 32); }
            }
            throw new UnsupportedOperationException("Tile has no decoded frontier object source: " + nativeTileId);
        }
    }

    /**
     * Separate optional sources for PLC_Std rows 10/11/12. No PLC service or live residency is implied;
     * the checkpoint gap and all sources in the initial/frontier banks remain unbound here.
     */
    public static final class ContactArtBank {
        private final List<ArtBinding> bindings;
        private ContactArtBank(List<ArtBinding> bindings) { this.bindings = List.copyOf(bindings); }
        public List<ArtBinding> bindings() { return bindings; }
        public ArtBinding binding(CdR11aAsset source) {
            return bindings.stream().filter(b -> b.source() == source).findFirst()
                    .orElseThrow(() -> new UnsupportedOperationException("No contact object art binding for " + source));
        }
        /** Native 11-bit tile ID after mapping/base-word addition; absent art is never zero-filled. */
        public CdAssetBytes tile(int nativeTileId) {
            if ((nativeTileId & ~0x7FF) != 0) { throw new IllegalArgumentException("Not an 11-bit tile ID"); }
            for (var binding : bindings) {
                int index = nativeTileId - binding.firstTileId();
                if (index >= 0 && index < binding.tileCount()) { return binding.bytes().slice(index * 32, 32); }
            }
            throw new UnsupportedOperationException("Tile has no decoded contact object source: " + nativeTileId);
        }
    }

    /**
     * Separate optional source for PLC_Std row 2 (Art_FloatBlock). No PLC service, transfer or live residency
     * is implied; every source in the initial, frontier and contact banks remains unbound here.
     */
    public static final class FloatBlockArtBank {
        private final List<ArtBinding> bindings;
        private FloatBlockArtBank(List<ArtBinding> bindings) { this.bindings = List.copyOf(bindings); }
        public List<ArtBinding> bindings() { return bindings; }
        public ArtBinding binding(CdR11aAsset source) {
            return bindings.stream().filter(b -> b.source() == source).findFirst()
                    .orElseThrow(() -> new UnsupportedOperationException("No FloatBlock art binding for " + source));
        }
        /** Native 11-bit tile ID after mapping/base-word addition; absent art is never zero-filled. */
        public CdAssetBytes tile(int nativeTileId) {
            if ((nativeTileId & ~0x7FF) != 0) { throw new IllegalArgumentException("Not an 11-bit tile ID"); }
            for (var binding : bindings) {
                int index = nativeTileId - binding.firstTileId();
                if (index >= 0 && index < binding.tileCount()) { return binding.bytes().slice(index * 32, 32); }
            }
            throw new UnsupportedOperationException("Tile has no decoded FloatBlock source: " + nativeTileId);
        }
    }


    /**
     * Separate optional normal Spring source for PLC_Std row 7. No PLC transfer, live residency or
     * diagonal/MovingSpring art is implied; every existing bank remains separate.
     */
    public static final class SpringArtBank {
        private final List<ArtBinding> bindings;
        private SpringArtBank(List<ArtBinding> bindings) { this.bindings = List.copyOf(bindings); }
        public List<ArtBinding> bindings() { return bindings; }
        public ArtBinding binding(CdR11aAsset source) {
            return bindings.stream().filter(b -> b.source() == source).findFirst()
                    .orElseThrow(() -> new UnsupportedOperationException("No Spring art binding for " + source));
        }
        /** Native 11-bit tile ID after mapping/base-word addition; adjacent Powerup art remains absent. */
        public CdAssetBytes tile(int nativeTileId) {
            if ((nativeTileId & ~0x7FF) != 0) { throw new IllegalArgumentException("Not an 11-bit tile ID"); }
            for (var binding : bindings) {
                int index = nativeTileId - binding.firstTileId();
                if (index >= 0 && index < binding.tileCount()) { return binding.bytes().slice(index * 32, 32); }
            }
            throw new UnsupportedOperationException("Tile has no decoded Spring source: " + nativeTileId);
        }
    }

    /**
     * Separate optional standard Tunnel Door source for PLC_Std row 1. No native PLC service, transfer
     * or live residency is implied; every existing art bank remains separate.
     */
    public static final class TunnelDoorArtBank {
        private final List<ArtBinding> bindings;
        private TunnelDoorArtBank(List<ArtBinding> bindings) { this.bindings = List.copyOf(bindings); }
        public List<ArtBinding> bindings() { return bindings; }
        public ArtBinding binding(CdR11aAsset source) {
            return bindings.stream().filter(b -> b.source() == source).findFirst()
                    .orElseThrow(() -> new UnsupportedOperationException("No Tunnel Door art binding for " + source));
        }
        /** Native 11-bit tile ID after mapping/base-word addition; adjacent FloatBlock art remains absent. */
        public CdAssetBytes tile(int nativeTileId) {
            if ((nativeTileId & ~0x7FF) != 0) { throw new IllegalArgumentException("Not an 11-bit tile ID"); }
            for (var binding : bindings) {
                int index = nativeTileId - binding.firstTileId();
                if (index >= 0 && index < binding.tileCount()) { return binding.bytes().slice(index * 32, 32); }
            }
            throw new UnsupportedOperationException("Tile has no decoded Tunnel Door source: " + nativeTileId);
        }
    }

    /**
     * Separate optional standard source for PLC_Std row 9. No PLC service, transfer or live residency
     * is implied; item/HUD priority attributes belong to execution and every existing bank stays separate.
     */
    public static final class MonitorTimeArtBank {
        private final List<ArtBinding> bindings;
        private MonitorTimeArtBank(List<ArtBinding> bindings) { this.bindings = List.copyOf(bindings); }
        public List<ArtBinding> bindings() { return bindings; }
        public ArtBinding binding(CdR11aAsset source) {
            return bindings.stream().filter(b -> b.source() == source).findFirst()
                    .orElseThrow(() -> new UnsupportedOperationException("No Monitor/time-post art binding for " + source));
        }
        /** Native 11-bit tile ID after mapping/base-word addition; absent art is never zero-filled. */
        public CdAssetBytes tile(int nativeTileId) {
            if ((nativeTileId & ~0x7FF) != 0) { throw new IllegalArgumentException("Not an 11-bit tile ID"); }
            for (var binding : bindings) {
                int index = nativeTileId - binding.firstTileId();
                if (index >= 0 && index < binding.tileCount()) { return binding.bytes().slice(index * 32, 32); }
            }
            throw new UnsupportedOperationException("Tile has no decoded Monitor/time-post source: " + nativeTileId);
        }
    }

    /**
     * Separate optional source for PLC_Cam2_Full row 2. This describes the native source/base association;
     * it executes no camera PLC, transfer or residency update and never extends an existing bank.
     */
    public static final class PataBataArtBank {
        private final List<ArtBinding> bindings;
        private PataBataArtBank(List<ArtBinding> bindings) { this.bindings = List.copyOf(bindings); }
        public List<ArtBinding> bindings() { return bindings; }
        public ArtBinding binding(CdR11aAsset source) {
            return bindings.stream().filter(b -> b.source() == source).findFirst()
                    .orElseThrow(() -> new UnsupportedOperationException("No Pata-Bata art binding for " + source));
        }
        /** Native 11-bit tile ID; adjacent Mosqui and Taga-Taga art remain absent. */
        public CdAssetBytes tile(int nativeTileId) {
            if ((nativeTileId & ~0x7FF) != 0) { throw new IllegalArgumentException("Not an 11-bit tile ID"); }
            for (var binding : bindings) {
                int index = nativeTileId - binding.firstTileId();
                if (index >= 0 && index < binding.tileCount()) { return binding.bytes().slice(index * 32, 32); }
            }
            throw new UnsupportedOperationException("Tile has no decoded Pata-Bata source: " + nativeTileId);
        }
    }

    // Preserve the opening load's exact nine table reads and two art decodes. New data is opt-in below.
    private static final List<Table> OPENING_TABLES = List.of(Table.RING_DELTAS, Table.RING_ANIMATIONS,
            Table.RING_MAPPINGS, Table.MOSQUI_NORMAL_ANIMATIONS, Table.MOSQUI_DAMAGED_ANIMATIONS,
            Table.MOSQUI_NORMAL_MAPPINGS, Table.MOSQUI_DAMAGED_MAPPINGS, Table.OBJECT_TILE_IDS, Table.TOUCH_DIMENSIONS);

    private final CdR11aAssets.Identity identity;
    private final CdR11aProfile profile;
    private final CdMmdImage image;
    private final CdR11aLoadPlan loadPlan;
    private final Map<Table, View> views;
    private final List<Delta> ringDeltas;
    private final List<TouchSize> touchDimensions;
    private final CdObjectVisualAssets rings;
    private final CdObjectVisualAssets normalMosqui;
    private final CdObjectVisualAssets damagedMosqui;
    private final InitialArtBank initialArtBank;
    // Lazily derived only from the already-authenticated, retained immutable image. Access is synchronized.
    private final Map<Table, View> frontierViews = new EnumMap<>(Table.class);
    private CdObjectVisualAssets plant;
    private CdObjectVisualAssets rampBooster;
    private CdObjectVisualAssets groundAnimal;
    private CdObjectVisualAssets flyingAnimal;
    private PlantOffsets plantOffsets;
    private FrontierArtBank frontierArtBank;
    private CdObjectVisualAssets explosion;
    private CdObjectVisualAssets points;
    private CdObjectVisualAssets flower;
    private ContactArtBank contactArtBank;
    private CdObjectVisualAssets floatBlock;
    private FloatBlockArtBank floatBlockArtBank;
    private SpringArtBank springArtBank;
    private CdObjectVisualAssets springVertical;
    private CdObjectVisualAssets springHorizontal;
    private CdObjectVisualAssets springDiagonal;
    private CdObjectVisualAssets tunnelDoorVertical;
    private TunnelDoorArtBank tunnelDoorArtBank;
    private CdObjectVisualAssets monitorTime;
    private MonitorTimeArtBank monitorTimeArtBank;
    private CdObjectVisualAssets tunnelDoor;
    private CdObjectVisualAssets pataBataNormal;
    private CdObjectVisualAssets pataBataDamaged;
    private PataBataArtBank pataBataArtBank;
    private CdObjectVisualAssets boulder;
    private CdObjectVisualAssets spikes;
    private CdObjectVisualAssets checkpoint;
    private CdObjectVisualAssets anton;
    private CdObjectVisualAssets collapsePlatform1;
    private CdObjectVisualAssets collapsePlatform2;
    private CdObjectVisualAssets collapsePlatform3;
    private CdObjectVisualAssets collapsePlatform4;
    private CdObjectVisualAssets platform;
    private CdObjectVisualAssets tamabbohNormal;
    private CdObjectVisualAssets tamabbohDamaged;
    private CdObjectVisualAssets tamabbohMissile;
    private CdObjectVisualAssets spinningDisc;
    private CdObjectVisualAssets logShadow;

    private CdR11aObjectAssets(CdR11aAssets.Identity identity, Map<Table, View> views, List<Delta> deltas,
                                List<TouchSize> dimensions, CdObjectVisualAssets rings,
                                CdObjectVisualAssets normalMosqui, CdObjectVisualAssets damagedMosqui,
                                InitialArtBank initialArtBank, CdR11aProfile profile,
                                CdMmdImage image, CdR11aLoadPlan loadPlan) {
        this.identity = identity;
        this.profile = profile;
        this.image = image;
        this.loadPlan = loadPlan;
        this.views = Map.copyOf(views);
        this.ringDeltas = List.copyOf(deltas);
        this.touchDimensions = List.copyOf(dimensions);
        this.rings = rings;
        this.normalMosqui = normalMosqui;
        this.damagedMosqui = damagedMosqui;
        this.initialArtBank = initialArtBank;
    }

    /** Package-owned binding to the existing immutable image; no public image/identity test constructor. */
    static CdR11aObjectAssets load(CdR11aProfile profile, CdR11aAssets.Identity identity, CdMmdImage image,
                                    CdR11aLoadPlan loadPlan) throws IOException {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(identity, "identity");
        if (profile.variant() != CdReleaseVariant.USA || identity.variant() != CdReleaseVariant.USA) {
            throw new UnsupportedOperationException("Opening object assets are reviewed only for USA R11A");
        }
        if (!profile.stage().equals(identity.stage()) || profile.file() != identity.file()
                || profile.domain() != identity.domain() || !profile.expectedMmdSha256().equals(identity.mmdSha256())) {
            throw new IllegalArgumentException("Object asset owner/profile identity mismatch");
        }
        // Re-hash the actual retained immutable file before reading any new table or decompressing art.
        // The identity record, including its asserted digest, cannot stand in for these opened bytes.
        profile.verifyProgram(identity.file(), identity.domain(), image);
        Objects.requireNonNull(loadPlan, "loadPlan");
        Map<Table, View> views = new EnumMap<>(Table.class);
        for (Table table : OPENING_TABLES) {
            var range = table.range();
            views.put(table, new View(table, range, range.read(image)));
        }
        var deltas = decodeRingDeltas(read(views, Table.RING_DELTAS));
        var dimensions = decodeTouchDimensions(read(views, Table.TOUCH_DIMENSIONS));
        int mosquiTile = decodeTileWord(read(views, Table.OBJECT_TILE_IDS), 0, 0);
        var rings = CdObjectVisualAssets.decode(read(views, Table.RING_MAPPINGS), 9,
                read(views, Table.RING_ANIMATIONS), 1, 14);
        var normal = CdObjectVisualAssets.decode(read(views, Table.MOSQUI_NORMAL_MAPPINGS), 5,
                read(views, Table.MOSQUI_NORMAL_ANIMATIONS), 2, 70);
        var damaged = CdObjectVisualAssets.decode(read(views, Table.MOSQUI_DAMAGED_MAPPINGS), 5,
                read(views, Table.MOSQUI_DAMAGED_ANIMATIONS), 2, 70);
        if (loadPlan.sections().getFirst().initialPlcId() != 2) {
            throw new IllegalArgumentException("Unreviewed initial section PLC association");
        }
        // ObjRing_Init (Ring/Main.asm:154-157) supplies the literal base word, independent of ObjectTileIDs.
        var ringArt = art(profile, image, loadPlan, CdR11aAsset.ART_RINGS, 1, 0xF5C0, 0xA7AE, 14);
        var mosquiArt = art(profile, image, loadPlan, CdR11aAsset.ART_MOSQUI, 2, 0x7400, mosquiTile, 70);
        return new CdR11aObjectAssets(identity, views, deltas, dimensions, rings, normal, damaged,
                new InitialArtBank(List.of(ringArt, mosquiArt)), profile, image, loadPlan);
    }

    public CdR11aAssets.Identity identity() { return identity; }
    public synchronized View view(Table table) {
        Objects.requireNonNull(table, "table");
        var opening = views.get(table);
        if (opening != null) { return opening; }
        return frontierViews.computeIfAbsent(table, key -> new View(key, key.range(), key.range().read(image)));
    }
    /** Selector is the native subtype's high nibble, 0..15, before its multiply by two. */
    public Delta ringDelta(int selector) { return ringDeltas.get(selector); }
    public TouchSize touchSize(int collisionType) {
        if ((collisionType & ~0xFF) != 0 || (collisionType & 0x3F) == 0) {
            throw new IllegalArgumentException("Collision type has no bounded ObjColSizes entry");
        }
        return touchDimensions.get((collisionType & 0x3F) - 1);
    }
    public int tileWord(int selector, int subtype2) { return decodeTileWord(read(views, Table.OBJECT_TILE_IDS), selector, subtype2); }
    public CdObjectVisualAssets rings() { return rings; }
    public CdObjectVisualAssets normalMosqui() { return normalMosqui; }
    public CdObjectVisualAssets damagedMosqui() { return damagedMosqui; }
    public InitialArtBank initialArtBank() { return initialArtBank; }

    /** Static mappings only: Obj3DPlant never calls AnimateObject and has no script include. */
    public synchronized CdObjectVisualAssets plant() {
        if (plant == null) { plant = CdObjectVisualAssets.decodeStatic(read(Table.PLANT_MAPPINGS), 2, 28); }
        return plant;
    }
    public synchronized CdObjectVisualAssets rampBooster() {
        if (rampBooster == null) {
            // MapSpr_3DRamp's last two count bytes consume 12 pieces each, leaving 11/21 authored bytes.
            // Keep the entire include but do not reinterpret those unreachable tails as drawn pieces.
            rampBooster = CdObjectVisualAssets.decode(read(Table.RAMP_BOOSTER_MAPPINGS), 10,
                    read(Table.RAMP_BOOSTER_ANIMATIONS), 3, 63, Map.of(8, 11, 9, 21));
        }
        return rampBooster;
    }
    /** Binding a visual table does not admit the roaming or hologram execution branches. */
    public synchronized CdObjectVisualAssets groundAnimal() {
        if (groundAnimal == null) {
            groundAnimal = CdObjectVisualAssets.decode(read(Table.GROUND_ANIMAL_MAPPINGS), 5,
                    read(Table.GROUND_ANIMAL_ANIMATIONS), 1, 20);
        }
        return groundAnimal;
    }
    public synchronized CdObjectVisualAssets flyingAnimal() {
        if (flyingAnimal == null) {
            flyingAnimal = CdObjectVisualAssets.decode(read(Table.FLYING_ANIMAL_MAPPINGS), 3,
                    read(Table.FLYING_ANIMAL_ANIMATIONS), 2, 20);
        }
        return flyingAnimal;
    }
    public synchronized PlantOffsets plantOffsets() {
        if (plantOffsets == null) { plantOffsets = decodePlantOffsets(read(Table.PLANT_OFFSETS)); }
        return plantOffsets;
    }
    /** Native act/timeZone are zero-based 0..2; this lookup is not an art-residency promise for other acts. */
    public int animalBaseTile(int act, int timeZone) {
        return decodeAnimalBaseTile(read(Table.ANIMAL_BASE_TILES), act, timeZone);
    }
    /** Explicit opt-in; no new art is decoded by openingObjects(), view(), or the visual-table accessors. */
    public synchronized FrontierArtBank frontierArtBank() throws IOException {
        if (frontierArtBank == null) {
            frontierArtBank = new FrontierArtBank(List.of(
                    frontierArt(CdR11aAsset.ART_ANIMALS, 1, 0x7100, animalBaseTile(0, 1), 20, 445),
                    frontierArt(CdR11aAsset.ART_3D_PLANT, 4, 0x8480, 0x4424, 28, 393),
                    frontierArt(CdR11aAsset.ART_3D_RAMP_BOOST, 5, 0x8820, 0x441, 63, 677)));
        }
        return frontierArtBank;
    }

    /** ObjExplosion_Init/Main (Explosion/Main.asm:73,89-92); binds both source scripts without executing them. */
    public synchronized CdObjectVisualAssets explosion() {
        if (explosion == null) {
            explosion = CdObjectVisualAssets.decode(read(Table.EXPLOSION_MAPPINGS), 7,
                    read(Table.EXPLOSION_ANIMATIONS), 2, 70);
        }
        return explosion;
    }
    /** ObjPoints_Init (HUD and Points/Main.asm:37-39) sets oMapFrame directly, with no animation scripts. */
    public synchronized CdObjectVisualAssets points() {
        if (points == null) { points = CdObjectVisualAssets.decodeStatic(read(Table.POINTS_MAPPINGS), 6, 5); }
        return points;
    }
    /** ObjFlower_Init/Animate (Flower/Main.asm:42,95-97); no saved-flags, floor or object-lifetime admission. */
    public synchronized CdObjectVisualAssets flower() {
        if (flower == null) {
            flower = CdObjectVisualAssets.decode(read(Table.FLOWER_MAPPINGS), 8,
                    read(Table.FLOWER_ANIMATIONS), 4, 39);
        }
        return flower;
    }
    /** Explicit opt-in from the retained authenticated MMD; does not extend either existing bank. */
    public synchronized ContactArtBank contactArtBank() throws IOException {
        if (contactArtBank == null) {
            // These are the init base words. Explosion layer and Flower palette changes belong to object execution.
            contactArtBank = new ContactArtBank(List.of(
                    contactArt(CdR11aAsset.ART_EXPLOSIONS, 10, 0xD000, 0x8680, 70, 1256),
                    contactArt(CdR11aAsset.ART_POINTS, 11, 0xD8C0, 0x06C6, 5, 101),
                    contactArt(CdR11aAsset.ART_FLOWER, 12, 0xDAE0, 0xA6D7, 39, 645)));
        }
        return contactArtBank;
    }
    /** ObjFloatBlock_Init/Appear/Visible/Vanish (Floating Block/Main.asm:42,48,110,129,140); scripts are not executed. */
    public synchronized CdObjectVisualAssets floatBlock() {
        if (floatBlock == null) {
            floatBlock = CdObjectVisualAssets.decode(read(Table.FLOAT_BLOCK_MAPPINGS), 6,
                    read(Table.FLOAT_BLOCK_ANIMATIONS), 3, 15);
        }
        return floatBlock;
    }
    /** Explicit opt-in from the retained authenticated MMD; does not extend any existing bank. */
    public synchronized FloatBlockArtBank floatBlockArtBank() throws IOException {
        if (floatBlockArtBank == null) {
            // PLC_Std row 2 (Data (Act 1 Present).asm:60-61); Init's MOVEQ #5 / JSR SetObjectTileID supplies the base
            // word (Floating Block/Main.asm:43-44). Every actual R11A FloatBlock record has subtype2 0.
            var source = CdR11aAsset.ART_FLOAT_BLOCK;
            var entries = loadPlan.plcs().get(1).entries();
            var entry = entries.get(2);
            var blob = profile.range(source);
            int baseWord = tileWord(5, 0);
            if (entry.source() != source || entry.vramAddress() != 0x6680 || !entry.sourceRange().equals(blob)
                    || entries.stream().filter(p -> p.source() == source).count() != 1
                    || (baseWord & 0x7FF) * 32 != entry.vramAddress()
                    || source.encodingForReviewedConsumer() != CdR11aAsset.Encoding.NEMESIS) {
                throw new IllegalArgumentException("Unverified native FloatBlock PLC row/base-word/source association");
            }
            // The independent prefix walk needs all 230 source bytes; the 8-bit reader's final peek also needs the
            // real next MMD byte, Art_SpringWheel's first byte at $366B6, never invented EOF padding.
            var reader = new CdR11aProfile.FileRange(blob.file(), blob.domain(), blob.fileOffset(), 231);
            var decoded = CdNemesisAsset.decode(reader.read(image).copy(), 15 * 32, 15 * 32);
            if (decoded.consumedInputBytes() != reader.length()) {
                throw new IOException("FloatBlock Nemesis reader consumption differs from its reviewed finite extent");
            }
            var read = new CdR11aAssets.NemesisRead(source, blob, reader, decoded.consumedInputBytes(), decoded.bytes());
            floatBlockArtBank = new FloatBlockArtBank(List.of(new ArtBinding(source, 1, entry, baseWord, read)));
        }
        return floatBlockArtBank;
    }


    /**
     * Independent native Spring1 index at include+0; retain the complete authenticated shared mapping source.
     * Spring/Main.asm:188 and Data/Mappings.asm:1-44. No normal-art decode, diagonal or body admission.
     */
    public synchronized CdObjectVisualAssets springVertical() {
        if (springVertical == null) {
            springVertical = CdObjectVisualAssets.decodeIndexed(read(Table.SPRING_MAPPINGS), 0, 3, 12, 46,
                    read(Table.SPRING_ANIMATIONS), 1, 36);
        }
        return springVertical;
    }

    /** Independent Spring2 index at include+6, with native signed words relative to that root. */
    public synchronized CdObjectVisualAssets springHorizontal() {
        if (springHorizontal == null) {
            springHorizontal = CdObjectVisualAssets.decodeIndexed(read(Table.SPRING_MAPPINGS), 6, 3, 46, 80,
                    read(Table.SPRING_ANIMATIONS), 1, 36);
        }
        return springHorizontal;
    }

    /**
     * USA Spring3 operand $209BF0 (Spring/Main.asm:216); its root is +80 in the existing full200 include.
     * Data/Mappings.asm:46-82 declares three counted bodies at86/122/158 with a real final EVEN byte199.
     * ObjSpring_Anim_Diag (:470-473) uses the same complete Ani_Spring script. No art or live residency here.
     */
    public synchronized CdObjectVisualAssets springDiagonal() {
        if (springDiagonal == null) {
            springDiagonal = CdObjectVisualAssets.decodeIndexed(read(Table.SPRING_MAPPINGS), 80, 3, 86, 200,
                    read(Table.SPRING_ANIMATIONS), 1, 46);
        }
        return springDiagonal;
    }

    /** ObjSpring_Init reads one signed native word through ANDI.W #2; unrelated subtype bits stay irrelevant. */
    public synchronized int springSpeedWord(int subtypeByte) {
        if ((subtypeByte & ~0xFF) != 0) throw new IllegalArgumentException("Spring subtype exceeds its native byte");
        return (short) view(Table.SPRING_SPEEDS).bytes().unsignedWord(subtypeByte & 2);
    }

    /**
     * Explicit normal-art opt-in from the retained authenticated USA MMD, not an object or renderer admission.
     * Spring's shared vertical/horizontal mapping headers remain raw views: the existing uniform visual decoder
     * cannot represent them. No rebased mappings, invented scripts or new decoder path are supplied here.
     */
    public synchronized SpringArtBank springArtBank() throws IOException {
        if (springArtBank == null) {
            // Spring/Main.asm:188-189 writes the full raw $0520 word; PLC_Std row 7 owns destination $A400
            // (Data (Act 1 Present).asm:70-71). ObjSpring2's $8520 and the high-byte palette BSET belong to execution.
            var source = CdR11aAsset.ART_SPRINGS;
            var entries = loadPlan.plcs().get(1).entries();
            var entry = entries.get(7);
            var blob = profile.range(source);
            int baseWord = 0x0520;
            if (entry.source() != source || entry.vramAddress() != 0xA400 || !entry.sourceRange().equals(blob)
                    || entries.stream().filter(p -> p.source() == source).count() != 1
                    || (baseWord & 0x7FF) * 32 != entry.vramAddress()
                    || source.encodingForReviewedConsumer() != CdR11aAsset.Encoding.NEMESIS) {
                throw new IllegalArgumentException("Unverified native Spring PLC row/base-word/source association");
            }
            // The prefix walk needs 546 source bytes. The finite 8-bit reader additionally consumes the real
            // first Art_MonitorTimePosts byte ($80 at $34B3A); the source blob remains distinct from reader input.
            var reader = new CdR11aProfile.FileRange(blob.file(), blob.domain(), blob.fileOffset(), 547);
            var decoded = CdNemesisAsset.decode(reader.read(image).copy(), 36 * 32, 36 * 32);
            if (decoded.consumedInputBytes() != reader.length()) {
                throw new IOException("Spring Nemesis reader consumption differs from its reviewed finite extent");
            }
            var read = new CdR11aAssets.NemesisRead(source, blob, reader, decoded.consumedInputBytes(), decoded.bytes());
            springArtBank = new SpringArtBank(List.of(new ArtBinding(source, 1, entry, baseWord, read)));
        }
        return springArtBank;
    }

    /** ObjTunnelDoorV_Init/Main/Reset (Tunnel Door/Main.asm:34-83) selects two frames directly, without AnimateObject. */
    public synchronized CdObjectVisualAssets tunnelDoorVertical() {
        if (tunnelDoorVertical == null) {
            tunnelDoorVertical = CdObjectVisualAssets.decodeStatic(read(Table.TUNNEL_DOOR_VERTICAL_MAPPINGS), 2, 12);
        }
        return tunnelDoorVertical;
    }

    /** Explicit standard-art opt-in from the retained authenticated USA MMD; does not extend any existing bank. */
    public synchronized TunnelDoorArtBank tunnelDoorArtBank() throws IOException {
        if (tunnelDoorArtBank == null) {
            // PLC_Std row 1 (Data (Act 1 Present).asm:58-59), Init selector $C (Tunnel Door/Main.asm:41-42)
            // and ObjectTileIDs selector $C at subtype2 0 (Object Tile IDs (Act 1 Present).asm:20,40).
            var source = CdR11aAsset.ART_TUNNEL_DOOR;
            var entries = loadPlan.plcs().get(1).entries();
            var entry = entries.get(1);
            var blob = profile.range(source);
            int baseWord = tileWord(0xC, 0);
            if (entry.source() != source || entry.vramAddress() != 0x6500 || !entry.sourceRange().equals(blob)
                    || entries.stream().filter(p -> p.source() == source).count() != 1
                    || (baseWord & 0x7FF) * 32 != entry.vramAddress()
                    || source.encodingForReviewedConsumer() != CdR11aAsset.Encoding.NEMESIS) {
                throw new IllegalArgumentException("Unverified native Tunnel Door PLC row/base-word/source association");
            }
            // The prefix walk consumes 152 bytes; the finite 8-bit reader needs 153, all inside the 154-byte
            // source. Its real lookahead byte is $00 at $36B96; neither adjacent splash art nor EOF padding is read.
            var reader = new CdR11aProfile.FileRange(blob.file(), blob.domain(), blob.fileOffset(), 153);
            var decoded = CdNemesisAsset.decode(reader.read(image).copy(), 12 * 32, 12 * 32);
            if (decoded.consumedInputBytes() != reader.length()) {
                throw new IOException("Tunnel Door Nemesis reader consumption differs from its reviewed finite extent");
            }
            var read = new CdR11aAssets.NemesisRead(source, blob, reader, decoded.consumedInputBytes(), decoded.bytes());
            tunnelDoorArtBank = new TunnelDoorArtBank(List.of(new ArtBinding(source, 1, entry, baseWord, read)));
        }
        return tunnelDoorArtBank;
    }

    /**
     * Full counted Monitor/post/HUD directory and ten real scripts, using the unchanged strict decoder.
     * Monitor and Time Post/Main.asm:38,100,304,366,644-650. Item first-piece pointers remain raw bytes
     * in view(MONITOR_TIME_MAPPINGS); this accessor registers no header-free static mapping consumer.
     */
    public synchronized CdObjectVisualAssets monitorTime() {
        if (monitorTime == null) {
            monitorTime = CdObjectVisualAssets.decode(read(Table.MONITOR_TIME_MAPPINGS), 20,
                    read(Table.MONITOR_TIME_ANIMATIONS), 10, 82);
        }
        return monitorTime;
    }

    /** Explicit standard-art opt-in from the retained authenticated USA MMD; no existing bank is extended. */
    public synchronized MonitorTimeArtBank monitorTimeArtBank() throws IOException {
        if (monitorTimeArtBank == null) {
            // PLC_Std row 9 (Data (Act 1 Present).asm:74-75) and ObjMonitor_Init's literal $05A8
            // (Monitor and Time Post/Main.asm:305). Item/HUD $85A8 and MMZ attributes belong to execution.
            // Shipped FixBugs=0 source is the authority; this descriptive binding executes no object or PLC.
            var source = CdR11aAsset.ART_MONITOR_TIME_POSTS;
            var entries = loadPlan.plcs().get(1).entries();
            var entry = entries.get(9);
            var blob = profile.range(source);
            int baseWord = 0x05A8;
            if (entry.source() != source || entry.vramAddress() != 0xB500 || !entry.sourceRange().equals(blob)
                    || entries.stream().filter(p -> p.source() == source).count() != 1
                    || (baseWord & 0x7FF) * 32 != entry.vramAddress()
                    || source.encodingForReviewedConsumer() != CdR11aAsset.Encoding.NEMESIS) {
                throw new IllegalArgumentException("Unverified native Monitor/time-post PLC row/base-word/source association");
            }
            // Actual header $8052 declares XOR/82 tiles. Independent prefix walk needs all 1540 source bytes;
            // the existing 8-bit reader needs 1541, including the real first Explosion header byte $00 at
            // MMD $3513E. Preserve the source/reader distinction; never invent EOF padding.
            var reader = new CdR11aProfile.FileRange(blob.file(), blob.domain(), blob.fileOffset(), 1541);
            var decoded = CdNemesisAsset.decode(reader.read(image).copy(), 82 * 32, 82 * 32);
            if (decoded.consumedInputBytes() != reader.length()) {
                throw new IOException("Monitor/time-post Nemesis reader consumption differs from its reviewed finite extent");
            }
            var read = new CdR11aAssets.NemesisRead(source, blob, reader, decoded.consumedInputBytes(), decoded.bytes());
            monitorTimeArtBank = new MonitorTimeArtBank(List.of(new ArtBinding(source, 1, entry, baseWord, read)));
        }
        return monitorTimeArtBank;
    }

    /** Normal Spin Tunnel Door: two counted frames and its one real FC script, sharing the existing standard art. */
    public synchronized CdObjectVisualAssets tunnelDoor() {
        if (tunnelDoor == null) {
            tunnelDoor = CdObjectVisualAssets.decode(read(Table.TUNNEL_DOOR_MAPPINGS), 2,
                    read(Table.TUNNEL_DOOR_ANIMATIONS), 1, 12);
        }
        return tunnelDoor;
    }

    /**
     * ObjPataBata's subtype-zero mapping root; both real shared scripts remain visible and bounded to frames 0..2.
     * Pata-Bata/Main.asm:23-30,76-92. The native body selects script 0; no animation or movement executes here.
     */
    public synchronized CdObjectVisualAssets pataBataNormal() {
        if (pataBataNormal == null) {
            pataBataNormal = CdObjectVisualAssets.decode(read(Table.PATA_BATA_NORMAL_MAPPINGS), 3,
                    read(Table.PATA_BATA_ANIMATIONS), 2, 50);
        }
        return pataBataNormal;
    }

    /** Nonzero native subtype selects this mapping root and script 1 from the same complete shared script view. */
    public synchronized CdObjectVisualAssets pataBataDamaged() {
        if (pataBataDamaged == null) {
            pataBataDamaged = CdObjectVisualAssets.decode(read(Table.PATA_BATA_DAMAGED_MAPPINGS), 3,
                    read(Table.PATA_BATA_ANIMATIONS), 2, 50);
        }
        return pataBataDamaged;
    }

    /** Explicit opt-in from the retained authenticated USA MMD; no object, camera PLC or live residency admission. */
    public synchronized PataBataArtBank pataBataArtBank() throws IOException {
        if (pataBataArtBank == null) {
            // PLC_Cam2_Full row 2 (Data (Act 1 Present).asm:102-115) owns $7600; Pata-Bata/Main.asm:21-22
            // selects ObjectTileIDs index 1, subtype2 0 ($23B0). All 35 actual R11A placements use subtype2 0.
            // Cam3_Full/Cam2_Incr/Cam3_Incr repeat this source/destination; none is executed by this accessor.
            var source = CdR11aAsset.ART_PATA_BATA;
            var entries = loadPlan.plcs().get(4).entries();
            var entry = entries.get(2);
            var blob = profile.range(source);
            int baseWord = tileWord(1, 0);
            if (entry.source() != source || entry.vramAddress() != 0x7600 || !entry.sourceRange().equals(blob)
                    || entries.stream().filter(p -> p.source() == source).count() != 1
                    || (baseWord & 0x7FF) * 32 != entry.vramAddress()
                    || source.encodingForReviewedConsumer() != CdR11aAsset.Encoding.NEMESIS) {
                throw new IllegalArgumentException("Unverified native Pata-Bata PLC row/base-word/source association");
            }
            // Actual header $8032 declares XOR/50 tiles. The prefix walk needs 713 bytes, while the existing
            // 8-bit reader consumes all 714 real source bytes, including the $00 at MMD $37641. No adjacent
            // source or invented padding is read; the source blob and finite reader have the same reviewed range.
            var reader = new CdR11aProfile.FileRange(blob.file(), blob.domain(), blob.fileOffset(), 714);
            var decoded = CdNemesisAsset.decode(reader.read(image).copy(), 50 * 32, 50 * 32);
            if (decoded.consumedInputBytes() != reader.length()) {
                throw new IOException("Pata-Bata Nemesis reader consumption differs from its reviewed finite extent");
            }
            var read = new CdR11aAssets.NemesisRead(source, blob, reader, decoded.consumedInputBytes(), decoded.bytes());
            pataBataArtBank = new PataBataArtBank(List.of(new ArtBinding(source, 4, entry, baseWord, read)));
        }
        return pataBataArtBank;
    }

    /** Boulder/Main.asm:30,57-59: one counted static frame, nineteen source tiles, no animation script. */
    public synchronized CdObjectVisualAssets boulder() {
        if (boulder == null) { boulder = CdObjectVisualAssets.decodeStatic(read(Table.BOULDER_MAPPINGS), 1, 19); }
        return boulder;
    }

    /** Common Spikes/Main.asm:21,87-89: one counted static frame, eight source tiles, no animation script. */
    public synchronized CdObjectVisualAssets spikes() {
        if (spikes == null) { spikes = CdObjectVisualAssets.decodeStatic(read(Table.SPIKES_MAPPINGS), 1, 8); }
        return spikes;
    }

    /**
     * Checkpoint/Main.asm:74-75,101-107,187-189: parent and ball share four native counted frames
     * and one real FF script. The native literal base $06CB is not a SetObjectTileID selector.
     * This reads raw visuals only; no art decode, object, save data or current residency is admitted.
     */
    public synchronized CdObjectVisualAssets checkpoint() {
        if (checkpoint == null) {
            checkpoint = CdObjectVisualAssets.decode(read(Table.CHECKPOINT_MAPPINGS), 4,
                    read(Table.CHECKPOINT_ANIMATIONS), 1, 12);
        }
        return checkpoint;
    }


    /**
     * Anton/Main.asm:27,85,98-103: all four counted frames share one two-script table.
     * The damaged heads use source tiles 18..21. Init leaves oMapFrame unchanged.
     * Raw visuals reuse the authenticated retained image; no art decode or residency is admitted.
     */
    public synchronized CdObjectVisualAssets anton() {
        if (anton == null) {
            anton = CdObjectVisualAssets.decode(read(Table.ANTON_MAPPINGS), 4,
                    read(Table.ANTON_ANIMATIONS), 2, 22);
        }
        return anton;
    }

    /** Native counted map 1; breakup scripts stay in raw size views, never AnimateObject data. */
    public synchronized CdObjectVisualAssets collapsePlatform1() {
        if (collapsePlatform1 == null) {
            collapsePlatform1 = CdObjectVisualAssets.decodeStatic(
                    read(Table.COLLAPSE_PLATFORM_MAPPINGS_1), 1, 57);
        }
        return collapsePlatform1;
    }

    /** Native counted map 2; breakup scripts stay in raw size views, never AnimateObject data. */
    public synchronized CdObjectVisualAssets collapsePlatform2() {
        if (collapsePlatform2 == null) {
            collapsePlatform2 = CdObjectVisualAssets.decodeStatic(
                    read(Table.COLLAPSE_PLATFORM_MAPPINGS_2), 6, 57);
        }
        return collapsePlatform2;
    }

    /** Native counted map 3; breakup scripts stay in raw size views, never AnimateObject data. */
    public synchronized CdObjectVisualAssets collapsePlatform3() {
        if (collapsePlatform3 == null) {
            collapsePlatform3 = CdObjectVisualAssets.decodeStatic(
                    read(Table.COLLAPSE_PLATFORM_MAPPINGS_3), 7, 57);
        }
        return collapsePlatform3;
    }

    /** Native counted map 4; breakup scripts stay in raw size views, never AnimateObject data. */
    public synchronized CdObjectVisualAssets collapsePlatform4() {
        if (collapsePlatform4 == null) {
            collapsePlatform4 = CdObjectVisualAssets.decodeStatic(
                    read(Table.COLLAPSE_PLATFORM_MAPPINGS_4), 6, 57);
        }
        return collapsePlatform4;
    }

    /** Platform/Main.asm:37-55,559-561: three counted static frames over the existing 57-tile source. */
    public synchronized CdObjectVisualAssets platform() {
        if (platform == null) {
            platform = CdObjectVisualAssets.decodeStatic(read(Table.PLATFORM_MAPPINGS), 3, 57);
        }
        return platform;
    }

    /** Obj3DPlant_Init (3D Ramp/Main.asm:57-90): ADD.W consumes signed offsets in two distinct bodies. */
    public static PlantOffsets decodePlantOffsets(byte[] input) {
        var bytes = exact(input, 14, "Obj3DPlant_Offsets1/Obj3DPlant_Offsets2");
        var main = new ArrayList<Integer>();
        var small = new ArrayList<Integer>();
        for (int i = 0; i < 4; i++) { main.add((int) (short) bytes.unsignedWord(i * 2)); }
        for (int i = 0; i < 3; i++) { small.add((int) (short) bytes.unsignedWord(8 + i * 2)); }
        return new PlantOffsets(main, small);
    }
    /** ObjAnimal_SetBaseTile (Animal/Main.asm:209-244): word at (act * 4 + timeZone) * 2. */
    public static int decodeAnimalBaseTile(byte[] input, int act, int timeZone) {
        if (act < 0 || act > 2 || timeZone < 0 || timeZone > 2) {
            throw new IllegalArgumentException("Animal base tile requires native act/timeZone 0..2");
        }
        return exact(input, 22, "ObjAnimal_BaseTileList").unsignedWord((act * 4 + timeZone) * 2);
    }

    /**
     * ObjTamabboh_Init (Tamabboh/Main.asm:40-57) selects the native root at include+0.
     * Full Mappings.asm:1-60 retains the other root's six-byte index and damaged-only six-byte body
     * between normal frames 0 and 1. Those twelve bytes remain raw, never counted normal pieces.
     * This reads only the retained mapping/script tables; source ordinal 3 already owns Tamabboh art.
     */
    public synchronized CdObjectVisualAssets tamabbohNormal() {
        if (tamabbohNormal == null) {
            tamabbohNormal = CdObjectVisualAssets.decode(read(Table.TAMABBOH_MAPPINGS), 3,
                    read(Table.TAMABBOH_NORMAL_ANIMATIONS), 2, 59, Map.of(0, 12));
        }
        return tamabbohNormal;
    }

    /** Native damaged root is full include+22 ($20B97E); both later bodies are shared with the normal root. */
    public synchronized CdObjectVisualAssets tamabbohDamaged() {
        if (tamabbohDamaged == null) {
            tamabbohDamaged = CdObjectVisualAssets.decodeIndexed(read(Table.TAMABBOH_MAPPINGS), 22, 3, 28, 62,
                    read(Table.TAMABBOH_DAMAGED_ANIMATIONS), 2, 59);
        }
        return tamabbohDamaged;
    }

    /** Tamabboh/Main.asm:226-234,280-289: four child frames and its one real FF script, using parent art. */
    public synchronized CdObjectVisualAssets tamabbohMissile() {
        if (tamabbohMissile == null) {
            tamabbohMissile = CdObjectVisualAssets.decode(read(Table.TAMABBOH_MISSILE_MAPPINGS), 4,
                    read(Table.TAMABBOH_MISSILE_ANIMATIONS), 1, 59);
        }
        return tamabbohMissile;
    }


    /**
     * ObjSpinningDisc_Init stores MapSpr_SpinningDisc ($2095C6); the wrapper's absolute LEA
     * at $2093BC selects Ani_SpinningDisc ($2095BE). Main.asm:111 keeps the separate raw
     * sixteen-byte player-frame table on view(Table.SPINNING_DISC_PLAYER_FRAMES).
     * Three counted frames use exactly twelve tiles from the existing section source.
     */
    public synchronized CdObjectVisualAssets spinningDisc() {
        if (spinningDisc == null) {
            spinningDisc = CdObjectVisualAssets.decode(read(Table.SPINNING_DISC_MAPPINGS), 3,
                    read(Table.SPINNING_DISC_ANIMATIONS), 1, 12);
        }
        return spinningDisc;
    }

    /** ObjLogShadow_Init selects the complete two-frame counted mapping table without animation scripts. */
    public synchronized CdObjectVisualAssets logShadow() {
        if (logShadow == null) {
            logShadow = CdObjectVisualAssets.decodeStatic(read(Table.LOG_SHADOW_MAPPINGS), 2, 16);
        }
        return logShadow;
    }

    /** Pure component decoder; signed bytes are extended as in ObjRing_Init (Ring/Main.asm:81-85). */
    public static List<Delta> decodeRingDeltas(byte[] input) {
        var bytes = exact(input, 32, "ObjRing_Deltas");
        var result = new ArrayList<Delta>();
        for (int i = 0; i < 16; i++) { result.add(new Delta(bytes.signedByte(i * 2), bytes.signedByte(i * 2 + 1))); }
        return List.copyOf(result);
    }
    /** Pure component decoder for the 63 unsigned half-size pairs; executes no touch handler. */
    public static List<TouchSize> decodeTouchDimensions(byte[] input) {
        var bytes = exact(input, 126, "ObjColSizes");
        var result = new ArrayList<TouchSize>();
        for (int i = 0; i < 63; i++) { result.add(new TouchSize(bytes.unsignedByte(i * 2), bytes.unsignedByte(i * 2 + 1))); }
        return List.copyOf(result);
    }
    /**
     * SetObjectTileID (Object Tile ID Set.asm:7-17) has a signed relative word index, then subtype2 words.
     * The source declares 18 index words (36 bytes), followed by 19 data words, not 37 index entries.
     * Only Mosqui's selector 0, Pata-Bata's selector 1 (Pata-Bata/Main.asm:21-22), FloatBlock's selector 5
     * (Floating Block/Main.asm:43-44), Boulder's selector $B (Boulder/Main.asm:34-35), and Tunnel Door's
     * selector $C (Tunnel Door/Main.asm:41-42), and primary diagonal Spring's selector $F
     * (Spring/Main.asm:217-220), and common Spikes' selector $A (Spikes/Main.asm:24-25),
     * and Tamabboh selector 4 (Tamabboh/Main.asm:38-39), each at subtype2 0, are admitted. Anton selector 2 (Anton/Main.asm:33-34) additionally admits
     * subtype2 0/1 from its two-word body (Object Tile IDs (Act 1 Present).asm:29-30).
     * Spinning Disc selector 6 (Spinning Disc/Main.asm:41-42) admits only subtype2 0 ($0409).
     * Ring writes literal $A7AE.
     */
    public static int decodeTileWord(byte[] input, int selector, int subtype2) {
        if (selector < 0 || selector > 0x7FFF || (subtype2 & ~0xFF) != 0) {
            throw new IllegalArgumentException("Object tile selector/subtype width");
        }
        var bytes = exact(input, 74, "ObjectTileIDs");
        if ((selector != 0 && selector != 1 && selector != 2 && selector != 4 && selector != 5 && selector != 6 && selector != 0xA && selector != 0xB && selector != 0xC && selector != 0xF)
                || (selector == 2 ? subtype2 > 1 : subtype2 != 0)) {
            throw new UnsupportedOperationException("Only Mosqui selector 0 / Pata-Bata selector 1 / Tamabboh selector 4 / FloatBlock selector 5 / Spinning Disc selector 6 / common Spikes selector $A / Boulder selector $B / Tunnel Door selector $C / primary diagonal Spring selector $F at subtype2 0, or Anton selector 2 at subtype2 0/1, are reviewed");
        }
        int target = (short) bytes.unsignedWord(selector * 2);
        int end = bytes.length();
        for (int i = 0; i < 18; i++) {
            int offset = (short) bytes.unsignedWord(i * 2);
            if (offset < 36 || (offset & 1) != 0 || offset > bytes.length() - 2) {
                throw new IllegalArgumentException("Object tile signed relative word points outside declared data bodies");
            }
            if (offset > target && offset < end) { end = offset; }
        }
        int selected = target + subtype2 * 2;
        if (end - selected < 2) {
            throw new IllegalArgumentException((selector == 0 ? "Mosqui" : selector == 1 ? "Pata-Bata" : selector == 2 ? "Anton" : selector == 4 ? "Tamabboh" : selector == 5 ? "FloatBlock" : selector == 6 ? "Spinning Disc"
                    : selector == 0xA ? "Common Spikes" : selector == 0xB ? "Boulder" : selector == 0xC ? "Tunnel Door" : "Primary diagonal Spring")
                    + " tile word crosses its selected body");
        }
        return bytes.unsignedWord(selected);
    }

    private static ArtBinding art(CdR11aProfile profile, CdMmdImage image, CdR11aLoadPlan plan,
                                    CdR11aAsset source, int plcId, int vram, int baseWord, int tiles) throws IOException {
        var matches = plan.plcs().get(plcId).entries().stream().filter(p -> p.source() == source).toList();
        var blob = profile.range(source);
        if (matches.size() != 1 || matches.getFirst().vramAddress() != vram
                || !matches.getFirst().sourceRange().equals(blob) || (baseWord & 0x7FF) * 32 != vram
                || source.encodingForReviewedConsumer() != CdR11aAsset.Encoding.NEMESIS) {
            throw new IllegalArgumentException("Unverified native object PLC/base-word/source association");
        }
        // Independent prefix/8-bit-lookahead checks need exactly 244/846 real bytes respectively.
        // No adjacent input or synthetic EOF padding is needed for these two reviewed sources.
        var decoded = CdNemesisAsset.decode(blob.read(image).copy(), tiles * 32, tiles * 32);
        var read = new CdR11aAssets.NemesisRead(source, blob, blob, decoded.consumedInputBytes(), decoded.bytes());
        return new ArtBinding(source, plcId, matches.getFirst(), baseWord, read);
    }
    private ArtBinding frontierArt(CdR11aAsset source, int row, int vram, int baseWord, int tiles,
                                    int readerBytes) throws IOException {
        // PLC_Cam1_Full (Data (Act 1 Present).asm:85-100), not a completed transfer or live section state.
        var entries = loadPlan.plcs().get(2).entries();
        var entry = entries.get(row);
        var blob = profile.range(source);
        if (entry.source() != source || entry.vramAddress() != vram || !entry.sourceRange().equals(blob)
                || entries.stream().filter(p -> p.source() == source).count() != 1
                || (baseWord & 0x7FF) * 32 != vram
                || source.encodingForReviewedConsumer() != CdR11aAsset.Encoding.NEMESIS) {
            throw new IllegalArgumentException("Unverified native frontier PLC row/base-word/source association");
        }
        // Independent prefix/8-bit-reader checks: Animals 445 of 446 source bytes, Ramp 677 of 678;
        // Plant needs 393 for its 392-byte source (real next MMD byte at $3AE02, never invented padding).
        var reader = new CdR11aProfile.FileRange(blob.file(), blob.domain(), blob.fileOffset(), readerBytes);
        var decoded = CdNemesisAsset.decode(reader.read(image).copy(), tiles * 32, tiles * 32);
        if (decoded.consumedInputBytes() != readerBytes) {
            throw new IOException("Frontier Nemesis reader consumption differs from its reviewed finite extent");
        }
        var read = new CdR11aAssets.NemesisRead(source, blob, reader, decoded.consumedInputBytes(), decoded.bytes());
        return new ArtBinding(source, 2, entry, baseWord, read);
    }
    private ArtBinding contactArt(CdR11aAsset source, int row, int vram, int baseWord, int tiles,
                                   int readerBytes) throws IOException {
        // PLC_Std (Data (Act 1 Present).asm:54-83), specifically the three adjacent source rows 10..12.
        var entries = loadPlan.plcs().get(1).entries();
        var entry = entries.get(row);
        var blob = profile.range(source);
        if (entry.source() != source || entry.vramAddress() != vram || !entry.sourceRange().equals(blob)
                || entries.stream().filter(p -> p.source() == source).count() != 1
                || (baseWord & 0x7FF) * 32 != vram
                || source.encodingForReviewedConsumer() != CdR11aAsset.Encoding.NEMESIS) {
            throw new IllegalArgumentException("Unverified native contact PLC row/base-word/source association");
        }
        // Independent prefix/8-bit-reader checks: Explosions 1256 of 1256, Flower 645 of 646;
        // Points needs 101 for its 100-byte source: real FlowerCapsule byte $80 at MMD $31628.
        var reader = new CdR11aProfile.FileRange(blob.file(), blob.domain(), blob.fileOffset(), readerBytes);
        var decoded = CdNemesisAsset.decode(reader.read(image).copy(), tiles * 32, tiles * 32);
        if (decoded.consumedInputBytes() != readerBytes) {
            throw new IOException("Contact Nemesis reader consumption differs from its reviewed finite extent");
        }
        var read = new CdR11aAssets.NemesisRead(source, blob, reader, decoded.consumedInputBytes(), decoded.bytes());
        return new ArtBinding(source, 1, entry, baseWord, read);
    }
    private byte[] read(Table table) { return view(table).bytes().copy(); }
    private static List<Integer> signedWords(List<Integer> words, int count) {
        var result = List.copyOf(words);
        if (result.size() != count || result.stream().anyMatch(word -> word < Short.MIN_VALUE || word > Short.MAX_VALUE)) {
            throw new IllegalArgumentException("Plant offsets must contain the declared signed-word body");
        }
        return result;
    }
    private static byte[] read(Map<Table, View> views, Table table) { return views.get(table).bytes().copy(); }
    private static CdAssetBytes exact(byte[] input, int length, String label) {
        if (input.length != length) { throw new IllegalArgumentException(label + " extent"); }
        return CdAssetBytes.copyOf(input);
    }


    /**
     * Immutable source provenance for one reviewed section-art producer. Canonical PLC metadata is descriptive:
     * it never selects a runtime list, destination, transfer, native tile binding or current residency.
     * The native reader range includes genuine prefill/refill bytes; decode() retains its existing Java range.
     */
    public static final class SectionArtSource {
        private final CdR11aAsset source;
        private final int canonicalPlcId;
        private final int canonicalRowIndex;
        private final CdR11aLoadPlan.PlcEntry canonicalPlc;
        private final CdR11aAssets.NemesisRead decode;
        private final CdR11aProfile.FileRange readerInputRange;
        private final CdAssetBytes readerBytes;

        private SectionArtSource(CdR11aAsset source, int canonicalPlcId, int canonicalRowIndex,
                                 CdR11aLoadPlan.PlcEntry canonicalPlc, CdR11aAssets.NemesisRead decode,
                                 CdR11aProfile.FileRange readerInputRange, CdAssetBytes readerBytes) {
            this.source = source;
            this.canonicalPlcId = canonicalPlcId;
            this.canonicalRowIndex = canonicalRowIndex;
            this.canonicalPlc = canonicalPlc;
            this.decode = decode;
            this.readerInputRange = readerInputRange;
            this.readerBytes = readerBytes;
        }
        public CdR11aAsset source() { return source; }
        public int canonicalPlcId() { return canonicalPlcId; }
        public int canonicalRowIndex() { return canonicalRowIndex; }
        public CdR11aLoadPlan.PlcEntry canonicalPlc() { return canonicalPlc; }
        public CdR11aAssets.NemesisRead decode() { return decode; }
        public CdR11aProfile.FileRange readerInputRange() { return readerInputRange; }
        public CdAssetBytes readerBytes() { return readerBytes; }
        public int tileCount() { return decode.bytes().length() / 32; }
    }

    /** Finite authenticated inputs and immutable decode products, with no native-address/tile resolver. */
    public static final class SectionArtSourceBank {
        private final List<SectionArtSource> sources;
        private SectionArtSourceBank(List<SectionArtSource> sources) { this.sources = List.copyOf(sources); }
        public List<SectionArtSource> sources() { return sources; }
        public SectionArtSource source(CdR11aAsset source) {
            Objects.requireNonNull(source, "source");
            return sources.stream().filter(value -> value.source() == source).findFirst()
                    .orElseThrow(() -> new UnsupportedOperationException("No reviewed section-art source for " + source));
        }
        /**
         * Match only the producer and authenticated source extent. In particular, native Anton relocation
         * remains valid: the caller owns the actual selected list, its order and this entry's destination.
         */
        public SectionArtSource source(CdR11aLoadPlan.PlcEntry entry) {
            Objects.requireNonNull(entry, "entry");
            var value = source(entry.source());
            if (!value.decode().sourceBlobRange().equals(entry.sourceRange())) {
                throw new IllegalArgumentException("Section-art PLC source differs from the authenticated extent");
            }
            return value;
        }
    }

    private SectionArtSourceBank sectionArtSources;

    /**
     * Explicit USA-only source opt-in. The complete first incremental camera PLC is ID 9, rows 0/1/2
     * (PLC_Cam2_Incr, Data (Act 1 Present).asm:183-190); PLC2 rows 2/4/5 describe the reload/seed producers.
     * Boulder appends canonical PLC2 row 0 (:85-88); diagonal Spring appends PLC_Std ID1 row5 (:66-67).
     * Common Spikes appends canonical PLC_Std ID1 row0 (:56-57). All are immutable source provenance
     * without a separate live art bank or a new current residency owner.
     * Existing banks and eager startup remain separate. This neither executes a PLC nor publishes residency.
     */
    public synchronized SectionArtSourceBank sectionArtSources() throws IOException {
        if (sectionArtSources == null) {
            var pata = pataBataArtBank().binding(CdR11aAsset.ART_PATA_BATA).decode();
            var anton = decodeSectionArt(CdR11aAsset.ART_ANTON, 22, 484);
            var tamabboh = decodeSectionArt(CdR11aAsset.ART_TAMABBOH, 59, 1142);
            var mosqui = initialArtBank.binding(CdR11aAsset.ART_MOSQUI).decode();
            var frontier = frontierArtBank();
            // Boulder header $8013 declares XOR/19 tiles. Java's final 8-bit peek consumes all 270 source
            // bytes; native refill before the final run additionally reads FloatBlock's real $80 at $365D0.
            // One immutable decode is shared by every Boulder lookup in this cached source bank.
            var boulder = decodeSectionArt(CdR11aAsset.ART_BOULDER, 19, 270);
            // Diagonal header $002E declares normal/46 tiles. Independent actual-USA finite walks establish
            // source780, Java779 and native780: native's final post-symbol refill reads the real source $00
            // at $34917. The adjacent normal-Spring header $8024 at $34918 is observed but never consumed.
            // One immutable NemesisRead is cached here; physical $9200..$97C0 remains the existing current
            // residency's responsibility, including later BigRing overwrites. No producer-specific tile bank.
            var diagonal = decodeSectionArt(CdR11aAsset.ART_DIAGONAL_SPRING, 46, 779);
            // Actual USA header $8008 declares XOR/eight tiles at $38018. Independent finite source models
            // establish Java86/native87, not an extent inferred from the 86-byte source inventory. The native
            // final post-symbol refill reads SwingingPlatform's genuine $00 at $3806E; Java consumes only
            // the source through $3806D ($C0). One immutable NemesisRead is shared by this cached source.
            // Physical patterns $320..$327 at $6400..$6500 remain the existing current owner's responsibility.
            var spikes = decodeSectionArt(CdR11aAsset.ART_SPIKES, 8, 86);
            // PLC_Level ID0 row1 (Data (Act 1 Present).asm:47-52; ID3 aliases the same list) owns
            // Art_Checkpoint's $D960 destination. Actual header $800C at $35DC6 declares XOR/12 tiles.
            // Independent finite models establish Java211/native212 from the complete 212-byte source.
            // Native's final inline post-symbol refill reads the real source $00 at $35E99, before the
            // last three nibbles; Java ends at $35E98 ($40). Adjacent data at $35E9A is never consumed.
            // Cache this one immutable decode here; physical $6CB..$6D6 remains the existing current
            // owner's responsibility. This adds no CheckpointArtBank, preload or tile resolver.
            var checkpointRead = decodeSectionArt(CdR11aAsset.ART_CHECKPOINT, 12, 211);
            // Platform's actual USA header $8039 at $362FE declares XOR/57 tiles. Separate finite source
            // models establish Java452/native453: native's final post-symbol refill consumes Boulder's
            // genuine $80 header byte at $364C2. The inventory stays 452 bytes, not padded or extended.
            // One immutable NemesisRead is cached in this bank; $97C0..$9EE0 aliases existing AT_9000.
            // Collapse uses literal tile $44BE, without SetObjectTileID or a secondary-subtype selector.
            var platform = decodeSectionArt(CdR11aAsset.ART_PLATFORM, 57, 452);
            // NemDecMain preloads two bytes (Functions (General).asm:415-418); the native decoder refills
            // below nine bits BEFORE emitting a run, including the final run (:436-441,471-490).
            // Native finite lengths therefore differ from Java's final pre-symbol peek lengths. These are
            // real retained bytes, never padding: Pata reads TagaTaga's $80 at $37642; Anton reads Mosqui's
            // $00 at $3702A; Tamabboh reads Springboard's $00 at $37DC6; Mosqui reads Pata's $80 at $37378;
            // Plant reads Booster's $80 at $3AE02; Ramp's final $00 at $3B0A7 remains inside its own blob.
            // Art_GoalPost (Data (Act 1 Present).asm:99-100,305-310): canonical PLC2 row6;
            // actual PLC4 row5 aliases the same source. Header $8018 declares XOR/24 tiles.
            // Java consumes the original 448 bytes; native449 also reads Signpost's genuine $80
            // at $3229A, never its following $4C. Cache one read here, with no new art bank.
            var goalPostRead = decodeSectionArt(CdR11aAsset.ART_GOAL_POST, 24, 448);
            // PLC_Cam3_Full (Data (Act 1 Present).asm:117-136), rows2/4/5/6, describes these
            // actual PLC10 queued producers. Cache one existing-helper Java decode per source here.
            // Hole: $800E XOR/14 tiles, source114 / Java113 / native114; native's final $00 is internal.
            var sonicHoleRead = decodeSectionArt(CdR11aAsset.ART_SONIC_HOLE, 14, 113);
            // Waterfall: $801D XOR/29 tiles, source328 / Java327 / native328; final refill stays internal.
            var tunnelWaterfallRead = decodeSectionArt(CdR11aAsset.ART_TUNNEL_WATERFALL, 29, 327);
            // Disc: $800C XOR/12 tiles, source166 / Java167 / native167. Both readers need Waterfall's
            // genuine $80 at $36812, never its following $1D at $36813. Do not pad the inventory.
            var spinningDiscRead = decodeSectionArt(CdR11aAsset.ART_SPINNING_DISC, 12, 167);
            // Shadow: $0010 NORMAL/16 tiles, source24 / Java23 / native24; no XOR history is invented.
            var logShadowRead = decodeSectionArt(CdR11aAsset.ART_LOG_SHADOW, 16, 23);
            sectionArtSources = new SectionArtSourceBank(List.of(
                    sectionArtSource(CdR11aAsset.ART_PATA_BATA, 9, 0, 0x7600, 50, 715, pata),
                    sectionArtSource(CdR11aAsset.ART_ANTON, 9, 1, 0x8120, 22, 485, anton),
                    sectionArtSource(CdR11aAsset.ART_TAMABBOH, 9, 2, 0x8500, 59, 1143, tamabboh),
                    sectionArtSource(CdR11aAsset.ART_MOSQUI, 2, 2, 0x7400, 70, 847, mosqui),
                    sectionArtSource(CdR11aAsset.ART_3D_PLANT, 2, 4, 0x8480, 28, 393,
                            frontier.binding(CdR11aAsset.ART_3D_PLANT).decode()),
                    sectionArtSource(CdR11aAsset.ART_3D_RAMP_BOOST, 2, 5, 0x8820, 63, 678,
                            frontier.binding(CdR11aAsset.ART_3D_RAMP_BOOST).decode()),
                    sectionArtSource(CdR11aAsset.ART_BOULDER, 2, 0, 0x6E80, 19, 271, boulder),
                    sectionArtSource(CdR11aAsset.ART_DIAGONAL_SPRING, 1, 5, 0x9200, 46, 780, diagonal),
                    sectionArtSource(CdR11aAsset.ART_SPIKES, 1, 0, 0x6400, 8, 87, spikes),
                    sectionArtSource(CdR11aAsset.ART_CHECKPOINT, 0, 1, 0xD960, 12, 212, checkpointRead),
                    sectionArtSource(CdR11aAsset.ART_PLATFORM, 1, 6, 0x97C0, 57, 453, platform),
                    // Art_Animals (Data (Act 1 Present).asm:89-90): canonical PLC2 row1, not runtime selection.
                    // Reuse frontier's Java445 read; native446 ends at the real final source $00 ($3837D).
                    // The adjacent $80 at $3837E is outside this retained source view and is never appended.
                    sectionArtSource(CdR11aAsset.ART_ANIMALS, 2, 1, 0x7100, 20, 446,
                            frontier.binding(CdR11aAsset.ART_ANIMALS).decode()),
                    sectionArtSource(CdR11aAsset.ART_GOAL_POST, 2, 6, 0x9EE0, 24, 449, goalPostRead),
                    sectionArtSource(CdR11aAsset.ART_SONIC_HOLE, 5, 2, 0x7400, 14, 114, sonicHoleRead),
                    sectionArtSource(CdR11aAsset.ART_TUNNEL_WATERFALL, 5, 4, 0x7C80, 29, 328, tunnelWaterfallRead),
                    sectionArtSource(CdR11aAsset.ART_SPINNING_DISC, 5, 5, 0x8120, 12, 167, spinningDiscRead),
                    sectionArtSource(CdR11aAsset.ART_LOG_SHADOW, 5, 6, 0x8300, 16, 24, logShadowRead)));
        }
        return sectionArtSources;
    }

    private CdR11aAssets.NemesisRead decodeSectionArt(CdR11aAsset source, int tiles, int javaReaderBytes)
            throws IOException {
        var blob = profile.range(source);
        var reader = new CdR11aProfile.FileRange(blob.file(), blob.domain(), blob.fileOffset(), javaReaderBytes);
        var decoded = CdNemesisAsset.decode(reader.read(image).copy(), tiles * 32, tiles * 32);
        if (decoded.consumedInputBytes() != javaReaderBytes) {
            throw new IOException("Section-art Java reader consumption differs from its reviewed finite extent");
        }
        return new CdR11aAssets.NemesisRead(source, blob, reader, decoded.consumedInputBytes(), decoded.bytes());
    }

    private SectionArtSource sectionArtSource(CdR11aAsset source, int plcId, int row, int vram, int tiles,
                                               int nativeReaderBytes, CdR11aAssets.NemesisRead decoded) {
        var blob = profile.range(source);
        var entry = loadPlan.plcs().get(plcId).entries().get(row);
        if (entry.source() != source || !entry.sourceRange().equals(blob) || entry.vramAddress() != vram
                || source.encodingForReviewedConsumer() != CdR11aAsset.Encoding.NEMESIS
                || decoded.asset() != source || !decoded.sourceBlobRange().equals(blob)
                || decoded.bytes().length() != tiles * 32
                || decoded.consumedInputBytes() != decoded.readerInputRange().length()) {
            throw new IllegalArgumentException("Unverified native section-art source/PLC/decode association");
        }
        var reader = new CdR11aProfile.FileRange(blob.file(), blob.domain(), blob.fileOffset(), nativeReaderBytes);
        var bytes = reader.read(image);
        if ((bytes.unsignedWord(0) & 0x7FFF) != tiles) {
            throw new IllegalArgumentException("Section-art header differs from its reviewed tile count");
        }
        return new SectionArtSource(source, plcId, row, entry, decoded, reader, bytes);
    }
    // Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
}
