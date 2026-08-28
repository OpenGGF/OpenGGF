#!/usr/bin/bash -p
set -euo pipefail

secure_fail() {
  /usr/bin/printf 'secure-runtime: %s\n' "$*" >&2
  return 1
}

secure_reject_ambient_overrides() {
  local entry name
  while IFS= read -r -d '' entry; do
    name=${entry%%=*}
    case "$name" in
      BASH_ENV|ENV|SHELLOPTS|CDPATH|GLOBIGNORE|\
      JAVA_HOME|JAVA_TOOL_OPTIONS|JDK_JAVA_OPTIONS|_JAVA_OPTIONS|JAVA_OPTS|JAVACMD|\
      MONO_*|DOTNET_*|NUGET_*|MSBUILD*|MSBuild*|\
      CC|CXX|CPP|AS|AR|LD|NM|OBJCOPY|OBJDUMP|RANLIB|STRIP|\
      CFLAGS|CXXFLAGS|CPPFLAGS|LDFLAGS|LIBRARY_PATH|CPATH|C_INCLUDE_PATH|\
      CPLUS_INCLUDE_PATH|OBJC_INCLUDE_PATH|MAKEFLAGS|MFLAGS|MAKELEVEL|\
      GCC_EXEC_PREFIX|COMPILER_PATH|CONFIG_SITE|PKG_CONFIG*|CMAKE_*|\
      GIT_*|SSH_*|\
      LD_*|BASH_FUNC_*) secure_fail "forbidden ambient variable: $name"; return 1 ;;
    esac
  done < <(/usr/bin/env -0)

  unset BASH_ENV ENV CDPATH GLOBIGNORE JAVA_HOME JAVA_TOOL_OPTIONS \
    JDK_JAVA_OPTIONS _JAVA_OPTIONS MONO_ENV_OPTIONS MONO_PATH \
    MONO_GAC_PREFIX DOTNET_ROOT DOTNET_HOST_PATH DOTNET_CLI_HOME \
    NUGET_PACKAGES CC CXX CPP AS AR LD NM OBJCOPY OBJDUMP RANLIB STRIP \
    CFLAGS CXXFLAGS CPPFLAGS LDFLAGS LIBRARY_PATH CPATH C_INCLUDE_PATH \
    CPLUS_INCLUDE_PATH OBJC_INCLUDE_PATH MAKEFLAGS MFLAGS MAKELEVEL \
    LD_PRELOAD LD_LIBRARY_PATH LD_AUDIT LD_DEBUG 2>/dev/null || true
  # A privileged Bash ignores an inherited SHELLOPTS before this script runs;
  # the replacement value is readonly Bash state and cannot be unset.
  readonly PATH=/usr/bin:/bin
  export PATH
  umask 0022
}

# Scripts may use these conventional names for readability, but every lookup
# terminates at a fixed absolute utility.  The functions are readonly so no
# sourced build input can replace the trust-root commands later in a run.
git() { /usr/bin/git "$@"; }; readonly -f git
sha256sum() { /usr/bin/sha256sum "$@"; }; readonly -f sha256sum
stat() { /usr/bin/stat "$@"; }; readonly -f stat
sed() { /usr/bin/sed "$@"; }; readonly -f sed
cmp() { /usr/bin/cmp "$@"; }; readonly -f cmp
find() { /usr/bin/find "$@"; }; readonly -f find
cp() { /usr/bin/cp "$@"; }; readonly -f cp
mv() { /usr/bin/mv "$@"; }; readonly -f mv
tar() { /usr/bin/tar "$@"; }; readonly -f tar
mkdir() { /usr/bin/mkdir "$@"; }; readonly -f mkdir
mktemp() { /usr/bin/mktemp "$@"; }; readonly -f mktemp
rm() { /usr/bin/rm "$@"; }; readonly -f rm
sort() { /usr/bin/sort "$@"; }; readonly -f sort
cut() { /usr/bin/cut "$@"; }; readonly -f cut
wc() { /usr/bin/wc "$@"; }; readonly -f wc
dirname() { /usr/bin/dirname "$@"; }; readonly -f dirname
basename() { /usr/bin/basename "$@"; }; readonly -f basename
env() { /usr/bin/env "$@"; }; readonly -f env
tail() { /usr/bin/tail "$@"; }; readonly -f tail
xxd() { /usr/bin/xxd "$@"; }; readonly -f xxd
ln() { /usr/bin/ln "$@"; }; readonly -f ln
readlink() { /usr/bin/readlink "$@"; }; readonly -f readlink
touch() { /usr/bin/touch "$@"; }; readonly -f touch

secure_require_absent_output() {
  local output=$1 parent
  [[ "$output" = /* ]] || secure_fail "output must be an absolute path"
  [[ ! -e "$output" && ! -L "$output" ]] \
    || secure_fail "output already exists: $output"
  parent=${output%/*}
  [[ -n "$parent" ]] || parent=/
  [[ -d "$parent" && ! -L "$parent" ]] \
    || secure_fail "output parent must be an existing non-symlink directory"
}

secure_verify_host_tool() {
  local expected=$1 tool=$2
  /usr/bin/printf '%s  %s\n' "$expected" "$tool" \
    | /usr/bin/sha256sum -c - >/dev/null \
    || secure_fail "host trust-root executable differs: $tool"
}

secure_publish_create_new() {
  local source=$1 target=$2
  [[ "$source" = /* && -d "$source" && ! -L "$source" ]] \
    || secure_fail "publication source must be an absolute, non-symlink directory"
  secure_require_absent_output "$target"
  secure_verify_host_tool \
    4dc8719b3b60a5e03b3720f3060415a8dd3b564b74319539b2a0dc52bc50c0df \
    /usr/bin/mv
  /usr/bin/mv -T --no-copy --no-clobber -- "$source" "$target"
  [[ ! -e "$source" && ! -L "$source" ]] \
    || secure_fail "publication target appeared concurrently: $target"
}

secure_snapshot_tree() {
  local source=$1 target=$2 parent stage
  [[ "$source" = /* && -d "$source" && ! -L "$source" ]] \
    || secure_fail "snapshot source must be an absolute, non-symlink directory"
  secure_require_absent_output "$target"
  parent=${target%/*}; [[ -n "$parent" ]] || parent=/
  stage=$(/usr/bin/mktemp -d "$parent/.gpgx-snapshot-staging.XXXXXX")
  if ! /usr/bin/cp -a -- "$source/." "$stage/"; then
    /usr/bin/rm -rf -- "$stage"
    secure_fail "snapshot copy failed"
    return 1
  fi
  if ! secure_publish_create_new "$stage" "$target"; then
    /usr/bin/rm -rf -- "$stage"
    return 1
  fi
}

secure_equal_files() {
  local left=$1 right=$2 left_sha right_sha
  [[ -f "$left" && ! -L "$left" && -f "$right" && ! -L "$right" ]] \
    || secure_fail "comparison inputs must be regular non-symlink files"
  left_sha=$(/usr/bin/sha256sum "$left"); left_sha=${left_sha%% *}
  right_sha=$(/usr/bin/sha256sum "$right"); right_sha=${right_sha%% *}
  [[ "$left_sha" = "$right_sha" ]] || secure_fail "comparison hashes differ"
  /usr/bin/cmp -s -- "$left" "$right" || secure_fail "comparison bytes differ"
}

secure_git_head() {
  local repository=$1
  [[ "$repository" = /* && -d "$repository" && ! -L "$repository" ]] \
    || secure_fail "Git repository must be an absolute, non-symlink directory"
  /usr/bin/env -i HOME=/nonexistent XDG_CONFIG_HOME=/nonexistent PATH=/usr/bin:/bin \
    LC_ALL=C GIT_CONFIG_NOSYSTEM=1 GIT_TERMINAL_PROMPT=0 \
    /usr/bin/git -c core.hooksPath=/dev/null -C "$repository" rev-parse HEAD
}

secure_verify_recipe() {
  local root=$1 recipe lock expected actual row relative digest observed tool
  [[ "$root" = /* && -d "$root" && ! -L "$root" ]] \
    || secure_fail "recipe root must be an absolute, non-symlink directory"
  recipe=$root/build-recipe.json
  lock=$root/toolchain-lock.json
  [[ -f "$recipe" && ! -L "$recipe" && -f "$lock" && ! -L "$lock" ]] \
    || secure_fail "recipe or toolchain lock is missing"
  expected=$(/usr/bin/jq -er '.build_recipe.sha256' "$lock") \
    || secure_fail "toolchain lock has no recipe digest"
  actual=$(/usr/bin/sha256sum "$recipe"); actual=${actual%% *}
  [[ "$actual" = "$expected" ]] \
    || secure_fail "build recipe digest differs: $actual"
  while IFS=$'\t' read -r relative digest; do
    [[ -n "$relative" && "$relative" != /* && "$relative" != *'..'* ]] \
      || secure_fail "unsafe build recipe input: $relative"
    [[ -f "$root/$relative" && ! -L "$root/$relative" ]] \
      || secure_fail "build recipe input is missing: $relative"
    observed=$(/usr/bin/sha256sum "$root/$relative"); observed=${observed%% *}
    [[ "$observed" = "$digest" ]] \
      || secure_fail "build recipe input differs: $relative"
  done < <(/usr/bin/jq -er '.versioned_inputs | to_entries[] | [.key,.value] | @tsv' "$recipe")
  while IFS=$'\t' read -r tool digest; do
    [[ "$tool" = /* && -f "$tool" ]] \
      || secure_fail "trust-root executable is missing: $tool"
    observed=$(/usr/bin/sha256sum "$tool"); observed=${observed%% *}
    [[ "$observed" = "$digest" ]] \
      || secure_fail "trust-root executable differs: $tool"
  done < <(/usr/bin/jq -er '(.trust_root.executables + .trust_root.system_files) | to_entries[] | [.key,.value] | @tsv' "$recipe")
  /usr/bin/printf '%s\n' "$actual"
}

secure_reject_ambient_overrides

if [[ "${BASH_SOURCE[0]}" = "$0" ]]; then
  command=${1-}; shift || true
  case "$command" in
    publish-create-new) [[ $# = 2 ]] || secure_fail "publish-create-new needs SOURCE TARGET"; secure_publish_create_new "$1" "$2" ;;
    snapshot-tree) [[ $# = 2 ]] || secure_fail "snapshot-tree needs SOURCE TARGET"; secure_snapshot_tree "$1" "$2" ;;
    equal-files) [[ $# = 2 ]] || secure_fail "equal-files needs LEFT RIGHT"; secure_equal_files "$1" "$2" ;;
    git-head) [[ $# = 1 ]] || secure_fail "git-head needs REPOSITORY"; secure_git_head "$1" ;;
    verify-recipe) [[ $# = 1 ]] || secure_fail "verify-recipe needs ROOT"; secure_verify_recipe "$1" ;;
    *) secure_fail "unknown command: $command" ;;
  esac
fi
