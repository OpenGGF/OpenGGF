package com.openggf.editor;

import com.openggf.i18n.UiMessages;
import java.util.Map;


import com.openggf.editor.commands.PlaceBlockCommand;
import com.openggf.editor.commands.DeleteObjectSpawnCommand;
import com.openggf.editor.commands.DeleteRingSpawnCommand;
import com.openggf.editor.commands.MoveObjectSpawnCommand;
import com.openggf.editor.commands.MoveRingSpawnCommand;
import com.openggf.editor.commands.PlaceObjectSpawnCommand;
import com.openggf.editor.commands.PlaceRingSpawnCommand;
import com.openggf.editor.persistence.EditorSaveManager;
import com.openggf.editor.persistence.FullLevelExporter;
import com.openggf.level.Block;
import com.openggf.level.Chunk;
import com.openggf.level.ChunkDesc;
import com.openggf.level.MutableLevel;
import com.openggf.level.PatternDesc;
import com.openggf.level.objects.ObjectPlacementEncoding;
import com.openggf.level.objects.ObjectRegistry;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.rings.RingSpawn;
import com.openggf.game.session.EditorCursorState;

import java.util.Objects;
public final class LevelEditorController {
    private final EditorCommandPalette commandPalette = new EditorCommandPalette();

    EditorCommandPalette commandPalette() { return commandPalette; }
    private static final int CHUNK_INDEX_MASK = 0x03FF;

    private final EditorCanvasNavigation canvasView = new EditorCanvasNavigation();
    public EditorCanvasNavigation canvasView() { return canvasView; }
    private com.openggf.editor.selection.CellRectangle regionSelection;
    private com.openggf.editor.selection.EditorRegionOperations.Clipboard clipboard;
    private final java.util.Set<Integer> clipboardBlocks = new java.util.HashSet<>();
    private final java.util.Set<Integer> clipboardChunks = new java.util.HashSet<>();
    private boolean selectingRegion;
    private boolean pastePreview;
    private int regionAnchorX, regionAnchorY;

    private final EditorHistory history = new EditorHistory();
    private EditorHierarchyDepth depth = EditorHierarchyDepth.WORLD;
    private EditorFocusRegion focusRegion = EditorFocusRegion.WORLD_CANVAS;
    private EditorSelectionState selection = EditorSelectionState.empty();
    private EditorCursorState worldCursor = new EditorCursorState(0, 0);
    private int blockGridSide = 8;
    private int selectedBlockCellX;
    private int selectedBlockCellY;
    private int selectedChunkCellX;
    private int selectedChunkCellY;
    private Integer selectedChunkDescriptorRaw;
    private Integer chunkBrushRaw;
    private Integer blockBrushIndex;
    private final java.util.Set<Integer> ownedBlocks=new java.util.HashSet<>();
    private final java.util.Set<Integer> ownedChunks=new java.util.HashSet<>();
    private Integer selectedPatternRaw;
    private int activeLayer;
    private MutableLevel level;
    private EditorSpawnEditMode spawnEditMode = EditorSpawnEditMode.TILES;
    private EditorStockObjectPalette objectPalette;
    private EditorSpawnFactory spawnFactory;
    private EditorPlacementIdAllocator placementIds;
    private ObjectSpawn selectedObjectSpawn;
    private com.openggf.editor.project.EditorProjectWorkspace projects;
    public void setProjects(com.openggf.editor.project.EditorProjectWorkspace value) { projects = value; }
    public com.openggf.editor.project.EditorProjectWorkspace projects() { return projects; }
    private com.openggf.level.metadata.ObjectPropertyProvider propertyProvider;
    private java.util.function.BiPredicate<Integer, Integer> paletteEditable = (line, color) -> false;
    private java.util.function.IntConsumer paletteRefresh = line -> { };
    public void setPaletteRefresh(java.util.function.IntConsumer refresh) { paletteRefresh = Objects.requireNonNull(refresh); }
    public boolean isPaletteCellEditable(int line, int color) { return paletteEditable.test(line, color); }
    public void editPaletteColor(int line, int color, int redStep, int greenStep, int blueStep) {
        executeCommand(com.openggf.editor.palette.EditorPaletteEditing.command(requireLevel(), line, color,
                com.openggf.editor.palette.EditorPaletteEditing.componentFromStep(redStep),
                com.openggf.editor.palette.EditorPaletteEditing.componentFromStep(greenStep),
                com.openggf.editor.palette.EditorPaletteEditing.componentFromStep(blueStep), paletteEditable, paletteRefresh));
        setStatus(UiMessages.text("editor.level_editor_controller.palette_color_updated_1g1pptl"));
    }

    private RingSpawn selectedRingSpawn;
    private ObjectSpawn selectedRingBackingObject;
    private boolean collisionOverlayEnabled;
    private EditorCollisionPath collisionPath = EditorCollisionPath.PRIMARY;
    private EditorSaveManager.ApplyResult persistenceStatus = EditorSaveManager.ApplyResult.NONE;
    private java.nio.file.Path lastExportDirectory;
    private Runnable pendingEditsFinisher = () -> { };
    private boolean cameraNavigationRequested = true;
    private boolean statusNotification;
    private String statusText = UiMessages.text("editor.level_editor_controller.ready_1axoqr");
    private java.util.function.BooleanSupplier modifiedState;
    private java.util.function.BooleanSupplier canvasNavigation = () -> false;

    public void setCanvasNavigationSupplier(java.util.function.BooleanSupplier supplier) {
        canvasNavigation = Objects.requireNonNull(supplier, "supplier");
    }

    public void setModifiedStateSupplier(java.util.function.BooleanSupplier supplier) {
        modifiedState = Objects.requireNonNull(supplier, "supplier");
    }

    public boolean isModified() {
        return modifiedState != null ? modifiedState.getAsBoolean()
                : level != null && level.isModifiedSinceLastSave();
    }

    public void setPendingEditsFinisher(Runnable finisher) {
        pendingEditsFinisher = Objects.requireNonNull(finisher, "finisher");
    }

    public void finishPendingEdits() { pendingEditsFinisher.run(); }
    public String statusText() { return statusText; }
    public void setStatus(String text) { statusText = Objects.requireNonNull(text, "text"); statusNotification = true; }
    public boolean hasStatusNotification() { return statusNotification; }
    public boolean consumeCameraNavigationRequest() {
        boolean requested = cameraNavigationRequested;
        cameraNavigationRequested = false;
        return requested;
    }
    /** Pointer hover never asks the camera to follow itself. */
    public void setPointerWorldCursor(EditorCursorState cursor) {
        worldCursor = clampWorldCursor(cursor.x(), cursor.y());
        if (selectingRegion) extendRegionToCursor();
    }
    public void focusRegion(EditorFocusRegion region) {
        finishPendingEdits();
        focusRegion = Objects.requireNonNull(region, "region");
    }
    public void selectActiveCell(int x, int y) {
        finishPendingEdits();
        int side = activeGridSide();
        if (depth == EditorHierarchyDepth.BLOCK) {
            selectedBlockCellX = clamp(x, 0, side - 1);
            selectedBlockCellY = clamp(y, 0, side - 1);
        } else if (depth == EditorHierarchyDepth.CHUNK) {
            selectedChunkCellX = clamp(x, 0, side - 1);
            selectedChunkCellY = clamp(y, 0, side - 1);
        }
    }

    public void attachLevel(MutableLevel level) {
        Objects.requireNonNull(level, "level");
        finishPendingEdits();
        if (this.level == level) return;
        this.level = level;
        canvasView.reset();
        regionSelection = null; clipboard = null; selectingRegion = false; pastePreview = false;
        clipboardBlocks.clear(); clipboardChunks.clear();
        cameraNavigationRequested = true;
        statusText = UiMessages.text("editor.level_editor_controller.ready_1axoqr");
        statusNotification = false;
        lastExportDirectory = null;
        history.clear();
        ownedBlocks.clear();ownedChunks.clear();
        depth = EditorHierarchyDepth.WORLD;
        focusRegion = EditorFocusRegion.WORLD_CANVAS;
        selection = EditorSelectionState.empty();
        worldCursor = new EditorCursorState(0, 0);
        blockGridSide = level.getChunksPerBlockSide();
        selectedBlockCellX = 0;
        selectedBlockCellY = 0;
        selectedChunkCellX = 0;
        selectedChunkCellY = 0;
        selectedChunkDescriptorRaw = null;
        chunkBrushRaw = null;
        blockBrushIndex = null;
        selectedPatternRaw = null;
        activeLayer = 0;
        spawnEditMode = EditorSpawnEditMode.TILES;
        objectPalette = null;
        propertyProvider = null;
        paletteEditable = (line, color) -> false;
        spawnFactory = null;
        placementIds = null;
        selectedObjectSpawn = null;
        selectedRingSpawn = null;
        selectedRingBackingObject = null;
        collisionOverlayEnabled = false;
        collisionPath = EditorCollisionPath.PRIMARY;
    }

    public void configureSpawnEditing(ObjectRegistry registry, ObjectPlacementEncoding encoding) {
        configureSpawnEditing(registry, encoding, registry::getPrimaryName);
    }

    /** Native edit files persist terrain/placements; palette authoring belongs to portable projects. */
    public void configureNativeSpawnEditing(ObjectRegistry registry, ObjectPlacementEncoding encoding) {
        configureSpawnEditing(registry, encoding);
        paletteEditable = (line, color) -> false;
    }

    public void configureSpawnEditing(ObjectRegistry registry, ObjectPlacementEncoding encoding,
                                      java.util.function.IntFunction<String> names) {
        MutableLevel attachedLevel = requireLevel();
        EditorStockObjectPalette previous=objectPalette;
        objectPalette = new EditorStockObjectPalette(registry, names);
        propertyProvider = registry instanceof com.openggf.level.metadata.ObjectPropertyProvider provider ? provider : null;
        paletteEditable = registry instanceof com.openggf.level.metadata.EditorPalettePolicyProvider provider
                ? provider::isPaletteCellEditable : (line, color) -> false;
        if(previous!=null) {
            if(previous.selectedIsKeyed()) {
                try {objectPalette.setObjectKey(previous.selectedObjectKey());}catch(IllegalArgumentException missing){setStatus(UiMessages.text("editor.level_editor_controller.object_brush_dependency_unavailable_15ftqrm"));}
            } else objectPalette.setObjectId(previous.selectedObjectId());
            objectPalette.setSubtype(previous.selectedSubtype());
            if(previous.renderFlags()!=0)objectPalette.toggleRenderFlag(previous.renderFlags());
            if(!previous.respawnTracked())objectPalette.toggleRespawnTracked();
        }
        if (placementIds == null) placementIds = new EditorPlacementIdAllocator(attachedLevel);
        spawnFactory = new EditorSpawnFactory(encoding, placementIds);
    }

    public void setSpawnEditMode(EditorSpawnEditMode mode) {
        finishPendingEdits();
        spawnEditMode = Objects.requireNonNull(mode, "mode");
        if (mode == EditorSpawnEditMode.OBJECTS && objectPalette != null && !objectPalette.selectedIsKeyed()
                && objectPalette.selectedObjectId() == 0)
            objectPalette.entries().stream().filter(entry -> entry.stockObjectId() != null && entry.previewArtKey() != null)
                    .findFirst().ifPresent(entry -> objectPalette.setObjectId(entry.stockObjectId()));
        if(mode!=EditorSpawnEditMode.TILES&&depth!=EditorHierarchyDepth.WORLD) {
            depth=EditorHierarchyDepth.WORLD;focusRegion=EditorFocusRegion.WORLD_CANVAS;
            selection=new EditorSelectionState(blockBrushIndex,null);
        }
        selectedObjectSpawn = null;
        selectedRingSpawn = null;
        selectedRingBackingObject = null;
        setStatus(switch (mode) {
            case TILES -> UiMessages.text("editor.level_editor_controller.terrain_brush_ready_y45ak2");
            case OBJECTS -> UiMessages.text("editor.level_editor_controller.choose_an_object_then_place_it_at_the_cursor_1rv355o");
            case RINGS -> UiMessages.text("editor.level_editor_controller.ring_brush_ready_pick_a_ring_to_move_or_delete_it_19gfga9");
        });
    }

    public EditorSpawnEditMode spawnEditMode() { return spawnEditMode; }
    public boolean isSpawnEditing() { return spawnEditMode != EditorSpawnEditMode.TILES; }
    public EditorStockObjectPalette objectPalette() {
        if (objectPalette == null) throw new IllegalStateException(UiMessages.text("editor.level_editor_controller.spawn_editing_is_not_configured_1u6wpck"));
        return objectPalette;
    }
    /** The object palette, or null before spawn editing is configured. */
    public EditorStockObjectPalette objectPaletteOrNull() { return objectPalette; }
    public boolean isPlaceableObject(EditorStockObjectPalette.Entry entry) {
        return entry.objectKey() != null || spawnFactory != null && spawnFactory.canCreateObject(entry.stockObjectId());
    }

    public void cycleSpawnEditMode() {
        setSpawnEditMode(switch (spawnEditMode) {
            case TILES -> EditorSpawnEditMode.OBJECTS;
            case OBJECTS -> EditorSpawnEditMode.RINGS;
            case RINGS -> EditorSpawnEditMode.TILES;
        });
    }

    public void placeObjectSpawnAtCursor() {
        finishPendingEdits();
        requireSpawnMode(EditorSpawnEditMode.OBJECTS);
        if (!objectPalette().selectedIsKeyed()
                && !requireSpawnFactory().canCreateObject(objectPalette().selectedObjectId())) {
            setStatus(UiMessages.text("editor.level_editor_controller.this_object_id_is_reserved_or_unsupported_choose_anothe_1ca2u00"));
            return;
        }
        ObjectSpawn spawn = objectPalette().selectedIsKeyed()
                ? requireSpawnFactory().createKeyedObjectSpawn(worldCursor.x(), worldCursor.y(),
                        objectPalette().selectedObjectKey(), objectPalette().selectedSubtype(), objectPalette().renderFlags(), objectPalette().respawnTracked())
                : requireSpawnFactory().createObjectSpawn(worldCursor.x(), worldCursor.y(),
                        objectPalette().selectedObjectId(), objectPalette().selectedSubtype(), objectPalette().renderFlags(), objectPalette().respawnTracked());
        if (com.openggf.level.objects.authored.AuthoredGizmos.handlesKey(spawn.objectKey()))
            spawn = com.openggf.editor.properties.EditorGizmoPlacements.initialize(
                    requireSpawnFactory().withProperties(spawn, spawn.subtype(), spawn.renderFlags(), true), requireLevel());
        history.execute(new PlaceObjectSpawnCommand(requireLevel(), spawn));
        selectedObjectSpawn = spawn;
    }

    public void placeRingSpawnAtCursor() {
        finishPendingEdits();
        requireSpawnMode(EditorSpawnEditMode.RINGS);
        RingSpawn spawn = requireSpawnFactory().createRingSpawn(worldCursor.x(), worldCursor.y());
        ObjectSpawn backingObject = requireSpawnFactory().createRingBackingObject(spawn);
        history.execute(new PlaceRingSpawnCommand(requireLevel(), spawn, backingObject));
        selectedRingSpawn = spawn;
        selectedRingBackingObject = backingObject;
    }

    public void placeSpawnAtCursor() {
        if (spawnEditMode == EditorSpawnEditMode.OBJECTS) placeObjectSpawnAtCursor();
        else if (spawnEditMode == EditorSpawnEditMode.RINGS) placeRingSpawnAtCursor();
    }

    public void deleteSpawnAtCursor() {
        finishPendingEdits();
        MutableLevel attachedLevel = requireLevel();
        if (spawnEditMode == EditorSpawnEditMode.OBJECTS) {
            ObjectSpawn spawn = findObjectAtCursor();
            if (spawn != null) {
                history.execute(new DeleteObjectSpawnCommand(attachedLevel, spawn));
                if (selectedObjectSpawn != null && selectedObjectSpawn.layoutIndex() == spawn.layoutIndex()) selectedObjectSpawn = null;
            }
        } else if (spawnEditMode == EditorSpawnEditMode.RINGS) {
            RingSpawn spawn = findRingAtCursor();
            if (spawn != null) {
                ObjectSpawn backingObject = attachedLevel.ringBackingObject(spawn);
                history.execute(new DeleteRingSpawnCommand(attachedLevel, spawn, backingObject));
                if (selectedRingSpawn != null && selectedRingSpawn.placementId() == spawn.placementId()) selectedRingSpawn = null;
                selectedRingBackingObject = null;
            }
        }
    }

    public void moveSelectedSpawn(int x, int y) {
        finishPendingEdits();
        MutableLevel attachedLevel = requireLevel();
        if (spawnEditMode == EditorSpawnEditMode.OBJECTS && selectedObjectSpawn != null) {
            ObjectSpawn moved = requireSpawnFactory().moveObjectSpawn(selectedObjectSpawn, x, y);
            history.execute(new MoveObjectSpawnCommand(attachedLevel, selectedObjectSpawn, moved));
            selectedObjectSpawn = moved;
        } else if (spawnEditMode == EditorSpawnEditMode.RINGS && selectedRingSpawn != null) {
            RingSpawn moved = requireSpawnFactory().moveRingSpawn(selectedRingSpawn, x, y);
            int dx = x - selectedRingSpawn.x();
            int dy = y - selectedRingSpawn.y();
            ObjectSpawn movedBackingObject = selectedRingBackingObject == null ? null
                    : requireSpawnFactory().moveRingBackingObject(selectedRingBackingObject,
                            selectedRingBackingObject.x() + dx,
                            selectedRingBackingObject.y() + dy);
            history.execute(new MoveRingSpawnCommand(attachedLevel, selectedRingSpawn, moved,
                    selectedRingBackingObject, movedBackingObject));
            selectedRingSpawn = moved;
            selectedRingBackingObject = movedBackingObject;
        }
    }

    public void eyedropSpawnAtCursor() {
        if (spawnEditMode == EditorSpawnEditMode.OBJECTS) {
            selectedObjectSpawn = findObjectAtCursor();
            if (selectedObjectSpawn != null) {
                try {objectPalette().eyedrop(selectedObjectSpawn);setStatus(UiMessages.text("editor.level_editor_controller.object_selected_subtype_h_j_flip_r_respawn_bffi6e"));}
                catch(IllegalArgumentException missing) {setStatus(UiMessages.text("editor.level_editor_controller.selected_missing_object_dependency_1y2wgdy", Map.of("value0", String.valueOf(selectedObjectSpawn.objectKey()))));}
            } else setStatus(UiMessages.text("editor.level_editor_controller.no_object_within_8_pixels_of_the_cursor_43qydy"));
        } else if (spawnEditMode == EditorSpawnEditMode.RINGS) {
            selectedRingSpawn = findRingAtCursor();
            selectedRingBackingObject = selectedRingSpawn == null ? null : requireLevel().ringBackingObject(selectedRingSpawn);
        }
    }

    private long cursorDistance(int x,int y) {
        long dx=(short)(x-worldCursor.x()),dy=(short)(y-worldCursor.y());return dx*dx+dy*dy;
    }
    private ObjectSpawn findObjectAtCursor() {
        if(level==null)return null;
        return level.getObjects().stream().filter(s -> !level.ringObjectPlacementMapping().containsKey(s))
                .filter(s -> cursorDistance(s.x(),s.y())<=64)
                .min(java.util.Comparator.comparingLong((ObjectSpawn s) -> cursorDistance(s.x(),s.y()))
                        .thenComparingInt(ObjectSpawn::layoutIndex)).orElse(null);
    }
    private RingSpawn findRingAtCursor() {
        if(level==null)return null;
        return level.getRings().stream().filter(s -> cursorDistance(s.x(),s.y())<=64)
                .min(java.util.Comparator.comparingLong((RingSpawn s) -> cursorDistance(s.x(),s.y()))
                        .thenComparingInt(RingSpawn::placementId)).orElse(null);
    }
    public ObjectSpawn selectedObjectSpawn() {return selectedObjectSpawn;}
    public com.openggf.level.metadata.ObjectPropertySchema selectedObjectProperties() {
        return selectedObjectSpawn == null || propertyProvider == null
                ? com.openggf.level.metadata.ObjectPropertySchema.FALLBACK : propertyProvider.objectProperties(selectedObjectSpawn);
    }
    public void setSelectedObjectProperty(String fieldId, int value) {
        if (selectedObjectSpawn == null) { setStatus(UiMessages.text("editor.level_editor_controller.pick_an_object_before_changing_its_properties_1tnztd6")); return; }
        var field = selectedObjectProperties().editableFields().stream().filter(f -> f.id().equals(fieldId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException(UiMessages.text("editor.level_editor_controller.unknown_object_property_gjgyg9", Map.of("value0", String.valueOf(fieldId)))));
        int subtype = field.encode(selectedObjectSpawn.subtype(), value);
        if (subtype != selectedObjectSpawn.subtype()) editSelectedSpawnProperties(subtype, selectedObjectSpawn.renderFlags(), selectedObjectSpawn.respawnTracked());
    }
    public void stepSelectedObjectProperty(int fieldIndex, int delta) {
        if (selectedObjectSpawn == null) return;
        var fields = selectedObjectProperties().editableFields();
        var field = fields.get(Math.floorMod(fieldIndex, fields.size()));
        setSelectedObjectProperty(field.id(), field.decode(field.step(selectedObjectSpawn.subtype(), delta)));
    }
    public java.util.List<com.openggf.editor.properties.AuthoredGizmoPropertyFields.Field> selectedGizmoFields() {
        return selectedObjectSpawn == null || selectedObjectSpawn.authoredProperties() == null ? java.util.List.of()
                : com.openggf.editor.properties.AuthoredGizmoPropertyFields.fields(selectedObjectSpawn.authoredProperties());
    }
    public void setSelectedGizmoProperty(String key, Object value) {
        if (selectedObjectSpawn == null || selectedObjectSpawn.authoredProperties() == null) return;
        var previous = selectedObjectSpawn;
        var properties = com.openggf.editor.properties.AuthoredGizmoPropertyFields.withValue(previous.authoredProperties(), key, value);
        if (properties.equals(previous.authoredProperties())) return;
        if (properties instanceof com.openggf.level.objects.authored.AuthoredObjectProperties.StageplayTrigger trigger && !trigger.blocking())
            throw new IllegalArgumentException(UiMessages.text("editor.level_editor_controller.the_public_stageplay_player_currently_requires_blocking_865edz"));
        var replacement = com.openggf.editor.properties.EditorGizmoPlacements.withProperties(previous, properties);
        var commands = new java.util.ArrayList<EditorCommand>();
        commands.add(new MoveObjectSpawnCommand(requireLevel(), previous, replacement));
        if (key.equals("twoWay") && properties instanceof com.openggf.level.objects.authored.AuthoredObjectProperties.Warp warp) {
            for (var other : requireLevel().getObjects()) if (other.layoutIndex() != previous.layoutIndex()
                    && other.authoredProperties() instanceof com.openggf.level.objects.authored.AuthoredObjectProperties.Warp endpoint
                    && endpoint.link().equals(warp.link()))
                commands.add(new MoveObjectSpawnCommand(requireLevel(), other,
                        com.openggf.editor.properties.EditorGizmoPlacements.withProperties(other,
                                new com.openggf.level.objects.authored.AuthoredObjectProperties.Warp(endpoint.width(), endpoint.height(), endpoint.link(), warp.twoWay()))));
        }
        executeCommand(new EditorCommand() {
            @Override public void apply() { commands.forEach(EditorCommand::apply); }
            @Override public void undo() { commands.reversed().forEach(EditorCommand::undo); }
        });
        selectedObjectSpawn = replacement;
    }
    public void stepSelectedGizmoProperty(int index, int delta) {
        var fields = selectedGizmoFields();
        if (fields.isEmpty()) return;
        var field = fields.get(Math.floorMod(index, fields.size()));
        Object current = field.value(selectedObjectSpawn.authoredProperties());
        switch (field.kind()) {
            case BOOL -> setSelectedGizmoProperty(field.key(), !(Boolean) current);
            case INT -> setSelectedGizmoProperty(field.key(), Math.clamp((long)(Integer)current + delta, field.min(), field.max()));
            case ENUM -> setSelectedGizmoProperty(field.key(), field.choices().get(Math.floorMod(field.choices().indexOf(current) + delta, field.choices().size())));
            case TEXT -> openGizmoTextField(field);
        }
    }
    public void openGizmoTextField(com.openggf.editor.properties.AuthoredGizmoPropertyFields.Field field) {
        if (projects == null) { setStatus(UiMessages.text("editor.level_editor_controller.open_an_authored_project_to_edit_text_properties_fokn11")); return; }
        var expected = selectedObjectSpawn;
        projects.menu().showForm(field.label(), java.util.List.of(new com.openggf.editor.project.EditorProjectMenu.Field(
                "value", field.label(), (String)field.value(expected.authoredProperties()), com.openggf.game.MenuTextEditor.Mode.TEXT, field.max())),
                UiMessages.text("editor.level_editor_controller.apply_property_19r8pk7"), values -> {
                    if (selectedObjectSpawn != expected) throw new IllegalStateException(UiMessages.text("editor.level_editor_controller.selected_object_changed_15fsteg"));
                    setSelectedGizmoProperty(field.key(), values.get("value"));
                });
    }
    public RingSpawn selectedRingSpawn() {return selectedRingSpawn;}
    public void editSelectedSpawnProperties(int subtype,int flags,boolean respawn) {
        finishPendingEdits();
        if(selectedObjectSpawn==null) {setStatus(UiMessages.text("editor.level_editor_controller.pick_an_object_before_changing_its_properties_1tnztd6"));return;}
        ObjectSpawn changed=requireSpawnFactory().withProperties(selectedObjectSpawn,subtype,flags,respawn);
        executeCommand(new MoveObjectSpawnCommand(requireLevel(),selectedObjectSpawn,changed));
        selectedObjectSpawn=changed;setStatus(UiMessages.text("editor.level_editor_controller.updated_selected_object_brush_preserved_5idf0u"));
    }
    public void adjustSelectedSpawnSubtype(int delta) {
        if(selectedObjectSpawn==null) {setStatus(UiMessages.text("editor.level_editor_controller.pick_an_object_before_changing_its_subtype_15cts4j"));return;}
        editSelectedSpawnProperties((selectedObjectSpawn.subtype()+delta)&255,selectedObjectSpawn.renderFlags(),selectedObjectSpawn.respawnTracked());
    }
    public void toggleSelectedSpawnFlag(int mask) {
        if(selectedObjectSpawn==null) {setStatus(UiMessages.text("editor.level_editor_controller.pick_an_object_before_changing_flags_gp9joo"));return;}
        editSelectedSpawnProperties(selectedObjectSpawn.subtype(),selectedObjectSpawn.renderFlags()^mask,selectedObjectSpawn.respawnTracked());
    }
    public void toggleSelectedSpawnRespawn() {
        if(selectedObjectSpawn==null) {setStatus(UiMessages.text("editor.level_editor_controller.pick_an_object_before_changing_respawn_tracking_1henm3y"));return;}
        editSelectedSpawnProperties(selectedObjectSpawn.subtype(),selectedObjectSpawn.renderFlags(),!selectedObjectSpawn.respawnTracked());
    }
    public String brushSummary() {
        if(spawnEditMode==EditorSpawnEditMode.RINGS)return UiMessages.text("editor.level_editor_controller.rings_1b0ieb");
        if(spawnEditMode==EditorSpawnEditMode.OBJECTS&&objectPalette!=null)
            return UiMessages.text("editor.controller.object_brush", Map.of("subtype", "%02X".formatted(objectPalette.selectedSubtype()), "flags", objectPalette.renderFlags(), "respawn", objectPalette.respawnTracked()?"1":"0", "name", objectPalette.selectedLabel()));
        return (chunkBrushRaw == null ? (selectedPatternRaw == null ? UiMessages.text("editor.level_editor_controller.terrain_brush_b_c_p_1azpq22", Map.of("value0", String.valueOf(blockBrushIndex), "value1", String.valueOf(("-")), "value2", String.valueOf(("-")))) : UiMessages.text("editor.level_editor_controller.terrain_brush_b_c_p_1azpq22", Map.of("value0", String.valueOf(blockBrushIndex), "value1", String.valueOf(("-")), "value2", String.valueOf(("%04X".formatted(selectedPatternRaw)))))) : (selectedPatternRaw == null ? UiMessages.text("editor.level_editor_controller.terrain_brush_b_c_p_1azpq22", Map.of("value0", String.valueOf(blockBrushIndex), "value1", String.valueOf((chunkBrushRaw & 1023)), "value2", String.valueOf(("-")))) : UiMessages.text("editor.level_editor_controller.terrain_brush_b_c_p_1azpq22", Map.of("value0", String.valueOf(blockBrushIndex), "value1", String.valueOf((chunkBrushRaw & 1023)), "value2", String.valueOf(("%04X".formatted(selectedPatternRaw)))))));
    }
    public String selectionSummary() {
        if(selectedObjectSpawn!=null)return UiMessages.text("editor.level_editor_controller.selected_1cmmhfw", Map.of("value0", String.valueOf(selectedObjectSpawn.layoutIndex()), "value1", String.valueOf(selectedObjectSpawn.x()), "value2", String.valueOf(selectedObjectSpawn.y()), "value3", String.valueOf(" :%02X F%d R%s".formatted(selectedObjectSpawn.subtype(), selectedObjectSpawn.renderFlags(), selectedObjectSpawn.respawnTracked() ? "1" : "0"))));
        if(selectedRingSpawn!=null)return UiMessages.text("editor.level_editor_controller.ring_1pjub4x", Map.of("value0", String.valueOf(selectedRingSpawn.placementId()), "value1", String.valueOf(selectedRingSpawn.x()), "value2", String.valueOf(selectedRingSpawn.y())));
        if (spawnEditMode == EditorSpawnEditMode.RINGS) return UiMessages.text("editor.level_editor_controller.click_to_place_a_ring_right_click_to_select_one_on7ymv");
        if (spawnEditMode == EditorSpawnEditMode.OBJECTS) return UiMessages.text("editor.level_editor_controller.right_click_a_placement_to_inspect_it_1ibvzzp");
        if (selection.selectedBlock() == null) return UiMessages.text("editor.level_editor_controller.choose_a_block_from_the_library_l9jw2l");
        return (selection.selectedChunk() == null ? String.valueOf((UiMessages.text("editor.level_editor_controller.block_1928zw8", Map.of("value0", String.valueOf(selection.selectedBlock()), "value1", String.valueOf(("")))))) : String.valueOf((UiMessages.text("editor.level_editor_controller.block_chunk_1w7yvxs", Map.of("value0", String.valueOf(selection.selectedBlock()), "value1", String.valueOf(selection.selectedChunk()))))));
    }

    private EditorSpawnFactory requireSpawnFactory() {
        if (spawnFactory == null) throw new IllegalStateException(UiMessages.text("editor.level_editor_controller.spawn_editing_is_not_configured_1u6wpck"));
        return spawnFactory;
    }

    private void requireSpawnMode(EditorSpawnEditMode expected) {
        if (spawnEditMode != expected) throw new IllegalStateException(UiMessages.text("editor.level_editor_controller.editor_is_not_in_mode_wwwwl", Map.of("value0", String.valueOf(expected))));
    }

    private com.openggf.editor.selection.EditorRegionOperations regionOperations() {
        return new com.openggf.editor.selection.EditorRegionOperations(requireLevel(), requireSpawnFactory());
    }
    private int cellX() { return worldCursor.x() / requireLevel().getBlockPixelSize(); }
    private int cellY() { return worldCursor.y() / requireLevel().getBlockPixelSize(); }
    public com.openggf.editor.selection.CellRectangle regionSelection() { return regionSelection; }
    public com.openggf.level.Level clipboardLibrary() {
        if (clipboard == null) return null;
        var library = clipboard.library();
        // Palette ownership is level-wide; a terrain clipboard shares current colors.
        for (int line = 0; line < level.getPaletteCount(); line++) for (int color = 0; color < 16; color++) {
            var rgb = level.getPalette(line).getColor(color);
            library.getPalette(line).setColor(color, new com.openggf.level.Palette.Color(rgb.r, rgb.g, rgb.b));
        }
        return library;
    }
    public boolean isSelectingRegion() { return selectingRegion; }
    public boolean isPastePreviewActive() { return pastePreview; }
    public void beginRegionAtCursor() {
        finishPendingEdits();
        if (depth != EditorHierarchyDepth.WORLD) { setStatus(UiMessages.text("editor.level_editor_controller.select_regions_in_world_view_1cfqyvd")); return; }
        pastePreview = false; selectingRegion = true; regionAnchorX = cellX(); regionAnchorY = cellY();
        extendRegionToCursor(); setStatus(UiMessages.text("editor.level_editor_controller.move_to_the_opposite_corner_then_finish_selection_5otalh"));
    }
    public void extendRegionToCursor() {
        if (selectingRegion) regionSelection = com.openggf.editor.selection.CellRectangle.between(regionAnchorX, regionAnchorY, cellX(), cellY());
    }
    public void finishRegionSelection() {
        extendRegionToCursor(); selectingRegion = false;
        if (regionSelection != null) setStatus(UiMessages.text("editor.level_editor_controller.region_x_cells_selected_o0q3pr", Map.of("value0", String.valueOf(regionSelection.width()), "value1", String.valueOf(regionSelection.height()))));
    }
    public void toggleRegionCorner() { if (selectingRegion) finishRegionSelection(); else beginRegionAtCursor(); }
    public void clearRegionSelection() { finishPendingEdits(); selectingRegion = false; regionSelection = null; pastePreview = false; }
    private com.openggf.editor.selection.CellRectangle requireRegion() {
        finishRegionSelection();
        if (regionSelection == null) throw new IllegalStateException(UiMessages.text("editor.level_editor_controller.select_two_region_corners_first_f6_5nied3"));
        return regionSelection;
    }
    private void retainClipboard(com.openggf.editor.selection.EditorRegionOperations.Clipboard copied) {
        clipboard = copied;
        clipboardBlocks.clear(); clipboardChunks.clear(); clipboardBlocks.addAll(copied.blocks());
        for (int index : clipboardBlocks) {
            Block block = requireLevel().getBlock(index);
            for (int y = 0; y < block.getGridSide(); y++) for (int x = 0; x < block.getGridSide(); x++)
                clipboardChunks.add(block.getChunkDesc(x, y).getChunkIndex());
        }
    }
    public void copyRegion() {
        finishPendingEdits(); retainClipboard(regionOperations().copy(activeLayer, requireRegion()));
        setStatus(UiMessages.text("editor.level_editor_controller.region_copied_paste_opens_a_movable_preview_1xtc4f2"));
    }
    public void cutRegion() {
        finishPendingEdits(); var cut = regionOperations().cut(activeLayer, requireRegion());
        executeCommand(cut.command()); retainClipboard(cut.clipboard()); reconcileSpawnSelection(); setStatus(UiMessages.text("editor.level_editor_controller.region_cut_u5h2"));
    }
    public void deleteRegion() {
        executeCommand(regionOperations().delete(activeLayer, requireRegion())); reconcileSpawnSelection(); setStatus(UiMessages.text("editor.level_editor_controller.region_deleted_15uiu4t"));
    }
    public void beginPastePreview() {
        finishPendingEdits();
        if (clipboard == null) throw new IllegalStateException(UiMessages.text("editor.level_editor_controller.copy_or_cut_a_region_first_i3xtw3"));
        if (depth != EditorHierarchyDepth.WORLD) throw new IllegalStateException(UiMessages.text("editor.level_editor_controller.paste_regions_in_world_view_v98rvk"));
        selectingRegion = false; pastePreview = true; setStatus(UiMessages.text("editor.level_editor_controller.paste_preview_move_enter_south_to_place_escape_east_can_hb88zi"));
    }
    /** Invalid previews are visibly rejected and never mutate the level. */
    public com.openggf.editor.selection.EditorRegionOperations.Preview pastePreview() {
        return pastePreview ? regionOperations().preview(clipboard, activeLayer, cellX(), cellY()) : null;
    }
    public void commitPastePreview() {
        if (!pastePreview) return;
        executeCommand(regionOperations().paste(clipboard, activeLayer, cellX(), cellY()));
        pastePreview = false; setStatus(UiMessages.text("editor.level_editor_controller.region_pasted_1noa7y5"));
    }
    public void cancelPastePreview() { pastePreview = false; setStatus(UiMessages.text("editor.level_editor_controller.paste_cancelled_1chrv8k")); }
    public void fillRegion() {
        if (blockBrushIndex == null) throw new IllegalStateException(UiMessages.text("editor.level_editor_controller.choose_a_block_brush_first_tgr2kv"));
        executeCommand(regionOperations().rectangleFill(activeLayer, requireRegion(), blockBrushIndex)); setStatus(UiMessages.text("editor.level_editor_controller.region_filled_1j23pwu"));
    }
    public void floodFillAtCursor() {
        if (blockBrushIndex == null) throw new IllegalStateException(UiMessages.text("editor.level_editor_controller.choose_a_block_brush_first_tgr2kv"));
        executeCommand(regionOperations().floodFill(activeLayer, cellX(), cellY(), blockBrushIndex)); setStatus(UiMessages.text("editor.level_editor_controller.connected_cells_filled_niggdk"));
    }

    public void placeBlock(int layer, int x, int y, int blockIndex) {
        finishPendingEdits();
        MutableLevel attachedLevel = requireLevel();
        if(blockIndex<0||blockIndex>=Math.min(256,attachedLevel.getBlockCount()))
            throw new IllegalArgumentException(UiMessages.text("editor.level_editor_controller.block_is_outside_the_map_s_8_bit_library_10duyjb"));
        int before = Byte.toUnsignedInt(attachedLevel.getMap().getValue(layer, x, y));
        if (before == blockIndex) {setStatus(UiMessages.text("editor.level_editor_controller.cell_already_uses_this_block_1ls1kah"));return;}
        history.execute(new PlaceBlockCommand(attachedLevel, layer, x, y, before, blockIndex));
    }

    public void executeCommand(EditorCommand command) {
        finishPendingEdits();
        history.execute(command);
        refreshSelectionFromActiveTarget();
    }

    public MutableLevel currentLevel() {
        return level;
    }

    public EditorSaveManager.ApplyResult persistenceStatus() {
        return persistenceStatus;
    }

    public void setPersistenceStatus(EditorSaveManager.ApplyResult persistenceStatus) {
        this.persistenceStatus = Objects.requireNonNull(persistenceStatus, "persistenceStatus");
    }

    public FullLevelExporter.ExportResult exportLevel(FullLevelExporter exporter,
                                                       FullLevelExporter.ExportRequest request)
            throws java.io.IOException {
        finishPendingEdits();
        if (level == null) throw new IllegalStateException(UiMessages.text("editor.level_editor_controller.no_mutable_level_is_attached_pxzgw5"));
        FullLevelExporter.ExportResult result = exporter.export(level, request);
        lastExportDirectory = result.directory();
        return result;
    }

    public java.nio.file.Path lastExportDirectory() { return lastExportDirectory; }

    public boolean isCollisionOverlayEnabled() {
        return collisionOverlayEnabled;
    }

    public void toggleCollisionOverlay() {
        collisionOverlayEnabled = !collisionOverlayEnabled;
    }

    public EditorCollisionPath collisionPath() {
        return collisionPath;
    }

    public void toggleCollisionPath() {
        collisionPath = collisionPath.other();
    }

    public void cycleSelectedCellCollisionMode() {
        finishPendingEdits();
        if (depth != EditorHierarchyDepth.BLOCK) { setStatus(UiMessages.text("editor.level_editor_controller.enter_a_block_to_edit_cell_collision_1hhprhb")); return; }
        int shift=collisionPath==EditorCollisionPath.PRIMARY?12:14;
        editLocalComposition(raw -> (raw & ~(3<<shift)) | (((((raw>>>shift)&3)+1)&3)<<shift),null);
    }

    public void setSelectedChunkSolidTileIndex(int newIndex) {
        finishPendingEdits();
        if(level==null||!isCollisionIndexEditingDepth()) { setStatus(UiMessages.text("editor.level_editor_controller.enter_a_block_or_chunk_to_edit_collision_shape_n0818"));return; }
        if(newIndex<0||newIndex>=level.getSolidTileCount())throw new IllegalArgumentException(UiMessages.text("editor.level_editor_controller.collision_shape_is_outside_the_library_b73r9k"));
        int stateIndex=Chunk.PATTERNS_PER_CHUNK+(collisionPath==EditorCollisionPath.PRIMARY?0:1);
        editLocalComposition(null,state -> state[stateIndex]=newIndex);
    }

    /** Changes only the world placement being edited, preserving shared library definitions. */
    private void editLocalComposition(java.util.function.IntUnaryOperator descriptorEdit,
                                      java.util.function.Consumer<int[]> chunkEdit) {
        MutableLevel attached = requireLevel();
        WorldMapPosition position = resolveWorldMapPosition(attached);
        if (position == null || depth == EditorHierarchyDepth.WORLD) {
            setStatus(UiMessages.text("editor.level_editor_controller.no_composition_target_selected_1gvo8id"));
            return;
        }
        int source = Byte.toUnsignedInt(attached.getMap().getValue(activeLayer, position.mapX(), position.mapY()));
        if (!Objects.equals(selection.selectedBlock(), source)) {
            setStatus(UiMessages.text("editor.level_editor_controller.pick_the_block_at_the_world_cursor_before_editing_i9wql4"));
            return;
        }
        Block sourceBlock = attached.getBlock(source);
        int[] blockState = sourceBlock.saveState().clone();
        int cell = selectedBlockCellY * sourceBlock.getGridSide() + selectedBlockCellX;
        if (cell < 0 || cell >= blockState.length) {
            setStatus(UiMessages.text("editor.level_editor_controller.no_block_cell_selected_17k1on"));
            return;
        }
        if (descriptorEdit != null) blockState[cell] = descriptorEdit.applyAsInt(blockState[cell]);
        int sourceChunk = blockState[cell] & CHUNK_INDEX_MASK;
        int[] chunkState = null;
        if (chunkEdit != null) {
            if (depth == EditorHierarchyDepth.CHUNK && !Objects.equals(selection.selectedChunk(), sourceChunk)) {
                setStatus(UiMessages.text("editor.level_editor_controller.pick_the_chunk_at_the_world_target_before_editing_1f4v4t9"));
                return;
            }
            if (!isValidChunkIndex(attached, sourceChunk)) {
                setStatus(UiMessages.text("editor.level_editor_controller.cell_references_a_missing_chunk_68bjwa"));
                return;
            }
            chunkState = attached.getChunk(sourceChunk).saveState().clone();
            chunkEdit.accept(chunkState);
        }
        if (java.util.Arrays.equals(sourceBlock.saveState(), blockState)
                && (chunkState == null || java.util.Arrays.equals(attached.getChunk(sourceChunk).saveState(), chunkState))) {
            setStatus(UiMessages.text("editor.level_editor_controller.cell_already_matches_no_editing_space_consumed_f91gqf"));
            return;
        }
        int blockTarget = ownedBlocks.contains(source) && countBlockPlacements(source) == 1
                ? source : findUnreferencedBlockSlot(attached, source);
        if (blockTarget < 0) {
            setStatus(UiMessages.text("editor.level_editor_controller.no_free_block_slots_create_a_project_with_reserved_edit_t1ils8"));
            return;
        }
        int chunkTarget = -1;
        if (chunkState != null) {
            chunkTarget = blockTarget == source && ownedChunks.contains(sourceChunk) && countChunkReferences(sourceChunk) == 1
                    ? sourceChunk : findUnreferencedChunkSlot(attached, sourceChunk);
            if (chunkTarget < 0) {
                setStatus(UiMessages.text("editor.level_editor_controller.no_free_chunk_slots_create_a_project_with_reserved_edit_18jrdt4"));
                return;
            }
            blockState[cell] = replaceChunkIndex(blockState[cell], chunkTarget);
        }
        executeCommand(new com.openggf.editor.commands.LocalCompositionCommand(attached, activeLayer,
                position.mapX(), position.mapY(), blockTarget, blockState, chunkTarget, chunkState));
        ownedBlocks.add(blockTarget);
        if (chunkTarget >= 0) ownedChunks.add(chunkTarget);
        setStatus(UiMessages.text("editor.level_editor_controller.edited_this_placement_other_placements_preserved_7rx1at"));
    }
    private int countBlockPlacements(int index) {
        int count=clipboardBlocks.contains(index)?1:0;var map=level.getMap();
        for(int layer=0;layer<map.getLayerCount();layer++)for(int y=0;y<map.getHeight();y++)for(int x=0;x<map.getWidth();x++)
            if(Byte.toUnsignedInt(map.getValue(layer,x,y))==index)count++;
        return count;
    }
    private int countChunkReferences(int index) {
        int count=clipboardChunks.contains(index)?1:0;
        for(int b=0;b<level.getBlockCount();b++) {
            Block block=level.getBlock(b);
            for(int y=0;y<block.getGridSide();y++)for(int x=0;x<block.getGridSide();x++)
                if(block.getChunkDesc(x,y).getChunkIndex()==index)count++;
        }
        return count;
    }

    public void adjustSelectedChunkSolidTileIndex(int delta) {
        finishPendingEdits();
        MutableLevel attachedLevel = level;
        if (attachedLevel == null || !isCollisionIndexEditingDepth()) {
            return;
        }
        // The chunk brush can differ from the cell being edited in the block canvas.
        Chunk chunk = selectedBlockCellPreview();
        if (chunk == null) return;
        int current = collisionPath == EditorCollisionPath.PRIMARY
                ? chunk.getSolidTileIndex() : chunk.getSolidTileAltIndex();
        int maxIndex = Math.max(0, attachedLevel.getSolidTileCount() - 1);
        int changed=clamp(current+delta,0,maxIndex);
        if(current==changed)return;
        setSelectedChunkSolidTileIndex(changed);
    }

    private boolean isCollisionIndexEditingDepth() {
        return depth == EditorHierarchyDepth.BLOCK || depth == EditorHierarchyDepth.CHUNK;
    }

    public void undo() {
        finishPendingEdits();
        if (history.undo()) {
            refreshSelectionFromActiveTarget();
            reconcileSpawnSelection();
        }
    }

    public void redo() {
        finishPendingEdits();
        if (history.redo()) {
            refreshSelectionFromActiveTarget();
            reconcileSpawnSelection();
        }
    }

    private void reconcileSpawnSelection() {
        if (level == null) {
            selectedObjectSpawn = null;
            selectedRingSpawn = null;
            selectedRingBackingObject = null;
            return;
        }
        if (selectedObjectSpawn != null) {
            int placementId = selectedObjectSpawn.layoutIndex();
            selectedObjectSpawn = level.getObjects().stream()
                    .filter(spawn -> spawn.layoutIndex() == placementId)
                    .findFirst().orElse(null);
        }
        if (selectedRingSpawn != null) {
            int placementId = selectedRingSpawn.placementId();
            selectedRingSpawn = level.getRings().stream()
                    .filter(spawn -> spawn.placementId() == placementId)
                    .findFirst().orElse(null);
        }
        selectedRingBackingObject = selectedRingSpawn == null
                ? null : level.ringBackingObject(selectedRingSpawn);
    }

    public int activeLayer() {
        return activeLayer;
    }

    public void toggleActiveLayer() {
        finishPendingEdits();
        MutableLevel attachedLevel = requireLevel();
        int layerCount = attachedLevel.getMap().getLayerCount();
        if (layerCount < 2) {
            activeLayer = 0;
            return;
        }
        activeLayer = activeLayer == 0 ? 1 : 0;
        refreshSelectionFromActiveTarget();
    }

    public boolean hasUndoHistory() {
        return history.hasUndoEntries();
    }

    public void selectBlock(int blockIndex) {
        requireNonNegative(blockIndex, "blockIndex");
        selection = new EditorSelectionState(blockIndex, null);
        blockBrushIndex = blockIndex;
        selectedChunkDescriptorRaw = null;
        if (depth == EditorHierarchyDepth.CHUNK) {
            depth = EditorHierarchyDepth.BLOCK;
            focusRegion = EditorFocusRegion.BLOCK_PANE;
        }
    }

    public void selectChunk(int chunkIndex) {
        requireNonNegative(chunkIndex, "chunkIndex");
        if (selection.selectedBlock() == null) {
            throw new IllegalStateException(UiMessages.text("editor.level_editor_controller.cannot_select_a_chunk_without_a_selected_block_11d3jo8"));
        }
        selection = new EditorSelectionState(selection.selectedBlock(), chunkIndex);
        selectedChunkDescriptorRaw = unflaggedChunkDescriptorRaw(chunkIndex);
        chunkBrushRaw = selectedChunkDescriptorRaw;
    }

    public Block selectedBlockPreview() {
        Integer selectedBlock = selection.selectedBlock();
        if (selectedBlock == null) {
            return null;
        }
        MutableLevel attachedLevel = requireLevel();
        if (selectedBlock < 0 || selectedBlock >= attachedLevel.getBlockCount()) {
            return null;
        }
        return attachedLevel.getBlock(selectedBlock);
    }

    public Chunk selectedBlockCellPreview() {
        Block block = selectedBlockPreview();
        if (block == null) {
            return null;
        }
        if (selectedBlockCellX < 0 || selectedBlockCellX >= block.getGridSide()
                || selectedBlockCellY < 0 || selectedBlockCellY >= block.getGridSide()) {
            return null;
        }
        int chunkIndex = block.getChunkDesc(selectedBlockCellX, selectedBlockCellY).getChunkIndex();
        MutableLevel attachedLevel = requireLevel();
        if (chunkIndex < 0 || chunkIndex >= attachedLevel.getChunkCount()) {
            return null;
        }
        return attachedLevel.getChunk(chunkIndex);
    }

    public ChunkDesc selectedBlockChunkDescriptor(int x,int y) {
        Block block=selectedBlockPreview();
        return block==null?new ChunkDesc():new ChunkDesc(block.getChunkDesc(x,y).get());
    }

    public Chunk selectedBlockChunkPreview(int blockCellX, int blockCellY) {
        Block block = selectedBlockPreview();
        if (block == null) {
            return null;
        }
        if (blockCellX < 0 || blockCellY < 0
                || blockCellX >= block.getGridSide()
                || blockCellY >= block.getGridSide()) {
            return null;
        }
        int chunkIndex = block.getChunkDesc(blockCellX, blockCellY).getChunkIndex();
        MutableLevel attachedLevel = requireLevel();
        if (chunkIndex < 0 || chunkIndex >= attachedLevel.getChunkCount()) {
            return null;
        }
        return attachedLevel.getChunk(chunkIndex);
    }

    public Chunk selectedChunkPreview() {
        Integer selectedChunk = selection.selectedChunk();
        if (selectedChunk == null) {
            return null;
        }
        MutableLevel attachedLevel = requireLevel();
        if (selectedChunk < 0 || selectedChunk >= attachedLevel.getChunkCount()) {
            return null;
        }
        return attachedLevel.getChunk(selectedChunk);
    }

    public void descend() {
        finishPendingEdits();
        if (isSpawnEditing()) {setStatus(UiMessages.text("editor.level_editor_controller.switch_to_terrain_mode_to_edit_composition_mvxj6o"));return;}
        if (depth == EditorHierarchyDepth.WORLD && selection.selectedBlock() != null) {
            if(level!=null) {
                WorldMapPosition p=resolveWorldMapPosition(level);
                int target=Byte.toUnsignedInt(level.getMap().getValue(activeLayer,p.mapX(),p.mapY()));
                selection=new EditorSelectionState(target,selection.selectedChunk());
            }
            depth = EditorHierarchyDepth.BLOCK;
            focusRegion = EditorFocusRegion.BLOCK_PANE;
        } else if (depth == EditorHierarchyDepth.BLOCK && selection.selectedChunk() != null) {
            if(level!=null) {
                ChunkDesc target=selectedBlockChunkDescriptor(selectedBlockCellX,selectedBlockCellY);
                selection=new EditorSelectionState(selection.selectedBlock(),target.getChunkIndex());
                selectedChunkDescriptorRaw=target.get();
            }
            depth = EditorHierarchyDepth.CHUNK;
            focusRegion = EditorFocusRegion.CHUNK_PANE;
        }
    }

    public void ascend() {
        finishPendingEdits();
        if (depth == EditorHierarchyDepth.CHUNK) {
            depth = EditorHierarchyDepth.BLOCK;
            focusRegion = EditorFocusRegion.BLOCK_PANE;
        } else if (depth == EditorHierarchyDepth.BLOCK) {
            depth = EditorHierarchyDepth.WORLD;
            focusRegion = EditorFocusRegion.WORLD_CANVAS;
            selection = new EditorSelectionState(blockBrushIndex,null);
        }
    }

    public EditorHierarchyDepth depth() {
        return depth;
    }

    public EditorFocusRegion focusRegion() {
        return focusRegion;
    }

    public void applyPrimaryAction() {
        if (isSpawnEditing()) {
            placeSpawnAtCursor();
            return;
        }
        if (depth == EditorHierarchyDepth.BLOCK) {
            applyBlockPrimaryAction();
            return;
        }
        if (depth == EditorHierarchyDepth.CHUNK) {
            applyChunkPrimaryAction();
            return;
        }
        applyWorldPrimaryAction();
    }

    private void applyWorldPrimaryAction() {
        if (focusRegion != EditorFocusRegion.WORLD_CANVAS) {
            return;
        }
        Integer selectedBlock = blockBrushIndex;
        if (selectedBlock == null) {
            setStatus(UiMessages.text("editor.level_editor_controller.choose_a_block_from_the_library_first_12zdjb5"));
            return;
        }
        MutableLevel attachedLevel = level;
        if (attachedLevel == null) {
            return;
        }
        WorldMapPosition mapPosition = resolveWorldMapPosition(attachedLevel);
        if (mapPosition == null) {
            return;
        }
        placeBlock(activeLayer, mapPosition.mapX(), mapPosition.mapY(), selectedBlock);
    }

    private void applyBlockPrimaryAction() {
        if(focusRegion!=EditorFocusRegion.BLOCK_PANE) {setStatus(UiMessages.text("editor.level_editor_controller.focus_the_block_canvas_to_apply_a_chunk_1y8wwj9"));return;}
        if(chunkBrushRaw==null) {setStatus(UiMessages.text("editor.level_editor_controller.choose_a_chunk_from_the_library_first_mj8e1t"));return;}
        int replacement=chunkBrushRaw;
        editLocalComposition(raw -> replacement,null);
    }

    private void applyChunkPrimaryAction() {
        if(focusRegion!=EditorFocusRegion.CHUNK_PANE) {setStatus(UiMessages.text("editor.level_editor_controller.focus_the_chunk_canvas_to_apply_a_pattern_ugxwag"));return;}
        if(selectedPatternRaw==null) {setStatus(UiMessages.text("editor.level_editor_controller.choose_a_pattern_from_the_library_first_ts0o0u"));return;}
        int replacement=selectedPatternRaw;
        int cell=selectedChunkCellY*2+selectedChunkCellX;
        editLocalComposition(null,state -> state[cell]=replacement);
    }

    public void eraseSelection() {
        finishPendingEdits();
        if (isSpawnEditing()) {deleteSpawnAtCursor();return;}
        MutableLevel attached = requireLevel();
        // Imported libraries may have art at index zero. Select actual transparent
        // data and no collision instead of assuming a particular numeric slot.
        boolean[] transparentPatterns = new boolean[Math.min(0x800, attached.getPatternCount())];
        for (int i = 0; i < transparentPatterns.length; i++) {
            transparentPatterns[i] = true;
            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++)
                if (attached.getPattern(i).getPixel(x, y) != 0) transparentPatterns[i] = false;
        }
        boolean[] transparentChunks = new boolean[Math.min(1024, attached.getChunkCount())];
        for (int i = 0; i < transparentChunks.length; i++) {
            transparentChunks[i] = true;
            for (int y = 0; y < 2; y++) for (int x = 0; x < 2; x++) {
                int pattern = attached.getChunk(i).getPatternDesc(x, y).getPatternIndex();
                if (pattern >= transparentPatterns.length || !transparentPatterns[pattern]) transparentChunks[i] = false;
            }
        }
        int empty = -1;
        if (depth == EditorHierarchyDepth.WORLD) {
            for (int i = 0; i < Math.min(256, attached.getBlockCount()) && empty < 0; i++) {
                Block block = attached.getBlock(i);
                boolean blank = true;
                for (int y = 0; y < block.getGridSide(); y++) for (int x = 0; x < block.getGridSide(); x++) {
                    ChunkDesc descriptor = block.getChunkDesc(x, y);
                    int chunk = descriptor.getChunkIndex();
                    if ((descriptor.get() & 0xF000) != 0 || chunk >= transparentChunks.length || !transparentChunks[chunk]) blank = false;
                }
                if (blank) empty = i;
            }
            if (empty < 0) {setStatus(UiMessages.text("editor.level_editor_controller.no_empty_block_in_this_library_create_a_project_with_em_awebd5"));return;}
            WorldMapPosition position = resolveWorldMapPosition(attached);
            placeBlock(activeLayer, position.mapX(), position.mapY(), empty);
        } else if (depth == EditorHierarchyDepth.BLOCK) {
            for (int i = 0; i < transparentChunks.length && empty < 0; i++)
                if (transparentChunks[i] && attached.getChunk(i).getSolidTileIndex() == 0
                        && attached.getChunk(i).getSolidTileAltIndex() == 0) empty = i;
            if (empty < 0) {setStatus(UiMessages.text("editor.level_editor_controller.no_empty_chunk_in_this_library_create_a_project_with_em_1c5y5ux"));return;}
            int replacement = empty;
            editLocalComposition(raw -> replacement, null);
        } else {
            for (int i = 0; i < transparentPatterns.length && empty < 0; i++) if (transparentPatterns[i]) empty = i;
            if (empty < 0) {setStatus(UiMessages.text("editor.level_editor_controller.no_transparent_pattern_in_this_library_create_a_project_1ffnc5d"));return;}
            int replacement = empty;
            int cell = selectedChunkCellY * 2 + selectedChunkCellX;
            editLocalComposition(null, state -> state[cell] = replacement);
        }
    }
    public void toggleDescriptorHFlip() { editDescriptorFlag(0); }
    public void toggleDescriptorVFlip() { editDescriptorFlag(1); }
    public void cyclePatternPalette() { editDescriptorFlag(2); }
    public void togglePatternPriority() { editDescriptorFlag(3); }
    private void editDescriptorFlag(int property) {
        finishPendingEdits();
        if(depth==EditorHierarchyDepth.BLOCK&&property<2)
            editLocalComposition(raw -> raw ^ (property==0?0x400:0x800),null);
        else if(depth==EditorHierarchyDepth.CHUNK) {
            int cell=selectedChunkCellY*2+selectedChunkCellX;
            editLocalComposition(null,state -> state[cell]=switch(property) {
                case 0 -> state[cell]^0x800;case 1 -> state[cell]^0x1000;
                case 2 -> (state[cell]&~0x6000)|((((state[cell]>>>13)+1)&3)<<13);
                default -> state[cell]^0x8000;
            });
        } else setStatus(UiMessages.text("editor.level_editor_controller.enter_a_chunk_for_palette_priority_or_a_block_for_flips_17058vx"));
    }

    public void performEyedrop() {
        if (isSpawnEditing()) {
            eyedropSpawnAtCursor();
            return;
        }
        if (depth == EditorHierarchyDepth.CHUNK) {
            performChunkEyedrop();
            return;
        }
        if (depth == EditorHierarchyDepth.BLOCK) {
            performBlockEyedrop();
            return;
        }
        if (focusRegion != EditorFocusRegion.WORLD_CANVAS) {
            return;
        }
        MutableLevel attachedLevel = level;
        if (attachedLevel == null) {
            return;
        }
        WorldMapPosition mapPosition = resolveWorldMapPosition(attachedLevel);
        if (mapPosition == null) {
            return;
        }
        int blockIndex = Byte.toUnsignedInt(attachedLevel.getMap().getValue(activeLayer, mapPosition.mapX(), mapPosition.mapY()));
        selectBlock(blockIndex);
    }

    private void performBlockEyedrop() {
        if (focusRegion != EditorFocusRegion.BLOCK_PANE) {
            return;
        }
        Integer selectedBlock = selection.selectedBlock();
        MutableLevel attachedLevel = level;
        if (selectedBlock == null || attachedLevel == null || !isValidBlockIndex(attachedLevel, selectedBlock)) {
            return;
        }
        Block block = attachedLevel.getBlock(selectedBlock);
        if (!isBlockCellInBounds(block, selectedBlockCellX, selectedBlockCellY)) {
            return;
        }
        ChunkDesc chunkDesc = block.getChunkDesc(selectedBlockCellX, selectedBlockCellY);
        int chunkIndex = chunkDesc.getChunkIndex();
        if (isValidChunkIndex(attachedLevel, chunkIndex)) {
            selection = new EditorSelectionState(selectedBlock, chunkIndex);
            selectedChunkDescriptorRaw = chunkDesc.get();
            chunkBrushRaw = chunkDesc.get();
        }
    }

    private void performChunkEyedrop() {
        if (focusRegion != EditorFocusRegion.CHUNK_PANE) {
            return;
        }
        Integer selectedChunk = selection.selectedChunk();
        MutableLevel attachedLevel = level;
        if (selectedChunk == null || attachedLevel == null || !isValidChunkIndex(attachedLevel, selectedChunk)) {
            return;
        }
        Chunk chunk = attachedLevel.getChunk(selectedChunk);
        selectedPatternRaw = chunk.getPatternDesc(selectedChunkCellX, selectedChunkCellY).get();
    }

    public void setWorldCursor(EditorCursorState cursor) {
        Objects.requireNonNull(cursor, "cursor");
        this.worldCursor = clampWorldCursor(cursor.x(), cursor.y());
        if (selectingRegion) extendRegionToCursor();
        cameraNavigationRequested = true;
    }

    public EditorCursorState worldCursor() {
        return worldCursor;
    }

    public int blockGridSide() {
        return blockGridSide;
    }

    public int chunkGridSide() {
        return 2;
    }

    public void moveWorldCursor(int dx, int dy) {
        worldCursor = clampWorldCursor(worldCursor.x() + dx, worldCursor.y() + dy);
        if (selectingRegion) extendRegionToCursor();
        cameraNavigationRequested = true;
    }

    /** Discrete terrain/spawn navigation shared by keyboard arrows and the D-pad. */
    public void stepWorldCursor(int dx, int dy) {
        if (isSpawnEditing() || level == null) {
            setWorldCursor(new EditorCursorState(worldCursor.x() + dx * 8, worldCursor.y() + dy * 8));
        } else {
            int size = level.getBlockPixelSize();
            setWorldCursor(new EditorCursorState(
                    (Math.floorDiv(worldCursor.x(), size) + dx) * size + size / 2,
                    (Math.floorDiv(worldCursor.y(), size) + dy) * size + size / 2));
        }
    }

    public void moveActiveSelection(int dx, int dy) {
        int gridSide = activeGridSide();
        if (depth == EditorHierarchyDepth.WORLD) {
            moveWorldCursor(dx, dy);
            return;
        }
        if (depth == EditorHierarchyDepth.BLOCK) {
            selectedBlockCellX = clamp(selectedBlockCellX + dx, 0, gridSide - 1);
            selectedBlockCellY = clamp(selectedBlockCellY + dy, 0, gridSide - 1);
            return;
        }
        selectedChunkCellX = clamp(selectedChunkCellX + dx, 0, gridSide - 1);
        selectedChunkCellY = clamp(selectedChunkCellY + dy, 0, gridSide - 1);
    }

    public int selectedBlockCellX() {
        return selectedBlockCellX;
    }

    public int selectedBlockCellY() {
        return selectedBlockCellY;
    }

    public int selectedChunkCellX() {
        return selectedChunkCellX;
    }

    public int selectedChunkCellY() {
        return selectedChunkCellY;
    }

    public EditorSelectionState selection() {
        return selection;
    }

    public void selectPattern(int index) {
        if(level==null||index<0||index>=level.getPatternCount()||index>0x7FF)
            throw new IllegalArgumentException(UiMessages.text("editor.level_editor_controller.pattern_is_outside_the_loaded_library_gmmh00"));
        selectedPatternRaw=(selectedPatternRaw==null?0:selectedPatternRaw&~0x7FF)|index;
    }
    public Integer selectedPatternRaw() { return selectedPatternRaw; }

    /** Chunk index of the block-composition brush, or null before one is chosen. */
    public Integer chunkBrushIndex() {
        return chunkBrushRaw == null ? null : chunkBrushRaw & CHUNK_INDEX_MASK;
    }

    public Integer selectedBlockIndex() {
        return blockBrushIndex;
    }

    public String breadcrumb() {
        if (depth == EditorHierarchyDepth.WORLD) {
            return UiMessages.text("editor.level_editor_controller.world_1dvecy");
        }
        if (depth == EditorHierarchyDepth.BLOCK) {
            return UiMessages.text("editor.level_editor_controller.world_block_du11ja", Map.of("value0", String.valueOf(selection.selectedBlock())));
        }
        return UiMessages.text("editor.level_editor_controller.world_block_chunk_1kkmqlt", Map.of("value0", String.valueOf(selection.selectedBlock()), "value1", String.valueOf(selection.selectedChunk())));
    }

    private static void requireNonNegative(int index, String name) {
        if (index < 0) {
            throw new IllegalArgumentException(UiMessages.text("editor.level_editor_controller.must_be_0_1a0p6he", Map.of("value0", String.valueOf(name))));
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private EditorCursorState clampWorldCursor(int x, int y) {
        if (level == null) {
            return new EditorCursorState(x, y);
        }
        if (canvasNavigation.getAsBoolean()) {
            int maxCanvasX = level.getMap().getWidth() * level.getBlockPixelSize() - 1;
            int maxCanvasY = level.getMap().getHeight() * level.getBlockPixelSize() - 1;
            return new EditorCursorState(clamp(x, 0, maxCanvasX), clamp(y, 0, maxCanvasY));
        }
        int minX = level.getMinX();
        int maxX = level.getMaxX();
        int minY = level.getMinY();
        int maxY = level.getMaxY();
        return new EditorCursorState(clamp(x, minX, maxX), clamp(y, minY, maxY));
    }

    private WorldMapPosition resolveWorldMapPosition(MutableLevel attachedLevel) {
        int blockPixelSize = attachedLevel.getBlockPixelSize();
        if (blockPixelSize <= 0) {
            return null;
        }
        int mapX = worldCursor.x();
        int mapY = worldCursor.y();
        int mapWidth = attachedLevel.getMap().getWidth();
        int mapHeight = attachedLevel.getMap().getHeight();
        if (mapWidth <= 0 || mapHeight <= 0) {
            return null;
        }
        mapX = clamp(mapX / blockPixelSize, 0, mapWidth - 1);
        mapY = clamp(mapY / blockPixelSize, 0, mapHeight - 1);
        return new WorldMapPosition(mapX, mapY);
    }

    private int activeGridSide() {
        if (depth == EditorHierarchyDepth.BLOCK) {
            return blockGridSide;
        }
        if (depth == EditorHierarchyDepth.CHUNK) {
            return chunkGridSide();
        }
        return 1;
    }

    private static boolean isBlockCellInBounds(Block block, int x, int y) {
        return x >= 0 && y >= 0 && x < block.getGridSide() && y < block.getGridSide();
    }

    private static boolean isValidBlockIndex(MutableLevel level, int blockIndex) {
        return blockIndex >= 0 && blockIndex < level.getBlockCount();
    }

    private static boolean isValidChunkIndex(MutableLevel level, int chunkIndex) {
        return chunkIndex >= 0 && chunkIndex < level.getChunkCount();
    }

    private static int unflaggedChunkDescriptorRaw(int chunkIndex) {
        return chunkIndex & CHUNK_INDEX_MASK;
    }

    private static int replaceChunkIndex(int descriptorRaw, int chunkIndex) {
        return (descriptorRaw & ~CHUNK_INDEX_MASK) | unflaggedChunkDescriptorRaw(chunkIndex);
    }

    private int findUnreferencedBlockSlot(MutableLevel level, int sourceBlockIndex) {
        for (int blockIndex = 0; blockIndex < Math.min(256,level.getBlockCount()); blockIndex++) {
            if (blockIndex != sourceBlockIndex && !clipboardBlocks.contains(blockIndex) && !level.isBlockReferencedInMap(blockIndex)) {
                return blockIndex;
            }
        }
        return -1;
    }

    private int findUnreferencedChunkSlot(MutableLevel level, int sourceChunkIndex) {
        for (int chunkIndex = 0; chunkIndex < Math.min(1024,level.getChunkCount()); chunkIndex++) {
            if (chunkIndex != sourceChunkIndex && !clipboardChunks.contains(chunkIndex) && !level.isChunkReferencedInBlocks(chunkIndex)) {
                return chunkIndex;
            }
        }
        return -1;
    }

    private void refreshSelectionFromActiveTarget() {
        if (depth == EditorHierarchyDepth.WORLD || level == null) {
            return;
        }
        WorldMapPosition mapPosition = resolveWorldMapPosition(level);
        if (mapPosition == null) {
            return;
        }
        int blockIndex = Byte.toUnsignedInt(level.getMap().getValue(activeLayer, mapPosition.mapX(), mapPosition.mapY()));
        if (!isValidBlockIndex(level, blockIndex)) {
            return;
        }
        Integer chunkIndex = null;
        Integer chunkDescriptorRaw = null;
        Block block = level.getBlock(blockIndex);
        if (isBlockCellInBounds(block, selectedBlockCellX, selectedBlockCellY)) {
            ChunkDesc activeChunkDesc = block.getChunkDesc(selectedBlockCellX, selectedBlockCellY);
            int activeChunkIndex = activeChunkDesc.getChunkIndex();
            if (isValidChunkIndex(level, activeChunkIndex)) {
                chunkIndex = activeChunkIndex;
                chunkDescriptorRaw = activeChunkDesc.get();

            }
        }
        selection = new EditorSelectionState(blockIndex, chunkIndex);
        selectedChunkDescriptorRaw = chunkDescriptorRaw;
    }

    private MutableLevel requireLevel() {
        if (level == null) {
            throw new IllegalStateException(UiMessages.text("editor.level_editor_controller.no_mutablelevel_is_attached_to_the_editor_controller_10qyxwk"));
        }
        return level;
    }

    private record WorldMapPosition(int mapX, int mapY) {
    }
}
