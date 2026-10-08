package sitarhero.stage;

import com.openggf.mods.scene.SceneArt;
import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneRomArt;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import sitarhero.model.Role;
import sitarhero.model.Roster;
import sitarhero.ui.SitarUi;

/**
 * The main menu's backdrop: the band rehearsing on a real act from one of the player's ROMs.
 *
 * <p>The venue rotates through the installed acts that have pictures, one per visit to the
 * title. The band is cast from the installed roster, one performer per instrument with the
 * player's chosen performer on lead sitar, so Sonic 1 alone gives a duo and Sonic 3 &amp;
 * Knuckles a quartet. Members tune up with sparse, unsynchronised licks: rehearsal, not a
 * performance, because nothing here is listening to a song. These performers are separate
 * instances from the ones a song animates, so rehearsal gestures can never leak into play.
 */
public final class TitleStage {
    /** Extra terrain cut around the stage so the camera can sway. */
    private static final int SLACK = 32;
    /** Ticks for one slow camera sway across the stage and back. */
    private static final int SWAY_PERIOD = 1200;
    private static final int RISER = 8;

    /** An installed act a show can be staged on. */
    public record Venue(String game, int zone, int act, String name) { }

    private record Member(Roster who, PerformerArt art, Role role, int anchor, int period, int phase) { }

    private final ConcertStage stage;
    private final Venue venue;
    private final List<Member> band;

    private TitleStage(ConcertStage stage, Venue venue, List<Member> band) {
        this.stage = stage; this.venue = venue; this.band = band;
    }

    /** Acts that can host the title, interleaving games so successive visits vary. */
    public static List<Venue> venues(SceneArt art, List<String> games) {
        List<Venue> all = List.of(new Venue("s3k", 0, 0, "Angel Island"), new Venue("s1", 0, 0, "Green Hill"),
                new Venue("s2", 1, 0, "Chemical Plant"), new Venue("s3k", 6, 0, "Launch Base"),
                new Venue("s3k", 0, 1, "Angel Island Act 2"));
        List<Venue> available = new ArrayList<>();
        for (Venue venue : all) {
            if (!games.contains(venue.game())) continue;
            SceneRomArt rom = art.rom(venue.game());
            if (rom != null && rom.hasZonePictures(venue.zone(), venue.act())) available.add(venue);
        }
        return List.copyOf(available);
    }

    /**
     * Stages the band for one visit to the title. {@code visit} picks the venue; {@code lead}
     * plays sitar. Returns a deck-only stage when no installed act has pictures. Decoding a
     * venue and a band takes tens of milliseconds, so the caller keeps both caches (on its
     * scene instance, never in static fields) and returning to the title reuses them.
     */
    public static TitleStage build(SceneArt art, List<String> games, Roster lead, int visit, int width, int height,
                                   Map<String, ConcertStage> stages, Map<String, PerformerArt> performers) {
        List<Venue> venues = venues(art, games);
        Venue venue = venues.isEmpty() ? null : venues.get(Math.floorMod(visit, venues.size()));
        ConcertStage stage = venue == null ? ConcertStage.build(null, 0, 0, width, height, 0)
                : stages.computeIfAbsent(venue.game() + ":" + venue.zone() + ":" + venue.act(), key ->
                ConcertStage.build(art.rom(venue.game()), venue.zone(), venue.act(), width, height, SLACK));
        List<Roster> cast = cast(games, lead);
        List<Member> band = new ArrayList<>();
        int left = width - 196, right = width - 8, spacing = (right - left) / Math.max(1, cast.size());
        int index = 0;
        for (Role role : List.of(Role.SYNTH, Role.BONGOS, Role.SITAR, Role.HARP)) {
            Roster who = cast.get(role.ordinal());
            if (who == null) continue;
            PerformerArt performer = performers.computeIfAbsent(who.id(),
                    id -> new PerformerArt(art.rom(who.game(games)), id));
            band.add(new Member(who, performer, role, left + spacing * index + spacing / 2,
                    157 + index * 41, index * 53));
            index++;
        }
        return new TitleStage(stage, venue, List.copyOf(band));
    }

    /** One performer per role, indexed by {@link Role#ordinal()}; null where nobody is installed. */
    static List<Roster> cast(List<String> games, Roster lead) {
        Roster[] chosen = new Roster[Role.values().length];
        List<Roster> taken = new ArrayList<>();
        chosen[Role.SITAR.ordinal()] = lead; taken.add(lead);
        Roster[][] preferences = {
                {Roster.KNUCKLES, Roster.SILVER_SONIC, Roster.EGG_ROBO, Roster.ROBOTNIK, Roster.SONIC},
                {Roster.TAILS, Roster.MECHA_SONIC, Roster.ROBOTNIK, Roster.SONIC},
                {Roster.ROBOTNIK, Roster.EGG_ROBO, Roster.MECHA_SONIC, Roster.TAILS, Roster.SILVER_SONIC}};
        Role[] roles = {Role.BONGOS, Role.SYNTH, Role.HARP};
        for (int i = 0; i < roles.length; i++) {
            for (Roster candidate : preferences[i]) {
                if (!candidate.available(games) || taken.contains(candidate)) continue;
                chosen[roles[i].ordinal()] = candidate; taken.add(candidate); break;
            }
        }
        return java.util.Arrays.asList(chosen);
    }

    /** The venue itself, so the menus behind the title can keep showing it. */
    public ConcertStage stage() { return stage; }

    /** The act on stage tonight, or null for the fallback deck. */
    public Venue venue() { return venue; }

    /** The band's performers in stage order, for tools and tests. */
    public List<Roster> lineUp() { return band.stream().map(Member::who).toList(); }

    /** Rehearsal: each member plays a short lick on its own slow cycle. */
    public void update(long ticks) {
        for (Member member : band) {
            long t = Math.floorMod(ticks + member.phase(), (long) member.period());
            int notes = member.role() == Role.BONGOS ? 4 : 3;
            if (t % 9 != 0 || t / 9 >= notes) continue;
            int note = (int) (t / 9);
            int lanes;
            if (member.role() == Role.BONGOS) lanes = note == 3 ? 16 : note % 2 == 0 ? 1 : 2;
            else lanes = 1 << (int) Math.floorMod(ticks / member.period() + note * 2, 5L);
            member.art().notePlayed(member.role(), lanes, ticks);
        }
    }

    /** Current camera sway, in pixels of terrain. */
    public int sway(long ticks) {
        int half = stage.slack() / 2;
        return half + (int) Math.round(half * Math.sin(2 * Math.PI * ticks / SWAY_PERIOD));
    }

    public void draw(SceneCanvas c, long ticks, boolean reducedFlashes) {
        stage.draw(c, ticks, sway(ticks));
        drawCast(c, ticks, reducedFlashes);
    }

    /** Everything in front of the venue: haze, lights, the band and the venue caption. */
    public void drawCast(SceneCanvas c, long ticks, boolean reducedFlashes) {
        int sway = sway(ticks), shift = sway - stage.slack() / 2;
        // Haze at the top keeps the logo legible over bright skies.
        for (int y = 0; y < 48; y += 4) c.fill(0, y, c.width(), 4, (0x70 - y * 2) << 24 | 0x06102A);
        Member lead = band.stream().filter(m -> m.role() == Role.SITAR).findFirst().orElse(null);
        if (lead != null) {
            int x = lead.anchor() - shift;
            int beam = reducedFlashes ? 0 : (int) Math.round(10 * Math.sin(2 * Math.PI * ticks / 420.0));
            light(c, x - 70, x + beam, stage.floorRow(x, sway));
            light(c, x + 80, x - beam / 2, stage.floorRow(x, sway));
        }
        for (Member member : band) {
            int x = member.anchor() - shift, floor = stage.floorRow(x, sway);
            if (member.role() == Role.BONGOS) {
                riser(c, x, floor);
                floor -= RISER;
            }
            int y = floor - member.art().footOffset() + (member.art().hovers() ? -6 : 1);
            member.art().draw(c, x, y, ticks, member.role(), true);
        }
        if (venue != null) {
            String caption = "LIVE AT " + venue.name().toUpperCase(java.util.Locale.ROOT);
            SitarUi.text(c, caption, c.width() - 12 - SitarUi.width(caption, 1), 188, SitarUi.GOLD);
        }
    }

    /** A soft stage-light cone from above the frame onto the floor at {@code floorX}. */
    private static void light(SceneCanvas c, int sourceX, int floorX, int floorY) {
        for (int y = 0; y < floorY; y += 2) {
            double t = y / (double) floorY;
            int centre = (int) Math.round(sourceX + (floorX - sourceX) * t);
            int half = 3 + (int) Math.round(24 * t);
            c.fill(centre - half, y, half * 2, 2, 0x12FFF2C0);
        }
        c.fill(floorX - 30, floorY - 2, 60, 3, 0x22FFF2C0);
        c.fill(floorX - 20, floorY - 3, 40, 1, 0x22FFF2C0);
    }

    /** Original wooden drum riser prop. */
    private static void riser(SceneCanvas c, int x, int floor) {
        c.fill(x - 30, floor - RISER, 60, RISER, 0xFF5A3420);
        c.fill(x - 30, floor - RISER, 60, 2, 0xFFC98B4A);
        c.fill(x - 30, floor - 1, 60, 1, 0xFF2A170F);
        for (int i = 0; i < 3; i++) c.fill(x - 26 + i * 26, floor - RISER + 3, 1, RISER - 4, 0xFF3A2116);
    }
}
