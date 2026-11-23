"use strict";

/*
 * Keeps the dark-mode policy for every host and answers content-script queries at
 * document_start. Kotlin owns the database; this extension only mirrors the policy that the
 * native layer pushes over the "chrogecko" native messaging port.
 */

const NATIVE_APP = "chrogecko";
const STORAGE_KEY = "chrogecko.dark.config";

let nativePort = null;
let reconnectTimer = null;
let reconnectDelay = 1000;
let persistTimer = null;
// Incremented whenever the native layer pushes config. A pending storage read must never
// overwrite a newer config that arrived while it was in flight.
let configSequence = 0;

let config = {
  enabled: false,
  engine: "invert",
  rules: {},
};

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
  sendNative({ type: "dark/ready", version: browser.runtime.getManifest().version });
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
  if (message.type === "dark/config") {
    configSequence += 1;
    applyConfig(message);
  } else if (message.type === "dark/request-status") {
    sendNative({ type: "dark/status", enabled: config.enabled, engine: config.engine });
  }
}

function normalizeMode(value) {
  return value === "off" || value === "invert" || value === "dynamic" ? value : null;
}

function applyConfig(message) {
  const enabled = Boolean(message.enabled);
  const engine = normalizeMode(message.engine) || "invert";
  const rules = {};
  if (Array.isArray(message.rules)) {
    for (const rule of message.rules) {
      if (!rule || !rule.domain) continue;
      const mode = normalizeMode(rule.mode);
      if (mode) rules[String(rule.domain).toLowerCase()] = mode;
    }
  } else if (message.rules && typeof message.rules === "object") {
    for (const [domain, rawMode] of Object.entries(message.rules)) {
      const mode = normalizeMode(rawMode);
      if (mode) rules[String(domain).toLowerCase()] = mode;
    }
  }
  config = { enabled, engine, rules };
  schedulePersist();
  broadcast();
}

function schedulePersist() {
  if (persistTimer) clearTimeout(persistTimer);
  const sequence = configSequence;
  persistTimer = setTimeout(() => {
    persistTimer = null;
    if (sequence !== configSequence) return; // a newer native config superseded this write
    browser.storage.local.set({ [STORAGE_KEY]: config }).catch(() => {});
  }, 300);
}

function hostOf(url) {
  try {
    return new URL(url).hostname.toLowerCase();
  } catch (error) {
    return "";
  }
}

function ruleFor(host) {
  if (!host) return null;
  if (Object.prototype.hasOwnProperty.call(config.rules, host)) return config.rules[host];
  const labels = host.split(".");
  for (let index = 1; index < labels.length - 1; index += 1) {
    const parent = labels.slice(index).join(".");
    if (Object.prototype.hasOwnProperty.call(config.rules, parent)) return config.rules[parent];
  }
  return null;
}

function modeFor(url) {
  if (!config.enabled) return "off";
  const host = hostOf(url);
  const rule = ruleFor(host);
  if (rule) return rule;
  return config.engine;
}

function broadcast() {
  browser.tabs
    .query({})
    .then((tabs) => {
      for (const tab of tabs) {
        if (typeof tab.id !== "number") continue;
        browser.tabs
          .sendMessage(tab.id, { type: "dark/apply", config })
          .catch(() => {});
      }
    })
    .catch(() => {});
}

browser.runtime.onMessage.addListener((message, sender) => {
  if (!message || typeof message !== "object") return undefined;
  if (message.type === "dark/config") {
    const url = message.url || (sender.tab && sender.tab.url) || "";
    return ready.then(() => ({
      mode: modeFor(url),
      host: hostOf(url),
      enabled: config.enabled,
      engine: config.engine,
      rules: config.rules,
    }));
  }
  if (message.type === "dark/status") {
    const tabId = sender.tab ? sender.tab.id : -1;
    if (tabId >= 0) {
      sendNative({
        type: "dark/status",
        tabId,
        url: message.url || (sender.tab && sender.tab.url) || "",
        host: hostOf(message.url || (sender.tab && sender.tab.url) || ""),
        mode: message.mode || "off",
        pageWasDark: Boolean(message.pageWasDark),
      });
    }
    return Promise.resolve({ ok: true });
  }
  return undefined;
});

const ready = browser.storage.local
  .get(STORAGE_KEY)
  .then((stored) => {
    const saved = stored && stored[STORAGE_KEY];
    if (configSequence > 0) return; // native config already arrived; it is authoritative
    if (saved && typeof saved === "object") {
      config = {
        enabled: Boolean(saved.enabled),
        engine: normalizeMode(saved.engine) || "invert",
        rules: saved.rules && typeof saved.rules === "object" ? saved.rules : {},
      };
    }
  })
  .catch(() => {});

ready.then(() => connectNative());
