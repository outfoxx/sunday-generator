
export interface ParentSpec {
}

export abstract class Parent implements ParentSpec {

  readonly abstract type: string;

  toString(): string {
    return `Parent()`;
  }

}
