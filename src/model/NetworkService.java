package model;

import java.util.LinkedList;

/**
 * @author Janon Wang
 */
public class NetworkService {
    public int sfcLength;
    public LinkedList<VirtualNetworkFunction> sfcList;
    public int trafficRate;

    public NetworkService(int sfcLength, LinkedList<VirtualNetworkFunction> sfc, int trafficRate) {
        this.sfcLength = sfcLength;
        this.sfcList = sfc;
        this.trafficRate = trafficRate;
    }
}
