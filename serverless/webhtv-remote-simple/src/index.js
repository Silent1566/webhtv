import { handleRelayRequest } from './relay.js';

export default {
  async fetch(request) {
    return handleRelayRequest(request, { serverName: 'Cloudflare Worker Relay' });
  }
};
