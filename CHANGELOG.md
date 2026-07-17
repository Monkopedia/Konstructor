# Changelog

## 0.3.0

Dependency and toolchain catch-up release, plus one logging-provider correctness fix.
No user-facing feature changes.

### Upstream library bumps

- **hauler** 0.4.0 → 0.4.2 (pulls ksrpc 1.1.4 → slf4j 2.0.18 transitively)
- **kcsg** / **kcsg-dsl** 0.4.3 → 0.4.5
- **kodemirror** 0.3.2 → 0.3.5

### Toolchain / stable dependency refresh

- **Kotlin** 2.4.0 → 2.4.10
- **kotlinx-coroutines** 1.10.2 → 1.11.0
- **kotlinx-serialization** 1.10.0 → 1.11.0
- **kotlinx-datetime** 0.7.1 → 0.8.0
- **Ktor** 3.4.2 → 3.5.1
- **Koin** 4.2.0 → 4.2.2
- **Compose Multiplatform** 1.10.3 → 1.11.1
- **AndroidX Lifecycle** 2.10.0 → 2.11.0
- **Clikt** 5.0.3 → 5.1.0
- **Logback** 1.5.18 → 1.5.38
- **Spotless** 8.4.0 → 8.8.0

### Build

- Pin the Node.js version used by the Kotlin/JS + Compose-wasm toolchains to
  **22.11.0** (via the `EnvSpec` API), since the default Compose-wasm Node download
  can land on a 404/incompatible build.

### Fixes

- **HaulerServiceProvider (SLF4J 2.0.x early-bind):** SLF4J 2.0.x binds the MDC
  adapter eagerly — `LoggerFactory.earlyBindMDCAdapter()` calls `getMDCAdapter()`
  *before* `initialize()` runs. The provider previously declared `mdcAdapter` as
  `lateinit` and only assigned it in `initialize()`, so the early call threw
  `UninitializedPropertyAccessException`, killing the compiled user-script
  subprocess. The adapter is now initialized eagerly (matching the same adapter
  type used post-`initialize()`), so `getMDCAdapter()` always returns a valid
  adapter. Behavior after `initialize()` is unchanged.

### Held (intentionally not bumped)

- **lsp** — WIP; kept on the current version.
- **shadow** — blocked by issue #64.
- **slf4j** — catalog stays at 2.0.17; the real resolved version is 2.0.18,
  pulled transitively via hauler → ksrpc 1.1.4.

### Notes

- Does **not** close #171. The released kodemirror 0.3.5 contains no #171 changes;
  the reported ½-character offset was a measurement artifact, and the real
  layout-readiness race fix lands in a later kodemirror patch.
- The `lib` shadow JAR now pins `archiveVersion` to empty so the project version
  (0.3.0) does not rename the artifact; the backend locates it by the fixed
  `lib-all.jar` / `lib-all.raj` name.
