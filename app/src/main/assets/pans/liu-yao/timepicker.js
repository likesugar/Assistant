/* 江氏小六壬 · 官方同款 24 小时制时钟时间选择器
 * 点击起课页「时间」字段弹出: 青绿头部 07:46 + 表盘(外圈 12/1..11, 内圈 00/13..23) + 取消/确定,
 * 左下角图标可切换键盘输入。确定后回填 HH:MM 并触发 onChange(刷新农历行)。
 * 与页面返回键协同: 打开时右滑先关闭本选择器(见 app.js __jlBack)。
 */
(function () {
  'use strict';
  var OUTER_H = ['12', '1', '2', '3', '4', '5', '6', '7', '8', '9', '10', '11'];
  var INNER_H = ['00', '13', '14', '15', '16', '17', '18', '19', '20', '21', '22', '23'];
  var MIN_LABEL = ['00', '05', '10', '15', '20', '25', '30', '35', '40', '45', '50', '55'];
  var R_OUT = 38, R_IN = 26;   // 相对表盘半宽的百分比(50% = 半径)
  var KB_ICON = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M20 5H4c-1.1 0-1.99.9-1.99 2L2 17c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm-9 3h2v2h-2V8zm0 3h2v2h-2v-2zM8 8h2v2H8V8zm0 3h2v2H8v-2zm-1 2H5v-2h2v2zm0-3H5V8h2v2zm9 7H8v-2h8v2zm0-4h-2v-2h2v2zm0-3h-2V8h2v2zm3 3h-2v-2h2v2zm0-3h-2V8h2v2z"/></svg>';
  var CLOCK_ICON = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zM12 20c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8zm.5-13H11v6l5.25 3.15.75-1.23-4.5-2.67z"/></svg>';

  var h = 7, m = 46, part = 'hour', mode = 'clock', onChange = null, bound = false;
  function $(id) { return document.getElementById(id); }
  function pad(n) { return (n < 10 ? '0' : '') + n; }
  function isOpen() { var e = $('timePickerMask'); return !!(e && e.classList.contains('open')); }
  // 关闭时复位为时钟模式, 避免下次打开时把上次键盘输入的旧值覆盖到新解析出的时间上
  function close() { var e = $('timePickerMask'); if (e) e.classList.remove('open'); mode = 'clock'; }

  function addNum(text, i, r, inner) {
    var a = (i * 30 - 90) * Math.PI / 180;
    var n = document.createElement('div');
    n.className = 'tp-num' + (inner ? ' inner' : '');
    n.textContent = text;
    n.style.left = (50 + r * Math.cos(a)).toFixed(2) + '%';
    n.style.top = (50 + r * Math.sin(a)).toFixed(2) + '%';
    $('tpDial').appendChild(n);
  }
  function drawNumbers() {
    var dial = $('tpDial');
    if (!dial) return;
    var old = dial.querySelectorAll('.tp-num');
    for (var k = 0; k < old.length; k++) { if (old[k].parentNode) old[k].parentNode.removeChild(old[k]); }
    var outer = part === 'hour' ? OUTER_H : MIN_LABEL;
    outer.forEach(function (t, i) { addNum(t, i, R_OUT, false); });
    if (part === 'hour') INNER_H.forEach(function (t, i) { addNum(t, i, R_IN, true); });
  }
  function renderHead() {
    var hh = $('tpHeadH'), mm = $('tpHeadM');
    if (hh) { hh.textContent = pad(h); hh.classList.toggle('dim', part !== 'hour'); }
    if (mm) { mm.textContent = pad(m); mm.classList.toggle('dim', part !== 'minute'); }
  }
  function renderHand() {
    var a, r, label;
    if (part === 'hour') {
      var i = h % 12, inner = (h === 0 || h >= 13);
      a = (i * 30 - 90) * Math.PI / 180;
      r = inner ? R_IN : R_OUT;
      label = inner ? (i === 0 ? '00' : pad(h)) : (i === 0 ? '12' : String(h));
    } else {
      a = (m * 6 - 90) * Math.PI / 180;
      r = R_OUT;
      label = pad(m);
    }
    var line = $('tpLine'), sel = $('tpSel');
    if (line) {
      var len = r * 2.4; // 表盘 240px / viewBox 240: 1% = 2.4px
      line.setAttribute('x2', (120 + len * Math.cos(a)).toFixed(1));
      line.setAttribute('y2', (120 + len * Math.sin(a)).toFixed(1));
    }
    if (sel) {
      sel.style.left = (50 + r * Math.cos(a)).toFixed(2) + '%';
      sel.style.top = (50 + r * Math.sin(a)).toFixed(2) + '%';
      sel.textContent = label;
    }
    dimNearLabels();
  }
  // 分钟不是 5 的倍数时, 选中圆会压住相邻标签; 淡化它们避免三个数字挤在一起
  function dimNearLabels() {
    var sel = $('tpSel'), dial = $('tpDial');
    if (!sel || !dial) return;
    var sx = parseFloat(sel.style.left), sy = parseFloat(sel.style.top);
    var list = dial.querySelectorAll('.tp-num');
    for (var i = 0; i < list.length; i++) {
      var dx = parseFloat(list[i].style.left) - sx;
      var dy = parseFloat(list[i].style.top) - sy;
      list[i].classList.toggle('near', Math.sqrt(dx * dx + dy * dy) < 12);
    }
  }
  function setPart(p) { part = p; drawNumbers(); renderHand(); renderHead(); }
  function readText() {
    var a = parseInt($('tpH').value, 10), b = parseInt($('tpM').value, 10);
    if (!isNaN(a)) h = Math.max(0, Math.min(23, a));
    if (!isNaN(b)) m = Math.max(0, Math.min(59, b));
    $('tpH').value = pad(h); $('tpM').value = pad(m);
    renderHead();
  }
  function setMode(md) {
    var prev = mode;
    mode = md;
    var clock = md === 'clock';
    var dial = $('tpDial'), text = $('tpText'), kb = $('tpKb');
    if (dial) dial.classList.toggle('hidden', !clock);
    if (text) text.classList.toggle('hidden', clock);
    if (kb) {
      kb.innerHTML = clock ? KB_ICON : CLOCK_ICON;
      kb.setAttribute('aria-label', clock ? '切换为键盘输入' : '切换为时钟选择');
    }
    if (!clock) { $('tpH').value = pad(h); $('tpM').value = pad(m); }
    else { if (prev === 'text') readText(); setPart(part === 'minute' ? 'minute' : 'hour'); }
  }
  // 坐标 -> 选中值(只更新数值与指针, 不切视图), 轻点与拖动共用。
  // 真机 WebView 上 touch-action:none 会让带位移的手势不派发 click, 必须自行处理按下/移动/抬起序列。
  function pick(clientX, clientY) {
    var dial = $('tpDial');
    if (!dial || !dial.getBoundingClientRect) return;
    var rect = dial.getBoundingClientRect();
    var x = clientX - rect.left - rect.width / 2;
    var y = clientY - rect.top - rect.height / 2;
    var deg = (Math.atan2(y, x) * 180 / Math.PI + 90 + 360) % 360;
    if (part === 'hour') {
      var i = Math.round(deg / 30) % 12;
      var inner = Math.sqrt(x * x + y * y) < rect.width * 0.32;
      h = inner ? (i === 0 ? 0 : i + 12) : (i === 0 ? 12 : i);
    } else {
      m = Math.round(deg / 6) % 60;
    }
    renderHead(); renderHand();
  }
  function finishPick() {
    if (part === 'hour') setPart('minute');  // 官方: 选完小时自动切分钟表盘
  }
  function pointOf(e) {
    var t = (e.touches && e.touches[0]) || (e.changedTouches && e.changedTouches[0]) || e;
    return (t && t.clientX !== undefined) ? { x: t.clientX, y: t.clientY } : null;
  }
  var dragging = false, pointerHandled = false;
  function down(e) {
    var p = pointOf(e); if (!p) return;
    dragging = true; pointerHandled = true;
    if (e.pointerId !== undefined && $('tpDial').setPointerCapture) {
      try { $('tpDial').setPointerCapture(e.pointerId); } catch (_) {}
    }
    pick(p.x, p.y);
  }
  function move(e) {
    if (!dragging) return;
    var p = pointOf(e); if (!p) return;
    pick(p.x, p.y);
    if (e.cancelable) e.preventDefault();
  }
  function up() {
    if (!dragging) return;
    dragging = false;
    finishPick();
    // 手势后浏览器可能合成 click(需抑制), 也可能因拖动 preventDefault 而不合成;
    // 延时复位避免 pointerHandled 残留导致后续纯 click 被误吃。
    setTimeout(function () { pointerHandled = false; }, 400);
  }
  function onDialTap(e) {
    // 已有按下/抬起序列完成选择时, 抑制随后合成的 click, 避免它在分钟盘上覆盖分钟值
    if (pointerHandled) { pointerHandled = false; return; }
    var p = pointOf(e); if (!p) return;
    pick(p.x, p.y); finishPick();
  }
  function confirm() {
    if (mode === 'text') readText();
    var v = pad(h) + ':' + pad(m);
    close();
    if (onChange) onChange(v);
  }
  function open(current, cb) {
    var t = /^(\d{1,2}):(\d{1,2})$/.exec(String(current || '').trim());
    var now = new Date();
    h = t ? Math.max(0, Math.min(23, +t[1])) : now.getHours();
    m = t ? Math.max(0, Math.min(59, +t[2])) : now.getMinutes();
    onChange = cb || null;
    part = 'hour';
    setMode('clock');
    var mask = $('timePickerMask');
    if (mask) mask.classList.add('open');
  }
  function bind() {
    if (bound || !$('timePickerMask')) return;
    bound = true;
    $('tpCancel').addEventListener('click', close);
    $('tpOk').addEventListener('click', confirm);
    $('tpKb').addEventListener('click', function () { setMode(mode === 'clock' ? 'text' : 'clock'); });
    $('tpHeadH').addEventListener('click', function () { if (mode === 'clock') setPart('hour'); });
    $('tpHeadM').addEventListener('click', function () { if (mode === 'clock') setPart('minute'); });
    // 表盘: 按下/拖动/抬起实时跟随(PointerEvent 优先, 老 WebView 降级 touch);
    // click 仅作无坐标环境兜底, 由 pointerHandled 抑制真机手势后合成的 click。
    var dial = $('tpDial');
    if (window.PointerEvent) {
      dial.addEventListener('pointerdown', down);
      dial.addEventListener('pointermove', move);
      dial.addEventListener('pointerup', up);
      dial.addEventListener('pointercancel', up);
    } else {
      dial.addEventListener('touchstart', down, { passive: false });
      dial.addEventListener('touchmove', move, { passive: false });
      dial.addEventListener('touchend', up);
      dial.addEventListener('touchcancel', up);
      document.addEventListener('touchend', up);
      document.addEventListener('touchcancel', up);
    }
    dial.addEventListener('click', onDialTap);
    $('timePickerMask').addEventListener('click', function (e) { if (e.target === $('timePickerMask')) close(); });
    setMode('clock');
  }
  window.TimePicker = { open: open, close: close, isOpen: isOpen };
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', bind);
  else bind();
})();
