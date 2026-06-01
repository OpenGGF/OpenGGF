package com.openggf.game.sonic3k;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.animation.AnimatedTileChannel;
import com.openggf.level.Level;
import com.openggf.level.Pattern;
import com.openggf.level.animation.AnimatedPatternManager;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kLbzPatternAnimation {
    @BeforeAll
    static void configure() {
        SonicConfigurationService.getInstance()
                .setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);
    }

    @Test
    void lbz1InstallsAnimatedTileGraphChannels() {
        HeadlessTestFixture.builder()
                .withZoneAndAct(0x06, 0)
                .build();

        assertGraphChannelsInstalled(
                "s3k.lbz.shared",
                "s3k.lbz1.scroll",
                "s3k.lbz1.script.0",
                "s3k.lbz1.spec.0",
                "s3k.lbz1.spec.1");
    }

    @Test
    void lbz1AnimatedTileUploadsReplacePlaceholderPatterns() {
        HeadlessTestFixture.builder()
                .withZoneAndAct(0x06, 0)
                .build();

        Sonic3kPatternAnimator animator = resolvePatternAnimator();
        assertRangesChangeAfterUpdates(animator, 4,
                new TileRange(0x160, 0x16F),
                new TileRange(0x350, 0x364),
                new TileRange(0x365, 0x36C));
    }

    @Test
    void lbz2AnimatedTileUploadsReplacePlaceholderPatterns() {
        HeadlessTestFixture.builder()
                .withZoneAndAct(0x06, 1)
                .build();

        assertGraphChannelsInstalled(
                "s3k.lbz.shared",
                "s3k.lbz2.scroll",
                "s3k.lbz2.waterline",
                "s3k.lbz2.script.0",
                "s3k.lbz2.script.1");

        Sonic3kPatternAnimator animator = resolvePatternAnimator();
        assertRangesChangeAfterUpdates(animator, 1,
                new TileRange(0x160, 0x16F),
                new TileRange(0x2D3, 0x2E2),
                new TileRange(0x2E3, 0x2E4));
    }

    private static void assertGraphChannelsInstalled(String... expectedChannelIds) {
        List<String> channelIds = GameServices.animatedTileChannelGraph().channels().stream()
                .map(AnimatedTileChannel::channelId)
                .toList();
        for (String expectedChannelId : expectedChannelIds) {
            assertTrue(channelIds.contains(expectedChannelId),
                    "Expected LBZ graph channel " + expectedChannelId + " but found " + channelIds);
        }
    }

    private static void assertRangesChangeAfterUpdates(Sonic3kPatternAnimator animator,
                                                       int maxFrames,
                                                       TileRange... ranges) {
        Level level = GameServices.level().getCurrentLevel();
        assertNotNull(level, "Level must be loaded");

        byte[][] before = new byte[ranges.length][];
        boolean[] changed = new boolean[ranges.length];
        for (int i = 0; i < ranges.length; i++) {
            before[i] = snapshotRange(level, ranges[i].startTile(), ranges[i].endTileInclusive());
        }

        for (int i = 0; i < maxFrames; i++) {
            animator.update();
            for (int rangeIndex = 0; rangeIndex < ranges.length; rangeIndex++) {
                if (changed[rangeIndex]) {
                    continue;
                }
                TileRange range = ranges[rangeIndex];
                byte[] after = snapshotRange(level, range.startTile(), range.endTileInclusive());
                changed[rangeIndex] = !Arrays.equals(before[rangeIndex], after);
            }
        }

        for (int i = 0; i < ranges.length; i++) {
            if (!changed[i]) {
                TileRange range = ranges[i];
                fail("Expected LBZ animated tile upload to change $" + Integer.toHexString(range.startTile())
                        + "-$" + Integer.toHexString(range.endTileInclusive()));
            }
        }
    }

    private static Sonic3kPatternAnimator resolvePatternAnimator() {
        AnimatedPatternManager manager = GameServices.level().getAnimatedPatternManager();
        assertNotNull(manager, "AnimatedPatternManager must be present");
        if (manager instanceof Sonic3kPatternAnimator animator) {
            return animator;
        }
        if (manager instanceof Sonic3kLevelAnimationManager levelAnimator) {
            try {
                Field field = Sonic3kLevelAnimationManager.class.getDeclaredField("patternAnimator");
                field.setAccessible(true);
                Object value = field.get(levelAnimator);
                if (value instanceof Sonic3kPatternAnimator animator) {
                    return animator;
                }
            } catch (ReflectiveOperationException e) {
                throw new AssertionError("Unable to access Sonic3kPatternAnimator", e);
            }
        }
        throw new AssertionError("Unexpected AnimatedPatternManager type: " + manager.getClass().getName());
    }

    private static byte[] snapshotRange(Level level, int startTile, int endTileInclusive) {
        int tileCount = endTileInclusive - startTile + 1;
        byte[] data = new byte[tileCount * Pattern.PATTERN_SIZE_IN_MEM];
        int writeOffset = 0;
        for (int tileIndex = startTile; tileIndex <= endTileInclusive; tileIndex++) {
            Pattern pattern = level.getPattern(tileIndex);
            assertNotNull(pattern, "Pattern tile must exist at $" + Integer.toHexString(tileIndex));
            byte[] tile = snapshot(pattern);
            System.arraycopy(tile, 0, data, writeOffset, tile.length);
            writeOffset += tile.length;
        }
        return data;
    }

    private static byte[] snapshot(Pattern pattern) {
        byte[] data = new byte[Pattern.PATTERN_SIZE_IN_MEM];
        int index = 0;
        for (int y = 0; y < Pattern.PATTERN_HEIGHT; y++) {
            for (int x = 0; x < Pattern.PATTERN_WIDTH; x++) {
                data[index++] = pattern.getPixel(x, y);
            }
        }
        return data;
    }

    private record TileRange(int startTile, int endTileInclusive) {
    }
}
