package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.*;
import com.openggf.game.soniccd.resources.CdR31aArtResidency;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.lang.reflect.Modifier;
import static org.junit.jupiter.api.Assertions.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static com.openggf.game.soniccd.objects.CdCczEnemyPorts.*;

/** SOURCE-AUTHORED, ALL JAVA UNRUN. Actual media/Frame/placement/physical pass; this Harness
 * explicitly CONSTRUCTS Core/Ports/slot vectors. Public World ingress is tested separately.
 * These tests neither mint World receipts nor establish natural enemy reach or pixels. */
class TestSonicCdR31aKamaSicklePass {
    @Test void actualSourceRootsCanonicalBytesAndDispatchAreIndependentLiterals() throws Exception {
        try(var h=new TestSonicCdR31aObjectPass.Harness()) {
            assertEquals("30bb932c3f7a18f0629dcee56fb982280e0b6d6063fb67392b23771f77e7682e",h.assets.identity().mmdSha256());
            var index=h.assets.view(CdR31aAssets.Asset.OBJECT_INDEX);
            assertEquals(0x20CFFA,index.unsignedLong((0x24-1)*4));
            assertEquals(0x20D27A,index.unsignedLong((0x25-1)*4));
            var a=h.objects.kamaSickleAssets();assertSame(a,h.objects.kamaSickleAssets());
            mapping(a,Mapping.KAMA_NORMAL,0x2358CA,431,8,"435a7e1a739c02ae53237d4bd2447e480923c3eec44c21e93c9e4d789e13507f");
            mapping(a,Mapping.KAMA_DAMAGED,0x235A7A,348,8,"3d89637d4a719b3c95d1e24e680f4182a105fb1f65e476065df755a9ab5cdc5a");
            mapping(a,Mapping.SICKLE,0x20D3DE,48,6,"f2f67125c9a5b050be133ee9bae536f9f447405a0eec0f683171f850e5c8a85b");
            assertEquals("fec1533c31f365f30c4290f9a67398d975c19c455d0f741ac872c04ea03d672f",sha(a.animation(Animation.KAMA).bytes()));
            assertEquals("da104fe20b83d1e8055649c6388d10b0e8902f0b3e36b71b842ab56eb96c17e2",sha(a.animation(Animation.SICKLE).bytes()));
            assertEquals(3,a.chainLinkCount());assertThrows(UnsupportedOperationException.class,()->a.mapping(Mapping.GA_NORMAL));
        }
    }
    @Test void sameMapperResolvesAllSixSickleFramesAndTheUnpaddedLastKamaFrame() throws Exception {
        try(var h=new TestSonicCdR31aObjectPass.Harness()) {
            var m=CdObjectMappings.fromR31aAssets(h.assets,h.objects);
            int[] tiles={0x19,0x1D,0x1019,0x1819,0x181D,0x819};
            for(int i=0;i<6;i++) {var f=assertInstanceOf(CdObjectDrawQueue.FrameMapping.class,m.resolve(0x20D3DE,false,i));
                assertEquals(1,f.countByte());assertEquals(tiles[i],f.pieces().getFirst().rawTileWord());}
            assertInstanceOf(CdObjectDrawQueue.FrameMapping.class,m.resolve(0x2358CA,false,7));
            assertThrows(UnsupportedOperationException.class,()->m.resolve(0x20D3DE,false,6));
        }
    }
    @Test void mappingPresenceDoesNotCreateCurrentPatterns() throws Exception {
        try(var h=new TestSonicCdR31aObjectPass.Harness()) {
            var unknown=CdR31aArtResidency.unknown(h.assets);
            var e=assertThrows(CdR31aArtResidency.OwnershipBoundary.class,()->CdR31aArtResidency.readTile(unknown,0x3F2));
            assertEquals(CdR31aArtResidency.Boundary.UNKNOWN_PATTERN,e.boundary());
            h.objects.kamaSickleAssets();assertThrows(CdR31aArtResidency.OwnershipBoundary.class,()->CdR31aArtResidency.readTile(unknown,0x3F2));
        }
    }
    @Test void laterChildrenRunInitOnceAndRetainEveryUnprescribedDirtyByte() throws Exception {
        try(var h=new TestSonicCdR31aObjectPass.Harness()) {
            watch(h,32);dirtyFree(h,33);dirtyFree(h,34);var before=h.placement.capture();var actor=h.frame.captureActor();var shared=h.frame.captureShared();
            h.walk();assertEquals(6,h.placement.pool().state(32).routine());assertEquals(0x77,h.placement.pool().state(32).word(TITLE_CARD_START_X));
            childInit(h,33,before.pool().slots().get(1),83,1000,0x40,32);
            childInit(h,34,before.pool().slots().get(2),91,998,0x14,32);
            nonwrites(before,h,Set.of(32,33,34));assertEquals(actor,h.frame.captureActor());assertEquals(shared,h.frame.captureShared());h.finish();
        }
    }
    @Test void earlierChildrenWaitUntilNextPhysicalWalk() throws Exception {
        try(var h=new TestSonicCdR31aObjectPass.Harness()) {
            watch(h,40);h.walk();for(int w:new int[]{32,33}){assertEquals(0x25,h.placement.pool().state(w).id());assertEquals(0,h.placement.pool().state(w).routine());}
            h.finish();h.walk();assertEquals(2,h.placement.pool().state(32).routine());assertEquals(0x40,h.placement.pool().state(32).word(TITLE_CARD_START_X));h.finish();
        }
    }
    @Test void dirtyChildRoutineIsNeitherClearedNorRepairedByTheBinding() throws Exception {
        try(var h=new TestSonicCdR31aObjectPass.Harness()) {
            watch(h,32);dirtyFree(h,33);h.placement.pool().write(h.placement.pool().state(33).moveByte(ROUTINE,0xEE));
            assertThrows(UnsupportedOperationException.class,h::walk);var c=h.placement.pool().state(33);
            assertEquals(0x25,c.id());assertEquals(0xEE,c.routine());assertEquals(0x40,c.word(TITLE_CARD_START_X));assertEquals(6,h.placement.pool().state(32).routine());
        }
    }
    @Test void cachedComponentsAndTheActualPoolSurviveACompletePlacementRestore() throws Exception {
        try(var h=new TestSonicCdR31aObjectPass.Harness()) {
            var k=field(h.pass,"kama");var s=field(h.pass,"sickle");watch(h,32);var before=h.placement.capture();h.walk();var after=h.placement.capture();h.finish();
            h.placement.restore(before);h.walk();assertEquals(after.pool().slots(),h.placement.capture().pool().slots());
            assertSame(k,field(h.pass,"kama"));assertSame(s,field(h.pass,"sickle"));h.finish();
        }
    }
    @Test void actualSickleFlyAppendsOneB9AtItsReachedNativeSite() throws Exception {
        try(var h=new TestSonicCdR31aObjectPass.Harness()) {
            fly(h);var actor=h.frame.captureActor();var shared=h.frame.captureShared();var display=h.frame.display();var art=h.frame.artState();var clock=h.frame.vIntClock();boolean paused=h.frame.paused();h.walk();
            assertEquals(1,h.cut.sounds().size());assertInstanceOf(SonicCdGameModule.PastKamaSickleFm.class,h.cut.sounds().getFirst());
            assertEquals(actor,h.frame.captureActor());assertEquals(shared,h.frame.captureShared());assertSame(display,h.frame.display());assertSame(art,h.frame.artState());assertEquals(clock,h.frame.vIntClock());assertEquals(paused,h.frame.paused());assertEquals(101,h.placement.pool().state(32).centreX());h.finish();
        }
    }
    @Test void invisibleSickleDoesNotPublishAnInventedSound() throws Exception {
        try(var h=new TestSonicCdR31aObjectPass.Harness()) {fly(h);h.placement.pool().write(h.placement.pool().state(32).moveByte(SPRITE_FLAGS,4));h.walk();assertTrue(h.cut.sounds().isEmpty());h.finish();}
    }
    @Test void soundOwnerRuntimeExceptionRetainsMovementAndOriginalIdentity() throws Exception {soundFault(new IllegalStateException("original B9"),false);}
    @Test void soundOwnerErrorRetainsMovementAndOriginalIdentity() throws Exception {soundFault(new AssertionError("original B9"),false);}
    @Test void distinctVerificationFaultIsSuppressedWithoutRollbackOrThrowableReplacement() throws Exception {soundFault(new AssertionError("original dirty B9"),true);}
    @Test void aSavedBankOnlyViolationCannotHideBehindAnUnchangedPool() throws Exception {
        try(var h=new TestSonicCdR31aObjectPass.Harness()) {
            fly(h);var before=h.placement.capture();var original=new AssertionError("saved-only native sound fault");h.fault=original;
            h.beforeAdopt=()->h.placement.bsetSavedFlag(200,2,CdObjectPlacementRuntime.SavedBit.LOADED);
            assertSame(original,assertThrows(AssertionError.class,h::walk));assertTrue(original.getSuppressed().length>0);
            assertEquals(before.status(),h.placement.status());assertEquals(0x80,h.placement.savedFlag(200,2));assertEquals(101,h.placement.pool().state(32).centreX());
            for(int w=33;w<128;w++)assertEquals(before.pool().slots().get(w-32),h.placement.pool().state(w));
        }
    }
    @Test void aStatusOnlyViolationKeepsTheOriginalFaultAndEveryBankByte() throws Exception {
        try(var h=new TestSonicCdR31aObjectPass.Harness()) {
            h.placement.spawnObjects(0,1);fly(h);var before=h.placement.capture();var original=new IllegalStateException("status-only native sound fault");h.fault=original;
            h.beforeAdopt=h.placement::clearLevelStartCameraValues;
            assertSame(original,assertThrows(IllegalStateException.class,h::walk));assertTrue(original.getSuppressed().length>0);
            assertNotEquals(before.status(),h.placement.status());assertArrayEquals(before.savedFlags().copy(),h.placement.capture().savedFlags().copy());
            for(int w=33;w<128;w++)assertEquals(before.pool().slots().get(w-32),h.placement.pool().state(w));
        }
    }
    @Test void completeFrameEnvironmentViolationIsNotRolledBackOrReplaced() throws Exception {
        try(var h=new TestSonicCdR31aObjectPass.Harness()) {
            fly(h);var original=new AssertionError("original environment fault");h.fault=original;var state=h.frame.state();
            h.beforeAdopt=()->h.frame.applyPreparedObjectSolidPublication(h.frame.prepareObjectPrimaryAngle(h.frame.environment().movement().globals().primaryAngle(),7));
            assertSame(original,assertThrows(AssertionError.class,h::walk));assertTrue(original.getSuppressed().length>0);assertSame(state,h.frame.state());
            assertEquals(7,h.frame.environment().movement().globals().primaryAngle());assertEquals(101,h.placement.pool().state(32).centreX());assertFalse(h.frame.closed());
        }
    }
    @Test void caughtPassReentryDuringSoundDoesNotCloseTheHealthyFrame() throws Exception {
        try(var h=new TestSonicCdR31aObjectPass.Harness()) {fly(h);h.beforeAdopt=()->{assertThrows(IllegalStateException.class,()->h.pass.runWalk(7));assertFalse(h.frame.closed());};h.walk();assertEquals(1,h.cut.sounds().size());h.finish();}
    }
    @Test void retainedPublicationCannotReplayAndThreadOrEqualValuesGrantNoAuthority() throws Exception {
        try(var h=new TestSonicCdR31aObjectPass.Harness()) {fly(h);h.walk();var original=h.lastAdoption;assertNotNull(original);
            assertThrows(IllegalStateException.class,original::validateBefore);assertThrows(IllegalStateException.class,original::applyNative);
            var thrown=new java.util.concurrent.atomic.AtomicReference<Throwable>();var t=new Thread(()->{try{original.applyNative();}catch(Throwable f){thrown.set(f);}});t.start();t.join(5000);assertFalse(t.isAlive());assertInstanceOf(IllegalStateException.class,thrown.get());
            assertEquals(1,h.cut.sounds().size());h.finish();
        }
    }
    @Test void currentTerrainBorrowRefreshesTheCachedKamaWithoutAQueryOrComponentAlgorithmFork() throws Exception {
        try(var h=new TestSonicCdR31aObjectPass.Harness()) {
            // CONSTRUCTED alternate immutable terrain in this pass-only harness; World tests
            // still obtain current terrain exclusively from the real Host/native writer owner.
            var original=field(h.pass,"kama");h.dynamic(32,0x24,2,0);
            h.placement.pool().write(h.placement.pool().state(32).moveWord(X,100).moveWord(Y,1004).moveWord(Y_VELOCITY,0x100).moveByte(SPRITE_FLAGS,4).moveByte(PRIORITY,3).moveByte(WIDTH,16).moveByte(Y_RADIUS,20).moveLong(MAPPINGS,0x2358CA).moveWord(TILE,0x23F2));h.terrainDiverged=true;
            h.walk();assertNotSame(original,field(h.pass,"kama"));assertSame(h.divergedTerrain,field(h.pass,"kamaTerrain"));
            assertEquals(1005,h.placement.pool().state(32).centreY());assertNotNull(h.lastAdoption);assertEquals(0,h.frame.environment().movement().globals().primaryAngle());assertFalse(h.frame.closed());h.finish();
        }
    }
    @Test void selectedMalformedMappingStopsTheActualDeferredQueueWithoutFallback() throws Exception {
        try(var h=new TestSonicCdR31aObjectPass.Harness()) {fly(h);h.placement.pool().write(h.placement.pool().state(32).moveByte(MAP_FRAME,6));h.walk();var before=h.cut.cpuSat();
            assertThrows(UnsupportedOperationException.class,h::finish);assertSame(before,h.cut.cpuSat());assertEquals(6,h.placement.pool().state(32).unsignedByte(MAP_FRAME));
        }
    }
    @Test void clearedParentDeletesTheSickleAndRetainsWrapperDrawTailSemantics() throws Exception {
        try(var h=new TestSonicCdR31aObjectPass.Harness()) {h.dynamic(32,0x25,2,0);h.placement.pool().write(h.placement.pool().state(32).moveWord(X,100).moveWord(RING_ANCHOR_X,100).moveWord(TITLE_CARD_START_Y,0xD840).moveWord(TITLE_CARD_START_X,2));
            h.walk();assertEquals(0,h.placement.pool().state(32).id());h.finish();
        }
    }
    @Test void currentTerrainCacheRefreshRetiresTheOldPrivateReceiverPort() throws Exception {
        try(var h=new TestSonicCdR31aObjectPass.Harness()) {
            var receiver=field(h.pass,"kama");var oldPort=(CdCczEnemyPorts)superField(receiver,"ports");
            h.dynamic(32,0x24,2,0);h.placement.pool().write(h.placement.pool().state(32).moveWord(X,100).moveWord(Y,1004).moveByte(SPRITE_FLAGS,4).moveByte(PRIORITY,3).moveByte(WIDTH,16).moveByte(Y_RADIUS,20).moveLong(MAPPINGS,0x2358CA).moveWord(TILE,0x23F2));h.terrainDiverged=true;
            h.walk();assertNotSame(receiver,field(h.pass,"kama"));assertThrows(IllegalStateException.class,()->oldPort.global(Global.GOOD_FUTURE));h.finish();
        }
    }
    @Test void originalSoundCallCannotReenterEscapeThreadOrSurviveItsPhysicalVisit() throws Exception {
        try(var h=new TestSonicCdR31aObjectPass.Harness()) {
            fly(h);var receiver=field(h.pass,"sickle");var port=(CdCczEnemyPorts)superField(receiver,"ports");
            var retained=new java.util.concurrent.atomic.AtomicReference<CdCczEnemyPorts.SoundCall>();
            h.beforeAdopt=()->{try {
                // Draw also publishes through this hook after the original sound scope retires.
                if(h.lastAdoption.next().sounds().size()==h.cut.sounds().size())return;
                var original=(CdCczEnemyPorts.SoundCall)superField(receiver,"currentSound");assertNotNull(original);
                assertNull(retained.get(),"one original Sickle FM publication");retained.set(original);assertTrue(original.isCurrent());
                assertThrows(IllegalStateException.class,()->port.sound(original));
                var failed=new java.util.concurrent.atomic.AtomicReference<Throwable>();var t=new Thread(()->{try{port.sound(original);}catch(Throwable f){failed.set(f);}});t.start();t.join(5000);assertFalse(t.isAlive());assertInstanceOf(IllegalStateException.class,failed.get());assertFalse(h.frame.closed());
            }catch(Exception failure){throw new AssertionError(failure);}};
            h.walk();assertNotNull(retained.get());assertFalse(retained.get().isCurrent());assertThrows(IllegalStateException.class,()->port.sound(retained.get()));assertEquals(1,h.cut.sounds().size());h.finish();
        }
    }
    @Test void physicalBindingIsPrivateAndDoesNotAddAPublicWorldLifecycleEntry() {
        assertFalse(Modifier.isPublic(SonicCdR31aObjectPass.class.getModifiers()));
        assertTrue(Arrays.stream(SonicCdR31aObjectPass.class.getDeclaredMethods()).filter(m->m.getName().startsWith("enemy")||m.getName().equals("runKamaSickle")).allMatch(m->Modifier.isPrivate(m.getModifiers())));
    }
    static void watch(TestSonicCdR31aObjectPass.Harness h,int w) {
        h.dynamic(w,0x24,4,0);h.placement.pool().write(h.placement.pool().state(w).moveByte(SPRITE_FLAGS,0x84).moveByte(PRIORITY,3).moveByte(WIDTH,16).moveByte(Y_RADIUS,20)
            .moveWord(X,100).moveWord(Y,1004).moveLong(MAPPINGS,0x2358CA).moveWord(TILE,0x23F2).moveByte(ANIMATION,2).moveByte(PREVIOUS_ANIMATION,2).moveByte(ANIMATION_TIME,5));
    }
    static void fly(TestSonicCdR31aObjectPass.Harness h) {
        h.dynamic(32,0x25,4,0);h.placement.pool().write(h.placement.pool().state(32).moveByte(SPRITE_FLAGS,0x84).moveByte(PRIORITY,1).moveByte(WIDTH,4).moveByte(Y_RADIUS,4)
            .moveWord(X,100).moveWord(Y,100).moveWord(RING_ANCHOR_X,100).moveWord(X_VELOCITY,0x100).moveWord(Y_VELOCITY,0x200).moveWord(TITLE_CARD_DEST_X,0x40)
            .moveByte(RING_MEMBER,5).moveLong(MAPPINGS,0x20D3DE).moveWord(TILE,0x23F2).moveByte(MAP_FRAME,0).moveByte(ANIMATION,0).moveByte(PREVIOUS_ANIMATION,0).moveByte(ANIMATION_TIME,5));
    }
    private static void dirtyFree(TestSonicCdR31aObjectPass.Harness h,int w) {
        // Existing storage aliases only. No broad raw-state constructor or new pool owner.
        var d=h.placement.pool().state(w);
        for(var f:CdObjectState.LongField.values())d=d.moveLong(f,0xA5A5A5A5);
        for(var f:CdObjectState.WordField.values())d=d.moveWord(f,0xA5A5);
        for(var f:CdObjectState.ByteField.values())d=d.moveByte(f,0xA5);
        h.placement.pool().write(d.moveByte(ID,0).moveByte(ROUTINE,0).moveByte(MAP_FRAME,0));
    }
    private static void childInit(TestSonicCdR31aObjectPass.Harness h,int w,CdObjectState dirty,int x,int y,int delay,int parent) {
        byte[] b=dirty.copyBytes();b[0]=0x25;word(b,0x30,0xD000+parent*64);word(b,0x2C,delay);b[0x28]=0;b[0x2E]=(byte)0x84;word(b,8,x);word(b,12,y);
        b[1]=5;b[0x18]=1;b[0x16]=4;b[0x17]=4;word(b,2,0x23F2);longWord(b,4,0x20D3DE);word(b,0x32,x);b[0x22]|=1;word(b,0x10,0xFD00);b[0x24]=2;
        assertArrayEquals(b,h.placement.pool().state(w).copyBytes(),"literal native spawn+Init; no countdown/gravity/animation on Init");
    }
    private static void nonwrites(CdObjectPlacementRuntime.Snapshot before,TestSonicCdR31aObjectPass.Harness h,Set<Integer> changed) {var after=h.placement.capture();assertEquals(before.status(),after.status());assertArrayEquals(before.savedFlags().copy(),after.savedFlags().copy());for(int w=32;w<128;w++)if(!changed.contains(w))assertEquals(before.pool().slots().get(w-32),h.placement.pool().state(w));}
    private static void soundFault(Throwable original,boolean dirty) throws Exception {
        try(var h=new TestSonicCdR31aObjectPass.Harness()) {fly(h);var before=h.placement.capture();h.fault=original;
            if(dirty)h.beforeAdopt=()->h.placement.pool().write(h.placement.pool().state(127).moveByte(RAMP_TIMER,0x77));
            Throwable caught=original instanceof Error?assertThrows(Error.class,h::walk):assertThrows(RuntimeException.class,h::walk);assertSame(original,caught);
            assertEquals(101,h.placement.pool().state(32).centreX());assertEquals(102,h.placement.pool().state(32).centreY());assertEquals(0x240,h.placement.pool().state(32).word(Y_VELOCITY));
            assertEquals(5,h.placement.pool().state(32).unsignedByte(RING_MEMBER));assertTrue(h.cut.sounds().isEmpty());assertFalse(h.frame.closed());
            if(dirty){assertTrue(original.getSuppressed().length>0);assertEquals(0x77,h.placement.pool().state(127).unsignedByte(RAMP_TIMER));}else nonwrites(before,h,Set.of(32));
        }
    }
    private static void mapping(CdCczEnemyAssets a,Mapping m,int address,int bytes,int frames,String hash){var v=a.mapping(m);assertEquals(address,v.address());assertEquals(bytes,v.bytes().length());assertEquals(frames,v.table().frameCount());assertEquals(hash,sha(v.bytes()));}
    private static String sha(CdAssetBytes bytes){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes.copy()));}catch(java.security.NoSuchAlgorithmException e){throw new AssertionError(e);}}
    private static Object superField(Object value,String name)throws Exception {var f=value.getClass().getSuperclass().getDeclaredField(name);f.setAccessible(true);return f.get(value);}
    private static Object field(Object value,String name)throws Exception {var f=value.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(value);}
    private static void word(byte[] b,int at,int value){b[at]=(byte)(value>>>8);b[at+1]=(byte)value;}
    private static void longWord(byte[] b,int at,int value){word(b,at,value>>>16);word(b,at+2,value);}
}
