package sdk

import sdk.primitive.*

// Definitions of syntax pattern follows cats library as an example
// https://github.com/typelevel/cats/blob/8d4cf2879df/core/src/main/scala/cats/syntax/all.scala

package object syntax {
  object all extends AllSyntax

  // Scala builtin/primitive types extensions
  object primitive extends ThrowableSyntax with TrySyntax with VoidSyntax
}
