package com.openggf.game.soniccd.audio;

import com.openggf.GameLoop;
import com.openggf.audio.LiveCaptureAudioHandle;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.game.soniccd.CdDiscDataSource;
import com.openggf.game.soniccd.CdPeriod;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.SonicCdGameModule;
import com.openggf.game.soniccd.SonicCdZoneRegistry;
import com.openggf.tools.HeadlessGameBoot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Past music on a direct stage load, through the production level load and audio presentation.
 *
 * <p>Natively the stage's PCM driver is loaded by {@code SPCmd_LoadLevel} (CD System Program/SPX.asm:744-772), the
 * command the initial program's {@code RunMMD} sends to run the stage (CD Initial Program/IPX.asm:1307-1309), before
 * the stage program's {@code LevelStart} sends {@code SCMD_PASTMUS} from {@code PlayLevelMusic} after one
 * {@code .WaitPLC} V-sync (Level/Main.asm:147-154, 555-573). The engine loads the driver in the Sonic CD load step
 * {@code CdLoadLevelPcmDriver}, ahead of the shared {@code InitAudio}.
 *
 * <p>Native side: the rehost fixture routes into each zone's Past act 1 from the stage select
 * ({@code <native.root>/aud1-fixtures/<payload>-audio}: the core's 44.1 kHz audio per frame, {@code frames.csv}
 * with {@code gameMode}, and every word the main CPU wrote to GACOMCMD0, {@code subcmd.csv}). The engine side boots
 * the stage directly in a {@link HeadlessGameBoot} with audio on and drains the presentation packets from the first
 * frame presented after the load. The engine's load is synchronous: no frame is presented between the command and
 * level frame 1, so the comparison is anchored on the command, not on the level's first frame.
 */
class TestCdPastMusicDirectLoad {
    private static final int AUDIBLE = 64;
    private static final int PAST_MUSIC = CdSubCpuAudio.commandId("SCMD_PASTMUS");
    /** {@code PCMM_PAST}, the byte {@code SPCmd_PlayPastMus} writes to {@code PCMDrvQueue} (SPX.asm:1612-1614). */
    private static final int PCMM_PAST = CdSubCpuAudio.command(PAST_MUSIC).argument();
    /** {@code PCMDrvQueue}'s first slot in {@link CdPcmDriver#capture()}'s variables. */
    private static final int QUEUE_SLOT = 14;

    static Stream<CdStagePayload> pastStages() {
        return Arrays.stream(CdStagePayload.values()).filter(p -> p.stage().period() == CdPeriod.PAST);
    }

    /**
     * Engine property, every Past stage (seven zones, acts 1 and 2): a direct load leaves {@code PCMM_PAST} queued in
     * the stage's own driver, and two seconds later the driver is playing a song that is heard.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("pastStages")
    void directLoadEndsWithTheDriverPlayingItsSong(CdStagePayload payload) throws Exception {
        try (Session session = Session.boot(payload)) {
            session.assertPastMusicQueuedInTheStageDriver(payload);
            List<short[]> frames = session.present(120);
            session.assertSongPlaying(payload);
            assertTrue(audible(frames.subList(60, 120)), payload + ": the Past music is heard");
        }
    }

    /** Stage select from one zone's Past to another's: the new stage's bank, then its song. */
    @Test
    void aDirectLoadFromAnotherZonesPastPlaysTheNewBank() throws Exception {
        try (Session session = Session.boot(CdStagePayload.R11B)) {
            session.present(30);
            GameServices.level().loadZoneAndAct(SonicCdZoneRegistry.zoneIndex(CdStagePayload.R31B),
                    SonicCdZoneRegistry.actIndex(CdStagePayload.R31B));
            session.assertPastMusicQueuedInTheStageDriver(CdStagePayload.R31B);
            List<short[]> frames = session.present(120);
            session.assertSongPlaying(CdStagePayload.R31B);
            assertTrue(audible(frames.subList(60, 120)), "R31B's Past music is heard after R11B");
        }
    }

    /**
     * The other two ways into a Past stage keep the loaded driver and queue the song again: a death restart re-enters
     * the stage's start in the same program ({@code .DoReset}, Level/Main.asm:388-395; {@code .NotPaused},
     * :397-408), and a time warp runs the same zone's Past
     * load command, whose driver is already loaded (CD Initial Program/IPX.asm:546-571; SPX.asm:762-764). The warp's
     * own program and fades are not run here: this is the load's part of it, a Present stage then its Past.
     */
    @Test
    void presentToPastAndDeathRestartKeepTheDriverAndQueueTheSongAgain() throws Exception {
        try (Session session = Session.boot(CdStagePayload.R11A)) {
            session.present(30);
            CdPcmDriver presentDriver = session.subCpu().pcmDriver();
            GameServices.level().loadZoneAndAct(SonicCdZoneRegistry.zoneIndex(CdStagePayload.R11B),
                    SonicCdZoneRegistry.actIndex(CdStagePayload.R11B));
            session.assertPastMusicQueuedInTheStageDriver(CdStagePayload.R11B);
            assertTrue(presentDriver == session.subCpu().pcmDriver(), "the same zone's Past keeps the loaded driver");
            session.present(120);
            session.assertSongPlaying(CdStagePayload.R11B);

            CdPcmDriver pastDriver = session.subCpu().pcmDriver();
            GameServices.level().restartCurrentLevelAfterDeath();
            session.assertPastMusicQueuedInTheStageDriver(CdStagePayload.R11B);
            assertTrue(pastDriver == session.subCpu().pcmDriver(), "a restart keeps the loaded driver");
            List<short[]> frames = session.present(120);
            session.assertSongPlaying(CdStagePayload.R11B);
            assertTrue(audible(frames.subList(60, 120)), "R11B's Past music is heard after the restart");
        }
    }

    /**
     * Against native, one Past stage per zone. Native: {@code SCMD_PASTMUS} is written on the frame after
     * {@code LevelStart} sets {@code gameMode} bit 7 (Level/Main.asm:56, 147-154); {@code SubCPUCmd} then waits until
     * the sub CPU echoes the command in GACOMSTAT0, then clears GACOMCMD0 (Level/Sub CPU.asm:21-43), which the probe logs as
     * a second write of the same word. The sub CPU echoes only after the handler ({@code SPCmdFinish} after the
     * {@code .SPCmds} call, CD System Program/SPX.asm:310-326, 2505-2515), so {@code SPCmd_PlayPastMus} has written the
     * queue by then (SPX.asm:1612-1614). That
     * receipt comes seven frames after the write on every recording. The engine's sub-CPU commands are synchronous
     * ({@link CdSubCpuAudio}), so its command is that receipt, and the load presents no frames, so the first frame it
     * presents after the load is the first after the receipt.
     *
     * <p>The first sounding sample comes when the driver's next timer run takes the queue byte ({@code UpdateDriver},
     * PCM/_Driver.asm:40-57), one run every 510 chip samples (15.67 ms). The native timer is free-running from the
     * driver load (the system program extension's start, SPX.asm:303-305, points the level 3 timer at the driver
     * with interval 255; the load command's end enables it again, :771-772), so its phase against the receipt
     * varies by up to one run; the engine's starts at the driver load. The engine's onset after its command must therefore lie within one driver run of native's onset after the start
     * of the receipt frame.
     */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"R11B", "R31B", "R41B", "R51B", "R61B", "R71B", "R81B"})
    void firstSoundingSampleAfterTheCommandIsWithinOneDriverRunOfNatives(String name) throws Exception {
        CdStagePayload payload = CdStagePayload.valueOf(name);
        NativeStart nativeStart = NativeStart.load(payload);
        assertEquals(nativeStart.levelStart() + 1, nativeStart.command(),
                name + ": native writes SCMD_PASTMUS one V-sync into LevelStart");
        double engineSeconds;
        int engineFrame;
        Runnable queuedAtLoadEnd;
        try (Session session = Session.boot(payload)) {
            CdSubCpuAudio.PcmState atLoadEnd = session.subCpu().state().pcm();
            queuedAtLoadEnd = () -> Session.assertPastMusicQueued(payload, atLoadEnd);
            List<short[]> frames = session.present(40);
            engineFrame = firstAudibleFrame(frames);
            long samples = 0;
            for (int i = 0; i < engineFrame; i++) samples += frames.get(i).length / 2;
            short[] first = frames.get(Math.max(engineFrame, 0));
            int k = 0;
            while (k < first.length && Math.abs(first[k]) <= AUDIBLE) k++;
            engineSeconds = engineFrame < 0 ? Double.NaN : (samples + k / 2) / (double) session.sampleRate();
        }
        double tick = CdPcmDriver.CHIP_SAMPLES_PER_TICK * (double) CdPcmDriver.CHIP_RATE_DENOMINATOR
                / CdPcmDriver.CHIP_RATE_NUMERATOR;
        System.out.printf("[past-music] %s native: LevelStart %d, command %d, receipt %d, first sound frame %d "
                        + "(%.2f ms after the receipt frame starts); engine: frame +%d (%.2f ms after its command); run %.2f ms%n",
                name, nativeStart.levelStart(), nativeStart.command(), nativeStart.receipt(), nativeStart.firstSound(),
                nativeStart.onsetSeconds() * 1000, engineFrame, engineSeconds * 1000, tick * 1000);
        assertTrue(engineFrame >= 0, name + ": the engine's Past music sounds after a direct load");
        assertTrue(Math.abs(engineSeconds - nativeStart.onsetSeconds()) < tick,
                String.format("%s: engine onset %.2f ms after its command, native %.2f ms after the receipt frame",
                        name, engineSeconds * 1000, nativeStart.onsetSeconds() * 1000));
        queuedAtLoadEnd.run();
    }

    private static int firstAudibleFrame(List<short[]> frames) {
        for (int i = 0; i < frames.size(); i++) {
            if (audible(List.of(frames.get(i)))) return i;
        }
        return -1;
    }

    private static boolean audible(List<short[]> frames) {
        for (short[] frame : frames) {
            for (short s : frame) if (Math.abs(s) > AUDIBLE) return true;
        }
        return false;
    }

    /**
     * Native frames: {@code LevelStart}'s first frame, the {@code SCMD_PASTMUS} write, its receipt (the second write of
     * the same word) and the first sounding frame, with the onset in seconds after the receipt frame's first sample.
     */
    record NativeStart(int levelStart, int command, int receipt, int firstSound, double onsetSeconds) {
        static NativeStart load(CdStagePayload payload) throws IOException {
            Path dir = CdAudioTestMedia.nativeDir("aud1-fixtures/" + payload + "-audio");
            List<String> rows = Files.readAllLines(dir.resolve("frames.csv"));
            List<String> commands = Files.readAllLines(dir.resolve("subcmd.csv"));
            int loadCommand = payload.loadCommand().id();
            int load = -1;
            for (String line : commands.subList(1, commands.size())) {
                String[] f = line.split(",");
                if (Integer.parseInt(f[1], 16) == loadCommand) load = Integer.parseInt(f[0]);
            }
            assertTrue(load > 0, payload + ": native sent the stage's load command");
            int levelStart = -1;
            for (String row : rows.subList(1, rows.size())) {
                String[] f = row.split(",");
                if (Integer.parseInt(f[0]) >= load && (Integer.parseInt(f[5]) & 0x80) != 0) {
                    levelStart = Integer.parseInt(f[0]);
                    break;
                }
            }
            List<Integer> writes = new ArrayList<>();
            for (String line : commands.subList(1, commands.size())) {
                String[] f = line.split(",");
                if (Integer.parseInt(f[0]) >= levelStart && Integer.parseInt(f[1], 16) == PAST_MUSIC) {
                    writes.add(Integer.parseInt(f[0]));
                }
            }
            assertTrue(levelStart > 0 && writes.size() >= 2, payload + ": native LevelStart, SCMD_PASTMUS and its receipt");
            int command = writes.get(0), receipt = writes.get(1);
            ByteBuffer raw = ByteBuffer.wrap(Files.readAllBytes(dir.resolve("audio.raw"))).order(ByteOrder.LITTLE_ENDIAN);
            long sample = 0, receiptStart = -1, onset = -1;
            int firstSound = -1;
            for (int frame = 1; frame < rows.size() && firstSound < 0; frame++) {
                int count = Integer.parseInt(rows.get(frame).split(",")[1]);
                if (frame == receipt) receiptStart = sample;
                if (frame >= command) {
                    for (int i = 0; i < count * 2; i++) {
                        if (Math.abs(raw.getShort((int) ((sample * 2 + i) * 2))) > AUDIBLE) {
                            firstSound = frame;
                            onset = sample + i / 2;
                            break;
                        }
                    }
                }
                sample += count;
            }
            assertTrue(firstSound >= receipt, payload + ": native Past music sounds only after the receipt");
            return new NativeStart(levelStart, command, receipt, firstSound, (onset - receiptStart) / 44_100.0);
        }
    }

    /** One direct stage load in its own headless boot, with audio on and the presentation drained per frame. */
    private static final class Session implements AutoCloseable {
        private final CdDiscDataSource disc;
        private final HeadlessGameBoot boot;
        private final GameLoop loop;
        private final LiveCaptureAudioHandle capture;
        private final boolean oldAudio;

        private Session(CdDiscDataSource disc, HeadlessGameBoot boot, GameLoop loop, LiveCaptureAudioHandle capture,
                        boolean oldAudio) {
            this.disc = disc;
            this.boot = boot;
            this.loop = loop;
            this.capture = capture;
            this.oldAudio = oldAudio;
        }

        static Session boot(CdStagePayload payload) throws Exception {
            boolean oldAudio = GameServices.configuration().getBoolean(SonicConfiguration.AUDIO_ENABLED);
            GameServices.configuration().setSessionOverride(SonicConfiguration.AUDIO_ENABLED, true);
            CdDiscDataSource disc = CdDiscDataSource.open(CdAudioTestMedia.usaCue());
            HeadlessGameBoot boot = new HeadlessGameBoot(320, 224);
            GameLoop loop = boot.boot(disc, new SonicCdGameModule(), SonicCdZoneRegistry.zoneIndex(payload),
                    SonicCdZoneRegistry.actIndex(payload));
            var audio = GameServices.audio();
            return new Session(disc, boot, loop, audio.beginLiveCaptureAudio(audio.presentationFrameRate()), oldAudio);
        }

        int sampleRate() {
            return capture.sampleRate();
        }

        CdSubCpuAudio subCpu() {
            return ((SonicCdAudioProfile) GameServices.audio().getAudioProfile()).subCpuAudio();
        }

        void assertPastMusicQueuedInTheStageDriver(CdStagePayload payload) {
            assertPastMusicQueued(payload, subCpu().state().pcm());
        }

        static void assertPastMusicQueued(CdStagePayload payload, CdSubCpuAudio.PcmState pcm) {
            assertNotNull(pcm, payload + ": a PCM driver is loaded");
            assertEquals(payload.pcmDriver().isoName(), pcm.bank(), payload + ": the stage's own driver");
            assertEquals(PCMM_PAST, pcm.driver().vars()[QUEUE_SLOT],
                    payload + ": PCMM_PAST waits in the loaded driver's queue when the load ends");
        }

        void assertSongPlaying(CdStagePayload payload) {
            CdPcmDriver driver = subCpu().pcmDriver();
            int playing = 0;
            for (int t = 0; t < 9; t++) {
                if ((driver.songTrack(t).flags & 0x80) != 0) playing++;
            }
            assertTrue(playing > 0, payload + ": the driver's song tracks are playing");
        }

        /** Steps and presents {@code frames} frames, returning each frame's drained packet. */
        List<short[]> present(int frames) {
            List<short[]> out = new ArrayList<>();
            short[] packet = new short[capture.maxStereoFramesPerPacket() * 2];
            for (int i = 0; i < frames; i++) {
                loop.step();
                HeadlessGameBoot.presentHeadlessOuterAudioFrame();
                int n = capture.drainPresentationFrame(packet);
                out.add(Arrays.copyOf(packet, n * 2));
            }
            return out;
        }

        @Override
        public void close() throws Exception {
            try {
                capture.close();
                boot.close();
                disc.close();
            } finally {
                GameServices.configuration().setSessionOverride(SonicConfiguration.AUDIO_ENABLED, oldAudio);
            }
        }
    }
}
