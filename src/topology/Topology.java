package topology;

import model.*;

/**
 * @author Janon Wang
 */
public interface Topology {
    PhysicalServer[] getServerByEdge(short podId, short edgei);
    PhysicalServer getServerById(int podId, int edgei, int serveri);
    PhysicalServer getServerByAddress(Address addr);
    Switch getSwitchByAddress(Address addr);
    Iterable<Link> getRelatedLinks(Address addr);
    void launchVM(PhysicalServer server, VirtualMachine vm);
    int[] getPodVnfCounts(int podId);
    int[] getEdgeVnfCounts(int podId, int edgeId);
}
