import {ArrayBufferSchema, ClientSettings, MediaType, Operation, SchemaLike, Transport, createOperation} from '@outfoxx/sunday';


export interface API<Factory extends SundayTransport> {

  fetchTest(): Operation<void, ArrayBuffer, Factory>;

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

  fetchTest(): Operation<void, ArrayBuffer, Factory> {
    return createOperation(this.transport, {
        request: {
          method: 'GET',
          pathTemplate: '/tests',
          security: this.clientSettings?.bindings['fetchTest'] ?? [],
          acceptTypes: [MediaType.OctetStream],
        },
        responseType: fetchTestReturnType
    });
  }

}

export function createAPI<Factory extends SundayTransport>(transport: Factory,
    options: { defaultContentTypes?: Array<MediaType>, defaultAcceptTypes?: Array<MediaType> } | undefined = undefined): API<Factory> {
  return new APIClient(transport, options);
}

type SundayTransport = Transport<unknown>;

const fetchTestReturnType: SchemaLike<ArrayBuffer> = ArrayBufferSchema;
