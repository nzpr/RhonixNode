package sdk.dag

final case class Dag[A](children: A => List[A], zeroIndegree: List[A]) {}
