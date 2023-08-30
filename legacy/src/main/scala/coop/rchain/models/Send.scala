// Generated by the Scala Plugin for the Protocol Buffer Compiler.
// Do not edit!
//
// Protofile syntax: PROTO3

package coop.rchain.models
import coop.rchain.models.BitSetBytesMapper.bitSetBytesMapper
import coop.rchain.models.ParSetTypeMapper.parSetESetTypeMapper
import coop.rchain.models.ParMapTypeMapper.parMapEMapTypeMapper
import coop.rchain.models.BigIntTypeMapper.bigIntBytesTypeMapper
import coop.rchain.models.EqualMDerivation.gen
import coop.rchain.models.EqualMImplicits._

/** *
  * A send is written `chan!(data)` or `chan!!(data)` for a persistent send.
  *
  * Upon send, all free variables in data are substituted with their values.
  */
@SerialVersionUID(0L)
final case class Send(
    chan: coop.rchain.models.Par = coop.rchain.models.Par.defaultInstance,
    data: _root_.scala.Seq[coop.rchain.models.Par] = _root_.scala.Seq.empty,
    persistent: _root_.scala.Boolean = false,
    locallyFree: coop.rchain.models.AlwaysEqual[scala.collection.immutable.BitSet] = coop.rchain.models.Send._typemapper_locallyFree.toCustom(_root_.com.google.protobuf.ByteString.EMPTY),
    connectiveUsed: _root_.scala.Boolean = false
    ) extends coop.rchain.models.StacksafeMessage[Send] with scalapb.lenses.Updatable[Send] {
    
    override def equals(x: Any): Boolean = {
    
      import coop.rchain.catscontrib.effect.implicits.sEval
    
     coop.rchain.models.EqualM[coop.rchain.models.Send].equals[cats.Eval](this, x).value
    
    }
    
    override def hashCode(): Int = {
    
      import coop.rchain.catscontrib.effect.implicits.sEval
    
     coop.rchain.models.HashM[coop.rchain.models.Send].hash[cats.Eval](this).value
    
    }
    
    
    def mergeFromM[F[_]: cats.effect.Sync](`_input__`: _root_.com.google.protobuf.CodedInputStream): F[coop.rchain.models.Send] = {
      
      import cats.effect.Sync
      import cats.syntax.all._
      
      Sync[F].defer {
        var __chan = this.chan
        val __data = (new _root_.scala.collection.immutable.VectorBuilder[coop.rchain.models.Par] ++= this.data)
        var __persistent = this.persistent
        var __locallyFree = this.locallyFree
        var __connectiveUsed = this.connectiveUsed
        var _done__ = false
        
        Sync[F].whileM_ (Sync[F].delay { !_done__ }) {
          for {
            _tag__ <- Sync[F].delay { _input__.readTag() }
            _ <- _tag__ match {
              case 0 => Sync[F].delay { _done__ = true }
              case 10 =>
                for {
                  readValue       <- coop.rchain.models.SafeParser.readMessage(_input__, __chan)
                  customTypeValue =  readValue
                  _               <- Sync[F].delay { __chan = customTypeValue }
                } yield ()
              case 18 =>
                for {
                  readValue       <- coop.rchain.models.SafeParser.readMessage(_input__, coop.rchain.models.Par.defaultInstance)
                  customTypeValue =  readValue
                  _               <- Sync[F].delay { __data += customTypeValue }
                } yield ()
              case 24 =>
                for {
                  readValue       <- Sync[F].delay { _input__.readBool() }
                  customTypeValue =  readValue
                  _               <- Sync[F].delay { __persistent = customTypeValue }
                } yield ()
              case 42 =>
                for {
                  readValue       <- Sync[F].delay { _input__.readBytes() }
                  customTypeValue =  coop.rchain.models.Send._typemapper_locallyFree.toCustom(readValue)
                  _               <- Sync[F].delay { __locallyFree = customTypeValue }
                } yield ()
              case 48 =>
                for {
                  readValue       <- Sync[F].delay { _input__.readBool() }
                  customTypeValue =  readValue
                  _               <- Sync[F].delay { __connectiveUsed = customTypeValue }
                } yield ()
            case tag => Sync[F].delay { _input__.skipField(tag) }
            }
          } yield ()
        }
        .map { _ => coop.rchain.models.Send(
          chan = __chan,
          data = __data.result(),
          persistent = __persistent,
          locallyFree = __locallyFree,
          connectiveUsed = __connectiveUsed
        )}
      }
    }
    
    @transient
    private[this] var __serializedSizeMemoized: _root_.scala.Int = 0
    private[this] def __computeSerializedSize(): _root_.scala.Int = {
      var __size = 0
      
      {
        val __value = chan
        if (__value.serializedSize != 0) {
          __size += 1 + _root_.com.google.protobuf.CodedOutputStream.computeUInt32SizeNoTag(__value.serializedSize) + __value.serializedSize
        }
      };
      data.foreach { __item =>
        val __value = __item
        __size += 1 + _root_.com.google.protobuf.CodedOutputStream.computeUInt32SizeNoTag(__value.serializedSize) + __value.serializedSize
      }
      
      {
        val __value = persistent
        if (__value != false) {
          __size += _root_.com.google.protobuf.CodedOutputStream.computeBoolSize(3, __value)
        }
      };
      
      {
        val __value = coop.rchain.models.Send._typemapper_locallyFree.toBase(locallyFree)
        if (!__value.isEmpty) {
          __size += _root_.com.google.protobuf.CodedOutputStream.computeBytesSize(5, __value)
        }
      };
      
      {
        val __value = connectiveUsed
        if (__value != false) {
          __size += _root_.com.google.protobuf.CodedOutputStream.computeBoolSize(6, __value)
        }
      };
      __size
    }
    override def serializedSize: _root_.scala.Int = {
      var __size = __serializedSizeMemoized
      if (__size == 0) {
        __size = __computeSerializedSize() + 1
        __serializedSizeMemoized = __size
      }
      __size - 1
      
    }
    
    @transient var _serializedSizeM: coop.rchain.models.Memo[Int] = null
    
    def serializedSizeM: coop.rchain.models.Memo[Int] = synchronized {
      if(_serializedSizeM == null) {
        _serializedSizeM = new coop.rchain.models.Memo(coop.rchain.models.ProtoM.serializedSize(this))
        _serializedSizeM
      } else _serializedSizeM
    }
    def writeTo(`_output__`: _root_.com.google.protobuf.CodedOutputStream): _root_.scala.Unit = {
      {
        val __v = chan
        if (__v.serializedSize != 0) {
          _output__.writeTag(1, 2)
          _output__.writeUInt32NoTag(__v.serializedSize)
          __v.writeTo(_output__)
        }
      };
      data.foreach { __v =>
        val __m = __v
        _output__.writeTag(2, 2)
        _output__.writeUInt32NoTag(__m.serializedSize)
        __m.writeTo(_output__)
      };
      {
        val __v = persistent
        if (__v != false) {
          _output__.writeBool(3, __v)
        }
      };
      {
        val __v = coop.rchain.models.Send._typemapper_locallyFree.toBase(locallyFree)
        if (!__v.isEmpty) {
          _output__.writeBytes(5, __v)
        }
      };
      {
        val __v = connectiveUsed
        if (__v != false) {
          _output__.writeBool(6, __v)
        }
      };
    }
    def withChan(__v: coop.rchain.models.Par): Send = copy(chan = __v)
    def clearData = copy(data = _root_.scala.Seq.empty)
    def addData(__vs: coop.rchain.models.Par *): Send = addAllData(__vs)
    def addAllData(__vs: Iterable[coop.rchain.models.Par]): Send = copy(data = data ++ __vs)
    def withData(__v: _root_.scala.Seq[coop.rchain.models.Par]): Send = copy(data = __v)
    def withPersistent(__v: _root_.scala.Boolean): Send = copy(persistent = __v)
    def withLocallyFree(__v: coop.rchain.models.AlwaysEqual[scala.collection.immutable.BitSet]): Send = copy(locallyFree = __v)
    def withConnectiveUsed(__v: _root_.scala.Boolean): Send = copy(connectiveUsed = __v)
    def getFieldByNumber(__fieldNumber: _root_.scala.Int): _root_.scala.Any = {
      (__fieldNumber: @_root_.scala.unchecked) match {
        case 1 => {
          val __t = chan
          if (__t != coop.rchain.models.Par.defaultInstance) __t else null
        }
        case 2 => data
        case 3 => {
          val __t = persistent
          if (__t != false) __t else null
        }
        case 5 => {
          val __t = coop.rchain.models.Send._typemapper_locallyFree.toBase(locallyFree)
          if (__t != _root_.com.google.protobuf.ByteString.EMPTY) __t else null
        }
        case 6 => {
          val __t = connectiveUsed
          if (__t != false) __t else null
        }
      }
    }
    def getField(__field: _root_.scalapb.descriptors.FieldDescriptor): _root_.scalapb.descriptors.PValue = {
      _root_.scala.Predef.require(__field.containingMessage eq companion.scalaDescriptor)
      (__field.number: @_root_.scala.unchecked) match {
        case 1 => chan.toPMessage
        case 2 => _root_.scalapb.descriptors.PRepeated(data.iterator.map(_.toPMessage).toVector)
        case 3 => _root_.scalapb.descriptors.PBoolean(persistent)
        case 5 => _root_.scalapb.descriptors.PByteString(coop.rchain.models.Send._typemapper_locallyFree.toBase(locallyFree))
        case 6 => _root_.scalapb.descriptors.PBoolean(connectiveUsed)
      }
    }
    def toProtoString: _root_.scala.Predef.String = _root_.scalapb.TextFormat.printToUnicodeString(this)
    def companion: coop.rchain.models.Send.type = coop.rchain.models.Send
    // @@protoc_insertion_point(GeneratedMessage[Send])
}

object Send extends scalapb.GeneratedMessageCompanion[coop.rchain.models.Send] {
  implicit def messageCompanion: scalapb.GeneratedMessageCompanion[coop.rchain.models.Send] = this
  def parseFrom(`_input__`: _root_.com.google.protobuf.CodedInputStream): coop.rchain.models.Send = {
    var __chan: _root_.scala.Option[coop.rchain.models.Par] = _root_.scala.None
    val __data: _root_.scala.collection.immutable.VectorBuilder[coop.rchain.models.Par] = new _root_.scala.collection.immutable.VectorBuilder[coop.rchain.models.Par]
    var __persistent: _root_.scala.Boolean = false
    var __locallyFree: _root_.com.google.protobuf.ByteString = _root_.com.google.protobuf.ByteString.EMPTY
    var __connectiveUsed: _root_.scala.Boolean = false
    var _done__ = false
    while (!_done__) {
      val _tag__ = _input__.readTag()
      _tag__ match {
        case 0 => _done__ = true
        case 10 =>
          __chan = _root_.scala.Some(__chan.fold(_root_.scalapb.LiteParser.readMessage[coop.rchain.models.Par](_input__))(_root_.scalapb.LiteParser.readMessage(_input__, _)))
        case 18 =>
          __data += _root_.scalapb.LiteParser.readMessage[coop.rchain.models.Par](_input__)
        case 24 =>
          __persistent = _input__.readBool()
        case 42 =>
          __locallyFree = _input__.readBytes()
        case 48 =>
          __connectiveUsed = _input__.readBool()
        case tag => _input__.skipField(tag)
      }
    }
    coop.rchain.models.Send(
        chan = __chan.getOrElse(coop.rchain.models.Par.defaultInstance),
        data = __data.result(),
        persistent = __persistent,
        locallyFree = coop.rchain.models.Send._typemapper_locallyFree.toCustom(__locallyFree),
        connectiveUsed = __connectiveUsed
    )
  }
  implicit def messageReads: _root_.scalapb.descriptors.Reads[coop.rchain.models.Send] = _root_.scalapb.descriptors.Reads{
    case _root_.scalapb.descriptors.PMessage(__fieldsMap) =>
      _root_.scala.Predef.require(__fieldsMap.keys.forall(_.containingMessage eq scalaDescriptor), "FieldDescriptor does not match message type.")
      coop.rchain.models.Send(
        chan = __fieldsMap.get(scalaDescriptor.findFieldByNumber(1).get).map(_.as[coop.rchain.models.Par]).getOrElse(coop.rchain.models.Par.defaultInstance),
        data = __fieldsMap.get(scalaDescriptor.findFieldByNumber(2).get).map(_.as[_root_.scala.Seq[coop.rchain.models.Par]]).getOrElse(_root_.scala.Seq.empty),
        persistent = __fieldsMap.get(scalaDescriptor.findFieldByNumber(3).get).map(_.as[_root_.scala.Boolean]).getOrElse(false),
        locallyFree = coop.rchain.models.Send._typemapper_locallyFree.toCustom(__fieldsMap.get(scalaDescriptor.findFieldByNumber(5).get).map(_.as[_root_.com.google.protobuf.ByteString]).getOrElse(_root_.com.google.protobuf.ByteString.EMPTY)),
        connectiveUsed = __fieldsMap.get(scalaDescriptor.findFieldByNumber(6).get).map(_.as[_root_.scala.Boolean]).getOrElse(false)
      )
    case _ => throw new RuntimeException("Expected PMessage")
  }
  def javaDescriptor: _root_.com.google.protobuf.Descriptors.Descriptor = RhoTypesProto.javaDescriptor.getMessageTypes().get(7)
  def scalaDescriptor: _root_.scalapb.descriptors.Descriptor = RhoTypesProto.scalaDescriptor.messages(7)
  def messageCompanionForFieldNumber(__number: _root_.scala.Int): _root_.scalapb.GeneratedMessageCompanion[_] = {
    var __out: _root_.scalapb.GeneratedMessageCompanion[_] = null
    (__number: @_root_.scala.unchecked) match {
      case 1 => __out = coop.rchain.models.Par
      case 2 => __out = coop.rchain.models.Par
    }
    __out
  }
  lazy val nestedMessagesCompanions: Seq[_root_.scalapb.GeneratedMessageCompanion[_ <: _root_.scalapb.GeneratedMessage]] = Seq.empty
  def enumCompanionForFieldNumber(__fieldNumber: _root_.scala.Int): _root_.scalapb.GeneratedEnumCompanion[_] = throw new MatchError(__fieldNumber)
  lazy val defaultInstance = coop.rchain.models.Send(
    chan = coop.rchain.models.Par.defaultInstance,
    data = _root_.scala.Seq.empty,
    persistent = false,
    locallyFree = coop.rchain.models.Send._typemapper_locallyFree.toCustom(_root_.com.google.protobuf.ByteString.EMPTY),
    connectiveUsed = false
  )
  implicit class SendLens[UpperPB](_l: _root_.scalapb.lenses.Lens[UpperPB, coop.rchain.models.Send]) extends _root_.scalapb.lenses.ObjectLens[UpperPB, coop.rchain.models.Send](_l) {
    def chan: _root_.scalapb.lenses.Lens[UpperPB, coop.rchain.models.Par] = field(_.chan)((c_, f_) => c_.copy(chan = f_))
    def data: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Seq[coop.rchain.models.Par]] = field(_.data)((c_, f_) => c_.copy(data = f_))
    def persistent: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Boolean] = field(_.persistent)((c_, f_) => c_.copy(persistent = f_))
    def locallyFree: _root_.scalapb.lenses.Lens[UpperPB, coop.rchain.models.AlwaysEqual[scala.collection.immutable.BitSet]] = field(_.locallyFree)((c_, f_) => c_.copy(locallyFree = f_))
    def connectiveUsed: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Boolean] = field(_.connectiveUsed)((c_, f_) => c_.copy(connectiveUsed = f_))
  }
  final val CHAN_FIELD_NUMBER = 1
  final val DATA_FIELD_NUMBER = 2
  final val PERSISTENT_FIELD_NUMBER = 3
  final val LOCALLYFREE_FIELD_NUMBER = 5
  final val CONNECTIVE_USED_FIELD_NUMBER = 6
  @transient
  private[models] val _typemapper_locallyFree: _root_.scalapb.TypeMapper[_root_.com.google.protobuf.ByteString, coop.rchain.models.AlwaysEqual[scala.collection.immutable.BitSet]] = implicitly[_root_.scalapb.TypeMapper[_root_.com.google.protobuf.ByteString, coop.rchain.models.AlwaysEqual[scala.collection.immutable.BitSet]]]
  def of(
    chan: coop.rchain.models.Par,
    data: _root_.scala.Seq[coop.rchain.models.Par],
    persistent: _root_.scala.Boolean,
    locallyFree: coop.rchain.models.AlwaysEqual[scala.collection.immutable.BitSet],
    connectiveUsed: _root_.scala.Boolean
  ): _root_.coop.rchain.models.Send = _root_.coop.rchain.models.Send(
    chan,
    data,
    persistent,
    locallyFree,
    connectiveUsed
  )
  // @@protoc_insertion_point(GeneratedMessageCompanion[Send])
}
