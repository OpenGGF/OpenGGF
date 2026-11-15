package com.openggf.game.soniccd.level;

import com.openggf.data.compression.NemesisReader;
import com.openggf.game.GameServices;
import com.openggf.game.soniccd.CdPeriod;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.AbstractLevel;
import com.openggf.level.Block;
import com.openggf.level.Chunk;
import com.openggf.level.Map;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.SolidTile;
import com.openggf.level.objects.ObjectSpawn;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A Sonic CD stage decoded from its stage file into the shared level model.
 *
 * <p>The geometry formats are those of the Sonic 1 engine, except where noted:
 * <ul>
 *   <li>Level art is the Nemesis entry of the level PLC named by {@code LevelDataIndex}
 *       (LevelStart .NormalLoad, Level/Main.asm:156-164), as in Sonic 1.</li>
 *   <li>16x16 block mappings are Nemesis-compressed ({@code NemDecToRAM} into
 *       {@code blockBuffer}, Load Level Data.asm:18-22); Sonic 1 uses Enigma.</li>
 *   <li>256x256 chunks are uncompressed and read in place ("chunks are uncompressed,
 *       and are referenced directly", Load Level Data.asm:24-25); Sonic 1 uses Kosinski.
 *       Chunk words keep Sonic 1's {@code 0SSY X0II IIII IIII} layout
 *       (GetBlockData {@code andi.w #$3FF}, Block Draw.asm:227-229).</li>
 *   <li>Layouts, the collision index, height/width/angle maps and the palette table
 *       match Sonic 1 (LoadLevelLayout, Load Level Data.asm:57-99; LoadLevelCollision,
 *       Main.asm:490-507; LoadPalette, Palette Load.asm:14-26).</li>
 *   <li>Object entries are 8 bytes, not Sonic 1's 6: X, Y/flags, ID, subtype, time-zone
 *       mask, second subtype (SpawnObject, Object Spawner.asm:285-314).</li>
 * </ul>
 */
public final class CdLevel extends AbstractLevel implements com.openggf.game.internal.PlayerLoopChunks,
        com.openggf.level.objects.RingObjectPlacementMapping,
        com.openggf.game.internal.CollisionLayoutRowWindow,
        com.openggf.game.internal.CollisionLayoutColumnWindow,
        com.openggf.game.internal.CollisionChunkOriginReadsBlank,
        com.openggf.game.internal.VerticalScrollWrapAtTop {
    private static final int SONIC_PALETTE_ID = 3;   // LevelStart: moveq #3,d0; bsr LoadPalette (scddisasm/src/Level/Main.asm:240-243)
    private static final int CHUNK_BYTES = 0x200;     // GetBlockData: subq.b #1; ror.w #7 (Block Draw.asm:217-219)
    private static final int CHUNK_GRID = 16;
    private static final int MAX_CHUNK_ID = 0x7F;     // GetBlockData: andi.w #$7F (Block Draw.asm:218)
    private static final int LAYOUT_ROW_BYTES = 0x80; // LoadLevelLayout: lea $80(a3),a3 (Load Level Data.asm:95)
    private static final int NATIVE_DRAW_ROWS = 8;    // GetBlockData: andi.w #$380 (Block Draw.asm:207)
    private static final int SOLID_TILE_COUNT = 0x100;
    private static final int OBJECT_ENTRY_BYTES = 8;  // oeSize (Level/_Definitions.inc:100-108)
    /** ObjectIndex entry $10 - Ring (Palmtree Panic/Object Index (Act 1 Present).asm:23). */
    public static final int RING_OBJECT_ID = 0x10;

    /** One raw object layout entry, kept whole for object lanes (ObjectSpawn has no second subtype). */
    public record CdPlacement(int layoutIndex, int x, int yWord, int idByte, int subtype, int timeZones,
                              int subtype2) {
        /** CheckObjTimeZone: rol.b #3; andi.b #7; btst timeZone (Object Spawner.asm:240-253). */
        public boolean spawnsIn(int timeZone) {
            int mask = ((timeZones & 0xFF) >>> 5) & 7;
            return (mask & (1 << timeZone)) != 0;
        }
    }

    private final CdStagePayload payload;
    // --- common2 ---
    private com.openggf.game.soniccd.objects.common.CdLevelFlags flags;

    /** The common objects' level flags; goodFuture starts set for a Good Future stage. */
    public com.openggf.game.soniccd.objects.common.CdLevelFlags flags() {
        if (flags == null) flags = new com.openggf.game.soniccd.objects.common.CdLevelFlags(
                payload.stage().period() == com.openggf.game.soniccd.CdPeriod.GOOD_FUTURE);
        return flags;
    }
    // --- end common2 ---
    private final int startX;
    private final int cameraYCentre;
    private final int startY;
    private final List<CdPlacement> placements;
    private java.util.Map<ObjectSpawn, List<com.openggf.level.rings.RingSpawn>> ringPlacementMapping = java.util.Map.of();
    private boolean[] fgLoopFlags;
    private int fgMapWidth;
    private int fgMapHeight;
    private int bgMapWidth;

    CdLevel(CdStagePayload payload, CdStageImage image, CdStageCatalogue.Entry entry, int zoneIndex,
            boolean publishGraphics) throws IOException {
        super(zoneIndex);
        this.payload = payload;
        int index = entry.levelDataIndex();
        GraphicsManager graphics = publishGraphics ? GameServices.graphics() : null;

        loadPalettes(image, entry.paletteTable(), image.u8(index + 15), graphics);
        loadPatterns(image, entry.plcLists(), image.u8(index), image.u32(index), graphics);
        loadSolidTiles(image, entry);
        loadBlockMappings(image, image.offsetOf(image.u32(index + 4)), entry.levelCollision());
        loadChunks(image, image.offsetOf(image.u32(index + 8)));
        loadLayouts(image, entry.levelLayouts());

        // CamBounds: unused word, left, right, top, bottom, Y centre (LevelSizeLoad,
        // Scroll (Act 1 Present).asm:19-33).
        minX = (short) image.u16(entry.camBounds() + 2);
        maxX = (short) image.u16(entry.camBounds() + 4);
        minY = (short) image.u16(entry.camBounds() + 6);
        maxY = (short) image.u16(entry.camBounds() + 8);
        cameraYCentre = image.u16(entry.camBounds() + 10);
        // LevelStartLoc: X, Y words (.DefaultStart, Scroll (Act 1 Present).asm:77-83).
        startX = image.u16(entry.levelStartLoc());
        startY = image.u16(entry.levelStartLoc() + 2);

        placements = readPlacements(image, entry.objectLayouts());
        int timeZone = nativeTimeZone(payload.stage().period());
        List<ObjectSpawn> spawns = new ArrayList<>();
        List<com.openggf.level.rings.RingSpawn> ringSpawns = new ArrayList<>();
        java.util.Map<ObjectSpawn, List<com.openggf.level.rings.RingSpawn>> ringGroups = new java.util.HashMap<>();
        for (CdPlacement placement : placements) {
            if (!placement.spawnsIn(timeZone)) continue;
            ObjectSpawn spawn = toSpawn(placement);
            spawns.add(spawn);
            if (spawn.objectId() == RING_OBJECT_ID) {
                List<com.openggf.level.rings.RingSpawn> group = expandRings(image, payload, spawn, ringSpawns.size());
                ringSpawns.addAll(group);
                ringGroups.put(spawn, group);
            }
        }
        objects = List.copyOf(spawns);
        rings = List.copyOf(ringSpawns);
        ringPlacementMapping = java.util.Collections.unmodifiableMap(ringGroups);
        ringSpriteSheet = com.openggf.game.soniccd.objects.art.CdObjectArtProvider.ringSheet(image, payload,
                com.openggf.game.soniccd.objects.art.CdObjectArtProvider.loadVram(image, payload));
    }

    /**
     * ObjRing_Init's group: {@code andi.w #7} with 7 clamped to 6 gives count-1, and
     * {@code ObjRing_Deltas[subtype >> 4]} the signed step between rings
     * (Ring/Main.asm:20-35,68-85). The shared ring owner keeps one entry per ring.
     */
    static List<com.openggf.level.rings.RingSpawn> expandRings(CdStageImage image, CdStagePayload payload,
                                                               ObjectSpawn spawn, int firstPlacementId) {
        int countMinusOne = spawn.subtype() & 7;
        if (countMinusOne == 7) countMinusOne = 6;
        int[] delta = ringDelta(image, payload, spawn.subtype());
        List<com.openggf.level.rings.RingSpawn> group = new ArrayList<>(countMinusOne + 1);
        for (int i = 0; i <= countMinusOne; i++) {
            group.add(new com.openggf.level.rings.RingSpawn(spawn.x() + i * delta[0], spawn.y() + i * delta[1],
                    firstPlacementId + i));
        }
        return List.copyOf(group);
    }

    /** ObjRing_Deltas (Ring/Main.asm:20-35): signed X/Y bytes indexed by the subtype's high nibble. */
    public static int[] ringDelta(CdStageImage image, CdStagePayload payload, int subtype) {
        int at = com.openggf.game.soniccd.objects.art.CdObjectCatalogue.entry(payload).ringDeltas()
                + ((subtype >> 4) & 0xF) * 2;
        return new int[] {(byte) image.u8(at), (byte) image.u8(at + 1)};
    }

    public CdStagePayload payload() { return payload; }

    /**
     * GetLevelBlock's row index: {@code lsr.w #1 / andi.w #$780} over the 16-row,
     * $800-byte layout RAM, narrowed to {@code andi.w #$380} in Tidal Tempest
     * (Collision Floor.asm:467-473; levelLayout, Level/_Definitions.inc:120). Rows past
     * the stage's layout are the zeroes LoadLevelLayout cleared (Palmtree Panic/Load Level Data.asm:57-63).
     */
    @Override
    public int collisionLayoutYMask() {
        return payload.stage().zone() == com.openggf.game.soniccd.CdZone.TIDAL_TEMPEST ? 0x07FF : 0x0FFF;
    }

    /**
     * GetLevelBlock's column: {@code lsr.w #8 / andi.w #$7F} into the layout RAM row, whose
     * first $40 bytes are the foreground and next $40 the background (LoadLevelLayout loads
     * the background at {@code levelLayout+$40}) after the whole RAM was cleared
     * (Collision Floor.asm:475-478; Palmtree Panic/Load Level Data (Act 3).asm:52-66). Sonic 1's
     * FindNearestTile reads the same row (s1disasm sub FindNearestTile &amp; FindFloor &amp;
     * FindWall.asm:18-24), so past the foreground's last column a probe reads chunk 0, blank,
     * not the first column again.
     */
    @Override
    public int collisionLayoutColumn(int x) {
        int column = (x >> 8) & 0x7F;
        if (column < fgMapWidth) {
            return column;
        }
        int bgColumn = column - LAYOUT_ROW_BYTES / 2;
        return bgColumn >= 0 && bgColumn < bgMapWidth ? bgColumn | BACKGROUND_HALF : -1;
    }

    /**
     * Tidal Tempest acts 2 and 3: {@code ScrollFgY} wraps by $800 when the scroll reaches row 0 at or above
     * the top boundary ({@code cmpi.w #0,d1; bgt; addi.w #$800}, r4/scroll_2a.asm:521-530,
     * r4/scroll_3c.asm:497-506), the stage being the 8-row window its collision reads. Every other stage's
     * {@code ScrollCamY} wraps only at -$100 (Palmtree Panic/Scroll (Act 1 Present).asm:716-725).
     */
    @Override
    public int verticalScrollWrapRange() {
        return payload.stage().zone() == com.openggf.game.soniccd.CdZone.TIDAL_TEMPEST
                && payload.stage().actNumber() >= 2 ? 0x800 : 0;
    }

    /** {@code camYCenter} as {@code LevelSizeLoad} writes it from the stage's CamBounds (Scroll (Act 1 Present).asm:32-33). */
    public int cameraYCentre() { return cameraYCentre; }
    public int startX() { return startX; }
    public int startY() { return startY; }
    /**
     * The entry's secondary subtype ({@code oeSubtype2}, copied to {@code oSubtype2} by
     * SpawnObject, Object Spawner.asm:312-314), which the shared spawn record does not carry;
     * 0 for a spawn that is not a layout entry.
     */
    public int subtype2(ObjectSpawn spawn) {
        int index = spawn.layoutIndex();
        return index >= 0 && index < placements.size() && placements.get(index).x() == spawn.x()
                ? placements.get(index).subtype2() : 0;
    }

    /** Every entry of the stage's object layout, before the time-zone filter. */
    public List<CdPlacement> placements() { return placements; }

    /** timeZone: 0 past, 1 present, 2 future (CheckObjTimeZone's bit index). */
    public static int nativeTimeZone(CdPeriod period) {
        return switch (period) {
            case PAST -> 0;
            case PRESENT -> 1;
            case GOOD_FUTURE, BAD_FUTURE -> 2;
        };
    }

    @Override public int getBlockPixelSize() { return 256; }
    @Override public int getChunksPerBlockSide() { return CHUNK_GRID; }

    @Override
    public java.util.Map<ObjectSpawn, List<com.openggf.level.rings.RingSpawn>> ringObjectPlacementMapping() {
        return ringPlacementMapping;
    }

    /** Layout bit 7 marks a loop chunk (GetLevelBlock: bmi.s .LoopChunk, Collision Floor.asm:483-485). */
    public boolean hasLoopFlag(int mapX, int mapY) {
        if (fgLoopFlags == null || mapX < 0 || mapX >= fgMapWidth || mapY < 0 || mapY >= fgMapHeight) {
            return false;
        }
        return fgLoopFlags[mapY * fgMapWidth + mapX];
    }

    /**
     * The zone-independent loop swap: chunk+1, with $29 becoming $51
     * (GetLevelBlock .NotMMZ2, Collision Floor.asm:643-650), identical to Sonic 1's
     * FindNearestTile. Wacky Workbench, Stardust Speedway and Metallic Madness replace
     * it with their own remaps (Collision Floor.asm:519-641); their zone lanes own those.
     */
    @Override
    public int resolveCollisionBlockIndex(int blockIndex, int mapX, int mapY) {
        if (!hasLoopFlag(mapX, mapY)) {
            return blockIndex;
        }
        int resolved = (blockIndex & 0x7F) + 1;
        return resolved == 0x29 ? 0x51 : resolved;
    }

    /** A zone's {@code GetLevelBlock} loop-chunk branch for the player: the chunk ID (bit 7 clear) to read. */
    @FunctionalInterface
    public interface PlayerLoopChunkRule {
        int chunk(int loopChunk, boolean onLoopPath);

        /**
         * The player's probe read a plain chunk (not blank, bit 7 clear): the zone's writes on that path of
         * {@code GetLevelBlock} (Collision Floor.asm:487-499). None by default.
         */
        default void plainChunk() { }

        /** {@code .NotMMZ2}: the zone-independent swap on the loop path (Collision Floor.asm:643-650). */
        PlayerLoopChunkRule STANDARD = (chunk, path) -> {
            if (!path) return chunk;
            int resolved = chunk + 1;
            return resolved == 0x29 ? 0x51 : resolved;
        };
    }

    private volatile PlayerLoopChunkRule playerLoopChunkRule = PlayerLoopChunkRule.STANDARD;

    /** The loaded zone's loop-chunk rule, installed with the zone's player profile. */
    public void setPlayerLoopChunkRule(PlayerLoopChunkRule rule) {
        playerLoopChunkRule = rule == null ? PlayerLoopChunkRule.STANDARD : rule;
    }

    /**
     * {@code GetLevelBlock} for the player: a plain chunk reads as itself; a loop chunk
     * ({@code bmi.s .LoopChunk}, Collision Floor.asm:483-485) takes the zone's rule, which may
     * read the stage {@code layer} as well as the player's loop path.
     */
    @Override
    public int playerCollisionBlockIndex(int rawBlockIndex, int mapX, int mapY, boolean onLoopPath) {
        if (!hasLoopFlag(mapX, mapY)) {
            if ((rawBlockIndex & 0x7F) != 0) playerLoopChunkRule.plainChunk();   // beq.s .Blank
            return rawBlockIndex;
        }
        return playerLoopChunkRule.chunk(rawBlockIndex & 0x7F, onLoopPath);
    }

    private void loadPalettes(CdStageImage image, int table, int levelPaletteId, GraphicsManager graphics) {
        palettes = new Palette[PALETTE_COUNT];
        for (int i = 0; i < PALETTE_COUNT; i++) palettes[i] = new Palette();
        // Entry 0's destination is the palette buffer itself ("dc.w palette",
        // Palette Data (Act 1 Present).asm:7-9); later entries are relative to it.
        int paletteBase = image.u16(table + 4);
        applyPaletteEntry(image, table, paletteBase, SONIC_PALETTE_ID);
        applyPaletteEntry(image, table, paletteBase, levelPaletteId);
        if (graphics != null && graphics.isGlInitialized()) {
            for (int i = 0; i < palettes.length; i++) graphics.cachePaletteTexture(palettes[i], i);
        }
    }

    private void applyPaletteEntry(CdStageImage image, int table, int paletteBase, int id) {
        int entry = table + id * 8;
        int source = image.offsetOf(image.u32(entry));
        int destination = image.u16(entry + 4) - paletteBase;
        int bytes = (image.u16(entry + 6) + 1) * 4;     // dbf over longwords
        byte[] data = image.slice(source, bytes);
        for (int i = 0; i + 1 < data.length; i += Palette.BYTES_PER_COLOR) {
            int at = destination + i;
            int line = at / Palette.PALETTE_SIZE_IN_ROM;
            if (at < 0 || line >= PALETTE_COUNT) continue;
            int color = (at % Palette.PALETTE_SIZE_IN_ROM) / Palette.BYTES_PER_COLOR;
            palettes[line].getColor(color).fromSegaFormat(data, i);
        }
    }

    /** Decodes every Nemesis entry of the level PLC: count-1 word, then art.l + VRAM.w entries. */
    private void loadPatterns(CdStageImage image, int plcLists, int plcId, int artPointer,
                              GraphicsManager graphics) throws IOException {
        List<int[]> cues = new ArrayList<>();
        if (plcId != 0) {
            int plc = plcLists + image.u16(plcLists + plcId * 2);
            int count = image.u16(plc) + 1;
            for (int i = 0; i < count; i++) {
                int at = plc + 2 + i * 6;
                cues.add(new int[] {image.offsetOf(image.u32(at)), image.u16(at + 4) / Pattern.PATTERN_SIZE_IN_ROM});
            }
        } else {
            // No level PLC: the art pointer in LevelDataIndex is the only level art.
            cues.add(new int[] {image.offsetOf(artPointer), 0});
        }
        List<byte[]> decoded = new ArrayList<>();
        int end = 0;
        for (int[] cue : cues) {
            byte[] data = nemesis(image, cue[0]);
            decoded.add(data);
            end = Math.max(end, cue[1] + data.length / Pattern.PATTERN_SIZE_IN_ROM);
        }
        patternCount = end;
        patterns = new Pattern[patternCount];
        for (int c = 0; c < cues.size(); c++) {
            byte[] data = decoded.get(c);
            for (int t = 0; t < data.length / Pattern.PATTERN_SIZE_IN_ROM; t++) {
                Pattern pattern = new Pattern();
                pattern.fromSegaFormat(Arrays.copyOfRange(data, t * Pattern.PATTERN_SIZE_IN_ROM,
                        (t + 1) * Pattern.PATTERN_SIZE_IN_ROM));
                patterns[cues.get(c)[1] + t] = pattern;
            }
        }
        for (int i = 0; i < patternCount; i++) {
            if (patterns[i] == null) patterns[i] = new Pattern();
            if (graphics != null && graphics.isGlInitialized()) graphics.cachePatternTexture(patterns[i], i);
        }
    }

    private void loadSolidTiles(CdStageImage image, CdStageCatalogue.Entry entry) {
        solidTileCount = SOLID_TILE_COUNT;
        solidTiles = new SolidTile[solidTileCount];
        for (int i = 0; i < solidTileCount; i++) {
            byte[] heights = image.slice(entry.colHeightMap() + i * SolidTile.TILE_SIZE_IN_ROM, SolidTile.TILE_SIZE_IN_ROM);
            byte[] widths = image.slice(entry.colWidthMap() + i * SolidTile.TILE_SIZE_IN_ROM, SolidTile.TILE_SIZE_IN_ROM);
            solidTiles[i] = new SolidTile(i, heights, widths, (byte) image.u8(entry.colAngleMap() + i));
        }
    }

    /** 16x16 mappings ("blocks" natively, {@link Chunk} in the shared model) and their collision IDs. */
    private void loadBlockMappings(CdStageImage image, int blocks, int collision) throws IOException {
        byte[] data = nemesis(image, blocks);
        chunkCount = data.length / Chunk.CHUNK_SIZE_IN_ROM;
        chunks = new Chunk[chunkCount];
        for (int i = 0; i < chunkCount; i++) {
            int solid = collision + i < image.size() ? image.u8(collision + i) : 0;
            chunks[i] = new Chunk();
            chunks[i].fromSegaFormat(Arrays.copyOfRange(data, i * Chunk.CHUNK_SIZE_IN_ROM,
                    (i + 1) * Chunk.CHUNK_SIZE_IN_ROM), solid, solid);
        }
    }

    /**
     * 256x256 chunks ({@link Block} in the shared model). Layout ID n reads
     * {@code LevelChunks + (n-1)*$200} for any n up to $7F, so every addressable chunk
     * that lies inside the stage file is decoded; ID 0 is the empty chunk.
     */
    private void loadChunks(CdStageImage image, int chunksOffset) {
        int available = Math.min(MAX_CHUNK_ID, (image.size() - chunksOffset) / CHUNK_BYTES);
        blockCount = available + 1;
        blocks = new Block[blockCount];
        blocks[0] = new Block(CHUNK_GRID);
        for (int i = 0; i < available; i++) {
            blocks[i + 1] = new Block(CHUNK_GRID);
            blocks[i + 1].fromSegaFormat(sonic1ChunkWords(image.slice(chunksOffset + i * CHUNK_BYTES, CHUNK_BYTES)),
                    CHUNK_GRID * CHUNK_GRID);
        }
    }

    /** {@code 0SSY X0II IIII IIII} to the shared {@code SSTT YXII IIII IIII}; one solidity for both paths. */
    private static byte[] sonic1ChunkWords(byte[] raw) {
        byte[] result = new byte[raw.length];
        for (int i = 0; i < raw.length; i += 2) {
            int word = ((raw[i] & 0xFF) << 8) | (raw[i + 1] & 0xFF);
            int solidity = (word >> 13) & 3;
            int converted = (word & 0x3FF) | ((word & 0x0800) != 0 ? 0x0400 : 0)
                    | ((word & 0x1000) != 0 ? 0x0800 : 0) | (solidity << 12) | (solidity << 14);
            result[i] = (byte) (converted >> 8);
            result[i + 1] = (byte) converted;
        }
        return result;
    }

    /** LevelLayouts: word offsets to FG (+0) and BG (+2), each (width-1, height-1, rows). */
    private void loadLayouts(CdStageImage image, int layouts) {
        int fg = layouts + image.u16(layouts);
        int bg = layouts + image.u16(layouts + 2);
        int fgWidth = image.u8(fg) + 1, fgHeight = image.u8(fg + 1) + 1;
        int bgWidth = image.u8(bg) + 1, bgHeight = image.u8(bg + 1) + 1;
        if (fgWidth > LAYOUT_ROW_BYTES / 2 || bgWidth > LAYOUT_ROW_BYTES / 2) {
            throw new IllegalStateException(payload + " layout exceeds its $40-byte half row");
        }
        byte[] fgData = image.slice(fg + 2, fgWidth * fgHeight);
        byte[] bgData = image.slice(bg + 2, bgWidth * bgHeight);
        fgMapWidth = fgWidth;
        fgMapHeight = fgHeight;
        bgMapWidth = bgWidth;
        fgLoopFlags = new boolean[fgWidth * fgHeight];
        int width = Math.max(fgWidth, bgWidth);
        int height = Math.max(NATIVE_DRAW_ROWS, Math.max(fgHeight, bgHeight));
        byte[] buffer = new byte[2 * width * height];
        for (int y = 0; y < fgHeight; y++) {
            for (int x = 0; x < fgWidth; x++) {
                int value = fgData[y * fgWidth + x] & 0xFF;
                fgLoopFlags[y * fgWidth + x] = (value & 0x80) != 0;
                buffer[y * width * 2 + x] = (byte) (value & 0x7F);
            }
        }
        // LoadLevelLayout clears layout RAM and copies only the declared rows; the
        // background is repeated horizontally for the shared wide-plane renderer, as Sonic 1's is.
        for (int y = 0; y < bgHeight; y++) {
            for (int x = 0; x < width; x++) {
                buffer[y * width * 2 + width + x] = (byte) (bgData[y * bgWidth + x % bgWidth] & 0x7F);
            }
        }
        map = new Map(2, width, height, buffer);
    }

    /** ObjectLayouts: word offset to the layout; entries until an X of $FFFF. */
    private static List<CdPlacement> readPlacements(CdStageImage image, int objectLayouts) {
        int at = objectLayouts + image.u16(objectLayouts);
        List<CdPlacement> result = new ArrayList<>();
        for (int index = 0; at + OBJECT_ENTRY_BYTES <= image.size(); index++, at += OBJECT_ENTRY_BYTES) {
            int x = image.u16(at);
            if (x == 0xFFFF) break;
            result.add(new CdPlacement(index, x, image.u16(at + 2), image.u8(at + 4), image.u8(at + 5),
                    image.u8(at + 6), image.u8(at + 7)));
        }
        return List.copyOf(result);
    }

    /** SpawnObject: Y & $FFF, flags = rol.w #2 & 3, ID bit 7 = saved flags (Object Spawner.asm:285-303). */
    private static ObjectSpawn toSpawn(CdPlacement p) {
        int y = p.yWord() & 0x0FFF;
        int flags = (p.yWord() >>> 14) & 3;
        return new ObjectSpawn(p.x(), y, p.idByte() & 0x7F, p.subtype(), flags,
                (p.idByte() & 0x80) != 0, p.yWord(), p.layoutIndex());
    }

    private static byte[] nemesis(CdStageImage image, int offset) throws IOException {
        return NemesisReader.decompress(Channels.newChannel(new ByteArrayInputStream(image.tail(offset))));
    }
}
