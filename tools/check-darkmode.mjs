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
console.log("darkmode checks passed");
