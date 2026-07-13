package com.openggf.tools.modsdk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Canonical Phase-2 JSON/binary writer for already validated TMX conversion state. */
final class TmxLevelExportWriter {
    private static final ObjectMapper JSON = new ObjectMapper();

    void write(Path output, TmxLevelImporter.Parsed source, TmxLevelImporter.Palette palette,
               TmxLevelImporter.Profiles profiles, TmxLevelImporter.Compiled level) throws IOException {
        Files.write(output.resolve("patterns.bin"), binary(out -> {
            out.writeBytes("GPTN"); out.writeShort(1); out.writeShort(32); out.writeInt(level.patterns().size());
            for (byte[] pattern : level.patterns()) out.write(pattern);
        }));
        Files.write(output.resolve("chunks.bin"), binary(out -> {
            out.writeBytes("GCHK"); out.writeShort(1); out.writeShort(8); out.writeInt(level.chunks().size());
            for (TmxLevelImporter.ChunkRecord chunk : level.chunks()) {
                for (int descriptor : chunk.descriptors()) out.writeShort(descriptor);
            }
        }));
        Files.write(output.resolve("blocks.bin"), binary(out -> {
            out.writeBytes("GBLK"); out.writeShort(1); out.writeByte(8); out.writeByte(0); out.writeInt(level.blocks().size());
            for (int[] block : level.blocks()) for (int descriptor : block) out.writeShort(descriptor);
        }));
        Files.write(output.resolve("fg-map.bin"), mapBytes(level.fgMap(), level.mapWidth(), level.mapHeight()));
        if (level.bgMap() != null) Files.write(output.resolve("bg-map.bin"), mapBytes(level.bgMap(), level.mapWidth(), level.mapHeight()));
        Files.write(output.resolve("solid-heights.bin"), profiles.heights());
        Files.write(output.resolve("solid-widths.bin"), profiles.widths());
        Files.write(output.resolve("solid-angles.bin"), profiles.angles());
        Files.write(output.resolve("collision-primary.bin"), collisions(level.chunks(), 0));
        Files.write(output.resolve("collision-secondary.bin"), collisions(level.chunks(), 1));
        Files.write(output.resolve("palettes.bin"), palette.bytes());
        Files.write(output.resolve("level.json"), JSON.writeValueAsBytes(metadata(source, level)));
    }

    private ObjectNode metadata(TmxLevelImporter.Parsed source, TmxLevelImporter.Compiled level) {
        ObjectNode root = JSON.createObjectNode();
        root.put("formatVersion", 1); root.put("zoneName", "Tiled Level"); root.put("zoneIndex", 0x40);
        root.put("levelIndex", 0x400); root.put("blockGridSide", 8);
        root.put("width", level.mapWidth()); root.put("height", level.mapHeight());
        ObjectNode bounds = root.putObject("bounds"); bounds.put("minX", 0); bounds.put("maxX", source.width() * 16);
        bounds.put("minY", 0); bounds.put("maxY", source.height() * 16);
        TmxLevelImporter.Point position = source.start() == null ? new TmxLevelImporter.Point(128, 128) : source.start();
        ObjectNode start = root.putObject("start"); start.put("x", position.x()); start.put("y", position.y());
        root.putObject("music").put("stockId", 0);
        ObjectNode assets = root.putObject("assets");
        assets.put("patterns", "patterns.bin"); assets.put("chunks", "chunks.bin"); assets.put("blocks", "blocks.bin");
        assets.put("foregroundMap", "fg-map.bin"); if (level.bgMap() != null) assets.put("backgroundMap", "bg-map.bin");
        assets.put("solidHeights", "solid-heights.bin"); assets.put("solidWidths", "solid-widths.bin");
        assets.put("solidAngles", "solid-angles.bin"); assets.put("collisionPrimary", "collision-primary.bin");
        assets.put("collisionSecondary", "collision-secondary.bin"); assets.put("palettes", "palettes.bin");
        var objects = root.putArray("objects");
        for (int placement = 0; placement < source.objects().size(); placement++) {
            TmxLevelImporter.ObjectMarker marker = source.objects().get(placement);
            ObjectNode object = objects.addObject(); object.put("placementId", placement);
            object.put("x", marker.position().x()); object.put("y", marker.position().y());
            if (marker.stockObjectId() != null) object.put("stockObjectId", marker.stockObjectId());
            else object.put("objectKey", marker.objectKey());
            object.put("subtype", marker.subtype()); object.put("renderFlags", 0);
            object.put("respawnTracked", marker.respawnTracked());
            object.put("rawYWord", marker.position().y() | (marker.respawnTracked() ? 0x8000 : 0));
        }
        var rings = root.putArray("rings");
        for (int placement = 0; placement < source.rings().size(); placement++) {
            TmxLevelImporter.Point ring = source.rings().get(placement);
            ObjectNode object = rings.addObject(); object.put("placementId", placement);
            object.put("x", ring.x()); object.put("y", ring.y());
        }
        return root;
    }

    private static byte[] mapBytes(byte[] map, int width, int height) throws IOException {
        return binary(out -> {
            out.writeBytes("GMAP"); out.writeShort(1); out.writeShort(width); out.writeShort(height);
            out.writeShort(1); out.writeInt(map.length); out.write(map);
        });
    }

    private static byte[] collisions(List<TmxLevelImporter.ChunkRecord> chunks, int path) throws IOException {
        return binary(out -> {
            out.writeBytes("GCOL"); out.writeShort(1); out.writeByte(path); out.writeByte(2); out.writeInt(chunks.size());
            for (TmxLevelImporter.ChunkRecord chunk : chunks) out.writeShort(path == 0 ? chunk.primary() : chunk.secondary());
        });
    }

    private static byte[] binary(IoWriter writer) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) { writer.write(output); }
        return bytes.toByteArray();
    }

    @FunctionalInterface
    private interface IoWriter { void write(DataOutputStream output) throws IOException; }
}
