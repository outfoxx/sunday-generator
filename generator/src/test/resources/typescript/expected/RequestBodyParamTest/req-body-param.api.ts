import {Test, TestSchema} from './test';
import {ClientSettings, MediaType, Operation, SchemaLike, Transport, createOperation} from '@outfoxx/sunday';


export interface API<Factory extends SundayTransport> {

  fetchTest(body: Test): Operation<Test, Test, Factory>;

}

class APIClient<Factory extends SundayTransport> {

  defaultContentTypes: Array<MediaType>;

  defaultAcceptTypes: Array<MediaType>;

  constructor(public transport: Factory,
      options: { defaultContentTypes?: Array<MediaType>, defaultAcceptTypes?: Array<MediaType> } | undefined = undefined,
      private clientSettings: ClientSettings | undefined = undefined) {
    this.defaultContentTypes =
        options?.defaultContentTypes ?? [MediaType.JSON];
    this.defaultAcceptTypes =
        options?.defaultAcceptTypes ?? [MediaType.JSON];
  }

  fetchTest(body: Test): Operation<Test, Test, Factory> {
    return createOperation(this.transport, {
        request: {
          method: 'GET',
          pathTemplate: '/tests',
          security: this.clientSettings?.bindings['fetchTest'] ?? [],
          body: body,
          bodyType: fetchTestBodyType,
          contentTypes: this.defaultContentTypes,
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

const fetchTestBodyType: SchemaLike<Test> = TestSchema;
const fetchTestReturnType: SchemaLike<Test> = TestSchema;
