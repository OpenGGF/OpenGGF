package com.openggf.game.sonic3k.competition;

import com.openggf.game.competition.NativeMenuRaster;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.SpriteSatEntry;

import com.openggf.game.competition.NativeMenuRasterRenderer;

import java.util.ArrayList;

/** Draws the domain's ROM menu scene without interpreting its object tables or mappings. */
public final class S3kCompetitionMenuRenderer implements AutoCloseable {
    private final NativeMenuRasterRenderer renderer;

    public S3kCompetitionMenuRenderer(GraphicsManager graphics) {
        this.renderer = new NativeMenuRasterRenderer(graphics);
    }

    public static NativeMenuRaster.Picture rasterize(S3kCompetitionMenuScene.Scene scene) {
        var sat = new ArrayList<SpriteSatEntry>();
        for (var group : scene.sprites()) for (var piece : group.pieces()) {
            sat.add(SpriteSatEntry.of(group.x() + piece.xOffset(), group.y() + piece.yOffset(),
                    piece.widthTiles(), piece.heightTiles(), piece.tileIndex(), piece.paletteIndex(),
                    piece.hFlip(), piece.vFlip(), piece.priority(), false));
        }
        return NativeMenuRaster.compose(
                new NativeMenuRaster.Plane(scene.planeWidth(), scene.planeHeight(), scene.planeA(),
                        scene.horizontalScrollA(), scene.verticalScrollA()),
                new NativeMenuRaster.Plane(scene.planeWidth(), scene.planeHeight(), scene.planeB(),
                        scene.horizontalScrollB(), scene.verticalScrollB()),
                scene.patterns(), sat, scene.shadowHighlight(), scene.backdropColorIndex(), scene.portraitBanks(), scene.portraitPalettes());
    }

    public void draw(S3kCompetitionMenuScene.Scene scene) {
        renderer.draw(rasterize(scene), scene.palettes());
    }

    @Override public void close() {
        renderer.close();
    }
}
