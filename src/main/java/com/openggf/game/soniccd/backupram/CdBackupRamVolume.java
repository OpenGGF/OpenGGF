/*
 * Translated from buram.c of buram, the Mega CD BackupRAM management tool,
 * https://github.com/superctr/buram commit 98c27a650be9c8f8e35271f44f5d127ba450edb6
 * (MIT licence; text below and in LICENSES/buram-MIT.txt).
 *
 * Copyright (c) 2022 Ian Karlsson
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 * Modified 2026-11 by the OpenGGF contributors: translated from C to Java
 * (buram_format, buram_read_repeat_code, buram_write_repeat_code,
 * buram_update_counters, buram_search, buram_read, buram_write, buram_delete,
 * and the directory addressing of buram_decode_buffer/buram_encode_buffer);
 * the BIOS format check and BRMVERIFY added; a read fails on any
 * uncorrectable block; no decoded-block cache.
 */
package com.openggf.game.soniccd.backupram;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * One backup-RAM device (the console's internal RAM or a RAM cartridge) with the firmware file system the
 * game observes: format, status, search, read, write, delete, directory and verify, as the BIOS
 * {@code _BURAM} calls {@code BRMINIT}..{@code BRMVERIFY} answer them (called through
 * {@code RunBuRAMCmd}, Backup RAM/Sub.asm:332-379). This is the device-owner of the three backup-RAM
 * owners: it holds the device's bytes exactly as the firmware lays them out, so a host file and a native
 * dump compare byte for byte, and nothing here knows the game's record layout.
 *
 * <p>Layout (firmware behaviour, not disc data; the file-system code is translated from the MIT-licensed
 * {@code superctr/buram} buram.c, see the header, and checked against the native fresh-boot dump): 64-byte blocks; block 0 is reserved; the last block is the footer (volume name,
 * four repeated free-block words at +$10, four repeated file-count words at +$18, the 32-byte format
 * signature at +$20); 32-byte directory entries grow down from the footer, two per protected block, and
 * file data grows up from block 1 in directory order. Formatting leaves {@code blocks - 3} free.
 */
public final class CdBackupRamVolume {
    public static final int BLOCK = 0x40;
    public static final int INTERNAL_BYTES = 0x2000;
    /** The footer the firmware's format writes (buram.c buram_format; native power-on dump $1FC0-$1FFF). */
    private static final byte[] VOLUME = "___________".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    private static final byte[] FOOTER_PAD = {0, 0, 0, 0, 0x40};
    private static final byte[] SIGNATURE = concat("SEGA_CD_ROM".getBytes(java.nio.charset.StandardCharsets.US_ASCII),
            new byte[] {0, 1, 0, 0, 0}, "RAM_CARTRIDGE___".getBytes(java.nio.charset.StandardCharsets.US_ASCII));

    private final byte[] bytes;
    /** Bumped by every change, so a rewind capture can reuse an unchanged image. */
    private int version;

    private CdBackupRamVolume(byte[] bytes) {
        if (bytes.length < 4 * BLOCK || bytes.length % BLOCK != 0) throw new IllegalArgumentException("Bad volume size");
        this.bytes = bytes;
    }

    /** A freshly formatted device of {@code size} bytes. */
    public static CdBackupRamVolume formatted(int size) {
        CdBackupRamVolume volume = new CdBackupRamVolume(new byte[size]);
        volume.format();
        return volume;
    }

    /** A device holding these bytes (a host file or a snapshot); formatted or not, as they are. */
    public static CdBackupRamVolume of(byte[] image) { return new CdBackupRamVolume(image.clone()); }

    public int size() { return bytes.length; }

    public byte[] image() { return bytes.clone(); }

    public int version() { return version; }

    public void restore(byte[] image) {
        if (image.length != bytes.length) throw new IllegalArgumentException("Volume size changed");
        System.arraycopy(image, 0, bytes, 0, bytes.length);
        version++;
    }

    private int top() { return bytes.length - BLOCK; }

    /** {@code BRMFORMAT}: an empty volume. */
    public void format() {
        version++;
        Arrays.fill(bytes, (byte) 0);
        System.arraycopy(VOLUME, 0, bytes, top(), VOLUME.length);
        System.arraycopy(FOOTER_PAD, 0, bytes, top() + 0x0B, FOOTER_PAD.length);
        repeat(top() + 0x10, bytes.length / BLOCK - 3);
        repeat(top() + 0x18, 0);
        System.arraycopy(SIGNATURE, 0, bytes, top() + 0x20, SIGNATURE.length);
    }

    /** {@code BRMINIT}'s formatted test: the format signature is present and both counters read. */
    public boolean isFormatted() {
        return Arrays.equals(bytes, top() + 0x20, top() + 0x40, SIGNATURE, 0, SIGNATURE.length)
                && repeated(top() + 0x10) >= 0 && repeated(top() + 0x18) >= 0;
    }

    /** {@code BRMSTAT} d0: free blocks. */
    public int freeBlocks() { return repeated(top() + 0x10); }

    /** {@code BRMSTAT} d1: files in the directory. */
    public int fileCount() { return repeated(top() + 0x18); }

    /** {@code BRMSERCH}: the entry named {@code name}, or empty (carry set). */
    public Optional<CdBackupRamFile> search(byte[] name) {
        for (CdBackupRamFile file : directory()) if (file.named(name)) return Optional.of(file);
        return Optional.empty();
    }

    /** Every directory entry in directory order ({@code BRMDIR} with the template {@code "***********"}). */
    public List<CdBackupRamFile> directory() {
        List<CdBackupRamFile> out = new ArrayList<>();
        int count = fileCount();
        for (int i = 0; i < count; i++) out.add(entry(i));
        return out;
    }

    /** {@code BRMREAD}: the file's data, {@code blocks * 64} bytes unprotected or {@code blocks * 32} protected; empty when missing or uncorrectable. */
    public Optional<byte[]> read(byte[] name) {
        Optional<CdBackupRamFile> found = search(name);
        if (found.isEmpty()) return Optional.empty();
        CdBackupRamFile file = found.get();
        if ((file.startBlock() + file.blocks()) * BLOCK > bytes.length) return Optional.empty();
        if (!file.protect()) {
            return Optional.of(Arrays.copyOfRange(bytes, file.startBlock() * BLOCK, (file.startBlock() + file.blocks()) * BLOCK));
        }
        byte[] out = new byte[file.blocks() * 32];
        for (int i = 0; i < file.blocks(); i++) {
            byte[] payload = CdBramEcc.decode(bytes, (file.startBlock() + i) * BLOCK);
            if (payload == null) return Optional.empty();
            System.arraycopy(payload, 0, out, i * 32, 32);
        }
        return Optional.of(out);
    }

    /**
     * {@code BRMWRITE}: replace or create a file of {@code blocks} blocks with {@code data} (64 bytes a block
     * unprotected, 32 protected; shorter data is zero-filled). False (carry set) when it does not fit.
     */
    public boolean write(byte[] name, int mode, int blocks, byte[] data) {
        Optional<CdBackupRamFile> old = search(name);
        if (old.isPresent()) {
            if (freeBlocks() + old.get().blocks() < blocks) return false;
            delete(name);
        } else if (freeBlocks() < blocks) {
            return false;
        }
        version++;
        int count = fileCount();
        int free = freeBlocks();
        int start = count == 0 ? 1 : entry(count - 1).startBlock() + entry(count - 1).blocks();
        CdBackupRamFile file = new CdBackupRamFile(name, mode, start, blocks);
        putEntry(count, file);
        int perBlock = file.protect() ? 32 : BLOCK;
        byte[] padded = Arrays.copyOf(data, blocks * perBlock);
        if (file.protect()) {
            for (int i = 0; i < blocks; i++) {
                byte[] payload = Arrays.copyOfRange(padded, i * 32, i * 32 + 32);
                System.arraycopy(CdBramEcc.encode(payload), 0, bytes, (start + i) * BLOCK, BLOCK);
            }
        } else {
            System.arraycopy(padded, 0, bytes, start * BLOCK, blocks * BLOCK);
        }
        // A new entry in an odd position opens another directory block (buram.c buram_update_counters).
        if ((count & 1) != 0) free--;
        repeat(top() + 0x10, free - blocks);
        repeat(top() + 0x18, count + 1);
        return true;
    }

    /** {@code BRMDEL}: remove the file and close the gap in data and directory. False when missing. */
    public boolean delete(byte[] name) {
        int count = fileCount();
        for (int i = 0; i < count; i++) {
            CdBackupRamFile file = entry(i);
            if (!file.named(name)) continue;
            version++;
            int shift = file.blocks();
            for (int j = i; j < count - 1; j++) {
                CdBackupRamFile next = entry(j + 1);
                CdBackupRamFile moved = new CdBackupRamFile(next.name(), next.mode(), next.startBlock() - shift, next.blocks());
                putEntry(j, moved);
                System.arraycopy(bytes, next.startBlock() * BLOCK, bytes, moved.startBlock() * BLOCK, next.blocks() * BLOCK);
            }
            int files = count - 1;
            int free = freeBlocks() + shift + ((files & 1) != 0 ? 1 : 0);
            repeat(top() + 0x10, free);
            repeat(top() + 0x18, files);
            return true;
        }
        return false;
    }

    /** {@code BRMVERIFY}: the stored file equals what a write of {@code data} would have stored. */
    public boolean verify(byte[] name, int blocks, byte[] data) {
        Optional<CdBackupRamFile> found = search(name);
        Optional<byte[]> stored = read(name);
        if (found.isEmpty() || stored.isEmpty() || found.get().blocks() != blocks) return false;
        return Arrays.equals(stored.get(), Arrays.copyOf(data, stored.get().length));
    }

    private int entryAddress(int index) { return top() - 0x20 - index * 0x20; }

    private CdBackupRamFile entry(int index) {
        int address = entryAddress(index);
        int base = address & -BLOCK;
        byte[] payload = CdBramEcc.decode(bytes, base);
        if (payload == null) payload = new byte[32];
        return CdBackupRamFile.fromDescriptor(payload, (address ^ base) >> 1);
    }

    /** Re-encode the directory block holding entry {@code index} with its descriptor replaced. */
    private void putEntry(int index, CdBackupRamFile file) {
        int address = entryAddress(index);
        int base = address & -BLOCK;
        byte[] payload = CdBramEcc.decode(bytes, base);
        if (payload == null) payload = new byte[32];
        System.arraycopy(file.descriptor(), 0, payload, (address ^ base) >> 1, 16);
        System.arraycopy(CdBramEcc.encode(payload), 0, bytes, base, BLOCK);
    }

    /** A value written four times; the reference reads it back only when all four copies agree (buram.c buram_read_repeat_code), else -1. */
    private int repeated(int at) {
        int[] words = new int[4];
        for (int i = 0; i < 4; i++) words[i] = ((bytes[at + 2 * i] & 0xFF) << 8) | (bytes[at + 2 * i + 1] & 0xFF);
        for (int i = 0; i < 2; i++) {
            int repeats = 0;
            for (int j = i + 1; j < 4; j++) if (words[i] == words[j]) repeats++;
            if (repeats > 2) return words[i];
        }
        return -1;
    }

    private void repeat(int at, int value) {
        for (int i = 0; i < 4; i++) {
            bytes[at + 2 * i] = (byte) (value >> 8);
            bytes[at + 2 * i + 1] = (byte) value;
        }
    }

    private static byte[] concat(byte[]... parts) {
        int n = 0;
        for (byte[] p : parts) n += p.length;
        byte[] out = new byte[n];
        int at = 0;
        for (byte[] p : parts) { System.arraycopy(p, 0, out, at, p.length); at += p.length; }
        return out;
    }
}
