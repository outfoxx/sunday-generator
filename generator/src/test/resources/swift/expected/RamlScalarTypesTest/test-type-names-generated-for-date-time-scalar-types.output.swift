import Foundation
import Sunday

public class Test : Codable, CustomDebugStringConvertible {

  public var dateOnly: Date
  public var timeOnly: Date
  public var dateTimeOnly: Date
  public var dateTime: Date
  public var debugDescription: String {
    return DescriptionBuilder(Test.self)
        .add(dateOnly, named: "dateOnly")
        .add(timeOnly, named: "timeOnly")
        .add(dateTimeOnly, named: "dateTimeOnly")
        .add(dateTime, named: "dateTime")
        .build()
  }

  public init(
    dateOnly: Date,
    timeOnly: Date,
    dateTimeOnly: Date,
    dateTime: Date
  ) {
    self.dateOnly = dateOnly
    self.timeOnly = timeOnly
    self.dateTimeOnly = dateTimeOnly
    self.dateTime = dateTime
  }

  public required init(from decoder: Decoder) throws {
    let container = try decoder.container(keyedBy: CodingKeys.self)
    self.dateOnly = try container.decode(Date.self, forKey: .dateOnly)
    self.timeOnly = try container.decode(Date.self, forKey: .timeOnly)
    self.dateTimeOnly = try container.decode(Date.self, forKey: .dateTimeOnly)
    self.dateTime = try container.decode(Date.self, forKey: .dateTime)
  }

  public func encode(to encoder: Encoder) throws {
    var container = encoder.container(keyedBy: CodingKeys.self)
    try container.encode(self.dateOnly, forKey: .dateOnly)
    try container.encode(self.timeOnly, forKey: .timeOnly)
    try container.encode(self.dateTimeOnly, forKey: .dateTimeOnly)
    try container.encode(self.dateTime, forKey: .dateTime)
  }

  public func withDateOnly(dateOnly: Date) -> Test {
    return Test(dateOnly: dateOnly, timeOnly: timeOnly, dateTimeOnly: dateTimeOnly,
        dateTime: dateTime)
  }

  public func withTimeOnly(timeOnly: Date) -> Test {
    return Test(dateOnly: dateOnly, timeOnly: timeOnly, dateTimeOnly: dateTimeOnly,
        dateTime: dateTime)
  }

  public func withDateTimeOnly(dateTimeOnly: Date) -> Test {
    return Test(dateOnly: dateOnly, timeOnly: timeOnly, dateTimeOnly: dateTimeOnly,
        dateTime: dateTime)
  }

  public func withDateTime(dateTime: Date) -> Test {
    return Test(dateOnly: dateOnly, timeOnly: timeOnly, dateTimeOnly: dateTimeOnly,
        dateTime: dateTime)
  }

  fileprivate enum CodingKeys : String, CodingKey {

    case dateOnly = "dateOnly"
    case timeOnly = "timeOnly"
    case dateTimeOnly = "dateTimeOnly"
    case dateTime = "dateTime"

  }

}
