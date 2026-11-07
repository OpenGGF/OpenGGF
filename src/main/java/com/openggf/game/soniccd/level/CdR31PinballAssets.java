package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.objects.CdObjectAnimation;
import java.util.Objects;
import java.util.Optional;

/** Finite borrowed original-media views. Construction authenticates nothing and creates no source owner.
 * Root supplies each independently authenticated programme's operands; no A-to-B/C/D relocation.
 * N=1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, CC Bumper/Flipper, Glass, Platform, Pocket data tails.
 */
public record CdR31PinballAssets(CdNativeFile programme, Visual bumper, Visual flipper,
        Visual glass, Visual platform, Visual pocketParent, Visual pocketChild,
        CdAssetBytes flipperSlopeReadHull) {
    public record Visual(CdObjectVisualAssets visual, int mappingAddress,
                         Optional<CdObjectAnimation.AnimationTable> animation) {
        public Visual { Objects.requireNonNull(visual); Objects.requireNonNull(animation); }
    }
    public CdR31PinballAssets {
        Objects.requireNonNull(programme);
        if(programme!=CdNativeFile.R31A && programme!=CdNativeFile.R31B
                && programme!=CdNativeFile.R31C && programme!=CdNativeFile.R31D)
            throw new IllegalArgumentException("Pinball source must be an actual R31 programme");
        Objects.requireNonNull(bumper);Objects.requireNonNull(flipper);Objects.requireNonNull(glass);
        Objects.requireNonNull(platform);Objects.requireNonNull(pocketParent);Objects.requireNonNull(pocketChild);
        Objects.requireNonNull(flipperSlopeReadHull);
        Visual[] views={bumper,flipper,glass,platform,pocketParent,pocketChild};
        int[] frames={3,3,2,1,4,2};
        for(int n=0;n<views.length;n++)if(views[n].visual().mappingFrames().size()!=frames[n])
            throw new IllegalArgumentException("Native pinball mapping frame count differs at "+n);

        // Mirrored left endpoint reads column64: top reads bottom[0], bottom reads BumperAnims[0].
        if(flipperSlopeReadHull.length()!=129)throw new IllegalArgumentException("Exact 129-byte reached slope hull required");
        if(bumper.animation().isEmpty()||flipper.animation().isEmpty()||glass.animation().isEmpty()
                ||pocketChild.animation().isEmpty()||platform.animation().isPresent()||pocketParent.animation().isPresent())
            throw new IllegalArgumentException("Native animator ownership differs");
        if(bumper.animation().orElseThrow().scriptCount()!=2||flipper.animation().orElseThrow().scriptCount()!=2
                ||glass.animation().orElseThrow().scriptCount()!=1||pocketChild.animation().orElseThrow().scriptCount()!=1)
            throw new IllegalArgumentException("Native pinball animation directory count differs");
    }
    public int flipperSlope(boolean bottom,int column) {
        if(column<0||column>64)throw new IllegalArgumentException("Native slope column outside0..64");
        return flipperSlopeReadHull.signedByte((bottom?64:0)+column);
    }
}
