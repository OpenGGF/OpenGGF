package com.openggf.game.soniccd.timeattack;

import com.openggf.data.compression.EnigmaReader;
import com.openggf.data.compression.NemesisReader;
import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.media.CdMmdHeader;
import com.openggf.game.soniccd.program.CdProgramData;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * ATTACK.MMD from the user's disc. The Time Attack program addresses its data by where it runs:
 * {@code RunMMD} copies the file body to the MMD origin ($FF2000, {@code $D000} bytes, IPX.asm RunMMD),
 * and the rest stays in Word RAM where SPCmd_LoadTimeAttack loaded the whole file ($200000,
 * SPX.asm:1342-1347). Labelled tables are located by {@code soniccd/timeattack-assets.tsv}
 * ({@code tools/soniccd/timeattack_catalogue.py}).
 */
public final class CdTimeAttackAssets {
    static final int WORD_RAM = CdMmdHeader.WORD_RAM_ADDRESS;
    private final byte[] file;
    private final int origin;
    private final int copied;
    private final Map<String, Integer> labels;

    public CdTimeAttackAssets(CdProgramData data) {
        this(data.file(CdNativeFile.TIMEATKMAIN));
    }

    CdTimeAttackAssets(byte[] file) {
        this.file = file;
        try {
            CdMmdHeader header = CdMmdHeader.parse(Arrays.copyOf(file, CdMmdHeader.HEADER_BYTES));
            origin = CdMmdHeader.nativeAddress(header.origin());
            copied = (header.sizeCounter() + 1) * 4;          // move.w mmdSize,d7; move.l ... dbf d7
        } catch (IOException failure) {
            throw new UncheckedIOException("ATTACK.MMD header", failure);
        }
        labels = load();
    }

    /** The program's load address ($FF2000). */
    int origin() { return origin; }

    /** The bytes {@code RunMMD} copies to the origin: the program image with its data and buffers. */
    byte[] image() { return Arrays.copyOfRange(file, CdMmdHeader.HEADER_BYTES, CdMmdHeader.HEADER_BYTES + copied); }

    /** The address of a catalogued label in the running image. */
    int label(String name) {
        Integer offset = labels.get(name);
        if (offset == null) throw new IllegalArgumentException("No Time Attack asset " + name);
        return offset - CdMmdHeader.HEADER_BYTES + origin;
    }

    /** File offset of a program address: the copied image, or Word RAM where the whole file was loaded. */
    int offset(int address) {
        if (address >= origin && address < origin + copied) return address - origin + CdMmdHeader.HEADER_BYTES;
        if (address >= WORD_RAM && address < WORD_RAM + file.length) return address - WORD_RAM;
        throw new IllegalArgumentException(String.format("$%X is not in ATTACK.MMD", address));
    }

    int word(int address) {
        int at = offset(address);
        return ((file[at] & 0xFF) << 8) | (file[at + 1] & 0xFF);
    }

    byte[] nemesis(int address) {
        try {
            return NemesisReader.decompress(Channels.newChannel(new ByteArrayInputStream(file, offset(address), file.length - offset(address))));
        } catch (IOException failure) {
            throw new UncheckedIOException(String.format("Nemesis art at $%X", address), failure);
        }
    }

    /** An Enigma map decoded with {@code base} added to each word, as words. */
    int[] enigma(int address, int base) {
        try {
            byte[] bytes = EnigmaReader.decompress(Channels.newChannel(new ByteArrayInputStream(file, offset(address),
                    file.length - offset(address))), base);
            int[] words = new int[bytes.length / 2];
            for (int i = 0; i < words.length; i++) words[i] = ((bytes[2 * i] & 0xFF) << 8) | (bytes[2 * i + 1] & 0xFF);
            return words;
        } catch (IOException failure) {
            throw new UncheckedIOException(String.format("Enigma map at $%X", address), failure);
        }
    }

    private static Map<String, Integer> load() {
        Map<String, Integer> out = new HashMap<>();
        try (InputStream in = CdTimeAttackAssets.class.getResourceAsStream("/soniccd/timeattack-assets.tsv")) {
            if (in == null) throw new IllegalStateException("soniccd/timeattack-assets.tsv is missing");
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            boolean header = true;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#") || line.isBlank()) continue;
                if (header) { header = false; continue; }
                String[] c = line.split("\t");
                out.put(c[1], Integer.parseInt(c[2], 16));
            }
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        return Map.copyOf(out);
    }
}
