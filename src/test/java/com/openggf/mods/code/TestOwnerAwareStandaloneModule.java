package com.openggf.mods.code;

import com.openggf.game.GameDataSource;
import com.openggf.game.GameModule;
import com.openggf.game.ZoneRegistry;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.level.objects.*;
import com.openggf.graphics.GLCommand;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.Set;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestOwnerAwareStandaloneModule {
    @Test
    void characterContributionsPreserveRegistrationOrder() {
        ModFaultBoundary boundary = new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                owners -> new ModStateSaveResult.Saved(), owners -> { });
        GameModule delegate = mock(GameModule.class);
        when(delegate.getPlayableCharacterRegistry())
                .thenReturn(com.openggf.game.PlayableCharacterRegistry.empty());
        com.openggf.game.CharacterKey runner = com.openggf.game.CharacterKey.mod("owner", "runner");
        com.openggf.game.CharacterKey friend = com.openggf.game.CharacterKey.mod("owner", "friend");
        Map<com.openggf.game.CharacterKey, com.openggf.game.CharacterDefinition> characters =
                new LinkedHashMap<>();
        characters.put(runner, characterDefinition(runner));
        characters.put(friend, characterDefinition(friend));

        GameModule wrapped = OwnerAwareStandaloneModule.wrap("owner", delegate, boundary, characters);

        assertEquals(List.of(runner, friend),
                wrapped.getPlayableCharacterRegistry().definitions().keySet().stream().toList());
    }

    @Test
    void dynamicCreatorObjectRetainsOwnerThroughRewindRecreation() {
        ModFaultBoundary boundary = new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                owners -> new ModStateSaveResult.Saved(), owners -> { });
        GameModule delegate = mock(GameModule.class);
        ObjectRegistry registry = mock(ObjectRegistry.class);
        ObjectSpawn spawn = ownedSpawn();
        when(delegate.createObjectRegistry()).thenReturn(registry);
        when(registry.objectSlotLayout()).thenReturn(ObjectSlotLayout.SONIC_1);
        when(registry.create(spawn)).thenAnswer(ignored -> new RewindableOwnedObject(spawn));
        GameModule wrapped = OwnerAwareStandaloneModule.wrap("owner", delegate, boundary, Map.of());
        ObjectRegistry wrappedRegistry = wrapped.createObjectRegistry();
        ObjectManager manager = new ObjectManager(List.of(), wrappedRegistry,
                0, null, null, null, null, new StubObjectServices());
        manager.setRewindClassResolver(new RewindClassResolver() {
            @Override public Optional<Class<?>> resolve(String owner, String binaryName) {
                return "owner".equals(owner) && RewindableOwnedObject.class.getName().equals(binaryName)
                        ? Optional.of(RewindableOwnedObject.class) : Optional.empty();
            }
            @Override public Optional<String> ownerOf(Class<?> type) {
                return type == RewindableOwnedObject.class ? Optional.of("owner") : Optional.empty();
            }
        });
        RewindableOwnedObject original = manager.createDynamicObject(
                () -> (RewindableOwnedObject) wrappedRegistry.create(spawn));

        var snapshot = manager.rewindSnapshottable().capture();
        assertEquals("owner", snapshot.dynamicObjects().getFirst().ownerModId());
        manager.removeDynamicObject(original);
        manager.rewindSnapshottable().restore(snapshot);

        ObjectInstance restored = manager.getActiveObjects().stream()
                .filter(RewindableOwnedObject.class::isInstance).findFirst().orElseThrow();
        assertNotSame(original, restored);
    }

    @Test
    void concreteStandaloneObjectUpdateRunsInsideOwnerBoundary() {
        BoundaryFixture fixture = objectFixture(Callback.UPDATE, false);
        assertSame(ThrowingObject.class, fixture.object.getClass(),
                "ObjectManager must retain the concrete type for service injection");
        assertOwnerAbort(fixture, () -> fixture.manager.update(0, null, List.of(), 0, false));
    }

    @Test
    void concreteStandaloneObjectRenderRunsInsideOwnerBoundary() {
        BoundaryFixture fixture = objectFixture(Callback.RENDER, false);
        assertOwnerAbort(fixture, fixture.manager::drawLowPriority);
    }

    @Test
    void concreteStandaloneObjectUnloadRunsInsideOwnerBoundary() {
        BoundaryFixture fixture = objectFixture(Callback.UNLOAD, false);
        fixture.object.setDestroyed(true);
        assertOwnerAbort(fixture, () -> fixture.manager.update(0, null, List.of(), 0, false));
    }

    @Test
    void concreteStandaloneObjectTouchListenerRunsInsideOwnerBoundary() {
        BoundaryFixture fixture = objectFixture(Callback.TOUCH, true);
        com.openggf.game.PlayableEntity player = mock(com.openggf.game.PlayableEntity.class);
        assertOwnerAbort(fixture, () -> fixture.manager.runTouchResponsesForPlayer(player, 1));
    }

    @Test
    void stockObjectCallbacksStayOnTheDirectPath() {
        ObjectRegistry registry = mock(ObjectRegistry.class);
        when(registry.objectSlotLayout()).thenReturn(ObjectSlotLayout.SONIC_1);
        ObjectManager manager = new ObjectManager(List.of(), registry, 0, null, null, null,
                mock(com.openggf.camera.Camera.class), new StubObjectServices());
        ObjectSpawn stock = new ObjectSpawn(0, 0, 1, 0, 0, false, 0);
        manager.createDynamicObject(() -> new ThrowingObject(stock, Callback.UPDATE));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> manager.update(0, null, List.of(), 0, false));
        assertEquals("update", failure.getMessage());
    }

    @Test
    void callbackOwnershipNeverConsultsCreatorGetSpawn() {
        ModRuntimeFindingStore findings = new ModRuntimeFindingStore();
        AtomicReference<Set<String>> disabled = new AtomicReference<>();
        ModFaultBoundary boundary = new ModFaultBoundary(Map.of(), findings,
                owners -> new ModStateSaveResult.Saved(), disabled::set);
        GameModule delegate = mock(GameModule.class);
        ObjectRegistry registry = mock(ObjectRegistry.class);
        ObjectSpawn spawn = ownedSpawn();
        HostileSpawnObject object = new HostileSpawnObject(spawn);
        when(delegate.createObjectRegistry()).thenReturn(registry);
        when(registry.objectSlotLayout()).thenReturn(ObjectSlotLayout.SONIC_1);
        when(registry.create(spawn)).thenReturn(object);
        GameModule wrapped = OwnerAwareStandaloneModule.wrap("owner", delegate, boundary, Map.of());
        com.openggf.camera.Camera camera = mock(com.openggf.camera.Camera.class);
        when(camera.getWidth()).thenReturn((short) 320);
        when(camera.getHeight()).thenReturn((short) 224);
        ObjectManager manager = new ObjectManager(List.of(spawn), wrapped.createObjectRegistry(),
                0, null, null, null, camera, new StubObjectServices());
        manager.reset(0);

        ModFaultBoundary.CallbackAborted aborted = assertThrows(
                ModFaultBoundary.CallbackAborted.class,
                () -> manager.update(0, null, List.of(), 0, false));
        assertEquals("update", aborted.getCause().getMessage());
        assertEquals(Set.of("owner"), disabled.get());
        assertEquals("MOD_CALLBACK_FAILED", findings.findingsFor("owner").getFirst().code());
    }

    @Test
    void hostileGetSpawnCallbackIsOwnerBounded() {
        BoundaryFixture fixture = objectFixture(Callback.GET_SPAWN, false);
        assertOwnerAbort(fixture, () -> fixture.manager.update(0, null, List.of(), 0, false));
    }

    @Test
    void rewindCaptureAndRestoreCallbacksRemainOwnerBounded() {
        BoundaryFixture captureFixture = objectFixture(Callback.CAPTURE_REWIND, false);
        assertOwnerAbort(captureFixture, captureFixture.manager.rewindSnapshottable()::capture);

        BoundaryFixture restoreFixture = objectFixture(Callback.NONE, false);
        var snapshot = restoreFixture.manager.rewindSnapshottable().capture();
        restoreFixture.object.callback = Callback.RESTORE_REWIND;
        assertOwnerAbort(restoreFixture,
                () -> restoreFixture.manager.rewindSnapshottable().restore(snapshot));
    }

    @Test
    void dynamicallySpawnedChildInheritsCreatorOwnershipWithoutSpawnMetadata() {
        ModRuntimeFindingStore findings = new ModRuntimeFindingStore();
        AtomicReference<Set<String>> disabled = new AtomicReference<>();
        ModFaultBoundary boundary = new ModFaultBoundary(Map.of(), findings,
                owners -> new ModStateSaveResult.Saved(), disabled::set);
        GameModule delegate = mock(GameModule.class);
        ObjectRegistry registry = mock(ObjectRegistry.class);
        when(delegate.createObjectRegistry()).thenReturn(registry);
        when(registry.objectSlotLayout()).thenReturn(ObjectSlotLayout.SONIC_1);
        when(registry.objectWindowingStrategy()).thenReturn(ObjectWindowingStrategy.LEGACY);
        AtomicReference<ObjectManager> managerRef = new AtomicReference<>();
        ObjectSpawn spawn = ownedSpawn();
        SpawningParent parent = new SpawningParent(spawn, managerRef);
        when(registry.create(spawn)).thenReturn(parent);
        GameModule wrapped = OwnerAwareStandaloneModule.wrap("owner", delegate, boundary, Map.of());
        com.openggf.camera.Camera camera = mock(com.openggf.camera.Camera.class);
        when(camera.getWidth()).thenReturn((short) 320);
        when(camera.getHeight()).thenReturn((short) 224);
        ObjectManager manager = new ObjectManager(List.of(spawn), wrapped.createObjectRegistry(),
                0, null, null, null, camera, new StubObjectServices());
        managerRef.set(manager);
        manager.reset(0);
        manager.update(0, null, List.of(), 0, false);

        ModFaultBoundary.CallbackAborted aborted = assertThrows(ModFaultBoundary.CallbackAborted.class,
                () -> manager.update(1, null, List.of(), 0, false));
        assertEquals("child", aborted.getCause().getMessage());
        assertEquals(Set.of("owner"), disabled.get());
        assertEquals("MOD_CALLBACK_FAILED", findings.findingsFor("owner").getFirst().code());
    }

    @Test
    void placementSetupCallbacksAreOwnerBounded() {
        BoundaryFixture setter = objectFixture(Callback.SET_SERVICES, false, false);
        assertOwnerAbort(setter, () -> setter.manager.reset(0));
        BoundaryFixture getter = objectFixture(Callback.GET_SLOT, false, false);
        assertOwnerAbort(getter, () -> getter.manager.reset(0));
    }

    @Test
    void dynamicSetupCallbackInheritsOwnerBeforeEngineSettersRun() {
        BoundaryFixture fixture = objectFixture(Callback.NONE, false);
        fixture.object.callback = Callback.SPAWN_HOSTILE_DYNAMIC;
        assertOwnerAbort(fixture, () -> fixture.manager.update(0, null, List.of(), 0, false));
    }

    @Test
    void rewindRecreationSetupGetterAndSetterRemainOwnerBounded() {
        BoundaryFixture setter = objectFixture(Callback.NONE, false);
        var setterSnapshot = setter.manager.rewindSnapshottable().capture();
        setter.manager.setRewindInPlaceRestoreEnabledForTest(false);
        setter.object.callback = Callback.SET_SERVICES;
        assertOwnerAbort(setter, () -> setter.manager.rewindSnapshottable().restore(setterSnapshot));

        BoundaryFixture getter = objectFixture(Callback.NONE, false);
        var getterSnapshot = getter.manager.rewindSnapshottable().capture();
        getter.object.callback = Callback.GET_SLOT;
        assertOwnerAbort(getter, () -> getter.manager.rewindSnapshottable().restore(getterSnapshot));
    }

    @Test
    void providerCallbackDynamicSpawnInheritsScopedOwner() {
        ModRuntimeFindingStore findings = new ModRuntimeFindingStore();
        AtomicReference<Set<String>> disabled = new AtomicReference<>();
        ModFaultBoundary boundary = new ModFaultBoundary(Map.of(), findings,
                owners -> new ModStateSaveResult.Saved(), disabled::set);
        GameModule delegate = mock(GameModule.class);
        ObjectRegistry registry = mock(ObjectRegistry.class);
        when(delegate.createObjectRegistry()).thenReturn(registry);
        when(registry.objectSlotLayout()).thenReturn(ObjectSlotLayout.SONIC_1);
        when(registry.objectWindowingStrategy()).thenReturn(ObjectWindowingStrategy.LEGACY);
        AtomicReference<ObjectManager> managerRef = new AtomicReference<>();
        com.openggf.game.LevelEventProvider provider = new com.openggf.game.LevelEventProvider() {
            @Override public void initLevel(int zone, int act) { }
            @Override public void update() {
                managerRef.get().createDynamicObject(HostileUpdateChild::new);
            }
        };
        when(delegate.getLevelEventProvider()).thenReturn(provider);
        GameModule wrapped = OwnerAwareStandaloneModule.wrap("owner", delegate, boundary, Map.of());
        ObjectManager manager = new ObjectManager(List.of(), wrapped.createObjectRegistry(),
                0, null, null, null, mock(com.openggf.camera.Camera.class), new StubObjectServices());
        managerRef.set(manager);

        wrapped.getLevelEventProvider().update();
        ModFaultBoundary.CallbackAborted aborted = assertThrows(ModFaultBoundary.CallbackAborted.class,
                () -> manager.update(0, null, List.of(), 0, false));
        assertEquals("provider-child", aborted.getCause().getMessage());
        assertEquals(Set.of("owner"), disabled.get());
        assertEquals("MOD_CALLBACK_FAILED", findings.findingsFor("owner").getFirst().code());
    }

    @Test
    void ownerCallbackScopeRestoresNestedOwnersAndClearsAfterException() {
        ModFaultBoundary boundary = new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                owners -> new ModStateSaveResult.Saved(), owners -> { });
        boundary.call("outer", () -> {
            assertEquals("outer", OwnerCallbackScope.current());
            boundary.call("inner", () -> {
                assertEquals("inner", OwnerCallbackScope.current());
                return null;
            });
            assertEquals("outer", OwnerCallbackScope.current());
            return null;
        });
        assertNull(OwnerCallbackScope.current());
        assertThrows(ModFaultBoundary.CallbackAborted.class,
                () -> boundary.call("outer", () -> boundary.call("inner", () -> {
                    throw new IllegalStateException("nested");
                })));
        assertNull(OwnerCallbackScope.current());
    }
    @Test
    void moduleProviderAndGameCallbacksRunInsideOwnerBoundary() throws Exception {
        ModRuntimeFindingStore findings = new ModRuntimeFindingStore();
        ModFaultBoundary boundary = new ModFaultBoundary(Map.of(), findings,
                owners -> new ModStateSaveResult.Saved(), owners -> { });
        GameModule delegate = mock(GameModule.class);
        when(delegate.getIdentifier()).thenReturn("owner");
        when(delegate.getGameCode()).thenReturn("owner");
        when(delegate.getGameId()).thenReturn(com.openggf.game.GameId.STANDALONE);
        ZoneRegistry zones = mock(ZoneRegistry.class);
        when(delegate.getZoneRegistry()).thenReturn(zones);
        when(zones.getZoneName(0)).thenThrow(new IllegalStateException("provider"));
        com.openggf.data.Game game = mock(com.openggf.data.Game.class);
        GameDataSource source = new GameDataSource() {
            @Override public Optional<com.openggf.data.Rom> rom() { return Optional.empty(); }
            @Override public java.io.InputStream openAsset(String path) {
                return java.io.InputStream.nullInputStream();
            }
            @Override public String identity() { return "mod:owner:test"; }
        };
        when(delegate.createGame(source)).thenReturn(game);
        when(game.getMusicId(0)).thenThrow(new java.io.IOException("game"));

        GameModule wrapped = OwnerAwareStandaloneModule.wrap("owner", delegate, boundary, Map.of());

        assertThrows(ModFaultBoundary.CallbackAborted.class,
                () -> wrapped.getZoneRegistry().getZoneName(0));
        assertThrows(ModFaultBoundary.CallbackAborted.class,
                () -> wrapped.createGame(source).getMusicId(0));
        assertEquals("MOD_CALLBACK_FAILED", findings.findingsFor("owner").getFirst().code());
    }

    @Test
    void directModuleFailureAbortsOwner() {
        ModFaultBoundary boundary = new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                owners -> new ModStateSaveResult.Saved(), owners -> { });
        GameModule delegate = mock(GameModule.class);
        when(delegate.getZoneRegistry()).thenThrow(new IllegalStateException("module"));
        GameModule wrapped = OwnerAwareStandaloneModule.wrap("owner", delegate, boundary, Map.of());

        assertThrows(ModFaultBoundary.CallbackAborted.class, wrapped::getZoneRegistry);
    }

    @Test
    void gameWrapperOverridesEveryAbstractGameCallback() {
        Class<?> wrapper = java.util.Arrays.stream(OwnerAwareStandaloneModule.class.getDeclaredClasses())
                .filter(type -> type.getSimpleName().equals("OwnerAwareStandaloneGame"))
                .findFirst().orElseThrow();

        java.util.Arrays.stream(com.openggf.data.Game.class.getDeclaredMethods())
                .filter(method -> Modifier.isAbstract(method.getModifiers()))
                .forEach(method -> assertDoesNotThrow(() ->
                        wrapper.getDeclaredMethod(method.getName(), method.getParameterTypes()),
                        () -> "Missing owner boundary override for " + method));
    }

    @Test
    void functionalFactoriesAndDynamicServicesRemainInsideBoundary() {
        ModFaultBoundary boundary = new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                owners -> new ModStateSaveResult.Saved(), owners -> { });
        GameModule delegate = mock(GameModule.class);
        when(delegate.getInvincibilityStarsFactory()).thenReturn(player -> {
            throw new IllegalStateException("factory");
        });
        DynamicService service = mock(DynamicService.class);
        when(delegate.getGameService(DynamicService.class)).thenReturn(service);
        doThrow(new IllegalStateException("service")).when(service).invoke();
        GameModule wrapped = OwnerAwareStandaloneModule.wrap("owner", delegate, boundary, Map.of());

        assertThrows(ModFaultBoundary.CallbackAborted.class,
                () -> wrapped.getInvincibilityStarsFactory().apply(null));
        assertThrows(ModFaultBoundary.CallbackAborted.class,
                () -> wrapped.getGameService(DynamicService.class).invoke());
    }

    @Test
    void standaloneLevelMusicMustBeNonNullNamespacedAndOwnedByTheModule() {
        assertEquals(com.openggf.game.MusicReference.namespaced("owner", "level"),
                wrappedMusic(com.openggf.game.MusicReference.namespaced("owner", "level"))
                        .getLevelMusicReference(0, 0));
        assertThrows(ModFaultBoundary.CallbackAborted.class,
                () -> wrappedMusic(com.openggf.game.MusicReference.stock(1))
                        .getLevelMusicReference(0, 0));
        assertThrows(ModFaultBoundary.CallbackAborted.class,
                () -> wrappedMusic(com.openggf.game.MusicReference.namespaced("other", "level"))
                        .getLevelMusicReference(0, 0));
        assertThrows(ModFaultBoundary.CallbackAborted.class,
                () -> wrappedMusic(null).getLevelMusicReference(0, 0));
    }

    private static GameModule wrappedMusic(com.openggf.game.MusicReference music) {
        ModFaultBoundary boundary = new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                owners -> new ModStateSaveResult.Saved(), owners -> { });
        GameModule delegate = mock(GameModule.class);
        when(delegate.getLevelMusicReference(0, 0)).thenReturn(music);
        return OwnerAwareStandaloneModule.wrap("owner", delegate, boundary, Map.of());
    }

    private static com.openggf.game.CharacterDefinition characterDefinition(
            com.openggf.game.CharacterKey key) {
        return new com.openggf.game.CharacterDefinition(key, key.persisted(),
                (code, x, y) -> null, null, com.openggf.game.PlayerCharacter.SONIC_ALONE,
                com.openggf.sprites.playable.SecondaryAbility.NONE, false, code -> null);
    }

    private interface DynamicService { void invoke(); }

    private static ObjectSpawn ownedSpawn() {
        return new ObjectSpawn(0, 0, 0, 0, 0, false, 0, -1,
                "owner", "owner:throwing");
    }

    private static BoundaryFixture objectFixture(Callback callback, boolean touch) {
        return objectFixture(callback, touch, true);
    }

    private static BoundaryFixture objectFixture(Callback callback, boolean touch, boolean reset) {
        ModRuntimeFindingStore findings = new ModRuntimeFindingStore();
        AtomicReference<Set<String>> disabled = new AtomicReference<>();
        ModFaultBoundary boundary = new ModFaultBoundary(Map.of(), findings,
                owners -> new ModStateSaveResult.Saved(), disabled::set);
        GameModule delegate = mock(GameModule.class);
        ObjectRegistry registry = mock(ObjectRegistry.class);
        when(delegate.createObjectRegistry()).thenReturn(registry);
        when(registry.objectSlotLayout()).thenReturn(ObjectSlotLayout.SONIC_1);
        GameModule wrapped = OwnerAwareStandaloneModule.wrap("owner", delegate, boundary, Map.of());
        com.openggf.debug.DebugOverlayManager debug = mock(com.openggf.debug.DebugOverlayManager.class);
        AtomicReference<ObjectManager> managerRef = new AtomicReference<>();
        StubObjectServices services = new StubObjectServices() {
            @Override public com.openggf.debug.DebugOverlayManager debugOverlay() { return debug; }
            @Override public ObjectManager objectManager() { return managerRef.get(); }
        };
        TouchResponseTable table = touch ? mock(TouchResponseTable.class) : null;
        com.openggf.graphics.GraphicsManager graphics = mock(com.openggf.graphics.GraphicsManager.class);
        ObjectSpawn spawn = ownedSpawn();
        ThrowingObject object = new ThrowingObject(spawn, callback);
        when(registry.create(spawn)).thenReturn(object);
        com.openggf.camera.Camera camera = mock(com.openggf.camera.Camera.class);
        when(camera.getWidth()).thenReturn((short) 320);
        when(camera.getHeight()).thenReturn((short) 224);
        ObjectManager manager = new ObjectManager(List.of(spawn), wrapped.createObjectRegistry(),
                0, null, table, graphics, camera, services);
        managerRef.set(manager);
        if (reset) manager.reset(0);
        return new BoundaryFixture(manager, object, findings, disabled);
    }

    private static void assertOwnerAbort(BoundaryFixture fixture, Runnable callback) {
        ModFaultBoundary.CallbackAborted aborted = assertThrows(
                ModFaultBoundary.CallbackAborted.class, callback::run);
        assertEquals("owner", aborted.owner());
        assertEquals(Set.of("owner"), aborted.disabledOwners());
        assertEquals(Set.of("owner"), fixture.disabled.get());
        assertEquals("MOD_CALLBACK_FAILED", fixture.findings.findingsFor("owner").getFirst().code());
    }

    private record BoundaryFixture(ObjectManager manager, ThrowingObject object,
                                   ModRuntimeFindingStore findings,
                                   AtomicReference<Set<String>> disabled) { }

    private enum Callback { NONE, UPDATE, RENDER, UNLOAD, TOUCH, GET_SPAWN, CAPTURE_REWIND,
        RESTORE_REWIND, SET_SERVICES, GET_SLOT, SPAWN_HOSTILE_DYNAMIC }

    private static class ThrowingObject extends AbstractObjectInstance
            implements TouchResponseProvider, TouchResponseListener {
        private Callback callback;
        private ThrowingObject(ObjectSpawn spawn, Callback callback) {
            super(spawn, "throwing");
            this.callback = callback;
        }
        @Override public void update(int frameCounter, com.openggf.game.PlayableEntity player) {
            if (callback == Callback.UPDATE) throw new IllegalStateException("update");
            if (callback == Callback.SPAWN_HOSTILE_DYNAMIC) {
                services().objectManager().createDynamicObject(HostileSetupChild::new);
            }
        }
        @Override public void appendRenderCommands(List<GLCommand> commands) {
            if (callback == Callback.RENDER) throw new IllegalStateException("render");
        }
        @Override public void onUnload() {
            if (callback == Callback.UNLOAD) throw new IllegalStateException("unload");
        }
        @Override public ObjectSpawn getSpawn() {
            if (callback == Callback.GET_SPAWN) throw new IllegalStateException("getSpawn");
            return super.getSpawn();
        }
        @Override public void setServices(ObjectServices services) {
            if (callback == Callback.SET_SERVICES) throw new IllegalStateException("setServices");
            super.setServices(services);
        }
        @Override public int getSlotIndex() {
            if (callback == Callback.GET_SLOT) throw new IllegalStateException("getSlotIndex");
            return super.getSlotIndex();
        }
        @Override public PerObjectRewindSnapshot captureRewindState() {
            if (callback == Callback.CAPTURE_REWIND) throw new IllegalStateException("captureRewindState");
            return super.captureRewindState();
        }
        @Override public void restoreRewindState(
                PerObjectRewindSnapshot snapshot) {
            if (callback == Callback.RESTORE_REWIND) throw new IllegalStateException("restoreRewindState");
            super.restoreRewindState(snapshot);
        }
        @Override public int getCollisionFlags() { return 0x41; }
        @Override public int getCollisionProperty() { return 0; }
        @Override public boolean requiresRenderFlagForTouch() { return false; }
        @Override public void onTouchResponse(com.openggf.game.PlayableEntity player,
                TouchResponseResult result, int frameCounter) {
            if (callback == Callback.TOUCH) throw new IllegalStateException("touch");
        }
    }

    private static final class HostileSpawnObject extends ThrowingObject {
        private HostileSpawnObject(ObjectSpawn spawn) { super(spawn, Callback.UPDATE); }
        @Override public void snapshotPreUpdatePosition() { }
        @Override public ObjectSpawn getSpawn() { throw new IllegalStateException("getSpawn"); }
    }

    private static final class SpawningParent extends AbstractObjectInstance {
        private final AtomicReference<ObjectManager> manager;
        private boolean spawned;
        private SpawningParent(ObjectSpawn spawn, AtomicReference<ObjectManager> manager) {
            super(spawn, "spawning-parent");
            this.manager = manager;
        }
        @Override public void update(int frameCounter, com.openggf.game.PlayableEntity player) {
            if (!spawned) {
                spawned = true;
                manager.get().createDynamicObject(() -> new NullSpawnChild());
            }
        }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }

    private static final class NullSpawnChild extends AbstractObjectInstance {
        private boolean armed;
        private NullSpawnChild() { super(null, "null-spawn-child"); }
        @Override public void update(int frameCounter, com.openggf.game.PlayableEntity player) {
            if (!armed) { armed = true; return; }
            throw new IllegalStateException("child");
        }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }

    private static final class HostileSetupChild extends AbstractObjectInstance {
        private HostileSetupChild() { super(null, "hostile-setup-child"); }
        @Override public void setServices(ObjectServices services) {
            throw new IllegalStateException("dynamic-setServices");
        }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }

    private static final class HostileUpdateChild extends AbstractObjectInstance {
        private HostileUpdateChild() { super(null, "hostile-update-child"); }
        @Override public void update(int frameCounter, com.openggf.game.PlayableEntity player) {
            throw new IllegalStateException("provider-child");
        }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }

    public static final class RewindableOwnedObject extends AbstractObjectInstance
            implements RewindRecreatable {
        public RewindableOwnedObject(ObjectSpawn spawn) { super(spawn, "rewindable-owned"); }
        @Override public AbstractObjectInstance recreateForRewind(RewindRecreateContext context) {
            return new RewindableOwnedObject(context.spawn());
        }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }
}
