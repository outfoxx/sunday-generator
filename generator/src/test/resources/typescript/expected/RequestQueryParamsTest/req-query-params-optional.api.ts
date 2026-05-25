import {Test, TestSchema} from './test';
import {MediaType, Operation, SchemaLike, Transport, createOperation} from '@outfoxx/sunday';


export interface API<Factory extends SundayTransport> {

  fetchTest(
      obj: Test | undefined,
      str: string | undefined,
      int: number | null,
      def1: string | undefined,
      def2: number | null | undefined
  ): Operation<void, Test, Factory>;

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

  fetchTest(
      obj: Test | undefined = undefined,
      str: string | undefined = undefined,
      int: number | null = null,
      def1: string | undefined = undefined,
      def2: number | null | undefined = undefined
  ): Operation<void, Test, Factory> {
    return createOperation(this.transport, {
        request: {
          method: 'GET',
          pathTemplate: '/tests',
          queryParameters: {
            obj,
            str,
            int,
            def1: def1 ?? 'test',
            def2: def2 ?? 10
          },
          acceptTypes: this.defaultAcceptTypes,
        },
        responseType: fetchTestReturnType
    });
  }

}

export function createAPI<Factory extends SundayTransport>(transport: Factory,
    options: { defaultContentTypes?: Array<MediaType>, defaultAcceptTypes?: Array<MediaType> } | undefined = undefined): API<Factory> {
  return new APIClient(transport, options);
}

type SundayTransport = Transport<unknown>;

const fetchTestReturnType: SchemaLike<Test> = TestSchema;
