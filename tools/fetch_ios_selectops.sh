#!/usr/bin/env bash
# Hämtar Googles officiella TensorFlowLiteSelectTfOps 2.17.0 (Flex-delegaten för
# BirdNET:s FlexRFFT) och packar upp till iosApp/Frameworks/. SHA-256-pinnad —
# vid mismatch avbryts bygget (samma princip som i2a:s downloadFlex16kJniLibs).
# Arkivet är 1,1 GB uppackat och gitignoreras; detta script är enda källan.
# Se docs/superpowers/research/2026-08-15-ios-i3-flex-select-ops-research.md.
set -euo pipefail

VERSION="2.17.0"
URL="https://dl.google.com/tflite-release/ios/prod/tensorflow/lite/release/ios/release/32/20240729-115310/TensorFlowLiteSelectTfOps/${VERSION}/224693067351224e/TensorFlowLiteSelectTfOps-${VERSION}.tar.gz"
SHA256="bc152ec8ceb1987e78d924d90e1e537b20e8594719c93c951595f33949fe9f85"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEST="${SCRIPT_DIR}/../iosApp/Frameworks/TensorFlowLiteSelectTfOps.xcframework"
MARKER="${DEST}/.fetched-sha256"

if [[ -f "${MARKER}" && "$(cat "${MARKER}")" == "${SHA256}" ]]; then
  exit 0
fi

CACHE_DIR="${HOME}/Library/Caches/birdy"
mkdir -p "${CACHE_DIR}"
TARBALL="${CACHE_DIR}/TensorFlowLiteSelectTfOps-${VERSION}.tar.gz"

if [[ ! -f "${TARBALL}" ]] || ! echo "${SHA256}  ${TARBALL}" | shasum -a 256 -c - >/dev/null 2>&1; then
  echo "Fetching TensorFlowLiteSelectTfOps ${VERSION} (266 MB)..."
  curl -fL --retry 3 -o "${TARBALL}" "${URL}"
fi

echo "${SHA256}  ${TARBALL}" | shasum -a 256 -c - || {
  echo "FEL: SHA-256-mismatch på ${TARBALL} — avbryter." >&2
  rm -f "${TARBALL}"
  exit 1
}

TMP="$(mktemp -d)"
trap 'rm -rf "${TMP}"' EXIT
tar -xzf "${TARBALL}" -C "${TMP}"
rm -rf "${DEST}"
mkdir -p "$(dirname "${DEST}")"
mv "${TMP}/TensorFlowLiteSelectTfOps-${VERSION}/Frameworks/TensorFlowLiteSelectTfOps.xcframework" "${DEST}"
echo "${SHA256}" > "${MARKER}"
echo "TensorFlowLiteSelectTfOps ${VERSION} klar: ${DEST}"
