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

  fetchTest(
      def2: number | null | undefined = undefined,
      obj: Test | undefined = undefined,
      str: string | undefined = undefined,
      def1: string | undefined = undefined,
      int: number | null = null,
      def: string,
      signal?: AbortSignal
  ): Promise<Test> {
    return this.requestFactory.result(
        {
          method: 'GET',
          pathTemplate: '/tests/{obj}/{str}/{int}/{def}/{def1}/{def2}',
          pathParameters: {
            def2: def2 ?? 10,
            obj,
            str,
            def1: def1 ?? 'test',
            int,
            def
          },
          acceptTypes: this.defaultAcceptTypes,
          signal: signal,
        },
        fetchTestReturnType
    );
  }

}

const fetchTestReturnType: SchemaLike<Test> = TestSchema;
