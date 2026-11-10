package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.media.*;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.ReadOnlyBufferException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** SOURCE-authored before production. Actual-media values are independent of shared decoding.
 * This is immutable Player DATA/caller coverage, never a Frame/World/Ready grant. All Java UNRUN. */
class TestCdOriginalR11SharedPlayerData {
    private static final CdPpzDataLayout.Programme R11=CdPpzDataLayout.Programme.R11A;
    // N1ecc5f3a actual R11A: Ani_Sonic, live MiniAnims, ChunkList, CalcSine and CalcAngle.
    private static final int[] OFFSETS={0x599A,0x594A,0x4710,0x872,0xB58};
    private static final int[] LENGTHS={578,60,6,640,257};
    private static final String[] HASHES={
        "96c145e86b5f841f36dbfca9361c7139df4ff5705423566836d361ace6098af9",
        "adcb220955503a487e05e9d1310f244e7c84e50c26c0fbf5c2a64e968f2f4c1a",
        "203fe6a5ab55693db5a4daca78171984996c54a85119395dbd59d5fae00c7218",
        "bbde7b84eb90b56a2425842a67a0ff0316b3f3b0ff49b956f9b86c1267d75bb9",
        "a981a16e050ef47017e44f23c991256a478755f1b415667f56e6788d1b655295"};
    private record Fixture(CdDiscHeader header,CdObservedMediaIdentity identity,CdProgramCatalogue catalogue,
                           CdMmdImage image,CdMmdImage foreign) { }
    private static Fixture retained;
    private static synchronized Fixture actual()throws Exception {
        if(retained==null)try(var media=TestCdCczProgrammeData.open()) {
            retained=new Fixture(media.header(),media.identity(),media.catalogue().orElseThrow(),
                    media.mmd(CdNativeFile.R11A),media.mmd(CdNativeFile.R12A));
        }
        return retained;
    }
    private static CdProgramAssets source(Fixture f,CdMmdImage image)throws IOException {
        var actual=mock(CdProgramAssets.class);
        when(actual.header()).thenReturn(f.header());when(actual.identity()).thenReturn(f.identity());
        when(actual.catalogue()).thenReturn(Optional.of(f.catalogue()));when(actual.mmd(CdNativeFile.R11A)).thenReturn(image);
        return actual;
    }
    private static CdProgrammeReader reader(CdMmdImage image)throws Exception {
        return CdProgrammeReader.open(source(actual(),image),R11.payload().stage());
    }
    private static CdR11aPlayerAssets legacy(CdMmdImage image,CdSonicVisualAssets sonic) {
        return CdR11aPlayerAssets.decode(image.readFile(OFFSETS[0],LENGTHS[0]),image.readFile(OFFSETS[1],LENGTHS[1]),
                image.readFile(OFFSETS[2],LENGTHS[2]),image.readFile(0x29F2,4),image.readFile(OFFSETS[3],LENGTHS[3]),
                image.readFile(OFFSETS[4],LENGTHS[4]),sonic);
    }
    private static void valuesEqual(CdR11aPlayerAssets expected,CdR11aPlayerAssets actual) {
        assertSame(expected.sonic(),actual.sonic());
        assertArrayEquals(expected.animationTable().copy(),actual.animationTable().copy());
        assertArrayEquals(expected.miniAnimations().copy(),actual.miniAnimations().copy());
        assertArrayEquals(expected.rampChunks().copy(),actual.rampChunks().copy());
        assertArrayEquals(expected.specialChunks().copy(),actual.specialChunks().copy());
        assertArrayEquals(expected.sineTable().copy(),actual.sineTable().copy());
        assertArrayEquals(expected.arcTanTable().copy(),actual.arcTanTable().copy());
        for(int i=0;i<60;i++){assertEquals(expected.animation(i).relativeOffset(),actual.animation(i).relativeOffset());assertArrayEquals(expected.animation(i).bytes().copy(),actual.animation(i).bytes().copy());}
    }
    @Test void onlyCanonicalOriginalR11GainsPpzPlayerDataWithoutLoadingOrWaterAdmission() {
        var d=CdProgrammeDefinition.select(R11.payload().stage());var p=d.player().orElseThrow();
        assertTrue(d.dataOnly());assertTrue(d.r3().isEmpty());assertTrue(d.tt().isEmpty());
        assertEquals(CdR11aPlayerAssets.SourceGraph.PPZ,p.graph());assertEquals(0,p.ordinaryZoneActWord());assertEquals(1,p.ordinaryPeriod());
        assertSame(d.visual(),p.visual());assertEquals(CdSonicVisualAssets.SourceFormat.FRAMES_198_WITH_EVEN_BYTE,p.visual().format());assertEquals(198,p.frames());
        assertEquals(Set.of(CdProgrammeReader.Role.ANI,CdProgrammeReader.Role.MINI,CdProgrammeReader.Role.RAMP,
                CdProgrammeReader.Role.SPECIAL,CdProgrammeReader.Role.SINE,CdProgrammeReader.Role.ATAN),p.roles().keySet());
        for(var other:CdPpzDataLayout.Programme.values())if(other!=R11&&other!=CdPpzDataLayout.Programme.R11B)assertTrue(CdProgrammeDefinition.select(other.payload().stage()).player().isEmpty(),other.name());
        assertEquals(77,R11.ranges().size()); // New fixed Player borrows are lazy, not new eager raw DATA spans.
    }
    @Test void ppz198BindingDoesNotRelabel167OrTt188Visuals() {
        var p=CdProgrammeDefinition.select(R11.payload().stage()).player().orElseThrow();
        var old=CdProgrammeDefinition.select(CdStagePayload.R31A.stage()).player().orElseThrow();
        var tt=CdProgrammeDefinition.select(CdStagePayload.R41A.stage()).player().orElseThrow();
        assertThrows(IllegalArgumentException.class,()->new CdProgrammeDefinition.Player(0,1,CdR11aPlayerAssets.SourceGraph.PPZ,old.visual(),p.roles()));
        assertThrows(IllegalArgumentException.class,()->new CdProgrammeDefinition.Player(0,1,CdR11aPlayerAssets.SourceGraph.PPZ,tt.visual(),p.roles()));
        assertThrows(IllegalArgumentException.class,()->new CdProgrammeDefinition.Player(0x100,1,CdR11aPlayerAssets.SourceGraph.R3,p.visual(),old.roles()));
        assertEquals(167,old.frames());assertEquals(188,tt.frames());
    }
    @Test void allFiveLazyBorrowRootsMatchActualMediaAndRetainCanonicalIdentity()throws Exception {
        var f=actual();var r=reader(f.image());var keys=CdPpzProgrammeProfiles.R11PlayerView.values();
        assertEquals(OFFSETS.length,keys.length);
        for(int i=0;i<keys.length;i++) {
            var key=keys[i];assertEquals(OFFSETS[i],key.offset());assertEquals(LENGTHS[i],key.length());assertEquals(HASHES[i],key.sha256());
            var bytes=r.playerRole(key.role());assertSame(bytes,r.view(key));assertSame(bytes,r.playerRole(key.role()));
            assertArrayEquals(f.image().readFile(OFFSETS[i],LENGTHS[i]),bytes.copy());assertEquals(HASHES[i],CdR11aProfile.sha256(bytes.copy()));
        }
        assertArrayEquals(new byte[]{6,7,8,0x49,0x4C,(byte)255},r.playerRole(CdProgrammeReader.Role.RAMP).copy());
        assertEquals(0x20,r.playerRole(CdProgrammeReader.Role.ATAN).unsignedByte(256));
        assertThrows(IndexOutOfBoundsException.class,()->r.playerRole(CdProgrammeReader.Role.ATAN).unsignedByte(257));
        assertThrows(IndexOutOfBoundsException.class,()->r.playerRole(CdProgrammeReader.Role.MINI).unsignedByte(60));
    }
    @Test void actualLoaderAndCommonPlayerAdapterShareOneReaderCachedPlayerAndSonic()throws Exception {
        var f=actual();var media=source(f,f.image());var owner=CdR11aAssetLoader.load(media,CdReleaseVariant.USA,R11.payload().stage());var r=owner.internalReader();
        assertSame(owner.player(),r.originalR11PlayerAssets());assertSame(owner.player(),CdR11aPlayerAssets.fromProgramme(r));
        assertSame(owner.sonic(),owner.player().sonic());assertSame(owner.sonic(),r.sonic());assertTrue(owner.player().ownsProgramme(r));
        assertEquals(CdR11aPlayerAssets.SourceGraph.PPZ,owner.player().sourceGraph());valuesEqual(legacy(f.image(),owner.sonic()),owner.player());
        verify(media,times(1)).mmd(CdNativeFile.R11A);
    }
    @Test void independentEqualReadersNeverSharePlayerProvenanceOrProducts()throws Exception {
        var f=actual();var media=source(f,f.image());var first=CdProgrammeReader.open(media,R11.payload().stage());var second=CdProgrammeReader.open(media,R11.payload().stage());
        var a=first.originalR11PlayerAssets();var b=second.originalR11PlayerAssets();
        assertNotSame(a,b);assertNotSame(a.sonic(),b.sonic());assertTrue(a.ownsProgramme(first));assertFalse(a.ownsProgramme(second));assertFalse(a.ownsProgramme(null));
        assertArrayEquals(a.animationTable().copy(),b.animationTable().copy());verify(media,times(2)).mmd(CdNativeFile.R11A);
    }
    @Test void roleReadsStayLazyAndFollowTheOriginalLoaderArgumentOrder()throws Exception {
        var image=spy(actual().image());var r=reader(image);clearInvocations(image);
        for(int i=0;i<OFFSETS.length;i++)verify(image,never()).readFile(OFFSETS[i],LENGTHS[i]);
        var p=r.originalR11PlayerAssets();var ordered=inOrder(image);
        for(int i=0;i<OFFSETS.length;i++)ordered.verify(image).readFile(OFFSETS[i],LENGTHS[i]);
        assertSame(p,r.originalR11PlayerAssets());
        for(int i=0;i<OFFSETS.length;i++)verify(image,times(1)).readFile(OFFSETS[i],LENGTHS[i]);
    }
    @Test void refusedAndCorruptLazyReadPublishesNeitherBorrowNorPlayerCache()throws Exception {
        var f=actual();var image=spy(f.image());var r=reader(image);var original=new IllegalStateException("controlled immutable source read");
        byte[] good=f.image().readFile(OFFSETS[0],LENGTHS[0]),bad=good.clone();bad[bad.length-1]^=1;
        doThrow(original).doReturn(bad).doReturn(good).when(image).readFile(OFFSETS[0],LENGTHS[0]);
        assertSame(original,assertThrows(IllegalStateException.class,r::originalR11PlayerAssets));
        assertThrows(IllegalArgumentException.class,r::originalR11PlayerAssets);
        var ready=r.originalR11PlayerAssets();assertSame(ready,r.originalR11PlayerAssets());
        assertArrayEquals(good,ready.animationTable().copy());verify(image,times(3)).readFile(OFFSETS[0],LENGTHS[0]);
    }
    @Test void laterFailedRoleRetainsEarlierBorrowButNoPartialPlayerProduct()throws Exception {
        var f=actual();var image=spy(f.image());var r=reader(image);var original=new IllegalStateException("mini read");
        doThrow(original).doReturn(f.image().readFile(OFFSETS[1],LENGTHS[1])).when(image).readFile(OFFSETS[1],LENGTHS[1]);
        assertSame(original,assertThrows(IllegalStateException.class,r::originalR11PlayerAssets));
        var a=r.playerRole(CdProgrammeReader.Role.ANI);var p=r.originalR11PlayerAssets();
        assertSame(a,r.playerRole(CdProgrammeReader.Role.ANI));assertSame(p,r.originalR11PlayerAssets());
        verify(image,times(1)).readFile(OFFSETS[0],LENGTHS[0]);verify(image,times(2)).readFile(OFFSETS[1],LENGTHS[1]);
    }
    @Test void cachedProductStillRejectsAChangedRetainedOriginalBeforeReuse()throws Exception {
        var f=actual();var image=spy(f.image());var r=reader(image);r.originalR11PlayerAssets();
        int fileBytes=image.fileBytes();
        byte[] changed=f.image().readFile(0,fileBytes);changed[OFFSETS[4]]^=1;
        doReturn(changed).when(image).readFile(0,fileBytes);
        assertThrows(IllegalStateException.class,r::originalR11PlayerAssets);
    }
    @Test void foreignKeysAndUnavailableBorrowRejectWithoutOriginalImageReads()throws Exception {
        var image=spy(actual().image());var r=reader(image);clearInvocations(image);
        assertThrows(IllegalArgumentException.class,()->r.view(CdR11aAsset.ANI_SONIC));
        var canonical=R11.range("SpecChunks");
        assertThrows(IllegalArgumentException.class,()->r.view(new CdPpzDataLayout.Range(canonical.key(),canonical.fileOffset(),canonical.length(),canonical.sha256())));
        assertThrows(IllegalArgumentException.class,()->r.view(new Object()));assertThrows(NullPointerException.class,()->r.view(null));
        verifyNoInteractions(image);
    }
    @Test void wrongProgrammeAndWrongOriginalNeverAcquireTheR11PlayerCache()throws Exception {
        var f=actual();var media=source(f,f.foreign());assertThrows(IOException.class,()->CdProgrammeReader.open(media,R11.payload().stage()));
        try(var original=TestCdCczProgrammeData.open()) {
            var r=CdProgrammeReader.open(original,CdStagePayload.R12A.stage());
            assertThrows(IllegalArgumentException.class,r::originalR11PlayerAssets);
            assertThrows(IllegalArgumentException.class,()->CdR11aPlayerAssets.fromProgramme(r));
        }
    }
    @Test void pureLegacyDecoderRetainsItsConstructedDataOnlySemantics()throws Exception {
        var r=reader(actual().image());var copied=legacy(actual().image(),r.sonic());var actual=r.originalR11PlayerAssets();
        valuesEqual(copied,actual);assertFalse(copied.ownsProgramme(r));assertEquals(CdR11aPlayerAssets.SourceGraph.PPZ,copied.sourceGraph());
    }
    @Test void immutableBorrowAndPlayerExportsCannotAlterTheCachedProduct()throws Exception {
        var r=reader(actual().image());var p=r.originalR11PlayerAssets();var input=r.playerRole(CdProgrammeReader.Role.ANI);var old=input.copy();
        var copy=input.copy();Arrays.fill(copy,(byte)0);assertArrayEquals(old,input.copy());
        assertThrows(ReadOnlyBufferException.class,()->input.buffer().put((byte)0));
        assertThrows(UnsupportedOperationException.class,()->p.animations().clear());assertArrayEquals(old,r.originalR11PlayerAssets().animationTable().copy());
    }
    @Test void sameReaderStageAndLoadingFacadesRemainDataOnly()throws Exception {
        var r=reader(actual().image());var p=r.originalR11PlayerAssets();var stage=r.playerSource();
        assertSame(p,stage.player());assertSame(stage,r.playerSource());assertSame(r.sonic(),stage.sonic());assertTrue(stage.ownsProgrammeReader(r));
        assertEquals(0,stage.nativeZoneActWord());assertEquals(1,stage.ordinaryPeriod());
        var loading=r.levelLoadingSource();assertEquals(CdLevelLoadingSource.Completion.STANDARD,loading.completion());
        assertSame(stage,loading.playerSource());loading.requirePlayerSource(stage);
    }
}
