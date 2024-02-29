package io.rhonix.rholang.normalizer

import cats.effect.Sync
import cats.syntax.all.*
import coop.rchain.rholang.interpreter.compiler.{createUniqueVarData, FreeContext, NameSort, SourcePosition, VarSort}
import coop.rchain.rholang.interpreter.errors.{
  ReceiveOnSameChannelsError,
  UnexpectedBundleContent,
  UnrecognizedNormalizerError,
}
import io.rhonix.rholang.ast.rholang.Absyn.*
import io.rhonix.rholang.normalizer.env.*
import io.rhonix.rholang.normalizer.syntax.all.*
import io.rhonix.rholang.types.*

import java.util.UUID
import scala.jdk.CollectionConverters.*

object InputNormalizer {
  @SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
  def normalizeInput[
    F[_]: Sync: NormalizerRec: BoundVarScope: FreeVarScope: NestingWriter,
    T: BoundVarWriter: FreeVarReader,
  ](p: PInput): F[ParN] = {
    if (p.listreceipt_.size() > 1) {
      NormalizerRec[F].normalize(
        p.listreceipt_.asScala.reverse.foldLeft(p.proc_) { (proc, receipt) =>
          val listReceipt = new ListReceipt()
          listReceipt.add(receipt)
          new PInput(listReceipt, proc)
        },
      )
    } else {

      val receiptContainsComplexSource: Boolean =
        p.listreceipt_.asScala.head match {
          case rl: ReceiptLinear =>
            rl.receiptlinearimpl_ match {
              case ls: LinearSimple =>
                ls.listlinearbind_.asScala.exists { case lbi: LinearBindImpl =>
                  lbi.namesource_ match {
                    case _: SimpleSource => false
                    case _               => true
                  }

                }
              case _                => false
            }
          case _                 => false
        }

      if (receiptContainsComplexSource) {
        p.listreceipt_.asScala.head match {
          case rl: ReceiptLinear =>
            rl.receiptlinearimpl_ match {
              case ls: LinearSimple =>
                val listReceipt           = new ListReceipt()
                val listLinearBind        = new ListLinearBind()
                val listNameDecl          = new ListNameDecl()
                listReceipt.add(new ReceiptLinear(new LinearSimple(listLinearBind)))
                val (sends, continuation) =
                  ls.listlinearbind_.asScala.foldLeft((new PNil: Proc, p.proc_)) { case ((sends, continuation), lb) =>
                    lb match {
                      case lbi: LinearBindImpl =>
                        lbi.namesource_ match {
                          case _: SimpleSource =>
                            listLinearBind.add(lbi)
                            (sends, continuation)
                          case _               =>
                            val identifier = UUID.randomUUID().toString
                            val r          = new NameVar(identifier)
                            lbi.namesource_ match {
                              case rss: ReceiveSendSource =>
                                lbi.listname_.asScala.prepend(r)
                                listLinearBind.add(
                                  new LinearBindImpl(
                                    lbi.listname_,
                                    lbi.nameremainder_,
                                    new SimpleSource(rss.name_),
                                  ),
                                )
                                (
                                  sends,
                                  new PPar(
                                    new PSend(r, new SendSingle, new ListProc()),
                                    continuation,
                                  ),
                                )
                              case srs: SendReceiveSource =>
                                listNameDecl.add(new NameDeclSimpl(identifier))
                                listLinearBind.add(
                                  new LinearBindImpl(
                                    lbi.listname_,
                                    lbi.nameremainder_,
                                    new SimpleSource(r),
                                  ),
                                )
                                srs.listproc_.asScala.prepend(new PEval(r))
                                (
                                  new PPar(
                                    new PSend(srs.name_, new SendSingle, srs.listproc_),
                                    sends,
                                  ): Proc,
                                  continuation,
                                )
                            }
                        }
                    }
                  }
                val pInput                = new PInput(listReceipt, continuation)
                NormalizerRec[F].normalize(
                  if (listNameDecl.isEmpty) pInput
                  else new PNew(listNameDecl, new PPar(sends, pInput)),
                )
            }

        }
      } else {

        // To handle the most common case where we can sort the binds because
        // they're from different sources, Each channel's list of patterns starts its free variables at 0.
        // We check for overlap at the end after sorting. We could check before, but it'd be an extra step.
        // We split this into parts. First we process all the sources, then we process all the bindings.

        // If we get to this point, we know p.listreceipt.size() == 1
        val (consumes, persistent, peek) =
          p.listreceipt_.asScala.head match {
            case rl: ReceiptLinear   =>
              rl.receiptlinearimpl_ match {
                case ls: LinearSimple =>
                  (
                    ls.listlinearbind_.asScala.toVector.map { case lbi: LinearBindImpl =>
                      (
                        (lbi.listname_.asScala.toVector, lbi.nameremainder_),
                        lbi.namesource_ match {
                          // all sources should be simple sources by this point
                          case ss: SimpleSource => ss.name_
                        },
                      )
                    },
                    false,
                    false,
                  )
              }
            case rr: ReceiptRepeated =>
              rr.receiptrepeatedimpl_ match {
                case rs: RepeatedSimple =>
                  (
                    rs.listrepeatedbind_.asScala.toVector.map { case rbi: RepeatedBindImpl =>
                      ((rbi.listname_.asScala.toVector, rbi.nameremainder_), rbi.name_)
                    },
                    true,
                    false,
                  )

              }
            case rp: ReceiptPeek     =>
              rp.receiptpeekimpl_ match {
                case ps: PeekSimple =>
                  (
                    ps.listpeekbind_.asScala.toVector.map { case pbi: PeekBindImpl =>
                      ((pbi.listname_.asScala.toVector, pbi.nameremainder_), pbi.name_)
                    },
                    false,
                    true,
                  )

              }

          }

        val (patterns, names) = consumes.unzip

        def createBinds(
          patterns: Vector[(Vector[Name], NameRemainder)],
          normalizedSources: Vector[ParN],
        ): F[Vector[ReceiveBindN]] =
          (patterns zip normalizedSources)
            .traverse { case ((names, remainder), source) =>
              for {
                initFreeCount <- Sync[F].delay(FreeVarReader[T].getFreeVars.size)
                rbNames       <- names.traverse(NormalizerRec[F].normalize)
                rbRemainder   <- NormalizerRec[F].normalize(remainder)
                freeCount      = FreeVarReader[T].getFreeVars.size - initFreeCount
              } yield ReceiveBindN(rbNames, source, rbRemainder, freeCount)
            }

        for {
          processedSources <- names.traverse(NormalizerRec[F].normalize)

          patternTuple <- createBinds(patterns, processedSources).withinPatternGetFreeVars(withinReceive = true)

          (binds, freeVars) = patternTuple

          thereAreDuplicatesInSources = processedSources.distinct.size != processedSources.size
          _                          <- ReceiveOnSameChannelsError(p.line_num, p.col_num)
                                          .raiseError[F, Unit]
                                          .whenA(thereAreDuplicatesInSources)

          // Normalize body in the copy of bound scope with added free variables as bounded
          continuation               <- NormalizerRec[F].normalize(p.proc_).withAbsorbedFreeVars(freeVars)

        } yield ReceiveN(binds, continuation, persistent, peek, freeVars.size)
      }
    }
  }

  def normalizeInputNew[
    F[_]: Sync: NormalizerRec: BoundVarScope: FreeVarScope: NestingWriter,
    T >: VarSort: BoundVarWriter: BoundVarReader: FreeVarWriter: FreeVarReader,
  ](p: PInput): F[ParN] = {

    def NormalizePatterns(
      patterns: Seq[(Seq[Name], NameRemainder)],
      normalizedSources: Seq[ParN],
    ): F[Seq[ReceiveBindN]] =
      (patterns zip normalizedSources)
        .traverse { case ((names, remainder), source) =>
          for {
            initFreeCount <- Sync[F].delay(FreeVarReader[T].getFreeVars.size)
            rbNames       <- names.traverse(NormalizerRec[F].normalize)
            rbRemainder   <- NormalizerRec[F].normalize(remainder)
            freeCount      = FreeVarReader[T].getFreeVars.size - initFreeCount
          } yield ReceiveBindN(rbNames, source, rbRemainder, freeCount)
        }

    def createBinds(
      patterns: Seq[(Seq[Name], NameRemainder)],
      processedSources: Seq[ParN],
    ): F[(Seq[ReceiveBindN], Seq[(String, FreeContext[T])])] =
      for {
        patternTuple     <- NormalizePatterns(patterns, processedSources).withinPatternGetFreeVars(withinReceive = true)
        (binds, freeVars) = patternTuple

        duplicatesInSources = processedSources.distinct.size != processedSources.size
        _                  <- ReceiveOnSameChannelsError(p.line_num, p.col_num).raiseError[F, Unit].whenA(duplicatesInSources)
      } yield (binds, freeVars)

    case class JoinData(
      binds: Seq[ReceiveBindN],
      freeVars: Seq[(String, FreeContext[T])],
      addSends: Seq[SendN],
      addNestedSendNames: Seq[String],
      persistentFlag: Boolean,
      peekFlag: Boolean,
    )

    def normalizeJoin(join: Receipt): F[JoinData] = join match {
      case rl: ReceiptLinear   =>
        // if we have a linear consume, e.g. `for (ptn1, ptn2, ... <- src1, src2, ...; ...) { inputContinuation }`
        rl.receiptlinearimpl_ match {
          case ls: LinearSimple =>
            val (
              patterns: Seq[(Seq[Name], NameRemainder)],
              processedSources: Seq[ParN],
              addSends: Seq[SendN],
              addNestedSendNames: Seq[String],
            ) =
              ls.listlinearbind_.asScala.toSeq
                .foldLeftM((Seq[(Seq[Name], NameRemainder)](), Seq[ParN](), Seq[SendN](), Seq[String]())) {
                  case ((foldPatterns, foldSources, foldAddSends, foldAddNestedSendNames), bind) =>
                    bind match {
                      case lbi: LinearBindImpl =>
                        lbi.namesource_ match {

                          case ss: SimpleSource =>
                            for {
                              normalizedSource <- NormalizerRec[F].normalize(ss.name_)
                            } yield (
                              foldPatterns :+ (lbi.listname_.asScala.toSeq, lbi.nameremainder_),
                              foldSources :+ normalizedSource,
                              foldAddSends,
                              foldAddNestedSendNames,
                            )

                          case rss: ReceiveSendSource =>
                            // NOTE: It is not clear how to get rid of BNFC type generation here
                            val uniqueVarName = createUniqueVarData(NameSort)._1
                            val genVar        = new NameVar(uniqueVarName)
                            for {
                              normalizedSource <- NormalizerRec[F].normalize(rss.name_)
                            } yield (
                              foldPatterns :+ (genVar +: lbi.listname_.asScala.toSeq, lbi.nameremainder_),
                              foldSources :+ normalizedSource,
                              foldAddSends,
                              foldAddNestedSendNames :+ uniqueVarName,
                            )

                          case srs: SendReceiveSource =>
                            val newVarIdx = BoundVarWriter[T].createBoundVar(NameSort)
                            val genSource = BoundVarN(newVarIdx)
                            for {
                              sendChan <- NormalizerRec[F].normalize(srs.name_)
                              sendArgs <- srs.listproc_.asScala.toSeq.traverse(NormalizerRec[F].normalize)
                            } yield (
                              foldPatterns :+ (lbi.listname_.asScala.toSeq, lbi.nameremainder_),
                              foldSources :+ genSource,
                              foldAddSends :+ SendN(sendChan, genSource +: sendArgs),
                              foldAddNestedSendNames,
                            )
                        }
                    }
                }

            for {
              bindsTuple       <- createBinds(patterns, processedSources)
              (binds, freeVars) = bindsTuple
            } yield JoinData(
              binds = binds,
              freeVars = freeVars,
              addSends = addSends,
              addNestedSendNames = addNestedSendNames,
              persistentFlag = false,
              peekFlag = false,
            )
        }
      case rr: ReceiptRepeated =>
        // if we have a persistent consume, e.g. `for (ptn1, ptn2, ... <= src1, src2, ...) { inputContinuation }`
        rr.receiptrepeatedimpl_ match {
          case rs: RepeatedSimple =>
            val (patterns, sources) = rs.listrepeatedbind_.asScala.toSeq.map { case rbi: RepeatedBindImpl =>
              ((rbi.listname_.asScala.toSeq, rbi.nameremainder_), rbi.name_)
            }.unzip
            for {
              processedSources <- sources.traverse(NormalizerRec[F].normalize)
              bindsTuple       <- createBinds(patterns, processedSources)
              (binds, freeVars) = bindsTuple
            } yield JoinData(
              binds = binds,
              freeVars = freeVars,
              addSends = Seq(),
              addNestedSendNames = Seq(),
              persistentFlag = true,
              peekFlag = false,
            )
        }
      case rp: ReceiptPeek     =>
        // if we have a peek consume, e.g. `for (ptn1, ptn2, ... <<- src1, src2, ...) { inputContinuation }`
        rp.receiptpeekimpl_ match {
          case ps: PeekSimple =>
            val (patterns, sources) = ps.listpeekbind_.asScala.toSeq.map { case pbi: PeekBindImpl =>
              ((pbi.listname_.asScala.toSeq, pbi.nameremainder_), pbi.name_)
            }.unzip
            for {
              processedSources <- sources.traverse(NormalizerRec[F].normalize)
              bindsTuple       <- createBinds(patterns, processedSources)
              (binds, freeVars) = bindsTuple
            } yield JoinData(
              binds = binds,
              freeVars = freeVars,
              addSends = Seq(),
              addNestedSendNames = Seq(),
              persistentFlag = false,
              peekFlag = true,
            )
        }
    }

    def constructResult(
      joinData: JoinData,
      continuation: ParN,
      addNestedSends: Seq[SendN],
    ): ParN = {
      val newContinuation = ParN.makeParProc(addNestedSends :+ continuation)
      val receive         = ReceiveN(
        joinData.binds,
        newContinuation,
        joinData.persistentFlag,
        joinData.peekFlag,
        joinData.freeVars.size + joinData.addNestedSendNames.size,
      )
      if (joinData.addSends.isEmpty) receive
      else
        //  Send-receive sources operations should be normalized before other sources
        //  to prevent conflicts with bound variables in the new context.
        NewN(
          bindCount = joinData.addSends.size,
          p = ParN.makeParProc(joinData.addSends :+ receive),
          uri = Seq(),
          injections = Map[String, ParN](),
        )
    }

    def constructNestedSends(chanNames: Seq[String]): Seq[SendN] = chanNames.map { name =>
      val varIdx = BoundVarReader[T].getBoundVar(name).get.index
      SendN(BoundVarN(varIdx), NilN)
    }

    /**
     * This function is used to process multiple receipts (joins) by normalizing them separately and then combining them.
     * {{{
     *   // Input: Two joins: `for (join1 ; join2) {Nil}`
     *  for(x1 <- @"ch11" & y1 <- @"ch12" & ... ;
     *      x2 <= @"ch21" & y2 <= @"ch22" & ... ) {
     *    Nil
     *  }
     *
     *  // Output: Normalized AST with two nested binds: `for(join1) { for(join2) {Nil} }`
     *  for(x1 <- @"ch11" & y1 <- @"ch12" & ... ) {
     *    for(x2 <= @"ch21" & y2 <= @"ch22" & ... ) {
     *      Nil
     *    }
     *  }
     * }}}
     *
     * @param joins A sequence of Receipt objects that need to be processed.
     * @return A tuple containing the first Receipt from the input sequence and a Proc object representing the continuation of the input.
     *         The continuation is created by folding the tail of the input sequence from right to left,
     *         creating a new ListReceipt for each Receipt and adding it to a new PInput along with the current Proc.
     */
    def normalizeJoins(firstJoin: Receipt, restJoins: Seq[Receipt], continuation: Proc): F[ParN] =
      // Each level of the recursion must be wrapped in a copy of the bound variable scope.
      // Because we create new bound variables during join normalization, we need to ensure that
      // the new variables are not visible in previous levels of the recursion.
      BoundVarScope[F].withCopyBoundVarScope(for {
        joinData <- normalizeJoin(firstJoin)

        processContinuation =
          for {
            newContinuation <- if (restJoins.isEmpty) NormalizerRec[F].normalize(continuation)
                               else normalizeJoins(restJoins.head, restJoins.tail, continuation)
            addNestedSends   = constructNestedSends(joinData.addNestedSendNames)
          } yield (newContinuation, addNestedSends)

        continuationTuple                <- processContinuation.withAbsorbedFreeVars(joinData.freeVars)
        (newContinuation, addNestedSends) = continuationTuple

      } yield constructResult(joinData, newContinuation, addNestedSends))

    def noJoinsError: F[Unit] = UnrecognizedNormalizerError(
      s"Receive should contain at least one join: ${SourcePosition(p.line_num, p.col_num)}.",
    ).raiseError

    for {
      joins <- Sync[F].delay(p.listreceipt_.asScala.toSeq)

      _ <- noJoinsError.whenA(joins.isEmpty) // if joins empty, throw exception

      res <- normalizeJoins(joins.head, joins.tail, p.proc_)
    } yield res
  }
}
