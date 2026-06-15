package com.openggf.level.objects;

import com.openggf.game.PlayableEntity;

public interface SolidObjectProvider {
    SolidObjectParams getSolidParams();

    default SolidRoutineProfile getSolidRoutineProfile() {
        return SolidRoutineProfile.fromProvider(this);
    }

    default SolidExecutionMode solidExecutionMode() {
        return SolidExecutionMode.AUTO_AFTER_UPDATE;
    }

    default boolean isSolidFor(PlayableEntity player) {
        return true;
    }

    default boolean isTopSolidOnly() {
        return false;
    }

    /**
     * Number of position-history frames to use for this object's new
     * {@code SolidObjectTop} geometry check.
     * <p>
     * Most objects use the player's current engine position. Object-specific
     * providers may opt into a different sampled phase when porting an inline
     * ROM top-solid helper. The call site must cite the concrete disassembly
     * routine; for S3K {@code SolidObjectTop}'s new-landing geometry reads
     * {@code x_pos/y_pos/y_radius} at sonic3k.asm:41982-42015.
     */
    default int getTopSolidPlayerPositionHistoryFrames(PlayableEntity player) {
        return 0;
    }

    /**
     * Whether this top-solid object rejects the exact surface boundary before landing.
     * <p>
     * Most shared top-solid callers keep the established engine/profile behavior.
     * Some helper variants reject {@code d0 == 0}; S3K's shared
     * {@code SolidObjectTop_1P} accepts it and only rejects positive separation
     * or overlap below {@code -$10}.
     */
    default boolean rejectsZeroDistanceTopSolidLanding() {
        return false;
    }

    default boolean rejectsZeroDistanceTopSolidLanding(PlayableEntity player) {
        return rejectsZeroDistanceTopSolidLanding();
    }

    /**
     * Whether this object accepts a new top-solid landing at the exact surface
     * boundary even when the game's shared top-solid profile normally rejects it.
     * <p>
     * The shared {@code topSolidLandingAllowsZeroDist} game flag models
     * {@code PlatformObject_ChkYRange}, whose new-landing fall-through window is
     * gated per game (S2 rejects the exact boundary; S1/S3K accept it).  However,
     * top-solid objects that land players through {@code SolidObject_Landed}
     * (reached via {@code SolidObject_TopBottom}) rather than
     * {@code PlatformObject_ChkYRange} accept the exact surface boundary on every
     * game: {@code SolidObject_TopBottom} does {@code cmpi.w #$10,d3 / blo
     * SolidObject_Landed} (docs/s2disasm/s2.asm:35488-35494), and {@code blo}
     * (unsigned lower-than $10) covers {@code d3 == 0}.  The engine identifies the
     * {@code SolidObject_Landed} routine by {@code !usesPlatformObjectLandingSnap()}
     * (the same predicate that selects the {@code playerY - distY + 3} snap), so a
     * SolidObject-routine top-solid object accepts the {@code distY == 0} boundary
     * regardless of the PlatformObject-oriented game flag.  This is required for
     * S2's Obj82 swinging platform (Obj82_Main calls JmpTo23_SolidObject,
     * docs/s2disasm/s2.asm:57159), where a fast-falling rider can reach the pillar
     * top with exactly {@code d3 == 0} and must still land that frame.
     */
    default boolean allowsZeroDistanceTopSolidLanding(PlayableEntity player) {
        // SolidObject_Landed (s2.asm:35488-35494 blo covers d3==0) accepts the
        // exact boundary; PlatformObject_ChkYRange (game flag) governs the rest.
        return isTopSolidOnly() && !usesPlatformObjectLandingSnap();
    }

    /**
     * Whether a new airborne {@code SolidObjectTop} landing should be gated by
     * the player's previous-frame position before applying the current contact.
     * <p>
     * Most solids run in the engine's shared contact phase and use the current
     * player position. A few object-local ROM helpers execute before the player's
     * movement for the frame; these can otherwise accept the top surface one
     * frame too early after the engine has already applied movement.
     */
    default boolean gatesNewTopSolidLandingWithPreviousPosition() {
        return false;
    }

    /**
     * Called when a top-solid first-landing check reaches the exact surface
     * boundary and this provider rejected that boundary.
     */
    default void onRejectedZeroDistanceTopSolidLanding(PlayableEntity player) {
        // Default no-op
    }

    /**
     * Whether this solid can keep a grounded player attached during the
     * pre-movement terrain attachment check used by S2/S3K inline solid
     * resolution.
     * <p>
     * Normal solids rely on the previous frame's standing snapshot. ROM helper
     * objects spawned immediately before their first {@code SolidObjectTop}
     * call do not have a previous snapshot yet, but can still support the
     * player in the same frame.
     */
    default boolean providesPreMovementGroundAttachmentSupport() {
        return false;
    }

    /**
     * Whether this solid should still be evaluated while the player is in an
     * object-controlled state. Most scripted object-control states suppress
     * generic solid contacts; a few ROM routines still call SolidObject and only
     * reject specific signed object_control values.
     */
    default boolean allowsObjectControlledSolidContacts() {
        return false;
    }

    /**
     * Whether this object's side-separation path should reject players whose
     * object-control state has bit 7 set.
     * <p>
     * Keep this object-local: some captured/riding states still need normal
     * solid support while object-controlled, and only concrete ROM routines
     * that prove a signed {@code object_control} side-contact reject should opt
     * in here.
     */
    default boolean rejectsBit7ObjectControlSideContact(PlayableEntity player) {
        return false;
    }

    /**
     * Whether this object's new {@code SolidObject_cont} contact path rejects
     * players whose object-control state has bit 7 set before side/top
     * classification.
     * <p>
     * Keep this object-local: existing standing-bit/riding branches may still
     * need to run for captured object-control states, while concrete ROM
     * routines can opt in when they prove the signed {@code object_control}
     * test belongs to the new-contact helper path.
     */
    default boolean rejectsBit7ObjectControlNewSolidContact(PlayableEntity player) {
        return false;
    }

    /**
     * Whether this object keeps the player attached while object-local code,
     * rather than the generic solid routine, owns player positioning.
     * <p>
     * Default is false: when an object is no longer solid for the current
     * player, the generic platform path treats that as a ride exit. Bespoke ROM
     * objects can opt in when their routine keeps the standing/on-object state
     * set while suppressing normal solid contacts for an object-control capture.
     * Example: S2 CNZ Obj85 launcher springs set {@code obj_control=$81} and
     * continue writing {@code x_pos/y_pos} in loc_2ADFE / loc_2AFFE without
     * clearing {@code status.player.on_object}; they only clear it on off-screen
     * release or launch (docs/s2disasm/s2.asm:57520-57545, 57684-57711).
     */
    default boolean preservesObjectManagedRideWhileNotSolidFor(PlayableEntity player) {
        return false;
    }

    /**
     * Whether this object's own stale standing bit should consume an airborne
     * player by clearing support and returning no contact before new-contact
     * resolution.
     * <p>
     * This is the {@code SolidObjectFull_1P} entry contract: when a0.d6 is set
     * and {@code Status_InAir} is set, the helper branches to the stale-rider
     * return instead of reaching {@code SolidObject_cont}. Keep this opt-in so
     * custom objects whose standing bits gate object-local capture paths, such
     * as CNZ cylinders, are not forced through plain full-solid semantics.
     */
    default boolean airborneStaleStandingBitReturnsNoContact(PlayableEntity player) {
        return false;
    }

    /**
     * Optional centre-Y write for objects that preserve a ride while object-local
     * code owns player positioning. Return {@code null} to leave Y unchanged.
     */
    default Integer getObjectManagedRideCentreY(PlayableEntity player, int objectY, SolidObjectParams params) {
        return null;
    }

    /**
     * Object-local correction applied when the shared solid resolver snaps a
     * player onto this object's top surface.
     * <p>
     * Use this for ROM helper quirks that belong to a concrete object routine
     * instead of broadening the shared top-solid behavior across games.
     */
    default int getTopLandingSnapAdjustment(PlayableEntity player, int solidTopYRadius) {
        return 0;
    }

    /**
     * Whether a player landing on this object's top surface keeps its rolling
     * state instead of running the generic floor roll-clear.
     * <p>
     * The shared object-landing path mirrors {@code Sonic_ResetOnFloor} /
     * {@code Solid_ResetFloor}, which clears the rolling flag (and applies the
     * full roll-exit y_radius restoration) on landing. A few object routines
     * deliberately keep the player curled: they call only
     * {@code SolidObject_Always_SingleCharacter}/{@code RideObject_SetRide} for
     * the snap and never invoke {@code Sonic_ResetOnFloor}, so the rolling flag
     * and ball radii survive untouched. Returning {@code true} skips the generic
     * roll-clear for those objects so the landing y_pos is left exactly where the
     * snap placed it.
     */
    default boolean landingPreservesRolling(PlayableEntity player) {
        return false;
    }

    /**
     * Whether this solid uses S3K's {@code SolidObjectFull} Player 2 visibility
     * gate. That helper processes Player 1, then skips Player 2 when Player 2's
     * {@code render_flags} bit 7 is clear (sonic3k.asm:41003-41008).
     */
    default boolean skipsCpuSidekickWhenRenderFlagOffScreen() {
        return false;
    }

    /**
     * Whether this object uses monitor-style solidity (SPG: "Item Monitor").
     * Monitor solidity differs from normal solid objects:
     * - No +4 added during vertical overlap check
     * - Landing only if player Y relative to top < 16 AND within object width + 4px margin
     * - Never pushes player downward, only to sides
     */
    default boolean hasMonitorSolidity() {
        return false;
    }

    /**
     * Vertical offset used by monitor-style solid overlap checks.
     * <p>
     * Defaults to zero to preserve existing monitor behavior. S3K monitors opt
     * into the generic {@code SolidObject_cont} offset because their monitor
     * gate branches directly there.
     */
    default int getMonitorSolidObjectVerticalOffset() {
        return 0;
    }

    /**
     * Whether this object should use the generic sticky contact buffer while being ridden.
     * <p>
     * The buffer reduces edge jitter for moving platforms, but some hazards should not
     * preserve contact through this tolerance.
     */
    default boolean usesStickyContactBuffer() {
        return true;
    }

    /**
     * Whether side-contact at exact edge overlap (distX == 0) should preserve
     * player subpixel motion instead of immediately zeroing horizontal speed.
     * <p>
     * Most static solids should return false to keep the player stable against
     * walls and avoid 1px edge jitter. Push-driven objects that depend on ROM
     * edge cadence (for example Sonic 1 push blocks) can return true.
     */
    default boolean preservesEdgeSubpixelMotion() {
        return false;
    }

    /**
     * Whether this object preserves a same-frame SPECIAL TouchResponse velocity
     * handoff through the following airborne post-movement side contact.
     * <p>
     * Default is false because shared Sonic solid helpers zero horizontal speed
     * on moving side contact across games. Object-local ROM ordering exceptions
     * must opt in with a citation and are additionally gated on an actual
     * SPECIAL touch callback in the current frame.
     */
    default boolean preservesPostSpecialTouchAirborneSideVelocity() {
        return false;
    }

    /**
     * Whether a grounded lower-half edge escape from the squash path should
     * set the player/object push bits even when the player is not moving into
     * the object.
     * <p>
     * Most callers keep the established engine behavior. Concrete ports of
     * S2/S3K {@code SolidObjectFull} can opt in when their ROM path proves the
     * edge escape branches back into the same side-contact helper that sets
     * push for any grounded side separation.
     */
    default boolean groundedSquashEdgeSideContactSetsPush() {
        return false;
    }

    /**
     * Whether this object's standing/pushing latch key should be the live
     * object instance rather than {@link ObjectInstance#getSpawn()}.
     * <p>
     * ROM {@code SolidObjectFull} stores standing and pushing bits in the
     * object's SST {@code status(a0)} byte. Objects that rebuild a dynamic
     * engine spawn as they move still need those bits to survive from the
     * contact frame into the next no-contact clear path for the same SST slot.
     */
    default boolean usesInstanceSolidStateLatchKey() {
        return false;
    }

    /**
     * Whether the right edge of the full solid X window is inclusive.
     * <p>
     * Most engine objects keep the established exclusive bound. S3K horizontal
     * springs use {@code SolidObjectFull2_1P}, whose initial X gate rejects with
     * {@code bhi}; that makes {@code relX == width * 2} a valid contact.
     */
    default boolean usesInclusiveRightEdge() {
        return false;
    }

    /**
     * Half-width of the standable top surface used by landing checks.
     * <p>
     * Defaults to the full collision half-width. Override for objects whose
     * side/body collision is intentionally wider than their top landing area.
     */
    default int getTopLandingHalfWidth(PlayableEntity player, int collisionHalfWidth) {
        return collisionHalfWidth;
    }

    /**
     * Whether the collision half-width already matches the ROM's standable top
     * width for new landings.
     * <p>
     * Most solid helpers pass {@code d1 = obActWid + $B} into the generic solid
     * routines, so the landing check must narrow back down to {@code obActWid}.
     * Platform-style helpers such as Sonic 1's {@code PlatformObject} instead pass
     * {@code d1 = obActWid} directly, so the collision half-width is already the
     * correct landing width and should not be narrowed again.
     */
    default boolean usesCollisionHalfWidthForTopLanding() {
        return false;
    }

    /**
     * Whether this top-only platform's new-contact geometry should use
     * {@link SolidObjectParams#groundHalfHeight()} as the top surface height.
     * <p>
     * S2 {@code PlatformObject} callers pass the platform surface height in
     * {@code d3}; the {@code d2} register is not part of the new-landing
     * {@code PlatformObject_cont -> PlatformObject_ChkYRange} path.
     */
    default boolean usesGroundHalfHeightForTopSolidContact() {
        return false;
    }

    /**
     * Whether a new airborne top-solid landing should apply the absolute
     * {@code PlatformObject_ChkYRange} snap formula
     * {@code anchorY - groundHalfHeight - yRadius - 1} after
     * {@code resolveContactInternal} places the player.
     * <p>
     * S2 {@code PlatformObject}-based objects use this formula (true).
     * S2 {@code SolidObject} (JmpTo22_SolidObject / {@code SolidObject_Landed})-based
     * objects do NOT — {@code resolveContactInternal} already produces the
     * correct position ({@code playerY - distY + 3}), so the override must not
     * overwrite it.  Default is {@code true} to preserve the established
     * PlatformObject-style landing snap for the majority of top-solid objects.
     */
    default boolean usesPlatformObjectLandingSnap() {
        return true;
    }

    /**
     * Whether a newly established ride should remember this frame's pre-update
     * object X as the baseline for the next continued-riding carry.
     * <p>
     * S2 {@code PlatformObject} callers pass the object's saved pre-move
     * {@code x_pos} in {@code d4} to the solid helper, so the first continued
     * riding frame carries by {@code current_x - d4}.
     */
    default boolean seedsNewRideCarryFromPreUpdateX() {
        return false;
    }

    /**
     * Whether new solid-contact geometry should use the object's pre-update
     * position instead of its current post-update position.
     * <p>
     * Most inline solid providers run their ROM-equivalent solid helper after
     * object motion, so the current position is correct. Object-local routines
     * that call {@code SolidObject*} before moving their body can opt in here
     * without changing the shared contact model for unrelated solids.
     */
    default boolean usesPreUpdatePositionForSolidContact(PlayableEntity player) {
        return false;
    }

    /**
     * Number of newly-pressed horizontal-input frames to ignore while this
     * object is the player's current riding solid.
     * <p>
     * Default is zero. Object-specific overrides are for ROM helper timing
     * quirks where the BK2 input row is aligned to V-int, but the player
     * movement routine does not consume the new logical horizontal value until
     * a later gameplay step. Keep this object-local; do not broaden stale input
     * suppression into shared movement unless all callers of the helper have
     * been checked.
     */
    default int staleHorizontalLogicalInputFramesWhileRiding(PlayableEntity player, int rideFrames) {
        return 0;
    }

    /**
     * Whether full-solid lower-half overlap should use the player's current
     * y-radius rather than the standing y-radius.
     * <p>
     * Most callers keep the current game profile behaviour. Object-specific
     * disassembly ports can opt in when a concrete helper path is known to
     * build both halves from the live {@code y_radius(a1)} value.
     */
    default boolean fullSolidBottomOverlapUsesCurrentYRadiusOnly(PlayableEntity player) {
        return false;
    }

    /**
     * Called when the player is pushing against this object.
     * ROM: bset #p1_pushing_bit,status(a0) (s2.asm:35220-35226).
     * Objects that need to react to being pushed (e.g., spring walls) can override.
     */
    default void setPlayerPushing(PlayableEntity player, boolean pushing) {
        // Default no-op
    }

    /**
     * Whether this object's continued-riding path should carry the rider by
     * this frame's horizontal platform delta.
     * <p>
     * ROM divergence: the S2 {@code SolidObject} caller passes the platform's
     * pre-{@code MvSonicOnPtfm} carry reference x in {@code d4} (s2.asm:35418).
     * That reference is read from {@code objoff_2E}, which the platform's main
     * routine saves at the start of each frame and which the per-subtype
     * movement routine may or may not refresh after updating {@code x_pos}.
     * If the subtype routine refreshes {@code objoff_2E} to the new x_pos
     * (e.g. S2 Obj65 button-triggered subtypes 1/2/6/7 via {@code loc_26D50}),
     * {@code MvSonicOnPtfm} sees a zero delta and the rider is not carried.
     * If the subtype leaves {@code objoff_2E} untouched (e.g. S2 Obj65
     * conveyor subtype 5 {@code loc_26E4A}), the carry reference stays at the
     * pre-move x and the rider follows the platform by the full delta.
     * <p>
     * Default {@code true} preserves existing behaviour. Override with the
     * platform's current movement-routine semantics when porting a ROM
     * object that exposes both refreshing and non-refreshing movement modes.
     */
    default boolean carriesRiderOnHorizontalMove(PlayableEntity player) {
        return true;
    }

    /**
     * Whether this object should run a DropOnFloor terrain check after repositioning
     * the player each frame. When enabled, if terrain is detected at or above the
     * player's feet, the player detaches from this object and enters the air state.
     * <p>
     * ROM: DropOnFloor (s2.asm:35810) — called by objects that can push the player
     * into solid terrain (e.g., vertically-moving platforms like HTZ rising lava).
     */
    default boolean dropOnFloor() {
        return false;
    }

    /**
     * Whether losing ride contact through the inline carrying path should force the
     * player airborne.
     * <p>
     * Generic platform helpers in the original games typically clear
     * {@code status.player.on_object} and set {@code status.player.in_air} when the
     * player walks off the ride bounds. Some bespoke solids, such as the EHZ/HPZ log
     * bridge helper ({@code PlatformObject11_cont}), clear only the on-object flag and
     * allow immediate terrain handoff without an airborne frame.
     */
    default boolean forceAirOnRideExit() {
        return true;
    }

    /**
     * Whether this object's continued-riding routine still applies its platform
     * carry after {@code ExitPlatform} has cleared the player's on-object flag
     * because the player jumped.
     * <p>
     * Most platform helpers stop as soon as the rider is airborne. Sonic 1 Obj52
     * is a narrow exception: {@code MBlock_StandOn} calls {@code ExitPlatform},
     * then moves the block, then unconditionally calls {@code MvSonicOnPtfm2}.
     * See {@code docs/s1disasm/_incObj/52 Moving Blocks.asm:65-83} and
     * {@code docs/s1disasm/_incObj/15 Swinging Platforms.asm:177-194}.
     */
    default boolean carriesAirborneRiderAfterExitPlatform() {
        return false;
    }

    /**
     * Whether the inline continued-riding slope sample should be suppressed for
     * exactly this frame while the player remains attached to the object.
     * <p>
     * Default: {@code false} (slope sample writes y_pos every frame, matching
     * ROM {@code SolidObjSloped2} sonic3k.asm:41727-41752 / {@code MvSonicOnSlope}
     * s2disasm:35429 invoked by {@code sub_205B6} sonic3k.asm:44830).
     * <p>
     * ROM divergence covered by this hook: S3K {@code Obj_CollapsingPlatform}
     * state-1 routine {@code loc_20594} (sonic3k.asm:44814-44824) decrements its
     * collapse timer {@code $38} and, when the timer is already zero at frame
     * start, branches to {@code ObjPlatformCollapse_CreateFragments}
     * (sonic3k.asm:45394-45442). That branch rewrites {@code (a0)} to
     * {@code loc_205DE} and {@code jmp}s to {@code Play_SFX} <em>without</em>
     * falling through to {@code sub_205B6} (sonic3k.asm:44830) -- so the slope
     * sample / y_pos write is skipped on the state-1 to state-2 transition
     * frame. Sonic remains attached because {@code Status_OnObj} and
     * {@code p1_standing_bit} are not cleared, but his y_pos is held at the
     * value written by the previous frame's {@code SolidObjSloped2}.
     * <p>
     * Engine architecture has the platform's {@code update()} (state machine)
     * and the {@code SolidContacts} continued-riding pass as separate steps,
     * so the post-update solid pass would still run a slope sample on the
     * transition frame. Returning {@code true} here for that exact frame keeps
     * the player riding (no air transition, no x carry change) while skipping
     * the y_pos write, mirroring ROM.
     */
    default boolean suppressSlopeSampleThisFrame(PlayableEntity player) {
        return false;
    }

    /**
     * Whether this object's ROM solid routine does not run at all this frame,
     * so its airborne-rider unseat (the {@code SolidObjectTopSloped2}/
     * {@code SolidObjectFull} air-unseat that clears {@code Status_OnObj}) must
     * be deferred by one frame.
     * <p>
     * Distinct from {@link #suppressSlopeSampleThisFrame}: that controls only
     * the y_pos slope write inside the object's OWN continued-riding pass,
     * which the engine evaluates AFTER the object's {@code update()} has run.
     * The generic airborne-rider unseat in {@code ObjectSolidContactController}
     * can fire DURING an earlier-slot object's solid pass -- before this
     * object's {@code update()} has promoted its transition-skip flag for the
     * frame -- so the unseat suppression needs a predicate that is already
     * correct at the START of the frame, independent of object exec order.
     * <p>
     * Default mirrors {@link #suppressSlopeSampleThisFrame}; objects whose
     * transition-skip flag is promoted inside {@code update()} (e.g. S3K
     * {@code Obj_CollapsingPlatform}) override this to also report the pending
     * (not-yet-promoted) state. ROM: {@code ObjPlatformCollapse_CreateFragments}
     * jmps to {@code Play_SFX} without running {@code sub_205B6}
     * (sonic3k.asm:44818, 45394), so the platform performs no air-unseat on the
     * collapse-transition frame; the rider keeps {@code Status_OnObj} that frame
     * and is unseated the next frame when {@code loc_205DE} re-runs
     * {@code sub_205B6}.
     */
    default boolean defersAirborneRiderUnseatThisFrame(PlayableEntity player) {
        return suppressSlopeSampleThisFrame(player);
    }

    /**
     * Whether continued-riding exit should still apply one final sloped
     * surface sample before clearing {@code Status_OnObj}.
     * <p>
     * ROM divergence: S3K {@code Obj_CollapsingPlatform} release frame
     * {@code loc_205DE} calls {@code sub_205B6} before {@code sub_205FC}
     * clears the standing bit and sets {@code Status_InAir}
     * (sonic3k.asm:44850-44864). If the engine's ride exit path clears the
     * rider first, the player misses that final slope-position write.
     */
    default boolean sampleSlopeOnRideExit(PlayableEntity player) {
        return false;
    }

    /**
     * Whether the {@code SolidObject_cont} on-screen gate (engine flag
     * {@link com.openggf.game.PhysicsFeatureSet#solidObjectOffscreenGate()})
     * should be bypassed for this object's new-contact resolution path.
     * <p>
     * ROM divergence: the on-screen gate at {@code loc_1DF88}
     * (sonic3k.asm:41390) lives <em>only</em> in the {@code SolidObjectFull_1P}
     * helper (sonic3k.asm:41016-41018). Objects that route through the
     * sibling helper {@code SolidObjectFull2_1P} (sonic3k.asm:41065-41067)
     * fall through directly to {@code SolidObject_cont} and never test
     * {@code render_flags} bit 7. Notably <strong>all spring variants</strong>
     * call {@code SolidObjectFull2_1P} (sonic3k.asm:47664/47673/47692/47701/
     * 47779/47798/47829/47848/48036/48045/48064/48074), so an off-screen
     * spring still resolves push and side contact in the ROM. The S2 spring
     * helpers use the equivalent {@code SolidObject_Always_SingleCharacter}
     * (s2.asm:33709/33718/33784/33802) which also bypasses the on-screen gate.
     * <p>
     * Default: {@code false} (gate applies, matching the existing
     * {@link com.openggf.game.PhysicsFeatureSet#solidObjectOffscreenGate()}
     * default behaviour). Spring instances and other objects that route through
     * the {@code Full2} helpers must override to {@code true}.
     */
    default boolean bypassesOffscreenSolidGate() {
        return false;
    }
}
