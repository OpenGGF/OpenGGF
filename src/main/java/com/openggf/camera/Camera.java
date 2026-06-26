package com.openggf.camera;

import com.openggf.configuration.DeadzoneMode;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.PhysicsFeatureSet;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.rewind.snapshot.CameraSnapshot;
import com.openggf.sprites.Sprite;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Tails;

public class Camera implements RewindSnapshottable<CameraSnapshot> {
	private short x = 0;
	private short y = 0;

	private short minX;
	private short minY;
	private short maxX;
	private short maxY;

	// Screen shake offsets (ROM: applied to Camera_X_pos_copy and Camera_Y_pos_copy)
	// These offsets affect both FG tiles and sprites to create unified screen shake.
	private short shakeOffsetX = 0;
	private short shakeOffsetY = 0;

	// Target boundaries for smooth easing (ROM: Camera_Max_Y_pos_target, etc.)
	private short minXTarget;
	private short minYTarget;
	private short maxXTarget;
	private short maxYTarget;

	// ROM uses 2 pixels per frame for boundary easing
	private static final short BOUNDARY_EASE_STEP = 2;

	// Flag indicating boundary is actively changing (ROM: Camera_Max_Y_Pos_Changing)
	// When true, normal vertical scroll rules may be modified
	private boolean maxYChanging = false;

	// ROM camera/boundary ordering (S1 DeformLayers (REV01).asm:16-18): ScrollHoriz
	// + ScrollVertical (camera move + clamp to the prior-frame v_limitbtm2) run
	// BEFORE DynamicLevelEvents (zone handler + bottom-boundary easing).
	// LevelFrameStep mirrors this: updatePosition() runs before the zone event
	// handler + updateBoundaryEasing(). So updatePosition() clamps to the maxY left
	// by the PREVIOUS frame's easing, and the airborne +8 boundary acceleration
	// applied by this frame's updateBoundaryEasing() (which reads the POST-scroll
	// camera, ROM v_screenposy) reaches the camera on the NEXT frame — matching ROM
	// without any explicit one-frame deferral state.

	// ROM: Horiz_scroll_delay_val - horizontal scroll delay counter
	// When > 0, horizontal scroll uses position history while vertical scroll continues normally
	private int horizScrollDelayFrames = 0;

	// Full camera freeze (both X and Y) - used for death, cutscenes, etc.
	// This is separate from horizScrollDelayFrames which only affects horizontal scroll.
	private boolean frozen = false;
	private boolean deferHorizontalBoundaryClampOnce = false;
	private boolean deferMaxYWriteUntilAfterUpdate = false;
	private short deferredMaxYValue = 0;

	// ROM: Level_started_flag.
	// Used by HUD/start-state flow and intro/cutscene sequencing.
	// This flag does NOT freeze camera scroll; use `frozen` for camera suppression.
	private boolean levelStarted = true;

	// ROM: Vertical wrapping — coordinates wrap modularly when top boundary is negative.
	// S1 LZ3/SBZ2: range 0x800 (DeformLayers.asm lines 542-580)
	// S3K zones with negative minY: range = level height (e.g. 0x1000 for MGZ1's 32-row map)
	private boolean verticalWrapEnabled = false;
	private int verticalWrapRange = 0x800;     // Default S1 range; overridden per-level
	private int verticalWrapMask = 0x7FF;      // Range - 1
	public static final int VERTICAL_WRAP_RANGE = 0x800;  // S1 default; referenced by LevelManager and GraphicsManager
	private static final int VERTICAL_WRAP_BG_MASK = 0x3FF; // AND mask for BG Y
	// Tracks whether a wrap occurred this frame, and the delta applied
	private boolean lastFrameWrapped = false;
	private short wrapDeltaY = 0;

	private AbstractPlayableSprite focusedSprite;

	private short width;
	private short height;
	private DeadzoneMode deadzoneMode = DeadzoneMode.PROPORTIONAL;

	// ROM: Camera_Y_pos_bias - vertical position target for camera centering
	// Default is (224/2)-16 = 96 (0x60). Used as center point for scroll windows.
	private static final short DEFAULT_Y_BIAS = 96;

	// ROM: Look up target bias: 0xC8 (200) - shifts camera up to show more above Sonic
	private static final short LOOK_UP_BIAS = (short) 0xC8;

	// ROM: Look down target bias: 8 - shifts camera down to show more below Sonic
	private static final short LOOK_DOWN_BIAS = 8;

	// ROM: Camera_Y_pos_bias - dynamic bias that can change during gameplay
	// (looking up/down, spindash, etc). Starts at 96.
	private short yPosBias = DEFAULT_Y_BIAS;

	// ROM: Airborne window is ±0x20 (32) around the bias
	private static final short AIRBORNE_WINDOW_HALF = 32;

	// ROM: Inertia threshold for fast scroll (0x800 = 2048)
	private static final short FAST_SCROLL_INERTIA_THRESHOLD = 0x800;

	// ROM: Maximum per-frame camera step used by the fast vertical paths and by
	// the horizontal catch-up clamp in ScrollHoriz / MoveCameraX.
	// S1/S2: 16 (0x10) pixels/frame.
	// S3K:   24 (0x18) pixels/frame.
	// Set per-game via setFastScrollCap().
	private static final short DEFAULT_FAST_SCROLL_CAP = 16;
	private short fastScrollCap = DEFAULT_FAST_SCROLL_CAP;

	// ROM S1 (FixBugs=0): the leftward horizontal camera move is UNCAPPED — only the
	// rightward move caps at fastScrollCap. The leftward cap is gated behind
	// `if FixBugs` (FixBugs=0 in the shipped ROM), so SH_MoveCameraLeft runs straight
	// to .moveLeft and adds the full (possibly >16px) offset
	// (docs/s1disasm/_inc/ScrollHoriz & ScrollVertical.asm:59-99). S2 (s2.asm:18102-
	// 18105) and S3K (sonic3k.asm:38403-38406) cap BOTH directions, so this stays
	// false for them. Set per-game from PhysicsFeatureSet.uncappedLeftwardHorizontalScroll.
	private boolean uncappedLeftwardHorizontalScroll = false;

	// ROM: Fast_V_scroll_flag. Moving solids request this for the current frame
	// when the player is standing on them, so grounded vertical follow uses the
	// fast cap even if the player's own ground speed is low.
	private boolean fastVerticalScrollRequested = false;

	public Camera() {
		this(GameServices.configuration());
	}

	public Camera(SonicConfigurationService configService) {
		width = configService.getShort(SonicConfiguration.SCREEN_WIDTH_PIXELS);
		height = configService.getShort(SonicConfiguration.SCREEN_HEIGHT_PIXELS);
		deadzoneMode = DeadzoneMode.parse(
				configService.getString(SonicConfiguration.WIDESCREEN_DEADZONE_MODE));
	}

	public void updatePosition() {
		updatePosition(false);
	}

	public void updatePosition(boolean force) {
		if (force) {
			// Position camera using ROM's level-load formula:
			//   v_screenposx = MainCharacter.x_pos - $A0  (subi.w #160,d1)
			//   v_screenposy = MainCharacter.y_pos - $60  (subi.w  #96,d0)
			// then clamp to the level bounds. References: s1disasm
			// _inc/LevelSizeLoad & BgScrollSpeed.asm:111,124; s2.asm:14787,14798;
			// sonic3k.asm:38241. ROM places the sprite at screen-x=160 (right edge
			// of the 144-160 horizontal scroll deadzone), not the deadzone
			// midpoint at 152.
			x = (short) (focusedSprite.getCentreX() - DeadzoneGeometry.rightEdge(width));
			y = (short) (focusedSprite.getCentreY() - 96);

			// Apply bounds clamping.
			// If max < min, treat the upper bound as wrapped/unbounded for this signed domain.
			// SCZ ObjB2 writes Camera_Max_X_pos = Camera_X_pos - $40, which can transiently
			// produce max < min at low X in this engine representation.
			x = clampAxisWithWrap(x, minX, maxX);
			y = clampAxisWithWrap(y, minY, maxY);
			fastVerticalScrollRequested = false;
			applyDeferredMaxYWrite();
			return;
		}

		// Full camera freeze (death, cutscenes) - don't update X or Y at all
		if (frozen) {
			fastVerticalScrollRequested = false;
			applyDeferredMaxYWrite();
			return;
		}

		// ROM behavior: Horiz_scroll_delay_val only affects horizontal scrolling.
		// Vertical scrolling (ScrollVerti) always uses current position and runs normally.
		// See s2.asm ScrollHoriz (line ~18009) vs ScrollVerti (line ~18112).

		// Horizontal scroll - may use position history if delay is active
		boolean deferHorizontalClampThisFrame = deferHorizontalBoundaryClampOnce;
		deferHorizontalBoundaryClampOnce = false;
		x = computeNextHorizontalCameraX(true, !deferHorizontalClampThisFrame);

		// Vertical scroll - always uses current position (ROM: ScrollVerti has no delay)
		// ROM: d0 = (v_player+obY).w - (v_screenposy).w
		// When vertical wrapping is active, compute using modular arithmetic to handle
		// cases where Sonic and camera are in different wrap periods (e.g. Sonic's Y was
		// updated by ground collision across the wrap boundary between frames).
		short focusedSpriteRealY;
		if (verticalWrapEnabled) {
			int diff = (int) focusedSprite.getCentreY() - (int) y;
			diff = ((diff % verticalWrapRange) + verticalWrapRange) % verticalWrapRange;
			if (diff > verticalWrapRange / 2) {
				diff -= verticalWrapRange;
			}
			focusedSpriteRealY = (short) diff;
		} else {
			focusedSpriteRealY = (short) (focusedSprite.getCentreY() - y);
		}

		short yBeforeVerticalScroll = y;

		// ROM: s2.asm:18121-18132 - Rolling height compensation
		// When rolling, Sonic's center shifts down by ~5px due to height change.
		// Subtract 5 from the Y delta to prevent camera jolt.
		// Tails is 4 pixels shorter, so only subtract 1 for Tails.
		if (focusedSprite.getRolling()) {
			focusedSpriteRealY -= 5;
			if (focusedSprite instanceof Tails) {
				focusedSpriteRealY += 4; // Net: subtract 1 for Tails
			}
		}

		// Vertical scroll logic (ROM: ScrollVerti)
		if (focusedSprite.getAir()) {
			// ROM: Airborne uses ±0x20 window around bias
			// Upper bound: bias - 32, Lower bound: bias + 32
			short upperBound = (short) (yPosBias - AIRBORNE_WINDOW_HALF);
			short lowerBound = (short) (yPosBias + AIRBORNE_WINDOW_HALF);
			if (focusedSpriteRealY < upperBound) {
				short difference = (short) (focusedSpriteRealY - upperBound);
				if (difference < -fastScrollCap) {
					y -= fastScrollCap;
				} else {
					y += difference;
				}
			} else if (focusedSpriteRealY >= lowerBound) {
				short difference = (short) (focusedSpriteRealY - lowerBound);
				if (difference > fastScrollCap) {
					y += fastScrollCap;
				} else {
					y += difference;
				}
			}
		} else {
			// ROM: s2.asm:18150-18195 - Grounded vertical scroll
			// Uses bias state and inertia (ground speed), NOT ySpeed
			short difference = (short) (focusedSpriteRealY - yPosBias);

			if (difference != 0) {
				// ROM: .decideScrollType - choose scroll cap based on bias and inertia
				short tolerance;
				if (yPosBias != DEFAULT_Y_BIAS) {
					// ROM: .doScroll_slow - bias is not normal (looking up/down)
					// Use 2px cap
					tolerance = 2;
				} else {
					// Bias is normal (96) - check inertia for medium vs fast
					short absInertia = (short) Math.abs(focusedSprite.getGSpeed());
					if (fastVerticalScrollRequested || absInertia >= FAST_SCROLL_INERTIA_THRESHOLD) {
						// ROM: .doScroll_fast - player moving very fast on ground
						// S2: 16px cap, S3K: 24px cap
						tolerance = fastScrollCap;
					} else {
						// ROM: .doScroll_medium - normal ground movement
						// Use 6px cap
						tolerance = 6;
					}
				}

				// Apply scroll with capping
				if (difference > 0) {
					// Scroll down
					if (difference > tolerance) {
						y += tolerance;
					} else {
						y += difference;
					}
				} else {
					// Scroll up (difference is negative)
					if (difference < -tolerance) {
						y -= tolerance;
					} else {
						y += difference;
					}
				}
			}
			// else: ROM: .doNotScroll - player is at bias, no scroll needed
		}

		// ROM: LZ3/SBZ2 vertical wrapping (DeformLayers.asm lines 542-580)
		// When wrapping is active, coordinate masking replaces normal Y clamping.
		lastFrameWrapped = false;
		wrapDeltaY = 0;
		if (verticalWrapEnabled) {
			// Upward wrap: camera Y at or below -256 (0xFF00 signed)
			// ROM: cmpi.w #-$100,d1 / bgt.s .noupwrap — wraps when d1 <= -$100
			if (y <= -0x100) {
				short oldY = y;
				y = (short) (y & verticalWrapMask);
				wrapFocusedSpriteYPositionWord();
				lastFrameWrapped = true;
				wrapDeltaY = (short) (y - oldY);
			}
			// Downward wrap: camera Y reached bottom boundary
			// ROM: cmp.w (Camera_Max_Y_pos).w,d1 / blt.s .nodownwrap / sub.w d0,y
			else if (y >= verticalWrapRange) {
				short oldY = y;
				y = (short) (y - verticalWrapRange);
				wrapFocusedSpriteYPositionWord();
				lastFrameWrapped = true;
				wrapDeltaY = (short) (y - oldY);
			}
		}

		// Clamp to boundaries (ROM: ScrollHoriz lines 18077-18092, ScrollVerti similar)
		if (!deferHorizontalClampThisFrame) {
			x = clampAxisWithWrap(x, minX, maxX);
		}
		// ROM: After a vertical wrap, DeformLayers.asm branches directly to loc_6724
		// (the store), skipping the normal boundary clamp. This is critical because
		// after wrapping from e.g. -260 to 1788, clamping to maxY could force the
		// camera to a different position than Sonic was wrapped to.
		// Normal (non-wrap) frames still clamp, which handles pit death in SBZ2
		// where v_limitbtm2=$510 constrains the camera even though wrapping is active.
		// ROM: ScrollVertical's SV_OnGround / SV_NotInAir path consults
		// f_bgscrollvert (docs/s1disasm/_inc/ScrollHoriz & ScrollVertical.asm:148-149,
		// 157-158): when the bottom level boundary moved on the PREVIOUS frame, it
		// branches to SV_BottomBoundaryMoving (line 210) which forces d0=0 and falls
		// through SV_SweetSpot -> SV_BottomBoundary (line 259), clamping the camera to
		// v_limitbtm2 EVEN when Sonic is exactly at the sweet spot and the normal
		// grounded scroll produced no movement.
		//
		// ROM order (DeformLayers (REV01).asm:16-18): ScrollVertical runs BEFORE
		// DynamicLevelEvents, so it clamps to the v_limitbtm2 left by the PREVIOUS
		// frame's DynamicLevelEvents, and consults the f_bgscrollvert that frame set.
		// LevelFrameStep mirrors this: updatePosition() (ScrollVertical) runs before
		// the zone event handler + updateBoundaryEasing() (DynamicLevelEvents). So at
		// this point maxY already holds the prior-frame boundary and maxYChanging
		// mirrors the prior-frame f_bgscrollvert — both ROM-correct without any extra
		// one-frame deferral. (The airborne +8 boundary acceleration applied by
		// updateBoundaryEasing later this frame therefore reaches the camera on the
		// NEXT frame, matching ROM — S1 MZ1 f2101.) The GHZ2 f3349 rising-boundary
		// case is covered because maxYChanging keeps the clamp live on a sweet-spot
		// frame whose grounded scroll produced no movement.
		if (!lastFrameWrapped && (y != yBeforeVerticalScroll || maxYChanging)) {
			y = clampAxisWithWrap(y, minY, maxY);
		}
		fastVerticalScrollRequested = false;
		applyDeferredMaxYWrite();
	}

	private void applyDeferredMaxYWrite() {
		if (!deferMaxYWriteUntilAfterUpdate) {
			return;
		}
		setMaxY(deferredMaxYValue);
		deferMaxYWriteUntilAfterUpdate = false;
	}

	private void wrapFocusedSpriteYPositionWord() {
		if (focusedSprite == null) {
			return;
		}
		// ROM masks only the y_pos word when Screen_Y_wrap_value is active
		// (sonic3k.asm:21989-21992, 26233-26236; MGZ sets #$FFF at
		// sonic3k.asm:102200). Preserve y_sub just like a 68000 word write.
		focusedSprite.setCentreYPreserveSubpixel((short) (focusedSprite.getCentreY() & verticalWrapMask));
	}

	/**
	 * Predicts the horizontal camera position that {@link #updatePosition()} will
	 * commit on this frame without consuming scroll-delay history.
	 * This lets event scripts reason about end-of-frame camera thresholds while
	 * preserving the actual camera state for the later camera step.
	 */
	public short previewNextX() {
		if (focusedSprite == null || frozen) {
			return x;
		}
		return computeNextHorizontalCameraX(false, true);
	}

	private short computeNextHorizontalCameraX(boolean consumeDelayState, boolean applyBoundaryClamp) {
		short nextX = x;
		short focusedSpriteRealX;
		if (horizScrollDelayFrames > 0) {
			// ROM: MoveCameraX stores the delay count in the high byte of
			// H_scroll_frame_offset and subtracts $100 before sampling Pos_table.
			// Our history buffer is also one frame behind by the time camera scroll
			// runs, so delay N maps to the buffered position from N-1 frames ago.
			int historyIndex = Math.max(0, Math.min(horizScrollDelayFrames - 1, 63));
			focusedSpriteRealX = (short) (focusedSprite.getCentreX(historyIndex) - nextX);
			if (consumeDelayState) {
				horizScrollDelayFrames--;
			}
		} else {
			focusedSpriteRealX = (short) (focusedSprite.getCentreX() - nextX);
		}

		short cameraStepCap = fastScrollCap;

		// Horizontal scroll logic (ROM: ScrollHoriz / MoveCameraX).
		int deadzoneLeft = DeadzoneGeometry.leftEdge(width, deadzoneMode);
		int deadzoneRight = DeadzoneGeometry.rightEdge(width);
		if (focusedSpriteRealX < deadzoneLeft) {
			short difference = (short) (focusedSpriteRealX - deadzoneLeft);
			// ROM S1 leaves the leftward move uncapped (FixBugs=0); S2/S3K cap it.
			if (!uncappedLeftwardHorizontalScroll && difference < -cameraStepCap) {
				nextX -= cameraStepCap;
			} else {
				nextX += difference;
			}
		} else if (focusedSpriteRealX > deadzoneRight) {
			short difference = (short) (focusedSpriteRealX - deadzoneRight);
			if (difference > cameraStepCap) {
				nextX += cameraStepCap;
			} else {
				nextX += difference;
			}
		}

		return applyBoundaryClamp ? clampAxisWithWrap(nextX, minX, maxX) : nextX;
	}

	/**
	 * Keeps newly written horizontal bounds available to object/player logic while
	 * delaying the visible camera clamp until the following camera step.
	 */
	public void deferHorizontalBoundaryClampOnce() {
		deferHorizontalBoundaryClampOnce = true;
	}

	private short clampAxisWithWrap(short value, short min, short max) {
		if (max < min) {
			return value < min ? min : value;
		}
		if (value < min) {
			return min;
		}
		if (value > max) {
			return max;
		}
		return value;
	}

	/**
	 * Sets horizontal scroll delay frames (ROM: Horiz_scroll_delay_val).
	 * When delay > 0, horizontal scroll uses position history while vertical scroll
	 * continues normally. This matches ROM behavior where ScrollHoriz checks
	 * Horiz_scroll_delay_val but ScrollVerti does not.
	 *
	 * @param delayFrames Number of frames to delay horizontal scroll (0 to clear)
	 */
	public void setHorizScrollDelay(int delayFrames) {
		this.horizScrollDelayFrames = delayFrames;
	}

	/**
	 * @return Current horizontal scroll delay frames remaining
	 */
	public int getHorizScrollDelay() {
		return horizScrollDelayFrames;
	}

	/**
	 * Sets full camera freeze (both X and Y).
	 * Use this for death, cutscenes, boss arenas, etc. where the camera should
	 * completely stop following the player.
	 *
	 * For spindash-style horizontal-only delay, use setHorizScrollDelay() instead.
	 *
	 * @param frozen true to freeze camera, false to unfreeze
	 */
	public void setFrozen(boolean frozen) {
		this.frozen = frozen;
		// When unfreezing, also clear any horizontal delay
		if (!frozen) {
			this.horizScrollDelayFrames = 0;
		}
	}

	/**
	 * @return true if camera is fully frozen (both X and Y)
	 */
	public boolean getFrozen() {
		return frozen;
	}

	/**
	 * Sets the level-started flag (ROM: Level_started_flag).
	 * This flag is used by level/HUD flow and intro state logic; it does not
	 * directly control camera scrolling.
	 *
	 * @param levelStarted true when level is considered started
	 */
	public void setLevelStarted(boolean levelStarted) {
		this.levelStarted = levelStarted;
	}

	/**
	 * @return true if Level_started_flag is set
	 */
	public boolean isLevelStarted() {
		return levelStarted;
	}

	/**
	 * Updates boundary easing - call once per frame.
	 * ROM behavior from RunDynamicLevelEvents (s2.asm:20297-20332):
	 * - Eases maxY toward target at 2px/frame (or 8px if accelerated)
	 * - When decreasing: if camera Y > target, snap maxY to camera Y first, then subtract
	 * - When increasing: if camera Y+8 >= maxY AND player airborne, use 4x speed (8px/frame)
	 * - Sets maxYChanging flag while boundary is transitioning
	 */
	public void updateBoundaryEasing() {
		maxYChanging = false;

		// Ease maxY toward target (ROM: s2.asm:20303-20332)
		if (maxY != maxYTarget) {
			short step = BOUNDARY_EASE_STEP; // d1 = 2
			short diff = (short) (maxYTarget - maxY);

			if (diff < 0) {
				// Decreasing max Y (target < current) - ROM lines 20308-20316
				step = (short) -BOUNDARY_EASE_STEP; // neg.w d1

				// If camera Y > target, snap maxY to camera Y first
				if (y > maxYTarget) {
					maxY = (short) (y & 0xFFFE); // Align to even pixels
				}
				// Always add step (subtract 2) after potential snap
				maxY += step;
			} else {
				// Increasing max Y (target > current) - ROM lines 20320-20331.
				// Boundary moving DOWN: check for the airborne acceleration
				// (ROM DynamicLevelEvents.asm:35-49). This reads the camera (y) and
				// the player airborne bit; because LevelFrameStep now runs
				// updateBoundaryEasing() AFTER updatePosition() (matching ROM
				// DynamicLevelEvents running after ScrollVertical), y here is the
				// POST-scroll camera, exactly as ROM reads v_screenposy. The +8 step
				// therefore reaches next frame's camera clamp, not this frame's.
				if (focusedSprite != null && (y + 8) >= maxY && focusedSprite.getAir()) {
					step = (short) (BOUNDARY_EASE_STEP * 4); // 8 pixels/frame
				}
				maxY += step;
			}

			// Clamp to target if we overshot
			if ((diff > 0 && maxY > maxYTarget) || (diff < 0 && maxY < maxYTarget)) {
				maxY = maxYTarget;
			}

			maxYChanging = true;
		}

		// Ease minY toward target (simple 2px/frame, no acceleration)
		if (minY != minYTarget) {
			short diff = (short) (minYTarget - minY);
			if (diff > 0) {
				minY += Math.min(diff, BOUNDARY_EASE_STEP);
			} else {
				minY += Math.max(diff, -BOUNDARY_EASE_STEP);
			}
		}

		// Ease maxX toward target
		if (maxX != maxXTarget) {
			short diff = (short) (maxXTarget - maxX);
			if (diff > 0) {
				maxX += Math.min(diff, BOUNDARY_EASE_STEP);
			} else {
				maxX += Math.max(diff, -BOUNDARY_EASE_STEP);
			}
		}

		// Ease minX toward target
		if (minX != minXTarget) {
			short diff = (short) (minXTarget - minX);
			if (diff > 0) {
				minX += Math.min(diff, BOUNDARY_EASE_STEP);
			} else {
				minX += Math.max(diff, -BOUNDARY_EASE_STEP);
			}
		}
	}

	/**
	 * Returns true if maxY is currently easing toward its target.
	 * ROM: Camera_Max_Y_Pos_Changing flag
	 */
	public boolean isMaxYChanging() {
		return maxYChanging;
	}

	public boolean isOnScreen(Sprite sprite) {
		int xLower = x;
		int yLower = y;
		int xUpper = x + width;
		int yUpper = y + height;
		int spriteX = sprite.getX();
		int spriteY = sprite.getY();
		return spriteX >= xLower && spriteY >= yLower && spriteX <= xUpper
				&& spriteY <= yUpper;
	}

	/**
	 * Computes the current-frame BuildSprites visibility that feeds
	 * {@code render_flags.on_screen}.
	 * <p>S3K {@code Render_Sprites} (sonic3k.asm:36336) does:
	 * <pre>
	 *   d1 = (y_pos - Camera_Y) + height_pixels  ; height_pixels = 0x18 = 24
	 *   d1 &= Screen_Y_wrap_value                ; default 0xFFFF (no mask)
	 *   if d1 &gt;= 2*height_pixels + 224:           ; threshold = 272
	 *       off-screen
	 * </pre>
	 * With the default {@code Screen_Y_wrap_value = 0xFFFF}, this is equivalent
	 * to {@code relY in [-24, 248)} — i.e., Y margin = {@code height_pixels = 24}
	 * symmetrically, NOT 32.
	 * <p>S1/S2 don't have a {@code Screen_Y_wrap_value} mechanism and the ROM
	 * routines use slightly different margins. Gate the S3K-specific 24-margin
	 * via {@link com.openggf.game.PhysicsFeatureSet#useScreenYWrapValueForVisibility()}
	 * so existing S1/S2 traces keep their 32-margin behaviour.
	 *
	 * <p><b>Vertical-wrap windowing (the off-screen-flag Y boundary):</b> the ROM
	 * does not mask the raw {@code relY} — it first biases by the low margin (so a
	 * sprite a few pixels above the camera top stays inside the window) and only
	 * then masks into the VDP plane wrap range before a single unsigned-range
	 * compare. Masking the raw signed {@code relY} (as an earlier version did)
	 * mapped a small negative {@code relY} to a huge unsigned value and wrongly
	 * reported off-screen one frame early. The two games:
	 * <ul>
	 *   <li>S2 {@code BuildSprites_ApproxYCheck} (s2.asm:30597-30605):
	 *       {@code d2 = (y_pos - Camera_Y_pos_copy + sprite_top_boundary) & $7FF};
	 *       on-screen iff {@code (sprite_top_boundary-32) <= d2 < (sprite_top_boundary+screen_height+32)}
	 *       with {@code sprite_top_boundary=$80}, {@code screen_height=224}. This is
	 *       algebraically {@code ((relY + 32) & $7FF) < screen_height + 64}. The mask
	 *       is the literal VDP {@code $7FF}, independent of the level's vertical
	 *       wrap range. S1 uses the same routine/margin.</li>
	 *   <li>S3K {@code Render_Sprites} (sonic3k.asm:36356-36364):
	 *       {@code d1 = ((y_pos - Camera_Y_pos_copy) + height_pixels) & Screen_Y_wrap_value};
	 *       off-screen iff {@code d1 >= 2*height_pixels + 224}. That is
	 *       {@code ((relY + margin) & Screen_Y_wrap_value) < screen_height + 2*margin}
	 *       with {@code margin = height_pixels = 24}, masked by the level-configured
	 *       {@code Screen_Y_wrap_value}.</li>
	 * </ul>
	 * Both reduce to {@code ((relY + yMargin) & mask) < height + 2*yMargin}.
	 */
	public boolean isVisibleForRenderFlag(AbstractPlayableSprite sprite) {
		int widthPixels = sprite.getRenderFlagWidthPixels();
		int relX = sprite.getRenderCentreX() - x;
		if (relX + widthPixels < 0 || relX - widthPixels >= width) {
			return false;
		}
		int relY = sprite.getRenderCentreY() - y;
		com.openggf.game.PhysicsFeatureSet fs = sprite.getPhysicsFeatureSet();
		boolean useS3kMargin = fs != null && fs.useScreenYWrapValueForVisibility();
		int yMargin = useS3kMargin ? widthPixels : 32;
		if (verticalWrapEnabled) {
			// ROM-accurate wrap window: bias by the low margin BEFORE masking, then
			// one unsigned-range compare. S2/S1 BuildSprites masks with the literal
			// VDP $7FF (s2.asm:30601); S3K masks with Screen_Y_wrap_value
			// (sonic3k.asm:36360, modelled by verticalWrapMask).
			int mask = useS3kMargin ? verticalWrapMask : 0x7FF;
			int wrapped = (relY + yMargin) & mask;
			return wrapped < height + 2 * yMargin;
		}
		return relY >= -yMargin && relY < height + yMargin;
	}

	public void setFocusedSprite(AbstractPlayableSprite sprite) {
		this.focusedSprite = sprite;
		x = sprite.getX();
		y = sprite.getY();
	}

	public AbstractPlayableSprite getFocusedSprite() {
		return focusedSprite;
	}

	public short getX() {
		return x;
	}

	public void setX(short x) {
		this.x = x;
	}

	public short getY() {
		return y;
	}

	public void setY(short y) {
		this.y = y;
	}

	/**
	 * Sets the screen shake offsets (ROM: applied to Camera_X_pos_copy and Camera_Y_pos_copy).
	 * These offsets are used by the rendering system to shake both foreground tiles and sprites.
	 *
	 * @param x horizontal shake offset in pixels
	 * @param y vertical shake offset in pixels
	 */
	public void setShakeOffsets(int x, int y) {
		this.shakeOffsetX = (short) x;
		this.shakeOffsetY = (short) y;
	}

	/**
	 * @return Current horizontal shake offset in pixels
	 */
	public short getShakeOffsetX() {
		return shakeOffsetX;
	}

	/**
	 * @return Current vertical shake offset in pixels
	 */
	public short getShakeOffsetY() {
		return shakeOffsetY;
	}

	/**
	 * @return Camera X position with shake offset applied (for rendering)
	 */
	public short getXWithShake() {
		return (short) (x + shakeOffsetX);
	}

	/**
	 * @return Camera Y position with shake offset applied (for rendering)
	 */
	public short getYWithShake() {
		return (short) (y + shakeOffsetY);
	}

	public short getWidth() {
		return width;
	}

	public short getHeight() {
		return height;
	}

	public short getMinX() {
		return minX;
	}

	/**
	 * Sets minX immediately (both current and target).
	 * Use setMinXTarget() for smooth easing.
	 */
	public void setMinX(short minX) {
		this.minX = minX;
		this.minXTarget = minX;
	}

	/**
	 * Sets minX target for smooth easing.
	 * Current minX will ease toward this value at 2px/frame.
	 */
	public void setMinXTarget(short minXTarget) {
		this.minXTarget = minXTarget;
	}

	public short getMinXTarget() {
		return minXTarget;
	}

	public short getMinY() {
		return minY;
	}

	/**
	 * Sets minY immediately (both current and target).
	 * Use setMinYTarget() for smooth easing.
	 *
	 * ROM: In S1, negative minY (e.g. LZ3 top=0xFF00=-256) indicates vertical
	 * wrapping (DeformLayers.asm lines 542-580). S3K zones can have negative
	 * minY without wrapping (e.g. MGZ1 minY=-$100 for falling intro headroom).
	 * Use {@link #setVerticalWrapEnabled(boolean)} to control wrapping explicitly.
	 */
	public void setMinY(short minY) {
		this.minY = minY;
		this.minYTarget = minY;
	}

	/**
	 * Explicitly enables or disables vertical wrapping.
	 * When enabled, the camera and player Y coordinates wrap at the given range.
	 *
	 * @param enabled whether to enable vertical wrapping
	 * @param range   wrap range in pixels (must be a power of 2; e.g. 0x800 for S1, 0x1000 for S3K 32-row levels)
	 */
	public void setVerticalWrapEnabled(boolean enabled, int range) {
		this.verticalWrapEnabled = enabled;
		if (enabled && range > 0) {
			this.verticalWrapRange = range;
			this.verticalWrapMask = range - 1;
		}
	}

	/**
	 * Convenience overload that uses the default S1 range (0x800).
	 */
	public void setVerticalWrapEnabled(boolean enabled) {
		setVerticalWrapEnabled(enabled, VERTICAL_WRAP_RANGE);
	}

	/**
	 * Applies the active vertical wrap mask to a playable object's ROM
	 * {@code y_pos} equivalent when vertical wrapping is active.
	 * <p>ROM references:
	 * {@code docs/skdisasm/sonic3k.asm:21989-21992} (Sonic),
	 * {@code docs/skdisasm/sonic3k.asm:25708-25711} (Tails/player display path),
	 * {@code docs/skdisasm/sonic3k.asm:26233-26236} (Tails control).
	 *
	 * @return true when the sprite's Y coordinate changed.
	 */
	public boolean applyScreenYWrapValue(AbstractPlayableSprite sprite) {
		if (!verticalWrapEnabled || sprite == null) {
			return false;
		}
		// ROM masks the PLAYER's y_pos every frame ONLY for games that have a real
		// Screen_Y_wrap_value (S3K: sonic3k.asm:21989-21992, 26233-26236). S1/S2 have
		// no such value on the player (useScreenYWrapValueForVisibility is false for
		// both); their LZ3/SBZ2 vertical wrap masks Sonic's y_pos ONLY in the same
		// frame the CAMERA crosses the wrap boundary (ScrollVertical
		// SV_BottomBoundary/SV_TopBoundary), which updatePosition already mirrors via
		// wrapFocusedSpriteYPositionWord. Applying an unconditional per-frame
		// y & 0x7FF here wrongly wrapped Sonic at y=0x800 long before the camera
		// reached the boundary (S1 LZ3 f466: 0x0807 -> 0x0007).
		com.openggf.game.PhysicsFeatureSet fs = sprite.getPhysicsFeatureSet();
		if (fs == null || !fs.useScreenYWrapValueForVisibility()) {
			return false;
		}
		short before = sprite.getCentreY();
		short after = (short) (before & verticalWrapMask);
		if (after == before) {
			return false;
		}
		sprite.setCentreYPreserveSubpixel(after);
		return true;
	}

	/**
	 * Sets minY target for smooth easing.
	 * Current minY will ease toward this value at 2px/frame.
	 */
	public void setMinYTarget(short minYTarget) {
		this.minYTarget = minYTarget;
	}

	public short getMinYTarget() {
		return minYTarget;
	}

	public short getMaxX() {
		return maxX;
	}

	/**
	 * Sets maxX immediately (both current and target).
	 * Use setMaxXTarget() for smooth easing.
	 */
	public void setMaxX(short maxX) {
		this.maxX = maxX;
		this.maxXTarget = maxX;
	}

	/**
	 * Sets maxX target for smooth easing.
	 * Current maxX will ease toward this value at 2px/frame.
	 */
	public void setMaxXTarget(short maxXTarget) {
		this.maxXTarget = maxXTarget;
	}

	public short getMaxXTarget() {
		return maxXTarget;
	}

	public short getMaxY() {
		return maxY;
	}

	/**
	 * Sets maxY immediately (both current and target).
	 * Use setMaxYTarget() for smooth easing.
	 */
	public void setMaxY(short maxY) {
		this.maxY = maxY;
		this.maxYTarget = maxY;
	}

	/**
	 * Defers an object-side max-Y boundary write until the current camera step has
	 * consumed the previous boundary. This matches ROM paths where an object
	 * routine runs after ScrollVerti for the visible frame.
	 */
	public void setMaxYAfterNextUpdate(short maxY) {
		this.deferredMaxYValue = maxY;
		this.deferMaxYWriteUntilAfterUpdate = true;
	}

	/**
	 * Sets maxY target for smooth easing.
	 * Current maxY will ease toward this value at 2px/frame.
	 * ROM: Camera_Max_Y_pos_target
	 */
	public void setMaxYTarget(short maxYTarget) {
		this.maxYTarget = maxYTarget;
	}

	public short getMaxYTarget() {
		return maxYTarget;
	}

	public void incrementX(short amount) {
		x += amount;
	}

	public void incrementY(short amount) {
		y += amount;
	}

	/**
	 * Gets the current Y position bias (ROM: Camera_Y_pos_bias).
	 * Default is 96. Used as the vertical target position for camera centering.
	 * @return Current Y position bias value
	 */
	public short getYPosBias() {
		return yPosBias;
	}

	/**
	 * Sets the Y position bias (ROM: Camera_Y_pos_bias).
	 * When bias != 96, grounded vertical scroll uses slower 2px/frame cap.
	 * Used by looking up/down mechanics and spindash release.
	 * @param yPosBias New bias value (default is 96)
	 */
	public void setYPosBias(short yPosBias) {
		this.yPosBias = yPosBias;
	}

	/**
	 * Resets Y position bias to the default value (96).
	 * ROM: Called during Obj01_ResetScr equivalents - rolling, spindash release, jumping.
	 * The bias gradually eases back to 96 at 2px/frame (4 toward, 2 back = net 2).
	 */
	public void resetYBias() {
		// ROM: Obj01_ResetScr_Part2 / Obj01_Jump_ResetScr
		// The actual reset is gradual: if bias < 96, add 4 then subtract 2 (net +2)
		// if bias > 96, just subtract 2
		// This method initiates the reset process - actual easing happens in updateYBiasEasing()
		this.yPosBias = DEFAULT_Y_BIAS;
	}

	/**
	 * Gradually increases bias toward the look-up target (0xC8 = 200).
	 * ROM: s2.asm:36406-36408 - adds 2 to bias each frame until reaching 0xC8.
	 * Call this each frame while looking up AND look delay counter has elapsed.
	 */
	public void incrementLookUpBias() {
		if (yPosBias < LOOK_UP_BIAS) {
			yPosBias += 2;
			if (yPosBias > LOOK_UP_BIAS) {
				yPosBias = LOOK_UP_BIAS;
			}
		}
	}

	/**
	 * Gradually decreases bias toward the look-down target (8).
	 * ROM: s2.asm:36420-36422 - subtracts 2 from bias each frame until reaching 8.
	 * Call this each frame while looking down AND look delay counter has elapsed.
	 */
	public void decrementLookDownBias() {
		if (yPosBias > LOOK_DOWN_BIAS) {
			yPosBias -= 2;
			if (yPosBias < LOOK_DOWN_BIAS) {
				yPosBias = LOOK_DOWN_BIAS;
			}
		}
	}

	/**
	 * Gradually eases bias back toward the default value (96).
	 * ROM: s2.asm:36431-36438 (Obj01_ResetScr_Part2)
	 * - If bias < 96: add 4, then subtract 2 (net +2 per frame)
	 * - If bias > 96: subtract 2
	 * Call this each frame when not actively panning.
	 */
	public void easeYBiasToDefault() {
		if (yPosBias < DEFAULT_Y_BIAS) {
			// ROM: addq.w #4, subq.w #2 = net +2, no intermediate clamp
			// (s2.asm:36431-36438, Obj01_ResetScr_Part2)
			yPosBias += 4;
			yPosBias -= 2;
		} else if (yPosBias > DEFAULT_Y_BIAS) {
			// ROM: subq.w #2
			yPosBias -= 2;
			if (yPosBias < DEFAULT_Y_BIAS) {
				yPosBias = DEFAULT_Y_BIAS;
			}
		}
	}

	/**
	 * @deprecated Use incrementLookUpBias() for ROM-accurate gradual adjustment.
	 * Sets the bias instantly for looking up (ROM target: 0xC8 = 200).
	 */
	@Deprecated
	public void setLookUpBias() {
		this.yPosBias = LOOK_UP_BIAS;
	}

	/**
	 * @deprecated Use decrementLookDownBias() for ROM-accurate gradual adjustment.
	 * Sets the bias instantly for looking down/crouching (ROM target: 8).
	 */
	@Deprecated
	public void setLookDownBias() {
		this.yPosBias = LOOK_DOWN_BIAS;
	}

	/**
	 * Gets the default Y bias value.
	 * @return Default Y bias (96)
	 */
	public static short getDefaultYBias() {
		return DEFAULT_Y_BIAS;
	}

	/**
	 * Gets the look up bias target value.
	 * @return Look up bias (200 / 0xC8)
	 */
	public static short getLookUpBias() {
		return LOOK_UP_BIAS;
	}

	/**
	 * Gets the look down bias target value.
	 * @return Look down bias (8)
	 */
	public static short getLookDownBias() {
		return LOOK_DOWN_BIAS;
	}

	/**
	 * @return true if vertical wrapping is active (LZ3/SBZ2 loop sections)
	 */
	public boolean isVerticalWrapEnabled() {
		return verticalWrapEnabled;
	}

	/**
	 * @return true if a vertical wrap occurred during the last updatePosition() call
	 */
	public boolean didWrapLastFrame() {
		return lastFrameWrapped;
	}

	/**
	 * @return the Y delta applied by wrapping last frame (e.g. -0x800 for downward wrap), or 0
	 */
	public short getWrapDeltaY() {
		return wrapDeltaY;
	}

	/**
	 * @return the current vertical wrap range for this camera instance
	 */
	public int getVerticalWrapRange() {
		return verticalWrapRange;
	}

	/**
	 * @return the BG Y mask for vertical wrapping (0x3FF)
	 */
	public static int getVerticalWrapBgMask() {
		return VERTICAL_WRAP_BG_MASK;
	}

	/**
	 * Resets mutable state without destroying the singleton instance.
	 * Preserves width/height (configuration), clears all runtime state.
	 */
	public void resetState() {
		x = 0;
		y = 0;
		minX = 0;
		minY = 0;
		maxX = 0;
		maxY = 0;
		shakeOffsetX = 0;
		shakeOffsetY = 0;
		minXTarget = 0;
		minYTarget = 0;
		maxXTarget = 0;
		maxYTarget = 0;
		maxYChanging = false;
		horizScrollDelayFrames = 0;
		frozen = false;
		deferHorizontalBoundaryClampOnce = false;
		deferMaxYWriteUntilAfterUpdate = false;
		deferredMaxYValue = 0;
		levelStarted = true;
		focusedSprite = null;
		yPosBias = DEFAULT_Y_BIAS;
		fastScrollCap = DEFAULT_FAST_SCROLL_CAP;
		fastVerticalScrollRequested = false;
		verticalWrapEnabled = false;
		verticalWrapRange = VERTICAL_WRAP_RANGE;
		verticalWrapMask = VERTICAL_WRAP_RANGE - 1;
		lastFrameWrapped = false;
		wrapDeltaY = 0;
	}

	/**
	 * Sets the maximum vertical scroll speed for airborne and fast-ground paths.
	 * ROM: S2 uses 16 (0x10), S3K uses 24 (0x18).
	 * (s2.asm:18189-18190 ".doScroll_fast"; sonic3k.asm:loc_1C1B0)
	 *
	 * @param cap scroll cap in pixels per frame (16 for S1/S2, 24 for S3K)
	 */
	public void setFastScrollCap(int cap) {
		this.fastScrollCap = (short) cap;
	}

	/**
	 * Sets whether leftward horizontal camera scrolling is uncapped (ROM S1
	 * FixBugs=0 behavior). When true, the per-frame cap applies only to rightward
	 * scrolling. Set per-game from
	 * {@link PhysicsFeatureSet#uncappedLeftwardHorizontalScroll()}.
	 */
	public void setUncappedLeftwardScroll(boolean uncapped) {
		this.uncappedLeftwardHorizontalScroll = uncapped;
	}

	/** Returns the current fast vertical scroll cap in pixels/frame. */
	public int getFastScrollCap() {
		return fastScrollCap;
	}

	/**
	 * Requests ROM {@code Fast_V_scroll_flag} behavior for the next camera update.
	 * The request is frame-scoped and is cleared by {@link #updatePosition()}.
	 */
	public void requestFastVerticalScroll() {
		fastVerticalScrollRequested = true;
	}

	@Override
	public String key() {
		return "camera";
	}

	@Override
	public CameraSnapshot capture() {
		return new CameraSnapshot(
				x, y, minX, minY, maxX, maxY,
				shakeOffsetX, shakeOffsetY,
				minXTarget, minYTarget, maxXTarget, maxYTarget,
				maxYChanging, horizScrollDelayFrames, frozen, deferHorizontalBoundaryClampOnce,
				deferMaxYWriteUntilAfterUpdate, deferredMaxYValue, levelStarted,
				verticalWrapEnabled, verticalWrapRange, verticalWrapMask,
				lastFrameWrapped, wrapDeltaY, yPosBias, fastScrollCap);
	}

	@Override
	public void restore(CameraSnapshot snapshot) {
		x = snapshot.x();
		y = snapshot.y();
		minX = snapshot.minX();
		minY = snapshot.minY();
		maxX = snapshot.maxX();
		maxY = snapshot.maxY();
		shakeOffsetX = snapshot.shakeOffsetX();
		shakeOffsetY = snapshot.shakeOffsetY();
		minXTarget = snapshot.minXTarget();
		minYTarget = snapshot.minYTarget();
		maxXTarget = snapshot.maxXTarget();
		maxYTarget = snapshot.maxYTarget();
		maxYChanging = snapshot.maxYChanging();
		horizScrollDelayFrames = snapshot.horizScrollDelayFrames();
		frozen = snapshot.frozen();
		deferHorizontalBoundaryClampOnce = snapshot.deferHorizontalBoundaryClampOnce();
		deferMaxYWriteUntilAfterUpdate = snapshot.deferMaxYWriteUntilAfterUpdate();
		deferredMaxYValue = snapshot.deferredMaxYValue();
		levelStarted = snapshot.levelStarted();
		verticalWrapEnabled = snapshot.verticalWrapEnabled();
		verticalWrapRange = snapshot.verticalWrapRange();
		verticalWrapMask = snapshot.verticalWrapMask();
		lastFrameWrapped = snapshot.lastFrameWrapped();
		wrapDeltaY = snapshot.wrapDeltaY();
		yPosBias = snapshot.yPosBias();
		fastScrollCap = snapshot.fastScrollCap();
		// Re-resolve focused sprite via SpriteManager after restore. Object instances
		// are rebuilt during rewind; this ensures Camera tracks the live main player
		// sprite rather than a stale or null reference (Track C / H.1).
		rebindFocusedSprite();
	}

	/**
	 * Re-resolves the focused sprite from the active SpriteManager using the
	 * configured main character code. Called from {@link #restore} to ensure the
	 * camera target is up-to-date after a rewind snapshot restore.
	 */
	private void rebindFocusedSprite() {
		com.openggf.sprites.managers.SpriteManager sm = GameServices.spritesOrNull();
		if (sm == null) {
			return;
		}
		String mainCode = GameServices.configuration()
				.getString(SonicConfiguration.MAIN_CHARACTER_CODE);
		if (mainCode == null || mainCode.isBlank()) {
			mainCode = "sonic";
		}
		Sprite candidate = sm.getSprite(mainCode);
		if (candidate instanceof AbstractPlayableSprite aps) {
			focusedSprite = aps;
		}
	}

}
