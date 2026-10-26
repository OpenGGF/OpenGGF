package com.openggf.level.objects;

import com.openggf.game.sonic1.objects.Sonic1PointsObjectInstance;
import com.openggf.game.sonic2.objects.PointsObjectInstance;
import com.openggf.game.sonic3k.objects.Sonic3kPointsObjectInstance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Obj_EnemyScore's object header is consumed before its ROM-backed mapping pieces are drawn. */
class TestS3kEnemyScorePresentation {
    private static final ObjectSpawn SPAWN = new ObjectSpawn(0x100, 0x200, 0x29, 0, 0, false, 0);

    @ParameterizedTest
    @CsvSource({"10,4", "100,0", "200,1", "500,2", "1000,3"})
    void nativeEnemyScoreHeaderSelectsBucketOneAndHighPlanePriority(int points, int mappingFrame) {
        var popup = new Sonic3kPointsObjectInstance(SPAWN, services(), points);
        var unified = buckets();var low = buckets();var high = buckets();
        ObjectRenderParts.addToRenderBuckets(popup, unified, low, high);
        assertAll(
                () -> assertEquals(1, popup.getPriorityBucket(), "Obj_EnemyScore priority=$80"),
                () -> assertTrue(popup.isHighPriority(), "make_art_tile(ArtTile_StarPost,0,1)"),
                () -> assertEquals(1, high[1].size(), "actual render consumer selects the native bucket and plane class"),
                () -> assertEquals(0, low[0].size(), "the inherited default must not route S3K points to bucket 0/low"),
                () -> assertEquals(mappingFrame, popup.scoreFrame, "existing score-to-mapping selection is preserved"),
                () -> assertEquals(-0x300, popup.yVel, "presentation does not change native popup motion"));
    }

    @Test
    void s1AndS2PointsKeepTheirExistingPresentationContract() {
        for (var popup : List.of(new Sonic1PointsObjectInstance(SPAWN, services(), 100),
                new PointsObjectInstance(SPAWN, services(), 100))) {
            assertEquals(0, popup.getPriorityBucket());
            assertFalse(popup.isHighPriority());
            assertEquals(0, popup.scoreFrame);
            assertEquals(-0x300, popup.yVel);
        }
    }

    private static ObjectServices services() {
        var services = mock(ObjectServices.class);
        when(services.renderManager()).thenReturn(mock(ObjectRenderManager.class));
        return services;
    }

    @SuppressWarnings("unchecked")
    private static List<ObjectRenderParts.RenderEntry>[] buckets() {
        var result = (List<ObjectRenderParts.RenderEntry>[]) new List<?>[8];
        for (int index = 0; index < result.length; index++) result[index] = new ArrayList<>();
        return result;
    }
}
