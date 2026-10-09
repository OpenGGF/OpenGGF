package typinghog.hub.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Choosing menu items by typing their names, as The Typing of the Dead's menus do: typing
 * {@code story} selects Story. Engine-free; the scene feeds it each tick's typed text.
 *
 * <p>Each item answers to one or more words (letters, digits and spaces, matched without
 * case). Typed characters build a buffer; while the buffer begins some item's word the cursor
 * follows the first such item (the current one when it still matches). Completing a word
 * selects its item. A character that fits no word starts the buffer again from itself, so a
 * slip only costs the word in progress; if even that fits nothing the buffer empties and the
 * keystroke is reported as rejected. Arrow keys keep working beside it ({@link #move}).
 */
public final class WordMenu {
    /** What a run of typed text did. */
    public enum Outcome { NONE, MOVED, SELECTED, REJECTED }

    /** A menu item: what it shows and the words that choose it. */
    public record Item(String label, List<String> words) {
        public Item {
            words = words.stream().map(WordMenu::normalise).filter(w -> !w.isEmpty()).toList();
            if (words.isEmpty()) throw new IllegalArgumentException("Menu item " + label + " has no words");
        }

        public static Item of(String label, String... words) {
            return new Item(label, words.length == 0 ? List.of(label) : List.of(words));
        }
    }

    private final List<Item> items = new ArrayList<>();
    private final StringBuilder buffer = new StringBuilder();
    private int cursor;
    private int selected = -1;

    public WordMenu(List<Item> items) {
        if (items.isEmpty()) throw new IllegalArgumentException("A menu needs items");
        this.items.addAll(items);
    }

    public List<Item> items() { return List.copyOf(items); }
    public int cursor() { return cursor; }
    public String buffer() { return buffer.toString(); }

    /** The item a {@link Outcome#SELECTED} outcome chose. */
    public int selected() { return selected; }

    public void setCursor(int index) {
        cursor = Math.floorMod(index, items.size());
    }

    /** Moves the cursor by {@code delta} rows, wrapping, and forgets the typed word. */
    public void move(int delta) {
        setCursor(cursor + delta);
        buffer.setLength(0);
    }

    public void clear() { buffer.setLength(0); }

    public void backspace() {
        if (buffer.length() > 0) buffer.setLength(buffer.length() - 1);
    }

    /** How many leading characters of {@code item}'s best-matching word the buffer covers. */
    public int matched(int item) {
        String typed = buffer.toString();
        if (typed.isEmpty()) return 0;
        for (String word : items.get(item).words()) if (word.startsWith(typed)) return typed.length();
        return 0;
    }

    /** The word of {@code item} the buffer is spelling (its first word when none matches). */
    public String spelling(int item) {
        String typed = buffer.toString();
        for (String word : items.get(item).words()) if (!typed.isEmpty() && word.startsWith(typed)) return word;
        return items.get(item).words().getFirst();
    }

    /** Applies typed text in order; the strongest outcome wins (a selection ends the run). */
    public Outcome type(String text) {
        Outcome result = Outcome.NONE;
        for (int i = 0; i < text.length(); i++) {
            Outcome step = type(text.charAt(i));
            if (step == Outcome.SELECTED) return step;
            if (step.ordinal() > result.ordinal()) result = step;
        }
        return result;
    }

    private Outcome type(char raw) {
        char c = Character.toLowerCase(raw);
        if (!Character.isLetterOrDigit(c) && c != ' ') return Outcome.NONE;
        if (c == ' ' && buffer.isEmpty()) return Outcome.NONE;
        String candidate = buffer.toString() + c;
        if (follow(candidate)) return finishOrMove(candidate);
        buffer.setLength(0);
        if (c != ' ' && follow(String.valueOf(c))) return finishOrMove(String.valueOf(c));
        return Outcome.REJECTED;
    }

    /** Moves the cursor to an item with a word starting with {@code prefix}; false if none has one. */
    private boolean follow(String prefix) {
        if (starts(cursor, prefix)) return true;
        for (int i = 0; i < items.size(); i++) {
            if (starts(i, prefix)) {
                cursor = i;
                return true;
            }
        }
        return false;
    }

    private boolean starts(int item, String prefix) {
        for (String word : items.get(item).words()) if (word.startsWith(prefix)) return true;
        return false;
    }

    private Outcome finishOrMove(String typed) {
        buffer.setLength(0);
        buffer.append(typed);
        for (String word : items.get(cursor).words()) {
            if (word.equals(typed)) {
                selected = cursor;
                buffer.setLength(0);
                return Outcome.SELECTED;
            }
        }
        return Outcome.MOVED;
    }

    static String normalise(String word) {
        StringBuilder out = new StringBuilder();
        for (char c : word.toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(c)) out.append(c);
            else if (c == ' ' && out.length() > 0 && out.charAt(out.length() - 1) != ' ') out.append(' ');
        }
        return out.toString().strip();
    }
}
