"use strict";

/*
 * Privileged background context for the media sniffer. It watches network requests for stream
 * manifests and progressive media, combines them with <video> discoveries from the content
 * script, and streams snapshots to the ChroGecko cast layer over native messaging.
 */

const NATIVE_APP = "chrogecko";
const MAX_ITEMS_PER_TAB = 40;
const MAX_HEADER_CACHE = 600;

let nativePort = null;
let reconnectTimer = null;
let reconnectDelay = 1000;

/** tabId -> { pageUrl, pageTitle, items: Map<url, item> } */
const tabs = new Map();
const flushTimers = new Map();
/** requestId -> headers captured in onBeforeSendHeaders. */
const headerCache = new Map();

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
  sendNative({ type: "sniffer/ready", version: browser.runtime.getManifest().version });
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
  switch (message.type) {
    case "sniffer/ping":
      sendNative({ type: "sniffer/ready", version: browser.runtime.getManifest().version });
      break;
    case "media/request":
      for (const tabId of tabs.keys()) scheduleFlush(tabId);
      break;
    case "media/clear":
      if (typeof message.tabId === "number") clearTab(message.tabId);
      break;
    default:
      break;
  }
}

function clearTab(tabId) {
  const timer = flushTimers.get(tabId);
  if (timer) clearTimeout(timer);
  flushTimers.delete(tabId);
  if (!tabs.delete(tabId)) return;
  sendNative({ type: "media/clear", tabId });
}

function scheduleFlush(tabId) {
  if (flushTimers.has(tabId)) return;
  flushTimers.set(
    tabId,
    setTimeout(() => {
      flushTimers.delete(tabId);
      const tab = tabs.get(tabId);
      if (!tab) return;
      const items = Array.from(tab.items.values())
        .sort((a, b) => b.detectedAt - a.detectedAt)
        .slice(0, MAX_ITEMS_PER_TAB);
      sendNative({
        type: "media/snapshot",
        tabId,
        pageUrl: tab.pageUrl,
        pageTitle: tab.pageTitle,
        items,
      });
    }, 350),
  );
}

function tabRecord(tabId, pageUrl, pageTitle) {
  let tab = tabs.get(tabId);
  if (!tab) {
    tab = { pageUrl: pageUrl || "", pageTitle: pageTitle || "", items: new Map() };
    tabs.set(tabId, tab);
  }
  if (pageUrl) tab.pageUrl = pageUrl;
  if (pageTitle) tab.pageTitle = pageTitle;
  return tab;
}

function recordMedia(tabId, pageUrl, pageTitle, candidate) {
  if (typeof tabId !== "number" || tabId < 0) return;
  if (!candidate || !isHttpUrl(candidate.url) || isSegmentUrl(candidate.url)) return;
  const tab = tabRecord(tabId, pageUrl, pageTitle);
  const existing = tab.items.get(candidate.url);
  const item = existing || {
    id: mediaId(tabId, candidate.url),
    url: candidate.url,
    detectedAt: Date.now(),
    title: "",
    poster: "",
    headers: {},
  };
  if (candidate.mime) item.mime = candidate.mime;
  if (candidate.kind) item.kind = candidate.kind;
  if (candidate.title) item.title = candidate.title;
  if (candidate.poster) item.poster = candidate.poster;
  if (candidate.duration != null) item.duration = candidate.duration;
  if (candidate.width != null) item.width = candidate.width;
  if (candidate.height != null) item.height = candidate.height;
  if (candidate.requiresAuth != null) item.requiresAuth = candidate.requiresAuth;
  if (candidate.headers) item.headers = Object.assign(item.headers || {}, candidate.headers);
  if (!item.kind) item.kind = kindOf(candidate.url, candidate.mime || "");
  tab.items.set(candidate.url, item);

  if (tab.items.size > MAX_ITEMS_PER_TAB) {
    const oldest = Array.from(tab.items.values()).sort((a, b) => a.detectedAt - b.detectedAt)[0];
    if (oldest) tab.items.delete(oldest.url);
  }
  scheduleFlush(tabId);
}

function updateTitle(tabId, title) {
  const tab = tabs.get(tabId);
  if (!tab) return;
  tab.pageTitle = title;
  scheduleFlush(tabId);
}

function isHttpUrl(url) {
  try {
    const parsed = new URL(url);
    return parsed.protocol === "http:" || parsed.protocol === "https:";
  } catch (error) {
    return false;
  }
}

const MEDIA_URL_PATTERN = /\.(mp4|m4v|webm|mkv|mov|ogv|m3u8|mpd|mp3|m4a)(?:$|[?#])/i;
const SEGMENT_URL_PATTERN = /\.(ts|m4s|aac|vtt|key|cmfv|cmfa)(?:$|[?#])/i;

function isSegmentUrl(url) {
  return SEGMENT_URL_PATTERN.test(url) || /\/(seg|segments?|chunk)[-_/]/i.test(url);
}

function isCandidateUrl(url) {
  return isHttpUrl(url) && MEDIA_URL_PATTERN.test(url) && !isSegmentUrl(url);
}

function guessMime(url) {
  const path = url.split(/[?#]/)[0].toLowerCase();
  if (path.endsWith(".m3u8")) return "application/vnd.apple.mpegurl";
  if (path.endsWith(".mpd")) return "application/dash+xml";
  if (path.endsWith(".mp4") || path.endsWith(".m4v")) return "video/mp4";
  if (path.endsWith(".webm")) return "video/webm";
  if (path.endsWith(".mkv")) return "video/x-matroska";
  if (path.endsWith(".mov")) return "video/quicktime";
  if (path.endsWith(".ogv")) return "video/ogg";
  if (path.endsWith(".mp3")) return "audio/mpeg";
  if (path.endsWith(".m4a")) return "audio/mp4";
  return "";
}

function kindOf(url, mime) {
  const path = url.split(/[?#]/)[0].toLowerCase();
  const lowerMime = (mime || "").toLowerCase();
  if (path.endsWith(".m3u8") || lowerMime.includes("mpegurl")) return "hls";
  if (path.endsWith(".mpd") || lowerMime.includes("dash+xml")) return "dash";
  return "file";
}

function headerMap(requestHeaders) {
  const result = {};
  if (!Array.isArray(requestHeaders)) return result;
  const ignored = new Set(["host", "content-length", "connection", "accept-encoding"]);
  for (const header of requestHeaders) {
    if (!header || !header.name) continue;
    const name = String(header.name).toLowerCase();
    if (ignored.has(name)) continue;
    result[name] = header.value == null ? "" : String(header.value);
  }
  return result;
}

function trimHeaderCache() {
  while (headerCache.size > MAX_HEADER_CACHE) {
    const oldest = headerCache.keys().next();
    if (oldest.done) break;
    headerCache.delete(oldest.value);
  }
}

function onBeforeSendHeaders(details) {
  // Cache headers only for media candidates: retaining Cookie/Authorization from every XHR on
  // every site would expose session credentials to the extension and to the cast layer.
  if (details.tabId < 0 || !isCandidateUrl(details.url)) return {};
  headerCache.set(details.requestId, headerMap(details.requestHeaders));
  trimHeaderCache();
  const headers = headerMap(details.requestHeaders);
  const mime = guessMime(details.url);
  recordMedia(details.tabId, details.documentUrl || details.originUrl || "", "", {
    url: details.url,
    mime,
    kind: kindOf(details.url, mime),
    headers,
    requiresAuth: Boolean(headers.authorization || headers.cookie),
  });
  return {};
}

function onHeadersReceived(details) {
  if (details.tabId < 0) return;
  const headers = details.responseHeaders || [];
  let mime = "";
  for (const header of headers) {
    if (header.name && header.name.toLowerCase() === "content-type") {
      mime = String(header.value || "").split(";")[0].trim().toLowerCase();
      break;
    }
  }
  if (isSegmentUrl(details.url) || mime === "video/iso.segment") return;
  if (!isStreamMime(mime) && !isCandidateUrl(details.url)) return;
  const requestHeaders = headerCache.get(details.requestId) || {};
  recordMedia(details.tabId, details.documentUrl || details.originUrl || "", "", {
    url: details.url,
    mime,
    kind: kindOf(details.url, mime),
    headers: requestHeaders,
    requiresAuth: Boolean(requestHeaders.authorization || requestHeaders.cookie),
  });
}

function isStreamMime(mime) {
  if (!mime) return false;
  if (mime === "video/mp2t") return false;
  if (mime.startsWith("video/")) return true;
  if (mime === "audio/mpeg" || mime === "audio/mp4" || mime === "audio/ogg") return true;
  return (
    mime === "application/vnd.apple.mpegurl" ||
    mime === "application/x-mpegurl" ||
    mime === "application/dash+xml"
  );
}

function forgetRequest(requestId) {
  headerCache.delete(requestId);
}

function mediaId(tabId, url) {
  let hash = 0x811c9dc5;
  for (let index = 0; index < url.length; index += 1) {
    hash ^= url.charCodeAt(index);
    hash = Math.imul(hash, 0x01000193);
  }
  return tabId + "-" + (hash >>> 0).toString(16);
}

browser.webRequest.onBeforeSendHeaders.addListener(
  onBeforeSendHeaders,
  {
    urls: ["<all_urls>"],
    types: ["media", "xmlhttprequest", "other", "object", "sub_frame"],
  },
  ["requestHeaders"],
);

browser.webRequest.onHeadersReceived.addListener(
  onHeadersReceived,
  { urls: ["<all_urls>"], types: ["media", "xmlhttprequest", "other", "object", "sub_frame"] },
  ["responseHeaders"],
);

browser.webRequest.onCompleted.addListener(
  (details) => forgetRequest(details.requestId),
  { urls: ["<all_urls>"] },
);

browser.webRequest.onErrorOccurred.addListener(
  (details) => forgetRequest(details.requestId),
  { urls: ["<all_urls>"] },
);

browser.tabs.onUpdated.addListener((tabId, changeInfo, tab) => {
  if (changeInfo.url) clearTab(tabId);
  if (changeInfo.title) updateTitle(tabId, changeInfo.title);
  if (changeInfo.status === "complete" && tab && tab.title) updateTitle(tabId, tab.title);
});

browser.tabs.onRemoved.addListener((tabId) => {
  clearTab(tabId);
});

browser.webNavigation.onHistoryStateUpdated.addListener((details) => {
  if (details.frameId === 0) clearTab(details.tabId);
});

browser.runtime.onMessage.addListener((message, sender) => {
  if (!message || typeof message !== "object") return undefined;
  if (message.type === "content/media") {
    const tabId = sender.tab ? sender.tab.id : -1;
    if (tabId < 0) return Promise.resolve({ ok: false, accepted: 0 });
    const pageUrl = (sender.tab && sender.tab.url) || message.pageUrl || "";
    const pageTitle = (sender.tab && sender.tab.title) || message.pageTitle || "";
    let accepted = 0;
    for (const raw of Array.isArray(message.items) ? message.items : []) {
      if (!raw || !raw.url) continue;
      recordMedia(tabId, pageUrl, pageTitle, {
        url: raw.url,
        mime: raw.mime || guessMime(raw.url),
        kind: raw.kind || kindOf(raw.url, raw.mime || ""),
        title: raw.title || "",
        poster: raw.poster || "",
        duration: raw.duration,
        width: raw.width,
        height: raw.height,
      });
      accepted += 1;
    }
    return Promise.resolve({ ok: true, accepted });
  }
  if (message.type === "content/title") {
    if (sender.tab) updateTitle(sender.tab.id, message.title || "");
    return Promise.resolve({ ok: true });
  }
  return undefined;
});

connectNative();
