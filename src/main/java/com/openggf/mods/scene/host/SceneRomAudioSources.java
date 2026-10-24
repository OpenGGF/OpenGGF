package com.openggf.mods.scene.host;

import com.openggf.audio.AudioManager;
import com.openggf.audio.GameAudioProfile;
import com.openggf.audio.ScopedDonorAudio;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Library-owned ROM source leases shared by authored cues and native performers. Engine-internal. */
public final class SceneRomAudioSources implements AutoCloseable {
    private final SceneRomLibrary library;
    private final Map<AudioManager, Epoch> epochs = new IdentityHashMap<>();
    private boolean closed;

    SceneRomAudioSources(SceneRomLibrary library) { this.library = library; }

    public synchronized Lease acquire(AudioManager audio, String game) {
        if (closed) throw new IllegalStateException("Scene ROM audio sources are closed");
        GameAudioProfile profile = audio.getAudioProfile();
        Epoch epoch = epochs.get(audio);
        if (epoch == null || epoch.profile != profile) {
            if (epoch != null) retire(epoch);
            epoch = new Epoch(profile);
            epochs.put(audio,epoch);
        }
        Entry entry = epoch.sources.get(game);
        if (entry == null) {
            var donor = library.module(game).getAudioProfile();
            var loader = donor.createSmpsLoader(library.sourceRom(game));
            if (loader == null) throw new IllegalArgumentException("Scene ROM has no native sound loader: " + game);
            entry = new Entry(ScopedDonorAudio.register(audio,game,loader,loader.loadDacData(),donor.getSequencerConfig(),donor));
            epoch.sources.put(game,entry);
        }
        entry.references++;
        return new Lease(this,audio,epoch,game,entry);
    }

    private synchronized void release(Lease lease, boolean abandon) {
        if (lease.closed) return;
        lease.closed = true;
        Entry entry = lease.entry;
        if (entry.retired) return;
        if (lease.epoch.profile != lease.audio.getAudioProfile()) {
            entry.retired = true;
            lease.epoch.sources.remove(lease.game,entry);
            return;
        }
        if (--entry.references == 0) {
            entry.retired = true;
            lease.epoch.sources.remove(lease.game,entry);
            if (!abandon) entry.scope.close();
        }
    }

    private static void retire(Epoch epoch) {
        // A new native shot installs a new stock module/profile instance even for
        // the same game. Its producer must not receive the old epoch's saved routes.
        for (var entry : epoch.sources.values()) entry.retired = true;
        epoch.sources.clear();
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        Throwable failure = null;
        try {
            for (var pair : epochs.entrySet()) {
                var epoch = pair.getValue();
                if (epoch.profile == pair.getKey().getAudioProfile())
                    for (var entry : epoch.sources.values()) {
                        entry.retired = true;
                        try { entry.scope.close(); }
                        catch (RuntimeException | Error error) {
                            if (failure == null) failure = error;
                            else if (failure != error) failure.addSuppressed(error);
                        }
                    }
                else retire(epoch);
            }
        } finally { epochs.clear(); }
        if (failure instanceof RuntimeException runtime) throw runtime;
        if (failure instanceof Error error) throw error;
    }

    public static final class Lease implements AutoCloseable {
        private final SceneRomAudioSources owner;
        private final AudioManager audio;
        private final Epoch epoch;
        private final String game;
        private final Entry entry;
        private boolean closed;

        private Lease(SceneRomAudioSources owner, AudioManager audio, Epoch epoch, String game, Entry entry) {
            this.owner=owner;this.audio=audio;this.epoch=epoch;this.game=game;this.entry=entry;
        }
        public boolean active() { return !closed && !entry.retired && epoch.profile == audio.getAudioProfile(); }
        public void abandon() { owner.release(this,true); }
        @Override public void close() { owner.release(this,false); }
    }

    private static final class Epoch {
        final GameAudioProfile profile;
        final Map<String,Entry> sources=new LinkedHashMap<>();
        Epoch(GameAudioProfile profile) { this.profile=profile; }
    }
    private static final class Entry {
        final ScopedDonorAudio scope;
        int references;
        boolean retired;
        Entry(ScopedDonorAudio scope) { this.scope=scope; }
    }
}
