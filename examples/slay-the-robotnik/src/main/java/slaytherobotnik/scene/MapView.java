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
 * The act map, bottom to top, with the boss above the last row. On the map room it lets the
 * player pick the next node; elsewhere ({@code peek}) it is a read-only overlay.
 */
final class MapView implements RunScreen.RoomView {
    private static final int COL = 36;
    private static final int ROW = 26;
    private static final int BOTTOM_PAD = 30;
    private final boolean peek;
    private final Hotspots spots = new Hotspots();
    private float scroll = Float.NaN;
    private float targetScroll;

    MapView(boolean peek) {
        this.peek = peek;
    }

    private int mapLeft(Shell shell) {
        return (shell.width() - 6 * COL) / 2 - 40;
    }

    /** World y of a row; row 15 is the boss. */
    private static int rowY(int row) {
        return BOTTOM_PAD + row * ROW;
    }

    private int worldHeight() {
        return rowY(Run.BOSS_FLOOR) + 50;
    }

    private int screenX(Shell shell, MapNode n) {
        return mapLeft(shell) + n.x() * COL + n.offsetX();
    }

    private int screenY(Shell shell, int worldY) {
        return shell.height() - worldY + (int) scroll;
    }

    private int nodeScreenY(Shell shell, MapNode n) {
        return screenY(shell, rowY(n.y()) + n.offsetY());
    }

    private void layout(Shell shell) {
        spots.clear();
        if (peek) {
            return;
        }
        Run run = shell.run;
        for (MapNode n : run.reachableNodes()) {
            spots.add("node" + n.x() + "_" + n.y(), screenX(shell, n) - 8, nodeScreenY(shell, n) - 8, 17, 17);
        }
        if (run.bossReachable()) {
            int bx = mapLeft(shell) + 3 * COL;
            spots.add("boss", bx - 24, screenY(shell, rowY(Run.BOSS_FLOOR)) - 20, 48, 40);
        }
    }

    @Override
    public void update(Shell shell, RunScreen screen) {
        Run run = shell.run;
        int maxScroll = Math.max(0, worldHeight() - (shell.height() - RunScreen.HUD_HEIGHT));
        int focusRow = Math.max(0, run.state().actFloor());
        targetScroll = Math.max(0, Math.min(maxScroll, rowY(focusRow) - 60));
        if (Float.isNaN(scroll)) {
            scroll = peek ? targetScroll : Math.max(0, targetScroll - 40);
        }
        if (shell.in.mouse.wheel() != 0) {
            targetScroll = Math.max(0, Math.min(maxScroll, scroll + shell.in.mouse.wheel() * 24));
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
        if (peek) {
            c.fill(0, 0, w, h, 0xE0000818);
        } else {
            Backdrops.zone(shell, c, run.act().zone(), (long) scroll * 2 + shell.ticks / 4);
            c.fill(0, 0, w, h, 0x90000818);
        }
        if (Float.isNaN(scroll)) {
            scroll = 0;
        }
        layout(shell);
        c.clip(0, RunScreen.HUD_HEIGHT, w, h - RunScreen.HUD_HEIGHT);
        // Paths.
        for (MapNode n : map.usedNodes()) {
            for (MapNode child : n.children()) {
                boolean taken = visited(run, n) && visited(run, child);
                dotted(c, screenX(shell, n), nodeScreenY(shell, n), screenX(shell, child), nodeScreenY(shell, child),
                        taken ? Colors.GOLD : 0xA0B6C8FF);
            }
            if (n.connectsToBoss()) {
                int bx = mapLeft(shell) + 3 * COL;
                dotted(c, screenX(shell, n), nodeScreenY(shell, n), bx, screenY(shell, rowY(Run.BOSS_FLOOR)) + 16,
                        0x80B6C8FF);
            }
        }
        // Nodes.
        List<MapNode> reachable = peek ? List.of() : run.reachableNodes();
        for (MapNode n : map.usedNodes()) {
            int x = screenX(shell, n);
            int y = nodeScreenY(shell, n);
            if (y < -20 || y > h + 20) {
                continue;
            }
            boolean canGo = reachable.contains(n);
            boolean here = n.x() == run.state().nodeX() && n.y() == run.state().actFloor();
            boolean past = n.y() < run.state().actFloor() || (here);
            float pulse = canGo ? 1f + 0.12f * (float) Math.sin(shell.ticks * 0.2) : 1f;
            SceneImage icon = shell.art.icon(nodeIcon(n.room()));
            int tint = past && !here ? 0xFF8090A0 : (canGo || here ? Colors.WHITE : 0xFFC8D0E0);
            if (canGo) {
                c.fill(x - 9, y - 9, 19, 19, Colors.alpha(Colors.FOCUS, 0.25f + 0.15f * (float) Math.sin(shell.ticks * 0.2)));
            }
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
                screen.tooltip(RoomType.label(n.room()).toUpperCase(), roomHint(n.room()), x + 14, y - 10);
            }
        }
        drawBoss(shell, screen, c);
        c.unclip();
        drawLegend(shell, c);
        if (!peek && run.state().actFloor() < 0) {
            String hint = "CHOOSE WHERE TO LAND";
            shell.font.drawOutlined(c, hint, mapLeft(shell) + 3 * COL - shell.font.width(hint) / 2,
                    h - 12, Colors.GOLD, 1);
        }
        if (peek) {
            String hint = "M / B TO CLOSE";
            shell.font.drawShadowed(c, hint, w - 8 - shell.font.width(hint), h - 10, Colors.TEXT_DIM);
        }
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
        int bx = mapLeft(shell) + 3 * COL;
        int by = screenY(shell, rowY(Run.BOSS_FLOOR));
        if (by < -40) {
            return;
        }
        boolean reachable = !peek && run.bossReachable();
        Gfx.panel(c, bx - 26, by - 18, 52, 38, reachable ? 0xF0481010 : 0xE0200810,
                reachable ? Colors.FOCUS : 0xFF904848);
        BossPortraits.draw(shell, c, run.state().boss(), bx, by + 10);
        String name = run.state().boss() == null ? "BOSS"
                : shell.catalog.encounter(run.state().boss()).name().toUpperCase();
        shell.font.drawOutlined(c, name, bx - shell.font.width(name) / 2, by + 22, Colors.TEXT_BAD, 1);
        if (spots.isFocused("boss")) {
            Gfx.focusFrame(c, bx - 26, by - 18, 52, 38, shell.ticks);
        }
    }

    private void drawLegend(Shell shell, SceneCanvas c) {
        int x = shell.width() - 86;
        int y = RunScreen.HUD_HEIGHT + 8;
        Gfx.panel(c, x, y, 80, 112, 0xD0081028, Colors.PANEL_EDGE_DARK);
        String[][] rows = {
                {RoomType.EVENT, "UNKNOWN"}, {RoomType.MONSTER, "BADNIKS"}, {RoomType.ELITE, "ELITE"},
                {RoomType.REST, "STARPOST"}, {RoomType.SHOP, "SHOP"}, {RoomType.TREASURE, "TREASURE"}};
        shell.font.drawShadowed(c, "LEGEND", x + 6, y + 5, Colors.GOLD);
        int ly = y + 16;
        for (String[] row : rows) {
            SceneImage icon = shell.art.icon(nodeIcon(row[0]));
            c.draw(icon, x + 6, ly, SceneDraw.plain().withScale(0.8f));
            shell.font.drawShadowed(c, row[1], x + 22, ly + 4, Colors.TEXT);
            ly += 15;
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
