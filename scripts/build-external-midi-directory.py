#!/usr/bin/env python3
"""Build an APS NoteCast-compatible external MIDI directory JSON file.

The output can be used as an input to an external search/database tool, or
served by a small endpoint that filters the `results` array by query.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import BinaryIO
from urllib.parse import quote


MIDI_EXTENSIONS = (".mid", ".midi", ".kar")
PEDAL_CONTROLLERS = {64, 66, 67}
DEFAULT_TEMPO_US_PER_QUARTER = 500_000
GENERIC_TRACK_NAMES = {
    "control track",
    "conductor track",
    "tempo track",
    "track",
    "untitled",
    "upper",
    "lower",
    "right",
    "left",
    "up",
    "down",
}


@dataclass
class MidiAnalysis:
    midi_format: int
    track_count: int
    division: int
    channels: set[int] = field(default_factory=set)
    note_channels: set[int] = field(default_factory=set)
    pedal_channels: set[int] = field(default_factory=set)
    program_numbers: dict[int, set[int]] = field(default_factory=dict)
    track_names: list[str] = field(default_factory=list)
    max_tick: int = 0
    tempo_events: list[tuple[int, int]] = field(default_factory=list)

    @property
    def pedal_only_channels(self) -> set[int]:
        return self.pedal_channels - self.note_channels

    @property
    def ticks_per_quarter(self) -> int | None:
        return self.division if self.division > 0 else None

    @property
    def duration_us(self) -> int | None:
        ticks_per_quarter = self.ticks_per_quarter
        if ticks_per_quarter is None:
            return None

        tempo_events = sorted(self.tempo_events) or [(0, DEFAULT_TEMPO_US_PER_QUARTER)]
        current_tempo = DEFAULT_TEMPO_US_PER_QUARTER
        current_tick = 0
        elapsed_us = 0.0

        for tick, tempo in tempo_events:
            if tick > self.max_tick:
                break
            if tick > current_tick:
                elapsed_us += (tick - current_tick) * current_tempo / ticks_per_quarter
            current_tick = tick
            current_tempo = tempo

        if self.max_tick > current_tick:
            elapsed_us += (self.max_tick - current_tick) * current_tempo / ticks_per_quarter
        return int(round(elapsed_us))


class MidiParseError(ValueError):
    pass


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Create an APS NoteCast external MIDI directory JSON file."
    )
    parser.add_argument("midi_root", type=Path, help="Directory containing MIDI files to index.")
    parser.add_argument(
        "--base-url",
        required=True,
        help="HTTPS URL prefix where midi_root is hosted, for example https://example.com/midi/",
    )
    parser.add_argument(
        "--output",
        "-o",
        type=Path,
        default=Path("external-midi-directory.json"),
        help="Output JSON path. Use '-' to write to stdout.",
    )
    parser.add_argument("--source-name", default="External MIDI Directory", help="Source label for each result.")
    parser.add_argument("--source-page-base", default="", help="Optional URL prefix for source pages.")
    parser.add_argument("--license", default="", help="Optional license or rights label.")
    parser.add_argument("--license-url", default="", help="Optional license or rights URL.")
    parser.add_argument("--maintainer", default="", help="Optional source maintainer label.")
    parser.add_argument(
        "--extensions",
        default=",".join(MIDI_EXTENSIONS),
        help="Comma-separated file extensions to include. Default: .mid,.midi,.kar",
    )
    parser.add_argument(
        "--compact",
        action="store_true",
        help="Write compact JSON instead of indented JSON.",
    )
    parser.add_argument(
        "--strict",
        action="store_true",
        help="Fail if any matching file cannot be parsed as Standard MIDI.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    root = args.midi_root.expanduser().resolve()
    if not root.is_dir():
        print(f"error: MIDI root is not a directory: {root}", file=sys.stderr)
        return 2

    extensions = normalize_extensions(args.extensions)
    base_url = normalize_url_prefix(args.base_url)
    source_page_base = normalize_url_prefix(args.source_page_base) if args.source_page_base else ""

    results = []
    used_ids: set[int] = set()
    skipped = 0

    for path in iter_midi_files(root, extensions):
        relative_path = path.relative_to(root).as_posix()
        try:
            analysis = analyze_midi_file(path)
        except (OSError, MidiParseError) as exc:
            skipped += 1
            message = f"warning: skipped {relative_path}: {exc}"
            if args.strict:
                print(message, file=sys.stderr)
                return 1
            print(message, file=sys.stderr)
            continue

        result_id = stable_result_id(relative_path, used_ids)
        url_path = quote(relative_path, safe="/")
        result = {
            "id": result_id,
            "title": infer_title(path, analysis),
            "filename": path.name,
            "relative_path": relative_path,
            "url": base_url + url_path,
            "folder": path.parent.relative_to(root).as_posix() if path.parent != root else "",
            "midi_type": "midi",
            "midi_format": analysis.midi_format,
            "track_count": analysis.track_count,
            "channel_count": len(analysis.channels),
            "channels": sorted(analysis.channels),
            "note_channels": sorted(analysis.note_channels),
            "pedal_only_channels": sorted(analysis.pedal_only_channels),
            "file_size": path.stat().st_size,
            "sha256": sha256_file(path),
            "source": args.source_name,
        }
        if analysis.ticks_per_quarter is not None:
            result["ticks_per_quarter"] = analysis.ticks_per_quarter
        if analysis.duration_us is not None:
            result["duration_us"] = analysis.duration_us
        if analysis.track_names:
            result["track_names"] = analysis.track_names
        if analysis.program_numbers:
            result["program_numbers_by_channel"] = {
                str(channel): sorted(programs)
                for channel, programs in sorted(analysis.program_numbers.items())
            }
        if args.license:
            result["license"] = args.license
        if args.license_url:
            result["license_url"] = args.license_url
        if args.maintainer:
            result["maintainer"] = args.maintainer
        if source_page_base:
            result["source_url"] = source_page_base + url_path
        results.append(result)

    payload = {
        "ok": True,
        "schema": "aps-notecast-external-midi-directory-v1",
        "generated_at": datetime.now(timezone.utc).replace(microsecond=0).isoformat(),
        "source": args.source_name,
        "base_url": base_url,
        "count": len(results),
        "skipped": skipped,
        "results": results,
    }
    write_json(payload, args.output, compact=args.compact)
    print(f"indexed {len(results)} MIDI file(s); skipped {skipped}", file=sys.stderr)
    return 0


def normalize_extensions(raw: str) -> tuple[str, ...]:
    extensions = []
    for part in raw.split(","):
        clean = part.strip().lower()
        if not clean:
            continue
        extensions.append(clean if clean.startswith(".") else f".{clean}")
    return tuple(extensions) or MIDI_EXTENSIONS


def normalize_url_prefix(raw: str) -> str:
    clean = raw.strip()
    if not clean:
        raise SystemExit("error: URL prefix cannot be blank")
    if not clean.startswith("https://"):
        print("warning: APS NoteCast only downloads external MIDI over HTTPS.", file=sys.stderr)
    return clean if clean.endswith("/") else f"{clean}/"


def iter_midi_files(root: Path, extensions: tuple[str, ...]) -> list[Path]:
    return sorted(
        (path for path in root.rglob("*") if path.is_file() and path.suffix.lower() in extensions),
        key=lambda path: path.relative_to(root).as_posix().lower(),
    )


def stable_result_id(relative_path: str, used_ids: set[int]) -> int:
    seed = relative_path
    attempt = 0
    while True:
        digest = hashlib.sha256(seed.encode("utf-8")).digest()
        value = int.from_bytes(digest[:4], "big") & 0x7FFFFFFF
        if value not in used_ids:
            used_ids.add(value)
            return value
        attempt += 1
        seed = f"{relative_path}#{attempt}"


def analyze_midi_file(path: Path) -> MidiAnalysis:
    data = path.read_bytes()
    offset = 0
    chunk_type, chunk_data, offset = read_chunk(data, offset)
    if chunk_type != b"MThd":
        raise MidiParseError("missing MThd header")
    if len(chunk_data) < 6:
        raise MidiParseError("short MIDI header")

    midi_format = read_u16(chunk_data, 0)
    track_count = read_u16(chunk_data, 2)
    division = read_i16(chunk_data, 4)
    analysis = MidiAnalysis(midi_format=midi_format, track_count=track_count, division=division)

    parsed_tracks = 0
    while offset < len(data) and parsed_tracks < track_count:
        chunk_type, chunk_data, offset = read_chunk(data, offset)
        if chunk_type != b"MTrk":
            continue
        parse_track(chunk_data, analysis)
        parsed_tracks += 1

    if parsed_tracks == 0:
        raise MidiParseError("no MTrk chunks found")
    return analysis


def parse_track(track: bytes, analysis: MidiAnalysis) -> None:
    offset = 0
    tick = 0
    running_status: int | None = None

    while offset < len(track):
        delta, offset = read_varlen(track, offset)
        tick += delta
        analysis.max_tick = max(analysis.max_tick, tick)
        if offset >= len(track):
            break

        status = track[offset]
        if status & 0x80:
            offset += 1
            if status < 0xF0:
                running_status = status
        elif running_status is not None:
            status = running_status
        else:
            raise MidiParseError("running status without previous channel status")

        if 0x80 <= status <= 0xEF:
            event_type = status & 0xF0
            channel = (status & 0x0F) + 1
            data_len = 1 if event_type in (0xC0, 0xD0) else 2
            event_data = track[offset : offset + data_len]
            if len(event_data) != data_len:
                raise MidiParseError("truncated MIDI channel event")
            offset += data_len
            analyze_channel_event(analysis, event_type, channel, event_data)
        elif status == 0xFF:
            if offset >= len(track):
                raise MidiParseError("truncated meta event")
            meta_type = track[offset]
            offset += 1
            length, offset = read_varlen(track, offset)
            payload = track[offset : offset + length]
            if len(payload) != length:
                raise MidiParseError("truncated meta payload")
            offset += length
            analyze_meta_event(analysis, tick, meta_type, payload)
            if meta_type == 0x2F:
                break
            running_status = None
        elif status in (0xF0, 0xF7):
            length, offset = read_varlen(track, offset)
            offset += length
            if offset > len(track):
                raise MidiParseError("truncated sysex payload")
            running_status = None
        else:
            raise MidiParseError(f"unsupported system event 0x{status:02x}")


def analyze_channel_event(
    analysis: MidiAnalysis,
    event_type: int,
    channel: int,
    event_data: bytes,
) -> None:
    analysis.channels.add(channel)
    if event_type == 0x90 and len(event_data) >= 2 and event_data[1] > 0:
        analysis.note_channels.add(channel)
    elif event_type == 0xB0 and len(event_data) >= 2 and event_data[0] in PEDAL_CONTROLLERS:
        analysis.pedal_channels.add(channel)
    elif event_type == 0xC0 and event_data:
        analysis.program_numbers.setdefault(channel, set()).add(event_data[0])


def analyze_meta_event(analysis: MidiAnalysis, tick: int, meta_type: int, payload: bytes) -> None:
    if meta_type == 0x03:
        name = decode_meta_text(payload)
        if name and name not in analysis.track_names:
            analysis.track_names.append(name)
    elif meta_type == 0x51 and len(payload) == 3:
        tempo = int.from_bytes(payload, "big")
        if tempo > 0:
            analysis.tempo_events.append((tick, tempo))


def infer_title(path: Path, analysis: MidiAnalysis) -> str:
    for name in analysis.track_names:
        if is_useful_track_title(name):
            return name
    return clean_filename_title(path) or path.name


def is_useful_track_title(name: str) -> bool:
    clean = re.sub(r"\s+", " ", name.strip().strip(":")).lower()
    clean = re.sub(r"[:\s]*\d+$", "", clean).strip()
    if len(clean) < 4:
        return False
    if clean in GENERIC_TRACK_NAMES:
        return False
    if re.fullmatch(r"(track|channel|part)\s*\d*", clean):
        return False
    return True


def clean_filename_title(path: Path) -> str:
    title = path.stem
    title = re.sub(r"[_\-.]+", " ", title)
    title = re.sub(r"\bdemo\b", "", title, flags=re.IGNORECASE)
    title = re.sub(r"\s+", " ", title).strip()
    return " ".join(capitalize_title_word(word) for word in title.split())


def capitalize_title_word(word: str) -> str:
    if not word or word.isupper():
        return word
    return word[0].upper() + word[1:]


def decode_meta_text(payload: bytes) -> str:
    for encoding in ("utf-8", "latin-1"):
        try:
            return payload.decode(encoding).strip("\x00\r\n\t ")
        except UnicodeDecodeError:
            continue
    return ""


def read_chunk(data: bytes, offset: int) -> tuple[bytes, bytes, int]:
    if offset + 8 > len(data):
        raise MidiParseError("truncated chunk header")
    chunk_type = data[offset : offset + 4]
    length = read_u32(data, offset + 4)
    start = offset + 8
    end = start + length
    if end > len(data):
        raise MidiParseError(f"truncated {chunk_type.decode('ascii', errors='replace')} chunk")
    return chunk_type, data[start:end], end


def read_varlen(data: bytes, offset: int) -> tuple[int, int]:
    value = 0
    for _ in range(4):
        if offset >= len(data):
            raise MidiParseError("truncated variable-length quantity")
        byte = data[offset]
        offset += 1
        value = (value << 7) | (byte & 0x7F)
        if byte & 0x80 == 0:
            return value, offset
    raise MidiParseError("variable-length quantity is too long")


def read_u16(data: bytes, offset: int) -> int:
    return int.from_bytes(data[offset : offset + 2], "big", signed=False)


def read_i16(data: bytes, offset: int) -> int:
    return int.from_bytes(data[offset : offset + 2], "big", signed=True)


def read_u32(data: bytes, offset: int) -> int:
    return int.from_bytes(data[offset : offset + 4], "big", signed=False)


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as input_file:
        copy_to_digest(input_file, digest)
    return digest.hexdigest()


def copy_to_digest(input_file: BinaryIO, digest: "hashlib._Hash") -> None:
    while True:
        chunk = input_file.read(1024 * 1024)
        if not chunk:
            return
        digest.update(chunk)


def write_json(payload: dict, output: Path, compact: bool) -> None:
    kwargs = {"ensure_ascii": True}
    if compact:
        kwargs["separators"] = (",", ":")
    else:
        kwargs["indent"] = 2
    text = json.dumps(payload, **kwargs) + "\n"
    if str(output) == "-":
        sys.stdout.write(text)
        return
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(text, encoding="utf-8")


if __name__ == "__main__":
    raise SystemExit(main())
