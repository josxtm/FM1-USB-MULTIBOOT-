/* SPDX-License-Identifier: MIT */
"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const core = require("../simulator/core.js");

test("starts READY with a nonempty demo catalog", () => {
  const state = core.createState();
  assert.equal(state.mode, "READY");
  assert.equal(state.entries.length, 4);
  assert.equal(state.selected, 0);
});

test("NEXT and PREV move without modifying original state", () => {
  const before = core.createState();
  const next = core.dispatch(before, "NEXT");
  assert.equal(before.selected, 0);
  assert.equal(next.selected, 1);
  assert.equal(core.dispatch(next, "PREV").selected, 0);
});

test("navigation wraps in both directions", () => {
  const init = core.createState();
  assert.equal(core.dispatch(init, "PREV").selected, init.entries.length - 1);
  const last = { ...init, selected: init.entries.length - 1 };
  assert.equal(core.dispatch(last, "NEXT").selected, 0);
});

test("SELECT is always blocked, never a launch request", () => {
  const state = core.dispatch(core.createState(), "SELECT");
  assert.equal(state.mode, "LAUNCH_BLOCKED");
  assert.match(state.message, /SIMULADOR/);
  assert.equal(Object.hasOwn(state, "flashWrite"), false);
  assert.equal(Object.hasOwn(state, "launch"), false);
});

test("REC returns from blocked to READY", () => {
  const state = core.dispatch(core.dispatch(core.createState(), "SELECT"), "BACK");
  assert.equal(state.mode, "READY");
  assert.equal(state.message, "");
});

test("REC from READY enters non-destructive recovery placeholder", () => {
  const state = core.dispatch(core.createState(), "BACK");
  assert.equal(state.mode, "RECOVERY");
  assert.match(state.message, /SOLO MAQUETA/);
  assert.equal(core.dispatch(state, "BACK").mode, "READY");
});

test("unplugging SD disables browsing, preserving selection", () => {
  const state = core.dispatch(core.dispatch(core.createState(), "NEXT"),
    { type: "SET_CARD", present: false });
  assert.equal(state.mode, "NO_CARD");
  assert.equal(core.dispatch(state, "NEXT").selected, 1);
  assert.equal(core.dispatch(state, "SELECT").mode, "NO_CARD");
});

test("replugging SD resumes without automatic launch", () => {
  let state = core.dispatch(core.createState(), { type: "SET_CARD", present: false });
  state = core.dispatch(state, { type: "SET_CARD", present: true });
  assert.equal(state.mode, "READY");
  assert.equal(state.selected, 0);
});

test("empty catalog and missing card have safe distinct states", () => {
  assert.equal(core.createState([], true).mode, "EMPTY");
  assert.equal(core.createState([], false).mode, "NO_CARD");
  const empty = core.dispatch(core.createState([], true), "SELECT");
  assert.equal(empty.mode, "EMPTY");
});

test("relative cover positions are bounded and wrap consistently", () => {
  assert.equal(core.relativePosition(3, 0, 4), -1);
  assert.equal(core.relativePosition(0, 3, 4), 1);
  assert.equal(core.wrap(-1, 4), 3);
  assert.equal(core.relativePosition(0, 0, 0), 0);
});
