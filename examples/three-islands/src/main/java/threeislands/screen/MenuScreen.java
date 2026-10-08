package threeislands.screen;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import java.util.ArrayList;
import java.util.List;
import threeislands.Game;
import threeislands.art.Font;
import threeislands.art.Heroes;
import threeislands.audio.Audio;
import threeislands.core.Hero;
import threeislands.core.HeroId;
import threeislands.core.Item;
import threeislands.core.Kinds;
import threeislands.core.Progress;
import threeislands.core.Skill;
import threeislands.view.Ui;

/**
 * The party menu over the map or the field: party status, using recovery items and Tails'
 * healing skills outside battle, saving (on the map) and returning to the title.
 */
public final class MenuScreen implements Screen {
    private enum Mode { ROOT, ITEMS, ITEM_TARGET, SKILL_HERO, SKILLS, SKILL_TARGET, CONFIRM_QUIT }

    private final Screen under;
    private final boolean onMap;
    private Mode mode = Mode.ROOT;
    private int root;
    private int itemIndex;
    private int heroIndex;
    private int skillIndex;
    private int target;
    private String message;
    private int messageTicks;
    private long ticks;

    public MenuScreen(Game game, Screen under, boolean onMap) {
        this.under = under;
        this.onMap = onMap;
    }

    @Override
    public String name() {
        return "MENU_" + mode.name();
    }

    private List<String> rootLabels() {
        return List.of("Items", "Skills", onMap ? "Save" : "Save (at Starposts)", "Quit to title", "Close");
    }

    private List<Item> items(Game game) {
        List<Item> out = new ArrayList<>();
        for (Item item : Item.values()) if (game.progress.count(item) > 0) out.add(item);
        return out;
    }

    private List<Skill> skills(Game game, Hero hero) {
        List<Skill> out = new ArrayList<>();
        for (Skill skill : Skill.values()) {
            if (!skill.includes(hero.id)) continue;
            if (skill.isTech()) {
                boolean all = true;
                for (HeroId id : HeroId.values()) if (skill.includes(id) && !game.progress.hasJoined(id)) all = false;
                if (all) out.add(skill);
            } else if (hero.level() >= skill.learnLevel) {
                out.add(skill);
            }
        }
        return out;
    }

    private static boolean fieldUsable(Skill skill) {
        return !skill.isTech() && (skill.effect == Kinds.HEAL || skill.effect == Kinds.REVIVE);
    }

    private static boolean fieldUsable(Item item) {
        return item.effect == Kinds.HEAL || item.effect == Kinds.RESTORE_EP || item.effect == Kinds.REVIVE;
    }

    @Override
    public void update(Game game) {
        ticks++;
        if (under instanceof MapScreen map) map.loadStep(game);
        if (messageTicks > 0) messageTicks--;
        List<Hero> party = game.progress.party();
        switch (mode) {
            case ROOT -> {
                int n = rootLabels().size();
                if (game.controls.up()) root = (root + n - 1) % n;
                if (game.controls.down()) root = (root + 1) % n;
                if (game.controls.back() || game.controls.menu()) {
                    game.swap(under);
                    return;
                }
                if (game.controls.accept()) {
                    switch (root) {
                        case 0 -> {
                            if (items(game).isEmpty()) say(game, "Your bag is empty.");
                            else mode = Mode.ITEMS;
                        }
                        case 1 -> mode = Mode.SKILL_HERO;
                        case 2 -> {
                            if (onMap) {
                                game.progress.setResume(null, 0);
                                game.save();
                                game.audio.sfx(Audio.SFX_STARPOST);
                                say(game, "Game saved.");
                            } else {
                                say(game, "Touch a Starpost to save in a zone.");
                            }
                        }
                        case 3 -> mode = Mode.CONFIRM_QUIT;
                        default -> game.swap(under);
                    }
                }
            }
            case ITEMS -> {
                List<Item> items = items(game);
                if (items.isEmpty()) {
                    mode = Mode.ROOT;
                    return;
                }
                itemIndex = Math.min(itemIndex, items.size() - 1);
                if (game.controls.up()) itemIndex = (itemIndex + items.size() - 1) % items.size();
                if (game.controls.down()) itemIndex = (itemIndex + 1) % items.size();
                if (game.controls.back()) mode = Mode.ROOT;
                else if (game.controls.accept()) {
                    Item item = items.get(itemIndex);
                    if (!fieldUsable(item)) say(game, "Use that in battle.");
                    else if (item.target == Kinds.ALL_ALLIES) useItem(game, item, null);
                    else {
                        mode = Mode.ITEM_TARGET;
                        target = 0;
                    }
                }
            }
            case ITEM_TARGET -> {
                if (game.controls.up() || game.controls.left()) target = (target + party.size() - 1) % party.size();
                if (game.controls.down() || game.controls.right()) target = (target + 1) % party.size();
                if (game.controls.back()) mode = Mode.ITEMS;
                else if (game.controls.accept()) useItem(game, items(game).get(itemIndex), party.get(target));
            }
            case SKILL_HERO -> {
                if (game.controls.up() || game.controls.left()) heroIndex = (heroIndex + party.size() - 1) % party.size();
                if (game.controls.down() || game.controls.right()) heroIndex = (heroIndex + 1) % party.size();
                if (game.controls.back()) mode = Mode.ROOT;
                else if (game.controls.accept()) {
                    mode = Mode.SKILLS;
                    skillIndex = 0;
                }
            }
            case SKILLS -> {
                List<Skill> skills = skills(game, party.get(heroIndex));
                if (game.controls.up()) skillIndex = (skillIndex + skills.size() - 1) % skills.size();
                if (game.controls.down()) skillIndex = (skillIndex + 1) % skills.size();
                if (game.controls.back()) mode = Mode.SKILL_HERO;
                else if (game.controls.accept()) {
                    Skill skill = skills.get(skillIndex);
                    Hero caster = party.get(heroIndex);
                    if (!fieldUsable(skill)) say(game, "That skill is for battle.");
                    else if (caster.ep() < skill.ep) say(game, caster.id.label + " needs " + skill.ep + " EP.");
                    else if (skill.target == Kinds.ALL_ALLIES) useSkill(game, skill, caster, null);
                    else {
                        mode = Mode.SKILL_TARGET;
                        target = 0;
                    }
                }
            }
            case SKILL_TARGET -> {
                if (game.controls.up() || game.controls.left()) target = (target + party.size() - 1) % party.size();
                if (game.controls.down() || game.controls.right()) target = (target + 1) % party.size();
                if (game.controls.back()) mode = Mode.SKILLS;
                else if (game.controls.accept()) {
                    Hero caster = party.get(heroIndex);
                    useSkill(game, skills(game, caster).get(skillIndex), caster, party.get(target));
                }
            }
            case CONFIRM_QUIT -> {
                if (game.controls.back()) mode = Mode.ROOT;
                else if (game.controls.accept()) game.title();
            }
            default -> mode = Mode.ROOT;
        }
    }

    private void say(Game game, String text) {
        message = text;
        messageTicks = 120;
        game.audio.sfx(Audio.SFX_CURSOR);
    }

    private void useItem(Game game, Item item, Hero hero) {
        Progress progress = game.progress;
        List<Hero> targets = hero == null ? progress.party() : List.of(hero);
        boolean helped = false;
        for (Hero h : targets) {
            switch (item.effect) {
                case Kinds.HEAL -> {
                    if (h.hp() > 0 && h.hp() < h.maxHp()) {
                        h.setHp(h.hp() + item.amount);
                        helped = true;
                    }
                }
                case Kinds.RESTORE_EP -> {
                    if (h.ep() < h.maxEp()) {
                        h.setEp(h.ep() + item.amount);
                        helped = true;
                    }
                }
                case Kinds.REVIVE -> {
                    if (h.hp() <= 0) {
                        h.setHp(h.maxHp() * item.amount / 100);
                        helped = true;
                    }
                }
                default -> { }
            }
        }
        if (!helped) {
            say(game, "It wouldn't have any effect.");
            return;
        }
        progress.takeItem(item);
        game.audio.sfx(Audio.SFX_RING);
        message = "Used a " + item.label + ".";
        messageTicks = 90;
        mode = items(game).isEmpty() ? Mode.ROOT : Mode.ITEMS;
    }

    private void useSkill(Game game, Skill skill, Hero caster, Hero hero) {
        List<Hero> targets = hero == null ? game.progress.party() : List.of(hero);
        boolean helped = false;
        for (Hero h : targets) {
            if (skill.effect == Kinds.HEAL && h.hp() > 0 && h.hp() < h.maxHp()) {
                h.setHp(h.hp() + skill.power + 4 * caster.level());
                helped = true;
            } else if (skill.effect == Kinds.REVIVE && h.hp() <= 0) {
                h.setHp(h.maxHp() * skill.power / 100);
                helped = true;
            }
        }
        if (!helped) {
            say(game, "It wouldn't have any effect.");
            return;
        }
        caster.setEp(caster.ep() - skill.ep);
        game.audio.sfx(Audio.SFX_RING);
        message = caster.id.label + " used " + skill.label + ".";
        messageTicks = 90;
        mode = Mode.SKILLS;
    }

    @Override
    public void draw(Game game, SceneCanvas c) {
        under.draw(game, c);
        int w = c.width();
        int h = c.height();
        c.fill(0, 0, w, h, 0x80000010);
        game.ui.window(c, 8, 8, 112, 70);
        game.ui.list(c, 14, 14, 100, 5, rootLabels(), null, root, null, mode == Mode.ROOT ? ticks : 0);
        Progress progress = game.progress;
        game.ui.window(c, 8, 82, 112, 52);
        game.font.draw(c, "~ " + progress.rings() + " rings", 14, 88, Ui.GOLD);
        game.font.draw(c, "Emeralds " + progress.emeraldCount() + "/7", 14, 100, Ui.TEXT);
        long minutes = progress.playTicks() / 3600;
        game.font.draw(c, "Time " + minutes / 60 + ":" + String.format("%02d", minutes % 60), 14, 112, Ui.DIM);
        // Party cards.
        List<Hero> party = progress.party();
        int cardH = 62;
        for (int i = 0; i < party.size(); i++) {
            Hero hero = party.get(i);
            int y = 8 + i * (cardH + 4);
            boolean picked = (mode == Mode.ITEM_TARGET || mode == Mode.SKILL_TARGET) && i == target
                    || (mode == Mode.SKILL_HERO || mode == Mode.SKILLS) && i == heroIndex;
            game.ui.window(c, 126, y, w - 134, cardH, picked ? 0xF02C4C9C : 0xF0182868, 0xF0081030);
            String g = game.heroes.gameFor(progress.island().game, hero.id);
            game.heroes.draw(c, g, hero.id, hero.hp() > 0 ? Heroes.IDLE : Heroes.DOWN, 150, y + 50, false, ticks,
                    SceneDraw.plain(), -1);
            int tx = 178;
            game.font.shadowed(c, hero.id.label, tx, y + 5, Ui.GOLD);
            game.font.draw(c, "Lv " + hero.level(), tx + 64, y + 5, Ui.TEXT);
            game.font.draw(c, "HP", tx, y + 18, Ui.DIM);
            game.ui.bar(c, tx + 16, y + 21, 60, 4, hero.hp(), hero.maxHp(), 0);
            game.font.draw(c, hero.hp() + "/" + hero.maxHp(), tx + 80, y + 18, Ui.TEXT);
            game.font.draw(c, "EP", tx, y + 30, Ui.DIM);
            game.ui.bar(c, tx + 16, y + 33, 60, 4, hero.ep(), hero.maxEp(), Ui.EP);
            game.font.draw(c, hero.ep() + "/" + hero.maxEp(), tx + 80, y + 30, Ui.TEXT);
            game.font.draw(c, "ATK " + hero.atk() + "  DEF " + hero.def() + "  SPD " + hero.spd(), tx, y + 42, Ui.DIM);
            if (hero.level() < Hero.MAX_LEVEL) {
                String next = "Next " + (Hero.xpToNext(hero.level()) - hero.xp());
                game.font.draw(c, next, w - 14 - game.font.width(next), y + 5, Ui.DIM);
            }
        }
        if (mode == Mode.ITEMS || mode == Mode.ITEM_TARGET) {
            List<Item> items = items(game);
            List<String> labels = new ArrayList<>();
            List<String> counts = new ArrayList<>();
            List<Boolean> enabled = new ArrayList<>();
            for (Item item : items) {
                labels.add(item.label);
                counts.add("x" + progress.count(item));
                enabled.add(fieldUsable(item));
            }
            int rows = Math.min(8, Math.max(1, items.size()));
            game.ui.window(c, 20, 40, 150, rows * Font.LINE + 12);
            game.ui.list(c, 26, 46, 138, rows, labels, enabled, Math.min(itemIndex, items.size() - 1), counts,
                    mode == Mode.ITEMS ? ticks : 0);
            if (!items.isEmpty()) footer(game, c, items.get(Math.min(itemIndex, items.size() - 1)).description);
        }
        if (mode == Mode.SKILLS || mode == Mode.SKILL_TARGET) {
            List<Skill> skills = skills(game, party.get(heroIndex));
            List<String> labels = new ArrayList<>();
            List<String> costs = new ArrayList<>();
            List<Boolean> enabled = new ArrayList<>();
            for (Skill skill : skills) {
                labels.add(skill.label);
                costs.add(skill.ep + "EP");
                enabled.add(fieldUsable(skill));
            }
            int rows = Math.min(9, skills.size());
            game.ui.window(c, 20, 40, 170, rows * Font.LINE + 12);
            game.ui.list(c, 26, 46, 158, rows, labels, enabled, skillIndex, costs, mode == Mode.SKILLS ? ticks : 0);
            footer(game, c, skills.get(skillIndex).description);
        }
        if (mode == Mode.CONFIRM_QUIT) {
            game.ui.window(c, w / 2 - 110, h / 2 - 20, 220, 40);
            game.font.centered(c, "Quit to the title? Unsaved progress is lost.", w / 2, h / 2 - 13, Ui.TEXT);
            game.font.centered(c, "A: quit    B: stay", w / 2, h / 2 + 2, Ui.GOLD);
        }
        if (messageTicks > 0 && message != null) footer(game, c, message);
    }

    private void footer(Game game, SceneCanvas c, String text) {
        int w = c.width();
        int h = c.height();
        game.ui.window(c, 8, h - 30, w - 16, 24);
        game.ui.paragraph(c, text, 14, h - 24, w - 30, Ui.TEXT, 1);
    }
}
