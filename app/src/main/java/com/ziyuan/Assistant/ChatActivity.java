package com.ziyuan.Assistant;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 聊天室: 局域网UDP广播互联(手机↔手机), 电脑经局域网共享网页收发 */
public class ChatActivity extends Activity {

    private LinearLayout msgList;
    private ScrollView scroll;
    private EditText input;
    private String nick;
    private long lastTs = 0;
    private int msgCount = 0;
    private final SimpleDateFormat fmt = new SimpleDateFormat("HH:mm", Locale.US);

    private boolean dark;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Immersive.hide(this);
        dark = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("dark", false);
        // 输入法弹起时压缩窗口, 不遮输入框
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        nick = getSharedPreferences("chat", MODE_PRIVATE).getString("nick", "");
        ChatHub.init(getApplicationContext());
        ChatHub.start();
        buildUi();
        askLogin();
    }

    /** 登录弹窗(美化卡片版): 用户名+密码(多用户本地存放), 取消登录=退出聊天室 */
    private void askLogin() {
        // 卡片容器
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cbg = new GradientDrawable();
        cbg.setColor(dark ? 0xFF16181D : 0xFFFFFFFF);
        cbg.setCornerRadius(dip(22));
        card.setBackground(cbg);
        card.setPadding(dip(28), dip(28), dip(28), dip(24));

        // 图标 + 标题
        TextView icon = new TextView(this);
        icon.setText("💬");
        icon.setTextSize(30);
        icon.setGravity(Gravity.CENTER);
        card.addView(icon, new LinearLayout.LayoutParams(-1, -2));
        TextView title = new TextView(this);
        title.setText("登录聊天室");
        title.setTextSize(18);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setTextColor(dark ? 0xFFEEEEEE : 0xFF1F2329);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dip(6), 0, dip(2));
        card.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView sub = new TextView(this);
        sub.setText("局域网互联 · 新用户输入即注册");
        sub.setTextSize(12);
        sub.setTextColor(0xFF888888);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, 0, 0, dip(14));
        card.addView(sub, new LinearLayout.LayoutParams(-1, -2));

        // 输入框(圆角胶囊)
        final EditText eu = new EditText(this);
        eu.setHint("用户名");
        eu.setText(nick);
        eu.setSelection(eu.getText().length());
        eu.setTextSize(15);
        eu.setTextColor(dark ? 0xFFEEEEEE : 0xFF1F2329);
        eu.setHintTextColor(0xFF999999);
        eu.setInputType(EditorInfo.TYPE_CLASS_TEXT);
        eu.setBackground(fieldBg());
        eu.setPadding(dip(16), dip(12), dip(16), dip(12));
        final EditText ep = new EditText(this);
        ep.setHint("密码（可为空）");
        ep.setTextSize(15);
        ep.setTextColor(dark ? 0xFFEEEEEE : 0xFF1F2329);
        ep.setHintTextColor(0xFF999999);
        ep.setInputType(EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_VARIATION_PASSWORD);
        ep.setBackground(fieldBg());
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, -2);
        pp.topMargin = dip(12);
        ep.setPadding(dip(16), dip(12), dip(16), dip(12));
        card.addView(ep, pp);
        // 登录框用户名行 + ▽历史用户名
        LinearLayout uRow = new LinearLayout(this);
        uRow.setOrientation(LinearLayout.HORIZONTAL);
        uRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(0, -2, 1f);
        eu.setLayoutParams(ulp);
        uRow.addView(eu);
        TextView drop = new TextView(this);
        drop.setText(" ▽");
        drop.setTextSize(16);
        drop.setTextColor(0xFF315CDE);
        drop.setPadding(dip(10), dip(12), dip(10), dip(12));
        drop.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            try {
                org.json.JSONObject users = new org.json.JSONObject(getSharedPreferences("chat", MODE_PRIVATE).getString("users", "{}"));
                java.util.Iterator<String> it = users.keys();
                java.util.List<String> names = new java.util.ArrayList<>();
                while (it.hasNext()) names.add(it.next());
                if (names.isEmpty()) { toast("还没有历史用户"); return; }
                new AlertDialog.Builder(ChatActivity.this)
                    .setTitle("历史用户")
                    .setItems(names.toArray(new String[0]), new android.content.DialogInterface.OnClickListener() {
                        public void onClick(android.content.DialogInterface d, int w) {
                            eu.setText(names.get(w));
                            String pv = users.optString(names.get(w), "");
                            ep.setText(pv == null ? "" : pv);
                        }
                    }).show();
            } catch (Throwable ignored) {}
        }});
        uRow.addView(drop);
        card.addView(uRow, new LinearLayout.LayoutParams(-1, -2));

        // 进入按钮(渐变胶囊)
        TextView enter = new TextView(this);
        enter.setText("进  入");
        enter.setTextSize(15);
        enter.setTypeface(null, android.graphics.Typeface.BOLD);
        enter.setTextColor(0xFFFFFFFF);
        enter.setGravity(Gravity.CENTER);
        GradientDrawable ebg = new GradientDrawable();
        ebg.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
        ebg.setColors(new int[]{0xFF315CDE, 0xFF7B4FD8});
        ebg.setCornerRadius(dip(22));
        enter.setBackground(ebg);
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(-1, dip(44));
        elp.topMargin = dip(20);
        card.addView(enter, elp);

        // 取消登录
        TextView cancel = new TextView(this);
        cancel.setText("取消登录");
        cancel.setTextSize(13);
        cancel.setTextColor(0xFF888888);
        cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(0, dip(14), 0, 0);
        card.addView(cancel, new LinearLayout.LayoutParams(-1, -2));

        final AlertDialog dlg = new AlertDialog.Builder(this)
            .setView(card)
            .setCancelable(false)
            .create();
        dlg.show();
        dlg.getWindow().setLayout(dip(320), -2);
        dlg.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        enter.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            String u = eu.getText().toString().trim();
            String p = ep.getText().toString().trim();
            if (u.isEmpty()) { toast("用户名不能为空"); return; }
            android.content.SharedPreferences sp = getSharedPreferences("chat", MODE_PRIVATE);
            try {
                org.json.JSONObject users = new org.json.JSONObject(sp.getString("users", "{}"));
                if (users.has(u)) {
                    if (!users.optString(u, "").equals(p)) { toast("密码错误"); return; }
                } else {
                    users.put(u, p);
                    sp.edit().putString("users", users.toString()).apply();
                }
            } catch (Throwable e) { toast("本地存储失败"); return; }
            nick = u;
            sp.edit().putString("nick", u).apply();
            dlg.dismiss();
            toast("欢迎, " + u);
        }});
        cancel.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            dlg.dismiss();
            finish(); // 回到工具箱首页
        }});
    }

    private GradientDrawable fieldBg() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(dark ? 0xFF1C1F26 : 0xFFF2F3F5);
        g.setCornerRadius(dip(24));
        return g;
    }

    private int bg()   { return dark ? 0xFF000000 : 0xFFFFFFFF; }
    private int main() { return dark ? 0xFFEEEEEE : 0xFF1F2329; }
    private int sub()  { return dark ? 0xFF888888 : 0xFF888888; }
    private int bubbleOther() { return dark ? 0xFF1C1F26 : 0xFFF2F3F5; }
    private int bubbleMine()  { return 0xFFD65DB1; }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        rootView = root;
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg());

        // 顶栏
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dip(16), dip(40), dip(16), dip(10));
        TextView back = new TextView(this); back.setText("‹");
        back.setTextSize(24); back.setTextColor(main());
        back.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { finish(); } });
        TextView title = new TextView(this); title.setText("  聊天室");
        title.setTextSize(18); title.setTextColor(main()); title.setTypeface(null, android.graphics.Typeface.BOLD);
        TextView ip = new TextView(this); ip.setText("  " + LanShareServer.localIp() + ":" + LanShareServer.getPort());
        ip.setTextSize(11); ip.setTextColor(sub());
        top.addView(back); top.addView(title); top.addView(ip);
        root.addView(top, new LinearLayout.LayoutParams(-1, -2));

        // 电脑接入说明
        TextView hint = new TextView(this);
        hint.setText("💻 电脑浏览器打开 http://" + LanShareServer.localIp() + ":" + LanShareServer.getPort() + "/chat 进聊天室");
        hint.setTextSize(10);
        hint.setTextColor(0xFF888888);
        hint.setPadding(dip(16), 0, dip(16), dip(4));
        root.addView(hint, new LinearLayout.LayoutParams(-1, -2));

        msgList = new LinearLayout(this);
        msgList.setOrientation(LinearLayout.VERTICAL);
        msgList.setPadding(dip(12), dip(4), dip(12), dip(4));
        scroll = new ScrollView(this);
        scroll.addView(msgList);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        // 底部输入: [表情] [文件] [输入框] [发送]
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setPadding(dip(6), dip(8), dip(6), dip(8));
        TextView emo = new TextView(this);
        emo.setText("😊");
        emo.setTextSize(20);
        emo.setPadding(dip(6), 0, dip(6), 0);
        emo.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showEmoji(); } });
        bottom.addView(emo);
        TextView file = new TextView(this);
        file.setText("📎");
        file.setTextSize(20);
        file.setPadding(dip(6), 0, dip(6), 0);
        file.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { pickFile(); } });
        bottom.addView(file);
        input = new EditText(this);
        input.setHint("说点什么…");
        input.setTextSize(14);
        input.setTextColor(main());
        input.setHintTextColor(sub());
        input.setBackgroundResource(android.R.color.transparent);
        GradientDrawable ibg = new GradientDrawable();
        ibg.setColor(dark ? 0xFF1C1F26 : 0xFFF2F3F5);
        ibg.setCornerRadius(dip(20));
        input.setBackground(ibg);
        input.setPadding(dip(14), dip(8), dip(14), dip(8));
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0, -2, 1f);
        bottom.addView(input, ilp);
        TextView send = new TextView(this);
        send.setText("发送");
        send.setTextColor(0xFFFFFFFF); send.setTextSize(14); send.setGravity(Gravity.CENTER);
        GradientDrawable sbg = new GradientDrawable();
        sbg.setColor(0xFF315CDE); sbg.setCornerRadius(dip(18));
        send.setBackground(sbg);
        send.setPadding(dip(16), dip(8), dip(16), dip(8));
        send.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { doSend(); } });
        bottom.addView(send, new LinearLayout.LayoutParams(-2, -2));
        root.addView(bottom, new LinearLayout.LayoutParams(-1, -2));
        input.setOnEditorActionListener(new android.widget.TextView.OnEditorActionListener() {
            public boolean onEditorAction(android.widget.TextView v, int a, android.view.KeyEvent e) {
                if (a == EditorInfo.IME_ACTION_SEND) { doSend(); return true; }
                return false;
            }
        });
        setContentView(root);
        rootBaseBottomPad = root.getPaddingBottom();
        setupImeFix();
        applyImmersive();
        renderAll();
    }

    private void doSend() {
        String m = input.getText().toString().trim();
        if (m.isEmpty()) return;
        input.setText("");
        if (nick.isEmpty()) { askLogin(); return; }
        ChatHub.send(nick, m);
    }

    /** 表情面板: 常用emoji插入输入框 */
    private void showEmoji() {
        final String[] es = {"😀","😂","🤣","😊","😍","😘","😜","🤔","😎","😭","😡","🥺",
            "👍","👎","👏","🙏","💪","🤝","❤️","💔","🎉","🌹","⚡","🔥",
            "🌙","⭐","🐶","🐱","🍉","🎂","🍺","☕"};
        android.widget.GridView gv = new android.widget.GridView(this);
        gv.setNumColumns(8);
        gv.setAdapter(new android.widget.BaseAdapter() {
            public int getCount() { return es.length; }
            public Object getItem(int p) { return es[p]; }
            public long getItemId(int p) { return p; }
            public View getView(int p, View cv, android.view.ViewGroup pg) {
                TextView t = new TextView(ChatActivity.this);
                t.setText(es[p]);
                t.setTextSize(24);
                t.setGravity(Gravity.CENTER);
                t.setPadding(dip(4), dip(8), dip(4), dip(8));
                return t;
            }
        });
        gv.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            public void onItemClick(android.widget.AdapterView<?> p, View v, int pos, long id) {
                input.append(es[pos]);
            }
        });
        new AlertDialog.Builder(this).setView(gv).setNegativeButton("收起", null).show();
    }

    /** 选文件 → 缓存+注册HTTP服务 → 广播文件消息 */
    private void pickFile() {
        android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT);
        i.setType("*/*");
        i.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        startActivityForResult(android.content.Intent.createChooser(i, "选择文件"), 9001);
    }

    @Override
    protected void onActivityResult(int req, int res, android.content.Intent data) {
        super.onActivityResult(req, res, data);
        if (req != 9001 || res != RESULT_OK || data == null || data.getData() == null) return;
        try {
            android.net.Uri uri = data.getData();
            String name = "文件";
            android.database.Cursor c = getContentResolver().query(uri, null, null, null, null);
            if (c != null) {
                try { int ni = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                      if (ni >= 0 && c.moveToFirst()) name = c.getString(ni); } finally { c.close(); }
            }
            java.io.File dir = new java.io.File(getExternalFilesDir(null), "聊天文件");
            if (!dir.exists()) dir.mkdirs();
            final java.io.File out = new java.io.File(dir, String.valueOf(System.currentTimeMillis()) + "_" + name);
            java.io.InputStream is = getContentResolver().openInputStream(uri);
            java.io.FileOutputStream fos = new java.io.FileOutputStream(out);
            byte[] buf = new byte[8192]; int n;
            while ((n = is.read(buf)) > 0) fos.write(buf, 0, n);
            is.close(); fos.close();
            final String fname = name;
            final long fts = System.currentTimeMillis();
            ChatHub.serveFile(fts, out);
            if (!LanShareServer.isRunning()) LanShareServer.start();
            ChatHub.sendObj(nick, "📎 " + fname + " (" + (out.length() / 1024) + "KB)", fts, fname, out.length());
            toast("文件已发送");
        } catch (Throwable e) { toast("发送失败: " + e); }
    }

    private void renderAll() {
        msgList.removeAllViews();
        msgCount = 0;
        org.json.JSONArray all = ChatHub.all();
        for (int i = 0; i < all.length(); i++) addBubble(all.optJSONObject(i), false);
        msgList.post(new Runnable() { public void run() { scroll.fullScroll(ScrollView.FOCUS_DOWN); } });
    }

    private void addBubble(JSONObject o, boolean scrollDown) {
        String nick2 = o.optString("nick", "?");
        String msg = o.optString("msg", "");
        long ts = o.optLong("ts", 0);
        boolean mine = nick2.equals(nick);
        if (ts > lastTs) lastTs = ts;
        msgCount++;

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setGravity(mine ? Gravity.END : Gravity.START);
        row.setPadding(0, dip(4), 0, dip(4));

        TextView meta = new TextView(this);
        meta.setText(nick2 + " · " + fmt.format(new Date(ts)));
        meta.setTextSize(10); meta.setTextColor(sub());
        row.addView(meta);

        TextView bubble = new TextView(this);
        bubble.setText(msg);
        bubble.setTextSize(15);
        bubble.setTextColor(mine ? 0xFFFFFFFF : main());
        bubble.setMaxWidth(dip(260));
        GradientDrawable bg2 = new GradientDrawable();
        bg2.setColor(mine ? bubbleMine() : bubbleOther());
        bg2.setCornerRadius(dip(14));
        bubble.setBackground(bg2);
        bubble.setPadding(dip(12), dip(8), dip(12), dip(8));
        final JSONObject fo = o;
        if (o.has("fname")) {
            bubble.setTextColor(0xFF315CDE);
            bubble.setTypeface(null, android.graphics.Typeface.BOLD);
            bubble.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { downloadFile(fo); } });
        }
        row.addView(bubble, new LinearLayout.LayoutParams(-2, -2));

        msgList.addView(row, new LinearLayout.LayoutParams(-2, -2));
        if (scrollDown || msgCount % 50 == 0)
            msgList.post(new Runnable() { public void run() { scroll.fullScroll(ScrollView.FOCUS_DOWN); } });
    }

    /** 点击文件气泡: 确保本地可用(没有就按需从源拉) */
    private void downloadFile(final JSONObject o) {
        if (o.optString("uid").equals(ChatHub.selfId())) { toast("这是自己发的文件"); return; }
        final long fts = o.optLong("fts", o.optLong("ts", 0));
        final String fname = o.optString("fname", "file");
        toast("开始接收 " + fname + "…");
        new Thread(new Runnable() { public void run() {
            java.io.File f = ChatHub.ensureFile(fts);
            if (f != null) toast("已收到: Android/data/.../files/聊天文件/" + fname);
            else toast("接收失败(发送方不在线?)");
        }}).start();
    }

    private ChatHub.Listener listener = new ChatHub.Listener() {
        public void onMessage(final JSONObject o) {
            runOnUiThread(new Runnable() { public void run() {
                // 防重: 自己发的在send里已渲染
                if (o.optLong("ts", 0) <= lastTs && o.optString("nick").equals(nick)) return;
                addBubble(o, true);
            }});
        }
    };

    private int dip(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private void toast(String s) {
        runOnUiThread(new Runnable() { public void run() {
            android.widget.Toast.makeText(ChatActivity.this, s, android.widget.Toast.LENGTH_SHORT).show();
        }});
    }

    private LinearLayout rootView;
    private int rootBaseBottomPad = 0;

    /** 隐藏导航栏+状态栏(沉浸式), 每次焦点恢复时重申 */
    private void applyImmersive() {
        View d = getWindow().getDecorView();
        d.setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    /** 键盘弹起检测: 给根布局加底部内边距, 输入框始终在键盘上方 */
    private void setupImeFix() {
        final View decor = getWindow().getDecorView();
        final android.graphics.Rect r = new android.graphics.Rect();
        decor.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
            public void onGlobalLayout() {
                if (rootView == null) return;
                decor.getWindowVisibleDisplayFrame(r);
                int visible = r.bottom;
                int total = decor.getHeight();
                int kb = total - visible;
                if (kb > dip(100)) {
                    rootView.setPadding(0, 0, 0, kb);
                } else if (rootView.getPaddingBottom() != rootBaseBottomPad) {
                    rootView.setPadding(0, 0, 0, rootBaseBottomPad);
                }
            }
        });
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersive();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyImmersive();
        ChatHub.addListener(listener); renderAll();
    }
    @Override
    protected void onPause() { super.onPause(); ChatHub.removeListener(listener); }
    @Override
    public void onBackPressed() { finish(); }
}
