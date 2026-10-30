from __future__ import annotations

from types import MethodType

MANAGED_AUTOMATION_CAPABILITY = "MANAGED_AUTOMATION_V1"


def attach_managed_automation_capability(server):
    base_capabilities = server.capabilities

    def capabilities(_self) -> list[str]:
        result = list(base_capabilities())
        if MANAGED_AUTOMATION_CAPABILITY not in result:
            result.append(MANAGED_AUTOMATION_CAPABILITY)
        return result

    server.capabilities = MethodType(capabilities, server)
    return server
