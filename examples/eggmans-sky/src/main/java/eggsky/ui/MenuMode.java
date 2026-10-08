package eggsky.ui;

import com.openggf.mods.scene.SceneCanvas;
import eggsky.Game;
import eggsky.Mode;
import eggsky.core.Controls;
import eggsky.core.Sound;
import eggsky.game.Catalog;
import eggsky.game.Missions;
import eggsky.game.Player;
import eggsky.game.Vitals;
import java.util.ArrayList;
import java.util.List;

/**
 * The pause menu over the surface or cockpit: cargo, the portable refiner, crafting, recharging
 * systems, installed technology, the discovery log, and the galaxy map, saving and quitting.
 * Left/right changes tab, up/down picks a row, confirm acts, back closes.
 */
public final class MenuMode implements Mode {
    private final String[] tabs = {"CARGO", "REFINE", "CRAFT", "RECHARGE", "TECH", "LOG", "SYSTEM"};
    private final Mode back;
    private int tab;
    private int row;
    private int scroll;
    private int confirmDiscard = -1;
    private int age;

    public MenuMode(Mode back) {
        this.back = back;
    }

    @Override
    public boolean live() {
        return false;
    }

    @Override
    public void enter(Game g) {
        g.sound.sfx(Sound.SWITCH);
        if (g.player.tutorial == 2) {
            tab = 1;
        } else if (g.player.tutorial == 3) {
            tab = 2;
        } else if (g.player.tutorial == 4) {
            tab = 3;
        }
    }

    private boolean inSpace() {
        return back instanceof eggsky.space.SpaceMode;
    }

    @Override
    public void update(Game g) {
        age++;
        Controls in = g.in;
        // Enter and pad Start also open menus during flight; here they confirm.
        if (in.backPressed || (in.menuPressed && !in.confirmPressed && age > 2)) {
            g.sound.sfx(Sound.SWITCH);
            g.setMode(back);
            return;
        }
        if (in.leftPressed || in.rightPressed) {
            tab = Math.floorMod(tab + (in.rightPressed ? 1 : -1), tabs.length);
            row = 0;
            scroll = 0;
            confirmDiscard = -1;
            g.sound.sfx(Sound.SWITCH, 3);
        }
        int rows = rowCount(g);
        if (rows > 0) {
            if (in.upRepeat) {
                row = Math.floorMod(row - 1, rows);
                confirmDiscard = -1;
                g.sound.sfx(Sound.PLINK, 2);
            }
            if (in.downRepeat) {
                row = Math.floorMod(row + 1, rows);
                confirmDiscard = -1;
                g.sound.sfx(Sound.PLINK, 2);
            }
            if (in.wheel != 0) {
                row = Math.max(0, Math.min(rows - 1, row - in.wheel));
            }
        }
        int visible = 12;
        if (row < scroll) {
            scroll = row;
        }
        if (row >= scroll + visible) {
            scroll = row - visible + 1;
        }
        if (in.confirmPressed && age > 2) {
            act(g);
        }
    }

    private int rowCount(Game g) {
        Player p = g.player;
        return switch (tab) {
            case 0 -> p.cargo.slots() + 1;
            case 1 -> refinable(g).size();
            case 2 -> g.catalog.recipes().size();
            case 3 -> 5;
            case 4 -> Catalog.TECH_COUNT;
            case 5 -> 0;
            default -> systemOptions().size();
        };
    }

    private List<String> systemOptions() {
        List<String> out = new ArrayList<>();
        out.add("RESUME");
        if (inSpace()) {
            out.add("GALAXY MAP");
        }
        out.add("SAVE");
        out.add("SAVE AND QUIT TO TITLE");
        if (back instanceof eggsky.surface.SurfaceMode) {
            out.add("RENAME THIS PLANET");
        }
        return out;
    }

    private List<Catalog.Refine> refinable(Game g) {
        List<Catalog.Refine> out = new ArrayList<>();
        for (Catalog.Refine r : g.catalog.refines()) {
            if (g.player.cargo.count(r.input()) >= r.inCount()) {
                out.add(r);
            }
        }
        return out;
    }

    private void act(Game g) {
        Player p = g.player;
        switch (tab) {
            case 0 -> {
                if (row == 0) {
                    p.cargo.sort();
                    g.sound.sfx(Sound.SWITCH);
                    return;
                }
                int slot = row - 1;
                if (p.cargo.itemAt(slot) == 0) {
                    return;
                }
                if (confirmDiscard == slot) {
                    g.toast("DISCARDED " + p.cargo.countAt(slot) + " " + g.catalog.name(p.cargo.itemAt(slot)), 0xFFFF8080);
                    p.cargo.set(slot, 0, 0);
                    confirmDiscard = -1;
                    g.sound.sfx(Sound.BREAK);
                } else {
                    confirmDiscard = slot;
                    g.sound.sfx(Sound.ERROR, 2);
                }
            }
            case 1 -> {
                List<Catalog.Refine> list = refinable(g);
                if (row >= list.size()) {
                    return;
                }
                Catalog.Refine r = list.get(row);
                int batches = p.cargo.count(r.input()) / r.inCount();
                // Never refine more than fits.
                int room = p.cargo.room(r.output()) + 0;
                batches = Math.min(batches, Math.max(1, room / Math.max(1, r.outCount())));
                p.cargo.remove(r.input(), batches * r.inCount());
                int left = p.cargo.add(r.output(), batches * r.outCount());
                if (left > 0) {
                    p.cargo.add(r.input(), left * r.inCount() / Math.max(1, r.outCount()));
                }
                g.toast("REFINED " + (batches * r.outCount() - left) + " " + g.catalog.name(r.output()), 0xFF80FFC0);
                g.sound.sfx(Sound.MECHA_SPARK);
            }
            case 2 -> {
                Catalog.Recipe r = g.catalog.recipes().get(row);
                for (int i = 0; i < r.inputs().length; i++) {
                    if (!p.cargo.has(r.inputs()[i], r.counts()[i])) {
                        g.toast("NOT ENOUGH " + g.catalog.name(r.inputs()[i]).toUpperCase(), 0xFFFF6060);
                        g.sound.sfx(Sound.ERROR, 4);
                        return;
                    }
                }
                if (p.cargo.room(r.output()) < r.outCount()) {
                    g.toast("CARGO FULL", 0xFFFF6060);
                    return;
                }
                for (int i = 0; i < r.inputs().length; i++) {
                    p.cargo.remove(r.inputs()[i], r.counts()[i]);
                }
                p.cargo.add(r.output(), r.outCount());
                g.toast("CRAFTED " + g.catalog.name(r.output()).toUpperCase(), 0xFFFFE060);
                g.sound.sfx(Sound.CLANK);
            }
            case 3 -> {
                int[] which = {Vitals.LIFE, Vitals.HAZARD, Vitals.LAUNCH, Vitals.HULL, Vitals.SHIP_SHIELD};
                String msg = Vitals.recharge(p, which[row]);
                if (msg == null) {
                    g.toast(Vitals.current(p, which[row]) >= Vitals.max(p, which[row]) - 0.5f ? "ALREADY FULL"
                            : "NOTHING TO RECHARGE WITH", 0xFFFF8080);
                    g.sound.sfx(Sound.ERROR, 4);
                } else {
                    g.toast(Vitals.name(which[row]).toUpperCase() + " " + msg, 0xFF80FFFF);
                    g.sound.sfx(Sound.SHIELD);
                }
            }
            case 6 -> {
                String option = systemOptions().get(row);
                switch (option) {
                    case "RESUME" -> g.setMode(back);
                    case "GALAXY MAP" -> g.setMode(new eggsky.space.GalaxyMode(back));
                    case "SAVE" -> {
                        g.save();
                        g.toast("SAVED", 0xFF80FF80);
                        g.sound.sfx(Sound.STARPOST);
                    }
                    case "SAVE AND QUIT TO TITLE" -> {
                        back.exit(g);
                        g.save();
                        g.setMode(new TitleMode());
                    }
                    case "RENAME THIS PLANET" -> g.setMode(new NameEntryMode(this,
                            ((eggsky.surface.SurfaceMode) back).planet));
                    default -> {
                    }
                }
            }
            default -> {
            }
        }
    }

    @Override
    public void draw(Game g, SceneCanvas c) {
        back.draw(g, c);
        Ui.dim(c, 0xA0);
        Font f = g.font;
        Player p = g.player;
        int x0 = 12;
        int y0 = 10;
        int w = g.width - 24;
        int h = g.height - 20;
        Ui.panel(c, x0, y0, w, h);
        // Tabs.
        int tx = x0 + 6;
        for (int i = 0; i < tabs.length; i++) {
            int tw = Font.width(tabs[i]) + 10;
            if (i == tab) {
                Ui.focus(c, tx, y0 + 5, tw, 11, g.ticks);
            }
            f.draw(c, tabs[i], tx + 5, y0 + 7, i == tab ? Ui.WHITE : Ui.DIM);
            tx += tw + 3;
        }
        f.right(c, "< >", x0 + w - 6, y0 + 7, Ui.DIM);
        Ui.rule(c, x0 + 6, y0 + 20, w - 12);
        int ly = y0 + 26;
        switch (tab) {
            case 0 -> drawCargo(g, c, x0, ly, w);
            case 1 -> drawRefine(g, c, x0, ly, w);
            case 2 -> drawCraft(g, c, x0, ly, w);
            case 3 -> drawRecharge(g, c, x0, ly, w);
            case 4 -> drawTech(g, c, x0, ly, w);
            case 5 -> drawLog(g, c, x0, ly, w);
            default -> drawSystem(g, c, x0, ly, w);
        }
        f.draw(c, "BACK: X/BACKSPACE", x0 + 6, y0 + h - 10, Ui.DIM);
        f.right(c, Ui.num(p.rings) + " RINGS   " + Ui.num(p.shards) + " SHARDS", x0 + w - 6, y0 + h - 10, Ui.GOLD);
    }

    private void listRow(Game g, SceneCanvas c, int i, int x, int y, int w) {
        if (i == row) {
            Ui.focus(c, x, y - 1, w, 10, g.ticks);
        }
    }

    private void drawCargo(Game g, SceneCanvas c, int x0, int y, int w) {
        Font f = g.font;
        Player p = g.player;
        f.draw(c, "CARGO " + p.cargo.usedSlots() + "/" + p.cargo.slots(), x0 + 8, y, Ui.GOLD);
        y += 11;
        int listW = 190;
        for (int i = scroll; i < Math.min(rowCount(g), scroll + 12); i++) {
            int ry = y + (i - scroll) * 11;
            listRow(g, c, i, x0 + 6, ry, listW);
            if (i == 0) {
                f.draw(c, "[SORT CARGO]", x0 + 10, ry, Ui.CYAN);
                continue;
            }
            int slot = i - 1;
            int id = p.cargo.itemAt(slot);
            if (id == 0) {
                f.draw(c, "- empty -", x0 + 10, ry, 0xFF404868);
                continue;
            }
            Catalog.Item item = g.catalog.item(id);
            c.fill(x0 + 10, ry + 1, 5, 5, item.colour());
            f.draw(c, item.name(), x0 + 19, ry, Ui.WHITE);
            f.right(c, p.cargo.countAt(slot) + "/" + item.stack(), x0 + 6 + listW - 4, ry, Ui.GREY);
        }
        // Details of the selected item.
        int dx = x0 + listW + 16;
        if (row > 0 && p.cargo.itemAt(row - 1) != 0) {
            Catalog.Item item = g.catalog.item(p.cargo.itemAt(row - 1));
            f.draw(c, item.name().toUpperCase(), dx, y, item.colour());
            int ly = y + 12;
            for (String line : Ui.wrap(item.info(), 28)) {
                f.draw(c, line, dx, ly, Ui.GREY);
                ly += 9;
            }
            ly += 4;
            f.draw(c, "VALUE " + Ui.num(item.value()) + " EACH", dx, ly, Ui.GOLD);
            ly += 9;
            Catalog.Refine r = g.catalog.refineFor(item.id());
            if (r != null) {
                f.draw(c, "REFINES: " + r.inCount() + " > " + r.outCount() + " " + g.catalog.name(r.output()), dx, ly, 0xFF80FFC0);
                ly += 9;
            }
            ly += 6;
            f.draw(c, confirmDiscard == row - 1 ? "CONFIRM AGAIN TO DISCARD" : "CONFIRM: DISCARD", dx, ly,
                    confirmDiscard == row - 1 ? Ui.RED : Ui.DIM);
        } else {
            f.draw(c, "Mine, harvest and loot to fill", dx, y, Ui.GREY);
            f.draw(c, "your cargo. Capsules and", dx, y + 9, Ui.GREY);
            f.draw(c, "stations add more slots.", dx, y + 18, Ui.GREY);
        }
    }

    private void drawRefine(Game g, SceneCanvas c, int x0, int y, int w) {
        Font f = g.font;
        Player p = g.player;
        f.draw(c, "PORTABLE REFINER", x0 + 8, y, Ui.GOLD);
        y += 12;
        List<Catalog.Refine> list = refinable(g);
        if (list.isEmpty()) {
            f.draw(c, "Nothing in cargo can be refined yet.", x0 + 10, y, Ui.GREY);
            y += 12;
        }
        for (int i = scroll; i < Math.min(list.size(), scroll + 10); i++) {
            Catalog.Refine r = list.get(i);
            int ry = y + (i - scroll) * 11;
            listRow(g, c, i, x0 + 6, ry, w - 12);
            Catalog.Item in = g.catalog.item(r.input());
            Catalog.Item out = g.catalog.item(r.output());
            c.fill(x0 + 10, ry + 1, 5, 5, in.colour());
            f.draw(c, r.inCount() + " " + in.name(), x0 + 19, ry, Ui.WHITE);
            f.draw(c, ">", x0 + 160, ry, Ui.GOLD);
            c.fill(x0 + 172, ry + 1, 5, 5, out.colour());
            f.draw(c, r.outCount() + " " + out.name(), x0 + 181, ry, out.colour());
            f.right(c, "x" + p.cargo.count(r.input()) / r.inCount(), x0 + w - 12, ry, Ui.GREY);
        }
        f.draw(c, "CONFIRM: REFINE ALL", x0 + 8, y + 116, Ui.DIM);
    }

    private void drawCraft(Game g, SceneCanvas c, int x0, int y, int w) {
        Font f = g.font;
        Player p = g.player;
        f.draw(c, "FABRICATOR", x0 + 8, y, Ui.GOLD);
        y += 12;
        List<Catalog.Recipe> list = g.catalog.recipes();
        for (int i = scroll; i < Math.min(list.size(), scroll + 11); i++) {
            Catalog.Recipe r = list.get(i);
            int ry = y + (i - scroll) * 13;
            listRow(g, c, i, x0 + 6, ry, w - 12);
            Catalog.Item out = g.catalog.item(r.output());
            boolean can = true;
            for (int k = 0; k < r.inputs().length; k++) {
                can &= p.cargo.has(r.inputs()[k], r.counts()[k]);
            }
            c.fill(x0 + 10, ry + 1, 5, 5, out.colour());
            f.draw(c, out.name(), x0 + 19, ry, can ? Ui.WHITE : Ui.DIM);
            int ix = x0 + 140;
            for (int k = 0; k < r.inputs().length; k++) {
                boolean has = p.cargo.has(r.inputs()[k], r.counts()[k]);
                String s = r.counts()[k] + " " + g.catalog.item(r.inputs()[k]).code();
                f.draw(c, s, ix, ry, has ? 0xFF80FF80 : 0xFFFF7070);
                ix += Font.width(s) + 10;
            }
        }
        if (row < list.size()) {
            Catalog.Item out = g.catalog.item(list.get(row).output());
            f.draw(c, out.info(), x0 + 8, y + 150, Ui.GREY);
        }
    }

    private void drawRecharge(Game g, SceneCanvas c, int x0, int y, int w) {
        Font f = g.font;
        Player p = g.player;
        f.draw(c, "RECHARGE SYSTEMS   (KEYS 1-4 WORK WHILE FLYING)", x0 + 8, y, Ui.GOLD);
        y += 14;
        int[] which = {Vitals.LIFE, Vitals.HAZARD, Vitals.LAUNCH, Vitals.HULL, Vitals.SHIP_SHIELD};
        String[] uses = {"Oxygen / Life Support Gel", "Sodium / Ion Battery", "Di-hydrogen, Jelly / Egg Fuel",
                "Ferrite, Pure Ferrite / Plating", "Sodium / Shield Cell"};
        int[] colours = {0xFF60FF80, 0xFFFFC040, 0xFFFF9020, 0xFFFF6040, 0xFF40E8FF};
        for (int i = 0; i < which.length; i++) {
            int ry = y + i * 22;
            listRow(g, c, i, x0 + 6, ry, w - 12);
            float cur = Vitals.current(p, which[i]);
            float max = Vitals.max(p, which[i]);
            f.draw(c, Vitals.name(which[i]).toUpperCase(), x0 + 10, ry, Ui.WHITE);
            Ui.bar(c, x0 + 140, ry + 1, 120, 5, cur / max, colours[i], false);
            f.right(c, Math.round(cur) + "/" + Math.round(max), x0 + w - 12, ry, Ui.GREY);
            f.draw(c, "uses " + uses[i], x0 + 18, ry + 10, Vitals.canRecharge(p, which[i]) ? 0xFF80FF80 : Ui.DIM);
        }
    }

    private void drawTech(Game g, SceneCanvas c, int x0, int y, int w) {
        Font f = g.font;
        Player p = g.player;
        f.draw(c, "INSTALLED TECHNOLOGY (UPGRADE AT AN EGG STATION)", x0 + 8, y, Ui.GOLD);
        y += 12;
        for (int i = scroll; i < Math.min(Catalog.TECH_COUNT, scroll + 12); i++) {
            Catalog.Tech t = g.catalog.tech(i);
            int ry = y + (i - scroll) * 11;
            listRow(g, c, i, x0 + 6, ry, w - 12);
            int lvl = p.level(i);
            f.draw(c, t.name(), x0 + 10, ry, lvl > 0 ? Ui.WHITE : Ui.DIM);
            for (int k = 0; k < t.max(); k++) {
                c.fill(x0 + 130 + k * 7, ry + 1, 5, 5, k < lvl ? Ui.CYAN : 0xFF303850);
            }
            f.draw(c, t.info(), x0 + 180, ry, Ui.GREY);
        }
    }

    private void drawLog(Game g, SceneCanvas c, int x0, int y, int w) {
        Font f = g.font;
        Player p = g.player;
        f.draw(c, "EXPEDITION LOG - GALAXY " + p.galaxyNumber, x0 + 8, y, Ui.GOLD);
        y += 13;
        // Chaos Emeralds.
        f.draw(c, "CHAOS EMERALDS", x0 + 10, y, Ui.WHITE);
        for (int i = 0; i < 7; i++) {
            boolean have = (p.emeralds & (1 << i)) != 0;
            int col = emerald(i);
            int ex = x0 + 110 + i * 16;
            c.fill(ex, y, 10, 8, have ? col : 0xFF303848);
            c.fill(ex + 2, y + 1, 4, 2, have ? 0xFFFFFFFF : 0xFF404858);
        }
        y += 13;
        String signal = p.shrineSystem == Long.MIN_VALUE ? "None. Read echidna ruins to find one."
                : g.galaxy.system(p.shrineSystem) == null ? "?" : g.galaxy.system(p.shrineSystem).name + ", planet "
                + (p.shrinePlanet + 1);
        f.draw(c, "EMERALD SIGNAL: " + signal, x0 + 10, y, 0xFF80FF90);
        y += 13;
        String[][] stats = {
                {"Species discovered", Integer.toString(p.statSpecies)},
                {"Planets visited", Integer.toString(p.statPlanets)},
                {"Systems visited", Integer.toString(p.visitedSystems.size())},
                {"Warps", Integer.toString(p.statWarps)},
                {"Things mined", Integer.toString(p.statMined)},
                {"Heroes repelled", Integer.toString(p.statSonicRepelled)},
                {"Pirates destroyed", Integer.toString(p.statPirates)},
                {"Rings earned", Ui.num(p.statRingsEarned)},
                {"Times rebuilt", Integer.toString(p.statDeaths)},
                {"Distance to core", Ui.num((long) g.system().distanceToCore()) + " ly"},
                {"Time played", (p.playTicks / 3600) + " min"},
        };
        for (String[] s : stats) {
            f.draw(c, s[0], x0 + 10, y, Ui.GREY);
            f.right(c, s[1], x0 + 200, y, Ui.WHITE);
            y += 9;
        }
        drawMission(g, c, x0 + 214, y - 9 * stats.length);
    }

    private void drawMission(Game g, SceneCanvas c, int x, int y) {
        Font f = g.font;
        f.draw(c, "MISSION", x, y, Ui.GOLD);
        String text = Missions.describeActive(g);
        int ly = y + 11;
        for (String line : Ui.wrap(text == null ? "None. Take one at an Egg Station." : text, 26)) {
            f.draw(c, line, x, ly, Ui.WHITE);
            ly += 9;
        }
    }

    private static int emerald(int i) {
        return switch (i) {
            case 0 -> 0xFF40E060;
            case 1 -> 0xFFFFE040;
            case 2 -> 0xFF4080FF;
            case 3 -> 0xFFFF70C0;
            case 4 -> 0xFF40F0F0;
            case 5 -> 0xFFFF4040;
            default -> 0xFFE0E0F0;
        };
    }

    private void drawSystem(Game g, SceneCanvas c, int x0, int y, int w) {
        Font f = g.font;
        List<String> options = systemOptions();
        y += 10;
        for (int i = 0; i < options.size(); i++) {
            int ry = y + i * 16;
            int ow = Font.width(options.get(i)) + 20;
            if (i == row) {
                Ui.focus(c, g.width / 2 - ow / 2, ry - 3, ow, 13, g.ticks);
            }
            f.centre(c, options.get(i), g.width / 2, ry, i == row ? Ui.WHITE : Ui.GREY);
        }
        f.centre(c, "The game also saves at Starposts, landings, launches and docking.", g.width / 2, y + 110, Ui.DIM);
    }
}
