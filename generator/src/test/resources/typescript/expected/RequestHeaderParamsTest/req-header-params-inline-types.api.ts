import {ArrayBufferEncoding, ClientSettings, DateEncoding, MediaType, NumericDateDecoding, Operation, SchemaLike, Transport, createOperation, createSchemaRuntime} from '@outfoxx/sunday';
import {z} from 'zod';


export interface API<Factory extends SundayTransport> {

  fetchTest(category: API.FetchTestCategoryHeaderParam,
      type: API.FetchTestTypeHeaderParam): Operation<void, Record<string, unknown>, Factory>;

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

  fetchTest(category: API.FetchTestCategoryHeaderParam,
      type: API.FetchTestTypeHeaderParam): Operation<void, Record<string, unknown>, Factory> {
    return createOperation(this.transport, {
        request: {
          method: 'GET',
          pathTemplate: '/tests',
          security: this.clientSettings?.bindings['fetchTest'] ?? [],
          acceptTypes: this.defaultAcceptTypes,
          headers: {
            category,
            type
          },
          parameterValidation: () => {
            const runtime = createSchemaRuntime({format: 'json', dateEncoding: DateEncoding.ISO8601, numericDateDecoding: NumericDateDecoding.MILLISECONDS_SINCE_EPOCH, arrayBufferEncoding: ArrayBufferEncoding.BASE64}, 'request');
            z.encode(runtime.resolveSchema(API.FetchTestCategoryHeaderParamSchema), category);
            z.encode(runtime.resolveSchema(API.FetchTestTypeHeaderParamSchema), type);
          },
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

  export enum FetchTestCategoryHeaderParam {
    Politics = 'politics',
    Science = 'science'
  }

  export const FetchTestCategoryHeaderParamSchema = z.enum(FetchTestCategoryHeaderParam);

  export enum FetchTestTypeHeaderParam {
    All = 'all',
    Limited = 'limited'
  }

  export const FetchTestTypeHeaderParamSchema = z.enum(FetchTestTypeHeaderParam);

}

type SundayTransport = Transport<unknown>;

const fetchTestReturnType: SchemaLike<Record<string, unknown>> = z.record(z.string(), z.unknown());
