import {PatchableTest, PatchableTestSchema} from './patchable-test';
import {Test, TestSchema} from './test';
import {MediaType, RequestFactory, ResultResponse, SchemaLike} from '@outfoxx/sunday';


export class API {

  defaultContentTypes: Array<MediaType>;

  defaultAcceptTypes: Array<MediaType>;

  constructor(public requestFactory: RequestFactory,
      options: { defaultContentTypes?: Array<MediaType>, defaultAcceptTypes?: Array<MediaType> } | undefined = undefined) {
    this.defaultContentTypes =
        options?.defaultContentTypes ?? [MediaType.JSON];
    this.defaultAcceptTypes =
        options?.defaultAcceptTypes ?? [MediaType.JSON];
  }

  fetchTest(signal?: AbortSignal): Promise<ResultResponse<Test>> {
    return this.requestFactory.resultResponse(
        {
          method: 'GET',
          pathTemplate: '/tests',
          acceptTypes: this.defaultAcceptTypes,
          signal: signal,
        },
        fetchTestReturnType
    );
  }

  putTest(body: Test, signal?: AbortSignal): Promise<ResultResponse<Test>> {
    return this.requestFactory.resultResponse(
        {
          method: 'PUT',
          pathTemplate: '/tests',
          body: body,
          bodyType: putTestBodyType,
          contentTypes: this.defaultContentTypes,
          acceptTypes: this.defaultAcceptTypes,
          signal: signal,
        },
        putTestReturnType
    );
  }

  postTest(body: Test, signal?: AbortSignal): Promise<ResultResponse<Test>> {
    return this.requestFactory.resultResponse(
        {
          method: 'POST',
          pathTemplate: '/tests',
          body: body,
          bodyType: postTestBodyType,
          contentTypes: this.defaultContentTypes,
          acceptTypes: this.defaultAcceptTypes,
          signal: signal,
        },
        postTestReturnType
    );
  }

  patchTest(body: Test, signal?: AbortSignal): Promise<ResultResponse<Test>> {
    return this.requestFactory.resultResponse(
        {
          method: 'PATCH',
          pathTemplate: '/tests',
          body: body,
          bodyType: patchTestBodyType,
          contentTypes: this.defaultContentTypes,
          acceptTypes: this.defaultAcceptTypes,
          signal: signal,
        },
        patchTestReturnType
    );
  }

  deleteTest(signal?: AbortSignal): Promise<ResultResponse<void>> {
    return this.requestFactory.resultResponse(
        {
          method: 'DELETE',
          pathTemplate: '/tests',
          signal: signal,
        }
    );
  }

  headTest(signal?: AbortSignal): Promise<ResultResponse<void>> {
    return this.requestFactory.resultResponse(
        {
          method: 'HEAD',
          pathTemplate: '/tests',
          signal: signal,
        }
    );
  }

  optionsTest(signal?: AbortSignal): Promise<ResultResponse<void>> {
    return this.requestFactory.resultResponse(
        {
          method: 'OPTIONS',
          pathTemplate: '/tests',
          signal: signal,
        }
    );
  }

  patchableTest(body: PatchableTest, signal?: AbortSignal): Promise<ResultResponse<Test>> {
    return this.requestFactory.resultResponse(
        {
          method: 'PATCH',
          pathTemplate: '/tests2',
          body: body,
          bodyType: patchableTestBodyType,
          contentTypes: this.defaultContentTypes,
          acceptTypes: this.defaultAcceptTypes,
          signal: signal,
        },
        patchableTestReturnType
    );
  }

  requestTest(signal?: AbortSignal): Promise<Request> {
    return this.requestFactory.request(
        {
          method: 'GET',
          pathTemplate: '/request',
          acceptTypes: this.defaultAcceptTypes,
          signal: signal,
        }
    );
  }

  responseTest(signal?: AbortSignal): Promise<Response> {
    return this.requestFactory.response(
        {
          method: 'GET',
          pathTemplate: '/response',
          acceptTypes: this.defaultAcceptTypes,
          signal: signal,
        }
    );
  }

}

const fetchTestReturnType: SchemaLike<Test> = TestSchema;
const putTestBodyType: SchemaLike<Test> = TestSchema;
const putTestReturnType: SchemaLike<Test> = TestSchema;
const postTestBodyType: SchemaLike<Test> = TestSchema;
const postTestReturnType: SchemaLike<Test> = TestSchema;
const patchTestBodyType: SchemaLike<Test> = TestSchema;
const patchTestReturnType: SchemaLike<Test> = TestSchema;
const patchableTestBodyType: SchemaLike<PatchableTest> = PatchableTestSchema;
const patchableTestReturnType: SchemaLike<Test> = TestSchema;
