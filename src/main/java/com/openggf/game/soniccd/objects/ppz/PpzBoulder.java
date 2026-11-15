package com.openggf.game.soniccd.objects.ppz;

import com.openggf.game.soniccd.objects.common.CdBoulder;
import com.openggf.level.objects.CameraRelativeDraw;
import com.openggf.level.objects.ObjectSpawn;

/** Palmtree Panic's common boulder routine with the current section's loaded art. */
// Inherited draw calls boulderArtKey virtually; camera X selects art, not screen coordinates.
@CameraRelativeDraw(CameraRelativeDraw.Use.ART_SELECTION)
public final class PpzBoulder extends CdBoulder {
    public PpzBoulder(ObjectSpawn spawn) {
        super(spawn);
    }

    @Override
    protected String boulderArtKey() {
        return PpzObjectArt.key(artProvider(), PpzObjectArt.PPZ_BOULDER, tileWord,
                services().camera().getX());
    }
}
