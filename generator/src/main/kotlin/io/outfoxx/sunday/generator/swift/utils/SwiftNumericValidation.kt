/*
 * Copyright 2026 Outfox, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.outfoxx.sunday.generator.swift.utils

import io.outfoxx.swiftpoet.CodeBlock

/** Decimal-first validation without restricting the range of the eventual Double storage. */
internal object SwiftNumericValidation {
  // This local type lives only inside a validation block, so it cannot shadow a user's stored model types.
  val helper =
    CodeBlock.of(
      """
      struct _SundayValidationNumber: Swift.Decodable, Swift.Hashable, Swift.Comparable {
        let negative: Bool
        let digits: [Int]
        let exponent: Int

        init(from decoder: Decoder) throws {
          let container = try decoder.singleValueContainer()
          if let value = try? container.decode(%T.self), value.isFinite {
            self.init(value.description)
          } else {
            let value = try container.decode(Swift.Double.self)
            guard value.isFinite else {
              throw DecodingError.dataCorruptedError(in: container, debugDescription: "Expected a finite number")
            }
            self.init(value.description)
          }
        }

        // Inputs come only from finite numeric descriptions or validated generator literals.
        init(_ literal: String) {
          let parts = literal.lowercased().split(separator: "e")
          let mantissa = parts[0]
          let negative = mantissa.hasPrefix("-")
          let unsigned = mantissa.drop(while: { ${'$'}0 == "-" || ${'$'}0 == "+" })
          let fraction = unsigned.split(separator: ".", omittingEmptySubsequences: false)
          var digits = fraction.joined().utf8.map { Int(${'$'}0) - 48 }
          var exponent = (parts.count == 2 ? Int(parts[1])! : 0) - (fraction.count == 2 ? fraction[1].count : 0)
          while digits.count > 1 && digits.first == 0 { digits.removeFirst() }
          while digits.count > 1 && digits.last == 0 {
            digits.removeLast()
            exponent += 1
          }
          let zero = digits == [0]
          self.negative = negative && !zero
          self.digits = digits
          self.exponent = zero ? 0 : exponent
        }

        static func < (lhs: Self, rhs: Self) -> Bool {
          if lhs.negative != rhs.negative { return lhs.negative }
          if lhs.digits == [0] { return rhs.digits != [0] }
          if rhs.digits == [0] { return false }
          let leftOrder = lhs.digits.count + lhs.exponent
          let rightOrder = rhs.digits.count + rhs.exponent
          if leftOrder != rightOrder {
            return lhs.negative ? leftOrder > rightOrder : leftOrder < rightOrder
          }
          let count = max(lhs.digits.count, rhs.digits.count)
          let left = lhs.digits + Array(repeating: 0, count: count - lhs.digits.count)
          let right = rhs.digits + Array(repeating: 0, count: count - rhs.digits.count)
          return lhs.negative ? right.lexicographicallyPrecedes(left) : left.lexicographicallyPrecedes(right)
        }

        func isMultipleOf(digits: [Int], exponent: Int) -> Bool {
          if self.digits == [0] { return true }
          var dividend = self.digits
          var divisor = digits
          let scale = self.exponent - exponent
          if scale >= 0 {
            dividend += Array(repeating: 0, count: scale)
          } else {
            divisor += Array(repeating: 0, count: -scale)
          }
          var remainder = [0]
          for digit in dividend {
            remainder.append(digit)
            while remainder.count > 1 && remainder.first == 0 { remainder.removeFirst() }
            while remainder.count > divisor.count ||
              (remainder.count == divisor.count && !remainder.lexicographicallyPrecedes(divisor)) {
              var borrow = 0
              for offset in 0..<remainder.count {
                let index = remainder.count - 1 - offset
                let subtrahend = offset < divisor.count ? divisor[divisor.count - 1 - offset] : 0
                let difference = remainder[index] - subtrahend - borrow
                remainder[index] = (difference + 10) %% 10
                borrow = difference < 0 ? 1 : 0
              }
              while remainder.count > 1 && remainder.first == 0 { remainder.removeFirst() }
            }
          }
          return remainder.allSatisfy { ${'$'}0 == 0 }
        }
      }

      """.trimIndent(),
      DECIMAL,
    )
}
