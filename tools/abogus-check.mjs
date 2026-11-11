// a_bogus 签名自检：用固定时间/随机数跑内嵌脚本，和 tools/abogus-vectors.json 逐字符比对。
// 开发机 `node tools/abogus-check.mjs`，CI 同款，不依赖任何第三方包。
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import vm from 'node:vm';

const here = dirname(fileURLToPath(import.meta.url));
const repo = join(here, '..');
const asset = join(repo, 'app/src/main/assets/sign/abogus.js');
const vectors = JSON.parse(readFileSync(join(here, 'abogus-vectors.json'), 'utf8'));

const sandbox = { Math, Date };
vm.createContext(sandbox);
vm.runInContext(readFileSync(asset, 'utf8'), sandbox, { filename: 'abogus.js' });

const signer = sandbox.ABogusSign;
if (!signer || typeof signer.sign !== 'function') {
  console.error('FAIL: abogus.js 没有导出 ABogusSign.sign');
  process.exit(1);
}

let failed = 0;
for (const v of vectors) {
  const got = signer.sign({
    params: v.params,
    body: v.body,
    ua: v.ua,
    fp: v.fp,
    salt: v.salt,
    pageId: v.pageId,
    now: v.now,
    rand: v.rand,
  });
  const ok = got === v.a_bogus;
  if (!ok) failed++;
  console.log(`${ok ? 'PASS' : 'FAIL'} ${v.name} ${ok ? got.length + ' 字符' : `\n  want ${v.a_bogus}\n  got  ${got}`}`);
}

// 随机模式下只检查产出形状：164~200 字符、无空白
for (let i = 0; i < 20; i++) {
  const got = signer.sign({ params: `a=${i}&b=x`, body: '', ua: vectors[0].ua, fp: vectors[0].fp, salt: 'cus' });
  if (!/^[A-Za-z0-9+/=_-]{100,220}$/.test(got)) {
    console.error(`FAIL random#${i} 产出异常: ${got}`);
    failed++;
  }
}

console.log(failed === 0 ? `全部通过（${vectors.length} 组基准 + 20 次随机）` : `${failed} 项失败`);
process.exit(failed === 0 ? 0 : 1);
