package com.openggf.level.objects;

import com.openggf.camera.Camera;
import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestObjectDrawNativeDisplayOrder {
    @Test void visitorMatchesSatFifoIncludingRepeatsZeroSkipAndCrossBucketSuppression() {
        var graphics = GraphicsManager.getInstance();
        graphics.initHeadless();
        try {
            var camera = mock(Camera.class);
            when(camera.getWidth()).thenReturn((short)320);
            when(camera.getHeight()).thenReturn((short)224);
            var manager = new ObjectManager(List.of(),null,0,null,null,graphics,camera,new StubObjectServices());
            var draws = new ArrayList<Integer>();
            var legacy = add(manager,40,3,draws);
            var queued = add(manager,50,6,draws);
            var zero = add(manager,60,3,draws);
            zero.liveId=0;
            add(manager,70,1,draws);
            add(manager,80,7,draws);
            NativeSpriteSubmissionAccess.submit(manager,50,3);
            NativeSpriteSubmissionAccess.submit(manager,50,3);
            NativeSpriteSubmissionAccess.submit(manager,60,3);
            NativeSpriteSubmissionAccess.submit(manager,61,3);
            NativeSpriteSubmissionAccess.submit(manager,50,0);
            NativeSpriteSubmissionAccess.submit(manager,80,5);
            var seen = new ArrayList<String>();
            ObjectDrawInternalAccess.visitNativeDisplayOrder(manager, (b,s,o,n) -> seen.add(b+":"+s+":"+n));
            assertEquals(List.of("0:50:true","1:70:false","3:50:true","3:50:true","3:40:false","5:80:true"),seen);
            assertTrue(draws.isEmpty(),"Visitor must not draw");
            var sat = mock(GraphicsManager.class);
            when(sat.isSpriteSatCollectionActive()).thenReturn(true);
            for(int bucket=0;bucket<8;bucket++) ObjectDrawInternalAccess.drawBucket(manager,bucket,sat,List.of());
            assertEquals(List.of(50,70,50,50,40,80),draws);
            var repeated = new ArrayList<Integer>();
            ObjectDrawInternalAccess.visitNativeDisplayOrder(manager,(b,s,o,n)->repeated.add(s));
            assertEquals(draws,repeated);
        } finally { graphics.resetState(); }
    }

    private static Probe add(ObjectManager manager,int slot,int bucket,List<Integer> draws) {
        var p=new Probe(new ObjectSpawn(80,80,1,0,0,false,0),bucket,draws);
        manager.addDynamicObjectAtSlot(p,slot);
        return p;
    }
    private static final class Probe extends AbstractObjectInstance {
        final int bucket; final List<Integer> draws; int liveId=1;
        Probe(ObjectSpawn spawn,int bucket,List<Integer> draws) {super(spawn,"display-order-probe");this.bucket=bucket;this.draws=draws;}
        @Override public int getPriorityBucket(){return bucket;}
        @Override public int getLiveObjectId(){return liveId;}
        @Override public void update(int clock,PlayableEntity player) { }
        @Override public void appendRenderCommands(List<GLCommand> commands){draws.add(getSlotIndex());}
    }
}
