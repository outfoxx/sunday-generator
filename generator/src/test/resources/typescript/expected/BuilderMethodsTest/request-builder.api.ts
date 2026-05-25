import {MediaType, Transport, TransportRequest} from '@outfoxx/sunday';


export interface API<Factory extends SundayTransport> {

  fetchTest(signal?: AbortSignal): Promise<TransportRequest<Factory>>;

}

class APIClient<Factory extends SundayTransport> {

  defaultContentTypes: Array<MediaType>;

  defaultAcceptTypes: Array<MediaType>;

  constructor(public transport: Factory,
      options: { defaultContentTypes?: Array<MediaType>, defaultAcceptTypes?: Array<MediaType> } | undefined = undefined) {
    this.defaultContentTypes =
        options?.defaultContentTypes ?? [];
    this.defaultAcceptTypes =
        options?.defaultAcceptTypes ?? [MediaType.JSON];
  }

  fetchTest(signal?: AbortSignal): Promise<TransportRequest<Factory>> {
    return this.transport.transportRequest(
        {
          method: 'GET',
          pathTemplate: '/test/request',
          acceptTypes: this.defaultAcceptTypes,
          signal: signal,
        }
    ) as Promise<TransportRequest<Factory>>;
  }

}

export function createAPI<Factory extends SundayTransport>(transport: Factory,
    options: { defaultContentTypes?: Array<MediaType>, defaultAcceptTypes?: Array<MediaType> } | undefined = undefined): API<Factory> {
  return new APIClient(transport, options);
}

type SundayTransport = Transport<unknown>;
