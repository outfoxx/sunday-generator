import Sunday

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

  public class Member1 : Group {

    public var memberValue1: String
    public override var debugDescription: String {
      return DescriptionBuilder(Member1.self)
          .add(value, named: "value")
          .add(memberValue1, named: "memberValue1")
          .build()
    }

    public init(value: String, memberValue1: String) {
      self.memberValue1 = memberValue1
      super.init(value: value)
    }

    public required init(from decoder: Decoder) throws {
      let container = try decoder.container(keyedBy: CodingKeys.self)
      self.memberValue1 = try container.decode(String.self, forKey: .memberValue1)
      try super.init(from: decoder)
    }

    public override func encode(to encoder: Encoder) throws {
      try super.encode(to: encoder)
      var container = encoder.container(keyedBy: CodingKeys.self)
      try container.encode(self.memberValue1, forKey: .memberValue1)
    }

    public override func withValue(value: String) -> Member1 {
      return Member1(value: value, memberValue1: memberValue1)
    }

    public func withMemberValue1(memberValue1: String) -> Member1 {
      return Member1(value: value, memberValue1: memberValue1)
    }

    fileprivate enum CodingKeys : String, CodingKey {

      case memberValue1 = "memberValue1"

    }

    public class Sub : Member1 {

      public var subMemberValue: String
      public override var debugDescription: String {
        return DescriptionBuilder(Sub.self)
            .add(value, named: "value")
            .add(memberValue1, named: "memberValue1")
            .add(subMemberValue, named: "subMemberValue")
            .build()
      }

      public init(
        value: String,
        memberValue1: String,
        subMemberValue: String
      ) {
        self.subMemberValue = subMemberValue
        super.init(value: value, memberValue1: memberValue1)
      }

      public required init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.subMemberValue = try container.decode(String.self, forKey: .subMemberValue)
        try super.init(from: decoder)
      }

      public override func encode(to encoder: Encoder) throws {
        try super.encode(to: encoder)
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(self.subMemberValue, forKey: .subMemberValue)
      }

      public override func withValue(value: String) -> Sub {
        return Sub(value: value, memberValue1: memberValue1, subMemberValue: subMemberValue)
      }

      public override func withMemberValue1(memberValue1: String) -> Sub {
        return Sub(value: value, memberValue1: memberValue1, subMemberValue: subMemberValue)
      }

      public func withSubMemberValue(subMemberValue: String) -> Sub {
        return Sub(value: value, memberValue1: memberValue1, subMemberValue: subMemberValue)
      }

      fileprivate enum CodingKeys : String, CodingKey {

        case subMemberValue = "subMemberValue"

      }

    }

  }

  public class Member2 : Group {

    public var memberValue2: String
    public override var debugDescription: String {
      return DescriptionBuilder(Member2.self)
          .add(value, named: "value")
          .add(memberValue2, named: "memberValue2")
          .build()
    }

    public init(value: String, memberValue2: String) {
      self.memberValue2 = memberValue2
      super.init(value: value)
    }

    public required init(from decoder: Decoder) throws {
      let container = try decoder.container(keyedBy: CodingKeys.self)
      self.memberValue2 = try container.decode(String.self, forKey: .memberValue2)
      try super.init(from: decoder)
    }

    public override func encode(to encoder: Encoder) throws {
      try super.encode(to: encoder)
      var container = encoder.container(keyedBy: CodingKeys.self)
      try container.encode(self.memberValue2, forKey: .memberValue2)
    }

    public override func withValue(value: String) -> Member2 {
      return Member2(value: value, memberValue2: memberValue2)
    }

    public func withMemberValue2(memberValue2: String) -> Member2 {
      return Member2(value: value, memberValue2: memberValue2)
    }

    fileprivate enum CodingKeys : String, CodingKey {

      case memberValue2 = "memberValue2"

    }

  }

}
