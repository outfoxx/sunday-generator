import {MediaType, Operation, SchemaLike, Transport, createOperation} from '@outfoxx/sunday';
import {z} from 'zod';


export interface API<Factory extends SundayTransport> {

  fetchTest(category: API.FetchTestCategoryHeaderParam,
      type: API.FetchTestTypeHeaderParam): Operation<void, Record<string, unknown>, Factory>;

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

  fetchTest(category: API.FetchTestCategoryHeaderParam,
      type: API.FetchTestTypeHeaderParam): Operation<void, Record<string, unknown>, Factory> {
    return createOperation(this.transport, {
        request: {
          method: 'GET',
          pathTemplate: '/tests',
          acceptTypes: this.defaultAcceptTypes,
          headers: {
            category,
            type
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
