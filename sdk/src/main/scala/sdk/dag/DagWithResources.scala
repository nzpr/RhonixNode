package sdk.dag

final case class DagWithResources[A, B](dag: Dag[A], resourceMap: A => B) {}
