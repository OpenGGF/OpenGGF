package com.openggf.game.timeattack.mp;

import com.openggf.ghost.GhostFrame;
import com.openggf.net.client.RemoteGhostPlayback;
import com.openggf.net.client.RemoteGhostRegistry;
import com.openggf.sprites.ghost.ActiveGhost;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TestRemoteGhostPresentation {
    @Test
    void nearestFourGetNamesAndFinishedGhostsDim() {
        List<ActiveGhost> ghosts = MultiplayerRaceCoordinator.presentRemoteGhosts(List.of(
                remote(1, "a", 1010, false), remote(2, "b", 1050, true),
                remote(3, "c", 1100, false), remote(4, "d", 1200, false),
                remote(5, "e", 1400, false), remote(6, "f", 1900, false)), 1000);
        assertEquals(4, ghosts.stream().filter(ghost -> ghost.nameplate() != null).count());
        assertNull(ghosts.get(5).nameplate());
        assertEquals(0.55f, ghosts.get(1).opacityScale());
        assertEquals(1f, ghosts.getFirst().opacityScale());
    }

    private static RemoteGhostRegistry.RemoteGhost remote(
            int slot, String name, int x, boolean finished) {
        GhostFrame frame = new GhostFrame(x, 100, 0,
                false, false, finished, 2, false);
        return new RemoteGhostRegistry.RemoteGhost(slot, name, "sonic",
                new RemoteGhostPlayback.RenderState(frame, 1f, false));
    }
}
