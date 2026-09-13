---
name: bk2-input-authoring
description: Write a Genesis controller sequence as a short script and compile it into a BizHawk Input Log or .bk2 that OpenGGF's Bk2MovieLoader and GameplayCaptureTool consume.
---

# BK2 input authoring

`com.openggf.tools.InputLogAuthorTool` compiles a compact script into a real BizHawk
`Input Log.txt` (or a minimal `.bk2` zip), then re-parses it with the production
`Bk2MovieLoader` and fails if the round trip differs. The same loader drives trace
replay and `GameplayCaptureTool`, so an authored log behaves exactly like a recorded
movie. Use it whenever a capture, probe, or test needs scripted player input.

```bash
mvn exec:java "-Dexec.mainClass=com.openggf.tools.InputLogAuthorTool" \
  "-Dexec.args=--script target/capture/run.script --out target/capture/run.txt"
mvn exec:java "-Dexec.mainClass=com.openggf.tools.InputLogAuthorTool" \
  "-Dexec.args=--inline '60 R; repeat 3 { 1 A ; 1 - }' --out target/capture/run.bk2 --game s3k"
```

The tool prints the frame count and a run-length summary such as
`60xR, 1xA, 1x-, 1xA, 1x-, 1xA, 1x-`; check it matches what you meant.

## Script grammar

One instruction per line or separated by `;`. `#` starts a comment.

| Form | Meaning |
| --- | --- |
| `60 R` | hold Right for 60 frames |
| `30 D+R` or `30 down right` | hold several buttons; separators are `+` or spaces |
| `1 A` | press A (jump) for one frame; `jump` is an alias for `A` |
| `10 -` | neutral for 10 frames (`.` also works) |
| `4 R / L` | P1 holds Right while P2 holds Left |
| `repeat 3` … `end` | repeat the block; nestable |
| `repeat 2 { 1 A ; 1 - }` | inline repeat |

Buttons: `U D L R A B C S` (`S` is Start), case-insensitive, or the words
`up down left right a b c start jump`.

## Frames are held states

Every line is what the controller reads on those frames, exactly as BizHawk records
it. A tap is one frame pressed then a release (`1 A` then `1 -`); holding `A` for
sixty frames is one jump, not sixty. A spindash is `1 D`, then `repeat n { 1 D+A ; 1 D }`,
then `1 -` to release. Rolling needs the ROM's ground-speed threshold before `D` lands,
so put a run-up before the `D` frame.

## Output format

The log key is `#P1 Up|P1 Down|P1 Left|P1 Right|P1 Start|P1 A|P1 B|P1 C|#P2 …|`
and each frame line is `|UDLRSABC|UDLRSABC|` with `.` for released, the same writer
`UserRecordingWriter` uses for user recordings. `Bk2MovieLoader.loadInputLog` reads the
bare text file; `loadMovieOrInputLog` accepts either container by zip magic.
