package coop.rchain.rholang.interpreter

final case class Env[A](envMap: Map[Int, A], level: Int) {
  def put(a: A): Env[A]      = Env(envMap + (level -> a), level + 1)
  def get(k: Int): Option[A] = envMap.get(k)
}

object Env {
  def apply[A]() = new Env[A](Map.empty[Int, A], level = 0)

  def makeEnv[A](k: A*): Env[A] = k.foldLeft(Env[A]())((acc, newVal) => acc.put(newVal))
}
