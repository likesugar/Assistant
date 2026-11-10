/* 梅花易数排盘引擎（移植自 https://github.com/Brhiza/mingyu ，邵雍《梅花易数》）
 * 实现年月日时/数字/声音/字数/方位/随机起卦，定体用、互变、生克旺衰。
 * 依赖同目录 lunar.js 提供公历->干支/农历转换。 */
(function (global) {
  'use strict';

  var BRANCH_ORDER = ['子', '丑', '寅', '卯', '辰', '巳', '午', '未', '申', '酉', '戌', '亥'];
  var TRIGRAMS = {
    1: { name: '乾', symbol: '☰', element: '金', nature: '天', lines: [1, 1, 1] },
    2: { name: '兑', symbol: '☱', element: '金', nature: '泽', lines: [1, 1, 0] },
    3: { name: '离', symbol: '☲', element: '火', nature: '火', lines: [1, 0, 1] },
    4: { name: '震', symbol: '☳', element: '木', nature: '雷', lines: [1, 0, 0] },
    5: { name: '巽', symbol: '☴', element: '木', nature: '风', lines: [0, 1, 1] },
    6: { name: '坎', symbol: '☵', element: '水', nature: '水', lines: [0, 1, 0] },
    7: { name: '艮', symbol: '☶', element: '土', nature: '山', lines: [0, 0, 1] },
    8: { name: '坤', symbol: '☷', element: '土', nature: '地', lines: [0, 0, 0] },
  };
  function mod8(n) { n = n % 8; return n === 0 ? 8 : n; }
  function mod6(n) { n = n % 6; return n === 0 ? 6 : n; }

  // 复用六爻 hexagram 表（仅取 name/binarySymbol/upper/lower/palace/yaoCi/symbol）
  var HEX = [
    ['乾为天', '111111', '乾', '乾', '☰☰'], ['天风姤', '111011', '乾', '巽', '☰☴'], ['天山遁', '111001', '乾', '艮', '☰☶'],
    ['天地否', '111000', '乾', '坤', '☰☷'], ['风地观', '011000', '巽', '坤', '☴☷'], ['山地剥', '001000', '艮', '坤', '☶☷'],
    ['火地晋', '101000', '离', '坤', '☲☷'], ['火天大有', '101111', '离', '乾', '☲☰'], ['坎为水', '010010', '坎', '坎', '☵☵'],
    ['水泽节', '010110', '坎', '兑', '☵☱'], ['水雷屯', '010100', '坎', '震', '☵☳'], ['水火既济', '010101', '坎', '离', '☵☲'],
    ['泽火革', '110101', '兑', '离', '☱☲'], ['雷火丰', '100101', '震', '离', '☳☲'], ['地火明夷', '000101', '坤', '离', '☷☲'],
    ['地水师', '000010', '坤', '坎', '☷☵'], ['艮为山', '001001', '艮', '艮', '☶☶'], ['山火贲', '001101', '艮', '离', '☶☲'],
    ['山天大畜', '001111', '艮', '乾', '☶☰'], ['山泽损', '001110', '艮', '兑', '☶☱'], ['火泽睽', '101110', '离', '兑', '☲☱'],
    ['天泽履', '111110', '乾', '兑', '☰☱'], ['风泽中孚', '011110', '巽', '兑', '☴☱'], ['风山渐', '011001', '巽', '艮', '☴☶'],
    ['震为雷', '100100', '震', '震', '☳☳'], ['雷地豫', '100000', '震', '坤', '☳☷'], ['雷水解', '100010', '震', '坎', '☳☵'],
    ['雷风恒', '100011', '震', '巽', '☳☴'], ['地风升', '000011', '坤', '巽', '☷☴'], ['水风井', '010011', '坎', '巽', '☵☴'],
    ['泽风大过', '110011', '兑', '巽', '☱☴'], ['泽雷随', '110100', '兑', '震', '☱☳'], ['巽为风', '011011', '巽', '巽', '☴☴'],
    ['风天小畜', '011111', '巽', '乾', '☴☰'], ['风火家人', '011101', '巽', '离', '☴☲'], ['风雷益', '011100', '巽', '震', '☴☳'],
    ['天雷无妄', '111100', '乾', '震', '☰☳'], ['火雷噬嗑', '101100', '离', '震', '☲☳'], ['山雷颐', '001100', '艮', '震', '☶☳'],
    ['山风蛊', '001011', '艮', '巽', '☶☴'], ['离为火', '101101', '离', '离', '☲☲'], ['火山旅', '101001', '离', '艮', '☲☶'],
    ['火风鼎', '101011', '离', '巽', '☲☴'], ['火水未济', '101010', '离', '坎', '☲☵'], ['山水蒙', '001010', '艮', '坎', '☶☵'],
    ['风水涣', '011010', '巽', '坎', '☴☵'], ['天水讼', '111010', '乾', '坎', '☰☵'], ['天火同人', '111101', '乾', '离', '☰☲'],
    ['坤为地', '000000', '坤', '坤', '☷☷'], ['地雷复', '000100', '坤', '震', '☷☳'], ['地泽临', '000110', '坤', '兑', '☷☱'],
    ['地天泰', '000111', '坤', '乾', '☷☰'], ['雷天大壮', '100111', '震', '乾', '☳☰'], ['泽天夬', '110111', '兑', '乾', '☱☰'],
    ['水天需', '010111', '坎', '乾', '☵☰'], ['水地比', '010000', '坎', '坤', '☵☷'], ['兑为泽', '110110', '兑', '兑', '☱☱'],
    ['泽水困', '110010', '兑', '坎', '☱☵'], ['泽地萃', '110000', '兑', '坤', '☱☷'], ['泽山咸', '110001', '兑', '艮', '☱☶'],
    ['水山蹇', '010001', '坎', '艮', '☵☶'], ['地山谦', '000001', '坤', '艮', '☷☶'], ['雷山小过', '100001', '震', '艮', '☳☶'],
    ['雷泽归妹', '100110', '震', '兑', '☳☱'],
  ];
  var HEX_BY_SYM = {};
  HEX.forEach(function (h) { HEX_BY_SYM[h[4]] = { name: h[0], binarySymbol: h[1], upper: h[2], lower: h[3], symbol: h[4], palace: h[5] }; });

  function findTrigramByLines(lines) {
    for (var i = 1; i <= 8; i++) {
      var t = TRIGRAMS[i];
      if (t.lines.length === lines.length) {
        var ok = true;
        for (var j = 0; j < lines.length; j++) if (t.lines[j] !== lines[j]) { ok = false; break; }
        if (ok) return { index: i, trigram: t };
      }
    }
    return null;
  }
  function findHexagramByTrigrams(up, low) {
    var u = TRIGRAMS[up], l = TRIGRAMS[low];
    return HEX_BY_SYM[u.symbol + l.symbol];
  }
  function resolveTiYongByMovingYao(upper, lower, moving) {
    if (moving > 3) return { tiGua: lower, yongGua: upper };
    return { tiGua: upper, yongGua: lower };
  }

  // 五行关系
  var SHENG = { 木: '火', 火: '土', 土: '金', 金: '水', 水: '木' };
  var KE = { 木: '土', 土: '水', 水: '火', 火: '金', 金: '木' };
  var BRANCH_WUXING = { 子: '水', 丑: '土', 寅: '木', 卯: '木', 辰: '土', 巳: '火', 午: '火', 未: '土', 申: '金', 酉: '金', 戌: '土', 亥: '水' };
  var MONTH_LING = BRANCH_WUXING;
  var SEASON_STATE = {
    春: { 木: '旺', 火: '相', 水: '休', 金: '囚', 土: '死' },
    夏: { 火: '旺', 土: '相', 木: '休', 水: '囚', 金: '死' },
    秋: { 金: '旺', 水: '相', 土: '休', 火: '囚', 木: '死' },
    冬: { 水: '旺', 木: '相', 金: '休', 土: '囚', 火: '死' },
  };
  function isSheng(a, b) { return SHENG[a] === b; }
  function isKe(a, b) { return KE[a] === b; }
  function getBranchWuxing(b) { return BRANCH_WUXING[b]; }
  function getSeasonState(wx, monthBranch) {
    var ling = MONTH_LING[monthBranch];
    if (ling === wx) return '旺';
    if (isSheng(ling, wx)) return '相';
    if (isSheng(wx, ling)) return '休';
    if (isKe(wx, ling)) return '囚';
    if (isKe(ling, wx)) return '死';
    return '未知';
  }
  function getElementRelation(yong, ti) {
    if (yong === ti) return '体用比和';
    if (isSheng(yong, ti)) return '用生体';
    if (isSheng(ti, yong)) return '体生用';
    if (isKe(yong, ti)) return '用克体';
    if (isKe(ti, yong)) return '体克用';
    return '杂';
  }
  function getTiYongRelation(yong, ti) {
    if (yong === ti) return '比和';
    if (isSheng(yong, ti)) return '用生体';
    if (isSheng(ti, yong)) return '体生用';
    if (isKe(yong, ti)) return '用克体';
    if (isKe(ti, yong)) return '体克用';
    return '杂';
  }
  function getSeasonByMonth(m) { return m >= 1 && m <= 3 ? '春' : m <= 6 ? '夏' : m <= 9 ? '秋' : '冬'; }

  // 应期
  var YAO_PERIOD = {
    1: '初爻动，先观察事情刚开始或基层条件的变化', 2: '二爻动，先观察内部配合与近端条件的变化',
    3: '三爻动，先观察由内向外过渡时的变化', 4: '四爻动，先观察外部环境开始介入时的变化',
    5: '五爻动，先观察核心决策与主导条件的变化', 6: '上爻动，先观察事情末端、退出或重新定局的变化',
  };
  function estimateYingQi(movingYaoIndex, tiElement, yongElement, seasonState) {
    var p = [YAO_PERIOD[movingYaoIndex] || '触发层位须结合实际事件再验'];
    if (yongElement === tiElement) p.push('体用比和，关系同气，可优先观察条件同步时的进展');
    else if (isSheng(yongElement, tiElement)) p.push('用生体，外部条件对体卦有生扶，可观察助力实际出现时的进展')
    else if (isKe(yongElement, tiElement)) p.push('用克体，外部事项对体卦形成压力，须先观察阻力是否缓解')
    else if (isKe(tiElement, yongElement)) p.push('体克用，体卦能够制约事项，但须核验投入和消耗是否可承受')
    else if (isSheng(tiElement, yongElement)) p.push('体生用，体卦向事项泄气，可观察投入消耗与恢复条件');
    if (seasonState === '旺' || seasonState === '相') p.push('体卦月令' + seasonState + '，可作应期偏快的盘内参考');
    else if (seasonState === '休' || seasonState === '囚' || seasonState === '死') p.push('体卦月令' + seasonState + '，可作应期偏缓的盘内参考');
    return p;
  }
  function getTiYongSeasonEvaluation(relation, tiSeason, yongSeason) {
    var tiS = tiSeason === '旺' || tiSeason === '相', yongS = yongSeason === '旺' || yongSeason === '相';
    var ctx = '体卦月令' + tiSeason + '、用卦月令' + yongSeason;
    if (relation === '用克体') {
      if (tiS && !yongS) return '主卦用克体，' + ctx + '；体旺用衰，克体条件较轻';
      if (!tiS && yongS) return '主卦用克体，' + ctx + '；用旺体衰，克体条件较重';
      return '主卦用克体，' + ctx;
    }
    if (relation === '体克用') return '主卦体克用，' + ctx + '；体卦' + (tiS ? '旺相，制用条件较强' : '休囚死，制用条件较弱');
    if (relation === '用生体') return '主卦用生体，' + ctx + '；用卦' + (yongS ? '旺相，生体条件较强' : '休囚死，生体条件较弱');
    if (relation === '体生用') return '主卦体生用，' + ctx + '；体卦向用卦泄气';
    if (relation === '比和') return '体用同五行，比和相应；体卦' + tiSeason + '、用卦' + yongSeason;
    return '主卦体用关系未定，' + ctx;
  }
  function getInterRelationToOriginalTi(label, src, ti) {
    if (src === ti) return label + '与原体比和';
    if (isSheng(src, ti)) return label + '生原体';
    if (isSheng(ti, src)) return '原体生' + label;
    if (isKe(src, ti)) return label + '克原体';
    if (isKe(ti, src)) return '原体克' + label;
    return label + '与原体关系未定';
  }
  function evaluateTimelineTrend(t) {
    var s = getTiYongRelation(t.originalYongElement, t.tiElement);
    var i1 = getInterRelationToOriginalTi('体互', t.interTiElement, t.tiElement);
    var i2 = getInterRelationToOriginalTi('用互', t.interYongElement, t.tiElement);
    var e = getTiYongRelation(t.changedYongElement, t.changedTiElement || t.tiElement);
    var sf = s === '用生体' || s === '比和', sd = s === '用克体' || s === '体生用';
    var mf = [i1, i2].every(function (r) { return r.indexOf('生原体') >= 0 || r.indexOf('与原体比和') >= 0; });
    var md = [i1, i2].some(function (r) { return r.indexOf('克原体') >= 0 || r.indexOf('原体生') >= 0; });
    var ef = e === '用生体' || e === '比和', ed = e === '用克体' || e === '体生用';
    var summary = '主卦用/体：' + s + '；互卦：' + i1 + '、' + i2 + '；变卦用/体：' + e;
    if (sd && ef) return { trend: '先难后易', summary: summary };
    if (sf && ed) return { trend: '先顺后阻', summary: summary };
    if (sf && ef && mf) return { trend: '始末顺畅', summary: summary };
    if (sd && ed && md) return { trend: '始终受制', summary: summary };
    if (md) return { trend: '中途多阻', summary: summary };
    return { trend: '未形成单向走势', summary: summary };
  }

  // 起卦方法
  function getLunarYearBranch(lunarYearText) { var b = lunarYearText.charAt(1); return b; }
  function getHourBranch(hourGZ) { return hourGZ.charAt(1); }

  function resolveTimeMethod(gz, lunar) {
    var yearZhi = getLunarYearBranch(gz.year);
    var month = Math.abs(lunar.lunarMonth), day = lunar.lunarDay;
    var timeZhi = getHourBranch(gz.hour);
    var yi = BRANCH_ORDER.indexOf(yearZhi) + 1, ti = BRANCH_ORDER.indexOf(timeZhi) + 1;
    return {
      upperTrigramIndex: mod8(yi + month + day), lowerTrigramIndex: mod8(yi + month + day + ti),
      movingYaoIndex: mod6(yi + month + day + ti),
      calc: { method: '年月日时起卦法', methodKey: 'time', yearZhi: yearZhi, yearZhiIndex: yi, month: month, day: day, timeZhi: timeZhi, timeZhiIndex: ti },
    };
  }
  function resolveNumberMethod(number, timeBranch) {
    var ti = BRANCH_ORDER.indexOf(timeBranch) + 1;
    var total = number + ti;
    return {
      upperTrigramIndex: mod8(number), lowerTrigramIndex: mod8(ti), movingYaoIndex: mod6(total),
      calc: { method: '数字起卦法', methodKey: 'number', number: number, timeZhi: timeBranch, timeZhiIndex: ti, totalWithTime: total },
    };
  }
  function resolveSoundMethod(sound, timeBranch) {
    var ti = BRANCH_ORDER.indexOf(timeBranch) + 1, total = sound + ti;
    return {
      upperTrigramIndex: mod8(sound), lowerTrigramIndex: mod8(total), movingYaoIndex: mod6(total),
      calc: { method: '声音起卦法', methodKey: 'sound', soundCount: sound, timeZhi: timeBranch, timeZhiIndex: ti, totalWithTime: total },
    };
  }
  function resolveCharacterMethod(s) {
    var count = s.characterCount;
    var upN, lowN, rule;
    if (count === 1) { upN = s.characterLeftStrokes; lowN = s.characterRightStrokes; rule = '单字按左右分笔取上下卦'; }
    else if (count <= 3) { var half = Math.floor(count / 2); upN = s.characterStrokeCounts.slice(0, half).reduce(function (a, b) { return a + b; }, 0); lowN = s.characterStrokeCounts.slice(half).reduce(function (a, b) { return a + b; }, 0); rule = '2-3字按各字笔画数分半取上下卦'; }
    else if (count <= 10) { var h2 = Math.floor(count / 2); upN = s.characterTones.slice(0, h2).reduce(function (a, b) { return a + b; }, 0); lowN = s.characterTones.slice(h2).reduce(function (a, b) { return a + b; }, 0); rule = '4-10字按平上去入声数分半取上下卦'; }
    else { var h3 = Math.floor(count / 2); upN = h3; lowN = count - h3; rule = '11-100字按字数分半取上下卦'; }
    var total = upN + lowN;
    return {
      upperTrigramIndex: mod8(upN), lowerTrigramIndex: mod8(lowN), movingYaoIndex: mod6(total),
      calc: { method: '字数起卦法', methodKey: 'character', characterCount: count, characterUpperNumber: upN, characterLowerNumber: lowN, characterRule: rule },
    };
  }
  var DIR_IDX = { northwest: 1, west: 2, south: 3, east: 4, southeast: 5, north: 6, northeast: 7, southwest: 8 };
  var OBJ_IDX = { heaven: 1, lake: 2, fire: 3, thunder: 4, wind: 5, water: 6, mountain: 7, earth: 8 };
  function resolveDirectionMethod(direction, objectType, timeBranch) {
    var di = DIR_IDX[direction], oi = OBJ_IDX[objectType], ti = BRANCH_ORDER.indexOf(timeBranch) + 1;
    var total = oi + di + ti;
    return {
      upperTrigramIndex: oi, lowerTrigramIndex: di, movingYaoIndex: mod6(total),
      calc: { method: '方位取象起卦法', methodKey: 'direction', direction: direction, objectType: objectType, objectTrigramIndex: oi, directionTrigramIndex: di, timeZhi: timeBranch, timeZhiIndex: ti, totalWithTime: total },
    };
  }
  function resolveRandomMethod() {
    function ri() { return Math.floor(Math.random() * 8) + 1; }
    var up = ri(), low = ri(), mv = Math.floor(Math.random() * 6) + 1;
    return { upperTrigramIndex: up, lowerTrigramIndex: low, movingYaoIndex: mv, calc: { method: '随机起卦法', methodKey: 'random' } };
  }

  function getGanZhi(date) {
    var Lunar = global.Lunar;
    var lu = Lunar.fromDate(date);
    return {
      year: lu.getYearInGanZhi(), month: lu.getMonthInGanZhi(), day: lu.getDayInGanZhi(), hour: lu.getTimeInGanZhi(),
      lunarYear: lu.getYear(), lunarMonth: lu.getMonth(), lunarDay: lu.getDay(),
    };
  }

  function generateMeihua(date, settings) {
    settings = settings || {};
    date = date || new Date();
    var gz = getGanZhi(date);
    var method = settings.method || 'time';
    var timeBranch = gz.hour.charAt(1);
    var mr = (function () {
      switch (method) {
        case 'number': return resolveNumberMethod(settings.number || 0, timeBranch);
        case 'sound': return resolveSoundMethod(settings.soundCount || 0, timeBranch);
        case 'character': return resolveCharacterMethod(settings);
        case 'direction': return resolveDirectionMethod(settings.direction, settings.objectType, timeBranch);
        case 'random': return resolveRandomMethod();
        case 'time': default: return resolveTimeMethod(gz, { lunarMonth: gz.lunarMonth, lunarDay: gz.lunarDay });
      }
    })();
    var up = TRIGRAMS[mr.upperTrigramIndex], low = TRIGRAMS[mr.lowerTrigramIndex];
    var mainHex = findHexagramByTrigrams(mr.upperTrigramIndex, mr.lowerTrigramIndex);
    var mainLines = low.lines.concat(up.lines);
    var changedLines = mainLines.slice();
    changedLines[mr.movingYaoIndex - 1] = 1 - changedLines[mr.movingYaoIndex - 1];
    var mutualSource = (mr.upperTrigramIndex === mr.lowerTrigramIndex && (mr.upperTrigramIndex === 1 || mr.upperTrigramIndex === 8)) ? changedLines : mainLines;
    var interLower = findTrigramByLines(mutualSource.slice(1, 4));
    var interUpper = findTrigramByLines(mutualSource.slice(2, 5));
    var interHex = findHexagramByTrigrams(interUpper.index, interLower.index);
    var changedLower = findTrigramByLines(changedLines.slice(0, 3));
    var changedUpper = findTrigramByLines(changedLines.slice(3, 6));
    var changingHex = findHexagramByTrigrams(changedUpper.index, changedLower.index);

    var tiYong = resolveTiYongByMovingYao(up, low, mr.movingYaoIndex);
    var movingInLower = mr.movingYaoIndex <= 3;
    var interTiGua = movingInLower ? interUpper.trigram : interLower.trigram;
    var interYongGua = movingInLower ? interLower.trigram : interUpper.trigram;
    var changedTiYong = resolveTiYongByMovingYao(changedUpper.trigram, changedLower.trigram, mr.movingYaoIndex);

    var monthBranch = gz.month.charAt(1);
    var tiSeason = getSeasonState(tiYong.tiGua.element, monthBranch);
    var yongSeason = getSeasonState(tiYong.yongGua.element, monthBranch);
    var season = getSeasonByMonth(Math.abs(gz.lunarMonth));

    var MOVING_NAMES = ['初爻', '二爻', '三爻', '四爻', '五爻', '上爻'];
    var movingName = MOVING_NAMES[mr.movingYaoIndex - 1];
    var movingYaoCi = mainHex.yaoCi ? mainHex.yaoCi[mr.movingYaoIndex - 1] : '';

    var yaosDetail = mainLines.map(function (line, i) {
      var isYong = i < 3 ? movingInLower : !movingInLower;
      return { position: i + 1, yaoType: line === 1 ? '阳' : '阴', isChanging: i === mr.movingYaoIndex - 1, tiYong: isYong ? '用' : '体' };
    });

    var tiYongRaw = getTiYongRelation(tiYong.yongGua.element, tiYong.tiGua.element);
    var analysis = {
      season: season, monthBranch: monthBranch, monthElement: getBranchWuxing(monthBranch),
      tiYongRelation: getElementRelation(tiYong.yongGua.element, tiYong.tiGua.element),
      tiSeasonState: tiSeason, yongSeasonState: yongSeason,
      inter1Relation: getInterRelationToOriginalTi('体互', interTiGua.element, tiYong.tiGua.element),
      inter2Relation: getInterRelationToOriginalTi('用互', interYongGua.element, tiYong.tiGua.element),
      changedRelation: getElementRelation(changedTiYong.yongGua.element, changedTiYong.tiGua.element),
      tiYongRaw: tiYongRaw,
      tiYongSeasonEvaluation: getTiYongSeasonEvaluation(tiYongRaw, tiSeason, yongSeason),
      timelineTrend: evaluateTimelineTrend({
        tiElement: tiYong.tiGua.element, originalYongElement: tiYong.yongGua.element,
        interTiElement: interTiGua.element, interYongElement: interYongGua.element,
        changedYongElement: changedTiYong.yongGua.element, changedTiElement: changedTiYong.tiGua.element,
      }),
      yingQi: estimateYingQi(mr.movingYaoIndex, tiYong.tiGua.element, tiYong.yongGua.element, tiSeason),
    };

    return {
      originalName: mainHex.name, changedName: changingHex.name, interName: interHex.name,
      tiGua: { name: tiYong.tiGua.name, element: tiYong.tiGua.element, nature: tiYong.tiGua.nature },
      yongGua: { name: tiYong.yongGua.name, element: tiYong.yongGua.element, nature: tiYong.yongGua.nature },
      changedTiGua: { name: changedTiYong.tiGua.name, element: changedTiYong.tiGua.element, nature: changedTiYong.tiGua.nature },
      changedYongGua: { name: changedTiYong.yongGua.name, element: changedTiYong.yongGua.element, nature: changedTiYong.yongGua.nature },
      interTiGua: { name: interTiGua.name, element: interTiGua.element, nature: interTiGua.nature },
      interYongGua: { name: interYongGua.name, element: interYongGua.element, nature: interYongGua.nature },
      mainHexagram: { name: mainHex.name, symbol: mainHex.symbol, upper: up.name, lower: low.name, yaoCi: mainHex.yaoCi, movingYaoCi: movingYaoCi },
      changedHexagram: { name: changingHex.name, symbol: changingHex.symbol, upper: changedUpper.trigram.name, lower: changedLower.trigram.name },
      interHexagram: { name: interHex.name, symbol: interHex.symbol, upper: interUpper.trigram.name, lower: interLower.trigram.name },
      movingYao: { position: mr.movingYaoIndex, name: movingName, description: '第' + mr.movingYaoIndex + '爻动' },
      analysis: analysis, ganzhi: gz, yaosDetail: yaosDetail, calculation: mr.calc, timestamp: date.getTime(),
    };
  }

  global.MeihuaEngine = { generateMeihua: generateMeihua, TRIGRAMS: TRIGRAMS };
})(typeof window !== 'undefined' ? window : this);
