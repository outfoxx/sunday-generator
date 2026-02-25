import {AnotherNotFoundProblem, AnotherNotFoundProblemSchema} from './another-not-found-problem';
import {Test, TestSchema} from './test';
import {TestNotFoundProblem, TestNotFoundProblemSchema} from './test-not-found-problem';
import {MediaType, RequestFactory, SchemaLike, nullifyProblem} from '@outfoxx/sunday';


export class API {

  defaultContentTypes: Array<MediaType>;

  defaultAcceptTypes: Array<MediaType>;

  constructor(public requestFactory: RequestFactory,
      options: { defaultContentTypes?: Array<MediaType>, defaultAcceptTypes?: Array<MediaType> } | undefined = undefined) {
    this.defaultContentTypes =
        options?.defaultContentTypes ?? [];
    this.defaultAcceptTypes =
        options?.defaultAcceptTypes ?? [MediaType.JSON];
    requestFactory.registerProblem('http://example.com/test_not_found', TestNotFoundProblemSchema);
    requestFactory.registerProblem('http://example.com/another_not_found', AnotherNotFoundProblemSchema);
  }

  fetchTest1OrNull(limit: number, signal?: AbortSignal): Promise<Test | null> {
    return nullifyProblem(
      this.fetchTest1(limit, signal),
      [404, 405],
      [(problem) => problem instanceof TestNotFoundProblem, (problem) => problem instanceof AnotherNotFoundProblem]
    );
  }

  fetchTest1(limit: number, signal?: AbortSignal): Promise<Test> {
    return this.requestFactory.result(
        {
          method: 'GET',
          pathTemplate: '/test1',
          queryParameters: {
            limit
          },
          acceptTypes: this.defaultAcceptTypes,
          signal: signal,
        },
        fetchTest1ReturnType
    );
  }

  fetchTest2OrNull(limit: number, signal?: AbortSignal): Promise<Test | null> {
    return nullifyProblem(
      this.fetchTest2(limit, signal),
      [404],
      [(problem) => problem instanceof TestNotFoundProblem, (problem) => problem instanceof AnotherNotFoundProblem]
    );
  }

  fetchTest2(limit: number, signal?: AbortSignal): Promise<Test> {
    return this.requestFactory.result(
        {
          method: 'GET',
          pathTemplate: '/test2',
          queryParameters: {
            limit
          },
          acceptTypes: this.defaultAcceptTypes,
          signal: signal,
        },
        fetchTest2ReturnType
    );
  }

  fetchTest3OrNull(limit: number, signal?: AbortSignal): Promise<Test | null> {
    return nullifyProblem(
      this.fetchTest3(limit, signal),
      [],
      [(problem) => problem instanceof TestNotFoundProblem, (problem) => problem instanceof AnotherNotFoundProblem]
    );
  }

  fetchTest3(limit: number, signal?: AbortSignal): Promise<Test> {
    return this.requestFactory.result(
        {
          method: 'GET',
          pathTemplate: '/test3',
          queryParameters: {
            limit
          },
          acceptTypes: this.defaultAcceptTypes,
          signal: signal,
        },
        fetchTest3ReturnType
    );
  }

  fetchTest4OrNull(limit: number, signal?: AbortSignal): Promise<Test | null> {
    return nullifyProblem(
      this.fetchTest4(limit, signal),
      [404, 405],
      []
    );
  }

  fetchTest4(limit: number, signal?: AbortSignal): Promise<Test> {
    return this.requestFactory.result(
        {
          method: 'GET',
          pathTemplate: '/test4',
          queryParameters: {
            limit
          },
          acceptTypes: this.defaultAcceptTypes,
          signal: signal,
        },
        fetchTest4ReturnType
    );
  }

  fetchTest5OrNull(limit: number, signal?: AbortSignal): Promise<Test | null> {
    return nullifyProblem(
      this.fetchTest5(limit, signal),
      [404],
      []
    );
  }

  fetchTest5(limit: number, signal?: AbortSignal): Promise<Test> {
    return this.requestFactory.result(
        {
          method: 'GET',
          pathTemplate: '/test5',
          queryParameters: {
            limit
          },
          acceptTypes: this.defaultAcceptTypes,
          signal: signal,
        },
        fetchTest5ReturnType
    );
  }

}

const fetchTest1ReturnType: SchemaLike<Test> = TestSchema;
const fetchTest2ReturnType: SchemaLike<Test> = TestSchema;
const fetchTest3ReturnType: SchemaLike<Test> = TestSchema;
const fetchTest4ReturnType: SchemaLike<Test> = TestSchema;
const fetchTest5ReturnType: SchemaLike<Test> = TestSchema;
