package com.openggf.game.sonic3k;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.level.animation.AniPlcScriptState;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code Offs_AniFunc} holds a {@code (AnimateTiles, AniPLC)} pair per act
 * (sonic3k.asm:53842-53881). Counting pairs from the table head, entry 18 is Lava Reef act 1 -
 * {@code AnimateTiles_LRZ1} with {@code AniPLC_LRZ1} - and entry 19 act 2, which pairs
 * {@code AnimateTiles_LRZ2} with {@code AniPLC_LRZ2}. The animator gave both acts
 * {@code AniPLC_LRZ1}.
 *
 * <p>The two lists are unambiguous: {@code AniPLC_LRZ1} (sonic3k.asm:56007) declares two scripts of
 * four VRAM tiles each at {@code $354} and {@code $350}, and {@code AniPLC_LRZ2} (56022) one of six
 * tiles at {@code $358} and one of eight at {@code $350}
 * ({@code zoneanimdecl duration,artaddr,vramaddr,numentries,numvramtiles}).
 *
 * <p>Both acts additionally run the custom {@code AnimateTiles_LRZ1}/{@code AnimateTiles_LRZ2}
 * split-DMA channels before the AniPLC pass; those are slice 2 and are not asserted here.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kLrzPatternAnimation {

    @Test
    void act1LoadsAniPlcLrz1() throws Exception {
        HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_LRZ, 0).build();

        List<AniPlcScriptState> scripts = animator().scriptsForTesting();

        assertEquals(List.of(4, 4), scripts.stream().map(AniPlcScriptState::tilesPerFrame).toList());
        assertEquals(List.of(0x354, 0x350),
                scripts.stream().map(AniPlcScriptState::destinationTileIndex).toList());
    }

    @Test
    void act2LoadsAniPlcLrz2() throws Exception {
        HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_LRZ, 1).build();

        List<AniPlcScriptState> scripts = animator().scriptsForTesting();

        assertEquals(List.of(6, 8), scripts.stream().map(AniPlcScriptState::tilesPerFrame).toList());
        assertEquals(List.of(0x358, 0x350),
                scripts.stream().map(AniPlcScriptState::destinationTileIndex).toList());
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
