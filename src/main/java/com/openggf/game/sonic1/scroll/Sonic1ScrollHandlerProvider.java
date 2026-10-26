package com.openggf.game.sonic1.scroll;

import com.openggf.data.Rom;
import com.openggf.game.internal.CheckpointBackgroundScroll;
import com.openggf.game.ScrollHandlerProvider;
import com.openggf.level.scroll.ZoneScrollHandler;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Scroll handler provider for Sonic the Hedgehog 1.
 * Provides zone-specific scroll handlers matching the original Deform_* routines
 * from the s1disasm DeformLayers.asm.
 *
 * <p>Sonic 1 scroll handlers do not require shared ROM data (no ParallaxTables).
 * Each handler manages its own BG camera state using frame deltas.
 */
public class Sonic1ScrollHandlerProvider implements ScrollHandlerProvider, com.openggf.game.internal.CheckpointScrollProvider {
    private static final Logger LOGGER = Logger.getLogger(Sonic1ScrollHandlerProvider.class.getName());

    private SwScrlGhz ghzHandler;
    private SwScrlEnd endHandler;
    private SwScrlLz lzHandler;
    private SwScrlLz sbz3Handler;
    private SwScrlMz mzHandler;
    private SwScrlSlz slzHandler;
    private SwScrlSyz syzHandler;
    private SwScrlSbz sbzHandler;
    private UniformQuarterSpeedScroll fzHandler;

    // Words unused by a zone's deform routine retain the load/checkpoint bank.
    // They are constant within a level timeline; full loads reset that timeline.
    private final CheckpointBackgroundScroll[] entryBackgrounds = new CheckpointBackgroundScroll[8];
    private int activeZone = -1;
    private int activeAct;
    private boolean loaded = false;

    @Override
    public void load(Rom rom) throws IOException {
        if (loaded) {
            return;
        }

        // Sonic 1 scroll handlers don't need ROM data - they compute
        // everything from camera positions and frame deltas.
        ghzHandler = new SwScrlGhz();
        endHandler = new SwScrlEnd();
        lzHandler = new SwScrlLz();
        sbz3Handler = new SwScrlLz();
        mzHandler = new SwScrlMz();
        slzHandler = new SwScrlSlz();
        syzHandler = new SwScrlSyz();
        sbzHandler = new SwScrlSbz();
        fzHandler = new UniformQuarterSpeedScroll();

        loaded = true;
        LOGGER.info("Sonic 1 scroll handlers loaded.");
    }

    @Override
    public ZoneScrollHandler getHandler(int zoneIndex) {
        if (!loaded) {
            return null;
        }

        return switch (scrollZone(zoneIndex)) {
            case Sonic1ZoneConstants.ZONE_GHZ -> ghzHandler;
            case Sonic1ZoneConstants.ZONE_LZ -> zoneIndex == Sonic1ZoneConstants.ZONE_SBZ
                    ? sbz3Handler : lzHandler;
            case Sonic1ZoneConstants.ZONE_MZ -> mzHandler;
            case Sonic1ZoneConstants.ZONE_SLZ -> slzHandler;
            case Sonic1ZoneConstants.ZONE_SYZ -> syzHandler;
            case Sonic1ZoneConstants.ZONE_SBZ -> sbzHandler;
            case Sonic1ZoneConstants.ZONE_FZ -> fzHandler;
            // ROM ending uses Deform_GHZ per-frame behavior but BgScroll_End
            // init (different BG camera starting positions for parallax offset).
            case Sonic1ZoneConstants.ZONE_ENDING -> endHandler;
            default -> null;
        };
    }

    @Override
    public void initForZone(int zoneId, int actId, int cameraX, int cameraY) {
        activeZone = zoneId;
        activeAct = actId;
        ZoneScrollHandler handler = getHandler(zoneId);
        if (handler != null) {
            entryBackgrounds[zoneId] = new CheckpointBackgroundScroll(
                    cameraX, cameraY, cameraX, cameraY, cameraX, 0);
            handler.init(actId, cameraX, cameraY);
        }
    }

    @Override
    public void restoreCheckpointBackground(int zoneId, CheckpointBackgroundScroll checkpoint) {
        if (checkpoint != null && getHandler(zoneId) instanceof Sonic1CheckpointScrollHandler handler) {
            entryBackgrounds[zoneId] = checkpoint;
            handler.restoreBackgroundScroll(checkpoint);
        }
    }

    @Override
    public CheckpointBackgroundScroll captureCheckpointBackground(int zoneId) {
        if (!(getHandler(zoneId) instanceof Sonic1CheckpointScrollHandler handler)) return null;
        CheckpointBackgroundScroll current = handler.captureBackgroundScroll();
        CheckpointBackgroundScroll entry = entryBackgrounds[zoneId];
        if (entry == null) return current;
        return switch (scrollZone(zoneId)) {
            case Sonic1ZoneConstants.ZONE_GHZ -> current;
            case Sonic1ZoneConstants.ZONE_MZ -> new CheckpointBackgroundScroll(
                    current.bgX(), current.bgY(), current.bg2X(), current.bgY(), current.bg3X(), current.bgY());
            case Sonic1ZoneConstants.ZONE_SBZ -> new CheckpointBackgroundScroll(
                    current.bgX(), current.bgY(), current.bg2X(),
                    activeAct == 0 ? current.bgY() : entry.bg2Y(), current.bg3X(),
                    activeAct == 0 ? current.bgY() : entry.bg3Y());
            default -> new CheckpointBackgroundScroll(current.bgX(), current.bgY(),
                    entry.bg2X(), entry.bg2Y(), entry.bg3X(), entry.bg3Y());
        };
    }

    private int scrollZone(int progressionZone) {
        // SBZ3 is native LZ act 4: Deform_Index ($62AE) and BgScroll_Index
        // ($61D4) dispatch by v_zone alone. Keep progression 5/2 intact while
        // resolving the same LZ owner for init, update, checkpoint and rewind.
        return progressionZone == Sonic1ZoneConstants.ZONE_SBZ
                && activeZone == progressionZone && activeAct == 2
                ? Sonic1ZoneConstants.ZONE_LZ : progressionZone;
    }

    @Override
    public ZoneConstants getZoneConstants() {
        return Sonic1ZoneConstants.INSTANCE;
    }
}
