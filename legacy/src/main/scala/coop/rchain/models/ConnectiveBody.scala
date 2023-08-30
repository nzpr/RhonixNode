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
final case class ConnectiveBody(
    ps: _root_.scala.Seq[coop.rchain.models.Par] = _root_.scala.Seq.empty
    ) extends coop.rchain.models.StacksafeMessage[ConnectiveBody] with scalapb.lenses.Updatable[ConnectiveBody] {
    
    override def equals(x: Any): Boolean = {
    
      import coop.rchain.catscontrib.effect.implicits.sEval
    
     coop.rchain.models.EqualM[coop.rchain.models.ConnectiveBody].equals[cats.Eval](this, x).value
    
    }
    
    override def hashCode(): Int = {
    
      import coop.rchain.catscontrib.effect.implicits.sEval
    
     coop.rchain.models.HashM[coop.rchain.models.ConnectiveBody].hash[cats.Eval](this).value
    
    }
    
    
    def mergeFromM[F[_]: cats.effect.Sync](`_input__`: _root_.com.google.protobuf.CodedInputStream): F[coop.rchain.models.ConnectiveBody] = {
      
      import cats.effect.Sync
      import cats.syntax.all._
      
      Sync[F].defer {
        val __ps = (new _root_.scala.collection.immutable.VectorBuilder[coop.rchain.models.Par] ++= this.ps)
        var _done__ = false
        
        Sync[F].whileM_ (Sync[F].delay { !_done__ }) {
          for {
            _tag__ <- Sync[F].delay { _input__.readTag() }
            _ <- _tag__ match {
              case 0 => Sync[F].delay { _done__ = true }
              case 10 =>
                for {
                  readValue       <- coop.rchain.models.SafeParser.readMessage(_input__, coop.rchain.models.Par.defaultInstance)
                  customTypeValue =  readValue
                  _               <- Sync[F].delay { __ps += customTypeValue }
                } yield ()
            case tag => Sync[F].delay { _input__.skipField(tag) }
            }
          } yield ()
        }
        .map { _ => coop.rchain.models.ConnectiveBody(
          ps = __ps.result()
        )}
      }
    }
    
    @transient
    private[this] var __serializedSizeMemoized: _root_.scala.Int = 0
    private[this] def __computeSerializedSize(): _root_.scala.Int = {
      var __size = 0
      ps.foreach { __item =>
        val __value = __item
        __size += 1 + _root_.com.google.protobuf.CodedOutputStream.computeUInt32SizeNoTag(__value.serializedSize) + __value.serializedSize
      }
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
      ps.foreach { __v =>
        val __m = __v
        _output__.writeTag(1, 2)
        _output__.writeUInt32NoTag(__m.serializedSize)
        __m.writeTo(_output__)
      };
    }
    def clearPs = copy(ps = _root_.scala.Seq.empty)
    def addPs(__vs: coop.rchain.models.Par *): ConnectiveBody = addAllPs(__vs)
    def addAllPs(__vs: Iterable[coop.rchain.models.Par]): ConnectiveBody = copy(ps = ps ++ __vs)
    def withPs(__v: _root_.scala.Seq[coop.rchain.models.Par]): ConnectiveBody = copy(ps = __v)
    def getFieldByNumber(__fieldNumber: _root_.scala.Int): _root_.scala.Any = {
      (__fieldNumber: @_root_.scala.unchecked) match {
        case 1 => ps
      }
    }
    def getField(__field: _root_.scalapb.descriptors.FieldDescriptor): _root_.scalapb.descriptors.PValue = {
      _root_.scala.Predef.require(__field.containingMessage eq companion.scalaDescriptor)
      (__field.number: @_root_.scala.unchecked) match {
        case 1 => _root_.scalapb.descriptors.PRepeated(ps.iterator.map(_.toPMessage).toVector)
      }
    }
    def toProtoString: _root_.scala.Predef.String = _root_.scalapb.TextFormat.printToUnicodeString(this)
    def companion: coop.rchain.models.ConnectiveBody.type = coop.rchain.models.ConnectiveBody
    // @@protoc_insertion_point(GeneratedMessage[ConnectiveBody])
}

object ConnectiveBody extends scalapb.GeneratedMessageCompanion[coop.rchain.models.ConnectiveBody] {
  implicit def messageCompanion: scalapb.GeneratedMessageCompanion[coop.rchain.models.ConnectiveBody] = this
  def parseFrom(`_input__`: _root_.com.google.protobuf.CodedInputStream): coop.rchain.models.ConnectiveBody = {
    val __ps: _root_.scala.collection.immutable.VectorBuilder[coop.rchain.models.Par] = new _root_.scala.collection.immutable.VectorBuilder[coop.rchain.models.Par]
    var _done__ = false
    while (!_done__) {
      val _tag__ = _input__.readTag()
      _tag__ match {
        case 0 => _done__ = true
        case 10 =>
          __ps += _root_.scalapb.LiteParser.readMessage[coop.rchain.models.Par](_input__)
        case tag => _input__.skipField(tag)
      }
    }
    coop.rchain.models.ConnectiveBody(
        ps = __ps.result()
    )
  }
  implicit def messageReads: _root_.scalapb.descriptors.Reads[coop.rchain.models.ConnectiveBody] = _root_.scalapb.descriptors.Reads{
    case _root_.scalapb.descriptors.PMessage(__fieldsMap) =>
      _root_.scala.Predef.require(__fieldsMap.keys.forall(_.containingMessage eq scalaDescriptor), "FieldDescriptor does not match message type.")
      coop.rchain.models.ConnectiveBody(
        ps = __fieldsMap.get(scalaDescriptor.findFieldByNumber(1).get).map(_.as[_root_.scala.Seq[coop.rchain.models.Par]]).getOrElse(_root_.scala.Seq.empty)
      )
    case _ => throw new RuntimeException("Expected PMessage")
  }
  def javaDescriptor: _root_.com.google.protobuf.Descriptors.Descriptor = RhoTypesProto.javaDescriptor.getMessageTypes().get(46)
  def scalaDescriptor: _root_.scalapb.descriptors.Descriptor = RhoTypesProto.scalaDescriptor.messages(46)
  def messageCompanionForFieldNumber(__number: _root_.scala.Int): _root_.scalapb.GeneratedMessageCompanion[_] = {
    var __out: _root_.scalapb.GeneratedMessageCompanion[_] = null
    (__number: @_root_.scala.unchecked) match {
      case 1 => __out = coop.rchain.models.Par
    }
    __out
  }
  lazy val nestedMessagesCompanions: Seq[_root_.scalapb.GeneratedMessageCompanion[_ <: _root_.scalapb.GeneratedMessage]] = Seq.empty
  def enumCompanionForFieldNumber(__fieldNumber: _root_.scala.Int): _root_.scalapb.GeneratedEnumCompanion[_] = throw new MatchError(__fieldNumber)
  lazy val defaultInstance = coop.rchain.models.ConnectiveBody(
    ps = _root_.scala.Seq.empty
  )
  implicit class ConnectiveBodyLens[UpperPB](_l: _root_.scalapb.lenses.Lens[UpperPB, coop.rchain.models.ConnectiveBody]) extends _root_.scalapb.lenses.ObjectLens[UpperPB, coop.rchain.models.ConnectiveBody](_l) {
    def ps: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Seq[coop.rchain.models.Par]] = field(_.ps)((c_, f_) => c_.copy(ps = f_))
  }
  final val PS_FIELD_NUMBER = 1
  def of(
    ps: _root_.scala.Seq[coop.rchain.models.Par]
  ): _root_.coop.rchain.models.ConnectiveBody = _root_.coop.rchain.models.ConnectiveBody(
    ps
  )
  // @@protoc_insertion_point(GeneratedMessageCompanion[ConnectiveBody])
}
