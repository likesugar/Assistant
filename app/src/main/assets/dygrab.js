// 抖音直播间地址抓取：在页面上下文内扫真实 DOM，避免 HttpURLConnection 被验证页拦住
(function () {
  if (window.__dyGrab) return;
  window.__dyGrab = 1;
  var timer = null;

  function push(u) {
    try {
      if (!u || u.charAt(0) !== 'h') return;
      AndroidPlayer.onDyUrl(String(u));
    } catch (e) {}
  }

  function scan() {
    try {
      var h = document.documentElement ? document.documentElement.innerHTML : '';
      if (!h || h.length < 200) return;
      var re = /https?:\/\/[^"'\\\s<>]+?\.(?:flv|m3u8)[^"'\\\s<>]*/g, m, n = 0, or4 = 0;
      while ((m = re.exec(h)) !== null && n < 40) {
        var u = m[0];
        if (u.indexOf('_sd') >= 0 || u.indexOf('_ld') >= 0) continue;
        if (u.indexOf('_or4') >= 0) or4++;
        push(u);
        n++;
      }
      // 已拿到原画就停扫，避免长跑占 CPU
      if (or4 > 0 && timer) { clearInterval(timer); timer = null; }
    } catch (e) {}
  }

  scan();
  timer = setInterval(scan, 2000);
})();
