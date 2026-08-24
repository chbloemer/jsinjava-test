# jsinjava-test — Isomorphic Vue 3 on a pure Java stack

An experiment: can you run an **isomorphic (SSR + hydration) Vue 3 webapp entirely on the JVM**, with no Node.js at runtime?

**Answer: yes.** This repo is a minimal working proof.

## How it works

```
                        BUILD TIME (Node required)
 ┌──────────────────────────────────────────────────────────────┐
 │  Vite builds the same Vue app twice:                         │
 │   • server-bundle.iife.js  (Vue + vue/server-renderer,       │
 │     bundled as IIFE exposing global `SSR.render`)            │
 │   • assets/<page>.js       (one client entry per page)       │
 └──────────────────────────────────────────────────────────────┘

                        RUNTIME (JVM only)
 ┌──────────────┐  view + model  ┌──────────────┐   state JSON    ┌─────────────────────────────┐
 │ Spring Boot  │ ─────────────▶ │ VueSsrView-  │ ──────────────▶ │ GraalJS (org.graalvm.       │
 │ PageController│               │ Resolver /   │                 │ polyglot) runs the SSR      │
 │ (plain MVC)  │                │ VueSsrView   │ ◀────────────── │ bundle: SSR.render(page,st) │
 └──────────────┘                └──────┬───────┘   HTML string   └─────────────────────────────┘
                                        │
                                        ▼
   HTML page  =  SSR markup  +  window.__INITIAL_STATE__  +  <script src="/assets/<page>.js">
        │
        ▼
   Browser hydrates with the same Vue app → interactive,
   talks to Spring via REST afterwards (GET /api/message)
```

Key points:

- **No REST call is needed for server-side rendering.** Java passes the initial
  state *directly* into the GraalJS context as a JSON string argument to
  `SSR.render(pageName, stateJson)`. The same JSON is inlined into the page as
  `window.__INITIAL_STATE__` so the client hydrates against identical data.
- **No Node.js at runtime.** GraalJS (`org.graalvm.polyglot:js`) runs on a plain
  Temurin JDK — no GraalVM distribution required. Node/npm/Vite are build-time
  tools only; the Gradle build invokes them and packages the bundles into the jar.
- `renderToString` returns a Promise, but GraalJS drains the microtask queue
  before control returns to Java, so the result can be read synchronously via
  `promise.invokeMember("then", ...)`. See `VueSsrRenderer`.

## Stack

| Layer | Tech |
|---|---|
| Backend | Spring Boot 4.1.1, JDK 21 |
| JS engine in the JVM | GraalJS / GraalVM Polyglot 25.2.4 (works on plain Temurin) |
| Frontend | Vue 3.5, built with Vite 7 |
| Build | Gradle 9 + [node-gradle plugin](https://github.com/node-gradle/gradle-node-plugin) |

## Project layout

```
src/main/java/dev/example/jsinjava/
  Application.java        Spring Boot entry point
  VueSsrRenderer.java     GraalJS context pool over a shared Engine, renders
  VueSsrViewResolver.java Spring MVC ViewResolver: view name → registered page
  VueSsrView.java         View: "state" attribute → JSON → SSR markup + entry script
  PageTemplate.java       HTML shell split at its outlets, pure concatenation
  PageController.java     GET / and /about → plain MVC: view name + model only
  ApiController.java      GET /api/message → JSON (post-hydration REST demo)
src/main/resources/
  templates/shell.html    shared HTML shell with ssr-/state-/entry-outlet
  ssr/ssr-polyfills.js    setTimeout/process shims GraalJS doesn't provide
src/main/frontend/
  src/pages/*.vue         page components (HomePage, AboutPage)
  src/pages.js            page registry: view name → component
  src/entry-server.js     SSR entry: SSR.render(pageName, stateJson)
  src/entries/*.js        one client entry per page (hydrate that page)
  src/hydrate.js          shared client bootstrap
  vite.config.ssr.mjs     lib/IIFE build → build/frontend/ssr
  vite.config.client.mjs  browser build  → build/frontend/client
```

## Build & run

Requirements: JDK 21 and Node 20.19+ or 22.12+ (build time only, matching
Vite 7's supported Node releases).

```sh
./gradlew build          # builds frontend (npm install + 2 vite builds) + jar + tests
./gradlew bootRun        # or: java -jar build/libs/jsinjava-test-0.0.1-SNAPSHOT.jar
```

Then:

- open <http://localhost:8080> — the page arrives fully server-rendered
  (check `curl -s localhost:8080/`: real markup, not an empty `<div id="app">`)
- the counter button works after hydration, without a page reload
- "Load message from server" fetches `GET /api/message` from Spring
- <http://localhost:8080/about> is a second page: own Vue component, own
  client entry (`/assets/about.js`), same shared shell — served by a plain
  MVC controller returning `"about"`

The jar is self-contained — it runs on a machine without Node installed.

## Gotchas discovered

- **Spring Boot 4 ships Jackson 3**: `tools.jackson.*` packages, not
  `com.fasterxml.jackson.*`.
- **GraalJS is missing some globals** Vue touches: `setTimeout`, `process`.
  A small prelude (`ssr-polyfills.js`) is evaluated before the bundle.
  Vite's `define` option eliminates most `process.env` references at build time.
- **Bundle as IIFE, not ESM.** GraalJS has no module resolver; a self-contained
  IIFE assigning a global (`var SSR = ...`) is the most robust format.
  Deliberately *not* using Vite's `build.ssr` mode — that targets Node module
  formats.
- **First render is slow** (~100s of ms): Truffle runs in interpreter mode on a
  stock JDK. Subsequent renders are in the low-ms range.
- **Threading**: a polyglot `Context` is single-threaded — concurrent access
  throws `IllegalStateException`. `VueSsrRenderer` therefore keeps a fixed pool
  of contexts (default: number of CPU cores, configurable via
  `ssr.context-pool-size`); each render borrows one, so up to pool-size renders
  run in parallel with no global lock. All contexts share one `Engine`, which
  holds the parsed/JIT-compiled bundle code — additional contexts start warm
  and add little memory. A render that fails on the host side gets its context
  replaced with a fresh one instead of returning a possibly broken context to
  the pool; a failure inside the page's own JavaScript is treated as an input
  problem and the context stays pooled.

## Status

Experiment / proof of concept. The rendering path is production-shaped —
context pool over a shared `Engine`, parallel renders, poisoned-context
replacement, graceful shutdown that waits for in-flight renders — but the rest
(observability, health checks, timeout tuning) is deliberately minimal.
