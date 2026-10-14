package com.ziyuan.Assistant;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
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
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** AI助手聊天 (图1渠道预设, OpenAI兼容) */
public class AiChatActivity extends Activity {

    private LinearLayout msgList;
    private ScrollView scroll;
    private EditText input;
    private boolean dark;
    private final JSONArray history = new JSONArray();
    private final SimpleDateFormat fmt = new SimpleDateFormat("HH:mm", Locale.US);

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        dark = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("dark", false);
        buildUi();
    }

    private int bg() { return dark ? 0xFF000000 : 0xFFFFFFFF; }
    private int main() { return dark ? 0xFFEEEEEE : 0xFF1F2329; }
    private int sub() { return dark ? 0xFF888888 : 0xFF999999; }
    private int bubbleOther() { return dark ? 0xFF1C1F26 : 0xFFF2F3F5; }
    private int dip(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
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
        TextView title = new TextView(this); title.setText("  🤖 AI助手");
        title.setTextSize(18); title.setTextColor(main()); title.setTypeface(null, Typeface.BOLD);
        TextView cfg = new TextView(this); cfg.setText("  ⚙ 接入");
        cfg.setTextSize(13); cfg.setTextColor(0xFF315CDE);
        cfg.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showConfig(); } });
        top.addView(back); top.addView(title); top.addView(cfg);
        root.addView(top, new LinearLayout.LayoutParams(-1, -2));

        // 状态提示
        TextView st = new TextView(this);
        st.setId(2001);
        st.setTextSize(11); st.setTextColor(sub());
        st.setPadding(dip(16), 0, dip(16), dip(6));
        st.setText(AiHelper.configured(this)
            ? "已接入: " + AiHelper.model(this) + " @ " + AiHelper.base(this)
            : "未配置 — 点右上角⚙选择渠道并填 Key");
        root.addView(st, new LinearLayout.LayoutParams(-1, -2));

        msgList = new LinearLayout(this);
        msgList.setOrientation(LinearLayout.VERTICAL);
        msgList.setPadding(dip(12), dip(4), dip(12), dip(4));
        scroll = new ScrollView(this);
        scroll.addView(msgList);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        // 底部输入
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setPadding(dip(10), dip(8), dip(10), dip(8));
        input = new EditText(this);
        input.setHint("问点什么…");
        input.setTextSize(14);
        input.setTextColor(main());
        input.setHintTextColor(sub());
        input.setMaxLines(4);
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
        send.setTypeface(null, Typeface.BOLD);
        GradientDrawable sbg = new GradientDrawable();
        sbg.setColor(0xFF315CDE); sbg.setCornerRadius(dip(18));
        send.setBackground(sbg);
        send.setPadding(dip(18), dip(8), dip(18), dip(8));
        send.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { doSend(); } });
        bottom.addView(send);
        input.setOnEditorActionListener(new android.widget.TextView.OnEditorActionListener() {
            public boolean onEditorAction(android.widget.TextView v, int a, android.view.KeyEvent e) {
                if (a == EditorInfo.IME_ACTION_SEND) { doSend(); return true; }
                return false;
            }
        });
        root.addView(bottom, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
    }

    private void doSend() {
        String m = input.getText().toString().trim();
        if (m.isEmpty()) return;
        if (!AiHelper.configured(this)) { showConfig(); return; }
        input.setText("");
        addBubble("我", m, true);
        history.put(silent("user", m));
        final TextView thinking = addBubble("AI", "思考中…", false);
        new Thread(new Runnable() { public void run() {
            try {
                String reply = AiHelper.chat(AiChatActivity.this, history);
                history.put(silent("assistant", reply));
                runOnUiThread(new Runnable() { public void run() {
                    thinking.setText(reply);
                }});
            } catch (final Throwable e) {
                runOnUiThread(new Runnable() { public void run() {
                    thinking.setText("❌ " + e.getMessage());
                }});
            }
        }}).start();
    }

    private JSONObject silent(String role, String content) {
        try { return new JSONObject().put("role", role).put("content", content); }
        catch (Throwable e) { return new JSONObject(); }
    }

    private TextView addBubble(String nick, String text, boolean mine) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setGravity(mine ? Gravity.END : Gravity.START);
        row.setPadding(0, dip(4), 0, dip(4));
        TextView meta = new TextView(this);
        meta.setText(nick + " · " + fmt.format(new Date()));
        meta.setTextSize(10); meta.setTextColor(sub());
        row.addView(meta);
        TextView bubble = new TextView(this);
        bubble.setText(text);
        bubble.setTextSize(15);
        bubble.setTextColor(mine ? 0xFFFFFFFF : main());
        bubble.setMaxWidth(dip(270));
        GradientDrawable bg2 = new GradientDrawable();
        bg2.setColor(mine ? 0xFFD65DB1 : bubbleOther());
        bg2.setCornerRadius(dip(14));
        bubble.setBackground(bg2);
        bubble.setPadding(dip(12), dip(8), dip(12), dip(8));
        row.addView(bubble, new LinearLayout.LayoutParams(-2, -2));
        msgList.addView(row, new LinearLayout.LayoutParams(-2, -2));
        msgList.post(new Runnable() { public void run() { scroll.fullScroll(ScrollView.FOCUS_DOWN); } });
        return bubble;
    }

    /** 接入配置: 图1渠道列表 + Key + 模型 */
    private void showConfig() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cbg = new GradientDrawable();
        cbg.setColor(dark ? 0xFF16181D : 0xFFFFFFFF);
        cbg.setCornerRadius(dip(18));
        card.setBackground(cbg);
        card.setPadding(dip(20), dip(16), dip(20), dip(16));

        TextView chLabel = new TextView(this);
        chLabel.setText("选择渠道（点击自动填入地址）");
        chLabel.setTextSize(12); chLabel.setTextColor(sub());
        card.addView(chLabel);

        ScrollView chScroll = new ScrollView(this);
        LinearLayout chList = new LinearLayout(this);
        chList.setOrientation(LinearLayout.VERTICAL);
        final EditText baseEt = new EditText(this);
        baseEt.setHint("API 地址 (https://…/v1)");
        baseEt.setTextSize(13);
        baseEt.setText(AiHelper.base(this));
        baseEt.setTextColor(main()); baseEt.setHintTextColor(sub());
        for (final String[] ch : AiHelper.CHANNELS) {
            TextView row = new TextView(this);
            row.setText(ch[0]);
            row.setTextSize(15);
            row.setTextColor(main());
            row.setPadding(dip(12), dip(11), dip(12), dip(11));
            GradientDrawable rg = new GradientDrawable();
            rg.setColor(dark ? 0xFF1C1F26 : 0xFFF5F6F8);
            rg.setCornerRadius(dip(10));
            row.setBackground(rg);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
            rp.topMargin = dip(6);
            row.setLayoutParams(rp);
            row.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                if (!ch[1].isEmpty()) baseEt.setText(ch[1]);
                if (ch[0].startsWith("自定义")) baseEt.requestFocus();
            }});
            chList.addView(row);
        }
        chScroll.addView(chList);
        chScroll.setLayoutParams(new LinearLayout.LayoutParams(-1, dip(200)));
        card.addView(chScroll);

        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(-1, -2);
        flp.topMargin = dip(12);
        baseEt.setLayoutParams(flp);
        card.addView(baseEt);
        final EditText keyEt = new EditText(this);
        keyEt.setHint("API Key (sk-…)");
        keyEt.setTextSize(13);
        keyEt.setText(AiHelper.key(this));
        keyEt.setTextColor(main()); keyEt.setHintTextColor(sub());
        keyEt.setInputType(EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_VARIATION_PASSWORD);
        LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(-1, -2);
        klp.topMargin = dip(10);
        card.addView(keyEt, klp);
        final EditText modelEt = new EditText(this);
        modelEt.setHint("模型名 (如 gpt-4o-mini / deepseek-chat / glm-4-flash)");
        modelEt.setTextSize(13);
        modelEt.setText(AiHelper.model(this));
        modelEt.setTextColor(main()); modelEt.setHintTextColor(sub());
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(-1, -2);
        mlp.topMargin = dip(10);
        card.addView(modelEt, mlp);

        TextView save = new TextView(this);
        save.setText("保存并使用");
        save.setTextColor(0xFFFFFFFF); save.setTextSize(15);
        save.setTypeface(null, Typeface.BOLD); save.setGravity(Gravity.CENTER);
        GradientDrawable sbg = new GradientDrawable();
        sbg.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
        sbg.setColors(new int[]{0xFF315CDE, 0xFF7B4FD8});
        sbg.setCornerRadius(dip(20));
        save.setBackground(sbg);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, dip(46));
        slp.topMargin = dip(16);
        card.addView(save, slp);

        final AlertDialog dlg = new AlertDialog.Builder(this).setView(card).create();
        dlg.show();
        dlg.getWindow().setLayout(dip(360), -2);
        dlg.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        save.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            AiHelper.save(AiChatActivity.this,
                baseEt.getText().toString().trim(),
                keyEt.getText().toString().trim(),
                modelEt.getText().toString().trim());
            dlg.dismiss();
            buildUi();
            toast("已接入");
        }});
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) Immersive.hide(this);
    }
    @Override
    protected void onResume() { super.onResume(); Immersive.hide(this); }
}
