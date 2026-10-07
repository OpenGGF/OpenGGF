package eggsky.space;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;
import eggsky.core.Colour;
import eggsky.core.Rng;
import java.util.List;

/**
 * Software-rendered space at half resolution into one streaming image: a procedural sky of
 * stars, nebulae and the galaxy's band, the system's sun, and every planet and the station ray
 * traced as textured, lit, spinning spheres with atmospheres and rings. A depth buffer lets the
 * full-resolution sprites (asteroids, ships) hide behind planets.
 */
public final class SpaceRenderer {
    public static final int W = 200;
    public static final int H = 112;
    private static final int SKY_W = 512;
    private static final int SKY_H = 256;

    private final int[] buffer = new int[W * H];
    /** Distance along each pixel's ray to the nearest opaque sphere (infinite for sky). */
    public final float[] depth = new float[W * H];
    private final SceneImage image = SceneImage.streaming(W, H);
    private final int[] sky = new int[SKY_W * SKY_H];
    private final double[] dirX = new double[W * H];
    private final double[] dirY = new double[W * H];
    private final double[] dirZ = new double[W * H];
    private final float[] ringBands = new float[64];
    private static final int STARS = 700;
    private final double[] starX = new double[STARS];
    private final double[] starY = new double[STARS];
    private final double[] starZ = new double[STARS];
    private final int[] starColourOf = new int[STARS];
    private final boolean[] starBig = new boolean[STARS];
    public int sunColour = 0xFFFFE8A0;
    public double sunSize = 3000;

    public SpaceRenderer(long seed, int starColour) {
        buildSky(seed, starColour);
        Rng rng = new Rng(seed ^ 0x52494E47L);
        for (int i = 0; i < ringBands.length; i++) {
            ringBands[i] = rng.chance(0.2) ? 0.15f : rng.range(0.4f, 1f);
        }
        sunColour = Colour.lerp(starColour, 0xFFFFFFFF, 0.35f);
    }

    private void buildSky(long seed, int starColour) {
        Rng rng = new Rng(seed);
        float hue = rng.range(0f, 360f);
        int nebulaA = Colour.fromHsv(hue, 0.75f, 0.75f, 255);
        int nebulaB = Colour.fromHsv(hue + rng.range(60f, 180f), 0.7f, 0.6f, 255);
        long n1 = rng.nextLong();
        long n2 = rng.nextLong();
        double bandTilt = rng.range(-0.6f, 0.6f);
        double bandYaw = rng.range(0f, 6.28f);
        for (int y = 0; y < SKY_H; y++) {
            double lat = (0.5 - (y + 0.5) / SKY_H) * Math.PI;
            for (int x = 0; x < SKY_W; x++) {
                double lon = ((x + 0.5) / SKY_W - 0.5) * Math.PI * 2;
                double dx = Math.cos(lat) * Math.sin(lon);
                double dy = Math.sin(lat);
                double dz = Math.cos(lat) * Math.cos(lon);
                // The galaxy's band: closeness to a tilted great circle.
                double ny = Math.cos(bandTilt);
                double nx = Math.sin(bandTilt) * Math.cos(bandYaw);
                double nz = Math.sin(bandTilt) * Math.sin(bandYaw);
                double band = 1 - Math.min(1, Math.abs(dx * nx + dy * ny + dz * nz) * 3.2);
                float u = x / (float) SKY_W;
                float v = y / (float) SKY_H;
                float cloud = 0;
                float amp = 1;
                int f = 4;
                for (int o = 0; o < 5; o++) {
                    cloud += Rng.noise2Wrapped(n1 + o, u * f, v * f * 0.5f, f) * amp;
                    amp *= 0.5f;
                    f *= 2;
                }
                float cloud2 = Rng.noise2Wrapped(n2, u * 6, v * 3, 6);
                int c = Colour.lerp(0xFF03040E, 0xFF0A0820, (float) band);
                float neb = Math.max(0, cloud * 1.3f + (float) band * 0.35f - 0.05f);
                int tint = Colour.lerp(nebulaA, nebulaB, Math.max(0, Math.min(1, cloud2 * 0.8f + 0.5f)));
                c = Colour.lerp(c, tint, Math.min(0.75f, neb * neb * 1.4f));
                // Bright knots inside the densest clouds.
                if (neb > 0.75f) {
                    c = Colour.lerp(c, Colour.lerp(tint, 0xFFFFFFFF, 0.5f), Math.min(0.4f, (neb - 0.75f) * 1.5f));
                }
                c = Colour.lerp(c, 0xFF8088B0, (float) (band * band * band) * 0.35f);
                sky[y * SKY_W + x] = c;
            }
        }
        // Stars are drawn separately at full resolution (see drawStars).
        for (int i = 0; i < STARS; i++) {
            double z = rng.range(-1f, 1f);
            double a = rng.range(0f, 6.2832f);
            double r = Math.sqrt(1 - z * z);
            starX[i] = r * Math.cos(a);
            starY[i] = z;
            starZ[i] = r * Math.sin(a);
            float b = rng.nextFloat();
            int col = rng.chance(0.15) ? Colour.lerp(0xFFFFFFFF, starColour, 0.6f)
                    : rng.chance(0.1) ? 0xFF90A8FF : 0xFFFFFFFF;
            starColourOf[i] = Colour.alpha(col, 90 + Math.round(b * b * 165));
            starBig[i] = b > 0.96f;
        }
    }

    /** Renders the scene for {@code cam} (whose focal length is for the full-size screen). */
    public void render(Cam cam, List<Body> bodies, long ticks) {
        double focal = cam.focal * W / cam.width;
        for (int y = 0; y < H; y++) {
            double v = -(y + 0.5 - H / 2.0) / focal;
            for (int x = 0; x < W; x++) {
                double u = (x + 0.5 - W / 2.0) / focal;
                double dx = cam.forward[0] + cam.right[0] * u + cam.up[0] * v;
                double dy = cam.forward[1] + cam.right[1] * u + cam.up[1] * v;
                double dz = cam.forward[2] + cam.right[2] * u + cam.up[2] * v;
                double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                int i = y * W + x;
                dirX[i] = dx / len;
                dirY[i] = dy / len;
                dirZ[i] = dz / len;
            }
        }
        // The sun sits at the origin.
        double sunDist = Math.max(1, Math.sqrt(cam.x * cam.x + cam.y * cam.y + cam.z * cam.z));
        double sx = -cam.x / sunDist;
        double sy = -cam.y / sunDist;
        double sz = -cam.z / sunDist;
        double sunAngle = Math.min(0.2, sunSize / sunDist);
        double sunCos = Math.cos(sunAngle);
        for (int i = 0; i < W * H; i++) {
            double dx = dirX[i];
            double dy = dirY[i];
            double dz = dirZ[i];
            double lon = Math.atan2(dx, dz);
            double lat = Math.asin(Math.max(-1, Math.min(1, dy)));
            int u = (int) ((lon / (Math.PI * 2) + 0.5) * SKY_W) & (SKY_W - 1);
            int v = Math.min(SKY_H - 1, Math.max(0, (int) ((0.5 - lat / Math.PI) * SKY_H)));
            int c = sky[v * SKY_W + u];
            double s = dx * sx + dy * sy + dz * sz;
            if (s > sunCos) {
                c = sunColour | 0xFF000000;
                if (s > 1 - (1 - sunCos) * 0.4) {
                    c = 0xFFFFFFFF;
                }
            } else if (s > 0.6) {
                double glow = Math.pow((s - 0.6) / (sunCos - 0.6), 6) * 0.9;
                c = Colour.lerp(c, sunColour, (float) Math.min(0.9, glow));
            }
            buffer[i] = c;
            depth[i] = Float.MAX_VALUE;
        }
        for (Body b : bodies) {
            drawBody(cam, b, sx, sy, sz, ticks);
        }
        image.update(buffer);
    }

    private void drawBody(Cam cam, Body b, double sunX, double sunY, double sunZ, long ticks) {
        PlanetTexture tex = b.texture;
        double ocx = cam.x - b.x;
        double ocy = cam.y - b.y;
        double ocz = cam.z - b.z;
        double r = b.radius;
        double cc = ocx * ocx + ocy * ocy + ocz * ocz;
        double dist = Math.sqrt(cc);
        // Light from the sun at the origin.
        double lx = -b.x;
        double ly = -b.y;
        double lz = -b.z;
        double ll = Math.max(1, Math.sqrt(lx * lx + ly * ly + lz * lz));
        lx /= ll;
        ly /= ll;
        lz /= ll;
        if (b.station) {
            lx = 0.5;
            ly = 0.6;
            lz = -0.6;
        }
        double atmo = b.station ? 1.0 : 1.12;
        // Screen bounds of the sphere (plus atmosphere and rings).
        double[] p = cam.project(b.x, b.y, b.z);
        double extent = r * (b.ringed ? 2.4 : atmo);
        int x0 = 0;
        int x1 = W - 1;
        int y0 = 0;
        int y1 = H - 1;
        if (p != null && dist > extent * 1.05) {
            double scale = W / (double) cam.width;
            double sr = extent / p[2] * cam.focal * scale * 1.15 + 2;
            double px = p[0] * scale;
            double py = p[1] * scale;
            x0 = (int) Math.max(0, px - sr);
            x1 = (int) Math.min(W - 1, px + sr);
            y0 = (int) Math.max(0, py - sr);
            y1 = (int) Math.min(H - 1, py + sr);
            if (x0 > x1 || y0 > y1) {
                return;
            }
        } else if (p == null && dist > extent) {
            return;
        }
        double cosT = Math.cos(b.tilt);
        double sinT = Math.sin(b.tilt);
        double spin = b.spin;
        double cosS = Math.cos(spin);
        double sinS = Math.sin(spin);
        double cloudSpin = spin * 1.3 + ticks * 0.0004;
        double ax = -sinT;
        double ay = cosT;
        double az = 0;
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                int i = y * W + x;
                double dx = dirX[i];
                double dy = dirY[i];
                double dz = dirZ[i];
                double bq = ocx * dx + ocy * dy + ocz * dz;
                double disc = bq * bq - (cc - r * r);
                double tHit = Double.MAX_VALUE;
                int colour = 0;
                boolean hit = false;
                if (disc > 0) {
                    double t = -bq - Math.sqrt(disc);
                    if (t > 0 && t < depth[i]) {
                        tHit = t;
                        hit = true;
                        double hx = (ocx + dx * t) / r;
                        double hy = (ocy + dy * t) / r;
                        double hz = (ocz + dz * t) / r;
                        double light = hx * lx + hy * ly + hz * lz;
                        // Undo the tilt (about Z) then the spin (about Y).
                        double tx = hx * cosT + hy * sinT;
                        double ty = -hx * sinT + hy * cosT;
                        double qx = tx * cosS - hz * sinS;
                        double qz = tx * sinS + hz * cosS;
                        double lon = Math.atan2(qx, qz);
                        double lat = Math.asin(Math.max(-1, Math.min(1, ty)));
                        int u = (int) ((lon / (Math.PI * 2) + 0.5) * PlanetTexture.W) & (PlanetTexture.W - 1);
                        int v = Math.min(PlanetTexture.H - 1, (int) ((0.5 - lat / Math.PI) * PlanetTexture.H));
                        int base = tex == null ? 0xFF606878 : tex.surface[v * PlanetTexture.W + u];
                        if (tex != null && !b.station) {
                            double cqx = tx * Math.cos(cloudSpin) - hz * Math.sin(cloudSpin);
                            double cqz = tx * Math.sin(cloudSpin) + hz * Math.cos(cloudSpin);
                            int cu = (int) ((Math.atan2(cqx, cqz) / (Math.PI * 2) + 0.5) * PlanetTexture.W)
                                    & (PlanetTexture.W - 1);
                            int cloud = tex.clouds[v * PlanetTexture.W + cu] & 0xFF;
                            if (cloud > 0) {
                                base = Colour.lerp(base, tex.cloudColour, cloud / 255f * 0.9f);
                            }
                        }
                        double lit = b.station ? 0.35 + 0.65 * Math.max(0, light) : 0.13 + 0.92 * Math.max(0, light);
                        // A warm band along the terminator.
                        colour = Colour.scale(base, (float) lit);
                        if (!b.station && tex != null) {
                            double rim = 1 + (hx * dx + hy * dy + hz * dz);
                            if (rim > 0) {
                                double f = Math.pow(1 - Math.min(1, -(hx * dx + hy * dy + hz * dz)), 2.5)
                                        * Math.max(0.15, light + 0.3);
                                colour = Colour.lerp(colour, tex.atmosphere, (float) Math.min(0.8, f));
                            }
                        }
                        colour |= 0xFF000000;
                    }
                }
                if (!hit && !b.station && bq < 0) {
                    // Atmosphere glow around the limb.
                    double closest = Math.sqrt(Math.max(0, cc - bq * bq));
                    if (closest < r * atmo && depth[i] == Float.MAX_VALUE) {
                        double f = 1 - (closest - r) / (r * (atmo - 1));
                        f = Math.max(0, Math.min(1, f));
                        int atm = tex == null ? 0xFF80A0FF : tex.atmosphere;
                        buffer[i] = Colour.lerp(buffer[i], atm, (float) (f * f * 0.75));
                    }
                }
                if (hit) {
                    buffer[i] = colour;
                    depth[i] = (float) tHit;
                }
                if (b.ringed) {
                    double denom = dx * ax + dy * ay + dz * az;
                    if (Math.abs(denom) > 1e-4) {
                        double t = -(ocx * ax + ocy * ay + ocz * az) / denom;
                        if (t > 0 && t < tHit && t < depth[i] + (hit ? 0 : 0)) {
                            double px2 = ocx + dx * t;
                            double py2 = ocy + dy * t;
                            double pz2 = ocz + dz * t;
                            double rr = Math.sqrt(px2 * px2 + py2 * py2 + pz2 * pz2) / r;
                            if (rr > 1.35 && rr < 2.3) {
                                int band = (int) ((rr - 1.35) / 0.95 * ringBands.length);
                                float a = ringBands[Math.min(ringBands.length - 1, band)] * 0.5f;
                                double shade = 0.45 + 0.55 * Math.abs(ly);
                                int rc = Colour.scale(b.ringColour, (float) shade);
                                buffer[i] = Colour.lerp(buffer[i], rc, a) | 0xFF000000;
                            }
                        }
                    }
                }
            }
        }
    }

    public void draw(SceneCanvas c, int width, int height) {
        c.draw(image, 0, 0, SceneDraw.plain().withScale(width / (float) W, height / (float) H));
    }

    /** The stars at full resolution, hidden where a sphere covers the sky. */
    public void drawStars(SceneCanvas c, Cam cam, long ticks) {
        for (int i = 0; i < STARS; i++) {
            double[] p = cam.projectDirection(starX[i], starY[i], starZ[i]);
            if (p == null || p[0] < 0 || p[1] < 0 || p[0] >= cam.width || p[1] >= cam.height) {
                continue;
            }
            int px = (int) (p[0] * W / cam.width);
            int py = (int) (p[1] * H / cam.height);
            if (depth[py * W + px] != Float.MAX_VALUE) {
                continue;
            }
            int col = starColourOf[i];
            if (starBig[i] && ((ticks / 9 + i) % 23) == 0) {
                col = 0xFFFFFFFF;
            }
            c.fill((int) p[0], (int) p[1], 1, 1, col);
            if (starBig[i]) {
                int dim = Colour.fade(col, 0.45f);
                c.fill((int) p[0] - 1, (int) p[1], 1, 1, dim);
                c.fill((int) p[0] + 1, (int) p[1], 1, 1, dim);
                c.fill((int) p[0], (int) p[1] - 1, 1, 1, dim);
                c.fill((int) p[0], (int) p[1] + 1, 1, 1, dim);
            }
        }
    }

    /** Whether something at screen (x, y) and distance {@code dist} is hidden behind a sphere. */
    public boolean occluded(double x, double y, double dist, int width, int height) {
        int px = (int) (x * W / width);
        int py = (int) (y * H / height);
        if (px < 0 || py < 0 || px >= W || py >= H) {
            return false;
        }
        return depth[py * W + px] < dist;
    }
}
