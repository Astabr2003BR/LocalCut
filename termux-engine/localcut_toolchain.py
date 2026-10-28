#!/usr/bin/env python3
from __future__ import annotations

import json
import shutil
from dataclasses import dataclass
from typing import Callable


@dataclass(frozen=True)
class ToolSpec:
    capability: str
    commands: tuple[str, ...]
    role: str


TOOL_SPECS = (
    ToolSpec("FFMPEG", ("ffmpeg",), "media-render"),
    ToolSpec("FFPROBE", ("ffprobe",), "media-analysis"),
    ToolSpec("BLENDER", ("blender",), "cgi-3d-worker"),
    ToolSpec("OPENTOONZ", ("opentoonz", "OpenToonz"), "2d-animation-worker"),
    ToolSpec("KRITA", ("krita",), "paint-worker"),
    ToolSpec("GIMP", ("gimp", "gimp-3.0"), "image-compositor-worker"),
    ToolSpec("GEGL", ("gegl",), "image-filter-worker"),
)


def detect_toolchain(which: Callable[[str], str | None] = shutil.which) -> dict:
    tools = []
    for spec in TOOL_SPECS:
        executable = None
        for command in spec.commands:
            candidate = which(command)
            if candidate:
                executable = candidate
                break
        tools.append({
            "capability": spec.capability,
            "role": spec.role,
            "available": executable is not None,
            "executable": executable,
        })
    return {
        "schemaVersion": 1,
        "tools": tools,
        "capabilities": [tool["capability"] for tool in tools if tool["available"]],
    }


def main() -> None:
    print(json.dumps(detect_toolchain(), ensure_ascii=False, separators=(",", ":")))


if __name__ == "__main__":
    main()
