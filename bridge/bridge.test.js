'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { EventEmitter, once } = require('node:events');
const WebSocket = require('ws');
const { EventForwarder, chatText, user, gift } = require('./events');
const { createBridge, connectionError } = require('./bridge');
const rawUser = { id: '98765432100001234', displayId: 'viewer', nickname: 'Зритель' };
const delay = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

test('wrong username has a clear Russian diagnostic from nested provider errors', () => {
  const error = new Error('Failed to retrieve Room ID from all sources.');
  error.config = { requestErrs: [new Error('[fetchRoomInfoApiLiveRoute] API Error 19881007 (user_not_found)')] };
  assert.match(connectionError(error, 'typo'), /не нашёл аккаунт @typo/);
  assert.match(connectionError({ exception: error }, 'typo'), /скопируйте его из ссылки/);
  assert.equal(connectionError(new Error('network failed'), 'test'), 'network failed');
});

test('v3 and legacy chat text preserve Cyrillic/emoji, never stringify objects', () => {
  assert.equal(chatText({ content: 'Привет 👋' }), 'Привет 👋');
  assert.equal(chatText({ comment: { text: 'зомби' } }), 'зомби');
  assert.equal(chatText({ message: { content: 'чат' } }), 'чат');
  assert.equal(chatText({ comment: { unknown: 'broken' } }), '');
  assert.equal(user({ user: rawUser }).uniqueId, 'viewer');
  assert.equal(user({ user: rawUser }).userId, '98765432100001234');
});

test('raw v3 gift fields and large IDs survive normalization', () => {
  const result = gift({ user: rawUser, giftId: '98765432100001234', gift: { name: 'Роза', type: 1, diamondCount: 2, image: { urlList: ['https://example.com/gift.png'] } }, repeatCount: 3, repeatEnd: 1 });
  assert.equal(result.giftName, 'Роза');
  assert.equal(result.giftId, '98765432100001234');
  assert.equal(result.diamondCount, 2);
  assert.equal(result.giftType, 1);
  assert.equal(result.repeatEnd, true);
  assert.equal(result.giftPictureUrl, 'https://example.com/gift.png');
});

test('gift groups, end packets, replay and reordered updates count exactly once', () => {
  const messages = [];
  const f = new EventForwarder((event, data) => messages.push({ event, data }));
  const base = { common: { roomId: 'room' }, user: rawUser, giftId: '1', gift: { name: 'Rose', type: 1 }, groupId: 'streak-1' };
  for (const [repeatCount, repeatEnd] of [[1, 0], [2, 0], [5, 0], [5, 1], [3, 0], [5, 1]]) f.accept('gift', { ...base, repeatCount, repeatEnd });
  f.accept('gift', { ...base, groupId: 'streak-2', repeatCount: 2, repeatEnd: 1 });
  f.accept('gift', { ...base, user: { ...rawUser, id: 'second' }, repeatCount: 4 });
  assert.equal(messages.reduce((sum, message) => sum + message.data.giftCountDelta, 0), 11);
  assert.ok(messages.every((message) => message.data.giftDelta === true));
});

test('legacy streak without group restarts, and message-id duplicates are suppressed', () => {
  const messages = [];
  const f = new EventForwarder((event, data) => messages.push({ event, data }));
  const base = { user: { uniqueId: 'legacy' }, giftId: 1, giftDetails: { giftName: 'Rose', giftType: 1 } };
  for (const [repeatCount, repeatEnd] of [[1, false], [3, false], [3, true], [1, false], [2, true]]) f.accept('gift', { ...base, repeatCount, repeatEnd });
  f.accept('chat', { common: { msgId: 'c1' }, content: 'hello' });
  f.accept('chat', { common: { msgId: 'c1' }, content: 'hello' });
  assert.equal(messages.filter((m) => m.event === 'gift').reduce((sum, m) => sum + m.data.giftCountDelta, 0), 5);
  assert.equal(messages.filter((m) => m.event === 'chat').length, 1);
});

test('5000 likes and 500 gifts retain every count while batched', () => {
  const totals = { like: 0, gift: 0 };
  let likeMessages = 0;
  const f = new EventForwarder((event, data) => {
    if (event === 'like') { totals.like += data.likeCount; likeMessages++; }
    if (event === 'gift') totals.gift += data.giftCountDelta;
  });
  for (let i = 0; i < 5000; i++) f.accept('like', { user: rawUser, count: 10 });
  for (let i = 0; i < 500; i++) f.accept('gift', { user: rawUser, giftId: '9', repeatCount: 3, gift: { type: 0 } });
  f.flushLikes();
  assert.deepEqual(totals, { like: 50000, gift: 1500 });
  assert.equal(likeMessages, 1);
  assert.equal(f.likes.size, 0);
});

test('remembered message IDs and per-viewer like batches remain bounded', () => {
  const f = new EventForwarder(() => {}, { maxRemembered: 20 });
  for (let i = 0; i < 1000; i++) {
    f.accept('chat', { common: { msgId: String(i + 1) }, content: 'x' });
    f.accept('like', { user: { id: String(i) }, count: 1 });
  }
  assert.equal(f.seen.size, 20);
  assert.ok(f.likes.size < 128);
});

test('follow/share and raw v3 subNotify all reach the game', () => {
  const events = [];
  const f = new EventForwarder((event) => events.push(event));
  for (const event of ['follow', 'share', 'subNotify']) f.accept(event, { user: rawUser });
  f.accept('subNotify', { user: rawUser, messageType: 1 });
  f.accept('subNotify', { user: rawUser, messageType: 0, subscribingStatus: 4 });
  assert.deepEqual(events, ['follow', 'share', 'subscribe']);
});

class FakeConnection extends EventEmitter {
  async connect() { return { roomId: 'fake-room' }; }
  async disconnect() { this.closed = true; }
  async fetchAvailableGifts() { return []; }
}

test('LIVE events work without calling the restricted gift catalog signer', async () => {
  let connector, options, catalogRequests = 0;
  const logs = [];
  const bridge = createBridge({ username: 'fixture', port: 0, log: (s) => logs.push(s), createConnection: (_name, connectionOptions) => {
    options = connectionOptions;
    connector = new FakeConnection();
    connector.fetchAvailableGifts = async () => { catalogRequests++; throw new Error('This endpoint requires a Business plan'); };
    return connector;
  } });
  let ws;
  try {
    await once(bridge.wss, 'listening');
    ws = new WebSocket(`ws://127.0.0.1:${bridge.wss.address().port}`);
    const messages = [];
    ws.on('message', (message) => messages.push(JSON.parse(message)));
    await once(ws, 'open');
    await delay(5);
    connector.emit('gift', { user: rawUser, giftId: '42', repeatCount: 2, gift: { name: 'Rose', type: 0, diamondCount: 1, image: { urlList: ['https://example.com/rose.png'] } } });
    await delay(20);
    assert.equal(options.enableExtendedGiftInfo, false);
    assert.equal(catalogRequests, 0);
    assert.equal(bridge.state.connected, true);
    const received = messages.find((message) => message.event === 'gift')?.data;
    assert.equal(received?.giftName, 'Rose');
    assert.equal(received?.giftCountDelta, 2);
    assert.equal(received?.diamondCount, 1);
    assert.equal(received?.giftPictureUrl, 'https://example.com/rose.png');
    assert.ok(!logs.some((line) => line.includes('Business plan')));
  } finally { ws?.terminate(); await bridge.close(); }
});

test('local websocket delivers normalized events/status and shutdown clears lifecycle', async () => {
  let connector;
  const logs = [];
  const bridge = createBridge({ username: 'fixture', port: 0, log: (s) => logs.push(s), createConnection: () => (connector = new FakeConnection()), likeBatchMs: 10 });
  let ws;
  try {
    await once(bridge.wss, 'listening');
    assert.equal(connector, undefined);
    ws = new WebSocket(`ws://127.0.0.1:${bridge.wss.address().port}`);
    const messages = [];
    ws.on('message', (message) => messages.push(JSON.parse(message)));
    await once(ws, 'open');
    await delay(5);
    connector.emit('chat', { user: rawUser, content: 'видимый текст' });
    connector.emit('like', { user: rawUser, count: 500 });
    connector.emit('subNotify', { user: rawUser });
    await delay(40);
    assert.ok(logs.includes('[RTTT_BRIDGE_READY]'));
    assert.ok(messages.some((m) => m.event === 'bridgeStatus' && m.data.connected));
    assert.ok(messages.some((m) => m.event === 'chat' && m.data.comment === 'видимый текст'));
    assert.equal(messages.filter((m) => m.event === 'like').reduce((n, m) => n + m.data.likeCount, 0), 500);
    assert.ok(messages.some((m) => m.event === 'subscribe'));
  } finally { ws?.terminate(); await bridge.close(); }
  assert.equal(connector.closed, true);
});

test('disconnect plus failed connect schedules exactly one retry', async () => {
  const connectors = [];
  let ws;
  const bridge = createBridge({ username: 'fixture', port: 0, log: () => {}, retryBaseMs: 20, createConnection: () => {
    const current = new FakeConnection();
    connectors.push(current);
    if (connectors.length === 1) current.connect = async () => { current.emit('disconnected'); throw new Error('offline'); };
    return current;
  } });
  try {
    await once(bridge.wss, 'listening');
    ws = new WebSocket(`ws://127.0.0.1:${bridge.wss.address().port}`);
    await once(ws, 'open');
    await delay(70);
    assert.equal(connectors.length, 2);
    assert.equal(connectors[0].closed, true);
    assert.equal(connectors[0].listenerCount('chat'), 0);
    assert.equal(bridge.state.connected, true);
  } finally { ws?.terminate(); await bridge.close(); }
  await delay(30);
  assert.equal(connectors.length, 2);
});

test('synchronous constructor failure retries without an unhandled rejection', async () => {
  let attempts = 0;
  let ws;
  const bridge = createBridge({ username: 'fixture', port: 0, log: () => {}, retryBaseMs: 10, createConnection: () => {
    if (++attempts === 1) throw new Error('constructor failed');
    return new FakeConnection();
  } });
  try {
    await once(bridge.wss, 'listening');
    ws = new WebSocket(`ws://127.0.0.1:${bridge.wss.address().port}`);
    await once(ws, 'open');
    await delay(50);
    assert.equal(attempts, 2);
    assert.equal(bridge.state.connected, true);
  } finally { ws?.terminate(); await bridge.close(); }
});
