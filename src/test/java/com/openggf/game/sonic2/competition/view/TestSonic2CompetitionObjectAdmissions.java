package com.openggf.game.sonic2.competition.view;

import com.openggf.camera.Camera;
import com.openggf.game.PlayableEntity;
import com.openggf.graphics.*;
import com.openggf.level.objects.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static com.openggf.game.sonic2.competition.view.Sonic2CompetitionDisplayInputs.*;

class TestSonic2CompetitionObjectAdmissions {
    @Test void nativeCallsKeepTheirFifoAndCapturedBucketWhileCorrectedLegacyBucketsKeepSlotOrder() {
        var graphics=GraphicsManager.getInstance();graphics.initHeadless();
        try {
            var manager=new ObjectManager(List.of(),null,0,null,null,graphics,mock(Camera.class),new StubObjectServices());
            add(manager,70,1,0);add(manager,40,4,0);add(manager,50,6,0);
            NativeSpriteSubmissionAccess.submit(manager,50,3);NativeSpriteSubmissionAccess.submit(manager,50,3);
            var input=Sonic2CompetitionObjectDisplay.admissions(manager,null);
            assertEquals(List.of(40,70,50,50),input.stream().map(Admission::slot).toList());
            assertEquals(List.of(0,0,3,3),input.stream().map(Admission::bucket).toList());
            assertEquals(List.of(0,1,2,3),input.stream().map(Admission::ordinal).toList());
        } finally {graphics.resetState();}
    }
    @Test void namedDeferredOwnersHaveNoOrdinaryFallbackAndRemainVisibleInTheExclusionInventory() {
        var graphics=GraphicsManager.getInstance();graphics.initHeadless();
        try {
            var manager=new ObjectManager(List.of(),null,0,null,null,graphics,mock(Camera.class),new StubObjectServices());
            var owner=new AbstractObjectInstance(new ObjectSpawn(100,80,0x76,0,0,false,0),"deferred-spikes") {
                @Override public void update(int vint,PlayableEntity player) { }
                @Override public void appendRenderCommands(List<GLCommand> commands){fail("deferred owner cannot fall back to ordinary drawing");}
            };
            manager.addDynamicObjectAtSlot(owner,40);
            assertTrue(Sonic2CompetitionObjectDisplay.admissions(manager,null).isEmpty());
            assertEquals(0x76,Sonic2CompetitionObjectDisplay.deferred(manager).getFirst().id());
            assertEquals(40,Sonic2CompetitionObjectDisplay.deferred(manager).getFirst().slot());
            assertFalse(Sonic2CompetitionObjectDisplay.adopted(owner));
        } finally {graphics.resetState();}
    }
    private static void add(ObjectManager manager,int slot,int legacyBucket,int nativeBucket) {
        manager.addDynamicObjectAtSlot(new Probe(legacyBucket,nativeBucket),slot);
    }
    private static final class Probe extends AbstractObjectInstance implements Sonic2NativeDisplaySource {
        private final int legacyBucket,nativeBucket;
        Probe(int legacyBucket,int nativeBucket){super(new ObjectSpawn(100,80,0x11,8,0,false,0),"admission-probe");this.legacyBucket=legacyBucket;this.nativeBucket=nativeBucket;}
        @Override public int getPriorityBucket(){return legacyBucket;}
        @Override public ObjectSlot nativeDisplaySlot(){return new ObjectSlot(0x11,4,100,80,8,0,new Mapping(0xFC70,0),0x41DB,null);}
        @Override public List<DisplaySlot> nativeDisplaySlots(){return List.of(new DisplaySlot(getSlotIndex(),nativeBucket,nativeDisplaySlot()));}
        @Override public void update(int vint,PlayableEntity player) { }
        @Override public void appendRenderCommands(List<GLCommand> commands){fail("explicit native source must not draw");}
    }
}
