package com.openggf.game.sonic3k;

import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * ROM {@code Obj_HyperSonicKnux_Trail} history selection. Position and the
 * Stat_table byte copied into art_tile are delayed together; mapping, render
 * flags and numeric priority remain live.
 */
public record HyperFormTrailSample(
        int centreX,
        int centreY,
        byte historicalArtTileAttribute,
        int mappingFrame,
        boolean horizontalFlip,
        boolean verticalFlip,
        int priorityBucket) {

    public boolean highPriority() {
        // 68k byte write to art_tile targets the word's high byte; bit 7 of
        // the recorded Stat_table byte therefore becomes the VDP priority bit.
        return (historicalArtTileAttribute & 0x80) != 0;
    }

    public static HyperFormTrailSample sample(
            AbstractPlayableSprite player, int levelFrameCounter) {
        int framesBehind = (levelFrameCounter & 1) == 0 ? 3 : 5;
        return new HyperFormTrailSample(
                player.getCentreX(framesBehind),
                player.getCentreY(framesBehind),
                player.getArtTileAttributeHistory(framesBehind),
                player.getMappingFrame(),
                player.getRenderHFlip(),
                player.getRenderVFlip(),
                player.getPriorityBucket());
    }
}
