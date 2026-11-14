package com.openggf.game.soniccd.zones.wwz;

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
 * File offsets of the Wacky Workbench palette-cycle scripts, tile-animation tables and the perspective
 * sine table inside R61A-R63D, generated from the user's disc by
 * {@code tools/soniccd/wwz_presentation_catalogue.py}. The resource holds offsets only.
 */
final class WwzCatalogue {
    private static final String RESOURCE = "/soniccd/wwz-presentation.tsv";
    private static final Map<CdStagePayload, List<Row>> ROWS = load();

    /**
     * One located table. {@code kind}: {@code script} (a CycleColor script {@code a} and its colours {@code b}),
     * {@code bosscolors} (the boss palette colours at {@code a}), {@code anim_simple} / {@code anim_script} (a tile
     * animation table {@code a}, copy length {@code b} = d6 longwords minus one, VRAM byte destination {@code c}),
     * {@code blink} (the two blink durations at {@code a}) and {@code sine} (the sine table at {@code a}).
     */
    record Row(String kind, int slot, int a, int b, int c) { }

    private WwzCatalogue() { }

    static List<Row> rows(CdStagePayload payload) {
        return ROWS.getOrDefault(payload, List.of());
    }

    static List<Row> rows(CdStagePayload payload, String kind) {
        return rows(payload).stream().filter(row -> row.kind().equals(kind)).toList();
    }

    private static Map<CdStagePayload, List<Row>> load() {
        Map<CdStagePayload, List<Row>> rows = new EnumMap<>(CdStagePayload.class);
        try (InputStream stream = WwzCatalogue.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) throw new IllegalStateException("Missing " + RESOURCE);
            var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.US_ASCII));
            boolean header = false;
            for (String line; (line = reader.readLine()) != null; ) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] cells = line.split("\t");
                if (!header) {
                    if (!List.of(cells).equals(List.of("stage", "kind", "slot", "a", "b", "c"))) {
                        throw new IllegalStateException(RESOURCE + " has unexpected columns");
                    }
                    header = true;
                    continue;
                }
                rows.computeIfAbsent(CdStagePayload.valueOf(cells[0]), k -> new ArrayList<>()).add(
                        new Row(cells[1], Integer.parseInt(cells[2]), Integer.parseInt(cells[3], 16),
                                Integer.parseInt(cells[4], 16), Integer.parseInt(cells[5], 16)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        rows.replaceAll((k, v) -> List.copyOf(v));
        return Map.copyOf(rows);
    }
}
