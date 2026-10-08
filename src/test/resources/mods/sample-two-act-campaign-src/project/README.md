# Tide Circuit

An original two-act Sonic 2 hosted campaign, maintained as a Mod API example.
Use Java 21 and the locally built candidate engine/SDK artifacts to build:

```sh
mvn package -Dopenggf.engine.jar=/absolute/path/OpenGGF-jar-with-dependencies.jar \
  -Dopenggf.sdk.jar=/absolute/path/OpenGGF-openggf-mod-sdk.jar
```

The SDK validates and packages the jar. Runtime requires the user's Sonic 2 World
REV01 ROM (CRC32 `7B905383`, SHA-1 `8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9`).
Enable/trust the jar in Mod Manager, restart, then finish EHZ Act 2. Sonic 2 supplies
native character, checkpoint, signpost, rings and results behavior. Both authored
acts contribute fresh event/state, water, scroll, tile/palette animation and staged
render callbacks. Act 1 finishes through the native signpost/results sequence.
Act 2 uses an original registered finish gate, captured ring tally and two-second
results hold before returning to the stock successor; native S2 signposts retire
in later acts. The original gate reuses ROM-backed signpost art and recreates
through its verified owner factory.

The binary geometry/art is original. Check or recreate it with Python 3:

```sh
python3 tools/generate_assets.py --check
python3 tools/generate_assets.py
```

The generator reads only its committed authored constants. `level.json` stores
hand-authored placements, bounds, music and owner-local export metadata. Keep
`64`/`1024` values local; the engine allocates actual runtime IDs. See the creator
handbook's two-act campaign guide and Tide Circuit's per-act/route matrix for
executed coverage and remaining breadth; a load alone is not route certification.
