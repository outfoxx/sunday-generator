export function JsonProperty(props: any): any { return null; }
export function JsonClassType(props: any): any { return null; }
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
