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

@SerialVersionUID(0L)
final case class KeyValuePair(
    key: coop.rchain.models.Par = coop.rchain.models.Par.defaultInstance,
    value: coop.rchain.models.Par = coop.rchain.models.Par.defaultInstance
    ) extends coop.rchain.models.StacksafeMessage[KeyValuePair] with scalapb.lenses.Updatable[KeyValuePair] {
    
    override def equals(x: Any): Boolean = {
    
      import coop.rchain.catscontrib.effect.implicits.sEval
    
     coop.rchain.models.EqualM[coop.rchain.models.KeyValuePair].equals[cats.Eval](this, x).value
    
    }
    
    override def hashCode(): Int = {
    
      import coop.rchain.catscontrib.effect.implicits.sEval
    
     coop.rchain.models.HashM[coop.rchain.models.KeyValuePair].hash[cats.Eval](this).value
    
    }
    
    
    def mergeFromM[F[_]: cats.effect.Sync](`_input__`: _root_.com.google.protobuf.CodedInputStream): F[coop.rchain.models.KeyValuePair] = {
      
      import cats.effect.Sync
      import cats.syntax.all._
      
      Sync[F].defer {
        var __key = this.key
        var __value = this.value
        var _done__ = false
        
        Sync[F].whileM_ (Sync[F].delay { !_done__ }) {
          for {
            _tag__ <- Sync[F].delay { _input__.readTag() }
            _ <- _tag__ match {
              case 0 => Sync[F].delay { _done__ = true }
              case 10 =>
                for {
                  readValue       <- coop.rchain.models.SafeParser.readMessage(_input__, __key)
                  customTypeValue =  readValue
                  _               <- Sync[F].delay { __key = customTypeValue }
                } yield ()
              case 18 =>
                for {
                  readValue       <- coop.rchain.models.SafeParser.readMessage(_input__, __value)
                  customTypeValue =  readValue
                  _               <- Sync[F].delay { __value = customTypeValue }
                } yield ()
            case tag => Sync[F].delay { _input__.skipField(tag) }
            }
          } yield ()
        }
        .map { _ => coop.rchain.models.KeyValuePair(
          key = __key,
          value = __value
        )}
      }
    }
    
    @transient
    private[this] var __serializedSizeMemoized: _root_.scala.Int = 0
    private[this] def __computeSerializedSize(): _root_.scala.Int = {
      var __size = 0
      
      {
        val __value = key
        if (__value.serializedSize != 0) {
          __size += 1 + _root_.com.google.protobuf.CodedOutputStream.computeUInt32SizeNoTag(__value.serializedSize) + __value.serializedSize
        }
      };
      
      {
        val __value = value
        if (__value.serializedSize != 0) {
          __size += 1 + _root_.com.google.protobuf.CodedOutputStream.computeUInt32SizeNoTag(__value.serializedSize) + __value.serializedSize
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
        val __v = key
        if (__v.serializedSize != 0) {
          _output__.writeTag(1, 2)
          _output__.writeUInt32NoTag(__v.serializedSize)
          __v.writeTo(_output__)
        }
      };
      {
        val __v = value
        if (__v.serializedSize != 0) {
          _output__.writeTag(2, 2)
          _output__.writeUInt32NoTag(__v.serializedSize)
          __v.writeTo(_output__)
        }
      };
    }
    def withKey(__v: coop.rchain.models.Par): KeyValuePair = copy(key = __v)
    def withValue(__v: coop.rchain.models.Par): KeyValuePair = copy(value = __v)
    def getFieldByNumber(__fieldNumber: _root_.scala.Int): _root_.scala.Any = {
      (__fieldNumber: @_root_.scala.unchecked) match {
        case 1 => {
          val __t = key
          if (__t != coop.rchain.models.Par.defaultInstance) __t else null
        }
        case 2 => {
          val __t = value
          if (__t != coop.rchain.models.Par.defaultInstance) __t else null
        }
      }
    }
    def getField(__field: _root_.scalapb.descriptors.FieldDescriptor): _root_.scalapb.descriptors.PValue = {
      _root_.scala.Predef.require(__field.containingMessage eq companion.scalaDescriptor)
      (__field.number: @_root_.scala.unchecked) match {
        case 1 => key.toPMessage
        case 2 => value.toPMessage
      }
    }
    def toProtoString: _root_.scala.Predef.String = _root_.scalapb.TextFormat.printToUnicodeString(this)
    def companion: coop.rchain.models.KeyValuePair.type = coop.rchain.models.KeyValuePair
    // @@protoc_insertion_point(GeneratedMessage[KeyValuePair])
}

object KeyValuePair extends scalapb.GeneratedMessageCompanion[coop.rchain.models.KeyValuePair] {
  implicit def messageCompanion: scalapb.GeneratedMessageCompanion[coop.rchain.models.KeyValuePair] = this
  def parseFrom(`_input__`: _root_.com.google.protobuf.CodedInputStream): coop.rchain.models.KeyValuePair = {
    var __key: _root_.scala.Option[coop.rchain.models.Par] = _root_.scala.None
    var __value: _root_.scala.Option[coop.rchain.models.Par] = _root_.scala.None
    var _done__ = false
    while (!_done__) {
      val _tag__ = _input__.readTag()
      _tag__ match {
        case 0 => _done__ = true
        case 10 =>
          __key = _root_.scala.Some(__key.fold(_root_.scalapb.LiteParser.readMessage[coop.rchain.models.Par](_input__))(_root_.scalapb.LiteParser.readMessage(_input__, _)))
        case 18 =>
          __value = _root_.scala.Some(__value.fold(_root_.scalapb.LiteParser.readMessage[coop.rchain.models.Par](_input__))(_root_.scalapb.LiteParser.readMessage(_input__, _)))
        case tag => _input__.skipField(tag)
      }
    }
    coop.rchain.models.KeyValuePair(
        key = __key.getOrElse(coop.rchain.models.Par.defaultInstance),
        value = __value.getOrElse(coop.rchain.models.Par.defaultInstance)
    )
  }
  implicit def messageReads: _root_.scalapb.descriptors.Reads[coop.rchain.models.KeyValuePair] = _root_.scalapb.descriptors.Reads{
    case _root_.scalapb.descriptors.PMessage(__fieldsMap) =>
      _root_.scala.Predef.require(__fieldsMap.keys.forall(_.containingMessage eq scalaDescriptor), "FieldDescriptor does not match message type.")
      coop.rchain.models.KeyValuePair(
        key = __fieldsMap.get(scalaDescriptor.findFieldByNumber(1).get).map(_.as[coop.rchain.models.Par]).getOrElse(coop.rchain.models.Par.defaultInstance),
        value = __fieldsMap.get(scalaDescriptor.findFieldByNumber(2).get).map(_.as[coop.rchain.models.Par]).getOrElse(coop.rchain.models.Par.defaultInstance)
      )
    case _ => throw new RuntimeException("Expected PMessage")
  }
  def javaDescriptor: _root_.com.google.protobuf.Descriptors.Descriptor = RhoTypesProto.javaDescriptor.getMessageTypes().get(21)
  def scalaDescriptor: _root_.scalapb.descriptors.Descriptor = RhoTypesProto.scalaDescriptor.messages(21)
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
  lazy val defaultInstance = coop.rchain.models.KeyValuePair(
    key = coop.rchain.models.Par.defaultInstance,
    value = coop.rchain.models.Par.defaultInstance
  )
  implicit class KeyValuePairLens[UpperPB](_l: _root_.scalapb.lenses.Lens[UpperPB, coop.rchain.models.KeyValuePair]) extends _root_.scalapb.lenses.ObjectLens[UpperPB, coop.rchain.models.KeyValuePair](_l) {
    def key: _root_.scalapb.lenses.Lens[UpperPB, coop.rchain.models.Par] = field(_.key)((c_, f_) => c_.copy(key = f_))
    def value: _root_.scalapb.lenses.Lens[UpperPB, coop.rchain.models.Par] = field(_.value)((c_, f_) => c_.copy(value = f_))
  }
  final val KEY_FIELD_NUMBER = 1
  final val VALUE_FIELD_NUMBER = 2
  def of(
    key: coop.rchain.models.Par,
    value: coop.rchain.models.Par
  ): _root_.coop.rchain.models.KeyValuePair = _root_.coop.rchain.models.KeyValuePair(
    key,
    value
  )
  // @@protoc_insertion_point(GeneratedMessageCompanion[KeyValuePair])
}
