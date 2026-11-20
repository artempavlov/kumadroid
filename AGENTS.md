# AGENTS.md

Operating notes for agents working in this repository.

## Project

KumaDroid is a native Android client for Uptime Kuma. It is intentionally a thin client over the
server's own Socket.IO and REST APIs. There is no application backend and no local database.

- `mobile/` — Android app (Kotlin, Compose, OkHttp).
- `.gitlab-ci.yml`, `.github/workflows/android.yml` — unit tests, lint, and debug APK build.
- `opencode.json` — local `mobile-mcp` server configuration.

## Commands

Run from the repository root with JDK 17 and Android SDK 35 installed:

```bash
cd mobile
./gradlew test lint assembleDebug
```

Install the debug build:

```bash
adb install -r mobile/app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n org.kumadroid.app/.MainActivity
```

Use `.tmp/` for generated files, captures, and research. It is ignored by Git. Do not write
absolute paths into project configuration.

## Protocol rules

- Uptime Kuma 2.5.5 is the reference implementation. Check the upstream `server/client.js`,
  `server/socket-handlers/`, and `server/model/` files before adding an event or field.
- Socket.IO acknowledgements are consumed in `KumaRepository`'s event handler. Add new events
  there, not in screens.
- Monitor edits must keep working on a deep copy and send the complete JSON object back.
  `MonitorCatalog.draft()` preserves unknown fields; do not replace it with a narrow DTO.
- Add new monitor types to `MonitorCatalog.types`, `label()`, capability helpers, defaults,
  edit-screen fields, and validation together.
- The server may return SQLite booleans as `1`/`0` or strings. Use the tolerant helpers in
  `KumaModels.kt` instead of `JSONObject.getBoolean()`.
- Push monitors need a 32-character `pushToken`; the server does not generate one for clients.
- A session token belongs to one server. `KumaRepository.configureServer` clears the stored token
  when the server address changes, so it is never replayed against a different host.

## UI conventions

- Compose screens receive `KumaRepository` and `Navigator`; navigation is a simple stack in
  `ui/Nav.kt`.
- Destructive actions use `ConfirmDialog`.
- Loading states use the shared components in `ui/Components.kt`.
- Keep UI text in English to match the rest of the app, unless a screen is explicitly localized.
- Update `README.md` and this file when adding a feature, command, or protocol constraint.

## Verification

The intended end-to-end path is:

1. Start a disposable Uptime Kuma instance and complete the setup wizard.
2. Start a redroid/Android emulator visible to `mobile-mcp`.
3. Install the debug APK and drive the real UI through all changed screens.
4. Verify server-side effects (monitor rows, heartbeats, status pages, maintenance windows) as
   well as the UI.
5. Delete test data, then run `./gradlew test lint assembleDebug`.

Local test instance used during development (example values; keep real connection details out of
the repository):

- Uptime Kuma: `http://<host>:<port>`
- Login: `admin` / `<test password>`
- Device: redroid exposed at `localhost:40050`

This instance is disposable and contains test data only. Do not put production credentials in
the repository.

## Known lint warnings

Gradle dependency warnings are expected: the pinned Compose/AndroidX versions are intentional and
should be upgraded as one tested change. `ObsoleteSdkInt` for
`mipmap-anydpi-v26` is an AAPT requirement for adaptive icons; moving those files to
`mipmap-anydpi` breaks resource linking. `AuthLeak` for connection-string placeholders and
`CustomX509TrustManager` for the opt-in self-signed mode are suppressed narrowly with comments.
Do not remove TLS verification or password fields to silence warnings.

## Repository and mirrors

GitLab is the primary repository. GitHub is a read-only push mirror; never commit or merge on
the GitHub side, because the next mirror update overwrites its branches.

## Git

The repository uses one commit per logical task. Write commit messages in the repository's
existing style once a history exists. Do not commit `.tmp/`, APKs, local SDK paths, or secrets.
