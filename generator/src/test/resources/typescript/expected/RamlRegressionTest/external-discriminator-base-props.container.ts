import {ParamType, ParamTypeSchema} from './param-type';
import {SchemaRuntime, defineSchema} from '@outfoxx/sunday';
import {z} from 'zod';


export interface ContainerSpec {

  type: ParamType;

  parameters?: Container.Parameters;

}

export class Container implements ContainerSpec {

  type: ParamType;

  parameters: Container.Parameters | undefined;

  constructor(init: ContainerSpec) {
    this.type = init.type;
    this.parameters = init.parameters;
  }

  copy(changes: Partial<ContainerSpec>): Container {
    return new Container(Object.assign({}, this, changes));
  }

  toString(): string {
    return `Container(type='${this.type}', parameters='${this.parameters}')`;
  }

}

export namespace Container {

  export interface ParametersSpec {

    version?: number;

  }

  export class Parameters implements ParametersSpec {

    version: number | undefined;

    constructor(init: ParametersSpec) {
      this.version = init.version;
    }

    toString(): string {
      return `Container.Parameters(version='${this.version}')`;
    }

  }

  export const ParametersSchema = defineSchema((runtime: SchemaRuntime) => {
    const wireSchema = z.looseObject({
      'version': z.number().optional()
    });
    return z.codec(wireSchema, z.instanceof(Parameters), {
      decode: () => { throw new TypeError(`Parameters requires external discriminator`); },
      encode: (value) => value as unknown as Record<string, unknown>,
    });
  });

  export interface AParametersSpec extends ParametersSpec {

    value: string;

  }

  export class AParameters extends Parameters implements AParametersSpec {

    value: string;

    constructor(init: AParametersSpec) {
      super(init);
      this.value = init.value;
    }

    copy(changes: Partial<AParametersSpec>): AParameters {
      return new AParameters(Object.assign({}, this, changes));
    }

    toString(): string {
      return `Container.AParameters(version='${this.version}', value='${this.value}')`;
    }

  }

  export const AParametersSchema = defineSchema((runtime: SchemaRuntime) => {
    const wireSchema = z.looseObject({
      'version': z.number().optional(),
      'value': z.string()
    });
    return z.codec(wireSchema, z.instanceof(AParameters), {
      decode: (value) => new AParameters({
        version: value['version'],
        value: value['value'],
      }),
      encode: (value) => ({
        'version': value.version,
        'value': value.value,
      }) as z.infer<typeof wireSchema>,
    });
  });

}

export const ContainerSchema = defineSchema((runtime: SchemaRuntime) => {
  const wireSchema = z.looseObject({
    'type': runtime.resolveSchema(ParamTypeSchema),
    'parameters': z.custom<Container.Parameters>().optional()
  });
  const externallyConstrainedWireSchema1 = z.union([
z.discriminatedUnion('type', [
z.looseObject({ ...wireSchema.shape, 'type': z.literal(ParamType.A), 'parameters': runtime.resolveSchema(Container.AParametersSchema) })
  ]),
z.looseObject({ ...wireSchema.shape, 'parameters': z.undefined().optional() })
  ]);
  return z.codec(externallyConstrainedWireSchema1, z.instanceof(Container), {
    decode: (value) => new Container({
      type: value['type'],
      parameters: value['parameters'],
    }),
    encode: (value) => ({
      'type': value.type,
      'parameters': value.parameters,
    }) as z.infer<typeof externallyConstrainedWireSchema1>,
  });
});
