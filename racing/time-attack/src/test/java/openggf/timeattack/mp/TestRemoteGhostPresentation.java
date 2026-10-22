package openggf.timeattack.mp;

import com.openggf.net.ghost.GhostFrame;
import com.openggf.net.client.RemoteGhostPlayback;
import com.openggf.net.client.RemoteGhostRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestRemoteGhostPresentation {
    @Test
    void everyRemoteGhostGetsItsPlayerNameAndFinishedGhostsDim() {
        List<com.openggf.mods.run.GhostPose> ghosts = MultiplayerRaceCoordinator.presentRemoteGhosts(List.of(
                remote(1, "a", 1010, false), remote(2, "b", 1050, true),
                remote(3, "c", 1100, false), remote(4, "d", 1200, false),
                remote(5, "e", 1400, false), remote(6, "f", 1900, false)),
                slot -> slot == 3 ? com.openggf.net.identity.PlayerAppearance.decode("0E6.20E6")
                        : com.openggf.net.identity.PlayerAppearance.STOCK);
        assertEquals(List.of("a", "b", "c", "d", "e", "f"),
                ghosts.stream().map(com.openggf.mods.run.GhostPose::nameplate).toList());
        assertEquals(0.55f, ghosts.get(1).opacity());
        assertEquals(1f, ghosts.getFirst().opacity());
        assertEquals("0E6.20E6", ghosts.get(2).appearance().encode(), "each ghost carries its player's look");
        assertEquals("", ghosts.get(0).appearance().encode());
    }

    private static RemoteGhostRegistry.RemoteGhost remote(
            int slot, String name, int x, boolean finished) {
        GhostFrame frame = new GhostFrame(x, 100, 0,
                false, false, finished, 2, false);
        return new RemoteGhostRegistry.RemoteGhost(slot, name, "sonic",
                new RemoteGhostPlayback.RenderState(frame, 1f, false));
    }
}
