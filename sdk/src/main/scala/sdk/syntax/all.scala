package sdk.syntax

import sdk.api.syntax.ApiFindSyntax
import sdk.diag.MetricsWriterSdkSyntax
import sdk.fs2.Fs2StreamSyntax
import sdk.primitive.*
import sdk.store.syntax.{KeyValueStoreManagerSyntax, KeyValueStoreSyntax, KeyValueTypedStoreSyntax}

trait AllSyntax
    extends ThrowableSyntax
    with TrySyntax
    with VoidSyntax
    with MapSyntax
    with ApiFindSyntax
    with KeyValueStoreSyntax
    with KeyValueTypedStoreSyntax
    with KeyValueStoreManagerSyntax
    with ByteBufferSyntax
    with ArrayByteSyntax
    with ByteArraySyntax
    with EffectSyntax
    with Fs2StreamSyntax
    with MetricsWriterSdkSyntax
