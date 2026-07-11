# Generated audio fixtures

`generated-silence-8000-mono.ogg` is a CC0 test fixture generated entirely from
silence (no third-party recording) with:

```text
ffmpeg -f lavfi -i "anullsrc=r=8000:cl=mono" -t 0.05 -c:a libvorbis -q:a 0 generated-silence-8000-mono.ogg
```

It exists only to exercise the real stb_vorbis handle decode path.
