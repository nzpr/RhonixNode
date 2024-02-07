package sdk.reflect

import scala.reflect.runtime.universe.*

/** Render classes into configuration file for Typesafe Config */
object ClassesAsConfig {
  def apply(root: String, classes: Any*): String = classes
    .map { clz =>
      val rm             = runtimeMirror(clz.getClass.getClassLoader)
      val instanceMirror = rm.reflect(clz)
      val classSymbol    = instanceMirror.symbol.asClass
      val configName     = classSymbol.annotations.head.tree.children.tail.head match {
        case Literal(Constant(value: String)) => value
        case _                                => "Could not extract annotation string"
      }

      ClassAsTuple(clz)
        .map { case (name, value, anno) =>
          s"""|# $anno
              |$root.$configName.$name: $value
              |""".stripMargin
        }
        .mkString("")
    }
    .mkString(s"\n")
}
