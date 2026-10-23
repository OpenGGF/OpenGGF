package com.openggf.editor.persistence;

import com.openggf.data.Rom;
import com.openggf.game.*;
import com.openggf.game.modzone.*;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic1.*;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.io.*;
import com.openggf.level.*;
import com.openggf.level.objects.*;
import com.openggf.level.rings.RingSpawn;
import com.openggf.mods.code.*;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.tests.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class TestEditorHostExports {
    @TempDir Path temp;
    @AfterEach void reset() { TestEnvironment.resetAll(); }

    @ParameterizedTest
    @EnumSource(value=GameId.class,names={"S1","S2","S3K"})
    void explicitGameExportLoadsNativeCollisionSpawnsAndRomArt(GameId game) throws Exception {
        java.io.File file=switch(game) {
            case S1 -> RomTestUtils.ensureSonic1RomAvailable();
            case S2 -> RomTestUtils.ensureSonic2RomAvailable();
            case S3K -> RomTestUtils.ensureSonic3kRomAvailable();
            default -> throw new AssertionError();
        };
        assumeTrue(file!=null,"A configured "+game+" ROM is required");
        try(Rom rom=new Rom()) {
            assertTrue(rom.open(file.getAbsolutePath()));
            TestEnvironment.configureRomFixture(rom);
            GameModule module=GameServices.module(); module.createGame(GameServices.worldSession().getDataSource());
            var fixture=new Fixture(game);
            fixture.getBlock(0).restoreState(fixture.getBlock(1).saveState());
            fixture.getMap().setValue(0,0,0,(byte)0);
            var source=MutableLevel.snapshot(fixture);
            var request=request(game,temp.resolve(game.code()));
            var exporter=new FullLevelExporter(new ModLevelExportStagingValidator(module));
            ModZoneLevelData memory=exporter.prepareHostData(source,request);
            assertEquals(0xA040,memory.objects().getFirst().rawYWord());
            exporter.export(source,request);
            assertEquals(game==GameId.S3K,!Files.exists(request.outputDirectory().resolve("palettes.bin")));
            ModLevelDefinition definition;
            try(ModAssetRoot assets=ModAssetRoot.directory(temp,request.outputDirectory(),
                    ModInputLimits.production(),DirectoryAccess.TEST)) {
                definition=ModLevelDefinitionParser.read(assets,new BakedLevelRef("level.json"));
            }
            assertEquals(game==GameId.S3K?2:1,definition.formatVersion());
            ModZoneLevelData disk=ModZoneLoader.prepareHostData(definition);
            assertArrayEquals(memory.patternBytes(),disk.patternBytes());
            assertEquals(memory.objects(),disk.objects());
            assertEquals(memory.hostMetadata(),disk.hostMetadata());
            assertEquals(memory.paletteClaims(),disk.paletteClaims());
            assertArrayEquals(memory.paletteLines(),disk.paletteLines());
            Level loaded=module.getModZoneAdapter().load("editor",disk);
            assertEquals(game==GameId.S1?256:128,loaded.getBlockPixelSize());
            assertArrayEquals(source.getBlock(0).saveState(),loaded.getBlock(0).saveState(),
                    "Parser-valid occupied block zero must survive the actual host decoder");
            assertEquals(game==GameId.S1?0x4040:0xA040,loaded.getObjects().getFirst().rawYWord());
            assertEquals(1,loaded.getRings().size());
            assertTrue(loaded.getRingSpriteSheet().getPatterns().length>0);
            LevelManager manager=TestEnvironment.activeGameplayMode().getLevelManager(); manager.setLevel(loaded);
            var floor=ObjectTerrainUtils.checkFloorDist(manager,8,8);
            assertTrue(floor.foundSurface()); assertTrue(floor.hasCollision());
            assertFalse(module.createObjectRegistry().create(loaded.getObjects().getFirst()) instanceof PlaceholderObjectInstance);
            if(game==GameId.S1) {
                var mapping=((RingObjectPlacementMapping)loaded).ringObjectPlacementMapping();
                assertEquals(1,mapping.size());
                assertEquals(loaded.getRings(),mapping.values().iterator().next());
                ObjectSpawn backing=mapping.keySet().iterator().next();
                assertInstanceOf(com.openggf.game.sonic1.objects.Sonic1RingInstance.class,
                        module.createObjectRegistry().create(backing));
                var art=module.getObjectArtProvider(); art.loadArtForZone(-1);
                assertNotNull(art.getRenderer(ObjectArtKeys.MONITOR));
            }
        }
    }

    @Test void authoredS3kFreshTerrainHandoffPreservesBakedArtWithoutStockQueueSubmission() throws Exception {
        java.io.File file=RomTestUtils.ensureSonic3kRomAvailable();
        assumeTrue(file!=null,"Configured S3K ROM required for authored runtime handoff");
        try(Rom rom=new Rom()) {
            assertTrue(rom.open(file.getAbsolutePath())); TestEnvironment.configureRomFixture(rom);
            GameModule module=GameServices.module(); var nativeGame=module.createGame(GameServices.worldSession().getDataSource());
            var data=new FullLevelExporter(new ModLevelExportStagingValidator(module)).prepareHostData(
                    MutableLevel.snapshot(new Fixture(GameId.S3K)),request(GameId.S3K,temp.resolve("fresh-art")));
            var loaded=assertInstanceOf(com.openggf.game.sonic3k.Sonic3kLevel.class,
                    module.getModZoneAdapter().load("editor",data));
            assertFalse(loaded.hasStockRomZoneIdentity());
            GameServices.level().setLevel(loaded);
            var timingBefore=GameServices.hardwareTiming().capture();
            var art=com.openggf.game.sonic3k.resources.S3kRuntimeArtCoordinator.current();
            var artBefore=art.capture();
            byte[] pixelsBefore=new byte[64]; loaded.getPattern(0).copyInto(pixelsBefore,0);
            assertDoesNotThrow(()->nativeGame.queueFreshLevelRuntimeArt(0x400));
            assertEquals(timingBefore,GameServices.hardwareTiming().capture());
            assertEquals(artBefore,art.capture(),"Authored terrain has no stock KosM submission or deferred handoff");
            byte[] pixelsAfter=new byte[64]; loaded.getPattern(0).copyInto(pixelsAfter,0);
            assertArrayEquals(pixelsBefore,pixelsAfter);
        }
    }

    @ParameterizedTest
    @EnumSource(value=GameId.class,names={"S1","S2","S3K"})
    void authoredGoalRunsNativeResultsAndReturnsToItsHost(GameId game) throws Exception {
        java.io.File file=switch(game) {
            case S1 -> RomTestUtils.ensureSonic1RomAvailable();
            case S2 -> RomTestUtils.ensureSonic2RomAvailable();
            case S3K -> RomTestUtils.ensureSonic3kRomAvailable();
            default -> throw new AssertionError();
        };
        assumeTrue(file!=null,"Configured ROM required for native authored goal");
        try(Rom rom=new Rom()) {
            assertTrue(rom.open(file.getAbsolutePath())); TestEnvironment.configureRomFixture(rom);
            GameModule module=GameServices.module(); module.createGame(GameServices.worldSession().getDataSource());
            Fixture fixture=new Fixture(game);
            fixture.setObjects(List.of(game!=GameId.S3K
                    ? module.getObjectPlacementEncoding().create(32,64,0x0D,0,0,true,9)
                    : module.getObjectPlacementEncoding().createKeyed(32,64,
                            com.openggf.game.sonic3k.objects.S3kAuthoredGoalInstance.OBJECT_KEY,0,0,true,9)));
            var data=new FullLevelExporter(new ModLevelExportStagingValidator(module)).prepareHostData(
                    MutableLevel.snapshot(fixture),request(game,temp.resolve("finish")));
            Level loaded=module.getModZoneAdapter().load("editor",data);
            LevelManager manager=TestEnvironment.activeGameplayMode().getLevelManager(); manager.setLevel(loaded);
            var player=new TestablePlayableSprite("sonic",(short)0,(short)0);
            com.openggf.sprites.NativePositionOps.writeXPosResetSubpixel(player,32);
            com.openggf.sprites.NativePositionOps.writeYPosResetSubpixel(player,64);
            player.setAirForTest(false);
            var camera=new com.openggf.camera.Camera(); camera.setX((short)0); camera.setY((short)0);
            camera.setMaxX((short)0); camera.setMaxY((short)0);
            var state=new GameStateManager(); boolean[] returned={false};
            var gameplay=TestEnvironment.activeGameplayMode();
            gameplay.beginGameplayRun(com.openggf.game.session.GameplayRunPolicy.isolatedAct(),0,0);
            var fade=new com.openggf.graphics.FadeManager();
            var reader=com.openggf.data.RomByteReader.fromRom(rom);
            ObjectManager[] objects={null};
            var services=new StubObjectServices() {
                @Override public ObjectManager objectManager() { return objects[0]; }
                @Override public LevelManager levelManager() { return manager; }
                @Override public Level currentLevel() { return loaded; }
                @Override public int romZoneId() { return 64; }
                @Override public com.openggf.camera.Camera camera() { return camera; }
                @Override public GameStateManager gameState() { return state; }
                @Override public GameModule gameModule() { return module; }
                @Override public Rom rom() { return rom; }
                @Override public com.openggf.data.RomByteReader romReader() { return reader; }
                @Override public RuntimeArtCoordinator runtimeArtCoordinator() { return gameplay.runtimeArtCoordinator(); }
                @Override public com.openggf.game.timing.HardwareTimingService hardwareTiming() { return gameplay.hardwareTiming(); }
                @Override public com.openggf.configuration.SonicConfigurationService configuration() { return GameServices.configuration(); }
                @Override public com.openggf.game.session.GameplayRunPolicy runPolicy() {
                    return gameplay.getRunPolicy();
                }
                @Override public void requestHostReturn() { manager.requestHostReturn(); }
                @Override public void advanceToNextLevel() {
                    try { manager.advanceToNextLevel(); }
                    catch(java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
                }
                @Override public com.openggf.graphics.FadeManager fadeManager() { return fade; }
                @Override public com.openggf.game.resources.NativeFadeLifecycle nativeFadeLifecycle() {
                    return gameplay.plcFrameLifecycle();
                }
                @Override public <T> T gameService(Class<T> type) { return module.getGameService(type); }
            }.withPlayerQuery(new ObjectPlayerQuery(()->player,List::of));
            objects[0]=new ObjectManager(List.of(),module.createObjectRegistry(),0,null,null,null,camera,services);
            var registry=module.createObjectRegistry();
            var goal=ObjectConstructionContext.construct(services,()->registry.create(loaded.getObjects().getFirst()));
            objects[0].addDynamicObject(goal);
            boolean sawResults=false;
            for(int frame=0;frame<3000 && !returned[0];frame++) {
                if(!manager.isLevelInactiveForTransition())
                    for(ObjectInstance object:List.copyOf(objects[0].getActiveObjects()))
                        if(!object.isDestroyed()) object.update(frame,player);
                sawResults |= objects[0].getActiveObjects().stream().anyMatch(object ->
                        object instanceof com.openggf.game.sonic1.objects.Sonic1ResultsScreenObjectInstance
                        || object instanceof com.openggf.game.sonic3k.objects.S3kResultsScreenObjectInstance
                        || object instanceof com.openggf.game.sonic2.objects.ResultsScreenObjectInstance);
                for(ObjectInstance object:List.copyOf(objects[0].getActiveObjects()))
                    if(object.isDestroyed()) objects[0].removeDynamicObject(object);
                if(game!=GameId.S3K && frame>180)
                    com.openggf.sprites.NativePositionOps.writeXPosResetSubpixel(player,0x140);
                if(game==GameId.S1) {
                    var plc=module.getGameService(com.openggf.game.sonic1.resources.Sonic1PlcService.class);
                    plc.serviceLevelVBlank();
                    plc.prepare();
                } else if(game==GameId.S2) {
                    var plc=module.getGameService(com.openggf.game.sonic2.resources.Sonic2PlcService.class);
                    plc.serviceLevelVBlank();
                    plc.prepare();
                }
                for(var boundary:com.openggf.game.timing.HardwareServiceBoundary.values())
                    gameplay.serviceHardwareTimingBoundary(boundary);
                fade.update();
                returned[0] |= manager.consumeHostReturnRequest();
            }
            assertTrue(sawResults,"The authored goal must allocate the native results graph");
            assertTrue(returned[0],"Native results must finish through the isolated host return contract");
            assertEquals(0,manager.getCurrentZone(),"Completion must return before loading a stock successor");
            if(game==GameId.S3K) {
                assertTrue(registry.browsableObjectKeys().contains(
                        com.openggf.game.sonic3k.objects.S3kAuthoredGoalInstance.OBJECT_KEY));
                assertInstanceOf(com.openggf.game.sonic3k.objects.Sonic3kPlacedRingObjectInstance.class,
                        registry.create(module.getObjectPlacementEncoding().create(32,64,0,0,0,true,10)));
            }
        }
    }

    @Test void boundedMetadataTokenLimitIsSharedByMemoryAndDisk() throws Exception {
        var fixture=new Fixture(GameId.S2);
        var exporter=new FullLevelExporter(new ModLevelExportStagingValidator(new Sonic2GameModule()));
        var request=request(GameId.S2,temp.resolve("ring-heavy"));
        fixture.setRings(java.util.stream.IntStream.range(0,10_000)
                .mapToObj(id->new RingSpawn(id,64,id)).toList());
        assertDoesNotThrow(()->exporter.prepareHostData(MutableLevel.snapshot(fixture),request),
                "Large metadata below the canonical token cap remains usable");
        fixture.setRings(java.util.stream.IntStream.range(0,20_000)
                .mapToObj(id->new RingSpawn(id,64,id)).toList());
        var source=MutableLevel.snapshot(fixture);
        assertAll(
                ()->assertThrows(java.io.IOException.class,()->exporter.prepareHostData(source,request)),
                ()->assertThrows(java.io.IOException.class,()->exporter.export(source,request)),
                ()->assertFalse(Files.exists(request.outputDirectory())),
                ()->assertFalse(Files.exists(temp.resolve("ring-heavy.export-lock"))));
    }

    @Test void malformedCollisionProfileFailsBothPublicationPaths() {
        var source=MutableLevel.snapshot(new Fixture(GameId.S2));
        source.setSolidTile(0,new SolidTile(0,new byte[16],new byte[15],(byte)0));
        var exporter=new FullLevelExporter(new ModLevelExportStagingValidator(new Sonic2GameModule()));
        var request=request(GameId.S2,temp.resolve("short-width"));
        assertAll(
                ()->assertThrows(IllegalArgumentException.class,()->exporter.prepareHostData(source,request)),
                ()->assertThrows(IllegalArgumentException.class,()->exporter.export(source,request)),
                ()->assertFalse(Files.exists(request.outputDirectory())));
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "S1,32767,32512", "S1,32768,32512", "S1,32,65500",
            "S2,32767,32512", "S2,32768,32512", "S2,32,65500"})
    void nativeSignpostUsesUnsignedWordActivationAndWalkOff(GameId game,int goalX,int cameraMaxX) {
        TestEnvironment.resetAll();
        GameModule module=game==GameId.S1?new Sonic1GameModule():new Sonic2GameModule();
        var player=new TestablePlayableSprite("sonic",(short)0,(short)0);
        player.setGameRulesForTest(module.getRules()); player.setAirForTest(false);
        com.openggf.sprites.NativePositionOps.writeXPosResetSubpixel(player,goalX);
        com.openggf.sprites.NativePositionOps.writeYPosResetSubpixel(player,64);
        var camera=new com.openggf.camera.Camera(); camera.setMaxX((short)cameraMaxX);
        var state=new GameStateManager();
        ObjectManager[] ref={null};
        var services=new StubObjectServices() {
            @Override public ObjectManager objectManager() { return ref[0]; }
            @Override public com.openggf.camera.Camera camera() { return camera; }
            @Override public GameStateManager gameState() { return state; }
            @Override public GameModule gameModule() { return module; }
            @Override public com.openggf.configuration.SonicConfigurationService configuration() {
                return GameServices.configuration();
            }
        };
        var registry=module.createObjectRegistry();
        ref[0]=new ObjectManager(List.of(),registry,0,null,null,null,camera,services);
        var spawn=module.getObjectPlacementEncoding().create(goalX,64,0x0D,0,0,true,9);
        var sign=ObjectConstructionContext.construct(services,()->registry.create(spawn));
        ref[0].addDynamicObject(sign); sign.update(0,player);
        assertTrue(state.isActCompletionSignalActive(),"Unsigned native X must activate the signpost");
        for(int frame=1;frame<300;frame++) sign.update(frame,player);
        java.util.function.BooleanSupplier results=()->ref[0].getActiveObjects().stream().anyMatch(object ->
                object instanceof com.openggf.game.sonic1.objects.Sonic1ResultsScreenObjectInstance
                || object instanceof com.openggf.game.sonic2.objects.ResultsScreenObjectInstance);
        assertFalse(results.getAsBoolean());
        int threshold=(cameraMaxX+0x128)&0xFFFF;
        com.openggf.sprites.NativePositionOps.writeXPosResetSubpixel(player,(threshold-1)&0xFFFF);
        sign.update(300,player); assertFalse(results.getAsBoolean(),"Below unsigned cmp.w threshold");
        com.openggf.sprites.NativePositionOps.writeXPosResetSubpixel(player,threshold);
        sign.update(301,player); assertTrue(results.getAsBoolean(),"Native unsigned walk-off must allocate results");
    }

    @Test void missingKeyDependenciesFailMemoryAndDiskBeforePublication() throws Exception {
        var fixture=new Fixture(GameId.S2);
        fixture.setObjects(List.of(new com.openggf.game.common.CommonObjectPlacementEncoding()
                .createKeyed(32,64,"creator:missing",0,0,true,9)));
        var source=MutableLevel.snapshot(fixture);
        var request=request(GameId.S2,temp.resolve("missing-key"));
        var exporter=new FullLevelExporter(new ModLevelExportStagingValidator(new Sonic2GameModule()));
        assertTrue(assertThrows(java.io.IOException.class,()->exporter.prepareHostData(source,request))
                .getMessage().contains("creator:missing"));
        assertThrows(java.io.IOException.class,()->exporter.export(source,request));
        assertFalse(Files.exists(request.outputDirectory()));
        var enabled=new com.openggf.game.patch.DelegatingGameModule(new Sonic2GameModule(),"keys") {
            @Override public ObjectRegistry createObjectRegistry() {
                return new ModDecoratedObjectRegistry(super.createObjectRegistry(),new ModObjectKeyRegistry(List.of(
                        new ModObjectKeyRegistry.Registration("creator","creator:missing",(spawn,registry)->null))));
            }
        };
        assertDoesNotThrow(()->new FullLevelExporter(new ModLevelExportStagingValidator(enabled))
                .prepareHostData(source,request));
    }

    @Test void s3kClaimsOnlyReachableColorsAndRejectsCharacterAndHudConflicts() throws Exception {
        var fixture=new Fixture(GameId.S3K);
        var exporter=new FullLevelExporter(new ModLevelExportStagingValidator());
        ModZoneLevelData data=exporter.prepareHostData(MutableLevel.snapshot(fixture),request(GameId.S3K,temp.resolve("valid")));
        assertEquals(List.of(new ModPaletteClaim(2,0,0),new ModPaletteClaim(2,1,0)),data.paletteClaims());
        fixture.getChunk(0).setPatternDesc(0,0,new PatternDesc(0));
        assertTrue(assertThrows(IllegalArgumentException.class,()->exporter.export(MutableLevel.snapshot(fixture),
                request(GameId.S3K,temp.resolve("character")))).getMessage().contains("line 0"));
        assertFalse(Files.exists(temp.resolve("character")));
        fixture.getChunk(0).setPatternDesc(0,0,new PatternDesc(0x2000));
        assertThrows(ModZoneRegistrationException.class,()->exporter.export(MutableLevel.snapshot(fixture),
                request(GameId.S3K,temp.resolve("hud"))));
        assertFalse(Files.exists(temp.resolve("hud")));
    }

    @Test void s1RejectsLoopCollisionRemapsAndStockBossesWithoutPublishing() {
        var exporter=new FullLevelExporter(new ModLevelExportStagingValidator());
        Fixture loop=new Fixture(GameId.S1) {
            @Override public int resolveCollisionBlockIndex(int block,int x,int y) { return block+1; }
        };
        assertTrue(assertThrows(IllegalArgumentException.class,()->exporter.export(MutableLevel.snapshot(loop),
                request(GameId.S1,temp.resolve("loop")))).getMessage().contains("loop"));
        Fixture boss=new Fixture(GameId.S1);
        boss.setObjects(List.of(new Sonic1ObjectPlacementEncoding().create(32,64,0x3D,0,0,true,9)));
        assertThrows(ModZoneRegistrationException.class,()->exporter.export(MutableLevel.snapshot(boss),
                request(GameId.S1,temp.resolve("boss"))));
        assertFalse(Files.exists(temp.resolve("boss")));
    }

    @Test void wrongDestinationHostRejectsStagingAndPreservesTheTarget() {
        var exporter=new FullLevelExporter(new ModLevelExportStagingValidator(new Sonic2GameModule()));
        assertThrows(ModZoneRegistrationException.class,()->exporter.export(MutableLevel.snapshot(new Fixture(GameId.S3K)),
                request(GameId.S3K,temp.resolve("wrong"))));
        assertFalse(Files.exists(temp.resolve("wrong")));
    }

    private FullLevelExporter.ExportRequest request(GameId game,Path directory) {
        return new FullLevelExporter.ExportRequest(directory,"Authored",64,1024,32,64,
                new FullLevelExporter.ExportMusic.Stock(1),game,game==GameId.S3K?ModObjectZoneSet.S3KL:null);
    }

    private static class Fixture extends AbstractLevel {
        final int side;
        Fixture(GameId game) {
            super(64); side=game==GameId.S1?16:8;
            patterns=new Pattern[]{new Pattern()}; patternCount=1; patterns[0].setPixel(0,0,(byte)1);
            chunks=new Chunk[]{new Chunk()}; chunkCount=1;
            for(int y=0;y<2;y++) for(int x=0;x<2;x++) chunks[0].setPatternDesc(x,y,new PatternDesc(0x4000));
            chunks[0].setSolidTileIndex(1); chunks[0].setSolidTileAltIndex(1);
            blocks=new Block[]{new Block(side),new Block(side)}; blockCount=2;
            for(int y=0;y<side;y++) for(int x=0;x<side;x++) blocks[1].setChunkDesc(x,y,new ChunkDesc(0xF000));
            byte[] heights=new byte[16]; Arrays.fill(heights,(byte)16);
            solidTiles=new SolidTile[]{new SolidTile(0,new byte[16],new byte[16],(byte)0),
                    new SolidTile(1,heights,heights.clone(),(byte)0)}; solidTileCount=2;
            map=new com.openggf.level.Map(2,1,1,new byte[]{1,0});
            palettes=new Palette[]{new Palette(),new Palette(),new Palette(),new Palette()};
            var codec=game==GameId.S1?new Sonic1ObjectPlacementEncoding():new com.openggf.game.common.CommonObjectPlacementEncoding();
            objects=List.of(codec.create(32,64,game==GameId.S3K?1:0x26,0,1,true,9));
            rings=List.of(new RingSpawn(48,64,11)); minX=0;maxX=side*16-1;minY=0;maxY=side*16-1;
        }
        void setObjects(List<ObjectSpawn> values) { objects=values; }
        void setRings(List<RingSpawn> values) { rings=values; }
        @Override public int getChunksPerBlockSide() { return side; }
        @Override public int getBlockPixelSize() { return side*16; }
    }
}
