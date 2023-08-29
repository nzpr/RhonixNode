package coop.rchain.shared

package object shared {
  // Importing syntax object means using all extensions in the project
  object syntax extends AllSyntaxShared
}

// Syntax for shared project
// TODO: unify syntax (extensions) with catscontrib,
//  one import per project similar as `import cats.syntax.all._`
trait AllSyntaxShared
    extends KeyValueStoreSyntax
    with KeyValueTypedStoreSyntax
    with KeyValueStoreManagerSyntax
    with Fs2StreamSyntax
    with catscontrib.ToBooleanF
    with MapSyntax
