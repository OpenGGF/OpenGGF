package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdObjectVisualAssets;
import com.openggf.game.soniccd.level.CdR11aAsset;
import com.openggf.game.soniccd.level.CdR11aAssets;
import com.openggf.game.soniccd.level.CdR11aObjectAssets;
import com.openggf.game.soniccd.level.CdR12aObjectAssets;
import com.openggf.game.soniccd.level.CdR12aProfile;
import com.openggf.game.soniccd.level.CdR12bTerrainAssets;
import com.openggf.game.soniccd.level.CdR12bPlayerAssets;
import com.openggf.game.soniccd.level.CdR12bObjectAssets;
import com.openggf.game.soniccd.level.CdStagePlayerSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import static com.openggf.game.soniccd.objects.CdObjectDrawQueue.*;

/**
 * Pointer-owned, immutable DrawObjects mapping views. No ID dispatch, file IO or runtime source lookup.
 * Source: scddisasm 1ecc5f3af, DrawObjects/.DrawSprite/.StaticMap
 * (Level/Object Functions.asm:327-343): full unsigned frame, signed relative word,
 * or exactly one header-free five-byte static piece at the actual pointer.
 * Constructed declared views are format inputs, NOT authenticated media certificates.
 */
public final class CdObjectMappings implements CdDrawMappings {
    /**
     * One bounded byte source and ONE registered counted-table root within it.
     * Negative relative words may address preceding bytes of this same view; aliases are preserved.
     * Native addresses retain all 32 bits. This declaration establishes no runtime asset provenance.
     */
    public record DeclaredTable(int nativeStartBits, CdAssetBytes bytes, int tableOffset, int frameCount) {
        public DeclaredTable {
            Objects.requireNonNull(bytes, "mapping source bytes");
            if (frameCount < 1 || frameCount > 256 || tableOffset < 0
                    || (long) tableOffset + frameCount * 2L > bytes.length()) {
                throw new IllegalArgumentException("Counted mapping table must fit its bounded source view");
            }
            if (Integer.toUnsignedLong(nativeStartBits) + bytes.length() > 0x1_0000_0000L) {
                throw new IllegalArgumentException("Declared source crosses the native 32-bit address boundary");
            }
        }
        public int tableAddressBits() { return nativeStartBits + tableOffset; }
    }

    /** Exactly the five bytes at this pointer; no count or frame-table header is present. */
    public record DeclaredStaticPiece(int addressBits, CdAssetBytes bytes) {
        public DeclaredStaticPiece {
            Objects.requireNonNull(bytes, "static mapping bytes");
            if (bytes.length() != 5 || Integer.toUnsignedLong(addressBits) + 5 > 0x1_0000_0000L) {
                throw new IllegalArgumentException("Static mapping requires one bounded raw five-byte piece");
            }
        }
    }

    /** Each Game/Time Over table holds two words (Mappings.asm:2-3,5-6). */
    private static final int GAME_OVER_FRAMES = 2;

    private final Map<Integer, DeclaredTable> tables;
    private final Map<Integer, DeclaredStaticPiece> staticPieces;

    private CdObjectMappings(List<DeclaredTable> tables, List<DeclaredStaticPiece> staticPieces) {
        var indexedTables = new HashMap<Integer, DeclaredTable>();
        for (var table : List.copyOf(tables)) {
            if (indexedTables.putIfAbsent(table.tableAddressBits(), table) != null) {
                throw new IllegalArgumentException("Duplicate counted mapping pointer registration");
            }
        }
        var indexedStatic = new HashMap<Integer, DeclaredStaticPiece>();
        for (var piece : List.copyOf(staticPieces)) {
            if (indexedStatic.putIfAbsent(piece.addressBits(), piece) != null) {
                throw new IllegalArgumentException("Duplicate static mapping pointer registration");
            }
        }
        this.tables = Map.copyOf(indexedTables);
        this.staticPieces = Map.copyOf(indexedStatic);
    }

    /** Pure declared-format binding. It does not authenticate media, an object owner or native execution. */
    public static CdObjectMappings declaredSourceViews(List<DeclaredTable> tables,
                                                       List<DeclaredStaticPiece> staticPieces) {
        return new CdObjectMappings(tables, staticPieces);
    }

    /**
     * Production factory over actual immutable loaded bytes. Opening objects are authenticated by
     * CdR11aAssets.openingObjects(), which rehashes the retained MMD before decoding. Both owners must
     * identify the same USA R11A source, including observed disc identity; decoded tables must equal
     * their actual source views. A declared view/identity record alone cannot create either owner.
     * Monitor items admit only the eight authenticated first-icon pieces at their exact raw pointers;
     * other production bit-5 requests explicitly reject.
     * The Game/Time Over include registers two counted roots from its validated CdGameOver.Assets record.
     */
    public static CdObjectMappings fromOpeningAssets(CdR11aAssets assets, CdR11aObjectAssets objects) {
        Objects.requireNonNull(assets, "loaded R11A assets");
        Objects.requireNonNull(objects, "loaded opening object assets");
        if (!assets.identity().equals(objects.identity())) {
            throw new IllegalArgumentException("Mapping assets and opening objects have different source identities");
        }
        var profile = assets.profile();
        var identity = assets.identity();
        if (identity.variant() != CdReleaseVariant.USA || profile.variant() != CdReleaseVariant.USA) {
            throw new UnsupportedOperationException("Opening mapping binding is reviewed only for USA R11A");
        }
        if (!profile.stage().equals(identity.stage()) || profile.file() != identity.file()
                || profile.domain() != identity.domain() || !profile.expectedMmdSha256().equals(identity.mmdSha256())) {
            throw new IllegalArgumentException("Mapping asset profile/source identity mismatch");
        }
        var tables = new ArrayList<DeclaredTable>();
        var sonic = assets.sonic();
        var sonicView = assets.view(CdR11aAsset.MAP_SPR_SONIC);
        requireEqualBytes(sonicView.bytes(), sonic.mappings());
        // Reuse the decoded Sonic body bounds/counts, and retain its actual raw source bytes.
        for (var frame : sonic.mappingFrames()) {
            requireEqualBytes(frame.bytes(), sonicView.bytes().slice(frame.relativeOffset(), 1 + frame.pieceCount() * 5));
        }
        tables.add(new DeclaredTable(sonicView.range().nativeAddress(), sonicView.bytes(), 0,
                sonic.mappingFrames().size()));
        addObject(tables, objects, CdR11aObjectAssets.Table.RING_MAPPINGS, objects.rings());
        addObject(tables, objects, CdR11aObjectAssets.Table.MOSQUI_NORMAL_MAPPINGS, objects.normalMosqui());
        addObject(tables, objects, CdR11aObjectAssets.Table.MOSQUI_DAMAGED_MAPPINGS, objects.damagedMosqui());
        // Frontier bodies store these exact longs: Obj3DPlant_Init MOVE.L #MapSpr_3DPlant ($20E020) and
        // Obj3DRamp_Init MOVE.L #MapSpr_3DRamp ($21FDBC). The booster's declared frame 8/9 tails stay outside
        // the consumed frame views; the counted resolve reads only each count byte and its pieces.
        addObject(tables, objects, CdR11aObjectAssets.Table.PLANT_MAPPINGS, objects.plant());
        addObject(tables, objects, CdR11aObjectAssets.Table.RAMP_BOOSTER_MAPPINGS, objects.rampBooster());
        addObject(tables, objects, CdR11aObjectAssets.Table.EXPLOSION_MAPPINGS, objects.explosion());
        addObject(tables, objects, CdR11aObjectAssets.Table.POINTS_MAPPINGS, objects.points());
        addObject(tables, objects, CdR11aObjectAssets.Table.FLOWER_MAPPINGS, objects.flower());
        addGameOver(tables, objects);
        // ObjFloatBlock_Init stores MapSpr_FloatBlock (Floating Block/Main.asm:42,174-176).
        // Its six counted frames include the real empty frame 0 (Data/Mappings.asm:1-11).
        addObject(tables, objects, CdR11aObjectAssets.Table.FLOAT_BLOCK_MAPPINGS, objects.floatBlock());
        // Spring/Main.asm:188/201: two real oMap roots in the SAME complete source include, not rebased tables.
        addObject(tables, objects, CdR11aObjectAssets.Table.SPRING_MAPPINGS, objects.springVertical());
        addObject(tables, objects, CdR11aObjectAssets.Table.SPRING_MAPPINGS, objects.springHorizontal());
        // ObjSpring_Init diagonal root80, actual USA209BF0; three bounded counted frames.
        addObject(tables, objects, CdR11aObjectAssets.Table.SPRING_MAPPINGS, objects.springDiagonal());
        // ObjSpinTunnel_Init's actual USA oMap long; Powerup/Data/Mappings.asm has 13 counted frames.
        // Frame 0 is empty at +37 inside frame 1's +26..46 body, so retain this complete raw source.
        // The uniform visual decoder deliberately rejects it; each selected frame is bounded by resolve().
        var powerup = objects.view(CdR11aObjectAssets.Table.POWERUP_MAPPINGS);
        if (powerup.table() != CdR11aObjectAssets.Table.POWERUP_MAPPINGS
                || !powerup.range().equals(CdR11aObjectAssets.Table.POWERUP_MAPPINGS.range())) {
            throw new IllegalArgumentException("Powerup mapping source range mismatch");
        }
        tables.add(new DeclaredTable(powerup.range().nativeAddress(), powerup.bytes(), 0, 13));
        // ObjTunnelDoorV_Init stores actual USA $20CE9A (Tunnel Door/Main.asm:36,90-91).
        // Reuse the exact-byte join: two counted static frames, four pieces and both real zero pads.
        addObject(tables, objects, CdR11aObjectAssets.Table.TUNNEL_DOOR_VERTICAL_MAPPINGS, objects.tunnelDoorVertical());
        // ObjMonitor_Init stores MapSpr_MonitorTime (Monitor and Time Post/Main.asm:304,648-650).
        // The unchanged exact-byte join validates the complete counted directory and every decoded body.
        addObject(tables, objects, CdR11aObjectAssets.Table.MONITOR_TIME_MAPPINGS, objects.monitorTime());
        addObject(tables, objects, CdR11aObjectAssets.Table.TUNNEL_DOOR_MAPPINGS, objects.tunnelDoor());
        // ObjPataBata.SetInfo retains either actual USA oMap long $20B52E or $20B550.
        // Pata-Bata/Main.asm:23-46,87-92 at scddisasm 1ecc5f3af: both strict three-frame
        // visuals join the complete raw source through the unchanged exact-byte helper.
        addObject(tables, objects, CdR11aObjectAssets.Table.PATA_BATA_NORMAL_MAPPINGS, objects.pataBataNormal());
        addObject(tables, objects, CdR11aObjectAssets.Table.PATA_BATA_DAMAGED_MAPPINGS, objects.pataBataDamaged());
        // ObjBoulder_Init stores actual USA MapSpr_Boulder $209654 (Boulder/Main.asm:30,57-59).
        addObject(tables, objects, CdR11aObjectAssets.Table.BOULDER_MAPPINGS, objects.boulder());
        // ObjSpikes_Init stores actual USA $20C1F0 (Spikes/Main.asm:21,87-89). Retain the full24
        // include and its real EVEN byte; one counted frame, four pieces/eight distinct tiles, no script.
        addObject(tables, objects, CdR11aObjectAssets.Table.SPIKES_MAPPINGS, objects.spikes());
        addObject(tables, objects, CdR11aObjectAssets.Table.CHECKPOINT_MAPPINGS, objects.checkpoint());
        // ObjAnton_Init stores the actual USA $20B3AA oMap long (Anton/Main.asm:27).
        // Join the complete112-byte, four-frame source through the existing exact-byte helper.
        addObject(tables, objects, CdR11aObjectAssets.Table.ANTON_MAPPINGS, objects.anton());
        // Literal Collapse oMap roots (Main.asm:36,40,183,281); retain all raw directories,
        // aliases and pads through the unchanged exact-byte helper, including both fragment roots.
        addObject(tables, objects, CdR11aObjectAssets.Table.COLLAPSE_PLATFORM_MAPPINGS_1, objects.collapsePlatform1());
        addObject(tables, objects, CdR11aObjectAssets.Table.COLLAPSE_PLATFORM_MAPPINGS_2, objects.collapsePlatform2());
        addObject(tables, objects, CdR11aObjectAssets.Table.COLLAPSE_PLATFORM_MAPPINGS_3, objects.collapsePlatform3());
        addObject(tables, objects, CdR11aObjectAssets.Table.COLLAPSE_PLATFORM_MAPPINGS_4, objects.collapsePlatform4());
        // Actual ID21 Init selects this complete 132-byte root; its pixels share Collapse's current57.
        addObject(tables, objects, CdR11aObjectAssets.Table.PLATFORM_MAPPINGS, objects.platform());
        // ObjTamabboh_Init selects $20B968/$20B97E (Tamabboh/Main.asm:40-57): the same full62,
        // with damaged root+22. Child Init stores $20BA70 (:233); retain its odd frame3 body (:288-290).
        addObject(tables, objects, CdR11aObjectAssets.Table.TAMABBOH_MAPPINGS, objects.tamabbohNormal());
        addObject(tables, objects, CdR11aObjectAssets.Table.TAMABBOH_MAPPINGS, objects.tamabbohDamaged());
        addObject(tables, objects, CdR11aObjectAssets.Table.TAMABBOH_MISSILE_MAPPINGS, objects.tamabbohMissile());
        addObject(tables, objects, CdR11aObjectAssets.Table.SPINNING_DISC_MAPPINGS, objects.spinningDisc());
        addObject(tables, objects, CdR11aObjectAssets.Table.LOG_SHADOW_MAPPINGS, objects.logShadow());
        addObject(tables, objects, CdR11aObjectAssets.Table.SONIC_HOLE_MAPPINGS, objects.sonicHole());
        var monitorView = objects.view(CdR11aObjectAssets.Table.MONITOR_TIME_MAPPINGS);
        var staticPieces = new ArrayList<DeclaredStaticPiece>();
        // ObjMonitorItem_Init (:451-459) adds the signed frame word and then one byte, skipping the count.
        // Only the eight Monitor icons (subtypes 0..7, :278-280) are admitted; post/HUD first pieces are not.
        // Keep the odd native pointers and exactly five actual source bytes, never a copied counted header.
        for (int frame = 0; frame < 8; frame++) {
            int firstPiece = (short) monitorView.bytes().unsignedWord(frame * 2) + 1;
            staticPieces.add(new DeclaredStaticPiece(monitorView.range().nativeAddress() + firstPiece,
                    monitorView.bytes().slice(firstPiece, 5)));
        }
        return new CdObjectMappings(tables, staticPieces);
    }

    /**
     * Eight exact first-icon source views for original USA R12A monitor items (animations 0..7).
     * The existing authenticated object owner supplies the complete retained monitor mapping include;
     * no counted registration, art read, visual decode or runtime admission is performed here.
     * ObjMonitorItem_Init (Monitor and Time Post/Main.asm:451-459, scddisasm 1ecc5f3af) adds
     * each signed directory WORD to the mapping root, then one byte to skip the piece count.
     * Retain the resulting odd pointer and exactly five source bytes. Other interiors stay undeclared.
     * Pass this immutable list to declaredSourceViews alongside the caller's existing counted tables.
     */
    public static List<DeclaredStaticPiece> act2MonitorItemStaticPieces(CdR12aObjectAssets objects) {
        Objects.requireNonNull(objects, "loaded original R12A object assets");
        var source = objects.view(CdR12aProfile.Asset.MAP_SPR_MONITOR_TIME);
        int root = objects.profile().range(CdR12aProfile.Asset.MAP_SPR_MONITOR_TIME).nativeAddress();
        var pieces = new ArrayList<DeclaredStaticPiece>(8);
        for (int frame = 0; frame < 8; frame++) {
            int firstPiece = (short) source.unsignedWord(frame * 2) + 1;
            pieces.add(new DeclaredStaticPiece(root + firstPiece, source.slice(firstPiece, 5)));
        }
        return List.copyOf(pieces);
    }


    /**
     * Complete genuine original-USA R12B data catalogue:22 object roots plus198-frame Sonic.
     * The existing stage-pair gate validates family/profile/disc lineage. These immutable
     * declarations grant no object invocation, Frame/World, art/current or pixel authority.
     * N1ecc5f3: Act 2 Past.asm includes; Object Functions.asm:327-343; native object Init roots.
     */
    public static CdObjectMappings fromAct2PastAssets(CdR12bTerrainAssets terrain, CdR12bPlayerAssets player) {
        var relation = CdStagePlayerSource.fromAct2Past(terrain, player);
        var sonic = relation.sonic();
        var sonicView = player.view(CdR12bPlayerAssets.Asset.MAP_SPR_SONIC);
        requireEqualBytes(sonicView, sonic.mappings());
        for (var frame : sonic.mappingFrames()) {
            requireEqualBytes(frame.bytes(), sonicView.slice(frame.relativeOffset(), 1 + frame.pieceCount() * 5));
        }
        var tables = new ArrayList<DeclaredTable>();
        tables.add(new DeclaredTable(relation.mappingAddress(), sonicView, 0, sonic.mappingFrames().size()));
        var objects = terrain.objects();
        addAct2PastObject(tables, objects, CdR12bObjectAssets.View.MAP_SPR_RING, objects.rings());
        // Native Spring oMap roots0/6/80 retain one complete200-byte include and its actual parent EVEN.
        addAct2PastObject(tables, objects, CdR12bObjectAssets.View.MAP_SPRING, objects.springVertical());
        addAct2PastObject(tables, objects, CdR12bObjectAssets.View.MAP_SPRING, objects.springHorizontal());
        addAct2PastObject(tables, objects, CdR12bObjectAssets.View.MAP_SPRING, objects.springDiagonal());
        addAct2PastObject(tables, objects, CdR12bObjectAssets.View.MAP_SPR_HUD, objects.hudVisuals());
        addAct2PastObject(tables, objects, CdR12bObjectAssets.View.MAP_SPR_MONITOR_TIME, objects.monitorTime());
        addAct2PastObject(tables, objects, CdR12bObjectAssets.View.MAP_SPR_COLLAPSE_PLATFORM_1, objects.collapsePlatform1());
        addAct2PastObject(tables, objects, CdR12bObjectAssets.View.MAP_SPR_COLLAPSE_PLATFORM_2, objects.collapsePlatform2());
        addAct2PastObject(tables, objects, CdR12bObjectAssets.View.MAP_SPR_COLLAPSE_PLATFORM_3, objects.collapsePlatform3());
        addAct2PastObject(tables, objects, CdR12bObjectAssets.View.MAP_SPR_COLLAPSE_PLATFORM_4, objects.collapsePlatform4());
        // Both Tamabboh roots share the complete62-byte original source; damaged root is+22.
        addAct2PastObject(tables, objects, CdR12bObjectAssets.View.MAP_SPR_TAMABBOH, objects.tamabbohNormal());
        addAct2PastObject(tables, objects, CdR12bObjectAssets.View.MAP_SPR_TAMABBOH, objects.tamabbohDamaged());
        addAct2PastObject(tables, objects, CdR12bObjectAssets.View.MAP_SPR_TAMABBOH_MISSILE, objects.tamabbohMissile());
        addAct2PastObject(tables, objects, CdR12bObjectAssets.View.MAP_SPR_FLYING_ANIMAL, objects.flyingAnimal());
        addAct2PastObject(tables, objects, CdR12bObjectAssets.View.MAP_SPR_GROUND_ANIMAL, objects.groundAnimal());
        addAct2PastObject(tables, objects, CdR12bObjectAssets.View.MAP_SPR_SWING, objects.swing());
        addAct2PastObject(tables, objects, CdR12bObjectAssets.View.MAP_SPR_ROBOT_GENERATOR, objects.robotGenerator());
        addAct2PastObject(tables, objects, CdR12bObjectAssets.View.MAP_SPR_ANTON, objects.anton());
        addAct2PastObject(tables, objects, CdR12bObjectAssets.View.MAP_SPR_PLATFORM, objects.platform());
        addAct2PastObject(tables, objects, CdR12bObjectAssets.View.MAP_SPR_BOULDER, objects.boulder());
        addAct2PastObject(tables, objects, CdR12bObjectAssets.View.MAP_SPR_BREAKABLE_WALL, objects.breakableWall());
        addAct2PastObject(tables, objects, CdR12bObjectAssets.View.MAP_SPR_FLOAT_BLOCK, objects.floatBlock());
        return declaredSourceViews(tables, act2PastMonitorItemStaticPieces(objects));
    }

    /** Original MonitorItem Init:451-459, counted directory WORD then+1 to skip the count.
     * Only the eight actual icon first pieces are admitted; all32 pointer bits survive. */
    public static List<DeclaredStaticPiece> act2PastMonitorItemStaticPieces(CdR12bObjectAssets objects) {
        Objects.requireNonNull(objects, "loaded original R12B object assets");
        var source = objects.view(CdR12bObjectAssets.View.MAP_SPR_MONITOR_TIME);
        int root = CdR12bObjectAssets.View.MAP_SPR_MONITOR_TIME.range().nativeAddress();
        var pieces = new ArrayList<DeclaredStaticPiece>(8);
        for (int frame = 0; frame < 8; frame++) {
            int firstPiece = (short) source.unsignedWord(frame * 2) + 1;
            pieces.add(new DeclaredStaticPiece(root + firstPiece, source.slice(firstPiece, 5)));
        }
        return List.copyOf(pieces);
    }

    /** Exact-byte join over the authenticated owner, including indexed roots, aliases and real tails. */
    private static void addAct2PastObject(List<DeclaredTable> tables, CdR12bObjectAssets owner,
                                          CdR12bObjectAssets.View source, CdObjectVisualAssets visual) {
        var bytes = owner.view(source);
        requireEqualBytes(bytes, visual.mappings());
        for (var frame : visual.mappingFrames()) {
            requireEqualBytes(frame.bytes(), bytes.slice(visual.mappingTableOffset() + frame.relativeOffset(),
                    1 + frame.pieces().size() * 5));
        }
        tables.add(new DeclaredTable(source.range().nativeAddress(), bytes,
                visual.mappingTableOffset(), visual.mappingFrames().size()));
    }

    @Override public MappingBody resolve(int addressBits, boolean staticPiece, int mapFrameByte) {
        if (mapFrameByte < 0 || mapFrameByte > 255) {
            throw new IllegalArgumentException("Mapping frame must be the full unsigned native byte");
        }
        if (staticPiece) {
            var view = staticPieces.get(addressBits);
            if (view == null) { throw unsupported(addressBits, true, mapFrameByte, "no admitted raw static piece at this pointer"); }
            return new StaticMapping(piece(view.bytes(), 0));
        }
        var table = tables.get(addressBits); // Exact raw long bits: NEVER mask unsupported addresses to 24 bits.
        if (table == null) { throw unsupported(addressBits, false, mapFrameByte, "no registered counted-table pointer"); }
        if (mapFrameByte >= table.frameCount()) {
            throw unsupported(addressBits, false, mapFrameByte, "frame outside the registered source table");
        }
        int relative = (short) table.bytes().unsignedWord(table.tableOffset() + mapFrameByte * 2);
        int at = table.tableOffset() + relative;
        if (at < 0 || at >= table.bytes().length()) {
            throw new IllegalArgumentException("Signed mapping relative word escapes its bounded source view");
        }
        int count = table.bytes().unsignedByte(at);
        if (count > 128) {
            throw unsupported(addressBits, false, mapFrameByte,
                    "count " + count + " takes the native empty branch; unsupported by bounded mapping policy");
        }
        int length = 1 + count * 5;
        if (length > table.bytes().length() - at) {
            throw new IllegalArgumentException("Counted mapping body escapes its bounded source view");
        }
        var pieces = new ArrayList<RawPiece>(count);
        for (int index = 0; index < count; index++) { pieces.add(piece(table.bytes(), at + 1 + index * 5)); }
        return new FrameMapping(count, pieces);
    }

    private static RawPiece piece(CdAssetBytes bytes, int at) {
        // Share the existing visual piece's signed offsets, raw attribute word and finite size policy.
        var piece = new CdObjectVisualAssets.Piece(bytes.signedByte(at), bytes.unsignedByte(at + 1),
                bytes.unsignedWord(at + 2), bytes.signedByte(at + 4));
        return new RawPiece(piece.y(), piece.sizeByte(), piece.rawTileWord(), piece.x());
    }

    private static void addObject(List<DeclaredTable> tables, CdR11aObjectAssets owner,
                                  CdR11aObjectAssets.Table table, CdObjectVisualAssets visual) {
        var view = owner.view(table);
        if (view.table() != table || !view.range().equals(table.range())) {
            throw new IllegalArgumentException("Object mapping source range mismatch");
        }
        requireEqualBytes(view.bytes(), visual.mappings());
        for (var frame : visual.mappingFrames()) {
            requireEqualBytes(frame.bytes(), view.bytes().slice(visual.mappingTableOffset() + frame.relativeOffset(),
                    1 + frame.pieces().size() * 5));
        }
        tables.add(new DeclaredTable(view.range().nativeAddress(), view.bytes(),
                visual.mappingTableOffset(), visual.mappingFrames().size()));
    }

    /**
     * ObjGameOver_Init stores MOVE.L #MapSpr_GameOver ($20F03E) or #MapSpr_TimeOver ($20F042) in oMap
     * (Game Over/Main.asm:36,43). One raw include holds both two-word tables, each relative to its own label
     * (Mappings.asm:1-6), so the same validated 56 bytes register two counted roots at offsets 0 and 4: no uniform
     * header, rebasing or compaction. oMapFrame is 0 or the OVER child's 1 (:80); frames 2..255 stay unregistered.
     */
    private static void addGameOver(List<DeclaredTable> tables, CdR11aObjectAssets owner) {
        var table = CdR11aObjectAssets.Table.GAME_OVER_MAPPINGS;
        var view = owner.view(table);
        if (view.table() != table || !view.range().equals(table.range())) {
            throw new IllegalArgumentException("Object mapping source range mismatch");
        }
        var gameOver = CdGameOver.Assets.fromObjectAssets(owner); // Raw extent, both tables, bodies, USA identity.
        requireEqualBytes(view.bytes(), gameOver.mappings());
        if (gameOver.gameOverMappingsAddress() != view.range().nativeAddress()) {
            throw new IllegalArgumentException("Game Over oMap long differs from its loaded source view");
        }
        tables.add(new DeclaredTable(gameOver.gameOverMappingsAddress(), gameOver.mappings(), 0, GAME_OVER_FRAMES));
        tables.add(new DeclaredTable(gameOver.gameOverMappingsAddress(), gameOver.mappings(),
                CdGameOver.Assets.TIME_OVER_TABLE_OFFSET, GAME_OVER_FRAMES));
    }

    private static void requireEqualBytes(CdAssetBytes source, CdAssetBytes decoded) {
        if (!Arrays.equals(source.copy(), decoded.copy())) {
            throw new IllegalArgumentException("Decoded mapping bytes differ from the loaded source view");
        }
    }

    private static UnsupportedOperationException unsupported(int pointer, boolean staticPiece, int frame, String reason) {
        return new UnsupportedOperationException(String.format(Locale.ROOT,
                "CD mapping $%08X static=%s frame=%d: %s", pointer, staticPiece, frame, reason));
    }
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
