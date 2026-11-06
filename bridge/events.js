'use strict';

// v3 protobuf events use content/count/gift/displayId. Legacy names still work.
const string = (...values) => {
  for (const value of values) {
    if (typeof value === 'string' && value) return value;
    if (typeof value === 'number' || typeof value === 'bigint') return String(value);
  }
  return '';
};
const number = (value, fallback = 0) => {
  const n = Number(value);
  return Number.isFinite(n) ? Math.max(0, Math.min(Number.MAX_SAFE_INTEGER, Math.floor(n))) : fallback;
};
const text = (value, depth = 0) => {
  if (typeof value === 'string') return value;
  if (!value || typeof value !== 'object' || depth > 3) return '';
  for (const key of ['text', 'content', 'comment', 'message']) {
    const result = text(value[key], depth + 1);
    if (result) return result;
  }
  return '';
};
const chatText = (data) => [data.content, data.comment, data.message, data.text].map((v) => text(v)).find(Boolean) || '';
const user = (data) => {
  const u = data.user || data;
  const uniqueId = string(u.uniqueId, u.displayId, u.username, u.userId, u.id, u.idStr);
  return { uniqueId, userId: string(u.userId, u.id, u.idStr, uniqueId), nickname: string(u.nickname, u.nickName, u.displayName, uniqueId) || 'зритель' };
};
const picture = (object) => {
  if (!object) return '';
  for (const key of ['giftPictureUrl', 'giftImage', 'image', 'icon', 'previewImage']) {
    const value = object[key];
    if (typeof value === 'string' && /^https?:\/\//i.test(value)) return value;
    if (!value || typeof value !== 'object') continue;
    const direct = string(value.giftPictureUrl, value.url);
    if (direct) return direct;
    const urls = value.urlList || value.url_list || value.urls;
    if (Array.isArray(urls)) {
      const url = urls.find((v) => typeof v === 'string');
      if (url) return url;
    }
  }
  return '';
};
const gift = (data, catalog = new Map()) => {
  const detail = data.gift || data.giftDetails || {};
  const extended = data.extendedGiftInfo || {};
  const giftId = string(data.giftId, detail.id);
  const cached = catalog.get(giftId) || {};
  return {
    ...user(data), giftId,
    giftName: string(data.giftName, detail.name, detail.giftName, extended.name, cached.name) || `Gift ${giftId}`,
    giftType: number(data.giftType ?? detail.type ?? detail.giftType ?? extended.type ?? cached.type),
    diamondCount: number(data.diamondCount ?? detail.diamondCount ?? extended.diamond_count ?? extended.diamondCount ?? cached.diamonds),
    repeatCount: Math.max(1, number(data.repeatCount ?? data.count, 1)),
    repeatEnd: data.repeatEnd === true || data.repeatEnd === 1 || data.repeatEnd === '1' || data.repeatEnd === 'true',
    groupId: string(data.groupId, data.streakId, data.comboId).replace(/^0$/, ''),
    giftPictureUrl: picture(data) || picture(detail) || picture(extended) || cached.picture || '',
  };
};

class EventForwarder {
  constructor(send, { now = Date.now, maxRemembered = 20000, retentionMs = 10 * 60 * 1000 } = {}) {
    this.send = send;
    this.now = now;
    this.maxRemembered = maxRemembered;
    this.retentionMs = retentionMs;
    this.seen = new Map();
    this.streaks = new Map();
    this.likes = new Map();
    this.catalog = new Map();
  }

  remember(map, key, value, maximum = this.maxRemembered) {
    map.delete(key);
    map.set(key, value);
    const cutoff = this.now() - this.retentionMs;
    for (const [oldest, entry] of map) {
      if (map.size <= maximum && entry.time >= cutoff) break;
      map.delete(oldest);
    }
  }

  duplicate(event, data) {
    const id = string(data.common?.msgId, data.msgId, data.messageId).replace(/^0$/, '');
    if (!id) return false;
    const suffix = event === 'gift' ? `:${data.repeatCount || 1}:${data.repeatEnd || 0}` : '';
    const key = `${event}:${string(data.common?.roomId, data.roomId)}:${id}${suffix}`;
    const old = this.seen.get(key);
    const time = this.now();
    if (old && time - old.time < this.retentionMs) return true;
    this.remember(this.seen, key, { time });
    return false;
  }

  accept(event, data = {}) {
    // subNotify also carries reminders, expiry and refunds. Only a successful
    // subscription is a gameplay subscription event (v3 MessageType=0).
    if (event === 'subNotify' && (number(data.messageType) !== 0 || number(data.subscribingStatus) >= 3)) return;
    if (this.duplicate(event, data)) return;
    if (event === 'chat') {
      const comment = chatText(data).slice(0, 4096);
      if (comment) this.send('chat', { ...user(data), comment });
    } else if (event === 'like') {
      const who = user(data);
      const key = who.userId || who.uniqueId || who.nickname;
      const count = number(data.count ?? data.likeCount, 1);
      if (!count) return;
      const pending = this.likes.get(key);
      this.likes.set(key, { ...who, likeCount: Math.min(Number.MAX_SAFE_INTEGER, (pending?.likeCount || 0) + count), totalLikeCount: number(data.total ?? data.totalLikeCount) });
      if (this.likes.size >= 128) this.flushLikes();
    } else if (event === 'gift') {
      const value = gift(data, this.catalog);
      let delta = value.repeatCount;
      if (value.giftType === 1) {
        const key = `${string(data.common?.roomId, data.roomId)}|${value.userId || value.uniqueId || value.nickname}|${value.giftId}|${value.groupId || value.giftName}`;
        const old = this.streaks.get(key);
        let previous = old && this.now() - old.time < this.retentionMs ? old.count : 0;
        // Retain completed group IDs to reject replay and out-of-order updates.
        if (!value.groupId && old && !value.repeatEnd && (old.ended || value.repeatCount < previous)) previous = 0;
        delta = Math.max(0, value.repeatCount - previous);
        this.remember(this.streaks, key, { count: Math.max(previous, value.repeatCount), ended: value.repeatEnd, time: this.now() }, 5000);
      }
      if (delta > 0) this.send('gift', { ...value, giftDelta: true, giftCountDelta: delta });
    } else if (['follow', 'share', 'subscribe', 'subNotify', 'superFanJoin'].includes(event)) {
      this.send(event === 'superFanJoin' || event === 'subNotify' ? 'subscribe' : event, user(data));
    }
  }

  flushLikes() {
    for (const value of this.likes.values()) this.send('like', value);
    this.likes.clear();
  }

  setCatalog(gifts) {
    if (!Array.isArray(gifts)) return;
    this.catalog.clear();
    for (const g of gifts.slice(0, 5000)) {
      const id = string(g.id, g.giftId);
      if (id) this.catalog.set(id, { name: string(g.name, g.giftName), diamonds: number(g.diamond_count ?? g.diamondCount), type: number(g.type ?? g.giftType), picture: picture(g) });
    }
  }

  giftList() {
    return [...this.catalog.entries()].map(([giftId, g]) => ({ giftId, name: g.name, diamondCount: g.diamonds, giftPictureUrl: g.picture })).filter((g) => g.name && g.giftPictureUrl);
  }
}

const utf8 = new TextDecoder('utf-8', { fatal: true });
const protoFields = (bytes) => {
  const fields = [];
  let i = 0;
  const varint = () => {
    let result = 0n, shift = 0n;
    for (;;) {
      if (i >= bytes.length || shift > 63n) throw new Error('varint');
      const b = bytes[i++];
      result |= BigInt(b & 0x7f) << shift;
      if (!(b & 0x80)) return result;
      shift += 7n;
    }
  };
  while (i < bytes.length) {
    const key = Number(varint());
    const field = key >>> 3, wire = key & 7;
    if (wire === 0) fields.push({ field, wire, value: varint() });
    else if (wire === 2) {
      const len = Number(varint());
      if (len < 0 || i + len > bytes.length) throw new Error('length');
      fields.push({ field, wire, value: bytes.subarray(i, i + len) });
      i += len;
    } else if (wire === 1) i += 8;
    else if (wire === 5) i += 4;
    else throw new Error('wire');
  }
  return fields;
};
const rawString = (bytes) => { try { return utf8.decode(bytes); } catch (_) { return ''; } };
// Fallback for chat payloads the protobuf schema rejects: content is field 3,
// the user is field 2 (id 1, nickname 3, displayId 38).
const rawChat = (payload) => {
  try {
    const top = protoFields(new Uint8Array(payload));
    const content = top.find((f) => f.field === 3 && f.wire === 2);
    if (!content) return null;
    const comment = rawString(content.value);
    if (!comment) return null;
    const u = { nickname: '', userId: '', displayId: '' };
    const userField = top.find((f) => f.field === 2 && f.wire === 2);
    if (userField) {
      try {
        for (const f of protoFields(userField.value)) {
          if (f.field === 1 && f.wire === 0) u.userId = f.value.toString();
          else if (f.field === 3 && f.wire === 2) u.nickname = rawString(f.value);
          else if (f.field === 38 && f.wire === 2) u.displayId = rawString(f.value);
        }
      } catch (_) { /* keep text without user */ }
    }
    return { content: comment, user: u };
  } catch (_) {
    return null;
  }
};

module.exports = { EventForwarder, chatText, user, gift, picture, rawChat };
