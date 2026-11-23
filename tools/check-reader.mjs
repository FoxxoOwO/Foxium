import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import vm from "node:vm";

const sentMessages = [];

class StubNode {
  constructor(nodeType, tagName = null, text = "") {
    this.nodeType = nodeType;
    this.tagName = tagName ? tagName.toLowerCase() : null;
    this.text = text;
    this.parentNode = null;
    this.childNodes = [];
    this.attrs = new Map();
  }

  get attributes() {
    return Array.from(this.attrs.entries()).map(([name, value]) => ({ name, value }));
  }

  setAttribute(name, value) {
    this.attrs.set(name.toLowerCase(), value);
  }

  getAttribute(name) {
    return this.attrs.has(name.toLowerCase()) ? this.attrs.get(name.toLowerCase()) : null;
  }

  hasAttribute(name) {
    return this.attrs.has(name.toLowerCase());
  }

  removeAttribute(name) {
    this.attrs.delete(name.toLowerCase());
  }

  appendChild(child) {
    child.parentNode = this;
    this.childNodes.push(child);
    return child;
  }

  remove() {
    if (!this.parentNode) return;
    const index = this.parentNode.childNodes.indexOf(this);
    if (index >= 0) this.parentNode.childNodes.splice(index, 1);
    this.parentNode = null;
  }

  cloneNode(deep = false) {
    const copy = new StubNode(this.nodeType, this.tagName, this.text);
    for (const [k, v] of this.attrs.entries()) copy.attrs.set(k, v);
    if (deep) {
      for (const child of this.childNodes) copy.appendChild(child.cloneNode(true));
    }
    return copy;
  }

  querySelectorAll(selector) {
    const wanted = new Set(
      String(selector)
        .split(",")
        .map((s) => s.trim().toLowerCase())
        .filter(Boolean),
    );
    const matchAll = wanted.has("*");
    const matches = [];
    const walk = (node) => {
      for (const child of node.childNodes) {
        if (child.nodeType === 1) {
          if (matchAll || wanted.has(child.tagName)) matches.push(child);
          walk(child);
        }
      }
    };
    walk(this);
    return matches;
  }
}

function parseAttributes(raw) {
  const attrs = new Map();
  const attrRe = /([a-zA-Z_:][a-zA-Z0-9_.:-]*)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+)))?/g;
  let match;
  while ((match = attrRe.exec(raw)) !== null) {
    const value = match[2] ?? match[3] ?? match[4] ?? "";
    attrs.set(match[1].toLowerCase(), value);
  }
  return attrs;
}

const VOID_TAGS = new Set(["img", "br", "hr", "input", "meta", "link", "base", "source"]);

function parseHtmlToBody(html) {
  const body = new StubNode(1, "body");
  const stack = [body];
  const tokenRe = /<(\/?)([a-zA-Z][a-zA-Z0-9-]*)((?:[^>"']|"[^"]*"|'[^']*')*)>|([^<]+)/g;
  let match;
  while ((match = tokenRe.exec(html)) !== null) {
    if (match[4] !== undefined) {
      stack[stack.length - 1].appendChild(new StubNode(3, null, match[4]));
      continue;
    }
    const isClose = match[1] === "/";
    const tag = match[2].toLowerCase();
    if (isClose) {
      for (let i = stack.length - 1; i > 0; i -= 1) {
        if (stack[i].tagName === tag) {
          stack.length = i;
          break;
        }
      }
      continue;
    }
    const element = new StubNode(1, tag);
    for (const [k, v] of parseAttributes(match[3])) element.setAttribute(k, v);
    stack[stack.length - 1].appendChild(element);
    if (!VOID_TAGS.has(tag) && !match[3].trim().endsWith("/")) {
      stack.push(element);
    }
  }
  return body;
}

const doc = {
  title: "Reader Test",
  body: new StubNode(1, "body"),
  head: new StubNode(1, "head"),
  documentElement: new StubNode(1, "html"),
  createDocumentFragment: () => new StubNode(11, "#fragment"),
  createElement: (tag) => new StubNode(1, tag),
  importNode: (node, deep) => node.cloneNode(Boolean(deep)),
  getElementById: () => null,
};

const context = vm.createContext({
  document: doc,
  window: {
    location: { href: "https://example.com/article", hostname: "example.com" },
    scrollY: 0,
    scrollTo: () => {},
  },
  isProbablyReaderable: () => true,
  DOMParser: class {
    parseFromString(html) {
      return { body: parseHtmlToBody(html) };
    }
  },
  browser: {
    runtime: {
      sendMessage: (msg) => sentMessages.push(msg),
      onMessage: { addListener: () => {} },
    },
  },
  console,
  Array,
  Set,
  String,
});

const source = readFileSync(
  new URL("../app/src/main/assets/extensions/sys_reader/content.js", import.meta.url),
  "utf8",
);
vm.runInContext(source, context, { filename: "sys_reader/content.js" });

// isUnsafeUri rejects executable / XSS URI schemes including whitespace/mixed-case evasion.
assert.equal(context.isUnsafeUri("javascript:alert(1)"), true);
assert.equal(context.isUnsafeUri("  JaVa\tScRiPt:alert(1)"), true);
assert.equal(context.isUnsafeUri("vbscript:msgbox(1)"), true);
assert.equal(context.isUnsafeUri("data:text/html,<script>alert(1)</script>"), true);
assert.equal(context.isUnsafeUri("data:application/xhtml+xml,x"), true);

assert.equal(context.isUnsafeUri("https://example.com/a"), false);
assert.equal(context.isUnsafeUri("/relative/path"), false);
assert.equal(context.isUnsafeUri("data:image/png;base64,AAAA"), false);

// sanitizeHtml strips active/form tags, event handlers, srcdoc, and unsafe URIs while keeping safe content.
const dirtyHtml = [
  "<p onclick=\"evil()\">Hello <strong>world</strong></p>",
  "<script>alert(1)</script>",
  "<iframe src=\"https://evil.test\" srcdoc=\"<p>x</p>\"></iframe>",
  "<form action=\"https://evil.test\"><input type=\"text\"></form>",
  "<style>body{display:none}</style>",
  "<a href=\"javascript:alert(1)\" onmouseover=\"evil()\">BadLink</a>",
  "<a href=\"https://example.com/post\">GoodLink</a>",
  "<img src=\"data:text/html,<svg onload=1>\" onload=\"evil()\">",
  "<img src=\"https://example.com/photo.png\">",
].join("");

const sanitized = context.sanitizeHtml(dirtyHtml, doc);
assert.equal(sanitized.querySelectorAll("script").length, 0);
assert.equal(sanitized.querySelectorAll("iframe").length, 0);
assert.equal(sanitized.querySelectorAll("form").length, 0);
assert.equal(sanitized.querySelectorAll("input").length, 0);
assert.equal(sanitized.querySelectorAll("style").length, 0);

const paragraphs = sanitized.querySelectorAll("p");
assert.equal(paragraphs.length, 1);
assert.equal(paragraphs[0].hasAttribute("onclick"), false);

const links = sanitized.querySelectorAll("a");
assert.equal(links.length, 2);
assert.equal(links[0].hasAttribute("href"), false);
assert.equal(links[0].hasAttribute("onmouseover"), false);
assert.equal(links[1].getAttribute("href"), "https://example.com/post");

const images = sanitized.querySelectorAll("img");
assert.equal(images.length, 2);
assert.equal(images[0].hasAttribute("src"), false);
assert.equal(images[0].hasAttribute("onload"), false);
assert.equal(images[1].getAttribute("src"), "https://example.com/photo.png");

assert.equal(sentMessages.length, 1);
assert.equal(sentMessages[0].type, "reader/ready");

console.log("reader checks passed");
