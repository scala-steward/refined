package eu.timepit.refined
package macros

import eu.timepit.refined.api.{RefType, Validate}
import eu.timepit.refined.internal.Resources
import macrocompat.bundle
import scala.reflect.macros.blackbox

@bundle
class RefineMacro(val c: blackbox.Context) extends MacroUtils {
  import c.universe._

  def impl[F[_, _], T: c.WeakTypeTag, P: c.WeakTypeTag](t: c.Expr[T])(
      rt: c.Expr[RefType[F]],
      v: c.Expr[Validate[T, P]]
  ): c.Expr[F[T, P]] = {
    val tValue: T = extractConstant(t).getOrElse {
      abort(Resources.refineNonCompileTimeConstant)
    }
    refineConstant(t, tValue)(rt, v)
  }

  def implApplyRef[FTP, F[_, _], T, P](t: c.Expr[T])(
      ev: c.Expr[F[T, P] =:= FTP],
      rt: c.Expr[RefType[F]],
      v: c.Expr[Validate[T, P]]
  ): c.Expr[FTP] =
    c.Expr(impl(t)(rt, v).tree)

  def unsafeFrom[F[_, _], T, P](t: c.Expr[T])(rt: c.Expr[RefType[F]], v: c.Expr[Validate[T, P]]): c.Expr[F[T, P]] = {
    extractConstant(t) match {
      case Some(tValue) => refineConstant(t, tValue)(rt, v)
      case None => reify(functions.refineUnsafe(t.splice)(rt.splice, v.splice))
    }
  }

  def extractConstant[T](t: c.Expr[T]): Option[T] =
    t.tree match {
      case Literal(Constant(value)) => Some(value.asInstanceOf[T])
      case _ => None
    }

  def refineConstant[F[_, _], T: c.WeakTypeTag, P: c.WeakTypeTag](t: c.Expr[T], tValue: T)(
    rt: c.Expr[RefType[F]], v: c.Expr[Validate[T, P]]
  ): c.Expr[F[T, P]] = {
    val validate = eval(v)
    val res = validate.validate(tValue)
    if (res.isFailed) {
      abort(validate.showResult(tValue, res))
    }

    val refType = eval(rt)
    refType.unsafeWrapM(c)(t)
  }
}