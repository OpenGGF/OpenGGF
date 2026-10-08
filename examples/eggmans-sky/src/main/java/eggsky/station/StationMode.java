package eggsky.station;

import com.openggf.mods.scene.SceneBackdrop;
import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneLevelKit;
import com.openggf.mods.scene.SceneSprite;
import eggsky.Game;
import eggsky.Mode;
import eggsky.art.Art;
import eggsky.core.Colour;
import eggsky.core.Controls;
import eggsky.core.Rng;
import eggsky.core.Sound;
import eggsky.game.Catalog;
import eggsky.game.Missions;
import eggsky.game.Player;
import eggsky.ui.Font;
import eggsky.ui.Ui;
import eggsky.world.StarSystem;
import java.util.ArrayList;
import java.util.List;

/**
 * Docked at the Egg Station: an Egg Robo runs the counter. Sell cargo (prices depend on the
 * system's wealth and what it is short of), buy supplies, install technology with Chaos Shards
 * and materials, take and hand in missions, and repair and refuel before launching.
 */
public final class StationMode implements Mode {
    private final String[] tabs = {"SELL", "BUY", "TECH", "JOBS", "REPAIR", "LEAVE"};
    private int tab;
    private int row;
    private int scroll;
    private int age;
    private StarSystem system;
    private SceneBackdrop backdrop;
    private final List<Integer> stock = new ArrayList<>();
    private List<long[]> offers;
    private String quip;
    private int quipTicks;

    @Override
    public boolean live() {
        return false;
    }

    @Override
    public void enter(Game g) {
        system = g.system();
        try {
            SceneLevelKit kit = g.art.s3k().levelKit(11, 0);
            backdrop = kit == null ? null : kit.backdrop();
        } catch (RuntimeException failed) {
            backdrop = null;
        }
        Rng rng = new Rng(Rng.hash(system.seed, 0x53544F43L));
        int[] goods = {Catalog.CARBON, Catalog.FERRITE, Catalog.OXYGEN, Catalog.SODIUM, Catalog.DIHYDROGEN,
                Catalog.PURE_FERRITE, Catalog.CONDENSED_CARBON, Catalog.CHROMATIC_METAL, Catalog.COBALT,
                Catalog.DIHYDROGEN_JELLY, Catalog.MAGNETISED_FERRITE, Catalog.CADMIUM, Catalog.EMERIL, Catalog.INDIUM};
        stock.add(Catalog.WARP_CELL);
        stock.add(Catalog.EGG_FUEL);
        for (int id : goods) {
            if (rng.chance(0.55)) {
                stock.add(id);
            }
        }
        offers = Missions.offers(g, system);
        g.sound.music(Sound.M_COMPETITION);
        Player p = g.player;
        String done = Missions.handIn(g);
        if (done != null) {
            g.banner("MISSION COMPLETE", done, 0xFF80FF80);
            g.sound.sfx(Sound.PERFECT);
        }
        say(pickGreeting(rng));
        if (p.tutorial == 6) {
            g.toast("SELL CARGO, BUY TECH, THEN WARP FROM THE GALAXY MAP", 0xFFFFE060);
            p.tutorial = 99;
        }
        tab = g.player.stationTab;
        restore(g);
        g.save();
    }

    private void remember(Game g) {
        if (tab == tabs.length - 1) return;
        g.player.stationTab = tab;
        g.player.stationRows[tab] = row;
        List<Integer> items = tab == 0 ? sellable(g) : tab == 1 ? stock : List.of();
        g.player.stationItems[tab] = row < items.size() ? items.get(row) : 0;
    }

    private void restore(Game g) {
        row = g.player.stationRows[tab];
        List<Integer> items = tab == 0 ? sellable(g) : tab == 1 ? stock : List.of();
        int index = items.indexOf(g.player.stationItems[tab]);
        if (index >= 0) row = index;
        row = Math.max(0, Math.min(row, rows(g) - 1));
        scroll = Math.max(0, row - 10);
    }

    @Override public void exit(Game g) { remember(g); }

    private static String pickGreeting(Rng rng) {
        String[] lines = {"Welcome back, Doctor! Any loot?", "The Egg Station is at your service, sir!",
                "Doctor! Your moustache looks magnificent today.", "We have warp cells! Very legal ones!",
                "Sonic was here asking about you. We said nothing!", "Please wipe your boots. Hedgehog quills everywhere."};
        return lines[rng.nextInt(lines.length)];
    }

    private void say(String text) {
        quip = text;
        quipTicks = 240;
    }

    private int price(Game g, int id, boolean selling) {
        Catalog.Item item = g.catalog.item(id);
        float factor = 0.75f + 0.2f * system.wealth;
        if (selling && id == system.demand) {
            factor *= 1.6f;
        }
        if (!selling) {
            factor = 1.5f + 0.1f * (2 - system.wealth);
        }
        return Math.max(1, Math.round(item.value() * factor));
    }

    private List<Integer> sellable(Game g) {
        List<Integer> out = new ArrayList<>();
        for (int id : g.catalog.itemIds()) {
            if (g.player.cargo.count(id) > 0) {
                out.add(id);
            }
        }
        return out;
    }

    private int rows(Game g) {
        return switch (tab) {
            case 0 -> sellable(g).size();
            case 1 -> stock.size();
            case 2 -> Catalog.TECH_COUNT;
            case 3 -> offers.size() + 1;
            case 4 -> 4;
            default -> 1;
        };
    }

    @Override
    public void update(Game g) {
        age++;
        Controls in = g.in;
        if (quipTicks > 0) {
            quipTicks--;
        }
        if (in.leftPressed || in.rightPressed) {
            remember(g);
            tab = Math.floorMod(tab + (in.rightPressed ? 1 : -1), tabs.length);
            restore(g);
            g.sound.sfx(Sound.SWITCH, 3);
        }
        int rows = rows(g);
        if (rows > 0) {
            if (in.upRepeat) {
                row = Math.floorMod(row - 1, rows);
                g.sound.sfx(Sound.PLINK, 2);
            }
            if (in.downRepeat) {
                row = Math.floorMod(row + 1, rows);
                g.sound.sfx(Sound.PLINK, 2);
            }
        }
        row = Math.max(0, Math.min(Math.max(0, rows - 1), row));
        if (row < scroll) {
            scroll = row;
        }
        if (row >= scroll + 11) {
            scroll = row - 10;
        }
        if (in.backPressed) {
            remember(g);
            tab = tabs.length - 1;
            row = 0;
            // Backspace is also stock Start: cancel must not confirm Leave on the same edge.
            return;
        }
        if (in.confirmPressed && age > 3) {
            remember(g);
            act(g, g.ctx.keyDown(com.openggf.mods.scene.SceneKeys.LEFT_SHIFT));
            restore(g);
        }
    }

    private void act(Game g, boolean bulk) {
        Player p = g.player;
        switch (tab) {
            case 0 -> {
                List<Integer> list = sellable(g);
                if (row >= list.size()) {
                    return;
                }
                int id = list.get(row);
                int n = p.available(id);
                if (n == 0) {
                    g.toast("STOCK RESERVED - CLEAR IN CARGO MENU", Ui.RED);
                    return;
                }
                int earned = n * price(g, id, true);
                p.cargo.remove(id, n);
                p.rings += earned;
                p.statRingsEarned += earned;
                g.toast("SOLD " + n + " " + g.catalog.name(id).toUpperCase() + " +" + Ui.num(earned), Ui.GOLD);
                g.sound.sfx(Sound.REGISTER);
                if (earned > 5000) {
                    say("Ooh, a big spender! I mean... seller!");
                }
            }
            case 1 -> {
                int id = stock.get(row);
                Catalog.Item item = g.catalog.item(id);
                int n = item.stack() >= 100 ? (bulk ? 100 : 25) : 1;
                int cost = n * price(g, id, false);
                if (p.rings < cost) {
                    g.toast("NOT ENOUGH RINGS", Ui.RED);
                    g.sound.sfx(Sound.ERROR, 4);
                    say("No rings, no goods. Even for you, Doctor.");
                    return;
                }
                if (p.cargo.room(id) < n) {
                    g.toast("CARGO FULL", Ui.RED);
                    g.sound.sfx(Sound.ERROR, 4);
                    return;
                }
                p.rings -= cost;
                p.cargo.add(id, n);
                g.toast("BOUGHT " + n + " " + item.name().toUpperCase(), 0xFF80FF80);
                g.sound.sfx(Sound.REGISTER);
            }
            case 2 -> {
                Catalog.Tech t = g.catalog.tech(row);
                int lvl = p.level(row);
                if (lvl >= t.max()) {
                    g.toast("FULLY UPGRADED", Ui.GREY);
                    return;
                }
                int next = lvl + 1;
                int shards = t.shards() * next;
                int mats = t.count() * next;
                if (p.shards < shards || !p.cargo.has(t.material(), mats)) {
                    g.toast("NEED " + shards + " SHARDS + " + mats + " " + g.catalog.name(t.material()).toUpperCase(),
                            Ui.RED);
                    g.sound.sfx(Sound.ERROR, 4);
                    return;
                }
                p.shards -= shards;
                p.cargo.remove(t.material(), mats);
                p.tech[row] = next;
                p.cargo.resize(p.slots());
                p.refill();
                g.banner("INSTALLED", t.name() + " " + roman(next), 0xFF60E0FF);
                g.sound.sfx(Sound.CLANK);
                say("Installed! It only exploded twice in testing.");
            }
            case 3 -> {
                if (row == 0) {
                    String done = Missions.handIn(g);
                    if (done != null) {
                        g.banner("MISSION COMPLETE", done, 0xFF80FF80);
                        g.sound.sfx(Sound.PERFECT);
                    } else if (Missions.active(p) != null) {
                        g.toast("MISSION NOT FINISHED", Ui.RED);
                        g.sound.sfx(Sound.ERROR, 4);
                    }
                    return;
                }
                if (Missions.active(p) != null) {
                    g.toast("FINISH YOUR CURRENT MISSION FIRST", Ui.RED);
                    g.sound.sfx(Sound.ERROR, 4);
                    return;
                }
                Missions.accept(p, offers.get(row - 1));
                g.toast("MISSION ACCEPTED", 0xFF80FF80);
                g.sound.sfx(Sound.SWITCH);
                say("Splendid! Report back to any Egg Station.");
            }
            case 4 -> service(g);
            default -> {
                g.sound.sfx(Sound.DOOR_CLOSE);
                g.undock();
            }
        }
    }

    private int repairCost(Player p) {
        return Math.round((p.maxHull() - p.hull) * 12);
    }

    private void service(Game g) {
        Player p = g.player;
        switch (row) {
            case 0 -> {
                int cost = repairCost(p);
                if (cost <= 0) {
                    g.toast("HULL ALREADY PERFECT", Ui.GREY);
                } else if (p.rings < cost) {
                    g.toast("NOT ENOUGH RINGS (" + Ui.num(cost) + ")", Ui.RED);
                } else {
                    p.rings -= cost;
                    p.hull = p.maxHull();
                    g.toast("HULL REPAIRED", 0xFF80FF80);
                    g.sound.sfx(Sound.CLANK);
                }
            }
            case 1 -> {
                int cost = Math.round((100 - p.launchFuel) * 18);
                if (cost <= 0) {
                    g.toast("THRUSTERS FULL", Ui.GREY);
                } else if (p.rings < cost) {
                    g.toast("NOT ENOUGH RINGS (" + Ui.num(cost) + ")", Ui.RED);
                } else {
                    p.rings -= cost;
                    p.launchFuel = 100;
                    g.toast("LAUNCH THRUSTERS FILLED", 0xFF80FF80);
                    g.sound.sfx(Sound.SHIELD);
                }
            }
            case 2 -> {
                p.shipShield = p.maxShipShield();
                p.life = p.maxLife();
                p.hazard = p.maxHazard();
                p.pulse = 100;
                g.toast("SHIELDS AND LIFE SUPPORT RECHARGED", 0xFF80FF80);
                g.sound.sfx(Sound.SHIELD);
            }
            default -> {
                int total = 0;
                for (int id : sellable(g)) {
                    Catalog.Item item = g.catalog.item(id);
                    if (item.kind() == Catalog.KIND_VALUABLE) {
                        int n = p.available(id);
                        total += n * price(g, id, true);
                        p.cargo.remove(id, n);
                    }
                }
                if (total > 0) {
                    p.rings += total;
                    p.statRingsEarned += total;
                    g.toast("SOLD VALUABLES +" + Ui.num(total), Ui.GOLD);
                    g.sound.sfx(Sound.REGISTER);
                } else {
                    g.toast("NO VALUABLES IN CARGO", Ui.GREY);
                }
            }
        }
    }

    private static String roman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> "VI";
        };
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void draw(Game g, SceneCanvas c) {
        Player p = g.player;
        Font f = g.font;
        // The station's interior: the Death Egg's background, scrolling slowly.
        if (backdrop != null) {
            c.drawBackdrop(backdrop, 0, 0, g.width, g.height, -20, age * 0.6, age);
        } else {
            c.clear(0x101830);
        }
        // Hangar floor and counter.
        c.fill(0, 150, g.width, 74, 0xFF283040);
        c.fill(0, 150, g.width, 2, 0xFF8090B8);
        for (int x = 0; x < g.width; x += 32) {
            c.fill(x, 152, 1, 72, 0xFF1A2030);
        }
        for (int x = 0; x < g.width; x += 8) {
            c.fill(x, 220, 4, 4, 0xFFE0C020);
        }
        // The Egg Mobile parked on the pad.
        SceneSprite ship = g.art.frame("ship", Art.SHIP_BODY);
        SceneSprite head = g.art.frame("ship", (age / 20) % 2 == 0 ? Art.SHIP_HEAD_IDLE0 : Art.SHIP_HEAD_IDLE1);
        float bob = (float) Math.sin(age * 0.05) * 1.5f;
        if (head != null) {
            c.draw(head, 70, 128 + bob - 0x1C, SceneDraw.plain().withFlipX(true));
        }
        if (ship != null) {
            c.draw(ship, 70, 128 + bob, SceneDraw.plain().withFlipX(true));
        }
        // The Egg Robo shopkeeper.
        // The Egg Robo hovering behind the counter (sub_91988: body frame 1 or 3 with the thruster
        // lit, arm frame 2 at (-$1C, -4), legs frame 5 at (-$C, $1C)).
        SceneSprite body2 = g.art.frame("egg_robo", (age / 2) % 2 == 0 ? 1 : 3);
        SceneSprite arm = g.art.frame("egg_robo", 2);
        SceneSprite legs = g.art.frame("egg_robo", 5);
        float rx = 160;
        float ry = 112 + (float) Math.sin(age * 0.06) * 3;
        if (body2 != null) {
            SceneDraw rs = SceneDraw.plain();
            if (arm != null) {
                c.draw(arm, rx - 0x1C, ry - 4 + (float) Math.sin(age * 0.2) * 2, rs);
            }
            if (legs != null) {
                c.draw(legs, rx - 0xC, ry + 0x1C, rs);
            }
            c.draw(body2, rx, ry, rs);
        }
        c.fill(120, 140, 70, 12, 0xFF505C78);
        c.fill(120, 140, 70, 1, 0xFFA0B0D0);
        if (quipTicks > 0 && quip != null) {
            List<String> lines = Ui.wrap(quip, 22);
            int w = 0;
            for (String line : lines) {
                w = Math.max(w, Font.width(line));
            }
            int bx = 196 - w - 10;
            int by = 60;
            Ui.panel(c, bx, by, w + 10, 8 + lines.size() * 9, 0xF0182850);
            c.fill(bx + w - 6, by + 8 + lines.size() * 9, 6, 4, 0xF0182850);
            int ly = by + 4;
            for (String line : lines) {
                f.draw(c, line, bx + 5, ly, 0xFFFFFFFF);
                ly += 9;
            }
        }
        f.drawBig(c, "EGG STATION", 70, 6, 2, 0xFFFFFFFF, Ui.GOLD, 255);
        f.draw(c, system.name.toUpperCase(), 8, 24, Ui.GREY);
        f.draw(c, system.wealthName().toUpperCase() + " ECONOMY", 8, 33, Ui.GREY);
        f.draw(c, "WANTS: " + g.catalog.name(system.demand).toUpperCase(), 8, 42, 0xFF80FF80);
        // Menu panel.
        int px = 206;
        int py = 6;
        int pw = g.width - px - 6;
        int ph = g.height - 12;
        Ui.panel(c, px, py, pw, ph);
        int tx = px + 5;
        for (int i = 0; i < tabs.length; i++) {
            int tw = Font.width(tabs[i]) + 4;
            if (i == tab) {
                Ui.focus(c, tx - 1, py + 4, tw, 10, g.ticks);
            }
            f.draw(c, tabs[i], tx + 1, py + 6, i == tab ? Ui.WHITE : Ui.DIM);
            tx += tw + 2;
        }
        Ui.rule(c, px + 4, py + 17, pw - 8);
        int ly = py + 22;
        switch (tab) {
            case 0 -> drawSell(g, c, px, ly, pw);
            case 1 -> drawBuy(g, c, px, ly, pw);
            case 2 -> drawTech(g, c, px, ly, pw);
            case 3 -> drawMissions(g, c, px, ly, pw);
            case 4 -> drawServices(g, c, px, ly, pw);
            default -> {
                f.centre(c, "PRESS CONFIRM TO LAUNCH", px + pw / 2, ly + 40, Ui.WHITE);
                f.centre(c, "BACK ALSO SELECTS LAUNCH", px + pw / 2, ly + 52, Ui.DIM);
            }
        }
        Ui.panel(c, 6, g.height - 22, 194, 16, 0xE0081020);
        f.draw(c, Ui.num(p.rings) + " RINGS", 11, g.height - 18, Ui.GOLD);
        f.right(c, Ui.num(p.shards) + " SHARDS", 195, g.height - 18, 0xFF80FFE0);
    }

    private void cursor(Game g, SceneCanvas c, int i, int x, int y, int w) {
        if (i == row) {
            Ui.focus(c, x, y - 1, w, 10, g.ticks);
        }
    }

    private void drawSell(Game g, SceneCanvas c, int px, int y, int pw) {
        Font f = g.font;
        List<Integer> list = sellable(g);
        if (list.isEmpty()) {
            f.draw(c, "Your cargo is empty.", px + 8, y, Ui.GREY);
            return;
        }
        for (int i = scroll; i < Math.min(list.size(), scroll + 11); i++) {
            int id = list.get(i);
            int ry = y + (i - scroll) * 11;
            cursor(g, c, i, px + 4, ry, pw - 8);
            Catalog.Item item = g.catalog.item(id);
            c.fill(px + 8, ry + 1, 5, 5, item.colour());
            f.draw(c, item.name(), px + 16, ry, id == system.demand ? 0xFF80FF80 : Ui.WHITE);
            f.right(c, g.player.available(id) + "x" + price(g, id, true), px + pw - 6, ry, Ui.GOLD);
        }
        f.draw(c, "SELL UNRESERVED STOCK: A/ENTER", px + 8, y + 126, Ui.DIM);
    }

    private void drawBuy(Game g, SceneCanvas c, int px, int y, int pw) {
        Font f = g.font;
        for (int i = scroll; i < Math.min(stock.size(), scroll + 11); i++) {
            int id = stock.get(i);
            int ry = y + (i - scroll) * 11;
            cursor(g, c, i, px + 4, ry, pw - 8);
            Catalog.Item item = g.catalog.item(id);
            c.fill(px + 8, ry + 1, 5, 5, item.colour());
            f.draw(c, item.name(), px + 16, ry, Ui.WHITE);
            f.right(c, Ui.num(price(g, id, false)), px + pw - 6, ry, Ui.GOLD);
        }
        f.draw(c, "CONFIRM: BUY 25 (SHIFT: 100) / 1", px + 8, y + 126, Ui.DIM);
    }

    private void drawTech(Game g, SceneCanvas c, int px, int y, int pw) {
        Font f = g.font;
        Player p = g.player;
        for (int i = scroll; i < Math.min(Catalog.TECH_COUNT, scroll + 11); i++) {
            Catalog.Tech t = g.catalog.tech(i);
            int ry = y + (i - scroll) * 11;
            cursor(g, c, i, px + 4, ry, pw - 8);
            int lvl = p.level(i);
            f.draw(c, t.name(), px + 8, ry, lvl >= t.max() ? Ui.CYAN : Ui.WHITE);
            for (int k = 0; k < t.max(); k++) {
                c.fill(px + pw - 8 - (t.max() - k) * 6, ry + 1, 4, 5, k < lvl ? Ui.CYAN : 0xFF303850);
            }
        }
        Catalog.Tech t = g.catalog.tech(row);
        int lvl = p.level(row);
        int by = y + 124;
        f.draw(c, t.info(), px + 8, by, Ui.GREY);
        if (lvl < t.max()) {
            int next = lvl + 1;
            boolean ok = p.shards >= t.shards() * next && p.cargo.has(t.material(), t.count() * next);
            f.draw(c, (t.shards() * next) + " SHARDS + " + (t.count() * next) + " " + g.catalog.item(t.material()).code(),
                    px + 8, by + 10, ok ? 0xFF80FF80 : 0xFFFF8080);
        } else {
            f.draw(c, "MAXIMUM LEVEL", px + 8, by + 10, Ui.CYAN);
        }
    }

    private void drawMissions(Game g, SceneCanvas c, int px, int y, int pw) {
        Font f = g.font;
        Player p = g.player;
        cursor(g, c, 0, px + 4, y, pw - 8);
        long[] active = Missions.active(p);
        if (active == null) {
            f.draw(c, "NO ACTIVE MISSION", px + 8, y, Ui.DIM);
        } else {
            boolean done = Missions.complete(g, active);
            f.draw(c, done ? "HAND IN MISSION!" : "ACTIVE (" + Missions.progress(g, active) + "/" + active[2] + ")", px + 8, y,
                    done ? 0xFF80FF80 : Ui.GOLD);
        }
        int ly = y + 14;
        for (int i = 0; i < offers.size(); i++) {
            long[] m = offers.get(i);
            cursor(g, c, i + 1, px + 4, ly, pw - 8);
            int line = ly;
            for (String s : Ui.wrap(Missions.describe(g, m), 30)) {
                f.draw(c, s, px + 8, line, Ui.WHITE);
                line += 9;
            }
            f.draw(c, Missions.reward(g, m), px + 8, line, Ui.GOLD);
            ly = line + 14;
        }
    }

    private void drawServices(Game g, SceneCanvas c, int px, int y, int pw) {
        Font f = g.font;
        Player p = g.player;
        String[] names = {"REPAIR HULL", "FILL LAUNCH THRUSTERS", "RECHARGE SHIELD & LIFE", "SELL ALL VALUABLES"};
        String[] costs = {Ui.num(repairCost(p)), Ui.num(Math.round((100 - p.launchFuel) * 18)), "FREE", ""};
        for (int i = 0; i < names.length; i++) {
            int ry = y + i * 14;
            cursor(g, c, i, px + 4, ry, pw - 8);
            f.draw(c, names[i], px + 8, ry, Ui.WHITE);
            f.right(c, costs[i], px + pw - 6, ry, Ui.GOLD);
        }
        Ui.bar(c, px + 8, y + 64, pw - 16, 4, p.hull / p.maxHull(), 0xFFFF6040, false);
        f.draw(c, "HULL", px + 8, y + 70, Ui.GREY);
        Ui.bar(c, px + 8, y + 84, pw - 16, 4, p.launchFuel / 100f, 0xFFFF9020, false);
        f.draw(c, "LAUNCH FUEL", px + 8, y + 90, Ui.GREY);
    }
}
