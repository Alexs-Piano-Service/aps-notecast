#!/usr/bin/env bash
set -euo pipefail

source_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
output_dir="$(cd "${source_dir}/.." && pwd)"
temporary_dir="$(mktemp -d /tmp/aps-notecast-screenshots.XXXXXX)"
trap 'rm -rf "${temporary_dir}"' EXIT

render_scene() {
  local scene="$1"
  local width="$2"
  local height="$3"
  local output_name="$4"
  local png_path="${temporary_dir}/${scene}.png"

  chromium \
    --headless \
    --no-sandbox \
    --disable-gpu \
    --disable-dev-shm-usage \
    --disable-breakpad \
    --disable-crash-reporter \
    --hide-scrollbars \
    --force-device-scale-factor=1 \
    --run-all-compositor-stages-before-draw \
    --virtual-time-budget=1000 \
    --user-data-dir="${temporary_dir}/chromium-${scene}" \
    --window-size="${width},${height}" \
    --screenshot="${png_path}" \
    "file://${source_dir}/showcase.html?scene=${scene}" >/dev/null 2>&1

  magick "${png_path}" \
    -strip \
    -quality 94 \
    -define webp:method=6 \
    -define webp:thread-level=1 \
    "${output_dir}/${output_name}"
}

render_scene library 720 1560 aps-notecast-midi-library.webp
render_scene playback 720 1560 aps-notecast-midi-playback.webp
render_scene recording 720 1560 aps-notecast-midi-recording.webp
render_scene mixer 720 1560 aps-notecast-midi-channel-mixer.webp
render_scene connection 720 1560 aps-notecast-bluetooth-usb-midi.webp
render_scene hero 1600 900 aps-notecast-android-midi-player.webp

identify \
  "${output_dir}/aps-notecast-midi-library.webp" \
  "${output_dir}/aps-notecast-midi-playback.webp" \
  "${output_dir}/aps-notecast-midi-recording.webp" \
  "${output_dir}/aps-notecast-midi-channel-mixer.webp" \
  "${output_dir}/aps-notecast-bluetooth-usb-midi.webp" \
  "${output_dir}/aps-notecast-android-midi-player.webp"
