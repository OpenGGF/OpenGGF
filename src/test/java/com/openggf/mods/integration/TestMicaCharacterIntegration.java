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

    @Test @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_2)
    void spindashCaptureS2() throws Exception { captureSpindash("s2", "sonic2.rom.path"); }
    @Test @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_3K)
    void spindashCaptureS3k() throws Exception { captureSpindash("s3k", "s3k.rom.path"); }

    @Test @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_3K)
    void aizVineUsesMicaHangMapping() throws Exception {
        Path output = Path.of(System.getProperty("mica.poses.captureRoot", "target/mica-poses")).resolve("aiz-vine");
        Files.createDirectories(output);
        var settings = new com.openggf.tools.GameplayCaptureSession.Settings(320, CODE, "", "off", null, null, null);
        try (var session = new com.openggf.tools.GameplayCaptureSession(settings)) {
            session.applyMod(buildSample());
            GameServices.configuration().setSessionOverride(SonicConfiguration.DISPLAY_SPRITE_SCANLINE_LIMIT, true);
            session.boot(Path.of(System.getProperty("s3k.rom.path")), 0, 0, settings);
            for (int i = 0; i < 120; i++) session.step(null);
            var player = session.player();
            var vine = new com.openggf.game.sonic3k.objects.AizRideVineObjectInstance(new com.openggf.level.objects.ObjectSpawn(
                    player.getCentreX() + 48, player.getCentreY() - 100, 6, 0, 0, false, 0));
            assertNotNull(GameServices.level().getObjectManager().createDynamicObject(() -> vine));
            session.step(null);
            // Declared positioned grab at the production handle; no gameplay state is supplied by a trace.
            var field = vine.getClass().getDeclaredField("handle"); field.setAccessible(true);
            Object handle = field.get(vine);
            var hx = handle.getClass().getDeclaredField("x"); hx.setAccessible(true);
            var hy = handle.getClass().getDeclaredField("y"); hy.setAccessible(true);
            player.setCentreX((short) hx.getInt(handle)); player.setCentreY((short) (hy.getInt(handle) + 4));
            player.setAir(true); player.setXSpeed((short) 0); player.setYSpeed((short) 0);
            var source = com.openggf.level.objects.PlayableSheetMaterializer.read(Files.readAllBytes(temp.resolve("classes/art/mica.ggfp"))).art();
            var hangFrames = source.animationSet().getScript(CanonicalAnimation.HANG.ordinal()).frames();
            var csv = new StringBuilder(com.openggf.tools.GameplayCaptureSession.stateHeader()).append('\n');
            int observed = 0, swings = 0;
            for (int i = 0; i < 90; i++) {
                session.step(null);
                int frame = player.getMappingFrame();
                if (frame >= 0x90 && frame <= 0x92) {
                    observed++;
                    assertEquals(source.dplcFrames().get(hangFrames.get(frame - 0x90)), player.getSpriteRenderer().dplcFrame(frame));
                    com.openggf.graphics.ScreenshotCapture.savePNG(session.render(), output.resolve("frame-" + i + ".png"));
                }
                if (frame >= 0x78 && frame <= 0x7F) {
                    swings++;
                    assertEquals(source.dplcFrames().get(hangFrames.getFirst()), player.getSpriteRenderer().dplcFrame(frame));
                    if (i % 10 == 0) com.openggf.graphics.ScreenshotCapture.savePNG(session.render(), output.resolve("swing-" + i + ".png"));
                }
                csv.append(session.stateLine(i, null)).append('\n');
            }
            Files.writeString(output.resolve("state.csv"), csv);
            assertTrue(observed > 0 && swings > 10, "native vine must progress from hang into sustained swing");
        }
    }

    private void captureSpindash(String game, String romProperty) throws Exception {
        Path output = Path.of(System.getProperty("mica.poses.captureRoot", "target/mica-poses")).resolve(game + "-spindash");
        Files.createDirectories(output);
        var settings = new com.openggf.tools.GameplayCaptureSession.Settings(320, CODE, "", "off", null, null, null);
        try (var session = new com.openggf.tools.GameplayCaptureSession(settings)) {
            session.applyMod(buildSample());
            GameServices.configuration().setSessionOverride(SonicConfiguration.DISPLAY_SPRITE_SCANLINE_LIMIT, true);
            session.boot(Path.of(System.getProperty(romProperty)), 0, 0, settings);
            var csv = new StringBuilder(com.openggf.tools.GameplayCaptureSession.stateHeader()).append('\n');
            for (int frame = 0; frame < 220; frame++) {
                int mask = frame >= 150 && frame < 190 ? 2 : 0;
                boolean jump = frame == 155 || frame == 165 || frame == 175;
                if (jump) mask |= 0x10;
                var input = new com.openggf.debug.playback.Bk2FrameInput(frame, mask, jump ? 1 : 0, false, "spindash");
                session.step(input);
                csv.append(session.stateLine(frame, input)).append('\n');
                if (frame == 180) {
                    assertTrue(session.player().getSpindash(), "native charge " + game);
                    assertEquals(GameServices.module().getDonorCapabilities().resolveNativeId(CanonicalAnimation.SPINDASH), session.player().getAnimationId());
                }
                if (frame == 190) {
                    assertFalse(session.player().getSpindash());
                    assertTrue(Math.abs(session.player().getGSpeed()) > 0, "native release " + game);
                }
                if (frame == 154 || frame == 180 || frame == 190 || frame == 205)
                    com.openggf.graphics.ScreenshotCapture.savePNG(session.render(), output.resolve("frame-" + frame + ".png"));
            }
            Files.writeString(output.resolve("state.csv"), csv);
        }
    }

    @Test @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_2)
    void sonic2SessionHudAndOneUpCapture() throws Exception {
        Path output = Path.of(System.getProperty("mica.hud.captureRoot",
                "target/mica-s2-hud-capture"));
        Files.createDirectories(output);
        var settings = new com.openggf.tools.GameplayCaptureSession.Settings(320, CODE, "", "off", null, null, null);
        try (var session = new com.openggf.tools.GameplayCaptureSession(settings)) {
            session.applyMod(buildSample());
            session.boot(com.openggf.tests.RomTestUtils.ensureSonic2RomAvailable().toPath(), 0, 0, settings);
            var presentation = com.openggf.game.sonic2.Sonic2CharacterPresentation.resolve(
                    GameServices.module(), GameServices.configuration());
            assertNotNull(presentation);
            assertEquals("MICA", presentation.displayName());
            var art = presentation.art().get(CharacterPresentation.Host.S2);
            var provider = GameServices.module().getObjectArtProvider();
            var hud = provider.getHudLivesPatterns();
            var icon = com.openggf.game.presentation.CharacterNameComposer.tiles(art.hudLifeIcon());
            var face = com.openggf.game.presentation.CharacterNameComposer.tiles(art.oneUpFace());
            var monitor = provider.getSheet(com.openggf.level.objects.ObjectArtKeys.MONITOR).getPatterns();
            for (int tile = 0; tile < 4; tile++) for (int x = 0; x < 8; x++) for (int y = 0; y < 8; y++) {
                assertEquals(icon[tile].getPixel(x, y), hud[tile].getPixel(x, y), "session HUD icon");
                assertEquals(face[tile].getPixel(x, y), monitor[
                        com.openggf.game.sonic2.Sonic2ObjectArt.MONITOR_LIFE_ICON_TILE + tile].getPixel(x, y),
                        "session 1-up face");
            }
            var name = com.openggf.game.sonic2.Sonic2CharacterPresentation.name(GameServices.rom().getRom(),
                    presentation, CharacterPresentation.Style.HUD);
            for (int x = 0; x < name.width(); x++) for (int y = 0; y < name.height(); y++)
                assertEquals(name.pixels()[y * name.width() + x], hud[4 + x / 8 * 2].getPixel(x % 8, y),
                        "session HUD name");
            // Declared setup: place a native 1-up beside the starting player to expose its face.
            var objects = GameServices.level().getObjectManager();
            assertNotNull(objects.createDynamicObject(() -> new com.openggf.game.sonic2.objects.MonitorObjectInstance(
                    new com.openggf.level.objects.ObjectSpawn(session.player().getCentreX() + 80,
                            session.player().getCentreY(), 0x26, 1, 0, false, 0), "HUD check 1-up")));
            var csv = new StringBuilder(com.openggf.tools.GameplayCaptureSession.stateHeader()).append('\n');
            for (int frame = 0; frame < 90; frame++) {
                session.step(null);
                session.renderFrame();
                csv.append(session.stateLine(frame, null)).append('\n');
                if (frame == 30 || frame == 60 || frame == 89)
                    com.openggf.graphics.ScreenshotCapture.savePNG(session.render(), output.resolve("frame-" + frame + ".png"));
            }
            Files.writeString(output.resolve("state.csv"), csv);
        }
    }

    @Test @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_1)
    void iconPaletteS1() throws Exception { captureIconPalette("s1", "sonic1.rom.path", 0x1C, 2); }
    @Test @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_2)
    void iconPaletteS2() throws Exception { captureIconPalette("s2", "sonic2.rom.path",
            com.openggf.game.sonic2.Sonic2ObjectArt.MONITOR_LIFE_ICON_TILE, 1); }
    @Test @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_3K)
    void iconPaletteS3k() throws Exception { captureIconPalette("s3k", "s3k.rom.path", 0x310, 1); }

    private void captureIconPalette(String game, String romProperty, int faceTile, int subtype) throws Exception {
        Path output = Path.of(System.getProperty("mica.icon.captureRoot", "target/mica-icon-palettes")).resolve(game);
        Files.createDirectories(output);
        var settings = new com.openggf.tools.GameplayCaptureSession.Settings(320, CODE, "", "off", null, null, null);
        try (var session = new com.openggf.tools.GameplayCaptureSession(settings)) {
            session.applyMod(buildSample());
            GameServices.configuration().setSessionOverride(SonicConfiguration.DISPLAY_SPRITE_SCANLINE_LIMIT, true);
            session.boot(Path.of(System.getProperty(romProperty)), 0, 0, settings);
            // Declared setup: native 1-up placed beside the leader, without changing stage palettes.
            var spawn = new com.openggf.level.objects.ObjectSpawn(session.player().getCentreX() + 80,
                    session.player().getCentreY(), 0x26, subtype, 0, false, 0);
            assertNotNull(GameServices.level().getObjectManager().createDynamicObject(() -> switch (game) {
                case "s1" -> new com.openggf.game.sonic1.objects.Sonic1MonitorObjectInstance(spawn);
                case "s2" -> new com.openggf.game.sonic2.objects.MonitorObjectInstance(spawn, "palette check 1-up");
                default -> new com.openggf.game.sonic3k.objects.Sonic3kMonitorObjectInstance(spawn);
            }));
            var sheet = GameServices.level().getObjectRenderManager().getArtProvider().getSheet(com.openggf.level.objects.ObjectArtKeys.MONITOR);
            int privateLine = com.openggf.game.presentation.CharacterIconPalette.activeLine(0);
            assertTrue(privateLine >= 4);
            boolean found = false;
            for (int frame = 0; frame < sheet.getFrameCount(); frame++) for (var piece : sheet.getFrame(frame).pieces()) {
                if (piece.tileIndex() >= faceTile && piece.tileIndex() < faceTile + 4) {
                    found = true;
                    assertEquals(privateLine, piece.paletteIndex());
                } else assertTrue(piece.paletteIndex() < 4, "native shell keeps its palette");
            }
            assertTrue(found);
            var csv = new StringBuilder(com.openggf.tools.GameplayCaptureSession.stateHeader()).append('\n');
            for (int frame = 0; frame < 90; frame++) {
                session.step(null);
                csv.append(session.stateLine(frame, null)).append('\n');
                if (frame == 30 || frame == 60 || frame == 89) {
                    var image = session.render();
                    com.openggf.graphics.ScreenshotCapture.savePNG(image, output.resolve("frame-" + frame + ".png"));
                    var renderer = session.player().getSpriteRenderer();
                    var palette = renderer.getRenderContext().getPalette(renderer.paletteLine());
                    var provider = GameServices.level().getObjectRenderManager().getArtProvider();
                    assertIconPixels(image, provider.getHudLivesPatterns(), 0, palette, 16, 200);
                    if (frame == 89) assertFacePresent(image, sheet.getPatterns(), faceTile, palette);
                }
            }
            Files.writeString(output.resolve("state.csv"), csv);
        }
    }

    private static int paletteRgb(com.openggf.level.Palette palette, int index) {
        var colour = palette.getColor(index);
        return (Byte.toUnsignedInt(colour.r) << 16) | (Byte.toUnsignedInt(colour.g) << 8) | Byte.toUnsignedInt(colour.b);
    }

    private static boolean matchesIcon(com.openggf.graphics.RgbaImage image, com.openggf.level.Pattern[] tiles,
            int first, com.openggf.level.Palette palette, int left, int top) {
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            int colour = Byte.toUnsignedInt(tiles[first + x / 8 * 2 + y / 8].getPixel(x % 8, y % 8));
            if (colour != 0 && (image.argb(left + x, top + y) & 0xFFFFFF) != paletteRgb(palette, colour)) return false;
        }
        return true;
    }

    private static void assertIconPixels(com.openggf.graphics.RgbaImage image, com.openggf.level.Pattern[] tiles,
            int first, com.openggf.level.Palette palette, int left, int top) {
        assertTrue(matchesIcon(image, tiles, first, palette, left, top), "HUD opaque pixels must use the character palette");
    }

    private static void assertFacePresent(com.openggf.graphics.RgbaImage image, com.openggf.level.Pattern[] tiles,
            int first, com.openggf.level.Palette palette) {
        // The declared monitor is right of the player; exclude the HUD and player themselves.
        for (int y = 64; y < 180; y++) for (int x = 128; x < 296; x++)
            if (matchesIcon(image, tiles, first, palette, x, y)) return;
        fail("1-up monitor opaque face pixels must use the character palette");
    }

    @Test @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_1)
    void stockIconPaletteS1() throws Exception { stockIconPalette("sonic1.rom.path", List.of("sonic")); }
    @Test @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_2)
    void stockIconPaletteS2() throws Exception { stockIconPalette("sonic2.rom.path", List.of("sonic", "tails")); }
    @Test @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_3K)
    void stockIconPaletteS3k() throws Exception { stockIconPalette("s3k.rom.path", List.of("sonic", "tails", "knuckles")); }

    private void stockIconPalette(String romProperty, List<String> characters) throws Exception {
        for (String character : characters) {
            var settings = new com.openggf.tools.GameplayCaptureSession.Settings(320, character, "", "off", null, null, null);
            try (var session = new com.openggf.tools.GameplayCaptureSession(settings)) {
                session.boot(Path.of(System.getProperty(romProperty)), 0, 0, settings);
                for (int frame = 0; frame < 90; frame++) session.step(null);
                var provider = GameServices.level().getObjectRenderManager().getArtProvider();
                var sheet = provider.getSheet(com.openggf.level.objects.ObjectArtKeys.MONITOR);
                assertEquals(com.openggf.level.objects.ObjectSpriteSheet.class, sheet.getClass(), "stock monitor sheet stays native");
                assertFalse(com.openggf.game.presentation.CharacterIconPalette.hasHudIcon());
                assertIconPixels(session.render(), provider.getHudLivesPatterns(), 0,
                        GameServices.level().getCurrentLevel().getPalette(0), 16, 200);
            }
        }
    }

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
            assertTrue(Integer.parseInt(last[12]) >= 256 && Integer.parseInt(last[12]) < 262, rows.getLast());
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

    @Test @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_2)
    void micaS2EndingCapture() throws Exception { captureS2Ending(false); }
    @Test @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_2)
    void micaS2SuperEndingCapture() throws Exception { captureS2Ending(true); }

    private void captureS2Ending(boolean powered) throws Exception {
        Path output=Path.of("/tmp/mica-endings-s1s2-20261108").resolve(powered?"s2-super":"s2-normal");
        Files.createDirectories(output);
        var settings=new com.openggf.tools.GameplayCaptureSession.Settings(320,CODE,"","off",null,null,null);
        try(var session=new com.openggf.tools.GameplayCaptureSession(settings)) {
            session.applyMod(buildSample());
            session.boot(com.openggf.tests.RomTestUtils.ensureSonic2RomAvailable().toPath(),10,0,settings);
            session.loop().closePresence();
            var engine=new com.openggf.Engine();
            engine.setInputHandler(new com.openggf.control.InputHandler());
            var bind=com.openggf.Engine.class.getDeclaredMethod("bindGameplayMode",
                    com.openggf.game.session.GameplayModeContext.class);
            bind.setAccessible(true); bind.invoke(engine,com.openggf.game.session.SessionManager.getCurrentGameplayMode());
            var reshape=com.openggf.Engine.class.getDeclaredMethod("reshape",int.class,int.class);
            reshape.setAccessible(true); reshape.invoke(engine,320,224);
            GameServices.graphics().setProjectionMatrixBuffer(engine.getProjectionMatrixBuffer());
            var loop=engine.getGameLoop();
            GameServices.fade().cancel();
            if(powered) for(int e=0;e<7;e++) GameServices.gameState().markEmeraldCollected(e);
            GameServices.level().requestCreditsTransition(); loop.step();
            assertEquals(GameMode.ENDING_CUTSCENE,loop.getCurrentGameMode());
            var provider=privateField(privateField(loop,"endingSession"),"endingProvider");
            var manager=privateField(provider,"cutsceneManager");
            var adapter=privateField(manager,"presentation"); assertNotNull(adapter);
            assertEquals(powered?com.openggf.game.sonic2.credits.Sonic2EndingArt.EndingRoutine.SUPER_SONIC:
                    com.openggf.game.sonic2.credits.Sonic2EndingArt.EndingRoutine.SONIC,privateField(manager,"routine"));
            var csv=new StringBuilder("frame,state,photo,objCe,rotation,white\n");
            String last="";
            for(int i=0;i<6000 && loop.getCurrentGameMode()==GameMode.ENDING_CUTSCENE;i++) {
                loop.step(); renderEnding(engine);
                String state=privateField(manager,"state").toString();
                String row=state+","+privateField(manager,"photoIndex")+","+privateField(manager,"objCeFrame")+","+
                        privateField(manager,"rotationDisplayFrame")+","+privateField(adapter,"white");
                csv.append(i).append(',').append(row).append('\n');
                if((!row.equals(last) || i%30==0) && (int)privateField(adapter,"white")==0
                        && !state.equals("TRIGGER_CREDITS")) {
                    var picture=renderEnding(engine);
                    if(state.startsWith("PALETTE"))
                        assertTrue(Arrays.stream(picture.pixels()).anyMatch(pixel -> pixel != picture.pixels()[0]),
                                "visible photo art, frame "+i);
                    com.openggf.graphics.ScreenshotCapture.savePNG(picture,output.resolve("frame-"+i+".png"));
                }
                last=row;
            }
            Files.writeString(output.resolve("state.csv"),csv);
            @SuppressWarnings("unchecked")
            var renderers=(Map<EndingSpriteSet.Surface,PlayerSpriteRenderer>)privateField(adapter,"renderers");
            @SuppressWarnings("unchecked")
            var sets=(Map<EndingSpriteSet.Surface,EndingSpriteSet>)privateField(adapter,"sets");
            for(var entry:renderers.entrySet()) {
                var actual=(com.openggf.sprites.art.SpriteArtSet)privateField(entry.getValue(),"artSet");
                var expected=com.openggf.game.presentation.StageSpriteCompiler.compile(sets.get(entry.getKey()).sprites(),null);
                assertEquals(expected.mappingFrames(),actual.mappingFrames());
                for(int t=0;t<expected.artTiles().length;t++) for(int y=0;y<8;y++) for(int x=0;x<8;x++)
                    assertEquals(expected.artTiles()[t].getPixel(x,y),actual.artTiles()[t].getPixel(x,y));
                assertTrue(entry.getValue().getRenderContext().getEffectivePaletteLine(0)>3);
                var palette=entry.getValue().getRenderContext().getPalette(0);
                if(!powered || entry.getKey()==EndingSpriteSet.Surface.S2_PHOTOS) {
                    for(int i=0;i<16;i++) {
                        var c=palette.getColor(i);
                        assertEquals(sets.get(entry.getKey()).rgb()[i],((c.r&255)<<16)|((c.g&255)<<8)|(c.b&255));
                    }
                } else {
                    var definition=com.openggf.game.presentation.StageCharacterPresentation.activeDefinition();
                    var cycle=definition.superFormSupplier().load(CODE).games().get(GameId.S2)
                            .get(SuperFormProfile.Tier.SUPER).dryCycle();
                    var words=new ArrayList<Integer>();
                    for(int i=0;i<16;i++) words.add(com.openggf.game.palette.PaletteWriteSupport.segaWordFromColor(palette.getColor(i)));
                    assertTrue(cycle.contains(words),"Gilded cycle owns the flight palette");
                }
            }
            var nativeArt=new com.openggf.game.sonic2.credits.Sonic2EndingArt();
            nativeArt.loadPalettes(GameServices.rom().getRom(),powered?
                    com.openggf.game.sonic2.credits.Sonic2EndingArt.EndingRoutine.SUPER_SONIC:
                    com.openggf.game.sonic2.credits.Sonic2EndingArt.EndingRoutine.SONIC);
            var palettes=(com.openggf.level.Palette[])privateField(adapter,"nativePalettes");
            for(int line=1;line<4;line++) assertTrue(nativeArt.getEndingPalettes()[line].dataEquals(palettes[line]));
            loop.closePresence();
        } finally { com.openggf.Engine.clearGlobalInstance(); }
    }

    private static com.openggf.graphics.RgbaImage renderEnding(com.openggf.Engine engine) throws Exception {
        org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
        engine.getGameLoop().getEndingProvider().setClearColor();
        org.lwjgl.opengl.GL11.glClear(org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT | org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT);
        var draw=com.openggf.Engine.class.getDeclaredMethod("draw"); draw.setAccessible(true); draw.invoke(engine);
        GameServices.graphics().flushScreenSpace();
        org.lwjgl.opengl.GL11.glFinish();
        return com.openggf.graphics.ScreenshotCapture.captureFramebuffer(320,224);
    }

    @Test @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_3K)
    void micaS3kEndingNormal() throws Exception { captureS3kEnding(-2); }
    @Test @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_3K)
    void micaS3kEndingSuper() throws Exception { captureS3kEnding(0); }
    @Test @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_3K)
    void micaS3kEndingHyper() throws Exception { captureS3kEnding(2); }

    private void captureS3kEnding(int progress) throws Exception {
        Path output = Path.of(System.getProperty("mica.ending.captureRoot", "target/mica-ending-captures"))
                .resolve(progress < 0 ? "normal" : progress == 0 ? "super" : "hyper");
        Files.createDirectories(output);
        var settings = new com.openggf.tools.GameplayCaptureSession.Settings(320,CODE,"","off",null,null,null,null,false,false);
        String previous = System.getProperty("openggf.saveRoot");
        System.setProperty("openggf.saveRoot",temp.resolve("ending-saves").toString());
        try (var session = new com.openggf.tools.GameplayCaptureSession(settings)) {
            session.applyMod(buildSample());
            session.boot(com.openggf.tests.RomTestUtils.ensureSonic3kRomAvailable().toPath(),13,1,settings);
            if (progress >= 0) for (int i=0;i<7;i++) GameServices.gameState().markEmeraldCollected(i);
            if (progress == 2) GameServices.gameState().restoreS3kEmeraldProgress(java.util.Collections.nCopies(7,3),true);
            var definition = com.openggf.game.presentation.StageCharacterPresentation.activeDefinition();
            assertNotNull(definition);
            var presentation = definition.presentationSupplier().load(CODE);
            // S1/S2 and S3K ending sets share one map; the six S3K surfaces must all be present.
            assertTrue(presentation.endings().keySet().containsAll(java.util.EnumSet.of(
                    EndingSpriteSet.Surface.S3K_NORMAL_POSE, EndingSpriteSet.Surface.S3K_POWERED_POSE,
                    EndingSpriteSet.Surface.S3K_PASSENGER, EndingSpriteSet.Surface.S3K_EYECATCH,
                    EndingSpriteSet.Surface.S3K_PLAYER, EndingSpriteSet.Surface.S3K_POWERED_PLAYER)));
            for (var entry : presentation.endings().entrySet()) {
                var compiled = com.openggf.game.presentation.StageSpriteCompiler.compile(entry.getValue().sprites(),null);
                for (var frame : entry.getValue().sprites().frames().entrySet()) {
                    assertFalse(compiled.mappingFrames().get(frame.getKey()).pieces().isEmpty());
                    var image = frame.getValue().image();
                    var decoded = new byte[image.width()*image.height()];
                    int start = compiled.dplcFrames().get(frame.getKey()).requests().getFirst().startTile();
                    for (var piece : compiled.mappingFrames().get(frame.getKey()).pieces())
                        for (int col=0;col<piece.widthTiles();col++) for (int row=0;row<piece.heightTiles();row++)
                            for (int x=0;x<8;x++) for (int y=0;y<8;y++) {
                                int px=piece.xOffset()+frame.getValue().hotspotX()+col*8+x;
                                int py=piece.yOffset()+frame.getValue().hotspotY()+row*8+y;
                                if (px<image.width() && py<image.height()) decoded[py*image.width()+px]=compiled.artTiles()[
                                        start+piece.tileIndex()+col*piece.heightTiles()+row].getPixel(x,y);
                            }
                    assertArrayEquals(image.pixels(),decoded,entry.getKey()+"/"+frame.getKey());
                }
            }
            var cacheField = Class.forName("com.openggf.game.sonic3k.ending.EndingCharacterPresentation").getDeclaredField("CACHE");
            cacheField.setAccessible(true);
            @SuppressWarnings("unchecked") var cache = (Map<Object,Map<EndingSpriteSet.Surface,PlayerSpriteRenderer>>)cacheField.get(null);
            cache.clear();
            var drawn = java.util.EnumSet.noneOf(EndingSpriteSet.Surface.class);
            var csv = new StringBuilder(com.openggf.tools.GameplayCaptureSession.stateHeader()).append('\n');
            boolean floatSeen=false, rideSeen=false, transformSeen=false;
            int lastMapping=-1;
            for (int i=0;i<18000;i++) {
                session.step(null);
                if (session.loop().getCurrentGameMode() == GameMode.TITLE_SCREEN) break;
                var player = GameServices.sprites().getMainPlayable();
                assertNotNull(player,"ending player retained until title return");
                int mapping = player.getMappingFrame();
                floatSeen |= mapping >= 200 && mapping <= 207;
                rideSeen |= mapping == 186;
                transformSeen |= mapping >= 210 && mapping <= 214;
                csv.append(session.stateLine(i,null)).append('\n');
                if (i % 120 == 0 || mapping != lastMapping && (mapping == 186 || mapping >= 200 && mapping <= 214)) {
                    session.renderFrame();
                    com.openggf.graphics.ScreenshotCapture.savePNG(session.render(),output.resolve("frame-"+i+".png"));
                }
                for (var renderers : cache.values()) {
                    drawn.addAll(renderers.keySet());
                    for (var entry : renderers.entrySet()) {
                        var context = entry.getValue().getRenderContext();
                        assertNotNull(context);
                        assertTrue(context.getEffectivePaletteLine(0)>3,"private character palette row");
                        if (entry.getKey() == EndingSpriteSet.Surface.S3K_NORMAL_POSE
                                || entry.getKey() == EndingSpriteSet.Surface.S3K_PASSENGER
                                || entry.getKey() == EndingSpriteSet.Surface.S3K_EYECATCH) {
                            int[] rgb = presentation.endings().get(entry.getKey()).rgb();
                            for (int color=0;color<16;color++) {
                                var actual=context.getPalette(0).getColor(color);
                                assertEquals(rgb[color],(Byte.toUnsignedInt(actual.r)<<16)
                                        | (Byte.toUnsignedInt(actual.g)<<8) | Byte.toUnsignedInt(actual.b));
                            }
                        }
                    }
                }
                lastMapping=mapping;
            }
            assertTrue(drawn.contains(EndingSpriteSet.Surface.S3K_PLAYER));
            assertTrue(drawn.contains(EndingSpriteSet.Surface.S3K_PASSENGER));
            assertTrue(drawn.contains(progress < 0 ? EndingSpriteSet.Surface.S3K_NORMAL_POSE : EndingSpriteSet.Surface.S3K_POWERED_POSE));
            if (progress >= 0) assertTrue(drawn.contains(EndingSpriteSet.Surface.S3K_POWERED_PLAYER));
            if (progress == 2) assertTrue(drawn.contains(EndingSpriteSet.Surface.S3K_EYECATCH));
            Files.writeString(output.resolve("state.csv"),csv);
            if (progress < 2) assertTrue(floatSeen,"native Float2 frames must run");
            assertTrue(rideSeen,"native plane ride must run");
            if (progress >= 0) assertTrue(transformSeen,"native reunion transform frames must run");
        } finally {
            if (previous == null) System.clearProperty("openggf.saveRoot"); else System.setProperty("openggf.saveRoot",previous);
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

    @Test @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_1)
    void continuePixelsS1() throws Exception { captureContinue("s1", "sonic1.rom.path"); }
    @Test @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_2)
    void continuePixelsS2() throws Exception { captureContinue("s2", "sonic2.rom.path"); }
    @Test @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_3K)
    void continuePixelsS3k() throws Exception { captureContinue("s3k", "s3k.rom.path"); }

    @Test @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_3K)
    void snowboardPixelsS3k() throws Exception {
        Path jar=buildSample();
        var settings=new com.openggf.tools.GameplayCaptureSession.Settings(320,CODE,"","off",null,null,null);
        try(var session=new com.openggf.tools.GameplayCaptureSession(settings)) {
            session.applyMod(jar);
            GameServices.configuration().setSessionOverride(SonicConfiguration.S3K_SKIP_INTROS,false);
            session.boot(Path.of(System.getProperty("s3k.rom.path")),5,0,settings);
            var set=com.openggf.game.presentation.ScreenFrameRenderer.active(CharacterPresentation.Host.S3K);
            assertEquals(13,set.banks().get("snowboard").size());
            boolean overlaySeen=false;
            for(int frame=0;frame<360;frame++) {
                session.step(null);
                if(frame>0 && frame%60==0) writeCapture(session.render(),"s3k-icz1-snowboard-"+frame);
                var objects=GameServices.level().getObjectManager();
                for(var object:objects.getActiveObjects()) {
                    if(object instanceof com.openggf.game.sonic3k.objects.IczSnowboardIntroInstance) overlaySeen=true;
                }
            }
            assertTrue(overlaySeen,"Native ICZ intro controller must run for MICA");
        }
    }

    private void captureContinue(String game, String romProperty) throws Exception {
        Path jar=buildSample();
        var settings=new com.openggf.tools.GameplayCaptureSession.Settings(320,CODE,"tails","off",null,null,null);
        try(var session=new com.openggf.tools.GameplayCaptureSession(settings)) {
            session.applyMod(jar);
            session.boot(Path.of(System.getProperty(romProperty)),0,0,settings);
            // Enter from a revealed level, rather than carrying its initial line-1..3 black fade into the menu.
            for (int i=0;i<120;i++) session.step(null);
            var state=GameServices.gameState();
            while(state.getLives()>0) state.loseLife();
            state.addContinue(); state.addContinue(); state.addContinue();
            GameServices.level().requestGameOverExit(GameOverExit.CONTINUE_SCREEN);
            for(int i=0;i<120 && session.loop().getCurrentGameMode()!=GameMode.CONTINUE_SCREEN;i++) session.step(null);
            assertEquals(GameMode.CONTINUE_SCREEN,session.loop().getCurrentGameMode());
            for(int i=0;i<140;i++) session.step(null);
            var waiting = drawContinue(session);
            long jade=0;
            for(int y=120;y<195;y++) for(int x=100;x<200;x++) {
                int pixel=waiting.argb(x,y),r=(pixel>>16)&255,g=(pixel>>8)&255,b=pixel&255;
                if(g>r+25 && g>=b) jade++;
            }
            assertTrue(jade>15,"MICA jade body pixels must be visible in continue screen");
            writeCapture(waiting,game+"-continue-wait");
            var screen=session.loop().getContinueScreenProvider();
            screen.update(true,false);
            for(int i=0;i<25;i++) screen.update(false,false);
            writeCapture(drawContinue(session),game+"-continue-depart");
            assertTrue(screen.isAccepted());
            if(game.equals("s3k")) capturePortraits();
        }
    }

    private static com.openggf.graphics.RgbaImage drawContinue(com.openggf.tools.GameplayCaptureSession session) {
        var graphics=GameServices.graphics();
        org.lwjgl.opengl.GL11.glClearColor(0,0,0,1);
        org.lwjgl.opengl.GL11.glClear(org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT | org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT);
        graphics.resetForFixedFunction();
        GameServices.camera().setX((short)0); GameServices.camera().setY((short)0);
        session.loop().getContinueScreenProvider().draw();
        graphics.flushScreenSpace();
        org.lwjgl.opengl.GL11.glFinish();
        return com.openggf.graphics.ScreenshotCapture.captureFramebuffer(320,224);
    }

    private void capturePortraits() throws Exception {
        Path saves=temp.resolve("portrait-saves");
        var manager=new SaveManager(saves);
        for(int slot=1;slot<=2;slot++) {
            Map<String,Object> payload=new LinkedHashMap<>();
            payload.put("act",0);payload.put("zone",0);payload.put("lives",3);
            payload.put("mainCharacter",CODE);payload.put("sidekicks",slot==1?List.of():List.of("tails"));
            payload.put("chaosEmeralds",List.of());payload.put("clear",false);
            payload.put("progressCode",1);payload.put("clearState",0);
            manager.writeSlot("s3k",slot,payload);
        }
        var presentation=new com.openggf.game.sonic3k.dataselect.S3kDataSelectPresentation(saves,GameServices.configuration());
        GameServices.graphics().clearPaletteFadePresentation();
        com.openggf.graphics.PaletteUploadPresentation.release(GameServices.graphics());
        presentation.initialize();
        assertEquals(DataSelectProvider.State.FADE_IN,presentation.getState());
        var method=presentation.getClass().getDeclaredMethod("resolveCustomPortraitFrame",com.openggf.game.save.SelectedTeam.class);
        method.setAccessible(true);
        for(var sidekicks:List.of(List.<String>of(),List.of("tails"))) {
            var frame=(com.openggf.level.render.SpriteMappingFrame)method.invoke(presentation,new com.openggf.game.save.SelectedTeam(CODE,sidekicks));
            assertTrue(frame.pieces().stream().anyMatch(piece->piece.tileIndex()>=0x2000));
            if(!sidekicks.isEmpty()) assertTrue(frame.pieces().stream().anyMatch(piece->piece.tileIndex()<0x2000),"Stock Tails remains in team composition");
        }
        var stateMethod=presentation.getClass().getDeclaredMethod("buildObjectState");
        stateMethod.setAccessible(true);
        var objectState=(com.openggf.game.sonic3k.dataselect.S3kSaveScreenObjectState)stateMethod.invoke(presentation);
        for(int slot=0;slot<2;slot++) {
            var icon=objectState.visualState().slotStates().get(slot).customLifeIcon();
            assertNotNull(objectState.visualState().slotStates().get(slot).customContinueIcon(),
                    "MICA save slot uses its continue miniature");
            assertNotNull(icon,"MICA save slot uses its HUD life face");
            assertTrue(icon.pieces().stream().allMatch(piece->piece.tileIndex()>=0x2000));
        }
        for(int i=0;i<24;i++) presentation.update(new com.openggf.control.InputHandler());
        GameServices.camera().setX((short)0); GameServices.camera().setY((short)0);
        presentation.setClearColor();
        org.lwjgl.opengl.GL11.glClear(org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT | org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT);
        GameServices.graphics().resetForFixedFunction();
        presentation.draw();GameServices.graphics().flushScreenSpace();
        org.lwjgl.opengl.GL11.glFinish();
        var saveImage = com.openggf.graphics.ScreenshotCapture.captureFramebuffer(320,224);
        var definition = com.openggf.game.presentation.StageCharacterPresentation.activeDefinition();
        var iconArt = definition.presentationSupplier().load(CODE).art().get(CharacterPresentation.Host.S3K).hudLifeIcon();
        var iconTiles = com.openggf.game.presentation.CharacterNameComposer.tiles(iconArt);
        var iconPalette = definition.paletteSupplier().load(CODE);
        int matches = 0;
        for (int y = 0; y <= 208; y++) for (int x = 0; x <= 304; x++)
            if (matchesIcon(saveImage, iconTiles, 0, iconPalette, x, y)) matches++;
        assertTrue(matches >= 2, "both saved MICA slots use her private life-icon colours");
        writeCapture(saveImage,"s3k-data-select-solo-and-tails");
        var controller=presentation.getSessionController();
        controller.moveSelection(-controller.menuModel().getSelectedRow());
        controller.loadAvailableTeams("" );
        var stock=(com.openggf.game.sonic3k.dataselect.S3kSaveScreenObjectState)stateMethod.invoke(presentation);
        assertNull(stock.visualState().noSaveCustomFrame(),"Stock NO SAVE portrait retains native mappings");
        controller.loadAvailableTeams(CODE+";"+CODE+",tails");
        for(var sidekicks:List.of(List.<String>of(),List.of("tails"))) {
            var wanted=new com.openggf.game.save.SelectedTeam(CODE,sidekicks);
            int target=controller.availableTeams().indexOf(wanted);
            assertTrue(target>=0,"MICA must be selectable in NO SAVE");
            controller.cycleTeam(target-controller.menuModel().getSelectedTeamIndex());
            // No animation tick: changing the team must invalidate the cached visual immediately.
            var selected=(com.openggf.game.sonic3k.dataselect.S3kSaveScreenObjectState)stateMethod.invoke(presentation);
            assertNotNull(selected.visualState().noSaveCustomFrame());
            assertTrue(selected.visualState().noSaveCustomFrame().pieces().stream().anyMatch(piece->piece.tileIndex()>=0x2000));
            if(!sidekicks.isEmpty()) assertTrue(selected.visualState().noSaveCustomFrame().pieces().stream().anyMatch(piece->piece.tileIndex()<0x2000));
            presentation.setClearColor();
            org.lwjgl.opengl.GL11.glClear(org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT | org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT);
            presentation.draw();GameServices.graphics().flushScreenSpace();
            org.lwjgl.opengl.GL11.glFinish();
            writeCapture(com.openggf.graphics.ScreenshotCapture.captureFramebuffer(320,224),
                    sidekicks.isEmpty()?"s3k-no-save-mica":"s3k-no-save-mica-tails");
        }
        presentation.reset();
    }

    private static void writeCapture(com.openggf.graphics.RgbaImage image,String name) throws Exception {
        Path root=Path.of(System.getProperty("mica.captureDir","target/mica-screen-captures"));
        Files.createDirectories(root);
        var png=new java.awt.image.BufferedImage(image.width(),image.height(),java.awt.image.BufferedImage.TYPE_INT_ARGB);
        png.setRGB(0,0,image.width(),image.height(),image.pixels(),0,image.width());
        javax.imageio.ImageIO.write(png,"PNG",root.resolve(name+".png").toFile());
        long visible=java.util.Arrays.stream(image.pixels()).filter(pixel->(pixel&0xffffff)!=0).count();
        assertTrue(visible>100,"Capture must contain rendered art");
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
            assertTrue(resolved.getPlayableCharacterRegistry().find(playable.characterKey()).orElseThrow()
                    .movementAbilities().contains(CharacterDefinition.MovementAbility.SPINDASH));
            assertEquals(!game.equals("s1"), resolved.getPlayableCharacterRegistry()
                    .movementAbilities(playable.characterKey(), base)
                    .contains(CharacterDefinition.MovementAbility.SPINDASH));
            fixtureWorld.stepIdleFrames(120);
            for (int duck = 0; duck < 4; duck++) fixtureWorld.stepFrame(false, true, false, false, false);
            assertFalse(playable.getAir(), "settled on floor: " + game);
            assertEquals(base.getDonorCapabilities().resolveNativeId(CanonicalAnimation.DUCK), playable.getAnimationId(), "crouching: " + game + " x=" + playable.getCentreX() + " y=" + playable.getCentreY() + " speed=" + playable.getGSpeed() + " crouch=" + playable.getCrouching() + " angle=" + playable.getAngle());
            fixtureWorld.stepFrame(false, true, false, false, true);
            assertEquals(!game.equals("s1"), playable.getSpindash(), "native host capability: " + game);
            if (!game.equals("s1")) {
                assertEquals(base.getDonorCapabilities().resolveNativeId(CanonicalAnimation.SPINDASH),
                        playable.getAnimationId());
                fixtureWorld.stepFrame(false, true, false, false, false);
                assertTrue(playable.getSpindash());
                fixtureWorld.stepFrame(false, false, false, false, false);
                assertFalse(playable.getSpindash());
                assertTrue(Math.abs(playable.getGSpeed()) > 0, "release must launch MICA");
            }
            verifySignTiles(resolved, game);
            verifyContinuePresentation(resolved, game);
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
        assertTrue(List.of(284, 285, 286, 287).contains(player.getMappingFrame()),
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

    private void verifyContinuePresentation(GameModule module, String game) throws Exception {
        var host=CharacterPresentation.Host.valueOf(game.toUpperCase(Locale.ROOT));
        var definition=module.getPlayableCharacterRegistry().find(CharacterKey.parsePersisted(CODE)).orElseThrow();
        var set=definition.presentationSupplier().load(CODE).art().get(host).screenFrames();
        assertNotNull(set);
        assertFalse(set.banks().get("continue/player").isEmpty());
        var palette=com.openggf.game.presentation.ScreenFrameRenderer.palette(set);
        assertTrue(palette.dataEquals(definition.paletteSupplier().load(CODE)), "Screen art uses MICA's unchanged palette");
        ContinueScreenProvider screen=switch(host) {
            case S1 -> new com.openggf.game.sonic1.continuescreen.Sonic1ContinueScreenProvider();
            case S2 -> new com.openggf.game.sonic2.continuescreen.Sonic2ContinueScreenProvider();
            case S3K -> new com.openggf.game.sonic3k.continuescreen.Sonic3kContinueScreenProvider();
        };
        screen.initialize(3);
        for(int i=0;i<100;i++) { screen.update(false,false); screen.draw(); }
        screen.update(true,false);
        for(int i=0;i<150 && !screen.isFinished();i++) { screen.update(false,false); screen.draw(); }
        assertTrue(screen.isAccepted());
        assertTrue(screen.isFinished());
        screen.reset();
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
        for (String surface : List.of("continue", "portrait", "snowboard")) {
            Path source = project.resolve("art/mica/" + surface);
            Path destination = classes.resolve("art/mica/" + surface);
            Files.createDirectories(destination);
            try (var files = Files.list(source)) {
                for (var file : files.toList()) Files.copy(file, destination.resolve(file.getFileName()));
            }
        }
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
