# Changelog

## 1.21.9 - 2026-09-29

This release combines the complete `archerbc2-fix-package` campaign based on
commit `a3d60fe`, the Windows wizard-race and diagnostic-logging follow-up, and
the schema, test, coverage, and lint hardening added before release. The package
manifest was verified before integration and its patch is preserved as the
first campaign commit.

### Wizard data-loss and validation fix

- Fixed the reported two-click wizard failure where entering a ballistic
  coefficient could briefly show a red status message and the saved G7 table
  could contain `{:bc 0.25}` without the required `:mv` field.
- Added an idempotent single-row BC normalizer which writes the default muzzle
  velocity to the correct nested path, `[:profile :coef-g1|:coef-g7 0 :mv]`.
  The original package attempted to seed that value at the root of wizard
  state, where profile validation could not see it.
- Pending formatted text fields are now committed synchronously before the
  wizard validates or advances. This removes the event-ordering race between
  focus transfer, locale-aware number parsing, atom updates, and the first
  press of **Next**.
- Invalid formatted input remains on the current page with an actionable
  validation error. The handler no longer advances on stale state and no
  longer needs a second press.
- Added an explicit ballistic-coefficient type fallback when wizard state does
  not yet contain a valid selection.
- Added focused regressions for G1/G7 row normalization, idempotence, missing
  `:mv`, pending editor commits, and wizard navigation.

### Persistent cross-platform diagnostics

- Added process-wide `java.util.logging` initialization that keeps normal jar
  console output and also writes three rotating 1 MiB files. A small Java
  factory makes the feature work on the bundled Java 8 runtime without relying
  on newer logging APIs.
- Log locations are `%LOCALAPPDATA%\ArcherBC2\logs` on Windows,
  `~/Library/Logs/ArcherBC2` on macOS, and
  `${XDG_STATE_HOME:-~/.local/state}/archerbc2/logs` on Linux. If the preferred
  location is not writable, startup continues with a system-temporary fallback.
- Startup, runtime environment, shutdown, wizard construction/page changes,
  pending-input commits, validation paths, file load/save/import/export,
  device polling, firmware operations, updater activity, status-bar errors,
  routine status updates, alerts, and uncaught exceptions are now observable.
- Every error presented through the bottom status bar is logged at warning
  level; ordinary status text is retained at fine/debug level without flooding
  the console. Validation summaries record problem locations and predicates,
  not complete profiles, profile names, or user notes.
- Logging is defensive: failure to create a log directory or handler cannot
  prevent the application from starting, and repeated initialization is safe.

### Binding lifetime and UI resource fixes from the package

- Added a local, EPL-compatible `seesaw.bind` fork which classpath-shadows the
  upstream namespace and automatically unsubscribes widget-backed binding
  chains after their widgets are permanently orphaned.
- Binding ownership covers property/value/selection sources, tee/funnel and
  nested bindings. It arms only after first displayability and performs an EDT
  deferred recheck so same-turn reparenting remains valid.
- Teardown is idempotent, manual unsubscribe still works, and failures in one
  unsubscribe cannot strand hierarchy listeners or abort remaining cleanup.
- Added `bind-persistent`, explicit `bind* {:host ...}` ownership, and runtime
  `stats`/`gc!` diagnostics for the few intentionally widget-less chains.
- Corrected the upstream `notify-when*` removal argument order, which became
  observable when automatic teardown was introduced.
- The coefficient regeneration chain now identifies its host widget. The
  current-file watcher has a unique per-frame key and is removed when its frame
  closes, avoiding the old-frame/new-frame teardown race.
- Wizard state and its bindings are now created per run instead of accumulating
  for the entire JVM lifetime.
- A display-dependent leak canary covers repeated frame/theme/language swaps,
  profile loads, wizard runs, drag-and-drop windows, and BC-type changes.

### File, profile, and device safety from the package

- A failed or malformed DRG import can no longer replace the complete app atom
  with `nil`; I/O, parsing, and validation complete before the pure state swap.
- JSON `null` values no longer trigger a null-pointer failure. Malformed JSON
  receives a localized, user-facing import error.
- Extension-less **Save As** now validates and reloads the actual `.a7p` path,
  and file-path watchers fire once rather than twice.
- Profile writes now use a same-directory temporary file, flush/sync, and an
  atomic replace where supported, with a safe replacement fallback. The old
  interrupt-and-retry writer could overlap two writes and truncate the only
  copy; it was removed.
- Reads use `Files/readAllBytes`, close resources reliably, detect empty files,
  and avoid partial-read and integer-size bugs.
- Dirty-state checks compare against an in-memory last-persisted snapshot
  rather than reading and parsing the file on the EDT. External modification of
  an already-open file is intentionally not treated as an in-app edit.
- Firmware discovery now really filters `CS10.upg`, handles vector/jar URIs,
  tolerates missing or malformed version segments, and isolates a bad mounted
  device instead of killing the whole notification pass.
- Missing CI-generated updater configuration and firmware resources have safe
  development defaults, allowing a fresh source checkout to start and test.

### Threading, polling, and network hardening from the package

- Background mutations feeding the status bar and file tree are marshalled to
  the Swing EDT with `notify-later`/`invoke-later` at the relevant boundaries.
- The old one-second non-daemon polling thread became a stoppable daemon
  scheduled executor with a three-second fixed delay, per-tick exception
  isolation, a four-second mount-scan budget, mtime-based manifest caching, and
  non-nil current-file invalidation.
- Firmware telemetry/upload and device copy no longer run on the EDT. HTTP
  connect/socket timeouts, wait-cursor restoration, and EDT-safe completion
  reporting prevent a slow network from freezing the UI.
- Update checks now close response streams, use network timeouts, and verify a
  downloaded jar against `profedit.jar.sha256` when the release provides one.
  Older releases without the checksum remain supported.

### Wizard lifecycle and general UI fixes from the package

- Cancelling the wizard's final save dialog no longer terminates the process.
  It opens the editor with the unsaved wizard profile, from which **Save As**
  remains available.
- Save-error retry is escapable, status is cleared for each attempt, and the
  chooser remains parented to the live wizard frame until the decision ends.
- Wizard navigation/state/finalization were split into smaller helpers, while
  preserving back/forward state and maximized-window behavior.
- Menu and toolbar items now share one Action set, avoiding duplicate global
  accelerator registration; toolbar buttons use icon-only views without
  blanking shared menu labels.
- The dirty-discard predicate was renamed and simplified to make its truth
  convention explicit. REPL load/save helpers now delegate to the same safe
  production file paths.
- Side effects were removed from retryable `swap!` functions in profile and
  distance editing; status and list selection updates occur after pure changes.
- Icon lookups are cached, use in-memory fallbacks for missing resources, and
  configuration accepts only the four shipped themes so a hand-edited theme
  cannot brick startup.
- Automatic app startup now requires `-Drepl=true` exactly. This prevents test,
  lint, and coverage namespace loading from unexpectedly opening the GUI.

### Calculator and localization fixes from the package

- Powder-temperature sensitivity preserves row order when deduplicating,
  requires at least two usable rows, ignores a duplicate reference temperature,
  and never divides by zero. Results no longer change at the hash-map ordering
  boundary.
- Restored English calculator and DRG strings that a pair of misnamed Ukrainian
  source bundles had overwritten. Renamed those sources to `*_uk.properties`.
- Added missing zeroing-tab, DRG chooser, validation, save, update, and wizard
  keys in English and Ukrainian; removed the orphan Ukrainian-only `saved-as`
  action key.
- Release CI rejects localization sources without an explicit locale suffix,
  preventing them from overwriting English base bundles.

### Release and updater hardening from the package

- Release artifacts include a SHA-256 file, and the updater verifies it before
  replacing the running jar while retaining backward compatibility.
- The Windows XP-compatible Zulu JRE remains deliberately pinned to 8.0.292
  win_i686 and its download is checksum-verified during packaging.
- Fresh-clone compilation and localization-suffix guards run in release CI.

### Schema and persistence hardening

- Added the previously missing Clojure Spec for `:c-zero-distance-idx`:
  integer, inclusive range 0 through 199.
- Profile validation additionally requires the zero-distance index to address
  an element that actually exists in the current distances vector.
- Added an independent, test-only Malli model of the full persisted profile and
  payload: all required keys, enums, string lengths, numeric bounds, collection
  sizes, BC/custom rows, switches, and the cross-field zero-distance invariant.
- Added a mutation corpus that both Malli and Clojure Spec must reject,
  including the exact reported `:coef-g7 [{:bc 0.25}]` missing-`:mv` shape,
  missing/blank/overlong values, NaN/infinity, bad enums and ranges, collection
  limits, and out-of-vector zero indices.
- Added 200 deterministic Malli-generated profile cross-checks against the
  production Spec and 45 generated payloads round-tripped through both protobuf
  and JSON, with validation by both schema systems after deserialization.
- Malli remains a development/test dependency and is not shipped in the
  release jar.

### Coverage, lint, and CI quality gates

- Added Cloverage with a hard 80% line/form floor and HTML plus LCOV output in
  `target/coverage`. The final Java 8 run covers 84.68% of measured forms and
  87.17% of measured lines.
- Coverage measures deterministic application core namespaces. Headless-host
  unstable Swing assembly/entrypoints and the vendored Seesaw compatibility code are
  exercised through regression/manual lifecycle checks instead. Pronto's
  `defmapper` namespace is excluded only from instrumentation because the macro
  expands a small mapper into 7,029 synthetic forms; its real binary/JSON paths
  remain heavily round-trip tested.
- Added branch-focused tests for profile state helpers, JSON/binary paths,
  current-file watches, safe failure handling, config and user directories,
  mounted-device manifests, firmware resource selection/copy, DRG conversion,
  config cache/locale branches, logging initialization/severity/fallbacks, and
  updater HTTP/file/future branches.
- Added pinned clj-kondo linting across the app and updater source/test trees,
  failing at warning level with no cache. The narrow vendored-Seesaw
  configuration preserves upstream protocol/reify names while application code
  remains warning-free.
- Release CI now runs a clean test suite, the 80% coverage gate, and lint before
  building artifacts. The final app suite has 51 tests and 964 assertions, and
  the updater suite has 2 tests and 9 assertions, all with no failures or
  errors; clj-kondo reports zero errors and zero warnings.
- CI is split into an always-on verification/portable-jar job and trusted
  Windows packaging jobs. Pushes, pull requests, tags, and manual dispatches
  receive downloadable jar/checksum and coverage artifacts; non-PR runs also
  receive the XP-compatible runtime archive and Inno Setup installer.
- Releases are published only for tags matching the `project.clj` version
  (plain or `v`-prefixed), rather than on every `main` push. Publishing uses the
  runner's GitHub CLI and is rerun-safe through clobbered assets.
- The new repository can build without inherited AWS secrets. When firmware
  credentials exist, the eight device firmware files are fetched with bounded,
  retried requests and bundled; when absent, CI emits a notice and produces the
  application artifacts without optional embedded firmware.
- CI-generated application config, updater resources, Launch4j metadata, and
  installer URLs now derive from `github.repository`. The identical workflow
  therefore builds self-updating artifacts correctly in either the original
  `JAremko/ArcherBC2` repository or a published fork such as
  `lpportorino/ArcherBC2`; local source builds retain the original repository as
  a compatibility fallback.
- Official GitHub checkout, Java setup, upload-artifact, and download-artifact
  actions were advanced to their current supported major versions. Artifact
  names, missing-file failures, 30-day retention, job timeouts, read-only
  default permissions, tag-only write permissions, and concurrency cancellation
  are explicit.

### Reflection-free Java 8 release builds

- Enabled `*warn-on-reflection*` in both app and updater uberjar profiles. The
  first clean Java 8 AOT audit exposed 72 warnings in bundled library source;
  the final clean app and updater AOT builds emit zero.
- Added a CI release gate which always cleans before AOT, captures the complete
  app/updater compiler output, and fails packaging if `Reflection warning`
  appears. Previously the uberjar profile explicitly disabled those warnings,
  so reflective regressions were invisible.
- Added local EPL-1.0-compatible Seesaw 1.5 source overlays for `core`, `table`,
  `keymap`, `chooser`, `tree`, and `forms`, alongside the existing `bind` fork.
  The overlays preserve upstream API behavior while making overloaded table
  mutations explicit, using the real `is...` chooser getters, typing tree/form
  listeners and builders, routing window transfer handlers through `JRootPane`,
  and replacing protected reflective painting with Java 8's typed
  `UIManager/getUI` delegate path.
- Removed the reflective `toml.core` wrapper from runtime dependencies. Device
  manifests now use the typed Toml4j Java API directly and explicitly convert
  its Java map keys to keywords.
- Removed the much larger reflective Docjure namespace from runtime. A focused,
  typed Apache POI adapter now owns exactly the workbook create/load/save,
  sheet/row/cell iteration, typed cell values, and column auto-sizing operations
  the application uses.
- Removed Seesaw entirely from the standalone updater. Its one status window,
  EDT dispatch, warning dialog, and text updates now use typed Swing classes
  directly, substantially shrinking its AOT/reflection surface.
- Added headless behavioral contracts for the patched Seesaw table overloads,
  chooser boolean properties, forms, painting, and tree listener dispatch, plus
  OOXML round trips and every supported typed-cell branch in the POI adapter.

### Complete affected-file inventory

Build, release, documentation, and tooling:

- `.github/workflows/release.yml` — push/PR/tag/manual verification, portable
  jar/checksum and coverage artifacts, optional bounded firmware enrichment,
  repository-aware updater/launcher/installer generation, Windows
  runtime/archive/installer builds, version/tag validation, and tag-only
  idempotent GitHub release publication.
- `.github/workflows/build.xml` — corrected canonical ArcherBC2 links, which CI
  rewrites to the repository that owns the workflow run.
- `.github/workflows/install.iss` — installer version and
  publisher/support/update URL now come from CI's release and
  repository-aware environment.
- `.github/workflows/updater/src/updater/core.clj` — release checksum generation
  and verification, repository-resource-derived release/download URLs, and a
  typed direct-Swing status UI with no Seesaw/reflection dependency.
- `.github/workflows/updater/project.clj` — reflection warnings enabled for AOT
  and the now-unneeded Seesaw dependency removed.
- `.github/workflows/updater/resources/release-repository.txt` — local/upstream
  repository fallback overwritten with `github.repository` for CI artifacts.
- `.github/workflows/updater/test/updater/core_test.clj` — repository-derived
  URL and retry/exhaustion contracts (2 tests, 9 assertions).
- `.clj-kondo/config.edn` — Pronto macro declaration and narrow lint
  compatibility for the pinned upstream Seesaw source style.
- `lib/lein-cloverage/lein-cloverage/1.2.4/_remote.repositories` — pinned local
  repository metadata for the coverage plugin.
- `lib/lein-cloverage/lein-cloverage/1.2.4/lein-cloverage-1.2.4.jar` — pinned
  coverage plugin artifact, consistent with the repository's offline release
  dependency layout.
- `lib/lein-cloverage/lein-cloverage/1.2.4/lein-cloverage-1.2.4.jar.sha1` —
  pinned artifact checksum.
- `lib/lein-cloverage/lein-cloverage/1.2.4/lein-cloverage-1.2.4.pom` — pinned
  coverage plugin descriptor.
- `lib/lein-cloverage/lein-cloverage/1.2.4/lein-cloverage-1.2.4.pom.sha1` —
  pinned descriptor checksum.
- `project.clj` — version 1.21.9, Malli, Cloverage, clj-kondo profile/alias,
  headless-test property, coverage scope/reports/threshold, reflection-visible
  AOT, direct Toml4j/Apache POI dependencies, and removal of reflective wrapper
  libraries.
- `README.md` — cross-platform log discovery, captured-event/privacy notes,
  development verification commands/scope, and CI artifact/release behavior.
- `CHANGELOG.md` — this full campaign record and file inventory.

Localization resources:

- `loc/uk/actions_uk.properties` — removed the orphan action key.
- `loc/uk/app_uk.properties` — added zeroing-tab text.
- `loc/uk/calc_uk.properties` — renamed from `loc/uk/calc.properties` so CI
  cannot overwrite the English base bundle.
- `loc/uk/cdf_uk.properties` — renamed from `loc/uk/cdf.properties` for the
  same locale-suffix guarantee.
- `loc/uk/frames_uk.properties` — added wizard unsaved/cancel lifecycle text.
- `loc/uk/rosetta_uk.properties` — added invalid JSON reporting.
- `loc/uk/widgets_uk.properties` — added missing chooser/save/status strings.
- `resources/tvt/a7/profedit/app.properties` — English zeroing-tab key.
- `resources/tvt/a7/profedit/calc.properties` — restored English calculator
  strings.
- `resources/tvt/a7/profedit/cdf.properties` — restored English DRG strings.
- `resources/tvt/a7/profedit/frames.properties` — English wizard lifecycle text.
- `resources/tvt/a7/profedit/rosetta.properties` — invalid JSON text.
- `resources/tvt/a7/profedit/widgets.properties` — missing chooser/save/status
  text.

Java and vendored runtime code:

- `src/java/loggingutil/FileHandlerFactory.java` — Java 8-compatible rotating
  file-handler construction.
- `src/java/powder/SensitivityCalculator.java` — safe duplicate-temperature and
  zero-divider handling.
- `src/seesaw/bind.clj` — lifecycle-aware local Seesaw binding implementation,
  safe unsubscription, ownership escapes, and diagnostics.
- `src/seesaw/core.clj` — pinned EPL Seesaw core with non-reflective layout,
  table-column, painting, root-pane, and transfer-handler paths.
- `src/seesaw/table.clj` — pinned EPL table model with explicit non-reflective
  row add/insert behavior and preserved full-row metadata.
- `src/seesaw/keymap.clj` — pinned EPL keymap with typed frame/root-pane access.
- `src/seesaw/chooser.clj` — pinned EPL chooser with typed file construction and
  correct boolean setters/getters.
- `src/seesaw/tree.clj` — pinned EPL tree events/listeners with typed arrays,
  models, and listener dispatch.
- `src/seesaw/forms.clj` — pinned EPL JGoodies forms adapter with typed builders,
  layouts, sizes, and overload selection.

Application namespaces:

- `src/tvt/a7/profedit/actions.clj` — action reuse and corrected discard flow.
- `src/tvt/a7/profedit/app.clj` — EDT-safe firmware flow, explicit content
  constructor, diagnostics startup/shutdown, and exact REPL auto-start flag.
- `src/tvt/a7/profedit/asi.clj` — logging for user-facing reports and alerts.
- `src/tvt/a7/profedit/ballistic.clj` — explicit host ownership for the
  widget-less coefficient regeneration binding.
- `src/tvt/a7/profedit/calc.clj` — deterministic, validated sensitivity input.
- `src/tvt/a7/profedit/cdf.clj` — transactional DRG import.
- `src/tvt/a7/profedit/config.clj` — safe config defaults, shipped-theme
  validation, cached icons, and placeholder fallbacks.
- `src/tvt/a7/profedit/distances.clj` — pure distance mutations with preserved
  selection/zeroing behavior and use of the typed local workbook adapter.
- `src/tvt/a7/profedit/fio.clj` — atomic/read-safe persistence, device polling,
  firmware selection, dirty snapshots, diagnostics, corrected save path, and
  direct typed Toml4j manifest parsing.
- `src/tvt/a7/profedit/frames.clj` — wizard lifecycle/decomposition, shared
  actions, unique watcher ownership, and frame/navigation logging.
- `src/tvt/a7/profedit/fulog.clj` — network timeouts, logging, and non-EDT use.
- `src/tvt/a7/profedit/log.clj` — platform log paths, handler initialization,
  severity APIs, event sanitization, and uncaught-exception capture.
- `src/tvt/a7/profedit/nullableinp.clj` — pending formatted-editor commit hook.
- `src/tvt/a7/profedit/profile.clj` — pure profile updates, validation logging,
  zero-distance Spec, and actual-vector index invariant.
- `src/tvt/a7/profedit/repl.clj` — production persistence delegation and lint
  cleanup.
- `src/tvt/a7/profedit/rosetta.clj` — null-safe JSON conversion, clear parse
  failures, and serialization diagnostics.
- `src/tvt/a7/profedit/spreadsheet.clj` — reflection-free, minimal Apache POI
  workbook adapter replacing Docjure.
- `src/tvt/a7/profedit/update.clj` — timeouts, response cleanup, checksum
  verification, updater logging, and failure isolation.
- `src/tvt/a7/profedit/widgets.clj` — EDT notification, binding ownership,
  dirty snapshot use, icon/chooser behavior, pending input commits, status
  diagnostics, and typed workbook adapter use.
- `src/tvt/a7/profedit/wizard.clj` — per-run state, nested BC defaults,
  synchronous locale-aware input commits, navigation/validation logging, and
  explicit fully-qualified localization keys for coverage-safe loading.
- `src/tvt/a7/profedit/wizard_state.clj` — pure wizard state normalization and
  pending-field commit helpers.

Tests:

- `test/tvt/a7/profedit/core_test.clj` — binary/JSON/checksum/spec/distance and
  firmware-tree contracts.
- `test/tvt/a7/profedit/core_coverage_test.clj` — branch-focused deterministic
  core, persistence, config, device, logging, and updater coverage.
- `test/tvt/a7/profedit/input_commit_test.clj` — formatted input commit and
  locale parsing regressions.
- `test/tvt/a7/profedit/leak_canary.clj` — display-dependent watch/binding
  lifetime scenarios and lint cleanup.
- `test/tvt/a7/profedit/log_test.clj` — platform paths, rotation setup,
  sanitization, severity, initialization, and failure tolerance.
- `test/tvt/a7/profedit/profile_schema.clj` — independent test-only Malli
  profile and payload contracts.
- `test/tvt/a7/profedit/regression_test.clj` — package, persistence, updater,
  schema, and Windows wizard bug regressions plus lint cleanup.
- `test/tvt/a7/profedit/schema_test.clj` — boundary/mutation validation,
  deterministic generation, Spec agreement, and persistence round trips.
- `test/tvt/a7/profedit/seesaw_compat_test.clj` — row overload, chooser getter,
  form, paint-super replacement, and typed tree-event compatibility tests.
- `test/tvt/a7/profedit/spreadsheet_test.clj` — OOXML persistence and all typed
  cell-conversion branches for the local Apache POI adapter.
- `test/tvt/a7/profedit/wizard_test.clj` — BC row normalization, fallback,
  idempotence, and wizard state behavior.

### Verification notes

- Automated verification targets the Java 8 production baseline. JDK 26 and
  newer cannot load upstream Seesaw 1.5 because `java.applet.Applet` was
  removed; this is an upstream compatibility boundary, not a release change.
- The graphical leak canary still requires a live display and is intentionally
  separate from headless CI. Windows 10/11 and the Windows XP bundled-runtime
  smoke matrix remains a release-candidate/manual gate.
