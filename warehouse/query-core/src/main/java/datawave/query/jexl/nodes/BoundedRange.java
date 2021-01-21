package datawave.query.jexl.nodes;

import org.apache.commons.jexl2.parser.JexlNode;
import org.apache.commons.jexl2.parser.JexlNodes;

/**
 * This is a node that can wrap an expression to mark that the source expression is a bounded range. A bounded range is a range that can be applied to only one
 * value out of a multi-valued field.
 */
public class BoundedRange extends QueryPropertyMarker {
    
    public BoundedRange(int id) {
        super(id);
    }
    
    public BoundedRange() {
        super();
    }
    
    /**
     * This will create a structure as follows around the specified node: Reference (this node) Reference Expression AND Reference Reference Expression
     * Assignment Reference Identifier:BoundedRange True node (the one specified
     *
     * Hence the resulting expression will be ((BoundedRange = True) AND (range)))
     *
     * @param node
     */
    public BoundedRange(JexlNode node) {
        super(node);
    }
    
    /**
     * @param node
     * @return
     */
    public static BoundedRange create(JexlNode node) {
        
        JexlNode parent = node.jjtGetParent();
        
        BoundedRange expr = new BoundedRange(node);
        
        if (parent != null) {
            JexlNodes.replaceChild(parent, node, expr);
        }
        
        return expr;
    }
}
