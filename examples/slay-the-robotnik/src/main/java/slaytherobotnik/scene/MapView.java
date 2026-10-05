package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneSprite;
import java.util.List;
import slaytherobotnik.core.RoomType;
import slaytherobotnik.map.ActMap;
import slaytherobotnik.map.MapNode;
import slaytherobotnik.run.Run;
import slaytherobotnik.ui.Colors;
import slaytherobotnik.ui.Ease;
import slaytherobotnik.ui.Gfx;
import slaytherobotnik.ui.Hotspots;
import slaytherobotnik.ui.SmallFont;

/**
 * The act map, left to right through the zone, with the boss at the far right. Floors are
 * columns and the map's lanes are rows; behind it scrolls a zoomed-out render of the act's
 * level from the ROM (or the zone's drawn backdrop when the game cannot render one). On the
 * map room it lets the player pick the next node; elsewhere ({@code peek}) it is a read-only
 * overlay.
 */
final class MapView implements RunScreen.RoomView {
    /** Horizontal distance between floors and vertical distance between lanes. */
    private static final int FLOOR_STEP = 40;
    private static final int LANE_STEP = 24;
    private static final int LEFT_PAD = 30;
    private static final int LEGEND_H = 16;
    private final boolean peek;
    private final Hotspots spots = new Hotspots();
    private float scroll = Float.NaN;
    private float targetScroll;

    MapView(boolean peek) {
        this.peek = peek;
    }

    /** Screen y of lane 0; the seven lanes are centred between the HUD and the legend strip. */
    private int laneTop(Shell shell) {
        int area = shell.height() - RunScreen.HUD_HEIGHT - LEGEND_H;
        return RunScreen.HUD_HEIGHT + (area - (ActMap.WIDTH - 1) * LANE_STEP) / 2;
    }

    /** World x of a floor; floor 15 is the boss. */
    private static int floorX(int floor) {
        return LEFT_PAD + floor * FLOOR_STEP + (floor >= Run.BOSS_FLOOR ? 24 : 0);
    }

    private int worldWidth() {
        return floorX(Run.BOSS_FLOOR) + 60;
    }

    private int maxScroll(Shell shell) {
        return Math.max(0, worldWidth() - shell.width());
    }

    private int screenX(int worldX) {
        return worldX - Math.round(scroll);
    }

    /** Node jitter is generated for a vertical map, so its axes swap here. */
    private int nodeScreenX(MapNode n) {
        return screenX(floorX(n.y()) + n.offsetY());
    }

    private int nodeScreenY(Shell shell, MapNode n) {
        return laneTop(shell) + n.x() * LANE_STEP + n.offsetX() / 2;
    }

    private int bossX() {
        return screenX(floorX(Run.BOSS_FLOOR));
    }

    private int bossY(Shell shell) {
        return laneTop(shell) + (ActMap.WIDTH - 1) * LANE_STEP / 2;
    }

    private void layout(Shell shell) {
        spots.clear();
        if (peek) {
            return;
        }
        Run run = shell.run;
        for (MapNode n : run.reachableNodes()) {
            spots.add("node" + n.x() + "_" + n.y(), nodeScreenX(n) - 8, nodeScreenY(shell, n) - 8, 17, 17);
        }
        if (run.bossReachable()) {
            spots.add("boss", bossX() - 26, bossY(shell) - 20, 52, 40);
        }
    }

    @Override
    public void update(Shell shell, RunScreen screen) {
        Run run = shell.run;
        int focusFloor = Math.max(0, run.state().actFloor());
        targetScroll = Math.max(0, Math.min(maxScroll(shell), floorX(focusFloor) - 90));
        if (Float.isNaN(scroll)) {
            scroll = peek ? targetScroll : Math.max(0, targetScroll - 40);
        }
        if (shell.in.mouse.wheel() != 0) {
            targetScroll = Math.max(0, Math.min(maxScroll(shell), scroll - shell.in.mouse.wheel() * 30));
        }
        scroll = Ease.approach(scroll, targetScroll, 0.15f);
        if (peek) {
            return;
        }
        layout(shell);
        String before = spots.focused();
        String picked = spots.update(shell.in);
        if (before != null && !before.equals(spots.focused())) {
            shell.sfx(Sounds.SFX_CURSOR);
        }
        if (picked == null) {
            return;
        }
        shell.sfx(Sounds.SFX_JUMP);
        if (picked.equals("boss")) {
            run.travelToBoss();
            return;
        }
        String[] xy = picked.substring(4).split("_");
        run.travelTo(run.map().node(Integer.parseInt(xy[0]), Integer.parseInt(xy[1])));
    }

    @Override
    public void draw(Shell shell, RunScreen screen, SceneCanvas c) {
        Run run = shell.run;
        ActMap map = run.map();
        int w = shell.width();
        int h = shell.height();
        if (Float.isNaN(scroll)) {
            scroll = 0;
        }
        if (peek) {
            c.fill(0, 0, w, h, 0xE0000818);
        } else {
            drawLevel(shell, c, w, h);
        }
        layout(shell);
        c.clip(0, RunScreen.HUD_HEIGHT, w, h - RunScreen.HUD_HEIGHT - LEGEND_H);
        // Paths.
        for (MapNode n : map.usedNodes()) {
            for (MapNode child : n.children()) {
                boolean taken = visited(run, n) && visited(run, child);
                dotted(c, nodeScreenX(n), nodeScreenY(shell, n), nodeScreenX(child), nodeScreenY(shell, child),
                        taken ? Colors.GOLD : 0xC0E0E8FF);
            }
            if (n.connectsToBoss()) {
                dotted(c, nodeScreenX(n), nodeScreenY(shell, n), bossX() - 24, bossY(shell), 0xA0E0E8FF);
            }
        }
        // Nodes.
        List<MapNode> reachable = peek ? List.of() : run.reachableNodes();
        for (MapNode n : map.usedNodes()) {
            int x = nodeScreenX(n);
            int y = nodeScreenY(shell, n);
            if (x < -20 || x > w + 20) {
                continue;
            }
            boolean canGo = reachable.contains(n);
            boolean here = n.x() == run.state().nodeX() && n.y() == run.state().actFloor();
            boolean past = n.y() < run.state().actFloor() || here;
            float pulse = canGo ? 1f + 0.12f * (float) Math.sin(shell.ticks * 0.2) : 1f;
            SceneImage icon = shell.art.icon(nodeIcon(n.room()));
            int tint = past && !here ? 0xFF8090A0 : (canGo || here ? Colors.WHITE : 0xFFD8E0F0);
            c.fill(x - 8, y - 8, 17, 17, canGo
                    ? Colors.alpha(Colors.FOCUS, 0.3f + 0.15f * (float) Math.sin(shell.ticks * 0.2))
                    : 0x60000010);
            c.draw(icon, x - icon.width() * pulse / 2f, y - icon.height() * pulse / 2f,
                    SceneDraw.plain().withScale(pulse).withTint(tint));
            if (visited(run, n) && !here) {
                c.fill(x - 6, y + 7, 13, 2, Colors.GOLD);
            }
            if (here) {
                drawHero(shell, c, x, y - 10);
            }
            String id = "node" + n.x() + "_" + n.y();
            if (spots.isFocused(id)) {
                Gfx.focusFrame(c, x - 8, y - 8, 17, 17, shell.ticks);
                screen.tooltip(RoomType.label(n.room()).toUpperCase(), roomHint(n.room()),
                        Math.min(x + 14, w - 150), Math.max(RunScreen.HUD_HEIGHT + 2, y - 30));
            }
        }
        drawBoss(shell, screen, c);
        c.unclip();
        drawLegend(shell, c);
        if (!peek && run.state().actFloor() < 0) {
            String hint = "CHOOSE WHERE TO LAND";
            shell.font.drawOutlined(c, hint, w - 8 - shell.font.width(hint), RunScreen.HUD_HEIGHT + 4, Colors.GOLD, 1);
        }
        if (peek) {
            String hint = "M / B TO CLOSE";
            shell.font.drawShadowed(c, hint, w - 8 - shell.font.width(hint), RunScreen.HUD_HEIGHT + 4, Colors.TEXT_DIM);
        }
    }

    /**
     * The act's level, zoomed out to fit the map's height, scrolling with the map so the path
     * runs from the level's start to its end. Falls back to the zone's drawn backdrop.
     */
    private void drawLevel(Shell shell, SceneCanvas c, int w, int h) {
        int areaTop = RunScreen.HUD_HEIGHT;
        int areaH = h - areaTop - LEGEND_H;
        SceneImage level = shell.art.levelOverview(shell.run.act().zone(), shell.run.act().zoneAct(), areaH);
        if (level == null) {
            Backdrops.zone(shell, c, shell.run.act().zone(), shell.run.act().zoneAct(), (long) scroll * 2 + shell.ticks / 4);
            c.fill(0, 0, w, h, 0x80000818);
            return;
        }
        c.fill(0, 0, w, h, 0xFF000010);
        // Scale the level to the map's height; pan across it as the map scrolls.
        float scale = areaH / (float) level.height();
        float drawnW = level.width() * scale;
        float pan = maxScroll(shell) == 0 ? 0 : scroll / maxScroll(shell);
        float x = drawnW <= w ? (w - drawnW) / 2f : -(drawnW - w) * pan;
        c.draw(level, x, areaTop, SceneDraw.plain().withScale(scale));
        c.fill(0, areaTop, w, areaH, 0x58000818);
    }

    private void drawHero(Shell shell, SceneCanvas c, int x, int y) {
        SceneSprite pose = Poses.still(shell.art.character(shell.run.state().character().id()), Poses.WAIT);
        if (pose != null) {
            c.draw(pose, x, y + 8, SceneDraw.plain().withScale(0.75f));
        } else {
            c.fill(x - 3, y - 3, 7, 7, Colors.GOLD);
        }
    }

    private void drawBoss(Shell shell, RunScreen screen, SceneCanvas c) {
        Run run = shell.run;
        int bx = bossX();
        int by = bossY(shell);
        if (bx < -40 || bx > shell.width() + 40) {
            return;
        }
        boolean reachable = !peek && run.bossReachable();
        Gfx.panel(c, bx - 26, by - 20, 52, 40, reachable ? 0xF0481010 : 0xE0200810,
                reachable ? Colors.FOCUS : 0xFF904848);
        BossPortraits.draw(shell, c, run.state().boss(), bx, by + 8);
        String name = run.state().boss() == null ? "BOSS"
                : shell.catalog.encounter(run.state().boss()).name().toUpperCase();
        shell.font.drawOutlined(c, name, bx - shell.font.width(name) / 2, by + 23, Colors.TEXT_BAD, 1);
        if (spots.isFocused("boss")) {
            Gfx.focusFrame(c, bx - 26, by - 20, 52, 40, shell.ticks);
        }
    }

    /** A one-line legend along the bottom of the screen. */
    private void drawLegend(Shell shell, SceneCanvas c) {
        int y = shell.height() - LEGEND_H;
        c.fill(0, y, shell.width(), LEGEND_H, 0xE0081028);
        c.fill(0, y, shell.width(), 1, Colors.PANEL_EDGE_DARK);
        String[][] rows = {
                {RoomType.EVENT, "UNKNOWN"}, {RoomType.MONSTER, "BADNIKS"}, {RoomType.ELITE, "ELITE"},
                {RoomType.REST, "STARPOST"}, {RoomType.SHOP, "SHOP"}, {RoomType.TREASURE, "TREASURE"}};
        int x = 6;
        for (String[] row : rows) {
            SceneImage icon = shell.art.icon(nodeIcon(row[0]));
            c.draw(icon, x, y + 2, SceneDraw.plain().withScale(0.75f));
            shell.font.drawShadowed(c, row[1], x + 14, y + 5, Colors.TEXT);
            x += 18 + shell.font.width(row[1]) + 10;
        }
    }

    static String nodeIcon(String room) {
        return switch (room == null ? "" : room) {
            case RoomType.MONSTER -> "node_monster";
            case RoomType.ELITE -> "node_elite";
            case RoomType.EVENT -> "node_event";
            case RoomType.TREASURE -> "node_treasure";
            case RoomType.SHOP -> "node_shop";
            case RoomType.REST -> "node_rest";
            default -> "node_boss";
        };
    }

    private static String roomHint(String room) {
        return switch (room) {
            case RoomType.MONSTER -> "Fight a group of badniks.";
            case RoomType.ELITE -> "A powerful foe. Defeat it for a relic.";
            case RoomType.EVENT -> "Something unexpected awaits.";
            case RoomType.TREASURE -> "An item capsule with a relic inside.";
            case RoomType.SHOP -> "The Egg Robo sells cards, relics and potions for rings.";
            case RoomType.REST -> "Rest to heal, or tune up a card.";
            default -> "";
        };
    }

    private static boolean visited(Run run, MapNode n) {
        // The current path is not stored; the player's column history is implied by the map
        // only for the current node, so mark nodes on rows already passed that lead here.
        int floor = run.state().actFloor();
        if (n.y() > floor || floor < 0) {
            return false;
        }
        if (n.y() == floor) {
            return n.x() == run.state().nodeX();
        }
        return run.state().pathColumns().size() > n.y() && run.state().pathColumns().get(n.y()) == n.x();
    }

    private static void dotted(SceneCanvas c, int x0, int y0, int x1, int y1, int color) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)) / 4;
        for (int i = 1; i < steps; i++) {
            int x = x0 + (x1 - x0) * i / steps;
            int y = y0 + (y1 - y0) * i / steps;
            c.fill(x, y, 2, 2, Colors.alpha(Colors.BLACK, 0.5f));
            c.fill(x - 1, y - 1, 2, 2, color);
        }
    }
}
