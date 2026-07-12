# Phase 2 sample mod source

`project/` is the checked-in output of `ggfmod init --id phase2-sample --package
example.phase2sample`. The integration test regenerates it and requires exact file
and byte parity, then the build scripts copy this source fixture and exercise its
Maven art/level conversion lifecycle, Java compilation, and validated deterministic
packaging. No built jar is checked in.
