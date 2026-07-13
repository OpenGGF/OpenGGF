package com.openggf.game.sonic2.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.identity.PlayerRefId;
import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.game.rewind.schema.CompactFieldCapturer;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.solid.ContactKind;
import com.openggf.game.solid.PlayerStandingState;
import com.openggf.game.solid.SolidExecutionRegistry;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestOOZSpringMultiSidekick {

    @Test
    void verticalPressureSpringLaunchesMainAndThreeStandingSidekicks() {
        TestablePlayableSprite main = player("sonic");
        TestablePlayableSprite first = player("tails");
        TestablePlayableSprite second = player("knuckles");
        TestablePlayableSprite third = player("sonic-2");
        OOZSpringObjectInstance spring = new OOZSpringObjectInstance(
                new ObjectSpawn(0x400, 0x300, 0x45, 0, 0, false, 0), "OOZSpring");
        SolidExecutionRegistry registry = mock(SolidExecutionRegistry.class);
        for (TestablePlayableSprite player : List.of(main, first, second, third)) {
            when(registry.previousStanding(spring, player))
                    .thenReturn(new PlayerStandingState(ContactKind.TOP, true, false));
        }
        spring.setServices(new Services(main, List.of(first, second, third), registry));
        for (int frame = 0; frame <= 9; frame++) {
            spring.update(frame, main);
        }

        for (TestablePlayableSprite player : List.of(main, first, second, third)) {
            assertEquals((short) -0x1000, player.getYSpeed(),
                    player.getCode() + " should receive the vertical pressure-spring launch");
        }
    }

    @Test
    void horizontalPendingLaunchFollowsSidekickIdentityAcrossReorder() throws Exception {
        TestablePlayableSprite main = player("sonic");
        TestablePlayableSprite first = player("tails");
        TestablePlayableSprite promoted = player("knuckles");
        MutableServices services = new MutableServices(main, List.of(first, promoted), mock(SolidExecutionRegistry.class));
        OOZSpringObjectInstance spring = horizontalSpring(services);
        spring.update(0, main);
        first.setDirection(com.openggf.physics.Direction.LEFT);
        spring.onSolidContact(first, new SolidContact(false, true, false, false, true, -1, false), 1);

        services.sidekicks = List.of(promoted, first);
        spring.update(2, main);

        assertTrue(stateSet(spring, "extensionPendingHorizontalLaunch").containsKey(first)
                        || first.getMoveLockTimer() == 0x0F,
                "the armed player must retain or consume its own pending launch after demotion");
        assertFalse(booleanField(spring, "pendingSidekickHorizontalLaunch"),
                "the promoted native P2 must not inherit another player's scalar latch");
    }

    @Test
    void omittedHorizontalExtensionStateIsPruned() throws Exception {
        TestablePlayableSprite main = player("sonic");
        TestablePlayableSprite first = player("tails");
        TestablePlayableSprite omitted = player("knuckles");
        MutableServices services = new MutableServices(main, List.of(first, omitted), mock(SolidExecutionRegistry.class));
        OOZSpringObjectInstance spring = horizontalSpring(services);
        spring.update(0, main);
        omitted.setDirection(com.openggf.physics.Direction.LEFT);
        spring.onSolidContact(omitted, new SolidContact(false, true, false, false, true, -1, false), 1);
        assertTrue(stateSet(spring, "extensionPendingHorizontalLaunch").containsKey(omitted));

        services.sidekicks = List.of(first);
        spring.update(2, main);

        assertFalse(stateSet(spring, "extensionPendingHorizontalLaunch").containsKey(omitted));
        assertFalse(stateSet(spring, "extensionFreshOrderedCarry").containsKey(omitted));
    }

    @Test
    void freshOrderedCarryFollowsIdentityAcrossPromotionAndDemotion() throws Exception {
        TestablePlayableSprite main = player("sonic");
        TestablePlayableSprite first = player("tails");
        TestablePlayableSprite promoted = player("knuckles");
        MutableServices services = new MutableServices(main, List.of(first, promoted), mock(SolidExecutionRegistry.class));
        OOZSpringObjectInstance spring = horizontalSpring(services);
        spring.update(0, main);
        setBooleanField(spring, "sidekickFreshOrderedCarry", true);

        services.sidekicks = List.of(promoted, first);
        spring.update(1, main);

        assertTrue(stateSet(spring, "extensionFreshOrderedCarry").containsKey(first));
        assertFalse(booleanField(spring, "sidekickFreshOrderedCarry"));
    }

    @Test
    void horizontalExtensionStateRestoresToReplacementPlayerRef() throws Exception {
        TestablePlayableSprite main = player("sonic");
        TestablePlayableSprite first = player("tails");
        TestablePlayableSprite extension = player("knuckles");
        MutableServices services = new MutableServices(main, List.of(first, extension), mock(SolidExecutionRegistry.class));
        OOZSpringObjectInstance spring = horizontalSpring(services);
        spring.update(0, main);
        extension.setDirection(com.openggf.physics.Direction.LEFT);
        spring.onSolidContact(extension, new SolidContact(false, true, false, false, true, -1, false), 1);
        var blob = CompactFieldCapturer.capture(spring, context(main, first, extension));

        TestablePlayableSprite replacementMain = player("replacement-main");
        TestablePlayableSprite replacementFirst = player("replacement-first");
        TestablePlayableSprite replacementExtension = player("replacement-extension");
        CompactFieldCapturer.restore(spring, blob,
                context(replacementMain, replacementFirst, replacementExtension));

        assertTrue(stateSet(spring, "extensionPendingHorizontalLaunch").containsKey(replacementExtension));
        assertFalse(stateSet(spring, "extensionPendingHorizontalLaunch").containsKey(extension));
    }

    private static OOZSpringObjectInstance horizontalSpring(TestObjectServices services) {
        OOZSpringObjectInstance spring = new OOZSpringObjectInstance(
                new ObjectSpawn(0x400, 0x300, 0x45, 0x10, 0, false, 0), "OOZSpring");
        spring.setServices(services);
        return spring;
    }

    @SuppressWarnings("unchecked")
    private static Map<PlayableEntity, Boolean> stateSet(OOZSpringObjectInstance spring, String name) throws Exception {
        Field field = OOZSpringObjectInstance.class.getDeclaredField(name);
        field.setAccessible(true);
        java.util.Set<PlayableEntity> set = (java.util.Set<PlayableEntity>) field.get(spring);
        Map<PlayableEntity, Boolean> result = new java.util.IdentityHashMap<>();
        set.forEach(player -> result.put(player, true));
        return result;
    }

    private static RewindCaptureContext context(TestablePlayableSprite main, TestablePlayableSprite... sidekicks) {
        RewindIdentityTable identities = new RewindIdentityTable();
        identities.registerPlayer(main, PlayerRefId.mainPlayer());
        for (int i = 0; i < sidekicks.length; i++) identities.registerPlayer(sidekicks[i], PlayerRefId.sidekick(i));
        return RewindCaptureContext.withIdentityTable(identities);
    }

    private static boolean booleanField(OOZSpringObjectInstance spring, String name) throws Exception {
        Field field = OOZSpringObjectInstance.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.getBoolean(spring);
    }

    private static void setBooleanField(OOZSpringObjectInstance spring, String name, boolean value) throws Exception {
        Field field = OOZSpringObjectInstance.class.getDeclaredField(name);
        field.setAccessible(true);
        field.setBoolean(spring, value);
    }

    private static TestablePlayableSprite player(String code) {
        TestablePlayableSprite player = new TestablePlayableSprite(code, (short) 0x400, (short) 0x2E0);
        player.setCpuControlled(!"sonic".equals(code));
        return player;
    }

    private static final class Services extends TestObjectServices {
        private final PlayableEntity main;
        private final List<? extends PlayableEntity> sidekicks;
        private final SolidExecutionRegistry registry;

        private Services(PlayableEntity main, List<? extends PlayableEntity> sidekicks,
                SolidExecutionRegistry registry) {
            this.main = main;
            this.sidekicks = sidekicks;
            this.registry = registry;
        }

        @Override public ObjectPlayerQuery playerQuery() {
            return new ObjectPlayerQuery(() -> main, () -> sidekicks);
        }

        @Override public SolidExecutionRegistry solidExecutionRegistry() {
            return registry;
        }
    }

    private static final class MutableServices extends TestObjectServices {
        private final PlayableEntity main;
        private List<? extends PlayableEntity> sidekicks;
        private final SolidExecutionRegistry registry;
        private MutableServices(PlayableEntity main, List<? extends PlayableEntity> sidekicks,
                                SolidExecutionRegistry registry) {
            this.main = main; this.sidekicks = sidekicks; this.registry = registry;
        }
        @Override public ObjectPlayerQuery playerQuery() { return new ObjectPlayerQuery(() -> main, () -> sidekicks); }
        @Override public SolidExecutionRegistry solidExecutionRegistry() { return registry; }
    }
}
