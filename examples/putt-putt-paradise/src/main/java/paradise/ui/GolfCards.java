package paradise.ui;

import java.util.List;
import java.util.Objects;

/**
 * Transitional presentation drawn over the course: the hole title sweep, the turn handoff
 * card, result toasts, the bobbing golfer tag, the last shot's trail and the course
 * progress line. Values arrive from {@link GolfFeedback} and the adapter; nothing advances here.
 */
public final class GolfCards {
    private GolfCards() { }

    /**
     * Incoming golfer at a handoff. button is the incoming player's own action-A label
     * (for example "SPACE", "RIGHT SHIFT" or "SQUARE"); viewerOwns is false for spectators.
     */
    public record Handoff(int owner, String name, boolean viewerOwns, String button, boolean requested,
                          boolean firstOfHole, int hole) {
        public Handoff {
            Objects.requireNonNull(name); Objects.requireNonNull(button);
            if (owner < 0 || owner > 1 || hole < 1 || hole > 2) throw new IllegalArgumentException("handoff");
        }
        /** "SONIC'S TURN"; names ending in S take a bare apostrophe ("TAILS' TURN"). */
        public String title() { return name + (name.endsWith("S") ? "' TURN" : "'S TURN"); }
        public String prompt() {
            if (!viewerOwns) return "WAITING FOR P" + (owner + 1) + " " + name;
            if (requested) return "READY! WAITING FOR THE HOST";
            return button.isEmpty() ? "PRESS A WHEN READY" : "PRESS " + button + " WHEN READY";
        }
    }

    // ---- hole title sweep ---------------------------------------------------------------
    public static void hole(GolfCanvas c, GolfFeedback f, int hole, String subtitle) {
        if (f.card() != GolfFeedback.Card.HOLE) return;
        float in = f.cardIn();
        float second = f.cardLeaving() ? in : GolfMotion.easeOutBack(GolfMotion.progress(f.cardAge() - 6, GolfFeedback.CARD_IN));
        int width = c.width();
        // Sonic 2 title-card homage: a blue band from the left, a gold band from the right.
        // The bands sit under the score chips, clear of the golfer at screen centre.
        var blue = c.offset(Math.round((in - 1) * width), 0);
        blue.rect(0, 38, width, 28, 0x1F49B8, 0.95f);
        blue.rect(0, 38, width, 2, 0x6F9BFF);
        blue.rect(0, 64, width, 2, 0x0D2466);
        blue.centeredShadowed("EMERALD HILL", 45, 2, GolfText.CREAM);
        var gold = c.offset(Math.round((1 - second) * width), 0);
        String line = "HOLE " + hole + "  -  " + subtitle;
        int bandWidth = GolfText.width(line, 1) + 28, left = (width - bandWidth) / 2;
        gold.rect(left, 68, bandWidth, 15, GolfText.GOLD, 0.97f);
        gold.rect(left, 68, 4, 15, 0xAC2638);
        gold.rect(left + bandWidth - 4, 68, 4, 15, 0xAC2638);
        gold.text(line, left + 14, 72, 1, GolfOverlay.NIGHT);
    }

    // ---- handoff card -------------------------------------------------------------------
    public static void handoff(GolfCanvas c, GolfFeedback f, Handoff h) {
        if (f.card() != GolfFeedback.Card.HANDOFF) return;
        float in = f.cardIn();
        int width = c.width(), cardWidth = Math.min(width - 24, 244), left = (width - cardWidth) / 2;
        // Lower third: the incoming golfer and their tag stay visible above the card.
        c.rect(0, 118, width, 92, GolfOverlay.NIGHT, 0.5f * Math.clamp(in, 0f, 1f));
        int slide = f.cardLeaving() ? Math.round((1 - in) * width) : Math.round((in - 1) * width);
        var card = c.offset(slide, 0).fade(f.cardLeaving() ? Math.max(0, in) : 1);
        int colour = GolfOverlay.characterColour(h.name());
        card.panel(left, 122, cardWidth, 82, GolfOverlay.NIGHT, 0.96f, colour);
        card.rect(left, 122, cardWidth, 16, colour, 0.95f);
        card.text("PLAYER " + (h.owner() + 1), left + 8, 127, 1, GolfOverlay.WHITE);
        String hole = h.firstOfHole() ? "EMERALD HILL " + h.hole() : "NEXT TURN";
        card.text(hole, left + cardWidth - 8 - GolfText.width(hole, 1), 127, 1, GolfOverlay.WHITE);
        String title = h.title();
        int scale = GolfText.width(title, 3) <= cardWidth - 16 ? 3 : 2;
        card.centeredShadowed(title, scale == 3 ? 145 : 148, scale, GolfText.GOLD);
        // Underline sweep grows after the card lands.
        int line = Math.round(GolfMotion.easeOut(GolfMotion.progress(f.cardAge() - GolfFeedback.CARD_IN, 16)) * (cardWidth - 40));
        card.rect(left + (cardWidth - line) / 2, 170, line, 1, colour);
        promptLine(card, h, f.clock(), 182);
    }

    /** "PRESS [SPACE] WHEN READY", with the incoming player's own binding in a keycap. */
    private static void promptLine(GolfCanvas card, Handoff h, long clock, int y) {
        if (!h.viewerOwns() || h.requested() || h.button().isEmpty()) {
            String text = h.prompt() + (h.viewerOwns() && !h.requested() ? "" : dots(clock));
            card.centered(text, y, 1, h.requested() ? GolfText.CYAN : GolfOverlay.MUTED);
            return;
        }
        String before = "PRESS ", key = h.button(), after = " WHEN READY";
        int keyWidth = GolfText.width(key, 1) + 8;
        int total = GolfText.width(before, 1) + 6 + keyWidth + GolfText.width(after, 1);
        int x = (card.width() - total) / 2;
        card.text(before, x, y, 1, GolfText.CREAM);
        x += GolfText.width(before, 1) + 6;
        float glow = 0.75f + 0.25f * GolfMotion.pulse(clock, 30);
        card.rect(x, y - 3, keyWidth, 13, GolfText.CREAM, glow);
        card.rect(x, y + 9, keyWidth, 1, 0x8A7A4F);
        card.text(key, x + 4, y, 1, GolfOverlay.NIGHT);
        x += keyWidth;
        card.text(after, x, y, 1, GolfText.CREAM);
    }

    private static String dots(long clock) { return ".".repeat((int) (Math.floorMod(clock / 15, 4))); }

    // ---- toasts -------------------------------------------------------------------------
    public static void toast(GolfCanvas c, GolfFeedback f, List<GolfOverlay.PlayerScore> players) {
        var kind = f.toast();
        if (kind == GolfFeedback.Toast.NONE) return;
        long age = f.toastAge();
        int lifetime = kind == GolfFeedback.Toast.TEE_OFF ? GolfFeedback.TEE_OFF_TICKS : GolfFeedback.TOAST_TICKS;
        float fade = 1 - GolfMotion.progress(age - (lifetime - 16), 16);
        var view = c.offset(f.shake(), 0).fade(fade);
        switch (kind) {
            case TEE_OFF -> {
                // Waits for the handoff card to leave, then rises into its place.
                if (age < GolfFeedback.CARD_OUT) break;
                int rise = Math.round((1 - GolfMotion.easeOut(GolfMotion.progress(age - GolfFeedback.CARD_OUT, 12))) * 10);
                view.centeredShadowed("TEE OFF!", 134 + rise, 3, GolfText.GOLD);
            }
            case PENALTY_DAMAGE, PENALTY_LOST, PENALTY_TIME, PENALTY -> {
                float in = GolfMotion.easeOutBack(GolfMotion.progress(age, 14));
                int w = Math.min(c.width() - 24, 220), left = (c.width() - w) / 2, top = GolfMotion.lerp(18, 36, in);
                view.panel(left, top, w, 38, 0x7A1420, 0.95f, GolfOverlay.RED);
                view.centeredShadowed("PENALTY +1", top + 6, 2, GolfOverlay.WHITE);
                String why = switch (kind) {
                    case PENALTY_DAMAGE -> "OUCH! BALL RETURNS TO ITS LIE";
                    case PENALTY_LOST -> "OUT OF BOUNDS - BALL RETURNS";
                    case PENALTY_TIME -> "TIME LIMIT - BALL RETURNS";
                    default -> "PENALTY STROKE - BALL RETURNS";
                };
                view.centered(GolfText.fit(why, w - 12, 1), top + 25, 1, 0xFFD0D0);
            }
            case FINISH, GOLFER_DONE -> {
                String title = kind == GolfFeedback.Toast.FINISH ? "HOLE COMPLETE!" : "IN THE HOLE!";
                int scale = 2, x = (c.width() - GolfText.width(title, scale)) / 2;
                for (int i = 0; i < title.length(); i++) {
                    // Letters drop in one after another and bounce on a common baseline.
                    float t = GolfMotion.bounce(GolfMotion.progress(age - i * 2, 20));
                    int y = GolfMotion.lerp(-14, 42, t);
                    view.shadowed(String.valueOf(title.charAt(i)), x + i * 6 * scale, y, scale, GolfText.GOLD);
                }
                int owner = Math.clamp(f.toastOwner(), 0, players.size() - 1);
                String who = players.isEmpty() ? "" : players.get(owner).name() + " - " + f.toastValue() + " STROKES";
                if (kind == GolfFeedback.Toast.GOLFER_DONE && players.size() > 1) who += " - P" + (2 - owner) + " PLAYS ON";
                if (age > 24) view.centeredShadowed(GolfText.fit(who, c.width() - 16, 1), 62, 1, GolfText.CREAM);
            }
            case REFUND -> {
                String text = "STROKE REFUNDED - " + (f.toastValue() < 0 ? "UNLIMITED REWINDS"
                        : f.toastValue() + (f.toastValue() == 1 ? " REWIND LEFT" : " REWINDS LEFT"));
                int w = GolfText.width(text, 1) + 14, left = (c.width() - w) / 2;
                int top = GolfMotion.lerp(24, 34, GolfMotion.easeOut(GolfMotion.progress(age, 12)));
                view.panel(left, top, w, 13, GolfOverlay.NIGHT, 0.9f, GolfText.CYAN);
                view.text(text, left + 7, top + 3, 1, GolfText.CYAN);
            }
            default -> { }
        }
    }

    /** "P2" tag bobbing above the incoming golfer, so a same-character match stays unambiguous. */
    public static void tag(GolfCanvas c, long clock, int x, int y, int owner, String name) {
        int colour = GolfOverlay.characterColour(name), bob = Math.round(GolfMotion.pulse(clock, 36) * 3);
        String tag = "P" + (owner + 1);
        int w = GolfText.width(tag, 1) + 8, top = y - 40 - bob;
        c.panel(x - w / 2, top, w, 11, colour, 0.95f, GolfOverlay.WHITE);
        c.text(tag, x - w / 2 + 4, top + 2, 1, GolfOverlay.WHITE);
        for (int row = 0; row < 3; row++) c.rect(x - 2 + row, top + 11 + row, 5 - row * 2, 1, colour);
    }

    /** The shot's path, newest bright and oldest faint; the samples nearest the ball stay hidden under it. */
    public static void trail(GolfCanvas c, List<Integer> points, int cameraX, int cameraY) {
        int count = points.size() / 2;
        for (int i = 0; i < count - 3; i++) {
            float age = (i + 1) / (float) count;
            int x = points.get(i * 2) - cameraX, y = points.get(i * 2 + 1) - cameraY;
            // A dark rim keeps the dotted path legible over sky, grass and checkerboard alike.
            c.rect(x - 2, y - 2, 4, 4, GolfOverlay.NIGHT, 0.2f + 0.35f * age);
            c.rect(x - 1, y - 1, 2, 2, GolfText.CREAM, 0.3f + 0.65f * age);
        }
    }

    /**
     * Course progress inside the hole chip: start, the finish pennant and each golfer's lie.
     * xs holds course x positions (negative when unknown).
     */
    public static void progress(GolfCanvas c, int left, int width, int y, int startX, int finishX,
                                int[] xs, String[] names, int active, long clock) {
        if (finishX <= startX) return;
        int track = width - 6;
        c.rect(left, y, track, 1, GolfOverlay.MUTED, 0.6f);
        c.rect(left + track, y - 3, 1, 4, GolfText.CREAM);
        c.rect(left + track + 1, y - 3, 3, 2, GolfText.GOLD);
        for (int i = 0; i < xs.length; i++) {
            if (xs[i] < 0) continue;
            int at = left + Math.round(Math.clamp((xs[i] - startX) / (float) (finishX - startX), 0f, 1f) * track);
            boolean pulse = i == active && GolfMotion.blink(clock, 24);
            c.rect(at - 1, y - 1, 3, 3, pulse ? GolfOverlay.WHITE : GolfOverlay.characterColour(names[i]));
        }
    }
}
