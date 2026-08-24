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
 │   • assets/app.js          (client entry, hydrates the DOM)  │
 └──────────────────────────────────────────────────────────────┘

                        RUNTIME (JVM only)
 ┌──────────────┐   state JSON    ┌─────────────────────────────┐
 │ Spring Boot  │ ──────────────▶ │ GraalJS (org.graalvm.       │
 │ PageController│                │ polyglot) runs the SSR      │
 │              │ ◀────────────── │ bundle: SSR.render(state)   │
 └──────┬───────┘   HTML string   └─────────────────────────────┘
        │
        ▼
   HTML page  =  SSR markup  +  window.__INITIAL_STATE__  +  <script src="/assets/app.js">
        │
        ▼
   Browser hydrates with the same Vue app → interactive,
   talks to Spring via REST afterwards (GET /api/message)
```

Key points:

- **No REST call is needed for server-side rendering.** Java passes the initial
  state *directly* into the GraalJS context as a JSON string argument to
  `SSR.render(stateJson)`. The same JSON is inlined into the page as
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
  VueSsrRenderer.java     GraalJS context: loads polyfills + SSR bundle, renders
  PageController.java     GET /  → SSR HTML with inlined initial state
  ApiController.java      GET /api/message → JSON (post-hydration REST demo)
src/main/resources/
  templates/page.html     HTML shell with <!--ssr-outlet--> / <!--state-outlet-->
  ssr/ssr-polyfills.js    setTimeout/process shims GraalJS doesn't provide
src/main/frontend/
  src/App.vue             the demo component (counter + fetch demo)
  src/entry-server.js     SSR entry: createSSRApp + renderToString
  src/entry-client.js     client entry: createSSRApp + mount (hydrate)
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
- **Threading**: a polyglot `Context` is single-threaded. This experiment uses
  one context with a `synchronized` render method; production would use a
  context pool over a shared `Engine`.

## Status

Experiment / proof of concept — not production code.
