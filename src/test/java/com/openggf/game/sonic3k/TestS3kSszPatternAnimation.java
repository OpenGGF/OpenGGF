package com.openggf.game.sonic3k;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.level.animation.AniPlcScriptState;
import com.openggf.level.animation.AnimatedPatternManager;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sky Sanctuary animated tiles against {@code AniPLC_SSZ} (sonic3k.asm:56040-56085) and the
 * {@code Offs_AniFunc} pair the zone's two acts take (sonic3k.asm:53881-53884).
 *
 * <p>{@code $A00}'s function word is {@code AnimateTiles_DoAniPLC}, so act 1 runs the six
 * scripts; {@code $A01}'s is {@code AnimateTiles_NULL}, a bare {@code rts}, so act 2 animates
 * nothing even though the table repeats the same {@code AniPLC_SSZ} pointer beside it.
 */
@RequiresRom(SonicGame.SONIC_3K)
public class TestS3kSszPatternAnimation {

    /**
     * {@code zoneanimdecl duration, art, destination, frameCount, tilesPerFrame} for each of the
     * six {@code AniPLC_SSZ} scripts, in declaration order.
     */
    private static final int[][] SSZ_SCRIPTS = {
            {7, 0x1F3, 4, 0x24},
            {7, 0x217, 4, 0x08},
            {7, 0x21F, 3, 0x08},
            {2, 0x1D9, 4, 0x09},
            {2, 0x1E2, 4, 0x04},
            {2, 0x1E6, 4, 0x0D}
    };

    @BeforeAll
    public static void configure() {
        SonicConfigurationService.getInstance()
                .setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);
    }

    @Test
    public void act1RunsTheSixAniPlcSszScripts() {
        HeadlessTestFixture.builder().withZoneAndAct(0x0A, 0).build();

        List<AniPlcScriptState> scripts = resolvePatternAnimator().scriptsForTesting();
        assertEquals(SSZ_SCRIPTS.length, scripts.size(),
                "AniPLC_SSZ declares dc.w zoneanimcount = 5, i.e. six scripts");
        for (int i = 0; i < SSZ_SCRIPTS.length; i++) {
            AniPlcScriptState script = scripts.get(i);
            assertEquals(SSZ_SCRIPTS[i][0], script.globalDurationForTesting(),
                    "script " + i + " duration");
            assertEquals(SSZ_SCRIPTS[i][1], script.destinationTileIndex(),
                    "script " + i + " destination tile");
            assertEquals(SSZ_SCRIPTS[i][2], script.frameTileIdsForTesting().length,
                    "script " + i + " frame count");
            assertEquals(SSZ_SCRIPTS[i][3], script.tilesPerFrame(),
                    "script " + i + " tiles per frame");
        }
    }

    /**
     * The third script's declaration carries a fourth offset byte the ROM comments as unused,
     * because its frame count is three. Reading four frames there would animate a tile range
     * that does not exist in {@code ArtUnc_AniSSZ__2}.
     */
    @Test
    public void thirdScriptStopsAtThreeFrames() {
        HeadlessTestFixture.builder().withZoneAndAct(0x0A, 0).build();

        int[] frames = resolvePatternAnimator().scriptsForTesting().get(2).frameTileIdsForTesting();
        assertEquals(3, frames.length);
        assertEquals(frames[0] + 0x08, frames[1]);
        assertEquals(frames[0] + 0x10, frames[2]);
    }

    /** {@code Offs_AniFunc} entry 42 is {@code AnimateTiles_NULL}: act 2 parses no script. */
    @Test
    public void act2AnimatesNothing() {
        HeadlessTestFixture.builder().withZoneAndAct(0x0A, 1).build();

        assertTrue(resolvePatternAnimator().scriptsForTesting().isEmpty(),
                "SSZ2 runs AnimateTiles_NULL, a bare rts");
    }

    private static Sonic3kPatternAnimator resolvePatternAnimator() {
        AnimatedPatternManager manager = GameServices.level().getAnimatedPatternManager();
        assertNotNull(manager, "AnimatedPatternManager must be present");
        if (manager instanceof Sonic3kPatternAnimator animator) {
            return animator;
        }
        if (manager instanceof Sonic3kLevelAnimationManager levelAnimator) {
            try {
                Field field =
                        Sonic3kLevelAnimationManager.class.getDeclaredField("patternAnimator");
                field.setAccessible(true);
                if (field.get(levelAnimator) instanceof Sonic3kPatternAnimator animator) {
                    return animator;
                }
            } catch (ReflectiveOperationException e) {
                throw new AssertionError("Unable to access Sonic3kPatternAnimator", e);
            }
        }
        throw new AssertionError("Unexpected AnimatedPatternManager: " + manager.getClass());
    }
}
