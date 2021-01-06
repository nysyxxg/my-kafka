/*
 The MIT License

 Copyright (c) 2004-2016 Paul R. Holser, Jr.

 Permission is hereby granted, free of charge, to any person obtaining
 a copy of this software and associated documentation files (the
 "Software"), to deal in the Software without restriction, including
 without limitation the rights to use, copy, modify, merge, publish,
 distribute, sublicense, and/or sell copies of the Software, and to
 permit persons to whom the Software is furnished to do so, subject to
 the following conditions:

 The above copyright notice and this permission notice shall be
 included in all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/

package joptsimple.util;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author <a href="mailto:pholser@alumni.rice.edu">Paul Holser</a>
 */
public class KeyValuePairTest {
    @Test( expected = NullPointerException.class )
    public void nullArg() {
        KeyValuePair.valueOf( null );
    }

    @Test
    public void empty() {
        KeyValuePair pair = KeyValuePair.valueOf( "" );

        assertEquals( "", pair.key );
        assertNull( pair.value );
    }

    @Test
    public void noEqualsSign() {
        KeyValuePair pair = KeyValuePair.valueOf( "aString" );

        assertEquals( "aString", pair.key );
        assertNull( pair.value );
    }

    @Test
    public void signAtEnd() {
        KeyValuePair pair = KeyValuePair.valueOf( "aKey=" );

        assertEquals( "aKey", pair.key );
        assertEquals( "", pair.value );
    }

    @Test
    public void signAtStart() {
        KeyValuePair pair = KeyValuePair.valueOf( "=aValue" );

        assertEquals( "", pair.key );
        assertEquals( "aValue", pair.value );
    }

    @Test
    public void typical() {
        KeyValuePair pair = KeyValuePair.valueOf( "aKey=aValue" );

        assertEquals( "aKey", pair.key );
        assertEquals( "aValue", pair.value );
    }

    @Test
    public void multipleEqualsSignsDoNotMatter() {
        KeyValuePair pair = KeyValuePair.valueOf( "aKey=1=2=3=4" );

        assertEquals( "aKey", pair.key );
        assertEquals( "1=2=3=4", pair.value );
    }
}
