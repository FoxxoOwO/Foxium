"use strict";

/*
 * Two-tier dark mode content engine.
 *
 * Tier 1 lives in Kotlin: GeckoRuntimeSettings.preferredColorScheme makes sites that honour
 * prefers-color-scheme render dark natively. This script only runs when the page stayed light.
 *
 * Tier 2 has two engines:
 *  - invert: one root CSS filter plus counter-filters for replaced content.
 *  - dynamic: walks computed styles and rewrites bright surfaces / dark text in place, keeping
 *    saturated accents and images readable.
 *
 * Every style this script writes is remembered so a live per-site toggle can restore the page
 * without a reload.
 */
(() => {
  const STYLE_ID = "chrogecko-dark";
  const MAX_ELEMENTS = 6000;
  const MAX_COLOR_CACHE = 512;
  const BORDER_SIDES = ["top", "right", "bottom", "left"];
  const SKIP_TAGS = new Set([
    "SCRIPT",
    "STYLE",
    "LINK",
    "META",
    "TITLE",
    "HEAD",
    "NOSCRIPT",
    "TEMPLATE",
    "BR",
    "HR",
  ]);

  const INVERT_CSS = `
html {
  background-color: #ededed !important;
  filter: invert(1) hue-rotate(180deg) !important;
  color-scheme: dark !important;
}
body {
  background-color: #ededed !important;
}
img, picture, video, canvas, svg image, iframe, embed, object, input[type="image"],
[style*="background-image"] {
  filter: invert(1) hue-rotate(180deg) !important;
}
::-webkit-scrollbar, ::-webkit-scrollbar-thumb {
  background-color: #3c4043 !important;
}
`;

  const DYNAMIC_CSS = `
:root { color-scheme: dark !important; }
input, textarea, select, button {
  background-color: #1e1e1e !important;
  color: #e0e0e0 !important;
  border-color: #3c4043 !important;
}
`;

  let config = { enabled: false, engine: "invert", rules: {} };
  let mode = "off";
  let generation = 0;
  // Set when the page looked already-dark at DOMContentLoaded; re-check once styles finish loading.
  let skippedAsDark = false;
  let appliedToLight = false;
  let observer = null;
  let mutationTimer = null;
  let pendingNodes = [];
  let touched = [];
  let originals = new WeakMap();
  let idleHandle = null;
  const parsedColorCache = new Map();
  const bgTransformCache = new Map();
  const fgTransformCache = new Map();
  const borderDarkenCache = new Map();

  function cacheSet(map, key, value) {
    if (map.size >= MAX_COLOR_CACHE) map.clear();
    map.set(key, value);
    return value;
  }

  function scheduleIdle(fn) {
    if (typeof requestIdleCallback === "function") {
      return requestIdleCallback(fn, { timeout: 120 });
    }
    return setTimeout(fn, 0);
  }

  function clearIdle() {
    if (idleHandle === null) return;
    if (typeof cancelIdleCallback === "function") {
      cancelIdleCallback(idleHandle);
    } else {
      clearTimeout(idleHandle);
    }
    idleHandle = null;
  }

  function normalizeConfig(raw) {
    const rules = {};
    if (raw && raw.rules && typeof raw.rules === "object") {
      for (const [domain, value] of Object.entries(raw.rules)) {
        if (value === "off" || value === "invert" || value === "dynamic") {
          rules[String(domain).toLowerCase()] = value;
        }
      }
    }
    return {
      enabled: Boolean(raw && raw.enabled),
      engine: raw && raw.engine === "dynamic" ? "dynamic" : "invert",
      rules,
    };
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

  function effectiveMode(url, cfg) {
    let host = "";
    try {
      host = new URL(url).hostname.toLowerCase();
    } catch (error) {
      host = "";
    }
    // The background page returns "off" whenever the global switch is off; content must mirror
    // that policy or a later dark/apply broadcast would re-enable a stored per-site rule.
    if (!cfg.enabled) return "off";
    const saved = ruleFor(host);
    if (saved) return saved;
    return cfg.engine;
  }

  function parseColor(value) {
    if (!value) return null;
    if (parsedColorCache.has(value)) return parsedColorCache.get(value);
    const match = /rgba?\(([^)]+)\)/i.exec(value);
    if (!match) return cacheSet(parsedColorCache, value, null);
    const parts = match[1]
      .split(/[,/\s]+/)
      .filter((part) => part.length > 0)
      .map(Number);
    if (parts.length < 3 || parts.some((part) => Number.isNaN(part))) {
      return cacheSet(parsedColorCache, value, null);
    }
    return cacheSet(parsedColorCache, value, {
      r: parts[0],
      g: parts[1],
      b: parts[2],
      a: parts.length > 3 ? parts[3] : 1,
    });
  }

  function luminance(color) {
    const channel = (value) => {
      const normalized = value / 255;
      return normalized <= 0.03928
        ? normalized / 12.92
        : Math.pow((normalized + 0.055) / 1.055, 2.4);
    };
    return 0.2126 * channel(color.r) + 0.7152 * channel(color.g) + 0.0722 * channel(color.b);
  }

  function saturation(color) {
    const max = Math.max(color.r, color.g, color.b);
    const min = Math.min(color.r, color.g, color.b);
    return max === 0 ? 0 : (max - min) / max;
  }

  function rgbChannels(color) {
    const clamp = (value) => Math.max(0, Math.min(255, Math.round(value)));
    return "rgb(" + clamp(color.r) + ", " + clamp(color.g) + ", " + clamp(color.b) + ")";
  }

  function lighten(color, amount) {
    return rgbChannels({
      r: color.r + (255 - color.r) * amount,
      g: color.g + (255 - color.g) * amount,
      b: color.b + (255 - color.b) * amount,
    });
  }

  function darkVariant(color, lum) {
    if (saturation(color) > 0.28) {
      const factor = lum > 0.85 ? 0.09 : 0.14;
      return rgbChannels({
        r: color.r * factor + 10,
        g: color.g * factor + 10,
        b: color.b * factor + 10,
      });
    }
    return lum > 0.82 ? "#121212" : "#1e1e1e";
  }

  function resolveBackgroundOverride(rawColor) {
    if (!rawColor) return "";
    if (bgTransformCache.has(rawColor)) return bgTransformCache.get(rawColor);
    const color = parseColor(rawColor);
    if (!color || color.a <= 0.05) return cacheSet(bgTransformCache, rawColor, "");
    const lum = luminance(color);
    if (lum <= 0.5) return cacheSet(bgTransformCache, rawColor, "");
    return cacheSet(bgTransformCache, rawColor, darkVariant(color, lum));
  }

  function resolveForegroundOverride(rawColor) {
    if (!rawColor) return "";
    if (fgTransformCache.has(rawColor)) return fgTransformCache.get(rawColor);
    const color = parseColor(rawColor);
    if (!color || color.a <= 0.05) return cacheSet(fgTransformCache, rawColor, "");
    const lum = luminance(color);
    if (lum >= 0.5) return cacheSet(fgTransformCache, rawColor, "");
    const mapped = saturation(color) < 0.35 ? "#e0e0e0" : lighten(color, 0.62);
    return cacheSet(fgTransformCache, rawColor, mapped);
  }

  function shouldDarkenBorder(rawColor) {
    if (!rawColor) return false;
    if (borderDarkenCache.has(rawColor)) return borderDarkenCache.get(rawColor);
    const color = parseColor(rawColor);
    const darken = Boolean(color && color.a > 0.05 && luminance(color) > 0.6);
    return cacheSet(borderDarkenCache, rawColor, darken);
  }

  function appendStyle(css) {
    const existing = document.getElementById(STYLE_ID);
    if (existing) existing.remove();
    const style = document.createElement("style");
    style.id = STYLE_ID;
    style.textContent = css;
    (document.head || document.documentElement).appendChild(style);
  }

  function setStyle(element, property, value) {
    let record = originals.get(element);
    if (!record) {
      if (touched.length >= MAX_ELEMENTS) return;
      record = {};
      originals.set(element, record);
      touched.push(element);
    }
    if (!Object.prototype.hasOwnProperty.call(record, property)) {
      record[property] = {
        value: element.style.getPropertyValue(property),
        priority: element.style.getPropertyPriority(property),
      };
    }
    element.style.setProperty(property, value, "important");
  }

  function teardown() {
    generation += 1;
    clearIdle();
    if (observer) {
      observer.disconnect();
      observer = null;
    }
    if (mutationTimer) {
      clearTimeout(mutationTimer);
      mutationTimer = null;
    }
    pendingNodes = [];
    for (const element of touched) {
      const record = originals.get(element);
      if (!record) continue;
      for (const [property, saved] of Object.entries(record)) {
        if (saved.value) element.style.setProperty(property, saved.value, saved.priority);
        else element.style.removeProperty(property);
      }
    }
    touched = [];
    originals = new WeakMap();
    const style = document.getElementById(STYLE_ID);
    if (style) style.remove();
    if (document.documentElement) {
      document.documentElement.removeAttribute("data-chrogecko-dark");
    }
  }

  function report(pageWasDark) {
    browser.runtime
      .sendMessage({
        type: "dark/status",
        url: location.href,
        mode,
        pageWasDark: Boolean(pageWasDark),
      })
      .catch(() => {});
  }

  function computeOverrides(element) {
    if (!element || element.nodeType !== 1) return null;
    if (SKIP_TAGS.has(element.tagName)) return null;
    let computed;
    try {
      computed = getComputedStyle(element);
    } catch (error) {
      return null;
    }

    const updates = [];
    const backgroundOverride = resolveBackgroundOverride(computed.backgroundColor);
    if (backgroundOverride) updates.push(["background-color", backgroundOverride]);

    const foregroundOverride = resolveForegroundOverride(computed.color);
    if (foregroundOverride) updates.push(["color", foregroundOverride]);

    for (const side of BORDER_SIDES) {
      const borderStyle = computed.getPropertyValue("border-" + side + "-style");
      if (!borderStyle || borderStyle === "none" || borderStyle === "hidden") continue;
      const width = parseFloat(computed.getPropertyValue("border-" + side + "-width"));
      if (!Number.isFinite(width) || width <= 0) continue;
      if (!shouldDarkenBorder(computed.getPropertyValue("border-" + side + "-color"))) continue;
      updates.push(["border-" + side + "-color", "#3c4043"]);
    }

    return updates.length > 0 ? updates : null;
  }

  function applyOverridesBatch(batch) {
    for (const [element, updates] of batch) {
      if (touched.length >= MAX_ELEMENTS && !originals.has(element)) break;
      for (const [property, value] of updates) {
        setStyle(element, property, value);
      }
    }
  }

  function processAll(gen) {
    const elements = [];
    if (document.documentElement) elements.push(document.documentElement);
    if (document.body) elements.push(document.body);
    for (const element of document.querySelectorAll("body *")) elements.push(element);

    let index = 0;
    const step = () => {
      idleHandle = null;
      if (gen !== generation || mode !== "dynamic") return;
      const end = Math.min(index + 150, elements.length);
      const batch = [];
      for (; index < end; index += 1) {
        if (touched.length + batch.length >= MAX_ELEMENTS) break;
        const updates = computeOverrides(elements[index]);
        if (updates) batch.push([elements[index], updates]);
      }
      applyOverridesBatch(batch);
      if (index < elements.length && touched.length < MAX_ELEMENTS) {
        idleHandle = scheduleIdle(step);
      } else {
        report(false);
      }
    };
    step();
  }

  function onMutations(mutations) {
    if (mode !== "dynamic") return;
    for (const mutation of mutations) {
      for (const node of mutation.addedNodes) {
        if (node && node.nodeType === 1) pendingNodes.push(node);
      }
    }
    if (mutationTimer) return;
    mutationTimer = setTimeout(() => {
      mutationTimer = null;
      const gen = generation;
      const nodes = pendingNodes;
      pendingNodes = [];
      let processed = 0;
      const batch = [];
      for (const node of nodes) {
        if (gen !== generation || mode !== "dynamic") return;
        const updates = computeOverrides(node);
        if (updates) batch.push([node, updates]);
        processed += 1;
        for (const child of node.querySelectorAll("*")) {
          if (gen !== generation || mode !== "dynamic") return;
          const childUpdates = computeOverrides(child);
          if (childUpdates) batch.push([child, childUpdates]);
          processed += 1;
          if (processed >= 800 || touched.length + batch.length >= MAX_ELEMENTS) break;
        }
        if (processed >= 800 || touched.length + batch.length >= MAX_ELEMENTS) break;
      }
      applyOverridesBatch(batch);
    }, 180);
  }

  function pageLuminance() {
    for (const element of [document.body, document.documentElement]) {
      if (!element) continue;
      const color = parseColor(getComputedStyle(element).backgroundColor);
      if (color && color.a > 0.4) return luminance(color);
    }
    return 1;
  }

  function applyInvert(gen) {
    const start = () => {
      if (gen !== generation || mode !== "invert") return;
      if (pageLuminance() < 0.42) {
        skippedAsDark = true;
        appliedToLight = false;
        report(true);
        return;
      }
      skippedAsDark = false;
      appliedToLight = true;
      appendStyle(INVERT_CSS);
      document.documentElement.setAttribute("data-chrogecko-dark", "invert");
      report(false);
    };
    if (document.body) start();
    else document.addEventListener("DOMContentLoaded", start, { once: true });
  }

  function applyDynamic(gen) {
    const start = () => {
      if (gen !== generation || mode !== "dynamic") return;
      if (pageLuminance() < 0.42) {
        skippedAsDark = true;
        appliedToLight = false;
        report(true);
        return;
      }
      skippedAsDark = false;
      appliedToLight = true;
      appendStyle(DYNAMIC_CSS);
      document.documentElement.setAttribute("data-chrogecko-dark", "dynamic");
      processAll(gen);
      observer = new MutationObserver(onMutations);
      observer.observe(document.documentElement, { childList: true, subtree: true });
    };
    if (document.body) start();
    else document.addEventListener("DOMContentLoaded", start, { once: true });
  }

  function applyMode(nextMode) {
    const appliedAttr = document.documentElement
      ? document.documentElement.getAttribute("data-chrogecko-dark")
      : null;
    if (nextMode === mode && (mode === "off" || appliedAttr === mode)) {
      return;
    }
    teardown();
    mode = nextMode;
    if (mode === "off") {
      skippedAsDark = false;
      appliedToLight = false;
      report(false);
      return;
    }
    if (!document.documentElement) return;
    if (mode === "invert") applyInvert(generation);
    else applyDynamic(generation);
  }

  function requestConfig() {
    browser.runtime
      .sendMessage({ type: "dark/config", url: location.href })
      .then((response) => {
        if (!response) return;
        config = normalizeConfig({
          enabled: response.enabled,
          engine: response.engine,
          rules: response.rules || config.rules,
        });
        applyMode(response.mode || "off");
      })
      .catch(() => {});
  }

  function recheckAfterLoad() {
    // A page can change its background after DOMContentLoaded (deferred CSS, SPA theming), or the
    // first config request can race the native port. Re-evaluate both directions: a dark page may
    // become light, and a light page we inverted may become dark.
    if (mode !== "off") {
      const isDark = pageLuminance() < 0.42;
      const needsReapply = isDark ? !skippedAsDark : !appliedToLight;
      if (needsReapply) {
        teardown();
        skippedAsDark = false;
        appliedToLight = false;
      }
    }
    requestConfig();
  }

  browser.runtime.onMessage.addListener((message) => {
    if (!message || typeof message !== "object") return undefined;
    if (message.type === "dark/apply") {
      config = normalizeConfig(message.config);
      applyMode(effectiveMode(location.href, config));
      return Promise.resolve({ ok: true });
    }
    return undefined;
  });

  requestConfig();
  if (document.readyState === "complete") {
    recheckAfterLoad();
  } else {
    window.addEventListener("load", recheckAfterLoad, { once: true });
  }
  // bfcache / same-document navigations restore the page without re-running content scripts.
  window.addEventListener("pageshow", recheckAfterLoad);
})();
