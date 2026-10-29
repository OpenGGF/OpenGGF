package com.openggf.tests;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestObjectGuardSourceScanner {
    @TempDir Path root;

    @Test
    void preservesOptionalRootsRecursiveTraversalAndPortableOrdering() throws Exception {
        Path nested = Files.createDirectory(root.resolve("a"));
        Path last = Files.writeString(root.resolve("z.java"), "");
        Path first = Files.writeString(nested.resolve("A.java"), "");
        Files.writeString(root.resolve("ignored.txt"), "");
        // Existing guards filter suffixes rather than regular files; preserve that contract.
        Path javaDirectory = Files.createDirectory(root.resolve("d.java"));
        assertEquals(List.of(first, javaDirectory, last),
                ObjectGuardSourceScanner.sortedJavaSources(root));
        assertEquals(List.of(), ObjectGuardSourceScanner.sortedJavaSources(root.resolve("missing")));
        assertEquals(List.of(last), ObjectGuardSourceScanner.sortedJavaSources(last));
        assertThrows(UnsupportedOperationException.class,
                () -> ObjectGuardSourceScanner.sortedJavaSources(root).clear());
    }

    @Test
    void compactIndexMatchesHistoricalMappingAtEveryUtf16Offset() {
        checkHistoricalMapping(List.of());
        checkHistoricalMapping(List.of("", " // removed", "* removed", "/* removed", "/** removed",
                "code // retained", "\tindented", "a\nb", "astral \uD83D\uDE80 and combining e\u0301", ""));
        // Crossing 127 catches the old Integer-cache boundary; empty and comment
        // lines must still own their appended newline, including at EOF.
        List<String> manyLines = new ArrayList<>();
        for (int i = 0; i < 260; i++) {
            manyLines.add(i % 3 == 0 ? " // ignored " + i : "line " + i);
        }
        checkHistoricalMapping(manyLines);
        Random random = new Random(0x51C0FFEE);
        String[] fragments = {"", "x", " ", "\t", "//", "/*", "*", "\n", "\uD83D\uDE80", "\u0301"};
        for (int sample = 0; sample < 200; sample++) {
            List<String> lines = new ArrayList<>();
            for (int line = 0, count = random.nextInt(20); line < count; line++) {
                StringBuilder text = new StringBuilder();
                for (int part = 0, countParts = random.nextInt(12); part < countParts; part++) {
                    text.append(fragments[random.nextInt(fragments.length)]);
                }
                lines.add(text.toString());
            }
            checkHistoricalMapping(lines);
        }
    }

    @Test
    void compactIndexRetainsTheFactorysMutableListBehavior() {
        ObjectGuardSourceScanner.SourceText source =
                ObjectGuardSourceScanner.sourceWithoutCommentOnlyLines(List.of("ab", "", "cd"));
        List<Integer> actual = source.lineByOffset();
        List<Integer> expected = new ArrayList<>(List.of(1, 1, 1, 2, 3, 3, 3));
        assertEquals(expected.set(0, 9), actual.set(0, 9));
        actual.add(1, 7);
        expected.add(1, 7);
        assertEquals(expected.remove(2), actual.remove(2));
        actual.addAll(List.of(5, 6));
        expected.addAll(List.of(5, 6));
        actual.subList(2, 4).clear();
        expected.subList(2, 4).clear();
        actual.replaceAll(value -> value + 1);
        expected.replaceAll(value -> value + 1);
        assertEquals(expected, actual);
        assertEquals(expected.hashCode(), actual.hashCode());
        assertEquals(expected.getFirst(), source.lineAt(0));
        assertEquals(expected.getLast(), source.lineAt(Integer.MAX_VALUE));
        actual.clear();
        assertEquals(List.of(), actual);
        assertEquals(1, source.lineAt(-1));
        actual.add(null);
        assertEquals(java.util.Collections.singletonList(null), actual);
        assertThrows(NullPointerException.class, () -> source.lineAt(0));
    }

    private static void checkHistoricalMapping(List<String> lines) {
        StringBuilder text = new StringBuilder();
        List<Integer> offsets = new ArrayList<>();
        List<String> filteredLines = new ArrayList<>();
        // Deliberate copy of the old implementation: independent equivalence oracle.
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            String scanned = trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")
                    ? "" : line;
            filteredLines.add(scanned);
            text.append(scanned);
            for (int j = 0; j < scanned.length(); j++) {
                offsets.add(i + 1);
            }
            text.append('\n');
            offsets.add(i + 1);
        }
        ObjectGuardSourceScanner.SourceText actual = ObjectGuardSourceScanner.sourceWithoutCommentOnlyLines(lines);
        assertEquals(text.toString(), actual.text());
        assertEquals(filteredLines, actual.lines());
        assertEquals(offsets, actual.lineByOffset());
        assertEquals(offsets.hashCode(), actual.lineByOffset().hashCode());
        for (int offset = 0; offset <= offsets.size() + 3; offset++) {
            int expectedLine = offsets.isEmpty() ? 1 : offsets.get(Math.min(offset, offsets.size() - 1));
            assertEquals(expectedLine, actual.lineAt(offset), "UTF-16 offset " + offset);
            assertEquals(filteredLines.isEmpty() ? "" : filteredLines.get(expectedLine - 1), actual.lineTextAt(offset));
        }
        assertEquals(offsets.isEmpty() ? 1 : offsets.getLast(), actual.lineAt(Integer.MAX_VALUE));
        if (offsets.isEmpty()) {
            assertEquals(1, actual.lineAt(-1));
            assertEquals("", actual.lineTextAt(-1));
        } else {
            assertThrows(IndexOutOfBoundsException.class, () -> actual.lineAt(-1));
            assertThrows(IndexOutOfBoundsException.class, () -> actual.lineTextAt(Integer.MIN_VALUE));
        }
        assertThrows(IndexOutOfBoundsException.class, () -> actual.lineByOffset().get(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> actual.lineByOffset().get(actual.lineByOffset().size()));
    }
}
