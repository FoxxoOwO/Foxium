"use strict";

/* ChroGecko Autofill: relays form-fill requests between the page and the app. */
const NATIVE_APP = "chrogecko";

let nativePort = null;
let reconnectTimer = null;
let reconnectDelay = 1000;

function onNativeMessage(message) {
  if (!message || message.type !== "autofill/fill") return;
  const tabId = message.tabId;
  if (typeof tabId !== "number") return;
  browser.tabs.sendMessage(tabId, message).catch(() => {});
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
  if (!nativePort) connectNative();
  if (!nativePort) return;
  try {
    nativePort.postMessage(message);
  } catch (error) {
    nativePort = null;
    scheduleReconnect();
  }
}

browser.runtime.onMessage.addListener((message, sender) => {
  if (!message || typeof message.type !== "string") return;
  sendNative({ ...message, tabId: sender.tab ? sender.tab.id : null });
});

connectNative();
