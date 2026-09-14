package com.openggf.game.sonic2.kis2;

import com.openggf.game.sonic2.continuescreen.Sonic2ContinueScreenProvider;
import com.openggf.level.Palette;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Knuckles;
import java.io.IOException;
import java.util.Objects;

/** KiS2 ObjDB: Knuckles shadow-boxes, then walks; no Sidekick slot is created. */
public final class Kis2ContinuePresentation implements Sonic2ContinueScreenProvider.PlayerPresentation {
    private final Kis2PlayerArt art;

    public Kis2ContinuePresentation(Kis2PlayerArt art) {
        this.art = Objects.requireNonNull(art);
    }

    @Override public SpriteArtSet loadArt() throws IOException { return art.loadKnuckles(); }
    @Override public AbstractPlayableSprite createSprite() { return new Knuckles("knuckles", (short) 0, (short) 0); }
    @Override public Palette palette() { return art.loadKnucklesPalette(); }
    @Override public int waitingAnimation() { return 0x24; }
    @Override public int departingAnimation() { return 0; }
}
