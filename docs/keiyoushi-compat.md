# dareader — Linux desktop Tachiyomi-style reader (Kotlin + Compose Multiplatform)

Status: loader shell slice working (2026-09-15). `--ui [apkUrl]` renders browse →
detail → reader against live Keiyoushi extensions (exercised: Comic Fury 1.4,
MangaDex 1.6). Open work lives on the board (`BOARD.md`); `UNFINISHED.md` is the
2026-09-15 audit history. This doc describes the compat approach, not a
completion claim.

Keiyoushi artifacts are Android APKs: `classes.dex` (Dalvik bytecode, not JVM
Desktop JVM cannot load dex, and extensions link `android.*` +
`tachiyomix` stubs. Proven fix (Suwayomi-Server, MPL-2.0):

1. Parse manifest without installing (`ApkFile`/`apk-parser` +
   `AndroidManifestParser`), check `uses-feature tachiyomi.extension` +
   `tachiyomi.extension.class`, `tachiyomix.name/extensionLib/contentWarning`.
2. `dex2jar` (`de.femtopedia.dex2jar:dex-translator` — `MultiDexFileReader` →
   `Dex2jar.from(...).to(jar)`), then two ASM repair passes
   (`load/BytecodeRepair.fixR8ConstructorSites`, `load/MangledRefRepair`).
3. Load with child-first `URLClassLoader` per jar, guarded by per-jar `Mutex`
   (`PackageTools.blockJarUsageWhile`), instantiate `Source | SourceFactory`.
4. Stub `android.*` via `AndroidCompat/` module (JVM-backed `Context`,
   `Bundle`, `Uri`, `Application`, prefs, network).

Upstream to vendor (do not reimplement):
- `Suwayomi-Server/AndroidCompat/` (whole module)
- `server/.../manga/impl/extension/Extension.kt` (install/load/unload, ~800 lines)
- `server/.../manga/impl/extension/ExtensionStoreService.kt` (index.pb/json fetch)
- `server/.../manga/impl/extension/github/` (NetworkExtensionStore models)
- `server/.../manga/impl/util/PackageTools.kt` (dex2jar, `LIB_VERSION_MIN=1.3`,
  `LIB_VERSION_MAX=1.6`, jar loader map)
- `server/.../manga/impl/util/AndroidManifestParser.kt`,
  `ResourceArscIconParser.kt`
- `server/.../manga/impl/util/source/GetSource.kt` (source registry)

## Module layout

```
dareader/
  settings.gradle.kts          # include :app, :extension-runtime
  gradle/libs.versions.toml    # version catalog (Kotlin 2.4.10, OkHttp 5.5.0, …)
  app/                         # Compose Multiplatform desktop shell
    src/desktopMain/kotlin/dareader/Main.kt   # CLI (store/apk/dex/load/popular/… + --ui)
    src/desktopMain/kotlin/dareader/ui/       # AppState, Screens, Images
  extension-runtime/           # vendored loader (Suwayomi port, JVM-only)
    src/main/kotlin/dareader/ext/  # ExtensionContract, pkg, load, store, trust, di
    src/main/kotlin/eu/kanade/     # vendored source/network/models
    src/main/kotlin/suwayomi/      # vendored server/config/source-registry bits
    src/main/java/android{,x}/     # AndroidCompat stubs (Context, prefs, Uri, …)
  docs/keiyoushi-compat.md     # this file (compat approach + pins)
```

## Version pins (must match extensions-lib 1.6 table)

- Kotlin 2.4.10, coroutines 1.11.0, serialization 1.11.0 (+protobuf, +json-okio)
- OkHttp 5.5.0 (+brotli, +zstd), jsoup 1.23.2
- dex2jar fork `de.femtopedia.dex2jar:dex-translator`/`dex-tools` 2.4.38,
  apk-parser `net.dongliu:apk-parser` 2.6.10
- JRE 21+ to run (build needs JDK 21). On this workstation the pinned toolchain
  lives at `~/.local/share/dareader-toolchain/jdk`; run
  `JAVA_HOME=~/.local/share/dareader-toolchain/jdk ./gradlew <task>` (there is
  no system JDK on PATH).

## Store / index compat

Same as Keiyoushi: `index.pb` (protobuf `Index{...}`) or `index.json`,
`repo.json` for store list, per-extension `{apkUrl, jarUrl?, iconUrl,
versionCode/Name, extensionLib, sources[]{id, name, lang}}`.
Keep source `id` (MD5 `name/lang/versionId`, sign bit cleared) stable so
Mihon `.tachibk` backups and chapter URLs interoperate. Persist
`StubSource{id, lang, name}` for uninstalled extensions (migration path).

## Known gaps

- WebView-dependent sources (Cloudflare/JS-rendered): needs JCEF + X11
  (`libxrender libxcomposite libxdamage libxkbcommon libxtst`), `DISPLAY` or
  Xvfb under Wayland; else mark source degraded. FlareSolverr hook optional.
- libVersion drift: Keiyoushi moving 1.6 → 1.7 (`language` BCP-47, `memo`,
  `getMangaUpdate` flags). The loader accepts 1.3..1.7 during the transition
  (`ExtensionContract.LIB_VERSION_MIN`/`MAX`).
- Trust: verify store `signingKey` / APK cert SHA-256; per-jar ClassLoader
  isolation; no silent auto-update.
