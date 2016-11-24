package manager;

import model.NetworkService;
import model.VirtualNetworkFunction;

import java.util.*;

/**
 * @author Janon Wang
 */
public class NetworkServiceManager {

    private double alpha;  // positive number!
    private double trafficRateMin; // Mbps
    private int vnfSum; // the number of the the vnf type
    private double vnfRelationPara;
    private Map<Integer, Integer> vnfMapping;
//    private double[][] vnfRelation;

    /**
     * Constructor.
     * @param vnfSum the total number of the vnf type
     * @param vnfRelationPara the relation para ranges from 0.1 to 1, step=0.1
     */
    public NetworkServiceManager(int vnfSum, double vnfRelationPara, double alpha, double trafficRateMin) {
        this.alpha = alpha;
        this.trafficRateMin = trafficRateMin;
        this.vnfSum = vnfSum;
        this.vnfRelationPara = vnfRelationPara;
        this.vnfMapping = new HashMap<>();
        // 3-->7-->9-->12
        // 2-->6-->8-->4-->10
        // 1-->5-->17-->16
        // 11-->13-->14
        // 15, 18, 19, 20
        this.vnfMapping.put(3,7);
        this.vnfMapping.put(7,9);
        this.vnfMapping.put(9,12);

        this.vnfMapping.put(2,6);
        this.vnfMapping.put(6,8);
        this.vnfMapping.put(8,4);
        this.vnfMapping.put(4,10);

        this.vnfMapping.put(1,5);
        this.vnfMapping.put(5,17);
        this.vnfMapping.put(17,16);

        this.vnfMapping.put(11,13);
        this.vnfMapping.put(13,14);
    }

    public NetworkService nextNS() {
        int sfcLength = generateSfcLength();
        int trafficRate = generateTrafficRate();
        LinkedList<VirtualNetworkFunction> vnfList = generateVnfList(sfcLength, trafficRate);
        return new NetworkService(sfcLength, vnfList, trafficRate);
    }

    // generate traffic rate according to the power law distribution, unit: Mbps
    private int generateTrafficRate() {
        // trafficRate 不会超过300mbps
        double trafficRate = randomToPowerLaw(this.trafficRateMin, this.alpha);
        return (int)(trafficRate % 150);
    }

    /**
     * generate a random number according to the Power law distribution.
     * @param mini the lower limit of the distribution
     * @param alpha the para of the distribution
     * @return the generated number
     */
    private double randomToPowerLaw(double mini, double alpha) {
        double base = Math.pow(mini, alpha - 1)/(1 - Math.random());
        double exponent = 1/(alpha - 1);
        return Math.pow(base, exponent);
    }

    private int generateSfcLength() {
        int sfcLengthShort = 1;
        int sfcLengthLong = 10;
        return randomUniformInt(sfcLengthShort, sfcLengthLong);
    }

    // generate vnf list according to the SFC possible chart
    private LinkedList<VirtualNetworkFunction> generateVnfList(int sfcLength, int trafficRate) {
        LinkedList<VirtualNetworkFunction> vnfList = new LinkedList<>();
        List<Integer> vnfUnChoosed = new ArrayList<>(vnfSum);
        // 初始化vnfUnChoosed集合
        for(int i = 0; i < vnfSum; i++) {
            vnfUnChoosed.add(i + 1);
        }
        int sfcHeaderType = randomUniformInt(1, vnfSum);
        vnfList.add(new VirtualNetworkFunction(sfcHeaderType, trafficRate));
        vnfUnChoosed.remove((Integer)sfcHeaderType);
        int lastVnfType = sfcHeaderType;
        // the first one has been handled， so begin with 1
        for(int i = 1; i < sfcLength; i++) {
            int nextVnfType = nextVNF(vnfUnChoosed, lastVnfType);
            vnfUnChoosed.remove((Integer)nextVnfType);
            lastVnfType = nextVnfType;
            vnfList.add(new VirtualNetworkFunction(nextVnfType, trafficRate));
        }
        return vnfList;
    }

    // 3-->7-->9-->12
    // 2-->6-->8-->4-->10
    // 1-->5-->17-->16
    // 11-->13-->14
    // 15, 18, 19, 20
    private int nextVNF(List<Integer> vnfUnChoosedList, int lastVnfType) {
        double random = Math.random();
        if(vnfMapping.containsKey(lastVnfType)) {
            int nextVnfType = vnfMapping.get(lastVnfType);
            if(vnfUnChoosedList.contains(nextVnfType)) {
                if(random < vnfRelationPara){
                    return nextVnfType;
                } else {
                    return vnfUnChoosedList.get(randomUniformInt(0, vnfUnChoosedList.size() - 1));
                }
            } else {
                return vnfUnChoosedList.get(randomUniformInt(0, vnfUnChoosedList.size() - 1));
            }
        } else {
            return vnfUnChoosedList.get(randomUniformInt(0, vnfUnChoosedList.size() - 1));
        }
    }

    /**
     * generate a int according to the uniform distribution, including the lower and high number.
     * @param lower the lower number
     * @param high the high number
     * @return the random number
     */
    private int randomUniformInt(int lower, int high) {
        return lower + (int)(Math.random()*(high - lower +1));
    }

    public static void main(String[] args) {
        // 关系系数取0.3时，已经有了很好的关联性
        NetworkServiceManager networkServiceManager = new NetworkServiceManager(20, 0.8, 2.1, 10);
        NetworkService networkService = networkServiceManager.nextNS();
        System.out.print("the length of the ns is:" + networkService.sfcLength + "\n");
        System.out.print("the traffic rate of the ns is:" + networkService.trafficRate + "\n");
        System.out.print("the sfc list of ns is: \n");
        for(VirtualNetworkFunction v : networkService.sfcList) {
            System.out.print("(vnfType: "+v.vnfType+"||  Mips: "+v.mipsNeeded+")"+"   ");
        }
    }
}
