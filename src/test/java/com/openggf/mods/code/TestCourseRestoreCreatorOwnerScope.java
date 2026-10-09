package com.openggf.mods.code;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.game.GameModule;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServiceBundle;
import com.openggf.game.GameServices;
import com.openggf.game.mode.CourseControl;
import com.openggf.game.mode.CourseControlProbeAccess;
import com.openggf.game.mode.GameplayFrameController;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.io.ModAssetRoot;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A course checkpoint restored from inside a creator callback (a frame controller's
 * {@code beforeTick}, where the Typing of the Hedgehog course restores its stands) rebuilds
 * engine objects as engine objects. The Hydrocity fan's sliding platform is a spawnless dynamic
 * child its parent re-spawns during reconstruction; when the creator owner leaked into that
 * rebuild the child came back owned by the mod, adoption rejected it, and the restore dropped it
 * (later failing to resolve its captured identity). Regression for the rails lane's HCZ1 stand
 * restore, 2026-10-10.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestCourseRestoreCreatorOwnerScope {
    static final String OWNER = "restore-owner";
    static final String PLATFORM = "HCZCGZFanObjectInstance$FanPlatformChild";
    private SharedLevel bootstrap;

    @AfterEach void dispose() {
        if (bootstrap != null) bootstrap.dispose();
        bootstrap = null;
        SonicConfigurationService.getInstance().clearSessionOverrides();
    }

    static final class Neutral implements GameplayFrameController,
            com.openggf.game.rewind.RewindSnapshottable<Integer> {
        @Override public boolean beforeTick(CourseControl course, LogicalInputSnapshot input) { return true; }
        @Override public void afterTick(CourseControl course, boolean advanced) { }
        @Override public String key() { return "neutral-course"; }
        @Override public Integer capture() { return 0; }
        @Override public void restore(Integer value) { }
    }

    /** HCZ1 with a mod whose registered object makes the object registry creator-aware. */
    private HeadlessTestFixture launch(int x, int y) throws Exception {
        bootstrap = SharedLevel.load(SonicGame.SONIC_3K, Sonic3kZoneIds.ZONE_HCZ, 0);
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        var context = new ModContext(OWNER, "s3k", ModAssetRoot.forTests(OWNER));
        context.registerObject("marker", (spawn, registry) -> null);
        context.registerServiceBundle("course", () -> GameServiceBundle.builder()
                .frameController("course", new Neutral()).build());
        var boundary = new ModFaultBoundary(Map.of(), new com.openggf.mods.ModRuntimeFindingStore(),
                owners -> new com.openggf.mods.ModStateSaveResult.Saved(), owners -> { });
        GameModule effective = new ModBackedGamePatch(context.freeze(), boundary).apply(GameServices.module(), null);
        SessionManager.clear();
        GameModuleRegistry.setCurrent(effective);
        TestEnvironment.activeGameplayMode();
        var builder = HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_HCZ, 0);
        if (x >= 0) builder.startPosition((short) x, (short) y).startPositionIsCentre();
        return builder.build();
    }

    private static List<ObjectInstance> platforms() {
        var query = GameServices.level().getObjectManager().objectQuery();
        List<ObjectInstance> out = new ArrayList<>();
        for (ObjectInstance o : query.activeObjectsOfType(ObjectInstance.class)) {
            if (o.getClass().getName().endsWith(PLATFORM)) out.add(o);
        }
        return out;
    }

    private static ObjectRefId identity(ObjectInstance o) {
        return GameServices.level().getObjectManager().objectQuery().identityOf(o).orElse(null);
    }

    @Test void restoreInsideACreatorCallbackRebuildsTheFanPlatformAsAnEngineObject() throws Exception {
        restoreBesideTheFan(true);
    }

    /** Control: the same restore with no creator callback in scope always rebuilt the platform. */
    @Test void restoreOutsideACreatorCallbackRebuildsTheFanPlatform() throws Exception {
        restoreBesideTheFan(false);
    }

    private void restoreBesideTheFan(boolean insideCreatorCallback) throws Exception {
        // Find a fan whose subtype spawns the sliding platform (bit 7), then start beside it.
        launch(-1, -1);
        ObjectSpawn fan = GameServices.level().getObjectManager().getAllSpawns().stream()
                .filter(s -> s.objectId() == Sonic3kObjectIds.HCZ_CGZ_FAN && (s.subtype() & 0x80) != 0)
                .findFirst().orElseThrow(() -> new AssertionError("HCZ1 has a platform fan"));
        dispose();
        var fixture = launch(fan.x() - 0x40, fan.y() - 0x20);
        for (int i = 0; i < 120 && platforms().isEmpty(); i++) fixture.stepFrame(false, false, false, false, false);
        List<ObjectInstance> before = platforms();
        assertFalse(before.isEmpty(), "the fan's platform child is live beside its fan");
        ObjectRefId captured = identity(before.getFirst());
        assertNotNull(captured, "the platform has a rewind identity at capture");
        assertEquals(-1, captured.spawnId(), "the platform is a spawnless dynamic child");

        CourseControl course = CourseControlProbeAccess.open(SessionManager.getCurrentGameplayMode());
        var checkpoint = course.capture();
        for (int i = 0; i < 30; i++) fixture.stepFrame(false, false, false, false, false);

        // The course restores its stands from inside the mod's frame-controller callback.
        if (insideCreatorCallback) {
            OwnerCallbackScope.call(OWNER, () -> {
                course.restore(checkpoint);
                assertEquals(OWNER, OwnerCallbackScope.current(), "the creator's own frame is reinstated after the restore");
                return null;
            });
            assertNull(OwnerCallbackScope.current(), "no owner frame outlives the callback");
        } else {
            course.restore(checkpoint);
        }

        List<ObjectInstance> after = platforms();
        assertTrue(after.stream().anyMatch(p -> captured.equals(identity(p))),
                "the restore rebuilds the platform under its captured identity " + captured
                        + "; live platforms " + after.stream().map(TestCourseRestoreCreatorOwnerScope::identity).toList());
        for (ObjectInstance p : after) {
            assertNull(callbackOwner(p), "an engine object rebuilt by a restore has no creator owner");
        }
    }

    /** The object manager's callback owner for {@code object} (package-private engine state). */
    private static String callbackOwner(ObjectInstance object) throws Exception {
        var manager = GameServices.level().getObjectManager();
        var routerMethod = manager.getClass().getDeclaredMethod("objectCallbacks");
        routerMethod.setAccessible(true);
        Object router = routerMethod.invoke(manager);
        var owner = router.getClass().getDeclaredMethod("owner", ObjectInstance.class);
        owner.setAccessible(true);
        return (String) owner.invoke(router, object);
    }
}
