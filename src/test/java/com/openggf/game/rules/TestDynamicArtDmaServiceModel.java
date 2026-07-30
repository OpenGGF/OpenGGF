package com.openggf.game.rules;

import com.openggf.game.resources.PlcLifecyclePhase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestDynamicArtDmaServiceModel {
    @Test
    void sonic2ServicesOnlyProcessDmaQueueEquivalentClaims() {
        var model = DynamicArtDmaServiceModel.SONIC_2_PROCESS_DMA_QUEUE;

        assertFalse(model.services(null));
        assertFalse(model.services(PlcLifecyclePhase.PALETTE_FADE));
        assertFalse(model.services(PlcLifecyclePhase.LEVEL_TITLE_CARD));
        assertTrue(model.services(PlcLifecyclePhase.ORDINARY_LEVEL));
        assertTrue(model.services(PlcLifecyclePhase.SPECIAL_STAGE));
    }

    @Test
    void sonic1PreservesClaimServiceBehavior() {
        for (PlcLifecyclePhase phase : PlcLifecyclePhase.values()) {
            assertTrue(DynamicArtDmaServiceModel.EVERY_CLAIM.services(phase));
        }
    }

    @Test
    void playerDynamicArtAuditEligibilityIsOwnedByTheTypedServiceModel() {
        assertTrue(GameRules.SONIC_1.dynamicArtDmaService()
                .supportsPlayerDynamicArtAudit());
        assertTrue(GameRules.SONIC_2.dynamicArtDmaService()
                .supportsPlayerDynamicArtAudit());
        assertFalse(GameRules.SONIC_3K.dynamicArtDmaService()
                .supportsPlayerDynamicArtAudit());
    }
}
