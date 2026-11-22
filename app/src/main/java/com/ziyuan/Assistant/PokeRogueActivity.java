package com.ziyuan.Assistant;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.http.SslError;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.ziyuan.Assistant.cal.ConstData;
import java.util.LinkedHashMap;
import java.util.Map;

/* loaded from: classes.dex */
public class PokeRogueActivity extends Activity {
    private static final String AUTO_JS = "(function(){\nif(window.__pbAuto)return;\n/* 1.0.60(VC165): 血量>50% 时自动识别完全排除树果(不吃树果, 就近选其它);\n   树果只经 hpGuard 血量<=50% 切入树果模式后寻找。\n   1.0.59(VC164): 取消自动识别中「树果无条件最高优先」(小地图一有树果就先去, 与血量无关,\n   用户反馈不应如此); 树果优先只保留 hpGuard 一条路——血量<=50% 自动切树果,\n   回血过半切回。血量健康时 auto 对树果与其它候选一律就近选择。\n   1.0.58(VC163): ① 恢复「生命过半(<=50%)自动找树果」, 回血过半自动切回原模式(1.0.56 行为,\n   1.0.57 曾按要求取消, 现按新需求恢复); ② 修复敌人攻击越多越原地踏步——\n   受击躲避不再左右交替换向(交替导致方向来回翻转原地打转), 改为每次受击用\n   sparseDir 选「敌人最少」的方向逃跑, 并保持该方向直到躲避结束, 敌越密越往空处跑。\n   1.0.56(VC161): ① 树果: 血一半后自动寻找(原有); 小地图没有树果时向宝可梦少的地方\n     移动并持续寻找(不停机); 自动识别中树果判定最高优先。\n   ② 催眠貘课程弹窗: 点「就绪」关闭并记住当前区域(.ht-zone), 本区域内不再\n     找催眠貘, 换区域自动恢复判定; ② 自动识别加入树果且判定最高优先(小地图出现树果先去找);\n   ③ 受击躲避简化: 掉血即垂直换向(交替左右), 不再点 dash/跳。\n   1.0.55(VC160): 自动游玩时受击躲避——弹幕数组模块作用域直读不到, 改用受击反应:\n   HP 相邻两拍下降 => 点 tc-dash(无则跳) + 远离最近小地图单位(无则垂直侧移交替), 压向约660ms。\n   1.0.54(VC159): 正辉的机器在已学技能数不足 2 个时不寻找(自动识别过滤该候选; 单独模式冻结等待),\n   技能达标后自动恢复; 读不到技能条 DOM 则放行不误锁。\n   1.0.53(VC158): 「自动识别」就近候选加入卡比兽(portraits/143, 需环色配对), 升级后同其它候选就近择优。\n   1.0.52(VC157): 修复自动菜单丢失回归(源码基线从反编译 1.0.50 恢复)。\n   1.0.51(VC156): 修复百变怪无可复制时的死循环 —— 该版本游戏不打开 showDitto 弹窗,\n   直接弹「百变怪没有可复制的东西」提示且不消耗交互物; 旧逻辑捕获不到 -> 每 220ms 反复\n   interact 形成死循环。新增 dittoNoCopyNotice() 识别提示文案(多语言)、dittoIsNearest() 精准\n   抑制对百变怪的交互触发; dittoModal() 不再强依赖 div.cards; 直到的等级提升才重新纳入。\n   点 div.actions button.btn.ghost(Ahora no, t(null)) 跳过这一级, 记住当时等级;\n   等级未提升期间不再把百变怪当目标(自动识别过滤/单独模式暂缓), 升级后自动重新纳入。 */\n/* 1.0.49 修复(VC154): 血量>一半立即退出树果模式(原 0.7 迟滞会卡在 0.5~0.7 继续找树果);\n   hpbar.mid 兜底(游戏定义 0.3~0.55)不再误判为低血; 树果与免费精灵球 vx() 环色同为\n   #3a7aff —— 共享色模式禁用按环色/RGB 兜底, 只认精确图标通道, 消除两者识别重叠。 */\n/* 模式表(VC153 / 1.0.48): 首栏 auto=自动识别(碎岩/免费精灵球/图腾祭坛/正辉的机器/百变怪五合一, 就近);\n   其余为区域地图「本区域已发现」除弹跳蘑菇外的交互物 + vx() 表中同类祭坛/特殊交互物。\n   ring=小地图环色, src=图标文件名, wk=世界标签 data-k 关键字, needRing=portraits 图标需环色配对(排除同 species 敌人)。\n   1.0.48: bill 交互物(Máquina de Bill / 正辉的机器)菜单名对齐官方中文; 「正辉的机器」「百变怪」并入自动识别就近候选。\n   1.0.48 补充: 祭坛类(守护者/图腾/挑战/诅咒/多边兽/道馆/耿鬼)等级<15 暂缓考虑 —— 依据游戏引导\n     「守护者祭坛: 先升级并打开精灵球, 再唤醒守护者」。HUD 把 t.level 写入 div.tl-lvl 的 <b>,\n     升级弹窗 ¡Nivel X! 兜底; 读不到等级则放行(不误锁功能)。自动识别低等级跳过祭坛子目标, 达 15 级自动纳入。\n   1.0.47: 选卡/道具弹窗(div.modal)出现 -> 暂停并记住模式, 关闭后自动续跑(含「自动识别」);\n   进入祭坛(sanctuary)后 hook minimap.arc 捕获祭坛圈, 把摇杆方向约束在圈内, 不超出祭坛圈。 */\nvar M=[\n {k:'auto', cn:'自动识别', subs:['berry','rock','freechest','altar','bill','ditto','snorlax'], hi:'#FFD23A'},\n {k:'rock',      cn:'碎岩',         ring:'#d8a830', rgb:[216,168,48],  src:'map-rock',      wk:'rock',       hi:'#FFE23A', act:'碎岩'},\n {k:'berry',     cn:'树果',         ring:'#3a7aff', rgb:[58,122,255],  src:'oran-berry',    wk:'berry',      hi:'#3A7AFF', act:'采集'},\n {k:'freechest', cn:'免费精灵球',   ring:'#3a7aff', rgb:[58,122,255],  src:'map-freeball',  wk:'freechest',  hi:'#6A9CFF', act:'开启'},\n {k:'altar',     cn:'图腾祭坛',     ring:'#2ec8e0', rgb:[46,200,224],  src:'map-unown',     wk:'',           hi:'#2EC8E0', act:'祝福'},\n {k:'boss',      cn:'守护者祭坛',   ring:'#e8243a', rgb:[232,36,58],   src:'map-boss',      wk:'boss',       hi:'#FF5A6A', act:'祭坛'},\n {k:'chest',     cn:'精灵球(付费)', ring:'#e8443a', rgb:[232,68,58],   src:'map-ball',      wk:'',           hi:'#FF8A8A', act:'开启'},\n {k:'slots',     cn:'老虎机',       ring:'#c8306a', rgb:[200,48,106],  src:'coin',          wk:'',           hi:'#FF6AB0', act:'投币'},\n {k:'challenge', cn:'挑战祭坛',     ring:'#ff6a3a', rgb:[255,106,58],  src:'map-challenge', wk:'',           hi:'#FF6A3A', act:'挑战'},\n {k:'cursed',    cn:'诅咒祭坛',     ring:'#2a2a3a', rgb:[42,42,58],    src:'map-cursed',    wk:'',           hi:'#8A8AA0', act:'祭坛'},\n {k:'porygon',   cn:'多边兽祭坛',   ring:'#3ac8e8', rgb:[58,200,232],  src:'portraits/137', wk:'', needRing:1, hi:'#3AC8E8', act:'祭坛'},\n {k:'dojo',      cn:'空手道馆',     ring:'#e0503a', rgb:[224,80,58],   src:'portraits/106', wk:'', needRing:1, hi:'#E0503A', act:'道馆'},\n {k:'greed',     cn:'耿鬼祭坛',     ring:'#8a4ad0', rgb:[138,74,208],  src:'portraits/94',  wk:'', needRing:1, hi:'#8A4AD0', act:'祭坛'},\n {k:'hypno',     cn:'催眠貘',       ring:'#e8c040', rgb:[232,192,64],  src:'portraits/97',  wk:'', needRing:1, hi:'#E8C040', act:'交互'},\n {k:'snorlax',   cn:'卡比兽',       ring:'#3a6a8a', rgb:[58,106,138],  src:'portraits/143', wk:'', needRing:1, hi:'#3A6A8A', act:'交互'},\n {k:'fossil',    cn:'化石遗迹',     ring:'#ff9a2a', rgb:[255,154,42],  src:'portraits/142', wk:'', needRing:1, hi:'#FF9A2A', act:'交互'},\n {k:'rhydon',    cn:'钻角犀兽',     ring:'#c8783a', rgb:[200,120,58],  src:'portraits/112', wk:'', needRing:1, hi:'#C8783A', act:'交互'},\n {k:'ditto',     cn:'百变怪',       ring:'#c88aff', rgb:[200,138,255], src:'portraits/132', wk:'', needRing:1, hi:'#C88AFF', act:'交互'},\n {k:'nest',      cn:'宝可梦蛋巢',   ring:'#6ad05a', rgb:[106,208,90],  src:'portraits/113', wk:'', needRing:1, hi:'#6AD05A', act:'交互'},\n {k:'bill',      cn:'正辉的机器',     ring:'#ffb43a', rgb:[255,180,58],  src:'map-bill',      wk:'',           hi:'#FFB43A', act:'交互'},\n {k:'magnet',    cn:'磁力',         ring:'#8a8aa0', rgb:[138,138,160], src:'items/magnet',  wk:'',           hi:'#8A8AA0', act:'交互'}\n];\nvar MIDX={};for(var mi=0;mi<M.length;mi++)MIDX[M[mi].k]=M[mi];\n/* 祭坛类 ac=1(进入战斗/挑战/圈的场景, 等级<15 暂缓); sn=自动识别停机提示短名。 */\nvar AC={boss:1,altar:1,challenge:1,cursed:1,porygon:1,dojo:1,greed:1};\nvar SN={rock:'碎岩',freechest:'免费球',altar:'图腾',bill:'正辉的机器',ditto:'百变怪',snorlax:'卡比兽',berry:'树果'};\nfor(var qi=0;qi<M.length;qi++){if(AC[M[qi].k])M[qi].ac=1;if(SN[M[qi].k])M[qi].sn=SN[M[qi].k];}\nvar A=window.__pbAuto={on:false,mode:'rock',sub:null,dirTimer:null,actTimer:null,ang:0.6,hi:null,arrow:null,\n lastDir:{x:0,y:-1},miss:0,dirInfo:'待机',lastDist:-1,still:0,jumpN:0,pickN:0,lowTick:0,noTgt:0,\n hpLow:false,hpOverride:false,prevMode:null,\n paused:false,pauseGap:0,resumeMode:null,resumeSub:null,dittoSkipLv:-1,dittoClicked:false,dittoNoticeHold:false,dodgeT:0,dodgeCd:0,dodgeDir:null,dodgeSide:false,lastHp:-1,hypnoZone:null,M:M};\nvar PID=9124;\nfunction bridge(){return(typeof AndroidBridge!=='undefined'&&AndroidBridge)?AndroidBridge:null;}\n/* ---- 祭坛圈约束(1.0.47): 玩家恒在小地图 (200,200); 圈心相对玩家向量 (x,y), 半径 r(px)。\n   缓冲带内想往外 => 只朝圈心; 否则去掉径向向外分量走切线, 保证不把角色推出祭坛圈。 ---- */\nfunction arenaVec(){\n var a=window.__pbArena,best=null;\n if(!a||!a.length)return null;\n for(var i=0;i<a.length;i++){var c=a[i];if(!(c.r>=14))continue;\n  if(!best||c.r<best.r)best=c;}\n return best;\n}\nfunction guardDir(x,y){\n var a=arenaVec();if(!a)return{x:x,y:y};\n var d=Math.sqrt(a.x*a.x+a.y*a.y);if(d<1)return{x:x,y:y};\n var ix=a.x/d,iy=a.y/d,safe=a.r-16;if(safe<10)safe=10;\n var inward=x*ix+y*iy;\n if(d>=a.r-3||(d>=safe&&inward<=0))return{x:ix,y:iy};\n if(d>=safe-20&&inward<0){\n  var tx=x-inward*ix,ty=y-inward*iy,L=Math.sqrt(tx*tx+ty*ty);\n  if(L>0.05)return{x:tx/L,y:ty/L};\n  return{x:ix,y:iy};}\n return{x:x,y:y};\n}\nfunction setD(x,y,s){var g=guardDir(x,y);var b=bridge();if(b)b.setDir(g.x,g.y);\n A.lastDir={x:g.x,y:g.y};A.dirInfo=s;}\nfunction status(s){var b=bridge();if(b)b.setStatus(s);}\nfunction cur(){return MIDX[A.mode]||M[1];}\nfunction curSub(){return(A.mode==='auto'&&A.sub&&MIDX[A.sub])?MIDX[A.sub]:cur();}\n/* ---- 等级(1.0.48): HUD 把 t.level 写入 div.tl-lvl 的 lastChild(<b>), 初始 '1';\n   升级弹窗标题 ¡Nivel X! 兜底。读不到返回 -1 -> 门控放行, 不因 DOM 变化误锁功能。 ---- */\nfunction level(){try{\n var e=document.querySelector('div.tl-lvl');\n if(e){var b=e.lastChild||e.querySelector('b'),t=b?(b.textContent||''):(e.textContent||'');\n  var m=/\\d+/.exec(t);if(m){var n=parseInt(m[0],10);if(n>=1&&n<=999)return n;}}\n var h=document.querySelector('div.modal h1');\n if(h){var m2=/\\d+/.exec(h.textContent||'');if(m2){var n2=parseInt(m2[0],10);if(n2>=1&&n2<=999)return n2;}}\n }catch(err){}return -1;}\nvar LVL_MIN=15;\nfunction lvlLow(){var l=level();return l>=0&&l<LVL_MIN;}\nfunction lvlGate(m){return !!m&&m.k!=='auto'&&!!m.ac&&lvlLow();}\n/* ---- 百变怪跳过(1.0.50): showDitto 弹窗里 button.card.mini 全部达上限\n   (源码 o&&(s.style.opacity='0.45'), 点击 L.deny()) 或根本没有道具 => 没有可复制的。\n   点 div.actions button.btn.ghost(Ahora no, t(null)) 跳过本轮, 记住当时等级; 等级未提升\n   期间不再把百变怪当目标, 升级(level 增大)自动解除并重新纳入。读不到等级则放行不误锁。 ---- */\nfunction dittoHold(){\n if(A.dittoSkipLv<0)return false;\n var l=level();if(l<0)return false;\n if(l>A.dittoSkipLv){A.dittoSkipLv=-1;A.dittoNoticeHold=false;return false;}\n return true;}\n/* ---- 技能数(1.0.54): HUD 技能条 div.slots(先于药水条创建), 已学技能 = .slot 非 empty/locked\n   且含 .nm 名称(buildSlots: 锁定位 class=slot locked, 未学=slot empty, 已学=slot[.linked] 含 div.nm)。\n   读不到 DOM 返回 -1 -> 门控放行, 不因结构变化误锁。 ---- */\nvar BILL_MIN=2;\nfunction skillCount(){\n try{\n  var bars=document.querySelectorAll('div.slots');\n  for(var i=0;i<bars.length;i++){\n   var slots=bars[i].querySelectorAll('.slot');\n   if(!slots.length)continue;\n   var nm=bars[i].querySelectorAll('.slot:not(.empty):not(.locked) .nm');\n   return nm.length;}\n }catch(e){}\n return -1;}\nfunction billHold(){var c=skillCount();return c>=0&&c<BILL_MIN;}\n/* ---- 催眠貘课程(1.0.56): 交互催眠貘出现课程弹窗(看着摆锤…忘记吧/课程已使用/下个区域回来) ->\n   点「就绪/Listo」关闭并记住当前区域(.ht-zone 文案, 如「区域 1/3」); 本区域内不再把催眠貘\n   当目标(自动识别过滤候选/单独模式冻结等待), 换区域后自动恢复判定。区域文案读不到时保持\n   等待(游戏 HUD 常驻该元素)。 ---- */\nfunction zoneId(){try{var z=document.querySelector('.ht-zone');\n var t=z?(z.textContent||''):'';return t||null;}catch(e){return null;}}\nfunction hypnoHold(){\n if(!A.hypnoZone)return false;\n var z=zoneId();\n if(z&&z!==A.hypnoZone){A.hypnoZone=null;return false;}\n return true;}\nfunction hypnoModalSkip(){\n var hn=(coords(MIDX.hypno)||[]).length>0;\n if(!(A.mode==='hypno'||A.sub==='hypno'||(A.mode==='auto'&&hn)))return false;\n var dm=document.querySelector('div.modal');\n if(!vis(dm))return false;\n var t=dm.textContent||'';\n if(!/课程|忘记|olvidar|Forget|Recuerda/i.test(t))return false;\n var btns=dm.querySelectorAll('button'),ok=null;\n for(var i=0;i<btns.length;i++){if(/就绪|Listo/i.test(btns[i].textContent||'')){ok=btns[i];break;}}\n if(ok)fireDomBtn(ok);\n A.hypnoZone=zoneId();\n status('催眠貘课程 · 下个区域再判定');\n return true;}\nfunction gateInfo(m){\n if(lvlGate(m))return{mid:'等级'+level()+'/'+LVL_MIN+' · 暂缓'+m.cn,\n  txt:'等级不足(需'+LVL_MIN+'级) · 暂缓'+m.cn};\n if(m.k==='ditto'&&dittoHold())return{mid:'百变怪 · 等待升级后再选',\n  txt:'百变怪无可复制 · 等待升级后再选'};\n if(m.k==='bill'&&billHold())return{mid:'正辉的机器 · 技能不足(需'+BILL_MIN+'个) · 暂缓',\n  txt:'正辉的机器 · 技能不足(需'+BILL_MIN+'个) · 暂缓寻找'};\n if(m.k==='hypno'&&hypnoHold())return{mid:'催眠貘 · 本区域已用课程 · 换区域再判定',\n  txt:'催眠貘课程已用 · 下个区域再判定'};\n return null;}\n/* 1.0.60(VC165): 血量>50% 时自动识别完全排除树果候选(不吃树果, 就近选其它);\n   树果只在血量<=50% hpGuard 切入树果模式后寻找。 */\nfunction allowedSubs(){var out=[],mm=MIDX.auto,low=lvlLow(),dh=dittoHold(),bh=billHold(),hh=hypnoHold();\n for(var q=0;q<mm.subs.length;q++){var sm=MIDX[mm.subs[q]];\n  if(low&&sm.ac)continue;if(dh&&sm.k==='ditto')continue;\n  if(bh&&sm.k==='bill')continue;if(hh&&sm.k==='hypno')continue;\n  if(!A.hpLow&&sm.k==='berry')continue;out.push(sm);}\n return out;}\nfunction autoNames(){var s=[],as=allowedSubs();for(var i=0;i<as.length;i++)s.push(as[i].sn||as[i].cn);return s.join('/');}\n/* ---- 精确捕获: hook ctx.fill(环色) + ctx.drawImage(图标 src) + ctx.arc(祭坛圈) ----\n   Sx(ctx,src,ring,...) 先以 ring 色 fill 圆环, 再 drawImage 图标; 两次 getTransform e/f 同为标记坐标。\n   needRing 模式(portraits 图标)必须与同坐标的环色配对, 否则敌方同 species 头像会被误当祭坛。\n   minimap 画圈: sanctuary 分支先 arc(arenaR*r) 白色 lineWidth3, 再 arc(ringR*r) 紫色 lineWidth5。 */\nvar probe=document.createElement('canvas').getContext('2d');\nvar RING2={};for(var ri=0;ri<M.length;ri++){if(!M[ri].ring)continue;\n probe.fillStyle=M[ri].ring;RING2[probe.fillStyle]=M[ri].ring;}\nif(!window.__pbHooked){window.__pbHooked=1;\n window.__pbSrc={};window.__pbPrevSrc={};window.__pbRing={};window.__pbPrevRing={};\n window.__pbArena=[];window.__pbLast=null;\n var F=CanvasRenderingContext2D.prototype.fill,DI=CanvasRenderingContext2D.prototype.drawImage,\n     CR=CanvasRenderingContext2D.prototype.clearRect,AR=CanvasRenderingContext2D.prototype.arc;\n CanvasRenderingContext2D.prototype.clearRect=function(x,y,w,h){\n  if(w===400&&h===400&&this.canvas&&(this.canvas.className||'').indexOf('minimap')>=0){\n   window.__pbPrevSrc=window.__pbSrc;window.__pbSrc={};\n   window.__pbPrevRing=window.__pbRing;window.__pbRing={};\n   window.__pbArena=[];window.__pbLast=null;window.__pbFoe=[];}\n  return CR.apply(this,arguments);};\n CanvasRenderingContext2D.prototype.fill=function(){\n  try{if(this.canvas&&(this.canvas.className||'').indexOf('minimap')>=0){\n   var hex=RING2[this.fillStyle];\n   if(hex){var t=this.getTransform?this.getTransform():null;\n    if(t){(window.__pbRing[hex]||(window.__pbRing[hex]=[])).push({x:t.e,y:t.f});\n     window.__pbLast={x:t.e,y:t.f,hex:hex};}}\n  }}catch(e){}\n  return F.apply(this,arguments);};\n CanvasRenderingContext2D.prototype.drawImage=function(img){\n  try{if(this.canvas&&(this.canvas.className||'').indexOf('minimap')>=0){\n   var s=(img&&(img.src||img.currentSrc))||'';\n   if(s){var t=this.getTransform?this.getTransform():null;\n    if(t){var L=window.__pbLast,\n     ring=(L&&Math.abs(L.x-t.e)<1.5&&Math.abs(L.y-t.f)<1.5)?L.hex:null;\n     for(var j=0;j<M.length;j++){var m=M[j];\n      if(!m.src||s.indexOf(m.src)<0)continue;\n      if(m.needRing&&m.ring!==ring){\n       (window.__pbFoe||(window.__pbFoe=[])).push({x:t.e,y:t.f});continue;}\n      (window.__pbSrc[m.k]||(window.__pbSrc[m.k]=[])).push({x:t.e,y:t.f});break;}}}\n  }}catch(e){}\n  return DI.apply(this,arguments);};\n CanvasRenderingContext2D.prototype.arc=function(cx,cy,rad){\n  try{if(this.canvas&&(this.canvas.className||'').indexOf('minimap')>=0){\n   var ss=String(this.strokeStyle||'').replace(/\\s+/g,'').toLowerCase(),lw=this.lineWidth,\n    purple=/176,90,255/.test(ss),white=/255,255,255,0\\.4/.test(ss)&&lw===3&&rad>=40;\n   if((purple&&rad>=14)||white){\n    var t=this.getTransform?this.getTransform():null,\n     a=t?(t.a||0):1,b=t?(t.b||0):0,c=t?(t.c||0):0,d=t?(t.d||0):1,\n     ex=t?(t.e||0):0,ey=t?(t.f||0):0;\n    var vx=a*cx+c*cy+ex-200,vy=b*cx+d*cy+ey-200,sc=Math.sqrt(a*a+b*b)||1;\n    window.__pbArena.push({x:vx,y:vy,r:rad*sc,kind:purple?'ring':'edge'});}\n  }}catch(e){}\n  return AR.apply(this,arguments);};}\n/* ---- 高亮框 + 中央方向箭头(按目标子模式配色) ---- */\nfunction hi(){if(A.hi&&A.hi.isConnected)return A.hi;\n var d=document.createElement('div');d.id='pbk-hi';\n d.style.cssText='position:fixed;z-index:2147483000;pointer-events:none;display:none;border-radius:10px';\n document.body.appendChild(d);A.hi=d;return d;}\nfunction box(r,c){var h=hi();if(!r){h.style.display='none';return;}\n h.style.border='3px solid '+c;h.style.boxShadow='0 0 0 2px #000a,0 0 14px '+c;\n h.style.display='block';h.style.left=Math.round(r.left-6)+'px';h.style.top=Math.round(r.top-6)+'px';\n h.style.width=Math.round(r.width+12)+'px';h.style.height=Math.round(r.height+12)+'px';}\nfunction arrow(x,y,c){if(!A.arrow){var a=document.createElement('div');a.id='pbk-arw';\n a.style.cssText='position:fixed;z-index:2147483000;pointer-events:none;left:50%;top:50%;'\n +'width:0;height:0;border-left:15px solid transparent;border-right:15px solid transparent;'\n +'transform-origin:50% 72%;opacity:.9';\n document.body.appendChild(a);A.arrow=a;}\n A.arrow.style.borderBottom='28px solid '+c;A.arrow.style.display='block';\n A.arrow.style.transform='translate(-50%,-72%) rotate('+(Math.atan2(y,x)*180/Math.PI+90)+'deg)';}\nfunction noArrow(){if(A.arrow)A.arrow.style.display='none';}\n/* ---- 识别: 世界标签 / 小地图环+图标 / 精确色像素兜底 ---- */\nfunction screenTargets(m){if(!m.wk)return[];var out=[],ls=document.querySelectorAll('.wlabel');\n for(var i=0;i<ls.length;i++){var e=ls[i];\n  if((e.getAttribute('data-k')||'').toLowerCase().indexOf(m.wk)<0)continue;\n  var s=getComputedStyle(e);if(s.display==='none'||s.visibility==='hidden')continue;\n  if(parseFloat(s.opacity||'1')<0.15)continue;\n  var r=e.getBoundingClientRect();if(r.width<2||r.height<2)continue;\n  var dx=r.left+r.width/2-innerWidth/2,dy=r.top+r.height/2-innerHeight/2,L=Math.hypot(dx,dy);\n  if(L<1)continue;out.push({x:dx/L,y:dy/L,dist:L,r:r,cn:m.cn,sub:m.k});}\n return out;}\nfunction mapTargets(rs,m){var out=[],seen=[];\n for(var i=0;i<rs.length;i++){var dx=rs[i].x-200,dy=rs[i].y-200,L=Math.hypot(dx,dy);\n  if(L<14||L>188)continue;\n  var dup=false;for(var j=0;j<seen.length;j++){if(Math.hypot(seen[j].x-rs[i].x,seen[j].y-rs[i].y)<16){dup=true;break;}}\n  if(dup)continue;seen.push({x:rs[i].x,y:rs[i].y});\n  out.push({x:dx/L,y:dy/L,dist:L,mx:rs[i].x,my:rs[i].y,cn:m.cn,sub:m.k});}\n return out;}\n/* ---- 共享环色/RGB 隔离(1.0.49): vx() 表 berry 与 freechest 同为 #3a7aff, rgb 也相同。\n   图标未加载(xx() 返回 null)时 Sx() 只画环色圆点、不 drawImage; 此时若按环色/RGB 兜底,\n   两类会拿到同一份混合坐标 => 树果与免费精灵球重叠。故: 被 >=2 个模式共用的颜色,\n   这些模式禁用环色/像素兜底, 只认精确图标通道(按 m.k 分开), 宁可短暂找不到也不误认。 ---- */\nvar RC={},GC={};\nfor(var ci=0;ci<M.length;ci++){var cm=M[ci];\n if(cm.ring)RC[cm.ring]=(RC[cm.ring]||0)+1;\n if(cm.rgb){var gk=cm.rgb.join(',');GC[gk]=(GC[gk]||0)+1;}}\nfunction sharedRing(m){return !!(m.ring&&RC[m.ring]>1);}\nfunction sharedPix(m){return !!(m.rgb&&GC[m.rgb.join(',')]>1);}\nfunction colorFallback(m){if(sharedPix(m))return[];\n var el=document.querySelector('canvas.minimap');if(!el)return[];\n var c;try{c=el.getContext('2d',{willReadFrequently:true});}catch(e){return[];}\n var d;try{d=c.getImageData(0,0,400,400).data;}catch(e){return[];}\n var R0=m.rgb[0],G0=m.rgb[1],B0=m.rgb[2],cells={};\n for(var y=2;y<398;y+=2)for(var x=2;x<398;x+=2){var i=(y*400+x)*4;\n  if(Math.abs(d[i]-R0)>14||Math.abs(d[i+1]-G0)>14||Math.abs(d[i+2]-B0)>16)continue;\n  var k=(x>>3)+','+(y>>3);cells[k]=(cells[k]||0)+1;}\n var out=[];for(var k in cells){if(cells[k]<2)continue;\n  var q=k.split(','),mx=(+q[0])*8+4,my=(+q[1])*8+4;\n  var dx=mx-200,dy=my-200,L=Math.hypot(dx,dy);if(L<14||L>188)continue;\n  out.push({x:dx/L,y:dy/L,dist:L,mx:mx,my:my,cn:m.cn+'?',sub:m.k});}\n return out;}\nfunction mapBox(t){var m=document.querySelector('canvas.minimap');if(!m)return null;\n var r=m.getBoundingClientRect();if(r.width<2)return null;\n var sx=r.width/400,sy=r.height/400;\n return{left:r.left+t.mx*sx-11,top:r.top+t.my*sy-11,width:22,height:22};}\nfunction byDist(p,q){return p.dist-q.dist;}\n/* 该模式坐标: 图标 src 精确通道优先, 其次上一帧, 再环色兜底 */\nfunction coords(m){\n var a=(window.__pbSrc&&window.__pbSrc[m.k])||[];\n if(!a.length)a=(window.__pbPrevSrc&&window.__pbPrevSrc[m.k])||[];\n if(!a.length&&!sharedRing(m)){a=(window.__pbRing&&window.__pbRing[m.ring])||[];\n  if(!a.length)a=(window.__pbPrevRing&&window.__pbPrevRing[m.ring])||[];}\n return a;}\nfunction coordsAct(m){if(m.k!=='auto')return coords(m);\n var out=[],as=allowedSubs();\n for(var q=0;q<as.length;q++){var c=coords(as[q]);\n  for(var i=0;i<c.length;i++)out.push(c[i]);}return out;}\n/* pick: auto 合并 allowedSubs() 的小地图候选(等级<15 跳过祭坛子目标), 按 dist 就近; 其余模式沿用屏幕标签优先。\n   1.0.59: 取消「树果无条件最高优先」——树果只由 hpGuard 在血量<=50% 时接管优先,\n   血量健康时 auto 与其它候选一样就近选择。 */\nfunction pick(){var m=cur();\n if(m.k==='auto'){\n  var list=[],as=allowedSubs();\n  for(var q=0;q<as.length;q++){var sm=as[q];\n   var mt=mapTargets(coords(sm),sm);if(!mt.length)mt=colorFallback(sm);\n   for(var i=0;i<mt.length;i++)list.push(mt[i]);}\n  if(!list.length)return null;\n  list.sort(byDist);return{g:list[0],scr:false,m:m,sub:list[0].sub};\n }\n var s=screenTargets(m);if(s.length){s.sort(byDist);return{g:s[0],scr:true,m:m,sub:m.k};}\n var mt2=mapTargets(coords(m),m);if(!mt2.length)mt2=colorFallback(m);\n if(mt2.length){mt2.sort(byDist);return{g:mt2[0],scr:false,m:m,sub:m.k};}\n return null;}\n/* ---- 可见性 / 合成点击(button() 不依赖 trusted) ---- */\nfunction vis(e){if(!e)return false;var s=getComputedStyle(e);\n if(s.display==='none'||s.visibility==='hidden')return false;\n if(parseFloat(s.opacity||'1')<0.15)return false;\n var r=e.getBoundingClientRect();return r.width>2&&r.height>2;}\nfunction fireBtn(e){if(!e)return;var r=e.getBoundingClientRect();\n var x=r.left+r.width/2,y=r.top+r.height/2;\n function ev(t,bt){return new PointerEvent(t,{pointerId:PID,pointerType:'touch',\n  clientX:x,clientY:y,bubbles:true,cancelable:true,button:0,buttons:bt,isPrimary:false});}\n try{e.dispatchEvent(ev('pointerdown',1));}catch(err){}\n setTimeout(function(){try{e.dispatchEvent(ev('pointerup',0));}catch(err){}},60);}\nfunction tapJump(){var j=document.querySelector('.tc-jump');if(j){fireBtn(j);return true;}return false;}\nfunction fireDomBtn(e){if(!e)return;try{e.click();}catch(err){}}\n/* ---- 百变怪没有可复制的 -> 点「Ahora no」跳过, 记等级等升级后再选(1.0.50) ----\n   仅在锁定百变怪(单独模式或自动识别子目标)时处理, 靠 DOM 结构判定不依赖多语言文案:\n   div.cards button.card.mini 全部 opacity<0.6(达上限)或列表为空 => 无可复制;\n   div.actions button.btn.ghost 为 t(null) 跳过按钮。有可复制项则不动, 交给用户手动选。\n   注意: 该版本游戏在「完全没有可复制道具」时不会走此弹窗, 而是直接弹提示(见 dittoNoCopyNotice)。 ---- */\nfunction dittoModal(){\n if(A.dittoClicked)return false;\n var dtarget=(A.mode==='ditto')||(A.mode==='auto'&&A.sub==='ditto');\n if(!dtarget)return false;\n if(!vis(document.querySelector('div.modal')))return false;\n if(!document.querySelector('div.cards'))return false;\n var cards=document.querySelectorAll('div.cards button.card.mini'),usable=0;\n for(var i=0;i<cards.length;i++){\n  var op=(cards[i].style&&cards[i].style.opacity)||'1';\n  if(parseFloat(op)>=0.6)usable++;}\n if(usable>0)return false;\n var skip=document.querySelector('div.actions button.btn.ghost');\n if(!vis(skip))return false;\n fireDomBtn(skip);A.dittoClicked=true;A.dittoSkipLv=level();\n status('百变怪无可复制 · 跳过, 升级后再选');\n return true;}\n/* 游戏在完全没有可复制道具时, 不会打开 showDitto 弹窗(div.modal/div.cards 均不存在),\n   而是直接弹「百变怪没有可复制的东西」提示且不消耗交互物。旧逻辑捕获不到该提示 ->\n   每 220ms 又 interact(KeyE) -> 游戏再弹提示, 形成死循环(右下「交互百变怪 #N」一直涨)。\n   故: 检测该提示文案(多语言), 命中即记等级跳过本轮, 升级后自动重新纳入。 */\nfunction dittoNoCopyNotice(){\n if(!(A.mode==='ditto'||A.sub==='ditto'||A.mode==='auto'))return false;\n if(A.dittoSkipLv>=0)return false; /* 已记过等级则不再重复命中, 交由 dittoHold 门控 */\n var txt='',ns=document.querySelectorAll('.notice,.toast,.app-notice,.prompt,.tc-act,[class*=\"notice\"],[class*=\"toast\"]');\n for(var i=0;i<ns.length;i++){if(vis(ns[i]))txt+=' '+(ns[i].textContent||'');}\n /* 必须含百变怪/Ditto 上下文, 避免\"其它提示文字没有可复制的东西\"等无关文案误触发 */\n if(!/百变怪(没有可复制|无可复制)|Ditto[^<]*?(no hay|nada que copiar|nothing to copy)|no hay (nada )?que copiar|nothing to (copy|clone)/i.test(txt))return false;\n A.dittoNoticeHold=true;A.dittoSkipLv=level();\n return true;}\n/* 自动识别模式下, 当前(最近)目标是否为百变怪: 用于精准抑制对其的交互触发, 不影响其它交互物。\n   仅当除百变怪外没有任何其它可导航目标时才视为\"只有百变怪\"(否则应继续导航到其它目标)。 */\nfunction dittoIsNearest(){\n if(A.mode!=='auto')return false;\n var d=coords('ditto');if(!d||!d.length)return false;\n if(!mapTargets(d,MIDX.ditto).length)return false;\n var as=allowedSubs();\n for(var q=0;q<as.length;q++){if(as[q].k==='ditto')continue;\n  var c=coords(as[q]);if(c&&c.length)return false;}\n return true;}\n/* ---- 选卡/道具弹窗: ui.modal() 创建 div.modal(+div.cards), $1rj1y5x() 时 remove() ----\n   升级选卡/找到对象/多个道具/祝福/进化/道馆/交易全部走同一个 modal。 */\nfunction modalOpen(){return vis(document.querySelector('div.modal'))||vis(document.querySelector('div.cards'));}\n/* ---- HP 比例: HUD 把 hp/maxHp 写成 .hpbar>i 的 transform:scaleX(x); 低/中血 class 与 div.lowhp 兜底 ---- */\nfunction hpRatio(){\n var bar=document.querySelector('.hpbar');\n if(bar&&bar.children&&bar.children.length){\n  var st=bar.children[0].style||{},m=/scaleX\\(\\s*([0-9.]+)\\s*\\)/i.exec(st.transform||'');\n  if(m){var v=parseFloat(m[1]);if(v>=0&&v<=1)return v;}}\n if(bar&&bar.classList){if(bar.classList.contains('low'))return 0.2;\n  if(bar.classList.contains('mid'))return 0.55;}\n if(vis(document.querySelector('div.lowhp')))return 0.2;\n return -1;}\n/* ---- 生命(1.0.58 恢复): <=0.5(一半及以下)自动找树果; >0.5(还有一半以上)立即退出并切回原模式。\n   状态翻转(hpLow)天然去重, 不会同方向重复切; 用户手动选树果时不覆盖。hpRatio 仍供受击躲避使用。 ---- */\nfunction hpGuard(){\n var r=hpRatio();if(r<0)return;\n if(r<=0.5){\n  if(!A.hpLow){A.hpLow=true;\n   if(A.mode!=='berry'){A.prevMode=A.mode;A.hpOverride=true;A.setMode('berry');\n    var b=bridge();if(b&&b.autoSwitch)b.autoSwitch('berry');\n    status('生命过半 · 自动找树果');}}\n }else{\n  if(A.hpLow){A.hpLow=false;\n   if(A.hpOverride&&A.prevMode){var pm=A.prevMode;A.prevMode=null;A.hpOverride=false;\n    A.setMode(pm);var b2=bridge();if(b2&&b2.autoSwitch)b2.autoSwitch(pm);\n    status('生命恢复 · 回到'+(MIDX[pm]?MIDX[pm].cn:pm));}}\n }}\n/* ---- 树果稀疏寻找(1.0.56): 血一半后 hpGuard 自动切树果模式; 小地图没有树果时,\n   向「宝可梦(敌方同类头像 __pbFoe)少」的方向移动并持续寻找(不计数停机)。\n   8 个候选方向(以前方为中心 ±30° 步进), 统计每方向前方 80px 锥内敌人头像数, 最少者优先,\n   平局时偏向当前行进方向。 ---- */\nfunction sparseDir(){\n var foes=window.__pbFoe||[];\n var lx=A.lastDir.x||0,ly=(A.lastDir.y===0)?0:(A.lastDir.y||-1);\n var base=Math.atan2(ly,lx);\n var best=null,bestScore=-1e9;\n for(var q=0;q<8;q++){\n  var ang=base+(q-4)*Math.PI/6,dx=Math.cos(ang),dy=Math.sin(ang),cnt=0;\n  for(var i=0;i<foes.length;i++){\n   var fx=foes[i].x-200,fy=foes[i].y-200,L=Math.hypot(fx,fy);\n   if(L<1||L>80)continue;\n   if((fx*dx+fy*dy)/L>0.55)cnt++;}\n  /* 评分: 敌人越少越好(0 敌方向必优于任何有敌方向), 平局偏向当前行进方向 */\n  var score=-cnt*100-Math.abs(q-4);\n  if(score>bestScore){bestScore=score;best={x:dx,y:dy};}}\n return best;}\n/* ---- 受击躲避(1.0.58): 掉血即向「敌人最少」的方向逃跑(sparseDir), 不再左右交替换向——\n   1.0.56 交替换向在敌人密集(连续掉血)时会左右来回翻转, 看起来原地踏步。\n   现在每次触发选一次稀疏方向并保持 3 拍, 冷却 5 拍; 敌越密越往空处跑, 不点 dash/跳。 ---- */\nfunction dodgeReact(){\n var r=hpRatio();if(r<0)return;\n if(A.dodgeCd>0)A.dodgeCd--;\n if(A.lastHp>=0&&A.dodgeCd<=0&&r<A.lastHp-0.01){\n  var sd=sparseDir(),d;\n  if(sd){d=sd;}\n  else{d=A.lastDir;var ex=-d.y,ey=d.x,L=Math.hypot(ex,ey)||1;d={x:ex/L,y:ey/L};}\n  A.dodgeDir={x:d.x,y:d.y};A.dodgeT=3;A.dodgeCd=5;\n  status('受击 · 向宝可梦少处躲避');\n }\n A.lastHp=r;}\n/* ---- 交互提示: $80yrav('[E]'+label) 写 div.prompt, touch.update 同步 .tc-act ----\n   rock/berry 保留关键词隔离; auto 与其余模式有交互提示即命中。 */\nfunction isRockTxt(t){return /碎岩|岩石|岩|Roca|Rock|Smash|Golpe|bonk/i.test(t||'');}\nfunction isBerryTxt(t){return /树果|橙橙果|桃桃果|文柚果|采集|Recoger|Bayas|Baya|Oran|Fruta|Berry/i.test(t||'');}\nfunction actText(){var a=document.querySelector('.tc-act');return vis(a)?(a.textContent||''):'';}\nfunction promptText(){var p=document.querySelector('div.prompt');return vis(p)?(p.textContent||''):'';}\nfunction labelText(){var out='',ls=document.querySelectorAll('.wlabel');\n for(var i=0;i<ls.length;i++){var e=ls[i];if(!vis(e))continue;\n  out+=' '+(e.getAttribute('data-k')||'')+' '+(e.textContent||'');}\n return out;}\nfunction hint(m){var at=actText(),pt=promptText(),lt=labelText();\n if(m.k==='rock')return isRockTxt(at)||isRockTxt(pt)||isRockTxt(lt);\n if(m.k==='berry')return isBerryTxt(at)||isBerryTxt(pt)||isBerryTxt(lt);\n return !!(at||pt)||isRockTxt(at)||isRockTxt(pt);}\n/* ---- 暂停/续跑(1.0.47): 弹窗出现 -> 暂停并记住模式, 摇杆归零 + 通知 Java 抬手;\n   连续 3 拍(660ms)确认弹窗关闭 -> 恢复上一次模式(含「自动识别」), 经 autoResume 重新按下摇杆。 ---- */\nfunction pauseModal(){\n A.pauseGap=0;\n if(A.paused)return;\n A.paused=true;A.resumeMode=A.mode;A.resumeSub=A.sub;\n var b=bridge();if(b){b.setDir(0,0);if(b.autoHold)b.autoHold();}\n status('选卡/道具界面 · 已暂停');box(null);noArrow();\n}\nfunction resumeModal(){\n A.pauseGap++;\n if(A.pauseGap<3){status('等待界面关闭 '+A.pauseGap+'/3');return false;}\n A.paused=false;A.pauseGap=0;A.dittoClicked=false;\n A.mode=A.resumeMode||A.mode;A.sub=A.resumeSub||null;\n A.noTgt=0;A.miss=0;A.still=0;A.lastDist=-1;A.lowTick=0;A.jumpN=0;\n var b=bridge();if(b&&b.autoResume)b.autoResume();\n status((MIDX[A.mode]?MIDX[A.mode].cn:A.mode)+' · 已继续');\n return true;\n}\n/* ---- 方向循环(900ms 低频锁向): 暂停期间不寻路/不计数; 祭坛圈内冻结 noTgt 且方向被 guardDir 约束 ---- */\nfunction dirTick(){if(!A.on)return;\n if(A.paused){A.dirTimer=setTimeout(dirTick,900);return;}\n if(A.dodgeT>0&&A.dodgeDir){setD(A.dodgeDir.x,A.dodgeDir.y,'闪避!');box(null);noArrow();\n  A.dirTimer=setTimeout(dirTick,900);return;}\n try{var m=cur();\n  var gi=gateInfo(m);\n  if(gi){box(null);noArrow();A.sub=null;A.noTgt=0;A.miss=0;\n   setD(0,0,gi.mid);status(gi.txt);\n   A.dirTimer=setTimeout(dirTick,900);return;}\n  var P=pick();\n  if(P){var g=P.g,sm=MIDX[P.sub]||m;A.sub=P.sub;\n   setD(g.x,g.y,g.cn+' '+Math.round(g.dist));\n   box(P.scr?g.r:mapBox(g),sm.hi);arrow(g.x,g.y,sm.hi);A.miss=0;A.noTgt=0;\n  }else{box(null);noArrow();A.sub=null;\n   /* 百变怪无可复制提示(无弹窗)路径: 冻结等待升级(不导航/不停止/不循环); 弹窗路径走 dittoModal+allowedSubs, 无其它目标则照常停机 */\n   if(A.dittoNoticeHold&&(A.mode==='ditto'||A.sub==='ditto'||(A.mode==='auto'&&dittoIsNearest()))){\n    A.noTgt=0;setD(0,0,'百变怪无可复制 · 已跳过, 升级后再选');A.dirTimer=setTimeout(dirTick,900);return;}\n   if(arenaVec()){A.noTgt=0;setD(A.lastDir.x,A.lastDir.y,'祭坛圈内 · 保持');}\n   else if(hint(m)){A.noTgt=0;setD(A.lastDir.x,A.lastDir.y,'交互中');}\n   else{A.miss++;A.noTgt++;\n    if(m.k==='berry'){var sd=sparseDir();\n     A.noTgt=0;A.miss=0;\n     if(sd)setD(sd.x,sd.y,'寻树果');\n     else setD(A.lastDir.x,A.lastDir.y,'寻树果');\n     status('寻找树果 · 向宝可梦少的地方移动');\n     A.dirTimer=setTimeout(dirTick,900);return;}\n    if(A.noTgt>=5){var rs=(m.k==='auto'?'未发现('+autoNames()+')':'未发现'+m.cn)+'·已停止';\n     status(rs);var b=bridge();if(b&&b.autoStop)b.autoStop(rs);A.stop();return;}\n    setD(A.lastDir.x,A.lastDir.y,'找'+(m.k==='auto'?'目标':m.cn)+' '+A.noTgt+'/5');}}\n }catch(e){A.dirInfo='方向错误 '+e;}\n A.dirTimer=setTimeout(dirTick,900);}\n/* ---- 动作循环(220ms): 弹窗暂停/续跑; HP 守卫; 命中交互提示即点 KeyE; 卡住/rock 连点跳跃 ---- */\nfunction actTick(){if(!A.on)return;\n try{\n  if(modalOpen()){hypnoModalSkip();dittoModal();pauseModal();A.actTimer=setTimeout(actTick,220);return;}\n  if(A.paused){if(!resumeModal()){A.actTimer=setTimeout(actTick,220);return;}}\n  hpGuard();dodgeReact();\n  var m=cur();\n  var gi2=gateInfo(m);\n  if(gi2){A.still=0;A.lastDist=-1;status(gi2.txt);\n   A.actTimer=setTimeout(actTick,220);return;}\n  /* 百变怪无可复制: 提示命中即记等级跳过本轮; 当前目标确为百变怪时抑制交互触发, 破死循环 */\n  if(dittoNoCopyNotice()&&A.dittoSkipLv<0){A.dittoSkipLv=level();}\n  if(dittoHold()&&(A.mode==='ditto'||A.sub==='ditto'||dittoIsNearest())){\n   status('百变怪无可复制 · 已跳过, 升级后再选');A.actTimer=setTimeout(actTick,220);return;}\n  var sm=curSub(),a=document.querySelector('.tc-act');\n  var mt=mapTargets(coordsAct(m),m),md=mt.length?mt[0].dist:-1;\n  if(md>=0){if(A.lastDist>=0&&md>=A.lastDist-1)A.still++;else A.still=0;A.lastDist=md;}\n  else{A.still=0;A.lastDist=-1;}\n  if(hint(m)){\n   if(vis(a)){fireBtn(a);A.pickN++;status(sm.act+sm.cn+' #'+A.pickN);}\n   if(sm.k==='rock'){tapJump();A.jumpN++;}\n   A.still=0;\n  }else if(sm.k==='rock'){\n   A.lowTick++;\n   if(A.still>=6||A.lowTick%4===0){tapJump();A.jumpN++;}\n   status(A.dirInfo+(A.jumpN?' · 跳'+A.jumpN:''));\n  }else if(A.still>=6){\n   tapJump();A.jumpN++;status('跳跃越障 #'+A.jumpN);\n  }else status(A.dirInfo+(A.pickN?' · '+sm.act+A.pickN:''));\n }catch(e){status('点击错误 '+e);}\n A.actTimer=setTimeout(actTick,220);}\n/* JS 内部平滑切模式(不重启摇杆), 由 Java 菜单/桥调用; 同时经桥同步状态栏 */\nA.setMode=function(mode){if(!MIDX[mode]||mode===A.mode)return;\n A.mode=mode;A.noTgt=0;A.still=0;A.lastDist=-1;A.pickN=0;A.sub=null;};\nA.start=function(mode){if(A.on)return;A.on=true;A.mode=MIDX[mode]?mode:'rock';A.sub=null;\n A.ang=0.6;A.miss=0;A.still=0;A.lastDist=-1;A.jumpN=0;A.pickN=0;A.lowTick=0;A.noTgt=0;\n A.hpLow=false;A.hpOverride=false;A.prevMode=null;\n A.paused=false;A.pauseGap=0;A.resumeMode=null;A.resumeSub=null;\n A.dittoSkipLv=-1;A.dittoClicked=false;A.dodgeT=0;A.dodgeCd=0;A.dodgeDir=null;A.dodgeSide=false;A.lastHp=-1;A.hypnoZone=null;\n dirTick();actTick();};\nA.stop=function(){A.on=false;A.noTgt=0;A.sub=null;\n A.paused=false;A.pauseGap=0;A.resumeMode=null;A.resumeSub=null;A.dittoClicked=false;A.dodgeT=0;A.dodgeCd=0;A.dodgeDir=null;A.lastHp=-1;A.hypnoZone=null;\n if(A.dirTimer){clearTimeout(A.dirTimer);A.dirTimer=null;}\n if(A.actTimer){clearTimeout(A.actTimer);A.actTimer=null;}\n if(A.hi)A.hi.style.display='none';noArrow();\n var b=bridge();if(b)b.setDir(0,0);};\n})();\n";
    private static final String[][] AUTO_MODES = {new String[]{"auto", "自动识别"}, new String[]{"rock", "碎岩"}, new String[]{"berry", "树果"}, new String[]{"freechest", "免费精灵球"}, new String[]{"altar", "图腾祭坛"}, new String[]{"boss", "守护者祭坛"}, new String[]{"chest", "精灵球(付费)"}, new String[]{"slots", "老虎机"}, new String[]{"challenge", "挑战祭坛"}, new String[]{"cursed", "诅咒祭坛"}, new String[]{"porygon", "多边兽祭坛"}, new String[]{"dojo", "空手道馆"}, new String[]{"greed", "耿鬼祭坛"}, new String[]{"hypno", "催眠貘"}, new String[]{"snorlax", "卡比兽"}, new String[]{"fossil", "化石遗迹"}, new String[]{"rhydon", "钻角犀兽"}, new String[]{"ditto", "百变怪"}, new String[]{"nest", "宝可梦蛋巢"}, new String[]{"bill", "正辉的机器"}, new String[]{"magnet", "磁力"}};
    private static final String BTN_CSS = "#pbk-mapbtn{display:none!important}.tc-jump,.tc-dash,.tc-lock,.tc-bag,.tc-pot,.tc-ultb,.tc-act{zoom:calc(var(--tcs,1)*var(--pbk,1))!important}.tc-stick{width:calc(128px*var(--tcs,1)*var(--pbk,1))!important;height:calc(128px*var(--tcs,1)*var(--pbk,1))!important;margin:calc(-64px*var(--tcs,1)*var(--pbk,1)) 0 0 calc(-64px*var(--tcs,1)*var(--pbk,1))!important}.tc-stick>i{width:calc(58px*var(--tcs,1)*var(--pbk,1))!important;height:calc(58px*var(--tcs,1)*var(--pbk,1))!important;margin:calc(-29px*var(--tcs,1)*var(--pbk,1))!important}html.touch .hud-bl{transform:translateX(-50%) scale(calc(var(--hs,1)*var(--pbk,1)))!important;scale:none!important;transform-origin:bottom!important}.tc-fs{display:none!important}@media (orientation:landscape){html.touch .hud-tr .minimap{width:calc(140px*var(--mm,1)*1.6)!important;height:calc(140px*var(--mm,1)*1.6)!important;margin-right:34px!important;margin-bottom:calc(-39px*var(--mm,1))!important}html.touch .hud-tr .counters{position:relative!important;left:calc(62px * var(--tcs,1) / .85 / var(--hs,1))!important;top:calc(-22px + var(--st))!important}.tc-inc{zoom:1!important;scale:calc(var(--hs,1)*var(--pbk,1))!important;right:auto!important;left:calc(50% + 120px*var(--hs,1)*var(--pbk,1) + 28px)!important;bottom:calc(14px*var(--hs,1)*var(--pbk,1) + var(--sb))!important;top:auto!important}#pbk-mapbtn{display:grid!important;pointer-events:auto!important;position:fixed;z-index:999999;right:calc(62px * var(--tcs,1) + 14px + var(--sr));top:calc(16px + var(--st));width:44px;height:44px;border-radius:14px;background:#22243aee;color:#fff;box-shadow:0 0 0 2px #ffffff66,0 3px 10px #0008;place-items:center;font:900 12px/1.1 system-ui,sans-serif;touch-action:manipulation;user-select:none;cursor:pointer}#pbk-mapbtn.on{background:#2f6bd8}#pbk-mapwrap{position:fixed;inset:0;z-index:999998;background:#0b0d18cc}#pbk-mapwrap .minimap.pbk-mm-big{position:absolute!important;left:50%!important;top:50%!important;transform:translate(-50%,-50%)!important;width:var(--pbk-mm-big,min(86vmin,560px))!important;height:var(--pbk-mm-big,min(86vmin,560px))!important;margin:0!important;pointer-events:none!important;box-shadow:0 0 0 5px #fff,0 0 0 11px #e8243a,0 0 0 15px #ffffff22,0 24px 80px #000c!important;animation:pbkmmpop .18s ease-out}@keyframes pbkmmpop{from{opacity:.4}to{opacity:1}}#pbk-maptip{position:absolute;bottom:24px;left:50%;transform:translateX(-50%);color:#fff;font:700 14px system-ui,sans-serif;text-shadow:0 2px 6px #000}}";
    private static final double BTN_SCALE_LANDSCAPE = 1.3d;
    private static final String MAP_JS = "(function init(n){if(document.getElementById('pbk-mapbtn'))return;var mm=document.querySelector('.minimap');if(!mm){if(n<40)setTimeout(function(){init(n+1)},300);return;}var btn=document.createElement('div');btn.id='pbk-mapbtn';btn.textContent='放大';document.body.appendChild(btn);var wrap=null,big=false,home=null,next=null;function open(){if(big)return;var m=document.querySelector('.minimap');if(!m)return;var r=m.getBoundingClientRect();var cur=Math.max(r.width,r.height);var side=Math.min(innerWidth,innerHeight);var t=Math.min(Math.max(cur*1.35,side*0.86),side*0.96);if(t<cur)t=cur;m.style.setProperty('--pbk-mm-big',Math.round(t)+'px');home=m.parentNode;next=m.nextSibling;wrap=document.createElement('div');wrap.id='pbk-mapwrap';var tip=document.createElement('div');tip.id='pbk-maptip';tip.textContent='点击任意处收起';wrap.appendChild(m);wrap.appendChild(tip);document.body.appendChild(wrap);m.classList.add('pbk-mm-big');btn.classList.add('on');btn.textContent='收起';big=true;wrap.addEventListener('click',function(e){e.stopPropagation();close();});}function close(){if(!big)return;big=false;var m=document.querySelector('.minimap');if(m){m.classList.remove('pbk-mm-big');m.style.removeProperty('--pbk-mm-big');if(home&&home.isConnected){if(next&&next.parentNode===home)home.insertBefore(m,next);else home.insertBefore(m,home.firstChild);}else{var tr=document.querySelector('.hud-tr')||document.body;tr.appendChild(m);}}if(wrap&&wrap.parentNode)wrap.parentNode.removeChild(wrap);wrap=null;btn.classList.remove('on');btn.textContent='放大';}btn.addEventListener('click',function(e){e.preventDefault();e.stopPropagation();big?close():open();});window.__pbkMapClose=close;})(0);";
    private static final String URL_GUIDE = "https://pokebonk-moves.app.workbuddy.host/";
    private static final String URL_HOME = "https://pokebonk.com/";
    private LinearLayout autoBarView;
    private PbAutoBridge autoBridge;
    private TextView autoStat;
    private FrameLayout capsule;
    private int itemW;
    private TextView menuBtn;
    private LinearLayout menuPanel;
    private View menuScrim;
    private ScrollView menuScroll;
    private TextView menuStay;
    private LinkedHashMap<String, TextView> modeViews;
    private View pill;
    private FrameLayout root;
    private TextView tabGuide;
    private TextView tabHome;
    private TextView tabLand;
    private WebView webGuide;
    private WebView webHome;
    private final Handler autoHandler = new Handler(Looper.getMainLooper());
    private long autoDownTime = 0;
    private boolean autoDownSent = false;
    private boolean autoOn = false;
    private float autoAx = 0.0f;
    private float autoAy = 0.0f;
    private float curVx = 0.3f;
    private float curVy = -1.0f;
    private float tgtVx = 0.3f;
    private float tgtVy = -1.0f;
    private boolean menuOpen = false;
    private boolean keepOnLandscape = true;
    private String autoMode = "rock";
    private final Runnable autoTick = new Runnable() { // from class: com.ziyuan.Assistant.PokeRogueActivity.1
        @Override // java.lang.Runnable
        public void run() {
            if (PokeRogueActivity.this.autoOn) {
                PokeRogueActivity.this.curVx += (PokeRogueActivity.this.tgtVx - PokeRogueActivity.this.curVx) * 0.22f;
                PokeRogueActivity.this.curVy += (PokeRogueActivity.this.tgtVy - PokeRogueActivity.this.curVy) * 0.22f;
                try {
                    PokeRogueActivity pokeRogueActivity = PokeRogueActivity.this;
                    pokeRogueActivity.dispatchStick(pokeRogueActivity.curVx, PokeRogueActivity.this.curVy);
                } catch (Throwable unused) {
                }
                PokeRogueActivity.this.autoHandler.postDelayed(this, 50L);
            }
        }
    };
    private boolean landscapeLocked = false;
    private boolean guideLoaded = false;
    private int current = 0;
    private boolean dark = true;

    /* JADX INFO: Access modifiers changed from: private */
    /* loaded from: classes.dex */
    public final class PbAutoBridge {
        private PbAutoBridge() {
        }

        /* synthetic */ PbAutoBridge(PokeRogueActivity pokeRogueActivity, PbAutoBridge pbAutoBridge) {
            this();
        }

        @JavascriptInterface
        public void autoHold() {
            PokeRogueActivity.this.autoHandler.post(new Runnable() { // from class: com.ziyuan.Assistant.PokeRogueActivity.PbAutoBridge.4
                @Override // java.lang.Runnable
                public void run() {
                    if (PokeRogueActivity.this.autoOn) {
                        PokeRogueActivity.this.autoHandler.removeCallbacks(PokeRogueActivity.this.autoTick);
                        PokeRogueActivity.this.releaseStick();
                    }
                }
            });
        }

        @JavascriptInterface
        public void autoResume() {
            PokeRogueActivity.this.autoHandler.post(new Runnable() { // from class: com.ziyuan.Assistant.PokeRogueActivity.PbAutoBridge.5
                @Override // java.lang.Runnable
                public void run() {
                    if (PokeRogueActivity.this.autoOn) {
                        PokeRogueActivity.this.autoDownSent = false;
                        PokeRogueActivity.this.autoHandler.removeCallbacks(PokeRogueActivity.this.autoTick);
                        PokeRogueActivity.this.autoHandler.post(PokeRogueActivity.this.autoTick);
                    }
                }
            });
        }

        @JavascriptInterface
        public void autoStop(final String str) {
            PokeRogueActivity.this.autoHandler.post(new Runnable() { // from class: com.ziyuan.Assistant.PokeRogueActivity.PbAutoBridge.3
                @Override // java.lang.Runnable
                public void run() {
                    if (PokeRogueActivity.this.autoOn) {
                        PokeRogueActivity.this.stopAuto(str);
                    }
                }
            });
        }

        @JavascriptInterface
        public void autoSwitch(final String str) {
            PokeRogueActivity.this.autoHandler.post(new Runnable() { // from class: com.ziyuan.Assistant.PokeRogueActivity.PbAutoBridge.6
                @Override // java.lang.Runnable
                public void run() {
                    if (PokeRogueActivity.this.autoOn) {
                        PokeRogueActivity.this.autoMode = str;
                        if (PokeRogueActivity.this.autoStat != null) {
                            PokeRogueActivity.this.autoStat.setText(String.valueOf(PokeRogueActivity.modeName(str)) + " · 运行中");
                        }
                        PokeRogueActivity.this.syncMenu();
                    }
                }
            });
        }

        @JavascriptInterface
        public void setDir(final float f3, final float f4) {
            PokeRogueActivity.this.autoHandler.post(new Runnable() { // from class: com.ziyuan.Assistant.PokeRogueActivity.PbAutoBridge.1
                @Override // java.lang.Runnable
                public void run() {
                    PokeRogueActivity.this.tgtVx = f3;
                    PokeRogueActivity.this.tgtVy = f4;
                }
            });
        }

        @JavascriptInterface
        public void setStatus(final String str) {
            PokeRogueActivity.this.autoHandler.post(new Runnable() { // from class: com.ziyuan.Assistant.PokeRogueActivity.PbAutoBridge.2
                @Override // java.lang.Runnable
                public void run() {
                    if (PokeRogueActivity.this.autoStat != null) {
                        PokeRogueActivity.this.autoStat.setText(str);
                    }
                }
            });
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void applyButtonScale() {
        if (this.webHome == null) {
            return;
        }
        boolean z2 = getResources().getConfiguration().orientation == 2;
        String valueOf = z2 ? String.valueOf(BTN_SCALE_LANDSCAPE) : "1.0";
        StringBuilder sb = new StringBuilder("(function(){try{document.documentElement.style.setProperty('--pbk',");
        sb.append(valueOf);
        sb.append(");");
        sb.append(z2 ? "" : "try{if(window.__pbkMapClose)window.__pbkMapClose()}catch(e){}");
        sb.append("var s=document.getElementById('pbk-zoom');");
        sb.append("if(!s){s=document.createElement('style');s.id='pbk-zoom';");
        sb.append("s.textContent='");
        sb.append(BTN_CSS);
        sb.append("';document.head.appendChild(s);}");
        sb.append(MAP_JS);
        sb.append(AUTO_JS);
        sb.append("}catch(e){}})()");
        try {
            this.webHome.evaluateJavascript(sb.toString(), null);
        } catch (Throwable unused) {
        }
    }

    /* JADX WARN: Code restructure failed: missing block: B:11:0x0021, code lost:
    
        r0 = r0.getInsetsController();
     */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    private void applyImmersive() {
        Window w = getWindow();
        if (w == null) return;
        View dv = w.getDecorView();
        if (Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false);
            if (dv != null && dv.isAttachedToWindow()) {
                WindowInsetsController c = w.getInsetsController();
                if (c != null) {
                    c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                    c.hide(WindowInsets.Type.systemBars());
                }
            }
        } else if (dv != null) {
            dv.setSystemUiVisibility(5894);
        }
    }

    private void applyOrientation() {
        if (this.capsule == null) {
            return;
        }
        this.capsule.setVisibility(getResources().getConfiguration().orientation == 2 ? 8 : 0);
        applyImmersive();
        updateKeepScreenOn();
        applyButtonScale();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void closeMenu() {
        this.menuOpen = false;
        View view = this.menuScrim;
        if (view != null) {
            view.setVisibility(8);
        }
        ScrollView scrollView = this.menuScroll;
        if (scrollView != null) {
            scrollView.setVisibility(8);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void dispatchStick(float f3, float f4) {
        WebView webView = this.webHome;
        if (webView == null || !this.autoOn) {
            return;
        }
        int width = webView.getWidth();
        int height = this.webHome.getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        if (!this.autoDownSent) {
            this.autoAx = width * 0.15f;
            this.autoAy = height * 0.8f;
            long uptimeMillis = SystemClock.uptimeMillis();
            this.autoDownTime = uptimeMillis;
            MotionEvent obtain = MotionEvent.obtain(uptimeMillis, uptimeMillis, 0, this.autoAx, this.autoAy, 0);
            obtain.setSource(ConstData.INTENT_SCHEDULE_SHOW_REFRESH);
            this.webHome.dispatchTouchEvent(obtain);
            obtain.recycle();
            this.autoDownSent = true;
        }
        float min = Math.min(width, height) * 0.2f;
        long uptimeMillis2 = SystemClock.uptimeMillis();
        double d3 = uptimeMillis2 * 0.017d;
        MotionEvent obtain2 = MotionEvent.obtain(this.autoDownTime, uptimeMillis2, 2, this.autoAx + (f3 * min) + (((float) Math.sin(d3)) * 2.0f), this.autoAy + (f4 * min) + (((float) Math.cos(d3)) * 2.0f), 0);
        obtain2.setSource(ConstData.INTENT_SCHEDULE_SHOW_REFRESH);
        this.webHome.dispatchTouchEvent(obtain2);
        obtain2.recycle();
    }

    private int dp(int i3) {
        return (int) ((i3 * getResources().getDisplayMetrics().density) + 0.5f);
    }

    private void evalAuto(String str) {
        WebView webView = this.webHome;
        if (webView == null) {
            return;
        }
        try {
            webView.evaluateJavascript(str, null);
        } catch (Throwable unused) {
        }
    }

    private GradientDrawable makeMenuBg() {
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setCornerRadius(dp(12));
        gradientDrawable.setColor(-233168842);
        gradientDrawable.setStroke(dp(1), -6297857);
        return gradientDrawable;
    }

    private TextView makeMenuItem(String str) {
        TextView textView = new TextView(this);
        textView.setText(str);
        textView.setTextSize(12.0f);
        textView.setTypeface(Typeface.DEFAULT_BOLD);
        textView.setTextColor(-6297857);
        textView.setGravity(16);
        textView.setPadding(dp(14), dp(11), dp(14), dp(11));
        textView.setClickable(true);
        return textView;
    }

    private TextView makeTab(String str) {
        TextView textView = new TextView(this);
        textView.setText(str);
        textView.setTextSize(12.5f);
        textView.setGravity(17);
        return textView;
    }

    private WebView makeWeb(boolean z2) {
        WebView webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setMixedContentMode(0);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setCacheMode(-1);
        settings.setUserAgentString("Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 PokeBonkAndroid/1.0.1");
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() { // from class: com.ziyuan.Assistant.PokeRogueActivity.10
            @Override // android.webkit.WebViewClient
            public void onPageFinished(WebView webView2, String str) {
                if (webView2 == PokeRogueActivity.this.webHome) {
                    PokeRogueActivity.this.applyButtonScale();
                }
            }

            @Override // android.webkit.WebViewClient
            public void onReceivedSslError(WebView webView2, SslErrorHandler sslErrorHandler, SslError sslError) {
                sslErrorHandler.proceed();
            }

            @Override // android.webkit.WebViewClient
            public boolean shouldOverrideUrlLoading(WebView webView2, String str) {
                return false;
            }
        });
        try {
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        } catch (Throwable unused) {
        }
        if (z2) {
            PbAutoBridge pbAutoBridge = new PbAutoBridge(this, null);
            this.autoBridge = pbAutoBridge;
            webView.addJavascriptInterface(pbAutoBridge, "AndroidBridge");
        }
        return webView;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static String modeName(String str) {
        for (String[] strArr : AUTO_MODES) {
            if (strArr[0].equals(str)) {
                return strArr[1];
            }
        }
        return str;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void releaseStick() {
        if (this.webHome != null && this.autoDownSent) {
            MotionEvent obtain = MotionEvent.obtain(this.autoDownTime, SystemClock.uptimeMillis(), 1, this.autoAx, this.autoAy, 0);
            obtain.setSource(ConstData.INTENT_SCHEDULE_SHOW_REFRESH);
            this.webHome.dispatchTouchEvent(obtain);
            obtain.recycle();
        }
        this.autoDownSent = false;
        this.autoDownTime = 0L;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void selectTab(int i3) {
        this.current = i3;
        this.webHome.setVisibility(i3 == 0 ? 0 : 8);
        this.webGuide.setVisibility(i3 == 1 ? 0 : 8);
        if (i3 == 1 && !this.guideLoaded) {
            this.guideLoaded = true;
            this.webGuide.loadUrl(URL_GUIDE);
        }
        int i4 = this.dark ? -7695194 : -8616300;
        styleTab(this.tabHome, i3 == 0, i4);
        styleTab(this.tabGuide, i3 == 1, i4);
        ValueAnimator ofFloat = ValueAnimator.ofFloat(this.pill.getTranslationX(), i3 * this.itemW);
        ofFloat.setDuration(240L);
        ofFloat.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() { // from class: com.ziyuan.Assistant.PokeRogueActivity.11
            @Override // android.animation.ValueAnimator.AnimatorUpdateListener
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                PokeRogueActivity.this.pill.setTranslationX(((Float) valueAnimator.getAnimatedValue()).floatValue());
            }
        });
        ofFloat.start();
    }

    private void startAuto(String str) {
        this.autoMode = str;
        this.autoOn = true;
        this.autoDownSent = false;
        this.tgtVx = 0.3f;
        this.curVx = 0.3f;
        this.tgtVy = -1.0f;
        this.curVy = -1.0f;
        evalAuto("if(window.__pbAuto)window.__pbAuto.start('" + str + "')");
        this.autoHandler.removeCallbacks(this.autoTick);
        this.autoHandler.post(this.autoTick);
        TextView textView = this.autoStat;
        if (textView != null) {
            textView.setText(String.valueOf(modeName(str)) + " · 运行中");
        }
        syncMenu();
    }

    private void stopAuto() {
        stopAuto(null);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void stopAuto(String str) {
        this.autoOn = false;
        this.autoHandler.removeCallbacks(this.autoTick);
        evalAuto("if(window.__pbAuto)window.__pbAuto.stop()");
        releaseStick();
        TextView textView = this.autoStat;
        if (textView != null) {
            if (str == null || str.length() <= 0) {
                str = "已停止";
            }
            textView.setText(str);
        }
        syncMenu();
    }

    private void styleTab(TextView textView, boolean z2, int i3) {
        if (z2) {
            i3 = -1;
        }
        textView.setTextColor(i3);
        textView.setTypeface(z2 ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void syncMenu() {
        LinkedHashMap<String, TextView> linkedHashMap = this.modeViews;
        if (linkedHashMap != null) {
            for (Map.Entry<String, TextView> entry : linkedHashMap.entrySet()) {
                boolean z2 = this.autoOn && entry.getKey().equals(this.autoMode);
                TextView value = entry.getValue();
                StringBuilder sb = new StringBuilder(String.valueOf(modeName(entry.getKey())));
                sb.append(z2 ? " · 停止" : " · 启动");
                value.setText(sb.toString());
                entry.getValue().setTextColor(z2 ? -30070 : "auto".equals(entry.getKey()) ? -11718 : -6297857);
            }
        }
        TextView textView = this.menuStay;
        if (textView != null) {
            StringBuilder sb2 = new StringBuilder("横屏常亮 · ");
            sb2.append(this.keepOnLandscape ? "开" : "关");
            textView.setText(sb2.toString());
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void toggleMenu() {
        if (this.menuOpen) {
            closeMenu();
            return;
        }
        this.menuOpen = true;
        this.menuScrim.setVisibility(0);
        this.menuScroll.setVisibility(0);
        this.autoBarView.bringToFront();
        this.menuScroll.bringToFront();
        syncMenu();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void toggleMode(String str) {
        if (this.autoOn) {
            stopAuto();
            if (str.equals(this.autoMode)) {
                return;
            }
        }
        startAuto(str);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void updateKeepScreenOn() {
        if ((getResources().getConfiguration().orientation == 2) && this.keepOnLandscape) {
            getWindow().addFlags(128);
        } else {
            getWindow().clearFlags(128);
        }
    }

    @Override // android.app.Activity, android.content.ComponentCallbacks
    public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        if (configuration.orientation == 1) {
            this.landscapeLocked = false;
        }
        applyOrientation();
    }

    @Override // android.app.Activity
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        this.dark = getSharedPreferences("settings", 0).getBoolean("dark", true);
        FrameLayout frameLayout = new FrameLayout(this);
        this.root = frameLayout;
        frameLayout.setBackgroundColor(this.dark ? -16777216 : -1);
        this.webHome = makeWeb(true);
        this.webGuide = makeWeb(false);
        this.root.addView(this.webHome, new FrameLayout.LayoutParams(-1, -1));
        this.root.addView(this.webGuide, new FrameLayout.LayoutParams(-1, -1));
        this.webGuide.setVisibility(8);
        this.itemW = Math.min(dp(78), (getResources().getDisplayMetrics().widthPixels - dp(20)) / 6);
        boolean z2 = this.dark;
        int i3 = z2 ? -12751361 : -15304705;
        int i4 = z2 ? -10847272 : -16032810;
        this.capsule = new FrameLayout(this);
        GradientDrawable.Orientation orientation = GradientDrawable.Orientation.TOP_BOTTOM;
        int[] iArr = new int[2];
        boolean z3 = this.dark;
        iArr[0] = z3 ? 536870911 : 1946157055;
        iArr[1] = z3 ? 184549375 : 1508898303;
        GradientDrawable gradientDrawable = new GradientDrawable(orientation, iArr);
        gradientDrawable.setCornerRadius(dp(22));
        gradientDrawable.setStroke(dp(1), this.dark ? 1090519039 : 1728053247);
        this.capsule.setBackground(gradientDrawable);
        this.pill = new View(this);
        GradientDrawable gradientDrawable2 = new GradientDrawable();
        gradientDrawable2.setCornerRadius(dp(16));
        gradientDrawable2.setColor(i3);
        gradientDrawable2.setStroke(dp(1), i4);
        this.pill.setBackground(gradientDrawable2);
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(this.itemW - dp(10), dp(36), 8388627);
        layoutParams.leftMargin = dp(5);
        this.capsule.addView(this.pill, layoutParams);
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(0);
        this.capsule.addView(linearLayout, new FrameLayout.LayoutParams(-2, -2));
        this.tabHome = makeTab("首页");
        this.tabGuide = makeTab("攻略");
        TextView makeTab = makeTab("横屏");
        this.tabLand = makeTab;
        makeTab.setTextColor(this.dark ? -7359233 : -15304705);
        this.tabLand.setTypeface(Typeface.DEFAULT_BOLD);
        this.tabHome.setOnClickListener(new View.OnClickListener() { // from class: com.ziyuan.Assistant.PokeRogueActivity.2
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                PokeRogueActivity.this.selectTab(0);
            }
        });
        this.tabGuide.setOnClickListener(new View.OnClickListener() { // from class: com.ziyuan.Assistant.PokeRogueActivity.3
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                PokeRogueActivity.this.selectTab(1);
            }
        });
        this.tabLand.setOnClickListener(new View.OnClickListener() { // from class: com.ziyuan.Assistant.PokeRogueActivity.4
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                PokeRogueActivity.this.landscapeLocked = true;
                PokeRogueActivity.this.setRequestedOrientation(6);
            }
        });
        linearLayout.addView(this.tabHome, new LinearLayout.LayoutParams(this.itemW, dp(42)));
        linearLayout.addView(this.tabGuide, new LinearLayout.LayoutParams(this.itemW, dp(42)));
        linearLayout.addView(this.tabLand, new LinearLayout.LayoutParams(this.itemW, dp(42)));
        FrameLayout.LayoutParams layoutParams2 = new FrameLayout.LayoutParams(-2, -2, 81);
        layoutParams2.bottomMargin = dp(12);
        this.root.addView(this.capsule, layoutParams2);
        LinearLayout linearLayout2 = new LinearLayout(this);
        this.autoBarView = linearLayout2;
        linearLayout2.setOrientation(1);
        this.autoBarView.setGravity(1);
        TextView textView = new TextView(this);
        this.menuBtn = textView;
        textView.setText("o");
        this.menuBtn.setTextSize(15.0f);
        this.menuBtn.setTypeface(Typeface.DEFAULT_BOLD);
        this.menuBtn.setGravity(17);
        this.menuBtn.setTextColor(-1);
        GradientDrawable gradientDrawable3 = new GradientDrawable();
        gradientDrawable3.setShape(1);
        gradientDrawable3.setColor(-433970118);
        gradientDrawable3.setStroke(dp(2), -6297857);
        this.menuBtn.setBackground(gradientDrawable3);
        this.menuBtn.setClickable(true);
        this.menuBtn.setOnClickListener(new View.OnClickListener() { // from class: com.ziyuan.Assistant.PokeRogueActivity.5
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                PokeRogueActivity.this.toggleMenu();
            }
        });
        this.autoBarView.addView(this.menuBtn, new LinearLayout.LayoutParams(dp(30), dp(30)));
        TextView textView2 = new TextView(this);
        this.autoStat = textView2;
        textView2.setText("未启动");
        this.autoStat.setTextSize(10.0f);
        this.autoStat.setTextColor(-6297857);
        this.autoStat.setGravity(17);
        this.autoStat.setBackgroundColor(-871690984);
        this.autoStat.setSingleLine(true);
        this.autoBarView.addView(this.autoStat, new LinearLayout.LayoutParams(dp(96), dp(18)));
        FrameLayout.LayoutParams layoutParams3 = new FrameLayout.LayoutParams(-2, -2, 8388661);
        layoutParams3.topMargin = dp(96);
        layoutParams3.rightMargin = dp(10);
        this.root.addView(this.autoBarView, layoutParams3);
        View view = new View(this);
        this.menuScrim = view;
        view.setBackgroundColor(16777216);
        this.menuScrim.setClickable(true);
        this.menuScrim.setOnClickListener(new View.OnClickListener() { // from class: com.ziyuan.Assistant.PokeRogueActivity.6
            @Override // android.view.View.OnClickListener
            public void onClick(View view2) {
                PokeRogueActivity.this.closeMenu();
            }
        });
        this.menuScrim.setVisibility(8);
        this.root.addView(this.menuScrim, new FrameLayout.LayoutParams(-1, -1));
        ScrollView scrollView = new ScrollView(this);
        this.menuScroll = scrollView;
        scrollView.setBackground(makeMenuBg());
        this.menuScroll.setVerticalScrollBarEnabled(true);
        this.menuScroll.setFillViewport(false);
        LinearLayout linearLayout3 = new LinearLayout(this);
        this.menuPanel = linearLayout3;
        linearLayout3.setOrientation(1);
        this.menuScroll.addView(this.menuPanel, new FrameLayout.LayoutParams(-1, -2));
        TextView textView3 = new TextView(this);
        textView3.setText("自动 · 菜单（上下滑动）");
        textView3.setTextSize(11.0f);
        textView3.setTypeface(Typeface.DEFAULT_BOLD);
        textView3.setTextColor(-11718);
        textView3.setGravity(17);
        textView3.setPadding(dp(10), dp(12), dp(10), dp(10));
        this.menuPanel.addView(textView3, new LinearLayout.LayoutParams(-1, -2));
        this.modeViews = new LinkedHashMap<>();
        int i5 = 0;
        while (true) {
            String[][] strArr = AUTO_MODES;
            if (i5 >= strArr.length) {
                TextView makeMenuItem = makeMenuItem("横屏常亮 · 开");
                this.menuStay = makeMenuItem;
                makeMenuItem.setOnClickListener(new View.OnClickListener() { // from class: com.ziyuan.Assistant.PokeRogueActivity.8
                    @Override // android.view.View.OnClickListener
                    public void onClick(View view2) {
                        PokeRogueActivity.this.keepOnLandscape = !PokeRogueActivity.this.keepOnLandscape;
                        PokeRogueActivity.this.updateKeepScreenOn();
                        PokeRogueActivity.this.syncMenu();
                    }
                });
                this.menuPanel.addView(this.menuStay, new LinearLayout.LayoutParams(-1, -2));
                TextView makeMenuItem2 = makeMenuItem("关闭菜单 ×");
                makeMenuItem2.setTextColor(-30070);
                makeMenuItem2.setOnClickListener(new View.OnClickListener() { // from class: com.ziyuan.Assistant.PokeRogueActivity.9
                    @Override // android.view.View.OnClickListener
                    public void onClick(View view2) {
                        PokeRogueActivity.this.closeMenu();
                    }
                });
                this.menuPanel.addView(makeMenuItem2, new LinearLayout.LayoutParams(-1, -2));
                this.menuScroll.setVisibility(8);
                FrameLayout.LayoutParams layoutParams4 = new FrameLayout.LayoutParams(dp(190), -1, 8388661);
                layoutParams4.topMargin = dp(132);
                layoutParams4.bottomMargin = dp(10);
                layoutParams4.rightMargin = dp(10);
                this.root.addView(this.menuScroll, layoutParams4);
                syncMenu();
                setContentView(this.root);
                this.webHome.loadUrl(URL_HOME);
                selectTab(0);
                applyOrientation();
                return;
            }
            String[] strArr2 = strArr[i5];
            final String str = strArr2[0];
            TextView makeMenuItem3 = makeMenuItem(String.valueOf(strArr2[1]) + " · 启动");
            if ("auto".equals(str)) {
                makeMenuItem3.setTextSize(13.0f);
            }
            makeMenuItem3.setOnClickListener(new View.OnClickListener() { // from class: com.ziyuan.Assistant.PokeRogueActivity.7
                @Override // android.view.View.OnClickListener
                public void onClick(View view2) {
                    PokeRogueActivity.this.toggleMode(str);
                    PokeRogueActivity.this.closeMenu();
                }
            });
            this.menuPanel.addView(makeMenuItem3, new LinearLayout.LayoutParams(-1, -2));
            this.modeViews.put(str, makeMenuItem3);
            i5++;
        }
    }

    @Override // android.app.Activity
    protected void onDestroy() {
        this.autoOn = false;
        this.autoHandler.removeCallbacksAndMessages(null);
        try {
            WebView webView = this.webHome;
            if (webView != null) {
                webView.destroy();
            }
        } catch (Throwable unused) {
        }
        try {
            WebView webView2 = this.webGuide;
            if (webView2 != null) {
                webView2.destroy();
            }
        } catch (Throwable unused2) {
        }
        super.onDestroy();
    }

    @Override // android.app.Activity, android.view.KeyEvent.Callback
    public boolean onKeyDown(int i3, KeyEvent keyEvent) {
        if (i3 == 4) {
            if (this.landscapeLocked) {
                this.landscapeLocked = false;
                setRequestedOrientation(-1);
                return true;
            }
            WebView webView = this.current == 0 ? this.webHome : this.webGuide;
            if (webView != null && webView.canGoBack()) {
                webView.goBack();
                return true;
            }
        }
        return super.onKeyDown(i3, keyEvent);
    }

    @Override // android.app.Activity
    protected void onPause() {
        stopAuto();
        closeMenu();
        super.onPause();
    }

    @Override // android.app.Activity
    protected void onResume() {
        super.onResume();
        applyImmersive();
    }

    @Override // android.app.Activity, android.view.Window.Callback
    public void onWindowFocusChanged(boolean z2) {
        super.onWindowFocusChanged(z2);
        if (z2) {
            applyImmersive();
        }
    }
}
