package com.openggf.game.sonic2;

import com.openggf.audio.AudioAdmissionObserver;
import com.openggf.audio.AudioManager;
import com.openggf.audio.driver.SfxContentionObserver;
import com.openggf.audio.driver.SmpsDriverServiceObserver;
import com.openggf.audio.synth.ChipWriteObserver;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.FieldKey;
import com.openggf.game.rewind.GenericFieldCapturer;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.audio.smps.Sonic2SmpsLoader;
import com.openggf.game.sonic2.objects.SuperSonicStarsObjectInstance;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.InvincibilityStarsObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.sprites.playable.SuperState;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Sonic_CheckGoSuper's effects precede the first global palette fade tick. */
@RequiresRom(SonicGame.SONIC_2)
class TestSonic2SuperTransformationStartEffects {
    @AfterEach
    void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
    }

    private static HeadlessTestFixture ready() {
        CrossGameFeatureProvider.getInstance().resetState();
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(0, 0)
                .withFreshLevelStartLifecycle().build();
        GameServices.gameState().configureSpecialStageProgress(7, 7);
        for (int emerald = 0; emerald < 7; emerald++) GameServices.gameState().markEmeraldCollected(emerald);
        var player = fixture.sprite();
        player.setRingCount(100);
        player.setAir(true);
        player.setJumping(true);
        player.setYSpeed((short) 0x28);
        player.setInvincibleFrames(321);
        player.currentLevelState().resumeTimer();
        return fixture;
    }

    private static Sonic2SuperStateController owner(HeadlessTestFixture fixture) {
        return assertInstanceOf(Sonic2SuperStateController.class, fixture.sprite().getSuperStateController());
    }

    private static ObjectManager objects() {
        return GameServices.level().getObjectManager();
    }

    private static SuperSonicStarsObjectInstance stars() {
        return objects().activeObjectsOfType(SuperSonicStarsObjectInstance.class).stream()
                .filter(stars -> !stars.isDestroyed() && stars.getSlotIndex() == 129)
                .findFirst().orElse(null);
    }

    private static AudioManager.DiagnosticObserverSet observe(List<String> requests, Runnable atRequest) {
        return new AudioManager.DiagnosticObserverSet((kind, id) -> {
            atRequest.run();
            requests.add(kind + ":" + id);
        }, AudioAdmissionObserver.NONE, SmpsDriverServiceObserver.NONE,
                ChipWriteObserver.NONE, SfxContentionObserver.NONE);
    }

    private static void assertStarted(HeadlessTestFixture fixture) {
        assertEquals(SuperState.TRANSFORMING, owner(fixture).getState());
        assertTrue(fixture.sprite().isSuperSonic());
        assertTrue(fixture.sprite().getInvulnerable(), "secondary invincibility protection is already active");
        assertEquals(0, fixture.sprite().getInvincibleFrames(), "the old monitor timer is cleared at start");
        assertNotNull(stars(), "Obj7E ID is published at the fixed SST before either audio request");
        assertTrue(stars().isOwnedBy(fixture.sprite()));
    }

    @Test
    void startPublishesStarsAndProtectionThenSfxAndSuperMusicOnceBeforePaletteRelease() throws Exception {
        var fixture = ready();
        var requests = new ArrayList<String>();
        try (var lease = GameServices.audio().acquireDiagnosticObservers(observe(requests, () -> assertStarted(fixture)))) {
            owner(fixture).checkTransformationBeforeMove();
            assertStarted(fixture);
            assertEquals(List.of("SPECIAL_SFX:223", "MUSIC:147"), requests,
                    "native PlaySound($DF) precedes PlayMusic; engine Super key is $93");
            assertEquals(0xF, owner(fixture).captureRewindState().paletteTimer());
            var effect = stars();
            var idle = effect.captureRewindState();
            fixture.sprite().setGSpeed((short) 0x900);
            effect.update(1, fixture.sprite());
            assertEquals(idle, effect.captureRewindState(),
                    "the already-written native obj_control prevents an idle star trigger on the start pass");
            // Request observer assertions above apply only to the start boundary.
        }
        var afterStart = new ArrayList<String>();
        try (var lease = GameServices.audio().acquireDiagnosticObservers(observe(afterStart, () -> { }))) {
            for (int tick = 0; tick < 80 && owner(fixture).getState() != SuperState.SUPER; tick++) {
                fixture.stepFrame(false, false, false, false, false);
            }
            assertEquals(SuperState.SUPER, owner(fixture).getState());
            assertEquals(3, owner(fixture).captureRewindState().paletteTimer(), "final palette reload remains native");
            assertFalse(afterStart.contains("SPECIAL_SFX:223"));
            assertFalse(afterStart.contains("MUSIC:147"), "palette activation does not request Super music again");
        }
        // $96 in the ROM's request namespace is not playMusic(int)'s engine
        // playlist key. These actual-ROM loader pointers distinguish the songs.
        var loader = new Sonic2SmpsLoader(GameServices.rom().getRom());
        assertEquals(0xFCBBC, loader.findMusicOffset(0x93));
        assertEquals(0xFD193, loader.findMusicOffset(0x96));
    }

    @Test
    void rejectedApexGateLeavesAllStartEffectsUntouched() {
        var fixture = ready();
        fixture.sprite().setRingCount(49);
        var requests = new ArrayList<String>();
        try (var lease = GameServices.audio().acquireDiagnosticObservers(observe(requests, () -> { }))) {
            owner(fixture).checkTransformationBeforeMove();
            assertEquals(SuperState.NORMAL, owner(fixture).getState());
            assertFalse(fixture.sprite().isSuperSonic());
            assertEquals(321, fixture.sprite().getInvincibleFrames());
            assertEquals(0, owner(fixture).getPaletteState());
            assertNull(stars());
            assertTrue(requests.isEmpty());
        }
    }

    @Test
    void fixedStarsPublishWithEveryGeneralDynamicSlotOccupied() {
        var fixture = ready();
        int capacity = objects().getObjectSlotCapacity();
        assertEquals(112, capacity);
        for (int added = 0; added < capacity && objects().getActiveObjectSlotCount() < capacity; added++) {
            objects().addDynamicObject(new PoolOccupant());
        }
        assertEquals(capacity, objects().getActiveObjectSlotCount());
        var overflow = new PoolOccupant();
        objects().addDynamicObject(overflow);
        assertTrue(overflow.isDestroyed(), "ordinary FindFreeObj really is exhausted");
        owner(fixture).checkTransformationBeforeMove();
        assertStarted(fixture);
        assertEquals(capacity, objects().getActiveObjectSlotCount(), "fixed129 consumes no dynamic slot");
    }

    @Test
    void ordinaryInvincibilityHasItsOwnFixedSlotAndIsNotOverwrittenAtTransformStart() {
        var fixture = ready();
        fixture.sprite().giveInvincibility(321);
        var ordinary = assertInstanceOf(InvincibilityStarsObjectInstance.class,
                fixture.sprite().getInvincibilityObject());
        assertEquals(136, ordinary.getSlotIndex(), "native Sonic_InvincibilityStars starts at $D200");
        var before = ordinary.captureRewindState();
        owner(fixture).checkTransformationBeforeMove();
        assertStarted(fixture);
        assertEquals(before, ordinary.captureRewindState(), "the ID-only $D040 write cannot change $D200");
        assertFalse(ordinary.isDestroyed());
        assertNotSame(ordinary, stars());
    }

    @Test
    void freshStarsRetainZeroTimerUntilTheFirstActiveAnimationStep() {
        var fixture = ready();
        fixture.sprite().setSuperSonic(true);
        fixture.sprite().setGSpeed((short) 0x900);
        var effect = new SuperSonicStarsObjectInstance(fixture.sprite());
        ObjectLifetimeOps.addDynamicAtReservedSlot(objects(), effect, 129);
        effect.update(0, fixture.sprite());
        assertEquals(0, starWord(effect, "mappingFrame"));
        assertEquals(0, starWord(effect, "frameTimer"), "loc_1E188 does not seed this native byte");
        effect.update(1, fixture.sprite());
        assertEquals(1, starWord(effect, "mappingFrame"), "the inherited zero expires on the next active call");
        assertEquals(1, starWord(effect, "frameTimer"));
        effect.update(2, fixture.sprite());
        assertEquals(1, starWord(effect, "mappingFrame"));
        assertEquals(0, starWord(effect, "frameTimer"));
    }

    private static int starWord(SuperSonicStarsObjectInstance effect, String field) {
        return (Integer) GenericFieldCapturer.captureObjectSubclassScalars(effect)
                .value(new FieldKey(SuperSonicStarsObjectInstance.class.getName(), field));
    }

    @Test
    void existingStarIdWriteRetainsAnimationAndWorldRestoreReplaysTheStartAndForwardTicks() {
        var fixture = ready();
        var player = fixture.sprite();
        var effect = new SuperSonicStarsObjectInstance(player);
        ObjectLifetimeOps.addDynamicAtReservedSlot(objects(), effect, 129);
        var initial = effect.captureRewindState();
        player.setSuperSonic(true);
        player.setGSpeed((short) 0x900);
        for (int tick = 0; tick < 3; tick++) effect.update(tick, player);
        var retained = effect.captureRewindState();
        assertNotEquals(initial, retained, "existing mapping/timer state is not an empty-star surrogate");
        player.setSuperSonic(false);
        var registry = fixture.gameplayMode().getRewindRegistry();
        var before = registry.capture();
        Object expectedStar = null;
        Object expectedController = null;
        var requests = new ArrayList<String>();
        try (var lease = GameServices.audio().acquireDiagnosticObservers(observe(requests, () -> { }))) {
            for (int replay = 0; replay < 2; replay++) {
                if (replay != 0) {
                    requests.clear();
                    registry.restore(before);
                    assertTrue(requests.isEmpty(), "restore must not issue transform audio");
                    assertEquals(retained, stars().captureRewindState());
                }
                var liveBefore = stars();
                owner(fixture).checkTransformationBeforeMove();
                assertStarted(fixture);
                assertSame(liveBefore, stars(), "writing Obj7E's ID retains the current owning instance");
                assertEquals(retained, stars().captureRewindState(), "no routine/timer/position clear at the ID write");
                assertEquals(List.of("SPECIAL_SFX:223", "MUSIC:147"), requests);
                for (int tick = 0; tick < 5; tick++) fixture.stepFrame(false, false, false, false, false);
                if (replay == 0) {
                    expectedStar = stars().captureRewindState();
                    expectedController = owner(fixture).captureRewindState();
                } else {
                    assertEquals(expectedStar, stars().captureRewindState());
                    assertEquals(expectedController, owner(fixture).captureRewindState());
                }
            }
        }
    }

    public static final class PoolOccupant extends AbstractObjectInstance implements RewindRecreatable {
        public PoolOccupant() { super(null, "TransformStartPoolOccupant"); }
        @Override public void update(int vIntRunCount, PlayableEntity player) { }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
        @Override public PoolOccupant recreateForRewind(RewindRecreateContext context) { return new PoolOccupant(); }
    }
}
