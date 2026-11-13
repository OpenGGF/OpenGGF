package openggf.timeattack;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TestMassStartRelease {
    @Test void offsetAndArenaLeadUseScheduledHubGo() {
        assertFalse(MassStartRelease.released(8999, 1000, 10000, false));
        assertTrue(MassStartRelease.released(9000, 1000, 10000, false));
        assertFalse(MassStartRelease.released(4999, 1000, 10000, true));
        assertTrue(MassStartRelease.released(5000, 1000, 10000, true));
        assertTrue(MassStartRelease.withinStartTolerance(12000, 10000));
        assertFalse(MassStartRelease.withinStartTolerance(12001, 10000));
    }
}
