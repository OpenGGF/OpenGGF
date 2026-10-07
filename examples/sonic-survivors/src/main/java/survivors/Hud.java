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
        boolean menu = stage.menuShowing();
        if (run != null && run.active && player != null && !player.getDead() && rings != null && !menu) {
            int n = stage.orbitRings(), distance = stage.orbitDistance();
            for (int i = 0; i < n; i++) {
                double a = (stage.orbitAngle / 1024.0 + i / (double) n) * Math.PI * 2;
                rings.drawRingAt(player.getCentreX() + (int) (Math.cos(a) * distance),
                        player.getCentreY() + (int) (Math.sin(a) * distance), stage.frameTick + i * 3);
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
                    if (rings != null && !menu) rings.drawRingAt(x, y, stage.frameTick * 2 + i);
                }
                case Stage.P_FLICKY -> {
                    if (animal != null) animal.drawFrameIndex(3 + (stage.frameTick / 4) % 2, x, y, stage.pVX[i] > 0, false);
                    else Draw.rectWorld(s, x - 3, y - 3, 6, 6, Draw.BLUE, 1f);
                }
                case Stage.P_LANCE -> drawLance(s, x, y, stage.pVY[i] < 0, stage.frameTick);
                default -> drawBoom(s, x, y, stage.pVX[i] < 0, stage.frameTick);
            }
        }
        for (int i = 0; i < Stage.MAX_E; i++) {
            int kind = stage.eKind[i];
            if (kind == 0) continue;
            int age = stage.eAge[i];
            switch (kind) {
                case Stage.E_NUMBER -> Draw.smallWorld(s, Integer.toString(stage.eA[i]),
                        stage.eX[i] - (Integer.toString(stage.eA[i]).length() * 4 - 1) * stage.eC[i] / 2,
                        stage.eY[i] - age / 2, stage.eC[i], stage.eB[i], Math.min(1f, (40 - age) / 12f));
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
                case Stage.E_WARN -> {
                    // A falling hazard's landing spot: a blinking chevron that tightens as it nears.
                    if ((age / 4) % 2 == 0 || age > 30) {
                        int w = 14 - age / 5, x = stage.eX[i], y = stage.eY[i];
                        Draw.rectWorld(s, x - w - 1, y - 1, w * 2 + 2, 5, Draw.NAVY, 0.8f);
                        Draw.rectWorld(s, x - w, y, w * 2, 3, Draw.RED, 1f);
                        // A downward chevron over the spot.
                        for (int k = 0; k < 4; k++) Draw.rectWorld(s, x - 4 + k, y - 12 + k, 9 - 2 * k, 1, Draw.RED, 1f);
                    }
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

    /** Cross Lance's vertical lance: the sonic boom's arcs turned upright. */
    private static void drawLance(ObjectServices s, int x, int y, boolean up, int tick) {
        int dir = up ? -1 : 1;
        for (int arc = 0; arc < 3; arc++) {
            int r = 6 + arc * 4;
            int colour = arc == 0 ? Draw.WHITE : (tick / 2 + arc) % 2 == 0 ? Draw.GOLD : Draw.ORANGE;
            for (int dx = -r; dx <= r; dx++) {
                int dy = (int) Math.sqrt(r * r - dx * dx);
                Draw.rectWorld(s, x + dx, y + dir * dy - (up ? 2 : 0), 1, 2, colour, 0.85f);
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
        if (stage.overlay == Stage.OVERLAY_CHEST) drawChest(s, stage, run);
        if (stage.phase == Stage.CLEAR) drawClear(s, stage, run);
    }

    private static String clock(int frames) {
        int seconds = Math.max(0, (frames + 59) / 60);
        return seconds / 60 + (seconds % 60 < 10 ? ":0" : ":") + seconds % 60;
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
        if (run.rule(Rules.NO_FEVER)) {
            Draw.shadow(s, "FEVER OFF", 8, y + 10, 1, Draw.GREY, 1f);
        } else {
            int needed = run.feverCharge();
            String fever = stage.feverFrames > 0 ? "FEVER " + clock(stage.feverFrames)
                    : stage.feverCooldown > 0 ? "RECHARGE " + clock(stage.feverCooldown)
                    : "FEVER " + stage.feverCharge + "/" + needed;
            Draw.shadow(s, fever, 8, y + 10, 1, stage.feverFrames > 0 ? Draw.PINK : Draw.CYAN, 1f);
            double charge = stage.feverFrames > 0 ? stage.feverFrames / (double) Stage.FEVER_DURATION
                    : stage.feverCooldown > 0 ? 1 - stage.feverCooldown / (double) Stage.FEVER_RECOVERY
                    : stage.feverCharge / (double) needed;
            Draw.bar(s, 8, y + 19, 80, 3, charge, stage.feverFrames > 0 ? Draw.PINK : Draw.CYAN, 1f);
        }
        if (stage.phase == Stage.FIGHT) {
            boolean event = stage.eventFrames > 0;
            String beat = event ? Stages.eventName(stage.arena().stage()) : "W" + stage.encounterNumber() + " " + stage.encounterName();
            Draw.centred(s, beat, 36, 1, event ? Draw.RED
                    : stage.encounterBeat() >= 18 && stage.encounterBeat() < 25 ? Draw.ORANGE : Draw.CYAN, 1f);
            Draw.bar(s, s.camera().getWidth() / 2 - 40, 46, 80, 3, event ? stage.eventFrames / (double) Stage.EVENT_FRAMES
                    : (stage.fightFrames % Stage.ENCOUNTER_FRAMES) / (double) Stage.ENCOUNTER_FRAMES,
                    event ? Draw.RED : Draw.GOLD, 1f);
        }
        Draw.rect(s, 4, 197, 182, 11, 0x080E30, 0.90f);
        Draw.shadow(s, "WEAPONS " + run.occupiedSlots(true) + "/3  BUFFS " + run.occupiedSlots(false) + "/3",
                8, 200, 1, Draw.WHITE, 1f);
        if (run.readyEvolutions() > 0 && (stage.frameTick / 20) % 3 != 0) {
            Draw.rect(s, 190, 197, 98, 11, 0x080E30, 0.90f);
            Draw.shadow(s, "EVOLUTION READY", 194, 200, 1, Draw.PINK, 1f);
        }
        // Owned upgrades, compact.
        int x = 8;
        y = 210;
        for (int id = 0; id < Upgrades.COUNT; id++) {
            int level = run.level(id);
            if (level <= 0) continue;
            String tag = abbreviation(id) + (run.evolvedWeapon(id) ? "*" : Integer.toString(level));
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
            if (stage.endless()) Draw.centred(s, "UNLIMITED", 25, 1, Draw.CYAN, 1f);
            boolean hurry = stage.phase == Stage.FIGHT && !stage.endless() && stage.stageFrames < 10 * 60;
            Draw.centred(s, time, 6, 2, hurry && (stage.frameTick / 10) % 2 == 0 ? Draw.RED : Draw.WHITE, 1f);
        }
        // Zone and kills, top right.
        var arena = stage.arena();
        String zone = Stages.name(arena.stage()) + (Stages.acts(arena.stage()) > 1 ? " " + (arena.act() + 1) : "");
        Draw.shadow(s, zone, width - 8 - Draw.width(zone, 1), 8, 1, Draw.GOLD, 1f);
        String kills = "KO " + run.kills;
        Draw.shadow(s, kills, width - 8 - Draw.width(kills, 1), 18, 1, Draw.WHITE, 1f);
        if (run.rules != 0) {
            String rules = "RULES +" + Rules.totalBonus(run.rules) + "%";
            Draw.shadow(s, rules, width - 8 - Draw.width(rules, 1), 196, 1, Draw.RED, 1f);
        }
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
            case Upgrades.TWIN_LANCE -> "LNC";
            case Upgrades.METEOR -> "MET";
            case Upgrades.PULSE -> "PLS";
            case Upgrades.REACH -> "AMP";
            case Upgrades.RECOVERY -> "WND";
            case Upgrades.SCHOLAR -> "STDY";
            default -> "BAR";
        };
    }

    private static void drawIntro(ObjectServices s, Stage stage) {
        int frames = stage.phaseFrames;
        float alpha = Math.min(1f, Math.min(frames / 10f, (Stage.INTRO_FRAMES - frames) / 15f));
        var arena = stage.arena();
        Draw.centred(s, Stages.name(arena.stage()), 92, 2, Draw.GOLD, alpha);
        String goal = arena.stage() == Stages.DEZ ? "DEFEAT SILVER SONIC!"
                : stage.endless() ? "UNLIMITED SURVIVAL!" : "SURVIVE " + clock(stage.survivalSeconds() * 60) + "!";
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
            if (card == Stage.CARD_OVERDRIVE) {
                Draw.shadow(s, "OVERDRIVE", left + 14, y + 5, 1, Draw.RED, 1f);
                Draw.shadow(s, "BUILD MAXED: ALL DAMAGE +6% (" + run.overdrive + " TAKEN)", left + 14, y + 17, 1, Draw.WHITE, 1f);
                continue;
            }
            if (card < 0) {
                Draw.shadow(s, "RING BONUS", left + 14, y + 5, 1, Draw.GOLD, 1f);
                Draw.shadow(s, "BUILD MAXED: +" + stage.maxedRingBonus() + " RINGS", left + 14, y + 17, 1, Draw.WHITE, 1f);
                continue;
            }
            int next = run.level(card) + 1;
            Draw.shadow(s, Upgrades.name(card), left + 14, y + 4, 1, Upgrades.kindColour(card), 1f);
            MenuArt.CardText text = s.gameService(MenuArt.class).cards[card][next];
            String level = text.level();
            Draw.shadow(s, level, left + panelW - 14 - Draw.width(level, 1), y + 4, 1, next == 1 ? Draw.GREEN : Draw.WHITE, 1f);
            String kind = (Upgrades.weapon(card) ? "WEAPON " : "BUFF ") + (run.has(card) ? "+" : "NEW");
            Draw.text(s, kind, left + 22 + Draw.width(Upgrades.name(card), 1), y + 4, 1, Draw.GREY, 1f);
            Draw.shadow(s, text.first(), left + 14, y + 14, 1, Draw.WHITE, 1f);
            Draw.shadow(s, text.second(), left + 14, y + 23, 1, Draw.WHITE, 1f);
            String hint = s.gameService(MenuArt.class).evolutionHints[card];
            // Only name a pairing the player can actually complete this run.
            if (hint != null && run.inPool(Upgrades.evoCounterpart(card))) Draw.shadow(s, hint, left + panelW - 14 - Draw.width(hint, 1), y + 23, 1, Draw.PINK, 1f);
        }
        Draw.centred(s, "UP/DOWN CHOOSE   ENTER / START CONFIRM", 214, 1, Draw.CYAN, 1f);
        if (rows > stage.cardCount) {
            int y = top + 26 + stage.cardCount * (cardH + 4);
            boolean selected = stage.menuIndex == stage.cardCount;
            String reroll = (selected ? "> " : "") + "REROLL (" + run.rerolls + " LEFT)";
            Draw.centred(s, reroll, y + 2, 1, selected ? Draw.GOLD : Draw.GREY, 1f);
        }
    }

    // ---- Treasure chest ----
    private static void drawChest(ObjectServices s, Stage stage, RunState run) {
        int width = s.camera().getWidth();
        int panelW = 300, panelH = 44 + stage.chestCount * 19, left = (width - panelW) / 2, top = Math.max(8, (224 - panelH) / 2);
        Draw.panel(s, left, top, panelW, panelH, 0.95f);
        boolean evolution = false;
        for (int i = 0; i < stage.chestCount; i++) evolution |= stage.chestItems[i] >= 100;
        Draw.centred(s, evolution ? "EVOLUTION!" : "TREASURE!", top + 6, 2, evolution ? Draw.PINK : Draw.GOLD, 1f);
        int shown = Math.min(stage.chestCount, 1 + (stage.phaseFrames - stage.phaseFramesAtOverlay) / 8);
        for (int i = 0; i < shown; i++) {
            int item = stage.chestItems[i], y = top + 26 + i * 19;
            if (item >= 100) {
                int evo = item - 100;
                Draw.shadow(s, Upgrades.evoName(evo), left + 12, y, 1, Draw.PINK, 1f);
                Draw.shadow(s, Upgrades.name(Upgrades.evoBase(evo)) + " EVOLVED: " + Upgrades.evoEffect(evo), left + 12, y + 10, 1,
                        Draw.WHITE, 1f);
            } else if (item >= 0) {
                Draw.shadow(s, Upgrades.name(item) + " LV " + stage.chestLevels[i], left + 12, y, 1, Upgrades.kindColour(item), 1f);
                Draw.shadow(s, Upgrades.kindName(item), left + 12, y + 10, 1, Draw.GREY, 1f);
            } else {
                Draw.shadow(s, "RING HOARD", left + 12, y, 1, Draw.GOLD, 1f);
                Draw.shadow(s, "+" + stage.chestRings / Math.max(1, ringPrizes(stage)) + " RINGS", left + 12, y + 10, 1, Draw.WHITE, 1f);
            }
        }
        if (stage.phaseFrames - stage.phaseFramesAtOverlay >= 40) {
            Draw.centred(s, "ENTER / START CONTINUE", top + panelH - 12, 1, Draw.CYAN, 1f);
        }
    }

    private static int ringPrizes(Stage stage) {
        int count = 0;
        for (int i = 0; i < stage.chestCount; i++) if (stage.chestItems[i] < 0) count++;
        return count;
    }

    // ---- Camp ----
    private static void drawCamp(ObjectServices s, Stage stage) {
        if (stage.campPage == Stage.PAGE_RULES) { drawRules(s, stage); return; }
        if (stage.campPage == Stage.PAGE_RECORDS) { drawRecords(s, stage); return; }
        if (stage.campPage == Stage.PAGE_SHOP) { drawShop(s, stage); return; }
        var profile = s.gameService(Profile.class);
        int width = s.camera().getWidth();
        int panelW = 340, left = (width - panelW) / 2, top = 6;
        Draw.panel(s, left, top, panelW, 212, 0.93f);
        Draw.centred(s, "SONIC SURVIVORS", top + 6, 3, Draw.GOLD, 1f);
        Draw.centred(s, "CAMP - " + Stages.name(stage.arena().stage()), top + 30, 1, Draw.CYAN, 1f);
        Draw.shadow(s, "RING BANK " + profile.bank, left + 10, top + 44, 1, Draw.YELLOW, 1f);
        drawEmeralds(s, profile, left + panelW - 10 - 7 * 12, top + 42);
        boolean tails = s.camera().getFocusedSprite() instanceof com.openggf.sprites.playable.Tails;
        int y = top + 62;
        for (int row = 0; row < Stage.CAMP_ROWS; row++) {
            boolean selected = stage.menuIndex == row;
            int colour = selected ? Draw.GOLD : Draw.WHITE;
            if (selected) Draw.rect(s, left + 6, y - 3, panelW - 12, 14, 0x2A3C90, 1f);
            String cursor = selected ? "> " : "  ";
            String label, detail = "";
            int detailColour = Draw.GREY;
            switch (row) {
                case Stage.CAMP_START -> {
                    label = "START RUN";
                    colour = Draw.GREEN;
                    detail = tails ? "TAILS: HOVER, WIDER MAGNET" : "SONIC: FEVER IN 10, +30% COMBO";
                    detailColour = Draw.CYAN;
                }
                case Stage.CAMP_SHOP -> {
                    label = "RING SHOP";
                    int affordable = 0;
                    for (int item = 0; item < Profile.SHOP_COUNT; item++) if (profile.canBuy(item)) affordable++;
                    detail = affordable > 0 ? affordable + " AFFORDABLE" : "SAVE UP RINGS";
                    detailColour = affordable > 0 ? Draw.YELLOW : Draw.GREY;
                }
                case Stage.CAMP_MODE -> {
                    label = "MODE: " + (stage.arena().stage() == Stages.DEZ ? "BOSS FINALE" : RunState.modeName(profile.selectedMode()));
                    detail = profile.extendedModesUnlocked() ? "ENTER TO CHANGE" : "CLEAR A BOSS TO UNLOCK";
                }
                case Stage.CAMP_RULES -> {
                    int rules = profile.selectedRules();
                    label = "EGGMAN'S RULES";
                    detail = !profile.extendedModesUnlocked() ? "CLEAR A BOSS TO UNLOCK"
                            : rules == 0 ? "NONE" : Integer.bitCount(rules) + " ON, +" + Rules.totalBonus(rules) + "% RINGS";
                    detailColour = rules != 0 ? Draw.RED : Draw.GREY;
                }
                case Stage.CAMP_RECORDS -> {
                    label = "RECORDS AND COLLECTION";
                    detail = profile.unlockedUpgrades() + "/" + Upgrades.COUNT + " UPGRADES";
                }
                default -> label = "BACK TO TITLE";
            }
            Draw.shadow(s, cursor + label, left + 10, y, 1, selected && row != Stage.CAMP_START ? Draw.GOLD : colour, 1f);
            Draw.shadow(s, detail, left + panelW - 10 - Draw.width(detail, 1), y, 1, detailColour, 1f);
            y += 16;
        }
        Draw.shadow(s, "RUNS " + profile.runs + "  WINS " + profile.wins + "  BEST ZONES " + profile.bestStages
                + "  BEST COMBO " + profile.bestCombo, left + 10, top + 166, 1, Draw.GREY, 1f);
        // Owned emerald powers, one at a time.
        int owned = profile.emeraldCount();
        if (owned > 0) {
            // With all seven, an eighth entry names the set bonus.
            int entries = owned + (profile.allEmeralds() ? 1 : 0);
            int pick = (stage.frameTick / 150) % entries;
            String line = "ALL SEVEN: +25% DAMAGE";
            for (int i = 0; i < Profile.EMERALDS && pick < owned; i++) {
                if (!profile.hasEmerald(i) || pick-- > 0) continue;
                line = Profile.emeraldName(i) + " EMERALD: " + Profile.emeraldEffect(i);
                break;
            }
            Draw.centred(s, line, top + 182, 1, line.startsWith("ALL") ? Draw.GOLD : Draw.GREEN, 1f);
        }
        Draw.centred(s, "UP/DOWN CHOOSE   ENTER SELECT", top + 199, 1, Draw.CYAN, 1f);
    }

    private static void drawShop(ObjectServices s, Stage stage) {
        var profile = s.gameService(Profile.class);
        int width = s.camera().getWidth();
        int panelW = 370, left = (width - panelW) / 2, top = 6;
        Draw.panel(s, left, top, panelW, 212, 0.95f);
        Draw.centred(s, "RING SHOP", top + 6, 2, Draw.GOLD, 1f);
        String bank = "RING BANK " + profile.bank;
        Draw.shadow(s, bank, left + 10, top + 26, 1, Draw.YELLOW, 1f);
        Draw.shadow(s, "EVERY RUN, FOREVER", left + panelW - 10 - Draw.width("EVERY RUN, FOREVER", 1), top + 26, 1, Draw.GREY, 1f);
        int y = top + 42;
        for (int row = 0; row <= Profile.SHOP_COUNT; row++) {
            boolean selected = stage.menuIndex == row;
            if (selected) Draw.rect(s, left + 6, y - 2, panelW - 12, 12, 0x2A3C90, 1f);
            String cursor = selected ? "> " : "  ";
            if (row == Profile.SHOP_COUNT) {
                Draw.shadow(s, cursor + "BACK TO CAMP", left + 10, y + 2, 1, selected ? Draw.GOLD : Draw.WHITE, 1f);
                break;
            }
            int level = profile.shop[row], max = Profile.shopMax(row);
            Draw.shadow(s, cursor + Profile.shopName(row), left + 10, y, 1, selected ? Draw.GOLD : Draw.WHITE, 1f);
            String rank = max >= 10 ? "LV " + level : level + "/" + max;
            Draw.shadow(s, rank, left + 132, y, 1, level >= max ? Draw.GREEN : Draw.CYAN, 1f);
            Draw.shadow(s, Profile.shopEffect(row), left + 180, y, 1, Draw.GREY, 1f);
            String cost = level >= max ? "MAX" : Integer.toString(Profile.shopCost(row, level));
            Draw.shadow(s, cost, left + panelW - 10 - Draw.width(cost, 1), y, 1,
                    level >= max ? Draw.GREEN : profile.canBuy(row) ? Draw.YELLOW : Draw.RED, 1f);
            y += 14;
        }
        Draw.centred(s, "ENTER BUYS A LEVEL - PRICES RISE EACH TIME", top + 199, 1, Draw.CYAN, 1f);
    }

    private static void drawRules(ObjectServices s, Stage stage) {
        var profile = s.gameService(Profile.class);
        int width = s.camera().getWidth();
        int panelW = 340, left = (width - panelW) / 2, top = 20;
        Draw.panel(s, left, top, panelW, 184, 0.95f);
        Draw.centred(s, "EGGMAN'S RULES", top + 6, 2, Draw.RED, 1f);
        int bonus = Rules.totalBonus(profile.selectedRules());
        Draw.centred(s, bonus == 0 ? "HARDER RUNS BANK MORE RINGS" : "HARDER RUNS BANK MORE RINGS: NOW +" + bonus + "%",
                top + 26, 1, bonus == 0 ? Draw.GREY : Draw.YELLOW, 1f);
        int y = top + 42;
        for (int row = 0; row <= Rules.COUNT; row++) {
            boolean selected = stage.menuIndex == row;
            if (selected) Draw.rect(s, left + 6, y - 2, panelW - 12, 19, 0x2A3C90, 1f);
            String cursor = selected ? "> " : "  ";
            if (row == Rules.COUNT) {
                Draw.shadow(s, cursor + "BACK TO CAMP", left + 10, y + 4, 1, selected ? Draw.GOLD : Draw.WHITE, 1f);
                break;
            }
            boolean on = (profile.rules & 1 << row) != 0;
            Draw.shadow(s, cursor + Rules.name(row), left + 10, y, 1, on ? Draw.RED : selected ? Draw.GOLD : Draw.WHITE, 1f);
            String state = on ? "ON  +" + Rules.bonus(row) + "%" : "OFF +" + Rules.bonus(row) + "%";
            Draw.shadow(s, state, left + panelW - 10 - Draw.width(state, 1), y, 1, on ? Draw.RED : Draw.GREY, 1f);
            Draw.shadow(s, Rules.effect(row), left + 22, y + 9, 1, Draw.GREY, 1f);
            y += 20;
        }
    }

    static String tabName(int tab) {
        return switch (tab) {
            case 0 -> "RECORDS";
            case 1 -> "UPGRADES";
            case 2 -> "EVOLUTIONS";
            default -> "BESTIARY";
        };
    }

    private static void drawRecords(ObjectServices s, Stage stage) {
        var profile = s.gameService(Profile.class);
        int width = s.camera().getWidth();
        int panelW = 380, left = (width - panelW) / 2, top = 4;
        Draw.panel(s, left, top, panelW, 216, 0.95f);
        int x = left + 10;
        for (int tab = 0; tab < Stage.RECORD_TABS; tab++) {
            String name = tabName(tab);
            boolean on = stage.recordsTab == tab;
            if (on) Draw.rect(s, x - 3, top + 4, Draw.width(name, 1) + 6, 11, 0x2A3C90, 1f);
            Draw.shadow(s, name, x, top + 6, 1, on ? Draw.GOLD : Draw.GREY, 1f);
            x += Draw.width(name, 1) + 14;
        }
        int y = top + 22;
        switch (stage.recordsTab) {
            case 0 -> {
                String[] lines = {
                        "RUNS " + profile.runs + "   WINS " + profile.wins,
                        "BEST ZONES CLEARED " + profile.bestStages,
                        "BEST COMBO " + profile.bestCombo + "   BEST LEVEL " + profile.bestLevel,
                        "BEST RUN KOS " + profile.bestKills + "   TOTAL KOS " + profile.totalKills,
                        "ELITES DEFEATED " + profile.totalElites,
                        "RINGS BANKED IN TOTAL " + profile.bankedTotal,
                        "CHAOS EMERALDS " + profile.emeraldCount() + "/7",
                };
                for (String line : lines) { Draw.shadow(s, line, left + 12, y, 1, Draw.WHITE, 1f); y += 11; }
                y += 4;
                Draw.shadow(s, "LONGEST UNLIMITED SURVIVAL", left + 12, y, 1, Draw.CYAN, 1f);
                y += 11;
                for (int stageIndex = 0; stageIndex < Stages.DEZ; stageIndex++) {
                    int seconds = profile.bestUnlimited[stageIndex];
                    String time = seconds <= 0 ? "--" : clock(seconds * 60);
                    int column = stageIndex % 3, row = stageIndex / 3;
                    Draw.shadow(s, Stages.name(stageIndex) + " " + time, left + 12 + column * 122, y + row * 11, 1,
                            seconds > 0 ? Draw.WHITE : Draw.GREY, 1f);
                }
            }
            case 1 -> {
                int pool = profile.upgradePool();
                for (int id = 0; id < Upgrades.COUNT; id++) {
                    boolean unlocked = Upgrades.core(id) || (pool & 1 << id) != 0;
                    int column = id % 2, row = id / 2;
                    int cx = left + 12 + column * 184, cy = y + row * 14;
                    Draw.shadow(s, Upgrades.name(id), cx, cy, 1, unlocked ? Upgrades.kindColour(id) : 0x606880, 1f);
                    if (!unlocked) {
                        String need = Upgrades.unlockShort(id);
                        Draw.shadow(s, need, cx + 176 - Draw.width(need, 1), cy, 1, Draw.GREY, 1f);
                    }
                }
            }
            case 2 -> {
                for (int evo = 0; evo < Upgrades.EVOLUTIONS; evo++) {
                    boolean seen = (profile.evolutionsSeen & 1 << evo) != 0;
                    int column = evo % 2, row = evo / 2;
                    int cx = left + 12 + column * 184, cy = y + row * 30;
                    Draw.shadow(s, seen ? Upgrades.evoName(evo) : "???", cx, cy, 1, seen ? Draw.PINK : Draw.GREY, 1f);
                    Draw.text(s, "MAX " + Upgrades.name(Upgrades.evoBase(evo)), cx + 6, cy + 9, 1, Draw.WHITE, 1f);
                    Draw.text(s, "+ " + Upgrades.name(Upgrades.evoPartner(evo)), cx + 6, cy + 18, 1, Draw.GREY, 1f);
                }
            }
            default -> {
                for (int id = 0; id < Species.COUNT; id++) {
                    int kills = profile.speciesKills[id];
                    int column = id % 3, row = id / 3;
                    String line = kills > 0 ? Species.of(id).name() + " " + kills : "???";
                    Draw.shadow(s, line, left + 12 + column * 122, y + row * 13, 1, kills > 0 ? Draw.WHITE : Draw.GREY, 1f);
                }
            }
        }
        Draw.centred(s, "LEFT/RIGHT TURN THE PAGE   ENTER BACK TO CAMP", top + 205, 1, Draw.CYAN, 1f);
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
        if (stage.newUnlocks != 0) {
            Draw.shadow(s, "UNLOCKED: " + unlockList(stage.newUnlocks), left + 10, top + 44, 1, Draw.PINK, 1f);
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

    /** Upgrade names for an unlock mask, abbreviated past two. */
    static String unlockList(int mask) {
        var out = new StringBuilder();
        int shown = 0, total = Integer.bitCount(mask);
        for (int id = 0; id < Upgrades.COUNT; id++) {
            if ((mask & 1 << id) == 0) continue;
            if (shown == 2) { out.append(" +").append(total - 2).append(" MORE"); break; }
            if (shown > 0) out.append(", ");
            out.append(Upgrades.name(id));
            shown++;
        }
        return out.toString();
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
        String bonus = run.rules != 0 ? " (RULES +" + Rules.totalBonus(run.rules) + "%)" : "";
        Draw.shadow(s, "RINGS BANKED +" + stage.bankedThisRun + bonus + "   BANK " + profile.bank, left + 40, y + 4, 1,
                Draw.YELLOW, alpha);
        if (stage.newUnlocks != 0) {
            Draw.centred(s, "NEW UPGRADES: " + unlockList(stage.newUnlocks), y + 16, 1, Draw.PINK, alpha);
        }
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
