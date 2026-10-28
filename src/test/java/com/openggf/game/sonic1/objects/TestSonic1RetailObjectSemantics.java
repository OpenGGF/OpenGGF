package com.openggf.game.sonic1.objects;

import com.openggf.data.RomByteReader;
import com.openggf.game.sonic1.Sonic1FloatingBlockState;
import com.openggf.game.sonic1.Sonic1RomProfile;
import com.openggf.game.sonic1.Sonic1SwitchManager;
import com.openggf.game.sonic1.constants.Sonic1Constants;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_1)
class TestSonic1RetailObjectSemantics {
    @Test
    void originalSpringYardBlockDoesNotBankTheRevisionOneDestinationLatch() throws Exception {
        var reader = RomByteReader.fromRom(TestEnvironment.currentRom());
        var switches = new Sonic1SwitchManager();
        switches.setBit(0x0F, 0);
        var state = new Sonic1FloatingBlockState();
        var services = new StubObjectServices() {
            @Override public RomByteReader romReader() { return reader; }
            @SuppressWarnings("unchecked")
            @Override public <T> T gameService(Class<T> type) {
                if (type == Sonic1SwitchManager.class) return (T) switches;
                if (type == Sonic1FloatingBlockState.class) return (T) state;
                return null;
            }
        };
        var block = new Sonic1FloatingBlockObjectInstance(
                new ObjectSpawn(0x1BB8, 0x400, 0x56, 0x37, 0, false, 0), Sonic1Constants.ZONE_SYZ);
        block.setServices(services);
        for (int frame = 0; frame < 0x380; frame++) block.update(frame, null);
        assertEquals(0x1F38, block.getX());
        assertEquals(!Sonic1RomProfile.isRev00(reader), state.isTunnelBlockAtDestination());
    }

    @Test
    void dynamicWaterStopsDuringDeathAndOnlyRevisionOneStopsEarlierDrowning() throws Exception {
        var rom = TestEnvironment.currentRom();
        var provider = new com.openggf.game.sonic1.Sonic1ZoneFeatureProvider();
        provider.initZoneFeatures(rom, Sonic1Constants.ZONE_GHZ, 0, 0);
        var player = org.mockito.Mockito.mock(com.openggf.sprites.playable.AbstractPlayableSprite.class);
        org.mockito.Mockito.when(player.isDrowningPreDeath()).thenReturn(true);
        assertEquals(Sonic1RomProfile.isRev00(rom), provider.shouldAdvanceDynamicWater(player));
        org.mockito.Mockito.when(player.getDead()).thenReturn(true);
        assertFalse(provider.shouldAdvanceDynamicWater(player));
    }

    @Test
    void risingBubbleUsesTheOriginalRevisionPositiveWobblePeak() throws Exception {
        var reader = RomByteReader.fromRom(TestEnvironment.currentRom());
        var level = org.mockito.Mockito.mock(com.openggf.level.Level.class);
        var water = org.mockito.Mockito.mock(com.openggf.level.WaterSystem.class);
        org.mockito.Mockito.when(water.hasWater(0, 0)).thenReturn(true);
        org.mockito.Mockito.when(water.getGameplayWaterLevelY(0, 0)).thenReturn(0);
        var services = new StubObjectServices() {
            @Override public RomByteReader romReader() { return reader; }
            @Override public com.openggf.level.Level currentLevel() { return level; }
            @Override public com.openggf.level.WaterSystem waterSystem() { return water; }
        };
        var bubble = new Sonic1BubblesObjectInstance(new ObjectSpawn(200, 100, 0x64, 0, 0, false, 0));
        bubble.setServices(services);
        var routine = Sonic1BubblesObjectInstance.class.getDeclaredField("routine");
        routine.setAccessible(true);
        routine.setInt(bubble, 4);
        var angle = Sonic1BubblesObjectInstance.class.getDeclaredField("wobbleAngle");
        angle.setAccessible(true);
        angle.setInt(bubble, 0x20);
        bubble.update(1, null);
        assertEquals(Sonic1RomProfile.isRev00(reader) ? 204 : 203, bubble.getX());
    }

    @Test
    void endingLogoHoldsForTheRevisionSpecificDuration() throws Exception {
        var reader = RomByteReader.fromRom(TestEnvironment.currentRom());
        int[] transitions = {0};
        var services = new StubObjectServices() {
            @Override public RomByteReader romReader() { return reader; }
            @Override public void requestCreditsTransition() { transitions[0]++; }
        };
        var logo = new Sonic1EndingSTHObjectInstance();
        logo.setServices(services);
        for (int frame = 0; frame < 15; frame++) logo.update(frame, null);
        int delay = Sonic1RomProfile.isRev00(reader) ? 120 : 300;
        for (int frame = 0; frame < delay; frame++) logo.update(15 + frame, null);
        assertEquals(0, transitions[0]);
        logo.update(15 + delay, null);
        assertEquals(1, transitions[0]);
        assertTrue(logo.isDestroyed());
    }
}
