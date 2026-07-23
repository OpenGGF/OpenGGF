# Unified Audio Presentation Design

## Status

Approved design for replacing split OpenAL/software audio ownership with one
software-mixed presentation stream. This design is a prerequisite for reliable
live viewport recording.

## Problem

OpenGGF currently has two incompatible presentation paths:

- SMPS and selected PCM streams can be rendered through
  `StreamBackedDeterministicAudioRuntime`.
- Normal LWJGL playback also owns legacy OpenAL sources, including static WAV
  music and independently pitched WAV SFX.

Live recording originally assumed the deterministic runtime was already active.
Enabling it globally made normal gameplay audio disappear. Scoping it to a
recording lease restored normal playback, but starting the lease still changed
audio ownership: special-stage ring/SFX voices disappeared while recording,
fallback WAV voices were not captured, and starting during an active rewind
could not transfer the current reverse cursor.

The root problem is split ownership. Recording must observe the final audible
PCM without changing which component generates or owns it.

## Goals

1. Preserve title, gameplay, special-stage, music, ring, and SFX audio across
   Sonic 1, Sonic 2, and Sonic 3&K.
2. Mix SMPS music/SFX, raw SEGA PCM, fallback WAV music, and pitched WAV SFX
   into one software-owned stereo presentation stream.
3. Make OpenAL an output sink for the final mixed PCM, not an independent
   musical or SFX owner.
4. Let live recording attach and detach a non-consuming tap without changing
   voice ownership, command routing, playback cursors, or OpenAL source mode.
5. Start recording immediately during held rewind by forking the currently
   audible reverse cursor at its exact position.
6. Keep pause, frame-step silence, rewind release/crossfade, and repeated
   recording deterministic.
7. Degrade audio-capture failures to correctly clocked stereo silence while
   video recording and gameplay continue.
8. Avoid allocation, decoding, file I/O, and unbounded work in the real-time
   mixing loop.

## Non-Goals

- Changing ROM-facing audio timing, priorities, or sound-selection behavior.
- Adding effects, spatial audio, new codecs, or user-facing mixing controls.
- Depending on optional OpenAL loopback extensions.
- Maintaining a second capture-only mirror of every audible voice.
- Preserving the superseded global-LWJGL or recording-lease runtime switches.

## Architecture

```text
Audio commands
     |
     v
Software voice registry
  - SMPS music/SFX
  - raw SEGA PCM
  - decoded WAV music
  - decoded/pitched WAV SFX
     |
     v
AudioPresentationMixer
  - one AudioFrameClock
  - reusable accumulation/output buffers
  - saturating stereo mix
     |
     +----> Presentation history / rewind cursor
     |
     +----> non-consuming live-recording tap
     |
     v
OpenAL PCM sink
```

`AudioPresentationMixer` is the sole owner of audible voice cursors. The final
mixed PCM is the sole presentation truth. OpenAL queues that PCM but does not
own independent music or SFX playback state.

## Components

### Presentation voice contract

Every audible source implements a small software voice contract with:

- stereo rendering into a caller-owned accumulation buffer;
- current cursor and completion state;
- gain and pitch where the existing route supports them;
- looping for fallback music;
- explicit stop semantics;
- snapshot/restore data required by rewind.

SMPS drivers are adapted without duplicating synthesis. Raw SEGA PCM and WAV
assets use decoded sample-backed voices. Decoding and resampling setup occur
outside the real-time mixing loop.

### Software voice registry

The registry owns active music and SFX voices and preserves existing policies:

- music replacement and override stack behavior;
- SFX priority, continuous-SFX extension, and stop-all behavior;
- SEGA PCM start/stop commands;
- independent fallback WAV SFX with pitch;
- fallback WAV music looping;
- per-game audio profile routing.

The registry provides deterministic iteration order. Voice creation/removal is
applied at frame boundaries so the mixer never traverses a concurrently
mutating collection.

### Audio presentation mixer

The mixer advances once per presentation frame using one
`AudioFrameClock(sampleRate, effectiveFrameRate)`. It:

1. obtains the exact stereo-frame count for the frame;
2. clears reusable wide accumulation buffers;
3. renders every active voice in stable order;
4. saturates once into a reusable 16-bit stereo packet;
5. commits the final packet to presentation history;
6. publishes it independently to the OpenAL sink and any capture tap.

No voice writes directly to OpenAL.

### OpenAL PCM sink

LWJGL/OpenAL owns a bounded queue of buffers containing only final mixer PCM.
It exposes device initialization, negotiated sample rate, enqueue/update, and
cleanup. It does not decode WAV files or own voice cursors.

If OpenAL initialization or output fails, the engine switches to a no-device
sink. The mixer, rewind history, and recording remain operational.

### Presentation history and rewind

One history ring stores final audible PCM after all voices are mixed. The
audible cursor is either:

- forward, consuming newly mixed packets; or
- reverse, reading backward from the history ring at the requested rate.

Starting recording forks the active audible cursor:

- in forward playback, the tap starts at the current forward presentation
  boundary;
- during held rewind, it forks the active reverse cursor with the same
  position, rate, bounds, and history epoch.

Recording never owns or advances the speaker cursor. Rewind release crossfades
from reverse history to live mixed PCM once, before both speaker and recording
observe the resulting presentation packet.

### Live recording tap

Shift+O attaches a capture-owned `AudioFrameClock` and a non-consuming tap to
the final presentation stream. It does not:

- replace an audio runtime;
- rebind voices;
- flush OpenAL;
- migrate source cursors;
- change command routing.

Each submitted video frame receives exactly one stereo PCM packet. If no fresh
audio exists for that presented frame, the packet is explicit silence rather
than stale PCM.

## Audio Failure Policy

Audio failure must be graceful and must not stop video recording or gameplay.

| Failure | Behavior |
|---|---|
| OpenAL device unavailable/fails | Switch to no-device sink; mixer and recording continue |
| Recording tap cannot attach | Start video recording with clocked stereo silence |
| Recording tap fails mid-session | Detach failed tap and submit clocked stereo silence for the remaining video |
| One malformed WAV/PCM asset | Warn, reject that voice, continue other voices |
| One software voice throws while rendering | Warn, remove that voice at the frame boundary, continue the mix |
| Encoder audio write fails but video remains viable | Continue video with silence if the recorder can maintain a valid container |
| Whole recorder/mux fails | Use the existing bounded abort and warning path |

The MKV retains a stereo audio track even when capture audio is unavailable.
Silence length is derived from the capture-owned clock so A/V duration remains
correct.

## Concurrency and Real-Time Constraints

- Audio commands enter a bounded frame-boundary queue.
- The mixer has one state owner; OpenAL and capture consumers receive immutable
  packet views or copies from bounded reusable pools.
- No decoding, file access, process launch, logging formatting, or collection
  growth occurs inside voice rendering.
- Accumulation uses reusable wide integer buffers and one final saturation pass.
- Voice and queue counts have explicit bounds with warning/drop policy.
- Cleanup order is recording tap, voice registry/history, then OpenAL sink.
- Failure cleanup is idempotent.

## Migration

Migration is staged so every commit retains usable audio:

1. Introduce/test the mixer and software sample voices without changing LWJGL.
2. Route SEGA PCM and fallback WAV voices through the software registry.
3. Adapt SMPS music/SFX into the same mixer and prove command parity.
4. Change LWJGL to consume only final mixed PCM.
5. Remove legacy independent OpenAL voice/source ownership.
6. Attach rewind history and live recording to final presentation PCM.
7. Remove the temporary live-capture runtime switching and handoff code.

The normal playback path must never again be changed by merely toggling
recording.

## Testing

### Unit and component tests

- exact stereo mixing, saturation, gain, pitch, looping, completion, and stop;
- decoded WAV and raw PCM resampling;
- stable multi-voice ordering and frame-boundary mutation;
- SMPS music plus simultaneous SMPS/WAV/PCM SFX;
- ring/SFX priority and continuous-SFX behavior;
- exact frame-clock packet sizes for NTSC and PAL;
- OpenAL sink queue bounds and no-device fallback;
- non-consuming speaker/recording equality;
- silent capture degradation at attach and mid-session;
- immediate capture start from an active reverse cursor;
- rewind rate changes, release crossfade, epoch reset, and repeated recording;
- ordinary exception and shutdown cleanup.

### Integration tests

- special-stage rings and SFX remain audible and captured before, during, and
  after Shift+O;
- title and gameplay audio remain present in Sonic 1, Sonic 2, and Sonic 3&K;
- SEGA PCM, fallback WAV music, multiple pitched WAV SFX, and SMPS all appear
  in final captured PCM;
- pause and frame-step submit fresh silence;
- recording toggles do not reset music or voice cursors;
- audio failure produces a valid silent-track MKV while video continues;
- FFmpeg/ffprobe confirm stereo FLAC duration remains within one sample of the
  video duration.

### Manual ROM-backed matrix

For each supported game:

1. boot through the SEGA/title sequence;
2. enter gameplay and a special stage where available;
3. start recording during active music and repeated ring/SFX playback;
4. hold rewind, start a second recording while rewind remains held, release,
   pause, frame-step, and stop;
5. play both MKVs and confirm uninterrupted audible/captured sources;
6. inject an audio-capture failure and confirm video continues with silence.

Manual validation is required before final approval because prior synthetic
tests failed to expose production OpenAL ownership regressions.

## Acceptance Criteria

1. Normal audio is unchanged when recording is inactive.
2. Starting/stopping recording never changes audible voice availability or
   resets a cursor.
3. Special-stage ring sounds and SFX remain audible while recording.
4. Every audible SMPS, PCM, and fallback WAV source is represented in captured
   PCM.
5. Recording starts immediately during held rewind with synchronized reverse
   audio.
6. Speaker and recorder consume independent views of the same final PCM.
7. Audio capture failure yields correctly timed stereo silence; video and
   gameplay continue.
8. OpenAL owns only final PCM output after migration.
9. All automated suites pass, and the three-game manual matrix passes before
   the branch is considered ready to merge.

