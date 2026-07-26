package com.openggf.level.objects;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestObjectCollisionResponseList {

    @Test
    void resetClearsOnlyTheCurrentBuildAndPreservesTheFrozenPlayerReadView() {
        ObjectInstance special = publisher();
        ObjectInstance enemy = publisher();
        ObjectInstance newlyLoaded = publisher();
        ObjectCollisionResponseList list = new ObjectCollisionResponseList();
        list.captureCompletedBuild(List.of(special, enemy));
        list.freezePreviousReadView();

        list.addToCurrentBuild(newlyLoaded);
        list.resetCurrentBuild();

        assertTrue(list.usesPrevious());
        assertEquals(List.of(special, enemy), list.playerReadView());
        assertTrue(list.currentBuildView().isEmpty());
    }

    @Test
    void completedBuildBecomesTheNextOrdinaryPlayerReadViewInPublicationOrder() {
        ObjectInstance old = publisher();
        ObjectInstance dynamic = publisher();
        ObjectInstance fixed = publisher();
        ObjectCollisionResponseList list = new ObjectCollisionResponseList();
        list.captureCompletedBuild(List.of(old));
        list.freezePreviousReadView();
        list.resetCurrentBuild();

        list.addToCurrentBuild(dynamic);
        list.addToCurrentBuild(fixed);
        assertEquals(List.of(old), list.playerReadView(),
                "LOAD and this pass's publications cannot enter the already-frozen view");

        list.captureCompletedBuild();

        assertEquals(List.of(dynamic, fixed), list.playerReadView());
        assertSame(dynamic, list.playerReadView().get(0));
        assertSame(fixed, list.playerReadView().get(1));
    }

    private static ObjectInstance publisher() {
        ObjectInstance instance = mock(ObjectInstance.class);
        when(instance.publishesTouchResponseListEntryThisFrame()).thenReturn(true);
        return instance;
    }
}
