package com.openggf.game.sonic3k.ending;

import com.openggf.data.Rom;
import com.openggf.game.sonic3k.Sonic3kPlcArtRegistry.StandaloneArtEntry;
import com.openggf.game.sonic3k.Sonic3kPlcArtRegistry.DplcLayout;
import com.openggf.game.sonic3k.S3kSpriteDataLoader;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.level.resources.CompressionType;
import com.openggf.util.PatternDecompressor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** ROM-backed ending sheets; queue submission remains with native scene owners. */
public final class S3kEndingArt {
    public static final String FONT = "s3k_ending_font";
    public static final String S3_BANNER = "s3k_ending_s3_banner";
    public static final String SK_BANNER = "s3k_ending_sk_banner";
    public static final String AND_KNUCKLES = "s3k_ending_and_knuckles";
    public static final String KNUCKLES_POSE = "s3k_ending_knuckles_pose";
    public static final String S3_GRAPHICS = "s3k_ending_s3_graphics";
    public static final String EGGMAN = "s3k_ending_eggman";
    public static final String EGG_ROBO = "s3k_ending_egg_robo";
    public static final String EYECATCH_KNUCKLES = "s3k_ending_eyecatch_knuckles";

    private S3kEndingArt() { }
    public static List<StandaloneArtEntry> terminalEntries() {
        return List.of(
                kos(S3_BANNER, S3kEndingResources.S3_BANNER, S3kEndingResources.MAP_S3_BANNER, 1),
                kos(SK_BANNER, S3kEndingResources.SK_BANNER, S3kEndingResources.MAP_SK_BANNER, 1),
                kos(AND_KNUCKLES, S3kEndingResources.AND_KNUCKLES, S3kEndingResources.MAP_AND_KNUCKLES, 1),
                nem(KNUCKLES_POSE, S3kEndingResources.KNUCKLES_POSE, S3kEndingResources.MAP_KNUCKLES_POSE, 0),
                nem(S3_GRAPHICS, S3kEndingResources.S3_ENDING_GRAPHICS, S3kEndingResources.MAP_S3_ENDING_GRAPHICS, 1),
                kos(EGGMAN, S3kEndingResources.EGGMAN_EYECATCH, S3kEndingResources.MAP_EGGMAN_EYECATCH, 1),
                kos(EGG_ROBO, S3kEndingResources.EGG_ROBO_EYECATCH, S3kEndingResources.MAP_EGG_ROBO_EYECATCH, 1),
                new StandaloneArtEntry(EYECATCH_KNUCKLES,Sonic3kConstants.ART_UNC_KNUCKLES_ADDR,
                        CompressionType.UNCOMPRESSED,Sonic3kConstants.ART_UNC_KNUCKLES_SIZE,
                        Sonic3kConstants.MAP_KNUCKLES_ADDR,2,Sonic3kConstants.DPLC_KNUCKLES_ADDR,
                        S3kSpriteDataLoader.MappingFormat.STANDARD,-1,0,DplcLayout.PLAYER));
    }
    private static StandaloneArtEntry kos(String key, int art, int map, int palette) {
        return new StandaloneArtEntry(key, art, CompressionType.KOSINSKI_MODULED, 0, map, palette, -1);
    }
    private static StandaloneArtEntry nem(String key, int art, int map, int palette) {
        return new StandaloneArtEntry(key, art, CompressionType.NEMESIS, 0, map, palette, -1);
    }
    /** One frame per native font tile; the glyph arrangement is decoded by sub_5B36C. */
    public static ObjectSpriteSheet font(Rom rom) throws IOException {
        var patterns = PatternDecompressor.nemesis(rom, S3kEndingResources.CREDIT_FONT);
        var frames = new ArrayList<SpriteMappingFrame>(patterns.length);
        for (int tile = 0; tile < patterns.length; tile++)
            frames.add(new SpriteMappingFrame(List.of(new SpriteMappingPiece(
                    0, 0, 1, 1, tile, false, false, 0, true))));
        return new ObjectSpriteSheet(patterns, frames, 0, 1);
    }
}
