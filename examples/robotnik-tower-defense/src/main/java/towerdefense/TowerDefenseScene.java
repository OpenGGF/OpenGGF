package towerdefense;

import com.openggf.mods.scene.*;
import towerdefense.art.RomArt;
import towerdefense.core.*;
import towerdefense.ui.BattleView;
import towerdefense.ui.PixelFont;

/** Input and scene lifecycle; the rules core remains engine-independent. */
public final class TowerDefenseScene implements ModScene, DebuggableScene {
    private final byte[] fontBytes;
    private BattleView view;
    private Battlefield battle;
    private Records records = new Records(0, 0, 0);
    private String screen = "TITLE", returnScreen = "TITLE", focus = "GRID", message = "";
    private int ticks, site, kind, action, cursor, helpPage, messageTicks;
    private boolean paused, recorded, debugRun;

    public TowerDefenseScene(byte[] fontBytes) { this.fontBytes = fontBytes.clone(); }
    public Battlefield battlefield() { return battle; }
    public String screen() { return screen; }
    public boolean paused() { return paused; }

    @Override public void enter(SceneContext ctx) {
        view = new BattleView(new PixelFont(fontBytes), new RomArt(ctx.art().rom()));
        records = Records.parse(ctx.storage().read("records.txt").orElse("0,0,0"));
        ctx.audio().playMusic(0x2F); // S3K Data Select
    }

    @Override public void update(SceneContext ctx) {
        if (!paused && !screen.equals("HELP")) ticks++;
        if (messageTicks > 0 && --messageTicks == 0) message = "";
        switch (screen) {
            case "TITLE" -> titleInput(ctx);
            case "HELP" -> {
                if (left(ctx) || right(ctx) || ctx.mouse().wheel() != 0) helpPage = 1 - helpPage;
                if (accept(ctx) || back(ctx) || ctx.mouse().rightPressed() || ctx.keyPressed(SceneKeys.H)
                        || ctx.mouse().leftPressed() && ctx.mouse().over(16, 197, 368, 16)) screen = returnScreen;
            }
            case "BATTLE" -> battleInput(ctx);
            case "RESULT" -> {
                record(ctx);
                if (back(ctx) || ctx.mouse().rightPressed()) title(ctx);
                else if (accept(ctx) || ctx.mouse().leftPressed() && ctx.mouse().over(62, 177, 276, 13)) newGame(ctx);
            }
            default -> throw new IllegalStateException("Unknown screen " + screen);
        }
    }

    private void titleInput(SceneContext ctx) {
        if (up(ctx)) cursor = (cursor + 2) % 3;
        if (down(ctx)) cursor = (cursor + 1) % 3;
        int picked = accept(ctx) ? cursor : -1;
        for (int i = 0; i < 3; i++) {
            int y = i == 0 ? 139 : i == 1 ? 162 : 181;
            int h = i == 0 ? 18 : 14;
            if (ctx.mouse().over(112, y, 176, h) && ctx.mouse().lastInputWasMouse()) cursor = i;
            if (ctx.mouse().leftPressed() && ctx.mouse().over(112, y, 176, h)) picked = i;
        }
        if (back(ctx)) { ctx.exitToMasterTitle(); return; }
        switch (picked) {
            case 0 -> newGame(ctx);
            case 1 -> help("TITLE");
            case 2 -> ctx.exitToGameTitle();
            default -> { }
        }
    }

    private void newGame(SceneContext ctx) {
        battle = new Battlefield();
        site = kind = action = cursor = 0;
        focus = "GRID";
        screen = "BATTLE";
        paused = recorded = debugRun = false;
        message = "PICK AN EMPTY SITE. A / ENTER OPENS THE SHOP.";
        messageTicks = 360;
        ctx.audio().playMusic(0x0D); // Launch Base Act 1
    }

    private void title(SceneContext ctx) {
        screen = "TITLE";
        paused = false;
        cursor = 0;
        ctx.audio().playMusic(0x2F);
    }

    private void help(String from) { returnScreen = from; screen = "HELP"; helpPage = 0; }

    private void battleInput(SceneContext ctx) {
        if (battle.finished()) {
            screen = "RESULT";
            record(ctx);
            ctx.audio().playMusic(battle.phase().equals(Battlefield.WON) ? 0x29 : 0x27);
            return;
        }
        boolean pausePressed = ctx.keyPressed(SceneKeys.P) || ctx.buttonPressed(SceneButtons.START);
        if (paused) {
            if (pausePressed || back(ctx)) { paused = false; return; }
            if (up(ctx)) cursor = (cursor + 2) % 3;
            if (down(ctx)) cursor = (cursor + 1) % 3;
            int picked = accept(ctx) ? cursor : -1;
            for (int i = 0; i < 3; i++) {
                if (ctx.mouse().lastInputWasMouse() && ctx.mouse().over(124, 97 + i * 20, 152, 14)) cursor = i;
                if (ctx.mouse().leftPressed() && ctx.mouse().over(124, 97 + i * 20, 152, 14)) picked = i;
            }
            switch (picked) {
                case 0 -> paused = false;
                case 1 -> { record(ctx); newGame(ctx); }
                case 2 -> { record(ctx); title(ctx); }
                default -> { }
            }
            return;
        }
        // Backspace is the stock Start binding as well as this scene's cancel key.
        // Consume a selection cancel before considering Start, so one key has one action.
        if ((back(ctx) || ctx.mouse().rightPressed()) && !focus.equals("GRID")) {
            focus = "GRID";
            return;
        }
        if (pausePressed) { paused = true; cursor = 0; return; }
        if (back(ctx) || ctx.mouse().rightPressed()) {
            if (!focus.equals("GRID")) focus = "GRID";
            else { paused = true; cursor = 0; }
            return;
        }
        if (ctx.keyPressed(SceneKeys.H)) { help("BATTLE"); return; }
        for (int i = 0; i < 6; i++) {
            if (ctx.keyPressed(SceneKeys.DIGIT_1 + i)) { kind = i; focus = "SHOP"; }
        }
        if (ctx.keyPressed(SceneKeys.U)) perform(ctx, 0);
        if (ctx.keyPressed(SceneKeys.X)) perform(ctx, 1);
        if (ctx.keyPressed(SceneKeys.R)) perform(ctx, 2);
        if (ctx.keyPressed(SceneKeys.F)) perform(ctx, 3);
        if (ctx.keyPressed(SceneKeys.E)) perform(ctx, 4);
        navigation(ctx);
        mouseInput(ctx);
        boolean shortcut = ctx.keyPressed(SceneKeys.U) || ctx.keyPressed(SceneKeys.X)
                || ctx.keyPressed(SceneKeys.R) || ctx.keyPressed(SceneKeys.F) || ctx.keyPressed(SceneKeys.E);
        for (int i = 0; i < 6; i++) shortcut |= ctx.keyPressed(SceneKeys.DIGIT_1 + i);
        if (accept(ctx) && !shortcut && !ctx.mouse().leftPressed()) {
            switch (focus) {
                case "SHOP" -> {
                    boolean ok = battle.build(site, kind);
                    outcome(ctx, ok, "BUILT " + Catalog.defense(kind).name(),
                            battle.tower(site) != null ? "SITE OCCUPIED. SELECT AN EMPTY SITE." : "NOT ENOUGH SCRAP.");
                    if (ok) focus = "GRID";
                }
                case "ACTIONS" -> perform(ctx, action);
                default -> {
                    focus = battle.tower(site) == null ? "SHOP" : "ACTIONS";
                    action = 0;
                }
            }
        }
        if (!screen.equals("BATTLE")) return;
        String before = battle.phase();
        int shots = battle.shots(), hp = battle.doorHp(), kills = battle.kills();
        battle.update();
        if (battle.shots() > shots && ticks % 9 == 0) ctx.audio().playSfx(0x6E); // boss hit
        if (battle.kills() > kills && ticks % 7 == 0) ctx.audio().playSfx(0x33); // recovered scrap
        if (battle.doorHp() < hp) ctx.audio().playSfx(0x9E); // metal clank
        if (!before.equals(battle.phase())) {
            if (battle.phase().equals(Battlefield.PREP)) {
                if (!debugRun) {
                    records = records.completed(battle.score(), battle.wave(), false);
                    ctx.storage().write("records.txt", records.encode());
                }
                outcome(ctx, true, "WAVE CLEAR! REPAIR, UPGRADE, THEN SEND THE NEXT.", "");
            } else if (battle.finished()) {
                screen = "RESULT";
                record(ctx);
                ctx.audio().playMusic(battle.phase().equals(Battlefield.WON) ? 0x29 : 0x27);
            }
        }
    }

    private void navigation(SceneContext ctx) {
        switch (focus) {
            case "SHOP" -> {
                if (left(ctx)) kind = (kind + 5) % 6;
                if (right(ctx)) kind = (kind + 1) % 6;
                if (up(ctx) || down(ctx)) focus = "GRID";
            }
            case "ACTIONS" -> {
                if (left(ctx)) action = (action + 5) % 6;
                if (right(ctx)) action = (action + 1) % 6;
                if (up(ctx) || down(ctx)) focus = "GRID";
            }
            default -> {
                if (left(ctx)) site = site / 5 * 5 + (site % 5 + 4) % 5;
                if (right(ctx)) site = site / 5 * 5 + (site % 5 + 1) % 5;
                if (up(ctx)) { if (site < 5) site += 5; else focus = "SHOP"; }
                if (down(ctx)) { if (site >= 5) site -= 5; else { focus = "ACTIONS"; action = 0; } }
            }
        }
    }

    private void mouseInput(SceneContext ctx) {
        SceneMouse mouse = ctx.mouse();
        if (!mouse.leftPressed()) return;
        for (int i = 0; i < 6; i++) {
            if (mouse.over(3 + i * 51, 186, 49, 27)) { kind = i; focus = "SHOP"; return; }
        }
        for (int i = 0; i < Battlefield.SITES; i++) {
            if (mouse.over(Battlefield.siteX(i) - 18, Battlefield.siteY(i) - 35, 36, 39)) {
                site = i;
                if (focus.equals("SHOP") && battle.tower(site) == null) {
                    boolean ok = battle.build(site, kind);
                    outcome(ctx, ok, "BUILT " + Catalog.defense(kind).name(), "NOT ENOUGH SCRAP.");
                    if (!ok) return;
                } else if (battle.tower(site) == null) {
                    focus = "SHOP";
                    return;
                }
                focus = "GRID";
                return;
            }
        }
        if (mouse.over(312, 186, 85, 27)) { perform(ctx, 4); return; }
        if (mouse.y() >= 215 && mouse.inside()) {
            if (mouse.over(3, 215, 44, 9)) perform(ctx, 5);
            else if (mouse.over(54, 215, 74, 9)) perform(ctx, 0);
            else if (mouse.over(133, 215, 50, 9)) perform(ctx, 1);
            else if (mouse.over(188, 215, 77, 9)) perform(ctx, 2);
            else if (mouse.over(270, 215, 127, 9)) perform(ctx, 3);
        }
    }

    private void perform(SceneContext ctx, int action) {
        switch (action) {
            case 0 -> {
                int cost = battle.upgradeCost(site);
                outcome(ctx, battle.upgrade(site), "UPGRADED FOR $" + cost,
                        battle.tower(site) == null ? "SELECT A TOWER FIRST." : cost == 0 ? "ALREADY LEVEL 3." : "UPGRADE COSTS $" + cost);
            }
            case 1 -> {
                int refund = battle.sellValue(site);
                outcome(ctx, battle.sell(site), "SOLD FOR $" + refund, "SELECT A TOWER FIRST.");
            }
            case 2 -> outcome(ctx, battle.repair(), "DOOR REPAIRED: +40 HP.", "REPAIR: BETWEEN WAVES, DAMAGED DOOR, $25.");
            case 3 -> outcome(ctx, battle.bomb(), "EMERGENCY BOMB!", "BOMB: DURING A WAVE, WHEN RECHARGED.");
            case 4 -> {
                if (battle.startWave()) {
                    focus = "GRID";
                    outcome(ctx, true, battle.preview().name(), "");
                    ctx.audio().playSfx(0x86); // Launch Base alarm
                    if (battle.wave() == 15) ctx.audio().playMusic(0x19); // boss theme for general strike
                }
            }
            case 5 -> help("BATTLE");
            default -> { }
        }
    }

    private void outcome(SceneContext ctx, boolean ok, String success, String failure) {
        message = ok ? success : failure;
        messageTicks = ok ? 160 : 220;
        ctx.audio().playSfx(ok ? 0x5B : 0xB2); // switch / error
    }

    private void record(SceneContext ctx) {
        if (recorded || battle == null || debugRun) return;
        records = records.completed(battle.score(), battle.wave(), battle.phase().equals(Battlefield.WON));
        ctx.storage().write("records.txt", records.encode());
        recorded = true;
    }

    @Override public void draw(SceneContext ctx, SceneCanvas canvas) {
        switch (screen) {
            case "TITLE" -> view.title(canvas, records, cursor, ticks);
            case "HELP" -> view.help(canvas, ticks, helpPage);
            case "BATTLE" -> {
                view.battlefield(canvas, battle, ticks, site, kind, focus, action, ctx.mouse(), message);
                if (paused) view.pause(canvas, cursor);
            }
            case "RESULT" -> view.result(canvas, battle, ticks);
            default -> { }
        }
    }

    @Override public void exit(SceneContext ctx) { record(ctx); }

    /** Debug/capture jumps replay normal rules; demonstration runs never update saved records. */
    @Override public boolean debugJump(String command) {
        try {
            if (command.equals("title")) { screen = "TITLE"; cursor = 0; paused = false; return true; }
            if (command.equals("help")) { help(screen); return true; }
            if (command.equals("win")) battle = CampaignDemo.toWave(16);
            else if (command.equals("lose")) {
                battle = new Battlefield(); battle.startWave();
                for (int i = 0; i < 20000 && !battle.finished(); i++) battle.update();
            } else if (command.startsWith("wave:")) {
                int number = Integer.parseInt(command.substring(5));
                if (number < 1 || number > Catalog.WAVES) return false;
                battle = CampaignDemo.toWave(number);
            } else return false;
            screen = "BATTLE";
            focus = "GRID";
            site = kind = action = cursor = 0;
            paused = recorded = false;
            debugRun = true;
            message = ""; messageTicks = 0;
            return true;
        } catch (NumberFormatException e) { return false; }
    }

    private boolean accept(SceneContext c) {
        return c.buttonPressed(SceneButtons.A | SceneButtons.C) || c.keyPressed(SceneKeys.ENTER) || c.keyPressed(SceneKeys.SPACE);
    }
    private boolean back(SceneContext c) { return c.buttonPressed(SceneButtons.B) || c.keyPressed(SceneKeys.BACKSPACE); }
    private boolean left(SceneContext c) { return c.buttonRepeated(SceneButtons.LEFT); }
    private boolean right(SceneContext c) { return c.buttonRepeated(SceneButtons.RIGHT); }
    private boolean up(SceneContext c) { return c.buttonRepeated(SceneButtons.UP); }
    private boolean down(SceneContext c) { return c.buttonRepeated(SceneButtons.DOWN); }
}
