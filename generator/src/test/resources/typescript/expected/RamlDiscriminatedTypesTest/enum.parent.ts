import {Type} from './type';


export interface ParentSpec {
}

export abstract class Parent implements ParentSpec {

  readonly abstract type: Type;

  toString(): string {
    return `Parent()`;
  }

}
