package towerdefense.ui;

import com.openggf.mods.scene.*;
import towerdefense.art.RomArt;
import towerdefense.core.*;

/** Fixed 400x224 battlefield and menus. All drawing reads state without advancing it. */
public final class BattleView {
    // Slay the Robotnik's Sonic 3 palette and compact font establish the visual vocabulary.
    public static final int WHITE = 0xFFEEEEEE, GOLD = 0xFFFFDA24, MUTED = 0xFF928CA0;
    private final PixelFont font;
    private final RomArt art;

    public BattleView(PixelFont font, RomArt art) { this.font = font; this.art = art; }

    public void background(SceneCanvas c, int ticks) {
        c.clear(0x172634);
        c.drawBackdrop(art.background, 0, 0, 400, 224, 20, 0, ticks);
        c.fill(0, 0, 400, 224, 0x55121B2B);
    }

    public void battlefield(SceneCanvas c, Battlefield b, int ticks, int site, int kind,
            String focus, int action, SceneMouse mouse, String message) {
        background(c, ticks);
        panel(c, 0, 0, 400, 23);
        font.text(c, "DOOR", 8, 6, WHITE);
        bar(c, 32, 5, 70, 7, b.doorHp(), Battlefield.DOOR_MAX, b.doorHp() > 70 ? 0xFF7EE5A4 : 0xFFFF7963);
        font.text(c, b.doorHp() + "/200", 44, 15, WHITE);
        font.text(c, "SCRAP " + b.scrap(), 122, 7, GOLD, 2);
        font.text(c, "WAVE " + b.wave() + "/15", 276, 5, WHITE);
        font.text(c, "[P] PAUSE", 344, 15, MUTED);
        Catalog.Wave wave = b.preview();
        font.centered(c, wave.name(), 180, 29, GOLD, 1);
        font.centered(c, wave.tip(), 180, 39, WHITE, 1);
        // Purpose-built gantries and door are original scene geometry, over ROM scenery.
        c.fill(0, 157, 349, 4, 0xFF576879);
        c.fill(0, 161, 349, 2, 0xFF162430);
        for (int x = 0; x < 348; x += 14) c.fill(x, 157, 7, 1, 0xFFC2D1D7);
        for (int x = 12; x < 330; x += 32) {
            c.fill(x, 87, 5, 1, 0x668DD1DC);
            c.fill(x + 4, 86, 2, 3, 0x668DD1DC);
        }
        door(c, b, ticks);
        Tower selected = b.tower(site);
        if (focus.equals("SHOP") || selected != null) {
            int range = selected == null ? Catalog.defense(kind).range() : selected.range();
            dottedCircle(c, Battlefield.siteX(site), Battlefield.siteY(site) - 12, range, 0x5575CDE0);
        }
        for (int i = 0; i < Battlefield.SITES; i++) {
            int x = Battlefield.siteX(i), y = Battlefield.siteY(i);
            if (i >= 5) {
                c.fill(x - 13, y, 26, 4, 0xFF536B79);
                c.fill(x - 10, y + 4, 3, 38, 0x995B6E78);
                c.fill(x + 7, y + 4, 3, 38, 0x995B6E78);
            } else c.fill(x - 14, y - 2, 28, 5, 0xFF485C68);
            int color = i == site ? GOLD : 0xFF7895A3;
            frame(c, x - 17, y - 34, 34, 37, i == site ? 0xAAFFCE62 : 0x445F8D99);
            Tower tower = b.tower(i);
            if (tower == null) {
                font.centered(c, "+", x, y - 22, color, 2);
            } else {
                art.defense(c, tower.kind(), ticks, x, y - 2, 30, tower.jamTicks() > 0 ? 0xFF9C8FC8 : WHITE);
                for (int level = 0; level < tower.level(); level++) c.fill(x - 6 + level * 5, y, 3, 2, GOLD);
                if (tower.jamTicks() > 0) font.centered(c, "JAM", x, y - 36, 0xFFFF9898, 1);
                if (tower.kind() == Catalog.ORBINAUT) {
                    for (int n = 0; n < 3; n++) {
                        double angle = ticks * 0.05 + n * Math.PI * 2 / 3;
                        c.fill(x + (int) (Math.cos(angle) * 14) - 2,
                                y - 15 + (int) (Math.sin(angle) * 9) - 2, 4, 4, 0xFF9FF4B8);
                    }
                }
            }
            font.text(c, Integer.toString(i + 1), x - 16, y - 33, color);
        }
        for (Flicky bird : b.birds()) bird(c, bird, ticks);
        for (Effect e : b.effects()) effect(c, e, ticks);
        c.fill(0, 176, 400, 8, 0xF00A1028);
        String detail = selected != null && !focus.equals("SHOP") ? Catalog.defense(selected.kind()).name()
                + " LV" + selected.level() + "  DMG " + (int) selected.damage() + "  RANGE " + selected.range()
                + "  UP $" + b.upgradeCost(site) + "  SELL $" + b.sellValue(site)
                : Catalog.defense(kind).description();
        boolean pointer = mouse.lastInputWasMouse();
        boolean inspecting = pointer && (mouse.over(0, 186, 308, 38) || mouse.over(312, 215, 85, 9));
        for (int i = 0; i < 6; i++) {
            if (pointer && mouse.over(3 + i * 51, 186, 49, 27)) detail = Catalog.defense(i).description();
        }
        if (pointer && mouse.over(54, 215, 74, 9)) detail = selected == null ? "SELECT A TOWER TO UPGRADE."
                : selected.level() == 3 ? "LEVEL 3: MAXIMUM UPGRADE."
                : "UPGRADE TO LV" + (selected.level() + 1) + ": $" + b.upgradeCost(site) + ". MORE DAMAGE, RANGE + FIRE RATE.";
        if (pointer && mouse.over(133, 215, 50, 9)) detail = "SELL: REFUNDS 75% OF THE TOTAL INVESTMENT. $" + b.sellValue(site);
        if (pointer && mouse.over(188, 215, 77, 9)) detail = "REPAIR BETWEEN WAVES: $25 RESTORES 40 DOOR HP.";
        if (pointer && mouse.over(270, 215, 127, 9)) detail = "BOMB: DAMAGE + KNOCKBACK. 20-SECOND WAVE COOLDOWN.";
        boolean showDetail = message.isEmpty() || inspecting;
        font.centered(c, showDetail ? detail : message, 200, 177, showDetail ? WHITE : GOLD, 1);
        shop(c, b, site, kind, focus, action, mouse);
    }

    private void door(SceneCanvas c, Battlefield b, int ticks) {
        c.fill(349, 65, 49, 111, 0xFF223544);
        frame(c, 348, 65, 50, 111, 0xFF86A5B5);
        c.fill(354, 117, 39, 49, 0xFF091922);
        for (int y = 118; y < 160; y += 7) c.fill(355, y, 37, 3, 0xFF4C697B);
        for (int y = 65; y < 175; y += 12) {
            c.fill(350, y, 3, 5, GOLD);
            c.fill(394, y + 6, 3, 5, GOLD);
        }
        art.capsule(c, 373, 95);
        font.centered(c, "EVIL HQ", 373, 112, GOLD, 1);
        font.centered(c, "DOOR", 373, 152, WHITE, 1);
        bar(c, 357, 168, 34, 3, b.doorHp(), 200, 0xFF8BF1AC);
        art.robotnik(c, 373, 57 + (float) Math.sin(ticks * 0.045) * 2, b.doorHp() < 70, 0.6f);
    }

    private void bird(SceneCanvas c, Flicky bird, int ticks) {
        int x = (int) bird.x(), y = (int) bird.y();
        if (bird.kind() == Catalog.ORGANISER) dottedCircle(c, x, y, 56, 0x33FF9D9D);
        int color = Catalog.birdColor(bird.kind());
        art.flicky(c, ticks + bird.id() * 3, x, y + (bird.flying() ? (float) Math.sin((ticks + bird.id() * 9) * 0.06) * 3 : 0),
                color, bird.flashing(), 1.0f);
        if (bird.kind() == Catalog.PICKETER || bird.kind() == Catalog.ORGANISER) {
            c.fill(x - 3, y - 23, 1, 15, 0xFFD7B387);
            String slogan = bird.kind() == Catalog.ORGANISER ? "UNION" : "PAY!";
            int signWidth = font.width(slogan) + 4;
            c.fill(x - signWidth / 2, y - 24, signWidth, 8,
                    bird.kind() == Catalog.ORGANISER ? 0xFFCE4545 : 0xFFEAD29E);
            font.text(c, slogan, x - signWidth / 2 + 2, y - 23, 0xFF272535);
        } else if (bird.kind() == Catalog.SHIELD) {
            c.fill(x + 5, y - 9, 5, 13, 0xFFCEDAE1);
            c.fill(x + 8, y - 7, 2, 9, 0xFF7193A5);
        } else if (bird.kind() == Catalog.COURIER) {
            c.fill(x - 6, y - 9, 10, 2, 0xFFFFCE62);
            c.fill(x - 9, y - 8, 3, 3, 0xFFFFCE62);
        } else if (bird.kind() == Catalog.SABOTEUR) {
            c.fill(x + 4, y - 15, 2, 10, 0xFF8BF1AC);
            c.fill(x + 1, y - 16, 8, 2, 0xFF8BF1AC);
        }
        if (bird.hp() < bird.maxHp()) bar(c, x - 8, y + 9, 16, 2, bird.hp(), bird.maxHp(), 0xFF83EFC0);
        if (bird.slowed()) c.fill(x - 7, y + 7, 14, 1, 0xFF8BF1AC);
    }

    private void effect(SceneCanvas c, Effect e, int ticks) {
        int x = (int) e.x(), y = (int) e.y();
        switch (e.type()) {
            case "shot", "jam" -> {
                for (int i = 0; i <= 12; i++) {
                    double p = i / 12.0;
                    c.fill((int) (x + (e.toX() - x) * p), (int) (y + (e.toY() - y) * p), 2, 2, e.color());
                }
            }
            case "burst" -> dottedCircle(c, x, y, (int) (e.toX() * (1 - e.remaining())), e.color());
            case "bomb" -> {
                dottedCircle(c, x, y, e.age() * 10, GOLD);
                if (e.age() < 20) art.explosion(c, e.age(), x - 15, y);
            }
            case "retreat" -> art.flicky(c, ticks, x - e.age() * 2, y - e.age(), e.color(), false, 1);
            case "peck" -> c.fill(x - 3, y - 6, 5, 12, e.color());
            default -> { }
        }
    }

    private void shop(SceneCanvas c, Battlefield b, int site, int kind, String focus, int action, SceneMouse mouse) {
        panel(c, 0, 184, 400, 40);
        for (int i = 0; i < 6; i++) {
            int x = 3 + i * 51;
            boolean active = i == kind && focus.equals("SHOP");
            boolean hover = mouse.lastInputWasMouse() && mouse.over(x, 186, 49, 27);
            gradient(c, x, 186, 49, 27, active || hover ? 0xFF3C6CDA : 0xFF24489C,
                    active || hover ? 0xFF1C3C90 : 0xFF14285C);
            frame(c, x, 186, 49, 27, active ? GOLD : 0xFF6C90DA);
            String name = switch (i) {
                case 0 -> "BLASTER"; case 1 -> "MORTAR"; case 2 -> "ANTI-AIR";
                case 3 -> "SPIKER"; case 4 -> "ORBITS"; default -> "EGG ROBO";
            };
            font.centered(c, name, x + 24, 189, Catalog.defense(i).color(), 1);
            art.defense(c, i, b.ticks(), x + 12, 211, 15, WHITE);
            font.text(c, "$" + Catalog.defense(i).cost(), x + 24, 201,
                    b.scrap() >= Catalog.defense(i).cost() ? GOLD : MUTED);
        }
        button(c, "SEND WAVE", 312, 186, 85, 27, focus.equals("ACTIONS") && action == 4,
                b.phase().equals(Battlefield.PREP) ? GOLD : MUTED);
        font.centered(c, b.phase().equals(Battlefield.PREP) ? "[E]  " + (b.wave() + 1) + "/15"
                : "LEFT " + (b.remainingSpawns() + b.birds().size()), 354, 204, WHITE, 1);
        Tower selected = b.tower(site);
        int upgrade = b.upgradeCost(site);
        action(c, "[H] HELP", 3, 44, action == 5 && focus.equals("ACTIONS"), true);
        action(c, selected != null && selected.level() == 3 ? "MAX LV3" : "[U] UP $" + upgrade,
                54, 74, action == 0 && focus.equals("ACTIONS"), upgrade > 0 && b.scrap() >= upgrade);
        action(c, "[X] SELL", 133, 50, action == 1 && focus.equals("ACTIONS"), selected != null);
        action(c, "[R] FIX $25", 188, 77, action == 2 && focus.equals("ACTIONS"),
                b.phase().equals(Battlefield.PREP) && b.doorHp() < 200 && b.scrap() >= 25);
        action(c, "[F] BOMB " + (b.bombCooldown() == 0 ? "READY" : (b.bombCooldown() + 59) / 60 + "S"),
                270, 127, action == 3 && focus.equals("ACTIONS"), b.phase().equals(Battlefield.WAVE) && b.bombCooldown() == 0);
    }

    private void action(SceneCanvas c, String label, int x, int width, boolean active, boolean enabled) {
        c.fill(x, 215, width, 9, active ? 0xFF243C90 : 0xFF102048);
        font.centered(c, label, x + width / 2, 217, enabled ? active ? GOLD : WHITE : MUTED, 1);
    }

    public void title(SceneCanvas c, Records records, int cursor, int ticks) {
        background(c, ticks);
        panel(c, 26, 21, 348, 105);
        logo(c, "ROBOTNIK", 183, 30, 4, ticks);
        font.centered(c, "TOWER DEFENCE", 183, 61, WHITE, 3);
        c.fill(64, 92, 232, 16, 0xFFAD3B3E);
        font.centered(c, "INDUSTRIAL ACTION", 180, 96, WHITE, 2);
        art.robotnik(c, 338, 77, false, 1);
        for (int i = 0; i < 4; i++) {
            art.flicky(c, ticks + i * 9, 42 + i * 16, 151 + (i % 2) * 13, WHITE, false, 1.1f);
        }
        font.centered(c, "THE WORKFORCE HAS HAD ENOUGH.", 200, 121, WHITE, 1);
        button(c, "START THE SIEGE", 112, 139, 176, 18, cursor == 0, GOLD);
        button(c, "HOW TO DEFEND", 112, 162, 176, 14, cursor == 1, WHITE);
        button(c, "PLAY SONIC 3 & KNUCKLES", 112, 181, 176, 14, cursor == 2, WHITE);
        font.centered(c, "BEST " + records.bestScore() + "   WAVE " + records.bestWave() + "   WINS " + records.wins(),
                200, 208, GOLD, 1);
        font.centered(c, "MOUSE OR PAD / ARROWS + ENTER", 200, 217, MUTED, 1);
    }

    public void help(SceneCanvas c, int ticks, int page) {
        background(c, ticks);
        panel(c, 16, 13, 368, 200);
        font.centered(c, page == 0 ? "DEFENDING YOUR EVIL BASE" : "KNOW YOUR MACHINES", 200, 24, GOLD, 2);
        if (page == 0) {
            String[] lines = {
                "SURVIVE 15 WAVES. KEEP THE DOOR ABOVE ZERO.",
                "BIRDS AT THE DOOR KEEP PECKING UNTIL REPELLED.",
                "BUILD ON THE 10 NUMBERED SITES. UPGRADE TO LV3.",
                "SCRAP: REPEL BIRDS + FINISH WAVES. SELL FOR 75%.",
                "REPAIR BETWEEN WAVES: $25 RESTORES 40 DOOR HP.",
                "MOUSE: PICK A CARD, THEN CLICK AN EMPTY SITE.",
                "CLICK A TOWER, THEN UP / SELL IN THE ACTION BAR.",
                "PAD: ARROWS MOVE. A/C CONFIRM. B CANCEL / PAUSE.",
                "UP FROM TOP SITES: SHOP. DOWN FROM BOTTOM: ACTIONS.",
                "E SEND WAVE. U UPGRADE. X SELL. R REPAIR. F BOMB.",
                "1-6 PICK A DEFENSE. P / START PAUSE. H OPENS HELP.",
                "BOMB: DAMAGE + KNOCKBACK. 20 SECONDS TO RECHARGE.",
                "PAUSE AND HELP FREEZE ALL GAMEPLAY TIMERS."
            };
            for (int i = 0; i < lines.length; i++) font.text(c, lines[i], 30, 46 + i * 11, WHITE);
        } else {
            for (int i = 0; i < 6; i++) {
                int y = 49 + i * 21;
                art.defense(c, i, ticks, 43, y + 13, 18, WHITE);
                font.text(c, Catalog.defense(i).name() + "  $" + Catalog.defense(i).cost(), 62, y, Catalog.defense(i).color());
                font.text(c, Catalog.defense(i).description(), 62, y + 8, WHITE);
            }
            font.centered(c, "RED FLAGS: ORGANISERS SPEED THEIR COMRADES.", 200, 181, 0xFFFF9C9C, 1);
            font.centered(c, "GREEN TOOLS: SABOTEURS JAM NEARBY TOWERS.", 200, 190, 0xFFAEEC90, 1);
        }
        font.centered(c, "LEFT / RIGHT: PAGE " + (page + 1) + "/2   A / ENTER: RETURN", 200, 203, GOLD, 1);
    }

    public void pause(SceneCanvas c, int cursor) {
        c.fill(0, 23, 400, 153, 0xB8101722);
        panel(c, 105, 68, 190, 94);
        frame(c, 105, 68, 190, 94, GOLD);
        font.centered(c, "OPERATIONS PAUSED", 200, 77, GOLD, 2);
        button(c, "RESUME", 124, 97, 152, 14, cursor == 0, WHITE);
        button(c, "RESTART SIEGE", 124, 117, 152, 14, cursor == 1, WHITE);
        button(c, "RETURN TO TITLE", 124, 137, 152, 14, cursor == 2, WHITE);
    }

    public void result(SceneCanvas c, Battlefield b, int ticks) {
        background(c, ticks);
        boolean won = b.phase().equals(Battlefield.WON);
        panel(c, 38, 30, 324, 166);
        frame(c, 38, 30, 324, 166, won ? GOLD : 0xFFFF9980);
        font.centered(c, won ? "BASE SECURED!" : "THE DOOR IS DOWN!", 200, 43, won ? GOLD : 0xFFFF9980, 3);
        art.robotnik(c, 200, 97 + (float) Math.sin(ticks * 0.045) * 3, !won, 1.0f);
        font.centered(c, won ? "I SHALL FILE A VERY STRONGLY WORDED MEMO."
                : "THE FLICKIES HAVE SEIZED THE MEANS OF PRODUCTION.", 200, 127, WHITE, 1);
        font.centered(c, "SCORE " + b.score() + "   WAVE " + b.wave() + "/15", 200, 144, GOLD, 2);
        font.centered(c, "REPELLED " + b.kills() + "   DOOR " + b.doorHp() + "/200", 200, 162, WHITE, 1);
        button(c, "A / CLICK: RETRY    B / RIGHT CLICK: TITLE", 62, 177, 276, 13, true, GOLD);
    }

    private void button(SceneCanvas c, String label, int x, int y, int w, int h, boolean active, int color) {
        gradient(c, x + 1, y + 1, w - 2, h - 2, active ? 0xFF3C6CDA : 0xFF24489C,
                active ? 0xFF1C3C90 : 0xFF14285C);
        frame(c, x, y, w, h, active ? GOLD : 0xFF6C90DA);
        font.centered(c, label, x + w / 2, y + 4, color, 1);
    }

    private void bar(SceneCanvas c, int x, int y, int w, int h, double value, double max, int color) {
        c.fill(x, y, w, h, 0xFF08121C);
        c.fill(x, y, (int) Math.ceil(w * Math.max(0, Math.min(1, value / max))), h, color);
    }

    private void frame(SceneCanvas c, int x, int y, int w, int h, int color) {
        c.fill(x, y, w, 1, color); c.fill(x, y + h - 1, w, 1, color);
        c.fill(x, y, 1, h, color); c.fill(x + w - 1, y, 1, h, color);
    }

    private void panel(SceneCanvas c, int x, int y, int w, int h) {
        c.fill(x + 1, y + 1, w - 2, h - 2, 0xF0102048);
        frame(c, x, y, w, h, 0xFF6C90DA);
        c.fill(x + 1, y + 1, w - 2, 1, 0x33FFFFFF);
        c.fill(x + 1, y + h - 2, w - 2, 1, 0x66000000);
    }

    private void gradient(SceneCanvas c, int x, int y, int w, int h, int top, int bottom) {
        for (int row = 0; row < h; row += 2) {
            double t = (double) row / Math.max(1, h - 1);
            int color = 0xFF000000;
            for (int shift = 0; shift <= 16; shift += 8) {
                int a = (top >>> shift) & 255, b = (bottom >>> shift) & 255;
                color |= (int) (a + (b - a) * t) << shift;
            }
            c.fill(x, y + row, w, Math.min(2, h - row), color);
        }
    }

    /** Two-tone outlined lettering, as in the existing mod's Sonic 3-inspired logo. */
    private void logo(SceneCanvas c, String text, int cx, int y, int scale, int ticks) {
        int x = cx - font.width(text) * scale / 2;
        int bob = (int) Math.round(Math.sin(ticks * 0.05));
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) font.text(c, text, x + dx, y + bob + dy, 0xFF000024, scale);
        }
        font.text(c, text, x, y + bob + 2, 0xFF900000, scale);
        font.text(c, text, x, y + bob, 0xFFDA2424, scale);
        c.clip(x - 2, y + bob, font.width(text) * scale + 4, scale * 5 / 2);
        font.text(c, text, x, y + bob, GOLD, scale);
        c.unclip();
    }

    private void dottedCircle(SceneCanvas c, int x, int y, int r, int color) {
        for (int i = 0; i < 48; i++) {
            double a = i * Math.PI / 24;
            int px = x + (int) (Math.cos(a) * r), py = y + (int) (Math.sin(a) * r);
            if (px >= 0 && px < 348 && py >= 47 && py < 175) c.fill(px, py, 1, 1, color);
        }
    }
}
