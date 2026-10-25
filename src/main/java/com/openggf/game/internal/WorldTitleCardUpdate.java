package com.openggf.game.internal;

import com.openggf.game.TitleCardProvider;
import com.openggf.game.session.WorldSession;

/** Game-owned title operation published by the actual native root, not a contributed provider. */
@FunctionalInterface
public interface WorldTitleCardUpdate {
    void update(TitleCardProvider title, WorldSession world);

    static void dispatch(TitleCardProvider title, WorldSession world) {
        var root = world == null ? null : NativeGameModuleLineage.root(world.rootGameModule());
        var operation = root == null ? null : root.getGameService(WorldTitleCardUpdate.class);
        if (operation != null) operation.update(title, world);
        else title.update();
    }
}
