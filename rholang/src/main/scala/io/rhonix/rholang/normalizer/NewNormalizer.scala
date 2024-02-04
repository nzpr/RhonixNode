package io.rhonix.rholang.normalizer

import cats.effect.Sync
import cats.syntax.all.*
import io.rhonix.rholang.*
import io.rhonix.rholang.ast.rholang.Absyn.*
import io.rhonix.rholang.interpreter.SourcePosition
import io.rhonix.rholang.interpreter.compiler.{NameSort, VarSort}
import io.rhonix.rholang.normalizer.GroundNormalizer.stripUri
import io.rhonix.rholang.normalizer.env.{BoundVarScope, BoundVarWriter, *}
import io.rhonix.rholang.normalizer.syntax.all.*

import scala.jdk.CollectionConverters.*

object NewNormalizer {
  def normalizeNew[F[_]: Sync: NormalizerRec: BoundVarScope, T >: VarSort: BoundVarWriter](p: PNew): F[NewN] =
    Sync[F].defer {
      val simpleBindings = p.listnamedecl_.asScala.toSeq.collect { case n: NameDeclSimpl =>
        (n.var_, NameSort, SourcePosition(n.line_num, n.col_num))
      } // Unsorted simple bindings

      val sortedUrnData = p.listnamedecl_.asScala.toSeq
        .collect { case n: NameDeclUrn =>
          (
            stripUri(n.uriliteral_),
            (n.var_, NameSort, SourcePosition(n.line_num, n.col_num)),
          )
        }
        .sortBy(_._1) // Sort by uris in lexicographical order

      val (uris, urnBindings) = sortedUrnData.unzip

      val boundVars = simpleBindings ++ urnBindings

      NormalizerRec[F]
        .normalize(p.proc_)
        .withAddedBoundVars[T](boundVars)
        .map { case (normalizedPar, indices) =>
          NewN(bindCount = indices.size, p = normalizedPar, uri = uris, injections = Map[String, ParN]())
        }
    }
}
