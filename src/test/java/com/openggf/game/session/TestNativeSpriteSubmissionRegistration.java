package com.openggf.game.session;

import com.openggf.camera.Camera;
import com.openggf.game.GameRng;
import com.openggf.game.GameStateManager;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.rewind.snapshot.LevelSnapshot;
import com.openggf.game.rewind.snapshot.LevelTilemapSnapshot;
import com.openggf.game.solid.DefaultSolidExecutionRegistry;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.graphics.FadeManager;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.LevelManager;
import com.openggf.level.LevelTilemapManager;
import com.openggf.level.objects.NativeSpriteSubmissionAccess;
import com.openggf.level.objects.NativeSstSlotAccess;
import com.openggf.level.objects.NativeSpriteSubmissionAccess.Admission;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.tests.FullReset;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.timer.TimerManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Actual session lifecycle owns the queue adapter alongside its current object graph. */
@ExtendWith(SingletonResetExtension.class)
@FullReset
class TestNativeSpriteSubmissionRegistration {
    private static final String QUEUE_KEY = NativeSpriteSubmissionAccess.REWIND_KEY;

    @Test
    void levelRegistrationOwnsIndependentWordResidueAfterObjectReconstruction() {
        GameplayModeContext context = attachedContext();
        ObjectManager objects = objectManager();
        NativeSstSlotAccess.clearWord2C(objects, 40);
        NativeSstSlotAccess.addWord2C(objects, 40, -1);
        context.registerLevelAdapters(levelWith(objects));
        RewindRegistry registry = context.getRewindRegistry();
        CompositeSnapshot frame = registry.capture();
        List<String> keys = List.copyOf(frame.entries().keySet());
        assertTrue(keys.indexOf(NativeSstSlotAccess.WORD_2C_REWIND_KEY) > keys.indexOf("object-manager"));
        NativeSstSlotAccess.writeWord2C(objects, 40, 12);
        registry.restore(frame);
        assertEquals(0xFFFF, NativeSstSlotAccess.readWord2C(objects, 40));
        assertEquals(frame.get(NativeSstSlotAccess.WORD_2C_REWIND_KEY),
                registry.capture().get(NativeSstSlotAccess.WORD_2C_REWIND_KEY));
    }

    @Test
    void replacementAndNullRebindRetireThePreviousWordAdapter() {
        GameplayModeContext context = attachedContext();
        ObjectManager old = objectManager();
        NativeSstSlotAccess.writeWord2C(old, 40, 0xFFFF);
        context.registerLevelAdapters(levelWith(old));
        RewindRegistry registry = context.getRewindRegistry();
        CompositeSnapshot oldFrame = registry.capture();
        ObjectManager next = objectManager();
        context.rebindActTransitionManagerAdapters(next, null);
        NativeSstSlotAccess.writeWord2C(next, 40, 12);
        registry.restore(oldFrame);
        assertEquals(0xFFFF, NativeSstSlotAccess.readWord2C(next, 40));
        NativeSstSlotAccess.addWord2C(old, 40, -1);
        assertEquals(0xFFFE, NativeSstSlotAccess.readWord2C(old, 40));
        context.rebindActTransitionManagerAdapters(null, null);
        assertFalse(registry.capture().containsKey(NativeSstSlotAccess.WORD_2C_REWIND_KEY));
        registry.restore(oldFrame);
        assertEquals(0xFFFE, NativeSstSlotAccess.readWord2C(old, 40));
    }

    @Test
    void nullLevelAndContextTeardownUnregisterTheWordAdapter() {
        GameplayModeContext context = attachedContext();
        RewindRegistry registry = context.getRewindRegistry();
        context.registerLevelAdapters(levelWith(objectManager()));
        assertTrue(registry.capture().containsKey(NativeSstSlotAccess.WORD_2C_REWIND_KEY));
        context.registerLevelAdapters(levelWith(null));
        assertFalse(registry.capture().containsKey(NativeSstSlotAccess.WORD_2C_REWIND_KEY));
        context.registerLevelAdapters(levelWith(objectManager()));
        context.tearDownManagers();
        assertFalse(registry.capture().containsKey(NativeSstSlotAccess.WORD_2C_REWIND_KEY));
    }

    @BeforeEach
    void configureServicesAndGraphics() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        GraphicsManager.getInstance().initHeadless();
    }

    @AfterEach
    void resetGraphics() {
        GraphicsManager.getInstance().resetState();
    }

    @Test
    void levelRegistrationCapturesAndRestoresQueueAfterTheActualObjectGraph() {
        GameplayModeContext context = attachedContext();
        RewindRegistry registry = context.getRewindRegistry();
        assertFalse(registry.capture().containsKey(QUEUE_KEY), "There is no object owner before level registration");
        ObjectManager objects = objectManager();
        admit(objects, 127, 7);
        admit(objects, 16, 0);
        context.registerLevelAdapters(levelWith(objects));

        CompositeSnapshot captured = registry.capture();

        assertNotNull(captured.get(QUEUE_KEY));
        assertObjectGraphPrecedesQueue(captured);
        assertTrue(objects.getActiveObjects().isEmpty(), "Queue metadata retains raw slots without live objects");
        admit(objects, 40, 4);
        registry.restore(captured);
        assertEquals(List.of(new Admission(127, 7), new Admission(16, 0)),
                NativeSpriteSubmissionAccess.admissions(objects));
        assertEquals(captured.get(QUEUE_KEY), registry.capture().get(QUEUE_KEY),
                "The session restores queue metadata through its separately registered adapter");
    }

    @Test
    void repeatedLevelRegistrationReplacesTheQueueOwnerWithoutRetainingThePreviousManager() {
        GameplayModeContext context = attachedContext();
        RewindRegistry registry = context.getRewindRegistry();
        ObjectManager source = objectManager();
        admit(source, 32, 2);
        context.registerLevelAdapters(levelWith(source));
        CompositeSnapshot sourceFrame = registry.capture();

        ObjectManager replacement = objectManager();
        admit(replacement, 90, 6);
        LevelManager replacementLevel = levelWith(replacement);
        context.registerLevelAdapters(replacementLevel);
        context.registerLevelAdapters(replacementLevel);
        CompositeSnapshot replacementFrame = registry.capture();
        assertObjectGraphPrecedesQueue(replacementFrame);
        assertNotEquals(sourceFrame.get(QUEUE_KEY), replacementFrame.get(QUEUE_KEY),
                "Capture must read the replacement queue rather than the previous manager's metadata");
        assertEquals(1, replacementFrame.entries().keySet().stream().filter(QUEUE_KEY::equals).count());

        admit(source, 33, 3);
        admit(replacement, 91, 5);
        registry.restore(sourceFrame);

        assertEquals(List.of(new Admission(32, 2)), NativeSpriteSubmissionAccess.admissions(replacement),
                "Restoring an older act's metadata must target the currently registered manager");
        assertEquals(List.of(new Admission(32, 2), new Admission(33, 3)),
                NativeSpriteSubmissionAccess.admissions(source), "The replaced manager must receive no restore");
        assertEquals(sourceFrame.get(QUEUE_KEY), registry.capture().get(QUEUE_KEY));
    }

    @Test
    void registeringALevelWithoutObjectsRemovesTheOldQueueAdapterAndAllowsAFreshOwner() {
        GameplayModeContext context = attachedContext();
        RewindRegistry registry = context.getRewindRegistry();
        ObjectManager source = objectManager();
        admit(source, 32, 2);
        context.registerLevelAdapters(levelWith(source));
        assertTrue(registry.capture().containsKey(QUEUE_KEY));

        context.registerLevelAdapters(levelWith(null));

        CompositeSnapshot withoutObjects = registry.capture();
        assertFalse(withoutObjects.containsKey("object-manager"));
        assertFalse(withoutObjects.containsKey(QUEUE_KEY), "Optional owner removal must unregister its queue too");
        ObjectManager fresh = objectManager();
        context.registerLevelAdapters(levelWith(fresh));
        admit(fresh, 40, 4);
        CompositeSnapshot freshFrame = registry.capture();
        assertObjectGraphPrecedesQueue(freshFrame);
        admit(fresh, 41, 5);
        registry.restore(freshFrame);
        assertEquals(List.of(new Admission(40, 4)), NativeSpriteSubmissionAccess.admissions(fresh));
        assertEquals(List.of(new Admission(32, 2)), NativeSpriteSubmissionAccess.admissions(source));
    }

    @Test
    void actTransitionRebindTargetsTheReplacementQueueAndRestoresPriorFrameMetadata() {
        GameplayModeContext context = attachedContext();
        RewindRegistry registry = context.getRewindRegistry();
        ObjectManager source = objectManager();
        admit(source, 127, 7);
        admit(source, 16, 0);
        context.rebindActTransitionManagerAdapters(source, null);
        CompositeSnapshot sourceFrame = registry.capture();
        assertObjectGraphPrecedesQueue(sourceFrame);

        ObjectManager replacement = objectManager();
        admit(replacement, 90, 6);
        context.rebindActTransitionManagerAdapters(replacement, null);
        CompositeSnapshot replacementFrame = registry.capture();
        assertObjectGraphPrecedesQueue(replacementFrame);
        assertNotEquals(sourceFrame.get(QUEUE_KEY), replacementFrame.get(QUEUE_KEY),
                "Transition capture must follow the replacement manager");
        admit(source, 32, 2);
        admit(replacement, 91, 5);

        registry.restore(sourceFrame);

        assertEquals(List.of(new Admission(127, 7), new Admission(16, 0)),
                NativeSpriteSubmissionAccess.admissions(replacement));
        assertEquals(List.of(new Admission(127, 7), new Admission(16, 0), new Admission(32, 2)),
                NativeSpriteSubmissionAccess.admissions(source));
        assertEquals(sourceFrame.get(QUEUE_KEY), registry.capture().get(QUEUE_KEY));
    }

    @Test
    void actTransitionRebindToNullUnregistersBothTheObjectGraphAndItsQueue() {
        GameplayModeContext context = attachedContext();
        RewindRegistry registry = context.getRewindRegistry();
        ObjectManager source = objectManager();
        admit(source, 32, 2);
        context.rebindActTransitionManagerAdapters(source, null);
        CompositeSnapshot sourceFrame = registry.capture();
        admit(source, 40, 4);

        context.rebindActTransitionManagerAdapters(null, null);

        CompositeSnapshot withoutObjects = registry.capture();
        assertFalse(withoutObjects.containsKey("object-manager"));
        assertFalse(withoutObjects.containsKey(QUEUE_KEY));
        registry.restore(sourceFrame);
        assertEquals(List.of(new Admission(32, 2), new Admission(40, 4)),
                NativeSpriteSubmissionAccess.admissions(source),
                "A stale snapshot key must not resurrect an unregistered queue owner");
    }

    @Test
    void teardownUnregistersTheQueueEvenFromARetainedRegistryReference() {
        GameplayModeContext context = attachedContext();
        RewindRegistry retainedRegistry = context.getRewindRegistry();
        ObjectManager objects = objectManager();
        admit(objects, 32, 2);
        context.rebindActTransitionManagerAdapters(objects, null);
        assertTrue(retainedRegistry.capture().containsKey(QUEUE_KEY));

        context.tearDownManagers();

        assertNull(context.getRewindRegistry());
        assertFalse(retainedRegistry.capture().containsKey(QUEUE_KEY),
                "Clearing the context field alone must not leave the old registry holding its queue owner");
    }

    private static GameplayModeContext attachedContext() {
        GameplayModeContext context = new GameplayModeContext(new WorldSession(new Sonic2GameModule()));
        context.attachGameplayManagers(new Camera(), new TimerManager(), new GameStateManager(),
                new FadeManager(), new GameRng(GameRng.Flavour.S1_S2), new DefaultSolidExecutionRegistry());
        return context;
    }

    private static ObjectManager objectManager() {
        ObjectManager[] holder = new ObjectManager[1];
        Camera camera = new Camera();
        ObjectServices services = new StubObjectServices() {
            @Override public ObjectManager objectManager() { return holder[0]; }
            @Override public Camera camera() { return camera; }
        };
        ObjectManager manager = new ObjectManager(List.of(), null, 0, null, null,
                GraphicsManager.getInstance(), camera, services);
        holder[0] = manager;
        return manager;
    }

    private static LevelManager levelWith(ObjectManager objects) {
        LevelManager level = mock(LevelManager.class);
        when(level.getTilemapManager()).thenReturn(mock(LevelTilemapManager.class));
        when(level.getObjectManager()).thenReturn(objects);
        when(level.levelRewindSnapshottable()).thenReturn(new RewindSnapshottable<LevelSnapshot>() {
            @Override public String key() { return "level"; }
            @Override public LevelSnapshot capture() { return new LevelSnapshot(0, null, null, null, 0); }
            @Override public void restore(LevelSnapshot snapshot) { }
        });
        when(level.levelTilemapRewindSnapshottable()).thenReturn(new RewindSnapshottable<LevelTilemapSnapshot>() {
            @Override public String key() { return "level-tilemap"; }
            @Override public LevelTilemapSnapshot capture() { return LevelTilemapSnapshot.invalid(); }
            @Override public void restore(LevelTilemapSnapshot snapshot) { }
        });
        return level;
    }

    private static void admit(ObjectManager objects, int slot, int bucket) {
        assertTrue(NativeSpriteSubmissionAccess.submit(objects, slot, bucket));
    }

    private static void assertObjectGraphPrecedesQueue(CompositeSnapshot snapshot) {
        List<String> keys = List.copyOf(snapshot.entries().keySet());
        int objects = keys.indexOf("object-manager");
        int queue = keys.indexOf(QUEUE_KEY);
        assertTrue(objects >= 0, "The actual object graph adapter must be registered");
        assertTrue(queue > objects, "Raw queue slots restore after their current object graph owner");
    }
}
