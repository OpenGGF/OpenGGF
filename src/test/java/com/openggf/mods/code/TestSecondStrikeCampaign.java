package com.openggf.mods.code;

import com.openggf.data.RomByteReader;
import com.openggf.game.GameModule;
import com.openggf.game.TitleScreenProvider;
import com.openggf.game.mutators.TransientMutatorPolicyAccess;
import com.openggf.game.session.WorldSession;
import com.openggf.game.session.WorldSessionPolicyAccess;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.Sonic3kObjectPlacement;
import com.openggf.game.sonic3k.Sonic3kRingPlacement;
import com.openggf.game.sonic3k.difficulty.AizMinibossAttackPolicy;
import com.openggf.game.sonic3k.difficulty.BlueSphereLayoutPolicy;
import com.openggf.game.sonic3k.difficulty.GumballSpringPolicy;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.objects.Sonic3kMonitorObjectInstance;
import com.openggf.game.sonic3k.mutators.NativeMutatorRuntime;
import com.openggf.io.ModAssetRoot;
import com.openggf.level.AbstractLevel;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.level.objects.TouchCategory;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.level.rings.RingSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual creator registration over native inventories; cold gameplay is a separate capture. */
@RequiresRom(SonicGame.SONIC_3K)
class TestSecondStrikeCampaign {
    @TempDir static Path temporary;
    static URLClassLoader loader;
    static GgfMod mod;
    static RomByteReader rom;

    @BeforeAll static void compileRealModAndReadOriginal() throws Exception {
        String romPath = System.getProperty("s3k.rom.path");
        assertNotNull(romPath, "Use the original absolute S3K ROM; these checks must not skip");
        rom = new RomByteReader(Files.readAllBytes(Path.of(romPath)));
        loader = SecondStrikeSourceFixture.compile(temporary.resolve("classes"), SecondStrikeSourceFixture.MAIN);
        mod = (GgfMod) loader.loadClass("secondstrike.SecondStrikeMod").getConstructor().newInstance();
    }
    @AfterAll static void closeLoader() throws Exception { if (loader != null) loader.close(); }

    private static ModRegistrationPlan registration() {
        var context = new ModContext("second-strike", "s3k", ModAssetRoot.forTests("second-strike"));
        mod.register(context);
        return context.freeze();
    }
    private static GameModule services(ModRegistrationPlan plan) {
        return OwnedModServices.decorate(mock(GameModule.class, CALLS_REAL_METHODS), plan,
                new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                        ignored -> new ModStateSaveResult.Saved(), ignored -> { }));
    }
    private static final class NativeInventory extends AbstractLevel {
        NativeInventory(int zone, int act) throws Exception {
            super(zone);
            objects = new Sonic3kObjectPlacement(rom).load(zone, act);
            rings = new Sonic3kRingPlacement(rom).load(zone, act);
        }
        void addRing(RingSpawn ring) { rings = java.util.stream.Stream.concat(rings.stream(), java.util.stream.Stream.of(ring)).toList(); }
        void replaceRecord(com.openggf.level.objects.ObjectSpawn replacement) {
            objects = objects.stream().map(spawn -> spawn.layoutIndex() == replacement.layoutIndex()
                    ? replacement : spawn).toList();
        }
    }

    @Test void treeFactorySelectionPreservesAnExistingForeignOwner() throws Exception {
        var forest = new NativeInventory(0, 0);
        var original = forest.getObjects().get(150);
        var foreign = new com.openggf.level.objects.ObjectSpawn(original.x(), original.y(), original.objectId(),
                original.subtype(), original.renderFlags(), original.respawnTracked(), original.rawYWord(),
                original.layoutIndex(), "another-mod", "another-mod:tree");
        forest.replaceRecord(foreign);
        var module = services(registration());
        module.transformDecodedLevel(forest);
        assertSame(foreign, forest.getObjects().get(150));
        module.transformDecodedLevel(forest);
        assertSame(foreign, forest.getObjects().get(150), "repeat admission cannot reclaim a foreign tree");
    }

    @Test void registrationAuthorsBothApparentActsAndPreservesOriginalRecordData() throws Exception {
        var plan = registration();
        assertEquals(1, plan.explicitPatches().size());
        var module = services(plan);
        var forest = new NativeInventory(0, 0);
        var shoes = forest.getObjects().stream().filter(s -> s.objectId() == 1 && (s.subtype() & 15) == 4).toList();
        assertSame(forest, module.transformDecodedLevel(forest));
        assertEquals(3, forest.getRings().size());
        assertEquals(21, forest.getObjects().stream().filter(s -> s.ownerModId() != null).count());
        assertEquals(shoes, forest.getObjects().stream().filter(s -> s.objectId() == 1 && (s.subtype() & 15) == 4).toList());
        assertSame(shoes.getFirst(), forest.getObjects().get(shoes.getFirst().layoutIndex()));
        assertEquals(10, forest.getObjects().stream().filter(s -> "second-strike:robotnik-monitor".equals(s.objectKey())).count());
        assertEquals(List.of(1, 35, 74), forest.getRings().stream().map(RingSpawn::placementId).toList());
        assertEquals(List.of(40), forest.getObjects().stream()
                .filter(s -> "second-strike:artillery-split".equals(s.objectKey())).map(s -> s.layoutIndex()).toList());
        var forestObjects = forest.getObjects(); var forestRings = forest.getRings();
        assertSame(forest, module.transformDecodedLevel(forest));
        assertEquals(forestObjects, forest.getObjects()); assertEquals(forestRings, forest.getRings());
        var fire = new NativeInventory(0, 1);
        var nativeCaterkillers = fire.getObjects().stream().filter(s -> s.objectId() == 0x8f && java.util.Set.of(82, 100, 119, 244, 625).contains(s.layoutIndex())).toList();
        assertEquals(List.of(82, 100, 119, 244, 625), nativeCaterkillers.stream().map(s -> s.layoutIndex()).toList());
        var nativeStructures = fire.getObjects().stream().filter(s -> s.objectId() < 0x8c && s.objectId() != 1).toList();
        assertSame(fire, module.transformDecodedLevel(fire));
        assertEquals(42, fire.getObjects().stream().filter(s -> s.ownerModId() != null).count());
        assertEquals(2, fire.getRings().stream().filter(r -> r.x() < 0x1000).count());
        assertEquals(List.of(12, 68), fire.getRings().stream().filter(r -> r.x() < 0x1000).map(RingSpawn::placementId).toList());
        assertEquals(5, forest.getRings().size() + fire.getRings().stream().filter(r -> r.x() < 0x1000).count());
        assertEquals(22, fire.getObjects().stream().filter(s -> "second-strike:robotnik-monitor".equals(s.objectKey())).count());
        assertEquals(List.of(95, 247, 296, 518, 704), fire.getObjects().stream()
                .filter(s -> "second-strike:artillery-split".equals(s.objectKey())).map(s -> s.layoutIndex()).toList());
        var longer = fire.getObjects().stream().filter(s -> "second-strike:caterkiller".equals(s.objectKey())).toList();
        assertEquals(5, longer.size());
        for (int i = 0; i < longer.size(); i++) {
            var original = nativeCaterkillers.get(i); var bound = longer.get(i);
            assertEquals(original.objectId(), bound.objectId()); assertEquals(original.layoutIndex(), bound.layoutIndex());
            assertEquals(original.x(), bound.x()); assertEquals(original.y(), bound.y());
            assertEquals(original.renderFlags(), bound.renderFlags()); assertEquals(original.subtype(), bound.subtype());
            assertEquals(original.rawYWord(), bound.rawYWord()); assertEquals(original.respawnTracked(), bound.respawnTracked());
            assertInstanceOf(com.openggf.game.sonic3k.objects.badniks.CaterkillerJrHeadInstance.class,
                    plan.objectFactories().get(bound.objectKey()).create(bound, null));
        }
        assertTrue(forest.getRings().size() + fire.getRings().stream().filter(r -> r.x() < 0x1000).count() < 20,
                "Placed rings and converted ordinary monitors cannot qualify a native bonus post; stage prizes are separate");
        assertEquals(nativeStructures, fire.getObjects().stream().filter(s -> s.objectId() < 0x8c && s.objectId() != 1)
                .map(s -> new com.openggf.level.objects.ObjectSpawn(s.x(), s.y(), s.objectId(), s.subtype(),
                        s.renderFlags(), s.respawnTracked(), s.rawYWord(), s.layoutIndex())).toList());
        assertSame(fire, module.transformDecodedLevel(fire));
        assertEquals(42, fire.getObjects().stream().filter(s -> s.ownerModId() != null).count(), "idempotent reload");
    }

    @Test void realCampaignEntryAdmitsFiveNativeZoneInventoriesWithoutChangingTheirRecords() throws Exception {
        var plan = registration();
        assertEquals(26, plan.objectFactories().size());
        assertEquals(12, plan.decodedLevelPatches().size());
        var module = services(plan);
        // Native zone indices skip Flying Battery (4), whose campaign follows Mushroom Hill.
        for (int zone : new int[]{1, 2, 3, 5, 6}) {
            for (int act : new int[]{0, 1}) {
                String scene = "zone " + zone + " physical act " + act;
                var source = new NativeInventory(zone, act);
                var originals = source.getObjects();
                var originalRings = source.getRings();
                assertSame(source, module.transformDecodedLevel(source), scene);
                assertEquals(originals.size(), source.getObjects().size(), scene);
                int bindings = 0;
                for (int index = 0; index < originals.size(); index++) {
                    var original = originals.get(index);
                    var actual = source.getObjects().get(index);
                    assertEquals(original, new com.openggf.level.objects.ObjectSpawn(
                            actual.x(), actual.y(), actual.objectId(), actual.subtype(), actual.renderFlags(),
                            actual.respawnTracked(), actual.rawYWord(), actual.layoutIndex()),
                            scene + " original record " + original.layoutIndex());
                    if (actual.ownerModId() != null) {
                        assertEquals("second-strike", actual.ownerModId(), scene);
                        assertTrue(plan.objectFactories().containsKey(actual.objectKey()), scene + " " + actual.objectKey());
                        bindings++;
                    }
                }
                // ICZ2/LBZ2 currently author resource budgets only; their encounter work remains open.
                assertTrue(bindings > 0 || source.getRings().size() < originalRings.size(),
                        scene + " must use its registered bindings or original-ring retention");
                assertTrue(source.getRings().size() >= 1 && source.getRings().size() <= 5, scene);
                assertTrue(originalRings.containsAll(source.getRings()), scene + " retains only original rings");
                var admittedObjects = source.getObjects();
                var admittedRings = source.getRings();
                assertSame(source, module.transformDecodedLevel(source), scene);
                assertEquals(admittedObjects, source.getObjects(), scene + " repeated objects");
                assertEquals(admittedRings, source.getRings(), scene + " repeated rings");
            }
        }
    }

    @Test void everyNonShoeMonitorUsesActualNativeRobotnikContentsWithoutChangingItsPlacement() throws Exception {
        var plan = registration(); var module = services(plan); int converted = 0;
        for (int act : new int[]{0, 1}) {
            var source = new NativeInventory(0, act);
            var original = source.getObjects(); module.transformDecodedLevel(source);
            for (var spawn : source.getObjects()) {
                if (!"second-strike:robotnik-monitor".equals(spawn.objectKey())) continue;
                var nativeSpawn = original.get(spawn.layoutIndex());
                assertEquals(nativeSpawn.x(), spawn.x()); assertEquals(nativeSpawn.y(), spawn.y());
                assertEquals(nativeSpawn.subtype(), spawn.subtype()); assertEquals(nativeSpawn.rawYWord(), spawn.rawYWord());
                assertEquals(nativeSpawn.renderFlags(), spawn.renderFlags()); assertEquals(nativeSpawn.objectId(), spawn.objectId());
                assertEquals(nativeSpawn.respawnTracked(), spawn.respawnTracked());
                assertNotEquals(4, spawn.subtype() & 15, "Never replace speed shoes");
                var monitor = (Sonic3kMonitorObjectInstance) plan.objectFactories().get(spawn.objectKey()).create(spawn, null);
                assertSame(spawn, monitor.getSpawn());
                monitor.setServices(new TestObjectServices());
                var player = mock(AbstractPlayableSprite.class);
                when(player.getAnimationId()).thenReturn(Sonic3kAnimationIds.ROLL.id());
                when(player.getRingCount()).thenReturn(1);
                monitor.onTouchResponse(player, new TouchResponseResult(0, 14, 14, TouchCategory.SPECIAL), 0);
                for (int tick = 1; tick <= 34; tick++) monitor.update(tick, player);
                verify(player).applyHurtOrDeath(0, false, true);
                verify(player, never()).addRings(anyInt());
                var restored = monitor.recreateForRewind(new com.openggf.level.objects.RewindRecreateContext(
                        spawn, monitor.captureRewindState(), new TestObjectServices()));
                assertEquals(monitor.getClass(), restored.getClass()); assertSame(spawn, restored.getSpawn());
                converted++;
            }
        }
        assertEquals(32, converted, "Every ordinary AIZ box except original shoes is truthfully hostile");
    }

    @Test void extraRecordsCannotEvadeEitherApparentActBudget() throws Exception {
        var module = services(registration()); var forest = new NativeInventory(0, 0);
        forest.addRing(new RingSpawn(0x2000, 0x220, 1000)); module.transformDecodedLevel(forest);
        assertEquals(3, forest.getRings().size());
        var fire = new NativeInventory(0, 1);
        fire.addRing(new RingSpawn(0x800, 0x220, 1000)); fire.addRing(new RingSpawn(0x3000, 0x220, 1001)); module.transformDecodedLevel(fire);
        assertEquals(2, fire.getRings().stream().filter(r -> r.x() < 0x1000).count());
        assertEquals(List.of(105, 126, 210, 250, 253), fire.getRings().stream().filter(r -> r.x() >= 0x1800).map(RingSpawn::placementId).toList());
        assertEquals(7, fire.getRings().size());
        assertThrows(UnsupportedOperationException.class, () -> forest.getRings().clear());
    }

    @Test void servicesAreCanonicalAndStockTitleSaveAndFrameOwnersStayDelegated() throws Exception {
        var plan = registration();
        var module = services(plan);
        assertEquals(16, module.getGameService(AizMinibossAttackPolicy.class).followupDelayTicks(3));
        assertEquals(-1, module.getGameService(AizMinibossAttackPolicy.class).followupDelayTicks(6));
        assertArrayEquals(new int[]{-32, 0, 32}, module.getGameService(GumballSpringPolicy.class).springOffsets());
        assertNotNull(module.getGameService(BlueSphereLayoutPolicy.class));
        var base = mock(GameModule.class, CALLS_REAL_METHODS);
        var title = mock(TitleScreenProvider.class);
        var dataSelect = mock(com.openggf.game.DataSelectProvider.class);
        when(base.getTitleScreenProvider()).thenReturn(title);
        when(base.getDataSelectProvider()).thenReturn(dataSelect);
        var patched = plan.explicitPatches().getFirst().apply(base, null);
        assertSame(dataSelect, patched.getDataSelectProvider());
        assertSame(base.gameplayFrameController(), patched.gameplayFrameController());
        assertSame(base.getSaveSnapshotProvider(), patched.getSaveSnapshotProvider());
        patched.getTitleScreenProvider().initialize();
        patched.getTitleScreenProvider().setExitToLevelHandler(() -> { });
        patched.getTitleScreenProvider().reset();
        verify(title).initialize(); verify(title).setExitToLevelHandler(any()); verify(title).reset();
    }

    @Test void publishedCampaignBoardProviderAdmitsAllFourteenOriginalBoards() throws Exception {
        var module = services(registration());
        var policy = module.getGameService(BlueSphereLayoutPolicy.class);
        assertNotNull(policy, "the real creator entry must publish its single board provider");
        assertArrayEquals(new int[]{-32, 0, 32},
                module.getGameService(GumballSpringPolicy.class).springOffsets());
        var nativeRom = new com.openggf.data.Rom();
        assertTrue(nativeRom.open(System.getProperty("s3k.rom.path")));
        try {
            byte[] superBoards = new com.openggf.game.sonic3k.specialstage.Sonic3kSpecialStageDataLoader(nativeRom)
                    .getCompressedLayoutSet(0);
            int authored = 0;
            for (int stage = 0; stage < 16; stage++) {
                byte[] original = stage < 8
                        ? nativeRom.readBytes(com.openggf.game.sonic3k.specialstage.Sonic3kSpecialStageRomOffsets
                                .LAYOUT_S3_STAGE_1 + stage * 0x408L, 0x408)
                        : java.util.Arrays.copyOfRange(superBoards, (stage - 8) * 0x408, (stage - 7) * 0x408);
                byte[] unchanged = original.clone();
                byte[] actual = policy.transformLayout(stage, original);
                assertArrayEquals(unchanged, original, "published provider input " + stage);
                assertArrayEquals(actual, com.openggf.game.sonic3k.difficulty.S3kDifficultyRules
                        .blueSphereLayout(original, actual), "published provider native admission " + stage);
                if (stage == 7 || stage == 15) {
                    assertArrayEquals(original, actual, "native non-campaign slot " + stage);
                } else {
                    assertFalse(java.util.Arrays.equals(original, actual), "authored campaign board " + stage);
                    authored++;
                }
            }
            assertEquals(14, authored);
        } finally {
            nativeRom.close();
        }
    }

    @Test void resolvedSecondStrikeWorldKeepsNativePolicyAssemblyWithoutAnOptInMutatorGraph() throws Exception {
        var plan = registration();
        var nativeModule = new Sonic3kGameModule();
        var faults = new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                ignored -> new ModStateSaveResult.Saved(), ignored -> { });
        GameModule resolved = new ModBackedGamePatch(plan, faults).apply(nativeModule, null);
        for (var patch : plan.explicitPatches()) resolved = patch.apply(resolved, null);
        var world = new WorldSession(nativeModule, resolved, null);

        assertFalse(WorldSessionPolicyAccess.hasPolicies(world));
        assertNull(TransientMutatorPolicyAccess.source(world));
        assertNull(WorldSessionPolicyAccess.getService(world, NativeMutatorRuntime.class));
        assertEquals(16, world.getGameModule().getGameService(AizMinibossAttackPolicy.class).followupDelayTicks(3));
    }

    @Test void zonesOutsideTheAizAndC1BatchKeepTheirExactNativeTables() throws Exception {
        var source = new NativeInventory(7, 0);
        var objects = source.getObjects(); var rings = source.getRings();
        assertSame(source, services(registration()).transformDecodedLevel(source));
        assertSame(objects, source.getObjects()); assertSame(rings, source.getRings());
    }

    @Test void campaignIdentityAndContentHaveTheSameCharacterEligibility() {
        var plan = registration();
        var content = new ModBackedGamePatch(plan, new ModFaultBoundary(Map.of(),
                new ModRuntimeFindingStore(), ignored -> new ModStateSaveResult.Saved(), ignored -> { }));
        var presentation = plan.explicitPatches().getFirst();
        for (String character : List.of("sonic", "tails", "knuckles", "example:runner")) {
            var request = new com.openggf.game.patch.GameplayLaunchRequest("s3k", character, List.of());
            assertEquals(content.activatesFor(request), presentation.activatesFor(request), character);
        }
        var otherGame = new com.openggf.game.patch.GameplayLaunchRequest("s2", "sonic", List.of());
        assertFalse(content.activatesFor(otherGame));
        assertFalse(presentation.activatesFor(otherGame));
    }
    @Test void hostSuppliedTitleCanvasDelegatesNativeDrawingAndDrawsBadgeOnlyWhileActive() throws Exception {
        var base = mock(GameModule.class);
        var nativeTitle = mock(TitleScreenProvider.class);
        when(base.getTitleScreenProvider()).thenReturn(nativeTitle);
        var title = registration().explicitPatches().getFirst().apply(base, null).getTitleScreenProvider();
        var canvas = mock(com.openggf.mods.ui.PixelCanvas.class);
        when(canvas.width()).thenReturn(528); when(canvas.height()).thenReturn(224);
        when(nativeTitle.getState()).thenReturn(TitleScreenProvider.State.ACTIVE);
        title.draw(); title.drawOverlay(canvas);
        verify(nativeTitle).draw(); verify(nativeTitle).drawOverlay(canvas);
        verify(canvas).fill(anyInt(), eq(195), eq(91), eq(12), eq(0xEF102438));
        clearInvocations(canvas);
        when(nativeTitle.getState()).thenReturn(TitleScreenProvider.State.FADE_IN);
        title.drawOverlay(canvas);
        verifyNoInteractions(canvas);
    }

}
