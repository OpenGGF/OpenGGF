package com.openggf.game.sonic3k.ending;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

/** Native D01 managed children, independent of a cold DEZ/DDZ route. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kRescueFlightChildren {
    @AfterEach void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
    }
    private HeadlessTestFixture boot(String main,int progress){return boot(main,progress,320);}
    private HeadlessTestFixture boot(String main, int progress, int width) {
        var config = SonicConfigurationService.getInstance();
        var aspect=java.util.Arrays.stream(com.openggf.configuration.WidescreenAspect.values())
                .filter(value -> value.pixelWidth()==width).findFirst().orElseThrow();
        com.openggf.tests.SozAcceptanceConfigurations.selectDisplay(aspect);
        GameServices.camera().refreshViewportDimensions(config);
        com.openggf.tests.SozAcceptanceConfigurations.assertDisplay(aspect);
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, main);
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(13, 1)
                .withFreshLevelStartLifecycle().build();
        com.openggf.tests.SozAcceptanceConfigurations.assertDisplay(aspect);
        var game = GameServices.gameState();
        game.configureSpecialStageProgress(7, 7);
        for (int i = 0; i < 7 && progress >= 0; i++) {
            game.markEmeraldCollected(i);
            if (progress > 0) game.markSuperEmeraldCollected(i);
        }
        return fixture;
    }
    private EndingRescueDirectorObjectInstance owner() {
        return GameServices.level().getObjectManager()
                .activeObjectsOfType(EndingRescueDirectorObjectInstance.class).getFirst();
    }
    private List<EndingKnucklesFlightChildObjectInstance> children() {
        return GameServices.level().getObjectManager()
                .activeObjectsOfType(EndingKnucklesFlightChildObjectInstance.class).stream()
                .filter(child -> !child.isDestroyed()).toList();
    }
    private void until(HeadlessTestFixture fixture, int routine) {
        for (int n = 0; n < 4000 && owner().routine() != routine; n++)
            fixture.stepFrame(false, false, false, false, false);
        assertEquals(routine, owner().routine(), "production phase must reach its own boundary");
    }
    @ParameterizedTest @CsvSource({"sonic,-2", "sonic,0", "sonic,2", "tails,-2", "tails,0", "tails,2"})
    void rescueAllocatesNativeLargeAndSmallGroupsAndReplaysTheirRetirement(String main, int progress) {
        var fixture = boot(main, progress);
        until(fixture, 22); // loc_5DC2C creates each independent source group.
        var large = children();
        assertEquals(progress < 0 ? 4 : 5, large.size(), "two flames, propeller, passenger, optional emerald");
        assertEquals(List.of(0, 2), large.stream().filter(c -> c.role() == 4)
                .map(EndingKnucklesFlightChildObjectInstance::subtype).toList());
        assertEquals(1, large.stream().filter(c -> c.role() == 5).count());
        assertEquals(1, large.stream().filter(c -> c.role() == 3).count());
        assertEquals(progress < 0 ? 0 : 1, large.stream().filter(c -> c.role() == 7).count());
        assertTrue(large.stream().allMatch(c -> c.getSlotIndex() > owner().getSlotIndex()),
                "CreateChild1_Normal/CreateChild6_Simple allocate after the physical parent");
        assertTrue(large.stream().allMatch(c -> c.parentSlot() == owner().getSlotIndex()));
        assertTrue(large.stream().allMatch(c -> !c.submittedForDraw()),
                "loc_5ECB4/5EBF0/5EC3C/5EC94 initializers return before Draw_Sprite");
        assertTrue(large.stream().allMatch(c -> (c.nativeSstState().renderFlags() & 4) == 0),
                "D01 parent and children retain native screen coordinates");
        assertEquals(main.equals("tails") ? List.of(0xD,0xF) : List.of(0xC,0xE),
                large.stream().filter(c -> c.role() == 4).map(EndingKnucklesFlightChildObjectInstance::mappingFrame).toList());
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        var saved = registry.capture();
        until(fixture, 26); // flags38 bit4 retires the old groups in this production pass.
        assertEquals(progress < 0 ? 1 : 3, children().size());
        if(progress >= 0) {
            var deferred = children().stream().filter(c -> c.role() == 7).findFirst().orElseThrow();
            assertFalse(deferred.submittedForDraw());
            assertEquals(0x10, deferred.nativeSstState().flags38() & 0x10,
                    "Child_Draw_Sprite2 schedules deletion for the following dispatch");
        }
        assertEquals(1, children().stream().filter(c -> c.role() == 6).count());
        assertEquals(progress < 0 ? 0 : 1, children().stream().filter(c -> c.role() == 8).count());
        until(fixture, 28); // loc_5DD34 sets bit5; the small emerald retires.
        assertEquals(1, children().size());
        assertEquals(6, children().getFirst().role());
        var expected = registry.capture();
        registry.restore(saved);
        until(fixture, 26);
        until(fixture, 28);
        var actual = registry.capture();
        assertEquals(expected.entries().keySet(), actual.entries().keySet());
        for (String key : expected.entries().keySet())
            assertTrue(com.openggf.game.rewind.RewindSnapshotDiff.diffKey(
                    key, expected.get(key), actual.get(key)).isEmpty(), key);
    }
    @ParameterizedTest @CsvSource({"sonic,320", "sonic,352", "sonic,400", "sonic,528", "sonic,800",
            "tails,320", "tails,352", "tails,400", "tails,528", "tails,800"})
    void actualHyperSceneHandoffAllocatesOnlyTheNativeAizReturnGroups(String main,int width) {
        var fixture = boot(main,2,width);
        assertEquals(width,GameServices.camera().getWidth());
        EndingRescueDirectorObjectInstance arrived = null;
        for(int n=0;n<6000&&arrived==null;n++) {
            fixture.stepFrame(false,false,false,false,false);
            arrived=GameServices.level().getObjectManager()
                    .activeObjectsOfType(EndingRescueDirectorObjectInstance.class).stream()
                    .filter(o -> !o.isDestroyed()&&o.sequence()==EndingRescueDirectorObjectInstance.AIZ_RETURN&&o.routine()==2)
                    .findFirst().orElse(null);
        }
        assertNotNull(arrived,"the actual foreground fade must dispatch the AIZ return scene");
        assertEquals(List.of(4,4,5),children().stream().map(EndingKnucklesFlightChildObjectInstance::role).sorted().toList());
        int parentSlot=arrived.getSlotIndex();
        assertTrue(children().stream().allMatch(c -> c.parentSlot()==parentSlot));
        assertTrue(children().stream().allMatch(c -> !c.submittedForDraw()));
        assertEquals(0x26E,arrived.nativeSstState().artTile(),"Obj_5DFEE has native low-priority art");
        assertTrue(children().stream().allMatch(c -> !c.isHighPriority()));
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry();
        var saved=registry.capture();
        for(int n=0;n<5;n++)fixture.stepFrame(false,false,false,false,false);
        var expected=registry.capture();
        registry.restore(saved);
        for(int n=0;n<5;n++)fixture.stepFrame(false,false,false,false,false);
        var actual=registry.capture();
        for(String key:expected.entries().keySet())
            assertTrue(com.openggf.game.rewind.RewindSnapshotDiff.diffKey(key,expected.get(key),actual.get(key)).isEmpty(),key);
    }

}
