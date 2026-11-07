package com.openggf.game.sonic2.competition.view;

import com.openggf.camera.Camera;
import com.openggf.game.sonic2.objects.PointsObjectInstance;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.*;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@RequiresRom(SonicGame.SONIC_2)
class TestSonic2PointsNativePriority {
    @Test void romInitPriorityReachesTheOrdinaryHighPriorityPass() throws Exception {
        byte[] rom=TestEnvironment.currentRom().readAllBytes();
        // Obj29_Init (s2.asm:24918-24926): move.w #make_art_tile(Numbers,0,1),art_tile(a0).
        assertArrayEquals(java.util.HexFormat.of().parseHex("317c84ac0002"),
                java.util.Arrays.copyOfRange(rom,0x11DE4,0x11DEA));
        assertArrayEquals(java.util.HexFormat.of().parseHex("117c00010018"),
                java.util.Arrays.copyOfRange(rom,0x11DF4,0x11DFA));
        var renderer=mock(PatternSpriteRenderer.class);
        var art=mock(ObjectRenderManager.class);
        when(art.getPointsRenderer()).thenReturn(renderer);
        var graphics=GraphicsManager.getInstance(); graphics.initHeadless();
        try {
            var camera=mock(Camera.class);
            when(camera.getWidth()).thenReturn((short)320);when(camera.getHeight()).thenReturn((short)224);
            var services=new StubObjectServices(){@Override public ObjectRenderManager renderManager(){return art;}};
            var manager=new ObjectManager(List.of(),null,0,null,null,graphics,camera,services);
            var points=new PointsObjectInstance(new ObjectSpawn(100,80,0x29,0,0,false,0),services,100);
            manager.addDynamicObjectAtSlot(points,40);
            assertTrue(points.isHighPriority(),"ROM bit15 must reach the S2 owner");
            assertEquals(1,points.getPriorityBucket());
            manager.drawLowPriority();
            verifyNoInteractions(renderer);
            manager.drawHighPriority();
            verify(renderer).drawFrameIndex(0,100,80,false,false);
        } finally {graphics.resetState();}
    }
}
