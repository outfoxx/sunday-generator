import Sunday

public class Root : Codable, CustomDebugStringConvertible {

  public var value: String
  public var debugDescription: String {
    return DescriptionBuilder(Root.self)
        .add(value, named: "value")
        .build()
  }

  public init(value: String) {
    self.value = value
  }

  public required init(from decoder: Decoder) throws {
    let container = try decoder.container(keyedBy: CodingKeys.self)
    self.value = try container.decode(String.self, forKey: .value)
  }

  public func encode(to encoder: Encoder) throws {
    var container = encoder.container(keyedBy: CodingKeys.self)
    try container.encode(self.value, forKey: .value)
  }

  public func withValue(value: String) -> Root {
    return Root(value: value)
  }

  fileprivate enum CodingKeys : String, CodingKey {

    case value = "value"

  }

  public class Group : Codable, CustomDebugStringConvertible {

    public var value: String
    public var debugDescription: String {
      return DescriptionBuilder(Group.self)
          .add(value, named: "value")
          .build()
    }

    public init(value: String) {
      self.value = value
    }

    public required init(from decoder: Decoder) throws {
      let container = try decoder.container(keyedBy: CodingKeys.self)
      self.value = try container.decode(String.self, forKey: .value)
    }

    public func encode(to encoder: Encoder) throws {
      var container = encoder.container(keyedBy: CodingKeys.self)
      try container.encode(self.value, forKey: .value)
    }

    public func withValue(value: String) -> Group {
      return Group(value: value)
    }

    fileprivate enum CodingKeys : String, CodingKey {

      case value = "value"

    }

    public class Member : Codable, CustomDebugStringConvertible {

      public var memberValue: String
      public var debugDescription: String {
        return DescriptionBuilder(Member.self)
            .add(memberValue, named: "memberValue")
            .build()
      }

      public init(memberValue: String) {
        self.memberValue = memberValue
      }

      public required init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.memberValue = try container.decode(String.self, forKey: .memberValue)
      }

      public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(self.memberValue, forKey: .memberValue)
      }

      public func withMemberValue(memberValue: String) -> Member {
        return Member(memberValue: memberValue)
      }

      fileprivate enum CodingKeys : String, CodingKey {

        case memberValue = "memberValue"

      }

    }

  }

}
