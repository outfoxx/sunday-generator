import {PatchableTest, PatchableTestSchema} from './patchable-test';
import {Test, TestSchema} from './test';
import {MediaType, Operation, SchemaLike, Transport, TransportRequest, createOperation} from '@outfoxx/sunday';


export interface API<Factory extends SundayTransport> {

  fetchTest(): Operation<void, Test, Factory>;

  putTest(body: Test): Operation<Test, Test, Factory>;

  postTest(body: Test): Operation<Test, Test, Factory>;

  patchTest(body: Test): Operation<Test, Test, Factory>;

  deleteTest(): Operation<void, void, Factory>;

  headTest(): Operation<void, void, Factory>;

  optionsTest(): Operation<void, void, Factory>;

  patchableTest(body: PatchableTest): Operation<PatchableTest, Test, Factory>;

  requestTest(signal?: AbortSignal): Promise<TransportRequest<Factory>>;

  responseTest(signal?: AbortSignal): Promise<Response>;

}

class APIClient<Factory extends SundayTransport> {

  defaultContentTypes: Array<MediaType>;

  defaultAcceptTypes: Array<MediaType>;

  constructor(public transport: Factory,
      options: { defaultContentTypes?: Array<MediaType>, defaultAcceptTypes?: Array<MediaType> } | undefined = undefined) {
    this.defaultContentTypes =
        options?.defaultContentTypes ?? [MediaType.JSON];
    this.defaultAcceptTypes =
        options?.defaultAcceptTypes ?? [MediaType.JSON];
  }

  fetchTest(): Operation<void, Test, Factory> {
    return createOperation(this.transport, {
        request: {
          method: 'GET',
          pathTemplate: '/tests',
          acceptTypes: this.defaultAcceptTypes,
        },
        responseType: fetchTestReturnType
    });
  }

  putTest(body: Test): Operation<Test, Test, Factory> {
    return createOperation(this.transport, {
        request: {
          method: 'PUT',
          pathTemplate: '/tests',
          body: body,
          bodyType: putTestBodyType,
          contentTypes: this.defaultContentTypes,
          acceptTypes: this.defaultAcceptTypes,
        },
        responseType: putTestReturnType
    });
  }

  postTest(body: Test): Operation<Test, Test, Factory> {
    return createOperation(this.transport, {
        request: {
          method: 'POST',
          pathTemplate: '/tests',
          body: body,
          bodyType: postTestBodyType,
          contentTypes: this.defaultContentTypes,
          acceptTypes: this.defaultAcceptTypes,
        },
        responseType: postTestReturnType
    });
  }

  patchTest(body: Test): Operation<Test, Test, Factory> {
    return createOperation(this.transport, {
        request: {
          method: 'PATCH',
          pathTemplate: '/tests',
          body: body,
          bodyType: patchTestBodyType,
          contentTypes: this.defaultContentTypes,
          acceptTypes: this.defaultAcceptTypes,
        },
        responseType: patchTestReturnType
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

  headTest(): Operation<void, void, Factory> {
    return createOperation(this.transport, {
        request: {
          method: 'HEAD',
          pathTemplate: '/tests',
        }
    });
  }

  optionsTest(): Operation<void, void, Factory> {
    return createOperation(this.transport, {
        request: {
          method: 'OPTIONS',
          pathTemplate: '/tests',
        }
    });
  }

  patchableTest(body: PatchableTest): Operation<PatchableTest, Test, Factory> {
    return createOperation(this.transport, {
        request: {
          method: 'PATCH',
          pathTemplate: '/tests2',
          body: body,
          bodyType: patchableTestBodyType,
          contentTypes: this.defaultContentTypes,
          acceptTypes: this.defaultAcceptTypes,
        },
        responseType: patchableTestReturnType
    });
  }

  requestTest(signal?: AbortSignal): Promise<TransportRequest<Factory>> {
    return this.transport.transportRequest(
        {
          method: 'GET',
          pathTemplate: '/request',
          acceptTypes: this.defaultAcceptTypes,
          signal: signal,
        }
    ) as Promise<TransportRequest<Factory>>;
  }

  responseTest(signal?: AbortSignal): Promise<Response> {
    return this.transport.transportResponse(
        {
          method: 'GET',
          pathTemplate: '/response',
          acceptTypes: this.defaultAcceptTypes,
          signal: signal,
        }
    );
  }

}

export function createAPI<Factory extends SundayTransport>(transport: Factory,
    options: { defaultContentTypes?: Array<MediaType>, defaultAcceptTypes?: Array<MediaType> } | undefined = undefined): API<Factory> {
  return new APIClient(transport, options);
}

type SundayTransport = Transport<unknown>;

const fetchTestReturnType: SchemaLike<Test> = TestSchema;
const putTestBodyType: SchemaLike<Test> = TestSchema;
const putTestReturnType: SchemaLike<Test> = TestSchema;
const postTestBodyType: SchemaLike<Test> = TestSchema;
const postTestReturnType: SchemaLike<Test> = TestSchema;
const patchTestBodyType: SchemaLike<Test> = TestSchema;
const patchTestReturnType: SchemaLike<Test> = TestSchema;
const patchableTestBodyType: SchemaLike<PatchableTest> = PatchableTestSchema;
const patchableTestReturnType: SchemaLike<Test> = TestSchema;
