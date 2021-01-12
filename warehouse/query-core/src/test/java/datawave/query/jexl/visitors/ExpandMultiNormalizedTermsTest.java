package datawave.query.jexl.visitors;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import datawave.data.type.IpAddressType;
import datawave.data.type.LcNoDiacriticsType;
import datawave.data.type.LcType;
import datawave.data.type.NoOpType;
import datawave.data.type.NumberType;
import datawave.data.type.Type;
import datawave.query.config.ShardQueryConfiguration;
import datawave.query.jexl.JexlASTHelper;
import datawave.query.util.MockMetadataHelper;
import org.apache.commons.jexl2.parser.ASTJexlScript;
import org.apache.commons.jexl2.parser.JexlNode;
import org.apache.commons.jexl2.parser.ParseException;
import org.apache.log4j.Logger;
import org.junit.Before;
import org.junit.Test;

import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ExpandMultiNormalizedTermsTest {
    
    private static final Logger log = Logger.getLogger(ExpandMultiNormalizedTermsTest.class);
    private ShardQueryConfiguration config;
    private MockMetadataHelper helper;
    
    @Before
    public void before() {
        // Create fresh query config
        config = new ShardQueryConfiguration();
        config.setBeginDate(new Date(0));
        config.setEndDate(new Date(System.currentTimeMillis()));
        
        // Create fresh metadata helper.
        helper = new MockMetadataHelper();
        
        // Each test configures fields as needed
    }
    
    @Test
    public void testSimpleCase() throws ParseException {
        Multimap<String,Type<?>> dataTypes = HashMultimap.create();
        dataTypes.putAll("FOO", Sets.newHashSet(new LcNoDiacriticsType()));
        
        helper.setIndexedFields(dataTypes.keySet());
        helper.setIndexOnlyFields(dataTypes.keySet());
        helper.addTermFrequencyFields(dataTypes.keySet());
        
        config.setQueryFieldsDatatypes(dataTypes);
        
        // No change expected
        String original = "FOO == 'bar'";
        expandTerms(original, original);
    }
    
    @Test
    public void testNumber() throws ParseException {
        Multimap<String,Type<?>> dataTypes = HashMultimap.create();
        dataTypes.putAll("MULTI", Sets.newHashSet(new LcNoDiacriticsType(), new NumberType()));
        dataTypes.put("NUM", new NumberType());
        
        helper.setIndexedFields(dataTypes.keySet());
        helper.setIndexOnlyFields(dataTypes.keySet());
        helper.addTermFrequencyFields(dataTypes.keySet());
        
        config.setQueryFieldsDatatypes(dataTypes);
        
        String original = "NUM == '1'";
        String expected = "NUM == '+aE1'";
        expandTerms(original, expected);
        
        original = "MULTI == '1'";
        expected = "(MULTI == '1' || MULTI == '+aE1')";
        expandTerms(original, expected);
    }
    
    @Test
    public void testNoOp() throws ParseException {
        Multimap<String,Type<?>> dataTypes = HashMultimap.create();
        dataTypes.put("NOOP", new NoOpType());
        
        helper.setIndexedFields(dataTypes.keySet());
        helper.setIndexOnlyFields(dataTypes.keySet());
        helper.addTermFrequencyFields(dataTypes.keySet());
        
        config.setQueryFieldsDatatypes(dataTypes);
        
        // No change expected
        String original = "NOOP == 'bar'";
        expandTerms(original, original);
    }
    
    @Test
    public void testMixedCase() throws ParseException {
        Multimap<String,Type<?>> dataTypes = HashMultimap.create();
        dataTypes.putAll("NAME", Sets.newHashSet(new LcNoDiacriticsType(), new NoOpType()));
        
        helper.setIndexedFields(dataTypes.keySet());
        helper.setIndexOnlyFields(dataTypes.keySet());
        helper.addTermFrequencyFields(dataTypes.keySet());
        
        config.setQueryFieldsDatatypes(dataTypes);
        
        String original = "NAME == 'alice'";
        String expected = "NAME == 'alice'";
        expandTerms(original, expected);
        
        original = "NAME == 'Bob'";
        expected = "(NAME == 'bob' || NAME == 'Bob')";
        expandTerms(original, expected);
        
        original = "NAME == 'CHARLIE'";
        expected = "(NAME == 'CHARLIE' || NAME == 'charlie')";
        expandTerms(original, expected);
    }
    
    @Test
    public void testMixedCaseWithNumber() throws ParseException {
        Multimap<String,Type<?>> dataTypes = HashMultimap.create();
        dataTypes.put("NUM", new NumberType());
        dataTypes.putAll("NAME", Sets.newHashSet(new LcNoDiacriticsType(), new NoOpType()));
        
        helper.setIndexedFields(dataTypes.keySet());
        helper.setIndexOnlyFields(dataTypes.keySet());
        helper.addTermFrequencyFields(dataTypes.keySet());
        
        config.setQueryFieldsDatatypes(dataTypes);
        
        String original = "(NAME == 'Alice' || NAME == 'BOB') && NUM < '1'";
        String expected = "((NAME == 'Alice' || NAME == 'alice') || (NAME == 'bob' || NAME == 'BOB')) && NUM < '+aE1'";
        expandTerms(original, expected);
    }
    
    @Test
    public void testIpAddressCase() throws ParseException {
        Multimap<String,Type<?>> dataTypes = HashMultimap.create();
        dataTypes.putAll("FOO", Sets.newHashSet(new LcNoDiacriticsType(), new LcType()));
        dataTypes.putAll("IP", Sets.newHashSet(new LcNoDiacriticsType(), new IpAddressType()));
        
        helper.setIndexedFields(dataTypes.keySet());
        helper.setIndexOnlyFields(dataTypes.keySet());
        helper.addTermFrequencyFields(dataTypes.keySet());
        
        config.setQueryFieldsDatatypes(dataTypes);
        
        String original = "FOO == 'bar' && IP == '127.0.0.1'";
        String expected = "FOO == 'bar' && (IP == '127.0.0.1' || IP == '127.000.000.001')";
        expandTerms(original, expected);
    }
    
    @Test
    public void testNullTermCase() throws ParseException {
        Multimap<String,Type<?>> dataTypes = HashMultimap.create();
        dataTypes.putAll("FOO", Sets.newHashSet(new LcNoDiacriticsType(), new LcType()));
        
        helper.setIndexedFields(dataTypes.keySet());
        helper.setIndexOnlyFields(dataTypes.keySet());
        helper.addTermFrequencyFields(dataTypes.keySet());
        
        config.setQueryFieldsDatatypes(dataTypes);
        
        // No change expected
        String original = "FOO == 'null'";
        expandTerms(original, original);
    }
    
    @Test
    public void testNormalizedBoundsCase() throws ParseException {
        Multimap<String,Type<?>> dataTypes = HashMultimap.create();
        dataTypes.putAll("FOO", Sets.newHashSet(new NumberType()));
        
        helper.setIndexedFields(dataTypes.keySet());
        helper.setIndexOnlyFields(dataTypes.keySet());
        helper.addTermFrequencyFields(dataTypes.keySet());
        
        config.setQueryFieldsDatatypes(dataTypes);
        
        String original = "FOO > '1' && FOO < '10'";
        String expected = "FOO > '+aE1' && FOO < '+bE1'";
        expandTerms(original, expected);
    }
    
    @Test
    public void testBoundedNormalizedBoundsCase() throws ParseException {
        Multimap<String,Type<?>> dataTypes = HashMultimap.create();
        dataTypes.putAll("FOO", Sets.newHashSet(new NumberType()));
        
        helper.setIndexedFields(dataTypes.keySet());
        helper.setIndexOnlyFields(dataTypes.keySet());
        helper.addTermFrequencyFields(dataTypes.keySet());
        
        config.setQueryFieldsDatatypes(dataTypes);
        
        String original = "((BoundedRange = true) && (FOO > '1' && FOO < '10'))";
        String expected = "((BoundedRange = true) && (FOO > '+aE1' && FOO < '+bE1'))";
        expandTerms(original, expected);
    }
    
    @Test
    public void testMultiNormalizedBounds() throws ParseException {
        Multimap<String,Type<?>> dataTypes = HashMultimap.create();
        dataTypes.putAll("FOO", Sets.newHashSet(new NumberType(), new LcNoDiacriticsType()));
        
        helper.setIndexedFields(dataTypes.keySet());
        helper.setIndexOnlyFields(dataTypes.keySet());
        helper.addTermFrequencyFields(dataTypes.keySet());
        
        config.setQueryFieldsDatatypes(dataTypes);
        
        String original = "FOO > 1 && FOO < 10";
        String expected = "(FOO > '1' || FOO > '+aE1') && (FOO < '+bE1' || FOO < '10')";
        expandTerms(original, expected);
    }
    
    @Test
    public void testBoundedMultiNormalizedBounds() throws ParseException {
        Multimap<String,Type<?>> dataTypes = HashMultimap.create();
        dataTypes.putAll("FOO", Sets.newHashSet(new NumberType(), new LcNoDiacriticsType()));
        
        helper.setIndexedFields(dataTypes.keySet());
        helper.setIndexOnlyFields(dataTypes.keySet());
        helper.addTermFrequencyFields(dataTypes.keySet());
        
        config.setQueryFieldsDatatypes(dataTypes);
        
        String original = "((BoundedRange = true) && (FOO > 1 && FOO < 10))";
        String expected = "((((BoundedRange = true) && (FOO > '+aE1' && FOO < '+bE1'))) || (((BoundedRange = true) && (FOO > '1' && FOO < '10'))))";
        expandTerms(original, expected);
    }
    
    @Test
    public void testUnNormalizedBoundsCase() throws ParseException {
        Multimap<String,Type<?>> dataTypes = HashMultimap.create();
        dataTypes.put("NEW", new LcNoDiacriticsType());
        
        helper.setIndexedFields(dataTypes.keySet());
        config.setQueryFieldsDatatypes(dataTypes);
        
        String original = "NEW == 'boo' && NEW > '1' && NEW < '10'";
        String expected = "NEW == 'boo' && NEW > '1' && NEW < '10'";
        expandTerms(original, expected);
    }
    
    @Test
    public void testBoundedUnNormalizedBoundsCase() throws ParseException {
        Multimap<String,Type<?>> dataTypes = HashMultimap.create();
        dataTypes.put("NEW", new LcNoDiacriticsType());
        
        helper.setIndexedFields(dataTypes.keySet());
        config.setQueryFieldsDatatypes(dataTypes);
        
        String original = "NEW == 'boo' && ((BoundedRange = true) && (NEW > '1' && NEW < '10'))";
        String expected = "NEW == 'boo' && ((BoundedRange = true) && (NEW > '1' && NEW < '10'))";
        expandTerms(original, expected);
    }
    
    @Test
    public void testNormalizedAndUnNormalizedBoundsCase() throws ParseException {
        Multimap<String,Type<?>> dataTypes = HashMultimap.create();
        dataTypes.put("NEW", new LcNoDiacriticsType());
        
        config.setQueryFieldsDatatypes(dataTypes);
        
        String original = "NEW > '0' && NEW < '9' && FOO > 1 && FOO < 10";
        String expected = "NEW > '0' && NEW < '9' && FOO > 1 && FOO < 10";
        expandTerms(original, expected);
    }
    
    @Test
    public void testBoundedNormalizedAndUnNormalizedBoundsCase() throws ParseException {
        Multimap<String,Type<?>> dataTypes = HashMultimap.create();
        dataTypes.put("NEW", new LcNoDiacriticsType());
        
        config.setQueryFieldsDatatypes(dataTypes);
        
        String original = "((BoundedRange = true) && (NEW > '0' && NEW < '9')) && ((BoundedRange = true) && (FOO > 1 && FOO < 10))";
        String expected = "((BoundedRange = true) && (NEW > '0' && NEW < '9')) && ((BoundedRange = true) && (FOO > 1 && FOO < 10))";
        expandTerms(original, expected);
    }
    
    @Test
    public void testMultiNormalizedFieldOpField() throws ParseException {
        Multimap<String,Type<?>> dataTypes = HashMultimap.create();
        dataTypes.putAll("FOO", Sets.newHashSet(new LcNoDiacriticsType(), new LcType()));
        
        helper.setIndexedFields(dataTypes.keySet());
        helper.setIndexOnlyFields(dataTypes.keySet());
        helper.addTermFrequencyFields(dataTypes.keySet());
        
        config.setQueryFieldsDatatypes(dataTypes);
        
        // No change expected
        String original = "FOO == FOO";
        expandTerms(original, original);
    }
    
    @Test
    public void testMultiNormalizedLiteralOpLiteral() throws ParseException {
        // No changed expected
        String original = "'bar' == 'bar'";
        expandTerms(original, original);
    }
    
    @Test
    public void testNormalizedFunctions() throws ParseException {
        Multimap<String,Type<?>> dataTypes = HashMultimap.create();
        dataTypes.putAll("FOO", Sets.newHashSet(new LcNoDiacriticsType(), new LcType()));
        
        helper.setIndexedFields(dataTypes.keySet());
        helper.setIndexOnlyFields(dataTypes.keySet());
        helper.addTermFrequencyFields(dataTypes.keySet());
        
        config.setQueryFieldsDatatypes(dataTypes);
        
        String original = "content:phrase(termOffsetMap, 'Foo', 'Bar')";
        String expected = "content:phrase(termOffsetMap, 'foo', 'bar')";
        expandTerms(original, expected);
    }
    
    @Test
    public void testNormalizedFunctionsWithField() throws ParseException {
        Multimap<String,Type<?>> dataTypes = HashMultimap.create();
        dataTypes.putAll("FOO", Sets.newHashSet(new LcNoDiacriticsType(), new LcType()));
        
        helper.setIndexedFields(dataTypes.keySet());
        helper.setIndexOnlyFields(dataTypes.keySet());
        helper.addTermFrequencyFields(dataTypes.keySet());
        
        config.setQueryFieldsDatatypes(dataTypes);
        String original = "content:phrase(FOO, termOffsetMap, 'Foo', 'Bar')";
        String expected = "content:phrase(FOO, termOffsetMap, 'foo', 'bar')";
        expandTerms(original, expected);
    }
    
    @Test
    public void testWeirdlyNormalizedFunction() throws ParseException {
        Multimap<String,Type<?>> dataTypes = HashMultimap.create();
        dataTypes.put("NUM", new NumberType());
        
        helper.setIndexedFields(dataTypes.keySet());
        helper.setIndexOnlyFields(dataTypes.keySet());
        helper.addTermFrequencyFields(dataTypes.keySet());
        
        config.setQueryFieldsDatatypes(dataTypes);
        String original = "content:phrase(termOffsetMap, '1', '2')";
        String expected = "content:phrase(termOffsetMap, '+aE1', '+aE2')";
        expandTerms(original, expected);
    }
    
    @Test
    public void testFilterFunctionNormalization() throws ParseException {
        Multimap<String,Type<?>> dataTypes = HashMultimap.create();
        dataTypes.put("IP", new IpAddressType());
        
        helper.setIndexedFields(dataTypes.keySet());
        helper.setIndexOnlyFields(dataTypes.keySet());
        helper.addTermFrequencyFields(dataTypes.keySet());
        
        config.setQueryFieldsDatatypes(dataTypes);
        
        String original = "filter:includeRegex(IP, '1\\.2\\.3\\..*')";
        String expected = "filter:includeRegex(IP, '1\\\\.2\\\\.3\\\\..*')";
        expandTerms(original, expected);
    }
    
    @Test
    public void testFilterFunctionNormalizationWithMultipleNormalizers() throws ParseException {
        Multimap<String,Type<?>> dataTypes = HashMultimap.create();
        dataTypes.putAll("IP", Sets.newHashSet(new IpAddressType(), new LcNoDiacriticsType()));
        
        helper.setIndexedFields(dataTypes.keySet());
        helper.setIndexOnlyFields(dataTypes.keySet());
        helper.addTermFrequencyFields(dataTypes.keySet());
        
        config.setQueryFieldsDatatypes(dataTypes);
        
        String original = "filter:includeRegex(IP, '1\\.2\\.3\\..*')";
        String expected = "filter:includeRegex(IP, '1\\\\.2\\\\.3\\\\..*')";
        expandTerms(original, expected);
    }
    
    @Test
    public void testFilterFunctionNormalizationWithNoPattern() throws ParseException {
        Multimap<String,Type<?>> dataTypes = HashMultimap.create();
        dataTypes.putAll("IP", Sets.newHashSet(new IpAddressType(), new LcNoDiacriticsType()));
        
        helper.setIndexedFields(dataTypes.keySet());
        helper.setIndexOnlyFields(dataTypes.keySet());
        helper.addTermFrequencyFields(dataTypes.keySet());
        
        config.setQueryFieldsDatatypes(dataTypes);
        
        String original = "filter:includeRegex(IP, '1\\.2\\.3\\.4')";
        String expected = "filter:includeRegex(IP, '1\\\\.2\\\\.3\\\\.4')";
        expandTerms(original, expected);
    }
    
    @Test
    public void testFilterFunctionNormalizationWithIndexedNumeric() throws ParseException {
        Multimap<String,Type<?>> dataTypes = HashMultimap.create();
        dataTypes.put("NUM", new NumberType());
        
        config.setQueryFieldsDatatypes(dataTypes);
        
        String original = "filter:includeRegex(NUM, '1')";
        expandTerms(original, original);
    }
    
    @Test
    public void testFilterFunctionNormalizationWithUnIndexedNumeric() throws ParseException {
        // Empty field-datatype multi-map
        config.setQueryFieldsDatatypes(HashMultimap.create());
        
        String original = "filter:includeRegex(NUM, '1')";
        expandTerms(original, original);
    }
    
    @Test
    public void testMultipleNormalizersForExceededRegex() throws ParseException {
        Multimap<String,Type<?>> dataTypes = HashMultimap.create();
        dataTypes.putAll("FOO", Sets.newHashSet(new LcNoDiacriticsType(), new NoOpType()));
        
        config.setQueryFieldsDatatypes(dataTypes);
        
        String original = "((ExceededValueThresholdMarkerJexlNode = true) && (FOO =~ 'bar.*'))";
        expandTerms(original, original);
    }
    
    @Test
    public void testMultipleNormalizersForBoundedRangeExceededThreshold() throws ParseException {
        Multimap<String,Type<?>> dataTypes = HashMultimap.create();
        dataTypes.putAll("FOO", Sets.newHashSet(new LcNoDiacriticsType(), new NoOpType()));
        
        config.setQueryFieldsDatatypes(dataTypes);
        
        String original = "((ExceededValueThresholdMarkerJexlNode = true) && (FOO > '1' && FOO < '10'))";
        expandTerms(original, original);
    }
    
    @Test
    public void testQueryThatShouldMakeNoRanges() throws Exception {
        String query = "FOO == 125 " + " AND (BAR).getValuesForGroups(grouping:getGroupsForMatchesInGroup(FOO,'125')) >= 35"
                        + " and (BAR).getValuesForGroups(grouping:getGroupsForMatchesInGroup(FOO,'125')) <= 36"
                        + " and (BAZ).getValuesForGroups(grouping:getGroupsForMatchesInGroup(FOO,'125')) >= 25"
                        + " and (BAZ).getValuesForGroups(grouping:getGroupsForMatchesInGroup(FOO,'125')) <= 26" + " and WHATEVER == 'la'";
        
        ASTJexlScript queryTree = JexlASTHelper.parseJexlQuery(query);
        ASTJexlScript smashed = TreeFlatteningRebuildingVisitor.flatten(queryTree);
        ASTJexlScript script = ExpandMultiNormalizedTerms.expandTerms(config, helper, smashed);
        
        String originalRoundTrip = JexlStringBuildingVisitor.buildQuery(queryTree);
        String smashedRoundTrip = JexlStringBuildingVisitor.buildQuery(smashed);
        String visitedRountTrip = JexlStringBuildingVisitor.buildQuery(script);
        
        assertEquals(originalRoundTrip, smashedRoundTrip);
        assertEquals(smashedRoundTrip, visitedRountTrip);
        assertLineage(script);
        
        // Verify the original input script was not modified, and still has a valid lineage.
        assertScriptEquality(queryTree, query);
        assertLineage(queryTree);
    }
    
    private void expandTerms(String original, String expected) throws ParseException {
        ASTJexlScript script = JexlASTHelper.parseJexlQuery(original);
        ASTJexlScript expanded = ExpandMultiNormalizedTerms.expandTerms(config, helper, script);
        
        // Verify the resulting script is as expected, with a valid lineage.
        assertScriptEquality(expanded, expected);
        assertLineage(expanded);
        
        // Verify the original input script was not modified, and still has a valid lineage.
        assertScriptEquality(script, original);
        assertLineage(script);
    }
    
    private void assertScriptEquality(JexlNode actual, String expected) throws ParseException {
        ASTJexlScript actualScript = JexlASTHelper.parseJexlQuery(JexlStringBuildingVisitor.buildQuery(actual));
        ASTJexlScript expectedScript = JexlASTHelper.parseJexlQuery(expected);
        TreeEqualityVisitor.Reason reason = new TreeEqualityVisitor.Reason();
        boolean equal = TreeEqualityVisitor.isEqual(expectedScript, actualScript, reason);
        if (!equal) {
            log.error("Expected " + PrintingVisitor.formattedQueryString(expectedScript));
            log.error("Actual " + PrintingVisitor.formattedQueryString(actualScript));
        }
        assertTrue(reason.reason, equal);
    }
    
    private void assertLineage(JexlNode node) {
        assertTrue(JexlASTHelper.validateLineage(node, true));
    }
}
