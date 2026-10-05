package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import slaytherobotnik.core.Card;
import slaytherobotnik.run.DeckChoice;
import slaytherobotnik.ui.Colors;
import slaytherobotnik.ui.Gfx;
import slaytherobotnik.ui.Hotspots;

/**
 * Answers a {@link DeckChoice}: pick cards from a grid (or from a row of big cards when
 * there are only a few to choose from). Upgrades preview the upgraded card.
 */
final class DeckChoiceView {
    private final DeckChoice choice;
    private final List<Card> options;
    private final CardGrid grid;
    private final Hotspots spots = new Hotspots();
    private final Set<Card> selected = new LinkedHashSet<>();
    private final boolean bigRow;

    DeckChoiceView(DeckChoice choice) {
        this.choice = choice;
        this.options = new ArrayList<>(choice.options());
        this.bigRow = options.size() <= 4 && choice.mode().equals(DeckChoice.PICK);
        this.grid = new CardGrid(options, 44, 2, choice.mode().equals(DeckChoice.UPGRADE) ? 3 : 5);
    }

    DeckChoice choice() {
        return choice;
    }

    private void layout(Shell shell) {
        spots.clear();
        if (bigRow) {
            int gap = 12;
            int total = options.size() * CardRenderer.BIG_W + (options.size() - 1) * gap;
            int x = (shell.width() - total) / 2;
            for (int i = 0; i < options.size(); i++) {
                spots.add("card" + i, x + i * (CardRenderer.BIG_W + gap), 50, CardRenderer.BIG_W, CardRenderer.BIG_H);
            }
        } else {
            grid.layout(spots, shell);
        }
        if (choice.max() > 1) {
            spots.add("confirm", shell.width() - 100, shell.height() - 22, 86, 15,
                    selected.size() >= Math.min(choice.min(), options.size()));
        }
        if (choice.cancellable()) {
            spots.add("cancel", 14, shell.height() - 22, 70, 15);
        }
    }

    void update(Shell shell, RunScreen screen) {
        layout(shell);
        String before = spots.focused();
        String picked = spots.update(shell.in);
        if (!bigRow) {
            grid.scroll(shell, spots, before);
        }
        if ((shell.in.back || shell.in.mouse.rightPressed()) && choice.cancellable()) {
            shell.run.cancelDeckChoice();
            shell.sfx(Sounds.SFX_SWITCH);
            return;
        }
        if (picked == null) {
            return;
        }
        if (picked.equals("cancel")) {
            shell.run.cancelDeckChoice();
            shell.sfx(Sounds.SFX_SWITCH);
        } else if (picked.equals("confirm")) {
            finish(shell);
        } else {
            Card card = options.get(Integer.parseInt(picked.substring(4)));
            if (choice.max() <= 1) {
                selected.clear();
                selected.add(card);
                finish(shell);
            } else if (!selected.remove(card) && selected.size() < choice.max()) {
                selected.add(card);
                shell.sfx(Sounds.SFX_CURSOR);
            }
        }
    }

    private void finish(Shell shell) {
        String mode = choice.mode();
        if (shell.run.resolveDeckChoice(new ArrayList<>(selected))) {
            shell.sfx(switch (mode) {
                case DeckChoice.UPGRADE -> Sounds.SFX_STARPOST;
                case DeckChoice.REMOVE -> Sounds.SFX_BREAK;
                case DeckChoice.TRANSFORM -> Sounds.SFX_ENTER_SPECIAL;
                default -> Sounds.SFX_RING;
            });
        } else {
            shell.sfx(Sounds.SFX_ERROR);
        }
    }

    void draw(Shell shell, RunScreen screen, SceneCanvas c) {
        c.fill(0, 0, shell.width(), shell.height(), 0xD0000010);
        var f = shell.font;
        String prompt = choice.prompt().toUpperCase();
        f.drawOutlined(c, prompt, (shell.width() - f.width(prompt)) / 2, 34, Colors.GOLD, 1);
        layout(shell);
        if (bigRow) {
            for (int i = 0; i < options.size(); i++) {
                Hotspots.Spot s = spots.spot("card" + i);
                boolean focus = spots.isFocused(s.id());
                screen.cards.drawBig(c, options.get(i), null, null, s.x(), s.y() - (focus ? 3 : 0), false);
                if (focus) {
                    Gfx.focusFrame(c, s.x(), s.y() - 3, s.w(), s.h(), shell.ticks);
                }
            }
        } else {
            grid.draw(shell, screen.cards, c, spots, selected);
            Card focus = grid.focusedCard(spots);
            if (focus != null) {
                int px = shell.width() - CardRenderer.BIG_W - 14;
                if (choice.mode().equals(DeckChoice.UPGRADE) && focus.canUpgrade()) {
                    Card preview = focus.duplicate();
                    preview.upgrade();
                    screen.cards.drawBig(c, focus, null, null, px - CardRenderer.BIG_W - 8, 50, false);
                    f.drawOutlined(c, ">", px - 7, 108, Colors.TEXT_GOOD, 1);
                    screen.cards.drawBig(c, preview, null, null, px, 50, false);
                } else {
                    screen.cards.drawBig(c, focus, null, null, px, 50, false);
                }
            }
        }
        Hotspots.Spot confirm = spots.spot("confirm");
        if (confirm != null) {
            Gfx.button(c, f, "CONFIRM " + selected.size() + "/" + choice.max(), confirm.x(), confirm.y(), confirm.w(),
                    confirm.h(), spots.isFocused("confirm"), confirm.enabled(), shell.ticks);
        }
        Hotspots.Spot cancel = spots.spot("cancel");
        if (cancel != null) {
            Gfx.button(c, f, "SKIP", cancel.x(), cancel.y(), cancel.w(), cancel.h(), spots.isFocused("cancel"), true,
                    shell.ticks);
        }
    }
}
