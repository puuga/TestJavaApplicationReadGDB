/*
 * @(#)ByteToCharConverter.java        1.41        05/11/17
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
 * An abstract base class for subclasses which convert character data
 * in an external encoding into Unicode characters.
 *
 * @author Asmus Freytag
 * @author Lloyd Honomichl
 *
 * @deprecated Replaced by {@link java.nio.charset}.
 * THIS API WILL BE REMOVED IN J2SE 1.6.
 */
@Deprecated
public class ByteToCharConverter extends Converter {

    /**
     * Characters to use for automatic substitution.  
     */
    protected char[] subChars = { '\uFFFD' };

    private final CharsetDecoder decoder;
    private ByteBuffer src;
    private CharBuffer dst;

    protected ByteToCharConverter() { // only provided for subclasses
        this(null, null);
    }
    ByteToCharConverter(Charset charset, String encoding) {
        super(encoding);
        decoder = charset.newDecoder()
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        // for compatibility to old ByteToCharASCII converter:
        if (charset.name().equals("US-ASCII"))
            decoder.onMalformedInput(CodingErrorAction.REPLACE);
    }

    /**
     * Create an instance of the default ByteToCharConverter subclass.
     */
    public static ByteToCharConverter getDefault() {
        Object cvt;
        cvt = Converters.newDefaultConverter(Converters.BYTE_TO_CHAR);
        return (ByteToCharConverter)cvt;
    }

    /**
     * Returns appropriate ByteToCharConverter subclass instance.
     * @param string represents encoding
     */
    public static ByteToCharConverter getConverter(String encoding)
            throws UnsupportedEncodingException {
        Object cvt;
        cvt = Converters.newConverter(Converters.BYTE_TO_CHAR, encoding);
        return (ByteToCharConverter)cvt;
    }

    /**
     * Converts an array of bytes containing characters in an external
     * encoding into an array of Unicode characters. This method allows
     * a buffer by buffer conversion of a data stream. The state of the
     * conversion is saved between calls to convert. Among other things,
     * this means multibyte input sequences can be split between calls.
     * If a call to convert results in an exception, the conversion may be
     * continued by calling convert again with suitably modified parameters.
     * All conversions should be finished with a call to the flush method.
     *
     * @return the number of bytes written to output.
     * @param input byte array containing text to be converted.
     * @param inStart begin conversion at this offset in input array.
     * @param inEnd stop conversion at this offset in input array (exclusive).
     * @param output character array to receive conversion result.
     * @param outStart start writing to output array at this offset.
     * @param outEnd stop writing to output array at this offset (exclusive).
     * @exception MalformedInputException if the input buffer contains any
     * sequence of bytes that is illegal for the input character set.
     * @exception UnknownCharacterException for any character that
     * that cannot be converted to Unicode. Thrown only when converter
     * is not in substitution mode.
     * @exception ConversionBufferFullException if output array is filled prior
     * to converting all the input.
     */
    public int convert(byte[] input, int inStart, int inEnd,
                       char[] output, int outStart, int outEnd)
            throws UnknownCharacterException, MalformedInputException,
                   ConversionBufferFullException {

        byteOff = inStart;
        charOff = outStart;
        // throw exceptions compatible to legacy ByteToCharXxx converters
        if (inStart >= inEnd)   return 0;
        if (inStart >= input.length)
            throw new ArrayIndexOutOfBoundsException(inStart);
        if (outStart >= outEnd || outStart >= output.length)
            throw new ConversionBufferFullException();

        if (src != null && src.array() == input)
            src.position(inStart).limit(inEnd);
        else
            src = ByteBuffer.wrap(input, inStart, inEnd-inStart);
        if (dst != null && dst.array() == output)
            dst.position(outStart).limit(outEnd);
        else
            dst = CharBuffer.wrap(output, outStart, outEnd-outStart);

        CoderResult cr;
        try {
            cr = decoder.decode(src, dst, false);
        } catch (IllegalStateException ise) {
            cr = decoder.reset().decode(src, dst, false);
        } finally {
            byteOff = src.position();
            charOff = dst.position();
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
            return charOff - outStart;
        return -1; // should be never reached
    }

    /**
     * Converts an array of bytes containing characters in an external
     * encoding into an array of Unicode characters. Unlike convert,
     * this method does not do incremental conversion. It assumes that
     * the given input array contains all the characters to be
     * converted. The state of the converter is reset at the beginning
     * of this method and is left in the reset state on successful
     * termination. The converter is not reset if an exception is
     * thrown. This allows the caller to determine where the bad input
     * was encountered by calling nextByteIndex.
     * <p>
     * This method uses substitution mode when performing the
     * conversion. The method setSubstitutionChars may be used to
     * determine what characters are substituted. Even though substitution
     * mode is used, the state of the converter's substitution mode is
     * not changed at the end of this method.
     *
     * @return an array of chars containing the converted characters.
     * @param input array containing Unicode characters to be converted.
     * @exception MalformedInputException if the input buffer contains any
     * sequence of chars that is illegal in the input character encoding.
     * After this exception is thrown,
     * the method nextByteIndex can be called to obtain the index of the
     * first invalid input byte and getBadInputLength can be called
     * to determine the length of the invalid input.
     *
     * @see   #nextByteIndex
     * @see   #setSubstitutionMode
     * @see   sun.io.CharToByteConverter#setSubstitutionBytes(byte[])
     * @see   #getBadInputLength
     */
    public char[] convertAll(byte[] input) throws MalformedInputException {
        reset();
        boolean savedSubMode = subMode;
        setSubstitutionMode(true);
        char[] output = new char[getMaxCharsPerByte() * input.length];

        try {
            int outputLength = convert(input, 0, input.length,
                                       output, 0, output.length);
            outputLength += flush(output, outputLength, output.length);
            char[] returnedOutput = new char[outputLength];
            System.arraycopy(output, 0, returnedOutput, 0, outputLength);
            return returnedOutput;
        }
        catch(ConversionBufferFullException e) {
            // Not supposed to happen. If it does, getMaxCharsPerByte() lied.
            throw new
                InternalError("this.getMaxCharsBerByte returned bad value");
        }
        catch(UnknownCharacterException e) {
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
     * @param output char array to receive flushed output.
     * @param outStart start writing to output array at this offset.
     * @param outEnd stop writing to output array at this offset (exclusive).
     * @exception MalformedInputException if the output to be flushed contained
     * a partial or invalid multibyte character sequence. flush will
     * write what it can to the output buffer and reset the converter before
     * throwing this exception. An additional call to flush is not required.
     * @exception ConversionBufferFullException if output array is filled
     * before all the output can be flushed. flush will write what it can
     * to the output buffer and remember its state. An additional call to
     * flush with a new output buffer will conclude the operation.
     */
    public int flush(char[] output, int outStart, int outEnd)
            throws MalformedInputException, ConversionBufferFullException {

        byteOff = charOff = 0;
        if (outStart >= outEnd || outStart >= output.length)
            throw new ConversionBufferFullException();
        if (dst != null && dst.array() == output)
            dst.position(outStart).limit(outEnd);
        else
            dst = CharBuffer.wrap(output, outStart, outEnd-outStart);

        CoderResult cr = null;
        try {
            if (src != null)
                cr = decoder.decode((ByteBuffer)src.clear(), dst, true);
            assert !cr.isUnmappable();
            if (cr.isMalformed()) {
                badInputLength = cr.length();
                reset();
                throw new MalformedInputException();
            }
        } catch (IllegalStateException ise) {
            if (src != null)
                cr = decoder.reset().decode(src, dst, true);
        }
        try {
            cr = decoder.flush(dst);
        } catch (Exception e) {
            assert false;
        } finally {
            byteOff = 0;
            charOff = dst.position();
            src = null;
        }
        if (cr.isOverflow())
            throw new ConversionBufferFullException();

        // Return the length written to the output buffer
        if (cr.isUnderflow()) {
            int written = charOff - outStart;
            reset();
            return written;
        }
        assert false;
        return -1; // should be never reached
    }

    /**
     * Resets converter to its initial state.
     */
    public void reset() {
        super.reset();
        decoder.reset();
        src = null;
        dst = null;
    }

    /**
     * Returns the maximum number of characters needed to convert a byte. Useful for
     * calculating the maximum output buffer size needed for a particular input buffer.
     */
    public int getMaxCharsPerByte() {
        if (decoder != null)
            return (int)Math.ceil(decoder.maxCharsPerByte());   
        else // only provided for subclasses
            return 1;   
            // Until UTF-16, this will do for every encoding
    }

    /**
     * Sets converter into substitution mode. In substitution mode,
     * the converter will replace untranslatable characters in the source
     * encoding with the substitution character set by setSubstitionChars.
     * When not in substitution mode, the converter will throw an
     * UnknownCharacterException when it encounters untranslatable input.
     *
     * @param doSub if true, enable substitution mode.
     * @see #setSubstitutionChars
     */
    public void setSubstitutionMode(boolean doSub) {
        super.setSubstitutionMode(doSub);
        if (decoder != null)
            decoder.onUnmappableCharacter
                    (doSub ? CodingErrorAction.REPLACE : CodingErrorAction.REPORT);
    }

    /**
     * Sets the substitution characters to use when the converter is in
     * substitution mode. The given chars must not be longer than the value
     * returned by getMaxCharsPerByte for this converter.
     *
     * @param chars the substitution chars
     * @exception IllegalArgumentException if given byte array is longer than
     * the value returned by the method getMaxBytesPerChar.
     * @see #setSubstitutionMode
     * @see #getMaxBytesPerChar
     */
    public void setSubstitutionChars(char[] chars) throws IllegalArgumentException {
        if (decoder != null)
            decoder.replaceWith(new String(chars));
       
        else { // only provided for subclasses
            if(chars.length > getMaxCharsPerByte())
                throw new IllegalArgumentException();
            subChars = new char[chars.length];
            System.arraycopy(chars, 0, subChars, 0, chars.length);
        }
    }

    /**
     * returns a string representation of the character conversion
     */
    public String toString() {
        return "ByteToCharConverter: "+getCharacterEncoding();
    }
}
