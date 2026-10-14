package com.openggf.tests;

import com.openggf.configuration.WidescreenAspect;
import com.openggf.data.PlayerSpriteArtProvider;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.sprites.managers.SpriteManagerInternalAccess;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/** The literal carry mapping owner borrows host resources without borrowing physics/controller RAM. */
@RequiresRom(SonicGame.SONIC_3K)
class TestNativeCarriedPlayerArtBinding {
    private final CanonicalEntryMatrixSupport support = new CanonicalEntryMatrixSupport() {
        @Override TestS3kCanonicalEntryLifecycle.Act binding() {
            return new TestS3kCanonicalEntryLifecycle.Act("CNZ1", 3, 0);
        }
    };
    @org.junit.jupiter.api.BeforeEach void saveConfiguration() { support.saveConfiguration(); }
    @org.junit.jupiter.api.AfterEach void restoreConfiguration() { support.restoreConfiguration(); }

    @org.junit.jupiter.api.Test
    void abandonedCarryResourcesCannotOverwriteALaterScriptOwner() throws Exception {
        var fixture = support.boot(new CanonicalEntryMatrixSupport.Row("sonic",
                WidescreenAspect.NATIVE_4_3, "s1", ""));
        fixture.stepIdleFrames(2);
        var player = GameServices.sprites().getMainPlayable();
        var manager = GameServices.sprites();
        assertTrue(SpriteManagerInternalAccess.hasBorrowedNativePlayerArt(manager, player));
        var art = ((PlayerSpriteArtProvider) GameServices.level().getGame()).loadPlayerSpriteArt("sonic");
        var laterRenderer = new com.openggf.sprites.render.PlayerSpriteRenderer(art);
        var laterProfile = art.animationProfile();
        player.setSpriteRenderer(laterRenderer);
        player.setAnimationProfile(laterProfile);
        // This is a resource-owner handoff, not gameplay state or a fixture input.
        // A missing/absent carry capture must preserve a separately published owner.
        var adapter = SpriteManagerInternalAccess.nativePlayerArtAdapter(manager);
        adapter.resetForMissingSnapshot();
        SpriteManagerInternalAccess.restoreNativePlayerArtAfterRoster(manager);
        assertSame(laterRenderer, player.getSpriteRenderer());
        assertSame(laterProfile, player.getAnimationProfile());
        assertFalse(SpriteManagerInternalAccess.hasBorrowedNativePlayerArt(manager, player));
        GameServices.sprites().getRegisteredSidekicks().getFirst().getTailsCarryController().clearAndReleaseMain();
        assertSame(laterRenderer, player.getSpriteRenderer(), "late release cannot revive the retired art borrower");
    }

    @ParameterizedTest(name = "literal carry / resource return / reverse restore {0}")
    @ValueSource(strings = {"off", "s1", "s2"})
    void nativeCarryAndLastPoseReturnRecreateAcrossBothWorldCycles(String donor) throws Exception {
        var row = new CanonicalEntryMatrixSupport.Row("sonic", WidescreenAspect.NATIVE_4_3, donor, "");
        var fixture = support.boot(row);
        var player = GameServices.sprites().getMainPlayable();
        var configured = donor.equals("off") ? (PlayerSpriteArtProvider) GameServices.level().getGame()
                : CrossGameFeatureProvider.getInstance();
        var host = (PlayerSpriteArtProvider) GameServices.level().getGame();
        var ordinaryRenderer = player.getSpriteRenderer();
        var ordinaryController = player.getSuperStateController();
        var rules = player.getGameRules();
        var physics = player.getPhysicsProfile();
        var registry = fixture.gameplayMode().getRewindRegistry();
        var before = registry.capture();
        assertFalse(SpriteManagerInternalAccess.hasBorrowedNativePlayerArt(GameServices.sprites(), player));
        fixture.stepIdleFrames(2);
        var active = registry.capture();
        assertTrue(SpriteManagerInternalAccess.hasBorrowedNativePlayerArt(GameServices.sprites(), player));
        TestS3kCanonicalEntryLifecycle.assertRendererSource(player.getSpriteRenderer(), host.loadPlayerSpriteArt("sonic"));
        TestS3kCanonicalEntryLifecycle.assertPaletteSource(player.getSpriteRenderer(), host.loadCharacterPalette("sonic"));
        assertSame(rules, player.getGameRules());
        assertSame(physics, player.getPhysicsProfile());
        assertSame(ordinaryController, player.getSuperStateController());
        assertEquals(0x91, player.getMappingFrame());
        assertEquals(0x0A, player.getAnimationTick(), "two native carry dispatches share the raw timer");
        var carrier = GameServices.sprites().getRegisteredSidekicks().getFirst();
        int untilRelease = 0;
        while (carrier.getTailsCarryController().isCarryingMainCharacter() && untilRelease < 900) {
            fixture.stepIdleFrames(1); untilRelease++;
            assertFalse(player.getDead());
            assertEquals(3, GameServices.gameState().getLives());
        }
        assertTrue(untilRelease < 900, "native carry must naturally release");
        assertTrue(SpriteManagerInternalAccess.hasBorrowedNativePlayerArt(GameServices.sprites(), player),
                "P2 release after P1 Animate keeps the final native pose drawable");
        var returning = registry.capture();
        TestS3kCanonicalEntryLifecycle.assertRendererSource(player.getSpriteRenderer(), host.loadPlayerSpriteArt("sonic"));
        fixture.stepIdleFrames(30);
        var released = registry.capture();
        assertFalse(SpriteManagerInternalAccess.hasBorrowedNativePlayerArt(GameServices.sprites(), player));
        assertSame(ordinaryRenderer, player.getSpriteRenderer());
        TestS3kCanonicalEntryLifecycle.assertRendererSource(player.getSpriteRenderer(), configured.loadPlayerSpriteArt("sonic"));
        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(returning);
            fixture.runner().primeInputState(new Bk2FrameInput(0, 0, 0, false, ""));
            TestS3kCanonicalEntryLifecycle.assertSameWorld(returning, registry.capture(), "last native pose restore");
            assertTrue(SpriteManagerInternalAccess.hasBorrowedNativePlayerArt(GameServices.sprites(), player));
            fixture.stepIdleFrames(30);
            TestS3kCanonicalEntryLifecycle.assertSameWorld(released, registry.capture(), "ordinary resource return replay");
            registry.restore(before);
            fixture.runner().primeInputState(new Bk2FrameInput(0, 0, 0, false, ""));
            TestS3kCanonicalEntryLifecycle.assertSameWorld(before, registry.capture(), "restore before first borrowing");
            assertSame(ordinaryRenderer, player.getSpriteRenderer());
            fixture.stepIdleFrames(2);
            TestS3kCanonicalEntryLifecycle.assertSameWorld(active, registry.capture(), "native carry attachment replay", 2);
            registry.restore(released);
            TestS3kCanonicalEntryLifecycle.assertSameWorld(released, registry.capture(), "reverse restore preserves absence");
        }
    }
}
