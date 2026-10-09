package nativecontrol;

import com.openggf.mods.code.GgfMod;
import com.openggf.mods.code.ModContext;

/** Runtime-loaded JDK bootstrap regression control, originating in the
 * 2026-10-09 Linux native gameplay failure. Never include in the image classpath:
 * closed-world reachability would hide missing interpreter bootstrap members.
 */
public final class BootstrapControl implements GgfMod {
    private record Pair(String name, int value) { }
    public interface Value { String value(); }

    @Override
    public void register(ModContext context) {
        Pair first = new Pair("control", 7);
        Pair second = new Pair("control", 7);
        if (!first.equals(second) || first.hashCode() != second.hashCode()
                || !first.toString().equals("Pair[name=control, value=7]")) {
            throw new AssertionError("Record bootstrap failed");
        }
        Object value = first;
        int result = switch (value) {
            case Pair pair -> pair.value();
            case String text -> text.length();
            default -> -1;
        };
        if (result != 7) throw new AssertionError("Pattern-switch bootstrap failed");
        Value proxy = (Value) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {Value.class}, (instance, method, arguments) -> "proxy control");
        if (!proxy.value().equals("proxy control")) throw new AssertionError("Runtime proxy bootstrap failed");
        String text = "native contr\u00f4le";
        if (!new String(text.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                java.nio.charset.StandardCharsets.UTF_8).equals(text)) {
            throw new AssertionError("Runtime UTF-8 constant/codec failed");
        }
        var writer = new java.io.StringWriter();
        writer.write(text);
        if (!writer.toString().equals(text)) throw new AssertionError("Runtime writer failed");
        try {
            var directory = java.nio.file.Files.createTempDirectory(java.nio.file.Path.of("."), ".native-control-");
            var firstFile = directory.resolve("first");
            var secondFile = directory.resolve("second");
            try {
                java.nio.file.Files.writeString(firstFile, text);
                java.nio.file.Files.copy(firstFile, secondFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                java.nio.file.Files.move(secondFile, firstFile, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                if (!java.nio.file.Files.readString(firstFile).equals(text)) throw new AssertionError("Runtime file copy/move failed");
            } finally {
                java.nio.file.Files.deleteIfExists(firstFile);
                java.nio.file.Files.deleteIfExists(secondFile);
                java.nio.file.Files.delete(directory);
            }
        } catch (java.io.IOException failure) { throw new AssertionError("Runtime file control failed", failure); }
        int[] numbers = {-4, 7, 7, 2};
        if (java.util.Arrays.stream(numbers).min().orElse(0) != -4
                || java.util.Arrays.stream(numbers).max().orElse(0) != 7
                || !java.util.Arrays.equals(java.util.Arrays.stream(numbers).distinct().sorted().toArray(),
                        new int[] {-4, 2, 7})
                || java.util.List.of("a", "bbb", "cc").stream().filter(item -> item.length() > 1)
                        .mapToInt(String::length).sum() != 5) {
            throw new AssertionError("Runtime primitive/reference stream dispatch failed");
        }
    }
}
