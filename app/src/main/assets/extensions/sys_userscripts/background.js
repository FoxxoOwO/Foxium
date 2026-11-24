"use strict";

/*
 * Privileged userscript host.
 *
 * Kotlin parses ==UserScript== metadata and pushes the normalized scripts here. This background
 * page decides which scripts run on a URL, resolves @require dependencies (extension principal,
 * so page CORS does not apply), persists GM_* storage, and proxies GM_xmlhttpRequest.
 */

const NATIVE_APP = "chrogecko";
const SCRIPTS_KEY = "chrogecko.userscripts.scripts";
const VALUES_KEY = "chrogecko.userscripts.values";
const REQUIRES_KEY = "chrogecko.userscripts.requires";
const RESOURCE_MAX_BYTES = 2 * 1024 * 1024;
const REQUIRE_TTL_MS = 7 * 24 * 60 * 60 * 1000;
const RESOURCE_TTL_MS = 24 * 60 * 60 * 1000;
const MAX_CACHED_RESOURCES = 64;
const MAX_REQUIRE_BYTES = 4 * 1024 * 1024;

let nativePort = null;
let reconnectTimer = null;
let reconnectDelay = 1000;

let scripts = [];
/** "tabId:frameId" -> commands registered in that frame, merged per tab for the native menu. */
const frameMenus = new Map();
/** tabId -> Map<commandId, frameId[]>; a click only reaches frames that registered that command. */
const menuTargets = new Map();
/** scriptId -> { key: value } */
let valuesByScript = {};
/** url -> { code, fetchedAt } */
let requiresById = {};
/** url -> { entry, fetchedAt } */
const resourcesByUrl = new Map();

let scriptsPersistTimer = null;
let valuesPersistTimer = null;
let requiresPersistTimer = null;

const ready = (async () => {
  try {
    const stored = await browser.storage.local.get([SCRIPTS_KEY, VALUES_KEY, REQUIRES_KEY]);
    if (Array.isArray(stored[SCRIPTS_KEY])) {
      scripts = stored[SCRIPTS_KEY].map(normalizeScript).filter(Boolean);
    }
    if (stored[VALUES_KEY] && typeof stored[VALUES_KEY] === "object") {
      valuesByScript = stored[VALUES_KEY];
    }
    if (stored[REQUIRES_KEY] && typeof stored[REQUIRES_KEY] === "object") {
      requiresById = stored[REQUIRES_KEY];
    }
  } catch (error) {
    scripts = [];
  }
  connectNative();
})();

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
  sendNative({ type: "userscripts/ready", version: browser.runtime.getManifest().version });
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
  if (message.type === "userscripts/sync") {
    onSync(message.scripts || []);
  } else if (message.type === "userscripts/menu-command") {
    const tabId = Number(message.tabId);
    if (Number.isFinite(tabId)) {
      const commandId = String(message.commandId || "");
      const registered = menuTargets.get(tabId);
      const frameIds = (registered && registered.get(commandId)) || [0];
      for (const frameId of frameIds) {
        browser.tabs
          .sendMessage(tabId, { type: "userscripts/menu-command", commandId }, { frameId })
          .catch(() => {});
      }
    }
  } else if (message.type === "userscripts/request-status") {
    sendStatus();
  }
}

function normalizeRunAt(value) {
  if (value === "document-start" || value === "document-end" || value === "document-idle") {
    return value;
  }
  if (value === "document-body") return "document-end";
  return "document-end";
}

function asStringArray(value) {
  if (Array.isArray(value)) return value.map((item) => String(item)).filter(Boolean);
  if (typeof value === "string" && value.trim()) return [value.trim()];
  return [];
}

function normalizeScript(raw) {
  if (!raw || raw.id == null) return null;
  return {
    id: String(raw.id),
    name: String(raw.name || "Unnamed script"),
    namespace: String(raw.namespace || ""),
    version: String(raw.version || "0"),
    description: String(raw.description || ""),
    enabled: raw.enabled !== false,
    match: asStringArray(raw.match || raw.matchPatterns),
    include: asStringArray(raw.include || raw.includePatterns),
    exclude: asStringArray(raw.exclude || raw.excludePatterns),
    runAt: normalizeRunAt(raw.runAt),
    grants: asStringArray(raw.grants),
    requires: asStringArray(raw.requires),
    resources: raw.resources && typeof raw.resources === "object" ? raw.resources : {},
    source: String(raw.source || ""),
    meta: String(raw.meta || ""),
    updateUrl: raw.updateUrl ? String(raw.updateUrl) : "",
  };
}

function onSync(rawScripts) {
  scripts = rawScripts.map(normalizeScript).filter(Boolean);
  scheduleScriptsPersist();
  sendStatus();
}

function sendStatus() {
  sendNative({
    type: "userscripts/status",
    count: scripts.length,
    enabled: scripts.filter((script) => script.enabled).length,
  });
}

function scheduleScriptsPersist() {
  if (scriptsPersistTimer) clearTimeout(scriptsPersistTimer);
  scriptsPersistTimer = setTimeout(() => {
    scriptsPersistTimer = null;
    browser.storage.local.set({ [SCRIPTS_KEY]: scripts }).catch(() => {});
  }, 400);
}

function scheduleValuesPersist() {
  if (valuesPersistTimer) clearTimeout(valuesPersistTimer);
  valuesPersistTimer = setTimeout(() => {
    valuesPersistTimer = null;
    browser.storage.local.set({ [VALUES_KEY]: valuesByScript }).catch(() => {});
  }, 400);
}

function scheduleRequiresPersist() {
  if (requiresPersistTimer) clearTimeout(requiresPersistTimer);
  requiresPersistTimer = setTimeout(() => {
    requiresPersistTimer = null;
    browser.storage.local.set({ [REQUIRES_KEY]: requiresById }).catch(() => {});
  }, 800);
}

function isAllowedRemoteUrl(rawUrl, httpsOnly) {
  let parsed;
  try {
    parsed = new URL(String(rawUrl));
  } catch (error) {
    return false;
  }
  const proto = parsed.protocol.toLowerCase();
  if (httpsOnly) {
    if (proto !== "https:") return false;
  } else if (proto !== "http:" && proto !== "https:") {
    return false;
  }
  return !isBlockedXhrHost(parsed.hostname);
}

async function fetchRequire(url) {
  try {
    if (!isAllowedRemoteUrl(url, true)) {
      throw new Error("Blocked unsafe or non-https @require URL");
    }
    const response = await fetch(url, {
      credentials: "omit",
      cache: "force-cache",
      redirect: "follow",
    });
    if (!response.ok) throw new Error("HTTP " + response.status);
    const text = await response.text();
    return { code: text.slice(0, MAX_REQUIRE_BYTES), fetchedAt: Date.now() };
  } catch (error) {
    return { code: "", fetchedAt: Date.now(), error: String(error) };
  }
}

async function resolveRequires(urls, quick = false) {
  const result = [];
  let pending = false;
  for (const url of urls) {
    let entry = requiresById[url];
    const fresh = entry && entry.code && Date.now() - entry.fetchedAt <= REQUIRE_TTL_MS;
    if (quick && !fresh) {
      pending = true;
      continue;
    }
    if (!fresh) {
      entry = await fetchRequire(url);
      requiresById[url] = entry;
      scheduleRequiresPersist();
    }
    if (entry.code) result.push({ url, code: entry.code });
  }
  return { result, pending };
}

async function resolveResources(map, quick = false) {
  const out = {};
  let pending = false;
  const entries = map && typeof map === "object" ? Object.entries(map) : [];
  const now = Date.now();
  for (const [name, url] of entries) {
    const resourceUrl = String(url);
    const cached = resourcesByUrl.get(resourceUrl);
    if (cached && now - cached.fetchedAt < RESOURCE_TTL_MS) {
      out[name] = cached.entry;
      continue;
    }
    if (quick) {
      pending = true;
      continue;
    }
    try {
      if (!isAllowedRemoteUrl(resourceUrl, false)) {
        throw new Error("Blocked unsafe @resource URL");
      }
      const response = await fetch(resourceUrl, { credentials: "omit" });
      if (!response.ok) throw new Error("HTTP " + response.status);
      const buffer = await response.arrayBuffer();
      if (buffer.byteLength > RESOURCE_MAX_BYTES) throw new Error("resource too large");
      const mime = (response.headers.get("content-type") || "text/plain").split(";")[0].trim();
      const entry = {
        mime,
        dataUrl: "data:" + mime + ";base64," + base64(buffer),
        text: new TextDecoder("utf-8").decode(buffer),
      };
      resourcesByUrl.set(resourceUrl, { entry, fetchedAt: now });
      while (resourcesByUrl.size > MAX_CACHED_RESOURCES) {
        const oldest = resourcesByUrl.keys().next();
        if (oldest.done) break;
        resourcesByUrl.delete(oldest.value);
      }
      out[name] = entry;
    } catch (error) {
      out[name] = { error: String(error) };
    }
  }
  return { result: out, pending };
}

async function buildPlan(url, quick = false) {
  await ready;
  const eligible = scripts.filter((script) => script.enabled && shouldRun(script, url));
  const plan = [];
  for (const script of eligible) {
    // In quick mode only cached dependencies are used. Scripts that still need a network fetch
    // are marked pending and receive their full plan as soon as it is ready, so document-start
    // scripts without remote dependencies run immediately at document_start.
    const resolvedRequires = await resolveRequires(script.requires || [], quick);
    const resolvedResources = await resolveResources(script.resources, quick);
    const requires = resolvedRequires.result;
    const resources = resolvedResources.result;
    const pending = resolvedRequires.pending || resolvedResources.pending;
    const values = valuesByScript[script.id] || {};
    plan.push({
      id: script.id,
      name: script.name,
      namespace: script.namespace,
      version: script.version,
      description: script.description,
      runAt: script.runAt,
      grants: script.grants,
      match: script.match,
      include: script.include,
      exclude: script.exclude,
      source: script.source,
      meta: script.meta,
      requires,
      resources,
      values,
      pending,
    });
  }
  return { url, scripts: plan };
}

function base64(buffer) {
  const bytes = new Uint8Array(buffer);
  let binary = "";
  const chunk = 0x8000;
  for (let index = 0; index < bytes.length; index += chunk) {
    binary += String.fromCharCode.apply(null, bytes.subarray(index, index + chunk));
  }
  return btoa(binary);
}

function isBlockedXhrHost(hostname) {
  const host = String(hostname || "").trim().toLowerCase().replace(/^\[|\]$/g, "");
  if (
    !host ||
    host === "localhost" ||
    host.endsWith(".localhost") ||
    host === "0.0.0.0" ||
    host === "::" ||
    host === "::1"
  ) {
    return true;
  }
  if (/^127\./.test(host) || /^10\./.test(host) || /^169\.254\./.test(host) || /^192\.168\./.test(host)) {
    return true;
  }
  const m172 = /^172\.(\d+)\./.exec(host);
  if (m172 && Number(m172[1]) >= 16 && Number(m172[1]) <= 31) return true;
  if (/^(fc|fd)[0-9a-f]{2}:/i.test(host) || /^fe80:/i.test(host)) return true;
  return false;
}

async function handleXhr(details) {
  if (!details || !details.url) return { ok: false, error: "missing url" };
  let parsedUrl;
  try {
    parsedUrl = new URL(String(details.url));
  } catch (error) {
    return { ok: false, error: "invalid url" };
  }
  if (parsedUrl.protocol !== "http:" && parsedUrl.protocol !== "https:") {
    return { ok: false, error: "Only http/https URLs are allowed in GM_xmlhttpRequest" };
  }
  if (isBlockedXhrHost(parsedUrl.hostname)) {
    return { ok: false, error: "Blocked non-public target host in GM_xmlhttpRequest" };
  }
  const controller = new AbortController();
  const timeoutMs = Number(details.timeout) > 0 ? Number(details.timeout) : 30000;
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  const headers = {};
  for (const [name, value] of Object.entries(details.headers || {})) {
    // shortcut: forbidden fetch headers (Cookie/Referer/Origin) are dropped; upgrade by
    // injecting them with webRequest if a target site rejects the proxied request.
    if (name.toLowerCase() === "cookie") continue;
    headers[name] = String(value);
  }
  const method = String(details.method || "GET").toUpperCase();
  // A body is illegal on GET/HEAD, and `undefined` is not a valid fetch body in Gecko either.
  const hasBody = method !== "GET" && method !== "HEAD" && details.data != null;
  try {
    const response = await fetch(details.url, {
      method,
      headers,
      ...(hasBody ? { body: details.data } : {}),
      credentials: details.anonymous ? "omit" : "include",
      redirect: "follow",
      signal: controller.signal,
      cache: "no-store",
    });
    const buffer = await response.arrayBuffer();
    const responseHeaders = Array.from(response.headers.entries())
      .map(([name, value]) => name + ": " + value)
      .join("\r\n");
    const binaryResponse = details.responseType === "arraybuffer";
    return {
      ok: true,
      status: response.status,
      statusText: response.statusText,
      finalUrl: response.url,
      responseHeaders,
      responseText: binaryResponse ? "" : new TextDecoder().decode(buffer),
      responseBase64: binaryResponse ? base64(buffer) : "",
    };
  } catch (error) {
    return { ok: false, error: String(error) };
  } finally {
    clearTimeout(timer);
  }
}

browser.runtime.onMessage.addListener((message, sender) => {
  if (!message || typeof message !== "object") return undefined;
  switch (message.type) {
    case "userscripts/plan": {
      const url = message.url || (sender.tab && sender.tab.url) || "";
      if (sender && sender.tab && sender.tab.id >= 0) {
        const tabId = sender.tab.id;
        // Return the cached-only plan immediately; deliver the full one when network deps land.
        buildPlan(url, false).then((full) => {
          browser.tabs.sendMessage(tabId, { type: "userscripts/plan-full", plan: full }).catch(() => {});
        });
        return buildPlan(url, true);
      }
      // No tab sender (e.g. self-checks): keep the original full-plan behaviour.
      return buildPlan(url, false);
    }
    case "gm/xhr":
      return handleXhr(message.details);
    case "gm/set": {
      const id = String(message.scriptId);
      const values = valuesByScript[id] || {};
      values[String(message.key)] = message.value;
      valuesByScript[id] = values;
      scheduleValuesPersist();
      return Promise.resolve({ ok: true });
    }
    case "gm/delete": {
      const id = String(message.scriptId);
      const values = valuesByScript[id] || {};
      delete values[String(message.key)];
      valuesByScript[id] = values;
      scheduleValuesPersist();
      return Promise.resolve({ ok: true });
    }
    case "userscripts/injected": {
      const tabId = sender.tab ? sender.tab.id : -1;
      if (tabId >= 0) {
        sendNative({
          type: "userscripts/injected",
          tabId,
          url: message.url || (sender.tab && sender.tab.url) || "",
          scripts: Array.isArray(message.scripts) ? message.scripts : [],
        });
      }
      return Promise.resolve({ ok: true });
    }
    case "userscripts/menu": {
      const tabId = sender.tab ? sender.tab.id : -1;
      if (tabId < 0) return Promise.resolve({ ok: true });
      const frameId = Number.isFinite(sender.frameId) ? sender.frameId : 0;
      const key = tabId + ":" + frameId;
      const commands = Array.isArray(message.commands) ? message.commands : [];
      if (commands.length) frameMenus.set(key, commands);
      else frameMenus.delete(key);
      const merged = [];
      const seen = new Set();
      const targets = new Map();
      frameMenus.forEach((list, frameKey) => {
        if (!frameKey.startsWith(tabId + ":")) return;
        const registeredFrame = Number(frameKey.slice(frameKey.indexOf(":") + 1));
        for (const command of list) {
          if (!command || typeof command.id !== "string") continue;
          if (!seen.has(command.id)) {
            seen.add(command.id);
            merged.push(command);
          }
          const frameIds = targets.get(command.id) || [];
          if (!frameIds.includes(registeredFrame)) frameIds.push(registeredFrame);
          targets.set(command.id, frameIds);
        }
      });
      menuTargets.set(tabId, targets);
      sendNative({
        type: "userscripts/menu",
        tabId,
        url: message.url || (sender.tab && sender.tab.url) || "",
        commands: merged,
      });
      return Promise.resolve({ ok: true });
    }
    default:
      return undefined;
  }
});

// A navigation wipes the tab's frames, so their menu entries must not survive it.
browser.tabs.onUpdated.addListener((tabId, changeInfo) => {
  if (!changeInfo || changeInfo.status !== "loading") return;
  for (const key of Array.from(frameMenus.keys())) {
    if (key.startsWith(tabId + ":")) frameMenus.delete(key);
  }
  menuTargets.delete(tabId);
});

browser.tabs.onRemoved.addListener((tabId) => {
  for (const key of Array.from(frameMenus.keys())) {
    if (key.startsWith(tabId + ":")) frameMenus.delete(key);
  }
  menuTargets.delete(tabId);
});
