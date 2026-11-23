package com.openggf.level.objects;

import com.openggf.game.GameServices;
import com.openggf.game.GameStateManager;
import com.openggf.game.PlayableEntity;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic1.objects.Sonic1AnimalsObjectInstance;
import com.openggf.game.sonic1.objects.badniks.Sonic1DestructionConfig;
import com.openggf.game.sonic3k.objects.Sonic3kPointsObjectInstance;
import com.openggf.game.sonic3k.objects.badniks.Sonic3kBadnikConfig;
import com.openggf.level.objects.DestructionEffects.DestructionConfig;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestDestructionEffects {

    private ObjectManager objectManager;
    private ObjectServices services;

    @BeforeEach
    void setUp() {
        TestEnvironment.resetAll();

        var renderManager = mock(ObjectRenderManager.class);
        services = new StubObjectServices() {
            @Override
            public ObjectRenderManager renderManager() {
                return renderManager;
            }

            @Override
            public GameStateManager gameState() {
                return GameServices.gameState();
            }

            @Override
            public ObjectManager objectManager() {
                return objectManager;
            }

            @Override
            public com.openggf.camera.Camera camera() {
                return GameServices.camera();
            }
        };

        objectManager = new ObjectManager(List.of(), new NoOpObjectRegistry(), 0, null, null,
                null, GameServices.camera(), services);
    }

    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    @Test
    void typedBeforeLatchCueRemainsOneShotAndPrecedesTransferredReplacement() {
        List<String> events=new java.util.ArrayList<>();AbstractBadnikInstance[] badnik={null};
        ObjectServices cueServices=new StubObjectServices() {
            @Override public ObjectManager objectManager(){return objectManager;}
            @Override public boolean playSfx(com.openggf.audio.StreamedMusicPort.SfxRef sound) {
                events.add("sound:"+badnik[0].isDestroyed());return true;
            }
        };
        objectManager = new ObjectManager(List.of(), new NoOpObjectRegistry(), 0, null, null,
                null, GameServices.camera(), cueServices);
        var config=new DestructionConfig(0,null,false,null,(x,y,svc,points)-> {
            events.add("replacement:"+badnik[0].isDestroyed());
            return new RecordingChildObject(new ObjectSpawn(x,y,0x27,0,0,false,0),"Replacement");
        },false).withSound(DestructionEffects.SoundCue.namespaced(
                new com.openggf.audio.StreamedMusicPort.SfxRef("sample-platformer","hit"),
                DestructionEffects.SoundOrder.BEFORE_LATCH));
        badnik[0]=new AbstractBadnikInstance(new ObjectSpawn(100,100,1,0,0,false,0),"Cue",config) {
            @Override protected void updateMovement(int vIntRunCount,com.openggf.game.PlayableEntity player) { }
            @Override protected int getCollisionSizeIndex(){return 0;}
            @Override public void appendRenderCommands(List<com.openggf.graphics.GLCommand> commands) { }
        };
        badnik[0].setServices(cueServices);objectManager.addDynamicObject(badnik[0]);
        int slot=badnik[0].getSlotIndex();
        badnik[0].onPlayerAttack(null,null);badnik[0].onPlayerAttack(null,null);
        assertEquals(List.of("sound:false","replacement:true"),events);
        assertTrue(objectManager.getActiveObjects().stream().anyMatch(object->object instanceof RecordingChildObject replacement &&replacement.getSlotIndex()==slot));
    }

    @Test
    void semanticCueRunsAfterReplacementWithoutDuplicatingTheNativeId() {
        List<String> events=new java.util.ArrayList<>();
        ObjectServices cueServices=new StubObjectServices() {
            @Override public ObjectManager objectManager(){return objectManager;}
            @Override public void playSfx(com.openggf.audio.GameSound sound){events.add(sound.name());}
            @Override public void playSfx(int sound){events.add("native:"+sound);}
        };
        var config=new DestructionConfig(0xC1,null,false,null,(x,y,svc,points)-> {
            events.add("replacement");return new RecordingChildObject(new ObjectSpawn(x,y,0x27,0,0,false,0),"Replacement");
        },false).withSound(DestructionEffects.SoundCue.semantic(com.openggf.audio.GameSound.BADNIK_HIT,
                DestructionEffects.SoundOrder.AFTER_EFFECTS));
        DestructionEffects.destroyBadnik(100,100,new ObjectSpawn(100,100,1,0,0,false,0),-1,null,cueServices,config);
        assertEquals(List.of("replacement","BADNIK_HIT"),events);
    }

    @Test
    void noPopupRecipeUsesTheSameDeferredAnimalOwnerAndNeverInstallsAChildPointsFactory() throws Exception {
        var config=DestructionEffects.deferredAnimalWithoutPopup(0xC1,true);
        assertEquals(0xC1,config.sfxId());assertTrue(config.useRespawnTracking());
        org.junit.jupiter.api.Assertions.assertNull(config.pointsFactory());
        org.junit.jupiter.api.Assertions.assertNull(config.explosionFactory());
        ObjectSpawn spawn=new ObjectSpawn(100,100,0x28,0,0,false,0);
        ObjectInstance animal=config.animalFactory().create(spawn,services);
        assertEquals(AnimalObjectInstance.class,animal.getClass());
        var points=AnimalObjectInstance.class.getDeclaredField("pointsFactory");points.setAccessible(true);
        org.junit.jupiter.api.Assertions.assertNull(points.get(animal),"Obj29 is deferred by the animal, not config.pointsFactory");
    }

    @Test
    void sonic1BadnikDestructionDefersAnimalUntilExplosionExecutes() {
        DestructionConfig config = Sonic1DestructionConfig.S1_DESTRUCTION_CONFIG;
        config = new DestructionConfig(
                config.sfxId(),
                config.animalFactory(),
                config.useRespawnTracking(),
                null,
                config.explosionFactory(),
                config.pointsAllocatedBeforeAnimal());

        DestructionEffects.destroyBadnik(
                0x0100,
                0x0120,
                new ObjectSpawn(0x0100, 0x0120, 0x78, 0, 0, false, 0),
                40,
                null,
                services,
                config);

        assertTrue(objectManager.getActiveObjects().stream()
                        .anyMatch(ExplosionObjectInstance.class::isInstance),
                "S1 badnik destruction should immediately replace the badnik with an explosion");
        assertFalse(objectManager.getActiveObjects().stream()
                        .anyMatch(Sonic1AnimalsObjectInstance.class::isInstance),
                "S1 should not spawn the animal until ExplosionItem routine 0 runs");

        objectManager.update(0, null, List.of(), 1);

        assertTrue(objectManager.getActiveObjects().stream()
                        .anyMatch(Sonic1AnimalsObjectInstance.class::isInstance),
                "S1 explosion processing should spawn the ROM-ported Sonic1 animals object");
        assertEquals(40, objectManager.getActiveObjects().stream()
                        .filter(ExplosionObjectInstance.class::isInstance)
                        .map(ExplosionObjectInstance.class::cast)
                        .mapToInt(ExplosionObjectInstance::getSlotIndex)
                        .findFirst()
                        .orElse(-1),
                "S1 explosion should inherit the destroyed badnik slot");
    }

    @Test
    void defaultBadnikDestructionDefersChildrenUntilExplosionExecutes() {
        DestructionConfig config = new DestructionConfig(
                -1,
                (spawn, svc) -> new RecordingChildObject(spawn, "Animal"),
                false,
                (spawn, svc, pts) -> new RecordingChildObject(spawn, "Points-" + pts),
                null,
                false);

        DestructionEffects.destroyBadnik(
                0x0190,
                0x0078,
                new ObjectSpawn(0x0190, 0x0078, 0xA7, 0, 0, false, 0),
                15,
                null,
                services,
                config);

        assertEquals(1, objectManager.getActiveObjects().stream()
                        .filter(ExplosionObjectInstance.class::isInstance)
                        .count(),
                "The shared recipe should immediately replace the badnik with an explosion");
        assertEquals(0, objectManager.getActiveObjects().stream()
                        .filter(RecordingChildObject.class::isInstance)
                        .count(),
                "Explicit explosion-owned factories run when the explosion executes");
        assertEquals(15, objectManager.getActiveObjects().stream()
                        .filter(ExplosionObjectInstance.class::isInstance)
                        .map(ExplosionObjectInstance.class::cast)
                        .mapToInt(ExplosionObjectInstance::getSlotIndex)
                        .findFirst()
                        .orElse(-1),
                "default explosion should inherit the destroyed badnik slot");

        objectManager.update(0, null, List.of(), 1);

        assertEquals(2, objectManager.getActiveObjects().stream()
                        .filter(RecordingChildObject.class::isInstance)
                        .count(),
                "Explosion update should allocate animal and points children from the explosion routine");
    }

    @Test
    void sonic2AnimalAllocatesPointsOnItsOwnFirstExecuteObjectsPass() {
        // S2 RunObjects scans the full 128-slot SST, while AllocateObject starts
        // at Dynamic_Object_RAM (global slot 16). With Obj27 retained in slot 22,
        // Obj28 therefore lands in the already-visited lower slot 16 and waits
        // for the following pass (docs/s2disasm/s2.asm:24596-24636,
        // 25125-25146,46707-46715).
        objectManager = new ObjectManager(List.of(), new NoOpObjectRegistry(ObjectSlotLayout.SONIC_2),
                0, null, null, null, GameServices.camera(), services);
        DestructionConfig config = new DestructionConfig(
                -1,
                (spawn, svc) -> AnimalObjectInstance.deferredArtVariant(spawn, svc,
                        (pointsSpawn, pointsSvc, points) -> new RecordingChildObject(
                                pointsSpawn, "Points-" + points)),
                true,
                null,
                null,
                false);

        DestructionEffects.destroyBadnik(
                0x078E,
                0x0538,
                new ObjectSpawn(0x078E, 0x0538, 0xA7, 0, 0, false, 0),
                22,
                null,
                services,
                config);

        objectManager.update(0x06DD, null, List.of(), 1);

        assertEquals(1, objectManager.getActiveObjects().stream()
                        .filter(AnimalObjectInstance.class::isInstance)
                        .count(),
                "S2 Obj27_InitWithAnimal allocates Obj28 from the explosion routine "
                        + "(docs/s2disasm/s2.asm:46707-46715)");
        assertEquals(0, objectManager.getActiveObjects().stream()
                        .filter(RecordingChildObject.class::isInstance)
                        .count(),
                "When Obj28 receives a lower free slot than Obj27, ExecuteObjects has already passed it");

        objectManager.update(0x06DD, null, List.of(), 2);

        assertEquals(1, objectManager.getActiveObjects().stream()
                        .filter(RecordingChildObject.class::isInstance)
                        .count(),
                "S2 Obj28_InitRandom allocates Obj29 during the animal's own first routine pass "
                        + "(docs/s2disasm/s2.asm:24596-24636)");
    }

    @Test
    void s3kLowerAnimalSlotDefersPopupUntilNextPassEvenAfterRewind() {
        useS3kSlotLayout();
        destroyS3kBadnikAt(5);

        objectManager.update(0, null, List.of(), 1);

        var animal = activeAnimal();
        assertEquals(4, animal.getSlotIndex());
        assertEquals(120, animal.getY());
        assertEquals(0, s3kPopups().size(), "The first pass has already visited the animal slot");

        var rewind = objectManager.rewindSnapshottable();
        var betweenPasses = rewind.capture();
        objectManager.update(0, null, List.of(), 2);
        assertS3kPopup(6, 117);
        assertEquals(120, activeAnimal().getY(), "The animal initializes without moving");

        objectManager.setRewindInPlaceRestoreEnabledForTest(false);
        rewind.restore(betweenPasses);
        assertNotSame(animal, activeAnimal());
        assertEquals(0, s3kPopups().size());
        objectManager.update(0, null, List.of(), 2);
        assertS3kPopup(6, 117);
        objectManager.update(0, null, List.of(), 3);
        assertS3kPopup(6, 114);
    }

    @Test
    void s3kHigherAnimalSlotAllocatesPopupDuringTheSamePass() {
        useS3kSlotLayout();
        var betweenExplosionAndAnimal = new RecordingChildObject(
                new ObjectSpawn(0, 0, 1, 0, 0, false, 0), "Slot observer") {
            @Override
            public void update(int vIntRunCount, PlayableEntity player) {
                assertEquals(6, activeAnimal().getSlotIndex());
                assertEquals(0, s3kPopups().size(), "The later animal has yet to dispatch");
            }
        };
        objectManager.addDynamicObjectAtSlot(betweenExplosionAndAnimal, 5);
        destroyS3kBadnikAt(4);

        objectManager.update(0, null, List.of(), 1);

        assertEquals(120, activeAnimal().getY());
        assertS3kPopup(7, 117);
    }

    @Test
    void s3kFailedAnimalAllocationLeavesNoPopupAndDoesNotRetry() {
        useS3kSlotLayout();
        RecordingChildObject lastOccupant = null;
        for (int slot = 5; slot < ObjectSlotLayout.SONIC_3K.lastDynamicSlotExclusive(); slot++) {
            lastOccupant = new RecordingChildObject(new ObjectSpawn(0, 0, 1, 0, 0, false, 0), "Occupant");
            objectManager.addDynamicObjectAtSlot(lastOccupant, slot);
        }
        destroyS3kBadnikAt(4);

        objectManager.update(0, null, List.of(), 1);
        assertNoS3kDestructionChildren();

        lastOccupant.setDestroyed(true);
        objectManager.update(0, null, List.of(), 2);
        objectManager.update(0, null, List.of(), 3);
        assertNoS3kDestructionChildren();
    }

    private void useS3kSlotLayout() {
        objectManager = new ObjectManager(List.of(), new NoOpObjectRegistry(ObjectSlotLayout.SONIC_3K),
                0, null, null, null, GameServices.camera(), services);
    }

    private void destroyS3kBadnikAt(int slot) {
        var player = mock(PlayableEntity.class);
        when(player.incrementBadnikChain()).thenReturn(500);
        DestructionEffects.destroyBadnik(400, 120, new ObjectSpawn(400, 120, 0xA7, 0, 0, false, 0),
                slot, player, services, Sonic3kBadnikConfig.destruction());
    }

    private AnimalObjectInstance activeAnimal() {
        return objectManager.getActiveObjects().stream().filter(AnimalObjectInstance.class::isInstance)
                .map(AnimalObjectInstance.class::cast).findFirst().orElseThrow();
    }

    private List<Sonic3kPointsObjectInstance> s3kPopups() {
        return objectManager.getActiveObjects().stream().filter(Sonic3kPointsObjectInstance.class::isInstance)
                .map(Sonic3kPointsObjectInstance.class::cast).toList();
    }

    private void assertS3kPopup(int slot, int y) {
        var popups = s3kPopups();
        assertEquals(1, popups.size());
        AbstractPointsObjectInstance popup = popups.getFirst();
        assertEquals(slot, popup.getSlotIndex());
        assertEquals(400, popup.getX());
        assertEquals(y, popup.getY());
        assertEquals(2, popup.scoreFrame, "500 points must retain the native score mapping");
    }

    private void assertNoS3kDestructionChildren() {
        assertFalse(objectManager.getActiveObjects().stream().anyMatch(AnimalObjectInstance.class::isInstance));
        assertEquals(0, s3kPopups().size());
    }

    private static final class NoOpObjectRegistry implements ObjectRegistry {
        private final ObjectSlotLayout slotLayout;

        private NoOpObjectRegistry() {
            this(ObjectSlotLayout.SONIC_1);
        }

        private NoOpObjectRegistry(ObjectSlotLayout slotLayout) {
            this.slotLayout = slotLayout;
        }

        @Override
        public ObjectInstance create(ObjectSpawn spawn) {
            return null;
        }

        @Override
        public void reportCoverage(List<ObjectSpawn> spawns) {
        }

        @Override
        public String getPrimaryName(int objectId) {
            return "noop";
        }

        @Override
        public ObjectSlotLayout objectSlotLayout() {
            return slotLayout;
        }
    }

    private static class RecordingChildObject extends AbstractObjectInstance {
        private RecordingChildObject(ObjectSpawn spawn, String name) {
            super(spawn, name);
        }

        @Override
        public void update(int vIntRunCount, com.openggf.game.PlayableEntity player) {
        }

        @Override
        public void appendRenderCommands(List<com.openggf.graphics.GLCommand> commands) {
        }
    }
}
