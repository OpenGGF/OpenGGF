package com.openggf.mods.code;

import com.openggf.game.GameModule;
import com.openggf.level.AbstractLevel;
import com.openggf.level.LevelPatch;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.rings.RingSpawn;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.io.ModAssetRoot;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestNativeDecodedPlacementPatchHost {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path saves;
    private static final class NativeFixture extends AbstractLevel {
        NativeFixture() {
            super(0);
            patternCount=1;patterns=new com.openggf.level.Pattern[]{new com.openggf.level.Pattern()};
            chunkCount=1;chunks=new com.openggf.level.Chunk[]{new com.openggf.level.Chunk()};
            blockCount=1;blocks=new com.openggf.level.Block[]{new com.openggf.level.Block(1)};
            solidTileCount=0;solidTiles=new com.openggf.level.SolidTile[0];
            map=new com.openggf.level.Map(2,4,4);
            palettes=new com.openggf.level.Palette[PALETTE_COUNT];
            for(int i=0;i<PALETTE_COUNT;i++) palettes[i]=new com.openggf.level.Palette();
            maxX=512;maxY=512;
            objects=List.of(new ObjectSpawn(100,200,0x8d,0,0,false,200,1));
            rings=List.of(new RingSpawn(120,180,1),new RingSpawn(140,180,2));
        }
    }
    private static ModFaultBoundary boundary() {
        return new ModFaultBoundary(Map.of(),new ModRuntimeFindingStore(),
                ignored->new ModStateSaveResult.Saved(),ignored->{ });
    }

    @Test void hostAdmitsAndAppliesNativePlacementCallbacksWithinTheRegisteredOwnerBoundary() {
        var context=new ModContext("sample","s3k",ModAssetRoot.forTests("sample"));
        context.registerObject("guard",(spawn,registry)->null);
        var calls=new AtomicInteger();
        context.decodedLevelPatch("placements",LevelPatch.empty().forZone(0).nativePlacements()
                .whenInventory(objects->{assertEquals("sample",OwnerCallbackScope.current());return true;})
                .select(s->s.ownerModId()==null).bind("guard")
                .retainRings((rings,objects)->{
                    assertEquals("sample",OwnerCallbackScope.current());calls.incrementAndGet();
                    assertNull(objects.getFirst().ownerModId());return List.of(rings.getFirst());
                }));
        var base=mock(GameModule.class,CALLS_REAL_METHODS);
        var module=OwnedModServices.decorate(base,context.freeze(),boundary());
        var source=new NativeFixture();
        assertSame(source,module.transformDecodedLevel(source));
        assertEquals("sample:guard",source.getObjects().getFirst().objectKey());
        assertEquals(1,source.getRings().size());assertEquals(1,calls.get());
        assertNull(OwnerCallbackScope.current());
    }

    @Test void additionsKeepNativeTablesAndHaveStableOwnedIdentitiesAcrossReloadAndComposition() {
        var context=new ModContext("sample","s3k",ModAssetRoot.forTests("sample"));
        context.registerObject("guard",(spawn,registry)->null);
        context.decodedLevelPatch("first",LevelPatch.empty().forZone(0).nativePlacements()
                .addObject("guard",40,90,7,2,true));
        context.decodedLevelPatch("second",LevelPatch.empty().nativePlacements()
                .addObject("guard",40,90,7,2,true));
        var base=mock(GameModule.class,CALLS_REAL_METHODS);
        when(base.getObjectPlacementEncoding()).thenReturn(new com.openggf.game.common.CommonObjectPlacementEncoding());
        var module=OwnedModServices.decorate(base,context.freeze(),boundary());
        var source=new NativeFixture();var original=source.getObjects().getFirst();var rings=source.getRings();
        assertSame(source,module.transformDecodedLevel(source));
        assertSame(original,source.getObjects().getFirst());
        assertEquals(rings,source.getRings());
        assertEquals(List.of(1,2,3),source.getObjects().stream().map(ObjectSpawn::layoutIndex).toList());
        var added=source.getObjects().get(1);
        assertEquals("sample",added.ownerModId());assertEquals("sample:guard",added.objectKey());
        assertEquals(40,added.x());assertEquals(90,added.y());assertEquals(7,added.subtype());
        assertEquals(2,added.renderFlags());assertTrue(added.respawnTracked());
        assertEquals(base.getObjectPlacementEncoding().createKeyed(40,90,"sample:guard",7,2,true,2),added);
        var reload=new NativeFixture();module.transformDecodedLevel(reload);
        assertEquals(source.getObjects(),reload.getObjects());
    }

    @Test void additionsRequireRegisteredLocalFactoriesAndRespectInventoryGates() {
        var context=new ModContext("sample","s3k",ModAssetRoot.forTests("sample"));
        context.decodedLevelPatch("missing",LevelPatch.empty().nativePlacements().addObject("guard",40,90,0,0,false));
        var rejected=assertThrows(ModFaultBoundary.CallbackAborted.class,()->OwnedModServices.decorate(
                mock(GameModule.class,CALLS_REAL_METHODS),context.freeze(),boundary()));
        assertInstanceOf(IllegalArgumentException.class,rejected.getCause());
        assertThrows(IllegalArgumentException.class,()->LevelPatch.empty().addObject("foreign:guard",40,90,0,0,false));
        assertThrows(IllegalArgumentException.class,()->LevelPatch.empty().addObject("guard",-1,90,0,0,false));
        assertThrows(IllegalStateException.class,()->LevelPatch.empty().addObject("guard",40,90,0,0,false).applyToObjects(List.of()));
        var valid=new ModContext("sample","s3k",ModAssetRoot.forTests("sample"));
        valid.registerObject("guard",(spawn,registry)->null);
        valid.decodedLevelPatch("gated",LevelPatch.empty().nativePlacements().whenInventory(objects->false)
                .addObject("guard",40,90,0,0,false));
        var source=new NativeFixture();
        var module=OwnedModServices.decorate(mock(GameModule.class,CALLS_REAL_METHODS),valid.freeze(),boundary());
        module.transformDecodedLevel(source);assertEquals(1,source.getObjects().size());
    }

    @Test void additionsUseEachHostEncodingAndSurviveNativeEnvelopeSaveReload() throws Exception {
        for (String game:List.of("s1","s2","s3k")) {
            com.openggf.level.objects.ObjectPlacementEncoding encoding=game.equals("s1")
                    ? new com.openggf.game.sonic1.Sonic1ObjectPlacementEncoding()
                    : new com.openggf.game.common.CommonObjectPlacementEncoding();
            for (int flips=0;flips<4;flips++) for (boolean tracked:List.of(false,true)) {
                var context=new ModContext("sample",game,ModAssetRoot.forTests("sample"));
                context.registerObject("guard",(spawn,registry)->null);
                context.decodedLevelPatch("extra",LevelPatch.empty().nativePlacements()
                        .addObject("guard",40,90,7,flips,tracked));
                var base=mock(GameModule.class,CALLS_REAL_METHODS);
                when(base.getObjectPlacementEncoding()).thenReturn(encoding);
                var module=OwnedModServices.decorate(base,context.freeze(),boundary());
                var source=new NativeFixture();
                assertSame(source,module.transformDecodedLevel(source));
                var added=source.getObjects().getLast();
                assertEquals(encoding.createKeyed(40,90,"sample:guard",7,flips,tracked,2),added,game);
                var decoded=saveAndReloadNativeEnvelope(added,game.equals("s1"));
                assertEquals(added.x(),decoded.x(),game);
                assertEquals(added.y(),decoded.y(),game);
                assertEquals(added.renderFlags(),decoded.renderFlags(),game);
                assertEquals(added.respawnTracked(),decoded.respawnTracked(),game);
                assertEquals(added.rawYWord(),decoded.rawYWord(),game);
                var reload=new NativeFixture();module.transformDecodedLevel(reload);
                assertEquals(added,reload.getObjects().getLast(),game);
                var editor=com.openggf.level.MutableLevel.snapshot(source);
                editor.replaceObjectSpawnsPersisted(List.of(added));
                var saveManager=new com.openggf.editor.persistence.EditorSaveManager(
                        saves.resolve(game+"-"+flips+"-"+tracked),"sample:guard"::equals,finding->fail(finding.toString()));
                var gameId=game.equals("s1") ? com.openggf.game.GameId.S1
                        : game.equals("s2") ? com.openggf.game.GameId.S2 : com.openggf.game.GameId.S3K;
                assertTrue(saveManager.save(gameId,encoding,0,0,editor).ok(),game);
                var fresh=com.openggf.level.MutableLevel.snapshot(new NativeFixture());
                var outcome=saveManager.tryApplyEdits(gameId,encoding,0,0,fresh);
                if(gameId==com.openggf.game.GameId.S3K) {
                    assertEquals(com.openggf.editor.persistence.EditorSaveManager.ApplyResult.UNSUPPORTED,outcome);
                } else {
                    assertEquals(com.openggf.editor.persistence.EditorSaveManager.ApplyResult.APPLIED,outcome,game);
                    assertEquals(List.of(added),fresh.getObjects(),game);
                }
            }
        }
    }

    /** Serialize the generated native envelope and read it through the actual game parsers. */
    private static ObjectSpawn saveAndReloadNativeEnvelope(ObjectSpawn spawn,boolean sonic1) {
        int table=sonic1 ? com.openggf.game.sonic1.constants.Sonic1Constants.OBJ_POS_INDEX_ADDR : 0;
        int start=sonic1 ? table+4 : 0;
        byte[] bytes=new byte[start+8];
        var buffer=java.nio.ByteBuffer.wrap(bytes);
        if (sonic1) buffer.putShort(table,(short)4);
        buffer.position(start);
        buffer.putShort((short)spawn.x()).putShort((short)spawn.rawYWord());
        buffer.put((byte)(spawn.objectId() | (sonic1 && spawn.respawnTracked() ? 0x80 : 0)));
        buffer.put((byte)spawn.subtype()).putShort((short)0xffff);
        var reader=new com.openggf.data.RomByteReader(bytes);
        return sonic1 ? new com.openggf.game.sonic1.Sonic1ObjectPlacement(reader).load(0,0).getFirst()
                : com.openggf.game.common.CommonPlacementParser.parseObjectRecords(reader,0).getFirst();
    }

    @Test void admittedAdditionsWithoutAHostEncodingFailBeforePublishingPlacements() {
        var patch=com.openggf.level.LevelPatchOwnership.bind("sample",LevelPatch.empty().nativePlacements()
                .addObject("guard",40,90,0,3,true),java.util.Set.of("sample:guard"));
        var source=new NativeFixture();var original=source.getObjects();
        var failure=assertThrows(IllegalStateException.class,
                ()->com.openggf.level.LevelPatchOwnership.applyDecoded(patch,source));
        assertTrue(failure.getMessage().contains("host game object placement encoding"));
        assertSame(original,source.getObjects());
        assertThrows(IllegalStateException.class,()->patch.applyToObjects(original));
        assertThrows(IllegalStateException.class,()->patch.apply(source));
        assertThrows(IllegalArgumentException.class,
                ()->LevelPatch.empty().addObject("guard",0xffff,90,0,0,false));
    }

    @Test void failedOwnerRetentionLeavesBothNativeTablesUnpublished() {
        var context=new ModContext("sample","s3k",ModAssetRoot.forTests("sample"));
        context.registerObject("guard",(spawn,registry)->null);
        context.decodedLevelPatch("placements",LevelPatch.empty().nativePlacements()
                .select(s->s.ownerModId()==null).bind("guard")
                .retainRings((rings,objects)->{
                    assertEquals("sample",OwnerCallbackScope.current());
                    throw new IllegalArgumentException("controlled retention failure");
                }));
        var module=OwnedModServices.decorate(mock(GameModule.class,CALLS_REAL_METHODS),context.freeze(),boundary());
        var source=new NativeFixture();var objects=source.getObjects();var rings=source.getRings();
        assertThrows(RuntimeException.class,()->module.transformDecodedLevel(source));
        assertSame(objects,source.getObjects());assertSame(rings,source.getRings());
        assertNull(OwnerCallbackScope.current());
    }
}
