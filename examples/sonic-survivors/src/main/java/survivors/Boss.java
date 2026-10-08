package survivors;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.*;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import java.util.List;

/**
 * The stage boss, arriving when the survival clock runs out: Eggman in the zone's own vehicle
 * (drawn with the zone's ROM boss art), sweeping the arena, dropping volleys of the zone's
 * projectile and swooping at Sonic; or, on the Death Egg, Silver Sonic, who walks, crouches
 * and spin-dashes across the arena (harmful while spinning). Stomps rebound Sonic off it with
 * the stock boss bounce. Spawn subtype: the stage index and the {@link #RUNNER} flag; the
 * hitpoints come through the constructor.
 */
public final class Boss extends AbstractObjectInstance
        implements ModRewindRecreatable, TouchResponseProvider, TouchResponseListener, TouchResponseAttackable {
    static final int ENTER = 0, SWEEP = 1, VOLLEY = 2, SWOOP = 3, RISE = 4, DEFEATED = 5;
    // Silver Sonic.
    static final int WALK = 10, CROUCH = 11, SPIN = 12, LEAP = 13;
    // The Death Egg finale: Eggman on foot, fleeing.
    static final int FLEE = 20, VAULT = 21;
    /** Subtype flag: the fleeing Eggman rather than the stage's boss. */
    static final int RUNNER = 0x10;
    static final int RUNNER_HITS = 3;
    static final int HIT_FLASH = 32;
    static final int DEFEAT_FRAMES = 150;

    private int x;
    private int y;
    private int vx;
    private int vy;
    private int subX;
    private int subY;
    private int hp;
    private int maxHp;
    private int state;
    private int timer;
    private int ticks;
    private int flash;
    private int iframes;
    private int laugh;
    private int shots;
    private boolean facingLeft = true;
    private int groundY;

    public Boss(ObjectSpawn spawn) {
        this(spawn, 1);
    }

    public Boss(ObjectSpawn spawn, int hitpoints) {
        super(spawn, "Survivors boss");
        x = spawn.x();
        y = spawn.y();
        hp = maxHp = Math.max(1, hitpoints);
        state = runner() ? FLEE : silver() ? WALK : ENTER;
    }

    static ObjectSpawn spawnAt(int x, int y, int stage) {
        return new ObjectSpawn(x, y, 0, stage & 0x0F, 0, false, y, -1,
                SurvivorsMod.ID, SurvivorsMod.ID + ":boss");
    }

    /** The Death Egg finale's fleeing Eggman: three stomps catch him. */
    static ObjectSpawn runnerAt(int x, int y) {
        return new ObjectSpawn(x, y, 0, Stages.DEZ | RUNNER, 0, false, y, -1,
                SurvivorsMod.ID, SurvivorsMod.ID + ":boss");
    }

    int stage() { return spawn.subtype() & 0x0F; }
    boolean runner() { return (spawn.subtype() & RUNNER) != 0; }
    boolean silver() { return stage() == Stages.DEZ && !runner(); }
    int hp() { return hp; }
    int maxHp() { return maxHp; }
    boolean alive() { return !isDestroyed() && state != DEFEATED; }

    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public boolean isPersistent() { return !isDestroyed(); }
    @Override public boolean isHighPriority() { return true; }
    @Override public AbstractObjectInstance recreateForRewind(ObjectReconstructionContext context) {
        return new Boss(context.spawn());
    }

    private Arena arena() { return services().gameService(Arena.class); }

    @Override public void update(int vIntRunCount, PlayableEntity player) {
        var run = services().gameService(RunState.class);
        if (run != null && run.paused) return;
        ticks++;
        if (flash > 0) flash--;
        if (iframes > 0) iframes--;
        if (laugh > 0) laugh--;
        int px = player == null ? x : player.getCentreX();
        int py = player == null ? y : player.getCentreY();
        if (state == DEFEATED) { defeated(); return; }
        if (runner()) flee(px); else if (silver()) silverSonic(px); else eggman(px, py);
        updateDynamicSpawn(x, y);
    }

    private void step() {
        subX += vx;
        subY += vy;
        x += subX >> 8;
        y += subY >> 8;
        subX &= 0xFF;
        subY &= 0xFF;
    }

    /**
     * Eggman hovers within a jump of the ground Sonic last stood on (Sonic's jump reaches about
     * 100px), so he stays reachable and on camera however the arena floor rises and falls.
     */
    private int hoverY() {
        var arena = arena();
        if (groundY == 0) groundY = arena == null ? y + 88 : arena.floorTop();
        int target = groundY - (tank() ? 28 : 88);
        // Keep the whole vehicle clear of the HUD even when the arena camera cannot follow a high ledge.
        return tank() ? target : Math.max(target, services().camera().getY() + 72);
    }

    /** Hill Top's boss is a tank: it rolls along the ground and charges instead of swooping. */
    private boolean tank() { return stage() == Stages.HTZ && !runner(); }

    /** Follows the ground Sonic stands on; held while he is airborne. */
    private void trackGround() {
        var player = services().camera().getFocusedSprite();
        if (player != null && !player.getAir() && !player.getDead()) groundY = player.getCentreY() + 19;
    }

    /** Eases toward the hover height rather than snapping when Sonic changes level. */
    private void approachHover(int bob) {
        int target = hoverY() + bob;
        y += Integer.signum(target - y) * Math.min(Math.abs(target - y), 2);
    }

    private void eggman(int px, int py) {
        var arena = arena();
        timer++;
        trackGround();
        switch (state) {
            case ENTER -> {
                y += 2;
                if (y >= hoverY()) { y = hoverY(); state = SWEEP; timer = 0; vx = facingLeft ? -0x180 : 0x180; }
            }
            case SWEEP -> {
                vy = 0;
                step();
                approachHover(tank() ? 0 : (int) Math.round(Math.sin(ticks * 0.06) * 10));
                if (arena != null && (x < arena.left() + 48 || x > arena.right() - 48)) {
                    x = arena.clampX(x, 48);
                    vx = -vx;
                }
                facingLeft = vx < 0;
                int enraged = hp * 2 < maxHp ? 40 : 0;
                if (timer > 150 - enraged) {
                    timer = 0;
                    state = ticks / 150 % 2 == 0 ? VOLLEY : SWOOP;
                    shots = 0;
                }
            }
            case VOLLEY -> {
                vy = 0;
                facingLeft = px < x;
                int count = hp * 2 < maxHp ? 5 : 3;
                if (timer % 18 == 0 && shots < count) {
                    shots++;
                    fire(px, py);
                }
                if (timer > 18 * count + 20) { state = SWEEP; timer = 0; vx = facingLeft ? -0x180 : 0x180; }
            }
            case SWOOP -> {
                if (tank()) {
                    // Charge along the ground at Sonic, then resume the patrol.
                    if (timer == 1) vx = (px < x ? -1 : 1) * 0x400;
                    facingLeft = vx < 0;
                    step();
                    approachHover(0);
                    if (arena != null && (x < arena.left() + 48 || x > arena.right() - 48) || timer > 70) {
                        x = arena == null ? x : arena.clampX(x, 48);
                        state = SWEEP;
                        timer = 0;
                        vx = facingLeft ? 0x180 : -0x180;
                    }
                    break;
                }
                // A readable wind-up before a dive; no hidden downward drift from the previous dive.
                if (timer <= 24) { vx = vy = 0; break; }
                if (stage() == Stages.WFZ) {
                    // The ace commits to a straight strafe after the warning, giving the player a dodge window.
                    if (timer == 25) vx = (px < x ? -1 : 1) * 0x500;
                    facingLeft = vx < 0;
                    step();
                    if (timer > 70 || arena != null && (x < arena.left() + 48 || x > arena.right() - 48)) {
                        if (arena != null) x = arena.clampX(x, 48);
                        state = RISE;
                        timer = 0;
                    }
                    break;
                }
                // Dive toward where Sonic stands, then climb back.
                facingLeft = px < x;
                int floor = groundY - 24;
                vx = Integer.signum(px - x) * Math.min(Math.abs(px - x) * 16, 0x300);
                vy = Integer.signum(floor - y) * Math.min(Math.abs(floor - y) * 16, 0x400);
                step();
                if (timer > 60 || Math.abs(floor - y) < 6 && Math.abs(px - x) < 12) { state = RISE; timer = 0; }
            }
            case RISE -> {
                vx = 0;
                y -= 2;
                if (y <= hoverY()) { y = hoverY(); state = SWEEP; timer = 0; vx = facingLeft ? -0x180 : 0x180; }
            }
            default -> { }
        }
    }

    private void fire(int px, int py) {
        int stage = stage();
        String key = Stages.bossShotKey(stage);
        int frame = Stages.bossShotFrame(stage);
        int svx, svy;
        boolean gravity;
        if (stage == Stages.MCZ) {
            // Mystic Cave: rocks fall from the cave roof above Sonic.
            var arena = arena();
            int rx = (arena == null ? px : arena.clampX(px, 16)) + (shots - 2) * 40;
            ArenaObjects.spawn(services(), () -> Shot.of(rx, y - 120, key, frame, true, 0, 0));
            return;
        }
        if (stage == Stages.ARZ) {
            // The queen's rotating three-way fan leaves gaps instead of aiming every shot at the player.
            for (int i = 0; i < 3; i++) {
                double angle = Math.PI / 2 + (i - 1) * 0.6 + (shots % 2 == 0 ? 0.2 : -0.2);
                int fx = (int) (Math.cos(angle) * 0x240), fy = (int) (Math.sin(angle) * 0x240);
                ArenaObjects.spawn(services(), () -> Shot.of(x, y + 16, key, frame, false, fx, fy));
            }
            services().playSfx(0xAE);
            return;
        }
        double dx = px - x, dy = py - y;
        double length = Math.max(1, Math.hypot(dx, dy));
        svx = (int) (dx / length * 0x280);
        svy = (int) (dy / length * 0x280);
        gravity = stage == Stages.EHZ || stage == Stages.MTZ;
        if (gravity) { svx = (int) Math.signum(dx) * (0x80 + shots * 0x60); svy = -0x100; }
        int fvx = svx, fvy = svy, sy = y + 16;
        boolean lob = gravity;
        ArenaObjects.spawn(services(), () -> Shot.of(x, sy, key, frame, lob, fvx, fvy));
        services().playSfx(0xAE); // S2 sfx_ArrowFiring.
    }

    private int floorAt(int fx, int feetY) {
        var levelManager = services().levelManager();
        for (int probe = feetY - 24; probe <= feetY + 200; probe += 16) {
            var r = ObjectTerrainUtils.checkFloorDist(levelManager, fx, probe);
            if (r.foundSurface() && r.distance() >= -16 && r.distance() < 16) return probe + r.distance();
        }
        return Integer.MIN_VALUE;
    }

    /** Silver Sonic: walk at Sonic, crouch, then spin-dash wall to wall; leap when enraged. */
    private void silverSonic(int px) {
        var arena = arena();
        timer++;
        int depth = 24;
        switch (state) {
            case WALK -> {
                facingLeft = px < x;
                vx = facingLeft ? -0x140 : 0x140;
                step();
                if (timer > 90) { state = CROUCH; timer = 0; vx = 0; services().playSfx(0xEE); }
            }
            case CROUCH -> {
                if (timer > 40) {
                    state = SPIN;
                    timer = 0;
                    vx = facingLeft ? -0x700 : 0x700;
                    services().playSfx(0xBC); // sfx_SpindashRelease.
                }
            }
            case SPIN -> {
                step();
                if (arena != null && (x < arena.left() + 24 || x > arena.right() - 24)) {
                    x = arena.clampX(x, 24);
                    vx = -vx;
                    facingLeft = vx < 0;
                    shots++;
                }
                if (shots >= (hp * 2 < maxHp ? 3 : 2)) {
                    shots = 0;
                    timer = 0;
                    state = hp * 2 < maxHp ? LEAP : WALK;
                    if (state == LEAP) { vy = -0x780; vx = Integer.signum(px - x) * 0x300; }
                }
            }
            case LEAP -> {
                vy += 0x38;
                step();
                if (arena != null) x = arena.clampX(x, 24);
                int floor = floorAt(x, y + depth);
                if (vy > 0 && floor != Integer.MIN_VALUE && y + depth >= floor) {
                    y = floor - depth;
                    vy = 0;
                    state = WALK;
                    timer = 0;
                    services().playSfx(0xBD); // sfx_Hammer: the landing thud.
                    for (int i = -1; i <= 1; i += 2) {
                        int dir = i, sy = y + 12;
                        ArenaObjects.spawn(services(), () -> Shot.of(x, sy, Stages.bossShotKey(Stages.DEZ), 15, false, dir * 0x300, 0));
                    }
                }
                return;
            }
            default -> { }
        }
        int floor = floorAt(x, y + depth);
        if (floor != Integer.MIN_VALUE) y = floor - depth;
        else y += 2;
    }

    /**
     * Eggman runs from Sonic along the floor, a little slower than Sonic's top speed. Cornered
     * against a wall, he vaults back over Sonic's head.
     */
    private void flee(int px) {
        var arena = arena();
        int depth = 20;
        timer++;
        if (state == VAULT) {
            vy += 0x38;
            step();
            if (arena != null) x = arena.clampX(x, 24);
            int floor = floorAt(x, y + depth);
            if (vy > 0 && floor != Integer.MIN_VALUE && y + depth >= floor) {
                y = floor - depth;
                vy = 0;
                state = FLEE;
                timer = 0;
            }
            return;
        }
        int away = px < x ? 1 : -1;
        facingLeft = away < 0;
        vx = away * (hp == 1 ? 0x500 : 0x440);
        step();
        if (arena != null && (x <= arena.left() + 32 || x >= arena.right() - 32) && Math.abs(px - x) < 120) {
            x = arena.clampX(x, 32);
            state = VAULT;
            timer = 0;
            vy = -0x800;
            vx = -away * 0x300;
            facingLeft = vx < 0;
            services().playSfx(0xA0); // sfx_Jump.
            return;
        }
        if (arena != null) x = arena.clampX(x, 32);
        int floor = floorAt(x, y + depth);
        if (floor != Integer.MIN_VALUE) y = floor - depth;
        else y += 2;
    }

    private void defeated() {
        timer++;
        if (timer % 8 == 0) services().playSfx(0xC4); // sfx_BossExplosion.
        if (!silver() && !runner()) y += 1;
        if (timer >= DEFEAT_FRAMES) {
            Stage stage = Stage.find(services());
            if (stage != null) stage.onBossDefeated(x, y);
            setDestroyed(true);
        }
    }

    /** Sonic was hurt: Eggman laughs. */
    void laugh() { laugh = 60; }

    private boolean spinning() { return silver() && (state == SPIN || state == LEAP); }

    @Override public int getCollisionFlags() {
        var run = services().gameService(RunState.class);
        if (isDestroyed() || state == DEFEATED || flash > 0 || state == ENTER || run != null && run.paused) return 0;
        // $0F (24x24): a hazard while Silver Sonic spins, otherwise an enemy that bounces Sonic off.
        return spinning() ? 0x80 | 0x0F : 0x0F;
    }

    /** Nonzero while alive: the engine applies the stock boss rebound (Touch_Enemy_Part2). */
    @Override public int getCollisionProperty() { return hp > 0 ? 1 : 0; }
    /** Arena objects are always near the camera; touch them without waiting for a render pass. */
    @Override public boolean requiresRenderFlagForTouch() { return false; }

    @Override public void onTouchResponse(PlayableEntity entity, TouchResponseResult result, int frameCounter) {
        if (isDestroyed() || !(entity instanceof AbstractPlayableSprite player) || player.isCpuControlled()) return;
        if (Guard.absorb(services(), player, result)) laugh();
    }

    @Override public void onPlayerAttack(PlayableEntity entity, TouchResponseResult result) {
        if (!alive()) return;
        var run = services().gameService(RunState.class);
        if (Enemy.idleSpin(entity) && entity instanceof AbstractPlayableSprite player) {
            // A crouched spin-dash charge or a slow roll is not an attack: the boss hurts.
            Guard.takeHit(services(), player);
            return;
        }
        boolean bounce = entity.getAir() && entity.getCentreY() < y;
        if (bounce && run != null) {
            run.combo++;
            run.bestCombo = Math.max(run.bestCombo, run.combo);
        }
        double scale = run == null ? 1 : run.damageMultiplier() * run.comboMultiplier();
        int base = run == null ? 1 : run.stompDamage();
        damage(Math.max(1, (int) Math.round(base * 2 * scale)));
        flash = HIT_FLASH;
        Stage stage = Stage.find(services());
        if (stage != null && bounce) stage.onBounce(x, y);
    }

    /** Weapon damage, with brief i-frames between weapon hits. */
    boolean hurt(int amount) {
        if (!alive() || iframes > 0 || state == ENTER || runner() && flash > 0) return false;
        // Short weapon i-frames: several weapons share the window, so it must not swallow them.
        iframes = 6;
        damage(amount);
        return state == DEFEATED;
    }

    private void damage(int amount) {
        if (runner()) {
            amount = 1;
            flash = 60;
        }
        int dealt = Math.min(hp, amount);
        hp -= amount;
        services().playSfx(0xAC); // sfx_HitBoss.
        Stage stage = Stage.find(services());
        if (stage != null) stage.statDamage += dealt;
        if (stage != null) stage.popup(x, y - 24, dealt);
        if (hp <= 0) {
            hp = 0;
            state = DEFEATED;
            timer = 0;
            vx = vy = 0;
            if (stage != null) stage.onBossBeaten();
        }
    }

    /**
     * Acts that load no Eggman art (Metropolis act 3 is its own ROM zone) get an Eggman core:
     * a code-drawn metal sphere with a red eye, ringed by the zone's own flying badniks.
     */
    private void drawCore() {
        var s = services();
        int[] air = Stages.air(stage());
        var guard = air.length == 0 ? null : getRenderer(Species.of(air[0]).artKey());
        if (guard != null) {
            var traits = Species.of(air[0]);
            for (int i = 0; i < 4; i++) {
                double a = ticks * 0.05 + i * Math.PI / 2;
                guard.drawFrameIndex(traits.frame(ticks), x + (int) (Math.cos(a) * 30), y + (int) (Math.sin(a) * 30),
                        false, false);
            }
        }
        Draw.circleWorld(s, x, y, 19, 19, Draw.NAVY, 1f);
        Draw.circleWorld(s, x, y, 17, 17, 0x8088A0, 1f);
        Draw.circleWorld(s, x - 4, y - 5, 7, 7, 0xC8D0E0, 1f);
        int eye = laugh > 0 ? Draw.YELLOW : flash > 0 ? Draw.WHITE : Draw.RED;
        Draw.circleWorld(s, x + (facingLeft ? -5 : 5), y + 2, 6, 6, Draw.NAVY, 1f);
        Draw.circleWorld(s, x + (facingLeft ? -5 : 5), y + 2, 4, 4, eye, 1f);
    }

    @Override public int getPriorityBucket() { return RenderPriority.bucket(3); }
    @Override public int getOnScreenHalfWidth() { return 48; }

    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed()) return;
        int stage = stage();
        if (state == SWOOP && timer <= 24 && !tank()) {
            Draw.labelWorld(services(), "DIVE!", x - 14, y - 48, Draw.RED);
        }
        int[] frames = Stages.bossFrames(stage);
        var renderer = getRenderer(Stages.bossKey(stage));
        if (state == DEFEATED) {
            var boom = getRenderer("boss_explosion");
            if (boom != null) {
                for (int i = 0; i < 3; i++) {
                    int seed = (timer / 6 + i * 3) * 2654435;
                    boom.drawFrameIndex((timer / 3 + i * 2) % 7, x + seed % 40 - 20, y + (seed >> 8) % 32 - 16, false, false);
                }
            }
        }
        if (flash > 0 && (flash & 2) != 0) return;
        if (renderer == null || stage == Stages.OOZ) {
            drawCore();
            return;
        }
        // Two zone champions break up the Eggman vehicle fights, using complete native badnik frames.
        if (stage == Stages.ARZ || stage == Stages.WFZ) {
            var champion = Species.of(stage == Stages.ARZ ? Species.WHISP : Species.BALKIRY);
            Draw.scaledSprite(services(), champion.artKey(), champion.frame(ticks), x, y,
                    champion.artFacesRight() == facingLeft, 2f);
            return;
        }
        int frame;
        if (runner()) {
            frame = state == DEFEATED ? 6 : state == VAULT ? 4 : 2 + (ticks / 4) % 3;
            var eggman = getRenderer("dez_eggman");
            if (eggman != null) eggman.drawFrameIndex(frame, x, y, !facingLeft, false);
            return;
        } else if (silver()) {
            frame = switch (state) {
                case CROUCH -> 3;
                case SPIN, LEAP -> 6 + (ticks / 2) % 3;
                default -> (ticks / 8) % 3;
            };
            if (state == DEFEATED) frame = 5;
        } else {
            frame = state == DEFEATED || flash > 0 ? frames[2] : laugh > 0 ? frames[1] : frames[0];
        }
        // The stock bosses compose vehicle and face mappings separately. A face alone is not a boss.
        // Component indices/order follow Sonic2*BossInstance.appendRenderCommands and their children.
        boolean flip = !facingLeft;
        switch (stage) {
            case Stages.EHZ -> renderer.drawFrameIndex(15, x, y, flip, false);
            case Stages.CPZ -> renderer.drawFrameIndex(0, x, y, flip, false);
            case Stages.CNZ -> {
                renderer.drawFrameIndex(4 + ticks / 8 % 2, x, y, flip, false);
                renderer.drawFrameIndex(1, x, y, flip, false);
                renderer.drawFrameIndex(6 + ticks / 4 % 2, x, y, flip, false);
                renderer.drawFrameIndex(2 + ticks / 8 % 2, x, y, flip, false);
                return;
            }
            case Stages.MCZ -> {
                renderer.drawFrameIndex(2 + ticks / 5 % 3, x + (flip ? 40 : -40), y, flip, false);
                renderer.drawFrameIndex(frame, x, y, flip, false);
                renderer.drawFrameIndex(ticks / 8 % 2, x, y, flip, false);
                renderer.drawFrameIndex(2 + ticks / 5 % 3, x, y, flip, false);
                renderer.drawFrameIndex(5 + ticks / 5 % 2, x, y, flip, false);
                return;
            }
            case Stages.MTZ -> {
                renderer.drawFrameIndex(0, x, y, flip, false);
                renderer.drawFrameIndex(2, x, y, flip, false);
            }
            default -> { }
        }
        renderer.drawFrameIndex(frame, x, y, flip, false,
                stage == Stages.EHZ || stage == Stages.CPZ ? 0 : -1);
        if (stage == Stages.HTZ && state != DEFEATED) {
            renderer.drawFrameIndex(2 + ticks / 6 % 2, x, y, flip, false);
        }
    }
}
