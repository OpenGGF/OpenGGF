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
 * the arena, runs the stage's phases (camp shop before a run, the intro, timed or unlimited waves,
 * the boss, the clear screen and route choice, game over and victory), spawns the badnik waves,
 * owns Sonic's weapons and moves, the bounce combo and the level-up cards, and draws the HUD.
 * Weapon projectiles use growable primitive arrays; cosmetic effects use a fixed pool.
 * The ordinary mod-object rewind capture restores both with the controller.
 */
public final class Stage extends AbstractObjectInstance implements RewindRecreatable {
    static final int CAMP = 0, INTRO = 1, FIGHT = 2, BOSS = 3, CLEAR_WAIT = 4, CLEAR = 5, DEAD = 6, VICTORY = 7;
    static final int OVERLAY_NONE = 0, OVERLAY_LEVEL_UP = 1, OVERLAY_CHEST = 2;
    static final int INTRO_FRAMES = 150;
    static final int ELITE_PERIOD = 60 * 30;
    // Fever: six seconds of invincibility, then eighteen of recovery (a quarter of the time at best).
    static final int FEVER_DURATION = 6 * 60, FEVER_RECOVERY = 18 * 60;
    /** Live badniks beyond this stop ordinary spawns; the overflow summons elites instead. */
    static final int SOFT_CAP = 160;
    static final int OVERFLOW_PER_ELITE = 12;
    // Mid-stage beats: a zone event and a warden squad, each every 150 seconds of survival.
    static final int EVENT_FIRST = 105, WARDEN_FIRST = 180, BEAT_PERIOD = 150, EVENT_FRAMES = 10 * 60;
    static final int CHEST_ITEMS = 8;
    // Tails's hover: frames of slowed descent per jump.
    static final int HOVER_FRAMES = 75;
    int feverCharge, feverFrames, feverCooldown;
    int encounter = -1;
    // One encounter lasts 30 seconds: 18 assault, 7 surge, 5 recovery.
    static final int ENCOUNTER_FRAMES = 30 * 60;
    static final int RING_FORMATION_PERIOD = 480;
    // GLFW_KEY_ESCAPE: a tap leaves the arena for the title (banking the run).
    private static final int KEY_ESCAPE = 256;

    // Player projectile pool.
    static final int INITIAL_PROJECTILES = 48;
    static final int P_SPARK = 1, P_HOMING = 2, P_BOOM = 3, P_FLICKY = 4, P_LANCE = 5;
    // Effect pool.
    static final int MAX_E = 64;
    static final int E_NUMBER = 1, E_SHOCK = 2, E_POUND = 3, E_BOLT = 4, E_PUFF = 5, E_WARN = 6;

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
    int ringMergeFrames;
    // Menus.
    int menuIndex;
    boolean upHeld, downHeld, jumpHeld;
    int[] cards = new int[4];
    int cardCount;
    // Camp sub-pages: Eggman's Rules and the records.
    static final int PAGE_CAMP = 0, PAGE_RULES = 1, PAGE_RECORDS = 2, PAGE_SHOP = 3;
    static final int RECORD_TABS = 4;
    int campPage, recordsTab;
    boolean leftHeld, rightHeld;
    // An opened chest: what it gave. Item codes: upgrade id, 100 + evolution, -1 rings.
    final int[] chestItems = new int[CHEST_ITEMS];
    /** The rank each upgrade prize reached, for the chest screen. */
    final int[] chestLevels = new int[CHEST_ITEMS];
    int chestCount, chestRings;
    /** Chests owed by a warden squad: its first fallen elite drops one. */
    int chestOwed;
    // The mid-stage beats.
    int eventFrames, eventsStarted, wardensStarted, eventTimer;
    int overflow;
    int hoverFrames;
    /** Unlocks earned by the run just banked or the zone just cleared, for the results screens. */
    int newUnlocks;
    // Balance telemetry (hitpoints spawned and damage dealt this arena), read by the balance probe.
    long statHpSpawned, statDamage;
    // Frozen player state while a menu pauses play.
    boolean playerFrozen;
    int savedX, savedY, savedG, savedInvincible, savedInvulnerable;
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

    int[] pX = new int[INITIAL_PROJECTILES], pY = new int[INITIAL_PROJECTILES], pVX = new int[INITIAL_PROJECTILES], pVY = new int[INITIAL_PROJECTILES],
            pLife = new int[INITIAL_PROJECTILES], pKind = new int[INITIAL_PROJECTILES], pDmg = new int[INITIAL_PROJECTILES], pHits = new int[INITIAL_PROJECTILES];
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

    /**
     * A full menu (camp, cards, chest, results) is on screen. ROM rings use the ring renderer,
     * which draws after the sprite list and so over the menu panels; they hide while it shows.
     */
    boolean menuShowing() {
        return overlay != OVERLAY_NONE || phase == CAMP || phase == CLEAR || phase == DEAD || phase == VICTORY;
    }

    static boolean menuShowing(ObjectServices services) {
        Stage stage = find(services);
        return stage != null && stage.menuShowing();
    }

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
        run.pressure = phase == FIGHT || phase == BOSS ? pressureSeconds() : run.pressure;
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
                if (overlay == OVERLAY_CHEST) { chestMenu(player); break; }
                if (overlay != OVERLAY_NONE) { levelUpMenu(player); break; }
                fight(player);
            }
            case CLEAR_WAIT -> {
                if (overlay == OVERLAY_CHEST) { chestMenu(player); break; }
                updateProjectiles();
                updateEffects();
                if (phaseFrames > 330) collectEmeraldNow();
            }
            case CLEAR -> clearMenu(player);
            case DEAD -> deadMenu(player);
            default -> victoryMenu(player);
        }
        if (!run.paused && !player.getDead() && ++ringMergeFrames >= RingClusters.PERIOD) {
            ringMergeFrames = 0;
            services().gameService(RingClusters.class).merge(services());
        }
    }

    /** The stock level timer is held at zero during play; arena phases own the countdown. */
    private void holdTimer() {
        var level = services().levelGamestate();
        if (phase == FIGHT || phase == BOSS) {
            if (level.isTimerPaused()) level.resumeTimer();
            level.setTimerFrames(0);
        } else {
            level.pauseTimer();
        }
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
        run.pressure = 0;
        if (run.relic(5)) player.giveShield();
        phase = INTRO;
        phaseFrames = 0;
    }

    /** Unlimited mode: every two minutes survived counts as one more zone of the route. */
    static final int UNLIMITED_TIER_SECONDS = 120;

    /** The difficulty tier now: the zone and act, plus unlimited mode's climb. */
    int tier() {
        var arena = arena();
        return Stages.tier(arena.stage(), arena.act()) + (endless() ? fightFrames / (UNLIMITED_TIER_SECONDS * 60) : 0);
    }

    /** Rewards stay at the zone's own tier: unlimited's climb is pure pressure, so it ends. */
    int rewardTier() {
        var arena = arena();
        return Stages.tier(arena.stage(), arena.act());
    }

    int survivalSeconds() { return Stages.survivalSeconds(arena().stage(), run().mode); }

    boolean endless() { return survivalSeconds() < 0; }

    private void beginFight() {
        phase = survivalSeconds() != 0 ? FIGHT : BOSS;
        phaseFrames = 0;
        fightFrames = 0;
        stageFrames = Math.max(0, survivalSeconds()) * 60;
        spawnTimer = 30;
        eliteTimer = ELITE_PERIOD;
        if (phase == BOSS) spawnBoss();
    }

    private void fight(AbstractPlayableSprite player) {
        var run = run();
        run.frames++;
        fightFrames++;
        if (feverFrames > 0) feverFrames--;
        else if (feverCooldown > 0) feverCooldown--;
        if (phase == FIGHT && !endless()) {
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

    /** Pressure keeps rising in endless; long mode adds encounters rather than stretching downtime. */
    int pressureSeconds() {
        return (int) (fightFrames / 60L * (run().mode == RunState.LONG ? 3 : 4) / 4);
    }

    int encounterBeat() { return fightFrames % ENCOUNTER_FRAMES / 60; }
    int encounterNumber() { return fightFrames / ENCOUNTER_FRAMES + 1; }
    String encounterName() {
        if (encounterBeat() >= 25) return "REGROUP";
        if (encounterBeat() >= 18) return "SURGE";
        return switch ((encounterNumber() - 1) % 3) {
            case 0 -> "CROSSFIRE";
            case 1 -> "AIR RAID";
            default -> "STAMPEDE";
        };
    }

    private void spawnWaves() {
        var arena = arena();
        int stage = arena.stage();
        int[] ground = Stages.ground(stage), air = Stages.air(stage);
        if (ground.length + air.length == 0) return;
        var run = run();
        int tier = tier();
        int seconds = pressureSeconds();
        int beat = encounterBeat();
        int alive = enemies().size();
        if (phase == FIGHT) {
            int section = fightFrames / ENCOUNTER_FRAMES * 3 + (beat >= 25 ? 2 : beat >= 18 ? 1 : 0);
            if (section != encounter) {
                encounter = section;
                banner(encounterName() + " - WAVE " + encounterNumber(), 100,
                        beat >= 25 ? Draw.CYAN : beat >= 18 ? Draw.ORANGE : Draw.GOLD);
            }
            if (fightFrames % ENCOUNTER_FRAMES == 15 * 60) {
                banner("SURGE IN 3...", 120, Draw.ORANGE);
                services().playSfx(0xBA);
            }
            if (--eliteTimer <= 0) {
                eliteTimer = Difficulty.elitePeriod(tier, seconds) / (run.rule(Rules.ELITE_HORDE) ? 2 : 1);
                spawnEnemy(ground, air, tier, true);
                banner("ELITE INCOMING!", 90, Draw.ORANGE);
            }
            stageBeats(ground, air, tier);
            if (endless() && fightFrames > 0 && fightFrames % (UNLIMITED_TIER_SECONDS * 60) == 0) {
                banner("THE HORDE GROWS STRONGER", 150, Draw.RED);
                services().playSfx(0xBA);
            }
        }
        // Recovery pauses reinforcements, never removes surviving enemies or rewards.
        if (phase == FIGHT && beat >= 25) return;
        if (--spawnTimer > 0) return;
        boolean surge = phase == FIGHT && beat >= 18;
        // Boss fights keep thinner reinforcements so weapons can find the boss.
        spawnTimer = Difficulty.spawnInterval(tier, seconds) * (phase == BOSS ? 3 : 1) * (eventFrames > 0 ? 2 : 1);
        if (surge) spawnTimer = Math.max(8, spawnTimer * 2 / 3);
        int batch = Math.max(1, Difficulty.batch(tier, seconds) + (surge ? 1 : 0) - (phase == BOSS ? 1 : 0));
        if (alive >= SOFT_CAP) {
            // A horde the build cannot thin no longer grows in bodies: it sends champions.
            overflow += batch;
            if (overflow >= OVERFLOW_PER_ELITE) {
                overflow -= OVERFLOW_PER_ELITE;
                spawnEnemy(ground, air, tier, true);
            }
            return;
        }
        int pattern = (encounterNumber() - 1) % 3;
        if (phase == FIGHT && !surge) {
            if (pattern == 1 && air.length > 0) ground = new int[0];
            if (pattern == 2 && ground.length > 0) air = new int[0];
        }
        for (int i = 0; i < batch; i++) spawnEnemy(ground, air, tier, false);
    }

    /** Mid-stage beats on the survival clock: the zone's event, then a warden squad. */
    private void stageBeats(int[] ground, int[] air, int tier) {
        int arenaStage = arena().stage();
        int elapsed = fightFrames / 60;
        boolean secondTick = fightFrames % 60 == 0;
        if (secondTick && Stages.eventKind(arenaStage) != Stages.EVENT_NONE
                && elapsed >= EVENT_FIRST + BEAT_PERIOD * eventsStarted && (endless() || stageFrames > EVENT_FRAMES)) {
            eventsStarted++;
            eventFrames = EVENT_FRAMES;
            eventTimer = 0;
            banner(Stages.eventName(arenaStage) + "!", 150,
                    Stages.eventKind(arenaStage) == Stages.EVENT_JACKPOT ? Draw.GOLD : Draw.RED);
            services().playSfx(Stages.eventKind(arenaStage) == Stages.EVENT_JACKPOT ? 0xC0 : 0xBA);
        }
        if (secondTick && elapsed >= WARDEN_FIRST + BEAT_PERIOD * wardensStarted && (endless() || stageFrames > 20 * 60)) {
            wardensStarted++;
            for (int i = 0; i < 3; i++) spawnEnemy(ground, air, tier, true);
            chestOwed++;
            banner("WARDEN SQUAD! A CHEST AWAITS", 150, Draw.ORANGE);
            services().playSfx(0xBA);
        }
        if (eventFrames > 0) {
            eventFrames--;
            zoneEvent(arenaStage, tier);
        }
    }

    private void zoneEvent(int arenaStage, int tier) {
        var run = run();
        var camera = services().camera();
        var player = camera.getFocusedSprite();
        if (player == null) return;
        var arena = arena();
        eventTimer++;
        switch (Stages.eventKind(arenaStage)) {
            case Stages.EVENT_RAIN -> {
                // Telegraphed falling hazards: a red marker on the floor shows where each lands.
                if (eventTimer % Math.max(6, 11 - tier / 2) != 0) return;
                int x = arena.clampX(player.getCentreX() - 170 + run.nextInt(341), 24);
                int floor = floorBelow(x, arena);
                effect(E_WARN, x, floor - 2, 0, Draw.RED, 0);
                String key = Stages.rainKey(arenaStage);
                int frame = Stages.rainFrame(arenaStage), top = camera.getY() - 8;
                ArenaObjects.spawn(services(), () -> Shot.of(x, top, key, frame, true, 0, 0x100));
            }
            case Stages.EVENT_SWARM -> {
                if (eventTimer % 120 != 1) return;
                int species = Stages.swarmSpecies(arenaStage);
                int side = run.nextInt(2) == 0 ? -1 : 1;
                int x = arena.clampX(side < 0 ? camera.getX() - 24 : camera.getX() + camera.getWidth() + 24, 24);
                int y = arena.floorTop() - 64 - run.nextInt(64);
                for (int i = 0; i < 6; i++) spawnSpecies(species, x + side * 18 * i, y - (i % 2) * 20, tier, false);
            }
            case Stages.EVENT_JACKPOT -> {
                // Casino Night pays out: ring formations shower around Sonic.
                if (eventTimer % 45 != 1) return;
                int x = arena.clampX(player.getCentreX() - 120 + run.nextInt(241), 40);
                int y = floorBelow(x, arena) - 40 - run.nextInt(40);
                int value = Math.max(1, (int) Difficulty.dropValue(rewardTier()));
                for (int i = 0; i < 5; i++) {
                    int rx = x + (i - 2) * 18, ry = y - Math.abs(i - 2) * 8;
                    ArenaObjects.spawn(services(), () -> Pickup.floating(rx, ry, value));
                }
                services().playSfx(0xC0);
            }
            default -> { }
        }
    }

    private void spawnEnemy(int[] ground, int[] air, int tier, boolean elite) {
        var run = run();
        boolean flying = ground.length == 0 || air.length > 0 && run.nextInt(2) == 0;
        int species = flying ? air[run.nextInt(air.length)] : ground[run.nextInt(ground.length)];
        var camera = services().camera();
        var arena = arena();
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
        spawnSpecies(species, x, y, tier, elite);
        if (species == Species.WHISP && !elite) {
            // Whisps travel in threes.
            for (int i = 1; i <= 2; i++) spawnSpecies(species, x + side * 16 * i, y - 12 * i, tier, false);
        }
    }

    /** Spawns one badnik with the arena's current hitpoints; returns them. */
    private int spawnSpecies(int species, int x, int y, int tier, boolean elite) {
        var t = Species.of(species);
        double scale = Difficulty.hpScale(tier, arena().act(), pressureSeconds())
                * (run().rule(Rules.TOUGH_HIDES) ? 1.5 : 1);
        int hp = (int) Math.max(1, Math.round(t.hp() * scale));
        if (elite) hp = hp * 6 + 10;
        var spawn = Enemy.spawnAt(x, y, species, elite);
        int hitpoints = hp;
        statHpSpawned += hp;
        ArenaObjects.spawn(services(), () -> new Enemy(spawn, hitpoints));
        return hp;
    }

    private void spawnBoss() {
        if (bossSpawned) return;
        bossSpawned = true;
        var arena = arena();
        var camera = services().camera();
        int stage = arena.stage();
        int hp = (int) Math.round(Difficulty.bossHp(stage, arena.act(), pressureSeconds(), run().mode)
                * (run().rule(Rules.TOUGH_HIDES) ? 1.5 : 1));
        int x = arena.clampX(camera.getX() + camera.getWidth() - 64, 64);
        int y = stage == Stages.DEZ ? arena.floorTop() - 40 : camera.getY() - 48;
        var spawn = Boss.spawnAt(x, y, stage);
        statHpSpawned += hp;
        ArenaObjects.spawn(services(), () -> new Boss(spawn, hp));
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
        int value = Math.max(1, (int) Difficulty.dropValue(rewardTier()));
        for (int i = 0; i < 5; i++) {
            int rx = x + (i - 2) * 20, ry = arc ? y + Math.abs(i - 2) * 10 : y;
            ArenaObjects.spawn(services(), () -> Pickup.floating(rx, ry, value));
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
        var leader = services().camera().getFocusedSprite();
        if (feverFrames == 0 && feverCooldown == 0 && !run.rule(Rules.NO_FEVER)
                && leader instanceof AbstractPlayableSprite sprite
                && sprite.getInvincibleFrames() == 0 && !sprite.isSuperSonic()) {
            if (++feverCharge >= run.feverCharge()) {
                feverCharge = 0;
                feverFrames = FEVER_DURATION;
                feverCooldown = FEVER_RECOVERY;
                sprite.giveInvincibility();
                sprite.setInvincibleFrames(FEVER_DURATION);
                banner("FEVER! 6 SECONDS", 120, Draw.PINK);
            }
        }
        double power = run.damageMultiplier() * run.comboMultiplier();
        if (dashFrames > 0 && run.evolvedWeapon(Upgrades.HOMING_DASH)) {
            // Light Speed Dash: the dash lands as a blast.
            effect(E_SHOCK, x, y, areaRadius(56), Draw.CYAN, 0);
            damageArea(x, y, 56, scaled(10 + 2 * run.level(Upgrades.HOMING_DASH), power), true);
        }
        dashFrames = 0;
        airJumpsUsed = 0;
        hoverFrames = 0;
        pounding = false;
        int lance = run.level(Upgrades.TWIN_LANCE);
        if (lance > 0) {
            boolean cross = run.evolvedWeapon(Upgrades.TWIN_LANCE);
            int damage = scaled((4 + 2 * lance) * (cross ? 2 : 1), power), pierce = 2 + lance + (cross ? 4 : 0);
            for (int dir : new int[]{-1, 1}) {
                projectile(P_BOOM, x, y, dir * 0x700, 0, 55, damage, pierce);
                if (cross) projectile(P_LANCE, x, y, 0, dir * 0x700, 40, damage, pierce);
            }
        }
        int shock = run.level(Upgrades.SHOCKWAVE);
        if (shock > 0) {
            boolean grand = run.evolvedWeapon(Upgrades.SHOCKWAVE);
            int radius = Upgrades.shockRadius(shock) * (grand ? 3 : 2) / 2;
            effect(E_SHOCK, x, y, areaRadius(radius), grand ? Draw.GOLD : Draw.ORANGE, 0);
            damageArea(x, y, radius, scaled(Upgrades.shockDamage(shock) * (grand ? 2.5 : 1), power), true);
        }
        int sparks = run.level(Upgrades.SPARKS);
        if (sparks > 0) {
            boolean nova = run.evolvedWeapon(Upgrades.SPARKS);
            int n = Upgrades.sparkCount(sparks) * (nova ? 2 : 1);
            for (int i = 0; i < n; i++) {
                double a = Math.PI * 2 * i / n + frameTick * 0.1;
                projectile(P_SPARK, x, y, (int) (Math.cos(a) * 0x500), (int) (Math.sin(a) * 0x500), 36,
                        scaled(Upgrades.sparkDamage(sparks) * (nova ? 2 : 1), power), nova ? 3 : 1);
            }
        }
        int zap = run.level(Upgrades.CHAIN_ZAP);
        if (zap > 0) {
            boolean lattice = run.evolvedWeapon(Upgrades.CHAIN_ZAP);
            chainZap(x, y, Upgrades.zapTargets(zap) + (lattice ? 6 : 0),
                    scaled(Upgrades.zapDamage(zap) * (lattice ? 2.5 : 1), power), lattice ? 240 : 170);
        }
        int homing = run.level(Upgrades.HOMING_RINGS);
        if (homing > 0) {
            boolean tempest = run.evolvedWeapon(Upgrades.HOMING_RINGS);
            int n = tempest ? 6 : Upgrades.homingCount(homing);
            for (int i = 0; i < n; i++) {
                projectile(P_HOMING, x, y - 8, (i - (n - 1) / 2) * 0x200, -0x400, 150,
                        scaled(Upgrades.homingDamage(homing) * (tempest ? 2 : 1), power), tempest ? 3 : 1);
            }
        }
    }

    private static int scaled(double damage, double power) {
        return Math.max(1, (int) Math.round(damage * power));
    }

    /** Elites drop a monitor; every fourth elite (and a warden squad's first) a chest instead. */
    static final int ELITES_PER_CHEST = 4;

    void onEnemyDefeated(Enemy enemy, int x, int y) {
        var run = run();
        run.kills++;
        stageKills++;
        run.speciesKills[Math.floorMod(enemy.traits().id(), Species.COUNT)]++;
        // Badniks are worth more further along the route, so rewards keep pace with the toll.
        double base = Difficulty.dropValue(rewardTier());
        double rings = base + (run.chance(0.2 * run.level(Upgrades.GREED)) ? 1 : 0) + (run.relic(3) ? 1 : 0);
        rings *= (1 + 0.1 * (run.comboMultiplier() - 1)) * (1 + 0.5 * arena().act());
        // Whisps come in threes: each is a third of a badnik's reward.
        if (enemy.traits().id() == Species.WHISP && !enemy.elite()) rings /= 3;
        int value = (int) rings;
        if (run.chance(rings - value)) value++;
        if (enemy.elite()) value += (int) Math.round(8 * base);
        dropRings(x, y, value);
        if (enemy.elite() && !sweeping) {
            run.elitesDefeated++;
            if (chestOwed > 0 || run.elitesDefeated % ELITES_PER_CHEST == 0) {
                if (chestOwed > 0) chestOwed--;
                dropChest(x, y - 8, 1);
                return;
            }
            int[] kinds = {Pickup.MON_RINGS, Pickup.MON_SHIELD, Pickup.MON_STARS, Pickup.MON_SHOES,
                    Pickup.MON_EGGMAN, Pickup.MON_MAGNET};
            var spawn = Pickup.spawnAt(x, y - 8, Pickup.MONITOR);
            int content = kinds[run.nextInt(kinds.length)];
            ArenaObjects.spawn(services(), () -> new Pickup(spawn, 0, -0x300, content));
        }
    }

    boolean sweeping;

    void dropChest(int x, int y, int size) {
        var spawn = Pickup.spawnAt(x, y, Pickup.CHEST);
        ArenaObjects.spawn(services(), () -> new Pickup(spawn, 0, -0x300, size));
    }

    // =========================================================================================
    // Chests
    // =========================================================================================

    /**
     * Opens a treasure chest of {@code size} prizes: each evolves a ready weapon first, otherwise
     * ranks up an owned upgrade (new ones only once no owned upgrade can grow), otherwise pays
     * rings. Prizes apply at once; the overlay shows what the chest held.
     */
    void openChest(int size) {
        var run = run();
        var player = services().camera().getFocusedSprite();
        run.chestsOpened++;
        chestCount = 0;
        chestRings = 0;
        for (int i = 0; i < Math.min(size, CHEST_ITEMS); i++) {
            int prize = chestPrize();
            chestItems[chestCount++] = prize;
            if (prize >= 100) {
                int evo = prize - 100;
                run.evolved |= 1 << evo;
                profile().evolutionsSeen |= 1 << evo;
            } else if (prize >= 0) {
                run.levels[prize]++;
                chestLevels[chestCount - 1] = run.levels[prize];
                if (prize == Upgrades.BARRIER && run.levels[prize] == 1 && player != null) player.giveShield();
            } else {
                // Two hits at the base toll: a toll read at the held total would compound itself.
                int rings = Math.max(25, 2 * run.toll(0));
                chestRings += rings;
                if (player != null) player.addRings(rings);
            }
        }
        overlay = OVERLAY_CHEST;
        // Pause at once: a chest picked up during the object pass must not leave badniks a frame.
        run.paused = true;
        menuIndex = 0;
        phaseFramesAtOverlay = phaseFrames;
        services().playSfx(0xBD); // sfx_Hammer: the chest lands.
    }

    int phaseFramesAtOverlay;

    // The chest opening, in frames from its start: the chest drops in and shakes, the lid bursts
    // (flash, light rays, a fountain of rings, Super Sonic's theme), then each prize spins in.
    static final int CHEST_DROP = 18, CHEST_BURST = 64, CHEST_FIRST_PRIZE = 84, CHEST_PRIZE_GAP = 22,
            CHEST_SPIN = 14;

    int chestFrames() { return phaseFrames - phaseFramesAtOverlay; }

    /** The frame the last prize lands; Enter before it reveals everything at once. */
    int chestRevealEnd() { return CHEST_FIRST_PRIZE + chestCount * CHEST_PRIZE_GAP + CHEST_SPIN; }

    boolean chestEvolution() {
        for (int i = 0; i < chestCount; i++) if (chestItems[i] >= 100) return true;
        return false;
    }

    private int chestPrize() {
        var run = run();
        int ready = 0;
        for (int evo = 0; evo < Upgrades.EVOLUTIONS; evo++) if (run.evolutionReady(evo)) ready++;
        if (ready > 0) {
            int pick = run.nextInt(ready);
            for (int evo = 0; evo < Upgrades.EVOLUTIONS; evo++) {
                if (run.evolutionReady(evo) && pick-- == 0) return 100 + evo;
            }
        }
        // Owned upgrades first, so chests deepen the build rather than fill it at random.
        for (boolean owned : new boolean[]{true, false}) {
            int count = 0;
            for (int id = 0; id < Upgrades.COUNT; id++) if (run.canUpgrade(id) && run.has(id) == owned) count++;
            if (count == 0) continue;
            int pick = run.nextInt(count);
            for (int id = 0; id < Upgrades.COUNT; id++) {
                if (run.canUpgrade(id) && run.has(id) == owned && pick-- == 0) return id;
            }
        }
        return -1;
    }

    private void chestMenu(AbstractPlayableSprite player) {
        int t = chestFrames();
        if (t > CHEST_DROP && t < CHEST_BURST && t % Math.max(3, 12 - (t - CHEST_DROP) / 5) == 0) {
            services().playSfx(0xCD); // sfx_Blip: the lid rattles, faster and faster.
        }
        if (t == CHEST_BURST) {
            services().playSfx(0xC1); // The lid bursts.
            services().audioManager().playMusic(Sonic2Music.SUPER_SONIC.id);
        }
        if (t > CHEST_BURST && t < CHEST_BURST + 40 && t % 8 == 0) {
            services().audioManager().playSecondarySfx(GameSound.RING);
        }
        for (int i = 0; i < chestCount; i++) {
            if (t == CHEST_FIRST_PRIZE + i * CHEST_PRIZE_GAP + CHEST_SPIN) {
                services().playSfx(chestItems[i] >= 100 ? 0xBF : 0xC9); // ContinueJingle / Bonus.
            }
        }
        if (!menuConfirmAny(player)) return;
        if (t < chestRevealEnd()) {
            // Skip straight to every prize revealed; a second press continues.
            if (t < CHEST_BURST) services().audioManager().playMusic(Sonic2Music.SUPER_SONIC.id);
            phaseFramesAtOverlay = phaseFrames - chestRevealEnd();
            return;
        }
        overlay = OVERLAY_NONE;
        services().playSfx(0xC9);
        restoreMusic();
    }

    /** After a chest's fanfare: the boss track in a boss fight, the zone's own music otherwise. */
    private void restoreMusic() {
        var audio = services().audioManager();
        if (phase == BOSS) {
            audio.playMusic(arena().stage() == Stages.DEZ ? Sonic2Music.FINAL_BOSS.id : Sonic2Music.BOSS.id);
        } else if (phase == FIGHT) {
            int music = services().levelManager().getCurrentLevelMusicId();
            if (music >= 0) audio.playMusic(music);
        } else {
            audio.fadeOutMusic();
        }
    }

    /** Drops the full reward in up to three physical pickups, without a population ceiling. */
    void dropRings(int x, int y, int value) {
        var run = run();
        int pieces = Math.min(value, 3);
        for (int i = 0; i < pieces; i++) {
            int share = value / pieces + (i < value % pieces ? 1 : 0);
            int vx = (run.nextInt(5) - 2) * 0x100, vy = -0x300 - run.nextInt(3) * 0x80;
            var spawn = Pickup.spawnAt(x, y, Pickup.RING);
            ArenaObjects.spawn(services(), () -> new Pickup(spawn, vx, vy, share));
        }
    }

    /** The guard's ring toll: half the lost rings scatter for Sonic to grab back. */
    void spillRings(int x, int y, int count) {
        var run = run();
        for (int i = 0; i < count; i++) {
            double a = Math.PI * (0.15 + 0.7 * i / Math.max(1, count - 1));
            int vx = (int) (-Math.cos(a) * 0x300), vy = (int) (-Math.sin(a) * 0x400);
            var spawn = Pickup.spawnAt(x, y - 8, Pickup.RING);
            int jitter = run.nextInt(64);
            ArenaObjects.spawn(services(), () -> Pickup.lost(spawn, vx + jitter, vy));
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
        // Their rings are the prize; elite monitors and chests are not, or a crowd left to grow
        // before the boss falls would pay out a chest for every fourth elite in it.
        sweeping = true;
        for (Enemy enemy : enemies()) enemy.hurt(99999, 0);
        sweeping = false;
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
            ArenaObjects.spawn(services(), () -> new Boss(spawn, Boss.RUNNER_HITS));
            banner("EGGMAN IS ESCAPING!", 150, Draw.RED);
            services().audioManager().playMusic(Sonic2Music.FINAL_BOSS.id);
            return;
        }
        phase = CLEAR_WAIT;
        phaseFrames = 0;
        services().audioManager().playMusic(Sonic2Music.ACT_CLEAR.id);
        int stage = arena.stage();
        // Every boss leaves a treasure chest: three prizes, sometimes five.
        if (stage != Stages.DEZ) {
            int prizes = Math.min(CHEST_ITEMS, (run().chance(0.2) ? 5 : 3) + run().shopTreasure);
            dropChest(arena.clampX(x - 24, 32), Math.min(y, arena.floorTop() - 32), prizes);
        }
        if (stage < Profile.EMERALDS && !profile.hasEmerald(stage)) {
            var spawn = Pickup.spawnAt(arena.clampX(x + 24, 32), Math.min(y, arena.floorTop() - 32), Pickup.EMERALD);
            ArenaObjects.spawn(services(), () -> new Pickup(spawn, 0, -0x200, stage));
        } else {
            dropRings(x, y, (int) Math.round(20 * Difficulty.dropValue(Stages.tier(stage, arena.act()))));
            phaseFrames = 200;
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
        // An unopened boss chest opens itself before the results.
        for (var object : services().objectManager().getActiveObjects()) {
            if (object instanceof Pickup p && p.kind() == Pickup.CHEST && !p.isDestroyed() && !p.collectedAlready()) {
                p.setDestroyed(true);
                openChest(p.value());
                return;
            }
        }
        for (var object : services().objectManager().getActiveObjects()) {
            if (object instanceof Pickup p && p.kind() == Pickup.EMERALD && !p.isDestroyed() && !p.collectedAlready()) {
                p.setDestroyed(true);
                onEmeraldCollected();
            }
        }
        openClear();
    }

    /**
     * The Eggman monitor: ordinary badniks on screen are destroyed, elites lose 40% of their
     * health and a boss 6%. A fixed hit would be meaningless against late-route hitpoints.
     */
    void screenNuke() {
        var camera = services().camera();
        hitFlash = 20;
        for (Enemy enemy : enemies()) {
            if (enemy.getX() > camera.getX() - 16 && enemy.getX() < camera.getX() + camera.getWidth() + 16) {
                enemy.hurtIgnoringFrames(enemy.elite() ? Math.max(1, enemy.maxHp() * 2 / 5) : enemy.hp());
            }
        }
        Boss boss = boss();
        if (boss != null && boss.alive()) boss.hurt(Math.max(1, boss.maxHp() * 6 / 100));
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

    void popup(int x, int y, int amount) {
        if (amount <= 0) return;
        int scale = amount >= 50 ? 3 : amount >= 10 ? 2 : 1;
        int colour = amount >= 50 ? Draw.PINK : amount >= 10 ? Draw.ORANGE : Draw.RED;
        effect(E_NUMBER, x, y, amount, colour, scale);
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
            hoverFrames = 0;
            return;
        }
        airFrames++;
        if (dashFrames > 0) dashFrames--;
        if (player.isHurt() || player.getDead()) return;
        if (pounding) {
            if (player.getYSpeed() < 0x900) player.setYSpeed((short) 0x900);
            // Impact Star: untouchable for the whole slam.
            if (run.evolvedWeapon(Upgrades.GROUND_POUND)) player.setInvulnerableFrames(Math.max(player.getInvulnerableFrames(), 2));
            return;
        }
        // Tails's hover: holding jump while falling slows the descent, a little each jump.
        if (run.tails && player.isJumpPressed() && player.getYSpeed() > 0x100 && hoverFrames < HOVER_FRAMES
                && dashFrames == 0) {
            hoverFrames++;
            player.setYSpeed((short) 0x100);
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
        int range = Upgrades.dashRange(run().level(Upgrades.HOMING_DASH))
                * (run().evolvedWeapon(Upgrades.HOMING_DASH) ? 8 : 5) / 5;
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
        boolean star = run.evolvedWeapon(Upgrades.GROUND_POUND);
        int radius = Upgrades.poundRadius(level) * (star ? 3 : 2) / 2;
        int x = player.getCentreX(), y = player.getCentreY() + 16;
        effect(E_POUND, x, y, areaRadius(radius), star ? Draw.GOLD : Draw.YELLOW, 0);
        damageArea(x, y, radius, scaled(Upgrades.poundDamage(level) * (star ? 3 : 1), run.damageMultiplier()), star);
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
            int bonus = (int) Math.round(Math.min(20, run.combo / 2)
                    * Difficulty.dropValue(rewardTier()));
            dropRings(player.getCentreX(), player.getCentreY() - 48, bonus);
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
            boolean saturn = run.evolvedWeapon(Upgrades.ORBIT_RINGS);
            int n = orbitRings(), distance = orbitDistance();
            int damage = scaled(Upgrades.orbitDamage(orbit) * (saturn ? 2 : 1), power);
            for (int i = 0; i < n; i++) {
                double a = (orbitAngle / 1024.0 + i / (double) n) * Math.PI * 2;
                int ox = px + (int) (Math.cos(a) * distance), oy = py + (int) (Math.sin(a) * distance);
                damageArea(ox, oy, saturn ? 16 : 12, damage, false);
            }
        }
        int boom = run.level(Upgrades.SONIC_BOOM);
        boolean cyclone = run.evolvedWeapon(Upgrades.SONIC_BOOM);
        if (boom > 0 && ++boomTimer >= (int) (Upgrades.boomCooldown(boom) * run.cooldownScale() * (cyclone ? 0.5 : 1))) {
            boomTimer = 0;
            int dir = player.getRenderHFlip() ? -1 : 1;
            int damage = scaled(Upgrades.boomDamage(boom) * (cyclone ? 2 : 1), power);
            projectile(P_BOOM, px + dir * 12, py, dir * 0x700, 0, 70, damage, 99);
            if (boom >= 4 || cyclone) projectile(P_BOOM, px - dir * 12, py, -dir * 0x700, 0, 70, damage, 99);
            services().playSfx(0xAB); // sfx_Swish.
        }
        int flicky = run.level(Upgrades.FLICKIES);
        boolean flock = run.evolvedWeapon(Upgrades.FLICKIES);
        if (flicky > 0 && ++flickyTimer >= (int) (Upgrades.flickyCooldown(flicky) * run.cooldownScale() * (flock ? 0.7 : 1))) {
            flickyTimer = 0;
            for (int i = 0; i < Upgrades.flickyCount(flicky) * (flock ? 2 : 1); i++) {
                projectile(P_FLICKY, px, py - 8, (i % 2 == 0 ? -1 : 1) * (0x200 + i / 2 * 0x80), -0x300, 160,
                        scaled(flock ? 8 : 3, power), 1);
            }
        }
        int meteor = run.level(Upgrades.METEOR);
        boolean comet = run.evolvedWeapon(Upgrades.METEOR);
        if (meteor > 0 && fightFrames % Math.max(1, (int) ((comet ? 120 : 180) * run.cooldownScale())) == 0) {
            int count = (2 + meteor) * (comet ? 2 : 1);
            for (int i = 0; i < count; i++)
                projectile(P_SPARK, px + (i * 2 - (count - 1)) * (comet ? 14 : 24), py - 96,
                        0, 0x500, 60, scaled((3 + meteor) * (comet ? 2.5 : 1), power), 2);
        }
        int pulse = run.level(Upgrades.PULSE);
        if (pulse > 0 && fightFrames % Math.max(1, (int) (120 * run.cooldownScale())) == 0) {
            boolean guardian = run.evolvedWeapon(Upgrades.PULSE);
            int radius = (40 + 8 * pulse) * (guardian ? 7 : 5) / 5;
            effect(E_SHOCK, px, py, areaRadius(radius), guardian ? Draw.GOLD : Draw.PURPLE, 0);
            damageArea(px, py, radius, scaled((2 + pulse) * (guardian ? 2.5 : 1), power), guardian);
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

    int orbitRings() {
        int orbit = run().level(Upgrades.ORBIT_RINGS);
        return orbit <= 0 ? 0 : Upgrades.orbitCount(orbit) + (run().evolvedWeapon(Upgrades.ORBIT_RINGS) ? 2 : 0);
    }

    int orbitDistance() { return run().evolvedWeapon(Upgrades.ORBIT_RINGS) ? 56 : 42; }

    private int areaRadius(int radius) {
        return (int) Math.round(radius * (1 + 0.15 * run().level(Upgrades.REACH)));
    }

    /** Damages nearby enemies and bosses, including the Amplifier radius bonus. */
    private void damageArea(int x, int y, int radius, int damage, boolean popShots) {
        radius = areaRadius(radius);
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

    private void chainZap(int x, int y, int targets, int damage, int range) {
        var hit = new ArrayList<Enemy>();
        int fromX = x, fromY = y;
        for (int n = 0; n < targets; n++) {
            Enemy next = null;
            double best = range;
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
        if (hit.size() < targets && boss != null && boss.alive() && Math.hypot(boss.getX() - fromX, boss.getY() - fromY) < range) {
            effect(E_BOLT, fromX, fromY, boss.getX(), Draw.CYAN, boss.getY());
            boss.hurt(damage);
        }
        if (!hit.isEmpty()) services().playSfx(0xB1); // sfx_Zap.
    }

    private void projectile(int kind, int x, int y, int vx, int vy, int life, int damage, int hits) {
        int slot = 0;
        while (slot < pKind.length && pKind[slot] != 0) slot++;
        if (slot == pKind.length) {
            int capacity = pKind.length * 2;
            pX = java.util.Arrays.copyOf(pX, capacity);
            pY = java.util.Arrays.copyOf(pY, capacity);
            pVX = java.util.Arrays.copyOf(pVX, capacity);
            pVY = java.util.Arrays.copyOf(pVY, capacity);
            pLife = java.util.Arrays.copyOf(pLife, capacity);
            pKind = java.util.Arrays.copyOf(pKind, capacity);
            pDmg = java.util.Arrays.copyOf(pDmg, capacity);
            pHits = java.util.Arrays.copyOf(pHits, capacity);
        }
        pKind[slot] = kind;
        pX[slot] = x << 8;
        pY[slot] = y << 8;
        pVX[slot] = vx;
        pVY[slot] = vy;
        pLife[slot] = life;
        pDmg[slot] = damage;
        pHits[slot] = hits;
    }

    private void updateProjectiles() {
        if (paused()) return;
        List<Enemy> enemies = null;
        Boss boss = null;
        boolean bossLooked = false;
        for (int i = 0; i < pKind.length; i++) {
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
            boolean wave = pKind[i] == P_BOOM || pKind[i] == P_LANCE;
            int size = wave ? 14 : 8;
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
                if (boss.hurt(pDmg[i]) || !wave) pHits[i]--;
            }
            if (wave) {
                for (var object : services().objectManager().getActiveObjects()) {
                    if (object instanceof Shot shot && !shot.isDestroyed()
                            && Math.abs(shot.getX() - x) < 16 && Math.abs(shot.getY() - y) < 16) shot.pop();
                }
            }
            var arena = arena();
            if (x < arena.left() - 32 || x > arena.right() + 32 || y < arena.floorTop() - 400
                    || y > arena.floorBottom() + 64) pKind[i] = 0;
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
                case E_WARN -> 45;
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
    static final int CAMP_START = 0, CAMP_SHOP = 1, CAMP_MODE = 2, CAMP_RULES = 3, CAMP_RECORDS = 4, CAMP_TITLE = 5,
            CAMP_ROWS = 6;

    /** Edge-detected left/right (returns -1, 0 or 1). */
    private int menuSideways(AbstractPlayableSprite player) {
        boolean left = player.isLeftPressed(), right = player.isRightPressed();
        int move = (left && !leftHeld ? -1 : 0) + (right && !rightHeld ? 1 : 0);
        leftHeld = left;
        rightHeld = right;
        return move;
    }

    private void campMenu(AbstractPlayableSprite player) {
        if (campPage == PAGE_RULES) { rulesMenu(player); return; }
        if (campPage == PAGE_RECORDS) { recordsMenu(player); return; }
        if (campPage == PAGE_SHOP) { shopMenu(player); return; }
        moveCursor(menuMove(player), CAMP_ROWS);
        menuSideways(player);
        if (!menuConfirm(player)) return;
        if (menuIndex == CAMP_START) { beginRun(player); return; }
        if (menuIndex == CAMP_TITLE) { exitToTitle(); return; }
        var profile = profile();
        if (menuIndex == CAMP_RULES) {
            if (!profile.extendedModesUnlocked()) {
                banner("CLEAR A BOSS TO UNLOCK EGGMAN'S RULES", 90, Draw.GOLD);
                services().playSfx(0xED);
                return;
            }
            openCampPage(PAGE_RULES);
            return;
        }
        if (menuIndex == CAMP_RECORDS) { openCampPage(PAGE_RECORDS); return; }
        if (menuIndex == CAMP_SHOP) { openCampPage(PAGE_SHOP); return; }
        if (menuIndex == CAMP_MODE) {
            if (arena().stage() == Stages.DEZ) {
                banner("DEATH EGG IS ALWAYS THE BOSS FINALE", 90, Draw.CYAN);
            } else if (profile.cycleMode()) {
                services().playSfx(0xCD);
            } else {
                banner("CLEAR A BOSS TO UNLOCK LONGER MODES", 90, Draw.GOLD);
                services().playSfx(0xED);
            }
        }
    }

    /** The ring-bank shop: one row per item, then BACK. */
    private void shopMenu(AbstractPlayableSprite player) {
        moveCursor(menuMove(player), Profile.SHOP_COUNT + 1);
        if (!menuConfirm(player)) return;
        if (menuIndex == Profile.SHOP_COUNT) { closeCampPage(CAMP_SHOP); return; }
        var profile = profile();
        if (profile.buy(menuIndex)) {
            services().playSfx(0xC0); // sfx_CasinoBonus.
            banner(Profile.shopName(menuIndex) + " " + profile.shop[menuIndex], 60, Draw.GREEN);
        } else {
            services().playSfx(0xED); // sfx_Error.
        }
    }

    private void openCampPage(int page) {
        campPage = page;
        menuIndex = 0;
        phaseFrames = 0;
        services().playSfx(0xCD);
    }

    private void closeCampPage(int row) {
        campPage = PAGE_CAMP;
        menuIndex = row;
        phaseFrames = 0;
        services().playSfx(0xCD);
    }

    /** Eggman's Rules: one row per rule, then BACK. */
    private void rulesMenu(AbstractPlayableSprite player) {
        moveCursor(menuMove(player), Rules.COUNT + 1);
        if (!menuConfirm(player)) return;
        if (menuIndex == Rules.COUNT) { closeCampPage(CAMP_RULES); return; }
        profile().toggleRule(menuIndex);
        services().playSfx((profile().rules & 1 << menuIndex) != 0 ? 0xC0 : 0xCD);
    }

    /** The records: left/right or up/down turn the page, Enter returns to camp. */
    private void recordsMenu(AbstractPlayableSprite player) {
        int move = menuMove(player) + menuSideways(player);
        if (move != 0) {
            recordsTab = Math.floorMod(recordsTab + move, RECORD_TABS);
            services().playSfx(0xCD);
        }
        if (menuConfirm(player)) closeCampPage(CAMP_RECORDS);
    }

    private void beginRun(AbstractPlayableSprite player) {
        var run = run();
        var arena = arena();
        var profile = profile();
        run.begin(arena.stage(), profile, SurvivorsMod.seed());
        run.tails = player instanceof com.openggf.sprites.playable.Tails;
        profile.runs++;
        profile.save();
        player.setRingCount(run.carriedRings);
        if (run.relic(5)) player.giveShield();
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
            if (!run.canUpgrade(id)) continue;
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
        // With everything maxed: Overdrive (-2, endless damage growth) or a ring bonus (-1).
        if (cardCount == 0) {
            cards[cardCount++] = CARD_OVERDRIVE;
            cards[cardCount++] = CARD_RINGS;
        }
        // Until Sonic owns a weapon, the first card is always one: stomps alone do not scale.
        boolean armed = false;
        for (int id = 0; id < Upgrades.COUNT; id++) armed |= Upgrades.weapon(id) && run.has(id);
        boolean offered = false;
        for (int i = 0; i < cardCount; i++) offered |= cards[i] >= 0 && Upgrades.weapon(cards[i]);
        if (!armed && !offered && cards[0] >= 0) {
            int weapons = 0;
            for (int id = 0; id < Upgrades.COUNT; id++) if (Upgrades.weapon(id) && run.canUpgrade(id)) weapons++;
            int pick = run.nextInt(Math.max(1, weapons));
            for (int id = 0; id < Upgrades.COUNT && weapons > 0; id++) {
                if (Upgrades.weapon(id) && run.canUpgrade(id) && pick-- == 0) { cards[0] = id; break; }
            }
        }
    }

    static final int CARD_RINGS = -1, CARD_OVERDRIVE = -2;

    /** A maxed build's ring card: three hits at the base toll. */
    int maxedRingBonus() { return Math.max(25, 3 * run().toll(0)); }

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
        if (card == CARD_OVERDRIVE) {
            run.overdrive++;
        } else if (card < 0) {
            player.addRings(maxedRingBonus());
        } else {
            run.levels[card]++;
            if (card == Upgrades.BARRIER && run.levels[card] == 1) player.giveShield();
        }
        run.pendingLevels--;
        services().playSfx(0xC9); // sfx_Bonus.
        banner(card == CARD_OVERDRIVE ? "OVERDRIVE " + run.overdrive : card < 0 ? "RING BONUS"
                        : Upgrades.name(card) + " LV " + run.levels[card], 70,
                card == CARD_OVERDRIVE ? Draw.RED : card < 0 ? Draw.GOLD : Upgrades.kindColour(card));
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
        int pool = profile.upgradePool(run.kills, run.bestCombo, run.level + 1, run.elitesDefeated, next, 0);
        newUnlocks = profile.claimNewUnlocks(pool);
        // Milestones reached mid-run join this run's pool from the next zone.
        run.pool |= pool;
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
    /**
     * Banks the run. Retiring at a zone clear also banks the rings in hand, less the free rings the
     * run was given (start rings and revives), so starting and retiring is never a ring farm.
     * Leaving mid-fight is a forfeit and pays like a game over. Eggman's Rules add their bonus.
     */
    private int bank(boolean won, boolean retired) {
        var run = run();
        var profile = profile();
        var player = services().camera().getFocusedSprite();
        int held = retired && player != null ? Math.max(0, player.getRingCount() - run.freeRings) : 0;
        // Badnik ring values grow along the route, so the bank takes a quarter of them.
        int earned = run.ringsCollected / 4 + 25 * run.stagesCleared + run.kills / 10 + held + (won ? 300 : 0);
        int banked = earned * (100 + Rules.totalBonus(run.rules)) / 100;
        newUnlocks = profile.claimNewUnlocks(profile.upgradePool(run.kills, run.bestCombo, run.level + 1,
                run.elitesDefeated, 0, banked));
        profile.bank += banked;
        profile.bankedTotal += banked;
        profile.totalKills += run.kills;
        profile.totalElites += run.elitesDefeated;
        for (int i = 0; i < Species.COUNT; i++) profile.speciesKills[i] += run.speciesKills[i];
        profile.bestKills = Math.max(profile.bestKills, run.kills);
        profile.bestCombo = Math.max(profile.bestCombo, run.bestCombo);
        profile.bestStages = Math.max(profile.bestStages, run.stagesCleared);
        profile.bestLevel = Math.max(profile.bestLevel, run.level + 1);
        if (endless() && run.mode == RunState.ENDLESS) {
            int stage = arena().stage();
            profile.bestUnlimited[stage] = Math.max(profile.bestUnlimited[stage], fightFrames / 60);
        }
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

    /** Ends the run voluntarily: at a zone clear it banks held rings; mid-fight it is a forfeit. */
    private void retire(boolean toTitle) {
        if (run().active) bank(false, phase == CLEAR);
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
                holdProtection(player);
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
            savedInvincible = player.getInvincibleFrames();
            savedInvulnerable = player.getInvulnerableFrames();
            holdProtection(player);
            player.setObjectControlled(true);
            player.setXSpeed((short) 0);
            player.setYSpeed((short) 0);
            player.setGSpeed((short) 0);
        } else {
            player.setInvincibleFrames(savedInvincible);
            player.setInvulnerableFrames(savedInvulnerable);
            player.setObjectControlled(false);
            player.setXSpeed((short) savedX);
            player.setYSpeed((short) savedY);
            player.setGSpeed((short) savedG);
        }
    }

    /** Player power-up clocks still tick under object control. Keep them above expiry during
     * menus and restore the exact saved duration on release; the extra tick is never playable. */
    private void holdProtection(AbstractPlayableSprite player) {
        player.setInvincibleFrames(savedInvincible > 0 ? savedInvincible + 1 : 0);
        player.setInvulnerableFrames(savedInvulnerable > 0 ? savedInvulnerable + 1 : 0);
    }

    // =========================================================================================
    // Drawing
    // =========================================================================================

    @Override public void appendRenderCommands(List<GLCommand> commands) {
        Hud.drawWorld(services(), this);
        Hud.draw(services(), this);
    }
}
