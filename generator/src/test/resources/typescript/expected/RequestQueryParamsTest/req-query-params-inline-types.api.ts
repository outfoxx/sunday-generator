import {ArrayBufferEncoding, DateEncoding, MediaType, NumericDateDecoding, Operation, SchemaLike, Transport, createOperation, createSchemaRuntime} from '@outfoxx/sunday';
import {z} from 'zod';


export interface API<Factory extends SundayTransport> {

  fetchTest(category: API.FetchTestCategoryQueryParam,
      type: API.FetchTestTypeQueryParam): Operation<void, Record<string, unknown>, Factory>;

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

  fetchTest(category: API.FetchTestCategoryQueryParam,
      type: API.FetchTestTypeQueryParam): Operation<void, Record<string, unknown>, Factory> {
    return createOperation(this.transport, {
        request: {
          method: 'GET',
          pathTemplate: '/tests',
          queryParameters: {
            category,
            type
          },
          acceptTypes: this.defaultAcceptTypes,
          parameterValidation: () => {
            const runtime = createSchemaRuntime({format: 'json', dateEncoding: DateEncoding.ISO8601, numericDateDecoding: NumericDateDecoding.MILLISECONDS_SINCE_EPOCH, arrayBufferEncoding: ArrayBufferEncoding.BASE64}, 'request');
            z.encode(runtime.resolveSchema(API.FetchTestCategoryQueryParamSchema), category);
            z.encode(runtime.resolveSchema(API.FetchTestTypeQueryParamSchema), type);
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

  export enum FetchTestCategoryQueryParam {
    Politics = 'politics',
    Science = 'science'
  }

  export const FetchTestCategoryQueryParamSchema = z.enum(FetchTestCategoryQueryParam);

  export enum FetchTestTypeQueryParam {
    All = 'all',
    Limited = 'limited'
  }

  export const FetchTestTypeQueryParamSchema = z.enum(FetchTestTypeQueryParam);

}

type SundayTransport = Transport<unknown>;

const fetchTestReturnType: SchemaLike<Record<string, unknown>> = z.record(z.string(), z.unknown());
