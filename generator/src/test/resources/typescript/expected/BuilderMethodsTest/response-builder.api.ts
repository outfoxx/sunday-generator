import {MediaType, RequestFactory} from '@outfoxx/sunday';


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

  fetchTest(signal?: AbortSignal): Promise<Response> {
    return this.requestFactory.response(
        {
          method: 'GET',
          pathTemplate: '/test/response',
          acceptTypes: this.defaultAcceptTypes,
          signal: signal,
        }
    );
  }

}
