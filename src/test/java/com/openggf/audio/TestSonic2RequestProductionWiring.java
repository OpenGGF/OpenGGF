package com.openggf.audio;

import com.openggf.audio.presentation.PresentationMode;
import com.openggf.audio.presentation.AudioPresentationParityProbe;
import com.openggf.audio.presentation.AudioPresentationProducer;
import com.openggf.audio.presentation.AudioPresentationCommand;
import com.openggf.audio.rewind.AudioCommand;
import com.openggf.audio.rewind.AudioLogicalSnapshot;
import com.openggf.data.Rom;
import com.openggf.game.sonic2.audio.Sonic2AudioProfile;
import com.openggf.game.sonic2.audio.Sonic2SoundRequestService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static com.openggf.tests.RomTestUtils.ensureSonic2RomAvailable;

class TestSonic2RequestProductionWiring {

    @AfterEach
    void tearDown() {
        AudioManager.getInstance().resetState();
        AudioManager.getInstance().setBackend(new NullAudioBackend());
    }

    @Test
    void baseS2IngressDefersUntilForwardPresentationAndRingResolvesOnce() {
        AudioManager audio = AudioManager.getInstance();
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
        audio.setAudioProfile(new Sonic2AudioProfile());
        audio.setSoundMap(new Sonic2AudioProfile().getSoundMap());

        assertTrue(audio.playSfx(0xB5));
        assertEquals(0, audio.commandTimeline().entryCount(),
                "S2 ingress must write the source mailbox without immediate playback");

        audio.presentFrame(PresentationMode.SILENT);
        assertEquals(0, audio.commandTimeline().entryCount(),
                "silent presentation must not consume S2 mailbox work");

        audio.presentFrame(PresentationMode.FORWARD);
        List<AudioCommand> commands = audio.commandTimeline().entries().stream()
                .map(entry -> entry.command()).toList();
        assertEquals(1, commands.size());
        assertEquals(0xCE, ((AudioCommand.PlaySfx) commands.getFirst()).sfxId(),
                "raw B5 is resolved only by the S2 pipeline");

        audio.presentFrame(PresentationMode.FORWARD);
        assertEquals(1, audio.commandTimeline().entryCount(),
                "one outer boundary cannot service the same mailbox twice");
    }

    @Test
    void nonS2ProfileRetainsImmediateIngress() {
        AudioManager audio = AudioManager.getInstance();
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
        audio.setAudioProfile(new AudioTestFixtures.StubAudioProfile(
                new AudioTestFixtures.StubSmpsLoader()));

        audio.playMusic(0x82);

        assertEquals(1, audio.commandTimeline().entryCount());
    }

    @Test
    void s2RequestConsequenceIsAppliedInsideItsForwardTransaction()
            throws Exception {
        AudioManager audio = AudioManager.getInstance();
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
        audio.setAudioProfile(new Sonic2AudioProfile());
        audio.setSoundMap(new Sonic2AudioProfile().getSoundMap());

        assertTrue(audio.playSfx(0xF8));
        audio.presentFrame(PresentationMode.FORWARD);

        assertEquals(0, shadowCommands(audio).size(),
                "a committed request consequence must not wait for a second frame");
        assertEquals(1, audio.commandTimeline().entryCount(),
                "the durable request consequence publishes at the same seal");
        assertTrue(audio.commandTimeline().entries().getFirst().command()
                instanceof AudioCommand.StopAllSfx);
    }

    @Test
    void failedS2RequestTransactionLeavesNoTimelineParityOrQueueEvidence()
            throws Exception {
        AudioManager audio = AudioManager.getInstance();
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
        audio.setAudioProfile(new Sonic2AudioProfile());
        audio.setSoundMap(new Sonic2AudioProfile().getSoundMap());
        assertTrue(audio.playSfx(0xBF));
        audio.presentFrame(PresentationMode.FORWARD);
        assertEquals(0x7F, requestService(audio).snapshot().pipeline()
                .sfxPriorityValue(),
                "the F8 rollback must cover a populated S2 priority latch");
        assertTrue(audio.playSfx(0xF8));
        AudioLogicalSnapshot populatedBeforeFailure =
                audio.captureLogicalSnapshot();
        AudioPresentationParityProbe.Snapshot before =
                audio.shadowParitySnapshot();
        AudioPresentationProducer producer = shadowProducer(audio);
        Field renderBuffer = AudioPresentationProducer.class
                .getDeclaredField("smpsSourcePcm");
        renderBuffer.setAccessible(true);
        short[] validBuffer = (short[]) renderBuffer.get(producer);
        renderBuffer.set(producer, new short[0]);

        assertThrows(IllegalArgumentException.class,
                () -> audio.presentFrame(PresentationMode.FORWARD));

        assertEquals(populatedBeforeFailure, audio.captureLogicalSnapshot(),
                "F8 must restore the non-empty mailbox and priority state exactly");
        assertEquals(1, audio.commandTimeline().entryCount());
        assertEquals(0, shadowCommands(audio).size());
        assertEquals(before.commandCount(), audio.shadowParitySnapshot()
                .commandCount());

        renderBuffer.set(producer, validBuffer);
        audio.presentFrame(PresentationMode.FORWARD);

        assertEquals(2, audio.commandTimeline().entryCount());
        assertEquals(0, shadowCommands(audio).size());
        assertEquals(before.commandCount() + 1,
                audio.shadowParitySnapshot().commandCount());
    }

    @Test
    void failedS2SpeedCommandRestoresTheExactCoalescedQueuePrefix()
            throws Exception {
        AudioManager audio = AudioManager.getInstance();
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
        audio.setAudioProfile(new Sonic2AudioProfile());
        audio.setSoundMap(new Sonic2AudioProfile().getSoundMap());
        audio.setSpeedShoes(false);
        AudioPresentationCommand prefix = shadowQueueEntries(audio).getFirst();
        assertTrue(audio.playSfx(0xFB));

        AudioPresentationProducer producer = shadowProducer(audio);
        Field renderBuffer = AudioPresentationProducer.class
                .getDeclaredField("smpsSourcePcm");
        renderBuffer.setAccessible(true);
        short[] validBuffer = (short[]) renderBuffer.get(producer);
        renderBuffer.set(producer, new short[0]);

        assertThrows(IllegalArgumentException.class,
                () -> audio.presentFrame(PresentationMode.FORWARD));

        assertEquals(List.of(prefix), shadowQueueEntries(audio));
        assertSame(prefix, shadowQueueEntries(audio).getFirst(),
                "the private request batch must not replace a durable coalesced prefix");
        assertEquals(new AudioPresentationCommand.SetSpeedShoes(false), prefix);
        renderBuffer.set(producer, validBuffer);
    }

    @Test
    void failedS2RomResolutionRestoresPrefixAndResolverCursor()
            throws Exception {
        File romFile = ensureSonic2RomAvailable();
        assumeTrue(romFile != null, "Sonic 2 ROM is required");
        Rom rom = new Rom();
        assertTrue(rom.open(romFile.getAbsolutePath()));
        AudioManager audio = AudioManager.getInstance();
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
        audio.setRom(rom);
        Sonic2AudioProfile profile = new Sonic2AudioProfile();
        audio.setAudioProfile(profile);
        audio.setSoundMap(profile.getSoundMap());
        audio.setSpeedShoes(false);
        AudioPresentationCommand prefix = shadowQueueEntries(audio).getFirst();
        assertTrue(audio.playSfx(0xCE));

        AudioPresentationProducer producer = shadowProducer(audio);
        Field renderBuffer = AudioPresentationProducer.class
                .getDeclaredField("smpsSourcePcm");
        renderBuffer.setAccessible(true);
        short[] validBuffer = (short[]) renderBuffer.get(producer);
        renderBuffer.set(producer, new short[0]);

        assertThrows(IllegalArgumentException.class,
                () -> audio.presentFrame(PresentationMode.FORWARD));

        assertEquals(List.of(prefix), shadowQueueEntries(audio));
        assertSame(prefix, shadowQueueEntries(audio).getFirst());
        assertEquals(1L, resolverNextVoiceId(audio),
                "failed ROM resolution must not consume a resolver voice id");
        renderBuffer.set(producer, validBuffer);
    }

    @Test
    void postSealS2DiagnosticFailureCannotReplayTheCommittedRequest()
            throws Exception {
        AtomicInteger observations = new AtomicInteger();
        AudioManager audio = AudioManager.getInstance();
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
        audio.setAudioProfile(new Sonic2AudioProfile(event -> {
            observations.incrementAndGet();
            throw new IllegalStateException("injected request observer failure");
        }));
        audio.setSoundMap(new Sonic2AudioProfile().getSoundMap());
        assertTrue(audio.playSfx(0xF8));

        audio.presentFrame(PresentationMode.FORWARD);
        audio.presentFrame(PresentationMode.FORWARD);

        assertEquals(4, observations.get(),
                "one F8 request has submission, transfer, decision, and dispatch exactly once");
        assertEquals(1, audio.commandTimeline().entryCount(),
                "observer failure must not replay a sealed request");
        assertEquals(0, shadowCommands(audio).size());
    }

    @Test
    void s2OneUpTransactionStopsSfxSavesMusicAndThenClearsPriority()
            throws Exception {
        File romFile = ensureSonic2RomAvailable();
        assumeTrue(romFile != null, "Sonic 2 ROM is required");
        Rom rom = new Rom();
        assertTrue(rom.open(romFile.getAbsolutePath()));
        AudioManager audio = AudioManager.getInstance();
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
        audio.setRom(rom);
        Sonic2AudioProfile profile = new Sonic2AudioProfile();
        audio.setAudioProfile(profile);
        audio.setSoundMap(profile.getSoundMap());

        audio.playMusic(0x82);
        audio.presentFrame(PresentationMode.FORWARD);
        assertTrue(audio.playSfx(0xBF));
        audio.presentFrame(PresentationMode.FORWARD);
        assertTrue(audio.captureLogicalSnapshot().presentation().smpsLogical()
                        .sequencers().stream().anyMatch(entry -> entry.sfx()),
                "precondition: a live SFX must be present before the one-up");
        assertEquals(0x7F, requestService(audio).snapshot().pipeline()
                .sfxPriorityValue());

        audio.playMusic(0xB5);
        audio.presentFrame(PresentationMode.FORWARD);

        var logical = audio.captureLogicalSnapshot().presentation()
                .smpsLogical();
        assertEquals(0xB5, logical.sequencers().getFirst().source().id());
        assertFalse(logical.sequencers().stream().anyMatch(entry -> entry.sfx()),
                "S2 fixBugs=0 clears live SFX before the replacement music starts");
        assertEquals(List.of(0x82), logical.savedOverrides().stream()
                .map(saved -> saved.logical().sequencers().getFirst().source().id())
                .toList(), "one-up must save the interrupted music before its start");
        assertEquals(0, requestService(audio).snapshot().pipeline()
                .sfxPriorityValue(),
                "the one-up's post-save lifecycle must clear SFX priority");
    }

    @Test
    void logicalSnapshotRestoresPendingMailboxWork() {
        AudioManager audio = AudioManager.getInstance();
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
        audio.setAudioProfile(new Sonic2AudioProfile());
        audio.setSoundMap(new Sonic2AudioProfile().getSoundMap());

        assertTrue(audio.playSfx(0xB5));
        AudioLogicalSnapshot pending = audio.captureLogicalSnapshot();

        audio.presentFrame(PresentationMode.FORWARD);
        assertEquals(1, audio.commandTimeline().entryCount());

        audio.restoreLogicalSnapshot(pending);
        audio.presentFrame(PresentationMode.FORWARD);

        assertEquals(2, audio.commandTimeline().entryCount(),
                "restored request work must replay once after the historical command");
        assertEquals(0xCE, ((AudioCommand.PlaySfx) audio.commandTimeline()
                .entries().getLast().command()).sfxId());
        audio.presentFrame(PresentationMode.FORWARD);
        assertEquals(2, audio.commandTimeline().entryCount(),
                "restored request work must not replay twice");
    }

    @Test
    void reverseDoesNotConsumeAndSameFrameSoundWritesOverwrite() {
        AudioManager audio = AudioManager.getInstance();
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
        Sonic2AudioProfile profile = new Sonic2AudioProfile();
        audio.setAudioProfile(profile);
        audio.setSoundMap(profile.getSoundMap());

        assertTrue(audio.playSfx(0xA0));
        assertTrue(audio.playSfx(0xB5));
        audio.presentFrame(PresentationMode.REVERSE);
        assertEquals(0, audio.commandTimeline().entryCount(),
                "reverse presentation must not consume the mailbox");

        audio.presentFrame(PresentationMode.FORWARD);
        List<AudioCommand> commands = audio.commandTimeline().entries().stream()
                .map(entry -> entry.command()).toList();
        assertEquals(1, commands.size());
        assertEquals(0xCE, ((AudioCommand.PlaySfx) commands.getFirst()).sfxId(),
                "the later raw write must overwrite SFX0 before the bridge runs");
    }

    @Test
    void unmappedS2GameSoundRetainsImmediateFallbackRoute() {
        AudioManager audio = AudioManager.getInstance();
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
        Sonic2AudioProfile profile = new Sonic2AudioProfile();
        audio.setAudioProfile(profile);
        audio.setSoundMap(profile.getSoundMap());

        audio.playSfx(GameSound.FIRE_SHIELD);

        assertEquals(1, audio.commandTimeline().entryCount());
        AudioCommand.PlaySfx command = (AudioCommand.PlaySfx) audio
                .commandTimeline().entries().getFirst().command();
        assertEquals(AudioCommand.SfxRoute.FALLBACK_NAME, command.route());
    }

    @Test
    void standaloneNamedSfxRetainsImmediateRoute() {
        AudioManager audio = AudioManager.getInstance();
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
        Sonic2AudioProfile profile = new Sonic2AudioProfile();
        audio.setAudioProfile(profile);

        audio.playSfx("standalone-cue");

        assertEquals(1, audio.commandTimeline().entryCount());
        assertEquals(AudioCommand.SfxRoute.FALLBACK_NAME,
                ((AudioCommand.PlaySfx) audio.commandTimeline().entries()
                        .getFirst().command()).route());
    }

    private static com.openggf.audio.presentation.AudioPresentationCommandQueue
            shadowCommands(AudioManager audio) throws Exception {
        Field field = AudioManager.class.getDeclaredField("shadowCommands");
        field.setAccessible(true);
        return (com.openggf.audio.presentation.AudioPresentationCommandQueue)
                field.get(audio);
    }

    private static AudioPresentationProducer shadowProducer(AudioManager audio)
            throws Exception {
        Field field = AudioManager.class.getDeclaredField("shadowProducer");
        field.setAccessible(true);
        return (AudioPresentationProducer) field.get(audio);
    }

    @SuppressWarnings("unchecked")
    private static List<AudioPresentationCommand> shadowQueueEntries(
            AudioManager audio) throws Exception {
        Object queue = shadowCommands(audio);
        Field field = queue.getClass().getDeclaredField("entries");
        field.setAccessible(true);
        int size = (int) queue.getClass().getMethod("size").invoke(queue);
        AudioPresentationCommand[] entries =
                (AudioPresentationCommand[]) field.get(queue);
        return List.of(java.util.Arrays.copyOf(entries, size));
    }

    private static long resolverNextVoiceId(AudioManager audio) throws Exception {
        Field resolver = AudioManager.class.getDeclaredField("shadowResolver");
        resolver.setAccessible(true);
        Object value = resolver.get(audio);
        Field next = value.getClass().getDeclaredField("nextVoiceId");
        next.setAccessible(true);
        return next.getLong(value);
    }

    private static Sonic2SoundRequestService requestService(AudioManager audio)
            throws Exception {
        Field field = AudioManager.class.getDeclaredField("shadowRequestService");
        field.setAccessible(true);
        return (Sonic2SoundRequestService) field.get(audio);
    }
}
