import {ParamType, ParamTypeSchema} from './param-type';
import {Payload} from './payload';
import {PayloadASchema} from './payload-a-schema';
import {PayloadBSchema} from './payload-b-schema';
import {SchemaRuntime, defineSchema} from '@outfoxx/sunday';
import {z} from 'zod';


export interface ContainerSpec {

  left?: Payload;

  leftType?: ParamType;

  right: Payload;

  rightType: ParamType;

}

export class Container implements ContainerSpec {

  left: Payload | undefined;

  leftType: ParamType | undefined;

  right: Payload;

  rightType: ParamType;

  constructor(init: ContainerSpec) {
    this.left = init.left;
    this.leftType = init.leftType;
    this.right = init.right;
    this.rightType = init.rightType;
  }

  copy(changes: Partial<ContainerSpec>): Container {
    return new Container(Object.assign({}, this, changes));
  }

  toString(): string {
    return `Container(left='${this.left}', leftType='${this.leftType}', right='${this.right}', rightType='${this.rightType}')`;
  }

}

export const ContainerSchema = defineSchema((runtime: SchemaRuntime) => {
  const wireSchema = z.looseObject({
    'left': z.custom<Payload>().optional(),
    'leftType': runtime.resolveSchema(ParamTypeSchema).optional(),
    'right': z.custom<Payload>(),
    'rightType': runtime.resolveSchema(ParamTypeSchema)
  });
  const externalDiscriminatorSchema1 = z.union([
z.object({ 'left': z.undefined().optional() }),
z.discriminatedUnion('leftType', [
  z.object({ 'leftType': z.literal(ParamType.B), 'left': runtime.resolveSchema(PayloadBSchema) }),
  z.object({ 'leftType': z.literal(ParamType.A), 'left': runtime.resolveSchema(PayloadASchema) })
  ])
  ]);
  const externallyConstrainedWireSchema1 = z.intersection(wireSchema, externalDiscriminatorSchema1);
  const externalDiscriminatorSchema2 = z.discriminatedUnion('rightType', [
  z.object({ 'rightType': z.literal(ParamType.B), 'right': runtime.resolveSchema(PayloadBSchema) }),
  z.object({ 'rightType': z.literal(ParamType.A), 'right': runtime.resolveSchema(PayloadASchema) })
  ]);
  const externallyConstrainedWireSchema2 = z.intersection(externallyConstrainedWireSchema1, externalDiscriminatorSchema2);
  return z.codec(externallyConstrainedWireSchema2, z.instanceof(Container), {
    decode: (value) => new Container({
      left: value['left'],
      leftType: value['leftType'],
      right: value['right'],
      rightType: value['rightType'],
    }),
    encode: (value) => ({
      'left': value.left,
      'leftType': value.leftType,
      'right': value.right,
      'rightType': value.rightType,
    }) as z.infer<typeof externallyConstrainedWireSchema2>,
  });
});
