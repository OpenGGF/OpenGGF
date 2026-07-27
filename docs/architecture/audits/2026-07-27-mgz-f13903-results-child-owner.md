# MGZ f13903 results-child owner audit

## Finding

The standalone MGZ regression was not a VBlank-phase or route-selection
problem. Its sign entered `Obj_EndSignResults` with Player 1 already grounded,
so routine 6 wrote the victory pose and allocated `Obj_LevelResults` in a later
native SST slot during the same object pass. The engine delayed the pose to its
next sign dispatch and began the free results child on the next manager pass,
losing one represented owner entry.

The complete-run route supplies different live state: Player 1 is airborne on
the first routine-6 dispatch. The sign waits and therefore exposes that owner
boundary before allocating the results child. Its existing create and
retirement timing was already correct.

## Correction

`ResultsChildAllocationOwner` records whether allocation belongs to the native
later slot or the ordinary next engine pass. Only a grounded, no-wait sign
without an already-preserved owner boundary selects `NATIVE_LATER_SLOT`. Its
one catch-up entry is applied symmetrically to the results create gate and the
carried child-retirement tail. Player 1's victory pose is published in routine
6 on both paths, matching `Obj_EndSignResults`.

No game, zone, route, trace, frame, or VBlank predicate is involved.

## Verification

- Focused RED: the owner-selection test initially failed to compile because
  the semantic owner did not exist.
- Focused sign/results/defeat-flow suites: pass.
- MGZ standalone: f23561 `rings`, expected `0`, actual `1`.
- MGZ complete run: f28398 `rings`, expected `2`, actual `1`.
- AIZ standalone: unchanged f8837 `rings`, expected `0`, actual `100`.
- AIZ complete run: unchanged f26107 `x`, expected `$0000`, actual `$4A9B`.
- CNZ standalone: unchanged f10728 `player_mapping_frame`, expected `$08`,
  actual `$59`.

LBZ was neither inspected nor executed.
