/* 六爻排盘引擎（移植自 https://github.com/Brhiza/mingyu ，京房八宫纳甲法）
 * 仅实现排盘计算核心：主卦/变卦/互卦、纳甲、六亲、六神、世应、动变、旬空、旺衰、月卦身、反吟伏吟、飞伏等。
 * 依赖同目录 lunar.js 提供公历->干支/农历转换。 */
(function (global) {
  'use strict';

  // ===== 基础数据 =====
  var BRANCH_ORDER = ['子', '丑', '寅', '卯', '辰', '巳', '午', '未', '申', '酉', '戌', '亥'];
  var STEM = ['甲', '乙', '丙', '丁', '戊', '己', '庚', '辛', '壬', '癸'];

  var WUXING = { 金: ['申', '酉'], 木: ['寅', '卯'], 水: ['子', '亥'], 火: ['巳', '午'], 土: ['辰', '戌', '丑', '未'] };
  var liuqinRelations = {
    金: { 金: '兄弟', 木: '妻财', 水: '子孙', 火: '官鬼', 土: '父母' },
    木: { 木: '兄弟', 土: '妻财', 火: '子孙', 金: '官鬼', 水: '父母' },
    水: { 水: '兄弟', 火: '妻财', 木: '子孙', 土: '官鬼', 金: '父母' },
    火: { 火: '兄弟', 金: '妻财', 土: '子孙', 水: '官鬼', 木: '父母' },
    土: { 土: '兄弟', 水: '妻财', 金: '子孙', 木: '官鬼', 火: '父母' },
  };
  var palaces = {
    乾: { name: '乾', wuxing: '金' }, 兑: { name: '兑', wuxing: '金' },
    离: { name: '离', wuxing: '火' }, 震: { name: '震', wuxing: '木' },
    巽: { name: '巽', wuxing: '木' }, 坎: { name: '坎', wuxing: '水' },
    艮: { name: '艮', wuxing: '土' }, 坤: { name: '坤', wuxing: '土' },
  };
  var palaceHexagrams = {
    乾: ['乾为天', '天风姤', '天山遁', '天地否', '风地观', '山地剥', '火地晋', '火天大有'],
    坎: ['坎为水', '水泽节', '水雷屯', '水火既济', '泽火革', '雷火丰', '地火明夷', '地水师'],
    艮: ['艮为山', '山火贲', '山天大畜', '山泽损', '火泽睽', '天泽履', '风泽中孚', '风山渐'],
    震: ['震为雷', '雷地豫', '雷水解', '雷风恒', '地风升', '水风井', '泽风大过', '泽雷随'],
    巽: ['巽为风', '风天小畜', '风火家人', '风雷益', '天雷无妄', '火雷噬嗑', '山雷颐', '山风蛊'],
    离: ['离为火', '火山旅', '火风鼎', '火水未济', '山水蒙', '风水涣', '天水讼', '天火同人'],
    坤: ['坤为地', '地雷复', '地泽临', '地天泰', '雷天大壮', '泽天夬', '水天需', '水地比'],
    兑: ['兑为泽', '泽水困', '泽地萃', '泽山咸', '水山蹇', '地山谦', '雷山小过', '雷泽归妹'],
  };
  var hexagramPalaceMap = {};
  Object.keys(palaceHexagrams).forEach(function (p) {
    palaceHexagrams[p].forEach(function (h) { hexagramPalaceMap[h] = p; });
  });
  var hexagramNaJia = {
    乾为天: ['子', '寅', '辰', '午', '申', '戌'], 天风姤: ['丑', '亥', '酉', '午', '申', '戌'],
    天山遁: ['辰', '午', '申', '午', '申', '戌'], 天地否: ['未', '巳', '卯', '午', '申', '戌'],
    风地观: ['未', '巳', '卯', '未', '巳', '卯'], 山地剥: ['未', '巳', '卯', '戌', '子', '寅'],
    火地晋: ['未', '巳', '卯', '酉', '未', '巳'], 火天大有: ['子', '寅', '辰', '酉', '未', '巳'],
    坎为水: ['寅', '辰', '午', '申', '戌', '子'], 水泽节: ['巳', '卯', '丑', '申', '戌', '子'],
    水雷屯: ['子', '寅', '辰', '申', '戌', '子'], 水火既济: ['卯', '丑', '亥', '申', '戌', '子'],
    泽火革: ['卯', '丑', '亥', '亥', '酉', '未'], 雷火丰: ['卯', '丑', '亥', '午', '申', '戌'],
    地火明夷: ['卯', '丑', '亥', '丑', '亥', '酉'], 地水师: ['寅', '辰', '午', '丑', '亥', '酉'],
    艮为山: ['辰', '午', '申', '戌', '子', '寅'], 山火贲: ['卯', '丑', '亥', '戌', '子', '寅'],
    山天大畜: ['子', '寅', '辰', '戌', '子', '寅'], 山泽损: ['巳', '卯', '丑', '戌', '子', '寅'],
    火泽睽: ['巳', '卯', '丑', '酉', '未', '巳'], 天泽履: ['巳', '卯', '丑', '午', '申', '戌'],
    风泽中孚: ['巳', '卯', '丑', '未', '巳', '卯'], 风山渐: ['辰', '午', '申', '未', '巳', '卯'],
    震为雷: ['子', '寅', '辰', '午', '申', '戌'], 雷地豫: ['未', '巳', '卯', '午', '申', '戌'],
    雷水解: ['寅', '辰', '午', '午', '申', '戌'], 雷风恒: ['丑', '亥', '酉', '午', '申', '戌'],
    地风升: ['丑', '亥', '酉', '丑', '亥', '酉'], 水风井: ['丑', '亥', '酉', '申', '戌', '子'],
    泽风大过: ['丑', '亥', '酉', '亥', '酉', '未'], 泽雷随: ['子', '寅', '辰', '亥', '酉', '未'],
    巽为风: ['丑', '亥', '酉', '未', '巳', '卯'], 风天小畜: ['子', '寅', '辰', '未', '巳', '卯'],
    风火家人: ['卯', '丑', '亥', '未', '巳', '卯'], 风雷益: ['子', '寅', '辰', '未', '巳', '卯'],
    天雷无妄: ['子', '寅', '辰', '午', '申', '戌'], 火雷噬嗑: ['子', '寅', '辰', '酉', '未', '巳'],
    山雷颐: ['子', '寅', '辰', '戌', '子', '寅'], 山风蛊: ['丑', '亥', '酉', '戌', '子', '寅'],
    离为火: ['卯', '丑', '亥', '酉', '未', '巳'], 火山旅: ['辰', '午', '申', '酉', '未', '巳'],
    火风鼎: ['丑', '亥', '酉', '酉', '未', '巳'], 火水未济: ['寅', '辰', '午', '酉', '未', '巳'],
    山水蒙: ['寅', '辰', '午', '戌', '子', '寅'], 风水涣: ['寅', '辰', '午', '未', '巳', '卯'],
    天水讼: ['寅', '辰', '午', '午', '申', '戌'], 天火同人: ['卯', '丑', '亥', '午', '申', '戌'],
    坤为地: ['未', '巳', '卯', '丑', '亥', '酉'], 地雷复: ['子', '寅', '辰', '丑', '亥', '酉'],
    地泽临: ['巳', '卯', '丑', '丑', '亥', '酉'], 地天泰: ['子', '寅', '辰', '丑', '亥', '酉'],
    雷天大壮: ['子', '寅', '辰', '午', '申', '戌'], 泽天夬: ['子', '寅', '辰', '亥', '酉', '未'],
    水天需: ['子', '寅', '辰', '申', '戌', '子'], 水地比: ['未', '巳', '卯', '申', '戌', '子'],
    兑为泽: ['巳', '卯', '丑', '亥', '酉', '未'], 泽水困: ['寅', '辰', '午', '亥', '酉', '未'],
    泽地萃: ['未', '巳', '卯', '亥', '酉', '未'], 泽山咸: ['辰', '午', '申', '亥', '酉', '未'],
    水山蹇: ['辰', '午', '申', '申', '戌', '子'], 地山谦: ['辰', '午', '申', '丑', '亥', '酉'],
    雷山小过: ['辰', '午', '申', '午', '申', '戌'], 雷泽归妹: ['巳', '卯', '丑', '午', '申', '戌'],
  };

  // 六十四卦基础信息
  var HEX = [
    ['乾为天', '111111', '乾', '乾', '乾', ['潜龙勿用', '见龙在田，利见大人', '君子终日乾乾，夕惕若厉，无咎', '或跃在渊，无咎', '飞龙在天，利见大人', '亢龙有悔'], '见群龙无首，吉'],
    ['天风姤', '111011', '乾', '巽', '乾', ['系于金柅，贞吉', '包有鱼，无咎', '臀无肤，其行次且', '包无鱼，起凶', '以杞包瓜', '姤其角，吝']],
    ['天山遁', '111001', '乾', '艮', '乾', ['遁尾，厉', '执之用黄牛之革', '系遁，有疾厉', '好遁，君子吉', '嘉遁，贞吉', '肥遁，无不利']],
    ['天地否', '111000', '乾', '坤', '乾', ['拔茅茹以其汇，贞吉亨', '包承，小人吉，大人否亨', '包羞', '有命无咎，畴离祉', '休否，大人吉', '倾否，先否后喜']],
    ['风地观', '011000', '巽', '坤', '乾', ['童观，小人无咎', '窥观，利女贞', '观我生，进退', '观国之光，利用宾于王', '观我生，君子无咎', '观其生，君子无咎']],
    ['山地剥', '001000', '艮', '坤', '乾', ['剥床以足，蔑贞凶', '剥床以辨，蔑贞凶', '剥之，无咎', '剥床以肤，凶', '贯鱼，以宫人宠', '硕果不食，君子得舆']],
    ['火地晋', '101000', '离', '坤', '乾', ['晋如摧如，贞吉', '晋如愁如，贞吉', '众允，悔亡', '晋如鼫鼠，贞厉', '悔亡，失得勿恤', '晋其角，维用伐邑']],
    ['火天大有', '101111', '离', '乾', '乾', ['无交害，匪咎，艰则无咎', '大车以载，有攸往，无咎', '公用亨于天子', '匪其彭，无咎', '厥孚交如威如，吉', '自天祐之，吉无不利']],
    ['坎为水', '010010', '坎', '坎', '坎', ['习坎，入于坎窞，凶', '坎有险，求小得', '来之坎坎，险且枕，入于坎窞，勿用', '樽酒簋贰，用缶，纳约自牖，终无咎', '坎不盈，祗既平，无咎', '系用徽纆，寘于丛棘，三岁不得，凶']],
    ['水泽节', '010110', '坎', '兑', '坎', ['不出户庭，无咎', '不出门庭，凶', '不节若，则嗟若', '安节，亨', '甘节，吉', '苦节，贞凶']],
    ['水雷屯', '010100', '坎', '震', '坎', ['磐桓，利居贞', '屯如邅如，乘马班如', '即鹿无虞', '乘马班如，求婚媾', '屯其膏，小贞吉', '乘马班如，泣血涟如']],
    ['水火既济', '010101', '坎', '离', '坎', ['曳其轮，濡其尾，无咎', '妇丧其茀，勿逐，七日得', '高宗伐鬼方，三年克之', '繻有衣袽，终日戒', '东邻杀牛，不如西邻之禴祭，实受其福', '濡其首，厉']],
    ['泽火革', '110101', '兑', '离', '坎', ['巩用黄牛之革', '己日乃革之，征吉', '征凶，贞厉', '悔亡，有孚改命', '大人虎变，未占有孚', '君子豹变，小人革面']],
    ['雷火丰', '100101', '震', '离', '坎', ['遇其配主，虽旬无咎', '丰其蔀，日中见斗', '丰其沛，日中见沬', '丰其蔀，日中见斗，遇其夷主', '来章，有庆誉', '丰其屋，蔀其家']],
    ['地火明夷', '000101', '坤', '离', '坎', ['明夷于飞，垂其翼', '明夷，夷于左股', '明夷于南狩', '入于左腹，获明夷之心', '箕子之明夷，利贞', '不明晦，初登于天']],
    ['地水师', '000010', '坤', '坎', '坎', ['师出以律，否臧凶', '在师中吉，无咎', '师或舆尸，凶', '师左次，无咎', '田有禽，利执言', '大君有命，开国承家']],
    ['艮为山', '001001', '艮', '艮', '艮', ['艮其趾，无咎，利永贞', '艮其腓，不拯其随，其心不快', '艮其限，列其夤，厉薰心', '艮其身，无咎', '艮其辅，言有序，悔亡', '敦艮，吉']],
    ['山火贲', '001101', '艮', '离', '艮', ['贲其趾，舍车而徒', '贲其须', '贲如濡如，永贞吉', '贲如皤如，白马翰如', '贲于丘园，束帛戋戋', '白贲，无咎']],
    ['山天大畜', '001111', '艮', '乾', '艮', ['有厉，利已', '舆说辐', '良马逐，利艰贞', '童牛之牿，元吉', '豶豕之牙，吉', '何天之衢，亨']],
    ['山泽损', '001110', '艮', '兑', '艮', ['已事遄往，无咎', '利贞，征凶', '三人行，则损一人', '损其疾，使遄有喜', '或益之十朋之龟', '弗损益之，无咎']],
    ['火泽睽', '101110', '离', '兑', '艮', ['悔亡，丧马勿逐', '遇主于巷，无咎', '见舆曳，其牛掣', '睽孤，遇元夫', '悔亡，厥宗噬肤', '睽孤，见豕负涂']],
    ['天泽履', '111110', '乾', '兑', '艮', ['素履，往无咎', '履道坦坦，幽人贞吉', '眇能视，跛能履，履虎尾，咥人，凶。武人为于大君。', '履虎尾，愬愬终吉', '夬履，贞厉', '视履考祥，其旋元吉']],
    ['风泽中孚', '011110', '巽', '兑', '艮', ['虞吉，有它不燕', '鸣鹤在阴，其子和之', '得敌，或鼓或罢', '月几望，马匹亡', '有孚挛如，无咎', '翰音登于天，贞凶']],
    ['风山渐', '011001', '巽', '艮', '艮', ['鸿渐于干，小子厉', '鸿渐于磐，饮食衎衎', '鸿渐于陆，夫征不复', '鸿渐于木，或得其桷', '鸿渐于陵，妇三岁不孕', '鸿渐于陆，其羽可用为仪']],
    ['震为雷', '100100', '震', '震', '震', ['震来虩虩，后笑言哑哑，吉', '震来厉，亿丧贝，跻于九陵，勿逐，七日得', '震苏苏，震行无眚', '震遂泥', '震往来厉，亿无丧，有事', '震索索，视矍矍，征凶']],
    ['雷地豫', '100000', '震', '坤', '震', ['鸣豫，凶', '介于石，不终日，贞吉', '盱豫，悔，迟有悔', '由豫，大有得', '贞疾，恒不死', '冥豫，成有渝']],
    ['雷水解', '100010', '震', '坎', '震', ['无咎', '田获三狐，得黄矢', '负且乘，致寇至', '解而拇，朋至斯孚', '君子维有解，吉', '公用射隼于高墉之上']],
    ['雷风恒', '100011', '震', '巽', '震', ['浚恒，贞凶', '悔亡', '不恒其德，或承之羞', '田无禽', '恒其德，贞，妇人吉', '振恒，凶']],
    ['地风升', '000011', '坤', '巽', '震', ['允升，大吉', '孚乃利用禴', '升虚邑', '王用亨于岐山', '贞吉，升阶', '冥升，利于不息之贞']],
    ['水风井', '010011', '坎', '巽', '震', ['井泥不食', '井谷射鲋，瓮敝漏', '井渫不食，为我心恻', '井甃，无咎', '井冽寒泉食', '井收勿幕，有孚元吉']],
    ['泽风大过', '110011', '兑', '巽', '震', ['藉用白茅，无咎', '枯杨生稊，老夫得其女妻', '栋桡，凶', '栋隆，吉', '枯杨生华，老妇得其士夫', '过涉灭顶，凶']],
    ['泽雷随', '110100', '兑', '震', '震', ['官有渝，贞吉', '系小子，失丈夫', '系丈夫，失小子', '随有获，贞凶', '孚于嘉，吉', '拘系之，乃从维之']],
    ['巽为风', '011011', '巽', '巽', '巽', ['进退，利武人之贞', '巽在床下，用史巫纷若，吉，无咎', '频巽，吝', '悔亡，田获三品', '贞吉悔亡，无不利，无初有终', '巽在床下，丧其资斧，贞凶']],
    ['风天小畜', '011111', '巽', '乾', '巽', ['复自道，何其咎，吉', '牵复，吉', '舆说辐，夫妻反目', '有孚，血去惕出，无咎', '有孚挛如，富以其邻', '既雨既处，尚德载']],
    ['风火家人', '011101', '巽', '离', '巽', ['闲有家，悔亡', '无攸遂，在中馈', '家人嗃嗃，悔厉吉', '富家，大吉', '王假有家，勿恤', '有孚威如，终吉']],
    ['风雷益', '011100', '巽', '震', '巽', ['利用为大作，元吉', '或益之十朋之龟', '益之用凶事，无咎', '中行告公从', '有孚惠心，勿问元吉', '莫益之，或击之']],
    ['天雷无妄', '111100', '乾', '震', '巽', ['无妄，往吉', '不耕获，不菑畬', '无妄之灾', '可贞，无咎', '无妄之疾，勿药有喜', '无妄，行有眚']],
    ['火雷噬嗑', '101100', '离', '震', '巽', ['屦校灭趾，无咎', '噬肤灭鼻，无咎', '噬腊肉，遇毒', '噬干胏，得金矢', '噬干肉，得黄金', '何校灭耳，凶']],
    ['山雷颐', '001100', '艮', '震', '巽', ['舍尔灵龟，观我朵颐', '颠颐，拂经', '拂颐，贞凶', '颠颐，吉', '拂经，居贞吉', '由颐，厉吉']],
    ['山风蛊', '001011', '艮', '巽', '巽', ['干父之蛊，有子', '干母之蛊，不可贞', '干父之蛊，小有悔', '裕父之蛊，往见吝', '干父之蛊，用誉', '不事王侯，高尚其事']],
    ['离为火', '101101', '离', '离', '离', ['履错然，敬之无咎', '黄离，元吉', '日昃之离，不鼓缶而歌，则大耋之嗟，凶', '突如其来如，焚如，死如，弃如', '出涕沱若，戚嗟若，吉', '王用出征，有嘉折首，获匪其丑，无咎']],
    ['火山旅', '101001', '离', '艮', '离', ['旅琐琐，斯其所取灾', '旅即次，怀其资', '旅焚其次，丧其童仆', '旅于处，得其资斧', '射雉一矢亡', '鸟焚其巢，旅人先笑后号咷']],
    ['火风鼎', '101011', '离', '巽', '离', ['鼎颠趾，利出否', '鼎有实，我仇有疾', '鼎耳革，其行塞', '鼎折足，覆公餗', '鼎黄耳金铉', '鼎玉铉，大吉无不利']],
    ['火水未济', '101010', '离', '坎', '离', ['濡其尾，吝', '曳其轮，贞吉', '未济，征凶，利涉大川', '贞吉，悔亡，震用伐鬼方，三年有赏于大国', '贞吉，无悔，君子之光，有孚吉', '有孚于饮酒，无咎，濡其首，有孚失是']],
    ['山水蒙', '001010', '艮', '坎', '离', ['发蒙，利用刑人', '包蒙吉，纳妇吉', '勿用取女，见金夫', '困蒙，吝', '童蒙，吉', '击蒙，不利为寇']],
    ['风水涣', '011010', '巽', '坎', '离', ['用拯马壮，吉', '涣奔其机，悔亡', '涣其躬，无悔', '涣其群，元吉', '涣汗其大号', '涣其血去逖出']],
    ['天水讼', '111010', '乾', '坎', '离', ['不永所事，小有言，终吉', '不克讼，归而逋', '食旧德，贞厉，终吉', '不克讼，复即命渝', '讼，元吉', '或锡之鞶带，终朝三褫之']],
    ['天火同人', '111101', '乾', '离', '离', ['同人于门，无咎', '同人于宗，吝', '伏戎于莽，升其高陵', '乘其墉，弗克攻，吉', '同人先号咷而后笑', '同人于郊，无悔']],
    ['坤为地', '000000', '坤', '坤', '坤', ['履霜，坚冰至', '直方大，不习无不利', '含章可贞，或从王事，无成有终', '括囊，无咎无誉', '黄裳，元吉', '龙战于野，其血玄黄'], '利永贞'],
    ['地雷复', '000100', '坤', '震', '坤', ['不远复，无祗悔', '休复，吉', '频复，厉，无咎', '中行独复', '敦复，无悔', '迷复，凶']],
    ['地泽临', '000110', '坤', '兑', '坤', ['咸临，贞吉', '咸临，吉，无不利', '甘临，无攸利', '至临，无咎', '知临，大君之宜，吉', '敦临，吉，无咎']],
    ['地天泰', '000111', '坤', '乾', '坤', ['拔茅茹以其汇，征吉', '包荒，用冯河，不遐遗，朋亡，得尚于中行', '无平不陂，无往不复，艰贞无咎', '翩翩，不富以其邻，不戒以孚', '帝乙归妹，以祉元吉', '城复于隍，勿用师']],
    ['雷天大壮', '100111', '震', '乾', '坤', ['壮于趾，征凶', '贞吉', '小人用壮，君子用罔', '贞吉悔亡，藩决不羸', '丧羊于易，无悔', '羝羊触藩，不能退']],
    ['泽天夬', '110111', '兑', '乾', '坤', ['壮于前趾，往不胜', '惕号，莫夜有戎', '壮于頄，有凶', '臀无肤，其行次且', '苋陆夬夬，中行无咎', '无号，终有凶']],
    ['水天需', '010111', '坎', '乾', '坤', ['需于郊，利用恒，无咎', '需于沙，小有言，终吉', '需于泥，致寇至', '需于血，出自穴', '需于酒食，贞吉', '入于穴，有不速之客三人来']],
    ['水地比', '010000', '坎', '坤', '坤', ['有孚比之，无咎', '比之自内，贞吉', '比之匪人', '外比之，贞吉', '显比，王用三驱', '比之无首，凶']],
    ['兑为泽', '110110', '兑', '兑', '兑', ['和兑，吉', '孚兑，吉，悔亡', '来兑，凶', '商兑，未宁，介疾有喜', '孚于剥，有厉', '引兑']],
    ['泽水困', '110010', '兑', '坎', '兑', ['臀困于株木，入于幽谷', '困于酒食，朱绂方来', '困于石，据于蒺藜', '来徐徐，困于金车', '劓刖，困于赤绂', '困于葛藟，于臲卼']],
    ['泽地萃', '110000', '兑', '坤', '兑', ['有孚不终，乃乱乃萃', '引吉无咎', '萃如嗟如，无攸利', '大吉，无咎', '萃有位，无咎', '赍咨涕洟，无咎']],
    ['泽山咸', '110001', '兑', '艮', '兑', ['咸其拇', '咸其腓，凶，居吉', '咸其股，执其随', '贞吉悔亡，憧憧往来', '咸其脢，无悔', '咸其辅颊舌']],
    ['水山蹇', '010001', '坎', '艮', '兑', ['往蹇，来誉', '王臣蹇蹇，匪躬之故', '往蹇，来反', '往蹇，来连', '大蹇，朋来', '往蹇，来硕，吉']],
    ['地山谦', '000001', '坤', '艮', '兑', ['谦谦君子，用涉大川，吉', '鸣谦，贞吉', '劳谦，君子有终，吉', '无不利，撝谦', '不富以其邻，利用侵伐', '鸣谦，利用行师']],
    ['雷山小过', '100001', '震', '艮', '兑', ['飞鸟以凶', '过其祖，遇其妣', '弗过防之', '无咎，弗过遇之', '密云不雨，自我西郊', '弗遇过之，飞鸟离之']],
    ['雷泽归妹', '100110', '震', '兑', '兑', ['归妹以娣', '眇能视，利幽人之贞', '归妹以须', '归妹愆期', '帝乙归妹', '女承筐无实']],
  ];
  var HEX_BY_NAME = {};
  var HEX_BY_BIN = {};
  HEX.forEach(function (h) {
    var o = { name: h[0], binarySymbol: h[1], upper: h[2], lower: h[3], palace: h[4], yaoCi: h[5], yongCi: h[6] };
    HEX_BY_NAME[h[0]] = o; HEX_BY_BIN[h[1]] = o;
  });

  // ===== 干支关系 =====
  var BRANCH_WUXING = { 子: '水', 丑: '土', 寅: '木', 卯: '木', 辰: '土', 巳: '火', 午: '火', 未: '土', 申: '金', 酉: '金', 戌: '土', 亥: '水' };
  var SHENG = { 木: '火', 火: '土', 土: '金', 金: '水', 水: '木' };
  var KE = { 木: '土', 土: '水', 水: '火', 火: '金', 金: '木' };
  var LIUHE = { 子: '丑', 丑: '子', 寅: '亥', 亥: '寅', 卯: '戌', 戌: '卯', 辰: '酉', 酉: '辰', 巳: '申', 申: '巳', 午: '未', 未: '午' };
  var LIUHAI = { 子: '未', 未: '子', 丑: '午', 午: '丑', 寅: '巳', 巳: '寅', 卯: '辰', 辰: '卯', 申: '亥', 亥: '申', 酉: '戌', 戌: '酉' };
  var LIUCHONG = { 子: '午', 午: '子', 丑: '未', 未: '丑', 寅: '申', 申: '寅', 卯: '酉', 酉: '卯', 辰: '戌', 戌: '辰', 巳: '亥', 亥: '巳' };
  var SANXING = { 子: '卯', 卯: '子', 寅: '巳', 巳: '申', 申: '寅', 丑: '戌', 戌: '未', 未: '丑', 辰: '辰', 午: '午', 酉: '酉', 亥: '亥' };
  var SANXING_GROUP = { 子: ['卯'], 卯: ['子'], 寅: ['巳', '申'], 巳: ['申', '寅'], 申: ['寅', '巳'], 丑: ['戌', '未'], 戌: ['未', '丑'], 未: ['丑', '戌'], 辰: ['辰'], 午: ['午'], 酉: ['酉'], 亥: ['亥'] };
  var MONTH_LING = BRANCH_WUXING; // 月建本气
  var CHANGSHENG_START = { 木: '亥', 火: '寅', 金: '巳', 水: '申', 土: '申' };
  var CHANGSHENG_ORDER = ['长生', '沐浴', '冠带', '临官', '帝旺', '衰', '病', '死', '墓', '绝', '胎', '养'];

  function isSheng(a, b) { return SHENG[a] === b; }
  function isKe(a, b) { return KE[a] === b; }
  function isLiuhe(a, b) { return LIUHE[a] === b; }
  function isLiuhai(a, b) { return LIUHAI[a] === b; }
  function isLiuchong(a, b) { return LIUCHONG[a] === b; }
  function isSanxing(a, b) {
    if ((a === '子' && b === '卯') || (a === '卯' && b === '子')) return true;
    if (['寅', '巳', '申'].indexOf(a) >= 0 && ['寅', '巳', '申'].indexOf(b) >= 0 && a !== b) return true;
    if (['丑', '戌', '未'].indexOf(a) >= 0 && ['丑', '戌', '未'].indexOf(b) >= 0 && a !== b) return true;
    if (['辰', '午', '酉', '亥'].indexOf(a) >= 0 && a === b) return true;
    return false;
  }
  function getSanxingType(branch) {
    if (branch === '子' || branch === '卯') return '无礼之刑';
    if (['寅', '巳', '申'].indexOf(branch) >= 0) return '无恩之刑';
    if (['丑', '戌', '未'].indexOf(branch) >= 0) return '恃势之刑';
    if (['辰', '午', '酉', '亥'].indexOf(branch) >= 0) return '自刑';
    return null;
  }
  function getSeasonState(yaoWuxing, monthBranch) {
    var ling = MONTH_LING[monthBranch];
    if (ling === yaoWuxing) return '旺';
    if (isSheng(ling, yaoWuxing)) return '相';
    if (isSheng(yaoWuxing, ling)) return '休';
    if (isKe(yaoWuxing, ling)) return '囚';
    if (isKe(ling, yaoWuxing)) return '死';
    return '未知';
  }
  function getBranchWuxing(branch) { return BRANCH_WUXING[branch]; }
  function getShiErGong(wuxing, branch) {
    var start = CHANGSHENG_START[wuxing];
    var si = BRANCH_ORDER.indexOf(start), bi = BRANCH_ORDER.indexOf(branch);
    var off = (((bi - si) % 12) + 12) % 12;
    return CHANGSHENG_ORDER[off];
  }
  function getXunKong(ganZhi) {
    var idx = -1;
    for (var i = 0; i < 60; i++) {
      var g = STEM[i % 10], b = BRANCH_ORDER[i % 12];
      if (g + b === ganZhi) { idx = i; break; }
    }
    if (idx < 0) return [];
    var bIdx = idx % 12;
    return [BRANCH_ORDER[(bIdx + 10) % 12], BRANCH_ORDER[(bIdx + 11) % 12]];
  }

  // 六神
  var SIX_GOD_ORDER = ['青龙', '朱雀', '勾陈', '螣蛇', '白虎', '玄武'];
  var SIX_GOD_START = { 甲: '青龙', 乙: '青龙', 丙: '朱雀', 丁: '朱雀', 戊: '勾陈', 己: '螣蛇', 庚: '白虎', 辛: '白虎', 壬: '玄武', 癸: '玄武' };
  function getSixAnimals(dayGan) {
    var s = SIX_GOD_START[dayGan];
    var k = SIX_GOD_ORDER.indexOf(s);
    var r = [];
    for (var i = 0; i < 6; i++) r.push(SIX_GOD_ORDER[(k + i) % 6]);
    return r;
  }

  // ===== 排盘计算 =====
  function toBin(yaos /* 初..上 6 */) {
    var lines = yaos.map(function (y) { return y === '阳' ? '1' : '0'; });
    return lines.slice(3, 6).join('') + lines.slice(0, 3).join('');
  }
  function findHex(bin) { return HEX_BY_BIN[bin]; }

  function findPalace(name) {
    var pn = hexagramPalaceMap[name];
    return { name: pn, wuxing: palaces[pn].wuxing };
  }
  function getNaJiaAndLiuQin(name, palace) {
    var arr = hexagramNaJia[name];
    return arr.map(function (dizhi) {
      var wx = null;
      for (var k in WUXING) if (WUXING[k].indexOf(dizhi) >= 0) wx = k;
      var lq = liuqinRelations[palace.wuxing][wx];
      return { dizhi: dizhi, wuxing: wx, liuqin: lq };
    });
  }
  var PALACE_ORDER = ['首卦', '一世', '二世', '三世', '四世', '五世', '游魂', '归魂'];
  var SHI_YAO_MAP = { 首卦: 6, 一世: 1, 二世: 2, 三世: 3, 四世: 4, 五世: 5, 游魂: 4, 归魂: 3 };
  function getPalaceStage(name, palaceName) {
    var list = palaceHexagrams[palaceName];
    return PALACE_ORDER[list.indexOf(name)];
  }
  function getShiYing(name, palaceName) {
    var stage = getPalaceStage(name, palaceName);
    var shi = SHI_YAO_MAP[stage];
    var ying = shi + 3 > 6 ? shi - 3 : shi + 3;
    return { shi: shi, ying: ying };
  }

  function hashStr(s) {
    var h = 2166136261 >>> 0;
    for (var i = 0; i < s.length; i++) { h ^= s.charCodeAt(i); h = Math.imul(h, 16777619) >>> 0; }
    return h >>> 0;
  }
  function mulberry32(a) {
    return function () {
      a |= 0; a = (a + 0x6D2B79F5) | 0;
      var t = Math.imul(a ^ (a >>> 15), 1 | a);
      t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  }
  function randYao(rng) {
    // 三钱法：每钱 2/3，合计 6/7/8/9（6老阴,7少阳,8少阴,9老阳）
    var c = [rng() < 0.5 ? 2 : 3, rng() < 0.5 ? 2 : 3, rng() < 0.5 ? 2 : 3];
    return c[0] + c[1] + c[2];
  }

  function getGanZhi(date) {
    var Lunar = global.Lunar;
    var lu = Lunar.fromDate(date);
    return {
      year: lu.getYearInGanZhi(),
      month: lu.getMonthInGanZhi(),
      day: lu.getDayInGanZhi(),
      hour: lu.getTimeInGanZhi(),
      lunarYear: lu.getYear(), lunarMonth: lu.getMonth(), lunarDay: lu.getDay(),
    };
  }

  function generateLiuyao(date, opts) {
    opts = opts || {};
    date = date || new Date();
    var gz = getGanZhi(date);
    var timestamp = date.getTime();
    var dayGan = gz.day.charAt(0), dayBranch = gz.day.charAt(1), monthBranch = gz.month.charAt(1);

    var rawYaos;
    if (opts.yaos && opts.yaos.length === 6) {
      rawYaos = opts.yaos.slice();
    } else if (opts.method === 'manual' && opts.yaos) {
      rawYaos = opts.yaos.slice();
    } else if (opts.method === 'coin') {
      // 随机三钱法: 三枚铜钱各 2/3, 每次点击以实时随机种子掷出 6 爻(可出 0~6 个动爻)
      var cseed = (Date.now() ^ Math.floor(Math.random() * 0x7fffffff)) >>> 0;
      var crng = mulberry32(cseed);
      rawYaos = [randYao(crng), randYao(crng), randYao(crng), randYao(crng), randYao(crng), randYao(crng)];
    } else {
      var seed = hashStr('时间起卦:' + timestamp);
      var rng = mulberry32(seed);
      rawYaos = [randYao(rng), randYao(rng), randYao(rng), randYao(rng), randYao(rng), randYao(rng)];
    }
    var mainYaos = rawYaos.map(function (y) { return (y === 7 || y === 9) ? '阳' : '阴'; });
    var changedYaos = rawYaos.map(function (y, i) {
      if (y === 6) return '阳'; if (y === 9) return '阴'; return mainYaos[i];
    });
    function interOf(yaos) { return yaos.slice(1, 4).concat(yaos.slice(2, 5)); }

    var mainHex = findHex(toBin(mainYaos));
    var changedHex = findHex(toBin(changedYaos));
    var interHex = findHex(toBin(interOf(mainYaos)));
    if (!mainHex || !changedHex || !interHex) throw new Error('卦象查找失败');

    var animals = getSixAnimals(dayGan);
    var palace = findPalace(mainHex.name);
    var yaosInfo = getNaJiaAndLiuQin(mainHex.name, palace);
    var shiYing = getShiYing(mainHex.name, palace.name);
    var palaceStage = getPalaceStage(mainHex.name, palace.name);
    var voids = getXunKong(gz.day);
    var changedYaosInfo = getNaJiaAndLiuQin(changedHex.name, palace);

    var changingYaos = rawYaos.map(function (y, i) {
      return { position: i + 1, isChanging: y === 6 || y === 9, type: y === 6 ? '老阴' : y === 9 ? '老阳' : '静爻' };
    }).filter(function (y) { return y.isChanging; });

    var special = getSpecialPattern(changingYaos.length, mainHex.name);

    var yaosDetail = yaosInfo.map(function (info, i) {
      var isChanging = rawYaos[i] === 6 || rawYaos[i] === 9;
      var changedInfo = isChanging ? changedYaosInfo[i] : null;
      var isDayClash = isLiuchong(info.dizhi, dayBranch);
      var isMonthBreak = isLiuchong(info.dizhi, monthBranch);
      var isVoid = voids.indexOf(info.dizhi) >= 0;
      var seasonState = getSeasonState(info.wuxing, monthBranch);
      var isHiddenMove = (!isChanging && isLiuchong(info.dizhi, dayBranch) && !isLiuchong(info.dizhi, monthBranch)
        && (isVoid || seasonState === '旺' || seasonState === '相'));
      var isDayBreak = !isChanging && isDayClash && !isHiddenMove;
      var changeDirection = changedInfo ? getChangeDirection(info.dizhi, changedInfo.dizhi) : null;
      var changeRelations = changedInfo ? getChangeRelations(info.wuxing, changedInfo.wuxing, info.dizhi, changedInfo.dizhi, isVoid) : [];
      var changeRelation = changedInfo ? changeRelations[0] : null;
      var dayLifeStage = getShiErGong(info.wuxing, dayBranch);
      var changedLifeStage = changedInfo ? getShiErGong(info.wuxing, changedInfo.dizhi) : undefined;
      var isRiMu = dayLifeStage === '墓';
      var isHuaMu = changedLifeStage === '墓';
      return {
        position: i + 1, rawValue: rawYaos[i], yaoType: mainYaos[i],
        isChanging: isChanging, changeType: rawYaos[i] === 6 ? '老阴' : rawYaos[i] === 9 ? '老阳' : '静爻',
        sixGod: animals[i], sixRelative: info.liuqin, najiaDizhi: info.dizhi, wuxing: info.wuxing,
        isWorld: shiYing.shi === i + 1, isResponse: shiYing.ying === i + 1, isVoid: isVoid,
        isDayClash: isDayClash, isDayBreak: isDayBreak, isMonthBreak: isMonthBreak, isHiddenMove: isHiddenMove,
        seasonState: seasonState, changeDirection: changeDirection, changeRelation: changeRelation, changeRelations: changeRelations,
        isSanxing: isSanxing(info.dizhi, dayBranch) || isSanxing(info.dizhi, monthBranch),
        sanxingType: getSanxingType(info.dizhi) || undefined,
        isLiuhe: isLiuhe(info.dizhi, dayBranch) || isLiuhe(info.dizhi, monthBranch),
        liuhePartner: isLiuhe(info.dizhi, dayBranch) ? dayBranch : (isLiuhe(info.dizhi, monthBranch) ? monthBranch : undefined),
        isLiuhai: isLiuhai(info.dizhi, dayBranch) || isLiuhai(info.dizhi, monthBranch),
        isRuMu: isRiMu || isHuaMu, dayLifeStage: dayLifeStage, changedLifeStage: changedLifeStage,
        isRiMu: isRiMu, isHuaMu: isHuaMu, shiErGong: getShiErGong(info.wuxing, info.dizhi),
        changedYao: changedInfo ? { dizhi: changedInfo.dizhi, wuxing: changedInfo.wuxing, liuqin: changedInfo.liuqin, isVoid: voids.indexOf(changedInfo.dizhi) >= 0 } : null,
      };
    });

    // 月卦身
    var shiYaoType = mainYaos[shiYing.shi - 1];
    var SHI_YANG = { 1: '子', 2: '丑', 3: '寅', 4: '卯', 5: '辰', 6: '巳' };
    var SHI_YIN = { 1: '午', 2: '未', 3: '申', 4: '酉', 5: '戌', 6: '亥' };
    var guaShenBranch = (shiYaoType === '阳' ? SHI_YANG : SHI_YIN)[shiYing.shi];
    var guaShenYao = yaosInfo.find(function (i) { return i.dizhi === guaShenBranch; });
    var guaShen = guaShenYao ? { branch: guaShenBranch, sixRelative: guaShenYao.liuqin, position: yaosInfo.indexOf(guaShenYao) + 1 } : null;

    // 六合/六冲/反吟伏吟/飞伏
    var hexRel = getHexagramRelation(mainHex.name);
    var changedRel = changingYaos.length ? getHexagramRelation(changedHex.name) : null;
    var fanfu = getFanFuRelations(mainHex.name, changedHex.name, changingYaos.length > 0);
    var hiddenSpirits = buildHiddenSpirits(mainHex.name, palace, yaosDetail, voids);

    var worldAndResponse = ['', '', '', '', '', ''];
    worldAndResponse[shiYing.shi - 1] = '世'; worldAndResponse[shiYing.ying - 1] = '应';

    return {
      originalName: mainHex.name, changedName: changedHex.name, interName: interHex.name,
      yaoArray: rawYaos, changingYaos: changingYaos,
      sixGods: animals, sixRelatives: yaosInfo.map(function (i) { return i.liuqin; }),
      najiaDizhi: yaosInfo.map(function (i) { return i.dizhi; }), wuxing: yaosInfo.map(function (i) { return i.wuxing; }),
      worldAndResponse: worldAndResponse, voidBranches: voids, palace: palace, palaceStage: palaceStage,
      ganzhi: gz, specialPattern: special.specialPattern, specialAdvice: special.specialAdvice,
      yaosDetail: yaosDetail, hiddenSpirits: hiddenSpirits,
      hexagramRelation: { original: hexRel, changed: changedRel }, fanfuRelations: fanfu, guaShen: guaShen,
      timestamp: timestamp,
    };
  }

  function getSpecialPattern(count, name) {
    if (count === 0) return { specialPattern: '静卦', specialAdvice: '六爻安静，以本卦卦意和世应用神为主，不取变爻之象。' };
    if (count === 5) return { specialPattern: '独静卦', specialAdvice: '五爻俱动，一爻独静。常见取法以独静爻为关键，同时兼看变卦所示趋势。' };
    if (count === 6) {
      if (name === '乾为天') return { specialPattern: '乾卦用九', specialAdvice: '乾卦六爻皆动，宜以用九“见群龙无首，吉”为主，兼参之卦总势。' };
      if (name === '坤为地') return { specialPattern: '坤卦用六', specialAdvice: '坤卦六爻皆动，宜以用六“利永贞”为主，兼参之卦总势。' };
      return { specialPattern: '全动卦', specialAdvice: '六爻全动，宜总观本卦与变卦气势，不宜按常规逐爻细碎分断。' };
    }
    return {};
  }

  // 动变关系
  var ADV = { 亥: '子', 寅: '卯', 巳: '午', 申: '酉', 丑: '辰', 辰: '未', 未: '戌' };
  var RET = { 子: '亥', 卯: '寅', 午: '巳', 酉: '申', 辰: '丑', 未: '辰', 戌: '未' };
  function getChangeDirection(a, b) {
    if (ADV[a] === b) return '化进神';
    if (RET[a] === b) return '化退神';
    return null;
  }
  function getChangeRelations(ow, cw, ob, cb, cv) {
    var rel = isSheng(cw, ow) ? '回头生' : isKe(cw, ow) ? '回头克' : (ow === cw ? '比和' : isSheng(ow, cw) ? '化泄' : isKe(ow, cw) ? '化耗' : '比和');
    var arr = isLiuchong(ob, cb) ? ['回头冲', rel] : [rel];
    if (cv) arr.push('化空');
    return arr;
  }

  // 整卦六合/六冲
  function getHexagramRelation(name) {
    var b = hexagramNaJia[name];
    if (!b) return null;
    var pairs = [[0, 3], [1, 4], [2, 5]];
    if (pairs.every(function (p) { return isLiuhe(b[p[0]], b[p[1]]); })) return '六合卦';
    if (pairs.every(function (p) { return isLiuchong(b[p[0]], b[p[1]]); })) return '六冲卦';
    return null;
  }
  var FANYIN_PAIRS = { 乾: '巽', 巽: '乾', 坎: '离', 离: '坎', 震: '兑', 兑: '震', 坤: '艮', 艮: '坤' };
  function getFanFuRelations(oName, cName, hasChanging) {
    if (!hasChanging) return { fanyin: [], fuyin: [], labels: [] };
    var o = HEX_BY_NAME[oName], c = HEX_BY_NAME[cName];
    var ob = hexagramNaJia[oName], cb = hexagramNaJia[cName];
    var lowY = ob.slice(0, 3).every(function (b, i) { return isLiuchong(b, cb[i]); });
    var upY = ob.slice(3).every(function (b, i) { return isLiuchong(b, cb[i + 3]); });
    var lowG = FANYIN_PAIRS[o.lower] === c.lower, upG = FANYIN_PAIRS[o.upper] === c.upper;
    var fanyin = [];
    var lk = lowY ? '爻反吟' : lowG ? '卦反吟' : null;
    var uk = upY ? '爻反吟' : upG ? '卦反吟' : null;
    if (lk && uk && lk === uk) fanyin.push({ kind: lk, scope: '内外', label: lk === '爻反吟' ? '内外爻反吟' : '内外反吟' });
    else {
      if (lk) fanyin.push({ kind: lk, scope: '内卦', label: lk === '爻反吟' ? '内卦爻反吟' : '内卦反吟' });
      if (uk) fanyin.push({ kind: uk, scope: '外卦', label: uk === '爻反吟' ? '外卦爻反吟' : '外卦反吟' });
    }
    var lowerFuyin = o.lower !== c.lower && ob.slice(0, 3).every(function (b, i) { return b === cb[i]; });
    var upperFuyin = o.upper !== c.upper && ob.slice(3).every(function (b, i) { return b === cb[i + 3]; });
    var fuyin = [];
    if (lowerFuyin && upperFuyin) fuyin.push({ kind: '伏吟', scope: '内外', label: '内外伏吟' });
    else if (lowerFuyin) fuyin.push({ kind: '伏吟', scope: '内卦', label: '内卦伏吟' });
    else if (upperFuyin) fuyin.push({ kind: '伏吟', scope: '外卦', label: '外卦伏吟' });
    return { fanyin: fanyin, fuyin: fuyin, labels: fanyin.concat(fuyin).map(function (x) { return x.label; }) };
  }

  // 飞伏
  function buildHiddenSpirits(name, palace, yaosDetail, voids) {
    var home = palaceHexagrams[palace.name][0];
    if (!home || home === name) return [];
    var appeared = {}; yaosDetail.forEach(function (y) { appeared[y.sixRelative] = true; });
    var homeInfo = getNaJiaAndLiuQin(home, palace);
    return homeInfo.map(function (hy, i) {
      var under = yaosDetail[i];
      var isHiddenVoid = voids.indexOf(hy.dizhi) >= 0;
      return {
        sixRelative: hy.liuqin, position: i + 1, najiaDizhi: hy.dizhi, wuxing: hy.wuxing, isVoid: isHiddenVoid,
        underYao: { position: under.position, sixRelative: under.sixRelative, najiaDizhi: under.najiaDizhi, wuxing: under.wuxing },
      };
    }).filter(function (it) { return !appeared[it.sixRelative]; });
  }

  global.LiuYaoEngine = { generateLiuyao: generateLiuyao, getXunKong: getXunKong, getSixAnimals: getSixAnimals, HEX_BY_NAME: HEX_BY_NAME, hexagramNaJia: hexagramNaJia, palaceHexagrams: palaceHexagrams };
})(typeof window !== 'undefined' ? window : this);
