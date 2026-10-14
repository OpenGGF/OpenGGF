package com.openggf.editor.render;

import com.openggf.editor.EditorHierarchyDepth;
import com.openggf.editor.LevelEditorController;
import com.openggf.game.GameServices;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GLCommandGroup;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Block;
import com.openggf.level.Chunk;
import com.openggf.level.PatternDesc;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.lwjgl.opengl.GL11.GL_LINES;

public class EditorLibraryPaneRenderer {
    private static final float CHROME_R = 0.44f;
    private static final float CHROME_G = 0.72f;
    private static final float CHROME_B = 1.0f;

    private final LevelEditorController controller;
    private final GraphicsManager graphicsManager;
    private final EditorTextRenderer textRenderer;

    public EditorLibraryPaneRenderer() {
        this(null, GameServices.graphics());
    }

    public EditorLibraryPaneRenderer(LevelEditorController controller) {
        this(controller, GameServices.graphics());
    }

    public EditorLibraryPaneRenderer(LevelEditorController controller, GraphicsManager graphicsManager) {
        this(controller, graphicsManager, new EditorTextRenderer(graphicsManager));
    }

    public EditorLibraryPaneRenderer(LevelEditorController controller,
                                     GraphicsManager graphicsManager,
                                     EditorTextRenderer textRenderer) {
        this.controller = controller;
        this.graphicsManager = Objects.requireNonNull(graphicsManager, "graphicsManager");
        this.textRenderer = Objects.requireNonNull(textRenderer, "textRenderer");
    }

    public void renderCommands(List<String> lines) {
        List<GLCommand> backdrop = new ArrayList<>();
        for (int[] point : new int[][] {{8, 40}, {graphicsManager.getProjectionWidth()-8, 40},
                {graphicsManager.getProjectionWidth()-8, 192}, {8, 192}}) {
            backdrop.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1,
                    GLCommand.BlendType.ONE_MINUS_SRC_ALPHA, 0.025f, 0.04f, 0.07f,
                    point[0], point[1], 0, 0));
        }
        graphicsManager.registerCommand(new GLCommandGroup(org.lwjgl.opengl.GL11.GL_TRIANGLE_FAN, backdrop));
        textRenderer.renderFittedLines(lines,18,42,graphicsManager.getProjectionWidth()-36,.65f,10);
    }

    public void render(EditorHierarchyDepth depth) {
        List<GLCommand> commands=new ArrayList<>();appendCommands(commands);
        graphicsManager.registerCommand(new GLCommandGroup(GL_LINES,commands));
        textRenderer.renderFittedLines(buildCompactLibraryLines(depth),18,44,128,.65f,9);
        if(controller==null)return;
        var browser=controller.libraryBrowser();var grid=layout().libraryGrid();
        int start=browser.cursor()/9*9;
        for(int i=start;i<Math.min(start+9,browser.visibleEntries().size());i++) {
            int cell=i-start,x=grid.x()+(cell%3)*grid.cellSize(),y=grid.y()+(cell/3)*grid.cellSize();
            var entry=browser.visibleEntries().get(i);
            for(PreviewPlacement placement:tilePreview(entry,x+2,y+2))renderTile(placement);
            if(entry.kind()==EditorLibraryBrowserPane.Kind.OBJECT&&entry.previewArtKey()!=null) {
                var renderer=controller.libraryObjectPreviewRenderer(entry.previewArtKey());
                if(renderer!=null) {
                    var bounds=renderer.getFrameBoundsForIndex(0);
                    if(bounds.maxX()-bounds.minX()<=28&&bounds.maxY()-bounds.minY()<=24)
                        renderer.drawFrameIndex(0,x+4-bounds.minX(),y+2-bounds.minY());
                }
            }
            String label=entry.kind()==EditorLibraryBrowserPane.Kind.OBJECT
                    ?(entry.stockObjectId()==null?"key":"%02X".formatted(entry.stockObjectId())):String.valueOf(entry.index());
            textRenderer.renderFittedLines(List.of(label),x+2,y+25,32,.65f,9);
        }
    }
    private EditorUiLayout layout(){return new EditorUiLayout(graphicsManager.getProjectionWidth(),224);}
    protected void appendCommands(List<GLCommand> commands) {
        var pane=layout().library();
        EditorToolbarRenderer.appendRectOutline(commands,pane.left(),pane.top(),pane.right(),pane.bottom(),CHROME_R,CHROME_G,CHROME_B);
        if(controller==null)return;
        var grid=layout().libraryGrid();var browser=controller.libraryBrowser();int start=browser.cursor()/9*9;
        for(int i=start;i<Math.min(start+9,browser.visibleEntries().size());i++) {
            int cell=i-start,x=grid.x()+cell%3*grid.cellSize(),y=grid.y()+cell/3*grid.cellSize();
            boolean selected=i==browser.cursor();
            EditorToolbarRenderer.appendRectOutline(commands,x,y,x+grid.cellSize()-2,y+grid.cellSize()-2,
                    selected?1f:.4f,selected?.95f:.5f,selected?.2f:.6f);
            if(browser.visibleEntries().get(i).kind()==EditorLibraryBrowserPane.Kind.OBJECT) {
                EditorToolbarRenderer.appendRectOutline(commands,x+8,y+5,x+26,y+23,.7f,.7f,.7f);
                EditorToolbarRenderer.appendLine(commands,x+8,y+5,x+26,y+23,.7f,.7f,.7f);
                EditorToolbarRenderer.appendLine(commands,x+26,y+5,x+8,y+23,.7f,.7f,.7f);
            }
        }
    }
    protected List<EditorTextRenderer.TextCommand> buildLibraryTextCommands(EditorHierarchyDepth depth) {
        return textRenderer.buildFittedTextCommands(buildCompactLibraryLines(depth),18,44,128,.65f,9);
    }

    private List<String> buildCompactLibraryLines(EditorHierarchyDepth depth) {
        if (controller == null) return List.of(libraryTitle(depth), "No controller");
        var browser = controller.libraryBrowser();
        var selected = browser.selected();
        String title = selected != null && selected.kind() == EditorLibraryBrowserPane.Kind.OBJECT
                ? "Object palette" : libraryTitle(depth);
        return List.of(title + " " + (browser.cursor() + 1) + "/" + browser.visibleEntries().size(),
                "Filter " + (controller.isLibraryFilterInputActive() ? "ON " : "OFF ")
                        + (browser.filter().isEmpty() ? "(Insert)" : browser.filter()),
                selected == null ? "No matching entries" : selected.label());
    }

    protected List<String> buildLibraryLines(EditorHierarchyDepth depth) {
        if(controller==null)return List.of(libraryTitle(depth),"No controller");
        var browser=controller.libraryBrowser();var selected=browser.selected();
        String title=selected==null?libraryTitle(depth):switch(selected.kind()) {
            case BLOCK->"Block library";case CHUNK->"Chunk library";case PATTERN->"Pattern library";case OBJECT->"Object palette";
        };
        return List.of(title+" | Cursor "+(browser.cursor()+1)+"/"+browser.visibleEntries().size(),
                "Filter "+(controller.isLibraryFilterInputActive()?"ON ":"OFF ")+(browser.filter().isEmpty()?"(Insert)":browser.filter()),
                selected==null?"No matching entries":selected.label(),
                selected!=null&&selected.kind()==EditorLibraryBrowserPane.Kind.OBJECT
                        ?(hasExactObjectArtPreview()?"Preview exact object art":"Preview [generic object]")
                        :controller.brushSummary(),"Focus "+controller.focusRegion());
    }
    protected List<PreviewPlacement> buildSelectedTilePreview() {
        if(controller==null||controller.libraryBrowser().selected()==null)return List.of();
        int cell=controller.libraryBrowser().cursor()%9;var grid=layout().libraryGrid();
        return tilePreview(controller.libraryBrowser().selected(),grid.x()+cell%3*grid.cellSize()+2,grid.y()+cell/3*grid.cellSize()+2);
    }
    private List<PreviewPlacement> tilePreview(EditorLibraryBrowserPane.Entry entry,int x,int y) {
        List<PreviewPlacement> placements=new ArrayList<>();
        if(entry.kind()==EditorLibraryBrowserPane.Kind.PATTERN)
            placements.add(new PreviewPlacement(new PatternDesc(entry.index()),x+8,y+4,16));
        else if(entry.kind()==EditorLibraryBrowserPane.Kind.CHUNK)
            appendChunk(placements,controller.libraryPreviewChunk(entry.index()),x+8,y+4,8,false,false);
        else if(entry.kind()==EditorLibraryBrowserPane.Kind.BLOCK) {
            Block block=controller.libraryPreviewBlock(entry.index());
            if(block!=null) {
                int patternSize=Math.max(1,Math.min(8,24/(block.getGridSide()*2)));
                for(int cy=0;cy<block.getGridSide();cy++)for(int cx=0;cx<block.getGridSide();cx++) {
                    var desc=block.getChunkDesc(cx,cy);
                    appendChunk(placements,controller.libraryPreviewChunk(desc.getChunkIndex()),
                            x+cx*patternSize*2,y+cy*patternSize*2,patternSize,desc.getHFlip(),desc.getVFlip());
                }
            }
        }
        return List.copyOf(placements);
    }
    private static void appendChunk(List<PreviewPlacement> placements,Chunk chunk,int x,int y,int size,boolean hFlip,boolean vFlip) {
        if(chunk==null)return;
        for(int cy=0;cy<2;cy++)for(int cx=0;cx<2;cx++) {
            PatternDesc source=chunk.getPatternDesc(hFlip?1-cx:cx,vFlip?1-cy:cy);
            placements.add(new PreviewPlacement(new PatternDesc(source.get()^(hFlip?0x800:0)^(vFlip?0x1000:0)),x+cx*size,y+cy*size,size));
        }
    }
    private void renderTile(PreviewPlacement placement) {
        graphicsManager.renderPatternWithIdScaled(placement.descriptor().getPatternIndex(),placement.descriptor(),
                placement.x(),placement.y(),placement.size(),placement.size());
    }

    protected boolean hasExactObjectArtPreview() {
        if(controller==null)return false;
        EditorLibraryBrowserPane.Entry selected=controller.libraryBrowser().selected();
        return selected!=null&&selected.previewArtKey()!=null
                &&controller.libraryObjectPreviewRenderer(selected.previewArtKey())!=null;
    }

    protected record PreviewPlacement(PatternDesc descriptor,int x,int y,int size) {
        public PreviewPlacement(PatternDesc descriptor,int x,int y){this(descriptor,x,y,8);}
    }

    private static String libraryTitle(EditorHierarchyDepth depth) {
        return switch (depth) {
            case WORLD -> "Block library";
            case BLOCK -> "Chunk library";
            case CHUNK -> "Pattern library";
        };
    }

    private static String valueOrNone(Integer value) {
        return value == null ? "-" : value.toString();
    }
}
