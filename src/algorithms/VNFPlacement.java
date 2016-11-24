package algorithms;

import model.NetworkService;
import model.VirtualMachine;

import java.util.Set;

/**
 * Created by Janon Wang on 2016/11/14.
 */
public interface VNFPlacement {
    void countVnf(NetworkService ns);
    //boolean placeVNFs(Set<Integer> vnfTypes);
    VirtualMachine placeNewVNF(int vnfType);
    void showVnfCountMatrix();
    int[][] getVnfCountMatrix();
}
