//package sdk
//
//import cats.effect.{Async, Ref}
//import cats.syntax.all.*
//
//package object dag {
//  def parFoldM[F[_]: Async, A, B](dag: Dag[A], initB: B)(f: (A, B) => F[B]): F[B] = Ref[F].of(initB).flatMap { oRef =>
//    dag.zeroIndegree.tailRecM { case curNodes => curNodes.parTraverse() }
//  }
//}
