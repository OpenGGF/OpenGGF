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

    @AfterEach void cleanup() {
        TestEnvironment.resetAll();
        com.openggf.graphics.RenderContext.reset();
    }

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
            byte[] encoded = Files.readAllBytes(temp.resolve("classes/art/mica.ggfp"));
            var sheet = com.openggf.level.objects.PlayableSheetReader.read(encoded);
            var source = com.openggf.level.objects.PlayableSheetMaterializer.read(encoded).art();
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
                    assertEquals(source.dplcFrames().get(sheet.nativeFrames().get("swing/" + (frame - 0x78))),
                            player.getSpriteRenderer().dplcFrame(frame));
                    if (i % 10 == 0) com.openggf.graphics.ScreenshotCapture.savePNG(session.render(), output.resolve("swing-" + i + ".png"));
                }
                csv.append(session.stateLine(i, null)).append('\n');
            }
            Files.writeString(output.resolve("state.csv"), csv);
            assertTrue(observed > 0 && swings > 10, "native vine must progress from hang into sustained swing");
        }
    }

    @Test @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_3K)
    void fbzChainUsesMicaHang() throws Exception { captureNativeHangObject(false); }
    @Test @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_3K)
    void mhzPulleyUsesMicaHang() throws Exception { captureNativeHangObject(true); }

    private void captureNativeHangObject(boolean pulley) throws Exception {
        Path output = Path.of(System.getProperty("mica.poses.captureRoot", "target/mica-poses")).resolve(pulley ? "mhz-pulley" : "fbz-chain");
        Files.createDirectories(output);
        var settings = new com.openggf.tools.GameplayCaptureSession.Settings(320, CODE, "", "off", null, null, null);
        try (var session = new com.openggf.tools.GameplayCaptureSession(settings)) {
            session.applyMod(buildSample());
            GameServices.configuration().setSessionOverride(SonicConfiguration.DISPLAY_SPRITE_SCANLINE_LIMIT, true);
            session.boot(Path.of(System.getProperty("s3k.rom.path")), pulley ? 7 : 4, pulley ? 1 : 0, settings);
            for (int i = 0; i < 120; i++) session.step(null);
            var player = session.player();
            player.setCentreY((short) (player.getCentreY() - 80));
            // Declared local airborne encounter: production object in its native host zone.
            var spawn = new com.openggf.level.objects.ObjectSpawn(player.getCentreX() + (pulley ? 0x32 : 0),
                    player.getCentreY() - (pulley ? 46 : 4), pulley ? 6 : 0x72, pulley ? 4 : 0x84, 0, false, 0);
            assertNotNull(GameServices.level().getObjectManager().createDynamicObject(() -> pulley
                    ? new com.openggf.game.sonic3k.objects.MhzPulleyLiftObjectInstance(spawn)
                    : new com.openggf.game.sonic3k.objects.FbzChainLinkObjectInstance(spawn)));
            player.setAir(true); player.setXSpeed((short) 0); player.setYSpeed((short) 0x100);
            var source = com.openggf.level.objects.PlayableSheetMaterializer.read(Files.readAllBytes(temp.resolve("classes/art/mica.ggfp"))).art();
            var hang = source.animationSet().getScript(CanonicalAnimation.HANG.ordinal()).frames();
            var csv = new StringBuilder(com.openggf.tools.GameplayCaptureSession.stateHeader()).append('\n');
            int seen = 0;
            for (int i = 0; i < 90; i++) {
                var input = new com.openggf.debug.playback.Bk2FrameInput(i, pulley && i > 20 ? 2 : 0, 0, false, "positioned native hang");
                session.step(input);
                int frame = player.getMappingFrame();
                if (frame >= 0x90 && frame <= 0x92) {
                    seen++;
                    assertEquals(source.dplcFrames().get(hang.get(frame - 0x90)), player.getSpriteRenderer().dplcFrame(frame));
                    if (i % 15 == 0) com.openggf.graphics.ScreenshotCapture.savePNG(session.render(), output.resolve("frame-" + i + ".png"));
                }
                csv.append(session.stateLine(i, input)).append('\n');
            }
            Files.writeString(output.resolve("state.csv"), csv);
            assertTrue(seen > 30, "native object must sustain an authored MICA hang pose");
        }
    }

    @Test @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_2)
    void tornadoWaitingWriteUsesMicaStand() throws Exception {
        Path output = Path.of(System.getProperty("mica.poses.captureRoot", "target/mica-poses")).resolve("s2-tornado-wait");
        Files.createDirectories(output);
        var settings = new com.openggf.tools.GameplayCaptureSession.Settings(320, CODE, "", "off", null, null, null);
        try (var session = new com.openggf.tools.GameplayCaptureSession(settings)) {
            session.applyMod(buildSample());
            GameServices.configuration().setSessionOverride(SonicConfiguration.DISPLAY_SPRITE_SCANLINE_LIMIT, true);
            session.boot(Path.of(System.getProperty("sonic2.rom.path")), 9, 0, settings);
            for (int i = 0; i < 30; i++) session.step(null);
            var plane = GameServices.level().getObjectManager().activeObjectsOfType(
                    com.openggf.game.sonic2.objects.TornadoObjectInstance.class).stream().findFirst().orElseThrow();
            // Component pose probe: execute ObjB2's actual waiting helper once.
            // This proves the raw byte's art binding, not a completed WFZ landing route.
            var helper = plane.getClass().getDeclaredMethod("applyWaitingAnimation", com.openggf.sprites.playable.AbstractPlayableSprite.class);
            helper.setAccessible(true); helper.invoke(plane, session.player());
            assertEquals(1, session.player().getMappingFrame());
            var source = com.openggf.level.objects.PlayableSheetMaterializer.read(Files.readAllBytes(temp.resolve("classes/art/mica.ggfp"))).art();
            int wait = source.animationSet().getScript(CanonicalAnimation.WAIT.ordinal()).frames().getFirst();
            assertEquals(source.dplcFrames().get(wait), session.player().getSpriteRenderer().dplcFrame(1));
            com.openggf.graphics.ScreenshotCapture.savePNG(session.render(), output.resolve("native-wait.png"));
            Files.writeString(output.resolve("state.csv"), com.openggf.tools.GameplayCaptureSession.stateHeader() + "\n" + session.stateLine(30, null) + "\n");
        }
    }

    @Test @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_3K)
    void existingNativePoseArtWinsOverCanonicalFallback() throws Exception {
        var settings = new com.openggf.tools.GameplayCaptureSession.Settings(320, CODE, "", "off", null, null, null);
        try (var session = new com.openggf.tools.GameplayCaptureSession(settings)) {
            session.applyMod(buildSample());
            session.boot(Path.of(System.getProperty("s3k.rom.path")), 0, 0, settings);
            byte[] encoded = Files.readAllBytes(temp.resolve("classes/art/mica.ggfp"));
            var sheet = com.openggf.level.objects.PlayableSheetReader.read(encoded);
            var source = com.openggf.level.objects.PlayableSheetMaterializer.read(encoded).art();
            var expected = new LinkedHashMap<Integer,String>();
            expected.put(0x24, "bar/release");
            for (int frame = 0x55; frame <= 0x5B; frame++) expected.put(frame, "twist/" + (frame - 0x55));
            for (int frame = 0x5C; frame <= 0x62; frame++) expected.put(frame, "pole/" + (frame - 0x5C));
            for (int frame = 0x63; frame <= 0x68; frame++) expected.put(frame, "bar/" + (frame - 0x63));
            for (int frame = 0x69; frame <= 0x77; frame++) expected.put(frame, "tumble/" + (frame - 0x69));
            for (int frame = 0x78; frame <= 0x7F; frame++) expected.put(frame, "swing/" + (frame - 0x78));
            expected.put(0x94, "bar-end/0"); expected.put(0x95, "bar-end/1");
            expected.put(0xCA, "crane/fall"); expected.put(0xCB, "hang/crane");
            for (var entry : expected.entrySet()) {
                int target = sheet.nativeFrames().get(entry.getValue());
                assertTrue(target >= 98, "reuse the existing native-pose asset instead of a core fallback");
                assertEquals(source.dplcFrames().get(target), session.player().getSpriteRenderer().dplcFrame(entry.getKey()), entry.getValue());
            }
            // Raw roll and stand bytes reuse the approved core cycles, so objects that
            // write $96-$9A/$BA directly (AIZ intro, slots) match her normal roll and idle.
            // ROLL interleaves the solid frame like native $96,$9A,$97,$9A,...
            var roll = sheet.animations().get("ROLL").steps().stream()
                    .map(com.openggf.level.objects.PlayableSheetReader.AnimationStep::frameIndex).toList();
            var core = new LinkedHashMap<Integer,Integer>();
            for (int i = 0; i < 4; i++) core.put(0x96 + i, roll.get(i * 2));
            core.put(0x9A, roll.get(1));
            core.put(0xBA, sheet.animations().get("WAIT").steps().get(0).frameIndex());
            for (var entry : core.entrySet()) {
                String name = entry.getKey() == 0xBA ? "ride/stand" : "roll/" + (entry.getKey() - 0x96);
                assertEquals(entry.getValue(), sheet.nativeFrames().get(name), name);
                assertEquals(source.dplcFrames().get(entry.getValue()),
                        session.player().getSpriteRenderer().dplcFrame(entry.getKey()), name);
            }
        }
    }

    @Test @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_3K)
    void aizPlaneIntroUsesMicaAndOwnedPoweredPalette() throws Exception {
        Path output = Path.of(System.getProperty("mica.poses.captureRoot", "target/mica-poses")).resolve("aiz-plane-intro");
        Files.createDirectories(output);
        var settings = new com.openggf.tools.GameplayCaptureSession.Settings(320, CODE, "", "off", null, null, null);
        try (var session = new com.openggf.tools.GameplayCaptureSession(settings)) {
            session.applyMod(buildSample());
            GameServices.configuration().setSessionOverride(SonicConfiguration.S3K_SKIP_INTROS, false);
            GameServices.configuration().setSessionOverride(SonicConfiguration.DISPLAY_SPRITE_SCANLINE_LIMIT, true);
            session.boot(Path.of(System.getProperty("s3k.rom.path")), 0, 0, settings);
            byte[] encoded = Files.readAllBytes(temp.resolve("classes/art/mica.ggfp"));
            var sheet = com.openggf.level.objects.PlayableSheetReader.read(encoded);
            var source = com.openggf.level.objects.PlayableSheetMaterializer.read(encoded).art();
            boolean normal = false, powered = false;
            var csv = new StringBuilder(com.openggf.tools.GameplayCaptureSession.stateHeader()).append('\n');
            for (int i = 0; i < 900; i++) {
                session.step(null); session.renderFrame();
                for (var actor : GameServices.level().getObjectManager().activeObjectsOfType(com.openggf.game.sonic3k.objects.AizPlaneIntroInstance.class)) {
                    var renderer = (PlayerSpriteRenderer) privateField(actor, "sonicRenderer");
                    if (renderer == null) continue;
                    assertEquals(source.dplcFrames().get(sheet.nativeFrames().get("ride/stand")), renderer.dplcFrame(0xBA));
                    assertTrue(renderer.getRenderContext().getEffectivePaletteLine(renderer.paletteLine()) > 3);
                    boolean superPose = (boolean)privateField(actor, "superSonicActive");
                    int actorX = (int) privateField(actor, "currentX");
                    int actorY = (int) privateField(actor, "currentY");
                    boolean visible = actorX >= 0xC0 && actorX <= 0x180 && actorY >= 0xA0 && actorY <= 0x140;
                    if (!superPose && !normal && visible && (int) privateField(actor, "mappingFrame") == 0xBA) {
                        normal = true;
                        com.openggf.graphics.ScreenshotCapture.savePNG(session.render(), output.resolve("normal.png"));
                    }
                    if (superPose && !powered && visible) {
                        powered = true;
                        var colour = renderer.getRenderContext().getPalette(renderer.paletteLine()).getColor(4);
                        assertTrue(Byte.toUnsignedInt(colour.r) > Byte.toUnsignedInt(colour.b), "Gilded actor uses the authored gold cycle");
                        com.openggf.graphics.ScreenshotCapture.savePNG(session.render(), output.resolve("powered.png"));
                    }
                }
                csv.append(session.stateLine(i, null)).append('\n');
                if (normal && powered) break;
            }
            Files.writeString(output.resolve("state.csv"), csv);
            assertTrue(normal && powered, "native intro must reach both actor banks");
            int remaining = 2400;
            while (remaining-- > 0 && !GameServices.level().getObjectManager()
                    .activeObjectsOfType(com.openggf.game.sonic3k.objects.AizPlaneIntroInstance.class).isEmpty()) {
                session.step(null);
            }
            assertTrue(remaining > 0, "custom Sonic intro must reach native retirement");
            // The plane hands control to the native Knuckles finale before retirement.
            while (remaining-- > 0 && session.player().isControlLocked()) session.step(null);
            assertFalse(session.player().isControlLocked(), "native Knuckles finale releases MICA");
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
                if (frame == 178) {
                    assertTrue(session.player().getSpindash(), "native charge " + game);
                    var dust = session.player().getSpindashDustController();
                    assertNotNull(dust, "canonical character gets host ROM dust " + game);
                    assertNotNull(dust.getRenderer());
                    // S3K renders the published SAT, so explicitly republish each
                    // diagnostic draw; changing a live controller cannot change it.
                    republishSprites();
                    var withDust = session.render();
                    session.player().setSpindashDustController(null);
                    republishSprites();
                    var withoutDust = session.render();
                    session.player().setSpindashDustController(dust);
                    republishSprites();
                    assertFalse(java.util.Arrays.equals(withDust.pixels(), withoutDust.pixels()),
                            "charged dust must contribute visible pixels " + game);
                    assertNotNull(com.openggf.level.objects.SkidDustObjectInstance.create(session.player()),
                            "skid puffs use the same independent ROM renderer");
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
        Path output=Path.of(System.getProperty("mica.s2.captureRoot", "target/mica-s2-ending-captures")).resolve("s1");
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
    void micaS2EndingCapture() throws Exception { captureS2Ending(false, ""); }
    @Test @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_2)
    void micaS2SuperEndingCapture() throws Exception { captureS2Ending(true, ""); }

    @Test @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_2)
    void micaS2EndingWithTailsCapture() throws Exception { captureS2Ending(false, "tails"); }

    private void captureS2Ending(boolean powered, String sidekick) throws Exception {
        Path output=Path.of(System.getProperty("mica.s2.captureRoot", "target/mica-s2-ending-captures")).resolve(powered?(sidekick.equals("mica-and-talon:talon")?"mica-talon-super":"s2-super"):sidekick.isEmpty()?"mica-alone":sidekick.equals("tails")?"mica-tails":"mica-talon");
        Files.createDirectories(output);
        var settings=new com.openggf.tools.GameplayCaptureSession.Settings(320,CODE,sidekick,"off",null,null,null);
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
            var definition = com.openggf.game.presentation.StageCharacterPresentation.activeDefinition();
            assertFalse(definition.presentationSupplier().load(CODE).endings().containsKey(EndingSpriteSet.Surface.S2_PHOTOS));
            var seenPhotos = new HashSet<Integer>();
            var csv=new StringBuilder("frame,state,photo,objCe,rotation,white\n");
            String last="";
            for(int i=0;i<6000 && loop.getCurrentGameMode()==GameMode.ENDING_CUTSCENE;i++) {
                loop.step(); renderEnding(engine);
                String state=privateField(manager,"state").toString();
                if (state.startsWith("PALETTE") && (int)privateField(adapter,"white")==0) {
                    int index=(int)privateField(manager,"photoIndex");
                    if (index>=0 && index<4 && seenPhotos.add(index)) {
                        var display=(com.openggf.level.Palette[])privateField(manager,"displayPalettes");
                        var photos=(com.openggf.level.Palette)privateField(manager,"photosPalette");
                        assertTrue(photos.dataEquals(display[0]), "native photo palette on line 0");
                        com.openggf.graphics.ScreenshotCapture.savePNG(renderEnding(engine),output.resolve("photo-"+(index+1)+".png"));
                    }
                }
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
            assertEquals(Set.of(0,1,2,3),seenPhotos);
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
                if(!powered || entry.getKey()==EndingSpriteSet.Surface.S2_PHOTOS
                        || entry.getKey()==EndingSpriteSet.Surface.S2_TORNADO_PILOT) {
                    for(int i=0;i<16;i++) {
                        var c=palette.getColor(i);
                        assertEquals(sets.get(entry.getKey()).rgb()[i],((c.r&255)<<16)|((c.g&255)<<8)|(c.b&255));
                    }
                } else {
                    var poweredDefinition=com.openggf.game.presentation.StageCharacterPresentation.activeDefinition();
                    var cycle=poweredDefinition.superFormSupplier().load(CODE).games().get(GameId.S2)
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
            for(var surface:List.of(StageSpriteSet.Surface.S2_HALF_PIPE,StageSpriteSet.Surface.S3K_BLUE_SPHERE,
                    StageSpriteSet.Surface.S3K_SLOT_MACHINE,StageSpriteSet.Surface.S3K_COMPETITION)) {
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
        var preview = com.openggf.game.session.WorldSessionPolicyAccess.getService(
                com.openggf.game.session.SessionManager.getCurrentWorldSession(),
                com.openggf.game.mutators.HomingAttackRuntime.class);
        assertTrue(reticleFlag(preview, "reticleVisible"), "MICA previews an airborne badnik");
        fixture.stepFrame(false, false, false, false, true);
        var runtime = com.openggf.game.session.WorldSessionPolicyAccess.getService(
                com.openggf.game.session.SessionManager.getCurrentWorldSession(),
                com.openggf.game.mutators.HomingAttackRuntime.class);
        assertEquals(com.openggf.game.mutators.HomingAttackRuntime.Phase.HOMING, runtime.phase());
        assertEquals(powered ? com.openggf.game.mutators.HomingAttackRuntime.Mode.INSTAGIB
                : com.openggf.game.mutators.HomingAttackRuntime.Mode.HOMING, runtime.capture().launchMode());
        // The Buzzer sits ahead and above, so the strike flies forward-up
        // (HOMING_DIVE_FORWARD_UP, sheet frames 114-117 after the 256 native frames).
        assertTrue(List.of(370, 371, 372, 373).contains(player.getMappingFrame()),
                "The strike selects the authored forward-up dive pose: " + player.getMappingFrame());
        assertEquals(player.resolveAnimationId(CanonicalAnimation.ROLL), player.getAnimationId(),
                "The visual dive must retain native roll attack semantics");
        assertTrue(reticleFlag(runtime, "reticleVisible"));
        assertTrue(reticleFlag(runtime, "reticleLocked"));
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
        player.setAir(false);
        com.openggf.sprites.playable.PlayableSpriteInternalAccess.movementTick(player);
        runtime.beforeMovement(player);
        assertFalse(reticleFlag(runtime, "reticleVisible"), "landing hides MICA's reticle");
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

    private static void republishSprites() {
        com.openggf.level.LevelSpritePresentation.prepare(GameServices.level(), GameServices.sprites());
        com.openggf.level.LevelSpritePresentation.publishPreparedScene(GameServices.level());
    }

    private static boolean reticleFlag(com.openggf.game.mutators.HomingAttackRuntime runtime, String name) {
        try {
            var field = runtime.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.getBoolean(runtime);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    @Test @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_1)
    void talonS1() throws Exception { talonAcceptance(new com.openggf.game.sonic1.Sonic1GameModule(), "s1"); }
    @Test @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_2)
    void talonS2() throws Exception { talonAcceptance(new Sonic2GameModule(), "s2"); }
    @Test @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_3K)
    void talonS3k() throws Exception { talonAcceptance(new com.openggf.game.sonic3k.Sonic3kGameModule(), "s3k"); }

    private void talonAcceptance(GameModule base, String game) throws Exception {
        String talonCode = "mica-and-talon:talon";
        try (CatalogFixture fixture = load(buildSample())) {
            var config = SonicConfigurationService.getInstance();
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, talonCode);
            config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
            config.setConfigValue(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);
            config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);
            var resolver = new ModuleResolutionService(List.of(), new EffectiveCatalogPatchEnablement(fixture.effective()),
                    new LogicalRomResolver(() -> null), config, ignored -> fixture.plan());
            var prepared = resolver.prepareLaunch(ModuleResolutionService.LaunchPolicy.STANDARD);
            var store = new LaunchProfileStore(config, resolver, prepared);
            var entry = game.equals("s1") ? MasterTitleScreen.GameEntry.SONIC_1
                    : game.equals("s2") ? MasterTitleScreen.GameEntry.SONIC_2 : MasterTitleScreen.GameEntry.SONIC_3K;
            var selection = LaunchProfile.stockFor(entry);
            for (int i=0; i<12 && !selection.mainCharacter().equals(CODE); i++)
                selection = store.withNext(selection, LaunchProfile.Row.MAIN_CHARACTER, entry);
            assertEquals(CODE, selection.mainCharacter());
            assertEquals(talonCode, selection.sidekick(), "MICA's declared default team");
            assertEquals(talonCode, store.sanitize(selection, entry).sidekick());
            GameModule resolved = resolver.resolveForLaunch(base,
                    new GameplayLaunchRequest(game, talonCode, List.of()), ModuleResolutionService.LaunchPolicy.STANDARD);
            GameModuleRegistry.setCurrent(resolved);
            com.openggf.game.session.SessionManager.clear();
            TestEnvironment.activeGameplayMode();
            var world = com.openggf.tests.HeadlessTestFixture.builder().withZoneAndAct(0, 0).build();
            var talon = world.sprite();
            assertEquals(talonCode, talon.characterKey().persisted());
            assertTrue(com.openggf.sprites.playable.PlayableArchetype.isTails(talon));
            assertEquals(PlayerCharacter.TAILS_ALONE,
                    com.openggf.game.session.ActiveGameplayTeamResolver.resolvePlayerCharacter(config));
            assertEquals(SecondaryAbility.FLY, talon.getSecondaryAbility());
            assertNull(talon.getTailsTailsController(), "wings live in the body sheet");
            var definition = resolved.getPlayableCharacterRegistry().find(talon.characterKey()).orElseThrow();
            var encoded = Files.readAllBytes(temp.resolve("classes/art/talon.ggfp"));
            var sheet = com.openggf.level.objects.PlayableSheetReader.read(encoded);
            var materialized = com.openggf.level.objects.PlayableSheetMaterializer.read(encoded);
            assertEquals(282, sheet.frames().size());
            assertEquals(47, sheet.animations().size());
            assertEquals(6, sheet.animations().get("TAILS_FLY").steps().size());
            assertEquals(6, sheet.animations().get("TAILS_FLY_TIRED").steps().size());
            assertEquals(materialized.art().dplcFrames(), definition.artSupplier().load(talonCode).dplcFrames());
            assertTrue(materialized.palette().dataEquals(definition.paletteSupplier().load(talonCode)));
            assertFalse(materialized.palette().dataEquals(resolved.getPlayableCharacterRegistry()
                    .find(CharacterKey.parsePersisted(CODE)).orElseThrow().paletteSupplier().load(CODE)));
            var solar = definition.superFormSupplier().load(talonCode).games().get(base.getGameId());
            assertEquals(Set.of(SuperFormProfile.Tier.SUPER), solar.keySet());
            assertEquals(PhysicsProfile.SONIC_3K_SUPER_TAILS, solar.get(SuperFormProfile.Tier.SUPER).physics());
            assertEquals(game.equals("s3k") ? "TALO" : "TALON", definition.presentationSupplier().load(talonCode, base.getGameId()).hudName());
            talon.setCentreY((short) 120);
            talon.setAir(true); talon.setJumping(true); talon.setRolling(true); talon.setRollingJump(true);
            var movement = new com.openggf.sprites.managers.PlayableSpriteMovement(talon);
            for (String name : List.of("jumpReleasedSinceJump", "inputJump", "inputJumpPress")) {
                var field = movement.getClass().getDeclaredField(name); field.setAccessible(true); field.setBoolean(movement, true);
            }
            var jump = movement.getClass().getDeclaredMethod("doJumpHeight"); jump.setAccessible(true); jump.invoke(movement);
            assertTrue(talon.getTailsFlightController().isActive(), "airborne repress must enter flight in " + game);
            talon.setDoubleJumpFlag(2); talon.setYSpeed((short) 0);
            talon.getTailsFlightController().updateVertical(false, false, 2);
            assertEquals(-0x20, talon.getYSpeed());
            talon.setSuperSonic(true); talon.setDoubleJumpFlag(2); talon.setYSpeed((short) 0);
            talon.getTailsFlightController().updateVertical(false, false, 2);
            assertEquals(-0x40, talon.getYSpeed(), "Solar doubles the native flap acceleration");
            talon.setSuperSonic(false);
            if (game.equals("s2")) verifyTalonDive(world, talon);
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, CODE);
            config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, talonCode);
            var teamModule = resolver.resolveForLaunch(base, new GameplayLaunchRequest(game, CODE, List.of(talonCode)),
                    ModuleResolutionService.LaunchPolicy.STANDARD);
            GameModuleRegistry.setCurrent(teamModule);
            com.openggf.game.session.SessionManager.clear();
            var teamWorld = com.openggf.tests.HeadlessTestFixture.builder().withZoneAndAct(0, 0).build();
            assertEquals(CODE, teamWorld.sprite().characterKey().persisted());
            var follower = GameServices.sprites().getSidekicks().getFirst();
            assertEquals(talonCode, follower.characterKey().persisted());
            assertTrue(follower.isCpuControlled());
            assertNotNull(follower.getCpuController());
            teamWorld.stepIdleFrames(120);
            int followerX = follower.getCentreX();
            for (int i=0; i<90; i++) teamWorld.stepFrame(false, false, false, true, false);
            assertNotEquals(followerX, follower.getCentreX(), "native CPU must follow MICA's movement");
            assertTrue(teamModule.getPlayableCharacterRegistry().find(follower.characterKey()).orElseThrow()
                    .respawnStrategyFactory().create(follower.getCpuController()) instanceof com.openggf.sprites.playable.TailsRespawnStrategy);
        }
    }

    @Test @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_1)
    void talonFlightArtS1() throws Exception { talonFlightArt("sonic1.rom.path"); }
    @Test @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_2)
    void talonFlightArtS2() throws Exception { talonFlightArt("sonic2.rom.path"); }
    @Test @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_3K)
    void talonFlightArtS3k() throws Exception { talonFlightArt("s3k.rom.path"); }

    private void talonFlightArt(String romProperty) throws Exception {
        var settings = new com.openggf.tools.GameplayCaptureSession.Settings(320, "mica-and-talon:talon", "", "off", null, null, null);
        try (var session = new com.openggf.tools.GameplayCaptureSession(settings)) {
            session.applyMod(buildSample());
            session.boot(Path.of(System.getProperty(romProperty)), 0, 0, settings);
            var talon = session.player();
            var source = com.openggf.level.objects.PlayableSheetMaterializer.read(
                    Files.readAllBytes(temp.resolve("classes/art/talon.ggfp"))).art().animationSet();
            for (int i = 0; i < 60; i++) session.step(null);
            talon.setCentreY((short) (talon.getCentreY() - 80));
            talon.setAir(true); talon.getTailsFlightController().activate();
            talon.setYSpeed((short) -0x80);
            session.step(null); session.step(null);
            var ascentFrames = new java.util.HashSet<Integer>();
            var ascentStates = new java.util.ArrayList<String>();
            for (int i = 0; i < 12; i++) {
                session.step(null);
                ascentFrames.add(talon.getMappingFrame());
                ascentStates.add(talon.getAnimationId() + "/" + talon.getForcedAnimationId()
                        + "/" + talon.getAnimationFrameIndex() + "/" + talon.getAnimationTick());
                assertTrue(source.getScript(CanonicalAnimation.TAILS_FLY_ASCEND.ordinal()).frames().stream()
                        .map(frame -> frame + 256).toList().contains(talon.getMappingFrame()), "ascent frame " + talon.getMappingFrame() + " in " + romProperty);
            }
            assertEquals(6, ascentFrames.size(), "integrated ascent wing cycle in " + romProperty
                    + " (animation/forced/cursor/timer): " + ascentStates);
            int nativeAscent = talon.resolveAnimationId(CanonicalAnimation.TAILS_FLY_ASCEND);
            if (nativeAscent >= 0) {
                talon.setYSpeed((short) -0x80);
                talon.setForcedAnimationId(nativeAscent);
                var forcedFrames = new java.util.HashSet<Integer>();
                for (int i = 0; i < 12; i++) {
                    session.step(null);
                    forcedFrames.add(talon.getMappingFrame());
                }
                assertEquals(6, forcedFrames.size(), "forced integrated wing cycle in " + romProperty);
                talon.setForcedAnimationId(-1);
            }
            talon.setDoubleJumpProperty((byte) 0);
            for (int i = 0; i < 12; i++) {
                session.step(null);
                assertTrue(source.getScript(CanonicalAnimation.TAILS_FLY_TIRED.ordinal()).frames().stream()
                        .map(frame -> frame + 256).toList().contains(talon.getMappingFrame()), "fatigue uses TALON tired wings in " + romProperty);
            }
        }
    }

    private void verifyTalonDive(com.openggf.tests.HeadlessTestFixture world,
            com.openggf.sprites.playable.AbstractPlayableSprite talon) throws Exception {
        var runtime = com.openggf.game.session.WorldSessionPolicyAccess.getService(
                com.openggf.game.session.SessionManager.getCurrentWorldSession(),
                com.openggf.game.mutators.HomingAttackRuntime.class);
        for (boolean solar : List.of(false, true)) {
            runtime.resetForMissingSnapshot();
            talon.setSuperSonic(solar);
            talon.setCentreX((short) (GameServices.camera().getX() + 80));
            talon.setCentreY((short) (GameServices.camera().getY() + 48));
            talon.setAir(true); talon.setJumping(false); talon.setYSpeed((short) 0);
            talon.getTailsFlightController().activate();
            var buzzer = GameServices.level().getObjectManager().createDynamicObject(() ->
                    new com.openggf.game.sonic2.objects.badniks.BuzzerBadnikInstance(
                            new com.openggf.level.objects.ObjectSpawn(talon.getCentreX() + 96, talon.getCentreY() + 32,
                                    com.openggf.game.sonic2.constants.Sonic2ObjectIds.BUZZER, 0, 0, false, 0)));
            world.stepFrame(false, true, false, false, false);
            assertTrue(reticleFlag(runtime, "reticleVisible"), "down in flight previews a native target");
            world.stepFrame(false, false, false, false, false);
            assertFalse(reticleFlag(runtime, "reticleVisible"), "release hides the reticle");
            world.stepFrame(false, true, false, false, false);
            assertTrue(reticleFlag(runtime, "reticleVisible"));
            talon.setAir(false);
            world.stepFrame(false, true, false, false, false);
            assertFalse(reticleFlag(runtime, "reticleVisible"), "landing hides the reticle");
            talon.setAir(true); talon.setJumping(false);
            talon.getTailsFlightController().activate();
            world.stepFrame(false, true, false, false, true);
            assertFalse(talon.getTailsFlightController().isActive());
            assertEquals(com.openggf.game.mutators.HomingAttackRuntime.Phase.HOMING, runtime.phase());
            assertEquals(solar ? com.openggf.game.mutators.HomingAttackRuntime.Mode.INSTAGIB
                    : com.openggf.game.mutators.HomingAttackRuntime.Mode.HOMING, runtime.capture().launchMode());
            var registry = com.openggf.game.session.SessionManager.getCurrentGameplayMode().getRewindRegistry();
            var before = registry.capture();
            var expected = new ArrayList<String>();
            for (int i = 0; i < 20; i++) {
                world.stepIdleFrames(1);
                expected.add(talon.getCentreX() + ":" + talon.getCentreY() + ":" + runtime.capture());
            }
            assertTrue(buzzer.isDestroyed(), "dive kills through native badnik touch");
            registry.restore(before);
            for (int i = 0; i < 20; i++) {
                world.stepIdleFrames(1);
                assertEquals(expected.get(i), talon.getCentreX() + ":" + talon.getCentreY() + ":" + runtime.capture());
            }
        }
    }

    @Test @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_3K)
    void competitionMiniaturesRenderBothCharactersAndNativeReference() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(com.openggf.tools.PresentationOffscreenTestContext.requested());
        Path output=Path.of(System.getProperty("competition.capture.output","target/competition-capture"));
        Files.createDirectories(output);
        for(boolean custom:List.of(false,true)) {
            var settings=new com.openggf.tools.GameplayCaptureSession.Settings(320,custom?CODE:"sonic",
                    custom?"mica-and-talon:talon":"tails","off",null,null,null);
            try(var capture=new com.openggf.tools.GameplayCaptureSession(settings)) {
                if(custom) capture.applyMod(buildSample());
                capture.boot(Path.of(System.getProperty("s3k.rom.path")),0,0,settings);
                capture.step(null);
                var engine=com.openggf.game.session.EngineServices.current();
                engine.graphics().setDisplayColorProfile(com.openggf.graphics.color.DisplayColorProfile.RAW_RGB);
                engine.graphics().setDisplayColorExpansion(com.openggf.graphics.color.DisplayColorExpansion.FULL);
                var input=new com.openggf.control.InputHandler();
                try(var views=new com.openggf.game.sonic3k.competition.S3kCompetitionViews(engine.graphics())) {
                    var host=new com.openggf.game.sonic3k.competition.S3kCompetitionNativeHost(engine,GameServices.module(),input,
                            capture.loop()::setGameplayMode,new com.openggf.game.sonic3k.competition.S3kCompetitionRecords(new byte[80]),
                            ignored->{},views,com.openggf.level.LevelArtInternalAccess::initializeObjectArt);
                    try {
                        host.openArena(new com.openggf.game.sonic3k.competition.S3kCompetitionScreen.Launch(
                                new com.openggf.game.sonic3k.competition.S3kCompetitionSession.Config(
                                com.openggf.game.sonic3k.competition.S3kCompetitionSession.Mode.MATCH_RACE,
                                com.openggf.game.sonic3k.competition.S3kCompetitionSession.Arena.AZURE_LAKE,true,2),
                                List.of(com.openggf.game.sonic3k.competition.S3kCompetitionPlayer.Character.SONIC,
                                        com.openggf.game.sonic3k.competition.S3kCompetitionPlayer.Character.TAILS)));
                        if(custom) {
                            assertNotNull(host.artProvider().getSheet("competition_sonic_creator"));
                            assertNotNull(host.artProvider().getSheet("competition_tails_creator"));
                        }
                        int ticks=0;
                        while(!host.session().started() && ticks++<1200) competitionStep(host,input,com.openggf.control.LogicalInputSnapshot.neutral());
                        assertTrue(host.session().started());
                        var right=com.openggf.control.PlayerInputState.of(8,0,0,0,false,false);
                        var logical=com.openggf.control.LogicalInputSnapshot.ofPlayers(right,right);
                        String prefix=custom?"mica-talon":"sonic-tails";
                        var csv=new StringBuilder("frame,p1x,p1y,p1map,p2x,p2y,p2map\n");
                        for(int frame=0;frame<120;frame++) {
                            competitionStep(host,input,logical);
                            org.lwjgl.opengl.GL11.glViewport(0,0,320,224);
                            views.drawArena(host.world().context(),host,host.scroll());
                            com.openggf.graphics.ScreenshotCapture.savePNG(com.openggf.graphics.ScreenshotCapture.captureFramebuffer(320,224),
                                    output.resolve(prefix+String.format("-%03d.png",frame)));
                            var p1=host.players().get(0); var p2=host.players().get(1);
                            csv.append(frame+","+p1.centreX()+","+p1.centreY()+","+p1.mappingFrame()+","+p2.centreX()+","+p2.centreY()+","+p2.mappingFrame()+"\n");
                        }
                        Files.writeString(output.resolve(prefix+".csv"),csv);
                    } finally {host.closeArena();}
                }
            }
            com.openggf.tests.TestEnvironment.resetAll();
        }
    }
    @Test @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_3K)
    void competitionMenusRenderAuthoredPortraitsBesideNative() throws Exception {
        Path output=Path.of(System.getProperty("competition.menu.capture.output","target/competition-menu-capture"));
        Files.createDirectories(output);
        var settings=new com.openggf.tools.GameplayCaptureSession.Settings(320,CODE,"mica-and-talon:talon","off",null,null,null);
        try(var capture=new com.openggf.tools.GameplayCaptureSession(settings)) {
            capture.applyMod(buildSample());
            capture.boot(Path.of(System.getProperty("s3k.rom.path")),0,0,settings);
            capture.step(null);capture.step(null);
            var registry=com.openggf.game.session.SessionManager.getCurrentWorldSession().getPlayableCharacterRegistry();
            var portraits=new java.util.HashMap<Integer,CharacterPresentation>();
            for(var entry:Map.of(0,"mica-and-talon:mica",1,"mica-and-talon:talon").entrySet()) {
                var d=registry.find(CharacterKey.parsePersisted(entry.getValue())).orElseThrow();
                var p=d.presentationSupplier().load(entry.getValue(),GameId.S3K);
                assertEquals(2,p.competitionPortraits().size());portraits.put(entry.getKey(),p);
            }
            var rom=GameServices.rom().getRom();
            var assets=new com.openggf.game.sonic3k.competition.S3kCompetitionAssets(rom);
            byte[] block=new byte[80];for(int z=0;z<5;z++)for(int r=0;r<3;r++)block[z*16+12+r]=(byte)r;
            var records=new com.openggf.game.sonic3k.competition.S3kCompetitionRecords(block);
            var mode=com.openggf.game.sonic3k.competition.S3kCompetitionSession.Mode.GRAND_PRIX;
            var times=List.of(101,202,303,404,505);
            var first=new com.openggf.game.sonic3k.competition.S3kCompetitionSession.PlayerResult(0,com.openggf.game.sonic3k.competition.S3kCompetitionPlayer.Character.SONIC,5,times,1515,true,0);
            var second=new com.openggf.game.sonic3k.competition.S3kCompetitionSession.PlayerResult(1,com.openggf.game.sonic3k.competition.S3kCompetitionPlayer.Character.TAILS,5,times,1515,true,0);
            for(var phase:List.of(com.openggf.game.sonic3k.competition.S3kCompetitionScreen.Phase.PLAYER_SELECT,
                    com.openggf.game.sonic3k.competition.S3kCompetitionScreen.Phase.ARENA_SELECT,
                    com.openggf.game.sonic3k.competition.S3kCompetitionScreen.Phase.RESULTS,
                    com.openggf.game.sonic3k.competition.S3kCompetitionScreen.Phase.TIME_ATTACK_RESULTS)) {
                var result=new com.openggf.game.sonic3k.competition.S3kCompetitionSession.Result(mode,com.openggf.game.sonic3k.competition.S3kCompetitionSession.Arena.AZURE_LAKE,List.of(first,second),List.of(times,times));
                var view=new com.openggf.game.sonic3k.competition.S3kCompetitionScreen.MenuView(phase,8,0,true,mode,0,2,List.of(0,1),List.of(0x40,0x95),List.of(false,false),0,0,result);
                int[] baseline=null;
                for(boolean authored:new boolean[]{false,true}) {
                    var composer=new com.openggf.game.sonic3k.competition.S3kCompetitionMenuScene(rom,assets,authored?portraits:Map.of());
                    var scene=composer.compose(view,records);
                    int[] rgb=com.openggf.game.sonic3k.competition.S3kCompetitionMenuRenderer.rasterize(scene).rgb(scene.palettes(),new com.openggf.graphics.PaletteFadePresentation(),com.openggf.graphics.color.DisplayColorProfile.RAW_RGB,com.openggf.graphics.color.DisplayColorExpansion.FULL);
                    if(!authored)baseline=rgb;else assertFalse(java.util.Arrays.equals(baseline,rgb));
                    var graphics=GameServices.graphics();
                    graphics.setDisplayColorProfile(com.openggf.graphics.color.DisplayColorProfile.RAW_RGB);
                    graphics.setDisplayColorExpansion(com.openggf.graphics.color.DisplayColorExpansion.FULL);
                    try(var renderer=new com.openggf.game.sonic3k.competition.S3kCompetitionMenuRenderer(graphics)) {
                        com.openggf.graphics.PaletteUploadPresentation.withoutDisplayedFade(graphics,()->{renderer.draw(scene);graphics.flushScreenSpace();});
                        var image=com.openggf.graphics.ScreenshotCapture.captureFramebuffer(320,224);
                        for(int y=0;y<224;y++)for(int x=0;x<320;x++)assertEquals(0xff000000|rgb[y*320+x],image.argb(x,y));
                        com.openggf.graphics.ScreenshotCapture.savePNG(image,output.resolve(phase.name().toLowerCase()+ (authored?"-authored.png":"-native.png")));
                    }
                }
            }
        }
    }

    private static void competitionStep(com.openggf.game.sonic3k.competition.S3kCompetitionNativeHost host,
            com.openggf.control.InputHandler input,com.openggf.control.LogicalInputSnapshot logical) {
        input.setLogicalOverride(logical);
        var frame=host.world().context().plcFrameLifecycle().latchBeforeFadeUpdate();
        try { com.openggf.game.competition.NativeCompetitionIterationAccess.runAdmitted(frame,()->host.stepArena(logical,host.session())); }
        finally {frame.finish();}
    }

    /** Positioned presentation probe: live host boot/controller, production animations and renderer.
     * Pose controls are declared; this does not certify traversal or wall contact. */
    @Test @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_3K)
    void poweredBankCaptureAndRewind() throws Exception {
        Path jar = buildSample();
        Path output = Path.of(System.getProperty("mica.powered.captureRoot","target/mica-powered-captures"));
        for (String game : List.of("s2","s3k")) for (boolean nativeCharacter : List.of(false,true)) {
            int tiers = game.equals("s3k") ? 2 : 1;
            for (int tier = 0; tier < tiers; tier++) {
                String label = game+"-"+(nativeCharacter?"native":"mica")+"-"+(tier==1?"hyper":"super");
                var settings = new com.openggf.tools.GameplayCaptureSession.Settings(320,nativeCharacter?"sonic":CODE,"","off",null,null,null);
                try (var session = new com.openggf.tools.GameplayCaptureSession(settings)) {
                    if (!nativeCharacter) session.applyMod(jar);
                    session.boot(Path.of(System.getProperty(game.equals("s2")?"sonic2.rom.path":"s3k.rom.path")),0,1,settings);
                    for (int tick=0;tick<120;tick++) session.step(null);
                    var player=session.player(); var controller=player.getSuperStateController();
                    assertNotNull(controller);
                    var state=player.currentGameState();
                    for (int e=0;e<state.getChaosEmeraldCount();e++) state.markEmeraldCollected(e);
                    if(tier==1) for(int e=0;e<7;e++) state.markSuperEmeraldCollected(e);
                    player.currentLevelState().resumeTimer();
                    com.openggf.sprites.playable.ObjectControlState.none().applyTo(player);
                    player.addRings(80-player.getRingCount()); player.setAir(true); player.setJumping(true); player.setYSpeed((short)0x28);
                    player.setInvincibleFrames(0);
                    if(game.equals("s3k")) assertTrue(controller.activateFromAirAbility()); else controller.checkTransformationBeforeMove();
                    assertTrue(controller.isSuper());
                    var transforming=player.captureRewindState(true);
                    var transformScripts=player.getAnimationSet();
                    for(int tick=0;tick<100;tick++) session.step(null);
                    assertEquals(com.openggf.sprites.playable.SuperState.SUPER,controller.getState());
                    assertEquals(tier==1,controller.isHyperFormActive());
                    var active=player.captureRewindState(true);
                    if(!nativeCharacter) {
                        controller.debugDeactivate(); player.restoreRewindState(transforming);
                        assertEquals(transformScripts.getAllScripts(),player.getAnimationSet().getAllScripts());
                        assertEquals(com.openggf.sprites.playable.SuperState.TRANSFORMING,controller.getState());
                        GameServices.level().refreshPlayableSpriteArt();
                        assertEquals(transformScripts.getAllScripts(),player.getAnimationSet().getAllScripts());
                        player.restoreRewindState(active);
                    }
                    player.setAir(false); player.setJumping(false); player.setYSpeed((short)0);
                    com.openggf.sprites.playable.ObjectControlState.none().applyTo(player);
                    player.setDirection(com.openggf.physics.Direction.RIGHT); player.setAngle((byte)0);
                    Files.createDirectories(output.resolve(label));
                    var csv=new StringBuilder("pose,animation,mapping,centre_x,centre_y,rings,tier\n");
                    for(var pose:List.of(CanonicalAnimation.WAIT,CanonicalAnimation.WALK,CanonicalAnimation.RUN,CanonicalAnimation.PUSH,CanonicalAnimation.DUCK)) {
                        int id=player.resolveAnimationId(pose);
                        if(pose==CanonicalAnimation.RUN) id=player.resolveAnimationId(CanonicalAnimation.WALK);
                        player.setPushing(pose==CanonicalAnimation.PUSH);
                        player.setMovementInputActive(pose==CanonicalAnimation.WALK||pose==CanonicalAnimation.RUN);
                        player.setGSpeed((short)(pose==CanonicalAnimation.RUN?0x900:pose==CanonicalAnimation.WALK?0x300:0));
                        player.setForcedAnimationId(id); player.setAnimationId(id); player.setAnimationFrameIndex(0); player.setAnimationTick(0);
                        com.openggf.sprites.playable.ObjectControlState.nativeBit7FullControl().applyTo(player);
                        for(int tick=0;tick<12;tick++) session.step(null);
                        com.openggf.graphics.ScreenshotCapture.savePNG(session.render(),output.resolve(label).resolve(pose.name().toLowerCase(java.util.Locale.ROOT)+".png"));
                        csv.append(pose).append(',').append(player.getAnimationId()).append(',').append(player.getMappingFrame()).append(',').append(player.getCentreX()).append(',').append(player.getCentreY()).append(',').append(player.getRingCount()).append(',').append(tier).append('\n');
                    }
                    Files.writeString(output.resolve(label).resolve("states.csv"),csv);
                }
            }
        }
    }

    @Test @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_2)
    void talonS2EndingCapture() throws Exception { captureS2Ending(false, "mica-and-talon:talon"); }

    @Test @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_2)
    void talonS2SuperEndingCapture() throws Exception { captureS2Ending(true, "mica-and-talon:talon"); }

    @Test @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_3K)
    void talonS3kReunionCapture() throws Exception {
        Path output = Path.of(System.getProperty("talon.captureRoot", "target/talon-endings"));
        for (String main : new String[]{"mica-and-talon:mica", "mica-and-talon:talon", "sonic", "tails"}) {
            var settings = new com.openggf.tools.GameplayCaptureSession.Settings(320,main,
                    main.equals(CODE) ? "mica-and-talon:talon" : "", "off", null,null,null);
            try (var session = new com.openggf.tools.GameplayCaptureSession(settings)) {
                session.applyMod(buildSample());
                session.boot(com.openggf.tests.RomTestUtils.ensureSonic3kRomAvailable().toPath(),13,1,settings);
                var manager = GameServices.level().getObjectManager();
                for (var object : List.copyOf(manager.getActiveObjects())) manager.removeDynamicObject(object);
                var director=manager.createDynamicObject(() -> new com.openggf.game.sonic3k.ending.EndingRescueDirectorObjectInstance(
                        com.openggf.game.sonic3k.ending.EndingRescueDirectorObjectInstance.REUNION));
                // Positioned component entry at native beginReunion: prepare its real SSZ
                // resource handoff and let ScreenEvents load/fade/scroll credits normally.
                com.openggf.game.sonic3k.ending.EndingSceneResourceHandoff.queueSsz(manager.getObjectServices());
                ((com.openggf.game.sonic3k.runtime.EndingZoneRuntimeState)GameServices.zoneRuntimeState())
                        .screen().foregroundRoutine=40;
                Path dest = output.resolve("s3k-"+main.replace(':','-')); Files.createDirectories(dest);
                var csv = new StringBuilder("input,scene,routine,mapping\n");
                int finalPoseAt=-1;
                boolean soloTails=main.equals("tails")||main.equals("mica-and-talon:talon");
                for (int i=0; i<(soloTails?18000:2000); i++) {
                    session.step(null); session.renderFrame();
                    if (i%60==0) com.openggf.graphics.ScreenshotCapture.savePNG(session.render(), dest.resolve("frame-"+i+".png"));
                    int frame=(int)privateField(director,"mappingFrame");
                    int routine=(int)privateField(director,"routine");
                    csv.append(i).append(",debug-reunion-component-entry,").append(routine).append(',').append(frame).append('\n');
                    if (soloTails && routine>=8 && (frame==3||frame==4)) {
                        com.openggf.graphics.ScreenshotCapture.savePNG(session.render(),dest.resolve("reunion-pose-"+frame+".png"));
                        if(frame==4&&finalPoseAt<0)finalPoseAt=i;
                    }
                    if(finalPoseAt>=0&&i>finalPoseAt+120)break;
                }
                if (main.startsWith("mica-and-talon:")) {
                    var cacheField=Class.forName("com.openggf.game.sonic3k.ending.EndingCharacterPresentation").getDeclaredField("CACHE");
                    cacheField.setAccessible(true);
                    @SuppressWarnings("unchecked") var cache=(Map<Object,Map<EndingSpriteSet.Surface,PlayerSpriteRenderer>>)cacheField.get(null);
                    assertTrue(cache.getOrDefault(GameServices.level().getCurrentLevel(),Map.of()).containsKey(
                            main.equals(CODE) ? EndingSpriteSet.Surface.S3K_TORNADO_PILOT :
                            EndingSpriteSet.Surface.S3K_NORMAL_POSE), "TALON reunion reached the renderer");
                }
                Files.writeString(dest.resolve("state.csv"),csv);
            }
            Files.delete(temp.resolve("mica-and-talon.jar"));
            TestEnvironment.resetAll();
            com.openggf.graphics.RenderContext.reset();
            // buildSample owns this disposable test build and expects an empty destination.
            try (var files = Files.walk(temp.resolve("classes"))) { for (var file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file); }
        }
    }

    @Test @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_1)
    void talonSurfacesS1() throws Exception { captureTalonSurfaces("s1", "sonic1.rom.path"); }
    @Test @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_2)
    void talonSurfacesS2() throws Exception { captureTalonSurfaces("s2", "sonic2.rom.path"); }
    @Test @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_3K)
    void talonSurfacesS3k() throws Exception { captureTalonSurfaces("s3k", "s3k.rom.path"); }

    private void captureTalonSurfaces(String game, String romProperty) throws Exception {
        String priorSaveRoot = System.getProperty("openggf.saveRoot");
        System.setProperty("openggf.saveRoot", temp.resolve("talon-capture-saves").toString());
        try {
        Path jar = buildSample();
        String talon = "mica-and-talon:talon";
        Path output = Path.of(System.getProperty("talon.presentation.captureRoot", "target/talon-presentation"));
        Files.createDirectories(output);
        for (String mode : List.of("solo", "team", "native", "native-solo")) {
            String main = mode.equals("solo") ? talon : mode.equals("team") ? CODE : mode.equals("native-solo") && !game.equals("s1") ? "tails" : "sonic";
            String follower = mode.equals("team") ? talon : mode.equals("native") && !game.equals("s1") ? "tails" : "";
            var settings = new com.openggf.tools.GameplayCaptureSession.Settings(320, main, follower, "off", null, null, null);
            try (var session = new com.openggf.tools.GameplayCaptureSession(settings)) {
                session.applyMod(jar); session.boot(Path.of(System.getProperty(romProperty)), 0, 0, settings);
                for (int i = 0; i < 120; i++) session.step(null);
                var definition = com.openggf.game.presentation.StageCharacterPresentation.tailsDefinition();
                if (!mode.startsWith("native")) {
                    assertNotNull(definition); assertEquals(talon, definition.key().persisted());
                    var presentation = definition.presentationSupplier().load(talon, GameId.valueOf(game.toUpperCase(Locale.ROOT)));
                    assertEquals("TALON", presentation.displayName());
                    var host = CharacterPresentation.Host.valueOf(game.toUpperCase(Locale.ROOT));
                    var art = presentation.art().get(host);
                    assertEquals(16, art.hudLifeIcon().width()); assertEquals(16, art.oneUpFace().height());
                    assertNotNull(art.sign().face().backing()); assertNotNull(art.sign().spin3());
                    assertEquals(18, presentation.stages().get(StageSpriteSet.Surface.S2_HALF_PIPE).frames().size());
                    assertEquals(11, presentation.stages().get(StageSpriteSet.Surface.S3K_BLUE_SPHERE).frames().size());
                    assertTrue(com.openggf.game.presentation.StageSpriteCompiler.compile(presentation.stages().get(StageSpriteSet.Surface.S3K_BLUE_SPHERE), null).mappingFrames().getFirst().pieces().isEmpty());
                    if (game.equals("s1")) {
                        var name = com.openggf.game.sonic1.Sonic1CharacterPresentation.name(GameServices.rom().getRom(), presentation, CharacterPresentation.Style.HUD);
                        assertTrue(name.complete()); assertEquals(32, name.image().width());
                    }
                    if (mode.equals("team")) assertEquals(CODE,
                            com.openggf.game.presentation.StageCharacterPresentation.activeDefinition().key().persisted());
                }
                var spawn = new com.openggf.level.objects.ObjectSpawn(session.player().getCentreX() + 80,
                        session.player().getCentreY(), 0x26, game.equals("s1") ? 2 : 1, 0, false, 0);
                assertNotNull(GameServices.level().getObjectManager().createDynamicObject(() -> switch (game) {
                    case "s1" -> new com.openggf.game.sonic1.objects.Sonic1MonitorObjectInstance(spawn);
                    case "s2" -> new com.openggf.game.sonic2.objects.MonitorObjectInstance(spawn, "presentation 1-up");
                    default -> new com.openggf.game.sonic3k.objects.Sonic3kMonitorObjectInstance(spawn);
                }));
                for (int i = 0; i < 30; i++) session.step(null);
                com.openggf.graphics.ScreenshotCapture.savePNG(session.render(), output.resolve(game + "-" + mode + "-hud-monitor.png"));
                captureTalonSignComposition(game, mode, output);
                var state = GameServices.gameState();
                while (state.getLives() > 0) state.loseLife();
                state.addContinue(); state.addContinue(); state.addContinue();
                GameServices.level().requestGameOverExit(GameOverExit.CONTINUE_SCREEN);
                for (int i = 0; i < 120 && session.loop().getCurrentGameMode() != GameMode.CONTINUE_SCREEN; i++) session.step(null);
                assertEquals(GameMode.CONTINUE_SCREEN, session.loop().getCurrentGameMode());
                for (int i = 0; i < 140; i++) session.step(null);
                com.openggf.graphics.ScreenshotCapture.savePNG(drawContinue(session), output.resolve(game + "-" + mode + "-continue-wait.png"));
                var screen = session.loop().getContinueScreenProvider(); screen.update(true, false);
                for (int i = 0; i < 50; i++) screen.update(false, false);
                com.openggf.graphics.ScreenshotCapture.savePNG(drawContinue(session), output.resolve(game + "-" + mode + "-continue-run.png"));
                if (game.equals("s3k") && mode.equals("team")) captureTalonPortraits();
            }
            try (var session = new com.openggf.tools.GameplayCaptureSession(settings)) {
                session.applyMod(jar); session.boot(Path.of(System.getProperty(romProperty)), 0, 0, settings);
                for (int i = 0; i < 120; i++) { session.step(null); session.renderFrame(); }
                session.loop().enterSpecialStage();
                for (int i = 0; i < 240; i++) { session.step(null); session.renderFrame(); }
                assertEquals(GameMode.SPECIAL_STAGE, session.loop().getCurrentGameMode());
                var manager = privateField(session.loop().getActiveSpecialStageProvider(), "manager");
                var renderer = privateField(manager, game.equals("s1") ? "sonicSpriteRenderer" : "renderer");
                if (game.equals("s2")) {
                    var stage = (com.openggf.game.sonic2.specialstage.Sonic2SpecialStageManager) manager;
                    assertNotNull(stage.getTailsPlayer(), "Tails-role solo and follower actors must actually be spawned");
                    assertEquals(mode.endsWith("solo"), stage.getSonicPlayer() == null);
                } else if (game.equals("s3k")) {
                    var stage = (com.openggf.game.sonic3k.specialstage.Sonic3kSpecialStageManager) manager;
                    assertEquals(!mode.endsWith("solo"), stage.isTailsEnabled());
                }
                if (!mode.startsWith("native")) {
                    Object tails = game.equals("s1") ? ((PlayerSpriteRenderer) renderer).getRenderContext() : privateField(renderer, game.equals("s2") ? "tailsStageArt" : mode.equals("solo") ? "characterStageRenderer" : "tailsStageRenderer");
                    assertNotNull(tails, "native Tails actor receives its own character art");
                    if (game.equals("s2") && mode.equals("solo")) assertNull(privateField(renderer, "characterStageArt"));
                }
                com.openggf.graphics.ScreenshotCapture.savePNG(session.render(), output.resolve(game + "-" + mode + "-special.png"));
                Files.writeString(output.resolve(game + "-" + mode + "-special-state.csv"),
                        com.openggf.tools.GameplayCaptureSession.stateHeader() + "\n" + session.stateLine(240, null) + "\n");
                // Declared review setup: production debug completion, not a played emerald route.
                var lifecycle = privateField(session.loop(), "specialStageLifecycle");
                var finish = lifecycle.getClass().getDeclaredMethod("enterResultsScreenWithDebugRings", boolean.class, int.class);
                finish.setAccessible(true); finish.invoke(lifecycle, true, 50);
                for (int i = 0; i < 240; i++) { session.step(null); session.renderFrame(); }
                assertEquals(GameMode.SPECIAL_STAGE_RESULTS, session.loop().getCurrentGameMode());
                com.openggf.graphics.ScreenshotCapture.savePNG(session.render(), output.resolve(game + "-" + mode + "-special-results.png"));
            }
            if (game.equals("s3k") && mode.endsWith("solo")) {
                try (var session = new com.openggf.tools.GameplayCaptureSession(settings)) {
                    session.applyMod(jar); session.boot(Path.of(System.getProperty(romProperty)), 0, 0, settings);
                    for (int i = 0; i < 120; i++) session.step(null);
                    GameServices.level().requestBonusStageEntry(BonusStageType.SLOT_MACHINE);
                    for (int i = 0; i < 240; i++) { session.step(null); session.renderFrame(); }
                    assertEquals(GameMode.BONUS_STAGE, session.loop().getCurrentGameMode());
                    var player = GameServices.sprites().getMainPlayable();
                    assertInstanceOf(com.openggf.game.sonic3k.bonusstage.slots.S3kSlotBonusPlayer.TailsSlotBonusPlayer.class, player);
                    if (mode.equals("solo")) {
                        assertNotNull(player.getSpriteRenderer().getRenderContext());
                        assertNull(player.getTailsTailsController(), "TALON has no separate tail actor");
                    }
                    com.openggf.graphics.ScreenshotCapture.savePNG(session.render(), output.resolve(game + "-" + mode + "-slots.png"));
                }
            }
            try (var session = new com.openggf.tools.GameplayCaptureSession(settings)) {
                session.applyMod(jar); session.boot(Path.of(System.getProperty(romProperty)), 0, 0, settings);
                for (int i = 0; i < 120; i++) session.step(null);
                var route = com.openggf.game.session.ActiveGameplayTeamResolver.resolvePlayerCharacter(GameServices.configuration());
                assertNotNull(GameServices.level().getObjectManager().createDynamicObject(() -> switch (game) {
                    case "s1" -> new com.openggf.game.sonic1.objects.Sonic1ResultsScreenObjectInstance(2, 50, 1);
                    case "s2" -> new com.openggf.game.sonic2.objects.ResultsScreenObjectInstance(2, 50, 1, false);
                    default -> new com.openggf.game.sonic3k.objects.S3kResultsScreenObjectInstance(route, 0);
                }));
                for (int i = 0; i < 180; i++) { session.step(null); session.renderFrame(); }
                com.openggf.graphics.ScreenshotCapture.savePNG(session.render(), output.resolve(game + "-" + mode + "-act-results.png"));
            }
        }
        } finally {
            if (priorSaveRoot == null) System.clearProperty("openggf.saveRoot");
            else System.setProperty("openggf.saveRoot", priorSaveRoot);
        }
    }

    private void captureTalonSignComposition(String game, String mode, Path output) throws Exception {
        var graphics = GameServices.graphics();
        var render = GameServices.level().getObjectRenderManager();
        var nativeSheet = game.equals("s3k") ? render.getSheet(com.openggf.game.sonic3k.Sonic3kObjectArtKeys.END_SIGN) : render.getSignpostSheet();
        assertNotNull(nativeSheet);
        var active = com.openggf.game.presentation.StageCharacterPresentation.activeDefinition();
        var presentation = active == null ? null : active.presentationSupplier().load(active.key().persisted());
        var host = CharacterPresentation.Host.valueOf(game.toUpperCase(Locale.ROOT));
        var art = presentation == null ? null : presentation.art().get(host);
        int[] frames = game.equals("s1") ? new int[]{4, -1, 1, 2, 3} : game.equals("s2") ? new int[]{0, 1, 3, 4, 5} : new int[]{0, -1, 4, 5, 6};
        int line = active == null ? 0 : GameServices.sprites().getMainPlayable().getSpriteRenderer().getRenderContext().getEffectivePaletteLine(0);
        org.lwjgl.opengl.GL11.glClearColor(0, 0, 0, 1);
        org.lwjgl.opengl.GL11.glClear(org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT | org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT);
        graphics.resetForFixedFunction(); GameServices.camera().setX((short) 0); GameServices.camera().setY((short) 0);
        for (int i = 0; i < frames.length; i++) {
            if (frames[i] < 0) continue;
            var pose = com.openggf.game.presentation.CharacterSignRenderer.select(art, frames[i], frames[0], frames[1], frames[2], frames[3], frames[4]);
            var sheet = pose == null ? nativeSheet : com.openggf.game.presentation.CharacterSignRenderer.compose(nativeSheet, frames[i], pose, line, GameServices.level().getCurrentLevel().getPalette(0));
            var renderer = new com.openggf.level.render.PatternSpriteRenderer(sheet, graphics);
            renderer.ensurePatternsCached(graphics, com.openggf.graphics.PatternAtlasRange.CONTINUE_SCREEN.base() + i * 0x200);
            renderer.drawFrameIndex(pose == null ? frames[i] : 0, 32 + i * 60, 80, false, false);
        }
        graphics.flushScreenSpace(); org.lwjgl.opengl.GL11.glFinish();
        com.openggf.graphics.ScreenshotCapture.savePNG(com.openggf.graphics.ScreenshotCapture.captureFramebuffer(320, 224), output.resolve(game + "-" + mode + "-sign-composition.png"));
        if (presentation != null) {
            var rom = GameServices.rom().getRom();
            if (game.equals("s1")) assertTrue(com.openggf.game.sonic1.Sonic1CharacterPresentation.name(rom, presentation, CharacterPresentation.Style.RESULTS).complete());
            else if (game.equals("s2")) assertNotNull(com.openggf.game.sonic2.Sonic2CharacterPresentation.name(rom, presentation, CharacterPresentation.Style.RESULTS));
            else {
                var font = new LinkedHashMap<>(com.openggf.game.sonic3k.Sonic3kCharacterFont.results(rom));
                com.openggf.game.sonic3k.Sonic3kCharacterFont.title(rom).forEach(font::putIfAbsent);
                assertTrue(com.openggf.game.presentation.CharacterNameComposer.compose(presentation.displayName(), font, Map.of(), 0).complete());
            }
        }
    }

    private void captureTalonPortraits() throws Exception {
        Path saves=temp.resolve("portrait-saves");
        var manager=new SaveManager(saves);
        String talon = "mica-and-talon:talon";
        var teams = List.of(new com.openggf.game.save.SelectedTeam(talon, List.of()),
                new com.openggf.game.save.SelectedTeam(CODE, List.of(talon)));
        for(int slot=1;slot<=3;slot++) {
            Map<String,Object> payload=new LinkedHashMap<>();
            payload.put("act",0);payload.put("zone",0);payload.put("lives",3);
            payload.put("mainCharacter",slot==3?"tails":teams.get(slot-1).mainCharacter());
            payload.put("sidekicks",slot==3?List.of():teams.get(slot-1).sidekicks());
            payload.put("chaosEmeralds",List.of());payload.put("clear",false);
            payload.put("progressCode",1);payload.put("clearState",0);
            manager.writeSlot("s3k",slot,payload);
        }
        var presentation=new com.openggf.game.sonic3k.dataselect.S3kDataSelectPresentation(saves,GameServices.configuration());
        GameServices.graphics().clearPaletteFadePresentation();
        com.openggf.graphics.PaletteUploadPresentation.release(GameServices.graphics());
        presentation.initialize();
        assertEquals(DataSelectProvider.State.FADE_IN,presentation.getState());
        var continueIcon = presentation.getClass().getDeclaredMethod("characterContinueIcon", String.class);
        continueIcon.setAccessible(true);
        assertNotNull(continueIcon.invoke(presentation, "mica-and-talon:talon"), "TALON miniature fits the native 16 by 24 menu cell");
        var method=presentation.getClass().getDeclaredMethod("resolveCustomPortraitFrame",com.openggf.game.save.SelectedTeam.class);
        method.setAccessible(true);
        for(var team:teams) {
            var frame=(com.openggf.level.render.SpriteMappingFrame)method.invoke(presentation,team);
            assertTrue(frame.pieces().stream().anyMatch(piece->piece.tileIndex()>=0x2000));
            if(!team.sidekicks().isEmpty()) assertTrue(frame.pieces().stream().anyMatch(piece->piece.tileIndex()>=0x2000),"Both mod portraits are authored");
        }
        var stateMethod=presentation.getClass().getDeclaredMethod("buildObjectState");
        stateMethod.setAccessible(true);
        var objectState=(com.openggf.game.sonic3k.dataselect.S3kSaveScreenObjectState)stateMethod.invoke(presentation);
        for(int slot=0;slot<2;slot++) {
            var icon=objectState.visualState().slotStates().get(slot).customLifeIcon();
            assertNotNull(objectState.visualState().slotStates().get(slot).customContinueIcon(),
                    "Each leader keeps its own continue miniature");
            assertNotNull(icon,"Each leader keeps its own HUD life face");
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
        var definition = GameServices.module().getPlayableCharacterRegistry().find(CharacterKey.parsePersisted("mica-and-talon:talon")).orElseThrow();
        var iconArt = definition.presentationSupplier().load("mica-and-talon:talon").art().get(CharacterPresentation.Host.S3K).hudLifeIcon();
        var iconTiles = com.openggf.game.presentation.CharacterNameComposer.tiles(iconArt);
        var iconPalette = definition.paletteSupplier().load("mica-and-talon:talon");
        int matches = 0;
        for (int y = 0; y <= 208; y++) for (int x = 0; x <= 304; x++)
            if (matchesIcon(saveImage, iconTiles, 0, iconPalette, x, y)) matches++;
        assertTrue(matches >= 1, "TALON solo slot uses his private life-icon colours");
        writeCapture(saveImage,"talon-s3k-data-select-solo-and-mica-team");
        var controller=presentation.getSessionController();
        controller.moveSelection(-controller.menuModel().getSelectedRow());
        controller.loadAvailableTeams("" );
        var stock=(com.openggf.game.sonic3k.dataselect.S3kSaveScreenObjectState)stateMethod.invoke(presentation);
        assertNull(stock.visualState().noSaveCustomFrame(),"Stock NO SAVE portrait retains native mappings");
        controller.loadAvailableTeams(talon+";"+CODE+","+talon);
        for(var wanted:teams) {
            int target=controller.availableTeams().indexOf(wanted);
            assertTrue(target>=0,"Solo TALON and MICA with TALON must be selectable in NO SAVE");
            controller.cycleTeam(target-controller.menuModel().getSelectedTeamIndex());
            // No animation tick: changing the team must invalidate the cached visual immediately.
            var selected=(com.openggf.game.sonic3k.dataselect.S3kSaveScreenObjectState)stateMethod.invoke(presentation);
            assertNotNull(selected.visualState().noSaveCustomFrame());
            assertTrue(selected.visualState().noSaveCustomFrame().pieces().stream().anyMatch(piece->piece.tileIndex()>=0x2000));
            if(!wanted.sidekicks().isEmpty()) assertTrue(selected.visualState().noSaveCustomFrame().pieces().stream().anyMatch(piece->piece.tileIndex()>=0x2000));
            presentation.setClearColor();
            org.lwjgl.opengl.GL11.glClear(org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT | org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT);
            presentation.draw();GameServices.graphics().flushScreenSpace();
            org.lwjgl.opengl.GL11.glFinish();
            writeCapture(com.openggf.graphics.ScreenshotCapture.captureFramebuffer(320,224),
                    wanted.sidekicks().isEmpty()?"talon-s3k-no-save-solo":"mica-talon-s3k-no-save-team");
        }
        presentation.reset();
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
        assertEquals(0,com.openggf.tools.modsdk.GgfModCli.run(new String[]{"convert","art","--playable",
                "--image",project.resolve("src/main/mod/talon.png").toString(),
                "--sheet",project.resolve("src/main/mod/talon-sheet.yaml").toString(),
                "--out",classes.resolve("art/talon.ggfp").toString()},System.out));
        for (String host : List.of("s2", "s3k")) {
            assertEquals(0,com.openggf.tools.modsdk.GgfModCli.run(new String[]{"convert","art","--playable",
                    "--image",project.resolve("src/main/mod/mica_powered_"+host+".png").toString(),
                    "--sheet",project.resolve("src/main/mod/mica_powered_"+host+"-sheet.yaml").toString(),
                    "--out",classes.resolve("art/mica-powered-"+host+".ggfp").toString()},System.out));
        }
        for (String surface : List.of("continue", "portrait", "snowboard")) {
            Path source = project.resolve("art/mica/" + surface);
            Path destination = classes.resolve("art/mica/" + surface);
            Files.createDirectories(destination);
            try (var files = Files.list(source)) {
                for (var file : files.toList()) Files.copy(file, destination.resolve(file.getFileName()));
            }
        }
        for(String character:List.of("mica","talon")) {
            Path source=project.resolve("art/"+character+"/competition");
            Path destination=classes.resolve("art/"+character+"/competition");
            Files.createDirectories(destination);
            try(var files=Files.list(source)) {
                for(var file:files.filter(f->f.toString().endsWith(".png")).toList()) Files.copy(file,destination.resolve(file.getFileName()));
            }
        }
        Path presentation = project.resolve("art/mica/presentation");
        Files.createDirectories(classes.resolve("art/mica/presentation"));
        try(var files=Files.list(presentation)) {
            for(var file:files.toList()) Files.copy(file,classes.resolve("art/mica/presentation").resolve(file.getFileName()));
        }
        for (String character : List.of("mica", "talon")) {
            Path menu = project.resolve("art/" + character + "/competition-menu");
            Files.createDirectories(classes.resolve("art/" + character + "/competition-menu"));
            try (var files = Files.list(menu)) {
                for (var file : files.filter(Files::isRegularFile).toList()) Files.copy(file,
                        classes.resolve("art/" + character + "/competition-menu").resolve(file.getFileName()));
            }
            Path reels = project.resolve("art/" + character + "/reels");
            Files.createDirectories(classes.resolve("art/" + character + "/reels"));
            try (var files = Files.list(reels)) {
                for (var file : files.toList()) Files.copy(file,
                        classes.resolve("art/" + character + "/reels").resolve(file.getFileName()));
            }
        }
        Path stages = project.resolve("art/mica/wave3");
        Files.createDirectories(classes.resolve("art/mica/wave3"));
        try(var files=Files.list(stages)) {
            for(var file:files.filter(p -> p.toString().endsWith(".png") && !p.toString().endsWith("-fixed.png")).toList())
                Files.copy(file,classes.resolve("art/mica/wave3").resolve(file.getFileName()));
        }
        try(var files=Files.walk(project.resolve("art/talon/endings"))) {
            for(var file:files.filter(Files::isRegularFile).filter(p->p.toString().endsWith(".png")).toList()) {
                Path destination=classes.resolve("art/talon/endings").resolve(file.getFileName());
                Files.createDirectories(destination.getParent()); Files.copy(file,destination);
            }
        }
        Path endings = project.resolve("art/mica/endings");
        try(var files=Files.walk(endings)) {
            for(var file:files.filter(p -> p.toString().endsWith(".png")).toList()) {
                Path destination=classes.resolve("art/mica/endings").resolve(endings.relativize(file));
                Files.createDirectories(destination.getParent()); Files.copy(file,destination);
            }
        }
        for (String surface : List.of("presentation", "continue", "portrait", "wave3")) {
            Path source = project.resolve("art/talon/" + surface);
            try (var files = Files.list(source)) {
                for (var file : files.filter(p -> p.toString().endsWith(".png")).toList()) {
                    Path destination = classes.resolve("art/talon/" + surface).resolve(file.getFileName());
                    Files.createDirectories(destination.getParent()); Files.copy(file, destination);
                }
            }
        }
        Path jar = temp.resolve("mica-and-talon.jar");
        assertEquals(0,com.openggf.tools.modsdk.GgfModCli.run(new String[]{"package","--input",
                classes.toString(),"--out",jar.toString(),"--warnings","error"},System.out));
        try (JarFile packed = new JarFile(jar.toFile())) {
            assertFalse(packed.stream().anyMatch(entry -> entry.getName().matches(
                    "art/mica/endings/s2/photo-[1-4]\\.png")), "Phase 1 packages no MICA photo overrides");
            var sheet = com.openggf.level.objects.PlayableSheetReader.read(
                    packed.getInputStream(packed.getJarEntry("art/mica.ggfp")).readAllBytes());
            assertEquals(274, sheet.frames().size());
            assertEquals(52, sheet.animations().size());
            assertEquals(List.of(27, 28, 29, 30), sheet.animations().get("HOMING_DIVE").steps()
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
