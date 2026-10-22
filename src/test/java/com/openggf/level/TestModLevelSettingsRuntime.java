package com.openggf.level;

import com.openggf.game.DynamicWaterHandler;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.WaterDataProvider;
import com.openggf.game.modzone.ModZoneRuntimeServices;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.sonic3k.scroll.SwScrlS3kDez;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestModLevelSettingsRuntime {
    @Test void explicitRuntimeRetainsItsOtherOwnersAlongsideAuthoredScrollAndWater() {
        var scroll = new SwScrlS3kDez();
        var water = water();
        var rewind = new RewindSnapshottable<Integer>() {
            @Override public String key() { return "settings-runtime-test"; }
            @Override public Integer capture() { return 1; }
            @Override public void restore(Integer value) { }
        };
        var builtIn = ModZoneRuntimeServices.builder().scroll(scroll).water(water).build();
        var explicit = ModZoneRuntimeServices.builder().rewindAdapter("custom-state", rewind).build();
        var combined = LevelContributedZoneRuntime.combine(builtIn, explicit);
        assertSame(scroll, combined.scroll());
        assertSame(water, combined.water());
        assertSame(rewind, combined.rewindAdapters().get("custom-state"));
        assertSame(builtIn, LevelContributedZoneRuntime.combine(builtIn, null));
        assertSame(explicit, LevelContributedZoneRuntime.combine(null, explicit));
    }

    @Test void competingRuntimeOwnersFailInsteadOfSilentlyIgnoringSettings() {
        var scroll = ModZoneRuntimeServices.builder().scroll(new SwScrlS3kDez()).build();
        var water = ModZoneRuntimeServices.builder().water(water()).build();
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> LevelContributedZoneRuntime.combine(scroll, scroll)).getMessage().contains("scroll"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> LevelContributedZoneRuntime.combine(water, water)).getMessage().contains("water"));
    }

    private static WaterDataProvider water() {
        return new WaterDataProvider() {
            @Override public boolean hasWater(int zone, int act, PlayerCharacter character) { return true; }
            @Override public int getStartingWaterLevel(int zone, int act) { return 300; }
            @Override public Palette[] getUnderwaterPalette(com.openggf.data.Rom rom, int zone, int act, PlayerCharacter character) { return null; }
            @Override public DynamicWaterHandler getDynamicHandler(int zone, int act, PlayerCharacter character) { return null; }
        };
    }
}
