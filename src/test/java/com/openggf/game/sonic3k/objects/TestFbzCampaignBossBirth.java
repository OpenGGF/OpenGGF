package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.difficulty.FlyingBatteryBossRecipe;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.openggf.game.sonic3k.objects.TestFbzCampaignBossRecipe.*;
import static org.junit.jupiter.api.Assertions.*;

/** Native event allocation boundary only; real owner fault/admission is integration-owned. */
class TestFbzCampaignBossBirth {
    @AfterEach void resetGraphics() { GraphicsManager.getInstance().resetState(); }

    @Test void freshRecipeIsSampledOnlyAfterAcceptedNativeBirthAndBeforeItsFirstUpdate() throws Exception {
        World world = new World(PlayerCharacter.SONIC_AND_TAILS);
        var controller = world.controllerAtBirth();
        world.bridge.stage = 8;
        controller.update(0, world.player);
        assertEquals(0, world.samples); assertEquals(0, world.creations);
        world.availableRecipe = FlyingBatteryBossRecipe.EXTRA_BEAT;
        world.bridge.stage = 0x0C;
        controller.update(1, world.player);
        var boss = world.manager.activeObjectsOfType(FbzEndBossInstance.class).getFirst();
        assertEquals(FlyingBatteryBossRecipe.EXTRA_BEAT, value(boss, "encounterRecipe"));
        assertFalse((boolean) value(boss, "nativeStarted"));
        assertEquals(List.of("create:172", "sample", "shake:false", "rebase:true"), world.log);
        assertEquals(1, world.samples); assertEquals(1, world.creations);
        controller.update(2, world.player);
        assertEquals(1, world.samples); assertEquals(1, world.creations);
        assertTrue(world.bridge.rebase); assertFalse(world.bridge.shake);
    }

    @Test void unavailableAtBirthCommitsStockAndLaterAvailabilityCannotUpgradeThatLifetime() throws Exception {
        World world = new World(PlayerCharacter.SONIC_AND_TAILS);
        var controller = world.controllerAtBirth();
        controller.update(0, world.player);
        var boss = world.manager.activeObjectsOfType(FbzEndBossInstance.class).getFirst();
        assertEquals(FlyingBatteryBossRecipe.STOCK, value(boss, "encounterRecipe"));
        world.availableRecipe = FlyingBatteryBossRecipe.EXTRA_BEAT;
        controller.update(1, world.player);
        prepareAttack(boss);
        assertEquals(9, integer(boss, "attackRoundsLeft"));
        assertEquals(1, world.samples);
        assertThrows(IllegalStateException.class, () -> boss.commitEncounterRecipe(FlyingBatteryBossRecipe.EXTRA_BEAT));
    }

    @Test void nativeSstExhaustionNeverSamplesOrRetriesButStillPublishesTheOriginalEventTail() throws Exception {
        World world = new World(PlayerCharacter.SONIC_AND_TAILS);
        var controller = world.controllerAtBirth();
        world.availableRecipe = FlyingBatteryBossRecipe.EXTRA_BEAT;
        world.manager.reserveAllButNFreeSlots(0);
        controller.update(0, world.player);
        assertEquals(1, world.creations); assertEquals(0, world.samples);
        assertTrue(world.manager.activeObjectsOfType(FbzEndBossInstance.class).isEmpty());
        assertEquals(List.of("create:172", "shake:false", "rebase:true"), world.log);
        assertTrue(world.bridge.rebase); assertFalse(world.bridge.shake);
        controller.update(1, world.player);
        assertEquals(1, world.creations); assertEquals(0, world.samples);
    }

    @Test void optionalProviderFirstAbortPropagatesWithoutCommittingOrPublishingTheTail() throws Exception {
        World world = new World(PlayerCharacter.SONIC_AND_TAILS);
        var controller = world.controllerAtBirth();
        world.availableRecipe = FlyingBatteryBossRecipe.EXTRA_BEAT;
        world.abortNextSample = true;
        assertThrows(com.openggf.level.objects.ObjectCallbackAbortException.class,
                () -> controller.update(0, world.player));
        var boss = world.manager.activeObjectsOfType(FbzEndBossInstance.class).getFirst();
        assertEquals(FlyingBatteryBossRecipe.STOCK, value(boss, "encounterRecipe"));
        assertEquals(false, value(boss, "encounterRecipeCommitted"));
        assertFalse((boolean) value(boss, "nativeStarted"));
        assertEquals(List.of("create:172", "sample"), world.log);
        assertEquals(1, world.samples); assertEquals(1, world.creations);
        assertFalse(world.bridge.rebase);
        // Partial native allocation is owned by host frame discard and teardown.
        // Do not tick/retry/restore this aborted graph as a successful encounter.
    }

    @Test void capacityDeclineAndSkDispatchDoNotEvenInvokeAFirstFaultingOptionalProvider() throws Exception {
        for (boolean sk : new boolean[]{false,true}) {
            World world = new World(PlayerCharacter.SONIC_AND_TAILS);
            var controller = world.controllerAtBirth();
            world.abortNextSample = true;
            if (sk) world.zone = 8; else world.manager.reserveAllButNFreeSlots(0);
            assertDoesNotThrow(() -> controller.update(0, world.player));
            assertEquals(0, world.samples); assertTrue(world.abortNextSample);
            assertEquals(1, world.creations);
            assertTrue(world.manager.activeObjectsOfType(FbzEndBossInstance.class).isEmpty());
            assertTrue(world.bridge.rebase); assertFalse(world.bridge.shake);
        }
    }

    @Test void unchangedAcZoneSetDispatchCreatesTheSkCapsuleWithoutSamplingAnFbzRecipe() throws Exception {
        World world = new World(PlayerCharacter.SONIC_AND_TAILS);
        world.zone = 8;
        world.availableRecipe = FlyingBatteryBossRecipe.EXTRA_BEAT;
        var controller = world.controllerAtBirth();
        controller.update(0, world.player);
        assertEquals(1, world.manager.activeObjectsOfType(SozHyudoroCapsuleObjectInstance.class).size());
        assertTrue(world.manager.activeObjectsOfType(FbzEndBossInstance.class).isEmpty());
        assertEquals(1, world.creations); assertEquals(0, world.samples);
        assertEquals(List.of("create:172", "shake:false", "rebase:true"), world.log);
    }
}
