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
        POWERUP_MAPPINGS("MapSpr_Powerup", 0x3EE9C, 274);

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
     * Only Mosqui's selector 0 and FloatBlock's selector 5 (Floating Block/Main.asm:43-44), each at subtype2 0,
     * are admitted; Ring writes its own literal $A7AE.
     */
    public static int decodeTileWord(byte[] input, int selector, int subtype2) {
        if (selector < 0 || selector > 0x7FFF || (subtype2 & ~0xFF) != 0) {
            throw new IllegalArgumentException("Object tile selector/subtype width");
        }
        var bytes = exact(input, 74, "ObjectTileIDs");
        if ((selector != 0 && selector != 5) || subtype2 != 0) {
            throw new UnsupportedOperationException("Only Mosqui selector 0 / FloatBlock selector 5 at subtype2 0 are reviewed");
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
        if (end - target < 2) {
            throw new IllegalArgumentException((selector == 0 ? "Mosqui" : "FloatBlock") + " tile word crosses its selected body");
        }
        return bytes.unsignedWord(target);
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
}
