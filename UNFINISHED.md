# dareader — unfinished items (audit 2026-09-15)

Status 2026-09-15: ALL ITEMS COMPLETE across 4 slices (RuntimeExt, ShimsNet, AppUi, CliDocs).
Verified: `:extension-runtime:test` 22/22 pass, `:app:compileKotlinDesktop` BUILD SUCCESSFUL
(Gradle 8.10.2, JDK 21). Two parent-side compile fixes were needed post-merge
(`parseRepos` reified serializers; `ByteArray.ifEmpty` → `takeIf`). Original findings below.

## RUNTIME (extension loader / store / trust)

- **RUNTIME-01 — GetSource is a stub registry** (`extension-runtime/.../suwayomi/.../source/GetSource.kt`).
  Comment admits it: "Grows into the real registry in the load-sources step; today it
  only satisfies NetworkHelper's reset hook." No source tracking, no find-by-id.
  ACCEPT: `register/unregister/findById/unregisterAll` backed by loaded extensions;
  `loadExtensionSources` registers, `LoadedExtension.close` unregisters.
- **RUNTIME-02 — factory metadata never parsed** (`extension-runtime/.../dareader/ext/pkg/ApkInfo.kt`).
  `ExtensionContract.METADATA_SOURCE_FACTORY` exists but `parseApkManifest` only reads
  `sourceClass`; multi-source factory extensions fail with "no source class".
  ACCEPT: parse factory meta, `judge()` accepts class-or-factory, loader entry uses it.
- **RUNTIME-03 — lib 1.7 rejected** (`extension-runtime/.../dareader/ext/ExtensionContract.kt`).
  `LIB_VERSION_MAX = 1.6` but `docs/keiyoushi-compat.md` requires accepting 1.6→1.7
  (`language` BCP-47, `memo`, `getMangaUpdate` flags) during transition.
  ACCEPT: accept 1.7, keep 1.3 floor.
- **RUNTIME-04 — TOFU trust not implemented** (`extension-runtime/.../dareader/ext/trust/ExtensionTrust.kt`).
  KDoc promises store-key OR user-pinned package+version+cert (TOFU); only
  `matchesStoreKey` exists, no storage, and no load path (`AppState`, `Main` CLI)
  gates on trust at all.
  ACCEPT: file-backed pinned-cert store + trust check wired into load paths.
- **RUNTIME-05 — repo.json + jarUrl ignored** (`extension-runtime/.../dareader/ext/store/StoreIndex.kt`).
  Doc promises `repo.json` store list and per-extension `jarUrl` direct-jar path;
  only index.pb/json + split list fetched, `jarUrl` never used (always APK→dex2jar).
  ACCEPT: `fetchRepos` + prefer `jarUrl` when present.
- **RUNTIME-06 — loader lifecycle leaks** (`ExtensionLoader.kt` + `app/.../ui/AppState.kt`).
  UI paths never call `LoadedExtension.close`; temp jar deleted while loader open
  (breaks lazy class loads); sources never registered (see RUNTIME-01).
  ACCEPT: `AppState` retains handle, closes on replace; jar lifetime covers loader.

## SHIMS (android stubs / network runtime)

- **SHIM-01 — Context surface too small** (`extension-runtime/src/main/java/android/content/Context.java`,
  `ContextWrapper.java`, `dareader/ext/android/StoreContext.java`, `app/Application.java`,
  `app/Activity.java`). Missing `getCacheDir/getFilesDir/getApplicationContext/getSystemService/
  getResources/getAssets`; `NetworkHelper`'s commented-out `cacheDir` depends on this.
  Extensions hitting these die with `NoSuchMethodError`. ACCEPT: add backed by XDG/app dirs.
- **SHIM-02 — Log levels missing** (`java/android/util/Log.java`). Only `e/wtf`;
  extensions call `d/i/v/w` + 2-arg `e`. ACCEPT: full level set routing to stderr/slf4j.
- **SHIM-03 — Uri too thin** (`java/android/net/Uri.java`). Only `parse/encode/toString`;
  needs `getQueryParameter/getHost/getPath/buildUpon` subset. ACCEPT: delegate to `java.net.URI`.
- **SHIM-04 — Preference state missing** (`java/androidx/preference/*.java`).
  No `getKey/getValue/setValue/persist*/getText/setText/isChecked/setChecked`;
  `ConfigurableSource` prefs exist but state can't round-trip. (UI binding: APP-03.)
  ACCEPT: key + value + persistence against source prefs store.
- **SHIM-05 — JS result cast wrong** (`.../eu/kanade/tachiyomi/network/JavaScriptEngine.kt`).
  Raw `evaluateString(...) as T` fails for Rhino `NativeObject`/`Double` vs expected
  String/Int/Boolean. ACCEPT: convert via `Context.jsToJava` + numeric coercion.
- **SHIM-06 — network cache is a temp-dir leak** (`.../eu/kanade/tachiyomi/network/NetworkHelper.kt`).
  `Files.createTempDirectory` per process: leaks dirs, never reuses HTTP cache.
  ACCEPT: persistent dir (`~/.cache/dareader/network`), env-gated verbose logging.

## APP (desktop UI)

- **APP-01 — reader NPEs on lazy image URLs** (`app/.../ui/Images.kt`, `ui/Screens.kt`).
  `HttpSource.getImage` does `GET(page.imageUrl!!)`; `PageView` never calls
  `getImageUrl` fallback when `imageUrl` is null (common for MangaDex-style sources).
  ACCEPT: resolve-then-fetch in `PageImages.page`.
- **APP-02 — browse is popular-page-1 only** (`ui/Screens.kt`, `ui/AppState.kt`).
  No search/filter UI, no latest-updates, no `hasNextPage` pagination.
  ACCEPT: search field + filter list + latest tab + next-page loading.
- **APP-03 — source prefs have no UI** (`ui/Screens.kt`). `setupPreferenceScreen` never
  invoked for `ConfigurableSource`. Depends on SHIM-04. ACCEPT: settings surface per source.
- **APP-04 — nav/resource bugs** (`ui/AppState.kt`, `ui/Screens.kt`). `close()` never called;
  Reader Back drops sources (`listOf(source)`); `first()` on possibly-empty manga/chapter
  lists crashes; jar deleted while loaded. ACCEPT: fix all four (with RUNTIME-06).
- **APP-05 — image pipeline has no backpressure** (`ui/Images.kt`, `ui/Screens.kt`).
  Unbounded per-image coroutines, 40-full-bitmap memory cache (OOM risk), no disk cache,
  no error/retry UI. ACCEPT: scoped loads, byte-capped/disk-backed cache, error states.

## CLI / DOCS

- **CLI-01 — arg handling crashes** (`app/.../dareader/Main.kt`). Bare `args[0]` with zero
  args → `IndexOutOfBounds`; no `--help`/usage; `popular` id `toLong()` unguarded.
  ACCEPT: usage text, empty-args path, validated parsing.
- **DOC-01 — compat doc stale** (`docs/keiyoushi-compat.md`). "(TODO)" at module layout;
  pins Kotlin 2.4.0 (actual 2.4.10); "workstation has Java 8 only" (21 present).
  ACCEPT: fill layout section, correct pins/notes.
- **DOC-02 — root build comment stale** (`build.gradle.kts`). "Compose UI lands here once
  proven" — `:app` Compose UI already exists. ACCEPT: describe actual modules.

## TESTS

- **TEST-01 — zero tests.** No `src/test` anywhere. ACCEPT (with runtime work):
  unit tests for pure logic — lib-version parsing, `judge()`, `matchesStoreKey`,
  `getUrlWithoutDomain` — following `Verify` rules (behavior, not plumbing).
