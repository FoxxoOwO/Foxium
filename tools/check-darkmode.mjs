import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import vm from "node:vm";

const base = new URL("../app/src/main/assets/extensions/sys_darkmode/", import.meta.url);
const stored = {};
let runtimeListener;
let nativePort;

const browser = {
  runtime: {
    getManifest: () => ({ version: "1.0.1" }),
    connectNative: () => {
      nativePort = {
        postMessage: () => {},
        onMessage: { addListener: () => {} },
        onDisconnect: { addListener: () => {} },
      };
      return nativePort;
    },
    onMessage: { addListener: (listener) => (runtimeListener = listener) },
  },
  storage: {
    local: {
      get: async () => stored,
      set: async (values) => Object.assign(stored, values),
    },
  },
  tabs: {
    query: async () => [],
    sendMessage: async () => {},
  },
};

const sandbox = { browser, URL, setTimeout, clearTimeout, console, module: { exports: {} } };
vm.createContext(sandbox);
vm.runInContext(readFileSync(new URL("background.js", base), "utf8"), sandbox, { filename: "background.js" });
await new Promise((resolve) => setTimeout(resolve, 20));
assert.ok(runtimeListener, "runtime.onMessage was not registered");

const response = await runtimeListener(
  { type: "dark/config", url: "https://example.com/page" },
  { tab: { id: 1, url: "https://example.com/page" } },
);
assert.equal(response.enabled, false);
assert.equal(response.mode, "off");
assert.ok(response.rules && typeof response.rules === "object",
  "config response must carry rules for late content scripts");

const withRules = await runtimeListener(
  { type: "dark/config", url: "https://example.com/page" },
  { tab: { id: 1, url: "https://example.com/page" } },
);
assert.ok(withRules.rules && typeof withRules.rules === "object");

// Execute the content script: a stored per-site rule must not be re-enabled by a later
// dark/apply broadcast while the global switch is off. Testing source text is not enough.
const contentMessages = [];
let contentListener;
const attributes = new Map();
const styleElement = { remove() {}, setAttribute() {}, textContent: "" };
const rootElement = {
  style: { setProperty() {}, removeProperty() {} },
  setAttribute: (name, value) => attributes.set(name, value),
  getAttribute: (name) => attributes.get(name) ?? null,
  removeAttribute: (name) => attributes.delete(name),
  appendChild() {},
};
const contentSandbox = {
  URL,
  Map,
  Set,
  WeakMap,
  RegExp,
  Number,
  Math,
  JSON,
  console,
  setTimeout,
  clearTimeout,
  location: { href: "https://example.com/page" },
  document: {
    readyState: "complete",
    documentElement: rootElement,
    body: { tagName: "BODY" },
    getElementById: () => null,
    createElement: () => styleElement,
    querySelectorAll: () => [],
    addEventListener() {},
  },
  window: { addEventListener() {} },
  getComputedStyle: () => ({ backgroundColor: "rgb(255, 255, 255)" }),
  MutationObserver: class {
    observe() {}
    disconnect() {}
  },
  browser: {
    runtime: {
      getManifest: () => ({ version: "1.0.1" }),
      sendMessage: (message) => {
        contentMessages.push(message);
        return Promise.resolve({
          enabled: false,
          engine: "invert",
          mode: "off",
          rules: { "example.com": "invert" },
        });
      },
      onMessage: { addListener: (listener) => (contentListener = listener) },
    },
  },
};
vm.createContext(contentSandbox);
vm.runInContext(
  readFileSync(new URL("content.js", base), "utf8"),
  contentSandbox,
  { filename: "content.js" },
);
assert.ok(contentListener, "content did not register runtime.onMessage");
await new Promise((resolve) => setTimeout(resolve, 10));
// First apply a light-page invert, then turn the global switch off while a per-site rule exists.
await Promise.resolve(
  contentListener({
    type: "dark/apply",
    config: { enabled: true, engine: "invert", rules: {} },
  }),
);
assert.equal(rootElement.getAttribute("data-chrogecko-dark"), "invert");
await Promise.resolve(
  contentListener({
    type: "dark/apply",
    config: {
      enabled: false,
      engine: "invert",
      rules: { "example.com": "invert" },
    },
  }),
);
assert.equal(
  rootElement.getAttribute("data-chrogecko-dark"),
  null,
  "global-off must not apply a stored per-site rule",
);
assert.ok(
  contentMessages.some((message) => message.type === "dark/status" && message.mode === "off"),
  "content must report mode off while global dark mode is disabled",
);

console.log("darkmode checks passed");
