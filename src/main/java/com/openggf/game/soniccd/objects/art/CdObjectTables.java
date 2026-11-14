package com.openggf.game.soniccd.objects.art;

import com.openggf.game.soniccd.CdStagePayload;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One owner's object catalogue: file offsets of object tables inside each stage file, generated
 * from the user's disc by {@code tools/soniccd/object_catalogue.py} into
 * {@code /soniccd/object-catalogue-<owner>.tsv} (owners: common, ppz, ccz, ttz, qqz, wwz, ssz,
 * mmz). Each table is located through the instruction that consumes it (the owner's anchor
 * module cites every anchor). A missing table ({@code -}) is {@code -1}: that stage links
 * another routine or none. Columns are read by name, so each owner's accessor stands alone.
 */
public final class CdObjectTables {
    private final String owner;
    private final List<String> columns;
    private final Map<CdStagePayload, Map<String, Integer>> rows;

    private CdObjectTables(String owner, List<String> columns, Map<CdStagePayload, Map<String, Integer>> rows) {
        this.owner = owner;
        this.columns = columns;
        this.rows = rows;
    }

    /** Loads {@code /soniccd/object-catalogue-<owner>.tsv}; every stage payload must have a row. */
    public static CdObjectTables load(String owner) {
        String resource = "/soniccd/object-catalogue-" + owner + ".tsv";
        Map<CdStagePayload, Map<String, Integer>> rows = new EnumMap<>(CdStagePayload.class);
        List<String> header = null;
        try (InputStream stream = CdObjectTables.class.getResourceAsStream(resource)) {
            if (stream == null) throw new IllegalStateException("Missing " + resource);
            var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.US_ASCII));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] cells = line.split("\t", -1);
                if (header == null) {
                    if (!"stage".equals(cells[0])) throw new IllegalStateException(resource + " has no stage column");
                    header = List.of(cells).subList(1, cells.length);
                    continue;
                }
                if (cells.length != header.size() + 1) throw new IllegalStateException(resource + ": bad row " + cells[0]);
                Map<String, Integer> row = new LinkedHashMap<>();
                for (int i = 0; i < header.size(); i++) {
                    row.put(header.get(i), "-".equals(cells[i + 1]) ? -1 : Integer.parseInt(cells[i + 1], 16));
                }
                rows.put(CdStagePayload.valueOf(cells[0]), Map.copyOf(row));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (header == null || rows.size() != CdStagePayload.values().length) {
            throw new IllegalStateException(resource + " covers " + rows.size() + " stage payloads");
        }
        return new CdObjectTables(owner, header, Map.copyOf(rows));
    }

    public String owner() { return owner; }

    public List<String> columns() { return columns; }

    /** The stage's offset for {@code column}, or -1 where the stage has no such table. */
    public int offset(CdStagePayload payload, String column) {
        Map<String, Integer> row = Objects.requireNonNull(rows.get(Objects.requireNonNull(payload, "payload")),
                () -> "No " + owner + " object catalogue row for " + payload);
        Integer value = row.get(column);
        if (value == null) throw new IllegalArgumentException("No " + owner + " object catalogue column " + column);
        return value;
    }

    /** Every column of the stage's row. */
    public Map<String, Integer> row(CdStagePayload payload) {
        return rows.get(Objects.requireNonNull(payload, "payload"));
    }
}
