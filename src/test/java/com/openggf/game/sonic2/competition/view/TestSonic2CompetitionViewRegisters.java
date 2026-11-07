package com.openggf.game.sonic2.competition.view;

import com.openggf.camera.Camera;
import com.openggf.game.resources.PlcLifecyclePhase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import static com.openggf.game.sonic2.competition.view.Sonic2CompetitionPlanes.PlaneId.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestSonic2CompetitionViewRegisters {
    private static final List<Integer> PALETTE = java.util.stream.IntStream.range(0, 64).boxed().toList();
    private static Sonic2CompetitionSpritePages.Pair pages() {
        return new Sonic2CompetitionSpritePages.Pair(Sonic2CompetitionSatBuilder.Page.empty(0), Sonic2CompetitionSatBuilder.Page.empty(1));
    }
    private static void publish(Sonic2CompetitionViewRegisters owner, PlcLifecyclePhase phase, int one, int two,
                                int horizontal, int timer, boolean paused) {
        int[] rows = new int[224]; java.util.Arrays.fill(rows, horizontal);
        var camera = new Camera().capture();
        owner.publish(phase, one, two, rows, camera, camera, pages(), PALETTE, timer, paused);
    }
    @ParameterizedTest @EnumSource(value=PlcLifecyclePhase.class, names={"ORDINARY_LEVEL","NORMAL_PAUSE","LEVEL_TITLE_CARD","LAG","PALETTE_FADE"})
    void everyInterruptLatchesItsOwnCellsAndRetainsTheUntouchedCells(PlcLifecyclePhase phase) {
        var camera = new Camera().capture();
        var owner = new Sonic2CompetitionViewRegisters(camera, camera);
        publish(owner, PlcLifecyclePhase.ORDINARY_LEVEL, 0x00200040, 0x00600080, 0x11112222, 0, false);
        publish(owner, phase, 0xFFFEFFFC, 0xFFFAFFF8, 0x33334444, 0, false);
        var actual = owner.capture();
        assertEquals(-2, actual.foreground1()); assertEquals(-4, actual.background1());
        assertTrue(actual.published());
        switch (phase) {
            case ORDINARY_LEVEL, NORMAL_PAUSE -> {
                assertEquals(FOREGROUND_P1, actual.upperPlane());
                assertEquals(-6, actual.foreground2()); assertEquals(-8, actual.background2());
                assertEquals(0x33334444, actual.horizontal().get(108));
            }
            case LEVEL_TITLE_CARD -> {
                assertEquals(FOREGROUND_P2, actual.upperPlane());
                assertEquals(-6, actual.foreground2()); assertEquals(-8, actual.background2());
                assertEquals(0x33334444, actual.horizontal().get(108));
            }
            case LAG -> {
                assertEquals(FOREGROUND_P1, actual.upperPlane());
                assertEquals(-6, actual.foreground2()); assertEquals(-8, actual.background2());
                assertEquals(0x11112222, actual.horizontal().get(108));
                assertEquals(PALETTE, actual.paletteWords());
            }
            case PALETTE_FADE -> {
                assertEquals(FOREGROUND_P2, actual.upperPlane());
                assertEquals(96, actual.foreground2()); assertEquals(128, actual.background2());
                assertEquals(0x33334444, actual.horizontal().get(108));
            }
            default -> fail("unexpected test selection");
        }
        assertEquals(FOREGROUND_P2, actual.nextUpperPlane());
        if (phase == PlcLifecyclePhase.LEVEL_TITLE_CARD || phase == PlcLifecyclePhase.PALETTE_FADE) {
            // Deliberately wrong reset rule: the comparison must detect it.
            assertThrows(org.opentest4j.AssertionFailedError.class,
                    () -> assertEquals(FOREGROUND_P1, actual.upperPlane(), "wrong rule table"));
        }
        var saved = owner.capture();
        publish(owner, PlcLifecyclePhase.ORDINARY_LEVEL, 0, 0, 0, 0, false);
        owner.restore(saved); assertEquals(saved, owner.capture());
    }
    @Test void teleportWhiteSpareBackdropColourAndPauseRetainsPreviousPalette() {
        var camera = new Camera().capture();
        var owner = new Sonic2CompetitionViewRegisters(camera, camera);
        publish(owner, PlcLifecyclePhase.ORDINARY_LEVEL, 0, 0, 0, 20, false);
        for (int colour = 0; colour < 64; colour++)
            assertEquals(colour == 32 ? 32 : 0xEEE, owner.capture().paletteWords().get(colour));
        var white = owner.capture().paletteWords();
        publish(owner, PlcLifecyclePhase.NORMAL_PAUSE, 0, 0, 0, 16, true);
        assertEquals(white, owner.capture().paletteWords());
        publish(owner, PlcLifecyclePhase.ORDINARY_LEVEL, 0, 0, 0, 16, false);
        assertEquals(PALETTE, owner.capture().paletteWords());
    }
    @ParameterizedTest @EnumSource(value=PlcLifecyclePhase.class, names={"ORDINARY_LEVEL","NORMAL_PAUSE","LEVEL_TITLE_CARD","LAG","PALETTE_FADE"})
    void publicationOrdersDmaPlaneWorkAndProductionPlc(PlcLifecyclePhase phase) {
        var calls = new ArrayList<String>();
        var art = mock(Sonic2CompetitionArtResidency.class);
        var scroll = mock(Sonic2CompetitionPlaneScroll.class);
        var planes = mock(Sonic2CompetitionPlanes.class);
        var draw = mock(Sonic2CompetitionPlaneDraw.class);
        var pages = mock(Sonic2CompetitionSpritePages.class); when(pages.completed()).thenReturn(pages());
        doAnswer(call -> { calls.add("dma"); return null; }).when(art).serviceDmaQueueVBlank();
        doAnswer(call -> { calls.add("plc"); return null; }).when(art).servicePlc(phase);
        doAnswer(call -> { calls.add("copy:" + call.getArgument(0)); return null; }).when(scroll).copyAtVInt(anyBoolean());
        doAnswer(call -> { calls.add("planes"); return null; }).when(scroll).drawForeground(planes, draw);
        var camera = new Camera().capture();
        var registers = new Sonic2CompetitionViewRegisters(camera, camera);
        var video = new Sonic2CompetitionVideoFrame(art, scroll, planes, draw, pages, registers, () -> {
            calls.add("latch");
            return new Sonic2CompetitionVideoFrame.Sample(0, 0, new int[224], camera, camera, PALETTE, 0, false);
        });
        video.setTitleCardPlanes(() -> calls.add("title")); video.serviceVBlank(phase);
        var expected = switch (phase) {
            case ORDINARY_LEVEL, NORMAL_PAUSE -> List.of("latch","dma","copy:false","planes","plc");
            case LEVEL_TITLE_CARD -> List.of("latch","dma","title","copy:true","plc");
            case LAG -> List.of("latch");
            case PALETTE_FADE -> List.of("latch","plc");
            default -> throw new AssertionError();
        };
        assertEquals(expected, calls);
    }
}
