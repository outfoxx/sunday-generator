import {Problem, ProblemWireSchema, SchemaLike, SchemaOutput, SchemaRuntime, createProblemCodec, createProblemReferenceCodec, defineModelSchema, validateModelConstruction} from '@outfoxx/sunday';
import {z} from 'zod';


export class InvalidIdProblem extends Problem {

  static TYPE: string = 'http://example.com/invalid_id';

  offendingId: string;

  constructor(spec: InvalidIdProblemSpec) {
    super({
      ...spec,
      type: InvalidIdProblem.TYPE,
      title: spec.title ?? 'Invalid Id',
      status: spec.status ?? 400,
      detail: spec.detail ?? 'The id contains one or more invalid characters.',
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
    'title': z.string().optional(),
    'status': z.number().int().optional(),
    'detail': z.string().optional(),
    'instance': z.union([z.string(), z.instanceof(URL)]).optional()
  });
});

export const InvalidIdProblemWireSchema = defineModelSchema((runtime: SchemaRuntime) => {
  return ProblemWireSchema.extend({
    'type': createProblemReferenceCodec(z.literal('http://example.com/invalid_id'), true),
    'title': ProblemWireSchema.shape.title.pipe(z.string().prefault('Invalid Id')),
    'status': ProblemWireSchema.shape.status.pipe(z.number().prefault(400)),
    'detail': ProblemWireSchema.shape.detail.pipe(z.string().prefault('The id contains one or more invalid characters.')),
    'offendingId': z.string(),
  });
});

export const InvalidIdProblemSchema: SchemaLike<InvalidIdProblem> = defineModelSchema((runtime: SchemaRuntime) => {
  return createProblemCodec(InvalidIdProblem, runtime.resolveSchema(InvalidIdProblemWireSchema));
});
