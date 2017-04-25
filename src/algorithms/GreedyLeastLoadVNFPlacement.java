package algorithms;

import model.*;
import topology.Topology;
import topology.fatTree.FatTreeTopo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by Janon Wang on 2017/2/14.
 */
public class GreedyLeastLoadVNFPlacement implements VNFPlacement{
    private Topology topo;
    private final int topN =1;
    private int k;

    private double para1;
    private double para2;
    private double para3;


    public GreedyLeastLoadVNFPlacement(Topology topo, double para1, double para2, double para3) {
        this.topo = topo;
        this.k = ((FatTreeTopo)topo).getK();
        this.para1 = para1;
        this.para2 = para2;
        this.para3 = para3;
    }

    @Override
    public VirtualMachine placeVNFInTopo(VirtualNetworkFunction v) {
        VirtualMachine availableVM = getAvailableVMInTopo(v);
        if(availableVM != null) {
//            System.out.print("existed -- ");
            return availableVM;
        }
        VirtualMachine vm = generateVnf(v.vnfType);
        PhysicalServer server = whichServer(vm);
        if(server == null) {
            return null;
        } else {
            this.topo.launchVM(server, vm);
            // 将已经启动过的VNF在队列中做记录
            // addQueue(vnfType);
//            System.out.print("new -- ");
            return vm;
        }
    }

    @Override
    public VirtualMachine placeVNFInServer(VirtualNetworkFunction virtualNetworkFunction, Address serverAddr) {
        return null;
    }

    @Override
    public VirtualMachine placeVNFInRack(VirtualNetworkFunction virtualNetworkFunction, Address serverAddr) {
        return null;
    }

    @Override
    public void countVnf(NetworkService ns) {

    }

    @Override
    public void showVnfCountMatrix() {

    }

    @Override
    public int[][] getVnfCountMatrix() {
        return new int[1][1];
    }

    @Override
    public boolean isMostRelated(int lastVNF, int thisVNF) {
        return false;
    }

    @Override
    public boolean inSameGroup(int lastVNF, int thisVNF) {
        return false;
    }

    private VirtualMachine getAvailableVMInTopo(VirtualNetworkFunction v) {
        ArrayList<VirtualMachine> availableVM = new ArrayList<>();
        for(short i = 1; i <= k; i++) {
            PhysicalServer[][] physicalServers = topo.getServerByPod(i);
            for(int j = 1; j <= k / 2; j++) {
                for(int h = 1; h <= k / 2; h++) {
                    Set<VirtualMachine> vmSet = physicalServers[j][h].getVmSet();
                    for(VirtualMachine vm : vmSet) {
                        if(vm.vnfType == v.vnfType && vm.isResEnough(v.mipsNeeded))
                            availableVM.add(vm);
                    }
                }
            }
        }
        if(availableVM.isEmpty()) {
            return null;
        } else {
            return availableVM.get(randomUniformInt(0, availableVM.size()-1));
        }
    }

    private PhysicalServer whichServer(VirtualMachine virtualMachine) { // checked!
        Set<PhysicalServer> filteredServers = filterServers(virtualMachine.cpu, virtualMachine.ram);
        if(filteredServers.isEmpty()) {
            return null;
        } else {
            ArrayList<PhysicalServer> weightedServers = this.weigthServers(filteredServers);
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
    private ArrayList<PhysicalServer> weigthServers(Set<PhysicalServer> serverSet) { // checked!
        // 如果可行的server小于topN， 则全部返回
        ArrayList<PhysicalServer> servers = new ArrayList<>();
        serverSet.forEach(servers::add);
        if(servers.size() <= this.topN) {
            return servers;
        }
        double[] multiplier = new double[]{para1, para2, para3};
        int size = servers.size();
        double[][] decisionM = new double[size][3];
        // 计算决策矩阵
        for(int i = 0; i < size; i++) {
            PhysicalServer s = servers.get(i);
            decisionM[i][0] = s.getCpuCoreRemain();
            decisionM[i][1] = s.getRamRemain();
            decisionM[i][2] = s.getTrafficIO();
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

    private VirtualMachine generateVnf(int vnfType) {
        return new VirtualMachine(1, 1024, vnfType);
    }

    private int randomUniformInt(int lower, int high) {
        return lower + (int)(Math.random()*(high - lower +1));
    }
}
