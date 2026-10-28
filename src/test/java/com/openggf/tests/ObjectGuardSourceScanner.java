package com.openggf.tests;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.RandomAccess;
import java.util.stream.Stream;

public final class ObjectGuardSourceScanner {
    public static final String[] GAME_OBJECT_PACKAGE_PATHS = {
            "com/openggf/game/sonic1/objects",
            "com/openggf/game/sonic2/objects",
            "com/openggf/game/sonic3k/objects",
    };

    public static final String[] OBJECT_PACKAGE_PATHS = {
            "com/openggf/game/sonic1/objects",
            "com/openggf/game/sonic2/objects",
            "com/openggf/game/sonic3k/objects",
            "com/openggf/level/objects",
    };

    private ObjectGuardSourceScanner() {
    }

    public static Path findSourceRoot() {
        Path cwd = Path.of(System.getProperty("user.dir"));
        Path srcMain = cwd.resolve("src/main/java");
        if (Files.isDirectory(srcMain)) {
            return srcMain;
        }
        Path parent = cwd.getParent();
        if (parent == null) {
            return null;
        }
        srcMain = parent.resolve("src/main/java");
        return Files.isDirectory(srcMain) ? srcMain : null;
    }

    public static List<Path> javaFilesUnderPackages(Path srcMain, String[] packages) throws IOException {
        List<Path> result = new ArrayList<>();
        for (String pkg : packages) {
            Path pkgDir = srcMain.resolve(pkg);
            if (!Files.isDirectory(pkgDir)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(pkgDir)) {
                files.filter(path -> path.toString().endsWith(".java"))
                        .forEach(result::add);
            }
        }
        return result;
    }

    /** Optional source tree, ordered by portable path for deterministic diagnostics. */
    public static List<Path> sortedJavaSources(Path root) throws IOException {
        if (!Files.exists(root)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(path -> path.toString().endsWith(".java"))
                    .sorted(java.util.Comparator.comparing(path -> path.toString().replace('\\', '/')))
                    .toList();
        }
    }

    public static String className(Path srcMain, Path sourceFile) {
        return srcMain.relativize(sourceFile).toString()
                .replace('\\', '/').replace(".java", "").replace('/', '.');
    }

    public static SourceText sourceWithoutCommentOnlyLines(List<String> lines) {
        StringBuilder source = new StringBuilder();
        int[] lineStarts = new int[lines.size()];
        List<String> sourceLines = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            String scannedLine = (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*"))
                    ? ""
                    : line;
            lineStarts[i] = source.length();
            sourceLines.add(scannedLine);
            source.append(scannedLine);
            source.append('\n');
        }
        return new SourceText(source.toString(), new LineByOffset(lineStarts, source.length()), sourceLines);
    }

    /**
     * Ordinary scans need one offset per input line, not a boxed entry per UTF-16
     * character. Keep the factory's mutable List contract by materializing the
     * expanded form only when a caller changes it.
     */
    private static final class LineByOffset extends AbstractList<Integer> implements RandomAccess {
        private final int[] lineStarts;
        private final int originalSize;
        private List<Integer> expanded;

        private LineByOffset(int[] lineStarts, int originalSize) {
            this.lineStarts = lineStarts;
            this.originalSize = originalSize;
        }

        @Override
        public Integer get(int index) {
            if (expanded != null) {
                return expanded.get(index);
            }
            Objects.checkIndex(index, originalSize);
            int found = Arrays.binarySearch(lineStarts, index);
            return found >= 0 ? found + 1 : -found - 1;
        }

        @Override
        public int size() {
            return expanded == null ? originalSize : expanded.size();
        }

        @Override
        public Integer set(int index, Integer element) {
            return expanded().set(index, element);
        }

        @Override
        public void add(int index, Integer element) {
            expanded().add(index, element);
            modCount++;
        }

        @Override
        public Integer remove(int index) {
            Integer previous = expanded().remove(index);
            modCount++;
            return previous;
        }

        private List<Integer> expanded() {
            if (expanded == null) {
                List<Integer> values = new ArrayList<>(originalSize);
                for (int i = 0; i < originalSize; i++) {
                    values.add(get(i));
                }
                expanded = values;
            }
            return expanded;
        }
    }

    public record SourceText(String text, List<Integer> lineByOffset, List<String> lines) {
        public int lineAt(int offset) {
            if (lineByOffset.isEmpty()) {
                return 1;
            }
            return lineByOffset.get(Math.min(offset, lineByOffset.size() - 1));
        }

        public String lineTextAt(int offset) {
            int lineNumber = lineAt(offset);
            if (lineNumber < 1 || lineNumber > lines.size()) {
                return "";
            }
            return lines.get(lineNumber - 1);
        }
    }
}
