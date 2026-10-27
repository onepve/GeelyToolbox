// CDP 实测：量卡片 4/5 底边 y 坐标 + 「计划运行中」按钮是否在卡片 4 边框内
// 用法: node scripts/probe_layout_mismatch.js [ws_url] [page_url片段]
const http = require('http');

function getJson(path) {
  return new Promise((resolve, reject) => {
    http.get({ host: '127.0.0.1', port: 19122, path }, (res) => {
      let d = '';
      res.on('data', (c) => (d += c));
      res.on('end', () => resolve(JSON.parse(d)));
    }).on('error', reject);
  });
}

(async () => {
  const pages = await getJson('/json');
  const wsUrl = process.argv[2]
    || (pages.find((p) => (p.url || '').includes('toolbox_ui')) || pages[0]).webSocketDebuggerUrl;
  console.log('target:', wsUrl ? wsUrl.slice(0, 60) + '...' : 'NONE', '| url:', (pages.find(p => p.webSocketDebuggerUrl === wsUrl) || {}).url);
  const ws = new WebSocket(wsUrl);
  let id = 0;
  const pending = new Map();

  const send = (method, params = {}) =>
    new Promise((resolve, reject) => {
      const mid = ++id;
      pending.set(mid, { resolve, reject });
      ws.send(JSON.stringify({ id: mid, method, params }));
    });

  ws.onmessage = (ev) => {
    const msg = JSON.parse(ev.data);
    if (msg.id && pending.has(msg.id)) {
      const { resolve, reject } = pending.get(msg.id);
      pending.delete(msg.id);
      msg.error ? reject(new Error(JSON.stringify(msg.error))) : resolve(msg.result);
    }
  };

  await new Promise((r) => (ws.onopen = r));
  await send('Runtime.enable');

  const evalJs = async (expr) => {
    const r = await send('Runtime.evaluate', {
      expression: expr,
      returnByValue: true,
      awaitPromise: true,
    });
    return r.result.value;
  };

  // 1) 找卡片 4/5 与「计划运行中」按钮
  const layout = await evalJs(`(() => {
    const out = {};
    const cards = [...document.querySelectorAll('div')].filter((d) =>
      /^4\\. 车速达标智能启播/.test((d.textContent || '').slice(0, 30)) &&
      d.querySelector('button') && d.className.includes('rounded-3xl')
    );
    const c4 = cards[cards.length - 1];
    if (c4) {
      const r = c4.getBoundingClientRect();
      out.card4 = { x: r.x, y: r.y, w: r.width, h: r.height, bottom: r.bottom };
    }
    const cards5 = [...document.querySelectorAll('div')].filter((d) =>
      /^5\\. 车速达标自定义动作/.test((d.textContent || '').slice(0, 30)) &&
      d.querySelector('button') && d.className.includes('rounded-3xl')
    );
    const c5 = cards5[cards5.length - 1];
    if (c5) {
      const r = c5.getBoundingClientRect();
      out.card5 = { x: r.x, y: r.y, w: r.width, h: r.height, bottom: r.bottom };
    }
    // 所有含「计划运行中/计划已暂停」的按钮及其父卡片
    out.btns = [...document.querySelectorAll('button')]
      .filter((b) => /计划运行中|计划已暂停/.test(b.textContent))
      .map((b) => {
        const r = b.getBoundingClientRect();
        // 找最近 rounded-3xl 祖先
        let a = b.parentElement, anc = null;
        while (a) { if ((a.className||'').includes && String(a.className).includes('rounded-3xl')) { anc = a; break; } a = a.parentElement; }
        const ar = anc ? anc.getBoundingClientRect() : null;
        return {
          text: b.textContent.trim().slice(0, 14),
          x: Math.round(r.x), y: Math.round(r.y),
          w: Math.round(r.width), h: Math.round(r.height),
          inCard: ar ? (r.left >= ar.left && r.right <= ar.right && r.top >= ar.top && r.bottom <= ar.bottom) : null,
          cardTitle: anc ? (anc.textContent.match(/^\\d\\. [^\\d]{2,18}/) || [''])[0].trim().slice(0, 20) : null,
        };
      });
    // 当前页面标识
    const nav = document.querySelector('.router-link-active, [class*="active"]');
    out.page = location.hash || location.pathname;
    out.vw = innerWidth; out.vh = innerHeight;
    return out;
  })()`);

  console.log(JSON.stringify(layout, null, 2));
  ws.close();
  process.exit(0);
})().catch((e) => { console.error('ERR', e.message); process.exit(1); });
