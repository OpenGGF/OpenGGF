package com.openggf.game.mutators;

import com.openggf.audio.AudioManager;
import com.openggf.audio.rewind.AudioCommand;
import com.openggf.audio.rewind.AudioReplayReason;
import com.openggf.audio.presentation.PresentationMode;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.game.GameplayObservation.Phase;
import com.openggf.game.LevelLoadCause;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.session.*;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.audio.Sonic3kAudioProfile;
import com.openggf.mods.mutators.AudioEffectPolicy;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestAudioMutatorRuntime {
    private AudioManager audio;
    private Fixture fixture;
    @BeforeEach void setup() {
        audio = AudioManager.getInstance();
        audio.setAudioProfile(new Sonic3kAudioProfile());
        audio.setRom(TestEnvironment.currentRom());
        fixture = new Fixture();
        fixture.runtime.beforeAssembly(LevelLoadCause.FULL_LEVEL_ASSEMBLY);
        fixture.runtime.bindRoster(fixture.sprites);
        audio.playMusic(1);
    }
    @AfterEach void cleanup() { fixture.runtime.retire(); audio.resetState(); }

    @Test void surpriseExpiresThroughCurrentNativeIntentAndCannotRestartSameActivation() {
        fixture.music(1, 3);
        tick();
        assertEquals(List.of(1, 3), musicRequests());
        for (int i = 0; i < 180; i++) tick();
        assertEquals(List.of(1, 3, 1), musicRequests());
        assertEquals(0, fixture.runtime.capture().musicActivation());
        assertTrue(fixture.reports.get(1L).contains("expired"));
    }

    @Test void nativePrioritySongsSupersedeAndExpiryNeverReplaysOldZone() {
        for (int priority : new int[]{0x18, 0x19, 0x29, 0x2C, 0x31}) {
            fixture.music(priority, 3);
            tick();
            int before = musicRequests().size();
            audio.playMusic(priority);
            for (int i = 0; i < 200; i++) tick();
            assertEquals(before + 1, musicRequests().size());
            assertEquals(priority, musicRequests().getLast());
            fixture.source.clear();
            audio.playMusic(1);
            for (int i = 0; i < 15; i++) tick();
        }
    }

    @Test void nativeOneUpSavesNativeIntentInsteadOfSurprise() {
        fixture.music(1, 3); tick();
        audio.playMusic(0x2A);
        assertEquals(List.of(1, 3, 1, 0x2A), musicRequests());
        audio.presentFrame(PresentationMode.FORWARD);
        assertEquals(List.of(1), audio.captureLogicalSnapshot().presentation().overrideStack().stream()
                .map(slot -> slot.musicId()).toList());
        audio.endMusicOverride(0x2A);
        fixture.source.clear(); tick();
        assertEquals(0, fixture.runtime.capture().musicActivation());
        assertEquals(1, fixture.runtime.capture().nativeMusic());
    }

    @Test void pendingAndNaturalOneUpPriorityIsObservedWithoutRawForegroundInference() {
        fixture.clang();
        audio.playMusic(0x2A);
        audio.playMusic(0x2A); // Shipped retrigger policy ignores a second request.
        fixture.source.add(entry(2, AudioEffectPolicy.Mode.MUSIC, 3));
        tick();
        assertEquals(List.of(1, 0x2A, 0x2A), musicRequests(), "a pending native jingle already owns priority");
        assertEquals(List.of(1), fixture.runtime.capture().nativeOverrides());
        for (int i = 0; i < 300 && !fixture.runtime.capture().nativeOverrides().isEmpty(); i++)
            audio.presentFrame(PresentationMode.FORWARD);
        assertTrue(fixture.runtime.capture().nativeOverrides().isEmpty(), "native E4 must notify the world intent owner");
        assertEquals(1, fixture.runtime.capture().nativeMusic());
        fixture.source.removeIf(e -> e.activationId() == 2);
        landing();
        assertEquals(List.of(0x49), sfxRequests());
    }

    @Test void speedAndControlRequestsReleaseMusicWithoutRestoringCapturedControls() {
        fixture.music(1, 3); tick();
        audio.setSpeedMultiplier(8);
        assertEquals(List.of(1, 3, 1), musicRequests());
        int count = audio.commandTimeline().entryCount();
        for (int i = 0; i < 200; i++) tick();
        assertEquals(count, audio.commandTimeline().entryCount());
        var speedCommands = commands().stream().filter(AudioCommand.SetSpeedMultiplier.class::isInstance).toList();
        assertEquals(List.of(new AudioCommand.SetSpeedMultiplier(8)), speedCommands);
        fixture.music(2, 4); tick();
        assertEquals(1, musicRequests().getLast());
        audio.setSpeedMultiplier(1); tick();
        assertEquals(4, musicRequests().getLast());
        audio.playMusic(0xE0);
        for (int i = 0; i < 200; i++) tick();
        assertEquals(0, fixture.runtime.capture().musicActivation());
    }

    @Test void overlappingActivationsCannotRestoreEarlierSurprise() {
        fixture.music(1, 3); tick();
        for (int i = 0; i < 15; i++) tick();
        fixture.source.add(entry(2, AudioEffectPolicy.Mode.MUSIC, 4)); tick();
        assertEquals(List.of(1, 3, 4), musicRequests());
        fixture.source.removeIf(e -> e.activationId() == 2); tick();
        assertEquals(List.of(1, 3, 4, 1), musicRequests());
        for (int i = 0; i < 200; i++) tick();
        assertEquals(List.of(1, 3, 4, 1), musicRequests());
    }

    @Test void invalidAndUnpreparedCuesReportRejectionAndNeverFallback() {
        fixture.music(1, 0x31); tick();
        assertTrue(fixture.reports.get(1L).contains("Rejected"));
        fixture.music(2, 3);
        audio.setRom(null); tick();
        assertTrue(fixture.reports.get(2L).contains("Rejected"));
        fixture.source.clear(); fixture.source.add(entry(3, AudioEffectPolicy.Mode.LANDING_CUE, 0xA9));
        landing();
        assertTrue(fixture.reports.get(3L).contains("Rejected"));
        assertEquals(List.of(1), musicRequests());
        assertTrue(sfxRequests().isEmpty());
    }

    @Test void inconsistentEngineProjectionCannotTurnTimedAudioIntoAnInstantPolicy() {
        fixture.source.add(new TransientMutatorPolicySource.Entry("test", 1,
                new AudioEffectPolicy(AudioEffectPolicy.Mode.MUSIC, 3, 15), 0, 0, true));
        fixture.source.add(new TransientMutatorPolicySource.Entry("test", 2,
                new AudioEffectPolicy(AudioEffectPolicy.Mode.LANDING_CUE, 0x49, 15), 0, 0, false));
        tick();
        assertTrue(fixture.reports.get(1L).contains("live timed activation"));
        assertTrue(fixture.reports.get(2L).contains("live timed activation"));
        assertEquals(List.of(1), musicRequests());
        assertTrue(sfxRequests().isEmpty());
    }

    @Test void realLandingAndCompletedSpindashEventsShareStrictGlobalCooldown() {
        fixture.clang();
        landing();
        assertEquals(List.of(0x49), sfxRequests());
        fixture.player.setSpindash(true);
        before(); fixture.player.setSpindash(false); fixture.runtime.spindashReleased(fixture.player); after();
        assertEquals(List.of(0x49), sfxRequests());
        for (int i = 0; i < 15; i++) tick();
        fixture.player.setSpindash(true);
        before(); fixture.player.setSpindash(false); fixture.runtime.spindashReleased(fixture.player); after();
        assertEquals(List.of(0x49, 0x49), sfxRequests());
    }

    @Test void rawGroundingSpinCancellationHurtAndForeignPlayersCannotManufactureCue() {
        fixture.clang();
        fixture.player.setAir(true); before(); fixture.player.setAir(false); after();
        fixture.player.setSpindash(true); before(); fixture.player.setSpindash(false); after();
        fixture.player.setHurt(true); before(); fixture.player.setHurt(false); fixture.runtime.landed(fixture.player); after();
        before(); fixture.runtime.landed(new Sonic("foreign", (short) 0, (short) 0)); after();
        fixture.player.setControlLocked(true); before(); fixture.runtime.landed(fixture.player); after();
        assertTrue(sfxRequests().isEmpty());
    }

    @Test void nativeWarningBeforeLandingAndActiveWarningPreventOptionalAdmission() {
        fixture.clang();
        before(); audio.playSfx(0xA9); fixture.runtime.landed(fixture.player); after();
        assertEquals(List.of(0xA9), sfxRequests());
        audio.presentFrame(PresentationMode.FORWARD);
        for (int i = 0; i < 31; i++) tick();
        // Keep the real, now-materialized warning alive independently of gameplay's quiet period.
        landing();
        assertEquals(List.of(0xA9), sfxRequests());
        assertTrue(fixture.reports.get(1L).contains("warning"));
    }

    @Test void surpriseAndItsDeferredExpiryCannotTruncateNativeWarning() {
        fixture.music(1, 3);
        audio.playSfx(0xA9);
        tick();
        assertEquals(List.of(1), musicRequests());
        audio.presentFrame(PresentationMode.FORWARD);
        for (int i = 0; i < 31; i++) tick();
        assertEquals(List.of(1), musicRequests(), "an active warning remains protected after the quiet period");
        for (int i = 0; i < 90; i++) audio.presentFrame(PresentationMode.FORWARD);
        tick();
        assertEquals(List.of(1, 3), musicRequests());
        audio.playSfx(0xA9);
        audio.presentFrame(PresentationMode.FORWARD);
        fixture.source.clear(); tick();
        assertTrue(fixture.runtime.capture().pendingNativeResume());
        assertEquals(List.of(1, 3), musicRequests(), "expiry must preserve the warning instead of loading zone music over it");
        for (int i = 0; i < 90; i++) audio.presentFrame(PresentationMode.FORWARD);
        for (int i = 0; i < 31; i++) tick();
        assertEquals(List.of(1, 3, 1), musicRequests());
        assertFalse(fixture.runtime.capture().pendingNativeResume());
    }

    @Test void sourceLossCancelsLeaseAndCannotAbortFollowingNativeControl() {
        fixture.music(1, 3); tick();
        audio.setRom(null);
        assertEquals(0, fixture.runtime.capture().musicActivation());
        assertDoesNotThrow(() -> audio.setSpeedMultiplier(8));
        assertInstanceOf(AudioCommand.SetSpeedMultiplier.class, commands().getLast());
        fixture.source.clear();
        for (int i = 0; i < 200; i++) tick();
        assertEquals(List.of(1, 3), musicRequests());
    }

    @Test void pauseHoldRewindPreserveStateAndLeavingOrdinaryGameplayReleasesIt() {
        fixture.music(1, 3); tick();
        var snapshot = fixture.runtime.capture();
        for (var phase : new Phase[]{Phase.PAUSED, Phase.HELD, Phase.REWIND}) {
            fixture.runtime.leaveOrdinaryGameplay(phase);
            fixture.runtime.onPresentation(null, LogicalInputSnapshot.neutral(), phase);
            assertEquals(snapshot, fixture.runtime.capture());
        }
        fixture.runtime.leaveOrdinaryGameplay(Phase.STAGE);
        assertEquals(List.of(1, 3, 1), musicRequests());
        assertEquals(0, fixture.runtime.capture().musicActivation());
    }

    @Test void capturedCooldownAndIntentRestoreWithoutSubmissionAndCrossWorldRestoreIsRejected() {
        fixture.music(1, 3); fixture.clang(2);
        landing();
        var snapshot = fixture.runtime.capture();
        int count = audio.commandTimeline().entryCount();
        audio.playMusic(0x31);
        fixture.runtime.restore(snapshot);
        assertEquals(count + 1, audio.commandTimeline().entryCount());
        assertEquals(snapshot, fixture.runtime.capture());
        try (var replay = audio.beginRewindReplay(5, 1, AudioReplayReason.SEEK)) {
            landing(); audio.playMusic(0x18);
        }
        assertEquals(count + 1, audio.commandTimeline().entryCount());
        var other = new Fixture();
        assertThrows(IllegalArgumentException.class, () -> other.runtime.restore(snapshot));
        other.runtime.retire();
    }

    @Test void exactOwnerRetirementCannotUnbindNewWorldOrRestoreOldSong() {
        fixture.music(1, 3); tick();
        var other = new Fixture();
        other.runtime.beforeAssembly(LevelLoadCause.FULL_LEVEL_ASSEMBLY);
        audio.playMusic(4);
        int count = audio.commandTimeline().entryCount();
        fixture.runtime.retire();
        assertEquals(count, audio.commandTimeline().entryCount());
        other.music(2, 5); other.runtime.beforeNativeTick(null, LogicalInputSnapshot.neutral());
        other.runtime.afterNativeTick(null, LogicalInputSnapshot.neutral());
        assertEquals(5, musicRequests().getLast());
        other.runtime.retire();
        assertThrows(IllegalStateException.class, () -> fixture.runtime.restore(fixture.runtime.capture()));
    }

    @Test void creatorClassloaderCannotArmTicksOrForgeNativeContactEvents() throws Exception {
        String name = ForeignCaller.class.getName();
        byte[] bytes;
        try (var input = ForeignCaller.class.getResourceAsStream("/" + name.replace('.', '/') + ".class")) {
            assertNotNull(input); bytes = input.readAllBytes();
        }
        var loader = new ClassLoader(AudioMutatorRuntime.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String type, boolean resolve) throws ClassNotFoundException {
                if (!type.equals(name)) return super.loadClass(type, resolve);
                var value = findLoadedClass(type);
                if (value == null) value = defineClass(type, bytes, 0, bytes.length);
                if (resolve) resolveClass(value);
                return value;
            }
        };
        var caller = loader.loadClass(name);
        var arm = assertThrows(java.lang.reflect.InvocationTargetException.class,
                () -> caller.getMethod("arm", AudioMutatorRuntime.class).invoke(null, fixture.runtime));
        assertInstanceOf(SecurityException.class, arm.getCause());
        var contact = assertThrows(java.lang.reflect.InvocationTargetException.class,
                () -> caller.getMethod("contact", com.openggf.sprites.playable.AbstractPlayableSprite.class)
                        .invoke(null, fixture.player));
        assertInstanceOf(SecurityException.class, contact.getCause());
    }
    public static class ForeignCaller {
        public static void arm(AudioMutatorRuntime runtime) { runtime.beforeNativeTick(null, LogicalInputSnapshot.neutral()); }
        public static void contact(com.openggf.sprites.playable.AbstractPlayableSprite player) { PlayableAudioEvents.landed(player); }
    }

    private void before() { fixture.runtime.beforeNativeTick(null, LogicalInputSnapshot.neutral()); }
    private void after() { fixture.runtime.afterNativeTick(null, LogicalInputSnapshot.neutral()); }
    private void tick() { before(); after(); }
    private void landing() {
        fixture.player.setAir(true); before(); fixture.player.setAir(false);
        fixture.runtime.landed(fixture.player); after();
    }
    private List<AudioCommand> commands() {
        var values = new ArrayList<AudioCommand>();
        for (int i = 0; i < audio.commandTimeline().entryCount(); i++) values.add(audio.commandTimeline().entryAt(i).command());
        return values;
    }
    private List<Integer> musicRequests() { return commands().stream().filter(AudioCommand.PlayMusic.class::isInstance)
            .map(AudioCommand.PlayMusic.class::cast).map(AudioCommand.PlayMusic::musicId).toList(); }
    private List<Integer> sfxRequests() { return commands().stream().filter(AudioCommand.PlaySfx.class::isInstance)
            .map(AudioCommand.PlaySfx.class::cast).map(AudioCommand.PlaySfx::sfxId).toList(); }
    private static TransientMutatorPolicySource.Entry entry(long id, AudioEffectPolicy.Mode mode, int cue) {
        return new TransientMutatorPolicySource.Entry("test", id, new AudioEffectPolicy(mode, cue, 15), 0, 600, false);
    }
    private static final class Fixture {
        final List<TransientMutatorPolicySource.Entry> source = new ArrayList<>();
        final Map<Long, String> reports = new HashMap<>();
        final WorldSession world;
        final AudioMutatorRuntime runtime;
        final SpriteManager sprites = new SpriteManager();
        final Sonic player = new Sonic("sonic", (short) 100, (short) 100);
        Fixture() {
            var nativeModule = new Sonic3kGameModule();
            var module = new DelegatingGameModule(nativeModule, "test") {
                @Override public <T> T getGameService(Class<T> type) {
                    if (type == WorldSessionPolicyProvider.class) return type.cast(WorldSessionPolicyProvider.compose(
                            owner -> new SourceState(), AudioMutatorRuntime::new));
                    return super.getGameService(type);
                }
            };
            world = new WorldSession(module);
            runtime = WorldSessionPolicyAccess.getService(world, AudioMutatorRuntime.class);
            sprites.addSprite(player);
            runtime.bindRoster(sprites);
        }
        void music(long id, int cue) { source.clear(); source.add(entry(id, AudioEffectPolicy.Mode.MUSIC, cue)); }
        void clang() { clang(1); }
        void clang(long id) { source.add(entry(id, AudioEffectPolicy.Mode.LANDING_CUE, 0x49)); }
        final class SourceState implements WorldSessionPolicyState, TransientMutatorPolicySource, RewindSnapshottable<Integer> {
            public <T> T getService(Class<T> type) { return type == TransientMutatorPolicySource.class ? type.cast(this) : null; }
            public RewindSnapshottable<?> rewindAdapter() { return this; }
            public List<Entry> entries() { return List.copyOf(source); }
            public boolean acknowledge(long id) { return true; }
            public boolean report(long id, String status) { reports.put(id, status); return true; }
            public void beforeAssembly(LevelLoadCause cause) { }
            public void bindRoster(SpriteManager sprites) { }
            public void failedAssembly(LevelLoadCause cause) { }
            public void closeScreens() { }
            public void retire() { }
            public String key() { return "test-audio-source"; }
            public Integer capture() { return 0; }
            public void restore(Integer value) { }
        }
    }
}
