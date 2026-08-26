# Complete-run audio parity tools

`run_complete_audio_parity.sh` is the create-new orchestration boundary for the
`complete_run_audio.v1` capture format. It runs each fixed producer twice,
requires byte-identical duplicates, validates both captures through the fixed
`CompleteRunAudioTool` class, and publishes the comparison report as one
directory. It never deletes or overwrites a run.

Build the executable JAR on JDK 21 first. Run roots are restricted to a direct
child of `target/audio-parity/runs`:

```bash
tools/audio/run_complete_audio_parity.sh \
  --run-root "$PWD/target/audio-parity/runs/my-new-run" \
  --profile complete-run-profile-id \
  --rom /absolute/pinned-rom \
  --bk2 /absolute/pinned-movie.bk2 \
  --run-manifest /absolute/pinned-run-manifest.json \
  --reference-home /absolute/separate-bizhawk-install
```

The producer dispatcher is a closed in-process `S1`/`S2`/`S3K` registry; callers
cannot replace its class IDs or register implementations. Task 9 reserves the
three exact profile entries and returns `PRODUCER_UNAVAILABLE` without output
until each game-specific plan supplies its built-in producer classes. Those
classes must write a fresh canonical capture directory. Only the reference producer receives the separately installed
BizHawk home. S2 and S3K producers must serialize their
immutable cutoff frontier before disabling or discarding observer state; S1
and S2 still serialize an explicit empty frontier.
The comparable frontier is producer-neutral: semantic service hierarchy,
completion state, cutoff-local chip projection, YM latches, and normalized
terminal state. Buffered BizHawk captures additionally store native
token/hook/PC/source events, raw range snapshots, arm proof, and the terminal
Z80 digest in a diagnostics sidecar covered by the storage root but excluded
from semantic equality. OpenGGF and callback producers must omit that sidecar.
Each built-in profile class statically registers its immutable profile; every
fresh CLI JVM lazily initializes only the profile class fixed in the closed
dispatcher, so no prior process or mutable registration command is required.

The fixed Java preflight authenticates the complete reference installation,
including entry types, permission modes, names, and every regular-file digest;
unknown additions and linked or special entries are rejected. The shell also
rechecks the caller installation and its private capture copy before and after
the producer runs. Any separate stock-control distribution is owned and pinned
inside the later built-in producer, never supplied as a replacement command.

Exit status is `0` for a match, `2` for usage/security refusal, `3` for a valid
semantic mismatch, and `4` for producer or capture failure. Ambient Java, shell,
Git, SSH, and dynamic-loader injection variables are rejected.
