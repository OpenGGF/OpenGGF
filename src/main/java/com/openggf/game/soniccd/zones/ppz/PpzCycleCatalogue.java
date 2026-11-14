package com.openggf.game.soniccd.zones.ppz;

import com.openggf.game.soniccd.CdStagePayload;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * File offsets of the Palmtree Panic palette-cycle tables inside R11A-D, R12A-D and R13C-D, generated from the
 * user's disc by {@code tools/soniccd/ppz_cycle_catalogue.py}. The resource holds offsets only.
 */
final class PpzCycleCatalogue {
    private static final String RESOURCE = "/soniccd/ppz-palette-cycles.tsv";
    private static final Map<CdStagePayload, List<Row>> ROWS = load();

    /** {@code kind} is {@code script} (a CycleColor script and its colours) or {@code proto} (two data sets). */
    record Row(String kind, int slot, int a, int b) { }

    private PpzCycleCatalogue() { }

    static List<Row> rows(CdStagePayload payload) {
        return ROWS.getOrDefault(payload, List.of());
    }

    private static Map<CdStagePayload, List<Row>> load() {
        Map<CdStagePayload, List<Row>> rows = new EnumMap<>(CdStagePayload.class);
        try (InputStream stream = PpzCycleCatalogue.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) throw new IllegalStateException("Missing " + RESOURCE);
            var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.US_ASCII));
            boolean header = false;
            for (String line; (line = reader.readLine()) != null; ) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] cells = line.split("\t");
                if (!header) {
                    if (!List.of(cells).equals(List.of("stage", "kind", "slot", "a", "b"))) {
                        throw new IllegalStateException(RESOURCE + " has unexpected columns");
                    }
                    header = true;
                    continue;
                }
                rows.computeIfAbsent(CdStagePayload.valueOf(cells[0]), k -> new ArrayList<>()).add(
                        new Row(cells[1], Integer.parseInt(cells[2]), Integer.parseInt(cells[3], 16),
                                Integer.parseInt(cells[4], 16)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        rows.replaceAll((k, v) -> List.copyOf(v));
        return Map.copyOf(rows);
    }
}
