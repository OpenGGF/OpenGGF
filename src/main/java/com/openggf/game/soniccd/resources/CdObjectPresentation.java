package com.openggf.game.soniccd.resources;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11aAsset;
import com.openggf.game.soniccd.level.CdR11aObjectAssets;
import com.openggf.game.soniccd.objects.CdObjectDrawQueue;
import com.openggf.graphics.SpritePresentation;
import com.openggf.level.render.SpritePieceRenderer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Pure completed CPU SAT -> screen-relative immutable picture. No graphics calls, native clocks,
 * admission, slot flags, player state, queue mutation, latching or display publication.
 * Root latches the PREVIOUS CPU SAT at completed NEXT selector-8 V-int with NOW serviced art
 * (VInt_Level, scddisasm 1ecc5f3af, Level/Main.asm:821-834); NORMAL_PAUSE also publishes,
 * PP selector-zero lag retains. This converter neither selects nor performs those transitions.
 *
 * <p>Coordinate policy is bounded normal non-interlaced geometry: SAT X 1..511, Y word 0..511,
 * each minus 128. Every active entry is retained, including outside the viewport. Other Y words
 * reject instead of inventing wrap. This is not general VDP Y/interlace/scanline emulation.</p>
 */
public final class CdObjectPresentation {
    private CdObjectPresentation() { }

    public record TilePattern(int logicalPatternId, SpritePresentation.PatternVersion version) {
        public TilePattern {
            if (logicalPatternId < 0) { throw new IllegalArgumentException("Logical pattern ID must be nonnegative"); }
            Objects.requireNonNull(version, "immutable pattern version");
        }
    }

    /**
     * Resolve only a FINAL native 11-bit pattern ID. Supply a stable source for this conversion.
     * Returned values are copied into the frame, so later DPLC/bank changes cannot alter its pixels.
     * This port does not claim source authentication, residency, a virtual bank or graphics ownership.
     */
    @FunctionalInterface public interface PatternSource { TilePattern resolve(int nativeTileId); }

    /** Pure declared pixel-bank input, not a media/residency certificate. No wrap or unowned padding. */
    public record PixelSpan(int nativeFirstTileId, int logicalFirstPatternId, CdAssetBytes bytes) {
        public PixelSpan {
            Objects.requireNonNull(bytes, "pixel bank bytes");
            int tiles = bytes.length() / 32;
            if (bytes.length() == 0 || bytes.length() % 32 != 0 || nativeFirstTileId < 0
                    || nativeFirstTileId > 2048 - tiles || logicalFirstPatternId < 0
                    || (long) logicalFirstPatternId + tiles - 1 > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Pixel span must contain bounded whole native patterns and logical IDs");
            }
        }
    }

    /** Binds immutable pixels once. Overlapping physical OR logical spans reject, including equal bytes. */
    public static PatternSource declaredPixelBank(List<PixelSpan> spans) {
        var patterns = new HashMap<Integer, TilePattern>();
        var logicalIds = new HashSet<Integer>();
        for (var span : List.copyOf(spans)) {
            for (int index = 0; index < span.bytes().length() / 32; index++) {
                int nativeId = span.nativeFirstTileId() + index;
                int logicalId = span.logicalFirstPatternId() + index;
                var tile = new TilePattern(logicalId, patternVersion(span.bytes().slice(index * 32, 32)));
                if (patterns.putIfAbsent(nativeId, tile) != null || !logicalIds.add(logicalId)) {
                    throw new IllegalArgumentException("Overlapping native or logical pixel-bank binding");
                }
            }
        }
        Map<Integer, TilePattern> frozen = Map.copyOf(patterns);
        return nativeId -> {
            if (nativeId < 0 || nativeId > 0x7FF) { throw new IllegalArgumentException("Expected final 11-bit native tile ID"); }
            var tile = frozen.get(nativeId);
            if (tile == null) { throw new UnsupportedOperationException("No admitted source pixels for native tile $"
                    + Integer.toHexString(nativeId)); }
            return tile;
        };
    }

    /**
     * Explicit USA_R11A_OPENING_DEV_PRELOADED_ART policy, not executed native PLC/section residency.
     * Snapshot the supplied immutable NOW-serviced complete 24-tile player window once, alongside
     * the actual initialArtBank Ring/Mosqui source bytes. Native object first IDs come from binding
     * metadata. Player F000/32 is the existing DPLC service's declared VRAM destination, not a slot
     * header inferred during an initial empty SAT. Caller owns stable virtual bank bases and binds
     * this state's world/source; this method makes no GraphicsManager claim or player raw-slot copy.
     */
    public static PatternSource usaR11aOpeningDevPreloadedArt(CdPlayerArtRuntime.State nowServicedPlayer,
                                                              CdR11aObjectAssets objects,
                                                              int playerLogicalBase, int ringLogicalBase,
                                                              int mosquiLogicalBase) {
        Objects.requireNonNull(nowServicedPlayer, "now-serviced player art");
        Objects.requireNonNull(objects, "authenticated opening object assets");
        if (objects.identity().variant() != CdReleaseVariant.USA) {
            throw new UnsupportedOperationException("DEV preloaded object art is reviewed only for USA R11A");
        }
        // State and bytes are immutable. Retain this one snapshot, never a callback to a live runtime.
        CdAssetBytes playerPixels = nowServicedPlayer.residentBank();
        var ring = objects.initialArtBank().binding(CdR11aAsset.ART_RINGS);
        var mosqui = objects.initialArtBank().binding(CdR11aAsset.ART_MOSQUI);
        return declaredPixelBank(List.of(
                new PixelSpan(CdPlayerArtRuntime.VRAM_BYTE_ADDRESS / 32, playerLogicalBase, playerPixels),
                new PixelSpan(ring.firstTileId(), ringLogicalBase, ring.bytes()),
                new PixelSpan(mosqui.firstTileId(), mosquiLogicalBase, mosqui.bytes())));
    }

    /**
     * Explicit USA_R11A_FRONTIER_DEV_PRELOADED_ART policy: the three opening spans above plus the two frontier
     * drawing sources, Art_3DPlant (PLC_Cam1_Full row 4, 28 tiles from VRAM $8480) and Art_3DRampBoost (row 5,
     * 63 tiles from VRAM $8820), taken from the owner's separately decoded frontier bank. Animals are never
     * bound: the admitted Present/projector-intact Animal path draws nothing. Native first IDs still come from
     * binding metadata. This remains a declared cold preload, not executed PLC/section residency; the first
     * section switch at cameraX $680 (PLC 9 over these destinations) is outside every DEV policy here.
     */
    public static PatternSource usaR11aFrontierDevPreloadedArt(CdPlayerArtRuntime.State nowServicedPlayer,
                                                               CdR11aObjectAssets objects,
                                                               CdR11aObjectAssets.FrontierArtBank frontier,
                                                               int playerLogicalBase, int ringLogicalBase,
                                                               int mosquiLogicalBase, int plantLogicalBase,
                                                               int rampLogicalBase) {
        Objects.requireNonNull(nowServicedPlayer, "now-serviced player art");
        Objects.requireNonNull(objects, "authenticated opening object assets");
        Objects.requireNonNull(frontier, "the owner's decoded frontier art bank");
        if (objects.identity().variant() != CdReleaseVariant.USA) {
            throw new UnsupportedOperationException("DEV preloaded object art is reviewed only for USA R11A");
        }
        CdAssetBytes playerPixels = nowServicedPlayer.residentBank();
        var ring = objects.initialArtBank().binding(CdR11aAsset.ART_RINGS);
        var mosqui = objects.initialArtBank().binding(CdR11aAsset.ART_MOSQUI);
        var plant = frontier.binding(CdR11aAsset.ART_3D_PLANT);
        var ramp = frontier.binding(CdR11aAsset.ART_3D_RAMP_BOOST);
        return declaredPixelBank(List.of(
                new PixelSpan(CdPlayerArtRuntime.VRAM_BYTE_ADDRESS / 32, playerLogicalBase, playerPixels),
                new PixelSpan(ring.firstTileId(), ringLogicalBase, ring.bytes()),
                new PixelSpan(mosqui.firstTileId(), mosquiLogicalBase, mosqui.bytes()),
                new PixelSpan(plant.firstTileId(), plantLogicalBase, plant.bytes()),
                new PixelSpan(ramp.firstTileId(), rampLogicalBase, ramp.bytes())));
    }

    /**
     * Explicit cold DEV preload of the five frontier spans and the three PLC1 contact sources.
     * The separate bank supplies explosions ($680, 70 tiles), points ($6C6, 5) and flowers
     * ($6D7, 39); the checkpoint gap stays unbound. This does not execute PLC residency.
     * Flower's palette/priority changes belong to its final SAT word, not to these pixels.
     */
    public static PatternSource usaR11aContactDevPreloadedArt(CdPlayerArtRuntime.State nowServicedPlayer,
                                                              CdR11aObjectAssets objects,
                                                              CdR11aObjectAssets.FrontierArtBank frontier,
                                                              CdR11aObjectAssets.ContactArtBank contact,
                                                              int playerLogicalBase, int ringLogicalBase,
                                                              int mosquiLogicalBase, int plantLogicalBase,
                                                              int rampLogicalBase, int explosionLogicalBase,
                                                              int pointsLogicalBase, int flowerLogicalBase) {
        Objects.requireNonNull(nowServicedPlayer, "now-serviced player art");
        Objects.requireNonNull(objects, "authenticated opening object assets");
        Objects.requireNonNull(frontier, "the owner's decoded frontier art bank");
        Objects.requireNonNull(contact, "the owner's decoded contact art bank");
        if (objects.identity().variant() != CdReleaseVariant.USA) {
            throw new UnsupportedOperationException("DEV preloaded object art is reviewed only for USA R11A");
        }
        var ring = objects.initialArtBank().binding(CdR11aAsset.ART_RINGS);
        var mosqui = objects.initialArtBank().binding(CdR11aAsset.ART_MOSQUI);
        var plant = frontier.binding(CdR11aAsset.ART_3D_PLANT);
        var ramp = frontier.binding(CdR11aAsset.ART_3D_RAMP_BOOST);
        var explosion = contact.binding(CdR11aAsset.ART_EXPLOSIONS);
        var points = contact.binding(CdR11aAsset.ART_POINTS);
        var flower = contact.binding(CdR11aAsset.ART_FLOWER);
        return declaredPixelBank(List.of(
                new PixelSpan(CdPlayerArtRuntime.VRAM_BYTE_ADDRESS / 32, playerLogicalBase, nowServicedPlayer.residentBank()),
                new PixelSpan(ring.firstTileId(), ringLogicalBase, ring.bytes()),
                new PixelSpan(mosqui.firstTileId(), mosquiLogicalBase, mosqui.bytes()),
                new PixelSpan(plant.firstTileId(), plantLogicalBase, plant.bytes()),
                new PixelSpan(ramp.firstTileId(), rampLogicalBase, ramp.bytes()),
                new PixelSpan(explosion.firstTileId(), explosionLogicalBase, explosion.bytes()),
                new PixelSpan(points.firstTileId(), pointsLogicalBase, points.bytes()),
                new PixelSpan(flower.firstTileId(), flowerLogicalBase, flower.bytes())));
    }

    /**
     * Explicit USA_R11A_GAME_OVER_DEV_PRELOADED_ART policy: the eight contact spans above plus the complete NOW-serviced
     * LoadShieldArt window, ObjGameOver's tile $544 (MOVE.W #$8544,oTile, Game Over/Main.asm:35). LVLDMA
     * aniArtBuffer,$A880,$480 (Powerup/Main.asm:173) makes the whole CPU buffer resident, so 36 tiles from $A880/32 are
     * bound, including the four tiles of the caller's explicit 128-byte RAM tail. No Game/Time Over piece reaches
     * them (Mappings.asm:1-58 uses tiles 0..31 and 0..29). The window is the supplied resident State, never an art
     * file alone: its copy changes with each serviced selector and its tail is RAM history. This method services
     * nothing and creates no default RAM, cold seed, clock, residency, renderer or global write. The runtime root
     * services LoadShieldArt in each selector-8 VInt_Level (UpdateAnimTiles, Level/Main.asm:837) before converting,
     * and owns powerup, the State, its capture and the 349-pattern bank.
     */
    public static PatternSource usaR11aGameOverDevPreloadedArt(CdPlayerArtRuntime.State nowServicedPlayer,
                                                               CdPowerupArtRuntime.State nowServicedPowerup,
                                                               CdR11aObjectAssets objects,
                                                               CdR11aObjectAssets.FrontierArtBank frontier,
                                                               CdR11aObjectAssets.ContactArtBank contact,
                                                               int playerLogicalBase, int ringLogicalBase,
                                                               int mosquiLogicalBase, int plantLogicalBase,
                                                               int rampLogicalBase, int explosionLogicalBase,
                                                               int pointsLogicalBase, int flowerLogicalBase,
                                                               int powerupLogicalBase) {
        Objects.requireNonNull(nowServicedPlayer, "now-serviced player art");
        Objects.requireNonNull(nowServicedPowerup, "now-serviced powerup art");
        Objects.requireNonNull(objects, "authenticated opening object assets");
        Objects.requireNonNull(frontier, "the owner's decoded frontier art bank");
        Objects.requireNonNull(contact, "the owner's decoded contact art bank");
        if (objects.identity().variant() != CdReleaseVariant.USA) {
            throw new UnsupportedOperationException("DEV preloaded object art is reviewed only for USA R11A");
        }
        var ring = objects.initialArtBank().binding(CdR11aAsset.ART_RINGS);
        var mosqui = objects.initialArtBank().binding(CdR11aAsset.ART_MOSQUI);
        var plant = frontier.binding(CdR11aAsset.ART_3D_PLANT);
        var ramp = frontier.binding(CdR11aAsset.ART_3D_RAMP_BOOST);
        var explosion = contact.binding(CdR11aAsset.ART_EXPLOSIONS);
        var points = contact.binding(CdR11aAsset.ART_POINTS);
        var flower = contact.binding(CdR11aAsset.ART_FLOWER);
        return declaredPixelBank(List.of(
                new PixelSpan(CdPlayerArtRuntime.VRAM_BYTE_ADDRESS / 32, playerLogicalBase, nowServicedPlayer.residentBank()),
                new PixelSpan(ring.firstTileId(), ringLogicalBase, ring.bytes()),
                new PixelSpan(mosqui.firstTileId(), mosquiLogicalBase, mosqui.bytes()),
                new PixelSpan(plant.firstTileId(), plantLogicalBase, plant.bytes()),
                new PixelSpan(ramp.firstTileId(), rampLogicalBase, ramp.bytes()),
                new PixelSpan(explosion.firstTileId(), explosionLogicalBase, explosion.bytes()),
                new PixelSpan(points.firstTileId(), pointsLogicalBase, points.bytes()),
                new PixelSpan(flower.firstTileId(), flowerLogicalBase, flower.bytes()),
                powerupResidentSpan(nowServicedPowerup, powerupLogicalBase)));
    }

    /**
     * Explicit USA_R11A_FLOAT_BLOCK_DEV_PRELOADED_ART policy: the nine Game/Time Over spans above plus the actual
     * 15-tile Art_FloatBlock decode from the owner's separate bank, giving 364 patterns. PLC_Std row 2 places it at
     * VRAM $6680 (Palmtree Panic/Data (Act 1 Present).asm:54-61); ObjFloatBlock_Init selects ObjectTileIDs entry 5
     * (Floating Block/Main.asm:42-48), whose word_20CF78 is $4334 (Object Tile IDs (Act 1 Present).asm:13,33).
     * Native tiles $334..$342 come from that binding's metadata and bytes, with no surrounding pixels invented.
     * Player and powerup windows are the supplied immutable NOW-serviced snapshots. This method services nothing
     * and claims no live PLC/section residency; the caller owns service order, world identity and logical bases.
     */
    public static PatternSource usaR11aFloatBlockDevPreloadedArt(CdPlayerArtRuntime.State nowServicedPlayer,
                                                                 CdPowerupArtRuntime.State nowServicedPowerup,
                                                                 CdR11aObjectAssets objects,
                                                                 CdR11aObjectAssets.FrontierArtBank frontier,
                                                                 CdR11aObjectAssets.ContactArtBank contact,
                                                                 CdR11aObjectAssets.FloatBlockArtBank floatBlock,
                                                                 int playerLogicalBase, int ringLogicalBase,
                                                                 int mosquiLogicalBase, int plantLogicalBase,
                                                                 int rampLogicalBase, int explosionLogicalBase,
                                                                 int pointsLogicalBase, int flowerLogicalBase,
                                                                 int powerupLogicalBase, int floatBlockLogicalBase) {
        Objects.requireNonNull(nowServicedPlayer, "now-serviced player art");
        Objects.requireNonNull(nowServicedPowerup, "now-serviced powerup art");
        Objects.requireNonNull(objects, "authenticated opening object assets");
        Objects.requireNonNull(frontier, "the owner's decoded frontier art bank");
        Objects.requireNonNull(contact, "the owner's decoded contact art bank");
        Objects.requireNonNull(floatBlock, "the owner's decoded FloatBlock art bank");
        if (objects.identity().variant() != CdReleaseVariant.USA) {
            throw new UnsupportedOperationException("DEV preloaded object art is reviewed only for USA R11A");
        }
        var ring = objects.initialArtBank().binding(CdR11aAsset.ART_RINGS);
        var mosqui = objects.initialArtBank().binding(CdR11aAsset.ART_MOSQUI);
        var plant = frontier.binding(CdR11aAsset.ART_3D_PLANT);
        var ramp = frontier.binding(CdR11aAsset.ART_3D_RAMP_BOOST);
        var explosion = contact.binding(CdR11aAsset.ART_EXPLOSIONS);
        var points = contact.binding(CdR11aAsset.ART_POINTS);
        var flower = contact.binding(CdR11aAsset.ART_FLOWER);
        var block = floatBlock.binding(CdR11aAsset.ART_FLOAT_BLOCK);
        return declaredPixelBank(List.of(
                new PixelSpan(CdPlayerArtRuntime.VRAM_BYTE_ADDRESS / 32, playerLogicalBase, nowServicedPlayer.residentBank()),
                new PixelSpan(ring.firstTileId(), ringLogicalBase, ring.bytes()),
                new PixelSpan(mosqui.firstTileId(), mosquiLogicalBase, mosqui.bytes()),
                new PixelSpan(plant.firstTileId(), plantLogicalBase, plant.bytes()),
                new PixelSpan(ramp.firstTileId(), rampLogicalBase, ramp.bytes()),
                new PixelSpan(explosion.firstTileId(), explosionLogicalBase, explosion.bytes()),
                new PixelSpan(points.firstTileId(), pointsLogicalBase, points.bytes()),
                new PixelSpan(flower.firstTileId(), flowerLogicalBase, flower.bytes()),
                powerupResidentSpan(nowServicedPowerup, powerupLogicalBase),
                new PixelSpan(block.firstTileId(), floatBlockLogicalBase, block.bytes())));
    }

    /**
     * Explicit USA_R11A_SPRING_DEV_PRELOADED_ART policy: all ten FloatBlock spans plus the owner's separate
     * 36-tile normal Art_Springs decode, giving eleven spans / 400 patterns. PLC_Std row 7 places this source at
     * VRAM $A400 (Palmtree Panic/Data (Act 1 Present).asm:70-71); ObjSpring_Init writes raw $0520
     * (Spring/Main.asm:188-190). Binding metadata and bytes supply native tiles $520..$543; diagonal art is absent.
     * Both player and powerup windows are immutable NOW-serviced snapshots. All spans are validated at bank
     * construction, even before an empty SAT. This services nothing and establishes no executed PLC residency;
     * the caller owns service order, world/source identity and every logical base.
     */
    public static PatternSource usaR11aSpringDevPreloadedArt(CdPlayerArtRuntime.State nowServicedPlayer,
                                                                 CdPowerupArtRuntime.State nowServicedPowerup,
                                                                 CdR11aObjectAssets objects,
                                                                 CdR11aObjectAssets.FrontierArtBank frontier,
                                                                 CdR11aObjectAssets.ContactArtBank contact,
                                                                 CdR11aObjectAssets.FloatBlockArtBank floatBlock,
                                                                 CdR11aObjectAssets.SpringArtBank spring,
                                                                 int playerLogicalBase, int ringLogicalBase,
                                                                 int mosquiLogicalBase, int plantLogicalBase,
                                                                 int rampLogicalBase, int explosionLogicalBase,
                                                                 int pointsLogicalBase, int flowerLogicalBase,
                                                                 int powerupLogicalBase, int floatBlockLogicalBase,
                                                                 int springLogicalBase) {
        Objects.requireNonNull(nowServicedPlayer, "now-serviced player art");
        Objects.requireNonNull(nowServicedPowerup, "now-serviced powerup art");
        Objects.requireNonNull(objects, "authenticated opening object assets");
        Objects.requireNonNull(frontier, "the owner's decoded frontier art bank");
        Objects.requireNonNull(contact, "the owner's decoded contact art bank");
        Objects.requireNonNull(floatBlock, "the owner's decoded FloatBlock art bank");
        Objects.requireNonNull(spring, "the owner\'s decoded normal Spring art bank");
        if (objects.identity().variant() != CdReleaseVariant.USA) {
            throw new UnsupportedOperationException("DEV preloaded object art is reviewed only for USA R11A");
        }
        var ring = objects.initialArtBank().binding(CdR11aAsset.ART_RINGS);
        var mosqui = objects.initialArtBank().binding(CdR11aAsset.ART_MOSQUI);
        var plant = frontier.binding(CdR11aAsset.ART_3D_PLANT);
        var ramp = frontier.binding(CdR11aAsset.ART_3D_RAMP_BOOST);
        var explosion = contact.binding(CdR11aAsset.ART_EXPLOSIONS);
        var points = contact.binding(CdR11aAsset.ART_POINTS);
        var flower = contact.binding(CdR11aAsset.ART_FLOWER);
        var block = floatBlock.binding(CdR11aAsset.ART_FLOAT_BLOCK);
        var normalSpring = spring.binding(CdR11aAsset.ART_SPRINGS);
        return declaredPixelBank(List.of(
                new PixelSpan(CdPlayerArtRuntime.VRAM_BYTE_ADDRESS / 32, playerLogicalBase, nowServicedPlayer.residentBank()),
                new PixelSpan(ring.firstTileId(), ringLogicalBase, ring.bytes()),
                new PixelSpan(mosqui.firstTileId(), mosquiLogicalBase, mosqui.bytes()),
                new PixelSpan(plant.firstTileId(), plantLogicalBase, plant.bytes()),
                new PixelSpan(ramp.firstTileId(), rampLogicalBase, ramp.bytes()),
                new PixelSpan(explosion.firstTileId(), explosionLogicalBase, explosion.bytes()),
                new PixelSpan(points.firstTileId(), pointsLogicalBase, points.bytes()),
                new PixelSpan(flower.firstTileId(), flowerLogicalBase, flower.bytes()),
                powerupResidentSpan(nowServicedPowerup, powerupLogicalBase),
                new PixelSpan(block.firstTileId(), floatBlockLogicalBase, block.bytes()),
                new PixelSpan(normalSpring.firstTileId(), springLogicalBase, normalSpring.bytes())));
    }

    /** The complete $480-byte resident window as 36 tiles from $544; package-private for the pure test. */
    static PixelSpan powerupResidentSpan(CdPowerupArtRuntime.State nowServicedPowerup, int logicalBase) {
        Objects.requireNonNull(nowServicedPowerup, "now-serviced powerup art");
        return new PixelSpan(CdPowerupArtRuntime.VRAM_BYTE_ADDRESS / 32, logicalBase, nowServicedPowerup.residentBank());
    }

    /**
     * Active SAT prefix only. Reverse complete native entry order for painter overlap; no ID,
     * player/object, bucket, priority, position or provenance sort. One 4x4 native piece expands
     * to sixteen tiles here AFTER queue capacity was decided. No second cull or admission pass.
     */
    public static SpritePresentation.Frame convert(CdObjectDrawQueue.CpuSat sat, PatternSource source) {
        Objects.requireNonNull(sat, "completed CPU SAT");
        Objects.requireNonNull(source, "immutable pattern source");
        byte[] bytes = sat.bytes();
        var tiles = new ArrayList<SpritePresentation.Tile>();
        var versions = new HashMap<Integer, SpritePresentation.PatternVersion>();
        var nativeSamples = new HashMap<Integer, TilePattern>();
        for (int index = sat.spriteCount() - 1; index >= 0; index--) {
            int at = index * 8;
            int yWord = word(bytes, at), xWord = word(bytes, at + 6);
            int size = bytes[at + 2] & 255, tileWord = word(bytes, at + 4);
            if (xWord < 1 || xWord > 511 || yWord > 511) {
                throw new UnsupportedOperationException("SAT entry " + index
                        + " is outside normal coordinate policy: X=" + xWord + " Y=" + yWord);
            }
            if (size > 15) { throw new UnsupportedOperationException("SAT size high bits are outside bounded mapping policy"); }
            int width = ((size >>> 2) & 3) + 1, height = (size & 3) + 1;
            // Tile word is ALREADY the complete native ADD.W then object-flip XOR result.
            // PreparedPiece avoids preparePiece's separate base/palette combination semantics.
            var piece = new SpritePieceRenderer.PreparedPiece(xWord - 128, yWord - 128, width, height,
                    tileWord & 0x7FF, tileWord & 0x7FF, (tileWord >>> 13) & 3,
                    (tileWord & 0x800) != 0, (tileWord & 0x1000) != 0,
                    (tileWord & 0x8000) != 0, false, 0, height);
            SpritePieceRenderer.renderPreparedPiece(piece, (physicalPattern, hFlip, vFlip, palette, x, y) -> {
                // A multi-tile piece can cross $7FF; mask EACH physical pattern BEFORE bank lookup.
                int nativeId = physicalPattern & 0x7FF;
                TilePattern sample = nativeSamples.get(nativeId);
                if (sample == null) {
                    sample = source.resolve(nativeId);
                    if (sample == null) { throw new UnsupportedOperationException("Pattern source has no tile $"
                            + Integer.toHexString(nativeId)); }
                    nativeSamples.put(nativeId, sample);
                }
                var previous = versions.putIfAbsent(sample.logicalPatternId(), sample.version());
                if (previous != null && !previous.equals(sample.version())) {
                    throw new IllegalArgumentException("Conflicting pixel versions share logical pattern ID "
                            + sample.logicalPatternId() + " in one picture");
                }
                tiles.add(new SpritePresentation.Tile(SpritePresentation.Layer.OBJECT, sample.logicalPatternId(),
                        palette, hFlip, vFlip, piece.piecePriority(), x, y, 8, 8,
                        true, 0, false, 1));
            });
        }
        return new SpritePresentation.Frame(tiles, List.of(), versions);
    }

    /** Sega 32-byte pattern -> existing four groups of sixteen palette-index nibbles, no mutable Pattern. */
    public static SpritePresentation.PatternVersion patternVersion(CdAssetBytes rawTile) {
        Objects.requireNonNull(rawTile, "raw pattern bytes");
        if (rawTile.length() != 32) { throw new IllegalArgumentException("One raw Sega pattern requires exactly 32 bytes"); }
        return new SpritePresentation.PatternVersion(packWord(rawTile, 0), packWord(rawTile, 16),
                packWord(rawTile, 32), packWord(rawTile, 48));
    }

    private static long packWord(CdAssetBytes bytes, int firstPixel) {
        long packed = 0;
        for (int i = 0; i < 16; i++) {
            int pixel = firstPixel + i;
            int value = bytes.unsignedByte(pixel >>> 1);
            int nibble = (pixel & 1) == 0 ? value >>> 4 : value & 15;
            packed |= (long) nibble << (i * 4);
        }
        return packed;
    }
    private static int word(byte[] bytes, int at) { return ((bytes[at] & 255) << 8) | (bytes[at + 1] & 255); }
}
