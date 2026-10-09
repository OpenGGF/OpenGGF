package eggsky.surface;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSprite;
import com.openggf.mods.scene.SceneSpriteSet;
import eggsky.core.Rng;
import eggsky.core.Sound;
import eggsky.world.Terrain;

/**
 * The galaxy's sentinels: Sonic's friends, who come after Eggman when he plunders a planet.
 * Flicky drones patrol and peck, Tails bombs from the air, Sonic homing-attacks, Knuckles glides
 * in punching, and at the top wanted level Super Sonic arrives. Beaming a hero knocks rings out
 * of them; empty one and it retreats.
 */
public final class Hero {
    public static final int FLICKY = 0;
    public static final int TAILS = 1;
    public static final int SONIC = 2;
    public static final int KNUCKLES = 3;
    public static final int SUPER = 4;

    // S3K player animation ids (Sonic3kAnimationIds).
    private static final int A_WALK = 0x00;
    private static final int A_RUN = 0x01;
    private static final int A_ROLL = 0x02;
    private static final int A_WAIT = 0x05;
    private static final int A_SPRING = 0x10;
    private static final int A_HURT = 0x1A;
    private static final int A_FLY = 0x20;

    private static final int APPROACH = 0;
    private static final int WINDUP = 1;
    private static final int DASH = 2;
    private static final int RECOVER = 3;
    private static final int RETREAT = 4;
    private static final int ENTER = 5;

    public final int kind;
    public float x;
    public float y;
    public float vx;
    public float vy;
    public int facing = 1;
    public float hp;
    public float maxHp;
    public int state = ENTER;
    public int stateTicks;
    public int hit;
    public boolean alive = true;
    public boolean grounded;
    private final Rng rng;
    private float orbit;
    private int anim;
    private int bombCooldown;
    private int hurtTicks;

    public Hero(int kind, float x, float y, long seed) {
        this.kind = kind;
        this.x = x;
        this.y = y;
        this.rng = new Rng(seed);
        this.maxHp = switch (kind) {
            case FLICKY -> 5;
            case TAILS -> 18;
            case SONIC -> 28;
            case KNUCKLES -> 34;
            default -> 80;
        };
        this.hp = maxHp;
        this.orbit = rng.range(0f, 6.28f);
        this.stateTicks = 40;
    }

    public String name() {
        return switch (kind) {
            case FLICKY -> "Flicky";
            case TAILS -> "Tails";
            case SONIC -> "Sonic";
            case KNUCKLES -> "Knuckles";
            default -> "Super Sonic";
        };
    }

    public float halfWidth() {
        return kind == FLICKY ? 7 : 12;
    }

    public float height() {
        return kind == FLICKY ? 14 : 30;
    }

    public float centreY() {
        return y - height() / 2;
    }

    public int damage() {
        return switch (kind) {
            case FLICKY -> 3;
            case TAILS -> 6;
            case SONIC -> 10;
            case KNUCKLES -> 12;
            default -> 17;
        };
    }

    /** Dashing heroes are only hurt by a beam outside their dash (spinning deflects it). */
    public boolean deflecting() {
        return state == DASH && kind >= SONIC;
    }

    public void update(SurfaceMode s, Terrain t, float shipX, float shipY) {
        if (!alive) {
            return;
        }
        anim++;
        if (hit > 0) {
            hit--;
        }
        if (hurtTicks > 0) {
            hurtTicks--;
        }
        if (bombCooldown > 0) {
            bombCooldown--;
        }
        stateTicks--;
        float dx = t.dx(x, shipX);
        float dy = shipY - centreY();
        float dist = (float) Math.hypot(dx, dy);
        if (state == RETREAT) {
            vy -= 0.25f;
            x += vx;
            y += vy;
            if (stateTicks <= 0 || y < -400) {
                alive = false;
            }
            return;
        }
        if (hp <= 0) {
            state = RETREAT;
            stateTicks = 120;
            vy = -4;
            vx = -Math.signum(dx) * 2;
            s.heroRepelled(this);
            return;
        }
        float speedBoost = kind == SUPER ? 1.6f : 1f;
        switch (kind) {
            case FLICKY -> {
                orbit += 0.04f;
                if (state == DASH) {
                    if (stateTicks <= 0) {
                        state = APPROACH;
                        stateTicks = rng.range(100, 180);
                    }
                } else if (stateTicks <= 0 && dist < 160) {
                    state = DASH;
                    stateTicks = 30;
                    float len = Math.max(1, dist);
                    vx = dx / len * 4.5f;
                    vy = dy / len * 4.5f;
                    s.game.sound.sfx(Sound.FLYING, 20);
                } else {
                    state = APPROACH;
                    float tx = shipX + (float) Math.cos(orbit) * 70;
                    float ty = shipY - 30 + (float) Math.sin(orbit * 1.3f) * 30;
                    vx += (t.dx(x, tx) * 0.02f - vx) * 0.1f;
                    vy += ((ty - y) * 0.02f - vy) * 0.1f;
                }
                x += vx;
                y += vy;
                facing = vx >= 0 ? 1 : -1;
            }
            case TAILS -> {
                float tx = shipX - Math.signum(dx == 0 ? 1 : dx) * 90;
                float ty = shipY - 70 + (float) Math.sin(anim * 0.04) * 10;
                vx += (t.dx(x, tx) * 0.03f - vx) * 0.08f;
                vy += ((ty - y) * 0.03f - vy) * 0.08f;
                x += vx;
                y += vy;
                facing = dx > 0 ? 1 : -1;
                if (bombCooldown == 0 && dist < 220 && state != ENTER) {
                    bombCooldown = rng.range(70, 120);
                    s.spawnRingBomb(x, centreY(), dx * 0.02f, -1.5f);
                }
                if (state == ENTER && stateTicks <= 0) {
                    state = APPROACH;
                }
            }
            default -> groundHero(s, t, shipX, shipY, dx, dy, dist, speedBoost);
        }
        x = t.wrapX(x);
        // Touching Eggman.
        if (Math.abs(t.dx(x, shipX)) < halfWidth() + s.ship.halfWidth && Math.abs(centreY() - shipY) < 26) {
            boolean dangerous = kind == FLICKY ? state == DASH : (state == DASH || kind == SUPER || kind == KNUCKLES);
            if (dangerous) {
                if (s.hurtShip(damage(), x)) {
                    // Bounce off, as on a boss.
                    vx = -Math.signum(t.dx(x, shipX)) * 3;
                    vy = -4.5f;
                    state = RECOVER;
                    stateTicks = 60;
                }
            }
        }
    }

    private void groundHero(SurfaceMode s, Terrain t, float shipX, float shipY, float dx, float dy, float dist,
            float boost) {
        float gravity = 0.21875f;
        switch (state) {
            case ENTER, APPROACH, RECOVER -> {
                if (state == ENTER && stateTicks <= 0) {
                    state = APPROACH;
                }
                if (state == RECOVER && stateTicks <= 0) {
                    state = APPROACH;
                }
                facing = dx > 0 ? 1 : -1;
                float want = facing * (kind == KNUCKLES ? 3.4f : 4.2f) * boost;
                if (Math.abs(dx) < 50 && state == APPROACH) {
                    want = 0;
                }
                vx += (want - vx) * 0.06f;
                if (kind == KNUCKLES && !grounded && vy > 0.5f) {
                    // Glide: fall slowly.
                    vy = Math.min(vy, 0.6f);
                }
                // Jump toward a hovering Eggman, or over walls.
                if (grounded && state == APPROACH && (dy < -40 && Math.abs(dx) < 140
                        || t.solid((int) (x + facing * 14), (int) (y - 14)))) {
                    vy = -6.8f * (kind == SUPER ? 1.15f : 1f);
                    grounded = false;
                    s.game.sound.sfx(Sound.JUMP, 10);
                }
                if (state == APPROACH && stateTicks <= 0 && dist < 170) {
                    state = WINDUP;
                    stateTicks = kind == SUPER ? 14 : 24;
                    s.game.sound.sfx(Sound.SPINDASH, 10);
                }
            }
            case WINDUP -> {
                vx *= 0.8f;
                facing = dx > 0 ? 1 : -1;
                if (stateTicks <= 0) {
                    state = DASH;
                    stateTicks = 30;
                    float len = Math.max(1, dist);
                    float sp = (kind == KNUCKLES ? 6f : 7.5f) * boost;
                    vx = dx / len * sp;
                    vy = dy / len * sp;
                    s.game.sound.sfx(kind == KNUCKLES ? Sound.DASH : Sound.SPRING, 6);
                }
            }
            case DASH -> {
                if (stateTicks <= 0) {
                    state = APPROACH;
                    stateTicks = rng.range(70, 130) / (kind == SUPER ? 2 : 1);
                }
            }
            default -> {
            }
        }
        if (state != DASH && state != WINDUP) {
            vy += gravity;
        }
        float nx = x + vx;
        if (t.solid((int) (nx + Math.signum(vx) * halfWidth()), (int) (y - 16))) {
            vx = state == DASH ? -vx * 0.4f : 0;
        } else {
            x = nx;
        }
        float ny = y + vy;
        int floor = t.groundBelow((int) x, (int) y - 8, (int) Math.max(12, vy + 12));
        if (vy >= 0 && floor >= 0 && floor <= ny + 1) {
            y = floor;
            vy = 0;
            grounded = true;
        } else if (vy < 0 && t.solid((int) x, (int) (ny - height()))) {
            vy = 0;
        } else {
            y = ny;
            grounded = false;
        }
        if (y > t.height()) {
            y = t.height();
            vy = 0;
            grounded = true;
        }
    }

    /** Hit by Eggman's laser or blaster. Returns rings knocked loose. */
    public int damage(float amount, float fromX, Terrain t) {
        if (state == RETREAT || state == ENTER && stateTicks > 20) {
            return 0;
        }
        if (deflecting()) {
            hit = 4;
            return 0;
        }
        hp -= amount;
        hit = 8;
        if (kind != FLICKY && hurtTicks == 0) {
            hurtTicks = 30;
            vx = Math.signum(t.dx(fromX, x)) * 2.5f;
            vy = -3f;
            if (state == WINDUP) {
                state = RECOVER;
                stateTicks = 40;
            }
            return kind == SUPER ? 5 : 3;
        }
        return 0;
    }

    public void draw(SurfaceMode s, SceneCanvas c, float sx, float sy, long ticks, int tint) {
        boolean right = facing > 0;
        SceneDraw style = SceneDraw.plain().withFlipX(!right).withTint(tint);
        if (kind == SUPER) {
            style = style.withTint(0xFFFFF070);
        }
        if (hit > 0 && (ticks / 2) % 2 == 0) {
            style = style.withFlash(0xFFFFFFFF);
        }
        if (kind == FLICKY) {
            SceneSprite f = s.game.art.frame("flicky", (int) ((ticks / 4) % 2));
            if (f != null) {
                c.draw(f, sx, sy - 8, SceneDraw.plain().withFlipX(right).withTint(tint).withFlash(hit > 0 ? 0xFFFFFFFF : 0));
            }
            if (state == DASH && (ticks / 3) % 2 == 0) {
                c.fill((int) sx - 1, (int) sy - 20, 3, 3, 0xFFFF4040);
            }
            return;
        }
        String set = kind == TAILS ? "tails" : kind == KNUCKLES ? "knuckles" : "sonic";
        int animId;
        if (state == RETREAT || hurtTicks > 0) {
            animId = A_HURT;
        } else if (kind == TAILS) {
            animId = A_FLY;
        } else if (state == DASH || state == WINDUP) {
            animId = A_ROLL;
        } else if (!grounded) {
            animId = kind == KNUCKLES && vy > 0 ? A_FLY : A_ROLL;
        } else if (Math.abs(vx) > 3) {
            animId = A_RUN;
        } else if (Math.abs(vx) > 0.3f) {
            animId = A_WALK;
        } else {
            animId = A_WAIT;
        }
        SceneSpriteSet chars = s.game.art.set(set);
        SceneSprite pose = pose(chars, animId, ticks);
        if (kind == TAILS) {
            SceneSpriteSet tails = s.game.art.set("tails_tails");
            if (tails != null && tails.frameCount() > 0x26) {
                SceneSprite tail = tails.frame(0x22 + (int) ((ticks / 3) % 5));
                c.draw(tail, sx, sy - 16, style);
            }
        }
        if (pose != null) {
            c.draw(pose, sx, sy - 16, style);
        } else {
            c.fill((int) sx - 8, (int) sy - 30, 16, 30, kind == KNUCKLES ? 0xFFE03030 : 0xFF3050FF);
        }
        if (kind == SUPER && ticks % 3 == 0) {
            s.particles.add(x + rng.range(-12f, 12f), y - rng.range(0f, 30f), 0, -0.5f, 0, 2, 0xFFFFFFA0, 20, true);
        }
        if (state == WINDUP && (ticks / 3) % 2 == 0) {
            s.game.font.centre(c, "!", (int) sx, (int) sy - 48, 0xFFFFE040);
        }
    }

    private static SceneSprite pose(SceneSpriteSet set, int anim, long ticks) {
        if (set == null) {
            return null;
        }
        int[] frames = set.animationFrames(anim);
        if (frames.length == 0) {
            return set.frameCount() > 0 ? set.frame(0) : null;
        }
        int delay = set.animationDelay(anim);
        if (delay > 30 || delay <= 0) {
            delay = 3;
        }
        int frame = frames[(int) ((ticks / delay) % frames.length)];
        if (frame >= 0xF0) {
            frame = frames[0];
        }
        return frame < set.frameCount() ? set.frame(frame) : null;
    }
}
