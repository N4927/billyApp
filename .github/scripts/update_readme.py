"""
Update the README.md "Feature Index" table based on the current repository structure.

Conventions:
- Each top-level directory in repo root (except a small ignore list) is a "feature".
- Inside each feature, each immediate subdirectory is a "document area" (e.g. architecture_client_server).
- Inside each document area, version folders are named: v_<MAJOR>_<MINOR>_<PATCH>.
- The highest semantic version is considered the "latest version" for that area.

The script replaces the section between:
  <!-- FEATURE_INDEX:START ... -->
and
  <!-- FEATURE_INDEX:END -->
in README.md.
"""

from __future__ import annotations

import os
import re
from dataclasses import dataclass
from pathlib import Path
from typing import List, Optional, Tuple

REPO_ROOT = Path(__file__).resolve().parents[2]
README_PATH = REPO_ROOT / "README.md"

FEATURE_IGNORE = {".git", ".github", ".idea", ".vscode", "__pycache__", "scripts"}
VERSION_PREFIX = "v_"


@dataclass
class AreaEntry:
    feature: str
    area: str
    latest_version: str
    path: str


def parse_version_dir(name: str) -> Optional[Tuple[int, int, int]]:
    """Parse a version dir name like v_1_2_3 into (1, 2, 3)."""
    if not name.startswith(VERSION_PREFIX):
        return None

    body = name[len(VERSION_PREFIX) :]
    parts = body.split("_")
    if len(parts) != 3:
        return None

    try:
        return tuple(int(p) for p in parts)  # type: ignore[return-value]
    except ValueError:
        return None


def collect_feature_entries() -> List[AreaEntry]:
    entries: List[AreaEntry] = []

    for feature_dir in sorted(
        [p for p in REPO_ROOT.iterdir() if p.is_dir() and p.name not in FEATURE_IGNORE and not p.name.startswith("_")]
    ):
        feature_name = feature_dir.name

        # Each immediate subdir is a "document area"
        area_dirs = [
            p for p in feature_dir.iterdir() if p.is_dir() and not p.name.startswith(".") and not p.name.startswith("_")
        ]
        if not area_dirs:
            # Feature with no areas yet
            entries.append(
                AreaEntry(
                    feature=feature_name,
                    area="(root)",
                    latest_version="—",
                    path=feature_name,
                )
            )
            continue

        for area_dir in sorted(area_dirs, key=lambda p: p.name):
            area_name = area_dir.name

            # Look for version directories
            version_dirs = [
                (parse_version_dir(p.name), p)
                for p in area_dir.iterdir()
                if p.is_dir()
            ]

            valid_versions = [(v, p) for v, p in version_dirs if v is not None]

            if valid_versions:
                # Pick highest semantic version
                valid_versions.sort(key=lambda t: t[0], reverse=True)
                latest_version_tuple, latest_dir = valid_versions[0]
                latest_version_name = latest_dir.name
                path = f"{feature_name}/{area_name}/{latest_version_name}"
                entries.append(
                    AreaEntry(
                        feature=feature_name,
                        area=area_name,
                        latest_version=latest_version_name,
                        path=path,
                    )
                )
            else:
                # No version dirs yet (e.g. WIP area)
                path = f"{feature_name}/{area_name}"
                entries.append(
                    AreaEntry(
                        feature=feature_name,
                        area=area_name,
                        latest_version="—",
                        path=path,
                    )
                )

    return entries


def build_table(entries: List[AreaEntry]) -> str:
    header = "| Feature | Area / Document | Latest Version | Path |"
    sep = "|--------|-----------------|----------------|------|"

    lines = [header, sep]
    for e in entries:
        lines.append(
            f"| `{e.feature}` | `{e.area}` | `{e.latest_version}` | `{e.path}` |"
        )
    return "\n".join(lines)


def replace_in_readme(table: str) -> None:
    text = README_PATH.read_text(encoding="utf-8")

    pattern = re.compile(
        r"(<!-- FEATURE_INDEX:START.*?-->\s*)(.*?)(\s*<!-- FEATURE_INDEX:END -->)",
        re.DOTALL,
    )

    replacement = r"\1" + "\n" + table + "\n" + r"\3"
    new_text, count = pattern.subn(replacement, text)

    if count == 0:
        raise RuntimeError(
            "Could not find FEATURE_INDEX markers in README.md. "
            "Make sure the START and END markers exist."
        )

    if new_text != text:
        README_PATH.write_text(new_text, encoding="utf-8")
        print("README.md updated.")
    else:
        print("README.md already up to date.")


def main() -> None:
    entries = collect_feature_entries()
    table = build_table(entries)
    replace_in_readme(table)


if __name__ == "__main__":
    main()