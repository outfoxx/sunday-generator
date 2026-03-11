import {Environment} from './environment';
import {MediaType, RequestFactory, SchemaLike, StringSchema, URLTemplate} from '@outfoxx/sunday';


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

  static baseURL(server?: string, environment?: Environment, version?: string): URLTemplate {
    return new URLTemplate(
      'http://{server}.{environment}.example.com/api/{version}',
      {server: server ?? 'master', environment: environment ?? Environment.Sbx, version: version ?? '1'}
    );
  }

  fetchTest(signal?: AbortSignal): Promise<string> {
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

const fetchTestReturnType: SchemaLike<string> = StringSchema;
