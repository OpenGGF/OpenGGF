package com.openggf.game.soniccd.program;

import com.openggf.game.soniccd.CdDiscDataSource;
import com.openggf.game.soniccd.CdNativeFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Data of the non-level programs read from the user's disc: title (TITLEM.MMD, TITLES.BIN),
 * stage select (STSEL_.MMD) and the IPX flow tables (IPX___.MMD). Where each blob lies in its
 * file comes from {@code soniccd/program-assets.tsv}, which {@code tools/soniccd/program_catalogue.py}
 * generates by locating the scddisasm source blobs in the disc files; it holds offsets only.
 */
public final class CdProgramData {
    private record Entry(CdNativeFile file, int offset, int length) { }

    private static final Map<String, Entry> ENTRIES = load();

    private final CdDiscDataSource disc;
    private final Map<CdNativeFile, byte[]> files = new EnumMap<>(CdNativeFile.class);

    public CdProgramData(CdDiscDataSource disc) {
        this.disc = Objects.requireNonNull(disc, "disc");
    }

    /** The named blob, exactly as long as the catalogue records it. */
    public byte[] bytes(String name) {
        Entry entry = entry(name);
        byte[] file = file(entry.file());
        if (entry.offset() + entry.length() > file.length) {
            throw new IllegalStateException(name + " lies outside " + entry.file().isoName());
        }
        return Arrays.copyOfRange(file, entry.offset(), entry.offset() + entry.length());
    }

    /** The named blob's offset and the rest of its file: compressed art whose length the decoder finds. */
    public byte[] from(String name) {
        Entry entry = entry(name);
        byte[] file = file(entry.file());
        return Arrays.copyOfRange(file, entry.offset(), file.length);
    }

    public int offset(String name) { return entry(name).offset(); }

    /** The whole program file. */
    public byte[] file(CdNativeFile file) {
        return files.computeIfAbsent(file, f -> {
            try {
                return disc.readFile(f);
            } catch (IOException failure) {
                throw new UncheckedIOException("Cannot read " + f.isoName() + " from the disc", failure);
            }
        });
    }

    private static Entry entry(String name) {
        Entry entry = ENTRIES.get(name);
        if (entry == null) throw new IllegalArgumentException("No program asset " + name);
        return entry;
    }

    private static Map<String, Entry> load() {
        Map<String, CdNativeFile> byIso = new HashMap<>();
        for (CdNativeFile file : CdNativeFile.values()) byIso.put(file.isoName(), file);
        Map<String, Entry> out = new HashMap<>();
        try (InputStream in = CdProgramData.class.getResourceAsStream("/soniccd/program-assets.tsv")) {
            if (in == null) throw new IllegalStateException("soniccd/program-assets.tsv is missing");
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            boolean header = true;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#") || line.isBlank()) continue;
                if (header) { header = false; continue; }
                String[] c = line.split("\t");
                CdNativeFile file = Objects.requireNonNull(byIso.get(c[0]), c[0]);
                out.put(c[1], new Entry(file, Integer.parseInt(c[2], 16), Integer.parseInt(c[3], 16)));
            }
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        return Map.copyOf(out);
    }
}
