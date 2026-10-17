package com.openggf.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class TestS3kEndingSamplerTool {
    @TempDir Path directory;

    private String[] arguments(String product, String progress, String completion) {
        return new String[]{"--rom", "original.gen", "--out-dir", "capture", "--spots", "spots.txt",
                "--product", product, "--region", "ntsc", "--progress", progress, "--completion", completion};
    }

    @Test void allNineExplicitProductsUseTheDeclaredRosterAndEmeraldInventory() {
        String[] products = {"sonic-solo", "tails-solo", "sonic-tails"};
        String[] main = {"sonic", "tails", "sonic"};
        String[] sidekick = {"", "", "tails"};
        int[] nativeOptions = {1, 2, 0};
        String[] progress = {"-2", "0", "+2"};
        String[] inventory = {"0000000", "1111111", "3333333"};
        for (int p = 0; p < products.length; p++) {
            for (int e = 0; e < progress.length; e++) {
                var args = S3kEndingSamplerTool.Arguments.parse(arguments(products[p], progress[e], "spots"));
                var settings = args.settings();
                assertEquals(main[p], settings.mainCharacter());
                assertEquals(sidekick[p], settings.sidekickCharacter());
                assertEquals(inventory[e], settings.emeraldStates());
                assertEquals(nativeOptions[p], args.product().nativePlayerOption);
                assertEquals(Integer.parseInt(progress[e]), args.progress());
                assertEquals(progress[e], args.progressLabel());
                assertEquals(320, settings.width());
                assertEquals("off", settings.donor());
                assertEquals("spots", args.completion());
                assertFalse(args.throughTitle());
            }
        }
    }

    @Test void r1SixArgumentTailsProductStillDefaultsToTitleCompletion() {
        var args = S3kEndingSamplerTool.Arguments.parse(java.util.Arrays.copyOf(arguments("tails-solo", "+2", "title"), 12));
        assertTrue(args.throughTitle());
        assertEquals("title", args.completion());
        assertEquals("3333333", args.emeraldStates());
    }

    @Test void refusesUnsupportedProductsRegionsProgressCompletionAndRepeatedFlags() {
        String[] valid = arguments("tails-solo", "+2", "spots");
        for (int index : new int[]{7, 9, 11, 13}) {
            String[] invalid = valid.clone();
            invalid[index] = "unsupported";
            assertThrows(IllegalArgumentException.class, () -> S3kEndingSamplerTool.Arguments.parse(invalid));
        }
        for (String region : new String[]{"pal", "NTSC", "auto"}) {
            String[] invalid = valid.clone(); invalid[9] = region;
            assertThrows(IllegalArgumentException.class, () -> S3kEndingSamplerTool.Arguments.parse(invalid));
        }
        for (String product : new String[]{"knuckles", "tails-sonic", "sonic-wide"}) {
            assertThrows(IllegalArgumentException.class, () -> S3kEndingSamplerTool.Arguments.parse(arguments(product, "+2", "spots")));
        }
        for (String progress : new String[]{"2", "+0", "-0", "7", " -2"}) {
            assertThrows(IllegalArgumentException.class, () -> S3kEndingSamplerTool.Arguments.parse(arguments("sonic-solo", progress, "spots")));
        }
        for (String progress : new String[]{"-2", "0"}) {
            assertThrows(IllegalArgumentException.class, () -> S3kEndingSamplerTool.Arguments.parse(arguments("sonic-solo", progress, "title")));
        }
        assertThrows(IllegalArgumentException.class, () -> S3kEndingSamplerTool.Arguments.parse(new String[0]));
        assertThrows(IllegalArgumentException.class, () -> S3kEndingSamplerTool.Arguments.parse(new String[]{"--rom"}));
        assertThrows(IllegalArgumentException.class, () -> S3kEndingSamplerTool.Arguments.parse(new String[]{"--rom", "one", "--rom", "two"}));
        String[] repeated = java.util.Arrays.copyOf(valid, valid.length + 2);
        repeated[valid.length] = "--completion"; repeated[valid.length + 1] = "title";
        assertThrows(IllegalArgumentException.class, () -> S3kEndingSamplerTool.Arguments.parse(repeated));
    }

    @Test void spotsAreObservationIndicesAndMustBeUniqueBoundedAndNonempty() throws Exception {
        Path file = directory.resolve("spots.txt");
        Files.writeString(file, "2\n32\n5120\n6144\n");
        assertEquals(Set.of(2, 32, 5120, 6144), S3kEndingSamplerTool.readSpots(file));
        for (String invalid : new String[]{"", "2\n2\n", "-1\n", "60000\n", "01\n", " 2\n", "\n"}) {
            Files.writeString(file, invalid);
            assertThrows(IllegalArgumentException.class, () -> S3kEndingSamplerTool.readSpots(file), invalid);
        }
    }
}
