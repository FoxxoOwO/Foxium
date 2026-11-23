"use strict";

/* Forwards page long-press targets to the app over the native port. */
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
