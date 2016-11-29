package algorithms;

import Jama.EigenvalueDecomposition;
import Jama.Matrix;
import main.VNFPSimulation;
import model.*;
import topology.Topology;
import topology.fatTree.FatTreeTopo;

import java.util.*;

/**
 * @author Janon Wang
 */
public class AdvancedVNFPlacement implements VNFPlacement{

    private Topology topo;

    private double[][] vnfCountMatrix;
    private LinkedList<Integer> vnfQueue;
    private final int cutNumberK = 5; // 暂定为5，分的太细了也不行，造成多个vnf一个一组的情况

    private double para1;
    private double para2;
    private double para3;

    public AdvancedVNFPlacement(Topology topo, double para1, double para2, double para3) {
        this.topo = topo;
        this.para1 = para1;
        this.para2 = para2;
        this.para3 = para3;
        this.vnfCountMatrix = new double[VNFPSimulation.vnfSum + 1][VNFPSimulation.vnfSum + 1];
        for(int i = 1; i < VNFPSimulation.vnfSum + 1; i++) {
            this.vnfCountMatrix[0][i] = i % 10;
            this.vnfCountMatrix[i][0] = i % 10;
        }
        this.vnfQueue = new LinkedList<>();
    }

    @Override
    public void countVnf(NetworkService ns) {
        LinkedList<VirtualNetworkFunction> sfcList = ns.sfcList;
        Iterator sfcIterator = sfcList.iterator();
        int lastVnf = ((VirtualNetworkFunction) sfcIterator.next()).vnfType;
        while(sfcIterator.hasNext()) {
            int thisVnf = ((VirtualNetworkFunction) sfcIterator.next()).vnfType;
            vnfCountMatrix[lastVnf][thisVnf]++;
            lastVnf = thisVnf;
        }
    }

    @Override
    public void showVnfCountMatrix() {
        System.out.println("the vnf count Matrix is :");
        for(int i = 0; i < VNFPSimulation.vnfSum + 1; i++) {
            for(int j = 0; j < VNFPSimulation.vnfSum + 1; j++) {
                System.out.print(vnfCountMatrix[i][j] + "   ");
            }
            System.out.println();
        }
    }

    @Override
    public int[][] getVnfCountMatrix() {
        int[][] tmp = new int[VNFPSimulation.vnfSum + 1][VNFPSimulation.vnfSum + 1];
        for (int i = 0; i < VNFPSimulation.vnfSum + 1; i++) {
            for (int j = 0; j < VNFPSimulation.vnfSum + 1; j++) {
                tmp[i][j] = (int) vnfCountMatrix[i][j];
            }
        }
        return tmp;
    }

    @Override
    public VirtualMachine placeNewVNF(int vnfType) {
        VirtualMachine vm = generateVnf(vnfType);
        PhysicalServer server = whichSever(vm);
        if(server == null) {
            return null;
        } else {
            topo.launchVM(server, vm);
            // 将已经启动过的VNF在队列中做记录
            addQueue(vnfType);
            return vm;
        }
    }

    private PhysicalServer whichSever(VirtualMachine virtualMachine) {
        int thisVnfType = virtualMachine.vnfType;
        int cpuNeeded =  virtualMachine.cpu;
        int ramNeeded = virtualMachine.ram;
        Set<Set<Integer>> vnfGroups = getKcut(cutNumberK);
        // vnfGroup中包含了和该vnf分在一组的其他vnf的信息
        Set<Integer> vnfGroup = getVnfGroup(vnfGroups, thisVnfType);
        int[] podIdRankResult = rankPod(vnfGroup);
        for(int podId : podIdRankResult) {
            int[] edgeSwitchIds = rankEdgeSwitch(vnfGroup, podId);
            for(int edgeSwitchId : edgeSwitchIds) {
                EdgeSwitch edgeSwitch = (EdgeSwitch) topo.getSwitchByAddress(new Address(podId, 0, edgeSwitchId, 0));
                Set<PhysicalServer> serverInEdge = new HashSet<>();
                edgeSwitch.getDownLinks().forEach(link -> serverInEdge.add((PhysicalServer) link.getDownNode()));
                ArrayList<PhysicalServer> filteredServer = filterServers(serverInEdge, cpuNeeded, ramNeeded);
                if(!filteredServer.isEmpty())
                    return weightServers(filteredServer, vnfGroup);
            }
        }
        // 遍历了全部的pod，都没有找到可行的server，则返回空
        return null;
    }

    // FIXME -- 当set为空的情况
    private int[] rankPod(Set<Integer> vnfGroup) {
        int k = ((FatTreeTopo)topo).getK();
        if(vnfGroup.isEmpty()) {
            int random = randomUniformInt(1, k);
            int[] rankResult = new int[k];
            for(int j = 0; j < k; j++) {
                rankResult[j] = random % k;
                random++;
            }
            return rankResult;
        } else {
            Map<Integer, Integer> podVnfCountsMap = new HashMap<>();         // <podId, relatedVnfSum>
            for(int i = 1; i <= k; i++) {
                int[] vnfCounts = topo.getPodVnfCounts(i);
                int relatedVnfSum = 0;
                for(Integer vnf : vnfGroup) {
                    relatedVnfSum += vnfCounts[vnf];
                }
                podVnfCountsMap.put(i, relatedVnfSum);
            }
            return sortMapByValue(podVnfCountsMap);
        }
    }

    // 返回一个pod中的根据vnfGroup的排序的edgeSwitch
    private int[] rankEdgeSwitch(Set<Integer> vnfGroup, int podId) {
        int k = ((FatTreeTopo)topo).getK();
        if(vnfGroup.isEmpty()){
            int random = randomUniformInt(1, k/2);
            int rankResult[] = new int[k/2];
            for(int j = 0; j < k/2; j++) {
                rankResult[j] = random % k;
                random++;
            }
            return rankResult;
        } else {
            Map<Integer, Integer> edgeVnfCountsMap = new HashMap<>();
            for(int i = 1; i <= k/2; i++) {
                int[] vnfCounts = topo.getEdgeVnfCounts(podId, i);
                int relatedVnfSum = 0;
                for(Integer vnf : vnfGroup) {
                    relatedVnfSum += vnfCounts[vnf];
                }
                edgeVnfCountsMap.put(i, relatedVnfSum);
            }
            return sortMapByValue(edgeVnfCountsMap);
        }
    }

    // order[0] 中存储的key的值的value是最大的 -- debug done!
    private int[] sortMapByValue(Map<Integer, Integer> map) {
        int[] order = new int[map.keySet().size()];
        int i = 0;
        for(Integer vnfType : map.keySet()){
            order[i] = vnfType;
            i++;
        }
        // bubble sort
        for(int j = order.length -1; j > 0; j--) {
            for(int k = 0; k < j; k++) {
                int tmp;
                if(map.get(order[k]) < map.get(order[k + 1])) {
                    tmp = order[k];
                    order[k] = order[k + 1];
                    order[k + 1] = tmp;
                }
            }
        }
        return order;
    }

    // 根据CPU，RAM信息，过滤edgeSwitch上的server
    private ArrayList<PhysicalServer> filterServers(Set<PhysicalServer>servers, int cpuNeeded, int ramNeeded) {
        ArrayList<PhysicalServer> filteredServers = new ArrayList<>();
        for(PhysicalServer server : servers) {
            if(server.isResEnough(cpuNeeded, ramNeeded))
                filteredServers.add(server);
        }
        return filteredServers;
    }

    //将edgeSwitch上过滤过得server排序，直接返回得分最高的server
    private PhysicalServer weightServers(ArrayList<PhysicalServer> servers, Set<Integer> vnfGroup) {
        double[] multiplier = new double[]{para1, para2, para3};
        int size = servers.size();
        double[][] decisionM = new double[size][3];
        // 计算决策矩阵
        int ii = 0;
        for(PhysicalServer s : servers) {
            decisionM[ii][0] = s.getCpuCoreRemain();
            decisionM[ii][1] = s.getRamRemain();
            decisionM[ii][2] = calculateVnfScore(s, vnfGroup);
            ii++;
        }
        // 数据归一化
        decisionM = normalizeM(decisionM, size);
        // 加权
        for(int i = 0; i < size; i++) {
            for(int j = 0; j < 3; j++) {
                decisionM[i][j] *= multiplier[j];
            }
        }
        // 找出得分最高的server
        int maxIndex = 0;
        double max = decisionM[0][0] + decisionM[0][1] + decisionM[0][2];
        for(int i = 0; i < size; i++) {
            if((decisionM[i][0] + decisionM[i][1] + decisionM[i][2]) > max) {
                maxIndex = i;
            }
        }
        return servers.get(maxIndex);
    }

    // 计算server关于vnf的得分，server上关于该组中的vnf组中vnf的数量总和
    private double calculateVnfScore(PhysicalServer server, Set<Integer> vnfGroup) {
        int[] vnfCount = server.getVnfCounts();
        double vnfSum = 0;
        for(Integer vnf : vnfGroup) {
            vnfSum += vnfCount[vnf];
        }
        return vnfSum;
    }

    private double[][] normalizeM(double[][] m, int size){
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

    private Set<Set<Integer>> getKcut(int k) {
        // 矩阵转化成对称矩阵，有向图变成无向图
        double[][] vnfCountMatrix1 = new double[vnfCountMatrix.length - 1][vnfCountMatrix.length - 1];
        for (int i = 1; i < vnfCountMatrix.length; i++) {
            for (int j = 1; j < vnfCountMatrix.length; j++) {
                vnfCountMatrix1[i - 1][j - 1] = vnfCountMatrix[i][j] + vnfCountMatrix[j][i];
            }
        }
        // 对角线元素代表元素的度
        for(int i = 0; i < vnfCountMatrix1.length; i++) {
            int count = 0;
            for(int j = 0; j < vnfCountMatrix1.length; j++) {
                if(vnfCountMatrix1[i][j] > 0)
                    count ++;
            }
            vnfCountMatrix1[i][i] = count;
        }
        int cutNumber = getCutNumber(vnfCountMatrix1);
        // 当划分的子图数量大于等于要求的子图数量的时候，停止划分子图
        while(cutNumber < k) {
            removeMinEdgeOfMatrix(vnfCountMatrix1);
            cutNumber = getCutNumber(vnfCountMatrix1);
        }
        return getSubGraph(vnfCountMatrix1);
    }

    // 根据拉普拉斯矩阵计算子图的数量，拉普拉斯矩阵的特征值为0的个数，即为图的子图数量
    private int getCutNumber(double[][] m) {
        // 深拷贝，转换成拉普拉斯矩阵，对角线元素不用改了
        double[][] mTmp = new double[m.length][];
        for(int i = 0; i < m.length; i++) {
            mTmp[i] = m[i].clone();
        }
        for(int i = 0; i < mTmp.length; i++) {
            for(int j = 0; j < mTmp.length; j++) {
                if(i == j)
                    continue;
                if(mTmp[i][j] > 0)
                    mTmp[i][j] = -1;
            }
        }
        int cutNumber = 0;
        Matrix mTmp1 = new Matrix(mTmp);
        EigenvalueDecomposition eigenvalueDecomposition = new EigenvalueDecomposition(mTmp1);
        double[] eigenValues = eigenvalueDecomposition.getRealEigenvalues();
        for(double eigenValue : eigenValues) {
            // 算出来的结果有一点点的偏差，不是正好是整数
            if(Math.abs(eigenValue - 0) < 0.001)
                cutNumber++;
        }
        return cutNumber;
    }

    // 去掉图中权值最小的边
    private void removeMinEdgeOfMatrix(double[][] matrix) {
        // 初始化一个较大的值
        double mini = 999999;
        int miniRow = 0;
        int miniColumn = 1;
        for(int i = 0; i < matrix.length; i++) {
            for(int j = i + 1; j <matrix.length; j++) {
                if(matrix[i][j] < mini && matrix[i][j] > 0) {
                    mini = matrix[i][j];
                    miniRow = i;
                    miniColumn = j;
                }
            }
        }
        // 将最小的边置0
        System.out.println("miniRow: " + miniRow + "miniColumn: " + miniColumn);
        matrix[miniRow][miniColumn] = 0;
        matrix[miniColumn][miniRow] = 0;
        matrix[miniColumn][miniColumn]--;
        matrix[miniRow][miniRow]--;
    }

    // 根据邻接矩阵来划分子图，子图中的元素放在同一个set中 --- debug finished！
    private Set<Set<Integer>> getSubGraph(double[][] matrix) {
// 所有的边
        Set<Pair> pairs = new HashSet<>();
        for(int i = 0; i < matrix.length; i++) {
            for(int j = i+1; j < matrix.length; j++) {
                if(matrix[i][j] > 0)
                    pairs.add(new Pair(i + 1, j + 1));
            }
        }
        // 所有的点集合
        Map<Integer, Set<Integer>> vnfGroups = new HashMap<>();
        // 记录vnf属于哪个集合
        int[] record = new int[matrix.length + 1];
        // 集合的序号，从1开始编号
        int setNumber = 1;
        for(Pair pair : pairs) {
            int vnf1 = pair.vnf1;
            int vnf2 = pair.vnf2;
            if(record[vnf1] == 0 && record[vnf2] == 0) {
                Set<Integer> vnfGroup = new HashSet<>();
                vnfGroups.put(setNumber, vnfGroup);
                vnfGroup.add(vnf1);
                vnfGroup.add(vnf2);
                record[vnf1] = setNumber;
                record[vnf2] = setNumber;
            } else if(record[vnf1] == 0 && record[vnf2] != 0) {
                vnfGroups.get(record[vnf2]).add(vnf1);
                record[vnf1] = record[vnf2];
            } else if(record[vnf1] != 0 && record[vnf2] == 0) {
                vnfGroups.get(record[vnf1]).add(vnf2);
                record[vnf2] = record[vnf1];
            } else {
                // 如果两个点都标记过了且他们所在的集合不是同一个，则需要将这两个集合合并
                if(record[vnf1] != record[vnf2]) {
                    Set<Integer> vnf1Set = vnfGroups.get(record[vnf1]);
                    int vnf2Record = record[vnf2];
                    for(Integer i : vnfGroups.get(record[vnf2])) {
                        record[i] = record[vnf1];
                        vnf1Set.add(i);
                    }
                    vnfGroups.remove(vnf2Record);
                    record[vnf2] = record[vnf1];
                }
            }
            setNumber++;
        }
        Set<Set<Integer>> setset = new HashSet<>();
        vnfGroups.keySet().forEach( key -> setset.add(vnfGroups.get(key)));
        // 对于unMarked集合中遗留的单个的点的处理,一个点一个集合
        for(int i = 1; i < record.length; i++) {
            if(record[i] == 0) {
                Set<Integer> singleSet = new HashSet<>();
                singleSet.add(i);
                setset.add(singleSet);
            }
        }
        return setset;
    }

    // 辅助类
    private class Pair{
        int vnf1;
        int vnf2;
        Pair(int vnf1, int vnf2){
            this.vnf1 = vnf1;
            this.vnf2 = vnf2;
        }
    }

    // 根据子图信息，找到该元素所属的子图，并返回该子图中除了该元素之外的元素
    private Set<Integer> getVnfGroup(Set<Set<Integer>> vnfGroups, int vnfType) {
        boolean isFind = false;
        for(Set<Integer> vnfGroup : vnfGroups) {
            for(Integer vnf : vnfGroup) {
                if(vnf == vnfType) {
                    isFind = true;
                    break;
                }
            }
            if(isFind) {
                vnfGroup.remove(vnfType);
                return vnfGroup;
            }
        }
        System.err.println("can not find the vnfType --- AdvancedVNFPlacement.getVnfGroup");
        return null;
    }

    private VirtualMachine generateVnf(int vnfType) {
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

    private void addQueue(int vnfType) {
        int queueLimit = 20;
        if(vnfQueue.size() >= queueLimit) {
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

    private int randomUniformInt(int lower, int high) {
        return lower + (int)(Math.random()*(high - lower +1));
    }

    public static void verifyKcut() {
        // TODO
    }
}
