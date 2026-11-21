package com.openggf.game.soniccd.objects.common;

import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.objects.art.CdStageVram;

import java.util.List;
import java.util.function.IntUnaryOperator;

/** Disc-backed art for the ordinary orbit and single-landing hop routines. */
public record CdAnimalArt(int flyingTile, int groundTile, int groundRadius, int flyingAnimations,
                          IntUnaryOperator sectionAt) {
    private static final String FLYING = "cd.animal.flying.";
    private static final String GROUND = "cd.animal.ground.";

    public static void register(CdObjectArtProvider art, int baseTiles, int flyingMappings, int groundMappings,
                                int flyingAnimations, int flyingPriorityBit, int groundRadius,
                                List<CdStageVram> sections, IntUnaryOperator sectionAt) {
        if (baseTiles < 0) return;
        var stage = art.payload().stage();
        // ObjAnimal_SetBaseTile (Level/Palmtree Panic/Objects/Animal/Main.asm:209-217).
        int index = (stage.actNumber() - 1) * 4 + CdLevel.nativeTimeZone(stage.period());
        int tile = art.image().u16(baseTiles + index * 2);
        int flyingTile = tile | flyingPriorityBit;
        var reader = art.image().reader();
        for (int i = 0; i < sections.size(); i++) {
            if (flyingMappings >= 0) {
                art.register(FLYING + i, art.sheet(reader, flyingMappings, flyingTile, sections.get(i)));
            }
            if (groundMappings >= 0) {
                art.register(GROUND + i, art.sheet(reader, groundMappings, tile, sections.get(i)));
            }
        }
        art.zoneArt(CdAnimalArt.class, () -> new CdAnimalArt(flyingTile, tile, groundRadius,
                flyingAnimations, sectionAt));
    }

    String sheet(boolean flying, int cameraX) {
        return (flying ? FLYING : GROUND) + sectionAt.applyAsInt(cameraX);
    }
}
