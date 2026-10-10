package starpost.scene;

import java.util.ArrayList;
import java.util.List;
import starpost.core.Inventory;
import starpost.core.Item;

/** The shipping signpost: put anything with a price in, and it pays out overnight. */
final class ShipMenu extends ListMenu {
    @Override
    protected String title(Shell shell) {
        return "SHIPPING SIGNPOST";
    }

    @Override
    protected List<Item> rows(Shell shell) {
        List<Item> out = new ArrayList<>();
        Inventory inv = shell.game.inventory;
        for (int i = 0; i < inv.size(); i++) {
            String id = inv.id(i);
            if (id != null) {
                Item item = shell.game.item(id);
                if (item.price() > 0 && !out.contains(item)) {
                    out.add(item);
                }
            }
        }
        return out;
    }

    @Override
    protected String right(Shell shell, Item item) {
        return shell.game.inventory.total(item.id()) + " X " + shell.game.sellPrice(item);
    }

    @Override
    protected String footer(Shell shell) {
        return "IN THE SIGNPOST: " + shell.game.shippingValue() + " RINGS";
    }

    @Override
    protected void choose(Shell shell, Item item) {
        int count = shell.game.inventory.remove(item.id(), Inventory.MAX_STACK * 3);
        shell.game.ship(item.id(), count);
        shell.sfx(Sfx.SIGNPOST);
    }
}
