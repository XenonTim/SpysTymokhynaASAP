
const ASAPCrypto = (() => {
  const RSA_PARAMS = { name: "RSA-OAEP", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" };
  const RSA_USAGE_PUB = ["encrypt"];
  const RSA_USAGE_PRIV = ["decrypt"];
  const AES_PARAMS = { name: "AES-GCM", length: 256 };

  let myKeyPair = null;
  let myPublicKeyB64 = null;

  const chatKeys = new Map();

  const peerPublicKeys = new Map();

  function bufToB64(buf) {
    return btoa(String.fromCharCode(...new Uint8Array(buf)));
  }
  function b64ToBuf(b64) {
    const bin = atob(b64);
    const bytes = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
    return bytes.buffer;
  }

  async function loadOrCreateIdentity(login) {
    const storageKey = `asap_rsa_keypair_${login}`;
    const stored = localStorage.getItem(storageKey);

    if (stored) {
      try {
        const jwk = JSON.parse(stored);
        const publicKey = await crypto.subtle.importKey(
          "jwk", jwk.publicKey, { name: "RSA-OAEP", hash: "SHA-256" }, true, RSA_USAGE_PUB
        );
        const privateKey = await crypto.subtle.importKey(
          "jwk", jwk.privateKey, { name: "RSA-OAEP", hash: "SHA-256" }, true, RSA_USAGE_PRIV
        );
        myKeyPair = { publicKey, privateKey };
        myPublicKeyB64 = await exportPublicKeySpki(publicKey);
        return myPublicKeyB64;
      } catch (e) {
        console.warn("ASAPCrypto: не вдалося завантажити збережені ключі, генерую нові", e);
      }
    }

    const keyPair = await crypto.subtle.generateKey(RSA_PARAMS, true, [...RSA_USAGE_PUB, ...RSA_USAGE_PRIV]);
    myKeyPair = keyPair;

    const publicJwk = await crypto.subtle.exportKey("jwk", keyPair.publicKey);
    const privateJwk = await crypto.subtle.exportKey("jwk", keyPair.privateKey);
    localStorage.setItem(storageKey, JSON.stringify({ publicKey: publicJwk, privateKey: privateJwk }));

    myPublicKeyB64 = await exportPublicKeySpki(keyPair.publicKey);
    return myPublicKeyB64;
  }

  async function exportPublicKeySpki(publicKey) {
    const spki = await crypto.subtle.exportKey("spki", publicKey);
    return bufToB64(spki);
  }

  function getMyPublicKeyB64() {
    return myPublicKeyB64;
  }

  async function importPeerPublicKey(login, publicKeyB64) {
    if (peerPublicKeys.has(login)) return peerPublicKeys.get(login);
    const key = await crypto.subtle.importKey(
      "spki", b64ToBuf(publicKeyB64), { name: "RSA-OAEP", hash: "SHA-256" }, true, RSA_USAGE_PUB
    );
    peerPublicKeys.set(login, key);
    return key;
  }

  async function hasChatKey(chatId) {
    if (chatKeys.has(chatId)) return true;

    const stored = localStorage.getItem("asap_chat_key_" + chatId);
    if (stored) {
      try {
        const key = await crypto.subtle.importKey("raw", b64ToBuf(stored), AES_PARAMS, true, ["encrypt", "decrypt"]);
        chatKeys.set(chatId, key);
        return true;
      } catch (e) {
        console.warn("Не вдалося відновити AES-ключ зі сховища", e);
      }
    }
    return false;
  }

  async function generateChatKey(chatId) {
    const key = await crypto.subtle.generateKey(AES_PARAMS, true, ["encrypt", "decrypt"]);
    chatKeys.set(chatId, key);

    const raw = await crypto.subtle.exportKey("raw", key);
    localStorage.setItem("asap_chat_key_" + chatId, bufToB64(raw));

    return key;
  }

  async function wrapChatKeyForPeer(chatId, peerPublicKey) {
    const key = chatKeys.get(chatId);
    const raw = await crypto.subtle.exportKey("raw", key);
    const encrypted = await crypto.subtle.encrypt({ name: "RSA-OAEP" }, peerPublicKey, raw);
    return bufToB64(encrypted);
  }

  async function unwrapChatKey(chatId, encryptedKeyB64) {
    const raw = await crypto.subtle.decrypt(
      { name: "RSA-OAEP" }, myKeyPair.privateKey, b64ToBuf(encryptedKeyB64)
    );
    const key = await crypto.subtle.importKey("raw", raw, AES_PARAMS, true, ["encrypt", "decrypt"]);
    chatKeys.set(chatId, key);
    return key;
  }

  async function encryptMessage(chatId, plainText) {
    const key = chatKeys.get(chatId);
    if (!key) throw new Error("No chat key for " + chatId);
    const iv = crypto.getRandomValues(new Uint8Array(12));
    const encoded = new TextEncoder().encode(plainText);
    const cipherBuf = await crypto.subtle.encrypt({ name: "AES-GCM", iv }, key, encoded);

    const combined = new Uint8Array(iv.length + cipherBuf.byteLength);
    combined.set(iv, 0);
    combined.set(new Uint8Array(cipherBuf), iv.length);
    return bufToB64(combined.buffer);
  }

  async function decryptMessage(chatId, payloadB64) {
    const key = chatKeys.get(chatId);
    if (!key) throw new Error("No chat key for " + chatId);
    const combined = new Uint8Array(b64ToBuf(payloadB64));
    const iv = combined.slice(0, 12);
    const cipherBytes = combined.slice(12);
    const plainBuf = await crypto.subtle.decrypt({ name: "AES-GCM", iv }, key, cipherBytes);
    return new TextDecoder().decode(plainBuf);
  }

  return {
    loadOrCreateIdentity,
    getMyPublicKeyB64,
    importPeerPublicKey,
    hasChatKey,
    generateChatKey,
    wrapChatKeyForPeer,
    unwrapChatKey,
    encryptMessage,
    decryptMessage,
  };
})();
