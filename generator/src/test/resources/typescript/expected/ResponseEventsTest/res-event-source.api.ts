import {ClientSettings, MediaType, Transport} from '@outfoxx/sunday';


export interface API<Factory extends SundayTransport> {

  fetchEvents(signal?: AbortSignal): EventSource;

}

class APIClient<Factory extends SundayTransport> {

  defaultContentTypes: Array<MediaType>;

  defaultAcceptTypes: Array<MediaType>;

  constructor(public transport: Factory,
      options: { defaultContentTypes?: Array<MediaType>, defaultAcceptTypes?: Array<MediaType> } | undefined = undefined,
      private clientSettings: ClientSettings | undefined = undefined) {
    this.defaultContentTypes =
        options?.defaultContentTypes ?? [];
    this.defaultAcceptTypes =
        options?.defaultAcceptTypes ?? [];
  }

  fetchEvents(signal?: AbortSignal): EventSource {
    return this.transport.eventSource(
        {
          method: 'GET',
          pathTemplate: '/tests',
          security: this.clientSettings?.bindings['fetchEvents'] ?? [],
          acceptTypes: [MediaType.EventStream],
          signal: signal,
        }
    );
  }

}

export function createAPI<Factory extends SundayTransport>(transport: Factory,
    options: { defaultContentTypes?: Array<MediaType>, defaultAcceptTypes?: Array<MediaType> } | undefined = undefined): API<Factory> {
  return new APIClient(transport, options);
}

type SundayTransport = Transport<unknown>;
