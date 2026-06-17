import { handleRelayRequest } from './relay.js';

export const config = {
  runtime: 'edge'
};

export default function handler(request) {
  return handleRelayRequest(request, { serverName: 'Vercel Edge Relay' });
}
