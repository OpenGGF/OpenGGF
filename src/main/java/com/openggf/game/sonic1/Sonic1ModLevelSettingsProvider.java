package com.openggf.game.sonic1;

import com.openggf.data.Rom;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.modzone.ModLevelSettings.StockScrollSource;
import com.openggf.game.modzone.StockModLevelSettingsProvider;
import com.openggf.game.sonic1.constants.Sonic1Constants;
import com.openggf.game.sonic1.scroll.Sonic1ZoneConstants;
import com.openggf.game.sonic1.scroll.SwScrlGhz;
import com.openggf.game.sonic1.scroll.UniformQuarterSpeedScroll;
import com.openggf.level.Palette;
import com.openggf.level.scroll.ZoneScrollHandler;

import java.util.List;

/** Standalone GHZ/FZ deformation, with Labyrinth's ROM underwater palette for authored water. */
public final class Sonic1ModLevelSettingsProvider extends StockModLevelSettingsProvider {
    public Sonic1ModLevelSettingsProvider() {
        super(List.of(
                new StockScrollOption(new StockScrollSource(Sonic1ZoneConstants.ZONE_GHZ, 0), "Green Hill Act 1"),
                new StockScrollOption(new StockScrollSource(Sonic1ZoneConstants.ZONE_GHZ, 1), "Green Hill Act 2"),
                new StockScrollOption(new StockScrollSource(Sonic1ZoneConstants.ZONE_GHZ, 2), "Green Hill Act 3"),
                new StockScrollOption(new StockScrollSource(Sonic1ZoneConstants.ZONE_FZ, 0), "Final Zone")));
    }

    @Override protected ZoneScrollHandler createStockScroll(StockScrollSource source, Rom rom) {
        if (source.zone() == Sonic1ZoneConstants.ZONE_FZ) return new UniformQuarterSpeedScroll();
        // GHZ's native initializer has a one-argument overload; hosted loads still initialize it explicitly.
        return new SwScrlGhz() {
            @Override public void init(int act, int cameraX, int cameraY) { super.init(cameraX); }
        };
    }

    @Override protected Palette[] loadWaterPalette(Rom rom, PlayerCharacter character) {
        return new Sonic1WaterDataProvider().getUnderwaterPalette(rom, Sonic1Constants.ZONE_LZ, 0, character);
    }
}
