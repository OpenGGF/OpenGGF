package com.openggf.game.soniccd.zones;

import com.openggf.data.Rom;
import com.openggf.game.DynamicWaterHandler;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.WaterDataProvider;
import com.openggf.game.soniccd.CdZone;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.level.Palette;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/** Routes the module's water queries to the stage zone's fragment; zones without water answer "none". */
public final class CdZoneWater implements WaterDataProvider {
    private final Supplier<CdStageImage> stage;
    private final Map<CdZone, Optional<WaterDataProvider>> providers = new EnumMap<>(CdZone.class);

    public CdZoneWater(Supplier<CdStageImage> stage) {
        this.stage = stage;
    }

    private WaterDataProvider zone(int zoneId) {
        CdZone[] zones = CdZone.values();
        if (zoneId < 0 || zoneId >= zones.length) return null;
        return providers.computeIfAbsent(zones[zoneId],
                z -> Optional.ofNullable(CdZones.fragment(z).createWaterProvider(stage))).orElse(null);
    }

    @Override
    public boolean hasWater(int zoneId, int actId, PlayerCharacter character) {
        WaterDataProvider p = zone(zoneId);
        return p != null && p.hasWater(zoneId, actId, character);
    }

    @Override
    public int getStartingWaterLevel(int zoneId, int actId) {
        WaterDataProvider p = zone(zoneId);
        return p == null ? 0 : p.getStartingWaterLevel(zoneId, actId);
    }

    @Override
    public Palette[] getUnderwaterPalette(Rom rom, int zoneId, int actId, PlayerCharacter character) {
        WaterDataProvider p = zone(zoneId);
        return p == null ? null : p.getUnderwaterPalette(rom, zoneId, actId, character);
    }

    @Override
    public DynamicWaterHandler getDynamicHandler(int zoneId, int actId, PlayerCharacter character) {
        WaterDataProvider p = zone(zoneId);
        return p == null ? null : p.getDynamicHandler(zoneId, actId, character);
    }

    @Override
    public int getWaterSpeed(int zoneId, int actId) {
        WaterDataProvider p = zone(zoneId);
        return p == null ? 1 : p.getWaterSpeed(zoneId, actId);
    }

    @Override
    public int getGameplayWaterLevelOffset(int zoneId, int actId) {
        WaterDataProvider p = zone(zoneId);
        return p == null ? 0 : p.getGameplayWaterLevelOffset(zoneId, actId);
    }

    @Override
    public int getVisualWaterLevelOffset(int zoneId, int actId) {
        WaterDataProvider p = zone(zoneId);
        return p == null ? 0 : p.getVisualWaterLevelOffset(zoneId, actId);
    }
}
