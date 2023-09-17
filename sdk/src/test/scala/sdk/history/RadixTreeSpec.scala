package sdk.history

import cats.effect.unsafe.implicits.global
import cats.effect.{IO, Sync}
import cats.syntax.all.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{EitherValues, OptionValues}
import sdk.codecs.Base16
import sdk.hashing.Blake2b256Hash
import sdk.history.KeySegment.commonPrefix
import sdk.history.RadixTree.*
import sdk.history.instances.RadixHistory
import sdk.primitive.ByteArray
import sdk.store.{InMemoryKeyValueStore, KeyValueTypedStore}
import sdk.syntax.all.{sharedSyntaxKeyValueStore, sharedSyntaxKeyValueTypedStore}

import java.nio.ByteBuffer
import scala.concurrent.duration.*

class RadixTreeSpec extends AnyFlatSpec with Matchers with OptionValues with EitherValues {
  "appending leaf in empty tree" should "create tree with one node" in withImplAndStore { (impl, _) =>
    val dataSet = radixKV("1122334455", "01")
    for {
      item1 <- impl.update(RadixTree.EmptyItem, dataSet.rKey, dataSet.rValue)

      newRootNode    <- impl.constructNodeFromItem(item1.get)
      printedTreeStr <- impl.printTree(newRootNode, "TREE WITH ONE LEAF", noPrintFlag = true)

      referenceTree = Vector(
                        "TREE WITH ONE LEAF: root =>",
                        "   [11]LEAF: prefix = 22334455, data = 0000...0001",
                      )

      _ = printedTreeStr shouldBe referenceTree
    } yield ()
  }

  "appending leaf to leaf (leafs contain common prefixes)" should "create 2 leafs with node ptr" in withImplAndStore {
    (impl, _) =>
      val dataSet = List(radixKV("1122334455", "01"), radixKV("112233AABB", "02"))
      for {
        item1Opt <- impl.update(RadixTree.EmptyItem, dataSet.head.rKey, dataSet.head.rValue)

        rootNode1    <- impl.constructNodeFromItem(item1Opt.get)
        printedTree1 <- impl.printTree(rootNode1, "TREE WITH ONE LEAF", noPrintFlag = true)

        referenceTree1 = Vector(
                           "TREE WITH ONE LEAF: root =>",
                           "   [11]LEAF: prefix = 22334455, data = 0000...0001",
                         )

        item2Opt <- impl.update(item1Opt.get, dataSet(1).rKey, dataSet(1).rValue)

        rootNode2    <- impl.constructNodeFromItem(item2Opt.get)
        printedTree2 <- impl.printTree(
                          rootNode2,
                          "TREE WITH ONE NODE POINTER AND 2 LEAFS",
                          noPrintFlag = true,
                        )

        referenceTree2 = Vector(
                           "TREE WITH ONE NODE POINTER AND 2 LEAFS: root =>",
                           "   [11]PTR: prefix = 2233, ptr =>",
                           "      [44]LEAF: prefix = 55, data = 0000...0001",
                           "      [AA]LEAF: prefix = BB, data = 0000...0002",
                         )

        _ = printedTree1 shouldBe referenceTree1
        _ = printedTree2 shouldBe referenceTree2
      } yield ()
  }
  "appending leaf with other prefix to leaf" should "create node with two leafs" in withImplAndStore { (impl, _) =>
    val dataSet = List(radixKV("1122334455", "01"), radixKV("AABBCCDDEE", "02"))
    for {
      rootItem1Opt <- impl.update(RadixTree.EmptyItem, dataSet.head.rKey, dataSet.head.rValue)

      rootItem2Opt <- impl.update(rootItem1Opt.get, dataSet(1).rKey, dataSet(1).rValue)

      rootNode <- impl.constructNodeFromItem(rootItem2Opt.get)

      printedTreeStr <- impl.printTree(rootNode, "TREE: TWO LEAFS", noPrintFlag = true)

      referenceTree = Vector(
                        "TREE: TWO LEAFS: root =>",
                        "   [11]LEAF: prefix = 22334455, data = 0000...0001",
                        "   [AA]LEAF: prefix = BBCCDDEE, data = 0000...0002",
                      )

      _ = printedTreeStr shouldBe referenceTree
    } yield ()
  }

  "updating leaf" should "update data in this leaf" in withImplAndStore { (impl, _) =>
    val initialKVPair = radixKV("1122334455", "01")
    val newKVPair     = radixKV("1122334455", "FF")
    for {

      // Create tree with one leaf
      item1Opt <- impl.update(RadixTree.EmptyItem, initialKVPair.rKey, initialKVPair.rValue)

      rootNode1    <- impl.constructNodeFromItem(item1Opt.get)
      printedTree1 <- impl.printTree(rootNode1, "TREE WITH ONE LEAF", noPrintFlag = true)

      item2Opt  <- impl.update(item1Opt.get, newKVPair.rKey, newKVPair.rValue)
      rootNode2 <- impl.constructNodeFromItem(item2Opt.get)

      printedTree2 <- impl.printTree(
                        rootNode2,
                        "TREE WITH ONE LEAF (AFTER CHANGING DATA)",
                        noPrintFlag = true,
                      )

      referenceTree1 = Vector(
                         "TREE WITH ONE LEAF: root =>",
                         "   [11]LEAF: prefix = 22334455, data = 0000...0001",
                       )

      referenceTree2 = Vector(
                         "TREE WITH ONE LEAF (AFTER CHANGING DATA): root =>",
                         "   [11]LEAF: prefix = 22334455, data = 0000...00FF",
                       )

      _ = printedTree1 shouldBe referenceTree1
      _ = printedTree2 shouldBe referenceTree2
    } yield ()
  }

  "RadixTreeImpl" should "not allow to enter keys with different lengths in the subtree" in withImplAndStore {
    (impl, _) =>
      val initialKVPair         = radixKV("1122334455", "01")
      val wrongKVPair           = radixKV("112233", "02")
      val referenceErrorMessage =
        s"assertion failed: The length of all prefixes in the subtree must be the same."
      for {
        leafItemOpt <- impl.update(RadixTree.EmptyItem, initialKVPair.rKey, initialKVPair.rValue)
        err         <- impl.update(leafItemOpt.get, wrongKVPair.rKey, wrongKVPair.rValue).attempt

        ex = err.left.value
        _  = ex shouldBe a[AssertionError]
        _  = ex.getMessage shouldBe referenceErrorMessage
      } yield ()
  }

  "RadixTreeImpl" should "not allow to radix key smaller than NodePtr key" in withImplAndStore { (impl, _) =>
    val initialItem           = NodePtr(createKeySegment("11223344"), createBlakeHash("01"))
    val wrongKVPair           = radixKV("11", "FF")
    val referenceErrorMessage = s"assertion failed: Radix key should be longer than NodePtr key."
    for {
      err <- impl.update(initialItem, wrongKVPair.rKey, wrongKVPair.rValue).attempt

      ex = err.left.value
      _  = ex shouldBe a[AssertionError]
      _  = ex.getMessage shouldBe referenceErrorMessage
    } yield ()
  }

  "deleting non - existent data" should "return none" in withImplAndStore { (impl, _) =>
    val initialKVPair  = radixKV("1122334455", "01")
    val nonExistentKey = createKeySegment("FFFFFFFFFF")
    for {
      // Create tree with one node
      itemOpt <- impl.update(RadixTree.EmptyItem, initialKVPair.rKey, initialKVPair.rValue)
      _       <- impl.constructNodeFromItem(itemOpt.get)

      // Trying to delete not existing leaf...
      del <- impl.delete(itemOpt.get, nonExistentKey)

      _ = del.map(item => item shouldBe None)
    } yield ()
  }

  "deleting leaf from tree with only one leaf" should "destroy tree" in withImplAndStore { (impl, _) =>
    val initialKVPair = radixKV("1122334455", "01")
    for {
      // Create tree with one node
      itemOpt <- impl.update(RadixTree.EmptyItem, initialKVPair.rKey, initialKVPair.rValue)

      // Trying to delete not existing leaf...
      item3Opt  <- impl.delete(itemOpt.get, initialKVPair.rKey)
      rootNode2 <- impl.constructNodeFromItem(item3Opt.get)

      printedEmptyTreeStr <- impl.printTree(rootNode2, "EMPTY TREE", noPrintFlag = true)

      referenceEmptyTreeStr = Vector("EMPTY TREE: root =>")

      _ = item3Opt.map(item => item shouldBe RadixTree.EmptyItem)
      _ = referenceEmptyTreeStr shouldBe printedEmptyTreeStr
    } yield ()
  }

  "deleting leaf from node with two leafs" should "leave one leaf" in withImplAndStore { (impl, _) =>
    val dataSet = List(radixKV("1122334455", "01"), radixKV("AABBCCDDEE", "02"))
    for {
      rootItem1Opt <- impl.update(RadixTree.EmptyItem, dataSet.head.rKey, dataSet.head.rValue)

      rootItem2Opt <- impl.update(rootItem1Opt.get, dataSet(1).rKey, dataSet(1).rValue)

      rootNode1    <- impl.constructNodeFromItem(rootItem2Opt.get)
      printedTree1 <- impl.printTree(
                        rootNode1,
                        "TREE: TWO LEAFS (BEFORE DELETING)",
                        noPrintFlag = true,
                      )

      itemIdx <- IO.delay(byteToInt(dataSet.head.rKey.head))

      itemToDelete = rootNode1(itemIdx)
      item3Opt    <- impl.delete(itemToDelete, dataSet.head.rKey.tail)
      rootNode2    = rootNode1.updated(itemIdx, item3Opt.get)

      printedTree2 <- impl.printTree(
                        rootNode2,
                        "TREE: TWO LEAFS (AFTER DELETING)",
                        noPrintFlag = true,
                      )

      referenceTree1 = Vector(
                         "TREE: TWO LEAFS (BEFORE DELETING): root =>",
                         "   [11]LEAF: prefix = 22334455, data = 0000...0001",
                         "   [AA]LEAF: prefix = BBCCDDEE, data = 0000...0002",
                       )

      referenceTree2 = Vector(
                         "TREE: TWO LEAFS (AFTER DELETING): root =>",
                         "   [AA]LEAF: prefix = BBCCDDEE, data = 0000...0002",
                       )
      _              = printedTree1 shouldBe referenceTree1
      _              = printedTree2 shouldBe referenceTree2
    } yield ()
  }

  "deleting one leaf from a child node containing two leafs" should "delete child node and reformat parent node" in withImplAndStore {
    (impl, _) =>
      val dataSet = List(radixKV("1122334455", "01"), radixKV("11223344FF", "02"))
      for {
        item1Opt <- impl.update(RadixTree.EmptyItem, dataSet.head.rKey, dataSet.head.rValue)
        item2Opt <- impl.update(item1Opt.get, dataSet(1).rKey, dataSet(1).rValue)

        rootNode1    <- impl.constructNodeFromItem(item2Opt.get)
        printedTree1 <- impl.printTree(
                          rootNode1,
                          "TREE WITH ONE NODE AND 2 LEAFS",
                          noPrintFlag = true,
                        )

        referenceTree1 = Vector(
                           "TREE WITH ONE NODE AND 2 LEAFS: root =>",
                           "   [11]PTR: prefix = 223344, ptr =>",
                           "      [55]LEAF: prefix = empty, data = 0000...0001",
                           "      [FF]LEAF: prefix = empty, data = 0000...0002",
                         )

        item3Opt  <- impl.delete(rootNode1(0x11), dataSet.head.rKey.tail)
        rootNode2 <- impl.constructNodeFromItem(item3Opt.get)

        printedTree2 <- impl.printTree(rootNode2, "TREE (AFTER DELETE)", noPrintFlag = true)

        referenceTree2 = Vector(
                           "TREE (AFTER DELETE): root =>",
                           "   [22]LEAF: prefix = 3344FF, data = 0000...0002",
                         )

        _ = printedTree1 shouldBe referenceTree1
        _ = printedTree2 shouldBe referenceTree2

      } yield ()
  }

  "reading data from existing node" should "return data" in withImplAndStore { (impl, _) =>
    val initialKVPair = radixKV("1122334455", "01")
    for {
      itemOpt  <- impl.update(RadixTree.EmptyItem, initialKVPair.rKey, initialKVPair.rValue)
      rootNode <- impl.constructNodeFromItem(itemOpt.get)

      readDataOpt <- impl.read(rootNode, initialKVPair.rKey)

      _ = readDataOpt.get shouldBe initialKVPair.rValue
    } yield ()
  }

  "reading non - existent data" should "return none" in withImplAndStore { (impl, _) =>
    val initialKVPair = radixKV("1122334455", "01")
    for {
      itemOpt  <- impl.update(RadixTree.EmptyItem, initialKVPair.rKey, initialKVPair.rValue)
      rootNode <- impl.constructNodeFromItem(itemOpt.get)

      notExistingKey = createKeySegment("0000")
      readDataOpt   <- impl.read(rootNode, notExistingKey)

      _ = readDataOpt shouldBe none

    } yield ()
  }

  "Call of functions saveNode() and then commit()" should "put node into store" in withImplAndStore {
    (impl, inMemoStore) =>
      for {
        nodesCount1 <- IO.delay(inMemoStore.numRecords())
        _            = impl.saveNode(EmptyNode)
        _           <- impl.commit

        // After saving node numRecords must return 1
        nodesCount2 = inMemoStore.numRecords()
        _           = nodesCount1 shouldBe 0
        _           = nodesCount2 shouldBe 1
      } yield ()
  }

  "function loadNode" should "load node from store" in withImplAndStore { (impl, _) =>
    for {
      hash <- IO.delay(impl.saveNode(EmptyNode))
      _    <- impl.commit

      _         = impl.clearReadCache()
      _         = impl.clearWriteCache()
      loadNode <- impl.loadNode(hash)
      _         = loadNode shouldBe EmptyNode
    } yield ()
  }

  "Trying to load a non-existent node" should "throw error" in withImplAndStore { (impl, store) =>
    for {
      hash <- IO.delay(impl.saveNode(EmptyNode))
      _    <- impl.commit
      _     = store.clear() // Clearing database
      _     = impl.clearReadCache()
      _     = impl.clearWriteCache()

      err                  <- impl.loadNode(hash).attempt
      ex                    = err.left.value
      _                     = ex shouldBe a[AssertionError]
      referenceErrorMessage = s"assertion failed: Missing node in database. ptr=${hash.bytes.toHex}."
      _                     = ex.getMessage shouldBe referenceErrorMessage
    } yield ()
  }

  // Data for test are given from RadixTree specification
  "encoding and then decoding a node" should "give this node" in {
    val leaf          = Leaf(
      createKeySegment("FFFF"),
      createBlakeHash("0000000000000000000000000000000000000000000000000000000000000001"),
    )
    val nodePtr       = NodePtr(
      createKeySegment(""),
      createBlakeHash("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"),
    )
    val referenceNode = EmptyNode
      .updated(1, leaf)
      .updated(2, nodePtr)

    val serializedNode      = RadixTree.Codecs.encode(referenceNode)
    val deserializedNode    = RadixTree.Codecs.decode(serializedNode)
    val referenceSerialized = createBA(
      "0102FFFF00000000000000000000000000000000000000000000000000000000000000010280FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF",
    )
    deserializedNode shouldBe referenceNode
    serializedNode shouldBe referenceSerialized
  }

  "decode wrong serialized data" should "be stopped with assertion error" in {
    val wrongSerialized = createBA(
      "0102FFFF00000000000000000000000000000010280FFFFFFFFFFFFFFFFFFFFFFFFFF",
    )
    try
      RadixTree.Codecs.decode(wrongSerialized)
    catch {
      case ex: Exception =>
        ex shouldBe a[Exception]
        ex.getMessage shouldBe s"Error during deserialization: invalid data format"
    }
  }

  "collisions in KVDB" should "be detected" in withImplAndStore { (impl, inMemoStore) =>
    def copyBaToBuf(ba: ByteArray): ByteBuffer = {
      val arr    = ba.toArray
      val newBuf = ByteBuffer.allocateDirect(arr.length)
      newBuf.put(arr).rewind()
    }

    val collisionKVPair       = (copyBaToBuf(RadixTree.EmptyRootHash.bytes), ByteArray(0x00.toByte))
    val referenceErrorMessage = s"1 collisions in KVDB (first collision with key = " +
      s"${RadixTree.EmptyRootHash.bytes.toHex})."
    for {
      _   <- inMemoStore.put[ByteArray](Seq(collisionKVPair), copyBaToBuf)
      _    = impl.saveNode(EmptyNode)
      err <- impl.commit.attempt

      ex = err.left.value
      _  = ex shouldBe a[RuntimeException]
      _  = ex.getMessage shouldBe referenceErrorMessage
    } yield ()
  }

  "tree with saveAndCommit" should "be built correctly and not create artefacts in KV - store" in withImplAndStore {
    (impl, inMemoStore) =>
      def createDeleteActions(keys: List[KeySegment]): List[DeleteAction] =
        keys.map(key => DeleteAction(key))

      /* treeDataSet:
            key      |   value
        111122334455 | 0000...0001
        11112233AABB | 0000...0002
        1111AABBCC   | 0000...0003
        33           | 0000...0004
        FF0011       | 0000...0005
        FF012222     | 0000...0006
       */
      val deleteActions = createDeleteActions(treeDataSet.map(_.rKey))

      val referenceTree1 = Vector(
        "TREE1: root =>",
        "   [11]PTR: prefix = 11, ptr =>",
        "      [22]PTR: prefix = 33, ptr =>",
        "         [44]LEAF: prefix = 55, data = 0000...0001",
        "         [AA]LEAF: prefix = BB, data = 0000...0002",
        "      [AA]LEAF: prefix = BBCC, data = 0000...0003",
        "   [33]LEAF: prefix = empty, data = 0000...0004",
        "   [FF]PTR: prefix = empty, ptr =>",
        "      [00]LEAF: prefix = 11, data = 0000...0005",
        "      [01]LEAF: prefix = 2222, data = 0000...0006",
      )
      val referenceTree2 = Vector("TREE2: root =>")
      for {
        // 1  Build a tree according to the example in specification
        rootNodeAndHash1Opt <- impl.saveAndCommit(RadixTree.EmptyNode, referenceInsertActions)
        (rootNode1, _)       = rootNodeAndHash1Opt.get

        // Get the tree for compare with reference
        tree1 <- impl.printTree(rootNode1, treeName = "TREE1", noPrintFlag = true)

        // Number of nodes must be equal to 4 (with root)
        nodesCount1 = inMemoStore.numRecords()

        // 2   Delete all data from tree...
        rootNodeAndHash2Opt <- impl.saveAndCommit(rootNode1, deleteActions)

        (rootNode2, _) = rootNodeAndHash2Opt.get
        tree2         <- impl.printTree(rootNode2, treeName = "TREE2", noPrintFlag = true)

        // Number of nodes after deleting data must be equal to 5 (with root)
        nodesCount2 = inMemoStore.numRecords()

        _ = rootNode2 shouldBe RadixTree.EmptyNode
        _ = tree1 shouldBe referenceTree1
        _ = nodesCount1 shouldBe 4
        _ = tree2 shouldBe referenceTree2
        _ = nodesCount2 shouldBe 5
      } yield ()
  }

  "sequentialExport" should "export all data from tree" in withImplAndStore { (impl, store) =>
    for {
      // Create tree with 6 leafs
      rootNodeAndHashOpt <- impl.saveAndCommit(RadixTree.EmptyNode, referenceInsertActions)
      (_, rootHash)       = rootNodeAndHashOpt.get

      // First data export
      typedStore       = store.toByteArrayTypedStore(RadixHistory.kCodec, RadixHistory.vCodec)
      exported1       <- sequentialExport(
                           rootHash,
                           None,
                           skipSize = 0,
                           takeSize = 100,
                           typedStore.get1,
                           exportSettings,
                         )
      (exportData1, _) = exported1

      // Create new storage
      nodeKVDBKeys   = exportData1.nodeKeys
      nodeKVDBValues = exportData1.nodeValues

      localStorage = (nodeKVDBKeys zip nodeKVDBValues).toMap

      // Export data from new storage
      exported2 <-
        sequentialExport(
          rootHash,
          None,
          skipSize = 0,
          takeSize = 100,
          x => IO.delay(localStorage.get(x)),
          exportSettings,
        )

      (exportData2, _) = exported2

      _ = exportData1.nodeKeys.size shouldBe 4
      _ = exported1 shouldBe exported2
      _ = exportData1.leafPrefixes shouldBe referenceLeafPrefixes
      _ = exportData1.leafValues shouldBe referenceLeafValues
      _ = exportData2.leafPrefixes shouldBe referenceLeafPrefixes
      _ = exportData2.leafValues shouldBe referenceLeafValues
    } yield ()
  }

  "invalid initial conditions in sequentialExport" should "raise exception" in withImplAndStore { (impl, store) =>
    val typedStore = store.toByteArrayTypedStore(RadixHistory.kCodec, RadixHistory.vCodec)
    for {
      // Create tree with 6 leafs
      rootAndHashOpt <- impl.saveAndCommit(RadixTree.EmptyNode, referenceInsertActions)
      (_, rootHash)   = rootAndHashOpt.get

      // Validate exception when skipSize = 0 and takeSize == 0
      err <- sequentialExport(
               rootHash,
               None,
               skipSize = 0,
               takeSize = 0,
               typedStore.get1,
               exportSettings,
             ).attempt

      ex = err.left.value
      _  = ex shouldBe a[RuntimeException]
      _  = ex.getMessage shouldBe "Export error: invalid initial conditions (skipSize, takeSize)==(0,0)."
    } yield ()
  }

  "multipage export with last prefix" should "work correctly" in withImplAndStore { (impl, store) =>
    val typedStore = store.toByteArrayTypedStore(RadixHistory.kCodec, RadixHistory.vCodec)
    for {
      // Create tree with 6 leafs
      rootNodeAndHashOpt <- impl.saveAndCommit(RadixTree.EmptyNode, referenceInsertActions)
      (_, rootHash)       = rootNodeAndHashOpt.get

      validateData                            <- validateMultipageExport(rootHash, typedStore, withSkip = false)
      (firstExportData, reconstructExportData) = (
                                                   validateData.firstExport._1,
                                                   validateData.reconstructExport._1,
                                                 )
      (firstExport, reconstructExport)         = (validateData.firstExport, validateData.reconstructExport)

      _ = firstExportData.nodeKeys.size shouldBe 4
      _ = firstExport shouldBe reconstructExport
      _ = firstExportData.leafPrefixes shouldBe referenceLeafPrefixes
      _ = firstExportData.leafValues shouldBe referenceLeafValues
      _ = reconstructExportData.leafPrefixes shouldBe referenceLeafPrefixes
      _ = reconstructExportData.leafValues shouldBe referenceLeafValues
    } yield ()
  }

  "multipage export with skip" should "work correctly" in withImplAndStore { (impl, store) =>
    val typedStore = store.toByteArrayTypedStore(RadixHistory.kCodec, RadixHistory.vCodec)
    for {
      // Create tree with 6 leafs
      rootNodeAndHashOpt <- impl.saveAndCommit(RadixTree.EmptyNode, referenceInsertActions)
      (_, rootHash)       = rootNodeAndHashOpt.get

      validateData                              <- validateMultipageExport(rootHash, typedStore, withSkip = true)
      (firstExportData1, reconstructExportData2) = (
                                                     validateData.firstExport._1,
                                                     validateData.reconstructExport._1,
                                                   )
      (firstExport, reconstructExport)           = (validateData.firstExport, validateData.reconstructExport)

      _ = firstExportData1.nodeKeys.size shouldBe 4
      _ = firstExport shouldBe reconstructExport
      _ = firstExportData1.leafPrefixes shouldBe referenceLeafPrefixes
      _ = firstExportData1.leafValues shouldBe referenceLeafValues
      _ = reconstructExportData2.leafPrefixes shouldBe referenceLeafPrefixes
      _ = reconstructExportData2.leafValues shouldBe referenceLeafValues
    } yield ()
  }

  "sequentialExport with non-existing tree" should "return empty data" in withImplAndStore { (impl, store) =>
    val typedStore    = store.toByteArrayTypedStore(RadixHistory.kCodec, RadixHistory.vCodec)
    val emptyRootHash = impl.saveNode(EmptyNode)
    for {
      exported1         <- sequentialExport(
                             emptyRootHash,
                             None,
                             skipSize = 0,
                             takeSize = 100,
                             typedStore.get1,
                             exportSettings,
                           )
      referenceEmptyData = (ExportData(Vector(), Vector(), Vector(), Vector(), Vector()), none)
      _                  = exported1 shouldBe referenceEmptyData
    } yield ()
  }

  "function commonPrefix" should "return correct prefixes" in {
    val v12345 = KeySegment(createBA(1, 2, 3, 4, 5))
    val v1245  = KeySegment(createBA(1, 2, 4, 5))
    val v123   = KeySegment(createBA(1, 2, 3))
    val v12367 = KeySegment(createBA(1, 2, 3, 6, 7))
    val v22345 = KeySegment(createBA(2, 2, 3, 4, 5))
    val res1   = commonPrefix(v12345, v1245)
    val res2   = commonPrefix(v12345, v123)
    val res3   = commonPrefix(v12345, KeySegment.Empty)
    val res4   = commonPrefix(v12345, v12367)
    val res5   = commonPrefix(v22345, v12345)
    val res6   = commonPrefix(KeySegment.Empty, KeySegment.Empty)

    val referenceRes1 = (
      KeySegment(createBA(1, 2)),
      KeySegment(createBA(3, 4, 5)),
      KeySegment(createBA(4, 5)),
    )

    val referenceRes2 = (
      KeySegment(createBA(1, 2, 3)),
      KeySegment(createBA(4, 5)),
      KeySegment.Empty,
    )

    val referenceRes3 =
      (KeySegment.Empty, KeySegment(createBA(1, 2, 3, 4, 5)), KeySegment.Empty)

    val referenceRes4 = (
      KeySegment(createBA(1, 2, 3)),
      KeySegment(createBA(4, 5)),
      KeySegment(createBA(6, 7)),
    )

    val referenceRes5 = (
      KeySegment.Empty,
      KeySegment(createBA(2, 2, 3, 4, 5)),
      KeySegment(createBA(1, 2, 3, 4, 5)),
    )

    val referenceRes6 = (KeySegment.Empty, KeySegment.Empty, KeySegment.Empty)

    res1 shouldBe referenceRes1
    res2 shouldBe referenceRes2
    res3 shouldBe referenceRes3
    res4 shouldBe referenceRes4
    res5 shouldBe referenceRes5
    res6 shouldBe referenceRes6
  }

  "create KeySegment object with key size more than 127 bytes" should "be stopped with exception" in {
    try {
      val seqLongKey = Seq.fill(128)(0.toByte)
      KeySegment(seqLongKey)
    } catch {
      case ex: Exception =>
        ex shouldBe a[Exception]
        ex.getMessage shouldBe "requirement failed: Size of key segment is more than 127"
    }
  }

  def createBlakeHash(s: String): Blake2b256Hash = {
    val notEmptyPart = createBA(s)
    val emptyPart    = List.fill(32 - notEmptyPart.size)(0x00.toByte)
    Blake2b256Hash.fromByteArrayUnsafe(ByteArray(emptyPart) ++ notEmptyPart)
  }

  def createBA(s: String): ByteArray = ByteArray(Base16.unsafeDecode(s))

  /** Constructs a ByteArray from a list of literal bytes.
   * Only the least significant byte is used of each integral value. */
  @SuppressWarnings(Array("org.wartremover.warts.Var"))
  def createBA[A: Integral](bytes: A*): ByteArray = {
    val integral = implicitly[Integral[A]]
    val buf      = new Array[Byte](bytes.size)
    var i        = 0
    bytes.foreach { b =>
      buf(i) = integral.toInt(b).toByte
      i += 1
    }
    ByteArray(buf)
  }

  def createKeySegment(s: String): KeySegment                         = KeySegment(createBA(s))
  def createInsertActions(dataSet: List[radixKV]): List[InsertAction] =
    dataSet.map { ds =>
      InsertAction(ds.rKey, ds.rValue)
    }

  case class radixKV(rKey: KeySegment, rValue: Blake2b256Hash)

  object radixKV {
    def apply(strKey: String, strValue: String): radixKV =
      new radixKV(createKeySegment(strKey), createBlakeHash(strValue))
  }

  /*
        key       |   value
     111122334455 | 0000...0001
     11112233AABB | 0000...0002
     1111AABBCC   | 0000...0003
     33           | 0000...0004
     FF0011       | 0000...0005
     FF012222     | 0000...0006
   */
  private val treeDataSet = List(
    radixKV("111122334455", "01"),
    radixKV("11112233AABB", "02"),
    radixKV("1111AABBCC", "03"),
    radixKV("33", "04"),
    radixKV("FF0011", "05"),
    radixKV("FF012222", "06"),
  )

  private val referenceLeafPrefixes = treeDataSet.map(_.rKey)
  private val referenceLeafValues   = treeDataSet.map(_.rValue)

  private val referenceInsertActions = createInsertActions(treeDataSet)

  private val exportSettings = ExportDataSettings(
    flagNodePrefixes = true,
    flagNodeKeys = true,
    flagNodeValues = true,
    flagLeafPrefixes = true,
    flagLeafValues = true,
  )
  case class ExportParameters(
    rootHash: Blake2b256Hash,      // hash
    typedStore: KeyValueTypedStore[IO, Blake2b256Hash, ByteArray],
    takeSize: Int,                 // take size
    skipSize: Int,                 // skip size
    withSkip: Boolean,             // start with skip is true
    exportData: ExportData,
    lastPrefix: Option[KeySegment],// last prefix
  )

  case class MultipageExportResults(
    firstExport: (ExportData, Option[KeySegment]),
    reconstructExport: (ExportData, Option[KeySegment]),
  )

  def validateMultipageExport(
    rootHash: Blake2b256Hash,
    store: KeyValueTypedStore[IO, Blake2b256Hash, ByteArray],
    withSkip: Boolean,
  ): IO[MultipageExportResults] = {

    def multipageExport(p: ExportParameters): IO[Either[ExportParameters, ExportParameters]] = {
      def collectExportData(prevData: ExportData, pageData: ExportData): ExportData =
        ExportData(
          prevData.nodePrefixes ++ pageData.nodePrefixes,
          prevData.nodeKeys ++ pageData.nodeKeys,
          prevData.nodeValues ++ pageData.nodeValues,
          prevData.leafPrefixes ++ pageData.leafPrefixes,
          prevData.leafValues ++ pageData.leafValues,
        )
      for {
        exported           <- sequentialExport(
                                rootHash,
                                if (withSkip || p.lastPrefix.get.isEmpty) None else p.lastPrefix,
                                if (withSkip) p.skipSize else 0,
                                p.takeSize,
                                store.get1,
                                exportSettings,
                              )
        (pageExportData, _) = exported
        pageKVDBKeys        = pageExportData.nodeKeys
        pageKVDBValues      = pageExportData.nodeValues
        result              = ExportParameters(
                                rootHash,
                                store,
                                p.takeSize,
                                if (withSkip) p.skipSize + p.takeSize else p.skipSize,
                                withSkip,
                                collectExportData(p.exportData, pageExportData),
                                exported._2,
                              )
      } yield
        if (pageKVDBKeys.isEmpty && pageKVDBValues.isEmpty) result.asRight
        else result.asLeft
    }

    // Initialize structure for export
    val initSeq        = Seq[ByteArray]()
    val initExportData =
      ExportData(Seq[KeySegment](), Seq.empty, initSeq, Seq[KeySegment](), Seq.empty)
    val initParameters = ExportParameters(
      rootHash,
      store,
      takeSize = 2,
      skipSize = 0,
      withSkip,
      initExportData,
      Option(KeySegment.Empty),
    )
    for {
      allExport       <- initParameters.tailRecM(multipageExport)
      firstExportData  = (allExport.exportData, allExport.lastPrefix)
      (exportData1, _) = firstExportData
      nodeKVDBKeys     = exportData1.nodeKeys
      nodeKVDBValues   = exportData1.nodeValues
      localStorage     = (nodeKVDBKeys zip nodeKVDBValues).toMap

      // Export data from new storage
      reconstructExportData <- sequentialExport(
                                 rootHash,
                                 None,
                                 skipSize = 0,
                                 takeSize = 100,
                                 x =>
                                   Sync[IO]
                                     .delay(localStorage.get(x)),
                                 exportSettings,
                               )

      result = MultipageExportResults(firstExportData, reconstructExportData)
    } yield result
  }

  private def withImplAndStore(
    f: (
      RadixTreeImpl[IO],
      InMemoryKeyValueStore[IO],
    ) => IO[Unit],
  ): Unit = {

    val store         = InMemoryKeyValueStore[IO]()
    val typedStore    = store.toByteArrayTypedStore(RadixHistory.kCodec, RadixHistory.vCodec)
    val radixTreeImpl = new RadixTreeImpl[IO](typedStore)
    f(radixTreeImpl, store).timeout(20.seconds).unsafeRunSync()
  }
};
