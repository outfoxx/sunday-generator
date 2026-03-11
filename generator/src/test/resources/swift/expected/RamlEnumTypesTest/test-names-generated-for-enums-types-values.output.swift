public enum TestEnum : String, CaseIterable, Codable {

  case none = "none"
  case some = "some"
  case all = "all"
  case snakeCase = "snake_case"
  case kebabCase = "kebab-case"
  case invalidChar = "invalid:char"

}
