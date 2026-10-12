package survivors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Immutable definitions prepared once per owner graph, separate from captured simulation. */
final class Definitions {
    record Lineup(List<Integer> ground, List<Integer> air, List<Integer> bossFrames, double averageHp) {
        Lineup {
            ground = List.copyOf(ground);
            air = List.copyOf(air);
            bossFrames = List.copyOf(bossFrames);
        }
    }
    private final List<Species.Traits> species;
    private final List<Lineup> stages;

    Definitions() {
        var traits = new ArrayList<Species.Traits>(Species.COUNT);
        for (int id = 0; id < Species.COUNT; id++) traits.add(Species.of(id));
        species = List.copyOf(traits);
        var lineups = new ArrayList<Lineup>(Stages.COUNT);
        for (int stage = 0; stage < Stages.COUNT; stage++) {
            var ground = integers(Stages.ground(stage));
            var air = integers(Stages.air(stage));
            int hp = 0;
            for (int id : ground) hp += species(id).hp();
            for (int id : air) hp += species(id).hp();
            int count = ground.size() + air.size();
            lineups.add(new Lineup(ground, air, integers(Stages.bossFrames(stage)), count == 0 ? 4 : hp / (double) count));
        }
        stages = List.copyOf(lineups);
    }

    private static List<Integer> integers(int[] values) { return Arrays.stream(values).boxed().toList(); }
    Species.Traits species(int id) { return species.get(Math.floorMod(id, Species.COUNT)); }
    Lineup stage(int stage) { return stages.get(stage >= 0 && stage < Stages.COUNT ? stage : Stages.DEZ); }
}
