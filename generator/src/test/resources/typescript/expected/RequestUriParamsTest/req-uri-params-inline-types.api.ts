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

  fetchTest(category: API.FetchTestCategoryUriParam, type: API.FetchTestTypeUriParam,
      signal?: AbortSignal): Promise<Record<string, unknown>> {
    return this.requestFactory.result(
        {
          method: 'GET',
          pathTemplate: '/tests/{category}/{type}',
          pathParameters: {
            category,
            type
          },
          acceptTypes: this.defaultAcceptTypes,
          signal: signal,
        },
        fetchTestReturnType
    );
  }

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

const fetchTestReturnType: SchemaLike<Record<string, unknown>> = z.record(z.string(), z.unknown());
