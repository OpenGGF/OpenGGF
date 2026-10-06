package survivors;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic2.objects.badniks.Sonic2BadnikConfig;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.*;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import java.util.List;

/**
 * A ROM-art badnik with hitpoints. The spawn subtype (a byte) holds the {@link Species} id and
 * the {@link #ELITE} flag; the starting hitpoints come through the constructor (rewind restores
 * them with the other fields). A stomp from Sonic
 * deals stomp damage times the bounce combo and always rebounds him; weapons call
 * {@link #hurt}. Defeat uses the stock Sonic 2 destruction (explosion, freed animal, points)
 * and drops rings. Movement is one of the species' AI archetypes, all held to the arena.
 */
public final class Enemy extends AbstractBadnikInstance implements RewindRecreatable, TouchResponseListener {
    static final int ELITE = 0x20;
    static final int GRAVITY = 0x38;
    // Frames an enemy ignores further weapon hits after one lands.
    static final int WEAPON_IFRAMES = 8;
    static final int HIT_FLASH = 10;

    private int hp;
    private int maxHp;
    private int ticks;
    private int flash;
    private int iframes;
    private int state;
    private int timer;
    private int vx;
    private int vy;
    private int subX;
    private int subY;
    private int shotTimer;
    private boolean grounded;
    private int homeX;
    private int litTimer;

    public Enemy(ObjectSpawn spawn) {
        this(spawn, Species.of(spawn.subtype() & 0x1F).hp());
    }

    public Enemy(ObjectSpawn spawn, int hitpoints) {
        super(spawn, "Survivors " + Species.of(spawn.subtype() & 0x1F).name(), Sonic2BadnikConfig.DESTRUCTION);
        hp = maxHp = Math.max(1, hitpoints);
        homeX = spawn.x();
        shotTimer = 60 + Math.floorMod(spawn.x() * 7 + spawn.y(), 90);
        timer = Math.floorMod(spawn.x() * 13, 40);
    }

    static ObjectSpawn spawnAt(int x, int y, int species, boolean elite) {
        int subtype = (species & 0x1F) | (elite ? ELITE : 0);
        return new ObjectSpawn(x, y, 0, subtype, 0, false, y, -1, SurvivorsMod.ID, SurvivorsMod.ID + ":enemy");
    }

    Species.Traits traits() { return Species.of(spawn.subtype() & 0x1F); }
    boolean elite() { return (spawn.subtype() & ELITE) != 0; }
    int hp() { return hp; }
    int maxHp() { return maxHp; }
    boolean alive() { return !isDestroyed() && hp > 0; }
    boolean grounded() { return grounded; }
    /** Weapon hits land only outside the brief i-frames after the last one. */
    boolean vulnerable() { return alive() && iframes == 0; }

    @Override public AbstractObjectInstance recreateForRewind(RewindRecreateContext context) {
        return new Enemy(context.spawn());
    }
    @Override public boolean isPersistent() { return !isDestroyed(); }

    private RunState run() { return services().gameService(RunState.class); }
    private Arena arena() { return services().gameService(Arena.class); }

    @Override protected void updateMovement(int vIntRunCount, PlayableEntity player) {
        var run = run();
        if (run != null && run.paused) return;
        ticks++;
        if (flash > 0) flash--;
        if (iframes > 0) iframes--;
        if (litTimer > 0) litTimer--;
        var t = traits();
        int px = player == null ? currentX : player.getCentreX();
        int py = player == null ? currentY : player.getCentreY();
        switch (t.archetype()) {
            case Species.WALKER -> walk(t, px);
            case Species.HOPPER -> hop(t, px, false);
            case Species.LEAPER -> hop(t, px, true);
            case Species.FLYER -> fly(t, px, py);
            case Species.DIVER -> dive(t, px, py);
            case Species.TURRET -> turret(px);
            default -> explode(t, px, py);
        }
        if (t.id() == Species.FLASHER && litTimer == 0 && ticks % 150 == 0) litTimer = 50;
        if (t.shoots()) shoot(t, px, py);
        var arena = arena();
        if (arena != null) {
            int margin = t.halfWidth() / 2;
            if (currentX < arena.left() + margin || currentX > arena.right() - margin) {
                currentX = arena.clampX(currentX, margin);
                vx = -vx;
                facingLeft = !facingLeft;
            }
            // Anything knocked far below the floor (a bad landing) is removed rather than lost.
            if (currentY > arena.floorBottom() + 256) setDestroyed(true);
        }
    }

    /** Moves by the velocity with 8-bit subpixels. */
    private void step() {
        subX += vx;
        subY += vy;
        currentX += subX >> 8;
        currentY += subY >> 8;
        subX &= 0xFF;
        subY &= 0xFF;
    }

    /** Floor surface Y below (x, feetY), searching a little above and below; MIN_VALUE if none. */
    private int floorAt(int x, int feetY) {
        var levelManager = services().levelManager();
        for (int probe = feetY - 24; probe <= feetY + 40; probe += 16) {
            var r = ObjectTerrainUtils.checkFloorDist(levelManager, x, probe);
            if (r.foundSurface() && r.distance() >= -16 && r.distance() < 16) return probe + r.distance();
        }
        return Integer.MIN_VALUE;
    }

    private void walk(Species.Traits t, int px) {
        if (!grounded) { fall(t); return; }
        if (Math.abs(px - currentX) > 6 && ticks % 30 == 0) facingLeft = px < currentX;
        int dir = facingLeft ? -1 : 1;
        int next = currentX + dir * ((t.speed() + subX) >> 8);
        subX = (t.speed() + subX) & 0xFF;
        int floor = floorAt(next, currentY + t.depth());
        if (floor == Integer.MIN_VALUE || Math.abs(floor - (currentY + t.depth())) > 20) {
            facingLeft = !facingLeft;
            return;
        }
        currentX = next;
        currentY = floor - t.depth();
    }

    /** Falling (spawned from above, or knocked off a ledge) until the floor catches it. */
    private void fall(Species.Traits t) {
        vy = Math.min(vy + GRAVITY, 0x800);
        step();
        int floor = floorAt(currentX, currentY + t.depth());
        if (vy >= 0 && floor != Integer.MIN_VALUE && currentY + t.depth() >= floor) {
            currentY = floor - t.depth();
            vy = 0;
            vx = 0;
            grounded = true;
        }
    }

    private void hop(Species.Traits t, int px, boolean leaper) {
        if (!grounded) {
            fall(t);
            return;
        }
        facingLeft = px < currentX;
        if (--timer > 0) return;
        timer = leaper ? 70 : 45 + Math.floorMod(ticks * 7, 30);
        grounded = false;
        int dir = facingLeft ? -1 : 1;
        vx = dir * (leaper ? t.speed() : t.speed() * 2);
        vy = leaper ? -0x700 : -0x480;
    }

    private void fly(Species.Traits t, int px, int py) {
        int ty = py - 8 + (elite() ? 0 : (int) (Math.sin((ticks + homeX) * 0.05) * 24));
        int accel = Math.max(8, t.speed() / 12);
        vx += Integer.signum(px - currentX) * accel;
        vy += Integer.signum(ty - currentY) * accel;
        vx = Math.max(-t.speed(), Math.min(t.speed(), vx));
        vy = Math.max(-t.speed(), Math.min(t.speed(), vy));
        step();
        facingLeft = vx < 0;
        keepAboveFloor(t);
    }

    /** Hovers level with Sonic at a distance, then charges straight across. */
    private void dive(Species.Traits t, int px, int py) {
        if (state == 0) {
            int side = currentX < px ? -1 : 1;
            int tx = px + side * 120, ty = py - 4;
            vx = Integer.signum(tx - currentX) * Math.min(Math.abs(tx - currentX) * 32, 0x180);
            vy = Integer.signum(ty - currentY) * Math.min(Math.abs(ty - currentY) * 32, 0x180);
            step();
            facingLeft = px < currentX;
            if (++timer > 70 || Math.abs(tx - currentX) < 8 && Math.abs(ty - currentY) < 8) {
                state = 1;
                timer = 0;
                vx = (facingLeft ? -1 : 1) * t.speed();
                vy = 0;
            }
        } else {
            step();
            if (++timer > 80) { state = 0; timer = 0; }
        }
        keepAboveFloor(t);
    }

    private void turret(int px) {
        if (!grounded) { fall(traits()); return; }
        facingLeft = px < currentX;
        animFrame = 7;
    }

    /** Asteron: drift in, then burst into five spikes once close. */
    private void explode(Species.Traits t, int px, int py) {
        fly(t, px, py);
        if (Math.abs(px - currentX) < 44 && Math.abs(py - currentY) < 44 && ++timer > 20) {
            for (int i = 0; i < 5; i++) {
                double a = -Math.PI / 2 + i * 2 * Math.PI / 5;
                fireShot(t.shotKey(), 2 + i % 3, (int) (Math.cos(a) * 0x300), (int) (Math.sin(a) * 0x300), false);
            }
            services().playSfx(0xC1);
            hp = 0;
            destroyBadnik(services().camera().getFocusedSprite());
        }
    }

    private void keepAboveFloor(Species.Traits t) {
        int floor = floorAt(currentX, currentY + t.depth());
        if (floor != Integer.MIN_VALUE && currentY + t.depth() > floor - 4) {
            currentY = floor - 4 - t.depth();
            if (vy > 0) vy = -vy / 2;
        }
    }

    private void shoot(Species.Traits t, int px, int py) {
        if (--shotTimer > 0) return;
        shotTimer = t.shotPeriod() - (elite() ? t.shotPeriod() / 3 : 0);
        var camera = services().camera();
        if (currentX < camera.getX() - 16 || currentX > camera.getX() + camera.getWidth() + 16) return;
        double dx = px - currentX, dy = py - currentY;
        double length = Math.max(1, Math.hypot(dx, dy));
        int speed = 0x220;
        boolean lob = t.id() == Species.COCONUTS || t.id() == Species.OCTUS;
        if (lob) fireShot(t.shotKey(), t.shotFrame(), (int) Math.signum(dx) * 0x180, -0x400, true);
        else fireShot(t.shotKey(), t.shotFrame(), (int) (dx / length * speed), (int) (dy / length * speed), false);
    }

    private void fireShot(String key, int frame, int svx, int svy, boolean gravity) {
        if (!services().objectManager().hasFreeDynamicSlot()) return;
        int sx = currentX, sy = currentY;
        spawnFreeChild(() -> Shot.of(sx, sy, key, frame, gravity, svx, svy));
    }

    @Override protected void updateAnimation(int vIntRunCount) {
        var t = traits();
        if (t.archetype() == Species.TURRET) {
            animFrame = shotTimer < 12 ? 8 + (12 - shotTimer) / 4 % 4 : 7;
        } else if (t.id() == Species.FLASHER && litTimer > 0) {
            animFrame = 3 + (ticks / 4) % 2;
        } else {
            animFrame = t.frame(ticks);
        }
    }

    /** A stomp from above on a spiked or lit enemy hurts Sonic instead. */
    private boolean spikedNow() {
        var t = traits();
        return t.spiked() || t.id() == Species.FLASHER && litTimer > 0;
    }

    @Override protected int getCollisionSizeIndex() { return traits().collision() & 0x3F; }
    /** Arena objects are always near the camera; touch them without waiting for a render pass. */
    @Override public boolean requiresRenderFlagForTouch() { return false; }

    @Override public int getCollisionFlags() {
        var run = run();
        if (isDestroyed() || hp <= 0 || run != null && run.paused || flash > HIT_FLASH - 4) return 0;
        return (spikedNow() ? 0x80 : 0x00) | getCollisionSizeIndex();
    }

    @Override public void onTouchResponse(PlayableEntity entity, TouchResponseResult result, int frameCounter) {
        if (isDestroyed() || !(entity instanceof AbstractPlayableSprite player) || player.isCpuControlled()) return;
        Guard.absorb(services(), player, result);
    }

    /** A stomp or roll: stomp damage times the combo, and a guaranteed rebound when from above. */
    @Override public void onPlayerAttack(PlayableEntity entity, TouchResponseResult result) {
        if (isDestroyed() || hp <= 0) return;
        var run = run();
        Stage stage = Stage.find(services());
        boolean bounce = entity.getAir() && entity.getCentreY() < currentY;
        if (bounce && run != null && !(entity instanceof AbstractPlayableSprite sprite && sprite.isCpuControlled())) {
            run.combo++;
            run.bestCombo = Math.max(run.bestCombo, run.combo);
        }
        double scale = run == null ? 1 : run.damageMultiplier() * run.comboMultiplier();
        int base = run == null ? 1 : run.stompDamage();
        int damage = Math.max(1, (int) Math.round(base * 2 * scale));
        int bounceSpeed = run == null ? 0x580 : run.bounceSpeed();
        boolean killed = applyDamage(damage, entity.getCentreX() < currentX ? 1 : -1, entity);
        if (bounce) {
            // The engine negates y_vel after a kill (Touch_KillEnemy); otherwise set the rebound here.
            entity.setYSpeed((short) (killed ? bounceSpeed : -bounceSpeed));
        } else if (!killed) {
            // A roll into a tough badnik knocks Sonic back off it.
            entity.setXSpeed((short) (entity.getCentreX() < currentX ? -0x300 : 0x300));
            entity.setGSpeed(entity.getXSpeed());
        }
        if (stage != null && bounce) stage.onBounce(currentX, currentY);
    }

    /** Weapon damage; returns true if it defeated the enemy. Respects the brief weapon i-frames. */
    boolean hurt(int damage, int knockDir) {
        if (!alive() || iframes > 0) return false;
        iframes = WEAPON_IFRAMES;
        return applyDamage(damage, knockDir, services().camera().getFocusedSprite());
    }

    private boolean applyDamage(int damage, int knockDir, PlayableEntity player) {
        hp -= damage;
        flash = HIT_FLASH;
        Stage stage = Stage.find(services());
        if (stage != null) stage.popup(currentX, currentY - 12, damage, elite());
        if (hp > 0) {
            services().playSfx(0xAC); // S2 sfx_HitBoss: a hit that did not destroy.
            if (traits().archetype() != Species.TURRET) {
                currentX += knockDir * 4;
                if (!grounded) vx = knockDir * 0x200;
            }
            return false;
        }
        if (stage != null) stage.onEnemyDefeated(this, currentX, currentY);
        destroyBadnik(player);
        return true;
    }

    @Override public int getPriorityBucket() { return RenderPriority.bucket(4); }
    @Override public int getOnScreenHalfWidth() { return traits().halfWidth(); }

    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed()) return;
        if (flash > 0 && (flash & 2) != 0) return;
        var t = traits();
        var renderer = getRenderer(t.artKey());
        if (renderer == null) return;
        boolean flip = t.artFacesRight() == facingLeft;
        if (elite() && (ticks / 6) % 3 == 0) {
            renderer.drawFrameIndex(animFrame, currentX, currentY, flip, false, 0);
        } else {
            renderer.drawFrameIndex(animFrame, currentX, currentY, flip, false);
        }
        if (elite() || hp < maxHp && maxHp >= 6) {
            int w = elite() ? 28 : 20;
            int top = currentY - t.halfWidth() - 10;
            Draw.rectWorld(services(), currentX - w / 2 - 1, top - 1, w + 2, 4, Draw.NAVY, 0.9f);
            Draw.rectWorld(services(), currentX - w / 2, top, w * Math.max(0, hp) / maxHp, 2,
                    elite() ? Draw.GOLD : Draw.RED, 1f);
        }
    }
}
