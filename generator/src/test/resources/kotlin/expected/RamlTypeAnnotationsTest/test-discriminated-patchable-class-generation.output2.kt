package io.test

import com.fasterxml.jackson.`annotation`.JsonInclude
import com.fasterxml.jackson.`annotation`.JsonTypeName
import io.outfoxx.sunday.json.patch.Patch
import io.outfoxx.sunday.json.patch.PatchOp
import io.outfoxx.sunday.json.patch.UpdateOp
import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.Unit

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonTypeName("child")
public class Child(
  public var child: UpdateOp<String> = PatchOp.none(),
) : Test(),
    Patch {
  override val type: TestType
    get() = TestType.Child

  override fun hashCode(): Int {
    var result = 31 * super.hashCode()
    result = 31 * result + child.hashCode()
    return result
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as Child

    if (child != other.child) return false

    return true
  }

  override fun toString(): String = """Child(child='$child')"""

  public companion object {
    public inline fun merge(`init`: Child.() -> Unit): PatchOp.Set<Child> {
      val patch = Child()
      patch.init()
      return PatchOp.Set(patch)
    }

    public inline fun patch(`init`: Child.() -> Unit): Child = merge(init).value
  }
}
