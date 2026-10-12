package slaytherobotnik.scene;

import static org.junit.jupiter.api.Assertions.*;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.mods.scene.*;
import java.lang.reflect.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import slaytherobotnik.art.Art;
import slaytherobotnik.content.Content;
import slaytherobotnik.core.*;
import slaytherobotnik.run.*;
import slaytherobotnik.ui.*;

class CombatLayoutTest {
    @Test
    void targetingAndDamageAreIndependentOfDrawCadence() throws Exception {
        for (boolean potion : List.of(false,true)) {
            Outcome expected = play(1, 1, potion);
            assertEquals(expected, play(0, 0, potion), "no draws");
            assertEquals(expected, play(5, 1, potion), "sparse draws");
            assertEquals(expected, play(1, 2, potion), "two draws per update");
        }
    }

    private record Outcome(List<Integer> hp, int energy, String phase, boolean pendingChoice,
                           List<String> hand, Properties save) { }

    private static Outcome play(int drawEvery, int draws, boolean potion) throws Exception {
        SceneArt art = proxy(SceneArt.class, (p, m, a) -> null);
        SceneAudio audio = proxy(SceneAudio.class, (p, m, a) -> null);
        SceneStorage storage = proxy(SceneStorage.class, (p, m, a) -> switch (m.getName()) {
            case "read", "readBytes" -> Optional.empty();
            case "write", "writeBytes", "delete" -> true;
            case "list" -> List.of();
            default -> throw new AssertionError(m.getName());
        });
        SceneContext ctx = proxy(SceneContext.class, (p, m, a) -> switch (m.getName()) {
            case "art" -> art;
            case "audio" -> audio;
            case "storage" -> storage;
            case "input" -> LogicalInputSnapshot.neutral();
            case "width" -> 400;
            case "height" -> 224;
            default -> throw new AssertionError(m.getName());
        });
        SceneCanvas canvas = proxy(SceneCanvas.class, (p, m, a) -> switch (m.getName()) {
            case "width" -> 400;
            case "height" -> 224;
            default -> null;
        });
        SmallFont font = new SmallFont(CombatLayoutTest.class.getResourceAsStream("/art/font.txt").readAllBytes());
        Shell shell = new Shell(ctx, font, new Art(ctx, new byte[0], new byte[0], new byte[0]), Content.build());
        shell.attach(Run.start(shell.catalog, "sonic", 7));
        EncounterDef encounter = shell.catalog.encounters(1, EncounterDef.STRONG).stream()
                .filter(e -> e.spawner().spawn(new Rng(1)).size() > 1).findFirst().orElseThrow();
        shell.run.enterFight(encounter.id());
        CombatRoom room = (CombatRoom) shell.run.room();
        CombatView view = new CombatView(shell, room);
        RunScreen screen = new RunScreen();
        screen.enter(shell);
        for (int tick = 0; tick < 300; tick++) {
            shell.ticks++;
            view.update(shell, screen);
            if (drawEvery > 0 && tick % drawEvery == 0) {
                for (int i = 0; i < draws; i++) view.draw(shell, screen, canvas);
            }
        }
        Combat combat = room.combat();
        Card attack = combat.player().hand().stream().filter(c -> CardTarget.needsChoice(c.target())
                && combat.whyUnplayable(c) == null).findFirst().orElseThrow();
        if (potion) {
            assertTrue(shell.run.state().addPotion(shell.catalog.potion("potion:robotnik_monitor")));
            field("targetingPotion").set(view, 0);
        } else field("selected").set(view, attack);
        shell.in.accept = true;
        view.update(shell, screen);
        if (potion) assertNull(shell.run.state().potionAt(0), "target selection uses the monitor");
        else assertFalse(combat.player().hand().contains(attack), "target selection plays the attack");
        shell.in.accept = false;
        Properties save = new Properties();
        save.load(new java.io.StringReader(SaveCodec.encode(shell.run)));
        return new Outcome(combat.enemies().stream().map(Enemy::hp).toList(), combat.player().energy(),
                combat.phase(), combat.hasPendingChoice(), combat.player().hand().stream().map(Card::id).toList(), save);
    }

    private static Field field(String name) throws Exception {
        Field field = CombatView.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }
}
