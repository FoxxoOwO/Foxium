"use strict";

/*
 * Reports <video>/<audio> sources to the background sniffer. Network-level detection runs in the
 * background; this covers element sources that never pass through webRequest (blob stores, MSE
 * manifests loaded by player scripts, or URLs hidden behind redirects).
 */
(() => {
  const SKIP_SUBTREE_TAGS = new Set([
    "SVG",
    "SCRIPT",
    "STYLE",
    "NOSCRIPT",
    "TEMPLATE",
    "TEXTAREA",
    "SELECT",
    "PRE",
    "CODE",
  ]);
  const seen = new Set();
  let scheduled = false;
  let observer = null;

  function absolute(url) {
    if (!url) return "";
    try {
      const resolved = new URL(url, location.href);
      if (resolved.protocol === "http:" || resolved.protocol === "https:") return resolved.href;
    } catch (error) {
      return "";
    }
    return "";
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
    const lowerMime = (mime || "").toLowerCase();
    if (/\.m3u8(?:$|[?#])/i.test(url) || lowerMime.includes("mpegurl")) return "hls";
    if (/\.mpd(?:$|[?#])/i.test(url) || lowerMime.includes("dash+xml")) return "dash";
    return "file";
  }

  function videoTitle(element) {
    return (
      element.getAttribute("title") ||
      element.getAttribute("aria-label") ||
      element.getAttribute("data-title") ||
      ""
    );
  }

  function collect() {
    const items = [];
    const elements = document.querySelectorAll("video, audio");
    for (const element of elements) {
      const urls = new Set();
      if (element.currentSrc) urls.add(element.currentSrc);
      if (element.src) urls.add(element.src);
      for (const source of element.querySelectorAll("source[src]")) urls.add(source.src);
      for (const raw of urls) {
        const url = absolute(raw);
        if (!url || seen.has(url)) continue;
        seen.add(url);
        const mime = guessMime(url);
        const duration =
          Number.isFinite(element.duration) && element.duration > 0
            ? Math.round(element.duration * 1000)
            : null;
        const width = element.videoWidth || null;
        const height = element.videoHeight || null;
        items.push({
          url,
          mime,
          kind: kindOf(url, mime),
          title: videoTitle(element),
          poster: absolute(element.poster),
          duration,
          width,
          height,
        });
      }
    }
    if (seen.size > 400) seen.clear();
    if (items.length === 0) return;
    browser.runtime
      .sendMessage({
        type: "content/media",
        pageUrl: location.href,
        pageTitle: document.title,
        items,
      })
      .catch(() => {});
  }

  function schedule() {
    if (scheduled) return;
    scheduled = true;
    setTimeout(() => {
      scheduled = false;
      collect();
    }, 700);
  }

  function hasMediaNode(mutations) {
    for (const mutation of mutations) {
      for (const node of mutation.addedNodes) {
        if (!node || node.nodeType !== 1) continue;
        const tag = node.tagName;
        if (tag === "VIDEO" || tag === "AUDIO" || tag === "SOURCE") return true;
        if (!node.childElementCount || SKIP_SUBTREE_TAGS.has(tag)) continue;
        if (
          node.getElementsByTagName &&
          (node.getElementsByTagName("video").length > 0 ||
            node.getElementsByTagName("audio").length > 0)
        ) {
          return true;
        }
      }
    }
    return false;
  }

  function start() {
    schedule();
    if (document.documentElement && !observer) {
      observer = new MutationObserver((mutations) => {
        if (scheduled) return;
        if (hasMediaNode(mutations)) schedule();
      });
      observer.observe(document.documentElement, { childList: true, subtree: true });
    }
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", start, { once: true });
  } else {
    start();
  }
  window.addEventListener("load", schedule, { once: true });
  // Listen to passive media lifecycle events instead of every click on the page.
  document.addEventListener("loadedmetadata", schedule, { capture: true, passive: true });
  document.addEventListener("play", schedule, { capture: true, passive: true });
})();
