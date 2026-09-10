# MineIT Mine Game

A MineIT-universe Android mining game experiment focused on making a complex underground mine readable and playable on a phone.

## Current POC — Mine Prism 0.2.0

The previous steerable-digger prototype has been superseded. Version `0.2.0` tests the **Mine Prism** interaction model:

- the mine exists as a 3-axis model (`x`, `y`, depth);
- an isometric geological prism presents the whole mine without requiring a free-flying 3D camera;
- a depth slider peels rock away to reveal deeper workings;
- one ore body visibly continues through multiple depths;
- working levels become visible when the depth slice reaches them;
- **Explode Levels** separates levels vertically while preserving their relationship to the shaft;
- tapping a revealed level unfolds it into a readable top-down operational view;
- pinch zoom and drag pan work in both views.

This is a presentation/interaction POC, not yet a mining simulation. There is deliberately no production, waste, workforce, power, equipment economy or save game in this build.

## Architecture

- `domain/` owns the mine's immutable 3D geometry and Prism state transitions.
- `ui/` owns Compose rendering, projection, hit-testing and transient camera pan/zoom.
- UI renders domain state and dispatches intent; it is not the source of truth for mine geometry.

See [`docs/MINE_PRISM_POC.md`](docs/MINE_PRISM_POC.md) for the current concept and evaluation goals, and [`docs/GAME_IDEA.md`](docs/GAME_IDEA.md) for the broader game discovery.

## CI / APK

GitHub Actions runs domain tests, builds a debug APK and verifies the persistent development signer before publishing the `mineit-mine-game-debug` artifact.
