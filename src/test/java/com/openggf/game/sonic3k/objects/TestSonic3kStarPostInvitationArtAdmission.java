package com.openggf.game.sonic3k.objects;

import com.openggf.game.BonusStageType;
import com.openggf.game.CheckpointState;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kObjectArtProvider;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.resources.S3kRuntimeArtCoordinator;
import com.openggf.game.timing.HardwareWorkSubmission;
import com.openggf.level.objects.ObjectArtKeys;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRegistry;
import com.openggf.level.objects.ObjectSlotLayout;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.GameplayCaptureSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.*;

/**
 * 2026-10-15 Second Strike: component fixtures in an actual original-ROM S3K
 * gameplay session. Real ObjectManager/provider/hardware queue and normal frame
 * service own admission/retirement. These fixture post contacts are not routes.
 * Only missing-render prerequisites use a delegate; all queue owners stay real.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestSonic3kStarPostInvitationArtAdmission {
    private static final int BLUE = Sonic3kConstants.ART_KOSM_STARPOST_STARS1_ADDR;
    private GameplayCaptureSession session;

    @BeforeEach void boot() throws Exception {
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null, 10832, 1402);
        session = new GameplayCaptureSession(settings);
        session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 0, 1, settings);
    }
    @AfterEach void close() { if (session != null) session.close(); }

    @Test void nativeTitleEnemyBatchStaysOwnedUntilOrdinaryAdmission() throws Exception {
        var pending = provider().capture().pendingKosModules();
        assertEquals(3, pending.size(), "actual native AIZ LoadEnemyArt batch prerequisite");
        var post = post(false);
        post.update(vint(), null);
        assertEquals("PENDING", state(post));
        assertEquals(pending, provider().capture().pendingKosModules());
        assertEquals(0, familyJobs());
        awaitAdmission(post);
        assertEquals(1, familyJobs());
        for (int i = 0; i < 10; i++) session.step(null);
        assertEquals(1, familyJobs(), "marker retries never duplicate its admitted family job");
    }

    @Test void fullFourParentFifoRetriesWithoutFlushingOrAddingAFifth() throws Exception {
        drainBootstrap(); fillFour();
        var ordinals = provider().capture().pendingKosOrdinals();
        var post = post(false); post.update(vint(), null);
        assertEquals("PENDING", state(post)); assertEquals(4, physicalModules());
        assertEquals(ordinals, provider().capture().pendingKosOrdinals()); assertEquals(4, familyJobs());
        awaitAdmission(post);
        assertEquals(5, familyJobs(), "four fixture jobs plus exactly one invitation job");
        for (int i = 0; i < 10; i++) session.step(null);
        assertEquals(5, familyJobs());
    }

    @Test void submittedMarkerIsNotReadyAndActivationReusesItsOneJobForFourChildren() throws Exception {
        drainBootstrap(); var post = post(true); post.update(vint(), null);
        assertEquals("SUBMITTED", state(post));
        var ordinals = provider().capture().pendingKosOrdinals(); assertEquals(1, ordinals.size());
        var handle = GameServices.hardwareTiming().pendingHandles().getFirst();
        assertFalse(GameServices.hardwareTiming().isReady(handle), "admission has not serviced or completed this job");
        post.update(vint(), session.player());
        assertEquals(4, GameServices.level().getObjectManager().activeObjectsOfType(Sonic3kStarPostBonusStarChild.class).size());
        assertEquals(ordinals, provider().capture().pendingKosOrdinals()); assertEquals(1, familyJobs());
        for (var child : GameServices.level().getObjectManager().activeObjectsOfType(Sonic3kStarPostBonusStarChild.class)) {
            assertEquals(0, value(child, "lifetime")); assertEquals("BLUE", value(child, "variant").toString());
        }
        post.update(vint(), session.player()); assertEquals(1, familyJobs());
    }

    @Test void missingRendererProviderAndInitialUnallocatedShellAreNotAdmission() throws Exception {
        drainBootstrap();
        var raw = Sonic3kStarPostObjectInstance.withInvitation(spawn(false), BonusStageType.GLOWING_SPHERE);
        raw.setServices(GameServices.level().getObjectManager().getObjectServices()); raw.update(vint(), null);
        assertEquals(-1, raw.getSlotIndex()); assertEquals("PENDING", state(raw)); assertEquals(0, familyJobs());
        for (boolean absentRenderer : new boolean[]{true, false}) {
            int priorJobs = familyJobs();
            var post = post(false); var real = GameServices.level().getObjectManager().getObjectServices();
            var substitute = mock(ObjectServices.class, delegatesTo(real));
            var render = mock(ObjectRenderManager.class);
            doReturn(absentRenderer ? null : render).when(substitute).renderManager();
            post.setServices(substitute); post.update(vint(), null);
            assertEquals("PENDING", state(post)); assertEquals(priorJobs, familyJobs());
            post.setServices(real); post.update(vint(), null); assertEquals("SUBMITTED", state(post));
            assertEquals(priorJobs + 1, familyJobs());
            GameServices.level().getObjectManager().removeDynamicObject(post);
            drainBootstrap();
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void alreadyVisitedOrUsedFreshPostCannotCreateMarkerJob(boolean used) throws Exception {
        drainBootstrap();
        if (used) checkpoint().markUsedForSpecialStage();
        else checkpoint().saveCheckpoint(120, 10912, 1402, false);
        var post = post(false); post.update(vint(), null);
        assertEquals("RETIRED", state(post)); assertEquals(0, familyJobs());
        if (!used) assertEquals(true, value(post, "activated"));
    }

    @Test void pendingDestructionAndLostAllocatedSlotCancelIntentOnly() throws Exception {
        drainBootstrap(); fillFour();
        var destroyed = post(false); destroyed.update(vint(), null); destroyed.setDestroyed(true);
        assertEquals("RETIRED", state(destroyed));
        var unloaded = post(false); unloaded.update(vint(), null);
        GameServices.level().getObjectManager().removeDynamicObject(unloaded);
        assertEquals("RETIRED", state(unloaded));
        var used = post(false); used.update(vint(), null); used.markUsedForSpecialStage();
        assertEquals("RETIRED", state(used)); assertEquals(4, familyJobs()); assertEquals(4, physicalModules());
    }

    @Test void submittedWorkOutlivesUsedAndUnloadUntilTheNativeProviderClaimsIt() throws Exception {
        drainBootstrap(); var post = post(false); post.update(vint(), null);
        var handle = GameServices.hardwareTiming().pendingHandles().getFirst();
        post.markUsedForSpecialStage(); GameServices.level().getObjectManager().removeDynamicObject(post);
        assertEquals("SUBMITTED", state(post)); assertTrue(GameServices.hardwareTiming().isPending(handle));
        assertTrue(provider().capture().pendingKosOrdinals().contains(handle.ordinal()));
        for (int i = 0; i < 300 && GameServices.hardwareTiming().isPending(handle); i++) session.step(null);
        assertFalse(GameServices.hardwareTiming().isPending(handle)); assertEquals(1, familyJobs());
        assertFalse(provider().capture().pendingKosOrdinals().contains(handle.ordinal()));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void destructiveRestoreKeepsPurePendingOrSubmittedHandlesAndReplaysForward(boolean submitted) throws Exception {
        drainBootstrap(); if (!submitted) fillFour();
        var post = post(false); post.update(vint(), null);
        assertEquals(submitted ? "SUBMITTED" : "PENDING", state(post));
        var manager = GameServices.level().getObjectManager(); manager.setRewindInPlaceRestoreEnabledForTest(false);
        var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
        var snapshot = registry.capture();
        var handles = GameServices.hardwareTiming().pendingHandles(); var art = provider().capture();
        var direct = S3kRuntimeArtCoordinator.current().directQueue().capture(); int jobs = familyJobs();
        var expected = forward(60);
        manager.removeDynamicObject(post);
        registry.restore(snapshot);
        var restored = manager.activeObjectsOfType(Sonic3kStarPostObjectInstance.class).stream()
                .filter(p -> p.getCheckpointIndex() == 120).findFirst().orElseThrow();
        assertNotSame(post, restored); assertEquals(submitted ? "SUBMITTED" : "PENDING", state(restored));
        assertEquals("BLUE", value(restored, "invitationVariant").toString());
        assertEquals(handles, GameServices.hardwareTiming().pendingHandles(), "restore cannot enqueue, service or claim");
        assertEquals(art, provider().capture()); assertEquals(direct, S3kRuntimeArtCoordinator.current().directQueue().capture());
        assertEquals(jobs, familyJobs()); assertEquals(expected, forward(60));
    }

    @Test void pendingContactReusesOneAdmissionAcrossDestructiveChildGraphReplay() throws Exception {
        drainBootstrap(); fillFour();
        var post = post(true); post.update(vint(), session.player());
        assertEquals(true, value(post, "activated")); assertEquals("PENDING", state(post));
        assertEquals(4, children().size()); assertEquals(4, physicalModules()); assertEquals(4, familyJobs());
        var manager = GameServices.level().getObjectManager(); manager.setRewindInPlaceRestoreEnabledForTest(false);
        var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
        var snapshot = registry.capture();
        var handles = GameServices.hardwareTiming().pendingHandles(); var art = provider().capture();
        var beforeGraph = invitationGraph(); var originalChildren = List.copyOf(children());
        var expected = forwardGraph(60);
        assertEquals("SUBMITTED", state(post)); assertEquals(5, familyJobs());
        assertEquals(4, children().size());
        for (var child : children()) assertEquals(60, value(child, "lifetime"));
        manager.removeDynamicObject(post);
        for (var child : List.copyOf(children())) manager.removeDynamicObject(child);
        registry.restore(snapshot);
        var restored = fixturePost();
        assertNotSame(post, restored); assertEquals(true, value(restored, "activated"));
        assertEquals("PENDING", state(restored)); assertEquals(beforeGraph, invitationGraph());
        assertEquals(handles, GameServices.hardwareTiming().pendingHandles(), "restore cannot enqueue/prepare/claim");
        assertEquals(art, provider().capture()); assertEquals(4, familyJobs());
        for (var child : children()) {
            assertFalse(originalChildren.contains(child));
            assertSame(restored, value(child, "parentStarPost"));
        }
        assertEquals(expected, forwardGraph(60)); assertEquals(5, familyJobs());
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 2})
    void markerUsesOnlySubmittedReadyExactFamily(int selectedReadiness) throws Exception {
        drainBootstrap(); var post = post(false); post.update(vint(), null);
        assertEquals("SUBMITTED", state(post));
        var real = GameServices.level().getObjectManager().getObjectServices();
        var substitute = mock(ObjectServices.class, delegatesTo(real));
        var render = mock(ObjectRenderManager.class); var art = mock(Sonic3kObjectArtProvider.class);
        var body = mock(PatternSpriteRenderer.class); var other = mock(PatternSpriteRenderer.class);
        var selected = mock(PatternSpriteRenderer.class);
        when(body.isReady()).thenReturn(true); when(other.isReady()).thenReturn(true);
        when(selected.isReady()).thenReturn(selectedReadiness == 2);
        when(render.getCheckpointRenderer()).thenReturn(body); when(render.getArtProvider()).thenReturn(art);
        // Another family is ready and the shared helper would return it as a
        // fallback. Missing/unready BLUE must never draw those misleading stars.
        when(art.getRenderer(anyString())).thenReturn(other);
        when(art.getRenderer(ObjectArtKeys.CHECKPOINT_STAR_BLUE)).thenReturn(selectedReadiness == 0 ? null : selected);
        when(render.getCheckpointStarRenderer(anyString())).thenReturn(other);
        doReturn(render).when(substitute).renderManager(); post.setServices(substitute);
        post.appendRenderCommands(new ArrayList<>());
        verifyNoInteractions(other);
        if (selectedReadiness == 2) {
            verify(selected).drawFrameIndex(1, post.getCenterX() - 12, post.getCenterY() - 0x30, false, false);
            verify(selected).drawFrameIndex(1, post.getCenterX() + 12, post.getCenterY() - 0x30, false, false);
            verify(selected, times(2)).drawFrameIndex(anyInt(), anyInt(), anyInt(), anyBoolean(), anyBoolean());
        } else verify(selected, never()).drawFrameIndex(anyInt(), anyInt(), anyInt(), anyBoolean(), anyBoolean());
        verify(body).drawFrameIndex(anyInt(), eq(post.getCenterX()), eq(post.getCenterY()), eq(false), eq(false));
        assertEquals(1, familyJobs());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void nativeSpawnBackedUnloadRetiresOnlyPendingIntent(boolean submitted) throws Exception {
        drainBootstrap(); if (!submitted) fillFour();
        // Actual original native post476 placement, with its opt-in producer in
        // a real isolated placement manager sharing the session's ROM provider
        // and hardware ledger. Viewport arguments exercise native out-of-range
        // retirement; this component fixture is not player route evidence.
        var spawn = GameServices.level().getCurrentLevel().getObjects().stream()
                .filter(s -> s.layoutIndex() == 476).findFirst().orElseThrow();
        assertEquals(0x34, spawn.objectId()); assertEquals(5, spawn.subtype());
        var real = GameServices.level().getObjectManager().getObjectServices();
        var services = mock(ObjectServices.class, delegatesTo(real));
        var registry = new ObjectRegistry() {
            @Override public Sonic3kStarPostObjectInstance create(ObjectSpawn s) {
                assertEquals(spawn, s);
                return Sonic3kStarPostObjectInstance.withInvitation(s, BonusStageType.GLOWING_SPHERE);
            }
            @Override public void reportCoverage(List<ObjectSpawn> ignored) { }
            @Override public String getPrimaryName(int ignored) { return "StarPost"; }
            @Override public ObjectSlotLayout objectSlotLayout() { return ObjectSlotLayout.SONIC_3K; }
        };
        var manager = new ObjectManager(List.of(spawn), registry, 0, null, null,
                GameServices.graphics(), GameServices.camera(), services);
        doReturn(manager).when(services).objectManager();
        manager.enableExecThenLoadPlacement(); manager.enablePermanentDestroyLatch();
        int cameraX = GameServices.camera().getX(); manager.reset(cameraX);
        // Normal exec-then-load creates on one pass and executes on the next.
        manager.update(cameraX, null, List.of(), vint()); manager.update(cameraX, null, List.of(), vint());
        var post = manager.activeObjectsOfType(Sonic3kStarPostObjectInstance.class).getFirst();
        int slot = post.getSlotIndex(); assertTrue(slot >= 0);
        assertEquals(submitted ? "SUBMITTED" : "PENDING", state(post));
        var handles = GameServices.hardwareTiming().pendingHandles(); var artState = provider().capture();
        manager.update(spawn.x() + 0x800, null, List.of(), vint());
        assertTrue(manager.activeObjectsOfType(Sonic3kStarPostObjectInstance.class).isEmpty());
        assertEquals(slot, post.getSlotIndex(), "spawn-backed unload does not clear the old instance's slot field");
        assertEquals(submitted ? "SUBMITTED" : "RETIRED", state(post));
        assertEquals(handles, GameServices.hardwareTiming().pendingHandles()); assertEquals(artState, provider().capture());
        assertEquals(submitted ? 1 : 4, familyJobs());
    }

    @Test void providerRejectsUnsupportedFamilyBeforeAnySubmission() throws Exception {
        var before = provider().capture(); var handles = GameServices.hardwareTiming().pendingHandles();
        assertThrows(IllegalArgumentException.class, () -> provider().tryQueueStarPostInvitationArt(0));
        assertEquals(before, provider().capture()); assertEquals(handles, GameServices.hardwareTiming().pendingHandles());
    }

    private List<Sonic3kStarPostBonusStarChild> children() {
        return GameServices.level().getObjectManager().activeObjectsOfType(Sonic3kStarPostBonusStarChild.class).stream()
                .sorted(java.util.Comparator.comparingInt(Sonic3kStarPostBonusStarChild::getSlotIndex)).toList();
    }
    private Sonic3kStarPostObjectInstance fixturePost() {
        return GameServices.level().getObjectManager().activeObjectsOfType(Sonic3kStarPostObjectInstance.class).stream()
                .filter(p -> p.getCheckpointIndex() == 120).findFirst().orElseThrow();
    }
    private List<String> invitationGraph() throws Exception {
        var post = fixturePost(); var rows = new ArrayList<String>();
        rows.add(state(post) + ":" + value(post, "activated") + ":" + value(post, "invitationVariant"));
        for (var child : children()) {
            var row = new StringBuilder(child.getSlotIndex() + ":" + child.getSpawn());
            for (String field : List.of("centerX", "centerY", "angle", "lifetime", "animFrame", "currentX", "currentY", "mappingFrame", "collisionEnabled", "variant"))
                row.append(':').append(value(child, field));
            row.append(":parent=").append(value(child, "parentStarPost") == post).append(":destroyed=").append(child.isDestroyed());
            rows.add(row.toString());
        }
        return rows;
    }
    private List<String> forwardGraph(int count) throws Exception {
        var rows = new ArrayList<String>();
        for (int i = 0; i < count; i++) {
            session.step(null);
            rows.add(invitationGraph() + ":" + physicalModules() + ":" + provider().capture().pendingKosOrdinals()
                    + ":" + GameServices.hardwareTiming().pendingHandles() + ":" + familyJobs());
        }
        return rows;
    }
    private List<String> forward(int count) throws Exception {
        var rows = new ArrayList<String>();
        for (int i = 0; i < count; i++) {
            session.step(null);
            var post = GameServices.level().getObjectManager().activeObjectsOfType(Sonic3kStarPostObjectInstance.class).stream()
                    .filter(p -> p.getCheckpointIndex() == 120).findFirst().orElseThrow();
            rows.add(state(post) + ":" + physicalModules() + ":" + provider().capture().pendingKosOrdinals()
                    + ":" + GameServices.hardwareTiming().pendingHandles() + ":" + familyJobs());
        }
        return rows;
    }
    private void awaitAdmission(Sonic3kStarPostObjectInstance post) throws Exception {
        for (int i = 0; i < 300 && state(post).equals("PENDING"); i++) session.step(null);
        assertEquals("SUBMITTED", state(post));
    }
    private void drainBootstrap() throws Exception {
        for (int i = 0; i < 300; i++) {
            if (provider().capture().pendingKosModules().isEmpty() && provider().capture().pendingKosOrdinals().isEmpty()
                    && !S3kRuntimeArtCoordinator.current().moduleQueue().hasPendingPhysicalModules()) return;
            session.step(null);
        }
        fail("real native art producer must drain through ordinary frames");
    }
    private void fillFour() {
        for (int i = 0; i < 4; i++) provider().queueStarPostBonusArt(BLUE);
        assertEquals(4, physicalModules());
    }
    private Sonic3kStarPostObjectInstance post(boolean contact) {
        return GameServices.level().getObjectManager().createDynamicObject(() ->
                Sonic3kStarPostObjectInstance.withInvitation(spawn(contact), BonusStageType.GLOWING_SPHERE));
    }
    private ObjectSpawn spawn(boolean contact) {
        return new ObjectSpawn(session.player().getCentreX() + (contact ? 0 : 80), session.player().getCentreY(), 0x34, 120, 0, false, 9999);
    }
    private int vint() { return GameServices.level().getObjectManager().getVblaCounter(); }
    private static Sonic3kObjectArtProvider provider() {
        return (Sonic3kObjectArtProvider) GameServices.level().getObjectManager().getObjectServices().renderManager().getArtProvider();
    }
    private static CheckpointState checkpoint() { return (CheckpointState) GameServices.level().getCheckpointState(); }
    private static String state(Object post) throws Exception { return value(post, "invitationArtState").toString(); }
    private static int physicalModules() {
        return (int) GameServices.hardwareTiming().pendingHandles().stream()
                .filter(h -> h.kind() == com.openggf.game.timing.HardwareWorkKind.KOS_MODULE_QUEUE)
                .filter(h -> !GameServices.hardwareTiming().coordinatorPreparation(h).isPrepared()).count();
    }
    private static int familyJobs() throws Exception {
        int result = 0;
        for (Object job : (List<?>) value(GameServices.hardwareTiming(), "jobs")) {
            if (((HardwareWorkSubmission) value(job, "submission")).romSourceAddress() == BLUE) result++;
        }
        return result;
    }
    private static Object value(Object object, String name) throws Exception {
        for (var type = object.getClass(); type != null; type = type.getSuperclass()) {
            try { var field = type.getDeclaredField(name); field.setAccessible(true); return field.get(object); }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
}
