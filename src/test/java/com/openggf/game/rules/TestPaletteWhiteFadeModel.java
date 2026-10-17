package com.openggf.game.rules;

import com.openggf.game.sonic3k.Sonic3kGameModule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class TestPaletteWhiteFadeModel {
    @Test
    void shippedHostsKeepTheirOwnWhiteFadeHardware() {
        assertSame(PaletteWhiteFadeModel.SCREEN_OVERLAY, GameRules.SONIC_1.paletteWhiteFade());
        assertSame(PaletteWhiteFadeModel.SCREEN_OVERLAY, GameRules.SONIC_2.paletteWhiteFade());
        assertSame(PaletteWhiteFadeModel.ROM_PALETTE, GameRules.SONIC_3K.paletteWhiteFade());
        assertEquals(3, GameRules.SONIC_3K.paletteWhiteFade().fromWhiteVintsPerStep());
    }

    @Test
    void olderCandidateConstructorsRetainTheirOverlayDefault() {
        var r = GameRules.SONIC_3K;
        var ten = new GameRules(r.playerMovement(), r.playerCapability(), r.collision(), r.playerAnimation(),
                r.camera(), r.ring(), r.objectInteraction(), r.sidekickCpu(), r.powerUp(), r.drowningBubble());
        var eleven = new GameRules(r.playerMovement(), r.playerCapability(), r.collision(), r.playerAnimation(),
                r.camera(), r.ring(), r.objectInteraction(), r.sidekickCpu(), r.powerUp(), r.drowningBubble(), r.dynamicArtDmaService());
        assertSame(PaletteWhiteFadeModel.SCREEN_OVERLAY, ten.paletteWhiteFade());
        assertSame(PaletteWhiteFadeModel.SCREEN_OVERLAY, eleven.paletteWhiteFade());
        assertSame(r.dynamicArtDmaService(), eleven.dynamicArtDmaService());
    }

    @Test
    void aCharacterDonorCannotReplaceTheHostsWhiteFade() {
        for (var host : new GameRules[]{GameRules.SONIC_1, GameRules.SONIC_2, GameRules.SONIC_3K}) {
            var composed = CrossGameRuleComposer.compose(host, GameRules.SONIC_3K,
                    new Sonic3kGameModule().getDonorCapabilities());
            assertSame(host.paletteWhiteFade(), composed.paletteWhiteFade());
            var overridden = CrossGameRuleComposer.withPlayerRules(host, host.playerMovement(),
                    host.objectInteraction(), host.collision(), host.ring());
            assertSame(host.paletteWhiteFade(), overridden.paletteWhiteFade());
        }
    }
}
