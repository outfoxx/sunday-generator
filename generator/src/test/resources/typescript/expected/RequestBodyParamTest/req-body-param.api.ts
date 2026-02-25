import {Test, TestSchema} from './test';
import {MediaType, RequestFactory, SchemaLike} from '@outfoxx/sunday';


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

  fetchTest(body: Test, signal?: AbortSignal): Promise<Test> {
    return this.requestFactory.result(
        {
          method: 'GET',
          pathTemplate: '/tests',
          body: body,
          bodyType: fetchTestBodyType,
          contentTypes: this.defaultContentTypes,
          acceptTypes: this.defaultAcceptTypes,
          signal: signal,
        },
        fetchTestReturnType
    );
  }

}

const fetchTestBodyType: SchemaLike<Test> = TestSchema;
const fetchTestReturnType: SchemaLike<Test> = TestSchema;
