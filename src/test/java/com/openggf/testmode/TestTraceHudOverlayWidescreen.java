package com.openggf.testmode;

import com.openggf.game.GameServices;
import com.openggf.trace.live.LiveTraceComparator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_TAB;

class TestTraceHudOverlayWidescreen {
    @AfterEach void resetProjectionWidth(){GameServices.graphics().setProjectionWidth(320);}
    @Test void detailsAndSummaryStayInsideTheActualNativeAndWideViewport(){
        var model=mock(LiveTraceComparator.class);when(model.recentMismatches()).thenReturn(List.of());
        var overlay=new TraceHudOverlay(model,()->"ENTER",()->true,()->"Sidekick");
        overlay.setPlaybackStatusSupplier(()->new TracePlaybackStatus("review.bk2","LEVEL",2147483646,2147483647,"1x"));
        var text=new TestTraceHudOverlay.RecordingText();
        for(int width:new int[]{320,352,400}){
            GameServices.graphics().setProjectionWidth(width);
            for(int mode=0;mode<2;mode++){
                text.draws.clear();overlay.render(text);
                assertTrue(text.draws.stream().allMatch(d->d.x()>=0&&d.x()+text.measureWidth(d.text(),d.scale())<=width));
                if(mode==0)TestTraceHudOverlay.press(overlay,GLFW_KEY_TAB);
            }
            assertTrue(text.contains("Camera: Sidekick (Left/Right)"));
            TestTraceHudOverlay.press(overlay,GLFW_KEY_TAB);
        }
    }
}
