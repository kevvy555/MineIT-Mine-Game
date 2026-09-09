# MineIT Mine Game

A MineIT universe mini-game focused on operating and physically developing a single mine.

## Current playable slice

Version `0.1.0` is deliberately narrow. It tests the core digging feel on Android:

- blue sky above the surface;
- grey rock below;
- a rectangular digger beginning at the surface;
- start and stop controls;
- a live steering-angle slider;
- smooth steering while digging;
- a persistent excavated shaft/tunnel;
- automatic camera follow as the digger descends;
- depth and heading readouts.

The gameplay concept beyond the prototype is captured in [`docs/GAME_IDEA.md`](docs/GAME_IDEA.md).

## Architecture

- `domain/` owns digger state and deterministic digging rules.
- `ui/` owns Compose rendering and input handling.
- `DigGameViewModel` bridges UI intent to the domain simulation.

This follows the repository guidance in `AGENTS.md`: Compose renders state and dispatches intent; gameplay behaviour remains outside the UI.

## CI / APK

GitHub Actions runs the domain tests, builds a debug APK and signs it with the same persistent development signer pattern used by MineIT Android CI. The APK is uploaded as the `mineit-mine-game-debug` workflow artifact.
