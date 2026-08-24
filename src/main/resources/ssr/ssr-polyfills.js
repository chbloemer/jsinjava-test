// Minimal globals that Vue's SSR renderer may touch but GraalJS does not provide.
globalThis.process = globalThis.process || { env: { NODE_ENV: 'production' } };
// Defers via microtask, not a real timer: the delay is ignored and the callback
// runs during the microtask drain. Keeps render() settling synchronously on the
// Java side, but code relying on macrotask ordering will not behave as in Node.
globalThis.setTimeout = globalThis.setTimeout || ((fn, _delay, ...args) => {
  queueMicrotask(() => fn(...args));
  return 0;
});
globalThis.clearTimeout = globalThis.clearTimeout || (() => {});
globalThis.setInterval = globalThis.setInterval || (() => 0);
globalThis.clearInterval = globalThis.clearInterval || (() => {});
globalThis.queueMicrotask = globalThis.queueMicrotask || ((fn) => Promise.resolve().then(fn));
