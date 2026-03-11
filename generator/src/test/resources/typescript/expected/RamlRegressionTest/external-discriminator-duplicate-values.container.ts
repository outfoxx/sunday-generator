import {ParamType, ParamTypeSchema} from './param-type';
import {Payload} from './payload';
import {PayloadASchema} from './payload-a-schema';
import {PayloadBSchema} from './payload-b-schema';
import {SchemaRuntime, defineSchema} from '@outfoxx/sunday';
import {z} from 'zod';


export interface ContainerSpec {

  payload: Payload;

  payloadType: ParamType;

}

export class Container implements ContainerSpec {

  payload: Payload;

  payloadType: ParamType;

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
    'payloadType': runtime.resolveSchema(ParamTypeSchema)
  });
  const externallyConstrainedWireSchema1 = z.union([
z.looseObject({ ...wireSchema.shape, 'payloadType': z.literal(ParamType.Dup), 'payload': runtime.resolveSchema(PayloadBSchema) }),
z.looseObject({ ...wireSchema.shape, 'payloadType': z.literal(ParamType.Dup), 'payload': runtime.resolveSchema(PayloadASchema) })
  ]);
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
