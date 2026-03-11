import {MediaType, RequestFactory, SchemaLike, SchemaRuntime, defineSchema} from '@outfoxx/sunday';
import {z} from 'zod';


export class API {

  defaultContentTypes: Array<MediaType>;

  defaultAcceptTypes: Array<MediaType>;

  constructor(public requestFactory: RequestFactory,
      options: { defaultContentTypes?: Array<MediaType>, defaultAcceptTypes?: Array<MediaType> } | undefined = undefined) {
    this.defaultContentTypes =
        options?.defaultContentTypes ?? [MediaType.JSON];
    this.defaultAcceptTypes =
        options?.defaultAcceptTypes ?? [MediaType.JSON];
  }

  checkAliases(body: Set<string>, signal?: AbortSignal): Promise<Set<string>> {
    return this.requestFactory.result(
        {
          method: 'POST',
          pathTemplate: '/aliases',
          body: body,
          bodyType: checkAliasesBodyType,
          contentTypes: this.defaultContentTypes,
          acceptTypes: this.defaultAcceptTypes,
          signal: signal,
        },
        checkAliasesReturnType
    );
  }

}

const checkAliasesBodyType: SchemaLike<Set<string>> = defineSchema((runtime: SchemaRuntime) => z.codec(z.array(z.string()), z.set(z.string()), {
      decode: (value) => new Set(value),
      encode: (value) => Array.from(value),
    }));
const checkAliasesReturnType: SchemaLike<Set<string>> = defineSchema((runtime: SchemaRuntime) => z.codec(z.array(z.string()), z.set(z.string()), {
      decode: (value) => new Set(value),
      encode: (value) => Array.from(value),
    }));
