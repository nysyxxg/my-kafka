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

package joptsimple;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.junit.Test;

import static java.util.Arrays.*;
import static java.util.Collections.*;
import static joptsimple.util.DateConverter.*;
import static org.junit.Assert.*;

public class NonOptionArgumentSpecTest extends AbstractOptionParserFixture {
    @Test
    public void allowsTypingOfNonOptionArguments() {
        OptionSpec<File> nonOptions = parser.nonOptions().ofType( File.class );

        OptionSet options = parser.parse( "/opt", "/var" );

        assertEquals( asList( new File( "/opt" ), new File( "/var" ) ), nonOptions.values( options ) );
    }

    @Test
    public void allowsDescriptionOfNonOptionArguments() {
        OptionSpec<String> nonOptions = parser.nonOptions( "directories" );

        OptionSet options = parser.parse( "/opt", "/var" );

        assertEquals( asList( "/opt", "/var" ), nonOptions.values( options ) );
    }

    @Test
    public void allowsTypeAndDescriptionOfNonOptionArguments() {
        OptionSpec<File> nonOptions = parser.nonOptions( "directories" ).ofType( File.class );

        OptionSet options = parser.parse( "/opt", "/var" );

        assertEquals( asList( new File( "/opt" ), new File( "/var" ) ), nonOptions.values( options ) );
    }

    @Test
    public void allowsArgumentDescriptionForNonOptionArguments() {
        OptionSpec<String> nonOptions = parser.nonOptions().describedAs( "dirs" );

        OptionSet options = parser.parse( "/opt", "/var" );

        assertEquals( asList( "/opt", "/var" ), nonOptions.values( options ) );
    }

    @Test
    public void doesNotAcceptArguments() {
        OptionDescriptor nonOptions = parser.nonOptions().describedAs( "dirs" );

        assertFalse( nonOptions.acceptsArguments() );
    }

    @Test
    public void convertingUsingConverter() throws Exception {
        OptionSpec<Date> date = parser.nonOptions().withValuesConvertedBy( datePattern( "MM/dd/yyyy" ) );

        OptionSet options = parser.parse( "01/24/2013" );

        assertEquals(
            singletonList( new SimpleDateFormat( "MM/dd/yyyy" ).parse( "01/24/2013" ) ),
            date.values( options ) );
    }

    @Test( expected = NullPointerException.class )
    public void convertingUsingNullConverter() {
        parser.nonOptions().withValuesConvertedBy( null );
    }

    @Test
    public void noSpecsCorrespondingToNonOptions() {
        OptionParser parser = new OptionParser();
        parser.nonOptions();

        OptionSet options = parser.parse( "one", "two" );

        assertEquals( emptyList(), options.specs() );
    }

    @Test
    public void specsCorrespondingToNonOptions() {
        OptionParser parser = new OptionParser();
        parser.nonOptions();

        OptionSet options = parser.parse( "one", "two" );

        assertEquals(
            asList(
                new NonOptionArgumentSpec( NonOptionArgumentSpec.NAME ),
                new NonOptionArgumentSpec( NonOptionArgumentSpec.NAME )
            ),
            options.specsWithNonOptions() );
    }
}
