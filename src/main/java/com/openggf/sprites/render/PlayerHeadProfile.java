package com.openggf.sprites.render;

import com.openggf.sprites.art.SpriteArtSet;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/** Reviewed anatomy metadata; pixels always come from the resolved production ROM bank.
 * Origin: native Mutator Lab expansion, 2026-10-08. No pose/piece-position inference. */
public final class PlayerHeadProfile {
    public enum Kind { MASKED, BALL_STOCK, UNSUPPORTED, EMPTY }
    public record Point(int x, int y) { }
    public record FrameHead(Kind kind, com.openggf.graphics.SpritePresentation.HeadMask mask, String reason) {
        public FrameHead(Kind kind, int anchorX, int anchorY, Set<Integer> pieces, List<Point> polygon, String reason) {
            this(kind, new com.openggf.graphics.SpritePresentation.HeadMask(anchorX, anchorY, pieces,
                    polygon.stream().map(p -> new com.openggf.graphics.SpritePresentation.MaskPoint(p.x(), p.y())).toList()), reason);
        }
        public int anchorX() { return mask.anchorX(); }
        public int anchorY() { return mask.anchorY(); }
        public Set<Integer> pieces() { return mask.pieces(); }
        /** Source pixel centres, in unflipped mapping coordinates. */
        public boolean contains(int piece, int x, int y) { return kind == Kind.MASKED && mask.contains(piece, x, y); }
    }
    private static final FrameHead UNKNOWN = new FrameHead(Kind.UNSUPPORTED, 0, 0, Set.of(), List.of(),
            "This art/pose has no reviewed head mask; stock pixels retained");
    private static final List<PlayerHeadProfile> STOCK = List.of(load("s1"), load("s2"), load("s3k"));
    private final String id, fingerprint;
    private final List<FrameHead> frames;
    private PlayerHeadProfile(String id, String fingerprint, List<FrameHead> frames) {
        this.id = id; this.fingerprint = fingerprint; this.frames = List.copyOf(frames);
    }
    public String id() { return id; }
    public int frameCount() { return frames.size(); }
    public FrameHead frame(int index) { return index >= 0 && index < frames.size() ? frames.get(index) : UNKNOWN; }
    public static FrameHead unsupported() { return UNKNOWN; }
    public static PlayerHeadProfile resolve(SpriteArtSet art) {
        String identity = fingerprint(art);
        return STOCK.stream().filter(p -> p.fingerprint.equals(identity)).findFirst().orElse(null);
    }
    /** Excludes virtual bank addresses/palette context, so duplicate-character banks share metadata safely. */
    public static String fingerprint(SpriteArtSet art) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            put(digest, art.artTiles().length);
            for (var pattern : art.artTiles()) for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++)
                digest.update((byte) (pattern.getPixel(x, y) & 15));
            put(digest, art.mappingFrames().size());
            for (var frame : art.mappingFrames()) {
                put(digest, frame.pieces().size());
                for (var p : frame.pieces()) {
                    for (int value : new int[]{p.xOffset(), p.yOffset(), p.widthTiles(), p.heightTiles(),
                            p.tileIndex(), p.hFlip() ? 1 : 0, p.vFlip() ? 1 : 0, p.paletteIndex(), p.priority() ? 1 : 0}) put(digest, value);
                }
            }
            put(digest, art.dplcFrames().size());
            for (var frame : art.dplcFrames()) {
                put(digest, frame.requests().size());
                for (var request : frame.requests()) {
                    put(digest, request.startTile()); put(digest, request.count()); put(digest, request.destinationOffset());
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    private static void put(MessageDigest digest, int value) {
        for (int shift = 24; shift >= 0; shift -= 8) digest.update((byte) (value >>> shift));
    }
    private static PlayerHeadProfile load(String id) {
        String resource = "/presentation/heads/" + id + ".txt";
        try (var stream = PlayerHeadProfile.class.getResourceAsStream(resource)) {
            if (stream == null) throw new IllegalStateException("Missing head metadata " + resource);
            var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            String fingerprint = null;
            List<FrameHead> frames = new ArrayList<>();
            for (String line; (line = reader.readLine()) != null;) {
                line = line.strip(); if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.startsWith("fingerprint=")) { fingerprint = line.substring(12); continue; }
                String[] fields = line.split("\\s+", 6);
                int frame = Integer.parseInt(fields[0], 16);
                if (frame != frames.size()) throw new IllegalStateException("Non-contiguous head frame " + line);
                Kind kind = Kind.valueOf(fields[1]);
                if (kind != Kind.MASKED) {
                    frames.add(new FrameHead(kind, 0, 0, Set.of(), List.of(), switch (kind) {
                        case BALL_STOCK -> "Ball pose has no separate neck; stock pixels retained";
                        case EMPTY -> "Native empty frame";
                        default -> "Uncurated aerial, special or transformed pose; stock pixels retained";
                    })); continue;
                }
                var pieces = new java.util.HashSet<Integer>();
                for (String value : fields[4].split(",")) pieces.add(Integer.parseInt(value));
                List<Point> polygon = new ArrayList<>();
                for (String pair : fields[5].split(";")) {
                    String[] xy = pair.split(","); polygon.add(new Point(Integer.parseInt(xy[0]), Integer.parseInt(xy[1])));
                }
                if (polygon.size() < 3 || pieces.isEmpty()) throw new IllegalStateException("Empty head mask " + line);
                frames.add(new FrameHead(kind, Integer.parseInt(fields[2]), Integer.parseInt(fields[3]), pieces, polygon, "Reviewed normal Sonic head"));
            }
            if (fingerprint == null || frames.isEmpty()) throw new IllegalStateException("Invalid head profile " + id);
            return new PlayerHeadProfile(id, fingerprint, frames);
        } catch (java.io.IOException failure) { throw new IllegalStateException("Head metadata unavailable", failure); }
    }
}
