import {Test1, Test1Schema} from './test1';
import {Test2, Test2Schema} from './test2';
import {Test3, Test3Schema} from './test3';
import {MediaType, SchemaLike, Transport} from '@outfoxx/sunday';


export interface API<Factory extends SundayTransport> {

  fetchEventsSimple(signal?: AbortSignal): AsyncIterable<Test1>;

  fetchEventsDiscriminated(signal?: AbortSignal): AsyncIterable<Test1 | Test2 | Test3>;

}

class APIClient<Factory extends SundayTransport> {

  defaultContentTypes: Array<MediaType>;

  defaultAcceptTypes: Array<MediaType>;

  constructor(public transport: Factory,
      options: { defaultContentTypes?: Array<MediaType>, defaultAcceptTypes?: Array<MediaType> } | undefined = undefined) {
    this.defaultContentTypes =
        options?.defaultContentTypes ?? [];
    this.defaultAcceptTypes =
        options?.defaultAcceptTypes ?? [];
  }

  fetchEventsSimple(signal?: AbortSignal): AsyncIterable<Test1> {
    return this.transport.eventStream<Test1>(
        {
          method: 'GET',
          pathTemplate: '/test1',
          acceptTypes: [MediaType.EventStream],
          signal: signal,
        },
        (decoder, event, id, data) => decoder.decodeText(data, fetchEventsSimpleEventType)
    );
  }

  fetchEventsDiscriminated(signal?: AbortSignal): AsyncIterable<Test1 | Test2 | Test3> {
    return this.transport.eventStream<Test1 | Test2 | Test3>(
        {
          method: 'GET',
          pathTemplate: '/test2',
          acceptTypes: [MediaType.EventStream],
          signal: signal,
        },
        (decoder, event, id, data, logger) => {
          switch (event) {
          case 'Test1': return decoder.decodeText(data, fetchEventsDiscriminatedEventType1);
          case 'test2': return decoder.decodeText(data, fetchEventsDiscriminatedEventType2);
          case 't3': return decoder.decodeText(data, fetchEventsDiscriminatedEventType3);
          default:
            logger?.error?.(`Unknown event type, ignoring event: event=${event}`);
            return undefined;
          }
        },
    );
  }

}

export function createAPI<Factory extends SundayTransport>(transport: Factory,
    options: { defaultContentTypes?: Array<MediaType>, defaultAcceptTypes?: Array<MediaType> } | undefined = undefined): API<Factory> {
  return new APIClient(transport, options);
}

type SundayTransport = Transport<unknown>;

const fetchEventsSimpleEventType: SchemaLike<Test1> = Test1Schema;
const fetchEventsDiscriminatedEventType1: SchemaLike<Test1> = Test1Schema;
const fetchEventsDiscriminatedEventType2: SchemaLike<Test2> = Test2Schema;
const fetchEventsDiscriminatedEventType3: SchemaLike<Test3> = Test3Schema;
