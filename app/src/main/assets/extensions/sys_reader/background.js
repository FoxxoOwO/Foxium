"use strict";

/*
 * Relay between the reader content script and Kotlin. The content script knows nothing about tab
 * ids, so readiness and state reports are tagged with the sender tab here.
 */

const NATIVE_APP = "chrogecko";

let nativePort = null;
let reconnectTimer = null;
let reconnectDelay = 1000;

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
  sendNative({ type: "reader/ready", version: browser.runtime.getManifest().version });
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

function tabIdOf(sender) {
  return sender && sender.tab && typeof sender.tab.id === "number" ? sender.tab.id : -1;
}

function onNativeMessage(message) {
  if (!message || typeof message !== "object") return;
  if (message.type === "reader/toggle" || message.type === "reader/activate") {
    relayToTab(message);
  }
}

function relayToTab(message) {
  const outgoing = { type: message.type === "reader/activate" ? "reader/activate" : "reader/toggle" };
  const tabId = typeof message.tabId === "number" ? message.tabId : -1;
  if (tabId >= 0) {
    browser.tabs.sendMessage(tabId, outgoing).catch(() => undefined);
    return;
  }
  // Kotlin does not track Gecko tab ids, so an untargeted request goes to the visible tab.
  browser.tabs.query({ active: true, currentWindow: true }).then((tabs) => {
    const active = tabs && tabs[0];
    if (active && typeof active.id === "number") {
      browser.tabs.sendMessage(active.id, outgoing).catch(() => undefined);
    }
  }).catch(() => undefined);
}

browser.runtime.onMessage.addListener((message, sender) => {
  if (!message || typeof message !== "object") return;
  const tabId = tabIdOf(sender);
  if (tabId < 0) return;

  if (message.type === "reader/ready" || message.type === "reader/state") {
    sendNative({
      type: "reader/state",
      tabId: tabId,
      url: message.url || (sender.tab && sender.tab.url) || "",
      host: message.host || "",
      available: message.available === true,
      active: message.active === true,
    });
  }
});

connectNative();
