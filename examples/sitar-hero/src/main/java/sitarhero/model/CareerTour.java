package sitarhero.model;

import java.util.HashSet;
import java.util.List;

/** One self-contained installed-ROM journey in native world order. */
public record CareerTour(String id, String game, String title, String subtitle, List<CareerWorld> worlds) {
    public CareerTour {
        CareerWorld.validateId(id); CareerWorld.validateText(title, 80); CareerWorld.validateText(subtitle, 150);
        if (game == null || !List.of("s1", "s2", "s3k").contains(game)) throw new IllegalArgumentException("Invalid tour game");
        worlds = List.copyOf(worlds);
        if (worlds.isEmpty() || worlds.size() > 35) throw new IllegalArgumentException("Invalid tour size");
        var worldIds = new HashSet<String>(); var songIds = new HashSet<String>();
        for (CareerWorld world : worlds) {
            if (!world.id().startsWith(game + "-") || !worldIds.add(world.id()))
                throw new IllegalArgumentException("Foreign or duplicate tour world");
            for (List<String> songs : List.of(world.requiredSongIds(), world.sideSongIds())) for (String song : songs)
                if (!songIds.add(song)) throw new IllegalArgumentException("Duplicate tour song");
        }
    }
}
