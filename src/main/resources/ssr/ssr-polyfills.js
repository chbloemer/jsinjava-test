// Minimal globals that Vue's SSR renderer may touch but GraalJS does not provide.
globalThis.process = globalThis.process || { env: { NODE_ENV: 'production' } };
globalThis.setTimeout = globalThis.setTimeout || ((fn) => { fn(); return 0; });
globalThis.clearTimeout = globalThis.clearTimeout || (() => {});
globalThis.setInterval = globalThis.setInterval || (() => 0);
globalThis.clearInterval = globalThis.clearInterval || (() => {});
globalThis.queueMicrotask = globalThis.queueMicrotask || ((fn) => Promise.resolve().then(fn));
