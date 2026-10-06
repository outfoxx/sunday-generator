import {ArrayBufferEncoding, ClientSettings, DateEncoding, MediaType, NumericDateDecoding, Operation, SchemaLike, Transport, createOperation, createSchemaRuntime} from '@outfoxx/sunday';
import {z} from 'zod';


export interface API<Factory extends SundayTransport> {

  fetchTest(select: API.FetchTestSelectUriParam, page: API.FetchTestPageQueryParam,
      xType: API.FetchTestXTypeHeaderParam): Operation<void, Record<string, unknown>, Factory>;

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

  fetchTest(select: API.FetchTestSelectUriParam, page: API.FetchTestPageQueryParam,
      xType: API.FetchTestXTypeHeaderParam): Operation<void, Record<string, unknown>, Factory> {
    return createOperation(this.transport, {
        request: {
          method: 'GET',
          pathTemplate: '/tests/{select}',
          security: this.clientSettings?.bindings['fetchTest'] ?? [],
          pathParameters: {
            select
          },
          queryParameters: {
            page
          },
          acceptTypes: this.defaultAcceptTypes,
          headers: {
            'x-type': xType
          },
          parameterValidation: () => {
            const runtime = createSchemaRuntime({format: 'json', dateEncoding: DateEncoding.ISO8601, numericDateDecoding: NumericDateDecoding.MILLISECONDS_SINCE_EPOCH, arrayBufferEncoding: ArrayBufferEncoding.BASE64}, 'request');
            z.encode(runtime.resolveSchema(API.FetchTestSelectUriParamSchema), select);
            z.encode(runtime.resolveSchema(API.FetchTestPageQueryParamSchema), page);
            z.encode(runtime.resolveSchema(API.FetchTestXTypeHeaderParamSchema), xType);
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

  export enum FetchTestSelectUriParam {
    All = 'all',
    Limited = 'limited'
  }

  export const FetchTestSelectUriParamSchema = z.enum(FetchTestSelectUriParam);

  export enum FetchTestPageQueryParam {
    All = 'all',
    Limited = 'limited'
  }

  export const FetchTestPageQueryParamSchema = z.enum(FetchTestPageQueryParam);

  export enum FetchTestXTypeHeaderParam {
    All = 'all',
    Limited = 'limited'
  }

  export const FetchTestXTypeHeaderParamSchema = z.enum(FetchTestXTypeHeaderParam);

}

type SundayTransport = Transport<unknown>;

const fetchTestReturnType: SchemaLike<Record<string, unknown>> = z.record(z.string(), z.unknown());
