package com.openggf.game.sonic3k.render;

import com.openggf.game.render.SpecialRenderEffectRegistry;
import com.openggf.game.render.SpecialRenderEffectStage;
import com.openggf.game.sonic3k.Sonic3kZoneFeatureProvider;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestSszAct2BackgroundPriority {
    @Test
    void act2RegistersVisibleIslandAndLowSpriteMaskWithoutChangingAct1() {
        var provider = new Sonic3kZoneFeatureProvider();
        var effects = new SpecialRenderEffectRegistry();
        provider.registerSpecialRenderEffects(effects, Sonic3kZoneIds.ZONE_SSZ, 1);
        assertEquals(1, effects.size(SpecialRenderEffectStage.AFTER_FOREGROUND));
        assertEquals(1, effects.size(SpecialRenderEffectStage.SPRITE_PRIORITY_MASK));
        effects.clear();
        provider.registerSpecialRenderEffects(effects, Sonic3kZoneIds.ZONE_SSZ, 0);
        assertEquals(0, effects.activeEffectCount());
    }
}
