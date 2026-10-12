package eggsky.space;

import eggsky.world.PlanetSpec;

/**
 * A sphere in a star system: a planet (with its spec) or the space station. Position and size
 * in system units; the texture is built later, a planet at a time.
 */
public final class Body {
    public final double x;
    public final double y;
    public final double z;
    public final double radius;
    /** Spin axis tilt (radians) and current spin angle. */
    public final double tilt;
    public double spin;
    public final double spinSpeed;
    public final PlanetSpec spec;
    public final boolean station;
    public PlanetTexture texture;
    public final boolean ringed;
    public final int ringColour;
    public final int index;

    public Body(int index, double x, double y, double z, double radius, double tilt, double spinSpeed, PlanetSpec spec,
            boolean station) {
        this.index = index;
        this.x = x;
        this.y = y;
        this.z = z;
        this.radius = radius;
        this.tilt = tilt;
        this.spinSpeed = spinSpeed;
        this.spec = spec;
        this.station = station;
        this.ringed = spec != null && spec.ringed;
        this.ringColour = spec == null ? 0 : spec.ringColour;
    }

    public double distance(double px, double py, double pz) {
        return Math.sqrt((px - x) * (px - x) + (py - y) * (py - y) + (pz - z) * (pz - z));
    }
}
