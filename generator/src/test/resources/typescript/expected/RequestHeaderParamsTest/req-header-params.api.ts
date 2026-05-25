import {Test, TestSchema} from './test';
import {MediaType, Operation, SchemaLike, Transport, createOperation} from '@outfoxx/sunday';


export interface API<Factory extends SundayTransport> {

  fetchTest(obj: Test, strReq: string, int: number | undefined): Operation<void, Test, Factory>;

  deleteTest(): Operation<void, void, Factory>;

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

  fetchTest(obj: Test, strReq: string,
      int: number | undefined = undefined): Operation<void, Test, Factory> {
    return createOperation(this.transport, {
        request: {
          method: 'GET',
          pathTemplate: '/tests',
          acceptTypes: this.defaultAcceptTypes,
          headers: {
            obj,
            'str-req': strReq,
            int: int ?? 5
          },
        },
        responseType: fetchTestReturnType
    });
  }

  deleteTest(): Operation<void, void, Factory> {
    return createOperation(this.transport, {
        request: {
          method: 'DELETE',
          pathTemplate: '/tests',
        }
    });
  }

}

export function createAPI<Factory extends SundayTransport>(transport: Factory,
    options: { defaultContentTypes?: Array<MediaType>, defaultAcceptTypes?: Array<MediaType> } | undefined = undefined): API<Factory> {
  return new APIClient(transport, options);
}

type SundayTransport = Transport<unknown>;

const fetchTestReturnType: SchemaLike<Test> = TestSchema;
