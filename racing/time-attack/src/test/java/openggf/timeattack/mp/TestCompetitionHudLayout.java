package openggf.timeattack.mp;

import com.openggf.mods.run.TrackKind;
import com.openggf.mods.ui.PixelCanvas;
import openggf.timeattack.CompetitionHudState;
import openggf.timeattack.CompetitionHudState.MassStartPhase;
import openggf.timeattack.CompetitionHudState.SwapNotice;
import openggf.timeattack.TimeAttackHud;
import openggf.timeattack.TimeAttackHudState;
import openggf.timeattack.ui.CompetitionSamples;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The Competition in-race HUD: right column only, sized to content, clear of the native arena HUD. */
class TestCompetitionHudLayout {
    /**
     * The native arena HUD pieces on the 320 frame (CompetitionHudObjectInstance: centre and half
     * size): TIMER $C8,$90 +-$40,$10; LAP $190,$98 +-8,$C; ITEM_ICON $120,$E0 +-$10,$10.
     */
    private static final int[][] NATIVE_ARENA_HUD = {
            {0xC8 - 0x80 - 0x40, 0x90 - 0x80 - 0x10, 0x80, 0x20},
            {0x190 - 0x80 - 8, 0x98 - 0x80 - 0x0C, 16, 0x18},
            {0x120 - 0x80 - 0x10, 0xE0 - 0x80 - 0x10, 0x20, 0x20}};
    /** The lives row (y 200) and the progress strip under it, which no plate may reach. */
    private static final int LIVES_ROW = 200;

    @Test
    void arenaRowsShowEstimateLapAndBestWithoutRepeatingTheNativeTotal() {
        var panel = MultiplayerHudRenderer.layout(320, 224,
                CompetitionSamples.room("RUNNING", CompetitionSamples.arena(false, SwapNotice.none(), false)), 100, false);
        assertEquals(List.of("P4/37", "LAP 3/5 0'21\"50", "BEST 0'19\"83"),
                panel.lines().stream().map(HudTextLayout.Line::plain).toList());
        assertEquals(HudTextLayout.MUTED, panel.lines().getFirst().color(), "the position is a dim estimate");
        assertFalse(panel.lines().stream().anyMatch(line -> line.plain().startsWith("ROOM")),
                "a Competition round has no Time Attack window row");
    }

    @Test
    void columnFitsAt320And400AndClearsTheNativeArenaHudAndLives() {
        for (int width : new int[] {320, 400}) {
            int origin = (width - 320) / 2;
            var state = CompetitionSamples.room("RUNNING",
                    CompetitionSamples.arena(true, SwapNotice.applied("Knuckles Fan"), true));
            var panel = MultiplayerHudRenderer.layout(width, 224, state, 100, false);
            var plates = MultiplayerHudRenderer.plates(width, 224, state, panel, false);
            assertEquals(2, plates.size(), "cutoff and swap plates");
            List<HudTextLayout.Panel> all = new ArrayList<>(plates);
            all.add(panel);
            for (var plate : all) {
                assertTrue(plate.width() <= HudTextLayout.PANEL_WIDTH, "the right column is at most 140 px");
                assertEquals(width - 4, plate.x() + plate.width(), "right-aligned in the column");
                assertTrue(plate.lines().stream().allMatch(line -> line.width() <= plate.width() - 12),
                        "every row fits its plate: " + plate.lines());
                assertTrue(plate.y() + plate.height() < LIVES_ROW, "clear of the lives row");
                for (int[] hud : NATIVE_ARENA_HUD) {
                    assertFalse(overlaps(plate, hud[0] + origin, hud[1], hud[2], hud[3]),
                            "plate " + plate.lines().getFirst().plain() + " covers native HUD at " + width);
                }
            }
            for (int i = 1; i < all.size(); i++) {
                assertTrue(all.get(i - 1).y() + all.get(i - 1).height() <= all.get(i).y()
                        || all.get(i).y() + all.get(i).height() <= all.get(i - 1).y(), "plates stack without overlap");
            }
        }
    }

    @Test
    void cutoffIsAnAmberPlateAndTheSwapNamesThePartner() {
        var state = CompetitionSamples.room("RUNNING",
                CompetitionSamples.arena(true, SwapNotice.applied("Knuckles Fan"), false));
        var panel = MultiplayerHudRenderer.layout(320, 224, state, 100, false);
        var plates = MultiplayerHudRenderer.plates(320, 224, state, panel, false);
        assertEquals("CUT 0:42", plates.get(0).lines().getFirst().plain(), "41.2 s left rounds up");
        assertEquals(HudTextLayout.AMBER, plates.get(0).accent());
        assertEquals(HudTextLayout.AMBER, plates.get(0).lines().getFirst().color());
        assertEquals("SWAP " + HudTextLayout.SWAP_MARK + " Knuckles Fan", plates.get(1).lines().getFirst().plain());
        assertEquals(HudTextLayout.ACCENT, plates.get(1).accent());
        var none = CompetitionSamples.room("RUNNING", CompetitionSamples.arena(false, SwapNotice.fallback(), false));
        assertEquals("SWAP: NONE", MultiplayerHudRenderer.plates(320, 224, none,
                MultiplayerHudRenderer.layout(320, 224, none, 100, false), false).getFirst().lines().getFirst().plain());
        var quiet = CompetitionSamples.room("RUNNING", CompetitionSamples.arena(false, SwapNotice.none(), false));
        assertTrue(MultiplayerHudRenderer.plates(320, 224, quiet,
                MultiplayerHudRenderer.layout(320, 224, quiet, 100, false), false).isEmpty());
    }

    @Test
    void unverifiedRoomsTagTheStandingsPlateInAmber() {
        var panel = MultiplayerHudRenderer.layout(320, 224,
                CompetitionSamples.room("RUNNING", CompetitionSamples.arena(false, SwapNotice.none(), true)), 100, false);
        HudTextLayout.Line first = panel.lines().getFirst();
        assertEquals("UNVERIFIED", first.suffix());
        assertEquals(HudTextLayout.AMBER, first.suffixColor());
        assertEquals(HudTextLayout.MUTED, first.color());
    }

    @Test
    void actsShowOnlyTheEstimateAndKeepTheTopRightCorner() {
        var panel = MultiplayerHudRenderer.layout(320, 224,
                CompetitionSamples.room("RUNNING", CompetitionSamples.act(false)), 100, false);
        assertEquals(List.of("P2/6"), panel.lines().stream().map(HudTextLayout.Line::plain).toList());
        assertEquals(HudTextLayout.ROOM_TOP, panel.y());
        var arena = MultiplayerHudRenderer.layout(320, 224,
                CompetitionSamples.room("RUNNING", CompetitionSamples.arena(false, SwapNotice.none(), false)), 100, false);
        assertEquals(MultiplayerHudRenderer.ARENA_TOP, arena.y(), "an arena column starts under the native lap digit");
    }

    @Test
    void massStartShowsTheStartPlateAndLoadingUntilHeld() {
        var loading = MultiplayerHudRenderer.layout(320, 224, CompetitionSamples.room("COUNTDOWN",
                CompetitionSamples.start(TrackKind.ACT, MassStartPhase.LOADING)), -1, false);
        assertEquals(List.of("START 0:08", "LOADING"), loading.lines().stream().map(HudTextLayout.Line::plain).toList());
        assertEquals(HudTextLayout.GOLD, loading.accent());
        var held = MultiplayerHudRenderer.layout(320, 224, CompetitionSamples.room("COUNTDOWN",
                CompetitionSamples.start(TrackKind.ARENA, MassStartPhase.HELD)), -1, false);
        assertEquals(List.of("START 0:08"), held.lines().stream().map(HudTextLayout.Line::plain).toList());
    }

    @Test
    void arenasNeverDrawTheRoomCountdownDigitButActsDo() {
        for (TrackKind kind : TrackKind.values()) {
            CompetitionHudState start = CompetitionSamples.start(kind, MassStartPhase.COUNTDOWN);
            var base = CompetitionSamples.room("COUNTDOWN", start);
            var counting = new MultiplayerHudState(true, "COUNTDOWN", 0, 2_500, List.of(), List.of(), false, null, 37,
                    base.farPlayers(), "OPEN", List.of(), List.of(), 0, null, List.of(), -1, base.nameColours(), 0, 0,
                    java.util.Map.of(), start);
            List<int[]> fills = new ArrayList<>();
            MultiplayerHudRenderer.render(overlay(320, fills), counting, 0, -1, false);
            boolean digit = fills.stream().anyMatch(fill -> fill[4] == HudTextLayout.GOLD && fill[1] >= 88 && fill[1] < 102
                    && fill[0] > 140 && fill[0] < 180);
            assertEquals(kind == TrackKind.ACT, digit, kind + ": the native neon is the arena's visible count");
        }
    }

    @Test
    void finishedRacersSeeTheLiveFinishOrderAboveTheStrip() {
        CompetitionHudState results = CompetitionSamples.results(true);
        var panel = MultiplayerHudRenderer.layout(320, 224, CompetitionSamples.room("RUNNING", results), 100, true);
        List<String> rows = panel.lines().stream().map(HudTextLayout.Line::plain).toList();
        assertEquals("P1/5 UNVERIFIED", rows.getFirst());
        assertTrue(panel.width() <= HudTextLayout.PANEL_WIDTH, "the finish order keeps the right column's budget");
        assertEquals("1. Tails 1'41\"19", rows.get(1), "the ROM total is the sum of the truncated laps");
        assertTrue(rows.get(2).startsWith("2. Knuckles") && rows.get(2).endsWith("1'46\"03"), rows.get(2));
        assertTrue(CompetitionSamples.placements(CompetitionHudState.VerifyState.UNVERIFIED).stream()
                .anyMatch(CompetitionHudState.PlacementRow::taintedSwap));
        assertTrue(panel.y() + panel.height() <= HudTextLayout.finishStripTop(224));
    }

    @Test
    void roundEndListsFinishersThenDnfThenDns() {
        var placements = MultiplayerHudRenderer.displayOrder(CompetitionSamples.placements(
                CompetitionHudState.VerifyState.UNVERIFIED));
        assertEquals(List.of("Tails", "Knuckles Fan", "Amy", "Quit Early", "Spectator Seven"),
                placements.stream().map(CompetitionHudState.PlacementRow::name).toList());
    }

    @Test
    void lateJoinersWaitForTheNextRound() {
        var panel = MultiplayerHudRenderer.layout(320, 224,
                CompetitionSamples.room("RUNNING", CompetitionSamples.lateJoin()), -1, false);
        assertEquals("NEXT ROUND", panel.lines().getFirst().plain());
    }

    @Test
    void arenaClockStripLeavesTheBandUnderTheNativeRowsToTheArenaHud() {
        TimeAttackHudState running = new TimeAttackHudState(true, 3000, 2900, -40, false, false, "",
                CompetitionSamples.arena(false, SwapNotice.none(), false));
        assertNull(TimeAttackHud.layout(320, 224, running));
        TimeAttackHudState finished = new TimeAttackHudState(true, 6000, -1, Integer.MIN_VALUE, true, false, "",
                CompetitionSamples.results(false));
        assertEquals("TIME 1'41\"19  FINISHED", TimeAttackHud.layout(320, 224, finished).lines().getFirst().plain());
    }

    @Test
    void arenaProgressStripPlacesRacersByRaceShareWithLapTicks() {
        List<int[]> fills = new ArrayList<>();
        var state = CompetitionSamples.room("RUNNING", CompetitionSamples.arena(false, SwapNotice.none(), false));
        MultiplayerHudRenderer.drawProgress(overlay(320, fills), state, 4096, 100);
        int trackWidth = 320 - 8 - 15;
        List<Integer> ticks = MinimapLayout.lapTicks(5, trackWidth);
        assertEquals(4, ticks.size());
        for (int tick : ticks) {
            assertTrue(fills.stream().anyMatch(fill -> fill[0] == 4 + 5 + tick + 1 && fill[2] == 1 && fill[3] == 5),
                    "a lap line at " + tick);
        }
        // Slot 3 is at 610 of 1000: further right than the local racer at 480, whatever its level x.
        int expected = 4 + 5 + MultiplayerHudRenderer.STACK_BIN * (MinimapLayout.permilleX(610, trackWidth)
                / MultiplayerHudRenderer.STACK_BIN);
        assertTrue(fills.stream().anyMatch(fill -> fill[4] == 0xFFFF6060 && fill[0] == expected),
                "rival drawn at its race share");
    }

    private static boolean overlaps(HudTextLayout.Panel panel, int x, int y, int w, int h) {
        return panel.x() < x + w && x < panel.x() + panel.width() && panel.y() < y + h && y < panel.y() + panel.height();
    }

    private static PixelCanvas overlay(int width, List<int[]> fills) {
        return new PixelCanvas() {
            @Override public int width() { return width; }
            @Override public int height() { return 224; }
            @Override public void fill(int x, int y, int w, int h, int argb) { fills.add(new int[] {x, y, w, h, argb}); }
        };
    }
}
