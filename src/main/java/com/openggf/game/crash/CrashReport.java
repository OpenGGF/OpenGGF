package com.openggf.game.crash;

import com.openggf.version.AppVersion;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * One engine fault, captured before the session it broke is torn down. Text leaving the process
 * (the report file, clipboard and issue link) names the player's home folder as {@code ~}.
 */
public final class CrashReport {
    /** A labelled fact about the build, machine or session. */
    public record Field(String label, String value) {
        public Field {
            Objects.requireNonNull(label, "label");
            value = value == null || value.isBlank() ? "unknown" : value;
        }
    }

    private static final String ENGINE_PACKAGE = "com.openggf.";
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC);

    private final FaultKind kind;
    private final Throwable failure;
    private final Instant time;
    private final List<Field> build;
    private final List<Field> session;
    private final String home;

    CrashReport(FaultKind kind, Throwable failure, Instant time, List<Field> build, List<Field> session,
                String home) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.failure = Objects.requireNonNull(failure, "failure");
        this.time = Objects.requireNonNull(time, "time");
        this.build = List.copyOf(build);
        this.session = List.copyOf(session);
        this.home = home == null ? "" : home;
    }

    /** Captures {@code failure} with this process's build facts and the caller's session facts. */
    public static CrashReport capture(Throwable failure, List<Field> session) {
        return new CrashReport(FaultKind.classify(failure), failure, Instant.now(), buildFields(),
                session, System.getProperty("user.home", ""));
    }

    static List<Field> buildFields() {
        return List.of(
                new Field("Version", AppVersion.get()),
                new Field("OS", System.getProperty("os.name", "") + " " + System.getProperty("os.version", "")
                        + " (" + System.getProperty("os.arch", "") + ")"),
                new Field("Java", System.getProperty("java.version", "") + " "
                        + System.getProperty("java.vm.name", "")));
    }

    public FaultKind kind() { return kind; }
    public Throwable failure() { return failure; }
    public Instant time() { return time; }
    public List<Field> buildFacts() { return build; }
    public List<Field> sessionFacts() { return session; }

    /** The failure's type and message as thrown, e.g. {@code IllegalStateException: no keyframe}. */
    public String error() { return describe(failure); }

    /** The innermost cause when it differs from {@link #error()}, otherwise empty. */
    public String rootCause() {
        Throwable root = root(failure);
        return root == failure ? "" : describe(root);
    }

    /** The first engine frame of the innermost cause, where the fault was raised. */
    public String where() {
        Throwable root = root(failure);
        for (StackTraceElement frame : root.getStackTrace()) {
            if (frame.getClassName().startsWith(ENGINE_PACKAGE)) return frame(frame);
        }
        StackTraceElement[] frames = root.getStackTrace();
        return frames.length == 0 ? "unknown" : frame(frames[0]);
    }

    /** The whole report: facts, then every stack frame. */
    public String fullText() {
        StringBuilder text = new StringBuilder();
        text.append("OpenGGF crash report\n");
        text.append("Kind: ").append(kind.name()).append('\n');
        text.append("Time: ").append(STAMP.format(time)).append('\n');
        appendFields(text, build);
        appendFields(text, session);
        text.append('\n');
        StringWriter trace = new StringWriter();
        failure.printStackTrace(new PrintWriter(trace));
        text.append(trace);
        return redact(text.toString());
    }

    /**
     * A shortened trace for the issue link: each throwable's first {@code framesEach} frames, then
     * how many were left out. The report file keeps them all.
     */
    public String shortTrace(int framesEach) {
        StringBuilder text = new StringBuilder();
        List<Throwable> chain = FaultKind.chain(failure);
        boolean first = true;
        for (Throwable each : chain) {
            if (!first) text.append(isCause(chain, each) ? "Caused by: " : "Suppressed: ");
            text.append(describe(each)).append('\n');
            StackTraceElement[] frames = each.getStackTrace();
            int shown = Math.min(framesEach, frames.length);
            for (int i = 0; i < shown; i++) text.append("    at ").append(frame(frames[i])).append('\n');
            if (frames.length > shown) text.append("    ... ").append(frames.length - shown).append(" more\n");
            first = false;
        }
        return redact(text.toString().stripTrailing());
    }

    /** Facts as {@code Label: value} lines, for the issue form. */
    public static String lines(List<Field> fields) {
        StringBuilder text = new StringBuilder();
        appendFields(text, fields);
        return text.toString().stripTrailing();
    }

    /** Replaces the home folder with {@code ~} so a shared report does not carry the player's account name. */
    public String redact(String text) {
        if (home.length() < 2 || text == null) return text;
        return text.replace(home, "~");
    }

    private static boolean isCause(List<Throwable> chain, Throwable candidate) {
        for (Throwable each : chain) {
            if (each.getCause() == candidate) return true;
        }
        return false;
    }

    private static void appendFields(StringBuilder text, List<Field> fields) {
        for (Field field : fields) text.append(field.label()).append(": ").append(field.value()).append('\n');
    }

    private static Throwable root(Throwable failure) {
        java.util.Set<Throwable> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        Throwable each = failure;
        while (seen.add(each) && each.getCause() != null && !seen.contains(each.getCause())) {
            each = each.getCause();
        }
        return each;
    }

    private static String describe(Throwable failure) {
        String message = failure.getMessage();
        String type = failure.getClass().getSimpleName();
        if (type.isEmpty()) type = failure.getClass().getName();
        return message == null || message.isBlank() ? type : type + ": " + message;
    }

    private static String frame(StackTraceElement frame) {
        String owner = frame.getClassName();
        String simple = owner.substring(owner.lastIndexOf('.') + 1);
        String file = frame.getFileName() == null ? "" : frame.getFileName();
        int line = frame.getLineNumber();
        return simple + "." + frame.getMethodName() + "(" + file + (line > 0 ? ":" + line : "") + ")";
    }
}
