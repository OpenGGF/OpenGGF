package com.openggf.mods.run;

import com.openggf.net.ghost.GhostFrame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestPlayerPoseGhostProjection {
    @Test
    void gameplayPoseProjectsAllNineWireFields() {
        PlayerPose pose = new PlayerPose(11, -22, 37, true, false, 3, true, true);
        assertEquals(new GhostFrame(11, -22, 37, true, false, false, 3, true, true),
                pose.toGhostFrame(false));
        assertEquals(new GhostFrame(11, -22, 37, true, false, true, 3, true, true),
                pose.toGhostFrame(true));
    }
}
