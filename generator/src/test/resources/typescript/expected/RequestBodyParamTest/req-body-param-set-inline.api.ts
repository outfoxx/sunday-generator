import {MediaType, Operation, SchemaLike, SchemaRuntime, Transport, createOperation, defineSchema} from '@outfoxx/sunday';
import {z} from 'zod';


export interface API<Factory extends SundayTransport> {

  checkAliases(body: Set<string>): Operation<Set<string>, Set<string>, Factory>;

}

class APIClient<Factory extends SundayTransport> {

  defaultContentTypes: Array<MediaType>;

  defaultAcceptTypes: Array<MediaType>;

  constructor(public transport: Factory,
      options: { defaultContentTypes?: Array<MediaType>, defaultAcceptTypes?: Array<MediaType> } | undefined = undefined) {
    this.defaultContentTypes =
        options?.defaultContentTypes ?? [MediaType.JSON];
    this.defaultAcceptTypes =
        options?.defaultAcceptTypes ?? [MediaType.JSON];
  }

  checkAliases(body: Set<string>): Operation<Set<string>, Set<string>, Factory> {
    return createOperation(this.transport, {
        request: {
          method: 'POST',
          pathTemplate: '/aliases',
          body: body,
          bodyType: checkAliasesBodyType,
          contentTypes: this.defaultContentTypes,
          acceptTypes: this.defaultAcceptTypes,
        },
        responseType: checkAliasesReturnType
    });
  }

}

export function createAPI<Factory extends SundayTransport>(transport: Factory,
    options: { defaultContentTypes?: Array<MediaType>, defaultAcceptTypes?: Array<MediaType> } | undefined = undefined): API<Factory> {
  return new APIClient(transport, options);
}

type SundayTransport = Transport<unknown>;

const checkAliasesBodyType: SchemaLike<Set<string>> = defineSchema((runtime: SchemaRuntime) => z.codec(z.array(z.string()), z.set(z.string()), {
      decode: (value) => new Set(value),
      encode: (value) => Array.from(value),
    }));
const checkAliasesReturnType: SchemaLike<Set<string>> = defineSchema((runtime: SchemaRuntime) => z.codec(z.array(z.string()), z.set(z.string()), {
      decode: (value) => new Set(value),
      encode: (value) => Array.from(value),
    }));
