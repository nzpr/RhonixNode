package node.comm

import cats.Eval
import com.google.protobuf.CodedOutputStream
import sdk.codecs.protobuf.{ProtoPrimitiveReader, ProtoPrimitiveWriter}
import sdk.codecs.{PrimitiveReader, PrimitiveWriter}

import java.io.{InputStream, PipedInputStream, PipedOutputStream}

// TODO remove, put in node.Serialize what is required, or in sdk if not protobuf related
object Serialize {
  def encode[A](obj: A, encodeF: (A, PrimitiveWriter[Eval]) => Eval[Unit]): InputStream = {
    val pipeInput   = new PipedInputStream
    val pipeOut     = new PipedOutputStream(pipeInput)
    val protoStream = CodedOutputStream.newInstance(pipeOut)

    val writer = ProtoPrimitiveWriter(protoStream)
    encodeF(obj, writer).value

    protoStream.flush()
    pipeOut.flush()
    pipeOut.close()
    pipeInput
  }

  def decode[A](stream: InputStream, decodeF: PrimitiveReader[Eval] => Eval[A]): A = {
    val reader = ProtoPrimitiveReader(stream)
    decodeF(reader).value
  }
}
