"use strict";

/*
 * Userscript execution sandbox.
 *
 * Scripts run in the content-script scope (Xray vision) with unsafeWindow mapped to the page's
 * wrappedJSObject, which is how Firefox userscript managers have historically balanced page
 * compatibility against extension privileges. GM_* storage is preloaded synchronously from the
 * plan; writes are forwarded to the background store.
 */
(() => {
  const MENU_SYNC_DELAY = 150;
  const REPORT_DELAY = 400;
  const topFrame = window === window.top;

  const menuRegistry = new Map();
  let menuSequence = 0;
  let menuSyncTimer = null;

  const pendingReports = [];
  let reportTimer = null;

  function errorText(error) {
    if (!error) return "unknown error";
    return String((error && error.stack) || error).slice(0, 1000);
  }

  function queueReport(scriptId, status, error) {
    pendingReports.push({ id: scriptId, status, error: error ? errorText(error) : "" });
    if (reportTimer) return;
    reportTimer = setTimeout(() => {
      reportTimer = null;
      const entries = pendingReports.splice(0, pendingReports.length);
      browser.runtime
        .sendMessage({ type: "userscripts/injected", url: location.href, scripts: entries })
        .catch(() => {});
    }, REPORT_DELAY);
  }

  function serializeCommands() {
    return Array.from(menuRegistry.entries()).map(([id, entry]) => ({
      id,
      label: entry.title,
      title: entry.title,
      scriptId: entry.scriptId,
      accessKey: entry.accessKey,
    }));
  }

  function syncMenuCommands() {
    if (menuSyncTimer) return;
    menuSyncTimer = setTimeout(() => {
      menuSyncTimer = null;
      // Reported per frame through the extension API so page scripts can neither inject menu
      // entries nor fire a menu handler: window.postMessage would let them do both. The
      // background merges the frames of a tab into one list.
      browser.runtime
        .sendMessage({ type: "userscripts/menu", url: location.href, commands: serializeCommands() })
        .catch(() => {});
    }, MENU_SYNC_DELAY);
  }

  function runMenuHandler(commandId) {
    const entry = menuRegistry.get(String(commandId));
    if (!entry || typeof entry.handler !== "function") return false;
    try {
      const result = entry.handler();
      if (result && typeof result.catch === "function") {
        result.catch((error) => console.error("[ChroGecko] menu command failed", error));
      }
    } catch (error) {
      console.error("[ChroGecko] menu command failed", error);
    }
    return true;
  }

  // Commands registered in iframes are merged by the background; a click reaches every frame.

  function addStyle(css) {
    const style = document.createElement("style");
    style.textContent = String(css);
    (document.head || document.documentElement).appendChild(style);
    return style;
  }

  function base64ToArrayBuffer(value) {
    if (!value) return new ArrayBuffer(0);
    const binary = atob(value);
    const bytes = new Uint8Array(binary.length);
    for (let index = 0; index < binary.length; index += 1) bytes[index] = binary.charCodeAt(index);
    return bytes.buffer;
  }

  function readPageWindow() {
    try {
      return window.wrappedJSObject || window;
    } catch (error) {
      return window;
    }
  }

  function buildShims(script, values) {
    function GM_getValue(name, defaultValue) {
      const key = String(name);
      return Object.prototype.hasOwnProperty.call(values, key) ? values[key] : defaultValue;
    }

    function GM_setValue(name, value) {
      const key = String(name);
      values[key] = value;
      browser.runtime
        .sendMessage({ type: "gm/set", scriptId: script.id, key, value })
        .catch(() => {});
    }

    function GM_deleteValue(name) {
      const key = String(name);
      delete values[key];
      browser.runtime.sendMessage({ type: "gm/delete", scriptId: script.id, key }).catch(() => {});
    }

    function GM_listValues() {
      return Object.keys(values);
    }

    function GM_xmlhttpRequest(details) {
      const request = details || {};
      let finished = false;
      const timeoutMs = Number(request.timeout) > 0 ? Number(request.timeout) : 30000;
      const guard = setTimeout(() => {
        if (finished) return;
        finished = true;
        const failure = { readyState: 4, status: 0, statusText: "timeout", error: "timeout" };
        if (typeof request.onerror === "function") request.onerror(failure);
        if (typeof request.onloadend === "function") request.onloadend(failure);
      }, timeoutMs + 5000);

      const control = {
        abort() {
          if (finished) return;
          finished = true;
          clearTimeout(guard);
          const response = { readyState: 0, status: 0, statusText: "abort" };
          if (typeof request.onabort === "function") request.onabort(response);
          if (typeof request.onloadend === "function") request.onloadend(response);
        },
      };

      browser.runtime
        .sendMessage({
          type: "gm/xhr",
          details: {
            url: String(request.url || ""),
            method: request.method || "GET",
            headers: request.headers || {},
            data: request.data,
            timeout: timeoutMs,
            anonymous: Boolean(request.anonymous),
            responseType: request.responseType || "text",
          },
        })
        .then((result) => {
          if (finished) return;
          finished = true;
          clearTimeout(guard);
          if (!result || !result.ok) {
            const failure = {
              readyState: 4,
              status: 0,
              statusText: "error",
              error: (result && result.error) || "request failed",
            };
            if (typeof request.onerror === "function") request.onerror(failure);
            if (typeof request.onloadend === "function") request.onloadend(failure);
            return;
          }
          const response = {
            readyState: 4,
            status: result.status,
            statusText: result.statusText,
            finalUrl: result.finalUrl,
            responseHeaders: result.responseHeaders,
            responseText: result.responseText,
            context: request.context,
          };
          if (request.responseType === "arraybuffer") {
            response.response = base64ToArrayBuffer(result.responseBase64);
          } else if (request.responseType === "json") {
            try {
              response.response = JSON.parse(result.responseText);
            } catch (error) {
              response.response = null;
            }
          } else {
            response.response = result.responseText;
          }
          try {
            if (typeof request.onreadystatechange === "function") {
              request.onreadystatechange({ readyState: 4, status: result.status });
            }
            if (typeof request.onload === "function") request.onload(response);
            if (typeof request.onloadend === "function") request.onloadend(response);
          } catch (error) {
            console.error("[ChroGecko] GM_xmlhttpRequest callback failed", error);
          }
        })
        .catch((error) => {
          if (finished) return;
          finished = true;
          clearTimeout(guard);
          const failure = { readyState: 4, status: 0, statusText: "error", error: String(error) };
          if (typeof request.onerror === "function") request.onerror(failure);
          if (typeof request.onloadend === "function") request.onloadend(failure);
        });

      return control;
    }

    function GM_registerMenuCommand(title, handler, accessKey) {
      menuSequence += 1;
      const id = script.id + ":" + menuSequence;
      menuRegistry.set(id, {
        title: String(title || ""),
        handler,
        scriptId: script.id,
        accessKey: accessKey == null ? "" : String(accessKey),
      });
      syncMenuCommands();
      return id;
    }

    function GM_unregisterMenuCommand(id) {
      if (menuRegistry.delete(String(id))) syncMenuCommands();
    }

    const scriptResources = script.resources || {};

    function GM_getResourceText(name) {
      const entry = scriptResources[name];
      return entry && typeof entry.text === "string" ? entry.text : null;
    }

    function GM_getResourceURL(name) {
      const entry = scriptResources[name];
      return entry && entry.dataUrl ? entry.dataUrl : "";
    }

    function GM_log(...args) {
      console.log("[ChroGecko][" + script.name + "]", ...args);
    }

    const GM_info = {
      script: {
        name: script.name,
        namespace: script.namespace,
        version: script.version,
        description: script.description,
        matches: script.match,
        includes: script.include,
        excludes: script.exclude,
        runAt: script.runAt,
        grant: script.grants,
      },
      scriptHandler: "Foxium Userscripts",
      version: "1.0.0",
      scriptMetaStr: script.meta || "",
    };

    const GM = {
      info: GM_info,
      addStyle,
    };
    const granted = new Set(Array.isArray(script.grants) ? script.grants.map((g) => String(g).trim()) : []);
    const hasGrant = (legacyName, modernName) =>
      granted.has(legacyName) || (modernName ? granted.has(modernName) : false);

    if (hasGrant("GM_getValue", "GM.getValue")) GM.getValue = (name, defaultValue) => Promise.resolve(GM_getValue(name, defaultValue));
    if (hasGrant("GM_setValue", "GM.setValue")) GM.setValue = (name, value) => Promise.resolve(GM_setValue(name, value));
    if (hasGrant("GM_deleteValue", "GM.deleteValue")) GM.deleteValue = (name) => Promise.resolve(GM_deleteValue(name));
    if (hasGrant("GM_listValues", "GM.listValues")) GM.listValues = () => Promise.resolve(GM_listValues());
    if (hasGrant("GM_getResourceText", "GM.getResourceText")) GM.getResourceText = (name) => GM_getResourceText(name);
    if (hasGrant("GM_getResourceURL", "GM.getResourceURL")) GM.getResourceURL = (name) => GM_getResourceURL(name);
    if (hasGrant("GM_registerMenuCommand", "GM.registerMenuCommand")) {
      GM.registerMenuCommand = (title, handler, accessKey) =>
        GM_registerMenuCommand(title, handler, accessKey);
    }
    if (hasGrant("GM_unregisterMenuCommand", "GM.unregisterMenuCommand")) {
      GM.unregisterMenuCommand = (id) => GM_unregisterMenuCommand(id);
    }
    if (hasGrant("GM_xmlhttpRequest", "GM.xmlHttpRequest")) {
      GM.xmlHttpRequest = (details) =>
        new Promise((resolve, reject) => {
          GM_xmlhttpRequest(
            Object.assign({}, details, {
              onload: resolve,
              onerror: (response) => reject(new Error((response && response.error) || "request failed")),
            }),
          );
        });
    }

    return {
      GM_xmlhttpRequest: granted.has("GM_xmlhttpRequest") ? GM_xmlhttpRequest : undefined,
      GM_setValue: granted.has("GM_setValue") ? GM_setValue : undefined,
      GM_getValue: granted.has("GM_getValue") ? GM_getValue : undefined,
      GM_deleteValue: granted.has("GM_deleteValue") ? GM_deleteValue : undefined,
      GM_listValues: granted.has("GM_listValues") ? GM_listValues : undefined,
      GM_addStyle: addStyle,
      GM_getResourceText: granted.has("GM_getResourceText") ? GM_getResourceText : undefined,
      GM_getResourceURL: granted.has("GM_getResourceURL") ? GM_getResourceURL : undefined,
      GM_registerMenuCommand: granted.has("GM_registerMenuCommand") ? GM_registerMenuCommand : undefined,
      GM_unregisterMenuCommand: granted.has("GM_unregisterMenuCommand") ? GM_unregisterMenuCommand : undefined,
      GM_log,
      GM_info,
      GM,
      unsafeWindow: readPageWindow(),
    };
  }

  /**
   * A script that asks for no GM_* API expects `window` to be the page's window (that is what
   * Tampermonkey's `@grant none` means). Those run in the page realm so page globals, page
   * libraries and `window.foo` assignments resolve; everything that uses GM_* keeps the isolated
   * content-script sandbox.
   */
  function wantsPageScope(script) {
    const grants = Array.isArray(script.grants) ? script.grants : [];
    if (grants.length === 0) return true;
    return grants.length === 1 && String(grants[0]).trim().toLowerCase() === "none";
  }

  let pageRunner = null;
  let pageRunnerResolved = false;

  /**
   * Runs [body] in the page's own realm through an injected <script>. An `eval` called from an
   * exported function still evaluates in the content-script compartment (measured: the page-less
   * `window.wrappedJSObject` was visible), so only a real script element reaches page globals.
   */
  function pageScopeRunner() {
    if (pageRunnerResolved) return pageRunner;
    pageRunnerResolved = true;
    const page = window.wrappedJSObject;
    if (!page) return null;
    pageRunner = function (body, gmInfo) {
      const parent = document.head || document.documentElement;
      if (!parent) return { started: false, error: null };
      const suffix = Math.random().toString(36).slice(2);
      const marker = "__chrogeckoRun" + suffix;
      const errorMarker = "__chrogeckoErr" + suffix;
      const element = document.createElement("script");
      element.textContent =
        "(function(){window[" +
        JSON.stringify(marker) +
        "]=1;var unsafeWindow=window;var GM_info=" +
        JSON.stringify(gmInfo) +
        ";var GM={info:GM_info};try{\n" +
        body +
        "\n}catch(e){window[" +
        JSON.stringify(errorMarker) +
        "]=String((e&&e.stack)||e);}})();";
      parent.appendChild(element);
      element.remove();
      let started = false;
      let error = null;
      try {
        started = Boolean(page[marker]);
        delete page[marker];
        if (page[errorMarker]) {
          error = String(page[errorMarker]);
          delete page[errorMarker];
        }
      } catch (err) {
        started = false;
      }
      return { started, error };
    };
    return pageRunner;
  }

  /** Runs [body] in page scope. Returns false when the page refused it and the sandbox must too. */
  function executeInPageScope(script, body, gmInfo) {
    const run = pageScopeRunner();
    if (!run) return false;
    let outcome = { started: false, error: null };
    try {
      outcome = run(body, gmInfo);
    } catch (error) {
      outcome = { started: false, error: null };
    }
    // A page CSP without 'unsafe-inline' silently drops the element, so nothing ran: the sandbox
    // retry cannot execute the script twice.
    if (!outcome.started) return false;
    if (outcome.error) {
      queueReport(script.id, "error", outcome.error);
    } else {
      queueReport(script.id, "ok", null);
    }
    return true;
  }

  function executeScript(script) {
    const values = Object.assign({}, script.values || {});
    const shims = buildShims(script, values);
    const requires = (script.requires || [])
      .map((required) => "// == @require " + required.url + " ==\n" + required.code)
      .join("\n;\n");
    const sourceUrl =
      "\n//# sourceURL=chrogecko-userscript-" + encodeURIComponent(script.name) + ".js";
    const body = (requires ? requires + "\n;\n" : "") + script.source + sourceUrl;
    if (wantsPageScope(script) && executeInPageScope(script, body, shims.GM_info)) return;
    const names = Object.keys(shims);
    let runner;
    try {
      runner = new Function(...names, body);
    } catch (error) {
      queueReport(script.id, "error", error);
      return;
    }
    try {
      runner(...names.map((name) => shims[name]));
      queueReport(script.id, "ok", null);
    } catch (error) {
      queueReport(script.id, "error", error);
    }
  }

  function scheduleIdle(callback) {
    if (typeof window.requestIdleCallback === "function") {
      window.requestIdleCallback(callback, { timeout: 2000 });
    } else {
      setTimeout(callback, 1);
    }
  }

  function runAtStage(runAt, callback) {
    if (runAt === "document-start") {
      callback();
      return;
    }
    if (runAt === "document-end") {
      if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", callback, { once: true });
      } else {
        callback();
      }
      return;
    }
    if (document.readyState === "complete") {
      scheduleIdle(callback);
    } else {
      window.addEventListener("load", () => scheduleIdle(callback), { once: true });
    }
  }

  browser.runtime.onMessage.addListener((message) => {
    if (!message || typeof message !== "object") return undefined;
    if (message.type === "userscripts/plan-full") {
      applyPlan(message.plan);
      return Promise.resolve({ ok: true });
    }
    if (message.type === "userscripts/menu-command") {
      return Promise.resolve({ ok: runMenuHandler(message.commandId) });
    }
    return undefined;
  });

  const scheduledScripts = new Set();
  function applyPlan(plan) {
    if (!plan || !Array.isArray(plan.scripts)) return;
    for (const script of plan.scripts) {
      if (!script || !script.source || script.pending || scheduledScripts.has(script.id)) continue;
      scheduledScripts.add(script.id);
      runAtStage(script.runAt, () => executeScript(script));
    }
  }

  browser.runtime
    .sendMessage({ type: "userscripts/plan", url: location.href })
    .then(applyPlan)
    .catch(() => {});

  // Reset this frame's menu entry on every document so stale commands never outlive their page.
  syncMenuCommands();
})();
