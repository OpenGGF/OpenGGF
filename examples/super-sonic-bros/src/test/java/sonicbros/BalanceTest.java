package sonicbros;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import sonicbros.content.Roster;
import sonicbros.sim.Arena;
import sonicbros.sim.Fighter;
import sonicbros.sim.Match;
import sonicbros.sim.Pad;
import sonicbros.sim.Rules;

/**
 * A small version of the balance probe: level-9 CPUs, every pairing, both sides, fixed seeds.
 * Every fighter must win between a quarter and three quarters of its matches, no match may run
 * past eight minutes, and self-destructs must stay rare. (The tuning target was 40-60% over a
 * larger sample; this guards against a change breaking a fighter outright.)
 */
class BalanceTest {
    @Test void noFighterDominatesOrCollapses() {
        Roster r = new Roster();
        int k = r.all().size();
        int[] wins = new int[k];
        int[] games = new int[k];
        int falls = 0;
        int sds = 0;
        for (int i = 0; i < k; i++) {
            for (int j = i + 1; j < k; j++) {
                for (int g = 0; g < 6; g++) {
                    Rules rules = new Rules();
                    rules.itemFrequency = 0;
                    rules.stocks = 2;
                    int a = g % 2 == 0 ? i : j;
                    int b = a == i ? j : i;
                    Match m = new Match(rules, Arena.flat(288, true), List.of(new Match.Entrant(r.get(a), 0, 0, 9),
                            new Match.Entrant(r.get(b), 0, 1, 9)), 1000L * i + 37L * j + g * 7919L);
                    Pad[] pads = {Pad.none(), Pad.none()};
                    int f = 0;
                    while (!m.over && f < 60 * 60 * 8) { m.step(pads); f++; }
                    assertTrue(m.over, r.get(a).id + " vs " + r.get(b).id + " finished");
                    for (Fighter fi : m.fighters) { falls += fi.falls; sds += fi.selfDestructs; }
                    int w = r.indexOf(m.winners().get(0).def.id);
                    wins[w]++;
                    games[i]++;
                    games[j]++;
                }
            }
        }
        for (int i = 0; i < k; i++) {
            double rate = wins[i] / (double) games[i];
            System.out.printf("%-10s %.0f%%%n", r.get(i).id, rate * 100);
            assertTrue(rate > 0.25 && rate < 0.75, r.get(i).id + " win rate " + rate);
        }
        assertTrue(sds < falls * 0.1, "self-destructs " + sds + " of " + falls + " falls");
    }
}
