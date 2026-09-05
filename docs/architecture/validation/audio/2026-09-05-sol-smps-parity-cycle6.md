# S3K SMPS parity cycle 6: diagnostic ring dispatch

Status: candidate; not delivered or post-merge verified.

Retail `zPlaySound_CheckRing` toggles boot-zeroed `zRingSpeaker` only when raw
request `33h` reaches dispatch, selecting Sound34/Sound33 in turn
(`Sound/Z80 Sound Driver.asm:547,1919-1928`, shipped `fix_sndbugs=0`). The
oracle adapter instead loaded Sound33 directly. The bounded correction lives
in the diagnostic capture callback, not production audio.

An incomplete production `AudioRequestService` was rejected: the current
forward boundary permits only one consequence, whereas retail clears both SFX
mailboxes during the 1-up override and normally cycles all three queue slots
(`:658-701`). Such a service could retain suppressed requests and reorder or
delay music/secondary requests. No test in this bounded repair claims the
capture's 1-up/fade suppression behavior; that comparison remains unverified.

## Evidence

- Initial hard-oracle run after the transform failed the old pin at service
  2409 (`MUS_PSG1.overridden`, reference `true`, engine `false`), proving the
  prior 2357 mismatch moved without changing the comparator.
- Bypass mutation (`RingDispatch.select(33h) -> 33h`) fails at the old service
  2357 frontier. Log: `target/audio-parity-cycle6-ring-dispatch/mutation-bypass.log`.
- `TestS3kCaptureRingDispatch` exercises the real pending request callbacks:
  two `33h` requests remain unconsumed before service and select `[34h,33h]`
  in service order; explicit `34h`, non-ring IDs and a new boot instance do
  not incorrectly advance/carry state.
- `firstRawRingSelectsFm5WithExactReferenceWrites` compares the service-2357
  non-DAC write sequence and pins FM5 key-off plus its four SSG-EG clears.

Full ordinary and guard suites are pending the requested baseline merge.
