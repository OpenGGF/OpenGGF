package survivors;

import com.openggf.level.objects.ObjectServices;
import java.util.Locale;

/**
 * Everything the {@link Stage} draws: world-space weapon visuals (orbit rings, projectiles,
 * shockwaves, bolts, damage numbers, off-screen badnik markers) and the screen HUD and menus
 * (camp shop, level-up cards, zone clear and route choice, game over, victory).
 */
final class Hud {
    private Hud() { }

    // =========================================================================================
    // World space
    // =========================================================================================

    static void drawWorld(ObjectServices s, Stage stage) {
        var run = s.gameService(RunState.class);
        var player = s.camera().getFocusedSprite();
        var rings = s.ringManager();
        if (run != null && run.active && player != null && !player.getDead() && rings != null) {
            int orbit = run.level(Upgrades.ORBIT_RINGS);
            int n = orbit > 0 ? Upgrades.orbitCount(orbit) : 0;
            for (int i = 0; i < n; i++) {
                double a = (stage.orbitAngle / 1024.0 + i / (double) n) * Math.PI * 2;
                rings.drawRingAt(player.getCentreX() + (int) (Math.cos(a) * 42),
                        player.getCentreY() + (int) (Math.sin(a) * 42), stage.frameTick + i * 3);
            }
        }
        var render = s.renderManager();
        var sparkle = render == null ? null : render.getRenderer("super_sonic_stars");
        var animal = render == null ? null : render.getRenderer("animal");
        for (int i = 0; i < stage.pKind.length; i++) {
            int kind = stage.pKind[i];
            if (kind == 0) continue;
            int x = stage.pX[i] >> 8, y = stage.pY[i] >> 8;
            switch (kind) {
                case Stage.P_SPARK -> {
                    if (sparkle != null) sparkle.drawFrameIndex(1 + (stage.frameTick / 3 + i) % 3, x, y, false, false);
                    else Draw.rectWorld(s, x - 2, y - 2, 4, 4, Draw.WHITE, 1f);
                }
                case Stage.P_HOMING -> {
                    if (rings != null) rings.drawRingAt(x, y, stage.frameTick * 2 + i);
                }
                case Stage.P_FLICKY -> {
                    if (animal != null) animal.drawFrameIndex(3 + (stage.frameTick / 4) % 2, x, y, stage.pVX[i] > 0, false);
                    else Draw.rectWorld(s, x - 3, y - 3, 6, 6, Draw.BLUE, 1f);
                }
                default -> drawBoom(s, x, y, stage.pVX[i] < 0, stage.frameTick);
            }
        }
        for (int i = 0; i < Stage.MAX_E; i++) {
            int kind = stage.eKind[i];
            if (kind == 0) continue;
            int age = stage.eAge[i];
            switch (kind) {
                case Stage.E_NUMBER -> Draw.smallWorld(s, Integer.toString(stage.eA[i]),
                        stage.eX[i] - 4, stage.eY[i] - age / 2, stage.eB[i], Math.min(1f, (40 - age) / 12f));
                case Stage.E_SHOCK, Stage.E_POUND -> {
                    int radius = stage.eA[i] * Math.min(age + 2, 10) / 10;
                    float alpha = Math.max(0f, 1f - age / 16f);
                    Draw.circleWorld(s, stage.eX[i], stage.eY[i], radius, 3, stage.eB[i], alpha);
                    Draw.circleWorld(s, stage.eX[i], stage.eY[i], Math.max(1, radius - 6), 1, Draw.WHITE, alpha * 0.7f);
                }
                case Stage.E_BOLT -> {
                    float alpha = Math.max(0f, 1f - age / 10f);
                    Draw.boltWorld(s, stage.eX[i], stage.eY[i], stage.eA[i], stage.eC[i], stage.eD[i] + age / 3,
                            stage.eB[i], alpha);
                    Draw.boltWorld(s, stage.eX[i], stage.eY[i], stage.eA[i], stage.eC[i], stage.eD[i] + 7,
                            Draw.WHITE, alpha * 0.6f);
                }
                default -> {
                    if (sparkle != null) sparkle.drawFrameIndex(Math.min(4, age / 3), stage.eX[i], stage.eY[i], false, false);
                }
            }
        }
        if (stage.phase == Stage.FIGHT || stage.phase == Stage.BOSS) drawMarkers(s);
    }

    /** A sonic boom: three nested arcs that shimmer. */
    private static void drawBoom(ObjectServices s, int x, int y, boolean left, int tick) {
        int dir = left ? -1 : 1;
        for (int arc = 0; arc < 3; arc++) {
            int r = 6 + arc * 4;
            int colour = arc == 0 ? Draw.WHITE : (tick / 2 + arc) % 2 == 0 ? Draw.CYAN : 0x80C0FF;
            for (int dy = -r; dy <= r; dy++) {
                int dx = (int) Math.sqrt(r * r - dy * dy);
                Draw.rectWorld(s, x + dir * dx - (left ? 2 : 0), y + dy, 2, 1, colour, 0.85f);
            }
        }
    }

    /** Red chevrons at the screen edge for badniks approaching from off-screen. */
    private static void drawMarkers(ObjectServices s) {
        var camera = s.camera();
        int left = camera.getX(), right = left + camera.getWidth(), top = camera.getY();
        int drawn = 0;
        for (var object : s.objectManager().getActiveObjects()) {
            if (!(object instanceof Enemy enemy) || !enemy.alive() || drawn >= 12) continue;
            int x = enemy.getX(), y = enemy.getY();
            if (x >= left && x <= right) continue;
            int sy = Math.max(top + 24, Math.min(top + camera.getHeight() - 12, y));
            int dir = x < left ? -1 : 1;
            int edge = x < left ? left + 3 : right - 4;
            int colour = enemy.elite() ? Draw.GOLD : Draw.RED;
            for (int k = 0; k < 4; k++) Draw.rectWorld(s, edge - dir * k, sy - 3 + k, 1, 7 - 2 * k, colour, 0.85f);
            drawn++;
        }
    }

    // =========================================================================================
    // Screen space
    // =========================================================================================

    static void draw(ObjectServices s, Stage stage) {
        var run = s.gameService(RunState.class);
        if (run == null) return;
        int width = s.camera().getWidth();
        if (stage.hitFlash > 0) Draw.rect(s, 0, 0, width, 224, Draw.RED, stage.hitFlash / 40f);
        switch (stage.phase) {
            case Stage.CAMP -> { drawCamp(s, stage); return; }
            case Stage.DEAD -> { drawGameOver(s, stage, false); return; }
            case Stage.VICTORY -> { drawGameOver(s, stage, true); return; }
            default -> { }
        }
        drawStatus(s, stage, run);
        if (stage.phase == Stage.INTRO && stage.overlay == Stage.OVERLAY_NONE) drawIntro(s, stage);
        if (stage.bannerFrames > 0 && !stage.banner.isEmpty()) {
            float alpha = Math.min(1f, stage.bannerFrames / 15f);
            Draw.centred(s, stage.banner, 64, 2, stage.bannerColour, alpha);
        }
        if (stage.overlay == Stage.OVERLAY_LEVEL_UP) drawLevelUp(s, stage, run);
        if (stage.phase == Stage.CLEAR) drawClear(s, stage, run);
    }

    private static String clock(int frames) {
        int seconds = Math.max(0, (frames + 59) / 60);
        return seconds / 60 + ":" + String.format(Locale.ROOT, "%02d", seconds % 60);
    }

    /**
     * The HUD keeps clear of the screen's right 24 pixels: at the arena's right wall the engine
     * fills the space beyond the level edge with static, drawn over world-space objects.
     */
    static final int RIGHT_MARGIN = 26;

    private static void drawStatus(ObjectServices s, Stage stage, RunState run) {
        int width = s.camera().getWidth() - RIGHT_MARGIN + 8;
        var player = s.camera().getFocusedSprite();
        int rings = player == null ? 0 : player.getRingCount();
        // Quiet backing keeps small readouts legible through bright terrain and dense hordes.
        Draw.rect(s, 4, 3, 110, run.revives > 0 ? 66 : 56, 0x080E30, 0.72f);
        if (stage.phase == Stage.FIGHT) {
            Draw.rect(s, s.camera().getWidth() / 2 - 54, 32, 108, 19, 0x080E30, 0.72f);
        }
        // Rings (health) and level.
        boolean danger = rings <= run.toll(rings) && (stage.frameTick / 8) % 2 == 0;
        Draw.shadow(s, "RINGS", 8, 8, 1, Draw.YELLOW, 1f);
        Draw.shadow(s, Integer.toString(rings), 44, 6, 2, danger ? Draw.RED : Draw.WHITE, 1f);
        Draw.shadow(s, "LV " + (run.level + 1), 8, 24, 1, Draw.CYAN, 1f);
        Draw.bar(s, 40, 25, 70, 4, run.xp / (double) RunState.xpToNext(run.level), Draw.CYAN, 1f);
        int y = 34;
        if (run.revives > 0) { Draw.shadow(s, "REVIVE x" + run.revives, 8, y, 1, Draw.GREEN, 1f); y += 10; }
        Draw.shadow(s, "HIT -" + run.toll(rings), 8, y, 1, danger ? Draw.RED : Draw.WHITE, 1f);
        String fever = stage.feverFrames > 0 ? "FEVER " + clock(stage.feverFrames)
                : stage.feverCooldown > 0 ? "RECHARGE " + clock(stage.feverCooldown)
                : "FEVER " + stage.feverCharge + "/" + Stage.FEVER_COMBO;
        Draw.shadow(s, fever, 8, y + 10, 1, stage.feverFrames > 0 ? Draw.PINK : Draw.CYAN, 1f);
        double charge = stage.feverFrames > 0 ? stage.feverFrames / (double) Stage.FEVER_DURATION
                : stage.feverCooldown > 0 ? 1 - stage.feverCooldown / (double) Stage.FEVER_RECOVERY
                : stage.feverCharge / (double) Stage.FEVER_COMBO;
        Draw.bar(s, 8, y + 19, 80, 3, charge, stage.feverFrames > 0 ? Draw.PINK : Draw.CYAN, 1f);
        if (stage.phase == Stage.FIGHT) {
            Draw.centred(s, "W" + stage.encounterNumber() + " " + stage.encounterName(), 36, 1,
                    stage.encounterBeat() >= 18 && stage.encounterBeat() < 25 ? Draw.ORANGE : Draw.CYAN, 1f);
            Draw.bar(s, s.camera().getWidth() / 2 - 40, 46, 80, 3,
                    (stage.fightFrames % Stage.ENCOUNTER_FRAMES) / (double) Stage.ENCOUNTER_FRAMES, Draw.GOLD, 1f);
        }
        // Owned upgrades, compact.
        int x = 8;
        y = 210;
        for (int id = 0; id < Upgrades.COUNT; id++) {
            int level = run.level(id);
            if (level <= 0) continue;
            String tag = abbreviation(id) + level;
            Draw.shadow(s, tag, x, y, 1, Upgrades.kindColour(id), 1f);
            x += Draw.width(tag, 1) + 6;
            if (x > width - 60) { x = 8; y -= 10; }
        }
        // Clock or boss bar, top centre.
        if (stage.phase == Stage.BOSS || stage.phase == Stage.CLEAR_WAIT) {
            Boss boss = stage.boss();
            Draw.centred(s, stage.finale ? "EGGMAN" : Stages.bossName(stage.arena().stage()), 6, 1, Draw.RED, 1f);
            double fraction = boss == null ? 0 : boss.hp() / (double) Math.max(1, boss.maxHp());
            Draw.bar(s, s.camera().getWidth() / 2 - 64, 16, 128, 6, fraction, Draw.RED, 1f);
        } else {
            String time = stage.endless() ? clock(stage.fightFrames / 60 * 60)
                    : clock(stage.phase == Stage.INTRO ? stage.survivalSeconds() * 60 : stage.stageFrames);
            if (stage.endless()) Draw.centred(s, "ENDLESS", 25, 1, Draw.CYAN, 1f);
            boolean hurry = stage.phase == Stage.FIGHT && !stage.endless() && stage.stageFrames < 10 * 60;
            Draw.centred(s, time, 6, 2, hurry && (stage.frameTick / 10) % 2 == 0 ? Draw.RED : Draw.WHITE, 1f);
        }
        // Zone and kills, top right.
        var arena = stage.arena();
        String zone = Stages.name(arena.stage()) + (Stages.acts(arena.stage()) > 1 ? " " + (arena.act() + 1) : "");
        Draw.shadow(s, zone, width - 8 - Draw.width(zone, 1), 8, 1, Draw.GOLD, 1f);
        String kills = "KO " + run.kills;
        Draw.shadow(s, kills, width - 8 - Draw.width(kills, 1), 18, 1, Draw.WHITE, 1f);
        // Combo.
        if (run.combo >= 2) {
            String combo = run.combo + " COMBO";
            String mult = String.format(Locale.ROOT, "x%.2f", run.comboMultiplier());
            int colour = run.combo >= 10 ? Draw.PINK : run.combo >= 5 ? Draw.GOLD : Draw.CYAN;
            Draw.shadow(s, combo, width - 8 - Draw.width(combo, 2), 34, 2, colour, 1f);
            Draw.shadow(s, mult, width - 8 - Draw.width(mult, 1), 52, 1, Draw.WHITE, 1f);
        }
    }

    static String abbreviation(int id) {
        return switch (id) {
            case Upgrades.SHOCKWAVE -> "SHK";
            case Upgrades.SPARKS -> "SPK";
            case Upgrades.CHAIN_ZAP -> "ZAP";
            case Upgrades.HOMING_RINGS -> "HRG";
            case Upgrades.ORBIT_RINGS -> "ORB";
            case Upgrades.SONIC_BOOM -> "BOOM";
            case Upgrades.FLICKIES -> "FLK";
            case Upgrades.HOMING_DASH -> "DASH";
            case Upgrades.AIR_JUMP -> "AIRJ";
            case Upgrades.GROUND_POUND -> "POUND";
            case Upgrades.POWER -> "POW";
            case Upgrades.MAGNET -> "MAG";
            case Upgrades.ARMOR -> "ARM";
            case Upgrades.HASTE -> "HST";
            case Upgrades.GREED -> "GRD";
            case Upgrades.SPRING_HEELS -> "HEEL";
            case Upgrades.COMBO_KEEPER -> "KEEP";
            default -> "BAR";
        };
    }

    private static void drawIntro(ObjectServices s, Stage stage) {
        int frames = stage.phaseFrames;
        float alpha = Math.min(1f, Math.min(frames / 10f, (Stage.INTRO_FRAMES - frames) / 15f));
        var arena = stage.arena();
        Draw.centred(s, Stages.name(arena.stage()), 92, 2, Draw.GOLD, alpha);
        String goal = arena.stage() == Stages.DEZ ? "DEFEAT SILVER SONIC!"
                : stage.endless() ? "ENDLESS SURVIVAL!" : "SURVIVE " + clock(stage.survivalSeconds() * 60) + "!";
        Draw.centred(s, goal, 114, 2, Draw.WHITE, alpha);
        Draw.centred(s, stage.endless() ? "ESC / BACK: BANK RINGS AND LEAVE"
                : "BOUNCE ON BADNIKS TO KEEP YOUR COMBO GOING", 140, 1, Draw.CYAN, alpha);
    }

    // ---- Level up ----
    private static void drawLevelUp(ObjectServices s, Stage stage, RunState run) {
        int width = s.camera().getWidth();
        int cardH = 34;
        int rows = stage.levelUpRows();
        int panelW = 300, panelH = 30 + stage.cardCount * (cardH + 4) + (rows > stage.cardCount ? 16 : 0);
        int left = (width - panelW) / 2, top = Math.max(4, (224 - panelH) / 2);
        Draw.panel(s, left, top, panelW, panelH, 0.95f);
        Draw.centred(s, "LEVEL UP!", top + 6, 2, Draw.GOLD, 1f);
        if (run.pendingLevels > 1) {
            String more = "+" + (run.pendingLevels - 1) + " MORE";
            Draw.shadow(s, more, left + panelW - 6 - Draw.width(more, 1), top + 8, 1, Draw.GREEN, 1f);
        }
        for (int i = 0; i < stage.cardCount; i++) {
            int y = top + 26 + i * (cardH + 4);
            boolean selected = stage.menuIndex == i;
            int card = stage.cards[i];
            Draw.rect(s, left + 6, y, panelW - 12, cardH, selected ? 0x2A3C90 : 0x141E58, 1f);
            if (selected) Draw.outline(s, left + 6, y, panelW - 12, cardH, (stage.frameTick / 6) % 2 == 0 ? Draw.GOLD : Draw.WHITE, 1f);
            if (card < 0) {
                Draw.shadow(s, "RING BONUS", left + 14, y + 5, 1, Draw.GOLD, 1f);
                Draw.shadow(s, "EVERYTHING IS MAXED: +25 RINGS", left + 14, y + 17, 1, Draw.WHITE, 1f);
                continue;
            }
            int next = run.level(card) + 1;
            Draw.shadow(s, Upgrades.name(card), left + 14, y + 4, 1, Upgrades.kindColour(card), 1f);
            String level = next == 1 ? "NEW!" : "LV " + (next - 1) + ">" + next;
            Draw.shadow(s, level, left + panelW - 14 - Draw.width(level, 1), y + 4, 1, next == 1 ? Draw.GREEN : Draw.WHITE, 1f);
            String kind = Upgrades.kindName(card);
            Draw.text(s, kind, left + 22 + Draw.width(Upgrades.name(card), 1), y + 4, 1, Draw.GREY, 1f);
            String[] lines = Upgrades.describe(card, next);
            Draw.shadow(s, lines[0], left + 14, y + 14, 1, Draw.WHITE, 1f);
            Draw.shadow(s, lines[1], left + 14, y + 23, 1, Draw.WHITE, 1f);
        }
        Draw.centred(s, "UP/DOWN CHOOSE   ENTER / START CONFIRM", 214, 1, Draw.CYAN, 1f);
        if (rows > stage.cardCount) {
            int y = top + 26 + stage.cardCount * (cardH + 4);
            boolean selected = stage.menuIndex == stage.cardCount;
            String reroll = (selected ? "> " : "") + "REROLL (" + run.rerolls + " LEFT)";
            Draw.centred(s, reroll, y + 2, 1, selected ? Draw.GOLD : Draw.GREY, 1f);
        }
    }

    // ---- Camp ----
    private static void drawCamp(ObjectServices s, Stage stage) {
        var profile = s.gameService(Profile.class);
        int width = s.camera().getWidth();
        int panelW = 360, left = (width - panelW) / 2, top = 6;
        Draw.panel(s, left, top, panelW, 212, 0.93f);
        Draw.centred(s, "SONIC SURVIVORS", top + 6, 3, Draw.GOLD, 1f);
        Draw.centred(s, "CAMP - " + Stages.name(stage.arena().stage()), top + 32, 1, Draw.CYAN, 1f);
        String bank = "RING BANK " + profile.bank;
        Draw.shadow(s, bank, left + 10, top + 46, 1, Draw.YELLOW, 1f);
        drawEmeralds(s, profile, left + panelW - 10 - 7 * 12, top + 44);
        int y = top + 62;
        for (int row = 0; row < Profile.SHOP_COUNT + 3; row++) {
            boolean selected = stage.menuIndex == row;
            int colour = selected ? Draw.GOLD : Draw.WHITE;
            if (selected) Draw.rect(s, left + 6, y - 2, panelW - 12, 11, 0x2A3C90, 1f);
            if (row == Stage.CAMP_START) {
                Draw.shadow(s, (selected ? "> " : "  ") + "START RUN", left + 10, y, 1, selected ? Draw.GREEN : Draw.GREEN, 1f);
            } else if (row == Stage.CAMP_MODE) {
                String label = stage.arena().stage() == Stages.DEZ ? "MODE: BOSS FINALE"
                        : !profile.extendedModesUnlocked() ? "MODE: 2 MIN (CLEAR A BOSS TO UNLOCK)"
                        : "MODE: " + RunState.modeName(profile.selectedMode()) + " (ENTER TO CHANGE)";
                Draw.shadow(s, (selected ? "> " : "  ") + label, left + 10, y, 1, colour, 1f);
            } else if (row == Stage.CAMP_TITLE) {
                Draw.shadow(s, (selected ? "> " : "  ") + "BACK TO TITLE", left + 10, y, 1, colour, 1f);
            } else {
                int item = row - 1;
                int level = profile.shop[item], max = Profile.shopMax(item);
                Draw.shadow(s, (selected ? "> " : "  ") + Profile.shopName(item), left + 10, y, 1, colour, 1f);
                Draw.shadow(s, level + "/" + max, left + 112, y, 1, level >= max ? Draw.GREEN : Draw.GREY, 1f);
                Draw.shadow(s, Profile.shopEffect(item), left + 144, y, 1, Draw.GREY, 1f);
                String cost = level >= max ? "MAX" : Integer.toString(Profile.shopCost(item, level));
                Draw.shadow(s, cost, left + panelW - 10 - Draw.width(cost, 1), y, 1,
                        level >= max ? Draw.GREEN : profile.canBuy(item) ? Draw.YELLOW : Draw.RED, 1f);
            }
            y += row == Stage.CAMP_START || row == Profile.SHOP_COUNT ? 14 : 11;
        }
        int records = top + 176;
        Draw.shadow(s, "RUNS " + profile.runs + "  WINS " + profile.wins + "  BEST ZONES " + profile.bestStages
                + "  BEST COMBO " + profile.bestCombo, left + 10, records, 1, Draw.GREY, 1f);
        // Owned emerald powers, one at a time.
        int owned = profile.emeraldCount();
        if (owned > 0) {
            // With all seven, an eighth entry names the set bonus.
            int entries = owned + (profile.allEmeralds() ? 1 : 0);
            int pick = (stage.frameTick / 150) % entries;
            String line = "ALL SEVEN: +25% DAMAGE, SUPER AT 50 RINGS";
            for (int i = 0; i < Profile.EMERALDS && pick < owned; i++) {
                if (!profile.hasEmerald(i) || pick-- > 0) continue;
                line = Profile.emeraldName(i) + " EMERALD: " + Profile.emeraldEffect(i);
                break;
            }
            Draw.centred(s, line, top + 187, 1, line.startsWith("ALL") ? Draw.GOLD : Draw.GREEN, 1f);
        }
        Draw.centred(s, "UP/DOWN CHOOSE   ENTER BUY / START", top + 199, 1, Draw.CYAN, 1f);
    }

    private static void drawEmeralds(ObjectServices s, Profile profile, int x, int y) {
        int[] colours = {0x40E040, 0xE0E040, 0x4080FF, 0xFF60A0, 0xE04040, 0xE0E0E0, 0x40E0E0};
        for (int i = 0; i < Profile.EMERALDS; i++) {
            boolean owned = profile.hasEmerald(i);
            int cx = x + i * 12 + 5;
            for (int dy = 0; dy < 9; dy++) {
                int half = dy < 3 ? 2 + dy : 6 - (dy - 3);
                if (half < 0) continue;
                Draw.rect(s, cx - half, y + dy, half * 2 + 1, 1, owned ? colours[i] : 0x303850, 1f);
            }
        }
    }

    // ---- Zone clear ----
    private static void drawClear(ObjectServices s, Stage stage, RunState run) {
        int width = s.camera().getWidth();
        var arena = stage.arena();
        int next = arena.stage() + 1;
        int panelW = 320, left = (width - panelW) / 2, top = 24;
        int panelH = 92 + stage.clearChoices * 12;
        Draw.panel(s, left, top, panelW, panelH, 0.93f);
        Draw.centred(s, Stages.name(arena.stage()) + " CLEAR!", top + 6, 2, Draw.GOLD, 1f);
        Draw.shadow(s, "BADNIKS " + stage.stageKills + "   BEST COMBO " + run.bestCombo + "   LV " + (run.level + 1),
                left + 10, top + 26, 1, Draw.WHITE, 1f);
        if (stage.emeraldNew) {
            Draw.shadow(s, "CHAOS EMERALD! " + Profile.emeraldEffect(arena.stage()), left + 10, top + 38, 1,
                    Draw.GREEN, 1f);
        }
        Draw.shadow(s, "CHOOSE THE NEXT ZONE:", left + 10, top + 54, 1, Draw.CYAN, 1f);
        int y = top + 68;
        for (int i = 0; i < stage.clearChoices; i++) {
            boolean selected = stage.menuIndex == i;
            String label = i == stage.clearChoices - 1 ? "RETIRE AND BANK YOUR RINGS"
                    : Stages.name(next) + (Stages.acts(next) > 1 ? " ACT " + (i + 1) : "") + " - "
                    + Stages.actFlavour(i);
            if (selected) Draw.rect(s, left + 6, y - 2, panelW - 12, 11, 0x2A3C90, 1f);
            Draw.shadow(s, (selected ? "> " : "  ") + label, left + 10, y, 1, selected ? Draw.GOLD : Draw.WHITE, 1f);
            y += 12;
        }
    }

    // ---- Game over / victory ----
    private static void drawGameOver(ObjectServices s, Stage stage, boolean won) {
        var run = s.gameService(RunState.class);
        var profile = s.gameService(Profile.class);
        int width = s.camera().getWidth();
        int panelW = 300, left = (width - panelW) / 2, top = 30;
        float alpha = Math.min(1f, stage.phaseFrames / 30f);
        Draw.panel(s, left, top, panelW, 156, 0.93f * alpha);
        Draw.centred(s, won ? "YOU BEAT EGGMAN!" : "GAME OVER", top + 8, 3, won ? Draw.GOLD : Draw.RED, alpha);
        int y = top + 40;
        String[] lines = {
                "ZONES CLEARED  " + run.stagesCleared,
                "BADNIKS        " + run.kills,
                "BEST COMBO     " + run.bestCombo,
                "LEVEL          " + (run.level + 1),
                "TIME           " + clock(run.frames),
        };
        for (String line : lines) {
            Draw.shadow(s, line, left + 40, y, 1, Draw.WHITE, alpha);
            y += 11;
        }
        Draw.shadow(s, "RINGS BANKED +" + stage.bankedThisRun + "   BANK " + profile.bank, left + 40, y + 4, 1,
                Draw.YELLOW, alpha);
        if (stage.phaseFrames < 60) return;
        y = top + 128;
        String[] options = {"TRY AGAIN", "TITLE"};
        for (int i = 0; i < options.length; i++) {
            boolean selected = stage.menuIndex == i;
            Draw.shadow(s, (selected ? "> " : "  ") + options[i], left + 110, y + i * 12, 1,
                    selected ? Draw.GOLD : Draw.WHITE, 1f);
        }
    }
}
