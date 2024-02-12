package coop.rchain.rholang.interpreter.compiler

import cats.effect.Sync
import cats.syntax.all.*
import coop.rchain.models.Par
import coop.rchain.models.rholang.sorter.Sortable
import coop.rchain.rholang.interpreter.errors.*
import io.rhonix.rholang.Bindings.*
import io.rhonix.rholang.ast.rholang.Absyn.Proc
import io.rhonix.rholang.ast.rholang.{parser, Yylex}
import io.rhonix.rholang.normalizer.NormalizerRecImpl
import io.rhonix.rholang.normalizer.env.*
import io.rhonix.rholang.normalizer.envimpl.*
import io.rhonix.rholang.types.ParN

import java.io.{Reader, StringReader}

trait Compiler[F[_]] {

  def sourceToADT(source: String): F[Par] =
    sourceToADT(source, Map.empty[String, Par])

  def sourceToADT(source: String, normalizerEnv: Map[String, Par]): F[Par] =
    sourceToADT(new StringReader(source), normalizerEnv)

  def sourceToADT(reader: Reader): F[Par] =
    sourceToADT(reader, Map.empty[String, Par])

  def sourceToADT(reader: Reader, normalizerEnv: Map[String, Par]): F[Par]

  def astToADT(proc: Proc): F[Par] =
    astToADT(proc, Map.empty[String, Par])

  def astToADT(proc: Proc, normalizerEnv: Map[String, Par]): F[Par]

  def sourceToAST(source: String): F[Proc] =
    sourceToAST(new StringReader(source))

  def sourceToAST(reader: Reader): F[Proc]

}

object Compiler {

  def apply[F[_]](implicit compiler: Compiler[F]): Compiler[F] = compiler

  implicit def parBuilder[F[_]](implicit F: Sync[F]): Compiler[F] = new Compiler[F] {

    def sourceToADT(reader: Reader, normalizerEnv: Map[String, Par]): F[Par] =
      for {
        proc <- sourceToAST(reader)
        par  <- astToADT(proc, normalizerEnv)
      } yield par

    def astToADT(proc: Proc, normalizerEnv: Map[String, Par]): F[Par] =
      for {
        par       <- normalizeTerm(proc)(normalizerEnv)
        sortedPar <- Sortable[Par].sortMatch(par)
      } yield sortedPar.term

    def sourceToAST(reader: Reader): F[Proc] =
      for {
        lexer  <- lexer(reader)
        parser <- parser(lexer)
        proc   <- F.delay(parser.pProc()).adaptError {
                    case ex: SyntaxError                                                                   =>
                      ex
                    case ex: Exception if ex.getMessage.startsWith("Syntax error")                         =>
                      SyntaxError(ex.getMessage)
                    case er: Error if er.getMessage.startsWith("Unterminated string at EOF, beginning at") =>
                      LexerError(er.getMessage)
                    case er: Error if er.getMessage.startsWith("Illegal Character")                        =>
                      LexerError(er.getMessage)
                    case er: Error if er.getMessage.startsWith("Unterminated string on line")              =>
                      LexerError(er.getMessage)
                    case th: Throwable                                                                     => UnrecognizedInterpreterError(th)
                  }
      } yield proc

    private def normalizeTerm(term: Proc)(implicit normalizerEnv: Map[String, Par]): F[Par] = {

      // TODO: The normalizerEnv should be utilized for creating injections during the New normalization process,
      //  although it is currently unused.
      val _ = normalizerEnv

      val boundMapChain                                    = VarMapChain.empty[F, VarSort]
      // TODO: Utilized get methods with index inversion as it functions equivalently to the legacy implementation.
      //  This approach ensures compatibility with the legacy reducer and associated tests.
      //  However, it should be rewritten using non-inverted methods following the completion of reducer rewriting.
      implicit val boundVarWriter: BoundVarWriter[VarSort] = BoundVarWriterImpl(boundMapChain.putVarInverted)
      implicit val boundVarReader: BoundVarReader[VarSort] =
        BoundVarReaderImpl(boundMapChain.getVarInverted, boundMapChain.getFirstVarInChainInverted)
      implicit val boundVarScope: BoundVarScope[F]         = BoundVarScopeImpl(boundMapChain)

      val freeMapChain                                   = VarMapChain.empty[F, VarSort]
      implicit val freeVarWriter: FreeVarWriter[VarSort] = FreeVarWriterImpl(freeMapChain.putVar)
      implicit val freeVarReader: FreeVarReader[VarSort] =
        FreeVarReaderImpl(freeMapChain.getVar, () => freeMapChain.getAllInScope)
      implicit val freeVarScope: FreeVarScope[F]         = FreeVarScopeImpl(freeMapChain)

      val patternInfoChain = PatternInfoChain()
      val bundleInfoChain  = BundleInfoChain()

      implicit val nestingInfoWriter: NestingWriter[F] = NestingWriterImpl(patternInfoChain, bundleInfoChain)
      implicit val nestingInfoReader: NestingReader    =
        NestingReaderImpl(
          () => patternInfoChain.getStatus._1,
          () => patternInfoChain.getStatus._2,
          () => bundleInfoChain.getStatus,
        )

      val normalizer              = NormalizerRecImpl[F, VarSort]()
      val normalizedTerm: F[ParN] = normalizer.normalize(term)
      // As other parts of the Rholang processing system still operate with the old data types,
      // we must ensure that we convert the result accordingly in this context.
      normalizedTerm.map(toProto)
    }

    /**
      * @note In lieu of a purely functional wrapper around the lexer and parser
      *       [[F.adaptError]] is used as a catch-all for errors thrown in their
      *       constructors.
      */
    private def lexer(fileReader: Reader): F[Yylex] =
      F.delay(new Yylex(fileReader)).adaptError { case th: Throwable =>
        LexerError("Lexer construction error: " + th.getMessage)
      }

    private def parser(lexer: Yylex): F[ErrorHandlingParser] =
      F.delay(new ErrorHandlingParser(lexer, lexer.getSymbolFactory)).adaptError { case th: Throwable =>
        ParserError("Parser construction error: " + th.getMessage)
      }
  }

}

/**
  * Signal errors to the caller rather than printing them to System.err.
  *
  * Please excuse the use of throw; we didn't design the CUP API.
  *
  * Ref Section 4. Customizing the Parser in
  * CUP User's Manual Last updated 06/2014 (v0.11b)
  * http://www2.cs.tum.edu/projects/cup/docs.php#parser
  */
@SuppressWarnings(Array("org.wartremover.warts.Throw"))
class ErrorHandlingParser(s: Yylex, sf: java_cup.runtime.SymbolFactory) extends parser(s, sf) {
  import java_cup.runtime.ComplexSymbolFactory.ComplexSymbol
  import java_cup.runtime.Symbol

  override def unrecovered_syntax_error(cur_token: Symbol): Unit =
    throw SyntaxError(
      cur_token match {
        case cs: ComplexSymbol =>
          s"syntax error(${cs.getName}): ${s
              .yytext()} at ${cs.getLeft.getLine}:${cs.getLeft.getColumn}-${cs.getRight.getLine}:${cs.getRight.getColumn}"
        case _                 => cur_token.toString()
      },
    )

  /**
    *  "This method is called by the parser as soon as a syntax error
    *  is detected (but before error recovery is attempted). In the
    *  default implementation it calls: `report_error("Syntax error",
    *  null);`." -- section 4.
    *
    * The Rholang grammar has no error recovery productions, so this is
    * always immediately followed by a call to
    * `unrecovered_syntax_error`.
    */
  override def syntax_error(cur_token: Symbol): Unit = ()

  /** always followed by report_fatal_error, so noop is appropriate
    */
  override def report_error(message: String, info: Object): Unit = ()

  override def report_fatal_error(message: String, info: Object): Unit =
    throw ParserError(message + info)
}
