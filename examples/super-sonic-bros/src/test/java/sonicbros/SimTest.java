package sonicbros;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import sonicbros.content.Roster;
import sonicbros.sim.Arena;
import sonicbros.sim.Event;
import sonicbros.sim.Fighter;
import sonicbros.sim.FighterDef;
import sonicbros.sim.Knockback;
import sonicbros.sim.Match;
import sonicbros.sim.Pad;
import sonicbros.sim.Rules;

/** The fight simulation on its own, without a ROM: determinism, launches, KOs and the modes. */
class SimTest {
    private final Roster roster = new Roster();

    private Match cpuMatch(String a, String b, Rules rules, long seed) {
        return new Match(rules, Arena.flat(288, true), List.of(new Match.Entrant(roster.byId(a), 0, 0, 9),
                new Match.Entrant(roster.byId(b), 0, 1, 9)), seed);
    }

    private static int run(Match m, int limit) {
        Pad[] pads = new Pad[m.fighters.size()];
        java.util.Arrays.fill(pads, Pad.none());
        int f = 0;
        while (!m.over && f < limit) { m.step(pads); f++; }
        return f;
    }

    @Test void sameSeedSameMatch() {
        Rules rules = new Rules();
        rules.stocks = 1;
        Match a = cpuMatch("sonic", "knuckles", rules, 42);
        Match b = cpuMatch("sonic", "knuckles", rules, 42);
        int fa = run(a, 60 * 60 * 6);
        int fb = run(b, 60 * 60 * 6);
        assertEquals(fa, fb, "frames to decide");
        for (int i = 0; i < 2; i++) {
            assertEquals(a.fighters.get(i).damage, b.fighters.get(i).damage, 1e-9);
            assertEquals(a.fighters.get(i).placement, b.fighters.get(i).placement);
        }
    }

    @Test void knockbackGrowsWithDamageAndShrinksWithWeight() {
        double low = Knockback.of(12, 20, 100, 30, 100);
        double high = Knockback.of(12, 120, 100, 30, 100);
        double heavy = Knockback.of(12, 120, 130, 30, 100);
        assertTrue(high > low * 1.8, "damage makes launches bigger");
        assertTrue(heavy < high, "weight resists");
    }

    @Test void everyFighterHasEveryMove() {
        for (FighterDef d : roster.all()) {
            for (FighterDef.Slot s : FighterDef.Slot.values()) {
                assertTrue(d.move(s) != null, d.id + " has " + s);
            }
            assertTrue(d.forwardThrow != null && d.backThrow != null && d.upThrow != null && d.downThrow != null);
        }
    }

    @Test void humanCanWalkJumpAndAttack() {
        Rules rules = new Rules();
        rules.itemFrequency = 0;
        Match m = new Match(rules, Arena.flat(288, true), List.of(new Match.Entrant(roster.byId("sonic"), 0, 0, 0),
                new Match.Entrant(roster.byId("knuckles"), 0, 1, 0)), 1);
        Pad[] idle = {Pad.none(), Pad.none()};
        while (m.countdown > 0) m.step(idle);
        Fighter s = m.fighters.get(0);
        double x0 = s.x;
        Pad p = Pad.none();
        for (int i = 0; i < 30; i++) { p = p.then(Pad.RIGHT); m.step(new Pad[] {p, Pad.none()}); }
        assertTrue(s.x > x0 + 40, "ran right");
        p = p.then(Pad.JUMP);
        m.step(new Pad[] {p, Pad.none()});
        for (int i = 0; i < 10; i++) { p = p.then(Pad.JUMP); m.step(new Pad[] {p, Pad.none()}); }
        assertTrue(s.y < -30, "jumped");
        for (int i = 0; i < 80; i++) { p = p.then(0); m.step(new Pad[] {p, Pad.none()}); }
        assertTrue(s.grounded(), "landed");
        p = p.then(Pad.ATTACK);
        m.step(new Pad[] {p, Pad.none()});
        assertEquals(Fighter.State.ATTACK, s.state, "attacked");
    }

    @Test void stocksRunOutAndTheMatchEnds() {
        Rules rules = new Rules();
        rules.stocks = 2;
        rules.itemFrequency = 2;
        Match m = cpuMatch("tails", "robotnik", rules, 7);
        int kos = 0;
        Pad[] pads = {Pad.none(), Pad.none()};
        for (int f = 0; f < 60 * 60 * 8 && !m.over; f++) {
            m.step(pads);
            for (Event e : m.events) if (e.type() == Event.KO || e.type() == Event.STAR_KO) kos++;
        }
        assertTrue(m.over, "a two-stock match ends");
        assertTrue(kos >= 2, "at least two KOs");
        assertEquals(1, m.winners().size());
    }

    @Test void timeModeScoresKos() {
        Rules rules = new Rules();
        rules.mode = Rules.Mode.TIME;
        rules.minutes = 2;
        Match m = cpuMatch("sonic", "mecha", rules, 3);
        run(m, 60 * 60 * 5);
        assertTrue(m.over, "time runs out (or sudden death decides)");
        Fighter w = m.winners().get(0);
        assertEquals(1, w.placement);
    }

    @Test void staminaModeEndsAtZero() {
        Rules rules = new Rules();
        rules.mode = Rules.Mode.STAMINA;
        rules.stamina = 60;
        Match m = cpuMatch("knuckles", "eggrobo", rules, 5);
        run(m, 60 * 60 * 6);
        assertTrue(m.over);
    }

    @Test void fourFighterTeamBattle() {
        Rules rules = new Rules();
        rules.teams = true;
        rules.stocks = 1;
        Match m = new Match(rules, Arena.flat(288, true), List.of(
                new Match.Entrant(roster.byId("sonic"), 0, 0, 7), new Match.Entrant(roster.byId("tails"), 0, 0, 7),
                new Match.Entrant(roster.byId("robotnik"), 0, 1, 7), new Match.Entrant(roster.byId("eggrobo"), 0, 1, 7)), 11);
        run(m, 60 * 60 * 8);
        assertTrue(m.over);
        int team = m.winners().get(0).team;
        for (Fighter f : m.winners()) assertEquals(team, f.team, "a whole team wins together");
    }
}
