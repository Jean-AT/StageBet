import ws from 'k6/ws';
import { check } from 'k6';

export const options = { vus: 5, duration: '30s' };

export default function () {
  const response = ws.connect(`${__ENV.WS_URL}/ws`, {}, (socket) => {
    socket.on('open', () => socket.send(JSON.stringify({ subscribe: `/topic/rounds/${__ENV.ROUND_ID}` })));
    socket.setTimeout(() => socket.close(), 5000);
  });
  check(response, { 'websocket handshake completed': (result) => result && result.status === 101 });
}
