import { handleRelayRequest } from './relay.js';

Deno.serve((request) => {
  return handleRelayRequest(request, { serverName: 'Deno Deploy Relay' });
});
