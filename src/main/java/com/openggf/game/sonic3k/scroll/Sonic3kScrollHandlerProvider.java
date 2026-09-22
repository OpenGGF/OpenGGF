package com.openggf.game.sonic3k.scroll;

import com.openggf.data.Rom;
import com.openggf.game.GameServices;
import com.openggf.game.ScrollHandlerProvider;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.level.scroll.ZoneScrollHandler;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Scroll handler provider for Sonic 3 &amp; Knuckles.
 * Provides zone-specific scroll handlers for parallax effects.
 */
public class Sonic3kScrollHandlerProvider implements ScrollHandlerProvider {
    private static final Logger LOGGER = Logger.getLogger(Sonic3kScrollHandlerProvider.class.getName());

    private boolean loaded = false;

    /** No act declared for this zone yet; the lookup keeps the historic zone-only mapping. */
    private static final int ACT_UNKNOWN = -1;
    private int lastInitZone = -1;
    private int lastInitAct = ACT_UNKNOWN;

    private SwScrlAiz aizHandler;
    private SwScrlCnz cnzHandler;
    private SwScrlHcz hczHandler;
    private SwScrlIcz iczHandler;
    private SwScrlLbz lbzHandler;
    private SwScrlMhz mhzHandler;
    private SwScrlMgz mgzHandler;
    private SwScrlFbz fbzHandler;
    private SwScrlSoz sozHandler;
    private SwScrlHpz hpzHandler;
    private SwScrlLrz lrzHandler;
    private SwScrlLrz3 lrzBossHandler;
    private SwScrlDdz ddzHandler;
    private SwScrlSsz sszHandler;
    private SwScrlGumball gumballHandler;
    private SwScrlPachinko pachinkoHandler;
    private SwScrlSlots slotsHandler;
    private SwScrlS3kDefault defaultHandler;

    @Override
    public void load(Rom rom) throws IOException {
        if (loaded) {
            return;
        }
        aizHandler = new SwScrlAiz();
        cnzHandler = new SwScrlCnz();
        byte[] hczWaterlineData = null;
        try {
            hczWaterlineData = rom.readBytes(
                    Sonic3kConstants.HCZ_WATERLINE_SCROLL_DATA_ADDR,
                    Sonic3kConstants.HCZ_WATERLINE_SCROLL_DATA_SIZE);
        } catch (IOException e) {
            LOGGER.fine(() -> "HCZ waterline scroll data unavailable; using fallback HCZ handler: "
                    + e.getMessage());
        }
        hczHandler = new SwScrlHcz(hczWaterlineData);
        iczHandler = new SwScrlIcz();
        byte[] lbzWaterlineData = null;
        try {
            lbzWaterlineData = rom.readBytes(
                    Sonic3kConstants.LBZ_WATERLINE_SCROLL_DATA_ADDR,
                    Sonic3kConstants.LBZ_WATERLINE_SCROLL_DATA_SIZE);
        } catch (IOException e) {
            LOGGER.fine(() -> "LBZ waterline scroll data unavailable; using fallback LBZ waterline lookup: "
                    + e.getMessage());
        }
        byte[] lbzDeathEggWaveData = null;
        try {
            lbzDeathEggWaveData = rom.readBytes(Sonic3kConstants.LBZ_DEATH_EGG_WAVE_DATA_ADDR,
                    Sonic3kConstants.LBZ_DEATH_EGG_WAVE_DATA_SIZE);
        } catch (IOException e) {
            LOGGER.fine(() -> "LBZ Death Egg wave data unavailable: " + e.getMessage());
        }
        lbzHandler = new SwScrlLbz(lbzWaterlineData, lbzDeathEggWaveData);
        mhzHandler = new SwScrlMhz();
        mgzHandler = new SwScrlMgz();
        fbzHandler = new SwScrlFbz();
        sozHandler = new SwScrlSoz(rom);
        hpzHandler = new SwScrlHpz();
        lrzHandler = new SwScrlLrz();
        lrzBossHandler = new SwScrlLrz3(rom);
        ddzHandler = new SwScrlDdz();
        sszHandler = new SwScrlSsz();
        gumballHandler = new SwScrlGumball();
        pachinkoHandler = new SwScrlPachinko();
        slotsHandler = new SwScrlSlots();
        defaultHandler = new SwScrlS3kDefault();
        loaded = true;
        LOGGER.info("Sonic 3K scroll handlers loaded.");
    }

    @Override
    public ZoneScrollHandler getHandler(int zoneIndex) {
        return getHandler(zoneIndex, currentActFor(zoneIndex));
    }

    /**
     * The act to key the lookup on when the caller gives only a zone. {@link #initForZone} records
     * the act of the current level, and a loaded level that has not yet run a scroll frame still
     * knows its own feature act. Anything else - a lookup for a zone that is not loaded, or no
     * runtime at all - is {@link #ACT_UNKNOWN}.
     */
    private int currentActFor(int zoneIndex) {
        if (zoneIndex == lastInitZone) {
            return lastInitAct;
        }
        var level = GameServices.hasRuntime() ? GameServices.levelOrNull() : null;
        if (level != null && level.getFeatureZoneId() == zoneIndex) {
            return level.getFeatureActId();
        }
        return ACT_UNKNOWN;
    }

    /**
     * Zones {@code $16} and {@code $17} pair a Hidden Palace layout with a boss act: {@code $1601}
     * is the playable Hidden Palace and {@code $1701} the Super Emerald sanctuary, while
     * {@code $1600} is the Lava Reef boss act and {@code $1700} Death Egg act 3. Only act 1 of each
     * runs {@code HPZ_BackgroundEvent}, so the handler lookup has to know the act.
     *
     * <p>{@code ParallaxManager.update} calls {@link #initForZone} before every handler lookup, so
     * the live path always resolves a known act. A lookup for a zone that is not the current one
     * passes {@link #ACT_UNKNOWN} and keeps the historic zone-only mapping.
     */
    public ZoneScrollHandler getHandler(int zoneIndex, int actIndex) {
        if (!loaded) {
            return null;
        }

        return switch (zoneIndex) {
            case Sonic3kZoneConstants.ZONE_AIZ -> aizHandler;
            case Sonic3kZoneConstants.ZONE_CNZ -> cnzHandler;
            case Sonic3kZoneConstants.ZONE_HCZ -> hczHandler;
            case Sonic3kZoneConstants.ZONE_ICZ -> iczHandler;
            case Sonic3kZoneConstants.ZONE_LBZ -> lbzHandler;
            case Sonic3kZoneConstants.ZONE_MHZ -> mhzHandler;
            case Sonic3kZoneConstants.ZONE_MGZ -> mgzHandler;
            case Sonic3kZoneConstants.ZONE_FBZ -> fbzHandler;
            case Sonic3kZoneConstants.ZONE_SOZ -> sozHandler;
            case Sonic3kZoneConstants.ZONE_DDZ -> ddzHandler;
            case Sonic3kZoneConstants.ZONE_LRZ -> lrzHandler;
            case Sonic3kZoneIds.ZONE_HPZ -> actIndex == 0 ? lrzBossHandler : hpzHandler;
            case Sonic3kZoneIds.ZONE_DEZ_BOSS_SS_ARENA -> actIndex == 0 ? defaultHandler : hpzHandler;
            case Sonic3kZoneConstants.ZONE_SSZ -> sszHandler;
            case Sonic3kZoneIds.ZONE_GUMBALL -> gumballHandler;
            case Sonic3kZoneIds.ZONE_GLOWING_SPHERE -> pachinkoHandler;
            case Sonic3kZoneIds.ZONE_SLOT_MACHINE -> slotsHandler;
            default -> defaultHandler;
        };
    }

    @Override
    public ZoneConstants getZoneConstants() {
        return Sonic3kZoneConstants.INSTANCE;
    }

    @Override
    public void initForZone(int zoneId, int actId, int cameraX, int cameraY) {
        lastInitZone = zoneId;
        lastInitAct = actId;
        ZoneScrollHandler handler = getHandler(zoneId, actId);
        if (handler != null) {
            handler.init(actId, cameraX, cameraY);
        }
    }
}
