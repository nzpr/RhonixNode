package sdk.store

package object syntax {
  object all extends KeyValueStoreSyntax with KeyValueTypedStoreSyntax with KeyValueStoreManagerSyntax
}
