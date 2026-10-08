package com.openggf.mods.testing;

import com.openggf.game.patch.GameplayLaunchRequest;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.mods.code.ModFaultBoundary;
import com.openggf.tools.modsdk.GgfModCli;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class TestModTestKit {
    @TempDir Path work;

    @Test void realPackedSceneUsesOwnerLoaderInputStorageAndImmutableDrawRecording() throws Exception {
        Path repository = Files.createDirectories(work.resolve("mods"));
        pack(repository, "owner", "", """
                context.registerStartupScene(() -> new com.openggf.mods.scene.ModScene() {
                    private int ticks;
                    public void enter(com.openggf.mods.scene.SceneContext ctx) { ctx.storage().write("entered.txt", "yes"); }
                    public void update(com.openggf.mods.scene.SceneContext ctx) {
                        if (ctx.keyDown(com.openggf.mods.scene.SceneKeys.SPACE)) ticks++;
                    }
                    public void draw(com.openggf.mods.scene.SceneContext ctx, com.openggf.mods.scene.SceneCanvas c) {
                        c.clip(0,0,10,10); c.fill(2,3,4,5,0xFF000000 | ticks);
                    }
                });
                """);
        Path storage = work.resolve("storage");
        try (ModTestKit kit = ModTestKit.openTrusted(repository, storage)) {
            assertEquals("owner", kit.plan("owner").ownerModId());
            assertNotSame(getClass().getClassLoader(), kit.loader("owner"));
            var module = kit.launch(new Sonic3kGameModule(), new GameplayLaunchRequest("s3k","sonic",List.of()));
            kit.openScene(module, 320,224);
            kit.input().key(org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE,true);
            kit.tick(); kit.tick();
            var frame = kit.draw();
            assertEquals(1,frame.size()); assertEquals(0xFF000002,frame.getFirst().tint());
            int[] clip = frame.getFirst().clip(); clip[0]=999;
            assertArrayEquals(new int[]{0,0,10,10},frame.getFirst().clip());
            assertThrows(UnsupportedOperationException.class, () -> frame.clear());
            assertEquals("yes", Files.readString(storage.resolve("mods/owner/entered.txt")));
            assertTrue(kit.findings().isEmpty());
            assertEquals(2_000_000_000L/60,kit.input().nanos());
        }
        Files.delete(repository.resolve("owner.jar"));
    }

    @Test void callbackFaultDisablesActualOwnerAndRequiredDependentAndDropsTheirPlans() throws Exception {
        Path repository = Files.createDirectories(work.resolve("mods"));
        pack(repository,"owner","", """
                context.registerStartupScene(() -> new com.openggf.mods.scene.ModScene() {
                    public void enter(com.openggf.mods.scene.SceneContext ctx) { }
                    public void update(com.openggf.mods.scene.SceneContext ctx) { throw new IllegalStateException("fixture fault"); }
                    public void draw(com.openggf.mods.scene.SceneContext ctx, com.openggf.mods.scene.SceneCanvas c) { }
                });
                """);
        pack(repository,"dependent","  - id: owner\n    versionRange: \"*\"\n", "");
        try (ModTestKit kit = ModTestKit.openTrusted(repository,work.resolve("storage"))) {
            var module = kit.launch(new Sonic3kGameModule(),new GameplayLaunchRequest("s3k","sonic",List.of()));
            kit.openScene(module,320,224);
            ModFaultBoundary.CallbackAborted failure=assertThrows(ModFaultBoundary.CallbackAborted.class,kit::tick);
            assertEquals("owner",failure.owner());
            assertEquals(java.util.Set.of("owner","dependent"),kit.disabledOwners());
            assertTrue(kit.findings().get("owner").stream().anyMatch(f->f.code().equals("MOD_CALLBACK_FAILED")));
            assertThrows(IllegalArgumentException.class,()->kit.plan("owner"));
            assertThrows(IllegalArgumentException.class,()->kit.plan("dependent"));
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void suppliedSceneServicesKeepKitStorageIsolationAndOtherHostCapabilities(boolean suppliesOtherRoot) throws Exception {
        Path repository = Files.createDirectories(work.resolve("service-mods"));
        pack(repository, "owner", "", """
                context.registerStartupScene(() -> new com.openggf.mods.scene.ModScene() {
                    public void enter(com.openggf.mods.scene.SceneContext ctx) {
                        ctx.storage().write("entered.txt", "yes");
                        ctx.audio().playSfx(17);
                        ctx.storage().write("art.txt", ctx.art().rom() != null ? "present" : "missing");
                    }
                    public void update(com.openggf.mods.scene.SceneContext ctx) { ctx.exitToGameTitle(); }
                    public void draw(com.openggf.mods.scene.SceneContext ctx, com.openggf.mods.scene.SceneCanvas canvas) { }
                });
                """);
        Path storage = work.resolve("isolated-storage");
        Path otherRoot = work.resolve("other-storage");
        var audio = org.mockito.Mockito.mock(com.openggf.audio.AudioManager.class);
        var art = org.mockito.Mockito.mock(com.openggf.mods.scene.SceneRomArt.class);
        var exits = new ArrayList<String>();
        var services = new com.openggf.mods.scene.host.SceneServices(audio, art,
                suppliesOtherRoot ? otherRoot : null, (x, y) -> new int[]{12, 23, 1},
                () -> exits.add("game"), () -> exits.add("master"));
        try (ModTestKit kit = ModTestKit.openTrusted(repository, storage)) {
            var module = kit.launch(new Sonic3kGameModule(), new GameplayLaunchRequest("s3k", "sonic", List.of()));
            kit.openScene(module, 320, 224, services);
            assertEquals("yes", Files.readString(storage.resolve("mods/owner/entered.txt")));
            assertEquals("present", Files.readString(storage.resolve("mods/owner/art.txt")));
            assertFalse(Files.exists(otherRoot), "supplied scene services cannot choose a second storage scope");
            org.mockito.Mockito.verify(audio).playSfx(17);
            kit.tick();
            assertEquals(List.of("game"), exits, "supplied exit callback is retained");
        }
    }

    @Test void repeatedHeadlessCloseReleasesTheSceneAndItsLibraryExactlyOnce() throws Exception {
        Path repository = Files.createDirectories(work.resolve("lifecycle-mods"));
        pack(repository, "owner", "", """
                context.registerStartupScene(() -> new com.openggf.mods.scene.ModScene() {
                    public void enter(com.openggf.mods.scene.SceneContext ctx) { }
                    public void update(com.openggf.mods.scene.SceneContext ctx) { }
                    public void draw(com.openggf.mods.scene.SceneContext ctx, com.openggf.mods.scene.SceneCanvas canvas) {
                        canvas.fill(1,2,3,4,0xFFFFFFFF);
                    }
                    public void exit(com.openggf.mods.scene.SceneContext ctx) {
                        int exits = Integer.parseInt(ctx.storage().read("exits.txt").orElse("0"));
                        ctx.storage().write("exits.txt", Integer.toString(exits + 1));
                    }
                });
                """);
        Path storage = work.resolve("lifecycle-storage");
        var library = org.mockito.Mockito.mock(com.openggf.mods.scene.host.SceneRomLibrary.class);
        var kit = ModTestKit.openTrusted(repository, storage);
        try {
            var module = kit.launch(new Sonic3kGameModule(), new GameplayLaunchRequest("s3k", "sonic", List.of()));
            kit.openScene(module, 320, 224, new com.openggf.mods.scene.host.SceneServices(
                    null, null, null, null, null, null, library));
            assertFalse(kit.draw().isEmpty());
            kit.close();
            kit.close();
            assertFalse(kit.sceneHost().isOpen());
            assertTrue(kit.sceneHost().recordedFrame().isEmpty());
            assertEquals("1", Files.readString(storage.resolve("mods/owner/exits.txt")));
            org.mockito.Mockito.verify(library).close();
            assertTrue(kit.rewindClassResolver().resolve("owner", "fixture.Entry").isEmpty(),
                    "the kit closes its owner runtime as well as the scene");
        } finally {
            kit.close();
        }
    }

    @Test void rewindResolverUsesTheActualPrivateOwnerLoadersForIdenticalClassNames() throws Exception {
        Path repository = Files.createDirectories(work.resolve("loader-mods"));
        pack(repository, "first", "", "");
        pack(repository, "second", "", "");
        try (ModTestKit kit = ModTestKit.openTrusted(repository, work.resolve("storage"))) {
            var resolver = kit.rewindClassResolver();
            Class<?> first = kit.loadOwned("first", "fixture.Entry");
            Class<?> second = kit.loadOwned("second", "fixture.Entry");
            assertNotSame(first, second);
            assertSame(first, resolver.resolve("first", "fixture.Entry").orElseThrow());
            assertSame(second, resolver.resolve("second", "fixture.Entry").orElseThrow());
            assertEquals("first", resolver.ownerOf(first).orElseThrow());
            assertEquals("second", resolver.ownerOf(second).orElseThrow());
            assertSame(String.class, resolver.resolve(null, "java.lang.String").orElseThrow());
            assertTrue(resolver.resolve("first", "fixture.Missing").isEmpty());
            assertTrue(resolver.ownerOf(String.class).isEmpty());
        }
    }

    @Test void drawFaultUsesTheProductionOwnerBoundaryWithASilentRenderer() throws Exception {
        Path repository = Files.createDirectories(work.resolve("draw-mods"));
        pack(repository, "owner", "", """
                context.registerStartupScene(() -> new com.openggf.mods.scene.ModScene() {
                    public void enter(com.openggf.mods.scene.SceneContext ctx) { }
                    public void update(com.openggf.mods.scene.SceneContext ctx) { }
                    public void draw(com.openggf.mods.scene.SceneContext ctx, com.openggf.mods.scene.SceneCanvas c) {
                        throw new IllegalStateException("draw fixture fault");
                    }
                });
                """);
        pack(repository, "dependent", "  - id: owner\n    versionRange: \"*\"\n", "");
        try (ModTestKit kit = ModTestKit.openTrusted(repository, work.resolve("storage"))) {
            var module = kit.launch(new Sonic3kGameModule(),
                    new GameplayLaunchRequest("s3k", "sonic", List.of()));
            kit.openScene(module, 320, 224);
            ModFaultBoundary.CallbackAborted failure = assertThrows(ModFaultBoundary.CallbackAborted.class, kit::draw);
            assertEquals("owner", failure.owner());
            assertEquals(java.util.Set.of("owner", "dependent"), kit.disabledOwners());
            assertTrue(kit.findings().get("owner").stream().anyMatch(f -> f.code().equals("MOD_CALLBACK_FAILED")));
        }
    }

    @Test void malformedPackedArtifactFailsThroughProductionScanner() throws Exception {
        Path repository=Files.createDirectories(work.resolve("invalid"));
        Files.writeString(repository.resolve("broken.jar"),"not a jar");
        assertThrows(java.io.IOException.class,()->ModTestKit.openTrusted(repository,work.resolve("storage")));
    }

    @Test void suppliedConfigurationDrivesTheKitsLiveLogicalInputSource() throws Exception {
        Path repository = Files.createDirectories(work.resolve("configured-mods"));
        pack(repository, "owner", "", "");
        var configuration = com.openggf.configuration.SonicConfigurationService.createStandalone();
        var right = com.openggf.configuration.SonicConfiguration.RIGHT;
        configuration.setConfigValue(right, org.lwjgl.glfw.GLFW.GLFW_KEY_D);
        try (ModTestKit kit = ModTestKit.openTrusted(repository, work.resolve("storage"),
                new com.openggf.game.patch.LogicalRomResolver(() -> null), configuration, List.of())) {
            kit.input().key(org.lwjgl.glfw.GLFW.GLFW_KEY_D, true);
            kit.input().beginTick();
            assertTrue((kit.input().handler().logical().player1().heldMask()
                    & com.openggf.sprites.playable.AbstractPlayableSprite.INPUT_RIGHT) != 0);
            kit.input().endTick();
            configuration.setConfigValue(right, org.lwjgl.glfw.GLFW.GLFW_KEY_L);
            kit.input().beginTick();
            assertEquals(0, kit.input().handler().logical().player1().heldMask()
                    & com.openggf.sprites.playable.AbstractPlayableSprite.INPUT_RIGHT);
            kit.input().endTick();
        }
    }

    @Test void missingSavedOwnerRecoveryRetainsTheNormalRuntimeWarning() throws Exception {
        Path repository = Files.createDirectories(work.resolve("zone-mods"));
        Path levels = Path.of("src/test/resources/mods/sample-two-act-campaign-src/project/src/main/resources/levels/tide/act1");
        var assets = new java.util.LinkedHashMap<String, byte[]>();
        try (var paths = Files.walk(levels)) {
            for (Path asset : paths.filter(Files::isRegularFile).toList()) {
                assets.put("levels/tide/act1/" + levels.relativize(asset).toString().replace('\\', '/'),
                        Files.readAllBytes(asset));
            }
        }
        pack(repository, "owner", "", """
                context.registerZone(com.openggf.mods.code.ModZoneContribution.singleAct("present",
                    new com.openggf.mods.code.BakedLevelRef("levels/tide/act1/level.json"),
                    null, null, false));
                """, "s2", assets);
        try (ModTestKit kit = ModTestKit.openTrusted(repository, work.resolve("storage"))) {
            var module = kit.launch(new com.openggf.game.sonic2.Sonic2GameModule(),
                    new GameplayLaunchRequest("s2", "sonic", List.of()));
            var payload = new java.util.LinkedHashMap<String, Object>();
            com.openggf.game.sonic2.dataselect.S2SavedZone.write(payload,
                    com.openggf.game.ZoneKey.mod("removed", "course"));
            assertEquals(new com.openggf.game.dataselect.DataSelectDestination(0, 0),
                    module.getDataSelectHostProfile().resolveLoadDestination(payload));
            var warning = kit.findings().get("removed").getFirst();
            assertEquals("S2_MOD_ZONE_MISSING", warning.code());
            assertEquals(com.openggf.mods.ModFindingSeverity.WARNING, warning.severity());
            assertTrue(kit.disabledOwners().isEmpty());
        }
    }

    @Test void catalogValidationRejectsMissingAudioBeforeGrantingTestTrust() throws Exception {
        Path repository = Files.createDirectories(work.resolve("invalid-audio"));
        try (var jar = new java.util.jar.JarOutputStream(Files.newOutputStream(repository.resolve("audio.jar")))) {
            jar.putNextEntry(new java.util.jar.JarEntry("META-INF/openggf-mod.yaml"));
            jar.write("""
                    formatVersion: 1
                    id: broken-audio
                    name: Broken original audio fixture
                    version: 1.0.0
                    authors: [OpenGGF]
                    description: Missing manifest fixture
                    engineApiRange: ">=0.7.0 <0.8.0"
                    type: patch
                    baseGame: s2
                    dependencies: []
                    audioOverrides: {1: "missing"}
                    artOverrides: {}
                    """.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            jar.closeEntry();
        }
        var failure = assertThrows(java.io.IOException.class,
                () -> ModTestKit.openTrusted(repository, work.resolve("storage")));
        assertTrue(failure.getMessage().contains("AUDIO_MANIFEST_MISSING"), failure::getMessage);
        assertFalse(Files.exists(work.resolve("storage")));
    }

    @Test void packagingNeverReplacesArtifactsOrAcceptsAnUnrelatedRepositoryEntry() throws Exception {
        Path repository = Files.createDirectories(work.resolve("existing"));
        Path artifact = repository.resolve("creator-test.jar");
        Files.writeString(artifact, "preserve existing fixture");
        var failure = assertThrows(java.io.IOException.class, () -> ModTestKit.packageAndOpen(
                work.resolve("classes"), repository, work.resolve("storage")));
        assertTrue(failure.getMessage().contains("empty dedicated"));
        assertEquals("preserve existing fixture", Files.readString(artifact));
    }

    @Test void moduleRoundtripUsesActualOwnedAdapterAndStableNamespace() throws Exception {
        Path repository=Files.createDirectories(work.resolve("mods"));
        pack(repository,"owner","", """
                context.registerGamePatch(new com.openggf.game.patch.GamePatch() {
                    public String id() { return "state"; }
                    public String displayName() { return "State fixture"; }
                    public String baseGameId() { return "s3k"; }
                    public boolean activatesFor(com.openggf.game.patch.GameplayLaunchRequest request) { return true; }
                    public java.util.Set<com.openggf.game.patch.LogicalRom> romPrerequisites() { return java.util.Set.of(); }
                    public java.util.List<String> providedMainCharacters() { return java.util.List.of(); }
                    public com.openggf.game.GameModule apply(com.openggf.game.GameModule base,
                            com.openggf.game.patch.PatchContext ctx) {
                        int[] value=new int[]{0};
                        return new com.openggf.game.patch.DelegatingGameModule(base,"owner:state") {
                            public java.util.List<com.openggf.game.rewind.RewindSnapshottable<?>> rewindAdapters() {
                                return java.util.List.of(new com.openggf.game.rewind.RewindSnapshottable<Integer>() {
                                    public String key() { return "camera"; }
                                    public Integer capture() { return value[0]; }
                                    public void restore(Integer snapshot) { value[0]=snapshot; }
                                });
                            }
                        };
                    }
                });
                """);
        try(ModTestKit kit=ModTestKit.openTrusted(repository,work.resolve("storage"))) {
            var module=kit.launch(new Sonic3kGameModule(),new GameplayLaunchRequest("s3k","sonic",List.of()));
            var registry=ModTestKit.moduleState(module);
            var initial=ModTestKit.capture(registry);
            assertFalse(initial.entries().containsKey("camera"));
            @SuppressWarnings("unchecked")
            var adapter=(com.openggf.game.rewind.RewindSnapshottable<Object>)module.rewindAdapters().getFirst();
            adapter.restore(7);
            var changed=ModTestKit.capture(registry);
            assertNotEquals(initial.entries(),changed.entries());
            ModTestKit.restore(registry,initial);
            assertEquals(initial.entries(),ModTestKit.capture(registry).entries());
            adapter.restore(7);
            assertEquals(changed.entries(),ModTestKit.capture(registry).entries());
            assertTrue(kit.findings().isEmpty());
        }
    }

    private void pack(Path repository,String owner,String dependencies,String registration) throws Exception {
        pack(repository, owner, dependencies, registration, "s3k", java.util.Map.of());
    }

    private void pack(Path repository, String owner, String dependencies, String registration,
            String gameId, java.util.Map<String, byte[]> resources) throws Exception {
        Path classes=Files.createDirectories(work.resolve(owner+"-classes"));
        Path source=work.resolve(owner+"-source/fixture/Entry.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source,"package fixture; public final class Entry implements com.openggf.mods.code.GgfMod { "
                +"public void register(com.openggf.mods.code.ModContext context) { "+registration+" } }");
        List<String> args=new ArrayList<>(List.of("--release","21","-cp",System.getProperty("java.class.path"),
                "-d",classes.toString(),source.toString()));
        assertEquals(0,ToolProvider.getSystemJavaCompiler().run(null,null,null,args.toArray(String[]::new)));
        Path manifest=classes.resolve("META-INF/openggf-mod.yaml"); Files.createDirectories(manifest.getParent());
        Files.writeString(manifest,"""
                formatVersion: 1
                id: %s
                name: Creator test fixture
                version: 1.0.0
                authors: [OpenGGF]
                description: Original production pipeline test fixture
                engineApiRange: ">=0.7.0 <0.8.0"
                type: patch
                baseGame: %s
                entrypoint: fixture.Entry
                dependencies:%s
                audioOverrides: {}
                artOverrides: {}
                """.formatted(owner, gameId, dependencies.isEmpty()?" []":"\n"+dependencies.stripTrailing()));
        for (var resource : resources.entrySet()) {
            Path destination = classes.resolve(resource.getKey());
            Files.createDirectories(destination.getParent());
            Files.write(destination, resource.getValue());
        }
        assertEquals(0,GgfModCli.run(new String[]{"package","--input",classes.toString(),"--out",
                repository.resolve(owner+".jar").toString()},System.out));
    }
}
