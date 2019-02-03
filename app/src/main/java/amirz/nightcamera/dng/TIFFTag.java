package amirz.nightcamera.dng;

import android.util.Rational;

public class TIFFTag {
    private final int type;
    private final Object[] value;

    public TIFFTag(int type, Object[] value) {
        this.type = type;
        this.value = value;
    }

    public int getInt() {
        return (int) value[0];
    }

    public int[] getIntArray() {
        int[] ints = new int[value.length];
        for (int i = 0; i < ints.length; i++) {
            if (type == TIFF.TYPE_UInt_16 || type == TIFF.TYPE_UInt_32) {
                ints[i] = (int) value[i];
            }
        }
        return ints;
    }

    public float[] getFloatArray() {
        float[] floats = new float[value.length];
        for (int i = 0; i < floats.length; i++) {
            if (type == TIFF.TYPE_Frac || type == TIFF.TYPE_UFrac) {
                floats[i] = ((Rational) value[i]).floatValue();
            }
        }
        return floats;
    }

    public Rational[] getRationalArray() {
        Rational[] rationals = new Rational[value.length];
        for (int i = 0; i < rationals.length; i++) {
            if (type == TIFF.TYPE_Frac || type == TIFF.TYPE_UFrac) {
                rationals[i] = (Rational) value[i];
            }
        }
        return rationals;
    }

    @Override
    public String toString() {
        StringBuilder buffer = new StringBuilder();
        if (type == TIFF.TYPE_String) {
            for (Object b : value) {
                buffer.append((char)(byte) b);
            }
        } else {
            for (int elementNum = 0; elementNum < value.length && elementNum < 20; elementNum++) {
                Object element = value[elementNum];
                if (element != null) {
                    buffer.append(element.toString()).append(" ");
                }
            }
        }
        return buffer.toString();
    }
}
