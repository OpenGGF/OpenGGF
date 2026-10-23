package com.openggf.game.sonic3k.competition;

import com.openggf.camera.Camera;
import com.openggf.data.Rom;
import com.openggf.game.ZoneFeatureProvider;
import com.openggf.game.internal.RetainedPlaneDescriptorSource;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.Objects;
import java.util.function.Supplier;

/** Native competition features: SST objects own interactions; retained planes own descriptor RAM. */
public final class S3kCompetitionZoneFeatures implements ZoneFeatureProvider,RetainedPlaneDescriptorSource {
    private final Supplier<S3kCompetitionPlanes> planes;
    public S3kCompetitionZoneFeatures(Supplier<S3kCompetitionPlanes> planes) { this.planes=Objects.requireNonNull(planes); }
    @Override public Plane foregroundPlane() {
        var current=planes.get();return current==null?null:current.foregroundPlane();
    }
    @Override public Plane backgroundPlane() {
        var current=planes.get();return current==null?null:current.backgroundPlane();
    }
    @Override public void initZoneFeatures(Rom rom,int zoneIndex,int actIndex,int cameraX) {
        Objects.requireNonNull(rom);S3kCompetitionSession.Arena.forZone(zoneIndex);
    }
    @Override public void update(AbstractPlayableSprite player,int cameraX,int zoneIndex) { }
    @Override public void reset() { }
    @Override public boolean hasCollisionFeatures(int zoneIndex) { return false; }
    @Override public boolean hasWater(int zoneIndex) { return false; }
    @Override public int getWaterLevel(int zoneIndex,int actIndex) { return Integer.MAX_VALUE; }
    @Override public void render(Camera camera,int frameCounter) { }
    @Override public int ensurePatternsCached(com.openggf.graphics.GraphicsManager graphics,int baseIndex) { return baseIndex; }
    // The native competition body omits OscillateNumDo (loc_66EA); initialize once, retain RAM.
    @Override public boolean shouldAdvanceGlobalOscillation(int zoneIndex,int actIndex) { return false; }
    @Override public boolean useSpriteSatMasking(int zoneIndex) { return true; }
}
