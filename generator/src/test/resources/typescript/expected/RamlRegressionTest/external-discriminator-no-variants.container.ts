import {Payload} from './payload';
import {SchemaRuntime, defineSchema} from '@outfoxx/sunday';
import {z} from 'zod';


export interface ContainerSpec {

  payload: Payload;

  payloadType: string;

}

export class Container implements ContainerSpec {

  payload: Payload;

  payloadType: string;

  constructor(init: ContainerSpec) {
    this.payload = init.payload;
    this.payloadType = init.payloadType;
  }

  copy(changes: Partial<ContainerSpec>): Container {
    return new Container(Object.assign({}, this, changes));
  }

  toString(): string {
    return `Container(payload='${this.payload}', payloadType='${this.payloadType}')`;
  }

}

export const ContainerSchema = defineSchema((runtime: SchemaRuntime) => {
  const wireSchema = z.looseObject({
    'payload': z.custom<Payload>(),
    'payloadType': z.string()
  });
  const externallyConstrainedWireSchema1 = wireSchema;
  return z.codec(externallyConstrainedWireSchema1, z.instanceof(Container), {
    decode: (value) => new Container({
      payload: value['payload'],
      payloadType: value['payloadType'],
    }),
    encode: (value) => ({
      'payload': value.payload,
      'payloadType': value.payloadType,
    }) as z.infer<typeof externallyConstrainedWireSchema1>,
  });
});
