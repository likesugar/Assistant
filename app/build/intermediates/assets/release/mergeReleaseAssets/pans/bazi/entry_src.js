import { Solar, Lunar } from 'lunar-javascript';

// ===== 原版 BaZiEngine (寻壸观吉 1.8.0) JS 移植 =====
const GAN=['甲','乙','丙','丁','戊','己','庚','辛','壬','癸'];
const DIZHI=['子','丑','寅','卯','辰','巳','午','未','申','酉','戌','亥'];
const WX=['木','火','土','金','水'];
const ZHI_WUXING={'子':'水','丑':'土','寅':'木','卯':'木','辰':'土','巳':'火','午':'火','未':'土','申':'金','酉':'金','戌':'土','亥':'水'};
const CANGGAN={'子':['癸'],'丑':['己','癸','辛'],'寅':['甲','丙','戊'],'卯':['乙'],'辰':['戊','乙','癸'],'巳':['丙','戊','庚'],'午':['丁','己'],'未':['己','丁','乙'],'申':['庚','壬','戊'],'酉':['辛'],'戌':['戊','辛','丁'],'亥':['壬','甲']};
const NAYIN={'甲子':'海中金','乙丑':'海中金','丙寅':'炉中火','丁卯':'炉中火','戊辰':'大林木','己巳':'大林木','庚午':'路旁土','辛未':'路旁土','壬申':'剑锋金','癸酉':'剑锋金','甲戌':'山头火','乙亥':'山头火','丙子':'涧下水','丁丑':'涧下水','戊寅':'城头土','己卯':'城头土','庚辰':'白蜡金','辛巳':'白蜡金','壬午':'杨柳木','癸未':'杨柳木','甲申':'泉中水','乙酉':'泉中水','丙戌':'屋上土','丁亥':'屋上土','戊子':'霹雳火','己丑':'霹雳火','庚寅':'松柏木','辛卯':'松柏木','壬辰':'长流水','癸巳':'长流水','甲午':'砂石金','乙未':'砂石金','丙申':'山下火','丁酉':'山下火','戊戌':'平地木','己亥':'平地木','庚子':'壁上土','辛丑':'壁上土','壬寅':'金箔金','癸卯':'金箔金','甲辰':'覆灯火','乙巳':'覆灯火','丙午':'天河水','丁未':'天河水','戊申':'大驿土','己酉':'大驿土','庚戌':'钗钏金','辛亥':'钗钏金','壬子':'桑柘木','癸丑':'桑柘木','甲寅':'大溪水','乙卯':'大溪水','丙辰':'沙中土','丁巳':'沙中土','戊午':'天上火','己未':'天上火','庚申':'石榴木','辛酉':'石榴木','壬戌':'大海水','癸亥':'大海水'};
const wxGan=(g)=>['木','木','火','火','土','土','金','金','水','水'][GAN.indexOf(g)];
const shengWo=(w)=>({'木':'水','火':'木','土':'火','金':'土','水':'金'})[w]; // 生我
const woSheng=(w)=>({'木':'火','火':'土','土':'金','金':'水','水':'木'})[w]; // 我生
const keWo=(w)=>({'木':'金','火':'水','土':'木','金':'火','水':'土'})[w];   // 克我
const woKe=(w)=>({'木':'土','火':'金','土':'水','金':'木','水':'火'})[w];   // 我克
// 十神 (原版 getTenGod: 同性偏/异性正)
const SHISHEN_MAP={};
for(const a of GAN) for(const b of GAN){
  const wa=wxGan(a), wb=wxGan(b);
  const same=(GAN.indexOf(a)%2===0)===(GAN.indexOf(b)%2===0);
  let rel;
  if(a===b) rel='比肩';
  else if(wa===wb) rel=same?'比肩':'劫财';
  else if(woSheng(wa)===wb) rel=same?'食神':'伤官';
  else if(shengWo(wa)===wb) rel=same?'偏印':'正印';
  else if(woKe(wa)===wb) rel=same?'偏财':'正财';
  else rel=same?'七杀':'正官';
  SHISHEN_MAP[a+b]=rel;
}
const shen=(dm,g)=>g===dm?'比肩':(SHISHEN_MAP[dm+g]||'');
// 十二长生 (原版: 阳干顺阴干逆, 长生起支)
const CS_START={'甲':'亥','乙':'午','丙':'寅','丁':'酉','戊':'寅','己':'酉','庚':'巳','辛':'子','壬':'申','癸':'卯'};
const CS_NAME=['长生','沐浴','冠带','临官','帝旺','衰','病','死','墓','绝','胎','养'];
function changSheng(stem, branch){
  const yang=GAN.indexOf(stem)%2===0;
  let start=CS_START[stem];
  if(!yang) start=({'木':'午','火':'酉','土':'子','金':'卯','水':'子'})[wxGan(stem)];
  const si=DIZHI.indexOf(start);
  const di=DIZHI.indexOf(branch);
  const off=((di-si)* (yang?1:-1) + 12*2)%12;
  return CS_NAME[off];
}
// 神煞 (原版完整表)
const SS_TIAN_YI={'甲':['丑','未'],'戊':['丑','未'],'庚':['丑','未'],'乙':['子','申'],'己':['子','申'],'丙':['亥','酉'],'丁':['亥','酉'],'壬':['卯','巳'],'辛':['寅','午'],'癸':['卯','巳']};
const SS_TAIJI={'甲':['子','午'],'乙':['子','午'],'丙':['卯','酉'],'丁':['卯','酉'],'戊':['辰','戌','丑','未'],'己':['辰','戌','丑','未'],'庚':['寅','亥'],'辛':['寅','亥'],'壬':['巳','申'],'癸':['巳','申']};
const SS_WENCHANG={'甲':'巳','乙':'午','丙':'申','戊':'申','丁':'酉','己':'酉','庚':'亥','辛':'子','壬':'寅','癸':'卯'};
const SS_LU={'甲':'寅','乙':'卯','丙':'巳','戊':'巳','丁':'午','己':'午','庚':'申','辛':'酉','壬':'亥','癸':'子'};
const SS_YANGREN={'甲':'卯','乙':'寅','丙':'午','戊':'午','丁':'巳','己':'巳','庚':'酉','辛':'申','壬':'子','癸':'亥'};
const SS_FEIREN={'甲':'酉','乙':'申','丙':'子','戊':'子','丁':'亥','己':'亥','庚':'卯','辛':'寅','壬':'午','癸':'巳'};
const SS_GUOYIN={'甲':'戌','乙':'亥','丙':'丑','戊':'丑','丁':'寅','己':'寅','庚':'辰','辛':'巳','壬':'未','癸':'申'};
const SS_FUXING={'甲':'寅','乙':'丑','丙':'子','丁':'戌','戊':'申','己':'未','庚':'午','辛':'巳','壬':'辰','癸':'卯'};
const SS_TIANCHU={'甲':'巳','己':'巳','乙':'午','庚':'午','丙':'申','辛':'申','丁':'酉','壬':'酉','戊':'亥','癸':'亥'};
const SS_HONGYAN={'甲':'午','乙':'午','丙':'寅','丁':'未','戊':'辰','己':'辰','庚':'戌','辛':'酉','壬':'子','癸':'申'};
const SS_XUEEN={'甲':'酉','乙':'申','丙':'子','丁':'亥','戊':'子','己':'亥','庚':'卯','辛':'寅','壬':'午','癸':'巳'};
const SS_YUEDE={'寅':'丙','午':'丙','戌':'丙','申':'壬','子':'壬','辰':'壬','亥':'甲','卯':'甲','未':'甲','巳':'庚','酉':'庚','丑':'庚'};
const SS_TIADE={'寅':'丁','辰':'壬','巳':'辛','未':'甲','申':'癸','戌':'丙','亥':'乙','丑':'庚'};
const SS_DEXIU={'寅':'丙','午':'丙','戌':'丙','申':'庚','子':'庚','辰':'庚','亥':'甲','卯':'甲','未':'甲','巳':'丙','酉':'丙','丑':'丙'};
const SS_DEXIU2={'寅':'戊','午':'戊','戌':'戊','申':'庚','子':'庚','辰':'庚','亥':'甲','卯':'甲','未':'甲','巳':'丙','酉':'丙','丑':'丙'};
const SS_YIMA={'申':'寅','子':'寅','辰':'寅','寅':'申','午':'申','戌':'申','亥':'巳','卯':'巳','未':'巳','巳':'亥','酉':'亥','丑':'亥'};
const SS_JIANGXING={'寅':'午','午':'午','戌':'午','申':'子','子':'子','辰':'子','巳':'丑','酉':'丑','丑':'丑','亥':'卯','卯':'卯','未':'卯'};
const SS_HUAGAI={'寅':'戌','午':'戌','戌':'戌','申':'辰','子':'辰','辰':'辰','巳':'丑','酉':'丑','丑':'丑','亥':'未','卯':'未','未':'未'};
const SS_TAOHUA={'申':'酉','子':'酉','辰':'酉','寅':'卯','午':'卯','戌':'卯','巳':'午','酉':'午','丑':'午','亥':'子','卯':'子','未':'子'};
const SS_JIESHA={'申':'巳','子':'巳','辰':'巳','寅':'亥','午':'亥','戌':'亥','亥':'申','卯':'申','未':'申','巳':'寅','酉':'寅','丑':'寅'};
const SS_ZAISHA={'申':'午','子':'午','辰':'午','寅':'子','午':'子','戌':'子','亥':'卯','卯':'卯','未':'卯','巳':'卯','酉':'卯','丑':'卯'};
const SS_WANGSHEN={'申':'亥','子':'亥','辰':'亥','寅':'巳','午':'巳','戌':'巳','亥':'寅','卯':'寅','未':'寅','巳':'申','酉':'申','丑':'申'};
const SS_GUCHEN={'寅':'巳','卯':'巳','辰':'巳','巳':'申','午':'申','未':'申','申':'亥','酉':'亥','戌':'亥','亥':'寅','子':'寅','丑':'寅'};
const SS_GUAXU={'寅':'戌','卯':'戌','辰':'戌','巳':'辰','午':'辰','未':'辰','申':'未','酉':'未','戌':'未','亥':'戌','子':'戌','丑':'戌'};
const SS_YUANCHEN={'申':'辰','子':'辰','辰':'辰','寅':'寅','午':'寅','戌':'寅','亥':'巳','卯':'巳','未':'巳','巳':'申','酉':'申','丑':'申'};
const SS_GOU={'子':'午','丑':'未','寅':'申','卯':'酉','辰':'戌','巳':'亥','午':'子','未':'丑','申':'寅','酉':'卯','戌':'辰','亥':'巳'};
const SS_JIAO={'子':'丑','丑':'子','寅':'亥','卯':'戌','辰':'酉','巳':'申','午':'未','未':'午','申':'巳','酉':'辰','戌':'卯','亥':'寅'};
const PILLAR_SETS=[['庚辰','壬辰','戊戌','庚戌','魁罡','凶煞'],['甲辰','乙亥','丙辰','丁酉','戊午','庚戌','庚寅','辛亥','壬寅','癸未','十灵日','学问'],['庚申','辛酉','壬子','癸亥','甲寅','乙卯','丙午','丁巳','四废日','凶煞'],['丁酉','戊子','己卯','壬午','癸巳','丙午','丁未','壬子','癸卯','九丑日','婚姻'],['甲寅','乙卯','丙午','丁巳','戊午','己未','庚申','辛酉','壬子','癸丑','孤鸾煞','婚姻'],['甲寅','乙卯','丙巳','丁未','戊申','己酉','庚子','辛丑','壬寅','八专日','婚姻'],['丙子','丁丑','戊寅','辛卯','壬辰','癸巳','丙午','丁未','戊申','辛酉','壬戌','癸亥','阴阳差错','婚姻'],['甲辰','乙巳','丙申','丁亥','戊戌','己丑','庚辰','辛巳','壬申','癸亥','十恶大败','凶煞']];

function calcShenSha(sz){
  const out=[];
  const push=(name,zhi,type)=>{ if(zhi) out.push({name, zhi, type}); };
  const yZ=sz[0][1], mZ=sz[1][1], dZ=sz[2][1], hZ=sz[3][1];
  const yG=sz[0][0], mG=sz[1][0], dG=sz[2][0], hG=sz[3][0];
  const srcZhis=[yZ,dZ];
  const allZhis=[yZ,mZ,dZ,hZ];
  const srcStems=[yG,dG];
  // 原版: 年干/日干查, 命中的禄支/文昌支等直接以该支为标注(不论是否在四柱中)
  const checkStemToBranch=(map,name,type)=>{ for(const g of srcStems){ const l=map[g]||[]; for(const z of l){ push(name,z,type); } } };
  const checkStemToSingle=(map,name,type)=>{ for(const g of srcStems){ if(map[g]) push(name,map[g],type); } };
  const checkBranchToBranch=(map,name,type)=>{ for(const src of srcZhis){ const t=map[src]; if(t && allZhis.includes(t)) push(name,t,type); } };
  checkStemToBranch(SS_TIAN_YI,'天乙贵人','贵人');
  checkStemToBranch(SS_TAIJI,'太极贵人','贵人');
  checkStemToSingle(SS_WENCHANG,'文昌贵人','贵人');
  checkStemToSingle(SS_LU,'禄神','官禄');
  checkStemToSingle(SS_YANGREN,'羊刃','凶煞');
  checkStemToSingle(SS_FEIREN,'飞刃','凶煞');
  checkStemToSingle(SS_GUOYIN,'国印贵人','贵人');
  checkStemToSingle(SS_FUXING,'福星贵人','贵人');
  checkStemToSingle(SS_TIANCHU,'天厨','贵人');
  checkStemToSingle(SS_HONGYAN,'红艳','婚姻');
  checkStemToSingle(SS_XUEEN,'血刃','凶煞');
  if(SS_YUEDE[mZ] && (SS_YUEDE[mZ]===yG||SS_YUEDE[mZ]===dG)) push('月德贵人',mZ,'贵人');
  if(SS_TIADE[mZ] && (SS_TIADE[mZ]===yG||SS_TIADE[mZ]===dG)) push('天德贵人',mZ,'贵人');
  if(SS_TIADE[mZ+'']===undefined && ({卯:'申',午:'亥',子:'巳',酉:'寅'})[mZ]) push('天德贵人',({卯:'申',午:'亥',子:'巳',酉:'寅'})[mZ],'贵人');
  if(SS_DEXIU[mZ] && (SS_DEXIU[mZ]===yG||SS_DEXIU[mZ]===dG)) push('德秀贵人',mZ,'贵人');
  if(SS_DEXIU2[mZ] && (SS_DEXIU2[mZ]===yG||SS_DEXIU2[mZ]===dG)) push('德秀贵人',mZ,'贵人');
  checkBranchToBranch(SS_YIMA,'驿马','驿马');
  checkBranchToBranch(SS_JIANGXING,'将星','官禄');
  checkBranchToBranch(SS_HUAGAI,'华盖','学问');
  checkBranchToBranch(SS_TAOHUA,'桃花','桃花');
  checkBranchToBranch(SS_JIESHA,'劫煞','凶煞');
  checkBranchToBranch(SS_ZAISHA,'灾煞','凶煞');
  checkBranchToBranch(SS_WANGSHEN,'亡神','凶煞');
  checkBranchToBranch(SS_GUCHEN,'孤辰','婚姻');
  checkBranchToBranch(SS_GUAXU,'寡宿','婚姻');
  checkBranchToBranch(SS_YUANCHEN,'元辰','凶煞');
  if(SS_GOU[dZ]) push('勾煞',SS_GOU[dZ],'凶煞');
  if(SS_JIAO[dZ]) push('绞煞',SS_JIAO[dZ],'凶煞');
  for(const set of PILLAR_SETS){
    const gzList=set.slice(0,-2), name=set[set.length-2], type=set[set.length-1];
    for(const p of sz){ if(gzList.includes(p[0]+p[1])) push(name,p[1],type); }
  }
  const seen=new Set();
  return out.filter(c=>{const k=c.name+c.zhi; if(seen.has(k))return false; seen.add(k); return true;});
}
function estimateLongitude(region){
  const m={'北京':116.4,'天津':117.2,'上海':121.5,'重庆':106.5,'石家庄':114.5,'唐山':118.2,'保定':115.5,'邯郸':114.5,'太原':112.5,'大同':113.3,'沈阳':123.4,'大连':121.6,'长春':125.3,'吉林':126.5,'哈尔滨':126.6,'齐齐哈尔':123.9,'南京':118.8,'苏州':120.6,'无锡':120.3,'常州':119.9,'徐州':117.2,'杭州':120.2,'宁波':121.6,'温州':120.7,'合肥':117.3,'芜湖':118.4,'蚌埠':117.4,'福州':119.3,'厦门':118.1,'泉州':118.6,'南昌':115.9,'九江':116.0,'赣州':114.9,'济南':117.0,'青岛':120.4,'烟台':121.4,'潍坊':119.1,'郑州':113.7,'洛阳':112.5,'开封':114.3,'南阳':112.5,'武汉':114.3,'宜昌':111.3,'襄阳':112.1,'长沙':113.0,'株洲':113.1,'衡阳':112.6,'广州':113.3,'深圳':114.1,'珠海':113.6,'东莞':113.7,'佛山':113.1,'汕头':116.7,'南宁':108.3,'桂林':110.3,'海口':110.3,'三亚':109.5,'成都':104.1,'绵阳':104.8,'贵阳':106.7,'遵义':106.9,'昆明':102.7,'大理':100.2,'拉萨':91.1,'西安':108.9,'咸阳':108.7,'宝鸡':107.1,'兰州':103.8,'西宁':101.8,'银川':106.3,'乌鲁木齐':87.6,'呼和浩特':111.7,'包头':109.8,'香港':114.2,'澳门':113.5,'台北':121.5,'高雄':120.3};
  for(const k of Object.keys(m)) if(region && region.includes(k)) return m[k];
  return 120.0;
}
function compute(cfg){
  let y=cfg.y,mo=cfg.mo,d=cfg.d,h=cfg.h,mi=cfg.mi;
  if(cfg.isLunar){
    const s2=Lunar.fromYmdHms(y,mo,d).getSolar(); y=s2.getYear(); mo=s2.getMonth(); d=s2.getDay();
  }
  if(cfg.xiaLing) h=Math.max(0,h-1);
  if(cfg.zhenTaiYang && cfg.lon!=null){
    const offMin=(cfg.lon-120)*4;
    let t=h*60+mi+offMin;
    if(t<0){t+=1440;d-=1;} if(t>=1440){t-=1440;d+=1;}
    h=Math.floor(t/60); mi=t%60;
  }
  const lunar=Solar.fromYmdHms(y,mo,d,h,mi,0).getLunar();
  const ec=lunar.getEightChar();
  if(cfg.zaoWanZi) ec.setSect(2);
  const sz=[[ec.getYearGan(),ec.getYearZhi()],[ec.getMonthGan(),ec.getMonthZhi()],[ec.getDayGan(),ec.getDayZhi()],[ec.getTimeGan(),ec.getTimeZhi()]];
  const dm=sz[2][0];
  // 五行统计: 天干4 + 支本气4 + 藏干全9
  const wxCount={'金':0,'木':0,'水':0,'火':0,'土':0};
  const addW=(w)=>{if(w&&wxCount[w]!=null)wxCount[w]++;};
  sz.forEach(p=>{addW(wxGan(p[0])); addW(ZHI_WUXING[p[1]]); (CANGGAN[p[1]]||[]).forEach(g=>addW(wxGan(g)));});
  // 旺衰评分(原版)
  const monthBranchWx=ZHI_WUXING[sz[1][1]], monthStemWx=wxGan(sz[1][0]);
  const dmWx=wxGan(dm);
  let score=(monthBranchWx===dmWx?3:0)+(monthStemWx===dmWx?2:0)+(monthBranchWx===shengWo(dmWx)?2:0)+(monthStemWx===shengWo(dmWx)?1:0)+wxCount[dmWx];
  let strength = score>=8?'身旺':(score>=6?'偏旺':(score>=4?'中和':(score>=2?'偏弱':'身弱')));
  // 喜用/忌神 (原版 determineXiYong/determineJiShen)
  let xi,ji;
  if(strength==='身旺'||strength==='偏旺'){ xi=[woSheng(dmWx),keWo(dmWx),woKe(dmWx)]; ji=[shengWo(dmWx),dmWx]; }
  else if(strength==='中和'){ xi=[dmWx,shengWo(dmWx)]; ji=[keWo(dmWx)]; }
  else { xi=[shengWo(dmWx),dmWx]; ji=[keWo(dmWx),woSheng(dmWx)]; }
  // 空亡
  const i=GAN.indexOf(sz[2][0]), j=DIZHI.indexOf(sz[2][1]);
  const idx=(j-i+60)%10;
  const XUN_KONG={'甲子':['戌','亥'],'甲戌':['申','酉'],'甲申':['午','未'],'甲午':['辰','巳'],'甲辰':['寅','卯'],'甲寅':['子','丑']};
  const kw=(XUN_KONG['甲'+DIZHI[idx]]||[]).join(' ');
  // 大运: getYun(gender, sect=2), 岁=startYear-birthYear
  const yun=ec.getYun(cfg.gender==='男'?1:0, 2);
  const daYun=yun.getDaYun().map((dy,idx2)=>({raw:dy,
    gz: dy.getGanZhi()||(idx2===0?sz[1][0]+sz[1][1]:''),
    startAge: idx2===0?0:(dy.getStartYear()-y),
    endAge: idx2===0?(dy.getEndYear()-y):(dy.getEndYear()-y),
    startYear: dy.getStartYear(), liuNian: (dy.getLiuNian?dy.getLiuNian():[]).map(ln=>({year:ln.getYear(),gz:ln.getGanZhi()}))
  }));
  // 起运
  const sy=yun.getStartYear(), smo=yun.getStartMonth(), sd=yun.getStartDay(), sh2=yun.getStartHour();
  let qd='出生后';
  if(sy>0) qd+=sy+'年'; if(smo>0) qd+=smo+'月'; if(sd>0) qd+=sd+'天'; if(sh2>0) qd+=sh2+'时辰';
  qd+='起运';
  const qiYunAge=Math.min(Math.max(sy+1,1),10);
  // 命宫/胎元/身宫 (原版公式)
  const toTrad=(x)=>((x+10)%12)+1;
  const fromTrad=(x)=>(x+1)%12;
  const mT=toTrad(DIZHI.indexOf(sz[1][1])), hT=toTrad(DIZHI.indexOf(sz[3][1]));
  let mg=(14-mT-hT); if(mg<1) mg+=12;
  const mingZhi=DIZHI[fromTrad(mg)];
  let i2=mT+hT-1; if(i2>12) i2=mT+hT-13;
  const shenZhi=DIZHI[fromTrad(i2)];
  const taiGan=GAN[(GAN.indexOf(sz[1][0])+1)%10], taiZhi=DIZHI[(DIZHI.indexOf(sz[1][1])+3)%12];
  const base=[2,4,6,8,0][GAN.indexOf(sz[0][0])%5];
  const stemFor=(bz)=>GAN[(base+((DIZHI.indexOf(bz)+10)%12))%10];
  const mingGong=stemFor(mingZhi)+mingZhi, shenGong=stemFor(shenZhi)+shenZhi;
  // 格局
  const monthHidden=CANGGAN[sz[1][1]]||[];
  let geTen=shen(dm,sz[1][0]);
  if(!monthHidden.includes(sz[1][0])) geTen=shen(dm,monthHidden[0]);
  const geju=({正官:'正官格',七杀:'七杀格',正印:'正印格',偏印:'偏印格',正财:'正财格',偏财:'偏财格',食神:'食神格',伤官:'伤官格',比肩:'建禄格',劫财:'羊刃格'})[geTen]||'';
  // 流月 (小寒起, drop1+take1 → 立春起)
  const startIdx=((GAN.indexOf(lunar.getYearInGanZhi()[0])%5)*2+2)%10;
  const jies=['小寒','立春','惊蛰','清明','立夏','芒种','小暑','立秋','白露','寒露','立冬','大雪'];
  const months=[];
  for(let n=1;n<=12;n++){
    months.push({num:n, jie:jies[n-1], gz:GAN[((n+10)%12+startIdx)%10]+DIZHI[n%12]});
  }
  const liuYue=months.slice(1).concat([months[0]]);
  const pillars=sz.map((p,idx2)=>({gan:p[0],zhi:p[1],gz:p[0]+p[1],shiShen:idx2===2?'日主':shen(dm,p[0]),cangGan:(CANGGAN[p[1]]||[]).join(''),naYin:NAYIN[p[0]+p[1]]||'',kongWang:xunKong(p[0]+p[1]),xing:changSheng(dm,p[1]),cangShi:({})}));
  const shenSha=calcShenSha(sz);
  return {
    pillars, wxCount, strength, score, xi, ji, geju, shenSha,
    mingGong, shenGong, taiYuan: taiGan+taiZhi,
    qiYunRaw: qd, qiYunAge, daYun, liuYue,
    xiaoYun: (()=>{ try{ const dy0=daYun.find(d=>d.gz&&d.raw)||null; const dy1=daYun[1]; const raw=dy1&&dy1.raw; if(!raw) return []; return (raw.getXiaoYun(y, y+10, cfg.gender==='男'?1:0)||[]).map(x=>({year:x.getYear(), gz:x.getGanZhi(), age:x.getAge()})); }catch(e){ return []; } })(),
    solarYear: y,
    solarCN: y+'年'+mo+'月'+d+'日 '+h+'时'+(mi<10?'0':'')+mi+'分',
    lunarCN: '农历'+lunar.getMonthInChinese()+'月'+lunar.getDayInChinese()+' '+lunar.getTimeZhi()+'时',
    lunar: '农历'+lunar.getMonthInChinese()+'月'+lunar.getDayInChinese()+' '+lunar.getTimeZhi()+'时',
    solar: y+'-'+mo+'-'+d+' '+h+':'+mi,
    ganzhi: lunar.getYearInGanZhi()+'年 '+lunar.getMonthInGanZhiExact()+'月 '+lunar.getDayInGanZhi()+'日 '+lunar.getTimeInGanZhi()+'时',
    gender: cfg.gender
  };
}
function xunKong(gz){
  const i=GAN.indexOf(gz[0]), j=DIZHI.indexOf(gz[1]);
  const idx=(j-i+60)%10;
  const XUN_KONG={'甲子':['戌','亥'],'甲戌':['申','酉'],'甲申':['午','未'],'甲午':['辰','巳'],'甲辰':['寅','卯'],'甲寅':['子','丑']};
  return (XUN_KONG['甲'+DIZHI[idx]]||[]).join(' ');
}
function esc(s){return String(s==null?'':s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');}


// ===== 注入样式 (原版风格) =====
(function(){
  const css=`.tmsBox{display:flex;flex-direction:column;align-items:center;gap:4px}.tmsLbl{font-size:11px;color:#8a8f9c}.sw{width:46px;height:26px;border-radius:14px;background:#262a35;position:relative;cursor:pointer;transition:.2s}.sw.on{background:#c9a45a}.sw .knob{position:absolute;top:3px;left:3px;width:20px;height:20px;border-radius:50%;background:#fff;transition:.2s}.sw.on .knob{left:23px}.segToggle{display:flex;border:1px solid #3a3325;border-radius:14px;overflow:hidden}.seg{padding:4px 14px;font-size:12px;color:#8a8f9c;cursor:pointer}.seg.sel{background:#c9a45a;color:#1a1408;font-weight:600}.qiYunLine{font-size:13px;color:#8a8f9c;margin-bottom:10px}.dyRow2{display:flex;gap:10px;overflow-x:auto;padding-bottom:8px}.dyCard2{min-width:60px;flex-shrink:0;border:1px solid #262a35;border-radius:12px;padding:10px 6px;text-align:center;cursor:pointer}.dyCard2.sel{border-color:#c9a45a;background:#241d10}.dyGz2{font-size:18px;font-weight:600}.dyAge2{font-size:11px;color:#9aa0ad;margin-top:3px}.dyYear{font-size:10px;color:#6b7180}.dyBigCard{display:flex;align-items:center;gap:16px;background:#171921;border-radius:14px;padding:14px 18px;margin:10px 0}.dyBigGz{font-size:34px;font-weight:700}.dyBigInfo{flex:1}.dyBigAge{font-size:14px;color:#d9d5c8}.dyBigYear{font-size:12px;color:#6b7180}.dyBigSS{display:flex;gap:14px;font-size:13px;color:#e8c87a}.lnGrid{display:grid;grid-template-columns:repeat(5,1fr);gap:8px;margin-top:8px}.lnCard{background:#171921;border:1px solid #262a35;border-radius:12px;padding:8px 2px;text-align:center}.lnCard.cur{border-color:#c9a45a;background:#241d10}.lnYear{font-size:12px;color:#9aa0ad}.lnGz{font-size:16px;font-weight:600;line-height:1.3;margin-top:2px}.wxLine{display:flex;align-items:center;gap:12px;padding:8px 0}.wxName{font-size:16px;font-weight:600;width:20px;text-align:center}.bar2{flex:1;height:10px;background:#22252f;border-radius:5px;overflow:hidden}.fill2{height:100%;border-radius:5px}.wxCount{font-size:15px;font-weight:600;width:20px;text-align:right}.chRow{padding:7px 0;font-size:13px;border-bottom:1px solid #22252f;display:flex;gap:8px;align-items:center}.chRow:last-child{border-bottom:0}.badge2{border-radius:10px;padding:2px 10px;font-size:12px;font-weight:600}.badge2.ok{background:#12321f;color:#4caf50}.badge2.bad{background:#321a12;color:#e2564e}.badge2.gold{background:#2a2317;color:#e8c87a}.csGrid{background:#22252f;border-radius:10px;padding:8px 6px;margin-bottom:10px}.csRow2{display:flex;justify-content:space-between;font-size:13px}.csRow2 span{flex:1;text-align:center}.pal3{display:flex;text-align:center}.pal3>div{flex:1;padding:6px 0}.pal3>div+div{border-left:1px solid #22252f}.palGz{font-size:22px;font-weight:700;margin:6px 0}.gejuDesc{font-size:15px;color:#d9d5c8;line-height:1.8;margin-top:6px}.chip.good{background:#12321f;color:#4caf50;border-color:#1d4a2e}.chip.bad{background:#321a12;color:#e2564e;border-color:#4a221f}.chip.gold{background:#2a2317;color:#e8c87a;border-color:#5a4a28}`;
  const st=document.createElement('style');st.textContent=css;document.head.appendChild(st);
})();

const WX_COLOR={'木':'#4caf50','火':'#e2564e','土':'#e8912d','金':'#d4b23a','水':'#2196f3'};
const LIU_HE={'子':'丑','丑':'子','寅':'亥','亥':'寅','卯':'戌','戌':'卯','辰':'酉','酉':'辰','巳':'申','申':'巳','午':'未','未':'午'};
const LIU_CHONG={'子':'午','午':'子','丑':'未','未':'丑','寅':'申','申':'寅','卯':'酉','酉':'卯','辰':'戌','戌':'辰','巳':'亥','亥':'巳'};
const LIU_HAI={'子':'未','未':'子','丑':'午','午':'丑','寅':'巳','巳':'寅','卯':'辰','辰':'卯','申':'亥','亥':'申','酉':'戌','戌':'酉'};
const GAN_HE={'甲':'己','己':'甲','乙':'庚','庚':'乙','丙':'辛','辛':'丙','丁':'壬','壬':'丁','戊':'癸','癸':'戊'};
const SHENGXIAO={'子':'鼠','丑':'牛','寅':'虎','卯':'兔','辰':'龙','巳':'蛇','午':'马','未':'羊','申':'猴','酉':'鸡','戌':'狗','亥':'猪'};
const ZHI_EMOJI={'子':'🐭','丑':'🐮','寅':'🐯','卯':'🐰','辰':'🐲','巳':'🐍','午':'🐴','未':'🐑','申':'🐵','酉':'🐔','戌':'🐶','亥':'🐷'};
const CHANGSHENG_DESC={'长生':'生机蓬勃，发展顺利','沐浴':'初入社会，历练成长','冠带':'渐入佳境，稳步上升','临官':'事业有成，如日中天','帝旺':'鼎盛时期，权力巅峰','衰':'由盛转衰，守成为上','病':'力有不逮，注意健康','死':'气数已尽，宜静不宜动','墓':'收藏储蓄，深藏不露','绝':'重新开始，另起炉灶','胎':'孕育新机，蓄势待发','养':'休养生息，等待时机'};
const GEJU_DESC={'正官格':'正直端方，循规蹈矩，宜公职管理','七杀格':'魄力过人，敢作敢当，宜武职创业','正印格':'聪慧仁慈，学业有成，宜文教文化','偏印格':'思维独特，直觉敏锐，宜学术宗教','正财格':'勤俭务实，财源稳定，宜经营实业','偏财格':'机变灵活，财路广博，宜商贸投资','食神格':'温和乐观，才艺出众，宜艺术餐饮','伤官格':'聪明外露，才气纵横，宜创作技术','建禄格':'自立自强，白手起家，宜技术专长','羊刃格':'意志坚定，行动果决，宜竞争行业','':''};
function coloredGan(g){const c=WX_COLOR[wxGan(g)];return `<span style="color:${c}">${g}</span>`;}
function coloredZhi(z){const c=WX_COLOR[ZHI_WUXING[z]];return `<span style="color:${c}">${z}</span>`;}
const ZHI_XING_SAN={'寅':'巳','巳':'申','申':'寅','丑':'戌','戌':'未','未':'丑','子':'卯','卯':'子'};
const ZHI_XING_SELF=['辰','午','酉','亥'];
function chongHeList(r){
  const zhis=r.pillars.map(p=>p.zhi); const gans=r.pillars.map(p=>p.gan); const out=[]; const seen=new Set();
  for(let i=0;i<zhis.length;i++) for(let j=i+1;j<zhis.length;j++){
    const a=zhis[i],b=zhis[j],pair=[a,b].sort().join('');
    if(LIU_HE[a]===b&&!seen.has('h'+pair)){out.push({type:'六合',color:'ok',pair:a+b,desc:a+b+'六合，和合有情'});seen.add('h'+pair);}
    if(LIU_CHONG[a]===b&&!seen.has('c'+pair)){out.push({type:'六冲',color:'bad',pair:a+b,desc:a+b+'相冲，动荡不安'});seen.add('c'+pair);}
    if(LIU_HAI[a]===b&&!seen.has('e'+pair)){out.push({type:'六害',color:'bad',pair:a+b,desc:a+b+'相害，暗中损伤'});seen.add('e'+pair);}
  }
  for(let i=0;i<gans.length;i++) for(let j=i+1;j<gans.length;j++){
    if(GAN_HE[gans[i]]===gans[j]&&!seen.has('g'+i+j)){out.push({type:'天干合',color:'gold',pair:gans[i]+gans[j],desc:gans[i]+gans[j]+'合'});seen.add('g'+i+j);}
  }
  for(let i=0;i<zhis.length;i++) for(let j=i+1;j<zhis.length;j++){
    const a=zhis[i],b=zhis[j],pair=[a,b].sort().join('');
    if(ZHI_XING_SAN[a]===b&&!seen.has('x'+pair)){out.push({type:'三刑',color:'bad',pair:a+b,desc:a+b+'相刑，刑法责难'});seen.add('x'+pair);}
    if(ZHI_XING_SELF.includes(a)&&a===b&&!seen.has('sx'+pair)){out.push({type:'自刑',color:'bad',pair:a+b,desc:a+'自刑，自我伤害'});seen.add('sx'+pair);}
  }
  return out;
}
function perPillarSS(r,i){
  const z=r.pillars[i].zhi;
  const list=r.shenSha.filter(x=>x.zhi===z).map(x=>x.name);
  return list.join(' ')||'—';
}
function renderResult(r){
  const monthWx=ZHI_WUXING[r.pillars[1].zhi];
  const states={}; states[monthWx]='旺'; states[woSheng(monthWx)]='相'; states[shengWo(monthWx)]='休'; states[woKe(monthWx)]='囚'; states[keWo(monthWx)]='死';
  const gz=r.pillars.map(p=>p.gz).join(' ');
  const strong=r.strength==='身旺'||r.strength==='偏旺';
  const head=`
  <div class="prof">
    <div class="avatar">${ZHI_EMOJI[r.pillars[0].zhi]||'🐴'}</div>
    <div class="pinfo">
      <div class="pname">${esc(window.__baziName||'案例1')}</div>
      <div class="pline">农历${esc(r.lunar.replace('农历',''))} <b class="gold">${r.gender==='男'?'乾造':'坤造'}</b></div>
      <div class="pdim">公历${esc(r.solarCN||r.solar)}</div>
    </div>
    <div class="tmsBox"><span class="tmsLbl">胎命身</span><div class="sw" id="tmsSw"><div class="knob"></div></div></div>
  </div>
  <div class="tabs">
    <div class="tab sel" data-t="0">基本信息</div>
    <div class="tab" data-t="1">基本排盘</div>
    <div class="tab" data-t="2">专业细盘</div>
    <div class="tab" data-t="3">断事笔记</div>
  </div>`;
  // tab0
  const order=['木','火','土','金','水'];
  const maxC=Math.max(...order.map(k=>r.wxCount[k]||0),1);
  const wxStats=order.map(k=>{
    const cnt=r.wxCount[k]||0;
    return `<div class="wxLine"><span class="wxName" style="color:${WX_COLOR[k]}">${k}</span><div class="bar2"><div class="fill2" style="width:${Math.round(cnt/maxC*100)}%;background:${WX_COLOR[k]}"></div></div><span class="wxCount" style="color:${WX_COLOR[k]}">${cnt}</span></div>`;
  }).join('');
  const ssChips=r.shenSha.map(c=>`<span class="chip ${c.type==='贵人'?'good':(c.type==='凶煞'?'bad':'gold')}">${esc(c.name)}(${esc(c.zhi)})</span>`).join('');
  const ch=chongHeList(r).map(x=>`<div class="chRow"><span class="badge2 ${x.color}">${x.type}</span><b>${x.pair}</b> <span class="dim3">${x.desc}</span></div>`).join('');
  const dm=r.pillars[2].gan;
  const dz=DIZHI.map(z=>`<span class="${z===r.pillars[2].zhi?'gold':''}">${z}</span>`).join('');
  const csRow=DIZHI.map(z=>{const s=changSheng(dm,z);const cls={'长生':'ok','临官':'ok','帝旺':'ok','墓':'gold','绝':'bad','死':'bad','病':'bad'}[s]||'dim3';return `<span class="${cls}">${s[0]}</span>`;}).join('');
  const t0=`
  <div class="pane" id="pane0">
    <div class="sec2">命局格局</div>
    <div class="card"><div class="kv"><span class="k">格局</span><span class="v gold" style="font-size:18px;font-weight:700">${esc(r.geju)}</span></div>
    <div class="gejuDesc">${esc(GEJU_DESC[r.geju]||'')}</div></div>
    <div class="sec2">五行统计</div>
    <div class="card">${wxStats}</div>
    <div class="sec2">喜用忌神</div>
    <div class="card"><div class="kv" style="align-items:flex-start"><span class="k">喜用神</span><span class="v">${r.xi.map(w=>`<span class="chip good" style="color:${WX_COLOR[w]}">${w}</span>`).join('')}</span></div>
    <div class="kv" style="align-items:flex-start"><span class="k">忌神</span><span class="v">${r.ji.map(w=>`<span class="chip bad" style="color:${WX_COLOR[w]}">${w}</span>`).join('')}</span></div>
    <div class="kv"><span class="k">旺衰</span><span class="v">${esc(r.strength)}</span></div></div>
    <div class="sec2" id="ssHead" style="cursor:pointer">神煞 <span id="ssArrow" class="dim3">▾</span></div>
    <div class="card" id="ssBody"><div class="chips">${ssChips||'-'}</div></div>
    <div class="sec2">刑冲合害</div>
    <div class="card">${ch||'<span class="dim3">无</span>'}</div>
    <div class="sec2">十二长生</div>
    <div class="card"><div class="csGrid"><div class="csRow2">${dz}</div><div class="csRow2">${csRow}</div></div>
    <div class="kv"><span class="k">日支长生</span><span class="v">${esc(r.pillars[2].xing)} · ${esc(CHANGSHENG_DESC[r.pillars[2].xing]||'')}</span></div></div>
    <div class="sec2">命宫·胎元·身宫</div>
    <div class="card"><div class="pal3">
      <div><div class="dim3">命宫</div><div class="palGz gold">${esc(r.mingGong)}</div><div class="dim3">${esc(wxGan(r.mingGong[0])+ZHI_WUXING[r.mingGong[1]])}</div><div class="dim3">精神</div></div>
      <div><div class="dim3">胎元</div><div class="palGz orange">${esc(r.taiYuan)}</div><div class="dim3">${esc(wxGan(r.taiYuan[0])+ZHI_WUXING[r.taiYuan[1]])}</div><div class="dim3">先天</div></div>
      <div><div class="dim3">身宫</div><div class="palGz gold">${esc(r.shenGong)}</div><div class="dim3">${esc(wxGan(r.shenGong[0])+ZHI_WUXING[r.shenGong[1]])}</div><div class="dim3">后天</div></div>
    </div></div>
    <div class="sec2">纳音·空亡</div>
    <div class="card">
      ${r.pillars.map((p,i)=>`<div class="kv"><span class="k">${['年柱','月柱','日柱','时柱'][i]}</span><span class="v"><b style="color:#ffd83a">${esc(p.gz)}</b> <span class="gold">${esc(p.naYin)}</span></span></div>`).join('')}
      <div class="kv"><span class="k">空亡</span><span class="v orange">${esc(r.pillars[2].kongWang)}</span></div>
    </div>
  </div>`;
  const rowClamp=(label,cells)=>`<div class="crow clampRow"><div class="cl">${label}</div>${cells.map(c=>`<div class="cv tap2">${c}</div>`).join('')}</div>`;
    const row=(label,cells)=>`<div class="crow"><div class="cl">${label}</div>${cells.map(c=>`<div class="cv">${c}</div>`).join('')}</div>`;
  const p=r.pillars;
  const chartHead=`<div class="crow head"><div class="cl"></div>${p.map((x,i)=>`<div class="cv dim2">${['年柱','月柱','日柱','时柱'][i]}</div>`).join('')}</div>`;
  const t1=`
  <div class="pane" id="pane1" style="display:none">
    <div class="chartCard">
    ${chartHead}
    ${row('十神', p.map(x=>`<span class="gold">${esc(x.shiShen)}</span>`))}
    ${row('天干', p.map(x=>coloredGan(x.gan)))}
    ${row('地支', p.map(x=>coloredZhi(x.zhi)))}
    ${row('藏干', p.map(x=>(CANGGAN[x.zhi]||[]).map(g=>coloredGan(g)+'<i class="ss2">'+shen(dm,g)+'</i>').join('<br>')))}
    ${row('纳音', p.map(x=>`<span class="dim3">${esc(x.naYin)}</span>`))}
    ${row('空亡', p.map(x=>`<span class="orange">${esc(x.kongWang)}</span>`))}
    ${row('神煞', p.map((x,i)=>`<span class="orange">${esc(perPillarSS(r,i))}</span>`))}
    ${row('星运', p.map(x=>`<span class="dim3">${esc(x.xing)}</span>`))}
    </div>
  </div>`;
  const dyCards=r.daYun.map((d,i)=>`<div class="dyCard2 ${i===0?'sel':''}" data-i="${i}"><div class="dyGz2" style="color:${d.gz?WX_COLOR[wxGan(d.gz[0])]:'#666'}">${esc(d.gz||'—')}</div><div class="dyAge2">${d.startAge}-${d.endAge}岁</div><div class="dyYear">${d.startYear?d.startYear+'-'+(d.startYear+9):''}</div></div>`).join('');
  const t2=`
  <div class="pane" id="pane2" style="display:none">
    <div class="qiYunLine">${esc(r.qiYunRaw)}</div>
    <div id="bigTableWrap"><div class="chartCard" style="overflow-x:auto"><div id="bigTable"></div></div></div>
    <div class="sec2">大运</div>
    <div class="dyRow2">${dyCards}</div>
    <div class="sec2" style="margin-top:14px">流年（点选看流年小运）</div>
    <div class="lnGrid" id="lnBar"></div>
    <div id="lnDetail"></div>
  </div>`;
  const t3=`
  <div class="pane" id="pane3" style="display:none">
    <div class="sec2">断事笔记</div>
    <div class="card"><div class="noteSummary">
      <div>四柱：${esc(gz)}</div>
      <div>日主：${esc(dm)}（${esc(wxGan(dm))}）${esc(r.strength)}</div>
      <div>格局：${esc(r.geju)}</div>
      <div>喜用：${esc(r.xi.join('、'))}　忌神：${esc(r.ji.join('、'))}</div>
    </div></div>
    <div class="noteBox"><textarea id="noteTa" placeholder="在此记录断事笔记..."></textarea></div>
    <div class="noteBtns"><button class="nb1" id="noteSave">保存</button><button class="nb2" id="noteRecords">保存记录</button></div>
    <div id="noteList"></div>
  </div>`;
  const wxBar=order.map(k=>{
    const cnt=r.wxCount[k]||0;
    const w=Math.max(8,Math.round(cnt/maxC*70));
    return `<div class="wxCol"><div class="wxTop" style="color:${WX_COLOR[k]}">${k}</div><div class="bar"><div class="fill" style="width:${w}%;background:${WX_COLOR[k]}"></div></div><div class="wxState" style="color:${WX_COLOR[k]}">${states[k]}</div></div>`;
  }).join('');
  const bottom=`<div class="bottomBar"><div class="wxBar">${wxBar}</div>
  <div class="btnRow"><button class="b1" id="btnAI">智能解读</button><button class="b2" id="btnCopy">复制排盘</button></div>
  <div id="aiPanel" style="display:none;background:#171921;border:1px solid #c9a45a;border-radius:14px;padding:12px;margin-top:10px;max-height:40vh;overflow-y:auto">
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:6px"><span style="color:#c9a45a;font-size:12px;letter-spacing:2px">AI 解读</span><span id="aiClose" style="color:#8a8f9c;cursor:pointer">✕</span></div>
    <div id="aiBody" style="font-size:13px;line-height:1.8;white-space:pre-wrap;color:#d9d5c8"></div>
  </div></div>`;
  const app=document.getElementById('app');
  app.innerHTML=head+`<div id="panes">`+t0+t1+t2+t3+`</div>`+bottom;
  app.querySelectorAll('.tab').forEach(t=>{
    t.onclick=()=>{
      app.querySelectorAll('.tab').forEach(x=>x.classList.remove('sel'));
      t.classList.add('sel');
      const n=+t.dataset.t;
      for(let k=0;k<4;k++){const pe=document.getElementById('pane'+k);if(pe)pe.style.display=k===n?'block':'none';}
      window.scrollTo(0,0);
    };
  });
  const ssHead=document.getElementById('ssHead');
  if(ssHead){ ssHead.onclick=()=>{ const b=document.getElementById('ssBody'); const open=b.style.display!=='none'; b.style.display=open?'none':'block'; document.getElementById('ssArrow').textContent=open?'▸':'▾'; }; }
  const sw=document.getElementById('tmsSw');
  window.__baziState={tms:false};
  sw.onclick=()=>{window.__baziState.tms=!window.__baziState.tms;sw.classList.toggle('on',window.__baziState.tms);if(curLn===undefined)curLn=null;renderBig();};

  let curDy=0, curLn=null;
  const renderBig=()=>{
    const d=r.daYun[curDy]||{gz:'',liuNian:[]};
    const ln=curLn||(d.liuNian||[]).find(x=>x.year===r.solarYear)||(d.liuNian||[])[0]||{gz:'',year:''};
    const tms=window.__baziState&&window.__baziState.tms;
    const dm=r.pillars[2].gan;
    const p=r.pillars;
    const colHead=(t)=>`<div class="cv dim2">${t}</div>`;
    let cols=[];
    if(tms){
      cols.push({label:'胎元',gz:r.taiYuan});
      cols.push({label:'命宫',gz:r.mingGong});
      cols.push({label:'身宫',gz:r.shenGong});
    }
    const liuCol={label:'流年',gz:ln.gz||''};
    const dyCol={label:'大运',gz:d.gz||''};
    cols=cols.concat([liuCol, dyCol]).concat(p.map((x,i2)=>({label:['年柱','月柱','日柱','时柱'][i2],gz:x.gz})));
    const G=(gz)=>gz||'';
    const row=(label,cells)=>`<div class="crow"><div class="cl">${label}</div>${cells.map((c,i)=>`<div class="cv tap" data-col="${i}">${c}</div>`).join('')}</div>`;
    document.getElementById('bigTable').innerHTML=`
      <div class="crow head"><div class="cl"></div>${cols.map(c=>colHead(c.label)).join('')}</div>
      ${row('十神', cols.map(c=>c.gz?`<span class="gold">${esc(shen(dm,c.gz[0]))}</span>`:''))}
      ${row('天干', cols.map(c=>c.gz?coloredGan(c.gz[0]):''))}
      ${row('地支', cols.map(c=>c.gz?coloredZhi(c.gz[1]):''))}
      ${row('藏干', cols.map(c=>c.gz?(CANGGAN[c.gz[1]]||[]).map(g=>coloredGan(g)+'<i class="ss2">'+esc(shen(dm,g))+'</i>').join('<br>'):''))}
      ${row('纳音', cols.map(c=>c.gz?`<span class="dim3">${esc(NAYIN[c.gz]||'')}</span>`:''))}
      ${row('空亡', cols.map(c=>c.gz?`<span class="orange">${esc(xunKong(c.gz))}</span>`:''))}
      ${rowClamp('神煞', cols.map(c=>{ if(!c.gz) return ''; const z=c.gz[1]; const l=r.shenSha.filter(x=>x.zhi===z).map(x=>x.name); return `<span class="orange">${esc(l.join(' ')||'—')}</span>`; }))}
      ${row('星运', cols.map(c=>c.gz?`<span class="dim3">${esc(c.label==='流年'||c.label==='大运'||c.label.startsWith('年柱')||c.label.startsWith('月柱')||c.label.startsWith('日柱')||c.label.startsWith('时柱')?changSheng(dm,c.gz[1]):changSheng(c.gz[0],c.gz[1]))}</span>`:''))}
      ${row('星运<sub>干</sub>', cols.map(c=>c.gz?`<span class="dim3">${esc(changSheng(c.gz[0],c.gz[1]))}</span>`:''))}
    `;
    const bt=document.getElementById('bigTable');
    bt.querySelectorAll('.clampRow').forEach(rr=>{ rr.onclick=()=>{ rr.classList.toggle('open'); }; });
    const gzAll=()=>{const tms=window.__baziState&&window.__baziState.tms; const d=r.daYun[curDy]; const ln=curLn||{};
      const pre=tms?[r.taiYuan,r.mingGong,r.shenGong]:[];
      return pre.concat([d.gz||''],[ln.gz||''],r.pillars.map(x=>x.gz));};
    bt.querySelectorAll('.cv.tap').forEach(cell=>{
      cell.onclick=()=>{
        const tms=window.__baziState&&window.__baziState.tms;
        const off=tms?3:2;
        const ci=+cell.dataset.col - off;
        const g=gzAll()[ci]||'';
        if(!g){alert('该列暂无干支');return;}
        const ss=r.shenSha.filter(x=>x.zhi===g[1]).map(x=>x.name).join(' ')||'无';
        alert('【'+g+'】\n纳音：'+(NAYIN[g]||'')+'\n空亡：'+xunKong(g)+'\n十神：'+shen(r.pillars[2].gan,g[0])+'\n星运：'+changSheng(r.pillars[2].gan,g[1])+'\n神煞：'+ss);
      };
    });
  };
  const renderLnBar=()=>{
    const d=r.daYun[curDy]||{liuNian:[]};
    const bar=document.getElementById('lnBar');
    if(!bar) return;
    const lns=d.liuNian||[];
    bar.innerHTML=lns.length?lns.map(ln=>`<div class="lnCard ${(curLn&&curLn.year===ln.year)?'cur':''}" data-y="${ln.year}"><div class="lnYear">${ln.year}</div><div class="lnGz"><span style="color:${WX_COLOR[wxGan(ln.gz[0])]}">${esc(ln.gz[0])}</span><br><span style="color:${WX_COLOR[ZHI_WUXING[ln.gz[1]]]}">${esc(ln.gz[1])}</span></div></div>`).join(''):'';
    (bar.querySelectorAll?bar.querySelectorAll('.lnCard'):[]).forEach&&null;
    Array.prototype.forEach.call(bar.querySelectorAll?bar.querySelectorAll('.lnCard'):[],card=>{
      card.onclick=()=>{
        const y=+card.dataset.y;
        curLn=(r.daYun[curDy].liuNian||[]).find(x=>x.year===y);
        renderBig();
        const d2=r.daYun[curDy];
        const ln2=curLn;
        const xy=(r.xiaoYun||[]).find(x=>x.year===y);
        const dm=r.pillars[2].gan;
        const detail=document.getElementById('lnDetail');
        if(!detail) return;
        const yGan2=ln2?ln2.gz[0]:r.pillars[0].gan;
        const base2=[2,4,6,8,0][GAN.indexOf(yGan2)%5];
        const jies2=['小寒','立春','惊蛰','清明','立夏','芒种','小暑','立秋','白露','寒露','立冬','大雪'];
        const lymonths=[];
        const mn=['正月','二月','三月','四月','五月','六月','七月','八月','九月','十月','冬月','腊月'];
        for(let n=1;n<=12;n++){
          const zhi=DIZHI[n%12];
          const mi=DIZHI.indexOf(zhi);
          lymonths.push({jie:jies2[n-1], num:(n%12+1)+'月', gz:GAN[((base2+((n+10)%12))%10)]+zhi});
        }
        const ly=lymonths.slice(1).concat([lymonths[0]]);
        let qijian='';
        if(ln2){
          try{
            const tb=Lunar.fromYmdHms(ln2.year,6,1).getJieQiTable();
            const mi2=DIZHI.indexOf(ln2.gz[1]);
            const jn=['小寒','立春','惊蛰','清明','立夏','芒种','小暑','立秋','白露','寒露','立冬','大雪'][mi2];
            const jn2=['立春','惊蛰','清明','立夏','芒种','小暑','立秋','白露','寒露','立冬','大雪','小寒'][mi2];
            const t1=tb[jn], t2b=tb[jn2];
            if(t1&&t2b) qijian='<div class="dyBigAge dim3">始于 '+(t1.getMonth())+'月'+(t1.getDay())+'日<br>止于 '+(t2b.getMonth())+'月'+(t2b.getDay())+'日</div>';
          }catch(e){}
        }
        detail.innerHTML=(ln2?`<div class="dyBigCard"><div class="dyBigGz" style="color:${WX_COLOR[wxGan(ln2.gz[0])]}">${esc(ln2.gz)}</div><div class="dyBigInfo"><div class="dyBigAge">${ln2.year}年</div><div class="dyBigYear">${d2.gz}运</div></div><div class="dyBigSS" style="margin-left:auto"><div class="dyBigAge dim3">${esc(shen(dm,ln2.gz[0]))} · ${esc(shen(dm,ln2.gz[1]))}</div>${qijian}</div></div>`:'')
          +(ln2?`<div class="sec2">${ln2.year}年 流月</div><div class="lnGrid">${ly.map(m=>`<div class="lnCard ${ln2&&ln2.year===r.solarYear&&(parseInt(m.num)-1)===(new Date().getMonth())?'cur':''}"><div class="lnYear gold">${m.num}</div><div class="lnYear dim3">${m.jie}</div><div class="lnGz"><span style="color:${WX_COLOR[wxGan(m.gz[0])]}">${esc(m.gz[0])}</span><i class="ss2">${esc(shen(dm,m.gz[0]))}</i><br><span style="color:${WX_COLOR[ZHI_WUXING[m.gz[1]]]}">${esc(m.gz[1])}</span><i class="ss2">${esc(shen(dm,m.gz[1]))}</i></div></div>`).join('')}</div>`:'')
          +(xy?`<div class="sec2">小运</div><div class="dyBigCard"><div class="dyBigGz" style="color:${WX_COLOR[wxGan(xy.gz[0])]}">${esc(xy.gz)}</div><div class="dyBigInfo"><div class="dyBigAge">${xy.year}年 ${xy.age}岁</div><div class="dyBigYear">小运</div></div><div class="dyBigSS"><div>${esc(shen(dm,xy.gz[0]))}</div><div>${esc(shen(dm,xy.gz[1]))}</div></div></div>`:'');
      };
    });
  };
  const sel=i=>{
    curDy=i;
    app.querySelectorAll('.dyCard2').forEach(x=>x.classList.remove('sel'));
    const c=app.querySelector('.dyCard2[data-i="'+i+'"]');
    if(c)c.classList.add('sel');
    curLn=null;
    renderBig();
    renderLnBar();
    const det=document.getElementById('lnDetail');
    if(det) det.innerHTML='';
  };
  let segMode='focus';
  const sf=document.getElementById('segFocus'), sa=document.getElementById('segAll');
  if(sf&&sa){
    sf.onclick=()=>{segMode='focus';sf.classList.add('sel');sa.classList.remove('sel');
      document.getElementById('segAllBox')&&(document.getElementById('segAllBox').style.display='none');
      document.getElementById('bigTableWrap')&&(document.getElementById('bigTableWrap').style.display='block');};
    sa.onclick=()=>{segMode='all';sa.classList.add('sel');sf.classList.remove('sel');
      const box=document.getElementById('segAllBox');
      const bt=document.getElementById('bigTableWrap');
      if(!box){const d2=document.createElement('div');d2.id='segAllBox';d2.innerHTML=r.daYun.filter(d=>d.gz).map(d=>{
        const dg=d.gz[0],dz2=d.gz[1];const dm=r.pillars[2].gan;
        return `<div class="dyBigCard"><div class="dyBigGz" style="color:${WX_COLOR[wxGan(dg)]}">${esc(d.gz)}</div>
        <div class="dyBigInfo"><div class="dyBigAge">${d.startAge}-${d.endAge}岁</div><div class="dyBigYear">${d.startYear}-${d.startYear+9}</div></div>
        <div class="dyBigSS"><div>${esc(shen(dm,dg))}</div><div>${esc(shen(dm,dz2))}</div></div></div>
        <div class="lnGrid">${(d.liuNian||[]).map(ln=>`<div class="lnCard ${ln.year===r.solarYear?'cur':''}"><div class="lnYear">${ln.year}</div><div class="lnGz"><span style="color:${WX_COLOR[wxGan(ln.gz[0])]}">${esc(ln.gz[0])}</span><i class="ss2">${esc(shen(dm,ln.gz[0]))}</i><br><span style="color:${WX_COLOR[ZHI_WUXING[ln.gz[1]]]}">${esc(ln.gz[1])}</span><i class="ss2">${esc(shen(dm,ln.gz[1]))}</i></div></div>`).join('')}</div>`;
      }).join('');bt.parentNode.insertBefore(d2,bt);}
      box.style.display='block';bt.style.display='none';
    };
  }
  sel(r.daYun.findIndex(d=>d.gz)>=0?r.daYun.findIndex(d=>d.gz):0);
  document.getElementById('aiClose')&&(document.getElementById('aiClose').onclick=()=>{document.getElementById('aiPanel').style.display='none';});
  document.getElementById('btnCopy').onclick=()=>{
    const p2=r.pillars;
    let txt='【八字排盘】'+(r.gender==='男'?'乾造':'坤造')+'\n公历：'+(r.solarCN||r.solar)+'\n农历：'+(r.lunarCN||r.lunar)+'\n';
    txt+='十神：'+p2.map(x=>x.shiShen).join('  ')+'\n';
    txt+='四柱：'+p2.map(x=>x.gz).join('  ')+'\n';
    txt+='藏干：'+p2.map(x=>x.cangGan).join('  ')+'\n';
    txt+='纳音：'+p2.map(x=>x.naYin).join('  ')+'\n';
    txt+='星运：'+p2.map(x=>x.xing).join('  ')+'\n';
    txt+='空亡：'+p2.map(x=>x.kongWang).join('  ')+'\n';
    txt+='五行：'+Object.entries(r.wxCount).map(x2=>x2[0]+x2[1]).join(' ')+'\n';
    txt+='格局：'+r.geju+'  旺衰：'+r.strength+'\n';
    txt+='喜用：'+r.xi.join('、')+'  忌神：'+r.ji.join('、')+'\n';
    txt+='神煞：'+r.shenSha.map(x=>x.name+'('+x.zhi+')').join(' ')+'\n';
    txt+='命宫：'+r.mingGong+'  胎元：'+r.taiYuan+'  身宫：'+r.shenGong+'\n';
    txt+='起运：'+r.qiYunRaw+'\n大运：'+r.daYun.filter(d=>d.gz).map(d=>d.gz+'('+d.startAge+'-'+d.endAge+')').join(' ');
    if(window.__acl&&window.__acl.set){ window.__acl.set(txt); alert('已复制到剪贴板'); }
    else if(navigator.clipboard&&navigator.clipboard.writeText){ navigator.clipboard.writeText(txt).then(()=>alert('已复制到剪贴板')).catch(()=>{var t=document.createElement('textarea');t.value=txt;document.body.appendChild(t);t.select();document.execCommand('copy');t.remove();alert('已复制到剪贴板');}); }
    else alert(txt);
  };
  document.getElementById('btnAI').onclick=()=>{
    const env=window.process&&window.process.env?window.process.env:{};
    const key=env.API_KEY||'', base=(env.BASE_URL||'https://api.openai.com/v1').replace(/\/$/,''), model=env.MODEL||'gpt-4o-mini';
    if(!key){ alert('请先在 我的api设置 里配置 Key'); return; }
    const panel=document.getElementById('aiPanel'), body=document.getElementById('aiBody');
    panel.style.display='block'; body.textContent='解读中...';
    const p2=r.pillars;
    const chart='八字：'+p2.map(x=>x.gz).join(' ')+'（'+(r.gender==='男'?'男命':'女命')+'）\n十神：'+p2.map(x=>x.shiShen).join(',')+'\n藏干：'+p2.map(x=>x.zhi+'('+x.cangGan+')').join(',')+'\n纳音：'+p2.map(x=>x.naYin).join(',')+'\n旺衰：'+r.strength+' 格局：'+r.geju+'\n喜用：'+r.xi.join('、')+' 忌神：'+r.ji.join('、')+'\n神煞：'+r.shenSha.map(x=>x.name).join('、')+'\n大运：'+r.daYun.filter(d=>d.gz).map(d=>d.gz+'('+d.startAge+'岁起)').join('、');
    const sys=(env.SYSTEM||'你是专业命理师，用中文简洁解读八字，分：性格、事业、财运、婚姻、健康，各2-3句。');
    fetch(base+'/chat/completions',{method:'POST',headers:{'Content-Type':'application/json','Authorization':'Bearer '+key},
      body:JSON.stringify({model:model,messages:[{role:'system',content:sys},{role:'user',content:chart}],temperature:0.8})
    }).then(res=>res.json()).then(j=>{
      const t=j.choices&&j.choices[0]&&j.choices[0].message?j.choices[0].message.content:(j.error?j.error.message:JSON.stringify(j));
      body.textContent=t;
    }).catch(e=>{body.textContent='请求失败：'+e.message;});
  };
  // 默认第一屏显示专业细盘
  app.querySelectorAll('.tab')[2].click();

}

// ============ 表单页(原版截图1:1) ============
function formVisible(v){}
function renderForm(){
  const now=new Date();
  document.getElementById('app').innerHTML=`
  <div class="h1">基 本 信 息</div>
  <input class="inp" id="f-name" placeholder="姓名（选填）">
  <div class="h1">出 生 时 间</div>
  <div class="pillRow"><div class="pillBtn sel" id="p-solar">公历</div><div class="pillBtn" id="p-lunar">农历</div></div>
  <div class="h2">出 生 日 期</div>
  <div class="dateRow">
    <input class="inp big" id="f-y" value="${now.getFullYear()}"><span class="unit">年</span>
    <input class="inp big" id="f-mo" value="${now.getMonth()+1}"><span class="unit">月</span>
    <input class="inp big" id="f-d" value="${now.getDate()}"><span class="unit">日</span>
  </div>
  <div class="h2">出 生 时 间</div>
  <div class="dateRow">
    <input class="inp big" id="f-h" value="${now.getHours()}"><span class="unit">时</span>
    <input class="inp big" id="f-mi" value="${now.getMinutes()}"><span class="unit">分</span>
  </div>
  <div class="h1">时 区 校 正</div>
  <div class="pillRow">
    <label class="pillBtn ck"><input type="checkbox" id="f-taiyang">真太阳时</label>
    <label class="pillBtn ck"><input type="checkbox" id="f-zaowan">早晚子时</label>
    <label class="pillBtn ck"><input type="checkbox" id="f-xialing">夏令时</label>
  </div>
  <div class="h2">出 生 地 区</div>
  <select class="inp big" id="f-city">
    <option value="">请选择出生地区（真太阳时用）</option>
    <option value="121.47">上海</option><option value="116.41">北京</option>
    <option value="113.26">广州</option><option value="114.06">深圳</option>
    <option value="104.07">成都</option><option value="106.55">重庆</option>
    <option value="114.31">武汉</option><option value="118.78">南京</option>
    <option value="120.16">杭州</option><option value="117.20">天津</option>
    <option value="108.95">西安</option><option value="112.95">太原</option>
    <option value="126.53">长春</option><option value="123.43">沈阳</option>
    <option value="125.32">哈尔滨</option><option value="87.62">乌鲁木齐</option>
    <option value="102.71">兰州</option><option value="106.71">西宁</option>
    <option value="119.30">福州</option><option value="110.33">海口</option>
    <option value="108.32">南宁</option><option value="113.65">济南</option>
    <option value="113.63">郑州</option><option value="111.67">呼和浩特</option>
  </select>
  <div class="h1">性 别</div>
  <div class="pillRow"><div class="pillBtn sel" id="p-male">♂ 男</div><div class="pillBtn" id="p-female">♀ 女</div></div>
  <input class="inp" id="f-ask" placeholder="问事（选填）">
  <button class="goBtn" id="goBtn">开 始 排 盘</button>
  <div class="ruleCard">
    <div class="rt">八字排盘规则</div>
    <ul>
      <li>八字以出生年月日时排定四柱干支，每柱一天干一地支</li>
      <li>年柱以立春为界，月柱以节气为界，日柱以子时为界</li>
      <li>时柱根据出生时辰确定，每两小时为一个时辰</li>
      <li>日主为日柱天干，是八字分析之核心</li>
      <li>旺衰以月令为主，兼看其他干支对日主之生克</li>
      <li>喜用神为命局所需之五行，忌神为命局所忌之五行</li>
      <li>大运由月柱顺逆推排，每运管十年</li>
    </ul>
  </div>`;
  let isLunar=false, gender='男';
  const pS=document.getElementById('p-solar'), pL=document.getElementById('p-lunar');
  pS.onclick=()=>{isLunar=false;pS.classList.add('sel');pL.classList.remove('sel');};
  pL.onclick=()=>{isLunar=true;pL.classList.add('sel');pS.classList.remove('sel');};
  const pM=document.getElementById('p-male'), pF=document.getElementById('p-female');
  pM.onclick=()=>{gender='男';pM.classList.add('sel');pF.classList.remove('sel');};
  pF.onclick=()=>{gender='女';pF.classList.add('sel');pM.classList.remove('sel');};
  document.getElementById('goBtn').onclick=()=>{
    try{
      window.__baziName=document.getElementById('f-name').value||'案例1';
      const cfg={y:+document.getElementById('f-y').value,mo:+document.getElementById('f-mo').value,d:+document.getElementById('f-d').value,h:+document.getElementById('f-h').value,mi:+document.getElementById('f-mi').value,gender,isLunar,zaoWanZi:document.getElementById('f-zaowan').checked,xiaLing:document.getElementById('f-xialing').checked,zhenTaiYang:document.getElementById('f-taiyang').checked,lon:document.getElementById('f-city').value?+document.getElementById('f-city').value:null};
      LAST=compute(cfg);
      renderResult(LAST);
    }catch(e){alert('排盘失败: '+e.message);}
  };
}
document.addEventListener('DOMContentLoaded',()=>{ renderForm(); });
window.addEventListener('error',function(ev){
  var d=document.createElement('div');d.style.cssText='position:fixed;bottom:0;left:0;right:0;z-index:99999;background:#900;color:#fff;font:12px monospace;padding:8px;white-space:pre-wrap';
  d.textContent='JSERR: '+(ev.message||'');document.body.appendChild(d);
});
