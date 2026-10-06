import {Environment} from './environment';
import {TestAPIConfig} from './test-api-config';
import {ClientSettings, Credentials, MediaType, Operation, SchemaLike, SecurityBinding, StringSchema, Transport, URLTemplate, createOperation} from '@outfoxx/sunday';


export interface API<Factory extends SundayTransport> {

  fetchTest(): Operation<void, string, Factory>;

}

class APIClient<Factory extends SundayTransport> {

  defaultContentTypes: Array<MediaType>;

  defaultAcceptTypes: Array<MediaType>;

  constructor(public transport: Factory,
      options: { defaultContentTypes?: Array<MediaType>, defaultAcceptTypes?: Array<MediaType> } | undefined = undefined,
      private clientSettings: ClientSettings | undefined = undefined) {
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
          security: this.clientSettings?.bindings['fetchTest'] ?? [],
          acceptTypes: this.defaultAcceptTypes,
        },
        responseType: fetchTestReturnType
    });
  }

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

/** Complete scheme-and-scope alternatives accepted by this service. */
export enum APISecurityAlternative {
  Public = 'Public',
}
/** API scheme credentials used by configuration factories. */
export interface APICredentials {
}
/** Constructs a client from an existing application transport. */
export function createAPI<Factory extends SundayTransport>(transport: Factory, options?: { defaultContentTypes?: Array<MediaType>, defaultAcceptTypes?: Array<MediaType> }): API<Factory>;
/** Resolves a server and invokes the application's transport factory exactly once. */
export function createAPI<Factory extends SundayTransport>(config: TestAPIConfig, transportFactory: (settings: ClientSettings) => Factory, options?: { defaultContentTypes?: Array<MediaType>, defaultAcceptTypes?: Array<MediaType> } & { credentials?: APICredentials; securityProfile?: string | null; securitySelection?: {readonly [operation: string]: APISecurityAlternative} }): API<Factory>;
export function createAPI<Factory extends SundayTransport>(input: Factory | TestAPIConfig, factoryOrOptions?: ((settings: ClientSettings) => Factory) | { defaultContentTypes?: Array<MediaType>, defaultAcceptTypes?: Array<MediaType> }, options?: { defaultContentTypes?: Array<MediaType>, defaultAcceptTypes?: Array<MediaType> } & { credentials?: APICredentials; securityProfile?: string | null; securitySelection?: {readonly [operation: string]: APISecurityAlternative} }): API<Factory> {
  if (typeof factoryOrOptions !== 'function') return new APIClient(input as Factory, factoryOrOptions);
  const config = input as TestAPIConfig;
  let endpoint: string;
  let defaultProfile: string | undefined;
  switch (config.serverId ?? 'server1') {
    case 'server1': {
      const values = config as TestAPIConfig;
      const variable0 = values.server ?? 'master';
      if (typeof variable0 !== 'string') throw new globalThis.TypeError('Invalid server variable \'server\'');
      const variable1 = values.environment ?? Environment.Sbx;
      if (typeof variable1 !== 'string') throw new globalThis.TypeError('Invalid server variable \'environment\'');
      if (!(['sbx', 'prd'] as readonly unknown[]).includes(variable1)) throw new globalThis.TypeError('Invalid server variable \'environment\'');
      const variable2 = values.version ?? '1';
      if (typeof variable2 !== 'string') throw new globalThis.TypeError('Invalid server variable \'version\'');
      endpoint = ClientSettings.serverUrl('http://{server}.{environment}.example.com/api/{version}', {'server': globalThis.String(variable0),'environment': globalThis.String(variable1),'version': globalThis.String(variable2),}, undefined);
      defaultProfile = undefined;
      break;
    }
    default: throw new globalThis.TypeError('Unknown server configuration');
  }
  const profile = options?.securityProfile === undefined ? defaultProfile : options.securityProfile ?? undefined;
  let alternatives: {readonly [operation: string]: readonly (readonly SecurityBinding[])[]};
  switch (config.serverId ?? 'server1') {
    case 'server1': {
      switch (profile) {
        case undefined: alternatives = {'fetchTest': [[]],}; break;
        default: throw new globalThis.TypeError('Unknown client security profile');
      }
      break;
    }
    default: throw new globalThis.TypeError('Unknown server configuration');
  }
  const credentials: {[scheme: string]: Credentials} = {};
  const requirements: {readonly [alternative in APISecurityAlternative]: {readonly [scheme: string]: readonly string[]}} = {
  [APISecurityAlternative.Public]: {},
  };
  if (globalThis.Object.keys(options?.securitySelection ?? {}).some(operation => !(operation in alternatives))) throw new globalThis.TypeError('Unknown operation in security selection');
  const selectedAlternatives = globalThis.Object.fromEntries(globalThis.Object.entries(alternatives).map(([operation, choices]) => {
    const selection = options?.securitySelection?.[operation];
    if (selection === undefined) return [operation, choices];
    const required = requirements[selection];
    return [operation, choices.filter(choice => choice.length === globalThis.Object.keys(required).length && choice.every(binding => required[binding.scheme]?.length === binding.scopes.length && binding.scopes.every(scope => required[binding.scheme].includes(scope))))];
  }));
  const settings = ClientSettings.resolve(endpoint, selectedAlternatives, credentials);
  const transport = factoryOrOptions(settings);
  return new APIClient(transport, options, settings);
}
