import {InvalidIdProblemSchema} from './invalid-id-problem';
import {Test, TestSchema} from './test';
import {TestNotFoundProblemSchema} from './test-not-found-problem';
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
    requestFactory.registerProblem('http://example.com/invalid_id', InvalidIdProblemSchema);
    requestFactory.registerProblem('http://example.com/test_not_found', TestNotFoundProblemSchema);
  }

  fetchTest(signal?: AbortSignal): Promise<Test> {
    return this.requestFactory.result(
        {
          method: 'GET',
          pathTemplate: '/tests',
          acceptTypes: this.defaultAcceptTypes,
          signal: signal,
        },
        fetchTestReturnType
    );
  }

}

const fetchTestReturnType: SchemaLike<Test> = TestSchema;
