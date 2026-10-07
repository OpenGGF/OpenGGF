package eggsky.surface;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSprite;
import com.openggf.mods.scene.SceneSpriteSet;
import eggsky.art.FaunaDef;
import eggsky.core.Rng;
import eggsky.world.Species;
import eggsky.world.Terrain;

/**
 * One animal or feral badnik roaming a planet. Walkers follow the floor and turn at walls and
 * drops; flyers drift at a height; hoppers bound. Temperament decides how it treats Eggman:
 * skittish ones flee, aggressive ones fight back, predators hunt him.
 */
public final class Creature {
    public static final int WANDER = 0;
    public static final int FLEE = 1;
    public static final int ATTACK = 2;
    public static final int REST = 3;

    public final Species species;
    public final String id;
    public float x;
    public float y;
    public float vx;
    public float vy;
    public int facing = 1;
    public float hp;
    public int state = WANDER;
    public int stateTicks;
    public int hit;
    public int attackCooldown;
    public boolean alive = true;
    public boolean grounded;
    public int scannedUntil;
    public float homeX;
    public float flyHeight;
    public boolean angered;
    private final Rng rng;
    private int anim;
    /** Frames since leaving the screen; creatures far away rest. */
    public int offscreen;

    public Creature(Species species, String id, float x, float y, long seed) {
        this.species = species;
        this.id = id;
        this.x = x;
        this.y = y;
        this.homeX = x;
        this.hp = species.health;
        this.rng = new Rng(seed);
        this.flyHeight = 40 + rng.nextInt(60);
        this.facing = rng.chance(0.5) ? 1 : -1;
    }

    public boolean flies() {
        return species.body.motion() == FaunaDef.FLY;
    }

    public float halfWidth() {
        return 10 * species.scale;
    }

    public float height() {
        return 22 * species.scale;
    }

    /** Centre of the body, for aiming and contact. */
    public float centreY() {
        return y - height() / 2;
    }

    public void update(SurfaceMode s, Terrain t, float shipX, float shipY) {
        if (!alive) {
            return;
        }
        anim++;
        if (hit > 0) {
            hit--;
        }
        if (attackCooldown > 0) {
            attackCooldown--;
        }
        stateTicks--;
        float dxShip = t.dx(x, shipX);
        float dist = (float) Math.hypot(dxShip, shipY - centreY());
        int temper = species.temperament;
        // Decide behaviour.
        if (angered && temper >= Species.AGGRESSIVE || temper == Species.PREDATOR && dist < 150 && !s.shipHidden()) {
            state = ATTACK;
        } else if ((temper == Species.SKITTISH && dist < 80) || (angered && temper < Species.AGGRESSIVE)) {
            if (state != FLEE) {
                stateTicks = 120;
            }
            state = FLEE;
        } else if (stateTicks <= 0) {
            state = rng.chance(0.3) ? REST : WANDER;
            stateTicks = rng.range(60, 220);
            if (state == WANDER && rng.chance(0.4)) {
                facing = -facing;
            }
            if (Math.abs(t.dx(homeX, x)) > 260) {
                facing = t.dx(x, homeX) > 0 ? 1 : -1;
            }
            angered = false;
        }
        float speed = species.speed * (species.body.motion() == FaunaDef.ROLL ? 1.8f : 1f);
        float targetVx;
        switch (state) {
            case FLEE -> {
                facing = dxShip > 0 ? -1 : 1;
                targetVx = facing * speed * 2.2f;
            }
            case ATTACK -> {
                facing = dxShip > 0 ? 1 : -1;
                targetVx = facing * speed * 1.7f;
                if (dist < 30 && attackCooldown == 0) {
                    s.creatureBite(this);
                    attackCooldown = 70;
                }
            }
            case REST -> targetVx = 0;
            default -> targetVx = facing * speed * 0.6f;
        }
        vx += (targetVx - vx) * 0.15f;
        if (flies()) {
            int floor = t.groundBelow((int) x, (int) y - 4, 200);
            float wantY = (floor < 0 ? y : floor - flyHeight) + (float) Math.sin(anim * 0.05 + homeX) * 8;
            if (state == ATTACK) {
                wantY = shipY;
            }
            vy += (wantY - y) * 0.01f;
            vy *= 0.92f;
            float nx = x + vx;
            if (t.solid((int) (nx + facing * halfWidth()), (int) centreY())) {
                facing = -facing;
                vx = -vx;
            } else {
                x = nx;
            }
            float ny = y + vy;
            if (!t.solid((int) x, (int) (ny - height())) && !t.solid((int) x, (int) ny)) {
                y = ny;
            } else {
                vy = 0;
            }
        } else {
            // Walk or hop along the floor.
            float nx = x + vx;
            float probe = nx + Math.signum(vx) * halfWidth();
            boolean wall = t.solid((int) probe, (int) (y - height() * 0.6f));
            int ahead = t.groundBelow((int) probe, (int) y - 12, 40);
            boolean drop = grounded && (ahead < 0 || ahead > y + 24);
            if ((wall || drop) && state != ATTACK) {
                facing = -facing;
                vx = 0;
            } else if (!wall) {
                x = nx;
            }
            vy += 0.25f;
            if (grounded && species.body.motion() == FaunaDef.HOP && state != REST && rng.chance(0.03)) {
                vy = -3.2f - rng.nextFloat() * 1.5f;
                grounded = false;
            }
            float ny = y + vy;
            int floor = t.groundBelow((int) x, (int) y - 10, (int) Math.max(14, vy + 14));
            if (vy >= 0 && floor >= 0 && floor <= ny + 1) {
                y = floor;
                vy = 0;
                grounded = true;
            } else {
                y = ny;
                grounded = false;
                if (y > t.height()) {
                    y = t.height();
                    vy = 0;
                }
            }
        }
        x = t.wrapX(x);
    }

    public void draw(SurfaceMode s, SceneCanvas c, float sx, float sy, long ticks, int tint) {
        SceneSprite sprite = frame(s);
        boolean right = facing > 0;
        SceneDraw style = SceneDraw.plain().withScale(species.scale).withFlipX(right).withTint(tint);
        if (hit > 0 && (ticks / 2) % 2 == 0) {
            style = style.withFlash(0xFFFFFFFF);
        }
        if (sprite == null) {
            c.fill((int) (sx - halfWidth()), (int) (sy - height()), (int) (halfWidth() * 2), (int) height(), 0xFFFF00FF);
            return;
        }
        float bottom = (sprite.height() - sprite.originY()) * species.scale;
        c.draw(sprite, sx, sy - bottom, style);
        if (state == ATTACK && (ticks / 8) % 2 == 0) {
            s.game.font.centre(c, "!", (int) sx, (int) (sy - height() - 12), 0xFFFF4040);
        }
    }

    private SceneSprite frame(SurfaceMode s) {
        SceneSpriteSet set = s.creatureArt(species);
        if (set == null || set.frameCount() == 0) {
            return null;
        }
        int[] frames = species.body.frames();
        boolean moving = Math.abs(vx) > 0.15f || flies();
        int index = moving ? (anim / Math.max(1, species.body.frameTicks())) % frames.length : 0;
        int f = Math.min(set.frameCount() - 1, frames[index]);
        return s.creatureFrame(species, f);
    }
}
