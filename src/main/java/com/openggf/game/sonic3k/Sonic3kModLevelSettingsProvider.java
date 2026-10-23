package com.openggf.game.sonic3k;

import com.openggf.data.Rom;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.modzone.ModLevelSettings.StockScrollSource;
import com.openggf.game.modzone.StockModLevelSettingsProvider;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.scroll.SwScrlFbz;
import com.openggf.game.sonic3k.scroll.SwScrlS3kDez;
import com.openggf.level.Palette;
import com.openggf.level.scroll.ZoneScrollHandler;

import java.util.List;

/** Event-independent stock deformations; authored water borrows Hydrocity Act 1's ROM palette. */
public final class Sonic3kModLevelSettingsProvider extends StockModLevelSettingsProvider {
    public Sonic3kModLevelSettingsProvider() {
        super(List.of(
                new StockScrollOption(new StockScrollSource(Sonic3kZoneIds.ZONE_FBZ, 0), "Flying Battery Act 1 (indoor deformation)"),
                new StockScrollOption(new StockScrollSource(Sonic3kZoneIds.ZONE_DEZ, 0), "Death Egg Act 1 (fixed background)"),
                new StockScrollOption(new StockScrollSource(Sonic3kZoneIds.ZONE_DEZ, 1), "Death Egg Act 2 (fixed background)")));
    }

    @Override protected ZoneScrollHandler createStockScroll(StockScrollSource source, Rom rom) {
        // Authored layouts own no stock indoor/outdoor redraw or boss state. Supplying null
        // selects the existing pure FBZ indoor deformation and cannot consume campaign events.
        return source.zone() == Sonic3kZoneIds.ZONE_FBZ ? new SwScrlFbz(() -> null) : new SwScrlS3kDez();
    }

    @Override protected Palette[] loadWaterPalette(Rom rom, PlayerCharacter character) {
        return new Sonic3kWaterDataProvider().getUnderwaterPalette(rom, Sonic3kZoneIds.ZONE_HCZ, 0, character);
    }
}
