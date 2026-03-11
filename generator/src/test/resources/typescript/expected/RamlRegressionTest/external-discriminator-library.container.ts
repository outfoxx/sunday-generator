import {Service} from './service';
import {ServiceType, ServiceTypeSchema} from './service-type';
import {SchemaRuntime, defineSchema} from '@outfoxx/sunday';
import {z} from 'zod';


export interface ContainerSpec {

  service: Service;

  type: ServiceType;

}

export class Container implements ContainerSpec {

  service: Service;

  type: ServiceType;

  constructor(init: ContainerSpec) {
    this.service = init.service;
    this.type = init.type;
  }

  copy(changes: Partial<ContainerSpec>): Container {
    return new Container(Object.assign({}, this, changes));
  }

  toString(): string {
    return `Container(service='${this.service}', type='${this.type}')`;
  }

}

export const ContainerSchema = defineSchema((runtime: SchemaRuntime) => {
  const wireSchema = z.looseObject({
    'service': z.custom<Service>(),
    'type': runtime.resolveSchema(ServiceTypeSchema)
  });
  const externallyConstrainedWireSchema1 = z.discriminatedUnion('type', [
z.looseObject({ ...wireSchema.shape, 'type': z.literal(ServiceType.A), 'service': runtime.resolveSchema(Service.ASchema) }),
z.looseObject({ ...wireSchema.shape, 'type': z.literal(ServiceType.B), 'service': runtime.resolveSchema(Service.BSchema) })
  ]);
  return z.codec(externallyConstrainedWireSchema1, z.instanceof(Container), {
    decode: (value) => new Container({
      service: value['service'],
      type: value['type'],
    }),
    encode: (value) => ({
      'service': value.service,
      'type': value.type,
    }) as z.infer<typeof externallyConstrainedWireSchema1>,
  });
});
