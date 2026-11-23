"use strict";

/*
 * Mirrors the snippet table pushed by Kotlin and answers content-script queries, including old
 * frames that missed a broadcast.
 */

const NATIVE_APP = "chrogecko";
const STORAGE_KEY = "chrogecko.expander.config";

let nativePort = null;
let reconnectTimer = null;
let reconnectDelay = 1000;
let config = { enabled: true, snippets: [] };

function normalize(value) {
  const source = value && typeof value === "object" ? value : {};
  const snippets = Array.isArray(source.snippets) ? source.snippets : [];
  return {
    enabled: source.enabled !== false,
    snippets: snippets
      .filter((item) => item && typeof item.trigger === "string" && typeof item.expansion === "string")
      .map((item) => ({ trigger: item.trigger, expansion: item.expansion })),
  };
}

function connectNative() {
  if (nativePort) return;
  try {
    nativePort = browser.runtime.connectNative(NATIVE_APP);
  } catch (error) {
    scheduleReconnect();
    return;
  }
  nativePort.onMessage.addListener(onNativeMessage);
  nativePort.onDisconnect.addListener(() => {
    nativePort = null;
    scheduleReconnect();
  });
  reconnectDelay = 1000;
  sendNative({ type: "expander/ready", version: browser.runtime.getManifest().version });
}

function scheduleReconnect() {
  if (reconnectTimer) return;
  reconnectTimer = setTimeout(() => {
    reconnectTimer = null;
    connectNative();
  }, reconnectDelay);
  reconnectDelay = Math.min(reconnectDelay * 2, 30000);
}

function sendNative(message) {
  if (!nativePort) return;
  try {
    nativePort.postMessage(message);
  } catch (error) {
    nativePort = null;
    scheduleReconnect();
  }
}

function onNativeMessage(message) {
  if (!message || typeof message !== "object") return;
  if (message.type === "expander/config") {
    applyConfig({ enabled: message.enabled, snippets: message.snippets });
  }
}

async function applyConfig(next) {
  config = normalize(next);
  await browser.storage.local.set({ [STORAGE_KEY]: config });
  broadcast();
  sendNative({
    type: "expander/status",
    enabled: config.enabled,
    count: config.snippets.length,
  });
}

function broadcast() {
  browser.tabs.query({}).then((tabs) => {
    for (const tab of tabs) {
      if (tab.id === undefined || tab.id < 0) continue;
      browser.tabs.sendMessage(tab.id, { type: "expander/apply", config: config })
        .catch(() => undefined);
    }
  }).catch(() => undefined);
}

browser.runtime.onMessage.addListener((message) => {
  if (!message || typeof message !== "object") return;
  if (message.type === "expander/config") {
    return ready.then(() => ({ enabled: config.enabled, snippets: config.snippets }));
  }
});

async function restore() {
  try {
    const stored = await browser.storage.local.get(STORAGE_KEY);
    if (stored && stored[STORAGE_KEY]) config = normalize(stored[STORAGE_KEY]);
  } catch (error) {
    // Storage is best effort: Kotlin pushes the authoritative table right after connecting.
  }
  connectNative();
}

const ready = restore();
