package com.openggf.tools.nativelinux;

import com.openggf.GameLoop;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.*;
import com.openggf.game.patch.*;
import com.openggf.game.session.*;
import com.openggf.graphics.*;
import com.openggf.io.*;
import com.openggf.mods.*;
import com.openggf.mods.code.OwnedSceneFactory;
import com.openggf.mods.scene.host.*;
import com.openggf.mods.testing.ModTestKit;
import com.openggf.tools.HeadlessGameBoot;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import static org.lwjgl.opengl.GL11.*;

/** Bounded native gameplay qualification using prebuilt immutable mod jars,
 * production loaders/fault boundaries, a real GLFW/OpenGL context and original
 * user ROM paths. No compiler, JDK fallback, user trust/save edits or ROM copies.
 * Inputs: slug, owned output directory, S1/S2/S3K paths and optional scene debug
 * commands. Origin: 2026-10-09 Linux friends ZIP. This is a smoke check, not a
 * certification of every route, multiplayer peer or graphics driver.
 */
public final class NativeGameplayCheck {
    private static final int WIDTH = 400, HEIGHT = 224;
    private static final float[] PROJECTION = {2f/WIDTH,0,0,0, 0,2f/HEIGHT,0,0,
            0,0,-1,0, -1,-1,0,1};
    private static final int[] VIEWPORT = {0,0,WIDTH,HEIGHT};
    private static short[] audioFrame;
    private static com.openggf.audio.LiveCaptureAudioHandle audioCapture;
    private static int audioPeak;
    private static com.openggf.data.Rom ownedRom;

    public static void main(String[] args) throws Exception {
        if (args.length < 5) throw new IllegalArgumentException("slug output s1-rom s2-rom s3k-rom [debug-command ...]");
        String slug = args[0];
        if (!slug.matches("[a-z0-9-]+")) throw new IllegalArgumentException("Unsafe slug");
        Path output = Path.of(args[1]).toAbsolutePath();
        Files.createDirectories(output);
        Path jar = Path.of("mods", slug + ".jar").toAbsolutePath();
        ModManifest manifest;
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            try (var stream = zip.getInputStream(zip.getEntry("META-INF/openggf-mod.yaml"))) {
                manifest = new ModManifestParser().parse(stream.readAllBytes());
            }
        }
        Path repository = Files.createDirectory(output.resolve("repository"));
        Files.copy(jar, repository.resolve(jar.getFileName()));
        System.setProperty("openggf.saveRoot", output.resolve("engine-saves").toString());
        System.setProperty("infinite-sonic.seed", "0x5eed");
        System.setProperty("sonic-survivors.seed", "0x5eed");
        try (HeadlessGameBoot boot = new HeadlessGameBoot(WIDTH, HEIGHT)) {
            var config = GameServices.configuration();
            config.setSessionOverride(SonicConfiguration.SONIC_1_ROM, Path.of(args[2]).toAbsolutePath().toString());
            config.setSessionOverride(SonicConfiguration.SONIC_2_ROM, Path.of(args[3]).toAbsolutePath().toString());
            config.setSessionOverride(SonicConfiguration.SONIC_3K_ROM, Path.of(args[4]).toAbsolutePath().toString());
            config.setSessionOverride(SonicConfiguration.ROMS_DIRECTORY, Path.of(args[2]).toAbsolutePath().getParent().toString());
            config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, "WIDE_16_9");
            config.setSessionOverride(SonicConfiguration.S3K_SKIP_INTROS, true);
            config.setSessionOverride(SonicConfiguration.AUDIO_ENABLED, true);
            config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
            config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
            config.setSessionOverride(SonicConfiguration.TEST_MODE_ENABLED, false);
            config.setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED, false);
            try (ModTestKit kit = ModTestKit.openTrusted(repository, output.resolve("storage"),
                    LogicalRomResolver.fromRomManager(GameServices.rom()), config, List.of())) {
                GameLoop loop;
                GameModule module;
                if (manifest.type().name().equals("STANDALONE")) {
                    module = kit.standalone(manifest.id());
                    String character = module.getPlayableCharacterRegistry().definitions().keySet().stream()
                            .filter(key -> !key.isBuiltin()).map(CharacterKey::persisted).sorted().findFirst().orElseThrow();
                    config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, character);
                    try (ModAssetRoot assets = ModAssetRoot.jar(repository, repository.resolve(jar.getFileName()), ModInputLimits.production())) {
                        GameDataSource source = new GameDataSource() {
                            public Optional<com.openggf.data.Rom> rom() { return Optional.empty(); }
                            public java.io.InputStream openAsset(String path) throws java.io.IOException {
                                return new java.io.ByteArrayInputStream(assets.readBounded(path, assets.limits().maxAssetBytes()));
                            }
                            public String identity() { return "native-check:" + manifest.id(); }
                        };
                        var mode = HeadlessGameBoot.openStandaloneSessionForBoot(EngineServices.current(), module, source);
                        GameplaySessionFactory.attachManagers(mode, EngineServices.current());
                        GameModuleRegistry.setCurrent(module);
                        installAudio(kit,repository,manifest,module.getGameCode());
                        loop = new GameLoop(EngineServices.current());
                        loop.setGameplayMode(mode);
                        loop.setInputHandler(kit.input().handler());
                        loop.setGameMode(GameMode.LEVEL);
                        var team = GameplayTeamBootstrap.registerActiveTeam(module, GameServices.sprites(), config);
                        GameServices.level().setRewindClassResolver(kit.rewindClassResolver());
                        GameServices.level().loadZoneAndAct(0, 0);
                        GameServices.camera().setFocusedSprite(team.mainSprite());
                        GameServices.camera().updatePosition(true);
                        exerciseLevel(kit, loop, output);
                    }
                } else {
                    String game = manifest.baseGame().equals("any") ? "s3k" : manifest.baseGame();
                    String character = slug.equals("character") ? "phase3-character:runner" : "sonic";
                    config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, character);
                    ownedRom=new com.openggf.data.Rom();
                    if (!ownedRom.open(args[switch (game) {case "s1" -> 2; case "s2" -> 3; default -> 4;}]))
                        throw new IllegalStateException("Cannot open original ROM");
                    EngineServices.current().roms().setRom(ownedRom);
                    GameModule root=EngineServices.current().romDetection().detectAndCreateModule(ownedRom).orElseThrow();
                    module=kit.launch(root,new GameplayLaunchRequest(game,character,List.of()));
                    var mode=HeadlessGameBoot.openResolvedSessionForBoot(EngineServices.current(),module);
                    GameplaySessionFactory.attachManagers(mode,EngineServices.current());
                    GameModuleRegistry.setCurrent(module);
                    installAudio(kit,repository,manifest,game);
                    loop=new GameLoop(EngineServices.current());
                    loop.setGameplayMode(mode);
                    loop.setGameMode(GameMode.LEVEL);
                    loop.setInputHandler(kit.input().handler());
                    var team=GameplayTeamBootstrap.registerActiveTeam(module,GameServices.sprites(),config);
                    GameServices.level().setRewindClassResolver(kit.rewindClassResolver());
                    int zone=ownedZone(module,manifest.id());
                    GameServices.level().loadZoneAndAct(zone,0);
                    GameServices.camera().setFocusedSprite(team.mainSprite());
                    GameServices.camera().updatePosition(true);
                    verifyArtOverrides(kit,module,manifest);
                    if (module.getGameService(OwnedSceneFactory.class) != null) {
                        startCapture();
                        SceneServices services=sceneServices(module,output);
                        kit.openScene(module, WIDTH, HEIGHT, services);
                        sceneFrame(kit, output.resolve("00-title.png"));
                        if (args.length == 5) press(kit, org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
                        for (int i = 5; i < args.length; i++) {
                            if (!kit.sceneHost().debugJump(args[i])) throw new AssertionError("Unsupported scene command: " + args[i]);
                            if (slug.equals("sitar-hero") && args[i].startsWith("perform:")) awaitPerformance(kit);
                            for (int tick = 0; tick < 120; tick++) sceneTick(kit);
                            sceneFrame(kit, output.resolve(String.format("%02d-phase.png", i-4)));
                            healthy(kit);
                        }
                        for (int tick = 0; tick < 360; tick++) {
                            if (tick % 45 == 0) press(kit, org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE);
                            sceneTick(kit);
                            if (tick % 60 == 0) sceneFrame(kit, null);
                        }
                        sceneFrame(kit, output.resolve("99-gameplay.png"));
                        if (!kit.sceneHost().isOpen()) throw new AssertionError("Scene closed unexpectedly");
                        kit.sceneHost().close();
                        healthy(kit);
                        // Reopen through the same production owner boundary, with fresh ROM leases.
                        kit.openScene(module, WIDTH, HEIGHT, sceneServices(module,output));
                        kit.tick();
                        sceneFrame(kit, output.resolve("reopen.png"));
                        if (slug.equals("sitar-hero") && audioPeak==0) throw new AssertionError("Sitar performance produced no PCM");
                        Files.writeString(output.resolve("audio.txt"),"offline PCM peak="+audioPeak+"\n");
                        audioCapture.close();
                    } else {
                        exerciseLevel(kit,loop,output);
                        for(int act=1;act<module.getZoneRegistry().getActCount(zone);act++) {
                            GameServices.level().loadZoneAndAct(zone,act);
                            Path actOutput=Files.createDirectories(output.resolve("act-"+(act+1)));
                            exerciseLevel(kit,loop,actOutput);
                        }
                    }
                }
                var registry = ModTestKit.moduleState(module);
                var snapshot = ModTestKit.capture(registry);
                ModTestKit.restore(registry, snapshot);
                healthy(kit);
                kit.sceneHost().close();
                healthy(kit);
                String vm=System.getProperty("org.graalvm.nativeimage.imagecode")==null ? "JVM" : "native";
                Files.writeString(output.resolve("result.txt"), "PASS "+vm+" rendered gameplay: " + manifest.id()
                        + "; no owner findings/disabled owners; module state roundtrip; scene reopen where applicable\n");
                System.out.println(Files.readString(output.resolve("result.txt")));
            }
        } finally {if (ownedRom!=null) ownedRom.close();}
    }

    private static int ownedZone(GameModule module,String owner) {
        var zones=module.getZoneRegistry();
        for(int index=0;index<zones.getZoneCount();index++)
            if(zones.zoneKey(index) instanceof ZoneKey.Mod key && key.ownerModId().equals(owner)) return index;
        return 0;
    }

    private static SceneServices sceneServices(GameModule module,Path output) throws Exception {
        return new SceneServices(GameServices.audio(),SceneRomArtFactory.forModule(module,GameServices.rom().getRom()),
            output.resolve("storage"),(x,y)->new int[]{(int)x,(int)y,1},()->{},()->{},
            new SceneRomLibrary(module,GameServices.rom().getRom(),GameServices.rom()));
    }

    private static void installAudio(ModTestKit kit,Path repository,ModManifest manifest,String game) throws Exception {
        var audio=GameServices.audio();
        audio.setBackend(new com.openggf.audio.HeadlessSmpsAudioBackend(GameServices.configuration(),EngineServices.current().profiler()));
        try(ZipFile zip=new ZipFile(kit.catalog().effective().orderedEnabled().getFirst().jarPath().toFile())) {
            var entry=zip.getEntry("audio/audio-manifest.yaml");
            if(entry==null) return;
            ModAudioManifest audioManifest;
            try(var stream=zip.getInputStream(entry)) {audioManifest=new ModAudioManifestParser(manifest.id()).parse(stream.readAllBytes());}
            var tracks=new ModTrackRegistry(audioManifest.tracks());
            var sfx=new ModSfxRegistry(audioManifest.sfx());
            var findings=new ModRuntimeFindingStore();
            var preparer=new ModAudioPreparer(repository,ModInputLimits.production(),findings,owners->new ModStateSaveResult.Saved());
            var prepared=preparer.prepare(kit.catalog().effective(),tracks,sfx,audio.outputSampleRate());
            if(!findings.snapshot().isEmpty() || !prepared.failedOwners().isEmpty()) throw new AssertionError("Audio preparation failed: "+findings.snapshot());
            var music=PreparedModMusic.build(kit.catalog().effective(),tracks,sfx,prepared,audio.outputSampleRate(),
                    manifest.type()==ModType.STANDALONE ? game : null);
            var port=new com.openggf.ModStreamedMusicPort(music,new StreamedMusicPlayer(audio.outputSampleRate()),game);
            audio.installStreamedMusicPort(port);
            for(int id:manifest.audioOverrides().keySet()) {
                if(!port.hasStockOverride(id)) throw new AssertionError("Missing prepared stock music override "+id);
            }
        }
    }
    private static void verifyArtOverrides(ModTestKit kit,GameModule module,ModManifest manifest) throws Exception {
        try(ZipFile zip=new ZipFile(kit.catalog().effective().orderedEnabled().getFirst().jarPath().toFile())) {
            for(var override:manifest.artOverrides().entrySet()) {
                com.openggf.level.objects.BakedSheetReader.BakedSheet expected;
                try(var stream=zip.getInputStream(zip.getEntry(override.getValue()))) {
                    expected=com.openggf.level.objects.BakedSheetReader.read(stream);
                }
                var actual=module.getObjectArtProvider().getSheet(override.getKey());
                if(actual==null || actual.getPatterns().length!=expected.patterns().length)
                    throw new AssertionError("Art override not installed: "+override.getKey());
                var want=expected.patterns();var found=actual.getPatterns();
                for(int i=0;i<want.length;i++) for(int y=0;y<8;y++) for(int x=0;x<8;x++)
                    if(want[i].getPixel(x,y)!=found[i].getPixel(x,y)) throw new AssertionError("Art override bytes differ");
            }
        }
    }

    private static void healthy(ModTestKit kit) {
        if (!kit.findings().isEmpty() || !kit.disabledOwners().isEmpty())
            throw new AssertionError("Mod callback failed: " + kit.findings() + "; disabled " + kit.disabledOwners());
    }
    private static void press(ModTestKit kit, int key) {
        kit.input().key(key,true); sceneTick(kit); kit.input().key(key,false); sceneTick(kit);
    }
    private static void startCapture() {
        audioCapture=GameServices.audio().beginLiveCaptureAudio(60);
        audioFrame=new short[audioCapture.maxStereoFramesPerPacket()*2];
    }
    private static void sceneTick(ModTestKit kit) {
        kit.tick();
        if (audioFrame!=null) {
            HeadlessGameBoot.presentHeadlessOuterAudioFrame();
            int count=audioCapture.drainPresentationFrame(audioFrame);
            for(int i=0;i<count*2;i++) audioPeak=Math.max(audioPeak,Math.abs((int)audioFrame[i]));
        }
        healthy(kit);
    }
    private static void awaitPerformance(ModTestKit kit) throws Exception {
        Object scene=NativeSceneInspection.scene(kit.sceneHost());
        var screen=scene.getClass().getMethod("screen");
        long deadline=System.nanoTime()+120_000_000_000L;
        while (screen.invoke(scene).equals("LOADING") && System.nanoTime()<deadline) {
            sceneTick(kit); Thread.sleep(10);
        }
        if (!screen.invoke(scene).equals("PLAY"))
            throw new AssertionError("Sitar did not reach PLAY: "+screen.invoke(scene));
    }
    private static void sceneFrame(ModTestKit kit, Path file) throws Exception {
        glClearColor(0,0,0,1); glClear(GL_COLOR_BUFFER_BIT);
        kit.sceneHost().draw(PROJECTION, VIEWPORT); glFinish();
        if (kit.sceneHost().recordedFrame().isEmpty()) throw new AssertionError("Scene produced no draw operations");
        if (file != null) image(ScreenshotCapture.captureFramebuffer(WIDTH,HEIGHT),file);
        healthy(kit);
    }
    private static void exerciseLevel(ModTestKit kit, GameLoop loop, Path output) throws Exception {
        var level = GameServices.level();
        startCapture();
        StringBuilder states=new StringBuilder("frame,x,y,dead,mode,objects\n");
        level.skipPendingInitialTitleCardPresentation();
        if (loop.getCurrentGameMode() == GameMode.TITLE_CARD) loop.setGameMode(GameMode.LEVEL);
        for (int frame=0; frame<600; frame++) {
            kit.input().key(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT, frame>60 && frame<300);
            kit.input().key(org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE, frame%90<8);
            kit.input().key(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, frame==10 || frame==30);
            kit.input().beginTick();
            try { loop.step(); } finally {kit.input().endTick();}
            HeadlessGameBoot.presentHeadlessOuterAudioFrame();
            int samples=audioCapture.drainPresentationFrame(audioFrame);
            for(int i=0;i<samples*2;i++) audioPeak=Math.max(audioPeak,Math.abs((int)audioFrame[i]));
            healthy(kit);
            if (frame%60==0 || frame==599) {
                var player=GameServices.camera().getFocusedSprite();
                if(player==null) throw new AssertionError("No playable sprite");
                states.append(frame).append(',').append(player.getCentreX()).append(',').append(player.getCentreY())
                    .append(',').append(player.getDead()).append(',').append(loop.getCurrentGameMode())
                    .append(',').append(level.getObjectManager().getActiveObjects().size()).append('\n');
                GameServices.graphics().runPendingRenderThreadTasks();
                level.setClearColor(); glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
                var controller = com.openggf.game.mode.ControlledFrameRuntime.controller(SessionManager.getCurrentGameplayMode());
                if (controller==null || !controller.drawScene()) level.drawWithSpritePriority(GameServices.sprites(),true);
                GameServices.graphics().flush();
                if (controller!=null) {GameServices.graphics().resetForFixedFunction();controller.drawOverlay();GameServices.graphics().flushScreenSpace();}
                glFinish();
                if (frame==0 || frame==599) image(ScreenshotCapture.captureFramebuffer(WIDTH,HEIGHT), output.resolve("level-"+frame+".png"));
            }
        }
        Files.writeString(output.resolve("state.csv"),states);
        Files.writeString(output.resolve("audio.txt"),"offline PCM peak="+audioPeak+"\n");
        audioCapture.close();
        if (GameServices.camera().getFocusedSprite()==null) throw new AssertionError("Missing playable sprite");
    }
    private static void image(RgbaImage image, Path path) throws Exception {
        ScreenshotCapture.savePNG(image,path);
        if (Arrays.stream(image.pixels()).distinct().limit(2).count()<2) throw new AssertionError("Blank/flat framebuffer: " + path);
    }
}
