package app.nzyme.core.rest;

import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.testng.Assert.assertEquals;

public class NzymeExceptionMapperTest {

    private static int status(Throwable t) {
        return new NzymeExceptionMapper().toResponse(t).getStatus();
    }

    @Test
    public void testValueThatPostgresCannotReadIsABadRequest() {
        assertEquals(status(new RuntimeException(new SQLException("invalid regular expression", "2201B"))), 400);
        assertEquals(status(new RuntimeException(new SQLException("invalid input syntax for type inet", "22P02"))), 400);
    }

    @Test
    public void testOtherDatabaseErrorsAreServerErrors() {
        assertEquals(status(new RuntimeException(new SQLException("syntax error", "42601"))), 500);
        assertEquals(status(new RuntimeException(new SQLException("no state"))), 500);
        assertEquals(status(new RuntimeException("something else")), 500);
    }

    @Test
    public void testInvalidArgumentIsABadRequest() {
        assertEquals(status(new IllegalArgumentException("Invalid filter.")), 400);
    }

}
