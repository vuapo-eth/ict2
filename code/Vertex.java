// example taken from github.com/mikrohash/ecsim

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class Vertex {

    public final Transaction transaction;
    // use list instead of hashset for memory reasons: on average, this collection contains not even 2 elements
    private final List<Vertex> children = new LinkedList<>();
    private Vertex branch, trunk;

    public Vertex(Transaction transaction) {
        this.transaction = transaction;
    }

    public Collection<Vertex> getChildren() {
        // consider using a custom list that cannot be modified externally for performance reasons instead of creating a new list 
        return new LinkedList<>(children);
    }

    public void addChild(Vertex child) {

        boolean isBranchOfChild = child.transaction.branch.equals(transaction.hash);
        boolean isTrunkOfChild = child.transaction.trunk.equals(transaction.hash);

        if(!isBranchOfChild && !isTrunkOfChild)
            throw new IllegalArgumentException(child.transaction.hash + " is not a child of " + transaction.hash);

        if(!childen.contains(child)) {
            children.add(child);
            if(isBranchOfChild) child.setBranch(this);
            if(isTrunkOfChild) child.setTrunk(this);
        }
    }

    public Vertex getBranch() {
        return branch;
    }

    public void setBranch(Vertex branch) {
        if(!branch.transaction.hash.equals(transaction.branch))
            throw new IllegalArgumentException(branch.transaction.hash + " is not the branch of " + transaction.hash);
        if(this.branch == null) {
            this.branch = branch;
            branch.addChild(this);
        }
    }

    public Vertex getTrunk() {
        return trunk;
    }

    public void setTrunk(Vertex trunk) {
        if(!trunk.transaction.hash.equals(transaction.trunk))
            throw new IllegalArgumentException(trunk.transaction.hash + " is not the trunk of " + transaction.hash);
        if(this.trunk == null) {
            this.trunk = trunk;
            trunk.addChild(this);
        }
    }
}
