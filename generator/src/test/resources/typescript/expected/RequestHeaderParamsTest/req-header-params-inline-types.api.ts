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

  fetchTest(category: API.FetchTestCategoryHeaderParam, type: API.FetchTestTypeHeaderParam,
      signal?: AbortSignal): Promise<Record<string, unknown>> {
    return this.requestFactory.result(
        {
          method: 'GET',
          pathTemplate: '/tests',
          acceptTypes: this.defaultAcceptTypes,
          headers: {
            category,
            type
          },
          signal: signal,
        },
        fetchTestReturnType
    );
  }

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

const fetchTestReturnType: SchemaLike<Record<string, unknown>> = z.record(z.string(), z.unknown());
