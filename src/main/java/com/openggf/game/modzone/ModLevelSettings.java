package com.openggf.game.modzone;

import com.openggf.game.GameplayLaunchTeam;
import com.openggf.game.ModApi;

/** Immutable authored runtime choices; null components retain the flat, dry, unrestricted defaults. */
@ModApi
public record ModLevelSettings(StockScrollSource background, Integer waterHeight, GameplayLaunchTeam team) {
    public static final ModLevelSettings DEFAULT = new ModLevelSettings(null, null, null);

    public ModLevelSettings {
        if (waterHeight != null && (waterHeight < Short.MIN_VALUE || waterHeight > Short.MAX_VALUE))
            throw new IllegalArgumentException("Authored water height must fit a signed 16-bit Y coordinate");
        if (team != null && team.sidekicks().size() > 16)
            throw new IllegalArgumentException("Authored team supports at most 16 sidekicks");
    }

    public ModLevelSettings withBackground(StockScrollSource source) {
        return new ModLevelSettings(source, waterHeight, team);
    }

    public ModLevelSettings withWaterHeight(Integer height) {
        return new ModLevelSettings(background, height, team);
    }

    public ModLevelSettings withTeam(GameplayLaunchTeam value) {
        return new ModLevelSettings(background, waterHeight, value);
    }

    /** Host stock zone/act identity, interpreted only by the selected host's capability. */
    @ModApi
    public record StockScrollSource(int zone, int act) {
        public StockScrollSource {
            if (zone < 0 || zone > 255 || act < 0 || act > 255)
                throw new IllegalArgumentException("Stock scroll zone and act must be unsigned bytes");
        }
    }
}
