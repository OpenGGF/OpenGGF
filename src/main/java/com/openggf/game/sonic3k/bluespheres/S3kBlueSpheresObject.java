package com.openggf.game.sonic3k.bluespheres;

import com.openggf.graphics.SpriteSatEntry;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;

import java.util.ArrayList;
import java.util.List;

/**
 * One object slot of the Blue Spheres screens: the SST fields their routines
 * use. Positions are native sprite coordinates ({@code $80} is the left/top
 * screen edge); {@code x}/{@code y} keep a 16-bit fraction for MoveSprite2.
 */
final class S3kBlueSpheresObject {
    /** Per-frame code: the slot's {@code (a0)} code pointer. */
    interface Routine {
        void run(S3kBlueSpheresObject self);
    }

    Routine routine;
    List<SpriteMappingFrame> mappings;
    int artTile;
    int priority;
    int x;
    int y;
    int xVel;
    int yVel;
    int frame;
    boolean xFlip;
    /** {@code anim}/{@code prev_anim}/{@code anim_frame}/{@code anim_frame_timer}. */
    int anim;
    int prevAnim;
    int animFrame;
    int animTimer;
    /** Object scratch words ({@code $30(a0)} and friends). */
    int scratch;
    int scratch2;
    /** Set when the routine called Draw_Sprite this frame. */
    boolean drawn;

    int xPos() {
        return x >> 16;
    }

    int yPos() {
        return y >> 16;
    }

    void setX(int pixels) {
        x = pixels << 16;
    }

    void setY(int pixels) {
        y = pixels << 16;
    }

    void draw() {
        drawn = true;
    }

    /** MoveSprite2: position += velocity * 256, no gravity. */
    void moveSprite2() {
        x += xVel << 8;
        y += yVel << 8;
    }

    void clear() {
        routine = null;
        mappings = null;
        artTile = priority = x = y = xVel = yVel = frame = 0;
        xFlip = false;
        anim = prevAnim = animFrame = animTimer = 0;
        scratch = scratch2 = 0;
        drawn = false;
    }

    /**
     * Render_Sprites for one screen-space object: each mapping piece offset
     * from the object, the art tile word added, and the object's x-flip
     * mirroring the piece.
     */
    void appendSat(List<SpriteSatEntry> sat) {
        if (mappings == null || frame < 0 || frame >= mappings.size()) return;
        int screenX = xPos() - 0x80;
        int screenY = yPos() - 0x80;
        int base = artTile & 0x7FF;
        int palette = (artTile >>> 13) & 3;
        boolean high = (artTile & 0x8000) != 0;
        for (SpriteMappingPiece piece : mappings.get(frame).pieces()) {
            int width = piece.widthTiles() * 8;
            int px = xFlip ? screenX - piece.xOffset() - width : screenX + piece.xOffset();
            sat.add(SpriteSatEntry.of(px, screenY + piece.yOffset(),
                    piece.widthTiles(), piece.heightTiles(), (piece.tileIndex() + base) & 0x7FF,
                    (piece.paletteIndex() + palette) & 3, piece.hFlip() ^ xFlip, piece.vFlip(),
                    piece.priority() || high, false));
        }
    }

    /** Render_Sprites' priority-level order: level 0 first, slot order within a level. */
    static List<SpriteSatEntry> render(S3kBlueSpheresObject[] slots) {
        List<SpriteSatEntry> sat = new ArrayList<>();
        for (int level = 0; level < 8; level++) {
            for (S3kBlueSpheresObject object : slots) {
                if (object.routine != null && object.drawn && ((object.priority >>> 7) & 7) == level) {
                    object.appendSat(sat);
                }
            }
        }
        return sat;
    }
}
