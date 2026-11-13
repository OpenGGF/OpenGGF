package com.openggf.game.presentation;

import com.openggf.game.StageSpriteSet;
import com.openggf.level.Pattern;
import com.openggf.level.render.*;
import com.openggf.sprites.art.SpriteArtSet;
import java.util.*;

/** Converts bounded indexed stage frames to ordinary engine mappings and DPLCs. */
public final class StageSpriteCompiler {
    private StageSpriteCompiler() { }
    /** Compiler slot only; never write this value into a native player selector. */
    public static int orientedFrameIndex(StageSpriteSet set, int nativeFrame,
                                         boolean horizontalFlip, boolean verticalFlip) {
        if (set == null || nativeFrame < 0 || nativeFrame > 255) return nativeFrame;
        int slot = nativeFrame + (horizontalFlip ? 256 : 0) + (verticalFlip ? 512 : 0);
        return set.frames().containsKey(slot) ? slot : nativeFrame;
    }

    public static SpriteArtSet compile(StageSpriteSet set, SpriteArtSet animationSource) {
        return compile(set, animationSource, com.openggf.graphics.PatternAtlasRange.CHARACTER_STAGE_PRESENTATION.base());
    }
    public static SpriteArtSet compile(StageSpriteSet set, SpriteArtSet animationSource, int patternBase) {
        int perActorBudget = com.openggf.graphics.PatternAtlasRange.CHARACTER_STAGE_PRESENTATION.size() / 2;
        long tileCount = set.frames().values().stream().mapToLong(frame ->
                ((frame.image().width() + 7L) / 8) * ((frame.image().height() + 7L) / 8)).sum();
        if (tileCount > perActorBudget) throw new IllegalArgumentException("Character stage art exceeds per-actor pattern budget");
        int size = set.frames().keySet().stream().mapToInt(Integer::intValue).max().orElseThrow() + 1;
        var tiles = new ArrayList<Pattern>();
        var maps = new ArrayList<SpriteMappingFrame>();
        var cues = new ArrayList<SpriteDplcFrame>();
        int bank = 1;
        for (int id = 0; id < size; id++) {
            var frame = set.frames().get(id);
            var pieces = new ArrayList<SpriteMappingPiece>();
            int start = tiles.size();
            if (frame != null) {
                var image = frame.image();
                byte[] pixels = image.pixels();
                // Split into legal 4x4-tile pieces, retaining authored hotspot and palette indices.
                for (int x = 0; x < image.width(); x += 32) {
                    for (int y = 0; y < image.height(); y += 32) {
                        int w = Math.min(4, (image.width() - x + 7) / 8);
                        int h = Math.min(4, (image.height() - y + 7) / 8);
                        pieces.add(new SpriteMappingPiece(x-frame.hotspotX(), y-frame.hotspotY(),
                                w,h,tiles.size()-start,false,false,0,true));
                        for (int col=0; col<w; col++) for (int row=0; row<h; row++) {
                            var tile = new Pattern();
                            for (int px=0; px<8; px++) for (int py=0; py<8; py++) {
                                int sx=x+col*8+px, sy=y+row*8+py;
                                if(sx<image.width() && sy<image.height()) tile.setPixel(px,py,pixels[sy*image.width()+sx]);
                            }
                            tiles.add(tile);
                        }
                    }
                }
            }
            int count=tiles.size()-start;
            bank=Math.max(bank,count);
            maps.add(new SpriteMappingFrame(List.copyOf(pieces)));
            cues.add(new SpriteDplcFrame(count==0 ? List.of() : List.of(new TileLoadRequest(start,count))));
        }
        return new SpriteArtSet(tiles.toArray(Pattern[]::new),List.copyOf(maps),List.copyOf(cues),
                0,patternBase,1,bank,animationSource==null?null:animationSource.animationProfile(),
                animationSource==null?null:animationSource.animationSet());
    }
}
