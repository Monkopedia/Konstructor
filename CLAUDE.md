# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Konstructor is a Kotlin-based 3D model builder with a web UI. Users write Kotlin scripts that generate 3D models (CSG operations), which are compiled and rendered in a browser.

## Build System

Gradle 9.4.1 multi-module project using a version catalog (`gradle/libs.versions.toml`).

### Modules

- **protocol** — Kotlin Multiplatform (JVM + JS + WasmJS). Shared RPC service interfaces using ksrpc. Defines the API contract between backend and frontend.
- **lib** — JVM only. CSG geometry library integration (kcsg). Built as a shadow JAR that gets bundled into the backend as a resource (`lib-all.raj`).
- **frontend** — Kotlin/WasmJS + Compose Multiplatform. Material3 UI with kodemirror code editor. Compiled to WebAssembly and bundled into backend resources.
- **backend** — Kotlin Multiplatform (JVM target). Ktor server that serves the frontend, manages workspaces/konstructions, compiles user scripts via lib, and communicates with frontend over WebSockets (ksrpc).
- **e2e** — JVM. End-to-end tests using Playwright + ksrpc client. Run with `./gradlew :e2e:test -Pe2e`.

### Key Commands

```bash
# Full build (compiles everything, bundles frontend+lib into backend shadow JAR)
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew shadowJar

# Build frontend wasmJs distribution
./gradlew :frontend:wasmJsBrowserDistribution

# Build lib shadow JAR
./gradlew :lib:shadowJar

# Run backend/protocol unit+integration tests
./gradlew test

# Run e2e tests (requires full build)
./gradlew :e2e:test -Pe2e

# Run a single test class
./gradlew :backend:jvmTest --tests "*.KonstructorImplTest"

# Lint/format check
./gradlew autostyleCheck

# Clean build (sometimes needed for Wasm compiler cache issues)
./gradlew clean shadowJar
```

### Build Flow

The backend's `shadowJar` task orchestrates the full build:
1. `:lib:shadowJar` produces `lib-all.jar` (renamed to `.raj`)
2. `:frontend:wasmJsBrowserDistribution` produces the Wasm/JS bundle
3. Both are copied into backend's `build/importedResources/` as resources
4. Backend compiles and packages everything into a single fat JAR

## Architecture Notes

- **ksrpc** (com.monkopedia.ksrpc) is the author's own RPC framework. Service interfaces are defined in `protocol/` and generate client/server stubs. Communication happens over Ktor WebSockets.
- **hauler** is the author's own logging/shipping library used for structured log delivery.
- **kodemirror** is the author's Compose Multiplatform CodeMirror wrapper (com.monkopedia.kodemirror on Maven Central).
- User scripts are compiled at runtime by the backend using the Kotlin compiler, loaded in an isolated classloader with the lib JAR on the classpath.
- The frontend uses Compose Multiplatform for UI rendering (Material3, wasmJs target).
- Koin for dependency injection with ViewModels (`koin-compose-viewmodel`).
- State management via Kotlin StateFlows, collected in Composables via `collectAsState()`.
- TestBridge pattern for Playwright e2e testing: exposes app state to `globalThis.__konstructor` since Compose renders to a WebGL canvas (no DOM elements for Playwright selectors).

## Frontend Structure (src/wasmJsMain/)

```
frontend/src/wasmJsMain/kotlin/com/monkopedia/konstructor/frontend/
├── Main.kt              — ComposeViewport entry point
├── KonstruktorApp.kt    — Root composable (Koin + MaterialTheme)
├── Theme.kt             — Material3 dark color scheme
├── TestBridge.kt        — Playwright e2e testing bridge
├── di/AppModule.kt      — Koin module definitions
├── viewmodel/           — ViewModels (ServiceHolder, SpaceList, Workspace, Konstruction, Settings, NavigationDialog)
├── ui/                  — Composable screens
│   ├── Initializer.kt   — Routes to Loading/Empty/Main based on state
│   ├── MainScreen.kt    — 50/50 split layout
│   ├── TopBar.kt        — Material3 TopAppBar
│   ├── editor/          — kodemirror editor integration
│   ├── navigation/      — Workspace/Konstruction list
│   ├── settings/        — Settings, GL settings, Selection panes
│   └── dialogs/         — Create/Edit workspace/konstruction dialogs
└── threejs/             — Three.js Wasm interop bindings (Phase 2)
```

## Code Style

- Apache 2.0 license headers on all Kotlin files (enforced by autostyle)
- ktlint formatting (version 0.42.1, android profile)
- JVM target: Java 17
- Requires JDK 21 to build (`JAVA_HOME=/usr/lib/jvm/java-21-openjdk`)
- Compiler flags: `-Xskip-prerelease-check` on most modules

## Testing

- **Backend unit tests**: protocol types serialization, Config, PathController, workspace/konstruction CRUD
- **Backend integration tests**: full lifecycle tests, compile+execute (requires `-Dintegration=true`)
- **E2e tests**: Playwright-based, uses TestBridge for Compose canvas interaction and ksrpc API for server operations
- **Screenshot baselines**: pre-migration screenshots in `e2e/baselines/` for visual regression comparison
