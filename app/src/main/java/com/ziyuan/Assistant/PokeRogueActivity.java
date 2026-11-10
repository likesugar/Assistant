package com.ziyuan.Assistant;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.res.Configuration;
import android.content.pm.ActivityInfo;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 宝可梦幸存者: 独立双页容器。
 * 「首页」= https://pokebonk.com/ (游戏)  「攻略」= https://pokebonk-moves.app.workbuddy.host/
 * 两个独立 WebView 叠放保活, 底部胶囊「首页/攻略」两页签只切可见性 —— 切走再切回不重载, 游戏进度/攻略滚动位置保留。
 * 第三格「横屏」是动作按钮: 点击 setRequestedOrientation(SENSOR_LANDSCAPE) 主动切横屏玩,
 * 配合 Manifest configChanges 不重建 WebView; 横屏胶囊隐藏(全屏游戏), 按返回键回竖屏。
 * 胶囊尺寸/样式与首页 CapsuleBottomBar 对齐(去阴影)。
 * 与首页其它卡片完全独立, 不复用娱乐/网页转应用等逻辑。
 */
public class PokeRogueActivity extends Activity {

    private static final String URL_HOME  = "https://pokebonk.com/";
    private static final String URL_GUIDE = "https://pokebonk-moves.app.workbuddy.host/";

    /** 横屏时游戏触摸按键放大系数(对齐官方 App 手感), 竖屏 1.0 原样。 */
    private static final double BTN_SCALE_LANDSCAPE = 1.3;
    /** 独立 CSS 变量 --pbk 叠加放大, 三个区域各自的原生缩放变量都保留(乘法叠加, 不废掉游戏内设置):
     *  1) 右下动作键: 游戏用 .tc-btn{zoom:var(--tcs,1)} —— zoom 会连同 right/bottom 一起等比缩放、
     *     自动拉开不重叠。必须沿用 zoom; 早期用 transform:scale 只视觉放大不改占位, 导致按钮互相压叠。
     *  2) 摇杆 .tc-stick 不吃 zoom(非 .tc-btn), 用 width/margin 的 calc 乘法以中心等比放大。
     *  3) 中下道具格 .hud-bl(touch 模式底部居中)归 --hs(hudScale) 管, 把 --pbk 乘进它的 transform。
     *  !important 压过游戏外链/内联样式; 右上角 .tc-sm 小按钮不在放大列(保持游戏原生 zoom:1)。
     *
     *  地图放大(VC=96): 右上小地图 .minimap 是 canvas, 触摸模式基准 140px*--mm。乘 1.6 放大并左移 34px
     *  (VC=95 是 1.5/左移 20px, 本次「往左一点再放大一点点」), 保留游戏内 mapScale 设置可继续调。
     *  高度增量用等量负 margin-bottom 抵消, 避免把下方 .counters 推去压住大圆动作键。
     *
     *  右上按钮与「精灵球 0」计数(VC=96): 撤销 VC=93/94 对 .tc-aim/.tc-pause 的位移, 保持游戏原生
     *  (暂停 top:12 在上、自动 top:70 在下)。「精灵球 0」计数器 .counters(JS: t.kills)是 .hud-tr
     *  flex 竖列的成员。VC=95 用简化 CSS 探针算出 top:-69px 能放到自动下方, 真机却反馈重合 ——
     *  真实 .hud-tr 带 transform:scale(.85)/scale:var(--hs) 双重缩放, 简化探针漏了, 实测 counter
     *  反压在自动按钮内(counter_aim 重叠 24px@hs=1)。本次按真实规则复现并把 top 改 -22px 下移约 40px,
     *  counter_aim 变 +16px@hs=1 / +44px@hs=1.2 间隙, 全部不重叠; 保留 flex 占位, .kgoal 不动。竖屏不改动。
     *
     *  紫色香炉 .tc-inc 移位: 原锚右下 right:50/bottom:232, 与一堆动作键挤在一起。按用户要求
     *  横屏移到中下道具栏「黄色挂锁格」右边的草地空白(锁格中心约屏幕 50%+120px, 视觉放大后右缘
     *  约 50%+156px, 故 left 取 50%+175px)。此处不能用 zoom —— zoom 会把 left/bottom 一起放大导致
     *  坐标漂移; 改用独立 scale 属性(只缩放自身、不改定位基准, 且与 game 的 transform:.88 按下态
     *  叠加而非冲突)。竖屏不应用(保持游戏原生右下位置)。不碰 display: 游戏每秒写内联显隐照常生效。
     *
     *  VC=103: ①「精灵球 0」右缘没和 .tc-pause/.tc-aim 对齐 —— left:62px 的补偿会被 .hud-tr 的
     *  transform:scale(.85) × scale:var(--hs) 缩掉, 实测偏左 9px; 改 62px*--tcs/.85/--hs 精确补偿,
     *  5 组缩放组合下右缘差 0px。②地图圆右上红圈处加「放大」按钮, 点击把 minimap 居中放大成实时大图,
     *  点任意处收起复位; 见 MAP_JS。 */
    private static final String BTN_CSS =
        "#pbk-mapbtn{display:none!important}"
      + ".tc-jump,.tc-dash,.tc-lock,.tc-bag,.tc-pot,.tc-ultb,.tc-act"
      + "{zoom:calc(var(--tcs,1)*var(--pbk,1))!important}"
      + ".tc-stick{width:calc(128px*var(--tcs,1)*var(--pbk,1))!important;"
      + "height:calc(128px*var(--tcs,1)*var(--pbk,1))!important;"
      + "margin:calc(-64px*var(--tcs,1)*var(--pbk,1)) 0 0 calc(-64px*var(--tcs,1)*var(--pbk,1))!important}"
      + ".tc-stick>i{width:calc(58px*var(--tcs,1)*var(--pbk,1))!important;"
      + "height:calc(58px*var(--tcs,1)*var(--pbk,1))!important;"
      + "margin:calc(-29px*var(--tcs,1)*var(--pbk,1))!important}"
      + "html.touch .hud-bl{transform:translateX(-50%) scale(calc(var(--hs,1)*var(--pbk,1)))!important;"
      + "scale:none!important;transform-origin:bottom!important}"
      // 游戏每帧 update() 会写 this.fs.style.display, WebView 不支持 HTML5 Fullscreen 时
      // 该「进入全屏」入口常驻(官方 Capacitor 壳全屏成功后自动隐藏), 用 !important 压过内联样式
      + ".tc-fs{display:none!important}"
      // 以下两条只在横屏生效, 竖屏完全保持游戏原生(与既有「横屏才对齐」一致)
      + "@media (orientation:landscape){"
      // 右上小地图再放大(基准 140px*--mm): 1.25 -> 1.5, 并整体左移 20px。
      // 高度比 1.25 多出 35px, 用 -25px 负 margin-bottom 抵消大部分: 既不让 .counters
      // (精灵球计数)被推下去压住大圆动作键, 又让它下移 10px 避开放大后地图圆的右下边缘。
      + "html.touch .hud-tr .minimap{width:calc(140px*var(--mm,1)*1.6)!important;"
      + "height:calc(140px*var(--mm,1)*1.6)!important;margin-right:34px!important;"
      + "margin-bottom:calc(-39px*var(--mm,1))!important}"
      // 按最初图恢复右上两按钮原生位置 —— 不注入 .tc-aim / .tc-pause 规则即回到暂停上、自动下。
      // 只调「精灵球 0」计数器 .counters: 相对定位往下移, 脱离「自动」按钮(VC=95 真机重合)。
      // 用真实 CSS(.hud-tr transform:scale(.85) + scale:var(--hs) + gap:6px/top:8px)复现实测:
      //   VC=95 top:-69px -> counter[1137,112,49,24], 自动底 136, 重叠 24px(@hs=1)。
      //   VC=96 top:-22px -> counter[1137,152,49,24], 间隙 +16px(@hs=1) / +44px(@hs=1.2), 全不重叠。
      // relative 保留 flex 占位: 下方 .kgoal 不上移; 计数器到地图圆心距 137 > 半径 95。
      + "html.touch .hud-tr .counters{position:relative!important;"
      + "left:calc(62px * var(--tcs,1) / .85 / var(--hs,1))!important;"
      + "top:calc(-22px + var(--st))!important}"
      // 紫色香炉移到黄色挂锁格右边的草地空白。
      // 锁格位置 = 视口中心 + 120px(slots 半宽) * --hs * --pbk; 把香炉绑到同一表达式,
      // 游戏内改 UI 缩放时两者同步, 不会漂移。独立 scale 属性只缩放自身、不改 left/bottom 定位基准,
      // 也不会像 zoom 那样把定位坐标一起放大; 且与游戏 transform:.88 按下态叠加而非冲突。
      + ".tc-inc{zoom:1!important;"
      + "scale:calc(var(--hs,1)*var(--pbk,1))!important;"
      + "right:auto!important;left:calc(50% + 120px*var(--hs,1)*var(--pbk,1) + 28px)!important;"
      + "bottom:calc(14px*var(--hs,1)*var(--pbk,1) + var(--sb))!important;top:auto!important}"
      // 放大地图按钮(VC=103): 落在 .hud-tr 右上角 = 地图圆右上、右上按钮组左侧的红圈草地。
      // 默认隐藏, 仅横屏 media 内放开; pointer-events:auto 防 HUD 父级拦截触摸。
      // VC=104 修复: 按钮必须挂 body + fixed + 极高 z-index。之前挂 .hud-tr 内,
      // 而 .hud-tr 带 transform:scale(.85)/scale:var(--hs) 会创建「局部层叠上下文」,
      // 子元素 z-index 再大也压不过盖在 HUD 上的游戏画布; 且 #hud *{pointer-events:none}。
      // 真实触摸 elementFromPoint 命中的是游戏层, 点击被吞 => 「点了没放大」。
      + "#pbk-mapbtn{display:grid!important;pointer-events:auto!important;position:fixed;z-index:999999;"
      + "right:calc(62px * var(--tcs,1) + 14px + var(--sr));top:calc(16px + var(--st));"
      + "width:44px;height:44px;border-radius:14px;background:#22243aee;color:#fff;"
      + "box-shadow:0 0 0 2px #ffffff66,0 3px 10px #0008;place-items:center;"
      + "font:900 12px/1.1 system-ui,sans-serif;touch-action:manipulation;user-select:none;cursor:pointer}"
      + "#pbk-mapbtn.on{background:#2f6bd8}"
      // 放大态: minimap 节点临时移进全屏遮罩并居中放大。canvas 内部分辨率固定 400x400,
      // 游戏仍持有同一节点每帧重绘 => 实时地图; pointer-events:none 避开游戏原有 minimap
      // click / mapTap 逻辑。点遮罩任意处收起, 节点原样插回 .hud-tr 首位。
      // 尺寸由 JS 按「当前实际视觉尺寸」写入 --pbk-mm-big: 现状*1.35 与 86% 短边取大,
      // 再夹到 96% 短边; 末尾兜底绝不 < 现状(地图被调到超大时至少居中占满)。
      // 之前写死 min(86vmin,560px), 用户把地图调到很大(--mm≈2, 实测约 452px)时反而更小。
      + "#pbk-mapwrap{position:fixed;inset:0;z-index:999998;background:#0b0d18cc}"
      // 绝对定位强制居中: grid/flex 的 place-items:center 在元素比容器还大时会退化为贴边(start),
      // 用户把地图调到超大(457px > 屏短边 412px)时就不会居中。transform 只做平移, 动画只淡入不再碰 transform。
      + "#pbk-mapwrap .minimap.pbk-mm-big{position:absolute!important;left:50%!important;top:50%!important;"
      + "transform:translate(-50%,-50%)!important;"
      + "width:var(--pbk-mm-big,min(86vmin,560px))!important;"
      + "height:var(--pbk-mm-big,min(86vmin,560px))!important;margin:0!important;pointer-events:none!important;"
      + "box-shadow:0 0 0 5px #fff,0 0 0 11px #e8243a,0 0 0 15px #ffffff22,0 24px 80px #000c!important;"
      + "animation:pbkmmpop .18s ease-out}"
      + "@keyframes pbkmmpop{from{opacity:.4}to{opacity:1}}"
      + "#pbk-maptip{position:absolute;bottom:24px;left:50%;transform:translateX(-50%);"
      + "color:#fff;font:700 14px system-ui,sans-serif;text-shadow:0 2px 6px #000}"
      + "}";

    /** 放大地图按钮: 幂等创建; minimap 未就绪(游戏 HUD 异步生成)时每 300ms 重试, 最多 12s。
     *  VC=104: 按钮挂 document.body 而非 .hud-tr —— 后者 transform 造层叠上下文且 #hud *
     *  pointer-events:none, 真实触摸点不到。放大尺寸按当前视觉尺寸动态算, 保证一定变大。
     *  竖屏由 applyButtonScale 调 window.__pbkMapClose 自动收起, 避免遮罩残留。 */
    private static final String MAP_JS =
        "(function init(n){"
      + "if(document.getElementById('pbk-mapbtn'))return;"
      + "var mm=document.querySelector('.minimap');"
      + "if(!mm){if(n<40)setTimeout(function(){init(n+1)},300);return;}"
      + "var btn=document.createElement('div');btn.id='pbk-mapbtn';btn.textContent='放大';"
      + "document.body.appendChild(btn);"
      + "var wrap=null,big=false,home=null,next=null;"
      + "function open(){"
      + "if(big)return;"
      + "var m=document.querySelector('.minimap');if(!m)return;"
      + "var r=m.getBoundingClientRect();"
      + "var cur=Math.max(r.width,r.height);"
      + "var side=Math.min(innerWidth,innerHeight);"
      // 现状*1.35 与 86% 短边取大(一定变大), 上限 96% 短边; 兜底绝不 < 现状
      + "var t=Math.min(Math.max(cur*1.35,side*0.86),side*0.96);"
      + "if(t<cur)t=cur;"
      + "m.style.setProperty('--pbk-mm-big',Math.round(t)+'px');"
      + "home=m.parentNode;next=m.nextSibling;"
      + "wrap=document.createElement('div');wrap.id='pbk-mapwrap';"
      + "var tip=document.createElement('div');tip.id='pbk-maptip';tip.textContent='点击任意处收起';"
      + "wrap.appendChild(m);wrap.appendChild(tip);document.body.appendChild(wrap);"
      + "m.classList.add('pbk-mm-big');"
      + "btn.classList.add('on');btn.textContent='收起';big=true;"
      + "wrap.addEventListener('click',function(e){e.stopPropagation();close();});}"
      + "function close(){"
      + "if(!big)return;big=false;"
      + "var m=document.querySelector('.minimap');"
      + "if(m){m.classList.remove('pbk-mm-big');m.style.removeProperty('--pbk-mm-big');"
      + "if(home&&home.isConnected)"
      + "{if(next&&next.parentNode===home)home.insertBefore(m,next);else home.insertBefore(m,home.firstChild);}"
      + "else{var tr=document.querySelector('.hud-tr')||document.body;tr.appendChild(m);}}"
      + "if(wrap&&wrap.parentNode)wrap.parentNode.removeChild(wrap);"
      + "wrap=null;btn.classList.remove('on');btn.textContent='放大';}"
      + "btn.addEventListener('click',function(e){e.preventDefault();e.stopPropagation();"
      + "big?close():open();});"
      + "window.__pbkMapClose=close;"
      + "})(0);";

    private WebView webHome, webGuide;
    private FrameLayout capsule;
    private View pill;
    private TextView tabHome, tabGuide, tabLand;
    private boolean landscapeLocked = false;   // 「横屏」页签主动锁横屏, 按返回键解除
    private int itemW;
    private boolean guideLoaded = false;
    private int current = 0;              // 0=首页 1=攻略
    private boolean dark = true;

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        // 注意: 不可在 setContentView 之前调用 applyImmersive(), 此时 DecorView 尚未挂载窗口,
        // getInsetsController() 内部访问会返回 null 并 NPE。沉浸式交由 onResume/onWindowFocusChanged 兜底。
        dark = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("dark", true);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(dark ? 0xFF000000 : 0xFFFFFFFF);

        // 两个独立 WebView 全屏叠放, 切页签只切可见性, 互不重载
        webHome  = makeWeb();
        webGuide = makeWeb();
        root.addView(webHome, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(webGuide, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        webGuide.setVisibility(View.GONE);

        // 底部悬浮胶囊: 首页 / 攻略 (尺寸/样式对齐首页 CapsuleBottomBar, 无阴影)
        int screenW = getResources().getDisplayMetrics().widthPixels;
        itemW = Math.min(dp(78), (screenW - dp(20)) / 6);   // 与首页单格同宽
        int pillBg = dark ? 0xFF3D6DFF : 0xFF1677FF;
        int pillStroke = dark ? 0xFF5A7BD8 : 0xFF0B5BD6;

        capsule = new FrameLayout(this);
        GradientDrawable cbg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{ dark ? 0x1FFFFFFF : 0x73FFFFFF, dark ? 0x0AFFFFFF : 0x59EFF5FF });
        cbg.setCornerRadius(dp(22));
        cbg.setStroke(dp(1), dark ? 0x40FFFFFF : 0x66FFFFFF);
        capsule.setBackground(cbg);   // 不设 elevation / outline shadow: 无阴影

        // 滑动选中块(与首页一致: 高36dp, 宽 itemW-10dp, 圆角16)
        pill = new View(this);
        GradientDrawable pg = new GradientDrawable();
        pg.setCornerRadius(dp(16));
        pg.setColor(pillBg);
        pg.setStroke(dp(1), pillStroke);
        pill.setBackground(pg);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
                itemW - dp(10), dp(36), Gravity.CENTER_VERTICAL | Gravity.START);
        plp.leftMargin = dp(5);
        capsule.addView(pill, plp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        capsule.addView(row, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        tabHome = makeTab("首页");
        tabGuide = makeTab("攻略");
        tabLand = makeTab("横屏");
        tabLand.setTextColor(dark ? 0xFF8FB4FF : 0xFF1677FF);   // 动作按钮用蓝色区分页面页签
        tabLand.setTypeface(Typeface.DEFAULT_BOLD);
        tabHome.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { selectTab(0); }
        });
        tabGuide.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { selectTab(1); }
        });
        tabLand.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // 主动切横屏玩: configChanges 保证 WebView 不重建, 游戏进度不丢; 不锁死竖屏
                landscapeLocked = true;
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            }
        });
        row.addView(tabHome, new LinearLayout.LayoutParams(itemW, dp(42)));
        row.addView(tabGuide, new LinearLayout.LayoutParams(itemW, dp(42)));
        row.addView(tabLand, new LinearLayout.LayoutParams(itemW, dp(42)));

        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        clp.bottomMargin = dp(12);   // 与首页一致
        root.addView(capsule, clp);

        setContentView(root);
        webHome.loadUrl(URL_HOME);
        selectTab(0);
        applyOrientation();
    }

    private WebView makeWeb() {
        WebView w = new WebView(this);
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setUseWideViewPort(true);
        // 对齐官方 Capacitor 壳: 不开 overview(否则整页被缩小, 游戏触摸按键跟着变小);
        // zoomEnabled=false, 缩放由游戏自己的 viewport(user-scalable=no) 管理。
        s.setLoadWithOverviewMode(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        // 对齐官方壳 capacitor.config.json 的 appendUserAgent, 游戏据此识别「已装 App」分支
        s.setUserAgentString("Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                + " PokeBonkAndroid/1.0.1");
        w.setWebChromeClient(new WebChromeClient());
        w.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView v, String u) {
                if (v == webHome) applyButtonScale();   // 页面就绪即注入放大样式(幂等)
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v, String u) { return false; }
            @Override public void onReceivedSslError(WebView v, android.webkit.SslErrorHandler h,
                                                     android.net.http.SslError e) { h.proceed(); }
        });
        try { CookieManager.getInstance().setAcceptThirdPartyCookies(w, true); } catch (Throwable ignored) {}
        return w;
    }

    private TextView makeTab(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(12.5f);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private void selectTab(int t) {
        current = t;
        webHome.setVisibility(t == 0 ? View.VISIBLE : View.GONE);
        webGuide.setVisibility(t == 1 ? View.VISIBLE : View.GONE);
        if (t == 1 && !guideLoaded) { guideLoaded = true; webGuide.loadUrl(URL_GUIDE); } // 攻略懒加载一次
        int offText = dark ? 0xFF8A94A6 : 0xFF7C8694;
        styleTab(tabHome, t == 0, offText);
        styleTab(tabGuide, t == 1, offText);
        // 滑动 pill 到对应格
        float to = t * itemW;
        ValueAnimator va = ValueAnimator.ofFloat(pill.getTranslationX(), to);
        va.setDuration(240);
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) { pill.setTranslationX((Float) a.getAnimatedValue()); }
        });
        va.start();
    }

    private void styleTab(TextView t, boolean on, int offText) {
        t.setTextColor(on ? 0xFFFFFFFF : offText);
        t.setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
    }

    /** 与首页 applyImmersive 一致: 隐藏系统状态栏+导航栏(沉浸式 sticky, 上滑临时呼出自动回落)。
     *  比通用 Immersive.hide(仅导航栏) 更彻底, 保证横屏游戏全屏不被三大键顶起。 */
    private void applyImmersive() {
        android.view.Window w = getWindow();
        if (w == null) return;
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false);
            // DecorView 未挂载窗口时 getInsetsController() 会 NPE, 必须先判 attached
            if (w.getDecorView() != null && w.getDecorView().isAttachedToWindow()) {
                android.view.WindowInsetsController c = w.getInsetsController();
                if (c != null) {
                    c.hide(android.view.WindowInsets.Type.systemBars());
                    c.setSystemBarsBehavior(android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                }
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    /** 横屏隐藏胶囊, 竖屏显示; 配合 Manifest 的 configChanges, 旋转不重建 WebView。
     *  旋转后系统会重新显示导航栏, 故同时重新隐藏。 */
    private void applyOrientation() {
        if (capsule == null) return;
        boolean landscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        capsule.setVisibility(landscape ? View.GONE : View.VISIBLE);
        applyImmersive();
        applyButtonScale();   // 横屏放大/竖屏还原, 实时改 CSS 变量, 不重载 WebView
    }

    /** app 级按键放大: 横屏放大游戏触摸摇杆与全部动作键, 竖屏原样。
     *  只改独立 CSS 变量 --pbk, 首次注入样式后切档无需重载, 游戏进度不丢。
     *  同时幂等挂载放大地图按钮(MAP_JS); 切竖屏时先收起放大态遮罩。 */
    private void applyButtonScale() {
        if (webHome == null) return;
        boolean landscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        String k = landscape ? String.valueOf(BTN_SCALE_LANDSCAPE) : "1.0";
        String js = "(function(){try{"
              + "document.documentElement.style.setProperty('--pbk'," + k + ");"
              + (landscape ? "" : "try{if(window.__pbkMapClose)window.__pbkMapClose()}catch(e){}")
              + "var s=document.getElementById('pbk-zoom');"
              + "if(!s){s=document.createElement('style');s.id='pbk-zoom';"
              + "s.textContent='" + BTN_CSS + "';document.head.appendChild(s);}"
              + MAP_JS
              + "}catch(e){}})()";
        try { webHome.evaluateJavascript(js, null); } catch (Throwable ignored) {}
    }

    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) landscapeLocked = false;
        applyOrientation();
    }

    /** 系统导航栏在 WebView 交互/输入法收起/回到前台后会被系统重新显示,
     *  与首页/娱乐页一致: 获得焦点或回前台时重新隐藏, 保证横屏游戏全屏不被三大键顶起。 */
    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersive();
    }

    @Override protected void onResume() {
        super.onResume();
        applyImmersive();
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent e) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // 「横屏」主动锁定态: 返回键先解除锁定回竖屏(胶囊重新出现), 不直接退出/WebView 回退
            if (landscapeLocked) {
                landscapeLocked = false;
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                return true;
            }
            WebView w = current == 0 ? webHome : webGuide;
            if (w != null && w.canGoBack()) { w.goBack(); return true; }
        }
        return super.onKeyDown(keyCode, e);
    }

    @Override protected void onDestroy() {
        try { if (webHome != null) webHome.destroy(); } catch (Throwable ignored) {}
        try { if (webGuide != null) webGuide.destroy(); } catch (Throwable ignored) {}
        super.onDestroy();
    }
}
