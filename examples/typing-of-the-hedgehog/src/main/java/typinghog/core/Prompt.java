package typinghog.core;

import java.util.Locale;
import java.util.Objects;

/**
 * Text a target carries. {@link #display} is what the plate shows; {@link #typed} is what the
 * player must type under the current rules: lower-cased unless capitals count, and with
 * punctuation removed unless punctuation counts (the plate still shows it, dimmed). Spaces are
 * always typed.
 */
public record Prompt(String display, String typed) {
    public Prompt {
        Objects.requireNonNull(display, "display");
        Objects.requireNonNull(typed, "typed");
        if (typed.isEmpty()) throw new IllegalArgumentException("Prompt has nothing to type: " + display);
    }

    /** Builds the typed form of {@code display} under the given rules. */
    public static Prompt of(String display, boolean capitals, boolean punctuation) {
        StringBuilder typed = new StringBuilder(display.length());
        for (int i = 0; i < display.length(); ) {
            int cp = display.codePointAt(i);
            i += Character.charCount(cp);
            if (!punctuation && !Character.isLetterOrDigit(cp) && cp != ' ') continue;
            typed.appendCodePoint(capitals ? cp : Character.toLowerCase(cp));
        }
        String form = typed.toString().replaceAll(" +", " ").strip();
        return new Prompt(display, form);
    }

    /** First character the player types, used to keep visible prompts distinguishable. */
    public int lead() {
        return typed.codePointAt(0);
    }

    /** True when this display character is skipped by the typed form (dimmed punctuation). */
    public static boolean skipped(int displayCodePoint, boolean punctuation) {
        return !punctuation && !Character.isLetterOrDigit(displayCodePoint) && displayCodePoint != ' ';
    }

    /** Normalises one typed character the way {@link #of} normalised the prompt. */
    public static int normalise(int codePoint, boolean capitals) {
        return capitals ? codePoint : Character.toLowerCase(codePoint);
    }

    /** Lower-case ASCII key for lexicon look-ups. */
    public String key() {
        return typed.toLowerCase(Locale.ROOT);
    }
}
