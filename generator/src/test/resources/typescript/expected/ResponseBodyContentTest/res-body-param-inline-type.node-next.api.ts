import {ClientSettings, MediaType, Operation, SchemaLike, SchemaOutput, SchemaRuntime, Transport, createOperation, defineModelSchema} from '@outfoxx/sunday';
import {z} from 'zod';


export interface API<Factory extends SundayTransport> {

  fetchTest(): Operation<void, API.FetchTestResponseBody, Factory>;

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
        options?.defaultAcceptTypes ?? [MediaType.JSON];
  }

  fetchTest(): Operation<void, API.FetchTestResponseBody, Factory> {
    return createOperation(this.transport, {
        request: {
          method: 'GET',
          pathTemplate: '/tests',
          security: this.clientSettings?.bindings['fetchTest'] ?? [],
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

  export const FetchTestResponseBodySchema = defineModelSchema((runtime: SchemaRuntime) => {
    const wireSchema = z.looseObject({
      'value': z.string()
    });
    const preservedSchema = z.codec(z.custom<z.input<typeof wireSchema>>(), z.custom<z.output<typeof wireSchema>>(), {
      decode: (value, context) => {
        const result = wireSchema.safeParse(value);
        if (!result.success) { context.issues.push(...result.error.issues.map(issue => ({...issue, input: undefined}))); return z.NEVER; }
        const entries: [string, unknown][] = Object.entries(result.data);
        if (typeof value === 'object' && value !== null && Object.hasOwn(value, '__proto__')) {
          const item = (value as {[key: string]: unknown})['__proto__'];
          entries.push(['__proto__', item]);
        }
        return Object.fromEntries(entries) as z.output<typeof wireSchema>;
      },
      encode: (value, context) => {
        const result = wireSchema.safeEncode(value);
        if (!result.success) { context.issues.push(...result.error.issues.map(issue => ({...issue, input: undefined}))); return z.NEVER; }
        const entries: [string, unknown][] = Object.entries(result.data);
        if (typeof value === 'object' && value !== null && Object.hasOwn(value, '__proto__')) {
          const item = (value as {[key: string]: unknown})['__proto__'];
          entries.push(['__proto__', item]);
        }
        return Object.fromEntries(entries) as z.input<typeof wireSchema>;
      },
    });
    return preservedSchema;
  });

}

type SundayTransport = Transport<unknown>;

const fetchTestReturnType: SchemaLike<API.FetchTestResponseBody> = API.FetchTestResponseBodySchema;
