package node.lmdb

import cats.effect.std.AtomicCell
import cats.effect.{Async, Deferred, Ref, Sync}
import cats.syntax.all.*
import enumeratum.{Enum, EnumEntry}
import node.lmdb.LmdbDirStoreManager.tb
import org.lmdbjava.ByteBufferProxy.PROXY_SAFE
import org.lmdbjava.{DbiFlags, Env, EnvFlags}
import sdk.store.{KeyValueStore, KeyValueStoreManager}

import java.nio.ByteBuffer
import java.nio.file.{Files, Path}

object LmdbStoreManager1 {
  def apply[F[_]: Async](dirPath: Path, maxEnvSize: Long = 1L * tb): F[KeyValueStoreManager[F]] =
    Deferred[F, Env[ByteBuffer]] map (LmdbStoreManagerImpl(dirPath, maxEnvSize, _))
}

/**
  * Wrapper around LMDB environment which can hold multiple databases.
  *
  * @param dirPath directory where LMDB files are stored.
  * @param maxEnvSize maximum LMDB environment (file) size.
  * @param envDefer deferred object for LMDB environment in use.
  * @return LMDB store manager.
  */
final private case class LmdbStoreManagerImpl1[F[_]: Async](
  dirPath: Path,
  maxEnvSize: Long,
  envDefer: Deferred[F, Env[ByteBuffer]],
) extends KeyValueStoreManager[F] {

  sealed trait EnvRefStatus extends EnumEntry
  object EnvRefStatus       extends Enum[EnvRefStatus] {
    val values = findValues
    case object EnvClosed   extends EnvRefStatus
    case object EnvStarting extends EnvRefStatus
    case object EnvOpen     extends EnvRefStatus
    case object EnvClosing  extends EnvRefStatus
  }
  import EnvRefStatus.*

  private case class DbState(
    status: EnvRefStatus,
    inProgress: Int,
    env: Env[ByteBuffer],
    dbs: Map[String, DbEnv[F]] = Map.empty,
  ) {
    override def toString() = s"DbState(status: $status, inProgress: $inProgress)"
  }

  // Internal manager state for LMDB databases and environments
  private val varState: AtomicCell[F, DbState] = ??? // AtomicCell[F].of(DbState(EnvClosed, 0, envDefer))

  override def store(dbName: String): F[KeyValueStore[F]] =
    Sync[F].delay(new LmdbKeyValueStore[F](getCurrentEnv(dbName)))

  private def getCurrentEnv(dbName: String): F[DbEnv[F]] =
    for {
      // Create environment if not created
      // this blocks until environment is ready
      env    <- varState.evalModify { st =>
                  if (st.status == EnvClosed) {
                    val newState = st.copy(status = EnvStarting)
                    createEnv.as(newState -> newState.env)
                  } else (st -> st.env).pure
                }
      // Find DB ref / first time create deferred object.
      // Block until database is ready.
      // If database is not found, create new Deferred object to block
      // callers until database is created and Deferred object completed.
      envDbi <- varState.evalModify { st =>
                  val mkDbi   = for {
                    dbi <- Sync[F].delay(env.openDbi(dbName, DbiFlags.MDB_CREATE))
                  } yield DbEnv(env, dbi, ().pure)
                  val maybeDb = st.dbs.get(dbName)
                  maybeDb.fold {
                    mkDbi.map { dbi =>
                      val newDbs = st.dbs.updated(dbName, dbi)
                      st.copy(dbs = newDbs) -> dbi
                    }
                  }(dbi => (st -> dbi).pure)
                }
    } yield envDbi

  // Create LMDB environment and update the state
  private def createEnv: F[Env[ByteBuffer]] =
    for {
      _    <- Sync[F].delay(Files.createDirectories(dirPath))
      flags = Seq(EnvFlags.MDB_NOTLS, EnvFlags.MDB_NORDAHEAD)
      // Create environment
      env  <- Sync[F].delay(
                Env
                  .create(PROXY_SAFE)
                  .setMapSize(maxEnvSize)
                  .setMaxDbs(20)
                  // Maximum parallel readers
                  .setMaxReaders(2048)
                  .open(dirPath.toFile, flags: _*),
              )
      // Update state to Open
      _    <- varState.modify { st =>
                val newState = st.copy(status = EnvOpen)
                (newState, newState.env)
              }
    } yield env

  override def shutdown: F[Unit] =
    for {
      // Close LMDB environment
      st <- varState.get
      _  <- Sync[F].delay(st.env.close()).whenA(st.status == EnvOpen)
    } yield ()
}
