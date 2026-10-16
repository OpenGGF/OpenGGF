package com.openggf.mods.code;

import com.openggf.GameLoop;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.control.InputHandler;
import com.openggf.game.GameModule;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.LevelEventProvider;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.objects.S3kTitleCardOwnerSlotObjectInstance;
import com.openggf.game.sonic3k.objects.SozHyudoroControllerObjectInstance;
import com.openggf.game.sonic3k.titlecard.Sonic3kTitleCardManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Additive zone decoration must preserve the inherited native title lifecycle. */
@RequiresRom(SonicGame.SONIC_3K)
class TestInheritedZoneTitleLifecycle {
    @AfterEach
    void cleanup() {
        TestEnvironment.resetAll();
    }

    @Test
    void inheritedSoz2TitleRetirementAllocatesPastOwnerAndDeletesOwner() {
        decorateNativeEvents();
        HeadlessTestFixture.builder().withSkippedZoneIntro().withZoneAndAct(8, 1)
                .withFreshLevelStartLifecycle().build();
        var objects = GameServices.level().getObjectManager();
        var owner = (AbstractObjectInstance) objects.createDynamicObject(() ->
                new S3kTitleCardOwnerSlotObjectInstance(new ObjectSpawn(0, 0, 0, 0, 0, false, 0)));
        int ownerSlot = owner.getSlotIndex();
        var title = (Sonic3kTitleCardManager) GameServices.module().getTitleCardProvider();

        title.completeOmittedPresentationFreshLevelRuntimeArtHandoff();

        var controllers = objects.getActiveObjects().stream()
                .filter(SozHyudoroControllerObjectInstance.class::isInstance)
                .map(SozHyudoroControllerObjectInstance.class::cast).toList();
        assertEquals(1, controllers.size(), "the native SOZ2 event remains active behind the decorator");
        assertTrue(controllers.getFirst().getSlotIndex() > ownerSlot,
                "Hyudoro allocates while the title owner still occupies its SST");
        assertTrue(owner.isDestroyed(), "retirement deletes the original title owner");
    }

    @Test
    void inheritedHcz1PostTitleReleaseRestoresAirbornePlayerState() throws Exception {
        decorateNativeEvents();
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(1, 0).build();
        var player = fixture.sprite();
        GameLoop loop = new GameLoop(new InputHandler());
        // The fixture already bound the omitted title's art lease. Model the
        // ordinary locked-title release state without initializing a second owner.
        player.setAir(false);
        player.setForcedAnimationId(-1);
        player.setControlLocked(true);
        loop.setGameMode(GameMode.TITLE_CARD);

        var exit = GameLoop.class.getDeclaredMethod("exitTitleCard");
        exit.setAccessible(true);
        exit.invoke(loop);

        assertTrue(player.getAir(), "post-title HCZ state must reach the inherited native event provider");
        assertFalse(player.isControlLocked(), "the same release also clears the title's input lock");
        assertEquals(Sonic3kAnimationIds.HURT_FALL.id(), player.getForcedAnimationId(),
                "the native hook publishes the falling override before the next animation dispatch");
    }

    private static void decorateNativeEvents() {
        GameModule stock = GameServices.module();
        var added = new PreparedModZone("test-owner", "extra-zone", null, null, null,
                "EXTRA", 0x400, 0x40, 0, 0);
        var events = new ModZoneEventProvider(stock.getLevelEventProvider(),
                stock.getZoneRegistry().getZoneCount(), List.of(added), null);
        var decorated = new DelegatingGameModule(stock, "test:inherited-title") {
            @Override public LevelEventProvider getLevelEventProvider() { return events; }
        };
        SessionManager.clear();
        GameModuleRegistry.setCurrent(decorated);
        TestEnvironment.activeGameplayMode();
        GameServices.configuration().setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        GameServices.configuration().setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
    }
}
