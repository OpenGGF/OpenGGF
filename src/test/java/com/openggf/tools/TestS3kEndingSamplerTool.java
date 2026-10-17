package com.openggf.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class TestS3kEndingSamplerTool {
    @TempDir Path directory;

    @Test void acceptsOnlyTheExplicitQualifiedProductAndUniqueOptions() {
        String[] valid = {"--rom", "original.gen", "--out-dir", "capture", "--spots", "spots.txt",
                "--product", "tails-solo", "--region", "ntsc", "--progress", "+2"};
        assertEquals(Path.of("capture"), S3kEndingSamplerTool.Arguments.parse(valid).output());
        for (int index : new int[]{7, 9, 11}) {
            String[] invalid = valid.clone();
            invalid[index] = "unsupported";
            assertThrows(IllegalArgumentException.class, () -> S3kEndingSamplerTool.Arguments.parse(invalid));
        }
        assertThrows(IllegalArgumentException.class, () -> S3kEndingSamplerTool.Arguments.parse(new String[0]));
        assertThrows(IllegalArgumentException.class, () -> S3kEndingSamplerTool.Arguments.parse(new String[]{"--rom"}));
        assertThrows(IllegalArgumentException.class, () -> S3kEndingSamplerTool.Arguments.parse(new String[]{"--rom", "one", "--rom", "two"}));
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
