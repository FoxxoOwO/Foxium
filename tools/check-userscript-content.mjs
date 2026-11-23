import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import vm from "node:vm";

const sentMessages = [];
const planScripts = [
  {
    id: "s1",
    name: "Grant GetValue Only",
    runAt: "document-start",
    grants: ["GM_getValue"],
    values: { k: "v1" },
    source: [
      "globalThis.__script1 = {",
      "  hasGetValue: typeof GM_getValue === 'function',",
      "  value: GM_getValue('k', 'fallback'),",
      "  hasModernGet: typeof GM.getValue === 'function',",
      "  hasInfo: typeof GM_info === 'object' && GM_info !== null,",
      "  hasAddStyle: typeof GM_addStyle === 'function',",
      "  hasLog: typeof GM_log === 'function',",
      "  xhrType: typeof GM_xmlhttpRequest,",
      "  setType: typeof GM_setValue,",
      "  deleteType: typeof GM_deleteValue,",
      "  menuType: typeof GM_registerMenuCommand,",
      "  modernXhrType: typeof GM.xmlHttpRequest,",
      "  modernSetType: typeof GM.setValue",
      "};",
    ].join("\n"),
  },
  {
    id: "s2",
    name: "Grant Xhr And Modern Set",
    runAt: "document-start",
    grants: ["GM_xmlhttpRequest", "GM.setValue"],
    values: {},
    source: [
      "globalThis.__script2 = {",
      "  hasLegacyXhr: typeof GM_xmlhttpRequest === 'function',",
      "  hasModernXhr: typeof GM.xmlHttpRequest === 'function',",
      "  hasModernSet: typeof GM.setValue === 'function',",
      "  legacySetType: typeof GM_setValue",
      "};",
    ].join("\n"),
  },
];

const contextObj = {
  location: { href: "https://example.com/page" },
  document: {
    readyState: "loading",
    head: { appendChild: () => {} },
    documentElement: { appendChild: () => {} },
    createElement: () => ({ textContent: "", remove: () => {} }),
    addEventListener: () => {},
  },
  browser: {
    runtime: {
      sendMessage: (msg) => {
        sentMessages.push(msg);
        if (msg.type === "userscripts/plan") {
          return Promise.resolve({ scripts: planScripts });
        }
        return Promise.resolve({ ok: true });
      },
      onMessage: { addListener: () => {} },
    },
  },
  setTimeout: () => 1,
  clearTimeout: () => {},
  console: { log: () => {}, error: () => {} },
  atob: (value) => Buffer.from(value, "base64").toString("binary"),
};
contextObj.window = {
  top: null,
  addEventListener: () => {},
};
contextObj.window.top = contextObj.window;
contextObj.globalThis = contextObj;

const context = vm.createContext(contextObj);

const source = readFileSync(
  new URL("../app/src/main/assets/extensions/sys_userscripts/content.js", import.meta.url),
  "utf8",
);
vm.runInContext(source, context, { filename: "sys_userscripts/content.js" });

await new Promise((resolve) => setImmediate(resolve));
await new Promise((resolve) => setImmediate(resolve));

assert.ok(sentMessages.some((m) => m.type === "userscripts/plan"));

assert.deepEqual(Object.assign({}, context.__script1), {
  hasGetValue: true,
  value: "v1",
  hasModernGet: true,
  hasInfo: true,
  hasAddStyle: true,
  hasLog: true,
  xhrType: "undefined",
  setType: "undefined",
  deleteType: "undefined",
  menuType: "undefined",
  modernXhrType: "undefined",
  modernSetType: "undefined",
});

assert.deepEqual(Object.assign({}, context.__script2), {
  hasLegacyXhr: true,
  hasModernXhr: true,
  hasModernSet: true,
  legacySetType: "undefined",
});

console.log("userscript content checks passed");
