package sonicbros.ui;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;
import sonicbros.input.Controls;
import sonicbros.sim.Pad;
import sonicbros.view.Costumes;

/**
 * Character select, the familiar way: every person has their own cursor (press a button on
 * any controller to join), points at a fighter and presses attack. Below sit the four player
 * panels; point at a panel's tab to add or remove a CPU, at its arrows to change colours, CPU
 * level or team. START (or the READY banner) goes on to the stage select once two or more
 * fighters are in.
 */
public final class SelectScreen implements Screen {
    private static final int PANEL_W = 92;
    private static final int PANEL_H = 120;
    private static final int PANEL_Y = 84;
    private static final int CELL = 44;
    private static final int CELL_Y = 30;
    private final Game g;
    private final String opener;
    private long t;
    private final double[] speed = new double[4];
    private int mouseSlot = -1;

    public SelectScreen(Game g, String opener) {
        this.g = g;
        this.opener = opener;
    }

    @Override public void enter() {
        g.music(Game.MUS_SELECT);
        g.voice("ann-choose-fighter");
        boolean anyone = false;
        for (Slot s : g.slots) if (s.active()) anyone = true;
        if (!anyone) {
            Slot p1 = g.slots[0];
            p1.kind = Slot.Kind.HUMAN;
            p1.device = opener == null ? (g.controls.devices().isEmpty() ? Controls.LOGICAL_1 : g.controls.devices().get(0)) : opener;
            p1.fighter = Slot.NONE;
            Slot cpu = g.slots[1];
            cpu.kind = Slot.Kind.CPU;
            cpu.fighter = 2;
            cpu.cpuLevel = 5;
            cpu.team = 1;
        }
        for (int i = 0; i < 4; i++) {
            Slot s = g.slots[i];
            if (s.kind == Slot.Kind.HUMAN) {
                s.cursorX = panelX(i) + PANEL_W / 2.0;
                s.cursorY = CELL_Y + CELL + 10;
            }
        }
    }

    private int cells() { return g.roster.all().size() + 1; }

    private int cellX(int i) { return g.width() / 2 - cells() * CELL / 2 + i * CELL; }

    private int panelX(int i) { return g.width() / 2 - (4 * PANEL_W + 3 * 6) / 2 + i * (PANEL_W + 6); }

    private boolean ready() {
        int chosen = 0;
        int firstTeam = -1;
        boolean twoTeams = false;
        for (Slot s : g.slots) {
            if (s.kind == Slot.Kind.HUMAN && s.fighter == Slot.NONE) return false;
            if (s.chosen()) {
                chosen++;
                if (firstTeam < 0) firstTeam = s.team; else if (s.team != firstTeam) twoTeams = true;
            }
        }
        return chosen >= 2 && (!g.rules.teams || twoTeams);
    }

    @Override public void update() {
        t++;
        joinNewDevices();
        mouse();
        if (g.ctx.keyPressed(com.openggf.mods.scene.SceneKeys.TAB)) toggleCpu();
        for (int i = 0; i < 4; i++) {
            Slot s = g.slots[i];
            if (s.kind != Slot.Kind.HUMAN) continue;
            Pad pad = g.controls.pad(s.device);
            int dx = pad.x();
            int dy = pad.y();
            if (dx != 0 || dy != 0) speed[i] = Math.min(4.2, speed[i] + 0.18); else speed[i] = 1.8;
            s.cursorX = Math.max(4, Math.min(g.width() - 4, s.cursorX + dx * speed[i]));
            s.cursorY = Math.max(4, Math.min(g.height() - 4, s.cursorY + dy * speed[i]));
            if (pad.hit(Pad.ATTACK | Pad.JUMP)) click(i, s.cursorX, s.cursorY);
            if (pad.hit(Pad.SHIELD) && s.fighter != Slot.NONE) cycleCostume(i, 1);
            if (pad.hit(Pad.SPECIAL)) back(i);
            if (g.screen != this || g.fading()) return;
            if (pad.hit(Pad.START) && ready()) { start(); return; }
        }
    }

    private void joinNewDevices() {
        for (String d : g.controls.devices()) {
            if (!g.controls.pressed(d, Pad.ATTACK | Pad.START | Pad.JUMP)) continue;
            boolean taken = false;
            for (Slot s : g.slots) if (s.kind == Slot.Kind.HUMAN && d.equals(s.device)) taken = true;
            if (taken) continue;
            int target = -1;
            for (int i = 0; i < 4 && target < 0; i++) if (g.slots[i].kind == Slot.Kind.OFF) target = i;
            for (int i = 3; i >= 0 && target < 0; i--) if (g.slots[i].kind == Slot.Kind.CPU) target = i;
            if (target < 0) return;
            Slot s = g.slots[target];
            s.kind = Slot.Kind.HUMAN;
            s.device = d;
            s.fighter = Slot.NONE;
            s.team = target % 2;
            s.cursorX = panelX(target) + PANEL_W / 2.0;
            s.cursorY = CELL_Y + CELL + 10;
            g.sfx(Game.SFX_JOIN);
            // The press that joined should not also pick.
            return;
        }
    }

    private void mouse() {
        var m = g.ctx.mouse();
        if (m == null || !m.lastInputWasMouse() || !(m.moved() || m.leftPressed() || m.rightPressed())) return;
        int owner = -1;
        for (int i = 0; i < 4 && owner < 0; i++) if (g.slots[i].kind == Slot.Kind.HUMAN) owner = i;
        if (owner < 0) return;
        mouseSlot = owner;
        g.slots[owner].cursorX = m.x();
        g.slots[owner].cursorY = m.y();
        if (m.leftPressed()) click(owner, m.x(), m.y());
        if (m.rightPressed()) back(owner);
    }

    private void click(int who, double x, double y) {
        Slot me = g.slots[who];
        // Roster cells.
        if (y >= CELL_Y && y < CELL_Y + CELL) {
            for (int i = 0; i < cells(); i++) {
                if (x >= cellX(i) && x < cellX(i) + CELL) {
                    me.fighter = i == cells() - 1 ? Slot.RANDOM : i;
                    me.costume = freeCostume(who, me.fighter, me.costume);
                    g.sfx(Game.SFX_ACCEPT);
                    announceFighter(me.fighter);
                    return;
                }
            }
        }
        // Ready banner.
        if (ready() && y >= g.height() - 18) { start(); return; }
        // Panels.
        for (int i = 0; i < 4; i++) {
            int px = panelX(i);
            if (x < px || x >= px + PANEL_W || y < PANEL_Y || y >= PANEL_Y + PANEL_H) continue;
            Slot s = g.slots[i];
            double ly = y - PANEL_Y;
            boolean left = x < px + PANEL_W / 2.0;
            if (ly < 14) {
                if (g.rules.teams && x > px + PANEL_W - 16 && s.active()) { s.team = (s.team + 1) % 3; g.sfx(Game.SFX_MOVE); return; }
                if (s.kind == Slot.Kind.CPU) { s.kind = Slot.Kind.OFF; g.sfx(Game.SFX_BACK); }
                else if (s.kind == Slot.Kind.OFF) {
                    s.kind = Slot.Kind.CPU;
                    s.fighter = (int) ((t * 7 + i * 3) % g.roster.all().size());
                    s.costume = freeCostume(i, s.fighter, 0);
                    s.team = i % 2;
                    g.sfx(Game.SFX_JOIN);
                }
                return;
            }
            if (!s.active()) return;
            if (ly >= 96 && ly < 108) { cycleCostume(i, left ? -1 : 1); return; }
            if (ly >= 108 && s.kind == Slot.Kind.CPU) {
                s.cpuLevel = Math.max(1, Math.min(9, s.cpuLevel + (left ? -1 : 1)));
                g.sfx(Game.SFX_MOVE);
                return;
            }
            if (s.kind == Slot.Kind.CPU && ly < 96) {
                int n = g.roster.all().size();
                s.fighter = s.fighter == Slot.RANDOM ? 0 : s.fighter + 1 >= n ? Slot.RANDOM : s.fighter + 1;
                s.costume = freeCostume(i, s.fighter, s.costume);
                g.sfx(Game.SFX_MOVE);
            }
            return;
        }
    }

    /** Tab: adds a CPU to the first empty slot, or removes the last CPU when all are full. */
    private void toggleCpu() {
        for (int i = 0; i < 4; i++) {
            Slot s = g.slots[i];
            if (s.kind == Slot.Kind.OFF) {
                s.kind = Slot.Kind.CPU;
                s.fighter = (int) ((t * 7 + i * 3) % g.roster.all().size());
                s.costume = freeCostume(i, s.fighter, 0);
                s.team = i % 2;
                g.sfx(Game.SFX_JOIN);
                return;
            }
        }
        for (int i = 3; i >= 0; i--) {
            if (g.slots[i].kind == Slot.Kind.CPU) { g.slots[i].kind = Slot.Kind.OFF; g.sfx(Game.SFX_BACK); return; }
        }
    }

    private void cycleCostume(int slot, int d) {
        Slot s = g.slots[slot];
        if (s.fighter == Slot.NONE) return;
        for (int k = 0; k < Costumes.COUNT; k++) {
            s.costume = Math.floorMod(s.costume + d, Costumes.COUNT);
            if (!costumeTaken(slot, s.fighter, s.costume)) break;
        }
        g.sfx(Game.SFX_MOVE);
    }

    private boolean costumeTaken(int slot, int fighter, int costume) {
        if (fighter < 0) return false;
        for (int i = 0; i < 4; i++) {
            Slot o = g.slots[i];
            if (i != slot && o.active() && o.fighter == fighter && o.costume == costume) return true;
        }
        return false;
    }

    private int freeCostume(int slot, int fighter, int preferred) {
        for (int k = 0; k < Costumes.COUNT; k++) {
            int c = (preferred + k) % Costumes.COUNT;
            if (!costumeTaken(slot, fighter, c)) return c;
        }
        return preferred;
    }

    private void back(int who) {
        Slot me = g.slots[who];
        if (me.fighter != Slot.NONE) {
            me.fighter = Slot.NONE;
            g.sfx(Game.SFX_BACK);
            return;
        }
        me.kind = Slot.Kind.OFF;
        me.device = null;
        g.sfx(Game.SFX_BACK);
        boolean humans = false;
        for (Slot s : g.slots) if (s.kind == Slot.Kind.HUMAN) humans = true;
        if (!humans) {
            for (Slot s : g.slots) s.kind = Slot.Kind.OFF;
            g.go(new MainMenu(g));
        }
    }

    private void announceFighter(int fighter) {
        String id = fighter == Slot.RANDOM ? "ann-random" : "ann-name-" + g.roster.get(fighter).id;
        g.voice(id);
        if (fighter >= 0) g.voice("voice-" + g.roster.get(fighter).id + "-select");
    }

    private void start() {
        g.sfx(Game.SFX_ACCEPT);
        g.voice("ann-ready");
        g.go(new StageScreen(g));
    }

    // ------------------------------------------------------------------ drawing

    @Override public void draw(SceneCanvas c) {
        int w = g.width();
        g.menuBackground(c, 0);
        g.bigCentered(c, "CHOOSE YOUR FIGHTER", 2, w / 2, 5, 0xFFFFFFFF, 0xFFFFC040);
        String rule = g.rules.describe() + (g.rules.teams ? "  TEAMS" : "") + (g.rules.itemFrequency == 0 ? "  NO ITEMS" : "");
        g.font.draw(c, rule, w - 4 - g.font.width(rule), 2, 0xA0FFFFFF);
        drawRoster(c);
        for (int i = 0; i < 4; i++) drawPanel(c, i);
        if (ready()) {
            int y = g.height() - 17;
            int pulse = (int) (Math.sin(t * 0.15) * 40 + 200);
            c.fill(0, y, w, 15, (pulse << 24) | 0xC02020);
            c.fill(0, y, w, 1, 0xFFFFE040);
            c.fill(0, y + 14, w, 1, 0xFFFFE040);
            g.textCentered(c, "READY TO FIGHT!  PRESS START", w / 2, y + 3, 0xFFFFFFFF);
        } else {
            g.textCentered(c, "JOIN: ATTACK ON ANY CONTROLLER   TAB KEY: ADD CPU", w / 2, g.height() - 13, 0x90FFFFFF);
        }
        for (int i = 0; i < 4; i++) drawCursor(c, i);
    }

    private void drawRoster(SceneCanvas c) {
        int n = cells();
        for (int i = 0; i < n; i++) {
            int x = cellX(i);
            boolean hover = false;
            for (Slot s : g.slots) if (s.kind == Slot.Kind.HUMAN && s.cursorX >= x && s.cursorX < x + CELL && s.cursorY >= CELL_Y && s.cursorY < CELL_Y + CELL) hover = true;
            c.fill(x + 1, CELL_Y, CELL - 2, CELL, hover ? 0xE0303A70 : 0xC0141C38);
            c.fill(x + 1, CELL_Y, CELL - 2, 1, hover ? 0xFFFFE040 : 0xFF405080);
            c.fill(x + 1, CELL_Y + CELL - 1, CELL - 2, 1, 0xFF203050);
            if (i == n - 1) {
                g.bigCentered(c, "?", 4, x + CELL / 2, CELL_Y + 6, 0xFFFFFFFF, 0xFFA0A0FF);
            } else {
                SceneImage face = g.art.portrait(g.roster.get(i).id, 0, 32);
                c.draw(face, x + (CELL - 32) / 2f, CELL_Y + 3);
            }
            String name = i == n - 1 ? "RANDOM" : g.roster.get(i).name;
            if (hover) {
                int tw = g.font.width(name);
                int tx = Math.max(2, Math.min(g.width() - tw - 2, x + CELL / 2 - tw / 2));
                c.fill(tx - 2, CELL_Y + CELL + 1, tw + 4, 11, 0xE0000000);
                g.font.draw(c, name, tx, CELL_Y + CELL + 2, 0xFFFFE040);
            }
        }
        // Little coloured tokens on the fighters each player has chosen.
        for (int i = 0; i < 4; i++) {
            Slot s = g.slots[i];
            if (!s.active() || s.fighter == Slot.NONE) continue;
            int cell = s.fighter == Slot.RANDOM ? cells() - 1 : s.fighter;
            int x = cellX(cell) + 3 + i * 9;
            c.fill(x, CELL_Y + CELL - 9, 8, 7, Costumes.playerColor(i, s.kind == Slot.Kind.CPU));
        }
    }

    private void drawPanel(SceneCanvas c, int i) {
        Slot s = g.slots[i];
        int x = panelX(i);
        int y = PANEL_Y;
        boolean cpu = s.kind == Slot.Kind.CPU;
        int col = g.rules.teams && s.active() ? Costumes.teamColor(s.team) : Costumes.playerColor(i, cpu);
        if (!s.active()) col = 0xFF404858;
        c.fill(x, y, PANEL_W, PANEL_H, s.active() ? 0xD0101830 : 0x80101020);
        c.fill(x, y, PANEL_W, 13, col);
        c.fill(x, y + PANEL_H - 1, PANEL_W, 1, col);
        c.fill(x, y, 1, PANEL_H, col);
        c.fill(x + PANEL_W - 1, y, 1, PANEL_H, col);
        String tab = switch (s.kind) {
            case HUMAN -> "P" + (i + 1);
            case CPU -> "CPU";
            default -> "N/A";
        };
        g.font.draw(c, tab, x + 4, y + 2, 0xFFFFFFFF);
        if (g.rules.teams && s.active()) {
            c.fill(x + PANEL_W - 14, y + 2, 10, 9, 0xFFFFFFFF);
            c.fill(x + PANEL_W - 13, y + 3, 8, 7, Costumes.teamColor(s.team));
        }
        if (!s.active()) {
            g.textCentered(c, "PRESS ATTACK", x + PANEL_W / 2, y + 48, 0x80FFFFFF);
            g.textCentered(c, "TO JOIN", x + PANEL_W / 2, y + 60, 0x80FFFFFF);
            g.textCentered(c, "ATTACK HERE", x + PANEL_W / 2, y + 92, 0x60FFFFFF);
            g.textCentered(c, "FOR A CPU", x + PANEL_W / 2, y + 103, 0x60FFFFFF);
            return;
        }
        // Pedestal and fighter.
        c.fill(x + 16, y + 82, PANEL_W - 32, 4, 0x60FFFFFF);
        c.fill(x + 12, y + 86, PANEL_W - 24, 3, 0x30FFFFFF);
        if (s.fighter >= 0) {
            g.drawFighter(c, s.fighter, s.costume, x + PANEL_W / 2.0, y + 84, 1.4, 1, 0);
        } else if (s.fighter == Slot.RANDOM) {
            int shown = (int) ((t / 6) % g.roster.all().size());
            g.drawFighter(c, shown, 0, x + PANEL_W / 2.0, y + 84, 1.4, 1, 0x80000000);
            g.bigCentered(c, "?", 3, x + PANEL_W / 2, y + 36, 0xFFFFFFFF, 0xFFA0A0FF);
        } else {
            g.textCentered(c, "CHOOSE!", x + PANEL_W / 2, y + 44, (t / 15) % 2 == 0 ? 0xFFFFFFFF : 0x80FFFFFF);
        }
        String name = s.fighter == Slot.NONE ? "" : g.fighterName(s.fighter);
        g.textCentered(c, name, x + PANEL_W / 2, y + 89, 0xFFFFFFFF);
        if (s.fighter >= 0) {
            String cn = Costumes.name(g.roster.get(s.fighter).id, s.costume);
            g.textCentered(c, "< " + cn + " >", x + PANEL_W / 2, y + 98, 0xFFC0D0FF);
        }
        if (cpu) {
            g.textCentered(c, "< LV " + s.cpuLevel + " >", x + PANEL_W / 2, y + 109, 0xFFFFE040);
        } else {
            g.textCentered(c, Controls.deviceName(s.device), x + PANEL_W / 2, y + 109, 0x90FFFFFF);
        }
    }

    private void drawCursor(SceneCanvas c, int i) {
        Slot s = g.slots[i];
        if (s.kind != Slot.Kind.HUMAN) return;
        int col = Costumes.playerColor(i, false);
        float x = (float) s.cursorX;
        float y = (float) s.cursorY;
        SceneImage disc = g.art.image("soft16");
        c.draw(disc, x - 10, y - 4, SceneDraw.plain().withScale(1.25f).withTint(0xA0000000));
        // A pointer: a filled arrow tip and a chip with the player number.
        for (int k = 0; k < 7; k++) c.fill((int) x - k / 2, (int) y + k, k + 1, 1, 0xFFFFFFFF);
        c.fill((int) x - 1, (int) y + 7, 13, 11, col);
        c.fill((int) x - 1, (int) y + 7, 13, 1, 0xFFFFFFFF);
        g.font.draw(c, "P" + (i + 1), (int) x, (int) y + 8, 0xFFFFFFFF);
    }

    @Override public String name() { return "select"; }

    /** Puts slot {@code i} under the CPU with a fighter, for debug jumps and tests. */
    void setCpu(int i, int fighter, int level) {
        Slot s = g.slots[i];
        s.kind = Slot.Kind.CPU;
        s.fighter = fighter;
        s.cpuLevel = level;
    }
}
