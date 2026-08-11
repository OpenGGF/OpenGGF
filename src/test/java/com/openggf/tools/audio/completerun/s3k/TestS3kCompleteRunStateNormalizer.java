package com.openggf.tools.audio.completerun.s3k;

import static com.openggf.tools.audio.completerun.CompleteRunAudioTrace.HardwareRole.DAC;
import static com.openggf.tools.audio.completerun.CompleteRunAudioTrace.HardwareRole.FM1;
import static com.openggf.tools.audio.completerun.CompleteRunAudioTrace.HardwareRole.FM2;
import static com.openggf.tools.audio.completerun.CompleteRunAudioTrace.HardwareRole.FM3;
import static com.openggf.tools.audio.completerun.CompleteRunAudioTrace.HardwareRole.FM4;
import static com.openggf.tools.audio.completerun.CompleteRunAudioTrace.HardwareRole.FM5;
import static com.openggf.tools.audio.completerun.CompleteRunAudioTrace.HardwareRole.PSG1;
import static com.openggf.tools.audio.completerun.CompleteRunAudioTrace.HardwareRole.PSG2;
import static com.openggf.tools.audio.completerun.CompleteRunAudioTrace.HardwareRole.PSG3;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.HardwareRole;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.NormalizedState;
import com.openggf.tools.audio.completerun.s3k.S3kCompleteRunStateNormalizer.Asset;
import com.openggf.tools.audio.completerun.s3k.S3kCompleteRunStateNormalizer.DriverGlobals;
import com.openggf.tools.audio.completerun.s3k.S3kCompleteRunStateNormalizer.LiveSfx;
import com.openggf.tools.audio.completerun.s3k.S3kCompleteRunStateNormalizer.RomPointer;
import com.openggf.tools.audio.completerun.s3k.S3kCompleteRunStateNormalizer.SavedMusic;
import com.openggf.tools.audio.completerun.s3k.S3kCompleteRunStateNormalizer.Snapshot;
import com.openggf.tools.audio.completerun.s3k.S3kCompleteRunStateNormalizer.Track;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TestS3kCompleteRunStateNormalizer {
    private static final Map<String, Asset> ASSETS = Map.of(
            "music.aiz1", new Asset("music.aiz1", 0x100000, 0x101000),
            "music.saved", new Asset("music.saved", 0x110000, 0x111000),
            "sfx.ring", new Asset("sfx.ring", 0x0f8000, 0x0f9000),
            "driver", new Asset("driver", 0, 0x2000));

    @Test
    void liveSfxOverlapOwnsItsPhysicalRoleWithoutExposingSavedCapacity() {
        List<Track> music = musicTracks("music.aiz1", 0x100100);
        List<Track> sfx = inactiveTracks(7);
        sfx.set(1, track("sfx.ring", 0x0f8120, 0x84, 0x04)); // SFX FM4 overrides music FM4.
        Snapshot snapshot = new Snapshot(globals(0, false), music, new LiveSfx(sfx));

        NormalizedState state = S3kCompleteRunStateNormalizer.normalizeReference(snapshot, ASSETS);

        assertEquals(List.of(DAC, FM1, FM2, FM3, FM4, FM5, PSG1, PSG2, PSG3),
                state.roles().stream().map(role -> role.role()).toList());
        assertEquals("SFX", roleFields(state, FM4).get("sourceLayer"));
        assertEquals("sfx.ring", roleFields(state, FM4).get("assetKey"));
        assertEquals(0x120L, roleFields(state, FM4).get("cursor"));
        Map<String, Object> overlap = globalMap(state, "overlap");
        assertEquals("LIVE_SFX", overlap.get("mode"));
        assertFalse(overlap.containsKey("savedCurrentTempo"));
        assertFalse(overlap.containsKey("savedTracks"));
    }

    @Test
    void oneUpOverlapPreservesSavedTempoPointersAndShippedSaveLoopBug() {
        List<Track> saved = musicTracks("music.saved", 0x110100);
        saved.replaceAll(track -> withPlayback(track, track.playbackControl() & 0x7f));
        Snapshot snapshot = new Snapshot(globals(0x29, true), musicTracks("music.aiz1", 0x100100),
                new SavedMusic(saved));

        NormalizedState state = S3kCompleteRunStateNormalizer.normalizeReference(snapshot, ASSETS);
        Map<String, Object> overlap = globalMap(state, "overlap");

        assertEquals("SAVED_MUSIC", overlap.get("mode"));
        assertEquals(0x96, overlap.get("savedCurrentTempo"));
        assertEquals(0x08, overlap.get("savedTempoSpeedup"));
        assertEquals(Map.of("assetKey", "music.saved", "cursor", 0x40L),
                overlap.get("savedVoiceTablePointer"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> savedTracks = (List<Map<String, Object>>) overlap.get("savedTracks");
        // fix_sndbugs=0 sets bit 2 in RAM and then overwrites it with A, so an ordinary
        // saved playing track becomes $00, not the intended $04.
        assertEquals(0, savedTracks.get(0).get("playbackControl"));
        assertTrue(state.roles().stream().anyMatch(role -> role.role() == FM1 && role.active()));
    }

    @Test
    void referenceAndEngineAdaptersCanonicalizePointersToTheSameAssetCursor() {
        Snapshot snapshot = new Snapshot(globals(0, false), musicTracks("music.aiz1", 0x100100),
                new LiveSfx(inactiveTracks(7)));

        assertEquals(S3kCompleteRunStateNormalizer.normalizeReference(snapshot, ASSETS),
                S3kCompleteRunStateNormalizer.normalizeEngine(snapshot, ASSETS));
    }

    @Test
    void rejectsWrongTrackInventoryAndOutOfAssetPointers() {
        assertThrows(IllegalArgumentException.class, () -> new Snapshot(globals(0, false),
                inactiveTracks(8), new LiveSfx(inactiveTracks(7))));

        List<Track> music = musicTracks("music.aiz1", 0x100100);
        music.set(0, track("music.aiz1", 0x101000, 0x80, 0x06));
        Snapshot badPointer = new Snapshot(globals(0, false), music, new LiveSfx(inactiveTracks(7)));
        assertThrows(IllegalArgumentException.class,
                () -> S3kCompleteRunStateNormalizer.normalizeReference(badPointer, ASSETS));
    }

    @Test
    void profileInventoryAcceptsTheStrictNormalizedState() {
        Snapshot snapshot = new Snapshot(globals(0, false), musicTracks("music.aiz1", 0x100100),
                new LiveSfx(inactiveTracks(7)));
        NormalizedState state = S3kCompleteRunStateNormalizer.normalizeReference(snapshot, ASSETS);

        S3kCompleteRunAudioProfile.profile().validateState(state);
        assertEquals(S3kCompleteRunStateNormalizer.GLOBAL_FIELDS,
                state.fields().stream().map(field -> field.name()).toList());
        assertEquals(S3kCompleteRunStateNormalizer.ACTIVE_ROLE_FIELDS,
                state.roles().get(0).fields().stream().map(field -> field.name()).toList());
    }

    private static DriverGlobals globals(int fadeToPrevious, boolean saved) {
        return new DriverGlobals(
                1, 2, List.of(0x01, 0x33, 0xbc), 0x08, 0x33, 0x01, 0x33, 0xbc,
                0x28, 6, 5, 0, 0, 0x90, fadeToPrevious, 0, 0x96,
                0xbc, 0x80, 5, 1, 0x40,
                saved ? new RomPointer("music.saved", 0x110040) : null,
                0x96, 0x20, 0x08, 3, 0x81, 2, 0,
                new RomPointer("music.aiz1", 0x100020), new RomPointer("driver", 0x700),
                new RomPointer("music.aiz1", 0x100030), new RomPointer("sfx.ring", 0x0f8010),
                1, 0x20, false);
    }

    private static List<Track> musicTracks(String assetKey, long pointer) {
        List<Track> tracks = new ArrayList<>();
        int[] voices = {0x06, 0x00, 0x01, 0x02, 0x04, 0x05, 0x80, 0xa0, 0xc0};
        for (int index = 0; index < voices.length; index++) {
            tracks.add(track(assetKey, pointer + index * 0x20L, 0x80, voices[index]));
        }
        return tracks;
    }

    private static List<Track> inactiveTracks(int count) {
        return new ArrayList<>(java.util.Collections.nCopies(count, Track.inactive()));
    }

    private static Track track(String assetKey, long pointer, int playback, int voiceControl) {
        RomPointer data = new RomPointer(assetKey, pointer);
        return new Track(true, data, playback, voiceControl, 1, 0xfe, 0x10,
                0x80, 2, 0x30, 0xc0, 3, 4, 0x1234, 1, 0xff, 0x02,
                3, 4, null, 5, null, 6, 7, data, 0x1234,
                8, 9, 0xfe, 10, List.of(1, 2), data, List.of(data));
    }

    private static Track withPlayback(Track source, int playback) {
        return new Track(source.populated(), source.dataPointer(), playback, source.voiceControl(),
                source.tempoDivider(), source.transpose(), source.volume(), source.modulationControl(),
                source.voiceIndex(), source.stackPointer(), source.amsFmsPan(), source.durationTimeout(),
                source.savedDuration(), source.frequencyOrDac(), source.voiceSongId(), source.detune(),
                source.unknown11(), source.volumeEnvelope(), source.fmVolumeEnvelope(), source.ssgEgPointer(),
                source.feedbackAlgorithm(), source.totalLevelPointer(), source.noteFillTimeout(),
                source.noteFillMaster(), source.modulationPointer(), source.modulationValue(),
                source.modulationWait(), source.modulationSpeed(), source.modulationDelta(),
                source.modulationSteps(), source.loopCounters(), source.voicesPointer(), source.returnStack());
    }

    private static Map<String, Object> roleFields(NormalizedState state, HardwareRole role) {
        return state.roles().stream().filter(candidate -> candidate.role() == role).findFirst().orElseThrow()
                .fields().stream().collect(LinkedHashMap::new,
                        (map, field) -> map.put(field.name(), field.value()), Map::putAll);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> globalMap(NormalizedState state, String name) {
        return (Map<String, Object>) state.fields().stream().filter(field -> field.name().equals(name))
                .findFirst().orElseThrow().value();
    }
}
