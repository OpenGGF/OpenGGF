package sonicbros.ui;

import com.openggf.mods.scene.SceneCanvas;
import sonicbros.sim.Item;
import sonicbros.sim.Pad;
import sonicbros.sim.Rules;

/**
 * Match rules: the mode (stock, time or stamina) and its limit, how often monitors drop and which
 * ones can, teams, friendly fire, tap-jump and the damage ratio. Saved between sessions.
 */
public final class RulesScreen implements Screen {
    private static final int VISIBLE = 9;
    private final Game g;
    private int cursor;
    private int scroll;
    private long t;

    public RulesScreen(Game g) {
        this.g = g;
    }

    private int rows() { return 7 + Item.KINDS + 1; }

    @Override public void update() {
        t++;
        int rows = rows();
        if (g.controls.anyRepeated(Pad.UP)) { cursor = (cursor + rows - 1) % rows; g.sfx(Game.SFX_MOVE); }
        if (g.controls.anyRepeated(Pad.DOWN)) { cursor = (cursor + 1) % rows; g.sfx(Game.SFX_MOVE); }
        int dx = 0;
        if (g.controls.anyRepeated(Pad.LEFT)) dx = -1;
        if (g.controls.anyRepeated(Pad.RIGHT)) dx = 1;
        boolean accept = g.controls.anyAccept();
        if (cursor == rows - 1) {
            if (accept) { leave(); return; }
        } else if (dx != 0 || accept) {
            change(cursor, dx == 0 ? 1 : dx);
            g.sfx(Game.SFX_MOVE);
        }
        if (g.controls.anyBack()) leave();
        if (cursor < scroll) scroll = cursor;
        if (cursor >= scroll + VISIBLE) scroll = cursor - VISIBLE + 1;
    }

    private void leave() {
        g.saveRules();
        g.sfx(Game.SFX_BACK);
        g.go(new MainMenu(g));
    }

    private void change(int row, int d) {
        Rules r = g.rules;
        switch (row) {
            case 0 -> r.mode = Rules.Mode.values()[Math.floorMod(r.mode.ordinal() + d, 3)];
            case 1 -> {
                switch (r.mode) {
                    case STOCK -> r.stocks = Math.max(1, Math.min(9, r.stocks + d));
                    case TIME -> r.minutes = Math.max(1, Math.min(10, r.minutes + d));
                    case STAMINA -> r.stamina = Math.max(50, Math.min(300, r.stamina + d * 25));
                }
            }
            case 2 -> r.itemFrequency = Math.floorMod(r.itemFrequency + d, 4);
            case 3 -> r.teams = !r.teams;
            case 4 -> r.friendlyFire = !r.friendlyFire;
            case 5 -> r.tapJump = !r.tapJump;
            case 6 -> r.damageRatio = Math.max(0.5, Math.min(2.0, Math.round((r.damageRatio + d * 0.1) * 10) / 10.0));
            default -> {
                int k = row - 7;
                if (k >= 0 && k < Item.KINDS) r.itemMask ^= 1 << k;
            }
        }
    }

    private String label(int row) {
        return switch (row) {
            case 0 -> "MODE";
            case 1 -> switch (g.rules.mode) { case STOCK -> "STOCKS"; case TIME -> "TIME LIMIT"; default -> "STAMINA"; };
            case 2 -> "MONITORS";
            case 3 -> "TEAM BATTLE";
            case 4 -> "FRIENDLY FIRE";
            case 5 -> "UP TO JUMP";
            case 6 -> "DAMAGE RATIO";
            default -> row - 7 < Item.KINDS ? "  " + Item.name(row - 7) : "BACK";
        };
    }

    private String value(int row) {
        Rules r = g.rules;
        return switch (row) {
            case 0 -> r.mode.toString();
            case 1 -> switch (r.mode) { case STOCK -> r.stocks + ""; case TIME -> r.minutes + " MIN"; default -> r.stamina + " HP"; };
            case 2 -> switch (r.itemFrequency) { case 0 -> "OFF"; case 1 -> "LOW"; case 2 -> "MEDIUM"; default -> "HIGH"; };
            case 3 -> r.teams ? "ON" : "OFF";
            case 4 -> r.friendlyFire ? "ON" : "OFF";
            case 5 -> r.tapJump ? "ON" : "OFF";
            case 6 -> String.format("%.1fX", r.damageRatio);
            default -> row - 7 < Item.KINDS ? ((r.itemMask & (1 << (row - 7))) != 0 ? "ON" : "OFF") : "";
        };
    }

    private String help(int row) {
        return switch (row) {
            case 0 -> g.rules.mode == Rules.Mode.STOCK ? "LAST FIGHTER WITH LIVES LEFT WINS"
                    : g.rules.mode == Rules.Mode.TIME ? "MOST KOS WHEN TIME RUNS OUT WINS (FALLS COST A POINT)"
                    : "HIT POINTS INSTEAD OF DAMAGE: REACH ZERO AND YOU'RE OUT";
            case 2 -> "HOW OFTEN MONITORS FALL ONTO THE STAGE";
            case 3 -> "RED VS BLUE VS GREEN: SET TEAMS ON THE FIGHTER SCREEN";
            case 4 -> "WHETHER TEAMMATES CAN HIT EACH OTHER";
            case 5 -> "PRESSING UP ALSO JUMPS (CPUS NEVER USE IT)";
            case 6 -> "MULTIPLIES ALL DAMAGE";
            default -> row >= 7 && row - 7 < Item.KINDS ? itemHelp(row - 7) : "";
        };
    }

    private String itemHelp(int k) {
        return switch (k) {
            case Item.SUPER_RING -> "HEALS 15%";
            case Item.FIRE_SHIELD -> "BLOCKS ONE HIT, +15% ATTACK POWER";
            case Item.BUBBLE_SHIELD -> "BLOCKS ONE HIT, HARDER TO LAUNCH";
            case Item.LIGHTNING_SHIELD -> "BLOCKS ONE HIT, AN EXTRA JUMP";
            case Item.INVINCIBILITY -> "EIGHT SECONDS UNTOUCHABLE";
            case Item.SPEED_SHOES -> "TEN SECONDS OF EXTRA SPEED";
            case Item.EGGMAN -> "A TRAP: IT EXPLODES!";
            default -> "TWELVE SECONDS OF SUPER FORM";
        };
    }

    @Override public void draw(SceneCanvas c) {
        int w = g.width();
        g.menuBackground(c, 0);
        g.bigCentered(c, "RULES", 3, w / 2, 8, 0xFFFFFFFF, 0xFF80B0FF);
        int rows = rows();
        int px = w / 2 - 150;
        for (int i = 0; i < VISIBLE && scroll + i < rows; i++) {
            int row = scroll + i;
            boolean sel = row == cursor;
            int y = 38 + i * 17;
            g.panel(c, px, y, 300, 15, sel ? 0xFFFFE040 : 0xFF304068);
            if (sel) c.fill(px + 1, y + 1, 298, 13, 0x403060C0);
            g.font.draw(c, label(row), px + 8, y + 3, sel ? 0xFFFFFFFF : 0xFFB0B8D0);
            String v = value(row);
            if (!v.isEmpty()) {
                String shown = sel ? "< " + v + " >" : v;
                g.font.draw(c, shown, px + 292 - g.font.width(shown), y + 3, sel ? 0xFFFFE040 : 0xFFE0E0E0);
            }
        }
        if (scroll > 0) g.textCentered(c, "^", w / 2, 30, 0xFFFFFFFF);
        if (scroll + VISIBLE < rows) g.textCentered(c, "v", w / 2, 38 + VISIBLE * 17, 0xFFFFFFFF);
        String h = help(cursor);
        if (!h.isEmpty()) g.textCentered(c, h, w / 2, g.height() - 24, 0xFFFFF0A0);
        g.textCentered(c, "LEFT/RIGHT CHANGE   X/B SAVE AND RETURN", w / 2, g.height() - 11, 0xA0FFFFFF);
    }

    @Override public String name() { return "rules"; }
}
