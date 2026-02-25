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
public class Child(
  string: UpdateOp<String> = PatchOp.none(),
  int: UpdateOp<Int> = PatchOp.none(),
  bool: UpdateOp<Boolean> = PatchOp.none(),
  nullable: PatchOp<String> = PatchOp.none(),
  optional: UpdateOp<String> = PatchOp.none(),
  nullableOptional: PatchOp<String> = PatchOp.none(),
  public var child: UpdateOp<String> = PatchOp.none(),
) : Test(string, int, bool, nullable, optional, nullableOptional),
    Patch {
  override fun hashCode(): Int {
    var result = 31 * super.hashCode()
    result = 31 * result + child.hashCode()
    return result
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as Child

    if (string != other.string) return false
    if (int != other.int) return false
    if (bool != other.bool) return false
    if (nullable != other.nullable) return false
    if (optional != other.optional) return false
    if (nullableOptional != other.nullableOptional) return false
    if (child != other.child) return false

    return true
  }

  override fun toString(): String = """
  |Child(string='$string',
  | int='$int',
  | bool='$bool',
  | nullable='$nullable',
  | optional='$optional',
  | nullableOptional='$nullableOptional',
  | child='$child')
  """.trimMargin()

  public companion object {
    public inline fun merge(`init`: Child.() -> Unit): PatchOp.Set<Child> {
      val patch = Child()
      patch.init()
      return PatchOp.Set(patch)
    }

    public inline fun patch(`init`: Child.() -> Unit): Child = merge(init).value
  }
}
