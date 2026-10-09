package eggsky.space;

/**
 * A first-person camera: position plus yaw, pitch and roll, giving an orthonormal basis
 * (right, up, forward). {@link #project} maps a world point to screen pixels.
 */
public final class Cam {
    public double x;
    public double y;
    public double z;
    public double yaw;
    public double pitch;
    public double roll;
    public final double[] right = new double[3];
    public final double[] up = new double[3];
    public final double[] forward = new double[3];
    /** Focal length in screen pixels (for the full-resolution screen). */
    public double focal = 230;
    public int width = 400;
    public int height = 224;

    public void update() {
        double cy = Math.cos(yaw);
        double sy = Math.sin(yaw);
        double cp = Math.cos(pitch);
        double sp = Math.sin(pitch);
        forward[0] = sy * cp;
        forward[1] = sp;
        forward[2] = cy * cp;
        // Right is horizontal, perpendicular to forward.
        double rx = cy;
        double rz = -sy;
        double ux = -sy * sp;
        double uy = cp;
        double uz = -cy * sp;
        double cr = Math.cos(roll);
        double sr = Math.sin(roll);
        right[0] = rx * cr + ux * sr;
        right[1] = uy * sr;
        right[2] = rz * cr + uz * sr;
        up[0] = ux * cr - rx * sr;
        up[1] = uy * cr;
        up[2] = uz * cr - rz * sr;
    }

    /** Camera-space coordinates of a world point: {right, up, depth}. */
    public double[] toCamera(double px, double py, double pz) {
        double dx = px - x;
        double dy = py - y;
        double dz = pz - z;
        return new double[] {
                dx * right[0] + dy * right[1] + dz * right[2],
                dx * up[0] + dy * up[1] + dz * up[2],
                dx * forward[0] + dy * forward[1] + dz * forward[2]};
    }

    /** Screen {x, y, depth} or null when behind the camera. */
    public double[] project(double px, double py, double pz) {
        double[] c = toCamera(px, py, pz);
        if (c[2] < 1) {
            return null;
        }
        return new double[] {width / 2.0 + c[0] / c[2] * focal, height / 2.0 - c[1] / c[2] * focal, c[2]};
    }

    /** Screen direction of a world-space direction (for things at infinity), or null when behind. */
    public double[] projectDirection(double dx, double dy, double dz) {
        double cx = dx * right[0] + dy * right[1] + dz * right[2];
        double cy = dx * up[0] + dy * up[1] + dz * up[2];
        double cz = dx * forward[0] + dy * forward[1] + dz * forward[2];
        if (cz < 0.01) {
            return null;
        }
        return new double[] {width / 2.0 + cx / cz * focal, height / 2.0 - cy / cz * focal, cz};
    }
}
