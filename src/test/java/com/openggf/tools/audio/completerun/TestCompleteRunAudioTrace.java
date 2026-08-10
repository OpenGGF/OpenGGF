package com.openggf.tools.audio.completerun;

import static com.openggf.tools.audio.completerun.CompleteRunAudioTrace.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class TestCompleteRunAudioTrace {
    private final Fixture fixture = new Fixture();

    @Test
    void oneFrameCanContainZeroOrMultipleOrderedServices() {
        var empty = fixture.frame(860, List.of(), List.of());
        var busy = fixture.frame(861, List.of(fixture.request(1)), List.of(
                fixture.service(0), fixture.service(1)));

        assertEquals(List.of(), empty.services());
        assertEquals(List.of(0L, 1L),
                busy.services().stream().map(DriverService::ordinal).toList());
    }

    @Test
    void sameIdOwnersRemainDistinctByRequestOrdinal() {
        assertNotEquals(new OwnerRef(OwnerClass.SFX, "sfx.explosion", 0xC0, 7),
                new OwnerRef(OwnerClass.SFX, "sfx.explosion", 0xC0, 8));
    }

    @Test
    void rejectsSignedOrOutOfRangeChipBytes() {
        assertThrows(IllegalArgumentException.class, () -> new YmWrite(0, -1, 0x22, 0));
        assertThrows(IllegalArgumentException.class, () -> new YmWrite(0, 0, 0x100, 0));
        assertThrows(IllegalArgumentException.class, () -> new PsgWrite(0, 0x100));
    }

    @Test
    void profileRejectsUnorderedOrDuplicateRoles() {
        assertThrows(IllegalArgumentException.class,
                () -> fixture.profile.validateState(fixture.state(List.of(HardwareRole.PSG1, HardwareRole.FM1))));
        assertThrows(IllegalArgumentException.class,
                () -> fixture.profile.validateState(fixture.state(List.of(HardwareRole.FM1, HardwareRole.FM1))));
    }

    @Test
    void stateRejectsDuplicateFieldNames() {
        assertThrows(IllegalArgumentException.class, () -> new NormalizedState(List.of(
                new StateField("tempo", 1), new StateField("tempo", 2)), List.of()));
    }

    @Test
    void rejectsEmptyContentKeys() {
        assertThrows(IllegalArgumentException.class,
                () -> new OwnerRef(OwnerClass.SFX, "", 0xC0, 7));
        assertThrows(IllegalArgumentException.class,
                () -> new Request(1, OwnerClass.SFX, " ", 0xC0, "mailbox", 0));
    }

    @Test
    void registryRejectsUnknownProfile() {
        assertThrows(IllegalArgumentException.class,
                () -> CompleteRunAudioProfiles.require("unknown.complete-run.profile"));
    }

    @Test
    void profileRejectsInactiveRolesWithStaleFields() {
        var stale = new NormalizedState(List.of(new StateField("tempo", 1)), List.of(
                new RoleState(HardwareRole.FM1, false, List.of(new StateField("cursor", 4))),
                new RoleState(HardwareRole.PSG1, false, List.of())));

        assertThrows(IllegalArgumentException.class, () -> fixture.profile.validateState(stale));
    }

    @Test
    void metadataRejectsTerminalWithWrongFrameCountOrExclusiveEnd() {
        assertThrows(IllegalArgumentException.class,
                () -> fixture.metadata.validateTerminal(new Terminal(862, 1, 0, 0, 0, 0, 0, 0, "a")));
        assertThrows(IllegalArgumentException.class,
                () -> fixture.metadata.validateTerminal(new Terminal(863, 2, 0, 0, 0, 0, 0, 0, "a")));
        assertDoesNotThrow(() -> fixture.metadata.validateTerminal(
                new Terminal(862, 2, 0, 0, 0, 0, 0, 0, "a")));
    }

    private static final class Fixture {
        private final CompleteRunAudioProfile profile = new TestProfile();
        private final Metadata metadata = new Metadata("complete_run_audio.v1", "test.profile", 860, 862,
                List.of(HardwareRole.FM1, HardwareRole.PSG1), List.of("tempo"));

        private Frame frame(int row, List<Request> requests, List<DriverService> services) {
            return new Frame(row, null, false, requests, services);
        }

        private Request request(long ordinal) {
            return new Request(ordinal, OwnerClass.SFX, "sfx.explosion", 0xC0, "mailbox", 0);
        }

        private DriverService service(long ordinal) {
            return new DriverService(ordinal, "driver", List.of(),
                    state(List.of(HardwareRole.FM1, HardwareRole.PSG1)), List.of());
        }

        private NormalizedState state(List<HardwareRole> roles) {
            return new NormalizedState(List.of(new StateField("tempo", 1)), roles.stream()
                    .map(role -> new RoleState(role, false, List.of()))
                    .toList());
        }
    }

    private static final class TestProfile implements CompleteRunAudioProfile {
        @Override
        public String id() {
            return "test.profile";
        }

        @Override
        public List<HardwareRole> hardwareRoles() {
            return List.of(HardwareRole.FM1, HardwareRole.PSG1);
        }

        @Override
        public StateInventory stateInventory() {
            return new StateInventory(List.of("tempo"), List.of("cursor"));
        }
    }
}
