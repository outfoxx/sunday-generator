import {Base} from './base';
import {BaseSchema} from './base-schema';
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

  fetchDerivedTest(signal?: AbortSignal): Promise<Base> {
    return this.requestFactory.result(
        {
          method: 'GET',
          pathTemplate: '/tests/derived',
          acceptTypes: this.defaultAcceptTypes,
          signal: signal,
        },
        fetchDerivedTestReturnType
    );
  }

}

const fetchTestReturnType: SchemaLike<Test> = TestSchema;
const fetchDerivedTestReturnType: SchemaLike<Base> = BaseSchema;
