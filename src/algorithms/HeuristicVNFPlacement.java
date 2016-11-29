package algorithms;

import main.VNFPSimulation;
import model.*;
import topology.Topology;
import topology.fatTree.FatTreeTopo;

import java.util.*;

/**
 * @author Janon Wang
 */
public class HeuristicVNFPlacement implements VNFPlacement{

    private Topology topo;
    private int[][] vnfCountMatrix;
    private LinkedList<Integer> vnfQueue;
    private final int topN =1;

    private double para1;
    private double para2;
    private double para3;


    public HeuristicVNFPlacement(Topology topo, double para1, double para2, double para3) {
        this.topo = topo;
        this.para1 = para1;
        this.para2 = para2;
        this.para3= para3;
        this.vnfCountMatrix = new int[VNFPSimulation.vnfSum + 1][VNFPSimulation.vnfSum + 1];
        for(int i = 1; i < VNFPSimulation.vnfSum + 1; i++) {
            this.vnfCountMatrix[0][i] = i;
            this.vnfCountMatrix[i][0] = i;
        }
        this.vnfQueue = new LinkedList<>();
    }

    @Override
    public void countVnf(NetworkService ns) {
        LinkedList<VirtualNetworkFunction> sfcList = ns.sfcList;
        Iterator<VirtualNetworkFunction> sfcIterator = sfcList.iterator();
        int lastVnf = (sfcIterator.next()).vnfType;
        while(sfcIterator.hasNext()) {
            int thisVnf = (sfcIterator.next()).vnfType;
            this.vnfCountMatrix[lastVnf][thisVnf]++;
            lastVnf = thisVnf;
        }
    }

    @Override
    public void showVnfCountMatrix() {
        System.out.println("the vnf count Matrix is :");
        for(int i = 0; i < VNFPSimulation.vnfSum + 1; i++) {
            for(int j = 0; j < VNFPSimulation.vnfSum + 1; j++) {
                System.out.print(this.vnfCountMatrix[i][j] + "   ");
            }
            System.out.println();
        }
    }

    @Override
    public int[][] getVnfCountMatrix() {
        return this.vnfCountMatrix;
    }

    @Override
    // 部署一个VNF，并将部署的VNF返回
    public VirtualMachine placeNewVNF(int vnfType) {
        VirtualMachine vm = generateVnf(vnfType);
        PhysicalServer server = whichServer(vm);
        if(server == null) {
            return null;
        } else {
            this.topo.launchVM(server, vm);
            // System.out.println(vnfType + " is launched at " + server.getAddress().toString());
            // 将已经启动过的VNF在队列中做记录
            addQueue(vnfType);
            return vm;
        }
    }

    /**
     * 过滤出所有的可行的server，然后根据server的cpu，ram，vnfTypeCount来打分.
     * 最后选出topN（5）的server，在其中随机选择一台server来启动VM
     * @param virtualMachine 需要启动的VM
     * @return 启动VM的server
     */
    private PhysicalServer whichServer(VirtualMachine virtualMachine) { // checked!
        Set<PhysicalServer> filteredServers = filterServers(virtualMachine.cpu, virtualMachine.ram);
        if(filteredServers.isEmpty()) {
            return null;
        } else {
            ArrayList<PhysicalServer> weightedServers = this.weigthServers(filteredServers, virtualMachine.vnfType);
            // 从topN中随机选择一个
            int random = randomUniformInt(0, weightedServers.size() - 1);
            return weightedServers.get(random);
        }
    }

    // 遍历所有的server，找出满足条件的server
    private Set<PhysicalServer> filterServers(int cpuNeeded, int ramNeeded) { // checked!
        Set<PhysicalServer> servers = new HashSet<>();
        int k = ((FatTreeTopo)this.topo).getK();
        for(int i = 1; i <= k; i++) {
            for(int j = 1; j <= k/2; j++) {
                for(int h = 1; h <= k/2; h++) {
                    PhysicalServer server = topo.getServerById(i, j, h);
                    if(server.isResEnough(cpuNeeded, ramNeeded))
                        servers.add(server);
                }
            }
        }
        return servers;
    }

    //将选出的server排序并选出topN的server,排序依据server剩余的cpu，ram和所要启动的VNF类型
    private ArrayList<PhysicalServer> weigthServers(Set<PhysicalServer> serversSet, int vnfType) { // checked!
        // 如果可行的server小于topN， 则全部返回
        ArrayList<PhysicalServer> servers = new ArrayList<>();
        serversSet.forEach(servers::add);
        if(servers.size() <= this.topN) {
            return servers;
        }
        double[] multiplier = new double[]{para1, para2, para3};  //FIXME  ---  设置server评分时元素的权重
        int size = servers.size();
        double[][] decisionM = new double[size][3];
        // 计算决策矩阵
        for(int i = 0; i < size; i++) {
            PhysicalServer s = servers.get(i);
            decisionM[i][0] = s.getCpuCoreRemain();
            decisionM[i][1] = s.getRamRemain();
            decisionM[i][2] = calculateVnfScore(s, vnfType);
        }
        // 数据归一化
        decisionM = normalizeM(decisionM, size);
        // 加权
        for(int i = 0; i < size; i++) {
            for(int j = 0; j < 3; j++) {
                decisionM[i][j] *= multiplier[j];
            }
        }
        // 算总分, 总分存在第一列
        double[] serverScore = new double[size];
        for(int i = 0; i < size; i++) {
            serverScore[i] = decisionM[i][0] + decisionM[i][1] + decisionM[i][2];
        }
        // topNIndex中存的是前TopN server的index
        int[] topNIndex = findTopN(serverScore, size);
        ArrayList<PhysicalServer> topNServers = new ArrayList<>();
        for(int i : topNIndex) {
            topNServers.add(servers.get(i));
        }
        // 直接返回topN servers
        return topNServers;
    }

    /**
     * 计算server与vnfA的关系系数，先根据统计矩阵找出与vnfA上一跳关系最紧密的vnfB和下一跳关系最紧密的vnfC.
     * 然后用server上vnfB的启动数量和vnfC的启动数量作为server的得分
     * @param server 需要打分的目标server
     * @param vnfType vnfA
     * @return server的得分
     */
    private double calculateVnfScore(PhysicalServer server, int vnfType) { // checked!
        // ------------------------- 根据vnfCount矩阵之间的数量关系来计算不同来计算系数
//        double[] vnfMultiplier = getVnfMultiplier(vnfType);
//        int[] vnfCounts = server.getVnfCounts();
//        double score = 0;
//        for(int i = 1; i <= VNFPSimulation.vnfSum; i++) {
//            score += vnfMultiplier[i]*vnfCounts[i];
//        }
//        return score;
        // ---------------------------------
        // vnfCount中存的是其他的vnf与vnfA关系系数
        int[] lastVnfCount = new int[VNFPSimulation.vnfSum + 1];
        int[] nextVnfCount = new int[VNFPSimulation.vnfSum + 1];
        for(int i = 1; i <= VNFPSimulation.vnfSum; i++){
            lastVnfCount[i] = this.vnfCountMatrix[i][vnfType];
            nextVnfCount[i] = this.vnfCountMatrix[vnfType][i];
        }
        int max1 = lastVnfCount[0];
        int max2 = nextVnfCount[0];
        // maxIndex 代表的是与vnfType 最相关的vnf的类型
        int maxIndex1 = 0;
        int maxIndex2 = 0;
        for(int i = 1; i <= VNFPSimulation.vnfSum; i++) {
            if(lastVnfCount[i] > max1)
                maxIndex1 = i;
            if(nextVnfCount[i] > max2)
                maxIndex2 = i;
        }
        return server.getVnfCount(maxIndex1) + server.getVnfCount(maxIndex2);
    }

    private double[][] normalizeM(double[][] m, int size) { //checked!
        for(int i = 0; i < 3; i++) {
            double max = m[0][i];
            double min = m[0][i];
            for(int j = 0; j < size; j++) {
                if(m[j][i] > max) {
                    max = m[j][i];
                }
                if(m[j][i] < min) {
                    min = m[j][i];
                }
            }
            if((max - min) != 0){
                for(int j = 0; j < size; j++) {
                    m[j][i] = (m[j][i] - min)/(max - min);
                }
                // 当最大值和最小值相等时候，该项分数就没有意义了，全部置0
            } else {
                for(int j = 0; j < size; j++) {
                    m[j][i] = 0;
                }
            }
        }
        return m;
    }

    /**
     * 找到数组scores中最大的topN个数字，返回他们的序号
     * @param scores 数组
     * @param size 数组的大小
     * @return 最大的topN个数字在数组中的序号
     */
    private int[] findTopN(double[] scores, int size) {  // tested!
        ScoresObject[] scoresObjects = new ScoresObject[size];
        for(int i = 0; i < size; i++) {
            scoresObjects[i] = new ScoresObject(i, scores[i]);
        }
        for(int i = size - 1; i > 0; i--) {
            for(int j = 0; j < i; j++) {
                if(scoresObjects[j].score < scoresObjects[j + 1].score) {
                    ScoresObject tmp = scoresObjects[j];
                    scoresObjects[j] = scoresObjects[j + 1];
                    scoresObjects[j + 1] = tmp;
                }
            }
        }
        ScoresObject[] topNScoreObject = new ScoresObject[this.topN];
        System.arraycopy(scoresObjects, 0, topNScoreObject, 0, this.topN);
        int[] index = new int[this.topN];
        for(int i = 0; i < this.topN; i++) {
            index[i] = topNScoreObject[i].position;
        }
        return index;
    }

    // 辅助分数类
    private class ScoresObject{
        private int position;
        private double score;
        ScoresObject(int position, double score) {
            this.position = position;
            this.score = score;
        }
    }

    // 根据vnfCount的统计结果，来计算每种vnf对该vnf的影响因子
    private double[] getVnfMultiplier(int vnfType) {
        int vnfSum = VNFPSimulation.vnfSum + 1;  //序号为0的不使用，为了和vnf的种类统一
        double[] vnfMultiplier = new double[vnfSum];
        int[] sums = new int[vnfSum];
        int sum = 0;
        for(int i = 1; i <= vnfSum; i++) {
            sums[i] = vnfCountMatrix[vnfType][i] + vnfCountMatrix[i][vnfType];
            sum += sums[i];
        }
        for(int i = 1; i <= vnfSum; i++) {
            vnfMultiplier[i] = sums[i]/sum;
        }
        return vnfMultiplier;
    }

    private VirtualMachine generateVnf(int vnfType) { // checked!
        int count = getCountInQueue(vnfType);
        //return new VirtualMachine(1, 1024, vnfType);
        switch(count) {
            case 0:
                // VirtualMachine(int cpuNeeded, int ramNeeded, int vnfType)
                return new VirtualMachine(1, 1024, vnfType);
            case 1:
                return new VirtualMachine(2, 2048, vnfType);
            case 2:
                return new VirtualMachine(3, 4096, vnfType);
            case 3:
                return new VirtualMachine(4, 8192, vnfType);
            default:
                return new VirtualMachine(4, 8192, vnfType);
        }
    }

    private void addQueue(int vnfType) { // checked!
        int queueLimit = 20;
        if(this.vnfQueue.size() >= queueLimit) {
            vnfQueue.removeLast();
            vnfQueue.addFirst(vnfType);
        } else {
            vnfQueue.addFirst(vnfType);
        }
    }


    private int getCountInQueue(int vnfType) { // checked!
        //return vnfQueue.indexOf(vnfType) + 1; // index函数从头开始遍历
        int count = 0;
        for(Integer vnf : vnfQueue) {
            if(vnf == vnfType)
                count++;
        }
        return count;
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
}
