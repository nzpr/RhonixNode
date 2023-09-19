package sdk.syntax

import sdk.api.syntax.ApiFindSyntax
import sdk.store.{KeyValueStoreSyntax, KeyValueTypedStoreSyntax}
import sdk.primitive.*

trait AllSyntax
    extends ThrowableSyntax
    with TrySyntax
    with VoidSyntax
    with MapSyntax
    with ApiFindSyntax
    with KeyValueStoreSyntax
    with KeyValueTypedStoreSyntax
    with ByteBufferSyntax
    with ArrayByteSyntax
