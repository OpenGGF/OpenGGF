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
    private static final long[] MAX_RELEASE = { 0, 3_150, 3_150, 3_150 };
    private static final long[] FREQUENCY_AND_KEY_ON = { 0, 2_700, 2_880 };
    public static final YmServiceTimingProfile PROFILE = create();

    private Sonic3kYmServiceTimingProfile() {
    }

    private static YmServiceTimingProfile create() {
        List<Segment> segments = new ArrayList<>();
        segments.add(new Segment(SegmentKind.SFX_ADMISSION_PREP,
                new Variant(1, 4, false, true, 0,
                        PathKind.FIRST_ADMISSION),
                new long[] { 0, 3_570, 3_150, 3_150, 3_150 }));

        for (int carrierMask = 0; carrierMask <= 0xF; carrierMask++) {
            Variant firstAttack = new Variant(1, 4, true, false,
                    carrierMask, PathKind.FIRST_VOICE_ATTACK);
            segments.add(new Segment(SegmentKind.SFX_MAX_RELEASE,
                    firstAttack, MAX_RELEASE));
            segments.add(new Segment(SegmentKind.FM_VOICE_UPLOAD,
                    firstAttack, voiceUpload(carrierMask)));
            segments.add(new Segment(SegmentKind.KEY_OFF,
                    firstAttack, new long[] { 0 }));
            segments.add(new Segment(SegmentKind.FREQUENCY_AND_KEY_ON,
                    firstAttack, FREQUENCY_AND_KEY_ON));

            Variant restore = new Variant(1, 4, true, false,
                    carrierMask, PathKind.COMPLETION_RESTORE);
            segments.add(new Segment(SegmentKind.COMPLETION_RESTORE,
                    restore, completionRestore(carrierMask)));
        }
        return YmServiceTimingProfile.of(34,
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
        long[] restore = new long[voice.length + 1];
        restore[0] = 0;
        // cfStopTrack's shipped fix_sndbugs=0 positive-voice FM5 path performs
        // the channel lookup and bank switch before zSendFMInstrument.
        restore[1] = 16_170;
        System.arraycopy(voice, 1, restore, 2, voice.length - 1);
        return restore;
    }
}
