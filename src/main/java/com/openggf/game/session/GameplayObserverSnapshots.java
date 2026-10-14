package com.openggf.game.session;

import com.openggf.game.rewind.RewindSnapshottable;
import java.util.List;

/** Internal projection of the verified observer graph; registry/fault ownership stays with its host. */
public interface GameplayObserverSnapshots {
    List<RewindSnapshottable<?>> adapters();
}
