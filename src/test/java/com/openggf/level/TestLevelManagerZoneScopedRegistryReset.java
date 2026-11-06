package com.openggf.level;

import com.openggf.game.GameServices;
import com.openggf.game.palette.PaletteOwnershipRegistry;
import com.openggf.game.palette.PaletteSurface;
import com.openggf.game.palette.PaletteWrite;
import com.openggf.game.render.AdvancedRenderMode;
import com.openggf.game.render.AdvancedRenderModeContext;
import com.openggf.game.render.AdvancedRenderFrameState;
import com.openggf.game.render.SpecialRenderEffect;
import com.openggf.game.render.SpecialRenderEffectContext;
import com.openggf.game.render.SpecialRenderEffectStage;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.GameplaySessionFactory;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.timing.HardwareReadinessAdmissionPolicy;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class TestLevelManagerZoneScopedRegistryReset {

    @BeforeEach
    void setUp() {
        TestEnvironment.resetAll();
    }

    @Test
    void levelLoadBoundaryClearsPaletteRotationDisableAlongsideRenderRegistries() {
        LevelManager levelManager = GameServices.level();
        GameServices.paletteOwnershipRegistry().setPaletteRotationDisabled(true);
        GameServices.specialRenderEffectRegistry().register(noOpEffect());
        GameServices.advancedRenderModeController().register(noOpMode());

        levelManager.resetZoneScopedRegistriesForLevelLoad();

        assertFalse(GameServices.paletteOwnershipRegistry().isPaletteRotationDisabled(),
                "level-load boundaries must release sticky palette rotation disables from abandoned writers");
        assertTrue(GameServices.specialRenderEffectRegistry().isEmpty(),
                "special render effects are zone-scoped and must be cleared at level load");
        assertTrue(GameServices.advancedRenderModeController().isEmpty(),
                "advanced render modes are zone-scoped and must be cleared at level load");
    }

    @Test
    void staleManagerCannotClearTheReplacementSessionsRegistries() {
        GameplayModeContext previous = TestEnvironment.activeGameplayMode();
        LevelManager stale = previous.getLevelManager();
        var previousWorld = previous.getWorldSession();

        GameplayModeContext replacement = SessionManager.openGameplaySession(new Sonic2GameModule());
        GameplaySessionFactory.attachManagers(replacement, EngineServices.current());
        assertNotSame(previousWorld, replacement.getWorldSession());
        assertNotSame(stale, replacement.getLevelManager());

        assertStaleResetPreservesReplacement(stale, replacement);
    }

    @Test
    void staleManagerCannotClearFreshManagersInTheSameWorld() {
        GameplayModeContext previous = TestEnvironment.activeGameplayMode();
        LevelManager stale = previous.getLevelManager();
        var world = previous.getWorldSession();

        GameplayModeContext replacement = SessionManager.reopenGameplaySession(HardwareReadinessAdmissionPolicy.LIVE);
        GameplaySessionFactory.attachManagers(replacement, EngineServices.current());
        assertSame(world, replacement.getWorldSession());
        assertNotSame(stale, replacement.getLevelManager());

        assertStaleResetPreservesReplacement(stale, replacement);
    }

    @Test
    void currentManagerStillClearsTargetsPendingWritesAndRenderRegistries() {
        GameplayModeContext current = TestEnvironment.activeGameplayMode();
        PaletteOwnershipRegistry palettes = seedRegistries(current);

        current.getLevelManager().resetZoneScopedRegistriesForLevelLoad();

        assertSame(current, SessionManager.getCurrentGameplayMode());
        assertSame(palettes, GameServices.paletteOwnershipRegistry());
        assertArrayEquals(new byte[2], palettes.targetSegaData(1, 2, 1));
        assertEquals("none", palettes.targetOwnerAt(1, 2));
        assertFalse(palettes.isPaletteRotationDisabled());
        assertTrue(current.getSpecialRenderEffectRegistry().isEmpty());
        assertTrue(current.getAdvancedRenderModeController().isEmpty());

        Palette normal = new Palette();
        palettes.resolveInto(new Palette[]{normal}, null, null, null);
        assertFalse(palettes.hasResolvedThisFrame(), "The current reset must discard pending palette writes");
        assertEquals(0, Byte.toUnsignedInt(normal.getColor(0).r));
        assertEquals("none", palettes.ownerAt(PaletteSurface.NORMAL, 0, 0));
    }

    private static void assertStaleResetPreservesReplacement(
            LevelManager stale, GameplayModeContext replacement) {
        PaletteOwnershipRegistry palettes = seedRegistries(replacement);

        stale.resetZoneScopedRegistriesForLevelLoad();

        assertSame(replacement, SessionManager.getCurrentGameplayMode());
        assertSame(replacement.getWorldSession(), SessionManager.getCurrentWorldSession());
        assertSame(palettes, GameServices.paletteOwnershipRegistry());
        assertArrayEquals(new byte[]{0x0A, 0x2E}, palettes.targetSegaData(1, 2, 1),
                "A retired LevelManager must not clear its successor's palette targets");
        assertEquals("replacement-target", palettes.targetOwnerAt(1, 2));
        assertTrue(palettes.isPaletteRotationDisabled());
        assertEquals(1, replacement.getSpecialRenderEffectRegistry().activeEffectCount());
        assertEquals(1, replacement.getAdvancedRenderModeController().size());

        Palette normal = new Palette();
        palettes.resolveInto(new Palette[]{normal}, null, null, null);
        assertTrue(palettes.hasResolvedThisFrame(), "The successor's pending write must remain queued");
        assertEquals(255, Byte.toUnsignedInt(normal.getColor(0).r));
        assertEquals(0, Byte.toUnsignedInt(normal.getColor(0).g));
        assertEquals(0, Byte.toUnsignedInt(normal.getColor(0).b));
        assertEquals("replacement-queued", palettes.ownerAt(PaletteSurface.NORMAL, 0, 0));
    }

    private static PaletteOwnershipRegistry seedRegistries(GameplayModeContext context) {
        assertSame(context, SessionManager.getCurrentGameplayMode());
        assertTrue(context.isGameplayRuntimeReady(), "The real factory must finish attaching the successor");
        PaletteOwnershipRegistry palettes = context.getPaletteOwnershipRegistry();
        palettes.applyTargetPatch("replacement-target", 1, 2, new byte[]{0x0A, 0x2E});
        palettes.submit(PaletteWrite.normal("replacement-queued", 0, 0, 0, new byte[]{0, 0x0E}));
        palettes.setPaletteRotationDisabled(true);
        context.getSpecialRenderEffectRegistry().register(noOpEffect());
        context.getAdvancedRenderModeController().register(noOpMode());
        return palettes;
    }

    private static SpecialRenderEffect noOpEffect() {
        return new SpecialRenderEffect() {
            @Override
            public SpecialRenderEffectStage stage() {
                return SpecialRenderEffectStage.AFTER_BACKGROUND;
            }

            @Override
            public void render(SpecialRenderEffectContext context) {
                // no-op
            }
        };
    }

    private static AdvancedRenderMode noOpMode() {
        return new AdvancedRenderMode() {
            @Override
            public String id() {
                return "test-mode";
            }

            @Override
            public void contribute(AdvancedRenderModeContext context, AdvancedRenderFrameState.Builder builder) {
                builder.enablePerLineForegroundScroll();
            }
        };
    }
}
