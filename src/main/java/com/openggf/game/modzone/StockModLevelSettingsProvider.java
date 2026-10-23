package com.openggf.game.modzone;

import com.openggf.data.Rom;
import com.openggf.game.DynamicWaterHandler;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.WaterDataProvider;
import com.openggf.level.Palette;
import com.openggf.level.scroll.ZoneScrollHandler;

import java.io.IOException;
import java.util.EnumMap;
import java.util.List;

/** Common construction mechanics; stock identity and ROM resource choices belong to each host. */
public abstract class StockModLevelSettingsProvider implements ModLevelSettingsProvider {
    private final List<StockScrollOption> sources;

    protected StockModLevelSettingsProvider(List<StockScrollOption> sources) {
        this.sources = List.copyOf(sources);
    }

    @Override public final List<StockScrollOption> stockScrollSources() { return sources; }
    @Override public final boolean supportsWater() { return true; }

    @Override public final ModZoneRuntimeServices create(ModLevelSettings settings, Rom rom) throws IOException {
        validate(settings);
        var result = ModZoneRuntimeServices.builder();
        if (settings.background() != null) {
            var source = settings.background();
            result.scroll(new SourceActScrollHandler(createStockScroll(source, rom), source.act()));
        }
        if (settings.waterHeight() != null) {
            if (rom == null) throw new IOException("Authored water requires the host ROM for its underwater palette");
            var palettes = new EnumMap<PlayerCharacter, Palette[]>(PlayerCharacter.class);
            for (var character : PlayerCharacter.values()) {
                Palette[] bank = loadWaterPalette(rom, character);
                if (bank == null || bank.length != 4)
                    throw new IOException("The host ROM did not supply a complete authored underwater palette for " + character);
                for (Palette line : bank) if (line == null)
                    throw new IOException("The host ROM supplied an incomplete authored underwater palette");
                palettes.put(character, copy(bank));
            }
            int height = settings.waterHeight();
            result.water(new WaterDataProvider() {
                @Override public boolean hasWater(int zone, int act, PlayerCharacter character) { return true; }
                @Override public int getStartingWaterLevel(int zone, int act) { return height; }
                @Override public Palette[] getUnderwaterPalette(Rom ignored, int zone, int act, PlayerCharacter character) {
                    return copy(palettes.get(character));
                }
                @Override public DynamicWaterHandler getDynamicHandler(int zone, int act, PlayerCharacter character) { return null; }
            });
        }
        return result.build();
    }

    protected abstract ZoneScrollHandler createStockScroll(ModLevelSettings.StockScrollSource source, Rom rom) throws IOException;
    protected abstract Palette[] loadWaterPalette(Rom rom, PlayerCharacter character);

    private static Palette[] copy(Palette[] source) {
        Palette[] result = new Palette[source.length];
        for (int line = 0; line < source.length; line++) result[line] = source[line].deepCopy();
        return result;
    }
}
