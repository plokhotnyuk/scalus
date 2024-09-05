package scalus.builtin
import supranational.blst.*
import scalus.utils.Hex

case class BLS12_381_G1_Element(p: P1):
    def value: ByteString = ByteString.fromArray(p.compress())
    override def equals(that: Any): Boolean = that match
        case that: BLS12_381_G1_Element => p.is_equal(that.p)
        case _                          => false
    // TODO: check if this is correct
    override def toString(): String = s"0x${Hex.bytesToHex(p.compress())}"

object BLS12_381_G1_Element:
    def apply(value: ByteString): BLS12_381_G1_Element = BLS12_381_G1_Element(new P1(value.bytes))

case class BLS12_381_G2_Element(p: P2):
    def value: ByteString = ByteString.fromArray(p.compress())
    override def equals(that: Any): Boolean = that match
        case that: BLS12_381_G2_Element => p.is_equal(that.p)
        case _                          => false

object BLS12_381_G2_Element:
    def apply(value: ByteString): BLS12_381_G2_Element = BLS12_381_G2_Element(new P2(value.bytes))

case class BLS12_381_MlResult(value: PT)

enum ByteOrder extends Enum[ByteOrder]:
    case BigEndian, LittleEndian
