package survivors;

import com.openggf.audio.GameSound;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic2.audio.Sonic2Music;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import java.util.ArrayList;
import java.util.List;

/**
 * The arena's controller, placed as the level's only object. It walls the camera and Sonic into
 * the arena, runs the stage's phases (camp shop before a run, the intro, two minutes of waves,
 * the boss, the clear screen and route choice, game over and victory), spawns the badnik waves,
 * owns Sonic's weapons and moves, the bounce combo and the level-up cards, and draws the HUD.
 * Weapon projectiles and effects live in fixed primitive pools here so the ordinary mod-object
 * rewind capture restores them with the controller.
 */
public final class Stage extends AbstractObjectInstance implements RewindRecreatable {
    static final int CAMP = 0, INTRO = 1, FIGHT = 2, BOSS = 3, CLEAR_WAIT = 4, CLEAR = 5, DEAD = 6, VICTORY = 7;
    static final int OVERLAY_NONE = 0, OVERLAY_LEVEL_UP = 1;
    static final int INTRO_FRAMES = 150;
    static final int ELITE_PERIOD = 60 * 30;
    /** Every this many chained bounces, Sonic goes invincible for the stock duration. */
    static final int FEVER_COMBO = 10;
    static final int RING_FORMATION_PERIOD = 600;
    // GLFW_KEY_ESCAPE: a tap leaves the arena for the title (banking the run).
    private static final int KEY_ESCAPE = 256;

    // Player projectile pool.
    static final int MAX_P = 48;
    static final int P_SPARK = 1, P_HOMING = 2, P_BOOM = 3, P_FLICKY = 4;
    // Effect pool.
    static final int MAX_E = 64;
    static final int E_NUMBER = 1, E_SHOCK = 2, E_POUND = 3, E_BOLT = 4, E_PUFF = 5;

    int phase;
    int phaseFrames;
    int overlay;
    boolean started;
    int stageFrames;
    int fightFrames;
    int spawnTimer;
    int eliteTimer;
    boolean bossSpawned;
    int frameTick;
    // Menus.
    int menuIndex;
    boolean upHeld, downHeld, jumpHeld;
    int[] cards = new int[4];
    int cardCount;
    // Frozen player state while a menu pauses play.
    boolean playerFrozen;
    int savedX, savedY, savedG;
    // Weapons and moves.
    int boomTimer, flickyTimer, barrierTimer, orbitAngle, ringTimer;
    int airFrames, airJumpsUsed, dashFrames, comboGrace;
    boolean pounding, downWasHeld, wasGrounded;
    // Banners.
    String banner = "";
    int bannerFrames;
    int bannerColour = Draw.GOLD;
    int hitFlash;
    // Stage results.
    int stageKills;
    int bankedThisRun;
    boolean emeraldNew;
    int clearChoices;

    final int[] pX = new int[MAX_P], pY = new int[MAX_P], pVX = new int[MAX_P], pVY = new int[MAX_P],
            pLife = new int[MAX_P], pKind = new int[MAX_P], pDmg = new int[MAX_P], pHits = new int[MAX_P];
    final int[] eX = new int[MAX_E], eY = new int[MAX_E], eKind = new int[MAX_E], eAge = new int[MAX_E],
            eA = new int[MAX_E], eB = new int[MAX_E], eC = new int[MAX_E], eD = new int[MAX_E];

    public Stage(ObjectSpawn spawn) { super(spawn, "Survivors stage"); }

    static ObjectSpawn spawnAt(int x, int y) {
        return new ObjectSpawn(x, y, 0, 0, 0, false, y, -1, SurvivorsMod.ID, SurvivorsMod.ID + ":stage");
    }

    /** The live controller, or null outside an arena. */
    static Stage find(ObjectServices services) {
        var objects = services.objectManager();
        if (objects == null) return null;
        for (var object : objects.getActiveObjects()) {
            if (object instanceof Stage stage && !stage.isDestroyed()) return stage;
        }
        return null;
    }

    @Override public boolean isPersistent() { return true; }
    @Override public boolean isHighPriority() { return true; }
    @Override public int getPriorityBucket() { return 0; }
    @Override public AbstractObjectInstance recreateForRewind(RewindRecreateContext context) {
        return new Stage(context.spawn());
    }

    RunState run() { return services().gameService(RunState.class); }
    Profile profile() { return services().gameService(Profile.class); }
    Arena arena() { return services().gameService(Arena.class); }
    boolean paused() { return run().paused; }

    // =========================================================================================
    // Frame update
    // =========================================================================================

    @Override public void update(int vIntRunCount, PlayableEntity entity) {
        if (!(entity instanceof AbstractPlayableSprite player)) return;
        var run = run();
        holdTimer();
        // Lives come from nothing here: pre-claim the stock 100/200-ring extra lives every frame.
        services().levelGamestate().setRingExtraLifeFlags(0x06);
        services().levelManager().setForceHudSuppressed(true);
        holdArena();
        holdCeiling(player);
        frameTick++;
        phaseFrames++;
        if (bannerFrames > 0) bannerFrames--;
        if (hitFlash > 0) hitFlash--;
        if (!started) start(player);
        if (escapePressed()) { retire(true); return; }
        if (player.getDead() && phase != DEAD && phase != VICTORY) { die(); }
        if (!player.getDead()) setLives(1);
        boolean wantPause = phase == CAMP || phase == CLEAR || phase == VICTORY || overlay != OVERLAY_NONE
                || phase == DEAD;
        run.paused = wantPause;
        freezePlayer(player, wantPause && !player.getDead());
        switch (phase) {
            case CAMP -> campMenu(player);
            case INTRO -> {
                if (overlay == OVERLAY_NONE && run.pendingLevels > 0) {
                    openLevelUp();
                    run.paused = true;
                    freezePlayer(player, true);
                    break;
                }
                if (overlay != OVERLAY_NONE) { levelUpMenu(player); break; }
                if (phaseFrames >= INTRO_FRAMES) beginFight();
                updateEffects();
            }
            case FIGHT, BOSS -> {
                if (overlay == OVERLAY_NONE && run.pendingLevels > 0) {
                    openLevelUp();
                    run.paused = true;
                    freezePlayer(player, true);
                    break;
                }
                if (overlay != OVERLAY_NONE) { levelUpMenu(player); break; }
                fight(player);
            }
            case CLEAR_WAIT -> {
                updateProjectiles();
                updateEffects();
                if (phaseFrames > 330) collectEmeraldNow();
            }
            case CLEAR -> clearMenu(player);
            case DEAD -> deadMenu(player);
            default -> victoryMenu(player);
        }
    }

    /**
     * The stock level timer never runs out here. During play it runs but is held at zero rather
     * than paused, because Sonic 2 refuses the Super Sonic transformation while it is paused.
     */
    private void holdTimer() {
        var level = services().levelGamestate();
        if (phase == FIGHT || phase == BOSS) {
            if (level.isTimerPaused()) level.resumeTimer();
            level.setTimerFrames(0);
        } else {
            level.pauseTimer();
        }
    }

    /** All seven Chaos Emeralds: the run carries them, so 50 rings and a double jump go Super. */
    private void grantEmeralds() {
        if (!run().allRelics()) return;
        var state = services().gameState();
        for (int i = 0; i < Profile.EMERALDS; i++) state.markEmeraldCollected(i);
    }

    /** Holds the camera and Sonic's level boundary to the arena (they read the camera bounds). */
    private void holdArena() {
        var arena = arena();
        var camera = services().camera();
        if (arena == null || camera == null) return;
        // Camera_Max_X_pos is the native scroll limit: the player's right boundary is it + $128.
        // Sonic 2's ordinary play lets Sonic run $40 past that, so the limit sits 384 inside the wall;
        // the engine then fills the screen beyond the wall with static, which marks it.
        short minX = (short) arena.left(), maxX = (short) Math.max(arena.left(), arena.right() - 384);
        short maxY = (short) Math.max(0, arena.floorBottom() - 224 + 72);
        short minY = (short) Math.min(maxY, Math.max(0, arena.floorTop() - 224 + 40));
        camera.setMinX(minX);
        camera.setMinXTarget(minX);
        camera.setMaxX(maxX);
        camera.setMaxXTarget(maxX);
        camera.setMinY(minY);
        camera.setMinYTarget(minY);
        camera.setMaxY(maxY);
        camera.setMaxYTarget(maxY);
    }

    /** The camera's top is a physical ceiling: upgrades must not reach terrain above the arena. */
    private void holdCeiling(AbstractPlayableSprite player) {
        if (player.getDead()) return;
        int ceiling = services().camera().getMinY() + 24;
        if (player.getCentreY() < ceiling) {
            com.openggf.sprites.NativePositionOps.writeYPosResetSubpixel(player, ceiling);
            player.setYSpeed((short) Math.max(0, player.getYSpeed()));
            player.setAir(true);
        }
    }

    private void start(AbstractPlayableSprite player) {
        started = true;
        var run = run();
        if (!run.active) {
            phase = CAMP;
            menuIndex = 0;
            player.setRingCount(0);
            return;
        }
        player.setRingCount(run.carriedRings);
        run.rerolls = run.rerollsPerStage();
        run.combo = 0;
        if (run.relic(5)) player.giveShield();
        grantEmeralds();
        phase = INTRO;
        phaseFrames = 0;
    }

    private void beginFight() {
        var arena = arena();
        phase = Stages.survivalSeconds(arena.stage()) > 0 ? FIGHT : BOSS;
        phaseFrames = 0;
        fightFrames = 0;
        stageFrames = Stages.survivalSeconds(arena.stage()) * 60;
        spawnTimer = 30;
        eliteTimer = ELITE_PERIOD;
        if (phase == BOSS) spawnBoss();
    }

    private void fight(AbstractPlayableSprite player) {
        var run = run();
        run.frames++;
        fightFrames++;
        if (phase == FIGHT) {
            int before = stageFrames / 60;
            stageFrames--;
            if (stageFrames <= 5 * 60 && stageFrames / 60 < before) services().playSfx(0xBA); // sfx_Ding countdown.
            if (stageFrames <= 0) {
                phase = BOSS;
                phaseFrames = 0;
                spawnBoss();
            }
        }
        spawnWaves();
        scatterRings(player);
        moves(player);
        combo(player);
        autoWeapons(player);
        updateProjectiles();
        updateEffects();
    }

    // =========================================================================================
    // Waves
    // =========================================================================================

    private List<Enemy> enemies() {
        var list = new ArrayList<Enemy>();
        for (var object : services().objectManager().getActiveObjects()) {
            if (object instanceof Enemy enemy && enemy.alive()) list.add(enemy);
        }
        return list;
    }

    Boss boss() {
        for (var object : services().objectManager().getActiveObjects()) {
            if (object instanceof Boss boss && !boss.isDestroyed()) return boss;
        }
        return null;
    }

    private void spawnWaves() {
        var arena = arena();
        int stage = arena.stage();
        int[] ground = Stages.ground(stage), air = Stages.air(stage);
        if (ground.length + air.length == 0) return;
        var run = run();
        int tier = Stages.tier(stage, arena.act());
        int seconds = fightFrames / 60;
        int alive = enemies().size();
        int maxAlive = Math.min(34, 7 + 2 * tier + seconds / 10) / (phase == BOSS ? 2 : 1);
        if (--eliteTimer <= 0 && phase == FIGHT) {
            eliteTimer = ELITE_PERIOD;
            spawnEnemy(ground, air, tier, true);
            banner("ELITE " + "INCOMING!", 90, Draw.ORANGE);
        }
        if (--spawnTimer > 0 || alive >= maxAlive) return;
        spawnTimer = Math.max(12, 66 - 4 * tier - seconds / 4) * (phase == BOSS ? 2 : 1);
        int batch = 1 + (seconds >= 40 ? 1 : 0) + (seconds >= 90 ? 1 : 0) + (tier >= 5 ? 1 : 0);
        // Aquatic Ruin's Whisps come in swarms.
        for (int i = 0; i < batch && alive + i < maxAlive; i++) spawnEnemy(ground, air, tier, false);
        if (run.chance(0.02 + 0.01 * tier)) spawnEnemy(ground, air, tier, false);
    }

    private void spawnEnemy(int[] ground, int[] air, int tier, boolean elite) {
        var objects = services().objectManager();
        if (!objects.hasFreeDynamicSlot()) return;
        var run = run();
        var arena = arena();
        var camera = services().camera();
        boolean flying = ground.length == 0 || air.length > 0 && run.nextInt(2) == 0;
        int species = flying ? air[run.nextInt(air.length)] : ground[run.nextInt(ground.length)];
        var t = Species.of(species);
        int side = run.nextInt(2) == 0 ? -1 : 1;
        int x = side < 0 ? camera.getX() - 24 : camera.getX() + camera.getWidth() + 24;
        x = arena.clampX(x, 24);
        var player = camera.getFocusedSprite();
        if (player != null && Math.abs(x - player.getCentreX()) < 96) {
            x = arena.clampX(player.getCentreX() - side * 160, 24);
        }
        int y = flying ? arena.floorTop() - 48 - run.nextInt(112) : arena.floorTop() - 40;
        if (flying && run.nextInt(3) == 0) {
            // Some flyers drop in from above the screen instead.
            x = arena.clampX(camera.getX() + 24 + run.nextInt(Math.max(1, camera.getWidth() - 48)), 24);
            y = camera.getY() - 24;
        }
        double progress = fightFrames / (double) Math.max(1, Stages.survivalSeconds(arena.stage()) * 60);
        double actScale = 1 + 0.2 * arena.act();
        int hp = (int) Math.round(t.hp() * (1 + 0.25 * tier) * (1 + 0.5 * Math.min(1.2, progress)) * actScale);
        if (elite) hp = hp * 6 + 10;
        var spawn = Enemy.spawnAt(x, y, species, elite);
        int hitpoints = hp;
        spawnFreeChild(() -> new Enemy(spawn, hitpoints));
        if (species == Species.WHISP && !elite) {
            for (int i = 1; i <= 2 && objects.hasFreeDynamicSlot(); i++) {
                var buddy = Enemy.spawnAt(x + side * 16 * i, y - 12 * i, species, false);
                spawnFreeChild(() -> new Enemy(buddy, hitpoints));
            }
        }
    }

    private void spawnBoss() {
        if (bossSpawned) return;
        bossSpawned = true;
        var arena = arena();
        var camera = services().camera();
        int stage = arena.stage();
        int hp = (int) Math.round(Stages.bossHp(stage, arena.act()) * (1 + 0.25 * arena.act()));
        int x = arena.clampX(camera.getX() + camera.getWidth() - 64, 64);
        int y = stage == Stages.DEZ ? arena.floorTop() - 40 : camera.getY() - 48;
        var spawn = Boss.spawnAt(x, y, stage);
        spawnFreeChild(() -> new Boss(spawn, hp));
        banner("WARNING! " + Stages.bossName(stage) + "!", 150, Draw.RED);
        services().audioManager().playMusic(stage == Stages.DEZ ? Sonic2Music.FINAL_BOSS.id : Sonic2Music.BOSS.id);
    }

    /**
     * Every ten seconds of the fight a formation of five floating rings appears somewhere in
     * the arena away from Sonic, so roaming the arena pays as well as fighting.
     */
    private void scatterRings(AbstractPlayableSprite player) {
        if (phase != FIGHT || ++ringTimer < RING_FORMATION_PERIOD) return;
        ringTimer = 0;
        var run = run();
        var arena = arena();
        int x = arena.clampX(arena.left() + 40 + run.nextInt(Math.max(1, arena.width() - 80)), 40);
        if (Math.abs(x - player.getCentreX()) < 120) x = arena.clampX(player.getCentreX() + (x < player.getCentreX() ? -200 : 200), 40);
        int y = floorBelow(x, arena) - 48 - run.nextInt(48);
        boolean arc = run.nextInt(2) == 0;
        for (int i = 0; i < 5 && services().objectManager().hasFreeDynamicSlot(); i++) {
            int rx = x + (i - 2) * 20, ry = arc ? y + Math.abs(i - 2) * 10 : y;
            spawnFreeChild(() -> Pickup.floating(rx, ry));
        }
    }

    /** The arena floor at {@code x}: the first surface from the floor's highest point down. */
    private int floorBelow(int x, Arena arena) {
        var levelManager = services().levelManager();
        for (int y = arena.floorTop() - 16; y <= arena.floorBottom() + 48; y += 16) {
            var r = com.openggf.physics.ObjectTerrainUtils.checkFloorDist(levelManager, x, y);
            if (r.foundSurface() && r.distance() >= 0 && r.distance() < 16) return y + r.distance();
        }
        return arena.floorTop();
    }

    // =========================================================================================
    // Hooks called by enemies, the boss, pickups and the guard
    // =========================================================================================

    /** Sonic rebounded off an enemy or the boss: fire every bounce weapon from there. */
    void onBounce(int x, int y) {
        var run = run();
        if (run.combo >= 2) services().playSfx(run.combo >= 10 ? 0xC9 : 0xB4); // sfx_Bonus / sfx_Bumper.
        if (run.combo > 0 && run.combo % FEVER_COMBO == 0) {
            // Fever: every tenth chained bounce turns Sonic invincible (stock stars and music).
            var player = services().camera().getFocusedSprite();
            if (player instanceof AbstractPlayableSprite sprite) {
                sprite.giveInvincibility();
                banner("FEVER! " + run.combo + " BOUNCES", 120, Draw.PINK);
            }
        }
        dashFrames = 0;
        airJumpsUsed = 0;
        pounding = false;
        double power = run.damageMultiplier() * run.comboMultiplier();
        int shock = run.level(Upgrades.SHOCKWAVE);
        if (shock > 0) {
            int radius = Upgrades.shockRadius(shock);
            effect(E_SHOCK, x, y, radius, Draw.ORANGE, 0);
            damageArea(x, y, radius, scaled(Upgrades.shockDamage(shock), power), true);
        }
        int sparks = run.level(Upgrades.SPARKS);
        if (sparks > 0) {
            int n = Upgrades.sparkCount(sparks);
            for (int i = 0; i < n; i++) {
                double a = Math.PI * 2 * i / n + frameTick * 0.1;
                projectile(P_SPARK, x, y, (int) (Math.cos(a) * 0x500), (int) (Math.sin(a) * 0x500), 36,
                        scaled(Upgrades.sparkDamage(sparks), power), 1);
            }
        }
        int zap = run.level(Upgrades.CHAIN_ZAP);
        if (zap > 0) chainZap(x, y, Upgrades.zapTargets(zap), scaled(Upgrades.zapDamage(zap), power));
        int homing = run.level(Upgrades.HOMING_RINGS);
        if (homing > 0) {
            int n = Upgrades.homingCount(homing);
            for (int i = 0; i < n; i++) {
                projectile(P_HOMING, x, y - 8, (i - (n - 1) / 2) * 0x200, -0x400, 150,
                        scaled(Upgrades.homingDamage(homing), power), 1);
            }
        }
    }

    private static int scaled(int damage, double power) {
        return Math.max(1, (int) Math.round(damage * power));
    }

    void onEnemyDefeated(Enemy enemy, int x, int y) {
        var run = run();
        run.kills++;
        stageKills++;
        int value = 1 + (run.chance(0.2 * run.level(Upgrades.GREED)) ? 1 : 0) + (run.relic(3) ? 1 : 0);
        value = (int) Math.round(value * (1 + 0.5 * (run.comboMultiplier() - 1)) * (1 + 0.5 * arena().act()));
        if (enemy.elite()) value += 8;
        dropRings(x, y, value);
        if (enemy.elite() && services().objectManager().hasFreeDynamicSlot()) {
            int[] kinds = {Pickup.MON_RINGS, Pickup.MON_SHIELD, Pickup.MON_STARS, Pickup.MON_SHOES,
                    Pickup.MON_EGGMAN, Pickup.MON_MAGNET};
            var spawn = Pickup.spawnAt(x, y - 8, Pickup.MONITOR);
            int content = kinds[run.nextInt(kinds.length)];
            spawnFreeChild(() -> new Pickup(spawn, 0, -0x300, content));
        }
    }

    /** Drops {@code value} rings' worth of pickups, merging into existing ones past the pile cap. */
    void dropRings(int x, int y, int value) {
        var run = run();
        int pieces = Math.min(value, 3);
        int live = 0;
        Pickup nearest = null;
        for (var object : services().objectManager().getActiveObjects()) {
            if (object instanceof Pickup p && !p.isDestroyed() && p.kind() == Pickup.RING && !p.lostRing() && !p.collectedAlready()) {
                live++;
                if (nearest == null || Math.abs(p.getX() - x) < Math.abs(nearest.getX() - x)) nearest = p;
            }
        }
        for (int i = 0; i < pieces; i++) {
            int share = value / pieces + (i < value % pieces ? 1 : 0);
            if (live >= 40 || !services().objectManager().hasFreeDynamicSlot()) {
                if (nearest != null) nearest.addValue(share);
                continue;
            }
            int vx = (run.nextInt(5) - 2) * 0x100, vy = -0x300 - run.nextInt(3) * 0x80;
            var spawn = Pickup.spawnAt(x, y, Pickup.RING);
            spawnFreeChild(() -> new Pickup(spawn, vx, vy, share));
            live++;
        }
    }

    /** The guard's ring toll: half the lost rings scatter for Sonic to grab back. */
    void spillRings(int x, int y, int count) {
        var run = run();
        for (int i = 0; i < count && services().objectManager().hasFreeDynamicSlot(); i++) {
            double a = Math.PI * (0.15 + 0.7 * i / Math.max(1, count - 1));
            int vx = (int) (-Math.cos(a) * 0x300), vy = (int) (-Math.sin(a) * 0x400);
            var spawn = Pickup.spawnAt(x, y - 8, Pickup.RING);
            int jitter = run.nextInt(64);
            spawnFreeChild(() -> Pickup.lost(spawn, vx + jitter, vy));
        }
    }

    void onPlayerHit(int lost) {
        hitFlash = 12;
        run().combo = 0;
        Boss boss = boss();
        if (boss != null) boss.laugh();
    }

    void onRevive() {
        banner("REVIVED!", 120, Draw.GREEN);
    }

    void onBossBeaten() {
        // Every badnik still standing is destroyed with the boss; their rings are the prize.
        for (Enemy enemy : enemies()) enemy.hurt(99999, 0);
        boolean silverSonic = arena().stage() == Stages.DEZ && !finale;
        if (!silverSonic) services().audioManager().fadeOutMusic();
        banner(silverSonic ? "SILVER SONIC DOWN!" : arena().stage() == Stages.DEZ ? "GOTCHA, EGGMAN!" : "BOSS DEFEATED!",
                150, Draw.GOLD);
    }

    void onBossDefeated(int x, int y) {
        var arena = arena();
        var profile = profile();
        if (arena.stage() == Stages.DEZ && !finale) {
            // Silver Sonic is scrap: Eggman bolts from the cockpit. Catch him to win.
            finale = true;
            var spawn = Boss.runnerAt(x, y);
            spawnFreeChild(() -> new Boss(spawn, Boss.RUNNER_HITS));
            banner("EGGMAN IS ESCAPING!", 150, Draw.RED);
            services().audioManager().playMusic(Sonic2Music.FINAL_BOSS.id);
            return;
        }
        phase = CLEAR_WAIT;
        phaseFrames = 0;
        services().audioManager().playMusic(Sonic2Music.ACT_CLEAR.id);
        int stage = arena.stage();
        if (stage < Profile.EMERALDS && !profile.hasEmerald(stage) && services().objectManager().hasFreeDynamicSlot()) {
            var spawn = Pickup.spawnAt(x, Math.min(y, arena.floorTop() - 32), Pickup.EMERALD);
            spawnFreeChild(() -> new Pickup(spawn, 0, -0x200, stage));
        } else {
            dropRings(x, y, 20);
            phaseFrames = 240;
        }
    }

    void onEmeraldCollected() {
        var run = run();
        var profile = profile();
        int stage = arena().stage();
        if (!profile.hasEmerald(stage)) {
            profile.emeralds |= 1 << stage;
            profile.save();
            run.emeraldsWon |= 1 << stage;
            emeraldNew = true;
            services().audioManager().playMusic(Sonic2Music.GOT_EMERALD.id);
        }
        banner("CHAOS EMERALD!", 150, Draw.GREEN);
        phaseFrames = Math.max(phaseFrames, 200);
    }

    private void collectEmeraldNow() {
        for (var object : services().objectManager().getActiveObjects()) {
            if (object instanceof Pickup p && p.kind() == Pickup.EMERALD && !p.isDestroyed() && !p.collectedAlready()) {
                p.setDestroyed(true);
                onEmeraldCollected();
            }
        }
        openClear();
    }

    /** The Eggman monitor: every badnik on screen takes a heavy hit. */
    void screenNuke() {
        var camera = services().camera();
        hitFlash = 20;
        for (Enemy enemy : enemies()) {
            if (enemy.getX() > camera.getX() - 16 && enemy.getX() < camera.getX() + camera.getWidth() + 16) {
                enemy.hurt(scaled(20, run().damageMultiplier()), 0);
            }
        }
        Boss boss = boss();
        if (boss != null) boss.hurt(scaled(10, run().damageMultiplier()));
    }

    /** The magnet monitor: every ring on the field flies to Sonic. */
    void magnetSweep() {
        magnetAllUntil = frameTick + 240;
    }

    int magnetAllUntil;
    /** Death Egg: Silver Sonic is down and Eggman is running. */
    boolean finale;

    void banner(String text, int frames) { banner(text, frames, Draw.GOLD); }

    void banner(String text, int frames, int colour) {
        banner = text;
        bannerFrames = frames;
        bannerColour = colour;
    }

    void popup(int x, int y, int amount, boolean big) {
        effect(E_NUMBER, x, y, amount, big ? Draw.GOLD : Draw.WHITE, 0);
    }

    // =========================================================================================
    // Moves and the combo
    // =========================================================================================

    private void moves(AbstractPlayableSprite player) {
        var run = run();
        boolean air = player.getAir();
        boolean down = player.isDownPressed();
        boolean downPressed = down && !downWasHeld;
        downWasHeld = down;
        if (!air) {
            if (pounding) quake(player);
            pounding = false;
            airFrames = 0;
            airJumpsUsed = 0;
            dashFrames = 0;
            return;
        }
        airFrames++;
        if (dashFrames > 0) dashFrames--;
        if (player.isHurt() || player.getDead()) return;
        if (pounding) {
            if (player.getYSpeed() < 0x900) player.setYSpeed((short) 0x900);
            return;
        }
        if (airFrames > 3 && player.isJumpJustPressed()) {
            if (run.has(Upgrades.HOMING_DASH) && dashFrames == 0 && homingDash(player)) return;
            if (airJumpsUsed < run.level(Upgrades.AIR_JUMP)) {
                airJumpsUsed++;
                player.setYSpeed((short) -0x600);
                ball(player);
                services().audioManager().playSfx(GameSound.JUMP);
                effect(E_PUFF, player.getCentreX(), player.getCentreY() + 12, 0, Draw.WHITE, 0);
                return;
            }
        }
        if (downPressed && airFrames > 3 && run.has(Upgrades.GROUND_POUND)) {
            pounding = true;
            player.setXSpeed((short) 0);
            player.setGSpeed((short) 0);
            player.setYSpeed((short) 0x900);
            ball(player);
            services().playSfx(0xBC); // sfx_SpindashRelease.
        }
    }

    private static void ball(AbstractPlayableSprite player) {
        player.setRolling(true);
        player.setAnimationId(2);
    }

    /** Dashes at the nearest badnik (or the boss) within range, preferring ones ahead and below. */
    private boolean homingDash(AbstractPlayableSprite player) {
        int range = Upgrades.dashRange(run().level(Upgrades.HOMING_DASH));
        int px = player.getCentreX(), py = player.getCentreY();
        int bestX = 0, bestY = 0;
        double best = Double.MAX_VALUE;
        for (Enemy enemy : enemies()) {
            double d = Math.hypot(enemy.getX() - px, enemy.getY() - py);
            if (d < range && d < best) { best = d; bestX = enemy.getX(); bestY = enemy.getY(); }
        }
        Boss boss = boss();
        if (boss != null && boss.alive()) {
            double d = Math.hypot(boss.getX() - px, boss.getY() - py);
            if (d < range && d < best) { best = d; bestX = boss.getX(); bestY = boss.getY(); }
        }
        if (best == Double.MAX_VALUE) return false;
        double length = Math.max(1, best);
        player.setXSpeed((short) ((bestX - px) / length * 0xA00));
        player.setYSpeed((short) ((bestY - py) / length * 0xA00));
        player.setGSpeed(player.getXSpeed());
        ball(player);
        dashFrames = 24;
        services().playSfx(0xBC);
        effect(E_PUFF, px, py, 0, Draw.CYAN, 0);
        return true;
    }

    private void quake(AbstractPlayableSprite player) {
        var run = run();
        int level = run.level(Upgrades.GROUND_POUND);
        int radius = Upgrades.poundRadius(level);
        int x = player.getCentreX(), y = player.getCentreY() + 16;
        effect(E_POUND, x, y, radius, Draw.YELLOW, 0);
        damageArea(x, y, radius, scaled(Upgrades.poundDamage(level), run.damageMultiplier()), false);
        services().playSfx(0xBD); // sfx_Hammer.
        hitFlash = 4;
    }

    private void combo(AbstractPlayableSprite player) {
        var run = run();
        if (player.getAir()) {
            wasGrounded = false;
            return;
        }
        if (!wasGrounded) {
            wasGrounded = true;
            comboGrace = Upgrades.comboGrace(run.level(Upgrades.COMBO_KEEPER));
        }
        if (comboGrace > 0) { comboGrace--; return; }
        if (run.combo <= 0) return;
        if (run.combo >= 5) {
            // A finished chain pays out: rings rain on Sonic.
            int bonus = run.combo * run.combo / 6;
            dropRings(player.getCentreX(), player.getCentreY() - 48, Math.min(60, bonus));
            banner(run.combo + " BOUNCE COMBO!", 90, Draw.CYAN);
        }
        run.combo = 0;
    }

    // =========================================================================================
    // Weapons
    // =========================================================================================

    private void autoWeapons(AbstractPlayableSprite player) {
        var run = run();
        double power = run.damageMultiplier();
        int px = player.getCentreX(), py = player.getCentreY();
        int orbit = run.level(Upgrades.ORBIT_RINGS);
        orbitAngle = (orbitAngle + 5 + orbit) & 0x3FF;
        if (orbit > 0) {
            int n = Upgrades.orbitCount(orbit);
            int damage = scaled(Upgrades.orbitDamage(orbit), power);
            for (int i = 0; i < n; i++) {
                double a = (orbitAngle / 1024.0 + i / (double) n) * Math.PI * 2;
                int ox = px + (int) (Math.cos(a) * 42), oy = py + (int) (Math.sin(a) * 42);
                damageArea(ox, oy, 12, damage, false);
            }
        }
        int boom = run.level(Upgrades.SONIC_BOOM);
        if (boom > 0 && ++boomTimer >= (int) (Upgrades.boomCooldown(boom) * run.cooldownScale())) {
            boomTimer = 0;
            int dir = player.getRenderHFlip() ? -1 : 1;
            projectile(P_BOOM, px + dir * 12, py, dir * 0x700, 0, 70, scaled(Upgrades.boomDamage(boom), power), 99);
            if (boom >= 4) projectile(P_BOOM, px - dir * 12, py, -dir * 0x700, 0, 70,
                    scaled(Upgrades.boomDamage(boom), power), 99);
            services().playSfx(0xAB); // sfx_Swish.
        }
        int flicky = run.level(Upgrades.FLICKIES);
        if (flicky > 0 && ++flickyTimer >= (int) (Upgrades.flickyCooldown(flicky) * run.cooldownScale())) {
            flickyTimer = 0;
            for (int i = 0; i < Upgrades.flickyCount(flicky); i++) {
                projectile(P_FLICKY, px, py - 8, (i % 2 == 0 ? -1 : 1) * 0x200, -0x300, 160,
                        scaled(3, power), 1);
            }
        }
        int barrier = run.level(Upgrades.BARRIER);
        if (barrier > 0 && !player.hasShield()) {
            if (++barrierTimer >= (int) (Upgrades.barrierCooldown(barrier) * run.cooldownScale())) {
                barrierTimer = 0;
                player.giveShield();
                services().playSfx(0xAF); // sfx_Shield.
            }
        } else {
            barrierTimer = 0;
        }
        if (frameTick < magnetAllUntil) pullAllRings(player);
    }

    private void pullAllRings(AbstractPlayableSprite player) {
        for (var object : services().objectManager().getActiveObjects()) {
            if (object instanceof Pickup p && p.kind() == Pickup.RING && !p.lostRing() && !p.collectedAlready()
                    && Math.abs(p.getX() - player.getCentreX()) < 2000) {
                p.homeIn();
            }
        }
    }

    /** Damages every badnik (and the boss) whose centre lies within {@code radius} of (x, y). */
    private void damageArea(int x, int y, int radius, int damage, boolean popShots) {
        for (Enemy enemy : enemies()) {
            int dx = enemy.getX() - x, dy = enemy.getY() - y;
            int reach = radius + enemy.bodyRadius();
            if (dx * dx + dy * dy <= reach * reach) enemy.hurt(damage, Integer.signum(dx));
        }
        Boss boss = boss();
        if (boss != null && boss.alive()) {
            int dx = boss.getX() - x, dy = boss.getY() - y, reach = radius + 24;
            if (dx * dx + dy * dy <= reach * reach) boss.hurt(damage);
        }
        if (popShots) {
            for (var object : services().objectManager().getActiveObjects()) {
                if (object instanceof Shot shot && !shot.isDestroyed()) {
                    int dx = shot.getX() - x, dy = shot.getY() - y;
                    if (dx * dx + dy * dy <= radius * radius) shot.pop();
                }
            }
        }
    }

    private void chainZap(int x, int y, int targets, int damage) {
        var hit = new ArrayList<Enemy>();
        int fromX = x, fromY = y;
        for (int n = 0; n < targets; n++) {
            Enemy next = null;
            double best = 170;
            for (Enemy enemy : enemies()) {
                if (hit.contains(enemy)) continue;
                double d = Math.hypot(enemy.getX() - fromX, enemy.getY() - fromY);
                if (d < best) { best = d; next = enemy; }
            }
            if (next == null) break;
            hit.add(next);
            effect(E_BOLT, fromX, fromY, next.getX(), Draw.CYAN, next.getY());
            fromX = next.getX();
            fromY = next.getY();
            next.hurt(damage, 0);
        }
        Boss boss = boss();
        if (hit.size() < targets && boss != null && boss.alive() && Math.hypot(boss.getX() - fromX, boss.getY() - fromY) < 170) {
            effect(E_BOLT, fromX, fromY, boss.getX(), Draw.CYAN, boss.getY());
            boss.hurt(damage);
        }
        if (!hit.isEmpty()) services().playSfx(0xB1); // sfx_Zap.
    }

    private void projectile(int kind, int x, int y, int vx, int vy, int life, int damage, int hits) {
        for (int i = 0; i < MAX_P; i++) {
            if (pKind[i] != 0) continue;
            pKind[i] = kind;
            pX[i] = x << 8;
            pY[i] = y << 8;
            pVX[i] = vx;
            pVY[i] = vy;
            pLife[i] = life;
            pDmg[i] = damage;
            pHits[i] = hits;
            return;
        }
    }

    private void updateProjectiles() {
        if (paused()) return;
        List<Enemy> enemies = null;
        Boss boss = null;
        boolean bossLooked = false;
        for (int i = 0; i < MAX_P; i++) {
            if (pKind[i] == 0) continue;
            if (--pLife[i] <= 0 || pHits[i] <= 0) { pKind[i] = 0; continue; }
            if (enemies == null) enemies = enemies();
            if (!bossLooked) { boss = boss(); bossLooked = true; }
            int x = pX[i] >> 8, y = pY[i] >> 8;
            if (pKind[i] == P_HOMING || pKind[i] == P_FLICKY) steer(i, x, y, enemies, boss);
            if (pKind[i] == P_SPARK) pVY[i] += 0x18;
            pX[i] += pVX[i];
            pY[i] += pVY[i];
            x = pX[i] >> 8;
            y = pY[i] >> 8;
            int size = pKind[i] == P_BOOM ? 14 : 8;
            for (Enemy enemy : enemies) {
                if (!enemy.alive()) continue;
                int reach = size + enemy.bodyRadius();
                if (enemy.vulnerable() && Math.abs(enemy.getX() - x) < reach && Math.abs(enemy.getY() - y) < reach) {
                    enemy.hurt(pDmg[i], Integer.signum(pVX[i]));
                    if (--pHits[i] <= 0) break;
                }
            }
            if (pHits[i] > 0 && boss != null && boss.alive()
                    && Math.abs(boss.getX() - x) < size + 24 && Math.abs(boss.getY() - y) < size + 20) {
                if (boss.hurt(pDmg[i]) || pKind[i] != P_BOOM) pHits[i]--;
            }
            if (pKind[i] == P_BOOM) {
                for (var object : services().objectManager().getActiveObjects()) {
                    if (object instanceof Shot shot && !shot.isDestroyed()
                            && Math.abs(shot.getX() - x) < 16 && Math.abs(shot.getY() - y) < 16) shot.pop();
                }
            }
            var arena = arena();
            if (x < arena.left() - 32 || x > arena.right() + 32) pKind[i] = 0;
        }
    }

    private void steer(int i, int x, int y, List<Enemy> enemies, Boss boss) {
        int tx = Integer.MIN_VALUE, ty = 0;
        double best = 260;
        for (Enemy enemy : enemies) {
            if (!enemy.alive()) continue;
            double d = Math.hypot(enemy.getX() - x, enemy.getY() - y);
            if (d < best) { best = d; tx = enemy.getX(); ty = enemy.getY(); }
        }
        if (boss != null && boss.alive() && Math.hypot(boss.getX() - x, boss.getY() - y) < best) {
            tx = boss.getX();
            ty = boss.getY();
        }
        if (tx == Integer.MIN_VALUE) { pVY[i] += 0x10; return; }
        double length = Math.max(1, Math.hypot(tx - x, ty - y));
        int speed = pKind[i] == P_FLICKY ? 0x480 : 0x5C0;
        pVX[i] += (int) ((tx - x) / length * speed - pVX[i]) / 6;
        pVY[i] += (int) ((ty - y) / length * speed - pVY[i]) / 6;
    }

    void effect(int kind, int x, int y, int a, int colour, int c) {
        int slot = 0, oldest = -1;
        for (int i = 0; i < MAX_E; i++) {
            if (eKind[i] == 0) { slot = i; oldest = Integer.MAX_VALUE; break; }
            if (eAge[i] > oldest) { oldest = eAge[i]; slot = i; }
        }
        eKind[slot] = kind;
        eX[slot] = x;
        eY[slot] = y;
        eA[slot] = a;
        eB[slot] = colour;
        eC[slot] = c;
        eD[slot] = frameTick;
        eAge[slot] = 0;
    }

    private void updateEffects() {
        for (int i = 0; i < MAX_E; i++) {
            if (eKind[i] == 0) continue;
            int life = switch (eKind[i]) {
                case E_NUMBER -> 40;
                case E_BOLT -> 10;
                case E_PUFF -> 16;
                default -> 16;
            };
            if (++eAge[i] >= life) eKind[i] = 0;
        }
    }

    // =========================================================================================
    // Menus
    // =========================================================================================

    /** Edge-detected up/down (returns -1, 0 or 1) and Enter/Start confirm. */
    private int menuMove(AbstractPlayableSprite player) {
        boolean up = player.isUpPressed(), down = player.isDownPressed();
        int move = (up && !upHeld ? -1 : 0) + (down && !downHeld ? 1 : 0);
        upHeld = up;
        downHeld = down;
        return move;
    }

    private boolean menuConfirm(AbstractPlayableSprite player) {
        return menuConfirmAny(player) && phaseFrames > 20;
    }

    private void moveCursor(int move, int count) {
        if (move == 0 || count <= 0) return;
        menuIndex = Math.floorMod(menuIndex + move, count);
        services().playSfx(0xCD); // sfx_Blip.
    }

    // ---- Camp: shop, then start ----
    static final int CAMP_START = 0, CAMP_TITLE = Profile.SHOP_COUNT + 1;

    private void campMenu(AbstractPlayableSprite player) {
        moveCursor(menuMove(player), Profile.SHOP_COUNT + 2);
        if (!menuConfirm(player)) return;
        if (menuIndex == CAMP_START) { beginRun(player); return; }
        if (menuIndex == CAMP_TITLE) { exitToTitle(); return; }
        int item = menuIndex - 1;
        var profile = profile();
        if (profile.buy(item)) {
            services().playSfx(0xC0); // sfx_CasinoBonus.
            banner(Profile.shopName(item) + " " + profile.shop[item], 60, Draw.GREEN);
        } else {
            services().playSfx(0xED); // sfx_Error.
        }
    }

    private void beginRun(AbstractPlayableSprite player) {
        var run = run();
        var arena = arena();
        var profile = profile();
        run.begin(arena.stage(), profile, SurvivorsMod.seed());
        profile.runs++;
        profile.save();
        player.setRingCount(run.carriedRings);
        if (run.relic(5)) player.giveShield();
        grantEmeralds();
        bankedThisRun = 0;
        phase = INTRO;
        phaseFrames = 0;
        services().playSfx(0xA1); // sfx_Checkpoint.
    }

    // ---- Level up ----
    private void openLevelUp() {
        overlay = OVERLAY_LEVEL_UP;
        menuIndex = 0;
        jumpHeld = true;
        dealCards();
        services().playSfx(0xA1);
    }

    private void dealCards() {
        var run = run();
        var options = new ArrayList<Integer>();
        var weights = new ArrayList<Integer>();
        for (int id = 0; id < Upgrades.COUNT; id++) {
            if (run.levels[id] >= Upgrades.maxLevel(id)) continue;
            options.add(id);
            // Owned upgrades come up more often, so builds can deepen.
            weights.add(run.levels[id] > 0 ? 3 : 2);
        }
        cardCount = 0;
        int want = run.cardCount();
        while (cardCount < want && !options.isEmpty()) {
            int total = weights.stream().mapToInt(Integer::intValue).sum();
            int roll = run.nextInt(total);
            int pick = 0;
            while (roll >= weights.get(pick)) roll -= weights.get(pick++);
            cards[cardCount++] = options.remove(pick);
            weights.remove(pick);
        }
        // With everything maxed the only card left is a ring bonus (-1).
        if (cardCount == 0) cards[cardCount++] = -1;
        // Until Sonic owns a weapon, the first card is always one: stomps alone do not scale.
        boolean armed = false;
        for (int id = 0; id <= Upgrades.FLICKIES; id++) armed |= run.levels[id] > 0;
        boolean offered = false;
        for (int i = 0; i < cardCount; i++) offered |= cards[i] >= 0 && cards[i] <= Upgrades.FLICKIES;
        if (!armed && !offered && cards[0] >= 0) cards[0] = run.nextInt(Upgrades.FLICKIES + 1);
    }

    /** Level-up menu rows: the cards, then REROLL when rerolls remain. */
    int levelUpRows() { return cardCount + (run().rerolls > 0 && cards[0] >= 0 ? 1 : 0); }

    private void levelUpMenu(AbstractPlayableSprite player) {
        var run = run();
        if (run.pendingLevels <= 0) { overlay = OVERLAY_NONE; return; }
        moveCursor(menuMove(player), levelUpRows());
        if (!menuConfirmAny(player)) return;
        if (menuIndex >= cardCount) {
            run.rerolls--;
            dealCards();
            menuIndex = Math.min(menuIndex, levelUpRows() - 1);
            services().playSfx(0xC6);
            return;
        }
        int card = cards[menuIndex];
        if (card < 0) {
            player.addRings(25);
        } else {
            run.levels[card]++;
            if (card == Upgrades.BARRIER && run.levels[card] == 1) player.giveShield();
        }
        run.pendingLevels--;
        services().playSfx(0xC9); // sfx_Bonus.
        banner(card < 0 ? "+25 RINGS" : Upgrades.name(card) + " LV " + run.levels[card], 70,
                card < 0 ? Draw.GOLD : Upgrades.kindColour(card));
        if (run.pendingLevels > 0) {
            menuIndex = 0;
            dealCards();
        } else {
            overlay = OVERLAY_NONE;
        }
    }

    /** Menu confirm that ignores the phase timer (overlays open mid-phase). */
    private boolean menuConfirmAny(AbstractPlayableSprite player) {
        var input = services().gameService(com.openggf.control.InputHandler.class);
        return input != null && (input.isKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER)
                || input.isKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER)
                || input.logical().menuStart());
    }

    // ---- Clear: results and the route ----
    private void openClear() {
        var run = run();
        var arena = arena();
        run.stagesCleared++;
        run.carriedRings = 0;
        var player = services().camera().getFocusedSprite();
        if (player != null) run.carriedRings = player.getRingCount();
        var profile = profile();
        int next = arena.stage() + 1;
        if (next < Stages.COUNT && next > profile.unlocked) {
            profile.unlocked = next;
            profile.save();
        }
        if (arena.stage() == Stages.DEZ) {
            victory();
            return;
        }
        phase = CLEAR;
        phaseFrames = 0;
        menuIndex = 0;
        jumpHeld = true;
        clearChoices = Stages.acts(next) + 1;
    }

    private void clearMenu(AbstractPlayableSprite player) {
        moveCursor(menuMove(player), clearChoices);
        if (!menuConfirm(player)) return;
        var run = run();
        if (menuIndex == clearChoices - 1) { retire(false); return; }
        run.stage = arena().stage() + 1;
        run.act = menuIndex;
        run.paused = false;
        services().levelManager().requestZoneAndAct(Stages.zone(run.stage), run.act, true);
    }

    // ---- Run end ----
    private int bank(boolean won, boolean retired) {
        var run = run();
        var profile = profile();
        var player = services().camera().getFocusedSprite();
        int held = retired && player != null ? player.getRingCount() : 0;
        int banked = run.ringsCollected / 2 + 25 * run.stagesCleared + run.kills / 5 + held + (won ? 300 : 0);
        profile.bank += banked;
        profile.bankedTotal += banked;
        profile.totalKills += run.kills;
        profile.bestKills = Math.max(profile.bestKills, run.kills);
        profile.bestCombo = Math.max(profile.bestCombo, run.bestCombo);
        profile.bestStages = Math.max(profile.bestStages, run.stagesCleared);
        if (won) profile.wins++;
        profile.save();
        bankedThisRun = banked;
        run.end();
        return banked;
    }

    private void die() {
        if (run().active) bank(false, false);
        phase = DEAD;
        phaseFrames = 0;
        menuIndex = 0;
        overlay = OVERLAY_NONE;
        jumpHeld = true;
    }

    private void victory() {
        bank(true, false);
        phase = VICTORY;
        phaseFrames = 0;
        menuIndex = 0;
        jumpHeld = true;
        services().audioManager().playMusic(Sonic2Music.ENDING.id);
    }

    /** Ends the run voluntarily (CLEAR's retire, or Escape), banking held rings too. */
    private void retire(boolean toTitle) {
        if (run().active) bank(false, true);
        if (toTitle || phase == CAMP) { exitToTitle(); return; }
        phase = DEAD;
        phaseFrames = 0;
        menuIndex = 0;
        jumpHeld = true;
    }

    static final int END_AGAIN = 0, END_TITLE = 1;

    private void deadMenu(AbstractPlayableSprite player) {
        if (phaseFrames < 60) return;
        moveCursor(menuMove(player), 2);
        if (!menuConfirm(player)) return;
        if (menuIndex == END_TITLE) { exitToTitle(); return; }
        // A fresh run from the same start zone: its act 1 opens in camp.
        var run = run();
        run.paused = false;
        services().levelManager().requestZoneAndAct(Stages.zone(run.startStage), 0, true);
    }

    private void victoryMenu(AbstractPlayableSprite player) {
        deadMenu(player);
    }

    private boolean exiting;

    private void exitToTitle() {
        if (exiting) return;
        exiting = true;
        run().paused = false;
        services().levelManager().requestGameOverExit(com.openggf.game.GameOverExit.TITLE_SCREEN);
    }

    private boolean escapePressed() {
        var input = services().gameService(com.openggf.control.InputHandler.class);
        return input != null && (input.isKeyPressed(KEY_ESCAPE) || input.isGamepadBackButtonPressed());
    }

    private void setLives(int lives) {
        var state = services().gameState();
        while (state.getLives() > lives) state.loseLife();
        while (state.getLives() < lives) state.addLife();
    }

    /** While a menu is up Sonic holds still: speeds are saved and restored around the pause. */
    private void freezePlayer(AbstractPlayableSprite player, boolean freeze) {
        if (freeze == playerFrozen) {
            if (freeze) {
                player.setXSpeed((short) 0);
                player.setYSpeed((short) 0);
                player.setGSpeed((short) 0);
            }
            return;
        }
        playerFrozen = freeze;
        if (freeze) {
            savedX = player.getXSpeed();
            savedY = player.getYSpeed();
            savedG = player.getGSpeed();
            player.setObjectControlled(true);
            player.setXSpeed((short) 0);
            player.setYSpeed((short) 0);
            player.setGSpeed((short) 0);
        } else {
            player.setObjectControlled(false);
            player.setXSpeed((short) savedX);
            player.setYSpeed((short) savedY);
            player.setGSpeed((short) savedG);
        }
    }

    // =========================================================================================
    // Drawing
    // =========================================================================================

    @Override public void appendRenderCommands(List<GLCommand> commands) {
        Hud.drawWorld(services(), this);
        Hud.draw(services(), this);
    }
}
