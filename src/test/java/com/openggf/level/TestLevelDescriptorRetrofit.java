package com.openggf.level;

import com.openggf.game.AbstractZoneRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestLevelDescriptorRetrofit {
    @Test
    void everyStockLevelRetainsItsIndexAndStartPositionThroughTheDescriptor() {
        for (LevelData stock : LevelData.values()) {
            LevelDescriptor descriptor = stock;

            assertEquals(stock.getLevelIndex(), descriptor.levelIndex(), stock.name());
            assertEquals(stock.getStartXPos(), descriptor.startX(), stock.name());
            assertEquals(stock.getStartYPos(), descriptor.startY(), stock.name());
        }
    }

    @Test
    void syntheticDescriptorUsesReservedBandAndFlowsThroughZoneRegistry() {
        LevelDescriptor synthetic = new SyntheticLevelDescriptor(0x400, 0x1234, 0x5678);
        AbstractZoneRegistry registry = new AbstractZoneRegistry(
                List.of(List.of(synthetic)), new String[]{"MOD ZONE"}) {
            @Override
            public int[] getStartPosition(int zoneIndex, int actIndex) {
                LevelDescriptor descriptor = zones.get(zoneIndex).get(actIndex);
                return new int[]{descriptor.startX(), descriptor.startY()};
            }

            @Override
            public int getMusicId(int zoneIndex, int actIndex) {
                return -1;
            }
        };

        assertTrue(synthetic.levelIndex() >= 0x400);
        assertTrue(java.util.Arrays.stream(LevelData.values())
                .allMatch(level -> level.getLevelIndex() < 0x400));
        assertSame(synthetic, registry.getLevelDataForZone(0).get(0));
        assertSame(synthetic, registry.getAllZones().get(0).get(0));
        assertEquals(0x1234, registry.getStartPosition(0, 0)[0]);
        assertEquals(0x5678, registry.getStartPosition(0, 0)[1]);
    }

    private record SyntheticLevelDescriptor(int levelIndex, int startX, int startY)
            implements LevelDescriptor {
    }
}
