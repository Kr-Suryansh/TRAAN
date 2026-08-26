"""
app/services/optimizer/stub.py — TEMPORARY STUB for OR-Tools allocation.

REPLACE WITH REAL IMPLEMENTATION from Component E.

Returns an empty assignment plan so the incident pipeline can run end-to-end
without OR-Tools. Callers must handle an empty list gracefully (they should —
the contract allows zero recommendations).

Replace by:
1. Component E implementing app/services/optimizer/ortools_solver.py
2. Importing from ortools_solver.py instead of stub.py in any caller
3. Deleting this file once real implementation is verified
"""

import logging
from typing import Any

logger = logging.getLogger(__name__)


async def allocate_resources(
    incidents: list[dict[str, Any]],
    resources: list[dict[str, Any]],
    constraints: dict[str, Any] | None = None,
) -> list[dict[str, Any]]:
    """
    STUB — returns an empty allocation plan.
    Real implementation uses OR-Tools to assign resources to incidents.

    Never dispatches anything automatically — the plan is only a recommendation.
    Human authority confirmation is required before any dispatch is recorded.
    """
    logger.warning(
        "Optimizer stub: allocate_resources called with %d incidents, "
        "%d resources — returning empty plan. Replace with Component E.",
        len(incidents),
        len(resources),
    )
    return []
