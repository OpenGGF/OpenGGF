package threeislands.screen;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSpriteSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import threeislands.Game;
import threeislands.art.Font;
import threeislands.art.Heroes;
import threeislands.audio.Audio;
import threeislands.core.Battle;
import threeislands.core.BattleEvent;
import threeislands.core.Combatant;
import threeislands.core.Command;
import threeislands.core.Element;
import threeislands.core.EnemyKind;
import threeislands.core.HeroId;
import threeislands.core.Item;
import threeislands.core.Kinds;
import threeislands.core.Skill;
import threeislands.field.Field;
import threeislands.field.Stage;
import threeislands.view.Ui;

/**
 * A battle, fought on the spot in the zone where the party met the foes. The battle waits
 * completely on each hero's turn; the menu offers Attack, Skills (including Dual and Triple
 * Techs), Items, Guard and Flee, and Super for Sonic with all seven emeralds. Each turn's
 * events then play out in order: lunges, hits with damage numbers, shields, knock-outs and
 * explosions. Holding A hurries the animation.
 */
public final class BattleScreen implements Screen {
    private enum Mode { ANIMATE, COMMAND, SKILL, ITEM, TARGET_FOE, TARGET_ALLY, RESULTS, DONE }

    /** Per-fighter presentation state. */
    private static final class Look {
        double out;
        double outTarget;
        double towardX;
        int flash;
        int hurt;
        int dying = -1;
        int buff;
        int buffColour;
    }

    private record Popup(double x, double y, String text, int colour, int age) {
    }

    private final FieldScreen field;
    private final Field.Spot spot;
    private final Stage stage;
    public final Battle battle;
    private final Map<Combatant, Look> looks = new HashMap<>();
    private final List<Popup> popups = new ArrayList<>();
    private final List<BattleEvent> queue = new ArrayList<>();
    private int eventIndex;
    private int eventAge;
    private Mode mode = Mode.ANIMATE;
    private int command;
    private int skillIndex;
    private int itemIndex;
    private int foeIndex;
    private int allyIndex;
    private Skill pendingSkill;
    private Item pendingItem;
    private boolean pendingAttack;
    private String message = "";
    private long ticks;
    private int resultsAge;
    private int superFlash;
    private final double levelLeft;

    public BattleScreen(Game game, FieldScreen field, Field.Spot spot, List<EnemyKind> group) {
        this.field = field;
        this.spot = spot;
        this.stage = field.stage();
        this.battle = new Battle(game.progress, group, field.zone().level);
        double x = field.field().x();
        stage.look(x + 90, stage.path.heightAt(x), 200, 150, game.width(), game.height(), 1);
        levelLeft = stage.camX();
        for (Combatant c : battle.party) looks.put(c, new Look());
        for (Combatant c : battle.foes) looks.put(c, new Look());
        battle.start();
        load(game);
        music(game);
    }

    @Override
    public String name() {
        return "BATTLE_" + mode.name();
    }

    private void music(Game game) {
        int id = Audio.MUS_MINIBOSS;
        for (Combatant foe : battle.foes) {
            if (foe.kind == EnemyKind.KNUCKLES_RIVAL) id = Audio.MUS_KNUCKLES;
            else if (foe.kind == EnemyKind.CONVERGENCE_ENGINE) id = Audio.MUS_FINAL_BOSS;
            else if (foe.kind.boss && id == Audio.MUS_MINIBOSS) id = Audio.MUS_BOSS;
        }
        game.audio.music("s3k", id);
    }

    // ------------------------------------------------------------------ layout

    /** Screen x of a fighter's home position. */
    private int homeX(Combatant c) {
        if (c.isHero()) return 132 - c.slot * 38;
        int count = battle.foes.size();
        if (count == 1) return c.kind.boss ? 300 - (c.kind.scale - 100) * 3 / 5 : 276;
        return 238 + c.slot * (count > 2 ? 52 : 70);
    }

    private double screenX(Combatant c) {
        Look look = looks.get(c);
        double home = homeX(c);
        return home + (look.towardX - home) * look.out;
    }

    private int floorY(Combatant c) {
        int lead = stage.sy(stage.path.heightAt(levelLeft + 132));
        int y = stage.sy(stage.path.heightAt(levelLeft + homeX(c)));
        return Math.max(lead - 36, Math.min(lead + 24, Math.max(70, Math.min(152, y))));
    }

    // ------------------------------------------------------------------ flow

    private void load(Game game) {
        queue.clear();
        queue.addAll(battle.events());
        eventIndex = 0;
        eventAge = 0;
        mode = Mode.ANIMATE;
        if (!queue.isEmpty()) begin(game, queue.get(0));
    }

    @Override
    public void update(Game game) {
        ticks++;
        game.progress.tick();
        for (Look look : looks.values()) {
            look.out += (look.outTarget - look.out) * 0.28;
            if (look.flash > 0) look.flash--;
            if (look.hurt > 0) look.hurt--;
            if (look.dying >= 0) look.dying++;
            if (look.buff > 0) look.buff--;
        }
        if (superFlash > 0) superFlash--;
        List<Popup> aged = new ArrayList<>();
        for (Popup p : popups) if (p.age() < 50) aged.add(new Popup(p.x(), p.y(), p.text(), p.colour(), p.age() + 1));
        popups.clear();
        popups.addAll(aged);
        switch (mode) {
            case ANIMATE -> animate(game);
            case COMMAND -> commandInput(game);
            case SKILL -> skillInput(game);
            case ITEM -> itemInput(game);
            case TARGET_FOE -> foeInput(game);
            case TARGET_ALLY -> allyInput(game);
            case RESULTS -> results(game);
            default -> { }
        }
    }

    private int duration(BattleEvent e) {
        return switch (e.type()) {
            case BattleEvent.LUNGE -> 16;
            case BattleEvent.HIT, BattleEvent.HEAL, BattleEvent.BLOCK, BattleEvent.EP -> 24;
            case BattleEvent.KO -> 30;
            case BattleEvent.SUPER -> 60;
            case BattleEvent.CHARGE -> 46;
            default -> 40;
        };
    }

    private void animate(Game game) {
        int speed = game.controls.holdAccept() ? 3 : 1;
        if (eventIndex < queue.size()) {
            eventAge += speed;
            if (eventAge >= duration(queue.get(eventIndex))) {
                eventIndex++;
                eventAge = 0;
                if (eventIndex < queue.size()) begin(game, queue.get(eventIndex));
            }
            return;
        }
        // Everyone steps back home before the next turn.
        boolean settled = true;
        for (Look look : looks.values()) {
            look.outTarget = 0;
            if (look.out > 0.03) settled = false;
        }
        if (!settled) return;
        proceed(game);
    }

    private void begin(Game game, BattleEvent e) {
        if (e.text() != null && !e.text().isEmpty()) message = e.text();
        Combatant source = e.source();
        Combatant target = e.target();
        switch (e.type()) {
            case BattleEvent.LUNGE -> {
                for (Look look : looks.values()) look.outTarget = 0;
                if (source != null) {
                    Look look = looks.get(source);
                    double toward;
                    if (target != null) toward = homeX(target) + (source.isHero() ? -30 : 30);
                    else toward = source.isHero() ? 230 : 120;
                    look.towardX = toward;
                    look.outTarget = 1;
                    if (source.isHero() && true) game.audio.sfx(source.hero.id == HeroId.TAILS
                            ? Audio.SFX_JUMP : Audio.SFX_ROLL);
                }
            }
            case BattleEvent.HIT -> {
                Look look = looks.get(target);
                look.flash = 10;
                look.hurt = target.isHero() ? 18 : 0;
                popup(target, String.valueOf(e.amount()), e.critical() ? Ui.GOLD : 0xFFFFFFFF);
                game.audio.sfx(target.isHero() ? Audio.SFX_HURT : Audio.SFX_BOSS_HIT);
            }
            case BattleEvent.HEAL -> {
                popup(target, "+" + e.amount(), Ui.GOOD);
                game.audio.sfx(Audio.SFX_RING);
            }
            case BattleEvent.EP -> {
                popup(target, "+" + e.amount() + " EP", Ui.EP);
                game.audio.sfx(Audio.SFX_RING);
            }
            case BattleEvent.BLOCK -> {
                Look look = looks.get(target);
                look.buff = 24;
                look.buffColour = e.element().color;
                popup(target, "Blocked", 0xFFA0E0FF);
                game.audio.sfx(Audio.SFX_SHIELD);
            }
            case BattleEvent.KO -> {
                if (!target.isHero()) {
                    looks.get(target).dying = 0;
                    game.audio.sfx(Audio.SFX_EXPLODE);
                } else {
                    game.audio.sfx(Audio.SFX_HURT);
                }
            }
            case BattleEvent.REVIVE -> {
                popup(target, "Revived", Ui.GOOD);
                game.audio.sfx(Audio.SFX_RING);
            }
            case BattleEvent.BUFF -> {
                Combatant who = target != null ? target : source;
                if (who != null) {
                    Look look = looks.get(who);
                    look.buff = 30;
                    look.buffColour = e.element() == Element.NONE ? 0xFFFFFFFF : e.element().color;
                }
                game.audio.sfx(switch (e.element()) {
                    case FIRE -> Audio.SFX_FIRE;
                    case WATER -> Audio.SFX_BUBBLE;
                    case ELEC -> Audio.SFX_LIGHTNING;
                    default -> Audio.SFX_SPRING;
                });
            }
            case BattleEvent.CHARGE -> {
                game.audio.sfx(Audio.SFX_SPINDASH);
            }
            case BattleEvent.SUPER -> {
                superFlash = 30;
                game.audio.sfx(Audio.SFX_SUPER);
            }
            default -> { }
        }
    }

    private void popup(Combatant c, String text, int colour) {
        popups.add(new Popup(screenX(c), floorY(c) - (c.isHero() ? 44 : 50), text, colour, 0));
    }

    private void proceed(Game game) {
        switch (battle.phase()) {
            case CHOOSE -> {
                mode = Mode.COMMAND;
                command = 0;
                Combatant actor = battle.actor();
                message = actor.name + "'s turn." + (actor.superForm ? " (Super: -" + Battle.SUPER_DRAIN + " rings a turn)" : "");
            }
            case RESOLVE -> {
                battle.advance();
                load(game);
            }
            case VICTORY -> {
                mode = Mode.RESULTS;
                resultsAge = 0;
                game.audio.jingle(Audio.MUS_ACT_CLEAR);
            }
            case FLED -> {
                mode = Mode.DONE;
                field.afterBattle(game, spot, false);
            }
            case DEFEAT -> {
                mode = Mode.DONE;
                game.go(new GameOverScreen(game));
            }
            default -> { }
        }
    }

    private void submit(Game game, Command command) {
        try {
            battle.submit(command);
        } catch (IllegalArgumentException invalid) {
            game.audio.sfx(Audio.SFX_ERROR);
            message = invalid.getMessage();
            mode = Mode.COMMAND;
            return;
        }
        game.audio.sfx(Audio.SFX_REGISTER);
        load(game);
    }

    // ------------------------------------------------------------------ menus

    private List<String> commands() {
        List<String> out = new ArrayList<>(List.of("Attack", "Skills", "Items", "Guard", "Flee"));
        if (battle.canTransform()) out.add(2, "Super!");
        return out;
    }

    private void commandInput(Game game) {
        List<String> commands = commands();
        if (game.controls.up()) {
            command = (command + commands.size() - 1) % commands.size();
            game.audio.sfx(Audio.SFX_CURSOR);
        }
        if (game.controls.down()) {
            command = (command + 1) % commands.size();
            game.audio.sfx(Audio.SFX_CURSOR);
        }
        if (!game.controls.accept()) return;
        switch (commands.get(command)) {
            case "Attack" -> {
                pendingAttack = true;
                pendingSkill = null;
                pendingItem = null;
                startFoeTarget();
            }
            case "Skills" -> {
                if (battle.availableSkills().isEmpty()) {
                    game.audio.sfx(Audio.SFX_ERROR);
                } else {
                    mode = Mode.SKILL;
                    skillIndex = Math.min(skillIndex, battle.availableSkills().size() - 1);
                }
            }
            case "Super!" -> submit(game, Command.transform());
            case "Items" -> {
                if (items(game).isEmpty()) {
                    game.audio.sfx(Audio.SFX_ERROR);
                    message = "The bag is empty.";
                } else {
                    mode = Mode.ITEM;
                    itemIndex = Math.min(itemIndex, items(game).size() - 1);
                }
            }
            case "Guard" -> submit(game, Command.guard());
            default -> {
                if (!battle.canFlee()) {
                    game.audio.sfx(Audio.SFX_ERROR);
                    message = "You can't run from this fight!";
                } else {
                    submit(game, Command.flee());
                }
            }
        }
    }

    private void startFoeTarget() {
        mode = Mode.TARGET_FOE;
        List<Combatant> foes = battle.livingFoes();
        foeIndex = Math.max(0, Math.min(foeIndex, foes.size() - 1));
    }

    private void skillInput(Game game) {
        List<Skill> skills = battle.availableSkills();
        if (game.controls.up()) skillIndex = (skillIndex + skills.size() - 1) % skills.size();
        if (game.controls.down()) skillIndex = (skillIndex + 1) % skills.size();
        if (game.controls.back()) {
            mode = Mode.COMMAND;
            return;
        }
        if (!game.controls.accept()) return;
        Skill skill = skills.get(skillIndex);
        String reason = battle.unusableReason(skill);
        if (reason != null) {
            game.audio.sfx(Audio.SFX_ERROR);
            message = reason + ".";
            return;
        }
        pendingSkill = skill;
        pendingItem = null;
        pendingAttack = false;
        choose(game, skill.target);
    }

    private List<Item> items(Game game) {
        List<Item> out = new ArrayList<>();
        for (Item item : Item.values()) if (game.progress.count(item) > 0) out.add(item);
        return out;
    }

    private void itemInput(Game game) {
        List<Item> items = items(game);
        if (items.isEmpty()) {
            mode = Mode.COMMAND;
            return;
        }
        if (game.controls.up()) itemIndex = (itemIndex + items.size() - 1) % items.size();
        if (game.controls.down()) itemIndex = (itemIndex + 1) % items.size();
        if (game.controls.back()) {
            mode = Mode.COMMAND;
            return;
        }
        if (!game.controls.accept()) return;
        Item item = items.get(itemIndex);
        if (item.target == Kinds.FALLEN_ALLY && battle.fallen().isEmpty()) {
            game.audio.sfx(Audio.SFX_ERROR);
            message = "Nobody needs reviving.";
            return;
        }
        pendingItem = item;
        pendingSkill = null;
        pendingAttack = false;
        choose(game, item.target);
    }

    private void choose(Game game, int target) {
        switch (target) {
            case Kinds.ENEMY -> startFoeTarget();
            case Kinds.ALLY, Kinds.FALLEN_ALLY -> {
                mode = Mode.TARGET_ALLY;
                allyIndex = 0;
            }
            case Kinds.SELF -> fire(game, battle.actor());
            default -> fire(game, null);
        }
    }

    private List<Combatant> allyChoices() {
        int target = pendingSkill != null ? pendingSkill.target : pendingItem != null ? pendingItem.target : Kinds.ALLY;
        return target == Kinds.FALLEN_ALLY ? battle.fallen() : battle.livingParty();
    }

    private void foeInput(Game game) {
        List<Combatant> foes = battle.livingFoes();
        if (foes.isEmpty()) {
            mode = Mode.COMMAND;
            return;
        }
        if (game.controls.left() || game.controls.up()) {
            foeIndex = (foeIndex + foes.size() - 1) % foes.size();
            game.audio.sfx(Audio.SFX_CURSOR);
        }
        if (game.controls.right() || game.controls.down()) {
            foeIndex = (foeIndex + 1) % foes.size();
            game.audio.sfx(Audio.SFX_CURSOR);
        }
        Combatant foe = foes.get(foeIndex);
        message = foe.name + (foe.scanned ? "  HP " + foe.hp + "/" + foe.maxHp
                + (foe.kind.weakness() != Element.NONE ? "  Weak: " + foe.kind.weakness().label : "") : "");
        if (game.controls.back()) {
            mode = pendingSkill != null ? Mode.SKILL : Mode.COMMAND;
            return;
        }
        if (game.controls.accept()) fire(game, foe);
    }

    private void allyInput(Game game) {
        List<Combatant> allies = allyChoices();
        if (allies.isEmpty()) {
            mode = Mode.COMMAND;
            return;
        }
        allyIndex = Math.min(allyIndex, allies.size() - 1);
        if (game.controls.left() || game.controls.up()) allyIndex = (allyIndex + allies.size() - 1) % allies.size();
        if (game.controls.right() || game.controls.down()) allyIndex = (allyIndex + 1) % allies.size();
        Combatant ally = allies.get(allyIndex);
        message = ally.name + "  HP " + ally.hp + "/" + ally.maxHp + "  EP " + ally.ep + "/" + ally.maxEp;
        if (game.controls.back()) {
            mode = pendingSkill != null ? Mode.SKILL : Mode.ITEM;
            return;
        }
        if (game.controls.accept()) fire(game, ally);
    }

    private void fire(Game game, Combatant target) {
        if (pendingAttack) submit(game, Command.attack(target));
        else if (pendingSkill != null) submit(game, Command.skill(pendingSkill, target));
        else if (pendingItem != null) submit(game, Command.item(pendingItem, target));
    }

    private void results(Game game) {
        resultsAge++;
        if (resultsAge > 30 && (game.controls.accept() || game.controls.back())) {
            mode = Mode.DONE;
            field.afterBattle(game, spot, true);
        }
    }

    /** Debug and test hook: win at once. */
    public void forceVictory(Game game) {
        battle.forceVictory();
        queue.clear();
        eventIndex = 0;
        mode = Mode.ANIMATE;
        for (Look look : looks.values()) look.out = 0;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void draw(Game game, SceneCanvas c) {
        int w = c.width();
        int h = c.height();
        stage.draw(c, ticks);
        c.fill(0, 0, w, h, 0x38000018);
        // Foes, then heroes in front.
        for (Combatant foe : battle.foes) drawFoe(game, c, foe);
        for (int i = battle.party.size() - 1; i >= 0; i--) drawHero(game, c, battle.party.get(i));
        for (Popup p : popups) {
            int y = (int) (p.y() - Math.min(16, p.age() * 0.8));
            int alpha = p.age() < 36 ? 255 : Math.max(0, 255 - (p.age() - 36) * 18);
            int colour = alpha << 24 | (p.colour() & 0xFFFFFF);
            int tw = game.font.width(p.text()) * 2;
            game.font.draw(c, p.text(), (int) p.x() - tw / 2 + 1, y + 1, alpha << 24, 2);
            game.font.draw(c, p.text(), (int) p.x() - tw / 2, y, colour, 2);
        }
        if (superFlash > 0) c.fill(0, 0, w, h, (superFlash * 8) << 24 | 0xFFFFFF);
        // Message and turn order.
        game.ui.window(c, 4, 4, w - 8, 20);
        game.font.draw(c, message, 10, 9, Ui.TEXT);
        turnStrip(game, c, w);
        panel(game, c, w, h);
        if (mode == Mode.RESULTS) drawResults(game, c, w, h);
    }

    private void turnStrip(Game game, SceneCanvas c, int w) {
        List<Combatant> order = battle.turnStrip();
        c.fill(4, 26, w - 8, 12, 0xA0000018);
        int x = 8;
        game.font.shadowed(c, "Round " + Math.max(1, battle.round()), x, 28, Ui.DIM);
        x += 52;
        for (int i = 0; i < order.size() && x < w - 40; i++) {
            Combatant who = order.get(i);
            String label = who.name.length() > 9 ? who.name.substring(0, 9) : who.name;
            int colour = who.isHero() ? 0xFF80C0FF : 0xFFFF9080;
            if (i == 0 && mode != Mode.ANIMATE) colour = Ui.GOLD;
            game.font.shadowed(c, label, x, 28, colour);
            x += game.font.width(label) + 4;
            if (i < order.size() - 1) {
                game.font.shadowed(c, ">", x, 28, Ui.DIM);
                x += 8;
            }
        }
    }

    private void drawHero(Game game, SceneCanvas c, Combatant hero) {
        Look look = looks.get(hero);
        double x = screenX(hero);
        int feet = floorY(hero);
        int pose = Heroes.IDLE;
        if (!hero.alive()) pose = Heroes.DOWN;
        else if (look.hurt > 0) pose = Heroes.HURT;
        else if (look.out > 0.1) pose = hero.hero.id == HeroId.TAILS ? Heroes.RUN : Heroes.ROLL;
        SceneDraw style = SceneDraw.plain();
        if (look.flash > 0 && look.flash % 4 < 2) style = style.withFlash(0xC0FFFFFF);
        if (hero.guarding && hero.alive()) style = style.withTint(0xFFB0C8FF);
        String g = game.heroes.gameFor(field.zone().game, hero.hero.id);
        int superEntry = hero.superForm ? 6 + (int) (ticks / 7 % 3) : -1;
        game.heroes.draw(c, g, hero.hero.id, pose, x, feet, false, ticks, style, superEntry);
        if (hero.shield != null && hero.alive()) ring(c, x, feet - 18, 18, hero.shield.color, ticks);
        if (hero.invincible > 0 && hero.alive()) sparkles(c, x, feet - 18, ticks);
        if (look.buff > 0) ring(c, x, feet - 18, 22 + (30 - look.buff) / 2, look.buffColour, ticks);
        if (battle.actor() == hero && (mode != Mode.ANIMATE) && mode != Mode.RESULTS) {
            game.ui.pointer(c, (int) x, feet - 52, ticks);
        }
        if (mode == Mode.TARGET_ALLY) {
            List<Combatant> allies = allyChoices();
            if (!allies.isEmpty() && allies.get(Math.min(allyIndex, allies.size() - 1)) == hero) {
                game.ui.pointer(c, (int) x, feet - 52, ticks);
            }
        }
    }

    private void drawFoe(Game game, SceneCanvas c, Combatant foe) {
        Look look = looks.get(foe);
        if (!foe.alive() && (look.dying < 0 || look.dying > 40)) return;
        double x = screenX(foe);
        int feet = floorY(foe);
        SceneDraw style = SceneDraw.plain();
        if (look.flash > 0 && look.flash % 4 < 2) style = style.withFlash(0xD0FFFFFF);
        else if (foe.charging) style = style.withFlash(((int) (100 + 80 * Math.sin(ticks / 4.0)) << 24) | 0xFF3030);
        else if (foe.defDown > 0) style = style.withTint(0xFFD0A0FF);
        if (field.zone().tier == 9 && !foe.kind.boss) style = style.withTint(0xFFE0B0FF);
        if (look.dying >= 0) style = style.withAlpha(Math.max(0, 1f - look.dying / 30f));
        int height = game.enemies.draw(c, foe.kind, x, feet, ticks, style);
        if (look.dying >= 0 && look.dying < 30) {
            SceneSpriteSet boom = game.art.sprites("s3k:explosion");
            if (boom != null) c.draw(boom.frame(Math.min(boom.frameCount() - 1, look.dying / 6)), (float) x,
                    feet - height / 2f, SceneDraw.plain());
        }
        if (foe.alive()) {
            int bw = Math.max(24, Math.min(60, foe.kind.boss ? 60 : 30));
            game.ui.bar(c, (int) x - bw / 2, feet + 3, bw, 3, foe.hp, foe.maxHp, Ui.BAD);
        }
        if (mode == Mode.TARGET_FOE) {
            List<Combatant> foes = battle.livingFoes();
            if (!foes.isEmpty() && foes.get(Math.min(foeIndex, foes.size() - 1)) == foe) {
                game.ui.pointer(c, (int) x, feet - height - 12, ticks);
            }
        }
    }

    private static void ring(SceneCanvas c, double x, double y, int radius, int colour, long ticks) {
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6 + ticks * 0.08;
            int px = (int) Math.round(x + Math.cos(a) * radius);
            int py = (int) Math.round(y + Math.sin(a) * radius);
            c.fill(px - 1, py - 1, 3, 3, (0xA0 << 24) | (colour & 0xFFFFFF));
        }
    }

    private static void sparkles(SceneCanvas c, double x, double y, long ticks) {
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2 + ticks * 0.2;
            c.fill((int) (x + Math.cos(a) * 16), (int) (y + Math.sin(a) * 22), 2, 2, 0xFFFFFFFF);
        }
    }

    private void panel(Game game, SceneCanvas c, int w, int h) {
        int top = h - 62;
        game.ui.window(c, 4, top, 128, 58);
        boolean choosing = mode == Mode.COMMAND || mode == Mode.SKILL || mode == Mode.ITEM
                || mode == Mode.TARGET_FOE || mode == Mode.TARGET_ALLY;
        if (choosing && battle.actor() != null) {
            List<String> commands = commands();
            List<Boolean> enabled = new ArrayList<>();
            for (String label : commands) enabled.add(!label.equals("Flee") || battle.canFlee());
            game.ui.list(c, 10, top + 4, 116, 5, commands, enabled, command, null, mode == Mode.COMMAND ? ticks : 0);
        } else {
            game.font.draw(c, battle.bossBattle ? "Boss battle!" : "Battle", 12, top + 6, battle.bossBattle ? Ui.BAD : Ui.DIM);
            game.font.draw(c, "Hold A: faster", 12, top + 40, Ui.DIM);
        }
        // Party status.
        int px = 136;
        game.ui.window(c, px, top, w - px - 4, 58);
        for (int i = 0; i < battle.party.size(); i++) {
            Combatant hero = battle.party.get(i);
            int y = top + 5 + i * 17;
            boolean acting = battle.actor() == hero && choosing;
            if (acting) c.fill(px + 3, y - 2, w - px - 10, 16, 0x603060C0);
            game.font.draw(c, hero.name, px + 8, y, hero.alive() ? (acting ? Ui.GOLD : Ui.TEXT) : Ui.BAD);
            game.ui.bar(c, px + 62, y + 2, 70, 4, hero.hp, hero.maxHp, 0);
            game.font.draw(c, hero.hp + "/" + hero.maxHp, px + 136, y, Ui.TEXT);
            game.ui.bar(c, px + 62, y + 8, 70, 2, hero.ep, hero.maxEp, Ui.EP);
            game.font.draw(c, hero.ep + "EP", px + 196, y, Ui.EP);
            String tags = tags(hero);
            if (!tags.isEmpty()) game.font.draw(c, tags, px + 62, y + 9 - 1 + 1, Ui.DIM);
        }
        if (mode == Mode.SKILL) drawSkills(game, c, w, top);
        if (mode == Mode.ITEM) drawItems(game, c, w, top);
        String rings = "~ " + game.progress.rings();
        game.font.shadowed(c, rings, w - 8 - game.font.width(rings), 28, Ui.GOLD);
    }

    private static String tags(Combatant hero) {
        StringBuilder out = new StringBuilder();
        if (hero.superForm) out.append("SUPER ");
        if (hero.haste > 0) out.append("Fast ");
        if (hero.shield != null) out.append(hero.shield.label).append(" ");
        if (hero.taunt > 0) out.append("Guardian ");
        if (hero.defDown > 0) out.append("DEF- ");
        return out.toString().trim();
    }

    private void drawSkills(Game game, SceneCanvas c, int w, int top) {
        List<Skill> skills = battle.availableSkills();
        List<String> labels = new ArrayList<>();
        List<String> costs = new ArrayList<>();
        List<Boolean> enabled = new ArrayList<>();
        for (Skill skill : skills) {
            labels.add(skill.label);
            costs.add(skill.ep + "EP" + (skill.isTech() ? "x" + Integer.bitCount(skill.members) : ""));
            enabled.add(battle.unusableReason(skill) == null);
        }
        int rows = Math.min(7, skills.size());
        int height = rows * Font.LINE + 10;
        int y = top - height - 26;
        game.ui.window(c, 4, y, 190, height);
        game.ui.list(c, 10, y + 5, 178, rows, labels, enabled, skillIndex, costs, ticks);
        Skill skill = skills.get(skillIndex);
        String reason = battle.unusableReason(skill);
        game.ui.window(c, 4, top - 24, w - 8, 22);
        game.font.draw(c, reason == null ? skill.description : reason + ".", 10, top - 18, reason == null ? Ui.TEXT : Ui.BAD);
    }

    private void drawItems(Game game, SceneCanvas c, int w, int top) {
        List<Item> items = items(game);
        if (items.isEmpty()) return;
        List<String> labels = new ArrayList<>();
        List<String> counts = new ArrayList<>();
        for (Item item : items) {
            labels.add(item.label);
            counts.add("x" + game.progress.count(item));
        }
        int rows = Math.min(7, items.size());
        int height = rows * Font.LINE + 10;
        int y = top - height - 26;
        game.ui.window(c, 4, y, 170, height);
        game.ui.list(c, 10, y + 5, 158, rows, labels, null, Math.min(itemIndex, items.size() - 1), counts, ticks);
        game.ui.window(c, 4, top - 24, w - 8, 22);
        game.font.draw(c, items.get(Math.min(itemIndex, items.size() - 1)).description, 10, top - 18, Ui.TEXT);
    }

    private void drawResults(Game game, SceneCanvas c, int w, int h) {
        List<String> lines = new ArrayList<>();
        lines.add("Experience +" + battle.xpReward());
        lines.add("Rings +" + battle.ringReward());
        for (Item item : battle.drops()) lines.add("Found a " + item.label + "!");
        lines.addAll(battle.levelUps());
        int height = 30 + lines.size() * Font.LINE;
        int y = (h - height) / 2 - 10;
        game.ui.window(c, 40, y, w - 80, height);
        game.font.centered(c, "VICTORY!", w / 2, y + 6, Ui.GOLD);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int colour = line.contains("level") ? Ui.GOOD : Ui.TEXT;
            game.ui.paragraph(c, line, 52, y + 20 + i * Font.LINE, w - 104, colour, 1);
        }
    }
}
