/**
 * 抖音 web a_bogus 签名（纯算法，不依赖浏览器、Node 或任何第三方库）。
 *
 * 算法来源：Johnserf-Seed/f2 的 ABogus（Apache-2.0，https://github.com/Johnserf-Seed/f2），
 * 按其公开的 1.0.1.19 版流程用 JavaScript 重新实现，未搬运原始代码。
 * 运行环境：Android 侧由 app.cash.quickjs 承载；开发机由 tools/abogus-check.mjs 直接执行。
 *
 * 入口：ABogusSign.sign(cfg) -> a_bogus 字符串
 *   cfg.params  已编码的查询串（不含 a_bogus）
 *   cfg.body    表单请求体，GET 传空串
 *   cfg.ua      实际请求头里的 User-Agent，必须与发请求时一致
 *   cfg.fp      浏览器环境指纹（屏幕/窗口尺寸）
 *   cfg.salt    盐值，默认 cus
 *   cfg.pageId  页面 id，默认 0
 *   cfg.now     毫秒时间戳，缺省取 Date.now()
 *   cfg.rand    三个 0-9999 的随机数，缺省用 Math.random() 生成
 */
var ABogusSign = (function () {
    'use strict';

    var ALPHABET_0 = 'Dkdpgh2ZmsQB80/MfvV36XI1R45-WUAlEixNLwoqYTOPuzKFjJnry79HbGcaStCe';
    var ALPHABET_1 = 'ckdp1h4ZKsUB80/Mfvw36XIgR25+WQAlEi7NLboqYTOPuzmFjJnryx9HVGDaStCe';
    var UA_KEY = [0, 1, 14];
    var AID = 6383;
    var BIG_ARRAY = [121,  243,  55,  234,  103,  36,  47,  228,  30,  231,  106,  6,  115,  95,  78,  101,  250,  207,  198,  50,  139,  227,  220,  105,  97,  143,  34,  28,  194,  215,  18,  100,  159,  160,  43,  8,  169,  217,  180,  120,  247,  45,  90,  11,  27,  197,  46,  3,  84,  72,  5,  68,  62,  56,  221,  75,  144,  79,  73,  161,  178,  81,  64,  187,  134,  117,  186,  118,  16,  241,  130,  71,  89,  147,  122,  129,  65,  40,  88,  150,  110,  219,  199,  255,  181,  254,  48,  4,  195,  248,  208,  32,  116,  167,  69,  201,  17,  124,  125,  104,  96,  83,  80,  127,  236,  108,  154,  126,  204,  15,  20,  135,  112,  158,  13,  1,  188,  164,  210,  237,  222,  98,  212,  77,  253,  42,  170,  202,  26,  22,  29,  182,  251,  10,  173,  152,  58,  138,  54,  141,  185,  33,  157,  31,  252,  132,  233,  235,  102,  196,  191,  223,  240,  148,  39,  123,  92,  82,  128,  109,  57,  24,  38,  113,  209,  245,  2,  119,  153,  229,  189,  214,  230,  174,  232,  63,  52,  205,  86,  140,  66,  175,  111,  171,  246,  133,  238,  193,  99,  60,  74,  91,  225,  51,  76,  37,  145,  211,  166,  151,  213,  206,  0,  200,  244,  176,  218,  44,  184,  172,  49,  216,  93,  168,  53,  21,  183,  41,  67,  85,  224,  155,  226,  242,  87,  177,  146,  70,  190,  12,  162,  19,  137,  114,  25,  165,  163,  192,  23,  59,  9,  94,  179,  107,  35,  7,  142,  131,  239,  203,  149,  136,  61,  249,  14,  156];
    var SORT_INDEX = [18,  20,  52,  26,  30,  34,  58,  38,  40,  53,  42,  21,  27,  54,  55,  31,  35,  57,  39,  41,  43,  22,  28,  32,  60,  36,  23,  29,  33,  37,  44,  45,  59,  46,  47,  48,  49,  50,  24,  25,  65,  66,  70,  71];
    var SORT_INDEX_2 = [18,  20,  26,  30,  34,  38,  40,  42,  21,  27,  31,  35,  39,  41,  43,  22,  28,  32,  36,  23,  29,  33,  37,  44,  45,  46,  47,  48,  49,  50,  24,  25,  52,  53,  54,  55,  57,  58,  59,  60,  65,  66,  70,  71];

    // ---------- SM3（GB/T 32905-2016）----------

    var SM3_IV = [0x7380166f, 0x4914b2b9, 0x172442d7, 0xda8a0600,
                  0xa96f30bc, 0x163138aa, 0xe38dee4d, 0xb0fb0e4e];

    function rotl(x, n) {
        n &= 31;
        return n === 0 ? x >>> 0 : ((x << n) | (x >>> (32 - n))) >>> 0;
    }

    function p0(x) { return (x ^ rotl(x, 9) ^ rotl(x, 17)) >>> 0; }
    function p1(x) { return (x ^ rotl(x, 15) ^ rotl(x, 23)) >>> 0; }

    function utf8Bytes(str) {
        var out = [];
        for (var i = 0; i < str.length; i++) {
            var c = str.charCodeAt(i);
            if (c < 0x80) {
                out.push(c);
            } else if (c < 0x800) {
                out.push(0xc0 | (c >> 6), 0x80 | (c & 63));
            } else if (c >= 0xd800 && c <= 0xdbff && i + 1 < str.length &&
                       (str.charCodeAt(i + 1) & 0xfc00) === 0xdc00) {
                var cp = 0x10000 + ((c - 0xd800) << 10) + (str.charCodeAt(i + 1) - 0xdc00);
                i++;
                out.push(0xf0 | (cp >> 18), 0x80 | ((cp >> 12) & 63),
                         0x80 | ((cp >> 6) & 63), 0x80 | (cp & 63));
            } else {
                out.push(0xe0 | (c >> 12), 0x80 | ((c >> 6) & 63), 0x80 | (c & 63));
            }
        }
        return out;
    }

    function sm3(bytes) {
        var buf = bytes.slice();
        var bitLen = bytes.length * 8;
        buf.push(0x80);
        while (buf.length % 64 !== 56) buf.push(0);
        var hi = Math.floor(bitLen / 4294967296);
        var lo = bitLen >>> 0;
        buf.push((hi >>> 24) & 255, (hi >>> 16) & 255, (hi >>> 8) & 255, hi & 255);
        buf.push((lo >>> 24) & 255, (lo >>> 16) & 255, (lo >>> 8) & 255, lo & 255);

        var v = SM3_IV.slice();
        var w = new Array(68);
        for (var off = 0; off < buf.length; off += 64) {
            var j;
            for (j = 0; j < 16; j++) {
                var k = off + j * 4;
                w[j] = ((buf[k] << 24) | (buf[k + 1] << 16) | (buf[k + 2] << 8) | buf[k + 3]) >>> 0;
            }
            for (j = 16; j < 68; j++) {
                w[j] = (p1((w[j - 16] ^ w[j - 9] ^ rotl(w[j - 3], 15)) >>> 0) ^
                        rotl(w[j - 13], 7) ^ w[j - 6]) >>> 0;
            }
            var a = v[0], b = v[1], c = v[2], d = v[3];
            var e = v[4], f = v[5], g = v[6], h = v[7];
            for (j = 0; j < 64; j++) {
                var t = j < 16 ? 0x79cc4519 : 0x7a879d8a;
                var a12 = rotl(a, 12);
                var ss1 = rotl((a12 + e + rotl(t, j)) >>> 0, 7);
                var ss2 = (ss1 ^ a12) >>> 0;
                var ff = j < 16 ? (a ^ b ^ c) >>> 0 : ((a & b) | (a & c) | (b & c)) >>> 0;
                var gg = j < 16 ? (e ^ f ^ g) >>> 0 : ((e & f) | (~e & g)) >>> 0;
                var tt1 = (ff + d + ss2 + (w[j] ^ w[j + 4])) >>> 0;
                var tt2 = (gg + h + ss1 + w[j]) >>> 0;
                d = c; c = rotl(b, 9); b = a; a = tt1;
                h = g; g = rotl(f, 19); f = e; e = p0(tt2);
            }
            v[0] = (v[0] ^ a) >>> 0;
            v[1] = (v[1] ^ b) >>> 0;
            v[2] = (v[2] ^ c) >>> 0;
            v[3] = (v[3] ^ d) >>> 0;
            v[4] = (v[4] ^ e) >>> 0;
            v[5] = (v[5] ^ f) >>> 0;
            v[6] = (v[6] ^ g) >>> 0;
            v[7] = (v[7] ^ h) >>> 0;
        }
        var out = [];
        for (var i = 0; i < 8; i++) {
            out.push((v[i] >>> 24) & 255, (v[i] >>> 16) & 255, (v[i] >>> 8) & 255, v[i] & 255);
        }
        return out;
    }

    function charCodes(str) {
        var out = [];
        for (var i = 0; i < str.length; i++) out.push(str.charCodeAt(i) & 255);
        return out;
    }

    // 字符串走 UTF-8 编码并加盐；字节数组（上一轮摘要）直接再摘要，与参考实现一致
    function sm3Of(input, salt) {
        if (typeof input !== 'string') return sm3(input);
        return sm3(utf8Bytes(salt ? input + salt : input));
    }

    // ---------- RC4 ----------

    function rc4(key, text) {
        var box = [];
        var i, j = 0;
        for (i = 0; i < 256; i++) box[i] = i;
        for (i = 0; i < 256; i++) {
            j = (j + box[i] + key[i % key.length]) % 256;
            var t = box[i]; box[i] = box[j]; box[j] = t;
        }
        var codes = charCodes(text);
        var out = [];
        i = 0; j = 0;
        for (var k = 0; k < codes.length; k++) {
            i = (i + 1) % 256;
            j = (j + box[i]) % 256;
            var s = box[i]; box[i] = box[j]; box[j] = s;
            out.push(codes[k] ^ box[(box[i] + box[j]) % 256]);
        }
        return out;
    }

    // ---------- 自定义 base64 ----------

    function base64(codes, alphabet) {
        var bits = 0;
        var rest = 0;
        var out = '';
        for (var i = 0; i < codes.length; i++) {
            bits = ((bits << 8) | (codes[i] & 255)) & 0xFFFFFF;
            rest += 8;
            while (rest >= 6) {
                rest -= 6;
                out += alphabet[(bits >> rest) & 63];
            }
        }
        var pad = 0;
        if (rest > 0) {
            out += alphabet[(bits << (6 - rest)) & 63];
            pad = 6 - rest;
        }
        for (var p = 0; p < Math.floor((pad === 6 ? 0 : pad) / 2); p++) out += '=';
        return out;
    }

    function bogusEncode(text, alphabet) {
        var out = [];
        var len = text.length;
        for (var i = 0; i < len; i += 3) {
            var n;
            if (i + 2 < len) {
                n = (text.charCodeAt(i) << 16) | (text.charCodeAt(i + 1) << 8) | text.charCodeAt(i + 2);
            } else if (i + 1 < len) {
                n = (text.charCodeAt(i) << 16) | (text.charCodeAt(i + 1) << 8);
            } else {
                n = text.charCodeAt(i) << 16;
            }
            var shifts = [18, 12, 6, 0];
            var masks = [0xFC0000, 0x03F000, 0x0FC0, 0x3F];
            for (var k = 0; k < 4; k++) {
                if (shifts[k] === 6 && i + 1 >= len) break;
                if (shifts[k] === 0 && i + 2 >= len) break;
                out.push(alphabet[(n & masks[k]) >> shifts[k]]);
            }
        }
        while (out.length % 4 !== 0) out.push('=');
        return out.join('');
    }

    // ---------- 字节混淆（RC4 之后的第二层）----------

    function mix(list) {
        var box = BIG_ARRAY.slice();
        var indexB = box[1];
        var initial = 0;
        var prev = 0;
        var out = [];
        for (var i = 0; i < list.length; i++) {
            var sum;
            if (i === 0) {
                initial = box[indexB];
                sum = indexB + initial;
                box[1] = initial;
                box[indexB] = indexB;
            } else {
                sum = initial + prev;
            }
            sum %= 256;
            out.push(list[i] ^ box[sum]);

            prev = box[(i + 2) % 256];
            sum = (indexB + prev) % 256;
            initial = box[sum];
            box[sum] = box[(i + 2) % 256];
            box[(i + 2) % 256] = initial;
            indexB = sum;
        }
        return out;
    }

    function noise(length, rands) {
        var out = [];
        for (var i = 0; i < length; i++) {
            var rd = rands && i < rands.length ? rands[i] : Math.floor(Math.random() * 10000);
            var low = rd & 255;
            out.push(((low & 170) | 1) & 255);
            out.push(((low & 85) | 2) & 255);
            var high = (rd >>> 8) & 255;
            out.push(((high & 170) | 5) & 255);
            out.push(((high & 85) | 40) & 255);
        }
        return out;
    }

    // ---------- 主流程 ----------

    function sign(cfg) {
        cfg = cfg || {};
        var params = cfg.params == null ? '' : String(cfg.params);
        var body = cfg.body == null ? '' : String(cfg.body);
        var ua = cfg.ua == null ? '' : String(cfg.ua);
        var fp = cfg.fp == null ? '' : String(cfg.fp);
        var salt = cfg.salt == null ? 'cus' : String(cfg.salt);
        var pageId = cfg.pageId == null ? 0 : (cfg.pageId | 0);
        var now = cfg.now == null ? Date.now() : cfg.now;
        var rands = cfg.rand || null;

        var start = now;
        var array1 = sm3Of(sm3Of(params, salt), salt);
        var array2 = sm3Of(sm3Of(body, salt), salt);
        var array3 = sm3(charCodes(base64(rc4(UA_KEY, ua), ALPHABET_1)));

        var dir = {
            8: 3,
            18: 44,
            19: [1, 0, 1, 0, 1],
            66: 0,
            69: 0,
            70: 0,
            71: 0
        };
        dir[20] = (start >>> 24) & 255;
        dir[21] = (start >>> 16) & 255;
        dir[22] = (start >>> 8) & 255;
        dir[23] = start & 255;
        dir[24] = Math.floor(start / 4294967296) | 0;
        dir[25] = Math.floor(start / 1099511627776) | 0;
        dir[26] = (0 >>> 24) & 255;
        dir[27] = (0 >>> 16) & 255;
        dir[28] = (0 >>> 8) & 255;
        dir[29] = 0;
        dir[30] = Math.floor(1 / 256) & 255;
        dir[31] = 1 & 255;
        dir[32] = (1 >>> 24) & 255;
        dir[33] = (1 >>> 16) & 255;
        dir[34] = (14 >>> 24) & 255;
        dir[35] = (14 >>> 16) & 255;
        dir[36] = (14 >>> 8) & 255;
        dir[37] = 14 & 255;
        dir[38] = array1[21];
        dir[39] = array1[22];
        dir[40] = array2[21];
        dir[41] = array2[22];
        dir[42] = array3[23];
        dir[43] = array3[24];
        var end = start;
        dir[44] = (end >>> 24) & 255;
        dir[45] = (end >>> 16) & 255;
        dir[46] = (end >>> 8) & 255;
        dir[47] = end & 255;
        dir[48] = dir[8];
        dir[49] = Math.floor(end / 4294967296) | 0;
        dir[50] = Math.floor(end / 1099511627776) | 0;
        dir[51] = (pageId >>> 24) & 255;
        dir[52] = (pageId >>> 16) & 255;
        dir[53] = (pageId >>> 8) & 255;
        dir[54] = pageId & 255;
        dir[55] = pageId;
        dir[56] = AID;
        dir[57] = AID & 255;
        dir[58] = (AID >>> 8) & 255;
        dir[59] = (AID >>> 16) & 255;
        dir[60] = (AID >>> 24) & 255;
        dir[64] = fp.length & 255;
        dir[65] = fp.length & 255;

        var values = [];
        for (var i = 0; i < SORT_INDEX.length; i++) values.push(dir[SORT_INDEX[i]] || 0);

        var xor = ((fp.length & 255) >>> 8) & 255;
        for (var s = 0; s < SORT_INDEX_2.length - 1; s++) {
            if (s === 0) xor = dir[SORT_INDEX_2[s]] || 0;
            xor ^= dir[SORT_INDEX_2[s + 1]] || 0;
        }

        values = values.concat(charCodes(fp));
        values.push(xor);

        var mixed = noise(3, rands).concat(mix(values));
        var text = '';
        for (var m = 0; m < mixed.length; m++) text += String.fromCharCode(mixed[m]);
        return bogusEncode(text, ALPHABET_0);
    }

    return {
        sign: sign,
        version: 'abogus-1.0.1.19',
        sm3Hex: function (text) {
            var bytes = sm3(utf8Bytes(text));
            var out = '';
            for (var i = 0; i < bytes.length; i++) {
                out += (bytes[i] < 16 ? '0' : '') + bytes[i].toString(16);
            }
            return out;
        }
    };
})();

if (typeof globalThis !== 'undefined') globalThis.ABogusSign = ABogusSign;
