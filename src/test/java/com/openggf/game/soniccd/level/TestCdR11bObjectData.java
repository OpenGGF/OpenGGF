package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.objects.CdObjectMappings;
import com.openggf.game.soniccd.objects.CdObjectDrawQueue.FrameMapping;
import com.openggf.game.soniccd.objects.CdObjectDrawQueue.StaticMapping;
import org.junit.jupiter.api.Test;
import java.nio.ReadOnlyBufferException;
import java.util.Arrays;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

/** Original-user-media DATA requirements authored before production. All Java UNRUN. */
class TestCdR11bObjectData {
    private static CdProgrammeReader reader(CdStagePayload stage)throws Exception {
        try(var media=TestCdCczProgrammeData.open()){return CdProgrammeReader.open(media,stage.stage());}
    }
    private static CdR11bObjectData source()throws Exception {
        return CdPpzProgrammeData.fromReader(reader(CdStagePayload.R11B)).objectSource();
    }
    @Test void retainedDataFacadeCachesItsOwnSourceAndKeepsTheActualReader()throws Exception {
        var r=reader(CdStagePayload.R11B);var data=CdPpzProgrammeData.fromReader(r);var source=data.objectSource();
        assertSame(source,data.objectSource());assertTrue(source.ownsReader(r));
        assertSame(r.view(CdPpzProgrammeProfiles.R11PastLoadingView.OBJECT_INDEX),source.view(CdR11bObjectData.Table.DISPATCH).bytes());
        assertEquals(data.wholeDiscSha256(),source.wholeDiscSha256());assertEquals(data.mmdSha256(),source.mmdSha256());
    }
    @Test void allSixtyFourPhysicalSlotsRetainEveryOriginalPointerAndThirteenNulls()throws Exception {
        var s=source();var index=s.view(CdR11bObjectData.Table.DISPATCH).bytes();int nulls=0;
        assertEquals(64,s.families().size());assertEquals(256,index.length());
        for(int i=1;i<=64;i++){var f=s.dispatch(i).orElseThrow();assertEquals(i,f.objectId());assertEquals(index.unsignedLong((i-1)*4),Integer.toUnsignedLong(f.handlerAddress()));if(f.nativeNull()){nulls++;assertEquals(0x203D38,f.handlerAddress());}}
        assertEquals(13,nulls);assertEquals(51,new HashSet<>(s.families().stream().map(CdR11bObjectData.Family::nativeLabel).toList()).size());
        assertEquals("10bc00004e75",java.util.HexFormat.of().formatHex(s.view(CdR11bObjectData.Table.NULL_HANDLER).bytes().copy()));
    }
    @Test void dispatcherNeverMasksHighObjectIdBitsOrInventsZeroHandlers()throws Exception {
        var s=source();assertTrue(s.dispatch(0).isEmpty());
        var edge=assertThrows(CdR11bObjectData.UnownedDispatchRead.class,()->s.dispatch(0x80));
        assertEquals(0x203E34L,edge.nativeAddress());assertEquals(0x80,edge.objectId());
        assertThrows(IllegalArgumentException.class,()->s.dispatch(256));assertThrows(IllegalArgumentException.class,()->s.dispatch(-1));
        assertEquals(s.dispatch(1).orElseThrow().handlerAddress(),s.dispatch(2).orElseThrow().handlerAddress());
    }
    @Test void everyNamedFiniteRoleHasActualOriginalBytesAndPrivateViewIdentity()throws Exception {
        var s=source();assertEquals(98,CdR11bObjectData.Table.values().length);
        for(var role:CdR11bObjectData.Table.values()){
            var view=s.view(role);assertSame(view,s.view(role));assertTrue(view.belongsTo(s));s.requireView(view);
            assertEquals(role.bytes(),view.bytes().length());assertEquals(role.sha256(),CdR11aProfile.sha256(view.bytes().copy()));
            assertEquals(0x200000+role.fileOffset(),view.nativeAddress());
        }
        assertEquals(72,s.view(CdR11bObjectData.Table.OBJECT_TILE_IDS).bytes().length());
        assertEquals(0x20CF94,s.view(CdR11bObjectData.Table.OBJECT_TILE_IDS).nativeAddress());
    }
    @Test void independentEqualReadersCannotExchangeViewsOrSourceProvenance()throws Exception {
        var a=source();var b=source();assertNotSame(a,b);var view=a.view(CdR11bObjectData.Table.DISPATCH);
        assertFalse(view.belongsTo(b));assertThrows(IllegalArgumentException.class,()->b.requireView(view));
        assertFalse(a.ownsReader(reader(CdStagePayload.R11B)));
    }
    @Test void presentOrForeignReaderCannotRelabelItselfAsPastObjectData()throws Exception {
        var a=reader(CdStagePayload.R11A);var b=reader(CdStagePayload.R11B);var data=CdPpzProgrammeData.fromReader(b);
        assertThrows(IllegalArgumentException.class,()->CdPpzProgrammeData.fromReader(a).objectSource());
        assertThrows(IllegalArgumentException.class,()->CdR11bObjectData.fromReader(data,a));
        assertThrows(IllegalArgumentException.class,()->CdR11bObjectData.fromReader(data,reader(CdStagePayload.R11B)));
    }
    @Test void mappingFactoryKeepsAllSixtyOwnRootsAndExistingSonic198()throws Exception {
        var r=reader(CdStagePayload.R11B);var s=CdPpzProgrammeData.fromReader(r).objectSource();var mapper=s.mappings();
        assertEquals(60,CdR11bObjectData.Mapping.values().length);assertSame(mapper,s.mappings());
        int frames=0;
        for(var root:CdR11bObjectData.Mapping.values()){
            var raw=s.view(root.table()).bytes();int address=root.nativeAddress();
            for(int frame=0;frame<root.frames();frame++){
                int at=root.tableOffset()+(short)raw.unsignedWord(root.tableOffset()+frame*2);
                var body=assertInstanceOf(FrameMapping.class,mapper.resolve(address,false,frame));assertEquals(raw.unsignedByte(at),body.countByte());
                for(int i=0;i<body.pieces().size();i++){var p=body.pieces().get(i);int start=at+1+i*5;
                    assertEquals(raw.signedByte(start),p.y());assertEquals(raw.unsignedByte(start+1),p.sizeByte());assertEquals(raw.unsignedWord(start+2),p.rawTileWord());assertEquals(raw.signedByte(start+4),p.x());}
                frames++;
            }
        }
        assertEquals(307,frames);var sonic=CdPpzDataLayout.Programme.R11B.range("PlayerMappings");
        for(int frame=0;frame<198;frame++)assertNotNull(mapper.resolve(0x200000+sonic.fileOffset(),false,frame));
        assertEquals(198,r.sonic().mappingFrames().size());
    }
    @Test void genuinePastCollapseTablesKeepTheirDifferentFrameCounts()throws Exception {
        assertEquals(2,CdR11bObjectData.Mapping.MAP_SPR_COLLAPSE_PLATFORM1.frames());
        assertEquals(7,CdR11bObjectData.Mapping.MAP_SPR_COLLAPSE_PLATFORM4.frames());
        assertEquals(0x20C378,CdR11bObjectData.Mapping.MAP_SPR_COLLAPSE_PLATFORM1.nativeAddress());
        var mapper=source().mappings();assertThrows(UnsupportedOperationException.class,()->mapper.resolve(0x20C520,false,0));
    }
    @Test void countedHeaderAliasesAndPackedTileWordsRemainSourceBytes()throws Exception {
        var s=source();var root=CdR11bObjectData.Mapping.MAP_SPR_POWERUP;var raw=s.view(root.table()).bytes();
        assertEquals(37,raw.unsignedWord(0));assertEquals(0,assertInstanceOf(FrameMapping.class,s.mappings().resolve(root.nativeAddress(),false,0)).countByte());
        assertEquals(13,root.frames());assertEquals(274,raw.length());
        assertThrows(UnsupportedOperationException.class,()->s.mappings().resolve(root.nativeAddress()^0x01000000,false,0));
        assertThrows(IllegalArgumentException.class,()->s.mappings().resolve(root.nativeAddress(),false,256));
    }
    @Test void eightMonitorItemStaticPiecesKeepActualOddPointersAndFiveBytes()throws Exception {
        var s=source();var source=s.view(CdR11bObjectData.Table.MAP_SPR_MONITOR_TIME);var pieces=s.monitorItemPieces();assertEquals(8,pieces.size());
        for(int i=0;i<8;i++){var piece=pieces.get(i);int at=(short)source.bytes().unsignedWord(i*2)+1;
            assertEquals(source.nativeAddress()+at,piece.addressBits());assertArrayEquals(source.bytes().slice(at,5).copy(),piece.bytes().copy());
            assertInstanceOf(StaticMapping.class,s.mappings().resolve(piece.addressBits(),true,255));}
        assertThrows(UnsupportedOperationException.class,()->s.mappings().resolve(source.nativeAddress()+2,true,0));
    }
    @Test void animationDirectoriesUseExistingImmutableKernelTablesWithoutService()throws Exception {
        var s=source();assertEquals(34,CdR11bObjectData.Animation.values().length);
        for(var animation:CdR11bObjectData.Animation.values()){
            var table=s.animation(animation);assertSame(table,s.animation(animation));assertSame(s.view(animation.table()).bytes(),table.bytes());
            assertEquals(animation.tableOffset(),table.tableOffset());assertEquals(animation.scripts(),table.scriptCount());
        }
    }
    @Test void originalPlcArtOwnersBorrowTheOneCommonReaderProductsInOrder()throws Exception {
        var r=reader(CdStagePayload.R11B);var source=CdPpzProgrammeData.fromReader(r).objectSource();var owners=source.artOwners();assertSame(owners,source.artOwners());int cursor=0;
        for(var list:r.loadTables().plcs())for(int i=0;i<list.body().entries().size();i++){
            var e=list.body().entries().get(i);var owner=owners.get(cursor++);assertEquals(list.nativeId(),owner.plcId());assertEquals(i,owner.entryOrdinal());
            assertEquals(e.sourceNativeAddress(),owner.nativeSourceAddress());assertEquals(e.vramAddress(),owner.vramDestination());
            assertSame(r.nativeInput(owner.range()),source.nativeArt(owner));assertSame(r.decoded(owner.range()),source.decodedArt(owner));}
        assertEquals(cursor,owners.size());var foreign=source();assertThrows(IllegalArgumentException.class,()->foreign.nativeArt(owners.getFirst()));
    }
    @Test void nativeSourceIdWriteMetadataStaysInertAndSourceOrdered()throws Exception {
        var s=source();assertEquals(22,s.idWrites().size());
        for(var write:s.idWrites()){assertTrue(write.line()>0);assertTrue(write.objectId()>=0&&write.objectId()<=64);assertTrue(write.sourcePath().startsWith("src/Level/"));}
        assertEquals(3,s.reservedHandler(CdR11bObjectData.ReservedRole.SHIELD_AND_STARS).objectId());
        assertEquals(0x1C,s.reservedHandler(CdR11bObjectData.ReservedRole.HUD_POINTS).objectId());
        assertEquals(0x3C,s.reservedHandler(CdR11bObjectData.ReservedRole.TITLE_CARD).objectId());
    }
    @Test void returnedViewsAndTablesRemainImmutableAndSupplyNoInvocationApi()throws Exception {
        var s=source();var v=s.view(CdR11bObjectData.Table.DISPATCH);var copy=v.bytes().copy();Arrays.fill(copy,(byte)0);
        assertNotEquals(0,v.bytes().unsignedLong(0));assertThrows(ReadOnlyBufferException.class,()->v.bytes().buffer().put((byte)0));
        assertThrows(UnsupportedOperationException.class,()->s.families().clear());assertThrows(UnsupportedOperationException.class,()->s.artOwners().clear());
        for(var constructor:CdR11bObjectData.class.getDeclaredConstructors())assertFalse(java.lang.reflect.Modifier.isPublic(constructor.getModifiers()));
        for(var method:CdR11bObjectData.class.getMethods())assertFalse(java.util.Set.of("invoke","restore","framePermit","fullPermit","beginService").contains(method.getName()));
    }
}
