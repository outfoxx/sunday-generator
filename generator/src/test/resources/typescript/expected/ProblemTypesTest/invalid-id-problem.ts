import {Problem, ProblemWireSchema, SchemaLike, SchemaRuntime, createProblemCodec, defineSchema} from '@outfoxx/sunday';
import {z} from 'zod';


export class InvalidIdProblem extends Problem {

  static TYPE: string = 'http://example.com/invalid_id';

  offendingId: string;

  constructor(spec: { offendingId: string, instance?: string | URL }) {
    super({
      type: InvalidIdProblem.TYPE,
      title: 'Invalid Id',
      status: 400,
      detail: 'The id contains one or more invalid characters.',
      instance: spec.instance,
      offendingId: spec.offendingId
    });

    this.offendingId = spec.offendingId;
  }

}

export const InvalidIdProblemSchema: SchemaLike<InvalidIdProblem> = defineSchema((runtime: SchemaRuntime) => {
  const wireSchema = ProblemWireSchema.extend({
    'offendingId': z.string()
  });
  return createProblemCodec(InvalidIdProblem, wireSchema);
});
