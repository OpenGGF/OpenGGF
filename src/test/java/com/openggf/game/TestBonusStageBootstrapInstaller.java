package com.openggf.game;

import com.openggf.game.sonic3k.Sonic3kBonusStageCoordinator;
import com.openggf.level.objects.ObjectManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.*;

class TestBonusStageBootstrapInstaller {

    @ParameterizedTest
    @EnumSource(BonusStageType.class)
    void startupResolutionUsesTheRequestedTypeBeforeProviderEntry(BonusStageType type) {
        var provider = new Sonic3kBonusStageCoordinator();
        assertEquals(BonusStageType.NONE, provider.getActiveType());
        var spawn = BonusStageBootstrapInstaller.resolveSpawn(provider, type);
        if (type == BonusStageType.GLOWING_SPHERE) {
            assertNotNull(spawn);
            assertEquals(0x78, spawn.x());
            assertEquals(0x0F30, spawn.y());
            assertEquals(com.openggf.game.sonic3k.constants.Sonic3kObjectIds.PACHINKO_ENERGY_TRAP, spawn.objectId());
        } else {
            assertNull(spawn);
        }
    }

    @Test
    void missingProviderBootstrapIsCreatedOnce() {
        BonusStageProvider provider = new Sonic3kBonusStageCoordinator();
        ObjectManager objectManager = mock(ObjectManager.class);
        when(objectManager.getActiveObjects()).thenReturn(List.of());

        BonusStageBootstrapInstaller.ensurePresent(
                provider, BonusStageType.GLOWING_SPHERE, objectManager);

        verify(objectManager).createDynamicObject(any());
    }

    @Test
    void matchingProviderBootstrapIsNotDuplicated() {
        BonusStageProvider provider = new Sonic3kBonusStageCoordinator();
        BonusStageProvider.BootstrapObject bootstrap =
                provider.bootstrapObject(BonusStageType.GLOWING_SPHERE);
        ObjectManager objectManager = mock(ObjectManager.class);
        when(objectManager.getActiveObjects()).thenReturn(
                List.of(bootstrap.create()));

        BonusStageBootstrapInstaller.ensurePresent(
                provider, BonusStageType.GLOWING_SPHERE, objectManager);

        verify(objectManager, never()).createDynamicObject(any());
    }

    @Test
    void missingSemanticDoesNotTouchTheObjectManager() {
        ObjectManager objectManager = mock(ObjectManager.class);

        BonusStageBootstrapInstaller.ensurePresent(
                NoOpBonusStageProvider.INSTANCE,
                BonusStageType.GLOWING_SPHERE,
                objectManager);

        verify(objectManager, never()).getActiveObjects();
        verify(objectManager, never()).createDynamicObject(any());
    }
}
