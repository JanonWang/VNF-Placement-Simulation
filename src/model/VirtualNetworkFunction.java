package model;

/**
 * @author Janon Wang
 */
public class VirtualNetworkFunction {

    public int vnfType;
    public int mipsNeeded;
    public Address position;

    public VirtualNetworkFunction(int vnfType, int trafficRate) {
        this.vnfType = vnfType;
        this.mipsNeeded = calculateMips(trafficRate);
    }

    private int calculateMips(int trafficRate) {
        double random = Math.random();
        if(0 <= random && random < 0.9) {
            return 10*trafficRate;
        } else {
            return (int)(10*trafficRate*(Math.log(trafficRate)));
        }
    }
}
