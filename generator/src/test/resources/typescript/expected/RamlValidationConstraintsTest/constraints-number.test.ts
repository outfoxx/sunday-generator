import {SchemaRuntime, defineSchema} from '@outfoxx/sunday';
import {z} from 'zod';


export interface TestSpec {

  floatMin: number;

  floatMax: number;

  floatMultiple: number;

  doubleMin: number;

  doubleMax: number;

  doubleMultiple: number;

}

export class Test implements TestSpec {

  floatMin: number;

  floatMax: number;

  floatMultiple: number;

  doubleMin: number;

  doubleMax: number;

  doubleMultiple: number;

  constructor(init: TestSpec) {
    this.floatMin = init.floatMin;
    this.floatMax = init.floatMax;
    this.floatMultiple = init.floatMultiple;
    this.doubleMin = init.doubleMin;
    this.doubleMax = init.doubleMax;
    this.doubleMultiple = init.doubleMultiple;
  }

  copy(changes: Partial<TestSpec>): Test {
    return new Test(Object.assign({}, this, changes));
  }

  toString(): string {
    return `Test(floatMin='${this.floatMin}', floatMax='${this.floatMax}', floatMultiple='${this.floatMultiple}', doubleMin='${this.doubleMin}', doubleMax='${this.doubleMax}', doubleMultiple='${this.doubleMultiple}')`;
  }

}

export const TestSchema = defineSchema((runtime: SchemaRuntime) => {
  const wireSchema = z.looseObject({
    'floatMin': z.number().gte(1.0),
    'floatMax': z.number().lte(2.0),
    'floatMultiple': z.number().multipleOf(3.0),
    'doubleMin': z.number().gte(4.0),
    'doubleMax': z.number().lte(5.0),
    'doubleMultiple': z.number().multipleOf(6.0)
  });
  return z.codec(wireSchema, z.instanceof(Test), {
    decode: (value) => new Test({
      floatMin: value['floatMin'],
      floatMax: value['floatMax'],
      floatMultiple: value['floatMultiple'],
      doubleMin: value['doubleMin'],
      doubleMax: value['doubleMax'],
      doubleMultiple: value['doubleMultiple'],
    }),
    encode: (value) => ({
      'floatMin': value.floatMin,
      'floatMax': value.floatMax,
      'floatMultiple': value.floatMultiple,
      'doubleMin': value.doubleMin,
      'doubleMax': value.doubleMax,
      'doubleMultiple': value.doubleMultiple,
    }) as z.infer<typeof wireSchema>,
  });
});
