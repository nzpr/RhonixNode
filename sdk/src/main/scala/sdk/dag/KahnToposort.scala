//package sdk.dag
//import cats.Parallel
//import cats.effect.Ref
//import cats.effect.kernel.Ref.Make
//import cats.effect.kernel.{Async, Deferred}
//import cats.syntax.all.*
//import fs2.Stream
//import sdk.syntax.all.*
//
//final case class KhanToposort[A](
//  dag: Map[A, List[A]],
//  indegreeMap: Map[A, Int],
//) {
//  def visited(a: A): (KhanToposort[A], List[A]) = {
//    val (newIndegreeMap, newOut) =
//      dag.getUnsafe(a).foldLeft(indegreeMap -> List.empty[A]) { case (curIndegree -> curOut) -> child =>
//        val newIndegree = curIndegree.getUnsafe(child) - 1
//        if (newIndegree > 0)
//          curIndegree.updated(child, newIndegree) -> curOut
//        else
//          (curIndegree - child)                   -> (child +: curOut)
//      }
//
//    copy(indegreeMap = newIndegreeMap) -> newOut
//  }
//}
//
//object KhanToposort {
//  def toposort[A](dag: KhanToposort[A], start: List[A]): LazyList[List[A]] =
//    LazyList
//      .unfold((dag, start)) { case (acc, step) =>
//        val (newAcc, out) = step.foldLeft((acc, List.empty[A])) { case ((acc1, out), x) =>
//          val (newAcc1, next) = acc1.visited(x)
//          (newAcc1, next ::: out)
//        }
//        out.nonEmpty.guard[Option].as(step -> (newAcc -> out))
//      }
//
//  def sorted[A](dag: KhanToposort[A], start: List[A]): List[A] =
//    toposort(dag, start).flatten.toList
//
//  def sortedOrdered[A: Ordering](dag: KhanToposort[A], start: List[A]): List[A] =
//    toposort(dag, start.sorted).flatMap(_.sorted).toList
//
//  /** Execute Kahn toposort with executing effects when visiting node. */
//  def parTraverseSorted[F[_]: Async: Parallel: Make, A, B](state: KhanToposort[A])(
//    start: List[A],   // starting nodes with indegree 0
//    effect: A => F[B],// effect executed when visiting node
//  ): Stream[F, B] = {
//    require(start.forall(state.indegreeMap(_) == 0), "Indegrees not zero")
//
//    final case class KhanParTraverse[A](
//      khan: KhanToposort[A],
//      visitsInProgress: Set[A],
//    ) {
//      def visit(a: A): (KhanParTraverse[A], List[A]) = {
//        val (newToposort, next) = khan.visited(a)
//        KhanParTraverse(newToposort, visitsInProgress - a) -> next
//      }
//    }
//
//    // state supporting traversal
//    val mkKhanStRef = Ref.of(KhanParTraverse(state, Set.empty[A]))
//
//    Stream
//      .eval(mkKhanStRef)
//      .flatMap { khanStRef =>
//        val workerQueueF = cats.effect.std.Queue.unbounded[F, A]
//
//        Stream.eval(workerQueueF).flatMap { case workerQueue =>
//          def executeVisit(node: A) = for {
//            // invoke effect
//            r    <- effect(node)
//            // update traversal state with visit completed
//            next <- khanStRef.modify(_.visit(node))
//            _    <- next.traverse(workerQueue.offer)
//          } yield r
//
//          Stream.eval(start.traverse(workerQueue.offer)) *>
//            Stream.fromQueueUnterminated(workerQueue, 1).parEvalMapUnorderedUnbounded(executeVisit)
//        }
//      }
//  }
//
//  /** Execute Kahn toposort with executing effects when visiting node. */
//  def parTraverseSorted[F[_]: Async: Parallel: Make, A, B](state: KhanToposort[A])(
//    start: List[A],      // starting nodes with indegree 0
//    effect: A => F[Unit],// effect executed when visiting node
//  ): Stream[F, A] = {
//    require(start.forall(state.indegreeMap(_) == 0), "Indegrees not zero")
//
//    final case class KhanParTraverse(
//      toposort: KhanToposort[A],
//      visitsInProgress: Map[A, Deferred[F, List[A]]],
//    ) {
//      def visited(a: A): (KhanParTraverse, (List[A], Deferred[F, List[A]])) = {
//        val (newToposort, next)     = toposort.visited(a)
//        val (newVisits, toComplete) = (visitsInProgress - a) -> visitsInProgress.getUnsafe(a)
//        KhanParTraverse(newToposort, newVisits) -> (next -> toComplete)
//      }
//    }
//
//    // state supporting traversal
//    val mkKhanStRef    = Ref.of(KhanParTraverse(state, Map.empty[A, Deferred[F, List[A]]]))
//    // state containing visits on progress
//    val mkVisitsMapRef = Ref.of(Map.empty[A, Deferred[F, List[A]]])
//
//    Stream
//      .eval((mkKhanStRef, mkVisitsMapRef).bisequence)
//      .flatMap { case (khanStRef, visitsMapRef) =>
//        val schedulerQueueF = cats.effect.std.Queue.unbounded[F, A]
//        val workerQueueF    = cats.effect.std.Queue.unbounded[F, A]
//
//        Stream.eval((schedulerQueueF, workerQueueF).bisequence).flatMap { case schedulerQueue -> workerQueue =>
//          val s1 = Stream
//            .fromQueueUnterminated(schedulerQueue, 1)
//            .evalMap(a => visit(a))
//            .parEvalMapUnorderedUnbounded { defToWait =>
//              for {
//                // wait for jobs to complete
//                r <- defToWait.get
//                // enqueue next jobs
//                _ <- r.traverse(schedulerQueue.offer)
//              } yield r
//            }
//
//          def visit(a: A): F[Deferred[F, List[A]]] = Deferred[F, List[A]].flatMap { newDef =>
//            visitsMapRef
//              .modify { s =>
//                val startJob = !s.contains(a)
//                val newS     = if (startJob) s.updated(a, newDef) else s
//                (newS, (startJob, if (startJob) newDef else s.getUnsafe(a)))
//              }
//              .flatMap { case (startJob, defToWait) =>
//                workerQueue.offer(a).whenA(startJob).as(defToWait)
//              }
//          }
//
//          def executeVisit(node: A) = for {
//            // invoke effect
//            r <- effect(node)
//            // update traversal state with visit completed
//            _ <- khanStRef.modify(_.visited(node)).flatMap { case unlocked -> visitDef =>
//                   // complete deferred awaiting for visit result
//                   visitDef.complete(unlocked)
//                 }
//          } yield r
//
//          val s2 = Stream.fromQueueUnterminated(workerQueue, 1).parEvalMapUnorderedUnbounded(executeVisit)
//
//          (s1 concurrently s2).flatMap(Stream.emits)
//        }
//      }
//  }
//
//}
