package datawave.query.jexl.visitors;

import datawave.query.jexl.JexlASTHelper;
import datawave.query.jexl.nodes.QueryPropertyMarker;
import org.apache.commons.jexl2.parser.ASTDelayedPredicate;
import org.apache.commons.jexl2.parser.JexlNode;
import org.apache.commons.jexl2.parser.ParseException;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class QueryPropertyMarkerVisitorTest {
    
    private QueryPropertyMarker.Instance instance;
    
    @Before
    public void setUp() throws Exception {
        instance = null;
    }
    
    @Test
    public void testNull() {
        instance = QueryPropertyMarkerVisitor.getInstance(null);
        assertNoType();
        assertNullSource();
    }
    
    @Test
    public void testAndNodeWithoutMarker() throws ParseException {
        givenNode("FOO == 1 && BAR == 2");
        assertNoType();
        assertNullSource();
    }
    
    @Test
    public void testAndWithNestedMarker() throws ParseException {
        givenNode("ABC == 'aaa' && ((ASTEvaluationOnly = true) && (FOO == 1 && BAR == 2))");
        assertNoType();
        assertNullSource();
    }
    
    @Test
    public void testAndWithMarkersOnBothSides() throws ParseException {
        givenNode("((BoundedRange = true) && (FOO > 1 && FOO < 2)) && ((ASTEvaluationOnly = true) && (FOO == 1 && BAR == 2))");
        assertNoType();
        assertNullSource();
    }
    
    @Test
    public void testUnwrappedMarkerAndUnwrappedSources() throws ParseException {
        givenNode("(ASTDelayedPredicate = true) && FOO == 1 && BAR == 2");
        assertType(ASTDelayedPredicate.class);
        assertSource("(BAR == 2 && FOO == 1)");
    }
    
    @Test
    public void testUnwrappedMarkerAndWrappedSources() throws ParseException {
        givenNode("(ASTDelayedPredicate = true) && (FOO == 1 && BAR == 2)");
        assertType(ASTDelayedPredicate.class);
        assertSource("FOO == 1 && BAR == 2");
    }
    
    @Test
    public void testWrappedMarkerAndUnwrappedSources() throws ParseException {
        givenNode("((ASTDelayedPredicate = true) && FOO == 1 && BAR == 2)");
        assertType(ASTDelayedPredicate.class);
        assertSource("(BAR == 2 && FOO == 1)");
    }
    
    @Test
    public void testWrappedMarkerAndWrappedSources() throws ParseException {
        givenNode("((ASTDelayedPredicate = true) && (FOO == 1 && BAR == 2))");
        assertType(ASTDelayedPredicate.class);
        assertSource("FOO == 1 && BAR == 2");
    }
    
    private void givenNode(String query) throws ParseException {
        JexlNode node = JexlASTHelper.parseJexlQuery(query);
        instance = QueryPropertyMarkerVisitor.getInstance(node);
    }
    
    private void assertNoType() {
        assertFalse(instance.isAnyType());
    }
    
    private void assertType(Class<? extends QueryPropertyMarker> type) {
        assertTrue(instance.isType(type));
    }
    
    private void assertNullSource() {
        assertNull(instance.getSource());
    }
    
    private void assertSource(String source) {
        assertEquals(source, JexlStringBuildingVisitor.buildQuery(instance.getSource()));
    }
}
