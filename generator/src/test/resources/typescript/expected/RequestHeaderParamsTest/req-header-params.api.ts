import {Test, TestSchema} from './test';
import {MediaType, RequestFactory, SchemaLike} from '@outfoxx/sunday';


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

  fetchTest(obj: Test, strReq: string, int: number | undefined = undefined,
      signal?: AbortSignal): Promise<Test> {
    return this.requestFactory.result(
        {
          method: 'GET',
          pathTemplate: '/tests',
          acceptTypes: this.defaultAcceptTypes,
          headers: {
            obj,
            'str-req': strReq,
            int: int ?? 5
          },
          signal: signal,
        },
        fetchTestReturnType
    );
  }

  deleteTest(signal?: AbortSignal): Promise<void> {
    return this.requestFactory.result(
        {
          method: 'DELETE',
          pathTemplate: '/tests',
          signal: signal,
        }
    );
  }

}

const fetchTestReturnType: SchemaLike<Test> = TestSchema;
