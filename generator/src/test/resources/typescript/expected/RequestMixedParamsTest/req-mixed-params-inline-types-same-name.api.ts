import {ArrayBufferEncoding, ClientSettings, DateEncoding, MediaType, NumericDateDecoding, Operation, SchemaLike, Transport, createOperation, createSchemaRuntime} from '@outfoxx/sunday';
import {z} from 'zod';


export interface API<Factory extends SundayTransport> {

  fetchTest(type: API.FetchTestTypeUriParam, type_: API.FetchTestTypeQueryParam,
      type__: API.FetchTestTypeHeaderParam): Operation<void, Record<string, unknown>, Factory>;

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

  fetchTest(type: API.FetchTestTypeUriParam, type_: API.FetchTestTypeQueryParam,
      type__: API.FetchTestTypeHeaderParam): Operation<void, Record<string, unknown>, Factory> {
    return createOperation(this.transport, {
        request: {
          method: 'GET',
          pathTemplate: '/tests/{type}',
          security: this.clientSettings?.bindings['fetchTest'] ?? [],
          pathParameters: {
            type
          },
          queryParameters: {
            type: type_
          },
          acceptTypes: this.defaultAcceptTypes,
          headers: {
            type: type__
          },
          parameterValidation: () => {
            const runtime = createSchemaRuntime({format: 'json', dateEncoding: DateEncoding.ISO8601, numericDateDecoding: NumericDateDecoding.MILLISECONDS_SINCE_EPOCH, arrayBufferEncoding: ArrayBufferEncoding.BASE64}, 'request');
            z.encode(runtime.resolveSchema(API.FetchTestTypeUriParamSchema), type);
            z.encode(runtime.resolveSchema(API.FetchTestTypeQueryParamSchema), type_);
            z.encode(runtime.resolveSchema(API.FetchTestTypeHeaderParamSchema), type__);
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

  export enum FetchTestTypeUriParam {
    All = 'all',
    Limited = 'limited'
  }

  export const FetchTestTypeUriParamSchema = z.enum(FetchTestTypeUriParam);

  export enum FetchTestTypeQueryParam {
    All = 'all',
    Limited = 'limited'
  }

  export const FetchTestTypeQueryParamSchema = z.enum(FetchTestTypeQueryParam);

  export enum FetchTestTypeHeaderParam {
    All = 'all',
    Limited = 'limited'
  }

  export const FetchTestTypeHeaderParamSchema = z.enum(FetchTestTypeHeaderParam);

}

type SundayTransport = Transport<unknown>;

const fetchTestReturnType: SchemaLike<Record<string, unknown>> = z.record(z.string(), z.unknown());
