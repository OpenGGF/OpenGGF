package com.openggf.game.sonic2.competition;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.data.RomChannel;
import com.openggf.data.compression.KosinskiReader;
import com.openggf.game.common.CommonPlacementParser;
import com.openggf.game.sonic2.Sonic2ObjectPlacement;
import com.openggf.game.sonic2.Sonic2PlcLoader;
import com.openggf.game.sonic2.Sonic2RingPlacement;
import com.openggf.game.sonic2.ZoneAct;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.resources.PlcParser.PlcDefinition;
import com.openggf.level.rings.RingSpawn;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Native REV01 competition level sources, including CNZ's separate object lists.
 * This is a ROM descriptor/base decoder, not a claim that a host has implemented
 * interlace rendering, two-camera streaming, or the shipped animated-map overrun.
 */
public record Sonic2CompetitionLevelProfile(
        ZoneAct zoneAct, Art art, int layoutAddress, int objectAddress, int ringAddress,
        int primaryCollisionAddress, int secondaryCollisionAddress,
        int paletteAddress, int paletteSize, Bounds bounds, Position sonicStart, Position tailsStart,
        AnimationPatch animationPatch, List<ObjectSpawn> objects, List<RingSpawn> rings) {
    private static final int START_LOCATIONS = 0xC1D0;
    private static final int CNZ_1_OBJECTS = 0x1802A;
    private static final int CNZ_2_OBJECTS = 0x18492;
    private static final int CNZ_2P_ANIMATED_MAP = 0x406BE;

    public record Art(int patternAddress, int block16Address, int chunk128Address,
                      int primaryPlc, int secondaryPlc, int paletteId) { }
    public record Position(int x, int y) { }
    /** Native camera-origin bounds, not terrain dimensions. */
    public record Bounds(int minX, int maxX, int minY, int maxY) { }
    /** Metadata only: the fixed-length copy is not the shipped FixBugs=0 execution. */
    public record AnimationPatch(int address, int destinationByteOffset,
                                 int declaredWordCount, boolean overwritesLoopCounter) { }

    /**
     * Decoded base sources before LoadAnimatedBlocks. Art bytes remain in ROM
     * upload order: interlace cells consume 64-byte 8x16 pairs. Block words have
     * the native halved pattern index; they cannot be fed to an ordinary 8x8
     * renderer without an explicit interlace adapter.
     */
    public record BaseTerrain(byte[] patternBytes, List<Integer> nativeBlockWords,
                              byte[] chunkBytes, byte[] layoutBytes,
                              byte[] primaryCollisionBytes, byte[] secondaryCollisionBytes) {
        public BaseTerrain {
            patternBytes = patternBytes.clone();
            nativeBlockWords = List.copyOf(nativeBlockWords);
            chunkBytes = chunkBytes.clone();
            layoutBytes = layoutBytes.clone();
            primaryCollisionBytes = primaryCollisionBytes.clone();
            secondaryCollisionBytes = secondaryCollisionBytes.clone();
        }
        @Override public byte[] patternBytes() { return patternBytes.clone(); }
        @Override public byte[] chunkBytes() { return chunkBytes.clone(); }
        @Override public byte[] layoutBytes() { return layoutBytes.clone(); }
        @Override public byte[] primaryCollisionBytes() { return primaryCollisionBytes.clone(); }
        @Override public byte[] secondaryCollisionBytes() { return secondaryCollisionBytes.clone(); }
        public boolean animationPatchApplied() { return false; }
    }

    public Sonic2CompetitionLevelProfile {
        requireAct(zoneAct.zone(), zoneAct.act());
        objects = List.copyOf(objects);
        rings = List.copyOf(rings);
    }

    public static boolean supportsZone(int romZone) { return romZone == 0 || romZone == 11 || romZone == 12; }

    private static void requireAct(int zone, int act) {
        if (!supportsZone(zone) || act < 0 || act > 1)
            throw new IllegalArgumentException("Not a native competition zone/act: " + zone + "/" + act);
    }

    public static List<Sonic2CompetitionLevelProfile> loadAll(Rom rom) throws IOException {
        var profiles = new ArrayList<Sonic2CompetitionLevelProfile>(6);
        for (int zone : new int[]{0, 11, 12}) {
            for (int act = 0; act < 2; act++) profiles.add(load(rom, zone, act));
        }
        return List.copyOf(profiles);
    }

    public static Sonic2CompetitionLevelProfile load(Rom rom, int romZone, int act) throws IOException {
        requireAct(romZone, act);
        var reader = RomByteReader.fromRom(rom);
        int header = Sonic2Constants.LEVEL_DATA_DIR + romZone * 12;
        int pattern = reader.readU32BE(header);
        int block = reader.readU32BE(header + 4);
        int chunk = reader.readU32BE(header + 8);
        // LevelArtPointers (s2.asm:89222-89278): top bytes are PLC1, PLC2 and palette ID.
        var art = new Art(pattern & 0xFFFFFF, block & 0xFFFFFF, chunk & 0xFFFFFF,
                pattern >>> 24, block >>> 24, chunk >>> 24);
        int index = romZone * 2 + act;
        int layouts = reader.readU32BE(Sonic2Constants.LEVEL_LAYOUT_DIR_ADDR_LOC);
        int layout = reader.readPointer16(layouts, index);
        int objects = reader.readPointer16(Sonic2ObjectPlacement.OFF_OBJECTS_REV01, index);
        // ObjectsManager_Init (s2.asm:32943-32950) overrides only CNZ in native 2P mode.
        if (romZone == 12) objects = act == 0 ? CNZ_1_OBJECTS : CNZ_2_OBJECTS;
        int rings = reader.readPointer16(Sonic2RingPlacement.OFF_RINGS_REV01, index);
        int paletteEntry = Sonic2Constants.LEVEL_PALETTE_DIR + art.paletteId() * 8;
        int palette = reader.readU32BE(paletteEntry);
        int paletteSize = (reader.readU16BE(paletteEntry + 6) + 1) * 4;
        int bounds = Sonic2Constants.LEVEL_BOUNDARIES_ADDR + index * 8;
        var cameraBounds = new Bounds(reader.readU16BE(bounds), reader.readU16BE(bounds + 2),
                reader.readS16BE(bounds + 4), reader.readS16BE(bounds + 6));
        int start = START_LOCATIONS + index * 4;
        var sonicStart = new Position(reader.readU16BE(start), reader.readU16BE(start + 2));
        // InitPlayers (s2.asm:5193-5196) offsets Tails from Sonic's native centre.
        var tailsStart = new Position((sonicStart.x() - 0x20) & 0xFFFF, (sonicStart.y() + 4) & 0xFFFF);
        int animation = romZone == 12 ? CNZ_2P_ANIMATED_MAP
                : reader.readPointer16(Sonic2Constants.ANIM_PAT_MAPS_ADDR, romZone);
        int destination = reader.readU16BE(animation);
        var patch = new AnimationPatch(animation, destination,
                destination == 0 ? 0 : reader.readU16BE(animation + 2) + 1, destination != 0);
        return new Sonic2CompetitionLevelProfile(new ZoneAct(romZone, act), art, layout, objects, rings,
                reader.readU32BE(Sonic2Constants.COLLISION_LAYOUT_DIR_ADDR + romZone * 4),
                reader.readU32BE(Sonic2Constants.ALT_COLLISION_LAYOUT_DIR_ADDR + romZone * 4),
                palette, paletteSize, cameraBounds, sonicStart, tailsStart, patch,
                CommonPlacementParser.parseObjectRecords(reader, objects),
                new Sonic2RingPlacement(reader).load(new ZoneAct(romZone, act)));
    }

    public int romZone() { return zoneAct.zone(); }
    public int act() { return zoneAct.act(); }
    public boolean hasSeparateCompetitionObjects() { return zoneAct.zone() == 12; }
    public List<ObjectSpawn> signposts() { return objects.stream().filter(spawn -> spawn.objectId() == 0x0D).toList(); }
    public PlcDefinition primaryPlc(Rom rom) throws IOException { return Sonic2PlcLoader.parsePlc(rom, art.primaryPlc()); }
    public PlcDefinition secondaryPlc(Rom rom) throws IOException { return Sonic2PlcLoader.parsePlc(rom, art.secondaryPlc()); }

    /** loadZoneBlockMaps (s2.asm:20084-20099) preserves flags and halves only the pattern index. */
    public static int nativePatternWord(int word) { return (word & 0xF800) | ((word & 0x7FF) >>> 1); }

    public BaseTerrain decodeBaseTerrain(Rom rom) throws IOException {
        byte[] block = kosinski(rom, art.block16Address());
        var nativeBlocks = new ArrayList<Integer>(block.length / 2);
        for (int i = 0; i < block.length; i += 2) {
            nativeBlocks.add(nativePatternWord((block[i] & 0xFF) << 8 | block[i + 1] & 0xFF));
        }
        // FixBugs=0 LoadLevelBlocks_2P (s2.asm:86878-86898) overwrites its loop counter.
        // Do not apply the declared-length patch: that would silently implement the fixed branch.
        return new BaseTerrain(kosinski(rom, art.patternAddress()), nativeBlocks,
                kosinski(rom, art.chunk128Address()), kosinski(rom, layoutAddress),
                kosinski(rom, primaryCollisionAddress), kosinski(rom, secondaryCollisionAddress));
    }

    private static byte[] kosinski(Rom rom, int address) throws IOException {
        try (var channel = RomChannel.at(rom, address)) {
            return KosinskiReader.decompress(channel, false);
        }
    }
}
