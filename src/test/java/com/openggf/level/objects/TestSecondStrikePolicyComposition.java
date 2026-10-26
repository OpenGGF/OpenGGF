package com.openggf.level.objects;

import com.openggf.LevelFrameContext;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.game.GameDataSource;
import com.openggf.game.GameModule;
import com.openggf.game.LevelLoadCause;
import com.openggf.game.mutators.*;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.session.*;
import com.openggf.game.sonic3k.mutators.NativeMutatorRuntime;
import com.openggf.level.LevelManager;
import com.openggf.mods.mutators.*;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.tests.rules.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import static com.openggf.level.objects.SecondStrikeBarrageFixture.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual compiled creator targets against the shared world-owned policy consumer; short local fixtures. */
@RequiresRom(SonicGame.SONIC_3K)
class TestSecondStrikePolicyComposition {
    @TempDir static Path temp;
    static URLClassLoader creator;
    SecondStrikeBarrageFixture f;
    NativeMutatorRuntime runtime;
    LevelFrameContext frame;
    AtomicReference<List<TransientMutatorPolicySource.Entry>> entries;
    @BeforeAll static void compile() throws Exception { creator = SecondStrikeBarrageFixture.compile(temp); }
    @AfterAll static void close() throws Exception { creator.close(); }
    @AfterEach void clear() { AbstractObjectInstance.resetCameraBoundsForTests(); SessionManager.clear(); }
    @BeforeEach void setup() throws Exception {
        f = new SecondStrikeBarrageFixture(creator);
        entries = new AtomicReference<>(List.of());
        var source = mock(TransientMutatorPolicySource.class);
        when(source.entries()).thenAnswer(call -> entries.get()); when(source.ownerAvailable(anyString())).thenReturn(true);
        var state = new WorldSessionPolicyState() {
            public <T> T getService(Class<T> type) { return type.isInstance(source) ? type.cast(source) : null; }
            public RewindSnapshottable<?> rewindAdapter() { return new RewindSnapshottable<Boolean>() {
                public String key() { return "source"; } public Boolean capture() { return true; } public void restore(Boolean saved) { }
            }; }
            public void beforeAssembly(LevelLoadCause cause) { } public void bindRoster(SpriteManager sprites) { }
            public void failedAssembly(LevelLoadCause cause) { } public void closeScreens() { } public void retire() { }
        };
        var module = mock(GameModule.class); var data = mock(GameDataSource.class);
        var rom = com.openggf.data.Rom.fromReader(com.openggf.data.RomByteReader.fromBytes(new byte[128]), "policy fixture");
        when(data.rom()).thenReturn(Optional.of(rom));
        var sizes = mock(TouchResponseTable.class); when(sizes.getWidthRadius(anyInt())).thenReturn(16);
        when(sizes.getHeightRadius(anyInt())).thenReturn(16);
        when(module.createTouchResponseTable(data)).thenReturn(sizes);
        when(module.getGameService(WorldSessionPolicyProvider.class)).thenReturn(world -> {
            runtime = new NativeMutatorRuntime(world);
            return CompositeWorldSessionPolicyState.of(state, runtime);
        });
        var world = new WorldSession(module, module, data, null); f.services.withWorldSession(world);
        var sprites = mock(SpriteManager.class); when(sprites.getMainPlayable()).thenReturn(f.player);
        when(sprites.getSidekicks()).thenReturn(List.of());
        var level = mock(LevelManager.class); when(level.getObjectManager()).thenReturn(f.manager);
        frame = mock(LevelFrameContext.class); when(frame.spriteManager()).thenReturn(sprites); when(frame.levelManager()).thenReturn(level);
    }
    void active(MutatorPolicy policy) {
        entries.set(List.of(new TransientMutatorPolicySource.Entry("test", 1, policy, 0, 600, false)));
        runtime.beforeNativeTick(frame, LogicalInputSnapshot.neutral());
    }
    @Test void targetingAndFiringAdmissionUseSupportedRulesAndKeepDefaultTimings() throws Exception {
        assertTrue(ObjectCombatRules.permitsTargeting(f.services)); assertTrue(ObjectCombatRules.permitsFiring(f.services, 400));
        assertEquals(108, ObjectCombatRules.firingCooldown(f.services, 108));
        active(new EnemyRulePolicy(EnemyRulePolicy.Kind.BLIND_SPOT));
        var guard = f.enemy("GuardBadnik", "guard", 400, 300); guard.update(0, f.player);
        assertEquals(0x0b, touch(guard).getCollisionFlags(), "blind targeting cannot start an armored tell");
        var spotter = f.enemy("SpotterBadnik", "spotter", 400, 300); spotter.update(0, f.player);
        assertFalse((boolean) creator.loadClass("secondstrike.enemies.SpotterBadnik").getMethod("isCalling").invoke(spotter));
        active(new EnemyRulePolicy(EnemyRulePolicy.Kind.LOOK_GATE));
        f.player.setDirection(com.openggf.physics.Direction.LEFT);
        assertFalse(ObjectCombatRules.permitsFiring(f.services, 600)); assertTrue(ObjectCombatRules.permitsFiring(f.services, 400));
        active(new EnemyRulePolicy(EnemyRulePolicy.Kind.FIRE_CADENCE));
        assertEquals(27, ObjectCombatRules.firingCooldown(f.services, 108));
        var gunner = f.gunner(); f.firstRelease(gunner); ticks(gunner, f.player, 12);
        assertEquals(16, f.shots().size(), "the native cadence global projectile bound includes admitted creator seeds");
    }
    @Test void groundedCreatorGuardOptsIntoStasisWhileStationaryArtilleryKeepsItsRoutine() throws Exception {
        var guard = f.enemy("GuardBadnik", "guard", 400, 300); var gunner = f.gunner();
        active(new EnemyRulePolicy(EnemyRulePolicy.Kind.STASIS));
        assertFalse(runtime.permitsRoutine(guard, f.player)); assertTrue(runtime.permitsRoutine(gunner, f.player));
        active(new EnemyRulePolicy(EnemyRulePolicy.Kind.TRUCE));
        assertFalse(runtime.permitsContact(guard, f.player)); assertFalse(runtime.permitsContact(gunner, f.player));
        assertTrue(runtime.permitsRoutine(guard, f.player));
        assertEquals(ObjectPresentationMetadata.Role.BADNIK, guard.presentationMetadata().role());
        assertEquals(guard.getX(), guard.presentationMetadata().anchorX());
    }
    @Test void creatorSeedsJoinBoundedFieldSelectionAndRewindExactIdentities() throws Exception {
        var gunner = f.gunner(); f.firstRelease(gunner); ticks(gunner, f.player, 12);
        place(f.player, 500, 300);
        active(new ObjectFieldPolicy(ObjectFieldPolicy.Target.PROJECTILE, ObjectFieldPolicy.Mode.ATTRACT));
        var policy = runtime.capture(); var selected = policy.fieldTargets().get(1L).identities(); assertEquals(6, selected.size());
        var objects = f.manager.rewindSnapshottable().capture();
        runtime.beforeNativeTick(frame, LogicalInputSnapshot.neutral());
        for (var seed : f.shots()) {
            int before = seed.getX(); runtime.afterRoutine(seed, f.player);
            assertEquals(before + (selected.contains(f.manager.objectQuery().identityOf(seed).orElseThrow()) ? 1 : 0), seed.getX());
        }
        var expected = f.manager.rewindSnapshottable().capture();
        f.manager.rewindSnapshottable().restore(objects); runtime.restore(policy); f.bounds();
        runtime.beforeNativeTick(frame, LogicalInputSnapshot.neutral());
        for (var seed : f.shots()) runtime.afterRoutine(seed, f.player);
        assertEquals(List.of(), com.openggf.game.rewind.RewindSnapshotDiff.diffKey("object-manager", expected, f.manager.rewindSnapshottable().capture()));
    }

    @Test void registeredGroundedCreatorBudsKeepExactContentIdentityAndBoundedLeaseAcrossReplay() throws Exception {
        var terrain = mock(com.openggf.level.Level.class);
        var block = new com.openggf.level.Block(8);
        for (int y=0;y<8;y++) for (int x=0;x<8;x++) block.setChunkDesc(x,y,new com.openggf.level.ChunkDesc(0));
        when(terrain.getBlockPixelSize()).thenReturn(128);
        when(terrain.getLayerWidthBlocks(anyInt())).thenReturn(32); when(terrain.getLayerHeightBlocks(anyInt())).thenReturn(16);
        when(terrain.getMap()).thenReturn(new com.openggf.level.Map(2,32,16));
        when(terrain.getBlockCount()).thenReturn(1); when(terrain.getBlock(0)).thenReturn(block);
        var level = frame.levelManager(); when(level.getCurrentLevel()).thenReturn(terrain);
        f.services.withLevelManager(level).withSpriteManager(frame.spriteManager()); f.flatFloor = true;
        when(f.services.camera().getMaxX()).thenReturn((short)4096);
        active(new EnemyRulePolicy(EnemyRulePolicy.Kind.BUDDING));
        var guard = f.enemy("GuardBadnik", "guard", 400, 300);
        var original = f.manager.rewindSnapshottable().capture(); var policy = runtime.capture();
        runtime.beforeNativeTick(frame, LogicalInputSnapshot.neutral());
        runtime.badnikDefeated(guard, f.player);
        var buds = f.manager.getActiveObjects().stream().filter(o -> o != guard && o.getClass() == guard.getClass()).toList();
        assertEquals(2, buds.size(), "a registered grounded creator joins budding without selecting a native AI class");
        for (var bud : buds) {
            assertEquals("second-strike", bud.getSpawn().ownerModId()); assertEquals("second-strike:guard", bud.getSpawn().objectKey());
            assertEquals(-1, bud.getSpawn().layoutIndex()); assertFalse(bud.getSpawn().respawnTracked());
            assertEquals(3, ((TouchResponseProvider) bud).getCollisionProperty() + 1);
            assertNotEquals(f.manager.objectQuery().identityOf(guard), f.manager.objectQuery().identityOf(bud));
        }
        var objects = f.manager.rewindSnapshottable().capture(); var leases = runtime.capture();
        assertEquals(2, leases.owned().size());
        leases.owned().forEach(lease -> { assertEquals("test", lease.owner()); assertEquals("second-strike", lease.contentOwner()); });
        runtime.badnikDefeated(buds.getFirst(), f.player); assertEquals(2, runtime.capture().owned().size(), "no recursive budding");
        f.manager.rewindSnapshottable().restore(original); runtime.restore(policy); f.bounds();
        runtime.beforeNativeTick(frame, LogicalInputSnapshot.neutral());
        runtime.badnikDefeated(f.manager.getActiveObjects().stream().filter(o -> o.getClass() == guard.getClass()).findFirst().orElseThrow(), f.player);
        assertEquals(objects.dynamicObjects(), f.manager.rewindSnapshottable().capture().dynamicObjects());
        assertEquals(leases, runtime.capture());
        runtime.ownerRetired("second-strike");
        assertTrue(runtime.capture().owned().isEmpty(), "content retirement removes leases owned by a separate mutator producer");
        f.manager.rewindSnapshottable().restore(objects); runtime.restore(leases); f.bounds();
        runtime.ownerRetired("test"); assertTrue(runtime.capture().owned().isEmpty(), "producer retirement independently removes its admitted derivatives");
    }

    @Test void bodyPresentationComposesWithoutMovingNativeCollisionOrCapturedGameplay() throws Exception {
        var guard = f.enemy("GuardBadnik", "guard", 400, 300);
        var saved = f.manager.rewindSnapshottable().capture(); var flags = touch(guard).getCollisionFlags();
        var metadata = guard.presentationMetadata();
        var body = new com.openggf.graphics.SpritePresentation.Body(com.openggf.graphics.SpritePresentation.Role.valueOf(metadata.role().name()),
                metadata.anchorX(), metadata.anchorY());
        var subject = new com.openggf.graphics.SpritePresentation.Subject("guard", com.openggf.graphics.SpritePresentation.Part.OBJECT_BODY,
                false, 100, null, body);
        var tile = new com.openggf.graphics.SpritePresentation.Tile(com.openggf.graphics.SpritePresentation.Layer.OBJECT,
                42, 0, false, false, false, 388, 292, 8, 8, false, 0, false, 1, 0, 8, subject);
        var stock = com.openggf.game.mutators.SpritePictureTransform.forTile(tile, 0, 0, List.of());
        assertEquals(1, stock.xx()); assertEquals(1, stock.yy());
        var policies = List.of(new TransientMutatorPolicySource.Entry("test", 1,
                new SpritePresentationPolicy(SpritePresentationPolicy.Mode.SCALE, SpritePresentationPolicy.Target.BADNIKS, 150, 120), 0, 600, false));
        var transformed = com.openggf.game.mutators.SpritePictureTransform.forTile(tile, 0, 0, policies);
        assertEquals(1.5, transformed.xx()); assertEquals(1.5, transformed.yy());
        assertEquals(400, guard.getX()); assertEquals(300, guard.getY()); assertEquals(flags, touch(guard).getCollisionFlags());
        assertEquals(List.of(), com.openggf.game.rewind.RewindSnapshotDiff.diffKey("object-manager", saved, f.manager.rewindSnapshottable().capture()));
    }
}
