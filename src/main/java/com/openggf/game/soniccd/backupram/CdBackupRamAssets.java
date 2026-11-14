package com.openggf.game.soniccd.backupram;

import com.openggf.game.soniccd.CdDiscDataSource;
import com.openggf.game.soniccd.CdNativeFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * The save layout and the initial save data from the user's disc. Where the initial tables lie in
 * BRAMINIT.MMD and the {@code rs} offsets of the save structure come from
 * {@code soniccd/backupram-assets.tsv}, which {@code tools/soniccd/backupram_catalogue.py} generates
 * from {@code Backup RAM/Initial Data.asm} and {@code _Include/Backup RAM.inc:36-55}.
 */
public final class CdBackupRamAssets {
    private static final Map<String, int[]> ENTRIES = load();

    private CdBackupRamAssets() { }

    /** A layout offset or size ({@code layout} rows of the catalogue). */
    public static int layout(String name) {
        int[] entry = ENTRIES.get("layout/" + name);
        if (entry == null) throw new IllegalArgumentException("No backup-RAM layout entry " + name);
        return entry[0];
    }

    /**
     * {@code InitSaveData} (Main CPU Functions.asm:223-244): {@code BuRAMTimeAttack} then {@code BuRAMMain},
     * as the 704 bytes Word RAM holds after it; the two trailing bytes of {@code BURAMMAINSZ} lie past the
     * {@code BURAMMAINSZ/4-1} long copy and past the 11-block file.
     */
    public static byte[] initialRecord(CdDiscDataSource disc) {
        byte[] file;
        try {
            file = disc.readFile(CdNativeFile.BURAMINIT);
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot read BRAMINIT.MMD from the disc", failure);
        }
        int[] times = ENTRIES.get("BRAMINIT.MMD;1/initialTimeAttack");
        int[] main = ENTRIES.get("BRAMINIT.MMD;1/initialMain");
        byte[] out = new byte[CdSaveRecord.FILE_BYTES];
        System.arraycopy(file, times[0], out, 0, times[1]);
        int copied = (layout("BURAMMAINSZ") / 4) * 4;                    // move.w #BURAMMAINSZ/4-1,d0; dbf
        System.arraycopy(Arrays.copyOfRange(file, main[0], main[0] + main[1]), 0, out, times[1], copied);
        return out;
    }

    private static Map<String, int[]> load() {
        Map<String, int[]> out = new HashMap<>();
        try (InputStream in = CdBackupRamAssets.class.getResourceAsStream("/soniccd/backupram-assets.tsv")) {
            if (in == null) throw new IllegalStateException("soniccd/backupram-assets.tsv is missing");
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            boolean header = true;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#") || line.isBlank()) continue;
                if (header) { header = false; continue; }
                String[] c = line.split("\t");
                String key = (c[0].equals("layout") ? "layout" : c[0]) + "/" + c[1];
                out.put(key, new int[] {Integer.parseInt(c[2], 16), Integer.parseInt(c[3], 16)});
            }
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        return Map.copyOf(out);
    }
}
