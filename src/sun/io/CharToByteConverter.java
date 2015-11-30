/*
 * @(#)CharToByteConverter.java        1.45        05/11/17
 *
 * Copyright 2006 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 * Contributors: Ulf Zibis, Germany, Ulf.Zibis @ CoSoCo.de
 */

package sun.io;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;

/**
 * An abstract base class for subclasses which convert Unicode
 * characters into an external encoding.
 *
 * @author Asmus Freytag
 * @author Lloyd Honomichl, Novell, Inc.
 *
 * @deprecated Replaced by {@link java.nio.charset}.  THIS API WILL BE
 * REMOVED IN J2SE 1.6.
 */
@Deprecated
public class CharToByteConverter extends Converter {

    /**
     * Bytes to substitute for unmappable input.
     */
    protected byte[] subBytes = { (byte)'?' };

    private final CharsetEncoder encoder;
    private CharBuffer src;
    private ByteBuffer dst;

    protected CharToByteConverter() { // only provided for subclasses
        this(null, null);
    }
    CharToByteConverter(Charset charset, String encoding) {
        super(encoding);
        encoder = charset.newEncoder()
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        subBytes = encoder.replacement();
    }

    /**
     * Create an instance of the default CharToByteConverter subclass.
     */
    public static CharToByteConverter getDefault() {
        Object cvt;
        cvt = Converters.newDefaultConverter(Converters.CHAR_TO_BYTE);
        return (CharToByteConverter)cvt;
    }

    /**
     * Returns appropriate CharToByteConverter subclass instance.
     * @param string represets encoding
     */
    public static CharToByteConverter getConverter(String encoding)
            throws UnsupportedEncodingException {
        Object cvt;
        cvt = Converters.newConverter(Converters.CHAR_TO_BYTE, encoding);
        return (CharToByteConverter)cvt;
    }

    /**
     * Converts an array of Unicode characters into an array of bytes
     * in the target character encoding. This method allows a buffer by
     * buffer conversion of a data stream. The state of the conversion is
     * saved between calls to convert. If a call to convert results in
     * an exception, the conversion may be continued by calling convert again
     * with suitably modified parameters. All conversions should be finished
     * with a call to the flush method.
     *
     * @return the number of bytes written to output.
     * @param input array containing Unicode characters to be converted.
     * @param inStart begin conversion at this offset in input array.
     * @param inEnd stop conversion at this offset in input array (exclusive).
     * @param output byte array to receive conversion result.
     * @param outStart start writing to output array at this offset.
     * @param outEnd stop writing to output array at this offset (exclusive).
     * @exception MalformedInputException if the input buffer contains any
     * sequence of chars that is illegal in Unicode (principally unpaired
     * surrogates and \uFFFF or \uFFFE). After this exception is thrown,
     * the method nextCharIndex can be called to obtain the index of the
     * first invalid input character. The MalformedInputException can
     * be queried for the length of the invalid input.
     * @exception UnknownCharacterException for any character that
     * that cannot be converted to the external character encoding. Thrown
     * only when converter is not in substitution mode.
     * @exception ConversionBufferFullException if output array is filled prior
     * to converting all the input.
     */
    public int convert(char[] input, int inStart, int inEnd,
                       byte[] output, int outStart, int outEnd)
            throws UnknownCharacterException, MalformedInputException,
                   ConversionBufferFullException {

        charOff = inStart;
        byteOff = outStart;
        // throw exceptions compatible to legacy CharToByteXxx converters
        if (inStart >= inEnd)   return 0;
        if (inStart >= input.length)
            throw new ArrayIndexOutOfBoundsException(inStart);
        if (outStart >= outEnd || outStart >= output.length)
            throw new ConversionBufferFullException();

        if (src != null && src.array() == input)
            src.position(inStart).limit(inEnd);
        else
            src = CharBuffer.wrap(input, inStart, inEnd-inStart);
        if (dst != null && dst.array() == output)
            dst.position(outStart).limit(outEnd);
        else
            dst = ByteBuffer.wrap(output, outStart, outEnd-outStart);

        CoderResult cr;
        try {
            cr = encoder.encode(src, dst, false);
        } catch (IllegalStateException ise) {
            cr = encoder.reset().encode(src, dst, false);
        } finally {
            charOff = src.position();
            byteOff = dst.position();
        }
        if (cr.isUnmappable()) {
            badInputLength = cr.length();
            throw new UnknownCharacterException();
        }
        if (cr.isMalformed()) {
            badInputLength = cr.length();
            throw new MalformedInputException();
        }
        if (cr.isOverflow())
            throw new ConversionBufferFullException();

        // Return the length written to the output buffer
        if (cr.isUnderflow())
            return byteOff - outStart;
        return -1; // should be never reached
    }

    /** Converts any array of characters, including malformed surrogate
     * pairs, into an array of bytes in the target character encoding.
     * A precondition is that substitution mode is turned on. This method
     * allows a buffer by buffer conversion of a data stream.
     * The state of the conversion is saved between calls to convert.
     * All conversions should be finished with a call to the flushAny method.
     *
     * @return the number of bytes written to output.
     * @param input array containing Unicode characters to be converted.
     * @param inStart begin conversion at this offset in input array.
     * @param inEnd stop conversion at this offset in input array (exclusive).
     * @param output byte array to receive conversion result.
     * @param outStart start writing to output array at this offset.
     * @param outEnd stop writing to output array at this offset (exclusive).
     * @exception ConversionBufferFullException if output array is filled prior
     * to converting all the input.
     */
    public int convertAny(char[] input, int inStart, int inEnd,
                          byte[] output, int outStart, int outEnd)
            throws ConversionBufferFullException {

        if (!subMode)       // Precondition: subMode == true
            throw new IllegalStateException("Substitution mode is not on");
        // Rely on the untested precondition that the indices are meaningful
        // For safety, use the public interface to charOff and byteOff, but
        // badInputLength is directly modified.
        int localInOff = inStart;
        int localOutOff = outStart;
        while(localInOff < inEnd) {
            try {
                convert(input, localInOff, inEnd, output, localOutOff, outEnd);
                return (nextByteIndex() - outStart);
            } catch (MalformedInputException e) {
                final byte[] s = subBytes;
                final int subSize = s.length;
                localOutOff = nextByteIndex();
                if ((localOutOff + subSize) > outEnd)
                    throw new ConversionBufferFullException();
                for (int i=0; i<subSize; i++)
                    output[localOutOff++] = s[i];
                localInOff = nextCharIndex() + badInputLength;
                badInputLength = 0;
                if (localInOff >= inEnd) {
                    byteOff = localOutOff;
                    return (byteOff - outStart);
                }
                continue;
            } catch (UnknownCharacterException e) {
                // Should never occur, since subMode == true
                throw new Error("UnknownCharacterException thrown in substititution mode", e);
            }
        }
        return nextByteIndex() - outStart;
    }

    /**
     * Converts an array of Unicode characters into an array of bytes
     * in the target character encoding. Unlike convert, this method
     * does not do incremental conversion. It assumes that the given
     * input array contains all the characters to be converted. The
     * state of the converter is reset at the beginning of this method
     * and is left in the reset state on successful termination.
     * The converter is not reset if an exception is thrown.
     * This allows the caller to determine where the bad input
     * was encountered by calling nextCharIndex.
     * <p>
     * This method uses substitution mode when performing the conversion.
     * The method setSubstitutionBytes may be used to determine what
     * bytes are substituted. Even though substitution mode is used,
     * the state of the converter's substitution mode is not changed
     * at the end of this method.
     *
     * @return an array of bytes containing the converted characters.
     * @param input array containing Unicode characters to be converted.
     * @exception MalformedInputException if the input buffer contains any
     * sequence of chars that is illegal in Unicode (principally unpaired
     * surrogates and \uFFFF or \uFFFE). After this exception is thrown,
     * the method nextCharIndex can be called to obtain the index of the
     * first invalid input character and getBadInputLength can be called
     * to determine the length of the invalid input.
     *
     * @see   #nextCharIndex
     * @see   #setSubstitutionMode
     * @see   #setSubstitutionBytes
     * @see   #getBadInputLength
     */
    public byte[] convertAll(char[] input) throws MalformedInputException {
        reset();
        boolean savedSubMode = subMode;
        setSubstitutionMode(true);
        byte[] output = new byte[getMaxBytesPerChar() * input.length];

        try {
            int outputLength = convert(input, 0, input.length,
                                       output, 0, output.length);
            outputLength += flush(output, nextByteIndex(), output.length);
                       byte[] returnedOutput = new byte[outputLength];
            System.arraycopy(output, 0, returnedOutput, 0, outputLength);
            return returnedOutput;
        }
        catch (ConversionBufferFullException e) {
            // Not supposed to happen. If it does, getMaxBytesPerChar() lied.
            throw new
                InternalError("this.getMaxBytesPerChar returned bad value");
        }
        catch (UnknownCharacterException e) {
            // Not supposed to happen since we're in substitution mode.
            throw new InternalError();
        }
        finally {
            setSubstitutionMode(savedSubMode);
        }
    }
    /**
     * Writes any remaining output to the output buffer and resets the
     * converter to its initial state.
     * @param output byte array to receive flushed output.
     * @param outStart start writing to output array at this offset.
     * @param outEnd stop writing to output array at this offset (exclusive).
     * @exception MalformedInputException if the output to be flushed contained
     * a partial or invalid multibyte character sequence. Will occur if the
     * input buffer on the last call to convert ended with the first character
     * of a surrogate pair. flush will write what it can to the output buffer
     * and reset the converter before throwing this exception. An additional
     * call to flush is not required.
     * @exception ConversionBufferFullException if output array is filled     * before all the output can be flushed. flush will write what it can
     * to the output buffer and remember its state. An additional call to
     * flush with a new output buffer will conclude the operation.
     */
    public int flush(byte[] output, int outStart, int outEnd)
            throws MalformedInputException, ConversionBufferFullException {

        charOff = byteOff = 0;
        if (outStart >= outEnd || outStart >= output.length)
            throw new ConversionBufferFullException();
        if (dst != null && dst.array() == output)
            dst.position(outStart).limit(outEnd);
        else
            dst = ByteBuffer.wrap(output, outStart, outEnd-outStart);

        CoderResult cr = null;
        try {
            if (src != null)
                cr = encoder.encode((CharBuffer)src.clear(), dst, true);
            assert !cr.isUnmappable();
            if (cr.isMalformed()) {
                badInputLength = cr.length();
                reset();
                throw new MalformedInputException();
            }
        } catch (IllegalStateException ise) {
            if (src != null)
                cr = encoder.reset().encode(src, dst, true);
        }
        try {
            cr = encoder.flush(dst);
        } catch (Exception e) {
            assert false;
        } finally {
            charOff = 0;
            byteOff = dst.position();
            src = null;
        }
        if (cr.isOverflow())
            throw new ConversionBufferFullException();

        // Return the length written to the output buffer
        if (cr.isUnderflow()) {
            int written = byteOff - outStart;
            reset();
            return written;
        }
        assert false;
        return -1; // should be never reached
    }

    /**
     * Writes any remaining output to the output buffer and resets the converter
     * to its initial state. May only be called when substitution mode is
     * turned on, and never complains about malformed input (always substitutes).
     *
     * @param output byte array to receive flushed output.
     * @param outStart start writing to output array at this offset.
     * @param outEnd stop writing to output array at this offset (exclusive).
     * @return number of bytes writter into output.
     * @exception ConversionBufferFullException if output array is filled
     * before all the output can be flushed. flush will write what it can
     * to the output buffer and remember its state. An additional call to
     * flush with a new output buffer will conclude the operation.
     */
    public int flushAny(byte[] output, int outStart, int outEnd)
            throws ConversionBufferFullException {
        if (!subMode)         // Precondition: subMode == true
            throw new IllegalStateException("Substitution mode is not on");
        try {
            return flush(output, outStart, outEnd);
        } catch (MalformedInputException e) {
            // Assume that if a malformed input exception has occurred,
            // no useful data has been placed in the output buffer.
            // i.e. there is no mixture of left over good + some bad data.
            // Usually occurs with a trailing high surrogate pair element.
            // Special cases occur in Cp970, 949c and 933 that seem
            // to be covered, but may require further investigation
            final byte[] s = subBytes;
            final int subSize = s.length;
            int outIndex = outStart;
            if ((outStart + subSize) > outEnd)
                throw new ConversionBufferFullException();
            for (int i=0; i<subSize; i++)
                output[outIndex++] = s[i];
            byteOff = charOff = 0; // Reset the internal state.
            badInputLength = 0;
            return subSize;
        }
    }

    /**
     * Resets converter to its initial state.
     */
    public void reset() {
        encoder.reset();
        super.reset();
        src = null;
        dst = null;
    }

    /**
     * Returns true if the given character can be converted to the
     * target character encoding.
     * @return true if given character is translatable, false otherwise.
     * @param c character to test
     */
    public boolean canConvert(char c) {
        if (encoder != null)
            try {
                return encoder.canEncode(c);
            } catch (IllegalStateException ise) {
                return encoder.reset().canEncode(c);
            }
        else // only provided for subclasses
            try {
                char[] input = new char[1];
                byte[] output = new byte[getMaxBytesPerChar()];
                input[0] = c;
                convert(input, 0, 1, output, 0, getMaxBytesPerChar());
                return true;
            } catch (CharConversionException e) {
                return false;
            }
    }

    /**
     * Returns the maximum number of bytes needed to convert a char. Useful
     * for calculating the maximum output buffer size needed for a particular
     * input buffer.
     */
    public int getMaxBytesPerChar() {
        if (encoder != null)
            return (int)Math.ceil(encoder.maxBytesPerChar());
        else // only provided for subclasses, should be overridden
            throw new UnsupportedOperationException();
    }

    /**
     * Sets converter into substitution mode. In substitution mode,
     * the converter will replace untranslatable characters in the source
     * encoding with the substitution character set by setSubstitutionBytes.
     * When not in substitution mode, the converter will throw an
     * UnknownCharacterException when it encounters untranslatable input.
     *
     * @param doSub if true, enable substitution mode.
     * @see #setSubstitutionBytes
     */
    public void setSubstitutionMode(boolean doSub) {
        super.setSubstitutionMode(doSub);
        if (encoder != null)
            encoder.onUnmappableCharacter
                    (doSub ? CodingErrorAction.REPLACE : CodingErrorAction.REPORT);
    }

    /**
     * Sets the substitution bytes to use when the converter is in
     * substitution mode. The given bytes should represent a valid
     * character in the target character encoding and must not be
     * longer than the value returned by getMaxBytesPerChar for this
     * converter.
     *
     * @param bytes the substitution bytes
     * @exception IllegalArgumentException if given byte array is longer than
     *    the value returned by the method getMaxBytesPerChar.
     * @see #setSubstitutionMode
     * @see #getMaxBytesPerChar
     */
    public void setSubstitutionBytes(byte[] bytes) throws IllegalArgumentException {
        byte[] tempSubBytes = new byte[bytes.length];
        System.arraycopy(bytes, 0, tempSubBytes, 0, bytes.length);
        if (encoder != null)
            encoder.replaceWith(tempSubBytes);
        else // only provided for subclasses
            if(bytes.length > getMaxBytesPerChar())
                throw new IllegalArgumentException();
        subBytes = tempSubBytes;
    }

    /**
     * Returns a string representation of the class.
     */
    public String toString() {
        return "CharToByteConverter: "+getCharacterEncoding();
    }
}
