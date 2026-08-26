# GPGX reproduction trust boundary

These scripts authenticate all versioned recipe inputs before a build and run
under `/usr/bin/bash -p`. They reject ambient shell, Git/SSH, loader, Java,
.NET/Mono, compiler, and make overrides. Subprocesses which need a search path
receive a fixed `PATH` inside `env -i`; normal utility calls terminate at the
absolute OS binaries wrapped by `secure-runtime.sh`.

There is necessarily a pre-process trust root: the running kernel, the system
ELF loader and libc, `/usr/bin/bash`, and the absolute OS utilities needed to
authenticate `build-recipe.json`. Their observed SHA-256 identities are listed
in that recipe and rechecked once the privileged shell is running. This is a
host-image lock, not a claim that repository bytes can authenticate the kernel
or loader which started them.

`fetch-source.sh` has no repository replacement option. It uses an empty HOME
and XDG configuration, disables system/user Git configuration, hooks and
prompts, rejects every protocol except HTTPS, and fetches only the immutable
object IDs in `source-lock.json`. Output directories are sibling-staged and
published create-new by one shared no-copy/no-clobber helper.

`prepare-managed-inputs.sh` is offline-only. It copies the exact SDK archive
and all 114 NuGet packages after checking `managed-nuget-manifest.json` and
publishes an immutable prepared input tree. The managed reproduction remains
an honest byte mismatch; `REFLECTION` is selected and no patched managed DLL
is distributed.

`reproduce-stock-pair.sh` is the durable real gate. It fetches and prepares two
fresh source/toolchain trees, builds two native cores, and compares raw,
compressed, and identity bytes between runs and each native payload against
the pinned stock core. It also repeats the locked managed mismatch twice.
