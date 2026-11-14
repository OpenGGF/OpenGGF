package com.openggf.game.soniccd.media;

import com.openggf.data.cd.Iso9660FileSystem;
import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.level.CdAssetBytes;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.ReadOnlyBufferException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.*;

/** Authored before production. Actual original USA source plus labelled constructed-invalid controls; Java UNRUN. */
class TestCdInitialSaveAssets {
    private static final String INIT_SHA="5895de24b19d0a362f9184ff049d4e5e857f32b0e591184027b0802342765fe6";
    private static final String SUB_SHA="a1585624a99082c7a41a4107848763e73f3a0e86a6fd74749c8868dcac114ab9";
    private static final String DISC_SHA="a6b026a2381f926485cebb2da0bded29319cfd515ef7956a3ca79b18645bdb2b";
    private static final String MANIFEST="""
TIME_ATTACK_TIMES 14d8 336 ff33d8 1b97fdc3294558df5ecfc4c9adb2d66ba451e429d5a45a449d9145766f74ed68
TIME_ATTACK_INITIALS 1628 336 ff3528 cbe19222b55065336a6085ddef955df7e3fbcc6732138458e39121efb419395f
DEFAULT_INITIALS 1778 4 ff3678 4966f3d5a53ffcc1dedc26f1122d537d06d0e18c532f9606af600227b46483a7
MAIN 177c 30 ff367c 696e976d86b7c24dc5f731046a0090efce405f9696234ebc4752ea7d2c0124e6
""";
    @Test void wholeOriginalPairAndEveryFiniteTableRangeAreAuthenticated() throws Exception {
        var f=actual();var a=f.assets();var id=a.identity();assertEquals(CdReleaseVariant.USA,id.variant());
        assertEquals(CdNativeFile.BURAMINIT,id.initializationFile());assertEquals(CdNativeFile.BURAMSUB,id.subCpuFile());
        assertEquals(INIT_SHA,id.initializationSha256());assertEquals(SUB_SHA,id.subCpuSha256());assertEquals(DISC_SHA,id.observedWholeDiscSha256());
        assertEquals(102,CdNativeFile.BURAMINIT.id());assertEquals("BRAMINIT.MMD;1",CdNativeFile.BURAMINIT.isoName());
        assertEquals(103,CdNativeFile.BURAMSUB.id());assertEquals("BRAMSUB.BIN;1",CdNativeFile.BURAMSUB.isoName());
        assertEquals(f.initEntry(),a.initializationEntry());assertEquals(10246,a.initializationEntry().extentBlock());assertEquals(10998,a.initializationEntry().size());
        assertEquals(f.subEntry(),a.subCpuEntry());assertEquals(10252,a.subCpuEntry().extentBlock());assertEquals(2592,a.subCpuEntry().size());
        var rows=MANIFEST.strip().lines().toList();assertEquals(4,CdInitialSaveAssets.Table.values().length);assertEquals(rows.size(),a.ranges().size());
        for(int n=0;n<rows.size();n++) {
            var v=rows.get(n).split(" ");var table=CdInitialSaveAssets.Table.valueOf(v[0]);var range=a.range(table);
            assertEquals(n,table.ordinal());assertSame(range,a.ranges().get(n));assertSame(range,a.range(table));
            assertEquals(CdNativeFile.BURAMINIT,range.file());assertEquals(CdMmdImage.Domain.RELOCATED_COPY,range.domain());
            assertEquals(Integer.parseInt(v[1],16),range.fileOffset());assertEquals(Integer.parseInt(v[2]),range.length());
            assertEquals(Integer.parseInt(v[3],16),range.nativeAddress());assertEquals(v[4],range.sha256());assertEquals(v[4],hash(a.view(table).copy()));
            assertSame(a.view(table),a.view(table));assertArrayEquals(f.init().readFile(range.fileOffset(),range.length()),a.view(table).copy());
            assertThrows(IndexOutOfBoundsException.class,()->a.view(table).unsignedByte(range.length()));
        }
        assertThrows(NullPointerException.class,()->a.range(null));assertThrows(NullPointerException.class,()->a.view(null));
    }
    @Test void fullTableExpansionIncludesAllTimesInitialsDefaultAndRealMainPadding() throws Exception {
        var a=actual().assets();assertEquals(706,a.initialData().length());assertEquals("c8e9bcc03252fd3cd3df44cbc2810c6bcc9ac1b1d155f2039b19ff76222abee1",hash(a.initialData().copy()));
        assertEquals(676,a.timeAttack().length());assertArrayEquals(a.initialData().slice(0,676).copy(),a.timeAttack().copy());
        // Independent literal native table expectations, never runtime replacement bytes.
        for(int row=0;row<84;row++) {
            assertEquals(0x00050000,a.view(CdInitialSaveAssets.Table.TIME_ATTACK_TIMES).unsignedLong(row*4));
            assertEquals(0x0B0B0B00,a.view(CdInitialSaveAssets.Table.TIME_ATTACK_INITIALS).unsignedLong(row*4));
        }
        assertEquals(0x23191F00,a.view(CdInitialSaveAssets.Table.DEFAULT_INITIALS).unsignedLong(0));
        var main=a.view(CdInitialSaveAssets.Table.MAIN);assertEquals(30,main.length());
        assertEquals("000000001000000000000000000000000000000000000000000000000000",HexFormat.of().formatHex(main.copy()));
        assertArrayEquals(main.copy(),a.initialData().slice(676,30).copy());
    }
    @Test void projectedInitialMainFieldsDescribeOnlyTheSourceTemplate() throws Exception {
        var a=actual().assets();var fields=a.initialMainFields();assertSame(fields,a.initialMainFields());
        assertEquals(0,fields.zoneByte());assertEquals(0,fields.timeAttackUnlockByte());assertEquals(0,fields.unknownByte());
        assertEquals(0,fields.goodFuturesByte());assertEquals(0x10,fields.titleFlagsByte());assertEquals(0,fields.specialStageByte());assertEquals(0,fields.timeStonesByte());
        assertEquals(a.view(CdInitialSaveAssets.Table.MAIN).unsignedWord(0),fields.savedLevelWord(),"Native savedLevel WORD overlaps zone/unlock; no progress owner is created");
        assertEquals(a.view(CdInitialSaveAssets.Table.MAIN).unsignedByte(4),fields.titleFlagsByte());
        assertTrue(Arrays.stream(CdInitialSaveAssets.class.getMethods()).noneMatch(m->Set.of("fresh","createProfile","restoreProgress","save","load","apply").contains(m.getName())));
    }
    @Test void declaredMmdCopyRetainsItsGenuineUnresolvedTail() throws Exception {
        var a=actual().assets();assertEquals(new CdMmdHeader(0,0,0xFF2000,0xBFF,0xFF2000,0,0xFF20AE),a.initializationHeader());
        assertEquals(12288,a.initializationHeader().declaredCopyBytes());assertEquals(10742,a.fileBackedInitializationCopyBytes());assertEquals(1546,a.unresolvedInitializationCopyBytes());
        var last=a.range(CdInitialSaveAssets.Table.MAIN);assertTrue(last.fileOffset()+last.length()<=10998);
        assertTrue(last.nativeAddress()+last.length()<=0xFF2000+10742,"All admitted tables are actually file-backed, not the unresolved relocation tail");
    }
    @Test void nativeLongwordTransfersPreserveBothDistinctShortTails() throws Exception {
        var a=actual().assets();var transfers=a.transfers();assertEquals(List.of(CdInitialSaveAssets.Transfer.values()),transfers);assertEquals(4,transfers.size());
        int[][] literal={{0x17D6,0xFF36D6,0xFF33D8,0x200040,0xA8,676,676},{0x17EC,0xFF36EC,0xFF367C,0x2002E4,6,30,28},
                {0x398,0x10398,0x80040,0x7400,0xAF,706,704},{0x3B8,0x103B8,0x7400,0x80040,0xAF,706,704}};
        for(int n=0;n<literal.length;n++) {
            var t=transfers.get(n);var v=literal[n];assertEquals(n<2?CdNativeFile.BURAMINIT:CdNativeFile.BURAMSUB,t.file());
            assertEquals(v[0],t.instructionFileOffset());assertEquals(v[1],t.instructionNativeAddress());assertEquals(v[2],t.sourceNativeAddress());
            assertEquals(v[3],t.destinationNativeAddress());assertEquals(v[4],t.counterWord());assertEquals(v[5],t.declaredDataBytes());assertEquals(v[6],t.copiedBytes());
            assertEquals((t.counterWord()+1)*4,t.copiedBytes());
        }
        assertEquals(2,transfers.get(1).declaredDataBytes()-transfers.get(1).copiedBytes());
        assertEquals(2,transfers.get(2).declaredDataBytes()-transfers.get(2).copiedBytes());
        assertArrayEquals(a.view(CdInitialSaveAssets.Table.MAIN).slice(28,2).copy(),a.initialData().slice(704,2).copy(),"Retained real table bytes do not claim the copy touched them");
        assertThrows(UnsupportedOperationException.class,()->transfers.clear());
    }
    @Test void actualMainAndSubConsumerOperandsBindEveryTransferToItsOwnOriginalImage() throws Exception {
        var f=actual();for(var t:f.assets().transfers()) {
            byte[] bytes=t.file()==CdNativeFile.BURAMINIT?f.init().readFile(0,10998):f.sub();int at=t.instructionFileOffset();var b=CdAssetBytes.copyOf(bytes);
            assertEquals(t.file()==CdNativeFile.BURAMINIT?0x41F9:0x207C,b.unsignedWord(at));assertEquals(t.sourceNativeAddress(),b.unsignedLong(at+2));
            assertEquals(t.file()==CdNativeFile.BURAMINIT?0x43F9:0x227C,b.unsignedWord(at+6));assertEquals(t.destinationNativeAddress(),b.unsignedLong(at+8));
            assertEquals(0x303C,b.unsignedWord(at+12));assertEquals(t.counterWord(),b.unsignedWord(at+14));assertEquals(0x22D8,b.unsignedWord(at+16));
            assertEquals(0x51C8,b.unsignedWord(at+18));assertEquals(0xFFFC,b.unsignedWord(at+20));
        }
        assertEquals(INIT_SHA,hash(f.init().readFile(0,10998)));assertEquals(SUB_SHA,hash(f.sub()));
    }
    @Test void detachedCachesAndOriginalBytesRemainImmutableAfterDiscOwnerCloses() throws Exception {
        var a=actual().assets();assertSame(a.initialData(),a.initialData());assertSame(a.timeAttack(),a.timeAttack());
        byte[] copy=a.initialData().copy();copy[4]^=1;assertEquals(0,a.initialData().unsignedByte(4));
        var b=a.initialData().buffer();assertTrue(b.isReadOnly());assertThrows(ReadOnlyBufferException.class,()->b.put(0,(byte)1));b.position(17);assertEquals(0,a.initialData().buffer().position());
        assertThrows(UnsupportedOperationException.class,()->a.ranges().clear());assertThrows(IndexOutOfBoundsException.class,()->a.initialData().slice(705,2));
        assertEquals("c8e9bcc03252fd3cd3df44cbc2810c6bcc9ac1b1d155f2039b19ff76222abee1",hash(a.initialData().copy()));
    }
    @Test void missingForeignAndInconsistentSourceOwnersFailBeforeAnyFileRead() throws Exception {
        var f=actual();assertThrows(NullPointerException.class,()->CdInitialSaveAssets.fromPrograms(null));
        var wrong=source(f,f.init(),f.sub());var header=mock(CdDiscHeader.class);when(header.observedVariant()).thenReturn(Optional.of(CdReleaseVariant.EUROPE));when(wrong.header()).thenReturn(header);
        assertThrows(IOException.class,()->CdInitialSaveAssets.fromPrograms(wrong));verify(wrong,never()).entry(any());verify(wrong,never()).mmd(any());
        var unknown=source(f,f.init(),f.sub());var id=f.disc();when(unknown.identity()).thenReturn(new CdObservedMediaIdentity(id.definition(),"0".repeat(64),id.cue(),id.binaryFiles(),id.originalContainer(),id.tracks()));
        assertThrows(IOException.class,()->CdInitialSaveAssets.fromPrograms(unknown));verify(unknown,never()).mmd(any());
        var inconsistent=source(f,f.init(),f.sub());when(inconsistent.identity()).thenReturn(new CdObservedMediaIdentity(id.definition(),DISC_SHA,id.cue(),id.binaryFiles(),id.originalContainer(),id.tracks().subList(0,34)));
        assertThrows(IOException.class,()->CdInitialSaveAssets.fromPrograms(inconsistent));verify(inconsistent,never()).mmd(any());
        var missing=source(f,f.init(),f.sub());when(missing.catalogue()).thenReturn(Optional.empty());assertThrows(IOException.class,()->CdInitialSaveAssets.fromPrograms(missing));verify(missing,never()).mmd(any());
        var foreign=source(f,f.init(),f.sub());when(foreign.catalogue()).thenReturn(Optional.of(CdProgramCatalogue.forRelease(CdReleaseVariant.JAPAN)));
        assertThrows(IOException.class,()->CdInitialSaveAssets.fromPrograms(foreign));verify(foreign,never()).mmd(any());
    }
    @Test void wrongCataloguePairAndIsoExtentsCannotBeSubstituted() throws Exception {
        var f=actual();var reversed=source(f,f.init(),f.sub());var catalogue=mock(CdProgramCatalogue.class);when(catalogue.release()).thenReturn(CdReleaseVariant.USA);
        when(catalogue.programForCommand(CdCommand.BURAMINIT)).thenReturn(Optional.of(CdNativeProgram.BURAMINIT));
        when(catalogue.nativeLoadFiles(CdCommand.BURAMINIT)).thenReturn(List.of(CdNativeFile.BURAMSUB,CdNativeFile.BURAMINIT));when(reversed.catalogue()).thenReturn(Optional.of(catalogue));
        assertThrows(IOException.class,()->CdInitialSaveAssets.fromPrograms(reversed));verify(reversed,never()).mmd(any());
        var wrongCommand=source(f,f.init(),f.sub());var other=mock(CdProgramCatalogue.class);when(other.release()).thenReturn(CdReleaseVariant.USA);
        when(other.programForCommand(CdCommand.BURAMINIT)).thenReturn(Optional.of(CdNativeProgram.BURAMMGR));when(other.nativeLoadFiles(CdCommand.BURAMINIT)).thenReturn(List.of(CdNativeFile.BURAMINIT,CdNativeFile.BURAMSUB));when(wrongCommand.catalogue()).thenReturn(Optional.of(other));
        assertThrows(IOException.class,()->CdInitialSaveAssets.fromPrograms(wrongCommand));verify(wrongCommand,never()).mmd(any());
        var alias=source(f,f.init(),f.sub());when(alias.entry(CdNativeFile.BURAMINIT)).thenReturn(new Iso9660FileSystem.Entry("/BRAMMAIN.MMD;1",false,10246,10998,1));
        assertThrows(IOException.class,()->CdInitialSaveAssets.fromPrograms(alias));verify(alias,never()).mmd(any());
        var shifted=source(f,f.init(),f.sub());when(shifted.entry(CdNativeFile.BURAMSUB)).thenReturn(new Iso9660FileSystem.Entry(f.subEntry().path(),false,10253,2592,1));
        assertThrows(IOException.class,()->CdInitialSaveAssets.fromPrograms(shifted));verify(shifted,never()).read(any(),anyLong(),anyInt());
    }
    @Test void everyOriginalTableAndHeaderMutationOrShortImageIsRejected() throws Exception {
        var f=actual();for(var range:f.assets().ranges()) {
            byte[] changed=f.init().readFile(0,10998);changed[range.fileOffset()+range.length()-1]^=1;
            assertThrows(IOException.class,()->CdInitialSaveAssets.fromPrograms(source(f,CdMmdImage.parse(changed),f.sub())),range.table().name());
        }
        for(int at:new int[]{0,2,6,8,16,0x17D6,0x17F8}) {
            byte[] changed=f.init().readFile(0,10998);changed[at]^=1;
            assertThrows(IOException.class,()->CdInitialSaveAssets.fromPrograms(source(f,CdMmdImage.parse(changed),f.sub())),"Constructed-invalid original mutation@"+at);
        }
        assertThrows(IOException.class,()->CdInitialSaveAssets.fromPrograms(source(f,CdMmdImage.parse(f.init().readFile(0,10997)),f.sub())));
    }
    @Test void originalSubFaultsAndReadExceptionsCannotProduceAnAssetProduct() throws Exception {
        var f=actual();for(int at:new int[]{0,0x398,0x3A6,0x3C6,2591}) {
            byte[] bad=f.sub();bad[at]^=1;assertThrows(IOException.class,()->CdInitialSaveAssets.fromPrograms(source(f,f.init(),bad)),"Constructed-invalid Sub mutation@"+at);
        }
        var shorter=source(f,f.init(),Arrays.copyOf(f.sub(),2591));assertThrows(IOException.class,()->CdInitialSaveAssets.fromPrograms(shorter));
        var badExtent=source(f,f.init(),f.sub());when(badExtent.entry(CdNativeFile.BURAMSUB)).thenReturn(new Iso9660FileSystem.Entry(f.subEntry().path(),false,10252,2591,1));
        assertThrows(IOException.class,()->CdInitialSaveAssets.fromPrograms(badExtent));verify(badExtent,never()).read(any(),anyLong(),anyInt());
        var fault=source(f,f.init(),f.sub());var original=new IOException("constructed original source read failure");when(fault.read(CdNativeFile.BURAMSUB,0,2592)).thenThrow(original);
        assertSame(original,assertThrows(IOException.class,()->CdInitialSaveAssets.fromPrograms(fault)));
    }
    @Test void publicValuesHaveStrictWidthsAndNoRawFactoryOrStorageAuthority() {
        // Constructed values only: this is NOT an authenticated positive asset or created profile.
        var fields=new CdInitialSaveAssets.MainFields(0xAB,0xCD,0xFE,0x81,0x90,0x7F,0xFF);assertEquals(0xABCD,fields.savedLevelWord());
        for(int at=0;at<7;at++){int[] v={0,0,0,0,0,0,0};v[at]=256;assertThrows(IllegalArgumentException.class,()->new CdInitialSaveAssets.MainFields(v[0],v[1],v[2],v[3],v[4],v[5],v[6]));}
        assertThrows(IllegalArgumentException.class,()->new CdInitialSaveAssets.MainFields(-1,0,0,0,0,0,0));
        assertThrows(IllegalArgumentException.class,()->new CdInitialSaveAssets.Identity(CdReleaseVariant.JAPAN,CdNativeFile.BURAMINIT,CdNativeFile.BURAMSUB,INIT_SHA,SUB_SHA,DISC_SHA));
        assertThrows(IllegalArgumentException.class,()->new CdInitialSaveAssets.Identity(CdReleaseVariant.USA,CdNativeFile.BURAMMAIN,CdNativeFile.BURAMSUB,INIT_SHA,SUB_SHA,DISC_SHA));
        assertThrows(IllegalArgumentException.class,()->new CdInitialSaveAssets.Identity(CdReleaseVariant.USA,CdNativeFile.BURAMINIT,CdNativeFile.BURAMSUB,"0".repeat(64),SUB_SHA,DISC_SHA));
        assertThrows(IllegalArgumentException.class,()->new CdInitialSaveAssets.Range(CdInitialSaveAssets.Table.MAIN,CdNativeFile.BURAMINIT,CdMmdImage.Domain.RELOCATED_COPY,0x177D,30,0xFF367C,"696e976d86b7c24dc5f731046a0090efce405f9696234ebc4752ea7d2c0124e6"));
        assertThrows(IllegalArgumentException.class,()->new CdInitialSaveAssets.Range(CdInitialSaveAssets.Table.MAIN,CdNativeFile.BURAMSUB,CdMmdImage.Domain.RELOCATED_COPY,0x177C,30,0xFF367C,"696e976d86b7c24dc5f731046a0090efce405f9696234ebc4752ea7d2c0124e6"));
        assertThrows(IllegalArgumentException.class,()->new CdInitialSaveAssets.Range(CdInitialSaveAssets.Table.MAIN,CdNativeFile.BURAMINIT,CdMmdImage.Domain.ORIGINAL_WORD_RAM,0x177C,30,0x20177C,"696e976d86b7c24dc5f731046a0090efce405f9696234ebc4752ea7d2c0124e6"));
        assertTrue(Arrays.stream(CdInitialSaveAssets.class.getDeclaredConstructors()).allMatch(c->Modifier.isPrivate(c.getModifiers())));
        var factories=Arrays.stream(CdInitialSaveAssets.class.getDeclaredMethods()).filter(m->Modifier.isPublic(m.getModifiers())&&Modifier.isStatic(m.getModifiers())).toList();
        assertEquals(1,factories.size());assertEquals("fromPrograms",factories.getFirst().getName());assertArrayEquals(new Class<?>[]{CdProgramAssets.class},factories.getFirst().getParameterTypes());
    }
    private record Fixture(CdInitialSaveAssets assets,CdDiscHeader header,CdObservedMediaIdentity disc,CdProgramCatalogue catalogue,
                           CdMmdImage init,byte[] sub,Iso9660FileSystem.Entry initEntry,Iso9660FileSystem.Entry subEntry) {
        private Fixture{sub=sub.clone();}@Override public byte[] sub(){return sub.clone();}
    }
    private static Fixture cached;
    private static String cachedRoot;
    private static synchronized Fixture actual() throws Exception {
        String root=System.getProperty("soniccd.media.root");assumeTrue(root!=null&&!root.isBlank(),"Optional configured original USA CD media absent");
        if(cached!=null&&root.equals(cachedRoot))return cached;Path usa=Path.of(root).resolve("USA");assertTrue(Files.isDirectory(usa),"Configured original USA media directory must exist");
        List<Path> cues;try(var files=Files.list(usa)){cues=files.filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();}
        assertEquals(1,cues.size(),"Configured original USA media requires exactly one CUE; bad configuration must fail");
        try(var programs=CdProgramAssets.open(cues.getFirst(),Optional.of(CdReleaseVariant.USA),Optional.empty())) {
            cached=new Fixture(CdInitialSaveAssets.fromPrograms(programs),programs.header(),programs.identity(),programs.catalogue().orElseThrow(),
                    programs.mmd(CdNativeFile.BURAMINIT),programs.read(CdNativeFile.BURAMSUB,0,2592),programs.entry(CdNativeFile.BURAMINIT),programs.entry(CdNativeFile.BURAMSUB));
        }
        cachedRoot=root;return cached;
    }
    private static CdProgramAssets source(Fixture f,CdMmdImage image,byte[] sub) throws Exception {
        var p=mock(CdProgramAssets.class);when(p.header()).thenReturn(f.header());when(p.identity()).thenReturn(f.disc());when(p.catalogue()).thenReturn(Optional.of(f.catalogue()));
        when(p.entry(CdNativeFile.BURAMINIT)).thenReturn(f.initEntry());when(p.entry(CdNativeFile.BURAMSUB)).thenReturn(f.subEntry());
        when(p.mmd(CdNativeFile.BURAMINIT)).thenReturn(image);when(p.read(CdNativeFile.BURAMSUB,0,2592)).thenReturn(sub);return p;
    }
    private static String hash(byte[] bytes) throws Exception {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
}
