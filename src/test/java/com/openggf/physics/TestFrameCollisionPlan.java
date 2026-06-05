package com.openggf.physics;

import com.openggf.level.objects.ObjectManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestFrameCollisionPlan {

    @Test
    void playableFrameNamesTerrainThenSolidsWithTraceRecording() {
        FrameCollisionPlan plan = FrameCollisionPlan.playableFrame();

        assertTrue(plan.runsTerrainProbes());
        assertTrue(plan.runsSolidObjectResolution());
        assertFalse(plan.runsPostResolutionGroundMode());
        assertTrue(plan.recordsTrace());
        assertEquals(List.of(
                        FrameCollisionPlan.Phase.TERRAIN_PROBES,
                        FrameCollisionPlan.Phase.SOLID_OBJECT_RESOLUTION),
                plan.orderedPhases());
    }

    @Test
    void terrainOnlyNamesOnlyTerrainProbes() {
        FrameCollisionPlan plan = FrameCollisionPlan.terrainOnly();

        assertTrue(plan.runsTerrainProbes());
        assertFalse(plan.runsSolidObjectResolution());
        assertFalse(plan.runsPostResolutionGroundMode());
        assertFalse(plan.recordsTrace());
        assertEquals(List.of(FrameCollisionPlan.Phase.TERRAIN_PROBES), plan.orderedPhases());
    }

    @Test
    void objectResolutionOnlyNamesOnlySolidObjectResolution() {
        FrameCollisionPlan plan = FrameCollisionPlan.objectResolutionOnly();

        assertFalse(plan.runsTerrainProbes());
        assertTrue(plan.runsSolidObjectResolution());
        assertFalse(plan.runsPostResolutionGroundMode());
        assertFalse(plan.recordsTrace());
        assertEquals(List.of(FrameCollisionPlan.Phase.SOLID_OBJECT_RESOLUTION), plan.orderedPhases());
    }

    @Test
    void objectResolutionPlanDelegatesToObjectManagerWithMovementFlags() {
        CollisionSystem collisionSystem = new CollisionSystem(new TerrainCollisionManager());
        ObjectManager objectManager = Mockito.mock(ObjectManager.class);
        AbstractPlayableSprite sprite = newTestSprite();

        collisionSystem.setObjectManager(objectManager);
        collisionSystem.runSolidObjectResolution(
                FrameCollisionPlan.objectResolutionOnly(), sprite, true, true);

        Mockito.verify(objectManager).updateSolidContacts(sprite, true, true);
    }

    @Test
    void legacyStepDelegatesThroughPlayableFramePlan() {
        CollisionSystem collisionSystem = new CollisionSystem(new EmptyTerrainCollisionManager());
        RecordingCollisionTrace trace = new RecordingCollisionTrace();
        AbstractPlayableSprite sprite = newTestSprite();

        collisionSystem.setTrace(trace);
        collisionSystem.step(sprite, new Sensor[0], new Sensor[0]);

        assertEquals(List.of(
                        CollisionEvent.EventType.TERRAIN_PROBES_START,
                        CollisionEvent.EventType.TERRAIN_PROBES_COMPLETE,
                        CollisionEvent.EventType.SOLID_CONTACTS_START,
                        CollisionEvent.EventType.SOLID_CONTACTS_COMPLETE),
                trace.getEvents().stream().map(CollisionEvent::type).toList());
    }

    private static AbstractPlayableSprite newTestSprite() {
        return new AbstractPlayableSprite("sonic", (short) 0, (short) 0) {
            @Override
            protected void defineSpeeds() {
            }

            @Override
            protected void createSensorLines() {
            }

            @Override
            public void draw() {
            }
        };
    }

    private static final class EmptyTerrainCollisionManager extends TerrainCollisionManager {
        @Override
        public SensorResult[] getSensorResult(Sensor[] sensors) {
            return new SensorResult[0];
        }
    }
}
