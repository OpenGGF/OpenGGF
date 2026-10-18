package com.openggf.editor;

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
    private RingSpawn selectedRingSpawn;
    private ObjectSpawn selectedRingBackingObject;
    private boolean collisionOverlayEnabled;
    private EditorCollisionPath collisionPath = EditorCollisionPath.PRIMARY;
    private EditorSaveManager.ApplyResult persistenceStatus = EditorSaveManager.ApplyResult.NONE;
    private java.nio.file.Path lastExportDirectory;
    private Runnable pendingEditsFinisher = () -> { };
    private boolean cameraNavigationRequested = true;
    private String statusText = "Ready";
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
    public void setStatus(String text) { statusText = Objects.requireNonNull(text, "text"); }
    public boolean consumeCameraNavigationRequest() {
        boolean requested = cameraNavigationRequested;
        cameraNavigationRequested = false;
        return requested;
    }
    /** Pointer hover never asks the camera to follow itself. */
    public void setPointerWorldCursor(EditorCursorState cursor) {
        worldCursor = clampWorldCursor(cursor.x(), cursor.y());
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
        cameraNavigationRequested = true;
        statusText = "Ready";
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

    public void configureSpawnEditing(ObjectRegistry registry, ObjectPlacementEncoding encoding,
                                      java.util.function.IntFunction<String> names) {
        MutableLevel attachedLevel = requireLevel();
        EditorStockObjectPalette previous=objectPalette;
        objectPalette = new EditorStockObjectPalette(registry, names);
        if(previous!=null) {
            if(previous.selectedIsKeyed()) {
                try {objectPalette.setObjectKey(previous.selectedObjectKey());}catch(IllegalArgumentException missing){setStatus("Object brush dependency unavailable");}
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
        if(mode!=EditorSpawnEditMode.TILES&&depth!=EditorHierarchyDepth.WORLD) {
            depth=EditorHierarchyDepth.WORLD;focusRegion=EditorFocusRegion.WORLD_CANVAS;
            selection=new EditorSelectionState(blockBrushIndex,null);
        }
        selectedObjectSpawn = null;
        selectedRingSpawn = null;
        selectedRingBackingObject = null;
    }

    public EditorSpawnEditMode spawnEditMode() { return spawnEditMode; }
    public boolean isSpawnEditing() { return spawnEditMode != EditorSpawnEditMode.TILES; }
    public EditorStockObjectPalette objectPalette() {
        if (objectPalette == null) throw new IllegalStateException("Spawn editing is not configured");
        return objectPalette;
    }
    /** The object palette, or null before spawn editing is configured. */
    public EditorStockObjectPalette objectPaletteOrNull() { return objectPalette; }

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
            setStatus("This object id is reserved or unsupported; choose another object");
            return;
        }
        ObjectSpawn spawn = objectPalette().selectedIsKeyed()
                ? requireSpawnFactory().createKeyedObjectSpawn(worldCursor.x(), worldCursor.y(),
                        objectPalette().selectedObjectKey(), objectPalette().selectedSubtype(), objectPalette().renderFlags(), objectPalette().respawnTracked())
                : requireSpawnFactory().createObjectSpawn(worldCursor.x(), worldCursor.y(),
                        objectPalette().selectedObjectId(), objectPalette().selectedSubtype(), objectPalette().renderFlags(), objectPalette().respawnTracked());
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
                try {objectPalette().eyedrop(selectedObjectSpawn);setStatus("Object selected; +/- subtype, H/J flip, R respawn");}
                catch(IllegalArgumentException missing) {setStatus("Selected missing object dependency: "+selectedObjectSpawn.objectKey());}
            } else setStatus("No object within 8 pixels of the cursor");
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
    public RingSpawn selectedRingSpawn() {return selectedRingSpawn;}
    public void editSelectedSpawnProperties(int subtype,int flags,boolean respawn) {
        finishPendingEdits();
        if(selectedObjectSpawn==null) {setStatus("Pick an object before changing its properties");return;}
        ObjectSpawn changed=requireSpawnFactory().withProperties(selectedObjectSpawn,subtype,flags,respawn);
        executeCommand(new MoveObjectSpawnCommand(requireLevel(),selectedObjectSpawn,changed));
        selectedObjectSpawn=changed;setStatus("Updated selected object; brush preserved");
    }
    public void adjustSelectedSpawnSubtype(int delta) {
        if(selectedObjectSpawn==null) {setStatus("Pick an object before changing its subtype");return;}
        editSelectedSpawnProperties((selectedObjectSpawn.subtype()+delta)&255,selectedObjectSpawn.renderFlags(),selectedObjectSpawn.respawnTracked());
    }
    public void toggleSelectedSpawnFlag(int mask) {
        if(selectedObjectSpawn==null) {setStatus("Pick an object before changing flags");return;}
        editSelectedSpawnProperties(selectedObjectSpawn.subtype(),selectedObjectSpawn.renderFlags()^mask,selectedObjectSpawn.respawnTracked());
    }
    public void toggleSelectedSpawnRespawn() {
        if(selectedObjectSpawn==null) {setStatus("Pick an object before changing respawn tracking");return;}
        editSelectedSpawnProperties(selectedObjectSpawn.subtype(),selectedObjectSpawn.renderFlags(),!selectedObjectSpawn.respawnTracked());
    }
    public String brushSummary() {
        if(spawnEditMode==EditorSpawnEditMode.RINGS)return "Rings";
        if(spawnEditMode==EditorSpawnEditMode.OBJECTS&&objectPalette!=null)
            return "Objects :%02X F%d R%s ".formatted(objectPalette.selectedSubtype(),objectPalette.renderFlags(),objectPalette.respawnTracked()?"1":"0")+objectPalette.selectedLabel();
        return "Terrain brush B"+blockBrushIndex+" C"+(chunkBrushRaw==null?"-":chunkBrushRaw&1023)
                +" P"+(selectedPatternRaw==null?"-":"%04X".formatted(selectedPatternRaw));
    }
    public String selectionSummary() {
        if(selectedObjectSpawn!=null)return "Selected #"+selectedObjectSpawn.layoutIndex()+" "+selectedObjectSpawn.x()+","+selectedObjectSpawn.y()
                +" :%02X F%d R%s".formatted(selectedObjectSpawn.subtype(),selectedObjectSpawn.renderFlags(),selectedObjectSpawn.respawnTracked()?"1":"0");
        if(selectedRingSpawn!=null)return "Ring #"+selectedRingSpawn.placementId()+" "+selectedRingSpawn.x()+","+selectedRingSpawn.y();
        return "Block "+selection.selectedBlock()+" Chunk "+selection.selectedChunk()+" cell "+selectedBlockCellX+","+selectedBlockCellY+" / "+selectedChunkCellX+","+selectedChunkCellY;
    }

    private EditorSpawnFactory requireSpawnFactory() {
        if (spawnFactory == null) throw new IllegalStateException("Spawn editing is not configured");
        return spawnFactory;
    }

    private void requireSpawnMode(EditorSpawnEditMode expected) {
        if (spawnEditMode != expected) throw new IllegalStateException("Editor is not in " + expected + " mode");
    }

    public void placeBlock(int layer, int x, int y, int blockIndex) {
        finishPendingEdits();
        MutableLevel attachedLevel = requireLevel();
        if(blockIndex<0||blockIndex>=Math.min(256,attachedLevel.getBlockCount()))
            throw new IllegalArgumentException("Block is outside the map's 8-bit library");
        int before = Byte.toUnsignedInt(attachedLevel.getMap().getValue(layer, x, y));
        if (before == blockIndex) {setStatus("Cell already uses this block");return;}
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
        if (level == null) throw new IllegalStateException("No mutable level is attached");
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
        if (depth != EditorHierarchyDepth.BLOCK) { setStatus("Enter a block to edit cell collision"); return; }
        int shift=collisionPath==EditorCollisionPath.PRIMARY?12:14;
        editLocalComposition(raw -> (raw & ~(3<<shift)) | (((((raw>>>shift)&3)+1)&3)<<shift),null);
    }

    public void setSelectedChunkSolidTileIndex(int newIndex) {
        finishPendingEdits();
        if(level==null||!isCollisionIndexEditingDepth()) { setStatus("Enter a block or chunk to edit collision shape");return; }
        if(newIndex<0||newIndex>=level.getSolidTileCount())throw new IllegalArgumentException("Collision shape is outside the library");
        int stateIndex=Chunk.PATTERNS_PER_CHUNK+(collisionPath==EditorCollisionPath.PRIMARY?0:1);
        editLocalComposition(null,state -> state[stateIndex]=newIndex);
    }

    /** Changes only the world placement being edited, preserving shared library definitions. */
    private void editLocalComposition(java.util.function.IntUnaryOperator descriptorEdit,
                                      java.util.function.Consumer<int[]> chunkEdit) {
        MutableLevel attached = requireLevel();
        WorldMapPosition position = resolveWorldMapPosition(attached);
        if (position == null || depth == EditorHierarchyDepth.WORLD) {
            setStatus("No composition target selected");
            return;
        }
        int source = Byte.toUnsignedInt(attached.getMap().getValue(activeLayer, position.mapX(), position.mapY()));
        if (!Objects.equals(selection.selectedBlock(), source)) {
            setStatus("Pick the block at the world cursor before editing");
            return;
        }
        Block sourceBlock = attached.getBlock(source);
        int[] blockState = sourceBlock.saveState().clone();
        int cell = selectedBlockCellY * sourceBlock.getGridSide() + selectedBlockCellX;
        if (cell < 0 || cell >= blockState.length) {
            setStatus("No block cell selected");
            return;
        }
        if (descriptorEdit != null) blockState[cell] = descriptorEdit.applyAsInt(blockState[cell]);
        int sourceChunk = blockState[cell] & CHUNK_INDEX_MASK;
        int[] chunkState = null;
        if (chunkEdit != null) {
            if (depth == EditorHierarchyDepth.CHUNK && !Objects.equals(selection.selectedChunk(), sourceChunk)) {
                setStatus("Pick the chunk at the world target before editing");
                return;
            }
            if (!isValidChunkIndex(attached, sourceChunk)) {
                setStatus("Cell references a missing chunk");
                return;
            }
            chunkState = attached.getChunk(sourceChunk).saveState().clone();
            chunkEdit.accept(chunkState);
        }
        if (java.util.Arrays.equals(sourceBlock.saveState(), blockState)
                && (chunkState == null || java.util.Arrays.equals(attached.getChunk(sourceChunk).saveState(), chunkState))) {
            setStatus("Cell already matches; no editing space consumed");
            return;
        }
        int blockTarget = ownedBlocks.contains(source) && countBlockPlacements(source) == 1
                ? source : findUnreferencedBlockSlot(attached, source);
        if (blockTarget < 0) {
            setStatus("No free block slots; create a project with reserved editing space");
            return;
        }
        int chunkTarget = -1;
        if (chunkState != null) {
            chunkTarget = blockTarget == source && ownedChunks.contains(sourceChunk) && countChunkReferences(sourceChunk) == 1
                    ? sourceChunk : findUnreferencedChunkSlot(attached, sourceChunk);
            if (chunkTarget < 0) {
                setStatus("No free chunk slots; create a project with reserved editing space");
                return;
            }
            blockState[cell] = replaceChunkIndex(blockState[cell], chunkTarget);
        }
        executeCommand(new com.openggf.editor.commands.LocalCompositionCommand(attached, activeLayer,
                position.mapX(), position.mapY(), blockTarget, blockState, chunkTarget, chunkState));
        ownedBlocks.add(blockTarget);
        if (chunkTarget >= 0) ownedChunks.add(chunkTarget);
        setStatus("Edited this placement; other placements preserved");
    }
    private int countBlockPlacements(int index) {
        int count=0;var map=level.getMap();
        for(int layer=0;layer<map.getLayerCount();layer++)for(int y=0;y<map.getHeight();y++)for(int x=0;x<map.getWidth();x++)
            if(Byte.toUnsignedInt(map.getValue(layer,x,y))==index)count++;
        return count;
    }
    private int countChunkReferences(int index) {
        int count=0;
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
            throw new IllegalStateException("Cannot select a chunk without a selected block");
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
        if (isSpawnEditing()) {setStatus("Switch to terrain mode to edit composition");return;}
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
            setStatus("Choose a block from the library first");
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
        if(focusRegion!=EditorFocusRegion.BLOCK_PANE) {setStatus("Focus the block canvas to apply a chunk");return;}
        if(chunkBrushRaw==null) {setStatus("Choose a chunk from the library first");return;}
        int replacement=chunkBrushRaw;
        editLocalComposition(raw -> replacement,null);
    }

    private void applyChunkPrimaryAction() {
        if(focusRegion!=EditorFocusRegion.CHUNK_PANE) {setStatus("Focus the chunk canvas to apply a pattern");return;}
        if(selectedPatternRaw==null) {setStatus("Choose a pattern from the library first");return;}
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
            if (empty < 0) {setStatus("No empty block in this library; create a project with empty definitions");return;}
            WorldMapPosition position = resolveWorldMapPosition(attached);
            placeBlock(activeLayer, position.mapX(), position.mapY(), empty);
        } else if (depth == EditorHierarchyDepth.BLOCK) {
            for (int i = 0; i < transparentChunks.length && empty < 0; i++)
                if (transparentChunks[i] && attached.getChunk(i).getSolidTileIndex() == 0
                        && attached.getChunk(i).getSolidTileAltIndex() == 0) empty = i;
            if (empty < 0) {setStatus("No empty chunk in this library; create a project with empty definitions");return;}
            int replacement = empty;
            editLocalComposition(raw -> replacement, null);
        } else {
            for (int i = 0; i < transparentPatterns.length && empty < 0; i++) if (transparentPatterns[i]) empty = i;
            if (empty < 0) {setStatus("No transparent pattern in this library; create a project with empty definitions");return;}
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
        } else setStatus("Enter a chunk for palette/priority, or a block for flips");
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
        cameraNavigationRequested = true;
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
            throw new IllegalArgumentException("Pattern is outside the loaded library");
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
            return "World";
        }
        if (depth == EditorHierarchyDepth.BLOCK) {
            return "World > Block " + selection.selectedBlock();
        }
        return "World > Block " + selection.selectedBlock() + " > Chunk " + selection.selectedChunk();
    }

    private static void requireNonNegative(int index, String name) {
        if (index < 0) {
            throw new IllegalArgumentException(name + " must be >= 0");
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

    private static int findUnreferencedBlockSlot(MutableLevel level, int sourceBlockIndex) {
        for (int blockIndex = 0; blockIndex < Math.min(256,level.getBlockCount()); blockIndex++) {
            if (blockIndex != sourceBlockIndex && !level.isBlockReferencedInMap(blockIndex)) {
                return blockIndex;
            }
        }
        return -1;
    }

    private static int findUnreferencedChunkSlot(MutableLevel level, int sourceChunkIndex) {
        for (int chunkIndex = 0; chunkIndex < Math.min(1024,level.getChunkCount()); chunkIndex++) {
            if (chunkIndex != sourceChunkIndex && !level.isChunkReferencedInBlocks(chunkIndex)) {
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
            throw new IllegalStateException("No MutableLevel is attached to the editor controller");
        }
        return level;
    }

    private record WorldMapPosition(int mapX, int mapY) {
    }
}
