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

  fetchTest(
      obj: Record<string, unknown>,
      str: string,
      def: string,
      int: number,
      signal?: AbortSignal
  ): Promise<Record<string, unknown>> {
    return this.requestFactory.result(
        {
          method: 'GET',
          pathTemplate: '/tests/{obj}/{str}/{int}/{def}',
          pathParameters: {
            obj,
            str,
            def,
            int
          },
          acceptTypes: this.defaultAcceptTypes,
          signal: signal,
        },
        fetchTestReturnType
    );
  }

}

const fetchTestReturnType: SchemaLike<Record<string, unknown>> = z.record(z.string(), z.unknown());
