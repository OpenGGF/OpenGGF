# OpenSMPSDeck interop fixtures

These portable schema 4 package fixtures come from original
[OpenSMPSDeck examples](https://github.com/raiscan/open-smps-deck/tree/b04de7be480d84fcbd56d12f5296e968b2aebcc9/examples)
at revision `b04de7be480d84fcbd56d12f5296e968b2aebcc9` (GPL-3.0-only).
The `native-controls` exports exercise FM and PSG in S1 and S2.
The S3K `dac-percussion` export also exercises custom envelope bindings and DAC.
All three manifests declare no ROM-derived or retail song bytes.

From the OpenSMPSDeck checkout, build and export with these commands.
In each directory, keep the ZIP's unmodified `manifest.json` and store one
`path=base64` line per binary entry in `binary-assets.properties`, in ZIP
entry order. This follows OpenGGF's text-source convention for authored
fixtures. The test decodes the binary entries into the exact exploded JAR
layout before running playback.

```sh
mvn -B -pl headless -am package -DskipTests
jar=headless/target/opensmpsdeck-headless-0.1.0-SNAPSHOT.jar
for driver in s1 s2; do
  java -jar "$jar" export examples/native-controls.smps.json "$driver-native-controls.smps.zip" \
    --format portable --driver "$driver" --no-source --license GPL-3.0-only --speed-up-tempo 48
done
java -jar "$jar" export examples/dac-percussion.smps.json s3k-dac-percussion.smps.zip \
  --format portable --driver s3k --no-source --license GPL-3.0-only
```
