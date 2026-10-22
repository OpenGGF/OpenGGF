package com.openggf.level.objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.openggf.camera.Camera;
import com.openggf.game.PlayableEntity;
import com.openggf.game.mutators.ObjectHeadRole;
import com.openggf.game.solid.SolidExecutionRegistry;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Pattern;
import com.openggf.level.objects.StageplayObjectDirection.*;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class TestStageplayObjectDirection {
    @Test
    void directedNativeGraphSkipsRoutineContactBookkeepingAndUnloadThenResumesAtOriginalAnchor() {
        Fixture fixture = new Fixture(List.of());
        var direction = ObjectDirectionAccess.forManager(fixture.objects);
        TestObject root =
                fixture.objects.createDynamicObject(
                        () -> new TestObject(new ObjectSpawn(100, 80, 1, 0, 0, false, 80)));
        TestObject child = root.child();
        direction.direct(root, heldTemplate(), new Direction(900, 400, true, true, 0, -1));
        assertTrue(direction.suspended(root));
        assertTrue(direction.suspended(child));
        new ObjectExecutionController(fixture.objects).execute(root, null, List.of(), false, false);
        new ObjectExecutionController(fixture.objects)
                .execute(child, null, List.of(), false, false);
        fixture.objects.snapshotTouchResponseState();
        fixture.objects.refreshPostCameraRenderState();
        fixture.objects.runPostPlayerHooks(null, 100);
        assertTrue(fixture.objects.getSolidProviderObjects().isEmpty());
        assertTrue(fixture.objects.getTouchResponseObjects().isEmpty());
        assertTrue(
                fixture.objects
                        .solidContacts()
                        .processParticipantCheckpoint(root, mock(PlayableEntity.class), false)
                        .perPlayer()
                        .isEmpty());
        fixture.objects.update(60000, null, List.of(), 0, false);
        assertEquals(0, root.updates + child.updates);
        assertEquals(0, root.snapshots + child.snapshots);
        assertEquals(0, root.refreshes + child.refreshes);
        assertEquals(0, root.unloads + child.unloads);
        assertEquals(100, root.getX());
        assertTrue(fixture.objects.getActiveObjects().contains(root));
        direction.releaseToFree(root);
        new ObjectExecutionController(fixture.objects).execute(root, null, List.of(), false, false);
        assertEquals(1, root.updates);
        assertFalse(direction.suspended(child));
        assertEquals(100, root.getX());
    }

    @Test
    void ordinaryNativeChildSpawnDoesNotCreateAStageplayController() {
        Fixture fixture = new Fixture(List.of());
        TestObject root =
                fixture.objects.createDynamicObject(
                        () -> new TestObject(new ObjectSpawn(100, 80, 1, 0, 0, false, 80)));
        root.child();
        assertNull(fixture.objects.stageplayDirection);
    }

    @Test
    void portablePlacementIdentityDistinguishesDuplicateAnchorsAndSurvivesRemovalReadmission() {
        ObjectSpawn first = new ObjectSpawn(100, 80, 1, 2, 0, false, 80, 4);
        ObjectSpawn second = new ObjectSpawn(100, 80, 1, 2, 0, false, 80, 5);
        Fixture fixture = new Fixture(List.of(first, second));
        var direction = ObjectDirectionAccess.forManager(fixture.objects);
        ObjectInstance a = direction.materializePlaced(PlacementIdentity.of(first));
        ObjectInstance b = direction.materializePlaced(PlacementIdentity.of(second));
        assertNotSame(a, b);
        assertSame(a, direction.materializePlaced(first));
        direction.remove(PlacementIdentity.of(first));
        assertFalse(direction.allowsPlacement(first));
        assertTrue(direction.allowsPlacement(second));
        assertTrue(direction.findPlaced(PlacementIdentity.of(first)).isEmpty());
        assertSame(b, direction.findPlaced(PlacementIdentity.of(second)).orElseThrow());
        ObjectInstance readmitted = direction.materializePlaced(first);
        assertNotSame(a, readmitted);
        assertFalse(
                direction.allowsPlacement(first),
                "Explicit actor does not reopen ordinary placement scans");
    }

    @Test
    void
            clearRejectsUnrelatedFutureNativeObjectsButExplicitCastAndItsLaterChildrenRemainAdmitted() {
        Fixture fixture = new Fixture(List.of());
        var direction = ObjectDirectionAccess.forManager(fixture.objects);
        direction.clearObjects();
        TestObject unrelated = new TestObject(new ObjectSpawn(100, 80, 1, 0, 0, false, 80));
        fixture.objects.addDynamicObject(unrelated);
        assertFalse(fixture.objects.getActiveObjects().contains(unrelated));
        assertEquals(0, unrelated.updates + unrelated.unloads);
        TestObject authored =
                (TestObject)
                        ObjectDirectionAccess.createNative(
                                fixture.objects, new ObjectSpawn(100, 80, 1, 0, 0, false, 80));
        assertTrue(direction.allowsObject(authored));
        TestObject child = authored.child();
        assertTrue(direction.allowsObject(child));
        direction.clearObjects();
        assertFalse(fixture.objects.getActiveObjects().contains(authored));
        assertFalse(fixture.objects.getActiveObjects().contains(child));
        assertEquals(0, authored.unloads + child.unloads);
    }

    @Test
    void badnikClearKeepsMachineryAndRejectsFutureSemanticBadniksWithoutNameHeuristics() {
        Fixture fixture = new Fixture(List.of());
        var direction = ObjectDirectionAccess.forManager(fixture.objects);
        direction.clearBadniks();
        TestObject machine = new TestObject(new ObjectSpawn(100, 80, 1, 0, 0, false, 80));
        TestObject badnik =
                new TestObject(new ObjectSpawn(100, 80, 1, 0, 0, false, 80)) {
                    @Override
                    public ObjectHeadRole headPresentationRole() {
                        return ObjectHeadRole.BADNIK;
                    }
                };
        fixture.objects.addDynamicObject(machine);
        fixture.objects.addDynamicObject(badnik);
        assertTrue(fixture.objects.getActiveObjects().contains(machine));
        assertFalse(fixture.objects.getActiveObjects().contains(badnik));
        assertEquals(0, badnik.updates + badnik.unloads);
    }

    @Test
    void badnikClearUsesContextualRegistryTraitsForNonbaseNativeRootsAndFutureAdmission() {
        ObjectRegistry registry =
                new AbstractObjectRegistry() {
                    @Override
                    protected void registerDefaultFactories() {
                        registerBadnikFactory(7, (spawn, owner) -> new TestObject(spawn));
                        registerFactory(8, (spawn, owner) -> new TestObject(spawn));
                    }

                    @Override
                    public String getPrimaryName(int id) {
                        return id == 7 ? "Ordinary machinery" : "Badnik-looking name";
                    }

                    @Override
                    protected ObjectRegistryCatalog.Semantics catalogSemantics(
                            int id, ObjectRegistryCatalog.Context context) {
                        return context.zone() == 9 && context.act() == 2
                                ? super.catalogSemantics(id, context)
                                : ObjectRegistryCatalog.Semantics.NONE;
                    }
                };
        Fixture fixture = new Fixture(List.of(), registry);
        var level = mock(com.openggf.level.LevelManager.class);
        when(level.getCurrentZone()).thenReturn(9);
        when(level.getCurrentAct()).thenReturn(2);
        when(fixture.services.levelManager()).thenReturn(level);
        var direction = ObjectDirectionAccess.forManager(fixture.objects);
        TestObject enemy =
                fixture.objects.createDynamicObject(
                        () ->
                                (TestObject)
                                        registry.create(
                                                new ObjectSpawn(100, 80, 7, 0, 0, false, 80)));
        TestObject child = enemy.child();
        TestObject machine =
                fixture.objects.createDynamicObject(
                        () ->
                                (TestObject)
                                        registry.create(
                                                new ObjectSpawn(140, 80, 8, 0, 0, false, 80)));
        assertNotEquals(ObjectHeadRole.BADNIK, enemy.headPresentationRole());
        direction.clearBadniks();
        assertFalse(fixture.objects.getActiveObjects().contains(enemy));
        assertFalse(fixture.objects.getActiveObjects().contains(child));
        assertTrue(fixture.objects.getActiveObjects().contains(machine));
        TestObject future =
                (TestObject) registry.create(new ObjectSpawn(180, 80, 7, 0, 0, false, 80));
        fixture.objects.addDynamicObject(future);
        assertFalse(fixture.objects.getActiveObjects().contains(future));
        assertEquals(
                0, enemy.updates + enemy.unloads + child.unloads + future.updates + future.unloads);
        when(level.getCurrentAct()).thenReturn(3);
        TestObject otherContext =
                fixture.objects.createDynamicObject(
                        () ->
                                (TestObject)
                                        registry.create(
                                                new ObjectSpawn(200, 80, 7, 0, 0, false, 80)));
        assertTrue(
                fixture.objects.getActiveObjects().contains(otherContext),
                "Use the injected logical source context, not a cached role from another act");
    }

    @Test
    void visualSnapshotRestoresHandleAndDirectionWithoutNativeSlotsAndRewindsRemovalPolicy() {
        Fixture fixture = new Fixture(List.of());
        var direction = ObjectDirectionAccess.forManager(fixture.objects);
        ObjectInstance visual =
                direction.installVisual(
                        "cast-robot", heldTemplate(), new Direction(5, 7, false, true, 0, -1));
        var adapter = direction.rewindSnapshottable();
        var saved = adapter.capture();
        direction.setDirection(visual, new Direction(100, 200, true, false, 0, -1));
        direction.clearObjects();
        adapter.restore(saved);
        assertTrue(
                fixture.objects.getActiveObjects().isEmpty(),
                "Baked visuals do not consume native SST slots");
        assertNotSame(
                visual,
                direction.findVisual("cast-robot").orElseThrow(),
                "A removed visual is re-resolved through its stable actor identity after rewind");
        assertEquals(saved, adapter.capture());
        assertTrue(direction.allowsPlacement(new ObjectSpawn(100, 80, 1, 0, 0, false, 80, 5)));
    }

    @Test
    void nativeCaptureEnumeratesMappingPosesAndFreezesPixelsPalettesAndMultipartOffsets() {
        Fixture fixture = new Fixture(List.of());
        Pattern source = new Pattern();
        source.setPixel(0, 0, (byte) 3);
        ObjectSpriteSheet sheet =
                new ObjectSpriteSheet(
                        new Pattern[] {source},
                        List.of(
                                new SpriteMappingFrame(
                                        List.of(
                                                new SpriteMappingPiece(
                                                        -4, -8, 1, 1, 0, false, false, 0))),
                                new SpriteMappingFrame(
                                        List.of(
                                                new SpriteMappingPiece(
                                                        4, 8, 1, 1, 0, true, false, 0)))),
                        0,
                        1);
        PatternSpriteRenderer renderer = new PatternSpriteRenderer(sheet, fixture.graphics);
        renderer.ensurePatternsCached(fixture.graphics, 512);
        com.openggf.level.Palette palette = new com.openggf.level.Palette();
        for (int i = 0; i < 16; i++) {
            palette.setColor(
                    i, new com.openggf.level.Palette.Color((byte) (i * 8), (byte) 20, (byte) 40));
        }
        fixture.graphics.cachePaletteTexture(palette, 0);
        TestObject root =
                fixture.objects.createDynamicObject(
                        () ->
                                new TestObject(new ObjectSpawn(100, 80, 1, 0, 0, false, 80)) {
                                    @Override
                                    public void appendRenderCommands(List<GLCommand> commands) {
                                        renderer.drawFrameIndex(1, 100, 80);
                                    }
                                });
        VisualTemplate template =
                ObjectDirectionAccess.forManager(fixture.objects).captureDynamic(root);
        assertEquals(1, template.parts().size());
        assertTrue(template.parts().getFirst().partId().startsWith("root/bucket"));
        assertTrue(template.parts().getFirst().partId().endsWith("/draw0"));
        assertEquals(2, template.parts().getFirst().poses().size());
        assertEquals(1, template.parts().getFirst().defaultPose());
        assertEquals(3, template.patterns().getFirst().a() & 15);
        assertEquals(0xff081428, template.palettes().getFirst().argb().get(1));
        assertEquals(4, template.parts().getFirst().poses().get(1).tiles().getFirst().x());
        source.setPixel(0, 0, (byte) 9);
        assertEquals(3, template.patterns().getFirst().a() & 15);
        assertThrows(UnsupportedOperationException.class, () -> template.patterns().clear());
        assertFalse(template.limitations().isEmpty());
    }

    @Test
    void explicitPoseWinsAndPureAnimationSamplingUsesExistingRunnerIncludingSwitchMarker() {
        var source = new com.openggf.sprites.animation.SpriteAnimationSet();
        source.addScript(
                0,
                new com.openggf.sprites.animation.SpriteAnimationScript(
                        0,
                        List.of(0),
                        com.openggf.sprites.animation.SpriteAnimationEndAction.LOOP,
                        0,
                        List.of(0xff)));
        source.addScript(
                1,
                new com.openggf.sprites.animation.SpriteAnimationScript(
                        0,
                        List.of(2),
                        com.openggf.sprites.animation.SpriteAnimationEndAction.SWITCH,
                        0,
                        List.of(0xfd, 0)));
        VisualPart part =
                new VisualPart(
                        "root/0",
                        4,
                        false,
                        0,
                        0,
                        List.of(
                                new VisualPose(0, List.of(), List.of()),
                                new VisualPose(1, List.of(), List.of()),
                                new VisualPose(2, List.of(), List.of())),
                        StageplayObjectDirection.pureAnimations(source, 3));
        assertEquals(
                1,
                StageplayObjectDirection.selectedPose(
                                part, new Direction(0, 0, false, true, 999, 1))
                        .pose());
        assertEquals(
                2,
                StageplayObjectDirection.selectedPose(
                                part, new Direction(0, 0, false, true, 1, -1, Map.of(), 1))
                        .pose());
        assertEquals(
                0,
                StageplayObjectDirection.selectedPose(
                                part, new Direction(0, 0, false, true, 1, -1, Map.of(), 2))
                        .pose());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        StageplayObjectDirection.selectedPose(
                                part, new Direction(0, 0, false, true, 999, -1)));
        assertEquals(
                0,
                StageplayObjectDirection.selectedPose(
                                heldTemplate().parts().getFirst(),
                                new Direction(0, 0, false, true, 0, -1))
                        .pose());
    }

    @Test
    void synthesizedAndRoutineChangingScriptsAreHeldAndUnsafeSwitchChainsAreExcluded() {
        var source = new com.openggf.sprites.animation.SpriteAnimationSet();
        source.addScript(
                0,
                new com.openggf.sprites.animation.SpriteAnimationScript(
                        0,
                        List.of(0),
                        com.openggf.sprites.animation.SpriteAnimationEndAction.LOOP,
                        0));
        source.addScript(
                1,
                new com.openggf.sprites.animation.SpriteAnimationScript(
                        0,
                        List.of(0),
                        com.openggf.sprites.animation.SpriteAnimationEndAction.LOOP,
                        0,
                        List.of(0xff)));
        source.addScript(
                2,
                new com.openggf.sprites.animation.SpriteAnimationScript(
                        0,
                        List.of(0),
                        com.openggf.sprites.animation.SpriteAnimationEndAction.LOOP,
                        0,
                        List.of(0xfc)));
        source.addScript(
                3,
                new com.openggf.sprites.animation.SpriteAnimationScript(
                        0,
                        List.of(0),
                        com.openggf.sprites.animation.SpriteAnimationEndAction.SWITCH,
                        2,
                        List.of(0xfd, 2)));
        source.addScript(
                4,
                new com.openggf.sprites.animation.SpriteAnimationScript(
                        0,
                        List.of(0),
                        com.openggf.sprites.animation.SpriteAnimationEndAction.SWITCH,
                        3,
                        List.of(0xfd, 3)));
        assertEquals(
                List.of(1),
                StageplayObjectDirection.pureAnimations(source, 1).stream()
                        .map(VisualAnimation::id)
                        .toList());
    }

    @Test
    void sharedLoadPolicySurvivesManagerReplacementBeforeAnyRoutineRunsAndRestoresFlags() {
        StageplayObjectLoadPolicy policy = new StageplayObjectLoadPolicy();
        Fixture old = new Fixture(List.of());
        StageplayObjectDirection first = new StageplayObjectDirection(old.objects, policy);
        old.objects.stageplayDirection = first;
        first.clearObjects();
        var saved = policy.rewindAdapter().capture();
        first.close();
        Fixture next = new Fixture(List.of());
        next.objects.stageplayDirection = new StageplayObjectDirection(next.objects, policy);
        TestObject event = new TestObject(new ObjectSpawn(100, 80, 1, 0, 0, false, 80));
        next.objects.addDynamicObject(event);
        assertTrue(next.objects.getActiveObjects().isEmpty());
        assertEquals(0, event.updates + event.unloads);
        policy.rewindAdapter().resetForMissingSnapshot();
        assertTrue(next.objects.stageplayDirection.allowsObject(event));
        policy.rewindAdapter().restore(saved);
        assertFalse(next.objects.stageplayDirection.allowsObject(event));
    }

    @Test
    void directedReplayRetainsNativeSubtileRowsInPainterAndSatPreparation() {
        Fixture fixture = new Fixture(List.of());
        var direction = ObjectDirectionAccess.forManager(fixture.objects);
        var tile = new VisualTile(0, 0, false, true, false, 1, 2, 8, 8, false, 7, 2, 6);
        var template =
                new VisualTemplate(
                        1,
                        0,
                        0,
                        List.of(
                                new VisualPart(
                                        "root/0",
                                        4,
                                        false,
                                        0,
                                        0,
                                        List.of(new VisualPose(0, List.of(tile), List.of())))),
                        List.of(new PatternPixels(3, 0, 0, 0)),
                        List.of(new PaletteColors(java.util.Collections.nCopies(16, 0))),
                        List.of());
        ObjectInstance visual =
                direction.installVisual(
                        "clipped", template, new Direction(100, 80, false, true, 0, -1));
        try {
            var painter =
                    com.openggf.level.render.SpritePresentationRenderer.prepare(
                            fixture.graphics,
                            0,
                            0,
                            () ->
                                    fixture.objects.drawUnifiedBucketWithPriority(
                                            4, fixture.graphics));
            var sat =
                    com.openggf.level.render.SpritePresentationRenderer.prepare(
                            fixture.graphics,
                            0,
                            0,
                            () -> {
                                fixture.graphics.beginSpriteSatCollection();
                                fixture.graphics.setCurrentSpriteSatBucket(4);
                                fixture.objects.drawUnifiedBucketWithPriority(4, fixture.graphics);
                                fixture.graphics.endSpriteSatCollectionAndReplay();
                            });
            for (var frame : List.of(painter, sat)) {
                assertEquals(1, frame.tiles().size());
                var drawn = frame.tiles().getFirst();
                assertEquals(101, drawn.x());
                assertEquals(82, drawn.y());
                assertEquals(2, drawn.rowStart());
                assertEquals(6, drawn.rowEnd());
                assertTrue(drawn.vFlip());
                assertEquals(
                        7,
                        drawn.occlusionMask(),
                        "Clipped rows preserve the captured palette mask");
                assertEquals(3, frame.patternVersions().get(drawn.patternId()).a() & 15);
            }
        } finally {
            direction.removeDynamic(visual);
        }
    }

    @Test
    void distinctSmallDirectedGraphsSharePaletteAndExceedOldTenGraphTrailLimit() {
        Fixture fixture = new Fixture(List.of());
        var direction = ObjectDirectionAccess.forManager(fixture.objects);
        java.util.List<ObjectInstance> actors = new java.util.ArrayList<>();
        var palette = new PaletteColors(java.util.Collections.nCopies(16, 0));
        try {
            for (int i = 0; i < 24; i++) {
                var tile = new VisualTile(0, 0, false, false, false, 0, 0, 8, 8, false, 15, 0, 8);
                var template =
                        new VisualTemplate(
                                1,
                                0,
                                0,
                                List.of(
                                        new VisualPart(
                                                "root/0",
                                                4,
                                                false,
                                                0,
                                                0,
                                                List.of(
                                                        new VisualPose(
                                                                0, List.of(tile), List.of())))),
                                List.of(new PatternPixels(i, 0, 0, 0)),
                                List.of(palette),
                                List.of());
                actors.add(
                        direction.installVisual(
                                "ensemble-" + i,
                                template,
                                new Direction(i * 8, 80, false, true, 0, -1)));
            }
            var frame =
                    com.openggf.level.render.SpritePresentationRenderer.prepare(
                            fixture.graphics,
                            0,
                            0,
                            () ->
                                    fixture.objects.drawUnifiedBucketWithPriority(
                                            4, fixture.graphics));
            assertEquals(24, frame.tiles().size());
            assertEquals(
                    24,
                    frame.tiles().stream()
                            .map(com.openggf.graphics.SpritePresentation.Tile::patternId)
                            .distinct()
                            .count());
            assertEquals(
                    1,
                    frame.tiles().stream()
                            .map(com.openggf.graphics.SpritePresentation.Tile::palette)
                            .distinct()
                            .count());
            direction.removeDynamic(actors.get(11));
            var retained =
                    com.openggf.level.render.SpritePresentationRenderer.prepare(
                            fixture.graphics,
                            0,
                            0,
                            () ->
                                    fixture.objects.drawUnifiedBucketWithPriority(
                                            4, fixture.graphics));
            assertEquals(23, retained.tiles().size());
            assertEquals(
                    1,
                    retained.tiles().stream()
                            .map(com.openggf.graphics.SpritePresentation.Tile::palette)
                            .distinct()
                            .count());
        } finally {
            direction.close();
        }
    }

    @Test
    void multipartDirectionRetainsChildSlotOcclusionAroundOtherNativeObjects() {
        Fixture fixture = new Fixture(List.of());
        var direction = ObjectDirectionAccess.forManager(fixture.objects);
        TestObject root =
                fixture.objects.createDynamicObject(
                        () -> new TestObject(new ObjectSpawn(100, 80, 1, 0, 0, false, 80)));
        fixture.objects.createDynamicObject(
                () ->
                        new TestObject(new ObjectSpawn(100, 80, 1, 0, 0, false, 80)) {
                            @Override
                            public int getPriorityBucket() {
                                return 4;
                            }

                            @Override
                            public void appendRenderCommands(List<GLCommand> commands) {
                                fixture.graphics.renderPatternWithId(
                                        777, new com.openggf.level.PatternDesc(), 77, 80);
                            }
                        });
        TestObject child = root.child();
        var palette = new PaletteColors(java.util.Collections.nCopies(16, 0));
        var first = new VisualTile(0, 0, false, false, false, 1, 0, 8, 8, false, 15, 0, 8);
        var second = new VisualTile(0, 0, false, false, false, 3, 0, 8, 8, false, 15, 0, 8);
        var template =
                new VisualTemplate(
                        1,
                        0,
                        0,
                        List.of(
                                new VisualPart(
                                        "root/0",
                                        4,
                                        false,
                                        root.getSlotIndex(),
                                        0,
                                        List.of(new VisualPose(0, List.of(first), List.of()))),
                                new VisualPart(
                                        "root/1",
                                        4,
                                        false,
                                        child.getSlotIndex(),
                                        0,
                                        List.of(new VisualPose(0, List.of(second), List.of())))),
                        List.of(new PatternPixels(1, 0, 0, 0)),
                        List.of(palette),
                        List.of());
        direction.direct(root, template, new Direction(0, 80, false, true, 0, -1));
        try {
            var frame =
                    com.openggf.level.render.SpritePresentationRenderer.prepare(
                            fixture.graphics,
                            0,
                            0,
                            () ->
                                    fixture.objects.drawUnifiedBucketWithPriority(
                                            4, fixture.graphics));
            assertEquals(
                    List.of(3f, 77f, 1f),
                    frame.tiles().stream()
                            .map(com.openggf.graphics.SpritePresentation.Tile::x)
                            .toList());
        } finally {
            direction.close();
        }
    }

    @Test
    void structuralPartIdentityDoesNotDependOnOtherActorsNativeSlotPressure() {
        java.util.List<java.util.Set<String>> captured = new java.util.ArrayList<>();
        for (boolean pressure : List.of(false, true)) {
            Fixture fixture = new Fixture(List.of());
            var direction = ObjectDirectionAccess.forManager(fixture.objects);
            PatternSpriteRenderer renderer =
                    new PatternSpriteRenderer(
                            new ObjectSpriteSheet(
                                    new Pattern[] {new Pattern()},
                                    List.of(
                                            new SpriteMappingFrame(
                                                    List.of(
                                                            new SpriteMappingPiece(
                                                                    0, 0, 1, 1, 0, false, false,
                                                                    0)))),
                                    0,
                                    1),
                            fixture.graphics);
            renderer.ensurePatternsCached(fixture.graphics, 512);
            if (pressure) {
                fixture.objects.createDynamicObject(
                        () -> new TestObject(new ObjectSpawn(40, 80, 1, 0, 0, false, 80)));
            }
            TestObject root =
                    fixture.objects.createDynamicObject(
                            () ->
                                    new TestObject(new ObjectSpawn(100, 80, 1, 0, 0, false, 80)) {
                                        @Override
                                        public void appendRenderCommands(List<GLCommand> commands) {
                                            renderer.drawFrameIndex(0, getX(), getY());
                                        }
                                    });
            root.visualChild(renderer);
            VisualTemplate template = direction.captureDynamic(root);
            captured.add(
                    template.parts().stream()
                            .map(VisualPart::partId)
                            .collect(java.util.stream.Collectors.toSet()));
        }
        assertEquals(captured.get(0), captured.get(1));
        assertEquals(2, captured.get(0).size());
        assertTrue(captured.get(0).stream().anyMatch(path -> path.startsWith("root/0/")));
    }

    @Test
    void residualPrimitivesFailExplicitlyInsteadOfChangingNativeRenderOrder() {
        var primitive =
                new VisualPrimitive(
                        0,
                        com.openggf.graphics.SpritePresentation.PrimitiveKind.RECTANGLE,
                        0,
                        List.of(
                                new com.openggf.graphics.SpritePresentation.Vertex(
                                        0, 0, 8, 8, 0xffffffff)));
        var part =
                new VisualPart(
                        "root/0",
                        4,
                        false,
                        0,
                        0,
                        List.of(new VisualPose(0, List.of(), List.of(primitive))));
        var failure =
                assertThrows(
                        UnsupportedRenderOrderException.class,
                        () ->
                                new VisualTemplate(
                                        1, 0, 0, List.of(part), List.of(), List.of(), List.of()));
        assertTrue(failure.getMessage().contains("use Free mode"));
        assertTrue(failure.getMessage().contains("Puppet/Pose cannot preserve this render order"));
    }

    @Test
    void captureKeepsOnlyUsedScopedPaletteAndNativeEntryOcclusionMask() {
        Fixture fixture = new Fixture(List.of());
        // Four distinct unrelated world rows must not consume a directed template's four-row
        // budget.
        for (int row = 0; row < 4; row++) {
            var palette = new com.openggf.level.Palette();
            palette.setColor(
                    1, new com.openggf.level.Palette.Color((byte) (row * 20), (byte) 1, (byte) 2));
            fixture.graphics.cachePaletteTexture(palette, row);
        }
        var used = new com.openggf.level.Palette();
        used.setColor(1, new com.openggf.level.Palette.Color((byte) 91, (byte) 92, (byte) 93));
        Object owner = new Object();
        var lease =
                com.openggf.graphics.ScopedPaletteGraphicsAccess.acquire(
                        fixture.graphics,
                        owner,
                        new com.openggf.graphics.PaletteView[] {used, used, used, used});
        var resolutions = new java.util.concurrent.atomic.AtomicInteger();
        VisualTemplate template;
        TestObject root;
        try {
            int paletteIndex =
                    com.openggf.graphics.ScopedPaletteGraphicsAccess.base(
                            lease, fixture.graphics, owner);
            PatternSpriteRenderer renderer = visualRenderer(fixture, 0);
            // Native scoped encounter producers retain the logical art_tile row and attach a
            // trusted palette projection to the sprite subject. A bank index in art_tile is not
            // the native scoped path (the native painter has only two palette bits).
            var subject =
                    new com.openggf.graphics.SpritePresentation.Subject(
                            "scoped",
                            com.openggf.graphics.SpritePresentation.Part.OBJECT_BODY,
                            false,
                            100,
                            null,
                            null,
                            (graphics, logicalLine) -> {
                                assertSame(fixture.graphics, graphics);
                                resolutions.incrementAndGet();
                                return paletteIndex + logicalLine;
                            });
            root =
                    fixture.objects.createDynamicObject(
                            () ->
                                    new TestObject(new ObjectSpawn(100, 80, 1, 0, 0, false, 80)) {
                                        @Override
                                        public int getTileOcclusionPaletteMask() {
                                            return 7;
                                        }

                                        @Override
                                        public void appendRenderCommands(List<GLCommand> commands) {
                                            com.openggf.graphics.SpritePresentation.withSubject(
                                                    fixture.graphics,
                                                    subject,
                                                    () -> renderer.drawFrameIndex(0, 100, 80));
                                        }
                                    });
            fixture.graphics.setCurrentSpriteTileOcclusionPaletteMask(15);
            template = ObjectDirectionAccess.forManager(fixture.objects).captureDynamic(root);
            assertEquals(1, template.palettes().size());
            assertEquals(0xff5b5c5d, template.palettes().getFirst().argb().get(1));
            assertEquals(
                    7,
                    template.parts()
                            .getFirst()
                            .poses()
                            .getFirst()
                            .tiles()
                            .getFirst()
                            .occlusionMask());
            assertEquals(15, fixture.graphics.getCurrentSpriteTileOcclusionPaletteMask());
        } finally {
            com.openggf.graphics.ScopedPaletteGraphicsAccess.release(lease, owner);
        }
        fixture.objects.removeDynamicObject(root);
        assertTrue(resolutions.get() > 0);
        int capturedResolutions = resolutions.get();
        assertEquals(1, template.parts().size());
        int capturedBucket = template.parts().getFirst().bucket();
        var direction = ObjectDirectionAccess.forManager(fixture.objects);
        ObjectInstance visual =
                direction.installVisual(
                        "frozen-scoped-palette",
                        template,
                        new Direction(100, 80, false, true, 0, -1));
        try {
            var frame =
                    com.openggf.level.render.SpritePresentationRenderer.prepare(
                            fixture.graphics,
                            0,
                            0,
                            () ->
                                    fixture.objects.drawUnifiedBucketWithPriority(
                                            capturedBucket, fixture.graphics));
            assertEquals(1, frame.tiles().size());
            var colors =
                    com.openggf.graphics.ObjectVisualGraphicsAccess.palette(
                            fixture.graphics, frame.tiles().getFirst().palette());
            assertEquals(0xff5b5c5d, colors.get(1));
            assertEquals(
                    capturedResolutions,
                    resolutions.get(),
                    "Directed replay must not retain the retired source palette resolver");
        } finally {
            direction.removeDynamic(visual);
        }
    }

    @Test
    void childCreationPathsKeepNonlexicalOrderAcrossRewindAndFreeRecapture() {
        Fixture fixture = new Fixture(List.of());
        var direction = ObjectDirectionAccess.forManager(fixture.objects);
        PatternSpriteRenderer renderer = visualRenderer(fixture, 0);
        TestObject root =
                fixture.objects.createDynamicObject(
                        () -> new TestObject(new ObjectSpawn(100, 80, 1, 0, 0, false, 80)));
        for (int i = 0; i < 12; i++) {
            root.visualChild(renderer);
        }
        var before = direction.captureDynamic(root);
        direction.direct(root, before, new Direction(200, 80, false, true, 0, -1));
        var adapter = direction.rewindSnapshottable();
        adapter.restore(adapter.capture());
        direction.releaseToFree(root);
        var after = direction.captureDynamic(root);
        assertEquals(
                before.parts(),
                after.parts(),
                "Native child identity strings must not reorder creation paths");
    }

    @Test
    void childCreationPathsPreserveMissingOrdinalAndNextCounterAcrossRewind() {
        Fixture fixture = new Fixture(List.of());
        var direction = ObjectDirectionAccess.forManager(fixture.objects);
        PatternSpriteRenderer renderer = visualRenderer(fixture, 0);
        TestObject root =
                fixture.objects.createDynamicObject(
                        () -> new TestObject(new ObjectSpawn(100, 80, 1, 0, 0, false, 80)));
        TestObject missing = root.visualChild(renderer);
        root.visualChild(renderer);
        fixture.objects.removeDynamicObject(missing);
        var adapter = direction.rewindSnapshottable();
        var snapshot = adapter.capture();
        assertEquals(List.of(1), snapshot.edges().stream().map(EdgeSnapshot::ordinal).toList());
        adapter.restore(snapshot);
        var restored = direction.captureDynamic(root);
        assertEquals(1, restored.parts().size());
        assertTrue(restored.parts().getFirst().partId().startsWith("root/1/"));
        root.visualChild(renderer);
        var expanded = direction.captureDynamic(root);
        assertEquals(2, expanded.parts().size());
        assertTrue(expanded.parts().stream().anyMatch(part -> part.partId().startsWith("root/2/")));
        assertTrue(
                expanded.parts().stream().noneMatch(part -> part.partId().startsWith("root/0/")));
    }

    @Test
    void nativeSatControlGraphFailsTypedBeforeCpuCaptureCanOmitItsMask() {
        Fixture fixture = new Fixture(List.of());
        var direction = ObjectDirectionAccess.forManager(fixture.objects);
        TestObject root =
                fixture.objects.createDynamicObject(
                        () ->
                                new TestObject(new ObjectSpawn(100, 80, 1, 0, 0, false, 80)) {
                                    @Override
                                    public void appendRenderCommands(List<GLCommand> commands) {
                                        com.openggf.game.sonic3k.S3kSpriteMaskSupport.submitFrame4(
                                                fixture.graphics, 100, 80);
                                    }
                                });
        var failure =
                assertThrows(
                        UnsupportedRenderOrderException.class,
                        () -> direction.captureDynamic(root));
        assertTrue(failure.getMessage().contains(root.getName()));
        assertTrue(failure.getMessage().contains("Obj_SpriteMask"));
        assertTrue(failure.getMessage().contains("use Free mode"));
        assertFalse(fixture.graphics.isSpriteSatCollectionActive());
        assertFalse(com.openggf.level.render.ObjectSpriteCapture.isObserving());
    }

    @Test
    void nativeCaptureCopiesMappedPixelsWithoutReadingUnusedSparseArtCapacity() {
        Fixture fixture = new Fixture(List.of());
        Pattern pixels = new Pattern();
        pixels.setPixel(0, 0, (byte) 9);
        PatternSpriteRenderer renderer =
                new PatternSpriteRenderer(
                        new ObjectSpriteSheet(
                                new Pattern[] {pixels, null, null},
                                List.of(
                                        new SpriteMappingFrame(
                                                List.of(
                                                        new SpriteMappingPiece(
                                                                0, 0, 1, 1, 0, false, false, 0)))),
                                0,
                                1),
                        fixture.graphics);
        renderer.ensurePatternsCached(fixture.graphics, 512);
        TestObject root =
                fixture.objects.createDynamicObject(
                        () ->
                                new TestObject(new ObjectSpawn(100, 80, 1, 0, 0, false, 80)) {
                                    @Override
                                    public void appendRenderCommands(List<GLCommand> commands) {
                                        renderer.drawFrameIndex(0, 100, 80);
                                    }
                                });
        var template = ObjectDirectionAccess.forManager(fixture.objects).captureDynamic(root);
        assertEquals(1, template.patterns().size());
        assertEquals(9, template.patterns().getFirst().a() & 15);
        assertEquals(1, template.parts().getFirst().poses().getFirst().tiles().size());
    }

    @Test
    void nativeCaptureRejectsMappedMissingPixelsInsteadOfUsingStaleAtlasResidency() {
        Fixture fixture = new Fixture(List.of());
        Pattern stale = new Pattern();
        stale.setPixel(0, 0, (byte) 7);
        fixture.graphics.cachePatternTexture(stale, 513);
        PatternSpriteRenderer renderer =
                new PatternSpriteRenderer(
                        new ObjectSpriteSheet(
                                new Pattern[] {new Pattern(), null},
                                List.of(
                                        new SpriteMappingFrame(
                                                List.of(
                                                        new SpriteMappingPiece(
                                                                0, 0, 1, 1, 1, false, false, 0)))),
                                0,
                                1),
                        fixture.graphics);
        renderer.ensurePatternsCached(fixture.graphics, 512);
        TestObject root =
                fixture.objects.createDynamicObject(
                        () ->
                                new TestObject(new ObjectSpawn(100, 80, 1, 0, 0, false, 80)) {
                                    @Override
                                    public void appendRenderCommands(List<GLCommand> commands) {
                                        renderer.drawFrameIndex(0, 100, 80);
                                    }
                                });
        var failure =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                ObjectDirectionAccess.forManager(fixture.objects)
                                        .captureDynamic(root));
        assertTrue(failure.getMessage().contains("mapping pose 0"));
        assertTrue(failure.getMessage().contains("unloaded ROM pattern 1"));
    }

    private static PatternSpriteRenderer visualRenderer(Fixture fixture, int palette) {
        var renderer =
                new PatternSpriteRenderer(
                        new ObjectSpriteSheet(
                                new Pattern[] {new Pattern()},
                                List.of(
                                        new SpriteMappingFrame(
                                                List.of(
                                                        new SpriteMappingPiece(
                                                                0, 0, 1, 1, 0, false, false, 0)))),
                                palette,
                                1),
                        fixture.graphics);
        renderer.ensurePatternsCached(fixture.graphics, 512);
        return renderer;
    }

    private static VisualTemplate heldTemplate() {
        return new VisualTemplate(
                1,
                100,
                80,
                List.of(
                        new VisualPart(
                                "root/0",
                                4,
                                false,
                                0,
                                0,
                                List.of(new VisualPose(0, List.of(), List.of())))),
                List.of(),
                List.of(),
                List.of());
    }

    private static final class Fixture {
        final GraphicsManager graphics = new GraphicsManager();
        final ObjectServices services = mock(ObjectServices.class);
        final ObjectManager objects;

        Fixture(List<ObjectSpawn> spawns) {
            this(spawns, null);
        }

        Fixture(List<ObjectSpawn> spawns, ObjectRegistry suppliedRegistry) {
            graphics.setHeadlessMode(true);
            for (int i = 0; i < 4; i++) {
                graphics.cachePaletteTexture(new com.openggf.level.Palette(), i);
            }
            when(services.graphicsManager()).thenReturn(graphics);
            when(services.solidExecutionRegistry()).thenReturn(SolidExecutionRegistry.inert());
            ObjectRegistry registry =
                    suppliedRegistry != null
                            ? suppliedRegistry
                            : new ObjectRegistry() {
                                public ObjectInstance create(ObjectSpawn spawn) {
                                    return new TestObject(spawn);
                                }

                                public void reportCoverage(List<ObjectSpawn> source) {}

                                public String getPrimaryName(int id) {
                                    return "TEST";
                                }
                            };
            objects =
                    new ObjectManager(
                            spawns,
                            registry,
                            0,
                            null,
                            null,
                            graphics,
                            mock(Camera.class),
                            services);
            when(services.objectManager()).thenReturn(objects);
        }
    }

    private static class TestObject extends AbstractObjectInstance
            implements SolidObjectProvider, TouchResponseProvider {
        int updates, snapshots, refreshes, unloads;

        TestObject(ObjectSpawn spawn) {
            super(spawn, "test native object");
        }

        TestObject child() {
            return spawnChild(() -> new TestObject(new ObjectSpawn(120, 80, 1, 0, 0, false, 80)));
        }

        TestObject visualChild(PatternSpriteRenderer renderer) {
            return spawnChild(
                    () ->
                            new TestObject(new ObjectSpawn(120, 80, 1, 0, 0, false, 80)) {
                                @Override
                                public void appendRenderCommands(List<GLCommand> commands) {
                                    renderer.drawFrameIndex(0, getX(), getY());
                                }
                            });
        }

        @Override
        public void update(int vIntRunCount, PlayableEntity player) {
            updates++;
        }

        @Override
        public void snapshotPreUpdatePosition() {
            snapshots++;
        }

        @Override
        public void snapshotTouchResponseState() {
            snapshots++;
        }

        @Override
        public void refreshPostCameraRenderState() {
            refreshes++;
        }

        @Override
        public void onUnload() {
            unloads++;
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {}

        public SolidObjectParams getSolidParams() {
            throw new AssertionError("Directed solid provider executed");
        }

        public int getCollisionFlags() {
            return 1;
        }

        public int getCollisionProperty() {
            throw new AssertionError("Directed touch provider executed");
        }
    }
}
