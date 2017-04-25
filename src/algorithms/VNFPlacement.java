package algorithms;

import model.Address;
import model.NetworkService;
import model.VirtualMachine;
import model.VirtualNetworkFunction;

import java.util.Set;

/**
 * Created by Janon Wang on 2016/11/14.
 */
public interface VNFPlacement {
    void countVnf(NetworkService ns);
    VirtualMachine placeVNFInTopo(VirtualNetworkFunction virtualNetworkFunction);
    VirtualMachine placeVNFInServer(VirtualNetworkFunction virtualNetworkFunction, Address serverAddr);
    VirtualMachine placeVNFInRack(VirtualNetworkFunction virtualNetworkFunction, Address serverAddr);
    void showVnfCountMatrix();
    int[][] getVnfCountMatrix();
    boolean isMostRelated(int lastVNF, int thisVNF);
    boolean inSameGroup(int lastVNF, int thisVNF);
}
