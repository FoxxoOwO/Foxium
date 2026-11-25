(() => {
  "use strict";

  // Deliberately longer than a casual flick: a reload needs a sustained pull.
  const PULL_THRESHOLD = 170;
  const MIN_PULL_MS = 450;
  const HORIZONTAL_SLOP = 70;
  const COOLDOWN_MS = 1500;

  let startX = 0;
  let startY = 0;
  let tracking = false;
  let lastRefresh = 0;
  let pullStartedAt = 0;
  let lastReportedProgress = -1;

  // Mirrors the pull distance to the app so it can show a native progress ring and haptic tick.
  function reportPull(progress, triggered, force = false) {
    const value = Math.max(0, Math.min(1, progress));
    // Never throttle the terminal report: a partial pull ending at 0 would otherwise be dropped
    // and the app's progress indicator would stay on screen.
    if (!force && !triggered && Math.abs(value - lastReportedProgress) < 0.04) return;
    lastReportedProgress = triggered ? -1 : value;
    browser.runtime
      .sendMessage({
        type: "gesture/pull",
        progress: value,
        triggered: Boolean(triggered),
      })
      .catch(() => {});
  }

  function hasScrolledAncestor(node) {
    let current = node && node.nodeType === 1 ? node : node && node.parentElement;
    while (current && current !== document.documentElement && current !== document.body) {
      if (current.scrollTop > 0) return true;
      current = current.parentElement;
    }
    return false;
  }

  addEventListener("touchstart", (event) => {
    if (event.touches.length !== 1) {
      tracking = false;
      return;
    }
    const touch = event.touches[0];
    startX = touch.clientX;
    startY = touch.clientY;
    pullStartedAt = Date.now();
    const top = window.scrollY <= 0 &&
      document.documentElement.scrollTop <= 0 &&
      !hasScrolledAncestor(event.target);
    tracking = top;
    if (top) reportPull(0, false, true);
  }, { passive: true });

  addEventListener("touchmove", (event) => {
    if (!tracking) return;
    const touch = event.touches[0];
    if (Math.abs(touch.clientX - startX) > HORIZONTAL_SLOP || touch.clientY <= startY) {
      tracking = false;
      reportPull(0, false, true);
      return;
    }
    const byDistance = (touch.clientY - startY) / PULL_THRESHOLD;
    const byTime = (Date.now() - pullStartedAt) / MIN_PULL_MS;
    reportPull(Math.min(byDistance, byTime), false);
  }, { passive: true });

  addEventListener("touchend", (event) => {
    if (!tracking) return;
    tracking = false;
    const touch = event.changedTouches[0];
    const pull = touch.clientY - startY;
    const horizontal = Math.abs(touch.clientX - startX);
    const heldLongEnough = Date.now() - pullStartedAt >= MIN_PULL_MS;
    const willReload = pull >= PULL_THRESHOLD && heldLongEnough && horizontal <= HORIZONTAL_SLOP &&
      window.scrollY <= 2 && document.documentElement.scrollTop <= 2 &&
      Date.now() - lastRefresh >= COOLDOWN_MS;
    reportPull(0, willReload, true);
    if (!willReload) return;
    lastRefresh = Date.now();
    location.reload();
  }, { passive: true });

  addEventListener("touchcancel", () => {
    tracking = false;
    reportPull(0, false, true);
  }, { passive: true });

  // Long press opens the app's context menu: GeckoView's own context-menu callback never fires
  // for touch long presses, so the page reports the targets instead.
  const LONG_PRESS_MS = 500;
  const LONG_PRESS_SLOP = 10;
  const EDGE_GUARD_PX = 32;
  const EDGE_SELECTION_GUARD_MS = 600;
  let pressTimer = null;
  let pressX = 0;
  let pressY = 0;
  let edgeTouchActive = false;
  let edgeSelectionGuardUntil = 0;

  function isEdgeTouch(touch) {
    const width = window.innerWidth || (document.documentElement && document.documentElement.clientWidth) || 0;
    return width > EDGE_GUARD_PX * 2 && (touch.clientX < EDGE_GUARD_PX || touch.clientX > width - EDGE_GUARD_PX);
  }

  function isEdgeSelectionSuppressed() {
    return edgeTouchActive || Date.now() < edgeSelectionGuardUntil;
  }

  function clearPageSelection() {
    try {
      const sel = window.getSelection && window.getSelection();
      if (sel && !sel.isCollapsed && typeof sel.removeAllRanges === "function") {
        sel.removeAllRanges();
      }
    } catch (error) {
      // Ignore selection access errors on restricted frames.
    }
  }

  function endEdgeTouch() {
    if (!edgeTouchActive) return;
    edgeTouchActive = false;
    edgeSelectionGuardUntil = Date.now() + EDGE_SELECTION_GUARD_MS;
    clearPageSelection();
  }

  function absolute(value, base) {
    if (!value) return null;
    try {
      const url = new URL(value, base);
      return url.protocol === "http:" || url.protocol === "https:" ? url.href : null;
    } catch (error) {
      return null;
    }
  }

  function report(node) {
    if (!node) return;
    const element = node.nodeType === 1 ? node : node.parentElement;
    if (!element) return;
    const base = (element.ownerDocument && element.ownerDocument.baseURI) || location.href;
    const linkNode = element.closest ? element.closest("a[href]") : null;
    const imageNode = element.closest ? element.closest("img[src]") : null;
    const link = linkNode ? absolute(linkNode.getAttribute("href"), base) : null;
    const image = imageNode ? absolute(imageNode.currentSrc || imageNode.src, base) : null;
    const text = ((linkNode && linkNode.textContent) || (imageNode && imageNode.alt) || "").trim();
    if (!link && !image) return;
    browser.runtime
      .sendMessage({ type: "contextmenu/request", link, image, text: text.slice(0, 200) })
      .catch(() => {});
  }

  function cancelPress() {
    if (pressTimer !== null) {
      clearTimeout(pressTimer);
      pressTimer = null;
    }
  }

  function cancelInteraction() {
    cancelPress();
    endEdgeTouch();
  }

  addEventListener("touchstart", (event) => {
    cancelPress();
    if (event.touches.length !== 1) {
      endEdgeTouch();
      return;
    }
    const touch = event.touches[0];
    if (isEdgeTouch(touch)) {
      edgeTouchActive = true;
      edgeSelectionGuardUntil = Date.now() + EDGE_SELECTION_GUARD_MS;
      return;
    }
    edgeTouchActive = false;
    edgeSelectionGuardUntil = 0;
    pressX = touch.clientX;
    pressY = touch.clientY;
    const target = event.target;
    pressTimer = setTimeout(() => {
      pressTimer = null;
      report(target);
    }, LONG_PRESS_MS);
  }, { passive: true, capture: true });

  addEventListener("touchmove", (event) => {
    if (pressTimer === null) return;
    const touch = event.touches[0];
    if (Math.abs(touch.clientX - pressX) > LONG_PRESS_SLOP ||
        Math.abs(touch.clientY - pressY) > LONG_PRESS_SLOP) {
      cancelPress();
    }
  }, { passive: true, capture: true });

  addEventListener("touchend", cancelInteraction, { passive: true, capture: true });
  addEventListener("touchcancel", cancelInteraction, { passive: true, capture: true });
  addEventListener("pointercancel", cancelInteraction, { passive: true, capture: true });
  addEventListener("scroll", cancelPress, { passive: true, capture: true });
  addEventListener("blur", cancelInteraction);
  document.addEventListener("visibilitychange", cancelInteraction);

  document.addEventListener("selectstart", (event) => {
    if (!isEdgeSelectionSuppressed()) return;
    if (event && typeof event.preventDefault === "function") {
      event.preventDefault();
    }
    clearPageSelection();
  }, true);

  document.addEventListener("selectionchange", () => {
    if (!isEdgeSelectionSuppressed()) return;
    clearPageSelection();
  }, true);

  // On addons.mozilla.org the page's own "Add to Firefox" button should install through the
  // browser, not download a file. Ask the app to use its WebExtension installer instead.
  const ADD_TO_FIREFOX = /^(add to firefox|přidat do firefoxu|install extension)\b/i;
  addEventListener("click", (event) => {
    try {
      const host = new URL(location.href).hostname.toLowerCase().replace(/^www\./, "");
      if (host !== "addons.mozilla.org") return;
      let node = event.target;
      for (let depth = 0; node && depth < 6; depth += 1) {
        const text = String(node.textContent || "").trim();
        const href = String(node.getAttribute && node.getAttribute("href") || "");
        if (ADD_TO_FIREFOX.test(text) || href.includes(".xpi")) {
          // Let the browser install it once; otherwise AMO's own handler still starts an .xpi
          // download alongside our native installer.
          event.preventDefault();
          event.stopPropagation();
          browser.runtime
            .sendMessage({ type: "amo/install", url: location.href })
            .catch(() => {});
          return;
        }
        node = node.parentElement;
      }
    } catch (error) {
      // Ignore clicks we cannot parse.
    }
  }, true);
})();
