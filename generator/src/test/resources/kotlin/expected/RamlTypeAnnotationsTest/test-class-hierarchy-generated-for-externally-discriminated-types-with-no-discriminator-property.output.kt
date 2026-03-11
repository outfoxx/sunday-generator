package io.test

import com.fasterxml.jackson.`annotation`.JsonSubTypes

@JsonSubTypes(value = [
  JsonSubTypes.Type(value = Child1::class),
  JsonSubTypes.Type(value = Child2::class)
])
public open class Parent
