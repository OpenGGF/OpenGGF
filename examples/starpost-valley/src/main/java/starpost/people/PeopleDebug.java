package starpost.people;

import starpost.core.Game;
import starpost.scene.PlayScreen;
import starpost.scene.Shell;

/**
 * Debug commands for the capture tool, after {@code people} ({@code jump=people_hearts_tails_4}):
 * <ul>
 *   <li>{@code hearts ID|all N}, {@code partner ID}, {@code translator on|off}, {@code hand ITEM}
 *       (one in the monitors, selected);</li>
 *   <li>{@code talk ID} opens a conversation, {@code event ID} starts a heart event,
 *       {@code social} opens the social page, {@code mail ID} posts a letter (delivered on the
 *       farm; {@code mail clear} empties the box);</li>
 *   <li>{@code hold ID X} stands a villager at valley x (until {@code release ID}),
 *       {@code pose ID NAME} poses a held villager, {@code snap} puts everyone where their
 *       schedule says.</li>
 * </ul>
 */
public final class PeopleDebug {
    private PeopleDebug() {
    }

    public static boolean apply(Shell shell, String[] args) {
        Game game = shell.game;
        People people = game == null ? null : game.section(People.class);
        if (people == null || args.length < 2) {
            return false;
        }
        String[] p = merge(args, people, game);
        PlayScreen play = shell.screen() instanceof PlayScreen s ? s : null;
        PeopleSystem sys = play == null ? null : PeopleSystem.of(play);
        switch (p[1]) {
            case "hearts" -> {
                int n = Integer.parseInt(p[3]);
                for (VillagerDef v : people.cast.all()) {
                    if (p[2].equals("all") || p[2].equals(v.id)) {
                        people.setHearts(v.id, n);
                    }
                }
            }
            case "partner" -> {
                people.setHearts(p[2], 10);
                people.bond(p[2]).partner = people.cast.get(p[2]).canPartner();
            }
            case "translator" -> {
                if (p[2].equals("on")) {
                    game.flags.add(People.TRANSLATOR);
                } else {
                    game.flags.remove(People.TRANSLATOR);
                }
            }
            case "talk" -> {
                if (sys == null || !sys.actors.containsKey(p[2])) {
                    return false;
                }
                VillagerActor actor = sys.actors.get(p[2]);
                actor.visible = true;
                sys.talk(actor);
            }
            case "event" -> {
                HeartEvent event = people.cast.event(p[2]);
                if (sys == null || event == null || shell.hasOverlay()) {
                    return false;
                }
                sys.startEvent(event);
            }
            case "social" -> shell.push(new SocialPage(null));
            case "hand" -> {
                String id = p[2];
                game.inventory.add(game.item(id), 1);
                for (int i = 0; i < starpost.core.Inventory.HOTBAR; i++) {
                    if (id.equals(game.inventory.id(i))) {
                        game.inventory.select(i);
                    }
                }
            }
            case "mail" -> {
                if (p[2].equals("clear")) {
                    people.morning(game);
                    while (people.nextLetter() != null) {
                        people.read(people.nextLetter());
                    }
                } else {
                    people.post(p[2]);
                }
            }
            case "hold" -> {
                if (sys == null || !sys.actors.containsKey(p[2])) {
                    return false;
                }
                VillagerActor actor = sys.actors.get(p[2]);
                actor.scripted = true;
                actor.view = play.onFarm() ? PeopleSystem.FARM : PeopleSystem.VALLEY;
                actor.x = Float.parseFloat(p[3]);
                actor.depth = p.length > 4 ? Float.parseFloat(p[4]) : 30;
                actor.feet = actor.groundAt(actor.x);
                actor.visible = true;
                actor.facingLeft = p.length > 5 && p[5].equals("left");
            }
            case "release" -> {
                if (sys == null || !sys.actors.containsKey(p[2])) {
                    return false;
                }
                sys.actors.get(p[2]).scripted = false;
            }
            case "pose" -> {
                if (sys == null || !sys.actors.containsKey(p[2])) {
                    return false;
                }
                sys.actors.get(p[2]).pose = Bodies.pose(p[3]);
            }
            case "snap" -> {
                if (sys == null) {
                    return false;
                }
                for (VillagerActor actor : sys.actors.values()) {
                    actor.scripted = false;
                    actor.snap();
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    /**
     * Rejoins ids the command line split apart ({@code elder_totem}, {@code robotnik_4},
     * {@code ring_radish}): the capture tool separates arguments with underscores. Only ids of
     * the kind the command takes are joined, so {@code hearts robotnik 4} stays three words.
     */
    private static String[] merge(String[] p, People people, Game game) {
        java.util.List<String> out = new java.util.ArrayList<>(java.util.List.of(p[0], p[1]));
        int i = 2;
        while (i < p.length) {
            int end = i;
            String id = p[i];
            StringBuilder joined = new StringBuilder(p[i]);
            for (int j = i + 1; j < p.length; j++) {
                joined.append('_').append(p[j]);
                if (known(p[1], joined.toString(), people, game)) {
                    end = j;
                    id = joined.toString();
                }
            }
            out.add(id);
            i = end + 1;
        }
        return out.toArray(new String[0]);
    }

    private static boolean known(String command, String id, People people, Game game) {
        return switch (command) {
            case "event" -> people.cast.event(id) != null;
            case "hand" -> game.catalog.hasItem(id);
            case "mail" -> people.cast.letter(id) != null || people.knownLetter(id);
            default -> people.cast.get(id) != null;
        };
    }
}
