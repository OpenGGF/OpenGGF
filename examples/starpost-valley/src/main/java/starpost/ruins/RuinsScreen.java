package starpost.ruins;

import com.openggf.mods.scene.SceneBackdrop;
import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneSprite;
import com.openggf.mods.scene.SceneSpriteSet;
import com.openggf.mods.state.SnapshotRandom;
import java.util.ArrayList;
import java.util.List;
import starpost.art.Anim;
import starpost.core.Game;
import starpost.core.Inventory;
import starpost.core.Item;
import starpost.scene.DayEndScreen;
import starpost.scene.InventoryMenu;
import starpost.scene.PlayScreen;
import starpost.scene.Screen;
import starpost.scene.Sfx;
import starpost.scene.Shell;
import starpost.ui.Text;
import starpost.valley.Runner;

/**
 * The Marble Ruins in play (design doc §6.5): a chamber at a time in side view on the ported
 * Sonic controller. Rings are health: a hit scatters the rings in hand in Sonic 1's burst, to be
 * grabbed back before they vanish, and a hit with none means fainting. The spin jump pops
 * badniks, freeing animals; rolling into rocks breaks them for ore (the Fire Shield burns them for
 * twice as much); in the Labyrinth the water slows Sonic down and the drowning countdown runs.
 * The exit hatch goes down a chamber, the shaft of light at the entry climbs out to the valley,
 * and every fifth chamber's Star Post is an elevator. Rings in hand are banked on leaving.
 */
public final class RuinsScreen implements Screen {
    private static final int FADE = 16;
    private static final int CARD = 80;
    private static final int FAINT_TICKS = 110;
    /** Sfx not in the scene's own list (Sonic3kSfx). */
    private static final int SFX_DEATH = 0x35;
    private static final int SFX_SPIKES = 0x37;
    private static final int SFX_BUBBLE = 0x38;
    private static final int SFX_DROWN = 0x3B;
    private static final int SFX_FIRE_DASH = 0x43;
    private static final int SFX_AIR_DING = 0xA9;
    private static final int SFX_EXPLODE = 0xB4;
    private static final int S1_DROWNING = 0x92;
    // S3K player animations (AniSonic): get air, drown, death, hurt.
    private static final int ANIM_GET_AIR = 0x15;
    private static final int ANIM_DROWN = 0x17;
    private static final int ANIM_DEATH = 0x18;
    private static final int ANIM_HURT = 0x1A;
    /** Map_Monitor: the ten-ring screen, and the broken husk. */
    private static final int MONITOR_RINGS = 4;
    private static final int MONITOR_BROKEN = 11;

    private final Shell shell;
    private final PlayScreen play;
    private final RuinsArt art;
    private final RuinsSection section;
    private final ChamberGen gen;
    private final SnapshotRandom rng;
    private final Runner runner = new Runner(0, 0);
    private final Anim anim = new Anim();

    private Chamber chamber;
    private int number;
    private float camX;
    private float camY;
    private int ringsInHand;
    private int flash;
    private int air = RuinsRules.AIR_SECONDS;
    private int airFrames;
    private boolean drowningMusic;
    private int getAir;
    private boolean fireDashUsed;
    private int fireDash;
    private boolean elevatorLit;
    private int springAt = -100;
    private long litAt = -100;
    private long ticks;

    // The chamber's contents.
    private boolean[] taken;
    private final List<Badnik> badniks = new ArrayList<>();
    private final List<Badnik.Shot> shots = new ArrayList<>();
    private final List<Loose.LostRing> lost = new ArrayList<>();
    private int lostTimer;
    private final List<Loose.Animal> animals = new ArrayList<>();
    private final List<Loose.Pickup> pickups = new ArrayList<>();
    private final List<Loose.Bubble> bubbles = new ArrayList<>();
    private final List<Loose.Puff> puffs = new ArrayList<>();
    private final List<float[]> harm = new ArrayList<>();
    private int nextLostIndex;

    // Transitions: a fade to the next chamber, a faint, the title card.
    private int fade;
    private int pendingChamber;
    private int faint;
    private boolean faintLosesItems;
    private String faintReason = "";
    private List<RuinsRules.Loss> faintLosses = new ArrayList<>();
    private int card;
    private boolean leaving;
    private String outOfMomentum = "";

    public RuinsScreen(Shell shell, PlayScreen play, RuinsArt art, int start) {
        this.shell = shell;
        this.play = play;
        this.art = art;
        this.section = RuinsSystem.section(shell.game);
        this.gen = new ChamberGen(art);
        this.rng = new SnapshotRandom(section.seed ^ shell.game.calendar.dayNumber() * 31L ^ start);
        this.number = start;
        int carry = RuinsRules.carried(shell.game.rings);   // banked back by leave()
        shell.game.rings -= carry;
        ringsInHand = carry;
    }

    PlayScreen play() {
        return play;
    }

    RuinsArt art() {
        return art;
    }

    @Override
    public void enter(Shell shell) {
        if (chamber == null) {
            load(number);
        }
        shell.music.want("s1", drowningMusic ? S1_DROWNING : RuinsRules.music(chamber.band));
    }

    /** Builds a chamber (the same all day) and stands Sonic at its entry. */
    private void load(int n) {
        number = n;
        long seed = RuinsRules.chamberSeed(section.seed, shell.game.calendar.dayNumber(), n);
        chamber = gen.generate(n, seed);
        section.bestChamber = Math.max(section.bestChamber, n);
        taken = new boolean[chamber.things.size()];
        badniks.clear();
        shots.clear();
        lost.clear();
        animals.clear();
        pickups.clear();
        bubbles.clear();
        puffs.clear();
        for (Chamber.Thing t : chamber.things) {
            if (t.type() == Chamber.BADNIK) {
                badniks.add(new Badnik(t.param(), t.x(), t.y()));
            }
        }
        runner.x = chamber.entryX;
        runner.y = chamber.entryY;
        runner.speed = 0;
        runner.ySpeed = 0;
        runner.onGround = true;
        runner.rolling = runner.dashing = runner.ducking = runner.hurt = runner.sprung = false;
        runner.underwater = chamber.underwater(runner.x, runner.y, runner.height());
        runner.facingLeft = false;
        wasUnderLast = runner.underwater;
        elevatorLit = RuinsRules.landmark(n) && section.deepest >= n;
        flash = RuinsRules.ARRIVAL_FLASH;
        if (!runner.underwater) {
            breathe();
        }
        camX = clampX(runner.x - shell.width() / 2f);
        camY = clampY(runner.y - 150);
        card = CARD;
        shell.music.want("s1", RuinsRules.music(chamber.band));
        drowningMusic = false;
    }

    // ------------------------------------------------------------------ update

    @Override
    public void update(Shell shell) {
        ticks++;
        Game game = shell.game;
        if (fade > 0) {
            if (--fade == FADE / 2) {
                load(pendingChamber);
            }
            return;
        }
        if (faint > 0) {
            updateFaint(shell);
            return;
        }
        if (card > 0) {
            card--;
        }
        if (shell.in.menu) {
            shell.push(new InventoryMenu());
            return;
        }
        hotbar(shell);
        game.calendar.tick();
        if (game.calendar.overtime()) {
            startFaint("THE DAY RAN OUT", false);
            return;
        }
        if (doorways(shell)) {
            return;
        }
        controls(shell);
        if (runner.y > chamber.height + 40) {
            descend(shell, false);
            return;
        }
        water(shell);
        if (faint > 0) {
            return;
        }
        things(shell);
        if (faint > 0 || fade > 0) {
            return;   // a hit with no rings (or the way down) began this frame
        }
        if (flash > 0) {
            flash--;
        }
        animate();
        float targetX = runner.x - shell.width() / 2f + (runner.facingLeft ? -24 : 24);
        camX += (clampX(targetX) - camX) * 0.18f;
        camY += (clampY(runner.y - 140) - camY) * 0.15f;
    }

    private void hotbar(Shell shell) {
        Inventory inv = shell.game.inventory;
        int before = inv.selected();
        if (shell.in.hotbarKey >= 0) {
            inv.select(shell.in.hotbarKey);
        }
        if (shell.in.nextTool) {
            inv.select(inv.selected() + 1);
        }
        if (shell.in.prevTool) {
            inv.select(inv.selected() - 1);
        }
        if (inv.selected() != before) {
            shell.hotbarChangedAt = shell.ticks;
        }
    }

    /** Up at the entry climbs out; up at a lit elevator rides up; down on the hatch goes deeper. */
    private boolean doorways(Shell shell) {
        if (!runner.onGround || runner.hurt) {
            return false;
        }
        if (shell.in.upPressed && near(chamber.entryX, chamber.entryY, 14)) {
            shell.push(new RuinsMenu("CLIMB OUT TO THE VALLEY?", new String[] {"CLIMB OUT", "STAY"}, c -> {
                if (c == 0) {
                    leave(shell);
                }
            }));
            return true;
        }
        if (chamber.elevatorX >= 0 && near(chamber.elevatorX, chamber.elevatorY, 16)) {
            if (!elevatorLit) {
                elevatorLit = true;
                litAt = ticks;
                shell.sfx(Sfx.STARPOST);
                if (section.reachElevator(number)) {
                    shell.toast("ELEVATOR " + number + " REACHED");
                }
            }
            if (shell.in.upPressed) {
                shell.push(new RuinsMenu("STAR POST ELEVATOR", new String[] {"RIDE UP TO THE VALLEY", "KEEP EXPLORING"},
                        c -> {
                            if (c == 0) {
                                leave(shell);
                            }
                        }));
                return true;
            }
        }
        if (chamber.exitX >= 0 && near(chamber.exitX, chamber.exitY, 14) && shell.in.down
                && Math.abs(runner.speed) < 1 && !runner.dashing) {
            descend(shell, true);
            return true;
        }
        return false;
    }

    private boolean near(int x, int y, int half) {
        return Math.abs(runner.x - x) <= half && Math.abs(runner.y - y) <= 8;
    }

    private void descend(Shell shell, boolean hatch) {
        if (number >= RuinsRules.CHAMBERS) {
            // Nothing below the last chamber: back up through the shaft of light.
            runner.x = chamber.entryX;
            runner.y = chamber.entryY;
            runner.speed = runner.ySpeed = 0;
            runner.onGround = true;
            shell.toast("THE WAY DOWN IS SEALED... FOR NOW");
            return;
        }
        shell.sfx(hatch ? Sfx.DOOR_OPEN : Sfx.ROLL);
        pendingChamber = number + 1;
        fade = FADE;
    }

    /** Banks the rings in hand and goes back to the valley doorway. */
    private void leave(Shell shell) {
        if (leaving) {
            return;
        }
        leaving = true;
        if (ringsInHand > 0) {
            shell.game.rings += ringsInHand;
            shell.toast(ringsInHand + " RINGS BANKED");
            ringsInHand = 0;
        }
        shell.go(play);
    }

    private void controls(Shell shell) {
        Game game = shell.game;
        boolean fire = "fire_shield".equals(game.inventory.selectedId());
        // S3K's Fire Shield dash: a second jump press in the air throws Sonic forward at $800.
        if (fire && !runner.onGround && !runner.hurt && runner.jumpedAt >= 0 && shell.in.jump && !fireDashUsed) {
            fireDashUsed = true;
            fireDash = 20;
            runner.speed = runner.facingLeft ? -8 : 8;
            runner.ySpeed = 0;
            shell.sfx(SFX_FIRE_DASH);
            shell.in.jump = false;
        }
        if (shell.in.act && fire && runner.onGround) {
            burnNearbyRock(shell);
        }
        boolean wasOnGround = runner.onGround;
        boolean wasDashing = runner.dashing;
        runner.character = shell.game.farmer;
        runner.climbUp = shell.in.up;
        if (runner.step(chamber, shell.in.left, shell.in.right, shell.in.down, shell.in.jump, shell.in.jumpHeld,
                (int) ticks)) {
            shell.sfx(Sfx.JUMP);
        }
        if (runner.dashing && shell.in.jump) {
            shell.sfx(Sfx.SPINDASH);
        }
        if (wasDashing && !runner.dashing) {
            shell.sfx(Sfx.DASH);
        }
        int event = chamber.afterStep(runner);
        if (event == 1) {
            springAt = (int) ticks;
            shell.sfx(Sfx.SPRING);
            game.restoreBySpeed(1);
        }
        if (runner.onGround) {
            fireDashUsed = false;
            fireDash = 0;
            if (!wasOnGround && runner.hurt) {
                runner.hurt = false;
            }
        } else if (fireDash > 0) {
            fireDash--;
        }
    }

    // ------------------------------------------------------------------ water and air

    private void water(Shell shell) {
        if (chamber.waterY == Chamber.NO_WATER) {
            if (drowningMusic) {
                restoreMusic(shell);
            }
            return;
        }
        // Chamber.afterStep switched the physics at the line; here are the splash and the air.
        if (runner.underwater && !wasUnderLast) {
            shell.sfx(Sfx.SPLASH);
            puffs.add(new Loose.Puff(Loose.Puff.SPLASH, runner.x, chamber.waterY, null));
            breathe();
        } else if (!runner.underwater && wasUnderLast) {
            shell.sfx(Sfx.SPLASH);
            puffs.add(new Loose.Puff(Loose.Puff.SPLASH, runner.x, chamber.waterY, null));
            breathe();
            if (drowningMusic) {
                restoreMusic(shell);
            }
        }
        wasUnderLast = runner.underwater;
        if (getAir > 0) {
            getAir--;
        }
        for (Chamber.Thing t : chamber.things) {
            if (t.type() == Chamber.BUBBLES && (ticks + t.x()) % 37 == 0) {
                boolean large = (ticks / 37 + t.x()) % 5 == 0;
                bubbles.add(new Loose.Bubble(t.x(), t.y() - 6, large, t.x() % 17));
            }
        }
        if (!runner.underwater || RuinsRules.breathes(shell.game)) {
            if (runner.underwater) {
                air = RuinsRules.AIR_SECONDS;
            }
            if (drowningMusic) {
                restoreMusic(shell);
            }
            return;
        }
        // Drown_Countdown: a second of air each 60 frames, dings at 25/20/15, the music at 12.
        if (++airFrames >= 60) {
            airFrames = 0;
            if (RuinsRules.airWarning(air)) {
                shell.sfx(SFX_AIR_DING);
            }
            if (air == RuinsRules.AIR_MUSIC && !drowningMusic) {
                drowningMusic = true;
                shell.music.want("s1", S1_DROWNING);
            }
            air--;
            if (air < 0) {
                if (shell.game.inventory.remove("tide_sapphire", 1) > 0) {
                    shell.toast("THE TIDE SAPPHIRE GAVE A BREATH");
                    shell.sfx(SFX_BUBBLE);
                    breathe();
                    restoreMusic(shell);
                    return;
                }
                shell.sfx(SFX_DROWN);
                startFaint(shell.game.farmer.toUpperCase() + " RAN OUT OF AIR", true);
            }
        }
    }

    private boolean wasUnderLast;

    private void breathe() {
        air = RuinsRules.AIR_SECONDS;
        airFrames = 0;
    }

    private void restoreMusic(Shell shell) {
        drowningMusic = false;
        shell.music.want("s1", RuinsRules.music(chamber.band));
    }

    // ------------------------------------------------------------------ contact

    /** Sonic's touch box (ReactToItem): x +-8, centre +-(y radius - 3). */
    private float[] body() {
        float radius = runner.height() / 2f;
        return new float[] {runner.x, runner.y - radius, 8, radius - 3};
    }

    private static boolean overlap(float[] a, float ax, float ay, float hw, float hh) {
        return Math.abs(a[0] - ax) < a[2] + hw && Math.abs(a[1] - ay) < a[3] + hh;
    }

    private boolean attacking() {
        return runner.rolling || runner.dashing || fireDash > 0;
    }

    private void things(Shell shell) {
        Game game = shell.game;
        float[] me = body();
        boolean lightning = "lightning_shield".equals(game.inventory.selectedId());
        boolean collectRings = flash < RuinsRules.RING_COLLECT_FLASH;
        for (int i = 0; i < chamber.things.size(); i++) {
            if (taken[i]) {
                continue;
            }
            Chamber.Thing t = chamber.things.get(i);
            switch (t.type()) {
                case Chamber.RING -> {
                    if (collectRings && (overlap(me, t.x(), t.y(), 6, 6) || lightning && magnet(t.x(), t.y()))) {
                        taken[i] = true;
                        collectRing(shell, t.x(), t.y());
                    }
                }
                case Chamber.ROCK -> rock(shell, i, t, me);
                case Chamber.MONITOR -> {
                    if (attacking() && overlap(me, t.x(), t.y() - 15, 14, 15)) {
                        taken[i] = true;
                        breakMonitor(shell, t);
                    }
                }
                case Chamber.SPIKES -> {
                    if (overlap(me, t.x(), t.y() - 8, Chamber.SPIKES_HALF, 8)) {
                        hurt(shell, t.x(), SFX_SPIKES);
                    }
                }
                default -> {
                }
            }
        }
        for (Chamber.Lava l : chamber.lava) {
            if (!RuinsRules.lavaImmune(game) && (l.contains(runner.x, runner.y) || l.contains(runner.x, runner.y - 8))) {
                hurt(shell, runner.x + (runner.facingLeft ? 1 : -1), SFX_DEATH);
            }
        }
        // Scattered rings: RLoss_Bounce; collected once the flashing is under 90 frames.
        if (lostTimer > 0) {
            lostTimer--;
        }
        for (Loose.LostRing r : lost) {
            r.update(chamber, ticks);
            if (lostTimer <= 0) {
                r.alive = false;
            }
            // ReactToItem: no ring while more than 90 frames of the hit's flashing remain (read now:
            // a hit earlier this frame must not hand the scattered rings straight back).
            if (r.alive && flash < RuinsRules.RING_COLLECT_FLASH
                    && (overlap(me, r.x, r.y, 6, 6) || lightning && magnet(r.x, r.y))) {
                r.alive = false;
                collectRing(shell, r.x, r.y);
            }
        }
        lost.removeIf(r -> !r.alive);
        badniks(shell, me);
        for (Badnik.Shot s : shots) {
            s.update(chamber);
            if (s.alive && overlap(me, s.x, s.y, s.half(), s.half())) {
                hurt(shell, s.x, SFX_DEATH);
                if (s.kind != Badnik.Shot.SPIKEBALL) {
                    s.alive = false;
                }
            }
            if (!s.alive && s.kind == Badnik.Shot.CANNONBALL) {
                puffs.add(new Loose.Puff(Loose.Puff.EXPLOSION, s.x, s.y, null));
            }
        }
        shots.removeIf(s -> !s.alive);
        for (Loose.Animal a : animals) {
            a.update(chamber);
        }
        animals.removeIf(a -> !a.alive);
        float cy = runner.y - runner.height() / 2f;
        for (Loose.Pickup p : pickups) {
            p.update(chamber, runner.x, cy);
            if (p.age > 26 && Math.abs(p.x - runner.x) < 10 && Math.abs(p.y - cy) < 14) {
                p.alive = false;
                take(shell, p);
            }
        }
        pickups.removeIf(p -> !p.alive);
        for (Loose.Bubble b : bubbles) {
            b.update(chamber);
            if (b.alive && b.large && b.frame() >= 6 && runner.underwater && overlap(me, b.x, b.y, 12, 12)) {
                // Breathing a large bubble (Bub_ChkSonic): air refilled, Sonic stops to gulp it.
                b.alive = false;
                breathe();
                getAir = 35;
                runner.speed = 0;
                runner.ySpeed = 0;
                shell.sfx(SFX_BUBBLE);
                if (drowningMusic) {
                    restoreMusic(shell);
                }
            }
        }
        bubbles.removeIf(b -> !b.alive);
        for (Loose.Puff p : puffs) {
            p.update();
        }
        puffs.removeIf(p -> !p.alive());
    }

    /** S3K's Lightning Shield pulls in rings within 64 pixels each way. */
    private boolean magnet(float x, float y) {
        return Math.abs(x - runner.x) < 64 && Math.abs(y - (runner.y - 16)) < 64;
    }

    private void collectRing(Shell shell, float x, float y) {
        ringsInHand++;
        shell.sfx(Sfx.RING);
        puffs.add(new Loose.Puff(Loose.Puff.SPARKLE, x, y, null));
        if (ringsInHand % 2 == 0) {
            shell.game.restoreBySpeed(1);
        }
    }

    private void rock(Shell shell, int i, Chamber.Thing t, float[] me) {
        if (!overlap(me, t.x(), t.y() - 14, 14, 14)) {
            return;
        }
        boolean fire = "fire_shield".equals(shell.game.inventory.selectedId());
        boolean rolling = runner.rolling && Math.abs(runner.speed) >= RuinsRules.BREAK_SPEED || runner.dashing;
        if (!(rolling || fireDash > 0)) {
            return;
        }
        breakRock(shell, i, t, fire);
    }

    private void burnNearbyRock(Shell shell) {
        for (int i = 0; i < chamber.things.size(); i++) {
            Chamber.Thing t = chamber.things.get(i);
            if (!taken[i] && t.type() == Chamber.ROCK && Math.abs(t.x() - runner.x) < 36 && Math.abs(t.y() - runner.y) < 12) {
                breakRock(shell, i, t, true);
                shell.in.consume();
                return;
            }
        }
    }

    private void breakRock(Shell shell, int i, Chamber.Thing t, boolean fire) {
        Game game = shell.game;
        int cost = fire ? RuinsRules.FIRE_BREAK_COST : RuinsRules.ROLL_BREAK_COST;
        if (!game.spend(cost)) {
            String key = number + ":" + i;
            if (!key.equals(outOfMomentum)) {
                outOfMomentum = key;
                shell.toast("OUT OF MOMENTUM: EAT SOMETHING");
                shell.sfx(Sfx.ERROR);
            }
            return;
        }
        taken[i] = true;
        game.xp(starpost.core.Skills.SCRAPPING, 3);
        shell.sfx(fire ? Sfx.FIRE_SHIELD : Sfx.BREAK);
        puffs.add(new Loose.Puff(Loose.Puff.EXPLOSION, t.x(), t.y() - 12, null));
        java.util.Set<String> owned = new java.util.HashSet<>();
        for (String flag : game.flags) {
            owned.add(flag);
        }
        java.util.Set<String> ownedRecords = new java.util.HashSet<>();
        for (Item item : game.catalog.items()) {
            if (RuinsRules.isRecord(item) && (owned.contains(RuinsContent.recordFlag(item.id()))
                    || game.inventory.total(item.id()) > 0)) {
                ownedRecords.add(item.id());
            }
        }
        List<RuinsRules.Drop> drops = new java.util.ArrayList<>(RuinsRules.oreYield(chamber.band, number, fire, rng, ownedRecords));
        String relic = starpost.museum.Finds.ruinsRelic(chamber.band, rng);   // the museum's relics, now and then
        if (relic != null && game.catalog.hasItem(relic)) {
            drops.add(new RuinsRules.Drop(relic, 1));
        }
        int n = 0;
        for (RuinsRules.Drop d : drops) {
            float vx = (n - drops.size() / 2f) * 0.8f;
            pickups.add(new Loose.Pickup(d.id(), d.count(), t.x(), t.y() - 16, vx, -3.2f - n * 0.3f));
            n++;
        }
    }

    private void breakMonitor(Shell shell, Chamber.Thing t) {
        shell.sfx(Sfx.BREAK);
        puffs.add(new Loose.Puff(Loose.Puff.EXPLOSION, t.x(), t.y() - 16, null));
        String prize = t.param() >= 0 && t.param() < chamber.prizes.size() ? chamber.prizes.get(t.param()) : null;
        if (prize != null && !alreadyHave(shell.game, prize)) {
            pickups.add(new Loose.Pickup(prize, 1, t.x(), t.y() - 24, 0, -3.5f));
        } else {
            for (int k = 0; k < 10; k++) {
                ringsInHand++;
            }
            shell.sfx(Sfx.RING);
            puffs.add(new Loose.Puff(Loose.Puff.TEXT, t.x(), t.y() - 40, "+10 RINGS"));
        }
        if (runner.ySpeed > 0) {
            runner.ySpeed = -runner.ySpeed; // bouncing off the monitor (Mon_BreakOpen negates)
        }
    }

    /** One-off finds (Records, Pud's seed) are found only once. */
    private boolean alreadyHave(Game game, String id) {
        if (id.equals("super_sunflower_seeds")) {
            return section.seedFound;
        }
        return RuinsContent.recordSong(id) >= 0 && game.flags.contains(RuinsContent.recordFlag(id));
    }

    private void take(Shell shell, Loose.Pickup p) {
        Game game = shell.game;
        Item item = game.item(p.id);
        int left = game.inventory.add(item, p.count);
        int got = p.count - left;
        if (got > 0) {
            shell.sfx(Sfx.GRAB);
            puffs.add(new Loose.Puff(Loose.Puff.TEXT, p.x, p.y - 12, "+" + got + " " + item.name()));
            if (RuinsContent.recordSong(p.id) >= 0) {
                game.flags.add(RuinsContent.recordFlag(p.id));
                shell.toast("A RECORD! " + item.name());
            }
            if (p.id.equals("super_sunflower_seeds")) {
                section.seedFound = true;
                shell.toast("PUD'S GOLDEN SEED!");
            }
        }
        if (left > 0) {
            shell.toast("NO ROOM FOR " + item.name());
            shell.sfx(Sfx.ERROR);
        }
    }

    private void badniks(Shell shell, float[] me) {
        float cx = runner.x, cy = runner.y - runner.height() / 2f;
        for (Badnik b : badniks) {
            if (!b.alive) {
                continue;
            }
            b.update(chamber, cx, cy, shots);
            if (!b.alive) {
                // The Bomb went off.
                puffs.add(new Loose.Puff(Loose.Puff.EXPLOSION, b.x, b.y, null));
                shell.sfx(SFX_EXPLODE);
                museumPart(shell, b);                        // its fuse, sometimes
                continue;
            }
            harm.clear();
            b.harm(harm);
            for (float[] h : harm) {
                if (overlap(me, h[0], h[1], h[2], h[3])) {
                    hurt(shell, h[0], SFX_DEATH);
                }
            }
            float[] box = b.body();
            if (!overlap(me, box[0], box[1], box[2], box[3])) {
                continue;
            }
            boolean fromAbove = runner.y - runner.height() / 2f < b.y - 8 && runner.ySpeed > 0;
            if (b.bopable() && attacking() && !(b.spikyTop() && fromAbove)) {
                pop(shell, b);
            } else {
                hurt(shell, b.x, SFX_DEATH);
            }
        }
        badniks.removeIf(b -> !b.alive);
    }

    /** React_Enemy: the badnik becomes an explosion and an animal, and Sonic bounces. */
    private void pop(Shell shell, Badnik b) {
        b.alive = false;
        shell.sfx(SFX_EXPLODE);
        puffs.add(new Loose.Puff(Loose.Puff.EXPLOSION, b.x, b.y, null));
        if (!runner.onGround) {
            int yVel = Math.round(runner.ySpeed * 256);
            boolean above = runner.y - runner.height() / 2f < b.y;
            runner.ySpeed = RuinsRules.bopBounce(yVel, above) / 256f;
        }
        String[] kinds = animalsOf(chamber.band);
        String kind = kinds[rng.nextInt(2)];
        animals.add(new Loose.Animal(kind, b.x, b.y + Badnik.halfHeight(b.kind)));
        section.popped++;
        section.freed++;
        shell.game.free();                                   // the animal walks home to the valley
        shell.game.xp(starpost.core.Skills.BOPPING, 10);
        shell.game.restoreBySpeed(RuinsRules.BOP_MOMENTUM);
        if (RuinsRules.dropsScrap(shell.game, rng)) {
            pickups.add(new Loose.Pickup("scrap", 1, b.x, b.y, runner.facingLeft ? 1 : -1, -3f));
        }
        museumPart(shell, b);
    }

    /** Sometimes a badnik leaves its part for the museum's Scrap Collection (starpost.museum.Finds). */
    private void museumPart(Shell shell, Badnik b) {
        String part = starpost.museum.Finds.ruinsPart(b.kind, rng);
        if (part != null && shell.catalog.hasItem(part)) {
            pickups.add(new Loose.Pickup(part, 1, b.x, b.y - 8, runner.facingLeft ? -1 : 1, -3.4f));
        }
    }

    /** Anml_VarIndex: the two animals a zone's badniks hold (Marble: squirrel, seal; Labyrinth: penguin, seal; Scrap Brain: rabbit, chicken). */
    static String[] animalsOf(int band) {
        return switch (band) {
            case RuinsRules.MARBLE -> new String[] {"ricky", "rocky"};
            case RuinsRules.LABYRINTH -> new String[] {"pecky", "rocky"};
            default -> new String[] {"pocky", "cucky"};
        };
    }

    // ------------------------------------------------------------------ hurting and fainting

    /** HurtSonic: rings scatter (or, with none, Sonic faints), knock-back, two seconds of flashing. */
    private void hurt(Shell shell, float fromX, int sfx) {
        if (flash > 0 || faint > 0 || fade > 0) {
            return;
        }
        if (RuinsRules.faintsOnHit(ringsInHand)) {
            shell.sfx(SFX_DEATH);
            startFaint(shell.game.farmer.toUpperCase() + " FAINTED", true);
            return;
        }
        int[][] burst = RuinsRules.ringBurst(ringsInHand);
        float cy = runner.y - runner.height() / 2f;
        for (int[] v : burst) {
            lost.add(new Loose.LostRing(runner.x, cy, v[0], v[1], nextLostIndex++));
        }
        lostTimer = RuinsRules.LOST_RING_FRAMES;
        ringsInHand = 0;
        runner.knockBack(runner.x < fromX);
        flash = RuinsRules.FLASH_FRAMES;
        shell.sfx(sfx);
        shell.sfx(Sfx.RING_LOSS);
    }

    private void startFaint(String reason, boolean losesItems) {
        faint = FAINT_TICKS;
        faintReason = reason;
        faintLosesItems = losesItems;
        runner.onGround = false;
        runner.hurt = false;
        runner.ySpeed = air < 0 ? 0 : -7;   // KillSonic: up at -$700 (drowning sinks instead)
        runner.speed = 0;
        anim.set(air < 0 ? ANIM_DROWN : ANIM_DEATH, 8);
        shell.music.stop();
        if (losesItems) {
            faintLosses = RuinsRules.faint(shell.game, rng);
        } else {
            shell.game.rings += ringsInHand;   // the day ran out: what was carried is kept
        }
        ringsInHand = 0;
    }

    private void updateFaint(Shell shell) {
        faint--;
        runner.y += runner.ySpeed;
        runner.ySpeed = Math.min(8, runner.ySpeed + (air < 0 ? 0.03f : 0x38 / 256f));
        anim.tick();
        if (faint == 0) {
            shell.go(new DayEndScreen(true));
        }
    }

    // ------------------------------------------------------------------ animation and camera

    private void animate() {
        float speed = Math.abs(runner.speed);
        if (runner.hurt) {
            anim.set(ANIM_HURT, 8);
        } else if (getAir > 0) {
            anim.set(ANIM_GET_AIR, 8);
        } else if (runner.flying) {
            anim.set(runner.flyTimer > 0 ? 0x20 : 0x24, runner.flyTimer > 0 ? 1 : 4);   // TAILS_FLY, TAILS_FLY_TIRED
        } else if (runner.gliding) {
            anim.set(0x20, 3);                                                         // Knuckles's glide
        } else if (runner.climbing) {
            anim.set(0x22, 8);                                                         // GLIDE_LAND frames on the wall
        } else if (runner.rolling || !runner.onGround && !runner.sprung) {
            anim.set(Anim.ROLL, Math.max(0, 4 - (int) speed));
        } else if (runner.sprung) {
            anim.set(Anim.SPRING, 2);
        } else if (runner.dashing) {
            anim.set(Anim.SPINDASH, 0);
        } else if (runner.ducking) {
            anim.set(Anim.DUCK, 6);
        } else if (runner.pushing) {
            anim.set(Anim.PUSH, 8);
        } else if (speed > 0.05f) {
            anim.set(speed >= runner.top() ? Anim.RUN : Anim.WALK, Math.max(0, 8 - (int) speed));
        } else {
            anim.set(Anim.WAIT, 6);
        }
        anim.tick();
    }

    private float clampX(float x) {
        return Math.max(0, Math.min(Math.max(0, chamber.width - shell.width()), x));
    }

    /** The camera may look 40 pixels above the chamber, so play along its top edge clears the HUD. */
    private float clampY(float y) {
        return Math.max(-40, Math.min(Math.max(0, chamber.height - shell.height()), y));
    }

    // ------------------------------------------------------------------ debug

    private boolean debugState;

    void debugState() {
        debugState = !debugState;
    }

    void debugRings(int n) {
        ringsInHand = Math.max(0, n);
    }

    /** Debug: a badnik of a kind {@code dx} pixels ahead of Sonic, on the floor there (or level with him). */
    void debugSpawn(int kind, int dx) {
        int x = Math.round(runner.x) + dx;
        int floor = chamber.floorBelow(x, Math.round(runner.y) - 40);
        int[] at = Badnik.walker(kind) && floor < chamber.height ? new int[] {x, floor - Badnik.halfHeight(kind)}
                : new int[] {x, Math.round(runner.y) - 40};
        badniks.add(new Badnik(kind, at[0], at[1]));
    }

    void debugHit() {
        flash = 0;
        hurt(shell, runner.x + (runner.facingLeft ? -10 : 10), SFX_DEATH);
    }

    /** Debug: stand at the exit hatch, the elevator, or the entry. */
    void debugGoto(String where) {
        switch (where) {
            case "exit" -> debugAt(chamber.exitX - 24, chamber.exitY - 8);
            case "elevator" -> debugAt(chamber.elevatorX - 30, chamber.elevatorY - 8);
            case "monitor", "rock" -> {
                int type = where.equals("monitor") ? Chamber.MONITOR : Chamber.ROCK;
                for (Chamber.Thing t : chamber.things) {
                    if (t.type() == type) {
                        debugAt(t.x() - 70, t.y() - 8);
                        return;
                    }
                }
            }
            default -> debugAt(chamber.entryX, chamber.entryY - 8);
        }
    }

    void debugAt(float x, float y) {
        runner.x = x;
        runner.y = chamber.floorBelow(Math.round(x), Math.round(y));
        runner.onGround = true;
        runner.speed = runner.ySpeed = 0;
        camX = clampX(runner.x - shell.width() / 2f);
        camY = clampY(runner.y - 140);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void draw(Shell shell, SceneCanvas canvas) {
        int w = canvas.width(), h = canvas.height();
        int cx = Math.round(camX), cy = Math.round(camY);
        canvas.clear(0x000000);
        drawBackground(canvas, cx, cy);
        drawBlocks(canvas, cx, cy);
        drawLava(canvas, cx, cy);
        drawThings(canvas, cx, cy);
        drawFarmer(canvas, cx, cy);
        drawLoose(canvas, cx, cy);
        drawWaterSurface(canvas, cx, cy);
        drawHud(shell, canvas);
        drawCard(canvas);
        drawFaint(canvas);
        if (fade > 0) {
            int a = fade > FADE / 2 ? (FADE - fade) * 255 / (FADE / 2) : fade * 255 / (FADE / 2);
            canvas.fill(0, 0, w, h, Math.min(255, Math.max(0, a)) << 24);
        }
    }

    /** Marble Zone's lava pools: the ROM's animated surface over its magma, cut to each pool. */
    private void drawLava(SceneCanvas canvas, int cx, int cy) {
        if (chamber.lava.isEmpty()) {
            return;
        }
        int frame = (int) (ticks / 20 % 3);
        SceneImage surface = art.lavaSurface(frame);
        SceneImage magma = art.magma(frame);
        if (surface == null || magma == null) {
            return;
        }
        for (Chamber.Lava l : chamber.lava) {
            int top = l.y() + 10 - 8;          // flames rise 8 pixels over the line Sonic stands on
            int bottom = l.y() + l.h();
            for (int x = l.x() - l.x() % 32; x < l.x() + l.w(); x += 32) {
                int x0 = Math.max(x, l.x()), x1 = Math.min(x + 32, l.x() + l.w());
                if (x1 - cx < 0 || x0 - cx > canvas.width()) {
                    continue;
                }
                canvas.drawRegion(surface, x0 - x, 0, x1 - x0, 16, x0 - cx, top - cy, x1 - x0, 16, SceneDraw.plain());
                for (int y = top + 16; y < bottom; y += 32) {
                    int h = Math.min(32, bottom - y);
                    canvas.drawRegion(magma, x0 - x, 0, x1 - x0, h, x0 - cx, y - cy, x1 - x0, h, SceneDraw.plain());
                }
            }
        }
    }

    private SceneDraw wet(SceneDraw style, float y) {
        return chamber.waterY != Chamber.NO_WATER && y > chamber.waterY ? style.withTint(0xFF92A4FF) : style;
    }

    private void drawBackground(SceneCanvas canvas, int cx, int cy) {
        SceneBackdrop backdrop = art.backdrop(chamber.zone, chamber.act);
        int w = canvas.width(), h = canvas.height();
        if (backdrop != null) {
            int top = Math.max(0, Math.min(backdrop.image().height() - h, Math.round(cy * 0.5f)));
            canvas.drawBackdrop(backdrop, 0, 0, w, h, top, cx * 0.5, ticks);
        }
        canvas.fill(0, 0, w, h, 0x50000000);
        if (chamber.waterY != Chamber.NO_WATER) {
            int wy = chamber.waterY - cy;
            if (wy < h) {
                canvas.fill(0, Math.max(0, wy), w, h - Math.max(0, wy), 0x6000247F);
            }
        }
    }

    private void drawBlocks(SceneCanvas canvas, int cx, int cy) {
        int size = chamber.size;
        for (int r = 0; r < chamber.rows; r++) {
            for (int c = 0; c < chamber.cols; c++) {
                int id = chamber.block(c, r);
                if (id <= 0) {
                    continue;
                }
                int x = c * size - cx, y = r * size - cy;
                if (x > canvas.width() || x + size < 0 || y > canvas.height() || y + size < 0) {
                    continue;
                }
                int split = chamber.waterY == Chamber.NO_WATER ? size : Math.max(0, Math.min(size, chamber.waterY - r * size));
                SceneImage dry = art.block(chamber.zone, chamber.act, id, false);
                if (split >= size) {
                    canvas.draw(dry, x, y);
                } else {
                    SceneImage wet = art.block(chamber.zone, chamber.act, id, true);
                    if (split > 0) {
                        canvas.drawRegion(dry, 0, 0, size, split, x, y, size, split, SceneDraw.plain());
                    }
                    canvas.drawRegion(wet, 0, split, size, size - split, x, y + split, size, size - split,
                            SceneDraw.plain());
                }
            }
        }
    }

    private void drawThings(SceneCanvas canvas, int cx, int cy) {
        SceneDraw plain = SceneDraw.plain();
        // The shaft of light at the entry, the way back up.
        int ex = chamber.entryX - cx;
        for (int i = 0; i < 6; i++) {
            int bw = 22 - i * 3;
            canvas.fill(ex - bw / 2, 0, bw, Math.max(0, chamber.entryY - cy), 0x10FFFFB6);
        }
        if (chamber.exitX >= 0) {
            drawHatch(canvas, chamber.exitX - cx, chamber.exitY - cy);
        }
        if (chamber.elevatorX >= 0) {
            // Map_StarPost: frame 0 is the post with its first ball, 4 with the lit one; it flickers
            // between them for half a second when touched.
            boolean flicker = ticks - litAt < 30 && (ticks / 4) % 2 == 0;
            int frame = elevatorLit && !flicker ? 4 : 0;
            drawSprite(canvas, art.art.starpost, frame, chamber.elevatorX - cx, chamber.elevatorY - cy,
                    wet(plain, chamber.elevatorY - 20));
        }
        for (int i = 0; i < chamber.things.size(); i++) {
            Chamber.Thing t = chamber.things.get(i);
            int x = t.x() - cx, y = t.y() - cy;
            if (taken[i]) {
                if (t.type() == Chamber.MONITOR) {
                    drawSprite(canvas, art.art.monitor, MONITOR_BROKEN, x, y, wet(plain, t.y() - 8));
                }
                continue;
            }
            if (x < -40 || x > canvas.width() + 40 || y < -60 || y > canvas.height() + 60) {
                continue;
            }
            SceneDraw style = wet(plain, t.y() - 8);
            switch (t.type()) {
                case Chamber.RING -> drawRing(canvas, x, y, style);
                case Chamber.ROCK -> {
                    SceneSpriteSet rock = chamber.band == RuinsRules.MARBLE && art.greenBlock() != null
                            ? art.greenBlock() : art.art.purpleRock;
                    drawSprite(canvas, rock, 0, x, y, style);
                }
                case Chamber.SPRING -> drawSprite(canvas, art.art.spring, (int) ticks - springAt < 12 ? 1 : 0, x, y, style);
                case Chamber.SPIKES -> drawSprite(canvas, art.spikes(chamber.band), 0, x, y, style);
                case Chamber.MONITOR -> drawMonitor(canvas, t, x, y, style);
                case Chamber.BUBBLES -> {
                    SceneSpriteSet b = art.bubbles();
                    drawSprite(canvas, b, 0x13 + (int) (ticks / 16 % 3), x, y, plain);
                }
                default -> {
                }
            }
        }
        for (Badnik b : badniks) {
            drawBadnik(canvas, b, cx, cy);
        }
    }

    /** The shaft down: a dark hatch in the floor, an arrow over it. */
    private void drawHatch(SceneCanvas canvas, int x, int y) {
        canvas.fill(x - 14, y - 3, 28, 6, 0xFF000000);
        canvas.fill(x - 15, y - 4, 30, 1, 0xFFB66D24);
        canvas.fill(x - 12, y - 1, 24, 2, 0xFF240000);
        int bob = (int) (ticks / 10 % 2) * 2;
        int ay = y - 30 + bob;
        canvas.fill(x - 2, ay, 4, 8, 0xFFFFDB00);
        canvas.fill(x - 6, ay + 8, 12, 2, 0xFFFFDB00);
        canvas.fill(x - 4, ay + 10, 8, 2, 0xFFFFDB00);
        canvas.fill(x - 2, ay + 12, 4, 2, 0xFFFFDB00);
    }

    private void drawRing(SceneCanvas canvas, float x, float y, SceneDraw style) {
        SceneSpriteSet ring = art.art.ring;
        if (ring == null || ring.frameCount() == 0) {
            return;
        }
        int frame = (int) (ticks / 8 % Math.min(4, ring.frameCount()));
        SceneSprite s = ring.frame(frame);
        canvas.draw(s, x, y, style);
    }

    /**
     * A monitor (Map_Monitor): the ring monitor's own frame, or for a find the static frame with
     * the find's icon on its screen (design doc §6.1: monitors show what is inside).
     */
    private void drawMonitor(SceneCanvas canvas, Chamber.Thing t, int x, int y, SceneDraw style) {
        String prize = t.param() >= 0 && t.param() < chamber.prizes.size() ? chamber.prizes.get(t.param()) : null;
        if (prize == null) {
            drawSprite(canvas, art.art.monitor, MONITOR_RINGS, x, y, style);
            return;
        }
        drawSprite(canvas, art.art.monitor, (int) (ticks / 4 % 3), x, y, style);
        if ((ticks / 4) % 3 != 0) {
            Item item = shell.game.item(prize);
            shell.art.icons.draw(canvas, item, x - 8, y - 29, style);
        }
    }

    private void drawBadnik(SceneCanvas canvas, Badnik b, int cx, int cy) {
        SceneSpriteSet set = art.badnik(b.kind, chamber.band);
        if (set == null || set.frameCount() == 0) {
            return;
        }
        float x = b.x - cx, y = b.y - cy;
        if (x < -64 || x > canvas.width() + 64 || y < -64 || y > canvas.height() + 64) {
            return;
        }
        SceneDraw style = wet(SceneDraw.plain(), b.y).withFlipX(!b.facingLeft);
        int frame = BadnikFrames.frame(b, ticks);
        if (b.kind == Badnik.CATERKILLER) {
            for (int i = 3; i >= 1; i--) {
                float[] s = b.segment(i);
                canvas.draw(set.frame(Math.min(set.frameCount() - 1, BadnikFrames.caterkillerBody(b, i))), s[0] - cx, s[1] - cy, style);
            }
        }
        canvas.draw(set.frame(Math.min(set.frameCount() - 1, frame)), x, y, style);
        if (b.kind == Badnik.ORBINAUT) {
            for (int i = 0; i < 4; i++) {
                float[] p = b.ball(i);
                canvas.draw(set.frame(Math.min(set.frameCount() - 1, BadnikFrames.ORBINAUT_BALL)), p[0] - cx, p[1] - cy, style);
            }
        }
    }

    private void drawLoose(SceneCanvas canvas, int cx, int cy) {
        boolean blink = lostTimer < 60 && (ticks / 2) % 2 == 0;
        for (Loose.LostRing r : lost) {
            if (!blink) {
                drawRing(canvas, r.x - cx, r.y - cy, wet(SceneDraw.plain(), r.y));
            }
        }
        for (Badnik.Shot s : shots) {
            drawShot(canvas, s, cx, cy);
        }
        for (Loose.Animal a : animals) {
            SceneSpriteSet set = art.art.animal(a.name);
            if (set != null && a.frame() < set.frameCount()) {
                SceneSprite sprite = set.frame(a.frame());
                canvas.draw(sprite, a.x - cx, a.y - cy - (sprite.height() - sprite.originY()), wet(SceneDraw.plain(), a.y));
            }
        }
        for (Loose.Pickup p : pickups) {
            Item item = shell.game.item(p.id);
            shell.art.icons.draw(canvas, item, p.x - cx - 8, p.y - cy - 8, SceneDraw.plain());
        }
        for (Loose.Bubble b : bubbles) {
            SceneSpriteSet set = art.bubbles();
            if (set != null && b.frame() < set.frameCount()) {
                canvas.draw(set.frame(b.frame()), b.x - cx, b.y - cy, SceneDraw.plain());
            }
        }
        for (Loose.Puff p : puffs) {
            switch (p.kind) {
                case Loose.Puff.EXPLOSION -> {
                    SceneSpriteSet ex = art.explosion;
                    if (ex != null && ex.frameCount() > 0) {
                        int f = Math.min(ex.frameCount() - 1, p.age / 6);
                        canvas.draw(ex.frame(f), p.x - cx, p.y - cy, SceneDraw.plain());
                    }
                }
                case Loose.Puff.SPARKLE -> {
                    SceneSpriteSet ring = art.art.ring;
                    if (ring != null && ring.frameCount() > 4) {
                        int f = 4 + Math.min(ring.frameCount() - 5, p.age / 4);
                        canvas.draw(ring.frame(f), p.x - cx, p.y - cy, SceneDraw.plain());
                    }
                }
                case Loose.Puff.SPLASH -> {
                    int r = 4 + p.age;
                    canvas.fill(Math.round(p.x - cx - r), Math.round(p.y - cy - 2 - p.age / 2f), r * 2, 2, 0xC0FFFFFF);
                }
                default -> {
                    int tw = canvas.textWidth(p.text);
                    int tx = Math.max(4, Math.min(canvas.width() - 4 - tw, Math.round(p.x - cx - tw / 2f)));
                    Text.shadow(canvas, p.text, tx, Math.max(44, Math.round(p.y - cy)), Text.WHITE);
                }
            }
        }
        // The drowning countdown's numbers over Sonic's head (Map_Bub $D-$12: 0-5).
        int number = RuinsRules.airNumber(air);
        if (runner.underwater && number >= 0 && faint == 0 && !RuinsRules.breathes(shell.game)) {
            int[] frames = {0x0D, 0x12, 0x11, 0x10, 0x0F, 0x0E};
            SceneSpriteSet set = art.bubbles();
            if (set != null && frames[number] < set.frameCount() && airFrames % 60 < 45) {
                canvas.draw(set.frame(frames[number]), runner.x - cx, runner.y - cy - 54, SceneDraw.plain());
            }
        }
    }

    private void drawShot(SceneCanvas canvas, Badnik.Shot s, int cx, int cy) {
        float x = s.x - cx, y = s.y - cy;
        switch (s.kind) {
            case Badnik.Shot.MISSILE -> {
                SceneSpriteSet m = art.missile(chamber.band);
                if (m != null && m.frameCount() > 0) {
                    canvas.draw(m.frame((int) (ticks / 4 % Math.min(2, m.frameCount()))), x, y, SceneDraw.plain().withFlipX(s.vx > 0));
                }
            }
            case Badnik.Shot.CANNONBALL -> {
                SceneSpriteSet hog = art.badnik(Badnik.BALL_HOG, chamber.band);
                if (hog != null && hog.frameCount() > BadnikFrames.CANNONBALL) {
                    canvas.draw(hog.frame(BadnikFrames.CANNONBALL), x, y, SceneDraw.plain());
                }
            }
            case Badnik.Shot.SHRAPNEL -> {
                SceneSpriteSet bomb = art.badnik(Badnik.BOMB, chamber.band);
                if (bomb != null && bomb.frameCount() > 0x0B) {
                    canvas.draw(bomb.frame(0x0A + (int) (ticks / 4 % 2)), x, y, SceneDraw.plain());
                }
            }
            default -> canvas.fill(Math.round(x) - 3, Math.round(y) - 3, 6, 6, 0xFFFFFFFF);
        }
    }

    private void drawFarmer(SceneCanvas canvas, int cx, int cy) {
        if (flash > 0 && (flash & 4) == 0 && faint == 0) {
            return; // the hit's flashing: four frames on, four off
        }
        SceneSpriteSet set = shell.art.farmer(shell.game.farmer);
        SceneSprite pose = anim.pose(set);
        SceneDraw style = wet(SceneDraw.plain(), runner.y - 16).withFlipX(runner.facingLeft);
        if (fireDash > 0) {
            style = style.withFlash(0x80FF6D00);
        }
        float feet = runner.y - cy;
        if (anim.id() == Anim.ROLL) {
            canvas.draw(pose, runner.x - cx, feet - 15, style);
        } else {
            canvas.draw(pose, runner.x - cx, feet - (pose.height() - pose.originY()), style);
        }
    }

    private void drawWaterSurface(SceneCanvas canvas, int cx, int cy) {
        if (chamber.waterY == Chamber.NO_WATER || chamber.waterY < 0) {
            return;
        }
        int y = chamber.waterY - cy;
        if (y < -16 || y > canvas.height() + 16) {
            return;
        }
        SceneSpriteSet surface = art.surface();
        if (surface != null && surface.frameCount() > 0) {
            int frame = (int) (ticks / 8 % Math.min(3, surface.frameCount()));
            SceneSprite s = surface.frame(frame);
            int step = Math.max(16, s.width());
            for (int x = -((cx % step) + step); x < canvas.width() + step; x += step) {
                canvas.draw(s, x + step / 2f, y, SceneDraw.plain());
            }
        } else {
            canvas.fill(0, y, canvas.width(), 2, 0xC0FFFFFF);
        }
    }

    private void drawHud(Shell shell, SceneCanvas canvas) {
        Game game = shell.game;
        // Sonic 1's own HUD, as in the valley: RINGS (carried, flashing red at zero) and TIME,
        // with the banked rings beneath in the menu font.
        boolean warn = ringsInHand == 0 && (ticks / 8) % 2 == 0;
        var hud = shell.art.hud;
        hud.timeRow(canvas, game.calendar.minutes() >= 24 * 60 && (ticks / 8) % 2 == 0,
                game.calendar.minutes() / 60 % 24, game.calendar.minutes() % 60, 8);
        hud.ringsRow(canvas, warn, ringsInHand, 24);
        com.openggf.mods.ui.CompactFont.shadowed(canvas, "BANK " + game.rings, 16, 40, 1, 0xFFB6B6B6, 0xFF000000);
        Text.right(canvas, "CHAMBER " + number, canvas.width() - 8, 6, Text.WHITE);
        int bw = 72, bx = canvas.width() - 8 - bw, by = 19;
        canvas.fill(bx - 1, by - 1, bw + 2, 8, 0xFF000000);
        int fillW = Math.round(bw * game.momentum / (float) game.maxMomentum);
        canvas.fill(bx, by, fillW, 6, game.momentum < game.maxMomentum / 5 ? 0xFFFF4924 : 0xFF24B6FF);
        canvas.fill(bx, by, fillW, 2, 0x60FFFFFF);
        Text.right(canvas, "MOMENTUM", bx - 4, 18, Text.YELLOW);
        PlayScreen.drawHotbar(shell, canvas);
        if (debugState) {
            Text.shadow(canvas, String.format("X%d Y%d SP%.1f YS%.1f %s%s%s%s A%X", Math.round(runner.x), Math.round(runner.y),
                    runner.speed, runner.ySpeed, runner.onGround ? "G" : "A", runner.rolling ? "R" : "", runner.hurt ? "H" : "",
                    runner.underwater ? "W" : "", anim.id()), 8, 44, Text.GREEN);
        }
        if (runner.onGround && faint == 0 && fade == 0) {
            String label = null;
            int lx = 0, ly = 0;
            if (near(chamber.entryX, chamber.entryY, 14)) {
                label = "UP: CLIMB OUT";
                lx = chamber.entryX;
                ly = chamber.entryY;
            } else if (chamber.exitX >= 0 && near(chamber.exitX, chamber.exitY, 14)) {
                label = "DOWN: CHAMBER " + (number + 1);
                lx = chamber.exitX;
                ly = chamber.exitY;
            } else if (chamber.elevatorX >= 0 && near(chamber.elevatorX, chamber.elevatorY, 16)) {
                label = "UP: RIDE THE ELEVATOR";
                lx = chamber.elevatorX;
                ly = chamber.elevatorY;
            }
            if (label != null) {
                int tw = canvas.textWidth(label);
                int x = Math.max(4, Math.min(canvas.width() - 4 - tw, Math.round(lx - camX) - tw / 2));
                Text.shadow(canvas, label, x, Math.max(44, Math.round(ly - camY) - 66), Text.YELLOW);
            }
        }
    }

    /** Sonic 1's title card, quickly: the chamber on a blue band, the zone below. */
    private void drawCard(SceneCanvas canvas) {
        if (card <= 0) {
            return;
        }
        int age = CARD - card;
        int w = canvas.width();
        int slide = age < 12 ? (12 - age) * 24 : card < 12 ? (12 - card) * 24 : 0;
        String title = chamber.landmark != null ? chamber.landmark : "CHAMBER " + number;
        String sub = (chamber.landmark != null ? "CHAMBER " + number + "  " : "") + RuinsRules.bandName(chamber.band);
        int bw = Math.max(canvas.textWidth(title), canvas.textWidth(sub)) + 40;
        canvas.fill(w / 2 - bw / 2 + slide, 80, bw, 20, 0xFF2449DB);
        canvas.fill(w / 2 - bw / 2 + slide, 100, bw, 3, 0xFFFFDB00);
        Text.shadow(canvas, title, w / 2 - canvas.textWidth(title) / 2 + slide, 86, Text.WHITE);
        Text.shadow(canvas, sub, w / 2 - canvas.textWidth(sub) / 2 - slide, 110, Text.YELLOW);
    }

    private void drawFaint(SceneCanvas canvas) {
        if (faint <= 0) {
            return;
        }
        int age = FAINT_TICKS - faint;
        if (age < 40) {
            return;
        }
        int a = Math.min(200, (age - 40) * 8);
        canvas.fill(0, 0, canvas.width(), canvas.height(), a << 24);
        Text.centred(canvas, faintReason, 80, Text.YELLOW);
        if (!faintLosesItems) {
            Text.centred(canvas, "TOO TIRED TO CLIMB OUT", 100, Text.WHITE);
        } else {
            Text.centred(canvas, "THE RINGS IN HAND ARE GONE", 100, Text.WHITE);
            int y = 116;
            for (RuinsRules.Loss loss : faintLosses) {
                Text.centred(canvas, "LOST " + loss.count() + " " + shell.game.item(loss.id()).name(), y, Text.GREY);
                y += 12;
            }
        }
    }

    private static void drawSprite(SceneCanvas canvas, SceneSpriteSet set, int frame, float x, float feet, SceneDraw style) {
        if (set == null || frame < 0 || frame >= set.frameCount()) {
            return;
        }
        SceneSprite sprite = set.frame(frame);
        canvas.draw(sprite, x, feet - (sprite.height() - sprite.originY()), style);
    }
}
