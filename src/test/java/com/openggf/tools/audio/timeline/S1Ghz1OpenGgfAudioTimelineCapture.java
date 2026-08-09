package com.openggf.tools.audio.timeline;

import static com.openggf.tools.audio.timeline.S1GameplayAudioTimeline.HardwareRole.FM3;
import static com.openggf.tools.audio.timeline.S1GameplayAudioTimeline.HardwareRole.FM4;
import static com.openggf.tools.audio.timeline.S1GameplayAudioTimeline.HardwareRole.FM5;
import static com.openggf.tools.audio.timeline.S1GameplayAudioTimeline.HardwareRole.PSG1;
import static com.openggf.tools.audio.timeline.S1GameplayAudioTimeline.HardwareRole.PSG2;
import static com.openggf.tools.audio.timeline.S1GameplayAudioTimeline.HardwareRole.PSG3;
import static com.openggf.tools.audio.timeline.S1GameplayAudioTimeline.OwnerClass.MUSIC;
import static com.openggf.tools.audio.timeline.S1GameplayAudioTimeline.OwnerClass.NORMAL_SFX;
import static com.openggf.tools.audio.timeline.S1GameplayAudioTimeline.OwnerClass.SPECIAL_SFX;
import static com.openggf.tools.audio.timeline.S1GameplayAudioTimeline.SoundClass.SFX;

import com.openggf.audio.AudioManager;
import com.openggf.audio.driver.SfxContentionObserver;
import com.openggf.audio.driver.SmpsDriver;
import com.openggf.audio.presentation.AudioPresentationSnapshot;
import com.openggf.audio.presentation.AudioVoiceRegistry;
import com.openggf.audio.presentation.PresentationVoice;
import com.openggf.audio.presentation.SmpsCompositeVoice;
import com.openggf.audio.rewind.AudioCommand;
import com.openggf.audio.rewind.AudioTimelineEntry;
import com.openggf.audio.rewind.SmpsDriverSnapshot;
import com.openggf.audio.rewind.SmpsTrackSnapshot;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.game.GameServices;
import com.openggf.tests.trace.runs.VisualRunReplayHarness;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

/**
 * Local-only OpenGGF producer for the pinned S1 GHZ1 semantic timeline. The
 * capture is deliberately observational: it drives the normal visual replay
 * path and serializes what that path naturally presented.
 */
public final class S1Ghz1OpenGgfAudioTimelineCapture {
    private S1Ghz1OpenGgfAudioTimelineCapture() { }

    public static void capture(Path runDirectory, Path output) throws Exception {
        CaptureState state = new CaptureState();
        try {
            VisualRunReplayHarness.replayAudio(runDirectory,
                    VisualRunReplayHarness.stopAfterSegmentBody(0), state);
            S1GameplayAudioTimelineJsonl.writeNew(output, metadata(), state.records().iterator());
        } finally {
            state.detach();
            VisualRunReplayHarness.tearDown();
        }
    }

    private static S1GameplayAudioTimeline.Metadata metadata() {
        return new S1GameplayAudioTimeline.Metadata(S1GameplayAudioTimeline.SCHEMA,
                S1GameplayAudioTimeline.OPENGGF_CAPTURE,
                S1GameplayAudioTimeline.S1_REV01_SHA1,
                S1GameplayAudioTimeline.S1_REV01_CRC32,
                S1GameplayAudioTimeline.BK2_SHA256,
                S1GameplayAudioTimeline.OPENGGF_PRODUCER,
                S1GameplayAudioTimeline.SEGMENT_START_BK2_FRAME,
                S1GameplayAudioTimeline.SEGMENT_END_BK2_FRAME,
                S1GameplayAudioTimeline.SEGMENT_FRAME_COUNT);
    }

    private static final class CaptureState implements VisualRunReplayHarness.FrameObserver,
            SfxContentionObserver {
        private final List<S1GameplayAudioTimeline.TimelineRecord> records = new ArrayList<>();
        private final List<SfxContentionObserver.Arbitration> arbitrations = new ArrayList<>();
        private final List<SfxContentionObserver.Admission> admissions = new ArrayList<>();
        private final Set<Long> consumedAdmissions = new HashSet<>();
        private final List<SmpsDriver> observedDrivers = new ArrayList<>();
        private int nextCommandEntry;
        private long diagnosticPresentations;
        private long emittedRequestCount;
        private long lastRequestOrdinal = -1;
        private boolean baselineCaptured;

        @Override
        public void beforeFirstSegmentRow(VisualRunReplayHarness.FrameView frame) {
            if (frame.consumedBk2Cursor() != S1GameplayAudioTimeline.SEGMENT_START_BK2_FRAME) {
                throw new IllegalStateException("GHZ1 baseline was not sampled before BK2 row 860");
            }
            installOnLiveDrivers();
            AudioManager audio = GameServices.audio();
            AudioPresentationSnapshot presentation = audio.captureLogicalSnapshot().presentation();
            int activeMusic = presentation.activeMusic() == null ? -1 : presentation.activeMusic().musicId();
            records.add(new S1GameplayAudioTimeline.Baseline(frame.consumedBk2Cursor(), activeMusic,
                    diagnosticPresentations, owners(presentation)));
            nextCommandEntry = audio.commandTimeline().entryCount();
            baselineCaptured = true;
        }

        @Override
        public void afterOuterFrame(VisualRunReplayHarness.FrameView frame) {
            diagnosticPresentations++;
            if (!baselineCaptured || !frame.semanticRow() || frame.segmentIndex() != 0) {
                return;
            }
            if (frame.consumedBk2Cursor() < S1GameplayAudioTimeline.SEGMENT_START_BK2_FRAME
                    || frame.consumedBk2Cursor() >= S1GameplayAudioTimeline.SEGMENT_END_BK2_FRAME) {
                return;
            }
            installOnLiveDrivers();
            AudioManager audio = GameServices.audio();
            List<S1GameplayAudioTimeline.Request> requests = drainRequests(audio);
            AudioPresentationSnapshot presentation = audio.captureLogicalSnapshot().presentation();
            records.add(new S1GameplayAudioTimeline.Frame(frame.consumedBk2Cursor(),
                    diagnosticPresentations, requests, owners(presentation)));
        }

        @Override
        public void onSfxAdmitted(SfxContentionObserver.Admission admission) {
            admissions.add(admission);
        }

        @Override
        public void onRoleArbitrated(SfxContentionObserver.Arbitration arbitration) {
            arbitrations.add(arbitration);
        }

        List<S1GameplayAudioTimeline.TimelineRecord> records() {
            if (!baselineCaptured || records.size() != S1GameplayAudioTimeline.SEGMENT_FRAME_COUNT + 1) {
                throw new IllegalStateException("capture did not produce exactly 4,115 semantic GHZ1 frames");
            }
            records.add(new S1GameplayAudioTimeline.Terminal(S1GameplayAudioTimeline.SEGMENT_FRAME_COUNT,
                    emittedRequestCount, S1GameplayAudioTimeline.SEGMENT_FRAME_COUNT + 1));
            return List.copyOf(records);
        }

        void detach() {
            for (SmpsDriver driver : observedDrivers) {
                driver.setSfxContentionObserver(SfxContentionObserver.NONE);
            }
        }

        private List<S1GameplayAudioTimeline.Request> drainRequests(AudioManager audio) {
            int end = audio.commandTimeline().entryCount();
            List<S1GameplayAudioTimeline.Request> requests = new ArrayList<>();
            for (int entryIndex = nextCommandEntry; entryIndex < end; entryIndex++) {
                AudioTimelineEntry entry = audio.commandTimeline().entryAt(entryIndex);
                if (entry.command() instanceof AudioCommand.PlaySfx sfx && sfx.sfxId() >= 0) {
                    requests.add(requestFor(SFX, sfx.sfxId(), takeAdmission(sfx.sfxId())));
                } else if (entry.command() instanceof AudioCommand.PlayMusic music) {
                    requests.add(requestFor(S1GameplayAudioTimeline.SoundClass.MUSIC, music.musicId(), null));
                }
            }
            nextCommandEntry = end;
            return List.copyOf(requests);
        }

        private S1GameplayAudioTimeline.Request requestFor(S1GameplayAudioTimeline.SoundClass soundClass,
                int soundId, SfxContentionObserver.Admission admission) {
            SfxContentionObserver.Source source = admission == null ? null : admission.source();
            long ordinal = source == null ? lastRequestOrdinal + 1 : source.admissionOrdinal();
            if (ordinal <= lastRequestOrdinal) {
                ordinal = lastRequestOrdinal + 1;
            }
            lastRequestOrdinal = ordinal;
            emittedRequestCount++;
            List<S1GameplayAudioTimeline.HardwareRole> roles = declaredRoles(admission, soundId, soundClass);
            List<S1GameplayAudioTimeline.RoleArbitration> decisions = new ArrayList<>();
            for (S1GameplayAudioTimeline.HardwareRole role : roles) {
                SfxContentionObserver.Arbitration event = latestArbitration(role, source);
                boolean acquired = event != null && event.acquired();
                S1GameplayAudioTimeline.OwnerRef displaced = event == null
                        ? musicOwner() : owner(event.previousOwner());
                S1GameplayAudioTimeline.OwnerRef finalOwner = acquired
                        ? new S1GameplayAudioTimeline.OwnerRef(ownerClass(soundClass, soundId), soundId, ordinal)
                        : displaced;
                decisions.add(new S1GameplayAudioTimeline.RoleArbitration(role, acquired,
                        displaced, finalOwner));
            }
            return new S1GameplayAudioTimeline.Request(ordinal, soundClass, soundId, roles, decisions);
        }

        private List<S1GameplayAudioTimeline.HardwareRole> declaredRoles(SfxContentionObserver.Admission admission, int soundId,
                S1GameplayAudioTimeline.SoundClass soundClass) {
            if (admission != null) {
                return admission.declaredRoles().stream().map(role -> role(role.bus(), role.channel()))
                        .filter(java.util.Objects::nonNull).distinct().toList();
            }
            for (SmpsDriver driver : observedDrivers) {
                SmpsDriverSnapshot snapshot = driver.captureSnapshot();
                for (SmpsDriverSnapshot.SequencerEntry entry : snapshot.sequencers()) {
                    if (entry.sfx() == (soundClass == SFX) && entry.source().id() == soundId) {
                        List<S1GameplayAudioTimeline.HardwareRole> roles = new ArrayList<>();
                        for (SmpsTrackSnapshot track : entry.snapshot().tracks()) {
                            S1GameplayAudioTimeline.HardwareRole role = role(track.type(), track.channelId());
                            if (role != null && !roles.contains(role)) {
                                roles.add(role);
                            }
                        }
                        if (!roles.isEmpty()) {
                            return List.copyOf(roles);
                        }
                    }
                }
            }
            throw new IllegalStateException("no declared SMPS role for sound $%02X".formatted(soundId));
        }

        private SfxContentionObserver.Arbitration latestArbitration(
                S1GameplayAudioTimeline.HardwareRole role,
                SfxContentionObserver.Source source) {
            for (int index = arbitrations.size() - 1; index >= 0; index--) {
                SfxContentionObserver.Arbitration event = arbitrations.get(index);
                if (role(event.bus(), event.channel()) == role
                        && (source == null || source.equals(event.challenger()))) {
                    return event;
                }
            }
            return null;
        }

        private S1GameplayAudioTimeline.OwnerVector owners(AudioPresentationSnapshot presentation) {
            Map<S1GameplayAudioTimeline.HardwareRole, S1GameplayAudioTimeline.OwnerRef> owners =
                    new EnumMap<>(S1GameplayAudioTimeline.HardwareRole.class);
            for (S1GameplayAudioTimeline.HardwareRole role : S1GameplayAudioTimeline.HardwareRole.values()) {
                owners.put(role, musicOwner());
            }
            for (var voice : presentation.voices()) {
                if (voice instanceof com.openggf.audio.presentation.PresentationVoiceSnapshot.Smps smps) {
                    applyLocks(smps.driver(), owners);
                }
            }
            return new S1GameplayAudioTimeline.OwnerVector(owners.get(FM3), owners.get(FM4), owners.get(FM5),
                    owners.get(PSG1), owners.get(PSG2), owners.get(PSG3));
        }

        private void applyLocks(SmpsDriverSnapshot snapshot,
                Map<S1GameplayAudioTimeline.HardwareRole, S1GameplayAudioTimeline.OwnerRef> owners) {
            applyLock(snapshot, snapshot.fmLockSequencerIds(), 2, FM3, owners);
            applyLock(snapshot, snapshot.fmLockSequencerIds(), 3, FM4, owners);
            applyLock(snapshot, snapshot.fmLockSequencerIds(), 4, FM5, owners);
            applyLock(snapshot, snapshot.psgLockSequencerIds(), 0, PSG1, owners);
            applyLock(snapshot, snapshot.psgLockSequencerIds(), 1, PSG2, owners);
            applyLock(snapshot, snapshot.psgLockSequencerIds(), 2, PSG3, owners);
        }

        private void applyLock(SmpsDriverSnapshot snapshot, int[] lockIds, int channel,
                S1GameplayAudioTimeline.HardwareRole role,
                Map<S1GameplayAudioTimeline.HardwareRole, S1GameplayAudioTimeline.OwnerRef> owners) {
            if (channel >= lockIds.length || lockIds[channel] < 0 || lockIds[channel] >= snapshot.sequencers().size()) {
                return;
            }
            SmpsDriverSnapshot.SequencerEntry sequencer = snapshot.sequencers().get(lockIds[channel]);
            SfxContentionObserver.Source source = latestAdmission(sequencer.source().id());
            owners.put(role, source == null ? noneOwner() : owner(source));
        }

        private SfxContentionObserver.Admission takeAdmission(int soundId) {
            for (int index = 0; index < admissions.size(); index++) {
                if (admissions.get(index).source().descriptor().id() == soundId
                        && consumedAdmissions.add(admissions.get(index).source().admissionOrdinal())) return admissions.get(index);
            }
            return null;
        }

        private SfxContentionObserver.Source latestAdmission(int soundId) {
            for (int index = admissions.size() - 1; index >= 0; index--) {
                if (admissions.get(index).source().descriptor().id() == soundId) return admissions.get(index).source();
            }
            return null;
        }

        private void installOnLiveDrivers() {
            try {
                Field field = AudioManager.class.getDeclaredField("shadowRegistry");
                field.setAccessible(true);
                AudioVoiceRegistry registry = (AudioVoiceRegistry) field.get(GameServices.audio());
                if (registry == null) {
                    return;
                }
                for (int index = 0; index < registry.orderedVoiceCount(); index++) {
                    PresentationVoice voice = registry.orderedVoiceAt(index);
                    if (voice instanceof SmpsCompositeVoice composite
                            && !observedDrivers.contains(composite.driver())) {
                        composite.driver().setSfxContentionObserver(this);
                        observedDrivers.add(composite.driver());
                    }
                }
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("audio presentation registry seam moved", failure);
            }
        }

        private static S1GameplayAudioTimeline.HardwareRole role(SfxContentionObserver.Bus bus, int channel) {
            return bus == SfxContentionObserver.Bus.FM ? fmRole(channel) : psgRole(channel);
        }

        private static S1GameplayAudioTimeline.HardwareRole role(SmpsSequencer.TrackType type, int channel) {
            return type == SmpsSequencer.TrackType.FM ? fmRole(channel)
                    : type == SmpsSequencer.TrackType.PSG ? psgRole(channel) : null;
        }

        private static S1GameplayAudioTimeline.HardwareRole fmRole(int channel) {
            return switch (channel) { case 2 -> FM3; case 3 -> FM4; case 4 -> FM5; default -> null; };
        }

        private static S1GameplayAudioTimeline.HardwareRole psgRole(int channel) {
            return switch (channel) { case 0 -> PSG1; case 1 -> PSG2; case 2 -> PSG3; default -> null; };
        }

        private static S1GameplayAudioTimeline.OwnerRef owner(SfxContentionObserver.Source source) {
            return source == null ? musicOwner() : new S1GameplayAudioTimeline.OwnerRef(
                    source.specialSfx() || source.descriptor().id() == 0xD0 ? SPECIAL_SFX : NORMAL_SFX,
                    source.descriptor().id(), source.admissionOrdinal());
        }

        private static S1GameplayAudioTimeline.OwnerRef musicOwner() {
            return new S1GameplayAudioTimeline.OwnerRef(MUSIC, 0x81, 0);
        }

        private static S1GameplayAudioTimeline.OwnerRef noneOwner() {
            return new S1GameplayAudioTimeline.OwnerRef(
                    S1GameplayAudioTimeline.OwnerClass.NONE, 0, -1);
        }

        private static S1GameplayAudioTimeline.OwnerClass ownerClass(
                S1GameplayAudioTimeline.SoundClass soundClass, int soundId) {
            return soundClass == S1GameplayAudioTimeline.SoundClass.MUSIC ? MUSIC
                    : soundId == 0xD0 ? SPECIAL_SFX : NORMAL_SFX;
        }
    }
}
