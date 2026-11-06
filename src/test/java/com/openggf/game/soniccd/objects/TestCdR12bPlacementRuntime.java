package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.media.CdTimeWarpPrograms;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.lang.reflect.Modifier;
import java.nio.ReadOnlyBufferException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static com.openggf.game.soniccd.objects.CdObjectPlacementRuntime.SavedBit.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * N1ecc5f3a Object Spawner.asm:42-319, actual original R12B source plus explicit component controls.
 * ALL JAVA UNRUN. No Frame, Visit, object body, current art, timing, route or natural-spawn claim.
 * Raw bank and dirty-slot preimages are caller-flow fixtures, never destination hydration.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCdR12bPlacementRuntime {
    @Test void explicitSavedBankIsValidatedBeforeTheAuthenticatedR12bAssetGate() {
        assertThrows(NullPointerException.class,()->CdObjectPlacementRuntime.fromAssets((CdR12bTerrainAssets)null,null));
        for(int n:new int[]{0,1,2,0x2FC,0x2FD,0x2FF,0x300,0x400}){
            var bank=CdAssetBytes.copyOf(new byte[n]);assertThrows(IllegalArgumentException.class,()->CdObjectPlacementRuntime.fromAssets((CdR12bTerrainAssets)null,bank));
        }
        assertThrows(NullPointerException.class,()->CdObjectPlacementRuntime.fromAssets((CdR12bTerrainAssets)null,CdAssetBytes.copyOf(rawBank())));
    }

    @Test void onlyOneExplicitFactoryAndTheExistingClosedSnapshotTypeAreExtended() throws Exception {
        var factory=CdObjectPlacementRuntime.class.getMethod("fromAssets",CdR12bTerrainAssets.class,CdAssetBytes.class);
        assertTrue(Modifier.isPublic(factory.getModifiers()));assertTrue(Modifier.isStatic(factory.getModifiers()));assertEquals(CdObjectPlacementRuntime.class,factory.getReturnType());
        assertThrows(NoSuchMethodException.class,()->CdObjectPlacementRuntime.class.getMethod("fromAssets",CdR12bTerrainAssets.class));
        assertEquals(List.of(1,2,3,4,5),Arrays.stream(CdObjectPlacementRuntime.class.getDeclaredConstructors()).map(c->c.getParameterCount()).sorted().toList());
        var snapshot=CdObjectPlacementRuntime.Snapshot.class;assertTrue(Modifier.isFinal(snapshot.getModifiers()));assertFalse(snapshot.isRecord());
        assertEquals(1,snapshot.getDeclaredConstructors().length);assertTrue(Modifier.isPrivate(snapshot.getDeclaredConstructors()[0].getModifiers()));
        assertEquals(9,snapshot.getDeclaredConstructors()[0].getParameterCount());assertEquals(0,snapshot.getConstructors().length);
        assertEquals(List.of("pool/0","savedFlags/0","status/0"),Arrays.stream(snapshot.getDeclaredMethods()).filter(m->Modifier.isPublic(m.getModifiers())).map(m->m.getName()+"/"+m.getParameterCount()).sorted().toList());
        for(var type:List.of(CdObjectPlacementRuntime.class,snapshot)){
            var field=type.getDeclaredField("act2PastIdentity");assertTrue(Modifier.isPrivate(field.getModifiers()));assertTrue(Modifier.isFinal(field.getModifiers()));assertEquals(Optional.class,field.getType());
        }
    }

    @Test void actualClosedOriginalFactoryKeepsR12bIdentityExactLayoutEveryRawBankByteAndAllNinetySixZeroSlots() throws Exception {
        var a=retail().r12b();var input=CdAssetBytes.copyOf(rawBank());var p=CdObjectPlacementRuntime.fromAssets(a,input);
        assertEquals(CdReleaseVariant.USA,a.identity().variant());assertEquals(CdStageKey.parse("R12B"),a.identity().stage());assertEquals(CdNativeFile.R12B,a.identity().file());
        assertEquals(CdMmdImage.Domain.ORIGINAL_WORD_RAM,a.identity().domain());assertEquals("14de4fdb4979125a8c0c197c926133353d20a2ca5b2d57db2e363c4e94460a64",a.identity().mmdSha256());
        assertSame(a.placements(),p.placements());assertEquals(488,p.placements().records().size());assertEquals(253,p.placements().records().stream().filter(CdObjectPlacements.Placement::hasSavedFlag).count());
        assertEquals(0x2076F8,a.profile().range(CdR12bProfile.Asset.OBJECT_LAYOUTS).nativeAddress());
        assertEquals("1e32466be6a13b758700341964febb20d5deee1bb50a915e836a56aa0f3558c2",sha(a.view(CdR12bProfile.Asset.OBJECT_LAYOUTS).copy()));
        assertEquals(new CdObjectPlacementRuntime.Status(0,0,0,0,0xC7,0x9E,-1,-1),p.status());assertArrayEquals(rawBank(),p.capture().savedFlags().copy());
        assertTrue(p.sourceIdentity().isEmpty());assertTrue(p.hasAuthenticatedSource());assertTrue(p.sameAct2PastSource(a));
        assertEquals(8,p.placements().backwardSentinel().orElseThrow().length());assertEquals(8,p.placements().forwardSentinel().length());assertEquals(6,p.placements().nullList().orElseThrow().length());
        for(int whole=32;whole<128;whole++)assertArrayEquals(new byte[64],p.pool().state(whole).copyBytes(),"whole"+whole);
        var capture=p.capture();byte[] detached=capture.savedFlags().copy();detached[0]=0;
        assertEquals(0xC7,capture.savedFlags().unsignedByte(0));assertThrows(UnsupportedOperationException.class,()->capture.pool().slots().clear());
        p.bsetSavedFlag(1,0,LOADED);assertArrayEquals(rawBank(),input.copy());assertArrayEquals(rawBank(),capture.savedFlags().copy());
        assertThrows(ReadOnlyBufferException.class,()->input.buffer().put(0,(byte)0));assertThrows(UnsupportedOperationException.class,()->p.placements().records().clear());
    }

    @Test void exactRetainedR12bLayoutIsRequiredEvenWhenIndependentAggregatesHaveEqualIdentityAndBytes() throws Exception {
        var f=retail();var p=placement();var before=p.capture();
        assertEquals(f.r12b().identity(),f.independentR12b().identity());assertNotSame(f.r12b().placements(),f.independentR12b().placements());
        assertEquals(f.r12b().placements().records(),f.independentR12b().placements().records());
        assertTrue(p.sameAct2PastSource(f.r12b()));assertFalse(p.sameAct2PastSource(f.independentR12b()));
        assertThrows(NullPointerException.class,()->p.sameAct2PastSource(null));assertCut(before,p);
        var raw=new CdObjectPlacementRuntime(f.r12b().placements());assertFalse(raw.hasAuthenticatedSource());assertFalse(raw.sameAct2PastSource(f.r12b()));
    }

    @Test void actualPastInitialRowsChangeOnlyNativePlacementFieldsAcrossAllNinetySixDirtyImages() throws Exception {
        var p=placement();dirtyFree(p);var before=p.capture();p.spawnObjects(0,0x80);
        assertSlots(before,p,past());assertEquals(new CdObjectPlacementRuntime.Status(2,0,7,0,4,1,0,0),p.status());
        byte[] bank=scanBank();bank[0]=4;bank[1]=1;for(int id:new int[]{1,2,3})bank[2+3*id]|=(byte)0x80;
        assertArrayEquals(bank,p.capture().savedFlags().copy());assertEquals(0x5C,p.pool().state(32).routine(),"No body initializes the dirty free slot");
        assertThrows(IndexOutOfBoundsException.class,()->p.pool().state(31));assertThrows(IndexOutOfBoundsException.class,()->p.pool().state(128));
    }

    @Test void actualCommonLayoutPeriodColumnsAndMarkerMaskKeepTheirSeparateLoadedBits() throws Exception {
        int[][] periods={{0,1,2,3,6},{0,1,2,3,5,6},{0,1,2,3,4,5,6}};
        int[][] records={{9,0x18D,0x0A,6,0,-1},{0x24,0x18B,0x10,0x11,0,1},{0xD0,0x138,0x10,0x12,0,2},
                {0x1BB,0xAB,0x24,0,0,-1},{0x208,0x1ED,0x0A,4,0,-1},{0x240,0x80,0x24,0,0,-1},{0x240,0x18B,0x10,0x52,0,3}};
        for(int period=0;period<3;period++){
            var p=placement();var before=p.capture();p.spawnObjects(0,0x80|period);int[][] expected=new int[periods[period].length][];
            for(int i=0;i<expected.length;i++)expected[i]=records[periods[period][i]];assertSlots(before,p,expected);
            byte[] bank=scanBank();bank[0]=4;bank[1]=1;for(int id:new int[]{1,2,3})bank[2+3*id+period]|=(byte)0x80;
            assertArrayEquals(bank,p.capture().savedFlags().copy());assertEquals(new CdObjectPlacementRuntime.Status(2,0,7,0,4,1,0,0),p.status());
        }
        assertEquals(281,retail().r12b().placements().forPeriod(com.openggf.game.soniccd.CdPeriod.PAST).size());
        assertEquals(279,retail().r12b().placements().forPeriod(com.openggf.game.soniccd.CdPeriod.PRESENT).size());
        assertEquals(282,retail().r12b().placements().forPeriod(com.openggf.game.soniccd.CdPeriod.BAD_FUTURE).size());
    }

    @Test void alreadyLoadedPastParentIsSkippedWhilePresentColumnAndMemberBitsRemainIndependent() throws Exception {
        byte[] bank=scanBank();bank[5]=(byte)0x85;bank[9]=(byte)0xD2; // saved1/Past and saved2/Present.
        var p=CdObjectPlacementRuntime.fromAssets(retail().r12b(),CdAssetBytes.copyOf(bank));var before=p.capture();p.spawnObjects(0,0);
        int[][] all=past();assertSlots(before,p,new int[][]{all[0],all[2],all[3],all[4]});
        bank[0]=4;bank[1]=1;bank[8]|=(byte)0x80;bank[11]|=(byte)0x80;assertArrayEquals(bank,p.capture().savedFlags().copy());
        assertEquals(0x85,p.savedFlag(1,0));assertEquals(0xD2,p.savedFlag(2,1));assertEquals(4,p.status().rightSavedId());
    }

    @Test void actualForwardBackwardAndSameChunkReplayPreserveAllCursorsBankAndNinetySixImages() throws Exception {
        var p=placement();dirtyFree(p);var empty=p.capture();p.spawnObjects(0,0);var initial=p.capture();
        p.spawnObjects(0x180,0);var forward=p.capture();assertEquals(new CdObjectPlacementRuntime.Status(2,0x180,17,3,8,3,0,0),p.status());
        int[][] later={{0x2D0,0xC0,0x19,9,0,5},{0x350,0x90,0x10,0x22,0,6},{0x35D,0x4234,0x28,0,0,-1},{0x3B9,0x4224,0x0A,4,0,-1},{0x3CE,0x222,0x1B,0,0,-1}};
        var all=new int[11][];System.arraycopy(past(),0,all,0,5);System.arraycopy(later,0,all,5,5);all[10]=past()[0];
        assertSlots(empty,p,Arrays.copyOf(all,10));byte[] bank=scanBank();bank[0]=8;bank[1]=3;for(int id:new int[]{1,2,3,5,6})bank[2+3*id]|=(byte)0x80;assertArrayEquals(bank,p.capture().savedFlags().copy());
        p.spawnObjects(0x80,0);var backward=p.capture();assertEquals(new CdObjectPlacementRuntime.Status(2,0x80,10,0,6,1,0,0),p.status());assertSlots(empty,p,all);
        bank[0]=6;bank[1]=1;assertArrayEquals(bank,p.capture().savedFlags().copy());
        p.pool().objNull(32);p.bsetSavedFlag(250,2,MEMBER_6);p.restore(initial);assertCut(initial,p);
        p.spawnObjects(0x180,0x80);assertCut(forward,p);p.spawnObjects(0x80,0);assertCut(backward,p);
        p.restore(forward);p.spawnObjects(0x1FF,0);assertCut(forward,p);assertTrue(p.sameAct2PastSource(retail().r12b()));
    }

    @Test void fullNinetySixSlotFailureRetainsEveryImageAndTheSameChunkCannotRetry() throws Exception {
        var p=placement();dirtyFree(p);for(int whole=32;whole<128;whole++)p.pool().write(p.pool().state(whole).moveByte(ID,0x7F));var full=p.capture();
        p.spawnObjects(0,0);assertEquals(new CdObjectPlacementRuntime.Status(2,0,0,0,1,1,0,0),p.status());
        assertEquals(full.pool().slots(),p.pool().capture().slots());byte[] bank=scanBank();bank[0]=bank[1]=1;assertArrayEquals(bank,p.capture().savedFlags().copy());
        p.pool().deleteObject(32);p.spawnObjects(0x7F,0);assertArrayEquals(new byte[64],p.pool().state(32).copyBytes());assertEquals(0,p.status().rightRecordIndex());
        p.spawnObjects(0x80,0);assertEquals(new CdObjectPlacementRuntime.Status(2,0x80,1,0,1,1,0,0),p.status());
        assertArrayEquals(literal(new CdObjectPool().state(32),past()[0]).copyBytes(),p.pool().state(32).copyBytes());
        for(int whole=33;whole<128;whole++)assertEquals(full.pool().slots().get(whole-32),p.pool().state(whole));
        assertArrayEquals(bank,p.capture().savedFlags().copy(),"Tentative saved1 LOADED bit from the next failed row is undone");
    }

    @Test void rawSavedAliasesAddressOnlyOnePeriodByteAndKeepHeaderAndFiniteTailBits() throws Exception {
        var p=CdObjectPlacementRuntime.fromAssets(retail().r12b(),CdAssetBytes.copyOf(rawBank()));byte[] expected=rawBank();
        for(var bit:CdObjectPlacementRuntime.SavedBit.values()){
            p.bsetSavedFlag(1,0,bit);expected[5]|=(byte)(1<<bit.ordinal());assertArrayEquals(expected,p.capture().savedFlags().copy());
            p.bclrSavedFlag(1,0,bit);expected[5]&=(byte)~(1<<bit.ordinal());assertArrayEquals(expected,p.capture().savedFlags().copy());
        }
        p.bsetSavedFlag(253,2,LOADED);expected[763]|=(byte)0x80;assertArrayEquals(expected,p.capture().savedFlags().copy());
        assertEquals(expected[764]&255,p.capture().savedFlags().unsignedByte(764));assertEquals(expected[765]&255,p.capture().savedFlags().unsignedByte(765));
        assertEquals(expected[764]&255,p.savedFlag(254,0));assertEquals(expected[765]&255,p.savedFlag(254,1));
        var before=p.capture();assertThrows(IndexOutOfBoundsException.class,()->p.savedFlag(254,2));assertCut(before,p);
    }

    @Test void snapshotsRejectForeignRuntimeSameSourceConstructedAndEveryOldSourceWithoutAdoption() throws Exception {
        var f=retail();var p=placement();p.spawnObjects(0,0);var before=p.capture();
        var same=CdObjectPlacementRuntime.fromAssets(f.r12b(),before.savedFlags());var independent=CdObjectPlacementRuntime.fromAssets(f.independentR12b(),before.savedFlags());
        var raw=new CdObjectPlacementRuntime(f.r12b().placements());
        for(var foreign:List.of(same.capture(),independent.capture(),raw.capture(),CdObjectPlacementRuntime.fromAssets(f.r11a()).capture(),CdObjectPlacementRuntime.fromAssets(f.r11b(),before.savedFlags()).capture(),CdObjectPlacementRuntime.fromAssets(f.r12a(),before.savedFlags()).capture())){
            assertThrows(IllegalArgumentException.class,()->p.validateRestore(foreign));assertThrows(IllegalArgumentException.class,()->p.restore(foreign));assertCut(before,p);
        }
        assertThrows(NullPointerException.class,()->p.validateRestore(null));assertThrows(NullPointerException.class,()->p.restore(null));assertCut(before,p);
    }

    @Test void snapshotRelationChecksR12bIdentityExactLayoutAndPoolBeforeRestoringAnyPartition() throws Exception {
        var f=retail();var p=placement();p.spawnObjects(0,0);var before=p.capture();var id=f.r12b().identity();
        var foreign=new CdR12bTerrainAssets.Identity(id.variant(),id.stage(),id.file(),id.domain(),id.mmdSha256(),"0".repeat(64));
        for(var malformed:List.of(replaceSnapshot(before,"act2PastIdentity",Optional.empty()),replaceSnapshot(before,"act2PastIdentity",Optional.of(foreign)),
                replaceSnapshot(before,"placements",f.independentR12b().placements()),replaceSnapshot(before,"pool",new CdObjectPool().capture()))){
            assertThrows(IllegalArgumentException.class,()->p.validateRestore(malformed));assertThrows(IllegalArgumentException.class,()->p.restore(malformed));assertCut(before,p);
        }
        p.restore(before);assertCut(before,p);assertTrue(p.sameAct2PastSource(f.r12b()));
    }

    @Test void newAuthenticatedPlacementCannotEnterOldOrPureBodyFactoriesAndIdentityMetadataCannotLendSource() throws Exception {
        var f=retail();var p=placement();var before=p.capture();
        assertThrows(IllegalArgumentException.class,()->new CdRing(p,TestCdRing.data()));
        assertThrows(IllegalArgumentException.class,()->CdRing.fromAssets(p,f.r11a().openingObjects()));
        assertThrows(IllegalArgumentException.class,()->CdRing.fromAssets(p,f.r11b()));assertThrows(IllegalArgumentException.class,()->CdRing.fromAssets(p,f.r12a().objects()));assertCut(before,p);
        assertFalse(p.sameBSource(f.r11b()));assertFalse(p.sameAct2Source(f.r12a().objects()));
        var id=f.r12b().identity();var foreign=new CdR12bTerrainAssets.Identity(id.variant(),id.stage(),id.file(),id.domain(),id.mmdSha256(),"0".repeat(64));
        var c=CdObjectPlacementRuntime.class.getDeclaredConstructor(CdObjectPlacements.class,Optional.class,Optional.class,Optional.class,Optional.class);c.setAccessible(true);
        // Reflection manufactures only a rejection control; never an accepted factory/capture/Frame.
        var forged=c.newInstance(f.r12b().placements(),Optional.empty(),Optional.empty(),Optional.empty(),Optional.of(foreign));
        assertFalse(forged.sameAct2PastSource(f.r12b()));assertThrows(IllegalArgumentException.class,()->new CdRing(forged,TestCdRing.data()));
        var pure=new CdObjectPlacementRuntime(f.r12b().placements());assertDoesNotThrow(()->new CdRing(pure,TestCdRing.data()));assertFalse(pure.sameAct2PastSource(f.r12b()));
    }

    @Test void oldConstructorsFactoriesAndPureRestoreKeepTheirOriginalSourceRelations() throws Exception {
        var f=retail();var a=CdObjectPlacementRuntime.fromAssets(f.r11a());var b=CdObjectPlacementRuntime.fromAssets(f.r11b(),CdAssetBytes.copyOf(rawBank()));var c=CdObjectPlacementRuntime.fromAssets(f.r12a(),CdAssetBytes.copyOf(rawBank()));
        assertTrue(a.sourceIdentity().isPresent());assertTrue(b.sourceIdentity().isEmpty());assertTrue(c.sourceIdentity().isEmpty());assertTrue(b.sameBSource(f.r11b()));assertTrue(c.sameAct2Source(f.r12a().objects()));
        for(var p:List.of(a,b,c,new CdObjectPlacementRuntime(f.r12b().placements()))){
            assertFalse(p.sameAct2PastSource(f.r12b()));var before=p.capture();p.bsetSavedFlag(1,0,MEMBER_1);p.restore(before);assertCut(before,p);
        }
        for(int n:new int[]{2,3,4}){
            var ctor=Arrays.stream(CdObjectPlacementRuntime.class.getDeclaredConstructors()).filter(x->x.getParameterCount()==n).findFirst().orElseThrow();assertTrue(Modifier.isPrivate(ctor.getModifiers()));
        }
    }

    @Test void invalidNativeWidthsPeriodsAndSavedAddressesLeaveTheEntireActualCutUnchanged() throws Exception {
        var p=placement();var before=p.capture();
        for(int[] input:new int[][]{{-1,0},{65536,0},{0,-1},{0,256},{0,3},{0,0x83}})assertThrows(IllegalArgumentException.class,()->p.spawnObjects(input[0],input[1]));
        assertThrows(IllegalArgumentException.class,()->p.bsetSavedFlag(256,0,LOADED));assertThrows(IllegalArgumentException.class,()->p.bsetSavedFlag(1,3,LOADED));
        assertThrows(NullPointerException.class,()->p.bsetSavedFlag(1,0,null));assertCut(before,p);
    }

    private static int[][] past(){return new int[][]{{9,0x18D,0x0A,6,0,-1},{0x24,0x18B,0x10,0x11,0,1},{0xD0,0x138,0x10,0x12,0,2},{0x1BB,0xAB,0x24,0,0,-1},{0x240,0x18B,0x10,0x52,0,3}};}
    private static CdObjectState literal(CdObjectState s,int[] r){int flips=((r[1]>>>14)&3);s=s.moveWord(X,r[0]).moveWord(Y,r[1]&0xFFF).moveByte(SPRITE_FLAGS,flips).moveByte(FLAGS,flips).moveByte(ID,r[2]).moveByte(SUBTYPE,r[3]).moveByte(SUBTYPE2,r[4]);return r[5]<0?s:s.moveByte(SAVED_FLAGS_ID,r[5]);}
    private static void assertSlots(CdObjectPlacementRuntime.Snapshot before,CdObjectPlacementRuntime p,int[][] rows){for(int i=0;i<96;i++){var expected=before.pool().slots().get(i);if(i<rows.length)expected=literal(expected,rows[i]);assertArrayEquals(expected.copyBytes(),p.pool().state(32+i).copyBytes(),"all64 bytes whole"+(32+i));}}
    private static byte[] rawBank(){byte[] a=new byte[766];for(int i=0;i<a.length;i++)a[i]=(byte)(0x5B+i*0x35);a[0]=(byte)0xC7;a[1]=(byte)0x9E;return a;}
    private static byte[] scanBank(){byte[] a=rawBank();for(int i=2;i<a.length;i++)a[i]&=0x7F;return a;}
    /** Explicit all64-byte dirty preimages with the SAME real pool owner; no source/visit admission is manufactured. */
    private static void dirtyFree(CdObjectPlacementRuntime p)throws Exception{var field=CdObjectState.class.getDeclaredField("owner");field.setAccessible(true);var ctor=CdObjectState.class.getDeclaredConstructor(Object.class,CdObjectState.Identity.class,byte[].class);ctor.setAccessible(true);for(int whole=32;whole<128;whole++){var seed=p.pool().state(whole);byte[] raw=new byte[64];for(int i=0;i<64;i++)raw[i]=(byte)(0x5B^whole*29^i*53);raw[0]=0;raw[0x24]=0x5C;p.pool().write(ctor.newInstance(field.get(seed),seed.identity(),raw));}}
    private static void assertCut(CdObjectPlacementRuntime.Snapshot expected,CdObjectPlacementRuntime p){assertEquals(expected.status(),p.status());assertEquals(expected.pool().slots(),p.pool().capture().slots());assertArrayEquals(expected.savedFlags().copy(),p.capture().savedFlags().copy());}
    private static CdObjectPlacementRuntime.Snapshot replaceSnapshot(CdObjectPlacementRuntime.Snapshot base,String target,Object value)throws Exception{String[] fields={"owner","placements","identity","pastIdentity","act2Identity","act2PastIdentity","status","pool","savedFlags"};var c=CdObjectPlacementRuntime.Snapshot.class.getDeclaredConstructors()[0];assertEquals(9,c.getParameterCount());Object[] args=new Object[9];for(int i=0;i<args.length;i++){var f=CdObjectPlacementRuntime.Snapshot.class.getDeclaredField(fields[i]);f.setAccessible(true);args[i]=fields[i].equals(target)?value:f.get(base);}c.setAccessible(true);return(CdObjectPlacementRuntime.Snapshot)c.newInstance(args);}
    private CdObjectPlacementRuntime placement()throws Exception{return CdObjectPlacementRuntime.fromAssets(retail().r12b(),CdAssetBytes.copyOf(scanBank()));}
    private static String sha(byte[] a)throws Exception{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(a));}
    private record Retail(CdR12bTerrainAssets r12b,CdR12bTerrainAssets independentR12b,CdR11aAssets r11a,CdR11bAssets r11b,CdR12aTerrainAssets r12a){}
    private Retail retained;
    private Retail retail()throws Exception{if(retained!=null)return retained;String root=System.getProperty("soniccd.media.root");assertNotNull(root,"Configure actual original CD media root");assertFalse(root.isBlank());Path cue;try(var files=Files.list(Path.of(root).resolve("USA"))){var cues=files.filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();assertEquals(1,cues.size());cue=cues.getFirst();}try(var source=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())){retained=new Retail(CdR12bTerrainAssets.fromPrograms(source),CdR12bTerrainAssets.fromPrograms(source),CdR11aAssetLoader.load(source,CdReleaseVariant.USA,CdStageKey.parse("R11A")),CdR11bAssets.fromPrograms(CdTimeWarpPrograms.fromPrograms(source)),CdR12aTerrainAssets.fromPrograms(source));}return retained;}
}
