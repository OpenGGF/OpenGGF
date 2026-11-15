package com.openggf.game.soniccd.fmv;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.program.CdProgramData;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Data of the video, ending and secret programs read from the user's disc. Where each blob lies in its file
 * comes from {@code soniccd/fmv-assets.tsv}, which {@code tools/soniccd/fmv_catalogue.py} generates from the
 * pinned scddisasm and the retail files; it holds offsets only.
 */
public final class CdFmvAssets {
    private record Entry(CdNativeFile file, int offset, int length) { }

    private static final Map<String, Entry> ENTRIES = load();

    private final CdProgramData data;

    public CdFmvAssets(CdProgramData data) {
        this.data = Objects.requireNonNull(data, "data");
    }

    public CdProgramData data() { return data; }

    /** The named blob, exactly as long as the catalogue records it. */
    public byte[] bytes(String name) {
        Entry entry = entry(name);
        return slice(entry.file(), entry.offset(), entry.length());
    }

    /** {@code length} bytes from the named blob's offset, for loops that read past their table. */
    public byte[] from(String name, int length) {
        Entry entry = entry(name);
        return slice(entry.file(), entry.offset(), length);
    }

    public int offset(String name) { return entry(name).offset(); }

    private byte[] slice(CdNativeFile file, int offset, int length) {
        byte[] whole = data.file(file);
        if (offset + length > whole.length) throw new IllegalStateException("Blob lies outside " + file.isoName());
        return Arrays.copyOfRange(whole, offset, offset + length);
    }

    private static Entry entry(String name) {
        Entry entry = ENTRIES.get(name);
        if (entry == null) throw new IllegalArgumentException("No video program asset " + name);
        return entry;
    }

    private static Map<String, Entry> load() {
        Map<String, CdNativeFile> byIso = new HashMap<>();
        for (CdNativeFile file : CdNativeFile.values()) byIso.put(file.isoName(), file);
        Map<String, Entry> out = new HashMap<>();
        try (InputStream in = CdFmvAssets.class.getResourceAsStream("/soniccd/fmv-assets.tsv")) {
            if (in == null) throw new IllegalStateException("soniccd/fmv-assets.tsv is missing");
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
