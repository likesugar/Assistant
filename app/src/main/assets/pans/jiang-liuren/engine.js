/* 江氏小六壬 · 排盘引擎
 * 算法来源: github.com/cococora-s/jiang-xiao-liu-ren (开源实现)
 * 与「江氏小六壬官方排盘系统」app 落宫/地支/六神/五星同源;
 * 六亲按开源版五行生克动态计算, 时刻/随机起课按同源两步落宫补齐。
 */
(function (global) {
  'use strict';

  var PALACES = ["大安", "留恋", "速喜", "赤口", "小吉", "空亡"];
  var ZHI = ["子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥"];
  var PALACE_WX = ["木", "水", "火", "金", "木", "土"];          // 六宫五行
  var ZHI_WX = { 寅: "木", 卯: "木", 巳: "火", 午: "火", 丑: "土", 辰: "土", 未: "土", 戌: "土", 申: "金", 酉: "金", 亥: "水", 子: "水" };
  var SHENG = { 木: "火", 火: "土", 土: "金", 金: "水", 水: "木" };  // 相生
  var KE = { 金: "木", 木: "土", 土: "水", 水: "火", 火: "金" };       // 相克
  var LIUSHEN = ["青龙", "朱雀", "勾陈", "白虎", "玄武", "腾蛇"];
  var WXSTAR = ["木星", "火星", "土星", "金星", "水星", "天空"];
  var HOUR_ZHI = { 23: "子", 0: "子", 1: "丑", 2: "丑", 3: "寅", 4: "寅", 5: "卯", 6: "卯", 7: "辰", 8: "辰", 9: "巳", 10: "巳", 11: "午", 12: "午", 13: "未", 14: "未", 15: "申", 16: "申", 17: "酉", 18: "酉", 19: "戌", 20: "戌", 21: "亥", 22: "亥" };
  var ZHI_IDX = {};
  ZHI.forEach(function (z, i) { ZHI_IDX[z] = i; });

  function zhiCount(z) { var i = ZHI_IDX[z]; return (i === undefined || i < 0) ? 1 : i + 1; }
  function hourZhi(h) { return HOUR_ZHI[h] || "子"; }

  // 刻地支: 每 10 分钟一步, 12 刻循环 (时刻起课第二步)
  function keZhi(h, mi) {
    var hz = hourZhi(h), s, i, n = 0;
    if (hz === "子") s = h >= 23 ? 60 * (h - 23) + mi : 60 * (h + 1) + mi;
    else {
      for (i = 0; i < 24; i++) if (HOUR_ZHI[i] === hz) { n = i; break; }
      s = 60 * (h - n) + mi;
    }
    return ZHI[Math.floor(s / 10) % 12];
  }

  // 六亲: 以「身宫地支五行」为我, 与各宫地支五行生克
  function liuQinOf(selfWX, targetWX) {
    if (!selfWX || !targetWX) return '';
    if (selfWX === targetWX) return '兄弟';
    if (SHENG[targetWX] === selfWX) return '父母';
    if (SHENG[selfWX] === targetWX) return '子孙';
    if (KE[selfWX] === targetWX) return '妻财';
    if (KE[targetWX] === selfWX) return '官鬼';
    return '';
  }

  function pad2(n) { return (n < 10 ? '0' : '') + n; }
  function fmtSolar(d) {
    return d.getFullYear() + '-' + pad2(d.getMonth() + 1) + '-' + pad2(d.getDate()) +
      ' ' + pad2(d.getHours()) + ':' + pad2(d.getMinutes());
  }

  /**
   * build({method, date, number, reason}) -> result
   * method: 日时起课 | 时刻起课 | 数字起课 | 随机起课
   */
  function build(opts) {
    opts = opts || {};
    var method = opts.method || '日时起课';
    var date = opts.date || new Date();
    var reason = (opts.reason || '').trim();
    var h = date.getHours(), mi = date.getMinutes();

    var Lunar = global.Lunar;
    var lu = (Lunar && Lunar.fromDate) ? Lunar.fromDate(date) : null;
    var timeZhi = (lu && lu.getTimeZhi) ? lu.getTimeZhi() : hourZhi(h);
    var lDay = (lu && lu.getDay) ? lu.getDay() : date.getDate();

    var A, B, shenZhi, step, num = null, ke = null;
    if (method === '日时起课') {
      A = (lDay - 1) % 6; step = zhiCount(timeZhi); shenZhi = timeZhi;
    } else if (method === '数字起课') {
      num = parseInt(opts.number, 10); if (isNaN(num) || num < 1) num = 0;
      var nn = num % 6; if (nn === 0) nn = 6;
      A = (nn - 1) % 6; step = zhiCount(timeZhi); shenZhi = timeZhi;
    } else if (method === '时刻起课') {
      step = zhiCount(timeZhi); A = (step - 1) % 6;
      ke = keZhi(h, mi); shenZhi = ke; step = zhiCount(ke);
    } else {
      // 随机起课走完全独立的官方算法(b()+y() 静态表), 不复用三种的五行生克流程
      return buildRandom(opts);
    }
    B = (A + step - 1) % 6;   // 身宫 = 时宫 = 落宫

    // 地支: 从身宫起, 起始为身宫地支, 隔位(+2)顺排六宫
    var startIdx = ZHI_IDX[shenZhi]; if (startIdx === undefined) startIdx = 0;
    var zhiOf = [];
    for (var s = 0; s < 6; s++) zhiOf[(B + s) % 6] = ZHI[(startIdx + s * 2) % 12];

    // 六亲 (开源版五行生克)
    var selfWX = ZHI_WX[shenZhi];
    var liuqin = [];
    for (var i = 0; i < 6; i++) {
      liuqin[i] = (i === B)
        ? (liuQinOf(selfWX, PALACE_WX[B]) || '身宫')
        : liuQinOf(selfWX, ZHI_WX[zhiOf[i]]);
    }
    var hasBrother = false;
    for (i = 0; i < 6; i++) if (i !== B && liuqin[i] === '兄弟') hasBrother = true;
    if (!hasBrother) {
      for (var st = 1; st <= 5; st++) {
        var pi = (B + st) % 6;
        if (ZHI_WX[zhiOf[pi]] === '土') { liuqin[pi] = '兄弟'; break; }
      }
    }

    // 六神: 青龙起点 = 身宫地支序号 % 6
    var qinglong = ((startIdx % 6) + 6) % 6;
    var liushen = [];
    for (s = 0; s < 6; s++) liushen[(qinglong + s) % 6] = LIUSHEN[s];

    // 五星: 从 A 宫(日/数/时/随机宫)起顺排
    var wuxing = [];
    for (s = 0; s < 6; s++) wuxing[(A + s) % 6] = WXSTAR[s];

    // 角标
    var marks = [];
    for (i = 0; i < 6; i++) {
      var m = [];
      if (method === '时刻起课') {
        if (i === A) m.push('时'); if (i === B) m.push('刻');
      } else if (method === '数字起课' || method === '随机起课') {
        if (i === A) m.push(String(num)); if (i === B) m.push('时');
      } else {
        if (i === A) m.push('日'); if (i === B) m.push('时');
      }
      marks[i] = m.join(' ');
    }

    // 主事人: 原 app 静态表 h[身宫地支][宫名] (开源版无此概念, 仅作九宫格展示)
    var T = global.JIANG_TABLES || {};
    var zhuTable = (T.h && T.h[shenZhi]) || {};

    var palaces = [];
    for (i = 0; i < 6; i++) {
      palaces.push({
        index: i, name: PALACES[i], zhi: zhiOf[i], liuqin: liuqin[i],
        liushen: liushen[i], wuxing: wuxing[i], zhushiren: zhuTable[PALACES[i]] || '',
        mark: marks[i],
        isRiGong: (i === A), isShiGong: (i === B), isShen: (i === B)
      });
    }

    var lunarTxt = '', ganzhi = [];
    if (lu) {
      try {
        lunarTxt = '农历' + (lu.getMonthInChinese ? lu.getMonthInChinese() : '') + '月' +
          (lu.getDayInChinese ? lu.getDayInChinese() : '') + ' ' + timeZhi + '时';
      } catch (e) { lunarTxt = timeZhi + '时'; }
      try {
        ganzhi = [lu.getYearInGanZhi(), lu.getMonthInGanZhi(), lu.getDayInGanZhi(), lu.getTimeInGanZhi()];
      } catch (e) { ganzhi = []; }
    } else { lunarTxt = timeZhi + '时'; }

    return {
      method: method, date: date, solar: fmtSolar(date), lunar: lunarTxt,
      shichen: timeZhi, keZhi: ke, ganzhi: ganzhi, reason: reason,
      A: A, B: B, shenZhi: shenZhi, step: step, number: num,
      palaces: palaces
    };
  }

  /**
   * 随机起课 —— 与日时/时刻/数字三种「完全分开」的独立算法。
   * 1:1 移植官方 liuren.js 的 b() + y(): 全随机入参, 不读取任何日期/时间/数字输入,
   * 盘面走官方静态表 f(六亲)/h(主事人)/d(六神)/x(五星), 身宫六亲显示「身」。
   */
  function buildRandom(opts) {
    opts = opts || {};
    var T = global.JIANG_TABLES || {};
    // b() 第一步: 随机数字 1~6 定 A宫(随机数字宫)
    var t = Math.floor(6 * Math.random()) + 1;         // randomNumber 1~6
    var A = (t - 1) % 6;                               // A宫索引
    // b() 第二步: 随机十二地支定身宫地支与序号
    var zi = Math.floor(12 * Math.random());            // 0~11
    var shenZhi = ZHI[zi];                              // randomShiZhi 身宫地支
    var r = zi + 1;                                     // 时辰序号 1~12
    var B = (A + r - 1) % 6;                            // B宫(身宫/时宫)索引

    // y() 第五步: 排地支 - 阴阳支分组, 从身宫起顺排六宫
    var yang = ["子", "寅", "辰", "午", "申", "戌"], yin = ["丑", "卯", "巳", "未", "酉", "亥"];
    var g = yang.indexOf(shenZhi) >= 0 ? yang : yin;
    var v = g.indexOf(shenZhi);
    var zhiOf = [];
    for (var m = 0; m < 6; m++) zhiOf[(B + m) % 6] = g[(v + m) % 6];

    // y() 第六~九步: 全部走官方静态表
    var fT = T.f[shenZhi] || {}, hT = T.h[shenZhi] || {},
        dT = T.d[shenZhi] || {}, xT = T.x[PALACES[A]] || {};
    var palaces = [];
    for (var i = 0; i < 6; i++) {
      var name = PALACES[i], zhi = zhiOf[i];
      // 角标(官方 getRiShiText 随机分支): B宫「时」, A宫随机数字(原值 1~6, 不取模), 同宫「时 N」
      var mark = '';
      if (i === B) mark = '时';
      if (i === A) mark = mark ? (mark + ' ' + t) : String(t);
      palaces.push({
        index: i, name: name, zhi: zhi,
        liuqin: (i === B) ? '身' : (fT[zhi] || ''),      // 第六步: 身宫显示「身」
        liushen: dT[name] || '',                           // 第八步: 六神按身宫地支
        wuxing: xT[name] || '',                            // 第九步: 五星按 A宫
        zhushiren: hT[name] || '',                         // 第七步: 主事人按身宫地支
        mark: mark,
        isRiGong: (i === A), isShiGong: (i === B), isShen: (i === B)
      });
    }

    // 官方随机结果页: 公历=当前时间(中文), 农历=「数字X，Y时」, 无四柱, 无时辰刻
    var now = new Date();
    function p2(x) { return (x < 10 ? '0' : '') + x; }
    var solarCN = now.getFullYear() + '年' + p2(now.getMonth() + 1) + '月' + p2(now.getDate()) + '日' +
      p2(now.getHours()) + '时' + p2(now.getMinutes()) + '分';
    return {
      method: '随机起课', date: now,
      solar: solarCN, lunar: '数字' + t + '，' + shenZhi + '时',
      shichen: shenZhi, keZhi: null, ganzhi: [],
      reason: (opts.reason || '').trim(),
      A: A, B: B, shenZhi: shenZhi, step: r, number: t,
      randomNumber: t, randomShiZhi: shenZhi,
      palaces: palaces
    };
  }

  global.JiangEngine = {
    PALACES: PALACES, ZHI: ZHI, ZHI_WX: ZHI_WX, PALACE_WX: PALACE_WX,
    LIUSHEN: LIUSHEN, WXSTAR: WXSTAR,
    zhiCount: zhiCount, hourZhi: hourZhi, keZhi: keZhi,
    liuQinOf: liuQinOf, build: build, buildRandom: buildRandom
  };
  if (typeof module !== 'undefined' && module.exports) module.exports = global.JiangEngine;
})(typeof window !== 'undefined' ? window : globalThis);
