package com.openggf.stageplay;

import com.openggf.game.ModApi;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import static com.openggf.stageplay.StageplayDocument.*;

/** Pure dialogue layout and silent, random-access reveal. No fonts, assets or gameplay owners are loaded. */
@ModApi
public final class StageplayDialogue {
    /** Existing SceneCanvas font metrics: unsupported UTF-16 cells still occupy their normal advance. */
    public static final int FONT_ADVANCE = 10;
    public static final int GLYPH_HEIGHT = 10;
    public static final int LINE_HEIGHT = 12;
    public static final int NAME_HEIGHT = 14;
    public static final int PORTRAIT_SIZE = 32;
    public static final int PORTRAIT_RESERVE = 40;

    private static final Pattern GRAPHEME = Pattern.compile("\\X");
    private static final DialogueTyper DEFAULT_TYPER = new DialogueTyper("builtin-default", 2, 6, 0);
    private static final DialogueSkin DEFAULT_SKIN = new DialogueSkin("builtin-default", 0xfff6f0dc,
            0xffffd45c, 0xff101827, 0xff8895a7, 6, "", 0, true);

    private StageplayDialogue() { }

    public static DialogueTyper typer(StageplayDocument document, DialogueCue cue) {
        return resolveTyper(Objects.requireNonNull(document, "document").dialogueTypers(), cue.typerId());
    }

    public static DialogueSkin skin(StageplayDocument document, DialogueCue cue) {
        return resolveSkin(Objects.requireNonNull(document, "document").dialogueSkins(), cue.skinId());
    }

    /** An explicit name overrides the role's display name; a nonempty role reference must always resolve. */
    public static String speakerName(StageplayDocument document, DialogueCue cue) {
        Objects.requireNonNull(document, "document");
        return speakerName(document.actors(), document.objectActors(), cue);
    }

    static String speakerName(List<Actor> actors, List<ObjectActor> objects, DialogueCue cue) {
        String name = cue.speakerName();
        if (!cue.speakerActor().isEmpty()) {
            String actorName = actors.stream().filter(actor -> actor.id().equals(cue.speakerActor()))
                    .map(Actor::name).findFirst().orElseGet(() -> objects.stream()
                            .filter(actor -> actor.id().equals(cue.speakerActor())).map(ObjectActor::name).findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("Unknown dialogue speaker: " + cue.speakerActor())));
            if (name.isEmpty()) name = actorName;
        }
        return name;
    }

    public static int graphemeCount(String text) {
        var matcher = GRAPHEME.matcher(Objects.requireNonNull(text, "text"));
        int count = 0;
        while (matcher.find()) count++;
        return count;
    }

    /**
     * Elapsed scene frames at which each grapheme becomes visible. The first automatic glyph is
     * visible at zero; each following glyph waits the cadence plus any pause after its predecessor.
     * A zero cadence ignores pauses. Explicit schedules can reveal several graphemes together.
     */
    public static List<Integer> revealFrames(StageplayDocument document, DialogueCue cue) {
        return revealFrames(cue, typer(document, cue));
    }

    /** Prefix of the original text, never splitting a Unicode grapheme. Negative elapsed time is empty. */
    public static String visibleText(StageplayDocument document, DialogueCue cue, int elapsedFrames) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(cue, "cue");
        if (elapsedFrames < 0) return "";
        if (!cue.revealFrames().isEmpty()) {
            List<Integer> frames = cue.revealFrames();
            int low = 0, high = frames.size();
            while (low < high) {
                int middle = (low + high) >>> 1;
                if (frames.get(middle) <= elapsedFrames) low = middle + 1;
                else high = middle;
            }
            return prefix(cue.text(), low);
        }
        DialogueTyper typer = typer(document, cue);
        if (typer.framesPerGrapheme() == 0) return cue.text();
        var matcher = GRAPHEME.matcher(cue.text());
        int revealAt = 0, end = 0;
        // Stop at the first unrevealed cluster; inactive cues are never evaluated by the timeline.
        while (revealAt <= elapsedFrames && matcher.find()) {
            end = matcher.end();
            revealAt += typer.framesPerGrapheme() + pause(matcher.group(), typer);
        }
        return cue.text().substring(0, end);
    }

    /**
     * Wraps the complete text before reveal. Row positions never depend on the visible prefix.
     * The panel reserves a name row and, when present, 40 pixels beside it for a 32x32 portrait.
     * Oversized names, graphemes or bodies fail rather than being silently truncated.
     */
    public static Layout layout(StageplayDocument document, DialogueCue cue) {
        Objects.requireNonNull(document, "document");
        if (cue.x() + cue.width() > document.width() || cue.y() + cue.height() > document.height())
            throw invalid(cue, "panel exceeds the viewport");
        return layout(cue, skin(document, cue), speakerName(document, cue));
    }

    /** Fixed layout with absolute screen coordinates; row n is drawn at textY+n*LINE_HEIGHT. */
    @ModApi
    public record Layout(String speakerName, int nameX, int nameY, int textX, int textY,
                         int textWidth, int textHeight, List<Row> rows) {
        public Layout {
            Objects.requireNonNull(speakerName, "speakerName");
            rows = List.copyOf(rows);
            if (textWidth <= 0 || textHeight < GLYPH_HEIGHT) throw new IllegalArgumentException("Invalid dialogue layout extent");
        }
    }

    /** A displayed row and its half-open grapheme range in the original cue text. */
    @ModApi
    public record Row(String text, int startGrapheme, int endGrapheme) {
        public Row {
            Objects.requireNonNull(text, "text");
            if (startGrapheme < 0 || endGrapheme < startGrapheme || endGrapheme - startGrapheme != graphemeCount(text))
                throw new IllegalArgumentException("Invalid dialogue row grapheme range");
        }
    }

    /** Applies a sampled prefix to the precomputed rows, retaining every row including unrevealed ones. */
    public static List<String> visibleRows(Layout layout, String visibleText) {
        Objects.requireNonNull(layout, "layout");
        int visible = graphemeCount(visibleText);
        return layout.rows().stream().map(row -> prefix(row.text(),
                Math.max(0, Math.min(row.endGrapheme(), visible) - row.startGrapheme()))).toList();
    }

    static DialogueTyper resolveTyper(List<DialogueTyper> typers, String id) {
        if (id.isEmpty()) return DEFAULT_TYPER;
        return typers.stream().filter(typer -> typer.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown dialogue typer: " + id));
    }

    static DialogueSkin resolveSkin(List<DialogueSkin> skins, String id) {
        if (id.isEmpty()) return DEFAULT_SKIN;
        return skins.stream().filter(skin -> skin.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown dialogue skin: " + id));
    }

    static void validate(DialogueCue cue, String speakerName, DialogueSkin skin, DialogueTyper typer) {
        layout(cue, skin, speakerName);
        revealFrames(cue, typer);
    }

    private static List<Integer> revealFrames(DialogueCue cue, DialogueTyper typer) {
        if (!cue.revealFrames().isEmpty()) return cue.revealFrames();
        var result = new ArrayList<Integer>();
        var matcher = GRAPHEME.matcher(cue.text());
        int revealAt = 0;
        while (matcher.find()) {
            if (revealAt >= cue.endFrame() - cue.startFrame())
                throw invalid(cue, "typing does not finish within the cue; extend its duration or change its typer");
            result.add(revealAt);
            if (typer.framesPerGrapheme() != 0) revealAt += typer.framesPerGrapheme() + pause(matcher.group(), typer);
        }
        return List.copyOf(result);
    }

    private static Layout layout(DialogueCue cue, DialogueSkin skin, String speakerName) {
        int portrait = cue.portraitAsset().isEmpty() ? 0 : PORTRAIT_RESERVE;
        int width = cue.width() - 2 * skin.padding() - portrait;
        int height = cue.height() - 2 * skin.padding() - NAME_HEIGHT;
        if (width < FONT_ADVANCE - 1 || height < GLYPH_HEIGHT
                || (portrait != 0 && cue.height() - 2 * skin.padding() < PORTRAIT_SIZE))
            throw invalid(cue, "panel has insufficient space for its padding, name, text and portrait");
        if (!skin.decorationAsset().isEmpty()
                && (2 * skin.decorationBorder() >= cue.width() || 2 * skin.decorationBorder() >= cue.height()))
            throw invalid(cue, "decoration border leaves no panel centre");
        if (speakerName.length() * FONT_ADVANCE - 1 > width)
            throw invalid(cue, "speaker name does not fit; enlarge the panel or shorten its name override");
        var rows = wrap(cue, width);
        if ((rows.size() - 1) * LINE_HEIGHT + GLYPH_HEIGHT > height)
            throw invalid(cue, "text overflows the panel; enlarge it or split the dialogue");
        int x = cue.x() + skin.padding() + portrait, y = cue.y() + skin.padding();
        return new Layout(speakerName, x, y, x, y + NAME_HEIGHT, width, height, rows);
    }

    private static List<Row> wrap(DialogueCue cue, int width) {
        var graphemes = new ArrayList<String>();
        var offsets = new ArrayList<Integer>();
        var matcher = GRAPHEME.matcher(cue.text());
        while (matcher.find()) { offsets.add(matcher.start()); graphemes.add(matcher.group()); }
        offsets.add(cue.text().length());
        int cells = (width + 1) / FONT_ADVANCE;
        var rows = new ArrayList<Row>();
        int paragraph = 0;
        while (paragraph <= graphemes.size()) {
            int end = paragraph;
            while (end < graphemes.size() && !newline(graphemes.get(end))) end++;
            if (paragraph == end) rows.add(new Row("", paragraph, paragraph));
            int cursor = paragraph;
            while (cursor < end) {
                int fit = cursor, used = 0, space = -1;
                while (fit < end && used + graphemes.get(fit).length() <= cells) {
                    used += graphemes.get(fit).length();
                    if (fit > cursor && whitespace(graphemes.get(fit))) space = fit;
                    fit++;
                }
                if (fit == cursor) throw invalid(cue, "a complete grapheme is wider than the text area");
                int stop = fit < end && !whitespace(graphemes.get(fit)) && space > cursor ? space : fit;
                rows.add(new Row(cue.text().substring(offsets.get(cursor), offsets.get(stop)), cursor, stop));
                cursor = stop;
                if (cursor < end) while (cursor < end && whitespace(graphemes.get(cursor))) cursor++;
            }
            if (end == graphemes.size()) break;
            paragraph = end + 1;
        }
        return List.copyOf(rows);
    }

    private static String prefix(String text, int graphemes) {
        if (graphemes <= 0) return "";
        var matcher = GRAPHEME.matcher(text);
        int end = 0;
        while (graphemes-- > 0 && matcher.find()) end = matcher.end();
        return text.substring(0, end);
    }

    private static boolean newline(String grapheme) { return grapheme.equals("\n") || grapheme.equals("\r") || grapheme.equals("\r\n"); }
    private static boolean whitespace(String grapheme) { return grapheme.codePoints().allMatch(Character::isWhitespace); }

    private static int pause(String grapheme, DialogueTyper typer) {
        if (whitespace(grapheme)) return typer.spacePauseFrames();
        for (int at = 0; at < grapheme.length(); ) {
            int codePoint = grapheme.codePointAt(at);
            int kind = Character.getType(codePoint);
            if (kind == Character.CONNECTOR_PUNCTUATION || kind == Character.DASH_PUNCTUATION
                    || kind == Character.START_PUNCTUATION || kind == Character.END_PUNCTUATION
                    || kind == Character.INITIAL_QUOTE_PUNCTUATION || kind == Character.FINAL_QUOTE_PUNCTUATION
                    || kind == Character.OTHER_PUNCTUATION) return typer.punctuationPauseFrames();
            at += Character.charCount(codePoint);
        }
        return 0;
    }

    private static IllegalArgumentException invalid(DialogueCue cue, String message) {
        return new IllegalArgumentException("Dialogue " + cue.id() + ": " + message);
    }
}
