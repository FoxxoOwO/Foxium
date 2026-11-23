// Verifies the text expander content script outside a browser: a fake DOM feeds the captured
// listeners the same trusted events Gecko would, so both the keydown and the IME (`input`) path
// can be asserted. Run: node tools/check-expander.mjs
import { readFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import vm from "node:vm";

const here = dirname(fileURLToPath(import.meta.url));
const source = readFileSync(
  resolve(join(here, "..", "app/src/main/assets/extensions/sys_expander/content.js")),
  "utf8",
);

const listeners = new Map();

class FakeInputEvent {
  constructor(type, init = {}) {
    Object.assign(this, { type, isTrusted: false }, init);
  }
}

function field(initial = "") {
  const element = {
    nodeType: 1,
    tagName: "TEXTAREA",
    value: initial,
    selectionStart: initial.length,
    selectionEnd: initial.length,
    isContentEditable: false,
    disabled: false,
    readOnly: false,
    events: [],
    getAttribute: () => null,
    setRangeText(text, start, end) {
      this.value = this.value.slice(0, start) + text + this.value.slice(end);
      this.selectionStart = this.selectionEnd = start + text.length;
    },
    dispatchEvent(event) {
      this.events.push(event);
      return true;
    },
  };
  return element;
}

let selectionMock = null;
let execCommandImpl = () => false;

const document = {
  addEventListener(type, handler) {
    listeners.set(type, handler);
  },
  createRange: () => ({
    startContainer: null,
    startOffset: 0,
    endOffset: 0,
    setStart(node, offset) {
      this.startContainer = node;
      this.startOffset = offset;
    },
    setEnd(_node, offset) {
      this.endOffset = offset;
    },
    deleteContents() {},
    insertNode() {},
  }),
  execCommand: (...args) => execCommandImpl(...args),
  createTextNode: () => ({ nodeType: 3 }),
};

// The content script asks for its table once at document_start, so seed it before evaluating.
const answer = {
  enabled: true,
  snippets: [
    { trigger: ";sig", expansion: "ChroGecko support" },
    { trigger: "d", expansion: "{{date}}" },
  ],
};
const context = {
  document,
  window: { getSelection: () => selectionMock },
  InputEvent: FakeInputEvent,
  Date,
  console,
  browser: {
    runtime: {
      sendMessage: () => Promise.resolve(answer),
      onMessage: { addListener() {} },
    },
  },
};
vm.createContext(context);
vm.runInContext(source, context);

await new Promise((done) => setTimeout(done, 0));

const failures = [];
function check(name, actual, expected) {
  if (actual !== expected) failures.push(`${name}: got ${JSON.stringify(actual)}, want ${JSON.stringify(expected)}`);
}

const input = listeners.get("input");
const keydown = listeners.get("keydown");
if (!input || !keydown) throw new Error("content script did not register keydown+input listeners");

// 1. IME path (Android soft keyboard): the space arrives as an input mutation, no keydown at all.
let area = field(";sig ");
input({ isTrusted: true, target: area, composedPath: () => [area] });
check("ime expansion", area.value, "ChroGecko support ");
check("ime caret", area.selectionStart, "ChroGecko support ".length);
check("ime input event", area.events[0]?.inputType, "insertReplacementText");

// 2. Keydown path: the space is consumed so it never reaches the page.
area = field(";sig");
let prevented = false;
keydown({
  isTrusted: true,
  key: " ",
  target: area,
  composedPath: () => [area],
  preventDefault: () => {
    prevented = true;
  },
});
check("keydown expansion", area.value, "ChroGecko support");
check("keydown consumed space", prevented, true);

// 3. Enter after a trigger expands and is left to the page.
area = field(";sig");
keydown({ isTrusted: true, key: "Enter", target: area, composedPath: () => [area], preventDefault: () => {} });
check("enter expansion", area.value, "ChroGecko support");

// 4. Untouched input stays untouched: unknown trigger, no trailing space, non-editable target.
area = field(";sigx ");
input({ isTrusted: true, target: area, composedPath: () => [area] });
check("unknown trigger", area.value, ";sigx ");

area = field(";sig");
input({ isTrusted: true, target: area, composedPath: () => [area] });
check("no terminator", area.value, ";sig");

area = field(";sig ");
const div = { nodeType: 1, tagName: "DIV", isContentEditable: false, getAttribute: () => null };
input({ isTrusted: true, target: div, composedPath: () => [div] });
check("non-editable", area.value, ";sig ");

// 5. Synthetic events are ignored, so the replacement cannot re-enter and loop.
area = field(";sig ");
input({ isTrusted: false, target: area, composedPath: () => [area] });
check("untrusted ignored", area.value, ";sig ");

// 6. Placeholders are resolved at expansion time.
area = field("d ");
input({ isTrusted: true, target: area, composedPath: () => [area] });
check("placeholder", /^\d{4}-\d{2}-\d{2} $/.test(area.value), true);

// 7. Android IME input path on contentEditable invokes expandEditable.
const textNode = { nodeType: 3, nodeValue: ";sig " };
const ceDiv = { nodeType: 1, tagName: "DIV", isContentEditable: true, getAttribute: () => null };
let activeRange = null;
selectionMock = {
  rangeCount: 1,
  getRangeAt: () => ({
    collapsed: true,
    startContainer: textNode,
    startOffset: textNode.nodeValue.length,
  }),
  removeAllRanges() {
    activeRange = null;
  },
  addRange(r) {
    activeRange = r;
  },
};
execCommandImpl = (cmd, _ui, text) => {
  if (cmd !== "insertText" || !activeRange) return false;
  const v = textNode.nodeValue;
  textNode.nodeValue = v.slice(0, activeRange.startOffset) + text + v.slice(activeRange.endOffset);
  return true;
};
input({ isTrusted: true, target: ceDiv, composedPath: () => [ceDiv] });
selectionMock = null;
execCommandImpl = () => false;
check("contenteditable ime expansion", textNode.nodeValue, "ChroGecko support ");

if (failures.length) {
  console.error("expander checks FAILED\n" + failures.join("\n"));
  process.exit(1);
}
console.log("expander checks passed");
