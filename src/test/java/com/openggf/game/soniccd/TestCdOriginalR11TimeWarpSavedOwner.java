package com.openggf.game.soniccd;

import com.openggf.game.save.SaveManager;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.objects.CdTimeWarpSaveData;
import com.openggf.game.soniccd.level.CdAssetBytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** SOURCE tests first. All Java UNRUN; real public ingress or explicit inert projection controls. */
@Isolated
class TestCdOriginalR11TimeWarpSavedOwner extends TestCdOriginalR11LoadingWorld {
    private static CdProgramInitialization.ExecutionEnvironment environment(){return new CdProgramInitialization.ExecutionEnvironment(Optional.of(new CdProgramInitialization.ExecutionRegion(0)));}
    private SonicCdGameModule.SelectedProfileAttempt attempt(CdGameDataSource source)throws Exception{return SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);}
    private static void ready(com.openggf.GameLoop loop,SonicCdGameModule module){for(int row=0;row<256&&module.originalR11LoadingPrefix().main().levelStartedByte()==0;row++)step(loop,0);assertEquals(1,module.originalR11LoadingPrefix().main().levelStartedByte());}
    private static CdTimeWarpSaveData.State privateBank(SonicCdGameModule module)throws Exception{var f=SonicCdGameModule.class.getDeclaredField("r11TimeWarpSaved");f.setAccessible(true);return(CdTimeWarpSaveData.State)f.get(module);}
    private static CdTimeWarpSaveData.State projection(int first,int end)throws Exception{var m=CdOriginalBootPrefix.class.getDeclaredMethod("warpSavedProjection",int.class,int.class);m.setAccessible(true);return(CdTimeWarpSaveData.State)m.invoke(null,first,end);}
    @Test void bothRealMainClearsProduceTheDistinctKnownWarpBank()throws Exception {
        var p=CdOriginalBootPrefix.prepare(TestCdOriginalBootPrefix.source(),new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,environment());
        assertEquals(2,Collections.frequency(p.boot().state().calls(),CdOriginalBootPrefix.Call.MAIN_VARIABLES_CLEAR));
        var saved=p.boot().timeWarpSavedAfterMainClear();assertSame(saved,p.boot().timeWarpSavedAfterMainClear());
        assertEquals(42,saved.knownMask().length());for(byte known:saved.knownMask().copy())assertEquals(1,known);
        for(var field:CdTimeWarpSaveData.SavedField.values())assertEquals(0,saved.read(field));
    }
    @Test void constructedClearProjectionLeavesEveryUnaddressedByteTypedUnknown()throws Exception {
        var partial=projection(0xFF1548,0xFF154A);assertEquals(0,partial.read(CdTimeWarpSaveData.SavedField.PRIMARY_X));
        var edge=assertThrows(CdTimeWarpSaveData.UnknownSavedByte.class,()->partial.read(CdTimeWarpSaveData.SavedField.PRIMARY_Y));assertEquals(0xFF154A,edge.nativeAddress());
        assertEquals(2L,java.util.stream.IntStream.range(0,partial.knownMask().length()).filter(i->partial.knownMask().unsignedByte(i)==1).count());
    }
    @Test void emptyConstructedProjectionDoesNotInventClearedKnowledge()throws Exception {
        var empty=projection(0xFF1548,0xFF1548);assertEquals(CdTimeWarpSaveData.State.unknown(),empty);
        assertThrows(CdTimeWarpSaveData.UnknownSavedByte.class,()->empty.read(CdTimeWarpSaveData.SavedField.RINGS));
    }
    @Test void devAndOldSelectedValuesDoNotCreateAnOriginalBankOwner()throws Exception {
        var dev=SonicCdGameModule.openingObjectsDevelopmentPlayer();assertNull(privateBank(dev));assertThrows(IllegalStateException.class,dev::originalR11TimeWarpSavedImage);
        var value=CdTimeWarpSaveData.State.supplied(CdAssetBytes.copyOf(new byte[42]),CdAssetBytes.copyOf(new byte[42]));assertEquals(CdTimeWarpSaveData.State.unknown(),value);
        assertNull(privateBank(dev));
    }
    @Test void publicStoreMetadataIsNotAnAcceptedModuleWriteCapability(){
        var writes=Arrays.stream(SonicCdGameModule.OriginalR11Access.class.getDeclaredMethods()).filter(m->m.getName().equals("timeWarpSaved")&&m.getParameterCount()==1).toList();
        assertEquals(1,writes.size());assertEquals(CdWorld.OriginalR11TimeWarpSaveStore.class,writes.getFirst().getParameterTypes()[0]);
        assertFalse(Arrays.stream(SonicCdGameModule.OriginalR11Access.class.getDeclaredMethods()).anyMatch(m->m.getName().equals("timeWarpSaved")&&Arrays.asList(m.getParameterTypes()).contains(CdTimeWarpSaveData.StoreCall.class)));
        assertTrue(Arrays.stream(CdWorld.OriginalR11TimeWarpSaveStore.class.getDeclaredConstructors()).allMatch(c->Modifier.isPrivate(c.getModifiers())));
    }
    @Test void preparedButUnboundModuleCannotReadItsOriginalBank()throws Exception {
        var m=attempt(TestCdOriginalBootPrefix.source()).module().orElseThrow();var before=privateBank(m);assertNotNull(before);
        assertThrows(IllegalStateException.class,m::originalR11TimeWarpSavedImage);assertSame(before,privateBank(m));
    }
    @Test void forgedPrivateAccessRejectsBeforeInspectingAnyStoreCarrier()throws Exception {
        var m=attempt(TestCdOriginalBootPrefix.source()).module().orElseThrow();var before=privateBank(m);
        var ctor=SonicCdGameModule.OriginalR11Access.class.getDeclaredConstructor(SonicCdGameModule.class);ctor.setAccessible(true);var forged=ctor.newInstance(m);
        var original=mock(CdWorld.OriginalR11TimeWarpSaveStore.class);assertThrows(IllegalStateException.class,()->forged.timeWarpSaved(original));verifyNoInteractions(original);
        assertThrows(IllegalStateException.class,forged::timeWarpSaved);assertSame(before,privateBank(m));
    }
    @Test void readyAndOrdinaryNonwriteRowsRetainTheExactEarlierClearProduct()throws Exception {
        var source=TestCdOriginalBootPrefix.source();var a=attempt(source);var m=a.module().orElseThrow();var seed=privateBank(m);
        try(var boot=boot();var cleanup=disposeWhileContextCurrent()){var loop=boot.bootSonicCdSelectedProfile(source,a);ready(loop,m);
            assertSame(seed,m.originalR11TimeWarpSavedImage());step(loop,0);step(loop,0);assertSame(seed,m.originalR11TimeWarpSavedImage());
            for(var field:CdTimeWarpSaveData.SavedField.values())assertEquals(0,seed.read(field));
        }
    }
    @Test void fullAndCourseRestoreRetainTheExactIssuedBankReference()throws Exception {
        var source=TestCdOriginalBootPrefix.source();var a=attempt(source);var m=a.module().orElseThrow();
        try(var boot=boot();var cleanup=disposeWhileContextCurrent()){var loop=boot.bootSonicCdSelectedProfile(source,a);ready(loop,m);var bank=m.originalR11TimeWarpSavedImage();
            var registry=SessionManager.getCurrentGameplayMode().getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();
            step(loop,8);registry.restore(full);assertSame(bank,m.originalR11TimeWarpSavedImage());
            step(loop,8);registry.restoreCourse(course);assertSame(bank,m.originalR11TimeWarpSavedImage());assertFalse(m.originalR11EntryFrame().closed());
        }
    }
    @Test void wrongThreadReadRejectsAndLeavesTheOwnedBankUnchanged()throws Exception {
        var source=TestCdOriginalBootPrefix.source();var a=attempt(source);var m=a.module().orElseThrow();
        try(var boot=boot();var cleanup=disposeWhileContextCurrent()){var loop=boot.bootSonicCdSelectedProfile(source,a);ready(loop,m);var bank=m.originalR11TimeWarpSavedImage();var result=new AtomicReference<Throwable>();
            var other=new Thread(()->{try{m.originalR11TimeWarpSavedImage();}catch(Throwable failure){result.set(failure);}});other.start();other.join();assertInstanceOf(IllegalStateException.class,result.get());assertSame(bank,m.originalR11TimeWarpSavedImage());
        }
    }
    @Test void retirementRevokesReadAndRestoreWithoutErasingSavedData()throws Exception {
        var source=TestCdOriginalBootPrefix.source();var a=attempt(source);var m=a.module().orElseThrow();Runnable restore;
        try(var boot=boot();var cleanup=disposeWhileContextCurrent()){var loop=boot.bootSonicCdSelectedProfile(source,a);ready(loop,m);var registry=SessionManager.getCurrentGameplayMode().getRewindRegistry();var full=registry.capture();restore=()->registry.restore(full);}
        var retained=privateBank(m);assertNotNull(retained);assertThrows(IllegalStateException.class,m::originalR11TimeWarpSavedImage);assertThrows(RuntimeException.class,restore::run);assertSame(retained,privateBank(m));
    }
    @Test void bankIsNotTheCheckpointOrDevOpeningSaveOwner()throws Exception {
        var source=TestCdOriginalBootPrefix.source();var a=attempt(source);var m=a.module().orElseThrow();
        try(var boot=boot();var cleanup=disposeWhileContextCurrent()){var loop=boot.bootSonicCdSelectedProfile(source,a);ready(loop,m);var warp=m.originalR11TimeWarpSavedImage();var checkpoint=m.originalR11LoadingImage().globals().checkpointSaved();
            assertNotSame(checkpoint,warp);assertNotEquals(checkpoint,warp);var field=SonicCdGameModule.class.getDeclaredField("openingObjects");field.setAccessible(true);assertNull(field.get(m));
        }
    }
    @Test void foreignSelectedWorldCaptureCannotMutateTheCurrentBank()throws Exception {
        var source=TestCdOriginalBootPrefix.source();var first=attempt(source);var prior=first.module().orElseThrow();com.openggf.game.rewind.CompositeSnapshot image;
        try(var boot=boot();var cleanup=disposeWhileContextCurrent()){var loop=boot.bootSonicCdSelectedProfile(source,first);ready(loop,prior);image=SessionManager.getCurrentGameplayMode().getRewindRegistry().capture();}
        var second=attempt(source);var current=second.module().orElseThrow();
        try(var boot=boot();var cleanup=disposeWhileContextCurrent()){var loop=boot.bootSonicCdSelectedProfile(source,second);ready(loop,current);var bank=current.originalR11TimeWarpSavedImage();
            var registry=SessionManager.getCurrentGameplayMode().getRewindRegistry();assertThrows(RuntimeException.class,()->registry.restore(image));
            assertSame(bank,current.originalR11TimeWarpSavedImage());assertFalse(current.originalR11EntryFrame().closed());
        }
    }

    // Retire World/textures before the existing boot releases its real EGL context.
    private static AutoCloseable disposeWhileContextCurrent(){return ()->{
        Throwable failure=null;
        try{SessionManager.clear();}catch(RuntimeException|Error original){failure=original;}
        try{com.openggf.game.GameServices.graphics().cleanup();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(cleanup!=failure)failure.addSuppressed(cleanup);}
        if(failure instanceof RuntimeException original)throw original;
        if(failure instanceof Error original)throw original;
    };}
}
