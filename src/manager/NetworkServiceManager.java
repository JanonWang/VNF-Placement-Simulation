package manager;

import main.VNFPSimulation;
import model.NetworkService;
import model.VirtualNetworkFunction;

import java.util.*;

/**
 * @author Janon Wang
 */
public class NetworkServiceManager {

    private double alpha;  // positive number!
    private double trafficRateMin; // Mbps
//    private int vnfSum; // the number of the the vnf type
    private double vnfRelationPara;
    private int vnfPerCluster;
    private Map<Integer, Integer> vnfMapping;
    public int vnfSum;
    private int generateNSTimes;
//    private double[][] vnfRelation;

    public NetworkServiceManager(double vnfRelationPara, double alpha, double trafficRateMin, int generateNSTimes) {
        this.alpha = alpha;
        this.trafficRateMin = trafficRateMin;
        this.vnfRelationPara = vnfRelationPara;
        this.vnfPerCluster = 10;
        this.vnfSum = VNFPSimulation.vnfSum;
        this.generateNSTimes = generateNSTimes;
        this.vnfMapping = new HashMap<>();
        // ---------------修改之后的分组情况
        // 3-->7-->9-->12-->15
        // 2-->6-->8-->4-->10-->18
        // 1-->5-->17-->16-->19
        // 11-->13-->14-->20
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

        // 后加的关联映射
        this.vnfMapping.put(12,15);
        this.vnfMapping.put(10,18);
        this.vnfMapping.put(16,19);
        this.vnfMapping.put(14,20);
    }

    public NetworkService nextNS() {
        if(generateNSTimes == 0) {
            return null;
        }
        int serviceType = randomUnifromInt(1, 5);
        // 该部分代码为调整chain长度时用的代码
//        int publicLength;
//        int serviceLength;
//        do{
//            publicLength = generateSfcLength();
//            serviceLength = generateSfcLength();
//        } while((publicLength + serviceLength) == 8);
        int publicLength = generateSfcLength();
        int serviceLength = generateSfcLength();

        int trafficRate = generateTrafficRate();
        LinkedList<VirtualNetworkFunction> vnfList = generateVnfList(serviceType, publicLength, serviceLength, trafficRate);
        generateNSTimes--;
        return new NetworkService(publicLength+serviceLength, vnfList, trafficRate);
    }

    // generate traffic rate according to the power law distribution, unit: Mbps
    private int generateTrafficRate() {
        // 近似取整---trafficRate 不会超过150mbps
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
        int sfcLengthLong = 5;
        return randomUnifromInt(sfcLengthShort, sfcLengthLong);
    }

    // generate vnf list according to the SFC possible chart
    private LinkedList<VirtualNetworkFunction> generateVnfList(int serviceType, int publicLength,
                                                               int serviceLength, int trafficRate) {
        LinkedList<VirtualNetworkFunction> vnfList = new LinkedList<>();
        // public part
        List<Integer> publicVnfUnChoose = new ArrayList<>(vnfPerCluster);
        // 初始化vnfUnChoose集合
        for(int i = 1; i <= vnfPerCluster; i++) {
            publicVnfUnChoose.add(i);
        }
        int publicHeaderType = randomUnifromInt(1, vnfPerCluster);
        vnfList.add(new VirtualNetworkFunction(publicHeaderType, trafficRate));
        publicVnfUnChoose.remove((Integer)publicHeaderType);
        int lastVnfType = publicHeaderType;
        // the first one has been handled， so begin with 1
        for(int i = 1; i < publicLength; i++) {
            int nextVnfType = nextVNF(publicVnfUnChoose, lastVnfType, 0); // publicVNF的类型为0
            publicVnfUnChoose.remove((Integer)nextVnfType);
            lastVnfType = nextVnfType;
            vnfList.add(new VirtualNetworkFunction(nextVnfType, trafficRate));
        }
        // service specific part
        List<Integer> serviceVnfUnChoose = new ArrayList<>(vnfPerCluster);
        for(int i = 0; i < vnfPerCluster; i++) {
            serviceVnfUnChoose.add(serviceType*vnfPerCluster + i + 1);
        }
        int serviceHeaderType = randomUnifromInt(serviceType*vnfPerCluster+1, serviceType*vnfPerCluster + vnfPerCluster);
        vnfList.add(new VirtualNetworkFunction(serviceHeaderType, trafficRate));
        serviceVnfUnChoose.remove((Integer)serviceHeaderType);
        lastVnfType = serviceHeaderType;
        for(int i = 1; i < serviceLength; i++) {
            int nextVnfType = nextVNF(serviceVnfUnChoose, lastVnfType, serviceType);
            serviceVnfUnChoose.remove((Integer)nextVnfType);
            lastVnfType = nextVnfType;
            vnfList.add(new VirtualNetworkFunction(nextVnfType, trafficRate));
        }
        return vnfList;
    }

    // ---------------修改之后的分组情况
    // 3-->7-->9-->12-->15
    // 2-->6-->8-->4-->10-->18
    // 1-->5-->17-->16-->19
    // 11-->13-->14-->20
    private int nextVNF(List<Integer> vnfUnChooseList, int lastVnfType, int serviceType) {
        double random = Math.random();
        int nextVnfType;
        if(lastVnfType + 1 > serviceType * vnfPerCluster + vnfPerCluster) {
            nextVnfType = serviceType * vnfPerCluster + 1;
        } else {
            nextVnfType = lastVnfType + 1;
        }
        if(random < vnfRelationPara && vnfUnChooseList.contains(nextVnfType)) {
            return nextVnfType;
        } else {
            return vnfUnChooseList.get(randomUnifromInt(0, vnfUnChooseList.size() - 1));
        }
    }
    /**
     * generate a int according to the uniform distribution, including the lower and high number.
     * @param lower the lower number
     * @param high the high number
     * @return the random number
     */
    private int randomUnifromInt(int lower, int high) {
        return lower + (int)(Math.random()*(high - lower +1));
    }

    //这个系数的解释是以比平均更大的概率产生
    public static void main(String[] args) {
        NetworkServiceManager networkServiceManager = new NetworkServiceManager(0, 2.1, 10, -1);
        int vnfSum = networkServiceManager.vnfSum;
        int[][] vnfCountMatrix = new int[vnfSum + 1][vnfSum + 1];
        int[] vnfCount = new int[vnfSum + 1];
        for(int i = 1; i < vnfSum + 1; i++) {
            vnfCountMatrix[0][i] = i;
            vnfCountMatrix[i][0] = i;
        }
        // 关系系数取0.3时，已经有了很好的关联性
        int count = 1000;
        while(count != 0) {
            NetworkService networkService = networkServiceManager.nextNS();
//            System.out.print("the length of the ns is:" + networkService.sfcLength + "\n");
//            System.out.print("the traffic rate of the ns is:" + networkService.trafficRate + "\n");
//            System.out.print("the sfc list of ns is: \n");
//            for(VirtualNetworkFunction v : networkService.sfcList) {
//                System.out.print("(vnfType: "+v.vnfType+"||  Mips: "+v.mipsNeeded+")"+"   ");
//            }
//            System.out.println();
            LinkedList<VirtualNetworkFunction> sfcList = networkService.sfcList;
            Iterator<VirtualNetworkFunction> sfcIterator = sfcList.iterator();
            int lastVnf = (sfcIterator.next()).vnfType;
            vnfCount[lastVnf]++;
            while(sfcIterator.hasNext()) {
                int thisVnf = (sfcIterator.next()).vnfType;
                vnfCount[thisVnf]++;
                vnfCountMatrix[lastVnf][thisVnf]++;
                lastVnf = thisVnf;
            }
            count--;
        }
        System.out.println("the vnf count is :");
        for(int i = 1; i < vnfSum+1; i++) {
            System.out.print(vnfCount[i] + "   ");
        }
        System.out.println();
        System.out.println("the vnf count Matrix is :");
        for(int i = 0; i < vnfSum+1; i++) {
            for(int j = 0; j < vnfSum+1; j++) {
                System.out.print(vnfCountMatrix[i][j] + "   ");
            }
            System.out.println();
        }
    }
}
