import {MediaType, RequestFactory, SchemaLike} from '@outfoxx/sunday';
import {z} from 'zod';


export class API {

  defaultContentTypes: Array<MediaType>;

  defaultAcceptTypes: Array<MediaType>;

  constructor(public requestFactory: RequestFactory,
      options: { defaultContentTypes?: Array<MediaType>, defaultAcceptTypes?: Array<MediaType> } | undefined = undefined) {
    this.defaultContentTypes =
        options?.defaultContentTypes ?? [];
    this.defaultAcceptTypes =
        options?.defaultAcceptTypes ?? [MediaType.JSON];
  }

  fetchTest(type: API.FetchTestTypeUriParam, type_: API.FetchTestTypeQueryParam,
      type__: API.FetchTestTypeHeaderParam,
      signal?: AbortSignal): Promise<Record<string, unknown>> {
    return this.requestFactory.result(
        {
          method: 'GET',
          pathTemplate: '/tests/{type}',
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
          signal: signal,
        },
        fetchTestReturnType
    );
  }

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

const fetchTestReturnType: SchemaLike<Record<string, unknown>> = z.record(z.string(), z.unknown());
