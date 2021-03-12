export declare type ClassType<T> = (new () => T) | (new (...args: any[]) => T) | ((...args: any[]) => T) | ((...args: any[]) => ((cls: any) => T)) | { name: string; prototype: T };
export interface ClassList<T> extends Array<any> {
    [index: number]: T | ClassList<T>;
    0: T;
}
export function JsonProperty(props: any): any { return null; }
export function JsonClassType(props: { type: () => ClassList<ClassType<any>> }): any { return null; }
export function JsonTypeInfo(props: any): any { return null; }
export function JsonSubTypes(props: any): any { return null; }
export function JsonIgnore(): any { return null; }

export function JsonTypeName(props: {value: string}): any { return null; }

export enum JsonTypeInfoId {
  NAME,
}

export enum JsonTypeInfoAs {
  PROPERTY,
  EXTERNAL_PROPERTY
}
