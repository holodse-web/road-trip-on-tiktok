'use strict';

const { WebSocketServer } = require('ws');
const { EventForwarder, rawChat } = require('./events');

function connectionError(error, username) {
  const original = error?.exception || error;
  const pending = [original];
  for (let i = 0; i < pending.length && i < 12; i++) {
    const item = pending[i];
    if (/user_not_found|19881007/i.test(item?.message || ''))
      return `TikTok не нашёл аккаунт @${username} (user_not_found). Проверьте ник: скопируйте его из ссылки на эфир`;
    pending.push(...(item?.config?.requestErrs || []));
  }
  return original?.message || String(original || 'связь прервана');
}

// Inject a connector for lifecycle tests which never contact a real LIVE.
function createBridge({ username, port = 21213, createConnection, log = console.log, retryBaseMs = 5000, likeBatchMs = 100 }) {
  const wss = new WebSocketServer({ host: '127.0.0.1', port, perMessageDeflate: false, maxPayload: 16384 });
  let connection = null;
  let retryTimer = null;
  let retry = retryBaseMs;
  let stopped = false;
  let generation = 0;
  let hasStarted = false;
  let sourcePaused = false;
  let state = { connected: false, status: 'ожидание подключения к TikTok', username, roomId: '' };
  const sendOne = (client, event, data) => {
    if (client.readyState !== 1) return;
    if (client.bufferedAmount > 512 * 1024 && !sourcePaused && connection?.wsClient?.pause) {
      connection.wsClient.pause();
      sourcePaused = true;
      log('[мост] игра догоняет очередь событий');
    }
    client.send(JSON.stringify({ event, data }), (error) => { if (error) client.terminate(); });
  };
  const send = (event, data) => { for (const client of wss.clients) sendOne(client, event, data); };
  const status = (connected, message, roomId = state.roomId) => {
    state = { connected, status: message, username, roomId };
    send('bridgeStatus', state);
  };
  const forwarder = new EventForwarder((event, data) => {
    if (event === 'chat') log(`[мост] чат: ${data.nickname}: ${data.comment}`);
    else if (event === 'gift') log(`[мост] подарок: ${data.nickname} → ${data.giftName} +${data.giftCountDelta} (${data.diamondCount} 💎)`);
    else if (event !== 'like') log(`[мост] ${event}: ${data.nickname}`);
    send(event, data);
  });
  const likeTimer = setInterval(() => {
    forwarder.flushLikes();
    if (sourcePaused && [...wss.clients].every((client) => client.bufferedAmount < 128 * 1024)) {
      connection?.wsClient?.resume();
      sourcePaused = false;
    }
  }, likeBatchMs);
  const heartbeat = setInterval(() => {
    for (const client of wss.clients) {
      if (client.alive === false) { client.terminate(); continue; }
      client.alive = false;
      client.ping();
    }
  }, 30000);
  wss.on('connection', (client) => {
    client.alive = true;
    client.on('pong', () => { client.alive = true; });
    client.on('error', () => client.terminate());
    sendOne(client, 'bridgeStatus', state);
    const gifts = forwarder.giftList();
    if (gifts.length) sendOne(client, 'giftlist', { gifts });
    log('[мост] игра подключилась');
    // Subscribe upstream only after the game is ready to receive events. The
    // launcher needs time to establish its first local WebSocket connection.
    if (!hasStarted) { hasStarted = true; void connect(); }
  });

  async function dispose(old) {
    if (!old) return;
    old.removeAllListeners();
    old.on('error', () => {});
    try { await old.disconnect(); } catch (_) { /* already closed */ }
  }

  function scheduleReconnect(token, error) {
    if (stopped || token !== generation || retryTimer) return;
    const message = connectionError(error, username);
    const delay = retry;
    retry = Math.min(60000, Math.ceil(retry * 1.5));
    status(false, `${message} — повтор через ${Math.ceil(delay / 1000)} с`);
    log(`[мост] ${state.status}`);
    retryTimer = setTimeout(() => { retryTimer = null; void connect(); }, delay);
  }

  async function connect() {
    if (stopped) return;
    const token = ++generation;
    const old = connection;
    connection = null;
    await dispose(old);
    if (stopped || token !== generation) return;
    let current;
    try {
      // Gift metadata already arrives in live events. Fetching the optional
      // catalog would invoke the provider's separately restricted URL signer.
      current = connection = createConnection(username, { processInitialData: false, enableExtendedGiftInfo: false });
      sourcePaused = false;
    } catch (error) { scheduleReconnect(token, error); return; }
    for (const event of ['chat', 'like', 'gift', 'follow', 'share', 'subscribe', 'subNotify', 'superFanJoin']) {
      current.on(event, (data) => { if (!stopped && current === connection) forwarder.accept(event, data); });
    }
    current.on('disconnected', () => scheduleReconnect(token, new Error('отключено от TikTok')));
    current.on('streamEnd', () => scheduleReconnect(token, new Error('эфир завершён')));
    let rawChatNoticed = false;
    current.on('websocketConnected', (ws) => {
      ws?.on?.('protoMessageFetchResult', (result) => {
        if (stopped || current !== connection) return;
        for (const message of result?.messages || []) {
          if (message.method !== 'WebcastChatMessage' || message.decodedData || !message.payload) continue;
          const data = rawChat(message.payload);
          if (!data) continue;
          if (!rawChatNoticed) {
            rawChatNoticed = true;
            log('[мост] часть сообщений чата библиотека не разобрала — текст достаю вручную');
          }
          forwarder.accept('chat', data);
        }
      });
    });
    current.on('error', (error) => {
      // Decoder warnings need no second connection. A transport close or a
      // rejected connect schedules reconnect through a single guarded timer.
      const message = connectionError(error, username);
      log(`[мост] TikTok: ${message}`);
    });
    status(false, `подключение к LIVE @${username}…`, '');
    try {
      const result = await current.connect();
      if (stopped || token !== generation || retryTimer) { await dispose(current); return; }
      retry = retryBaseMs;
      const roomId = String(result.roomId || current.roomId || '');
      status(true, `LIVE @${username} подключён`, roomId);
      log(`[мост] подключено к LIVE @${username} (room ${roomId})`);
    } catch (error) { scheduleReconnect(token, error); }
  }

  wss.on('listening', () => {
    log(`[мост] WebSocket для игры: ws://127.0.0.1:${wss.address().port}/`);
    log('[RTTT_BRIDGE_READY]');
  });
  wss.on('error', (error) => {
    log(error.code === 'EADDRINUSE' ? `[мост] порт ${port} занят: остановите другой мост или TikFinity.` : `[мост] ошибка WebSocket: ${error.message}`);
    void close().then(() => { process.exitCode = 1; });
  });

  async function close() {
    if (stopped) return;
    stopped = true;
    ++generation;
    clearTimeout(retryTimer);
    clearInterval(likeTimer);
    clearInterval(heartbeat);
    forwarder.flushLikes();
    status(false, 'мост остановлен');
    for (const client of wss.clients) client.terminate();
    const old = connection;
    connection = null;
    await dispose(old);
    await new Promise((resolve) => wss.close(() => resolve()));
  }
  return { wss, close, forwarder, get state() { return state; } };
}

async function main() {
  const username = (process.argv[2] || '').trim().replace(/^@/, '');
  const port = Number(process.argv[3] || 21213);
  if (!/^[\w.]{1,64}$/.test(username) || !Number.isInteger(port) || port < 1 || port > 65535) {
    console.log('Использование: node bridge.js ВАШ_НИК_В_TIKTOK [порт 1–65535]');
    process.exitCode = 1;
    return;
  }
  const { TikTokLiveConnection } = await import('tiktok-live-connector');
  const bridge = createBridge({ username, port, createConnection: (name, options) => new TikTokLiveConnection(name, options) });
  for (const signal of ['SIGINT', 'SIGTERM']) process.once(signal, () => { void bridge.close().then(() => { process.exitCode = 0; }); });
}

if (require.main === module) main().catch((error) => { console.error('[мост]', error.message); process.exitCode = 1; });
module.exports = { createBridge, connectionError };
