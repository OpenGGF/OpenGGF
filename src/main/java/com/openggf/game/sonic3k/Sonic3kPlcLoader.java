package com.openggf.game.sonic3k;

import com.openggf.data.Rom;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.resources.LevelResourcePlan;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.resources.LoadOp;
import com.openggf.level.resources.PlcParser;
import com.openggf.level.resources.PlcParser.PlcDefinition;
import com.openggf.level.resources.PlcParser.PlcEntry;
import com.openggf.level.Pattern;
import com.openggf.level.resources.KosinskiModuleQueue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Parses and applies Pattern Load Cues (PLCs) from the S3K ROM.
 *
 * <p>PLCs are Nemesis-compressed art entries that get loaded into VRAM at
 * specific tile indices. The ROM stores them in an offset table ({@code Offs_PLC})
 * followed by per-PLC data blocks. Each block has a count-1 header word
 * followed by 6-byte entries: 4-byte Nemesis ROM address + 2-byte VRAM
 * destination (tile index × 32).
 *
 * <p>Two ROM routines use PLCs:
 * <ul>
 *   <li>{@code Load_PLC} — appends entries to the decompression queue</li>
 *   <li>{@code Load_PLC_2} — clears the queue, then loads entries</li>
 * </ul>
 *
 * <p>This class is stateless; all methods take explicit parameters.
 * Format parsing is delegated to {@link PlcParser}; this class adds
 * S3K-specific range validation and level application logic.
 */
public final class Sonic3kPlcLoader {
    public static int[] fbzLevelPlcIds(int actIndex) {
        if (actIndex == 0) return new int[]{0x1A, 0x1B};
        if (actIndex == 1) return new int[]{0x1C, 0x1D};
        throw new IllegalArgumentException("FBZ act: " + actIndex);
    }

    /** One raw {@code plreq} entry: native VRAM tile destination plus Nemesis source. */
    public record RawPlcEntry(int tileIndex, int artAddress) { }

    /** One locked-on Queue_Kos_Module entry, with its byte-addressed VRAM destination. */
    public record KosmQueueEntry(int sourceAddress, int destinationVramBytes) { }

    /** Exact inline {@code PLCKosM_FBZ2Subboss}: cloud first, pillar second. */
    public static List<KosmQueueEntry> fbz2SubbossDefeatKosmEntries() {
        return List.of(
                new KosmQueueEntry(Sonic3kConstants.ART_KOSM_FBZ_CLOUD_ADDR,
                        Sonic3kConstants.ART_TILE_FBZ_CLOUD * 32),
                new KosmQueueEntry(Sonic3kConstants.ART_KOSM_FBZ_BOSS_PILLAR_ADDR,
                        Sonic3kConstants.ART_TILE_FBZ_BOSS_PILLAR * 32));
    }

    /** Exact inline PLCKosM_FBZEndBoss_Exit: door first, then hall. */
    public static List<KosmQueueEntry> fbzEndBossExitKosmEntries() {
        return List.of(
                new KosmQueueEntry(Sonic3kConstants.ART_KOSM_FBZ_EXIT_DOOR_ADDR,
                        Sonic3kConstants.ART_TILE_FBZ_EXIT_DOOR * 32),
                new KosmQueueEntry(Sonic3kConstants.ART_KOSM_FBZ_EXIT_HALL_ADDR,
                        Sonic3kConstants.ART_TILE_FBZ_EXIT_HALL * 32));
    }

    /** Exact body of locked-on {@code PLC_Monitors}; this does not prescribe sequencing. */
    public static List<RawPlcEntry> monitorPlcEntries() {
        return List.of(new RawPlcEntry(Sonic3kConstants.ARTTILE_MONITORS,
                Sonic3kConstants.ART_NEM_MONITORS_ADDR));
    }

    /** Exact body of locked-on {@code PLC_MonitorsSpikesSprings}; no event ordering implied. */
    public static List<RawPlcEntry> monitorSpikesSpringsPlcEntries() {
        return List.of(
                new RawPlcEntry(Sonic3kConstants.ARTTILE_MONITORS,
                        Sonic3kConstants.ART_NEM_MONITORS_ADDR),
                new RawPlcEntry(Sonic3kConstants.ARTTILE_SPIKES_SPRINGS,
                        Sonic3kConstants.ART_NEM_SPIKES_SPRINGS_ADDR));
    }

    /** Applies an inline {@code Load_PLC_Raw} body without inventing a numeric PLC id. */
    public static List<TileRange> applyRawToLevel(List<RawPlcEntry> entries,
                                                   Sonic3kLevel level, Rom rom) throws IOException {
        List<TileRange> modified = new ArrayList<>(entries.size());
        for (RawPlcEntry entry : entries) {
            byte[] data = PlcParser.decompressEntryRaw(rom,
                    new PlcEntry(entry.artAddress(), entry.tileIndex()));
            level.applyPatternOverlay(data, entry.tileIndex() * 32, false);
            modified.add(new TileRange(entry.tileIndex(), data.length / 32));
        }
        return modified;
    }

    /** Object-facing fault isolation: ROM-less state tests intentionally have no art source. */
    public record RawPlcApplyResult(int attemptedEntries, int appliedEntries, String failure) {
        public boolean complete() { return failure == null && attemptedEntries == appliedEntries; }
    }

    public static RawPlcApplyResult applyRawQuietly(List<RawPlcEntry> entries,
                                       com.openggf.level.objects.ObjectServices services) {
        List<RawPlcEntry> safeEntries = entries == null ? List.of() : entries;
        int attempted = safeEntries.size();
        if (services == null || !(services.currentLevel() instanceof Sonic3kLevel level)) {
            String failure = "S3K level pattern owner unavailable";
            LOG.warning("Raw PLC prefix not applied: " + failure);
            return new RawPlcApplyResult(attempted, 0, failure);
        }
        int applied = 0;
        List<TileRange> changed = new ArrayList<>();
        try {
            Rom rom = services.rom();
            if (rom == null && !safeEntries.isEmpty()) {
                String failure = "ROM owner unavailable";
                LOG.warning("Raw PLC prefix not applied: " + failure);
                return new RawPlcApplyResult(attempted, 0, failure);
            }
            KosinskiModuleQueue queue = services.kosinskiModuleQueue();
            if (queue != null) {
                bindRuntimePatternDmaTarget(queue, services);
                for (RawPlcEntry entry : safeEntries) {
                    byte[] data = PlcParser.decompressEntryRaw(rom,
                            new PlcEntry(entry.artAddress(), entry.tileIndex()));
                    if (!queue.applyImmediateDma(entry.tileIndex() * 32, data)) {
                        String failure = "DMA target unavailable after " + applied + " raw PLC entries";
                        LOG.warning("Raw PLC prefix only: " + failure);
                        return new RawPlcApplyResult(attempted, applied, failure);
                    }
                    applied++;
                }
            } else {
                for (RawPlcEntry entry : safeEntries) {
                    byte[] data = PlcParser.decompressEntryRaw(rom,
                            new PlcEntry(entry.artAddress(), entry.tileIndex()));
                    level.applyPatternOverlay(data, entry.tileIndex() * 32, false);
                    changed.add(new TileRange(entry.tileIndex(), data.length / 32));
                    applied++;
                }
                refreshAffectedRenderers(changed, services.levelManager());
            }
            return new RawPlcApplyResult(attempted, applied, null);
        } catch (IOException failure) {
            String message = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
            LOG.log(Level.WARNING, "Raw PLC prefix stopped after " + applied + " entries", failure);
            return new RawPlcApplyResult(attempted, applied, message);
        }
    }

    /**
     * Binds the gameplay queue to the active level pattern memory. The queue owns the sparse
     * rewind journal; this target performs each write and refreshes all overlapping consumers.
     */
    public static void bindRuntimePatternDmaTarget(KosinskiModuleQueue queue,
                                                    com.openggf.level.objects.ObjectServices services) {
        if (queue == null || services == null) return;
        queue.bindDmaTarget(new KosinskiModuleQueue.DmaTarget() {
            @Override public boolean isAvailable() {
                return services.currentLevel() instanceof Sonic3kLevel;
            }
            @Override public byte[] read(int destinationVramBytes, int length) {
                if (!(services.currentLevel() instanceof Sonic3kLevel current)) return new byte[length];
                return readPatternBytes(current, destinationVramBytes, length);
            }

            @Override public void apply(KosinskiModuleQueue.DmaChunk chunk) {
                if (!(services.currentLevel() instanceof Sonic3kLevel current)) return;
                byte[] data = chunk.data();
                current.applyPatternOverlay(data, chunk.destinationVramBytes(), false);
                refreshAffectedRenderers(List.of(new TileRange(
                        chunk.destinationVramBytes() / Pattern.PATTERN_SIZE_IN_ROM,
                        data.length / Pattern.PATTERN_SIZE_IN_ROM)), services.levelManager());
            }
        });
    }

    private static byte[] readPatternBytes(Sonic3kLevel level, int destinationVramBytes, int length) {
        int usableLength = Math.max(0, length / Pattern.PATTERN_SIZE_IN_ROM * Pattern.PATTERN_SIZE_IN_ROM);
        byte[] result = new byte[usableLength];
        int startTile = destinationVramBytes / Pattern.PATTERN_SIZE_IN_ROM;
        for (int tile = 0; tile < usableLength / Pattern.PATTERN_SIZE_IN_ROM; tile++) {
            int index = startTile + tile;
            if (index >= level.getPatternCount()) continue;
            Pattern pattern = level.getPattern(index);
            int offset = tile * Pattern.PATTERN_SIZE_IN_ROM;
            for (int y = 0; y < Pattern.PATTERN_HEIGHT; y++) {
                for (int x = 0; x < Pattern.PATTERN_WIDTH; x += 2) {
                    int high = pattern.getPixel(x, y) & 0x0F;
                    int low = pattern.getPixel(x + 1, y) & 0x0F;
                    result[offset++] = (byte) ((high << 4) | low);
                }
            }
        }
        return result;
    }

    public static int fbzEndBossPlcId() { return 0x6F; }
    private static final Logger LOG = Logger.getLogger(Sonic3kPlcLoader.class.getName());

    private Sonic3kPlcLoader() {}

    /** A tile range affected by a PLC application. */
    public record TileRange(int startTileIndex, int tileCount) {}

    /** Pre-decompressed PLC entry data ready for fast application. */
    public record PreDecompressedEntry(int tileIndex, byte[] data) {}

    /**
     * Parses a PLC definition from ROM with S3K-specific range validation.
     *
     * @param rom the ROM to read from
     * @param plcId the PLC ID (0x00–0x7B)
     * @return the parsed PLC definition, or a definition with empty entries if invalid
     */
    public static PlcDefinition parsePlc(Rom rom, int plcId) throws IOException {
        if (plcId < 0 || plcId >= Sonic3kConstants.OFFS_PLC_ENTRY_COUNT) {
            LOG.warning(String.format("PLC ID 0x%02X out of range (max 0x%02X)",
                    plcId, Sonic3kConstants.OFFS_PLC_ENTRY_COUNT - 1));
            return new PlcDefinition(plcId, List.of());
        }

        return PlcParser.parse(rom, Sonic3kConstants.OFFS_PLC_ADDR, plcId);
    }

    /**
     * Applies a PLC definition to a level by decompressing Nemesis entries
     * into the level's pattern buffer.
     *
     * @param definition the parsed PLC
     * @param level the target level
     * @return list of tile ranges that were modified
     */
    public static List<TileRange> applyToLevel(PlcDefinition definition, Sonic3kLevel level) throws IOException {
        return applyToLevel(definition, level, GameServices.rom().getRom());
    }

    /**
     * Applies a PLC definition to a level by decompressing Nemesis entries
     * into the level's pattern buffer, using an explicitly provided ROM.
     *
     * @param definition the parsed PLC
     * @param level the target level
     * @param rom the ROM to read from
     * @return list of tile ranges that were modified
     */
    public static List<TileRange> applyToLevel(PlcDefinition definition, Sonic3kLevel level, Rom rom) throws IOException {
        List<TileRange> modified = new ArrayList<>();

        for (PlcEntry entry : definition.entries()) {
            byte[] data = PlcParser.decompressEntryRaw(rom, entry);
            int tileCount = data.length / 32;
            level.applyPatternOverlay(data, entry.tileIndex() * 32, false);
            modified.add(new TileRange(entry.tileIndex(), tileCount));

            LOG.fine(String.format("PLC entry: Nemesis at 0x%06X -> tile 0x%03X (%d tiles decompressed)",
                    entry.romAddr(), entry.tileIndex(), tileCount));
        }

        LOG.info(String.format("Applied PLC 0x%02X: %d entries to level",
                definition.plcId(), definition.entries().size()));
        return modified;
    }

    /**
     * Pre-decompresses all entries in a PLC definition without applying them.
     * Use {@link #applyPreDecompressed} later for frame-hitch-free application.
     */
    public static List<PreDecompressedEntry> preDecompress(PlcDefinition definition) throws IOException {
        return preDecompress(definition, GameServices.rom().getRom());
    }

    /**
     * Pre-decompresses all entries in a PLC definition without applying them,
     * using an explicitly provided ROM.
     * Use {@link #applyPreDecompressed} later for frame-hitch-free application.
     *
     * @param definition the parsed PLC
     * @param rom the ROM to read from
     */
    public static List<PreDecompressedEntry> preDecompress(PlcDefinition definition, Rom rom) throws IOException {
        List<PreDecompressedEntry> result = new ArrayList<>(definition.entries().size());
        int totalBytes = 0;

        for (PlcEntry entry : definition.entries()) {
            byte[] data = PlcParser.decompressEntryRaw(rom, entry);
            result.add(new PreDecompressedEntry(entry.tileIndex(), data));
            totalBytes += data.length;
        }

        LOG.info(String.format("Pre-decompressed PLC 0x%02X: %d entries, total %d bytes",
                definition.plcId(), result.size(), totalBytes));
        return result;
    }

    /**
     * Applies previously pre-decompressed PLC data to a level.
     *
     * @return list of tile ranges that were modified
     */
    public static List<TileRange> applyPreDecompressed(List<PreDecompressedEntry> entries,
                                                        Sonic3kLevel level) {
        List<TileRange> modified = new ArrayList<>(entries.size());
        for (PreDecompressedEntry entry : entries) {
            int tileCount = entry.data().length / 32;
            level.applyPatternOverlay(entry.data(), entry.tileIndex() * 32, false);
            modified.add(new TileRange(entry.tileIndex(), tileCount));
        }
        return modified;
    }

    /**
     * Converts a PLC definition into {@link LoadOp} entries suitable for
     * {@link LevelResourcePlan.Builder#addPatternOp}.
     * Each entry becomes a Nemesis overlay at the appropriate byte offset.
     */
    public static List<LoadOp> toPatternOps(PlcDefinition definition) {
        return PlcParser.toPatternOps(definition);
    }

    /**
     * Refreshes GPU textures for all object renderers whose tile ranges
     * overlap the given modified ranges.
     *
     * @param modifiedRanges tile ranges that were modified by PLC application
     * @param levelManager the level manager with object render state
     */
    public static void refreshAffectedRenderers(List<TileRange> modifiedRanges,
                                                 LevelManager levelManager) {
        GraphicsManager gfx = GameServices.graphics();
        if (gfx == null || !gfx.isGlInitialized() || levelManager == null) {
            return;
        }
        ObjectRenderManager renderManager = levelManager.getObjectRenderManager();
        if (renderManager == null) {
            return;
        }

        Sonic3kObjectArtProvider artProvider = getArtProvider(levelManager);
        if (artProvider == null) {
            return;
        }

        List<String> affectedKeys = artProvider.getAffectedRendererKeys(modifiedRanges);
        int refreshed = 0;
        for (String key : affectedKeys) {
            PatternSpriteRenderer renderer = renderManager.getRenderer(key);
            if (renderer != null && renderer.isReady()) {
                ObjectSpriteSheet sheet = renderManager.getSheet(key);
                if (sheet != null) {
                    renderer.updatePatternRange(gfx, 0, sheet.getPatterns().length);
                    refreshed++;
                }
            }
        }

        if (refreshed > 0) {
            LOG.info(String.format("Refreshed %d renderer(s) for PLC tile ranges", refreshed));
        }
    }

    private static Sonic3kObjectArtProvider getArtProvider(LevelManager levelManager) {
        ObjectRenderManager renderManager = levelManager.getObjectRenderManager();
        if (renderManager == null) {
            return null;
        }
        var provider = renderManager.getArtProvider();
        if (provider instanceof Sonic3kObjectArtProvider s3kProvider) {
            return s3kProvider;
        }
        return null;
    }
}
