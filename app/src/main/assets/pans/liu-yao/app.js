/* 六爻排盘 · UI 逻辑（移植自 https://github.com/Brhiza/mingyu 京房八宫纳甲法）
 * 提供「时间起卦 / 随机三钱法 / 手工摇卦」三种起卦方式, 结果展示主/变/互卦、纳甲、六亲、六神、世应、动变、旬空、旺衰、月卦身等。
 * 纯黑主题, 不含顶部网址与底部传承说明。 */
(function () {
  'use strict';
  var E = window.LiuYaoEngine;
  var cur = null, method = 'time';
  var YAO_VALUES = [6, 7, 8, 9]; // 老阴/少阳/少阴/老阳
  var YAO_NAME = { 6: '老阴', 7: '少阳', 8: '少阴', 9: '老阳' };
  var YAO_SYM = { 6: '⚋', 7: '⚊', 8: '⚋', 9: '⚊' };
  var manualYaos = [8, 8, 8, 8, 8, 8]; // 六爻, 默认全静(少阴)

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
    var titleMap = { time: '时间起卦', manual: '手工摇卦', coin: '随机三钱法' };
    $('viewCourse').querySelector('.title').textContent = titleMap[m] || '起卦';
    $('timeFields').classList.toggle('hidden', m === 'manual' || m === 'coin');
    $('manualFields').classList.toggle('hidden', m !== 'manual');
    $('coinFields').classList.toggle('hidden', m !== 'coin');
    document.querySelector('.course-btns').classList.toggle('hidden', m === 'coin');
    if (m === 'manual') { renderYaoPick(); }
    else if (m === 'time') { fillNow(); }
    else if (m === 'coin') { resetCoin(); }
    show('course');
  }

  // === 随机三钱法: 逐爻投掷(初爻→上爻) ===
  var coinYaos = [null, null, null, null, null, null]; // index 0=上爻..5=初爻
  var coinOrder = [5, 4, 3, 2, 1, 0]; // 投掷顺序: 初爻起, 上爻止
  var coinStep = 0, coinSpins = 0, coinBusy = false, coinAlling = false;
  function resetCoin() {
    coinYaos = [null, null, null, null, null, null];
    coinStep = 0; coinSpins = 0; coinBusy = false; coinAlling = false;
    document.querySelectorAll('#coinCoins .coin').forEach(function (el) { el.style.transform = 'rotateY(0deg)'; });
    setCoinBtns(true);
    renderCoinUI();
  }
  function setCoinBtns(enabled) {
    var tb = $('tossBtn'), ab = $('tossAllBtn');
    if (tb) { tb.disabled = !enabled; tb.style.opacity = enabled ? '' : '.5'; tb.style.pointerEvents = enabled ? '' : 'none'; }
    if (ab) { ab.disabled = !enabled; ab.style.opacity = enabled ? '' : '.5'; ab.style.pointerEvents = enabled ? '' : 'none'; }
  }
  function renderCoinUI() {
    var html = '';
    for (var i = 0; i < 6; i++) {
      var v = coinYaos[i], filled = v != null, isNext = !filled && i === coinOrder[coinStep];
      html += '<div class="cs ' + (filled ? 'filled' : '') + (isNext ? ' next' : '') + '" data-i="' + i + '">'
        + '<div class="pos">' + YAO_POS[i] + '</div>'
        + '<div class="sym">' + (filled ? YAO_SYM[v] : '·') + '</div>'
        + '<div class="nm">' + (filled ? YAO_NAME[v] : '—') + '</div>'
        + '<div class="st">' + (filled ? '已定' : (isNext ? '待掷' : '等待')) + '</div>'
        + '</div>';
    }
    $('coinSlots').innerHTML = html;
    if (coinStep >= 6) {
      $('tossBtn').textContent = '查看排盘';
      $('coinHint').textContent = '六爻已成，点击查看排盘结果';
    } else if (!coinAlling) {
      var p = YAO_POS[coinOrder[coinStep]];
      $('tossBtn').textContent = '抛第 ' + (coinStep + 1) + ' 爻（' + p + '）';
      $('coinHint').textContent = '点击一次，掷三枚铜钱定 ' + p;
    }
  }
  // 抛单枚铜钱一次(字2/背3 各 50%), 返回 Promise, 动画结束后定该爻
  function throwOneYao(idx) {
    return new Promise(function (resolve) {
      coinBusy = true;
      var faces = [];
      for (var k = 0; k < 3; k++) faces.push(Math.random() < 0.5 ? 2 : 3); // 2=字 3=背
      var val = faces[0] + faces[1] + faces[2]; // 6/7/8/9
      var coinEls = document.querySelectorAll('#coinCoins .coin');
      coinEls.forEach(function (el, k) {
        coinSpins += 1;
        var finalRot = (faces[k] === 2) ? 180 : 0; // 字=正面(180) 背=背面(0)
        el.style.transform = 'rotateY(' + (coinSpins * 720 + finalRot) + 'deg)';
      });
      setTimeout(function () {
        coinYaos[idx] = val;
        coinStep++;
        coinBusy = false;
        resolve();
      }, 640);
    });
  }
  // 单爻手动抛(逐爻): 点一次抛一爻
  function tossCoin() {
    if (coinBusy || coinAlling) return;
    if (coinStep >= 6) { doCoinDivine(); return; }
    var idx = coinOrder[coinStep];
    throwOneYao(idx).then(renderCoinUI);
  }
  // 全抛: 一次点按, 6 爻依次(初→上)串行抛出, 一爻一爻出
  function tossAll() {
    if (coinBusy || coinAlling || coinStep >= 6) return;
    coinAlling = true;
    setCoinBtns(false);
    (function next() {
      if (coinStep >= 6) {
        coinAlling = false;
        setCoinBtns(true);
        renderCoinUI();
        return;
      }
      var p = YAO_POS[coinOrder[coinStep]];
      $('coinHint').textContent = '正在抛掷 ' + p + ' …';
      throwOneYao(coinOrder[coinStep]).then(function () {
        renderCoinUI();
        setTimeout(next, 120); // 爻间短停, 一爻一爻出
      });
    })();
  }
  function doCoinDivine() {
    try {
      var date = new Date();
      var opts = { yaos: coinYaos.slice(), reason: $('reason').value.trim() };
      cur = E.generateLiuyao(date, opts);
      cur.__method = '随机三钱法';
      cur.__date = date; cur.__reason = opts.reason;
      renderResult(cur);
      show('result');
    } catch (e) {
      toast('排盘出错：' + (e && e.message ? e.message : e));
    }
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

  var YAO_POS = ['上爻', '五爻', '四爻', '三爻', '二爻', '初爻']; // index0 = 第6爻(上)
  function renderYaoPick() {
    var html = '';
    for (var i = 0; i < 6; i++) {
      var v = manualYaos[i];
      html += '<div class="yp" data-i="' + i + '">'
        + '<div class="pos">' + YAO_POS[i] + '</div>'
        + '<div class="sym">' + YAO_SYM[v] + '</div>'
        + '<div class="nm">' + YAO_NAME[v] + '</div>'
        + '<button class="btn6" data-i="' + i + '">点击切换（' + v + '）</button>'
        + '</div>';
    }
    $('yaoPick').innerHTML = html;
    $('yaoPick').querySelectorAll('.btn6').forEach(function (b) {
      b.addEventListener('click', function () {
        var i = +b.dataset.i;
        var idx = YAO_VALUES.indexOf(manualYaos[i]);
        manualYaos[i] = YAO_VALUES[(idx + 1) % 4];
        var row = b.parentNode;
        row.querySelector('.sym').textContent = YAO_SYM[manualYaos[i]];
        row.querySelector('.nm').textContent = YAO_NAME[manualYaos[i]];
        b.textContent = '点击切换（' + manualYaos[i] + '）';
      });
    });
  }

  function doDivine() {
    try {
      var date, opts = { reason: $('reason').value.trim() };
      if (method === 'manual') {
        opts.yaos = manualYaos.slice();
        opts.method = 'manual';
        date = readDateTime() || new Date();
      } else {
        date = readDateTime();
        if (!date) return;
      }
      cur = E.generateLiuyao(date, opts);
      var methodLabel = { time: '时间起卦', manual: '手工摇卦', coin: '随机三钱法' };
      cur.__method = methodLabel[method] || method;
      cur.__date = date; cur.__reason = opts.reason;
      renderResult(cur);
      show('result');
    } catch (e) {
      toast('排盘出错：' + (e && e.message ? e.message : e));
    }
  }

  function yaoRow(r, idx) { // idx 0=上爻..5=初爻
    var y = r.yaosDetail[idx];
    var sym = (y.yaoType === '阳' ? '⚊' : '⚋');
    var tags = '';
    if (y.isChanging) {
      var arrow = (y.changeType === '老阳') ? '阳→阴' : '阴→阳';
      tags += '<span class="tag" style="color:var(--red);border-color:var(--red)">' + esc(y.changeType) + '·' + arrow + '</span> ';
    }
    if (r.voidBranches.indexOf(y.najiaDizhi) >= 0) tags += '<span class="tag">空亡</span> ';
    if (y.seasonState === '旺' || y.seasonState === '相') tags += '<span class="tag wang">' + y.seasonState + '</span> ';
    else if (y.seasonState === '休' || y.seasonState === '囚' || y.seasonState === '死') tags += '<span class="tag">' + y.seasonState + '</span> ';
    if (y.isVoid) tags += '<span class="tag">真空</span> ';
    if (y.isHiddenMove) tags += '<span class="tag" style="color:var(--red)">暗动</span> ';
    if (y.isDayBreak) tags += '<span class="tag" style="color:var(--red)">日破</span> ';
    if (y.isLiuhe) tags += '<span class="tag sheng">六合</span> ';
    if (y.isSanxing) tags += '<span class="tag" style="color:var(--red)">' + esc(y.sanxingType || '三刑') + '</span> ';
    if (y.shiErGong && y.shiErGong !== '无') tags += '<span class="tag">' + esc(y.shiErGong) + '</span> ';
    var sy = (y.isWorld ? '<span class="sy">世</span>' : (y.isResponse ? '<span class="sy">应</span>' : ''));
    var mv = y.isChanging ? '<span class="mv">■</span>' : '';
    return '<tr>'
      + '<td class="pos">' + YAO_POS[idx] + '</td>'
      + '<td class="sym">' + sym + mv + '</td>'
      + '<td class="dz">' + esc(y.najiaDizhi) + '</td>'
      + '<td class="wx">' + esc(y.wuxing) + '</td>'
      + '<td class="lq">' + esc(y.sixRelative) + '</td>'
      + '<td class="ls">' + esc(y.sixGod) + '</td>'
      + '<td>' + sy + '</td>'
      + '<td style="text-align:left">' + tags + '</td>'
      + '</tr>';
  }

  function renderResult(r) {
    var dt = r.__date, lu = window.Lunar.fromDate(dt);
    var html = '';

    // 基本信息
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
      + '</div>';
    html += '</div>';

    // 三卦
    html += '<div class="sec-title">— 主 · 变 · 互 卦 —</div>';
    html += '<div class="gua3">'
      + guaCard('本卦', r.originalName)
      + guaCard('变卦', r.changedName)
      + guaCard('互卦', r.interName)
      + '</div>';

    // 卦宫 / 世应
    html += '<div class="card"><div class="lab">卦宫与世应</div><div class="tagline">';
    html += '<div class="t"><b>' + esc(r.palace.name) + '宫</b> · ' + esc(r.palace.wuxing) + '</div>';
    html += '<div class="t">世在 <b>' + (r.worldAndResponse.indexOf('世') + 1) + '</b> 爻</div>';
    html += '<div class="t">应在 <b>' + (r.worldAndResponse.indexOf('应') + 1) + '</b> 爻</div>';
    if (r.palaceStage) html += '<div class="t">宫位：<b>' + esc(r.palaceStage) + '</b></div>';
    if (r.guaShen) html += '<div class="t">卦身：<b>' + esc(r.guaShen.branch) + '</b> · ' + esc(r.guaShen.sixRelative) + '（第' + r.guaShen.position + '爻）</div>';
    html += '</div></div>';

    // 六爻表 (上→下)
    html += '<div class="sec-title">— 六爻详断 —</div>';
    html += '<div class="card" style="padding:8px 10px">';
    html += '<table class="yaotable"><thead><tr>'
      + '<th>爻位</th><th>卦爻</th><th>纳甲</th><th>五行</th><th>六亲</th><th>六神</th><th>世应</th><th>象意</th>'
      + '</tr></thead><tbody>';
    for (var i = 5; i >= 0; i--) html += yaoRow(r, i);
    html += '</tbody></table></div>';

    // 旬空 / 格局 / 关系
    html += '<div class="card"><div class="lab">旬空与特殊格局</div><div class="tagline">';
    if (r.voidBranches.length) html += '<div class="t">旬空：<b>' + r.voidBranches.join('、') + '</b></div>';
    if (r.specialPattern) html += '<div class="t">格局：<b>' + esc(r.specialPattern) + '</b></div>';
    if (r.hexagramRelation && r.hexagramRelation.original) html += '<div class="t">本卦：<b>' + esc(r.hexagramRelation.original) + '</b></div>';
    if (r.hexagramRelation && r.hexagramRelation.changed) html += '<div class="t">变卦：<b>' + esc(r.hexagramRelation.changed) + '</b></div>';
    var ff = r.fanfuRelations;
    if (ff) {
      if (ff.fanyin && ff.fanyin.length) html += '<div class="t">反吟：<b>' + esc(ff.fanyin.join('、')) + '</b></div>';
      if (ff.fuyin && ff.fuyin.length) html += '<div class="t">伏吟：<b>' + esc(ff.fuyin.join('、')) + '</b></div>';
    }
    html += '</div>';
    if (r.specialAdvice) html += '<div style="margin-top:10px;color:var(--sub);font-size:13px;line-height:1.7">' + esc(r.specialAdvice) + '</div>';
    html += '</div>';

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

  var TRIGRAM_GLYPH = { '乾': '☰', '兑': '☱', '离': '☲', '震': '☳', '巽': '☴', '坎': '☵', '艮': '☶', '坤': '☷' };
  function guaCard(t, name) {
    var HEX = window.LiuYaoEngine.HEX_BY_NAME[name];
    var up = HEX ? HEX.upper : '?', low = HEX ? HEX.lower : '?';
    var sym = (TRIGRAM_GLYPH[up] || '☯') + (TRIGRAM_GLYPH[low] || '☯');
    return '<div class="gc"><div class="gt">' + t + '</div><div class="gs">' + sym + '</div><div class="gn">' + esc(name) + '</div><div class="gu">' + esc(up) + '上' + esc(low) + '下</div></div>';
  }

  function copyResult() {
    if (!cur) return;
    var r = cur, dt = r.__date, lu = window.Lunar.fromDate(dt);
    var txt = '【六爻排盘】\n起卦：' + r.__method
      + '\n公历：' + dt.getFullYear() + '-' + pad2(dt.getMonth() + 1) + '-' + pad2(dt.getDate()) + ' ' + pad2(dt.getHours()) + ':' + pad2(dt.getMinutes())
      + '\n农历：' + lu.getMonthInChinese() + '月' + lu.getDayInChinese() + ' ' + lu.getTimeZhi() + '时'
      + '\n干支：' + r.ganzhi.year + ' ' + r.ganzhi.month + ' ' + r.ganzhi.day + ' ' + r.ganzhi.hour + '\n';
    if (r.__reason) txt += '占事：' + r.__reason + '\n';
    txt += '\n本卦：' + r.originalName + '　变卦：' + r.changedName + '　互卦：' + r.interName + '\n';
    txt += '卦宫：' + r.palace.name + '宫（' + r.palace.wuxing + '）　世' + (r.worldAndResponse.indexOf('世') + 1) + '应' + (r.worldAndResponse.indexOf('应') + 1) + '\n';
    if (r.voidBranches.length) txt += '旬空：' + r.voidBranches.join('、') + '\n';
    txt += '\n六爻（上→下）：\n';
    for (var i = 5; i >= 0; i--) {
      var y = r.yaosDetail[i];
      txt += YAO_POS[i] + '　' + (y.yaoType === '阳' ? '阳' : '阴') + (y.isChanging ? '（动·' + y.changeType + '）' : '')
        + '　纳甲' + y.najiaDizhi + '　' + y.wuxing + '　' + y.sixRelative + '　' + y.sixGod
        + (y.isWorld ? '　世' : (y.isResponse ? '　应' : '')) + '\n';
    }
    if (r.specialPattern) txt += '\n格局：' + r.specialPattern + '\n';
    var jd = $('judgment');
    if (jd && jd.value.trim()) txt += '\n断语：' + jd.value.trim() + '\n';
    if (window.__copy && window.__copy.done) window.__copy.done(txt);
    else {
      var ta = document.createElement('textarea'); ta.value = txt; document.body.appendChild(ta); ta.select();
      try { document.execCommand('copy'); toast('已复制'); } catch (e) { toast('复制失败'); }
      document.body.removeChild(ta);
    }
  }

  // 系统返回键逐层回退(单页H5无浏览历史)
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
    $('randomFill').addEventListener('click', function () {
      manualYaos = manualYaos.map(function () { return YAO_VALUES[Math.floor(Math.random() * 4)]; });
      renderYaoPick();
    });
    $('clearYao').addEventListener('click', function () { manualYaos = [8, 8, 8, 8, 8, 8]; renderYaoPick(); });
    $('tossBtn').addEventListener('click', tossCoin);
    $('tossAllBtn').addEventListener('click', tossAll);
    show('home');
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
  else init();
})();
