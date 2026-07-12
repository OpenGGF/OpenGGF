package com.openggf.tools.modsdk;

import com.openggf.io.ModInputLimits;
import com.openggf.level.objects.PlayableSheetReader;
import com.openggf.level.objects.BakedSheetReader;
import com.openggf.game.ModKeySyntax;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Canonical writer for GGFP playable-sheet v2. */
public final class PlayableSheetWriter {
    private PlayableSheetWriter() {}

    public static byte[] write(PlayableSheetReader.PlayableSheet sheet) throws IOException {
        Objects.requireNonNull(sheet, "sheet");
        validate(sheet);
        byte[] base = base(sheet.baseSheetV1());
        byte[] meta = meta(sheet.meta());
        byte[] frames = frames(sheet.frames());
        byte[] animations = animations(sheet.animations());
        byte[] appendages = sheet.appendages().isEmpty() ? null : appendages(sheet.appendages());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeBytes("GGFP"); out.writeShort(2); out.writeShort(appendages == null ? 4 : 5);
            section(out, "BASE", base); section(out, "META", meta); section(out, "FRAM", frames);
            section(out, "ANIM", animations); if (appendages != null) section(out, "APND", appendages);
        }
        byte[] encoded = bytes.toByteArray();
        if (encoded.length > ModInputLimits.DEFAULT_MAX_ASSET_BYTES) throw new IllegalArgumentException("playable sheet exceeds production byte limit");
        // One strict parse validates every cross-section range and canonical-domain rule.
        PlayableSheetReader.read(encoded);
        return encoded;
    }

    private static byte[] base(byte[] ggfs) throws IOException {
        return payload(out -> {
            out.writeInt(ggfs.length);
            out.write(ggfs);
        });
    }

    private static byte[] meta(PlayableSheetReader.Meta meta) throws IOException {
        return payload(out -> {
            out.writeInt(meta.basePatternIndex());
            out.writeInt(meta.bankSize());
            out.writeByte(meta.paletteLine());
            out.writeByte(0);
            out.writeShort(0);
        });
    }

    private static byte[] frames(List<PlayableSheetReader.Frame> frames) throws IOException {
        return payload(out -> {
            out.writeShort(frames.size());
            for (var frame : frames) {
                out.writeShort(frame.originX());
                out.writeShort(frame.originY());
                out.writeShort(frame.collisionWidth());
                out.writeShort(frame.collisionHeight());
                out.writeShort(frame.runs().size());
                for (var run : frame.runs()) {
                    out.writeShort(run.sourceTile());
                    out.writeShort(run.tileCount());
                    out.writeShort(run.bankOffset());
                }
            }
        });
    }

    private static byte[] animations(Map<String, PlayableSheetReader.Animation> values)
            throws IOException {
        return payload(out -> {
            List<String> keys = sortedKeys(values);
            out.writeShort(keys.size());
            for (String key : keys) {
                writeString(out, key);
                var animation = values.get(key);
                out.writeShort(animation.steps().size());
                for (var step : animation.steps()) {
                    out.writeShort(step.frameIndex());
                    out.writeShort(step.duration());
                    out.writeByte(step.loop() ? 1 : 0);
                    out.writeByte(0);
                }
            }
        });
    }

    private static byte[] appendages(Map<String, List<Integer>> values) throws IOException {
        return payload(out -> {
            List<String> keys = sortedKeys(values);
            out.writeShort(keys.size());
            for (String key : keys) {
                writeString(out, key);
                List<Integer> indices = values.get(key);
                out.writeShort(indices.size());
                for (int index : indices) out.writeShort(index);
            }
        });
    }
    private static List<String> sortedKeys(Map<String, ?> values) {
        ArrayList<String> keys = new ArrayList<>(values.keySet());
        keys.sort(PlayableSheetWriter::compareUtf8);
        return keys;
    }
    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length == 0 || bytes.length > 0xFFFF) {
            throw new IllegalArgumentException("invalid UTF-8 key length");
        }
        out.writeShort(bytes.length);
        out.write(bytes);
    }

    private static void section(DataOutputStream out, String tag, byte[] payload)
            throws IOException {
        out.writeBytes(tag);
        out.writeInt(payload.length);
        out.write(payload);
    }

    private static byte[] payload(IoWriter writer) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            writer.write(out);
        }
        return bytes.toByteArray();
    }
    @FunctionalInterface private interface IoWriter { void write(DataOutputStream out) throws IOException; }

    private static void validate(PlayableSheetReader.PlayableSheet sheet) throws IOException {
        BakedSheetReader.BakedSheet baked = BakedSheetReader.read(sheet.baseSheetV1());
        int baseFrames = baked.frames().size();
        int basePatterns = baked.patterns().length;
        requireU16(sheet.frames().size(), "frame count");
        if (sheet.frames().size() != baseFrames) throw new IllegalArgumentException("FRAM count must match BASE");
        var meta = sheet.meta();
        requireU32(meta.basePatternIndex(), "base pattern index");
        requireU32(meta.bankSize(), "bank size");
        if (meta.bankSize() == 0 || (long) meta.basePatternIndex() + meta.bankSize() > Integer.MAX_VALUE)
            throw new IllegalArgumentException("invalid META pattern-bank span");
        if (meta.paletteLine() < 0 || meta.paletteLine() > 3) throw new IllegalArgumentException("palette line");
        for (var frame : sheet.frames()) {
            requireS16(frame.originX(), "origin x"); requireS16(frame.originY(), "origin y");
            requirePositiveU16(frame.collisionWidth(), "collision width");
            requirePositiveU16(frame.collisionHeight(), "collision height");
            requirePositiveU16(frame.runs().size(), "DPLC run count");
            for (var run : frame.runs()) {
                requireU16(run.sourceTile(), "source tile");
                requirePositiveU16(run.tileCount(), "tile count");
                requireU16(run.bankOffset(), "bank offset");
                if ((long) run.sourceTile() + run.tileCount() > basePatterns
                        || (long) run.bankOffset() + run.tileCount() > meta.bankSize()) {
                    throw new IllegalArgumentException("DPLC span out of bounds");
                }
            }
        }
        requireU16(sheet.animations().size(), "animation count");
        for (var entry : sheet.animations().entrySet()) {
            ModKeySyntax.requireLocalName(entry.getKey());
            requirePositiveU16(entry.getValue().steps().size(), "animation step count");
            for (var step : entry.getValue().steps()) {
                requireU16(step.frameIndex(), "animation frame");
                requirePositiveU16(step.duration(), "animation duration");
            }
        }
        requireU16(sheet.appendages().size(), "appendage count");
        for (var entry : sheet.appendages().entrySet()) {
            ModKeySyntax.requireDisplayKey(entry.getKey());
            requirePositiveU16(entry.getValue().size(), "appendage frame count");
            for (int index : entry.getValue()) requireU16(index, "appendage frame");
        }
    }

    private static int compareUtf8(String left, String right) {
        byte[] a = left.getBytes(StandardCharsets.UTF_8);
        byte[] b = right.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            int compared = Integer.compare(Byte.toUnsignedInt(a[i]), Byte.toUnsignedInt(b[i]));
            if (compared != 0) return compared;
        }
        return Integer.compare(a.length,b.length);
    }

    private static void requireS16(int value, String name) {
        if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) throw new IllegalArgumentException(name);
    }

    private static void requireU16(int value, String name) {
        if (value < 0 || value > 0xFFFF) throw new IllegalArgumentException(name);
    }

    private static void requirePositiveU16(int value, String name) {
        requireU16(value, name);
        if (value == 0) throw new IllegalArgumentException(name);
    }

    private static void requireU32(int value, String name) {
        if (value < 0) throw new IllegalArgumentException(name);
    }
}
