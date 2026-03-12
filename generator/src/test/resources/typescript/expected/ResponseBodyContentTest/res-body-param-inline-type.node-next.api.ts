import {MediaType, RequestFactory, SchemaLike, SchemaRuntime, defineSchema} from '@outfoxx/sunday';
import {z} from 'zod';


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

  fetchTest(signal?: AbortSignal): Promise<API.FetchTestResponseBody> {
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

export namespace API {

  export interface FetchTestResponseBodySpec {

    value: string;

  }

  export class FetchTestResponseBody implements FetchTestResponseBodySpec {

    value: string;

    constructor(init: FetchTestResponseBodySpec) {
      this.value = init.value;
    }

    copy(changes: Partial<FetchTestResponseBodySpec>): FetchTestResponseBody {
      return new FetchTestResponseBody(Object.assign({}, this, changes));
    }

    toString(): string {
      return `API.FetchTestResponseBody(value='${this.value}')`;
    }

  }

  export const FetchTestResponseBodySchema = defineSchema((runtime: SchemaRuntime) => {
    const wireSchema = z.looseObject({
      'value': z.string()
    });
    return z.codec(wireSchema, z.instanceof(FetchTestResponseBody), {
      decode: (value) => new FetchTestResponseBody({
        value: value['value'],
      }),
      encode: (value) => ({
        'value': value.value,
      }) as z.infer<typeof wireSchema>,
    });
  });

}

const fetchTestReturnType: SchemaLike<API.FetchTestResponseBody> = API.FetchTestResponseBodySchema;
