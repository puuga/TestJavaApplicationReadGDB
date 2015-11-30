/*
 * @(#)Converters.java        1.20        06/04/07
 *
 * Copyright 2006 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 * Contributors: Ulf Zibis, Germany, Ulf.Zibis @ CoSoCo.de
 */

package sun.io;

import java.io.UnsupportedEncodingException;
import java.lang.ref.SoftReference;
import java.nio.charset.*;
import java.util.Properties;

/**
 * Package-private utility class that caches the default converter classes and
 * provides other logic common to both the ByteToCharConverter and
 * CharToByteConverter classes.
 *
 * @author   Mark Reinhold
 * @version  1.7, 00/07/07
 * @since    1.2
 *
 * @deprecated Replaced by {@link java.nio.charset}.  THIS API WILL BE
 * REMOVED IN J2SE 1.6.
 */
@Deprecated
class Converters {

    private Converters() {} // To prevent instantiation 

    // Lock for all static fields in this class
    private static final Object lock = Converters.class;

    // Cached values of system properties
    private static String converterPackageName = null;  // file.encoding.pkg
    private static String defaultEncoding = null;       // file.encoding

    // Property name for the default charset, also used as cache key
    private static final String DEFAULT_ENC = "file.encoding";

    // Converter type constants and names
    public static final int BYTE_TO_CHAR = 0;
    public static final int CHAR_TO_BYTE = 1;
    private static final String[] converterPrefix = { "ByteToChar", "CharToByte" };


    private static synchronized String getDefaultEncodingName() {
        if (defaultEncoding == null)
            defaultEncoding = java.security.AccessController.doPrivileged(
                    new sun.security.action.GetPropertyAction(DEFAULT_ENC));
        return defaultEncoding;
    }

    public static void resetDefaultEncodingName() {
        // This method should only be called during VM initialization.
        if (sun.misc.VM.isBooted())
            return;
        synchronized (lock) {
            defaultEncoding = "ISO-8859-1";
            Properties p = System.getProperties();
            p.setProperty("file.encoding", defaultEncoding);
            System.setProperties(p);
        }
    }

    /**
     * Create a converter object that implements the given type of converter
     * for the default encoding. If the default encoding cannot be determined
     * or doesn't work, try the fallback default encoding, which is just ISO 8859-1.
     */
    static Converter newDefaultConverter(int type) {
        Object factory;
        synchronized (lock) {
            // First check the factory cache
            if ((factory = cache(type, DEFAULT_ENC)) == null) {
                try {
                    // Determine the encoding name; try to find its factory
                    factory = getConverterFactory(type, getDefaultEncodingName());
                    cache(type, DEFAULT_ENC, factory);
                } catch (UnsupportedEncodingException x) {
                    // Can't find the default's factory, so fall back to ISO 8859-1
                    try {
                        factory = getConverterFactory(type, "ISO8859_1");
                    } catch (UnsupportedEncodingException y) {
                        throw new InternalError
                                ("Cannot find default "+converterPrefix[type]+" converter class");
                    }
                }
            }
        }
        try {
            return newConverter(type, "", factory);
        } catch (UnsupportedEncodingException x) {
            throw new InternalError
                    ("Cannot instantiate default converter from "+factory);
        }
    }

    /**
     * Create a converter object that implements the given type of converter
     * for the given encoding, or throw an UnsupportedEncodingException if no
     * appropriate converter class can be found and instantiated
     */
    static Converter newConverter(int type, String enc)
            throws UnsupportedEncodingException {
        Object factory;
        synchronized (lock) {
            // First check the factory cache
            if ((factory = cache(type, enc)) == null) {
                factory = getConverterFactory(type, enc);
                if (!(factory == CharToByteConverter.class &&
                        CharacterEncoding.aliasName(enc) == "UTF8"))
                    cache(type, enc, factory);
            }
        }
        return newConverter(type, enc, factory);
    }

    /**
     * Instantiate new converter from the given factory, or throw an
     * UnsupportedEncodingException if it cannot be instantiated
     */
    private static Converter newConverter(int type, String enc, Object factory)
            throws UnsupportedEncodingException {
        if (factory instanceof Charset) {
            Charset cs = (Charset)factory;
            if (type == BYTE_TO_CHAR)
                return new ByteToCharConverter(cs, enc);
            if (type == CHAR_TO_BYTE)
                if (!cs.canEncode())
                    throw new UnsupportedEncodingException(enc);
                else
                    return new CharToByteConverter(cs, enc);
            throw new InternalError("Illegal converter type: "+type);
        }
        else if (factory instanceof Class)
            try {
                return ((Class<Converter>)factory).newInstance();
            } catch(InstantiationException e) {
                throw new UnsupportedEncodingException(enc);
            } catch(IllegalAccessException e) {
                throw new UnsupportedEncodingException(enc);
            }
        else
            throw new InternalError("Illegal factory object: "+factory);
    }

    /**
     * Get the class that implements the given type of converter for the named encoding,
     * or throw an UnsupportedEncodingException if no such class can be found
     */
    private static Object getConverterFactory(int type, String encoding)
            throws UnsupportedEncodingException {

        // For package name "sun.io" retrieve a charset object from sun.nio.cs package
        if (getConverterPackageName().equals("sun.io")) try {
            // Workaround for aliases missing in sun.nio.cs package
            encoding = translateAliasesMissingInSunNio(encoding);
            try {
                return Charset.forName(encoding);
            } catch (UnsupportedCharsetException uce) {
                // Because some old converters, which lack entry in class CharacterEncoding,
                // were public accessible, instantiate anonymous charset classes
                return getHiddenCharset(encoding);
            }
        } catch (IllegalArgumentException e) {
            throw new UnsupportedEncodingException(encoding);
        }

        // For other package names than "sun.io" retrieve a converter class
        // from it's package path by help of aliases from CharacterEncoding class

        // "ISO8859_1" is the canonical name for the ISO-Latin-1 encoding.
        // Native code in the JDK commonly uses the alias "8859_1" instead of
        // "ISO8859_1".  We hardwire this alias here in order to avoid loading
        // the full alias table just for this case.
        // On Solaris with nl_langinfo() called in GetJavaProperties():
        //
        //   locale undefined -> NULL -> hardcoded default
        //   "C" locale       -> ""   -> hardcoded default     (on 2.6)
        //   "C" locale       -> "646"                         (on 2.7)
        //   "en_US" locale   -> "ISO8859-1"
        //   "en_GB" locale   -> "ISO8859-1"                   (on 2.7)
        //   "en_UK" locale   -> "ISO8859-1"                   (on 2.6)

        String enc = "ISO8859_1".equals(encoding)                     ? encoding :
            "8859_1".equals(encoding) || "ISO8859-1".equals(encoding) ? "ISO8859_1" :
            "646".equals(encoding)                                    ? "ASCII" :
            CharacterEncoding.aliasName(encoding);
        if (enc == null)  enc = encoding;

        try {
            return Class.forName
                    (getConverterPackageName()+"."+converterPrefix[type]+enc);
        } catch(ClassNotFoundException e) {
            throw new UnsupportedEncodingException(enc);
        }
    }

    /** Get the name of the converter package */
    private static String getConverterPackageName() {
        String cp = converterPackageName;
        if (cp != null) return cp;
        cp = java.security.AccessController.doPrivileged(
                new sun.security.action.GetPropertyAction("file.encoding.pkg"));
        if (cp != null)
            // Property is set, so take it as the true converter package
            converterPackageName = cp;
        else
            // Fall back to sun.io
            cp = "sun.io";
        return cp;
    }

    // Workaround for aliases missing in sun.nio.cs package, see: bug_id=6745216
    private static String translateAliasesMissingInSunNio(final String encoding) {
        if ("unicode-1-1".equals(encoding))  return "UTF-16BE";
        if ("834".equals(encoding))  return "x-IBM834";
        if ("jis auto detect".equals(encoding))  return "x-JISAutoDetect";
        if ("\u30b7\u30d5\u30c8\u7b26\u53f7\u5316\u8868\u73fe".equals(encoding))
            return "Shift_JIS";
        return encoding;
    }

    // Because some old converters, which lack entry in class CharacterEncoding, were public accessible,
    // instantiate anonymous classes from abstract charset classes, hidden in sun.nio.cs package
    private static Charset getHiddenCharset(final String encoding)
            throws UnsupportedEncodingException {
        if ("HKSCS".equalsIgnoreCase(encoding))
            return new sun.nio.cs.ext.HKSCS() {
                public boolean contains(Charset cs) { return cs.getClass() == this.getClass(); }
            };
        else if ("HKSCS_2001".equalsIgnoreCase(encoding))
            return new sun.nio.cs.ext.HKSCS_2001() {
                public boolean contains(Charset cs) { return cs.getClass() == this.getClass(); }
            };
        else if ("JIS0208_Solaris".equalsIgnoreCase(encoding))
            return new Charset("x-JIS-X-0208-Solaris", null) {
                public boolean contains(Charset cs) { return cs.getClass() == this.getClass(); }
                public CharsetDecoder newDecoder() {
                    return new sun.nio.cs.ext.JIS_X_0208_Solaris_Decoder(this);
                }
                public CharsetEncoder newEncoder() {
                    return new sun.nio.cs.ext.JIS_X_0208_Solaris_Encoder(this);
                }
            };
        else if ("JIS0212_Solaris".equalsIgnoreCase(encoding))
            return new Charset("x-JIS-X-0212-Solaris", null) {
                public boolean contains(Charset cs) { return cs.getClass() == this.getClass(); }
                public CharsetDecoder newDecoder() {
                    return new sun.nio.cs.ext.JIS_X_0212_Solaris_Decoder(this);
                }
                public CharsetEncoder newEncoder() {
                    return new sun.nio.cs.ext.JIS_X_0212_Solaris_Encoder(this);
                }
            };
        else
            throw new UnsupportedEncodingException(encoding);
    }


    // -- Converter factory cache : --

    /**
     * Caches converter factories, CACHE_SIZE per converter type. Each cache
     * entry is a soft reference to a two-object array; the first element of the
     * array is an object (typically a string) representing the encoding name that
     * was used to request the converter, the second is the converter factory,
     * which can be a class of type Class or java.nio.charset.Charset, e.g.,
     *
     *     classCache[CHAR_TO_BYTE][i].get()[0]
     *
     * will be the string encoding name used to request it, assuming that cache
     * entry i is valid and
     *
     *     classCache[CHAR_TO_BYTE][i].get()[1]
     *
     * will be a CharToByteConverter's Class or Charset object.
     *
     * Ordinarily we'd do this with a private static utility class, but since
     * this code can be involved in the startup sequence it's important to keep
     * the footprint down.
     */

    private static final int CACHE_SIZE = 3;

    private static SoftReference<Object[]>[][] classCache = new SoftReference[][]
            { new SoftReference[CACHE_SIZE], new SoftReference[CACHE_SIZE] };

    private static void moveToFront(Object[] factoryEntry, int i) {
        Object temp = factoryEntry[i];
        for (int j=i; j>0; j--)
            factoryEntry[j] = factoryEntry[j-1];
        factoryEntry[0] = temp;
    }

    private static Object cache(int type, String encoding) {
        SoftReference<Object[]>[] srs = classCache[type];
        for (int i=0; i<CACHE_SIZE; i++) {
            SoftReference<Object[]> sr = srs[i];
            if (sr == null)
                continue;
            Object[] factoryEntry = sr.get();
            if (factoryEntry == null) {
                srs[i] = null;
                continue;
            }
            if (factoryEntry[0].equals(encoding)) {
                moveToFront(srs, i);
                return factoryEntry[1];
            }
        }
        return null;
    }

    private static Object cache(int type, String encoding, Object factory) {
        SoftReference<Object[]>[] srs = classCache[type];
        srs[CACHE_SIZE-1] = new SoftReference(new Object[] { encoding, factory });
        moveToFront(srs, CACHE_SIZE-1);
        return factory;
    }
// Nowhere used ?
//    /**
//     * Used to avoid doing expensive charset lookups for charsets that are not
//     * yet directly supported by NIO.
//     */
//    public static boolean isCached(int type, String encoding) {
//        synchronized (lock) {
//            SoftReference<Object[]>[] srs = classCache[type];
//            for (int i = 0; i < CACHE_SIZE; i++) {
//                SoftReference<Object[]> sr = srs[i];
//                if (sr == null)
//                    continue;
//                Object[] factoryEntry = sr.get();
//                if (factoryEntry == null) {
//                    srs[i] = null;
//                    continue;
//                }
//                if (factoryEntry[0].equals(encoding))
//                    return true;
//            }
//            return false;
//        }
//    }
}
