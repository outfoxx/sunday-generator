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

  fetchTest(select: API.FetchTestSelectUriParam, page: API.FetchTestPageQueryParam,
      xType: API.FetchTestXTypeHeaderParam,
      signal?: AbortSignal): Promise<Record<string, unknown>> {
    return this.requestFactory.result(
        {
          method: 'GET',
          pathTemplate: '/tests/{select}',
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
          signal: signal,
        },
        fetchTestReturnType
    );
  }

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

const fetchTestReturnType: SchemaLike<Record<string, unknown>> = z.record(z.string(), z.unknown());
