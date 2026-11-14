package com.openggf.game.soniccd.zones.ttz;

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
 * File offsets of the Tidal Tempest presentation tables inside R41A-R43D, generated from the user's
 * disc by {@code tools/soniccd/ttz_catalogue.py}. The resource holds offsets only.
 */
final class TtzCatalogue {
    private static final String RESOURCE = "/soniccd/ttz-presentation.tsv";
    private static final Map<CdStagePayload, List<Row>> ROWS = load();

    /**
     * {@code kind} is {@code cycle-std}, {@code cycle-upper} (a CycleColor script and its colours),
     * {@code wobble} (the 256-byte WobbleTable, {@code a}) or {@code anim} (script {@code a},
     * destination VRAM byte address {@code b}).
     */
    record Row(String kind, int slot, int a, int b) { }

    private TtzCatalogue() { }

    static List<Row> rows(CdStagePayload payload, String kind) {
        List<Row> out = new ArrayList<>();
        for (Row row : ROWS.getOrDefault(payload, List.of())) {
            if (row.kind().equals(kind)) out.add(row);
        }
        return out;
    }

    /** The one wobble row of a Tidal Tempest stage. */
    static int wobbleOffset(CdStagePayload payload) {
        List<Row> rows = rows(payload, "wobble");
        if (rows.size() != 1) throw new IllegalStateException(payload + " has no wobble table row");
        return rows.get(0).a();
    }

    private static Map<CdStagePayload, List<Row>> load() {
        Map<CdStagePayload, List<Row>> rows = new EnumMap<>(CdStagePayload.class);
        try (InputStream stream = TtzCatalogue.class.getResourceAsStream(RESOURCE)) {
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
