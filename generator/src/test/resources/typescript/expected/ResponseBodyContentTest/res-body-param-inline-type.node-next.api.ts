import {MediaType, Operation, SchemaLike, SchemaOutput, SchemaRuntime, Transport, createOperation, defineSchema} from '@outfoxx/sunday';
import {z} from 'zod';


export interface API<Factory extends SundayTransport> {

  fetchTest(): Operation<void, API.FetchTestResponseBody, Factory>;

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

  fetchTest(): Operation<void, API.FetchTestResponseBody, Factory> {
    return createOperation(this.transport, {
        request: {
          method: 'GET',
          pathTemplate: '/tests',
          acceptTypes: this.defaultAcceptTypes,
        },
        responseType: fetchTestReturnType
    });
  }

}

export function createAPI<Factory extends SundayTransport>(transport: Factory,
    options: { defaultContentTypes?: Array<MediaType>, defaultAcceptTypes?: Array<MediaType> } | undefined = undefined): API<Factory> {
  return new APIClient(transport, options);
}

export namespace API {

  export type FetchTestResponseBody = SchemaOutput<typeof FetchTestResponseBodySchema>;

  export const FetchTestResponseBodySchema = defineSchema((runtime: SchemaRuntime) => {
    const wireSchema = z.looseObject({
      'value': z.string()
    });
    return wireSchema;
  });

}

type SundayTransport = Transport<unknown>;

const fetchTestReturnType: SchemaLike<API.FetchTestResponseBody> = API.FetchTestResponseBodySchema;
