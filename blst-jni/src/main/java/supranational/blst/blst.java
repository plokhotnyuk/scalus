/* ----------------------------------------------------------------------------
 * This file was automatically generated by SWIG (http://www.swig.org).
 * Version 3.0.12
 *
 * Do not make changes to this file unless you know what you are doing--modify
 * the SWIG interface file instead.
 * ----------------------------------------------------------------------------- */

package supranational.blst;

public class blst {
  public static P1 G1() {
    return new P1(blstJNI.G1());
}

  public static P2 G2() {
    return new P2(blstJNI.G2());
}

  public static P1_Affine getBLS12_381_G1() {
    return new P1_Affine(blstJNI.BLS12_381_G1_get());
}

  public static P1_Affine getBLS12_381_NEG_G1() {
    return new P1_Affine(blstJNI.BLS12_381_NEG_G1_get());
}

  public static P2_Affine getBLS12_381_G2() {
    return new P2_Affine(blstJNI.BLS12_381_G2_get());
}

  public static P2_Affine getBLS12_381_NEG_G2() {
    return new P2_Affine(blstJNI.BLS12_381_NEG_G2_get());
}

}
