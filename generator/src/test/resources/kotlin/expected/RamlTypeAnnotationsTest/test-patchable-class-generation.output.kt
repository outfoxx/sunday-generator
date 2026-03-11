package io.test

import com.fasterxml.jackson.`annotation`.JsonInclude
import io.outfoxx.sunday.json.patch.Patch
import io.outfoxx.sunday.json.patch.PatchOp
import io.outfoxx.sunday.json.patch.UpdateOp
import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.Unit

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public open class Test(
  public var string: UpdateOp<String> = PatchOp.none(),
  public var int: UpdateOp<Int> = PatchOp.none(),
  public var bool: UpdateOp<Boolean> = PatchOp.none(),
  public var nullable: PatchOp<String> = PatchOp.none(),
  public var optional: UpdateOp<String> = PatchOp.none(),
  public var nullableOptional: PatchOp<String> = PatchOp.none(),
) : Patch {
  override fun hashCode(): Int {
    var result = 1
    result = 31 * result + string.hashCode()
    result = 31 * result + int.hashCode()
    result = 31 * result + bool.hashCode()
    result = 31 * result + nullable.hashCode()
    result = 31 * result + optional.hashCode()
    result = 31 * result + nullableOptional.hashCode()
    return result
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as Test

    if (string != other.string) return false
    if (int != other.int) return false
    if (bool != other.bool) return false
    if (nullable != other.nullable) return false
    if (optional != other.optional) return false
    if (nullableOptional != other.nullableOptional) return false

    return true
  }

  override fun toString(): String = """
  |Test(string='$string',
  | int='$int',
  | bool='$bool',
  | nullable='$nullable',
  | optional='$optional',
  | nullableOptional='$nullableOptional')
  """.trimMargin()

  public companion object {
    public inline fun merge(`init`: Test.() -> Unit): PatchOp.Set<Test> {
      val patch = Test()
      patch.init()
      return PatchOp.Set(patch)
    }

    public inline fun patch(`init`: Test.() -> Unit): Test = merge(init).value
  }
}
