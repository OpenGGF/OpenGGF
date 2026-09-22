package com.openggf.game.sonic3k;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.level.animation.AniPlcScriptState;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code AniPLC_DEZ} (sonic3k.asm:56079, ROM {@code $28AEE}) — the Sonic 3 &amp; Knuckles Death
 * Egg animated-art scripts. {@code Offs_AniFunc} entries 22 and 23 (:53885-53888) pair both acts
 * with the generic {@code AnimateTiles_DoAniPLC}, so all eight scripts run on every frame of both
 * acts with no camera, boss or trigger gate. Entry 46, the {@code $1700} final-boss act, is
 * {@code AnimateTiles_NULL} and has no script at all.
 *
 * <p>Each {@code zoneanimdecl} is {@code duration, art, vramaddr, numentries, numvramtiles}. A
 * global duration of {@code -1} means the frame list is {@code (tileId, duration)} pairs instead
 * of bare tile IDs; DEZ script 3 is the only one of those.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kDezAnimatedTiles {

    /** {@code zoneanimdecl} global durations, in script order. {@code -1} reads back as {@code $FF}. */
    private static final List<Integer> DURATIONS = List.of(0, 1, 3, 0xFF, 4, 4, 1, 0);
    /** {@code vramaddr} per script. */
    private static final List<Integer> DESTINATION_TILES = List.of(0x0E4, 0x1F4, 0x0EC, 0x05F, 0x04B, 0x06B, 0x028, 0x26D);
    /** {@code numvramtiles} per script. */
    private static final List<Integer> TILES_PER_FRAME = List.of(2, 0x1E, 4, 6, 2, 3, 8, 5);
    /** {@code numentries} per script. */
    private static final List<Integer> FRAME_COUNTS = List.of(2, 6, 8, 4, 4, 6, 2, 0x84);

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void bothActsLoadTheEightAniPlcDezScriptsWithTheirRomDeclarations(int actIndex) throws Exception {
        List<AniPlcScriptState> scripts = scriptsFor(actIndex);

        assertEquals(8, scripts.size(), "AniPLC_DEZ declares eight scripts");
        assertEquals(DURATIONS, scripts.stream().map(AniPlcScriptState::globalDurationForTesting).toList());
        assertEquals(DESTINATION_TILES, scripts.stream().map(AniPlcScriptState::destinationTileIndex).toList());
        assertEquals(TILES_PER_FRAME, scripts.stream().map(AniPlcScriptState::tilesPerFrame).toList());
        assertEquals(FRAME_COUNTS,
                scripts.stream().map(script -> script.frameTileIdsForTesting().length).toList());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void theShortScriptsCarryTheirRomFrameOrder(int actIndex) throws Exception {
        List<AniPlcScriptState> scripts = scriptsFor(actIndex);

        assertEquals(List.of(0, 2), tileIds(scripts.get(0)));
        assertEquals(List.of(0, 0x1E, 0x3C, 0, 0x1E, 0x3C), tileIds(scripts.get(1)));
        assertEquals(List.of(0, 4, 8, 0xC, 0x10, 0x14, 0x18, 0x1C), tileIds(scripts.get(2)));
        // Script 3 is the per-frame-duration one: (0,9) (6,4) ($C,9) (6,4).
        assertEquals(List.of(0, 6, 0xC, 6), tileIds(scripts.get(3)));
        assertEquals(List.of(0, 2, 4, 2), tileIds(scripts.get(4)));
        assertEquals(List.of(0, 3, 6, 0, 3, 6), tileIds(scripts.get(5)));
        assertEquals(List.of(0, 8), tileIds(scripts.get(6)));
    }

    /**
     * Script 7 really has {@code $84} = 132 one-byte frames: every odd frame is tile {@code $2D},
     * and the even frames step {@code 0, 5, $A, $F, $14, $19, $1E, $23} six frames each and then
     * hold {@code $28} for the last eighteen. Its global duration is 0, so it advances on every
     * pass and one full cycle is 132 frames.
     */
    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void scriptSevenHasAllHundredAndThirtyTwoFrames(int actIndex) throws Exception {
        List<Integer> expected = new ArrayList<>(132);
        int[] evenRuns = {0, 5, 0xA, 0xF, 0x14, 0x19, 0x1E, 0x23};
        for (int value : evenRuns) {
            for (int repeat = 0; repeat < 6; repeat++) {
                expected.add(value);
                expected.add(0x2D);
            }
        }
        for (int repeat = 0; repeat < 18; repeat++) {
            expected.add(0x28);
            expected.add(0x2D);
        }
        assertEquals(132, expected.size(), "the expectation itself must be 132 frames");

        assertEquals(expected, tileIds(scriptsFor(actIndex).get(7)));
    }

    /** {@code Offs_AniFunc} entry 46: the {@code $1700} arena is {@code AnimateTiles_NULL}. */
    @Test
    void theFinalBossActHasNoAniPlcScripts() throws Exception {
        HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_DEZ_BOSS_SS_ARENA, 0)
                .build();

        assertEquals(List.of(), animator().scriptsForTesting());
    }

    private static List<Integer> tileIds(AniPlcScriptState script) {
        List<Integer> ids = new ArrayList<>();
        for (int id : script.frameTileIdsForTesting()) {
            ids.add(id);
        }
        return ids;
    }

    private static List<AniPlcScriptState> scriptsFor(int actIndex) throws Exception {
        HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_DEZ, actIndex)
                .build();
        return animator().scriptsForTesting();
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
