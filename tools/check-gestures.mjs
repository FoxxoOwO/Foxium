import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import vm from "node:vm";

const GESTURES_JS = readFileSync(
  new URL("../app/src/main/assets/extensions/sys_gestures/content.js", import.meta.url),
  "utf8",
);

function runHarness() {
  const windowListeners = new Map();
  const documentListeners = new Map();
  const messages = [];
  let nextTimerId = 1;
  const timers = new Map();
  let nowMs = 10000;
  let selectionCollapsed = true;
  let selectionClearedCount = 0;

  class MockDate extends Date {
    static now() {
      return nowMs;
    }
  }

  const mockSelection = {
    get isCollapsed() {
      return selectionCollapsed;
    },
    removeAllRanges() {
      selectionClearedCount += 1;
      selectionCollapsed = true;
    },
  };

  function addListener(map, type, fn) {
    if (!map.has(type)) map.set(type, []);
    map.get(type).push(fn);
  }

  function dispatch(map, type, event = {}) {
    for (const fn of map.get(type) || []) {
      fn(event);
    }
  }

  const sandbox = {
    window: {
      innerWidth: 400,
      scrollY: 0,
      getSelection() {
        return mockSelection;
      },
    },
    document: {
      documentElement: { clientWidth: 400, scrollTop: 0 },
      body: {},
      addEventListener(type, fn) {
        addListener(documentListeners, type, fn);
      },
    },
    location: {
      href: "https://example.com/",
      reload() {},
    },
    browser: {
      runtime: {
        sendMessage(msg) {
          messages.push(msg);
          return Promise.resolve();
        },
      },
    },
    addEventListener(type, fn) {
      addListener(windowListeners, type, fn);
    },
    setTimeout(fn, delay) {
      const id = nextTimerId++;
      timers.set(id, { fn, delay });
      return id;
    },
    clearTimeout(id) {
      timers.delete(id);
    },
    Date: MockDate,
    Math,
    URL,
    Promise,
  };
  sandbox.globalThis = sandbox;

  vm.runInNewContext(GESTURES_JS, sandbox);

  const linkNode = {
    nodeType: 1,
    textContent: "Example link",
    closest(selector) {
      if (selector === "a[href]") {
        return {
          textContent: "Example link",
          ownerDocument: { baseURI: "https://example.com/" },
          getAttribute(name) {
            return name === "href" ? "https://example.com/target" : null;
          },
        };
      }
      return null;
    },
  };

  function flushTimers() {
    for (const [id, entry] of Array.from(timers.entries())) {
      timers.delete(id);
      entry.fn();
    }
  }

  function touchEvent(x, y = 200) {
    return {
      touches: [{ clientX: x, clientY: y }],
      changedTouches: [{ clientX: x, clientY: y }],
      target: linkNode,
    };
  }

  // 1. Left-edge long press (< 32px) must not open context menu.
  messages.length = 0;
  dispatch(windowListeners, "touchstart", touchEvent(10));
  flushTimers();
  assert.equal(messages.filter((m) => m.type === "contextmenu/request").length, 0);

  // 2. Right-edge long press (> 400 - 32px) must not open context menu.
  messages.length = 0;
  dispatch(windowListeners, "touchstart", touchEvent(390));
  flushTimers();
  assert.equal(messages.filter((m) => m.type === "contextmenu/request").length, 0);

  // 3. Center long press on a link opens context menu after timer fires.
  messages.length = 0;
  dispatch(windowListeners, "touchstart", touchEvent(200));
  flushTimers();
  const contextMessages = messages.filter((m) => m.type === "contextmenu/request");
  assert.equal(contextMessages.length, 1);
  assert.equal(contextMessages[0].link, "https://example.com/target");

  // 4. Center long press cancelled by pointercancel / scroll / blur / visibilitychange must not open context menu.
  for (const cancelType of ["pointercancel", "scroll", "blur"]) {
    messages.length = 0;
    dispatch(windowListeners, "touchstart", touchEvent(200));
    dispatch(windowListeners, cancelType, {});
    flushTimers();
    assert.equal(messages.filter((m) => m.type === "contextmenu/request").length, 0);
  }
  messages.length = 0;
  dispatch(windowListeners, "touchstart", touchEvent(200));
  dispatch(documentListeners, "visibilitychange", {});
  flushTimers();
  assert.equal(messages.filter((m) => m.type === "contextmenu/request").length, 0);

  function selectStartEvent() {
    const event = {
      defaultPrevented: false,
      preventDefault() {
        event.defaultPrevented = true;
      },
    };
    return event;
  }

  // 5. Left-edge touch suppresses selectstart and clears accidental selection, including brief cooldown after touchcancel.
  selectionClearedCount = 0;
  selectionCollapsed = false;
  dispatch(windowListeners, "touchstart", touchEvent(10));
  const leftSelect = selectStartEvent();
  dispatch(documentListeners, "selectstart", leftSelect);
  assert.equal(leftSelect.defaultPrevented, true);
  assert.equal(selectionCollapsed, true);
  assert.equal(selectionClearedCount, 1);

  selectionCollapsed = false;
  dispatch(documentListeners, "selectionchange", {});
  assert.equal(selectionCollapsed, true);
  assert.equal(selectionClearedCount, 2);

  selectionCollapsed = false;
  dispatch(windowListeners, "touchcancel", touchEvent(10));
  assert.equal(selectionCollapsed, true);
  assert.equal(selectionClearedCount, 3);

  nowMs += 200;
  const postCancelSelect = selectStartEvent();
  dispatch(documentListeners, "selectstart", postCancelSelect);
  assert.equal(postCancelSelect.defaultPrevented, true);

  // After cooldown expires, selectstart is allowed again.
  nowMs += 500;
  const expiredSelect = selectStartEvent();
  dispatch(documentListeners, "selectstart", expiredSelect);
  assert.equal(expiredSelect.defaultPrevented, false);

  // 6. Right-edge touch suppresses selectstart and clears accidental selection.
  selectionClearedCount = 0;
  selectionCollapsed = false;
  dispatch(windowListeners, "touchstart", touchEvent(390));
  const rightSelect = selectStartEvent();
  dispatch(documentListeners, "selectstart", rightSelect);
  assert.equal(rightSelect.defaultPrevented, true);
  assert.equal(selectionCollapsed, true);
  assert.equal(selectionClearedCount, 1);
  dispatch(windowListeners, "touchend", touchEvent(390));

  // 7. Center-screen touch immediately clears an active edge cooldown so normal text selection works.
  selectionClearedCount = 0;
  selectionCollapsed = false;
  nowMs += 100;
  dispatch(windowListeners, "touchstart", touchEvent(200));
  const centerSelect = selectStartEvent();
  dispatch(documentListeners, "selectstart", centerSelect);
  dispatch(documentListeners, "selectionchange", {});
  assert.equal(centerSelect.defaultPrevented, false);
  assert.equal(selectionCollapsed, false);
  assert.equal(selectionClearedCount, 0);

  // 8. AMO's "Add to Firefox" must be consumed so its own .xpi download never starts.
  sandbox.location.href = "https://addons.mozilla.org/en-US/firefox/addon/example/";
  const amoNode = {
    textContent: "Add to Firefox",
    getAttribute: () => null,
    parentElement: null,
  };
  const clickEvent = {
    target: amoNode,
    isTrusted: true,
    button: 0,
    defaultPrevented: false,
    preventDefault() {
      this.defaultPrevented = true;
    },
    stopPropagation() {
      this.stopped = true;
    },
  };
  messages.length = 0;
  dispatch(windowListeners, "click", clickEvent);
  assert.equal(messages.filter((m) => m.type === "amo/install").length, 1);
  assert.equal(clickEvent.defaultPrevented, true);
  assert.equal(clickEvent.stopped, true);
}

runHarness();
console.log("check-gestures: ok");
