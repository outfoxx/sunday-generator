import {Problem, ProblemWireSchema, SchemaLike, SchemaOutput, SchemaRuntime, createProblemCodec, defineModelSchema, validateModelConstruction} from '@outfoxx/sunday';
import {z} from 'zod';


export class InvalidIdProblem extends Problem {

  static TYPE: string = 'http://example.com/invalid_id';

  offendingId: string;

  constructor(spec: InvalidIdProblemSpec) {
    super({
      type: InvalidIdProblem.TYPE,
      title: 'Invalid Id',
      status: 400,
      detail: 'The id contains one or more invalid characters.',
      instance: spec.instance,
      offendingId: spec.offendingId
    });

    this.offendingId = spec.offendingId;
    if (new.target === InvalidIdProblem) validateModelConstruction(this, InvalidIdProblemSchema, InvalidIdProblem);
  }

}

export type InvalidIdProblemSpec = SchemaOutput<typeof InvalidIdProblemSpecSchema>;

export const InvalidIdProblemSpecSchema = defineModelSchema((runtime: SchemaRuntime) => {
  return z.looseObject({
    'offendingId': z.string(),
    'instance': z.union([z.string(), z.instanceof(URL)]).optional()
  });
});

export const InvalidIdProblemWireSchema = defineModelSchema((runtime: SchemaRuntime) => {
  return ProblemWireSchema.extend({
    'offendingId': z.string()
  });
});

export const InvalidIdProblemSchema: SchemaLike<InvalidIdProblem> = defineModelSchema((runtime: SchemaRuntime) => {
  return createProblemCodec(InvalidIdProblem, runtime.resolveSchema(InvalidIdProblemWireSchema));
});
