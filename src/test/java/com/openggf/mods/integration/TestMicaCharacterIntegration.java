package com.openggf.mods.integration;

import com.openggf.configuration.*;
import com.openggf.game.*;
import com.openggf.game.launch.*;
import com.openggf.game.patch.*;
import com.openggf.game.save.SaveManager;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.RenderPriority;
import com.openggf.graphics.SpritePresentation;
import com.openggf.level.render.SpritePresentationRenderer;
import com.openggf.sprites.render.PlayerSpriteRenderer;
import com.openggf.mods.*;
import com.openggf.mods.code.*;
import com.openggf.io.ModInputLimits;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.SecondaryAbility;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.TestSessionOutputPaths;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

@Isolated
class TestMicaCharacterIntegration {
    private static final String CODE = "mica-and-talon:mica";
    @TempDir Path temp;

    @AfterEach void cleanup() { TestEnvironment.resetAll(); }

    @Test @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_1)
    void sonic1() throws Exception { acceptance(new com.openggf.game.sonic1.Sonic1GameModule(), "s1", 1, 0); }
    @Test @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_2)
    void sonic2() throws Exception { acceptance(new Sonic2GameModule(), "s2", 1, 1); }
    @Test @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_3K)
    void sonic3k() throws Exception { acceptance(new com.openggf.game.sonic3k.Sonic3kGameModule(), "s3k", 1, 0); }

    @Test @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_3K)
    void titleRouteKeepsExplicitDevelopmentCharacter() throws Exception {
        Path input = temp.resolve("title.txt");
        com.openggf.tools.InputLogAuthorTool.main(new String[]{"--inline",
                "121 -; 4 S; 18 -; 5 S; 25 -; 5 S; 200 -; 1 D; 60 -; 1 S; 240 -",
                "--out", input.toString()});
        String previous = System.getProperty("openggf.saveRoot");
        System.setProperty("openggf.saveRoot", temp.resolve("capture-saves").toString());
        try {
            com.openggf.tools.GameplayCaptureTool.run(
                    com.openggf.tools.GameplayCaptureTool.Arguments.parse(new String[]{
                            "--game", "s3k", "--zone", "aiz", "--act", "1", "--mod", buildSample().toString(),
                            "--main", CODE, "--extra-teams", CODE, "--title-screen", "--interactive-pacing",
                            "--input", input.toString(), "--every", "400", "--no-video",
                            "--out-dir", temp.resolve("capture").toString()}));
            var rows = Files.readAllLines(temp.resolve("capture/state.csv"));
            assertTrue(rows.stream().anyMatch(row -> row.contains(",DATA_SELECT,")));
            // Session teardown retires the world; inspect the final native mapping frame.
            // MICA's six WAIT frames are 0..5; stock Sonic's idle is 186.
            var last = rows.getLast().split(",");
            assertEquals("LEVEL", last[19]);
            assertTrue(Integer.parseInt(last[12]) < 6, rows.getLast());
        } finally {
            if (previous == null) System.clearProperty("openggf.saveRoot");
            else System.setProperty("openggf.saveRoot", previous);
        }
    }

    @Test @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_1)
    void micaMazeCapture() throws Exception { captureStage("s1", false); }
    @Test @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_2)
    void micaHalfPipeCapture() throws Exception { captureStage("s2", false); }
    @Test @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_3K)
    void micaBlueSphereCapture() throws Exception { captureStage("s3k", false); }
    @Test @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_3K)
    void micaSlotsCapture() throws Exception { captureStage("s3k", true); }

    @Test @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_1)
    void micaS1EndingCapture() throws Exception {
        Path output=Path.of("/tmp/mica-endings-s1s2-20261108/s1");
        Files.createDirectories(output);
        var settings=new com.openggf.tools.GameplayCaptureSession.Settings(320,CODE,"","off",null,null,null);
        try(var session=new com.openggf.tools.GameplayCaptureSession(settings)) {
            session.applyMod(buildSample());
            session.boot(com.openggf.tests.RomTestUtils.ensureSonic1RomAvailable().toPath(),7,0,settings);
            for(int emerald=0;emerald<6;emerald++) GameServices.gameState().markEmeraldCollected(emerald);
            var csv=new StringBuilder(com.openggf.tools.GameplayCaptureSession.stateHeader()).append('\n');
            com.openggf.game.sonic1.objects.Sonic1EndingSonicObjectInstance endingActor=null;
            for(int i=0;i<1400;i++) {
                session.step(null); session.renderFrame();
                csv.append(session.stateLine(i,null)).append('\n');
                var live=GameServices.level().getObjectManager().activeObjectsOfType(
                        com.openggf.game.sonic1.objects.Sonic1EndingSonicObjectInstance.class);
                if(!live.isEmpty()) endingActor=live.getFirst();
                if(i==420 || i==540 || i==900 || i==1100 || i==1399)
                    com.openggf.graphics.ScreenshotCapture.savePNG(session.render(),output.resolve("frame-"+i+".png"));
            }
            Files.writeString(output.resolve("state.csv"),csv);
            var set=com.openggf.game.presentation.EndingCharacterPresentation.get(EndingSpriteSet.Surface.S1_ENDING);
            assertNotNull(set); assertEquals(8,set.sprites().frames().size());
            assertNotNull(endingActor);
            var renderer=(PlayerSpriteRenderer)privateField(endingActor,"characterRenderer");
            assertNotNull(renderer);
            var actual=(com.openggf.sprites.art.SpriteArtSet)privateField(renderer,"artSet");
            assertEquals(com.openggf.game.presentation.StageSpriteCompiler.compile(set.sprites(),null).mappingFrames(),
                    actual.mappingFrames());
            assertTrue(renderer.getRenderContext().getEffectivePaletteLine(0)>3);
            var expected=com.openggf.game.presentation.StageSpriteCompiler.compile(set.sprites(),null);
            assertEquals(expected.artTiles().length,actual.artTiles().length);
            for(int t=0;t<expected.artTiles().length;t++) for(int y=0;y<8;y++) for(int x=0;x<8;x++)
                assertEquals(expected.artTiles()[t].getPixel(x,y),actual.artTiles()[t].getPixel(x,y));
            for(int i=0;i<16;i++) {
                var color=renderer.getRenderContext().getPalette(0).getColor(i);
                assertEquals(set.rgb()[i],((color.r&255)<<16)|((color.g&255)<<8)|(color.b&255));
            }
        }
    }

    private void captureStage(String game, boolean bonus) throws Exception {
        Path jar=buildSample();
        var settings=new com.openggf.tools.GameplayCaptureSession.Settings(320,CODE,"","off",null,null,null,null,false,false);
        Path rom=switch(game) {
            case "s1" -> com.openggf.tests.RomTestUtils.ensureSonic1RomAvailable().toPath();
            case "s2" -> com.openggf.tests.RomTestUtils.ensureSonic2RomAvailable().toPath();
            default -> com.openggf.tests.RomTestUtils.ensureSonic3kRomAvailable().toPath();
        };
        Path output=Path.of(System.getProperty("mica.stage.captureRoot","target/mica-stage-captures")).resolve(bonus?"s3k-slots":game);
        Files.createDirectories(output);
        String old=System.getProperty("openggf.saveRoot");
        System.setProperty("openggf.saveRoot",temp.resolve("capture-saves").toString());
        try(var session=new com.openggf.tools.GameplayCaptureSession(settings)) {
            session.applyMod(jar);
            session.boot(rom,0,0,settings);
            for(int i=0;i<240;i++) { session.step(null); session.renderFrame(); }
            if(bonus) GameServices.level().requestBonusStageEntry(BonusStageType.SLOT_MACHINE);
            else session.loop().enterSpecialStage();
            var csv=new StringBuilder(com.openggf.tools.GameplayCaptureSession.stateHeader()).append('\n');
            for(int i=0;i<240;i++) {
                session.step(null); session.renderFrame();
                csv.append(session.stateLine(i,null)).append('\n');
                if(i==120 || i==180 || i==239)
                    com.openggf.graphics.ScreenshotCapture.savePNG(session.render(),output.resolve("frame-"+i+".png"));
            }
            Files.writeString(output.resolve("state.csv"),csv);
            assertEquals(bonus?GameMode.BONUS_STAGE:GameMode.SPECIAL_STAGE,session.loop().getCurrentGameMode());
            var definition=com.openggf.game.presentation.StageCharacterPresentation.activeDefinition();
            assertNotNull(definition);
            var palette=definition.paletteSupplier().load(CODE);
            if(!bonus) {
                var provider=session.loop().getActiveSpecialStageProvider();
                var manager=privateField(provider,"manager");
                if(game.equals("s1")) {
                    var actual=(PlayerSpriteRenderer)privateField(manager,"sonicSpriteRenderer");
                    assertTrue(palette.dataEquals(actual.getRenderContext().getPalette(0)));
                    var expected=com.openggf.game.presentation.StageCharacterPresentation.playable(
                            new com.openggf.game.sonic1.Sonic1PlayerArt(com.openggf.data.RomByteReader.fromRom(GameServices.rom().getRom())).loadSonic());
                    var actualArt=(com.openggf.sprites.art.SpriteArtSet)privateField(actual,"artSet");
                    assertEquals(expected.mappingFrames(),actualArt.mappingFrames());
                } else {
                    var actual=privateField(manager,"renderer");
                    if(game.equals("s2")) {
                        assertNotNull(privateField(actual,"characterStageArt"));
                        int line=(int)privateField(actual,"characterStagePalette");
                        assertTrue(line>3);
                        assertTrue(com.openggf.graphics.RenderContext.getSidekickContexts().stream()
                                .anyMatch(c -> c.getEffectivePaletteLine(0)==line && palette.dataEquals(c.getPalette(0))));
                    } else {
                        var player=(PlayerSpriteRenderer)privateField(actual,"characterStageRenderer");
                        assertNotNull(player);
                        assertTrue(palette.dataEquals(player.getRenderContext().getPalette(0)));
                    }
                }
            } else {
                var player=GameServices.sprites().getMainPlayable();
                assertNotNull(player);
                assertInstanceOf(com.openggf.game.sonic3k.bonusstage.slots.S3kSlotBonusPlayer.class,player);
                assertTrue(palette.dataEquals(player.getSpriteRenderer().getRenderContext().getPalette(0)));
                var actualArt=(com.openggf.sprites.art.SpriteArtSet)privateField(player.getSpriteRenderer(),"artSet");
                var expected=com.openggf.game.presentation.StageSpriteCompiler.compile(
                        definition.presentationSupplier().load(CODE).stages().get(StageSpriteSet.Surface.S3K_SLOT_MACHINE),null);
                for(int id=150;id<=154;id++) {
                    var wanted=expected.mappingFrames().get(id).pieces();
                    var actual=actualArt.mappingFrames().get(id).pieces();
                    assertEquals(wanted.size(),actual.size());
                    for(int i=0;i<wanted.size();i++) {
                        assertFalse(actual.get(i).priority(),"slot art stays behind native foreground glass");
                        assertEquals(wanted.get(i).xOffset(),actual.get(i).xOffset());
                        assertEquals(wanted.get(i).yOffset(),actual.get(i).yOffset());
                        assertEquals(wanted.get(i).tileIndex(),actual.get(i).tileIndex());
                    }
                }
            }
            var presentation=definition.presentationSupplier().load(CODE);
            for(var surface:StageSpriteSet.Surface.values()) {
                var set=presentation.stages().get(surface);
                assertNotNull(set);
                var art=com.openggf.game.presentation.StageSpriteCompiler.compile(set,null);
                for(var entry:set.frames().entrySet()) {
                    var frame=entry.getValue();
                    var cue=art.dplcFrames().get(entry.getKey()).requests().getFirst();
                    byte[] decoded=new byte[frame.image().width()*frame.image().height()];
                    for(var piece:art.mappingFrames().get(entry.getKey()).pieces())
                        for(int x=0;x<piece.widthTiles()*8;x++) for(int y=0;y<piece.heightTiles()*8;y++) {
                            int dx=piece.xOffset()+frame.hotspotX()+x, dy=piece.yOffset()+frame.hotspotY()+y;
                            if(dx<frame.image().width() && dy<frame.image().height()) {
                                int tile=cue.startTile()+piece.tileIndex()+(x/8)*piece.heightTiles()+y/8;
                                decoded[dy*frame.image().width()+dx]=(byte)art.artTiles()[tile].getPixel(x%8,y%8);
                            }
                        }
                    assertArrayEquals(frame.image().pixels(),decoded,surface+" frame "+entry.getKey());
                }
            }
            var renderer=com.openggf.game.presentation.StageCharacterPresentation.renderer(StageSpriteSet.Surface.S3K_BLUE_SPHERE);
            assertNotNull(renderer.getRenderContext());
            assertTrue(palette.dataEquals(renderer.getRenderContext().getPalette(0)));
        } finally {
            if(old==null) System.clearProperty("openggf.saveRoot"); else System.setProperty("openggf.saveRoot",old);
        }
    }

    private static Object privateField(Object owner,String name) throws Exception {
        var field=owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private void acceptance(GameModule base, String game, int waterZone, int waterAct) throws Exception {
        try (CatalogFixture fixture = load(buildSample())) {
            var config = SonicConfigurationService.getInstance();
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, CODE);
            config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
            config.setConfigValue(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);
            config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);
            var resolver = new ModuleResolutionService(List.of(),
                    new EffectiveCatalogPatchEnablement(fixture.effective()),
                    new LogicalRomResolver(() -> null), config, ignored -> fixture.plan());
            assertTrue(resolver.availableMainCharactersForLaunch(game,
                    ModuleResolutionService.LaunchPolicy.STANDARD).contains(CODE));
            GameModule resolved = resolver.resolveForLaunch(base,
                    new GameplayLaunchRequest(game, CODE, List.of()),
                    ModuleResolutionService.LaunchPolicy.STANDARD);
            GameModuleRegistry.setCurrent(resolved);
            com.openggf.game.session.SessionManager.clear();
            TestEnvironment.activeGameplayMode();
            var fixtureWorld = com.openggf.tests.HeadlessTestFixture.builder().withZoneAndAct(0, 0).build();
            var playable = fixtureWorld.sprite();
            assertEquals(base.getPhysicsProvider().getProfile("sonic"), playable.getPhysicsProfile(),
                    "MICA retains every native Sonic physics constant in " + game);
            assertEquals(CODE, playable.characterKey().persisted());
            assertEquals(PlayerCharacter.SONIC_ALONE,
                    com.openggf.game.session.ActiveGameplayTeamResolver.resolvePlayerCharacter(config));
            assertNotNull(GameServices.level().getCurrentLevel());
            assertNotNull(playable.getSpriteRenderer());
            verifySignTiles(resolved, game);
            config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
            assertEquals(PlayerCharacter.SONIC_AND_TAILS,
                    com.openggf.game.session.ActiveGameplayTeamResolver.resolvePlayerCharacter(config));
            config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
            if (game.equals("s2")) verifyHoming(fixtureWorld, false);
            if (game.equals("s3k")) verifyTransformationHook(fixtureWorld);
            verifyGilded(playable, resolved, game);
            if (game.equals("s2")) verifyHoming(fixtureWorld, true);
            var sprites = GameServices.sprites();
            var rewind = sprites.rewindSnapshottable();
            var state = rewind.capture();
            int x = playable.getCentreX();
            playable.setCentreX((short) (x + 40));
            rewind.restore(state);
            assertEquals(x, sprites.getSprite(CODE).getCentreX());
            assertEquals(CODE, ((com.openggf.sprites.playable.AbstractPlayableSprite)
                    sprites.getSprite(CODE)).characterKey().persisted());

            Map<String,Object> payload = new LinkedHashMap<>();
            payload.put("act",0); payload.put("zone",0); payload.put("lives",3);
            payload.put("mainCharacter",CODE); payload.put("sidekicks",List.of());
            payload.put("chaosEmeralds",List.of()); payload.put("clear",false);
            payload.put("progressCode",1); payload.put("clearState",0);
            SaveManager saves = new SaveManager(temp.resolve("saves"));
            saves.writeSlot(game,1,payload);
            var profile = (com.openggf.game.dataselect.DataSelectGameProfile) resolved.getDataSelectHostProfile();
            assertEquals(CODE, saves.readSlotSummary(game,1,profile).payload().get("mainCharacter"));
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE,
                    saves.readSlotSummary(game,1,profile).payload().get("mainCharacter").toString());
            var reloaded = com.openggf.game.session.GameplayTeamBootstrap.registerActiveTeam(resolved,
                    new SpriteManager(config), config);
            assertEquals(CODE, reloaded.mainSprite().characterKey().persisted());
            byte[] saved = Files.readAllBytes(temp.resolve("saves").resolve(game).resolve("slot1.json"));

            // Load real host water tables and exercise the mod palette's derived-row policy.
            GameServices.level().loadZoneAndAct(waterZone, waterAct);
            assertEquals(CODE, ((com.openggf.sprites.playable.AbstractPlayableSprite)
                    GameServices.sprites().getSprite(CODE)).characterKey().persisted());
            var water = resolved.getWaterDataProvider().getUnderwaterPalette(
                    TestEnvironment.currentRom(), game.equals("s2") ? 13 : waterZone, waterAct, PlayerCharacter.SONIC_ALONE);
            assertNotNull(water);
            var dry = GameServices.level().getCurrentLevel().getPalette(0);
            var definition = resolved.getPlayableCharacterRegistry().find(CharacterKey.parsePersisted(CODE)).orElseThrow();
            var presentation = definition.presentationSupplier().load(CODE);
            assertEquals("MICA", presentation.displayName());
            for(var host:CharacterPresentation.Host.values()) {
                var art=presentation.art().get(host);
                assertEquals(16,art.hudLifeIcon().width());
                assertEquals(16,art.oneUpFace().width());
                assertEquals(48,art.sign().face().face().width());
                assertEquals(32,art.sign().spin1().face().width());
                assertEquals(32,art.sign().spin3().face().width());
                assertNotNull(art.sign().face().backing());
                assertEquals(0,art.sign().face().backing().paletteLine());
            }
            assertEquals(java.util.Set.of('M','A'),presentation.supplementaryGlyphs()
                    .get(new CharacterPresentation.Font(CharacterPresentation.Host.S1,CharacterPresentation.Style.HUD)).keySet());
            var custom = definition.paletteSupplier().load(CODE);
            var current = (com.openggf.sprites.playable.AbstractPlayableSprite) GameServices.sprites().getSprite(CODE);
            var context = current.getSpriteRenderer().getRenderContext();
            assertNotNull(context);
            assertTrue(custom.dataEquals(context.getPalette(0)));
            assertNull(context.getUnderwaterPalette(0), "Absent authored palette retains live host derivation");
            var derived = com.openggf.graphics.RenderContext.deriveUnderwaterPalette(custom, dry, water[0]);
            assertFalse(custom.dataEquals(derived), "Water must change the jade art");

            // Disabled owner: use the stock registry and preserve the persisted slot.
            var findings = new ArrayList<com.openggf.game.session.GameplayTeamBootstrap.CharacterFinding>();
            var fallback = com.openggf.game.session.GameplayTeamBootstrap.registerActiveTeam(base,
                    resolved.getPlayableCharacterRegistry(),
                    CharacterAvailability.dynamic(owner -> owner.equals("mica-and-talon"), owner -> false),
                    new SpriteManager(config), config, (short) 100, (short) 100, findings::add);
            assertEquals(CharacterKey.SONIC, fallback.mainSprite().characterKey());
            assertFalse(findings.isEmpty());
            assertEquals(CODE, findings.getFirst().requestedCode());
            assertEquals(PlayableCharacterRegistry.FallbackReason.DISABLED_OWNER, findings.getFirst().reason());
            assertEquals(CODE, saves.readSlotSummary(game,1,profile).payload().get("mainCharacter"));
            assertArrayEquals(saved, Files.readAllBytes(temp.resolve("saves").resolve(game).resolve("slot1.json")));
            assertTrue(fixture.runtime().runtimeDisabledOwners().isEmpty());
        }
    }

    private void verifyTransformationHook(com.openggf.tests.HeadlessTestFixture fixture) {
        var registry = com.openggf.game.session.SessionManager.getCurrentGameplayMode().getRewindRegistry();
        var before = registry.capture();
        var player = fixture.sprite();
        fixture.stepIdleFrames(60);
        player.currentLevelState().resumeTimer();
        for (int i = 0; i < 7; i++) player.currentGameState().markEmeraldCollected(i);
        player.addRings(50 - player.getRingCount());
        fixture.stepFrame(false, false, false, false, true);
        fixture.stepIdleFrames(6);
        fixture.stepFrame(false, false, false, false, true);
        assertTrue(player.isSuperSonic(), "MICA's hook must preserve the S3K transformation press");
        registry.restore(before);
    }

    /** Execute MICA's actual air hook against native touch/defeat, then replay the strike. */
    private void verifyHoming(com.openggf.tests.HeadlessTestFixture fixture, boolean powered) {
        var player = fixture.sprite();
        var registry = com.openggf.game.session.SessionManager.getCurrentGameplayMode().getRewindRegistry();
        var original = registry.capture();
        player.getSuperStateController().debugDeactivate();
        for (int i = 0; i < 40; i++) player.getSuperStateController().update();
        player.addRings(-player.getRingCount());
        fixture.stepIdleFrames(60);
        if (powered) {
            for (int i = 0; i < player.currentGameState().getChaosEmeraldCount(); i++)
                player.currentGameState().markEmeraldCollected(i);
            player.addRings(50);
            player.setAir(true); player.setJumping(true); player.setYSpeed((short) 0x28);
            player.getSuperStateController().checkTransformationBeforeMove();
            for (int i = 0; i < 80; i++) player.getSuperStateController().update();
            assertTrue(player.isSuperSonic());
            player.setAir(false); player.setJumping(false); player.setYSpeed((short) 0);
        }
        var enemy = GameServices.level().getObjectManager().createDynamicObject(() ->
                new com.openggf.game.sonic2.objects.badniks.BuzzerBadnikInstance(new com.openggf.level.objects.ObjectSpawn(
                        player.getCentreX() + 96, player.getCentreY() - 72,
                        com.openggf.game.sonic2.constants.Sonic2ObjectIds.BUZZER, 0, 0, false, 0)));
        fixture.stepIdleFrames(2);
        fixture.stepFrame(false, false, false, false, true);
        fixture.stepIdleFrames(6);
        fixture.stepFrame(false, false, false, false, true);
        var runtime = com.openggf.game.session.WorldSessionPolicyAccess.getService(
                com.openggf.game.session.SessionManager.getCurrentWorldSession(),
                com.openggf.game.mutators.HomingAttackRuntime.class);
        assertEquals(com.openggf.game.mutators.HomingAttackRuntime.Phase.HOMING, runtime.phase());
        assertEquals(powered ? com.openggf.game.mutators.HomingAttackRuntime.Mode.INSTAGIB
                : com.openggf.game.mutators.HomingAttackRuntime.Mode.HOMING, runtime.capture().launchMode());
        assertTrue(List.of(28, 29, 30, 31).contains(player.getMappingFrame()),
                "The strike selects the authored dive pose");
        assertEquals(player.resolveAnimationId(CanonicalAnimation.ROLL), player.getAnimationId(),
                "The visual dive must retain native roll attack semantics");
        var strike = registry.capture();
        var expected = new ArrayList<String>();
        for (int i = 0; i < 18; i++) {
            fixture.stepIdleFrames(1);
            expected.add(player.getCentreX() + ":" + player.getCentreY() + ":" + runtime.capture());
        }
        assertTrue(enemy.isDestroyed(), "MICA kills through native touch");
        registry.restore(strike);
        for (int i = 0; i < 18; i++) {
            fixture.stepIdleFrames(1);
            assertEquals(expected.get(i), player.getCentreX() + ":" + player.getCentreY() + ":" + runtime.capture());
        }
        registry.restore(original);
    }

    private void verifyGilded(com.openggf.sprites.playable.AbstractPlayableSprite player,
                               GameModule module, String game) throws Exception {
        var controller = player.getSuperStateController();
        assertInstanceOf(com.openggf.sprites.playable.ProfileSuperStateController.class, controller);
        var state = player.currentGameState();
        player.currentLevelState().resumeTimer();
        com.openggf.sprites.playable.ObjectControlState.none().applyTo(player);
        player.setAir(true); player.setJumping(true); player.setYSpeed((short) 0x28);
        player.addRings(50 - player.getRingCount());
        controller.checkTransformationBeforeMove();
        assertFalse(controller.isSuper(), "No emeralds must not transform");
        for (int i = 0; i < state.getChaosEmeraldCount(); i++) state.markEmeraldCollected(i);
        player.addRings(-1);
        if (game.equals("s3k")) assertFalse(controller.activateFromAirAbility());
        else controller.checkTransformationBeforeMove();
        assertFalse(controller.isSuper(), "49 rings must not transform");
        player.addRings(1);
        if (game.equals("s3k")) assertTrue(controller.activateFromAirAbility());
        else controller.checkTransformationBeforeMove();
        assertTrue(player.isSuperSonic());
        var transforming = controller.captureRewindState();
        var wholePlayer = player.captureRewindState(true);
        for (int i = 0; i < 80; i++) controller.update();
        assertEquals(com.openggf.sprites.playable.SuperState.SUPER, controller.getState());
        assertFalse(controller.isHyperFormActive());
        var definition = module.getPlayableCharacterRegistry().find(player.characterKey()).orElseThrow();
        var form = definition.superFormSupplier().load(CODE).games().get(module.getGameId()).get(SuperFormProfile.Tier.SUPER);
        // Observe a complete authored loop; every frame must be MICA's palette, never Sonic's.
        for (int i = 0; i < form.cycleTicks() * form.dryCycle().size(); i++) {
            var before = controller.captureRewindState();
            controller.update();
            var after = controller.captureRewindState();
            assertEquals(before.paletteTimer() == 0 ? form.cycleTicks() - 1 : before.paletteTimer() - 1,
                    after.paletteTimer());
            assertEquals(before.paletteTimer() == 0 ? (before.paletteFrame() + 1) % form.dryCycle().size()
                    : before.paletteFrame(), after.paletteFrame());
            var palette = player.getSpriteRenderer().getRenderContext().getPalette(0);
            assertTrue(form.dryCycle().stream().anyMatch(row -> paletteMatches(palette, row)));
        }
        assertTrue(player.getRingCount() < 50, "Native ring drain runs during transformation too");
        var active = controller.captureRewindState();
        controller.debugDeactivate();
        for (int i = 0; i < 40; i++) controller.update();
        assertFalse(player.isSuperSonic());
        controller.restoreRewindState(active);
        // Whole-player rewind owns the flag; the controller restore owns phase/physics/presentation.
        player.setSuperSonic(true);
        assertEquals(active, controller.captureRewindState());
        player.restoreRewindState(wholePlayer);
        assertTrue(player.isSuperSonic());
        assertEquals(transforming, controller.captureRewindState());
        controller.debugDeactivate();
        for (int i = 0; i < 40; i++) controller.update();
        if (game.equals("s3k")) {
            for (int i = 0; i < 7; i++) state.markSuperEmeraldCollected(i);
            player.addRings(50 - player.getRingCount()); player.setInvincibleFrames(0);
            assertTrue(controller.activateFromAirAbility());
            assertTrue(controller.isHyperFormActive());
            for (int i = 0; i < 80; i++) controller.update();
            assertFalse(GameServices.level().getObjectManager().getActiveObjects().stream().anyMatch(object ->
                    object instanceof com.openggf.game.sonic3k.objects.HyperSonicStarsObjectInstance));
        }
        player.addRings(1 - player.getRingCount());
        if (!controller.isSuper()) controller.debugActivate();
        player.addRings(1 - player.getRingCount());
        for (int i = 0; i < 100; i++) controller.update();
        assertFalse(controller.isSuper(), "Exhausted rings revert");
        for (int i = 0; i < 40; i++) controller.update();
        player.setAir(false); player.setJumping(false);
        state.resetState();
        if (game.equals("s1")) {
            var world = com.openggf.game.session.SessionManager.getCurrentWorldSession();
            var settings = com.openggf.mods.mutators.MutatorWorldAccess.state(world);
            assertNotNull(settings);
            String key = settings.requested().keySet().stream().filter(k -> k.endsWith(":gilded")).findFirst().orElseThrow();
            settings.requestEnabled(key, true);
            settings.requestOption(key, "emeralds", 0); settings.requestOption(key, "rings", 10);
            assertTrue(settings.boundary(com.openggf.mods.mutators.MutatorScope.LOAD,
                    com.openggf.mods.mutators.MutatorSessionState.LoadCause.FULL_LEVEL_ASSEMBLY).accepted());
            var profile = definition.superFormSupplier().load(CODE);
            var changed = new com.openggf.sprites.playable.ProfileSuperStateController(player, profile, GameId.S1);
            player.setSuperStateController(changed);
            player.addRings(10 - player.getRingCount()); player.setAir(true); player.setJumping(true);
            player.setYSpeed((short) 0x28); player.currentLevelState().resumeTimer();
            changed.checkTransformationBeforeMove(); assertTrue(changed.isSuper());
            changed.debugDeactivate();
            settings.requestOption(key, "s1", false);
            assertTrue(settings.boundary(com.openggf.mods.mutators.MutatorScope.LOAD,
                    com.openggf.mods.mutators.MutatorSessionState.LoadCause.FULL_LEVEL_ASSEMBLY).accepted());
            var disabled = new com.openggf.sprites.playable.ProfileSuperStateController(player, profile, GameId.S1);
            player.setSuperStateController(disabled); disabled.debugActivate(); assertFalse(disabled.isSuper());
            settings.requestEnabled(key, false);
            assertTrue(settings.boundary(com.openggf.mods.mutators.MutatorScope.LOAD,
                    com.openggf.mods.mutators.MutatorSessionState.LoadCause.FULL_LEVEL_ASSEMBLY).accepted());
            player.setSuperStateController(controller); player.setAir(false); player.setJumping(false);
        }
    }

    private static boolean paletteMatches(com.openggf.level.Palette palette, List<Integer> words) {
        var expected = new com.openggf.level.Palette();
        for (int i = 0; i < 16; i++) expected.getColor(i).fromSegaFormat(words.get(i));
        return expected.dataEquals(palette);
    }

    /** Exercise authored pixels against each host's actual ROM sheet, without changing native art. */
    private void verifySignTiles(GameModule module, String game) throws Exception {
        var definition = module.getPlayableCharacterRegistry().find(CharacterKey.parsePersisted(CODE)).orElseThrow();
        var host = CharacterPresentation.Host.valueOf(game.toUpperCase(Locale.ROOT));
        var art = definition.presentationSupplier().load(CODE).art().get(host);
        var render = GameServices.level().getObjectRenderManager();
        var nativeSheet = game.equals("s3k")
                ? render.getSheet(com.openggf.game.sonic3k.Sonic3kObjectArtKeys.END_SIGN)
                : render.getSignpostSheet();
        assertNotNull(nativeSheet);
        int[] frames = switch (host) {
            case S1 -> new int[]{4, -1, 1, 2, 3};
            case S2 -> new int[]{0, 1, 3, 4, 5};
            case S3K -> new int[]{0, -1, 4, 5, 6};
        };
        var original = nativeSheet.getFrame(frames[0]);
        for (int frame : new int[]{frames[0], frames[2], frames[4]}) {
            var pose = com.openggf.game.presentation.CharacterSignRenderer.select(art, frame,
                    frames[0], frames[1], frames[2], frames[3], frames[4]);
            assertNotNull(pose);
            var composed = com.openggf.game.presentation.CharacterSignRenderer.compose(nativeSheet,
                    frame, pose, 8, GameServices.level().getCurrentLevel().getPalette(0));
            var face = pose.face();
            var decoded = com.openggf.game.presentation.CharacterNameComposer.extract(composed.getPatterns(),
                    nativeSheet.getPatterns().length, face.width()/8, face.height()/8,
                    0, 0, face.width(), face.height());
            assertArrayEquals(face.pixels(), decoded.pixels(), game + " face/spin pixels");
            assertTrue(!java.util.Arrays.equals(new byte[face.pixels().length], face.pixels()));
            assertTrue(composed.getFrame(0).pieces().stream().anyMatch(piece -> piece.paletteIndex() == 8));
            assertTrue(composed.getFrame(0).pieces().stream().anyMatch(piece -> piece.paletteIndex() == 0));
            assertSame(original, nativeSheet.getFrame(frames[0]), "Stock mapping is unchanged");
            for (int i=0; i<nativeSheet.getPatterns().length; i++)
                assertSame(nativeSheet.getPatterns()[i], composed.getPatterns()[i]);
        }
    }

    private Path buildSample() throws Exception {
        Path project = Path.of("examples/mica-and-talon"), classes = temp.resolve("classes");
        Files.createDirectories(classes.resolve("art"));
        var args = new ArrayList<String>(List.of("--release","25","-classpath",
                System.getProperty("java.class.path"),"-d",classes.toString()));
        try(var sources=Files.walk(project.resolve("src/main/java"))) {
            sources.filter(p->p.toString().endsWith(".java")).sorted().map(Path::toString).forEach(args::add);
        }
        assertEquals(0,javax.tools.ToolProvider.getSystemJavaCompiler().run(null,null,null,args.toArray(String[]::new)));
        Files.createDirectories(classes.resolve("META-INF"));
        Files.copy(project.resolve("src/main/resources/META-INF/openggf-mod.yaml"),
                classes.resolve("META-INF/openggf-mod.yaml"));
        assertEquals(0,com.openggf.tools.modsdk.GgfModCli.run(new String[]{"convert","art","--playable",
                "--image",project.resolve("src/main/mod/mica.png").toString(),
                "--sheet",project.resolve("src/main/mod/mica-sheet.yaml").toString(),
                "--out",classes.resolve("art/mica.ggfp").toString()},System.out));
        Path presentation = project.resolve("art/mica/presentation");
        Files.createDirectories(classes.resolve("art/mica/presentation"));
        try(var files=Files.list(presentation)) {
            for(var file:files.toList()) Files.copy(file,classes.resolve("art/mica/presentation").resolve(file.getFileName()));
        }
        Path stages = project.resolve("art/mica/wave3");
        Files.createDirectories(classes.resolve("art/mica/wave3"));
        try(var files=Files.list(stages)) {
            for(var file:files.filter(p -> p.toString().endsWith(".png") && !p.toString().endsWith("-fixed.png")).toList())
                Files.copy(file,classes.resolve("art/mica/wave3").resolve(file.getFileName()));
        }
        Path endings = project.resolve("art/mica/endings");
        try(var files=Files.walk(endings)) {
            for(var file:files.filter(p -> p.toString().endsWith(".png")).toList()) {
                Path destination=classes.resolve("art/mica/endings").resolve(endings.relativize(file));
                Files.createDirectories(destination.getParent()); Files.copy(file,destination);
            }
        }
        Path jar = temp.resolve("mica-and-talon.jar");
        assertEquals(0,com.openggf.tools.modsdk.GgfModCli.run(new String[]{"package","--input",
                classes.toString(),"--out",jar.toString(),"--warnings","error"},System.out));
        try (JarFile packed = new JarFile(jar.toFile())) {
            var sheet = com.openggf.level.objects.PlayableSheetReader.read(
                    packed.getInputStream(packed.getJarEntry("art/mica.ggfp")).readAllBytes());
            assertEquals(98, sheet.frames().size());
            assertEquals(18, sheet.animations().size());
            assertEquals(List.of(28, 29, 30, 31), sheet.animations().get("HOMING_DIVE").steps()
                    .stream().map(com.openggf.level.objects.PlayableSheetReader.AnimationStep::frameIndex).toList());
            assertEquals(List.of(8, 8, 8, 8), sheet.animations().get("WALK").slopes()
                    .stream().map(List::size).toList());
            var base = com.openggf.level.objects.BakedSheetReader.read(sheet.baseSheetV1());
            assertArrayEquals(new int[]{0x0000, 0x0222, 0x0440, 0x0660, 0x0680, 0x08A2,
                    0x0AC6, 0x08CE, 0x06AC, 0x068A, 0x044E, 0x022A, 0x0EEE, 0x0CCC,
                    0x088E, 0x0024}, base.palette().orElseThrow().colors());
            assertTrue(sheet.frames().stream().allMatch(f -> f.runs().stream()
                    .mapToInt(r -> r.tileCount()).sum() <= 36));
        }
        return jar;
    }

    private CatalogFixture load(Path jar) throws Exception {
        Path repo = temp.resolve("repo"); Files.createDirectories(repo);
        Path copy = repo.resolve(jar.getFileName()); Files.copy(jar, copy);
        var scanned = new DefaultModRepositoryScanner().scan(repo.toAbsolutePath().normalize());
        var validated = new ModCatalogValidator(repo.toAbsolutePath().normalize(),
                ModInputLimits.production(), (game, id) -> true).validate(scanned);
        ModDescriptor descriptor = (ModDescriptor) validated.entries().getFirst();
        assertFalse(descriptor.hasErrors(), descriptor.findings()::toString);
        ModState state = new ModState(1, List.of(new ModState.Entry(
                descriptor.manifest().id(), true, 0, true, descriptor.sha256())));
        EffectiveModCatalog effective = new EffectiveCatalogBuilder()
                .build(validated.entries(), state).effective();
        ModRuntime runtime = new ModClassLoaderFactory(getClass().getClassLoader())
                .create(effective, Set.of(descriptor.manifest().id()));
        runtime.installFaultBoundary(new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                owners -> new ModStateSaveResult.Saved(), owners -> { }));
        return new CatalogFixture(runtime, effective, runtime.newRegistrationPlan());
    }

    private static void createJar(Path root, Path jar, Predicate<String> include) throws Exception {
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar));
             var paths = Files.walk(root)) {
            for (Path file : paths.filter(Files::isRegularFile).sorted().toList()) {
                String entry = root.relativize(file).toString().replace('\\', '/');
                if (!include.test(entry)) continue;
                out.putNextEntry(new JarEntry(entry)); Files.copy(file, out); out.closeEntry();
            }
        }
    }

    private record CatalogFixture(ModRuntime runtime, EffectiveModCatalog effective,
                                  ModuleResolutionService.PatchPlan plan) implements AutoCloseable {
        @Override public void close() throws Exception { runtime.close(); }
    }
}
