import {Test, TestSchema} from './test';
import {ArrayBufferEncoding, ClientSettings, DateEncoding, MediaType, NumericDateDecoding, Operation, SchemaLike, Transport, createOperation, createSchemaRuntime} from '@outfoxx/sunday';
import {z} from 'zod';


export interface API<Factory extends SundayTransport> {

  fetchTest(
      obj?: Test | undefined,
      str?: string | undefined,
      int?: number | null,
      def1?: string | null | undefined,
      def2?: number | null | undefined
  ): Operation<void, Test, Factory>;

}

class APIClient<Factory extends SundayTransport> {

  defaultContentTypes: Array<MediaType>;

  defaultAcceptTypes: Array<MediaType>;

  constructor(public transport: Factory,
      options: { defaultContentTypes?: Array<MediaType>, defaultAcceptTypes?: Array<MediaType> } | undefined = undefined,
      private clientSettings: ClientSettings | undefined = undefined) {
    this.defaultContentTypes =
        options?.defaultContentTypes ?? [];
    this.defaultAcceptTypes =
        options?.defaultAcceptTypes ?? [MediaType.JSON];
  }

  fetchTest(
      obj: Test | undefined = undefined,
      str: string | undefined = undefined,
      int: number | null = null,
      def1: string | null | undefined = 'test',
      def2: number | null | undefined = 10
  ): Operation<void, Test, Factory> {
    return createOperation(this.transport, {
        request: {
          method: 'GET',
          pathTemplate: '/tests',
          security: this.clientSettings?.bindings['fetchTest'] ?? [],
          queryParameters: {
            obj: obj == null ? undefined : obj,
            str: str == null ? undefined : str,
            int: int == null ? undefined : int,
            def1: def1 == null ? undefined : def1,
            def2: def2 == null ? undefined : def2
          },
          acceptTypes: this.defaultAcceptTypes,
          parameterValidation: () => {
            const runtime = createSchemaRuntime({format: 'json', dateEncoding: DateEncoding.ISO8601, numericDateDecoding: NumericDateDecoding.MILLISECONDS_SINCE_EPOCH, arrayBufferEncoding: ArrayBufferEncoding.BASE64}, 'request');
            if (obj != null) {
              z.encode(runtime.resolveSchema(TestSchema).optional(), obj);
            }
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

type SundayTransport = Transport<unknown>;

const fetchTestReturnType: SchemaLike<Test> = TestSchema;
