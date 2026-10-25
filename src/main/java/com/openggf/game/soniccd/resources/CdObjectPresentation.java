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


    /**
     * Explicit USA_R11A_TUNNEL_DOOR_VERTICAL_DEV_PRELOADED_ART policy: the eleven Spring spans plus the owner's
     * separate 12-tile Art_TunnelDoor decode, giving twelve spans / 412 patterns. PLC_Std row 1 places it at VRAM
     * $6500 (Palmtree Panic/Data (Act 1 Present).asm:58-59); ObjTunnelDoorV_Init selects ObjectTileIDs entry $C
     * (Tunnel Door/Main.asm:36-42), whose subtype-zero word is $8328 (Object Tile IDs (Act 1 Present).asm:20,40).
     * Native tiles $328..$333 come only from that binding's metadata and actual decoded bytes. The complete
     * NOW-serviced player and powerup windows are immutable snapshots, including the explicit powerup RAM tail.
     * Every span is validated before returning, even for an empty SAT. This services no art or clock and creates
     * no RAM, epoch, mutable owner or executed PLC residency; the caller owns world/source identity and bases.
     */
    public static PatternSource usaR11aTunnelDoorVerticalDevPreloadedArt(CdPlayerArtRuntime.State nowServicedPlayer,
                                                                         CdPowerupArtRuntime.State nowServicedPowerup,
                                                                         CdR11aObjectAssets objects,
                                                                         CdR11aObjectAssets.FrontierArtBank frontier,
                                                                         CdR11aObjectAssets.ContactArtBank contact,
                                                                         CdR11aObjectAssets.FloatBlockArtBank floatBlock,
                                                                         CdR11aObjectAssets.SpringArtBank spring,
                                                                         CdR11aObjectAssets.TunnelDoorArtBank tunnelDoor,
                                                                         int playerLogicalBase, int ringLogicalBase,
                                                                         int mosquiLogicalBase, int plantLogicalBase,
                                                                         int rampLogicalBase, int explosionLogicalBase,
                                                                         int pointsLogicalBase, int flowerLogicalBase,
                                                                         int powerupLogicalBase, int floatBlockLogicalBase,
                                                                         int springLogicalBase,
                                                                         int tunnelDoorVerticalLogicalBase) {
        Objects.requireNonNull(nowServicedPlayer, "now-serviced player art");
        Objects.requireNonNull(nowServicedPowerup, "now-serviced powerup art");
        Objects.requireNonNull(objects, "authenticated opening object assets");
        Objects.requireNonNull(frontier, "the owner's decoded frontier art bank");
        Objects.requireNonNull(contact, "the owner's decoded contact art bank");
        Objects.requireNonNull(floatBlock, "the owner's decoded FloatBlock art bank");
        Objects.requireNonNull(spring, "the owner's decoded normal Spring art bank");
        Objects.requireNonNull(tunnelDoor, "the owner's decoded Tunnel Door art bank");
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
        var door = tunnelDoor.binding(CdR11aAsset.ART_TUNNEL_DOOR);
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
                new PixelSpan(normalSpring.firstTileId(), springLogicalBase, normalSpring.bytes()),
                new PixelSpan(door.firstTileId(), tunnelDoorVerticalLogicalBase, door.bytes())));
    }

    /**
     * Explicit USA_R11A_MONITOR_TIME_DEV_PRELOADED_ART policy: the unchanged twelve Door spans plus the owner's
     * separate 82-tile Art_MonitorTimePosts decode, giving thirteen spans / 494 patterns. PLC_Std row 9 places it
     * at VRAM $B500 (Palmtree Panic/Data (Act 1 Present).asm:74-75); ObjMonitor_Init writes $05A8 and item Init
     * writes $85A8 (Monitor and Time Post/Main.asm:305,440). Native tiles $5A8..$5F9 come from binding metadata
     * and authenticated decoded bytes; palette, priority and flips remain in each final SAT word.
     * Both complete NOW-serviced player and powerup windows are immutable snapshots. Every physical/logical
     * span is validated before returning, including for an empty SAT. This services no art or clock and creates
     * no RAM or executed PLC residency; the caller owns service order, world/source identity and logical bases.
     */
    public static PatternSource usaR11aMonitorTimeDevPreloadedArt(CdPlayerArtRuntime.State nowServicedPlayer,
                                                                         CdPowerupArtRuntime.State nowServicedPowerup,
                                                                         CdR11aObjectAssets objects,
                                                                         CdR11aObjectAssets.FrontierArtBank frontier,
                                                                         CdR11aObjectAssets.ContactArtBank contact,
                                                                         CdR11aObjectAssets.FloatBlockArtBank floatBlock,
                                                                         CdR11aObjectAssets.SpringArtBank spring,
                                                                         CdR11aObjectAssets.TunnelDoorArtBank tunnelDoor,
                                                                         CdR11aObjectAssets.MonitorTimeArtBank monitorTime,
                                                                         int playerLogicalBase, int ringLogicalBase,
                                                                         int mosquiLogicalBase, int plantLogicalBase,
                                                                         int rampLogicalBase, int explosionLogicalBase,
                                                                         int pointsLogicalBase, int flowerLogicalBase,
                                                                         int powerupLogicalBase, int floatBlockLogicalBase,
                                                                         int springLogicalBase,
                                                                         int tunnelDoorVerticalLogicalBase,
                                                                         int monitorTimeLogicalBase) {
        Objects.requireNonNull(nowServicedPlayer, "now-serviced player art");
        Objects.requireNonNull(nowServicedPowerup, "now-serviced powerup art");
        Objects.requireNonNull(objects, "authenticated opening object assets");
        Objects.requireNonNull(frontier, "the owner's decoded frontier art bank");
        Objects.requireNonNull(contact, "the owner's decoded contact art bank");
        Objects.requireNonNull(floatBlock, "the owner's decoded FloatBlock art bank");
        Objects.requireNonNull(spring, "the owner's decoded normal Spring art bank");
        Objects.requireNonNull(tunnelDoor, "the owner's decoded Tunnel Door art bank");
        Objects.requireNonNull(monitorTime, "the owner's decoded Monitor/time-post art bank");
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
        var door = tunnelDoor.binding(CdR11aAsset.ART_TUNNEL_DOOR);
        var monitor = monitorTime.binding(CdR11aAsset.ART_MONITOR_TIME_POSTS);
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
                new PixelSpan(normalSpring.firstTileId(), springLogicalBase, normalSpring.bytes()),
                new PixelSpan(door.firstTileId(), tunnelDoorVerticalLogicalBase, door.bytes()),
                new PixelSpan(monitor.firstTileId(), monitorTimeLogicalBase, monitor.bytes())));
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


    /**
     * Explicit USA_R11A_CURRENT_CAMERA_DEV_PRELOADED_ART policy: retain the historical thirteen spans / 494
     * logical IDs, replacing only their 70 Mosqui, 28 Plant and 63 Ramp pixels with the supplied immutable
     * current-camera generation. The other 333 versions remain the old DEV preload, including BOTH complete
     * NOW-serviced player/powerup windows. This is one physical value per final native ID, never an actor or
     * producer choice: Pata's $7600 writes replace Mosqui's middle, and Tamabboh's $8500 writes replace parts
     * of Plant and Ramp. Anton and the $8800 gap stay unadmitted even when this State knows their bytes.
     *
     * <p>Destinations: PLC_Cam1_Full (scddisasm 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff,
     * src/Level/Palmtree Panic/Data (Act 1 Present).asm:85-100); alias writers PLC_Cam2_Incr (:183-190).
     * VInt_Level (src/Level/Main.asm:821-847) transfers the prior SAT before servicing camera art. The caller
     * selects the correct pre/post-service State; this factory services no PLC, time, upload or lifecycle.
     * All old physical/logical validation runs first, then every admitted current tile is eagerly read,
     * including for an empty SAT. Sources and converted pictures retain immutable versions across later
     * writes. Neither the supplied generation nor this hybrid DEV policy certifies actual seed provenance,
     * native cold/title history, service chronology, or renderer residency.</p>
     */
    public static PatternSource usaR11aCurrentCameraDevPreloadedArt(CdPlayerArtRuntime.State nowServicedPlayer,
                                                                   CdPowerupArtRuntime.State nowServicedPowerup,
                                                                   CdR11aObjectAssets objects,
                                                                   CdR11aObjectAssets.FrontierArtBank frontier,
                                                                   CdR11aObjectAssets.ContactArtBank contact,
                                                                   CdR11aObjectAssets.FloatBlockArtBank floatBlock,
                                                                   CdR11aObjectAssets.SpringArtBank spring,
                                                                   CdR11aObjectAssets.TunnelDoorArtBank tunnelDoor,
                                                                   CdR11aObjectAssets.MonitorTimeArtBank monitorTime,
                                                                   CdR11aCameraArtResidency.State cameraResidency,
                                                                   int playerLogicalBase, int ringLogicalBase,
                                                                   int mosquiLogicalBase, int plantLogicalBase,
                                                                   int rampLogicalBase, int explosionLogicalBase,
                                                                   int pointsLogicalBase, int flowerLogicalBase,
                                                                   int powerupLogicalBase, int floatBlockLogicalBase,
                                                                   int springLogicalBase,
                                                                   int tunnelDoorVerticalLogicalBase,
                                                                   int monitorTimeLogicalBase) {
        PatternSource preloaded = usaR11aMonitorTimeDevPreloadedArt(nowServicedPlayer, nowServicedPowerup,
                objects, frontier, contact, floatBlock, spring, tunnelDoor, monitorTime,
                playerLogicalBase, ringLogicalBase, mosquiLogicalBase, plantLogicalBase, rampLogicalBase,
                explosionLogicalBase, pointsLogicalBase, flowerLogicalBase, powerupLogicalBase,
                floatBlockLogicalBase, springLogicalBase, tunnelDoorVerticalLogicalBase, monitorTimeLogicalBase);
        Objects.requireNonNull(cameraResidency, "immutable current-camera art generation");
        var mosqui = objects.initialArtBank().binding(CdR11aAsset.ART_MOSQUI);
        var plant = frontier.binding(CdR11aAsset.ART_3D_PLANT);
        var ramp = frontier.binding(CdR11aAsset.ART_3D_RAMP_BOOST);
        var mosquiRegion = CdR11aCameraArtResidency.Region.AT_7400;
        var combinedRegion = CdR11aCameraArtResidency.Region.AT_8480;
        // Plant/Ramp are drawable subsets of the combined region, separated by one owned unadmitted tile.
        // Counts follow their authenticated source extents; the ownership class alone admits no new span.
        if (mosqui.plc().vramAddress() != mosquiRegion.destinationWord()
                || mosqui.bytes().length() != mosquiRegion.byteLength()
                || plant.plc().vramAddress() != combinedRegion.destinationWord() || plant.tileCount() != 28
                || ramp.plc().vramAddress() != plant.plc().vramAddress() + plant.bytes().length() + 32
                || ramp.tileCount() != 63
                || ramp.plc().vramAddress() + ramp.bytes().length()
                        != combinedRegion.destinationWord() + combinedRegion.byteLength()) {
            throw new IllegalArgumentException("Drawable camera bindings disagree with physical residency ownership");
        }
        var replacements = new HashMap<Integer, TilePattern>();
        for (var binding : List.of(mosqui, plant, ramp)) {
            for (int tile = 0; tile < binding.tileCount(); tile++) {
                int nativeId = binding.firstTileId() + tile;
                var admitted = preloaded.resolve(nativeId);
                replacements.put(nativeId, new TilePattern(admitted.logicalPatternId(),
                        patternVersion(CdR11aCameraArtResidency.readTile(cameraResidency, nativeId))));
            }
        }
        Map<Integer, TilePattern> frozen = Map.copyOf(replacements);
        return nativeId -> {
            var current = frozen.get(nativeId);
            return current == null ? preloaded.resolve(nativeId) : current;
        };
    }

    /**
     * Boulder admission adds exactly nineteen physical/logical bindings to the old 494-pattern policy.
     * ObjBoulder_Init stores tile $0374 (Boulder/Main.asm:30-35); PLC_Cam1_Full row0 places ART_BOULDER
     * at $6E80 (Palmtree Panic/Data (Act 1 Present).asm:85-100), header $8013. Its later eighteen-tile
     * Anton overlap is resolved ONLY from the supplied immutable current residency, never producer pixels.
     * The old overload remains unchanged. It validates its complete policy first; this overload then
     * validates the extra physical/logical range and eagerly freezes all nineteen current tiles, even
     * for empty SATs. Expanded storage admits no other complementary-region picture or preload history.
     */
    public static PatternSource usaR11aCurrentCameraDevPreloadedArt(CdPlayerArtRuntime.State nowServicedPlayer,
                                                                   CdPowerupArtRuntime.State nowServicedPowerup,
                                                                   CdR11aObjectAssets objects,
                                                                   CdR11aObjectAssets.FrontierArtBank frontier,
                                                                   CdR11aObjectAssets.ContactArtBank contact,
                                                                   CdR11aObjectAssets.FloatBlockArtBank floatBlock,
                                                                   CdR11aObjectAssets.SpringArtBank spring,
                                                                   CdR11aObjectAssets.TunnelDoorArtBank tunnelDoor,
                                                                   CdR11aObjectAssets.MonitorTimeArtBank monitorTime,
                                                                   CdR11aCameraArtResidency.State cameraResidency,
                                                                   int playerLogicalBase, int ringLogicalBase,
                                                                   int mosquiLogicalBase, int plantLogicalBase,
                                                                   int rampLogicalBase, int explosionLogicalBase,
                                                                   int pointsLogicalBase, int flowerLogicalBase,
                                                                   int powerupLogicalBase, int floatBlockLogicalBase,
                                                                   int springLogicalBase, int tunnelDoorVerticalLogicalBase,
                                                                   int monitorTimeLogicalBase, int boulderLogicalBase) {
        var previous = usaR11aCurrentCameraDevPreloadedArt(nowServicedPlayer, nowServicedPowerup, objects,
                frontier, contact, floatBlock, spring, tunnelDoor, monitorTime, cameraResidency,
                playerLogicalBase, ringLogicalBase, mosquiLogicalBase, plantLogicalBase, rampLogicalBase,
                explosionLogicalBase, pointsLogicalBase, flowerLogicalBase, powerupLogicalBase,
                floatBlockLogicalBase, springLogicalBase, tunnelDoorVerticalLogicalBase, monitorTimeLogicalBase);
        // The source owner authenticates this static frame and native selector. Conversion reads NO
        // compressed producer bank: header $8013 and the mapping's 3+16 tiles establish the admitted19.
        int first = objects.tileWord(0x0B, 0), count = 19;
        if (first != 0x374 || objects.boulder().mappingFrames().size() != 1) {
            throw new IllegalArgumentException("Boulder pixels disagree with the authenticated native binding");
        }
        byte[] current = new byte[count * 32];
        for (int tile = 0; tile < count; tile++) {
            byte[] bytes = CdR11aCameraArtResidency.readTile(cameraResidency, first + tile).copy();
            System.arraycopy(bytes, 0, current, tile * 32, 32);
        }
        var extraSpan = new PixelSpan(first, boulderLogicalBase, CdAssetBytes.copyOf(current));
        var extra = declaredPixelBank(List.of(extraSpan)); // Includes nonnegative/overflow validation.
        var oldRanges = new ArrayList<int[]>();
        oldRanges.add(new int[]{CdPlayerArtRuntime.VRAM_BYTE_ADDRESS / 32, nowServicedPlayer.residentBank().length() / 32});
        oldRanges.add(new int[]{CdPowerupArtRuntime.VRAM_BYTE_ADDRESS / 32, nowServicedPowerup.residentBank().length() / 32});
        for (var binding : List.of(objects.initialArtBank().binding(CdR11aAsset.ART_RINGS),
                objects.initialArtBank().binding(CdR11aAsset.ART_MOSQUI),
                frontier.binding(CdR11aAsset.ART_3D_PLANT), frontier.binding(CdR11aAsset.ART_3D_RAMP_BOOST),
                contact.binding(CdR11aAsset.ART_EXPLOSIONS), contact.binding(CdR11aAsset.ART_POINTS),
                contact.binding(CdR11aAsset.ART_FLOWER), floatBlock.binding(CdR11aAsset.ART_FLOAT_BLOCK),
                spring.binding(CdR11aAsset.ART_SPRINGS), tunnelDoor.binding(CdR11aAsset.ART_TUNNEL_DOOR),
                monitorTime.binding(CdR11aAsset.ART_MONITOR_TIME_POSTS))) {
            oldRanges.add(new int[]{binding.firstTileId(), binding.tileCount()});
        }
        long extraEnd = (long) boulderLogicalBase + count;
        for (int[] range : oldRanges) {
            int oldLogicalFirst = previous.resolve(range[0]).logicalPatternId();
            if (first < range[0] + range[1] && range[0] < first + count
                    || boulderLogicalBase < (long) oldLogicalFirst + range[1] && oldLogicalFirst < extraEnd) {
                throw new IllegalArgumentException("Overlapping Boulder native or logical picture binding");
            }
        }
        return nativeId -> nativeId >= first && nativeId < first + count
                ? extra.resolve(nativeId) : previous.resolve(nativeId);
    }

    /**
     * Diagonal Spring adds46 logical bindings for the CURRENT physical bytes490..4BD.
     * Spring/Main.asm:209-220 and PLC_Std row5 bind selectorF to0490 at VRAM9200;
     * the authentic mapping's final piece2C/size01 reaches tile2D (46 tiles, header002E).
     * The old513 policy validates first and stays unchanged. A later producer, including
     * BigRing at9100..9E40, replaces these same physical bytes; no actor-selected fallback.
     * Explicit DEV preload/carry and service scheduling remain with the caller.
     */
    public static PatternSource usaR11aCurrentCameraDevPreloadedArt(CdPlayerArtRuntime.State nowServicedPlayer,
                                                                   CdPowerupArtRuntime.State nowServicedPowerup,
                                                                   CdR11aObjectAssets objects,
                                                                   CdR11aObjectAssets.FrontierArtBank frontier,
                                                                   CdR11aObjectAssets.ContactArtBank contact,
                                                                   CdR11aObjectAssets.FloatBlockArtBank floatBlock,
                                                                   CdR11aObjectAssets.SpringArtBank spring,
                                                                   CdR11aObjectAssets.TunnelDoorArtBank tunnelDoor,
                                                                   CdR11aObjectAssets.MonitorTimeArtBank monitorTime,
                                                                   CdR11aCameraArtResidency.State cameraResidency,
                                                                   int playerLogicalBase, int ringLogicalBase,
                                                                   int mosquiLogicalBase, int plantLogicalBase,
                                                                   int rampLogicalBase, int explosionLogicalBase,
                                                                   int pointsLogicalBase, int flowerLogicalBase,
                                                                   int powerupLogicalBase, int floatBlockLogicalBase,
                                                                   int springLogicalBase, int tunnelDoorVerticalLogicalBase,
                                                                   int monitorTimeLogicalBase, int boulderLogicalBase,
                                                                   int diagonalSpringLogicalBase) {
        var previous = usaR11aCurrentCameraDevPreloadedArt(nowServicedPlayer, nowServicedPowerup, objects,
                frontier, contact, floatBlock, spring, tunnelDoor, monitorTime, cameraResidency,
                playerLogicalBase, ringLogicalBase, mosquiLogicalBase, plantLogicalBase, rampLogicalBase,
                explosionLogicalBase, pointsLogicalBase, flowerLogicalBase, powerupLogicalBase,
                floatBlockLogicalBase, springLogicalBase, tunnelDoorVerticalLogicalBase,
                monitorTimeLogicalBase, boulderLogicalBase);
        int first = objects.tileWord(0x0F, 0), count = 46;
        var visual = objects.springDiagonal();
        if (first != 0x490 || visual.mappingTableOffset() != 80 || visual.mappingFrames().size() != 3) {
            throw new IllegalArgumentException("Diagonal Spring disagrees with its authenticated native binding");
        }
        byte[] current = new byte[count * 32];
        for (int tile = 0; tile < count; tile++) {
            byte[] bytes = CdR11aCameraArtResidency.readTile(cameraResidency, first + tile).copy();
            System.arraycopy(bytes, 0, current, tile * 32, 32);
        }
        var extra = declaredPixelBank(List.of(new PixelSpan(first, diagonalSpringLogicalBase,
                CdAssetBytes.copyOf(current))));
        var oldRanges = new ArrayList<int[]>();
        oldRanges.add(new int[]{CdPlayerArtRuntime.VRAM_BYTE_ADDRESS / 32, nowServicedPlayer.residentBank().length() / 32});
        oldRanges.add(new int[]{CdPowerupArtRuntime.VRAM_BYTE_ADDRESS / 32, nowServicedPowerup.residentBank().length() / 32});
        for (var binding : List.of(objects.initialArtBank().binding(CdR11aAsset.ART_RINGS),
                objects.initialArtBank().binding(CdR11aAsset.ART_MOSQUI),
                frontier.binding(CdR11aAsset.ART_3D_PLANT), frontier.binding(CdR11aAsset.ART_3D_RAMP_BOOST),
                contact.binding(CdR11aAsset.ART_EXPLOSIONS), contact.binding(CdR11aAsset.ART_POINTS),
                contact.binding(CdR11aAsset.ART_FLOWER), floatBlock.binding(CdR11aAsset.ART_FLOAT_BLOCK),
                spring.binding(CdR11aAsset.ART_SPRINGS), tunnelDoor.binding(CdR11aAsset.ART_TUNNEL_DOOR),
                monitorTime.binding(CdR11aAsset.ART_MONITOR_TIME_POSTS))) {
            oldRanges.add(new int[]{binding.firstTileId(), binding.tileCount()});
        }
        oldRanges.add(new int[]{objects.tileWord(0x0B, 0), 19});
        long extraEnd = (long) diagonalSpringLogicalBase + count;
        for (int[] range : oldRanges) {
            int oldLogicalFirst = previous.resolve(range[0]).logicalPatternId();
            if (first < range[0] + range[1] && range[0] < first + count
                    || diagonalSpringLogicalBase < (long) oldLogicalFirst + range[1] && oldLogicalFirst < extraEnd) {
                throw new IllegalArgumentException("Overlapping diagonal Spring native or logical picture binding");
            }
        }
        return nativeId -> nativeId >= first && nativeId < first + count
                ? extra.resolve(nativeId) : previous.resolve(nativeId);
    }

    /**
     * Spikes appends eight bindings to the unchanged C25/current559 policy, giving 567 patterns in
     * sixteen logical spans. ObjSpikes_Init selects ObjectTileIDs $A/subtype2 zero ($0320) and stores
     * MapSpr_Spikes $20C1F0 (scddisasm 1ecc5f3af, Spikes/Main.asm:21-25,87-89). Four size05 pieces
     * reuse eight patterns. PLC_Std row0 describes ART_SPIKES at $238018 -> $6400, header $8008;
     * this metadata is evidence, never a producer bank or a preload performed by this factory.
     *
     * <p>C25 validates first. All eight tiles are eagerly copied ONLY from the supplied immutable
     * current physical State, even for an empty SAT. UNKNOWN ingress rejects without source fallback.
     * The AT_6400 half-open extent is exactly $6400..$6500; adjacent Door $328 and the unowned gap
     * remain separate. Every old native/logical span is checked for overlap, and all old IDs, NOW
     * player/powerup windows and current camera versions retain their existing policy. DEV seeding,
     * service order, capture and renderer publication remain with the caller.</p>
     */
    public static PatternSource usaR11aCurrentCameraDevPreloadedArt(CdPlayerArtRuntime.State nowServicedPlayer,
                                                                   CdPowerupArtRuntime.State nowServicedPowerup,
                                                                   CdR11aObjectAssets objects,
                                                                   CdR11aObjectAssets.FrontierArtBank frontier,
                                                                   CdR11aObjectAssets.ContactArtBank contact,
                                                                   CdR11aObjectAssets.FloatBlockArtBank floatBlock,
                                                                   CdR11aObjectAssets.SpringArtBank spring,
                                                                   CdR11aObjectAssets.TunnelDoorArtBank tunnelDoor,
                                                                   CdR11aObjectAssets.MonitorTimeArtBank monitorTime,
                                                                   CdR11aCameraArtResidency.State cameraResidency,
                                                                   int playerLogicalBase, int ringLogicalBase,
                                                                   int mosquiLogicalBase, int plantLogicalBase,
                                                                   int rampLogicalBase, int explosionLogicalBase,
                                                                   int pointsLogicalBase, int flowerLogicalBase,
                                                                   int powerupLogicalBase, int floatBlockLogicalBase,
                                                                   int springLogicalBase, int tunnelDoorVerticalLogicalBase,
                                                                   int monitorTimeLogicalBase, int boulderLogicalBase,
                                                                   int diagonalSpringLogicalBase, int spikesLogicalBase) {
        var previous = usaR11aCurrentCameraDevPreloadedArt(nowServicedPlayer, nowServicedPowerup, objects,
                frontier, contact, floatBlock, spring, tunnelDoor, monitorTime, cameraResidency,
                playerLogicalBase, ringLogicalBase, mosquiLogicalBase, plantLogicalBase, rampLogicalBase,
                explosionLogicalBase, pointsLogicalBase, flowerLogicalBase, powerupLogicalBase,
                floatBlockLogicalBase, springLogicalBase, tunnelDoorVerticalLogicalBase,
                monitorTimeLogicalBase, boulderLogicalBase, diagonalSpringLogicalBase);
        int first = objects.tileWord(0x0A, 0), count = 8;
        var visual = objects.spikes();
        var region = CdR11aCameraArtResidency.Region.AT_6400;
        if (first != 0x320 || visual.mappingTableOffset() != 0 || visual.mappingFrames().size() != 1
                || !visual.animations().isEmpty() || visual.animationTable().length() != 0
                || region.destinationWord() != first * 32 || region.byteLength() != count * 32) {
            throw new IllegalArgumentException("Spikes disagrees with its authenticated static mapping or physical extent");
        }
        var referenced = new HashSet<Integer>();
        int cells = 0;
        for (var piece : visual.mappingFrames().getFirst().pieces()) {
            for (int tile = 0; tile < piece.tileCount(); tile++) {
                int offset = piece.tileOffset() + tile;
                if (offset < 0 || offset >= count) {
                    throw new IllegalArgumentException("Spikes mapping exceeds its eight current patterns");
                }
                referenced.add(offset);
                cells++;
            }
        }
        if (visual.mappingFrames().getFirst().pieces().size() != 4 || cells != 16 || referenced.size() != count) {
            throw new IllegalArgumentException("Spikes static frame must reuse eight patterns in sixteen cells");
        }
        byte[] current = new byte[count * 32];
        for (int tile = 0; tile < count; tile++) {
            byte[] bytes = CdR11aCameraArtResidency.readTile(cameraResidency, first + tile).copy();
            System.arraycopy(bytes, 0, current, tile * 32, 32);
        }
        var extraSpan = new PixelSpan(first, spikesLogicalBase, CdAssetBytes.copyOf(current));
        var extra = declaredPixelBank(List.of(extraSpan));
        var oldRanges = new ArrayList<int[]>();
        oldRanges.add(new int[]{CdPlayerArtRuntime.VRAM_BYTE_ADDRESS / 32, nowServicedPlayer.residentBank().length() / 32});
        oldRanges.add(new int[]{CdPowerupArtRuntime.VRAM_BYTE_ADDRESS / 32, nowServicedPowerup.residentBank().length() / 32});
        for (var binding : List.of(objects.initialArtBank().binding(CdR11aAsset.ART_RINGS),
                objects.initialArtBank().binding(CdR11aAsset.ART_MOSQUI),
                frontier.binding(CdR11aAsset.ART_3D_PLANT), frontier.binding(CdR11aAsset.ART_3D_RAMP_BOOST),
                contact.binding(CdR11aAsset.ART_EXPLOSIONS), contact.binding(CdR11aAsset.ART_POINTS),
                contact.binding(CdR11aAsset.ART_FLOWER), floatBlock.binding(CdR11aAsset.ART_FLOAT_BLOCK),
                spring.binding(CdR11aAsset.ART_SPRINGS), tunnelDoor.binding(CdR11aAsset.ART_TUNNEL_DOOR),
                monitorTime.binding(CdR11aAsset.ART_MONITOR_TIME_POSTS))) {
            oldRanges.add(new int[]{binding.firstTileId(), binding.tileCount()});
        }
        oldRanges.add(new int[]{objects.tileWord(0x0B, 0), 19});
        oldRanges.add(new int[]{objects.tileWord(0x0F, 0), 46});
        long extraEnd = (long) spikesLogicalBase + count;
        for (int[] range : oldRanges) {
            int oldLogicalFirst = previous.resolve(range[0]).logicalPatternId();
            if (first < range[0] + range[1] && range[0] < first + count
                    || spikesLogicalBase < (long) oldLogicalFirst + range[1] && oldLogicalFirst < extraEnd) {
                throw new IllegalArgumentException("Overlapping Spikes native or logical picture binding");
            }
        }
        return nativeId -> nativeId >= first && nativeId < first + count
                ? extra.resolve(nativeId) : previous.resolve(nativeId);
    }

    /**
     * Current Checkpoint12 extends C26/current567 to seventeen spans and579 patterns.
     * ObjCheckpoint_Init (Checkpoint/Main.asm:74-75,101-102 at scddisasm
     * 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff) writes literal $06CB and
     * the actual $23F120 mapping pointer. PLC_Level (Data (Act 1 Present).asm:47-52)
     * places the $800C producer at $D960. That metadata is no preload authority.
     * All twelve bytes-per-tile versions are eagerly copied from the one current
     * physical State, including unreferenced tiles in a particular SAT frame.
     * The old567 binding/overlap validation runs first and remains unchanged.
     * Unknown ingress rejects, with no object-selected producer fallback.
     */
    public static PatternSource usaR11aCurrentCameraDevPreloadedArt(CdPlayerArtRuntime.State nowServicedPlayer,
                                                                   CdPowerupArtRuntime.State nowServicedPowerup,
                                                                   CdR11aObjectAssets objects,
                                                                   CdR11aObjectAssets.FrontierArtBank frontier,
                                                                   CdR11aObjectAssets.ContactArtBank contact,
                                                                   CdR11aObjectAssets.FloatBlockArtBank floatBlock,
                                                                   CdR11aObjectAssets.SpringArtBank spring,
                                                                   CdR11aObjectAssets.TunnelDoorArtBank tunnelDoor,
                                                                   CdR11aObjectAssets.MonitorTimeArtBank monitorTime,
                                                                   CdR11aCameraArtResidency.State cameraResidency,
                                                                   int playerLogicalBase, int ringLogicalBase,
                                                                   int mosquiLogicalBase, int plantLogicalBase,
                                                                   int rampLogicalBase, int explosionLogicalBase,
                                                                   int pointsLogicalBase, int flowerLogicalBase,
                                                                   int powerupLogicalBase, int floatBlockLogicalBase,
                                                                   int springLogicalBase, int tunnelDoorVerticalLogicalBase,
                                                                   int monitorTimeLogicalBase, int boulderLogicalBase,
                                                                   int diagonalSpringLogicalBase, int spikesLogicalBase,
                                                                   int checkpointLogicalBase) {
        var previous = usaR11aCurrentCameraDevPreloadedArt(nowServicedPlayer, nowServicedPowerup, objects,
                frontier, contact, floatBlock, spring, tunnelDoor, monitorTime, cameraResidency,
                playerLogicalBase, ringLogicalBase, mosquiLogicalBase, plantLogicalBase, rampLogicalBase,
                explosionLogicalBase, pointsLogicalBase, flowerLogicalBase, powerupLogicalBase,
                floatBlockLogicalBase, springLogicalBase, tunnelDoorVerticalLogicalBase,
                monitorTimeLogicalBase, boulderLogicalBase, diagonalSpringLogicalBase, spikesLogicalBase);
        // ObjCheckpoint_Init writes literal $06CB; it never calls SetObjectTileID.
        int first = 0x6CB, count = 12;
        var visual = objects.checkpoint();
        var region = CdR11aCameraArtResidency.Region.AT_D960;
        if (visual.mappingTableOffset() != 0 || visual.mappingFrames().size() != 4
                || visual.animations().size() != 1
                || region.destinationWord() != first * 32 || region.byteLength() != count * 32) {
            throw new IllegalArgumentException("Checkpoint disagrees with its authenticated mapping or physical extent");
        }
        var referenced = new HashSet<Integer>();
        for (var frame : visual.mappingFrames()) for (var piece : frame.pieces()) {
            for (int tile = 0; tile < piece.tileCount(); tile++) {
                int offset = piece.tileOffset() + tile;
                if (offset < 0 || offset >= count) {
                    throw new IllegalArgumentException("Checkpoint mapping exceeds its twelve current patterns");
                }
                referenced.add(offset);
            }
        }
        if (referenced.size() != count) {
            throw new IllegalArgumentException("Checkpoint frames must address all twelve current patterns");
        }
        byte[] current = new byte[count * 32];
        for (int tile = 0; tile < count; tile++) {
            byte[] bytes = CdR11aCameraArtResidency.readTile(cameraResidency, first + tile).copy();
            System.arraycopy(bytes, 0, current, tile * 32, 32);
        }
        var extraSpan = new PixelSpan(first, checkpointLogicalBase, CdAssetBytes.copyOf(current));
        var extra = declaredPixelBank(List.of(extraSpan));
        var oldRanges = new ArrayList<int[]>();
        oldRanges.add(new int[]{CdPlayerArtRuntime.VRAM_BYTE_ADDRESS / 32, nowServicedPlayer.residentBank().length() / 32});
        oldRanges.add(new int[]{CdPowerupArtRuntime.VRAM_BYTE_ADDRESS / 32, nowServicedPowerup.residentBank().length() / 32});
        for (var binding : List.of(objects.initialArtBank().binding(CdR11aAsset.ART_RINGS),
                objects.initialArtBank().binding(CdR11aAsset.ART_MOSQUI),
                frontier.binding(CdR11aAsset.ART_3D_PLANT), frontier.binding(CdR11aAsset.ART_3D_RAMP_BOOST),
                contact.binding(CdR11aAsset.ART_EXPLOSIONS), contact.binding(CdR11aAsset.ART_POINTS),
                contact.binding(CdR11aAsset.ART_FLOWER), floatBlock.binding(CdR11aAsset.ART_FLOAT_BLOCK),
                spring.binding(CdR11aAsset.ART_SPRINGS), tunnelDoor.binding(CdR11aAsset.ART_TUNNEL_DOOR),
                monitorTime.binding(CdR11aAsset.ART_MONITOR_TIME_POSTS))) {
            oldRanges.add(new int[]{binding.firstTileId(), binding.tileCount()});
        }
        oldRanges.add(new int[]{objects.tileWord(0x0B, 0), 19});
        oldRanges.add(new int[]{objects.tileWord(0x0F, 0), 46});
        oldRanges.add(new int[]{objects.tileWord(0x0A, 0), 8});
        long extraEnd = (long) checkpointLogicalBase + count;
        for (int[] range : oldRanges) {
            int oldLogicalFirst = previous.resolve(range[0]).logicalPatternId();
            if (first < range[0] + range[1] && range[0] < first + count
                    || checkpointLogicalBase < (long) oldLogicalFirst + range[1] && oldLogicalFirst < extraEnd) {
                throw new IllegalArgumentException("Overlapping Checkpoint native or logical picture binding");
            }
        }
        return nativeId -> nativeId >= first && nativeId < first + count
                ? extra.resolve(nativeId) : previous.resolve(nativeId);
    }

    /**
     * C28 adds the canonical Anton logical extent to C27: eighteen spans / 601 potential
     * logical patterns. Despite the compatibility factory name, the new22 have conditional
     * native-current admission: cold DEV legitimately leaves $8120..$83E0 UNKNOWN.
     * C27 executes FIRST with its unchanged eager579 policy and argument precedence.
     *
     * <p>ObjAnton_Init selects ObjectTileIDs selector2/subtype2 zero, word $2409
     * (Anton/Main.asm:32-33; Object Tile IDs (Act 1 Present).asm:7-30 at scddisasm
     * 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff). The complete four-frame/two-script
     * visual reaches offsets0..21. Validate its canonical metadata and all seventeen
     * legacy native/logical spans even for an empty SAT, without reading new pixels,
     * consulting sectionArtSources, decoding art or manufacturing a validation span.</p>
     *
     * <p>Capture this immutable physical State value. Only resolve of a selected final
     * native $409..$41E reads that tile through the strict current owner; UNKNOWN stops
     * there. No queue service, live callback, pixel cache, padding or producer fallback.
     * A source and every converted Frame keep their old knownness/bytes after new commits.
     * The $2370 relocation word is metadata, not service or admission authority:
     * $374..$385 retain the existing eighteen Boulder logical aliases/current versions,
     * $386 survives relocation, and lower $370..$373 remain unadmitted here.
     * Canonical pixels always come from current $8120 bytes, regardless of producer.</p>
     */
    public static PatternSource usaR11aCurrentCameraDevPreloadedArt(CdPlayerArtRuntime.State nowServicedPlayer,
                                                                   CdPowerupArtRuntime.State nowServicedPowerup,
                                                                   CdR11aObjectAssets objects,
                                                                   CdR11aObjectAssets.FrontierArtBank frontier,
                                                                   CdR11aObjectAssets.ContactArtBank contact,
                                                                   CdR11aObjectAssets.FloatBlockArtBank floatBlock,
                                                                   CdR11aObjectAssets.SpringArtBank spring,
                                                                   CdR11aObjectAssets.TunnelDoorArtBank tunnelDoor,
                                                                   CdR11aObjectAssets.MonitorTimeArtBank monitorTime,
                                                                   CdR11aCameraArtResidency.State cameraResidency,
                                                                   int playerLogicalBase, int ringLogicalBase,
                                                                   int mosquiLogicalBase, int plantLogicalBase,
                                                                   int rampLogicalBase, int explosionLogicalBase,
                                                                   int pointsLogicalBase, int flowerLogicalBase,
                                                                   int powerupLogicalBase, int floatBlockLogicalBase,
                                                                   int springLogicalBase, int tunnelDoorVerticalLogicalBase,
                                                                   int monitorTimeLogicalBase, int boulderLogicalBase,
                                                                   int diagonalSpringLogicalBase, int spikesLogicalBase,
                                                                   int checkpointLogicalBase, final int antonLogicalBase) {
        var previous = usaR11aCurrentCameraDevPreloadedArt(nowServicedPlayer, nowServicedPowerup, objects,
                frontier, contact, floatBlock, spring, tunnelDoor, monitorTime, cameraResidency,
                playerLogicalBase, ringLogicalBase, mosquiLogicalBase, plantLogicalBase, rampLogicalBase,
                explosionLogicalBase, pointsLogicalBase, flowerLogicalBase, powerupLogicalBase,
                floatBlockLogicalBase, springLogicalBase, tunnelDoorVerticalLogicalBase,
                monitorTimeLogicalBase, boulderLogicalBase, diagonalSpringLogicalBase, spikesLogicalBase,
                checkpointLogicalBase);
        int tileWord = objects.tileWord(2, 0), first = tileWord & 0x7FF, count = 22;
        var visual = objects.anton();
        var region = CdR11aCameraArtResidency.Region.AT_8120;
        if (tileWord != 0x2409 || first != 0x409 || first < 0 || first > 2048 - count
                || region.destinationWord() != 0x8120 || region.destinationWord() != first * 32
                || region.byteLength() != 0x2C0 || region.byteLength() != count * 32 || region.tileCount() != count
                || visual.mappingTableOffset() != 0 || visual.mappings().length() != 112
                || visual.mappingFrames().size() != 4 || visual.animations().size() != 2
                || visual.animationTable().length() != 12) {
            throw new IllegalArgumentException("Anton disagrees with its authenticated canonical metadata");
        }
        if (antonLogicalBase < 0 || (long) antonLogicalBase + count - 1 > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Anton logical pattern extent must be nonnegative and bounded");
        }
        var referenced = new HashSet<Integer>();
        for (var frame : visual.mappingFrames()) {
            if (frame.pieces().size() != 5) {
                throw new IllegalArgumentException("Anton requires five pieces in each of its four frames");
            }
            for (var piece : frame.pieces()) for (int tile = 0; tile < piece.tileCount(); tile++) {
                int offset = piece.tileOffset() + tile;
                if (offset < 0 || offset >= count) {
                    throw new IllegalArgumentException("Anton mapping exceeds its twenty-two current patterns");
                }
                referenced.add(offset);
            }
        }
        if (referenced.size() != count) {
            throw new IllegalArgumentException("Anton frames must address all twenty-two current patterns");
        }
        // Reuse actual old binding extents and C27's already-frozen logical lookup, for metadata only.
        var oldRanges = new ArrayList<int[]>();
        oldRanges.add(new int[]{CdPlayerArtRuntime.VRAM_BYTE_ADDRESS / 32, nowServicedPlayer.residentBank().length() / 32});
        oldRanges.add(new int[]{CdPowerupArtRuntime.VRAM_BYTE_ADDRESS / 32, nowServicedPowerup.residentBank().length() / 32});
        for (var binding : List.of(objects.initialArtBank().binding(CdR11aAsset.ART_RINGS),
                objects.initialArtBank().binding(CdR11aAsset.ART_MOSQUI),
                frontier.binding(CdR11aAsset.ART_3D_PLANT), frontier.binding(CdR11aAsset.ART_3D_RAMP_BOOST),
                contact.binding(CdR11aAsset.ART_EXPLOSIONS), contact.binding(CdR11aAsset.ART_POINTS),
                contact.binding(CdR11aAsset.ART_FLOWER), floatBlock.binding(CdR11aAsset.ART_FLOAT_BLOCK),
                spring.binding(CdR11aAsset.ART_SPRINGS), tunnelDoor.binding(CdR11aAsset.ART_TUNNEL_DOOR),
                monitorTime.binding(CdR11aAsset.ART_MONITOR_TIME_POSTS))) {
            oldRanges.add(new int[]{binding.firstTileId(), binding.tileCount()});
        }
        oldRanges.add(new int[]{objects.tileWord(0x0B, 0), 19});
        oldRanges.add(new int[]{objects.tileWord(0x0F, 0), 46});
        oldRanges.add(new int[]{objects.tileWord(0x0A, 0), 8});
        oldRanges.add(new int[]{0x6CB, 12});
        long nativeEnd = (long) first + count, logicalEnd = (long) antonLogicalBase + count;
        for (int[] range : oldRanges) {
            int oldLogicalFirst = previous.resolve(range[0]).logicalPatternId();
            if (first < (long) range[0] + range[1] && range[0] < nativeEnd
                    || antonLogicalBase < (long) oldLogicalFirst + range[1] && oldLogicalFirst < logicalEnd) {
                throw new IllegalArgumentException("Overlapping Anton native or logical picture binding");
            }
        }
        CdR11aCameraArtResidency.State snapshot = cameraResidency;
        return nativeId -> {
            // No mask: out-of-range inputs delegate to the unchanged strict C27 boundary.
            if (nativeId >= first && nativeId < nativeEnd) {
                return new TilePattern(antonLogicalBase + (nativeId - first),
                        patternVersion(CdR11aCameraArtResidency.readTile(snapshot, nativeId)));
            }
            return previous.resolve(nativeId);
        };
    }

    /**
     * C29 appends one Platform57 logical span to unchanged C28: nineteen spans and658
     * potential patterns. ObjCollapsePlatform_Init and both fragment producers store literal
     * $44BE (scddisasm 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff,
     * src/Level/Palmtree Panic/Objects/Collapsing Platform/Main.asm:35,182,279).
     * PLC_Std row6 (Data (Act 1 Present).asm:68-69) describes $97C0..$9EE0;
     * its57 tiles lie wholly inside existing AT_9000. No selector or region is added.
     *
     * <p>C28 executes FIRST, retaining its eager579 and selected-current Anton22 policies.
     * Metadata and all eighteen explicit old logical/native extents validate without resolving
     * Anton or Platform pixels, even for an empty SAT. Four unscripted counted visuals retain
     * their complete sources and sparse40-tile union; all57 canonical tiles are conditionally
     * admitted. Only a selected final native $4BE..$4F6 strictly reads this immutable supplied
     * residency. UNKNOWN has no producer fallback. A later BigRing-address overwrite is the
     * current owner; old sources and converted pictures retain their previous values.</p>
     *
     * <p>No source-bank lookup, art decode, preload, queue service, live callback or pixel
     * cache occurs here. Explicit DEV ingress, verbatim restart and publication belong to
     * the caller.</p>
     */
    public static PatternSource usaR11aCurrentCameraDevPreloadedArt(CdPlayerArtRuntime.State nowServicedPlayer,
                                                                   CdPowerupArtRuntime.State nowServicedPowerup,
                                                                   CdR11aObjectAssets objects,
                                                                   CdR11aObjectAssets.FrontierArtBank frontier,
                                                                   CdR11aObjectAssets.ContactArtBank contact,
                                                                   CdR11aObjectAssets.FloatBlockArtBank floatBlock,
                                                                   CdR11aObjectAssets.SpringArtBank spring,
                                                                   CdR11aObjectAssets.TunnelDoorArtBank tunnelDoor,
                                                                   CdR11aObjectAssets.MonitorTimeArtBank monitorTime,
                                                                   CdR11aCameraArtResidency.State cameraResidency,
                                                                   int playerLogicalBase, int ringLogicalBase,
                                                                   int mosquiLogicalBase, int plantLogicalBase,
                                                                   int rampLogicalBase, int explosionLogicalBase,
                                                                   int pointsLogicalBase, int flowerLogicalBase,
                                                                   int powerupLogicalBase, int floatBlockLogicalBase,
                                                                   int springLogicalBase, int tunnelDoorVerticalLogicalBase,
                                                                   int monitorTimeLogicalBase, int boulderLogicalBase,
                                                                   int diagonalSpringLogicalBase, int spikesLogicalBase,
                                                                   int checkpointLogicalBase, final int antonLogicalBase,
                                                                   final int collapsePlatformLogicalBase) {
        var previous = usaR11aCurrentCameraDevPreloadedArt(nowServicedPlayer, nowServicedPowerup, objects,
                frontier, contact, floatBlock, spring, tunnelDoor, monitorTime, cameraResidency,
                playerLogicalBase, ringLogicalBase, mosquiLogicalBase, plantLogicalBase, rampLogicalBase,
                explosionLogicalBase, pointsLogicalBase, flowerLogicalBase, powerupLogicalBase,
                floatBlockLogicalBase, springLogicalBase, tunnelDoorVerticalLogicalBase,
                monitorTimeLogicalBase, boulderLogicalBase, diagonalSpringLogicalBase, spikesLogicalBase,
                checkpointLogicalBase, antonLogicalBase);
        // Literal oTile word; Collapse never calls SetObjectTileID.
        final int tileWord = 0x44BE, first = tileWord & 0x7FF, count = 57;
        var region = CdR11aCameraArtResidency.Region.AT_9000;
        if (first != 0x4BE || first > 2048 - count || first * 32 != 0x97C0
                || (first + count) * 32 != 0x9EE0
                || first * 32 < region.destinationWord()
                || (long) (first + count) * 32 > (long) region.destinationWord() + region.byteLength()) {
            throw new IllegalArgumentException("Collapse Platform exceeds its existing physical owner");
        }
        if (collapsePlatformLogicalBase < 0
                || (long) collapsePlatformLogicalBase + count - 1 > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Collapse Platform logical pattern extent must be nonnegative and bounded");
        }
        var tables = List.of(CdR11aObjectAssets.Table.COLLAPSE_PLATFORM_MAPPINGS_1,
                CdR11aObjectAssets.Table.COLLAPSE_PLATFORM_MAPPINGS_2,
                CdR11aObjectAssets.Table.COLLAPSE_PLATFORM_MAPPINGS_3,
                CdR11aObjectAssets.Table.COLLAPSE_PLATFORM_MAPPINGS_4);
        var visuals = List.of(objects.collapsePlatform1(), objects.collapsePlatform2(),
                objects.collapsePlatform3(), objects.collapsePlatform4());
        int[] lengths = {118, 408, 62, 158}, frames = {1, 6, 7, 6};
        var referenced = new HashSet<Integer>();
        for (int index = 0; index < visuals.size(); index++) {
            var visual = visuals.get(index);
            var table = tables.get(index);
            var view = objects.view(table);
            if (view.table() != table || !view.range().equals(table.range())
                    || view.bytes().length() != lengths[index]
                    || visual.mappings().length() != lengths[index] || visual.mappingTableOffset() != 0
                    || visual.mappingFrames().size() != frames[index]
                    || !visual.animations().isEmpty() || visual.animationTable().length() != 0
                    || !java.util.Arrays.equals(view.bytes().copy(), visual.mappings().copy())) {
                throw new IllegalArgumentException("Collapse Platform disagrees with its authenticated static mapping sources");
            }
            for (var frame : visual.mappingFrames()) for (var piece : frame.pieces()) {
                for (int tile = 0; tile < piece.tileCount(); tile++) {
                    int offset = piece.tileOffset() + tile;
                    if (offset < 0 || offset >= count) {
                        throw new IllegalArgumentException("Collapse mapping exceeds its fifty-seven current patterns");
                    }
                    referenced.add(offset);
                }
            }
        }
        // Sparse native union is intentional; unreferenced canonical tiles remain conditionally admitted.
        for (int offset = 0; offset < count; offset++) {
            boolean expected = offset >= 0x0D && offset <= 0x2E || offset >= 0x31 && offset <= 0x36;
            if (referenced.contains(offset) != expected) {
                throw new IllegalArgumentException("Collapse static roots disagree with their forty-pattern union");
            }
        }
        // Explicit bases avoid reading lazy Anton pixels merely to discover their logical ID.
        var oldRanges = new ArrayList<int[]>();
        oldRanges.add(new int[]{CdPlayerArtRuntime.VRAM_BYTE_ADDRESS / 32,
                nowServicedPlayer.residentBank().length() / 32, playerLogicalBase});
        oldRanges.add(new int[]{CdPowerupArtRuntime.VRAM_BYTE_ADDRESS / 32,
                nowServicedPowerup.residentBank().length() / 32, powerupLogicalBase});
        var bindings = List.of(objects.initialArtBank().binding(CdR11aAsset.ART_RINGS),
                objects.initialArtBank().binding(CdR11aAsset.ART_MOSQUI),
                frontier.binding(CdR11aAsset.ART_3D_PLANT), frontier.binding(CdR11aAsset.ART_3D_RAMP_BOOST),
                contact.binding(CdR11aAsset.ART_EXPLOSIONS), contact.binding(CdR11aAsset.ART_POINTS),
                contact.binding(CdR11aAsset.ART_FLOWER), floatBlock.binding(CdR11aAsset.ART_FLOAT_BLOCK),
                spring.binding(CdR11aAsset.ART_SPRINGS), tunnelDoor.binding(CdR11aAsset.ART_TUNNEL_DOOR),
                monitorTime.binding(CdR11aAsset.ART_MONITOR_TIME_POSTS));
        int[] logicalBases = {ringLogicalBase, mosquiLogicalBase, plantLogicalBase, rampLogicalBase,
                explosionLogicalBase, pointsLogicalBase, flowerLogicalBase, floatBlockLogicalBase,
                springLogicalBase, tunnelDoorVerticalLogicalBase, monitorTimeLogicalBase};
        for (int index = 0; index < bindings.size(); index++) {
            var binding = bindings.get(index);
            oldRanges.add(new int[]{binding.firstTileId(), binding.tileCount(), logicalBases[index]});
        }
        oldRanges.add(new int[]{objects.tileWord(0x0B, 0), 19, boulderLogicalBase});
        oldRanges.add(new int[]{objects.tileWord(0x0F, 0), 46, diagonalSpringLogicalBase});
        oldRanges.add(new int[]{objects.tileWord(0x0A, 0), 8, spikesLogicalBase});
        oldRanges.add(new int[]{0x6CB, 12, checkpointLogicalBase});
        oldRanges.add(new int[]{objects.tileWord(2, 0) & 0x7FF, 22, antonLogicalBase});
        long nativeEnd = (long) first + count, logicalEnd = (long) collapsePlatformLogicalBase + count;
        for (int[] range : oldRanges) {
            if (first < (long) range[0] + range[1] && range[0] < nativeEnd
                    || collapsePlatformLogicalBase < (long) range[2] + range[1] && range[2] < logicalEnd) {
                throw new IllegalArgumentException("Overlapping Collapse Platform native or logical picture binding");
            }
        }
        CdR11aCameraArtResidency.State snapshot = cameraResidency;
        return nativeId -> {
            if (nativeId >= first && nativeId < nativeEnd) {
                return new TilePattern(collapsePlatformLogicalBase + (nativeId - first),
                        patternVersion(CdR11aCameraArtResidency.readTile(snapshot, nativeId)));
            }
            return previous.resolve(nativeId);
        };
    }
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
