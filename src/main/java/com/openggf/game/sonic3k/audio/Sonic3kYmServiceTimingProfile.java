package com.openggf.game.sonic3k.audio;

import com.openggf.audio.smps.YmServiceTimingProfile;
import com.openggf.audio.smps.YmServiceTimingProfile.PathKind;
import com.openggf.audio.smps.YmServiceTimingProfile.Segment;
import com.openggf.audio.smps.YmServiceTimingProfile.SegmentKind;
import com.openggf.audio.smps.YmServiceTimingProfile.Variant;

import java.util.ArrayList;
import java.util.List;

/** Source-derived locked-on S3K timing for the audited FM5 first-attack path. */
public final class Sonic3kYmServiceTimingProfile {
    private static final int MAX_LOCKED_ON_SFX_TRACKS = 4;
    private static final int MAX_HARDWARE_ATTEMPTS_PER_TRACK = 34;
    private static final int MAX_WRITES_PER_DRIVER_SERVICE =
            MAX_LOCKED_ON_SFX_TRACKS * MAX_HARDWARE_ATTEMPTS_PER_TRACK;
    private static final long[] MAX_RELEASE = { 0, 3_150, 3_150, 3_150 };
    private static final long[] FREQUENCY_AND_KEY_ON = { 0, 2_700, 2_880 };
    private static final long[] TRACK_STOP_KEY_OFF = { 2_715 };
    public static final YmServiceTimingProfile PROFILE = create();

    private Sonic3kYmServiceTimingProfile() {
    }

    private static YmServiceTimingProfile create() {
        List<Segment> segments = new ArrayList<>();
        segments.add(new Segment(SegmentKind.SFX_ADMISSION_PREP,
                new Variant(1, 4, false, true, 0,
                        0,
                        PathKind.FIRST_ADMISSION),
                new long[] { 0, 3_570, 3_150, 3_150, 3_150 }));

        for (int carrierMask = 0; carrierMask <= 0xF; carrierMask++) {
            for (int octaveLoops = 0; octaveLoops <= 7; octaveLoops++) {
                Variant firstAttack = new Variant(1, 4, true, false,
                        carrierMask, octaveLoops,
                        PathKind.FIRST_VOICE_ATTACK);
                segments.add(new Segment(SegmentKind.SFX_MAX_RELEASE,
                        firstAttack, MAX_RELEASE));
                segments.add(new Segment(SegmentKind.FM_VOICE_UPLOAD,
                        firstAttack, withLeadingAdvance(6_435,
                                voiceUpload(carrierMask))));
                segments.add(new Segment(SegmentKind.KEY_OFF,
                        firstAttack, new long[] { 8_055 }));
                // zGetNextNote's octave loop executes 35 Z80 T-states for
                // every successful 12-note subtraction.  The retained Blue
                // Sphere authority owns four loops; S3K master cycles are
                // 15 per Z80 T-state.
                long noteLookupAdvance = Math.addExact(30_630L,
                        Math.multiplyExact(octaveLoops - 4L, 525L));
                segments.add(new Segment(SegmentKind.FREQUENCY_AND_KEY_ON,
                        firstAttack, withLeadingAdvance(noteLookupAdvance,
                                FREQUENCY_AND_KEY_ON)));
            }

            Variant restore = new Variant(1, 4, true, false,
                    carrierMask, 4, PathKind.COMPLETION_RESTORE);
            segments.add(new Segment(SegmentKind.COMPLETION_RESTORE,
                    restore, completionRestore(carrierMask)));
        }
        for (int port = 0; port <= 1; port++) {
            segments.add(new Segment(SegmentKind.TRACK_STOP_KEY_OFF,
                    trackStop(port), TRACK_STOP_KEY_OFF));
        }
        // Sound_59 (Collapse) and Sound_66 (All Spheres Collected) are the
        // largest shipped locked-on headers at four tracks. The complete SFX
        // owner service is transactional, so reserve every sibling track at
        // the audited 34-attempt FM upper bound (PSG tracks use fewer slots).
        return YmServiceTimingProfile.of(MAX_WRITES_PER_DRIVER_SERVICE,
                segments.toArray(Segment[]::new));
    }

    private static long[] voiceUpload(int carrierMask) {
        long[] advances = {
                0, 3_225, 3_765,
                3_570, 3_570, 3_570, 3_570, 3_570, 3_570, 3_570,
                3_570, 3_570, 3_570, 3_570, 3_570, 3_570, 3_570,
                3_570, 3_570, 3_570, 3_570, 3_570,
                5_145, 3_540, 3_540, 3_540
        };
        for (int operator = 0; operator < 4; operator++) {
            if ((carrierMask & (1 << operator)) != 0) {
                advances[22 + operator] = Math.addExact(
                        advances[22 + operator], 285);
            }
        }
        return advances;
    }

    private static long[] completionRestore(int carrierMask) {
        long[] voice = voiceUpload(carrierMask);
        long[] restore = new long[voice.length];
        // cfStopTrack's shipped fix_sndbugs=0 positive-voice FM5 path performs
        // the channel lookup and bank switch before zSendFMInstrument.
        restore[0] = 16_170;
        System.arraycopy(voice, 1, restore, 1, voice.length - 1);
        return restore;
    }

    static Variant trackStop(int port) {
        return new Variant(port, 4, false, false, 0, 0,
                PathKind.TRACK_STOP);
    }

    private static long[] withLeadingAdvance(long leading, long[] advances) {
        long[] combined = advances.clone();
        combined[0] = Math.addExact(combined[0], leading);
        return combined;
    }
}
