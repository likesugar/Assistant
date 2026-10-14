package com.ziyuan.Assistant;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** AI助手: OpenAI兼容多渠道聊天接口 (图1渠道预设) */
public class AiHelper {

    // 渠道预设: name → baseUrl
    public static final String[][] CHANNELS = {
        {"OpenAI", "https://api.openai.com/v1"},
        {"自定义（兼容 OpenAI）", ""},
        {"DeepSeek", "https://api.deepseek.com/v1"},
        {"Z.AI (GLM)", "https://open.bigmodel.cn/api/paas/v4"},
        {"Moonshot AI", "https://api.moonshot.cn/v1"},
        {"SiliconFlow", "https://api.siliconflow.cn/v1"},
        {"OpenRouter", "https://openrouter.ai/api/v1"},
        {"Groq", "https://api.groq.com/openai/v1"},
        {"xAI (Grok)", "https://api.x.ai/v1"},
        {"AI21", "https://api.ai21.com/studio/v1"},
        {"Azure OpenAI", "https://你的资源名.openai.azure.com/v1"},
        {"Chutes", "https://api.chutes.ai/app/v1"},
        {"Cloudflare Workers AI", "https://api.cloudflare.com/client/v4"},
        {"Cohere", "https://api.cohere.ai/compatibility/v1"},
        {"Electron Hub", "https://api.electronhub.top/v1"},
        {"Fireworks AI", "https://api.fireworks.ai/inference/v1"},
        {"Google AI Studio", "https://generativelanguage.googleapis.com/v1beta/openai"},
        {"Google Vertex AI", "https://aiplatform.googleapis.com/v1"},
        {"MistralAI", "https://api.mistral.ai/v1"},
        {"MiniMax", "https://api.minimax.chat/v1"},
        {"NanoGPT", "https://nano-gpt.com/api/v1"},
        {"Perplexity", "https://api.perplexity.ai"},
        {"Pollinations", "https://text.pollinations.ai/openai"},
        {"AI/ML API", "https://api.aimlapi.com/v1"}
    };

    public static SharedPreferences sp(Context c) { return c.getSharedPreferences("ai", Context.MODE_PRIVATE); }
    public static String base(Context c) {
        String b = sp(c).getString("base", "");
        if (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        return b;
    }
    public static String key(Context c) { return sp(c).getString("key", ""); }
    public static String model(Context c) { return sp(c).getString("model", ""); }
    public static boolean configured(Context c) { return !base(c).isEmpty() && !key(c).isEmpty() && !model(c).isEmpty(); }

    public static void save(Context c, String base, String key, String model) {
        sp(c).edit().putString("base", base).putString("key", key).putString("model", model).apply();
    }

    /** OpenAI兼容 chat/completions; msgs=[{"role":"user","content":"..."}...] */
    public static String chat(Context c, JSONArray msgs) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(base(c) + "/chat/completions").openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(120000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + key(c));
        JSONObject body = new JSONObject();
        body.put("model", model(c));
        body.put("messages", msgs);
        body.put("stream", false);
        java.io.OutputStream os = conn.getOutputStream();
        os.write(body.toString().getBytes(StandardCharsets.UTF_8));
        os.close();
        int code = conn.getResponseCode();
        InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while (is != null && (n = is.read(buf)) > 0) bos.write(buf, 0, n);
        if (is != null) is.close();
        String resp = new String(bos.toByteArray(), StandardCharsets.UTF_8);
        if (code >= 400) throw new Exception("HTTP " + code + ": " + resp.substring(0, Math.min(resp.length(), 300)));
        JSONObject r = new JSONObject(resp);
        return r.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
    }
}
