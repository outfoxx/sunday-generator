import {Parent, ParentSpec} from './parent';


export interface Child1Spec extends ParentSpec {

  value?: string;

  value1: number;

}

export class Child1 extends Parent implements Child1Spec {

  value: string | undefined;

  value1: number;

  constructor(init: Child1Spec) {
    super();
    this.value = init.value;
    this.value1 = init.value1;
  }

  get type(): string {
    return 'Child1';
  }

  copy(changes: Partial<Child1Spec>): Child1 {
    return new Child1(Object.assign({}, this, changes));
  }

  toString(): string {
    return `Child1(value='${this.value}', value1='${this.value1}')`;
  }

}
