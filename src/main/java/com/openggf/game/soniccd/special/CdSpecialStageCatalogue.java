package com.openggf.game.soniccd.special;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Offsets of the special stage's data inside {@code SPSS__.BIN} (sub-CPU program) and
 * {@code SPMM__.MMD} (main-CPU program), generated from the user's disc by
 * {@code tools/soniccd/special_stage_catalogue.py}. The resource holds offsets, lengths and the
 * owning label's citation; every byte is read from the disc at load time.
 */
public final class CdSpecialStageCatalogue {
    private static final String RESOURCE = "/soniccd/special-stage-catalogue.tsv";
    private static final List<String> COLUMNS = List.of("name", "file", "offset", "length", "citation");

    /** One located block. {@code file} is "-" for a scalar whose value is held in {@code offset}. */
    public record Block(String name, String file, int offset, int length, String citation) { }

    private static final Map<String, Block> BLOCKS = load();

    private CdSpecialStageCatalogue() { }

    public static Block block(String name) {
        return Objects.requireNonNull(BLOCKS.get(name), () -> "No special-stage catalogue entry " + name);
    }

    /** A scalar entry (stamp animation counts). */
    public static int scalar(String name) {
        Block block = block(name);
        if (!block.file().equals("-")) throw new IllegalStateException(name + " is a data block");
        return block.offset();
    }

    private static Map<String, Block> load() {
        Map<String, Block> blocks = new HashMap<>();
        try (InputStream stream = CdSpecialStageCatalogue.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) throw new IllegalStateException("Missing " + RESOURCE);
            var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.US_ASCII));
            boolean header = false;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] cells = line.split("\t");
                if (!header) {
                    if (!List.of(cells).equals(COLUMNS)) throw new IllegalStateException(RESOURCE + " has unexpected columns");
                    header = true;
                    continue;
                }
                if (cells.length != COLUMNS.size()) throw new IllegalStateException("Bad row in " + RESOURCE + ": " + line);
                Block block = new Block(cells[0], cells[1], Integer.decode(cells[2]), Integer.decode(cells[3]), cells[4]);
                if (blocks.put(block.name(), block) != null) throw new IllegalStateException("Duplicate " + block.name());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return Map.copyOf(blocks);
    }
}
