package io.test

import com.fasterxml.jackson.`annotation`.JsonProperty

public enum class TestEnum {
  @JsonProperty(value = "none")
  None,
  @JsonProperty(value = "some")
  Some,
  @JsonProperty(value = "all")
  All,
  @JsonProperty(value = "snake_case")
  SnakeCase,
  @JsonProperty(value = "kebab-case")
  KebabCase,
  @JsonProperty(value = "invalid:char")
  InvalidChar,
}
