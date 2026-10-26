package com.openggf.level.objects;

import com.openggf.game.sonic1.objects.Sonic1PointsObjectInstance;
import com.openggf.game.sonic2.objects.PointsObjectInstance;
import com.openggf.game.sonic3k.objects.Sonic3kPointsObjectInstance;
import com.openggf.level.render.PatternSpriteRenderer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Native chain offsets 0/2/4/6/$A become frames 0/1/2/3/5:
 * Obj28_InitRandom (s2.asm:24628-24635), Map_obj29 (mappings/sprite/obj29.asm:1-8),
 * loc_2C940 (sonic3k.asm:61121-61128),
 * Map_EnemyScore_ (General/Sprites/Enemy Misc/Map - Enemy Points.asm:1-8),
 * and Map_Poi_internal (_maps/Points.asm:4-11). Check the actual draw payload;
 * no GL context or pixel equivalence is claimed.
 */
class TestEnemyChainPointsMapping {
    @ParameterizedTest
    @CsvSource({
            "S1,100,0", "S1,200,1", "S1,500,2", "S1,1000,3", "S1,10000,5",
            "S2,100,0", "S2,200,1", "S2,500,2", "S2,1000,3", "S2,10000,5",
            "S3K,100,0", "S3K,200,1", "S3K,500,2", "S3K,1000,3", "S3K,10000,5"
    })
    void constructorPassesNativeMappingFrameToTheDrawConsumer(String game, int score, int frame) {
        var renderer = mock(PatternSpriteRenderer.class);
        var popup = popup(game, score, renderer);

        popup.appendRenderCommands(new ArrayList<>());

        verify(renderer).drawFrameIndex(frame, 0x100, 0x120, false, false);
    }

    @ParameterizedTest
    @CsvSource({
            "S1,100,0", "S1,200,1", "S1,500,2", "S1,1000,3", "S1,10000,5",
            "S2,100,0", "S2,200,1", "S2,500,2", "S2,1000,3", "S2,10000,5",
            "S3K,100,0", "S3K,200,1", "S3K,500,2", "S3K,1000,3", "S3K,10000,5"
    })
    void scoreReplacementPassesNativeMappingFrameToTheDrawConsumer(String game, int score, int frame) {
        var renderer = mock(PatternSpriteRenderer.class);
        // Start with a different frame so retaining the constructor's selection fails.
        var popup = popup(game, score == 100 ? 200 : 100, renderer);

        popup.setScore(score);
        popup.appendRenderCommands(new ArrayList<>());

        verify(renderer).drawFrameIndex(frame, 0x100, 0x120, false, false);
    }

    private static AbstractPointsObjectInstance popup(String game, int score, PatternSpriteRenderer renderer) {
        var services = mock(ObjectServices.class);
        var art = mock(ObjectRenderManager.class);
        when(services.renderManager()).thenReturn(art);
        when(art.getPointsRenderer()).thenReturn(renderer);
        var spawn = new ObjectSpawn(0x100, 0x120, 0x29, 0, 0, false, 0);
        return switch (game) {
            case "S1" -> new Sonic1PointsObjectInstance(spawn, services, score);
            case "S2" -> new PointsObjectInstance(spawn, services, score);
            case "S3K" -> new Sonic3kPointsObjectInstance(spawn, services, score);
            default -> throw new IllegalArgumentException(game);
        };
    }
}
