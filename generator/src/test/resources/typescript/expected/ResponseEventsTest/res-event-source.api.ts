import {MediaType, RequestFactory} from '@outfoxx/sunday';


export class API {

  defaultContentTypes: Array<MediaType>;

  defaultAcceptTypes: Array<MediaType>;

  constructor(public requestFactory: RequestFactory,
      options: { defaultContentTypes?: Array<MediaType>, defaultAcceptTypes?: Array<MediaType> } | undefined = undefined) {
    this.defaultContentTypes =
        options?.defaultContentTypes ?? [];
    this.defaultAcceptTypes =
        options?.defaultAcceptTypes ?? [];
  }

  fetchEvents(signal?: AbortSignal): EventSource {
    return this.requestFactory.eventSource(
        {
          method: 'GET',
          pathTemplate: '/tests',
          acceptTypes: [MediaType.EventStream],
          signal: signal,
        }
    );
  }

}
