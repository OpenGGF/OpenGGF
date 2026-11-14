package com.openggf.game.soniccd.zones.qqz;

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
 * File offsets of the Quartz Quadrant palette-cycle tables inside R51A-R53D, generated from the user's
 * disc by {@code tools/soniccd/qqz_cycle_catalogue.py}. The resource holds offsets only.
 */
final class QqzCycleCatalogue {
    private static final String RESOURCE = "/soniccd/qqz-palette-cycles.tsv";
    private static final Map<CdStagePayload, List<Row>> ROWS = load();

    /** When a slot's cycle runs, as its routine gates it (scddisasm src/r5/palette_cycle_a-d.asm). */
    enum Gate { ALWAYS, NOBOSS, BG_EVEN, BG_ODD }

    /** {@code reverse} is the table the slot switches to while {@code r5_conveyor_reverse} is set, or -1. */
    record Row(int slot, Gate gate, int table, int reverse) { }

    private QqzCycleCatalogue() { }

    static List<Row> rows(CdStagePayload payload) {
        return ROWS.getOrDefault(payload, List.of());
    }

    private static Map<CdStagePayload, List<Row>> load() {
        Map<CdStagePayload, List<Row>> rows = new EnumMap<>(CdStagePayload.class);
        try (InputStream stream = QqzCycleCatalogue.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) throw new IllegalStateException("Missing " + RESOURCE);
            var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.US_ASCII));
            boolean header = false;
            for (String line; (line = reader.readLine()) != null; ) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] cells = line.split("\t");
                if (!header) {
                    if (!List.of(cells).equals(List.of("stage", "slot", "gate", "table", "reverse"))) {
                        throw new IllegalStateException(RESOURCE + " has unexpected columns");
                    }
                    header = true;
                    continue;
                }
                rows.computeIfAbsent(CdStagePayload.valueOf(cells[0]), k -> new ArrayList<>()).add(
                        new Row(Integer.parseInt(cells[1]), Gate.valueOf(cells[2].toUpperCase()),
                                Integer.parseInt(cells[3], 16),
                                cells[4].equals("-") ? -1 : Integer.parseInt(cells[4], 16)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        rows.replaceAll((k, v) -> List.copyOf(v));
        return Map.copyOf(rows);
    }
}
