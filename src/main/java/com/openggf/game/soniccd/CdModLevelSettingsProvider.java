package com.openggf.game.soniccd;

import com.openggf.data.Rom;
import com.openggf.game.DynamicWaterHandler;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.WaterDataProvider;
import com.openggf.game.modzone.ModLevelSettings;
import com.openggf.game.modzone.ModLevelSettingsProvider;
import com.openggf.game.modzone.ModZoneRuntimeServices;
import com.openggf.game.soniccd.level.CdStageLoader;
import com.openggf.game.soniccd.zones.ttz.TtzWaterDataProvider;
import com.openggf.level.Palette;

import java.io.IOException;
import java.util.Arrays;
import java.util.function.Supplier;

final class CdModLevelSettingsProvider implements ModLevelSettingsProvider {
    private final Supplier<CdDiscDataSource> disc;

    CdModLevelSettingsProvider(Supplier<CdDiscDataSource> disc) {
        this.disc = disc;
    }

    @Override public boolean supportsWater() { return true; }

    @Override public ModZoneRuntimeServices create(ModLevelSettings settings, Rom rom) throws IOException {
        validate(settings);
        if (settings.waterHeight() == null) return ModZoneRuntimeServices.empty();

        CdDiscDataSource source = disc.get();
        if (source == null) throw new IOException("Authored Sonic CD water requires the host disc");
        var stage = new CdStageLoader(source).readStage(CdStagePayload.R41A);
        var tidalTempest = new TtzWaterDataProvider(() -> stage);
        Palette[] palettes = tidalTempest.getUnderwaterPalette(null, CdZone.TIDAL_TEMPEST.ordinal(), 0,
                PlayerCharacter.SONIC_ALONE);
        if (palettes == null || palettes.length != 4) {
            throw new IOException("The disc did not supply four Tidal Tempest underwater palettes");
        }
        return ModZoneRuntimeServices.builder().water(new AuthoredWater(settings.waterHeight(), palettes)).build();
    }

    private record AuthoredWater(int height, Palette[] palettes) implements WaterDataProvider {
        @Override public boolean hasWater(int zone, int act, PlayerCharacter character) { return true; }
        @Override public int getStartingWaterLevel(int zone, int act) { return height; }

        @Override public Palette[] getUnderwaterPalette(Rom rom, int zone, int act, PlayerCharacter character) {
            return Arrays.stream(palettes).map(Palette::deepCopy).toArray(Palette[]::new);
        }

        @Override public DynamicWaterHandler getDynamicHandler(int zone, int act, PlayerCharacter character) {
            return null;
        }
    }
}
