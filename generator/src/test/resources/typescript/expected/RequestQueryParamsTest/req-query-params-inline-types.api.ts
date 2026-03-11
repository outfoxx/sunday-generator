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

  fetchTest(category: API.FetchTestCategoryQueryParam, type: API.FetchTestTypeQueryParam,
      signal?: AbortSignal): Promise<Record<string, unknown>> {
    return this.requestFactory.result(
        {
          method: 'GET',
          pathTemplate: '/tests',
          queryParameters: {
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

const fetchTestReturnType: SchemaLike<Record<string, unknown>> = z.record(z.string(), z.unknown());
