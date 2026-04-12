# ADR: Screen-Owned DTO and Fixture Contracts

## Status
Accepted

## Context
This repository follows a self-contained screen architecture where each screen can be copied to another app with minimal dependencies. Screens share some backend resources (tables, RPCs), which introduces hidden coupling risk: a backend or DTO change can break another screen unexpectedly.

## Decision
Each screen owns its contract boundary artifacts:

- Screen DTOs (`@Serializable` models) are owned by that screen package.
- RPC JSON fixtures are owned per screen under test resources.
- Contract tests are owned per screen and decode only that screen's DTOs.
- Fixtures are never shared between screens, even when two screens call the same RPC.

## Rules
- Any response shape change must update the affected screen fixtures and contract tests in the same change set.
- A screen can consume another screen's backend RPC, but it must define and test its own DTO view of the response.
- SQL contract checks validate business invariants and mandatory RPC output keys.
- Local and CI gates must run contract tests and `assembleDebug`.

## Consequences
- Positive: safer autonomous evolution of self-contained screens and easier copy/paste portability across apps.
- Positive: contract failures are localized to the screen that owns the DTO contract.
- Trade-off: duplicated fixtures across screens are intentional and preferred over implicit coupling.
