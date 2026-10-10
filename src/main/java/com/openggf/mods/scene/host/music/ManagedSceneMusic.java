package com.openggf.mods.scene.host.music;

import com.openggf.audio.AudioManager;
import com.openggf.audio.GameAudioProfile;
import com.openggf.audio.presentation.ScenePcmSource;
import com.openggf.audio.session.OwnedSmpsAudioStream;
import com.openggf.audio.session.SmpsDriverSessionConfiguration;
import com.openggf.audio.session.SmpsPhysicalDevice;
import com.openggf.audio.smps.*;
import com.openggf.audio.synth.ChipWriteObserver;
import com.openggf.data.Rom;
import com.openggf.game.BuiltInRomDetectors;
import com.openggf.game.GameId;
import com.openggf.mods.scene.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import java.util.function.Function;
import java.util.function.LongSupplier;

/** Host-owned finite music; only independent ROM synthesis leaves the scene/audio owner. */
public final class ManagedSceneMusic implements SceneMusic, AutoCloseable {
    private final AudioManager audio;
    private final Function<String, Rom> roms;
    private final LongSupplier nanoTime;
    private Prepared cached;
    private Player player;
    private boolean closed;
    private Job pending;
    private ThreadPoolExecutor worker;
    private static final AtomicInteger WORKER_IDS = new AtomicInteger();
    private static final int MAX_FRAMES = 36_000;
    private static final long MAX_PCM_BYTES = 256L * 1024 * 1024;
    private static final int MAX_NOTE_EVENTS = 200_000;

    public ManagedSceneMusic(AudioManager audio, Function<String, Rom> roms) {
        this(audio, roms, System::nanoTime);
    }

    ManagedSceneMusic(AudioManager audio, Function<String, Rom> roms, LongSupplier nanoTime) {
        this.audio = Objects.requireNonNull(audio, "audio");
        this.roms = Objects.requireNonNull(roms, "roms");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
    }

    /** Park audible playback while a retained scene visits an act; keep preparations and jobs. */
    public void suspendPlayback() { if (player != null) player.stop(); }

    @Override public ScenePreparedMusic prepare(String gameId, int musicId, int durationFrames) {
        requireOpen();
        String game = checkedGame(gameId, durationFrames);
        cancelPending();
        if (matches(game, musicId, durationFrames)) return cached;
        Prepared next = load(game, musicId, durationFrames);
        retireCached();
        try {
            Rendered rendered = render(RenderInput.of(next), List.of(new SceneMusicPart(0, 0, 0, false)), true, ignored -> { });
            next.full = rendered.pcm(); next.notes = rendered.notes();
        } catch (RuntimeException | Error failure) {
            next.retire();
            throw failure;
        }
        cached = next;
        return next;
    }

    @Override public SceneMusicPreparation prepareAsync(String gameId, int musicId, int durationFrames) {
        requireOpen();
        String game = checkedGame(gameId, durationFrames);
        discardTerminalJob();
        if (matches(game, musicId, durationFrames)) {
            cancelPending();
            return completed(cached);
        }
        if (pending != null && pending.full && pending.song.game.equals(game)
                && pending.song.id == musicId && pending.song.durationFrames == durationFrames) return pending;
        Prepared next = load(game, musicId, durationFrames);
        cancelPending(); retireCached();
        return submit(new Job(next, List.of(new SceneMusicPart(0, 0, 0, false)), true));
    }

    @Override public SceneMusicPreparation preparePartAsync(ScenePreparedMusic song, List<SceneMusicPart> parts) {
        requireOpen();
        discardTerminalJob();
        Prepared prepared = current(song);
        List<SceneMusicPart> selection = selection(prepared, parts, 0);
        if (player != null) player.stop();
        if (pending != null && !pending.full && pending.song == prepared && pending.parts.equals(selection)) return pending;
        cancelPending();
        if (prepared.masked != null && selection.equals(prepared.parts)) return completed(prepared);
        prepared.masked = null; prepared.parts = null;
        return submit(new Job(prepared, selection, false));
    }

    private String checkedGame(String gameId, int durationFrames) {
        if (durationFrames < 1 || durationFrames > MAX_FRAMES)
            throw new IllegalArgumentException("finite music duration must be 1..36000 NTSC frames");
        int rate = audio.outputSampleRate();
        long samples = (long) durationFrames * rate / 60;
        if (rate <= 0 || samples * 2 * Short.BYTES * 2 > MAX_PCM_BYTES)
            throw new IllegalArgumentException("finite music exceeds the 256 MiB PCM budget at this output rate");
        return Objects.requireNonNull(gameId, "gameId").toLowerCase(Locale.ROOT);
    }

    private boolean matches(String game, int id, int frames) {
        return cached != null && cached.game.equals(game) && cached.id == id && cached.durationFrames == frames;
    }

    private Prepared load(String game, int musicId, int durationFrames) {
        GameAudioProfile profile = profile(game);
        Rom rom = Objects.requireNonNull(roms.apply(game), "requested game ROM is unavailable");
        SmpsLoader loader = profile.createSmpsLoader(rom);
        AbstractSmpsData data = Objects.requireNonNull(loader.loadMusic(musicId), "ROM music ID unavailable");
        DacData dac = Objects.requireNonNull(loader.loadDacData(), "ROM DAC bank unavailable");
        return new Prepared(this, game, musicId, durationFrames, audio.outputSampleRate(), profile, data, dac);
    }

    private Prepared current(ScenePreparedMusic song) {
        if (!(song instanceof Prepared prepared) || prepared.owner != this || prepared != cached)
            throw new IllegalArgumentException("music must be this scene host's current preparation");
        return prepared;
    }

    private static List<SceneMusicPart> selection(Prepared prepared, List<SceneMusicPart> parts, int leadInSamples) {
        List<SceneMusicPart> selection = List.copyOf(parts);
        if (selection.isEmpty() || selection.size() > 128 || selection.getFirst().onsetSamples() != 0
                || leadInSamples < 0 || leadInSamples > prepared.sampleRate * 10)
            throw new IllegalArgumentException("invalid part or lead-in");
        long previous = -1;
        for (SceneMusicPart part : selection) {
            if (part.onsetSamples() <= previous || part.onsetSamples() >= prepared.lengthSamples())
                throw new IllegalArgumentException("part sections must increase within the finite song");
            previous = part.onsetSamples();
        }
        return selection;
    }

    private SceneMusicPreparation completed(Prepared song) {
        return new SceneMusicPreparation() {
            @Override public State state() { return State.READY; }
            @Override public int progressPercent() { return 100; }
            @Override public String error() { return null; }
            @Override public ScenePreparedMusic prepared() { return song; }
            @Override public void cancel() { }
        };
    }

    private Job submit(Job job) {
        if (worker == null) {
            worker = new ThreadPoolExecutor(1, 1, 5, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1), task -> {
                Thread thread = new Thread(task, "openggf-scene-music-" + WORKER_IDS.incrementAndGet());
                thread.setDaemon(true); return thread;
            });
            worker.allowCoreThreadTimeOut(true);
        }
        pending = job;
        job.future = new FutureTask<>(job, null);
        worker.execute(job.future);
        return job;
    }

    private void cancelPending() {
        if (pending != null) pending.cancel();
        pending = null;
    }

    private void discardTerminalJob() {
        if (pending != null && (pending.state() == SceneMusicPreparation.State.CANCELLED
                || pending.state() == SceneMusicPreparation.State.FAILED)) cancelPending();
    }

    private final class Job implements SceneMusicPreparation, Runnable {
        final Prepared song;
        final List<SceneMusicPart> parts;
        final boolean full;
        private RenderInput input;
        private FutureTask<Void> future;
        private volatile State state = State.PREPARING;
        private volatile int progress;
        private volatile String error;
        private Rendered rendered;
        private boolean published;
        Job(Prepared song, List<SceneMusicPart> parts, boolean full) {
            this.song = song; this.parts = parts; this.full = full; input = RenderInput.of(song);
        }
        @Override public State state() { return state; }
        @Override public int progressPercent() { return state == State.READY ? 100 : progress; }
        @Override public String error() { return error; }
        @Override public void run() {
            RenderInput source;
            synchronized (this) {
                if (state != State.PREPARING) return;
                source = input; input = null;
            }
            try {
                Rendered result = render(source, parts, full, value -> progress = value);
                synchronized (this) {
                    if (state == State.PREPARING) { rendered = result; state = State.READY; }
                }
            } catch (CancellationException cancelled) {
                synchronized (this) { if (state == State.PREPARING) state = State.CANCELLED; }
            } catch (RuntimeException | Error failure) {
                synchronized (this) {
                    if (state == State.PREPARING) {
                        String message = Objects.toString(failure.getMessage(), failure.getClass().getSimpleName());
                        error = message.substring(0, Math.min(256, message.length())); state = State.FAILED;
                    }
                }
            }
        }
        @Override public synchronized ScenePreparedMusic prepared() {
            if (state != State.READY) throw new IllegalStateException("music preparation is " + state);
            if (!published) {
                requireOpen();
                if (pending != this) throw new IllegalStateException("music preparation was superseded");
                if (full) { song.full = rendered.pcm(); song.notes = rendered.notes(); cached = song; }
                else { current(song); song.masked = rendered.pcm(); song.parts = parts; }
                rendered = null; published = true; pending = null;
            }
            return song;
        }
        @Override public synchronized void cancel() {
            if (published) return;
            if (future != null) { future.cancel(true); if (worker != null) worker.remove(future); }
            input = null; rendered = null;
            if (state != State.FAILED) { error = null; state = State.CANCELLED; }
            if (full) song.retire();
            if (pending == this) pending = null;
        }
    }

    private record RenderInput(String game, int durationFrames, int sampleRate,
                               GameAudioProfile profile, AbstractSmpsData data, DacData dac) {
        static RenderInput of(Prepared song) {
            return new RenderInput(song.game, song.durationFrames, song.sampleRate, song.profile, song.data, song.dac);
        }
    }
    private record Rendered(short[] pcm, List<SceneNoteEvent> notes) { }

    @Override public SceneMusicPlayer start(ScenePreparedMusic song, int fmMask, int psgMask,
                                            boolean dacMuted, int leadInSamples) {
        return start(song, List.of(new SceneMusicPart(0, fmMask, psgMask, dacMuted)), leadInSamples);
    }

    @Override public SceneMusicPlayer start(ScenePreparedMusic song, List<SceneMusicPart> parts,
                                            int leadInSamples) {
        requireOpen();
        discardTerminalJob();
        Prepared prepared = current(song);
        List<SceneMusicPart> selection = selection(prepared, parts, leadInSamples);
        if (pending != null) throw new IllegalStateException("music preparation must finish before starting");
        if (player != null) player.stop();
        if (prepared.masked == null || !selection.equals(prepared.parts)) {
            prepared.masked = null;
            prepared.masked = render(RenderInput.of(prepared), selection, false, ignored -> { }).pcm();
            prepared.parts = selection;
        }
        short[] masked = prepared.masked;
        player = new Player(prepared, masked, leadInSamples);
        audio.setScenePcmSource(player);
        return player;
    }

    @Override public void close() {
        if (closed) return;
        try {
            cancelPending(); retireCached();
        } finally {
            closed = true;
            if (worker != null) worker.shutdownNow();
        }
    }

    private void retireCached() {
        try {
            if (player != null) player.stop();
        } finally {
            if (cached != null) cached.retire();
            cached = null;
        }
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("scene music host is closed");
    }

    private static GameAudioProfile profile(String game) {
        return BuiltInRomDetectors.forGame(GameId.fromCode(game)).createModule().getAudioProfile();
    }

    private static Rendered render(RenderInput prepared, List<SceneMusicPart> parts, boolean notes, IntConsumer progress) {
        long framesLong = (long) prepared.durationFrames * prepared.sampleRate / 60;
        short[] pcm = new short[Math.toIntExact(framesLong * 2)];
        GameAudioProfile profile = prepared.profile;
        SmpsCoordFlagHandlerOwner handlers = new SmpsCoordFlagHandlerOwner(new SmpsCoordFlagRuntimeState());
        profile.configurePresentationCoordFlagHandlers(handlers);
        SmpsSequencerConfig baseConfig = profile.getSequencerConfig();
        SmpsSequencerConfig config = SmpsConfigBinding.bind(baseConfig,
                () -> baseConfig.getCoordFlagHandler() == null ? null : handlers.handlerFor(prepared.game));
        try (OwnedSmpsAudioStream stream = new OwnedSmpsAudioStream(prepared.game, 0,
                new SmpsPhysicalDevice.Settings(prepared.sampleRate, false), profile.smpsPhysicalPolicy(),
                ChipWriteObserver.NONE, new SmpsDriverSessionConfiguration(profile.smpsStatefulCommandPolicy()))) {
            var driver = stream.logicalDriver();
            driver.setRegion(SmpsSequencer.Region.NTSC);
            // A finite prepared program owns no ambient override to restore.
            // Never let a jingle's native restore flag mutate the running game.
            SmpsSequencer seq = new SmpsSequencer(prepared.data, prepared.dac, driver, () -> { }, config);
            seq.setSampleRate(prepared.sampleRate);
            seq.setFallbackVoiceData(prepared.data);
            driver.addSequencer(seq, false);
            EventBuilder builder = notes ? new EventBuilder(seq) : null;
            if (builder != null) seq.setNoteListener(builder);
            int maximum = (prepared.sampleRate + 59) / 60;
            short[] frame = new short[maximum * 2];
            int outputFrame = 0;
            int partIndex = -1;
            for (int index = 0; index < prepared.durationFrames; index++) {
                if (Thread.currentThread().isInterrupted()) throw new CancellationException("music preparation cancelled");
                progress.accept(index * 100 / prepared.durationFrames);
                int nextPart = partIndex;
                while (nextPart + 1 < parts.size() && parts.get(nextPart + 1).onsetSamples() <= outputFrame) nextPart++;
                if (nextPart != partIndex) {
                    partIndex = nextPart;
                    SceneMusicPart part = parts.get(partIndex);
                    // Logical PSG3 owns its tone generator and physical noise output.
                    int physicalPsgMask = part.psgMask() | ((part.psgMask() & 0b0100) << 1);
                    stream.applyChannelMasks(part.fmMask(), physicalPsgMask);
                    // DAC durations and loops still advance. Skip selected sample starts,
                    // preserving FM6's distinct source-track ownership.
                    for (SmpsSequencer.Track track : seq.getTracks()) {
                        if (track.type == SmpsSequencer.TrackType.DAC) track.dacMuted = part.dacMuted();
                    }
                }
                int end = (int) ((long) (index + 1) * prepared.sampleRate / 60);
                int count = end - outputFrame;
                if (builder != null) builder.sample = outputFrame;
                stream.serviceAndRenderFrame(frame, count);
                System.arraycopy(frame, 0, pcm, outputFrame * 2, count * 2);
                outputFrame = end;
            }
            return new Rendered(pcm, builder == null ? List.of() : builder.finish(framesLong));
        }
    }

    private static final class Prepared implements ScenePreparedMusic {
        final ManagedSceneMusic owner;
        final String game;
        final int id, durationFrames, sampleRate;
        GameAudioProfile profile;
        AbstractSmpsData data;
        DacData dac;
        short[] full;
        short[] masked;
        List<SceneMusicPart> parts;
        List<SceneNoteEvent> notes = List.of();
        Prepared(ManagedSceneMusic owner, String game, int id, int durationFrames, int sampleRate,
                 GameAudioProfile profile, AbstractSmpsData data, DacData dac) {
            this.owner = owner; this.game = game; this.id = id; this.durationFrames = durationFrames;
            this.sampleRate = sampleRate; this.profile = profile; this.data = data; this.dac = dac;
        }
        @Override public int sampleRate() { return sampleRate; }
        @Override public long lengthSamples() { return (long) durationFrames * sampleRate / 60; }
        @Override public List<SceneNoteEvent> notes() { return notes; }
        void retire() {
            full = null; masked = null; profile = null; data = null; dac = null; parts = null;
        }
    }

    private static final class Pending {
        final int track, channel, pitch, offset;
        final SceneNoteEvent.Kind kind;
        final long onset;
        Pending(int track, SmpsSequencer.Track data, int baseNoteOffset, long onset) {
            this.track = track; channel = data.channelId;
            pitch = data.rawFreqMode ? -1 : data.type == SmpsSequencer.TrackType.DAC ? data.note
                    : data.note + data.keyOffset + baseNoteOffset;
            offset = data.pos; kind = SceneNoteEvent.Kind.valueOf(data.type.name()); this.onset = onset;
        }
        SceneNoteEvent finish(long sample) {
            return new SceneNoteEvent(track, kind, channel, pitch, offset, onset, Math.max(0, sample - onset));
        }
    }

    private static final class EventBuilder implements SmpsNoteListener {
        final IdentityHashMap<SmpsSequencer.Track, Integer> tracks = new IdentityHashMap<>();
        final IdentityHashMap<SmpsSequencer.Track, Pending> sounding = new IdentityHashMap<>();
        final List<SceneNoteEvent> events = new ArrayList<>();
        long sample;
        final AbstractSmpsData data;
        EventBuilder(SmpsSequencer seq) {
            data = seq.getSmpsData();
            for (int index = 0; index < seq.trackCount(); index++) tracks.put(seq.trackAt(index), index);
        }
        @Override public void attack(SmpsSequencer.Track track) {
            release(track);
            int baseOffset = track.type == SmpsSequencer.TrackType.PSG
                    ? data.getPsgBaseNoteOffset() : data.getBaseNoteOffset();
            sounding.put(track, new Pending(tracks.get(track), track, baseOffset, sample));
        }
        @Override public void release(SmpsSequencer.Track track) {
            Pending note = sounding.remove(track);
            if (note != null) {
                if (events.size() >= MAX_NOTE_EVENTS) throw new IllegalArgumentException("ROM music exceeds the note event budget");
                events.add(note.finish(sample));
            }
        }
        List<SceneNoteEvent> finish(long length) {
            for (Pending note : sounding.values()) events.add(note.finish(length));
            events.sort(Comparator.comparingLong(SceneNoteEvent::onsetSamples).thenComparingInt(SceneNoteEvent::trackIndex));
            return List.copyOf(events);
        }
    }

    private final class Player implements SceneMusicPlayer, ScenePcmSource {
        private final Prepared song;
        private short[] masked;
        private final int leadIn;
        private long produced, observed, observedNanos, underruns, backendUnderruns;
        private final long[] clockNanos = new long[64];
        private final long[] clockSamples = new long[64];
        private int clockCount, clockWrite;
        private boolean stopped, paused, manualPaused, hostPaused, speakerUnavailable, audible = true;
        private double whammy;
        private double partGain = 1;
        private final PartCueMixer cues;
        Player(Prepared song, short[] masked, int leadIn) {
            this.song = song; this.masked = masked; this.leadIn = leadIn;
            cues = new PartCueMixer(song.full, masked, song.sampleRate);
            observedNanos = nanoTime.getAsLong();
            recordClock(observedNanos, 0);
        }
        @Override public void render(short[] target, int frames) {
            if (stopped || paused) {
                Arrays.fill(target, 0, frames * 2, (short) 0);
                return;
            }
            for (int index = 0; index < frames; index++) {
                long position = produced + index - leadIn;
                double goal = audible ? 1 : 0;
                double step = 1.0 / Math.max(1, song.sampleRate / 250);
                partGain += Math.max(-step, Math.min(step, goal - partGain));
                for (int channel = 0; channel < 2; channel++) {
                    int value = 0;
                    if (position >= 0 && position < song.lengthSamples()) {
                        int offset = (int) position * 2 + channel;
                        value = masked[offset] + (int) Math.round((song.full[offset] - masked[offset]) * partGain);
                        if (partGain != 0 && whammy != 0) {
                            // Controlled pitch modulation of the selected residual only. Full and
                            // masked stock mixes are separately synthesized, never assumed additive.
                            double shifted = position + Math.sin(position * 2 * Math.PI * 5 / song.sampleRate) * 24 * whammy;
                            shifted = Math.max(0, Math.min(song.lengthSamples() - 1, shifted));
                            int shiftedFrame = (int) shifted;
                            int shiftedOffset = shiftedFrame * 2 + channel;
                            int nextOffset = Math.min(song.full.length - 2 + channel, shiftedOffset + 2);
                            double fraction = shifted - shiftedFrame;
                            double first = song.full[shiftedOffset] - masked[shiftedOffset];
                            double next = song.full[nextOffset] - masked[nextOffset];
                            value = masked[offset] + (int) Math.round((first + (next - first) * fraction) * partGain);
                        }
                    }
                    target[index * 2 + channel] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value));
                }
            }
            cues.mix(target, frames);
            // The finite tail owns the complete presentation, including accepted cues.
            int taper = Math.max(1, song.sampleRate / 200);
            for (int index = 0; index < frames; index++) {
                long position = produced + index - leadIn;
                long remaining = song.lengthSamples() - position - 1;
                if (position >= 0 && remaining < taper) {
                    long tail = Math.max(0,remaining);
                    target[index*2] = (short)(target[index*2]*tail/taper);
                    target[index*2+1] = (short)(target[index*2+1]*tail/taper);
                }
            }
            produced += frames;
            if (produced >= leadIn + song.lengthSamples()) cues.close();
        }
        @Override public long samplePosition() {
            long now = nanoTime.getAsLong();
            if (!paused && !stopped) {
                long hardware = audio.consumedPresentationStereoFrames();
                long position = hardware < 0 ? produced : Math.min(produced, hardware);
                long currentUnderruns = audio.presentationUnderrunCount();
                if (speakerUnavailable) return Math.min(song.lengthSamples(), observed - leadIn);
                if (position < leadIn + song.lengthSamples() && currentUnderruns > backendUnderruns)
                    underruns += currentUnderruns - backendUnderruns;
                backendUnderruns = currentUnderruns;
                observed = Math.max(observed, position);
                observedNanos = now;
                recordClock(now, observed);
            }
            return Math.min(song.lengthSamples(), observed - leadIn);
        }
        @Override public long samplePositionAt(long nanos) {
            long position = samplePosition();
            if (paused || stopped || audio.consumedPresentationStereoFrames() < 0) return position;
            int oldest = (clockWrite - clockCount + clockNanos.length) % clockNanos.length;
            long baseNanos = clockNanos[oldest], baseSample = clockSamples[oldest];
            long upperSample = observed;
            for (int index = 0; index < clockCount; index++) {
                int slot = (oldest + index) % clockNanos.length;
                if (clockNanos[slot] > nanos) {
                    // A later observation bounds this historical interval. In
                    // particular, equal samples at both ends identify a stall.
                    upperSample = clockSamples[slot];
                    break;
                }
                baseNanos = clockNanos[slot]; baseSample = clockSamples[slot];
            }
            long delta = Math.round(Math.max(0, nanos - baseNanos) * (song.sampleRate / 1_000_000_000.0));
            long bounded = Math.min(observed, Math.min(upperSample, baseSample + delta));
            return Math.min(song.lengthSamples(), Math.max(-leadIn, bounded - leadIn));
        }
        private void recordClock(long nanos, long sample) {
            if (clockCount > 0) {
                int latest = (clockWrite - 1 + clockNanos.length) % clockNanos.length;
                if (nanos - clockNanos[latest] < 1_000_000) return;
            }
            clockNanos[clockWrite] = nanos; clockSamples[clockWrite] = sample;
            clockWrite = (clockWrite + 1) % clockNanos.length;
            clockCount = Math.min(clockNanos.length, clockCount + 1);
        }
        @Override public void pause() {
            if (!stopped && !manualPaused) {
                if (!paused) samplePosition();
                manualPaused = true; paused = true; audio.pause();
            }
        }
        @Override public void resume() {
            if (!stopped && manualPaused) {
                manualPaused = false; audio.resume();
            }
        }
        @Override public void onHostPause() {
            if (!stopped) {
                if (!paused) samplePosition();
                hostPaused = true; paused = true;
            }
        }
        @Override public boolean onHostResume() {
            hostPaused = false;
            if (stopped) return true;
            if (speakerUnavailable) return false;
            paused = manualPaused;
            if (!paused) {
                observedNanos = nanoTime.getAsLong();
                clockCount = 0; clockWrite = 0;
                recordClock(observedNanos, observed);
            }
            return !paused;
        }
        @Override public void onSpeakerFailure() {
            if (stopped || speakerUnavailable) return;
            speakerUnavailable = true;
            paused = true;
            underruns++;
            // Do not query the failed sink: -1 now describes its silent fallback,
            // not proof that produced-ahead PCM was heard by the player.
            recordClock(nanoTime.getAsLong(), observed);
        }
        @Override public boolean cuePart(long source, int length, double from, double to, double gain, double pan) {
            // Validate even when inactive; a rejected playback state never legitimizes malformed parameters.
            if (length < 1 || length > song.sampleRate / 4 || !PartCueMixer.bounded(from,.5,2)
                    || !PartCueMixer.bounded(to,.5,2) || !PartCueMixer.bounded(gain,0,1) || !PartCueMixer.bounded(pan,-1,1)
                    || source != PLAYHEAD && (source < 0 || source >= song.lengthSamples()))
                throw new IllegalArgumentException("invalid part cue");
            if (stopped || paused || produced >= leadIn + song.lengthSamples()) return false;
            long onset = source == PLAYHEAD ? produced - leadIn : source;
            if (onset < 0 || onset >= song.lengthSamples()) return false;
            return cues.play(onset,length,from,to,gain,pan);
        }
        @Override public void setPartAudible(boolean value) { audible = value; }
        @Override public void setWhammy(double amount) {
            if (!Double.isFinite(amount) || amount < 0 || amount > 1) throw new IllegalArgumentException("whammy must be 0..1");
            whammy = amount;
        }
        @Override public boolean finished() { return stopped || samplePosition() >= song.lengthSamples(); }
        @Override public boolean paused() { return paused; }
        @Override public long underrunCount() { samplePosition(); return underruns; }
        @Override public void stop() {
            if (stopped) return;
            samplePosition(); stopped = true; cues.close(); masked = null;
            if (player == this) { audio.resume(); audio.setScenePcmSource(null); player = null; }
        }
    }
}
