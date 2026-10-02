import {ArrayBufferEncoding, DateEncoding, MediaType, NumericDateDecoding, Operation, SchemaLike, Transport, createOperation, createSchemaRuntime} from '@outfoxx/sunday';
import {z} from 'zod';


export interface API<Factory extends SundayTransport> {

  fetchTest(category: API.FetchTestCategoryUriParam,
      type: API.FetchTestTypeUriParam): Operation<void, Record<string, unknown>, Factory>;

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

  fetchTest(category: API.FetchTestCategoryUriParam,
      type: API.FetchTestTypeUriParam): Operation<void, Record<string, unknown>, Factory> {
    return createOperation(this.transport, {
        request: {
          method: 'GET',
          pathTemplate: '/tests/{category}/{type}',
          pathParameters: {
            category,
            type
          },
          acceptTypes: this.defaultAcceptTypes,
          parameterValidation: () => {
            const runtime = createSchemaRuntime({format: 'json', dateEncoding: DateEncoding.ISO8601, numericDateDecoding: NumericDateDecoding.MILLISECONDS_SINCE_EPOCH, arrayBufferEncoding: ArrayBufferEncoding.BASE64}, 'request');
            z.encode(runtime.resolveSchema(API.FetchTestCategoryUriParamSchema), category);
            z.encode(runtime.resolveSchema(API.FetchTestTypeUriParamSchema), type);
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

  export enum FetchTestCategoryUriParam {
    Politics = 'politics',
    Science = 'science'
  }

  export const FetchTestCategoryUriParamSchema = z.enum(FetchTestCategoryUriParam);

  export enum FetchTestTypeUriParam {
    All = 'all',
    Limited = 'limited'
  }

  export const FetchTestTypeUriParamSchema = z.enum(FetchTestTypeUriParam);

}

type SundayTransport = Transport<unknown>;

const fetchTestReturnType: SchemaLike<Record<string, unknown>> = z.record(z.string(), z.unknown());
