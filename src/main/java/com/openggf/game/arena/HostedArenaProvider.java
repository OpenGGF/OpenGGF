package com.openggf.game.arena;

import com.openggf.game.nativemenu.NativeMenuHostContext;
import java.util.List;

/** Game service for native hosted arenas. */
public interface HostedArenaProvider {
    List<String> characters();
    HostedArenaSession open(HostedArenaRequest request, NativeMenuHostContext context);
}
