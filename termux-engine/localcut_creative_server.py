#!/usr/bin/env python3
from __future__ import annotations

from types import MethodType

from localcut_toolchain import TOOL_SPECS, detect_toolchain


_ALLOWED_TOOLCHAIN_CAPABILITIES = frozenset(spec.capability for spec in TOOL_SPECS)


def validate_toolchain_capabilities(values) -> tuple[str, ...]:
    result: list[str] = []
    for raw in values:
        if not isinstance(raw, str) or raw not in _ALLOWED_TOOLCHAIN_CAPABILITIES:
            raise ValueError("TOOLCHAIN_CAPABILITY_INVALID")
        if raw not in result:
            result.append(raw)
    return tuple(result)


def attach_toolchain_capabilities(server, capabilities):
    validated = validate_toolchain_capabilities(capabilities)
    base_capabilities = server.capabilities

    def capabilities(_self) -> list[str]:
        result = list(base_capabilities())
        for capability in validated:
            if capability not in result:
                result.append(capability)
        return result

    server.capabilities = MethodType(capabilities, server)
    return server


def create_toolchain_server(*args, toolchain_capabilities=None, **kwargs):
    if toolchain_capabilities is None:
        toolchain_capabilities = detect_toolchain()["capabilities"]
    validated = validate_toolchain_capabilities(toolchain_capabilities)

    from localcut_engine import create_server

    server = create_server(*args, **kwargs)
    return attach_toolchain_capabilities(server, validated)
