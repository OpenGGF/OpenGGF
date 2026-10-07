package sitarhero.model;

import java.util.List;

/** Explicit route metadata for all 79 substantive songs; collection instances belong to callers. */
public final class CareerTours {
    private CareerTours() { }

    public static List<CareerTour> all() {
        return List.of(
                new CareerTour("s1", "s1", "Sonic 1 World Tour", "A journey across South Island", List.of(
                        world("s1-green-hill", "Green Hill", List.of("green-hill"), "s1-special-stage"),
                        world("s1-marble", "Marble", List.of("marble")),
                        world("s1-spring-yard", "Spring Yard", List.of("spring-yard"), "s1-boss"),
                        world("s1-labyrinth", "Labyrinth", List.of("labyrinth")),
                        world("s1-star-light", "Star Light", List.of("star-light")),
                        world("s1-scrap-brain", "Scrap Brain", List.of("scrap-brain")),
                        world("s1-finale", "Final Zone", List.of("final-zone")),
                        world("s1-encore", "South Island Encore", List.of("s1-ending", "s1-credits")))),
                new CareerTour("s2", "s2", "Sonic 2 World Tour", "A journey across West Side Island", List.of(
                        world("s2-emerald-hill", "Emerald Hill", List.of("emerald-hill"), "emerald-hill-2p", "options-s2", "two-player-menu-s2"),
                        world("s2-chemical-plant", "Chemical Plant", List.of("chemical-plant")),
                        world("s2-aquatic-ruin", "Aquatic Ruin", List.of("aquatic-ruin"), "special-stage-s2"),
                        world("s2-casino-night", "Casino Night", List.of("casino-night"), "casino-night-2p"),
                        world("s2-hill-top", "Hill Top", List.of("hill-top")),
                        world("s2-mystic-cave", "Mystic Cave", List.of("mystic-cave"), "mystic-cave-2p", "hidden-palace-s2"),
                        world("s2-oil-ocean", "Oil Ocean", List.of("oil-ocean")),
                        world("s2-metropolis", "Metropolis", List.of("metropolis"), "boss-s2"),
                        world("s2-sky-chase", "Sky Chase", List.of("sky-chase")),
                        world("s2-wing-fortress", "Wing Fortress", List.of("wing-fortress")),
                        world("s2-death-egg", "Death Egg", List.of("death-egg")),
                        world("s2-finale", "Final Battle", List.of("final-battle-s2")),
                        world("s2-encore", "West Side Island Encore", List.of("ending-s2", "credits-s2")))),
                new CareerTour("s3k", "s3k", "Sonic 3 & Knuckles World Tour", "Angel Island to the final broadcast", List.of(
                        world("s3k-angel-island", "Angel Island", List.of("angel-island-1", "angel-island-2"),
                                "s3-data-select", "s3-miniboss", "s3k-special-stage"),
                        world("s3k-hydrocity", "Hydrocity", List.of("hydrocity-1", "hydrocity-2"), "azure-lake"),
                        world("s3k-marble-garden", "Marble Garden", List.of("marble-garden-1", "marble-garden-2"), "s3k-gumball"),
                        world("s3k-carnival-night", "Carnival Night", List.of("carnival-night-1", "carnival-night-2"),
                                "balloon-park", "s3k-competition-menu"),
                        world("s3k-icecap", "IceCap", List.of("icecap-1", "icecap-2")),
                        world("s3k-launch-base", "Launch Base", List.of("launch-base-1", "launch-base-2"),
                                "chrome-gadget", "s3-ending", "s3-credits"),
                        world("s3k-mushroom-hill", "Mushroom Hill", List.of("mushroom-hill-1", "mushroom-hill-2"),
                                "s3k-data-select", "s3k-miniboss"),
                        // Native journey order differs from the music header's earlier Flying Battery slots.
                        world("s3k-flying-battery", "Flying Battery", List.of("flying-battery-1", "flying-battery-2"), "flying-battery-1-s3"),
                        world("s3k-sandopolis", "Sandopolis", List.of("sandopolis-1", "sandopolis-2"), "desert-palace", "s3k-slots"),
                        world("s3k-lava-reef", "Lava Reef / Hidden Palace", List.of("lava-reef-1", "lava-reef-2"), "s3k-boss"),
                        world("s3k-sky-sanctuary", "Sky Sanctuary", List.of("sky-sanctuary"), "sky-sanctuary-s3", "s3k-pachinko", "endless-mine"),
                        world("s3k-death-egg", "Death Egg", List.of("death-egg-1", "death-egg-2")),
                        world("s3k-finale", "Final Battle / Doomsday", List.of("s3k-final-boss", "doomsday")),
                        world("s3k-encore", "Angel Island Encore", List.of("s3k-ending", "s3k-credits")))));
    }

    /** Keep whole routes only when every required song has the matching installed-ROM donor. */
    public static List<CareerTour> available(List<SongSpec> availableSongs) {
        java.util.Objects.requireNonNull(availableSongs, "availableSongs");
        return all().stream().filter(tour -> tour.worlds().stream().flatMap(world -> world.requiredSongIds().stream())
                .allMatch(id -> availableSongs.stream().anyMatch(song -> song.id().equals(id) && song.game().equals(tour.game())))).toList();
    }

    private static CareerWorld world(String id, String title, List<String> required, String... side) {
        return new CareerWorld(id, title, required, List.of(side));
    }
}
