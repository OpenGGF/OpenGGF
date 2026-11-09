package com.openggf.net.protocol;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TestTrackKey {
    @Test void actAndArenaRoundTrip() {
        for (String key : new String[]{"s2:0:1","s3k:arena:4"}) assertEquals(key,TrackKey.parse(key).format());
    }
    @Test void canonicalizesAndChecksModeEligibility() {
        assertEquals("s2:0:1", TrackKey.parse("s2:000:001").format());
        assertFalse(com.openggf.net.hub.TrackRules.legal(TrackKey.parse("s3k:arena:0"), "TIME_ATTACK"));
        assertTrue(com.openggf.net.hub.TrackRules.legal(TrackKey.parse("s3k:arena:4"), "COMPETITION"));
        assertFalse(com.openggf.net.hub.TrackRules.legal(TrackKey.parse("s1:0:0"), "COMPETITION"));
        assertFalse(com.openggf.net.hub.TrackRules.legal(TrackKey.parse("s3k:arena:5"), "COMPETITION"));
    }
    @Test void rejectsMalformedKeys() {
        for (String key : new String[]{"s3k:arena:-1","s3k:arena:1:0","s3k:0","S3K:0:0","s3k:1000:0"}) assertThrows(IllegalArgumentException.class,()->TrackKey.parse(key));
    }
}
