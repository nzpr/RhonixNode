package io.rhonix.rholang.normalizer

import cats.Applicative
import cats.syntax.all.*
import io.rhonix.rholang.EMethodN
import io.rhonix.rholang.ast.rholang.Absyn.PMethod

import scala.jdk.CollectionConverters.CollectionHasAsScala

object MethodNormalizer {
  def normalizeMethod[F[_]: Applicative: NormalizerRec](p: PMethod): F[EMethodN] =
    (NormalizerRec[F].normalize(p.proc_), p.listproc_.asScala.toList.traverse(NormalizerRec[F].normalize))
      .mapN((target, args) => EMethodN(target, p.var_, args))
}
