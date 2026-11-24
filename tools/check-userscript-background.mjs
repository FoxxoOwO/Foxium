import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import vm from "node:vm";

const base = new URL("../app/src/main/assets/extensions/sys_userscripts/", import.meta.url);
const nativeMessages = [];
const tabMessages = [];
const storage = {};
let runtimeListener;
let portListener;
let tabUpdatedListener;
let tabRemovedListener;

const port = {
  postMessage: (message) => nativeMessages.push(message),
  onMessage: { addListener: (listener) => (portListener = listener) },
  onDisconnect: { addListener: () => {} },
};

const browser = {
  runtime: {
    connectNative: () => port,
    getManifest: () => ({ version: "1.0.0" }),
    onMessage: { addListener: (listener) => (runtimeListener = listener) },
  },
  storage: {
    local: {
      get: async (keys) => {
        const wanted = Array.isArray(keys) ? keys : [keys];
        const result = {};
        for (const key of wanted) if (key in storage) result[key] = storage[key];
        return result;
      },
      set: async (values) => Object.assign(storage, values),
    },
  },
  tabs: {
    sendMessage: async (tabId, message, options) => {
      tabMessages.push({ tabId, message, options });
    },
    onUpdated: {
      addListener: (listener) => {
        tabUpdatedListener = listener;
      },
    },
    onRemoved: {
      addListener: (listener) => {
        tabRemovedListener = listener;
      },
    },
  },
};

const fetchStub = async (url, options) => {
  if (String(url).includes("logo.svg")) {
    return {
      ok: true,
      headers: new Headers({ "content-type": "image/svg+xml" }),
      arrayBuffer: async () => new TextEncoder().encode("<svg/>").buffer,
    };
  }
  if (String(url).includes("get-with-data")) {
    // A GET request with `data` must still be issued: fetch() rejects a body on GET/HEAD.
    return {
      ok: true,
      status: 200,
      statusText: "OK",
      url: String(url),
      headers: new Headers(),
      arrayBuffer: async () => new ArrayBuffer(0),
    };
  }
  return fetch(url, options);
};

const sandbox = {
  browser,
  URL,
  RegExp,
  fetch: fetchStub,
  btoa,
  TextDecoder,
  AbortController,
  setTimeout,
  clearTimeout,
  console,
  module: { exports: {} },
};
vm.createContext(sandbox);
for (const file of ["matching.js", "background.js"]) {
  vm.runInContext(readFileSync(new URL(file, base), "utf8"), sandbox, { filename: file });
}
await new Promise((resolve) => setTimeout(resolve, 20));
assert.ok(runtimeListener, "runtime.onMessage was not registered");
assert.ok(portListener, "native port was not opened");
assert.ok(
  nativeMessages.some((message) => message.type === "userscripts/ready"),
  "userscripts/ready was not sent",
);

portListener({
  type: "userscripts/sync",
  scripts: [
    {
      id: "1",
      name: "Example enhancer",
      namespace: "test",
      version: "1.0",
      match: ["https://example.com/*"],
      exclude: ["https://example.com/private/*"],
      runAt: "document-idle",
      source: "console.log('example');",
      meta: "// ==UserScript==\n// @name Example Enhancer\n// ==/UserScript==",
      resources: { logo: "https://example.com/logo.svg" },
      enabled: true,
    },
    {
      id: "2",
      name: "Disabled",
      match: ["<all_urls>"],
      source: "console.log('disabled');",
      enabled: false,
    },
  ],
});

const plan = await runtimeListener({ type: "userscripts/plan", url: "https://example.com/page" });
assert.equal(plan.scripts.length, 1);
assert.equal(plan.scripts[0].name, "Example enhancer");
assert.equal(plan.scripts[0].runAt, "document-idle");
assert.match(plan.scripts[0].source, /example/);
// GM_info must expose match/include/exclude and the original metadata block.
assert.deepEqual(Array.from(plan.scripts[0].match), ["https://example.com/*"]);
assert.deepEqual(Array.from(plan.scripts[0].include), []);
assert.deepEqual(Array.from(plan.scripts[0].exclude), ["https://example.com/private/*"]);
assert.match(plan.scripts[0].meta, /==UserScript==/);
assert.equal(plan.scripts[0].resources.logo.text, "<svg/>");
assert.match(plan.scripts[0].resources.logo.dataUrl, /^data:image\/svg\+xml;base64,/);

const excluded = await runtimeListener({
  type: "userscripts/plan",
  url: "https://example.com/private/page",
});
assert.equal(excluded.scripts.length, 0);

await runtimeListener({ type: "gm/set", scriptId: "1", key: "count", value: 7 });
const withValues = await runtimeListener({
  type: "userscripts/plan",
  url: "https://example.com/page",
});
assert.equal(withValues.scripts[0].values.count, 7);

await runtimeListener(
  { type: "userscripts/menu", url: "https://example.com/page", commands: [{ id: "1:1", title: "Run" }] },
  { tab: { id: 7, url: "https://example.com/page" } },
);
const menuMessage = nativeMessages.find((message) => message.type === "userscripts/menu");
assert.equal(menuMessage.tabId, 7);
assert.equal(menuMessage.commands[0].title, "Run");

portListener({ type: "userscripts/menu-command", tabId: 7, commandId: "1:1" });
assert.equal(tabMessages.at(-1).message.type, "userscripts/menu-command");
assert.equal(tabMessages.at(-1).message.commandId, "1:1");
assert.equal(tabMessages.at(-1).options.frameId, 0);

// A duplicate registration in an iframe stays one menu entry, but a click reaches only the
// frames that actually registered it (and no unrelated frames).
await runtimeListener(
  { type: "userscripts/menu", url: "https://example.com/page", commands: [{ id: "1:1", title: "Run" }] },
  { tab: { id: 7, url: "https://example.com/page" }, frameId: 3 },
);
const mergedMenu = nativeMessages.filter((m) => m.type === "userscripts/menu").at(-1);
assert.equal(mergedMenu.commands.length, 1);
const beforeClick = tabMessages.length;
portListener({ type: "userscripts/menu-command", tabId: 7, commandId: "1:1" });
assert.equal(tabMessages.length - beforeClick, 2);
assert.deepEqual(
  tabMessages.slice(beforeClick).map((m) => m.options.frameId).sort((a, b) => a - b),
  [0, 3],
);

assert.ok(tabUpdatedListener, "tabs.onUpdated was not registered");
tabUpdatedListener(7, { status: "loading" });
for (const frameId of [0, 3]) {
  await runtimeListener(
    { type: "userscripts/menu", url: "https://example.com/other", commands: [] },
    { tab: { id: 7, url: "https://example.com/other" }, frameId },
  );
}
const clearedMenu = nativeMessages.filter((message) => message.type === "userscripts/menu").at(-1);
assert.equal(clearedMenu.commands.length, 0);
assert.ok(tabRemovedListener, "tabs.onRemoved was not registered");

const blockedXhr = await runtimeListener({
  type: "gm/xhr",
  details: { url: "http://127.0.0.1:8080/secret" },
});
assert.equal(blockedXhr.ok, false);

const badSchemeXhr = await runtimeListener({
  type: "gm/xhr",
  details: { url: "file:///etc/passwd" },
});
assert.equal(badSchemeXhr.ok, false);

const getWithData = await runtimeListener({
  type: "gm/xhr",
  details: { url: "https://example.com/get-with-data", method: "GET", data: "ignored" },
});
assert.equal(getWithData.ok, true, "a GET with data must not fail with a body error");

assert.ok(
  nativeMessages.some((message) => message.type === "userscripts/status" && message.count === 2),
  "userscripts/status was not reported",
);

console.log("userscript background checks passed");
