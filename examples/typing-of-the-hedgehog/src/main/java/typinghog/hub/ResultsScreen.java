package typinghog.hub;

import com.openggf.mods.scene.SceneBackdrop;
import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;
import java.util.List;
import typinghog.hub.model.Grades;
import typinghog.hub.model.Handoff;
import typinghog.hub.model.Profile;
import typinghog.hub.model.ResultMerge;
import typinghog.hub.model.StoryBook;
import typinghog.save.ActResult;
import typinghog.save.LaunchIntent;

/**
 * What the act came to, shown when gameplay hands back: score, words per minute, accuracy,
 * best chain and kills count up one line at a time with the ROM's tally clicks, then the rank
 * letter slams down. New records, a won emerald, a newly opened stop and Boss Rush stars
 * follow. A Boss Rush carries on to its next boss from here.
 *
 * <p>Enter returns to the mode's screen; AGAIN replays the act; NEXT starts the next story stop.
 */
final class ResultsScreen implements Screen {
    private static final int SLIDE = 16;
    private static final int LINE = 26;
    private static final int LINES = 5;

    private ResultMerge.Outcome outcome;
    private Handoff.Rush rushNext;
    private boolean rushDone;
    private boolean rushOver;
    private int rushPlace;
    private long rushScore;
    private long opened;
    private boolean skipped;
    private boolean rankPlayed;
    private boolean extrasPlayed;
    private SceneBackdrop backdrop;
    private String typed = "";

    @Override public String name() { return "RESULTS"; }

    /** Takes a merged result and works out where a Boss Rush goes next. */
    void show(HubScene hub, ResultMerge.Outcome merged, Handoff.Rush rush) {
        outcome = merged;
        rushNext = null;
        rushDone = false;
        rushOver = false;
        rushPlace = 0;
        ActResult r = merged.result();
        boolean ours = rush != null && r.mode() == LaunchIntent.Mode.BOSS_RUSH && rush.game().equals(r.game())
                && !rush.finished() && rush.nextZone() == r.zone() && rush.nextAct() == r.act();
        if (!ours) {
            hub.ctx.storage().delete(Handoff.RUSH);
            return;
        }
        Handoff.Rush after = r.cleared() ? rush.won(r.score()) : rush;
        rushScore = after.score() + (r.cleared() ? 0 : r.score());
        if (r.cleared() && !after.finished()) {
            rushNext = after;
            hub.ctx.storage().write(Handoff.RUSH, after.encode());
            return;
        }
        rushDone = r.cleared();
        rushOver = !r.cleared();
        hub.ctx.storage().delete(Handoff.RUSH);
        rushPlace = hub.profile.file(Profile.rushTable(r.game(), merged.difficulty()),
                new Profile.Entry(rushScore, r.grade(), r.wpm()));
        hub.save();
    }

    /** A made-up result for screenshots and tests ({@code sample}, {@code fail} or {@code emerald}). */
    void sample(HubScene hub, String kind) {
        StoryBook.Stop stop = hub.story.chapter(hub.game).isEmpty() ? null : hub.story.chapter(hub.game).getFirst();
        int zone = stop == null ? 0 : stop.zone(), act = stop == null ? 0 : stop.act();
        boolean fail = kind.equals("fail");
        ActResult r = new ActResult(LaunchIntent.Mode.STORY, hub.game, zone, act, !fail, fail ? 8150 : 48620,
                fail ? 31.4 : 58.7, fail ? 0.88 : 0.973, fail ? 6 : 23, fail ? 21 : 64, fail ? 4 : 1, 3, fail ? 0 : 27,
                fail ? "D" : "A", fail ? 0 : 21700, !fail, "", "");
        outcome = new ResultMerge.Outcome(r, hub.profile.difficulty(), "sample", fail ? 0 : 1, 41200, !fail,
                kind.equals("emerald") || kind.equals("sample"), 0);
        rushNext = null;
        rushDone = rushOver = false;
    }

    @Override public void open(HubScene hub) {
        opened = hub.ticks;
        skipped = false;
        rankPlayed = false;
        extrasPlayed = false;
        typed = "";
        ActResult r = outcome.result();
        backdrop = hub.art.zoneBackdrop(r.game(), r.zone(), r.act());
        hub.audio.theme(r.cleared() || r.mode() == LaunchIntent.Mode.ENDLESS ? HubAudio.Theme.CLEAR : HubAudio.Theme.GAME_OVER);
    }

    private int rankTick() { return SLIDE + LINES * LINE + 10; }

    @Override public void update(HubScene hub) {
        long age = hub.ticks - opened;
        if (!skipped && age > SLIDE && age < SLIDE + LINES * LINE && age % 3 == 0) hub.audio.cue(HubAudio.Cue.TALLY);
        if (!rankPlayed && (skipped || age >= rankTick())) {
            rankPlayed = true;
            hub.audio.cue(HubAudio.Cue.REGISTER);
        }
        if (!extrasPlayed && (skipped || age >= rankTick() + 30)) {
            extrasPlayed = true;
            if (outcome.newEmerald()) hub.audio.theme(HubAudio.Theme.EMERALD);
        }
        Controls in = hub.controls;
        for (char c : in.typed().toCharArray()) {
            if (Character.isLetter(c)) typed = (typed + Character.toLowerCase(c));
            if (typed.length() > 8) typed = typed.substring(typed.length() - 8);
        }
        if (in.backspace() && !typed.isEmpty()) typed = typed.substring(0, typed.length() - 1);
        if (typed.endsWith("again") || typed.endsWith("retry")) {
            typed = "";
            if (finished(age)) again(hub);
            return;
        }
        if (typed.endsWith("next")) {
            typed = "";
            if (finished(age)) next(hub);
            return;
        }
        if (in.accept() || in.back()) {
            if (!finished(age)) {
                skipped = true;
                return;
            }
            carryOn(hub);
        }
    }

    private boolean finished(long age) { return skipped || age >= rankTick() + 30; }

    private void again(HubScene hub) {
        ActResult r = outcome.result();
        if (r.mode() == LaunchIntent.Mode.BOSS_RUSH) {
            carryOn(hub);
            return;
        }
        hub.audio.cue(HubAudio.Cue.CONFIRM);
        hub.play(r.mode(), r.game(), r.zone(), r.act(), 1);
    }

    private void next(HubScene hub) {
        ActResult r = outcome.result();
        List<StoryBook.Stop> chapter = hub.story.chapter(r.game());
        for (int i = 0; i + 1 < chapter.size(); i++) {
            StoryBook.Stop s = chapter.get(i);
            if (s.zone() == r.zone() && s.act() == r.act()) {
                for (int j = i + 1; j < chapter.size(); j++) {
                    StoryBook.Stop n = chapter.get(j);
                    StoryBook.State st = StoryBook.state(chapter, j, hub.profile.cleared(r.game()), hub::railed);
                    if (st == StoryBook.State.OPEN || st == StoryBook.State.CLEARED) {
                        hub.play(LaunchIntent.Mode.STORY, n.game(), n.zone(), n.act(), 1);
                        return;
                    }
                }
            }
        }
        hub.refuse("NO OPEN STOP AFTER THIS ONE");
    }

    private void carryOn(HubScene hub) {
        hub.audio.cue(HubAudio.Cue.CONFIRM);
        if (rushNext != null) {
            hub.play(LaunchIntent.Mode.BOSS_RUSH, rushNext.game(), rushNext.nextZone(), rushNext.nextAct(),
                    hub.profile.stars(rushNext.game(), rushNext.nextZone(), rushNext.nextAct()) + 1);
            return;
        }
        ActResult r = outcome.result();
        switch (r.mode()) {
            case STORY -> {
                hub.storyMap.chapter(hub, r.game());
                hub.go(hub.storyMap);
            }
            case ARCADE -> hub.go(hub.arcade);
            case ENDLESS -> hub.go(hub.endless);
            case BOSS_RUSH -> hub.go(hub.rush);
        }
    }

    // ---- drawing ----------------------------------------------------------------------------

    @Override public void draw(HubScene hub, SceneCanvas c) {
        long t = hub.ticks;
        long age = skipped ? 10_000 : t - opened;
        int w = c.width();
        ActResult r = outcome.result();
        if (backdrop != null) {
            int top = Math.max(0, Math.min(backdrop.image().height() - c.height(), 96));
            c.drawBackdrop(backdrop, top, t * 0.3, t);
            c.fill(0, 0, w, c.height(), 0x98060C24);
        } else {
            hub.backdrop(c, 0.6f);
        }
        Hero.sonic(c, hub.art, hub.game, HubArt.ANIM_WAIT, t, 62, 190, false, 1f);
        float slide = Math.min(1, age / (float) SLIDE);
        int px = 116 + (int) ((1 - slide) * (w - 100));
        int pw = w - 124;
        Gfx.panel(c, px, 10, pw, 196);
        String head = switch (r.mode()) {
            case ENDLESS -> "WAVES SURVIVED";
            case BOSS_RUSH -> r.cleared() ? "BOSS DOWN!" : "THE BOSS GOT AWAY";
            default -> r.cleared() ? "SONIC TYPED THROUGH" : "GAME OVER";
        };
        hub.font.shadow(c, head, px + 10, 18, r.cleared() || r.mode() == LaunchIntent.Mode.ENDLESS ? Gfx.GOLD : Gfx.RED, 1);
        hub.font.outline(c, hub.stopTitle(r.game(), r.zone(), r.act()).toUpperCase(), px + 10, 30, Gfx.WHITE,
                0xFF0A1440, 2);
        hub.font.shadow(c, "ACT " + (r.act() + 1) + "   " + outcome.difficulty().name() + "   " + HubScene.gameName(r.game()),
                px + 10, 52, Gfx.DIM, 1);
        String[] labels = {"SCORE", "SPEED", "ACCURACY", "BEST CHAIN", "BADNIKS"};
        for (int i = 0; i < LINES; i++) {
            long start = SLIDE + (long) i * LINE;
            if (age < start) break;
            float k = Math.min(1, (age - start) / (float) (LINE - 6));
            int y = 68 + i * 16;
            hub.font.shadow(c, labels[i], px + 10, y, Gfx.TEXT, 1);
            String value = switch (i) {
                case 0 -> Long.toString(Math.round(r.score() * k));
                case 1 -> Math.round(r.wpm() * k) + " WPM";
                case 2 -> String.format(java.util.Locale.ROOT, "%.1f%%", r.accuracy() * 100 * k);
                case 3 -> Integer.toString(Math.round(r.bestChain() * k));
                default -> Math.round(r.kills() * k) + (r.rescues() > 0 ? "  +" + r.rescues() + " RESCUED" : "");
            };
            hub.font.right(c, value, px + pw - 70, y, i == 0 ? Gfx.GOLD : Gfx.WHITE, i == 0 ? 1 : 1);
        }
        if (age >= rankTick()) rank(hub, c, r.grade(), px + pw - 38, 96, age - rankTick());
        if (age >= rankTick() + 20) extras(hub, c, px + 10, 152, pw - 20, t);
        if (finished(age)) {
            String hint = rushNext != null ? "ENTER: NEXT BOSS" : r.mode() == LaunchIntent.Mode.STORY && r.cleared()
                    ? "ENTER: MAP   NEXT: NEXT STOP   AGAIN: REPLAY" : "ENTER: CONTINUE   AGAIN: REPLAY";
            if ((t / 20) % 3 != 0) hub.font.centred(c, hint, px + pw / 2, 194, Gfx.WHITE, 1);
        }
    }

    private void rank(HubScene hub, SceneCanvas c, String grade, int cx, int cy, long age) {
        float scale = age < 8 ? 6 + (8 - age) * 1.5f : 6;
        int s = Math.round(scale);
        int colour = Grades.colour(grade);
        hub.font.centred(c, "RANK", cx, cy - 34, Gfx.DIM, 1);
        int gw = hub.font.width(grade, s);
        int x = cx - gw / 2, y = cy - (HubFont.H * s) / 3;
        hub.font.draw(c, grade, x + s / 2 + 1, y + s / 2 + 1, 0xC0000000, s);
        for (int dy = -1; dy <= 1; dy++)
            for (int dx = -1; dx <= 1; dx++)
                if (dx != 0 || dy != 0) hub.font.draw(c, grade, x + dx * 2, y + dy * 2, 0xFF101028, s);
        hub.font.draw(c, grade, x, y, colour, s);
        if (age < 12) c.fill(0, 0, c.width(), c.height(), Gfx.alpha(0xFFFFFFFF, (12 - age) / 24f));
    }

    private void extras(HubScene hub, SceneCanvas c, int x, int y, int w, long t) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        java.util.List<Integer> colours = new java.util.ArrayList<>();
        if (outcome.place() == 1 && outcome.result().score() > outcome.previousBest()) {
            lines.add("NEW RECORD!");
            colours.add((t / 8) % 2 == 0 ? Gfx.GOLD : Gfx.WHITE);
        } else if (outcome.place() > 0) {
            lines.add("TOP 10: #" + outcome.place());
            colours.add(Gfx.GOLD);
        }
        if (outcome.newEmerald()) {
            lines.add("CHAOS EMERALD!");
            colours.add(0xFF60FF70);
        }
        if (outcome.firstClear()) {
            lines.add("NEXT STOP OPEN");
            colours.add(0xFF80D0FF);
        }
        if (outcome.result().mode() == LaunchIntent.Mode.BOSS_RUSH && outcome.stars() > 0) {
            lines.add("STAR LEVEL " + outcome.stars());
            colours.add(Gfx.GOLD);
        }
        if (rushNext != null) {
            lines.add("RUSH: " + rushNext.won() + " DOWN, " + rushNext.remaining().size() + " TO GO");
            colours.add(Gfx.WHITE);
        } else if (rushDone || rushOver) {
            lines.add((rushDone ? "RUSH COMPLETE! " : "RUSH OVER  ") + rushScore + (rushPlace > 0 ? "  #" + rushPlace : ""));
            colours.add(rushDone ? Gfx.GOLD : Gfx.RED);
        }
        int lx = x;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int lw = hub.font.width(line, 1) + 10;
            if (lx + lw > x + w) {
                lx = x;
                y += 15;
            }
            Gfx.panel(c, lx, y, lw, 13, 0xE0101830, colours.get(i));
            hub.font.draw(c, line, lx + 5, y + 2, colours.get(i), 1);
            if (line.startsWith("CHAOS")) {
                SceneImage gem = hub.art.image("emerald");
                if (gem != null) {
                    int index = Math.max(0, hub.profile.emeraldCount(outcome.result().game()) - 1);
                    float bob = (float) Math.sin(t * 0.15) * 2;
                    c.draw(gem, lx + lw + 3, y + 2 + bob, SceneDraw.plain().withTint(HubArt.emeraldColour(index)));
                    lw += 14;
                }
            }
            lx += lw + 4;
        }
    }
}
