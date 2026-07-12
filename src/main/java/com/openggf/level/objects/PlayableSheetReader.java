package com.openggf.level.objects;

import com.openggf.game.ModApi;
import com.openggf.game.ModKeySyntax;
import com.openggf.io.ModInputLimits;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Strict bounded reader for the canonical GGFP playable-sheet container v2. */
@ModApi
public final class PlayableSheetReader {
    static final int BASE = tag("BASE"), META = tag("META"), FRAM = tag("FRAM"),
            ANIM = tag("ANIM"), APND = tag("APND");
    private PlayableSheetReader() {}

    public static PlayableSheet read(byte[] bytes) throws IOException {
        return read(bytes, ModInputLimits.production());
    }

    public static PlayableSheet read(byte[] bytes, ModInputLimits limits) throws IOException {
        Objects.requireNonNull(bytes, "bytes"); Objects.requireNonNull(limits, "limits");
        if (bytes.length > limits.maxAssetBytes()) throw invalid("container exceeds byte limit");
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != tag("GGFP")) throw invalid("bad magic");
            if (in.readUnsignedShort() != 2) throw invalid("unsupported version");
            int sectionCount = in.readUnsignedShort();
            if (sectionCount < 4 || sectionCount > 64) throw invalid("invalid section count");
            LinkedHashMap<Integer, byte[]> sections = new LinkedHashMap<>();
            for (int i = 0; i < sectionCount; i++) {
                int tag = in.readInt();
                long rawLength = Integer.toUnsignedLong(in.readInt());
                if (rawLength > limits.maxAssetBytes() || rawLength > in.available()) {
                    throw invalid("invalid section length");
                }
                byte[] payload = in.readNBytes((int) rawLength);
                if (sections.putIfAbsent(tag, payload) != null) throw invalid("duplicate section");
            }
            if (in.read() != -1) throw invalid("trailing bytes");
            requireOrder(sections.keySet());
            byte[] basePayload = required(sections, BASE);
            byte[] metaPayload = required(sections, META);
            byte[] framePayload = required(sections, FRAM);
            byte[] animPayload = required(sections, ANIM);
            for (int tag : sections.keySet()) {
                if (tag != BASE && tag != META && tag != FRAM && tag != ANIM && tag != APND
                        && (tag & 0x80000000) == 0) throw invalid("unknown required section");
            }
            byte[] ggfs = parseBase(basePayload, limits);
            BakedSheetReader.BakedSheet base = BakedSheetReader.read(ggfs, limits);
            Meta meta = parseMeta(metaPayload);
            List<Frame> frames = parseFrames(framePayload, base.frames().size(), base.patterns().length,
                    meta.bankSize(), limits);
            Map<String, Animation> animations = parseAnimations(animPayload, frames.size(), limits);
            Map<String, List<Integer>> appendages = sections.containsKey(APND)
                    ? parseAppendages(sections.get(APND), frames.size(), limits) : Map.of();
            return new PlayableSheet(ggfs, meta, frames, animations, appendages);
        } catch (EOFException failure) {
            throw invalid("truncated container", failure);
        }
    }

    private static byte[] parseBase(byte[] payload, ModInputLimits limits) throws IOException {
        try (DataInputStream in = input(payload)) {
            long length = Integer.toUnsignedLong(in.readInt());
            if (length == 0 || length > limits.maxAssetBytes() || length != in.available())
                throw invalid("BASE embedded length mismatch");
            return in.readNBytes((int) length);
        }
    }

    private static Meta parseMeta(byte[] payload) throws IOException {
        if (payload.length != 12) throw invalid("META length");
        try (DataInputStream in = input(payload)) {
            long basePattern = Integer.toUnsignedLong(in.readInt());
            long bank = Integer.toUnsignedLong(in.readInt());
            int palette = in.readUnsignedByte(), flags = in.readUnsignedByte(), reserved = in.readUnsignedShort();
            if (basePattern > Integer.MAX_VALUE || bank == 0 || bank > Integer.MAX_VALUE
                    || basePattern + bank > Integer.MAX_VALUE || palette > 3
                    || flags != 0 || reserved != 0) throw invalid("invalid META");
            return new Meta((int) basePattern, (int) bank, palette);
        }
    }

    private static List<Frame> parseFrames(byte[] payload, int expectedFrames, int patterns,
                                            int bankSize, ModInputLimits limits) throws IOException {
        try (DataInputStream in = input(payload)) {
            int count = in.readUnsignedShort();
            if (count != expectedFrames || count > limits.maxSheetFrames()) throw invalid("FRAM count mismatch");
            ArrayList<Frame> result = new ArrayList<>(count); long totalRuns = 0;
            for (int i = 0; i < count; i++) {
                int ox = in.readShort(), oy = in.readShort();
                int width = in.readUnsignedShort(), height = in.readUnsignedShort(), runCount = in.readUnsignedShort();
                if (width == 0 || height == 0 || runCount == 0 || (totalRuns += runCount) > limits.maxSheetPieces())
                    throw invalid("invalid playable frame");
                ArrayList<DplcRun> runs = new ArrayList<>(runCount);
                for (int r = 0; r < runCount; r++) {
                    int source = in.readUnsignedShort(), tiles = in.readUnsignedShort(), offset = in.readUnsignedShort();
                    if (tiles == 0 || source + (long) tiles > patterns || offset + (long) tiles > bankSize)
                        throw invalid("DPLC span out of bounds");
                    runs.add(new DplcRun(source, tiles, offset));
                }
                result.add(new Frame(ox, oy, width, height, runs));
            }
            requireEnd(in, "FRAM"); return List.copyOf(result);
        }
    }

    private static Map<String, Animation> parseAnimations(byte[] payload, int frames,
                                                           ModInputLimits limits) throws IOException {
        try (DataInputStream in = input(payload)) {
            int count = in.readUnsignedShort();
            if (count > limits.maxSheetFrames()) throw invalid("animation count exceeds limit");
            LinkedHashMap<String, Animation> result = new LinkedHashMap<>(); String prior = null;
            long totalSteps = 0;
            for (int i = 0; i < count; i++) {
                String key = string(in);
                try { ModKeySyntax.requireLocalName(key); }
                catch (IllegalArgumentException rejected) { throw invalid("invalid animation key", rejected); }
                if (prior != null && compareUtf8(prior, key) >= 0) throw invalid("animation keys not sorted/unique");
                prior = key; int steps = in.readUnsignedShort();
                if (steps == 0 || (totalSteps += steps) > limits.maxSheetPieces()) throw invalid("invalid animation step count");
                ArrayList<AnimationStep> values = new ArrayList<>(steps);
                for (int s = 0; s < steps; s++) {
                    int frame = in.readUnsignedShort(), duration = in.readUnsignedShort();
                    int flags = in.readUnsignedByte(), reserved = in.readUnsignedByte();
                    if (frame >= frames || duration == 0 || (flags & ~1) != 0 || reserved != 0)
                        throw invalid("invalid animation step");
                    values.add(new AnimationStep(frame, duration, (flags & 1) != 0));
                }
                result.put(key, new Animation(values));
            }
            requireEnd(in, "ANIM"); return Collections.unmodifiableMap(result);
        }
    }

    private static Map<String, List<Integer>> parseAppendages(byte[] payload, int frames,
                                                               ModInputLimits limits) throws IOException {
        try (DataInputStream in = input(payload)) {
            int count = in.readUnsignedShort();
            if (count > limits.maxSheetFrames()) throw invalid("appendage count exceeds limit");
            LinkedHashMap<String, List<Integer>> result = new LinkedHashMap<>(); String prior = null;
            long total = 0;
            for (int i = 0; i < count; i++) {
                String key = string(in);
                try { ModKeySyntax.requireDisplayKey(key); }
                catch (IllegalArgumentException rejected) { throw invalid("invalid appendage key", rejected); }
                if (prior != null && compareUtf8(prior, key) >= 0) throw invalid("appendage keys not sorted/unique");
                prior = key; int frameCount = in.readUnsignedShort();
                if (frameCount == 0 || (total += frameCount) > limits.maxSheetPieces()) throw invalid("invalid appendage frame count");
                ArrayList<Integer> indices = new ArrayList<>(frameCount);
                for (int f = 0; f < frameCount; f++) { int index = in.readUnsignedShort(); if (index >= frames) throw invalid("dangling appendage frame"); indices.add(index); }
                result.put(key, List.copyOf(indices));
            }
            requireEnd(in, "APND"); return Collections.unmodifiableMap(result);
        }
    }

    private static void requireOrder(Set<Integer> tags) throws IOException {
        List<Integer> list = new ArrayList<>(tags);
        if (list.size() < 4 || list.get(0) != BASE || list.get(1) != META || list.get(2) != FRAM || list.get(3) != ANIM)
            throw invalid("required sections out of order");
        boolean sawOptionalUnknown = false;
        for (int i = 4; i < list.size(); i++) {
            int tag = list.get(i);
            if (tag == APND) { if (sawOptionalUnknown) throw invalid("APND out of order"); }
            else { sawOptionalUnknown = true; if ((tag & 0x80000000) == 0) throw invalid("unknown required section"); }
        }
    }

    private static byte[] required(Map<Integer, byte[]> sections, int tag) throws IOException { byte[] value = sections.get(tag); if (value == null) throw invalid("missing required section"); return value; }
    private static DataInputStream input(byte[] bytes) { return new DataInputStream(new ByteArrayInputStream(bytes)); }
    private static void requireEnd(DataInputStream in, String section) throws IOException { if (in.read() != -1) throw invalid("trailing " + section + " bytes"); }
    private static String string(DataInputStream in) throws IOException {
        int length = in.readUnsignedShort(); if (length == 0) throw invalid("empty key"); byte[] bytes = in.readNBytes(length); if (bytes.length != length) throw new EOFException();
        try { return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
        catch (CharacterCodingException e) { throw invalid("invalid UTF-8", e); }
    }
    static int compareUtf8(String left, String right) {
        byte[] a = left.getBytes(StandardCharsets.UTF_8), b = right.getBytes(StandardCharsets.UTF_8);
        int length = Math.min(a.length, b.length);
        for (int i = 0; i < length; i++) {
            int compared = Integer.compare(Byte.toUnsignedInt(a[i]), Byte.toUnsignedInt(b[i]));
            if (compared != 0) return compared;
        }
        return Integer.compare(a.length, b.length);
    }
    static int tag(String value) { byte[] b = value.getBytes(StandardCharsets.US_ASCII); return ByteBuffer.wrap(b).getInt(); }
    private static IOException invalid(String message) { return new IOException("Invalid playable sheet: " + message); }
    private static IOException invalid(String message, Throwable cause) { return new IOException("Invalid playable sheet: " + message, cause); }

    @ModApi public record Meta(int basePatternIndex, int bankSize, int paletteLine) {}
    @ModApi public record DplcRun(int sourceTile, int tileCount, int bankOffset) {}
    @ModApi public record Frame(int originX, int originY, int collisionWidth, int collisionHeight, List<DplcRun> runs) { public Frame { runs = List.copyOf(runs); } }
    @ModApi public record AnimationStep(int frameIndex, int duration, boolean loop) {}
    @ModApi public record Animation(List<AnimationStep> steps) { public Animation { steps = List.copyOf(steps); } }
    @ModApi public record PlayableSheet(byte[] baseSheetV1, Meta meta, List<Frame> frames,
            Map<String, Animation> animations, Map<String, List<Integer>> appendages) {
        public PlayableSheet {
            baseSheetV1 = baseSheetV1.clone();
            frames = List.copyOf(frames);
            animations = Collections.unmodifiableMap(new LinkedHashMap<>(animations));
            LinkedHashMap<String, List<Integer>> appendageCopy = new LinkedHashMap<>();
            appendages.forEach((key, value) -> appendageCopy.put(key, List.copyOf(value)));
            appendages = Collections.unmodifiableMap(appendageCopy);
        }
        @Override public byte[] baseSheetV1() { return baseSheetV1.clone(); }
        @Override public boolean equals(Object other) { return other instanceof PlayableSheet that
                && Arrays.equals(baseSheetV1, that.baseSheetV1) && meta.equals(that.meta)
                && frames.equals(that.frames) && animations.equals(that.animations)
                && appendages.equals(that.appendages); }
        @Override public int hashCode() { return Objects.hash(Arrays.hashCode(baseSheetV1), meta, frames, animations, appendages); }
    }
}
