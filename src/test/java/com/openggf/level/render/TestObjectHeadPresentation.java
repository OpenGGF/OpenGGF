package com.openggf.level.render;

import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.PatternAtlasRange;
import com.openggf.graphics.SpritePresentation;
import com.openggf.game.sonic2.Sonic2ObjectArt;
import com.openggf.data.RomByteReader;
import com.openggf.tests.FullReset;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Isolated;
import java.util.Set;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SingletonResetExtension.class) @FullReset @Isolated
class TestObjectHeadPresentation {
    @Test @RequiresRom(SonicGame.SONIC_2)
    void repeatedNativeEnemiesHaveIndependentFrozenHeadFragmentsAndStockProjectiles() throws Exception {
        var graphics=GraphicsManager.getInstance(); graphics.initHeadless(); graphics.discardQueuedCommands();
        var art=new Sonic2ObjectArt(TestEnvironment.currentRom(),RomByteReader.fromRom(TestEnvironment.currentRom()));
        var renderer=new PatternSpriteRenderer(art.loadCoconutsSheet(),graphics);
        renderer.ensurePatternsCached(graphics,0x10000);
        var first=frame(graphics,renderer,0,100,false,150);
        var second=frame(graphics,renderer,1,160,true,200);
        Set<Integer> firstIds=headIds(first),secondIds=headIds(second);
        assertFalse(firstIds.isEmpty()); assertFalse(secondIds.isEmpty());
        assertTrue(java.util.Collections.disjoint(firstIds,secondIds),"same-art siblings cannot reuse queued cutouts");
        assertTrue(first.tiles().stream().allMatch(t->t.layer()==SpritePresentation.Layer.OBJECT));
        SpritePresentationRenderer.draw(graphics,first,0,0,layer->true);
        SpritePresentationRenderer.draw(graphics,second,0,0,layer->true);
        for(int id:firstIds) assertEquals(first.patternVersions().get(id),SpritePresentation.patternSample(graphics,id));
        var projectile=frame(graphics,renderer,3,100,false,200);
        assertTrue(headIds(projectile).isEmpty(),"Coconuts' thrown coconut keeps native size");
        graphics.discardQueuedCommands();
        var next=frame(graphics,renderer,0,100,false,150);
        assertEquals(firstIds,headIds(next),"completed/aborted frames release only their draw-local companions");
    }

    @Test @RequiresRom(SonicGame.SONIC_2)
    void ehzRobotnikHeadEnlargesWithoutResizingVehicleWheelsSpikesOrPropeller() throws Exception {
        var graphics=GraphicsManager.getInstance(); graphics.initHeadless(); graphics.discardQueuedCommands();
        var art=new Sonic2ObjectArt(TestEnvironment.currentRom(),RomByteReader.fromRom(TestEnvironment.currentRom()));
        var renderer=new PatternSpriteRenderer(art.loadEHZBossSheet(),graphics);
        renderer.ensurePatternsCached(graphics,0x10000);
        for(int i=0;i<16;i++) {
            var nativeFrame=frame(graphics,renderer,i,100,false,100);
            var selected=frame(graphics,renderer,i,100,false,200);
            assertEquals(nativeFrame.patternVersions(),selected.patternVersions(),"native machinery frame "+i);
            assertTrue(headIds(selected).isEmpty());
        }
        for(int i=16;i<22;i++) {
            var head=frame(graphics,renderer,i,100,false,200);
            assertFalse(headIds(head).isEmpty(),"actual vehicle-top Robotnik frame "+i);
            assertTrue(head.tiles().stream().anyMatch(t->t.width()==8),"upper seat remains at native size");
            assertTrue(head.tiles().stream().allMatch(t->t.subject().head()==null));
        }
        var captured=SpritePresentationRenderer.prepare(graphics,0,0,()-> {
            graphics.beginSpriteSatCollection();
            SpritePresentation.withSubject(graphics,new SpritePresentation.Subject("boss",SpritePresentation.Part.OBJECT_BODY,false,200),
                    ()->renderer.drawFrameIndexForcedPriority(16,100,100,false,false,0,true));
            graphics.endSpriteSatCollectionAndReplay();
        });
        assertFalse(headIds(captured).isEmpty(),"native SAT/forced-priority path also carries the reviewed head");
        assertTrue(captured.tiles().stream().allMatch(t->t.priority()&&t.layer()==SpritePresentation.Layer.OBJECT));
        boolean enlarged=false;
        for(boolean priority:new boolean[]{false,true}) {
            var filtered=SpritePresentationRenderer.prepare(graphics,0,0,()->SpritePresentation.withSubject(graphics,
                    new SpritePresentation.Subject("boss",SpritePresentation.Part.OBJECT_BODY,false,200),
                    ()->renderer.drawFrameIndexFilteredByPriority(16,100,100,false,false,0,priority)));
            assertTrue(filtered.tiles().stream().allMatch(t->t.priority()==priority),
                    "head fragments retain their native terrain-priority partition");
            enlarged|=!headIds(filtered).isEmpty();
        }
        assertTrue(enlarged,"filtered native boss pass includes its reviewed head");
    }

    private SpritePresentation.Frame frame(GraphicsManager graphics,PatternSpriteRenderer renderer,int frame,int x,boolean flip,int scale) {
        return SpritePresentationRenderer.prepare(graphics,0,0,()->SpritePresentation.withSubject(graphics,
                new SpritePresentation.Subject("enemy",SpritePresentation.Part.OBJECT_BODY,false,scale),
                ()->renderer.drawFrameIndex(frame,x,100,flip,false)));
    }
    private Set<Integer> headIds(SpritePresentation.Frame frame) {
        return frame.tiles().stream().filter(t->t.width()!=8).map(SpritePresentation.Tile::patternId).collect(Collectors.toSet());
    }
}
