package com.openggf;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Copies callback data only; the next UI frame owns path validation and modal admission. */
final class ContentImportWindowBridge {
    private final Consumer<List<Path>> drop;
    private final Supplier<String> blockedReason;
    private final Consumer<String> feedback;
    private List<String> pending;

    ContentImportWindowBridge(Consumer<List<Path>> drop, Supplier<String> blockedReason,
                              Consumer<String> feedback) {
        this.drop = Objects.requireNonNull(drop);
        this.blockedReason = Objects.requireNonNull(blockedReason);
        this.feedback = Objects.requireNonNull(feedback);
    }

    void receive(List<String> paths) {
        pending = List.copyOf(paths);
    }

    void update() {
        var names = pending;
        pending = null;
        if (names == null || names.isEmpty()) return;
        String reason = blockedReason.get();
        if (reason != null) { feedback.accept(reason); return; }
        try { drop.accept(names.stream().map(Path::of).toList()); }
        catch (InvalidPathException failure) { feedback.accept("Cannot import this path: " + failure.getReason()); }
    }
}
