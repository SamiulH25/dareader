# dareader — project context

Kotlin desktop manga reader (Compose Multiplatform shell `:app` + JVM-only
vendored Keiyoushi extension loader `:extension-runtime`). Gradle 8.10.2, JDK 21,
Kotlin 2.4.10. Modules: `:app` (Compose desktop, `dareader.MainKt`), `:extension-runtime`
(dex2jar/apk-parser/OkHttp/Rhino loader consumed by `:app`).

Commands (no system JDK on this box — use the pinned local toolchain; the
wrapper lives in-repo, `./gradlew` needs `JAVA_HOME` on PATH-less hosts):
- `TC=~/.local/share/dareader-toolchain; JAVA_HOME=$TC/jdk ./gradlew :extension-runtime:test` — unit tests
- `JAVA_HOME=~/.local/share/dareader-toolchain/jdk ./gradlew :app:compileKotlinDesktop` — desktop compile check
- `JAVA_HOME=~/.local/share/dareader-toolchain/jdk ./gradlew :app:run --args="--help"` — CLI smoke

Docs: `docs/keiyoushi-compat.md` (compat approach + pins), `UNFINISHED.md`
(last audit; 2026-09-15 slices complete — history, not backlog).

## Board protocol (always follow)

Local board server auto-starts each session (global `kanban` extension,
`~/.omp/agent/extensions/kanban-server.mjs`, 127.0.0.1:37741,
`$OMP_KANBAN_PORT` overrides). Truth is its local JSON store
(`~/.local/share/omp-kanban/boards.json`); `BOARD.md` (injected below) is a
two-way mirror — hand-edits are imported on next read, tool moves are exported.
Human web UI: `http://127.0.0.1:37741/?project=<cwd>` (`/board` prints it).

1. Call `kanban_status`, then `kanban_list`, at session start; work top of
  `todo` first unless the user names tickets.
2. Claim before coding: `kanban_move` `todo` → `doing`.
3. Implement + verify per the ticket body; update callers, no stubs.
4. Close: `kanban_move` → `done` after verifying.
5. Never leave stale `doing`; never batch tickets without moving them.
@../BOARD.md

