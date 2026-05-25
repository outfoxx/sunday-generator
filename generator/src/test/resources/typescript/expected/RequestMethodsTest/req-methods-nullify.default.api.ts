import {AnotherNotFoundProblem, AnotherNotFoundProblemSchema} from './another-not-found-problem';
import {Test, TestSchema} from './test';
import {TestNotFoundProblem, TestNotFoundProblemSchema} from './test-not-found-problem';
import {MediaType, NullableOperation, SchemaLike, Transport, createNullableOperation} from '@outfoxx/sunday';


export interface API<Factory extends SundayTransport> {

  fetchTest1(limit: number): NullableOperation<void, Test, Factory>;

  fetchTest2(limit: number): NullableOperation<void, Test, Factory>;

  fetchTest3(limit: number): NullableOperation<void, Test, Factory>;

  fetchTest4(limit: number): NullableOperation<void, Test, Factory>;

  fetchTest5(limit: number): NullableOperation<void, Test, Factory>;

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
    transport.registerProblem('http://example.com/test_not_found', TestNotFoundProblemSchema);
    transport.registerProblem('http://example.com/another_not_found', AnotherNotFoundProblemSchema);
  }

  fetchTest1(limit: number): NullableOperation<void, Test, Factory> {
    return createNullableOperation(this.transport, {
        request: {
          method: 'GET',
          pathTemplate: '/test1',
          queryParameters: {
            limit
          },
          acceptTypes: this.defaultAcceptTypes,
        },
        responseType: fetchTest1ReturnType
    }, {
      statuses: [404, 405],
      problemTypes: [(problem) => problem instanceof TestNotFoundProblem, (problem) => problem instanceof AnotherNotFoundProblem]
      });
  }

  fetchTest2(limit: number): NullableOperation<void, Test, Factory> {
    return createNullableOperation(this.transport, {
        request: {
          method: 'GET',
          pathTemplate: '/test2',
          queryParameters: {
            limit
          },
          acceptTypes: this.defaultAcceptTypes,
        },
        responseType: fetchTest2ReturnType
    }, {
      statuses: [404],
      problemTypes: [(problem) => problem instanceof TestNotFoundProblem, (problem) => problem instanceof AnotherNotFoundProblem]
      });
  }

  fetchTest3(limit: number): NullableOperation<void, Test, Factory> {
    return createNullableOperation(this.transport, {
        request: {
          method: 'GET',
          pathTemplate: '/test3',
          queryParameters: {
            limit
          },
          acceptTypes: this.defaultAcceptTypes,
        },
        responseType: fetchTest3ReturnType
    }, {
      statuses: [],
      problemTypes: [(problem) => problem instanceof TestNotFoundProblem, (problem) => problem instanceof AnotherNotFoundProblem]
      });
  }

  fetchTest4(limit: number): NullableOperation<void, Test, Factory> {
    return createNullableOperation(this.transport, {
        request: {
          method: 'GET',
          pathTemplate: '/test4',
          queryParameters: {
            limit
          },
          acceptTypes: this.defaultAcceptTypes,
        },
        responseType: fetchTest4ReturnType
    }, {
      statuses: [404, 405],
      problemTypes: []
      });
  }

  fetchTest5(limit: number): NullableOperation<void, Test, Factory> {
    return createNullableOperation(this.transport, {
        request: {
          method: 'GET',
          pathTemplate: '/test5',
          queryParameters: {
            limit
          },
          acceptTypes: this.defaultAcceptTypes,
        },
        responseType: fetchTest5ReturnType
    }, {
      statuses: [404],
      problemTypes: []
      });
  }

}

export function createAPI<Factory extends SundayTransport>(transport: Factory,
    options: { defaultContentTypes?: Array<MediaType>, defaultAcceptTypes?: Array<MediaType> } | undefined = undefined): API<Factory> {
  return new APIClient(transport, options);
}

type SundayTransport = Transport<unknown>;

const fetchTest1ReturnType: SchemaLike<Test> = TestSchema;
const fetchTest2ReturnType: SchemaLike<Test> = TestSchema;
const fetchTest3ReturnType: SchemaLike<Test> = TestSchema;
const fetchTest4ReturnType: SchemaLike<Test> = TestSchema;
const fetchTest5ReturnType: SchemaLike<Test> = TestSchema;
