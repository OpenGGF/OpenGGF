package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdStagePayload;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * File offsets of the level tables inside each of the 70 stage files, generated from
 * the user's disc by {@code tools/soniccd/stage_catalogue.py}. The generator locates
 * every table through the instruction that consumes it in that stage's copy of the
 * level engine; the resource holds offsets only. Regenerate it with that script.
 */
public final class CdStageCatalogue {
    private static final String RESOURCE = "/soniccd/stage-catalogue.tsv";
    private static final List<String> COLUMNS = List.of("stage", "levelDataIndex", "plcLists", "levelLayouts",
            "colAngleMap", "colHeightMap", "colWidthMap", "paletteTable", "objectLayouts", "camBounds",
            "levelStartLoc", "playerMappings", "playerAnimations", "playerDplc", "objColSizes", "playerArt", "levelCollision");

    /** Offsets into one stage file (the file is loaded at Word RAM $200000). */
    public record Entry(CdStagePayload payload, int levelDataIndex, int plcLists, int levelLayouts,
                        int colAngleMap, int colHeightMap, int colWidthMap, int paletteTable,
                        int objectLayouts, int camBounds, int levelStartLoc, int playerMappings,
                        int playerAnimations, int playerDplc, int objColSizes, int playerArt, int levelCollision) { }

    private static final Map<CdStagePayload, Entry> ENTRIES = load();

    private CdStageCatalogue() { }

    public static Entry entry(CdStagePayload payload) {
        return Objects.requireNonNull(ENTRIES.get(Objects.requireNonNull(payload, "payload")),
                () -> "No catalogue entry for " + payload);
    }

    private static Map<CdStagePayload, Entry> load() {
        Map<CdStagePayload, Entry> entries = new EnumMap<>(CdStagePayload.class);
        try (InputStream stream = CdStageCatalogue.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing " + RESOURCE);
            }
            var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.US_ASCII));
            String line;
            boolean header = false;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] cells = line.split("\t");
                if (!header) {
                    if (!List.of(cells).equals(COLUMNS)) {
                        throw new IllegalStateException(RESOURCE + " has unexpected columns");
                    }
                    header = true;
                    continue;
                }
                int[] v = new int[cells.length - 1];
                for (int i = 1; i < cells.length; i++) v[i - 1] = Integer.parseInt(cells[i], 16);
                CdStagePayload payload = CdStagePayload.valueOf(cells[0]);
                entries.put(payload, new Entry(payload, v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8],
                        v[9], v[10], v[11], v[12], v[13], v[14], v[15]));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (entries.size() != CdStagePayload.values().length) {
            throw new IllegalStateException(RESOURCE + " covers " + entries.size() + " of "
                    + CdStagePayload.values().length + " stage payloads");
        }
        return Map.copyOf(entries);
    }
}
