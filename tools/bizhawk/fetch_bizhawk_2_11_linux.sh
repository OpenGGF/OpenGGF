#!/usr/bin/env bash
set -euo pipefail

readonly BIZHAWK_VERSION="2.11"
readonly ARCHIVE_NAME="BizHawk-2.11-linux-x64.tar.gz"
readonly ARCHIVE_URL="https://github.com/TASEmulators/BizHawk/releases/download/${BIZHAWK_VERSION}/${ARCHIVE_NAME}"
readonly ARCHIVE_SHA256="cdaf9650d880bae660d63a388430f630b8d8a96b1ba59ebf0e0195a645c3bab8"

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd -- "${script_dir}/../.." && pwd)"
docs_dir="${repo_root}/docs"
destination="${docs_dir}/BizHawk-${BIZHAWK_VERSION}-linux-x64"

if [[ -e "${destination}" || -L "${destination}" ]]; then
    echo "ERROR: BizHawk destination already exists: ${destination}" >&2
    echo "Remove or move it manually before reinstalling; this script will not overwrite it." >&2
    exit 1
fi

staging_root="$(mktemp -d "${docs_dir}/.bizhawk-${BIZHAWK_VERSION}-install.XXXXXX")"
cleanup() {
    rm -rf -- "${staging_root}"
}
trap cleanup EXIT

archive_path="${staging_root}/${ARCHIVE_NAME}"
staged_install="${staging_root}/BizHawk-${BIZHAWK_VERSION}-linux-x64"

echo "Downloading ${ARCHIVE_URL}"
curl --fail --location --show-error --output "${archive_path}" "${ARCHIVE_URL}"

echo "${ARCHIVE_SHA256}  ${archive_path}" | sha256sum --check -
tar --extract --gzip --file "${archive_path}" --directory "${staging_root}"

client_library="${staged_install}/dll/BizHawk.Client.Common.dll"
camhack_example="${staged_install}/Lua/GBA/SonicAdvance_CamHack.lua"
if [[ ! -f "${staged_install}/EmuHawk.exe" ]]; then
    echo "ERROR: downloaded archive does not contain EmuHawk.exe" >&2
    exit 1
fi
if [[ ! -f "${client_library}" || ! -f "${camhack_example}" ]]; then
    echo "ERROR: downloaded archive does not have the expected BizHawk 2.11 layout" >&2
    exit 1
fi
if ! grep --binary-files=text -Fq "invisibleemulation" "${client_library}" \
        || ! grep -Fq "client.invisibleemulation" "${camhack_example}"; then
    echo "ERROR: downloaded BizHawk does not provide client.invisibleemulation" >&2
    exit 1
fi

mv --no-clobber --no-target-directory -- "${staged_install}" "${destination}"
if [[ -e "${staged_install}" || -L "${staged_install}" ]]; then
    echo "ERROR: BizHawk destination appeared while installing: ${destination}" >&2
    echo "The existing destination was left untouched." >&2
    exit 1
fi
echo "Installed BizHawk ${BIZHAWK_VERSION} Linux x64 at ${destination}"
