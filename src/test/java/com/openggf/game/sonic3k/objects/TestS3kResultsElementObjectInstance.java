package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayerCharacter;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.TestObjectServices;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestS3kResultsElementObjectInstance {

    @Test
    void nativeWidthBoundsAndPriorRenderBitDelayRetirementByOneDispatch() throws Exception {
        assertTrue(S3kResultsElementObjectInstance.withinNativeRenderWindow(-0x30, 0x30),
                "x+width==0 remains rendered on the native left boundary");
        assertFalse(S3kResultsElementObjectInstance.withinNativeRenderWindow(-0x31, 0x30));
        assertTrue(S3kResultsElementObjectInstance.withinNativeRenderWindow(0x16F, 0x30));
        assertFalse(S3kResultsElementObjectInstance.withinNativeRenderWindow(0x170, 0x30),
                "x-width==320 is outside the native right boundary");

        TestObjectServices services = new TestObjectServices();
        S3kResultsScreenObjectInstance parent = ObjectConstructionContext.withRewindActiveRestore(
                () -> ObjectConstructionContext.construct(services,
                        () -> new S3kResultsScreenObjectInstance(PlayerCharacter.SONIC_ALONE, 1)));
        parent.setServices(services);
        S3kResultsElementObjectInstance child = new S3kResultsElementObjectInstance(
                parent, 1, PlayerCharacter.SONIC_ALONE); // width=$30, exits left
        child.setServices(services);

        setField(parent, "state", 4);
        setField(parent, "exitQueueCounter", 1);
        setField(child, "currentX", -0x30);
        setField(child, "renderedOnScreen", true);

        child.update(0, null);
        assertFalse(child.isDestroyed(),
                "the dispatch that moves $20 across the edge uses the prior render bit");
        child.update(1, null);
        assertTrue(child.isDestroyed(),
                "Render_Sprites clears bit 7 after crossing, so the next dispatch deletes");
    }

    @Test
    void wideViewportRetirementUsesTheCenteredLiveRightEdge() throws Exception {
        GraphicsManager graphics = mock(GraphicsManager.class);
        when(graphics.getProjectionWidth()).thenReturn(400);
        TestObjectServices services = new TestObjectServices().withGraphicsManager(graphics);
        S3kResultsScreenObjectInstance parent = ObjectConstructionContext.withRewindActiveRestore(
                () -> ObjectConstructionContext.construct(services,
                        () -> new S3kResultsScreenObjectInstance(PlayerCharacter.SONIC_ALONE, 1)));
        parent.setServices(services);
        S3kResultsElementObjectInstance child = new S3kResultsElementObjectInstance(
                parent, 2, PlayerCharacter.SONIC_ALONE); // width=$70, exits right
        child.setServices(services);

        setField(parent, "state", 4);
        setField(parent, "exitQueueCounter", 3);
        setField(child, "currentX", 447);
        setField(child, "renderedOnScreen", true);

        child.update(0, null);
        assertFalse(child.isDestroyed(), "the crossing dispatch uses the prior render bit");
        child.update(1, null);
        assertTrue(child.isDestroyed(), "the next dispatch retires past the live 400px edge");
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
