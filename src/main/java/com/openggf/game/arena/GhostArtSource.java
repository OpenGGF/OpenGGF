package com.openggf.game.arena;

import com.openggf.level.Palette;
import com.openggf.sprites.art.SpriteArtSet;
import java.io.IOException;

/** Track-selected ghost art and display origin; presentation only. */
public interface GhostArtSource {
    SpriteArtSet art(String character) throws IOException;
    Palette palette(String character);
    default int cameraX() { return 0; }
    default int cameraY() { return 0; }
    /** Screen anchor of a streamed ghost position; a magnified view scales the offset, not the art. */
    default int screenX(int worldX) { return worldX - cameraX(); }
    default int screenY(int worldY) { return worldY - cameraY(); }
    /** Where a streamed frame's art is drawn; a source may correct for art drawn at another scale. */
    default int frameScreenX(String character, com.openggf.ghost.GhostFrame frame) { return screenX(frame.x()); }
    default int frameScreenY(String character, com.openggf.ghost.GhostFrame frame) { return screenY(frame.y()); }
    /** Frame of {@link #art} that presents a streamed mapping frame; the stream itself never changes. */
    default int artFrame(String character, int streamedFrame) { return streamedFrame; }
}
