package com.openggf.stageplay.editor;

import com.openggf.audio.AudioManager;
import com.openggf.data.Rom;
import com.openggf.data.RomManager;
import com.openggf.game.GameModule;
import com.openggf.mods.scene.host.SceneRomLibrary;
import com.openggf.mods.scene.host.SceneSurface;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.StageplayTimeline;
import com.openggf.stageplay.runtime.StageplayAudio;
import com.openggf.stageplay.runtime.StageplayRenderer;
import com.openggf.stageplay.runtime.StageplayResources;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.lwjgl.opengl.GL11.*;

/** Disposable preview resources. The workspace and its undo history outlive a loaded level. */
public final class StageplayEditorSession implements AutoCloseable {
    private final Supplier<GameModule> module;
    private final RomManager roms;
    private final AudioManager audioManager;
    private GameModule currentModule;
    private Rom currentRom;
    private SceneRomLibrary library;
    private StageplayResources resources;
    private StageplayRenderer renderer;
    private StageplayAudio audio;
    private Path assetRoot;
    private SceneSurface surface;
    private StageplayDocument prepared;
    private final StageplayExportJob export = new StageplayExportJob();

    public StageplayEditorSession(Supplier<GameModule> module, RomManager roms, AudioManager audioManager) {
        this.module = Objects.requireNonNull(module); this.roms = Objects.requireNonNull(roms);
        this.audioManager = Objects.requireNonNull(audioManager);
    }

    private void ensure(Path root) throws IOException {
        GameModule nextModule = module.get(); Rom nextRom = roms.getRom();
        Path nextRoot = root.toAbsolutePath().normalize();
        if (library != null && currentModule == nextModule && currentRom == nextRom && nextRoot.equals(assetRoot)) return;
        release();
        library = new SceneRomLibrary(nextModule, nextRom, roms);
        resources = new StageplayResources(library, false); resources.setProjectRoot(nextRoot);
        renderer = new StageplayRenderer(resources);
        audio = new StageplayAudio(audioManager, library, nextRoot);
        currentModule = nextModule; currentRom = nextRom; assetRoot = nextRoot;
    }

    public void validate(StageplayDocument document, Path root) throws IOException {
        ensure(root); resources.validate(document); audio.prepare(document); prepared = document;
    }

    public void play(StageplayDocument document, Path root, int frame) throws IOException {
        ensure(root);
        if (!document.equals(prepared)) validate(document,root);
        audio.seek(frame - 1); audio.playFrame(document,frame);
    }

    public void present(StageplayDocument document, int frame) { if (audio != null) audio.playFrame(document,frame); }
    public void stop() { if (audio != null) audio.stop(); }

    /** Draws in the engine's current offscreen viewport; no desktop focus or window is created. */
    public void draw(StageplayDocument document, int frame, Path root, Consumer<Throwable> onFailure) {
        draw(document,StageplayTimeline.sample(document,frame),root,onFailure);
    }

    /** The shared workspace can supply transient camera framing without adding document keys. */
    public void draw(StageplayDocument document, StageplayTimeline.Frame frame, Path root, Consumer<Throwable> onFailure) {
        Objects.requireNonNull(document); Objects.requireNonNull(onFailure);
        if (surface == null) surface = new SceneSurface();
        try {
            ensure(root);
            surface.record(document.width(),document.height(), canvas -> renderer.draw(document,frame,canvas));
        } catch (IOException | RuntimeException failure) {
            stop(); onFailure.accept(failure);
            surface.record(document.width(),document.height(),canvas -> {
                canvas.clear(0x101820); canvas.text("Stageplay preview unavailable",10,16,0xFFFFFFFF);
                canvas.text("Check the set / ROM and actor properties.",10,36,0xFFFFCC88);
            });
        }
        int[] viewport = new int[4]; glGetIntegerv(GL_VIEWPORT,viewport);
        int[] fitted = fitViewport(viewport,document.width(),document.height());
        glViewport(fitted[0],fitted[1],fitted[2],fitted[3]);
        try { surface.render(SceneSurface.projection(document.width(),document.height()),fitted); }
        finally { glViewport(viewport[0],viewport[1],viewport[2],viewport[3]); }
    }

    /** Fit film pixels to a host viewport without changing the film or gameplay aspect. */
    public static int[] fitViewport(int[] viewport, int filmWidth, int filmHeight) {
        double fit = Math.min(viewport[2]/(double)filmWidth,viewport[3]/(double)filmHeight);
        int width = Math.max(1,(int)Math.round(filmWidth*fit)), height = Math.max(1,(int)Math.round(filmHeight*fit));
        return new int[] {viewport[0]+(viewport[2]-width)/2,viewport[1]+(viewport[3]-height)/2,width,height};
    }

    public void export(StageplayDocument document, Path source, Path output) throws IOException {
        export.start(document,source,output,roms);
    }
    public String pollExport() { return export.poll(); }

    /** Call before replacing a native session, while its borrowed ROM is still valid. */
    public void release() {
        prepared = null;
        if (audio != null) { audio.close(); audio = null; }
        if (resources != null) { resources.close(); resources = null; }
        if (library != null) { library.close(); library = null; }
        renderer = null; currentModule = null; currentRom = null; assetRoot = null;
        if (surface != null) surface.releaseTextures();
    }

    @Override public void close() { export.close(); release(); if (surface != null) { surface.close(); surface = null; } }
}
