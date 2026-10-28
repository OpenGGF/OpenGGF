# Architecture lifecycle remediation

The review and remediation start from develop commit
`8945c0a789b27be57b2361c70f561f31872a76ca`.

## World and observer ownership

Session retirement detaches the published world and clears future admission before
running fallible cleanup. Mode teardown remains the primary failure when retirement
also fails. Cleanup attempts each accepted observer once; later clear calls do not
retry a partially executed creator callback.

Accepted gameplay observers use the existing world policy owner even with an empty
mutator catalogue or no supported mutator profile. Those worlds receive callbacks,
rewind and retirement without opening mutator preferences. Layered service patches
reuse the accumulated provider and preserve native policy children once.

Two design investigations compared this repair with a separate observer owner.
The separate owner would have moved temporary-mutator state, boundary ordering and
snapshot ownership between composed children. The repair keeps those established
contracts and limits the new state to explicit absence of preference persistence.

## Scene visit admission

Context identity admits native audio for the current visit, including its retained
native act. The Starpost round-trip tests exercise donor music changes during that
act; rejecting every suspended SceneAudio call prevents those valid changes.
Title exits and finite SceneMusic additionally require active scene presentation.
Finite music receives that engine-owned predicate because a retained music handle
otherwise bypasses the context getter. Preparation and start reject an inactive visit; retained player
controls cannot mutate playback. Host suspension and close use an internal stop
operation so resource retirement still works after command admission ends.

Prepared metadata and already-running synthesis jobs remain owned by the retained
visit. Returning from a native act readmits the same context and its preparation.
Host audio cleanup still restores tempo and releases sources after retirement.

## Rewind boundary and regression evidence

The accepted-decision journal captures its before-tick state after observer callbacks.
Replay skips those callbacks and restores their accepted decisions. Tests therefore
restore the value after the callback at both ends of a tick that makes no further
observer changes. Moving capture earlier would repeat or lose creator decisions.

The original review reproduced stale world publication, a retained recorded-admission
policy, missed observer cleanup and stale scene commands at the source commit above.
Permanent regressions cover successful and failed retirement, suppressed failures,
observer-only and layered ownership, actual restored observer values, absent
preferences, current and retired scene effects, and finite music across suspension,
resume and exit. The finite-music regressions use short real Sonic 1 preparations
and first reproduce playback starting after admission has ended.
