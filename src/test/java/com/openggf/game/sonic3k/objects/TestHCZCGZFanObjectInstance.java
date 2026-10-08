package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameRng;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.LevelManager;
import com.openggf.level.WaterSystem;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectSlotLayout;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.sprites.playable.ObjectControlState;
import com.openggf.tests.TestablePlayableSprite;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestHCZCGZFanObjectInstance {

    @Test
    void bubbleMovesOnceThenChecksWaterOnTheNextPassAndReplaysAfterRestore() {
        AbstractObjectInstance.resetCameraBoundsForTests();
        var water = Mockito.mock(WaterSystem.class);
        Mockito.when(water.getWaterLevelY(ArgumentMatchers.anyInt(),
                ArgumentMatchers.anyInt())).thenReturn(100);
        var bubble = new HCZCGZFanObjectInstance.FanBubbleChild(
                new ObjectSpawn(100, 116, Sonic3kObjectIds.HCZ_CGZ_FAN, 0, 0, false, 0));
        bubble.setServices(new TestObjectServices().withWaterSystem(water));
        var before = bubble.captureRewindState();
        for (int replay = 0; replay < 2; replay++) {
            if (replay != 0) bubble.restoreRewindState(before);
            // HCZCGZFan_Bubble: water check, one MoveSprite2, then Draw_Sprite.
            bubble.update(0, null);
            assertEquals(108, bubble.getY(), "-$800 moves the native Y word by eight pixels once");
            assertTrue(!bubble.isDestroyed());
            bubble.update(1, null);
            assertEquals(100, bubble.getY());
            assertTrue(!bubble.isDestroyed(), "surface crossing is retired on the following pass");
            bubble.update(2, null);
            assertTrue(bubble.isDestroyed());
            assertEquals(100, bubble.getY(), "water check retires before movement");
        }
    }

    @Test
    void bubbleOutsideCameraMarginKeepsMovingBelowWaterAndReplaysAfterRestore() {
        AbstractObjectInstance.resetCameraBoundsForTests();
        var bubble = bubbleBelowWater(400, 100);
        var before = bubble.captureRewindState();
        for (int replay = 0; replay < 2; replay++) {
            if (replay != 0) bubble.restoreRewindState(before);
            // Draw_Sprite only enqueues: being beyond camera bottom +64 is not deletion.
            bubble.update(0, null);
            assertEquals(392, bubble.getY());
            assertTrue(!bubble.isDestroyed(), "only the water boundary retires this child");
        }
    }

    @Test
    void bubbleHasNoAgeCapAndReplaysLongMovementUntilWater() {
        AbstractObjectInstance.updateCameraBounds(0, 0, 320, 4096, 0);
        try {
            var bubble = bubbleBelowWater(2000, 100);
            var before = bubble.captureRewindState();
            for (int replay = 0; replay < 2; replay++) {
                if (replay != 0) bubble.restoreRewindState(before);
                // Independent deep-water control: remain visibly below water beyond120 passes.
                for (int update = 0; update < 200; update++) {
                    bubble.update(update, null);
                    assertTrue(!bubble.isDestroyed(), "native routine has no age counter");
                    assertEquals(2000 - 8 * (update + 1), bubble.getY());
                }
                for (int update = 200; update < 238; update++) bubble.update(update, null);
                assertEquals(96, bubble.getY());
                assertTrue(!bubble.isDestroyed(), "surface test precedes movement");
                bubble.update(238, null);
                assertTrue(bubble.isDestroyed());
                assertEquals(96, bubble.getY());
            }
        } finally {
            AbstractObjectInstance.resetCameraBoundsForTests();
        }
    }

    private static HCZCGZFanObjectInstance.FanBubbleChild bubbleBelowWater(int y, int waterLevel) {
        var water = Mockito.mock(WaterSystem.class);
        Mockito.when(water.getWaterLevelY(ArgumentMatchers.anyInt(),
                ArgumentMatchers.anyInt())).thenReturn(waterLevel);
        var bubble = new HCZCGZFanObjectInstance.FanBubbleChild(
                new ObjectSpawn(100, y, Sonic3kObjectIds.HCZ_CGZ_FAN, 0, 0, false, 0));
        bubble.setServices(new TestObjectServices().withWaterSystem(water));
        return bubble;
    }

    @Test
    void bubbleSpawnUsesLevelClockWhenVblankHasAdvancedDifferently() {
        var harness = fanSpawnHarness(4);
        var fan = underwaterAlwaysOnFan(harness.services());
        fan.update(1, null);
        assertEquals(1, harness.manager().activeObjectsOfType(
                HCZCGZFanObjectInstance.FanBubbleChild.class).size(),
                "Level_frame_counter low byte, not V_int_run_count, gates bubble allocation");
        Mockito.when(harness.level().getFrameCounter()).thenReturn(5);
        fan.update(4, null);
        assertEquals(1, harness.manager().activeObjectsOfType(
                HCZCGZFanObjectInstance.FanBubbleChild.class).size());
    }

    @Test
    void fanSoundUsesLevelClockWhenVblankHasAdvancedDifferently() {
        AbstractObjectInstance.resetCameraBoundsForTests();
        var level = Mockito.mock(LevelManager.class);
        Mockito.when(level.getFrameCounter()).thenReturn(15);
        var services = new FanSoundServices();
        services.withLevelManager(level);
        var fan = new HCZCGZFanObjectInstance(
                new ObjectSpawn(100, 200, Sonic3kObjectIds.HCZ_CGZ_FAN, 0x10, 0, false, 0));
        fan.setServices(services);
        fan.update(0, null);
        assertEquals(1, services.fanSounds);
        Mockito.when(level.getFrameCounter()).thenReturn(16);
        fan.update(15, null);
        assertEquals(1, services.fanSounds, "VBlank's sound phase must not drive this gate");
    }

    @Test
    void saturatedBubblePoolDoesNotConsumeRandomNumber() {
        var harness = fanSpawnHarness(4);
        harness.manager().reserveAllButNFreeSlots(0);
        var before = harness.rng().capture();
        underwaterAlwaysOnFan(harness.services()).update(4, null);
        assertEquals(before, harness.rng().capture(),
                "AllocateObject failure returns before Random_Number");
        assertTrue(harness.manager().activeObjectsOfType(
                HCZCGZFanObjectInstance.FanBubbleChild.class).isEmpty());
    }

    @Test
    void availableBubbleSlotConsumesOneRandomNumberAndUsesLowestFreeSlot() {
        var harness = fanSpawnHarness(4);
        var expected = new GameRng(GameRng.Flavour.S3K, 0x12345678);
        int expectedX = 100 + expected.nextInt(16) - 8;
        underwaterAlwaysOnFan(harness.services()).update(4, null);
        var bubbles = harness.manager().activeObjectsOfType(HCZCGZFanObjectInstance.FanBubbleChild.class);
        assertEquals(1, bubbles.size());
        assertEquals(expectedX, bubbles.getFirst().getX());
        assertEquals(ObjectSlotLayout.SONIC_3K.firstDynamicSlot(),
                bubbles.getFirst().getSlotIndex());
        assertEquals(expected.capture(), harness.rng().capture());
    }

    private static HCZCGZFanObjectInstance underwaterAlwaysOnFan(TestObjectServices services) {
        var fan = new HCZCGZFanObjectInstance(
                new ObjectSpawn(100, 200, Sonic3kObjectIds.HCZ_CGZ_FAN, 0x50, 0, false, 0));
        fan.setServices(services);
        return fan;
    }

    private record FanSpawnHarness(TestObjectServices services,
            ObjectManager manager,
            LevelManager level, GameRng rng) {}

    private static FanSpawnHarness fanSpawnHarness(int levelFrame) {
        AbstractObjectInstance.resetCameraBoundsForTests();
        var level = Mockito.mock(LevelManager.class);
        Mockito.when(level.getFrameCounter()).thenReturn(levelFrame);
        var rng = new GameRng(GameRng.Flavour.S3K, 0x12345678);
        var services = new TestObjectServices().withLevelManager(level).withRng(rng);
        var manager = new ObjectManager(List.of(),
                new Sonic3kObjectRegistry(), 0, null, null,
                GraphicsManager.getInstance(), null, services);
        services.withDirectObjectManager(manager);
        Mockito.when(level.getObjectManager()).thenReturn(manager);
        return new FanSpawnHarness(services, manager, level, rng);
    }

    private static class FanSoundServices extends TestObjectServices {
        int fanSounds;
        @Override public void playSfx(int soundId) {
            if (soundId == Sonic3kSfx.FAN_SMALL.id) fanSounds++;
        }
    }

    @Test
    void activeFanPushesUniqueQueryParticipantsIndependently() {
        HCZCGZFanObjectInstance fan = new HCZCGZFanObjectInstance(
                new ObjectSpawn(0x1000, 0x1000, Sonic3kObjectIds.HCZ_CGZ_FAN, 0x10, 0, false, 0));
        TestablePlayableSprite main = playerInFanColumn("sonic");
        TestablePlayableSprite tails = playerInFanColumn("tails");
        TestablePlayableSprite knuckles = playerInFanColumn("knuckles");
        fan.setServices(new QueryOnlyPlayerServices(main, List.of(tails, tails, knuckles)));

        fan.update(0, main);

        assertTrue(main.getAir());
        assertEquals(main.getCentreY(), tails.getCentreY(),
                "Duplicate sidekick entries should be de-duplicated by the participation policy");
        assertEquals(main.getCentreY(), knuckles.getCentreY(),
                "All engine sidekicks should still receive the independent fan push");
    }

    @Test
    void timerFanKeepsTerminalIdleTickBeforeSettingConveyorMarker() {
        HCZCGZFanObjectInstance fan = new HCZCGZFanObjectInstance(
                new ObjectSpawn(0x1000, 0x1000, Sonic3kObjectIds.HCZ_CGZ_FAN, 0x44, 0, false, 0));
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x1000, (short) 0x0F80);
        player.setCentreX((short) 0x1000);
        player.setCentreY((short) 0x0F80);
        ObjectControlState.nativeBits0To6CpuAllowedMovementSuppressed().applyTo(player);
        fan.setServices(new QueryOnlyPlayerServices(player, List.of()));

        for (int frame = 0; frame <= 121; frame++) {
            player.setGSpeed((short) 0);
            fan.update(frame, player);
            assertEquals(0, player.getGSpeed(),
                    "Timer fan should not set the conveyor marker during the terminal idle tick");
        }

        fan.update(122, player);

        assertEquals(1, player.getGSpeed(),
                "The next update after the terminal idle tick should resume fan/conveyor interaction");
    }

    @Test
    void fanLiftPreservesNativeYSubpixel() {
        HCZCGZFanObjectInstance fan = new HCZCGZFanObjectInstance(
                new ObjectSpawn(0x1000, 0x1000, Sonic3kObjectIds.HCZ_CGZ_FAN, 0x10, 0, false, 0));
        TestablePlayableSprite player = playerInFanColumn("sonic");
        player.setSubpixelRaw(0, 0xF000);
        fan.setServices(new QueryOnlyPlayerServices(player, List.of()));

        fan.update(0, player);

        assertEquals(0xF000, player.getYSubpixelRaw(),
                "Obj_HCZCGZFan add.w y_pos must preserve the low subpixel word");
        assertTrue(player.getAir());
    }

    @Test
    void fanAndPlatformUnloadFromRomOriginWhenSliding() {
        HCZCGZFanObjectInstance fan = new HCZCGZFanObjectInstance(
                new ObjectSpawn(0x1000, 0x1000, Sonic3kObjectIds.HCZ_CGZ_FAN, 0x10, 0, false, 0));
        fan.setFanX(0x0F80);

        HCZCGZFanObjectInstance.FanPlatformChild platform =
                new HCZCGZFanObjectInstance.FanPlatformChild(
                        new ObjectSpawn(0x1000, 0x101C, Sonic3kObjectIds.HCZ_CGZ_FAN, 0, 0, false, 0),
                        fan,
                        0x60,
                        true);

        assertEquals(0x1000, fan.getOutOfRangeReferenceX(),
                "Obj_HCZCGZFan Sprite_OnScreen_Test2 uses $40(a0), not current x_pos");
        assertEquals(0x1000, platform.getOutOfRangeReferenceX(),
                "Obj_HCZCGZFan platform Sprite_OnScreen_Test2 uses $40(a0), not current x_pos");
    }

    @Test
    void platformModeRunsFanAfterSlidingFanX() {
        HCZCGZFanObjectInstance fan = new HCZCGZFanObjectInstance(
                new ObjectSpawn(0x0FD0, 0x1050, Sonic3kObjectIds.HCZ_CGZ_FAN, 0x10, 0, false, 0));
        TestablePlayableSprite player = playerInFanColumn("sonic");

        HCZCGZFanObjectInstance.FanPlatformChild platform =
                new HCZCGZFanObjectInstance.FanPlatformChild(
                        new ObjectSpawn(0x1000, 0x0FC0, Sonic3kObjectIds.HCZ_CGZ_FAN, 0, 0, false, 0),
                        fan,
                        0x60,
                        false);
        QueryOnlyPlayerServices services = new QueryOnlyPlayerServices(player, List.of());
        fan.setServices(services);
        platform.setServices(services);

        platform.update(0, player);
        fan.setLatchedOn(false);
        fan.setFanX(0x0FD0);
        player.setCentreY((short) 0x0FD0);
        player.setYSpeed((short) 0x0200);

        platform.update(1, player);

        assertEquals(0, player.getYSpeed(),
                "Platform-mode fan child should apply lift after the platform writes the current fan x_pos");
        assertTrue(player.getAir());
    }

    private static TestablePlayableSprite playerInFanColumn(String code) {
        TestablePlayableSprite player = new TestablePlayableSprite(code, (short) 0x1000, (short) 0x1000);
        player.setCentreX((short) 0x1000);
        player.setCentreY((short) 0x1000);
        return player;
    }

    private static final class QueryOnlyPlayerServices extends TestObjectServices {
        private final PlayableEntity main;
        private final List<? extends PlayableEntity> queriedSidekicks;

        private QueryOnlyPlayerServices(PlayableEntity main, List<? extends PlayableEntity> queriedSidekicks) {
            this.main = main;
            this.queriedSidekicks = List.copyOf(queriedSidekicks);
        }

        @Override
        public ObjectPlayerQuery playerQuery() {
            return new ObjectPlayerQuery(() -> main, () -> queriedSidekicks);
        }

        @Override
        public List<PlayableEntity> sidekicks() {
            return List.of();
        }
    }
}
