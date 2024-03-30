package coop.rchain.rholang.interpreter

final case class Env[A](envMap: Map[Int, A], level: Int, shift: Int) {
  def put(a: A): Env[A] =
    Env(envMap + (level -> a), level + 1, shift)

  def get(k: Int): Option[A] = envMap.get(k)

  def shift(j: Int): Env[A] =
    this.copy(shift = shift + j)
}

object Env {
  def apply[A]() = new Env[A](Map.empty[Int, A], level = 0, shift = 0)

  def makeEnv[A](k: A*): Env[A] = k.foldLeft(Env[A]())((acc, newVal) => acc.put(newVal))
}
