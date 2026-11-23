"use strict";

/*
 * Expands a snippet when the text right before the caret equals its trigger and the user types a
 * terminator (space, Enter or Tab). The snippet table is owned by Kotlin and mirrored through the
 * background page, so nothing is stored in the page.
 */

const TERMINATORS = { " ": true, Enter: true, Tab: true };
const PLACEHOLDER = /\{\{(date|time|datetime)\}\}/g;

let config = { enabled: true, snippets: [] };
let byTrigger = new Map();

function setConfig(next) {
  config = {
    enabled: !next || next.enabled !== false,
    snippets: next && Array.isArray(next.snippets) ? next.snippets : [],
  };
  byTrigger = new Map();
  for (const snippet of config.snippets) {
    if (snippet && typeof snippet.trigger === "string" && typeof snippet.expansion === "string") {
      byTrigger.set(snippet.trigger, snippet.expansion);
    }
  }
}

function two(value) {
  return value < 10 ? "0" + value : String(value);
}

function expandPlaceholders(text) {
  if (text.indexOf("{{") < 0) return text;
  const now = new Date();
  const date = now.getFullYear() + "-" + two(now.getMonth() + 1) + "-" + two(now.getDate());
  const time = two(now.getHours()) + ":" + two(now.getMinutes());
  return text.replace(PLACEHOLDER, (match, name) => {
    if (name === "date") return date;
    if (name === "time") return time;
    return date + " " + time;
  });
}

function isEditable(node) {
  if (!node || node.nodeType !== 1) return null;
  if (node.isContentEditable) return node;
  const tag = node.tagName;
  if (tag === "TEXTAREA") return node;
  if (tag !== "INPUT") return null;
  const type = (node.getAttribute("type") || "text").toLowerCase();
  const allowed = ["text", "search", "url", "email", "tel", ""];
  if (allowed.indexOf(type) < 0) return null;
  if (node.disabled || node.readOnly) return null;
  return node;
}

function editableFor(event) {
  const path = typeof event.composedPath === "function" ? event.composedPath() : [];
  for (const node of path) {
    const editable = isEditable(node);
    if (editable) return editable;
  }
  return isEditable(event.target);
}

/** Text before the caret inside [element], or null when the caret is not in a plain text node. */
function textBeforeCaret(element) {
  if (element.isContentEditable) {
    const selection = window.getSelection();
    if (!selection || selection.rangeCount === 0) return null;
    const range = selection.getRangeAt(0);
    if (!range.collapsed) return null;
    const node = range.startContainer;
    if (node.nodeType !== 3) return null;
    const value = node.nodeValue || "";
    return { text: value.slice(0, range.startOffset), caret: range.startOffset, node: node };
  }
  if (typeof element.selectionStart !== "number") return null;
  if (element.selectionStart !== element.selectionEnd) return null;
  const value = element.value || "";
  return {
    text: value.slice(0, element.selectionStart),
    caret: element.selectionStart,
    node: null,
  };
}

function trailingToken(text) {
  const match = /(\S+)$/.exec(text);
  return match ? match[1] : "";
}

function expandInput(element, caret, token, replacement) {
  const start = caret - token.length;
  if (start < 0) return false;
  if (typeof element.setRangeText === "function") {
    element.setRangeText(replacement, start, caret, "end");
  } else {
    const value = element.value || "";
    element.value = value.slice(0, start) + replacement + value.slice(caret);
    element.selectionStart = element.selectionEnd = start + replacement.length;
  }
  element.dispatchEvent(new InputEvent("input", {
    bubbles: true,
    cancelable: false,
    inputType: "insertReplacementText",
    data: replacement,
  }));
  return true;
}

function expandEditable(node, caret, token, replacement) {
  const range = document.createRange();
  const start = caret - token.length;
  if (start < 0) return false;
  range.setStart(node, start);
  range.setEnd(node, caret);
  const selection = window.getSelection();
  if (!selection) return false;
  selection.removeAllRanges();
  selection.addRange(range);
  // insertText keeps the browser's own undo stack and fires `input` for framework bindings.
  const inserted = document.execCommand("insertText", false, replacement);
  if (!inserted) {
    range.deleteContents();
    range.insertNode(document.createTextNode(replacement));
    selection.collapseToEnd();
    node.parentNode && node.parentNode.normalize();
  }
  return true;
}

/** Runs [replacement] over the [token] that ends at [before].caret. */
function applyExpansion(element, before, token, replacement) {
  return before.node
    ? expandEditable(before.node, before.caret, token, replacement)
    : expandInput(element, before.caret, token, replacement);
}

function onKeyDown(event) {
  if (!config.enabled || byTrigger.size === 0) return;
  if (event.isComposing || event.defaultPrevented) return;
  if (event.ctrlKey || event.metaKey || event.altKey) return;
  if (!TERMINATORS[event.key]) return;

  const element = editableFor(event);
  if (!element) return;

  const before = textBeforeCaret(element);
  if (!before) return;

  const token = trailingToken(before.text);
  if (!token) return;

  const snippet = byTrigger.get(token);
  if (!snippet) return;

  if (!applyExpansion(element, before, token, expandPlaceholders(snippet))) return;

  // The terminator follows the expansion: a typed space is consumed, Enter/Tab still reach the page
  // so chat boxes and forms behave as before.
  if (event.key === " ") event.preventDefault();
}

/*
 * Android soft keyboards usually commit text instead of sending key events, so the space that ends
 * a trigger arrives as an `input` mutation with no `keydown` at all. Expanding from the mutation
 * covers both paths; synthetic events are ignored so the replacement cannot re-enter.
 */
function onInput(event) {
  if (!event.isTrusted || !config.enabled || byTrigger.size === 0) return;

  const element = editableFor(event);
  if (!element) return;

  const before = textBeforeCaret(element);
  if (!before || !before.text.endsWith(" ")) return;

  const token = trailingToken(before.text.slice(0, -1));
  if (!token) return;

  const snippet = byTrigger.get(token);
  if (!snippet) return;

  // The typed space is part of the replaced range, so the replacement carries its own separator
  // and the page never sees a doubled gap.
  applyExpansion(element, before, token + " ", expandPlaceholders(snippet) + " ");
}

document.addEventListener("keydown", onKeyDown, true);
document.addEventListener("input", onInput, true);

browser.runtime.onMessage.addListener((message) => {
  if (message && message.type === "expander/apply") setConfig(message.config);
});

browser.runtime.sendMessage({ type: "expander/config" }).then((value) => {
  setConfig(value);
}).catch(() => undefined);
