// asap-client.js
// Тонка обгортка над WebSocket для спілкування з WebGateway.
// Кожне повідомлення — плоский JSON-об'єкт з полем "type", що відповідає
// org.example.shared.protocol.PacketType на сервері.

const ASAPClient = (() => {
  let socket = null;
  let onMessageHandlers = [];
  let onOpenHandlers = [];
  let onCloseHandlers = [];

  function connect() {
    return new Promise((resolve, reject) => {
      const protocol = location.protocol === "https:" ? "wss:" : "ws:";
      socket = new WebSocket(`${protocol}//${location.host}/ws`);

      socket.onopen = () => {
        onOpenHandlers.forEach((fn) => fn());
        resolve();
      };

      socket.onmessage = (event) => {
        let data;
        try {
          data = JSON.parse(event.data);
        } catch (e) {
          console.error("ASAPClient: некоректний JSON від сервера", event.data);
          return;
        }
        onMessageHandlers.forEach((fn) => fn(data));
      };

      socket.onclose = () => {
        onCloseHandlers.forEach((fn) => fn());
      };

      socket.onerror = (err) => {
        reject(err);
      };
    });
  }

  function send(type, fields) {
    if (!socket || socket.readyState !== WebSocket.OPEN) {
      console.warn("ASAPClient: спроба надіслати повідомлення без активного з'єднання");
      return;
    }
    socket.send(JSON.stringify({ type, ...fields }));
  }

  function onMessage(fn) {
    onMessageHandlers.push(fn);
  }

  function onOpen(fn) {
    onOpenHandlers.push(fn);
  }

  function onClose(fn) {
    onCloseHandlers.push(fn);
  }

  function clearHandlers() {
    onMessageHandlers = [];
    onOpenHandlers = [];
    onCloseHandlers = [];
  }

  return { connect, send, onMessage, onOpen, onClose, clearHandlers };
})();
