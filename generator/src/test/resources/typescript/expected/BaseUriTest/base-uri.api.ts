import {Environment} from './environment';
import {MediaType, Operation, SchemaLike, StringSchema, Transport, URLTemplate, createOperation} from '@outfoxx/sunday';


export interface API<Factory extends SundayTransport> {

  fetchTest(): Operation<void, string, Factory>;

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
  }

  fetchTest(): Operation<void, string, Factory> {
    return createOperation(this.transport, {
        request: {
          method: 'GET',
          pathTemplate: '/tests',
          acceptTypes: this.defaultAcceptTypes,
        },
        responseType: fetchTestReturnType
    });
  }

}

export function createAPI<Factory extends SundayTransport>(transport: Factory,
    options: { defaultContentTypes?: Array<MediaType>, defaultAcceptTypes?: Array<MediaType> } | undefined = undefined): API<Factory> {
  return new APIClient(transport, options);
}

export namespace API {

  export function baseURL(server: string | null | undefined = 'master',
      environment: Environment | null | undefined = Environment.Sbx,
      version: string | null | undefined = '1'): URLTemplate {
    return new URLTemplate(
      'http://{server}.{environment}.example.com/api/{version}',
      {server, environment, version}
    );
  }

}

type SundayTransport = Transport<unknown>;

const fetchTestReturnType: SchemaLike<string> = StringSchema;
