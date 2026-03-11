import {Child1Schema} from './child1-schema';
import {Child2Schema} from './child2-schema';
import {Parent} from './parent';
import {SchemaRuntime, defineSchema} from '@outfoxx/sunday';
import {z} from 'zod';


export interface TestSpec {

  parent: Parent;

  parentType: string;

}

export class Test implements TestSpec {

  parent: Parent;

  parentType: string;

  constructor(init: TestSpec) {
    this.parent = init.parent;
    this.parentType = init.parentType;
  }

  copy(changes: Partial<TestSpec>): Test {
    return new Test(Object.assign({}, this, changes));
  }

  toString(): string {
    return `Test(parent='${this.parent}', parentType='${this.parentType}')`;
  }

}

export const TestSchema = defineSchema((runtime: SchemaRuntime) => {
  const wireSchema = z.looseObject({
    'parent': z.custom<Parent>(),
    'parentType': z.string()
  });
  const externallyConstrainedWireSchema1 = z.discriminatedUnion('parentType', [
z.looseObject({ ...wireSchema.shape, 'parentType': z.literal('Child1'), 'parent': runtime.resolveSchema(Child1Schema) }),
z.looseObject({ ...wireSchema.shape, 'parentType': z.literal('child2'), 'parent': runtime.resolveSchema(Child2Schema) })
  ]);
  return z.codec(externallyConstrainedWireSchema1, z.instanceof(Test), {
    decode: (value) => new Test({
      parent: value['parent'],
      parentType: value['parentType'],
    }),
    encode: (value) => ({
      'parent': value.parent,
      'parentType': value.parentType,
    }) as z.infer<typeof externallyConstrainedWireSchema1>,
  });
});
