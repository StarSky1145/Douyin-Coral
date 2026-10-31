package com.lin0721.douyincoral.core.web

/**
 * 注入到抖音网页里的胶水脚本。
 *
 * 关键判断（真机日志逼出来的）：抖音的签名器 webmssdk 把虚拟机放在 Worker 里，
 * 主线程上根本没有 window.byted_acrawler，它对外只表现为「patch 掉 window.fetch」。
 * 所以正确的用法不是找签名函数自己算 a_bogus，而是用页面当前那个 fetch ——
 * 它会在发请求时自己补签名。只有页面还没被 patch 时，才退回手动签名。
 */
internal object PumpScript {

    const val JS: String = """
(function () {
  if (window.__mcReady) { return; }
  window.__mcReady = true;

  function post(obj) {
    try { if (window.McBridge && McBridge.post) { McBridge.post(JSON.stringify(obj)); } } catch (e) { }
  }

  function cookie(name) {
    try {
      var c = document.cookie || '';
      var p = String(name) + '=';
      var i = c.indexOf(p);
      if (i < 0) { return ''; }
      var v = c.substring(i + p.length);
      var s = v.indexOf(';');
      return s >= 0 ? v.substring(0, s) : v;
    } catch (e) { return ''; }
  }

  function acrawler() {
    try {
      var a = window.byted_acrawler;
      if (a && (typeof a.frontierSign === 'function' || typeof a.sign === 'function')) { return a; }
    } catch (e) { }
    try {
      var b = window.bdms && window.bdms.acrawler;
      if (b && (typeof b.frontierSign === 'function' || typeof b.sign === 'function')) { return b; }
    } catch (e) { }
    return null;
  }

  var origFetch = window.fetch;
  var origIsNative = true;
  try { origIsNative = /native code/.test(Function.prototype.toString.call(origFetch)); } catch (e) { }

  function myFetch(input, init) {
    var url = '';
    try { url = (typeof input === 'string') ? input : ((input && input.url) || ''); } catch (e) { }
    noteEnv(url);
    return origFetch.apply(this, arguments);
  }

  /** 页面自己的签名器是否接管了 fetch（被后来的实现覆盖 = 被接管） */
  function signerPatched() {
    try {
      if (window.fetch !== myFetch) { return true; }
      if (!origIsNative) { return true; }
    } catch (e) { }
    return false;
  }

  /** 当前真正该用的 fetch：被接管了就用它（自带签名），否则用原始那个 */
  function currentFetch() {
    try {
      if (signerPatched()) { return window.fetch; }
    } catch (e) { }
    return origFetch;
  }

  function applySign(u, res) {
    if (!res) { return false; }
    try {
      if (typeof res === 'string') { u.searchParams.set('a_bogus', res); return true; }
      if (res['a_bogus']) { u.searchParams.set('a_bogus', String(res['a_bogus'])); return true; }
      if (res['X-Bogus']) { u.searchParams.set('X-Bogus', String(res['X-Bogus'])); return true; }
      if (typeof res.data === 'string' && res.data.length > 20) { u.searchParams.set('a_bogus', res.data); return true; }
      if (res.data && res.data['a_bogus']) { u.searchParams.set('a_bogus', String(res.data['a_bogus'])); return true; }
    } catch (e) { }
    return false;
  }

  window.__mcEnvUrl = window.__mcEnvUrl || '';

  function noteEnv(url) {
    try {
      if (!window.__mcEnvUrl && url && String(url).indexOf('device_platform=') >= 0) {
        window.__mcEnvUrl = String(url);
      }
    } catch (e) { }
  }

  var ENV_KEYS = ['pc_libra_divert', 'update_version_code', 'support_h265', 'support_dash',
    'version_code', 'version_name', 'webid', 'uifid'];

  function applyCommon(u) {
    try {
      u.searchParams.set('device_platform', 'webapp');
      u.searchParams.set('aid', '6383');
      u.searchParams.set('channel', 'channel_pc_web');
      u.searchParams.set('pc_client_type', '1');
      var fp = cookie('s_v_web_id');
      if (fp) { u.searchParams.set('verifyFp', fp); u.searchParams.set('fp', fp); }
      var ms = cookie('msToken');
      if (ms) { u.searchParams.set('msToken', ms); }
      var src = window.__mcEnvUrl;
      if (src) {
        var p = new URL(src, location.origin).searchParams;
        for (var i = 0; i < ENV_KEYS.length; i++) {
          var v = p.get(ENV_KEYS[i]);
          if (v) { u.searchParams.set(ENV_KEYS[i], v); }
        }
      }
    } catch (e) { }
  }

  function sign(u) {
    var ac = acrawler();
    if (!ac) { return false; }
    try {
      u.searchParams.delete('a_bogus');
      u.searchParams.delete('X-Bogus');
      var q = u.pathname + '?' + u.searchParams.toString();
      var out = null;
      if (typeof ac.frontierSign === 'function') {
        try { out = ac.frontierSign({ url: q }); } catch (e) { out = null; }
        if (!applySign(u, out)) {
          try { out = ac.frontierSign(q); } catch (e) { out = null; }
        }
      }
      if (!applySign(u, out) && typeof ac.sign === 'function') {
        try { out = ac.sign({ url: q }); } catch (e) { out = null; }
        if (!applySign(u, out)) {
          try { out = ac.sign(q); } catch (e) { out = null; }
        }
      }
      return applySign(u, out);
    } catch (e) { return false; }
  }

  window.fetch = myFetch;

  var XO = XMLHttpRequest.prototype.open;
  var XS = XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.open = function (m, u) {
    try { this.__mcUrl = u; noteEnv(u); } catch (e) { }
    return XO.apply(this, arguments);
  };
  XMLHttpRequest.prototype.send = function () {
    var self = this;
    try {
      this.addEventListener('load', function () {
        try {
          var u = self.__mcUrl || '';
          if (u.indexOf('/aweme/v1/web/') >= 0 && self.responseText && self.responseText.length > 200) {
            post({ type: 'capture', url: u, body: self.responseText });
          }
        } catch (e) { }
      });
    } catch (e) { }
    return XS.apply(this, arguments);
  };

  window.__mcRpc = function (id, path, paramsJson, method, body) {
    var verb = (method || 'GET').toUpperCase();
    var attempt = 0;

    function go() {
      var u;
      try { u = new URL(path, location.origin); } catch (e) {
        post({ type: 'rpc', id: id, ok: false, err: 'bad-url' });
        return;
      }
      try {
        var p = JSON.parse(paramsJson || '{}');
        for (var k in p) {
          if (p[k] !== null && p[k] !== undefined) { u.searchParams.set(k, String(p[k])); }
        }
      } catch (e) { }
      applyCommon(u);

      var patched = signerPatched();
      if (verb === 'GET' && !patched) {
        if (!sign(u)) {
          attempt++;
          if (attempt < 40) { setTimeout(go, 500); return; }
          post({ type: 'rpc', id: id, ok: false, err: 'sign-unavailable' });
          return;
        }
      }

      var init = { method: verb, credentials: 'include', headers: { 'accept': 'application/json, text/plain, */*' } };
      if (verb === 'POST') {
        init.headers['content-type'] = 'application/x-www-form-urlencoded';
        init.body = body || '';
      }
      var f = currentFetch();
      try {
        f.call(window, u.toString(), init).then(function (r) {
          return r.text().then(function (t) {
            post({ type: 'rpc', id: id, ok: true, status: r.status, signed: patched, body: t });
          });
        }).catch(function (e) {
          post({ type: 'rpc', id: id, ok: false, err: String(e) });
        });
      } catch (e) {
        post({ type: 'rpc', id: id, ok: false, err: String(e) });
      }
    }

    go();
  };

  /** 给原生层看的现场快照 */
  window.__mcInfo = function () {
    var out = {
      r: (typeof window.__mcRpc === 'function') ? 1 : 0,
      ac: !!acrawler(),
      patched: signerPatched(),
      ready: String(document.readyState || ''),
      href: String(location.href || ''),
      scripts: (document.scripts ? document.scripts.length : 0),
      js: [],
      envUrl: !!window.__mcEnvUrl,
      nonce: !!window.__ac_nonce,
      body: (document.body ? String(document.body.innerText || '').replace(/\s+/g, ' ').slice(0, 70) : ''),
      ttwid: !!cookie('ttwid'),
      msToken: !!cookie('msToken')
    };
    try {
      var e = performance.getEntriesByType('resource');
      for (var i = 0; i < e.length; i++) {
        var n = e[i].name || '';
        if (n.indexOf('.js') >= 0) { out.js.push(n.slice(-48)); }
      }
      out.js = out.js.slice(-6);
    } catch (x) { }
    return out;
  };

  /** 页面自己不动，就轻推它一把，逼它去请求推荐流 */
  window.__mcNudge = function () {
    try {
      window.scrollBy(0, 320);
      window.dispatchEvent(new Event('scroll'));
      var v = document.querySelector('video');
      if (v) { v.dispatchEvent(new Event('mouseover', { bubbles: true })); }
      return true;
    } catch (e) { return false; }
  };

  window.__mcSignDiagnose = function () {
    var ac = acrawler();
    post({ type: 'diag', body: JSON.stringify({ signer: !!ac, patched: signerPatched() }) });
  };

  post({ type: 'boot' });
})();
"""
}
