package com.openggf.editor.render;

import com.openggf.editor.*;

/** Shared logical-pixel editor chrome and pointer geometry, including wide viewports. */
public record EditorUiLayout(int width, int height) {
    public record Rect(int left, int top, int right, int bottom) {
        public int width() { return right-left; }
        public int height() { return bottom-top; }
        public boolean contains(int x,int y) { return x>=left&&x<right&&y>=top&&y<bottom; }
    }
    public record Grid(int x,int y,int cellSize,int side) {
        public Rect bounds() { return new Rect(x,y,x+side*cellSize,y+side*cellSize); }
        public int cellX(int px) { return (px-x)/cellSize; }
        public int cellY(int py) { return (py-y)/cellSize; }
    }
    public EditorUiLayout { width=Math.max(320,width);height=Math.max(224,height); }
    public Rect toolbar() { return new Rect(4,4,width-4,28); }
    public Rect commands() { return new Rect(4,height-26,width-4,height-4); }
    public Rect library() { return new Rect(12,34,152,height-30); }
    public Rect focused(EditorHierarchyDepth depth) {
        return new Rect(width-(depth==EditorHierarchyDepth.BLOCK?124:144),34,width-4,height-30);
    }
    public Grid focusedGrid(EditorHierarchyDepth depth,int side) {
        Rect pane=focused(depth);
        int nativeCell=depth==EditorHierarchyDepth.BLOCK?16:8;
        int cell=Math.max(2,Math.min(nativeCell,Math.min(pane.width()-8,pane.height()-8)/Math.max(1,side)));
        return new Grid(pane.left()+(pane.width()-side*cell)/2,
                pane.top()+(pane.height()-side*cell)/2,cell,side);
    }
    public EditorFocusRegion libraryRegion(LevelEditorController controller) {
        if(controller.spawnEditMode()==EditorSpawnEditMode.OBJECTS)return EditorFocusRegion.SPAWN_PALETTE;
        return switch(controller.depth()) {
            case WORLD -> EditorFocusRegion.BLOCK_PANE;
            case BLOCK -> EditorFocusRegion.CHUNK_PANE;
            case CHUNK -> EditorFocusRegion.PATTERN_PANE;
        };
    }
    public EditorFocusRegion hit(LevelEditorController controller,int x,int y) {
        if(toolbar().contains(x,y))return EditorFocusRegion.TOOLBAR;
        if(commands().contains(x,y))return EditorFocusRegion.COMMAND_STRIP;
        if(controller.isLibraryBrowserFocused()&&library().contains(x,y))return libraryRegion(controller);
        if(controller.depth()!=EditorHierarchyDepth.WORLD&&focused(controller.depth()).contains(x,y))
            return controller.depth()==EditorHierarchyDepth.BLOCK?EditorFocusRegion.BLOCK_PANE:EditorFocusRegion.CHUNK_PANE;
        return EditorFocusRegion.WORLD_CANVAS;
    }
    public Grid libraryGrid() { return new Grid(28,82,36,3); }
}
