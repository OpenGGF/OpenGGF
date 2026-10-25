package com.openggf.graphics;

import com.openggf.graphics.color.DisplayColorConverter;
import com.openggf.graphics.color.DisplayColorExpansion;
import com.openggf.graphics.color.DisplayColorProfile;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static com.openggf.level.LevelConstants.COLORS_PER_PALETTE;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_CLAMP_TO_EDGE;

/**
 * One rendering host's complete palette texture/publication/residency lifetime.
 * CPU banks and mutable ROM colours remain with their existing owners. Construction
 * allocates no native resources. The host is used only for its existing dispatch and
 * rendering-mode contracts; this owner has no gameplay/session lookup authority.
 */
final class PaletteTextureOwner {
    private static final UnderwaterPaletteUploadOps OPEN_GL_UNDERWATER_PALETTE_UPLOAD_OPS =
            new OpenGlUnderwaterPaletteUploadOps();
    private final GraphicsManager graphics;
	private final Map<Object,ScopedPaletteLease> scopedPaletteOwners=new java.util.IdentityHashMap<>();
	private long scopedPaletteGeneration;
    private ScopedUnderwaterBasis scopedUnderwaterBasis;
    private boolean scopedUnderwaterDirty;
    private int underwaterCompositionLines;
    private Object scopedPaletteWorld;
    private final AppearancePalettes.Rows appearancePaletteRows = new AppearancePalettes.Rows(this);
	private final Map<String, Integer> paletteTextureMap = new HashMap<>(); // Map for palette textures
	// Last palette handed to cachePaletteTexture per line, so a palette fade change
	// can re-upload the affected lines without asking their owners to write again.
	private PaletteView[] lastCachedPaletteLines = new PaletteView[0];
	private boolean paletteUploadLatched;
	// Held as PaletteView so the latch state stays in the graphics layer's own types.
	private PaletteView[] pendingUnderwaterPalettes;
	private PaletteView pendingUnderwaterLine0;
	private boolean underwaterUploadPending;
	private final boolean[] pendingPaletteUploads = new boolean[64];
	private final PaletteFadePresentation paletteFadePresentation = new PaletteFadePresentation();
	// The fade CRAM currently holds. A fade step is a Normal_palette rewrite by the
	// game's logic (S3K Animate_Palette -> Pal_FromBlack, sonic3k.asm:5050-5060), so
	// while uploads are latched it reaches CRAM with the next V-int publication
	// (VInt_8 dma68kToVDP Normal_palette), together with that frame's sprite table and
	// scroll, not on the frame the logic requested it. Unlatched games keep the
	// immediate upload: the published fade follows the requested one at once.
	private final PaletteFadePresentation publishedPaletteFade = new PaletteFadePresentation();
	private boolean paletteFadePublicationPending;
	// Set by a rewind restore of the displayed generation; cleared by the next publication.
	private boolean displayedPaletteFadeHeld;
	private Integer combinedPaletteTextureId;
	private int currentPaletteTextureHeight = 0;
	private DisplayColorProfile displayColorProfile = DisplayColorProfile.RAW_RGB;
	private DisplayColorExpansion displayColorExpansion = DisplayColorExpansion.FULL;
	// Lazily allocated to avoid LWJGL native library loading in headless tests
	private ByteBuffer paletteUploadBuffer;
	private ByteBuffer underwaterPaletteUploadBuffer;
	private byte[] underwaterPaletteContentKey;
	private int underwaterPaletteContentKeyLength;
	private int pendingUnderwaterPaletteContentKeyLength;
	private int underwaterPaletteContentKeyWriteIndex;
	private boolean underwaterPaletteContentKeyChanged;
	private boolean underwaterPaletteContentKeyValid;
	private Object[] underwaterPaletteRowSources;
	private byte[] underwaterPaletteRowCases;
	private byte[] underwaterPaletteSourceRgb;
	private byte[][] underwaterDerivedRowKeys;
	private int[][] underwaterDerivedRowRgba;
	private int underwaterDerivedRowRecomputeCount;
	private UnderwaterPaletteUploadOps underwaterPaletteUploadOps = OPEN_GL_UNDERWATER_PALETTE_UPLOAD_OPS;
	private static final byte UNDERWATER_ROW_ABSENT = 0;
	private static final byte UNDERWATER_ROW_DIRECT = 1;
	private static final byte UNDERWATER_ROW_DERIVED = 2;
	private static final byte UNDERWATER_BASE_NORMAL = 3;
	private static final byte UNDERWATER_BASE_SHIFTED = 4;


    PaletteTextureOwner(GraphicsManager graphics) {
        this.graphics = graphics;
    }

    // Read separately by the typed host adapter to preserve cast/sampling order.
    PaletteView[] pendingUnderwaterPalettes() { return pendingUnderwaterPalettes; }
    PaletteView pendingUnderwaterLine0() { return pendingUnderwaterLine0; }

    void forgetTextureEntries() { paletteTextureMap.clear(); }
	/**
	 * Lazily allocate the palette upload buffer.
	 * This avoids triggering LWJGL native library loading during GraphicsManager construction.
	 */
	private ByteBuffer ensurePaletteUploadBuffer() {
		if (paletteUploadBuffer == null) {
			paletteUploadBuffer = MemoryUtil.memAlloc(COLORS_PER_PALETTE * 4);
		}
		return paletteUploadBuffer;
	}

	/**
	 * Lazily allocate the underwater palette upload buffer.
	 * This avoids triggering LWJGL native library loading during GraphicsManager construction.
	 */
	private ByteBuffer ensureUnderwaterPaletteUploadBuffer(int requiredCapacity) {
		if (underwaterPaletteUploadBuffer == null) {
			underwaterPaletteUploadBuffer = MemoryUtil.memAlloc(requiredCapacity);
		} else if (underwaterPaletteUploadBuffer.capacity() < requiredCapacity) {
			underwaterPaletteUploadBuffer = MemoryUtil.memRealloc(underwaterPaletteUploadBuffer, requiredCapacity);
		}
		return underwaterPaletteUploadBuffer;
	}

	void setDisplayColorProfile(DisplayColorProfile displayColorProfile) {
        var next = displayColorProfile != null ? displayColorProfile : DisplayColorProfile.RAW_RGB;
        if (this.displayColorProfile == next) return;
		this.displayColorProfile = next;
        // Native normal rows retain their existing owner-driven refresh. Scoped
        // intensity recipes have no level owner to republish their derived RGB.
        for (int line = 0; line < lastCachedPaletteLines.length; line++) {
            if (lastCachedPaletteLines[line] instanceof NativeIntensityPalette)
                cacheLatchablePaletteTexture(lastCachedPaletteLines[line], line);
        }
        scopedUnderwaterDirty = true;
        PatternRenderCommand.resetFrameState();
	}

	DisplayColorProfile getDisplayColorProfile() {
		return displayColorProfile;
	}

	/** Changes only uploaded RGB; native palette bytes, fades and pending V-int writes retain ownership. */
	void setDisplayColorExpansion(DisplayColorExpansion expansion) {
		DisplayColorExpansion next = expansion == null ? DisplayColorExpansion.FULL : expansion;
		if (displayColorExpansion == next) return;
		displayColorExpansion = next;
		for (int line = 0; line < lastCachedPaletteLines.length; line++) {
			if (lastCachedPaletteLines[line] != null) cacheLatchablePaletteTexture(lastCachedPaletteLines[line], line);
		}
		scopedUnderwaterDirty = true;
		PatternRenderCommand.resetFrameState();
	}

	DisplayColorExpansion getDisplayColorExpansion() { return displayColorExpansion; }

	void writePaletteColor(ByteBuffer buffer, int r, int g, int b, int colorIndex) {
		DisplayColorConverter.writeRgbBytes(r, g, b, displayColorProfile, displayColorExpansion, buffer);
		buffer.put((byte) (colorIndex == 0 ? 0 : 255));
	}

	int[] paletteUploadRgbaForTest(int r, int g, int b, int colorIndex) {
		ByteBuffer buffer = ByteBuffer.allocate(4);
		writePaletteColor(buffer, r, g, b, colorIndex);
		buffer.flip();
		return new int[] {
				Byte.toUnsignedInt(buffer.get()),
				Byte.toUnsignedInt(buffer.get()),
				Byte.toUnsignedInt(buffer.get()),
				Byte.toUnsignedInt(buffer.get())
		};
	}


	int scopedPaletteBank(Object owner,PaletteView[] palettes) {
		if(owner==null || palettes==null || palettes.length!=4 || java.util.Arrays.stream(palettes).anyMatch(java.util.Objects::isNull))
			throw new IllegalArgumentException("A scoped palette bank needs an owner and exactly four ROM palette lines");
		var lease=scopedPaletteOwners.get(owner);
		if(lease==null || !RenderContext.scopedPaletteBankValid(lease.token(),lease.base())) {
			Object token=new Object();int base=RenderContext.reserveScopedPaletteBank(token);
			lease=new ScopedPaletteLease(token,base,++scopedPaletteGeneration);scopedPaletteOwners.put(owner,lease);
		}
		for(int line=0;line<4;line++) cacheImmediatePaletteTexture(palettes[line],lease.base()+line);
        // A trusted resolver may upload between draws while the atlas unit is active.
        PatternRenderCommand.resetFrameState();
        scopedUnderwaterDirty=true;
		return lease.base();
	}
	long scopedPaletteGeneration(Object owner) { return scopedPaletteOwners.get(owner).generation(); }
	boolean scopedPaletteBankValid(Object owner,int base,long generation) {
		var lease=scopedPaletteOwners.get(owner);
		return lease!=null && lease.base()==base && lease.generation()==generation
				&& RenderContext.scopedPaletteBankValid(lease.token(),base);
	}
	void releaseScopedPaletteBank(Object owner) {
		var lease=scopedPaletteOwners.get(owner);if(lease==null) return;
        try {
			if(RenderContext.scopedPaletteBankValid(lease.token(),lease.base())) {
				for(int line=lease.base();line<lease.base()+4;line++) {
					uploadPaletteLine(ScopedUnderwaterBasis.blankLine(),line);paletteTextureMap.remove("palette_"+line);
					if(line<lastCachedPaletteLines.length)lastCachedPaletteLines[line]=null;
					pendingPaletteUploads[line]=false;
				}
				RenderContext.releaseScopedPaletteBank(lease.token());
			}
            // Keep the exact lease reserved until every row and its cached inputs clear.
            // A failed upload may be retried through the same public Bank handle.
            scopedPaletteOwners.remove(owner,lease);
        } finally {
			underwaterPaletteContentKeyValid=false;
            PatternRenderCommand.resetFrameState();
            scopedUnderwaterDirty=true;
        }
	}
    void bindScopedPaletteWorld(Object world) {
        if(scopedPaletteWorld!=null && scopedPaletteWorld!=world)clearScopedUnderwaterBasis();
        scopedPaletteWorld=world;
    }
    void clearScopedUnderwaterBasis(Object world) {
        if(scopedPaletteWorld==world) { clearScopedUnderwaterBasis();scopedPaletteWorld=null; }
    }
    private void clearScopedUnderwaterBasis() {
        scopedUnderwaterBasis=null;scopedUnderwaterDirty=false;underwaterCompositionLines=0;
    }
    void refreshScopedUnderwaterResidency() {
        var basis=scopedUnderwaterBasis;
        if(basis!=null && (scopedUnderwaterDirty || underwaterCompositionLines!=RenderContext.getTotalPaletteLines())) {
            composeUnderwaterPaletteTexture(basis.rows(),basis.normalLine0(),basis);
            PatternRenderCommand.resetFrameState();
        }
    }
    private PaletteView scopedPaletteForEffectiveLine(int row) {
        for(var lease:scopedPaletteOwners.values())if(row>=lease.base() && row<lease.base()+4
                && RenderContext.scopedPaletteBankValid(lease.token(),lease.base()))
            return row<lastCachedPaletteLines.length?lastCachedPaletteLines[row]:null;
        return null;
    }
	private void invalidateScopedPaletteBanks() {
		for(var lease:scopedPaletteOwners.values())RenderContext.releaseScopedPaletteBank(lease.token());
		scopedPaletteOwners.clear();
		appearancePaletteRows.invalidateAll();
	}

	AppearancePalettes.Rows appearancePaletteRows() { return appearancePaletteRows; }

	/** The palette last handed to {@code row}, or null. */
	PaletteView cachedPaletteLine(int row) {
		return row >= 0 && row < lastCachedPaletteLines.length ? lastCachedPaletteLines[row] : null;
	}

	/** Uploads a composed appearance row; it fades with {@code fadeLine}, the row it mirrors. */
	void uploadAppearanceRow(PaletteView palette, int row, int fadeLine) {
		uploadPaletteRow(palette, row, fadeLine);
	}

	/** Appearance rows were reserved or released: draws and the water texture see new rows. */
	void appearanceRowsChanged() {
		PatternRenderCommand.resetFrameState();
		scopedUnderwaterDirty = true;
	}

	void appearanceUnderwaterChanged() {
		scopedUnderwaterDirty = true;
	}



	void cacheImmediatePaletteTexture(PaletteView palette, int paletteId) {
		rememberCachedPaletteLine(palette, paletteId);
		if (paletteId >= 0 && paletteId < pendingPaletteUploads.length) {
			pendingPaletteUploads[paletteId] = false;
		}
		uploadPaletteLine(palette, paletteId);
	}

	/** Level-pipeline palette write: deferred to the next V-int publication while latched. */
	void cacheLatchablePaletteTexture(PaletteView palette, int paletteId) {
		rememberCachedPaletteLine(palette, paletteId);
		boolean inRange = paletteId >= 0 && paletteId < pendingPaletteUploads.length;
		if (paletteUploadLatched && inRange) {
			pendingPaletteUploads[paletteId] = true;
			return;
		}
		if (inRange) {
			pendingPaletteUploads[paletteId] = false;
		}
		uploadPaletteLine(palette, paletteId);
	}

	/**
	 * Hardware CRAM is written from {@code Normal_palette} in V-int, together with the sprite
	 * table and scroll buffers. While latched, palette line writes wait for the next publication
	 * instead of reaching the GPU on the frame the game logic wrote them.
	 */
	void setPaletteUploadLatched(boolean latched) {
		flushPendingPaletteUploads();
		paletteUploadLatched = latched;
	}

	boolean isPaletteUploadLatched() {
		return paletteUploadLatched;
	}

	void flushPendingPaletteUploads() {
		displayedPaletteFadeHeld = false;
		if (paletteFadePublicationPending) {
			paletteFadePublicationPending = false;
			publishRequestedPaletteFade();
		}
		if (underwaterUploadPending) {
			underwaterUploadPending = false;
			boolean latched = paletteUploadLatched;
			paletteUploadLatched = false;
			try {
				graphics.replayPendingUnderwaterUpload();
			} finally {
				paletteUploadLatched = latched;
				pendingUnderwaterPalettes = null;
				pendingUnderwaterLine0 = null;
			}
		}
		for (int id = 0; id < pendingPaletteUploads.length; id++) {
			if (!pendingPaletteUploads[id]) {
				continue;
			}
			pendingPaletteUploads[id] = false;
			if (id < lastCachedPaletteLines.length && lastCachedPaletteLines[id] != null) {
				uploadPaletteLine(lastCachedPaletteLines[id], id);
			}
		}
	}

	private void uploadPaletteLine(PaletteView palette, int paletteId) {
		uploadPaletteRow(palette, paletteId, paletteId);
		// Private appearance rows mirror this row; recompose them from what it now holds.
		appearancePaletteRows.sourceUploaded(paletteId, palette);
	}

	private void uploadPaletteRow(PaletteView palette, int paletteId, int fadeLineId) {
		if (graphics.paletteHeadlessMode()) {
			// In headless mode, just record that the palette was cached
			paletteTextureMap.put("palette_" + paletteId, -1);
			return;
		}
		int requiredHeight = RenderContext.getTotalPaletteLines();
		if (combinedPaletteTextureId == null) {
			combinedPaletteTextureId = glGenTextures();
			currentPaletteTextureHeight = requiredHeight;
			ByteBuffer emptyBuffer = MemoryUtil.memAlloc(COLORS_PER_PALETTE * 4 * requiredHeight);
			try {
				glBindTexture(GL_TEXTURE_2D, combinedPaletteTextureId);
				glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, 16, requiredHeight, 0, GL_RGBA, GL_UNSIGNED_BYTE, emptyBuffer);
				glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
				glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
				glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
				glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
			} finally {
				MemoryUtil.memFree(emptyBuffer);
			}
		} else if (requiredHeight > currentPaletteTextureHeight) {
			// Texture needs to grow to accommodate new contexts.
			// Read back existing palette data before replacing the texture so that
			// level palettes (lines 0-3) are not wiped by the resize.
			int oldBytes = COLORS_PER_PALETTE * 4 * currentPaletteTextureHeight;
			ByteBuffer oldData = MemoryUtil.memAlloc(oldBytes);
			try {
				glBindTexture(GL_TEXTURE_2D, combinedPaletteTextureId);
				glGetTexImage(GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_BYTE, oldData);
				currentPaletteTextureHeight = requiredHeight;
				ByteBuffer newBuffer = MemoryUtil.memAlloc(COLORS_PER_PALETTE * 4 * requiredHeight);
				try {
					oldData.rewind();
					newBuffer.put(oldData);
					while (newBuffer.hasRemaining()) {
						newBuffer.put((byte) 0);
					}
					newBuffer.flip();
					glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, 16, requiredHeight, 0, GL_RGBA, GL_UNSIGNED_BYTE, newBuffer);
				} finally {
					MemoryUtil.memFree(newBuffer);
				}
			} finally {
				MemoryUtil.memFree(oldData);
			}
		}

		ByteBuffer paletteBuffer = ensurePaletteUploadBuffer();
		paletteBuffer.clear();
		// An active palette fade owns what CRAM receives for its lines (PalFadeIn_Alt
		// rebuilds v_palette from v_palette_fading); the owner's palette is untouched.
		boolean fadeLine = publishedPaletteFade.affects(fadeLineId);
		for (int i = 0; i < COLORS_PER_PALETTE; i++) {
            if (palette instanceof NativeIntensityPalette intensity) {
                int rgb = intensity.displayedRgb(i, publishedPaletteFade, displayColorProfile, displayColorExpansion);
                paletteBuffer.put((byte) (rgb >>> 16)).put((byte) (rgb >>> 8)).put((byte) rgb)
                        .put((byte) (i == 0 ? 0 : 255));
                continue;
            }
			int r = Byte.toUnsignedInt(palette.red(i));
			int g = Byte.toUnsignedInt(palette.green(i));
			int b = Byte.toUnsignedInt(palette.blue(i));
			if (fadeLine) {
				int faded = publishedPaletteFade.fadeRgb(r, g, b);
				r = (faded >>> 16) & 0xFF;
				g = (faded >>> 8) & 0xFF;
				b = faded & 0xFF;
			}
			writePaletteColor(paletteBuffer, r, g, b, i);
		}
		paletteBuffer.flip();

		glBindTexture(GL_TEXTURE_2D, combinedPaletteTextureId);
		glTexSubImage2D(GL_TEXTURE_2D, 0, 0, paletteId, 16, 1, GL_RGBA, GL_UNSIGNED_BYTE, paletteBuffer);

		paletteTextureMap.put("palette_" + paletteId, combinedPaletteTextureId);
	}

	private void rememberCachedPaletteLine(PaletteView palette, int paletteId) {
		if (paletteId < 0 || paletteId >= 64) {
			return;
		}
		if (paletteId >= lastCachedPaletteLines.length) {
			lastCachedPaletteLines = java.util.Arrays.copyOf(lastCachedPaletteLines, paletteId + 1);
		}
		lastCachedPaletteLines[paletteId] = palette;
	}

	/** The palette fade currently applied to CRAM uploads; see {@link PaletteFadePresentation}. */
	PaletteFadePresentation getPaletteFadePresentation() {
		return paletteFadePresentation;
	}

	/**
	 * Applies a Mega Drive palette fade step to every later upload of the masked
	 * lines and immediately re-uploads the lines already cached, so the change is
	 * visible on the frame that requests it regardless of whether their owners
	 * write again. {@code steps} counts the FadeIn_AddColor / FadeOut_DecColor
	 * passes applied so far.
	 */
	void setPaletteFadePresentation(PaletteFadePresentation.Mode mode, int steps, int lineMask) {
		if (paletteFadePresentation.set(mode, steps, lineMask)) {
			requestPaletteFadePublication();
		}
	}

	/** Ends the palette fade and re-uploads the lines it was transforming from their owners' palettes. */
	void clearPaletteFadePresentation() {
		if (paletteFadePresentation.clear()) {
			requestPaletteFadePublication();
		}
	}

	private void requestPaletteFadePublication() {
        // Rebinding a restored owner is a redraw, not a new V-int request.
        if (displayedPaletteFadeHeld
                && paletteFadePresentation.mode() == publishedPaletteFade.mode()
                && paletteFadePresentation.steps() == publishedPaletteFade.steps()
                && paletteFadePresentation.lineMask() == publishedPaletteFade.lineMask()) return;
		if (paletteUploadLatched || displayedPaletteFadeHeld) {
			paletteFadePublicationPending = true;
			return;
		}
		publishRequestedPaletteFade();
	}

	/** The fade CRAM holds now; it can trail the requested fade by one V-int publication. */
	PaletteFadePresentation displayedPaletteFade() {
		return publishedPaletteFade;
	}

	PaletteUploadPresentation.DisplayedFade captureDisplayedPaletteFade() {
		return new PaletteUploadPresentation.DisplayedFade(publishedPaletteFade.mode(), publishedPaletteFade.steps(),
				publishedPaletteFade.lineMask(), paletteFadePublicationPending);
	}

	/**
	 * Restores the displayed generation after a rewind redraw. Palette owners that re-request
	 * their fade while the snapshot is restored update only the requested state; the restored
	 * picture keeps the captured CRAM generation until the next V-int publication.
	 */
	void restoreDisplayedPaletteFade(PaletteUploadPresentation.DisplayedFade state) {
		int previousMask = publishedPaletteFade.lineMask();
		boolean changed = state.mode() == PaletteFadePresentation.Mode.NONE
				? publishedPaletteFade.clear()
				: publishedPaletteFade.set(state.mode(), state.steps(), state.lineMask());
		paletteFadePublicationPending = state.pending();
		displayedPaletteFadeHeld = true;
		if (changed) {
            if (scopedUnderwaterBasis != null) {
                // Keep captured raw water inputs detached from later palette writers.
                scopedUnderwaterBasis = scopedUnderwaterBasis.withFade(publishedPaletteFade);
                scopedUnderwaterDirty = true;
            }
			reuploadPaletteFadeLines(previousMask | publishedPaletteFade.lineMask());
		}
	}

    /** Draw-only reveal: preserve native fade requests, publication gates and rewind state. */
    void withoutDisplayedPaletteFade(Runnable draw) {
        var saved = captureDisplayedPaletteFade();
        boolean held = displayedPaletteFadeHeld;
        try {
            restoreDisplayedPaletteFade(new PaletteUploadPresentation.DisplayedFade(
                    PaletteFadePresentation.Mode.NONE, 0, 0, saved.pending()));
            // A latched water cache keeps the native publication's raw RGB inputs.
            // Recompose those inputs for this draw without admitting pending writes.
            refreshScopedUnderwaterResidency();
            draw.run();
        } finally {
            try {
                restoreDisplayedPaletteFade(saved);
                refreshScopedUnderwaterResidency();
            } finally {
                displayedPaletteFadeHeld = held;
            }
        }
    }

	private void publishRequestedPaletteFade() {
        var previousMode = publishedPaletteFade.mode();
		int previousMask = publishedPaletteFade.lineMask();
		boolean changed = paletteFadePresentation.isActive()
				? publishedPaletteFade.set(paletteFadePresentation.mode(), paletteFadePresentation.steps(),
						paletteFadePresentation.lineMask())
				: publishedPaletteFade.clear();
		if (changed) {
            if (scopedUnderwaterBasis != null && (isWhitePaletteFade(previousMode)
                    || isWhitePaletteFade(publishedPaletteFade.mode()))) {
                // Keep captured raw water inputs detached from later palette writers.
                scopedUnderwaterBasis = scopedUnderwaterBasis.withFade(publishedPaletteFade);
                scopedUnderwaterDirty = true;
            }
			reuploadPaletteFadeLines(previousMask | publishedPaletteFade.lineMask());
		}
	}

    private static boolean isWhitePaletteFade(PaletteFadePresentation.Mode mode) {
        return mode == PaletteFadePresentation.Mode.FROM_WHITE || mode == PaletteFadePresentation.Mode.TO_WHITE;
    }

    private static boolean paletteFadeAffects(PaletteFadePresentation.Mode mode, int mask, int line) {
        return mode != PaletteFadePresentation.Mode.NONE && line >= 0
                && (mask == PaletteFadePresentation.ALL_LINES
                || line < Integer.SIZE && (mask & (1 << line)) != 0);
    }

	private void reuploadPaletteFadeLines(int lineMask) {
		for (int line = 0; line < lastCachedPaletteLines.length; line++) {
            int sourceLine = lastCachedPaletteLines[line] instanceof NativeIntensityPalette intensity
                    ? intensity.nativeSourceLine() : line;
			if ((lineMask == PaletteFadePresentation.ALL_LINES
                    || sourceLine < Integer.SIZE && (lineMask & (1 << sourceLine)) != 0)
                    && lastCachedPaletteLines[line] != null) {
				uploadPaletteLine(lastCachedPaletteLines[line], line);
			}
		}
	}


	Integer getCombinedPaletteTextureId() {
		return combinedPaletteTextureId;
	}


	private Integer underwaterPaletteTextureId;

	Integer getUnderwaterPaletteTextureId() {
		return underwaterPaletteTextureId;
	}

	/**
	 * Drops all cached palette texture state while leaving pattern/shader state intact.
	 * Use this when changing games through non-pattern-rendered screens so the next
	 * game cannot render a frame with another game's stale palette rows.
	 */
	void clearPaletteTextures() {
		invalidateScopedPaletteBanks();
        clearScopedUnderwaterBasis();scopedPaletteWorld=null;
		paletteTextureMap.clear();
		// This is a session boundary, so discard the Java-side presentation
		// owners together with their native textures. Calling
		// clearPaletteFadePresentation() here would re-upload the old owners
		// while the textures are being disposed.
		paletteFadePresentation.clear();
		publishedPaletteFade.clear();
		paletteFadePublicationPending = false;
		displayedPaletteFadeHeld = false;
		lastCachedPaletteLines = new PaletteView[0];
		if (!graphics.paletteHeadlessMode() && graphics.paletteGlInitialized()) {
			if (combinedPaletteTextureId != null) {
				glDeleteTextures(combinedPaletteTextureId);
			}
			if (underwaterPaletteTextureId != null) {
				glDeleteTextures(underwaterPaletteTextureId);
			}
		}
		combinedPaletteTextureId = null;
		currentPaletteTextureHeight = 0;
		underwaterPaletteTextureId = null;
		graphics.clearUnderwaterPaletteSelection();
		if (paletteUploadBuffer != null) {
			MemoryUtil.memFree(paletteUploadBuffer);
			paletteUploadBuffer = null;
		}
		if (underwaterPaletteUploadBuffer != null) {
			MemoryUtil.memFree(underwaterPaletteUploadBuffer);
			underwaterPaletteUploadBuffer = null;
		}
		underwaterPaletteContentKey = null;
		underwaterPaletteContentKeyLength = 0;
		underwaterPaletteContentKeyValid = false;
		underwaterPaletteRowSources = null;
		underwaterPaletteRowCases = null;
		underwaterPaletteSourceRgb = null;
		underwaterDerivedRowKeys = null;
		underwaterDerivedRowRgba = null;
		underwaterDerivedRowRecomputeCount = 0;
	}

	void setUnderwaterPaletteUploadOps(UnderwaterPaletteUploadOps uploadOps) {
		underwaterPaletteUploadOps = Objects.requireNonNull(uploadOps);
	}



	Integer cacheUnderwaterPaletteTexture(PaletteView[] palettes, PaletteView normalLine0) {
		if (graphics.paletteHeadlessMode() && underwaterPaletteUploadOps == OPEN_GL_UNDERWATER_PALETTE_UPLOAD_OPS) {
			return null;
		}
		if (paletteUploadLatched && underwaterPaletteTextureId != null) {
			// Water_palette reaches CRAM in the same V-int as Normal_palette.
			pendingUnderwaterPalettes = palettes;
			pendingUnderwaterLine0 = normalLine0;
			underwaterUploadPending = true;
			return underwaterPaletteTextureId;
		}
        return composeUnderwaterPaletteTexture(palettes,normalLine0,null);
    }
    private Integer composeUnderwaterPaletteTexture(PaletteView[] palettes,PaletteView normalLine0,ScopedUnderwaterBasis publishedBasis) {
        var fadeMode = publishedBasis == null ? publishedPaletteFade.mode() : publishedBasis.fadeMode();
        int fadeSteps = publishedBasis == null ? publishedPaletteFade.steps() : publishedBasis.fadeSteps();
        int fadeMask = publishedBasis == null ? publishedPaletteFade.lineMask() : publishedBasis.fadeMask();
        boolean whiteFade = isWhitePaletteFade(fadeMode);
        // Public requests may exceed the native endpoint. Canonicalise before
        // storing a byte-sized derived-row key (256 must not alias step zero).
        if (whiteFade) fadeSteps = Math.min(PaletteFadePresentation.COLOR_STEPS, fadeSteps);
		int totalLines = RenderContext.getTotalPaletteLines();
		PaletteView underwaterLine0 = (palettes != null && palettes.length > 0) ? palettes[0] : null;
		ensureUnderwaterPaletteSourceScratch(totalLines);
		boolean hasDerivedDonorRow = false;
		for (int row = 0; row < totalLines; row++) {
			PaletteView source = palettes != null && row < palettes.length ? palettes[row] : null;
			byte rowCase = UNDERWATER_ROW_DIRECT;
			if (source == null) {
				rowCase = UNDERWATER_ROW_ABSENT;
				PaletteView appearance = appearancePaletteRows.isEmpty() ? null
						: appearancePaletteRows.underwaterRow(row, palettes, normalLine0, underwaterLine0);
				if (appearance != null) {
					source = appearance;
					rowCase = UNDERWATER_ROW_DIRECT;
				} else if (normalLine0 != null && underwaterLine0 != null) {
					source = RenderContext.getUnderwaterPaletteForEffectiveLine(row);
					if (source != null) {
						rowCase = UNDERWATER_ROW_DIRECT;
					} else {
						source = getRenderContextPaletteForEffectiveLine(row);
                        if(source==null)source=scopedPaletteForEffectiveLine(row);
					}
					if (source != null && rowCase != UNDERWATER_ROW_DIRECT) {
						rowCase = UNDERWATER_ROW_DERIVED;
						hasDerivedDonorRow = true;
					}
				} else {
                    source=scopedPaletteForEffectiveLine(row);
                    if(source!=null)rowCase=UNDERWATER_ROW_DIRECT;
                }
			}
			underwaterPaletteRowSources[row] = source;
			underwaterPaletteRowCases[row] = rowCase;
		}
		underwaterPaletteRowSources[totalLines] = hasDerivedDonorRow ? normalLine0 : null;
		underwaterPaletteRowSources[totalLines + 1] = hasDerivedDonorRow ? underwaterLine0 : null;

		underwaterPaletteContentKeyWriteIndex = 0;
		underwaterPaletteContentKeyChanged = !underwaterPaletteContentKeyValid;
		writeUnderwaterContentKeyInt(totalLines);
		writeUnderwaterContentKeyInt(displayColorProfile.ordinal());
		writeUnderwaterContentKeyInt(displayColorExpansion.ordinal());
        // White fades step the final CRAM colour, including a derived water target.
        // Their generation is therefore part of the key independently of raw RGB.
        writeUnderwaterContentKeyInt(whiteFade ? fadeMode.ordinal() : 0);
        writeUnderwaterContentKeyInt(whiteFade ? fadeSteps : 0);
        writeUnderwaterContentKeyInt(whiteFade ? fadeMask : 0);
		int sourceCount = totalLines + (hasDerivedDonorRow ? 2 : 0);
		for (int sourceIndex = 0; sourceIndex < sourceCount; sourceIndex++) {
			byte sourceTag = sourceIndex < totalLines
					? underwaterPaletteRowCases[sourceIndex]
					: (sourceIndex == totalLines ? UNDERWATER_BASE_NORMAL : UNDERWATER_BASE_SHIFTED);
			writeUnderwaterContentKeyByte(sourceTag);
			PaletteView source = (PaletteView) underwaterPaletteRowSources[sourceIndex];
			writeUnderwaterContentKeyByte(underwaterPaletteRecipeKey(source));
			if (source == null) {
				continue;
			}
			int rgbOffset = sourceIndex * 16 * 3;
			// Rows below totalLines are effective palette lines; a fade on one of them
			// (FadeIn_FromBlack also walks v_palette_water) transforms the composed
			// bytes, and the content key built from them changes with each fade step.
            boolean fadeRow = !whiteFade && sourceIndex < totalLines
                    && paletteFadeAffects(fadeMode, fadeMask, underwaterPaletteFadeLine(sourceIndex));
			for (int colorIndex = 0; colorIndex < 16; colorIndex++) {
				byte r = source.red(colorIndex);
				byte g = source.green(colorIndex);
				byte b = source.blue(colorIndex);
				if (fadeRow) {
                    int faded=PaletteFadePresentation.fadeRgb(Byte.toUnsignedInt(r),Byte.toUnsignedInt(g),Byte.toUnsignedInt(b),fadeMode,fadeSteps);
					r = (byte) (faded >>> 16);
					g = (byte) (faded >>> 8);
					b = (byte) faded;
				}
				underwaterPaletteSourceRgb[rgbOffset++] = r;
				underwaterPaletteSourceRgb[rgbOffset++] = g;
				underwaterPaletteSourceRgb[rgbOffset++] = b;
				writeUnderwaterContentKeyByte(r);
				writeUnderwaterContentKeyByte(g);
				writeUnderwaterContentKeyByte(b);
			}
		}
		pendingUnderwaterPaletteContentKeyLength = underwaterPaletteContentKeyWriteIndex;
		underwaterPaletteContentKeyChanged |= underwaterPaletteContentKeyLength
				!= pendingUnderwaterPaletteContentKeyLength;
        if(publishedBasis==null && (scopedUnderwaterBasis==null || !scopedUnderwaterBasis.matches(palettes,normalLine0,publishedPaletteFade)))
            scopedUnderwaterBasis=ScopedUnderwaterBasis.capture(palettes,normalLine0,publishedPaletteFade);
		if (!underwaterPaletteContentKeyChanged) {
            scopedUnderwaterDirty=false;underwaterCompositionLines=totalLines;
			return underwaterPaletteTextureId;
		}
		underwaterPaletteContentKeyValid = false;

		int ratioR = 256;
		int ratioG = 256;
		int ratioB = 256;
		if (hasDerivedDonorRow) {
			int normalOffset = totalLines * 16 * 3;
			int shiftedOffset = (totalLines + 1) * 16 * 3;
			long sumNR = 0;
			long sumNG = 0;
			long sumNB = 0;
			long sumUR = 0;
			long sumUG = 0;
			long sumUB = 0;
			for (int colorIndex = 1; colorIndex < 16; colorIndex++) {
				int colorOffset = colorIndex * 3;
				int nr = Byte.toUnsignedInt(underwaterPaletteSourceRgb[normalOffset + colorOffset]);
				int ng = Byte.toUnsignedInt(underwaterPaletteSourceRgb[normalOffset + colorOffset + 1]);
				int nb = Byte.toUnsignedInt(underwaterPaletteSourceRgb[normalOffset + colorOffset + 2]);
				if (UnderwaterPaletteTransform.eligible(nr, ng, nb)) {
					sumNR += nr;
					sumNG += ng;
					sumNB += nb;
					sumUR += Byte.toUnsignedInt(underwaterPaletteSourceRgb[shiftedOffset + colorOffset]);
					sumUG += Byte.toUnsignedInt(underwaterPaletteSourceRgb[shiftedOffset + colorOffset + 1]);
					sumUB += Byte.toUnsignedInt(underwaterPaletteSourceRgb[shiftedOffset + colorOffset + 2]);
				}
			}
			ratioR = UnderwaterPaletteTransform.ratioX256(sumNR, sumUR);
			ratioG = UnderwaterPaletteTransform.ratioX256(sumNG, sumUG);
			ratioB = UnderwaterPaletteTransform.ratioX256(sumNB, sumUB);
		}

		int bufferSize = 16 * totalLines * 4;
		ByteBuffer paletteBuffer = ensureUnderwaterPaletteUploadBuffer(bufferSize);
		paletteBuffer.clear();
		paletteBuffer.limit(bufferSize);
		for (int row = 0; row < totalLines; row++) {
			byte rowCase = underwaterPaletteRowCases[row];
			int rgbOffset = row * 16 * 3;
			if (rowCase == UNDERWATER_ROW_DERIVED) {
				int normalOffset = totalLines * 16 * 3;
				int shiftedOffset = (totalLines + 1) * 16 * 3;
                var rowWhiteMode = whiteFade && paletteFadeAffects(fadeMode, fadeMask, underwaterPaletteFadeLine(row))
                        ? fadeMode : PaletteFadePresentation.Mode.NONE;
				if (!underwaterDerivedRowKeyMatches(row, rgbOffset, normalOffset, shiftedOffset,
                        rowWhiteMode, fadeSteps)) {
					updateUnderwaterDerivedRow(row, rgbOffset, normalOffset, shiftedOffset,
							ratioR, ratioG, ratioB, rowWhiteMode, fadeSteps);
				}
				for (int value : underwaterDerivedRowRgba[row]) {
					paletteBuffer.put((byte) value);
				}
				continue;
			}
			for (int colorIndex = 0; colorIndex < 16; colorIndex++) {
				if (rowCase == UNDERWATER_ROW_ABSENT) {
					paletteBuffer.putInt(0);
					continue;
				}
				int r = Byte.toUnsignedInt(underwaterPaletteSourceRgb[rgbOffset++]);
				int g = Byte.toUnsignedInt(underwaterPaletteSourceRgb[rgbOffset++]);
				int b = Byte.toUnsignedInt(underwaterPaletteSourceRgb[rgbOffset++]);
                if (whiteFade && paletteFadeAffects(fadeMode, fadeMask, underwaterPaletteFadeLine(row))) {
                    int faded = PaletteFadePresentation.fadeRgb(r, g, b, fadeMode, fadeSteps);
                    r = (faded >>> 16) & 255;
                    g = (faded >>> 8) & 255;
                    b = faded & 255;
                }
				if (underwaterPaletteRowSources[row] instanceof NativeIntensityPalette) {
					int rgb = underwaterNativeShadowRgb(r, g, b);
					paletteBuffer.put((byte) (rgb >>> 16)).put((byte) (rgb >>> 8)).put((byte) rgb)
							.put((byte) (colorIndex == 0 ? 0 : 255));
				} else {
					writePaletteColor(paletteBuffer, r, g, b, colorIndex);
				}
			}
		}
		paletteBuffer.flip();

		if (underwaterPaletteTextureId == null) {
			underwaterPaletteTextureId = underwaterPaletteUploadOps.createTexture();
			underwaterPaletteUploadOps.configureTexture(underwaterPaletteTextureId);
		}
		underwaterPaletteUploadOps.uploadTexture(underwaterPaletteTextureId, totalLines, paletteBuffer);
		underwaterPaletteContentKeyLength = pendingUnderwaterPaletteContentKeyLength;
		underwaterPaletteContentKeyValid = true;
        scopedUnderwaterDirty=false;underwaterCompositionLines=totalLines;
		return underwaterPaletteTextureId;
	}

	private int underwaterPaletteFadeLine(int row) {
		return underwaterPaletteRowSources[row] instanceof NativeIntensityPalette intensity
				? intensity.nativeSourceLine() : appearancePaletteRows.fadeLine(row);
	}

	/** Zero is ordinary colour; shadow recipes retain their native source line (0..3). */
	private static byte underwaterPaletteRecipeKey(PaletteView source) {
		return (byte) (source instanceof NativeIntensityPalette intensity ? intensity.nativeSourceLine() + 1 : 0);
	}

	/** Convert the composed water colour, after its existing scaling and fade. */
	private int underwaterNativeShadowRgb(int r, int g, int b) {
		return DisplayColorConverter.toNativeShadowPackedRgb(PaletteFadePresentation.toGenesisChannel(r),
				PaletteFadePresentation.toGenesisChannel(g), PaletteFadePresentation.toGenesisChannel(b),
				displayColorProfile, displayColorExpansion);
	}

	private PaletteView getRenderContextPaletteForEffectiveLine(int effectiveLine) {
		for (RenderContext context : RenderContext.getDonorContexts()) {
			PaletteView palette = contextPaletteForEffectiveLine(context, effectiveLine);
			if (palette != null) return palette;
		}
		for (RenderContext context : RenderContext.getSidekickContexts()) {
			PaletteView palette = contextPaletteForEffectiveLine(context, effectiveLine);
			if (palette != null) return palette;
		}
		return null;
	}

	private PaletteView contextPaletteForEffectiveLine(RenderContext context, int effectiveLine) {
		int logicalLine = effectiveLine - context.getPaletteLineBase();
		return logicalLine >= 0 && logicalLine < RenderContext.LINES_PER_CONTEXT
				? context.getPalette(logicalLine)
				: null;
	}

	private void ensureUnderwaterPaletteSourceScratch(int totalLines) {
		int sourceSlots = totalLines + 2;
		if (underwaterPaletteRowSources == null || underwaterPaletteRowSources.length < sourceSlots) {
			underwaterPaletteRowSources = new Object[sourceSlots];
			underwaterPaletteRowCases = new byte[totalLines];
			underwaterPaletteSourceRgb = new byte[sourceSlots * 16 * 3];
		}
		if (underwaterDerivedRowKeys == null) {
			underwaterDerivedRowKeys = new byte[totalLines][];
			underwaterDerivedRowRgba = new int[totalLines][];
		} else if (underwaterDerivedRowKeys.length < totalLines) {
			underwaterDerivedRowKeys = Arrays.copyOf(underwaterDerivedRowKeys, totalLines);
			underwaterDerivedRowRgba = Arrays.copyOf(underwaterDerivedRowRgba, totalLines);
		}
	}

	private boolean underwaterDerivedRowKeyMatches(int row, int donorOffset,
			int normalOffset, int shiftedOffset, PaletteFadePresentation.Mode whiteMode, int whiteSteps) {
		byte[] key = underwaterDerivedRowKeys[row];
		if (key == null || key[0] != (byte) displayColorProfile.ordinal()
				|| key[1] != (byte) displayColorExpansion.ordinal()
                || key[2] != (byte) whiteMode.ordinal()
                || key[3] != (byte) (whiteMode == PaletteFadePresentation.Mode.NONE ? 0 : whiteSteps)
				|| key[4] != underwaterPaletteRecipeKey((PaletteView) underwaterPaletteRowSources[row])) {
			return false;
		}
		return underwaterDerivedRowKeyRangeMatches(key, 5, donorOffset)
				&& underwaterDerivedRowKeyRangeMatches(key, 5 + 16 * 3, normalOffset)
				&& underwaterDerivedRowKeyRangeMatches(key, 5 + 2 * 16 * 3, shiftedOffset);
	}

	private boolean underwaterDerivedRowKeyRangeMatches(byte[] key, int keyOffset, int sourceOffset) {
		for (int i = 0; i < 16 * 3; i++) {
			if (key[keyOffset + i] != underwaterPaletteSourceRgb[sourceOffset + i]) {
				return false;
			}
		}
		return true;
	}

	private void updateUnderwaterDerivedRow(int row, int donorOffset,
			int normalOffset, int shiftedOffset, int ratioR, int ratioG, int ratioB,
            PaletteFadePresentation.Mode whiteMode, int whiteSteps) {
		byte[] key = underwaterDerivedRowKeys[row];
		if (key == null) {
			key = new byte[5 + 3 * 16 * 3];
			underwaterDerivedRowKeys[row] = key;
			underwaterDerivedRowRgba[row] = new int[16 * 4];
		}
		key[0] = (byte) displayColorProfile.ordinal();
		key[1] = (byte) displayColorExpansion.ordinal();
        key[2] = (byte) whiteMode.ordinal();
        key[3] = (byte) (whiteMode == PaletteFadePresentation.Mode.NONE ? 0 : whiteSteps);
		key[4] = underwaterPaletteRecipeKey((PaletteView) underwaterPaletteRowSources[row]);
		System.arraycopy(underwaterPaletteSourceRgb, donorOffset, key, 5, 16 * 3);
		System.arraycopy(underwaterPaletteSourceRgb, normalOffset, key, 5 + 16 * 3, 16 * 3);
		System.arraycopy(underwaterPaletteSourceRgb, shiftedOffset, key, 5 + 2 * 16 * 3, 16 * 3);

		int[] rgba = underwaterDerivedRowRgba[row];
		for (int colorIndex = 0; colorIndex < 16; colorIndex++) {
			int colorOffset = donorOffset + colorIndex * 3;
			int r = UnderwaterPaletteTransform.scale(Byte.toUnsignedInt(underwaterPaletteSourceRgb[colorOffset]), ratioR);
			int g = UnderwaterPaletteTransform.scale(Byte.toUnsignedInt(underwaterPaletteSourceRgb[colorOffset + 1]), ratioG);
			int b = UnderwaterPaletteTransform.scale(Byte.toUnsignedInt(underwaterPaletteSourceRgb[colorOffset + 2]), ratioB);
            if (whiteMode != PaletteFadePresentation.Mode.NONE) {
                int faded = PaletteFadePresentation.fadeRgb(r, g, b, whiteMode, whiteSteps);
                r = (faded >>> 16) & 255;
                g = (faded >>> 8) & 255;
                b = faded & 255;
            }
			int rgbaOffset = colorIndex * 4;
			if (underwaterPaletteRowSources[row] instanceof NativeIntensityPalette) {
				int rgb = underwaterNativeShadowRgb(r, g, b);
				rgba[rgbaOffset] = (rgb >>> 16) & 255;
				rgba[rgbaOffset + 1] = (rgb >>> 8) & 255;
				rgba[rgbaOffset + 2] = rgb & 255;
			} else {
				DisplayColorConverter.writeRgbBytes(r, g, b, displayColorProfile, displayColorExpansion, rgba, rgbaOffset);
			}
			rgba[rgbaOffset + 3] = colorIndex == 0 ? 0 : 255;
		}
		underwaterDerivedRowRecomputeCount++;
	}

	int getUnderwaterDerivedRowRecomputeCount() {
		return underwaterDerivedRowRecomputeCount;
	}

	private void writeUnderwaterContentKeyInt(int value) {
		writeUnderwaterContentKeyByte((byte) (value >>> 24));
		writeUnderwaterContentKeyByte((byte) (value >>> 16));
		writeUnderwaterContentKeyByte((byte) (value >>> 8));
		writeUnderwaterContentKeyByte((byte) value);
	}

	private void writeUnderwaterContentKeyByte(byte value) {
		if (underwaterPaletteContentKey == null) {
			underwaterPaletteContentKey = new byte[256];
		} else if (underwaterPaletteContentKeyWriteIndex == underwaterPaletteContentKey.length) {
			underwaterPaletteContentKey = Arrays.copyOf(underwaterPaletteContentKey,
					underwaterPaletteContentKey.length * 2);
		}
		if (underwaterPaletteContentKeyWriteIndex >= underwaterPaletteContentKeyLength
				|| underwaterPaletteContentKey[underwaterPaletteContentKeyWriteIndex] != value) {
			underwaterPaletteContentKeyChanged = true;
		}
		underwaterPaletteContentKey[underwaterPaletteContentKeyWriteIndex++] = value;
	}


	PaletteView objectVisualPalette(int line) {
		PaletteView palette =
				line >= 0 && line < lastCachedPaletteLines.length
						? lastCachedPaletteLines[line]
						: null;
		return palette != null ? palette : getRenderContextPaletteForEffectiveLine(line);
	}

}
