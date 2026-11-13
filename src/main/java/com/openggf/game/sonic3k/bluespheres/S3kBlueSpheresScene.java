package com.openggf.game.sonic3k.bluespheres;

import com.openggf.game.competition.NativeMenuRaster;
import com.openggf.graphics.SpriteSatEntry;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;

import java.util.List;

/**
 * One displayed Blue Spheres frame: VRAM patterns, both 64x32 planes with
 * per-line horizontal scroll, full-screen vertical scroll, the SAT and CRAM.
 */
record S3kBlueSpheresScene(Pattern[] vram, int[] planeA, int[] planeB, int[] hScrollA, int[] hScrollB,
                           int vScrollA, int vScrollB, List<SpriteSatEntry> sat, Palette[] cram) {
    S3kBlueSpheresScene {
        planeA = planeA.clone();
        planeB = planeB.clone();
        hScrollA = hScrollA == null ? null : hScrollA.clone();
        hScrollB = hScrollB == null ? null : hScrollB.clone();
        sat = List.copyOf(sat);
    }

    NativeMenuRaster.Picture rasterize() {
        int w = S3kBlueSpheresAssets.PLANE_WIDTH;
        int h = S3kBlueSpheresAssets.PLANE_HEIGHT;
        return NativeMenuRaster.compose(
                new NativeMenuRaster.Plane(w, h, planeA, 0, vScrollA, hScrollA),
                new NativeMenuRaster.Plane(w, h, planeB, 0, vScrollB, hScrollB),
                vram, sat, false, 0);
    }
}
