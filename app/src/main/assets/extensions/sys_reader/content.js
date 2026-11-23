"use strict";

/*
 * Reader view built on Mozilla's Readability. The page is only transformed on demand: the
 * readiness flag uses the cheap `isProbablyReaderable()` heuristic, the full extraction runs
 * when the user turns reading mode on.
 */

const STYLE_ID = "chrogecko-reader-style";
const ROOT_ID = "chrogecko-reader";

let active = false;
let savedNodes = null;
let savedScroll = 0;

function readerCss() {
  return [
    ":root { color-scheme: light dark; }",
    "html, body { background: #ffffff; }",
    "#" + ROOT_ID + " {",
    "  box-sizing: border-box;",
    "  max-width: 44rem;",
    "  margin: 0 auto;",
    "  padding: 20px 18px 64px;",
    "  font-family: Georgia, 'Noto Serif', serif;",
    "  font-size: 19px;",
    "  line-height: 1.62;",
    "  color: #1a1a1a;",
    "  overflow-wrap: break-word;",
    "}",
    "#" + ROOT_ID + " h1 { font-size: 30px; line-height: 1.25; margin: 0 0 8px; }",
    "#" + ROOT_ID + " .chrogecko-reader-byline { margin: 0 0 24px; font-size: 15px; color: #5f6368; }",
    "#" + ROOT_ID + " img, #" + ROOT_ID + " video, #" + ROOT_ID + " figure,",
    "#" + ROOT_ID + " table, #" + ROOT_ID + " pre { max-width: 100%; height: auto; }",
    "#" + ROOT_ID + " figure { margin: 24px 0; }",
    "#" + ROOT_ID + " figcaption { font-size: 14px; color: #5f6368; }",
    "#" + ROOT_ID + " a { color: #1a73e8; }",
    "#" + ROOT_ID + " pre { overflow-x: auto; padding: 12px; background: rgba(0,0,0,0.05); }",
    "#" + ROOT_ID + " blockquote { margin: 20px 0; padding-left: 16px; border-left: 3px solid rgba(0,0,0,0.2); }",
    "@media (prefers-color-scheme: dark) {",
    "  html, body { background: #121212; }",
    "  #" + ROOT_ID + " { color: #e3e3e3; }",
    "  #" + ROOT_ID + " .chrogecko-reader-byline, #" + ROOT_ID + " figcaption { color: #9aa0a6; }",
    "  #" + ROOT_ID + " a { color: #8ab4f8; }",
    "  #" + ROOT_ID + " pre { background: rgba(255,255,255,0.08); }",
    "  #" + ROOT_ID + " blockquote { border-left-color: rgba(255,255,255,0.24); }",
    "}",
  ].join("\n");
}

function isAvailable() {
  try {
    return typeof isProbablyReaderable === "function" && isProbablyReaderable(document);
  } catch (error) {
    return false;
  }
}

function parseArticle() {
  try {
    return new Readability(document.cloneNode(true)).parse();
  } catch (error) {
    return null;
  }
}

const SANITIZE_DROP_TAGS =
  "script, style, iframe, frame, frameset, object, embed, applet, form, input, button, select, textarea, meta, link, base, noscript, template, foreignobject, animate, set";
const SANITIZE_URI_ATTRS = new Set([
  "href",
  "src",
  "xlink:href",
  "action",
  "formaction",
  "poster",
]);

function isUnsafeUri(value) {
  const cleaned = String(value || "").replace(/[\u0000-\u0020\u007f]+/g, "").toLowerCase();
  return (
    cleaned.startsWith("javascript:") ||
    cleaned.startsWith("vbscript:") ||
    cleaned.startsWith("data:text/html") ||
    cleaned.startsWith("data:application/xhtml")
  );
}

function hasUnsafeSrcset(value) {
  const candidates = String(value || "").split(",");
  for (const rawCandidate of candidates) {
    const urlPart = rawCandidate.trim().split(/\s+/)[0];
    if (isUnsafeUri(urlPart)) return true;
  }
  return false;
}

function sanitizeHtml(rawHtml, doc) {
  const fragment = doc.createDocumentFragment();
  if (!rawHtml) return fragment;
  const parsedDoc = new DOMParser().parseFromString(String(rawHtml), "text/html");
  if (!parsedDoc || !parsedDoc.body) return fragment;
  for (const node of Array.from(parsedDoc.body.querySelectorAll(SANITIZE_DROP_TAGS))) {
    node.remove();
  }
  for (const element of Array.from(parsedDoc.body.querySelectorAll("*"))) {
    for (const attr of Array.from(element.attributes)) {
      const name = attr.name.toLowerCase();
      if (name.startsWith("on") || name === "srcdoc") {
        element.removeAttribute(attr.name);
      } else if (SANITIZE_URI_ATTRS.has(name) && isUnsafeUri(attr.value)) {
        element.removeAttribute(attr.name);
      } else if (name === "srcset" && hasUnsafeSrcset(attr.value)) {
        element.removeAttribute(attr.name);
      }
    }
  }
  for (const child of Array.from(parsedDoc.body.childNodes)) {
    fragment.appendChild(doc.importNode(child, true));
  }
  return fragment;
}

function buildRoot(parsed, doc) {
  const root = doc.createElement("div");
  root.id = ROOT_ID;

  const title = doc.createElement("h1");
  title.textContent = parsed.title || doc.title || "";
  root.appendChild(title);

  if (parsed.byline) {
    const byline = doc.createElement("p");
    byline.className = "chrogecko-reader-byline";
    byline.textContent = parsed.byline;
    root.appendChild(byline);
  }

  const content = doc.createElement("div");
  content.className = "chrogecko-reader-content";
  content.appendChild(sanitizeHtml(parsed.content || "", doc));
  root.appendChild(content);

  return root;
}

function ensureStyle(doc) {
  if (doc.getElementById(STYLE_ID)) return;
  const style = doc.createElement("style");
  style.id = STYLE_ID;
  style.textContent = readerCss();
  (doc.head || doc.documentElement).appendChild(style);
}

function render(parsed) {
  if (active || !document.body) return active;
  ensureStyle(document);
  savedNodes = Array.from(document.body.childNodes);
  savedScroll = window.scrollY || 0;
  document.body.replaceChildren(buildRoot(parsed, document));
  window.scrollTo(0, 0);
  active = true;
  return true;
}

function restore() {
  if (!document.body || !savedNodes) return false;
  document.body.replaceChildren(...savedNodes);
  const style = document.getElementById(STYLE_ID);
  if (style) style.remove();
  savedNodes = null;
  active = false;
  window.scrollTo(0, savedScroll);
  return true;
}

function currentHost() {
  return (window.location.hostname || "").toLowerCase();
}

function report(type, extra) {
  const message = {
    type: type,
    url: window.location.href,
    host: currentHost(),
    available: active ? true : isAvailable(),
    active: active,
  };
  if (extra) Object.assign(message, extra);
  try {
    browser.runtime.sendMessage(message);
  } catch (error) {
    // The background page may be restarting; the next interaction reports again.
  }
}

function toggle() {
  if (active) {
    restore();
    report("reader/state");
    return { active: active };
  }
  const parsed = parseArticle();
  if (!parsed || !parsed.content) {
    report("reader/state", { available: false });
    return { active: false };
  }
  render(parsed);
  report("reader/state");
  return { active: active };
}

browser.runtime.onMessage.addListener((message) => {
  if (!message || typeof message !== "object") return;
  if (message.type === "reader/activate") {
    if (active) return Promise.resolve({ active: true });
    return Promise.resolve(toggle());
  }
  if (message.type === "reader/toggle") {
    return Promise.resolve(toggle());
  }
});

report("reader/ready");
