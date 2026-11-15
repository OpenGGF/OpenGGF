/*
 * Follows the CHD v5 hunk map and CD codec framing of MAME src/lib/util/chd.cpp and chdcodec.cpp,
 * https://github.com/mamedev/mame (decompress_v5_map, chd_cd_decompressor), rewritten in Java
 * (BSD-3-Clause; text below and in LICENSES/MAME-BSD-3-Clause.txt).
 *
 * Copyright-holders: Aaron Giles
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * Modified 2026-11 by the OpenGGF contributors: translated from C++ to Java
 * (rf5c68_device::rf5c68_w, rf5c68_r, rf5c68_mem_r, rf5c68_mem_w, sound_stream_update and the
 * rf5c164_map address decode; the pcm_channel fields become per-channel arrays). The stream,
 * memory-space, save-state and sample-end-callback plumbing is not translated. Three behaviours
 * differ from upstream; see the class comment. Added by OpenGGF, not in upstream: stepSplit, which
 * also reports the part of the channel sum from a caller's set of channels, for the host's mix.
 */
package com.openggf.data.cd.chd;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.IntStream;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * The hunk map and per-hunk decompression of a CHD v5 CD image. Map layout and codecs follow MAME's
 * {@code chd.cpp}/{@code chdcodec.cpp} and libchdr (BSD-3-Clause). Every hunk's CRC-16 is checked when the
 * map provides one. Reads are bounded by the file size; a hunk never allocates more than its fixed size.
 * Decoding is stateless per hunk: {@link #readMany} decodes independent hunks in parallel, each worker with
 * its own codec workspace; positional file reads are safe to share.
 */
public final class ChdHunkDecoder {
    /** One CD frame unit: 2352 sector bytes followed by 96 subcode bytes. */
    public static final int FRAME_BYTES = 2448;
    private static final int SECTOR_BYTES = 2352;
    private static final int SUBCODE_BYTES = 96;

    private static final int TYPE_0 = 0;
    private static final int TYPE_3 = 3;
    private static final int NONE = 4;
    private static final int SELF = 5;
    private static final int PARENT = 6;
    private static final int RLE_SMALL = 7;
    private static final int RLE_LARGE = 8;
    private static final int SELF_0 = 9;
    private static final int SELF_1 = 10;
    private static final int PARENT_SELF = 11;
    private static final int PARENT_0 = 12;
    private static final int PARENT_1 = 13;

    private static final int CDZL = tag("cdzl");
    private static final int CDLZ = tag("cdlz");
    private static final int CDFL = tag("cdfl");
    private static final int CDZS = tag("cdzs");

    private final FileChannel channel;
    private final long fileSize;
    private final int[] compressors;
    private final int hunkBytes;
    private final int hunkCount;
    private final long logicalBytes;
    /** Twelve bytes per hunk (type, 24-bit length, 48-bit offset, CRC-16), or null when uncompressed. */
    private final byte[] map;
    /** Uncompressed CHDs: hunk offset in units of hunkBytes. */
    private final long[] uncompressedOffsets;

    private final ConcurrentLinkedQueue<Workspace> workspaces = new ConcurrentLinkedQueue<>();
    private final List<Workspace> allWorkspaces = new ArrayList<>();
    private boolean closed;

    /** Codec state and scratch for one decoding thread. */
    private final class Workspace {
        final Inflater inflater = new Inflater(true);
        final LzmaDecoder lzma = new LzmaDecoder();
        final FlacDecoder flac = new FlacDecoder();
        final ZstdDecoder zstd = new ZstdDecoder();
        final byte[] sectors = new byte[hunkBytes / FRAME_BYTES * SECTOR_BYTES];
        final byte[] subcodes = new byte[hunkBytes / FRAME_BYTES * SUBCODE_BYTES];
    }

    private ChdHunkDecoder(FileChannel channel, long fileSize, int[] compressors, int hunkBytes, int hunkCount,
                           long logicalBytes, byte[] map, long[] uncompressedOffsets) {
        this.channel = channel;
        this.fileSize = fileSize;
        this.compressors = compressors;
        this.hunkBytes = hunkBytes;
        this.hunkCount = hunkCount;
        this.logicalBytes = logicalBytes;
        this.map = map;
        this.uncompressedOffsets = uncompressedOffsets;
    }

    public static ChdHunkDecoder open(FileChannel channel, long fileSize, int[] compressors, int hunkBytes,
                                      int hunkCount, long logicalBytes, long mapOffset) throws IOException {
        for (int compressor : compressors) {
            if (compressor != 0 && compressor != CDZL && compressor != CDLZ && compressor != CDFL
                    && compressor != CDZS) {
                throw new ChdFormatException("CHD compressor '" + name(compressor) + "' is not supported "
                        + "(supported: cdzl, cdlz, cdfl, cdzs)");
            }
        }
        if (compressors[0] == 0) {
            for (int i = 1; i < 4; i++) {
                if (compressors[i] != 0) {
                    throw new ChdFormatException("CHD declares compressors after an empty first slot");
                }
            }
            byte[] raw = read(channel, fileSize, mapOffset, Math.multiplyExact(hunkCount, 4));
            ByteBuffer buffer = ByteBuffer.wrap(raw);
            long[] offsets = new long[hunkCount];
            for (int i = 0; i < hunkCount; i++) {
                offsets[i] = Integer.toUnsignedLong(buffer.getInt());
            }
            return new ChdHunkDecoder(channel, fileSize, compressors, hunkBytes, hunkCount, logicalBytes, null, offsets);
        }
        return new ChdHunkDecoder(channel, fileSize, compressors, hunkBytes, hunkCount, logicalBytes,
                decodeMap(channel, fileSize, hunkBytes, hunkCount, mapOffset), null);
    }

    /** The v5 compressed map: Huffman-coded types with RLE, then bit-packed lengths, offsets and CRCs. */
    private static byte[] decodeMap(FileChannel channel, long fileSize, int hunkBytes, int hunkCount, long mapOffset)
            throws IOException {
        ByteBuffer header = ByteBuffer.wrap(read(channel, fileSize, mapOffset, 16));
        long mapBytes = Integer.toUnsignedLong(header.getInt());
        long firstOffset = ((long) (header.getShort() & 0xFFFF) << 32) | Integer.toUnsignedLong(header.getInt());
        int mapCrc = header.getShort() & 0xFFFF;
        int lengthBits = header.get() & 0xFF;
        int selfBits = header.get() & 0xFF;
        int parentBits = header.get() & 0xFF;
        if (lengthBits > 32 || selfBits > 32 || parentBits > 32) {
            throw new ChdFormatException("CHD map field widths are invalid");
        }
        if (mapBytes > fileSize - mapOffset - 16 || mapBytes > (long) hunkCount * 16 + 1024) {
            throw new ChdFormatException("CHD map runs past the end of the file");
        }
        byte[] packed = read(channel, fileSize, mapOffset + 16, (int) mapBytes);
        MsbBitReader bits = new MsbBitReader(packed, 0, packed.length);
        ChdHuffman huffman = ChdHuffman.importTreeRle(bits, 16, 8);
        byte[] map = new byte[Math.multiplyExact(hunkCount, 12)];
        int repeat = 0;
        int last = 0;
        for (int hunk = 0; hunk < hunkCount; hunk++) {
            if (repeat > 0) {
                map[hunk * 12] = (byte) last;
                repeat--;
                continue;
            }
            int value = huffman.decodeOne(bits);
            if (value == RLE_SMALL) {
                map[hunk * 12] = (byte) last;
                repeat = 2 + huffman.decodeOne(bits);
            } else if (value == RLE_LARGE) {
                map[hunk * 12] = (byte) last;
                repeat = 2 + 16 + (huffman.decodeOne(bits) << 4);
                repeat += huffman.decodeOne(bits);
            } else {
                map[hunk * 12] = (byte) value;
                last = value;
            }
        }
        long offset = firstOffset;
        long lastSelf = 0;
        long lastParent = 0;
        for (int hunk = 0; hunk < hunkCount; hunk++) {
            int at = hunk * 12;
            int type = map[at];
            long entryOffset = offset;
            long length = 0;
            int crc = 0;
            switch (type) {
                case 0, 1, 2, 3 -> {
                    length = bits.readLong(lengthBits);
                    offset += length;
                    crc = bits.read(16);
                }
                case NONE -> {
                    length = hunkBytes;
                    offset += length;
                    crc = bits.read(16);
                }
                case SELF -> {
                    lastSelf = bits.readLong(selfBits);
                    entryOffset = lastSelf;
                }
                case PARENT -> {
                    entryOffset = bits.readLong(parentBits);
                    lastParent = entryOffset;
                }
                case SELF_1, SELF_0 -> {
                    if (type == SELF_1) {
                        lastSelf++;
                    }
                    map[at] = SELF;
                    entryOffset = lastSelf;
                }
                case PARENT_SELF -> {
                    map[at] = PARENT;
                    lastParent = (long) hunk * hunkBytes / FRAME_BYTES;
                    entryOffset = lastParent;
                }
                case PARENT_1, PARENT_0 -> {
                    if (type == PARENT_1) {
                        lastParent += hunkBytes / FRAME_BYTES;
                    }
                    map[at] = PARENT;
                    entryOffset = lastParent;
                }
                default -> throw new ChdFormatException("CHD map has invalid hunk type " + type);
            }
            if (length > 0xFFFFFF || entryOffset < 0 || entryOffset > 0xFFFFFFFFFFFFL) {
                throw new ChdFormatException("CHD map entry out of range");
            }
            map[at + 1] = (byte) (length >>> 16);
            map[at + 2] = (byte) (length >>> 8);
            map[at + 3] = (byte) length;
            for (int i = 0; i < 6; i++) {
                map[at + 4 + i] = (byte) (entryOffset >>> (40 - 8 * i));
            }
            map[at + 10] = (byte) (crc >>> 8);
            map[at + 11] = (byte) crc;
        }
        if (bits.overflowed()) {
            throw new ChdFormatException("CHD map is truncated");
        }
        if (Crc16.of(map, 0, map.length) != mapCrc) {
            throw new ChdFormatException("CHD map CRC mismatch");
        }
        return map;
    }

    public int hunkBytes() {
        return hunkBytes;
    }

    public int hunkCount() {
        return hunkCount;
    }

    public long logicalBytes() {
        return logicalBytes;
    }

    public List<String> compressorNames() {
        List<String> names = new ArrayList<>();
        for (int compressor : compressors) {
            if (compressor != 0) {
                names.add(name(compressor));
            }
        }
        return List.copyOf(names);
    }

    /** Decodes the given hunks, in parallel when there are several; results are in argument order. */
    public byte[][] readMany(int[] hunks) throws IOException {
        byte[][] result = new byte[hunks.length][];
        if (hunks.length == 1) {
            result[0] = read(hunks[0]);
            return result;
        }
        try {
            IntStream.range(0, hunks.length).parallel().forEach(i -> {
                try {
                    result[i] = read(hunks[i]);
                } catch (IOException failure) {
                    throw new UncheckedIOException(failure);
                }
            });
        } catch (UncheckedIOException failure) {
            throw failure.getCause();
        }
        return result;
    }

    /** A freshly allocated copy of one decoded hunk. Safe to call from several threads. */
    public byte[] read(int hunk) throws IOException {
        Workspace workspace = borrow();
        try {
            return read(hunk, workspace);
        } finally {
            workspaces.add(workspace);
        }
    }

    private Workspace borrow() throws IOException {
        Workspace workspace = workspaces.poll();
        if (workspace != null) {
            return workspace;
        }
        synchronized (allWorkspaces) {
            if (closed) {
                throw new IOException("Disc image is closed");
            }
            workspace = new Workspace();
            allWorkspaces.add(workspace);
            return workspace;
        }
    }

    private byte[] read(int hunk, Workspace workspace) throws IOException {
        if (hunk < 0 || hunk >= hunkCount) {
            throw new ChdFormatException("CHD hunk " + hunk + " is outside the image");
        }
        byte[] out = new byte[hunkBytes];
        if (map == null) {
            long offset = uncompressedOffsets[hunk];
            if (offset != 0) {
                byte[] data = read(channel, fileSize, Math.multiplyExact(offset, (long) hunkBytes), hunkBytes);
                System.arraycopy(data, 0, out, 0, hunkBytes);
            }
            return out;
        }
        int target = hunk;
        for (int depth = 0; ; depth++) {
            int at = target * 12;
            int type = map[at];
            long length = ((map[at + 1] & 0xFFL) << 16) | ((map[at + 2] & 0xFFL) << 8) | (map[at + 3] & 0xFFL);
            long offset = 0;
            for (int i = 0; i < 6; i++) {
                offset = (offset << 8) | (map[at + 4 + i] & 0xFF);
            }
            int crc = ((map[at + 10] & 0xFF) << 8) | (map[at + 11] & 0xFF);
            if (type == SELF) {
                if (offset >= target || depth > hunkCount) {
                    throw new ChdFormatException("CHD hunk " + target + " copies a hunk that is not earlier");
                }
                target = (int) offset;
                continue;
            }
            if (type == PARENT) {
                throw new ChdFormatException("CHD hunk " + target + " needs a parent CHD, which is not supported");
            }
            if (type == NONE) {
                byte[] data = read(channel, fileSize, offset, hunkBytes);
                System.arraycopy(data, 0, out, 0, hunkBytes);
            } else if (type >= TYPE_0 && type <= TYPE_3) {
                int compressor = compressors[type];
                if (compressor == 0) {
                    throw new ChdFormatException("CHD hunk " + target + " uses an undeclared compressor");
                }
                if (length > (long) hunkBytes * 2) {
                    throw new ChdFormatException("CHD hunk " + target + " is implausibly large");
                }
                byte[] data = read(channel, fileSize, offset, (int) length);
                decompress(workspace, compressor, data, out);
            } else {
                throw new ChdFormatException("CHD map has invalid hunk type " + type);
            }
            if (Crc16.of(out, 0, hunkBytes) != crc) {
                throw new ChdFormatException("CHD hunk " + target + " CRC mismatch");
            }
            return out;
        }
    }

    private void decompress(Workspace w, int compressor, byte[] src, byte[] out) throws ChdFormatException {
        int frames = hunkBytes / FRAME_BYTES;
        int sectorBytes = frames * SECTOR_BYTES;
        int subcodeBytes = frames * SUBCODE_BYTES;
        if (compressor == CDFL) {
            int used = w.flac.decode(src, 0, src.length, w.sectors, sectorBytes);
            inflate(w.inflater, src, used, src.length - used, w.subcodes, subcodeBytes);
            interleave(w, frames, out, null);
            return;
        }
        int lengthBytes = hunkBytes < 65536 ? 2 : 3;
        int eccBytes = (frames + 7) / 8;
        int headerBytes = eccBytes + lengthBytes;
        if (src.length < headerBytes) {
            throw new ChdFormatException("CHD CD hunk header is truncated");
        }
        int baseLength = 0;
        for (int i = 0; i < lengthBytes; i++) {
            baseLength = (baseLength << 8) | (src[eccBytes + i] & 0xFF);
        }
        if (baseLength > src.length - headerBytes) {
            throw new ChdFormatException("CHD CD hunk base stream runs past the hunk");
        }
        int subStart = headerBytes + baseLength;
        int subLength = src.length - subStart;
        if (compressor == CDZL) {
            inflate(w.inflater, src, headerBytes, baseLength, w.sectors, sectorBytes);
            inflate(w.inflater, src, subStart, subLength, w.subcodes, subcodeBytes);
        } else if (compressor == CDLZ) {
            w.lzma.decode(src, headerBytes, baseLength, w.sectors);
            inflate(w.inflater, src, subStart, subLength, w.subcodes, subcodeBytes);
        } else {
            w.zstd.decode(src, headerBytes, baseLength, w.sectors, sectorBytes);
            w.zstd.decode(src, subStart, subLength, w.subcodes, subcodeBytes);
        }
        interleave(w, frames, out, src);
    }

    /** Releases the native inflaters. Call only when no read is in progress. */
    public void close() {
        synchronized (allWorkspaces) {
            closed = true;
            for (Workspace workspace : allWorkspaces) {
                workspace.inflater.end();
            }
            allWorkspaces.clear();
            workspaces.clear();
        }
    }

    /** Sector then subcode per frame; where the ECC bit is set, restore the sync pattern and P/Q parity. */
    private static void interleave(Workspace w, int frames, byte[] out, byte[] eccFlags) {
        for (int frame = 0; frame < frames; frame++) {
            int at = frame * FRAME_BYTES;
            System.arraycopy(w.sectors, frame * SECTOR_BYTES, out, at, SECTOR_BYTES);
            System.arraycopy(w.subcodes, frame * SUBCODE_BYTES, out, at + SECTOR_BYTES, SUBCODE_BYTES);
            if (eccFlags != null && (eccFlags[frame / 8] & (1 << (frame % 8))) != 0) {
                CdEcc.regenerate(out, at);
            }
        }
    }

    private static void inflate(Inflater inflater, byte[] src, int offset, int length, byte[] out, int outLength)
            throws ChdFormatException {
        inflater.reset();
        byte[] input = new byte[length + 1];
        System.arraycopy(src, offset, input, 0, length);
        inflater.setInput(input);
        int produced = 0;
        try {
            while (produced < outLength) {
                int count = inflater.inflate(out, produced, outLength - produced);
                if (count == 0 && (inflater.finished() || inflater.needsInput() || inflater.needsDictionary())) {
                    break;
                }
                produced += count;
            }
        } catch (DataFormatException exception) {
            throw new ChdFormatException("CHD deflate stream is corrupt", exception);
        }
        if (produced != outLength) {
            throw new ChdFormatException("CHD deflate stream is " + produced + " bytes, expected " + outLength);
        }
    }

    private static byte[] read(FileChannel channel, long fileSize, long position, int length) throws IOException {
        if (position < 0 || length < 0 || position > fileSize || length > fileSize - position) {
            throw new ChdFormatException("CHD data lies outside the file");
        }
        ByteBuffer buffer = ByteBuffer.allocate(length);
        while (buffer.hasRemaining()) {
            int count = channel.read(buffer, position + buffer.position());
            if (count <= 0) {
                throw new IOException("Truncated or unreadable CHD file");
            }
        }
        return buffer.array();
    }

    private static int tag(String text) {
        return ByteBuffer.wrap(text.getBytes(StandardCharsets.US_ASCII)).getInt();
    }

    private static String name(int tag) {
        byte[] bytes = ByteBuffer.allocate(4).putInt(tag).array();
        StringBuilder text = new StringBuilder();
        for (byte b : bytes) {
            text.append(b >= 0x20 && b < 0x7F ? (char) b : '?');
        }
        return text.toString();
    }
}
