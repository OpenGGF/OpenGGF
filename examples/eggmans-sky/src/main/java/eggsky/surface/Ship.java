package eggsky.surface;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSprite;
import eggsky.Game;
import eggsky.art.Art;
import eggsky.core.Colour;
import eggsky.core.Controls;
import eggsky.game.Catalog;
import eggsky.game.Player;
import eggsky.world.Terrain;

/**
 * Dr. Eggman in the Egg Mobile, as a hovercraft over a planet's surface. It floats on a cushion
 * a little above whatever floor is below, climbs on jet energy (which recharges on the
 * cushion), boosts, and collides with the terrain's solid masks. Position is the centre of the
 * pod; the head and exhaust flame are drawn from the ROM's Robotnik ship mappings.
 */
public final class Ship {
    /** Height of the hover cushion above the floor (pod bottom to floor). */
    private static final float CUSHION = 10;
    private static final float CUSHION_RANGE = 48;

    public float x;
    public float y;
    public float vx;
    public float vy;
    public int facing = 1;
    public float halfWidth = 22;
    public float top = -30;
    public float bottom = 16;
    public int hurtTicks;
    public int invulnerable;
    public boolean boosting;
    public boolean onCushion;
    public float groundDistance = 999;
    public int laughTicks;
    public float bank;
    /** Frames the launch input has been held. */
    public int launchHold;
    public float tilt;

    public void measure(Art art) {
        SceneSprite body = art.frame("ship", Art.SHIP_BODY);
        if (body != null) {
            halfWidth = Math.min(body.originX(), body.width() - body.originX()) - 3;
            top = -body.originY() - 2;
            bottom = body.height() - body.originY() - 2;
        }
    }

    /** One physics step. {@code allowFlight} is false while dead or in a cutscene. */
    public void update(Game g, Terrain t, Controls in, float gravity, boolean allowFlight, float speedBoost) {
        Player p = g.player;
        float accel = 0.16f * (1 + 0.15f * p.level(Catalog.T_THRUSTERS)) * speedBoost;
        float maxSpeed = 2.6f * (1 + 0.15f * p.level(Catalog.T_THRUSTERS)) * speedBoost;
        boosting = allowFlight && in.boost && p.jet > 1 && !in.up;
        if (boosting) {
            accel *= 2.2f;
            maxSpeed *= 1.9f;
            p.jet = Math.max(0, p.jet - 0.55f);
        }
        int dx = allowFlight ? in.dx() : 0;
        if (dx != 0) {
            vx += dx * accel;
            facing = dx;
        } else {
            vx *= 0.93f;
        }
        if (Math.abs(vx) > maxSpeed) {
            vx = Math.signum(vx) * Math.max(maxSpeed, Math.abs(vx) * 0.96f);
        }
        // Distance from the pod's bottom to the floor below.
        groundDistance = probeGround(t);
        onCushion = groundDistance < CUSHION_RANGE;
        vy += gravity;
        if (onCushion) {
            // Spring toward the cushion height, damped.
            float err = groundDistance - CUSHION;
            vy -= gravity;
            vy += err * 0.012f;
            vy *= 0.9f;
            p.jet = Math.min(p.maxJet(), p.jet + 0.9f);
        }
        if (allowFlight && in.up) {
            if (p.jet > 0.5f || onCushion) {
                vy -= 0.34f * speedBoost;
                if (!onCushion || groundDistance > CUSHION + 6) {
                    p.jet = Math.max(0, p.jet - 0.32f);
                }
            }
        }
        if (allowFlight && in.down) {
            vy += 0.22f;
        }
        if (climbAssist > 0) {
            climbAssist--;
            if (dx != 0) {
                vy = Math.min(vy, -1.6f);
            }
        }
        vy = Math.max(-3.4f * speedBoost, Math.min(5.5f, vy));
        moveX(t, vx);
        moveY(t, vy);
        bank += ((dx == 0 ? 0 : dx * 2) - bank) * 0.15f;
        tilt += (Math.max(-1, Math.min(1, vy * 0.25f)) - tilt) * 0.2f;
        if (hurtTicks > 0) {
            hurtTicks--;
        }
        if (invulnerable > 0) {
            invulnerable--;
        }
        if (laughTicks > 0) {
            laughTicks--;
        }
        x = t.wrapX(x);
        // The sky is open, but not endless: thin air above the act's top row.
        float ceiling = -260;
        if (y < ceiling) {
            y = ceiling;
            vy = Math.max(0, vy);
        }
    }

    private float probeGround(Terrain t) {
        float feet = y + bottom;
        int[] xs = {(int) (x - halfWidth * 0.6f), (int) x, (int) (x + halfWidth * 0.6f)};
        float best = 999;
        for (int sx : xs) {
            int gy = t.groundBelow(sx, (int) feet, (int) CUSHION_RANGE + 8);
            if (gy >= 0) {
                best = Math.min(best, gy - feet);
            }
        }
        return best;
    }

    private boolean blockedAt(Terrain t, float px, float py, boolean descending, float prevBottom) {
        byte s = t.solidity((int) px, (int) py);
        if (s == com.openggf.mods.scene.SceneLevelKit.SOLID) {
            return true;
        }
        return s == com.openggf.mods.scene.SceneLevelKit.TOP_SOLID && descending && prevBottom <= Math.floor(py);
    }

    private void moveX(Terrain t, float amount) {
        if (amount == 0) {
            return;
        }
        float nx = x + amount;
        float edge = nx + Math.signum(amount) * halfWidth;
        boolean hit = false;
        for (float sy = y + top + 6; sy <= y + bottom - 4; sy += 6) {
            if (t.solid((int) edge, (int) sy)) {
                hit = true;
                break;
            }
        }
        if (hit) {
            // Step up small slopes instead of stopping dead.
            for (int up = 1; up <= 6; up++) {
                boolean clear = true;
                for (float sy = y + top + 6 - up; sy <= y + bottom - 4 - up; sy += 6) {
                    if (t.solid((int) edge, (int) sy)) {
                        clear = false;
                        break;
                    }
                }
                if (clear) {
                    x = nx;
                    y -= up;
                    return;
                }
            }
            vx = -vx * 0.2f;
            // Hover-climb: pushing into a wall lifts the pod over it.
            climbAssist = 6;
        } else {
            x = nx;
        }
    }

    /** Frames left of the automatic lift after bumping a wall. */
    public int climbAssist;

    private void moveY(Terrain t, float amount) {
        if (amount == 0) {
            return;
        }
        float prevBottom = y + bottom;
        float ny = y + amount;
        float edge = amount > 0 ? ny + bottom : ny + top;
        boolean hit = false;
        for (float sx = x - halfWidth + 3; sx <= x + halfWidth - 3; sx += 5) {
            if (amount > 0 ? blockedAt(t, sx, edge, true, prevBottom) : t.solid((int) sx, (int) edge)) {
                hit = true;
                break;
            }
        }
        if (hit) {
            // Settle against the surface pixel by pixel.
            float step = Math.signum(amount);
            int guard = 0;
            while (guard++ < 12) {
                float e2 = step > 0 ? y + step + bottom : y + step + top;
                boolean block = false;
                for (float sx = x - halfWidth + 3; sx <= x + halfWidth - 3; sx += 5) {
                    if (step > 0 ? blockedAt(t, sx, e2, true, y + bottom) : t.solid((int) sx, (int) e2)) {
                        block = true;
                        break;
                    }
                }
                if (block) {
                    break;
                }
                y += step;
            }
            vy = amount > 0 ? -Math.abs(vy) * 0.15f : 0;
        } else {
            y = ny;
        }
    }

    /** Whether any part of the pod is inside solid terrain (stuck after carving or a respawn). */
    public boolean embedded(Terrain t) {
        return t.solid((int) x, (int) y) && t.solid((int) x, (int) (y + top + 8));
    }

    /** The muzzle of the mining laser, at the front of the pod. */
    public float gunX() {
        return x + facing * (halfWidth + 2);
    }

    public float gunY() {
        return y + 2;
    }

    /** Draws the pod at screen ({@code sx}, {@code sy}). */
    public void draw(Game g, SceneCanvas c, float sx, float sy, boolean thrusting, int headOverride) {
        Art art = g.art;
        boolean right = facing > 0;
        boolean blink = invulnerable > 0 && (g.ticks / 3) % 2 == 0 && hurtTicks <= 0;
        if (blink) {
            return;
        }
        float bob = (float) Math.sin(g.ticks * 0.08) * (onCushion ? 1.2f : 0.6f);
        float py = sy + bob;
        SceneDraw style = SceneDraw.plain().withFlipX(!right);
        if (hurtTicks > 0 && (g.ticks / 2) % 2 == 0) {
            style = style.withFlash(0xFFFFFFFF);
        }
        // Exhaust flame at the rear, flickering; longer while boosting.
        SceneSprite flame = art.frame("ship", Art.SHIP_FLAME);
        if (flame != null && (thrusting || boosting || (g.ticks / 2) % 3 != 0)) {
            float fx = sx - facing * (halfWidth + 6 + (boosting ? 6 : 0));
            float scale = boosting ? 1.6f + (g.ticks % 3) * 0.2f : 1f;
            c.draw(flame, fx, py + 2, SceneDraw.plain().withFlipX(!right).withScale(scale));
        }
        int head = headOverride >= 0 ? headOverride
                : hurtTicks > 0 ? Art.SHIP_HEAD_HURT
                : laughTicks > 0 ? Art.SHIP_HEAD_LAUGH
                : (g.ticks / 8) % 2 == 0 ? Art.SHIP_HEAD_IDLE0 : Art.SHIP_HEAD_IDLE1;
        SceneSprite headSprite = art.frame("ship", head);
        SceneSprite body = art.frame("ship", Art.SHIP_BODY);
        if (headSprite != null) {
            c.draw(headSprite, sx, py - 0x1C, style);
        }
        if (body != null) {
            c.draw(body, sx, py, style);
        } else {
            c.fill((int) (sx - 20), (int) (py - 12), 40, 24, 0xFF8090A0);
        }
        if (boosting) {
            for (int i = 0; i < 3; i++) {
                c.fill((int) (sx - facing * (halfWidth + 12 + i * 9)), (int) (py - 6 + i * 4), 8, 1,
                        Colour.alpha(0xFFFFFFFF, 160 - i * 40));
            }
        }
    }
}
