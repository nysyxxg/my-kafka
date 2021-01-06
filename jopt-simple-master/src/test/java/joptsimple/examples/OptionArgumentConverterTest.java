package joptsimple.examples;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.joda.time.LocalDate;
import org.junit.Test;

import static joptsimple.util.DateConverter.*;
import static joptsimple.util.RegexMatcher.*;
import static org.junit.Assert.*;

public class OptionArgumentConverterTest {
    @Test
    public void usesConvertersOnOptionArgumentsWhenTold() {
        OptionParser parser = new OptionParser();
        parser.accepts( "birthdate" ).withRequiredArg().withValuesConvertedBy( datePattern( "MM/dd/yy" ) );
        parser.accepts( "ssn" ).withRequiredArg().withValuesConvertedBy( regex( "\\d{3}-\\d{2}-\\d{4}" ));

        OptionSet options = parser.parse( "--birthdate", "02/24/05", "--ssn", "123-45-6789" );

        assertEquals( new LocalDate( 2005, 2, 24 ).toDate(), options.valueOf( "birthdate" ) );
        assertEquals( "123-45-6789", options.valueOf( "ssn" ) );
    }
}
