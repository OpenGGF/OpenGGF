package com.openggf.tools;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestGameplayCaptureToolArgs {

    @Test
    void parsesHexPositionsOneBasedActsAndStills() {
        GameplayCaptureTool.Arguments args = GameplayCaptureTool.Arguments.parse(new String[] {
                "--game", "s3k", "--zone", "fbz", "--act", "2", "--x", "0x1CF0", "--y", "$76C",
                "--width", "400", "--donor", "s1", "--sidekick", "none", "--input", "in.txt",
                "--settle", "30", "--every", "2", "--stills", "10,0x20", "--no-video",
                "--out-dir", "out"});
        assertEquals(1, args.act());
        assertEquals(0x1CF0, args.startX());
        assertEquals(0x76C, args.startY());
        assertEquals(400, args.width());
        assertEquals("s1", args.donor());
        assertEquals("", args.sidekickCharacter());
        assertEquals(Path.of("in.txt"), args.input());
        assertEquals(30, args.settle());
        assertEquals(2, args.every());
        assertEquals(Set.of(10, 32), args.stills());
        assertFalse(args.video());
        assertNull(args.frames());
        assertTrue(args.stopOnDeath());
        assertEquals(Path.of("out"), args.outDir());
    }

    @Test
    void ringsIsUnsetUntilAskedForAndThenCarriesTheDeclaredCount() {
        GameplayCaptureTool.Arguments bare = GameplayCaptureTool.Arguments.parse(new String[] {
                "--zone", "ssz", "--act", "1", "--out-dir", "out"});
        assertNull(bare.rings());
        GameplayCaptureTool.Arguments seeded = GameplayCaptureTool.Arguments.parse(new String[] {
                "--zone", "ssz", "--act", "1", "--out-dir", "out", "--rings", "355"});
        assertEquals(355, seeded.rings());
    }

    @Test
    void checkpointRingsAndReverseGravityRemainIndependentAfterCampaignMerge() {
        var args = GameplayCaptureTool.Arguments.parse(new String[] {
                "--zone", "dez", "--act", "2", "--out-dir", "out",
                "--star-post", "--rings", "17", "--reverse-gravity"});
        assertTrue(args.starPost());
        assertTrue(args.reverseGravity());
        assertEquals(17, args.rings());
        var oldSettings = new GameplayCaptureSession.Settings(320, "sonic", "", "off",
                null, 0x140, 0x3AC, null, false, false, null, null, true, 17);
        assertTrue(oldSettings.starPost(), "existing positioned-capture constructor retains its meaning");
        assertFalse(oldSettings.reverseGravity());
        var combined = new GameplayCaptureSession.Settings(320, "sonic", "", "off",
                null, 0x140, 0x3AC, null, false, false, null, null, true, 17, true);
        assertTrue(combined.starPost());
        assertTrue(combined.reverseGravity());
        assertEquals(17, combined.rings());
    }

    @Test
    void requiresZoneActAndOutputDirectory() {
        assertThrows(IllegalArgumentException.class, () -> GameplayCaptureTool.Arguments.parse(
                new String[] {"--zone", "aiz", "--out-dir", "o"}));
        assertThrows(IllegalArgumentException.class, () -> GameplayCaptureTool.Arguments.parse(
                new String[] {"--zone", "aiz", "--act", "1"}));
        assertThrows(IllegalArgumentException.class, () -> GameplayCaptureTool.Arguments.parse(
                new String[] {"--zone", "aiz", "--act", "0", "--out-dir", "o"}));
        assertThrows(IllegalArgumentException.class, () -> GameplayCaptureTool.Arguments.parse(
                new String[] {"--zone", "aiz", "--act", "1", "--out-dir", "o", "--bogus", "1"}));
    }

    @Test
    void zoneIdsResolveByNameOrNumberPerGame() {
        assertEquals(4, GameplayCaptureTool.ZoneIds.resolve("s3k", "fbz"));
        assertEquals(0, GameplayCaptureTool.ZoneIds.resolve("s3k", "AIZ"));
        assertEquals(0x0B, GameplayCaptureTool.ZoneIds.resolve("s3k", "0x0B"));
        assertEquals(7, GameplayCaptureTool.ZoneIds.resolve("s3k", "7"));
        assertEquals(0, GameplayCaptureTool.ZoneIds.resolve("s1", "ghz"));
        assertEquals(0, GameplayCaptureTool.ZoneIds.resolve("s2", "ehz"));
        assertThrows(IllegalArgumentException.class, () -> GameplayCaptureTool.ZoneIds.resolve("s3k", "nowhere"));
        assertThrows(IllegalArgumentException.class, () -> GameplayCaptureTool.ZoneIds.resolve("s4", "ghz"));
    }
}
