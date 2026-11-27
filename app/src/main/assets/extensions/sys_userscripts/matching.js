"use strict";

/*
 * URL matching for userscript @match / @include / @exclude. Shared by the background page and by
 * tools/check-userscript-matching.mjs, which is why the functions are globals.
 */

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function globToRegExp(glob) {
  return new RegExp("^" + glob.split("*").map(escapeRegExp).join(".*") + "$");
}

function matchPattern(pattern, url) {
  if (pattern === "<all_urls>") return /^(https?|file|ftp):/i.test(url);
  // Tampermonkey/Violentmonkey allow shorthand patterns without a scheme (e.g. `example.com/*`).
  const normalized = pattern.includes("://") ? pattern : "*://" + pattern.replace(/^\/+/, "/");
  const parsed = /^(\*|https?|file|ftp):\/\/(\*|\*\.[^/*]+|[^/*]*)(\/.*)$/i.exec(normalized);
  if (!parsed) return false;
  let target;
  try {
    target = new URL(url);
  } catch (error) {
    return false;
  }
  const scheme = parsed[1].toLowerCase();
  const protocol = target.protocol.slice(0, -1).toLowerCase();
  if (scheme === "*") {
    if (protocol !== "http" && protocol !== "https") return false;
  } else if (scheme !== protocol) {
    return false;
  }

  let host = parsed[2].toLowerCase();
  let patternPort = null;
  const portMatch = /^(.*):(\d+)$/.exec(host);
  if (portMatch) {
    host = portMatch[1];
    patternPort = Number(portMatch[2]);
  }
  if (patternPort !== null) {
    const actualPort = target.port ? Number(target.port) :
      (protocol === "https" ? 443 : protocol === "http" ? 80 : 0);
    if (actualPort !== patternPort) return false;
  }
  if (host !== "*") {
    const hostname = target.hostname.toLowerCase();
    if (host.startsWith("*.")) {
      const base = host.slice(2);
      if (hostname !== base && !hostname.endsWith("." + base)) return false;
    } else if (hostname !== host) {
      return false;
    }
  }
  return globToRegExp(parsed[3]).test(target.pathname + target.search);
}

function includeMatches(pattern, url) {
  if (pattern.length > 2 && pattern.startsWith("/") && pattern.endsWith("/")) {
    // Long regex patterns can backtrack catastrophically on a URL and freeze the background page.
    if (pattern.length > 256) return false;
    try {
      return new RegExp(pattern.slice(1, -1)).test(url);
    } catch (error) {
      return false;
    }
  }
  return globToRegExp(pattern).test(url);
}

function shouldRun(script, url) {
  const matchedByMatch = script.match.some((pattern) => matchPattern(pattern, url));
  const matchedByInclude = script.include.some((pattern) => includeMatches(pattern, url));
  if (!matchedByMatch && !matchedByInclude) return false;
  return !script.exclude.some((pattern) => includeMatches(pattern, url));
}

if (typeof module !== "undefined" && module.exports) {
  module.exports = { escapeRegExp, globToRegExp, matchPattern, includeMatches, shouldRun };
}
