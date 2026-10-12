package com.openggf.game;

import com.openggf.level.Level;
import com.openggf.game.save.SaveSnapshotProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.Map;
import com.openggf.level.objects.*;
import static org.junit.jupiter.api.Assertions.*;

class TestStandaloneGameSpec {
    @TempDir java.nio.file.Path saveRoot;
    private Level level() {
        return (Level)Proxy.newProxyInstance(Level.class.getClassLoader(),new Class<?>[]{Level.class},
                (proxy,method,args)->null);
    }

    @Test void declarativeObjectsShareKnownBrowseCreateDefinitionAndSnapshotFactories() {
        var calls=new java.util.ArrayList<ObjectRegistry>();
        var builder=StandaloneGameSpec.builder("sample")
                .zone("FIRST",new StandaloneGameSpec.Act(0x400,level(),0,0,MusicReference.stock(-1)));
        builder.object("sample:zeta",(spawn,registry)-> { calls.add(registry); return new StubObject(spawn); });
        builder.object("sample:alpha",(spawn,registry)->new StubObject(spawn));
        var spec=builder.build();
        builder.object("sample:later",(spawn,registry)->new StubObject(spawn));
        ObjectRegistry first=spec.createObjectRegistry(),second=spec.createObjectRegistry();
        assertNotSame(first,second);
        assertEquals(java.util.List.of("sample:alpha","sample:zeta"),first.browsableObjectKeys());
        assertThrows(UnsupportedOperationException.class,()->first.browsableObjectKeys().clear());
        for(String key:first.browsableObjectKeys()) assertTrue(first.hasObjectKey(key));
        assertFalse(first.hasObjectKey("sample:later"));
        var spawn=new ObjectSpawn(32,64,0,0,0,false,64,-1,"sample","sample:zeta");
        ObjectInstance original=first.create(spawn),recreated=second.create(spawn);
        assertNotSame(original,recreated);
        assertEquals(java.util.List.of(first,second),calls);
        assertSame(spawn,original.getSpawn());
        assertThrows(IllegalArgumentException.class,()->first.create(new ObjectSpawn(0,0,0,0,0,false,0,-1,"sample","sample:missing")));
        assertThrows(IllegalArgumentException.class,()->first.create(new ObjectSpawn(0,0,0,0,0,false,0,-1,"foreign","foreign:zeta")));
        assertThrows(IllegalArgumentException.class,()->first.create(new ObjectSpawn(0,0,0,0,0,false,0)));
    }

    @Test void declarativeObjectsRejectForeignDuplicateAndMixedExpertDeclarations() {
        ObjectFactory factory=(spawn,registry)->new StubObject(spawn);
        var builder=StandaloneGameSpec.builder("sample");
        assertThrows(IllegalArgumentException.class,()->builder.object("foreign:walker",factory));
        assertThrows(IllegalArgumentException.class,()->builder.object("walker",factory));
        assertThrows(NullPointerException.class,()->builder.object("sample:walker",null));
        builder.object("sample:walker",factory);
        assertThrows(IllegalArgumentException.class,()->builder.object("sample:walker",factory));
        assertThrows(IllegalStateException.class,()->builder.objects(()->null));
        var expert=StandaloneGameSpec.builder("sample").objects(()->null);
        assertThrows(IllegalStateException.class,()->expert.object("sample:walker",factory));
    }

    private static final class StubObject extends AbstractObjectInstance {
        private StubObject(ObjectSpawn spawn) { super(spawn,"stub"); }
        @Override public void update(int frame,com.openggf.game.PlayableEntity player) { }
        @Override public void appendRenderCommands(java.util.List<com.openggf.graphics.GLCommand> commands) { }
    }
    @Test void typedMultiActAssemblyRetainsMusicDescriptorsAndNoRomBoundary() throws Exception {
        Level first=level(),second=level();
        var module=StandaloneGameSpec.builder("sample")
                .zone("FIRST",new StandaloneGameSpec.Act(0x400,first,32,96,MusicReference.namespaced("sample","theme")),
                        new StandaloneGameSpec.Act(0x401,second,64,160,MusicReference.stock(-1)))
                .supportsSidekick(true).build().module();
        GameDataSource source=new GameDataSource() {
            public java.util.Optional<com.openggf.data.Rom> rom() { return java.util.Optional.empty(); }
            public java.io.InputStream openAsset(String name) { throw new UnsupportedOperationException(); }
            public String identity() { return "sample"; }
        };
        var game=module.createGame(source);
        assertSame(first,game.loadLevel(0x400)); assertSame(second,game.loadLevel(0x401));
        assertThrows(IOException.class,()->game.loadLevel(0x402));
        assertEquals(2,module.getZoneRegistry().getActCount(0));
        assertArrayEquals(new int[]{64,160},module.getZoneRegistry().getStartPosition(0,1));
        assertEquals(MusicReference.namespaced("sample","theme"),module.getLevelMusicReference(0,0));
        assertTrue(module.supportsSidekick());
        assertEquals(GameId.STANDALONE,module.getGameId());
        assertNull(game.getRom());
        assertThrows(UnsupportedOperationException.class,()->module.createGame((com.openggf.data.Rom)null));
        assertNull(module.getAudioProfile().createSmpsLoader(null));
        assertTrue(module.getAudioProfile().getSoundMap().isEmpty());
    }
    @Test void expertOverridesAndCustomSavePayloadRemainAvailable() {
        SaveSnapshotProvider saves=(reason,context)->Map.of("custom",42);
        var spec=StandaloneGameSpec.builder("sample")
                .zone("FIRST",new StandaloneGameSpec.Act(0x400,level(),0,0,MusicReference.stock(-1)))
                .saveSnapshotProvider(saves).build();
        var module=new AbstractStandaloneGameModule(spec) {
            @Override public boolean supportsSidekick() { return true; }
        };
        assertTrue(module.supportsSidekick());
        assertSame(saves,module.getSaveSnapshotProvider());
        assertEquals(Map.of("custom",42),module.getSaveSnapshotProvider().capture(null,null));
        assertThrows(IllegalArgumentException.class,()->StandaloneGameSpec.builder("sample").build());
        assertThrows(IllegalArgumentException.class,()->StandaloneGameSpec.builder("sample")
                .zone("FIRST",new StandaloneGameSpec.Act(0x400,level(),0,0,MusicReference.stock(-1)),
                        new StandaloneGameSpec.Act(0x400,level(),0,0,MusicReference.stock(-1))).build());
    }

    @Test void expertAudioOverridesRetainTheirLoaderAndRomInputs() {
        var audio=org.mockito.Mockito.mock(com.openggf.audio.GameAudioProfile.class);
        var rom=org.mockito.Mockito.mock(com.openggf.data.Rom.class);
        var loader=org.mockito.Mockito.mock(com.openggf.audio.smps.SmpsLoader.class);
        org.mockito.Mockito.when(audio.createSmpsLoader(rom)).thenReturn(loader);
        var module=StandaloneGameSpec.builder("sample")
                .zone("FIRST",new StandaloneGameSpec.Act(0x400,level(),0,0,MusicReference.stock(-1)))
                .audio(audio).build().module();
        assertSame(audio,module.getAudioProfile());
        assertSame(loader,module.getAudioProfile().createSmpsLoader(rom));
        org.mockito.Mockito.verify(audio).createSmpsLoader(rom);
    }

    @Test void customProgressSurvivesNewGameContinueAndCompletionSaveBoundaries() throws Exception {
        var score=new java.util.concurrent.atomic.AtomicInteger(12);
        var reasons=new java.util.ArrayList<com.openggf.game.save.SaveReason>();
        SaveSnapshotProvider custom=(reason,context)-> {
            reasons.add(reason);
            return Map.of("zone",context.currentZone(),"act",context.currentAct(),
                    "mainCharacter",context.selectedTeam().mainCharacter(),
                    "sidekicks",context.selectedTeam().sidekicks(),"clear",context.isClear(),
                    "customProgress",Map.of("score",score.get(),"inventory",java.util.List.of("key")));
        };
        var module=StandaloneGameSpec.builder("custom-sample")
                .zone("FIRST",new StandaloneGameSpec.Act(0x400,level(),32,96,MusicReference.stock(-1)))
                .saveSnapshotProvider(custom).build().module();
        var team=new com.openggf.game.save.SelectedTeam("custom-sample:hero",java.util.List.of());
        var session=com.openggf.game.save.SaveSessionContext.forSlot("custom-sample",1,team,0,0);
        var manager=new com.openggf.game.save.SaveManager(saveRoot);
        session.requestSave(com.openggf.game.save.SaveReason.NEW_SLOT_START,
                com.openggf.game.save.RuntimeSaveContext.forNewGame(session),module.getSaveSnapshotProvider(),manager);
        var first=manager.readSlotSummary("custom-sample",1);
        assertTrue(first.isLoadable());
        assertEquals(Map.of("score",12,"inventory",java.util.List.of("key")),first.payload().get("customProgress"));

        var continued=com.openggf.game.save.SaveSessionContext.forSlot("custom-sample",1,team,
                ((Number)first.payload().get("zone")).intValue(),((Number)first.payload().get("act")).intValue());
        score.set(((Number)((Map<?,?>)first.payload().get("customProgress")).get("score")).intValue()+8);
        continued.requestSave(com.openggf.game.save.SaveReason.EXISTING_SLOT_LOAD,
                com.openggf.game.save.RuntimeSaveContext.forNewGame(continued),module.getSaveSnapshotProvider(),manager);
        continued.markClear();
        continued.requestSave(com.openggf.game.save.SaveReason.PROGRESSION_SAVE,
                com.openggf.game.save.RuntimeSaveContext.forNewGame(continued),module.getSaveSnapshotProvider(),manager);
        var complete=manager.readSlotSummary("custom-sample",1);
        assertEquals(true,complete.payload().get("clear"));
        assertEquals(Map.of("score",20,"inventory",java.util.List.of("key")),complete.payload().get("customProgress"));
        assertEquals(java.util.List.of(com.openggf.game.save.SaveReason.NEW_SLOT_START,
                com.openggf.game.save.SaveReason.EXISTING_SLOT_LOAD,com.openggf.game.save.SaveReason.PROGRESSION_SAVE),reasons);
    }
}
