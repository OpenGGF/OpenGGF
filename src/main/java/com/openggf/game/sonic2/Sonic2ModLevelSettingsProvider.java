package com.openggf.game.sonic2;

import com.openggf.data.Rom;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.modzone.ModLevelSettings.StockScrollSource;
import com.openggf.game.modzone.StockModLevelSettingsProvider;
import com.openggf.game.sonic2.scroll.ParallaxTables;
import com.openggf.game.sonic2.scroll.Sonic2ZoneConstants;
import com.openggf.game.sonic2.scroll.SwScrlEhz;
import com.openggf.level.Palette;
import com.openggf.level.scroll.ZoneScrollHandler;

import java.io.IOException;
import java.util.List;

/** EHZ's camera/frame-only ROM deformation; authored water borrows the Aquatic Ruin palette. */
public final class Sonic2ModLevelSettingsProvider extends StockModLevelSettingsProvider {
    public Sonic2ModLevelSettingsProvider() {
        super(List.of(
                new StockScrollOption(new StockScrollSource(Sonic2ZoneConstants.ZONE_EHZ, 0), "Emerald Hill Act 1"),
                new StockScrollOption(new StockScrollSource(Sonic2ZoneConstants.ZONE_EHZ, 1), "Emerald Hill Act 2")));
    }

    @Override protected ZoneScrollHandler createStockScroll(StockScrollSource source, Rom rom) throws IOException {
        if (rom == null) throw new IOException("Emerald Hill deformation requires the host ROM ripple table");
        // The existing handler retains shipped fixBugs=0, including the final two unwritten scanlines.
        return new SwScrlEhz(new ParallaxTables(rom));
    }

    @Override protected Palette[] loadWaterPalette(Rom rom, PlayerCharacter character) {
        return new Sonic2WaterDataProvider().getUnderwaterPalette(rom, Sonic2ZoneConstants.ROM_ZONE_ARZ, 0, character);
    }
}
