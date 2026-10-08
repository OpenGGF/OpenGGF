package com.openggf.game.presentation;

import com.openggf.game.ModApi;

import java.io.*;
import java.util.*;

/** Versioned bounded scene codec. No art bytes, engine objects, callbacks or gameplay snapshots. */
@ModApi
public final class SceneFrameCodec {
    public static final int MAX_BYTES = 2 * 1_024 * 1_024;
    private static final int MAGIC = 0x50505343;
    private SceneFrameCodec() { }

    public static byte[] encode(ScenePresentationFrame frame) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(new FilterOutputStream(bytes) {
            int count;
            @Override public void write(int value) throws IOException {
                if (++count > MAX_BYTES) throw new IOException("Scene exceeds byte limit");
                out.write(value);
            }
            @Override public void write(byte[] values, int offset, int length) throws IOException {
                if (length > MAX_BYTES - count) throw new IOException("Scene exceeds byte limit");
                count += length;
                out.write(values, offset, length);
            }
        })) {
            out.writeInt(MAGIC); out.writeShort(ScenePresentationFrame.SCHEMA);
            out.writeLong(frame.revision()); out.writeByte(frame.act());
            out.writeShort(frame.width()); out.writeShort(frame.height());
            out.writeInt(frame.cameraX()); out.writeInt(frame.cameraY()); out.writeInt(frame.backdropArgb());
            int[] palette = frame.paletteArgb(); out.writeShort(palette.length);
            for (int color : palette) out.writeInt(color);
            var refs = new LinkedHashMap<ScenePresentationFrame.ArtReference, Integer>();
            for (var tile : frame.tiles()) refs.computeIfAbsent(tile.art(), ignored -> refs.size());
            if (refs.size() > 65_535) throw new IOException("Too many art references");
            out.writeShort(refs.size());
            for (var art : refs.keySet()) { out.writeUTF(art.recipe()); out.writeShort(art.tile()); }
            out.writeInt(frame.tiles().size());
            for (var tile : frame.tiles()) {
                out.writeByte(tile.layer().ordinal()); out.writeShort(refs.get(tile.art())); out.writeByte(tile.palette());
                out.writeByte((tile.hFlip() ? 1 : 0) | (tile.vFlip() ? 2 : 0) | (tile.priority() ? 4 : 0));
                out.writeInt(tile.x()); out.writeInt(tile.y()); out.writeShort(tile.width()); out.writeShort(tile.height());
                out.writeByte(tile.rowStart()); out.writeByte(tile.rowEnd());
                out.writeByte(tile.occlusionMask()); out.writeByte(tile.alpha());
            }
            out.writeShort(frame.primitives().size());
            for (var primitive : frame.primitives()) {
                out.writeInt(primitive.beforeTile()); out.writeByte(primitive.kind().ordinal());
                out.writeByte(primitive.method()); out.writeShort(primitive.vertices().size());
                for (var vertex : primitive.vertices()) {
                    out.writeInt(vertex.x1()); out.writeInt(vertex.y1()); out.writeInt(vertex.x2());
                    out.writeInt(vertex.y2()); out.writeInt(vertex.argb());
                }
            }
        }
        return bytes.toByteArray();
    }

    public static ScenePresentationFrame decode(byte[] bytes) throws IOException {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length > MAX_BYTES) throw new IOException("Scene exceeds byte limit");
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != MAGIC || in.readUnsignedShort() != ScenePresentationFrame.SCHEMA) {
                throw new IOException("Unsupported scene schema");
            }
            long revision = in.readLong(); int act = in.readUnsignedByte();
            int width = in.readUnsignedShort(), height = in.readUnsignedShort();
            int x = in.readInt(), y = in.readInt(), backdrop = in.readInt();
            int paletteSize = bounded(in.readUnsignedShort(), 1_024, "palette");
            int[] palette = new int[paletteSize];
            for (int i = 0; i < palette.length; i++) palette[i] = in.readInt();
            int count = in.readUnsignedShort();
            var refs = new ArrayList<ScenePresentationFrame.ArtReference>(count);
            for (int i = 0; i < count; i++) {
                // Bound before DataInputStream allocates or decodes a supplied UTF string.
                int length = bounded(in.readUnsignedShort(), 96, "art recipe");
                byte[] recipe = new byte[length]; in.readFully(recipe);
                refs.add(new ScenePresentationFrame.ArtReference(new String(recipe, java.nio.charset.StandardCharsets.US_ASCII),
                        in.readUnsignedShort()));
            }
            count = bounded(in.readInt(), ScenePresentationFrame.MAX_TILES, "tiles");
            var tiles = new ArrayList<ScenePresentationFrame.Tile>(count);
            for (int i = 0; i < count; i++) {
                var layer = enumAt(ScenePresentationFrame.Layer.values(), in.readUnsignedByte());
                int ref = in.readUnsignedShort();
                if (ref >= refs.size()) throw new IOException("Unknown art reference");
                int pal = in.readUnsignedByte(), flags = in.readUnsignedByte();
                if ((flags & ~7) != 0) throw new IOException("Unknown tile flags");
                tiles.add(new ScenePresentationFrame.Tile(layer, refs.get(ref), pal, (flags & 1) != 0, (flags & 2) != 0,
                        (flags & 4) != 0, in.readInt(), in.readInt(), in.readUnsignedShort(), in.readUnsignedShort(),
                        in.readUnsignedByte(), in.readUnsignedByte(), in.readUnsignedByte(), in.readUnsignedByte()));
            }
            count = bounded(in.readUnsignedShort(), ScenePresentationFrame.MAX_PRIMITIVES, "primitives");
            var primitives = new ArrayList<ScenePresentationFrame.Primitive>(count);
            for (int i = 0; i < count; i++) {
                int before = in.readInt();
                var kind = enumAt(ScenePresentationFrame.PrimitiveKind.values(), in.readUnsignedByte());
                int method = in.readUnsignedByte(), vertexCount = bounded(in.readUnsignedShort(), 256, "vertices");
                var vertices = new ArrayList<ScenePresentationFrame.Vertex>(vertexCount);
                for (int v = 0; v < vertexCount; v++) {
                    vertices.add(new ScenePresentationFrame.Vertex(in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readInt()));
                }
                primitives.add(new ScenePresentationFrame.Primitive(before, kind, method, vertices));
            }
            if (in.read() != -1) throw new IOException("Trailing scene data");
            return new ScenePresentationFrame(revision, act, width, height, x, y, backdrop, palette, tiles, primitives);
        } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
            throw new IOException("Invalid scene values", e);
        }
    }

    private static int bounded(int value, int maximum, String label) throws IOException {
        if (value < 0 || value > maximum) throw new IOException("Invalid " + label + " count");
        return value;
    }
    private static <E> E enumAt(E[] values, int ordinal) throws IOException {
        if (ordinal >= values.length) throw new IOException("Unknown scene enum");
        return values[ordinal];
    }
}
