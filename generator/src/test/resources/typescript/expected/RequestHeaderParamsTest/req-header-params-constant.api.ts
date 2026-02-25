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

  putTest(xCustom: string, signal?: AbortSignal): Promise<Test> {
    return this.requestFactory.result(
        {
          method: 'PUT',
          pathTemplate: '/tests',
          acceptTypes: this.defaultAcceptTypes,
          headers: {
            Expect: '100-continue',
            'x-custom': xCustom
          },
          signal: signal,
        },
        putTestReturnType
    );
  }

}

const putTestReturnType: SchemaLike<Test> = TestSchema;
