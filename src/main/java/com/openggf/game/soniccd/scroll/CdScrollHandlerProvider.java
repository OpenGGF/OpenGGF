package com.openggf.game.soniccd.scroll;

import com.openggf.data.Rom;
import com.openggf.game.ScrollHandlerProvider;
import com.openggf.game.soniccd.CdZone;
import com.openggf.game.soniccd.SonicCdZoneRegistry;
import com.openggf.game.soniccd.zones.CdZones;
import com.openggf.level.scroll.ZoneScrollHandler;

/** Scroll handlers per stage, supplied by each zone's fragment. */
public final class CdScrollHandlerProvider implements ScrollHandlerProvider {
    private final ZoneScrollHandler[] handlers = new ZoneScrollHandler[CdZone.values().length];
    private int act;

    @Override
    public void load(Rom rom) {
        // Disc-backed: no cartridge data is needed.
    }

    @Override
    public void initForZone(int zoneId, int actId, int cameraX, int cameraY) {
        act = actId;
        if (zoneId >= 0 && zoneId < handlers.length) {
            CdZone zone = CdZone.values()[zoneId];
            handlers[zoneId] = CdZones.fragment(zone).createScrollHandler(SonicCdZoneRegistry.payload(zoneId, actId));
        }
    }

    @Override
    public ZoneScrollHandler getHandler(int zoneIndex) {
        if (zoneIndex < 0 || zoneIndex >= handlers.length) return null;
        if (handlers[zoneIndex] == null) {
            handlers[zoneIndex] = CdZones.fragment(CdZone.values()[zoneIndex])
                    .createScrollHandler(SonicCdZoneRegistry.payload(zoneIndex, act));
        }
        return handlers[zoneIndex];
    }

    @Override
    public ZoneConstants getZoneConstants() {
        return new ZoneConstants() {
            @Override public int getZoneCount() { return CdZone.values().length; }
            @Override public String getZoneName(int index) { return CdZone.values()[index].name(); }
        };
    }
}
