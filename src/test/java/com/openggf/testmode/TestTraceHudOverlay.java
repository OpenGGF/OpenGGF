package com.openggf.testmode;

import com.openggf.control.InputHandler;
import com.openggf.debug.DebugColor;
import com.openggf.graphics.MenuPixelFont;
import com.openggf.graphics.PixelFontTextRenderer;
import com.openggf.graphics.PixelFontVariant;
import com.openggf.trace.Severity;
import com.openggf.trace.live.LiveTraceComparator;
import com.openggf.trace.live.MismatchEntry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.lwjgl.glfw.GLFW.*;

class TestTraceHudOverlay {
    @Test
    void failuresNeverOpenDetailsAndTabOpensAndClosesWithoutChangingComparison() {
        var model = mock(LiveTraceComparator.class);
        when(model.recentMismatches()).thenReturn(List.of(new MismatchEntry(44,"p1CentreX","100","321","221",Severity.ERROR,1)));
        when(model.errorCount()).thenReturn(1);
        when(model.hasRecordingDesync()).thenReturn(true);
        var overlay = new TraceHudOverlay(model, () -> "ENTER", () -> true, () -> null);
        overlay.setPlaybackStatusSupplier(() -> null);
        var text = new RecordingText();
        overlay.render(text);
        assertFalse(overlay.detailsVisible());
        assertTrue(text.contains("Tab: Details"));
        assertFalse(text.contains("TRACE DETAILS"));
        press(overlay, GLFW_KEY_TAB);
        text.draws.clear(); overlay.render(text);
        assertTrue(text.contains("TRACE DETAILS"));
        assertTrue(text.contains("Field: p1CentreX"));
        assertTrue(text.contains("ROM: 100"));
        assertTrue(text.contains("Engine: 321"));
        assertTrue(text.contains("Mismatch 1/1  F:44"));
        assertTrue(text.draws.stream().allMatch(d -> d.scale == 1), "no fractional debug-font downsampling");
        press(overlay, GLFW_KEY_TAB);
        assertFalse(overlay.detailsVisible());
        text.draws.clear(); overlay.render(text);
        assertFalse(text.contains("TRACE DETAILS"), "persistent errors cannot reopen a dismissed panel");
    }

    @Test
    void longIdentifiersAndEveryMismatchRemainAvailableThroughNativeSizePages() {
        var model = mock(LiveTraceComparator.class);
        String identifier = "long_object_field_".repeat(7);
        when(model.recentMismatches()).thenReturn(List.of(
                new MismatchEntry(44,identifier,"-2147483648","2147483647","4294967295",Severity.ERROR,2),
                new MismatchEntry(45,"cameraY","16","32","16",Severity.WARNING,1)));
        var overlay = new TraceHudOverlay(model, () -> "ENTER", () -> false, () -> null);
        overlay.setPlaybackStatusSupplier(() -> new TracePlaybackStatus("long-file-name-".repeat(5)+".bk2","LEVEL",1248,3600,"1x"));
        var text = new RecordingText();
        press(overlay, GLFW_KEY_TAB);
        var seen = new StringBuilder();
        for (int i=0;i<5;i++) {
            text.draws.clear(); overlay.render(text);
            for (Draw d:text.draws) {
                assertTrue(d.x >= 0 && d.x + text.measureWidth(d.text, d.scale) <= 320, d.text);
                assertTrue(d.y >= 0 && d.y + 8 <= 224, d.text);
                assertTrue(d.text.startsWith("Input:") || !d.text.contains("..."), "diagnostics wrap rather than lose values");
                if(d.y >= 34 && d.y < 190) seen.append(d.text.replace(" ",""));
            }
            press(overlay, GLFW_KEY_PAGE_DOWN);
        }
        assertTrue(seen.toString().contains(identifier));
        assertTrue(seen.toString().contains("ROM:-2147483648"));
        assertTrue(seen.toString().contains("Engine:2147483647"));
        assertTrue(seen.toString().contains("Field:cameraY"));
    }

    @Test
    void compactTraceStatusKeepsRewindFramesAndPlaybackRateVisible() {
        var model=mock(LiveTraceComparator.class);
        var overlay=new TraceHudOverlay(model,()->"ENTER",()->false,()->null,()->"REWIND 44");
        overlay.setPlaybackStatusSupplier(()->new TracePlaybackStatus("review.bk2","LEVEL",44,3600,"< 1.5x >"));
        var text=new RecordingText();overlay.render(text);
        assertTrue(text.contains("REW"));
        assertTrue(text.contains("F:44"));
        assertTrue(text.contains("< 1.5x > Tab: Details"));
        assertFalse(overlay.detailsVisible());
    }

    @Test
    void onlyPhysicalTabEdgesToggleDetails() {
        var model=mock(LiveTraceComparator.class);
        var overlay=new TraceHudOverlay(model);
        var input=mock(InputHandler.class);
        when(input.isKeyPressed(GLFW_KEY_TAB)).thenReturn(true);
        overlay.handleInput(input);
        assertFalse(overlay.detailsVisible(), "recorded logical input must not control presentation");
        when(input.isPhysicalKeyPressed(GLFW_KEY_TAB)).thenReturn(true);
        overlay.handleInput(input);
        assertTrue(overlay.detailsVisible());
    }

    static void press(TraceSessionOverlay overlay,int key) {
        var input=new InputHandler();input.handleKeyEvent(key,GLFW_PRESS);
        overlay.handleInput(input);input.update();
        input.handleKeyEvent(key,GLFW_RELEASE);overlay.handleInput(input);input.update();
    }

    static final class RecordingText extends PixelFontTextRenderer {
        final List<Draw> draws=new ArrayList<>();
        RecordingText(){super(MenuPixelFont.compact(),PixelFontVariant.PIXEL_FONT_NO_SHADOW);}
        @Override public void beginBatch(){}
        @Override public void endBatch(){}
        @Override public void drawShadowedText(String t,int x,int y,DebugColor c,float scale){draws.add(new Draw(t,x,y,scale));}
        boolean contains(String value){return draws.stream().anyMatch(d -> d.text.equals(value));}
    }
    record Draw(String text,int x,int y,float scale){}
}
