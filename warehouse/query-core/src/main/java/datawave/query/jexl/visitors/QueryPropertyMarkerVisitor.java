package datawave.query.jexl.visitors;

import com.google.common.collect.ImmutableMap;
import datawave.query.jexl.JexlASTHelper;
import datawave.query.jexl.JexlNodeFactory;
import datawave.query.jexl.nodes.BoundedRange;
import datawave.query.jexl.nodes.ExceededOrThresholdMarkerJexlNode;
import datawave.query.jexl.nodes.ExceededTermThresholdMarkerJexlNode;
import datawave.query.jexl.nodes.ExceededValueThresholdMarkerJexlNode;
import datawave.query.jexl.nodes.IndexHoleMarkerJexlNode;
import datawave.query.jexl.nodes.QueryPropertyMarker;
import datawave.query.jexl.nodes.QueryPropertyMarker.Instance;
import org.apache.commons.jexl2.parser.ASTAndNode;
import org.apache.commons.jexl2.parser.ASTAssignment;
import org.apache.commons.jexl2.parser.ASTDelayedPredicate;
import org.apache.commons.jexl2.parser.ASTEvaluationOnly;
import org.apache.commons.jexl2.parser.ASTOrNode;
import org.apache.commons.jexl2.parser.JexlNode;

import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.apache.commons.jexl2.parser.JexlNodes.children;

/**
 * This class is used to determine whether the specified node is an instance of a query marker. The reason for this functionality is that if the query is
 * serialized and deserialized, then only the underlying assignment will persist. This class will identify the Reference, ReferenceExpression, or And nodes,
 * created by the original QueryPropertyMarker instance, as the marked node. Children of the marker node will not be identified as marked.
 */
public class QueryPropertyMarkerVisitor extends BaseVisitor {
    
    // @formatter:off
    private static final Map<String,Class<? extends QueryPropertyMarker>> IDENTIFIERS = new ImmutableMap.Builder<String,Class<? extends QueryPropertyMarker>>()
                    .put(IndexHoleMarkerJexlNode.class.getSimpleName(), IndexHoleMarkerJexlNode.class)
                    .put(ASTDelayedPredicate.class.getSimpleName(), ASTDelayedPredicate.class)
                    .put(ASTEvaluationOnly.class.getSimpleName(), ASTEvaluationOnly.class)
                    .put(ExceededOrThresholdMarkerJexlNode.class.getSimpleName(), ExceededOrThresholdMarkerJexlNode.class)
                    .put(ExceededValueThresholdMarkerJexlNode.class.getSimpleName(), ExceededValueThresholdMarkerJexlNode.class)
                    .put(ExceededTermThresholdMarkerJexlNode.class.getSimpleName(), ExceededTermThresholdMarkerJexlNode.class)
                    .put(BoundedRange.class.getSimpleName(), BoundedRange.class).build();
    // @formatter:on
    
    /**
     * Examine the specified node to see if it represents a query property marker, and return an {@link Instance} with the marker's type and source node. If the
     * specified node is not a marker type, an empty {@link Instance} will be returned.
     * 
     * @param node
     *            the node
     * @return an {@link Instance}
     */
    public static Instance getInstance(JexlNode node) {
        return getInstance(node, false);
    }
    
    /**
     * This method differs from {@link #getInstance(JexlNode)} only in that if the specified node is a marker type, a safe copy of the node's source will be
     * made before being returned in the {@link Instance}. This is necessary for the {@link TreeFlatteningRebuildingVisitor}.
     * 
     * @param node
     *            the node
     * @return an {@link Instance}
     */
    public static Instance getCopiedInstance(JexlNode node) {
        return getInstance(node, true);
    }
    
    /**
     * Examine the specified node to see if it represents a query property marker, and return an {@link Instance} with the marker's type and source node. If the
     * specified node is not a marker type, an empty {@link Instance} will be returned. If specified, safe copies will be made of the node's source.
     * 
     * @param node
     *            the possible marker
     * @param copySources
     *            if true, make safe copies of the node's source
     * @return an {@link Instance}
     */
    private static Instance getInstance(JexlNode node, boolean copySources) {
        if (node != null) {
            QueryPropertyMarkerVisitor visitor = new QueryPropertyMarkerVisitor();
            node.jjtAccept(visitor, null);
            if (visitor.markerFound()) {
                JexlNode source = consolidate(visitor.sourceNodes, copySources);
                return Instance.of(visitor.marker, source);
            }
        }
        return Instance.of();
    }
    
    private static JexlNode consolidate(List<JexlNode> nodes, boolean copy) {
        // @formatter:off
        nodes = nodes.stream()
                        .map((node) -> copy ? RebuildingVisitor.copy(node) : node) // Make a safe copy if necessary.
                        .map(JexlASTHelper::dereference) // Unwrap each node.
                        .collect(Collectors.toList());
        // @formatter:on
        
        if (nodes.isEmpty()) {
            return null;
        } else if (nodes.size() == 1) {
            return nodes.get(0);
        } else {
            return JexlNodeFactory.createUnwrappedAndNode(nodes);
        }
    }
    
    private Class<? extends QueryPropertyMarker> marker;
    private List<JexlNode> sourceNodes;
    private boolean visitedFirstAndNode;
    
    private QueryPropertyMarkerVisitor() {}
    
    @Override
    public Object visit(ASTAssignment node, Object data) {
        // Do not search for a marker in any assignment nodes that are not within the first AND node.
        if (visitedFirstAndNode) {
            String identifier = JexlASTHelper.getIdentifier(node);
            if (identifier != null) {
                marker = IDENTIFIERS.get(identifier);
            }
        }
        return null;
    }
    
    @Override
    public Object visit(ASTOrNode node, Object data) {
        // Do not descend into children of OR nodes.
        return null;
    }
    
    @Override
    public Object visit(ASTAndNode node, Object data) {
        // Only the first AND node is a potential marker candidate.
        if (!visitedFirstAndNode) {
            visitedFirstAndNode = true;
            
            // Get the flattened children.
            List<JexlNode> children = getFlattenedChildren(node);
            
            // Examine each child for a marker identifier.
            List<JexlNode> siblings = new ArrayList<>();
            for (JexlNode child : children) {
                // Look for a marker only if one hasn't been found yet.
                if (!markerFound()) {
                    child.jjtAccept(this, null);
                    // / If a marker was found, continue and do not add this node as a sibling.
                    if (markerFound()) {
                        continue;
                    }
                }
                
                siblings.add(child);
            }
            
            // If a marker was found, assign the source nodes.
            if (markerFound())
                sourceNodes = siblings;
        }
        return null;
    }
    
    /**
     * Return the flattened children of the specified node. Nested {@link ASTAndNode} nodes are ignored, and their children are considered direct children for
     * the parent {@link ASTAndNode} node.
     *
     * @param node
     *            the node to retrieve the flattened children of
     * @return the flattened children
     */
    private List<JexlNode> getFlattenedChildren(JexlNode node) {
        List<JexlNode> children = new ArrayList<>();
        Deque<JexlNode> stack = new LinkedList<>();
        stack.push(node);
        while (!stack.isEmpty()) {
            JexlNode descendent = stack.pop();
            if (descendent instanceof ASTAndNode) {
                for (JexlNode sibling : children(descendent)) {
                    stack.push(sibling);
                }
            } else {
                children.add(descendent);
            }
        }
        return children;
    }
    
    /**
     * Return whether or not a {@link QueryPropertyMarker} has been found yet.
     *
     * @return true if a marker has been found, or false otherwise
     */
    private boolean markerFound() {
        return marker != null;
    }
}
