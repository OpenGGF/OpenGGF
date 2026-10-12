package starpost.people;

import com.openggf.mods.scene.SceneCanvas;
import starpost.core.Game;
import starpost.core.Item;
import starpost.scene.Screen;
import starpost.scene.Sfx;
import starpost.scene.Shell;
import starpost.ui.Text;

/**
 * Talking to a neighbour (an overlay; the clock stops). Holding something giftable asks first
 * whether to give it; otherwise (or on "no") they say the day's line.
 */
final class DialogueScreen implements Screen {
    private final PeopleSystem sys;
    private final VillagerActor actor;
    private final Item held;
    private boolean asking;
    private boolean yes = true;
    private Speech speech;
    private long opened;

    DialogueScreen(PeopleSystem sys, VillagerActor actor, Item held) {
        this.sys = sys;
        this.actor = actor;
        this.held = held;
    }

    @Override
    public boolean overlay() {
        return true;
    }

    @Override
    public void enter(Shell shell) {
        opened = shell.ticks;
        Game game = shell.game;
        // An errand finished (a Signpost Board request delivered) comes before gifts and talk.
        Line errand = sys.people.errand(actor.def.id, game);
        if (errand != null) {
            speech = Speech.of(sys, actor.def.id, errand);
            actor.heartUp = true;
            actor.heartAt = shell.ticks + 1_000_000;
            shell.sfx(Sfx.PERFECT);
            return;
        }
        asking = held != null && sys.people.canGift(actor.def.id, held, game) == People.GiftStatus.GIVEN;
        if (!asking) {
            talk(shell);
        }
    }

    private void talk(Shell shell) {
        People.Talk talk = sys.people.talk(actor.def.id, shell.game);
        speech = Speech.of(sys, actor.def.id, talk.line());
        if (talk.points() > 0) {
            actor.heartAt = shell.ticks + 1_000_000;     // shown when the conversation closes
            actor.heartUp = true;
        }
    }

    private void give(Shell shell) {
        People.Gift gift = sys.people.gift(actor.def.id, held, shell.game);
        speech = Speech.of(sys, actor.def.id, gift.line());
        actor.heartUp = gift.points() >= 0;
        actor.heartAt = shell.ticks + 1_000_000;
        shell.sfx(switch (gift.taste()) {
            case LOVE -> Sfx.PERFECT;
            case LIKE, NEUTRAL -> Sfx.RING;
            default -> Sfx.ERROR;
        });
    }

    @Override
    public void update(Shell shell) {
        if (asking) {
            if (shell.in.leftPressed || shell.in.rightPressed || shell.in.upPressed || shell.in.downPressed) {
                yes = !yes;
                shell.sfx(Sfx.SWITCH);
            } else if (shell.in.confirm || shell.in.act) {
                asking = false;
                if (yes) {
                    give(shell);
                } else {
                    talk(shell);
                }
                shell.in.consume();
            }
            return;
        }
        if (speech != null && speech.update(shell.in)) {
            if (actor.heartAt > shell.ticks) {
                actor.heartAt = shell.ticks;
            }
            shell.pop();
        }
    }

    @Override
    public void draw(Shell shell, SceneCanvas canvas) {
        if (asking) {
            String question = "GIVE THE " + held.name() + " TO " + actor.def.name + "?";
            int w = Math.max(220, canvas.textWidth(question) + 40), h = 58;
            int x = (canvas.width() - w) / 2, y = canvas.height() - h - 30;
            Text.panel(canvas, x, y, w, h);
            shell.art.icons.draw(canvas, held, x + 10, y + 8, com.openggf.mods.scene.SceneDraw.plain());
            Text.shadow(canvas, question, x + 30, y + 12, Text.WHITE);
            Text.shadow(canvas, yes ? "> YES" : "  YES", canvas.width() / 2 - 60, y + 34, yes ? Text.YELLOW : Text.GREY);
            Text.shadow(canvas, yes ? "  NO" : "> NO", canvas.width() / 2 + 20, y + 34, yes ? Text.GREY : Text.YELLOW);
            return;
        }
        if (speech != null) {
            speech.draw(sys, canvas, shell.ticks - opened);
        }
    }
}
