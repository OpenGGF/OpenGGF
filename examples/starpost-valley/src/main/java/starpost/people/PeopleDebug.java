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

    public static boolean apply(Shell shell, String[] p) {
        Game game = shell.game;
        People people = game == null ? null : game.section(People.class);
        if (people == null || p.length < 2) {
            return false;
        }
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
                if (sys == null || event == null) {
                    return false;
                }
                sys.startEvent(event);
            }
            case "social" -> shell.push(new SocialPage(null));
            case "hand" -> {
                // Item ids contain underscores, which the capture tool's commands split on.
                String id = String.join("_", java.util.Arrays.copyOfRange(p, 2, p.length));
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
}
