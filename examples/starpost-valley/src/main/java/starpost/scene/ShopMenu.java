package starpost.scene;

import java.util.List;
import starpost.core.Item;

/** Dandel's seed stall: this season's seeds, one packet per press. */
final class ShopMenu extends ListMenu {
    @Override
    protected String title(Shell shell) {
        return "DANDEL'S SEEDS";
    }

    @Override
    protected List<Item> rows(Shell shell) {
        return shell.catalog.seedsFor(shell.game.calendar.season());
    }

    @Override
    protected String right(Shell shell, Item item) {
        return shell.catalog.seedPrice(item.id()) + "";
    }

    @Override
    protected String footer(Shell shell) {
        return "CONFIRM: BUY ONE   BACK: LEAVE";
    }

    @Override
    protected void choose(Shell shell, Item item) {
        int price = shell.catalog.seedPrice(item.id());
        if (shell.game.rings < price) {
            shell.toast("NOT ENOUGH RINGS");
            shell.sfx(Sfx.ERROR);
        } else if (!shell.game.inventory.fits(item, 1)) {
            shell.toast("NO ROOM IN YOUR MONITORS");
            shell.sfx(Sfx.ERROR);
        } else {
            shell.game.rings -= price;
            shell.game.inventory.add(item, 1);
            shell.sfx(Sfx.REGISTER);
        }
    }
}
