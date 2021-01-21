package datawave.query.jexl.nodes;

import org.apache.commons.jexl2.parser.JexlNode;
import org.apache.commons.jexl2.parser.JexlNodes;

/**
 * This is a node that can wrap an expression (normally an EQ or RE node) to denote that it references a known hole in the global index.
 */
public class IndexHoleMarkerJexlNode extends QueryPropertyMarker {
    
    public IndexHoleMarkerJexlNode(int id) {
        super(id);
    }
    
    public IndexHoleMarkerJexlNode() {
        super();
    }
    
    /**
     * This will create a structure as follows around the specified node: Reference (this node) Reference Expression AND Reference Reference Expression
     * Assignment Reference Identifier:IndexHoleMarkerJexlNode True node (the one specified
     *
     * Hence the resulting expression will be ((IndexHoleMarkerJexlNode = True) AND {specified node})
     *
     * @param node
     */
    public IndexHoleMarkerJexlNode(JexlNode node) {
        super(node);
    }
    
    /**
     * @param node
     * @return
     */
    public static IndexHoleMarkerJexlNode create(JexlNode node) {
        
        JexlNode parent = node.jjtGetParent();
        
        IndexHoleMarkerJexlNode expr = new IndexHoleMarkerJexlNode(node);
        
        if (parent != null) {
            JexlNodes.replaceChild(parent, node, expr);
        }
        
        return expr;
    }
}
