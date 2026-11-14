package com.openggf.game.soniccd.objects.mmz;

import com.openggf.game.soniccd.objects.common.CdAnimator;
import com.openggf.game.soniccd.objects.common.CdObject;
import com.openggf.game.soniccd.zones.mmz.MmzStageSignals;
import com.openggf.level.objects.ObjectSpawn;

/** Shared helpers of the Metallic Madness object classes. */
abstract class MmzObject extends CdObject {

    /** {@code oXSub} and {@code oYSub}, the low words of the 16.16 position. */
    protected int xSub;
    protected int ySub;

    protected MmzObject(ObjectSpawn spawn, String name) {
        super(spawn, name);
    }

    /** An animator over the stage's script table {@code column} of {@link MmzObjectCatalogue}, or {@code null} before art. */
    protected CdAnimator mmzAnimator(String column, int anim) {
        var provider = artProvider();
        if (provider == null) return null;
        int table = MmzObjectCatalogue.offset(provider.payload(), column);
        return table < 0 ? null : new CdAnimator(provider.image(), table, anim);
    }

    /** Draws frame {@code frame} of sheet {@code kind} from the VRAM of the camera's section (see {@link MmzObjectArt}). */
    protected void drawSheet(String kind, int frame, boolean hFlip, boolean vFlip) {
        var camera = services().camera();
        String key = MmzObjectArt.key(artProvider(), kind, camera == null ? 0 : camera.getX());
        if (key != null) drawFrame(key, frame, hFlip, vFlip);
    }

    /** The zone's object-to-player signals ({@link MmzStageSignals}), or {@code null} outside a stage. */
    protected MmzStageSignals signals() {
        return MmzStageSignals.of(services().gameModule(), cdLevel());
    }

    /** {@code stage_layer}: 0 when the signals are unavailable (cleared RAM). */
    protected int stageLayer() {
        MmzStageSignals signals = signals();
        return signals == null ? 0 : signals.stageLayer();
    }

    /**
     * CheckBlockDown (common/object_block_collide.asm:128-160) at the object's X and the given Y, with its Y radius:
     * the distance from that radius point to the floor.
     */
    protected final int floorDistanceAt(int probeY, int radius) {
        return com.openggf.physics.ObjectTerrainUtils.checkFloorDist(x, probeY, radius).distance();
    }

    /** {@code add.l d0,oX(a0)}. */
    protected final void addToX(int velocity) {
        int position = ((x & 0xFFFF) << 16 | xSub) + velocity;
        x = position >>> 16;
        xSub = position & 0xFFFF;
    }

    /** {@code add.l d0,oY(a0)}. */
    protected final void addToY(int velocity) {
        int position = ((y & 0xFFFF) << 16 | ySub) + velocity;
        y = position >>> 16;
        ySub = position & 0xFFFF;
    }
}
