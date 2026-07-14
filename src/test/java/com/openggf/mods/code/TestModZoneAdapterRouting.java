package com.openggf.mods.code;

import com.openggf.game.GameModule;
import com.openggf.game.patch.DelegatingGameModule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestModZoneAdapterRouting {

    @Test
    void delegatingModuleForwardsTheExactAdapterInstance() {
        ModZoneAdapter adapter = mock(ModZoneAdapter.class);
        GameModule base = moduleWithAdapter(adapter);
        GameModule decorated = new DelegatingGameModule(base, "test") {};

        assertSame(adapter, decorated.getModZoneAdapter());
    }

    @Test
    void aModuleWithoutAnAdapterRejectsAdditiveZones() {
        assertTrue(GameModule.EMPTY_MOD_ZONE_ADAPTER.isUnsupported());
        assertThrows(ModRegistrationException.class,
                () -> GameModule.EMPTY_MOD_ZONE_ADAPTER.validate("alpha", levelDefinition()));
    }

    private static GameModule moduleWithAdapter(ModZoneAdapter adapter) {
        GameModule module = mock(GameModule.class);
        when(module.getModZoneAdapter()).thenReturn(adapter);
        return module;
    }

    private static ModLevelDefinition levelDefinition() {
        return new ModLevelDefinition(1, "ZONE", 0x40, 0x400, 8, 1, 1,
                new ModLevelDefinition.Bounds(0, 0x100, 0, 0x100),
                new ModLevelDefinition.Start(0x20, 0x20),
                new ModLevelDefinition.StockMusic(1), List.of(), List.of(),
                new byte[32], new byte[8], new byte[128], new byte[1], null,
                new byte[16], new byte[16], new byte[1], new int[]{0}, new int[]{0},
                new byte[][]{new byte[32], new byte[32], new byte[32], new byte[32]},
                1, 1, 1, 1);
    }
}
