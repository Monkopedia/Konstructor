# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Konstructor is a Kotlin-based 3D model builder with a web UI. Users write Kotlin scripts that generate 3D models (CSG operations), which are compiled and rendered in a browser via a Three.js/WebGL frontend.

## Build System

Gradle 8.6 multi-module project using a version catalog (`gradle/libs.versions.toml`). Kotlin wrappers version is in `gradle.properties`.

### Modules

- **protocol** — Kotlin Multiplatform (JVM + JS). Shared RPC service interfaces using ksrpc. Defines the API contract between backend and frontend.
- **lib** — JVM only. CSG geometry library integration (kcsg). Built as a shadow JAR that gets bundled into the backend as a resource (`lib-all.raj`), used to compile and execute user scripts in an isolated classloader.
- **frontend** — Kotlin/JS (IR). React-based browser UI using kotlin-wrappers (MUI, emotion, react-router). Includes CodeMirror editor and Three.js GL rendering. Compiled via webpack and bundled into backend resources.
- **backend** — Kotlin Multiplatform (JVM target). Ktor server that serves the frontend, manages workspaces/konstructions, compiles user scripts via lib, and communicates with frontend over WebSockets (ksrpc).

### Key Commands

```bash
# Full build (compiles everything, bundles frontend+lib into backend shadow JAR)
./gradlew shadowJar

# Build backend only (still requires frontend + lib artifacts)
./gradlew :backend:shadowJar

# Build frontend JS bundle (development)
./gradlew :frontend:jsBrowserDevelopmentWebpack

# Build frontend JS bundle (production)
./gradlew :frontend:jsBrowserDistribution

# Build lib shadow JAR
./gradlew :lib:shadowJar

# Run tests
./gradlew test

# Run a single test class
./gradlew :backend:jvmTest --tests "com.monkopedia.konstructor.SomeTest"

# Lint/format check
./gradlew autostyleCheck

# Auto-fix formatting
./gradlew autostyleApply
```

### Build Flow

The backend's `shadowJar` task orchestrates the full build:
1. `:lib:shadowJar` produces `lib-all.jar` (renamed to `.raj`)
2. `:frontend:jsBrowserDevelopmentWebpack` (or production variant) produces the JS bundle
3. Both are copied into backend's `build/importedResources/` as resources
4. Backend compiles and packages everything into a single fat JAR

Use `-Prelease` to switch the frontend from development to production webpack build.

## Architecture Notes

- **ksrpc** (com.monkopedia.ksrpc) is the author's own RPC framework. Service interfaces are defined in `protocol/` and generate client/server stubs. Communication happens over Ktor WebSockets.
- **hauler** is the author's own logging/shipping library used for structured log delivery.
- User scripts are compiled at runtime by the backend using the Kotlin compiler, loaded in an isolated classloader with the lib JAR on the classpath.
- The frontend uses Koin for dependency injection with custom coroutine-aware scopes (`RootScope`, `WorkspaceScope`, `KonstructionScope`).
- CodeMirror bindings in `frontend/src/jsMain/kotlin/.../editor/codemirror/` are manually written Kotlin/JS external declarations.
- Three.js bindings live in `frontend/three-kt/` (vendored wrapper, included via `srcDir`).

## Code Style

- Apache 2.0 license headers on all Kotlin files (enforced by autostyle)
- ktlint formatting (version 0.42.1, android profile)
- JVM target: Java 17
- Compiler flags: `-Xskip-prerelease-check`, `-Xno-param-assertions` on most modules
