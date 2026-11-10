/* 江氏小六壬 · UI 逻辑
 * 起课页按官方三图 1:1: 日时/时刻/数字三个独立表单, 字段各不相同, 农历实时自动显示。
 * 不含顶部网址与底部传承说明。
 */
(function () {
  'use strict';
  var E = window.JiangEngine, D = window.JiangDetail;
  var DISPLAY = [1, 2, 3, 0, 5, 4]; // 显示位置 -> 排盘索引 (留恋/速喜/赤口/大安/空亡/小吉)
  var cur = null, method = '日时起课';

  function $(id) { return document.getElementById(id); }
  function esc(s) {
    return String(s == null ? '' : s).replace(/[&<>"]/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c];
    });
  }
  function show(v) {
    $('viewHome').classList.toggle('hidden', v !== 'home');
    $('viewCourse').classList.toggle('hidden', v !== 'course');
    $('viewResult').classList.toggle('hidden', v !== 'result');
    window.scrollTo(0, 0);
    // 上报视图层级给原生(0首页/1起课页/2结果页), 供系统返回键逐层回退(单页H5无浏览历史)
    var depth = v === 'result' ? 2 : v === 'course' ? 1 : 0;
    if (window.__nav && window.__nav.setDepth) {
      try { window.__nav.setDepth(depth); } catch (e) {}
    }
  }
  function toast(msg) {
    var t = document.createElement('div'); t.className = 'toast'; t.textContent = msg;
    document.body.appendChild(t);
    setTimeout(function () { if (t.parentNode) t.parentNode.removeChild(t); }, 1600);
  }
  function pad2(n) { n = parseInt(n, 10); return (isNaN(n) || n < 10 ? '0' : '') + (isNaN(n) ? '' : n); }

  // 官方: 日时=日期/时间/农历, 时刻=时间/农历, 数字=数字/时间/农历, 随机=仅事由(不读任何输入)
  function openCourse(m) {
    method = m;
    var isRand = (m === '随机起课');
    $('rowDate').classList.toggle('hidden', m !== '日时起课');
    $('rowNum').classList.toggle('hidden', m !== '数字起课');
    $('rowTime').classList.toggle('hidden', isRand);
    $('rowLunar').classList.toggle('hidden', isRand);
    if (!isRand) { fillNow(); refreshLunar(); }
    else { $('lunarText').textContent = '—'; }
    show('course');
  }

  function fillNow() {
    var n = new Date();
    $('date').value = n.getFullYear() + '-' + pad2(n.getMonth() + 1) + '-' + pad2(n.getDate());
    $('time').value = pad2(n.getHours()) + ':' + pad2(n.getMinutes());
    refreshLunar();
  }

  // 从「日期」+「时间」两个输入框组装 Date; 日期行仅日时起课显示, 但三种方式共用同一数据源(fillNow 总会填当天)
  function readDateTime() {
    var n = new Date(), y = n.getFullYear(), mo = n.getMonth() + 1, d = n.getDate();
    var dm = /^(\d{4})-(\d{1,2})-(\d{1,2})$/.exec($('date').value.trim());
    if (dm) { y = +dm[1]; mo = +dm[2]; d = +dm[3]; }
    var tm = /^(\d{1,2}):(\d{1,2})$/.exec($('time').value.trim());
    if (!tm) { toast('时间格式应为 HH:MM'); return null; }
    var h = +tm[1], mi = +tm[2];
    if (mo < 1 || mo > 12 || d < 1 || d > 31 || h < 0 || h > 23 || mi < 0 || mi > 59) { toast('日期或时间不合法'); return null; }
    return new Date(y, mo - 1, d, h, mi);
  }

  // 农历行实时自动显示: 日时/时刻/数字三种文案各不同(对齐官方三图)
  function refreshLunar() {
    var dt = readDateTime();
    if (!dt) { $('lunarText').textContent = '—'; return; }
    var lu = window.Lunar.fromDate(dt);
    var mcn = lu.getMonthInChinese(), dcn = lu.getDayInChinese(), zhi = lu.getTimeZhi();
    var txt;
    if (method === '时刻起课') {
      // 图2: 农历：亥时丑刻  (时辰 + 刻地支, 每 10 分钟一步)
      txt = zhi + '时' + E.keZhi(dt.getHours(), dt.getMinutes()) + '刻';
    } else if (method === '数字起课' || method === '随机起课') {
      // 图3: 农历：十月初一亥时
      txt = mcn + '月' + dcn + zhi + '时';
    } else {
      // 图1: 农历：农历十月初一 亥时
      txt = '农历' + mcn + '月' + dcn + ' ' + zhi + '时';
    }
    $('lunarText').textContent = txt;
  }

  function doDivine() {
    try {
      // 随机起课: 官方独立算法 b()+y(), 完全不读日期/时间/数字输入, 与另外三种分开
      if (method === '随机起课') {
        cur = E.buildRandom({ reason: $('reason').value });
        renderResult(cur);
        show('result');
        return;
      }
      var date = readDateTime();
      if (!date) return;
      var number = null;
      if (method === '数字起课') {
        number = parseInt($('num').value, 10);
        if (isNaN(number) || number < 1) { toast('请输入起课数字'); return; }
      }
      cur = E.build({ method: method, date: date, number: number, reason: $('reason').value });
      renderResult(cur);
      show('result');
    } catch (e) {
      toast('排盘出错：' + (e && e.message ? e.message : e));
    }
  }

  function cell(cls, text) {
    return '<div class="cell ' + cls + '">' + (text ? esc(text) : '') + '</div>';
  }

  function renderResult(r) {
    var html = '';

    // 时间信息
    html += '<div class="info">';
    html += '<div class="ir"><span class="ik">起课</span><span class="iv">' + esc(r.method) + (r.number != null ? ' · ' + r.number : '') + '</span></div>';
    html += '<div class="ir"><span class="ik">公历</span><span class="iv">' + esc(r.solar) + '</span></div>';
    html += '<div class="ir"><span class="ik">农历</span><span class="iv">' + esc(r.lunar) + '</span></div>';
    if (r.keZhi) html += '<div class="ir"><span class="ik">时辰刻</span><span class="iv">' + esc(r.shichen) + '时 ' + esc(r.keZhi) + '刻</span></div>';
    if (r.reason) html += '<div class="ir"><span class="ik">事由</span><span class="iv">' + esc(r.reason) + '</span></div>';
    if (r.ganzhi && r.ganzhi.length === 4) {
      html += '<div class="gz">';
      ['年', '月', '日', '时'].forEach(function (lb, i) {
        html += '<div class="g"><small>' + lb + '柱</small><b>' + esc(r.ganzhi[i]) + '</b></div>';
      });
      html += '</div>';
    }
    html += '</div>';

    // 六宫排列 (固定方位: 留恋/速喜/赤口 / 大安/空亡/小吉)
    html += '<div class="sec-title">— 六宫排列 —</div>';
    html += '<div class="liu-grid">';
    DISPLAY.forEach(function (idx) {
      var p = r.palaces[idx];
      html += '<div class="pc' + (p.isShen ? ' shen' : '') + '" data-idx="' + idx + '">';
      html += '<div class="nine">';
      html += cell('c-wx', p.wuxing) + cell('', '') + cell('c-ls', p.liushen);
      html += cell('c-zhi', p.zhi) + cell('c-shen', p.isShen ? '身' : '') + cell('c-zhu', p.liuqin);
      html += cell('c-mark', p.mark) + cell('', '') + cell('c-name', p.name);
      html += '</div></div>';
    });
    html += '</div>';

    html += '<div class="rules" style="text-align:center;margin-top:12px;color:var(--sub);font-size:12px">点击宫位查看含义</div>';

    // 断语
    html += '<div class="card" style="margin-top:18px"><div class="lab">断语（可写你的断语，记得截图保存）</div>';
    html += '<textarea id="judgment" maxlength="500" placeholder="输入断语"></textarea></div>';

    // 按钮
    html += '<div class="act"><button class="btn sec" id="again">重新起课</button><button class="btn" id="copyBtn">复制排盘结果</button></div>';
    html += '<button class="btn btn-ghost" id="backHome">返回首页</button>';

    $('resultBody').innerHTML = html;

    // 动态按钮绑定
    $('copyBtn').addEventListener('click', copyResult);
    $('again').addEventListener('click', function () { openCourse(method); });
    $('backHome').addEventListener('click', function () { show('home'); });
    document.querySelectorAll('.pc').forEach(function (c) {
      c.addEventListener('click', function () { openDetail(parseInt(c.dataset.idx, 10)); });
    });
  }

  function openDetail(idx) {
    if (!cur) return;
    var p = cur.palaces[idx];
    var pi = D.PALACE_INFO[p.name] || { gee: '', desc: '' };
    var h = '<h3>' + esc(p.name) + ' · 宫位详解</h3>';
    h += block('宫位含义', pi.gee + '\n' + pi.desc);
    h += block('五星 · ' + p.wuxing, D.WXSTAR_INFO[p.wuxing] || '');
    h += block('六神 · ' + p.liushen, D.LIUSHEN_INFO[p.liushen] || '');
    h += block('六亲 · ' + p.liuqin, D.LIUQIN_INFO[p.liuqin] || '');
    h += block('地支 · ' + p.zhi, D.ZHI_INFO[p.zhi] || '');
    if (p.zhushiren) h += block('主事人', p.zhushiren);
    if (p.mark) h += block('定位', p.mark.replace(/ /g, ' · '));
    h += '<button class="btn" id="modalClose">确定</button>';
    $('modalBox').innerHTML = h;
    $('modalMask').classList.remove('hidden');
    $('modalClose').addEventListener('click', function () { $('modalMask').classList.add('hidden'); });
  }
  function block(t, c) {
    return '<div class="mb"><div class="mt">' + esc(t) + '</div><div class="mc">' + esc(c) + '</div></div>';
  }

  function copyResult() {
    if (!cur) return;
    var r = cur;
    var txt = '【江氏小六壬排盘】\n起课：' + r.method + (r.number != null ? ' · ' + r.number : '') +
      '\n公历：' + r.solar + '\n农历：' + r.lunar + '\n';
    if (r.reason) txt += '事由：' + r.reason + '\n';
    txt += '\n六宫排列：\n';
    DISPLAY.forEach(function (idx) {
      var p = r.palaces[idx];
      txt += p.name + '　' + p.wuxing + ' ' + p.liushen + ' ' + p.zhi + ' ' + p.liuqin +
        (p.mark ? ' [' + p.mark + ']' : '') + (p.isShen ? ' ←身宫' : '') + '\n';
    });
    var pi = D.PALACE_INFO[r.palaces[r.B].name];
    if (pi) txt += '\n结果宫：' + r.palaces[r.B].name + '\n' + pi.gee + '\n' + pi.desc + '\n';
    var jd = $('judgment');
    if (jd && jd.value.trim()) txt += '\n断语：' + jd.value.trim() + '\n';
    if (window.__copy && window.__copy.done) window.__copy.done(txt);
    else {
      var ta = document.createElement('textarea'); ta.value = txt;
      document.body.appendChild(ta); ta.select();
      try { document.execCommand('copy'); toast('已复制'); } catch (e) { toast('复制失败'); }
      document.body.removeChild(ta);
    }
  }

  // 系统返回键逐层回退(单页H5无浏览历史): 弹窗 > 结果页 > 起课页 > 首页。
  // 返回 'stay' = 已回退一层(原生留在页面); 'exit' = 已在首页(原生立即 finish)。
  // 由原生一次性读取本次调用的返回值决定是否退出, 避免依赖异步层级上报导致右滑两次才退出。
  window.__jlBack = function () {
    // 时间选择器打开时优先关闭它(优先级高于宫位弹窗), 不回退视图也不退出
    if (window.TimePicker && window.TimePicker.isOpen && window.TimePicker.isOpen()) {
      window.TimePicker.close(); return 'stay';
    }
    if (!$('modalMask').classList.contains('hidden')) { $('modalMask').classList.add('hidden'); return 'stay'; }
    if (!$('viewResult').classList.contains('hidden')) { show('course'); return 'stay'; }
    if (!$('viewCourse').classList.contains('hidden')) { show('home'); return 'stay'; }
    return 'exit';
  };

  function init() {
    document.querySelectorAll('.entry').forEach(function (e) {
      e.addEventListener('click', function () { openCourse(e.dataset.method); });
    });
    $('courseBack').addEventListener('click', function () { show('home'); });
    $('resultBack').addEventListener('click', function () { show('course'); });
    $('now').addEventListener('click', fillNow);
    // 官方同款时钟时间选择器: 时间字段只读, 点击弹出表盘(不弹系统键盘), 确定后刷新农历行
    $('time').addEventListener('click', function () {
      if (window.TimePicker) {
        window.TimePicker.open($('time').value, function (v) {
          $('time').value = v;
          refreshLunar();
        });
      }
    });
    $('go').addEventListener('click', doDivine);
    $('reselect').addEventListener('click', function () { show('home'); });
    // 日期/时间/数字变化 -> 农历行实时刷新
    ['date', 'time', 'num'].forEach(function (id) {
      var el = $(id);
      el.addEventListener('input', refreshLunar);
      el.addEventListener('change', refreshLunar);
    });
    $('modalMask').addEventListener('click', function (e) {
      if (e.target === $('modalMask')) $('modalMask').classList.add('hidden');
    });
    show('home');
  }

  // readyState 兜底: DOMContentLoaded 已过也能初始化, 避免按钮失效
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
