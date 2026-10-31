package com.lin0721.douyincoral.core.web

/**
 * 注入到抖音网页里的胶水脚本。
 *
 * 为什么要在网页里跑：抖音 web 的每个接口都要求 a_bogus 签名，而签名函数就在页面自己的
 * JS 里（byted_acrawler.frontierSign）。与其逆向算法、天天跟着更新，不如把网页当成签名器，
 * 我们在页面上下文里拼好 URL、借它的函数签名、用它的 Cookie 发请求，再把 JSON 交回原生层。
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
        if (!u.searchParams.get('webid')) {
          var w = p.get('webid');
          if (w) { u.searchParams.set('webid', w); }
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

  var origFetch = window.fetch;
  window.fetch = function (input, init) {
    var url = '';
    try { url = (typeof input === 'string') ? input : ((input && input.url) || ''); } catch (e) { }
    noteEnv(url);
    return origFetch.apply(this, arguments);
  };

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
      if (verb === 'GET' && !sign(u)) {
        attempt++;
        if (attempt < 30) { setTimeout(go, 400); return; }
        post({ type: 'rpc', id: id, ok: false, err: 'sign-unavailable' });
        return;
      }
      var init = { method: verb, credentials: 'include', headers: { 'accept': 'application/json, text/plain, */*' } };
      if (verb === 'POST') {
        init.headers['content-type'] = 'application/x-www-form-urlencoded';
        init.body = body || '';
      }
      origFetch.call(window, u.toString(), init).then(function (r) {
        return r.text().then(function (t) {
          post({ type: 'rpc', id: id, ok: true, status: r.status, body: t });
        });
      }).catch(function (e) {
        post({ type: 'rpc', id: id, ok: false, err: String(e) });
      });
    }

    go();
  };

  window.__mcSignDiagnose = function () {
    var ac = acrawler();
    var out = { signer: !!ac, cookieTtwid: !!cookie('ttwid'), cookieMsToken: !!cookie('msToken') };
    try { out.hasFrontier = !!(ac && ac.frontierSign); } catch (e) { }
    post({ type: 'diag', body: JSON.stringify(out) });
  };

  post({ type: 'boot' });
})();
"""
}
