package com.openggf.game.sonic3k;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.level.animation.AniPlcScriptState;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** AniPLC_HPZ (sonic3k.asm:56327) drives both Hidden Palace ($1601) and the sanctuary ($1701). */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kHpzPatternAnimation {

    @ParameterizedTest
    @ValueSource(ints = {Sonic3kZoneIds.ZONE_HPZ, Sonic3kZoneIds.ZONE_DEZ_BOSS_SS_ARENA})
    void hiddenPalaceActsLoadTheFourAniPlcHpzScripts(int zone) throws Exception {
        HeadlessTestFixture.builder().withZoneAndAct(zone, 1).build();

        List<AniPlcScriptState> scripts = animator().scriptsForTesting();

        // zoneanimdecl tiles per frame: 3 ($2D0), 2 ($2D3), 4 ($2D5), 3 ($2D9).
        assertEquals(List.of(3, 2, 4, 3),
                scripts.stream().map(AniPlcScriptState::tilesPerFrame).toList());
    }

    private static Sonic3kPatternAnimator animator() throws ReflectiveOperationException {
        var manager = GameServices.level().getAnimatedPatternManager();
        if (manager instanceof Sonic3kPatternAnimator animator) {
            return animator;
        }
        Field field = Sonic3kLevelAnimationManager.class.getDeclaredField("patternAnimator");
        field.setAccessible(true);
        return (Sonic3kPatternAnimator) field.get(manager);
    }
}
