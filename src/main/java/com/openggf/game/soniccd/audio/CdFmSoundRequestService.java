package com.openggf.game.soniccd.audio;

import com.openggf.audio.presentation.AudioPresentationCommandResolver.AppliedOutcome;
import com.openggf.audio.presentation.AudioPresentationCommandResolver.OutcomeReservation;
import com.openggf.audio.presentation.AudioPresentationCommandResolver.OutcomeSeal;
import com.openggf.audio.presentation.AudioPresentationForwardService;
import com.openggf.audio.presentation.AudioRequestService;
import com.openggf.audio.presentation.GamePcmContributor;
import com.openggf.audio.rewind.AudioCommand;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntUnaryOperator;

/**
 * Sonic CD's sound request path, from the stage code's request to the driver's admission.
 *
 * <p>FM effects: {@code PlayFMSound} puts the ID in the first empty main-CPU queue byte and
 * drops it when all three are full (Level/Functions (General).asm:288-310). V-int's
 * {@code UpdateFMQueues} copies queue 1 to the driver's {@code sndPlay} directly and queues 2
 * and 3 to its first two {@code sndQueues} slots (Level/Functions (General).asm:316-345; addresses, _Include/Sound.inc:16-18).
 * The Z80's {@code ProcSoundQueue} moves a slot into {@code sndPlay} when its priority is at
 * least {@code curPriority}, stopping at the first slot without a sound ID
 * (FM/_Driver.asm:1001-1041), and each Timer B tick plays only {@code sndPlay}
 * ({@code PlaySoundID}, FM/_Driver.asm:774-783). So one effect starts per tick: a second request in the same
 * frame replaces the first when its priority allows. Any track's {@code TrkCmd_Stop} clears
 * {@code curPriority} (TrkCmd_Stop, FM/_Driver.asm:1720-1734). One video frame stands for one driver tick here; the driver
 * runs 59.45 ticks for 59.92 frames per second, so about one frame in 128 has no tick natively.
 *
 * <p>Sub-CPU commands (music, PCM, CD audio) arrive as music requests and go to
 * {@link CdSubCpuAudio}, whose output this service contributes to the presentation mix.
 */
public final class CdFmSoundRequestService implements AudioRequestService, GamePcmContributor {
    private static final int IDLE = 0x80;
    /** {@code FMC_STOP} (FM/_Sound IDs.inc:93-94); every ID from it up reaches StopSound. */
    private static final int STOP = 0xE0;

    public record Snapshot(int queue1, int queue2, int queue3, int sndPlay,
                           int slot1, int slot2, int slot3, int curPriority)
            implements AudioPresentationForwardService.Snapshot {
        static final Snapshot POWER_ON = new Snapshot(0, 0, 0, IDLE, 0, 0, 0, 0);
    }

    /** The pipeline plus the driver's ring byte, as one rewind and rollback unit. */
    public record ServiceSnapshot(Snapshot pipeline, int ringChannel, int trackQueued)
            implements AudioPresentationForwardService.Snapshot { }

    private final IntUnaryOperator priorities;
    private final CdSubCpuAudio subCpu;
    private final CdFmDriverState driver;
    private Snapshot state = Snapshot.POWER_ON;
    private boolean forwardOpen;

    /**
     * @param priorities native priority byte by sound ID ({@link CdFmSoundAssets#nativePriorityByte})
     * @param subCpu     the sub CPU's audio owner
     */
    public CdFmSoundRequestService(IntUnaryOperator priorities, CdSubCpuAudio subCpu, CdFmDriverState driver) {
        this.priorities = Objects.requireNonNull(priorities, "priorities");
        this.subCpu = Objects.requireNonNull(subCpu, "subCpu");
        this.driver = Objects.requireNonNull(driver, "driver");
    }

    @Override
    public void submitMusic(int nativeRequestId, AudioCommand command) {
        subCpu.submit(nativeRequestId);
    }

    @Override
    public void submitSound(int nativeRequestId, AudioCommand command) {
        int id = nativeRequestId & 0xFF;
        Snapshot s = state;
        if (s.queue1() == 0) {
            state = with(s, id, s.queue2(), s.queue3());
        } else if (s.queue2() == 0) {
            state = with(s, s.queue1(), id, s.queue3());
        } else if (s.queue3() == 0) {
            state = with(s, s.queue1(), s.queue2(), id);
        }
    }

    private static Snapshot with(Snapshot s, int q1, int q2, int q3) {
        return new Snapshot(q1, q2, q3, s.sndPlay(), s.slot1(), s.slot2(), s.slot3(), s.curPriority());
    }

    public Snapshot pipelineState() {
        return state;
    }

    @Override
    public AudioPresentationForwardService.Snapshot snapshot() {
        return new ServiceSnapshot(state, driver.ringChannel(), driver.trackQueued());
    }

    @Override
    public void restore(AudioPresentationForwardService.Snapshot snapshot) {
        if (!(snapshot instanceof ServiceSnapshot selected)) {
            throw new IllegalArgumentException("snapshot does not belong to the Sonic CD request service");
        }
        if (forwardOpen) {
            throw new IllegalStateException("cannot restore during an open forward boundary");
        }
        state = selected.pipeline();
        driver.setRingChannel(selected.ringChannel());
        driver.setTrackQueued(selected.trackQueued());
    }

    @Override
    public void bindOutputRate(int sampleRate) {
        subCpu.bindOutputRate(sampleRate);
    }

    @Override
    public void render(short[] target, int stereoFrames) {
        subCpu.render(target, stereoFrames);
    }

    /**
     * The FM effects already take the session's sound-effect gain: the FM driver has no songs, its
     * {@code PlaySoundID} plays an effect or stops (FM/_Driver.asm:774-782), so every FM channel is
     * driven by an effect track. The sub CPU's disc audio and PCM chip take their gains here.
     */
    @Override
    public void setMixGains(int musicGain, int sfxGain) {
        subCpu.setMixGains(musicGain, sfxGain);
    }

    @Override
    public ForwardBoundary beginForwardBoundary() {
        if (forwardOpen) {
            throw new IllegalStateException("Sonic CD request boundary is already open");
        }
        forwardOpen = true;
        return new ForwardBoundary(new ServiceSnapshot(state, driver.ringChannel(), driver.trackQueued()));
    }

    /** One V-int queue transfer, queue scan and driver tick. */
    static Dispatch service(Snapshot s, IntUnaryOperator priorities) {
        int sndPlay = s.queue1() != 0 ? s.queue1() : s.sndPlay();
        int[] slots = {s.queue2() != 0 ? s.queue2() : s.slot1(), s.queue3() != 0 ? s.queue3() : s.slot2(), s.slot3()};
        int priority = s.curPriority();
        for (int i = 0; i < 3; i++) {
            int id = slots[i];
            // .ProcSlot returns without clearing or advancing on a byte without bit 7 or below
            // FM_START, so the remaining calls read the same slot (.ProcSlot, FM/_Driver.asm:1012-1018).
            if ((id & 0x80) == 0 || id < CdFmSmpsLoader.FIRST_SFX) {
                break;
            }
            int declared = priorities.applyAsInt(id);
            if (priority <= declared) {
                sndPlay = id;
                priority = declared & 0x7F;
            }
            slots[i] = 0;
        }
        AudioCommand consequence = null;
        if ((sndPlay & 0x80) == 0 || sndPlay >= STOP) {
            // StopSound clears sndPlay through VariablesEnd, curPriority included (StopSound, FM/_Driver.asm:945-972).
            consequence = new AudioCommand.StopAllSfx();
            return new Dispatch(new Snapshot(0, 0, 0, IDLE, 0, 0, 0, 0), consequence);
        }
        if (sndPlay >= CdFmSmpsLoader.FIRST_SFX) {
            consequence = new AudioCommand.PlaySfx(sndPlay, null, AudioCommand.SfxRoute.BASE_SMPS_ID, 1.0f, null);
            // PlaySFX ends at ResetSoundQueue (FM/_Driver.asm:863-866); 80h-8Fh return early and stay.
            sndPlay = IDLE;
        }
        return new Dispatch(new Snapshot(0, 0, 0, sndPlay, slots[0], slots[1], slots[2], priority), consequence);
    }

    record Dispatch(Snapshot after, AudioCommand consequence) { }

    public final class ForwardBoundary implements AudioPresentationForwardService.ForwardBoundary {
        private final ServiceSnapshot before;
        private AudioCommand staged;
        private OutcomeReservation reservation;
        private AppliedOutcome applied;
        private boolean serviced;
        private boolean prepared;
        private boolean closed;
        private CommittedReceipt receipt;

        private ForwardBoundary(ServiceSnapshot before) {
            this.before = before;
        }

        @Override
        public void service(Consumer<AudioCommand> commandSink) {
            requireOpen();
            if (serviced) throw new IllegalStateException("Sonic CD request boundary was already serviced");
            serviced = true;
            Snapshot s = state;
            if (driver.trackQueued() != 0) {
                // A track's write to sndQueues (TrkCmd_SwapRingChn) lands before the next V-int
                // copies the main-CPU queues over the same slots (UpdateFMQueues, Level/Functions (General).asm:316-345).
                s = new Snapshot(s.queue1(), s.queue2(), s.queue3(), s.sndPlay(), driver.trackQueued(),
                        s.slot2(), s.slot3(), s.curPriority());
                driver.setTrackQueued(0);
            }
            Dispatch dispatch = CdFmSoundRequestService.service(s, priorities);
            state = dispatch.after();
            if (dispatch.consequence() != null) {
                staged = dispatch.consequence();
                commandSink.accept(staged);
            }
        }

        @Override
        public void reserveOutcome(OutcomeReservation candidate) {
            requireOpen();
            if (!serviced || staged == null || reservation != null) {
                throw new IllegalStateException("request boundary cannot reserve this outcome");
            }
            reservation = Objects.requireNonNull(candidate, "reservation");
        }

        @Override
        public void applyOutcome(AppliedOutcome outcome) {
            requireOpen();
            if (!serviced || staged == null || applied != null || reservation == null
                    || !Objects.requireNonNull(outcome, "outcome").belongsTo(reservation)) {
                throw new IllegalStateException("applied outcome does not belong to this request boundary");
            }
            applied = outcome;
        }

        @Override
        public void onSfxTrackStop() {
            requireOpen();
            Snapshot s = state;
            state = new Snapshot(s.queue1(), s.queue2(), s.queue3(), s.sndPlay(), s.slot1(), s.slot2(), s.slot3(), 0);
        }

        @Override
        public void prepareCommit() {
            requireOpen();
            if (prepared) throw new IllegalStateException("request boundary is already prepared");
            if (!serviced || (staged != null && (reservation == null || applied == null))) {
                throw new IllegalStateException("request boundary consequences are not prepared");
            }
            receipt = new CommittedReceipt(applied, applied == null ? null : applied.seal(reservation));
            prepared = true;
        }

        @Override
        public CommittedReceipt commit() {
            requireOpen();
            if (!prepared) throw new IllegalStateException("cannot commit an unprepared Sonic CD request boundary");
            closed = true;
            forwardOpen = false;
            return receipt;
        }

        @Override
        public void publishDiagnostics(AudioPresentationForwardService.CommittedReceipt committed) {
            if (committed != receipt) throw new IllegalArgumentException("receipt does not belong to Sonic CD");
        }

        @Override
        public void rollback() {
            requireOpen();
            state = before.pipeline();
            driver.setRingChannel(before.ringChannel());
            driver.setTrackQueued(before.trackQueued());
            closed = true;
            forwardOpen = false;
        }

        private void requireOpen() {
            if (closed || !forwardOpen) throw new IllegalStateException("Sonic CD request boundary is closed");
        }
    }

    public static final class CommittedReceipt implements AudioPresentationForwardService.CommittedReceipt {
        private final List<AppliedOutcome> outcomes;
        private final OutcomeSeal seal;

        private CommittedReceipt(AppliedOutcome outcome, OutcomeSeal seal) {
            this.outcomes = outcome == null ? List.of() : List.of(outcome);
            this.seal = seal;
        }

        @Override
        public OutcomeSeal sealFor(AppliedOutcome outcome) {
            return outcomes.stream().anyMatch(candidate -> candidate == outcome) ? seal : null;
        }
    }
}
