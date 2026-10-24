package com.openggf.game;

import com.openggf.control.InputHandler;
import com.openggf.graphics.PixelFont;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

class TestMenuDetailsScreen {
    @Test void sharedHelpUsesCompactTitleAndOneArrowFooterAndAWorkingBackButton() {
        var screen=new MenuDetailsScreen("Audio cue","A readable explanation.");
        var draws=new ArrayList<String>();var titleScale=new float[1];
        screen.render(new PixelFont(){@Override public void drawText(String text,int x,int y,float scale,float r,float g,float b,float a){
            draws.add(text);if(text.equals("Audio cue"))titleScale[0]=scale;
        }},320);
        assertEquals(MenuStyle.COMPACT,titleScale[0],"Details use the small form title, not the old large heading");
        assertTrue(draws.contains("Back"));
        assertEquals(1,draws.stream().filter(t->t.contains("↑↓")&&t.contains("Enter")&&t.contains("Esc")).count());
        assertTrue(draws.stream().noneMatch(t->t.equals("DETAILS")||t.contains("Lines 1-")));
        var input=new InputHandler();input.handleKeyEvent(GLFW_KEY_ENTER,GLFW_PRESS);input.refreshLogicalSnapshot();
        assertTrue(screen.update(input),"Enter activates the primary Back button");
    }
    @Test void longDetailsRemainScrollableAndTheFullWidthButtonsStayVisible() {
        var screen=new MenuDetailsScreen("Long help",java.util.stream.IntStream.range(0,40)
                .mapToObj(i->"Line "+i).collect(java.util.stream.Collectors.joining("\n")));
        var draws=new ArrayList<String>();
        var font=new PixelFont(){@Override public void drawText(String t,int x,int y,float scale,float r,float g,float b,float a){draws.add(t);}};
        screen.render(font,320);assertTrue(draws.contains("Line 0"));assertFalse(draws.contains("Line 39"));assertTrue(draws.contains("Back"));
        for(int i=0;i<8;i++) {var input=new InputHandler();input.handleKeyEvent(GLFW_KEY_RIGHT,GLFW_PRESS);input.refreshLogicalSnapshot();screen.update(input);}
        draws.clear();screen.render(font,320);assertTrue(draws.contains("Line 39"));assertTrue(draws.contains("Back"));
        assertEquals("Unsupported [U+1F600]",MenuDetailsScreen.readable("Unsupported 😀"));
    }

}
