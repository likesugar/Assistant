/* 梅花易数 · UI 逻辑（移植自 https://github.com/Brhiza/mingyu 邵雍《梅花易数》）
 * 提供「时间/数字/方位/随机」起卦, 定体用、互变、体用生克旺衰、应期趋势。
 * 纯黑主题, 不含顶部网址与底部传承说明。 */
(function () {
  'use strict';
  var E = window.MeihuaEngine;
  var cur = null, method = 'time';

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
  }
  function toast(msg) {
    var t = document.createElement('div'); t.className = 'toast'; t.textContent = msg;
    document.body.appendChild(t);
    setTimeout(function () { if (t.parentNode) t.parentNode.removeChild(t); }, 1600);
  }
  function pad2(n) { n = parseInt(n, 10); return (isNaN(n) || n < 10 ? '0' : '') + (isNaN(n) ? '' : n); }
  function lunarText(dt) {
    var lu = window.Lunar.fromDate(dt);
    return '农历' + lu.getMonthInChinese() + '月' + lu.getDayInChinese() + ' ' + lu.getTimeZhi() + '时';
  }

  function openCourse(m) {
    method = m;
    $('viewCourse').querySelector('.title').textContent = ({
      time: '时间起卦', number: '数字起卦', direction: '方位起卦', random: '随机起卦'
    })[m] || '起卦';
    $('timeFields').classList.toggle('hidden', m !== 'time');
    $('numberFields').classList.toggle('hidden', m !== 'number');
    $('directionFields').classList.toggle('hidden', m !== 'direction');
    if (m === 'time') fillNow();
    show('course');
  }

  function fillNow() {
    var n = new Date();
    $('date').value = n.getFullYear() + '-' + pad2(n.getMonth() + 1) + '-' + pad2(n.getDate());
    $('time').value = pad2(n.getHours()) + ':' + pad2(n.getMinutes());
    $('lunarText').textContent = lunarText(n);
  }
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

  function doDivine() {
    try {
      var date, settings = { method: method, reason: $('reason').value.trim() };
      if (method === 'time') {
        date = readDateTime(); if (!date) return;
      } else if (method === 'number') {
        var num = parseInt($('num').value, 10);
        if (isNaN(num) || num < 1) { toast('请输入起卦数字'); return; }
        settings.number = num; date = readDateTime() || new Date();
      } else if (method === 'direction') {
        settings.direction = $('dir').value; settings.objectType = $('obj').value;
        date = readDateTime() || new Date();
      } else { // random
        date = readDateTime() || new Date();
      }
      cur = E.generateMeihua(date, settings);
      var dirTxt = method === 'direction' ? ($('dir').selectedOptions[0].text + $('obj').selectedOptions[0].text) : '';
      cur.__method = ({
        time: '时间起卦', number: '数字起卦（' + settings.number + '）',
        direction: '方位起卦（' + dirTxt + '）', random: '随机起卦'
      })[method] || method;
      cur.__date = date; cur.__reason = settings.reason;
      renderResult(cur);
      show('result');
    } catch (e) {
      toast('排盘出错：' + (e && e.message ? e.message : e));
    }
  }

  function guaCard(t, g) {
    if (!g) return '';
    var sub = (g.nature && g.element) ? (esc(g.nature) + ' · ' + esc(g.element)) : (esc(g.upper) + '上' + esc(g.lower) + '下');
    return '<div class="gc"><div class="gt">' + t + '</div><div class="gs">' + esc(g.symbol) + '</div><div class="gn">' + esc(g.name) + '</div><div class="gu">' + sub + '</div></div>';
  }

  function renderResult(r) {
    var dt = r.__date, lu = window.Lunar.fromDate(dt);
    var html = '';

    html += '<div class="info">';
    html += '<div class="ir"><span class="ik">起卦</span><span class="iv">' + esc(r.__method) + '</span></div>';
    html += '<div class="ir"><span class="ik">公历</span><span class="iv">' + dt.getFullYear() + '-' + pad2(dt.getMonth() + 1) + '-' + pad2(dt.getDate()) + ' ' + pad2(dt.getHours()) + ':' + pad2(dt.getMinutes()) + '</span></div>';
    html += '<div class="ir"><span class="ik">农历</span><span class="iv">' + lu.getMonthInChinese() + '月' + lu.getDayInChinese() + ' ' + lu.getTimeZhi() + '时</span></div>';
    if (r.__reason) html += '<div class="ir"><span class="ik">占事</span><span class="iv">' + esc(r.__reason) + '</span></div>';
    var gz = r.ganzhi;
    html += '<div class="gz">'
      + '<div class="g"><small>年柱</small><b>' + esc(gz.year) + '</b></div>'
      + '<div class="g"><small>月柱</small><b>' + esc(gz.month) + '</b></div>'
      + '<div class="g"><small>日柱</small><b>' + esc(gz.day) + '</b></div>'
      + '<div class="g"><small>时柱</small><b>' + esc(gz.hour) + '</b></div>'
      + '</div></div>';

    // 体用
    html += '<div class="sec-title">— 体用卦 —</div>';
    html += '<div class="tirow">'
      + '<div class="tg ti"><div class="tl">体卦</div><div class="tn">' + esc(r.tiGua.name) + '</div><div class="te">' + esc(r.tiGua.nature) + ' · ' + esc(r.tiGua.element) + '</div></div>'
      + '<div class="tg yong"><div class="tl">用卦</div><div class="tn">' + esc(r.yongGua.name) + '</div><div class="te">' + esc(r.yongGua.nature) + ' · ' + esc(r.yongGua.element) + '</div></div>'
      + '</div>';

    // 三卦
    html += '<div class="sec-title">— 本 · 互 · 变 卦 —</div>';
    html += '<div class="gua3">'
      + guaCard('本卦', r.mainHexagram)
      + guaCard('互卦', r.interHexagram)
      + guaCard('变卦', r.changedHexagram)
      + '</div>';

    // 动爻
    if (r.movingYao) {
      html += '<div class="card"><div class="lab">动爻</div><div class="tagline">';
      html += '<div class="t">动爻：<b>' + esc(r.movingYao.name) + '</b>（第' + r.movingYao.position + '爻）</div>';
      if (r.mainHexagram.movingYaoCi) html += '<div class="t" style="flex-basis:100%;color:var(--sub);font-size:12px">爻辞：' + esc(r.mainHexagram.movingYaoCi) + '</div>';
      html += '</div></div>';
    }

    // 体用分析
    var a = r.analysis;
    html += '<div class="card"><div class="lab">体用生克与旺衰</div><div class="anl">';
    html += '<div><span class="k">体用关系：</span>' + esc(a.tiYongRelation) + '（' + esc(a.tiYongRaw) + '）</div>';
    html += '<div><span class="k">体卦旺衰：</span>' + esc(a.tiSeasonState) + '（' + esc(a.season) + a.monthBranch + '月令·' + esc(a.monthElement) + '）</div>';
    html += '<div><span class="k">用卦旺衰：</span>' + esc(a.yongSeasonState) + '</div>';
    html += '<div style="margin-top:8px;color:var(--fg)">' + esc(a.tiYongSeasonEvaluation) + '</div>';
    html += '</div></div>';

    // 互变与趋势
    html += '<div class="card"><div class="lab">互变与趋势</div><div class="tagline">';
    html += '<div class="t">体互：<b>' + esc(a.inter1Relation) + '</b></div>';
    html += '<div class="t">用互：<b>' + esc(a.inter2Relation) + '</b></div>';
    html += '<div class="t">变卦：<b>' + esc(a.changedRelation) + '</b></div>';
    if (a.timelineTrend) html += '<div class="t" style="flex-basis:100%">走势：<b>' + esc(a.timelineTrend.trend) + '</b></div>';
    html += '</div>';
    if (a.timelineTrend && a.timelineTrend.summary) html += '<div class="anl" style="margin-top:8px">' + esc(a.timelineTrend.summary) + '</div>';
    html += '</div>';

    // 应期
    if (a.yingQi && a.yingQi.length) {
      html += '<div class="card"><div class="lab">应期提示</div><div class="anl">';
      a.yingQi.forEach(function (s) { html += '<div>· ' + esc(s) + '</div>'; });
      html += '</div></div>';
    }

    // 断语 + 按钮
    html += '<div class="card" style="margin-top:18px"><div class="lab">断语（可写你的断语，记得截图保存）</div>';
    html += '<textarea id="judgment" maxlength="500" placeholder="输入断语"></textarea></div>';
    html += '<div class="act"><button class="btn sec" id="again">重新起卦</button><button class="btn" id="copyBtn">复制排盘结果</button></div>';
    html += '<button class="btn btn-ghost" id="backHome">返回首页</button>';

    $('resultBody').innerHTML = html;
    $('copyBtn').addEventListener('click', copyResult);
    $('again').addEventListener('click', function () { openCourse(method); });
    $('backHome').addEventListener('click', function () { show('home'); });
  }

  function copyResult() {
    if (!cur) return;
    var r = cur, dt = r.__date, lu = window.Lunar.fromDate(dt);
    var txt = '【梅花易数排盘】\n起卦：' + r.__method
      + '\n公历：' + dt.getFullYear() + '-' + pad2(dt.getMonth() + 1) + '-' + pad2(dt.getDate()) + ' ' + pad2(dt.getHours()) + ':' + pad2(dt.getMinutes())
      + '\n农历：' + lu.getMonthInChinese() + '月' + lu.getDayInChinese() + ' ' + lu.getTimeZhi() + '时'
      + '\n干支：' + r.ganzhi.year + ' ' + r.ganzhi.month + ' ' + r.ganzhi.day + ' ' + r.ganzhi.hour + '\n';
    if (r.__reason) txt += '占事：' + r.__reason + '\n';
    txt += '\n体卦：' + r.tiGua.name + '（' + r.tiGua.nature + '·' + r.tiGua.element + '）\n';
    txt += '用卦：' + r.yongGua.name + '（' + r.yongGua.nature + '·' + r.yongGua.element + '）\n';
    txt += '本卦：' + r.mainHexagram.name + '　互卦：' + r.interHexagram.name + '　变卦：' + r.changedHexagram.name + '\n';
    if (r.movingYao) txt += '动爻：' + r.movingYao.name + '（第' + r.movingYao.position + '爻）\n';
    var a = r.analysis;
    txt += '\n体用关系：' + a.tiYongRelation + '\n体卦旺衰：' + a.tiSeasonState + '（' + a.season + a.monthBranch + '月令）\n';
    txt += '互变：' + a.inter1Relation + '、' + a.inter2Relation + '；变卦：' + a.changedRelation + '\n';
    if (a.timelineTrend) txt += '走势：' + a.timelineTrend.trend + '\n';
    if (a.yingQi) txt += '应期：' + a.yingQi.join('；') + '\n';
    var jd = $('judgment');
    if (jd && jd.value.trim()) txt += '\n断语：' + jd.value.trim() + '\n';
    if (window.__copy && window.__copy.done) window.__copy.done(txt);
    else {
      var ta = document.createElement('textarea'); ta.value = txt; document.body.appendChild(ta); ta.select();
      try { document.execCommand('copy'); toast('已复制'); } catch (e) { toast('复制失败'); }
      document.body.removeChild(ta);
    }
  }

  window.__jlBack = function () {
    if (window.TimePicker && window.TimePicker.isOpen && window.TimePicker.isOpen()) { window.TimePicker.close(); return 'stay'; }
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
    $('time').addEventListener('click', function () {
      if (window.TimePicker) window.TimePicker.open($('time').value, function (v) { $('time').value = v; $('lunarText').textContent = lunarText(readDateTime() || new Date()); });
    });
    $('go').addEventListener('click', doDivine);
    $('reselect').addEventListener('click', function () { show('home'); });
    ['date', 'time'].forEach(function (id) {
      var el = $(id);
      el.addEventListener('input', function () { if ($('lunarText')) $('lunarText').textContent = lunarText(readDateTime() || new Date()); });
    });
    show('home');
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
  else init();
})();
