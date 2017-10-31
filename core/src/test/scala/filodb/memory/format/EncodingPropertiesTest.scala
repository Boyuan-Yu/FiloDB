package filodb.memory.format

import filodb.memory.NativeMemoryManager
import filodb.memory.format.vectors.UTF8Vector

import org.scalatest.prop.PropertyChecks
import org.scalatest.{FunSpec, Matchers}

class EncodingPropertiesTest extends FunSpec with Matchers with PropertyChecks {
  import filodb.memory.format.Encodings._

  import org.scalacheck._
  import Arbitrary.arbitrary

  // Generate a list of bounded integers, every time bound it slightly differently
  // (to test different int compression techniques)
  def boundedIntList: Gen[Seq[Option[Int]]] =
    for {
      minVal <- Gen.oneOf(Int.MinValue, -65536, -32768, -256, -128, 0)
      maxVal <- Gen.oneOf(15, 127, 255, 32767, Int.MaxValue)
      seqOptList <- Gen.containerOf[Seq, Option[Int]](
                      noneOrThing[Int](Arbitrary(Gen.choose(minVal, maxVal))))
    } yield { seqOptList }

  // Write our own generator to force frequent NA elements
  def noneOrThing[T](implicit a: Arbitrary[T]): Gen[Option[T]] =
    Gen.frequency((5, arbitrary[T].map(Some(_))),
                  (1, Gen.const(None)))

  def optionList[T](implicit a: Arbitrary[T]): Gen[Seq[Option[T]]] =
    Gen.containerOf[Seq, Option[T]](noneOrThing[T])

  implicit val utf8arb = Arbitrary(arbitrary[String].map(ZeroCopyUTF8String.apply))

  it("should match elements and length for BinaryIntVectors with missing/NA elements") {
    val memFactory = new NativeMemoryManager(1000 * 1024)
    import filodb.memory.format.vectors.IntBinaryVector
    forAll(boundedIntList) { s =>
      val intVect = IntBinaryVector.appendingVector(memFactory, 1000)
      s.foreach(intVect.add)
      val binarySeq = FiloVector[Int](intVect.optimize(memFactory).toFiloBuffer)
      binarySeq.length should equal (s.length)
      val elements = binarySeq.optionIterator.toSeq
      elements should equal (s)
    }
  }

  it("should match elements and length for offheap BinaryIntVectors with missing/NA elements") {
    import filodb.memory.format.vectors.IntBinaryVector
    val memFactory = new NativeMemoryManager(1000 * 1024)
    forAll(boundedIntList) { s =>
      val intVect = IntBinaryVector.appendingVector(memFactory, 1000)
      s.foreach(intVect.add)
      val binarySeq = FiloVector[Int](intVect.optimize(memFactory).toFiloBuffer)
      binarySeq.length should equal (s.length)
      val elements = binarySeq.optionIterator.toSeq
      elements should equal (s)
    }
  }


  it("should match elements and length for UTF8Vectors with missing/NA elements") {
    forAll(optionList[ZeroCopyUTF8String]) { s =>
      val memFactory = new NativeMemoryManager(100 * 1024)
      val utf8vect = UTF8Vector.appendingVector(memFactory, 500)
      s.foreach(utf8vect.add)
      val buf = utf8vect.optimize(memFactory).toFiloBuffer
      val binarySeq = FiloVector[ZeroCopyUTF8String](buf)
      binarySeq.length should equal (s.length)
      val elements = binarySeq.optionIterator.toSeq
      elements should equal (s)
    }
  }



  it("should match elements and length for DictUTF8Vectors with missing/NA elements") {
    forAll(optionList[ZeroCopyUTF8String]) { s =>
      val memFactory = new NativeMemoryManager(100 * 1024)
      val utf8strs = s.map(_.getOrElse(ZeroCopyUTF8String.NA))
      val buf = UTF8Vector(memFactory, utf8strs).optimize(memFactory,
                                                          AutoDictString(spaceThreshold=0.8)).toFiloBuffer
      val binarySeq = FiloVector[ZeroCopyUTF8String](buf)
      binarySeq.length should equal (s.length)
      val elements = binarySeq.optionIterator.toSeq
      elements should equal (s)
    }
  }
}