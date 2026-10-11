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
    public enum Kind { MASKED, BALL_STOCK, OCCLUDED_STOCK, NO_HEAD, UNSUPPORTED, EMPTY }
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
    private static final List<PlayerHeadProfile> STOCK = loadAll();
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
    public static PlayerHeadProfile resolve(com.openggf.level.render.SpriteSheet<?> art) {
        String identity=fingerprint(art);
        return STOCK.stream().filter(p->p.fingerprint.equals(identity)).findFirst().orElse(null);
    }
    /** Object identity follows actual mapped ROM pixels, excluding unrelated shared-bank slots. */
    public static String fingerprint(com.openggf.level.render.SpriteSheet<?> art) {
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            put(digest,art.getFrameCount());
            for(int frame=0;frame<art.getFrameCount();frame++) {
                var pieces=art.getFrame(frame).pieces(); put(digest,pieces.size());
                for(var p:pieces) {
                    for(int value:new int[]{p.xOffset(),p.yOffset(),p.widthTiles(),p.heightTiles(),
                            p.hFlip()?1:0,p.vFlip()?1:0}) put(digest,value);
                    for(int index=p.tileIndex();index<p.tileIndex()+p.widthTiles()*p.heightTiles();index++) {
                        if(index<0||index>=art.getPatterns().length) return "";
                        var pattern=art.getPatterns()[index];
                        if(pattern==null) return "";
                        for(int y=0;y<8;y++) for(int x=0;x<8;x++) digest.update((byte)(pattern.getPixel(x,y)&15));
                    }
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch(NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    private static List<PlayerHeadProfile> loadAll() {
        var profiles=new ArrayList<>(List.of(load("s1"),load("s2"),load("s3k")));
        try(var stream=PlayerHeadProfile.class.getResourceAsStream("/presentation/heads/profiles.list")) {
            if(stream!=null) {
                var reader=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8));
                for(String line;(line=reader.readLine())!=null;) {
                    line=line.strip(); if(!line.isBlank()&&!line.startsWith("#")) profiles.add(load(line));
                }
            }
        } catch(java.io.IOException failure) { throw new IllegalStateException("Head profile inventory unavailable",failure); }
        return List.copyOf(profiles);
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
                        case OCCLUDED_STOCK -> "Native foreground obscures head anatomy; stock pixels retained";
                        case NO_HEAD -> "Appendage, projectile or machinery has no character head";
                        default -> "Uncurated aerial, special or transformed pose; stock pixels retained";
                    })); continue;
                }
                String[] regions = fields[5].split("\\s*\\|\\s*");
                var head = region(fields[4], regions[0]);
                var foreground = new ArrayList<com.openggf.graphics.SpritePresentation.MaskRegion>();
                for (int i = 1; i < regions.length; i++) {
                    String[] parts = regions[i].split("\\s+", 2);
                    foreground.add(region(parts[0], parts[1]));
                }
                var mask = new com.openggf.graphics.SpritePresentation.HeadMask(Integer.parseInt(fields[2]),
                        Integer.parseInt(fields[3]), head.pieces(), head.polygon(), foreground);
                frames.add(new FrameHead(kind, mask, "Reviewed ROM head"));
            }
            if (fingerprint == null || frames.isEmpty()) throw new IllegalStateException("Invalid head profile " + id);
            return new PlayerHeadProfile(id, fingerprint, frames);
        } catch (java.io.IOException failure) { throw new IllegalStateException("Head metadata unavailable", failure); }
    }
    private static com.openggf.graphics.SpritePresentation.MaskRegion region(String pieceIds, String contour) {
        var pieces = new java.util.HashSet<Integer>();
        for (String value : pieceIds.split(",")) pieces.add(Integer.parseInt(value));
        var polygon = new ArrayList<com.openggf.graphics.SpritePresentation.MaskPoint>();
        for (String pair : contour.split(";")) {
            String[] xy = pair.split(",");
            polygon.add(new com.openggf.graphics.SpritePresentation.MaskPoint(
                    Integer.parseInt(xy[0]), Integer.parseInt(xy[1])));
        }
        if (polygon.size() < 3 || pieces.isEmpty()) throw new IllegalStateException("Empty anatomy region");
        return new com.openggf.graphics.SpritePresentation.MaskRegion(pieces, polygon);
    }

}
