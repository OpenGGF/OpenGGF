package com.openggf.game;

import com.openggf.game.modzone.ModZoneRuntimeContribution;
import com.openggf.level.LevelDescriptor;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

/** Complete forwarding base, retaining stable zone identity and completion topology. */
@ModApi
public class DelegatingZoneRegistry implements ZoneRegistry {
    protected final ZoneRegistry source;
    public DelegatingZoneRegistry(ZoneRegistry source) { this.source = Objects.requireNonNull(source, "source"); }
    @Override public ModZoneRuntimeContribution modZoneRuntimeContribution(int levelIndex) { return source.modZoneRuntimeContribution(levelIndex); }
    @Override public int getZoneCount() { return source.getZoneCount(); }
    @Override public int getActCount(int zoneIndex) { return source.getActCount(zoneIndex); }
    @Override public String getZoneName(int zoneIndex) { return source.getZoneName(zoneIndex); }
    @Override public int[] getStartPosition(int zoneIndex, int actIndex) { return source.getStartPosition(zoneIndex, actIndex); }
    @Override public List<LevelDescriptor> getLevelDataForZone(int zoneIndex) { return source.getLevelDataForZone(zoneIndex); }
    @Override public List<List<LevelDescriptor>> getAllZones() { return source.getAllZones(); }
    @Override public int getMusicId(int zoneIndex, int actIndex) { return source.getMusicId(zoneIndex, actIndex); }
    @Override public MusicReference getMusicReference(int zoneIndex, int actIndex) { return source.getMusicReference(zoneIndex, actIndex); }
    @Override public ZoneKey zoneKey(int zoneIndex) { return source.zoneKey(zoneIndex); }
    @Override public OptionalInt resolveZoneKey(ZoneKey key) { return source.resolveZoneKey(key); }
    @Override public int resolveStockZoneAnchor(String stockKey) { return source.resolveStockZoneAnchor(stockKey); }
    @Override public ZoneProgressionPlan.ZoneTopology progressionTopology() { return source.progressionTopology(); }
    @Override public ZoneProgressionPlan progressionPlan() { return source.progressionPlan(); }
}
