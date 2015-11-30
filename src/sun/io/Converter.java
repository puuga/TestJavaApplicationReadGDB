/*
 * @(#)Converter.java        0.0        09/07/29
 *
 * Copyright 2006 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 * Contributors: Ulf Zibis, Germany, Ulf.Zibis @ CoSoCo.de
 */

package sun.io;

/**
 * An interface for subclasses which convert Unicode characters
 * into an external encoding and vice versa.
 *
 * @author Ulf Zibis <Ulf.Zibis at CoSoCo.de>
 *
 * @deprecated Replaced by {@link java.nio.charset}.  THIS API WILL BE
 * REMOVED IN J2SE 1.6.
 */
@Deprecated
public abstract class Converter {

    /**
     * Substitution mode flag.
     */
    protected boolean subMode = true;

    /**
     * Offset of next character to be processed
     */
    protected int charOff;

    /**
     * Offset of next byte to be processed
     */
    protected int byteOff;

    /**
     * Length of bad input that caused a MalformedInputException.
     */
    protected int badInputLength;

    private final String encoding;

    Converter(String encoding) {
        this.encoding = encoding;
    }

    /**
     * Returns the character set id for the conversion
     */
    public String getCharacterEncoding() {
        return encoding;
    }

    /**
     * Sets converter into substitution mode. In substitution mode, the
     * converter will replace untranslatable bytes/chars in the source encoding
     * with the substitution bytes/chars set by setSubstitutionBytes/Chars.
     * When not in substitution mode, the converter will throw an
     * UnknownCharacterException when it encounters untranslatable input.
     *
     * @param doSub if true, enable substitution mode.
     * @see ByteToCharConverter#setSubstitutionChars
     * @see CharToByteConverter#setSubstitutionBytes
     */
    public void setSubstitutionMode(boolean doSub) {
        subMode = doSub;
    }

    /**
     * Resets converter to its initial state.
     */
    public void reset() {
        byteOff = charOff = 0;
    }

    /**
     * Returns the index of the character just past the last character
     * successfully processed by the previous call to convert.
     */
    public int nextCharIndex() {
        return charOff;
    }


    /**
     * Returns the index of the byte just past the last byte
     * successfully processed by the previous call to convert.
     */
    public int nextByteIndex() {
        return byteOff;
    }

    /**
     * Returns the length, in bytes/chars, of the input which caused a
     * MalformedInputException. Always refers to the last
     * MalformedInputException thrown by the converter. If none have
     * ever been thrown, returns 0.
     */
    public int getBadInputLength() {
        return badInputLength;
    }
}
