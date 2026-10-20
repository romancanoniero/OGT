import { test, expect } from '@playwright/test';
import { readFile } from 'node:fs/promises';

test.skip(process.env.OGT_FULL_E2E !== '1', 'Requires the isolated API, PostGIS and Firebase emulator.');

test.beforeEach(async ({ request }) => {
  expect((await request.get('/health', { timeout: 5000 })).ok()).toBe(true);
});

const EMULATOR = 'http://127.0.0.1:19099/identitytoolkit.googleapis.com/v1';

async function mintUser(request, label) {
  const email = `qa-${label}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@example.test`;
  const signed = await request.post(`${EMULATOR}/accounts:signUp?key=demo-key`, {
    data: { email, password: 'OnlyGoodThings-QA-2468', returnSecureToken: true },
  });
  expect(signed.ok(), await signed.text()).toBe(true);
  const { idToken } = await signed.json();
  const session = await request.post('/api/v1/auth/session', { data: { token: idToken } });
  expect(session.ok(), await session.text()).toBe(true);
  const me = await request.post('/api/v1/auth/me', {
    headers: { Authorization: `Bearer ${idToken}` },
    data: {},
  });
  expect(me.ok()).toBe(true);
  return { email, token: idToken, me: (await me.json()).data };
}

function authed(request, token) {
  return (path, data = {}) => request.post(path, {
    headers: { Authorization: `Bearer ${token}` },
    data,
  });
}

function socketExchange(token, listens) {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket('ws://127.0.0.1:19083/db');
    const messages = [];
    const timer = setTimeout(() => {
      ws.close();
      reject(new Error(`socket sin respuesta: ${JSON.stringify(messages)}`));
    }, 8000);
    const finish = () => {
      clearTimeout(timer);
      ws.close();
      resolve(messages);
    };
    ws.addEventListener('open', () => {
      ws.send(JSON.stringify({ t: 'auth', token }));
    });
    ws.addEventListener('message', (ev) => {
      const msg = JSON.parse(String(ev.data));
      messages.push(msg);
      if (msg.t === 'ack' && msg.rid === 0) {
        for (const frame of listens) ws.send(JSON.stringify(frame));
        if (listens.length === 0) finish();
      }
      if (msg.t === 'error' && msg.code === 'UNAUTHENTICATED' && listens.length === 0) finish();
      const settled = listens.length > 0 && listens.every((frame) =>
        messages.some((item) => item.id === frame.id && (item.t === 'listen_ack' || item.t === 'error')),
      );
      if (settled) finish();
    });
    ws.addEventListener('error', () => {
      clearTimeout(timer);
      reject(new Error(`socket error: ${JSON.stringify(messages)}`));
    });
  });
}

test('invalid, tampered and laboratory tokens do not open a session or a socket', async ({ request }) => {
  const expired = 'eyJhbGciOiJub25lIn0.eyJleHAiOjF9.';
  for (const token of ['dev.qa.COMPANY_ADMIN', 'not-a-jwt', expired]) {
    const session = await request.post('/api/v1/auth/session', { data: { token } });
    expect(session.status()).toBe(401);
    const me = await request.post('/api/v1/auth/me', {
      headers: { Authorization: `Bearer ${token}` },
      data: {},
    });
    expect(me.status()).toBe(401);
    const frames = await socketExchange(token, []);
    expect(frames.some((frame) => frame.t === 'error' && frame.code === 'UNAUTHENTICATED')).toBe(true);
    expect(frames.some((frame) => frame.t === 'ack')).toBe(false);
  }
});

test('account B cannot listen to private paths and cannot edit account A', async ({ request }) => {
  const accountA = await mintUser(request, 'socket-a');
  const accountB = await mintUser(request, 'socket-b');
  const callA = authed(request, accountA.token);
  const callB = authed(request, accountB.token);
  const marker = `Nombre aislado ${Date.now()}`;
  expect((await callA('/api/v1/users/profile/update', { displayName: marker })).ok()).toBe(true);
  const crossed = await callB('/api/v1/users/profile/update', {
    userId: accountA.me.userId,
    displayName: 'Nombre robado',
    role: 'COMPANY_ADMIN',
  });
  expect(crossed.ok()).toBe(true);
  const stillA = await callA('/api/v1/account/export', {});
  expect(stillA.ok()).toBe(true);
  const exportedA = (await stillA.json()).data;
  expect(exportedA.displayName).toBe(marker);
  expect(exportedA.email).toBe(accountA.email);
  const exportedB = (await (await callB('/api/v1/account/export', { userId: accountA.me.userId })).json()).data;
  expect(exportedB.userId).toBe(accountB.me.userId);
  expect(exportedB.email).toBe(accountB.email);
  expect(JSON.stringify(exportedB)).not.toContain(accountA.email);
  expect(JSON.stringify(exportedB)).not.toContain(marker);

  const denied = [
    { t: 'listen', id: 1, path: '' },
    { t: 'listen', id: 2, path: 'ogt' },
    { t: 'listen', id: 3, path: 'ogt/presence' },
    { t: 'listen', id: 4, path: `ogt/presence/${accountA.me.userId}` },
    { t: 'listen', id: 5, path: 'ogt/private/inbox' },
    { t: 'listen', id: 6, path: 'ogt/social' },
  ];
  const allowed = [
    { t: 'listen', id: 7, path: 'ogt/social/posts' },
    { t: 'listen', id: 8, path: 'ogt/parking/spots' },
    { t: 'listen', id: 9, path: 'ogt/alerts' },
    { t: 'listen', id: 10, path: `ogt/presence/${accountB.me.userId}` },
  ];
  const frames = await socketExchange(accountB.token, [...denied, ...allowed]);
  for (const frame of denied) {
    const reply = frames.find((item) => item.id === frame.id);
    expect(reply?.t, JSON.stringify(reply)).toBe('error');
    expect(reply?.code).toBe('PERMISSION_DENIED');
  }
  for (const frame of allowed) {
    expect(frames.find((item) => item.id === frame.id)?.t).toBe('listen_ack');
  }

  const jpeg = await readFile(new URL('../../backend/src/test/resources/images/rotated.jpg', import.meta.url));
  const uploaded = await callA('/api/v1/media/upload', {
    filename: 'aviso.jpg',
    contentType: 'image/jpeg',
    bytesBase64: jpeg.toString('base64'),
  });
  expect(uploaded.ok(), await uploaded.text()).toBe(true);
  const mediaUrl = (await uploaded.json()).data.url;
  const published = await callA('/api/v1/social/publish', {
    body: `Aviso QA ${Date.now()}`,
    topic: 'Comunidad',
    media: [{ url: mediaUrl, kind: 'IMAGE' }],
  });
  expect(published.ok(), await published.text()).toBe(true);
  const postId = (await published.json()).data.id;
  const report = await callB('/api/v1/social/report', {
    postId,
    reason: 'QA',
    details: 'Denuncia sintética del gate local',
  });
  expect(report.ok()).toBe(true);
  const stolen = await callB('/api/v1/social/post/delete', { postId });
  expect(stolen.status()).toBeGreaterThanOrEqual(400);
  expect(stolen.status()).toBeLessThan(500);
});

test('account export stays private and deletion removes that account only', async ({ request }) => {
  const accountA = await mintUser(request, 'baja-a');
  const accountB = await mintUser(request, 'baja-b');
  const callA = authed(request, accountA.token);
  const callB = authed(request, accountB.token);
  const marker = `Texto baja ${Date.now()}`;
  expect((await callA('/api/v1/users/profile/update', { displayName: marker })).ok()).toBe(true);
  const unconfirmed = await callA('/api/v1/account/delete', { userId: accountB.me.userId });
  expect(unconfirmed.status()).toBe(400);
  expect((await callB('/api/v1/account/export', {})).ok()).toBe(true);
  const removed = await callA('/api/v1/account/delete', {
    confirm: 'BAJA',
    userId: accountB.me.userId,
  });
  expect(removed.ok(), await removed.text()).toBe(true);
  const survivor = await callB('/api/v1/account/export', {});
  expect(survivor.ok()).toBe(true);
  expect((await survivor.json()).data.userId).toBe(accountB.me.userId);
  const recreated = await callA('/api/v1/auth/me', {});
  expect(recreated.ok()).toBe(true);
  const fresh = (await recreated.json()).data;
  expect(fresh.userId).not.toBe(accountA.me.userId);
  expect(fresh.displayName).not.toBe(marker);
  const gone = await callB('/api/v1/users/profile', { userId: accountA.me.userId });
  const goneBody = await gone.json();
  expect(JSON.stringify(goneBody)).not.toContain(marker);
  expect(JSON.stringify(goneBody)).not.toContain(accountA.email);
});

test('parking publish rate limit rejects the ninth burst from the same account', async ({ request }) => {
  const account = await mintUser(request, 'rate');
  const call = authed(request, account.token);
  const body = {
    latitude: -34.6037,
    longitude: -58.3816,
    ttlMinutes: 15,
    notes: 'QA rate',
    leftoverNow: true,
  };
  const statuses = [];
  for (let i = 0; i < 9; i += 1) {
    statuses.push((await call('/api/v1/parking/publish', body)).status());
  }
  expect(statuses.slice(0, 8).every((status) => status === 200)).toBe(true);
  expect(statuses[8]).toBe(429);
});
